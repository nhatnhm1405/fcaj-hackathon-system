# Bàn giao BE1: migration, storage và AI jobs

Ngày triển khai bắt đầu 27/10/2026. Phạm vi được người dùng chốt: hoàn tất code và test local trước; chưa có outputs/quyền AWS từ BE2. Những tài liệu bàn giao cũ là nguồn context, không phải xác nhận hệ thống đã deploy hoặc cho phép thay dữ liệu dùng chung.

## Kết quả triển khai

| Phần | Đầu ra trong checkout |
|---|---|
| Migration | Local/AWS dùng Flyway và Hibernate `validate`; cấm automatic baseline/clean/update khi bật migration. V1 giữ nguyên, V2 thêm `AiJob`, `AiJobOutbox`, `AiProcessedEvent`. |
| Dữ liệu cũ | Restore `.bak` trên MySQL8.4 riêng; baseline V1 có đối chiếu cấu trúc và backup; V2 giữ nguyên fingerprint của cả 627 dòng cũ, thêm ba bảng rỗng. Không sửa dump hay DB đang dùng. |
| Object storage | Local/S3 adapters, key riêng, bounded reads, SHA-256/version references, PutObject bất biến với `If-None-Match: *`. Bucket/region/prefix được cấu hình. |
| Đề thi | Upload/download qua storage, coordinator đúng event, participant đúng track/đội APPROVED và đã phát đề. Chỉ dọn file cũ sau commit; rollback giữ file cũ. |
| Avatar | Upload/read/replace/delete qua storage; giữ URL `/uploads/avatars/...`, hỗ trợ local legacy, kiểm loại ảnh thực, không nhận SVG/HTML. Avatar provider bên ngoài được giữ khi phù hợp. |
| Snapshot | Public GitHub cố định host HTTPS, không redirect; resolve commit SHA đầy đủ → tree SHA → kiểm blob SHA. Đọc text có giới hạn, không chạy code; ghi source/rubric/manifest bất biến. |
| Job API | Ba API thật, cùng DB nghiệp vụ; quyền theo event/assignment/requester và approval hiện tại. Snapshot ngoài transaction, lock/recheck dữ liệu/quyền trước commit job+outbox. |
| Dispatcher | Claim có lease, backoff, messageId bền, gửi thành công mới mark sent/QUEUED; không hạ terminal nếu completion về trước. |
| Consumer | Thread long-poll riêng; kiểm schema/identity/checksum/provenance/rubric/evidence; ledger và state commit nguyên tử trước DeleteMessage. Duplicate chính xác có thể ack từ ledger khi S3 tạm lỗi; conflict không ghi đè. |
| Mock worker AWS | CLI xử lý một message/lượt, kiểm artifacts, ghi output, gửi completion rồi ack jobs. Output và messageId xác định để retry; không tự chạy cùng backend. Chưa gọi AWS thật. |
| JWT/OAuth | Giữ JWT cookie HttpOnly/Secure trên AWS, CSRF/CORS; kiểm quyền hiện tại. Google yêu cầu email verified, chặn thay provider/subject đã liên kết, email bên thứ ba cần bước link riêng; giữ avatar tự upload. |
| Vận hành | `X-Request-ID`, lỗi ngoài dự kiến không trả SQL/token/stack trace; health live/ready/version; log jobId/attempt, không log source/raw output. |
| CI | Bổ sung test MySQL8.4 vào job backend của workflow đang có; không tạo pipeline deploy song song. CI remote chưa được chạy trong lượt này. |

Checkout vẫn ở `codex/backend1-foundation`, nền `f42a168d9cb0613a43801edca632a2de64837099`, còn thay đổi local chưa commit. Không thể dùng SHA nền để đại diện cho image chứa toàn bộ thay đổi này. BE2 handoff đang dùng `fcaj-api/com.fcaj`; checkout này là `seal-api/com.seal`. Cần thống nhất revision/package/path trước build/deploy chung.

## Cách kiểm thử local

