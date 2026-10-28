# Backend 1 — cấu hình và outputs tích hợp

Cập nhật: 27/10/2026, Asia/Saigon. Người dùng xác nhận chưa có outputs BE2/AI. Không điền secret trong file này.

## Cấu hình foundation ứng dụng

| Biến / cấu hình | Khi cần | Nguồn / người cấp |
|---|---|---|
| SPRING_PROFILES_ACTIVE | Chọn local, test hoặc aws; OAuth opt-in theo provider | BE1 định nghĩa, BE2 runtime |
| SPRING_DATASOURCE_URL / USERNAME / PASSWORD | AWS; test dùng H2 riêng | BE2 DB/secret; dùng tên Spring chuẩn |
| DB_USERNAME / DB_PASSWORD | Alias tương thích cấu hình dev; giá trị Spring chuẩn ưu tiên | Local/BE2 nếu dùng alias |
| DDL_AUTO | AWS chốt rõ update/validate/none; validate chỉ khi schema đã có | BE1 + BE2 |
| JWT_SECRET | AWS bắt buộc Base64 ít nhất 32 bytes; local/test có key phát triển riêng | Runtime secret owner |
| APP_FRONTEND_URL | Frontend origin; AWS HTTPS | Lead/FE/BE2 |
| APP_CORS_ALLOWED_ORIGINS | Allowlist; AWS chỉ các origin HTTPS đã chốt | FE/BE2 |
| JWT_COOKIE_SECURE / CSRF_COOKIE_DOMAIN | HTTPS và domain dùng chung sau khi chốt topology | BE1/BE2 |
| BOOTSTRAP_ADMIN_ENABLED | Mặc định false mọi môi trường | Owner triển khai bật có chủ đích |
| BOOTSTRAP_ADMIN_EMAIL / PASSWORD | Chỉ bắt buộc khi bootstrap bật | Secret owner; không commit |
| SEED_SCENARIO | Mặc định NONE; demo opt-in | Local demo hoặc PoC được thống nhất |
| GOOGLE_CLIENT_ID / SECRET | Chỉ khi bật oauth-google | Owner OAuth |
| GITHUB_CLIENT_ID / SECRET | Chỉ khi bật oauth-github | Owner OAuth |
| GEMINI_API_KEY / GITHUB_TOKEN | Chỉ cho tính năng đồng bộ hiện tại nếu sử dụng | Owner AI/GitHub |
| MAIL_* | Email/OTP thật khi nghiệm thu | Owner mail |

Tên biến cuối cùng và ví dụ chạy xem `.env.example`/`.env.aws.example` trong backend. S3/SQS chưa được implement trong foundation; các tên dưới đây là outputs cần thống nhất, chưa phải property đã hoạt động.

## Outputs còn chờ

| Output | Owner | Trạng thái / tiêu chí |
|---|---|---|
| Region | BE2 | Pending; proposal dự kiến ap-southeast-1 |
| RDS version/minor, endpoint/port/database | BE2 | Pending; dự kiến MySQL8.4, fcaj_poc |
| DB user/secret reference, CA/TLS/network | BE2 | Pending; không gửi mật khẩu trong tài liệu |
| Schema setup/runtime DDL policy | BE1 + BE2 | Pending; tiếp tục Hibernate/entity, không tự thêm Flyway |
| S3 bucket, prefixes, versioning/retention | BE2 | Pending; inputs/rubrics/results dự kiến, đề thi có prefix riêng |
| Jobs/results queue URLs + DLQs | BE2 | Pending; xác nhận standard queue/delivery contract |
| App runtime identity + IAM actions/prefixes | BE2 + BE1 | Pending; instance role cho EC2 |
| Worker identity + IAM actions/prefixes | BE2 + AI | Pending; worker không nhận DB credentials |
| Local test identity/queue/prefix hoặc lịch cô lập | BE2 + BE1 + AI | Pending trước send/receive/delete thật |
| Worker mock owner/runner/lịch online | AI + BE2 | Pending; đề xuất xử lý một job/lần |
| Source format, repo/SHA, input limits | AI + BE1 | Pending; fixture hiện synthetic, không chạy code bài nộp |
| Contract fixtures/DTO/error sign-off | FE + AI + BE2 | Pending; [bộ mẫu để review](../contracts/ai-job-v1/README.md) |
| Timeout/late completion/retry policy | AI + BE1 + BE2 | Pending trước tích hợp thật; mock giữ pending |
| API URL/log group/health route | BE2 | Pending |
| Image commit/digest/release/rollback | BE2 + BE1 | Pending; build/test local không chứng minh deploy |
| Ngân sách/lịch tài nguyên và mốc demo | Lead + BE2 + người dùng | Pending |

M0 chỉ đạt sau vòng mock thật với bằng chứng quyền, file/state bền sau restart, duplicate/stale/race và logs. Không dùng Console poll queue chung để dò khi chưa có ca test cô lập được thống nhất.
