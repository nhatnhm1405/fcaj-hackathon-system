# Plan sửa và thảo luận BE1–BE2 trước deploy AWS

Ngày lập: **03/11/2026**, Asia/Saigon. Trạng thái: **đề xuất để thảo luận; chưa bắt đầu sửa implementation theo plan này**.

**Thứ tự thực thi cập nhật:** dùng [plan giao AI hoàn thiện BE1 local trước báo cáo](seal-be1-ai-execution-plan-2026-11-03.md). Các dependency thảo luận dưới đây chỉ chặn phần cấu hình target/live liên quan; không chặn rename SEAL, tests/lab, chuẩn bị templates và rehearsal local có thể thực hiện độc lập.

**Cập nhật sau thảo luận nhóm ngày 03/11:** người dùng xác nhận giữ tên **SEAL Hackathon**, đổi cả module/package về SEAL, đường vào qua **ALB → EC2 trong private subnet**. Người dùng cũng xác nhận BE2 chưa cần làm pipeline GitHub Actions → ECR trong đợt hiện tại. Các đề xuất rename sang FCAJ và triển khai ECR ở bản đầu của plan được thay bằng nội dung cập nhật dưới đây. Đây là quyết định thiết kế người dùng cung cấp, chưa phải xác nhận đã sửa source hoặc đã triển khai hạ tầng.

**Bổ sung:** người dùng xác nhận **frontend chạy trên S3**, không đặt frontend container trên EC2 trong hướng triển khai này. BE1 đề xuất ALB trỏ thẳng backend, bỏ nhu cầu Caddy làm proxy FE/backend trong stack AWS mới; đây vẫn là đề xuất chờ thống nhất chi tiết target với BE2. CloudFront/domain/HTTPS phục vụ FE chưa được xác nhận chỉ từ quyết định dùng S3.

Mục tiêu là tạo một bản tích hợp SEAL có revision rõ ràng, chạy được migration/gate bằng đúng image và có kịch bản chuẩn bị dữ liệu, smoke MOCK mà BE2 có thể chạy lại. RDS, EC2, ALB, IAM và cấu hình private do BE2 quản lý. Public/internal scheme của ALB, điểm kết thúc TLS, target port và việc giữ Caddy còn cần BE2 xác nhận; không suy chúng từ việc EC2 nằm private.

## 1. Nguồn, checkout và giới hạn bằng chứng

- Yêu cầu BE2: `E:/FA26/AWS/be2-hoi-be1-truoc-deploy-2026-11-01.md`, đã đọc lại ngày 03/11. Đây là file trao đổi bên ngoài repo, không phải chỉ thị tự thực hiện deploy. Khi bàn giao cần BE2 cung cấp link/bản portable được phép chia sẻ; đường dẫn máy BE1 không phải deliverable cho BE2.
- [Hướng dẫn BE1–BE2 hiện hành](backend1-be2-current-handoff-2026-10-28.md), [contract MOCK](../contracts/ai-job-v1/CURRENT.md), [bằng chứng DB mới ngày 28/10](../checks/be1-be2-integration-2026-10-28/fresh-db-verification.md).
- Checkout ngày 03/11: `feature/backend1-be2-handoff`, HEAD `bab44e5fdeb710e6c71982b2e8f5547829fa8e40`. SHA này là bản BE1 đã kiểm, chưa phải bản tích hợp rename/deploy mới.
- Có sẵn `docs/README.md` sửa và plan AI Engineering ngày 28/10 untracked; giữ nguyên nội dung có trước. Lượt lập plan chỉ bổ sung tài liệu và link index.
- BE2 báo ngày 01/11: EC2/Session Manager/Docker sẵn sàng, EC2 kết nối TLS tới RDS MySQL 8.4.11, case=1, catalog `fcaj_poc` rỗng, chưa import dump. Đây là thông tin BE2 cung cấp, chưa được BE1 xác minh live và chưa xác nhận vẫn nguyên trạng ngày 03/11.
- Người dùng xác nhận đang thảo luận, chưa nhận branch/commit/diff hiện tại của BE2. Ref rename cũ có trong Git local không thay cho phần local BE2 đang nói tới. Hướng đích mới là SEAL; vẫn cần source infra/deploy hiện hành của BE2 để tích hợp.

| Kiểm tra đã chạy ngày 01/11 trên source BE1 | Kết quả | Giới hạn |
|---|---|---|
| Hai test BE2 nêu trong `AiJobIntegrationTest` | Đạt trong smoke và full suite | Chưa tái hiện hoặc xác định nguyên nhân lỗi ở máy BE2 |
| Smoke `AiJobIntegrationTest` + `AwsMockWorkerTest` | 25/25 đạt | H2, GitHub/S3/SQS giả lập |
| Full backend test/package | 622 được thống kê; 613 thực thi đạt, 9 MySQL-only skipped | Không phải 622 test thực thi; không cộng smoke trùng vào tổng |
| Deploy command-order tests | 8/8 đạt | Docker/Git giả lập, không deploy |
| Contract samples và bundle | Samples hợp lệ; 494 checksum khớp HEAD | Không chứng minh AWS hoặc working tree mới đã kiểm |
| MySQL lab chạy lại ngày 01/11 | Chưa hoàn tất do Docker Engine chưa sẵn sàng | 36/36 MySQL ngày 28/10 là evidence lịch sử |

Log ngày 01/11 còn ở `back-end/src/fcaj-api/target/be2-review-2026-11-01-*.log`, là artifact local bị ignore. Lượt đầu sandbox gặp lỗi Mockito attach/HTTP loopback/Git Bash IPC; lượt host đạt. Không ghi các lỗi môi trường này thành lỗi ứng dụng, cũng không dùng chúng để giải thích thay cho log BE2 chưa nhận. Ngày 03/11 chỉ đọc lại log, không chạy lại test.

## 2. Phân loại việc cần làm

