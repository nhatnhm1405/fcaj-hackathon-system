# Sổ Tay Kỹ Thuật: Các Ràng Buộc & Nguyên Tắc Bất Di Bất Dịch Của BE1

**Tài liệu tham chiếu:** `backend1-ai-engineering-discussion-handoff-plan-2026-10-28.md`  
**Mục đích:** Lưu trữ toàn bộ các quy tắc bất biến, giới hạn hệ thống và ranh giới kiến trúc do BE1 thiết lập để toàn bộ code của AI Worker (`ai-worker/`) **tuyệt đối không được vi phạm**.

---

## 1. Ranh Giới Dữ Liệu & Snapshot (Data Boundary)

1. **Chỉ dùng Snapshot bất biến của BE:**
   - BE1 là bên duy nhất chịu trách nhiệm resolve GitHub commit SHA và lưu snapshot (source, rubric, manifest) bất biến lên S3.
   - **CẤM:** Worker tuyệt đối **không tự ý gọi GitHub API để kéo code mới (HEAD)**, không tự đọc URL bên ngoài, không cào các link trong README.
2. **Xử lý khi thiếu dữ liệu:**
   - Nếu tiêu chí rubric đòi hỏi đánh giá UX, slide thuyết trình, hoặc video mà trong source snapshot không có:
   - **BẮT BUỘC:** AI phải ghi rõ `"THIEU_DU_LIEU"` / Nêu rõ giới hạn không đủ thông tin để chấm. **Tuyệt đối không bịa đặt (hallucinate)** nhận xét như thể đã xem demo.
3. **Chính sách vượt Context:**
   - Kích thước source code tối đa BE cho phép là 8MB (collector tối đa 40 files, 100KB/file, 1MB text).
   - Nếu bài thi vượt quá token budget: Worker phải **báo lỗi an toàn (Safe Failure)** kèm **Coverage Report** (báo rõ đã quét bao nhiêu file/dòng). **CẤM âm thầm cắt xén code** rồi tuyên bố đã đọc toàn bộ.

---

## 2. Ranh Giới Nghiệp Vụ & Quyền Hạn Điểm Số (Business Boundary)

1. **AI không can thiệp điểm số:**
   - AI chỉ đóng vai trò **Trợ lý Kỹ thuật (Technical Advisor)** cho Giám khảo.
   - Mốc đầu giữ `suggestedScoreRange = null`.
   - **CẤM:** AI tuyệt đối **không được ghi điểm hay cập nhật các bảng `Score` hoặc `RoundResult`** trong Database.
2. **Bảo vệ tính ẩn danh (Blind Review):**
   - Loại bỏ toàn bộ thông tin định danh tác giả (tên, email, MSSV) trước khi đưa vào context chấm điểm nếu có yêu cầu.

---

## 3. Ràng Buộc Kỹ Thuật Về Dẫn Chứng (Evidence Constraints)

1. **Định vị chính xác 100%:**
   - Mọi `evidence` phải trỏ vào `file_path` có thật trong snapshot.
   - `line_start` và `line_end` phải là số dòng hợp lệ trong file gốc.
   - **CẤM:** Không được thay đổi line endings hoặc đánh lại số dòng làm lệch ánh xạ với file gốc của BE.
2. **Số lượng evidence:**
   - Tối đa **20 evidence / một tiêu chí (criterion)**.
3. **Safe Text Validation:**
   - Backend sẽ validate và **từ chối (reject)** các chuỗi text chứa URL, email, hoặc ký tự NULL (`\0`) trong phần feedback để phòng chống Prompt Injection hoặc rò rỉ dữ liệu.

---

## 4. Giới Hạn Hạ Tầng & Kích Thước (System Limits)

