# BE2 → BE1: bàn giao cấu hình và chuẩn bị tích hợp PoC

Ngày: **28/10/2026**, giờ Việt Nam. Người gửi: **Backend 2**. Người nhận: **Backend 1**; phần contract và worker phối hợp với FE/AI.

## 1. Phản hồi báo cáo BE1 và đầu ra cần gửi thêm

Cảm ơn bạn, báo cáo đã rõ. Để mình nhận phần BE1 và ghép với hạ tầng BE2, bạn gửi thêm giúp mình:

1. **Source đã commit trên branch riêng**, kèm commit SHA và link branch/PR để lấy code, review và build đúng phiên bản. Thống nhất đường dẫn/package với repo hiện tại trước khi ghép; chưa merge/push `main` vì có thể kích hoạt deploy.
2. **Các file được nhắc trong báo cáo:** `.env.aws.example`, migration V1/V2, script migration/test/mock worker và evidence MySQL. Link evidence tương đối trong file gửi riêng hiện chưa đủ để mình truy cập.
3. **Contract cho FE/AI:** request/response mẫu của ba API, trạng thái và mã lỗi; schema cùng JSON mẫu hợp lệ cho jobs/results và source/rubric/output. Ghi rõ version và quy tắc resolve bucket/prefix/key.
4. **Bộ chạy lại smoke local:** dữ liệu/tài khoản test theo vai trò, cách chuẩn bị và chạy một job đến kết quả MOCK; dùng dữ liệu giả, không kèm secret. Các bước migration và chuyển file cũ cần trỏ tới script cụ thể.
5. **Danh sách còn chờ phối hợp**, ghi rõ phần BE1, BE2, FE và AI; sau khi BE2 có outputs/quyền thì cùng chạy smoke AWS theo checklist trong báo cáo.

Phần model thật và FE job UI mình ghi nhận là chưa nghiệm thu, không coi là phần bạn đã hoàn tất trong đợt local này.

Các kết quả 591 backend tests, 19 MySQL integration tests và bảo toàn 627 dòng cũ là **kết quả BE1 báo cáo ngày 27/10**, BE2 chưa chạy lại. Trạng thái tiếp nhận hiện tại: **đã nhận báo cáo local, chờ source có revision và kiểm chứng tích hợp**.

## 2. Trạng thái BE2 cập nhật

| Hạng mục | Trạng thái để hai bên phối hợp |
|---|---|
| IAM role cho BE1 | **BE2 đã giao**, theo xác nhận ngày 28/10. BE1 tiếp tục dùng role/cách truy cập đã nhận; không cần chờ cấp lại từ đầu. |
| Quyền IAM có hiệu lực với ứng dụng | Dùng bộ quyền đã trao đổi. Tài liệu này không chép lại policy/ARN chưa có trong nguồn đối chiếu; kiểm tra thực tế bằng đúng identity khi smoke. Việc đã giao role chưa thay thế kết quả gọi S3/SQS từ tiến trình Java. |
| S3/SQS/ECR/log group | Đã có bộ Outputs được ghi nhận trong tài liệu BE2 ngày 27/10, chép đầy đủ tại mục 4. Chưa đọc lại trạng thái AWS trực tiếp trong lượt soạn tài liệu này. |
| EC2 | **Chưa tạo**. Chưa có máy chạy backend, instance profile được gắn vào máy, API endpoint hoặc bản deploy mới. |
| RDS | **Chưa tạo**. Chưa có endpoint, database user/password, CA/trust đã cấu hình hoặc kết quả kết nối RDS. |
| Worker | BE1 báo có mock worker CLI local; người/identity chạy thử AWS cần phối hợp BE2/AI. Model thật chưa tích hợp/nghiệm thu. |
| Smoke AWS đầy đủ | Chưa thực hiện. Có thể chuẩn bị source/contract/local và kiểm tra S3/SQS trước; buổi nghiệm thu trên EC2/RDS thực hiện sau khi runtime sẵn sàng. |

Thông tin IAM đã giao ở trên cập nhật mô tả “chưa có quyền AWS” trong báo cáo BE1 ngày 27/10. Không suy ra rằng EC2/RDS đã có từ việc role đã được giao.