| Nhóm | Việc | Cách xử lý |
|---|---|---|
| Đổi hướng tên đã xác nhận | Module/package hiện `fcaj-api`/`com.fcaj.hackathon`, đích mới SEAL; `seal-backend`, `seal-web`, `/etc/seal/...` đang phù hợp tên giữ lại | Rename có kiểm soát module/package về SEAL; giữ các tên SEAL phù hợp, không revert cả commit BE1 |
| Đường vào mới | ALB → EC2 private; template đang bind backend loopback cho proxy local | Chốt ALB direct hay qua Caddy; sửa mapping port/headers/health theo target thực |
| Khác cách vận hành | Workflow hiện SSH từ GitHub-hosted runner; EC2 mới private, BE2 chưa cần ECR pipeline | Chốt cách operator/build/deploy có đường mạng phù hợp; giữ gate cùng image, chưa làm ECR pipeline |
| Thiếu bộ chạy lại | Chưa có runbook chuẩn bị dữ liệu qua API đã kiểm từ DB rỗng | Chuẩn bị runbook/request mẫu, rehearsal trên lab |
| Thiếu bằng chứng tích hợp | Chưa có MySQL/test/image evidence cho revision tích hợp mới; chưa có IAM policy thực | Kiểm lại candidate và nhận đầu vào BE2 |
| Chưa xác nhận lỗi | Hai test dispatch fail ở BE2 nhưng pass trên BE1 | Thu log/SHA/lệnh/môi trường, chỉ sửa khi có nguyên nhân |
| Đã có, cần hướng dẫn rõ | V1–V3, initializer, verify gate, bootstrap, API/worker MOCK | Dùng lại implementation và bổ sung hướng dẫn; không viết lại chỉ để đáp ứng tài liệu |

Phạm vi đợt này giữ contract MOCK, bốn state, quyền event/assignment, cookie/CSRF và kết quả không sửa `Score`/`RoundResult`. Model thật, FE job UI, lifecycle mới, restore dữ liệu cũ và chuyển domain/browser acceptance đầy đủ là các đầu việc riêng nếu được nhóm chọn. Việc sửa rename không tự yêu cầu migration V4; giữ nguyên bytes V1/V2/V3 đã phát hành.

## 3. Các quyết định cần thảo luận trước phần phụ thuộc

| ID | Nội dung | Đề xuất của BE1 để thảo luận | Ai cần xác nhận | Trạng thái |
|---|---|---|---|---|
| D01 | Bộ tên và source tích hợp | Giữ SEAL Hackathon; đổi module `fcaj-api` → `seal-api`, package `com.fcaj.hackathon` → `com.seal.hackathon`; giữ `seal-web`, `seal-backend`, `/etc/seal/deploy-settings.sh` | Người dùng xác nhận cả module/package; BE1 + BE2 phối hợp source | Hướng SEAL đã chốt; chưa implement |
| D02 | Cách đưa image lên EC2 | Pipeline Actions → ECR để giai đoạn sau theo trao đổi người dùng/BE2. Đợt này vẫn cần source/image identity và migration/gate/runtime cùng candidate; nơi build/cách đưa artifact do BE2 thống nhất | BE2 chủ trì, BE1 review gate | ECR hoãn; cách build/delivery hiện tại chờ xác nhận |
| D03 | Người/cơ chế thao tác EC2 | EC2 private; đề xuất operator BE2 qua Session Manager cho mốc đầu nếu đã sẵn sàng. GitHub-hosted runner SSH trực tiếp chưa có đường mạng được xác nhận | BE2 | Chờ chốt thao tác; không yêu cầu mở EC2 public |
| D04 | Dữ liệu ban đầu | Dữ liệu giả tối thiểu cho smoke trước; restore `.bak` là đợt riêng | Người dùng/BE1 + BE2, BE2 quản lý RDS | Chưa quyết định |
| D05 | Chuẩn bị dữ liệu qua API hay công cụ riêng | Rehearsal qua API với guard hiện hành trước; chỉ thêm runner khi đã có chuỗi request hoạt động | BE1 đề xuất, BE2 thống nhất vận hành | Đề xuất |
| D06 | FE và mức nghiệm thu buổi đầu | FE chạy trên S3 theo xác nhận người dùng. Đề xuất backend + DB/TLS, file S3 và job MOCK trước; CloudFront/domain/HTTPS và browser acceptance cần chốt riêng | Người dùng chốt FE S3; BE1 + BE2 + FE chốt scope | FE S3 đã chốt; phần phục vụ HTTPS/scope còn pending |
| D07 | Worker và queue window | Một người chạy CLI MOCK, một backend results consumer theo lịch; thống nhất nơi chạy/identity và dừng consumer cạnh tranh | BE1 + BE2; AI nếu nhóm muốn tham gia | Chưa chốt |
| D08 | IAM/storage/queue thực | Nhận policy đã lọc, prefix, role/provider trong container, versioning/encryption và queue config thực | BE2 | Chờ đầu vào |
| D09 | ALB và target trên EC2 | Đã chốt ALB tới EC2 private. Với FE trên S3, đề xuất ALB direct backend, không cần Caddy cho FE/backend trong stack AWS mới; cần scheme/listener/TLS/target port/health/routing | Người dùng chốt topology và FE; BE2 chốt chi tiết với BE1 | Direct backend là đề xuất; chi tiết pending |
| D10 | Kết nối đi ra từ private EC2 | Kiểm đường tới RDS, S3/SQS/SSM, GitHub và nơi tải dependencies/images; NAT/proxy/endpoints theo thiết kế BE2, không mặc định private subnet đã có internet | BE2 | Chờ đầu vào |

Không cần chờ toàn bộ D01–D10 để chuẩn bị inventory file, runbook draft, ma trận cấu hình và thu log. Những phần triển khai phụ thuộc lựa chọn phải bám quyết định đã được ghi, không tự áp lên RDS/IAM/EC2.

