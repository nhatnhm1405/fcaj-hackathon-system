# Đóng góp Backend 1 — Dữ liệu, phân quyền và AI job

Ngày: 21/10/2026 · Dự án: SEAL/FCAJ Hackathon System · Trạng thái: đề xuất để team review.

## 1. Nội dung đóng góp vào proposal

Theo khảo sát tại `main@f42a168`, backend Spring Boot/JPA có 31 entity; 195 unit tests liên quan đã pass. MySQL local có 31 bảng nhưng thiếu `MentorSupportRequest` và ba cột của `Notification`, đồng thời còn bảng legacy `AccountApproval`. Vì vậy, cần đối chiếu schema đầy đủ trước khi chuyển dữ liệu. Đề xuất dùng **RDS MySQL lưu dữ liệu quan hệ và trạng thái nghiệp vụ; S3 private lưu nội dung file**. Backend kiểm quyền người dùng ở mỗi API; IAM cấp quyền AWS cho backend/worker.[1]

| Dữ liệu | Nơi lưu đề xuất | Quyền đọc | Quyền ghi / quản lý |
|---|---|---|---|
| Tài khoản, role, event, team/entry, submission, điểm, lịch sử/audit; repo/demo/slide URL | RDS MySQL | Qua API theo chủ sở hữu, role, event và assignment; dữ liệu công khai chỉ qua trường được phép/công bố | User, leader, judge, coordinator hoặc admin theo chức năng được cấp; audit do backend ghi. Không trả password/OTP hash |
| Avatar upload | S3 private; RDS giữ key và metadata | Đề xuất giữ đọc công khai qua ứng dụng, chờ FE/Lead xác nhận | Chủ tài khoản upload/xóa. Avatar OAuth giữ URL ngoài, không tự copy hoặc xóa |
| Đề thi theo track | S3 private; `Track` giữ key, metadata và trạng thái release | Participant thuộc team `APPROVED` đúng track và đề đã release; staff preview theo quyền được duyệt. Role mentor/judge không tự cấp quyền tải | Coordinator đúng event quản lý; backend kiểm quyền mỗi lần tải, retract chặn lượt tải mới.[2] |
| Metadata AI job | Bảng `AiJob` mới trong RDS | Requester còn quyền hoặc coordinator đúng event, qua API | Backend quản lý trạng thái; worker không truy cập RDS |
| Input snapshot / kết quả AI JSON | S3 private; RDS giữ key, hash và version | Backend/worker đọc input; người có quyền xem kết quả đã lọc qua backend. Không cấp mặc định cho participant/mentor | Backend ghi input; worker ghi result; backend kiểm chứng trước khi xác nhận thành công |

**Metadata job tối thiểu:** `jobId`, `submissionId`, `requesterUserId`, `repoUrlSnapshot`, full `commitSha`, `status`, `createdAt`, `startedAt?`, `finishedAt?`, `resultKey?`, `errorCode?`, `errorMessage?`. Trạng thái gồm `QUEUED / RUNNING / SUCCEEDED / FAILED / EXPIRED`; thời gian dùng UTC. Bổ sung snapshot input/rubric, hash và schema/model/prompt/rubric versions để xác định đúng phiên bản được phân tích.[3]

**Quy tắc xem kết quả đề xuất:** chỉ requester còn tài khoản/assignment hợp lệ hoặc coordinator đúng event được xem; judge khác trong cùng nhóm chấm không tự có quyền. Thu hồi assignment chặn request tiếp theo. Khi đóng/tạm dừng chấm, chỉ đọc lịch sử riêng nếu còn quyền, không tạo job mới; job đã nhận có thể hoàn tất nhưng không mở lại quyền sửa điểm. FE nhận trạng thái, thời gian, lỗi an toàn và insight đã lọc; không nhận danh tính đội, repo URL hoặc S3 key. AI không ghi `Score`/`RoundResult`.[4]

## 2. Danh sách thay đổi cần thực hiện trong backend

