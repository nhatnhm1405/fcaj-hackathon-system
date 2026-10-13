# TeamInviteService Validation & Test Report

File service: `src/main/java/com/seal/hackathon/service/TeamInviteService.java`

File test: `src/test/java/com/seal/hackathon/service/TeamInviteServiceTest.java`

Files lien quan da sua:

- `src/main/java/com/seal/hackathon/dto/request/CreateInviteRequest.java`
- `src/main/java/com/seal/hackathon/entity/TeamInvite.java`
- `src/main/java/com/seal/hackathon/entity/TeamMember.java`
- `src/main/java/com/seal/hackathon/repository/TeamInviteRepository.java`
- `src/main/java/com/seal/hackathon/repository/TeamRepository.java`

Quy uoc status:

- `Valid`: test case ky vong service xu ly thanh cong, khong nem exception.
- `Invalid`: test case ky vong service chan request/action khong hop le bang exception.

Tong so public function trong `TeamInviteService`: **4**

Tong so test case trong `TeamInviteServiceTest`: **24**

## Business Rules Implemented

Nhung rule duoi day da duoc implement de service tu phong thu, khong phu thuoc hoan toan vao controller:

- Invite/accept chi xu ly khi caller/current user ton tai, `isApproved = true`, `isActive = true`.
- User duoc invite phai ton tai, active, approved va co `userType` la `FPT_STUDENT` hoac `EXTERNAL_STUDENT`.
- Chi team leader moi duoc tao invite.
- Team chi duoc nhan invite khi:
  - event status la `OPEN`;
  - registration window dang hop le;
  - team status la `APPROVED`;
  - team chua du 5 members.
- Message duoc normalize:
  - `null` hoac blank -> `null`;
  - non-blank -> `trim()`;
  - max length = 1000.
- Invite chi accept/decline duoc khi status la `PENDING`.
- Khong cho invite user da la member cua mot team trong cung event.
- Khong cho tao invite moi neu da co invite `PENDING` cho cung `(team, invitedUser)`.
- Reuse invite cu chi hop le neu status cu la `ACCEPTED` hoac `DECLINED`; status la gia tri la se bi chan.

## Implementation Summary

| Area | Change |
|---|---|
| Input validation | Them `requireId`, `requireInvitedUserId`, chan `null` truoc khi goi repository. |
| User validation | Them `requireApprovedActiveUser` cho caller/current user va `validateInvitableUser` cho invited user. |
| Team validation | Them `validateTeamCanReceiveInvite`: event `OPEN`, registration window, team `APPROVED`, max 5 members. |
| Message validation | Them trim/blank-to-null/max 1000 trong service va `@Size(max = 1000)` trong DTO. |
| Ownership | Leader check van nam trong service, sau khi xac nhan inviter active/approved. |
| Status | Dung constants `PENDING`, `ACCEPTED`, `DECLINED`; accept/decline chi cho `PENDING`. |
| Duplicate | Existing pending invite bi chan; accepted/declined invite duoc reset ve pending. |
| Race mitigation | Them pessimistic lock cho invite va team; dung `saveAndFlush` de bat DB conflict som hon. |
| DB constraint | Them entity-level unique constraint cho `TeamMember(team_id, user_id)`. |
| Timestamp reuse | Bo `updatable = false` cua `TeamInvite.createdAt` de reset timestamp khi reuse invite cu co the persist. |

## Public Function Coverage Summary

| Function | So test | Coverage chinh |
|---|---:|---|
| `createInvite` | 13 | Success, trim/blank message, null input, message length, team not found, inviter inactive, leader permission, team/event/status/full, invited user type/status, existing membership, pending duplicate |
| `getPendingInvites` | 3 | Success, null user id, user not found |
| `acceptInvite` | 5 | Success, null invite id, ownership, non-pending status, team full at accept time |
| `declineInvite` | 3 | Success, null invite id, invite not found |

## Validation Matrix By Function

### `createInvite(Integer inviterId, Integer teamId, CreateInviteRequest request)`