Chạy từ `back-end/src/seal-api`, Java21:

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21.0.10'
./mvnw.cmd -B '-Dspring.profiles.active=test' test
./Test/run-ai-mysql-tests.ps1
./Test/schema/run-dump-lab.ps1 -DumpPath 'E:/FA26/AWS/seal_hackathon.bak' -JdkHome $env:JAVA_HOME
```

Hai script Docker luôn tạo server mới chỉ bind loopback, không nối DB local/RDS đang dùng; dừng và giữ container để xem lại. Dump và statement chứa dữ liệu chỉ nằm ở nguồn/`target/` bị Git ignore. Không dùng `run-demo.ps1` để chuyển dữ liệu cũ: đó vẫn là công cụ reset demo được gọi chủ đích.

Test tích hợp chạy API/JWT/JPA/JDBC và V1/V2 thật trên MySQL8.4; S3/SQS/GitHub dùng mocks. Test local H2 chạy thêm các ca storage/worker/schema/error. Việc này chưa chứng minh IAM, TLS RDS, queue redrive, S3 versioning hoặc network GitHub ngoài máy.

## Migration DB mới và DB có dữ liệu

DB mới: BE2 tạo catalog MySQL8.4 với `lower_case_table_names=1`, utf8mb4/utf8mb4_0900_ai_ci trước; cung cấp migration identity và CA trust. Flyway áp V1 rồi V2, Hibernate chỉ validate. Không sửa migration đã apply, không chạy `repair` để bỏ qua checksum.

DB có dữ liệu: backup có thể restore → restore rehearsal vào target riêng → đối chiếu cấu trúc/count/FK/unique/file references → baseline **1** → migrate V2. `.bak` không chứa các file vật lý; phải chuyển riêng file nếu DB có key/avatar local. Bản dump đã nhận không có problem object key; không tự suy ra các file tồn tại.

Runner dưới đây không load `.env`; nhận biến môi trường do operator cung cấp qua kênh bảo mật:

```powershell
# Set BE1_MIGRATION_JDBC_URL / BE1_MIGRATION_USERNAME / BE1_MIGRATION_PASSWORD externally.
./Test/run-db-migration.ps1 -Action inspect -Catalog fcaj_poc

# Only after checking that the backup belongs to this exact target:
# BE1_MIGRATION_BACKUP_PATH = path to the backup file
# BE1_MIGRATION_BACKUP_SHA256 = SHA-256 of that file
# BE1_MIGRATION_BACKUP_CATALOG = fcaj_poc
./Test/run-db-migration.ps1 -Action baseline-v1 -Catalog fcaj_poc
./Test/run-db-migration.ps1 -Action migrate -Catalog fcaj_poc
```

`baseline-v1` chỉ ghi marker, chưa áp V2. Runner yêu cầu MySQL8.4/case1, đúng catalog, cấu trúc V1 đã kiểm (columns/defaults/collation/index/FK/check/trigger) và backup hash; từ chối schema lạ. Không kiểm được chỉ từ tên file rằng backup thuộc target: operator phải xác nhận việc này. Không khởi chạy baseline tự động khi ứng dụng boot. Dừng ghi trong cửa sổ baseline/cutover; backup được tạo/restore trước ngoài runner. Với DB8.0 hiện hữu, restore sang target8.4 riêng trước.

V1 SHA-256 `91a3b9b0cb4d41ce5027fa850200848126bdfe57ba0ceef97defa1ff7928ea9d`, Flyway checksum `-732525868`.

V2 SHA-256 `4e40d1f4370c2324e90cd5d1c7e7396c2ce068e2331682421fb5f1e0a3aa4ea7`.

V1 structural fingerprint `6a18f5a3f982daab7257cc0a72441840d64a5c171be51835b10ee79c14466c6e`. Tên index/constraint không làm đổi fingerprint; các cột trong index, unique/FK/rules có tham gia.

## Cấu hình BE2 cần giao

Dùng `.env.aws.example` làm danh sách biến, không commit giá trị thật. `aws` không import `.env` local.

- `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, RDS CA/trust và catalog; TLS `sslMode=VERIFY_IDENTITY`, không nhúng credential trong URL. Migration user cần DDL; tài khoản runtime/quy trình migration phải chốt cùng BE2.
- `JWT_SECRET` Base64 tối thiểu32 byte; HTTPS frontend/CORS origins, cookie domain nếu dùng subdomain, reverse proxy tin cậy. Backend không mở trực tiếp ra Internet khi tin forwarded headers.
- `AWS_REGION`, `APP_STORAGE_BUCKET`, `APP_STORAGE_PREFIX` **không rỗng**, cùng mapping giữa backend và worker.
- `APP_AI_JOBS_JOBS_QUEUE_URL`, `APP_AI_JOBS_RESULTS_QUEUE_URL`, hai standard queues khác nhau cùng region; DLQ/redrive/retention do BE2 cấu hình.
- `APP_AI_JOBS_ENABLED=true` chỉ sau khi có worker và lịch/queue riêng. Mặc định `false`; S3 vẫn dùng cho file trên AWS. Không kết hợp với profile `mock-demo`.
- Runtime identity qua EC2 role/default credential chain. Không đưa access key vào source, message hoặc chat. `APP_BUILD_REVISION` phải là SHA thật của source trong image.

