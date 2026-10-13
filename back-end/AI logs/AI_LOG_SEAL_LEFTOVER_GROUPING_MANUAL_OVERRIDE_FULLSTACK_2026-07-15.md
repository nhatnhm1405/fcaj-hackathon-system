# AI LOG — Leftover-Grouping Manual Override + Editable Proposed Teams (FULLSTACK) — 2026-07-15

> Bổ sung **escape hatch thủ công** cho tính năng gom "người lẻ" SETUP-phase đã có
> (xem `AI_LOG_SEAL_★_SETUP_LEFTOVER_TEAM_GROUPING_FULLSTACK_2026-07-10.md`), đúng
> mục "next steps" (mục 9) của log đó: *"Cho coordinator sửa proposal trước khi
> commit (hiện commit re-compute plan; edit là enhancement)"*.
> Nhánh: `develop` (làm trực tiếp, chưa tách nhánh riêng, chưa commit).
> **Không đụng** entity/schema/`database scripts/` — toàn bộ tính năng dựng trên
> `Team`/`TeamMember`/`User` sẵn có.

---

## 1. Bối cảnh & yêu cầu ban đầu

`LeftoverGroupingPlanner` (từ 2026-07-10) tự động ghép người lẻ, nhưng tự nó thừa
nhận có những thế bí không tự giải được — 2 warning message trong code đã "hứa"
sẵn một cửa thoát thủ công chưa tồn tại:

```java
"...may proceed as-is or be merged manually."                       // DEFICIENT_TEAM_UNRESCUED
"...place them manually or approve a solo team."                    // UNPLACEABLE_LEFTOVER
```

Yêu cầu (từ file `prompt.txt` người dùng đưa, bị lỗi xuống dòng khi copy-paste,
phải suy luận lại ý từ ngữ cảnh còn sót): thêm 1 endpoint cho coordinator:
1. Đặt một nhóm người cụ thể vào một team có sẵn.
2. Hoặc force-approve một team mới **dưới MIN** (kể cả team 1 người — "thi lẻ").

Sau khi làm xong backend + một bản UI kéo-thả đầu tiên, người dùng test trên UI
thật và phản hồi 2 vòng, dẫn tới việc **mở rộng phạm vi đáng kể** so với yêu cầu
gốc — xem mục 5 và 6.

---

## 2. Vòng 1 — Endpoint `manual-assign` (đúng yêu cầu gốc)

### Backend

**DTO mới** `dto/request/ManualAssignLeftoverRequest.java`:
```java
List<Integer> userIds;   // @NotEmpty, size <= 5 (MAX_TEAM_MEMBERS)
Integer targetTeamId;    // null = force-tạo team mới
String reason;           // optional, audit note
```

**`LeftoverGroupingService.manualAssign(eventId, actorUserId, request)`** (mới):
- Guard SETUP (dùng lại `requireSetupEvent`, y hệt `preview`/`commit`).
- Không cho `userIds` trùng lặp.
- **Eligibility** — mỗi userId phải đang thuộc "leftover pool" của event:
  (a) free agent (`UserRepository#findGroupableFreeAgents`), hoặc
  (b) member của 1 team APPROVED có size `< MIN`.
  Kéo người từ team đã đủ MIN → `BadRequestException` nêu rõ tên user vi phạm.
- Nếu có `targetTeamId` → đặt thẳng vào làm MEMBER (ghi trực tiếp `TeamMember`,
  bỏ qua flow JoinRequest/Invite thông thường — giống cách `commit()` đã làm),
  kiểm tra không vượt `MAX_TEAM_MEMBERS=5`.
- Nếu `targetTeamId = null` → tạo team mới `status=APPROVED`, người đầu tiên
  trong `userIds` làm LEADER — **cách chính thức để force-approve 1 người thành
  team đơn** (truyền list 1 phần tử).
- Dời người khỏi team nguồn thiếu người → nếu team nguồn rỗng, `dissolve()`
  (dọn `JoinRequest`/`TeamInvite` rồi xóa `Team` — tái dùng nguyên hàm cũ).
- Notification khớp văn phong `applyNewTeam`/`growExistingTeam` đã có, có nêu
  tên leader mới khi force-tạo team.
- Audit `MANUAL_ASSIGN_SETUP_LEFTOVERS` + `recomputeSetupTrackCapacities`.
- **Trả về đúng `GroupingCommitResponse`** có sẵn — không tạo DTO response mới.
- Javadoc ghi rõ **caveat đã biết, cố ý không sửa**: 1 team force-approve size
  1–2 sẽ bị `buildContext` coi là atom leftover bình thường ở lần preview/commit
  tự động kế tiếp → có thể bị nuốt lại và giải tán. Khuyến nghị coordinator chạy
  manual override **sau khi** đã chạy hết các đợt auto-commit.

