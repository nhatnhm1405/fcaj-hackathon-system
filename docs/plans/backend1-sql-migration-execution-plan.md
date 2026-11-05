# Kế hoạch cho AI thực hiện — SQL migrations và Backend 1

Ngày: 27/10/2026, Asia/Saigon. Đã triển khai V1/V2, runtime Flyway/validate và restore rehearsal giữ dữ liệu cũ. Xem [bàn giao và evidence hiện hành](../report/backend1-local-completion-2026-10-27.md). Nội dung còn lại giữ kế hoạch ban đầu làm tham chiếu; chưa nghiệm thu cutover DB/AWS thật.

## 1. Mục tiêu và thay đổi so với kế hoạch cũ

Theo yêu cầu mới của người dùng, chuyển quyền tạo/thay đổi schema từ Hibernate `update` sang **SQL migrations có phiên bản bằng Flyway**. Java entity/JPA/repository vẫn là mapping và lớp truy cập dữ liệu; không chuyển sang SQL Server, không bỏ Hibernate, không viết lại mọi repository thành native SQL.

Luồng đích cho môi trường dùng migration:

```text
Database/catalog do môi trường test hoặc BE2 tạo trước
→ Flyway kiểm history và chạy V1/V2/... SQL migrations
→ Hibernate ddl-auto=validate kiểm mapping
→ Java reference/bootstrap/demo seeders theo policy đã chốt
→ Backend phục vụ API
```

Kế hoạch này thay thế phần “chưa thêm Flyway/tiếp tục Hibernate update” trong roadmap BE1 trước. Đây là hướng làm người dùng vừa giao, không tự xác nhận BE2/Lead đã đồng ý mọi lựa chọn cấu hình hoặc nguồn dữ liệu.

**Đích ưu tiên:** DB PoC mới `fcaj_poc`, MySQL8.4 sandbox trước, sau đó RDS private. Dùng dữ liệu mẫu được đồng ý; kế thừa dữ liệu lịch sử là nhánh riêng.

## 2. Hiện trạng phải bảo toàn khi AI bắt đầu

- Git root hiện là `E:/FA26/AWS/fcaj-hackathon-system`, branch `codex/backend1-foundation`, HEAD `f42a168d9cb0613a43801edca632a2de64837099` lúc đọc.
- Có nhiều thay đổi local chưa commit về profiles, AWS validation, bootstrap, JWT, demo script và tests; có fixtures AI trong `docs/contracts/ai-job-v1`. Đây không phải working tree sạch. AI phải đọc lại diff/status và giữ các thay đổi đó.
- Backend thực tế hiện nằm ở `back-end/src/seal-api`, package `com.seal.hackathon`. Handoff BE2 dùng `fcaj-api/com.fcaj` và commit93c3cba khác; object commit đó chưa có local khi kiểm. Không tự checkout/rename để khớp tài liệu.
- POM hiện dùng Java21/SpringBoot4.0.6, chưa có Flyway. BOM4.0.6 cached quản lý Flyway11.14.1 và có starter/module Flyway/MySQL. Khi triển khai phải resolve dependency thật và xác nhận auto-configuration, không coi đọc BOM là build đã đạt.
- `application-local.properties` hiện mặc định Hibernate update. AWS profile yêu cầu DDL_AUTO rõ ràng; AwsEnvironmentConfiguration đang chấp nhận update/validate/none. Test H2 dùng create-drop.
- `BootstrapAdminSeeder` đã tồn tại local và opt-in; DataSeeder hiện giữ role/template. Không áp lại nhận định cũ “chưa tách bootstrap” mà không đọc source mới.
- Dump `seal_hackathon.bak` chưa được cung cấp trong phạm vi đã đọc. Đường D:/... trong tài liệu BE2 là đường trên máy BE2. Số31bảng/627dòng/55FK là BE2 báo cáo, chưa do AI tại workspace này xác minh.
- Các SQL legacy trong repo không phải baseline được chấp nhận. Chỉ tái sử dụng nội dung sau khi đối chiếu đầy đủ; không copy nguyên script reset.

## 3. Ranh giới trách nhiệm và điều kiện đầu vào

