# Báo cáo kiểm tra backend — dữ liệu, file và AI job

Ngày lập: 20/10/2026 · Cập nhật: 21/10/2026 · Source: `main@f42a168` · Phạm vi: Backend 1, proposal hạn 12:00 ngày 21/10.

## 1. Kết luận và mức độ minh chứng

Đã kiểm tra source entity/repository/service/controller/config và FE AI client; xác nhận mô hình lưu trữ hiện tại và lập [proposal chi tiết](../proposals/data-platform-proposal-detailed.md). Không chỉnh Java/config triển khai; không chạy seed, migration hoặc backend app. Các API/policy mới trong proposal là thiết kế, không phải chức năng đã tồn tại.

| Loại bằng chứng | Trạng thái |
|---|---|
| Source và đường dẫn | Đã kiểm tra trực tiếp tại checkout |
| Runtime MySQL local | Service MySQL80 chạy, TCP 127.0.0.1:3306 reachable khi kiểm tra ngoài sandbox |
| Đăng nhập DB / inventory thực tế | CLI bằng cấu hình source từng bị ERROR 1045; sau đó đọc được metadata/core counts qua phiên Workbench user mở ngày 21/10. Chưa full export/diff |
| Phiên bản MySQL | Đã xác nhận server 8.0.46 qua SELECT VERSION(); client cũng 8.0.46 |
| File thực tế | Không thấy thư mục mặc định ở workspace root/backend project; chưa biết đường runtime override |
| AWS | Chưa provision hoặc kiểm tra bucket/IAM/RDS/queue |
| Unit tests | 195 passed, 0 failures/errors/skipped; 6 suite; không suy ra từ việc đọc test source |
| FE/AI sign-off | Chưa có xác nhận; không ghi đã thống nhất |

## 2. Nhật ký thực thi kiểm tra

| ID | Thao tác đã thực hiện | Kết quả / giới hạn |
|---|---|---|
| C01 | Kiểm branch/HEAD và working tree | main/f42a168; giữ nguyên tài liệu untracked có sẵn, không reset |
| C02 | Liệt kê `@Entity` và trường | 31 entity; không coi là số bảng runtime |
| C03 | Đọc Team/TeamEventEntry/TeamMember và repository | Identity đội tách theo mùa; query quyền đề thi join TeamEventEntry |
| C04 | Đọc Submission/Score và submit/score service | URL/mô tả nằm SQL; unique team-round và score per judge/criteria |
| C05 | Đọc avatar upload/remove, OAuth mapper và resource handler | Local file vs URL ngoài phân biệt được; /uploads public; upload 5 MiB |
| C06 | Đọc đề thi controller/service/Track | Local protected, 20 MiB, release/retract/membership; staff guard cần scope review |
| C07 | Đọc application.properties, pom, Compose, Dockerfile | Hibernate update mặc định; chưa migration tool; Compose thiếu backend file volume |
| C08 | Đọc DataSeeder và DemoSeeder | Bootstrap chạy riêng; NONE không tắt bootstrap admin; không công bố credential |
| C09 | Đọc AI service/repository/DTO và FE api/ai.ts | Gemini đồng bộ; chưa AiJob/result persistence; FE đang dùng /insights |
| C10 | Đọc score list và round timer | Score list chưa lọc own-judge; timer có read-only history sau bắt đầu |
| C11 | Kiểm Docker | Docker Engine không truy cập được; user sau đó xác nhận không dùng Docker cho DB local |
| C12 | Kiểm service/cổng DB | Sandbox chặn socket/SCM; kiểm tra ngoài sandbox xác nhận MySQL80 Running, 3306 reachable |
| C13 | Kiểm mysql client | Executable tồn tại, client 8.0.46; tùy chọn `--no-login-paths` không được client hỗ trợ, bỏ trước retry |
| C14 | Kết nối DB bằng cấu hình source trong memory | ERROR 1045; secret không in ra, biến MYSQL_PWD được khôi phục; query schema chưa chạy |
| C15 | Kiểm local file paths và .env backend | Không thấy 4 đường mặc định đã kiểm; không có .env backend theo path Compose; không suy ra server không có file |
| C16 | Chạy test biệt lập | Java 21, Maven cache, test profile; chạy lại ngoài sandbox sau lỗi attach pipe, 195 passed |
| C17 | Đối chiếu tài liệu AWS | S3 private/presigned/versioning, RDS TLS/restore và SQS duplicates; link ở proposal |
| C18 | Đọc toàn bộ seal_schema.sql | 687 dòng, 31 CREATE TABLE (header còn ghi 28); có AccountApproval, thiếu MentorSupportRequest và cột Notification mới |
| C19 | Mở Workbench qua computer-use sau khi user cho kiểm tra | Phiên Local instance MySQL80 đã đăng nhập; Action Output có DROP/CREATE trước agent thao tác; không thực thi tab seal_schema/seal_seed |
| C20 | SELECT metadata trong tab riêng | 13 metric rows; server/case/timezone và thiếu bảng/cột đã xác nhận; không đọc credential |
| C21 | SELECT counts/index/constraint aggregates | 17 metric rows; 10 core tables rỗng, Role 4; 31 InnoDB, 58 FK và ba unique đúng thứ tự cột |

