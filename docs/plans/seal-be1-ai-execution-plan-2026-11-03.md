# Plan giao AI thực hiện phần BE1 trước báo cáo và bàn giao BE2

**Cập nhật sau thực thi03/11:** AI-00→AI-07 đã hoàn thiện ở phạm vi local; xem [báo cáo kết quả](../report/seal-be1-local-completion-2026-11-03.md), [evidence mới](../checks/seal-be1-local-2026-11-03/README.md) và [Q01–Q10 cho BE2](seal-be1-be2-clarifications-2026-11-03.md). Candidate chưa commit/push/deploy. Các câu “chưa thực hiện” bên dưới ghi trạng thái lúc khởi lập plan, được giữ để truy lịch sử; kết quả thực tế và phần live còn pending nằm trong báo cáo.

Ngày: **03/11/2026**, Asia/Saigon. Trạng thái: **plan thực thi cho lượt triển khai tiếp theo; chưa thực hiện các task code bên dưới**.

Mục tiêu: AI hoàn thiện phần BE1 có thể làm độc lập trong checkout, kiểm bằng chứng local, sau đó mới viết báo cáo kết quả và bộ câu hỏi bàn giao BE2. Không để thiếu domain/policy/network của BE2 trở thành lý do dừng toàn bộ công việc local. Phần nghiệm thu AWS phụ thuộc đầu vào thực được giữ pending rõ ràng.

## 1. Nguồn và quyết định phải giữ

- [Plan thảo luận tổng thể](backend1-be2-predeploy-execution-discussion-plan-2026-11-03.md).
- [Bản phản hồi sáu mục BE2](seal-be1-tra-loi-be2-truoc-deploy-2026-11-03.md).
- [Contract MOCK hiện hành](../contracts/ai-job-v1/CURRENT.md), [bàn giao ngày 28/10](backend1-be2-current-handoff-2026-10-28.md).
- Checkout khi lập plan: `feature/backend1-be2-handoff`, HEAD `bab44e5fdeb710e6c71982b2e8f5547829fa8e40`. Kiểm lại Git status/HEAD/AGENTS lúc bắt đầu thực thi, không coi checkpoint này là release mới.
- Có sẵn `docs/README.md` sửa và ba file plan/phản hồi mới untracked. Giữ nguyên nội dung có trước; không reset/stash/checkout đè để làm tree sạch.

| Nội dung | Quyết định / giới hạn hiện tại |
|---|---|
| Tên sản phẩm và source | Giữ SEAL Hackathon, đổi module về `back-end/src/seal-api`, Java package về `com.seal.hackathon` |
| FE/service/settings | Giữ `front-end/src/seal-web`, `seal-backend`, `/etc/seal/deploy-settings.sh`; vị trí private files host do BE2 xác nhận |
| Topology | FE trên S3; API qua ALB đến EC2 private |
| Caddy | ALB direct backend là đề xuất phù hợp với FE S3, chưa có xác nhận cuối của BE2; không coi là đã deploy/bỏ Caddy |
| HTTPS/domain FE | CloudFront được đề xuất, chưa xác nhận domain/HTTPS thực |
| ECR | Pipeline Actions → ECR đã hoãn; không triển khai trong scope này |
| Dữ liệu RDS | RDS thuộc BE2. Dữ liệu giả trước/restore `.bak` riêng vẫn chờ thống nhất; rehearsal local không cần chờ quyết định ghi RDS |
| AI | MOCK, giữ bốn state và ba API hiện hành; model thật và FE job UI ngoài mốc local này |

Giữ nguyên bytes migration V1/V2/V3, schema/table names, catalog/tài nguyên AWS hiện có, namespace message `fcaj.ai.*`, object key và policy quyền nghiệp vụ. Đổi tên Java không phải đổi protocol/storage. Không viết migration mới nếu task chỉ thay package/path/config.

## 2. Phạm vi thực thi và cách xử lý đầu vào thiếu

**Làm local trước:** rename source có kiểm soát, cập nhật launchers/build/scripts/docs đang dùng, chuẩn bị template ALB, làm rõ runtime config, rehearsal dữ liệu API, kiểm hồi quy và đóng gói evidence.