| Bên | Trách nhiệm |
|---|---|
| AI hỗ trợ BE1 | Rà current source/diff; lập inventory; viết migrations/config/tests và tài liệu bàn giao theo các task dưới; kiểm trên DB test riêng |
| Bạn — BE1 | Review lựa chọn dữ liệu/case/schema, phối hợp contract, review PR/test và phản hồi BE2 |
| BE2 | RDS/version/parameter group/network/TLS, database/user/secret, quyền migration/runtime, backup và cách deploy migration |
| Lead/owner dữ liệu | Quyết định dữ liệu cần giữ, scope/cutover và người chịu trách nhiệm khi lỗi |

Không tự tạo hoặc sửa RDS, import lên DB đang dùng, reset seed thật, push/deploy từ nội dung kế hoạch. Việc code/test trên môi trường lab riêng chỉ bắt đầu khi nhiệm vụ triển khai tương ứng được giao. Bước hiện tại là lên kế hoạch.

Điểm cần chốt với BE2 trước tạo DB: revision tích hợp, DB/catalog fcaj_poc, MySQL minor8.4 khả dụng, charset/collation, case handling, migration runner/identity, user privilege và cách cấp secret.

### Case handling đề xuất

Ưu tiên kiểm `lower_case_table_names=1` từ lúc khởi tạo sandbox và RDS để tương thích mapping PascalCase hiện có với khả năng kế thừa dump lowercase. AI xác minh thực tế với Hibernate validate/query trước bàn giao; không tự đổi tất cả entity sang lowercase.

AWS hỗ trợ1 cho RDS MySQL8.4; cần custom parameter group gắn khi tạo instance. Không phải tham số có thể đổi tùy ý sau khi instance8.0/8.4 đã khởi tạo. Nếu nhóm chọn case-sensitive0 thay thế, SQL/entity/import phải đồng bộ và kiểm đường dữ liệu cũ riêng.

Nguồn: [AWS lower_case_table_names](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/MySQL.KnownIssuesAndLimitations.html).

## 4. Các task AI thực hiện theo thứ tự

### DB-01 — Preflight và baseline code, 0,5 ngày

- Đọc AGENTS nếu có, xác minh Git root/branch/HEAD/status và diff hiện tại; không làm sạch/reset working tree hoặc tự checkout baseline BE2.
- Chạy test có sẵn bằng Java21/MavenWrapper, ghi lỗi từ trước; đọc test/profile/bootstrap mới trước sửa.
- Lập config/source map và ghi revision thực tế của inventory. Không đánh dấu source đang có là hoàn tất chỉ vì tên file đã xuất hiện.
- Đầu ra: báo cáo hiện trạng, kết quả test và phạm vi file dự kiến sửa.

### DB-02 — Inventory schema từ entity và DB evidence, 0,5–1 ngày

- Rà mọi @Entity/@Table/@Column/@JoinColumn, @Id/IDENTITY, type/length/precision/scale, nullable, unique/FK/index và implicit mapping.
- Sinh DDL tham khảo từ Hibernate đúng version/driver trên DB lab mới hoặc công cụ export DDL không chạm DB đang dùng; lưu phương pháp, commit và cấu hình sinh. Chỉ dùng update/create phục vụ export khi tách biệt rõ khỏi profile runtime đích.
- Review DDL: chính sách tên/case, utf8mb4/collation, boolean/time/decimal/text, thứ tự FK, constraint/index; không chỉ so tên cột.
- Nếu nhận dump hợp lệ, đọc SQL text trước, không thực thi. Phân biệt dump DB thật với các script legacy. Không ghi password hash, dữ liệu cá nhân hoặc credential vào report.
- Đầu ra: inventory + DDL review matrix cho31 entity hiện tại; schema mới AiJob chưa có thì không coi đã nằm trong baseline.

### DB-03 — Thêm Flyway tích hợp đúng Boot4, 0,5 ngày