## 4. Backlog chi tiết

Các path source trong mục này tính từ repository root. Những path `fcaj-api` mô tả file đang có trước PRE-01; sau rename phải dùng `seal-api` và launcher `com.seal.hackathon`. Path deliverable mới là **đề xuất**, chưa tồn tại hoặc chưa được implement chỉ vì được liệt kê.

### PRE-00 — Chốt source và thu đầu vào

**Owner đề xuất:** BE1 tổng hợp; BE2 cung cấp phần local. **Dependency:** không.

1. Ghi branch/full SHA/status của BE1; nhận branch/commit/diff hiện tại của BE2, gồm infra ALB/private EC2, workflow/Compose/proxy liên quan và file mới cần thiết. Hướng SEAL đã được người dùng xác nhận, không chờ một bản rename FCAJ để lấy làm đích.
2. Xin BE2 full log hai lỗi dispatch, SHA/lệnh chạy, JDK/OS, test profile, H2 hay MySQL, thời điểm và suite có chạy đồng thời không. Không yêu cầu file chứa secret.
3. Lập danh sách file chung và người sửa để tránh hai bên cùng thay workflow/Compose/gate theo hai hướng.
4. Nhận lại trạng thái target ngày chạy thực: engine/case/catalog/charset/collation, CA/network, runtime/migration identity, Docker Compose version. Không suy từ xác nhận ngày 01/11 rằng target hiện vẫn rỗng.

**Đầu ra:** bảng source + decision record D01–D10 + danh sách đầu vào còn chờ có owner. **Đạt khi:** không còn gọi một SHA cũ là bản chứa thay đổi chưa commit của BE2.

### PRE-01 — Đưa module/package về SEAL, giữ chức năng BE1 đã có

**Owner đề xuất:** BE1 module/package/tests/docs; BE2 cùng review path trong infra/deploy. **Dependency:** inventory PRE-00; hướng tên D01 đã được người dùng xác nhận.

Các thay đổi cần làm khi triển khai:

- Đổi thư mục `back-end/src/fcaj-api` → `back-end/src/seal-api`; cây Java main/test và các khai báo `package`/`import`/fully qualified class name từ `com.fcaj.hackathon` → `com.seal.hackathon`. Rà Maven group/mainClass và cấu hình có tên class; không tự đổi artifactId/JAR filename nếu không cần.
- Cập nhật launchers `HackathonApplication`, `DatabaseMigrationMain`, `AwsMockWorkerMain`, test gọi packaged CLI, compiler/runtime properties và script runner liên quan. Các method/logic migration/storage/jobs giữ nguyên.
- `.github/workflows/deploy.yml`, `docker-compose.yml`, Docker build command, Test runners, E2E/demo scripts, README/current handoff và bundle inventory dùng module mới `seal-api`.
- Giữ `front-end/src/seal-web`, service `seal-backend` và settings `/etc/seal/deploy-settings.sh` đang khớp quyết định SEAL. Nếu diff BE2 đã đổi sang FCAJ thì reconcile riêng các tham chiếu cần thiết.
- `deploy/deploy-settings.sh.example` hiện còn trỏ private files tới `/etc/fcaj/...`; đề xuất thống nhất về `/etc/seal/...` khi BE2 xác nhận vị trí operator thực. Không tự move/delete private files trên host.
- Root Compose/Caddy giữ vai trò dev hoặc proxy theo quyết định D09. Loopback mapping của template AWS được xử lý tại PRE-09, không giữ hoặc đổi mù quáng trong rename.

Không dùng `git revert` toàn bộ commit `bab44e5...` để đổi tên vì commit chứa chức năng BE1 mới. Không thay schema/table/migration bytes, namespace JSON `fcaj.ai.*`, catalog `fcaj_poc`, bucket/queue/prefix hoặc object keys chỉ vì đổi tên source: chúng đang có contract và tài nguyên tham chiếu. Nếu muốn đổi protocol/tài nguyên AWS thì tách quyết định riêng với AI/BE2.

**Kiểm chứng:** clean backend build/test ở `seal-api`, khởi chạy packaged migration/worker entrypoints trong test/lab; deploy tests, fixtures và Compose parse. FE test/build chỉ cần chạy theo phần FE/CI thực sự đổi. Tìm `fcaj-api`/`com.fcaj` trong file thực thi và phân loại mọi hit còn giữ; tài liệu/evidence lịch sử không viết lại thành kết quả mới. **Đạt khi:** chức năng BE1 được giữ, paths/main classes/tests/gate thống nhất tên SEAL.

### PRE-02 — Giữ migration, gate và runtime cùng candidate; ECR pipeline hoãn

**Owner đề xuất:** BE2 thao tác build/delivery/deploy; BE1 gate/templates. **Dependency:** D02/D03, PRE-01 và phần port/proxy PRE-09.

Theo trao đổi người dùng với BE2, đợt hiện tại chưa làm pipeline Actions → ECR. Không thêm yêu cầu phải có ECR digest để qua mốc local/AWS của đợt này. BE2 chốt build trên EC2 private hay đưa artifact bằng cơ chế hiện có; nếu build tại EC2 thì kiểm đường ra để lấy source/base images/dependencies tại PRE-09. Không mặc định đã chọn cách build chỉ từ việc hoãn ECR.

Trình tự cần triển khai/giữ:

```text
SHA tích hợp SEAL → test/build theo cách BE2 chốt → ghi image identity
→ kiểm Compose/runtime target
→ initializer khi chọn initialize-empty
→ verify-deploy bằng chính image đó
→ thay backend trong scope
→ health/version + readiness + ALB target health + smoke
```

