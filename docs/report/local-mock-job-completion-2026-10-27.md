# Hoàn thiện local AI job mock

Ngày 27/10/2026, Asia/Saigon. Source: `codex/backend1-foundation`, HEAD
`f42a168d9cb0613a43801edca632a2de64837099` cùng working tree đã có.

## Kết quả

Đã hoàn thiện phần worker mock bị gián đoạn ở chat “Scan backend Markdown context”.
`LocalMockJobService` hiện có implementation cùng private store, access policy,
ba API, xử lý lỗi, profile và tests. Backend compile thành công; **530 tests pass,
0 failures/errors/skipped** trong full suite lúc 22:02:20 UTC+07. Trong đó có
**27 tests mới cho mock** và một assertion mới ở normal application context.

Không tạo stub rỗng để né compile; không xóa worker, không sửa core V1 đã nghiệm
thu MySQL. Không thêm JPA entity vào core schema. [Hướng dẫn chạy mock](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/mockdemo/README.md).

## Thành phần và hành vi

| Thành phần | Trách nhiệm |
|---|---|
| LocalMockJobService | Tạo job, status/result, fixed synthetic result và worker operations |
| LocalMockJobStore | Private JDBC H2 store; Flyway schema riêng; row lock và ledger transaction |
| LocalMockJobAccess | Query current user/roles/assignment từ business DB; phân biệt tạo và đọc lịch sử |
| LocalMockJobController/Errors | Ba route có auth/CSRF; DTO allowlist và safe error codes |
| MockDemoConfiguration | Bắt buộc explicit local/test, loopback và store URL được phép; chặn aws |
| LocalMockJobWorker | Tick có giới hạn, success/failure và completion giao lại cùng identity |
| db/mock-demo/V1 | Schema riêng cho MockAiJob và MockAiProcessedResult |

Luồng: `PENDING_DISPATCH → QUEUED → SUCCEEDED/FAILED`. Worker tắt thì giữ trạng
thái pending; chưa tự thêm timeout. Late mark-dispatched không ghi đè terminal.
Completion sai revision/unknown job/conflicting outcome không ghi đè state. Cùng
message và payload giao lại là DUPLICATE; processed ledger và result commit trong
cùng local DB transaction. Job cũ và mới độc lập, không có latest-pointer bị đảo.

Các route chỉ đăng ký khi profile mock-demo được bật:

```text
POST /api/ai/submissions/{submissionId}/jobs
GET  /api/ai/jobs/{jobId}
GET  /api/ai/jobs/{jobId}/result
```

Response status ghi `mode=LOCAL_MOCK`, kết quả ghi model `LOCAL_MOCK_V1` và disclaimer
synthetic. Không trả requester identity, repo URL, internal refs/revision, stack
trace hoặc raw completion. Result không chứa score recommendations và không ghi
Score/RoundResult. API không nhận client-chosen requester/revision/message payload.

Coordinator cần grant đúng event (null/global không đủ); Judge cần role hiện tại,
active assignment đúng round/track và chỉ đọc job của chính requester. Tạo cần
judging mở, đọc lịch sử không cần timer còn mở. Thu hồi quyền/phân công chặn lần
đọc sau. Account phải approved; không dùng participation isActive để khóa staff.

## Persistence và phạm vi cấu hình

Maven profile `mock-demo` đưa H2 vào runtime; normal artifact vẫn chỉ dùng H2 cho
tests. Spring profile `local,mock-demo` bật tính năng và bind `127.0.0.1`.
H2 jobs nằm tại `target/mock-demo/ai-jobs.mv.db`, riêng với business datasource.
Store tự tạo private DriverManagerDataSource, không đăng ký DataSource bean thứ
hai làm Boot bỏ datasource chính. Migration `classpath:db/mock-demo` chỉ áp dụng
private store; common `spring.flyway.enabled=false` của giai đoạn chuẩn bị core
vẫn được giữ. Core local/aws schema-management rollout còn là DB-05/06.

Job file tồn tại qua restart nhưng nằm dưới target bị Git ignore; `mvn clean`
hoặc xóa target sẽ mất lịch sử demo. Chưa dùng đây làm production storage.

## Kiểm thử

| Suite mới | Ca | Phạm vi |
|---|---:|---|
| LocalMockJobAccessTest | 6 | Exact event/global/wrong-event, assignment/current role, owner/history/revoke/approval |
| LocalMockJobApiTest | 6 | Spring context, JWT filter/current DB roles, CSRF, success/failure/pending, safe DTO, role revoke, datasource isolation |
| LocalMockJobStoreTest | 9 | File reopen/ledger, duplicate/conflict/stale, race, rollback, late dispatch, independent old/new job |
| MockDemoConfigurationTest | 6 | Opt-in, aws rejection, loopback, local file vs test memory store |

Full suite chạy:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21.0.10'
./mvnw.cmd -o -B '-Dspring.profiles.active=test' '-Dlogging.level.root=WARN' test
```

Test đầu tiên trong môi trường giới hạn tiến trình gặp lỗi kênh giao tiếp JVM;
đã dừng và chạy lại ngoài giới hạn này. Sau đó phát hiện đúng lỗi H2 CHECK(IN)
qua connections ở H2 2.4.240, khớp [issue chính thức #4302](https://github.com/h2database/h2database/issues/4302)
và [reproducer #4308](https://github.com/h2database/h2database/issues/4308). Đã override
H2 sang 2.5.250 có bản sửa, giữ CHECK constraints và chạy lại tất cả tests thành
công. Thay đổi chỉ ảnh hưởng test H2 và Maven profile mock runtime; không đổi
Connector/J/Hibernate/Flyway hay migrations MySQL. Flyway vẫn cảnh báo H2 mới hơn
bản verified 2.3.232; ghi nhận cảnh báo, không suy ra nghiệm thu production.

## Giới hạn còn lại

Đây là local lifecycle simulator. inputRevision là marker synthetic theo từng job,
không phải full commit SHA hay source/rubric manifest. Chưa có repo collector,
S3 artifacts, SQS/outbox production hoặc worker model. Private row dispatch marker
không phải transaction outbox chung với business DB. Chưa chốt POST idempotency,
timeout/late-result, quotas và production snapshot/result validation cùng nhóm.

Tests tạo dữ liệu synthetic trong H2 riêng; không đọc hoặc ghi bản dump thật,
local MySQL hoặc RDS. Full suite pass gỡ blocker compile trước đây nhưng không
thay việc chạy startup/API trên MySQL 8.4/RDS sau khi đổi runtime migrations.

Không commit/push/deploy hoặc gửi tin sang chat khác trong lượt này.

## Đóng gói và kiểm tra cuối

`./mvnw.cmd -B -Pmock-demo -DskipTests package` đã BUILD SUCCESS lúc
22:06:04 UTC+07, sau khi tải các dependency Maven packaging chưa có cache.
Đã kiểm archive có LocalMockJobService, cả hai migration locations riêng và
`BOOT-INF/lib/h2-2.5.250.jar`. Dependency tree xác nhận H2 là runtime khi có
`-Pmock-demo`, test-scope khi không chọn Maven profile này.

Đối soát Surefire XML: 40 suites, 530 tests, 0 failures/errors/skipped. `git diff
--check`, local document links và core V1 SHA-256 đều đạt; core V1 vẫn là
`91a3b9b0cb4d41ce5027fa850200848126bdfe57ba0ceef97defa1ff7928ea9d`.
Không khởi chạy app với cấu hình MySQL local hoặc restore dump trong lượt này.
