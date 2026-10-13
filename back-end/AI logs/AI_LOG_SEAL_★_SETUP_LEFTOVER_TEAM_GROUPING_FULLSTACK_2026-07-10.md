# ★ AI LOG — SETUP Leftover-Team Grouping (FULLSTACK) — 2026-07-10

> Tính năng **tự động ghép "người lẻ" thành team hợp lệ** trong phase `SETUP`, chạy
> **trước** bước bốc thăm track (draw tracks). Coordinator xem **preview → duyệt → commit**.
> Nhánh: `NhatNHM-setup-leftover-team-grouping` (tách từ `develop`).
> **Không đụng** bất kỳ file `entity/` hay `.sql` nào → schema nguyên trạng.

---

## 1. Bối cảnh & vấn đề

Requirements (mục 14) yêu cầu demo luồng đầy đủ từ lúc tạo event. Trong lúc `SETUP`
(đóng đăng ký, sắp team vào track trước khi thi), luôn còn **"người lẻ"**: người tự tạo
team một mình, hoặc team chưa đủ 3 người. Gate `SETUP → IN_PROGRESS` đòi **mỗi track ≥ 2
team APPROVED** và **không team nào bị bỏ trống track** → nếu còn người lẻ rải rác thì
event kẹt, không start được.

Cần một công cụ cho coordinator: gom người lẻ + team thiếu thành các team hợp lệ (3–5
người), **giữ nhóm bạn bè nguyên vẹn**, và cảnh báo khi gặp thế bí không tự giải được.

**Phát hiện nền tảng về data model:** participant chỉ gắn với event **qua team
membership** (`TeamMemberRepository.existsByUser_UserIdAndTeam_Event_EventId`). Không có
bảng "đăng ký event cho cá nhân". → **"người lẻ" = team 1 người.**

---

## 2. Các quyết định thiết kế (Q&A với người dùng)

| Vấn đề | Quyết định |
|--------|-----------|
| Đơn vị grouping | **Atom** (khối không cắt): người lẻ = atom size 1; team 2 người = atom size 2 (giữ nguyên) |
| Team 2 người | **Mặc định là bạn bè** → 1 atom, không tách, **mọc thêm tại chỗ** |
| Cơ chế | **Propose → coordinator duyệt → commit** (KHÔNG auto-commit random) |
| "Người lẻ" trong DB | = **team 1 thành viên** (data model không có participant teamless) |
| Team ghép ra | Team mới: **leader random**; team mọc thêm: **giữ leader cũ** |
| Vị trí trong lifecycle | Trong `SETUP`, **trước** draw tracks (đúng bản chất SETUP) |
| Rải người lẻ vào team sẵn có | **OK** (giảng viên xác nhận) + notification cho team đó |
| Thế bí (deadlock) | **KHÔNG tự nhồi bậy** → trả WARNING cho coordinator xử tay |
| "Thi lẻ" (team 1 người) | Hợp lệ — hệ thống **không có gate min-member** lúc chuyển state |
| Xóa team nguồn khi gộp | Dời member + **dọn FK (JoinRequest/TeamInvite)** rồi xóa team (FK không cascade) |

**"Đường cắt" (cut-line):** đám seed/live không trộn nhau. Grouping dựng roster tới một
state nhất quán; account đăng ký live tạo team MỚI riêng, không chui vào team đã seed.

---

## 3. ★ Thuật toán ghép (LeftoverGroupingPlanner — thuần toán, không DB)

### Định nghĩa

```
MIN = 3, MAX = 5   (MAX = MAX_TEAM_MEMBERS đã có trong code)

ATOM = khối KHÔNG cắt được, phải đi cùng nhau:
   • người lẻ (team 1 người)      → atom size 1, existingTeam = false (di chuyển được)
   • team sẵn có size 2           → atom size 2, existingTeam = true  (giữ nguyên, mọc tại chỗ)

SETTLED = team sẵn có size ≥ 3  → hợp lệ, KHÔNG regroup; chỉ làm "chỗ chứa dự phòng"
          (sức chứa room = MAX − size)

F = số người lẻ (free agent) còn lại;  bin = team đích, sức chứa [MIN..MAX]
```

Mấu chốt: gộp cả người lẻ lẫn team thiếu vào chung 1 rổ atom → bài toán thành **xếp khối
size {1,2} vào thùng [3..5], không cắt khối**.

### 4 phase (deterministic — xử theo thứ tự ref, tái lập được)

