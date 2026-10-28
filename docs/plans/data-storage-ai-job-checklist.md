# Kế hoạch kiểm tra phần việc dữ liệu — deadline 12:00 ngày 21/10/2026

Ngày lập: 20/10/2026; cập nhật 21/10/2026, Asia/Saigon. Scope: khảo sát, phương án dữ liệu, đề xuất hợp đồng và backlog cho proposal.
Người thực hiện: Backend 1 theo phân công; tên chưa được cung cấp.

## 1. Đầu ra và quy tắc thực hiện

- Bản đóng góp ngắn: [data-storage-access-proposal.md](../proposals/data-storage-access-proposal.md).
- Proposal chi tiết: [data-platform-proposal-detailed.md](../proposals/data-platform-proposal-detailed.md).
- Phiếu trao đổi/nghiệm thu metadata và quyền: [ai-job-review-handoff.md](../proposals/ai-job-review-handoff.md), chưa có FE/AI sign-off.
- Kết quả kiểm tra thực thi: [báo cáo source/runtime/tests](../report/data-storage-backend-audit-2026-10-20.md); [SQL inventory chỉ đọc](../checks/data-storage-runtime-audit.sql).
- Phụ lục kiểm tra này: nguồn bằng chứng, đề xuất metadata/quyền, câu hỏi chờ chốt và 5 task tuần 2.
- Giữ riêng trạng thái: **đã đọc source**, **đề xuất**, **chờ xác nhận**, **đã kiểm thử thực tế**.
- Đã khảo sát source, chạy 195 unit tests liên quan thành công và đọc runtime MySQL qua Workbench ngày 21/10: 31 bảng sau reset còn lệch entity, core counts đã ghi. Chưa full export/diff, file manifest, AWS hoặc migration/restore drill; không sửa backend/DB.
- Không trích giá trị secret/password vào báo cáo; chỉ nêu vị trí và cách loại bỏ default.
- Không tự đổi quy tắc nghiệp vụ khi thay storage. Mọi thay đổi về người xem, release/retract hoặc lịch sử AI phải được ghi quyết định.
- Không đánh dấu Frontend/AI đã đồng ý nếu chưa có phản hồi. Soạn nội dung để người thực hiện gửi/trao đổi, không tự nhắn thành viên.
- Tài liệu này hỗ trợ phần đóng góp cá nhân. Sơ đồ nộp workshop vẫn do nhóm tự thiết kế/review theo quy định FCAJ.

## 2. Lịch thực hiện tới hạn nộp

Các mốc T+ tính từ lúc bắt đầu phiên làm việc; là timebox đề xuất để điều chỉnh theo giờ thực tế, không phải giờ công đã ghi nhận.

| Mốc | Công việc | Đầu ra / Q&A |
|---|---|---|
| T+0–45 phút | Rà entity/repository, avatar/đề thi, cấu hình và AI access | Ghi source evidence; hỏi phạm vi bàn giao và các quyết định FE/AI đã có |
| T+45–90 phút | Hoàn thiện bảng RDS/S3 và quyền đề thi | Hỏi avatar có tiếp tục public, yêu cầu retract và quyền admin/coordinator |
| T+90–150 phút | Soạn job schema và quy tắc đọc kết quả | Mang contract tới FE/AI: ai xem, khi nào xem, phiên bản/retry/lỗi |
| T+150–195 phút | Migration/backup/restore, security backlog | Xác nhận dữ liệu cũ cần giữ hay chỉ là demo; owner của AWS/DB/secrets |
| T+195–240 phút | Rút gọn bản proposal, chốt 3–5 task tuần 2 | Đối chiếu từng yêu cầu, ghi quyết định và pending |
| 21/10 trước 10:30 | Nhận phản hồi FE/AI/Lead, sửa điểm chưa rõ | Chốt acceptance hoặc ghi người quyết định + hạn phản hồi |
| 21/10 10:30–11:30 | Review cuối và đóng gói bản bàn giao | Một bảng proposal + contract + backlog; không phát sinh scope mới |
| Trước 12:00 ngày 21/10 | Bàn giao theo kênh của nhóm | Người dùng tự gửi; lưu bằng chứng review/đóng góp cá nhân |

