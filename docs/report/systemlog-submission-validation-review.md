# SystemLogService & SubmissionService Validation/Logic Review

## Thong tin chung

- Ngay review: 2026-06-24
- Pham vi: `SystemLogService`, `SubmissionService`, DTO/entity/repository/controller va cac luong lien quan den submission/scoring/system-log.
- Cach lam: static review source code theo `unit_testing_prompt.md`; khong chinh sua source code goc va chua viet unit test moi.

## Tom tat nhanh

`SystemLogService` dang kha gon va dung muc tieu "best-effort" khi actor khong ton tai, nhung implementation chua that su "never blocks" voi input null/DB error, khong ghi duoc IP, va khong validate `action`.

`SubmissionService` co happy path upsert ro rang, co check round `ACTIVE`, membership theo event, va team `APPROVED`. Tuy nhien co cac khoang ho validation/authorization dang nam o backend: member khong phai leader van co the submit neu goi API truc tiep, sau deadline van co the tao/cap nhat submission voi status `LATE`, va judge/coordinator/participant co the doc submission qua API rong hon rule trong tai lieu.

## Findings

### P1 - Non-leader participant co the submit/cap nhat submission bang API

- Evidence:
  - `SubmissionController.submit()` chi yeu cau role `PARTICIPANT`: `back-end/src/seal-api/src/main/java/com/seal/hackathon/controller/SubmissionController.java:24-31`.
  - `SubmissionService.submit()` chi tim membership trong event, khong kiem tra `TeamMember.memberRole == LEADER`: `back-end/src/seal-api/src/main/java/com/seal/hackathon/service/SubmissionService.java:42-56`.
  - Frontend lai chi cho leader submit: `front-end/src/seal-web/src/features/submissions/TeamSubmitPage.tsx:30` va `:84-86`.
- Impact: bat ky team member co role `PARTICIPANT` co the bypass UI va overwrite submission cua team trong round active.
- Recommendation: backend nen enforce leader-only neu business rule la "Only the team leader can submit"; throw `ForbiddenException` hoac `BadRequestException` khi `memberRole` khong phai `LEADER`.
- Unit test nen co:
  - `submit_WhenUserIsTeamMemberButNotLeader_ThenThrowsForbiddenOrBadRequest`
  - `submit_WhenUserIsLeader_ThenSavesSubmission`

### P1 - Sau deadline van co the submit/cap nhat, trai voi tai lieu va frontend

- Evidence:
  - Service tinh `status = LATE` khi `now.isAfter(round.getSubmissionDeadline())`, roi van save: `SubmissionService.java:58-80`.
  - Frontend khoa form khi qua deadline: `TeamSubmitPage.tsx:84-86` va hien "submission window is closed": `TeamSubmitPage.tsx:186-189`.
  - Tai lieu role noi sau deadline submission bi lock: `front-end/src/seal-web/src/imports/pasted_text/hackathon-roles.md:184-185`; management note cung noi form read-only sau deadline: `hackathon-management.md:110-111`.
- Impact: nguoi dung goi API truc tiep co the tao submission tre hoac overwrite submission da nop sau deadline. Neu scoring da bat dau, du lieu judge dang xem co the bi thay doi.
- Recommendation: chot rule nghiep vu:
  - Neu he thong khong chap nhan nop tre: throw `BadRequestException` sau deadline.
  - Neu chap nhan nop tre: cap nhat lai frontend/tai lieu va them audit/log de coordinator thay doi ro rang; can can nhac khoa update neu submission da ton tai va da qua deadline.
- Unit test nen co:
  - `submit_WhenAfterDeadline_ThenThrowsBadRequest` neu lock.
  - Hoac `submit_WhenAfterDeadlineAllowed_ThenStatusLate` neu chap nhan late.
  - `submit_WhenExistingSubmissionAfterDeadline_ThenDoesNotOverwrite` neu muon lock update.

### P1 - Submission read/list khong rang buoc owner/assignment