**Chỉ chờ BE2 ở phần liên quan:** apply RDS migration thật, xác nhận ALB/TLS/domain/private network/IAM, chạy S3/SQS thật hoặc worker thật, provisioning tài khoản/dữ liệu trên RDS và deploy EC2. Thiếu các đầu vào này không chặn rename, package, MySQL lab hoặc chuẩn bị runbook.

Lượt triển khai local không tự commit/push/merge/deploy, không gửi tin cho team, không gọi AWS, không thay RDS/queue/bucket/IAM. Đây là ranh giới của mốc local; yêu cầu trực tiếp tiếp theo của người dùng có thể cấp scope mới. Không thêm quy trình xin phép lại cho các sửa local đã được giao. Nếu cần công cụ ngoài sandbox để chạy test hợp lệ, dùng cơ chế approval của công cụ và ghi rõ lỗi môi trường, không sửa assertion để né.

Không bật production endpoint để fake GitHub/S3, không nới CSRF/grant/assignment để tạo được job, không dùng demo reset trên DB đang dùng. Dùng lab loopback/dữ liệu giả cho các test ghi hoặc đổi schema.

## 3. Thứ tự bắt buộc

```text
AI-00 inventory/baseline
→ AI-01 rename SEAL
→ AI-02 runners/gate/templates local
→ AI-03 cấu hình và template ALB có giả định rõ
→ AI-04 runbook + rehearsal dữ liệu qua API
→ AI-05 hồi quy/package/MySQL/evidence
→ AI-06 báo cáo BE1 đã làm
→ AI-07 bộ BE2 cần làm rõ và bảng giao nhận
```

Có thể soạn draft yêu cầu cấu hình/runbook sớm, nhưng chỉ ghi hoàn tất sau khi kiểm thực tế. Không phát hành báo cáo “đã sẵn sàng deploy” trước AI-05. Không chạy hai Maven build/test cùng ghi chung `target`.

## 4. Task chi tiết cho AI

### AI-00 — Kiểm checkout, inventory và baseline

**Đầu vào:** checkout hiện tại; không cần BE2.

1. Đọc AGENTS áp dụng, Git status/branch/HEAD và các nguồn mục 1. Phân biệt thay đổi có trước với thay đổi của lượt triển khai.
2. Inventory `fcaj-api`, `com.fcaj`, `com/fcaj`, fully qualified launcher và references trong source/tests/POM/resources/scripts/workflow/Compose/demo/current docs. Tránh đọc/in `.env`, credentials, dump hoặc raw user artifacts để làm inventory.
3. Ghi hash migrations/contract fixtures, số test baseline và tool versions Java 21/Node/Docker/Maven. Chạy baseline phù hợp trước rename khi môi trường cho phép.
4. Nếu baseline fail, lưu lỗi và phân loại source hay môi trường. Hai test BE2 nêu pass ngày 01/11; không khẳng định root cause BE2 đã được sửa.

**Đạt khi:** có inventory rõ scope, baseline/evidence hoặc lý do kiểm nào chưa chạy; không làm mất file có trước.

### AI-01 — Đổi module/package về SEAL

**File/phạm vi:** module backend hiện `back-end/src/fcaj-api`, Java main/test, `pom.xml`, resource/config chứa class name, scripts và các file gọi backend.

- Xác minh absolute source/destination nằm trong workspace, destination không chứa source riêng trước khi move. Dùng native PowerShell cùng một shell cho file operations trên Windows; không dựng lệnh `cmd` từ danh sách path để move/delete. Không chuyển bằng một lệnh revert toàn bộ commit BE1.
- Đổi module thành `back-end/src/seal-api`; đổi cây Java main/test và `package`/`import`/FQCN sang `com.seal.hackathon`.
- Cập nhật Maven group `com.seal`, Spring Boot mainClass, reflection/class name trong tests/config, migration CLI, worker CLI, schema tools và mock-demo launcher. Giữ `artifactId=hackathon`/JAR filename nếu không có lý do riêng phải đổi.
- Giữ logic migrations/storage/job/auth và file bytes contract như baseline. Không đưa thư mục `target` cũ thành bằng chứng build package mới; kiểm build từ output sạch ở đúng module sau rename. Chỉ dọn generated output khi đã xác minh path, không xóa uploads/private files hoặc output cần giữ làm evidence baseline.

**Đạt khi:** backend compile/package từ source ở module mới, `com.seal` mains có trong archive, migrations/contract hash không đổi; không còn tham chiếu executable `com.fcaj` ngoài danh sách ngoại lệ được giải thích.