## 3. Lựa chọn kỹ thuật cho đợt tích hợp

### 3.1. Database: RDS for MySQL 8.4

- **Chốt MySQL 8.4** làm target tích hợp, đồng nhất với lab BE1 đã báo cáo. Minor version cụ thể ghi lại khi BE2 tạo RDS và đối chiếu khả dụng tại region.
- Dùng DB PoC riêng; tên catalog dự kiến `fcaj_poc`, BE2 xác nhận giá trị thực khi giao endpoint.
- `lower_case_table_names=1`: cấu hình custom DB parameter group phù hợp MySQL 8.4 **trước khi tạo RDS**, gắn ngay lúc tạo. Không dự kiến đổi tham số này sau khi instance đã khởi tạo.
- Charset `utf8mb4`, collation `utf8mb4_0900_ai_ci`; RDS private, EC2 và RDS cùng VPC, kết nối DB từ backend qua security group phù hợp.
- Migration do Flyway quản lý; Hibernate dùng `validate`. DB mới áp V1 → V2. DB restore dữ liệu cũ phải kiểm schema/backup và baseline V1 có chủ đích trước V2, theo script BE1 bàn giao; không automatic baseline/clean/update.
- BE2 bàn giao sau: endpoint/port/catalog, phiên bản thực, CA/trust, kết nối TLS `sslMode=VERIFY_IDENTITY`, runtime user và migration user. Secret cấu hình/chuyển qua kênh bảo mật riêng.
- `docker-compose.yml` ở checkout BE2 vẫn dùng MySQL 8.0 và cấu hình DB cũ; **chưa được điều chỉnh trong lần bàn giao Markdown này**. Chuẩn bị cấu hình runtime MySQL 8.4 riêng trước khi deploy; không coi Compose hiện tại đã phù hợp target mới.

Tham chiếu: [AWS — lower_case_table_names trên RDS for MySQL](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/MySQL.KnownIssuesAndLimitations.html). AWS ghi nhận giá trị `1` hỗ trợ MySQL 8.4 và phải đặt trước khi tạo instance.

### 3.2. SQS: visibility 120 giây cho hai queue chính

**Chọn `VisibilityTimeout=120` giây cho cả jobs và results trong PoC MOCK.** Đây là mức khởi đầu phù hợp với thông tin hiện có, chưa phải kết quả tối ưu từ benchmark.

- Khớp results consumer BE1 báo đang dùng 120 giây; có thêm thời gian cho thao tác S3/DB và retry so với 60 giây.
- Khi xử lý thành công, consumer xóa message sau bước lưu/gửi cần thiết, không chờ hết visibility. Đánh đổi là khi consumer chết trước ack, message thường phải đợi lâu hơn để được nhận lại so với cấu hình 60 giây.
- Giữ Standard queues, long polling 20 giây, retention queue chính 4 ngày, DLQ 14 ngày và `maxReceiveCount=5` lượt nhận. Không đổi visibility của DLQ trong quyết định này.
- Standard queue vẫn có thể giao trùng; tiếp tục dùng ledger/idempotency và chỉ ack sau khi trạng thái cần thiết đã bền vững.
- Trước model thật: đo thời gian xử lý, chốt deadline/retry và gia hạn visibility nếu cần; khi dùng `ChangeMessageVisibility`, identity xử lý cần action tương ứng.

**Trạng thái áp dụng:** template `infra/cloudformation/poc.yaml` đang đặt 60 giây; tài liệu này **chưa sửa template hoặc cập nhật queue trên AWS**. BE2 đồng bộ default của hai queue chính lên 120 giây và ghi lại cấu hình thực trước smoke. BE1/AI đối chiếu cả override `VisibilityTimeout` trong `ReceiveMessage`, nếu có, để cùng dùng 120 giây.

Tham chiếu: [AWS — SQS visibility timeout](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html). AWS khuyến nghị đặt theo thời gian xử lý, có thể khởi đầu 2 phút khi chưa rõ thời gian và điều chỉnh/gia hạn sau đo đạc.

## 4. Bộ 16 Outputs BE2 bàn giao lại

