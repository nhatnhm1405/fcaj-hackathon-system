# AI LOG — Leave Event, Leave Team fix, and Coordinator Removal (FULLSTACK) — 2026-07-15

> Bổ sung 2 năng lực còn thiếu quanh vòng đời `User.isActive` của participant: (1)
> Coordinator có thể gỡ 1 thành viên cụ thể khỏi team đang thi (`IN_PROGRESS`), và
> (2) participant tự nguyện thoát khỏi mùa giải hiện tại. Đi kèm 1 bugfix latent
> (xoá team không dọn FK) và 1 lần **tự sửa sai giữa chừng** sau khi review lại
> đúng ranh giới nghiệp vụ giữa "leave team" và "leave event".
> Nhánh: `develop` (làm trực tiếp, chưa tách nhánh riêng, chưa commit).
> **Không đụng** entity/schema/`database scripts/` — dựng trên `Team`/`TeamMember`/
> `User` sẵn có, không thêm cột nào.

---

## 1. Bối cảnh

Trước phiên này, `User.isActive` ("đang thi mùa nào") chỉ có 2 cách thay đổi:
- Tự động `false` khi event `COMPLETED` (`HackathonEventService.lockCompletedEventParticipantsReadOnly`).
- Coordinator duyệt `ParticipationAccessRequest` → `true` (participant tự request "cho tôi thi mùa này", Coordinator approve).

Không có cách nào để **chủ động set `false`** ngoài lúc event complete tự động. Hai
khoảng trống được yêu cầu bổ sung:

1. Participant lỡ đăng ký, muốn rút khỏi mùa giải khi event còn `OPEN`/`SETUP`.
2. Coordinator cần loại 1 người khỏi team đang thi (`IN_PROGRESS`) vì vắng mặt/điểm
   danh — không phải loại cả team (`disqualifyTeam` đã có, nhưng đó là toàn team).

### Quyết định nghiệp vụ đã chốt qua Q&A trước khi code

| Vấn đề | Quyết định |
|---|---|
| Overlap với `AdminService.setUserActive` (System Admin) | Không overlap thật — Admin là toggle account thô, vô điều kiện, giữ nguyên không đổi. Coordinator chỉ có 1 lối hẹp, có điều kiện, có audit, không phải trang danh sách account nào cả. |
| Coordinator gỡ leader | Auto-promote **tự động** (không cho coordinator chọn) — người `joinedAt` sớm nhất trong số còn lại lên LEADER, có notify. |
| Coordinator gỡ người cuối cùng | Team chuyển `DISQUALIFIED` (không xoá) — vì lúc `IN_PROGRESS` có thể đã có submission/score tham chiếu `team_id` qua FK. |
| Team 1 người tồn tại được không lúc SETUP? | Có — nhánh leftover-grouping-manual-override (đã merge trước đó) cho phép coordinator force-approve team 1 người. Vậy rule "hết người → disqualify" phải áp dụng cho case này. |
| Reason bắt buộc cho coordinator, không bắt buộc cho self-service | Đúng — coordinator-remove là action đặc quyền giữa lúc thi, phải audit log kèm lý do; participant tự rời thì không cần. |

---

## 2. Vòng 1 — Coordinator removal (đúng như dự tính, không phải sửa lại)

### Backend

**`TeamService.coordinatorRemoveMember(coordinatorId, teamId, targetUserId, reason)`** (mới):
- Guard: `team.getEvent().getStatus() == IN_PROGRESS`, `team.getStatus() == APPROVED`.
- Xoá `TeamMember`, set `isActive=false` cho người bị gỡ.
- Hết người → `status=DISQUALIFIED`, `disqualifiedReason` = lý do coordinator nhập
  (prefix "No remaining members (last member removed by coordinator): "),
  `disqualifiedAt=now`. **Không xoá** team row.