- Evidence:
  - `GET /api/submissions/{submissionId}` cho `PARTICIPANT`, `JUDGE`, `EVENT_COORDINATOR` nhung service khong nhan authentication context de check owner/assignment: `SubmissionController.java:51-55`, `SubmissionService.java:122-126`.
  - `GET /api/submissions/round/{roundId}` cho moi `JUDGE` hoac `EVENT_COORDINATOR`, service tra tat ca submissions cua round: `SubmissionController.java:44-48`, `SubmissionService.java:111-117`.
  - Tai lieu yeu cau judge chi thay submissions trong assignment: `front-end/src/seal-web/src/imports/pasted_text/hackathon-management.md:55` va `hackathon-roles.md:662-664`.
- Impact: participant co the doc submission cua team khac neu biet `submissionId`; judge co the xem repo/demo/slide cua submission ngoai round/track duoc assign.
- Recommendation: tach API:
  - Participant: `getMySubmission` hoac `getSubmissionById(userId, submissionId)` check team owner.
  - Judge: list/read theo `JudgeAssignment` va track/final-round.
  - Coordinator: co the xem tat ca trong event minh quan ly neu co event-scoped role.
- Unit test nen co:
  - `getSubmissionById_WhenParticipantDoesNotOwnTeam_ThenThrowsForbidden`
  - `getSubmissionsByRound_WhenJudgeNotAssigned_ThenReturnsEmptyOrThrowsForbidden`
  - `getSubmissionsByRound_WhenJudgeAssignedToTrack_ThenReturnsOnlyTrackSubmissions`

### P1 - Scoring flow lien quan cung thieu guard assignment

- Evidence:
  - `ScoringService.submitScores()` load judge va submission, nhung khong kiem tra judge co assignment cho submission/round/track do: `back-end/src/seal-api/src/main/java/com/seal/hackathon/service/ScoringService.java:63-98`.
  - `ScoringController.submitScores()` chi check role `JUDGE`: `ScoringController.java:49-56`.
- Impact: judge co role `JUDGE` co the submit/update score cho submission bat ky neu biet `submissionId` va criteria id hop le.
- Recommendation: them validation assignment trong scoring, hoac dung chung helper authorization voi submission list.
- Unit test nen co:
  - `submitScores_WhenJudgeNotAssignedToSubmissionRound_ThenThrowsForbidden`
  - `submitScores_WhenJudgeAssignedToTrack_ThenAllowsOnlyTrackSubmission`

### P2 - SubmitRequest validation qua mong so voi DB va frontend

- Evidence:
  - `SubmitRequest` chi `@NotNull` cho `roundId`; `repoUrl`, `demoUrl`, `slideUrl`, `description` khong co `@NotBlank`, `@Size`, hay URL validation: `SubmitRequest.java:7-13`.
  - DB/entity gioi han URL 500 ky tu: `Submission.java:32-39`, `seal_schema.sql:233-235`.
  - Frontend bat buoc repo URL: `TeamSubmitPage.tsx:91-95`.
- Impact: service co the luu submission khong co repo URL, URL qua dai co the gay DB exception 500 thay vi loi validation 400, va format URL sai van vao DB.
- Recommendation: them validation theo rule:
  - `repoUrl`: `@NotBlank`, `@Size(max = 500)`, optional pattern/domain rule neu can.
  - `demoUrl`, `slideUrl`: `@Size(max = 500)` va trim blank thanh null.
  - `description`: `@Size(max = ...)` neu muon gioi han.
- Unit test nen co:
  - `submit_WhenRepoUrlBlank_ThenThrowsBadRequest`
  - `submit_WhenUrlLongerThan500_ThenThrowsBadRequest`
  - `submit_WhenOptionalUrlBlank_ThenStoresNullOrBlankConsistently`

### P2 - Upsert submission co race condition o lan submit dau

