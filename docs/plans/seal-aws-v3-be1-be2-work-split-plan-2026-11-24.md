# Plan chi tiết BE1 – BE2 triển khai kiến trúc AWS v3

Ngày 24/11/2026, Asia/Saigon. Nguồn chính: sơ đồ `E:\seal-aws-architecture-v3 (1).drawio` (sửa 23/11, Status: Proposed). Đối chiếu thêm bản 17/11 trong `docs/architecture/seal-aws-architecture-v3.drawio`, [plan vẽ v3](seal-aws-architecture-v3-redraw-plan-2026-11-15.md), [handoff BE1→BE2](backend1-be2-current-handoff-2026-10-28.md) và code tại branch `codex/pr-23-keep-seal`. Đây là **kế hoạch**; chưa có hạ tầng nào được apply.

---

## Phần A — Review sơ đồ ngày 23/11

### A1. Thay đổi so với bản 17/11

| # | Thay đổi | Đánh giá |
|---|---|---|
| C1 | ALB → Auto Scaling group: **HTTP 8080 → HTTPS 8443** | Hợp lệ, nhưng kéo theo việc code/deploy (mục B, task BE1-07). Cần chốt là quyết định thật, không chỉ sửa nhãn |
| C2 | Thêm khối **Rate limits (outbound calls)**: DeepSeek 5 job/judge/giờ, 20/ngày, 300 job/event/ngày, ≤60k input + 4k output token, cùng SHA dùng lại kết quả, backoff 429; GitHub token 5.000 req/giờ, mỗi SHA fetch 1 lần; SES 1 OTP/phút và 5/giờ mỗi địa chỉ; WAF rate rule `/oauth2/*`, `/login/*` | Tốt cho vấn đáp, nhưng **hầu hết chưa có trong code** (A3). Nếu giữ trên hình phải làm hoặc ghi là kế hoạch |
| C3 | **Legend + bảng chú thích số luồng + khối tiêu đề bị mất** (cell legend bị thay bằng khối rate limit) | **Lỗi** so với checklist mục 8 của plan vẽ: cần legend nét liền/đứt, bảng 17 số, khối tiêu đề v3/ngày/người vẽ/người review/Proposed |
| C4 | AWS Budgets chuyển ra ngoài Region (cạnh global) | Đúng (Budgets là dịch vụ global) |
| C5 | S3 Gateway Endpoint đổi container cha từ VPC sang Region trong XML | Không phải lỗi: khi render, icon vẫn nằm đúng trên mép phải VPC (đã kiểm lại ngày 24/11) |
| C6 | Bỏ đường EC2 → NAT AZ-B | Chấp nhận (vẽ một lần từ AZ-A), nhưng NAT AZ-B giờ không có dây nào; legend cũ giải thích việc này, đã mất theo C3 |
| C7 | Nhãn hai dòng cho ACM (us-east-1 / ap-southeast-1), SQS, Lambda, ECR | Tốt |

### A2. Lỗi/thiếu còn lại trên hình (cả hai bản)