| Validation group | Current behavior |
|---|---|
| Null input | Chan `inviterId`, `teamId`, `request`, `request.invitedUserId` bang `BadRequestException`. |
| Resource exists | Check team exists; check inviter exists; check invited user exists. |
| Caller account | Inviter phai approved va active. |
| Ownership | Inviter phai la member role `LEADER` cua team. |
| Team/event rule | Event phai `OPEN`, registration window hop le, team phai `APPROVED`, team chua full. |
| Invited user rule | Invited user phai approved, active, userType trong `FPT_STUDENT`/`EXTERNAL_STUDENT`. |
| Membership duplicate | Chan neu invited user da co team trong cung event. |
| Invite duplicate | Chan existing invite status `PENDING`; reuse existing `ACCEPTED`/`DECLINED`. |
| Message | Blank -> `null`, trim, max 1000. |
| Race/DB | Lock existing invite row neu co; `saveAndFlush`; catch `DataIntegrityViolationException`. |

### `getPendingInvites(Integer userId)`

| Validation group | Current behavior |
|---|---|
| Null input | Chan `userId = null` bang `BadRequestException`. |
| Resource exists | Check user exists. |
| Account status | User phai approved va active. |
| Status filter | Chi query invite status `PENDING`. |
| Side effect | Read-only, khong save, khong notify. |

### `acceptInvite(Integer userId, Integer inviteId)`

| Validation group | Current behavior |
|---|---|
| Null input | Chan `userId`, `inviteId` bang `BadRequestException`. |
| Resource exists | Check user exists; check invite exists; lock team by id. |
| Ownership | Invite phai thuoc ve `userId`. |
| Invite status | Chi accept invite `PENDING`. |
| Account status | Current user va invited user phai approved/active; invited user phai la student participant. |
| Team/event rule | Re-check event `OPEN`, registration window, team `APPROVED`, team chua full tai thoi diem accept. |
| Membership duplicate | Chan user da la member cua team khac trong cung event. |
| Side effects | Set invite `ACCEPTED`, tao `TeamMember`, notify leader, decline cac pending invite khac cung event. |
| Race/DB | Lock invite + team; catch DB conflict khi save invite/member. |

### `declineInvite(Integer userId, Integer inviteId)`

| Validation group | Current behavior |
|---|---|
| Null input | Chan `userId`, `inviteId` bang `BadRequestException`. |
| Resource exists | Check user exists; check invite exists. |
| Ownership | Invite phai thuoc ve `userId`. |
| Invite status | Chi decline invite `PENDING`. |
| Account status | User phai approved va active. |
| Side effects | Set invite `DECLINED`, set `respondedAt`, khong tao member. |
| Race/DB | Lock invite; catch DB conflict khi save invite. |

## Detailed Test Cases

