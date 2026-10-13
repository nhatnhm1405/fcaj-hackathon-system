# AI Log — SEAL: Tách role System Administrator & đồng bộ Schema/Seed/API

**Dự án:** SEAL – Software Engineering Hackathon Management System (SU26SWP04)
**Phạm vi phiên:** Thiết kế tách role `SYSTEM_ADMIN` khỏi `EVENT_COORDINATOR`, chia việc FE/BE, sửa schema MySQL, sửa seed data, sửa Postman collection.
**Công cụ:** Claude (Opus 4.8) + MariaDB 10.11 để test thật.

---

## 1. Mục tiêu phiên

Ban đầu hệ thống gộp chung Coordinator và Admin thành một role. Phiên này nhằm:

1. Xác định ranh giới nhiệm vụ giữa **System Administrator** (vận hành nền tảng) và **Event Coordinator** (vận hành cuộc thi).
2. Chia công việc triển khai cho team (FE/BE).
3. Sửa schema MySQL để thêm role mới.
4. Rà soát từng attribute từng bảng để loại bỏ field thừa / sai thiết kế.
5. Đồng bộ seed data với schema mới + áp ràng buộc "mỗi mùa 1 event".
6. Đồng bộ Postman collection.

---

## 2. Phân chia Admin vs Coordinator

**Nguyên tắc cốt lõi:**
> Nếu một nhiệm vụ vẫn đúng kể cả khi không có cuộc thi nào diễn ra → **Admin**.
> Nếu nhiệm vụ chỉ có nghĩa khi đang có event → **Coordinator**.
>
> *Coordinator điều hành cuộc thi, Admin điều hành hệ thống.*

| Nhiệm vụ | Admin | Coordinator |
|---|---|---|
| Tạo/khóa/xóa tài khoản hệ thống | ✅ | ❌ |
| Duyệt account participant để vào thi | ❌ | ✅ |
| Gán role COORDINATOR cho người khác | ✅ | ❌ |
| Gán role JUDGE/MENTOR | ❌ | ✅ |
| Tạo account guest judge | ✅ (tạo account) | ✅ (assign vào round) |
| Scoring criteria template (global) | ✅ | ❌ |
| Scoring criteria của event | ❌ | ✅ |
| Xem audit log toàn hệ thống (System Log) | ✅ | ❌ |
| Xem audit log của event mình | ❌ | ✅ |
| Tạo/quản lý event, round, track | ❌ | ✅ |
| Cấu hình OAuth/system settings | ✅ | ❌ |

**Quyết định vùng xám:**
- Guest judge: **Admin tạo account, Coordinator assign** vào round.
- Bootstrap: Admin là role seed cứng đầu tiên (không ai gán được), rồi Admin gán Coordinator.

---

## 3. Chia việc FE / BE

**Thứ tự dependency:** Schema → BE (entity/security/API) → FE (màn hình + routing). Schema phải xong trước.

### Backend
- Schema: thêm `SYSTEM_ADMIN`, `UserEventRole.event_id` nhận NULL, seed bootstrap admin.
- Security: tách path `/api/admin/**` (chỉ SYSTEM_ADMIN) vs `/api/coordinator/**`. JWT trả role kể cả system-wide (event_id NULL).
- API Admin-only: CRUD user toàn cục, grant/revoke COORDINATOR, criteria template global, system logs.
- `SystemLog` tách biệt `AuditLog`.

### Frontend
- AuthContext + type: thêm `SYSTEM_ADMIN`, nhận diện từ `UserEventRole.event_id === null`.
- Routing `/admin/**` bọc bởi RoleGate.
- Màn hình mới: Admin Dashboard, User Management, Role Assignment, Criteria Template, **System Log**.
- Tách chức năng quản lý user toàn cục ra khỏi Coordinator dashboard.
- Đổi tên "Global Audit Log" → **"System Log"**.

