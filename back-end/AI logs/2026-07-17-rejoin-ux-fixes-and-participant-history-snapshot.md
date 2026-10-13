# AI LOG — Rejoin UX fixes (e2e bug report) + Participant History one-row-per-entry + Participant History Snapshot (archival) — BE (+ FE nhỏ) — 2026-07-17

> Phiên này gồm 3 mảng việc nối tiếp nhau, tất cả cùng xoay quanh trục
> "participant history phải sống sót qua mọi biến động của Team/TeamMember/
> TeamEventEntry":
>
> 1. **Fix 4 bug UX** user tự phát hiện khi test tay tính năng Rejoin (đã
>    thiết kế+code ở phiên trước, xem
>    `AI_LOG_SEAL_TEAM_EVENTENTRY_MIGRATION_BE_2026-07-15.md` mục 12–13) —
>    Dashboard không nhận biết team dormant, Submit Project không ẩn khi
>    team/account inactive, badge Team Info hiện data cũ, panel duyệt
>    rejoin của coordinator thiếu hẳn UI đúng chuẩn (hover-menu).
> 2. **Sửa `getMyResultHistory` từ 1-dòng-mỗi-team sang 1-dòng-mỗi-`TeamEventEntry`**
>    — lỗ hổng đã được cảnh báo trước từ phiên trước ("chưa fix vì rejoin
>    chưa tồn tại"), giờ tái hiện thật: sau khi rejoin, mùa `COMPLETED` cũ
>    biến mất khỏi trang History/Certificates.
> 3. **Tính năng mới: Participant History Snapshot (archival)** — `TeamMember`
>    bị hard-delete khi rời/bị xoá khỏi team, kéo theo mất sạch lịch sử mùa
>    đã thi. Giải pháp: đóng băng kết quả thành 1 bản JSON tĩnh
>    (`ParticipantEventHistory`) tại 2 thời điểm — lúc rời team và lúc event
>    complete — không còn phụ thuộc dữ liệu sống.
>
> **Cả 3 mảng đều đã code xong, test xong (394/394 backend test xanh), và
> verify trên DB/API/UI thật** — không phải "để lại cho sau". Phát hiện và tự
> sửa **2 bug thật** trong lúc verify (chi tiết ở mục 6.5) — không phải chỉ
> lý thuyết. **Chưa commit** — user chưa yêu cầu.

---

## 1. Bối cảnh

Phiên bắt đầu ngay sau khi tính năng Rejoin (Team/TeamEventEntry migration
"nhánh 1" + Rejoin request/approve) đã được code và verify sơ bộ ở phiên
trước. User tự tay test luồng e2e thật trên browser bằng tài khoản leader
"Federico Valverde" (team PSG) và gửi 5 điểm nhận xét kèm ảnh chụp màn hình,
với yêu cầu tường minh: **"khoan thao tác code"** — chỉ xác nhận + bàn hướng
xử lý trước, không tự ý sửa ngay.

Sau khi thảo luận, xác nhận nguyên nhân, và được user chốt "làm đi", các
phần việc lần lượt triển khai theo đúng chu trình: thảo luận → (Plan Mode
với các việc đủ lớn) → code → test → verify sống trên DB/API/UI thật.

---

## 2. Phần A — Fix 4 bug UX từ báo cáo e2e thủ công của user

### 2.1. Báo cáo gốc (dịch từ tiếng Việt, giữ nguyên ý)

1. Đăng nhập leader từ mùa cũ (team đã dormant) → Dashboard vẫn hiện thông
   tin vòng chung kết/last round rank/next deadline như đang thi, dù team
   chưa active vào event mới; badge track/event vẫn là dữ liệu cũ; **không
   có nút để active tài khoản/team vào mùa mới**.
2. Màn hình Submit Project vẫn truy cập được dù team/account chưa active
   (lẽ ra phải ẩn).
3. Không thấy nút "active team vào event mới" ở trang My Team (**sau khi
   truy vết code + gọi API trực tiếp xác nhận: đây là bug cache trình
   duyệt cũ, code đã đúng từ trước — không cần sửa gì**).
4. Không thấy chỗ duyệt Team Rejoin cho coordinator; nếu có thì nút
   Approve/Reject nên gộp lại thành 1 menu hover-reveal (kèm ảnh mẫu UI),
   khi hover vào dòng nào thì hiện icon menu của dòng đó, sau khi
   approve/reject xong thì giữ nguyên dòng và chỉ đổi badge trạng thái
   (không biến mất khỏi bảng như tab Accounts vẫn làm).

### 2.2. Root cause (đọc code trực tiếp, không đoán)

Giả định sai lầm gốc: `team_id !== null` ⇒ "đang thi tích cực" — đúng cho
`TeamViewPage.tsx` (đã sửa đúng từ phiên trước) nhưng **sai** cho 4 màn
hình khác chưa từng được cập nhật theo tinh thần dormant:

| File | Vấn đề |
|---|---|
| `ExistingTeamDashboard.tsx` | Không có khái niệm dormant, luôn hiện 3 ô GlassStat submission/rank/deadline giả và badge track/event cũ |
| `ParticipantDashboard.tsx` | `team_id !== null` return sớm, bỏ qua hẳn luồng "request reactivate" |
| `TeamSubmitPage.tsx` | Chỉ disable nút Submit, không ẩn cả trang/nav |
| `DashboardLayout.tsx` | Mục nav "Submit Project" hiện vô điều kiện khi `team_id !== null` |
| `TeamRejoinRequestsPanel.tsx` (chưa tồn tại UI đúng) | Chỉ có nút Approve/Reject luôn hiện, không theo pattern hover-menu đã có sẵn trong `CoordAccountsPage.tsx` (tab Accounts) |

Phát hiện thêm 1 bug tiềm ẩn **ngoài phạm vi hỏi** trong lúc truy vết fix
#1: `NotificationProvider.tsx` xử lý sự kiện `PARTICIPATION_ACCESS_APPROVED`
bằng cách set cứng `team_id: null` — dưới nghiệp vụ rejoin mới, việc này sẽ
**xoá nhầm** team dormant của leader ngay sau khi họ tự kích hoạt lại tài
khoản, đúng lúc lẽ ra phải cho họ thấy team cũ để request rejoin. Sửa bằng
cách gọi lại `refreshTeamContext()` thay vì set cứng.

### 2.3. Code

- **`AuthProvider.tsx`**: `AuthUser` thêm field `team_dormant: boolean`;
  `fetchTeamContext()` tính `teamDormant = status==='DISQUALIFIED' ||
  eventStatus==='COMPLETED'`, trả về cùng `teamId`/`isLeader`; cả 3 nơi gọi
  (session-restore, `login()`, `refreshTeamContext()`) đều gán thêm field
  này; `clearTeam()` reset về `false`.
- **`NotificationProvider.tsx`**: đổi
  `patchCurrentUser({is_active:true, team_id:null, is_leader:false})`
  thành `patchCurrentUser({is_active:true}); refreshTeamContext();`.
- **`DashboardLayout.tsx`**: `buildNav` nhận thêm `teamDormant`, ẩn mục
  "Submit Project" khi dormant.
- **`ParticipantDashboard.tsx`**: nhánh `team_id !== null` giờ luôn render
  `ExistingTeamDashboard` kèm props `inactive`/`requestingActive`/
  `activeRequested`/`onRequestActive`, cộng `ConfirmDialog` "Request to
  compete this season?" y hệt pattern đã có ở `NoTeamDashboard`.
- **`ExistingTeamDashboard.tsx`**: thêm `isDormant`, badge "SEASON ENDED",
  banner reactivate, thay 3 ô GlassStat bằng 1 dòng thông báo + nút "My
  Team" khi dormant, ẩn `ParticipantProblemCard`/nút Submit khi dormant.
- **`TeamSubmitPage.tsx`**: tính `teamDormant`, bọc toàn bộ phần thân
  tương tác trong điều kiện, hiện thông báo khoá thay thế khi dormant.
- **`TeamRejoinRequestsPanel.tsx`** (viết lại toàn bộ): thay 2 nút
  Approve/Reject luôn hiện bằng `PixelMenu` hover-reveal (class
  `row-actionable`/`row-action` — pattern CSS đã có sẵn, dùng lại nguyên
  vẹn từ tab Accounts), thêm `statusBadgeColor()`, `resolveRequest` giờ
  **patch dòng tại chỗ** (`setRequests(prev => prev.map(...))`) thay vì
  xoá khỏi danh sách — khớp đúng yêu cầu "team thì giữ dòng + đổi badge,
  account thì vẫn xoá dòng như cũ" (user tự làm rõ điểm này qua 1 lượt hỏi
  đáp riêng).