**Repo mới:** `TeamMemberRepository#findByTeam_Event_EventIdAndTeam_StatusAndUser_UserIdIn`
— tra cứu hàng loạt eligibility trong 1 query.

**Controller:** `POST /api/teams/event/{eventId}/leftover-grouping/manual-assign`,
`EVENT_COORDINATOR`, cạnh `preview`/`commit`.

**Postman:** thêm 2 request mẫu vào mục "8. TEAMS" (đặt free agent vào team có
sẵn; force-tạo team đơn) — chèn thủ công đúng style file gốc (2-space, CRLF),
verify JSON hợp lệ + diff chỉ có phần thêm.

### Frontend (bản đầu — vẫn checkbox/dropdown)

- `apiClient.ts`: `ManualAssignLeftoverPayload` + `teamsApi.leftoverGroupingManualAssign`.
- `LeftoverGroupingModal.tsx`: mỗi warning row thêm checkbox chọn người + dropdown
  chọn team đích + nút "PLACE (n)".

**Kiểm thử:** `mvnw compile` sạch; `LeftoverGroupingPlannerTest` +
`LeftoverGroupingFreeAgentTest` pass; `tsc --noEmit` sạch (1 lỗi TS có sẵn ở
`CoordJudgesPage.tsx`, xác nhận không liên quan — file không nằm trong diff).

---

## 3. Vòng 2 — Phản hồi: "vẫn méo kéo thả được"

Người dùng test UI checkbox/dropdown, phản hồi muốn **kéo-thả** thay vì
checkbox — đúng pattern đã dùng cho gán team vào track (`react-dnd` +
`DraggableTeamRow`/`TeamDropZone` trong `CoordEventsPage.tsx`).

→ Viết lại phần "Needs your attention": mỗi người là 1 **chip kéo được**
(`DraggablePersonChip`, DND type `LEFTOVER_PERSON`), thả vào 1 dải **drop-zone**
(`DropTargetPalette`): mỗi team `APPROVED` còn chỗ (`x/5`, mờ đi khi đầy) + 1 ô
riêng **"+ Force new team"**. Thả → gọi `manual-assign` ngay lập tức, preview
tự refresh "âm thầm" (`load({silent:true})`, không nháy loading).

`teams` (danh sách team của event, đã có sẵn ở `CoordEventsPage`) được truyền
xuống modal làm prop để dựng drop-zone, refresh qua callback `onManualAssigned`
mới (khác `onCommitted` — **không đóng modal**, coordinator xử nhiều warning
liên tiếp trong 1 lần mở).

**Ảnh chụp màn hình người dùng gửi** cho thấy 9 người leftover được thuật toán
tự xếp gọn vào 3 team đủ 3 người, **0 warning** → không có gì để kéo. Giải thích
+ verify: chạy tay qua thuật toán (rescue Bayern 2→3 dùng 1 filler, còn 6 filler
chia 2 team mới 3+3 = khớp `⌈6/5⌉=2`) → xác nhận **đúng hành vi**, không phải bug
hay do chưa restart dev server. Chạy lại `LeftoverGroupingPlannerTest` (đã có sẵn
case `UNPLACEABLE_LEFTOVER` + `DEFICIENT_TEAM_UNRESCUED`) để xác nhận logic
warning không hỏng.

---

## 4. Vòng 3 — Yêu cầu mở rộng: kéo-thả ngay trong "Proposed Teams"

Người dùng: *"tôi vẫn muốn cho chức năng kéo thả ngay tại mục proposed team
luôn... nếu 1 team nào đó có ít hơn 3 người và khác 0 thì sẽ chặn không cho
apply grouping."*

**Vấn đề kiến trúc:** "Proposed Teams" chỉ là **preview thuần tính toán**, chưa
có `teamId` thật (chỉ được tạo khi bấm APPLY → gọi `commit()`, mà `commit()` lại
**tự chạy lại thuật toán từ đầu**, bỏ qua mọi chỉnh sửa của coordinator). Cần
endpoint mới nhận **kế hoạch đã chỉnh sửa** thay vì tự tính lại.

### Backend — endpoint `apply` (mới)

**DTO mới** `dto/request/ApplyLeftoverGroupingRequest.java`:
```java
List<TeamComposition> teams;   // mỗi phần tử = 1 thẻ Proposed Team
String reason;

class TeamComposition {
  Integer existingTeamId;       // null = team mới hoàn toàn
  List<Integer> memberUserIds;  // roster CUỐI CÙNG đầy đủ (không phải delta)
}
```

