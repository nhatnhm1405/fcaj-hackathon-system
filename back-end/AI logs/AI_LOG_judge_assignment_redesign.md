# AI Log — Thiết kế lại cơ chế phân công Judge/Mentor (SEAL Hackathon)

**Dự án:** SEAL – Software Engineering Hackathon Management System
**Phạm vi phiên làm việc:** Phân tích nghiệp vụ Judge → phát hiện lỗi schema → thiết kế lại bảng phân công → sửa schema DDL và seed data.
**Công cụ hỗ trợ:** Claude (AI assistant)
**Kết quả:** 2 file SQL hoàn chỉnh (`seal_schema.sql`, `seal_seed.sql`).

---

## 1. Bối cảnh & câu hỏi khởi đầu

Bắt đầu từ việc phân tích `ProjectRequirements.md` để làm rõ **Judge nên được quản lý như thế nào**. Quá trình thảo luận đi qua các câu hỏi nghiệp vụ theo thứ tự:

1. Judge quản lý ra sao (Internal vs Guest, phân công, flow chấm điểm)?
2. Judge chấm theo đơn vị nào — Submission, Team, Round hay Track?
3. Phân công Judge theo `round_id + track_id` để làm gì?
4. Cái "ô" được phân công đó đặt tên là gì?
5. Trong một ô có nhiều submission thì chấm hết hay chia ra?
6. MentorAssignment có nên gộp chung với JudgeAssignment không?
7. Có hardcode số round không?

---

## 2. Các quyết định nghiệp vụ đã chốt

### 2.1. Đơn vị chấm điểm
- **Judge chấm theo `Submission`** — vì đó là thứ thực sự nhìn vào (repo, demo, slide).
- Submission luôn gắn context: `Team → Track`, `Submission → Round`.
- `Score` lưu FK về `submission_id + judge_id + criteria_id`; Round/Track derive ngược từ submission, không lưu trực tiếp.

### 2.2. Phân công Judge = access control
- Phân công theo cặp `(round_id, track_id)` xác định Judge **được phép thấy và chấm** submission nào.
- Mục đích: filter danh sách bài cần chấm + validate quyền khi submit điểm + giới hạn quyền Guest Judge.

### 2.3. Đặt tên & phạm vi
- Bảng phân công Judge đặt tên `JudgeAssignment`.
- MVP: Judge chấm **toàn bộ** submission trong ô được phân công (không chia submission cụ thể cho từng judge). Có thể mở rộng sau bằng bảng `JudgeSubmissionAssignment` nếu cần.

### 2.4. Vòng loại vs Chung kết (theo lời giảng viên)
- **Vòng loại:** chấm chia theo track → `track_id` có giá trị.
- **Chung kết:** chấm chung tất cả → `track_id = NULL`.
- Phân biệt bằng cột mới `Round.is_final` (BOOLEAN), **không hardcode** số round vì requirements nói "mỗi sự kiện có thể có nhiều vòng".

### 2.5. Tách Mentor khỏi Judge
- **Không gộp** `MentorAssignment` với `JudgeAssignment` — bản chất khác nhau:
  - Mentor: phân công theo `track` (cả event), hỗ trợ team xuyên suốt.
  - Judge: phân công theo `round + track`, liên quan scoring.

---

## 3. Lỗi đã phát hiện trong schema gốc

### 3.1. Bảng `TeamAssignment` sai thiết kế
- Gộp Mentor + Judge vào 1 bảng, dùng `team_id` làm gốc.
- Sai đơn vị: Mentor gắn theo track (không phải team lẻ); Judge chấm theo round/track (không "thuộc" team nào).
- Trùng chức năng với `UserEventRole` → 2 nguồn sự thật, dễ lệch dữ liệu.
- Thiếu `track_id`.

### 3.2. Bảng `UserEventRole` ôm 2 trách nhiệm
- Trộn lẫn **định danh role** (`user_id, role_id, event_id`) với **phân công cụ thể** (`track_id, round_id, judge_type`).
- Gây nhiều cột nullable, DB không ép được tính đúng đắn.

