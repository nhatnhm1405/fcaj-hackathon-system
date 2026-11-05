# TrackService Validation & Test Report

File service: `src/main/java/com/seal/hackathon/service/TrackService.java`

File test: `src/test/java/com/seal/hackathon/service/TrackServiceTest.java`

Files lien quan da sua / lien quan truc tiep:

- `src/main/java/com/seal/hackathon/dto/request/CreateTrackRequest.java`
- `src/main/java/com/seal/hackathon/dto/response/TrackResponse.java`
- `src/main/java/com/seal/hackathon/entity/Track.java`
- `src/main/java/com/seal/hackathon/repository/TrackRepository.java`
- `src/main/java/com/seal/hackathon/repository/TeamRepository.java`

Quy uoc status:

- `Valid`: test case ky vong service xu ly thanh cong, khong nem exception.
- `Invalid`: test case ky vong service chan request/action khong hop le bang exception.

Tong so public function trong `TrackService`: **5**

Tong so JUnit executions trong `TrackServiceTest`: **64**

## Business Rules Implemented

Nhung rule duoi day da duoc implement/test theo code hien tai:

- `eventId` va `trackId` public input phai non-null; null bi chan bang `BadRequestException`.
- Moi operation theo event deu check event ton tai truoc khi tiep tuc.
- `getTrackById`, `updateTrack`, `deleteTrack` deu check track thuoc dung event.
- Track list duoc sort theo `name`, case-insensitive, de FE/review de doc hon.
- Event status duoc normalize:
  - `trim()`;
  - uppercase;
  - space/hyphen -> underscore;
  - `SET_UP` duoc normalize thanh `SETUP`.
- `createTrack` duoc phep khi event status la `DRAFT` hoac `OPEN`.
- `createTrack` bi chan khi event status la `SETUP`, `ON-GOING`, `COMPLETED`, `CANCELLED`, null hoac blank.
- `updateTrack` va `deleteTrack` duoc phep khi event status la `DRAFT`, `OPEN`, hoac `SETUP`.
- `updateTrack` va `deleteTrack` bi chan khi event status la `ON-GOING`, `COMPLETED`, `CANCELLED`, null hoac blank.
- Track name:
  - request null/name null/blank bi chan;
  - name duoc `trim()`;
  - max length = 255.
- Track description:
  - `null` giu nguyen logic update/create phu hop;
  - non-null duoc `trim()`;
  - blank -> `null`;
  - max length = 2000.
- Duplicate track name duoc check bang normalized name `UPPER(TRIM(name))`.
- `deleteTrack` hien tai khong block khi co team. Code hien tai move cac team ve unassigned pool (`track = null`), `saveAll`, sau do delete track.
- `TrackResponse` co them `capacity`; `TrackService.mapToResponse` map `capacity` tu entity.

## Implementation Summary

| Area | Change |
|---|---|
| Input validation | Them `requireId` cho `eventId`, `trackId`; service tu phong thu, khong phu thuoc hoan toan controller. |
| Event lookup | Them `requireEvent`; `getTrackById` check event ton tai truoc khi lookup track. |
| Status validation | Them allowed sets cho create va mutation; normalize `SET UP`, `set-up`, `SET_UP` ve `SETUP`. |
| Name validation | Them service-level max 255, trim, null/blank guard; them `@Size(max = 255)` trong DTO. |
| Description validation | Them trim/blank-to-null/max 2000 trong service; them `@Size(max = 2000)` trong DTO. |
| Duplicate detection | Them repository query `UPPER(TRIM(t.name))` cho create/update. |
| DB metadata | Them entity-level unique constraint `uq_track_event_name` tren `(event_id, name)`. |
| Delete behavior | Them `TeamRepository.findAllByTrack_TrackId`; khi xoa track thi unassign teams truoc khi delete. |
| Response mapping | `TrackResponse` co `capacity`; service map `capacity` ra response. |
| Unit tests | Them `TrackServiceTest` voi Mockito, cover valid/invalid branch theo tung public function. |

## Public Function Coverage Summary

