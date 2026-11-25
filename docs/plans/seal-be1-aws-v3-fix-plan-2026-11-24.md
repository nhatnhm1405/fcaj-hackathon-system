# Plan sửa phía BE1 cho kiến trúc AWS v3

Ngày 24/11/2026, Asia/Saigon. Tách từ [plan chung BE1–BE2](seal-aws-v3-be1-be2-work-split-plan-2026-11-24.md) (giữ nguyên mã task). Plan này chỉ gồm việc của BE1: code `back-end/src/seal-api`, contract AI, script deploy và sơ đồ. Đã đọc code thực tế trên `codex/pr-23-keep-seal` để chỉ đúng file cần sửa.

## 0. Nguyên tắc cho đợt sửa này

1. **Không thêm migration V4 trong đợt này.** Gate `verify-deploy` đang yêu cầu đúng V3 (history, checksum, fingerprint). Thêm V4 thì phải sửa và kiểm lại gate. Quota và dùng lại kết quả làm được bằng các cột/index đã có trong `AiJob` (`requester_id`, `event_id`, `submission_id`, `created_at`, `input_revision`, `source_json`). Nếu sau này thật sự cần cột mới thì tách thành đợt riêng có kèm sửa gate.
2. **Không đổi tên** catalog, bucket, queue, logical key hay namespace `fcaj.ai.*`.
3. Mỗi task một commit theo conventional commit, trên một branch từ `develop`, ví dụ `feature/be1-aws-v3`. Chạy full test sau mỗi task.
4. Đổi gì trong API hoặc message thì cập nhật luôn `docs/contracts/ai-job-v1/CURRENT.md` và current-examples trong cùng commit.

## 1. Thứ tự làm

| Đợt | Task | Lý do xếp trước |
|---|---|---|
| A — dọn dẹp (1–2 ngày) | BE1-01 sửa sơ đồ, BE1-02 bỏ Gemini | Giảm code thừa trước khi sửa luồng AI; sơ đồ cần cho buổi chốt J-01 |
| B — AI job (3–4 ngày) | BE1-03 token, BE1-21 chốt SHA theo deadline, BE1-04 quota, BE1-05 dùng lại kết quả | Cùng sửa `GitHubSourceReader`/`AiJobService`, nên làm liên tiếp |
| C — an toàn nhiều instance (2 ngày) | BE1-06 cooldown OTP, BE1-08 test 2 instance | BE1-08 kiểm luôn quota đợt B khi chạy 2 JVM |
| D — deploy (2–3 ngày) | BE1-09 danh sách parameter, BE1-10 script lấy env từ SSM, BE1-11 gate kéo image ECR, BE1-07 cổng 8443 (theo D-01), BE1-12 SES | Cần tên tài nguyên từ BE2 (J-04) |
| E — phát hành (1 ngày) | BE1-13 | Gom kết quả để giao BE2 |

Tổng khoảng **9–12 ngày công**. Đợt A–C không phụ thuộc BE2, làm được ngay.

---

## 2. Chi tiết từng task

### BE1-01 — Sửa sơ đồ v3

**File:** `E:\seal-aws-architecture-v3 (1).drawio` → chép vào `docs/architecture/seal-aws-architecture-v3.drawio`, xuất lại PNG.

- Khôi phục **legend** (nét liền/đứt/chấm), **bảng chú thích ①–⑰**, **khối tiêu đề** (v3, ngày, người vẽ, người review, Proposed). Khối Rate limits đặt thành ô riêng.
- S3 Gateway Endpoint: giữ nguyên. Khi render icon đã nằm trên mép VPC; chỉ container cha trong XML khác.
- Browser nối thẳng CloudFront (①). Route 53 nối nét chấm "DNS", WAF nối nét chấm "Web ACL" với CloudFront.
- Nhãn ③: `/api/*, /oauth2/*, /login/oauth2/*, /uploads/*`.
- Ghi trong legend: ⑥ Outbound gồm GitHub, OAuth token/userinfo, SES SMTP; EC2 đọc Parameter Store qua script deploy; ALB bật sticky session.
- Thêm nét chấm ALB — ACM (ap-southeast-1).
- Nhãn ALB → ASG theo kết quả D-01 (8443 hoặc 8080).
- Sửa ghi chú rate limit: "Same SHA + rubric + prompt version reuses the stored result".