- POM dự kiến thêm `org.springframework.boot:spring-boot-starter-flyway` và `org.flywaydb:flyway-mysql` theo dependency management Boot4.0.6; không pin Flyway mới nhất tùy tiện.
- Kiểm dependency resolution và auto-configuration thực tế. Chỉ thêm Maven plugin Flyway/CLI nếu chọn nó làm migration runner; dependency runtime không tự cung cấp lệnh Maven flyway:migrate.
- Chọn một owner chạy migration mỗi release. Lab/local có thể startup migration; AWS đề xuất một bước pre-deploy bằng identity migration, runtime dùng Hibernate validate. Nếu PoC chọn startup migration thì ghi rõ quyền DDL, thứ tự và lifecycle, không giả định runtime user chỉ cần DML.
- Đầu ra: dependencies resolve/build được; biết rõ entry point chạy migration và identity/config sẽ dùng.

### DB-04 — V1 schema nền sạch, 1–1,5 ngày

- Tạo `src/main/resources/db/migration/V1__create_core_schema.sql` từ inventory đã review, tương ứng core entity ở revision tích hợp.
- Chỉ tạo tables/constraints/indexes cần thiết trong database đã chọn. Không có DROP DATABASE, USE seal_hackathon, credential, dump business rows hoặc giả định tự tạo catalog bằng app root.
- Không dùng IF NOT EXISTS/continue-on-error để che schema không khớp; DB không rỗng đi theo nhánh baseline riêng.
- Không trộn feature AiJob/S3 mới chưa được implement vào V1 để đoán trước. Các thay đổi về sau có version mới, không sửa migration đã áp dụng.
- Đầu ra: database rỗng → migrate V1 → Hibernate validate và queries đạt; chạy lần2 không thay schema hoặc tạo bảng trùng case.

### DB-05 — Config và validator chuyển sang SQL-managed schema, 0,5–1 ngày

- Môi trường MySQL local dùng migration và AWS: Hibernate validate; Flyway enabled đúng owner/runner; tắt basic SQL initializer bằng policy rõ ràng để không có hai cơ chế schema.
- Thiết lập baseline-on-migrate=false, clean-disabled=true và validate-on-migrate theo version hỗ trợ; không bật tự baseline schema lạ. Kiểm property thực tế của Boot/Flyway đang dùng trước viết config.
- Nếu deploy runner chạy trước, runtime cấu hình Flyway disabled và Hibernate validate; nếu startup migration, Flyway phải chạy trước JPA validation và seeders. Có test cho mode được chọn.
- Sửa AwsEnvironmentConfiguration đang cho update/none: profile AWS SQL-managed phải yêu cầu validate, kiểm policy Flyway/runner phù hợp. Không vô tình đòi migration credential ở runtime khi migrations chạy ngoài app.
- Cập nhật .env.example/.env.aws.example, profile tests và comments đang nói Hibernate tạo schema.
- Test H2 hiện có có thể giữ Flyway disabled + Hibernate create-drop cho unit/context test nhanh, ghi rõ đây là ngoại lệ test. Thêm profile mysql-it dùng Flyway thật + Hibernate validate; không đưa SQL MySQL vào H2 rồi sửa SQL production cho H2 chạy.
- Đầu ra: local/test/aws/mysql-it không gọi nhầm môi trường hoặc đồng thời schema writers; AWS update/create/create-drop bị từ chối trong mode mới.

### DB-06 — Seeders và demo/reset script, 0,5–1 ngày

- Lựa chọn tối thiểu: SQL migrations quản lý schema; Java DataSeeder tiếp tục dữ liệu tham chiếu role/template có tính idempotent. Không bắt buộc chuyển mọi seed sang SQL, không lưu account/credential trong migration.
- Nếu nhóm muốn role/reference data qua SQL thì tách migration riêng và bỏ đúng phần Java tương ứng; mỗi tập dữ liệu chỉ có một owner. Không vừa seed SQL vừa seed Java cùng tập dữ liệu mà chưa đối chiếu IDs/logic.
- Giữ BootstrapAdminSeeder opt-in theo thay đổi local đã có; DemoSeeder chỉ môi trường được phép. Kiểm reference role/template đủ trước bootstrap/demo.
- Cập nhật run-demo.ps1 và preflight tests: không khẳng định Hibernate tự dựng schema nữa; demo reset chỉ DB local/sandbox được chỉ định, không target AWS/RDS; sau reset chạy migration rồi seed theo cấu hình. Reset không phải restore.
- Đầu ra: startup lần2 không nhân đôi role/template/admin; demo local đạt; AWS không tự tạo demo/admin cố định.