| Function | So JUnit executions | Coverage chinh |
|---|---:|---|
| `getTracksByEvent` | 4 | Event exists/not found, null eventId, empty list, sorted list |
| `getTrackById` | 6 | Success, null ids, event not found, track not found, track sai event |
| `createTrack` | 22 | Success DRAFT/OPEN, normalized DRAFT, SETUP lock, status invalid/null/blank, request/name/description validation, duplicate normalized name |
| `updateTrack` | 19 | Success, description-only update, blank description, null ids, event/track not found, status invalid/null/blank, track sai event, request/name/description validation, duplicate normalized name |
| `deleteTrack` | 13 | Success SETUP, normalized SETUP, null ids, event/track not found, status invalid/null/blank, track sai event, delete with team unassign |

## Validation Matrix By Function

### `getTracksByEvent(Integer eventId)`

| Validation group | Current behavior |
|---|---|
| Null input | `eventId = null` bi chan bang `BadRequestException`. |
| Resource exists | Event phai ton tai, neu khong nem `ResourceNotFoundException`. |
| Repository query | Chi query tracks sau khi event hop le. |
| Output order | Tracks duoc sort theo `name`, case-insensitive. |
| Empty result | Event ton tai nhung khong co track thi tra list rong. |

### `getTrackById(Integer eventId, Integer trackId)`

| Validation group | Current behavior |
|---|---|
| Null input | `eventId`/`trackId` null bi chan bang `BadRequestException`. |
| Event exists | Check event ton tai truoc khi lookup track. |
| Track exists | Neu track khong ton tai thi nem `ResourceNotFoundException`. |
| Ownership | Neu track khong thuoc event request thi nem `BadRequestException`. |
| Mapping | Tra `trackId`, `eventId`, `name`, `description`, `capacity`. |

### `createTrack(Integer eventId, CreateTrackRequest request)`

| Validation group | Current behavior |
|---|---|
| Null input | `eventId`, request, request.name null/blank bi chan. |
| Event exists | Event phai ton tai. |
| Event status | Chi cho create khi status la `DRAFT` hoac `OPEN`; chan `SETUP` tro di. |
| Status normalization | ` draft ` hop le; `SET UP`/`set-up` normalize thanh `SETUP` va bi chan cho create. |
| Name | Trim, max 255, duplicate check bang `UPPER(TRIM(name))`. |
| Description | Trim, blank -> `null`, max 2000. |
| Side effect | Save track moi khi tat ca validation pass. |

### `updateTrack(Integer eventId, Integer trackId, CreateTrackRequest request)`

| Validation group | Current behavior |
|---|---|
| Null input | `eventId`, `trackId`, request null bi chan. |
| Event exists | Event phai ton tai truoc khi lookup track. |
| Event status | Chi cho update khi status la `DRAFT`, `OPEN`, hoac `SETUP`. |
| Track exists | Track phai ton tai. |
| Ownership | Track phai thuoc dung event. |
| Name | Neu name non-null thi validate non-blank/max 255, trim, duplicate normalized. |
| Description | Neu description non-null thi trim, blank -> `null`, max 2000. |
| Side effect | Save track sau khi update hop le. |

### `deleteTrack(Integer eventId, Integer trackId)`

| Validation group | Current behavior |
|---|---|
| Null input | `eventId`, `trackId` null bi chan. |
| Event exists | Event phai ton tai truoc khi lookup track. |
| Event status | Chi cho delete khi status la `DRAFT`, `OPEN`, hoac `SETUP`. |
| Track exists | Track phai ton tai. |
| Ownership | Track phai thuoc dung event. |
| Teams on track | Neu co teams, set `team.track = null`, `saveAll`, roi delete track. |
| Side effect | Delete track sau khi optional unassign teams. |

## Detailed Test Cases