- Mapping source SHA ↔ build ↔ local image ID hoặc registry digest theo cách giao artifact phải có evidence. Chỉ đặt `APP_BUILD_REVISION` bằng env không chứng minh nội dung image đúng SHA.
- Cả CLI migration, CLI verify và runtime dùng cùng image/truststore/target đã review; DB username có thể khác theo vai trò.
- Giữ fail-before-replacement; kiểm cả init fail, gate fail và image/Compose mismatch. Không in Compose config đã expand private env.
- Health check có bound sau start. Nếu health/version không đạt, dừng nghiệm thu và giữ log; rollback image theo phương án BE2, không hứa rollback DDL.
- Đề xuất operator BE2 qua Session Manager cho lần đầu nếu đã sẵn sàng. ALB phục vụ HTTP(S), không tạo đường SSH tới private EC2 cho GitHub-hosted runner. Workflow cũ không tự dùng được trên topology mới; CI/deploy tự động để task phối hợp sau, không mở EC2 public chỉ để giữ workflow cũ.

**Tests cần giữ/cập nhật theo thay đổi thực:** Compose không dùng candidate, migration/verify cùng candidate, gate fail không replace, init success rồi verify rồi replace đúng backend. Không gọi AWS trong unit command-order tests.

**Đạt khi:** trace được một candidate duy nhất từ source/build tới runtime; gate bảo toàn; phương án vận hành tới private EC2 khả thi mà không phụ thuộc pipeline ECR mới.

### PRE-03 — Điều tra hai test dispatch và kiểm chứng candidate

**Owner đề xuất:** BE1; BE2 cung cấp log/reproduce. **Dependency:** PRE-00; acceptance cuối sau PRE-01/02.

1. So SHA/log/lệnh/môi trường BE2 với bản ngày 01/11; kiểm có chạy profile khác, thiếu fixtures, DB không sạch hoặc build cùng thư mục song song không.
2. Reproduce bằng runner chuẩn trên DB riêng. Nếu còn lỗi, lưu job/outbox state, việc claim/send/mark sent/retry và exception đã lọc; không log request source/credentials.
3. Chỉ khi xác nhận nguyên nhân mới sửa `AiJobStore`, `JobDelivery`, test fixture hoặc runner tương ứng. Chưa có bằng chứng để chọn sẵn một production fix, đổ lỗi rename hay thêm sleep để làm pass.
4. Nếu là lỗi môi trường Mockito/loopback/Git Bash, chuẩn hóa hướng dẫn runner hoặc cấu hình test khi cần; giữ fail rõ ràng khi dependency lab thiếu.
5. Chạy full test/package, smoke và MySQL lab trên candidate tích hợp. Runner MySQL chỉ dùng loopback/container riêng; không trỏ test phá schema tới RDS.

**Đạt khi:** hai ca pass trên candidate, full suite pass với skips được giải thích; bộ MySQL chạy thực không skip; ghi SHA/JDK/OS/engine/lệnh/thời gian. Nếu lỗi BE2 chưa tái hiện thì kết luận đúng là chưa tái hiện, không ghi đã sửa root cause.

### PRE-04 — Hoàn thiện hướng dẫn migration và quyền DB

**Owner đề xuất:** BE1 runbook/CLI; BE2 tạo identity và vận hành RDS. **Dependency:** D04, PRE-02 cho lệnh image chính thức.

- Giữ initializer hiện có: kiểm runtime URL trước kết nối/ghi, MySQL 8.4.x/case=1/catalog/charset/collation; DB rỗng migrate V1→V2→V3, sau đó verify. Gate hiện yêu cầu V3.
- Viết lệnh Linux/container chính xác theo image đã chọn; file private migration/runtime tách riêng. Chỉ `source` file settings có shell syntax; `runtime.env`/`migration.env` là env-file, không mặc định source chúng như shell script.
- Hướng dẫn kiểm Flyway history/checksum/schema fingerprint và kết quả `verify-deploy`; không chỉ kiểm có đủ số bảng. Có 34 bảng ứng dụng cộng Flyway history khi khởi tạo chuẩn, nhưng số lượng không thay fingerprint.
- Ma trận quyền: migration identity có DDL/metadata và quyền cần chạy migrations trong catalog do BE2 review; runtime DML và đọc history khi schema current, không cần DDL. Kiểm lại Flyway no-op và Hibernate validate bằng runtime identity.
- Sau lần khởi tạo chuyển mode về `verify-only`. Không dùng initializer để nâng DB V1/V2 đang tồn tại; DB không rõ schema/history đi quy trình operator riêng.
- RDS 8.4.11 khác minor lab 8.4.13: chạy gate và JDBC TLS từ image thật trên target được BE2 chọn; kết nối CLI từ EC2 chưa thay cho CA/trust trong JVM container.
- Nếu nhóm chọn dữ liệu cũ: tách restore rehearsal/backup/schema compare/baseline V1/V2/V3 thành task có scope riêng. Không chạy tạo V1 lên dump đã restore.

**Đạt khi:** BE2 review được thứ tự/lệnh/quyền, lab mới lên V3, rerun bảo toàn dữ liệu, runtime no-op không cần DDL. Chạy lên RDS chỉ trong buổi đã phối hợp với BE2.

### PRE-05 — Làm rõ cấu hình bắt buộc, tùy chọn và quyền AWS

**Owner đề xuất:** BE1 requirement/templates; BE2 private config/policy thực. **Dependency:** có thể soạn ngay; chốt theo D02/D06/D08.

