# TeamServiceTest Report

File test: `src/test/java/com/seal/hackathon/service/TeamServiceTest.java`

Quy uoc status:

- `Valid`: test case ky vong service xu ly thanh cong, khong nem exception.
- `Invalid`: test case ky vong service chan request/action khong hop le bang exception hoac tra ket qua rong cho input khong hop le.

Tong so test case trong `TeamServiceTest`: **75**

## Summary

| Function | So test | Coverage chinh |
|---|---:|---|
| `createTeam` | 10 | Success, user/event/track not found, event not open, registration window, track sai event, duplicate normalized name, user da co team |
| `getMyTeam` | 2 | Co team active, khong co team active |
| `updateTeam` | 10 | Success, quyen leader, team/user membership, locked team, duplicate normalized name, unchanged normalized name, blank name/description |
| `removeMember` | 8 | Success, leader tu remove, team/caller/target validation, locked team |
| `transferLeadership` | 7 | Success, self-transfer, team/caller/new leader validation, locked team |
| `leaveTeam` | 6 | Member leave, only leader leave, leader with members, team/user validation, locked team |
| `searchInvitableUsers` | 4 | Query invalid/null, limit 10, empty result |
| `getTeamsByEvent` | 3 | Event co team, event not found, event khong co team |
| `getTeamById` | 2 | Team detail, team not found |
| `approveTeam` | 5 | Chi approve `PENDING`, chan status khac |
| `rejectTeam` | 7 | Chi reject `PENDING`, null/blank reason, chan status khac |
| `disqualifyTeam` | 7 | Chi disqualify `APPROVED`, null/blank reason, chan status khac |
| `getActiveEventsWithTracks` | 4 | Registration window, no open events, expired registration |

## Detailed Test Cases