**Refactor `LeftoverGroupingService`** — tách logic đặt người dùng chung giữa
`manualAssign` và endpoint mới ra `applyComposition(event, existingTeamId,
memberUserIds)` (trả `CompositionResult(team, newTeamCreated, newlyPlacedCount)`):
- Người **đã đúng y hệt trên `targetTeam`** → no-op, **không đụng** role/row
  (tránh ghi đè LEADER hiện có khi coordinator gửi lại nguyên roster cũ).
- Người khác → check eligibility y hệt `manualAssign`, rồi đặt.
- MAX cap tính theo **số người mới thực sự thêm vào** (không tính người đã ở sẵn).

**`applyPlan(eventId, actorUserId, request)`** (mới):
- Check trùng `userId` giữa các `TeamComposition` (1 người không được ở 2 team).
- Chạy `applyComposition` cho từng composition không rỗng (composition rỗng =
  slot bị dọn trống hoàn toàn → bỏ qua, không lỗi).
- **Sau khi áp dụng hết**, validate lại size THẬT của mọi team đã đụng tới:
  `0` hoặc `>= MIN` mới hợp lệ; `1` hoặc `2` → `BadRequestException` → **rollback
  toàn bộ transaction** (mọi thay đổi trong request này, kể cả các composition
  đã xử lý trước đó) — coordinator edit là **tất cả hoặc không gì cả**.
- Audit `APPLY_EDITED_LEFTOVER_GROUPING` + `recomputeSetupTrackCapacities`.
- **Khác `manualAssign` một điểm cốt lõi:** `manualAssign` CỐ Ý bỏ qua MIN (mục
  đích chính là force-approve dưới MIN); `applyPlan` thì **luôn enforce MIN**
  (trừ trường hợp 0) — đúng yêu cầu người dùng.

**Endpoint:** `POST /api/teams/event/{eventId}/leftover-grouping/apply`.

### 🐛 Bug tự phát hiện khi rà soát lại (trước khi báo hoàn thành)

Trace tay qua case: 1 **settled team** (đã thật sự ≥ MIN trong DB, ví dụ size 4)
hấp thụ thêm 1 người lẻ qua pha **Spill** của thuật toán (case này đã có test
`plan_shouldSpillStragglersIntoSettledTeam_whenNewTeamsCannotFormThem` từ
2026-07-10) → thẻ Proposed Team loại `EXISTING` hiển thị 4 người cũ + 1 người
mới = 5. Nếu coordinator **không kéo gì cả**, bấm APPLY luôn:

- Bản đầu của `applyComposition` check eligibility **trước** khi biết ai là
  no-op → 4 người cũ (đã thật sự ở trên team, size thật = 4 ≥ MIN) bị
  `BadRequestException("...already meets the recommended minimum size...")`
  dù họ **không hề bị di chuyển**, chỉ được gửi lại y nguyên trong request.
- **Hệ quả:** để nguyên 1 thẻ Proposed Team hợp lệ (do thuật toán tự đề xuất,
  chưa qua chỉnh sửa gì) cũng khiến APPLY thất bại — sai hoàn toàn.

**Sửa:** đổi thứ tự — xác định no-op (đã đúng y hệt `targetTeam`) **trước**,
chỉ check eligibility cho người **thực sự đang bị di chuyển**. Người ngoài cuộc
của 1 settled team (chưa từng thuộc leftover pool) vẫn **có thể gửi lại y
nguyên** (no-op, không việc gì) nhưng **không thể bị kéo đi nơi khác** (vẫn bị
chặn đúng như thiết kế gốc — "không được rút người khỏi team đã hợp lệ").
Compile + chạy lại `LeftoverGroupingPlannerTest`/`LeftoverGroupingFreeAgentTest`
sau khi sửa — vẫn pass.

### Frontend — Proposed Teams thành khu vực chỉnh sửa được

`LeftoverGroupingModal.tsx`:
- State `editedTeams: EditableTeam[]` — bản sao cục bộ của
  `preview.proposedTeams`, seed lại mỗi khi `preview` đổi (kể cả sau 1 lần thả ở
  "Needs your attention" — chấp nhận đánh đổi: edit dở ở Proposed Teams bị mất
  nếu xen kẽ thao tác 2 khu vực, ghi rõ trong comment).
- Mỗi thẻ team (`ProposedTeamCard`) **vừa là nguồn kéo vừa là nơi thả**:
  từng người (`DraggableProposedMemberRow`) kéo được sang thẻ khác. DND type
  riêng `PROPOSED_TEAM_MEMBER` — **tách biệt hoàn toàn** với `LEFTOVER_PERSON`
  (2 hệ kéo-thả không nhận nhầm của nhau).
- Kéo-thả ở đây **chỉ đổi state client**, không gọi API — chỉ gửi lên khi bấm
  CONFIRM APPLY (gọi endpoint `apply` mới, thay cho `commit()` cũ).
