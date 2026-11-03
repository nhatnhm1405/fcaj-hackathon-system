# HackathonEventService Validation & Test Report

File service: `src/main/java/com/seal/hackathon/service/HackathonEventService.java`

File test: `src/test/java/com/seal/hackathon/service/HackathonEventServiceTest.java`

Files lien quan da sua / lien quan truc tiep:

- `src/main/java/com/seal/hackathon/controller/HackathonEventController.java`
- `src/main/java/com/seal/hackathon/dto/request/CreateEventRequest.java`
- `src/main/java/com/seal/hackathon/dto/request/UpdateEventRequest.java`
- `src/main/java/com/seal/hackathon/dto/response/HackathonEventResponse.java`
- `src/main/java/com/seal/hackathon/entity/HackathonEvent.java`
- `src/main/java/com/seal/hackathon/repository/HackathonEventRepository.java`
- `src/main/java/com/seal/hackathon/repository/TrackRepository.java`
- `src/main/java/com/seal/hackathon/repository/TeamRepository.java`
- `src/main/java/com/seal/hackathon/service/AuditLogService.java`

Quy uoc status:

- `Valid`: test case ky vong service xu ly thanh cong, khong nem exception.
- `Invalid`: test case ky vong service chan request/action khong hop le bang exception.

Tong so public function trong `HackathonEventService`: **6**

Tong so test case trong `HackathonEventServiceTest`: **21**

## Business Rules Implemented

Nhung rule duoi day da duoc implement/test theo code hien tai:

- `season` chi chap nhan `SPRING`, `SUMMER`, `FALL`; input duoc trim va uppercase.
- `year` bat buoc trong khoang `2026..3000`.
- `name` bat buoc, duoc trim, max length = 255.
- 4 moc ngay cua event gom `registrationStart`, `registrationEnd`, `startDate`, `endDate` phai co du khi create hoac khi update schedule.
- Thu tu ngay bat buoc: `registrationStart <= registrationEnd <= startDate <= endDate`.
- Ca 4 moc ngay phai nam trong dung `season` va dung `year`.
- Quy uoc mua hien tai:
  - `SPRING`: `01-01` den `04-30`.
  - `SUMMER`: `05-01` den `08-31`.
  - `FALL`: `09-01` den `12-31`.
- Create event khong cho bat ky moc ngay nao nam truoc ngay hien tai.
- Update event chi check "khong o qua khu" voi nhung field ngay duoc gui trong request.
- Update event dung effective state: field nao request gui thi lay request, field nao khong gui thi lay event hien co, sau do validate lai toan bo lich.
- Moi cap `(season, year)` chi duoc co 1 active event.
- Event status `CANCELLED` khong chiem slot `(season, year)` va khong duoc tinh la active khi check duplicate/overlap.
- Khi update `season` hoac `year`, service check cap `(season, year)` moi da co active event khac chua.
- Khi doi status tu `CANCELLED` sang `DRAFT` hoac trang thai non-`CANCELLED` duoc transition cho phep, service check lai cap `(season, year)` va overlap active.
- Khoang thi dau `startDate/endDate` cua active event khong duoc overlap voi active event khac.
- `trackSelectionMode` chi chap nhan `SELF_SELECT` hoac `RANDOM`, va chi duoc doi khi event dang `DRAFT` hoac `OPEN`.
- Status transition duoc validate theo map trong service; `SETUP -> IN_PROGRESS` bi gate boi setup completeness.
- Khi bat dau event tu `SETUP -> IN_PROGRESS`, moi track phai co it nhat 2 team approved va khong co approved team nao bi unassigned.

## Implementation Summary

