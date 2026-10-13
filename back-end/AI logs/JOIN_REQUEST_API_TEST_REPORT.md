# Join Request API Test Report

Test time: 2026-06-15 23:58, Asia/Saigon

Scope: backend only, Join Request endpoints under `/api/join-requests`.

## Environment

- Backend was started on `http://localhost:18080` for testing, then stopped after tests.
- Database seed used: `back-end/database scripts/seed_join_request_test_data.sql`.
- Seed was reset before the final API run and reset again after the run.
- Final DB reset check: `join_request_count_after_final_reset = 0`.
- Auth used signed JWTs for these existing seed users:
  - requester: `join.tester@fpt.edu.vn`
  - AI leader: `join.leader.ai@fpt.edu.vn`
  - Web leader: `join.leader.web@fpt.edu.vn`

## Build Result

- Command: `mvn test`
- Result: `BUILD SUCCESS`
- Tests: `1` run, `0` failures, `0` errors.

## Fix Found During API Test

The first edge-case run found that framework HTTP errors were falling into the generic `500` handler:

- Unsupported content type returned `500` instead of `415`.
- Missing path/resource could return `500` instead of `404`.

Fixed file:

- `back-end/src/seal-api/src/main/java/com/seal/hackathon/exception/GlobalExceptionHandler.java`

Added handlers:

- `HttpMessageNotReadableException` -> `400`
- `HttpMediaTypeNotSupportedException` -> `415`
- `NoResourceFoundException` -> `404`

After the fix, the final API suite passed `33/33`.

## Important Notes

- `GET /api/join-requests/joinable-teams?query=web` without `eventId` can return teams from every `OPEN` event. In this DB it returned both `Team Horizon` from event `2` and `Join Web Open` from event `4`. This is expected from current backend logic. If the UI wants one event only, always pass `eventId`.
- `POST /api/join-requests/teams/{teamId}` accepts an empty JSON body when `Content-Type: application/json`. If a client sends `application/x-www-form-urlencoded`, the backend now returns `415` instead of a false `500`.
- This run tested in-app notification side effects only indirectly by exercising service paths. It did not test WebSocket or real-time behavior because this feature is in-app refresh based.

## Seed State Used For Tests

| Team ID | Team name | Event ID | Track | Event status | Team status | Members |
|---:|---|---:|---|---|---|---:|
| 11 | Join AI Open | 4 | AI Solution | OPEN | APPROVED | 1 |
| 12 | Join Web Open | 4 | Web Application | OPEN | APPROVED | 2 |
| 13 | Join AI Full | 4 | AI Solution | OPEN | APPROVED | 5 |
| 14 | Join AI Pending | 4 | AI Solution | OPEN | PENDING | 1 |

## Final API Results

### 1. List joinable teams by event and query

- API: `GET /api/join-requests/joinable-teams?eventId=4&query=ai`
- Input: requester token `join.tester@fpt.edu.vn`
- Output:

```json
{
  "success": true,
  "message": "Joinable teams retrieved.",
  "data": {
    "totalJoinableTeams": 1,
    "teams": [
      {
        "teamId": 11,
        "teamName": "Join AI Open",
        "trackName": "AI Solution",
        "teamStatus": "APPROVED",
        "memberCount": 1,
        "maxMembers": 5,
        "myRequestId": null,
        "myRequestStatus": null
      }
    ]
  }
}
```

- Result: PASS

### 2. List all joinable teams by event

- API: `GET /api/join-requests/joinable-teams?eventId=4`
- Input: requester token
- Output:

```json
{
  "success": true,
  "message": "Joinable teams retrieved.",
  "data": {
    "totalJoinableTeams": 2,
    "teams": [
      { "teamId": 11, "teamName": "Join AI Open", "memberCount": 1, "maxMembers": 5 },
      { "teamId": 12, "teamName": "Join Web Open", "memberCount": 2, "maxMembers": 5 }
    ]
  }
}
```

- Result: PASS

### 3. List joinable teams without eventId

- API: `GET /api/join-requests/joinable-teams?query=web`
- Input: requester token
- Output:

```json
{
  "success": true,
  "message": "Joinable teams retrieved.",
  "data": {
    "totalJoinableTeams": 2,
    "teams": [
      { "teamId": 6, "teamName": "Team Horizon", "eventId": 2, "trackName": "Web Application" },
      { "teamId": 12, "teamName": "Join Web Open", "eventId": 4, "trackName": "Web Application" }
    ]
  }
}
```

- Result: PASS

### 4. Create request with unsupported content type

- API: `POST /api/join-requests/teams/12`
- Input: requester token, no JSON body, default PowerShell content type `application/x-www-form-urlencoded`
- Output:

```json
{
  "success": false,
  "message": "Unsupported content type: application/x-www-form-urlencoded;charset=UTF-8. Use application/json."
}
```

- Result: PASS

### 5. Create request with empty JSON body

