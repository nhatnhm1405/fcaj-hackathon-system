# Kế hoạch sửa backend khi đổi domain và chuyển AWS

Ngày: 27/10/2026, Asia/Saigon. Source được rà: `f42a168` và các cấu hình trong working tree. Người dùng xác nhận **chưa có domain mới**. Tài liệu là kết quả kiểm tra và kế hoạch; chưa sửa code, cấu hình chạy, tài nguyên hoặc tài khoản bên ngoài.

**Đính chính cùng ngày:** schema hiện được Hibernate tạo/cập nhật từ Java entity; SQL cũ không thuộc luồng khởi động đã rà. Kế hoạch mặc định tiếp tục code-first. Flyway/baseline migration là phương án tương lai nếu nhóm chọn, không phải việc bắt buộc do SQL cũ lệch entity.

## 1. Giả định dùng để lập kế hoạch

Theo proposal: frontend static trên S3 private + CloudFront; browser gọi API trực tiếp; API Spring Boot Docker trên EC2 sau Caddy; RDS MySQL 8.4 là phương án đích chờ lab; application files trên S3 private. SQS/worker là phần AI bổ sung theo phân công, không phải điều kiện kỹ thuật của riêng việc đổi domain.

Các placeholder:

| Tên | Ý nghĩa | Ví dụ cấu trúc, không phải domain đã chốt |
|---|---|---|
| ROOT_DOMAIN | Domain gốc nhóm sở hữu | `<ROOT_DOMAIN>` |
| FRONTEND_ORIGIN | Scheme + hostname frontend, không kèm path | `https://app.<ROOT_DOMAIN>` |
| API_ORIGIN | Scheme + hostname API, không kèm `/api` | `https://api.<ROOT_DOMAIN>` |

Tên placeholder khái niệm khác tên biến ứng dụng. Backend hiện đã đọc `APP_FRONTEND_URL`; FE đọc `VITE_API_URL`; không tự thêm `FRONTEND_ORIGIN`/`API_ORIGIN` vào môi trường rồi cho rằng code sẽ đọc chúng.

Ưu tiên frontend/API cùng domain gốc và HTTPS để giữ cơ chế cookie/CSRF hiện có. Nếu dùng raw CloudFront domain và API ở domain gốc khác, cần thiết kế lại phần cookie/CSRF hoặc routing trước nghiệm thu. Không tự thay topology proposal bằng CloudFront proxy API khi nhóm chưa quyết định.

## 2. Các phát hiện cụ thể