### 2.4. Verify

- `npx tsc -p tsconfig.app.json --noEmit`: sạch, chỉ còn đúng 1 lỗi có sẵn
  từ trước không liên quan (`CoordJudgesPage.tsx`).
- Playwright end-to-end thật (script tại
  `scratchpad/pw-verify/verify8.mjs`, `verify9.mjs`, `verify10.mjs`), dùng
  3 context (leader p20, coordinator, kiểm tra lại p20): request reactivate
  → coordinator approve request → leader vào My Team thấy banner dormant +
  nút "Request to rejoin" → gửi request → coordinator vào tab "Team
  Rejoin", hover dòng PSG thấy menu hiện ra, bấm Approve → badge "APPROVED"
  thay chỗ menu, dòng không biến mất → leader load lại thấy team đã về
  trạng thái editable bình thường cho mùa mới.
- Phát hiện + xử lý 1 "known gotcha" của `PixelMenu`: component tự đóng
  khi có bất kỳ scroll nào (hành vi cố ý, đã ghi chú sẵn trong code) —
  `page.screenshot({fullPage:true})` tự cuộn trang để chụp full-page, vô
  tình đóng menu đang mở giữa bước kiểm tra và bước click. Sửa bằng cách
  đổi thứ tự: click menu item **trước**, chụp full-page screenshot **sau**.

