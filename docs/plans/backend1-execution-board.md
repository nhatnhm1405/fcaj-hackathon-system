# Backend 1 — bảng thực thi và quyết định

Cập nhật: 27/10/2026, Asia/Saigon. Kế hoạch nguồn: [backend-1-implementation-plan.md](backend-1-implementation-plan.md). Bảng này ghi tiến độ thực thi; các acceptance trong kế hoạch nguồn vẫn áp dụng.

## Phân công multi-agent

| Người thực hiện | Phạm vi đợt đầu | Reviewer |
|---|---|---|
| Agent profiles_config | BE1-02: common/local/test/aws, OAuth profiles, kiểm cấu hình và config examples | Agent chính |
| Agent bootstrap_jwt | BE1-03: bootstrap opt-in, reference seeder, demo compatibility, JWT startup validation | contract_review + agent chính |
| Agent contract_review | BE1-11: job/success/failure fixtures và kiểm tính nhất quán; review chéo foundation | Agent chính; FE/AI/BE2 review sau |
| Agent chính | BE1-01 baseline, CORS/OAuth gate/CSRF integration tests, tích hợp code, bảng tiến độ và BE1-19 matrix | Người dùng review kết quả |

Agent dùng chung checkout với phạm vi file riêng. Agent chính chạy Maven tuần tự để không ghi đè build/test artifacts. Nhánh triển khai: `codex/backend1-foundation`; source baseline `f42a168d9cb0613a43801edca632a2de64837099`.

## Review kế hoạch

Kế hoạch có đủ các mảng ứng dụng, quyền, persistence, queue và vận hành. Đường tới M0 phải ưu tiên một luồng file và một job hoàn chỉnh. Domain/OAuth browser, avatar đầy đủ, dữ liệu thật và model thật nằm ở các mốc sau như kế hoạch đã phân chia.

Các điểm cần kiểm soát khi triển khai:

1. Contract phải tách quyết định người dùng khỏi xác nhận liên nhóm. Bộ fixtures là đầu ra review, không chứng minh API hay worker đã chạy.
2. Quyền Coordinator hiện flatten thành authority, nên API job mới phải query grant đúng event. Tạo và đọc lịch sử dùng hai phép kiểm khác nhau.
3. Submission chưa có version: snapshot cần identity bất biến và lock/recheck thật; không chỉ đặt một trường version trong JSON.
4. Completion có thể tới trước mark-dispatched. State terminal phải được giữ nguyên; commit DB trước ack queue và dedup nguyên tử là acceptance bắt buộc.
5. Profile test phải tách `.env`, DB và OAuth khỏi cấu hình local. H2 không thay bằng chứng MySQL 8.4/RDS.
6. Nghiệm thu AWS chờ outputs/identity/lịch test thực. Tên resource dự kiến không phải resource đã tồn tại. Không gom toàn bộ roadmap vào thay đổi foundation đầu tiên.

## Quyết định được người dùng xác nhận trong chat

| ID | Quyết định ngày 27/10 | Phạm vi |
|---|---|---|
| D01 | Chưa có môi trường hoặc contract BE2/AI bàn giao | BE1-06/11/19/20 còn phụ thuộc ngoài repo |
| D02 | Bốn state: PENDING_DISPATCH, QUEUED, SUCCEEDED, FAILED; mock trước; nhóm review trước tích hợp AWS | Contract v1 |
| D03 | Bootstrap admin opt-in mọi môi trường, email/password cấp bên ngoài | Foundation |
| D04 | Coordinator chỉ đúng event được phân công; chỉ tạo job lúc judging mở | API job mới; grant null/global không đủ |
| D05 | Judge đọc job mình tạo khi còn quyền; Coordinator đọc trong event được phân công; đóng chấm vẫn đọc lịch sử; revoke chặn lần đọc tiếp theo | API job mới |
| D06 | Mock giữ trạng thái chờ khi worker offline; ghi nhận để kiểm tra, chưa tự đặt timeout | Timeout/late-result phải chốt trước tích hợp thật |
| D07 | Hạn demo M0 và thời gian review hôm nay | Đã hỏi, chờ trả lời |

D02/D04/D05/D06 là quyết định phía người dùng; FE/AI/BE2 chưa được ghi là đã xác nhận. Không tự gửi thông báo hoặc cập nhật Project online.

## Backlog toàn bộ scope

`Review/test` nghĩa đã có đầu ra local cần nghiệm thu. `Chờ ngoài repo` ghi rõ phần vẫn có thể chuẩn bị local. Chỉ đánh dấu hoàn tất khi acceptance tương ứng có bằng chứng.