- API: `POST /api/join-requests/teams/12`
- Input: requester token, `Content-Type: application/json`, empty body
- Output:

```json
{
  "success": true,
  "message": "Join request sent.",
  "data": {
    "requestId": 7,
    "teamId": 12,
    "teamName": "Join Web Open",
    "message": null,
    "status": "PENDING"
  }
}
```

- Result: PASS

### 6. Non-owner withdraws request

- API: `DELETE /api/join-requests/7`
- Input: AI leader token
- Output:

```json
{
  "success": false,
  "message": "This join request does not belong to you."
}
```

- Result: PASS

### 7. Requester withdraws pending request

- API: `DELETE /api/join-requests/7`
- Input: requester token
- Output:

```json
{
  "success": true,
  "message": "Join request withdrawn."
}
```

- Result: PASS

### 8. Withdraw already deleted request

- API: `DELETE /api/join-requests/7`
- Input: requester token
- Output:

```json
{
  "success": false,
  "message": "Join request not found: 7"
}
```

- Result: PASS

### 9. Create request with oversized message

- API: `POST /api/join-requests/teams/11`
- Input:

```json
{
  "message": "<1001 characters>"
}
```

- Output:

```json
{
  "success": false,
  "message": "Validation failed: {message=Message must be at most 1000 characters}"
}
```

- Result: PASS

### 10. Create join request to AI team

- API: `POST /api/join-requests/teams/11`
- Input:

```json
{
  "message": "Please let me join AI team"
}
```

- Output:

```json
{
  "success": true,
  "message": "Join request sent.",
  "data": {
    "requestId": 8,
    "teamId": 11,
    "teamName": "Join AI Open",
    "requesterEmail": "join.tester@fpt.edu.vn",
    "message": "Please let me join AI team",
    "status": "PENDING",
    "respondedAt": null
  }
}
```

- Result: PASS

### 11. Create duplicate pending request

- API: `POST /api/join-requests/teams/11`
- Input:

```json
{
  "message": "Duplicate request"
}
```

- Output:

```json
{
  "success": false,
  "message": "You already have a pending request for this team."
}
```

- Result: PASS

### 12. Get my join requests after create

- API: `GET /api/join-requests/my`
- Input: requester token
- Output:

```json
{
  "success": true,
  "message": "My join requests retrieved.",
  "data": [
    {
      "requestId": 8,
      "teamId": 11,
      "teamName": "Join AI Open",
      "message": "Please let me join AI team",
      "status": "PENDING"
    }
  ]
}
```

- Result: PASS

### 13. Leader lists pending requests for team

- API: `GET /api/join-requests/teams/11`
- Input: AI leader token
- Output:

```json
{
  "success": true,
  "message": "Pending join requests retrieved.",
  "data": [
    {
      "requestId": 8,
      "teamId": 11,
      "requesterEmail": "join.tester@fpt.edu.vn",
      "status": "PENDING"
    }
  ]
}
```

- Result: PASS

### 14. Non-leader lists pending requests for team

- API: `GET /api/join-requests/teams/11`
- Input: requester token
- Output:

```json
{
  "success": false,
  "message": "Only the team leader can perform this action."
}
```

- Result: PASS

### 15. Wrong leader declines request

- API: `PUT /api/join-requests/8/decline`
- Input: Web leader token
- Output:

```json
{
  "success": false,
  "message": "Only the team leader can perform this action."
}
```

- Result: PASS

### 16. Correct leader declines request

- API: `PUT /api/join-requests/8/decline`
- Input: AI leader token
- Output:

```json
{
  "success": true,
  "message": "Join request declined.",
  "data": {
    "requestId": 8,
    "teamId": 11,
    "status": "DECLINED",
    "respondedAt": "2026-06-15T23:58:57"
  }
}
```

- Result: PASS

### 17. Re-send request after declined

- API: `POST /api/join-requests/teams/11`
- Input:

```json
{
  "message": "Retry after declined"
}
```

- Output:

```json
{
  "success": true,
  "message": "Join request sent.",
  "data": {
    "requestId": 8,
    "teamId": 11,
    "message": "Retry after declined",
    "status": "PENDING",
    "respondedAt": null
  }
}
```

- Result: PASS
- Note: request ID stayed `8`, confirming reuse of the old row because DB has `UNIQUE(team_id, requester_user_id)`.

### 18. Withdraw pending request after resend

- API: `DELETE /api/join-requests/8`
- Input: requester token
- Output:

```json
{
  "success": true,
  "message": "Join request withdrawn."
}
```

- Result: PASS

### 19. Create request to full team

- API: `POST /api/join-requests/teams/13`
- Input:

```json
{
  "message": "Want full team"
}
```

- Output:

```json
{
  "success": false,
  "message": "This team is already full (maximum 5 members)."
}
```

- Result: PASS

### 20. Create request to pending team

- API: `POST /api/join-requests/teams/14`
- Input:

```json
{
  "message": "Want pending team"
}
```

- Output:

```json
{
  "success": false,
  "message": "Only approved teams can receive join requests."
}
```

- Result: PASS

### 21. Create AI request for accept flow

- API: `POST /api/join-requests/teams/11`
- Input:

```json
{
  "message": "Accept me to AI"
}
```

- Output:

```json
{
  "success": true,
  "message": "Join request sent.",
  "data": {
    "requestId": 9,
    "teamId": 11,
    "status": "PENDING"
  }
}
```

- Result: PASS

### 22. Create Web request before AI accepted

- API: `POST /api/join-requests/teams/12`
- Input:

```json
{
  "message": "Also applying to Web"
}
```

- Output:

```json
{
  "success": true,
  "message": "Join request sent.",
  "data": {
    "requestId": 10,
    "teamId": 12,
    "status": "PENDING"
  }
}
```

- Result: PASS
- Note: multiple pending requests are allowed before one is accepted.

### 23. Wrong leader accepts request

- API: `PUT /api/join-requests/9/accept`
- Input: Web leader token
- Output:

```json
{
  "success": false,
  "message": "Only the team leader can perform this action."
}
```

- Result: PASS

### 24. Correct leader accepts AI request

- API: `PUT /api/join-requests/9/accept`
- Input: AI leader token
- Output:

```json
{
  "success": true,
  "message": "Join request accepted.",
  "data": {
    "requestId": 9,
    "teamId": 11,
    "status": "ACCEPTED",
    "respondedAt": "2026-06-15T23:58:57"
  }
}
```

- Result: PASS

### 25. Get my requests after accept

- API: `GET /api/join-requests/my`
- Input: requester token
- Output:

```json
{
  "success": true,
  "message": "My join requests retrieved.",
  "data": [
    { "requestId": 9, "teamId": 11, "status": "ACCEPTED" },
    { "requestId": 10, "teamId": 12, "status": "DECLINED" }
  ]
}
```

- Result: PASS
- Note: accepting AI request auto-declined the other pending request from the same requester in the same event.

### 26. Web leader pending list after auto-decline

- API: `GET /api/join-requests/teams/12`
- Input: Web leader token
- Output:

```json
{
  "success": true,
  "message": "Pending join requests retrieved.",
  "data": []
}
```

- Result: PASS
- Note: team request list returns only `PENDING`, so auto-declined request `10` is hidden.

### 27. Joinable teams after requester already joined event team

- API: `GET /api/join-requests/joinable-teams?eventId=4`
- Input: requester token
- Output:

```json
{
  "success": true,
  "message": "Joinable teams retrieved.",
  "data": {
    "totalJoinableTeams": 0,
    "teams": []
  }
}
```

- Result: PASS

### 28. Accept auto-declined Web request

- API: `PUT /api/join-requests/10/accept`
- Input: Web leader token
- Output:

```json
{
  "success": false,
  "message": "This join request has already been declined."
}
```

- Result: PASS

### 29. Decline already accepted AI request

- API: `PUT /api/join-requests/9/decline`
- Input: AI leader token
- Output:

```json
{
  "success": false,
  "message": "This join request has already been accepted."
}
```

- Result: PASS

### 30. Withdraw accepted request

- API: `DELETE /api/join-requests/9`
- Input: requester token
- Output:

```json
{
  "success": false,
  "message": "Only pending join requests can be withdrawn."
}
```

- Result: PASS

### 31. Create new request after already joined event team

- API: `POST /api/join-requests/teams/12`
- Input:

```json
{
  "message": "Try after accepted elsewhere"
}
```

- Output:

```json
{
  "success": false,
  "message": "You are already a member of a team in this event."
}
```

- Result: PASS

### 32. List pending requests for missing team

- API: `GET /api/join-requests/teams/999999`
- Input: requester token
- Output:

```json
{
  "success": false,
  "message": "Team not found: 999999"
}
```

- Result: PASS

### 33. Delete malformed URL missing requestId

- API: `DELETE /api/join-requests/`
- Input: requester token
- Output:

```json
{
  "success": false,
  "message": "Resource not found: api/join-requests"
}
```

- Result: PASS

## DB Side-Effect Check Before Final Reset

Direct DB check after the accept flow:

| Request ID | Team | Status |
|---:|---|---|
| 9 | Join AI Open | ACCEPTED |
| 10 | Join Web Open | DECLINED |

Team member count after accept:

| Team ID | Team | Members |
|---:|---|---:|
| 11 | Join AI Open | 2 |
| 12 | Join Web Open | 2 |
| 13 | Join AI Full | 5 |
| 14 | Join AI Pending | 1 |

Then the seed script was run again, leaving `JoinRequest` rows for the test event at `0`.

## Files Created Or Changed In This Test Pass

- Changed: `back-end/src/seal-api/src/main/java/com/seal/hackathon/exception/GlobalExceptionHandler.java`
- Created: `back-end/AI logs/JOIN_REQUEST_API_TEST_REPORT.md`

