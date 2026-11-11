# SubmissionService Validation & Test Report

File service: `src/main/java/com/seal/hackathon/service/SubmissionService.java`

File test: `src/test/java/com/seal/hackathon/service/SubmissionServiceTest.java`

Files lien quan truc tiep:

- `src/main/java/com/seal/hackathon/controller/SubmissionController.java`
- `src/main/java/com/seal/hackathon/dto/request/SubmitRequest.java`
- `src/main/java/com/seal/hackathon/dto/response/SubmissionResponse.java`
- `src/main/java/com/seal/hackathon/entity/Submission.java`
- `src/main/java/com/seal/hackathon/repository/SubmissionRepository.java`
- `src/main/java/com/seal/hackathon/repository/TeamMemberRepository.java`
- `src/main/java/com/seal/hackathon/repository/RoundRepository.java`
- `src/main/java/com/seal/hackathon/repository/UserRepository.java`

Quy uoc status:

- `Valid`: service xu ly thanh cong, khong nem exception.
- `Invalid`: service chan request/action khong hop le bang exception.

Tong so public function trong `SubmissionService`: **4**

Tong so JUnit executions trong `SubmissionServiceTest`: **46**

## Business Rules Implemented

- `SubmitRequest` duoc validate o service-level: request, `roundId`, URL, description.
- `repoUrl` required, trim, max 500, chi chap nhan `http://`/`https://`, khong co whitespace.
- `demoUrl` va `slideUrl` optional; blank -> `null`; neu co thi validate nhu URL.
- `description` optional; blank -> `null`; max 5000.
- Submitter phai la student (`FPT_STUDENT` hoac `EXTERNAL_STUDENT`), khong chi dua vao team role.
- Round phai ton tai va status phai la `ACTIVE` hoac `OPEN`.
- User phai thuoc team trong cung event voi round.
- Team phai `APPROVED`.
- Chi `LEADER` moi duoc submit/update.
- Qua `submissionDeadline` thi bi chan, accepted submission luon la `SUBMITTED`.
- Existing submission theo `(teamId, roundId)` duoc update thay vi tao duplicate.
- `getMySubmission` chi tra submission cua team user trong event/round do.
- `getSubmissionsByRound`: coordinator xem all, judge xem assigned, role khac bi forbid.
- `getSubmissionById`: coordinator xem all, student xem own-team, judge xem assigned, role khac bi forbid.

## Implementation Summary

| Area | Change / coverage |
|---|---|
| Submit validation | Cover request null, roundId null, repo/demo/slide invalid/too long, description too long. |
| Submit authorization | Cover non-student, non-leader, user not in team, team not approved. |
| Submit lifecycle | Cover round not found, invalid round status, `OPEN` accepted, deadline passed. |
| Submit upsert | Cover create new submission and update existing submission. |
| Read own submission | Cover success, round missing, no team, submission missing. |
| Round list | Cover coordinator all, judge assigned, round missing, role forbidden. |
| Single read | Cover coordinator, student owner/non-owner, judge assigned/unassigned, requester missing, submission missing, role forbidden. |

## Public Function Coverage Summary

| Function | JUnit executions | Coverage chinh |
|---|---:|---|
| `submit` | 25 | Request validation, URL/description limits, student-only, leader-only, team/round/deadline, create/update |
| `getMySubmission` | 4 | Success and not-found branches |
| `getSubmissionsByRound` | 4 | Coordinator, judge, round missing, forbidden role |
| `getSubmissionById` | 8 | Coordinator, student owner/non-owner, judge assigned/unassigned, requester/submission missing, forbidden role |

## Detailed Test Cases