| Area | Change |
|---|---|
| Controller validation | Them `@Valid` cho update endpoint de `UpdateEventRequest` duoc Bean Validation. |
| DTO validation | Them `@Size(max = 255)` cho name; them `@Min(2026)` va `@Max(3000)` cho year. |
| Season validation | Them normalize/validate season trong service cho create va update. |
| Year validation | Service validate year `2026..3000`, khong chi phu thuoc annotation DTO. |
| Date ordering | Validate `registrationStart <= registrationEnd <= startDate <= endDate`. |
| Season date window | Them helper `seasonWindow(...)` de bat 4 ngay nam dung mua/nam. |
| Past date rule | Create check tat ca 4 ngay khong o qua khu; update chi check ngay nao request gui len. |
| Effective update | Update ghep request + event hien tai thanh effective schedule roi moi validate. |
| Duplicate season/year | Them check active duplicate `(season, year)` khi create, update season/year, hoac reactivate tu `CANCELLED`. |
| Cancelled handling | `CANCELLED` event khong bi tinh la active trong duplicate/overlap. |
| Overlap active event | Them repository query check overlap `startDate/endDate` voi active event khac. |
| Reactivate guard | `CANCELLED -> DRAFT` phai check schedule, duplicate `(season, year)`, va overlap truoc khi save. |
| Unit tests | Them Mockito unit tests cover create/update schedule, duplicate, overlap, reactivate, va setup start gate. |

## Public Function Coverage Summary

| Function | So test | Coverage chinh |
|---|---:|---|
| `getAllHackathonEvents` | 0 | Chua co unit test rieng trong file nay. |
| `getEventById` | 0 | Chua co unit test rieng trong file nay. |
| `createEvent` | 6 | Success, date in past, date outside season, duplicate active `(season, year)`, overlap active event, year before 2026 |
| `updateEvent` | 15 | Invalid season/year, effective date ordering, requested date in past, season/year change, cancelled conflict, reactivate conflict, overlap, one-date valid update, start event gate |
| `completeEvent` | 0 | Chua co unit test rieng trong file nay. |
| `reopenEvent` | 0 | Chua co unit test rieng trong file nay. |

## Validation Matrix By Function

### `createEvent(CreateEventRequest request, Integer actorUserId)`

| Validation group | Current behavior |
|---|---|
| Name | Required, trim, max 255. |
| Season | Required, trim, uppercase, chi chap nhan `SPRING`, `SUMMER`, `FALL`. |
| Year | Required, chi chap nhan `2026..3000`. |
| Required dates | Bat buoc co du `registrationStart`, `registrationEnd`, `startDate`, `endDate`. |
| Date order | Chan registration end truoc registration start, start date truoc registration end, end date truoc start date. |
| Season window | Ca 4 ngay phai nam trong dung mua va nam. |
| Past dates | Ca 4 ngay khong duoc nam truoc ngay hien tai. |
| Status | Null/blank default `DRAFT`; non-blank phai thuoc valid statuses. |
| Track mode | Null/blank default `SELF_SELECT`; non-blank phai la `SELF_SELECT` hoac `RANDOM`. |
| Duplicate active event | Chan neu da co active event cung `(season, year)`. |
| Overlap active event | Chan neu khoang thi dau overlap voi active event khac. |
| Cancelled handling | Neu tao event status `CANCELLED`, duplicate/overlap active duoc bo qua. |
| Side effects | Save event va ghi audit log khi tat ca validation pass. |

### `updateEvent(Integer eventId, UpdateEventRequest request)`