**Xong khi:** đi hết checklist mục 8 của [plan vẽ](seal-aws-architecture-v3-redraw-plan-2026-11-15.md).

### BE1-02 — Bỏ luồng Gemini

**Xoá:**
- `controller/AiController.java` (`POST /api/ai/submissions/{id}/insights`)
- `service/AiJudgeAssistantService.java`
- `service/GitHubRepoService.java`
- `dto/RepoDigest.java` (chỉ hai service trên dùng)
- Test: `AiJudgeAssistantAccessTest`, `AiJudgeAssistantRepoAnalysisTest`, `GitHubRepoServiceTest`
- `application.properties`: các dòng `app.ai.gemini.*`; `.env.aws.example`: `GEMINI_API_KEY`

**Giữ:** `dto/response/AiInsightResponse.java`, vì `AiJobService.result()`, `LocalMockJobService` và `LocalMockJobController` vẫn dùng. Giữ `app.github.token=${GITHUB_TOKEN:}` để BE1-03 dùng lại.

**Sửa:** comment ở `SecurityConfig` (dòng ~158) cho đúng luồng AI job; rule `/api/ai/**` giữ nguyên.

**Báo FE (FE-01):** `shared/api/ai.ts` và `JudgeScoringPage.tsx` đang gọi `/insights`. Sau khi merge, endpoint này trả 404. FE chuyển sang 3 API AI job. Nên merge BE1-02 cùng lúc với FE-01, hoặc giữ controller trả 410 Gone trong một bản phát hành.

**Test:** `./mvnw.cmd -Dspring.profiles.active=test test`; `grep -ri gemini src` trả về 0 dòng.

### BE1-03 — Collector GitHub dùng token

**File:** `aijob/GitHubSourceReader.java`, `aijob/JobEnvironmentConfiguration.java` (nơi tạo bean).

- Thêm constructor nhận `token` (đọc `app.github.token`). Nếu có token, `get()` thêm `Authorization: Bearer <token>`.
- Đổi `User-Agent` thành `seal-backend-snapshot`. Chỉ đổi chuỗi này nếu không test hay fixture nào so khớp nó.
- Không log token. `toString` và thông báo lỗi không chứa header.
- Phân biệt lỗi GitHub 403/429 do hết rate limit với repo private. Có thể thêm mã `REPOSITORY_RATE_LIMITED` (503); nếu thêm thì cập nhật contract.

**Test:** dùng `HttpClient` giả, kiểm có/không header; kiểm token không xuất hiện trong exception message.

### BE1-21 — Chốt SHA theo hạn nộp (mới phát hiện)

**Vấn đề:** `collectWithinBudget` gọi `GET /repos/{repo}/commits?per_page=1`, tức lấy **commit mới nhất ở thời điểm judge bấm**. Đội có thể push sau deadline, và AI sẽ đánh giá code nộp muộn. Sơ đồ lại ghi "SHA cố định".

**Sửa (không cần cột mới):** truyền thời điểm kết thúc CONTEST của round, lấy từ `RoundTimer`/`Round` mà `JobSnapshotReader` đã đọc, vào collector và gọi `commits?per_page=1&until=<ISO-8601 UTC>`. Không có commit nào trước deadline thì trả `INVALID_REPOSITORY_REVISION`.

**File:** `aijob/JobSnapshotReader.java` (thêm `contestEndedAt` vào `Input`), `aijob/GitHubSourceReader.java`, `CURRENT.md` (ghi rõ quy tắc chọn SHA).

**Test:** fixture có commit trước/sau deadline, kiểm SHA được chọn. Cần thống nhất với nhóm trước khi làm, vì đây là quy tắc nghiệp vụ.

### BE1-04 — Quota job

**File:** `aijob/AiJobService.java`, `aijob/AiJobStore.java`, `aijob/JobErrors.java`, config mới `app.ai.jobs.quota.*`, `CURRENT.md`.