| # | Vấn đề | Đề xuất sửa |
|---|---|---|
| R1 | Chuỗi **Browser → Route 53 → WAF → CloudFront** vẽ như các hop nối tiếp. Thực tế Route 53 chỉ trả DNS, browser kết nối thẳng CloudFront, WAF là web ACL gắn vào CloudFront | Browser → CloudFront (①); Route 53 nối nét chấm "DNS"; WAF nối nét chấm "Web ACL" với CloudFront |
| R2 | ③ chỉ ghi `/api/*`. CloudFront còn route `/oauth2/*`, `/login/oauth2/*`, `/uploads/*` tới ALB | Ghi "③ /api/*, /oauth2/*, /uploads/*" hoặc ghi trong legend |
| R3 | Thiếu đường **API EC2 → Google/GitHub OAuth** (server đổi code lấy token, gọi userinfo qua NAT). ⑭ chỉ có phía browser | Thêm nhánh từ ⑥ Outbound tới khối OAuth, hoặc ghi trong legend "⑥ Outbound: GitHub repo, OAuth token, SES SMTP" |
| R4 | EC2 đọc Parameter Store (qua script deploy) không được vẽ; chỉ có Lambda ⑯ | Thêm nét đứt EC2/Run Command → Parameter Store, hoặc ghi trong legend |
| R5 | ACM ap-southeast-1 đứng riêng, không gắn ALB | Thêm nét chấm ALB — ACM (ap-southeast-1) như CloudFront — ACM (us-east-1) |
| R6 | Nếu giữ C1 (HTTPS 8443), cần ghi chứng chỉ phía EC2 đến từ đâu | Ghi trong legend: "ALB → EC2 HTTPS 8443, self-signed/private cert, ALB không xác thực cert target" |
| R7 | Khối rate limit nói "Same SHA reuses the stored result". Kết quả còn phụ thuộc rubric, prompt và model | Sửa thành "Same SHA + rubric + prompt version reuses the stored result" |
| R8 | Không thấy ghi đặt sticky session trên ALB cho OAuth (2 instance) | Ghi một dòng trong legend |

### A3. Khối Rate limits so với code hiện tại

| Giới hạn trên hình | Code hiện tại | Task |
|---|---|---|
| 5 job/judge/giờ, 20/ngày; 300/event/ngày | **Chưa có**; mỗi POST tạo job mới (`AiJobService.create`) | BE1-04 |
| Cùng SHA dùng lại kết quả | **Chưa có** | BE1-05 |
| ≤60k input / 4k output token | Chỉ có giới hạn byte (`JobLimits`: source ≤8 MB, 40 file, 1 MB text); token cap thuộc Lambda | BE1-05 (giới hạn input), AI-02 (token) |
| Backoff 429, dừng khi hết số dư | Chưa có (chưa có Lambda) | AI-02 |
| GitHub token 5.000 req/giờ | `GitHubSourceReader` **không đọc token**; chỉ `GitHubRepoService` (luồng Gemini cũ) có `app.github.token` | BE1-03 |
| Mỗi SHA fetch 1 lần | Chưa có | BE1-05 |
| SES 1 OTP/phút, 5/giờ mỗi địa chỉ | `PasswordResetService` chỉ có hết hạn 10 phút và tối đa 10 lần nhập sai; **chưa có cooldown gửi lại** | BE1-06 |
| WAF rate rule `/oauth2/*`, `/login/*` | Hạ tầng | BE2-14 |

### A4. Phát hiện ảnh hưởng plan cũ

- `AiJobService.result()` trả về **`AiInsightResponse`**, DTO dùng chung với luồng Gemini. Khi bỏ Gemini **không được xoá DTO này**; chỉ xoá `AiJudgeAssistantService`, `GitHubRepoService`, controller Gemini và `app.ai.gemini.*`.
- `/uploads/**` vẫn `permitAll` và `WebMvcConfig` vẫn serve thư mục local. Trên AWS file nằm ở S3, nên phải kiểm lại `/uploads/avatars/*` đi qua `AvatarController`/S3 và không còn phụ thuộc đĩa EC2 (2 instance không chung đĩa).

---

## Phần B — Phân vai

| | BE1 — ứng dụng & contract | BE2 — hạ tầng AWS & vận hành |
|---|---|---|
| Sở hữu | `back-end/src/seal-api`, Flyway, contract `fcaj.ai.*`, gate/script deploy, runbook smoke, dữ liệu test qua API, cập nhật sơ đồ | Tài khoản AWS, VPC/SG/IAM, RDS, S3, SQS, hạ tầng Lambda, CloudFront/WAF/Route 53/ACM, ECR, SSM, CloudWatch/SNS/Budgets, chạy deploy |
| Không làm | Apply hạ tầng | Sửa code app |
| Phối hợp | **FE**: build, domain, cookie, màn AI job. **AI** (`ai-worker`): handler Lambda gọi DeepSeek | |

Ký hiệu: **BE1-xx**, **BE2-xx**, **AI-xx**, **FE-xx**, **J-xx** (làm chung). Ước lượng tính theo ngày công một người.