Ref trong JSON là **key logic tương đối** như `inputs/{jobId}/source.json`; key S3 thực là `{APP_STORAGE_PREFIX}/{key logic}` (không nhân đôi dấu `/`). Bucket không nằm trong message. Đây là điểm BE2/AI phải dùng cùng cách resolve.

| Identity | S3 object actions, scope | SQS actions, scope |
|---|---|---|
| Backend | GetObject/GetObjectVersion trên prefix cần đọc; PutObject trên problems/avatars/inputs/rubrics; DeleteObject trên problems/avatars cho cleanup. Get results. | SendMessage jobs; ReceiveMessage/DeleteMessage results. |
| Mock/AI worker | GetObject/GetObjectVersion inputs/rubrics; PutObject results, GetObject results để kiểm retry bất biến. | ReceiveMessage/DeleteMessage jobs; SendMessage results. |

Không cần ListBucket hay quyền tạo bucket/queue trong code này. Nếu bucket dùng customer-managed KMS key, BE2 cấp thêm KMS permissions phù hợp. Bucket private, HTTPS, encryption/versioning/lifecycle và DLQ alarm là cấu hình hạ tầng; chưa xác minh tại đây. Không đặt lifecycle xóa artifacts còn được tham chiếu bởi job. Cleanup orphan do thất bại trước commit cần đối chiếu tham chiếu DB; không xóa toàn prefix theo tuổi một cách mù quáng.

## Smoke test với BE2 sau khi có outputs

1. Chốt cùng revision, DB/prefix/queues riêng và người chạy worker; triển khai image bằng quy trình BE2.
2. Kiểm `/health/live`, `/health/ready`, `/health/version`; readiness chỉ kiểm DB, không khẳng định worker đang online.
3. Login user approved, coordinator đúng event hoặc judge đang assigned; tạo ACTIVE round/JUDGING timer RUNNING, rubric và submission của repo GitHub public nhỏ được đồng ý.
4. Upload/read/replace một file đề thi, kiểm wrong-event/unreleased/wrong-track. Upload/đổi/xóa avatar, restart backend rồi đọc lại.
5. `POST /api/ai/submissions/{id}/jobs` trả202 PENDING_DISPATCH; backend gửi queue rồi QUEUED. POST retry hiện tạo job khác; FE không tự retry POST mù quáng, idempotency key HTTP chưa được chốt.
6. Dùng identity worker riêng và cùng region/bucket/prefix/queue pair, chạy `./Test/run-aws-mock-worker.ps1 -Outcome SUCCEEDED`. Script chỉ lấy một message. Khi muốn thử fail có chủ đích dùng `FAILED` cho một job mới.
7. Poll `GET /api/ai/jobs/{uuid}`, sau SUCCEEDED gọi `/result`. Kết quả ghi rõ MOCK; không thay Score/RoundResult chính thức.
8. Kiểm duplicate/retry/restart, DB lỗi trước ack, completion cũ, wrong revision/hash, revoke assignment/approval. Message sai để queue redrive/DLQ, không biến job hợp lệ thành FAILED. Không replay DLQ khi chưa sửa nguyên nhân hoặc chưa cô lập queue.

