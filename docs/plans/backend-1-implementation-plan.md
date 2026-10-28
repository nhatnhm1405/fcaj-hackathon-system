# Kế hoạch thực thi chính — Backend 1 chuyển AWS và tích hợp AI

Cập nhật triển khai 27/10/2026: đã có code và test local cho migration/storage/job delivery. Trạng thái nghiệm thu và giới hạn hiện hành nằm trong [bàn giao BE1](../report/backend1-local-completion-2026-10-27.md); checklist/ước lượng bên dưới là kế hoạch ban đầu, chưa phải xác nhận deploy AWS. Nền source `f42a168`, thay đổi còn local.

**Quyết định schema:** **Flyway tạo/thay đổi schema, Hibernate validate và giữ JPA**. BE1-21 đã có V1/V2, runtime policy và lab restore; chi tiết kế hoạch DB-01…10 tại [kế hoạch SQL migration](backend1-sql-migration-execution-plan.md). Người dùng yêu cầu giữ dữ liệu cũ và test chuyển cùng đợt; đã rehearsal trên DB riêng. Cutover DB thật/AWS còn chờ BE2.

Đã đối chiếu với [proposal phối hợp BE1–BE2 tuần 2](E:/FA26/AWS/week-2-backend-1-2-integration-proposal.md). Proposal còn chờ owner xác nhận; cập nhật task không có nghĩa AI/BE2/Lead đã ký contract hoặc AWS đã tồn tại. Giữ các ID BE1-01…18, bổ sung **BE1-19** (quyền/cấu hình tích hợp) và **BE1-20** (nghiệm thu PoC mock tuần 2).

Sau trao đổi về trạng thái job, dùng **PENDING_DISPATCH, QUEUED, SUCCEEDED, FAILED** làm baseline kế hoạch của BE1 cho vòng đầu. RUNNING/EXPIRED chỉ bổ sung khi có contract tương ứng. Việc người dùng thống nhất hướng làm trong chat không thay thế review contract chung với AI/BE2/FE; chưa có enum/API mới được implement.

## 0. Đối chiếu mới và ưu tiên tuần 2

| Chủ đề | So với kế hoạch đã chốt | Cập nhật task/quyết định |
|---|---|---|
| Producer/consumer + snapshot | Trùng với scope BE1 đã giao; proposal vẫn xin các owner xác nhận | Giữ BE1-12/14/15, ghi xác nhận phối hợp tại BE1-11/19 |
| Flyway/schema migration | Hiện mapping trong Java/Hibernate; yêu cầu mới chuyển quản lý schema sang SQL migrations | Thêm BE1-21: Flyway + SQL mới từ mappings đã review; giữ entity/JPA, Hibernate validate. Không dùng SQL legacy làm baseline mặc định |
| DB PoC/data | Proposal dùng DB mới `fcaj_poc`, không chuyển dữ liệu thật | Dùng tên này làm cấu hình dự kiến chờ BE2 xác nhận; BE1-07 chuyển sau PoC |
| Input/output S3 | Proposal dùng `inputs/`, `rubrics/`, `results/` | BE1-08/12/19 cấu hình theo prefix dự kiến, không hardcode bucket/account/resource names |
| Trạng thái | Proposal có PENDING_DISPATCH → QUEUED → SUCCEEDED/FAILED; plan cũ có RUNNING/EXPIRED | BE1-11 chốt semantics trước code. Dùng mô hình proposal làm bản nháp tuần 2; RUNNING chỉ khi có STARTED. Deadline/EXPIRED đã có trong plan giữ là quyết định cần chốt, không âm thầm bỏ |
| Quyền BE1 | Mặc định xem Console/log; local S3/SQS write/consume là quyền riêng | Thêm BE1-19 chốt đường thử deploy chung hay local có identity riêng và cô lập test messages |
| Mốc tích hợp | Proposal ưu tiên EC2/RDS/S3/SQS + worker mock một job/lần | Thêm M0/BE1-20 trước hoàn thiện model thật, avatar và browser trên domain chính thức |
| Scrum Master | Proposal nhắc lại vai trò ban đầu của bạn | Giữ task điều phối hằng ngày, không coi là việc tùy chọn đã bỏ |

**Mục tiêu tuần 2:** một bản backend đúng commit/digest chạy trên EC2, dùng RDS PoC, có một luồng file S3 có kiểm quyền và một AI job đi hết vòng bằng worker mock. Log/request-response và kiểm duplicate/stale result là đầu ra bắt buộc. Chưa cam kết hoàn tất toàn bộ roadmap 12–18 ngày công trong tuần 2.

**Phần không chặn M0:** domain chính thức/OAuth browser đầy đủ, avatar S3 toàn bộ, dữ liệu thật, model thật, STARTED/RUNNING nếu worker chưa hỗ trợ, Lambda và monitoring nâng cao. Quyền API/job và kiểm dữ liệu/message vẫn bắt buộc trong PoC; không tắt bảo mật để đạt vòng mock.

## 1. Những điều đã xác nhận

- Bạn là Backend 1: code persistence RDS/S3; kiểm quyền; API AiJob; gửi jobs/nhận result SQS; log; bàn giao code chạy được và hỗ trợ BE2 debug.
- BE2 tạo/vận hành AWS resources, IAM/network/backup, DNS/TLS/Caddy/runtime, ECR/pipeline/deploy/rollback. BE1 cung cấp yêu cầu và kiểm ứng dụng sau deploy.
- AI phụ trách worker/model; FE phụ trách giao diện, API client, polling và cấu hình build frontend. Những bên này chưa được coi là đã ký contract chỉ vì kế hoạch ghi trách nhiệm.
- Mapping hiện lấy từ **31 Java entity**; local profile đang mặc định Hibernate update, AWS chọn DDL_AUTO rõ ràng, test dùng H2/create-drop. Java seeders nạp dữ liệu. SQL legacy không phải baseline hiện tại.
- Flyway chưa có trong app, nhưng đã được đưa vào kế hoạch mới theo yêu cầu người dùng: SQL migrations quản lý schema ở môi trường MySQL đích, Hibernate validate. BE2 cần chốt revision/case và runner/user, không tự suy ra họ đã cấp quyền hay dựng RDS.
- Domain mới chưa có. Dùng placeholder khái niệm FRONTEND_ORIGIN/API_ORIGIN; ưu tiên HTTPS `app.<ROOT_DOMAIN>` và `api.<ROOT_DOMAIN>` cùng domain gốc theo proposal. Placeholder không phải giá trị có thể deploy thật.
- RDS MySQL 8.4 là phiên bản đích đề xuất, còn cần test Hibernate và nghiệp vụ trên server thật. Không coi test H2 là nghiệm thu 8.4.
- Kiến trúc đích theo proposal: FE static trên CloudFront/S3; API Docker trên EC2 sau Caddy; RDS private; S3 application private; SQS jobs/results và hai DLQ; worker local chủ động poll.

## 2. Mốc tuần 2 và ba mốc hoàn thiện

