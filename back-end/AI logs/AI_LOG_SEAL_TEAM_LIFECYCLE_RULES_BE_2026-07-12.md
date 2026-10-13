# AI LOG — Team/Event Lifecycle Rules (BACKEND) — 2026-07-12

> Hai ràng buộc nghiệp vụ quanh vòng đời team/event, phát hiện khi rà demo flow:
> (1) chặn vào SETUP khi còn team chờ duyệt; (2) team chưa duyệt vẫn dựng được roster.
> Nhánh: `NhatNHM-grouping-setup-roster-rules` (off develop).

---

## 1. Rule A — Chặn OPEN→SETUP khi còn team PENDING

**Vấn đề:** transition `enteringSetup` chỉ gọi `computeTrackCapacities`, **không** kiểm
tra đơn đội chờ duyệt → có thể đóng đăng ký (SETUP) khi vẫn còn team PENDING, rồi
**đóng băng roster / bốc track** trên tập chưa xử lý xong.

**Sửa (`HackathonEventService`):**
- Thêm `requireAllTeamsResolved(event)` gọi **trước** `computeTrackCapacities` trong nhánh
  `enteringSetup`. Đếm `teamRepository.countByEvent_EventIdAndStatus(eventId, "PENDING")`,
  > 0 → `BadRequestException`. Guard chạy trước mọi mutation → fail thì rollback sạch.
- `TeamRepository` + `countByEvent_EventIdAndStatus(...)`.

**Rule:** vào SETUP thì **mọi team phải resolved** (APPROVED / REJECTED / DISQUALIFIED),
không còn PENDING.

**Test:** `HackathonEventServiceTest#moveToSetup_shouldThrow_whenTeamsAreStillPendingApproval`.

## 2. Rule B — Team chưa approved vẫn dựng được roster

**Vấn đề:** `TeamInviteService` + `JoinRequestService` bắt team phải **APPROVED** mới
mời / nhận join request → team PENDING (vừa tạo, chờ duyệt) **không lập được đội hình**.

**Nguyên tắc đúng (người dùng chốt):** approval **chỉ** gate **chọn track**
(`TeamService#selectTrack` — đã có sẵn gate, giữ nguyên). Việc dựng roster thì team
PENDING vẫn làm được.

**Sửa:**
- `TeamInviteService.validateTeamCanReceiveInvite` — bỏ chặn `!APPROVED`; thay bằng chặn
  **REJECTED / DISQUALIFIED** (team đã "đóng" thì thôi).
- `JoinRequestService.validateTeamCanAcceptRequest` — nới y hệt, cho nhất quán 2 chiều
  (leader mời ↔ người ngoài xin vào).

**Test:**
- `TeamInviteServiceTest` — thêm PENDING-mời-được, đổi case cũ thành REJECTED-bị-chặn.
- `JoinRequestServiceTest` (mới) — PENDING xin-vào-được + REJECTED-bị-chặn.

> Ghi chú: join request vẫn yêu cầu event **OPEN** (chặt hơn invite cho OPEN+SETUP) —
> giữ nguyên, ngoài phạm vi yêu cầu.

## 3. File
**Sửa:** `service/HackathonEventService.java`, `repository/TeamRepository.java`,
`service/TeamInviteService.java`, `service/JoinRequestService.java`
**Test:** `HackathonEventServiceTest` (+1), `TeamInviteServiceTest` (sửa+thêm),
`JoinRequestServiceTest` (mới)

## 4. Verify
- Compile PASS. Test PASS: HackathonEvent 22, TeamInvite 26, JoinRequest 2.
- Chưa boot app thật (unit test cấp service).
