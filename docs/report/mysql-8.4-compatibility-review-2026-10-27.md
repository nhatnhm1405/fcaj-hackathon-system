# Đánh giá tương thích MySQL 8.4 cho SEAL

Ngày: 27/10/2026, Asia/Saigon. Commit source: `f42a168`.

**Đính chính sau kiểm tra lại cùng ngày:** schema thiết kế hiện lấy từ entity Java và Hibernate; các SQL cũ không thuộc luồng khởi động đã tìm. Không dùng chênh lệch script cũ/entity hoặc 58 FK trong script làm bằng chứng DB runtime thiếu bảng hay đã tương thích. Flyway chưa được tích hợp; chỉ là phương án quản lý thay đổi tương lai nếu nhóm chọn.

## Kết luận

**Có cơ sở chọn RDS MySQL 8.4 cho DB mới. Chưa phát hiện blocker ở tầng Java/JPA/query qua rà source. Chưa nghiệm thu chạy trên MySQL 8.4 hoặc RDS.**

Docker `mysql:8.0` là lựa chọn server trong Compose; không phải ràng buộc buộc toàn bộ backend chỉ chạy 8.0. Dependency driver/ORM hiện đại của project phù hợp để thử 8.4 mà chưa thấy cần viết lại nghiệp vụ.

Đề xuất: chốt 8.4 là phiên bản đích có điều kiện, thử trên DB 8.4 mới/riêng rồi nghiệm thu trên RDS trước chuyển môi trường chính. Không tự sửa Compose hay nâng cấp volume/DB đang có trong lần đánh giá này.

## 1. Tình trạng hỗ trợ và mục tiêu chi phí

Theo AWS tại thời điểm tra cứu:

| Engine | Hết RDS standard support | Bắt đầu Extended Support year 1 pricing |
|---|---|---|
| MySQL 8.0 | 31/07/2026 | 01/08/2026 |
| MySQL 8.4 | 31/07/2029 | 01/08/2029 |

Vì vậy, chọn RDS 8.4 mới phù hợp mục tiêu tránh phụ phí Extended Support của RDS 8.0. Chi phí DB/storage/backup thông thường vẫn phải tính. Docker MySQL 8.0 local không tự phát sinh phụ phí RDS này.

Nguồn: [AWS RDS MySQL version lifecycle](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/MySQL.Concepts.VersionMgmt.html).

Ngày support của major không thay thế lịch hỗ trợ từng minor. BE2 cần kiểm minor 8.4 và instance class thực sự khả dụng ở Singapore, chốt minor cho lab/RDS và cập nhật estimate theo cấu hình đó.

## 2. Bằng chứng dependency

| Thành phần | Bằng chứng trong môi trường/repo | Đánh giá |
|---|---|---|
| Java | `pom.xml` đặt Java 21; JDK 21.0.10 chạy được bằng đường dẫn trực tiếp | Không thấy ràng buộc server 8.0 |
| Spring Boot | Parent 4.0.6 trong pom | Dùng dependency management của Boot |
| Connector/J | Không pin version riêng trong pom; BOM 4.0.6 đã cache đặt 9.7.0; classpath trong báo cáo test có sẵn cũng có 9.7.0 | Oracle xác nhận Connector/J 9.7.0 dùng với MySQL Server 8.0 trở lên, bao gồm 8.4 |
| Hibernate ORM | BOM và classpath báo cáo test có sẵn chỉ 7.2.12.Final | Tài liệu nhánh 7.2 liệt kê MySQLDialect với minimum DB 8.0 |
| Driver class | `com.mysql.cj.jdbc.Driver` | Không phải driver legacy `com.mysql.jdbc.Driver` |
| Dialect | Không thấy ép một MySQL dialect cũ trong main application config | Có thể để ORM nhận diện DB qua connection metadata |