| # | Function | Test case | JUnit count | Status | Expected result |
|---:|---|---|---:|---|---|
| 1 | `submit` | `submit_shouldSaveSubmission_whenUserIsLeaderAndBeforeDeadline_NewSubmission` | 1 | Valid | Student leader tao submission moi, trim repo/demo/slide/description, status `SUBMITTED`. |
| 2 | `submit` | `submit_shouldUpdateSubmission_whenSubmissionAlreadyExists` | 1 | Valid | Existing submission duoc update, optional blank fields -> `null`. |
| 3 | `submit` | `submit_shouldThrowBadRequest_whenRequestIsNull` | 1 | Invalid | Request null bi chan. |
| 4 | `submit` | `submit_shouldThrowBadRequest_whenRoundIdIsNull` | 1 | Invalid | Round id null bi chan. |
| 5 | `submit` | `submit_shouldThrowBadRequest_whenRepoUrlIsInvalid` | 6 | Invalid | Repo null/empty/blank/non-http/space/no-scheme bi chan. |
| 6 | `submit` | `submit_shouldThrowBadRequest_whenRepoUrlIsTooLong` | 1 | Invalid | Repo URL > 500 bi chan. |
| 7 | `submit` | `submit_shouldThrowBadRequest_whenDemoUrlIsTooLong` | 1 | Invalid | Demo URL > 500 bi chan. |
| 8 | `submit` | `submit_shouldThrowBadRequest_whenDemoUrlIsInvalid` | 3 | Invalid | Demo URL invalid bi chan. |
| 9 | `submit` | `submit_shouldThrowBadRequest_whenSlideUrlIsInvalid` | 3 | Invalid | Slide URL invalid bi chan. |
| 10 | `submit` | `submit_shouldThrowBadRequest_whenSlideUrlIsTooLong` | 1 | Invalid | Slide URL > 500 bi chan. |
| 11 | `submit` | `submit_shouldThrowBadRequest_whenDescriptionIsTooLong` | 1 | Invalid | Description > 5000 bi chan. |
| 12 | `submit` | `submit_shouldThrowNotFound_whenUserDoesNotExist` | 1 | Invalid | Missing submitter bi not found. |
| 13 | `submit` | `submit_shouldThrowForbidden_whenSubmitterIsNotStudent` | 1 | Invalid | Staff user bi chan truoc khi lookup round/team. |
| 14 | `submit` | `submit_shouldThrowNotFound_whenRoundDoesNotExist` | 1 | Invalid | Missing round bi not found. |
| 15 | `submit` | `submit_shouldThrowBadRequest_whenRoundStatusIsInvalid` | 2 | Invalid | `DRAFT`/`CLOSED` bi chan. |
| 16 | `submit` | `submit_shouldAllowSubmit_whenRoundStatusIsOpen` | 1 | Valid | `OPEN` round duoc submit. |
| 17 | `submit` | `submit_shouldThrowBadRequest_whenUserNotInTeamForEvent` | 1 | Invalid | Khong co membership trong event bi chan. |
| 18 | `submit` | `submit_shouldThrowBadRequest_whenTeamStatusIsNotApproved` | 1 | Invalid | Team not approved bi chan. |
| 19 | `submit` | `submit_shouldThrowForbidden_whenUserIsNotLeader` | 1 | Invalid | Non-leader bi chan. |
| 20 | `submit` | `submit_shouldThrowBadRequest_whenDeadlineHasPassed` | 1 | Invalid | Qua deadline bi chan, khong save. |
| 21 | `getMySubmission` | `getMySubmission_shouldReturnSubmission_whenExists` | 1 | Valid | Tra submission cua team user. |
| 22 | `getMySubmission` | `getMySubmission_shouldThrowNotFound_whenRoundNotFound` | 1 | Invalid | Missing round. |
| 23 | `getMySubmission` | `getMySubmission_shouldThrowNotFound_whenUserNotInTeam` | 1 | Invalid | User khong thuoc team trong event. |
| 24 | `getMySubmission` | `getMySubmission_shouldThrowNotFound_whenSubmissionDoesNotExist` | 1 | Invalid | Team chua co submission. |
| 25 | `getSubmissionsByRound` | `getSubmissionsByRound_shouldReturnAllSubmissions_whenCoordinator` | 1 | Valid | Coordinator dung query all by round. |
| 26 | `getSubmissionsByRound` | `getSubmissionsByRound_shouldReturnOnlyAssignedSubmissionsForJudge` | 1 | Valid | Judge dung assignment query. |
| 27 | `getSubmissionsByRound` | `getSubmissionsByRound_shouldThrowNotFound_whenRoundDoesNotExist` | 1 | Invalid | Missing round. |
| 28 | `getSubmissionsByRound` | `getSubmissionsByRound_shouldThrowForbidden_whenOtherRole` | 1 | Invalid | Role khac bi forbid. |
| 29 | `getSubmissionById` | `getSubmissionById_shouldAllowCoordinatorToReadAll` | 1 | Valid | Coordinator doc submission ton tai. |
| 30 | `getSubmissionById` | `getSubmissionById_shouldAllowStudent_whenSubmissionBelongsToTeam` | 1 | Valid | Student owner doc duoc. |
| 31 | `getSubmissionById` | `getSubmissionById_shouldThrowForbidden_whenStudentDoesNotOwnTeam` | 1 | Invalid | Student non-owner bi forbid. |
| 32 | `getSubmissionById` | `getSubmissionById_shouldUseJudgeAssignment_whenRequesterIsJudge` | 1 | Valid | Judge assigned dung assignment query. |
| 33 | `getSubmissionById` | `getSubmissionById_shouldThrowNotFound_whenJudgeIsNotAssigned` | 1 | Invalid | Judge unassigned nhan message chung. |
| 34 | `getSubmissionById` | `getSubmissionById_shouldThrowForbidden_whenOtherRole` | 1 | Invalid | Staff/no valid role bi forbid. |
| 35 | `getSubmissionById` | `getSubmissionById_shouldThrowNotFound_whenRequesterNotFound` | 1 | Invalid | Requester missing. |
| 36 | `getSubmissionById` | `getSubmissionById_shouldThrowNotFound_whenCoordinatorAndSubmissionNotFound` | 1 | Invalid | Coordinator request submission missing. |

## Test Commands Run

Targeted suite:

```powershell
& 'C:\Users\DAO HOANG NHAT\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd' '-Dtest=SubmissionServiceTest,ScoringServiceTest,SystemLogServiceTest' test
```

Result for this file:

- Tests run: 46
- Failures: 0
- Errors: 0
- Skipped: 0

Full suite:

- Tests run: 285
- Failures: 0
- Errors: 0
- Skipped: 0

## Notes For Review

- Da them guard moi: non-student submitter bi chan bang `ForbiddenException`.
- Chua test race condition 2 first-submit cung luc cho unique `(team_id, round_id)`.
- `getMySubmission` hien khong check userType; neu endpoint nay can student-only guard, can harden them.