### 3.3. Lỗ hổng nhất quán trong seed cũ
- Seed cũ chỉ phân công Thầy An (user 2) + Guest (user 4) chấm AI ở **Preliminary**.
- Nhưng phần `Score` lại có họ chấm cả **Semi-final** và **Final**.
- → Tồn tại điểm số **không có phân công tương ứng** = sai logic nghiệp vụ.

---

## 4. Giải pháp đã áp dụng

### 4.1. Kiến trúc 2 tầng cho role & assignment

```
UserEventRole    = "User được PHÉP làm role gì trong event nào"  (authorization, N-N)
        ↓
JudgeAssignment  = "Judge PHẢI chấm round/track nào"             (work assignment)
MentorAssignment = "Mentor hỗ trợ track nào"                     (work assignment)
```

### 4.2. Thay đổi schema (`seal_schema.sql`)

| Hành động | Chi tiết |
|---|---|
| **Bỏ** `TeamAssignment` | Trùng lặp + sai đơn vị nghiệp vụ |
| **Gọn** `UserEventRole` | Còn `(user_id, role_id, event_id, assigned_by)` + `UNIQUE(user_id, role_id, event_id)` |
| **Thêm** `JudgeAssignment` | `(judge_user_id, round_id, track_id NULL, judge_type, assigned_by, is_active)` |
| **Thêm** `MentorAssignment` | `(mentor_user_id, track_id, assigned_by, is_active)` |
| **Thêm cột** `Round.is_final` | Phân biệt chung kết (chấm chung) vs vòng loại (chấm theo track) |

Đã verify: thứ tự FK hợp lệ, mọi bảng được tham chiếu đều tạo trước.

### 4.3. Thay đổi seed data (`seal_seed.sql`)

- Tách mục 6 cũ thành 3 block: `UserEventRole` (mục 6) + `JudgeAssignment` (6b) + `MentorAssignment` (6c).
- Giữ đúng case N-N: user 2 (Thầy An) có 2 dòng role ở event 1 — vừa MENTOR vừa JUDGE.
- **Vá lỗ hổng:** bổ sung đủ JudgeAssignment cho các round mà Score có ghi nhận (Semi/Final của Thầy An + Guest).
- Thêm verify cho 2 bảng mới ở cuối file.
- Kiểm chứng bằng script: **11/11 cặp (judge, round) trong Score đều có JudgeAssignment tương ứng** → nhất quán 100%.

---

## 5. Việc còn lại cần xử lý ở service layer (Java)

DB không tự ép được các ràng buộc sau, cần validate trong code:

1. `round.is_final = FALSE` → `track_id` bắt buộc có giá trị.
2. `round.is_final = TRUE` → `track_id` phải NULL.
3. **Conflict of interest:** 1 user không nên vừa là Judge vừa là Mentor cho cùng 1 track (mentor mình thì không nên chấm).
4. **Score visibility (cho RBL):** Judge không thấy điểm của Judge khác khi đang chấm, tránh anchoring bias.

---

## 6. Lưu ý vận hành

1. Thứ tự chạy: `seal_schema.sql` → `seal_seed.sql`.
2. Schema có `DROP DATABASE` đầu file → wipe toàn bộ dữ liệu. Production nên dùng migration tool (Flyway/Liquibase).
3. Seed hiện chưa set `is_final = TRUE` cho round nào. Final (round 3) đang giữ `track_id` trong JudgeAssignment để khớp Score cũ (chấm theo track, mỗi track 1 champion). Nếu muốn test luồng "chung kết chấm chung", set `Round.is_final = TRUE` và để `track_id = NULL`.

---

## 7. Sản phẩm bàn giao

- `seal_schema.sql` — DDL hoàn chỉnh, 17 bảng, đã sửa toàn bộ vấn đề phân công.
- `seal_seed.sql` — seed data đã đồng bộ với schema mới, đã vá lỗ hổng nhất quán.
- `AI_LOG_judge_assignment_redesign.md` — tài liệu này.