---

## Phần C — Các phase và task

### Phase 0 — Chốt quyết định (2 ngày)

| ID | Việc | Owner | Kết quả |
|---|---|---|---|
| J-01 | **D-01: ALB → EC2 HTTPS 8443 hay HTTP 8080.** 8443 mã hoá trong VPC nhưng phải quản lý keystore trên mỗi instance, đổi Dockerfile/compose/health check. 8080 đơn giản, SG đã giới hạn chỉ ALB | BE1+BE2 (+mentor) | Quyết định ghi vào plan; sơ đồ khớp |
| J-02 | Domain thật, nơi quản lý DNS | BE2 | `app.<domain>` |
| J-03 | Revision nền (`3eda1fb` hay bản tích hợp `develop`/`main` mới), mapping tên SEAL ↔ FCAJ | BE1 đề xuất, BE2 xác nhận | SHA nền |
| J-04 | Bảng tên tài nguyên: catalog `fcaj_poc`, bucket, prefix, `jobs`/`jobs-dlq`/`results`/`results-dlq`, ECR repo, parameter path `/seal/<env>/…`, log group | BE2 chốt, BE1 review | Bảng trong `docs/plans/` |
| J-05 | Operator deploy, cửa sổ test, kênh private chuyển secret | BE2 | Tên người + lịch |
| J-06 | Xác nhận số rate limit (A3) là yêu cầu thật | Nhóm | Danh sách giới hạn chốt |
| BE1-01 | Sửa sơ đồ theo A1/A2 (legend, khối tiêu đề, S3 endpoint, R1–R8), cập nhật bản trong `docs/architecture/` | BE1 | `.drawio` + PNG mới |

**Cổng qua:** J-01…J-06 có đáp án; sơ đồ sửa xong.

### Phase 1 — Nền hạ tầng (BE2) song song với code (BE1) (1–1,5 tuần)

**BE2**

| ID | Việc | Phụ thuộc | Done khi |
|---|---|---|---|
| BE2-01 | VPC `10.20.0.0/16`, 2 AZ × (public, private app, private DB), IGW, NAT AZ-A + EIP, route table | J-04 | Outputs subnet/route |
| BE2-02 | S3 Gateway Endpoint gắn route table private app; policy chỉ cho bucket dự án + bucket layer ECR | BE2-01 | EC2 tới S3 không qua NAT |
| BE2-03 | SG: ALB chỉ nhận prefix list CloudFront 443; EC2 chỉ nhận 8443/8080 (theo D-01) từ SG ALB; RDS chỉ nhận 3306 từ SG EC2 | BE2-01, J-01 | Bảng SG |
| BE2-04 | RDS MySQL 8.4 Single-AZ, parameter group `lower_case_table_names=1` **khi tạo**, catalog rỗng `utf8mb4_0900_ai_ci`, backup + PITR, deletion protection | BE2-01 | Endpoint, CA bundle |
| BE2-05 | Hai identity DB: migration (DDL trong catalog) và runtime (DML + đọc `flyway_schema_history`) | BE2-04 | Credential vào Parameter Store |
| BE2-06 | S3 application (private, versioning, block public, SSE); S3 frontend (private, chờ OAC) | J-04 | Bucket |
| BE2-07 | SQS jobs + Job DLQ, results + Result DLQ; redrive maxReceive (đề xuất 5); visibility results 120s; jobs để Phase 4 chỉnh | J-04 | Queue URL/ARN |
| BE2-08 | ECR repo (scan on push, lifecycle giữ N image) | — | URI |
| BE2-09 | Parameter Store SecureString theo danh sách của BE1-09 | BE1-09 | Paths đã tạo (không gửi giá trị) |
| BE2-10 | IAM role EC2: S3 theo prefix, SQS (send jobs; receive/delete results), SSM, Parameter Store read, ECR pull, CloudWatch | BE2-06…09 | Policy gửi BE1 đối chiếu |
| BE2-11 | Launch template (AL2023, Docker, SSM agent, không SSH, IMDSv2 hop limit 2 cho container) + ASG desired 1, min 1, max 2 | BE2-10 | Instance hiện trong SSM |
| BE2-12 | ALB (public subnet 2 AZ), listener HTTPS 443 với ACM ap-southeast-1; target group theo D-01, health `/health/ready`, sticky session | BE2-11 | Target healthy (sau Phase 2) |

