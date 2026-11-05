# Review thay đổi BE1 chưa commit — 28/10/2026

Phạm vi: tracked diff và source/config/migrations/scripts mới chưa được Git track. Review tập trung đường migration/deploy, quyền, file storage, snapshot và job delivery. Đây là lượt review, chưa sửa production code, chưa commit hay gọi AWS.

Kết luận: có **2 vấn đề P1 và 3 vấn đề P2** cần xử lý. Kết quả591 test ngày27/10 là bằng chứng cho các ca đã chạy, chưa bao phủ các tình huống dưới đây. Không coi checklist code/local đã hoàn thành là xác nhận đủ điều kiện deploy.

## R1 — [P1] DB có thể trỏ lại file đã bị xoá khi hai thao tác xen kẽ

Vị trí: `src/main/java/com/seal/hackathon/storage/AvatarStorage.java:38`, `service/AuthService.java:260`, `service/TrackProblemService.java:270` (đường dẫn source tính từ `back-end/src/seal-api`).

Hai request cùng đọc avatar A. Request đổi avatar ghi B, commit và xoá A. Request cập nhật tên hồ sơ vẫn giữ entity cũ và flush toàn bộ User, ghi avatar A trở lại. User/Track chưa có optimistic version hoặc cơ chế khóa bảo vệ mọi writer. Tương tự, request phát đề đọc key A có thể ghi lại A sau khi request thay đề đã commit B và xoá A. Lúc này DB vẫn có reference nhưng tải file không được.

Đã tái hiện bằng Hibernate thật với hai transaction xen kẽ trên H2 bộ nhớ và local objects giả: `AVATAR_DB_REVERTED_TO_OLD=true`, `AVATAR_REFERENCED_OBJECT_MISSING=true`, `PROBLEM_DB_REVERTED_TO_OLD=true`, `PROBLEM_REFERENCED_OBJECT_MISSING=true`.

Hướng sửa: bảo vệ concurrent updates của User/Track trước khi dọn file, ví dụ optimistic version có migration và xử lý conflict, hoặc cập nhật cột có điều kiện/khóa nhất quán trên các writer liên quan. Chỉ thêm lock ở riêng upload không ngăn được một writer khác đã đọc entity cũ ghi ngược sau đó. Thêm regression test đổi avatar cùng sửa profile, thay đề cùng release/retract.

## R2 — [P1] Chưa có bước chuyển DB cũ trước đường auto-deploy đang tồn tại

Vị trí: `src/main/resources/application-local.properties:9`, `.github/workflows/deploy.yml:119`; context `docker-compose.yml` dùng MySQL8.0/volume `db_data` và không chọn profile AWS.

Bản cũ để Hibernate tạo schema, chưa có Flyway history. Bản mới bật Flyway, tắt automatic baseline. Workflow deploy vẫn thay container bằng `docker compose up ...` mà không kiểm/migrate/baseline DB trước. Nếu deploy vào volume hiện hữu từ bản cũ, Flyway sẽ từ chối non-empty schema và backend mới không khởi động. Các test DB mới hoặc restore trong lab không kiểm đường upgrade của compose đang tồn tại. Runner baseline hiện cũng chỉ nhận MySQL8.4/case1, khác cấu hình MySQL8.0 mặc định của compose.

Hướng sửa: chốt target/runtime cùng BE2 và thêm pre-deploy gate xác nhận DB đã chuyển/baseline/migrate đúng, trước khi thay backend đang chạy. Đường giữ dữ liệu cần backup/rehearsal/cutover được kiểm. Không chữa bằng bật baseline-on-migrate hoặc xóa volume. Không khẳng định DB production hiện đang ở trạng thái nào vì lượt này không truy cập máy deploy.

## R3 — [P2] Coordinator trong dữ liệu demo không đáp ứng event-scope mới

Vị trí: `service/TrackProblemService.java:259`, `aijob/JobAccess.java:25`; context `config/seed/DemoScenario.java:124`.