- Cấu hình: `per-requester-hour=5`, `per-requester-day=20`, `per-event-day=300`. Đặt `0` để tắt.
- **Kiểm sớm** ở đầu `create()`, trước `github.collect()`, để không tốn lượt gọi GitHub khi đã vượt quota.
- **Kiểm chính thức** trong `tx.executeWithoutResult` của `create()`:
  - Thêm `select event_id from HackathonEvent where event_id=? for update`. Requester đã bị khoá bởi dòng `User … for update`, nên hai instance không cùng vượt được ngưỡng.
  - Đếm `AiJob` theo `requester_id` trong 1 giờ và 1 ngày (`created_at >= ?`), và theo `event_id` trong 1 ngày. Dùng index `ix_ai_job_requester(requester_id,event_id)` và index FK của `event_id`; không cần V4.
- Vượt quota: HTTP **429**, mã `JOB_QUOTA_EXCEEDED`, theo đúng wrapper lỗi job. Đếm cả job FAILED để không bị bấm lặp vô hạn. Job được dùng lại (BE1-05) **không** tính vào quota.
- Lưu ý: S3 snapshot (`writer.prepare`) chạy trước transaction. Nếu bị từ chối ở bước kiểm chính thức, object S3 bị bỏ lại; vì key theo `jobId` và không có job tham chiếu nên không ảnh hưởng gì. Ghi chú điều này trong code.

**Test:** H2 + MySQL lab: ca thứ 6 trong giờ trả 429; hai thread tạo đồng thời khi chỉ còn 1 slot thì chỉ một job thành công.

### BE1-05 — Dùng lại kết quả và source theo SHA

**File:** `aijob/AiJobService.java`, `aijob/AiJobStore.java`, `aijob/GitHubSourceReader.java`, `CURRENT.md`.

- Tách collector làm hai bước: `resolve(url, until)`, tốn 1 request, trả `commitSha` + `treeSha`; và `collect(...)`, tải cây thư mục và các file.
- **Dùng lại source:** tìm `AiJob` mới nhất cùng `submission_id` có `source_json` cùng `commitSha` → dùng lại `source_json` đó, bỏ qua bước tải cây/blob. Đáp ứng "mỗi SHA fetch một lần".
- **Dùng lại kết quả:** sau khi dựng request, nếu tồn tại job **SUCCEEDED** cùng `submission_id` và cùng `input_revision` → tạo **job mới cho requester hiện tại** ở trạng thái SUCCEEDED, chép `result_json`/`terminal_digest`, `finished_at=now`, **không** tạo outbox và không gửi SQS. `input_revision` là SHA-256 của manifest, gồm commitSha, rubric, processing/model/prompt version và snapshot submission, nên giống hệt nghĩa là đầu vào giống hệt.
  - Phải tạo job mới, không trả jobId cũ, vì `JobAccess.read` chỉ cho requester gốc xem lịch sử. Trả job của người khác sẽ phá quy tắc quyền.
  - Ràng buộc `ck_ai_job_terminal` cho phép SUCCEEDED có `finished_at`, `terminal_digest`, `result_json`.
- Contract: POST vẫn trả 202; `status` có thể là SUCCEEDED ngay. Có thể thêm `reused:true` trong data; nếu thêm thì cập nhật validator và examples.

**Test:** job 2 cùng đầu vào không gọi GitHub tải cây và không ghi outbox; đổi rubric thì không dùng lại; judge B dùng lại kết quả của judge A nhưng không đọc được job của A.

### BE1-06 — Cooldown gửi OTP

**File:** `service/PasswordResetService.java`, `repository/PasswordResetOtpRepository.java`, config `app.password-reset.resend-*`.

- Thêm `countByUser_UserIdAndCreatedAtAfter(userId, time)`.
- Trong `requestOtp`: OTP gần nhất tạo dưới 60 giây trước, hoặc đã có 5 OTP trong 1 giờ → **không gửi**, trả lỗi 429 với thông báo chung. Đặt kiểm tra **trước** `markActiveTokensUsed`, để không vô hiệu hoá OTP đang dùng.
- Kèm sửa lộ thông tin: hiện email không tồn tại thì trả `"mail invalid"`, nên ai cũng dò được email nào có tài khoản. Đổi thành luôn trả thành công chung (không gửi mail nếu không có tài khoản), giữ lỗi 429 cho cooldown. Báo FE nếu thông báo hiển thị thay đổi.
- Bảng `PasswordResetOtp` đã có `created_at`, không cần cột mới.

**Test:** gửi 2 lần trong 1 phút thì lần 2 bị 429; gửi 6 lần trong 1 giờ thì lần 6 bị 429; email không tồn tại cũng trả thành công chung.