**BE1**

| ID | Việc | File chính | Done khi |
|---|---|---|---|
| BE1-02 | **Bỏ Gemini:** xoá `AiJudgeAssistantService`, `GitHubRepoService`, controller/endpoint Gemini, `app.ai.gemini.*`, `GEMINI_*` trong env mẫu. **Giữ** `AiInsightResponse`, `RepoDigest` nếu AI job còn dùng. FE-01 đổi màn judge sang 3 API AI job | `service/`, `controller/`, `application.properties`, FE `shared/api/ai.ts`, `JudgeScoringPage.tsx` | Full test xanh, grep `gemini` = 0 |
| BE1-03 | `GitHubSourceReader` gửi `Authorization: Bearer ${GITHUB_TOKEN}` nếu có; không log token; test có/không token | `aijob/GitHubSourceReader.java` | Test pass |
| BE1-04 | **Quota job:** đếm trong bảng job theo requester/giờ, requester/ngày, event/ngày; vượt → `429` mã `JOB_QUOTA_EXCEEDED`; ngưỡng cấu hình qua `app.ai.jobs.quota.*`. Kiểm cả khi 2 instance (đếm bằng DB, không đếm trong RAM) | `aijob/AiJobService.java`, `AiJobStore.java`, migration **V4** nếu cần index, contract `CURRENT.md` | Test quota + contract cập nhật |
| BE1-05 | **Dùng lại kết quả:** trước khi tạo job, tìm job SUCCEEDED cùng `commitSha + rubricVersion + promptVersion + modelConfigVersion` → trả job đó (hoặc job mới trỏ tới result cũ, chốt trong contract). Cache source snapshot theo `commitSha` để không fetch lại | `AiJobService`, `AiJobStore`, V4 | Test reuse + không gọi GitHub lần 2 |
| BE1-06 | **Cooldown OTP:** 1 lần/phút, 5 lần/giờ mỗi email; trả lỗi chung không lộ email có tồn tại hay không | `service/PasswordResetService.java`, repo OTP | Test pass |
| BE1-07 | **Theo D-01.** Nếu 8443: `server.port=8443`, `server.ssl.*` từ keystore mount readonly; Dockerfile `EXPOSE 8443`; compose ALB-direct; health check HTTPS; script tạo self-signed cert lúc boot. Nếu 8080: giữ nguyên | `application-aws.properties`, `Dockerfile`, `deploy/compose.aws.alb-direct.example.yml` | Container trả `/health/ready` qua HTTPS |
| BE1-08 | **Chạy 2 instance:** test 2 dispatcher tranh outbox (claim lease), 2 `ResultPoller` nhận cùng completion (ledger báo DUPLICATE), timer notice (`TimerNoticeClaimer`), mail `@Async`. Xác nhận avatar/đề thi không dùng đĩa local trên profile `aws` | `aijob/*`, `service/RoundTimerService.java`, `config/WebMvcConfig.java` | Test MySQL lab 2 JVM |
| BE1-09 | Danh sách parameter: tên, bắt buộc/tuỳ chọn, ai đọc (EC2/migration/Lambda), không giá trị | `docs/plans/` | BE2 tạo được |
| BE1-10 | **Script render env:** trên host, đọc `/seal/<env>/*` bằng role EC2 → ghi `runtime.env`, `migration.env` quyền 600, không in giá trị | `Test/render-env-from-ssm.sh` (mới) | Chạy thử với parameter giả |
| BE1-11 | **Gate chế độ pull:** `deploy-verified.sh` nhận `CANDIDATE_IMAGE=<ecr>@sha256:…`, pull, chạy migration CLI **từ image đó**, verify, rồi mới thay container; giữ chế độ build cũ; cập nhật 8 test thứ tự lệnh | `Test/deploy-verified.sh`, test `.mjs` | Test thứ tự lệnh xanh |
| BE1-12 | SES: `.env.aws.example` thêm `MAIL_HOST=email-smtp.ap-southeast-1.amazonaws.com`, 587, địa chỉ gửi; kiểm `EmailService` không hard-code from | `.env.aws.example`, `EmailService.java` | Review |
| BE1-13 | Phát hành **SHA mới**: full test, MySQL lab, contract validator, release verifier mới | `docs/checks/…` | SHA + evidence |

