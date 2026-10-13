# Backend Review - Join Request + Notifications

## Scope

Chi code backend. Khong sua file frontend.

Tinh nang da them:
- Participant gui join request vao team.
- Participant xem team co the xin join, xem request da gui, rut request pending.
- Team leader xem pending request cua team, accept/decline request.
- Reuse JoinRequest cu khi request da DECLINED va participant gui lai.
- Khi accept thanh cong, cac pending request khac cua cung requester trong cung event duoc doi thanh DECLINED.
- Notification in-app duoc noi vao cac service da co.

## Files created

1. `back-end/src/seal-api/src/main/java/com/seal/hackathon/entity/JoinRequest.java`
   - Entity map bang `JoinRequest`.
   - Luu team, requester, message, status, createdAt, respondedAt.
   - Co unique constraint `(team_id, requester_user_id)` dung theo DB.
   - `createdAt` duoc phep update rieng cho case reuse row DECLINED -> PENDING.

2. `back-end/src/seal-api/src/main/java/com/seal/hackathon/repository/JoinRequestRepository.java`
   - Repository query JoinRequest theo team/requester/status/event.
   - Dung cho resend, list my requests, list pending cua team, va auto-decline request khac sau accept.

3. `back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/request/CreateJoinRequestRequest.java`
   - Request body cho POST join request.
   - Chi co `message`, gioi han 1000 ky tu.

4. `back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/response/JoinRequestResponse.java`
   - Response day du cho request da gui/toi team.
   - Gom request, team, event, track, requester, status, timestamps.

5. `back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/response/JoinableTeamResponse.java`
   - Response cho list team co the xin join.
   - Gom team/event/track/status, memberCount, maxMembers, leader info, va request hien tai cua user neu co.

6. `back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/response/JoinableTeamListResponse.java`
   - Wrapper response cho endpoint joinable teams.
   - Gom `totalJoinableTeams` va danh sach `teams`.

7. `back-end/src/seal-api/src/main/java/com/seal/hackathon/service/JoinRequestService.java`
   - Chua toan bo business logic cua Join Request.

8. `back-end/src/seal-api/src/main/java/com/seal/hackathon/controller/JoinRequestController.java`
   - Expose endpoint `/api/join-requests/**`.

9. `back-end/database scripts/migration_join_request.sql`
   - Migration tao bang `JoinRequest`.

## Files changed

1. `back-end/database scripts/seal_schema.sql`
   - Tang comment table count tu 18 len 19.
   - Them `CREATE TABLE JoinRequest` sau `TeamInvite`.

2. `back-end/src/seal-api/src/main/java/com/seal/hackathon/config/SecurityConfig.java`
   - Them matcher `/api/join-requests/**` -> `hasRole("PARTICIPANT")`.
   - Leader authorization van nam trong service bang `requireLeader`.

3. `back-end/src/seal-api/src/main/java/com/seal/hackathon/repository/TeamRepository.java`
   - Them `findAllByEvent_Status(String status)`.
   - Dung de list team trong cac event `OPEN`.

4. `back-end/src/seal-api/src/main/java/com/seal/hackathon/repository/TeamMemberRepository.java`
   - Them `countByTeam_TeamId(Integer teamId)`.
   - Dung de check max 5 thanh vien va tinh memberCount.

5. `back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/response/TeamInviteResponse.java`
   - Them `trackName`, `teamStatus`.

6. `back-end/src/seal-api/src/main/java/com/seal/hackathon/service/TeamInviteService.java`
   - Map them `trackName`, `teamStatus`.
   - Gui notification khi leader moi user.
   - Gui notification cho leader khi invite duoc accept.

7. `back-end/src/seal-api/src/main/java/com/seal/hackathon/service/TeamService.java`
   - Gui notification cho toan bo team khi coordinator approve/reject/disqualify team.

8. `back-end/src/seal-api/src/main/java/com/seal/hackathon/service/AccountService.java`
   - Gui notification cho user khi account duoc approve.
   - Reject khong gui notification vi user bi inactive.

9. `back-end/src/seal-api/src/main/java/com/seal/hackathon/service/RoundResultService.java`
   - Gui notification cho thanh vien cac team khi result duoc publish lan dau.
   - Khong gui lai notification neu result da published truoc do.

## Endpoint flow

### POST `/api/join-requests/teams/{teamId}`

Controller: `JoinRequestController.createJoinRequest`

Service: `JoinRequestService.createJoinRequest`

