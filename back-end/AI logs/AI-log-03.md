# AI Session Log — Backend API Completion

**Date:** 2026-06-09  
**Project:** SEAL – Software Engineering Hackathon Management System  
**Branch:** `NhatNHM-SyncDataRefactoring`  
**Scope:** Hoàn thiện toàn bộ REST API Backend — từ Event CRUD đến Scoring, RoundResult, Notification  
**Build result:** `BUILD SUCCESS` — 112 source files compiled, 0 errors

---

## Bối cảnh

Backend trước session này chỉ có module Auth và một số endpoint cơ bản (GET events, create team, get my team, get round detail, mentor/judge assignments). Toàn bộ domain logic cho Submission, Scoring, Results, Notifications, TeamInvite, và CRUD cho Event/Track/Round chưa tồn tại.

---

## Pre-session Fixes (mockData.ts + seal_hackathon.sql)

Trước khi implement API, phát hiện 2 lỗi cần fix ngay:

### `seal_hackathon.sql`
| Line | Lỗi | Fix |
|------|-----|-----|
| 141 | Stray word `teammember` trong column `created_at` của bảng `Team` | Xoá từ thừa — SQL sẽ fail khi chạy nếu không fix |

### `mockData.ts` (Frontend)
| Interface | Vấn đề | Fix |
|-----------|---------|-----|
| `Prize` | `rank_position: number` nhưng data entry `prize_id: 5` có `null` | Đổi thành `number \| null` |
| `AccountApproval` | Thiếu `reviewed_by`, `reviewed_at` | Thêm vào interface + data |
| `HackathonEvent` | Thiếu `year`, `description`, `registration_start/end`, `created_by`, `created_at` | Thêm vào interface + data |
| `Round` | Thiếu `start_time`, `end_time`, `is_calibration` | Thêm vào interface + data |
| `Submission` | Thiếu `submitted_by` (NOT NULL trong DB), `status` | Thêm vào interface + data |
| `Score` | Thiếu `comment`, `updated_at` | Thêm vào interface + data |
| `RoundResult` | Thiếu `is_published`, `finalized_at`, `finalized_by` | Thêm vào interface + data |
| `Prize` | Thiếu `track_id`, `team_id`, `awarded_at` | Thêm vào interface + data |
| Thiếu hoàn toàn | `TeamAssignment` interface + data | Thêm mới (17 records) |

**Lưu ý giữ nguyên:** `User.university_name` — DB dùng `university` nhưng 3 component frontend đang dùng `university_name`, sẽ map tại API layer.

---

## Phase 1 — Event / Track / Round CRUD

### Files mới / cập nhật

| File | Loại | Nội dung |
|------|------|----------|
| `dto/request/CreateEventRequest.java` | New | name, season, year, description, dates, status |
| `dto/request/UpdateEventRequest.java` | New | Tất cả fields nullable (partial update) |
| `dto/request/CreateTrackRequest.java` | New | name, description |
| `dto/request/CreateRoundRequest.java` | New | name, orderNumber, times, topNAdvance, isCalibration |
| `dto/request/UpdateRoundRequest.java` | New | Tất cả nullable + status |
| `dto/response/RoundResponse.java` | New | Full round fields kể cả eventId, eventName |
| `dto/response/TrackResponse.java` | Updated | Thêm `eventId` |
| `service/HackathonEventService.java` | Updated | Thêm `createEvent()`, `updateEvent()`, `getEventById()` |
| `service/TrackService.java` | New | getTracksByEvent, getTrackById, createTrack, updateTrack, deleteTrack |
| `service/RoundService.java` | Updated | Thêm `getRoundsByEvent()`, `createRound()`, `updateRound()` |
| `controller/HackathonEventController.java` | Updated | `GET /{id}`, `POST /`, `PUT /{id}` |
| `controller/TrackController.java` | New | Full CRUD tại `/api/events/{id}/tracks` |
| `controller/RoundController.java` | Updated | Đổi base path → `/api/events/{id}/rounds`, thêm GET all, POST, PUT |
| `repository/RoundRepository.java` | Updated | Thêm `findAllByEvent_EventIdOrderByOrderNumber()` |
| `repository/TeamRepository.java` | Updated | Thêm `findAllByEvent_EventId()`, `findAllByEvent_EventIdAndStatus()` |

