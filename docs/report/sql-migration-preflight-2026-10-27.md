# SQL migration — preflight và inventory đợt đầu

Ngày: 27/10/2026, Asia/Saigon. Source: `codex/backend1-foundation`,
HEAD `f42a168d9cb0613a43801edca632a2de64837099` cùng working tree hiện tại.

## Quyết định và phạm vi

Người dùng giao bắt đầu thực hiện [execution plan](../plans/backend1-sql-migration-execution-plan.md)
và Q&A trong quá trình làm. Đã chọn **giữ dữ liệu SEAL cũ và kiểm thử chuyển trong
cùng đợt**. Chưa nhận đường dẫn dump; đã giải thích dump là bản xuất cấu trúc và dữ
liệu. Chưa có quyết định môi trường lab được trả lời. Docker Engine đọc được qua
quyền ngoài sandbox; lúc kiểm tra không có container/image local.

Không có AGENTS.md trong inventory repository đã tìm. Giữ nguyên các thay đổi
profiles/JWT/bootstrap/demo/fixtures có sẵn. Không thực thi reset/demo runner,
restore, migration trên DB, thao tác AWS, commit, push hoặc deploy.

## DB-01 — bằng chứng build trước thay đổi

Chạy Maven Wrapper 3.9.16 với JDK 21.0.10:

```powershell
./mvnw.cmd -o '-Dspring.profiles.active=test' test
```

**BUILD FAILURE trước khi chạy test**: `mockdemo/LocalMockJobWorker.java` tham
chiếu `LocalMockJobService`, nhưng class service chưa có trong checkout. Đây là
source untracked có trước lượt migration. Không thêm stub service, bỏ file khỏi
build hoặc báo test pass. Cần xác nhận owner phần đang viết dở hoặc hoàn thiện
source trước khi kiểm thử toàn backend. Annotation `@Profile("mock-demo")` chỉ
điều khiển runtime; không bỏ qua yêu cầu resolve type khi Java compile.

Kiểm tra độc lập đã chạy trong lượt này:

| Kiểm tra | Kết quả | Giới hạn |
|---|---|---|
| `Test/run-demo-preflight-tests.ps1` | 19 checks pass | Chỉ helper, không gọi runner/MySQL |
| AI fixture checker | 7 JSON fixtures pass | Không gọi AWS/network |
| Entity-only Java compilation + Hibernate export | Đạt | Không phải build toàn app |
| Maven dependency classpath và dependency tree | Đạt | Không phải test hoặc app startup |

## DB-02 — inventory từ Hibernate thực tế

Đã bổ sung [công cụ offline](../../back-end/src/seal-api/Test/schema/README.md).
Công cụ compile 31 entity hiện tại với classpath Maven thật, dùng Hibernate
7.2.12.Final, MySQLDialect 8.4 và PhysicalNamingStrategyStandardImpl. JDBC metadata
access tắt; UserSuppliedConnectionProviderImpl không cung cấp kết nối. Chỉ xuất
script, `jakarta.persistence.schema-generation.database.action=none`.

Đầu ra review:

- [Inventory chi tiết từng cột, PK, FK, unique, source hashes](../checks/schema-inventory-2026-10-27/entity-schema-inventory.md).
- [DDL MySQL tham chiếu](../checks/schema-inventory-2026-10-27/hibernate-mysql84-reference.sql).

Đếm từ metadata/DDL: **31 bảng, 253 cột, 31 IDENTITY PK, 55 FK, 18 unique
constraints**. Có 3 FK ON DELETE CASCADE theo `@OnDelete`; không tự thêm cascade
khác. 0 secondary indexes được khai báo trong entity; MySQL tự tạo indexes hỗ
trợ FK cần kiểm sau khi thực thi. Đây chưa phải V1 được nghiệm thu.

### Review matrix và quyết định còn cần