Ba API dùng ApiResponse hiện có. Pending result trả409 `RESULT_NOT_READY`; fail có chủ đích409 `MOCK_PROCESSING_FAILED`; hidden job404; create sai quyền403; đóng judging409. FE chỉ nhận allowlist insight hiện có; không nhận requester, source/manifest, S3 refs, repo URL, provenance hoặc evidence path. Free text vẫn cần review chính sách ẩn danh trước model thật.

## Giới hạn và phần phụ thuộc bên ngoài

- Release này chấp nhận **MOCK output**, modelConfig/prompt/processing version mặc định mock; chưa phải tích hợp model thật/benchmark. Source format `fcaj.ai.source.github.v1` cần AI/BE2 review. Fixture cũ là minh họa, không được gửi như job thật.
- Snapshot: public github.com canonical, default branch tại lúc resolve, full commit SHA; tối đa2000 tree entries,40 file text,100KB/file,1MB text; response tree1MB/blob150KB; tổng fetch có budget45s (một request đang chạy còn chịu timeout15s). Không private repo, submodule, Git LFS, binary hay chạy build/script. Một số thư mục/file bị loại; chưa phân tích toàn bộ repo lớn. GitHub không token có rate limit; lỗi fetch trả safe code và không tạo job.
- Outbox lease300s; một worker/poller message mỗi lần; results visibility120s, S3 output tối đa256KB, message32KB, DB transaction timeout30s; adapter AWS call timeout40s. Chốt tải/retry/deadline và late-result policy trước model thật. Pending không tự hết hạn; không tự có RUNNING/EXPIRED.
- Google cần client credentials, callback domain/TLS và browser E2E. Code/test không thay được bước này. Email bên thứ ba không có Google Workspace/Gmail authoritative claim cần link riêng; chưa có UI/OTP link-account hay đổi email provider. JWT có expiry/signature/cookie và kiểm quyền DB; chưa bổ sung refresh-token/revocation-list flow.
- SMTP cần cấu hình và test gửi mail thật. FE job UI, AI worker/model thật, outputs/IAM/RDS/EC2/ECR/DNS/TLS và nghiệm thu AWS còn do các bên phối hợp. Chưa commit/push/deploy hoặc gửi tin cho BE2.

## Nguồn kỹ thuật

- [AWS SDK Java2 SQS send/receive/delete](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-sqs-messages.html)
- [GitHub Git trees](https://docs.github.com/en/rest/git/trees)
- [Google identity claims](https://developers.google.com/identity/openid-connect/openid-connect)

## Kết quả cuối

**591 backend tests đạt**,0 fail/error/skipped và package thành công lúc23:07:33+07:00 ngày27/10. **19 integration tests đạt trên MySQL8.4 thật**, S3/SQS/GitHub được mock; restore V1/baseline/V2 giữ nguyên cả627 dòng cũ. Thêm7 parser tests,7 JSON fixture checks và19 demo-preflight checks đều đạt. Cú pháp script PowerShell/Bash và `git diff --check` đạt; CI remote chưa chạy.

JAR tại `back-end/src/seal-api/target/hackathon-0.0.1-SNAPSHOT.jar`, SHA-256 `c352fee336dbedec34e5ee237a87063885cff4bfbf7aa26ebb339e08e3da3d5a`. Đây là artifact local chưa có release commit/image digest; không tự push/deploy. Artifact thường không chứa H2 runtime; profile Maven `mock-demo` vẫn là lựa chọn demo riêng.

Xem [evidence đã lọc](../checks/schema-inventory-2026-10-27/mysql84-v2-lab-evidence.md) để đối chiếu phiên bản, checksum, lab và các ca kiểm thử. Tất cả container lab đã dừng. Công việc tiếp theo cần inputs BE2 là ghép revision, cấu hình/quyền và buổi smoke AWS/browser; model thật/FE job UI chưa được nghiệm thu.
