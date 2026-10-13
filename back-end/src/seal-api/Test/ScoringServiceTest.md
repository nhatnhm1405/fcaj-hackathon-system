# ScoringService Validation & Test Report

File service: `src/main/java/com/seal/hackathon/service/ScoringService.java`

File test: `src/test/java/com/seal/hackathon/service/ScoringServiceTest.java`

Files lien quan truc tiep:

- `src/main/java/com/seal/hackathon/controller/ScoringController.java`
- `src/main/java/com/seal/hackathon/dto/request/CreateCriteriaRequest.java`
- `src/main/java/com/seal/hackathon/dto/request/SubmitScoresRequest.java`
- `src/main/java/com/seal/hackathon/dto/response/ScoreResponse.java`
- `src/main/java/com/seal/hackathon/dto/response/ScoringCriteriaResponse.java`
- `src/main/java/com/seal/hackathon/repository/ScoreRepository.java`
- `src/main/java/com/seal/hackathon/repository/ScoringCriteriaRepository.java`
- `src/main/java/com/seal/hackathon/repository/SubmissionRepository.java`
- `src/main/java/com/seal/hackathon/repository/UserRepository.java`

Quy uoc status:

- `Valid`: service xu ly thanh cong, khong nem exception.
- `Invalid`: service chan request/action khong hop le bang exception.

Tong so public function trong `ScoringService`: **5**

Tong so JUnit executions trong `ScoringServiceTest`: **33**

## Business Rules Implemented

- `getCriteriaByRound` map criteria theo order tu repository sang response.
- `createCriteria` yeu cau event ton tai va round thuoc event.
- `submitScores` validate request null, `submissionId` null, score list null/empty.
- Judge phai ton tai.
- Judge phai duoc assign voi submission qua `findBySubmissionIdAndJudgeId`.
- Submission missing va unassigned judge dung message chung `Submission not found or not assigned to this judge.`
- Moi score entry phai non-null va co `criteriaId`, `value`.
- Criteria phai ton tai va phai thuoc round cua submission.
- Score phai trong khoang `0..criteria.maxScore`.
- Batch submit nhieu criteria trong mot request duoc save tung entry.
- Existing score theo `(submission, judge, criteria)` duoc update thay vi tao duplicate.
- `draft` flag va `comment` duoc persist vao `Score`.
- `getScoresBySubmission`: coordinator xem existing submission; judge phai assigned; role/authorities khac bi forbid.
- `getMyScoresByRound` query theo judge va round.

## Implementation Summary

| Area | Change / coverage |
|---|---|
| Service mismatch fix | `ScoringService.getScoresBySubmission` da duoc sua ve signature `(requesterId, authorities, submissionId)` de khop controller/test. |
| Assignment guard | `submitScores` va judge score read dung `findBySubmissionIdAndJudgeId`. |
| Request validation | Cover null request, null submissionId, scores null/empty, entry null, missing fields. |
| Score validation | Cover criteria missing, wrong/null criteria round, negative score, score over max, zero/max accepted. |
| Persistence behavior | Cover create score, update existing score, draft/comment, multiple entries. |
| Read authorization | Cover coordinator, judge, forbidden role, null authorities, missing submission/unassigned judge. |

## Public Function Coverage Summary

| Function | JUnit executions | Coverage chinh |
|---|---:|---|
| `getCriteriaByRound` | 2 | Non-empty and empty criteria list |
| `createCriteria` | 3 | Success, event missing, round not in event |
| `submitScores` | 21 | Success/update/batch/range/request/assignment/criteria validation |
| `getScoresBySubmission` | 6 | Coordinator, judge, missing/unassigned, forbidden/null authorities |
| `getMyScoresByRound` | 2 | Non-empty and empty scores |

## Detailed Test Cases