### BE1-08 — Chạy an toàn khi 2 instance

**Kiểm và viết test (MySQL lab, 2 Spring context hoặc 2 thread dùng chung DB):**

| Thành phần | Kỳ vọng | Bằng chứng hiện có |
|---|---|---|
| `JobDelivery` + `AiJobStore.claim()` | Hai dispatcher không gửi trùng một message | Claim có điều kiện `claimed_until` (comment "multiple backend replicas cannot own the same delivery") → thêm test 2 dispatcher |
| `ResultPoller` | Hai poller nhận cùng completion, chỉ một APPLIED, còn lại DUPLICATE | Đã có ledger + test redelivery → thêm ca 2 poller song song |
| `TimerNoticeClaimer` / `RoundTimerService` | Thông báo mốc thời gian chỉ gửi một lần | Đọc lại cơ chế claim, viết test nếu chưa có |
| `@Async("mailTaskExecutor")` | Mỗi sự kiện chỉ một instance gửi mail (sự kiện phát từ request nên chỉ chạy ở instance nhận request) | Ghi xác nhận |
| Quota BE1-04 | Không vượt ngưỡng khi 2 instance | Test ở BE1-04 |
| File | Profile `aws` không đọc/ghi đĩa local: avatar qua `AvatarController`/S3, đề thi qua `TrackProblemService`/S3; `WebMvcConfig` `/uploads/**` không dùng trên aws | Đọc `storage/*`, thêm test cấu hình nếu thiếu |
| OAuth | Cần sticky session ALB (session tạm trong RAM) | Ghi vào runbook cho BE2 |

**Xong khi:** có test cho mọi dòng "thêm test" và một mục mới trong runbook.

### BE1-09 — Danh sách parameter

**File mới:** `docs/plans/seal-aws-v3-parameter-inventory.md`.

Bảng gồm: tên biến app, đường dẫn Parameter Store (`/seal/<env>/…`), SecureString hay String, bắt buộc hay tuỳ chọn, ai đọc (runtime, migration, Lambda). Nhóm: DB runtime, DB migration, JWT, frontend/CORS, storage, AI jobs (queue URL, quota), GitHub token, mail/SES, OAuth, bootstrap admin, truststore password, DeepSeek key (chỉ Lambda). **Không có giá trị thật.** Gửi BE2 để làm BE2-09.

### BE1-10 — Script lấy env từ Parameter Store

**File mới:** `Test/render-env-from-ssm.sh`, test kèm `Test/render-env-from-ssm.test.mjs`.

- Input: `SSM_PREFIX`, `OUT_DIR`. Gọi `aws ssm get-parameters-by-path --with-decryption --recursive` bằng role EC2.
- Ghi `runtime.env`, `migration.env` với `umask 077`. Ghi ra file tạm rồi `mv` để file luôn đầy đủ.
- Từ chối nếu thiếu biến bắt buộc theo BE1-09. Không `set -x`, không in giá trị, chỉ in tên biến thiếu.
- Viết giá trị theo định dạng `env_file format: raw` mà compose đang dùng, giữ nguyên `$` và dấu nháy.

**Test:** giả lập `aws` CLI bằng script stub trả JSON; kiểm quyền file, biến thiếu, ký tự đặc biệt.

### BE1-11 — Gate kéo image dựng sẵn từ ECR

**File:** `Test/deploy-verified.sh`, `Test/deploy-verified.test.mjs`.

- Thêm `DEPLOY_IMAGE_SOURCE=build|pull`, mặc định `build` như cũ.
- `pull`: bắt buộc `CANDIDATE_BACKEND_IMAGE=<ecr-uri>@sha256:<digest>`. Không dùng tag. Chạy `docker pull`, kiểm label `org.opencontainers.image.revision` bằng `CANDIDATE_REVISION` thay cho `git rev-parse HEAD`, vì host không cần checkout.
- Migration (`initialize-empty`, `verify-deploy`) vẫn chạy **từ chính image đó**. Compose phải chọn đúng image đó; giữ assert hiện có.
- `Dockerfile`: thêm `ARG REVISION` + `LABEL org.opencontainers.image.revision=$REVISION` để CI gắn nhãn.
- Phần ASG nhiều instance: thêm `DEPLOY_DB_STEP=run|skip`. Instance đầu chạy migration + verify; các instance sau chỉ `verify-deploy` rồi thay container. Thiết kế chi tiết làm chung trong J-14.
- Cập nhật 8 test thứ tự lệnh, thêm ca: pull đúng digest; label lệch → dừng trước migration; dùng tag thay digest → từ chối.

