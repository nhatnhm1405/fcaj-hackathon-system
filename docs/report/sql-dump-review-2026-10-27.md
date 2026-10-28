# Kiểm tra dump SEAL trước migration

Ngày kiểm tra: 27/10/2026, Asia/Saigon. Người dùng cung cấp file để tiếp tục
[kế hoạch SQL migrations](../plans/backend1-sql-migration-execution-plan.md) và đã
chọn giữ dữ liệu cũ, kiểm thử chuyển trong cùng đợt.

## Kết quả

`E:/FA26/AWS/seal_hackathon.bak` là **plain-text MySQL dump** chứa cấu trúc và dữ
liệu, không phải bản backup nhị phân của SQL Server. Có thể đọc để chuẩn bị restore
MySQL; đuôi `.bak` không xác định engine/format.

| Thuộc tính | Giá trị đọc trực tiếp |
|---|---|
| Dung lượng | 157.581 bytes |
| SHA-256 | `ab6279c0ad77be4405ca1505c1da8b7db2a1a7c6b9e052db5b672790e1f23802` |
| Dump client/server | MySQL 8.0.45 |
| Dump completed trong header | 2026-10-27 11:00:26; header không ghi timezone |
| Tables / columns | 31 / 253 |
| Rows trong INSERT tuples | 627 |
| INSERT statements | 24; 7 bảng không có INSERT |
| PK / FK / unique constraints | 31 / 55 / 18 |
| ON DELETE CASCADE | 3 theo mappings hiện tại |
| Table engine / charset / collation | InnoDB / utf8mb4 / utf8mb4_0900_ai_ci trên cả 31 bảng |
| Tên bảng | Chữ thường trong dump |

Số 627 được đếm từ tuples thực trong file, có xử lý dấu nháy/escape/dấu phẩy/JSON
trong dữ liệu, không lấy lại con số từ handoff BE2. Chưa dùng con số đó để khẳng
định restore hoặc dữ liệu được bảo toàn.

## Đối chiếu với backend

Đã so dump với [Hibernate reference DDL](../checks/schema-inventory-2026-10-27/hibernate-mysql84-reference.sql)
từ 31 entity, Hibernate 7.2.12.Final, MySQLDialect 8.4, physical naming StandardImpl.
Source inventory thuộc `f42a168d9cb0613a43801edca632a2de64837099` cùng working tree;
hash từng entity nằm trong [inventory](../checks/schema-inventory-2026-10-27/entity-schema-inventory.md).

**31/31 bảng khớp trong phạm vi so sánh tĩnh; 0 khác biệt** về tập cột, kiểu/length/
precision/scale, nullable, IDENTITY, default/ON UPDATE, ordered PK/unique/FK columns,
FK target và update/delete rule. `int/integer`, `bit/bit(1)`, implicit NULL/default
NULL và FK NO ACTION/RESTRICT được chuẩn hóa.

Giới hạn phải giữ rõ:

- Tên bảng/cột được so không phân biệt hoa/thường. Dump lowercase và entity
  PascalCase chỉ được runtime chấp nhận khi case handling phù hợp. Chưa đọc
  `lower_case_table_names` từ DB đích; file không đủ để chứng minh giá trị này.
- Thứ tự cột và tên constraint được bỏ qua khi so cấu trúc. INSERT của dump dùng
  thứ tự vật lý của bảng dump; không lấy INSERT đó nạp trực tiếp vào V1 có thứ tự
  cột khác. Restore cả schema/data vào bản sao trước, hoặc xuất lại với column list
  khi chọn đường data-only đã review.
- Secondary indexes được đếm riêng, không nằm trong assertion tương đương.
  Cần so definitions/column order/index type với indexes thực do MySQL tạo cho V1.
- Reference DDL kế thừa charset/collation của database. Nếu dùng collation khác
  dump, cần kiểm uniqueness và so sánh chuỗi trước khi chuyển dữ liệu.
- `User.expired_at` trong dump là nullable datetime(6), không có ON UPDATE. Mapping
  export cũng như vậy; comment Java cũ không chứng minh có hành vi DB đó.