### DB-07 — Đường DB cũ có dữ liệu: restore + baseline, điều kiện riêng

- Chỉ thực hiện khi dump được cung cấp hợp lệ và người dùng chọn giữ dữ liệu. Thiếu dump không chặn V1/new PoC, nhưng phải ghi nhánh restore chưa kiểm.
- Giữ dump gốc; nếu có CREATE DATABASE/USE/DROP TABLE/LOCK TABLES, làm bản import cho sandbox có review, không replace chuỗi toàn cục làm đổi dữ liệu. Xác minh server/catalog/identity trước bất kỳ import nào.
- Restore bản sao vào DB riêng với case/charset phù hợp; so schema thực đầy đủ với baseline được chọn. Chưa khớp không được tự đánh dấu baseline1 để bỏ qua sai lệch.
- Với DB đã chứng minh tương ứng V1: explicit baseline version1 để bỏ qua V1 rồi chỉ áp version cao hơn. Baseline là ghi marker, không tạo/sửa schema và không chứng minh schema khớp.
- Nếu DB khác V1, lập schema diff và chọn baseline/delta strategy nhất quán cho cả đường DB mới/cũ trước code tiếp. Không dùng V1 tạo lại bảng trên DB restore hoặc bật baseline-on-migrate để né lỗi.
- Kiểm counts/FK/unique/relationships trước/sau;627dòng/31bảng chỉ dùng làm expected khi thật sự nhận đúng dump chưa lọc mà BE2 mô tả. Có lọc thì dùng manifest mới.
- Đầu ra: report restore/baseline đã test hoặc ghi rõ chưa thực hiện/thiếu đầu vào, không claim data preserved từ đọc tài liệu.