| Nhóm | Nội dung phải ghi trong checklist |
|---|---|
| Boot AWS | `SPRING_PROFILES_ACTIVE=aws`; datasource URL/user/password; JWT Base64 giải mã ≥32 bytes; HTTPS frontend origin; region/bucket/prefix; CA/trust cho JVM |
| Mặc định an toàn cho lần đầu | `SEED_SCENARIO=NONE`; jobs tắt tới window; bootstrap chỉ bật có chủ đích với credentials private |
| Jobs | Bật `APP_AI_JOBS_ENABLED`, hai queue URLs khác nhau; delivery bật nếu muốn dispatcher/results consumer chạy. Flag delivery=false không phải lỗi SQS |
| Browser/ALB | CORS/HTTPS/Secure cookies, CSRF cookie domain khi topology cần; kiểm forwarded headers và public origin sau ALB theo PRE-09; không tắt CSRF để smoke cookie auth |
| OAuth/mail | OAuth opt-in qua provider profile; SMTP chưa cấu hình thì các flow mail chưa nghiệm thu. Chưa có mail feature toggle riêng được xác nhận; không ghi để trống credentials = đã tắt mail |
| Deploy host | Runtime/migration env file paths, settings, Compose, truststore, revision và candidate image theo thiết kế chốt |

Profile AWS vẫn yêu cầu cấu hình S3 khi jobs tắt. Template phải tách rõ boot requirement với cấu hình chỉ cần cho feature test; không thêm biến mới nếu implementation chưa đọc.

Đối chiếu IAM theo code và policy thực do BE2 cung cấp: backend put/read input/rubric, read results, file avatar/problem và cleanup; worker read input/rubric, put/read result để retry, backend send jobs/receive-delete results, worker receive-delete jobs/send results. Khi dùng object version/KMS, BE2 review quyền version/key tương ứng; không tự kết luận role hiện tại đủ hoặc thiếu khi chưa có policy. Worker không cần DB/JWT. Không cần mở quyền quản trị bucket/queue chỉ để chạy luồng này.

Ghi role/provider thật trong container, cùng bucket/prefix, queue region/pair và receive override 120s/long poll 20s; BE2 kiểm queue defaults thực. Không sửa queue hoặc IAM trong task BE1 chỉ để làm test đạt.

**Đạt khi:** checklist phân biệt boot/feature, không có secret trong repo/log, quyền yêu cầu và quyền đang có được so theo resource/action cụ thể; mục chưa có bằng chứng vẫn pending.

### PRE-06 — Bộ chuẩn bị dữ liệu giả qua API có thể chạy lại

**Owner đề xuất:** BE1 xây và rehearsal; BE2 chọn target/window và giữ credentials. **Dependency:** D04/D05/D06; lab không cần chờ RDS.

Deliverable đề xuất: `docs/plans/backend1-be2-smoke-data-runbook.md`, kèm request JSON dùng placeholder và danh sách ID cần thu. Chỉ thêm script runner riêng sau khi chuỗi request manual đã chạy đạt và nhóm thấy cần lặp lại; không bắt buộc thêm seeder production hoặc endpoint provisioning.

Các bước phải được làm cụ thể trong runbook:

1. Khởi tạo schema đúng V3; reference roles/template được `DataSeeder` nạp. Bootstrap admin bằng env opt-in, tắt bootstrap sau provisioning; không dùng credentials demo cố định.
2. Login admin qua auth hiện hành; chọn cookie + CSRF cho runbook chính để sát browser. Lấy CSRF qua `GET /api/csrf`, giữ cookie và header tương ứng; không tự tạo JWT từ secret. Nếu bổ sung bearer flow thì kiểm login response thực trước, không giả định có token trong body.
3. Admin tạo event và các tài khoản test; tạo user qua admin API pre-approved là một hướng tránh phụ thuộc SMTP, role grant vẫn là bước riêng. Admin không tự có quyền tạo job chỉ vì có SYSTEM_ADMIN.
4. Gán coordinator đúng event. Chọn STAFF judge đã approved và assignment phù hợp; phân biệt grant role với assignment round/track.
5. Tạo một track, một round và số đội đáp ứng guard thật. **Luồng OPEN→SETUP→IN_PROGRESS hiện cần ít nhất hai đội APPROVED trên mỗi track**, không còn đội PENDING hoặc APPROVED chưa gán track. Không hứa một đội đủ chỉ vì test integration chèn entity trực tiếp.
6. Rehearsal cách tạo roster/membership/approval/track assignment theo API. Đối chiếu guard hiện hành thay vì suy cứng số thành viên tối thiểu từ requirement cũ; ghi dataset tối thiểu thực sự chạy được.
7. Event theo lifecycle hợp lệ; round ACTIVE. Áp rubric từ template hoặc tạo criteria cho đúng round; template tồn tại chưa đồng nghĩa round đã có rubric.
8. Tạo judge assignment trước khi mở judging. Chạy CONTEST để leader nộp repo public nhỏ được phép dùng; stop/expire CONTEST trước khi start JUDGING. Mỗi submission cần có assignment bao phủ để start judging được.
9. JUDGING RUNNING còn thời gian; xác nhận submission status/repo/rubric. Ghi `eventId`, `trackId`, `roundId`, team/entry IDs, user/assignment IDs và `submissionId` từ response, không hardcode ID môi trường khác.
10. Kiểm việc lặp: nhận biết dữ liệu đã tạo, dùng tên/run ID riêng, tránh tự retry POST tạo tài nguyên. Không reset database hoặc xóa event cũ để chạy lại; dọn theo danh sách tài nguyên test sau thỏa thuận riêng.

Dates/timer phải dùng giá trị phù hợp thời điểm chạy, mùa/năm và validation; không copy ngày 01/11/2026 cố định vào request chạy tuần sau. Repo cần có supported source trong giới hạn collector; kiểm kết nối public GitHub từ môi trường backend.

**Đạt khi:** từ DB lab mới, operator làm theo tài liệu tạo được dữ liệu qua API, không insert SQL để vượt guard, không chạy run-demo reset. Ít nhất một submission đủ tạo job và có tài khoản/ID cho ca chặn quyền; không có mật khẩu/token trong bộ chia sẻ.

### PRE-07 — Runbook smoke MOCK và thu evidence

**Owner đề xuất:** BE1 kịch bản/expected results; BE2 vận hành runtime; worker operator theo D07. **Dependency:** PRE-04/05/06 và queue window.