### BE1-07 — Cổng ALB → EC2 (theo D-01)

**Nếu chọn 8443:**
- `application-aws.properties`: `server.port=${SERVER_PORT:8443}`, `server.ssl.enabled=true`, `server.ssl.key-store=/run/secrets/server-keystore.p12`, password từ env.
- `Dockerfile`: `EXPOSE 8443`. Compose ALB-direct: publish `${DEPLOY_TARGET_BIND_IP}:8443:8443`, mount keystore readonly.
- Script `Test/generate-server-keystore.sh`: tạo self-signed cert lúc instance boot (BE2 gọi trong user-data). ALB không xác thực cert target.
- Kiểm `server.forward-headers-strategy=framework` vẫn cho redirect OAuth ra `https://app.<domain>`. Health check của ALB dùng HTTPS `/health/ready`.
- Test: chạy container với keystore tạm, `curl -k https://127.0.0.1:8443/health/live`.

**Nếu chọn 8080:** không sửa code; chỉ sửa nhãn sơ đồ (BE1-01).

### BE1-12 — SES

**File:** `.env.aws.example`, `service/EmailService.java`.

- `.env.aws.example`: `MAIL_HOST=email-smtp.ap-southeast-1.amazonaws.com`, `MAIL_PORT=587`, `MAIL_USERNAME`/`MAIL_PASSWORD` là SMTP credential SES (Parameter Store), thêm `MAIL_FROM=no-reply@<domain>`.
- Kiểm `EmailService` set `From`. Với SES, `From` phải là địa chỉ hoặc domain đã verify; nếu đang lấy từ `spring.mail.username` thì thêm `app.mail.from`.
- Kiểm timeout SMTP hiện tại (5s/3s/5s) đủ với SES qua NAT.

### BE1-13 — Phát hành cho BE2

- Full test, MySQL lab, `run-integration-smoke.ps1`, contract validator, `verify-deploy-templates.mjs`.
- Release verifier mới tại `docs/checks/seal-be1-aws-v3-<ngày>/`.
- Cập nhật `docs/plans/backend1-be2-current-handoff-2026-10-28.md` (hoặc bản handoff mới) với: SHA, thay đổi contract (429 quota, dùng lại kết quả, quy tắc chọn SHA), biến env mới, script mới.
- Push branch và gửi BE2 SHA + link. Việc push/gửi chỉ làm khi bạn đồng ý.

---

## 3. Thay đổi contract cần báo FE/AI

| Thay đổi | Ảnh hưởng |
|---|---|
| Bỏ `POST /api/ai/submissions/{id}/insights` | FE-01 phải chuyển sang AI job |
| POST job có thể trả 429 `JOB_QUOTA_EXCEEDED` | FE hiển thị, không tự retry |
| POST job có thể trả SUCCEEDED ngay (dùng lại kết quả) | FE không giả định trạng thái đầu là PENDING |
| SHA chọn theo deadline CONTEST | Ghi trong thể lệ; AI/Lambda không đổi |
| Quên mật khẩu: luôn trả thành công chung; 429 khi gửi quá nhanh | FE đổi thông báo |

## 4. Những việc BE1 cần từ người khác

| Cần | Từ | Cho task |
|---|---|---|
| D-01: 8443 hay 8080 | Họp BE1+BE2 (+mentor) | BE1-07, BE1-01 |
| Xác nhận số quota và quy tắc chọn SHA theo deadline | Nhóm | BE1-04, BE1-21 |
| Bảng tên tài nguyên + parameter prefix | BE2 (J-04) | BE1-09, BE1-10 |
| Thời điểm FE chuyển màn judge | FE | BE1-02 |

## 5. Bắt đầu ngay

1. Tạo branch `feature/be1-aws-v3` từ `develop`.
2. BE1-01 (sơ đồ), rồi BE1-02 (bỏ Gemini), chạy full test.
3. BE1-03 → BE1-21 → BE1-04 → BE1-05.