| Mốc | Đầu ra cụ thể | Điều kiện chấp nhận |
|---|---|---|
| M0 — PoC tuần 2 với worker mock | EC2 đúng commit/digest → RDS PoC → S3 file có quyền → SQS jobs → mock worker → S3 result/SQS results → RDS/API | Một job/lần; duplicate không sinh tác dụng phụ; result cũ không ghi đè lần mới; có log theo jobId và bằng chứng request/response |
| M1 — Backend chạy được trên môi trường AWS | Profile/config, auth đúng origin, Hibernate/RDS8.4, avatar/đề thi S3 | Login/quyền đúng; thay container vẫn đọc DB/file; không phụ thuộc DB/file local |
| M2 — AI job đi hết luồng | Snapshot → AiJob/outbox → SQS → worker → S3/result queue → DB → API | Mock worker trước, model thật sau; duplicate/retry/deadline/quyền được kiểm |
| M3 — Bàn giao và vận hành | Log, health/version, build/config/API fixtures, smoke test, runbook | BE2 deploy được; từ requestId/jobId tìm được lỗi; có bằng chứng sau deploy |

M0 là đích tích hợp sớm, không phải đã hoàn tất mọi acceptance của M1/M2/M3. Phần mock của M2 được kéo lên M0; model thật/FE/domain và data cutover được nghiệm thu sau. Không để thời gian benchmark model chặn PoC.

## 3. Cách chia phạm vi công việc

| Việc | Bạn chịu trách nhiệm | Bạn cần từ đồng đội |
|---|---|---|
| Config ứng dụng | Profiles, biến đọc trong code, validation, startup | BE2: cách cấp config/secret vào runtime |
| Domain/auth | CORS, cookie/CSRF, redirects, API routes và test | BE2/Lead: hostname/TLS; owner OAuth: đăng ký callback; FE: client |
| Database | Entity/mappings, Hibernate schema, JPA queries, kiểm dữ liệu | BE2: RDS8.4 endpoint/user/TLS/network/backup |
| Files | S3 adapter, upload/download/delete, metadata và quyền | BE2: bucket/prefix/IAM/versioning/retention |
| AI | Contract, snapshot, AiJob/API, outbox/dispatcher/result consumer | AI: worker/output; FE: UI; BE2: queues/DLQ/IAM |
| Deploy | Code build được, cấu hình mẫu, smoke test và debug | BE2: image/release/SSM/Caddy/CloudWatch |
| Scrum theo phân công ban đầu | Cập nhật Project, dependency/blocker và nhắc cập nhật | Mỗi người cập nhật việc của mình; Lead quyết định ưu tiên |

## 4. Backlog chi tiết theo thứ tự

Task bên dưới là đầu ra/acceptance cần xác minh. Workspace đã có code local về config/bootstrap/JWT và fixtures AI từ công việc khác; đọc lại diff/test trước sửa hoặc báo tiến độ. Tên class mới là dự kiến; dùng cấu trúc package hiện có, không tạo microservice mới.

### Giai đoạn A — Chuẩn bị và cấu hình

**BE1-01 — Ghi mốc hiện trạng và quyết định dữ liệu. Ước lượng: 0,5 ngày.**

- Ghi commit tích hợp, các thay đổi đang có và các luồng phải giữ hoạt động; không đưa tài liệu untracked có sẵn vào commit code ngoài ý muốn.
- Chạy test hiện có bằng Java 21/Maven Wrapper với profile test; ghi lỗi có sẵn riêng.
- Dùng dữ liệu mẫu/repository đã được đồng ý trong DB PoC mới theo hướng proposal; tên `fcaj_poc` vẫn chờ BE2 xác nhận. Giữ nguyên dữ liệu/file hiện hữu, không chuyển dữ liệu thật vào PoC. Nhu cầu bảo toàn/cutover thật ghi cho giai đoạn sau.
- Lập danh sách config hiện đọc từ source; không đưa secret thật vào tài liệu.
- Đầu ra: baseline test + inventory/config checklist + quyết định dữ liệu Pending/Confirmed rõ ràng.

**BE1-02 — Tách profile local/test/aws. Ước lượng: 0,5–1 ngày.**

- Giữ `application.properties` cho common settings; thêm `application-local.properties`/`application-aws.properties` khi thực sự cần tách.
- Local/test tiếp tục chạy được. Cơ chế import .env local không được vô tình ghi đè config AWS/test; không đóng gói .env vào image.
- AWS đọc datasource/origin/cookie/storage/queue config từ môi trường hoặc cơ chế runtime BE2 cấp. Dùng tên biến Spring đang hỗ trợ; không thêm DB_URL rồi bỏ quên code không đọc nó.
- Thiếu config/secret bắt buộc thì fail startup với thông báo không chứa giá trị secret. Các feature chưa bật không bắt app phải có credential/queue/model key của feature đó.
- AWS SDK clients dùng credential provider chain/runtime role; không hardcode access key. Môi trường local có adapter local/mock theo cấu hình; không vô tình gọi AWS ở unit test.
- Cập nhật `.env.example` hoặc file config mẫu AWS: tên biến, ý nghĩa, bắt buộc khi nào, bên cấp, placeholder.
- Xong khi: local/test không bị phá; AWS profile chọn đúng datasource/settings; config thiếu được báo rõ trước vận hành.

**BE1-19 — Chốt đường thử, IAM/config và bàn giao BE2. Ước lượng: 0,5 ngày phối hợp; chờ quyền không tính là đã hoàn tất.**

- Chọn đường tích hợp mặc định qua bản deploy chung: BE1 xem Console/log theo quyền; app EC2 dùng instance role của app. Không dùng credential cá nhân thay runtime role.
- Nếu local cần gọi S3/SQS, xin identity test riêng với đúng prefix/queue. ReceiveMessage/DeleteMessage là thao tác ảnh hưởng queue; không dùng Console poll/local consumer trên message đang phục vụ consumer chung ngoài kế hoạch test. Chốt queue/prefix test riêng hoặc thời gian cô lập một job/lần trước khi thử.
- Worker AI dùng identity riêng; chốt ai viết/chạy worker mock, lệnh chạy và lịch online. Đề xuất AI làm mock theo cùng interface worker thật; BE1 cung cấp fixtures/validator, BE2 hỗ trợ IAM/transport. Owner này còn chờ xác nhận.
- Nhận outputs thực: Region, DB endpoint/port/database/TLS/user-secret reference, bucket/prefix/versioning, job/result queue URLs và DLQ, log group/cách đọc, API URL và image digest. Tên dự kiến trong proposal không chứng minh tài nguyên tồn tại.
- Giá trị tham chiếu proposal: Region ap-southeast-1, DB fcaj_poc, queues fcaj-poc-ai-jobs/results và DLQ tương ứng, log group /fcaj/poc/backend, ECR fcaj-poc-backend. Bucket nhận từ output BE2; không hardcode tên bucket/account dự kiến vào code. Prefix AI dự kiến inputs/, rubrics/, results/; file đề thi mẫu dùng prefix riêng BE2 cấp, không coi đó là source snapshot.
- Cùng Lead/BE2 chốt trần chi/lịch tài nguyên và lịch tích hợp; không tự tạo tài nguyên từ bảng tên đề xuất.
- Xong khi: config matrix đã điền outputs hoặc Pending rõ ràng; đường test/identity/owner mock và buổi tích hợp được xác nhận; không ghi secret vào Git/tài liệu.

**BE1-03 — Tách bootstrap khỏi demo và loại fallback nhạy cảm ở AWS. Ước lượng: 0,5 ngày.**