| # | Function | Test case | Status | Expected result |
|---:|---|---|---|---|
| 1 | `createInvite` | `createInvite_shouldCreatePendingInviteWithTrimmedMessage_whenRequestIsValid` | Valid | Tao invite `PENDING`, trim message, save invite, notify invited user. |
| 2 | `createInvite` | `createInvite_shouldNormalizeBlankMessageToNull` | Valid | Blank message duoc normalize thanh `null`. |
| 3 | `createInvite` | `createInvite_shouldThrowBadRequest_whenInputIsNull` | Invalid | Chan `inviterId`, `teamId`, request body, `invitedUserId` null; khong goi repository save/notify. |
| 4 | `createInvite` | `createInvite_shouldThrowBadRequest_whenMessageIsTooLong` | Invalid | Chan message dai hon 1000 ky tu truoc khi goi repository. |
| 5 | `createInvite` | `createInvite_shouldThrowResourceNotFound_whenTeamDoesNotExist` | Invalid | Nem `ResourceNotFoundException` khi team khong ton tai; khong lookup user/invite. |
| 6 | `createInvite` | `createInvite_shouldThrowBadRequest_whenInviterIsNotApprovedOrActive` | Invalid | Chan inviter inactive/unapproved; khong check leader, khong save. |
| 7 | `createInvite` | `createInvite_shouldThrowForbidden_whenInviterIsNotLeader` | Invalid | Chan caller khong phai leader cua team. |
| 8 | `createInvite` | `createInvite_shouldThrowBadRequest_whenTeamIsNotApproved` | Invalid | Chan invite vao team khong phai `APPROVED`. |
| 9 | `createInvite` | `createInvite_shouldThrowBadRequest_whenEventIsNotOpen` | Invalid | Chan invite khi event khong phai `OPEN`. |
| 10 | `createInvite` | `createInvite_shouldThrowBadRequest_whenTeamIsFull` | Invalid | Chan invite khi team da co 5 members. |
| 11 | `createInvite` | `createInvite_shouldThrowBadRequest_whenInvitedUserIsNotActiveStudentParticipant` | Invalid | Chan invited user khong phai active approved student participant. |
| 12 | `createInvite` | `createInvite_shouldThrowBadRequest_whenInvitedUserAlreadyBelongsToEventTeam` | Invalid | Chan invited user da co team trong cung event. |
| 13 | `createInvite` | `createInvite_shouldThrowBadRequest_whenPendingInviteAlreadyExists` | Invalid | Chan duplicate pending invite cho cung team/user. |
| 14 | `getPendingInvites` | `getPendingInvites_shouldReturnMappedPendingInvites` | Valid | Tra danh sach pending invite cua user da approved/active. |
| 15 | `getPendingInvites` | `getPendingInvites_shouldThrowBadRequest_whenUserIdIsNull` | Invalid | Chan `userId = null`, khong goi repository. |
| 16 | `getPendingInvites` | `getPendingInvites_shouldThrowResourceNotFound_whenUserDoesNotExist` | Invalid | Nem `ResourceNotFoundException`, khong query invite list. |
| 17 | `acceptInvite` | `acceptInvite_shouldAcceptInviteAndDeclineOtherPendingInvites_whenRequestIsValid` | Valid | Set invite `ACCEPTED`, tao member, notify leader, decline pending invite khac cung event. |
| 18 | `acceptInvite` | `acceptInvite_shouldThrowBadRequest_whenInviteIdIsNull` | Invalid | Chan `inviteId = null`, khong goi repository. |
| 19 | `acceptInvite` | `acceptInvite_shouldThrowForbidden_whenInviteBelongsToAnotherUser` | Invalid | Chan user accept invite khong thuoc ve minh. |
| 20 | `acceptInvite` | `acceptInvite_shouldThrowBadRequest_whenInviteIsNotPending` | Invalid | Chan accept invite da `ACCEPTED`/khong con pending. |
| 21 | `acceptInvite` | `acceptInvite_shouldThrowBadRequest_whenTeamIsFullAtAcceptTime` | Invalid | Chan accept neu team full tai thoi diem accept; khong save invite/member. |
| 22 | `declineInvite` | `declineInvite_shouldDeclinePendingInvite_whenRequestIsValid` | Valid | Set invite `DECLINED`, khong tao member. |
| 23 | `declineInvite` | `declineInvite_shouldThrowBadRequest_whenInviteIdIsNull` | Invalid | Chan `inviteId = null`, khong goi repository. |
| 24 | `declineInvite` | `declineInvite_shouldThrowResourceNotFound_whenInviteDoesNotExist` | Invalid | Nem `ResourceNotFoundException`, khong save invite. |

## Test Commands Run

Related test:

```powershell
& 'C:\Users\daonh\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd' -Dtest=TeamInviteServiceTest test
```

Result:

- Tests run: 24
- Failures: 0
- Errors: 0
- Skipped: 0

Full test suite:

```powershell
& 'C:\Users\daonh\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd' test
```

Result:

- Tests run: 165
- Failures: 0
- Errors: 0
- Skipped: 0

## Notes For Review

- Rule "team phai `APPROVED` moi duoc invite/accept" la rule chat. Neu business muon leader moi tao team `PENDING` van co the moi thanh vien truoc khi coordinator approve, can noi rule nay.
- Rule "event phai `OPEN` va con registration window" cung la rule chat. Neu invite duoc phep sau registration deadline, can noi rule nay.
- `TeamMember` unique constraint moi chi la entity annotation. Vi project dang `spring.jpa.hibernate.ddl-auto=none`, DB hien tai se khong tu update constraint. Can migration/schema SQL rieng neu muon DB enforce that su.
- Pessimistic lock va `saveAndFlush` giam race condition, nhung test hien tai la unit test voi Mockito, chua simulate concurrent transaction thuc te.
- `TeamInvite.createdAt` da bo `updatable = false` de reuse invite cu co the cap nhat timestamp. Neu business muon giu created time cua row dau tien, can tach them field khac nhu `resentAt`.
- Controller-level validation `@Size(max = 1000)` cua `CreateInviteRequest.message` chua co controller test rieng; service da validate lai nen bypass controller van bi chan.
- Workspace hien co nhieu file dirty/untracked ngoai pham vi invite tu truoc. Bao cao nay chi mo ta phan TeamInvite va cac file lien quan truc tiep.