### AI-02 — Cập nhật runners, deploy gate và tài liệu đang dùng

**File cần rà:** `.github/workflows/deploy.yml`, root `docker-compose.yml`, `E2E testing/scenario-*/*.bat`, module `Test/*.ps1`, `Test/*.sh`, `Test/schema/*`, `deploy/*`, README/current handoff/contracts links và tests gọi packaged JAR.

- Thay module/FQCN trong build/test/launcher, kể cả `DatabaseMigrationMain`, `AwsMockWorkerMain`, packaged CLI tests và Git-clean scope của gate. Giữ service `seal-backend` và FE `seal-web`.
- Giữ migration URL phải khớp runtime URL trước ghi, init/gate chạy từ đúng candidate image, không thay backend trước gate, `verify-only` mặc định và init rỗng có chọn rõ.
- Rà `compose up` để thay đúng backend theo scope; việc sửa này nếu thực hiện phải giữ cấu hình dependencies/proxy phù hợp và có test command ordering. Không thay DB/FE/proxy ngoài scope ngầm.
- Chuẩn hóa private-file paths template về SEAL có placeholder/ghi chú operator rõ ràng; không move file thật trên EC2. ECR pipeline không phải dependency và không thêm fallback deploy không được nhóm chọn.
- Workflow cũ SSH từ GitHub-hosted runner chưa có đường tới private EC2 được xác nhận. Cập nhật path/tests cần thiết, ghi deploy step chưa được nghiệm thu topology mới; không tự tạo SSM pipeline hoặc chạy push `main` để thử.
- Sửa links tài liệu đang được dùng, đặc biệt contract README có links module/package không khớp. Báo cáo/checksum lịch sử giữ làm checkpoint; không thay toàn bộ chữ FCAJ trong repository bằng SEAL.

**Đạt khi:** runner gọi đúng source/classes, deploy command-order tests đạt, current docs links hợp lệ, checklist chỉ rõ đường deploy còn chờ BE2. Không coi old HEAD bundle verifier pass là bằng chứng cho working tree mới.

### AI-03 — Chuẩn bị cấu hình AWS/ALB mà không tự chọn hạ tầng thay BE2

**Đầu ra đề xuất:** `.env.aws.example` được rà lại, template Compose backend-only cho **ALB direct** và runbook config. Có thể giữ template proxy-local riêng nếu còn hữu ích; tên file phải giúp operator phân biệt hai topology.

1. Template ALB direct ghi rõ **giả định chờ BE2 xác nhận**: target là backend 8080, target protocol/TLS theo BE2. Publish backend trên interface ALB truy cập được thay vì chỉ `127.0.0.1`; không tự mở security group. Template yêu cầu SG target chỉ nhận từ SG ALB, target health `/health/ready`.
2. Không sửa template loopback rồi vẫn ghi dùng sau proxy local. Không xóa root Caddy/dev stack vì AWS FE đã chuyển S3. Không coi direct backend là quyết định cuối chỉ vì AI đã tạo template.
3. Source đã có `server.forward-headers-strategy=framework`; kiểm hành vi request scheme/redirect/cookie liên quan bằng test thích hợp trước khi thêm setting mới. Giữ Secure cookies/CSRF. Browser qua ALB thật còn chờ domain/TLS/network.
4. Tách nhóm biến boot, jobs, bootstrap, OAuth, mail và worker. S3 vẫn bắt buộc trong AWS profile khi jobs tắt; `APP_AI_JOBS_DELIVERY_ENABLED=false` chủ động tắt delivery phải được tài liệu giải thích. Không thêm mail toggle mới chỉ vì muốn bỏ cấu hình SMTP trong smoke.
5. `.env.aws.example`/settings/migration/worker env phải không chứa secret, URL host/domain/prefix giả không được ghi như giá trị thực đã cấp. Worker không cần DB/JWT.
6. Tạo bảng nhu cầu network/IAM từ code: GitHub public, RDS, S3/SQS/SSM và nguồn build nếu build ở private EC2. Endpoint policy/role thực chờ BE2; không tự cấp thêm quyền. Giữ visibility override 120s/long poll 20s trừ quyết định mới.

**Kiểm chứng:** Compose parse với synthetic env không in secret; health route hiện hữu và test config/header khi thay đổi semantics. Parse template chỉ chứng minh cấu trúc, không phải ALB target healthy hoặc JVM RDS TLS đạt.