- Evidence:
  - Service lam `findByTeam_TeamIdAndRound_RoundId(...).orElse(null)` roi `save()`: `SubmissionService.java:61-80`.
  - DB co unique constraint `(team_id, round_id)`: `Submission.java:8-11`, `seal_schema.sql:241`.
- Impact: hai request dong thoi cua cung team/round co the deu khong thay record, cung insert, mot request fail bang DB unique violation thay vi response domain ro rang.
- Recommendation: them lock query (`findBy...ForUpdate`) hoac bat `DataIntegrityViolationException` de reload/update/tra loi 409/400; neu leader-only thi risk giam nhung van co double-click/race.
- Unit test/integration nen co:
  - Unit verify service goi repository method lock neu them.
  - Integration/concurrency test neu can bao dam behavior DB.

### P2 - SystemLogService.record khong that su "never blocks"

- Evidence:
  - Comment noi "never blocks the business action": `SystemLogService.java:27-30`.
  - Code goi `userRepository.findById(actorUserId)` truc tiep; neu `actorUserId == null`, Spring Data se throw truoc khi no-op: `SystemLogService.java:31-35`.
  - `systemLogRepository.save(...)` exception khong duoc bat: `SystemLogService.java:37-41`.
- Impact: mot loi logging input/DB co the rollback action admin dang goi trong transaction ngoai, trai voi y do best-effort.
- Recommendation: neu dung best-effort that su, validate null actor truoc khi find, va can nhac catch/log exception trong `record()` hoac tach transaction `REQUIRES_NEW` + swallow controlled exceptions.
- Unit test nen co:
  - `record_WhenActorUserIdNull_ThenDoesNotThrowAndDoesNotSave`
  - `record_WhenSaveFails_ThenDoesNotPropagate` neu chon best-effort.

### P2 - SystemLog action/detail chua duoc validate/normalize

- Evidence:
  - Entity/DB yeu cau `action` not null va max 50: `SystemLog.java:36-42`, `seal_schema.sql:454-458`.
  - `record()` nhan `action`, `detail` va save thang: `SystemLogService.java:32-41`.
- Impact: blank/null/qua-dai action co the tao log rac hoac gay DB exception; action case khong thong nhat lam UI search/filter kem on dinh.
- Recommendation: normalize `action = trim().toUpperCase()`, reject blank/too long bang no-op hoac `BadRequestException` tuy chon, va trim detail neu can.
- Unit test nen co:
  - `record_WhenActionBlank_ThenDoesNotSaveOrThrowsBadRequest`
  - `record_WhenActionHasLowercaseAndSpaces_ThenSavesNormalizedAction`
  - `record_WhenActionTooLong_ThenDoesNotHitDatabaseConstraint`

### P3 - SystemLog co cot IP nhung record khong ghi duoc IP

- Evidence:
  - `SystemLog` va response co `ipAddress`: `SystemLog.java:44-45`, `SystemLogResponse.java:27`.
  - `SystemLogService.record(actorUserId, action, detail)` khong nhan IP va khong set IP: `SystemLogService.java:32-41`.
  - Admin UI hien IP neu co: `AdminSystemLogsPage.tsx:71-94`.
- Impact: cot IP luon null voi log tao tu service, lam mat gia tri audit cho hanh dong admin nhay cam.
- Recommendation: them overload `record(actorUserId, action, detail, ipAddress)` va controller truyen IP tu request/header trusted proxy.
- Unit test nen co:
  - `record_WhenIpProvided_ThenSavesIpAddress`

### P3 - getAllLogs khong phan trang

- Evidence:
  - `SystemLogService.getAllLogs()` tra toan bo list: `SystemLogService.java:44-49`.
  - Repository query order newest-first nhung khong limit/page: `SystemLogRepository.java:17-18`.
- Impact: khi log lon, admin page co the cham/nang bo nho.
- Recommendation: dung `Pageable` cho `/api/admin/system-logs`, co filter/search server-side neu can.
- Unit test nen co:
  - Test mapper hien tai; paging nen la integration/controller-service test sau khi doi API.