Flow:
1. Lay `requesterUserId` tu JWT principal.
2. Load requester va team.
3. Goi `validateRequesterCanAskToJoin`.
4. Tim request cu bang `findByTeam_TeamIdAndRequester_UserId`.
5. Neu request cu `PENDING`, tra loi loi vi da gui request roi.
6. Neu request cu `ACCEPTED`, tra loi loi vi request da duoc accept.
7. Neu request cu `DECLINED`, reuse row:
   - set status ve `PENDING`
   - update message
   - update createdAt ve thoi diem moi
   - clear respondedAt
8. Neu chua co request cu, tao row moi.
9. Goi `notifyLeaderAboutJoinRequest` de bao leader.
10. Tra `JoinRequestResponse`.

Validation trong `validateRequesterCanAskToJoin`:
- User phai active va approved.
- Event phai `OPEN`.
- Team phai `APPROVED`.
- Team chua full, count member < 5.
- User chua o team nao trong cung event.

Ly do:
- Chap nhan resend sau DECLINED ma khong doi schema.
- Giu dung unique key DB.
- Chan request vao team pending/full/event khong open.

### GET `/api/join-requests/joinable-teams?eventId=&query=`

Controller: `JoinRequestController.getJoinableTeams`

Service: `JoinRequestService.getJoinableTeams`

Flow:
1. Xac nhan requester ton tai.
2. Neu co `eventId`, lay team `APPROVED` cua event do va filter event `OPEN`.
3. Neu khong co `eventId`, lay team trong cac event `OPEN` bang `findAllByEvent_Status("OPEN")`, roi filter team `APPROVED`.
4. Filter team chua full: `countByTeam_TeamId < 5`.
5. Filter bo event ma requester da co team.
6. Search theo `teamName` va `trackName` bang `matchesQuery`.
7. Map sang `JoinableTeamResponse`.
8. Boc danh sach vao `JoinableTeamListResponse` de tra `totalJoinableTeams` va `teams`.

Response co them `myRequestId/myRequestStatus`:
- Frontend co the biet user da pending/declined/accepted voi team do hay chua.
- Team van la joinable theo capacity/status, nhung POST se tu bao loi neu dang PENDING.

Response shape:

```json
{
  "success": true,
  "message": "Joinable teams retrieved.",
  "data": {
    "totalJoinableTeams": 1,
    "teams": []
  }
}
```

### GET `/api/join-requests/my`

Controller: `JoinRequestController.getMyRequests`

Service: `JoinRequestService.getMyRequests`

Flow:
1. Lay requester tu JWT.
2. Query tat ca request cua requester order by createdAt desc.
3. Map sang `JoinRequestResponse`.

Ly do:
- Participant xem lich su request da gui, gom PENDING/ACCEPTED/DECLINED.

### DELETE `/api/join-requests/{requestId}`

Controller: `JoinRequestController.withdrawRequest`

Service: `JoinRequestService.withdrawRequest`

Flow:
1. Load request theo id.
2. Check request thuoc requester hien tai.
3. Chi cho xoa neu status la `PENDING`.
4. Hard delete row khoi DB.

Ly do:
- Withdraw la user tu rut, schema khong co status `WITHDRAWN`.
- Hard delete giup user co the gui lai request moi.

### GET `/api/join-requests/teams/{teamId}`

Controller: `JoinRequestController.getPendingRequestsForTeam`

Service: `JoinRequestService.getPendingRequestsForTeam`

Flow:
1. Lay leader user id tu JWT.
2. Goi `requireLeader` de check caller la leader cua team.
3. Query chi request status `PENDING`.
4. Map sang response.

Ly do:
- Leader chi can request dang cho duyet.
- DECLINED/ACCEPTED khong hien trong inbox leader.

### PUT `/api/join-requests/{requestId}/accept`

Controller: `JoinRequestController.acceptRequest`

Service: `JoinRequestService.acceptRequest`

Flow:
1. Goi `requirePendingRequest` de load request va dam bao status `PENDING`.
2. Goi `requireLeader` de dam bao caller la leader cua team.
3. Goi `validateTeamCanAcceptRequest`:
   - event `OPEN`
   - team `APPROVED`
   - member count < 5
4. Check requester chua o team nao trong cung event.
5. Tao `TeamMember` role `MEMBER`.
6. Set request hien tai thanh `ACCEPTED`, set respondedAt.
7. Goi `declineOtherPendingRequestsForRequester`:
   - tim pending request khac cua requester trong cung event
   - set thanh `DECLINED`
   - set respondedAt cung thoi diem