- `DataSeeder` vẫn giữ dữ liệu nền cần thiết như role/template theo nghiệp vụ; bootstrap admin tách thành opt-in với credential cấp ngoài code.
- Rà các tham chiếu `DataSeeder.ADMIN_EMAIL` trong demo fixtures/scenarios khi refactor để không phá demo local.
- `DemoSeeder` giữ scenario opt-in; AWS mặc định không nạp demo data. SEED_SCENARIO=NONE không được coi là đã tắt bootstrap nếu code chưa đổi.
- Kiểm JWT secret đúng định dạng/độ dài tại startup phù hợp môi trường, không đợi request login đầu tiên mới phát hiện lỗi; không tạo key mới mỗi lần restart.
- Xong khi: startup bình thường không tạo account credential cố định; bật bootstrap có chủ đích được và không tạo account trùng; local demo còn chạy theo cấu hình.

### Giai đoạn B — Domain, cookie và OAuth

**BE1-04 — CORS/cookie/CSRF theo cấu hình. Ước lượng: 0,5–1 ngày.**

- Rà `SecurityConfig`, `JwtCookieFactory`, `CsrfController`, `CsrfCookieFilter` và tests hiện có.
- AWS chỉ cho FE origins được cấu hình; credentials bật đúng; local origins nằm trong cấu hình local khi cần.
- Giữ JWT HttpOnly/host-only cho API trong topology sibling subdomain; Secure=true ở HTTPS; cấp và xóa cookie cùng attributes.
- CSRF cookie cho FE đọc được theo ROOT_DOMAIN đã chốt, không nhầm thành URL có scheme/path. Giữ CSRF cho thao tác dùng cookie, không tắt để chữa lỗi 403.
- Kiểm JSESSIONID của OAuth state, forwarded headers từ Caddy và external HTTPS URL.
- Domain chưa có: test bằng fixtures và config ví dụ; browser acceptance để Pending. Nếu khác domain gốc, ghi quyết định thiết kế auth-CSRF riêng trước phụ thuộc vào cơ chế hiện tại.
- Xong khi: unit/integration về origin/cookie và request CSRF đạt; trên domain thật cold browser login → me → POST/PUT/DELETE → logout → relogin chạy đúng, CSRF sai bị chặn.

**BE1-05 — OAuth redirects và email links. Ước lượng: 0,5 ngày + chờ provider/domain.**

Hoàn thiện sau M0 nếu domain/provider chưa có; không biến thành blocker cho PoC API đã xác thực. M0 dùng API URL thật BE2 cấp và client được phép, không tắt CSRF/CORS/auth để thay thế nghiệm thu browser.

- `OAuth2LoginSuccessHandler`, failure handler và `EmailService` tiếp tục dùng APP_FRONTEND_URL; kiểm URL cấu hình/normalization, không chèn hostname mới vào từng method.
- Cung cấp owner OAuth callback `API_ORIGIN/login/oauth2/code/google` và `/github`; phân biệt với route FE `/oauth2/redirect`.
- Cùng BE2 kiểm Caddy/forwarded headers; cùng FE đặt VITE_API_URL=API_ORIGIN, giữ credentials và CSRF helper tương thích.
- Kiểm email/OTP theo SMTP hiện được chọn; SES chỉ làm nếu nhóm giao thêm. Domain mới không tự buộc chuyển nhà cung cấp email.
- Xong khi: Google/GitHub success/failure và email links đúng frontend/API origin; bằng chứng browser khi có môi trường thật.

### Giai đoạn C — RDS/MySQL8.4 từ Java/Hibernate

**BE1-21 — SQL migrations/Flyway thay Hibernate update. Ước lượng:5–8 ngày công core/config/tests, cần điều chỉnh theo lab/dữ liệu.**

- Thực hiện DB-01…10 theo kế hoạch SQL migration liên kết ở đầu tài liệu: preflight current revision/diff → inventory entity/DDL → Flyway/SQL V1 → profiles/validator/seed/demo → MySQL8.4 tests → nhánh restore/baseline nếu có dump → bàn giao BE2.
- Giữ entity/JPA/repository; Flyway quản lý schema, Hibernate validate. Không copy SQL legacy, không tự dùng dump chưa nhận, không tự import lên DB thật.
- Tách đường DB mới rỗng chạy V1 khỏi DB đã có dữ liệu cần diff và explicit baseline được xác minh. Case handling chốt trước tạo sandbox/RDS. Không bật baseline-on-migrate hoặc clean để bỏ qua mismatch.
- Giữ các thay đổi local đã có, cập nhật test H2/MySQL/CI theo phạm vi thật. Không đánh dấu21 xong nếu chỉ viết SQL mà chưa migrate/validate trên MySQL thật.

**BE1-06 — Kết nối và kiểm schema trên MySQL8.4 lab. Ước lượng: 0,5–1 ngày.**

- Nhận version/user/config/TLS từ BE2. Phát triển trên MySQL8.4 lab mới, không gắn lại volume8.0 cần giữ và không mở public RDS chỉ để laptop kết nối.
- DB/catalog PoC dự kiến fcaj_poc, dữ liệu mẫu được đồng ý; giữ môi trường8.0 hiện tại để đối chiếu. Nếu8.4 không đạt, ghi lỗi và bàn BE2/Lead phương án engine/chi phí Extended Support trước khi đổi, không tự fallback sang8.0.
- Kết nối bằng driver hiện tại. DB/catalog tạo trước; Flyway chạy SQL migrations từ BE1-21, Hibernate validate. Hibernate DDL export chỉ dùng để tham khảo baseline trên lab riêng ở bước chuẩn bị, không làm schema writer song song trong runtime mới. Không import seal_schema.sql/seal_seed.sql.
- Kiểm startup SQL/errors, tables/columns/FK/index/unique; kiểm chữ hoa/thường, charset/collation, tiếng Việt, decimal điểm và timestamp.
- Xác minh role/reference seeding và application DB user; phối hợp BE2 quyền DDL trong bước tạo schema và quyền runtime sau đó.
- Chốt migration runner với BE2: chạy trước app bằng identity có quyền DDL hoặc startup migration có quyền phù hợp; schema có sau migrate thì Hibernate validate mới đạt. Không chỉ bật validate lên DB trống mà chưa có bước tạo schema.
- Xong khi: schema đúng entity trên MySQL8.4 thật; CRUD/event/team/submission/score/notification/support request và restart đạt. Kiểm lại TLS/network trên RDS từ EC2 sau khi có lab local.

**BE1-07 — Di chuyển dữ liệu đang có, nếu cần giữ. Ước lượng: phụ thuộc dữ liệu.**

**Sau M0, chưa làm trong PoC tuần 2:** proposal dùng dữ liệu mẫu và không chuyển dữ liệu thật. Task này giữ trong roadmap để quyết định sau, không đưa vào acceptance của buổi tích hợp đầu.

- Export metadata/data từ DB thực, backup/restore vào môi trường riêng; đối chiếu với entity thay vì SQL legacy.
- Kiểm counts, FK/unique và dữ liệu đại diện; phân loại bảng legacy theo nhu cầu bảo toàn, không tự drop vì không có entity.
- Ghi cách cutover/dừng ghi hoặc đồng bộ cuối và phục hồi cùng BE2. Nếu nhóm dùng DB demo mới, ghi rõ phạm vi task này không áp dụng thay vì giả vờ đã migration.
- Xong khi: dữ liệu cần giữ được đọc đúng qua API sau restore; có bằng chứng, owner và phương án quay lại.

### Giai đoạn D — File S3 và quyền