| Bước | Thao tác | Kết quả cần ghi |
|---|---|---|
| Preflight | Xác nhận SHA/image identity/target/role/prefix/queue pair, người chạy worker và consumer; kiểm health/version/ready và ALB target | Version khớp revision bàn giao; ready chỉ chứng minh DB |
| File | Upload/read avatar hoặc đề thi theo tài khoản/quyền trong scope, đọc lại sau restart backend | S3 persistence và quyền nghiệp vụ, không chỉ health |
| Tạo job success | `POST /api/ai/submissions/{submissionId}/jobs` không cần body, đúng auth/CSRF | 202, lấy jobId; không tự retry POST |
| Dispatch | Bật delivery trong window, theo dõi outbox và log đã lọc | Request gửi jobs; QUEUED hoặc terminal khi poll đủ nhanh |
| Worker | Dùng cùng candidate JAR/image, Java `AwsMockWorkerMain --one-success` với worker.env private | Read refs đúng hash, output S3, send completion rồi ack jobs |
| Đọc success | `GET /api/ai/jobs/{jobId}` và `/result` | SUCCEEDED/200, mode MOCK, DTO đã lọc, không đổi điểm |
| Failure riêng | Tạo job mới rồi worker `--one-failure` | FAILED; GET result 409, message MOCK_PROCESSING_FAILED |
| Kiểm quyền | Sai event/requester theo contract, revoke, judging đóng | Từ chối đúng; quyền đọc lịch sử theo policy hiện hành |
| Persistence | Restart backend có phối hợp rồi đọc lại job/result/file | Dữ liệu giữ, version đúng artifact; readiness không thay kiểm này |

Worker một lượt nhận một message bất kỳ đủ điều kiện trong queue, không chọn trực tiếp jobId. Dùng queue window có kiểm soát; nếu có backlog, BE2 và worker operator đối chiếu message/job trước, không Purge queue để làm thuận tiện. Không gửi fixture synthetic `current-examples` vào queue runtime thay cho job được API tạo.

Duplicate, crash/ack failure và DLQ/replay được kiểm local trước; fault injection/replay trên AWS chỉ trong scope/window BE2 cùng thống nhất. Không biến các ca phá phụ thuộc thành điều kiện phải thử trực tiếp trên RDS đang dùng.

**Đạt khi:** success và failure trên hai job riêng được ghi với SHA/image identity/jobId/thời gian, S3 refs/checksum, trạng thái DB và log an toàn; mọi ca chưa chạy có owner/giới hạn. MOCK pass không được ghi thành model thật hoặc FE UI pass.

### PRE-08 — Cập nhật bộ bàn giao và evidence theo revision mới

**Owner đề xuất:** BE1, BE2 xác nhận đã nhận đúng bộ. **Dependency:** PRE-01…07 theo mốc tương ứng.

- Cập nhật current handoff và template index sau khi implementation đúng; giữ link contract MOCK nếu semantics không đổi. Plan AI Engineering tiếp tục là đề xuất riêng.
- Tạo evidence mới theo ngày/checkpoint triển khai; lưu logs đã lọc có lệnh/môi trường và test counts đúng, không commit cả `target`, private env hoặc dump thật.
- Bộ checksum ngày 28/10 đang là checkpoint lịch sử. Khi rename/update docs phải có manifest mới/version mới và verifier tương ứng cho candidate; không sửa checksum evidence cũ để làm như bản mới đã được kiểm từ trước.
- Chú ý verifier hiện đọc manifests từ working tree nhưng hash bytes HEAD hoặc staged: chọn chế độ và inventory cùng một checkpoint, ghi working-tree diff riêng. Có hash nội dung không đồng nghĩa các test đã chạy trên nội dung đó.
- Sau khi source được chốt/commit theo yêu cầu triển khai riêng, ghi full SHA/link branch hoặc PR. Ghi image ID/digest phù hợp với cơ chế build/delivery BE2 chọn; không suy image identity từ local JAR hash. ECR pipeline đã hoãn, không dùng thiếu ECR digest để chặn đợt này.
- Lập bảng trả lời sáu câu hỏi BE2 bằng đường dẫn cụ thể trong revision mới. Đầu vào chưa có ghi pending và owner, không điền thay bằng giả định.

**Đạt khi:** một người mới checkout revision được chốt có đủ docs/fixtures/scripts để chạy lại; source revision, evidence và image mapping nhất quán.

### PRE-09 — Tích hợp ALB với private EC2 và kiểm đường đi ra

**Owner đề xuất:** BE2 ALB/subnets/security groups/network/access; BE1 container/proxy/header/health config và kiểm chức năng. **Dependency:** D09/D10; phối hợp PRE-01/02/05/07.

Topology đã được người dùng chốt:

```text
Client → ALB → EC2 trong private subnet → backend container
                                      → RDS / S3 / SQS
```

FE đã chốt chạy trên S3. Đường FE HTTPS đề xuất `Browser → CloudFront → S3 private` cần BE2/FE xác nhận; browser gọi API qua ALB theo domain/topology được chọn. Sơ đồ chưa quyết định ALB internet-facing hay internal, listener/TLS hoặc target port. Các mũi tên S3/SQS của backend là kết nối SDK do ứng dụng chủ động tạo, không phải ALB chuyển tiếp job/SDK traffic và không phải đường tải FE.