```
Phase 0  S == 0 → không có gì để ghép, trả plan rỗng.

Phase 1  RESCUE — cứu team thiếu (existing size 1–2) lên MIN:
         xử team CẦN ÍT filler nhất trước (sort theo MIN − load tăng dần)
         → tối đa hoá số team được cứu.

Phase 2  FORM — gom người lẻ còn lại thành team MỚI, chia ĐỀU:
         chỉ chạy khi F ≥ MIN.  k = ⌈F / MAX⌉ thùng, rải round-robin
         → CHỨNG MINH được mọi thùng ∈ [MIN, MAX] (vd 7 người → 4+3, KHÔNG 5+2).

Phase 3  SPILL — nhét 1–2 người lẻ cuối vào team còn chỗ (load < MAX):
         ưu tiên team mới/mọc-thêm, rồi tới SETTLED.

Phase 4  WARN — phần còn kẹt trả về coordinator (KHÔNG tự nhồi):
         • team thiếu không cứu nổi  → DEFICIENT_TEAM_UNRESCUED (mềm; vẫn thi được)
         • người lẻ hết chỗ nhét     → UNPLACEABLE_LEFTOVER (đề xuất: thi lẻ / gán tay)
```

### Chứng minh Phase 2 luôn hợp lệ

Với `F ≥ MIN` và `k = ⌈F/MAX⌉`, mỗi thùng có `floor(F/k)` hoặc `+1` người.
`k = ⌈F/5⌉ ⇒ F > 5(k−1) ⇒ base = ⌊F/k⌋ ≥ 3`, và `base+1 ≤ 5`. → mọi thùng ∈ [3,5]. ∎

### Deadlock là ĐÚNG, không phải bug

- **3 cặp đôi (2+2+2 = 6 người, 0 người lẻ):** `⌈6/5⌉ = 2` thùng → chỉ ra `{2,2}=4` và
  `{2}=2 < MIN`, mà không được xé cặp → **bất khả thi** nếu không có người lẻ bắc cầu hoặc
  cho vượt MAX → đẩy ra WARNING.
- **26 người, 5 team full 5/5, lẻ 1:** người lẻ không join được (đầy), không thành team
  (cần 3) → UNPLACEABLE_LEFTOVER → coordinator duyệt "thi lẻ" / gán tay.

### So bản gốc của user

| Bản gốc | Sau khi vá |
|---|---|
| "rải từng NGƯỜI" (xé bạn bè) | rải theo **ATOM** (giữ nhóm) |
| `N<3` mặc định có chỗ nhét | **kiểm capacity**, hết chỗ → WARNING |
| `N>5` chia đều + đệ quy phần dư | **FFD/chia đều 1 lượt**, phần dư gom về WARNING |
| random tự commit | **propose → duyệt → commit** |

---

## 4. Verify các GATE của event/team (đọc code, không đoán)

| Gate | Luật thật | Nguồn |
|---|---|---|
| Vào `SETUP` | Cần ≥1 track; **đóng băng roster**; `computeTrackCapacities` chia slot | `HackathonEventService` |
| `SETUP → IN_PROGRESS` | **Mỗi track ≥ 2 team APPROVED** (`MIN_TEAMS_PER_TRACK=2`), 0 team unassigned | `requireSetupComplete` |
| Tạo team / join | Bắt buộc event `OPEN` → **SETUP thì bị chặn** (đây là "đóng băng roster") | `TeamService:62`, `JoinRequestService:197` |
| Số member | Tối đa **5** (`MAX_TEAM_MEMBERS`); **KHÔNG** gate min lúc chuyển state | `TeamInviteService`, `JoinRequestService` |
| Approve team | Không check số member → **team 1 người approve được** (escape hatch "thi lẻ") | `TeamService.approveTeam` |
| Gán track | SETUP-only (draw/self-select/manual) | `TeamService` |

**Hệ quả:** commit ghép phải **ghi thẳng qua repository** (SETUP-scoped, cố ý bypass gate
OPEN), và phải **tính lại capacity sau khi ghép** vì roster đổi giữa chừng SETUP.

---

## 5. Triển khai BACKEND

### Core thuần (package `service/grouping/`, không phụ thuộc JPA)
- `Atom`, `SettledTeam` — input; `GroupingPlan` / `ProposedTeam` / `GroupingWarning` — output.
- `LeftoverGroupingPlanner` — thuật toán 4 phase ở trên.

### Service (`service/LeftoverGroupingService`)
- **Build atom từ team APPROVED của event:** size 1 → free agent; size 2 → existing atom;
  size ≥3 (<MAX) → settled (room). Ref = `"T{teamId}"`, giữ `Map<ref, Source(team,members)>`.
- `preview(eventId)` — `@Transactional(readOnly=true)`, chạy planner, map ra response.
- `commit(eventId, actorUserId, reason)` — deterministic nên **preview == commit**:
  - Team mới: `Team(status=APPROVED)` + dời member (role MEMBER) + **leader random** + tên `Auto Team N`.
  - Team mọc thêm: giữ leader cũ, nhét member.
  - **Giải tán team-1-người:** `dissolve()` xóa `JoinRequest` + `TeamInvite` (FK không cascade) rồi xóa `Team`.
  - **Tính lại capacity:** `HackathonEventService.recomputeSetupTrackCapacities()` (method public mới, tái dùng `computeTrackCapacities`).
  - Audit `GROUP_LEFTOVERS` + notification `TEAM_GROUPED` cho mọi người bị xếp.