| ID | Hiện trạng đọc được | Tác động khi chuyển |
|---|---|---|
| F01 | Caddyfile ghi domain cũ và reverse proxy tới frontend Docker | Phải thay domain/routing; AWS static FE không cần frontend container ở EC2 |
| F02 | Compose ghi trực tiếp URL `mysql-db`, dependency vào mysql service | Đổi `.env` riêng chưa đủ; deploy phải thật sự dùng RDS |
| F03 | Compose build source trên máy deploy; workflow fetch đầu nhánh rồi build | Khác mục tiêu release đúng commit qua ECR/digest và SSM |
| F04 | Workflow ghi trực tiếp CSRF cookie domain cũ, cho cancel deploy đang chạy | Có thể cấp cookie sai domain; cần đổi biến và tuần tự hóa deploy |
| F05 | SecurityConfig dùng APP_FRONTEND_URL cho CORS; JWT và CSRF dùng SameSite=Lax | Cần chốt origin/site, cookie Secure và CSRF domain cùng nhau |
| F06 | FE đọc XSRF-TOKEN từ document.cookie; GET /api/csrf hiện trả 204 | Nếu FE không đọc được cookie thì bootstrap hiện tại không lấy được token để gửi POST |
| F07 | OAuth dùng `{baseUrl}/login/oauth2/code/{registrationId}`; success/failure redirect theo frontendUrl | Cần đúng external HTTPS URL/proxy và callback đăng ký tại provider |
| F08 | AuthService ghi avatar local; WebMvcConfig map filesystem /uploads/** | Không có persistence S3; avatar path cần tiếp tục resolve đúng API origin |
| F09 | TrackProblemService ghi file đề thi local; Compose chưa mount volume cho hai loại file này | File có thể mất khi thay container nếu không chuyển/sao lưu |
| F10 | Datasource/JWT có fallback nhạy cảm; DataSeeder luôn gọi bootstrap admin bằng credential cố định | Cần cấu hình AWS fail-fast và bootstrap opt-in |
| F11 | Hibernate ddl-auto mặc định update; entity Java định nghĩa schema, SQL legacy không được nạp | Kiểm schema do Hibernate sinh trên RDS/lab; validate chỉ sau khi schema đã có, migration tool là quyết định riêng |
| F12 | Test CI dùng H2; chưa thấy health/version endpoint chuyên dụng hay Actuator dependency | Cần MySQL 8.4 integration và smoke test để deploy biết bản mới chạy được |
| F13 | Caddyfile hiện không khai báo access log directive | Nếu cần request log ở Caddy phải BE2 bật; app log vẫn do BE1 bổ sung |
| F14 | EmailService tạo link theo APP_FRONTEND_URL | Đổi cấu hình đúng thì handler/email tái sử dụng được; không cần replace domain trong mọi method |
| F15 | Generic exception handler trả `ex.getMessage()` ra response; config error include-message=always và show-sql=true | AWS cần log nội bộ có ngữ cảnh và phản hồi lỗi an toàn; tránh đưa thông tin lỗi hệ thống/SQL ra client |

Kết quả tĩnh không xác nhận deployment hiện đang dùng đúng mọi cấu hình repo. Không đọc secret thật, provider console, AWS account hay DNS live trong lần rà này.

## 3. Danh sách sửa đổi theo file

Đường dẫn trong bảng tính từ root repository. **P0:** phải có trước cutover theo baseline; **P1:** hoàn thiện vận hành cùng đợt; **AI:** feature mới triển khai theo mốc riêng.

| Mức | File/thành phần | Sửa đổi đề xuất | Owner |
|---|---|---|---|
| P0 | `back-end/src/seal-api/src/main/resources/application.properties` | Chuyển cấu hình phụ thuộc môi trường khỏi default AWS; giữ common settings, phân tách local/test/aws; bỏ fallback nhạy cảm ở AWS | BE1 |
| P0 | `application-local.properties`, `application-aws.properties` — file mới dự kiến | Local dùng cấu hình phát triển; AWS yêu cầu secret/config hợp lệ, Secure cookies, forwarded headers, chính sách tạo/kiểm schema đã chốt và log phù hợp | BE1 |
| P0 | `back-end/src/seal-api/.env.example` | Bổ sung profile/config matrix cho DB, domain, cookie, OAuth, S3; chỉ placeholder và hướng dẫn | BE1 |
| P0 | `config/SecurityConfig.java` | Origin allowlist theo môi trường; xác nhận CSRF cookie domain; giữ credentials và CSRF; không mặc định cho localhost ở AWS nếu không dùng | BE1 |
| P0 | `security/JwtCookieFactory.java` | Kiểm Secure/HttpOnly/SameSite/path và cookie clear nhất quán; giữ host-only JWT cho API trong phương án cùng domain gốc; chưa thấy cần sửa logic khi chỉ đổi hostname | BE1 |
| P0 | `security/oauth2/OAuth2LoginSuccessHandler.java`, `OAuth2LoginFailureHandler.java` | Dùng APP_FRONTEND_URL mới; validate/normalize URL cấu hình ở startup; kiểm success/failure không quay domain cũ | BE1 |
| P0 | OAuth registration trong application config và provider consoles | Google/GitHub callback theo API_ORIGIN; homepage/authorized origins/domain nếu provider yêu cầu | BE1 + owner OAuth |
| P0 | `Caddyfile` | API domain mới/config theo env; proxy backend 8080, giữ đường OAuth, uploads tương thích và docs/health theo policy; bỏ dependency tới frontend EC2 trong topology CloudFront | BE2, BE1 review route |
| P0 | `docker-compose.yml` và `compose.aws.yml` mới dự kiến | Giữ local compose; deploy AWS dùng file độc lập rõ ràng cho backend+Caddy, ECR digest và runtime config; không local mysql/frontend hoặc dependency sót lại | BE2 + BE1 config |
| P0 | `pom.xml` | Thêm AWS SDK S3; thêm SQS khi làm AI; không cần đổi framework/driver hoặc bắt buộc thêm Flyway chỉ vì domain/RDS mới | BE1 |
| Tùy chọn, chờ quyết định | `src/main/resources/db/migration/` — chỉ tạo nếu nhóm chọn migration tool | Lấy baseline từ entity/DDL thật đã xác minh; không dùng SQL legacy làm nguồn chuẩn | BE1 |
| P0 | `config/DataSeeder.java`, `config/seed/DemoSeeder.java` | Tách role/reference data và bootstrap credential; bootstrap opt-in, secret ngoài source; SEED_SCENARIO=NONE không tự tắt DataSeeder | BE1 |
| P0 | AWS config + storage adapter — mới | S3 client dùng runtime role, Region/bucket/prefix cấu hình; quy định put/get/version/hash, exception và timeout | BE1, BE2 IAM |
| P0 | `service/AuthService.java` | Avatar upload/delete chuyển sang adapter S3; DB metadata/URL tương thích; xử lý object/DB thất bại | BE1 |
| P0 | `config/WebMvcConfig.java` và avatar controller/route mới nếu cần | Không tiếp tục chỉ map filesystem trên AWS; chọn API proxy endpoint ổn định cho avatar, giữ link cũ qua mapping nếu cần | BE1 |
| P0 | `service/TrackProblemService.java`, `controller/TrackProblemController.java`, `entity/Track.java` | File đề thi S3, key/version/checksum cần thiết; kiểm staff/event và participant; stream, headers và cleanup sau commit | BE1 |
| P0 | `service/EmailService.java` | Link lấy từ origin mới; kiểm SMTP credentials/runtime; không bắt buộc chuyển SES | BE1 |
| P0 | `.github/workflows/deploy.yml` | Bỏ domain cố định; CI build đúng SHA → ECR → deploy digest qua SSM theo proposal; frontend build đúng API URL; manifest/smoke test, deploy không hủy giữa chừng | BE2, BE1 phối hợp |
| P0 | FE `src/shared/api/core.ts`, `features/auth/SocialAuthButtons.tsx`, build config | VITE_API_URL=API_ORIGIN, CSRF bootstrap/cookies đúng topology; OAuth bắt đầu tại API domain | FE + BE1 |
| P0 | FE `DashboardLayout.tsx`, `features/users/ProfilePage.tsx` | Kiểm avatar URL tương đối vẫn ghép đúng API_BASE_URL; đổi DTO/route theo contract storage nếu cần | FE + BE1 |
| P1 | `exception/GlobalExceptionHandler.java`, application logging | Generic 500 không trả raw ex.getMessage(); log nội bộ có requestId/errorCode; cấu hình error include-message phù hợp, tắt SQL show-sql ở AWS; không log secrets | BE1 |
| P1 | Health/version endpoint, Docker/runtime health check | Thêm cơ chế tối thiểu xác nhận app/version; health output không lộ config; DB readiness phù hợp, không làm public chi tiết | BE1 + BE2 |
| P0/P1 | Tests và deploy smoke collection | Kiểm browser thật cho auth/domain, test MySQL/S3 thật, E2E sau deploy, runbook rollback | BE1 + FE + BE2 |
| AI | AiJob/outbox/API/dispatcher/consumer — mới | Luồng async SQS/S3 theo contract đã chốt; thêm lifecycle/validation/idempotency/log | BE1 + AI; BE2 queues/IAM |

Các file Java rút gọn ở bảng nằm dưới `back-end/src/seal-api/src/main/java/com/seal/hackathon/`.

## 4. Thay đổi domain/auth cần chốt cùng nhau

### 4.1. Mapping cấu hình

| Cấu hình | Giá trị đích |
|---|---|
| `APP_FRONTEND_URL` | FRONTEND_ORIGIN, chỉ origin không có slash cuối/path |
| `VITE_API_URL` | API_ORIGIN, không gắn thêm `/api` vì client đã thêm path |
| `JWT_COOKIE_SECURE` | true ở AWS HTTPS |
| `CSRF_COOKIE_DOMAIN` | ROOT_DOMAIN nếu frontend/API là sibling subdomain và giữ cách JS đọc cookie hiện tại |
| `SERVER_FORWARD_HEADERS_STRATEGY` | framework khi Caddy là proxy trước app, cùng ranh giới tin cậy được BE2 cấu hình |
| Session cookie | Kiểm JSESSIONID dùng cho OAuth state; Secure/path/SameSite theo HTTPS topology |
| Google callback | `API_ORIGIN/login/oauth2/code/google` |
| GitHub callback | `API_ORIGIN/login/oauth2/code/github` |

`CSRF_COOKIE_DOMAIN` không bao gồm scheme, port hay path. JWT hiện không đặt Domain: giữ API host-only là phù hợp khi browser gọi API subdomain với credentials; không cần làm JWT readable từ FE.

Cookie cấp cho domain cũ không chuyển sang domain mới; nghiệm thu cần đăng nhập mới. Login/logout phải cấp/xóa cookie với cùng attributes tương ứng. Không xoay JWT secret mỗi deploy; lưu secret ổn định theo môi trường, thay khi có kế hoạch.

### 4.2. Nếu frontend/API không cùng domain gốc

Hiện FE gọi GET /api/csrf rồi đọc document.cookie, trong khi endpoint trả 204. Frontend ở domain gốc khác sẽ không đọc được cookie API theo cách này. SameSite=Lax cũng không phù hợp cho cross-site fetch gửi auth cookie; chỉ đổi CORS hoặc chỉ đặt SameSite=None chưa giải quyết toàn bộ cơ chế và chính sách third-party cookie.

Hai hướng để nhóm quyết định: custom frontend/API cùng domain gốc như proposal; hoặc thay thiết kế routing/auth-CSRF (ví dụ same-origin proxy/BFF hoặc token bootstrap phù hợp) và kiểm browser. Hướng sau phát sinh scope, không tự triển khai trong kế hoạch baseline.

Nguồn: [Spring Security CSRF và SPA](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), [Cookie Domain/SameSite](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie).

### 4.3. OAuth và email

Callback OAuth là endpoint backend `/login/oauth2/code/...`; `/oauth2/redirect` là route frontend sau khi backend đăng nhập thành công. Không đăng ký nhầm hai loại URL này.

Google cần callback đúng cấu hình đã đăng ký; GitHub cần cập nhật callback theo quy tắc OAuth app. Trước cutover có thể kiểm môi trường mới bằng OAuth client riêng hoặc cấu hình provider hỗ trợ, không làm hỏng môi trường cũ đang phục vụ người dùng.

`EmailService` và OAuth handlers đã dùng frontendUrl; chủ yếu đổi cấu hình và test. Không cần thay từng chuỗi URL trong method khi chúng đã dùng config.

Nguồn: [Google OAuth web server](https://developers.google.com/identity/protocols/oauth2/web-server), [GitHub OAuth redirect URLs](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps).

## 5. Persistence và dữ liệu cũ

### RDS

- `SPRING_DATASOURCE_URL` phải trỏ RDS thật; kiểm source precedence của Compose/env/profile, không dùng DB_URL chưa được code đọc.
- App user quyền nghiệp vụ; bước Hibernate tạo/cập nhật schema cần quyền DDL tương ứng, thống nhất identity/bước chạy với BE2; sau khi schema sẵn sàng có thể runtime validate với quyền hẹp hơn. Migration user/step chỉ đặt ra nếu nhóm chọn migration tool; không mặc định dùng root.
- TLS xác minh server/CA, database tạo trước; không dùng cấu hình local tắt SSL làm cấu hình đích.
- MySQL 8.4 còn chờ integration; xem báo cáo `docs/report/mysql-8.4-compatibility-review-2026-10-27.md`.
- Entity Java là nguồn schema; không nạp SQL legacy. Kiểm MentorSupportRequest/Notification và mappings hiện tại trong schema Hibernate thực sự tạo. Chỉ kết luận drift khi so DB thật với entity ở commit tích hợp; không suy ra từ file SQL cũ. Đối với DB có dữ liệu, kiểm đường nâng cấp/giữ dữ liệu riêng.
- Không đặt validate lên database trống rồi kỳ vọng Hibernate tạo bảng. Lab mới thử update theo cơ chế hiện tại, xác minh DDL/schema rồi mới quyết định chế độ deploy. Không dùng script reset/demo để chuyển hoặc phục hồi dữ liệu cần giữ.
- Backup/restore: BE2 vận hành DB backup; BE1 kiểm row counts, constraints, API, file references sau restore.

Nguồn: [AWS RDS TLS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.SSL.html).

### S3 và URL avatar

- S3 private cho app files, runtime EC2 role riêng; user gọi API, không có AWS credentials.
- Avatar upload local hiện lưu `/uploads/avatars/...` vào DB. FE đã ghép đường này với API_BASE_URL: không nhất thiết phải rewrite mọi URL thành hostname mới.
- Có thể giữ route API `/uploads/avatars/...` như lớp tương thích nhưng đổi phía sau sang S3; hoặc chốt route mới với FE. Không map S3 như một filesystem bằng WebMvcConfig hiện tại.
- Avatar OAuth URL ngoài hệ thống giữ nguyên theo policy; không replace tất cả URL trong User.
- Với avatar cũ/đề thi cũ: scan file runtime thực, manifest path → S3 key/version/hash; copy/xác minh trước đổi DB reference, dọn sau theo retention. File đã mất khỏi container không tự phục hồi bằng cách đổi metadata.
- Đề thi vẫn đi qua guard event/track/release/team; AI result kiểm assignment/requester; không chuyển app bucket thành public frontend bucket.
- Upload object mới rồi commit metadata; lỗi DB phải giữ reference cũ hợp lệ; cleanup sau commit có retry/log.

Nguồn credential: [AWS Java credential chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html).

## 6. Runtime/deploy phối hợp BE2

1. Giữ local compose để dev. Tạo `compose.aws.yml` độc lập dùng rõ ràng bằng `-f`, tránh vô tình merge còn local DB/FE hoặc ports/volumes/dependencies cũ.
2. AWS runtime lấy backend image từ ECR digest; config/secret được BE2 cấp trước khi khởi động. Không cần source/Git trên EC2 để build release.
3. Caddy runtime chỉ proxy API trong baseline CloudFront static; giữ certificate data volume và routing OAuth. Host backend 8080 không public; DB private. Proxy forwarded headers phải nằm trong ranh giới tin cậy, không cho client gọi thẳng app bypass Caddy.
4. DNS app trỏ CloudFront, DNS api trỏ EC2/EIP; CloudFront custom domain certificate theo yêu cầu AWS. Caddy quản lý API cert riêng. Đây là phần BE2/owner domain, không sửa Java để thay DNS.
5. VITE_API_URL được đóng vào frontend lúc build: đổi env ở backend hoặc container sau build không cập nhật JS cũ. FE/BE2 phải rebuild/release assets với URL mới, kiểm cache/invalidation và SPA deep-link fallback trên CloudFront.
6. Workflow build từ đúng SHA đã test, publish digest/artifact/manifest, deploy qua SSM theo proposal. `cancel-in-progress: true` hiện tại cần đổi để không hủy giữa thay container/migration. Tách checks PR khỏi release có mutation.
7. Health/version và smoke test phải phân biệt backend sống, DB sẵn sàng và phiên bản đúng; không thêm dependency nặng chỉ để có một endpoint health.
8. Release manifest giữ commit/image digest/config reference/schema compatibility. Restore DB/files khác app rollback; có người quyết định khi schema đã thay không tương thích.

Nguồn: [Caddy reverse proxy/forwarded headers](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy), [CloudFront custom domain HTTPS](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/using-https-alternate-domain-names.html).

## 7. AI/SQS: tách thành milestone riêng trong cùng chương trình AWS

Chuyển domain không tự tạo tính năng AI job. Nếu nghiệm thu AWS bao gồm luồng AI theo phân công ban đầu, cần triển khai thêm:

- AiJob/outbox, snapshot SHA/rubric/version/hash và migration.
- POST jobs, GET status/result với quyền từng request.
- BE1 viết dispatcher gửi jobs và consumer nhận STARTED/completion; BE2 tạo queues/DLQ/IAM.
- DB commit result rồi mới ack; chống duplicate/late event, expiry/deadline, log theo jobId.
- AI worker đọc input/ghi result S3, gửi result event; không có DB credentials và không public Ollama.
- Feature flag/cách chuyển từ API Gemini `/insights` với FE; không xóa luồng cũ trước khi luồng mới được nghiệm thu.

Chi tiết task đã có ở `docs/plans/backend-1-implementation-plan.md`. Không làm AI thành blocker cho login/domain/RDS/S3 nếu Lead cho phép nghiệm thu theo từng mốc.

## 8. Backlog thực hiện và thứ tự

Ước lượng là ngày công tập trung BE1, chưa gồm thời gian chờ BE2/domain/provider và migration dữ liệu lớn; chưa có deadline người dùng chốt.

| Thứ tự | Task/đầu ra | Ước lượng | Dependency |
|---|---|---|---|
| A01 | Chốt topology, origin/config matrix, dữ liệu giữ lại, commit và owner | 0,5 ngày | Lead/FE/BE2; hostname cụ thể có thể điền sau |
| A02 | Tách profile local/test/aws; secret fail-fast; bootstrap opt-in, log config | 1–1,5 ngày | A01 |
| A03 | CORS/cookie/proxy/OAuth/email config và test browser với domain lab | 1–1,5 ngày | A01/A02 + DNS/TLS/OAuth từ owner |
| A04 | Entity/Hibernate code-first và datasource RDS8.4/TLS; tạo/kiểm schema ở lab, chốt ddl-auto; migration tool nếu chọn là task riêng | 1–2 ngày | A01, BE2 DB/config |
| A05 | S3 adapter + avatar/đề thi, quyền và URL compatibility | 2–3 ngày | A02/A04, BE2 S3/IAM |
| A06 | Data/file manifest, copy/restore rehearsal và API verify | 0,5–2 ngày | A04/A05; tăng nếu dữ liệu lớn |
| A07 | Health/version/error log + gói cấu hình/image handoff | 0,5–1 ngày | Làm dần cùng A02–05 |
| A08 | Review compose/Caddy/ECR/SSM pipeline do BE2 triển khai | BE1 0,5–1 ngày phối hợp | BE2, FE release |
| A09 | Smoke/E2E AWS, cutover/rollback rehearsal và sign-off | 1–2 ngày | A03–08 |
| AI01 | AI jobs/SQS milestone | Theo plan Backend 1 riêng | Contract/AI/BE2/FE |

Có thể làm A03 và A04–05 theo điều kiện môi trường, nhưng không đánh dấu auth hoàn tất trước test domain thật. Không cộng máy móc timebox thành deadline cam kết; dùng capacity và dữ liệu thực tế để lên Project.

## 9. Checklist nghiệm thu trước chuyển traffic

| Nhóm | Phải kiểm được |
|---|---|
| Config | AWS không trỏ localhost/mysql-db/domain cũ; thiếu secret bắt buộc fail rõ; đúng profile |
| DNS/TLS | App/API hostname và chứng chỉ đúng; OAuth dùng HTTPS external URL |
| CORS | FE origin được phép; preflight POST/upload ổn; origin ngoài allowlist không được cấp CORS |
| Cookie/CSRF | Cold browser GET csrf → login → me → POST/PUT/DELETE; thiếu/sai CSRF bị chặn; logout xóa cookie; relogin hoạt động |
| OAuth | Google/GitHub success/failure/incomplete profile/approval; callback backend và redirect FE đúng hostname |
| DB | Hibernate tạo/cập nhật schema MySQL8.4 lab từ entity; restart validate sau khi schema đã có nếu chọn; CRUD/event/team/submission/score/notification, tiếng Việt/decimal/time và constraints |
| Files | Upload/avatar display, đề thi trước/sau release/retract, wrong team/event; container replacement không mất file |
| Mail | Mail/OTP theo cấu hình được chọn; link email không về localhost/domain cũ |
| Deploy | Digest/version đúng commit; restart ổn; health/log theo request/job; rollback theo schema compatibility |
| Data | Restore sample và counts/reference/file checksum khớp; backup/cutover owner rõ |
| AI nếu trong mốc | Mock round trip rồi worker thật, duplicate/offline/late và quyền xem result |

Postman/backend unit tests không thay cho kiểm browser cookie/CORS/OAuth. Test H2 không thay cho MySQL8.4 integration.

## 10. Cutover đề xuất

1. Dựng AWS mới cạnh môi trường cũ, dùng dữ liệu lab/copy và domain lab được nhóm sở hữu; kiểm trước khi thay traffic chính.
2. Chốt hostname thật, OAuth configs và release frontend/API tương thích. Chuẩn bị backup DB/file và manifest release trước.
3. Nếu bảo toàn dữ liệu, chốt một khoảng dừng ghi hoặc phương án đồng bộ cuối để tránh hai DB nhận ghi lệch nhau; copy delta/file references rồi xác minh.
4. Phát hành/cấu hình DNS và origin theo lịch nhóm; giám sát auth, DB, file. Người dùng đăng nhập lại trên domain mới.
5. Nếu cần quay lại, xác định dữ liệu ghi sau cutover trước khi rollback; chỉ đổi DNS về DB cũ có thể làm mất các cập nhật mới. Lead/BE2/BE1 chọn phương án theo bằng chứng.
6. Chỉ dọn môi trường/credential cũ sau thời gian giữ và xác nhận dữ liệu/rollback không còn cần.

Không tự redirect POST/API từ domain cũ sang mới để coi là migration session. Nếu giữ redirect web cũ, review riêng OAuth, cookies và thao tác ghi.

## 11. Giới hạn và phần không cần sửa hàng loạt

Lần này đã đọc config, auth/cookie/OAuth handlers, upload/avatar/file paths, email, workflow, frontend API/CSRF helpers và proposal. Chưa build/test/deploy mới, đọc DB/runtime hoặc kiểm AWS/DNS/provider live. Chưa có domain mới theo phản hồi người dùng.

Không cần đổi business entity chỉ vì đổi hostname; không cần đổi JWT cookie name/app branding chỉ vì domain đổi; không thay `api.github.com`, Google OAuth endpoints hoặc OAuth avatar URLs bằng domain dự án. Các phần đã dùng config chỉ cần config mới và kiểm thử phù hợp, không viết lại toàn bộ.

Chỉ tạo kế hoạch này; không áp dụng thay đổi mã nguồn/hạ tầng. Domain thật được điền vào config matrix sau khi nhóm chốt, không chặn công việc refactor cấu hình/storage/migration độc lập.