**Cổng qua:** BE2 gửi Outputs đã lọc (BE2-01…12); BE1 có SHA mới (BE1-13).

### Phase 2 — Backend lên AWS, worker Java MOCK (3–4 ngày)

Dùng worker MOCK đã kiểm để tách lỗi hạ tầng khỏi lỗi AI.

| ID | Việc | Owner | Done khi |
|---|---|---|---|
| BE2-13 | Build image từ SHA BE1-13, push ECR (tạm tay), ghi digest | BE2 | Digest |
| J-07 | Qua SSM Run Command: `render-env` → `initialize-empty` → `verify-deploy` → start `seal-backend`; chuyển `DEPLOY_DB_MODE=verify-only` | BE2 chạy, BE1 trực | Flyway V1…V4, target ALB healthy |
| BE1-14 | Bootstrap admin có chủ đích rồi tắt; tạo dataset 2 đội × 3 người qua API theo [runbook](seal-be1-smoke-data-runbook.md); ghi ID | BE1 | Bảng ID |
| J-08 | Bật `APP_AI_JOBS_ENABLED` + `DELIVERY_ENABLED`; chạy `AwsMockWorkerMain --one-success` và `--one-failure` trên 2 job; thử duplicate | BE1+BE2 | SUCCEEDED/FAILED đúng; Score/RoundResult không đổi |
| BE1-15 | Kiểm quota (BE1-04), reuse (BE1-05) trên AWS | BE1 | 429 đúng ngưỡng; job lặp SHA không fetch lại |
| BE1-16 | Upload avatar/đề thi → S3; restart container và đọc lại | BE1 | File còn |
| J-09 | Tăng ASG lên 2, lặp J-08 khi cả 2 instance chạy | BE1+BE2 | Không gửi/ghi trùng |

**Cổng qua:** evidence trong `docs/checks/seal-aws-v3-phase2-<ngày>/`.

### Phase 3 — Cửa vào CloudFront (4–5 ngày)

| ID | Việc | Owner |
|---|---|---|
| BE2-14 | ACM us-east-1 cho `app.<domain>`; ACM ap-southeast-1 cho ALB **phủ `app.<domain>`** (CloudFront forward Host thì dùng Host làm SNI tới origin) | BE2 |
| BE2-15 | CloudFront: `/*` → S3 FE (OAC, SPA fallback `index.html`); `/api/*`, `/oauth2/*`, `/login/oauth2/*`, `/uploads/*` → ALB, CachingDisabled, origin request policy AllViewer (cookie, query, `X-XSRF-TOKEN`, Host), mọi method | BE2 |
| BE2-16 | WAF: rate-based theo IP (ngưỡng rộng vì IP chung FPT), rule chặt hơn cho `/api/auth/*`, `/oauth2/*`, `/login/*`; managed rules. **`SizeRestrictions_BODY` (Core rule set) chặn body > 8 KB → đặt Count hoặc scope-down loại trừ path upload** | BE2 |
| BE2-17 | Route 53 `app.<domain>` → CloudFront; SG ALB chỉ còn prefix list CloudFront | BE2 |
| BE2-18 | SES: verify domain (DKIM), xin ra khỏi sandbox, SMTP credential vào Parameter Store | BE2 |
| BE1-17 | Env: `APP_FRONTEND_URL=https://app.<domain>`, `CSRF_COOKIE_DOMAIN` trống, `JWT_COOKIE_SECURE=true`; bật profile `aws,oauth-google,oauth-github` | BE1 |
| FE-02 | Build `VITE_API_URL=https://app.<domain>`; BE2 sync S3 + invalidate | FE+BE2 |
| J-10 | Đăng ký lại OAuth callback `https://app.<domain>/login/oauth2/code/{google,github}` | BE1+chủ OAuth app |
| J-11 | Nghiệm thu trình duyệt: login cookie, CSRF qua nhiều mutation, logout, OAuth Google/GitHub (2 instance + sticky), upload 20 MB, OTP qua SES + cooldown, WAF rate rule trả 403/429 | BE1+FE |