- Còn người, người bị gỡ là LEADER → promote tự động người `joinedAt` sớm nhất, notify.
- Notify người bị gỡ (kèm lý do).
- Audit log `COORDINATOR_REMOVE_MEMBER` (actor, targetType=TEAM, reason, metadata:
  removedUserId/resultingStatus/promotedLeaderUserId).
- Trả về `TeamDetailResponse` (giống `approveTeam`/`rejectTeam`/`disqualifyTeam`).

**DTO mới** `CoordinatorRemoveMemberRequest { @NotBlank @Size(max=1000) reason }`.

**Controller:** `PUT /api/teams/{teamId}/members/{userId}/coordinator-remove`,
`EVENT_COORDINATOR`, cạnh `approve`/`reject`/`disqualify`.

**Postman:** thêm happy-path (200) + Participant gọi (expect 403) vào mục "8. TEAMS".

### Frontend

- `TeamDetailModal.tsx` (**shared, dùng chung với Mentor/Judge**): thêm 1 prop tuỳ
  chọn `renderMemberAction?: (member) => ReactNode`, render cạnh badge role của
  từng member. Backward-compatible 100% — Mentor/Judge không truyền prop này nên
  không đổi gì bên họ (verify bằng `tsc`, 0 lỗi mới ở 2 file đó).
- `CoordTeamsPage.tsx`: `EventRow` thêm field `status`; nút **"Remove"** đỏ nhỏ per-member
  trong modal chi tiết team, chỉ hiện khi `team.status===APPROVED && event.status===IN_PROGRESS`.
  Modal xác nhận riêng (`RemoveMemberModal`, không đụng `TeamActionModal`/
  `getTeamActionConfig` cũ) — bắt buộc nhập reason, message đổi theo ngữ cảnh
  (member thường / leader sắp bị auto-promote người khác / member cuối cùng →
  disqualify). Sau khi remove: cập nhật thẳng từ response backend, **giữ modal mở**
  để xử tiếp nhiều người vắng mặt trong 1 lần (trừ khi team rỗng hẳn thì tự đóng).

**Kiểm thử:** verify trực tiếp trên UI thật (dev server đang chạy, seed có event
"SEAL Demo Summer 2026") — xác nhận nút Remove hiện đúng trên team `Borussia
Dortmund` (APPROVED, event IN_PROGRESS lúc đó), đúng cả 3 member kể cả LEADER.

---

## 3. Vòng 2 — "Leave event": **sai lầm ban đầu**, phải sửa lại

### 3.1. Thiết kế sai (đã làm rồi mới nhận ra)

Hiểu ban đầu: "rời event = rời team" là **1 hành động duy nhất** → sửa thẳng
`TeamService.leaveTeam` để nó **vừa** rời team **vừa** set `isActive=false`, thêm
gate chặn khi event không phải `OPEN`/`SETUP`. Đã code xong cả BE (`leaveTeam` sửa,
`TeamServiceTest` thêm 2 mock) lẫn FE (`TeamViewPage.tsx`: hiện lý do khoá bằng
`teamLockReason`, `patchCurrentUser({is_active:false})` sau khi leave).

### 3.2. Người dùng sửa lưng — 2 điểm sai nghiêm trọng

> *"leave team vốn dĩ có logic cũ rất tốt ở chỗ nó sẽ rời team và thành participant
> no team như bình thường (isactive vẫn true), chứ Leave event là khi đó mới đụng
> tới isactive."*

Kết luận đúng: **"Leave team" và "Leave event" là 2 hành động độc lập, không phải
1**:
- **Leave Team** (đã có từ trước, không được đụng): rời roster team → thành
  participant teamless → **`isActive` giữ nguyên `true`**. Có thể tạo/join team
  khác trong cùng event.
- **Leave Event** (cần xây mới, tách biệt hoàn toàn): chỉ dành cho participant
  **đã teamless**, mới là hành động đụng `isActive`.