**Đạt khi:** bộ config có thể review/fill, assumptions và input còn thiếu rõ; ALB direct/proxy-local không bị lẫn; không triển khai AWS.

### AI-04 — Runbook dữ liệu và rehearsal qua API

**Đầu ra đề xuất:** `docs/plans/seal-be1-smoke-data-runbook.md`, request JSON dùng placeholder, record IDs và evidence rehearsal. Nếu cần runner tự động thì bổ sung sau khi chuỗi request đã hoạt động, không bắt buộc tạo seeder production.

Runbook phải thể hiện:

1. V3 → reference roles/template → bootstrap admin có chủ đích. Chứng minh bootstrap không tự xuất hiện khi flag tắt và không đổi mật khẩu account đã có.
2. Cookie login + `GET /api/csrf`, gửi cookie/header đúng khi POST. Login hiện bỏ token khỏi JSON; không tự tạo JWT từ secret hoặc giả định lấy bearer token từ response.
3. Admin tạo event/accounts, cấp coordinator theo event và JUDGE + assignment theo round/track. Phân biệt admin, event grant, assignment và account approval.
4. Tạo track/round/teams/entries/roster theo API; xử lý PENDING trước SETUP, gán track và có ít nhất **hai đội APPROVED mỗi track** trước IN_PROGRESS. Không suy một đội đủ từ fixture test chèn DB trực tiếp; số thành viên dùng guard thực tế khi rehearsal.
5. Round ACTIVE, rubric đúng round; judge assignments trước khi JUDGING bắt đầu. Start CONTEST, leader nộp repo; stop/expire CONTEST rồi start JUDGING RUNNING còn thời gian.
6. Tạo hai job riêng cho success/failure. Lấy IDs từ response; dates/timer dùng thời điểm rehearsal phù hợp season/year, không hardcode ngày cũ.
7. Ghi cách chạy lại/dừng và nhận biết tài nguyên của test run; không reset DB hoặc retry POST tạo resource một cách mù quáng.

Rehearsal phải đi qua controllers/services/security thực của chuỗi chuẩn bị dữ liệu, không chỉ gọi helper chèn entity cho sẵn. Có thể dùng test-only mocks cho GitHub/S3/SQS và clock khi cần, tuyệt đối không mở backdoor trong production. Bootstrap/reference setup được khởi tạo theo policy ứng dụng. Chọn HTTP lab riêng hoặc test context phù hợp; mô tả chính xác mức chứng minh, không gọi MockMvc là browser/RDS smoke.

Nếu rehearsal phát hiện lỗi ứng dụng thật chặn flow được giao, tái hiện rồi sửa tối thiểu kèm regression có ý nghĩa; không nới guard hoặc bypass validation để runbook chạy. Nếu flow chỉ thiếu bước thì sửa runbook. Không yêu cầu log BE2 trước khi làm phần này.

**Đạt khi:** từ lab mới đi hết setup qua API, tạo job/read result MOCK đúng quyền, có ca sai quyền/judging đóng theo contract; không chứa credential thật. Dữ liệu RDS/người chạy RDS vẫn pending BE2, không chặn nghiệm thu rehearsal local.

### AI-05 — Hồi quy và nghiệm thu local

Chạy theo thứ tự để tránh build artifacts ghi đè:

| Kiểm tra | Phạm vi | Điều kiện đạt |
|---|---|---|
| Backend clean package/full tests | Java 21, profile test, module SEAL | Không failure/error; ghi rõ executed/skipped, không cộng smoke trùng vào tổng |
| Production-component smoke | `AwsMockWorkerTest`, `AiJobIntegrationTest` | Hai ca dispatch BE2 nêu và round-trip/duplicate/commit-before-ack đạt |
| MySQL lab | Runners ở `seal-api/Test`, MySQL 8.4 riêng loopback | Migrations, FreshDatabaseDeploymentTest, job integration/concurrency chạy thật; không skip các ca thuộc scope |
| Packaged launchers | Initializer/verify trong lab, worker entrypoint trong test harness | FQCN mới có trong JAR, launcher được kiểm; không gọi worker SQS thật |
| Deploy gate | `node --test back-end/src/seal-api/Test/deploy-verified.test.mjs` | Init/gate fail không replace, thứ tự và candidate đúng |
| Contract/parser | Current examples validator và dump parser tests | Fixtures/protocol bytes không đổi ngoài thay đổi có review |
| Templates/docs | Compose synthetic config, PS/Bash syntax nếu script đổi, local link scan, `git diff --check` | Không lỗi config/path/link/whitespace; không print secrets |
| FE | Chạy test/build khi file FE/runtime/build behavior thực sự đổi | Không chạy lại toàn FE chỉ vì đổi backend docs; ghi scope rename CI/E2E đã kiểm |