| Task | Thay đổi và kết quả cần đạt |
|---|---|
| B1 — Cấu hình và quyền | Bỏ JWT secret fallback và bootstrap credential cố định; bootstrap có opt-in; kiểm scope event ở nhánh coordinator của đề thi/AI |
| B2 — Schema, migration và phục hồi | Export/diff schema, xử lý sai lệch đã khảo sát và review bảng legacy; thêm Flyway, runtime `DDL_AUTO=validate`; thử migration và restore trên DB riêng, đối chiếu dữ liệu/file |
| B3 — Lưu file trên S3 | Thêm storage adapter LOCAL/S3 và metadata; chuyển file theo manifest/checksum; giữ guard team/track/release; dùng key mới khi thay file và dọn file cũ sau commit |
| B4 — AI job và API | Thêm `AiJob`, snapshot/SHA/version, API tạo–đọc trạng thái–đọc kết quả, DTO lọc trường và kiểm quyền mỗi request; review quyền xem điểm của judge để nhất quán với policy chấm độc lập |
| B5 — Tích hợp BE2/AI | Phối hợp dispatch/outbox và SQS jobs/results/DLQ; xử lý message trùng, sai thứ tự, retry/timeout; kiểm hash/SHA/version/output trước khi chốt thành công |

## 3. Bàn giao / nghiệm thu

| Tiêu chí | Trạng thái và điều kiện hoàn tất |
|---|---|
| Bảng đóng góp vào proposal | Đã soạn tại mục 1 |
| Bảng lưu trữ/phân quyền rõ ràng | Đã soạn policy mục tiêu; cần review và kiểm thử khi triển khai |
| Metadata job và quy tắc xem kết quả đã trao đổi với Frontend/AI | **Chưa hoàn tất theo hồ sơ hiện có.** FE cần xác nhận state/DTO/polling/lỗi và revoke/hết giờ; AI cần xác nhận input/result, SHA/version và lỗi. Ghi người xác nhận, ngày, phản hồi và các điểm còn mở trong [phiếu review FE/AI](ai-job-review-handoff.md) |
| Danh sách thay đổi backend | Đã xác định B1–B5 tại mục 2; chưa triển khai trong đợt khảo sát |

## 4. Chú thích giải thích

- **[1] Schema và quyền:** số lượng bảng bằng số entity không chứng minh cấu trúc khớp nhau. Ba cột thiếu là `sender_user_id`, `sender_role`, `scope_label`. IAM kiểm quyền thành phần ứng dụng trên AWS; backend vẫn phải kiểm user được phép truy cập dữ liệu nào. 195 tests là unit tests hiện có, chưa chứng minh tích hợp RDS/S3/SQS.
- **[2] Release/retract:** release mở quyền tải đề; retract chặn request mới sau khi cập nhật có hiệu lực, không thu hồi bản đã tải. Đề xuất stream file qua backend để kiểm quyền mỗi lượt; không cấp presigned URL cho đề thi/result ở v1.
- **[3] Metadata/snapshot:** metadata mô tả job; snapshot chụp input và rubric khi nhận job. Full commit SHA ghim phiên bản code; hash đối chiếu nội dung; version ghi cấu hình phân tích. Dấu `?` là field có thể chưa có giá trị. `EXPIRED` nghĩa là quá hạn xử lý, khác hết giờ chấm hoặc hết thời hạn lưu result.
- **[4] Contract chờ review:** requester là người yêu cầu job, assignment là phân công chấm. Quyền được kiểm lại khi đọc, không tồn tại vĩnh viễn chỉ vì đã tạo job. Áp dụng điều kiện judging mở cho cả coordinator tạo job là đề xuất mới cần Lead duyệt. Lọc DTO chỉ hỗ trợ ẩn danh ở mức ứng dụng; README/code/output AI vẫn cần kiểm thông tin nhận diện.

**Giới hạn bàn giao:** AWS, migration, backup/restore drill và FE/AI sign-off chưa được nghiệm thu theo tài liệu khảo sát. Trước migration cần xác nhận dữ liệu phải giữ, backup cả DB và file, thử phục hồi trên DB riêng; không dùng script reset demo làm quy trình phục hồi.

Nguồn: [Báo cáo khảo sát và minh chứng](backend1-team-proposal-report.md) · [Thiết kế chi tiết và ma trận kiểm thử](data-platform-proposal-detailed.md).