**Cổng qua:** mọi luồng chạy qua `app.<domain>`; truy cập thẳng ALB bị chặn.

### Phase 4 — Lambda + DeepSeek (1,5–2 tuần)

| ID | Việc | Owner | Done khi |
|---|---|---|---|
| BE1-18 | Contract cho model thật: thêm `mode` giá trị mới (vd `LIVE`), `provenance` cho DeepSeek, mã lỗi (`MODEL_RATE_LIMITED`, `MODEL_BUDGET_EXHAUSTED`, `MODEL_OUTPUT_INVALID`); cập nhật `JobResultValidator`, `CURRENT.md`, current-examples | BE1 | Validator + examples |
| BE1-19 | Bộ test contract dùng chung (JSON fixtures + script) để Lambda tự kiểm trước khi deploy | BE1 | Chạy được từ `ai-worker/` |
| AI-01 | Handler Lambda Python: nhận SQS event (batch 1) → đọc manifest/source/rubric từ S3 theo Ref, kiểm sha256 → gọi DeepSeek → ghi `results/{jobId}/…json` (If-None-Match) → gửi completion `fcaj.ai.message.v1`. **Không** fetch GitHub, **không** tính điểm, `suggestedScoreRange=null`, evidence path/dòng có thật | AI (BE1 review) | Qua BE1-19 |
| AI-02 | Giới hạn trong Lambda: cắt input ≤60k token, `max_tokens` 4k, backoff 429 có trần, hết số dư → FAILED mã ngân sách (không retry vô hạn); prompt đánh dấu code là dữ liệu không tin cậy; che secret trong source | AI | Unit test |
| BE2-19 | Hạ tầng Lambda: ngoài VPC; ESM từ jobs queue, batch 1, `ReportBatchItemFailures`, max concurrency 2; timeout T (đề xuất 120–180s), **visibility jobs ≥ 6×T**; DeepSeek key qua Parameters and Secrets Lambda Extension; role chỉ đọc `inputs/`,`rubrics/`, ghi `results/`, gửi results queue | BE2 | Deploy được |
| BE2-20 | Hạn mức nạp tiền DeepSeek + Budgets alarm | BE2+nhóm | Alarm thử |
| J-12 | Smoke: 1 repo public nhỏ (SUCCEEDED), 1 lỗi có chủ đích (FAILED), 1 redelivery (DUPLICATE), 1 vượt quota (429), 1 SHA lặp (reuse). Score/RoundResult không đổi | BE1+BE2+AI | Evidence |
| FE-03 | Màn judge: tạo job, poll, hiển thị kết quả + disclaimer, xử lý mã lỗi; không tự retry POST | FE | Nghiệm thu UI |

**Cổng qua:** job thật qua Lambda đi hết validator Java; Java MOCK worker chỉ dùng trong test.

### Phase 5 — CI/CD, mùa thi, giám sát (1 tuần)

