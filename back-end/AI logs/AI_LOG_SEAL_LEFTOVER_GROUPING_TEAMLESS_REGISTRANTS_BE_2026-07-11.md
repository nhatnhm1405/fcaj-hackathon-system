# AI LOG — Leftover Grouping cover Teamless Registrants (BACKEND) — 2026-07-11

> Grouping người lẻ lúc SETUP giờ gom **cả registrant chưa vào team nào**, không chỉ
> thành viên của đội thiếu quân. Kèm seed demo (S1) + unit test.
> Tiếp nối phiên demo code-first: [[AI_LOG_SEAL_RUN_DEMO_SCRIPT_BE_2026-07-11]].

---

## 1. Bối cảnh & phát hiện

Đang chỉnh seed demo (S1) cho phần "ghép người lẻ" thì nhận ra 1 câu hỏi nghiệp vụ:
**sau registration, thế nào cũng có người đăng ký mà chưa vào đội nào** — grouping có
cover họ không?

Rà code `LeftoverGroupingService.buildContext` → **KHÔNG**. Nó chỉ quét **đội APPROVED**
của event (dòng cũ 128), coi "người lẻ" = thành viên đội thiếu quân (đội 1 người = free
agent). User teamless thật sự **bị bỏ sót**.

## 2. Vì sao trước đó tưởng "không có người teamless"

Data model: user gắn với event **chỉ qua Team** (TeamMember→Team→Event).
`ParticipationAccessRequest` = duyệt **tài khoản** (không gắn event); `JoinRequest` = xin
vào **1 team**. Nên schema **không có state "đăng ký event nhưng chưa có team"** — 1 người
lẻ được biểu diễn bằng **đội solo (1 người)**.

**Nhưng** vẫn tồn tại người teamless thật: user **active + approved** (đang thuộc mùa hiện
tại) mà chưa tạo/join đội nào. Đó chính là nhóm cần gom.

## 3. Nghiệp vụ `isActive` (verify trong code)

`HackathonEventService.lockCompletedEventParticipantsReadOnly` (khi event COMPLETED):
lấy **student** (FPT/EXTERNAL) trong đội của event, không còn membership ở event chưa xong
nào khác → **`setIsActive(false)`**. Mở lại qua `ParticipationAccessRequest` (`setIsActive(true)`).

→ Theo luật **"1 event active tại 1 thời điểm"**, **active student** ≈ cohort của event
hiện tại. Nên nguồn free-agent = **active + approved + student + không thuộc team nào của event**.

## 4. Thay đổi (feature thật, không phải seed)

**`UserRepository`**
- `findGroupableFreeAgents(eventId)` — JPQL: `isApproved AND isActive AND userType IN (FPT/EXTERNAL) AND userId NOT IN (thành viên team của event)`.

**`LeftoverGroupingService`**
- `Source` giờ là **team HOẶC user trần**: `ofTeam(...)` / `ofUser(...)`; ref `"T{id}"` vs `"U{id}"`.
- `buildContext` — sau vòng lặp team, thêm mỗi free-agent user thành `Atom.freeAgent`.
- `reassignMembers` — nếu là user trần: **TẠO `TeamMember` mới** (chưa có membership), **bỏ `dissolve`** (không có team nguồn).
- `membersOf` (preview) — hiện tên user trần.
- Cập nhật javadoc lớp.

**3 ràng buộc đã chốt (thảo luận trước khi code):**
1. **Chỉ student** — không thì coordinator/judge/mentor/admin bị gom thành thí sinh 💀.
2. Phải **approved** (acc mới default active nhưng chưa duyệt → chưa tính).
3. Dựa vào invariant **1 event active** để "active student" = participant event này.

## 5. Test

`LeftoverGroupingFreeAgentTest` (Mockito) — **2 test, PASS**:
- `previewCountsTeamlessRegistrantsAsFreeAgents` — 3 registrant teamless → 3 leftover / 1 đội đề xuất.
- `commitCreatesMembershipsForTeamlessFreeAgents` — commit → tạo 1 đội mới + **tạo membership** cho cả 3 (không dời/dissolve).

## 6. Seed demo (S1)

Thêm **3 student teamless** (tên cầu thủ, approved+active, không team) vào S1 để demo
đúng feature. Giờ S1 có: 2 solo + 1 pair + **3 teamless** → material cho grouping.

⚠️ **Demo lưu ý:** grouping chỉ chạy khi event **SETUP** (`requireSetupEvent`), mà S1 là
**OPEN** → lúc demo coordinator phải chuyển **OPEN → SETUP** rồi mới bấm grouping.
Lưu ý: spare `leader1@`/`member1@` cũng là student teamless → cũng bị gom nếu chạy grouping
trước khi dùng chúng để demo "tạo đội tay".

## 7. File
**Sửa:** `repository/UserRepository.java`, `service/LeftoverGroupingService.java`, `config/seed/DemoScenario.java`
**Mới:** `test/.../service/LeftoverGroupingFreeAgentTest.java`

## 8. Còn ngỏ (bàn sau nếu cần)
- Người chỉ có **pending/declined JoinRequest** (chưa tự tạo solo, chưa active-teamless): hiện vẫn ngoài phạm vi — cân nhắc sau.
- Verify end-to-end trên app thật (mới compile + unit test, chưa boot).