Hệ quả: phải **revert `leaveTeam` về đúng nguyên bản** — bỏ gate `OPEN/SETUP` và
bỏ `deactivate()` khỏi nó; **revert toàn bộ `TeamViewPage.tsx`** về bản gốc (`git
checkout --`) vì mọi thứ thêm vào đó đều dựa trên hiểu sai. Chỉ giữ lại đúng 1 phần
hợp lệ: fix bug JoinRequest/TeamInvite chưa dọn trước khi xoá team (bug có sẵn,
độc lập với tranh cãi isActive — xem mục 4).

### 3.3. Thiết kế đúng, chốt qua thêm 1 vòng Q&A

| Câu hỏi | Trả lời |
|---|---|
| Điều kiện tiên quyết để bấm Leave Event | Phải **đã teamless** (rời team trước qua Leave Team, xong mới thấy nút Leave Event). Nhờ vậy transfer-lead-first không còn liên quan gì tới Leave Event nữa — tới lúc bấm được thì họ đã hết vai trò gì trong team cả. |
| Status event nào cho phép | Ban đầu chốt OPEN+SETUP, sau đơn giản hoá lại còn **chỉ OPEN** — SETUP thì kệ (không ai cần tự rời lúc đó), IN_PROGRESS đã có coordinator lo (mục 2). |
| Đặt nút ở đâu | Thử ý tưởng "ẩn trong event tile ở Leader Console" — không hợp vì Leader Console chỉ hiện khi CÒN team (mâu thuẫn với việc phải teamless mới thấy nút). Đúng chỗ: `EventDetailDrawer.tsx` — drawer "VIEW DETAILS" mở từ event card ở **dashboard teamless** (`NoTeamDashboard`), vốn dĩ chỉ tải event **OPEN** (`GET /api/teams/active-events`) → khớp 100% với gate OPEN-only, không cần mở rộng query gì thêm. |
| Reason bắt buộc? | Không — self-service, giống `leaveTeam`/`requestAccess` không audit log. |
| Leader lẫn Member đều thấy? | Có — nhưng vì đã teamless nên khái niệm leader/member không còn áp dụng ở bước này nữa. |

---

## 4. Bugfix phụ (giữ lại xuyên suốt, không liên quan tranh cãi isActive)

`TeamService.leaveTeam` nhánh solo-leader-rời (team chỉ còn 1 người, rời xong xoá
cả team) gọi thẳng `teamRepository.delete(team)` **không dọn** `JoinRequest`/
`TeamInvite` trước — cả 2 bảng đều có FK tới `Team` **không** `ON DELETE CASCADE`
(`database scripts/seal_schema.sql` dòng ~507, ~523). Nếu team đó đang có lời mời/
join-request pending, lệnh xoá sẽ vỡ FK ngay ở DB. Fix: thêm helper
`deletePendingTeamRequests(Team)` (dọn 2 bảng trên trước khi xoá team), mirror
đúng pattern `LeftoverGroupingService#dissolve(Team)` đã làm.

---

## 5. Triển khai cuối cùng

### Backend

**`TeamService.leaveTeam`** — về đúng nguyên bản (rời team, không đụng `isActive`),
chỉ cộng thêm bugfix mục 4.

**`TeamService.leaveEvent(userId, eventId)`** (mới, tách biệt hoàn toàn khỏi
`leaveTeam`):
- Guard: `event.getStatus() == OPEN`.
- Guard: `user.getIsActive() == true` (không thể "rời" cái đã inactive).
- Guard: `!teamMemberRepository.existsByUser_UserIdAndTeam_Event_EventId(userId, eventId)`
  — phải đang teamless trong event này.
- `deactivate(user)` — set `isActive=false`.
- Không reason, không audit log.

**Controller:** `POST /api/teams/event/{eventId}/leave-event`, `PARTICIPANT`.

### Frontend