8. Goi `notifyRequesterAboutDecision(..., true)`.
9. Tra response.

Ly do:
- Chan user vao 2 team cung event.
- Don sach pending request khong con kha thi cua requester.
- Khong can auto-decline theo team full vi full duoc check truc tiep khi list/accept.

### PUT `/api/join-requests/{requestId}/decline`

Controller: `JoinRequestController.declineRequest`

Service: `JoinRequestService.declineRequest`

Flow:
1. Load pending request.
2. Check caller la leader cua team.
3. Set status `DECLINED`, set respondedAt.
4. Goi notification cho requester.
5. Tra response.

Ly do:
- Request DECLINED van duoc giu lai de user xem lich su.
- User co the gui lai, service se reuse row do.

## Helper functions in JoinRequestService

`validateRequesterCanAskToJoin`
- Dung cho POST request.
- Check account active/approved, team/event/status/capacity, va membership cung event.

`validateTeamCanAcceptRequest`
- Dung cho POST va ACCEPT.
- Gom cac rule joinable cot loi: event OPEN, team APPROVED, member count < 5.

`requireLeader`
- Dung cho leader-only operations.
- Khong dua vao URL role vi participant nao cung co ROLE_PARTICIPANT; phai check membership role `LEADER`.

`requirePendingRequest`
- Dung cho accept/decline.
- Dam bao khong accept/decline request da xu ly.

`declineOtherPendingRequestsForRequester`
- Dung sau khi accept thanh cong.
- Don cac pending request khac cua cung requester trong cung event.

`findLeader`
- Dung de notify leader va map leader info.

`matchesQuery`
- Search chi theo team name va track name theo yeu cau.

`normalizeMessage`
- Trim message, convert blank -> null.

`mapToJoinableTeamResponse`
- Gom thong tin team, leader, member count, max 5, va request status cua current user.

`mapToResponse`
- Gom full context cho JoinRequestResponse.

## Notification flow

### TeamService

`approveTeam`
- Sau khi set status `APPROVED`, notify tat ca member.

`rejectTeam`
- Sau khi set status `REJECTED`, notify tat ca member, kem reason neu co.

`disqualifyTeam`
- Sau khi set status `DISQUALIFIED`, notify tat ca member, kem reason neu co.

`notifyTeamMembers`
- Lay member bang `teamMemberRepository.findByTeam_TeamId`.
- Goi `notificationService.createNotification` cho tung user.

### TeamInviteService

`createInvite`
- Sau khi save invite, notify invited user.

`acceptInvite`
- Sau khi add member va decline invite khac, notify current leader.

`findCurrentLeader`
- Lay leader hien tai cua team, tranh gui sai neu leadership da transfer sau khi invite duoc tao.

### JoinRequestService

`notifyLeaderAboutJoinRequest`
- Goi khi participant gui/resend request.

`notifyRequesterAboutDecision`
- Goi khi leader accept/decline request.

### AccountService

`approveUser`
- Sau khi approve, notify user.
- Reject khong notify vi service set inactive.

### RoundResultService

`publishResults`
- Lay `newlyPublished` truoc khi set published.
- Chi notify result lan dau publish.

`notifyTeamResultPublished`
- Notify toan bo member cua team ve rank.
- Them message advanced neu team dat dieu kien advance.

## Validation notes

- Max member dang hard-code `MAX_TEAM_MEMBERS = 5`, dong bo voi TeamInviteService hien tai.
- Joinable list khong hien team full, team pending, event khong OPEN.
- Accept van check lai capacity va membership de tranh race/stale UI.
- Resend request sau DECLINED khong insert row moi do unique DB; service update lai row cu.
- Withdraw hard delete vi schema khong co status WITHDRAWN.

## Verification

Command da chay:

```powershell
& "C:\Users\daonh\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd" compile
```

Ket qua:
- `BUILD SUCCESS`
- Compiler warning cu: `JwtAuthenticationFilter` uses deprecated API.

## Notes

- Maven wrapper `mvnw.cmd` hien tai loi tren PowerShell voi message `Cannot start maven from wrapper`.
- May khong co `mvn` global.
- Compile duoc chay bang Maven 3.9.16 da co san trong `~/.m2/wrapper/dists`.
- Working tree truoc do da co thay doi frontend `front-end/src/seal-web/package-lock.json`; file do khong thuoc scope backend nay va khong duoc sua trong task nay.