---

## 3. Phần B — Fix badge "Team Info" hiện dữ liệu cũ khi team dormant

### 3.1. Vấn đề

Sau khi Phần A xong và user tự test lại, phát hiện thêm 1 chỗ sót: khối
"Team Info" (4 badge TEAM/TRACK/EVENT/CURRENT ROUND) trong
`ExistingTeamDashboard.tsx` **là một khối `PixelCard` riêng, tách biệt**
với 3 ô GlassStat round-status đã sửa ở Phần A — khối này chưa từng được
gate theo `isDormant`, nên vẫn hiện nguyên `team.trackName`/`team.eventName`
là dữ liệu của mùa **cũ** (COMPLETED), dù badge "SEASON ENDED" và banner
dormant phía trên đã đúng.

### 3.2. Fix

Bọc toàn bộ phần thân của khối "Team Info" (banner PENDING, lưới 4
`InfoRow`, `ParticipantProblemCard`) trong điều kiện `isDormant`; khi
dormant, thay bằng 1 dòng text: *"Track, event, and round details are from
the last completed season and stay hidden until this team rejoins an
active one."* Nút "MANAGE TEAM" (leader) giữ nguyên bên ngoài điều kiện
này để vẫn dẫn được vào My Team.

`npx tsc -p tsconfig.app.json --noEmit`: sạch (cùng 1 lỗi có sẵn không liên
quan như trên).

---

## 4. Phần C — Thảo luận: Account (`isActive`) vs Team (`TeamEventEntry`) có nên đồng nhất kiến trúc?

Câu hỏi user đặt ra: `User` dùng 1 cột boolean `isActive` để biểu diễn
"đang thi mùa nào", trong khi `Team` đã có hẳn 1 weak entity
`TeamEventEntry` — có nên đồng nhất 2 cơ chế này không, vì bản chất nghiệp
vụ "phải xuyên suốt nhiều mùa, không chỉ 1 mùa" là giống nhau?

**Kết luận (không code, chỉ phân tích + khuyến nghị)**: **không nên** đồng
nhất.

- `TeamEventEntry` tồn tại vì Team có **dữ liệu thật gắn theo từng mùa**
  (track, status APPROVED/DISQUALIFIED, thứ hạng round) — cần nhiều dòng
  lịch sử vì mỗi lần tham gia là 1 bộ dữ liệu khác nhau.
- `User.isActive` **không phải nơi lưu lịch sử nghiệp vụ** — nó chỉ là 1
  cái cổng (gate) "tài khoản này có được hoạt động mùa hiện tại không".
  Vai trò/kết quả thi đấu của 1 user đã được ghi nhận đầy đủ thông qua
  `TeamMember → TeamEventEntry` (đi qua Team) rồi — tách riêng
  `UserEventEntry` sẽ phải làm lại y hệt bài toán "resolve current entry"
  đã làm cho Team, nhưng gần như không có gì thêm để lưu trong mỗi dòng.
- Đối chiếu với quyết định cũ đã có sẵn trong memory
  (`participant-lifecycle-inactive-semantics`, 2026-06-12/07-12): đã từng
  chốt giữ `is_active` dạng boolean cho User — không phải bị bỏ sót.
- **Chỗ thực sự đáng sửa** không phải chuyện kiến trúc User, mà là món nợ
  kỹ thuật cụ thể đã ghi chú sẵn từ phiên migration trước: `getMyHistory`/
  `getMyResultHistory` 1-dòng-mỗi-`TeamMember` thay vì 1-dòng-mỗi-entry —
  → dẫn thẳng sang Phần D.

