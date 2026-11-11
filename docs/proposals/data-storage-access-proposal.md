# Đóng góp Backend 1 — Phương án dữ liệu và quyền truy cập

Ngày: 21/10/2026 · Phạm vi: phần đóng góp vào proposal chung của nhóm, chưa triển khai AWS.

**Kết quả khảo sát:** backend có 31 entity; 195 unit tests liên quan pass. MySQL local sau lần tạo lại có 31 bảng nhưng còn thiếu MentorSupportRequest và ba cột Notification; cần đối chiếu schema trước migration, không lấy nguyên SQL cũ làm baseline.

**Đề xuất:** RDS MySQL giữ dữ liệu quan hệ và quyền nghiệp vụ; S3 private giữ nội dung file. Người dùng truy cập qua backend có kiểm quyền, không được cấp quyền trực tiếp vào RDS/S3.

| Loại dữ liệu | Nơi lưu đề xuất | Quyền truy cập mục tiêu |
|---|---|---|
| Tài khoản, role, event, team/entry, submission, điểm, lịch sử/audit; repo/demo/slide URL | RDS MySQL | Qua API theo role, chủ sở hữu, event và assignment; dữ liệu công khai chỉ theo DTO/công bố. Không trả password/OTP hash; không sao chép toàn bộ repo vào S3 |
| Avatar upload | S3 private; RDS giữ key/metadata | Chủ tài khoản sửa/xóa; đề xuất giữ đọc công khai qua ứng dụng, chờ FE duyệt. Avatar OAuth giữ URL ngoài, không tự copy/xóa |
| Đề thi theo track | S3 private; Track giữ key, metadata và release | Coordinator đúng event quản lý; coordinator/admin preview theo quyền được duyệt. Participant chỉ tải khi thuộc team APPROVED đúng track và đã release; mentor/judge không tự có quyền tải |
| Metadata AI job | Bảng AiJob mới trong RDS | Backend ghi; requester còn quyền hoặc coordinator đúng event đọc qua API. Worker không kết nối RDS |
| Input snapshot / kết quả AI JSON | S3 private; RDS giữ key/hash/version | Backend ghi input, worker đọc input/ghi result, backend kiểm chứng rồi trả cho người có quyền; không công khai cho participant/mentor |

**Metadata job tối thiểu:** `jobId`, `submissionId`, `requesterUserId`, `repoUrlSnapshot`, `commitSha`, `status` (QUEUED/RUNNING/SUCCEEDED/FAILED/EXPIRED), `createdAt`, `startedAt?`, `finishedAt?`, `resultKey?`, `errorCode?`, `errorMessage?`. Timestamps dùng UTC; bổ sung input/rubric snapshot, hash và model/prompt/schema versions để tái lập.

**Quy tắc AI đề xuất:** chỉ requester còn account/assignment hợp lệ hoặc coordinator đúng event xem; không tự chia sẻ panel. Thu hồi assignment chặn request tiếp theo; hết giờ chỉ xem lịch sử riêng, không tạo job mới. FE nhận status, timestamps và insight đã lọc, không nhận danh tính đội, repo URL, S3 key hoặc lỗi nội bộ. AI không ghi Score.

**Migration/backup:** export/diff schema rồi thử migration trên DB riêng; runtime `DDL_AUTO=validate`. File chuyển theo manifest/checksum trước khi đổi reference; đề thi vẫn kiểm quyền mỗi lượt tải, retract chặn request mới. Backup DB schema+data và file; đề xuất RDS backup 7 ngày/S3 Versioning, restore thử DB mới và đối chiếu file trước cutover. Chưa thực hiện migration/restore.

**5 task backend tuần 2:** (1) bỏ JWT fallback/bootstrap credential cố định; (2) chuẩn hóa schema, migration và restore thử; (3) S3 adapter, guard đề thi và cleanup sau commit; (4) AiJob/API/snapshot/DTO và quyền xem; (5) phối hợp BE2/AI tích hợp queue/result, chống trùng và kiểm version/output.

**Trạng thái nghiệm thu:** đã khảo sát, có bảng quyền và backlog. User xác nhận **chưa trao đổi với Frontend/AI**; metadata/quyền trong bản này dùng để xin review, chưa được nhóm thống nhất. Dùng [phiếu review FE/AI](ai-job-review-handoff.md) để ghi phản hồi trước khi đánh dấu hoàn tất tiêu chí này. Các quyền trong bảng là đề xuất, không phải toàn bộ đã có trong code.

Phụ lục: [proposal chi tiết](data-platform-proposal-detailed.md) · [minh chứng source/runtime/tests](../report/data-storage-backend-audit-2026-10-20.md).