### P3 - Round status naming mismatch giua FE va BE

- Evidence:
  - Frontend chon round dang open neu status `ACTIVE` hoac `OPEN`: `TeamSubmitPage.tsx:46`.
  - Backend chi chap nhan `ACTIVE`: `SubmissionService.java:38-40`.
  - `UpdateRoundRequest` comment status la `PENDING | ACTIVE | CLOSED | FINALIZED`: `UpdateRoundRequest.java:16`.
- Impact: neu round bi set `OPEN` tu UI/cu phap cu, frontend co the hien nhu submit duoc nhung API reject.
- Recommendation: chuan hoa enum/status duy nhat; neu backend chi dung `ACTIVE`, frontend khong nen coi `OPEN` la round-open.

### P3 - Cleanup nho

- Evidence:
  - `SubmissionService` inject `TeamRepository` nhung khong dung: `SubmissionService.java:23`.
  - Import `ForbiddenException` nhung khong dung: `SubmissionService.java:7`.
- Impact: khong gay loi runtime, nhung lam test setup/mock theo prompt bi nhieu hon can thiet va che mo intent.
- Recommendation: xoa dependency/import khong dung khi duoc phep refactor source.

## Logic hien tai dang dung

### SystemLogService

- `record()` tim actor theo id, actor khong ton tai thi no-op, actor ton tai thi save `SystemLog` voi actor/action/detail.
- `createdAt` duoc set boi entity `@PrePersist`.
- `getAllLogs()` dung query `JOIN FETCH actor ORDER BY createdAt DESC`, tranh N+1 va tra newest-first.
- Mapper tra du `logId`, actor id/name, action/detail, IP, createdAt.

### SubmissionService

- `submit()`:
  - Validate user ton tai.
  - Validate round ton tai.
  - Chi cho round status `ACTIVE`.
  - Tim team cua user trong cung event, voi event status `OPEN`/`IN_PROGRESS`.
  - Yeu cau team `APPROVED`.
  - Upsert theo unique key team + round.
  - Cap nhat links/description/submittedAt/status/submittedBy.
- `getMySubmission()`:
  - Validate round ton tai.
  - Tim membership trong event.
  - Tra submission cua team trong round hoac 404.
- `getSubmissionsByRound()`:
  - Validate round ton tai.
  - Tra tat ca submissions cua round.
- `getSubmissionById()`:
  - Validate submission ton tai.
  - Tra response.

## De xuat test coverage theo prompt

### SubmissionServiceTest nen them

- Happy path:
  - submit moi before deadline -> `SUBMITTED`, save.
  - update existing submission -> giu team/round, update fields, `submittedBy`, `submittedAt`.
  - getMySubmission -> dung team/round.
  - getSubmissionsByRound -> map list.
  - getSubmissionById -> map fields.
- Exception/validation:
  - user not found.
  - round not found.
  - round not `ACTIVE`.
  - user khong co team trong event.
  - team khong `APPROVED`.
  - non-leader submit neu enforce leader-only.
  - after deadline behavior theo rule da chot.
  - blank/long repo URL neu them validation.
  - unauthorized read submission cua team khac neu them owner check.

### SystemLogServiceTest nen them

- Happy path:
  - actor ton tai -> save log dung actor/action/detail.
  - getAllLogs -> map actor/action/detail/ip/createdAt.
- Exception/boundary:
  - actor khong ton tai -> no-op, khong save.
  - actor id null -> khong throw neu giu best-effort.
  - action blank/null/too long behavior theo rule da chot.
  - repository save fail khong lam fail caller neu giu best-effort.

## Ket luan

Hai service co skeleton tot cho unit test, nhung `SubmissionService` can duoc uu tien harden o backend vi hien co risk authorization va deadline bypass. `SystemLogService` it risk hon, nhung can lam ro "best-effort" va them validation nho de tranh logging lam hong business transaction.