| Validation group | Current behavior |
|---|---|
| Resource exists | Event phai ton tai, neu khong nem `ResourceNotFoundException`. |
| Name | Neu request gui name thi validate required sau trim va max 255. |
| Season | Neu request gui season thi validate `SPRING`/`SUMMER`/`FALL`. |
| Year | Neu request gui year thi validate `2026..3000`. |
| Effective schedule | Ghep field trong request voi field event hien co roi validate lai. |
| Date order | Luon validate thu tu ngay tren effective schedule. |
| Schedule touched | Neu request gui season/year/bat ky ngay nao thi bat buoc effective schedule co du 4 ngay va dung season/year. |
| Past date update | Chi ngay nao request gui len moi bi check khong duoc o qua khu. |
| Duplicate active event | Check khi season/year/startDate/endDate thay doi hoac reactivate tu `CANCELLED`. |
| Overlap active event | Check khoang thi dau effective voi active event khac, exclude chinh event dang update. |
| Cancelled handling | Neu effective status la `CANCELLED` thi duplicate/overlap active duoc bo qua. |
| Reactivate cancelled | `CANCELLED -> DRAFT` phai validate schedule day du, duplicate va overlap truoc khi save. |
| Track mode | Chi doi duoc khi event dang `DRAFT` hoac `OPEN`. |
| Status transition | Validate theo transition map trong service. |
| Start event gate | `SETUP -> IN_PROGRESS` chi pass khi setup complete. |

### `completeEvent(Integer eventId)`

| Validation group | Current behavior |
|---|---|
| Resource exists | Event phai ton tai. |
| Status | Chi event `IN_PROGRESS` moi duoc complete. |
| Side effect | Set status `COMPLETED` va save. |
| Test coverage | Chua co test rieng trong `HackathonEventServiceTest`. |

### `reopenEvent(Integer eventId)`

| Validation group | Current behavior |
|---|---|
| Resource exists | Event phai ton tai. |
| Status | Chi event `COMPLETED` moi duoc reopen. |
| Side effect | Set status `IN_PROGRESS` va save. |
| Test coverage | Chua co test rieng trong `HackathonEventServiceTest`. |

## Detailed Test Cases

| # | Function | Test case | Status | Expected result |
|---:|---|---|---|---|
| 1 | `createEvent` | `createEvent_shouldSucceed_whenFutureDatesMatchSeasonAndNoDuplicateOrOverlap` | Valid | Tao event thanh cong, normalize season `FALL`, default status `DRAFT`, save event. |
| 2 | `createEvent` | `createEvent_shouldThrow_whenAnyDateIsInThePast` | Invalid | Chan create khi co moc ngay nam truoc ngay hien tai, khong save. |
| 3 | `createEvent` | `createEvent_shouldThrow_whenDateDoesNotMatchSeason` | Invalid | Chan create khi ngay khong nam trong season/year request. |
| 4 | `createEvent` | `createEvent_shouldThrow_whenActiveSeasonYearAlreadyExists` | Invalid | Chan create khi da co active event cung `(season, year)`. |
| 5 | `createEvent` | `createEvent_shouldThrow_whenActiveEventDatesOverlap` | Invalid | Chan create khi khoang thi dau overlap voi active event khac. |
| 6 | `createEvent` | `createEvent_shouldThrow_whenYearIsBefore2026` | Invalid | Chan create voi `year = 2025`. |
| 7 | `updateEvent` | `updateEvent_shouldThrow_whenSeasonIsInvalid` | Invalid | Chan update season `WINTER`, khong save. |
| 8 | `updateEvent` | `updateEvent_shouldValidateRequestedDateAgainstExistingEventDates` | Invalid | Chan khi request `registrationStart` lam effective date order khong hop le. |
| 9 | `updateEvent` | `updateEvent_shouldThrow_whenRequestedDateIsInThePast` | Invalid | Chan update ngay request nam trong qua khu, khong save. |
| 10 | `updateEvent` | `updateEvent_shouldThrow_whenYearIsAfter3000` | Invalid | Chan update voi `year = 3001`. |
| 11 | `updateEvent` | `updateEvent_shouldThrow_whenSeasonChangeDoesNotMatchExistingDates` | Invalid | Chan doi season neu effective dates hien co khong thuoc season moi. |
| 12 | `updateEvent` | `updateEvent_shouldThrow_whenYearUpdateTargetsExistingActiveSeasonEvent` | Invalid | Chan doi year vao cap `(season, year)` da co active event khac. |
| 13 | `updateEvent` | `updateEvent_shouldSucceed_whenSeasonUpdateOnlyConflictsWithCancelledEvent` | Valid | Cho update season khi cap moi chi conflict voi event `CANCELLED`; save event. |
| 14 | `updateEvent` | `updateEvent_shouldThrow_whenReactivatingCancelledEventAndActiveSeasonYearExists` | Invalid | Chan `CANCELLED -> DRAFT` neu da co active event cung `(season, year)`. |
| 15 | `updateEvent` | `updateEvent_shouldSucceed_whenReactivatingCancelledEventAndNoActiveSeasonYearExists` | Valid | Cho `CANCELLED -> DRAFT` khi chua co active event cung `(season, year)` va khong overlap. |
| 16 | `updateEvent` | `updateEvent_shouldThrow_whenUpdatedCompetitionDatesOverlapAnotherActiveEvent` | Invalid | Chan update start/end date neu overlap active event khac. |
| 17 | `updateEvent` | `updateEvent_shouldSucceed_whenOnlyOneRequestedDateKeepsEffectiveScheduleValid` | Valid | Cho update chi 1 field ngay neu effective schedule van hop le. |
| 18 | `updateEvent` | `startEvent_shouldSucceed_whenEveryTrackHasTwoTeamsAndNoneUnassigned` | Valid | Cho `SETUP -> IN_PROGRESS` khi moi track co >= 2 approved teams va khong co team unassigned. |
| 19 | `updateEvent` | `startEvent_shouldThrow_whenATrackHasFewerThanTwoTeams` | Invalid | Chan start event khi co track duoi 2 approved teams. |
| 20 | `updateEvent` | `startEvent_shouldThrow_whenTeamsAreUnassigned` | Invalid | Chan start event khi con approved team chua duoc assign track. |
| 21 | `updateEvent` | `startEvent_shouldThrow_whenEventHasNoTracks` | Invalid | Chan start event khi event khong co track. |