Docker không sẵn sàng thì xử lý môi trường hoặc ghi chính xác check còn thiếu; không ghi MySQL pass bằng evidence ngày 28/10. Mockito/loopback/Git Bash sandbox failure phải được phân biệt với failure nghiệp vụ, giữ log lần thất bại và lần chạy thành công. Chưa tái hiện được lỗi BE2 thì giữ kết luận đó, không tự ghi đã sửa nguyên nhân.

Mọi test mới tập trung vào failure tái hiện, gate semantics hoặc runbook flow; không thêm test chỉ assert chuỗi rename. Khi checks đã pass, chỉ chạy lại phần cần thiết nếu source/checkpoint thay đổi.

**Đạt khi:** tất cả checks local bắt buộc có evidence mới; nếu check chưa chạy thì báo phần BE1 chưa hoàn tất đúng phạm vi, không chuyển lỗi môi trường local thành câu hỏi bắt BE2 làm thay.

### AI-06 — Viết báo cáo hoàn thành BE1 sau khi kiểm chứng

**Đầu ra đề xuất:** `docs/report/seal-be1-local-completion-<ngay-thuc-thi>.md` và `docs/checks/seal-be1-local-<ngay-thuc-thi>/`.

Báo cáo phải có:

- Vấn đề → thay đổi → kết quả kiểm chứng cho AI-01…05; danh sách file chính và việc chưa làm.
- Source baseline SHA, branch, working-tree diff/inventory và thời gian test. Nếu chưa commit thì nói rõ **chưa có release SHA**, không dùng SHA baseline để đại diện source đã rename.
- Test counts/logs đã lọc, runtime/tools/engine versions, hash migration/contract, JAR/image identity nếu đã build. Docker build từ working tree không phải image BE2 đã nhận.
- So sánh input trước/sau rename, xác nhận không mất code/migration/contract và không có private/generated artifacts trong bộ nguồn dự kiến bàn giao.
- Mức kết luận tách riêng: local package/tests, MySQL lab, API rehearsal, template parse; ALB/RDS/S3/SQS live/browser/model thật pending theo evidence thực.

Bundle manifests/evidence mới phải thuộc checkpoint mới. Không sửa manifest lịch sử ngày 28/10 để làm như code mới đã kiểm ở checkpoint cũ. Verifier HEAD hiện không đọc working-tree source: khi chưa commit, dùng inventory/hash working-tree rõ cách tính và không gọi đó là committed release; release verification thực hiện sau khi có revision được người dùng chốt. Không stage/commit chỉ để vượt Git-clean gate trong scope local này.

**Đạt khi:** người dùng review được phần BE1 đã thực hiện và giới hạn, không có kết quả giả định hoặc ngày/SHA/image digest tự điền.

### AI-07 — Đóng gói phần BE2 cần làm rõ sau báo cáo local

**Đầu ra đề xuất:** `docs/plans/seal-be1-be2-clarifications-<ngay-thuc-thi>.md`; cập nhật bản phản hồi sáu mục BE2 và current handoff/index theo implementation thực.

Mỗi câu hỏi có: thông tin cần nhận → vì sao cần → task/live check bị phụ thuộc → ai trả lời → trạng thái. Không gửi nguyên plan nội bộ dài thay cho bảng bàn giao có thể trả lời.