Nguồn: [Connector/J 9.7.0 release notes](https://dev.mysql.com/doc/relnotes/connector-j/en/news-9-7-0.html), [Hibernate ORM 7.2 dialect support](https://docs.hibernate.org/orm/7.2/dialect/).

Không cần đổi driver thành 8.4 chỉ để trùng số phiên bản server. Hai sản phẩm có version độc lập. Kết quả dependency ở đây dựa vào BOM cached và báo cáo test có sẵn; không phải một lần dependency resolution/build mới thành công.

## 3. Entity, query và SQL đã rà

- Entity sử dụng kiểu thông thường: INT/BIGINT, VARCHAR, TEXT, boolean, decimal và thời gian; IDENTITY/AUTO_INCREMENT. Chưa thấy custom type bắt buộc MySQL 8.0.
- Repository dùng Spring Data và JPQL/HQL; không thấy `nativeQuery=true`, `createNativeQuery`, JdbcTemplate/JdbcClient trong production source đã tìm.
- JPQL đã rà chủ yếu JOIN/FETCH, EXISTS/COUNT, điều kiện, CONCAT/LOWER/LIKE và ORDER BY. Chưa thấy phụ thuộc câu lệnh server bị loại bỏ trong 8.4.
- Chưa thấy config ép `mysql_native_password` hoặc `default_authentication_plugin`, replication commands cũ, stored procedure/trigger/DEFINER trong SQL nguồn đã tìm.
- Script schema dùng InnoDB, utf8mb4; CREATE DATABASE chỉ rõ `utf8mb4_unicode_ci`. Đây không phải blocker 8.4; vẫn phải giữ/chọn collation nhất quán khi chuyển dữ liệu.
- Production Java hiện có 31 `@Entity`/`@Table`, 55 `@JoinColumn`, không thấy `referencedColumnName` tùy chỉnh. Mappings được rà chưa thể hiện tham chiếu khóa không chuẩn; cần kiểm DDL thực do Hibernate sinh trên 8.4 để xác nhận.
- Thống kê cũ về 31 CREATE TABLE/58 REFERENCES chỉ mô tả script legacy, không dùng để nghiệm thu schema ứng dụng hiện tại.
- Chưa thấy identifier mới bị reserved trong 8.4 (`QUALIFY`, `TABLESAMPLE`) trong production entity/config/SQL đã tìm. Bảng `User` được quote trong entity và script.

MySQL 8.4 hạn chế tạo foreign key tới non-unique/partial referenced keys theo mặc định. Rà mapping Java chưa thấy dấu hiệu blocker này; vẫn chưa xác minh FK/index trong DB thật hoặc mọi câu SQL do Hibernate sinh.

Nguồn thay đổi: [MySQL 8.4 changes](https://dev.mysql.com/doc/refman/8.4/en/mysql-nutshell.html), [MySQL 8.4 keywords](https://dev.mysql.com/doc/refman/8.4/en/keywords.html).

## 4. Vấn đề cụ thể cần giải quyết

### 4.1. Nguồn schema thực tế là Java/Hibernate

- `application.properties` cấu hình `spring.jpa.hibernate.ddl-auto=${DDL_AUTO:update}`: mặc định Hibernate tạo/cập nhật schema từ mappings khi ứng dụng khởi động; có thể bị override bởi môi trường.
- PhysicalNamingStrategyStandardImpl giữ tên bảng/cột được khai báo trong Java.
- Compose chỉ mount volume dữ liệu MySQL, không mount `seal_*.sql` vào initdb.
- `src/main/resources` không có schema.sql/data.sql/import.sql hoặc migration directory; chưa thấy SQL initializer tùy chỉnh, Flyway/Liquibase dependency hay bước chạy script cũ trong workflow đã rà.
- `DataSeeder` và `DemoSeeder` ghi dữ liệu bằng Java repositories sau khi JPA được khởi tạo. Seeder không phải phần định nghĩa schema.

Vì vậy, `MentorSupportRequest` và các cột Notification đã có trong Java không bị coi là thiếu khỏi schema hiện tại chỉ vì SQL cũ không ghi chúng. Bảng AccountApproval trong SQL cũ cũng không chứng minh DB thực đang có bảng đó. Sự khác biệt chỉ cho thấy script legacy không còn là nguồn chuẩn.

Không cần sửa script cũ để backend tạo schema trên MySQL 8.4. Cần cho Hibernate sinh/cập nhật schema trên DB lab mới, đọc startup DDL/log và kiểm metadata thực. Với DB có dữ liệu, đối chiếu entity với DB thật, không đối chiếu với file SQL cũ để kết luận runtime.

Flyway là lựa chọn mới cần nhóm quyết định riêng, không phải prerequisite bắt buộc để thử 8.4. `validate` không tạo bảng; chỉ chuyển sang chế độ đó sau khi schema đã được tạo đúng. `update` cũng không thay thế đầy đủ việc kiểm soát rename/drop/data migration trên DB có dữ liệu.

### 4.2. Tài khoản DB và authentication plugin

MySQL 8.4 tắt `mysql_native_password` theo mặc định; dùng `caching_sha2_password` cho tài khoản DB phù hợp. Chưa đọc plugin của account trong DB đang chạy nên đây là mục cần kiểm tra khi migration, không phải lỗi đã xác nhận.

Với RDS mới, tạo application DB user mới theo quyền cần thiết và thử bằng đúng Connector/J runtime. Không chuyển nguyên system database/user tables từ MySQL local sang RDS. Đây là xác thực kết nối DB, khác với mật khẩu tài khoản người dùng ứng dụng lưu trong bảng User.

Nguồn: [MySQL 8.4 native authentication change](https://dev.mysql.com/doc/refman/8.4/en/mysql-nutshell.html).

### 4.3. JDBC URL local chưa phù hợp làm cấu hình RDS

`application.properties` trỏ localhost; Compose override URL tới `mysql-db`. URL local có tắt SSL và tùy chọn tự tạo database. Chuyển sang RDS cần endpoint/database/user/secret thực và TLS có kiểm chứng server theo driver/CA của RDS.

Không chỉ đổi `.env` rồi cho rằng Compose sẽ dùng RDS: giá trị `SPRING_DATASOURCE_URL` đang được ghi trực tiếp trong phần environment của service backend. Phải sửa/override cấu hình triển khai tương ứng; Compose hiện còn dependency vào local mysql service.

Nguồn: [RDS SSL/TLS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.SSL.html).

### 4.4. Case, collation và sql_mode

Project giữ nguyên PascalCase bằng PhysicalNamingStrategyStandardImpl. Khi chuyển giữa Windows/local và Linux/RDS, kiểm `lower_case_table_names` và tên thật của các bảng. Đây là khác biệt môi trường cần kiểm tra, không phải riêng thay đổi 8.4.

Kiểm charset/collation thực tế, tiếng Việt, email unique, decimal điểm và timestamp. Không đổi sql_mode/khóa ngoại sang chế độ lỏng chỉ để import thành công; xác định nguyên nhân query/data trước.

### 4.5. Test hiện tại chưa chứng minh MySQL 8.4

`application-test.properties` dùng H2 MySQL mode, `ddl-auto=create-drop`; CI backend hiện chạy profile này. Test pass trên H2 không xác nhận SQL/DDL/auth/TLS của MySQL 8.4. Cần một lần integration trên 8.4 thật, sau đó trên RDS.

## 5. Kế hoạch nghiệm thu trước khi chốt chuyển

1. **Chuẩn bị môi trường riêng:** MySQL 8.4 với container/volume/database mới, không gắn volume 8.0 hiện tại; giữ nguyên DB nguồn và backup cần thiết. Chọn minor tương ứng RDS dự kiến.
2. **Chuẩn bị schema code-first:** cho Hibernate từ commit tích hợp tạo/cập nhật DB lab mới với `DDL_AUTO=update`; không import seal_schema.sql/seal_seed.sql cũ. Kiểm các bảng/cột/FK/index thật và Java seeders. Nếu nhóm chọn migration tool thì lên task riêng từ DDL/entity đúng, không dùng SQL cũ làm baseline mặc định.
3. **Kết nối bằng application DB user:** dùng driver hiện tại, auth plugin mục tiêu; kiểm startup log không có DDL/FK/syntax error. Sau dựng schema, restart bằng `ddl-auto=validate`.
4. **Smoke test nghiệp vụ:** login/session, CRUD event/track/team, assignment, submit/update bài, chấm điểm/ranking, notification/support request và quyền đọc/ghi. Kiểm tiếng Việt, số thập phân, thời gian và constraint.
5. **Nếu giữ dữ liệu cũ:** export schema/data từ DB thật; restore thử vào 8.4 riêng; so row counts, constraints, dữ liệu mẫu và API. Không import mysql system schema. Sửa lỗi có bằng chứng rồi thử lại.
6. **RDS integration với BE2:** verify engine version, instance class, TLS/CA, DB user, SG và datasource thực tế; chạy lại các smoke test từ backend trên EC2.
7. **Chốt bàn giao:** commit/entity version, schema thực/DDL được xác minh, chính sách ddl-auto, engine minor, log/test evidence, backup/cutover/rollback; migration artifacts nếu nhóm đã chọn công cụ đó. Rollback app chỉ khi schema tương thích; không coi đổi image về 8.0 sau khi volume đã được 8.4 nâng cấp là phương án rollback.

Owner: BE1 schema/application/test; BE2 DB/network/backup/cấu hình RDS; Lead quyết định cutover và dữ liệu cần giữ.

## 6. Những gì đã và chưa thực hiện

Đã: đọc source/config/SQL/workflow, kiểm BOM cached và classpath test cũ, parse tĩnh FK/schema names, đối chiếu tài liệu chính thức Oracle/Hibernate/AWS. JDK 21 trực tiếp chạy được.

Chưa: chạy MySQL 8.4, import/export DB thật, smoke test backend trên 8.4, kiểm RDS/account/Region class availability hoặc benchmark.

Giới hạn công cụ: Docker CLI có nhưng Docker Desktop Linux engine chưa chạy, báo không có named pipe engine. Lệnh Maven dependency tree offline không chạy được vì maven-dependency-plugin 3.9.0 chưa có cache; đây không phải lỗi tương thích MySQL của ứng dụng. Không dùng báo cáo unit test có sẵn như kết quả test mới.

Chưa sửa code ứng dụng, Compose, secret, DB hoặc volume. Đánh giá hiện tại đủ để chọn hướng lab 8.4, chưa đủ gắn nhãn “đã chuyển thành công”.