| # | Function | Test case | Status | Expected result |
|---:|---|---|---|---|
| 1 | `createTeam` | `createTeam_shouldCreatePendingTeamAndLeaderMember_whenRequestIsValid` | Valid | Tao team `PENDING`, trim team name, tao member role `LEADER`. |
| 2 | `createTeam` | `createTeam_shouldThrowBadRequest_whenEventIsNotOpen` | Invalid | Chan tao team khi event khong phai `OPEN`. |
| 3 | `createTeam` | `createTeam_shouldThrowBadRequest_whenNormalizedTeamNameAlreadyExists` | Invalid | Chan duplicate team name bang `UPPER(TRIM(name))`. |
| 4 | `createTeam` | `createTeam_shouldThrowResourceNotFound_whenEventDoesNotExist` | Invalid | Nem `ResourceNotFoundException` khi event khong ton tai. |
| 5 | `createTeam` | `createTeam_shouldThrowBadRequest_whenRegistrationHasNotStarted` | Invalid | Chan tao team truoc thoi gian registration start. |
| 6 | `createTeam` | `createTeam_shouldThrowBadRequest_whenRegistrationDeadlineHasPassed` | Invalid | Chan tao team sau registration deadline. |
| 7 | `createTeam` | `createTeam_shouldThrowResourceNotFound_whenTrackDoesNotExist` | Invalid | Nem `ResourceNotFoundException` khi track khong ton tai. |
| 8 | `createTeam` | `createTeam_shouldThrowBadRequest_whenTrackDoesNotBelongToEvent` | Invalid | Chan track khong thuoc event da chon. |
| 9 | `createTeam` | `createTeam_shouldThrowBadRequest_whenUserAlreadyHasTeamInEvent` | Invalid | Chan user da co team trong event. |
| 10 | `createTeam` | `createTeam_shouldThrowResourceNotFound_whenUserDoesNotExist` | Invalid | Nem `ResourceNotFoundException` khi user khong ton tai. |
| 11 | `getMyTeam` | `getMyTeam_shouldReturnCurrentTeamWithMembers` | Valid | Tra ve team active cua user cung danh sach members. |
| 12 | `getMyTeam` | `getMyTeam_shouldThrowResourceNotFound_whenUserHasNoActiveTeam` | Invalid | Nem `ResourceNotFoundException` khi user khong co team active. |
| 13 | `updateTeam` | `updateTeam_shouldUpdateNameAndDescription_whenUserIsLeader` | Valid | Leader cap nhat name/description thanh cong. |
| 14 | `updateTeam` | `updateTeam_shouldThrowBadRequest_whenUserIsNotLeader` | Invalid | Chan member khong phai leader update team. |
| 15 | `updateTeam` | `updateTeam_shouldThrowResourceNotFound_whenTeamDoesNotExist` | Invalid | Nem `ResourceNotFoundException` khi team khong ton tai. |
| 16 | `updateTeam` | `updateTeam_shouldThrowBadRequest_whenUserIsNotTeamMember` | Invalid | Chan user khong thuoc team update team. |
| 17 | `updateTeam` | `updateTeam_shouldThrowBadRequest_whenTeamIsRejected` | Invalid | Chan update team `REJECTED`. |
| 18 | `updateTeam` | `updateTeam_shouldThrowBadRequest_whenTeamIsDisqualified` | Invalid | Chan update team `DISQUALIFIED`. |
| 19 | `updateTeam` | `updateTeam_shouldThrowBadRequest_whenNormalizedNewNameAlreadyExists` | Invalid | Chan doi sang ten da ton tai sau normalize. |
| 20 | `updateTeam` | `updateTeam_shouldNotCheckDuplicate_whenNormalizedNameDoesNotChange` | Valid | Cho phep doi ten chi khac hoa/thuong/khoang trang normalize giong ten cu. |
| 21 | `updateTeam` | `updateTeam_shouldIgnoreBlankName` | Valid | Bo qua blank name, van cho update description. |
| 22 | `updateTeam` | `updateTeam_shouldClearDescription_whenDescriptionIsBlank` | Valid | Blank description duoc set thanh `null`. |
| 23 | `removeMember` | `removeMember_shouldDeleteTargetMember_whenUserIsLeader` | Valid | Leader remove member thanh cong. |
| 24 | `removeMember` | `removeMember_shouldThrowBadRequest_whenLeaderRemovesSelf` | Invalid | Chan leader tu remove chinh minh. |
| 25 | `removeMember` | `removeMember_shouldThrowResourceNotFound_whenTeamDoesNotExist` | Invalid | Nem `ResourceNotFoundException` khi team khong ton tai. |
| 26 | `removeMember` | `removeMember_shouldThrowBadRequest_whenLeaderIsNotTeamMember` | Invalid | Chan caller khong thuoc team. |
| 27 | `removeMember` | `removeMember_shouldThrowBadRequest_whenCallerIsNotLeader` | Invalid | Chan caller khong phai leader. |
| 28 | `removeMember` | `removeMember_shouldThrowBadRequest_whenTeamIsRejected` | Invalid | Chan remove member khi team `REJECTED`. |
| 29 | `removeMember` | `removeMember_shouldThrowBadRequest_whenTargetIsNotTeamMember` | Invalid | Chan remove user khong thuoc team. |
| 30 | `removeMember` | `removeMember_shouldThrowBadRequest_whenTargetIsLeader` | Invalid | Chan remove target co role `LEADER`. |
| 31 | `transferLeadership` | `transferLeadership_shouldSwapRoles_whenNewLeaderIsMember` | Valid | Leader cu thanh `MEMBER`, member moi thanh `LEADER`. |
| 32 | `transferLeadership` | `transferLeadership_shouldThrowBadRequest_whenLeaderTransfersToSelf` | Invalid | Chan transfer leadership cho chinh minh. |
| 33 | `transferLeadership` | `transferLeadership_shouldThrowResourceNotFound_whenTeamDoesNotExist` | Invalid | Nem `ResourceNotFoundException` khi team khong ton tai. |
| 34 | `transferLeadership` | `transferLeadership_shouldThrowBadRequest_whenLeaderIsNotTeamMember` | Invalid | Chan caller khong thuoc team. |
| 35 | `transferLeadership` | `transferLeadership_shouldThrowBadRequest_whenCallerIsNotLeader` | Invalid | Chan caller khong phai leader. |
| 36 | `transferLeadership` | `transferLeadership_shouldThrowBadRequest_whenTeamIsDisqualified` | Invalid | Chan transfer khi team `DISQUALIFIED`. |
| 37 | `transferLeadership` | `transferLeadership_shouldThrowBadRequest_whenNewLeaderIsNotTeamMember` | Invalid | Chan transfer cho user khong thuoc team. |
| 38 | `leaveTeam` | `leaveTeam_shouldDeleteMember_whenUserIsNotLeader` | Valid | Member thuong roi team, team khong bi xoa. |
| 39 | `leaveTeam` | `leaveTeam_shouldDeleteTeam_whenOnlyLeaderLeaves` | Valid | Leader duy nhat roi team thi xoa member va xoa team. |
| 40 | `leaveTeam` | `leaveTeam_shouldThrowBadRequest_whenLeaderLeavesTeamWithOtherMembers` | Invalid | Chan leader roi team khi van con member khac. |
| 41 | `leaveTeam` | `leaveTeam_shouldThrowResourceNotFound_whenTeamDoesNotExist` | Invalid | Nem `ResourceNotFoundException` khi team khong ton tai. |
| 42 | `leaveTeam` | `leaveTeam_shouldThrowBadRequest_whenTeamIsRejected` | Invalid | Chan leave khi team `REJECTED`. |
| 43 | `leaveTeam` | `leaveTeam_shouldThrowBadRequest_whenUserIsNotTeamMember` | Invalid | Chan user khong thuoc team roi team. |
| 44 | `searchInvitableUsers` | `searchInvitableUsers_shouldReturnEmptyList_whenQueryIsTooShort` | Invalid | Query duoi 2 ky tu tra list rong, khong goi repository. |
| 45 | `searchInvitableUsers` | `searchInvitableUsers_shouldReturnEmptyList_whenQueryIsNull` | Invalid | Query `null` tra list rong, khong goi repository. |
| 46 | `searchInvitableUsers` | `searchInvitableUsers_shouldReturnAtMostTenUsers` | Valid | Query hop le tra toi da 10 users. |
| 47 | `searchInvitableUsers` | `searchInvitableUsers_shouldReturnEmptyList_whenRepositoryFindsNoUsers` | Valid | Query hop le nhung khong co user thi tra list rong. |
| 48 | `getTeamsByEvent` | `getTeamsByEvent_shouldReturnAllTeamsForEvent` | Valid | Tra danh sach team trong event cung member info. |
| 49 | `getTeamsByEvent` | `getTeamsByEvent_shouldThrowResourceNotFound_whenEventDoesNotExist` | Invalid | Nem `ResourceNotFoundException` khi event khong ton tai. |
| 50 | `getTeamsByEvent` | `getTeamsByEvent_shouldReturnEmptyList_whenEventHasNoTeams` | Valid | Event ton tai nhung khong co team thi tra list rong. |
| 51 | `getTeamById` | `getTeamById_shouldReturnTeamDetail` | Valid | Tra team detail theo `teamId`. |
| 52 | `getTeamById` | `getTeamById_shouldThrowResourceNotFound_whenTeamDoesNotExist` | Invalid | Nem `ResourceNotFoundException` khi team khong ton tai. |
| 53 | `approveTeam` | `approveTeam_shouldApproveTeamAndNotifyMembers` | Valid | Approve team `PENDING`, doi status `APPROVED`, notify members. |
| 54 | `approveTeam` | `approveTeam_shouldThrowBadRequest_whenTeamAlreadyApproved` | Invalid | Chan approve team da `APPROVED`. |
| 55 | `approveTeam` | `approveTeam_shouldThrowResourceNotFound_whenTeamDoesNotExist` | Invalid | Nem `ResourceNotFoundException` khi team khong ton tai. |
| 56 | `approveTeam` | `approveTeam_shouldThrowBadRequest_whenTeamIsRejected` | Invalid | Chan approve team `REJECTED`. |
| 57 | `approveTeam` | `approveTeam_shouldThrowBadRequest_whenTeamIsDisqualified` | Invalid | Chan approve team `DISQUALIFIED`. |
| 58 | `rejectTeam` | `rejectTeam_shouldRejectTeamStoreReasonAndNotifyMembers` | Valid | Reject team `PENDING`, trim reason, notify members. |
| 59 | `rejectTeam` | `rejectTeam_shouldRejectTeamWithNullReason_whenRequestIsNull` | Valid | Reject team `PENDING` voi request `null`, reason giu `null`. |
| 60 | `rejectTeam` | `rejectTeam_shouldThrowResourceNotFound_whenTeamDoesNotExist` | Invalid | Nem `ResourceNotFoundException` khi team khong ton tai. |
| 61 | `rejectTeam` | `rejectTeam_shouldThrowBadRequest_whenTeamIsApproved` | Invalid | Chan reject team `APPROVED`. |
| 62 | `rejectTeam` | `rejectTeam_shouldThrowBadRequest_whenTeamIsRejected` | Invalid | Chan reject lai team `REJECTED`. |
| 63 | `rejectTeam` | `rejectTeam_shouldThrowBadRequest_whenTeamIsDisqualified` | Invalid | Chan reject team `DISQUALIFIED`. |
| 64 | `rejectTeam` | `rejectTeam_shouldSetReasonNull_whenReasonIsBlank` | Valid | Blank reason duoc normalize thanh `null`. |
| 65 | `disqualifyTeam` | `disqualifyTeam_shouldDisqualifyTeamStoreReasonAndTimestamp` | Valid | Disqualify team `APPROVED`, trim reason, set timestamp, notify members. |
| 66 | `disqualifyTeam` | `disqualifyTeam_shouldDisqualifyTeamWithNullReason_whenRequestIsNull` | Valid | Disqualify team `APPROVED` voi request `null`, reason giu `null`. |
| 67 | `disqualifyTeam` | `disqualifyTeam_shouldThrowResourceNotFound_whenTeamDoesNotExist` | Invalid | Nem `ResourceNotFoundException` khi team khong ton tai. |
| 68 | `disqualifyTeam` | `disqualifyTeam_shouldThrowBadRequest_whenTeamIsPending` | Invalid | Chan disqualify team `PENDING`. |
| 69 | `disqualifyTeam` | `disqualifyTeam_shouldThrowBadRequest_whenTeamIsRejected` | Invalid | Chan disqualify team `REJECTED`. |
| 70 | `disqualifyTeam` | `disqualifyTeam_shouldThrowBadRequest_whenTeamIsAlreadyDisqualified` | Invalid | Chan disqualify lai team `DISQUALIFIED`. |
| 71 | `disqualifyTeam` | `disqualifyTeam_shouldSetReasonNull_whenReasonIsBlank` | Valid | Blank reason duoc normalize thanh `null`. |
| 72 | `getActiveEventsWithTracks` | `getActiveEventsWithTracks_shouldReturnOnlyOpenEventsInsideRegistrationWindow` | Valid | Tra event `OPEN` dang trong registration window, kem tracks. |
| 73 | `getActiveEventsWithTracks` | `getActiveEventsWithTracks_shouldReturnOpenEvent_whenRegistrationWindowIsNull` | Valid | Event `OPEN` khong co registration window van duoc tra ve. |
| 74 | `getActiveEventsWithTracks` | `getActiveEventsWithTracks_shouldReturnEmptyList_whenThereAreNoOpenEvents` | Valid | Khong co event `OPEN` thi tra list rong. |
| 75 | `getActiveEventsWithTracks` | `getActiveEventsWithTracks_shouldExcludeEventsAfterRegistrationDeadline` | Invalid | Event het han registration bi loai khoi ket qua. |

## Notes For Review

- `getTeamById` role validation khong nam trong `TeamServiceTest` vi role check nam o `TeamController` qua `@PreAuthorize`.
- `UpdateTeamRequest.name` co `@Size(max = 255)`, day la Bean Validation o controller layer nen chua duoc test trong unit test service.
- Cac test `Invalid` van duoc xem la pass neu service nem dung exception hoac chan dung hanh dong.