## Test Commands Run

Related test:

```powershell
& 'C:\Users\DAO HOANG NHAT\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd' -Dtest=HackathonEventServiceTest test
```

Result:

- Tests run: 21
- Failures: 0
- Errors: 0
- Skipped: 0

Full test suite:

```powershell
& 'C:\Users\DAO HOANG NHAT\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd' test
```

Result:

- Chua chay lai full suite trong lan update nay.
- Related test `HackathonEventServiceTest` da pass 21/21.

## Notes For Review

- Rule "1 event trong 1 season" hien duoc hieu la 1 active event cho moi cap `(season, year)`, vi neu unique chi theo season thi cac nam sau se khong tao lai duoc `SPRING`/`SUMMER`/`FALL`.
- `CANCELLED` event khong chiem slot `(season, year)`, nen co the tao/update event moi vao cung mua/nam neu event cu da cancelled.
- Update chi check "ngay qua khu" cho field ngay duoc gui trong request. Cach nay tranh viec status-only update cua event dang dien ra bi fail vi registrationStart/registrationEnd da nam trong qua khu.
- `completeEvent` va `reopenEvent` la public functions nhung chua co unit test rieng trong file nay. Neu can coverage day du public API cua service, nen them test cho 2 function nay.
- `getAllHackathonEvents` va `getEventById` chua co unit test rieng trong file nay; cac test hien tai tap trung vao validation create/update va start-event gate.
- Quy uoc season window dang suy ra tu seed data: `SPRING` Jan-Apr, `SUMMER` May-Aug, `FALL` Sep-Dec. Neu business quy dinh moc mua khac, can doi `seasonWindow(...)` va update test tuong ung.
- Repository duplicate/overlap query bo qua `CANCELLED`, nhung unit test voi Mockito khong simulate race condition 2 request song song. Neu can enforce tuyet doi, nen co them DB unique/constraint hoac transaction-level strategy.
- Maven wrapper `mvnw.cmd` trong project co loi PowerShell voi `.m2` Target tren may nay, nen test duoc chay bang Maven binary trong `.m2/wrapper/dists`.