### Calibration round + AI (thảo luận RBL)
Hướng ứng dụng AI nếu làm RBL sau: phát hiện giám khảo outlier (thống kê ICC/z-score), LLM-as-a-judge làm mốc tham chiếu, AI diễn giải phương sai điểm, gợi ý điểm đồng thuận. Khả thi nhất cho đồ án: thống kê làm core, LLM-as-judge làm điểm cộng.

---

## 4. Thay đổi Schema (`seal_schema.sql`)

### Thêm mới
- **Role**: `SYSTEM_ADMIN` làm `role_id=1`, đẩy COORDINATOR/MENTOR/JUDGE xuống 2/3/4.
- **SystemLog** (bảng mới, tối giản): `actor_user_id, action, detail, ip_address, created_at`. Tách biệt AuditLog.
- **User.judge_type**: `INTERNAL`/`GUEST`/NULL (chuyển từ JudgeAssignment lên User).
- **Index** `idx_uer_role_systemwide (role_id, event_id)` cho query role system-wide.

### Đã bỏ
- `HackathonEvent.created_by` + FK (tra AuditLog action=CREATE_EVENT nếu cần).
- `Round.is_calibration` (để dành cho RBL sau).
- `UserEventRole.assigned_by` + `assigned_at` (tra SystemLog).
- `JudgeAssignment.judge_type` (→ User), `assigned_by`, `assigned_at` (tra AuditLog).
- `MentorAssignment.assigned_by` + `assigned_at`.
- `RoundResult.advanced` (suy từ `rank_position <= Round.top_n_advance`, group theo track).
- `Notification.related_event_id` + FK.

### Đã sửa
- `User.expired_at`: bỏ `ON UPDATE CURRENT_TIMESTAMP` → DATETIME thường (đúng nghĩa soft-expire, set tay).

### Giữ nguyên (đã xác nhận không trùng)
- `is_approved` (đã duyệt vào thi) vs `is_active` (account còn hoạt động).
- `AccountApproval` (sổ ghi chép duyệt: ai duyệt/lý do/thời gian) vs `User.is_approved` (trạng thái bit hiện tại).
- `Score.is_draft` (lưu nháp điểm trước submit).
- `RoundResult.total_score` (denormalize cho nhanh).
- 3 URL Submission riêng (repo/demo/slide).
- `ScoringCriteria` 3 tầng FK: `template_id` (bộ mẫu global) → `event_id` (kế thừa, copy thành dòng riêng để sửa độc lập) → `round_id` (override riêng từng vòng). Resolve ưu tiên round-specific trước, fallback event-level.

### Bootstrap
- Bỏ seed account trong schema (chỉ giữ seed Role) → seed file quản toàn bộ user, một nguồn duy nhất, user_id ổn định.

---

## 5. Thay đổi Seed (`seal_seed.sql`)

### Ràng buộc "mỗi mùa 1 event"
- Mỗi `(season, year)` có đúng 1 event, không chồng thời gian. 1 năm tối đa 3 event: Spring/Summer/Fall, 1 địa điểm duy nhất.
- Enforced ở **service layer** (BE check khi create/update event), không phải DB constraint — vì overlap check trải nhiều dòng, khó biểu diễn bằng column constraint MySQL.
- Bỏ event Summer trùng (FUHN cũ), bỏ tên campus → còn 3 event: Spring (COMPLETED), Summer (IN_PROGRESS), Fall (DRAFT).

### Đồng bộ schema
- Role thêm SYSTEM_ADMIN (id 1), role dịch xuống.
- Thêm admin account (user_id 1), coordinator thành user_id 2.
- `judge_type` set trên User: An/Binh/Cam = INTERNAL, Guest = GUEST.
- Bỏ hết cột đã xóa khỏi mọi INSERT.
- Phân công judge/mentor ghi qua AuditLog (ASSIGN_JUDGE/ASSIGN_MENTOR) thay vì cột assigned_by.
- Thêm mục 19: SystemLog seed (CREATE_USER, GRANT_ROLE, LOGIN_FAILED).