Nếu timebox vượt 10:30 ngày 21/10, ưu tiên bản proposal và các quyết định quyền dữ liệu; thử nghiệm live chuyển thành task tuần 2 có tiêu chí nghiệm thu.

## 3. Kiểm tra từng mục được giao

| Mục | Cách kiểm tra | Tiêu chí hoàn thành | Hiện trạng |
|---|---|---|---|
| Backend lưu dữ liệu gì | Liệt kê entity, quan hệ Team/TeamEventEntry, Submission/Score và config datasource/schema | Phân biệt dữ liệu SQL, file local và URL ngoài hệ thống; có nguồn source | Đã rà 31 entity; 195 tests pass; đọc metadata/core counts/unique runtime qua Workbench; full diff còn pending |
| Avatar | Đọc AuthService, User.avatarUrl, WebMvcConfig và SecurityConfig | Xác định đường ghi/đọc/xóa, giới hạn upload và ai sửa/đọc | Đã rà source |
| Đề thi | Đọc TrackProblemService/Controller, Track, TeamMemberRepository | Biết upload/release/retract/download và membership theo mùa | Đã rà source |
| Chọn RDS/S3 | Ánh xạ từng loại dữ liệu; xác định DB key so với file bytes | Bảng proposal không lưu secret/whole repo vào queue; không dùng public policy cho đề thi | Có proposal chi tiết; chờ review nhóm |
| Bảng quyền | Tách người dùng API khỏi IAM principal | Nêu rõ đọc/ghi và điều kiện, nhất là avatar, đề thi, AI result | Chờ quyết định avatar/result sharing |
| Quyền tải đề | So sánh code guard với phương án S3 và test matrix | Giữ TeamEventEntry, release/retract và preview; nêu giới hạn thu hồi | Đề xuất stream, chưa live test |
| Metadata AI job | Đối chiếu AI hiện tại, thảo luận FE/AI về state/input/output | Field required/null, state transitions, version và access rules được ghi rõ | Dự thảo; chưa được FE/AI chốt |
| Migration/backup | Tách DB mới/demo khỏi DB cần bảo toàn; ghi quy trình restore thử | Không coi ddl-auto update/SQL cũ là kế hoạch migration; kiểm cả object reference | Đã bổ sung baseline gates, hai nhánh dữ liệu và restore runbook; user hỏi giải thích trước chọn nhánh; chưa thực hành |
| JWT/admin/schema | Kiểm tra default và quan hệ DataSeeder/DemoSeeder | Có vị trí source và task xử lý, không công bố giá trị credential | Đã xác minh source |
| 3–5 task tuần 2 | Viết task có dependency, owner/reviewer và acceptance | Mỗi task có thể review độc lập, không chỉ là tên dịch vụ | 5 task dự thảo ở mục 10 |

## 4. Bằng chứng hiện trạng

Các đường dẫn nguồn dưới đây theo source `main@f42a168`; cần rà lại nếu checkout đổi.