| Task | Mốc | Trạng thái hiện tại | Phần tiếp theo / phụ thuộc |
|---|---|---|---|
| BE1-01 Hiện trạng/test | M0 | Baseline đã chạy; đang ghi báo cáo/config inventory | 433 tests: 432 pass, 1 context error; kiểm lại sau foundation |
| BE1-02 Profiles/config | M0 | Đang triển khai | Profile isolation, startup checks, config matrix và tests |
| BE1-03 Bootstrap/JWT | M0 | Code xong, chờ test tích hợp | Opt-in, không tự promote existing account, JWT validation |
| BE1-19 Bàn giao tích hợp | M0 | Matrix được chuẩn bị; chờ ngoài repo | [Outputs cần BE2/AI](backend1-integration-matrix.md) |
| BE1-11 Contract | M0 | Fixtures đã kiểm local; chờ nhóm review | [Contract v1](../contracts/ai-job-v1/README.md) |
| BE1-06 MySQL8.4/Hibernate | M0 | Chưa thực hiện | DB lab mới, driver/TLS/schema/CRUD/restart; outputs BE2 |
| BE1-08 Storage adapter | M0 | Chưa thực hiện | Local/S3 adapter, stream/metadata/failure tests |
| BE1-09 Đề thi có quyền | M0 một luồng mẫu | Chưa thực hiện | Adapter08, staff policy/fixture, rollback/cleanup |
| BE1-12 Snapshot | M0 | Chưa thực hiện | Contract11, repo/SHA thật, limits/format và adapter08 |
| BE1-13 AiJob/outbox/API | M0 | Chưa thực hiện | Snapshot12; quyền D04/D05; transactional state/idempotency |
| BE1-14 Dispatcher SQS | M0 | Chưa thực hiện | Outbox13, queue/IAM và race tests |
| BE1-15 Result consumer | M0 | Chưa thực hiện | Contract11, DB13, queue/IAM, validation và commit-before-ack |
| BE1-16 Log/health/version | M0 tối thiểu | Chưa thực hiện | Log theo request/job, health/version phục vụ deploy |
| BE1-17 Gói deploy | M0 tối thiểu | Chưa thực hiện | Build/config/smoke; BE2 release/digest/API/log |
| BE1-20 Mock AWS | M0 | Chờ các task M0 và ngoài repo | EC2/RDS/S3/SQS thật, worker mock, duplicate/stale/restart |
| BE1-04 Auth/domain | M1 | Một phần config/CORS/CSRF đang làm trong foundation | Browser/TLS/domain thật chưa có |
| BE1-05 OAuth/email | M1 | OAuth profiles được chuẩn bị | Callback/provider/mail/domain và browser thật |
| BE1-10 Avatar S3 | M1 | Chưa thực hiện | Adapter08 và URL/FE contract |
| BE1-07 Dữ liệu cần giữ | Sau M0 nếu cần | Chưa thực hiện | Quyết định data và backup/restore riêng |
| BE1-18 Model thật/FE | M2/M3 | Chưa thực hiện | Model/FE/AWS/runtime và acceptance các mốc |

## Nhịp review tiếp theo

Đợt 1 nghiệm thu profiles/bootstrap/JWT và bộ fixtures. Đợt 2 triển khai adapter/file và chuẩn bị MySQL 8.4; snapshot/API AI dựa trên contract đã review. Đợt 3 triển khai transport/result và vòng mock AWS khi outputs đã sẵn sàng. Mỗi đợt lưu bằng chứng tests và giới hạn còn lại; chờ hạ tầng không đồng nghĩa dừng các phần local độc lập.

SM-01…04 thuộc phần điều phối của người dùng: chuyển bảng task sang Project khi có yêu cầu, theo dõi owner/reviewer, phụ thuộc và lịch tích hợp. Agent mới chỉ chuẩn bị bảng local.

## Cập nhật nghiệm thu local — 27/10, 22:02 UTC+07

Các bảng phía trên giữ mốc đợt đầu. Source hiện đã có core V1 và lab MySQL8.4
restore/validate ([report](../report/sql-dump-review-2026-10-27.md)).
Đã viết tiếp phần LocalMockJobService/worker dang dở: ba API opt-in, private H2
job/ledger bền qua reopen, current authorization và duplicate/stale/race checks.
Full backend suite mới nhất: **530 pass, 0 failure/error/skipped**, gồm 27 tests
mock. Blocker thiếu LocalMockJobService đã được gỡ.

Demo local đã có code/tests; AWS M0, production snapshot/outbox/S3/SQS và runtime
migration rollout vẫn pending. Chi tiết và lệnh chạy trong
[report local mock](../report/local-mock-job-completion-2026-10-27.md).