| # | Function | Test case | Status | Expected result |
|---:|---|---|---|---|
| 1 | `getCriteriaByRound` | `getCriteriaByRound_shouldReturnAllCriteriaForRound` | Valid | Tra criteria theo order va map fields. |
| 2 | `getCriteriaByRound` | `getCriteriaByRound_shouldReturnEmptyList_whenNoCriteriaExist` | Valid | Tra list rong. |
| 3 | `createCriteria` | `createCriteria_shouldSaveCriteria_whenEventAndRoundExist` | Valid | Save criteria va trim name. |
| 4 | `createCriteria` | `createCriteria_shouldThrowNotFound_whenEventDoesNotExist` | Invalid | Missing event, khong save. |
| 5 | `createCriteria` | `createCriteria_shouldThrowNotFound_whenRoundDoesNotBelongToEvent` | Invalid | Round missing/sai event, khong save. |
| 6 | `submitScores` | `submitScores_shouldSaveScores_whenJudgeIsAssignedToSubmission` | Valid | Assigned judge save score moi. |
| 7 | `submitScores` | `submitScores_shouldUpdateExistingScore_whenScoreAlreadyExists` | Valid | Existing score duoc update. |
| 8 | `submitScores` | `submitScores_shouldSetScoreAtExactMaxScore` | Valid | Score bang max duoc chap nhan. |
| 9 | `submitScores` | `submitScores_shouldSetScoreAtZero` | Valid | Score 0 duoc chap nhan. |
| 10 | `submitScores` | `submitScores_shouldPersistDraftFlagAndComment` | Valid | Persist `isDraft=true` va comment. |
| 11 | `submitScores` | `submitScores_shouldSaveMultipleEntriesInOneRequest` | Valid | Batch 2 criteria save 2 scores. |
| 12 | `submitScores` | `submitScores_shouldThrowBadRequest_whenRequestIsNull` | Invalid | Request null bi chan truoc repository. |
| 13 | `submitScores` | `submitScores_shouldThrowBadRequest_whenSubmissionIdIsNull` | Invalid | `submissionId` null bi chan. |
| 14 | `submitScores` | `submitScores_shouldThrowBadRequest_whenScoresListIsEmpty` | Invalid | Scores empty bi chan. |
| 15 | `submitScores` | `submitScores_shouldThrowBadRequest_whenScoresListIsNull` | Invalid | Scores null bi chan. |
| 16 | `submitScores` | `submitScores_shouldThrowNotFound_whenJudgeDoesNotExist` | Invalid | Missing judge bi not found. |
| 17 | `submitScores` | `submitScores_shouldThrowNotFound_whenSubmissionIsNotAssignedToJudge` | Invalid | Unassigned judge dung message chung. |
| 18 | `submitScores` | `submitScores_shouldThrowBadRequest_whenScoreEntryHasNullCriteriaId` | Invalid | Entry thieu criteriaId bi chan. |
| 19 | `submitScores` | `submitScores_shouldThrowBadRequest_whenScoreEntryIsNull` | Invalid | Null entry bi chan. |
| 20 | `submitScores` | `submitScores_shouldThrowBadRequest_whenScoreEntryHasNullValue` | Invalid | Entry thieu value bi chan. |
| 21 | `submitScores` | `submitScores_shouldThrowNotFound_whenCriteriaDoesNotExist` | Invalid | Missing criteria bi not found. |
| 22 | `submitScores` | `submitScores_shouldThrowBadRequest_whenCriteriaDoesNotBelongToSubmissionRound` | Invalid | Criteria round khac submission round bi chan. |
| 23 | `submitScores` | `submitScores_shouldThrowBadRequest_whenCriteriaRoundIsNull` | Invalid | Criteria round null bi chan. |
| 24 | `submitScores` | `submitScores_shouldThrowBadRequest_whenScoreIsNegative` | Invalid | Score am bi chan. |
| 25 | `submitScores` | `submitScores_shouldThrowBadRequest_whenScoreExceedsMaxScore` | Invalid | Score vuot max bi chan. |
| 26 | `getScoresBySubmission` | `getScoresBySubmission_shouldReturnScores_whenCoordinatorRequests` | Valid | Coordinator xem scores cua submission ton tai. |
| 27 | `getScoresBySubmission` | `getScoresBySubmission_shouldRequireJudgeAssignmentForJudgeRole` | Valid | Judge read phai qua assignment query. |
| 28 | `getScoresBySubmission` | `getScoresBySubmission_shouldThrowNotFound_whenCoordinatorAndSubmissionNotFound` | Invalid | Coordinator request submission missing. |
| 29 | `getScoresBySubmission` | `getScoresBySubmission_shouldThrowNotFound_whenJudgeNotAssigned` | Invalid | Judge unassigned bi not found/message chung. |
| 30 | `getScoresBySubmission` | `getScoresBySubmission_shouldThrowForbidden_whenOtherRole` | Invalid | Participant/other role bi forbid. |
| 31 | `getScoresBySubmission` | `getScoresBySubmission_shouldThrowForbidden_whenAuthoritiesAreNull` | Invalid | Null authorities bi forbid. |
| 32 | `getMyScoresByRound` | `getMyScoresByRound_shouldReturnScoresForJudgeAndRound` | Valid | Tra score cua judge trong round. |
| 33 | `getMyScoresByRound` | `getMyScoresByRound_shouldReturnEmptyList_whenNoScoresExist` | Valid | Tra list rong. |

## Test Commands Run

Targeted suite:

```powershell
& 'C:\Users\DAO HOANG NHAT\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd' '-Dtest=SubmissionServiceTest,ScoringServiceTest,SystemLogServiceTest' test
```

Result for this file:

- Tests run: 33
- Failures: 0
- Errors: 0
- Skipped: 0

Full suite:

- Tests run: 285
- Failures: 0
- Errors: 0
- Skipped: 0

## Notes For Review

- `createCriteria` van chu yeu dua vao controller `@Valid` cho request fields; service test hien cover resource checks, chua harden null/blank request field o service-level.
- `getScoresBySubmission` hien cho assigned judge xem tat ca scores cua submission. Neu product muon judge chi xem diem cua minh thi can doi behavior va test.