- Team còn 1–2 người (khác 0) → viền đỏ + banner liệt kê tên team vi phạm +
  **disable nút APPLY GROUPING lẫn CONFIRM APPLY** cho tới khi coordinator kéo
  bù hoặc dọn trống hẳn (0 người = "sẽ không được tạo", không tính là lỗi).

`apiClient.ts`: `ApplyLeftoverGroupingPayload` + `teamsApi.leftoverGroupingApplyPlan`.

**Kiểm thử:** `mvnw compile` sạch, test backend pass lại lần nữa sau bugfix;
`tsc --noEmit -p tsconfig.app.json` sạch (chỉ còn lỗi có sẵn không liên quan ở
`CoordJudgesPage.tsx`).

---

## 5. Kiến trúc cuối — 2 hệ kéo-thả song song, không giao nhau

| | "Needs your attention" (warnings) | "Proposed Teams" |
|---|---|---|
| Đối tượng kéo | Người trong warning (chưa xếp được) | Người trong đề xuất (đã xếp) |
| DND type | `LEFTOVER_PERSON` | `PROPOSED_TEAM_MEMBER` |
| Khi thả | Gọi API `manual-assign` **ngay lập tức** | Chỉ đổi **state cục bộ** |
| Bypass MIN? | **Có** (mục đích chính) | **Không** (chặn nếu 1–2, khác 0) |
| Khi nào ghi DB | Ngay khi thả | Chỉ khi bấm CONFIRM APPLY (gọi `apply`) |
| Endpoint backend | `POST .../manual-assign` | `POST .../apply` |

Cả 2 đều dùng chung lõi `applyComposition()` ở backend (eligibility rule giống
hệt: free agent hoặc member của team APPROVED size `< MIN`), chỉ khác ở việc
**có enforce lại MIN sau khi áp dụng hay không**.

---

## 6. Việc chưa làm / để ngỏ

- [ ] Chưa cập nhật Postman cho endpoint `apply` mới (đã có cho `manual-assign`
      từ vòng 1; `apply` nhận body phức tạp hơn, để sau nếu cần).
- [ ] Chưa verify end-to-end trên DB thật qua UI (người dùng tự test bằng
      `npm run dev`, chưa có xác nhận cuối cùng "đã đúng như mong đợi").
- [ ] Frontend cho phép kéo **bất kỳ ai** hiển thị trong 1 thẻ Proposed Team,
      kể cả người "vô can" của 1 settled team đang hấp thụ spill (xem mục 4) —
      nếu coordinator kéo người này đi, APPLY sẽ bị backend từ chối rõ ràng
      (400, nêu tên) chứ không cho thử — chấp nhận được vì hiếm gặp, nhưng có
      thể làm UI disable trước (không cho kéo) nếu muốn UX mượt hơn.
- [ ] Chưa commit git (branch `develop`, toàn bộ đang là working-tree changes).

---

## 7. Danh sách file

**Backend — mới:**
```
dto/request/ManualAssignLeftoverRequest.java
dto/request/ApplyLeftoverGroupingRequest.java
```
**Backend — sửa:**
```
controller/TeamController.java                  (+manual-assign, +apply)
service/LeftoverGroupingService.java             (+manualAssign, +applyPlan,
                                                   +applyComposition dùng chung,
                                                   +placeUser, +notifyManualAssign,
                                                   bugfix thứ tự eligibility-check)
repository/TeamMemberRepository.java             (+findByTeam_Event_EventIdAndTeam_StatusAndUser_UserIdIn)
Postman/Postman_Full_Collection.json             (+2 request mẫu manual-assign)
```

**Frontend — sửa:**
```
shared/apiClient.ts                              (+ManualAssignLeftoverPayload,
                                                   +ApplyLeftoverGroupingPayload,
                                                   +teamsApi.leftoverGroupingManualAssign,
                                                   +teamsApi.leftoverGroupingApplyPlan)
features/events/LeftoverGroupingModal.tsx        (viết lại: warnings → drag-and-drop
                                                   vào DropTargetPalette; Proposed
                                                   Teams → editable drag-and-drop cards)
features/events/CoordEventsPage.tsx              (truyền teams + onManualAssigned
                                                   xuống modal)
```

---

*Hằng số nhắc lại: `MIN=3`, `MAX_TEAM_MEMBERS=5`. `manual-assign` bỏ qua MIN có
chủ đích (escape hatch cho 1 người/1 team kẹt); `apply` luôn enforce MIN (trừ 0)
vì nó thao tác trên **toàn bộ** đề xuất, không phải 1 ca ngoại lệ đơn lẻ.*
