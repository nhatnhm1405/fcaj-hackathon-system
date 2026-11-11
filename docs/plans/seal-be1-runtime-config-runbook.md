# SEAL BE1 — Cấu hình runtime và phương án ALB để review

Ngày 03/11/2026. Người dùng xác nhận **chưa có đầu vào mới từ BE2; các phương án hạ tầng vẫn là ý tưởng thảo luận**. Bộ này chuẩn bị local, chưa áp dụng AWS. ECR pipeline hoãn; không tự bổ sung SSM/deploy pipeline thay thế.

## 1. Chọn đúng template

| File trong `back-end/src/seal-api/deploy/` | Dùng khi | Cần xác nhận |
|---|---|---|
| `compose.aws.example.yml` | Backend sau reverse proxy local, bind `127.0.0.1:8080` | Proxy/routing/forwarded headers và TLS do operator quản lý |
| `compose.aws.alb-direct.example.yml` | **Đề xuất** ALB → private IP EC2 HTTP:8080, FE ở S3 riêng | BE2 xác nhận port/protocol/TLS, interface thật qua `DEPLOY_TARGET_BIND_IP`, SG target chỉ nhận SG ALB |
| `deploy-settings.sh.example` | Mẫu đường dẫn private cho operator; gợi ý `/etc/seal/` | Không move file thật; BE2 chọn paths và template Compose tương ứng |
| `migration.env.example` | Identity migration riêng và truststore JVM | URL phải trùng runtime URL trước ghi; credentials ngoài Git |
| `worker.env.example` | Worker MOCK một message trong window | Bucket/prefix/queues/region/identity/window, không DB/JWT |

Không ghép hai Compose templates với nhau. Template direct bắt buộc khai báo IP interface mà ALB tới được; loopback không phải direct target. Mẫu không tạo ALB/subnet/SG/TLS và không mở EC2 public. Root Compose/Caddy vẫn là stack dev riêng.

Deploy gate dùng đúng candidate để initialize/verify rồi chỉ gọi `up -d --no-deps --no-build seal-backend`; không thay FE, DB hay proxy. Các dependency cần có do operator chuẩn bị trước. Workflow SSH cũ đã được ghi chú chưa nghiệm thu đường từ GitHub-hosted runner tới EC2 private; ALB không cung cấp đường SSH.

## 2. Biến theo nhóm

| Nhóm | Biến / điều kiện |
|---|---|
| Boot/DB | `SPRING_PROFILES_ACTIVE=aws`, datasource URL/user/password, MySQL8.4, V3, case1, utf8mb4/utf8mb4_0900_ai_ci, JDBC `sslMode=VERIFY_IDENTITY` |
| JVM TLS | Private `JAVA_TOOL_OPTIONS` trỏ `/run/secrets/rds-truststore.p12` và private password; CLI/runtime cùng mount readonly. Xem migration env mẫu; không ghi password vào command/evidence |
| Auth | `JWT_SECRET` Base64 ≥32 decoded bytes, `JWT_COOKIE_SECURE=true`, session Secure; không đổi secret mỗi restart |
| FE | HTTPS `APP_FRONTEND_URL`, optional `APP_CORS_ALLOWED_ORIGINS`, `CSRF_COOKIE_DOMAIN`; FE build `VITE_API_URL`. Domain/CloudFront/TLS chưa chốt |
| Storage, kể cả jobs tắt | `AWS_REGION`, `APP_STORAGE_BUCKET`, `APP_STORAGE_PREFIX` vẫn bắt buộc vì profile aws chọn S3 |
| Job API | `APP_AI_JOBS_ENABLED=true` chỉ khi thử; jobs/results queue URLs riêng, cùng bucket/prefix với worker |
| Delivery | Mẫu để `APP_AI_JOBS_DELIVERY_ENABLED=false`: cố ý tắt dispatcher **và** result poller; POST vẫn có thể tạo pending job. Bật `true` trong window phối hợp |
| Bootstrap | Mặc định false, bật có chủ đích với private email/password, tắt sau provisioning; không đổi account/grant đã tồn tại |
| Seed | `SEED_SCENARIO=NONE`; không dùng reset/demo với RDS |
| OAuth | Chỉ thêm profile oauth-google/github khi có credentials/callback đã đăng ký; chưa bật thì không cần credentials |
| Mail | SMTP không bắt buộc boot; các luồng gửi email cần MAIL_HOST/PORT/USERNAME/PASSWORD. Không thêm toggle tắt mail hoặc kết luận email đã hoạt động |
| Worker | Region/bucket/prefix/queue pair và default credential provider có scope riêng; không DB/JWT, không cần model thật |
| Revision | `APP_BUILD_REVISION` lấy từ candidate commit; working-tree local chưa có release SHA mới |

`server.forward-headers-strategy=framework` đã có ở source; không thêm setting trùng. Cookie/CSRF config local được kiểm, nhưng scheme/host/redirect qua ALB thật phải kiểm riêng. BE2 hạn chế quyền gửi trusted forwarded headers bằng mạng/SG. Nếu có proxy trung gian, review việc preserve headers để tránh redirect loop.