Nguồn: [Flyway baseline command](https://documentation.red-gate.com/flyway/reference/commands/baseline), [Baseline on migrate](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-baseline-on-migrate-setting).

### DB-08 — Integration tests và CI, 1–1,5 ngày

- Thêm test chạy migration thật trên MySQL8.4 lab/Testcontainers hoặc CI database service riêng; khởi tạo với case handling mục tiêu trước tạo data directory.
- Ca bắt buộc: empty migrate → Hibernate validate; migrate/restart lần2; mismatch migration checksum bị phát hiện; entity/schema mismatch bị chặn; FK/unique/index thực tế; không có bảng trùng chỉ khác case.
- Kiểm login LOCAL, role/assignment, event/team/submission/score/ranking/history, notification/support request; Unicode/time/decimal và query chính.
- Nếu cần giữ DB cũ, chạy bộ restore/baseline riêng; nếu chưa có dump thì báo Pending, không tạo fixture giả rồi gọi đó là đã validate dump BE2.
- Giữ unit/H2 suite và phân biệt báo cáo H2 với MySQL. Chạy test liên quan các thay đổi local profile/JWT/bootstrap; bảo toàn fixtures AI và kiểm lại checker nếu sửa metadata.
- CI giao phối hợp BE2: test PR không deploy; database test không chứa data thật. Chưa có Docker/DB lab thì vẫn review/build/unit được, nhưng không đánh dấu migration nghiệm thu.
- Đầu ra: test results, actual version/driver/case/charset, schema history và log đã lọc; không dựa riêng Hibernate validate để kết luận mọi index/default/data đúng.

### DB-09 — SQL migrations cho S3/AiJob/outbox khi làm tính năng, theo roadmap

- Từ thời điểm đổi cơ chế, mọi entity/column/constraint mới cần migration SQL tương ứng trong cùng PR và test.
- Bổ sung S3 metadata key/version/hash chỉ khi thiết kế cần; không tạo bảng artifact chỉ vì tên xuất hiện trong handoff.
- Thêm AiJob/outbox và durable processed-result identity khi code service/consumer; bốn state baseline PENDING_DISPATCH/QUEUED/SUCCEEDED/FAILED, các state khác theo contract.
- Unique/concurrency/idempotency constraints, input/output provenance và query indexes cần review. Không tự thêm Score/RoundResult writes cho AI.
- Tên ví dụ `V2__add_storage_metadata.sql`, `V3__add_ai_job_outbox.sql` chỉ là minh họa; số version chốt theo migrations thực đã merge để tránh va chạm giữa PR.
- Đầu ra: feature code + migration nâng cấp schema đã test, không còn phụ thuộc Hibernate update.

### DB-10 — Bàn giao BE2 và release runbook, 0,5–1 ngày

- Giao commit/PR, schema inventory/case choice, migration list/version/checksum, dependency/runner version, config không có secret, test results và nhánh dump đã/chưa kiểm.
- Chốt catalog/user/TLS và quyền identity migrate/runtime. Runtime không cần DDL nếu migrations do pre-deploy runner làm.
- Chốt cách gọi migration thực sự, working directory/locations/credential sources; không đưa lệnh có password trên command line/log và không giả định Maven plugin đã có.
- Release: backup trước thay đổi theo dữ liệu cần giữ → run migrations đã review → start app validate → smoke/health/version → ghi release manifest. Schema không tương thích thì không tự rollback image và mong DB trở lại.
- MySQL DDL không được coi là toàn bộ rollback nguyên tử: migration lỗi một phần phải đọc schema/history thực tế, có phương án forward-fix hoặc restore với owner; không tự repair history chỉ để báo xanh.
- Đầu ra: BE2 có đủ thông tin để chuẩn bị RDS parameter group và deploy, nhưng AI không tự tác động AWS chỉ vì runbook tồn tại.

## 5. File/backend cần sửa khi triển khai

Đường dẫn dưới tính từ root repository; Java classes vẫn ở package com.seal.hackathon tại checkout đang có.

| File/thành phần | Thay đổi |
|---|---|
| `back-end/src/seal-api/pom.xml` | Flyway starter/MySQL module, test integration; plugin runner chỉ khi được chọn |
| `src/main/resources/db/migration/V1__create_core_schema.sql` — mới | DDL đã đối chiếu entity, không data/secret/reset commands |
| `application.properties` và local/aws profiles | Migration policy, Hibernate validate, SQL initializer policy; không Hibernate update trong runtime SQL-managed |
| `config/AwsEnvironmentConfiguration.java` | Validation đổi theo schema-management policy mới và runner thực |
| `.env.example`, `.env.aws.example` | Config runner/runtime, DDL policy, catalog/secret references không có secret thật |
| `config/DataSeeder.java`, BootstrapAdminSeeder và config/seed/* | Chọn owner data rõ, giữ opt-in/idempotency và startup order |
| `run-demo.ps1`, `Test/run-demo-preflight-tests.ps1` | Migration trước seed, reset chỉ môi trường được phép, giữ preflight đang có |
| `src/test/resources/application-test.properties` | H2 ngoại lệ có Flyway disabled; không fake migration acceptance |
| `src/test/resources/application-mysql-it.properties` hoặc test config tương đương — mới | MySQL8.4, migration thật và ORM validate, test credentials riêng |
| `AwsEnvironmentConfigurationTest`, `EnvironmentProfilesTest`, context/bootstrap/demo tests | Chỉnh expectations theo mode mới, giữ kiểm tra secret/profile hiện có |
| Migration/MySQL integration tests — mới | Empty/repeat/history/case/schema constraints và nghiệp vụ |
| `.github/workflows/deploy.yml` hoặc workflow test/release tương ứng | Cùng BE2 thêm MySQL migration check, runner release và rollback evidence |
| `docs/contracts/ai-job-v1/README.md` | Cập nhật dòng RDS/schema còn nói không thêm Flyway; giữ trạng thái review liên nhóm |
| Roadmap BE1/config handoff/database report | Đánh dấu policy SQL-managed mới và kết quả thực; không sửa báo cáo cũ thành test đã chạy |
| Entity/service/repository | Giữ JPA và nghiệp vụ; sửa mapping chỉ khi diff có lý do; features mới đi cùng migration |

## 6. Các đường triển khai cần phân biệt

| Đường | Luồng đúng | Không làm |
|---|---|---|
| New PoC empty | Database tạo trước → migrate V1+ → Hibernate validate → Java seed có kiểm soát | Baseline để bỏ qua V1 trên DB rỗng |
| Existing DB khớp V1 đã xác minh | Backup/restore lab → schema diff → explicit baseline1 → migrations2+ → validate | Chạy V1 CREATE TABLE lên DB có bảng |
| Existing DB lệch V1 | Lập diff/strategy baseline-delta, review rồi test trên bản sao | baseline-on-migrate hoặc repair để che mismatch |
| H2 unit/context | Flyway disabled, Hibernate test create-drop, ghi đúng phạm vi | Kết luận đã kiểm MySQL migration |
| RDS release | Runner/identity được chốt, migrate trước app validate và seeders | Flyway và Hibernate update cùng sửa schema |

Flyway validate kiểm lịch sử/checksum migrations, Hibernate validate kiểm mappings ở phạm vi ORM; cả hai không thay việc kiểm dữ liệu/constraints/indexes thực tế. [Flyway validate](https://documentation.red-gate.com/flyway/reference/commands/validate), [Spring Boot initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html).

## 7. Thứ tự PR, acceptance và ước lượng

| Đợt | Task | Đầu ra review được |
|---|---|---|
| PR1 | DB-01/02/03 | Revision/inventory, dependency và migration design, chưa target DB thật |
| PR2 | DB-04/05/06 | V1 + profiles/validator + seed/demo adjustments, unit tests |
| PR3 | DB-08 | MySQL8.4 tests/CI và report new-empty/repeat/schema mismatch |
| PR4 nếu giữ dump | DB-07 | Import/baseline strategy, dữ liệu đối soát, không dump secret vào Git |
| Feature PRs | DB-09 | S3/AiJob migrations đi cùng feature code |
| Handoff | DB-10 | BE2 checklist/runner/release-runbook và test evidence |

Ước lượng chuyển core schema/config/tests khoảng5–8 ngày công tập trung, chưa gồm chờ DB/Docker/dependency network, revision liên nhóm hoặc dump data phức tạp. Đây là phần phát sinh thay cơ chế cũ, không tự cộng cứng vào mọi task trong roadmap hay cam kết xong cả AI/S3 trong thời gian đó.

Mỗi PR cần nêu nguồn DDL/revision, file thay đổi, môi trường/data dùng, kết quả test đã chạy và phần chưa kiểm. Không mark “done” nếu chỉ sinh SQL mà chưa migrate/validate trên MySQL thật.

## 8. Giao việc cho AI ở lần triển khai kế tiếp

1. Bắt đầu DB-01 và DB-02, bảo toàn working tree hiện hữu; đối chiếu schema từ entity hiện tại, không tự lấy paths/commit của BE2 làm source đang có.
2. Ưu tiên đường DB PoC mới rỗng. Nếu chưa có dump, ghi rõ nhánh dữ liệu cũ Pending và tiếp tục phần độc lập; không suy đoán dump.
3. Chuẩn bị PR migration/config/tests theo thứ tự trên, chạy kiểm tra đủ cho thay đổi khi có môi trường riêng. Không dùng AWS/DB đang hoạt động để thử.
4. Khi gặp quyết định chưa có đầu vào như target DB identity/case hoặc giữ dữ liệu, nêu đúng điểm cần chốt và tiếp tục phần không phụ thuộc. Không xin xác nhận lại cho thao tác đã được người dùng giao rõ.
5. Bàn giao diff/SQL/config/test report cho người dùng review; không tự push/deploy/chạy migration trên RDS hoặc dữ liệu thật.

## 9. Phạm vi đã thực hiện trong lượt lập kế hoạch

Đã đọc handoff BE2, current branch/status/profiles/validator/POM/seed/test/contract và dependency BOM cached; kiểm tài liệu Flyway/Spring/AWS chính thức. Chỉ tạo/cập nhật tài liệu kế hoạch. Chưa thêm Flyway/V1, đổi runtime DDL mode, chạy migration/test DB hoặc sửa các thay đổi source local đang có.