| Chủ đề | Kết quả đọc source | Nguồn |
|---|---|---|
| Dữ liệu quan hệ | 31 entity JPA; Team giữ identity, TeamEventEntry giữ event/track/status | [TeamEventEntry](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/entity/TeamEventEntry.java) |
| Submission | Lưu repo/demo/slide URL và mô tả trong DB; không có file bài nộp repository trong entity | [Submission](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/entity/Submission.java) |
| Avatar | Ghi `uploads/avatars`, DB giữ URL `/uploads/avatars/...`; own profile upload/xóa; tối đa 5 MiB theo code, kiểm MIME prefix image | [AuthService](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/service/AuthService.java#L252), [WebMvcConfig](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/config/WebMvcConfig.java), [SecurityConfig](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/config/SecurityConfig.java#L132) |
| Đề thi | Một file/track, local `protected/problems`, tối đa 20 MiB, PDF/DOC/DOCX/ZIP; DB giữ key và release metadata | [TrackProblemService](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/service/TrackProblemService.java#L71), [Track](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/entity/Track.java) |
| Membership | Tên method còn giống mô hình cũ nhưng JPQL đã JOIN TeamEventEntry; không dùng Team.track/status trực tiếp | [TeamMemberRepository](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/repository/TeamMemberRepository.java#L47) |
| Preview / management | Coordinator và System Admin được preview; chỉ Coordinator có các endpoint quản lý. Guard role này chưa tự chứng minh scope theo event | [TrackProblemController](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/controller/TrackProblemController.java#L35) |
| AI hiện tại | Gemini gọi đồng bộ, trả gợi ý; chưa có AiJob entity/queue/result persistence trong package entity đã khảo sát | [AiJudgeAssistantService](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/service/AiJudgeAssistantService.java#L69) |
| Quyền AI hiện tại | Coordinator truy cập theo role; Judge phải active assignment đúng round/track (track null nghĩa all-track) và judging open khi yêu cầu phân tích | [SubmissionRepository](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/repository/SubmissionRepository.java#L29), [AiJudgeAssistantService](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/service/AiJudgeAssistantService.java#L69) |
| Ẩn danh | DTO AI hiện tránh team/member identity; job metadata nội bộ không đồng nghĩa được trả nguyên cho judge | [AiInsightResponse](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/response/AiInsightResponse.java) |
| JWT | Cấu hình có fallback JWT_SECRET; cần fail-fast khi thiếu ở môi trường AWS | [application.properties](../../back-end/src/seal-api/src/main/resources/application.properties#L45) |
| Bootstrap | DataSeeder chạy riêng và tạo admin với credential cố định; `SEED_SCENARIO=NONE` chỉ tắt DemoSeeder | [DataSeeder](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/config/DataSeeder.java#L59), [DemoSeeder](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/config/seed/DemoSeeder.java) |
| Schema / volume | `DDL_AUTO` mặc định update; Compose chỉ có named volume cho DB/Caddy, chưa có volume riêng avatar/đề thi | [application.properties](../../back-end/src/seal-api/src/main/resources/application.properties#L25), [docker-compose.yml](../../docker-compose.yml) |
| Runtime sau reset | Server 8.0.46, 31 InnoDB tables, 58 FK; Role 4, 10 bảng nghiệp vụ rỗng; thiếu MentorSupportRequest/ba cột Notification, có AccountApproval; ba unique chính đúng | [Báo cáo runtime 21/10, mục 7](../report/data-storage-backend-audit-2026-10-20.md) |

Các dòng source là kiểm tra tĩnh; dòng runtime là SELECT qua phiên Workbench user mở ngày 21/10. CLI trước đó bị ERROR 1045 nhưng không còn là blocker đọc metadata qua UI. Agent quan sát Action Output đã DROP/CREATE trước thao tác, chỉ đọc DB sau reset; không suy ra data trước reset hoặc schema ở máy/deploy khác. Đã có 195 unit tests pass; chưa kiểm API/S3/restore thật hoặc full schema diff. Không thấy 4 thư mục upload mặc định đã kiểm; chưa biết đường runtime override. Chưa thấy test class chuyên biệt TrackProblem trong inventory được rà; không suy ra hoàn toàn không có coverage gián tiếp.

## 5. Quyền đề thi: giữ nguyên nghiệp vụ khi đổi storage

### Điều kiện hiện tại cần giữ

1. User được xác thực; track phải thuộc event trong URL.
2. Coordinator/System Admin có quyền preview theo role hiện tại, kể cả trước release. Việc scope staff theo từng event cần Lead xác nhận, không tự coi đã có trong code.
3. Các user khác chỉ được tải khi có membership trong team với TeamEventEntry APPROVED gắn đúng track.
4. File phải tồn tại và `problemReleased=true` cho participant.
5. Upload/replace/remove chỉ khi event SETUP hoặc IN_PROGRESS; release chỉ khi IN_PROGRESS.
6. Retract hiện không có event-status guard riêng; replace đang giữ nguyên cờ released. Thay đổi các hành vi này là quyết định nghiệp vụ mới.

### Phương án S3 đề xuất

- Backend tự resolve object key từ Track trong DB, không nhận một S3 key tùy ý từ client.
- Object key ví dụ `problems/events/{eventId}/tracks/{trackId}/{uuid}.pdf`; không dùng tên file người dùng làm đường dẫn tin cậy.
- Backend kiểm tra guard mỗi request rồi stream S3 GetObject; trả Content-Disposition hợp lệ và `Cache-Control: private, no-store`.
- Bucket không public, không đặt prefix đề thi dưới CloudFront behavior public của avatar/frontend.
- Retract chặn lượt tải mới sau khi trạng thái đã commit; không thể thu hồi bản người dùng đã lưu hoặc tự cắt stream đã được cấp.
- Nếu chọn presigned URL, phải ghi TTL và độ trễ thu hồi chấp nhận được. Đổi cờ release trong RDS không tự vô hiệu link S3 đã cấp.
- Upload object mới -> commit metadata tham chiếu mới -> dọn object cũ sau commit. Nếu DB lỗi, giữ object cũ hợp lệ và dọn orphan có retry; S3 và DB không chung transaction.

### Test matrix để tuần 2 thực hiện

| Tình huống | Kết quả kỳ vọng |
|---|---|
| Anonymous tải | Bị từ chối trước khi trả bytes |
| Participant APPROVED đúng track, đã release | Tải được, nội dung đúng object/checksum |
| Đúng track nhưng chưa release / sau retract | Không nhận được bytes |
| Khác track hoặc khác event trong URL | Bị từ chối / resource mismatch |
| Team PENDING/REJECTED/DISQUALIFIED, không có membership hoặc đã rời team | Không được tải theo membership đó |
| Mentor/Judge không có membership/privileged role | Không được tải chỉ nhờ role mentor/judge |
| Coordinator/Admin preview trước release | Được preview theo policy đã chốt; Admin không tự có management endpoint |
| Thay file khi released | Theo code hiện tại vẫn released; xác nhận nhóm trước nghiệm thu |
| Object không tồn tại / storage lỗi | Lỗi kiểm soát được; không rò bucket/key/credentials |
| Request truyền file/key của track khác | Không vượt guard qua key tự chọn |
| S3 URL anonymous | Access denied, không có bytes |
| Stream bắt đầu trước retract | Ghi rõ giới hạn; request mới phải bị chặn |

## 6. Metadata AI job và hợp đồng FE/AI — dự thảo

### Field nội bộ tối thiểu

| Field | Quy tắc đề xuất |
|---|---|
| `jobId` | UUID do backend cấp; định danh và khóa chống xử lý trùng |
| `submissionId` | FK tới submission; backend xác minh quyền trước khi tạo |
| `requesterUserId` | FK người yêu cầu để audit; không tự cấp quyền xem vĩnh viễn |
| `repoUrlSnapshot` | Repo được chấp nhận tại thời điểm tạo, chỉ dùng nội bộ/worker; không suy ra từ URL submission đã bị sửa sau đó |
| `commitSha` | Full commit hash đã resolve để tái lập; khi dùng QUEUED như trạng thái sẵn sàng chạy thì commit phải có |
| `status` | Đề xuất QUEUED, RUNNING, SUCCEEDED, FAILED, EXPIRED |
| `createdAt` | Server time UTC; field bắt buộc |
| `startedAt` | Null trước khi worker nhận/claim; không suy ra RUNNING từ thời điểm frontend gọi API |
| `finishedAt` | Có ở trạng thái cuối; cập nhật cùng state |
| `resultKey` | Null trước thành công; S3 object key nội bộ. Không trả thẳng thành public URL |
| `resultVersionId` | Nếu bucket versioning được bật và cần chỉ đúng object version |
| `errorCode`, `errorMessage` | Null khi không lỗi; message cho người dùng đã sanitize, không có stack trace/secret |

Thông tin đề xuất thêm để integration: `analysisVersion`, `schemaVersion`, `deadlineAt`, `modelId/revision`, `adapterVersion`, `rubricVersion`, `promptVersion`. Worker report actual model/version; backend đối chiếu contract. Chỉ thêm retry/heartbeat fields sau khi thống nhất BE2, tránh biến bảng tối thiểu thành toàn bộ queue engine.

- Job mới dùng UTC và timestamp API có timezone; dữ liệu cũ LocalDateTime cần xác định timezone trước khi chuyển đổi, không tự thêm Z vào lịch sử.
- Event/round/track suy ra từ submission và entry theo event; nếu cần snapshot để nghiên cứu thì mô tả riêng, không dùng snapshot cũ để bỏ qua kiểm tra quyền hiện tại.
- Chọn FK delete behavior và retention với Lead; chưa đề xuất cascade mất lịch sử AI.
- Result key chỉ được nhận trong bucket/prefix dành cho chính job/version; backend kiểm schema, khoảng điểm và evidence trước khi SUCCEEDED.
- Hai kết quả trùng không tạo side effect trùng; version cũ không ghi đè version mới. AiJob không thay thế Score/RoundResult.

### Quy tắc đọc kết quả đề xuất — chờ chốt

- Tạo job: giữ quyền yêu cầu AI hiện tại — Judge phải được phân công active đúng submission và judging open; Coordinator theo quyền hiện hành.
- Đề xuất đầu tiên cho đọc: requester còn quyền assignment hợp lệ hoặc Coordinator; judge khác chưa được tự chia sẻ. Nhóm có thể chọn chia sẻ toàn panel, nhưng phải xác nhận và sửa policy rõ.
- Participant, Mentor, System Admin không mặc nhiên có quyền đọc raw AI result chỉ vì có tài khoản/role đó. Quyền phục vụ quản trị riêng cần Lead chốt.
- Đề xuất sau thảo luận với user: revoke chặn request sau, giữ audit; judging paused/closed chỉ xem lịch sử riêng nếu account/assignment còn hợp lệ; không tạo job mới; không tự chia sẻ job của judge cũ cho người thay thế. Chưa có xác nhận nhóm.
- Frontend nhận DTO status/result đã lọc: job ID, status, safe error, thời điểm cần thiết, result qua API có kiểm quyền. Không trả requester identity, repo URL/bucket key hoặc dữ liệu nhận diện team cho màn judge ẩn danh.
- Worker không dùng RDS credentials. Worker chỉ nhận thông điệp tối thiểu, ghi result S3, gửi result/status event theo IAM role.

### Hợp đồng cần gửi FE/AI để review

| Bên | Cần họ xác nhận | Đầu ra mong đợi |
|---|---|---|
| Frontend | Enum state, polling/backoff, hiển thị lỗi, xem job cũ, ai được xem và cách giữ ẩn danh | Status/result DTO + mock QUEUED/RUNNING/SUCCEEDED/FAILED/EXPIRED |
| AI | Ai resolve SHA, input schema, actual model/rubric version, JSON evidence/error, giới hạn repo và thời gian | Một input và một result JSON mẫu đã thống nhất |
| Backend 2 | Ai publish/consume queue, STARTED signal, duplicate/version, retry/DLQ/recovery | Event contract và trách nhiệm cập nhật state |
| Lead | Staff event scope, chia sẻ judge, đọc sau đóng judging, retention và dữ liệu cần giữ | Quyết định nghiệp vụ có người xác nhận |

Tên API đề xuất: POST `/api/ai/submissions/{id}/jobs` trả 202/jobId; GET `/api/ai/jobs/{jobId}`; GET `/api/ai/jobs/{jobId}/result`. Đây là thiết kế mới, không ghi là route đang tồn tại. Endpoint hiện tại vẫn là request insight đồng bộ.

## 7. Migration, backup và restore — mô tả cho proposal

### Dữ liệu DB

1. Xác nhận DB chỉ có demo hay phải giữ tài khoản/submission/score thật. Lấy inventory schema/version/row counts bằng tài khoản read-only khi được cấp; không tự kết nối môi trường chưa được chỉ định.
2. Chuẩn bị baseline migration từ entity và schema thực tế; SQL cũ chỉ làm tham khảo. Chọn RDS MySQL còn standard support và kiểm tra tương thích ở DB thử nghiệm.
3. DB mới: migrate schema rồi bootstrap có kiểm soát; DB hiện có: backup, restore thử, đối chiếu và baseline migration history đúng hiện trạng trước cutover.
4. Chạy migration một lần bằng tài khoản riêng; runtime app có quyền dữ liệu, `DDL_AUTO=validate`, demo seed tắt ở môi trường tích hợp đã chốt.
5. Cutover có maintenance window hoặc chặn ghi theo scope; giữ DB cũ và kế hoạch hoàn nguyên. Rollback image không tự rollback schema/dữ liệu.

Cập nhật runtime: chưa dùng DB sau reset hiện tại làm baseline vì nó thiếu bảng/cột backend cần. Chốt schema theo các gate: export snapshot → xác nhận data cần giữ → schema ứng viên ở DB thử → diff đầy đủ → initial migration hoặc baseline phù hợp → validate và restore thử. Code-first không đồng nghĩa app tự khởi động MySQL; Hibernate có thể đổi schema khi kết nối được. run-demo.ps1 có DROP mặc định, không dùng làm migration/restore.

### File

- Lập manifest file local -> S3 key, size, SHA-256 và metadata liên quan; phân biệt avatar upload với avatarUrl từ OAuth bên ngoài.
- Copy object, kiểm checksum/khả năng đọc, rồi cập nhật reference DB. Không tự download hoặc di chuyển avatar OAuth không thuộc storage của app.
- Giữ bản gốc đến khi xác minh quyền/redeploy và có backup. Thay storage không thay cờ release hoặc tự công khai object.

### Backup/restore

- Đề xuất automated backups RDS với retention 7 ngày cho demo (chờ cost/Lead), snapshot trước migration; thực hành restore sang instance khác, không overwrite DB gốc.
- Kiểm security group/parameter/endpoint của DB restore; smoke test quan hệ team/event, submission/score và đối chiếu file manifest.
- S3 Versioning cho dữ liệu cần phục hồi, lifecycle version cũ theo retention được chốt. RDS restore không tự đưa S3 về cùng thời điểm; immutable keys/version ID giúp đối chiếu và phát hiện missing/orphan objects.
- Ghi thời gian restore và dữ liệu khôi phục thực tế; RPO/RTO là mục tiêu chờ đo, chưa có kết quả benchmark.
- Schema-only export không chứa row data; cần DB schema+data, file/manifest, version app/migration và secrets ở kho riêng để có bộ phục hồi. Chưa có bằng chứng bộ backup này đã được tạo.
- Restore DB sang instance mới, kiểm cả reference/version S3 và quyền, rồi mới đổi endpoint khi Lead duyệt. Không tự replay AI job/queue sau restore mà chưa reconcile trạng thái và result đã có.

## 8. Danh sách thay đổi backend

- Storage interface cho avatar/problem; local adapter cho dev và S3 adapter cho AWS; cập nhật map URL/key và thao tác xóa sau commit.
- Giữ guard đề thi và tên file/content headers; bổ sung cache-control và test quyền theo event/track/release.
- JWT secret bắt buộc ở môi trường AWS; bỏ fallback, kiểm định dạng/độ mạnh phù hợp thư viện và fail-fast; không log secret.
- Tách bootstrap admin khỏi seed demo, dùng giá trị cấp riêng một lần. Nếu tài khoản default đã tồn tại thì cần rotate/disable theo owner, không chỉ sửa code tạo mới.
- Flyway/baseline, cấu hình RDS/TLS, migrate user riêng, runtime validate; tắt demo seed không đồng nghĩa tắt bootstrap.
- Entity/repository/job API mới, output DTO ẩn danh, result validator, version/idempotency; giữ chính sách assignment và điểm chính thức.
- Phối hợp BE2 cho outbox/queue/result consumer và error handling; không đưa AWS account credentials vào DB nghiệp vụ.

## 9. Nhật ký Q&A và quyết định chờ xác nhận

| ID | Câu hỏi / quyết định | Trạng thái |
|---|---|---|
| Q01 | Hạn bàn giao và thời điểm bắt đầu | User xác nhận bắt đầu ngay; hạn 12:00 ngày 21/10/2026 |
| Q02 | Chỉ khảo sát/proposal hay phải triển khai thử? | User yêu cầu bắt đầu kiểm tra và hoàn thiện proposal chi tiết; chưa yêu cầu sửa/deploy backend |
| Q03 | FE/AI đã thống nhất contract/quyền nào? | User xác nhận ngày 21/10 chưa trao đổi, cần bản để nhóm review. Đã chuẩn bị phiếu và lời nhắn gửi kèm; chưa có sign-off |
| Q04 | Avatar giữ public như hiện tại hay chuyển authenticated-only? | Đề xuất giữ hành vi hiện tại qua app; cần FE/Lead chốt |
| Q05 | Retract chặn request mới là đủ? Replace file đã release có giữ released? | Đề xuất giữ code hiện tại và stream; cần xác nhận nghiệp vụ |
| Q06 | Chỉ requester xem AI result hay toàn panel? Có xem sau đóng judging? | Đã thảo luận ưu/nhược; đề xuất requester + revoke + history riêng; hỏi xác nhận, chưa được nhóm chốt |
| Q07 | Dữ liệu cũ bắt buộc giữ không; môi trường nào được cấp để inventory? | Đã đọc runtime qua Workbench user mở; DB sau reset lệch backend, core counts rỗng. Hỏi data ở môi trường khác; user yêu cầu giải thích migration/backup, chưa chọn nhánh. Cần full export/diff, không cần gửi password |
| Q08 | AI trả mẫu JSON/version/evidence/error nào; ai resolve SHA? | Chờ buổi review contract |

Sau mỗi vòng trả lời: cập nhật câu trả lời, người xác nhận, ngày giờ và mục bị ảnh hưởng. Nếu thiếu phản hồi trước hạn, ghi pending cụ thể; không biến giả định thành quyết định nhóm.

## 10. Năm task backend đề xuất cho tuần 2

| Task | Owner / phối hợp | Dependency | Nghiệm thu |
|---|---|---|---|
| B1 — JWT/Bootstrap config | BE1 + Lead | Lead chốt nơi cấp secret | AWS profile thiếu secret fail rõ; không dùng credential mặc định; test seed không tạo lại admin sau restart |
| B2 — RDS migration baseline | BE1 + BE2/Lead | Q07, schema export/diff sau phát hiện runtime, B1 | Candidate schema khớp backend, không xóa legacy tùy ý; migration DB mới + restore có fixture/data; runtime validate/TLS; không DDL bằng app user |
| B3 — S3 avatar/đề thi | BE1 + FE + Lead | Q04–Q05, bucket/IAM, B2 nếu đổi cột | Upload/read/delete đúng, metadata/checksum đúng, redeploy không mất file, pass test matrix quyền |
| B4 — AI job entity/API/quyền | BE1 + FE/AI | Q03/Q06/Q08, migration B2 | POST trả202; state/result DTO đúng contract; unauthorized/thu hồi assignment bị chặn; giữ ẩn danh |
| B5 — Tích hợp result bền vững | BE1 + BE2 + AI | B4 và event contract | Một job thật từ web tới worker về S3/DB; duplicate/stale version/sai schema không phá state; xử lý offline/quá hạn có bằng chứng |

Đây là 5 work packages để đề xuất cuối tuần; chỉ chốt lịch/người nhận sau khi nhóm review, rồi tách thành PR nhỏ. Queue/infra consumer là phần phối hợp BE2, không ngầm chuyển toàn bộ AWS workload sang cá nhân Backend 1.

## 11. Checklist đóng gói bàn giao

- [x] Có khảo sát source và link bằng chứng.
- [x] Có bảng loại dữ liệu -> nơi lưu -> quyền.
- [x] Có mô tả đề thi/release và giới hạn thu hồi.
- [x] Có schema job tối thiểu và câu hỏi access/version.
- [x] Có hướng migration/backup/restore và security backlog.
- [x] Có 5 task backend tuần 2 với acceptance.
- [x] Có proposal chi tiết, bảng findings và SQL inventory chỉ đọc.
- [x] Đã rút thành bảng đóng góp Backend 1 và phiếu review FE/AI riêng; không tự ghi “đã trao đổi” thay các bên.
- [x] Chạy 195 unit tests liên quan thành công; ghi riêng giới hạn test/runtime.
- [x] Đọc được runtime metadata/core counts/ba unique qua Workbench; ghi giới hạn DB sau reset.
- [ ] Export schema và diff đầy đủ cột/index/FK/delete rules; chốt dữ liệu cần giữ ở các môi trường.
- [ ] Baseline/candidate schema được review và thử trên DB riêng; chưa chạy migration hoặc restore.
- [ ] Lead hoàn tất Calculator estimate toàn hệ thống, không chỉ công thức chi phí dữ liệu.
- [ ] Người dùng review nội dung, cập nhật quyết định Q02–Q08 có liên quan.
- [ ] Frontend/AI xác nhận metadata, DTO và quyền xem kết quả hoặc pending được Lead tiếp nhận rõ.
- [ ] Rút bản đóng góp vào template proposal của nhóm; bản cuối song ngữ nếu template yêu cầu.
- [ ] Lưu phản hồi review, worklog và bằng chứng đóng góp.

## 12. Nguồn AWS và quy định để đối chiếu

- [S3 presigned URL: quyền truy cập theo thời hạn](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html)
- [RDS point-in-time restore tạo DB instance mới](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_PIT.html)
- [S3 Versioning](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Versioning.html)
- [RDS MySQL supported versions](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/MySQL.Concepts.VersionMgmt.html)
- [Proposal chi tiết và nguồn AWS đã đối chiếu](../proposals/data-platform-proposal-detailed.md)
- [Quy định Workshop HCM](https://hcm-rules.awsfcaj.com/3-project/)

## 13. Trạng thái cập nhật ngày 21/10

| Đã làm | Chưa làm / không được suy diễn |
|---|---|
| Đọc source + 687 dòng seal_schema.sql; chạy 195 tests | Không coi SQL cũ đồng bộ entity hoặc tests pass là AWS pass |
| Workbench SELECT xác nhận DB sau reset và drift | Chưa có full schema export; không biết dữ liệu trước reset hoặc DB khác |
| Proposal có hai nhánh migration, baseline gates và restore runbook | Chưa chọn nhánh thay user/Lead; không chạy reset/migration/backup/restore |
| Có bảng lưu trữ/quyền và AI contract dự thảo | FE/AI/Lead chưa sign-off; chi phí chưa chốt Calculator |