1. **Chốt target port và proxy.** Với FE trên S3, đề xuất ALB direct tới backend port 8080 trên EC2; Caddy không còn cần làm proxy frontend/backend trong stack AWS mới nếu không có yêu cầu proxy riêng khác. BE2 xác nhận target/protocol trước khi BE1 sửa Compose. Root Compose/Caddy dev giữ riêng, không xóa stack dev chỉ vì topology AWS đổi.
2. **Sửa binding đúng phương án.** `deploy/compose.aws.example.yml` hiện `127.0.0.1:8080:8080`, chỉ phù hợp khi proxy local là điểm nhận. ALB direct tới private IP EC2 không kết nối được port chỉ bind loopback. Nếu chọn direct, publish trên interface mà ALB tới được và phối hợp BE2 hạn chế source SG từ ALB; nếu qua Caddy thì mở đúng proxy port và giữ backend nội bộ. Không đổi sang public access để giải quyết lỗi bind.
3. **TLS/headers/cookie.** Nếu TLS kết thúc tại ALB và hop sau HTTP, kiểm ALB `X-Forwarded-Proto/Port/For`, giữ public HTTPS origin và Secure cookies. Source hiện có `server.forward-headers-strategy=framework`; không mặc định cần thêm setting mới nhưng phải test redirect/login/cookie qua đường thật. Nếu qua Caddy, review việc tin/preserve header của proxy trước và tránh redirect loop. HTTPS hoặc HTTP ALB→target cần BE2 chốt, không tự bật TLS trong Spring.
4. **Health và routing.** Đề xuất target health `/health/ready`, response 200 khi DB sẵn sàng; `/health/live` kiểm process, `/health/version` phục vụ truy revision. Khi qua proxy, route health phải tới backend, tránh rơi vào FE fallback. Rà listener rules cho `/api/*`, `/uploads/avatars/*`, cùng `/oauth2/*` và `/login/oauth2/*` nếu bật OAuth; không chỉ chuyển `/api/*` rồi làm mất ảnh/callback. Host-based API domain có thể route toàn host thay vì từng path, cần FE/BE2 chốt.
5. **Network/IAM đi ra.** BE2 kiểm DNS/routing/security groups tới RDS và đường tới S3/SQS/SSM từ container/instance. NAT/proxy hoặc endpoints được chọn theo service và thiết kế; endpoint policy không thay IAM/bucket/queue policy. Collector hiện gọi public GitHub để resolve/read repo, nên chỉ có endpoints S3/SQS chưa đủ tạo job. Nếu build ở EC2, cần đường lấy Git source, Maven dependencies và Docker base images; SMTP/OAuth chỉ tính vào scope khi bật.
6. **Vận hành private instance.** Chốt Session Manager hoặc đường quản trị BE2 có sẵn; không suy ALB HTTP listener cung cấp SSH/deploy access. Lần smoke API qua ALB, migration CLI chạy theo đường operator private; ghi rõ cách lấy log mà không cần public IP EC2.
7. **Frontend S3 qua HTTPS.** S3 website endpoint không hỗ trợ HTTPS; đề xuất CloudFront với regular S3 bucket origin private và OAC, chưa ghi thành cấu hình nhóm đã chọn. Chốt FE/API domain và `VITE_API_URL`, CORS/credentials/CSRF để browser dùng cơ chế cookie hiện tại. Không mặc định raw `*.cloudfront.net` và API domain khác gốc tương thích với policy cookie/CSRF hiện có; chọn domain cùng gốc hoặc routing phù hợp sau khi review. CloudFront không tự nằm trên đường API nếu nhóm chưa cấu hình như vậy.

**Kiểm chứng:** health từ ALB target tới đúng service, API login/CSRF/cookie/redirect qua public/internal origin đúng thiết kế, avatar route và job snapshot GitHub từ container thật. Test config local chỉ khi có thay đổi cụ thể; AWS ALB health/network do hai bên kiểm trong window. Backend health ready không kiểm S3/SQS/GitHub, cần probes/functional smoke riêng.

**Đạt khi:** client dùng đường ALB đã chốt, target healthy, backend nhận đúng scheme/host, private EC2 có các kết nối cần cho scope, operator có đường chạy CLI/log; không đổi quyền hoặc business guards để làm health pass.