### Endpoints mới

```
GET  /api/events/{eventId}                            — public
POST /api/events                                      — EVENT_COORDINATOR
PUT  /api/events/{eventId}                            — EVENT_COORDINATOR
GET  /api/events/{eventId}/tracks                     — public
GET  /api/events/{eventId}/tracks/{trackId}           — public
POST /api/events/{eventId}/tracks                     — EVENT_COORDINATOR
PUT  /api/events/{eventId}/tracks/{trackId}           — EVENT_COORDINATOR
DELETE /api/events/{eventId}/tracks/{trackId}         — EVENT_COORDINATOR
GET  /api/events/{eventId}/rounds                     — public
GET  /api/events/{eventId}/rounds/{roundId}           — public
POST /api/events/{eventId}/rounds                     — EVENT_COORDINATOR
PUT  /api/events/{eventId}/rounds/{roundId}           — EVENT_COORDINATOR
```

---

## Phase 2 — Team Management (Coordinator View)

### Files mới / cập nhật

| File | Loại | Nội dung |
|------|------|----------|
| `dto/request/RejectTeamRequest.java` | New | `reason: String` (optional) |
| `dto/response/TeamDetailResponse.java` | New | Full team + nested `List<MemberInfo>` (userId, fullName, email, memberRole, joinedAt) |
| `service/TeamService.java` | Updated | Thêm `getTeamsByEvent()`, `getTeamById()`, `approveTeam()`, `rejectTeam()`, `disqualifyTeam()` |
| `controller/TeamController.java` | Updated | Thêm 5 coordinator endpoints |

### Endpoints mới

```
GET  /api/teams/event/{eventId}         — EVENT_COORDINATOR
GET  /api/teams/{teamId}                — EVENT_COORDINATOR, MENTOR, JUDGE
PUT  /api/teams/{teamId}/approve        — EVENT_COORDINATOR
PUT  /api/teams/{teamId}/reject         — EVENT_COORDINATOR (body: reason?)
PUT  /api/teams/{teamId}/disqualify     — EVENT_COORDINATOR (body: reason?)
```

---

## Phase 3 — Team Invite

Lý do: Participant (user không có team) cần cơ chế nhận lời mời từ Team Leader trước khi gia nhập team.

### Entity: `TeamInvite`

```
invite_id (PK), team_id (FK), invited_user_id (FK), invited_by (FK),
message (TEXT), status (PENDING|ACCEPTED|DECLINED),
created_at, responded_at
UNIQUE(team_id, invited_user_id)
```

### Business rules

- Chỉ **Team Leader** mới được gửi invite
- Không invite user đã có team trong cùng event
- Khi accept: tự động tạo `TeamMember` record + cancel tất cả pending invites khác của user đó trong cùng event
- Khi decline: chỉ update status

### Files mới

| File | Nội dung |
|------|----------|
| `entity/TeamInvite.java` | JPA entity |
| `repository/TeamInviteRepository.java` | `findByInvitedUser_UserIdAndStatus`, `existsByTeam_TeamIdAndInvitedUser_UserId` |
| `dto/request/CreateInviteRequest.java` | `invitedUserId`, `message` |
| `dto/response/TeamInviteResponse.java` | Full invite info kèm tên team, event, các user |
| `service/TeamInviteService.java` | createInvite, getPendingInvites, acceptInvite, declineInvite |
| `controller/TeamInviteController.java` | 4 endpoints tại `/api/invites` |

### Endpoints

```
POST /api/invites/teams/{teamId}     — PARTICIPANT (phải là Leader)
GET  /api/invites/pending            — PARTICIPANT
PUT  /api/invites/{inviteId}/accept  — PARTICIPANT
PUT  /api/invites/{inviteId}/decline — PARTICIPANT
```

---

## Phase 4 — Submission

### Entity: `Submission`

```
submission_id (PK), team_id (FK), round_id (FK),
repo_url, demo_url, slide_url, description,
submitted_at, submitted_by (FK → User),
status (DRAFT|SUBMITTED|LATE|INVALID)
UNIQUE(team_id, round_id)
```

### Business rules