---

## 5. Phần D — `getMyResultHistory`: từ 1-dòng-mỗi-`TeamMember` sang 1-dòng-mỗi-`TeamEventEntry`

### 5.1. Điều tra (đọc code trực tiếp trước khi lên plan)

Có **2 endpoint** trông giống nhau nhưng thực ra phục vụ 2 mục đích khác
hẳn nhau — điểm mấu chốt suýt bị hiểu nhầm cả 2 đều lỗi như nhau:

| Endpoint | Method BE | Dùng bởi FE | Có nên đổi? |
|---|---|---|---|
| `GET /api/teams/my/history` | `getMyTeamHistory` → `mapToMyTeamResponse(TeamMember)` | `TeamSubmitPage.tsx` — "team nào tôi có thể submit ngay bây giờ" | **Không** — `requireCurrentEntry`-mỗi-team hiện tại đã đúng, vì không bao giờ nên cho chọn 1 mùa đã COMPLETED làm đích submit |
| `GET /api/teams/my/result-history` | `getMyResultHistory` → `mapToTeamHistoryResponse(TeamMember)` | `HistoryPage.tsx` (trang Certificates), `LeaderboardPage.tsx` (lọc event) | **Có** — đây mới thực sự là "mọi mùa tôi từng thi", cần 1-dòng-mỗi-entry |

Phát hiện thêm 1 bug **chưa từng được ghi nhận trước đó**: cả 2 endpoint
(và cả 1 method dead-code `getMyHistory()` — tồn tại nhưng **không hề được
gọi từ bất kỳ controller nào**, xác nhận qua grep toàn repo) đều query
submissions/round-results **theo team-wide**, không lọc theo event:
`submissionRepository.findAllByTeam_TeamId(...)`,
`roundResultRepository.findAllByTeamIdOrderByRoundOrder(...)` — nếu chỉ
tách 1-dòng-mỗi-entry mà không sửa luôn 2 chỗ này, mỗi dòng lịch sử (mỗi
mùa) sẽ hiện lẫn dữ liệu của TẤT CẢ các mùa khác của team đó.

`prizeRepository.findFirstByTeam_TeamIdAndAwardedAtIsNotNullOrderByRankPositionAsc`
cũng tương tự — lấy giải **đầu tiên** của team qua mọi mùa, không lọc theo
event.

### 5.2. Thiết kế đã duyệt (Plan Mode, `ExitPlanMode` được approve)

- `getMyResultHistory`: với mỗi `TeamMember`, lấy **toàn bộ**
  `TeamEventEntry` của team đó, sort theo `createdAt`, lọc theo quy tắc
  "roster-join-timing": loại bỏ 1 entry nếu tồn tại 1 entry **kế tiếp**
  được tạo trước hoặc đúng lúc member này join (member chưa từng có mặt ở
  mùa đó).
- `mapToTeamHistoryResponse` (đổi tên nội bộ khi chuyển sang service mới ở
  Phần E thành `buildHistoryView`) nhận thêm tham số `TeamEventEntry`
  tường minh thay vì tự resolve `requireCurrentEntry` bên trong; lọc lại
  submissions/round-results/prize theo đúng `event.getEventId()` của entry
  đang xử lý.
- Xoá hẳn `getMyHistory()` (dead code, đã bị `getMyResultHistory` sau khi
  sửa thay thế hoàn toàn).