- Chưa kiểm orphan FK, duplicate, auto-increment counters, checksum dữ liệu,
  password login hay nghiệp vụ trên database thật.

[JSON kết quả](../checks/schema-inventory-2026-10-27/dump-static-comparison.json)
chỉ chứa metadata, row counts theo bảng và kết quả so schema; không chứa giá trị
tài khoản, password hashes hoặc dữ liệu bài nộp.

## Bản import lab cần chuẩn bị

File gốc có **1 CREATE DATABASE, 1 USE, 31 DROP TABLE và 31 LOCK TABLES**, cùng
session settings của mysqldump. Đã kiểm cấu trúc DDL, không thấy CREATE TRIGGER,
PROCEDURE hoặc FUNCTION trong định dạng dump này.

Giữ file gốc ngoài repository. Khi restore lab:

1. Chọn MySQL 8.4 riêng và khởi tạo case handling trước khi tạo data directory;
   đề xuất `lower_case_table_names=1` để kiểm cả dump lowercase và entity PascalCase.
   Ghi actual minor/version/case/charset/collation trong evidence.
2. Tạo database sandbox riêng, kiểm identity và server thực tế. Chuẩn bị bản
   import ở vị trí không đưa vào Git; xử lý từng statement điều khiển catalog/reset,
   không replace chuỗi toàn cục trên dữ liệu.
3. Restore bản sao schema/data, kiểm counts cho cả 31 bảng, FK/orphan/unique,
   auto-increment và dữ liệu đặc trưng. Dump tạm tắt FK checks khi import nên việc
   import thành công tự nó chưa xác nhận không có orphan.
4. Tạo V1 từ inventory đã review, chạy vào DB rỗng riêng; so metadata thực của
   DB đó và DB restore, rồi chạy Hibernate validate/query trên cả hai.
5. Chỉ explicit baseline version 1 trên bản restore sau khi xác minh tương ứng
   V1. Không bật `baseline-on-migrate`, không dùng marker để bỏ qua chênh lệch.

Kết quả đọc file hiện đủ để **tiếp tục chuẩn bị V1 và restore lab**. Chưa chạy
restore, migration hoặc baseline. DB-07/DB-08 còn Pending runtime acceptance.

## Kiểm tra công cụ

Đã thêm `Test/schema/inspect-dump.mjs` và chạy **7 tests pass**, bao gồm tuple
escaping/JSON, DEFAULT NULL, type aliases, missing tables, malformed tuple,
unsupported attributes và FK rules/unique column order. Chạy inspector trên dump
thật và reference DDL thành công. Tool chỉ đọc/so file; không có DB client hoặc
SQL execution. Dùng output path mới để tránh ghi đè evidence.

## Cập nhật sau restore lab thực tế

Các mục trên ghi nhận mốc đọc tĩnh ban đầu. Sau đó đã tiếp tục thử trên Docker
MySQL **8.4.13**, Connector/J **9.7.0**, Hibernate **7.2.12.Final**, Flyway **11.14.1**.
Image chính thức được pin digest:
`mysql@sha256:76f0358884cb92d9a086d24e5671c38048d3fef40f2588703d73eb55392d6b69`.

Cấu hình lab: port động chỉ bind `127.0.0.1`, `lower_case_table_names=1`,
`utf8mb4/utf8mb4_0900_ai_ci`. Hai catalog mới trong container riêng:
`fcaj_restore_lab` và `fcaj_empty_lab`. Credentials sinh riêng trong process,
không sử dụng `.env` hoặc user DB hiện hữu. Thử nghiệm không kết nối RDS/MySQL nguồn.

### Kết quả nghiệm thu lớp schema/dữ liệu