**BE1-08 — Storage adapter có local/S3 implementation. Ước lượng: 0,5–1 ngày.**

- Thêm dependency SDK S3 tương thích; cấu hình client dùng role, Region/bucket/prefix/timeout.
- Đề xuất interface ObjectStorage cho put/read/cleanup theo nhu cầu; metadata có key, size, content type, checksum và versionId nếu dùng versioning.
- Key do server sinh, giới hạn file theo luồng; không cho client chọn object key tùy ý. Stream/đóng tài nguyên đúng vòng đời response, không buffer file lớn không cần thiết.
- Local implementation giữ trải nghiệm dev; test fake không đòi credential AWS.
- Xong khi: round trip bytes/checksum đúng; lỗi storage có mã ứng dụng an toàn, không rò credential.

**BE1-09 — Đề thi upload/download S3 có kiểm quyền. Ước lượng: 1 ngày.**

M0 ưu tiên một file mẫu qua luồng đề thi hiện có, với user/track/event fixture hợp lệ để chứng minh quyền. Không tạo endpoint debug đọc key tùy ý hoặc yêu cầu chuyển mọi file/avatar mới được demo.

- Refactor `TrackProblemService` dùng adapter, giữ API controller hiện có khi khả thi.
- Rà service guard staff theo event, track thuộc event, participant APPROVED đúng track và release/retract; thống nhất ngoại lệ admin với Lead.
- Tận dụng metadata Track hiện có; thêm version/checksum nếu cần bằng entity Java.
- Upload object mới → commit metadata → cleanup object cũ sau commit theo retention. DB lỗi không mất reference cũ; object mồ côi có cleanup/retry.
- Download tự tra key/version từ DB sau kiểm quyền, stream qua backend; content disposition an toàn và private/no-store.
- Xong khi: đúng quyền tải được; wrong event/team, chưa release và sau retract bị chặn; replace/DB lỗi không làm mất file cũ; audit còn đúng.

**BE1-10 — Avatar S3 và URL tương thích. Ước lượng: 0,5–1 ngày.**

Sau M0 nếu cần ưu tiên vòng AI mock; vẫn phải hoàn tất trước nghiệm thu persistence đầy đủ của M1 nếu avatar thuộc scope deploy. Không đồng nghĩa chấp nhận file local mất khi redeploy môi trường chính.

- Refactor upload/delete trong `AuthService`; thay phụ thuộc filesystem của `WebMvcConfig` ở AWS bằng route đọc object qua backend theo policy avatar được chốt.
- Ưu tiên giữ contract URL `/uploads/avatars/...` hoặc mapping tương thích để FE ghép API_BASE_URL như hiện tại; không mặc định trả public S3 URL.
- Giữ OAuth avatar URLs ngoài hệ thống; kiểm upload chỉ sửa avatar chính user.
- Nếu có file cũ, manifest/copy/checksum rồi mới đổi reference; audit runtime file thực, không giả định file còn ở workspace.
- Xong khi: FE hiển thị avatar upload cũ/mới theo phạm vi được giữ; replace/delete và restart không hỏng liên kết; không mở quyền cho đề thi/result khi phục vụ avatar.

### Giai đoạn E — AI contract, AiJob/API và SQS

**BE1-11 — Chốt contract có fixtures. Ước lượng: 0,5–1 ngày, có thể làm sớm cùng giai đoạn A.**

- Với AI/BE2/FE chốt schemaVersion, job/submission IDs, full SHA, snapshot/rubric versions/hash, modelConfigVersion/processingVersion, deadline/completion và safe errors. Bắt buộc ba fixtures tuần 2: một job, một success result, một failure result. STARTED fixture chỉ thêm khi nhóm chọn progress event.
- Submission hiện chưa có version field: chọn cơ chế revision/snapshot identity thật, không ghi một version hư cấu vào contract.
- Phân biệt ID cuộc thi với ID của message/event; chốt dedup identity và HTTP error semantics, FE polling/terminal states.
- Với Lead chốt ai tạo/xem; assignment revoke; coordinator đúng event; cửa sổ tạo job và quyền xem lịch sử; quy tắc tạo lại.
- Chốt mô hình đề xuất PENDING_DISPATCH → QUEUED → SUCCEEDED/FAILED: PENDING_DISPATCH nghĩa job+outbox đã commit nhưng chưa xác nhận send; QUEUED chỉ sau SendMessage thành công. Không dùng trạng thái này để giấu input chưa được chuẩn bị xong. Chưa bắt buộc PROCESSING/RUNNING khi worker chưa phát progress.
- Deadline/EXPIRED trong plan trước chưa có ở sơ đồ trạng thái proposal: giữ kiểm deadline và đối soát là yêu cầu cần xác nhận. Nếu chọn EXPIRED, ghi rõ transition/late result trước tích hợp; không bỏ qua silently hoặc gán timeout thành điểm0.
- Standard queue/event có thể đến trễ: completion có thể về trước transaction mark-dispatched. Thiết kế cập nhật có điều kiện cho phép terminal hợp lệ từ PENDING_DISPATCH khi đối chiếu được job/input; dispatcher không được ghi đè terminal thành QUEUED. Đây là ca test bắt buộc khi chọn state mới.
- Xong khi: ba fixtures bắt buộc, state/error policy và bảng quyền được xác nhận thực tế; STARTED/EXPIRED nếu chọn có contract rõ; Pending có owner/hạn phản hồi. Không tự đổi FE contract enum đã chốt khi chưa review.

**BE1-12 — Snapshot input cố định. Ước lượng: 1 ngày cho phạm vi repo nhỏ.**

- Validate repo/host/redirect/phạm vi được phép; resolve full commit SHA; giới hạn dung lượng nén/giải nén, path và số file.
- Snapshot/rubric/manifest/hash lưu S3; chính sách chọn file/ngôn ngữ/token do AI xác nhận. Không chạy script/build/code từ repo bài thi.
- Dùng prefixes inputs/, rubrics/, results/ làm giá trị dự kiến cấu hình theo BE2 outputs. PoC chọn repo nhỏ public được cho phép tại SHA cố định; private repo/submodule/LFS chưa tự đưa vào scope hỗ trợ, phải chốt trước nếu có nhu cầu.
- Chuẩn bị input ngoài transaction DB, recheck quyền và input identity trước tạo job runnable.
- Repo quá lớn/chậm: trả lỗi giới hạn rõ; nếu cần ingestion bất đồng bộ mới thì bổ sung thiết kế/API state được nhóm chốt, không tạo QUEUED runnable khi chưa có input.
- Xong khi: worker tải được đúng input versions/hash; lỗi chuẩn bị không tạo job giả và có cleanup object dư.

**BE1-13 — AiJob/outbox và ba API. Ước lượng: 1–1,5 ngày.**