### Kết quả load (MariaDB thật)
Schema + seed load **0 lỗi**. Row counts: Role 4, User 27, Event 3, Track 7, Round 6, UserEventRole 11, JudgeAssign 11, MentorAssign 3, Team 10, TeamMember 21, Submission 12, ScoringCriteria 15, Score 70, RoundResult 12, Prize 11, AccountApproval 15, TeamInvite 2, Notification 9, AuditLog 19, SystemLog 4.

### Integrity checks (12 check, tất cả pass)
1. Roles đúng thứ tự (ADMIN=1...JUDGE=4).
2. Không trùng (season, year).
3. Không overlap thời gian giữa các event.
4. Admin system-wide (event_id NULL).
5. Mọi judge được phân công đều có judge_type.
6. Mọi Score dùng đúng criteria của round mà submission thuộc về.
7. Mọi judge chỉ chấm track/round được phân công.
8. Không member trỏ tới user không tồn tại.
9. Mỗi team đúng 1 LEADER.
10. Người nộp bài là thành viên team đó.
11. Track cùng event với team.
12. RoundResult khớp event; user_id liên tục 1–27.

---

## 6. Thay đổi Postman Collection

### Cấu trúc mới (15 folder, 84 request, mỗi request có test assertion)
1. AUTH (thêm Login – Admin, lưu adminToken)
2. ADMIN – USER & ROLE MGMT (mới): CRUD user, grant/revoke role, activate/deactivate, **GET system-logs** — dùng adminToken
3. ACCOUNT APPROVAL (coordinator)
4. HACKATHON EVENTS
5. TRACKS
6. ROUNDS (đã xóa request Calibration)
7. COORDINATOR – ASSIGNMENTS (mới): assign judge/mentor, gồm case final trackId null + case thiếu trackId expect 400
8. TEAMS
9. TEAM INVITES
10. SUBMISSIONS
11. SCORING
12. ROUND RESULTS
13. ASSIGNMENTS – MY DASHBOARD (Mentor/Judge)
14. NOTIFICATIONS
15. SECURITY TESTS (thêm: participant→admin 403, coordinator→system-log 403)

### Đồng bộ
- Thêm biến `adminToken`.
- Tách user-management từ coordinator sang admin (`/api/admin/**`).
- Bỏ `isCalibration` khỏi mọi body, xóa request Calibration.
- Create staff: `userType: STAFF` + `judgeType`, bỏ roleName/eventId/roundId.
- Grant role COORDINATOR: eventId null (system-wide).
- Create event đổi sang Spring 2027 (không trùng mùa với seed).
- Mọi request có `pm.test` kiểm tra status code.

**Lưu ý:** Các endpoint `/api/admin/**` và `/api/coordinator/assignments/**` là giả định theo phân chia mới — BE cần implement đúng path hoặc sửa URL trong collection cho khớp.

---

## 7. Việc cần làm tiếp (TODO cho team)

1. **Đổi BCrypt hash placeholder** trong seed trước khi deploy/demo công khai.
2. **Code ở BE 2 logic thủ công:**
   - Resolve tiêu chí chấm: round-specific ưu tiên hơn event-level.
   - Suy "advanced": group theo track rồi so `rank_position <= top_n_advance`.
3. **Enforce quy tắc 1-event-per-season ở service layer** (DB không tự chặn).
4. **Implement endpoint admin/coordinator** đúng path trong Postman collection.
5. **FE**: đổi "Global Audit Log" → "System Log", thêm route guard SYSTEM_ADMIN dựa trên event_id === null.

---

## 8. Artifact xuất ra trong phiên

- `seal_schema.sql` — schema MySQL đã sửa (18 bảng dữ liệu).
- `seal_seed.sql` — seed data đồng bộ + ràng buộc 1-mùa-1-event (đã test thật trên MariaDB).
- `Postman_Full_Collection.json` — API collection đã đồng bộ + test assertion.
- `AI_LOG_SEAL_Admin_Split.md` — tài liệu này.