- Chỉ accept khi Round status = `ACTIVE`
- Nếu submit sau `submissionDeadline` → status tự động = `LATE`
- Upsert: submit lại cùng round sẽ update bài nộp cũ
- User phải thuộc team `APPROVED` trong event tương ứng

### Files mới

| File | Nội dung |
|------|----------|
| `entity/Submission.java` | JPA entity, `@PrePersist @PreUpdate` tự set `submittedAt` |
| `repository/SubmissionRepository.java` | findByTeam+Round (unique), findAllByRound, findAllByTeam |
| `dto/request/SubmitRequest.java` | roundId, repoUrl, demoUrl, slideUrl, description |
| `dto/response/SubmissionResponse.java` | Full submission info + team/round names |
| `service/SubmissionService.java` | submit (upsert), getMySubmission, getSubmissionsByRound, getById |
| `controller/SubmissionController.java` | 4 endpoints tại `/api/submissions` |

### Endpoints

```
POST /api/submissions                          — PARTICIPANT
GET  /api/submissions/my/round/{roundId}       — PARTICIPANT
GET  /api/submissions/round/{roundId}          — JUDGE, EVENT_COORDINATOR
GET  /api/submissions/{submissionId}           — PARTICIPANT, JUDGE, EVENT_COORDINATOR
```

---

## Phase 5 — Scoring

### Entities

**`ScoringCriteriaTemplate`** — template tái dùng cho các event khác nhau  
**`ScoringCriteria`** — criteria cho 1 round cụ thể, có FK → event, round, template  
**`Score`** — kết quả chấm: `UNIQUE(submission_id, judge_user_id, criteria_id)`

```
Score fields: value (DECIMAL 5,2), comment, is_draft, scored_at, updated_at
```

### Business rules

- Judge có thể lưu draft (`is_draft: true`) nhiều lần, chỉ submit final khi sẵn sàng
- Validate: `value <= criteria.maxScore`
- Score là upsert — judge chấm lại cùng criteria sẽ update record cũ
- `@PreUpdate` tự cập nhật `updated_at`

### Files mới

| File | Nội dung |
|------|----------|
| `entity/ScoringCriteriaTemplate.java` | JPA entity |
| `entity/ScoringCriteria.java` | FK → event, round, template; weight + maxScore (BigDecimal) |
| `entity/Score.java` | FK → submission, judge, criteria; `@PrePersist/@PreUpdate` |
| `repository/ScoringCriteriaTemplateRepository.java` | `findFirstByIsDefaultTrue()` |
| `repository/ScoringCriteriaRepository.java` | findByRound, findByEventAndNoRound |
| `repository/ScoreRepository.java` | findBySubmission, findByJudge+Round, upsert lookup |
| `dto/request/CreateCriteriaRequest.java` | name, description, weight, maxScore, orderNumber |
| `dto/request/SubmitScoresRequest.java` | submissionId, draft flag, `List<ScoreEntry{criteriaId, value, comment}>` |
| `dto/response/ScoringCriteriaResponse.java` | criteria fields |
| `dto/response/ScoreResponse.java` | score + judge/criteria names resolved |
| `service/ScoringService.java` | getCriteriaByRound, createCriteria, submitScores (batch upsert), getScoresBySubmission, getMyScoresByRound |
| `controller/ScoringController.java` | 5 endpoints tại `/api/events/.../criteria` + `/api/scores` |

### Endpoints

```
GET  /api/events/{eventId}/rounds/{roundId}/criteria        — authenticated
POST /api/events/{eventId}/rounds/{roundId}/criteria        — EVENT_COORDINATOR
POST /api/scores                                            — JUDGE (batch)
GET  /api/scores/submission/{submissionId}                  — JUDGE, EVENT_COORDINATOR
GET  /api/scores/my/round/{roundId}                         — JUDGE
```

---

## Phase 6 — Round Results & Leaderboard

### Entity: `RoundResult`

```
result_id (PK), team_id (FK), round_id (FK),
total_score (DECIMAL 7,2), rank_position, advanced,
is_published, finalized_at, finalized_by (FK → User)
UNIQUE(team_id, round_id)
```

### Finalize algorithm