ALB health đề xuất `/health/ready` chỉ kiểm DB; kiểm thêm `/health/live` và `/health/version`. Routing phải đưa đúng API/health, `/uploads/avatars/*`, và `/oauth2/*`, `/login/oauth2/*` nếu bật OAuth tới backend. Không route chỉ `/api/*` rồi bỏ ảnh/callback. FE S3 HTTPS/CloudFront/OAC và FE/API domains vẫn chờ thiết kế; không coi domain ví dụ là đã cấp.

## 3. Network cần BE2 xác nhận

| Đường đi | Căn cứ source / nhu cầu | Bằng chứng trên target cần có |
|---|---|---|
| ALB → EC2 private:8080 theo đề xuất | Compose direct publish private interface; backend nhận HTTP nếu TLS ở ALB | Port/protocol/listener/TLS/SG/target health đúng |
| Backend → RDS TCP3306 | JDBC/runtime/migration CLI | DNS/routing/SG và JVM VERIFY_IDENTITY với CA thật |
| Backend → `api.github.com` HTTPS443 | `GitHubSourceReader` resolve commit/tree/blob; public repo nhỏ | Collector từ container thật; S3/SQS endpoints không thay đường public GitHub |
| Backend/worker → S3 HTTPS443 | `S3ObjectStorage` SDK | Provider/role, prefix và put/read/delete đúng scope |
| Backend/worker → SQS HTTPS443 | Outbox/worker/results consumer | Send/receive/delete đúng queue pair và window |
| Operator → private EC2 | Đường quản trị/deploy BE2 chọn | Session Manager hoặc đường hiện hữu phải được chốt; không tự giả định SSH từ Actions có route |
| SSM services nếu BE2 chọn | Nhu cầu vận hành, không phải API ứng dụng gọi SSM | BE2 cung cấp routing/endpoints/agent/role và cách chạy CLI/log |
| Build trên EC2 nếu được chọn | Git source, Maven distribution/dependencies và Docker base images | DNS/HTTPS egress tới các registry/repositories thực của build |
| SMTP/OAuth nếu bật | Feature tùy chọn | SMTP port theo cấu hình, provider HTTPS/callback đúng, nghiệm thu riêng |

NAT/proxy/endpoints là lựa chọn BE2; endpoint policy không thay IAM/resource policy. Default AWS credential chain được dùng bởi SDK, nhưng role của host không tự chứng minh container lấy được credentials. Không gửi access keys/private env trong phản hồi.

## 4. Action/resource matrix từ code

Đây là nhu cầu để review policy, **không phải policy đã áp dụng**. Object thật là `{APP_STORAGE_PREFIX}/{logicalKey}`; không nhân đôi prefix hoặc đổi namespace `fcaj.ai.*` khi rename Java.

| Identity | S3 theo prefix/key | SQS |
|---|---|---|
| Backend | `s3:PutObject`, `s3:GetObject`, cleanup `s3:DeleteObject` cho avatars/problems và inputs/rubrics; đọc results; versioned refs cần action version tương ứng (`GetObjectVersion`, cleanup `DeleteObjectVersion`) | `sqs:SendMessage` jobs; `sqs:ReceiveMessage`, `sqs:DeleteMessage` results |
| Worker | Đọc inputs/rubrics, `s3:PutObject` và `s3:GetObject` results để reuse output bất biến; đọc version nếu refs có version | `sqs:ReceiveMessage`, `sqs:DeleteMessage` jobs; `sqs:SendMessage` results |
| Migration | Không cần S3/SQS để chạy CLI DB | Không cần |

Chỉ cấp actions/resources cho vai trò thực; BE2 review KMS nếu bucket/queue mã hóa theo key riêng. Source không dùng ListBucket/PurgeQueue để tạo job; không thêm quyền đó để làm smoke thuận tiện. Code receive override visibility120s, long poll20s, mỗi lượt một message; outbox lease300s là cơ chế khác. BE2 đối chiếu queue defaults/DLQ/backlog/consumer trước window.

## 5. Parse local và các phần còn pending

Từ repository root:

```text
node back-end/src/seal-api/Test/verify-deploy-templates.mjs
```

Verifier chỉ gọi `docker compose config`, dùng synthetic env/truststore dưới target, không in expanded config và không gọi `run/up`. Kiểm candidate/profile/revision/JDBC URL, raw env giữ literal dollar/quotes, binding đúng từng topology, truststore readonly và thiếu bind IP phải reject.

Compose2.30+ cần cho `env_file.format: raw`; parse pass không chứng minh truststore thật, credentials/container role, TLS RDS, target healthy hoặc browser login. Những phần này, live S3 file sau restart, job success/failure và source/image mapping phải do operator kiểm trên target trong window đã chốt.

Probe env riêng đã chạy trên image local bằng `Test/verify-container-env.ps1 -CandidateImage <local-image>`: Java nhỏ kiểm giá trị giả có dollar/quotes, network container tắt, không khởi động backend. Probe này bổ sung cho parse config (Compose có thể escape dollar khi serialize), không thay thử credentials/JVM TLS thật.