| Đầu vào BE2 cần làm rõ | BE1 bàn giao được trước | Phần còn phụ thuộc |
|---|---|---|
| Current infra/deploy revision và file đang sửa | Source SEAL local/inventory/gate đã kiểm | Tích hợp cấu hình deploy thực |
| ALB direct hay proxy; port/protocol/TLS/health/scheme | Template direct và assumptions, health/header checks local | Apply target/SG/ALB smoke |
| FE S3 qua CloudFront/HTTPS/domain nào; API domain | Biến FE/API/CORS/CSRF và runbook | Browser login/cookie/redirect |
| Network private EC2: GitHub/RDS/S3/SQS/SSM và nguồn build | Danh sách đích network theo code, local tests | Collector/SDK/build từ container target |
| RDS trạng thái/schema/charset/case/CA và identities | CLI/runbook/lab V1–V3 + runtime no-DDL | Init/gate/TLS RDS theo operator BE2 |
| Prefix/policy/provider/queue config | Ma trận nhu cầu action/resource, worker env mẫu | Live S3/SQS permissions và delivery |
| Cách build/giao candidate và vận hành private EC2, ECR hoãn | Gate cùng candidate, artifact local/evidence | Source/image mapping trên target |
| Dữ liệu ban đầu, provisioning operator, restore có tách riêng không | Runbook API và rehearsal local | Tạo account/IDs thật trên RDS |
| Worker owner/nơi chạy/window/consumer-backlog | Worker SEAL launcher và success/failure runbook | SQS receive/delete thật và AWS evidence |
| Log hai test dispatch cũ | Kết quả candidate mới, trạng thái reproduce | Điều tra khác biệt máy BE2 nếu chưa có root cause |

Không yêu cầu secret trong câu trả lời; operator xử lý private values riêng. Không hỏi BE2 làm lại việc local mà AI chưa hoàn thành. Những mục có thể chuẩn bị local được tiếp tục dù BE2 chưa trả lời.

**Đạt khi:** bản phản hồi dùng tên/path/classes thực sau sửa, phân biệt source đã có với target chưa xác nhận; bảng BE2 ngắn gọn, có dependency rõ. Chưa gửi team, chưa gán tên/ngày cam kết thay người khác.

## 5. Trạng thái hoàn thành và trình tự bàn giao

| Mức | Tiêu chí | Cách ghi báo cáo |
|---|---|---|
| BE1 code/local | AI-00…05 có bằng chứng đạt | Hoàn thành phần local; nêu check nào thiếu nếu có |
| Bộ bàn giao review được | AI-06/07 đủ, docs/current config nhất quán | Sẵn sàng người dùng review; release SHA chưa có nếu chưa commit |
| AWS integration | BE2 xác nhận đầu vào, có window và operator, live checks thực thi | Chỉ đạt khi có evidence target thực; không suy từ local |

Không chuyển task code/local sang Done chỉ vì đã tạo file. Không đánh dấu “blocked bởi BE2” cho toàn bộ milestone khi chỉ thiếu domain/target/policy cho live checks. Nếu sau sửa phát hiện thay đổi nghiệp vụ/schema ngoài scope, ghi finding và tiếp tục phần độc lập; không triển khai mở rộng âm thầm.

## 6. Prompt giao cho AI thực thi

> Thực hiện các task AI-00→AI-07 trong plan này trên checkout hiện tại. Bắt đầu bằng AGENTS/Git status/HEAD và giữ nguyên thay đổi có trước. Hoàn thiện phần BE1 local trước: đổi module/package về SEAL, cập nhật runners/gate/current docs, chuẩn bị template ALB direct có assumptions rõ, làm runbook dữ liệu qua API và rehearsal lab, chạy hồi quy/package/MySQL phù hợp. Giữ migrations/protocol/business guards; không làm pipeline ECR đã hoãn. Chỉ sửa lỗi dispatch khi tái hiện được nguyên nhân. Không chờ đầu vào BE2 cho công việc local độc lập; các live checks ALB/domain/network/IAM/RDS/S3/SQS ghi pending theo đúng dependency. Sau kiểm chứng mới viết báo cáo kết quả BE1 và file BE2 cần làm rõ, cập nhật handoff/index. Không commit/push/deploy/gọi AWS hoặc gửi tin team trong scope local này. Báo cáo rõ thực hiện/chưa thực hiện, evidence, source revision/working-tree checkpoint và phần còn phụ thuộc; không dùng kết quả cũ để thay cho candidate mới.

Lượt tạo plan ngày 03/11 chỉ bổ sung tài liệu và links; chưa đổi module/package hoặc chạy các task code. Plan này là thứ tự thực thi mới ưu tiên hoàn thiện BE1 local trước báo cáo, bổ sung cho plan thảo luận tổng thể; các câu hỏi BE2 không còn được xem là điều kiện phải có trước mọi sửa local.