- `EventDetailDrawer.tsx`: nút nhỏ "Leave this event" (đỏ mờ, dưới nút chính
  "REGISTER & CREATE TEAM"), chỉ hiện khi `event.status === "OPEN"`. Bọc trong
  `ConfirmDialog` có sẵn (variant danger). Thành công → `patchCurrentUser({is_active:
  false})` (patch local state ngay, tránh phải chờ reload/re-login mới thấy banner
  "REQUEST TO COMPETE" ở dashboard) → toast → đóng drawer.
- `apiClient.ts`: `teamsApi.leaveEvent(eventId)`.
- `TeamViewPage.tsx`: revert 100% về bản gốc trước phiên này.

---

## 6. Kiến trúc cuối — 3 hành động độc lập, không chồng lấn

| | Leave Team (có sẵn) | Leave Event (mới) | Coordinator Remove (mới) |
|---|---|---|---|
| Ai gọi | Participant tự | Participant tự (đã teamless) | Event Coordinator |
| Điều kiện | Có team, chưa `REJECTED/DISQUALIFIED` | Teamless, event `OPEN` | Team `APPROVED`, event `IN_PROGRESS` |
| Đụng `isActive`? | **Không** | **Có** (→`false`) | **Có** (→`false` cho người bị gỡ) |
| Cần reason? | Không | Không | **Bắt buộc** |
| Audit log? | Không | Không | Có (`COORDINATOR_REMOVE_MEMBER`) |
| Hệ quả team | Rời roster; solo-leader rời → xoá team | — (đã teamless từ trước) | Auto-promote leader / disqualify nếu hết người |
| Vị trí UI | `/team/view` — nút LEAVE TEAM | `EventDetailDrawer` (dashboard teamless) | `CoordTeamsPage` → modal chi tiết team, per-member |

---

## 7. Việc chưa làm / để ngỏ

- [ ] Chưa verify end-to-end `leaveEvent` trên UI thật qua trình duyệt (mới verify
      `coordinatorRemoveMember` qua screenshot thật; `leaveEvent` mới chỉ qua
      `tsc`/đọc code + Postman request mới thêm, chưa thật sự bấm trên UI).
- [ ] Trong `EventDetailDrawer`, biến `st` dùng để gate nút đã có sẵn từ trước
      (dùng chung với màu badge status) — không tạo biến mới, nhưng nếu sau này
      thứ tự tính `st` đổi thì nhớ nút Leave Event vẫn phải bám đúng nó.
- [ ] Chưa commit git (toàn bộ đang là working-tree changes trên `develop`).

---

## 8. Danh sách file

**Backend — mới:**
```
dto/request/CoordinatorRemoveMemberRequest.java
```
**Backend — sửa:**
```
service/TeamService.java              (leaveTeam: revert + bugfix dọn FK;
                                        +coordinatorRemoveMember; +leaveEvent;
                                        +deactivate/deletePendingTeamRequests helpers)
controller/TeamController.java        (+coordinator-remove, +leave-event)
test/.../TeamServiceTest.java          (+2 mock: JoinRequestRepository, TeamInviteRepository)
Postman/Postman_Full_Collection.json  (+5 request: coordinator-remove happy path,
                                        coordinator-remove bị Participant gọi expect 403,
                                        leave-event happy path (teamless, OPEN),
                                        leave-event vẫn còn team expect 400)
```

**Frontend — mới:** không có file mới, chỉ sửa.

**Frontend — sửa:**
```
shared/components/TeamDetailModal.tsx                          (+renderMemberAction, backward-compatible)
shared/apiClient.ts                                             (+teamsApi.leaveEvent)
features/teams/CoordTeamsPage.tsx                                (+RemoveMemberModal, +per-member Remove action)
features/dashboard/dashboards/participant/components/EventDetailDrawer.tsx
                                                                  (+"Leave this event" nút + ConfirmDialog)
```
**Frontend — revert về nguyên bản (không còn thay đổi gì so với trước phiên này):**
```
features/teams/TeamViewPage.tsx
```