1. Lấy tất cả `Submission` của round
2. Với mỗi submission: lấy tất cả `Score` có `is_draft = false`
3. Group scores by `judge_user_id`, tính `SUM(value × weight)` cho mỗi judge
4. Average qua tất cả judges → `total_score` của team đó
5. Sort descending → assign `rank_position` (1 = nhất)
6. Nếu `round.topNAdvance != null` → các team rank ≤ topN được `advanced = true`
7. Delete existing results trước khi re-compute (idempotent)
8. Set `round.status = FINALIZED`

### Publish

- Chỉ set `is_published = true` trên tất cả results của round đó
- Public GET chỉ thấy published results; coordinator thấy tất cả

### Files mới

| File | Nội dung |
|------|----------|
| `entity/RoundResult.java` | JPA entity |
| `repository/RoundResultRepository.java` | findByRound+published, findByRound (all), findByTeam+Round |
| `dto/response/RoundResultResponse.java` | result + team/track/round names + finalized info |
| `service/RoundResultService.java` | getPublishedResults, getAllResults, finalizeRound, publishResults |
| `controller/RoundResultController.java` | 4 endpoints tại `/api/events/{eid}/rounds/{rid}/results` |

### Endpoints

```
GET  /api/events/{eventId}/rounds/{roundId}/results          — public (published only)
GET  /api/events/{eventId}/rounds/{roundId}/results/all      — EVENT_COORDINATOR
POST /api/events/{eventId}/rounds/{roundId}/results/finalize — EVENT_COORDINATOR
POST /api/events/{eventId}/rounds/{roundId}/results/publish  — EVENT_COORDINATOR
```

---

## Phase 7 — Notifications

### Entity: `Notification`

```
notification_id (PK), recipient_user_id (FK),
title, content, type (ANNOUNCEMENT|RESULT|REMINDER|ASSIGNMENT|APPROVAL),
related_event_id (FK, nullable), is_read, created_at
```

### Design

- `NotificationService.createNotification()` là internal method — các service khác (approve team, publish result...) có thể gọi để tạo notification
- Không expose endpoint tạo notification trực tiếp (coordinator dùng qua side effects)

### Files mới

| File | Nội dung |
|------|----------|
| `entity/Notification.java` | JPA entity |
| `repository/NotificationRepository.java` | findByRecipient (all/unread), countUnread |
| `dto/response/NotificationResponse.java` | notification + relatedEventName resolved |
| `service/NotificationService.java` | getMyNotifications, getUnreadCount, markAsRead, markAllAsRead, createNotification (internal) |
| `controller/NotificationController.java` | 4 endpoints tại `/api/notifications` |

### Endpoints

```
GET  /api/notifications                          — authenticated
GET  /api/notifications/unread-count             — authenticated
PUT  /api/notifications/{notificationId}/read    — authenticated (owner only)
PUT  /api/notifications/read-all                 — authenticated
```

---

## Phase 8 — SecurityConfig Update

Thêm permission rules cho endpoints mới:

```java
.requestMatchers("/api/notifications/**").authenticated()
.requestMatchers("/api/invites/**").hasAnyRole("PARTICIPANT", "EVENT_COORDINATOR")
.requestMatchers("/api/events/*/rounds/*/results/**").authenticated()
```

---

## Tổng quan thay đổi

### Files mới tạo: 37

| Nhóm | Số files |
|------|----------|
| Entity (JPA) | 8 (`TeamInvite`, `Submission`, `ScoringCriteriaTemplate`, `ScoringCriteria`, `Score`, `RoundResult`, `Notification`, cập nhật `Round`) |
| Repository | 8 |
| Request DTO | 9 |
| Response DTO | 9 |
| Service | 6 (mới) + 4 (cập nhật) |
| Controller | 6 (mới) + 4 (cập nhật) |

### Files cập nhật: 10

`HackathonEventService`, `HackathonEventController`, `RoundService`, `RoundController`, `RoundRepository`, `TeamService`, `TeamController`, `TeamRepository`, `TrackResponse`, `SecurityConfig`

---

## API Surface hoàn chỉnh sau session