| # | Function | Test case | JUnit count | Status | Expected result |
|---:|---|---|---:|---|---|
| 1 | `getTracksByEvent` | `getTracksByEvent_shouldReturnTracksSortedByName_whenEventExists` | 1 | Valid | Event ton tai, tracks tra ve theo thu tu `AI`, `Data`, `Web`. |
| 2 | `getTracksByEvent` | `getTracksByEvent_shouldReturnEmptyList_whenEventHasNoTracks` | 1 | Valid | Event ton tai nhung khong co track thi tra list rong. |
| 3 | `getTracksByEvent` | `getTracksByEvent_shouldThrowBadRequest_whenEventIdIsNull` | 1 | Invalid | Chan `eventId = null`, khong goi repository find tracks. |
| 4 | `getTracksByEvent` | `getTracksByEvent_shouldThrowResourceNotFound_whenEventDoesNotExist` | 1 | Invalid | Nem `ResourceNotFoundException`, khong query tracks. |
| 5 | `getTrackById` | `getTrackById_shouldReturnTrack_whenTrackBelongsToEvent` | 1 | Valid | Tra track response khi event va track ton tai, track thuoc event. |
| 6 | `getTrackById` | `getTrackById_shouldThrowBadRequest_whenEventIdIsNull` | 1 | Invalid | Chan `eventId = null`, khong lookup event/track. |
| 7 | `getTrackById` | `getTrackById_shouldThrowBadRequest_whenTrackIdIsNull` | 1 | Invalid | Chan `trackId = null`, khong lookup event/track. |
| 8 | `getTrackById` | `getTrackById_shouldThrowResourceNotFound_whenEventDoesNotExist` | 1 | Invalid | Event khong ton tai thi nem `ResourceNotFoundException`, khong lookup track. |
| 9 | `getTrackById` | `getTrackById_shouldThrowResourceNotFound_whenTrackDoesNotExist` | 1 | Invalid | Track khong ton tai thi nem `ResourceNotFoundException`. |
| 10 | `getTrackById` | `getTrackById_shouldThrowBadRequest_whenTrackDoesNotBelongToEvent` | 1 | Invalid | Chan track thuoc event khac. |
| 11 | `createTrack` | `createTrack_shouldCreateTrack_whenEventIsDraftAndRequestIsValid` | 1 | Valid | Tao track khi event `DRAFT`, trim name/description, save track. |
| 12 | `createTrack` | `createTrack_shouldAcceptNormalizedDraftStatusAndMaxLengthName` | 1 | Valid | Status ` draft ` hop le; name dung 255 ky tu duoc chap nhan. |
| 13 | `createTrack` | `createTrack_shouldSetDescriptionNull_whenDescriptionIsBlank` | 1 | Valid | Blank description duoc normalize thanh `null`. |
| 14 | `createTrack` | `createTrack_shouldThrowBadRequest_whenEventIdIsNull` | 1 | Invalid | Chan `eventId = null`, khong save. |
| 15 | `createTrack` | `createTrack_shouldThrowResourceNotFound_whenEventDoesNotExist` | 1 | Invalid | Event khong ton tai thi nem `ResourceNotFoundException`. |
| 16 | `createTrack` | `createTrack_shouldThrowBadRequest_whenEventStatusCannotMutateTracks` | 3 | Invalid | Chan create khi status la `ON-GOING`, `COMPLETED`, `CANCELLED`. |
| 17 | `createTrack` | `createTrack_shouldThrowBadRequest_whenEventIsInSetup` | 4 | Invalid | Chan create khi status la `SETUP`, `SET UP`, `set-up`, ` setup `. |
| 18 | `createTrack` | `createTrack_shouldCreateTrack_whenEventIsOpen` | 1 | Valid | Cho create khi event status la `OPEN`. |
| 19 | `createTrack` | `createTrack_shouldThrowBadRequest_whenEventStatusIsNull` | 1 | Invalid | Chan event status null. |
| 20 | `createTrack` | `createTrack_shouldThrowBadRequest_whenEventStatusIsBlank` | 1 | Invalid | Chan event status blank. |
| 21 | `createTrack` | `createTrack_shouldThrowBadRequest_whenRequestIsNull` | 1 | Invalid | Chan request null, khong save. |
| 22 | `createTrack` | `createTrack_shouldThrowBadRequest_whenTrackNameIsNull` | 1 | Invalid | Chan name null, khong save. |
| 23 | `createTrack` | `createTrack_shouldThrowBadRequest_whenTrackNameIsBlank` | 2 | Invalid | Chan name `""` va `"   "`. |
| 24 | `createTrack` | `createTrack_shouldThrowBadRequest_whenTrackNameExceedsLimit` | 1 | Invalid | Chan name dai 256 ky tu. |
| 25 | `createTrack` | `createTrack_shouldThrowBadRequest_whenDescriptionExceedsLimit` | 1 | Invalid | Chan description dai 2001 ky tu. |
| 26 | `createTrack` | `createTrack_shouldThrowBadRequest_whenNormalizedNameAlreadyExists` | 1 | Invalid | Chan duplicate name sau normalize `UPPER(TRIM(name))`. |
| 27 | `updateTrack` | `updateTrack_shouldUpdateNameAndDescription_whenEventIsDraft` | 1 | Valid | Update name/description khi event `DRAFT`, trim gia tri. |
| 28 | `updateTrack` | `updateTrack_shouldUpdateDescriptionOnly_whenNameIsNull` | 1 | Valid | Name null thi khong doi name, update description, khong check duplicate name. |
| 29 | `updateTrack` | `updateTrack_shouldClearDescription_whenDescriptionIsBlank` | 1 | Valid | Blank description duoc set thanh `null`. |
| 30 | `updateTrack` | `updateTrack_shouldThrowBadRequest_whenEventIdIsNull` | 1 | Invalid | Chan `eventId = null`, khong lookup event/track. |
| 31 | `updateTrack` | `updateTrack_shouldThrowBadRequest_whenTrackIdIsNull` | 1 | Invalid | Chan `trackId = null`, khong lookup event/track. |
| 32 | `updateTrack` | `updateTrack_shouldThrowResourceNotFound_whenEventDoesNotExist` | 1 | Invalid | Event khong ton tai thi nem `ResourceNotFoundException`, khong lookup track. |
| 33 | `updateTrack` | `updateTrack_shouldThrowBadRequest_whenEventStatusCannotMutateTracks` | 3 | Invalid | Chan update khi status la `ON-GOING`, `COMPLETED`, `CANCELLED`. |
| 34 | `updateTrack` | `updateTrack_shouldThrowBadRequest_whenEventStatusIsNull` | 1 | Invalid | Chan event status null. |
| 35 | `updateTrack` | `updateTrack_shouldThrowBadRequest_whenEventStatusIsBlank` | 1 | Invalid | Chan event status blank. |
| 36 | `updateTrack` | `updateTrack_shouldThrowResourceNotFound_whenTrackDoesNotExist` | 1 | Invalid | Track khong ton tai thi nem `ResourceNotFoundException`, khong save. |
| 37 | `updateTrack` | `updateTrack_shouldThrowBadRequest_whenTrackDoesNotBelongToEvent` | 1 | Invalid | Chan update track thuoc event khac. |
| 38 | `updateTrack` | `updateTrack_shouldThrowBadRequest_whenRequestIsNull` | 1 | Invalid | Chan request null, khong save. |
| 39 | `updateTrack` | `updateTrack_shouldThrowBadRequest_whenNameIsBlank` | 2 | Invalid | Chan name `""` va `"   "`. |
| 40 | `updateTrack` | `updateTrack_shouldThrowBadRequest_whenNameExceedsLimit` | 1 | Invalid | Chan name dai 256 ky tu. |
| 41 | `updateTrack` | `updateTrack_shouldThrowBadRequest_whenDescriptionExceedsLimit` | 1 | Invalid | Chan description dai 2001 ky tu. |
| 42 | `updateTrack` | `updateTrack_shouldThrowBadRequest_whenNormalizedNameAlreadyExists` | 1 | Invalid | Chan duplicate normalized name khi update. |
| 43 | `deleteTrack` | `deleteTrack_shouldDeleteTrack_whenEventIsSetUpAndTrackHasNoTeams` | 1 | Valid | Event `SET UP`, track khong co teams, delete track. |
| 44 | `deleteTrack` | `deleteTrack_shouldAcceptHyphenatedSetUpStatus` | 1 | Valid | Status `set-up` normalize thanh `SETUP`, delete thanh cong. |
| 45 | `deleteTrack` | `deleteTrack_shouldThrowBadRequest_whenEventIdIsNull` | 1 | Invalid | Chan `eventId = null`, khong lookup event/track. |
| 46 | `deleteTrack` | `deleteTrack_shouldThrowBadRequest_whenTrackIdIsNull` | 1 | Invalid | Chan `trackId = null`, khong lookup event/track. |
| 47 | `deleteTrack` | `deleteTrack_shouldThrowResourceNotFound_whenEventDoesNotExist` | 1 | Invalid | Event khong ton tai thi nem `ResourceNotFoundException`. |
| 48 | `deleteTrack` | `deleteTrack_shouldThrowBadRequest_whenEventStatusCannotMutateTracks` | 3 | Invalid | Chan delete khi status la `ON-GOING`, `COMPLETED`, `CANCELLED`. |
| 49 | `deleteTrack` | `deleteTrack_shouldThrowBadRequest_whenEventStatusIsNull` | 1 | Invalid | Chan event status null. |
| 50 | `deleteTrack` | `deleteTrack_shouldThrowBadRequest_whenEventStatusIsBlank` | 1 | Invalid | Chan event status blank. |
| 51 | `deleteTrack` | `deleteTrack_shouldThrowResourceNotFound_whenTrackDoesNotExist` | 1 | Invalid | Track khong ton tai thi nem `ResourceNotFoundException`; khong query teams/delete. |
| 52 | `deleteTrack` | `deleteTrack_shouldThrowBadRequest_whenTrackDoesNotBelongToEvent` | 1 | Invalid | Chan delete track thuoc event khac. |
| 53 | `deleteTrack` | `deleteTrack_shouldUnassignTeamsThenDelete_whenTrackHasTeams` | 1 | Valid | Teams tren track bi set `track = null`, saveAll teams, delete track. |