- **Không đụng** `getMyTeamHistory`/`TeamSubmitPage.tsx`.
- Cập nhật lại javadoc `requireCurrentEntry` (đang nói sai "rejoin chưa
  được xây" — rejoin đã xong từ phiên trước).

### 5.3. Bug tự phát hiện khi code (không phải lý thuyết)

Rule "roster-join-timing" viết lần đầu trong plan (`entry.createdAt >=
membership.joinedAt`) **sai chiều** — unit test tự viết bắt lỗi ngay
(`expected: <1> but was: <0>`). Rule đúng phải dựa theo **entry kế tiếp**:

```java
for (int i = 0; i < entries.size(); i++) {
    TeamEventEntry entry = entries.get(i);
    LocalDateTime nextEntryCreatedAt = (i + 1 < entries.size()) ? entries.get(i + 1).getCreatedAt() : null;
    if (nextEntryCreatedAt != null && !membership.getJoinedAt().isBefore(nextEntryCreatedAt)) {
        continue; // member join sau khi mùa kế tiếp đã bắt đầu -> chưa từng có mặt ở mùa này
    }
    ...
}
```

### 5.4. Test

3 test mới trong `TeamServiceTest`:
`getMyResultHistory_shouldReturnOneRowPerEntry_scopedToItsOwnEvent_whenTeamHasRejoined`,
`getMyResultHistory_shouldExcludeEntry_whenMemberJoinedAfterThatEntryWasCreated`,
`getMyResultHistory_shouldReturnSingleRow_whenTeamHasOnlyOneEntry` — cả 3
xanh sau khi sửa rule.

`./mvnw -o test`: **383/383 pass**.

### 5.5. Verify sống

- Live smoke test qua curl (đăng nhập `p20@fpt.edu.vn`, leader team PSG đã
  rejoin từ phiên trước, có sẵn 2 `TeamEventEntry`): `GET
  /api/teams/my/result-history` trả về đúng 2 dòng — Fall 2026 (OPEN) và
  Summer 2026 (COMPLETED, track "AI Solution") — cả 2 đều đúng dữ liệu
  riêng của mùa đó.
- Playwright: trang History hiện đúng 2 card tách biệt (trước đây chỉ
  thấy 1).

---

## 6. Phần E — Tính năng mới: Participant History Snapshot (archival)

### 6.1. Vấn đề gốc

User đặt câu hỏi: nếu 1 participant **rời khỏi team** (tự rời, bị leader
xoá, hoặc bị coordinator xoá), họ có còn xem được lịch sử mùa mình từng
tham gia không? Trả lời: **không** — cả 4 chỗ hard-delete `TeamMember`
(`removeMember`, `leaveTeam` — cả 2 nhánh, `coordinatorRemoveMember`) xoá
thẳng row, nghĩa là mất luôn mọi liên hệ tới lịch sử team đó, kể cả mùa đã
thi thật.

### 6.2. Hướng bị loại: soft-delete `TeamMember`

Thêm cột `leftAt`/status thay vì xoá hẳn — bị loại vì phạm vi ảnh hưởng quá
rộng: gần như mọi query "roster hiện tại" trong toàn bộ codebase (kiểm tra
leader, điều kiện invite, `notifyTeamMembers`, roster hiển thị
coordinator/mentor/judge, điều kiện track assignment...) đang ngầm định
`TeamMember` tồn tại = đang ở team; sửa hết các chỗ này rủi ro regression
rất cao. Ngoài ra unique constraint `(team_id, user_id)` sẽ chặn việc rời
rồi được mời lại chính team đó sau này.

### 6.3. Hướng được chọn: đóng băng thành JSON tĩnh, không phải query sống

User làm rõ ý: lịch sử là của **từng participant riêng lẻ**, độc lập theo
mùa (mùa này ở team A, mùa sau có thể ở team khác hoặc không ở team nào) —
và một khi mùa đã COMPLETED + có kết quả/certificate, dòng lịch sử đó nên
là **1 bản export tĩnh (text/JSON)**, không phải tham chiếu API/entity
sống — để dù sau này team/roster đổi thế nào, dữ liệu cũ vẫn nguyên vẹn.

Điều tra trước khi chốt thời điểm "đóng băng" (đọc code, không đoán):

- `RoundResultService.publishResults`: **không hề gate theo `event.status`**
  ở cả backend lẫn controller — publish được bất cứ lúc nào round đã
  FINALIZED, thường ngay khi event còn IN_PROGRESS.
- `PrizeService.announce`: cũng **không gate theo `event.status`** ở
  backend (`requireEvent` chỉ check tồn tại); FE chỉ yêu cầu
  `finalRound.status === "FINALIZED"`, độc lập hoàn toàn với status của
  event; `CoordPrizesPage.tsx` còn ưu tiên hiện event IN_PROGRESS trước
  COMPLETED.
- Kết luận: "khoá ở complete" chỉ là **thói quen quy trình** (coordinator
  luôn finalize → publish → announce → **rồi mới** complete), không phải
  rule cứng — nên chỉ cần **1 điểm chụp duy nhất tại `completeEvent()`** là
  đủ cho trường hợp "ở lại tới cuối mùa". Trường hợp coordinator quên/làm
  sai thứ tự đã có sẵn lối thoát: `reopenEvent()` (COMPLETED → IN_PROGRESS)
  — chỉ cần thiết kế snapshot kiểu **upsert**, reopen → làm nốt việc còn
  thiếu → complete lại → snapshot tự refresh, không cần thêm hook ở
  `RoundResultService`/`PrizeService`.

### 6.4. Thiết kế đã duyệt (Plan Mode, `ExitPlanMode` được approve)

**2 điểm chụp, cùng ghi vào 1 bảng, khoá duy nhất `(user_id, event_id)`,
upsert:**

1. **Lúc rời/bị xoá khỏi team** — `removeMember`, `leaveTeam` (cả 2
   nhánh), `coordinatorRemoveMember` — chụp **ngay trước** khi xoá
   `TeamMember`.
2. **Lúc `completeEvent()`** — chụp mọi participant còn sống trên team tại
   thời điểm đó, tái dùng đúng hình dạng vòng lặp có sẵn của
   `lockCompletedEventParticipantsReadOnly`.

**Entity mới `ParticipantEventHistory`** — cố tình **không phải tham chiếu
sống**: không có FK tới `Team`; `eventId` là số nguyên thường (chỉ dùng để
so sánh với status hiện tại của event, không dùng để hiển thị); toàn bộ dữ
liệu hiển thị (tên team, track, roster, round, submission, giải thưởng)
nằm trong **1 cột JSON** `resultJson` — serialize nguyên `TeamHistoryResponse`
(đúng DTO endpoint live đang trả về), nên FE **không cần đổi gì**.

```java
@Entity
@Table(name = "ParticipantEventHistory", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "event_id"})
})
class ParticipantEventHistory {
    Integer id;
    @ManyToOne User user;      // User không bao giờ bị xoá -> FK an toàn
    Integer eventId;           // KHÔNG phải FK
    String snapshotReason;     // COMPLETED | LEFT_TEAM | REMOVED_BY_LEADER | REMOVED_BY_COORDINATOR
    LocalDateTime snapshotAt;
    String resultJson;         // TeamHistoryResponse serialize nguyên
}
```

**Service mới `ParticipantHistorySnapshotService`** — hướng phụ thuộc
1 chiều (`TeamService`/`HackathonEventService` → service này, không vòng
lại), tránh circular dependency:

- `buildHistoryView(membership, entry)` — **chuyển nguyên** từ
  `TeamService.mapToTeamHistoryResponse` (không viết lại logic, giữ đúng
  fix event-scoping vừa làm ở Phần D) — dùng chung cho cả đường live lẫn
  đường snapshot, 1 nguồn sự thật duy nhất.
- `snapshotDeparture(membership, reason)` — chụp mọi entry liên quan (xem
  6.5 — đây chính là chỗ có bug tự tìm ra).
- `snapshotEventCompletion(eventId)` — chụp mọi member còn sống của mọi
  entry thuộc event đó.
- `getSnapshotsForUser(userId)` → `Map<eventId, TeamHistoryResponse>`.
- `backfillCompletedEvents()` — quét toàn bộ event đã COMPLETED **từ
  trước khi tính năng này tồn tại** (chưa có snapshot), chạy lại
  `snapshotEventCompletion` cho từng cái. Expose qua
  `POST /api/admin/participant-history/backfill` (`SYSTEM_ADMIN`, không có
  nút FE — chạy tay khi cần).

**`TeamService.getMyResultHistory`** — merge 2 nguồn theo từng `eventId`:
còn sống (live) **và** `eventStatus` hiện tại **không phải** COMPLETED →
ưu tiên live (để không hiện snapshot cũ trong lúc event bị reopen sửa
tạm); ngược lại nếu có snapshot → dùng snapshot; nếu COMPLETED mà chưa có
snapshot (dữ liệu cũ trước khi tính năng này tồn tại, chưa backfill) → vẫn
fallback về live như cũ, không vỡ gì.

### 6.5. 2 bug tự phát hiện khi test/verify (không phải chỉ lý thuyết)

**Bug 1 — thiếu bean `ObjectMapper`.** Full test suite (`HackathonApplicationTests`,
test load context Spring thật) fail ngay: app này **không có bean
`ObjectMapper` tự động** (`NoSuchBeanDefinitionException`) — sai với giả
định ban đầu trong plan. Sửa: service tự khởi tạo `ObjectMapper` riêng có
đăng ký `JavaTimeModule` (`TeamHistoryResponse` có field `LocalDateTime`,
`new ObjectMapper()` trơn sẽ throw khi serialize) — giống cách
`AuditLogService` đã làm (`new ObjectMapper()`), chỉ khác là có thêm
module cho date/time.

**Bug 2 — snapshot lúc rời team chỉ chụp đúng 1 entry (nghiêm trọng hơn,
phát hiện khi test SỐNG trên DB thật, không phải unit test).** Thiết kế
ban đầu: `snapshotDeparture(membership, entry, reason)` chỉ chụp entry
**hiện tại** (mới nhất) của team. Nhưng `TeamMember` dùng **chung** cho
MỌI mùa của team đó (không tách theo season) — xoá row này đi thì member
mất quyền truy cập **toàn bộ** lịch sử team, không chỉ mùa hiện tại. Test
sống thật: xoá `p6@fpt.edu.vn` khỏi team Barcelona (team có 2 mùa: Summer
COMPLETED + Fall OPEN) → chỉ thấy 1 dòng `ParticipantEventHistory` được
tạo (event 2) thay vì 2 — xác nhận đúng bug, không phải nghi ngờ suông.

Sửa bằng cách **tách riêng logic resolve "entry nào member này chịu trách
nhiệm"** (`resolveEntriesForMembership`) thành method dùng chung giữa
`computeLiveHistory` (đường live, Phần D) và `snapshotDeparture` (đường
archival) — đảm bảo 2 đường luôn thấy đúng **cùng 1 tập** season, không bao
giờ lệch nhau:

```java
List<TeamEventEntry> resolveEntriesForMembership(TeamMember membership) {
    // sort ascending theo createdAt, áp đúng rule "next entry" đã chốt ở Phần D
    // trả về newest-first
}

public void snapshotDeparture(TeamMember membership, String reason) {
    for (TeamEventEntry entry : resolveEntriesForMembership(membership)) {
        upsert(membership.getUser(), entry.getEvent().getEventId(), reason,
               buildHistoryView(membership, entry));
    }
}
```

Verify lại sống: xoá `p6` khỏi Barcelona → **2 dòng** `ParticipantEventHistory`
xuất hiện đúng (event 1 + event 2, `REMOVED_BY_LEADER`) → `p6` vẫn thấy đủ
2 mùa qua API dù `TeamMember` đã bị xoá hẳn.

### 6.6. Test

- **`ParticipantHistorySnapshotServiceTest`** (mới, 5 test): upsert
  insert-rồi-overwrite; `snapshotEventCompletion` chụp đúng mọi member của
  mọi entry trong event; round-trip JSON đầy đủ field `LocalDateTime`
  (bắt được chính xác lỗi ObjectMapper nếu tái diễn); `backfillCompletedEvents`
  chỉ xử lý event COMPLETED; và test regression riêng cho Bug 2 —
  `snapshotDeparture_shouldCoverEveryEntry_notJustTheNewestOne`.
- **`TeamServiceTest`**: cập nhật mock (`participantHistorySnapshotService`
  thay cho `submissionRepository`/`prizeRepository` đã không còn field
  trực tiếp trên `TeamService`); thêm test merge-priority (live thắng khi
  chưa COMPLETED, snapshot thắng khi đã COMPLETED, snapshot dùng khi không
  còn entry sống); thêm `verify(...).snapshotDeparture(...)` vào các test
  `removeMember`/`leaveTeam` đã có; thêm test mới hoàn toàn cho
  `coordinatorRemoveMember` (**trước đây chưa từng có test nào cho method
  này**).
- **`HackathonEventServiceTest`**: file này trước giờ chỉ test validation
  lịch event, chưa từng test `completeEvent()`/`reopenEvent()` — phải bổ
  sung hẳn 3 mock còn thiếu (`TeamMemberRepository`, `UserRepository`,
  `JudgeAssignmentRepository`) mới chạy được, cộng 2 test mới
  (`completeEvent_shouldSnapshotParticipantHistory_whenTransitioningFromInProgress`,
  `completeEvent_shouldThrowBadRequest_whenEventIsNotInProgress`).

`./mvnw -o test`: **394/394 pass** (bao gồm cả `HackathonApplicationTests`
load context Spring thật, phát hiện Bug 1).

### 6.7. Verify sống trên DB/API/UI thật

Toàn bộ chạy trên backend build mới (kill process cũ PID cache mã cũ,
`spring-boot:run` lại — `spring.jpa.hibernate.ddl-auto=update` nên bảng
`ParticipantEventHistory` tự sinh, xác nhận qua `DESCRIBE` trực tiếp):

1. **Departure snapshot + đủ mọi entry**: leader `p4@fpt.edu.vn` xoá
   `p6@fpt.edu.vn` khỏi team Barcelona (`DELETE
   /api/teams/2/members/17`) → DB có 2 row `ParticipantEventHistory`
   (event 1 + 2, `REMOVED_BY_LEADER`) → `GET /api/teams/my/result-history`
   của `p6` vẫn trả đủ 2 mùa, roster snapshot đúng-tại-thời-điểm-rời (bao
   gồm cả chính `p6`).
2. **Backfill**: đăng nhập `admin@fpt.edu.vn` (SYSTEM_ADMIN), gọi
   `POST /api/admin/participant-history/backfill` → xử lý 1 event COMPLETED
   → 39 row mới cho mọi participant còn sống của event đó, **không đụng**
   2 row đã có sẵn của `p6`.
3. **Round-trip reopen → complete (upsert)**: `POST /api/events/1/reopen`
   → `p4`'s `result-history` cho event 1 đổi sang `eventStatus: IN_PROGRESS`
   (đúng — ưu tiên live) → `POST /api/events/1/complete` lại → DB xác nhận
   **cùng 1 row id** được cập nhật `snapshot_at` mới, không tạo dòng
   trùng.
4. **Playwright FE**: đăng nhập `p6@fpt.edu.vn` (giờ đã teamless), trang
   History hiện đúng 2 card (Fall 2026 OPEN, Summer 2026 COMPLETED kèm nút
   CERTIFICATE) — khớp hoàn toàn dữ liệu API.

---

## 7. Danh sách file thay đổi

**Backend — mới (Phần E):**
```
entity/ParticipantEventHistory.java
repository/ParticipantEventHistoryRepository.java
service/ParticipantHistorySnapshotService.java
controller/ParticipantHistoryController.java
test/.../service/ParticipantHistorySnapshotServiceTest.java
```

**Backend — sửa:**
```
service/TeamService.java            (Phần D: getMyResultHistory, computeLiveHistory,
                                      buildHistoryView chuyển ra service mới;
                                      Phần E: 3 điểm gọi snapshotDeparture)
service/HackathonEventService.java  (Phần E: hook snapshotEventCompletion trong completeEvent)
database scripts/seal_schema.sql    (Phần E: +bảng ParticipantEventHistory)
test/.../service/TeamServiceTest.java              (Phần D+E: nhiều test mới/sửa)
test/.../service/HackathonEventServiceTest.java     (Phần E: +3 mock, +2 test)
```

**Frontend — mới/sửa (Phần A):**
```
features/users/TeamRejoinRequestsPanel.tsx   (mới, viết lại toàn bộ)
app/providers/AuthProvider.tsx
app/providers/NotificationProvider.tsx
app/layouts/DashboardLayout.tsx
features/dashboard/dashboards/ParticipantDashboard.tsx
features/dashboard/dashboards/participant/screens/ExistingTeamDashboard.tsx  (Phần A + Phần B)
features/submissions/TeamSubmitPage.tsx
```

**Không đụng gì (cố tình, đã xác nhận):** `HistoryPage.tsx`,
`CertificateModal.tsx`, `apiClient.ts` (`TeamHistoryEntry`/
`getMyResultHistory`), `features/teams/TeamSubmitPage.tsx`'s
`getMyHistory()`/`/api/teams/my/history` — endpoint "current entry" vẫn
giữ nguyên hành vi cũ vì đúng cho mục đích của nó.

---

## 8. Kết quả test cuối cùng

```
./mvnw -o test
Tests run: 394, Failures: 0, Errors: 0 — BUILD SUCCESS
```

`npx tsc -p tsconfig.app.json --noEmit`: sạch (1 lỗi có sẵn từ trước,
không liên quan, `CoordJudgesPage.tsx`).

---

## 9. Còn lại / cố tình chưa làm

- **`TeamMember` hard-delete vẫn không lưu "khoảng thời gian" ở team** —
  nếu member rời rồi sau đó có publish/announce muộn, snapshot của họ
  **không** được cập nhật lại (đóng băng đúng-tại-thời-điểm-rời, cố tình,
  đã nói rõ với user là quyết định nghiêng về hướng này chứ không tự động
  chọn).
- **Chưa commit** — toàn bộ thay đổi (Phần A→E, cộng cả Rejoin feature gốc
  từ phiên trước) vẫn nằm ở working tree, `git status` xác nhận.
- Gap cũ đã biết từ trước (`TeamMember` bị xoá vẫn mất quyền vào "roster
  hiện tại" của coordinator/mentor xem — khác với "participant tự xem
  lịch sử của mình" mà Phần E đã giải quyết) — **ngoài phạm vi** yêu cầu
  lần này, chưa đụng tới.
