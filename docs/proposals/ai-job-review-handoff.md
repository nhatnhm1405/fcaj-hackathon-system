# Phiếu review Frontend / AI — metadata và quyền xem job

Ngày: 21/10/2026 · Backend 1 chuẩn bị để trao đổi với nhóm. **User xác nhận chưa trao đổi với Frontend/AI, cần bản để nhóm review.** Đây là dự thảo, chưa gửi/duyệt trong phiên làm việc này; người dùng tự gửi/trao đổi, không ghi phiếu này là biên bản đã họp.

## 1. Nội dung đề nghị xác nhận

| Nội dung | Phương án Backend 1 đề xuất | Bên review |
|---|---|---|
| Metadata tối thiểu | jobId, submissionId, requesterUserId, repoUrlSnapshot, full commitSha, status, createdAt, startedAt?, finishedAt?, resultKey?, errorCode?, errorMessage? | FE + AI |
| State | QUEUED/RUNNING/SUCCEEDED/FAILED/EXPIRED; EXPIRED là timeout xử lý, không phải hết giờ judging hay hết retention result | FE + AI + BE2 |
| Snapshot/version | Input và rubric được chụp tại lúc nhận job; input hash và schema/model/adapter/prompt/rubric versions có thể đối chiếu | AI + BE2 |
| Ghim commit | Backend resolve full SHA trước nhận job cho repo-analysis; AI đọc đúng snapshot/SHA; private repo/text-only cần chốt riêng | AI |
| Người xem | Requester còn account/assignment hợp lệ hoặc coordinator đúng event; không tự chia sẻ panel | FE + AI + Lead |
| Thu hồi assignment | Chặn request đọc/tạo tiếp theo; vẫn giữ audit/result theo retention; judge thay thế không tự nhận quyền đọc job cũ | FE + Lead |
| Đóng/tạm dừng judging | Không tạo job mới; lịch sử riêng chỉ đọc nếu còn quyền. Job đã nhận có thể hoàn tất sau giờ chấm, không mở lại quyền sửa điểm | FE + AI + Lead |
| Coordinator tạo job | Đề xuất v1 cũng yêu cầu judging mở; đây là siết thêm so với nhánh coordinator của AI service hiện tại | Lead + FE |
| FE DTO allowlist | jobId, mã bài ẩn danh, status, timestamps, safe error, resultAvailability và insight đã lọc; giữ insight tương thích khi có thể | FE |
| Không trả ra FE judge | Danh tính đội/thành viên/requester khác, GitHub owner/repo URL, raw S3 key, credentials, stack trace hoặc raw input snapshot | FE + AI |
| Output AI | JSON có provenance job/version/SHA/hash; summary, strengths, concerns, criteriaInsights, repo facts đã làm sạch, disclaimer/model; không ghi Score/RoundResult | AI |
| S3/queue | Worker đọc input/ghi result bằng quyền giới hạn, không có RDS credential; BE2/BE1 kiểm duplicate, sai version và output trước chốt thành công | AI + BE2 |

## 2. API mới để FE mock — chưa tồn tại trong backend

- POST `/api/ai/submissions/{id}/jobs`: 202 sau khi job/snapshot được ghi bền; requester lấy từ phiên, không từ client.
- GET `/api/ai/jobs/{jobId}`: status DTO; kiểm quyền lại ở mỗi request.
- GET `/api/ai/jobs/{jobId}/result`: insight khi sẵn sàng; không redirect public S3; xử lý chưa sẵn sàng/mất quyền/purge theo contract.
- FE hiện dùng `/insights` đồng bộ; cần chốt kế hoạch chuyển UI, trạng thái loading/error/history và polling.
- Nếu giữ suggestedScoreRange dạng chuỗi như DTO hiện tại thì validate theo rubric; đổi cấu trúc điểm cần version hóa contract.

Mẫu JSON và test matrix chi tiết ở [proposal đầy đủ, mục 6–8](data-platform-proposal-detailed.md). Giảm nhận diện trong field không đảm bảo README/code/model output hoàn toàn ẩn danh; cần cả kiểm đầu vào/đầu ra và báo đúng giới hạn.

## 3. Điều kiện đủ để ghi “đã trao đổi với Frontend/AI”

- FE xác nhận enum state, DTO hiển thị, polling/error và bốn tình huống quyền: người khác, revoke, hết giờ, tài khoản không hợp lệ.
- AI xác nhận input/result mẫu, full SHA, versions, error và giới hạn input; phối hợp BE2 chốt event/retry/deadline.
- Lead hoặc người được giao chốt quy tắc nghiệp vụ còn tranh luận; không mặc định có đồng thuận chỉ vì chưa phản hồi.
- Lưu phản hồi hoặc biên bản có người xác nhận, ngày giờ và điểm cần sửa. Đây là sign-off thiết kế; không thay cho nghiệm thu API/worker đã triển khai.

| Bên | Người xác nhận | Ngày / bằng chứng | Trạng thái / sửa đổi |
|---|---|---|---|
| Frontend | Chưa có | Chưa có | Chờ review |
| AI | Chưa có | Chưa có | Chờ review |
| BE2 — messaging | Chưa có | Chưa có | Chờ review |
| Lead — policy | Chưa có | Chưa có | Chờ quyết định |

Sau khi có phản hồi thực tế, cập nhật phiếu và [bản đóng góp ngắn](data-storage-access-proposal.md); không tự điền tên hoặc đổi sang “đã duyệt”.

## 4. Lời nhắn gửi kèm để người thực hiện tự chuyển cho nhóm

“Mình gửi phần đề xuất dữ liệu và AI job của Backend 1. Nhờ Frontend review trạng thái/DTO hiển thị, polling/lỗi và quyền xem khi hết giờ hoặc bị thu hồi assignment. Nhờ AI review input/result JSON, commit SHA, model/rubric/prompt versions và cách báo lỗi. Phương án quyền hiện đề xuất là chỉ requester còn quyền hoặc coordinator đúng event xem, không tự chia sẻ panel; AI không ghi điểm chính thức. Mọi người ghi giúp điểm đồng ý/cần sửa vào phiếu để mình cập nhật proposal. Đây là bản xin review, chưa chốt contract.”