Guard mới yêu cầu coordinator có grant đúng event; grant eventId=null không đủ. DemoScenario vẫn chỉ tạo coordinator với grant null, rồi tạo event mà không thêm grant theo event. Vì vậy sau khi seed S1/S25, coordinator demo bị403 khi quản lý đề thi; ở round có judging mở vẫn bị từ chối tạo job. Tests hiện dùng coordinator được tạo riêng với grant đúng event nên không bắt được lỗi demo này.

Hướng sửa: khi tạo demo event, thêm grant coordinator cho chính event đó; kiểm API bằng account thực từ seeder. Dữ liệu cũ cần review/cấp event grant bằng quy trình quản trị. Giữ guard theo policy đã chốt, không mở lại quyền global để làm demo chạy.

## R4 — [P2] Backend nhận rubric lớn hơn worker có thể xử lý

Vị trí: `aijob/JobSnapshotWriter.java:29`, `aijob/AwsMockWorkerMain.java:66`.

Snapshot writer không giới hạn bytes rubric trước khi lưu/ghi job; reader chỉ giới hạn số criteria<=50. CreateCriteriaRequest không có giới hạn description nhỏ hơn TEXT. Worker lại từ chối rubric>262144 bytes. Với năm criteria, mỗi description60000 ký tự ASCII (vẫn nằm trong TEXT), producer đã tạo được rubric300485 bytes nhưng worker trả `INVALID_INPUT_REFERENCE`, không gửi completion. Sau khi job đã commit/dispatch, trạng thái không được hoàn tất và message có thể chuyển DLQ.

Đã tái hiện producer→mock worker bằng JSON và storage/queue giả: `PRODUCER_ACCEPTED_RUBRIC_BYTES=300485`, `WORKER_REJECTED_VALID_DB_INPUT=INVALID_INPUT_REFERENCE`, `COMPLETIONS_SENT=0`.

Hướng sửa: dùng chung giới hạn bytes giữa producer/worker và kiểm trước upload/commit job; trả mã lỗi rõ cho API, hoặc cùng AI/BE2 chốt nâng giới hạn có kiểm soát. Thêm test ngay dưới/trên ngưỡng với dữ liệu UTF-8.

## R5 — [P2] Timeout GitHub chưa bao phủ lúc đọc response body trên Java21

Vị trí: `aijob/GitHubSourceReader.java:38`.

`HttpRequest.timeout(15s)` được kết hợp `BodyHandlers.ofInputStream()`, sau đó gọi `readNBytes` đồng bộ. Trên Java21, request timeout không giới hạn toàn bộ quá trình tiêu thụ body. Nếu đã nhận headers nhưng body ngừng truyền, request tạo job có thể giữ thread rất lâu; deadline45s chỉ được kiểm giữa các lần fetch nên không ngắt được lần read đang kẹt.

Hướng sửa: timeout/cancel thực sự bao phủ body và budget toàn snapshot, đồng thời giữ giới hạn bytes; đóng/hủy stream khi quá hạn. Test một response trả headers ngay nhưng không hoàn tất body. Không đề xuất nâng JDK chỉ để né lỗi này trong phạm vi review.

Đối chiếu [giải thích của đội Java tại Oracle](https://inside.java/2026/03/04/jdk-26-http-client/#timeout-update): phạm vi timeout nhận body được mở rộng ở Java26; dự án hiện dùng Java21.

## Kiểm tra trong lượt review

- Hai probe tái hiện nằm dưới `back-end/src/seal-api/target/review/` bị Git ignore; chỉ dùng dữ liệu giả và DB bộ nhớ. Không thay source/test chính thức.
- `concurrency-probe.log`: chứng minh cả hai reference bị ghi ngược về object đã xóa.
- `job-boundary-probe.log`: chứng minh mismatch300485/262144 bytes.
- `git diff --check` đạt; không chạy lại toàn bộ591 test trong lượt review. Chưa kiểm AWS hoặc DB hiện hữu.
- Không có knowledge graph `.understand-anything/knowledge-graph.json`; review theo source và luồng gọi trực tiếp.

Ưu tiên xử lý: R1 và R2 trước khi duyệt deploy; R3 để demo bằng dữ liệu seed hoạt động; R4/R5 trước buổi ghép worker thật. Khi sửa, thêm regression tests rồi kiểm lại artifact/config bàn giao.