Nguồn kỹ thuật AWS đã đối chiếu ngày 03/11: [ALB target protocol/port và private IP](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-target-groups.html), [ALB forwarded headers](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/x-forwarded-headers.html), [SG target nhận từ SG ALB](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-update-security-groups.html), [NAT cho private subnet](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-nat-gateway.html), [Systems Manager endpoints](https://docs.aws.amazon.com/systems-manager/latest/userguide/setup-create-vpc.html), [SQS endpoints](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-internetwork-traffic-privacy.html), [endpoint policy và IAM](https://docs.aws.amazon.com/vpc/latest/privatelink/vpc-endpoints-access.html). Đây là căn cứ thiết kế, không phải bằng chứng AWS của nhóm đã cấu hình.

Nguồn FE S3: [S3 website endpoints và giới hạn HTTPS](https://docs.aws.amazon.com/AmazonS3/latest/userguide/WebsiteEndpoints.html), [CloudFront OAC cho regular S3 bucket origin](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-restricting-access-to-s3.html). OAC không áp cho S3 website endpoint.

## 5. Thứ tự và các mốc hoàn tất

| Mốc | Công việc | Điều kiện qua mốc |
|---|---|---|
| M0 — thống nhất hướng tích hợp | PRE-00; draft PRE-04/05/06/09; D01–D10 | Đã có hướng SEAL/private EC2; chốt port/proxy/TLS/network cho phần sắp sửa và owner |
| M1 — candidate local | PRE-01/02/03/09 phần config + rehearsal PRE-04/06 + PRE-08 checkpoint local | Module/package SEAL/build/gate/contract/MySQL lab đạt, runbook API đạt; không phụ thuộc pipeline ECR |
| M2 — backend lên target BE2 | BE2 config/identity/image/ALB/network; PRE-04/05/09 + PRE-07 preflight | Init/gate từ candidate, runtime ít quyền, JVM DB TLS/health/version và ALB target đạt; chưa gọi đây là AI end-to-end |
| M3 — smoke MOCK AWS | PRE-07 còn lại, PRE-08 evidence AWS | S3 file/job success/failure/persistence và quyền đạt theo scope; bàn giao giới hạn còn lại |

PRE-03 chạy nghiệm thu candidate sau các sửa thực thi, không chạy full suite lặp lại sau mỗi sửa tài liệu. Dừng và điều tra khi có failure; không tăng timeout nghiệp vụ/bỏ assertion chỉ để pass. Test mới tập trung semantic change ở gate hoặc lỗi được tái hiện; rename/tài liệu dùng checks hiện có khi đủ.

Ước lượng ban đầu 2–4 ngày công BE1 cần đánh giá lại sau inventory full module/package rename và D09/D10. Không tính công pipeline ECR đã hoãn vào mốc hiện tại. BE2 ước lượng riêng ALB/private network/window; chưa cam kết ngày khi thiếu source/port/proxy/network hoặc lỗi dispatch chưa tái hiện.

## 6. Bộ kiểm chứng bắt buộc theo phạm vi

Các lệnh dưới đây là lệnh đang có trước rename, không phải yêu cầu chạy lên target AWS trong lượt lập plan. Recheck paths/runner options khi implementation mới đã chốt.

```text
Backend module:
  Maven test/package với Java 21 và profile test
  Test/run-integration-smoke.ps1
  Test/run-ai-mysql-tests.ps1 hoặc Test/run-ai-mysql-tests.sh

Repository root sau PRE-01:
  node --test back-end/src/seal-api/Test/deploy-verified.test.mjs
  node --test back-end/src/seal-api/Test/schema/inspect-dump.test.mjs
  node docs/contracts/ai-job-v1/validate-current-examples.mjs
  verifier bundle của checkpoint tương ứng

Frontend khi scope thay đổi cần kiểm:
  npm ci, npm test, npm run build tại front-end/src/seal-web
```

Linux smoke có thể dùng Maven Wrapper với `AwsMockWorkerTest,AiJobIntegrationTest` và profile test; Windows runner dùng Maven offline nên máy mới cần dependency cache. MySQL runner tạo DB/container riêng, package candidate trước test; không dùng nó làm migration runner RDS. Compose phải đủ hỗ trợ `env_file.format: raw` nếu giữ template này; parse template không thay kiểm JVM TLS và credentials trong container thật.

## 7. Các câu hỏi dùng trong buổi thảo luận

1. Với hướng SEAL đã chốt, BE2 có thể bàn giao current infra/deploy ở revision nào, file nào mỗi bên sửa? Giữ nguyên tài nguyên/contract `fcaj_*` hiện có trừ khi có quyết định đổi riêng.
2. ECR pipeline đã hoãn: buổi đầu BE2 build/giao candidate và thao tác private EC2 bằng cách nào? Nếu có quy trình hiện hữu thì review trước khi sửa gate.
3. FE đã chốt S3: BE2 có đồng ý ALB direct backend, target port/protocol nào và TLS kết thúc ở đâu? FE sẽ qua CloudFront/HTTPS/domain nào, API domain và cookie/CSRF topology ra sao?
4. Có đồng ý dùng dữ liệu giả theo API trước và giữ restore `.bak` thành task riêng? Target RDS/test data/cleanup do BE2 kiểm soát thế nào?
5. Ai chạy worker, ở EC2 hay local, bằng role/provider nào, queue có consumer/backlog khác không? Khi nào có thể đặt window?
6. BE2 có thể gửi policy/prefix/queue config đã lọc và log hai test cũ khi nào? Những đầu vào này không cần kèm secret.
7. EC2 private có đường tới public GitHub và nơi tải source/base images/dependencies không? S3/SQS/SSM dùng NAT/proxy hay endpoints nào theo thiết kế BE2?

## 8. Phiếu theo dõi quyết định và bàn giao

| Mục | Người phụ trách cụ thể | Quyết định/link bằng chứng | Thời hạn do owner xác nhận | Trạng thái |
|---|---|---|---|---|
| D01/source rename | BE1 thực thi, BE2 review tích hợp; tên người chưa điền | Người dùng xác nhận giữ SEAL và đổi cả module/package về SEAL ngày 03/11 | Chưa chốt ngày | Quyết định đã có, chưa implement |
| D02/D03/image và deploy | Chưa điền | ECR pipeline hoãn theo trao đổi người dùng/BE2; cách giao candidate/operator private còn chờ | Chưa chốt | Một phần đã quyết định |
| D04/D05/dữ liệu | Chưa điền | Người dùng yêu cầu thảo luận, RDS thuộc BE2 | Chưa chốt | Pending |
| D06/FE và browser/API scope | Chưa điền | Người dùng xác nhận FE S3 ngày 03/11; CloudFront/domain/HTTPS và acceptance scope chưa chốt | Chưa chốt | Một phần đã quyết định |
| D07/worker window | Chưa điền | Chưa có lịch/worker operator | Chưa chốt | Pending |
| D08/IAM/prefix/queue | Chưa điền | Chưa có policy thực để đối chiếu | Chưa chốt | Pending |
| D09/ALB-private EC2 | BE2 hạ tầng, BE1 app/container; tên người chưa điền | Người dùng xác nhận ALB → EC2 private ngày 03/11; port/proxy/TLS còn chờ | Chưa chốt | Topology đã có quyết định |
| D10/đường đi ra | BE2; tên người chưa điền | Chưa có network outputs/probes từ container | Chưa chốt | Pending |
| Log/reproduce BE2 | Chưa điền | BE1 pass ngày 01/11, nguyên nhân lượt BE2 chưa xác định | Chưa chốt | Pending |
| Candidate/evidence mới | Chưa điền | Chưa implement theo plan | Chưa chốt | Pending |

Lượt 03/11 tạo plan và cập nhật link tài liệu; không thay application/config runtime, không chạy migration/deploy/AWS, không commit/push hoặc gửi tài liệu cho BE2. Bước tiếp theo là dùng PRE-00 và bảng quyết định để thảo luận; các task implementation là việc tiếp theo khi người dùng giao thực hiện và dependency tương ứng đã rõ.