Agent không chạy `seal_schema.sql`, không bật Docker/MySQL/Spring Boot, không seed/migrate. Lịch sử DROP/CREATE đã hiện trong Workbench trước thao tác của agent; đó là trạng thái được quan sát, không phải thao tác agent thực hiện hay bằng chứng xác định người thực hiện.

## 3. Findings có source

| ID | Phát hiện | Nguồn | Hành động |
|---|---|---|---|
| F01 | TeamEventEntry giữ event/track/status; Team không còn giữ trực tiếp | [Entity](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/entity/TeamEventEntry.java) | Bảo toàn ID/FK khi migration |
| F02 | Submission cập nhật lại một row theo team-round, chưa lưu SHA/snapshot | [Submit service](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/service/SubmissionService.java#L94) | Snapshot độc lập cho AI job |
| F03 | Score là điểm mỗi tiêu chí/judge, không phải AI result | [Score](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/entity/Score.java) | Không để consumer AI ghi Score |
| F04 | Avatar local, URL trong DB; OAuth có thể trỏ ngoài | [Avatar service](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/service/AuthService.java#L252), [OAuth mapper](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/security/oauth2/CustomOAuth2UserService.java#L102) | Manifest phân loại, chỉ migrate file app sở hữu |
| F05 | Avatar update chưa thấy cleanup ảnh cũ trong method | [Update avatar](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/service/AuthService.java#L267) | Cleanup sau commit, không xóa nhầm ảnh ngoài |
| F06 | Đề thi xóa file cũ trước khi save/commit metadata | [Upload problem](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/service/TrackProblemService.java#L93) | Immutable key và cleanup sau commit |
| F07 | Membership đề thi join đúng TeamEventEntry; method name còn legacy | [Repository](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/repository/TeamMemberRepository.java#L47) | Giữ query guard; có thể đổi tên method ở PR riêng |
| F08 | Replace giữ release; retract không guard event status riêng | [Service](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/service/TrackProblemService.java#L103) | Không tự thay nghiệp vụ khi đổi S3 |
| F09 | Staff guard problem controller theo role, chưa thể hiện ownership event ở luồng đã đọc | [Controller](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/controller/TrackProblemController.java#L35) | Bổ sung/test scope trước public demo |
| F10 | AI coordinator branch không kiểm timer như judge branch | [AI service](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/service/AiJudgeAssistantService.java#L69) | Đánh dấu siết policy nếu giới hạn cả coordinator |
| F11 | Get scores trả mọi scores của bài sau assignment guard | [ScoringService](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/service/ScoringService.java#L323) | Chốt chấm độc lập rồi lọc response/query phù hợp |
| F12 | Read-only history được timer service cho phép sau bắt đầu | [Timer](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/service/RoundTimerService.java#L303) | AI history nên nhất quán, vẫn kiểm account/assignment |
| F13 | JWT secret fallback; bootstrap credential cố định | [Properties](../../back-end/src/seal-api/src/main/resources/application.properties#L45), [Seeder](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/config/DataSeeder.java#L42) | Fail-fast/config một lần, rotate dữ liệu đã tồn tại theo owner |
| F14 | Hibernate update mặc định; SQL snapshot có bảng legacy AccountApproval | [Properties](../../back-end/src/seal-api/src/main/resources/application.properties#L25), [SQL](../../back-end/database%20scripts/seal_schema.sql#L501) | Inventory thật rồi baseline; không dùng SQL cũ làm schema authority |
| F15 | Compose không persist avatar/problem bằng volume backend | [Compose](../../docker-compose.yml) | Chuyển S3; chỉ là rủi ro deploy Compose, không kết luận local data mất |
| F16 | DB runtime sau reset có AccountApproval, thiếu MentorSupportRequest và ba cột Notification | SELECT information_schema qua Workbench ngày 21/10; mục 7 | Chưa chốt baseline; cần export/diff đầy đủ và schema ứng viên ở DB thử |
| F17 | run-demo.ps1 có DROP DATABASE trước khi chạy backend nếu không -NoDrop | [Script](../../back-end/src/seal-api/run-demo.ps1#L105) | Không dùng reset script làm migration/restore; -NoDrop cũng không bảo đảm app chỉ đọc |

F01–F15/F17 là source findings, F16 có kiểm chứng runtime một phần. Đây không phải pentest đã chứng minh exploit hoặc kết luận UI đã lộ dữ liệu. Phạm vi hardening được ghi vào 5 task tuần 2.

## 4. Kiểm thử đã thử chạy

Lệnh wrapper ban đầu dùng `mvnw.cmd -o` nhưng chưa tiến tới test; đã dừng phiên đó. Dùng Maven có sẵn để tránh wrapper bootstrap/download:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21.0.10'
# Dùng mvn.cmd 3.9.16 từ Maven cache đã có trên máy, không chỉnh wrapper.
mvn -o '-Dspring.profiles.active=test' `
  '-Dtest=SubmissionServiceTest,ScoringServiceTest,TrackServiceTest,TeamQueryServiceTest,AuthServiceTest,AiJudgeAssistantRepoAnalysisTest' test
```

Lệnh rút gọn trên giả định Maven cache đã được đưa vào PATH; phiên thực thi dùng đường tuyệt đối tới `mvn.cmd`. Test Mockito/mapping không kết nối MySQL local. `application-test.properties` cấu hình H2 in-memory cho test context khi cần; không suy ra tương thích RDS từ H2.

Phiên chạy trong sandbox bị Java attach named pipe access denied khi Mockito khởi tạo. Đã dừng phiên đó và chạy lại đúng 6 suite ngoài sandbox, không thay code hoặc assertion.

**Kết quả xác nhận lúc 23:30:46 ngày 20/10/2026, UTC+07:** BUILD SUCCESS; 195 tests, 0 failures, 0 errors, 0 skipped; Maven báo tổng thời gian 5,660 giây cho lần chạy lại sau khi đã compile.

| Suite | Tests | Failures / errors / skipped |
|---|---:|---|
| AiJudgeAssistantRepoAnalysisTest | 8 | 0 / 0 / 0 |
| AuthServiceTest | 8 | 0 / 0 / 0 |
| ScoringServiceTest | 35 | 0 / 0 / 0 |
| SubmissionServiceTest | 53 | 0 / 0 / 0 |
| TeamQueryServiceTest | 24 | 0 / 0 / 0 |
| TrackServiceTest | 67 | 0 / 0 / 0 |
| **Tổng** | **195** | **0 / 0 / 0** |

Evidence máy: `back-end/src/seal-api/target/surefire-reports/TEST-*.xml`. Thư mục target là build artifact có thể bị clean, không cam kết lưu bền. Không công bố toàn bộ XML vì có thể chứa đường dẫn máy/system properties; bảng trên giữ số liệu cần cho worklog. Còn cảnh báo Java dynamic agent/deprecated API, không phải test failure và không bị che bằng cách sửa production code.

Không thấy class test chuyên biệt TrackProblem trong inventory test được rà; không khẳng định không có bất kỳ coverage gián tiếp nào. S3/RDS/SQS integration tests chưa có bằng chứng chạy trong lần khảo sát này.

## 5. Runtime còn cần hoàn tất

1. Kết nối Workbench đã đọc được. Còn cần schema-only export vào file mới trong workspace và inventory môi trường có data cần giữ; không gửi mật khẩu trong chat.
2. Đã đọc server/case/timezone/core counts/ba unique và số FK ở mục 7. Chưa chạy toàn bộ [data-storage-runtime-audit.sql](../checks/data-storage-runtime-audit.sql); cần full columns/FK/index/delete-rule diff, aggregate file refs và orphan checks nếu có dữ liệu.
3. Lập manifest file từ đường runtime đã xác nhận; đối chiếu DB reference với file/checksum. Không đưa filename/URL chứa PII vào báo cáo công khai.
4. Sau triển khai tuần 2: chạy matrix quyền API/S3, crash/duplicate/output sai và restore drill; lưu evidence không chứa secret.

DB inventory hiện ở mức **partial**, không còn là “chưa kết nối được”, nhưng chưa DONE cho full schema/data audit hoặc FE/AI contract sign-off. Bản proposal có thể review/nộp nội dung với assumptions/pending minh bạch. User đã hỏi giải thích migration/backup, chưa xác nhận chỉ demo hay cần giữ data khác.

## 6. Kiểm tra bộ tài liệu trước bàn giao

- Lần kiểm 20/10: 65 local links tồn tại. Sau cập nhật 21/10 đã kiểm lại 68 local links trong 4 tài liệu proposal/report/checklist; source anchors L-number không vượt số dòng file, không có lỗi liên kết hoặc trailing whitespace.
- 3 mẫu JSON trong proposal parse được; đây là kiểm cú pháp mẫu, chưa phải JSON Schema validation hoặc FE/AI sign-off.
- Sau khi rút gọn phần đóng góp Backend 1 và thêm phiếu review: kiểm 72 local links trong 5 tài liệu, 3 JSON examples, whitespace và git diff check đều pass; không sửa production code hoặc chạy thêm DB operations.
- Script SQL có transaction READ ONLY; kiểm tĩnh không có câu lệnh ghi/DDL/execute ở đầu statement. Script chưa chạy trên DB và chưa được xác nhận tương thích schema runtime.
- Không thay Java, cấu hình deploy hoặc dữ liệu DB; target chứa build/test artifacts do Maven sinh.

## 7. Kiểm tra Workbench thực tế — 21/10/2026

Skill computer-use được dùng vì CLI trước đó không đăng nhập được, còn user đã mở phiên Workbench hợp lệ. Chỉ tương tác với cửa sổ Workbench, tạo một tab SQL riêng và chạy hai truy vấn SELECT; không nhập mật khẩu, không chạy seed/schema tab và không xuất dữ liệu cá nhân. Các mục sau ghi lại đúng kết quả quan sát, không phải file schema export.

### 7.1. Metadata và lệch schema

| Metric | Giá trị |
|---|---|
| server_version / port | 8.0.46 / 3306 |
| lower_case_table_names | 1 |
| global_time_zone / session_time_zone | SYSTEM / SYSTEM |
| server_charset / server_collation | utf8mb4 / utf8mb4_0900_ai_ci |
| database_collation | utf8mb4_unicode_ci |
| table_count / engine_innodb_tables | 31 / 31 |
| foreign_key_count | 58 |
| MentorSupportRequest_exists | 0 |
| AccountApproval_exists | 1 |
| notification_new_columns | 0 trong ba cột sender_user_id, sender_role, scope_label |
| AiJob_exists / flyway_history_exists | 0 / 0 |

Ba unique indexes được kiểm theo cột thực tế, không chỉ theo tên index: TeamEventEntry `(team_id,event_id)`; Submission `(team_id,round_id)`; Score `(submission_id,judge_user_id,criteria_id)`.

### 7.2. Exact counts đã đọc

| Bảng | Bản ghi |
|---|---:|
| Role | 4 |
| User | 0 |
| HackathonEvent | 0 |
| Team | 0 |
| TeamMember | 0 |
| TeamEventEntry | 0 |
| Track | 0 |
| Round | 0 |
| Submission | 0 |
| Score | 0 |
| RoundResult | 0 |

Không suy ra 20 bảng còn lại cũng rỗng. Không có user nên chưa có bootstrap admin trong DB này tại mốc SELECT; không suy ra trạng thái ở máy/deploy khác.

### 7.3. Giới hạn và ảnh hưởng tới baseline

Action Output trước truy vấn agent đã ghi DROP DATABASE, CREATE DATABASE, CREATE TABLE và INSERT 4 role lúc 23:59:28–30. UI không gắn ngày cho từng dòng đó; hai truy vấn mới trả kết quả lúc 00:05:18 và 00:06:09 ngày 21/10 trong phiên quan sát. Chỉ có thể báo cáo DB sau lần tạo lại, không khôi phục thông tin trước reset bằng lần đọc này.

DB hiện có dấu hiệu phù hợp schema SQL cũ ở các điểm đã kiểm, không phải bằng chứng backend code-first đang chạy tương thích. Chưa export full schema, chưa so toàn bộ cột/default/index/FK, chưa kiểm validate app và chưa chạy restore drill. Bước tiếp theo trong proposal là chụp schema, review diff, dựng DB thử riêng rồi mới quyết định initial migration hoặc baseline cho DB cần giữ.