| Thành phần | Giới hạn tối đa | Lưu ý kỹ thuật |
| :--- | :--- | :--- |
| **SQS Message Body** | **32,768 bytes (32 KB)** | Không nhồi toàn bộ code vào message SQS; SQS chỉ chứa request refs / S3 pointers. |
| **Artifact (Manifest / Rubric / Output)** | **262,144 bytes (256 KB)** | Kết quả output JSON lưu trên S3 không được vượt quá 256 KB. |
| **Source JSON Snapshot** | **8,000,000 bytes (8 MB)** | Dung lượng tối đa snapshot source code từ BE. |
| **Collector Limits** | **40 files, 100KB/file, 1MB text** | Giới hạn thu thập file của BE. |
| **Rubric Criteria** | **1 đến 50 tiêu chí** | AI phải trả đủ đúng số lượng tiêu chí, không thừa không thiếu. |
| **Độ dài Identifier** | **Tối đa 100 ký tự** | `modelId`, `modelRevision`, `adapterVersion` chỉ được chứa `[a-zA-Z0-9_.-]`. **CẤM dùng dấu gạch chéo `/`**. |

---

## 5. Quy Chuẩn Idempotency & Xử Lý Trùng Lặp (Duplicate Handling)

1. **Khóa xử lý (Processing Key):**
   - Khóa duy nhất xác định một lượt chấm: `jobId + inputRevision + modelConfigVersion`.
2. **Durable Terminal Decision:**
   - Khi đã sinh kết quả và lưu thành công lên S3, kết quả đó là **BẤT BIẾN (Immutable)**.
   - Nếu tin nhắn SQS bị gửi lại (Redelivery / Timeout): Worker phải kiểm tra artifact S3 đã có chưa; nếu đã có thì **gửi lại đúng completion message cũ**, **CẤM gọi model inference lại lần 2** làm sai lệch kết quả hoặc tốn token.
3. **Thứ tự Ack Message bắt buộc:**
   - **Phía Worker:** Ghi output lên S3 -> Gửi completion message vào Results SQS -> Sau khi gửi thành công mới được ACK/Xóa message khỏi Jobs SQS.
   - **Phía Backend:** Đọc Results SQS -> Kiểm tra identity/hash -> Commit DB thành công -> Mới được ACK/Xóa message khỏi Results SQS.

---

## 6. SQS Visibility Timeout & Quản Lý Thời Gian

1. **Visibility Timeout:**
   - Queue SQS hiện override visibility là **120 giây**, long polling là **20 giây**.
   - Nếu thời gian gọi AI inference vượt quá 120 giây: Worker **bắt buộc phải gọi API `ChangeMessageVisibility`** để gia hạn (renew visibility), tránh việc SQS đẩy message cho worker khác xử lý trùng.
2. **Inference Deadline:**
   - Thiết lập timeout tối đa cho 1 lượt gọi AI là **60 giây** (Cloud AI thực tế chỉ mất 5s – 15s).

---

## 7. Bảo Mật & Phân Quyền IAM (Least Privilege)

1. **Quyền của AI Worker:**
   - **S3:** Chỉ cần quyền `GetObject` (đọc snapshot input) và `PutObject` (ghi kết quả output).
   - **SQS:** Chỉ cần `ReceiveMessage`, `DeleteMessage`, `ChangeMessageVisibility` trên input queue; và `SendMessage` trên results queue.
   - **CẤM:** **Tuyệt đối không cấp quyền truy cập Database (JDBC/MySQL) hay cấp JWT token cho AI Worker.**
2. **Logging an toàn:**
   - Log theo `jobId`, `attempt`, `stage`, `elapsed_time`.
   - **CẤM:** Tuyệt đối không in raw credentials, API keys, raw source code, hay raw prompt vào log hệ thống.

---

## 8. Versioning & Chuyển Đổi Contract

1. **Không can thiệp Contract MOCK cũ:**
   - Validator của BE hiện tại chỉ nhận mode `MOCK` và mã lỗi `MOCK_PROCESSING_FAILED`.
   - **CẤM:** Không được tự ý đổi tên model trong queue MOCK đang chạy vì BE sẽ reject ngay lập tức.
2. **Version mới cho Model thật:**
   - Phải cùng BE1 thống nhất contract mới (ví dụ: `ai-job-v2`) và triển khai đồng bộ ở cả 2 phía trước khi test SQS thật.