| Kiểm tra thực thi | Kết quả |
|---|---|
| Restore dump vào catalog riêng | Đủ 31 bảng / 627 rows; từng bảng khớp tuple counts trong dump |
| Import warnings qua JDBC | 0; không bỏ qua cảnh báo chuyển kiểu/truncation |
| 55 FK orphan checks | 0 orphan |
| Duplicate check mọi unique key/PK | 0 vi phạm; xử lý NULL theo semantics MySQL |
| Auto-increment counters | Đều lớn hơn MAX ID tương ứng |
| Flyway tạo schema từ V1 trên DB rỗng | 1 migration thực thi |
| Chạy migrate lần 2 | 0 migration thực thi |
| Runtime schema comparison | Columns/types/null/defaults/extra/collation, engine, secondary indexes và FK rules khớp; bỏ qua thứ tự cột và tên index/constraint |
| Hibernate validate | Đạt trên cả schema mới và schema restore |
| Probe checksum migration bị thay đổi | Flyway validate từ chối như mong đợi |
| Probe schema thiếu Notification.scope_label | Hibernate validate từ chối như mong đợi |
| Migrate DB có dữ liệu chưa baseline | Bị từ chối; baseline-on-migrate=false |
| Explicit baseline version 1 sau schema/data checks | Đạt; không chạy lại V1 trên schema restore |
| Đối soát trước/sau baseline | SHA-256 từng bảng theo ordered PK và JDBC bytes không đổi; schema cũng không đổi |

Đã đưa [V1__create_core_schema.sql](../../back-end/src/seal-api/src/main/resources/db/migration/V1__create_core_schema.sql)
vào source và kiểm lại bằng **đúng file này** sau chuẩn hóa định dạng. Final SHA-256:
`91a3b9b0cb4d41ce5027fa850200848126bdfe57ba0ceef97defa1ff7928ea9d`;
Flyway checksum **-732525868**. V1 chỉ tạo schema, không chứa catalog/reset/data/credential.

[Evidence cuối](../checks/schema-inventory-2026-10-27/mysql84-lab-evidence.md)
chỉ ghi metadata/counts. Byte fingerprints so trong memory, không xuất dữ liệu
tài khoản, bài nộp hoặc row hashes vào Git.

### Phạm vi còn lại và cảnh báo thực tế

Đây là nghiệm thu schema/restore trong lab, chưa phải nghiệm thu ứng dụng/RDS.
Chưa chuyển local/aws profiles sang Hibernate validate/Flyway runtime. Chưa có
full app startup/login/API smoke vì source `LocalMockJobWorker` còn thiếu
`LocalMockJobService`. Các bước cấu hình DB-05/06 và CI/full service acceptance
DB-08 vẫn cần hoàn tất. MySQL minor trên RDS chưa được BE2 xác nhận.

Flyway 11.14.1 trong BOM phát cảnh báo MySQL 8.4 mới hơn phiên bản được thư viện
ghi nhận verified (8.1). Các checks trên 8.4.13 đã đạt, nhưng không bỏ qua cảnh báo
hoặc tự nâng dependency: cần review ở bước release/integration. Hibernate cũng
cảnh báo built-in pool chỉ phù hợp test; pool này chỉ dùng trong lab utility.
Cảnh báo createSchemas=false xuất hiện trong negative control chưa có history;
hai catalog đã được tạo chủ đích trước, không cho Flyway tự tạo DB.

Đã chạy ba lab: lần đầu dựng đường kiểm thử, lần hai bổ sung per-table counts và
SQL warnings, lần cuối kiểm bằng đúng V1 đã định dạng trong source. Container
`fcaj-migration-lab-6a60a82c8f10`, `fcaj-migration-lab-936480e0b66c` và
`fcaj-migration-lab-c395bb6cd245` đều đã dừng, giữ lại bản sao dữ liệu để review.
Statement files chứa dữ liệu nằm dưới `back-end/src/seal-api/target/` được Git ignore.
Chưa xóa container/volume/image; dump gốc không sửa và hash được kiểm lại.

Cập nhật sau đó lúc 22:06 UTC+07: đã hoàn thiện LocalMockJobService và chạy full
test profile thành công 530/530 ca, gồm mock API/security tests trên H2. Blocker
compile nêu ở trên đã được gỡ; chưa thay đổi kết luận về MySQL/RDS application
acceptance hoặc runtime migration rollout. [Report mock](./local-mock-job-completion-2026-10-27.md).