| Hạng mục | Đã thấy ở mapping/export | Cần kiểm/chốt |
|---|---|---|
| Tên bảng/case | PascalCase; User được quote | Đề xuất lower_case_table_names=1 khi khởi tạo lab; đối chiếu dump thực |
| Charset/collation | Entity chưa chỉ rõ; DDL kế thừa database | Chọn cùng lab/RDS sau khi đọc metadata/dump; chưa tự đổi collation |
| Identity | 30 integer PK, PasswordResetOtp.id bigint | Auto-increment sau restore phải lớn hơn max ID thực |
| Boolean | Hibernate sinh BIT | Validate/bind/query thực trên MySQL |
| Thời gian | LocalDateTime sinh datetime(6) | Kiểm dữ liệu múi giờ và microseconds khi restore |
| Decimal | Score/criteria decimal(5,2), result decimal(7,2) | Kiểm precision/rounding và values dữ liệu cũ |
| Text/length | Inventory liệt kê TEXT/varchar và nullability | So từng cột, không chỉ tên bảng |
| Defaults | Không có SQL defaults từ mapping đã xuất | Builder và @PrePersist hoạt động ở Java; không tự chuyển sang SQL default |
| User.expired_at | DDL chỉ datetime(6) | Comment Java nói ON UPDATE nhưng mapping không khai báo; cần so dump trước quyết định |
| FK/unique | Đúng mapping hiện tại; 55/18 | Kiểm index order, delete rule, orphan/duplicate trên bản restore |
| Unique với NULL | JudgeAssignment/UserEventRole có cột nullable | Giữ semantics MySQL hiện tại; không tự siết dữ liệu trong V1 |
| AiJob/outbox | Chưa có entity trong core | Không thêm vào baseline bằng suy đoán |

## DB-03 — dependency preparation

POM bổ sung `spring-boot-starter-flyway` và `flyway-mysql`, không pin version
ngoài BOM. Đã resolve thật qua Maven Central, sau đó chạy dependency tree offline
thành công:

| Thành phần | Version resolved |
|---|---|
| Spring Boot starter/module Flyway | 4.0.6 |
| Flyway core/MySQL | 11.14.1 |
| Hibernate ORM | 7.2.12.Final |
| Connector/J | 9.7.0 |

`spring.flyway.enabled=false` được đặt rõ trong common config **cho giai đoạn
chuẩn bị này**, tránh việc thêm starter tự chạy vào DB local đang có. Chưa chuyển
profiles sang validate và chưa thêm migration vào runtime locations. Bước DB-04/05
sẽ thay trạng thái này sau khi có V1 và runner rõ ràng. Không đánh dấu DB-03 hoàn
tất: full app compile/auto-configuration/startup còn cần kiểm.

Nguồn kỹ thuật đã đối chiếu: [Spring Boot initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html),
[Hibernate schema coordinator](https://docs.hibernate.org/orm/7.2/javadocs/org/hibernate/tool/schema/spi/SchemaManagementToolCoordinator.html),
[Flyway baseline-on-migrate](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-baseline-on-migrate-setting).

## Bước kế tiếp

1. Nhận vị trí dump hoặc xác định DB nguồn cần export. Dump có thể chứa dữ liệu
   cá nhân/password hashes; giữ ngoài Git và chỉ ghi metadata/aggregate vào report.
2. Chốt MySQL 8.4 lab riêng, case handling và collation. Chưa chạy migration hoặc
   baseline trên DB hiện hữu. Bản restore phải được so đầy đủ trước explicit baseline.
3. Xử lý source worker thiếu service cùng owner; chạy lại full tests trước/sau
   thay đổi migration. Export offline không thay thế bước này.
4. Review reference DDL thành V1; thêm cấu hình SQL-managed và runner/test MySQL,
   rồi kiểm new-empty/repeat/mismatch và restore/baseline trong cùng đợt giữ dữ liệu.

DB-07 restore/data-preservation và DB-08 MySQL acceptance hiện **Pending**;
chưa có evidence runtime để tuyên bố bảo toàn dữ liệu hoặc chuyển thành công.

## Cập nhật: đã nhận dump nguồn

Người dùng đã cung cấp `E:/FA26/AWS/seal_hackathon.bak` sau đợt preflight.
Đã đọc file và hoàn tất đối chiếu tĩnh; xem [báo cáo dump](./sql-dump-review-2026-10-27.md).
Input dump không còn thiếu. Các đoạn phía trên giữ lại trạng thái tại mốc
preflight. Đã tiếp tục restore/migrate/validate trong MySQL 8.4.13 lab; xem phần
cập nhật runtime và evidence trong báo cáo dump. Đã thêm V1 được kiểm thực tế.
Full backend/API tests và chuyển profiles runtime vẫn chưa được nghiệm thu.

Cập nhật 22:06 UTC+07: blocker LocalMockJobService đã được xử lý; full backend
test profile đạt 530/530 ca và mock API được kiểm qua Spring/JWT trên H2. Xem
[báo cáo hoàn thiện mock](./local-mock-job-completion-2026-10-27.md). Việc chuyển
runtime SQL-managed profiles và kiểm API trên MySQL8.4/RDS vẫn còn riêng.