- Thêm entity/repository cho AiJob/outbox và dấu event đã xử lý khi nhận result; có SQL migration mới tương ứng theo BE1-21, không dựa Hibernate update hoặc sửa SQL legacy.
- Lưu requester/submission/context, input/SHA/versions, status/timestamps/deadline, result reference/safe error; có concurrency control phù hợp DB.
- POST `/api/ai/submissions/{id}/jobs`: requester từ phiên; kiểm quyền; snapshot sẵn sàng; transaction ghi AiJob+outbox, trả 202 sau commit. Theo bản nháp tuần 2, state ban đầu PENDING_DISPATCH; chỉ áp dụng sau BE1-11 được xác nhận. GET status/fixtures dùng cùng enum.
- GET `/api/ai/jobs/{jobId}` và `/result`: kiểm quyền hiện tại, trả DTO allowlist, không expose entity/S3 internals/danh tính không cần thiết. Result AI không ghi Score chính thức.
- State tuần 2 theo BE1-11: PENDING_DISPATCH → QUEUED → SUCCEEDED/FAILED; cho phép completion hợp lệ trước mark-dispatched, terminal không mở lại. RUNNING/STARTED và EXPIRED là nhánh chỉ thêm sau khi contract xác nhận; không tạo hai state machines khác nhau ở DTO/DB/worker.
- Idempotency cho request POST, nếu chọn, cần key/fingerprint/unique DB, khác chống duplicate SQS; chốt với FE trước implement.
- Xong khi: API chạy bằng fixture/mock, revoke quyền chặn request tiếp theo; state/transaction/concurrent update có test phù hợp.

**BE1-14 — Dispatcher gửi job SQS. Ước lượng: 0,5–1 ngày.**

- BE1 viết client/dispatcher; BE2 bàn giao jobs queue/DLQ/URLs/IAM. Chỉ gửi metadata/reference, không gửi source/secret.
- Đọc/claim outbox đã commit, gửi message rồi mark sent; retry/backoff/recover khi backend restart. Giữ transaction/lock DB ngắn, không cần khóa trong lúc chờ network.
- Nếu dùng PENDING_DISPATCH, mark QUEUED có điều kiện trong bước đánh dấu phát; không ghi đè SUCCEEDED/FAILED/EXPIRED nếu consumer đã nhận result trước. Send lỗi giữ ý định phát và trạng thái phù hợp; không báo đã queued khi AWS chưa nhận.
- Send xong chưa mark có thể gửi lại: worker/result processing phải chấp nhận duplicate; không hứa inference exactly-once.
- Xong khi: queue lỗi sau tạo job rồi phục hồi vẫn gửi được; restart không làm mất việc cần gửi.

**BE1-15 — Consumer nhận progress/result, expiry và retry. Ước lượng: 1–1,5 ngày.**

M0 bắt buộc completion success/failure, duplicate/stale và commit-before-ack; progress STARTED/RUNNING chỉ bật khi BE1-11 chốt. Expiry contract chưa chốt phải ghi Pending và chốt trước khi chạy job có deadline thật, không tự bịa timeout.

- Long-poll results queue bằng worker loop có giới hạn thread/backoff/graceful shutdown; cấu hình HTTP timeout phù hợp polling.
- Kiểm schema/job/SHA/versions và reference S3 thuộc job/prefix được phép; kiểm result size/hash/schema/evidence path-dòng theo snapshot.
- Transaction chống trùng và cập nhật state/result nguyên tử; **DB commit trước DeleteMessage**. Duplicate hợp lệ là no-op; DB lỗi chưa ack. Quản lý visibility nếu xử lý dài.
- STARTED muộn không hạ terminal; expiry cạnh tranh an toàn với completion; result trễ không mở lại job hoặc ghi đè lần đánh giá mới.
- Phân loại lỗi tạm thời, output invalid, message sai/unknown job; không để message không tin cậy tự làm job bất kỳ thành FAILED. DLQ/replay theo runbook, không coi DLQ tự cập nhật DB.
- Xong khi: mock worker round trip; duplicate, đảo thứ tự, DB lỗi, offline, sai hash/version và quá hạn xử lý đúng. AI worker ghi S3/gửi completion thành công rồi mới ack jobs theo contract.

**BE1-20 — Nghiệm thu PoC tuần 2 bằng worker mock. Ước lượng: 0,5–1 ngày tích hợp sau khi các phần cần thiết sẵn sàng.**

- Dependency: BE1-01/02/03/06/08, một luồng mẫu từ09, contract11, snapshot12, API13, dispatcher14, completion consumer15, logging tối thiểu16, deploy handshake17 và quyền/config19. Đây là kiểm tra dọc từng phần tối thiểu, không đòi mọi acceptance của toàn roadmap đã xong.
- Chạy backend trên EC2 đúng SHA/image digest, DB PoC và S3/SQS thật; mock worker xử lý một job mỗi lần. Mock result gắn nhãn/mock model metadata đúng, không coi là benchmark AI thật.
- Kịch bản: user hợp lệ upload/read một file S3 mẫu → tạo job → PENDING_DISPATCH/QUEUED theo contract → mock đọc input → ghi result S3 → gửi results queue → backend commit → GET result đọc lại.
- Thử wrong-user/file scope và job/result access; gửi lại success result không nhân đôi tác dụng; result của job cũ không ghi đè đánh giá mới; dispatcher không hạ terminal khi mark send muộn.
- Restart backend rồi đọc lại job/file/result; tìm log theo jobId và xác nhận outbox/DB state. Phối hợp BE2/AI thử offline/crash/DLQ trong ca test được thống nhất; BE1 đối chiếu DB, không kết luận DLQ tự đổi FAILED.
- Gói bằng chứng: SHA/digest/engine version, config không chứa secrets, jobId/S3 refs nội bộ đã lọc khi chia sẻ, request-response theo quyền, test duplicate/stale/restart và log/PR. Không báo mốc đạt chỉ bằng Console screenshot tài nguyên tồn tại.
- Xong khi: Lead/BE2 và owner liên quan review kết quả buổi mock; ghi cụ thể phần Passed/Failed/Pending. M0 không tự đánh dấu model thật, browser OAuth/domain hoặc data cutover hoàn tất.

### Giai đoạn F — Log, deploy và nghiệm thu

**BE1-16 — Log/error/health/version. Ước lượng: 0,5–1 ngày, làm dần từ giai đoạn A.**

- Generic exception handler không trả raw ex.getMessage ra client; ghi errorCode/requestId và chi tiết nội bộ an toàn.
- Log các mốc request/storage/job dispatch/result commit/reject với jobId/event ID/attempt/duration theo nhu cầu; không log secret/token/full source/raw output không cần thiết; hạn chế polling log rỗng.
- Tắt SQL logging không cần thiết ở AWS. Audit nghiệp vụ giữ theo service hiện có.
- Thống nhất health/version tối thiểu dùng cho deploy; liveness khác DB readiness; output công khai không lộ connection/config. Không cần xây hệ thống giám sát riêng.
- Xong khi: BE2 kiểm được app/version và từ requestId/jobId tìm được bước lỗi; endpoint health bảo vệ/giới hạn theo thiết kế đã chốt.

**BE1-17 — Bàn giao runtime và hỗ trợ BE2. Ước lượng: 0,5–1 ngày phối hợp.**

- Giao PR/commit, build/test evidence, config matrix, entity/schema policy, API fixtures, IAM actions/prefix code cần, smoke test.
- Review cùng BE2 Caddy/compose AWS: backend đúng env, không còn mysql local override/dependency; FE CloudFront không cần frontend container tại EC2; giữ route API/OAuth/avatar/health theo contract.
- BE2 làm ECR/digest/SSM/pipeline/DNS/TLS/CloudWatch; BE1 hỗ trợ lỗi ứng dụng và xác minh sau deploy. Không tự dựng song song một pipeline khác.
- FE build lại với VITE_API_URL mới; kiểm OAuth/CSRF/avatars và SPA deep-link/cache khi lên CloudFront.
- Xong khi: BE2 deploy được đúng code/config; backend qua health/smoke tests; rollback có xét schema/file và dữ liệu phát sinh sau cutover.