Nguồn: `backend1-poc-outputs-test-guide.md` ngày 27/10/2026, đã ghi nhận stack `fcaj-poc` đạt `UPDATE_COMPLETE`, đủ 12 resources và 16 Outputs do BE2 cung cấp. **Đây là bản chép lại nguồn ngày 27/10, chưa phải kiểm tra AWS mới ngày 28/10.** Trước khi chạy, đối chiếu lại account/region/tài nguyên; nếu có thay đổi, BE2 cung cấp Outputs mới.

| Output key | Giá trị |
|---|---|
| `BackendLogGroupArn` | `arn:aws:logs:ap-southeast-1:582129522297:log-group:/fcaj/poc/backend:*` |
| `BackendLogGroupName` | `/fcaj/poc/backend` |
| `BackendRepositoryArn` | `arn:aws:ecr:ap-southeast-1:582129522297:repository/fcaj-poc-backend` |
| `BackendRepositoryName` | `fcaj-poc-backend` |
| `BackendRepositoryUri` | `582129522297.dkr.ecr.ap-southeast-1.amazonaws.com/fcaj-poc-backend` |
| `DataBucketArn` | `arn:aws:s3:::fcaj-poc-data-582129522297-ap-southeast-1` |
| `DataBucketName` | `fcaj-poc-data-582129522297-ap-southeast-1` |
| `DeploymentRegion` | `ap-southeast-1` |
| `JobDeadLetterQueueArn` | `arn:aws:sqs:ap-southeast-1:582129522297:fcaj-poc-ai-jobs-dlq` |
| `JobDeadLetterQueueUrl` | `https://sqs.ap-southeast-1.amazonaws.com/582129522297/fcaj-poc-ai-jobs-dlq` |
| `JobQueueArn` | `arn:aws:sqs:ap-southeast-1:582129522297:fcaj-poc-ai-jobs` |
| `JobQueueUrl` | `https://sqs.ap-southeast-1.amazonaws.com/582129522297/fcaj-poc-ai-jobs` |
| `ResultDeadLetterQueueArn` | `arn:aws:sqs:ap-southeast-1:582129522297:fcaj-poc-ai-results-dlq` |
| `ResultDeadLetterQueueUrl` | `https://sqs.ap-southeast-1.amazonaws.com/582129522297/fcaj-poc-ai-results-dlq` |
| `ResultQueueArn` | `arn:aws:sqs:ap-southeast-1:582129522297:fcaj-poc-ai-results` |
| `ResultQueueUrl` | `https://sqs.ap-southeast-1.amazonaws.com/582129522297/fcaj-poc-ai-results` |

URL dùng gọi SQS; ARN dùng giới hạn IAM. ECR URI chưa có tag/digest, không đại diện cho image đã build. Log group tồn tại chưa chứng minh backend đã gửi log vào đó.

## 5. Mapping cấu hình backend/worker

Dùng tên biến theo báo cáo BE1 mới; các tên `S3_BUCKET_NAME`/`SQS_JOB_QUEUE_URL` trong tài liệu cũ là đề xuất trước đây, không dùng thay cho tên dưới đây nếu code BE1 không hỗ trợ.

```dotenv
AWS_REGION=ap-southeast-1
APP_STORAGE_BUCKET=fcaj-poc-data-582129522297-ap-southeast-1
APP_AI_JOBS_JOBS_QUEUE_URL=https://sqs.ap-southeast-1.amazonaws.com/582129522297/fcaj-poc-ai-jobs
APP_AI_JOBS_RESULTS_QUEUE_URL=https://sqs.ap-southeast-1.amazonaws.com/582129522297/fcaj-poc-ai-results
APP_AI_JOBS_ENABLED=false
```

Đoạn trên chỉ là các giá trị không nhạy cảm đã có nguồn, **chưa đủ để khởi chạy profile AWS**:

- `APP_STORAGE_PREFIX` bắt buộc không rỗng. Hai bên ghi lại prefix thực thống nhất theo scope của role đã cấp trước khi bật app/worker; chưa có giá trị được xác nhận trong bộ nguồn này nên không tự đặt một prefix có thể nằm ngoài quyền hiện hữu.
- Ref trong JSON là key logic tương đối, ví dụ `inputs/{jobId}/source.json`; object key thực là `{APP_STORAGE_PREFIX}/{key logic}`. Backend/worker dùng cùng bucket và prefix; không đưa bucket vào message theo fixture cũ, không nhân đôi prefix.
- Namespace ứng dụng gồm `inputs/`, `rubrics/`, `results/`, cùng `problems/` và `avatars/` cho file nghiệp vụ. BE2 phối hợp đối chiếu phạm vi role đã cấp nếu code mới cần thêm namespace/action.
- `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, CA/trust: BE2 giao khi RDS sẵn sàng. `JWT_SECRET`, HTTPS frontend/CORS/cookie domain, OAuth/SMTP nếu test: cấu hình riêng theo `.env.aws.example` BE1 gửi; không ghi secret vào bản bàn giao.
- `APP_BUILD_REVISION` là commit SHA thật được build, chưa có giá trị cho đến khi nhận revision BE1. Khi lên EC2 dùng instance role/default credential chain; role đã giao cho BE1 hiện nay không tự được gắn vào EC2 chưa tạo.
- Chỉ đổi `APP_AI_JOBS_ENABLED=true` trong cửa sổ test khi có worker, credential provider hợp lệ và kiểm soát consumer trên cặp queue đã thống nhất. Phiên Console không tự cấp credentials cho Java local.

Không gửi lại fixture transport cũ vào consumer nghiệp vụ: source/manifest/job/result phải dùng đúng schema BE1 bàn giao ở mục 1.

## 6. Việc làm ngay và trách nhiệm tiếp theo

| Việc | Người phụ trách | Đầu ra cần có |
|---|---|---|
| Commit source riêng, giao revision và file/evidence/contract | BE1 | Link branch/PR, SHA, bộ tài liệu chạy lại theo mục 1 |
| Ghép rename | BE1 + BE2 | Thống nhất `back-end/src/fcaj-api`, package `com.fcaj` với code BE1 đang ở `seal-api/com.seal`; cập nhật nhất quán build/test/script trước build chung |
| Kiểm tra AWS từ backend local trên DB MySQL 8.4 riêng | BE1 + BE2 | Credential provider dùng role đã giao, prefix khớp quyền, log S3/SQS theo jobId; chỉ chạy khi thống nhất cửa sổ/cặp queue |
| Đồng bộ queue visibility | BE2; BE1/AI đối chiếu receive override | Hai queue chính 120 giây; ghi lại cấu hình thực, không sửa queue ngoài phạm vi test |
| Dựng RDS MySQL 8.4 và EC2 | BE2, đợt hạ tầng tiếp theo | DB private, endpoint/catalog/CA, network, runtime identity, cấu hình deploy và API HTTPS |
| Migration | BE1 cung cấp/chạy theo phối hợp BE2 | DB mới V1 → V2, hoặc restore rehearsal/baseline có kiểm tra; không chạy reset/seed trên dữ liệu dùng chung |
| Mock worker AWS | BE1 bàn giao CLI; BE2/AI chốt người/identity chạy | Cùng region/bucket/prefix/queue pair; output ghi rõ MOCK |
| FE dùng API job | FE + BE1 | Contract được thống nhất; không tự retry POST tạo job vì HTTP idempotency key chưa chốt |
| Model thật | AI + BE1 + BE2 | Contract/model version, timeout, benchmark và chính sách output được review riêng |

**Thứ tự tiếp theo:** BE1 gửi revision/bộ bổ sung → hai bên chốt prefix và credential provider theo role đã giao → kiểm tra S3/SQS có kiểm soát, có thể dùng DB local MySQL 8.4 riêng → BE2 hoàn thiện EC2/RDS và đồng bộ cấu hình runtime → cùng smoke AWS.

## 7. Điều kiện và kết quả smoke cần ghi lại

1. Ghi account/region, commit SHA/image digest, DB target, prefix, queue pair, role thực sử dụng và người chạy worker. Hai bên kiểm tra Git root/branch/HEAD/status trước khi dùng bằng chứng cũ.
2. Sau khi deploy, kiểm `/health/live`, `/health/ready`, `/health/version`; readiness chỉ kiểm DB theo báo cáo BE1, không chứng minh worker online.
3. Chuẩn bị dữ liệu giả và quyền hợp lệ: user approved, coordinator đúng event hoặc judge assigned, round ACTIVE, timer JUDGING RUNNING, rubric/submission và repo public nhỏ được đồng ý.
4. Upload/read/replace đề thi; upload/đổi/xóa avatar và đọc lại sau restart. Kiểm wrong-event/unreleased/wrong-track theo checklist BE1.
5. `POST /api/ai/submissions/{id}/jobs` → 202; job đi từ `PENDING_DISPATCH` sang `QUEUED`. Chạy mock worker một lượt với `SUCCEEDED`, poll `GET /api/ai/jobs/{uuid}`, rồi `GET /api/ai/jobs/{uuid}/result`; kết quả ghi rõ MOCK, không đổi điểm chính thức. Trạng thái có thể đi nhanh đến terminal nên đối chiếu thêm log/DB thay vì bắt buộc quan sát mọi trạng thái qua polling.
6. Thử fail có chủ đích trên job mới, duplicate/retry/restart và DB lỗi trước ack; kiểm message lỗi/redrive/DLQ trong cửa sổ riêng. Không dùng Purge, reset DB hoặc replay DLQ chưa xác định nguyên nhân để dọn bài test.
7. Lưu evidence đã lọc: thời điểm, jobId/requestId, test data ID, trạng thái DB, S3 key/version/checksum, message ID, log liên quan và lỗi còn lại/owner. Không ghi token, credential, raw source hoặc raw output nhạy cảm.

Đạt smoke MOCK trên AWS mới ghi nhận kết quả tích hợp tương ứng. Model thật, browser OAuth/SMTP và FE job UI có checklist riêng; kết quả local/transport không tự thay thế nghiệm thu các phần này.

## 8. Nguồn và revision để đối chiếu

- Repo BE2 trên máy soạn: `D:/Documents/AWS/fcaj-hackathon-system`; branch `feature/aws-poc-infra`; HEAD `93c3cbab5b7e42cc4a19e2e7b6eeaf45913d91b4` ngày 28/10. Đây là HEAD checkout, **không phải SHA chứa code BE1 hay toàn bộ hạ tầng chưa commit**.
- Trước khi soạn, working tree có `.gitignore` sửa và `docs/A-runtime-overview.jpg`, `docs/B-ai-review-flow.jpg`, `docs/C-cicd-operations.jpg`, `infra/` chưa track. Giữ nguyên các thay đổi đó; lượt này chỉ thêm file Markdown này.
- Nguồn BE1: `backend1-local-completion-2026-10-27.md`, bản đã gửi BE2; trên máy soạn là `D:/Downloads/backend1-local-completion-2026-10-27.md` (đường dẫn local, không phải link portable). BE1 ghi branch `codex/backend1-foundation`, SHA nền `f42a168d9cb0613a43801edca632a2de64837099`, còn code chưa commit; cần SHA mới theo mục 1.
- Nguồn Outputs: `docs/vq/backend1-poc-outputs-test-guide.md`; nguồn DB trước đó: `docs/vq/backend1-ai-database-seal-fcaj-handoff.md`. Hai tài liệu này nằm trong `docs/vq/` bị Git ignore, không mặc định có trên máy BE1; các giá trị và kết luận cần thiết đã chép vào file này.
- Nguồn cấu hình hiện tại: `infra/cloudformation/poc.yaml`, `infra/cloudformation/README.md`, `docker-compose.yml`, `.github/workflows/deploy.yml`.
- Xác nhận mới ngày 28/10 từ BE2: IAM role đã giao BE1; EC2/RDS chưa làm; chọn MySQL 8.4 và giao quyền lựa chọn visibility cho đợt PoC. Lựa chọn 120 giây được ghi ở mục 3, chưa phải thay đổi AWS đã áp dụng.

File này có thể gửi độc lập cho BE1, không chứa secret và không đóng gói source/evidence. Việc gửi file không tự thực hiện commit, push, deploy hoặc thay đổi tài nguyên AWS.