## Test Commands Run

Related test:

```powershell
& 'C:\Users\daonh\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd' -Dtest=TrackServiceTest test
```

Result:

- Tests run: 64
- Failures: 0
- Errors: 0
- Skipped: 0

Full test suite:

```powershell
& 'C:\Users\daonh\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd' test
```

Result:

- Tests run: 171
- Failures: 0
- Errors: 0
- Skipped: 0

## Notes For Review

- Co mot diem can review ky: yeu cau truoc do tung noi `deleteTrack` chi duoc xoa khi track khong co team. Code/test hien tai lai xac nhan hanh vi moi: neu co team thi unassign teams (`track = null`) roi xoa track. Neu business rule cu van dung, can doi lai service va test `deleteTrack_shouldUnassignTeamsThenDelete_whenTrackHasTeams` thanh invalid case.
- Co mot diem can review ky nua: yeu cau truoc do tung noi `createTrack` chi duoc tao khi event `DRAFT`. Code/test hien tai cho phep ca `OPEN`. Neu business rule can DRAFT-only, can sua `TRACK_CREATE_ALLOWED_EVENT_STATUSES` va test `createTrack_shouldCreateTrack_whenEventIsOpen`.
- Code hien tai dung status `SETUP` sau normalize, trong khi input UI/API co the gui `SET UP`, `SET_UP` hoac `set-up`; service da normalize cac bien the nay.
- Test invalid status dang cover `ON-GOING`, nhung `HackathonEventService` hien tai con dung `IN_PROGRESS` trong mot so logic. Can dong bo naming status truoc khi production de tranh FE/BE lech nhau.
- Entity-level unique constraint `uq_track_event_name` khong tu update DB vi project dang `spring.jpa.hibernate.ddl-auto=none`. Muon DB enforce duplicate that su thi can migration/schema SQL rieng.
- Repository duplicate normalized query giam loi duplicate do hoa/thuong/khoang trang, nhung unit test voi Mockito khong simulate race condition 2 request song song.
- Controller-level Bean Validation `@Size` trong `CreateTrackRequest` chua co controller test rieng; service da validate lai nen bypass controller van bi chan.
- `TrackResponse.capacity` da duoc map trong service. Test hien tai chua co assertion rieng cho capacity; neu capacity la output quan trong cua UI, nen them assertion rieng.
- Lan dau chay Maven trong sandbox bi chan network khi resolve parent POM; sau do da chay bang Maven binary voi quyen network va full suite pass.