**BE1-18 — Nghiệm thu AWS/model thật và dữ liệu. Ước lượng: 1–2 ngày + dự phòng tích hợp.**

- Chạy trên MySQL8.4 thật trước, RDS/S3/SQS thật sau; worker giả rồi model thật và FE.
- Kiểm auth browser/domain thật, wrong-role/event/team/assignment, file restart, job state/retry/duplicate/deadline, logs và schema runtime.
- Nếu giữ dữ liệu cũ: restore rehearsal và xác minh API/file references cùng BE2 trước cutover; không dùng script reset/demo làm restore.
- Lưu commit/engine version/jobId/log đã lọc/test results và người review; không tự ghi “đã duyệt” khi chưa có phản hồi.
- Xong khi: M1/M2/M3 có bằng chứng và giới hạn còn lại được ghi rõ; Lead nghiệm thu mốc tương ứng.

## 5. File sẽ sửa/tạo khi bắt đầu code

Các Java path rút gọn trong bảng nằm dưới `back-end/src/seal-api/src/main/java/com/seal/hackathon/`.

| Nhóm | File hiện có | File/thành phần mới dự kiến |
|---|---|---|
| Config | resources/application.properties, test/resources/application-test.properties, .env.example | application-local.properties/application-aws.properties, typed config/validation khi cần |
| SQL migrations | pom.xml, profiles, AwsEnvironmentConfiguration, seed/demo/tests hiện có | db/migration/V1__create_core_schema.sql và migrations feature sau, profile/tests MySQL integration; giữ JPA/entity |
| Bootstrap/JWT | config/DataSeeder.java, config/seed/*, security/JwtService.java | BootstrapAdminSeeder hoặc tách trách nhiệm tương đương |
| Auth/domain | config/SecurityConfig.java, security/JwtCookieFactory.java, security/oauth2/*Handler.java, service/EmailService.java | Origin/config validation khi cần |
| S3 | service/AuthService.java, service/TrackProblemService.java, config/WebMvcConfig.java, controller/TrackProblemController.java, entity/Track.java | ObjectStorage + Local/S3 implementation, AWS clients, avatar read route phù hợp |
| AI | controller/AiController.java; service/AiJudgeAssistantService.java được giữ/chuyển theo contract FE | AiJob/Outbox/ProcessedEvent, repositories, AiJobService/access/validator, snapshot service, dispatcher, result consumer, DTO/contracts |
| Vận hành | exception/GlobalExceptionHandler.java, config, pom.xml | Request/job logging, health/version, smoke-test/runbook |
| Tests | Test hiện có liên quan auth/service/config | Test quyền/state/transaction và MySQL8.4/AWS integration theo nhu cầu |
| Phối hợp BE2/FE | Caddyfile, docker-compose.yml, .github/workflows/deploy.yml, FE shared/api/core.ts và build config | Compose AWS/release config chủ yếu BE2; BE1 cung cấp yêu cầu/review |

Theo yêu cầu mới, tạo db/migration bằng taskBE1-21 từ mappings/DDL đã review; không dùng seal_*.sql legacy làm baseline mặc định. Seeders và dữ liệu cần chuyển có owner/policy riêng, không trộn credential/data thật vào schema migration.

## 6. Danh sách cấu hình cần bàn giao

| Cấu hình | BE1 cần làm | Nguồn giá trị |
|---|---|---|
| SPRING_PROFILES_ACTIVE | Cơ chế chọn local/test/aws rõ ràng | Runtime BE2 |
| APP_FRONTEND_URL | Đọc/validate origin, dùng CORS/redirect/email theo thiết kế | Lead/FE/BE2 sau chốt domain |
| VITE_API_URL | BE1 cung cấp API origin/routes, FE build theo giá trị | FE/BE2 |
| JWT_SECRET | Bắt buộc/hợp lệ ở AWS, không có fallback nhạy cảm | Secret runtime BE2/owner |
| JWT_COOKIE_SECURE, CSRF_COOKIE_DOMAIN | Phù hợp HTTPS/cùng domain gốc | BE1 định nghĩa, BE2 cấp |
| SPRING_DATASOURCE_URL, DB_USERNAME/DB_PASSWORD | App đọc đúng; TLS/user/database được kiểm | BE2 DB/secret |
| DDL_AUTO / Flyway runner policy | Migrate trước rồi Hibernate validate ở MySQL runtime đích; identity/privilege theo runner thực | BE1 + BE2 |
| GOOGLE/GITHUB OAuth configs | App callback/feature config đúng | Owner OAuth + BE2 |
| MAIL config | Giữ/cấu hình theo nhà cung cấp được chọn | Owner mail/BE2 |
| Region/bucket/prefix/queue URLs | BE1 chọn property/biến chính thức trong code, ghi ví dụ | BE2 outputs |
| Storage/AI feature flags | Tên cụ thể chốt khi code; local/test không tự chạy AWS consumers | BE1, BE2 chọn runtime |

S3/SQS feature phải có names/config thật được code đọc; không dùng placeholder khái niệm như một biến đã implement. Secret được cấp ngoài repo/image/log. Nếu BE2 chọn Parameter Store thì thống nhất họ inject runtime hay app đọc trực tiếp; không tự thêm SDK SSM khi chưa cần.

**Đầu ra thêm cho BE1-02/19:** một config matrix dùng chung để BE2 không cấp nhầm tên biến. Có thể đề xuất AWS_REGION, APP_S3_BUCKET, APP_S3_INPUT_PREFIX=inputs/, APP_S3_RUBRIC_PREFIX=rubrics/, APP_S3_RESULT_PREFIX=results/, APP_SQS_JOB_QUEUE_URL và APP_SQS_RESULT_QUEUE_URL; đây là tên dự kiến, phải thống nhất property mapping và code thật trước bàn giao. Không ghi queue URLs/bucket/endpoint dự kiến thành “đang hoạt động”. Prefix file đề thi tách riêng theo policy bucket.

## 7. Kiểm thử theo mốc

| Mốc | Những kiểm tra bắt buộc |
|---|---|
| M0 tuần 2 | Đúng commit/digest EC2, RDS PoC, file S3 mẫu có quyền, ba API job, một vòng SQS/mock, duplicate/stale/mark-dispatched race và restart; log/request-response theo jobId |
| Config | Local/test chạy; AWS thiếu secret bắt buộc fail rõ; không gọi AWS trong unit test; bootstrap opt-in |
| Auth | Origin cho phép, credentials, cookie login/logout, cold-start CSRF, OAuth callbacks và redirect, revoke quyền |
| DB | SQL migrations chạy trên MySQL8.4, Hibernate validate, history/restart/constraints và nghiệp vụ; H2 unit không được coi là migration acceptance |
| Files | Upload/read checksum, wrong scope, release/retract, DB failure sau put, replace/cleanup, redeploy không mất file |
| AI | AiJob+outbox atomic, queue lỗi/recover, duplicate/state ordering, validation, commit-before-ack, offline/expiry/late result |
| Deploy | RDS/S3/SQS runtime role/config thật; health/version/digest; logs theo ID; browser FE/API domain thật; restore nếu giữ dữ liệu |

Không viết test chỉ lặp lại implementation; tập trung hành vi/quyền, transaction/race và failure paths. Mock chỉ chứng minh logic/contract, không nghiệm thu AWS/model. Chạy bộ test liên quan sau mỗi thay đổi và broader suite trước bàn giao; không chạy lại vô hạn khi chưa có thay đổi/lỗi mới.

## 8. Thứ tự PR và lịch tích hợp đã cập nhật

| PR/đợt | Task chính | Có thể bắt đầu khi chưa có AWS/domain? |
|---|---|---|
| PR1 — Config, bootstrap và integration handshake | 01–03 +19 | Có; chốt contract11 cùng lúc, không chờ PR xong |
| PR2 — SQL migration/MySQL8.4 và file S3 mẫu | 21 +06, sau đó08 + phần tối thiểu09 | Entity/SQL/config trước, MySQL lab mới; nhánh dump chờ đầu vào, AWS chờ BE2 |
| PR3 — Contract/snapshot/API AI | 11–13 | Fixtures/mock trước; contract cần AI/FE/Lead xác nhận |
| PR4 — SQS/mock và M0 tuần 2 | 14–15 + phần tối thiểu16/17 +20 | Local test trước; tích hợp thật chờ queues/IAM/runtime và owner mock |
| PR5 — Domain/OAuth và persistence đầy đủ | 04–05 +09 còn lại +10 | Code/test trước; domain/provider thật chờ owner |
| PR6 — Dữ liệu thật và deploy hardening nếu thuộc scope | 07 +16/17 còn lại | Sau M0 và quyết định giữ/chuyển dữ liệu |
| PR7 — Model thật/FE và nghiệm thu đầy đủ | 18 | Sau M0, benchmark/input limits và lịch AI/FE |

Đây là nhóm review, không bắt buộc đợi PR trước merge mới viết fixtures hay log cho PR sau. Chốt contract AI sớm và thêm test/log cùng từng phần. Ước lượng sơ bộ toàn phần BE1 khoảng 12–18 ngày công tập trung, chưa gồm chờ phản hồi/hạ tầng và dữ liệu lớn; task19/20 chủ yếu là coordination/verification đã tách rõ, cần điều chỉnh capacity theo lịch thực. Đây không phải deadline cam kết hoặc bằng chứng mọi task sẽ xong trong tuần2.

**Nhịp triển khai M0:** (A) BE1-01/02/03/19 và BE1-11 song song theo phản hồi; (B) BE1-21 core migration/config/test rồi06/08 và một file mẫu09, snapshot12; (C) BE1-13/14/15 cùng migrations feature và log/deploy handshake; (D) BE1-20. Chuyển schema-management làm thay đổi capacity của lịch cũ: ước lượng12–18ngày trước đó chưa là cam kết mới và phải tính lại, không cộng máy móc phần overlap. Nếu quá sức chứa hoặc chậm hạ tầng/contract, báo Lead điều chỉnh mốc; không tự bỏ kiểm quyền/atomicity/idempotency để giữ lịch.

**Buổi triển khai đầu tiên:** BE1-01, bắt đầu BE1-02, mở checklist BE1-19 và chuẩn bị ba fixtures cho BE1-11 để xin xác nhận. Đầu ra là baseline test, config matrix/profile skeleton và danh sách quyết định state/access/schema. Chưa đổi datasource môi trường chính, domain, DB volume hoặc dữ liệu hiện hữu.

## 9. Quyết định còn chờ xác nhận với proposal tuần 2

| Quyết định | Bản nháp/đề xuất hiện tại | Người cần xác nhận | Trạng thái |
|---|---|---|---|
| Owner producer/consumer/outbox/snapshot | BE1 theo phân công đã nhận; đề nghị ghi lại trong biên bản phối hợp | BE1 + BE2 + AI | Chưa có sign-off liên nhóm |
| Schema bootstrap | Người dùng đã yêu cầu kế hoạch Flyway/SQL, giữ entity/JPA và Hibernate validate | BE2 + Lead về revision/case/runner/privileges | Kế hoạch đã cập nhật, code chưa triển khai |
| DB/version | DB PoC fcaj_poc, thử8.4 riêng, không chuyển dữ liệu thật | BE1 kiểm + BE2/Lead | Dự kiến, chưa lab |
| Đường test/identity | Ưu tiên deploy chung; local S3/SQS chỉ khi có scope/ca test riêng | BE1 + BE2 | Pending |
| Prefix/resources/config names | inputs/rubrics/results + outputs thực từ BE2 | BE1 + BE2 + AI | Pending |
| States/progress/deadline | PENDING_DISPATCH/QUEUED/success/failure bản nháp; progress chưa bắt buộc; deadline/EXPIRED phải có quyết định trước ca chạy liên quan | BE1 + AI + FE/Lead | Pending |
| Ba JSON fixtures và revision identity | Job/success/failure; chưa giả định submissionVersion hiện hữu | BE1 + AI, BE2 transport, FE DTO | Pending |
| Owner mock/lịch tích hợp | AI mock được đề xuất; worker xử lý một job/lần | AI + BE1 + BE2 | Pending |
| Commit/digest, API URL/log, ngân sách/lịch tài nguyên | Theo outputs/release và quyết định owner | BE2 + Lead | Pending |

## 10. Project và phần Scrum Master theo phân công

- Mỗi BE1-xx là issue có owner, reviewer, đầu ra, acceptance, dependency và hạn dự kiến; việc lớn tách checklist nửa ngày đến một ngày.
- Board gợi ý Backlog → Ready → In progress → Review/Test → Done; nhãn Blocked có người xử lý và thời điểm cập nhật lại.
- Mỗi ngày cập nhật kết quả kèm PR/test, việc tiếp theo, blocker và dự báo hoàn thành; dành 10–20 phút rà dependency thay vì chỉ báo phần trăm.
- Báo Lead ngay khi dự báo trễ milestone hoặc dependency chặn luồng chung; blocker nhỏ chưa có hướng gỡ sau một ngày làm việc đưa lên xử lý. Đây là quy ước đề xuất để nhóm thống nhất.
- Không tự gửi tin nhắn cho đồng đội hoặc cập nhật Project online trong bước lập kế hoạch này.
- Theo dõi riêng BE1-19/11: ai cấp config/quyền, ai chốt fixtures/state, ai chạy mock và hạn phản hồi; đây là đường phụ thuộc trực tiếp M0. Theo dõi cả task BE2/AI liên quan qua thông tin nhóm cung cấp, không chỉ việc code BE1.

## 11. Phạm vi chưa thực hiện

Mới đối chiếu proposal tuần2 và cập nhật task/ưu tiên trong file này. Giữ proposal nguồn nguyên trạng; không sửa file ngoài repository, không đánh dấu checkbox xác nhận trong proposal hoặc tự gửi tin nhắn/cập nhật Project online. Chưa chạy baseline test mới, sửa source/config chạy, thêm dependency, tạo branch/commit, tạo AWS resource hoặc tác động DB/file hiện hữu. Các điều kiện domain, dữ liệu cần giữ, contract và môi trường được ghi dependency; phần độc lập vẫn bắt đầu được.

## 12. Checklist toàn bộ công việc của bạn theo thứ tự thực hiện

Đây là bảng dùng để theo dõi hằng ngày; chi tiết thao tác và acceptance ở mục4. **M0** là bắt buộc cho PoC mock; **sau M0** là phần vẫn thuộc scope tổng thể nhưng không chặn buổi tích hợp đầu. Mọi checkbox bên dưới đều còn chưa đánh dấu, không suy ra tiến độ code từ việc đã lập kế hoạch.

| Thứ tự | Task | Phạm vi | Đầu ra để review | Phụ thuộc cần gỡ |
|---|---|---|---|---|
| 1 | [ ] BE1-01 Hiện trạng/test | M0 | Baseline test, commit, config inventory, PoC chỉ dùng dữ liệu mẫu | Commit baseline với Lead/BE2 |
| 2 | [ ] BE1-19 Bàn giao tích hợp | M0, làm song song | Outputs/config matrix, identity test, owner mock, lịch tích hợp | BE2/AI/Lead phản hồi |
| 3 | [ ] BE1-11 Contract | M0, làm sớm | Job/success/failure JSON, bốn state, quyền, revision, deadline/error policy | AI/BE2/FE/Lead review |
| 4 | [ ] BE1-02 Profiles/config | M0 | Local/test/aws chạy đúng config, .env.example không chứa secret | Config names với BE2 |
| 5 | [ ] BE1-03 Bootstrap/JWT | M0 | Bootstrap opt-in, role/template giữ được, secret validation | Secret injection từ BE2 khi chạy AWS |
| 6 | [ ] BE1-06 MySQL8.4/Hibernate | M0 | Schema từ entity và CRUD/restart lab/RDS có bằng chứng | BE2 DB/network/TLS/DDL privileges |
| 7 | [ ] BE1-08 Storage adapter | M0 | Local/S3 implementations, bytes/hash và lỗi được kiểm | BE2 bucket/IAM/prefix |
| 8 | [ ] BE1-09 File đề thi có quyền | Một luồng mẫu cho M0; hoàn thiện tiếp nếu còn | Upload/release/download/retract, wrong-user/event bị chặn | Dữ liệu fixture hợp lệ và staff policy |
| 9 | [ ] BE1-12 Snapshot | M0 | Repo full SHA + rubric + manifest/hash trên S3 | Contract11, storage08, AI input policy |
| 10 | [ ] BE1-13 AiJob/outbox/API | M0 | Entity/repository/service/DTO, ba API và quyền | Contract11, schema06, snapshot12 |
| 11 | [ ] BE1-14 Gửi jobs SQS | M0 | Outbox dispatcher/retry, mark-dispatch an toàn | API13, BE2 jobs queue/IAM |
| 12 | [ ] BE1-15 Nhận result SQS | M0 | Validate, atomic state/dedup, commit-before-ack | Contract11, API13, BE2 results queue, AI fixtures |
| 13 | [ ] BE1-16 Log/health/version | Tối thiểu cho M0, thêm dần từ đầu | Tìm lỗi theo request/job, health/version đúng | BE2 log access và health route |
| 14 | [ ] BE1-17 Gói deploy | Tối thiểu cho M0 | Build/config/API fixtures, PR/SHA, smoke test và schema policy | BE2 image/digest/runtime/Caddy |
| 15 | [ ] BE1-20 Buổi mock AWS | M0 | Vòng EC2/RDS/S3/SQS thật, duplicate/stale/restart và bằng chứng | Các phần M0 trên + worker mock được phân công |
| 16 | [ ] BE1-04 Auth/domain hoàn chỉnh | Sau M0 nếu chưa có domain | Browser CSRF/login/me/write/logout, CORS/cookies đúng | Domain/TLS/FE, phần config làm trước được |
| 17 | [ ] BE1-05 OAuth/email | Sau M0 nếu provider chưa sẵn sàng | Callback/redirect/email đúng origin | Owner OAuth/mail, BE2/FE |
| 18 | [ ] BE1-10 Avatar S3 | Sau M0 | Upload/display/replace/delete và URL tương thích | Storage08, FE avatar contract |
| 19 | [ ] BE1-07 Dữ liệu cần giữ | Sau M0, chỉ khi có quyết định giữ/chuyển | Restore rehearsal/counts/API/file refs | Lead quyết định dữ liệu, BE2 backup/restore |
| 20 | [ ] BE1-18 Model thật/FE/nghiệm thu | Sau M0 | End-to-end thật, giới hạn model, data/deploy evidence và bàn giao | AI worker/benchmark, FE, BE2 runtime |
| Trước06 và13 | [ ] BE1-21 SQL migrations/Flyway | M0 core và migrations feature về sau | Inventory/V1/config/seeder/tests, nhánh restore nếu có dump, BE2 runbook | Current diff/revision, case choice, MySQL lab và runner BE2 |

Nếu một phần auth hiện tại chưa đáp ứng kiểm quyền của PoC thì sửa phần cần thiết ngay trong M0. Nhãn “sau M0” chỉ hoãn nghiệm thu hostname/browser/OAuth đầy đủ, không cho phép endpoint PoC bỏ xác thực hoặc phân quyền.

## 13. Công việc Scrum Master của bạn

| Mã điều phối | Việc cần làm | Nhịp/đầu ra |
|---|---|---|
| SM-01 | Đưa task lên Project, chia việc đủ nhỏ; owner/reviewer/acceptance/dependency rõ | Đầu đợt; backlog có thể review, không chỉ tên dịch vụ |
| SM-02 | Nhắc từng người cập nhật đã xong/tiếp theo/blocker/dự báo hoàn thành | Hằng ngày, khoảng10–20 phút điều phối; link PR/test khi có |
| SM-03 | Gỡ phụ thuộc contract/quyền/runtime/worker; báo Lead khi có nguy cơ trễ | Task/blocker có người xử lý và thời điểm kiểm tra lại |
| SM-04 | Chuẩn bị buổi tích hợp và tổng hợp bằng chứng/việc còn thiếu | Trước/sau M0 và từng milestone; Passed/Failed/Pending rõ |

Backend2/AI tự chịu trách nhiệm cập nhật việc của họ; bạn giúp thông tin và phụ thuộc không bị bỏ sót. Phân công/đổi ưu tiên/ngân sách do Lead/owner tương ứng quyết định. Lần lập kế hoạch này chỉ chuẩn bị checklist, chưa thao tác Project hoặc nhắn đồng đội.

## 14. Buổi làm việc đầu tiên và đầu ra phải có

1. **Baseline:** xác nhận working tree/commit; chạy test hiện có với Java21/profile test; ghi lỗi đang có, không đổi DB runtime để chữa test.
2. **Config:** lập bảng property/biến môi trường thật từ source; đề xuất tách local/test/aws; xác định secret bắt buộc và feature có thể tắt để local/test chạy.
3. **Phối hợp:** chuẩn bị checklist19 và ba fixtures11 để người dùng trao đổi với BE2/AI/FE; cập nhật hướng SQL migrations mới và chốt revision/case/runner với BE2.
4. **Review đầu tiên theo hướng mới:** DB-01/02 rà current diff và inventory, rồi dependency/SQL baseline/config theo BE1-21. Giữ code profiles/bootstrap hiện có, không viết lại hay gộp storage/AI/domain thực vào cùng một thay đổi lớn.

Kết thúc buổi đầu cần có baseline test report, config matrix, skeleton cấu hình có thể review khi bắt đầu code và danh sách câu hỏi contract/outputs. Không cam kết mọi đầu ra đã thực hiện chỉ vì được liệt kê trong kế hoạch.