- Guard: cả hai chỉ chạy khi event ở `SETUP`.

### 2 endpoint (`TeamController`, quyền `EVENT_COORDINATOR`)
```
GET  /api/teams/event/{eventId}/leftover-grouping/preview   → GroupingPreviewResponse (dry-run)
POST /api/teams/event/{eventId}/leftover-grouping/commit?reason=  → GroupingCommitResponse
```

### Sửa kèm
- `HackathonEventService` — thêm `recomputeSetupTrackCapacities(eventId)` (SETUP-only, no-op nếu chưa có track).
- `JoinRequestRepository`, `TeamInviteRepository` — thêm `findByTeam_TeamId` để dọn FK.

---

## 6. Triển khai FRONTEND (`front-end/src/seal-web`)

- `shared/apiClient.ts` — types (`GroupingPreview`, `GroupingProposedTeam`, `GroupingWarning`,
  `GroupingCommitResult`) + `teamsApi.leftoverGroupingPreview` / `.leftoverGroupingCommit`.
- `features/events/LeftoverGroupingModal.tsx` (mới) — modal preview: summary (leftover/solo/pairs)
  + team đề xuất (badge NEW/GROWN, tên, size, ai được thêm) + khối cảnh báo (vàng).
  Nút **APPLY GROUPING → CONFIRM APPLY** (2 lớp xác nhận vì commit phá hủy). Empty state khi không có gì.
- `features/events/CoordEventsPage.tsx` — panel **"Group leftover participants"** + nút
  **GROUP LEFTOVERS** trong tab Tracks (SETUP), đặt **ngay trên** panel Draw tracks. Commit
  xong → toast summary + refresh roster.

---

## 7. Kiểm thử

- **`LeftoverGroupingPlannerTest` — 15/15 PASS.** Phủ: rỗng; 3/4/5→1 team; 6/7/8/10/11 chia
  đều; rescue tại chỗ; rescue ưu tiên cần-ít; spill vào settled; **ca 26** (UNPLACEABLE);
  **ca 3 cặp đôi 2+2+2** (DEFICIENT); mix. Có invariant "không mất/nhân bản người".
- Backend `mvn compile` sạch; Frontend `tsc --noEmit` sạch.
- **CHƯA verify end-to-end trên DB thật** (bị dừng để tránh sửa seed event `SEAL Summer 2026`).
  Cần: dựng scenario riêng (event SETUP + team 1/2/3 người) → gọi preview/commit → soi DB.

---

## 8. Tương thích với code-first (Hibernate)

Feature này **không đụng entity/schema**, và **tự dọn FK bằng tay** (không dựa vào
`ON DELETE CASCADE` của DB). → Khi bật code-first (`ddl-auto=update`) sau này, grouping
**vẫn chạy y hệt**, không xung đột. Hai việc hoàn toàn tách biệt (nhánh khác nhau).

---

## 9. Còn lại / next steps

- [ ] Verify end-to-end trên scenario DB riêng (an toàn, không đụng seed).
- [ ] (tuỳ) Cho coordinator **sửa proposal** trước khi commit (hiện commit re-compute plan; edit là enhancement).
- [ ] (tuỳ) Chỉ báo "còn N người lẻ" ở `CoordTeamsPage` để nhắc chạy grouping.
- [ ] Commit git (17 file, hiện chưa commit).

---

## 10. Danh sách file

**Backend — mới:**
```
service/grouping/Atom.java, SettledTeam.java, ProposedTeam.java,
                 GroupingWarning.java, GroupingPlan.java, LeftoverGroupingPlanner.java
service/LeftoverGroupingService.java
dto/response/GroupingPreviewResponse.java, GroupingCommitResponse.java
test/.../service/grouping/LeftoverGroupingPlannerTest.java
```
**Backend — sửa:** `controller/TeamController.java`, `service/HackathonEventService.java`,
`repository/JoinRequestRepository.java`, `repository/TeamInviteRepository.java`

**Frontend — mới:** `features/events/LeftoverGroupingModal.tsx`
**Frontend — sửa:** `features/events/CoordEventsPage.tsx`, `shared/apiClient.ts`

---

*Hằng số: `MIN_TEAMS_PER_TRACK = 2`, `MAX_TEAM_MEMBERS = 5`, `MIN` (khuyến nghị) `= 3` —
lưu ý MIN không có gate cứng trong DB, chỉ là mục tiêu của grouping.*