| Module | Method | Endpoint | Auth |
|--------|--------|----------|------|
| **Auth** | POST | `/api/auth/register` | Public |
| | POST | `/api/auth/login` | Public |
| | GET | `/api/auth/me` | Authenticated |
| | POST | `/api/auth/logout` | Authenticated |
| **Events** | GET | `/api/events` | Public |
| | GET | `/api/events/{id}` | Public |
| | POST | `/api/events` | COORDINATOR |
| | PUT | `/api/events/{id}` | COORDINATOR |
| **Tracks** | GET | `/api/events/{id}/tracks` | Public |
| | GET | `/api/events/{id}/tracks/{tid}` | Public |
| | POST | `/api/events/{id}/tracks` | COORDINATOR |
| | PUT | `/api/events/{id}/tracks/{tid}` | COORDINATOR |
| | DELETE | `/api/events/{id}/tracks/{tid}` | COORDINATOR |
| **Rounds** | GET | `/api/events/{id}/rounds` | Public |
| | GET | `/api/events/{id}/rounds/{rid}` | Public |
| | POST | `/api/events/{id}/rounds` | COORDINATOR |
| | PUT | `/api/events/{id}/rounds/{rid}` | COORDINATOR |
| **Teams** | POST | `/api/teams` | PARTICIPANT |
| | GET | `/api/teams/my` | PARTICIPANT |
| | GET | `/api/teams/active-events` | Public |
| | GET | `/api/teams/event/{eventId}` | COORDINATOR |
| | GET | `/api/teams/{id}` | COORDINATOR, MENTOR, JUDGE |
| | PUT | `/api/teams/{id}/approve` | COORDINATOR |
| | PUT | `/api/teams/{id}/reject` | COORDINATOR |
| | PUT | `/api/teams/{id}/disqualify` | COORDINATOR |
| **Users** | POST | `/api/users/staff` | COORDINATOR |
| | GET | `/api/users` | COORDINATOR |
| | GET | `/api/users/{id}` | COORDINATOR |
| | GET | `/api/users/roles` | COORDINATOR |
| | PUT | `/api/users/{id}/activate` | COORDINATOR |
| | PUT | `/api/users/{id}/deactivate` | COORDINATOR |
| | POST | `/api/users/{id}/roles` | COORDINATOR |
| **Account Approvals** | GET | `/api/account-approvals/pending` | COORDINATOR |
| | PUT | `/api/account-approvals/{id}/approve` | COORDINATOR |
| | PUT | `/api/account-approvals/{id}/reject` | COORDINATOR |
| **Invites** | POST | `/api/invites/teams/{teamId}` | PARTICIPANT |
| | GET | `/api/invites/pending` | PARTICIPANT |
| | PUT | `/api/invites/{id}/accept` | PARTICIPANT |
| | PUT | `/api/invites/{id}/decline` | PARTICIPANT |
| **Submissions** | POST | `/api/submissions` | PARTICIPANT |
| | GET | `/api/submissions/my/round/{rid}` | PARTICIPANT |
| | GET | `/api/submissions/round/{rid}` | JUDGE, COORDINATOR |
| | GET | `/api/submissions/{id}` | PARTICIPANT, JUDGE, COORDINATOR |
| **Scoring** | GET | `/api/events/{eid}/rounds/{rid}/criteria` | Authenticated |
| | POST | `/api/events/{eid}/rounds/{rid}/criteria` | COORDINATOR |
| | POST | `/api/scores` | JUDGE |
| | GET | `/api/scores/submission/{id}` | JUDGE, COORDINATOR |
| | GET | `/api/scores/my/round/{rid}` | JUDGE |
| **Results** | GET | `/api/events/{eid}/rounds/{rid}/results` | Public (published) |
| | GET | `/api/events/{eid}/rounds/{rid}/results/all` | COORDINATOR |
| | POST | `/api/events/{eid}/rounds/{rid}/results/finalize` | COORDINATOR |
| | POST | `/api/events/{eid}/rounds/{rid}/results/publish` | COORDINATOR |
| **Assignments** | GET | `/api/mentor/assignments` | MENTOR |
| | GET | `/api/judge/assignments` | JUDGE |
| **Notifications** | GET | `/api/notifications` | Authenticated |
| | GET | `/api/notifications/unread-count` | Authenticated |
| | PUT | `/api/notifications/{id}/read` | Authenticated (owner) |
| | PUT | `/api/notifications/read-all` | Authenticated |