| ID | Việc | Owner |
|---|---|---|
| BE2-21 | IAM OIDC provider + role GitHub Actions (giới hạn repo + branch `main`) | BE2 |
| J-13 | Viết lại `.github/workflows/deploy.yml`: test → build image → push ECR (tag SHA) → build FE + sync S3 + invalidate → SSM Run Command (`render-env` + gate theo digest). Bỏ `appleboy/ssh-action` | BE1 (build/gate) + BE2 (AWS/secret) |
| J-14 | Deploy cho ASG 2 instance: migration chạy **một lần** trên một instance trước, sau đó instance refresh/rolling; rollback image không undo DDL | BE1 thiết kế, BE2 cấu hình |
| BE2-22 | CloudWatch: log group app/Lambda; alarm ALB 5xx, target unhealthy, RDS CPU/kết nối, SQS `ApproximateAgeOfOldestMessage`, DLQ > 0, Lambda errors/throttles → SNS email | BE2 |
| BE2-23 | Runbook mùa thi: bật RDS Multi-AZ, ASG desired 2, NAT AZ-B (+ route AZ-B); tắt sau mùa; RTO/RPO mục tiêu | BE2 viết, BE1 review |
| BE1-20 | Kịch bản k6 (~500 user, 150–250 req/s) qua CloudFront, có đăng nhập + poll | BE1 chạy, BE2 theo dõi |
| J-15 | Diễn tập: bật/tắt chế độ mùa thi một lần; tắt 1 instance khi đang có tải | BE1+BE2 |

**Cổng qua:** một commit vào `main` lên AWS không thao tác tay; diễn tập mùa thi đạt.

---

## Phần D — Thứ tự và phụ thuộc

```
Phase 0 (J-01..J-06, BE1-01)
   ├─► BE2-01..12 ──────────────┐
   └─► BE1-02..13 (SHA mới) ────┴─► Phase 2 ─► Phase 3 ─► Phase 4 ─► Phase 5
                                               (BE1-18/19 + AI-01/02 có thể
                                                bắt đầu song song từ Phase 2)
```

Đường găng: J-01/J-04 → BE2-01…04 → J-07. Việc AI (BE1-18/19, AI-01/02) không chặn Phase 2–3 nên làm song song.

## Phần E — Điểm bàn giao

| BE1 → BE2 | BE2 → BE1 |
|---|---|
| SHA phát hành + release verifier mỗi phase | Outputs đã lọc: subnet, SG, RDS endpoint, bucket/prefix, queue URL, ECR URI, parameter paths, ALB/CloudFront domain |
| Danh sách parameter (BE1-09) | Policy IAM thực để đối chiếu |
| Bảng quyền S3/SQS tối thiểu theo identity | Image digest, log gate/deploy đã lọc |
| Script render env + gate pull | Kết quả `verify-deploy`, health, log CloudWatch khi lỗi |
| Contract AI + bộ test cho Lambda | Cấu hình Lambda/ESM/visibility/DLQ thực |
| Runbook smoke + bảng ID cần ghi | Cửa sổ test, operator, kênh secret |

## Phần F — Rủi ro

| Rủi ro | Xử lý |
|---|---|
| Sơ đồ ghi rate limit mà code chưa có, bị hỏi khi vấn đáp | Làm BE1-04/05/06 trước buổi bảo vệ, hoặc đánh dấu "planned" trên hình |
| WAF chặn upload > 8 KB | BE2-16 + test J-11 |
| 2 instance gửi/ghi trùng | BE1-08; chưa đạt thì giữ ASG desired 1 |
| HTTPS 8443: cert hết hạn/lệch trên instance mới | Sinh cert lúc boot trong launch template; ALB không xác thực cert target |
| OAuth session trong RAM | Sticky session; Redis khi cần nhiều hơn 2 instance |
| SQS gửi lặp tới Lambda | If-None-Match khi ghi output + ledger messageId phía Java |
| Contract Python ≠ Java | Lambda không deploy khi chưa qua BE1-19 |
| Gửi code thí sinh ra DeepSeek | Chỉ repo public tại SHA đã nộp, che secret, không gửi thông tin đội; ghi trong thể lệ |
| Migration V4 lỗi giữa chừng | Snapshot RDS trước migration; không repair/clean để vượt gate |

## Phần G — Tuần này

- **BE1:** BE1-01 (sửa sơ đồ), BE1-02, BE1-03, BE1-06, BE1-08; thiết kế BE1-04/05 (cần V4).
- **BE2:** J-02, J-04, J-05; BE2-01…08.
- **Họp chung:** J-01 (8443 hay 8080), J-03, J-06.
