# AI LOG — Team/TeamEventEntry Migration ("nhánh 1" của Team entity season-independence) — BE — 2026-07-15

> Tách `Team` từ một row khoá cứng vào 1 event (`event_id NOT NULL`, bất biến)
> thành một **identity ổn định tồn tại xuyên suốt nhiều mùa giải** — mirror
> đúng cách `User`/`UserEventRole` đã làm. Entity mới `TeamEventEntry` gánh
> toàn bộ dữ kiện theo-mùa (event, track, status, disqualified...). Đây là
> "nhánh 1" — nhánh lớn nhất và cuối cùng trong 3 nhánh của sáng kiến
> season-independence (nhánh 2 "leave event/coordinator removal" và nhánh 3
> "leftover-grouping manual override" đã xong từ trước, xem
> `AI_LOG_SEAL_LEAVE_EVENT_AND_COORDINATOR_REMOVAL_FULLSTACK_2026-07-15.md`).
> Bắt đầu tiếp trên `TrangNHK-leave-event-coordinator-removal`, sau đó tách
> riêng thành nhánh `TrangNHK-team-event-entry-migration` (từ nhánh trên) và
> **đã commit** thành 6 commit conventional (xem mục 9). Phạm vi: **chỉ
> Backend** — 0 file frontend bị đụng (đã tự kiểm bằng `git status`), vì
> thiết kế cố tình giữ nguyên toàn bộ API contract hiện có. **Đã reset DB dev
> + smoke test end-to-end qua API thật + query DB thật** (mục 10) — không
> còn là "chưa làm". Sau đó, **tính năng Rejoin** (leader request mở lại team
> đã dormant → coordinator duyệt → 1 `TeamEventEntry` mới) — thứ duy nhất
> migration cố tình để ngỏ — cũng đã được thiết kế + code (BE+FE) + test
> (380 test BE, tsc sạch FE) + smoke-test cả API/DB thật lẫn UI thật qua
> Playwright, trong **cùng phiên làm việc này** (xem mục 12–13).

---

## 1. Bối cảnh

Phiên trước (ghi trong memory `team-entity-season-independence`) đã chốt thiết
kế nhưng **chưa code**: `Team.event_id NOT NULL` khiến một team chỉ có thể
thi đúng 1 mùa duy nhất trong lịch sử — nếu muốn thi lại mùa sau, hệ thống
buộc phải tạo ra một `Team` hoàn toàn mới, không có liên hệ gì với team cũ.
User đánh giá việc này "không hợp lý" và yêu cầu `Team` phải tồn tại xuyên
mùa như `User` đã làm.

Phiên này bắt đầu bằng lệnh "tiếp tục nhánh 1", đọc lại memory, sau đó dùng
**Plan Mode** để khảo sát toàn bộ codebase (2 Explore agent chạy song song +
tự đọc trực tiếp các file lõi) trước khi viết bất kỳ dòng code nào, vì quy mô
đã được cảnh báo trước là rất lớn (ước tính ~12 file trong memory, thực tế khi
rà kỹ ra tới **~28 file** có chạm `.getEvent()`/`.getTrack()`/`.getStatus()`
trên `Team`).

---

## 2. Thiết kế / các quyết định quan trọng chốt trong phiên này

Thiết kế khung (Team thin identity + TeamEventEntry) đã có sẵn từ memory.
Phiên này phải tự quyết thêm mấy điểm mà memory chưa nói rõ:

| Vấn đề | Quyết định | Vì sao |
|---|---|---|
| Route coordinator/leader hiện tại chỉ có `teamId`, không có `eventId` (`PUT /{teamId}/approve`, `/reject`, `/disqualify`, `/track-assignment`, `/members/{userId}/coordinator-remove`, `GET /{teamId}`, `PUT /{teamId}/track`) | **Không đổi API, không đổi FE.** `TeamService` tự resolve "entry hiện tại" = `TeamEventEntry` mới nhất của team (`findTopByTeam_TeamIdOrderByIdDesc`) | Một team chỉ đang "mid-review" ở đúng 1 mùa tại 1 thời điểm trong thực tế — rejoin (thứ duy nhất có thể tạo entry thứ 2 đang sống) chưa được xây. Nhờ vậy **0 thay đổi hợp đồng API, 0 thay đổi frontend** |
| `TeamInvite`/`JoinRequest` trước giờ suy ra "thuộc event nào" qua `team.getEvent()` — giờ `Team` không còn field đó | Thêm cột `event_id` (Integer thường, không FK-relation) vào cả 2 entity, set lúc tạo dựa theo entry hiện tại của team | Mirror đúng pattern `UserEventRole.eventId` đã có sẵn trong codebase — không cần thêm quan hệ JPA mới |
| `Team.isActive` nên hoạt động thế nào? | Mirror y hệt `User.isActive`: tự `false` khi event hoàn thành (`lockCompletedEventParticipantsReadOnly`, mở rộng thêm để loop cả team), tự `true` lại khi event reopen (`reactivateEventParticipants`, mở rộng tương tự) — **trừ** `DISQUALIFIED` luôn set `false` ngay lập tức bất kể event đang ở phase nào (theo đúng refinement #2 đã chốt trong memory), và reopen **không được** hồi sinh 1 team đã bị disqualify | Diễn giải lại đúng câu chữ trong memory: "isActive nên flip false ngay lập tức khi DISQUALIFIED, **không đợi** event complete" — nghĩa là hành vi mặc định (không disqualify) vốn dĩ ĐÃ PHẢI đợi event complete mới flip, y hệt User |
| "Team rỗng" (solo leader rời đi) xoá gì? | Chỉ xoá `TeamEventEntry` của mùa hiện tại, **không bao giờ** xoá row `Team` | Đã chốt trong memory (refinement #1) — Team giờ có thể đã từng thi các mùa trước, xoá cả `Team` sẽ xoá luôn lịch sử |
| Roster (`TeamMember`) có cần tách theo mùa không? | **Không** — giữ nguyên gắn thẳng vào `Team`, không đổi gì | Đây chính là điều làm rejoin sau này "trivial" (memory đã nói) — roster tồn tại độc lập với entry |
| `getMyHistory`/`getMyResultHistory` sẽ hơi "quá rộng" một khi rejoin ra đời (member join sau vẫn thấy được mùa cũ team từng thi trước khi họ join, vì `TeamMember` không có ranh giới theo mùa) | Biết trước, **không sửa ngay** — chỉ để lại comment cảnh báo trong code, vì rejoin chưa tồn tại nên hiện tại mỗi Team luôn chỉ có đúng 1 entry, bug này chưa thể xảy ra trên thực tế | Tránh code "phòng hờ" cho tính năng chưa được xây (matching "không thiết kế cho yêu cầu giả định") |

Phiên làm việc dùng **Plan Mode** đầy đủ (EnterPlanMode → 2 Explore agent
song song khảo sát toàn bộ service/repository/entity layer → đọc trực tiếp
`Team.java`, `User.java`/`UserEventRole.java` (mẫu tham chiếu),
`TeamService.java`, `TeamRepository.java`, `TeamMemberRepository.java`,
`LeftoverGroupingService.java`, `TeamController.java`,
`JoinRequestController.java`, `TeamInviteController.java`, các đoạn
completion/reactivation trong `HackathonEventService.java`, entity
`TeamInvite`/`JoinRequest`, schema SQL, seed SQL → viết kế hoạch chi tiết ra
file plan → `ExitPlanMode` xin duyệt → user duyệt → mới bắt đầu code).

---

## 3. Triển khai — theo 5 bước đã lên kế hoạch

### Bước 1 — Entity & Repository layer

- `entity/Team.java`: bỏ `event`, `track`, `status`, `disqualifiedReason`,
  `disqualifiedAt`; thêm `isActive` (Boolean, default `true`).
- **Entity mới** `entity/TeamEventEntry.java`: `id`, `team` (ManyToOne),
  `event` (ManyToOne), `track` (ManyToOne, nullable), `status` (default
  `PENDING`), `disqualifiedReason`, `disqualifiedAt`, `createdAt`. Unique
  `(team_id, event_id)`.
- **Repository mới** `repository/TeamEventEntryRepository.java`: gom toàn bộ
  query theo event/status/track vốn nằm ở `TeamRepository` (di chuyển
  nguyên vẹn), cộng thêm: `findByTeam_TeamIdAndEvent_EventId`,
  `existsByTeam_TeamIdAndEvent_EventId`,
  `findTopByTeam_TeamIdOrderByIdDesc` (bộ giải "entry hiện tại"),
  `findByIdForUpdate` (pessimistic lock), JPQL kiểm tra tên trùng theo event
  (`existsByEventIdAndNormalizedName`, thay cho bản cũ trên `TeamRepository`).
- `repository/TeamRepository.java`: co lại chỉ còn identity-thuần
  (`JpaRepository` mặc định) + giữ lại `findByIdForUpdate` — lock này vẫn
  còn ý nghĩa vì nó khoá **roster** (đang mutate `TeamMember`), không phải
  state theo-mùa.
- `entity/TeamInvite.java`, `entity/JoinRequest.java`: thêm cột `eventId`.
- `repository/TeamMemberRepository.java`: viết lại 4 derived-query từng đi
  qua `Team.event`/`Team.status`/`Team.track` thành JPQL join tường minh
  qua `TeamEventEntry` (JPA cho phép `JOIN OtherEntity ON ...` giữa 2 entity
  không có quan hệ mapped, từ JPA 2.1).
- `repository/JoinRequestRepository.java`,
  `repository/TeamInviteRepository.java`: 2 method mỗi bên đơn giản hoá
  thành query phẳng trên cột `eventId` mới, không cần join nữa.
- `database scripts/seal_schema.sql`: thêm bảng `TeamEventEntry`; sửa bảng
  `Team` (bỏ cột event/track/status/disqualified, thêm `is_active`); sửa
  `TeamInvite`/`JoinRequest` (thêm cột `event_id` + FK).
- `database scripts/seal_seed.sql`: tách insert `Team` (17 team) thành 2 bước
  — insert `Team` (chỉ name/description) rồi insert `TeamEventEntry` tương
  ứng theo đúng `team_id` cũ; sửa 2 dòng insert `TeamInvite` thêm cột
  `event_id`.

### Bước 2 — Viết lại `TeamService.java`

Gần như toàn bộ method của service này đều đổi (đúng như memory đã cảnh
báo trước): `createTeam` giờ tạo cả `Team` lẫn `TeamEventEntry` cùng lúc;
mọi method coordinator-facing (`approveTeam`, `rejectTeam`, `disqualifyTeam`,
`assignTeamToTrack`, `getTeamById`, `coordinatorRemoveMember`, `selectTrack`)
dùng helper riêng `requireCurrentEntry(Team)` để lấy entry đang sống; 4 helper
map response (`mapToTeamResponse`, `mapToMyTeamResponse`, `mapToDetailResponse`,
`mapToTeamHistoryResponse`) đều nhận thêm tham số `TeamEventEntry` để lấy field
event/track/status/disqualified từ đó thay vì từ `Team`. `leaveTeam` nhánh
solo-leader giờ chỉ `teamEventEntryRepository.delete(entry)`, không đụng
`teamRepository.delete(team)` nữa. `disqualifyTeam` và
`coordinatorRemoveMember` (nhánh hết người) đều set thêm
`team.setIsActive(false)` bên cạnh việc set `entry.status=DISQUALIFIED`.

### Bước 3 — Sửa các service phụ thuộc

Tổng cộng chạm tới các service sau (theo đúng bản kiểm kê 2 Explore agent
tổng hợp, cộng thêm 2 file phát hiện thêm khi rà lại lần cuối — xem mục 4):

- `LeftoverGroupingService.java` — 2 chỗ `Team.builder()` (tạo team mới lúc
  gom leftover) giờ tách thành cặp `Team.builder()` + `TeamEventEntry.builder()`;
  `dissolve(Team)` đổi thành `dissolve(Team, Integer eventId)` — xoá đúng
  `TeamEventEntry` của mùa đang xử lý, không đụng identity `Team` (khớp
  refinement #1); `Source` record trong file thêm field `TeamEventEntry entry`.
- `TeamInviteService.java`, `JoinRequestService.java` — resolve entry hiện
  tại lúc tạo invite/request để set cột `eventId` mới; mọi chỗ đọc
  `team.getEvent()/getStatus()/getTrack()` đổi thành đọc từ entry (resolve
  theo `invite.getEventId()`/`request.getEventId()` khi accept, không phải
  "entry hiện tại" — vì invite/request đã gắn cứng với mùa lúc tạo).
- `SubmissionService.java` — thay idiom "tìm team hiện tại của user trong
  event" bằng `teamEventEntryRepository.existsByTeam_TeamIdAndEvent_EventId`.
- `PrizeService.java` — `resolveTeam` đổi từ so sánh `team.getEvent()` sang
  kiểm tra tồn tại `TeamEventEntry`; `mapToResponse` resolve entry theo
  `(team, prize.getEvent())` để lấy tên track.
- `HackathonEventService.java` — mở rộng `lockCompletedEventParticipantsReadOnly`/
  `reactivateEventParticipants` để cũng loop qua team (mirror User, xem
  bảng quyết định ở mục 2); `hasNonCompletedMembership` đổi ngữ nghĩa từ
  "1 event duy nhất của team" sang "duyệt qua **mọi** entry của team".
- `MentorSupportRequestService.java` — thêm helper `currentEntry(Team)` để
  lấy track hiện tại (trước đây đọc thẳng `team.getTrack()`).
- `AuthService.isCurrentEventMembership` — đổi từ đọc 1 event duy nhất sang
  duyệt qua mọi entry của team, kiểm tra có entry nào event đang OPEN/SETUP/
  IN_PROGRESS không.
- `AssignmentService.java`, `RoundTimerService.java`, `RoundResultService.java`,
  `JudgeScoringCompletenessService.java` — đều đã có sẵn context event (qua
  `round.getEvent()`/`assignment.getRound().getEvent()`) nên **không cần
  đổi signature**, chỉ đổi cách resolve track/status từ `TeamEventEntry`
  thay vì `Team` trực tiếp.
- `TrackProblemService` — không cần sửa gì (kế thừa tự động từ việc
  `TeamMemberRepository` được viết lại ở Bước 1).

### Bước 4 — Seed data

- `DemoFixtures.team(...)` — build cặp `Team` + `TeamEventEntry` thay vì 1
  `Team.builder()...event()...status()`.
- `DemoFixtures.deactivateCompletedEventUsers` — đổi sang
  `teamEventEntryRepository.findAllByEvent_EventId`.
- `DemoScenario.java` — record `Slot` (dùng để build kịch bản demo theo
  track/strength) thêm field `Track track` (trước đây suy ra qua
  `s.team.getTrack()`, giờ Team không còn track nữa nên phải truyền thẳng
  lúc tạo `Slot`).

### Bước 5 — Compile, sửa test, chạy toàn bộ suite

Xem chi tiết ở mục 5 (bug tìm thấy) và mục 6 (kết quả cuối).

---

## 4. Bug/lỗ hổng phát hiện thêm ngoài kế hoạch ban đầu

Bản kế hoạch (từ 2 Explore agent + tự đọc code) chỉ bắt được các lệnh gọi
Java kiểu `.getEvent()`/`.getTrack()`/`.getStatus()`. Khi build thật, lộ ra
thêm mấy chỗ mà cách rà bằng grep-theo-method-call không thấy được:

1. **`AnnouncementService.java`, `TrackService.java`** — 2 service này gọi
   `teamRepository.findAllByTrack_Track...`/`findAllByEvent_...` nhưng
   không nằm trong danh sách 2 Explore agent báo cáo (có lẽ bị bỏ sót khi
   khảo sát). Phát hiện được nhờ 1 lượt `grep` quét lại toàn bộ
   `teamRepository.findAllBy|countBy|existsBy` trên cả codebase **sau khi**
   tưởng đã xong Bước 3 — sửa bổ sung trước khi compile lần đầu.
2. **2 câu JPQL thô** — không bị grep theo `.getEvent()` bắt được vì nằm
   trong chuỗi `@Query(...)`, không phải lệnh gọi method Java:
   - `UserRepository.findGroupableFreeAgents` — `WHERE tm.team.event.eventId
     = :eventId` → phải join tường minh qua `TeamEventEntry`. Lỗi này khiến
     **toàn bộ Spring context load thất bại lúc khởi động** (bean
     `dataSeeder`→`userRepository` không tạo được), bắt được nhờ chạy
     `HackathonApplicationTests.contextLoads`.
   - `SubmissionRepository.findBySubmissionIdAndJudgeId` /
     `findAllByRoundIdAndJudgeId` — `JOIN s.team t LEFT JOIN t.track
     teamTrack` → cũng phải join qua `TeamEventEntry` trước khi lấy `track`.
     Lỗi này **chỉ lộ ra sau khi đã sửa xong lỗi #1 ở trên** (context load
     tiếp tục thất bại ở 1 bean khác), vì Hibernate validate từng named
     query một, không báo hết lỗi cùng lúc.
3. **`RoundResultService.finalizeRound`** — sau khi sửa để resolve track
   qua `TeamEventEntry`, khai báo trùng tên biến `eventId` (method đã có
   sẵn tham số `eventId`) → lỗi compile "variable already defined", sửa
   bằng cách bỏ khai báo local thừa, dùng thẳng tham số method.
4. **Test cũ kỳ vọng sai hành vi mới** — `TeamServiceTest
   .leaveTeam_shouldDeleteTeam_whenOnlyLeaderLeaves` verify
   `teamRepository.delete(team)`, nhưng theo thiết kế mới (refinement #1)
   hành vi đúng là chỉ xoá `TeamEventEntry`. Đổi tên test thành
   `leaveTeam_shouldDeleteOnlyTheSeasonEntry_whenOnlyLeaderLeaves`, verify
   `teamEventEntryRepository.delete(entry)` + `teamRepository, never()).delete(any())`.

---

## 5. Sửa test — cách tiếp cận

10 file test cần sửa (42 lỗi compile ban đầu): `AuthServiceTest`,
`HackathonEventServiceTest`, `JoinRequestServiceTest`,
`JudgeScoringCompletenessServiceTest`, `LeftoverGroupingFreeAgentTest`,
`RoundTimerServiceTest`, `ScoringServiceTest`, `SubmissionServiceTest`,
`TeamInviteServiceTest`, `TeamServiceTest`, `TrackServiceTest`.

Với những file có 1 helper `team(...)` dùng chung ở nhiều test (đặc biệt
`TeamServiceTest` — ~50 chỗ gọi), cách sửa hiệu quả nhất là:
- Đổi helper từ `private static` thành instance method (không static nữa),
  để nó có thể truy cập mock `teamEventEntryRepository` của class test.
- Helper build cả `Team` lẫn `TeamEventEntry` tương ứng, rồi
  `lenient().when(teamEventEntryRepository.findTopByTeam_TeamIdOrderByIdDesc(...))`
  ngay trong helper — `lenient()` vì nhiều test build team nhưng không bao
  giờ thật sự chạm tới bước resolve entry (case ResourceNotFound sớm...),
  nếu dùng `when()` thường sẽ dính `UnnecessaryStubbingException` (Mockito
  strict-stubs mặc định của `MockitoExtension`).

Nhờ cách này, phần lớn các test method **không cần sửa gì thêm** — chỉ những
test **assert trực tiếp** trên `team.getTrack()`/`.getStatus()` (giờ đã
không còn tồn tại trên `Team`) hoặc **verify** `teamRepository.save(...)`
tại những chỗ mà code thật giờ save `TeamEventEntry` thay vì `Team` mới cần
sửa tay từng dòng (ví dụ 3 test `assignTeamToTrack_*`, và bỏ 4 dòng stub
`teamRepository.save(team)` không còn dùng tới ở `approveTeam`/`rejectTeam`
vì 2 method này giờ không đụng `teamRepository.save` nữa — chỉ
`disqualifyTeam` còn đụng, vì đó là chỗ set `isActive=false`).

`TeamRepository` không còn cần thiết trong `TrackServiceTest` nữa (đã xoá
hẳn khỏi `TrackService`) — xoá luôn mock, không giữ mock thừa không dùng.

---

## 6. Kết quả kiểm thử cuối cùng

```
./mvnw -o clean test-compile     → BUILD SUCCESS (242 file main + 17 file test)
./mvnw -o test                   → Tests run: 366, Failures: 0, Errors: 0 — BUILD SUCCESS
```

`HackathonApplicationTests.contextLoads` nằm trong 366 test đó và **pass** —
nghĩa là toàn bộ entity mapping + mọi câu JPQL (kể cả những câu không có
test riêng) đã được Hibernate xác thực hợp lệ lúc khởi động thật.

Frontend: `npx tsc -p tsconfig.app.json --noEmit` — còn đúng 1 lỗi, nhưng là
lỗi **có sẵn từ trước, không liên quan** (`CoordJudgesPage.tsx`, kiểu
`ReactPortal | null` không gán được cho `Element`) — xác nhận bằng
`git status --short front-end/` cho ra rỗng, tức phiên này **không đụng file
frontend nào**, khớp đúng thiết kế "0 thay đổi API/FE" ở mục 2.

---

## 7. Việc chưa làm / để ngỏ

- [x] ~~Chưa commit~~ — **đã commit**, xem mục 9.
- [x] ~~Chưa reset DB dev~~ — **đã làm**, xem mục 10.
- [x] ~~Chưa verify end-to-end qua Postman/DB thật~~ — **đã smoke-test qua
      API thật + query DB thật** (không dùng browser/Playwright lần này, vì
      mục tiêu là xác nhận đúng hành vi entity/DB, không phải UI — UI không
      hề đổi nên rủi ro nằm ở tầng data, không nằm ở tầng hiển thị), xem mục
      10.
- [ ] `getMyHistory`/`getMyResultHistory` có 1 lỗ hổng ngữ nghĩa lý thuyết
      (mục 2, hàng cuối bảng) — chưa fix, chỉ để comment cảnh báo trong
      code, vì tính năng rejoin (thứ duy nhất kích hoạt được lỗ hổng này)
      chưa tồn tại.
- [x] ~~Rejoin (leader request → coordinator approve → 1 `TeamEventEntry` mới
      cho `Team` đã có sẵn)~~ — **đã thiết kế + code + test (BE lẫn FE) trong
      cùng phiên**, xem mục 12.
- [ ] Chưa smoke-test nhánh `submit`/`MentorSupportRequest`/`Prize` qua API
      thật (chỉ qua unit test) — không nằm trong golden path đã chọn ở mục
      10 vì cần cấu hình `RoundTimer` phức tạp hơn để mở cửa sổ nộp bài;
      rủi ro thấp vì logic entity-resolve ở các service này giống hệt pattern
      đã verify qua các bước khác.

---

## 9. Tách nhánh riêng + commit (theo yêu cầu người dùng)

Theo yêu cầu, tách khỏi `TrangNHK-leave-event-coordinator-removal` sang nhánh
riêng `TrangNHK-team-event-entry-migration`, chia toàn bộ thay đổi thành 6
commit conventional, **không** có `Co-authored-by` (yêu cầu tường minh của
người dùng — tránh xuất hiện contributor AI trên GitHub):

```
5dca13d feat(team): split Team into a persistent identity plus TeamEventEntry
89dce08 feat(team): rewrite TeamService around TeamEventEntry
daf0b70 feat(team): migrate dependent services to TeamEventEntry
4206e2b chore(seed): update demo seed data for the TeamEventEntry split
dff21f0 test(team): update Mockito fixtures for the TeamEventEntry split
beebe30 docs: add AI log for Team/TeamEventEntry migration session
```

Nhóm theo lớp kiến trúc (entity/repo → service lõi → service phụ thuộc →
seed → test → docs) thay vì theo file ngẫu nhiên, để mỗi commit đọc log là
hiểu ngay phạm vi. File `srs_text.txt` (untracked, có sẵn từ trước, không
liên quan phiên này) được cố tình bỏ ngoài mọi commit. Chưa push — để người
dùng tự push.

---

## 10. Reset DB dev + smoke test end-to-end (API thật + DB thật)

### 10.1. Reset DB

Dùng đúng script có sẵn của repo (`run-demo.ps1`, xem
[[AI_LOG_SEAL_RUN_DEMO_SCRIPT_BE_2026-07-11]]) thay vì tự chế lệnh mysql —
đây chính là "verified path" của repo cho việc này. Chạy
`./run-demo.ps1 -Scenario S1 -Force`: drop `seal_hackathon`, set
`SEED_SCENARIO=S1`, `mvnw.cmd spring-boot:run`. Chọn kịch bản **S1** (event
`OPEN` + 15 đội đang hình thành, gồm 2 solo + 1 pair + team PENDING + vài
registrant teamless) vì đây là kịch bản duy nhất cho phép tự tay đi **trọn**
golden path từ đầu (approve → SETUP → leftover-grouping → draw-tracks →
IN_PROGRESS → disqualify → complete → reopen), thay vì S2/S3 vốn đã nhảy cóc
qua các bước đó sẵn.

**2 trục trặc vận hành gặp phải, không liên quan logic migration:**
- Lần chạy đầu tiên dùng `*>> file 2>&1` để gom log nền → PowerShell biến
  1 dòng cảnh báo vô hại của `mysql.exe` ("Using a password on the command
  line... insecure") thành `NativeCommandError` **terminating** (do
  `$ErrorActionPreference='Stop'` trong script), khiến script chết ngay sau
  bước drop, chưa kịp `spring-boot:run`. Sửa: bỏ hẳn `2>&1` khi gọi native
  exe qua PowerShell (đúng lưu ý đã có sẵn trong hướng dẫn dùng tool
  PowerShell) — lần chạy 2 xuyên suốt không lỗi.
- Cổng 8080 đã bị chiếm bởi 1 tiến trình `java.exe` (`HackathonApplication`)
  **có sẵn từ trước phiên này** (start time sớm hơn) — dò bằng
  `netstat`/`Get-CimInstance Win32_Process` xác nhận đúng process, dừng
  bằng `Stop-Process -Force` rồi chạy lại `-NoDrop` (DB đã drop sạch ở lần
  trước rồi, không cần drop lại).

Backend khởi động sạch (`Started HackathonApplication in 18.668 seconds`),
không log lỗi nào ngoài 4 warning benign có sẵn từ trước (CGLIB proxy,
open-in-view, static resource trailing slash) — **không có gì liên quan tới
migration**. Việc Hibernate `ddl-auto=update` tạo thành công schema hoàn
toàn mới (bảng `TeamEventEntry` mới, `Team` đã bỏ cột) từ entity, và
`DemoSeeder`/`DemoFixtures` (đã sửa ở nhánh này) seed thành công 15 đội theo
đúng cặp `Team`+`TeamEventEntry`, tự nó đã là một xác nhận mạnh — nếu mapping
hay JPQL nào còn sai, app sẽ không boot lên được (như đã thấy 2 lần lúc build
— mục 4).

### 10.2. Smoke test — đi hết golden path qua API thật (curl), verify bằng
DB thật (không qua UI/Playwright — xem lý do ở mục 7)

Auth qua cookie + CSRF (`GET /api/csrf` lấy `XSRF-TOKEN`, gửi lại header
`X-XSRF-TOKEN` mọi request ghi). Lưu ý vận hành: file cookie của curl trên
Windows có `\r\n`, nếu trích token bằng `awk` không lọc `\r` sẽ dính token
kèm ký tự CR vô hình → 403 khó hiểu; phải `tr -d '\r'` sau `awk`. Token cũng
**xoay vòng sau mỗi response** — phải đọc lại file cookie ngay trước mỗi
request, không cache biến từ đầu.

| Bước | Gọi | Kết quả |
|---|---|---|
| Login coordinator | `POST /api/auth/login` | 200, role `EVENT_COORDINATOR` |
| Xem 15 đội event 1 | `GET /api/teams/event/1` | 200 — đúng 15 đội, đúng status/track (null lúc này), xác nhận `mapToDetailResponse(team, entry)` resolve đúng qua DB thật |
| Duyệt đội PENDING ("Real Madrid") | `PUT /api/teams/3/approve` | 200, status→APPROVED |
| OPEN→SETUP | `PUT /api/events/1 {status:SETUP}` | 200 — xác nhận `requireAllTeamsResolved` (dùng `teamEventEntryRepository.countByEvent_EventIdAndStatus`) không còn PENDING nên cho qua |
| Leftover-grouping preview | `GET .../leftover-grouping/preview` | 200 — đúng 1 EXISTING (Bayern hấp thụ 1 solo) + 2 NEW, xác nhận `buildContext` đọc đúng `TeamEventEntry` |
| Leftover-grouping commit | `POST .../leftover-grouping/commit` | 200 — `teamsCreated:2, teamsGrown:1, peoplePlaced:7` |
| **Verify refinement #1** (dissolve chỉ xoá entry, giữ Team) | `GET /api/teams/4` (Chelsea, đội solo vừa bị hấp thụ) | **404 "No season participation found for team: 4"** — khác hẳn "Team not found" nếu bug thật sự xoá cả `Team`. Đây là bằng chứng trực tiếp: `teamRepository.findById(4)` **tìm thấy** row, chỉ `requireCurrentEntry` báo không còn entry → đúng thiết kế |
| Bốc track ngẫu nhiên | `POST .../draw-tracks` | 200 — cả 2 đội mới tạo (`Auto Team 1/2`) lẫn 15 đội cũ đều được gán track đúng, xác nhận `TeamEventEntry` mới tạo cũng vào được vòng bốc track |
| SETUP→IN_PROGRESS | `PUT /api/events/1 {status:IN_PROGRESS}` | 200 (1 lần đầu dính 403 do CSRF token trong file chưa kịp đồng bộ giữa 2 request liên tiếp — gọi lại ngay sau là qua, không phải lỗi logic) |
| Disqualify 1 đội (Arsenal) | `PUT /api/teams/1/disqualify` | 200, status→DISQUALIFIED, `disqualifiedReason`/`At` set đúng |
| **Verify `isActive` sau disqualify** | Query DB `Team.is_active` | Arsenal→**FALSE**; Chelsea/Auto Team 1/Auto Team 2→**TRUE** (đúng, chưa bị đụng) |
| Login admin | `POST /api/auth/login` (admin) | 200, role `SYSTEM_ADMIN` |
| Complete event | `POST /api/events/1/complete` | 200 |
| **Verify `isActive` sau complete** | Query DB join `Team`+`TeamEventEntry` | **Mọi đội có entry ở event 1** (kể cả Arsenal đã DISQUALIFIED từ trước) →**FALSE**; Chelsea/Juventus (không còn entry vì đã bị dissolve) → **TRUE, không bị đụng** — đúng thiết kế "chỉ loop team có entry" |
| Reopen event | `POST /api/events/1/reopen` | 200 |
| **Verify `isActive` sau reopen** (refinement #2) | Query DB | Mọi đội APPROVED →**TRUE** trở lại; **Arsenal (DISQUALIFIED) vẫn FALSE — không được hồi sinh** — đúng chính xác refinement #2 |

**Kết luận:** toàn bộ golden path chạy đúng trên DB MySQL thật, mọi hành vi
migration cốt lõi (entity split, "current entry" resolver, dissolve-giữ-
identity, isActive mirror + exception cho DISQUALIFIED) đều được xác nhận
bằng dữ liệu thật, không chỉ bằng mock. Backend vẫn đang chạy nền (PID theo
`java.exe`, cổng 8080) sau khi kết thúc smoke test — chưa dừng, để người
dùng tự tiếp tục nếu muốn dùng UI thật kiểm thêm.

---

## 11. Danh sách file

**Backend — mới (2 file):**
```
entity/TeamEventEntry.java
repository/TeamEventEntryRepository.java
```

**Backend — sửa, entity/repository (9 file):**
```
entity/Team.java                        (bỏ event/track/status/disqualified*, +isActive)
entity/TeamInvite.java                  (+eventId)
entity/JoinRequest.java                 (+eventId)
repository/TeamRepository.java          (co lại còn identity-only + findByIdForUpdate)
repository/TeamMemberRepository.java    (4 query viết lại qua TeamEventEntry)
repository/JoinRequestRepository.java   (1 query đơn giản hoá theo cột eventId mới)
repository/TeamInviteRepository.java    (1 query đơn giản hoá theo cột eventId mới)
repository/UserRepository.java          (findGroupableFreeAgents — JPQL join qua TeamEventEntry)
repository/SubmissionRepository.java    (2 JPQL — join qua TeamEventEntry để lấy track)
```

**Backend — sửa, service layer (14 file):**
```
service/TeamService.java                 (gần như viết lại toàn bộ)
service/LeftoverGroupingService.java     (Team.builder() → cặp Team+TeamEventEntry; dissolve theo entry)
service/TeamInviteService.java
service/JoinRequestService.java
service/SubmissionService.java
service/PrizeService.java
service/HackathonEventService.java       (+isActive mirroring team lúc complete/reopen)
service/MentorSupportRequestService.java
service/AuthService.java
service/AssignmentService.java
service/RoundTimerService.java
service/RoundResultService.java
service/JudgeScoringCompletenessService.java
service/TrackService.java
service/AnnouncementService.java
```

**Backend — sửa, seed & schema snapshot (4 file):**
```
config/seed/DemoFixtures.java
config/seed/DemoScenario.java
database scripts/seal_schema.sql
database scripts/seal_seed.sql
```

**Backend — sửa, test (11 file):**
```
test/.../service/TeamServiceTest.java
test/.../service/TeamInviteServiceTest.java
test/.../service/JoinRequestServiceTest.java
test/.../service/HackathonEventServiceTest.java
test/.../service/AuthServiceTest.java
test/.../service/JudgeScoringCompletenessServiceTest.java
test/.../service/LeftoverGroupingFreeAgentTest.java
test/.../service/RoundTimerServiceTest.java
test/.../service/ScoringServiceTest.java
test/.../service/SubmissionServiceTest.java
test/.../service/TrackServiceTest.java
```

**Frontend:** không có file nào bị đụng (nhánh migration nói riêng — mục 11
chỉ liệt kê phạm vi migration; tính năng rejoin ở mục 12 có đụng FE).

---

## 12. Tính năng Rejoin (leader request → coordinator approve) — thiết kế + code trong cùng phiên

### 12.1. Bối cảnh phát sinh

Sau khi migration xong, user tự tay test thử 1 luồng e2e thật trên UI:
hoàn thành 1 event cũ → tạo + mở event mới → đăng nhập lại bằng leader của
1 team ở event cũ. Phát hiện 2 lỗ hổng UX/data nghiêm trọng, đúng như đã
cảnh báo trước ("chưa xây") ở mục 7/9 phiên trước:

1. **"My Team" biến mất hoàn toàn (404)** khi season duy nhất của team đã
   `COMPLETED` — dù `Team` (identity) và `TeamMember` (roster) vẫn còn
   nguyên trong DB. Root cause: `TeamService.getMyTeam` chỉ chấp nhận
   membership có entry đang `OPEN/SETUP/IN_PROGRESS`.
2. **Không có đường quay lại**: dù user tự kích hoạt lại tài khoản qua flow
   `ParticipationAccessRequest` có sẵn (vốn chỉ đụng `User.isActive`, không
   biết gì về `Team`/`TeamEventEntry`), họ vẫn rơi vào trạng thái "participant
   không có team" — có thể vô tình tạo ra 1 team mới hoàn toàn trùng lặp,
   bỏ rơi team cũ (data-integrity risk phát hiện thêm khi truy vết code, không
   phải chỉ suy đoán).

User mô tả rõ 4 yêu cầu qua 1 đoạn test-case tự viết (dùng nguyên văn làm
spec): (a) "My Team" vẫn phải hiện team cũ ở chế độ read-only kể cả khi
chưa có season nào mở, chỉ chặn action tạo/join mới; (b) cần 1 luồng
"request mở lại" để leader cũ quay về đúng team cũ, không phải teamless;
(c) roster phải hiện rõ thành viên nào còn "inactive" (cần tự kích hoạt lại
riêng); (d) chỉ LEADER được quyền request rejoin, MEMBER thì không.

Được hỏi qua `AskUserQuestion`, user chọn **vào Plan Mode thiết kế + code
ngay**, không chỉ dừng ở việc ghi nhận yêu cầu.

### 12.2. Thiết kế (chốt qua Plan Mode, đã ExitPlanMode được duyệt)

Mirror đúng pattern `ParticipationAccessRequest` đã có sẵn trong codebase
(đọc nguyên file làm mẫu) thay vì nghĩ ra cơ chế mới:

- **Entity mới** `TeamRejoinRequest`: `team` (ManyToOne), `eventId` (Integer
  thường — mùa *đích*, giống pattern `TeamInvite.eventId`), `requestedBy`
  (ManyToOne User — chính là leader), `status` (String, default `PENDING`),
  `requestedAt`/`resolvedAt`/`resolvedBy`. Idempotent — chỉ 1 row `PENDING`
  cho mỗi team tại 1 thời điểm.
- **Service** `TeamRejoinRequestService`:
  - `requestRejoin` — validate leader (403 nếu không phải), validate
    `team.isActive == false` ("đã active rồi" → 400, vì cờ này chính là
    "đang thi mùa nào đó"), validate event đích đang `OPEN`, validate chưa
    có `TeamEventEntry` cho cặp (team, event) đó, tái dùng row `PENDING` cũ
    nếu có (idempotent). **Không** gửi notification lúc tạo request (giống
    hệt tiền lệ `approveTeam`/`rejectTeam` — chỉ notify lúc *resolve*, không
    notify lúc *tạo*, vì coordinator đã tự thấy hàng đợi qua dashboard riêng).
  - `approve` — tạo `TeamEventEntry` mới (status `APPROVED` ngay — vì bước
    duyệt request rejoin CHÍNH LÀ bước review của coordinator, không cần
    duyệt lại lần 2), set `team.isActive = true`, notify **toàn bộ roster**,
    ghi `AuditLogService` (hành động nhắm vào Team, không phải User → dùng
    đúng service theo tiền lệ đã chốt ở memory `security-role-tokens`/pattern
    log hiện có).
  - `reject` — resolve `REJECTED`, chỉ notify người request, **không đụng**
    `team.isActive`.
  - Cố tình **không** đụng tới `User.isActive` của bất kỳ ai — giữ ranh giới
    rõ ràng với `ParticipationAccessRequestService` (2 khái niệm độc lập:
    "tài khoản tôi có được hoạt động mùa này không" vs "team tôi có đang thi
    mùa nào không"), đúng tinh thần trong chính test-case của user (họ tự
    kích hoạt tài khoản **trước**, rồi mới mong được nối lại team).
- **`TeamService.getMyTeam` — 2 tầng resolve**: tầng 1 giữ nguyên (bất kỳ
  membership nào có entry `OPEN/SETUP/IN_PROGRESS`); tầng 2 (mới, chỉ chạy
  khi tầng 1 rỗng) — lấy `TeamMember` **mới nhất** của user, resolve team
  qua entry mới nhất bất kể status → trả về dù event đã `COMPLETED`. Không
  đổi chữ ký bất kỳ method nào khác; định nghĩa "entry hiện tại = mới nhất"
  (dùng xuyên suốt từ migration) vẫn đúng sau khi có rejoin, vì rejoin chỉ
  được phép filed khi `isActive == false`, tức tại một thời điểm bất kỳ tối
  đa 1 entry đang sống → entry mới tạo do rejoin luôn là "mới nhất", không
  bao giờ ambiguous.
- **DTO thêm** (additive, không breaking): `MyTeamResponse.hasPendingRejoinRequest`;
  `isActive` trên từng `TeamMemberInfo`/`MemberInfo` (lấy thẳng từ
  `User.getIsActive()`, giống cách `studentId`/`university` đã làm).
- **Lỗ hổng biết trước, cố tình KHÔNG fix trong lượt này**: `getMyHistory`/
  `getMyResultHistory` vốn 1-dòng-mỗi-`TeamMember` (không phải 1-dòng-mỗi-
  entry), luôn hiển thị entry **mới nhất** của team — lỗ hổng lý thuyết này
  đã được cảnh báo từ mục 2/7 phiên trước ("chưa thể xảy ra vì rejoin chưa
  tồn tại"). Giờ rejoin đã tồn tại, lỗ hổng **có thể xảy ra thật**: sau khi
  rejoin, mùa `COMPLETED` cũ của 1 member sẽ **biến mất khỏi trang
  History/Certificates** của họ (chỉ còn thấy mùa mới nhất). Ghi nhận rõ ràng
  ở đây, **không fix** — sửa đúng sẽ cần đổi hẳn cấu trúc history sang
  1-dòng-mỗi-entry, phạm vi lớn hơn nhiều so với tính năng rejoin.

### 12.3. Code

Backend: entity + repo + 2 DTO (request/response) + service + controller
(`POST /api/teams/{teamId}/rejoin-requests` PARTICIPANT,
`GET/POST /api/coordinator/team-rejoin-requests...` EVENT_COORDINATOR) +
bảng `TeamRejoinRequest` trong `seal_schema.sql` + 13 test mới
(`TeamRejoinRequestServiceTest`) + cập nhật `TeamServiceTest` (thêm mock
`teamRejoinRequestRepository` — bắt buộc vì MỌI method trả `MyTeamResponse`
giờ đều gọi repo này, nếu không stub sẽ NPE toàn bộ; cộng 1 test mới
`getMyTeam_shouldReturnDormantTeam_whenNoLiveEntryButHistoricalMembershipExists`).

Frontend: `apiClient.ts` (+`TeamRejoinRequest` type, +`teamRejoinRequestsApi`,
+`isActive`/`hasPendingRejoinRequest` trên các type có sẵn); `TeamViewPage.tsx`
(banner dormant + nút "REQUEST TO REJOIN <tên event>" khi có season đang mở,
`ConfirmDialog` xác nhận, badge INACTIVE trên từng thành viên); shared
`TeamDetailModal.tsx` (badge INACTIVE, dùng chung Coordinator/Mentor/Judge);
`CoordTeamsPage.tsx` (field `isActive` truyền qua); tab mới "Team Rejoin"
trong `CoordAccountsPage.tsx` (component mới `TeamRejoinRequestsPanel.tsx`,
mirror y hệt cấu trúc `ParticipationRequestsPanel` đã có sẵn — bảng, ô tìm
kiếm, nút Reject/Approve, badge đếm pending).

`npx tsc -p tsconfig.app.json --noEmit` — chỉ còn đúng 1 lỗi có sẵn từ trước,
không liên quan (`CoordJudgesPage.tsx`).

### 12.4. Kết quả kiểm thử

**Backend — `./mvnw -o test`**: `Tests run: 380, Failures: 0, Errors: 0 —
BUILD SUCCESS` (13 test mới của `TeamRejoinRequestServiceTest` nằm trong
số đó, cộng 1 test dormant-fallback mới trong `TeamServiceTest`).

**Live smoke test qua API thật + query DB thật** (dùng lại đúng golden path
resource từ mục 10 — reset DB bằng `run-demo.ps1 -Scenario S1`, dừng process
`java.exe` cũ trước khi chạy lại để chắc chắn code mới (entity/table
`TeamRejoinRequest`) được nạp, không dựa vào devtools auto-reload cho một
entity JPA mới):

| Bước | Gọi | Kết quả |
|---|---|---|
| Chuẩn bị: duyệt đội PENDING, OPEN→SETUP→(draw-tracks)→IN_PROGRESS→complete event 1 | các API đã dùng ở mục 10 | Event 1 → `COMPLETED`; mọi team có entry ở event 1 (kể cả Barcelona, teamId=2) → `is_active=false` tự động (mirror hành vi đã verify ở mục 10) |
| Tạo + mở event 2 ("SEAL Demo Fall 2026") | `POST /api/events` (admin) rồi `PUT /api/events/2 {status:OPEN}` | 200 — event 2 `OPEN`, event 1 vẫn `COMPLETED` |
| Login leader Barcelona cũ (p4, `isActive=false`) | `POST /api/auth/login` | 200 — **login vẫn thành công dù isActive=false** (xác nhận lại đúng ngữ nghĩa memory `participant-lifecycle-inactive-semantics`: isActive = "đang thi mùa nào", không phải "được phép đăng nhập") |
| **Verify bug cũ đã hết** | `GET /api/teams/my` (leader p4) | 200, trả về **team Barcelona dormant** (`eventStatus:"COMPLETED"`, `myRole:"LEADER"`, roster đủ 3 người, `isActive:false` từng người) — **không còn 404** như trước khi có tính năng này |
| Thử request rejoin **trước khi** tự kích hoạt tài khoản | `POST /api/teams/2/rejoin-requests {eventId:2}` | **403 "Your account is inactive for the current season..."** — đúng như thiết kế: filter chung của hệ thống chặn action ở tầng tài khoản trước khi vào tới logic leader-check |
| Leader tự request kích hoạt + coordinator duyệt | `POST /api/participation-requests` rồi `POST /api/coordinator/participation-requests/1/approve` | 200 cả 2 — `User.isActive` của p4 → `true` |
| **`GET /api/teams/my` sau khi tự kích hoạt (chưa rejoin)** | | Vẫn trả về đúng team Barcelona dormant (không bị rơi về teamless) — xác nhận yêu cầu (b) của user: kích hoạt tài khoản và rejoin team là 2 việc tách biệt, không cái nào tự kéo theo cái kia |
| Request rejoin (giờ mới hợp lệ) | `POST /api/teams/2/rejoin-requests {eventId:2}` | 201, `status:PENDING` |
| **Member (không phải leader) thử request rejoin** | login p5 (đã tự kích hoạt tài khoản, KHÔNG phải leader) → `POST /api/teams/2/rejoin-requests` | **400 "Only the team leader can request to rejoin."** — xác nhận đúng yêu cầu (d) |
| Coordinator xem hàng đợi + duyệt | `GET /api/coordinator/team-rejoin-requests` → `POST .../1/approve` | 200 — request `APPROVED` |
| **Verify sau approve** | `GET /api/teams/my` (leader p4) | `eventId:2`, `eventStatus:"OPEN"`, roster: p4+p5 `isActive:true` (đã tự kích hoạt), p6 (Vinícius, chưa kích hoạt) vẫn `isActive:false` — xác nhận yêu cầu (c) |
| **Verify DB thật** | Query `Team.is_active` + `TeamEventEntry WHERE team_id=2` | `is_active=1`; **2 dòng** `TeamEventEntry` cho team 2: id=2 (event 1, entry gốc, giữ nguyên) + id=16 (event 2, `APPROVED`, entry mới do rejoin tạo) — xác nhận Team identity không bị nhân bản, chỉ thêm entry mới đúng thiết kế |

**Frontend — Playwright (`chromium`, `front-end/src/seal-web:verify` skill,
driver script tự viết theo mẫu skill)**, lặp lại đúng golden path trên qua
UI thật (team Arsenal/leader p1, để có 1 lượt "sạch" chưa qua bước nào bằng
API):

1. Login p1 (leader Arsenal, `isActive=false`) → dashboard vẫn hiện được
   "Team Leader Console" bình thường (không bị chặn ở tầng dashboard).
2. Vào `/team/view` → đúng như thiết kế: banner "This event has ended — your
   team is locked." + banner dormant "Your team's season is over, but its
   identity and roster are still here..." + nút "REQUEST TO REJOIN SEAL DEMO
   FALL 2026" + bảng thành viên hiện badge `INACTIVE` cho cả 3 người.
3. Bấm nút → `ConfirmDialog` hiện đúng cảnh báo phụ đã viết thêm lúc code
   ("Each roster member still needs their own account reactivated separately
   before they can act on the new season.") → bấm xác nhận **trong khi vẫn
   chưa tự kích hoạt tài khoản** → toast lỗi "REQUEST FAILED — Your account
   is inactive for the current season..." hiện đúng, UI không crash, không
   rơi vào trạng thái treo.
4. Coordinator duyệt participation-request của p1 qua tab "RESOLVE REQUEST"
   có sẵn (UI thật, không qua API) → p1 quay lại `/team/view`, bấm lại nút
   rejoin → xác nhận → toast "REJOIN REQUEST SENT" + banner đổi thành "Your
   request to rejoin is awaiting coordinator review." + badge `INACTIVE` của
   riêng p1 biến mất (2 đồng đội còn lại vẫn còn).
5. Coordinator vào tab mới "TEAM REJOIN" trong Accounts page → thấy đúng 1
   dòng "Arsenal / Lionel Messi / SEAL DEMO FALL 2026" → bấm APPROVE → toast
   "TEAM REJOINED — Arsenal can now compete in SEAL Demo Fall 2026." → danh
   sách về lại rỗng "No pending team rejoin requests".
6. p1 vào lại `/team/view` → **CURRENT EVENT đổi thành "SEAL Demo Fall 2026
   OPEN"**, toàn bộ control chỉnh sửa quay lại bình thường (nút "EDIT NAME",
   "INVITE MEMBER", "LEAVE TEAM", cột Actions trên bảng thành viên) — team đã
   thật sự "sống lại" cho mùa mới; 2 đồng đội chưa tự kích hoạt vẫn hiện đúng
   badge `INACTIVE`.

Không phát hiện lỗi console mới nào liên quan tới tính năng (chỉ còn vài
401/404 nền vô hại xảy ra cả trước khi có tính năng này, giống các warning đã
ghi nhận ở mục 6).

**Kết luận:** cả 4 yêu cầu user nêu trong test-case gốc đều được xác nhận
đúng bằng cả API+DB thật lẫn UI thật qua trình duyệt, không chỉ qua unit
test/mock.

---

## 13. Danh sách file — tính năng Rejoin

**Backend — mới (6 file):**
```
entity/TeamRejoinRequest.java
repository/TeamRejoinRequestRepository.java
dto/request/CreateTeamRejoinRequestRequest.java
dto/response/TeamRejoinRequestResponse.java
service/TeamRejoinRequestService.java
controller/TeamRejoinRequestController.java
```

**Backend — sửa (4 file):**
```
service/TeamService.java              (getMyTeam — 2 tầng resolve, dormant fallback)
dto/response/MyTeamResponse.java      (+hasPendingRejoinRequest, member +isActive)
dto/response/TeamDetailResponse.java  (member +isActive)
database scripts/seal_schema.sql      (+bảng TeamRejoinRequest)
```

**Backend — test (2 file):**
```
test/.../service/TeamRejoinRequestServiceTest.java   (mới, 13 test)
test/.../service/TeamServiceTest.java                (+mock teamRejoinRequestRepository, +1 test dormant-fallback)
```

**Frontend — mới (1 file):**
```
features/users/TeamRejoinRequestsPanel.tsx
```

**Frontend — sửa (4 file):**
```
shared/apiClient.ts
features/teams/TeamViewPage.tsx
shared/components/TeamDetailModal.tsx
features/teams/CoordTeamsPage.tsx
features/users/CoordAccountsPage.tsx   (+tab "Team Rejoin")
```
