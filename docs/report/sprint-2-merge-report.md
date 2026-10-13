# Sprint 2 — Merge Report (`develop` → `main`)

**Project:** SEAL Hackathon Management System
**Prepared for:** merge review before promoting `develop` into `main` at the end of Sprint 2
**Branch state:** `main` is a direct ancestor of `develop` (main is 0 commits ahead, develop is 112 commits ahead) → the merge is a clean **fast-forward**, no conflicts.

| Metric | Value |
|---|---|
| Commits (`main..develop`) | 112 |
| Files changed | 271 |
| Lines added / removed | +31,002 / −13,710 |
| Merge type | Fast-forward (main becomes byte-for-byte identical to develop) |

---

## 1. Executive Summary

Sprint 2 turns the system from a mock-driven prototype into a backend-connected application and introduces the platform/competition responsibility split. The headline outcomes:

1. **System Admin / Coordinator split** — a new `SYSTEM_ADMIN` role owns the *platform* (users, role grants, system logs, event creation/completion/reopen), while `EVENT_COORDINATOR` runs the *competition* (rounds, tracks, scoring, approvals) scoped to an event.
2. **Event lifecycle with a `SETUP` stage** — status-driven gating (not date-driven), per-event track assignment by self-select or random draw, and per-track capacity.
3. **Frontend de-mocking** — `mockData.ts` and `DevToolbar` removed; a real HTTP `apiClient` with 17 endpoint groups mapping 1:1 to backend controllers.
4. **New workflows** — event reopen requests, team join requests, team invites, in-app notifications, and a competition scoring/results pipeline.
5. **Hardened validation & tests** on team, track and invite flows.

The merge is technically low-risk (fast-forward). The main operational note is the database strategy (full rebuild from `seal_schema.sql` + `seal_seed.sql`, by design) and a couple of cosmetic config cleanups.

---

## 2. Sprint 2 Scope (Feature Map)

| # | Feature area | Backend | Frontend | DB |
|---|---|---|---|---|
| 1 | Admin / Coordinator split | `AdminController`, `AdminService`, `CoordinatorController` | `Admin*` pages, role normalization | `SYSTEM_ADMIN` role, `SystemLog` |
| 2 | Event lifecycle + SETUP & tracks | `HackathonEventService` state machine, `TeamService.drawTracks/selectTrack` | `CoordEventsPage`, `eventUtils`, `teamPhase` | `SETUP` status, `track_selection_mode`, `Track.capacity`, nullable `Team.track_id` |
| 3 | Event reopen workflow | `ReopenRequestController/Service` | reopen request UI | `ReopenRequest` table |
| 4 | Audit & events console | `AuditLogController/Service` | events console + audit tab | `AuditLog` wiring |
| 5 | Join requests | `JoinRequestController/Service` | join flow UI | `JoinRequest` table |
| 6 | Team invites + validation | `TeamInviteController/Service` (+ tests) | invitations drawer | `TeamInvite` table |
| 7 | Scoring & results | `ScoringService`, `RoundResultService` | `JudgeScoringPage`, `LeaderboardPage` | `Score`, `ScoringCriteria(Template)`, `RoundResult` |
| 8 | Notifications | `NotificationController/Service` | `NotificationProvider` | `Notification` table |
| 9 | Participant onboarding/UX | profile/avatar endpoints, `EmailService` | onboarding tour, rules modal, journey bar, profile | — |
| 10 | Auth / OAuth2 | `AuthService`, complete-profile, approval gate | `CompleteProfilePage`, `OAuth2RedirectPage` | `User.judge_type` |
| 11 | Assignment redesign | `AssignmentController/Service` | coordinator assignment matrix | `JudgeAssignment`, `MentorAssignment` (replaced `TeamAssignment`) |
| 12 | Cleanup | removed legacy docs/SQL & AI logs | removed `mockData`, `DevToolbar`, legacy dashboards | removed duplicate DB scripts |

---

## 3. Database & Migration

### 3.1 Strategy (intentional)

The team owns the schema through two long-lived source-of-truth files, rebuilt on every change:

- `back-end/database scripts/seal_schema.sql` — full DDL (drops & recreates the database; **wipes data** on run).
- `back-end/database scripts/seal_seed.sql` — all seed rows, including the bootstrap `SYSTEM_ADMIN` (user_id 1) and a demo coordinator.

The two `migration_*.sql` files (`migration_audit_log.sql`, `migration_reopen_request.sql`) are **one-shot, additive `CREATE TABLE IF NOT EXISTS` helpers** for older databases — they are no-ops on a current schema and are not part of an ongoing migration framework. This is a deliberate MVP choice: dev/demo databases are rebuilt from schema + seed, not migrated in place.

> Note for later: there is no incremental migration tooling (Flyway/Liquibase). Any environment holding real data would lose it on a schema rebuild. Acceptable for the current stage; flagged for a future sprint.

### 3.2 Structural changes vs `main`

| Table | Change | Type |
|---|---|---|
| `Role` | Added `SYSTEM_ADMIN` inserted first → `role_id` values shift down (COORDINATOR 1→2, etc.) | Breaking (data) |
| `User` | `+judge_type` (INTERNAL/GUEST); `expired_at` semantics changed (manual expiry, no `ON UPDATE`) | Additive |
| `HackathonEvent` | `+track_selection_mode`; status adds `SETUP`; dropped FK `created_by` | Mixed |
| `Track` | `+capacity` (auto-computed on entering SETUP) | Additive |
| `Team` | `track_id` `NOT NULL` → nullable (assigned later) | Relaxing |
| `Round` | dropped `is_calibration` | Breaking |
| `UserEventRole` | dropped `assigned_at`, `assigned_by` + FK; added system-wide index | Breaking |
| `JudgeAssignment` | dropped `judge_type`, `assigned_at`, `assigned_by` (moved to AuditLog/User) | Breaking |
| `MentorAssignment` | dropped `assigned_at`, `assigned_by` | Breaking |
| `Notification` | dropped `related_event_id` + FK | Breaking |
| `RoundResult` | dropped `advanced` column (now derived from `rank_position <= top_n_advance`) | Breaking |
| `JoinRequest`, `ReopenRequest`, `SystemLog` | new tables | New |

`SystemLog` (platform/admin events, admin-only) is kept separate from `AuditLog` (competition business actions, also readable by the owning coordinator).

### 3.3 Legacy script cleanup

`develop` already removes the obsolete DB scripts that exist on `main` (commit `bec597a`): `docs/database/MySQL/*`, `docs/database/SQL server/*`, `docs/database/dbuml/seal_erd.html`. The fast-forward merge applies these deletions automatically, leaving a single clean script set under `back-end/database scripts/`.

---

## 4. Backend API

### 4.1 Authorization model

- **`PARTICIPANT`** is *derived* from `User.user_type` (`FPT_STUDENT` / `EXTERNAL_STUDENT`) → `ROLE_PARTICIPANT`. Staff roles come from `UserEventRole` as `ROLE_<role_name>` (`UserPrincipal.buildAuthorities`), loaded via a FETCH JOIN to avoid N+1.
- Staff role authorities are **global** (not scoped per event at the authority layer). Per-event restriction is enforced in the **service layer** via ownership checks — a deliberate MVP pattern.
- Two enforcement layers: URL matchers in `SecurityConfig` + method-level `@PreAuthorize`. `@EnableMethodSecurity` is enabled, so method security is authoritative even where a URL is `permitAll`.

### 4.2 API surface (by authorization group)

| Group | Base path | Authority | Notes |
|---|---|---|---|
| Admin (platform) | `/api/admin/**` | `SYSTEM_ADMIN` | users CRUD, activate/deactivate, grant/revoke role, system-logs, reopen-request approve/reject |
| Coordinator | `/api/coordinator/**` | `EVENT_COORDINATOR` | staff list, create guest judge, assign mentors/judges |
| Account approval | `/api/account-approvals/**` | `EVENT_COORDINATOR` | pending / approve / reject |
| Events | `/api/events/**` | public GET; writes via method security | create = ADMIN; complete/reopen = ADMIN; update = ADMIN or COORDINATOR |
| Rounds / Tracks / Audit | `/api/events/{id}/...` | `EVENT_COORDINATOR` (audit also ADMIN) | |
| Scoring | `/api/scores`, `/criteria` | JUDGE scores; COORDINATOR defines criteria | |
| Round results | `.../results` | published = public; all/finalize/publish = COORDINATOR | |
| Teams | `/api/teams/**` | PARTICIPANT; COORDINATOR/MENTOR for views | draw-tracks, approve/reject/disqualify = COORDINATOR |
| Invites / Join requests | `/api/invites`, `/api/join-requests` | PARTICIPANT | |
| Submissions | `/api/submissions/**` | PARTICIPANT submit; JUDGE/COORDINATOR view | |
| Notifications | `/api/notifications/**` | any authenticated user | |

### 4.3 Event lifecycle state machine (verified)

Backend is the source of truth; FE buttons only mirror it.

```
DRAFT ↔ OPEN ↔ SETUP ↔ IN_PROGRESS      (free forward/backward)
COMPLETED → {}     (no exit via generic PUT) → use admin-only /reopen
CANCELLED → DRAFT  (can be revived)
```

- `complete` (`IN_PROGRESS → COMPLETED`) and `reopen` (`COMPLETED → IN_PROGRESS`) are **dedicated SYSTEM_ADMIN-only endpoints**, intentionally excluded from the generic transition map so a coordinator's `PUT` can never complete or reopen an event.
- Entering `SETUP` closes registration, **freezes the roster**, and computes per-track capacity.

### 4.4 Track capacity & assignment

- `computeTrackCapacities`: distributes approved teams evenly across tracks (`floor = approved / trackCount`, remainder spread one each to the first tracks) so per-track counts differ by at most 1 and total capacity equals approved-team count.
- `drawTracks` (RANDOM mode): SETUP-only, shuffle + capacity-respecting greedy fill; a REDRAW wipes and reshuffles all teams, with an audit entry.
- `selectTrack` (SELF_SELECT mode): SETUP-only, correct mode, approved team, track belongs to event, and **capacity is checked** (excluding the team itself).

---

## 5. Frontend

### 5.1 De-mocking (headline FE change)

- `mockData.ts` (−440) and `DevToolbar.tsx` (−133) removed; no remaining references.
- `apiClient.ts` (+568) is a real HTTP layer: `fetch` + `Authorization: Bearer`, `BASE_URL` from `VITE_API_URL` (defaults to `http://localhost:8080`).
- **17 API groups** map 1:1 to backend controllers: `authApi, accountApprovalsApi, adminApi, eventsApi, reopenRequestsApi, auditLogsApi, tracksApi, roundsApi, teamsApi, invitesApi, joinRequestsApi, submissionsApi, scoringApi, resultsApi, notificationsApi, assignmentsApi, coordinatorApi`.

### 5.2 Admin / Coordinator split on the FE

- New: `AdminEventsPage`, `AdminAccountsPage`, `AdminRolesPage`, `AdminSystemLogsPage`, `AdminDashboard`, `adminApi`.
- Removed: `AdminPage` (legacy combined), `CoordAuditPage`, `CoordPrizesPage`.
- Routes split `/admin/*` (single-role, no role gate) vs `/coordinator/*` (behind `RoleGate`).

### 5.3 Permissions & routing

- `permissions.ts` (new, unit-tested): pure functions `canCreateEvent`, `canReopenEvent`, `canRequestReopen`, `canCompleteEvent`, `canChangeEventStatus`, `canManageReopenRequests`, plus a `usePermissions()` hook. Documented explicitly: the backend is the real gate; FE checks are UX only.
- `AuthProvider.mapBackendRole` normalizes raw backend names by substring match (`SYSTEM_ADMIN → ADMIN`, `EVENT_COORDINATOR → COORDINATOR`) with priority `ADMIN > COORDINATOR > JUDGE > MENTOR > PARTICIPANT`, tolerant of multiple profile shapes (`roles/role/roleName/role_name`, string or array).
- Route guards: `RequireAuth` handles session-restore, unauthenticated, incomplete-profile, not-approved, and role mismatch redirects; `RoleGate` forces multi-role staff to pick an active role first.

### 5.4 Participant dashboard refactor & UX

- The 1,441-line `ParticipantDashboard` is split into screens (`NoTeamDashboard`, `ExistingTeamDashboard`, `CreateTeamScreen`, `SuccessScreen`), components (`EventDetailDrawer`, `InvitationsDrawer`) and `utils/formatters`.
- `TeamManagePage` (−411) consolidated into `TeamViewPage` (+594); `/team/manage` redirects to `/team/view`.
- `teamPhase.ts` mirrors backend lifecycle rules: team editable only in `OPEN`/`SETUP`; `canPickTrack` only in `SETUP` + `SELF_SELECT`; team size 3–5.
- New onboarding/UX: `OnboardingTour` + `TourProvider`, `CompetitionRulesModal` + `RulesProvider`, `ParticipantJourneyBar`, `CompleteProfilePage`, expanded `ProfilePage` (avatar, change password, locked student ID).
- FE test infrastructure added: `vitest` setup, `permissions.test.ts`, `ConfirmDialog.test.tsx`.

---

## 6. Risks, Findings & Recommendations

| # | Severity | Finding | Recommendation |
|---|---|---|---|
| 1 | Info | DB is rebuilt from schema + seed; no incremental migration tooling | Adopt Flyway/Liquibase in a future sprint if real data must be preserved |
| 2 | Info | `Role` ids shift because `SYSTEM_ADMIN` is inserted first | Always rebuild from the current `seal_schema.sql` + `seal_seed.sql`; never run new code over a pre-Sprint-2 database |
| 3 | Low (cosmetic) | `SecurityConfig` has a dead matcher: `/api/events/*/rounds/*/results/**` `.authenticated()` is unreachable because `/api/events/**` `permitAll` precedes it | Remove the dead line (behavior is already correct via method security) |
| 4 | Low (cosmetic) | `/api/join-requests/**` matcher declared twice | Remove the duplicate |
| 5 | Low | `selectTrack` capacity check is read-then-write without locking → concurrent leaders could exceed a track by one slot | Acceptable for MVP; add a DB constraint or pessimistic lock if needed later |
| 6 | Low | `computeTrackCapacities` has no guard when entering SETUP with 0 approved teams (all capacities become 0) | Add a guard or validation if the empty-roster case is reachable |
| 7 | Info | FE requires `VITE_API_URL` in production; otherwise falls back to `localhost:8080` | Add to the deployment checklist |

**Security posture:** sound. All mutating event endpoints are protected by `@PreAuthorize`; public endpoints (event listing, published results) are public by design. FE guards are UX only — the backend is the authoritative gate.

---

## 7. Pre-Merge Checklist

- [ ] `develop` builds and tests pass (backend + frontend).
- [ ] Database rebuilt from `seal_schema.sql` + `seal_seed.sql` and smoke-tested (admin + coordinator bootstrap accounts log in).
- [ ] `VITE_API_URL` set for the target frontend environment.
- [ ] (Optional cleanup) remove the two cosmetic `SecurityConfig` lines (findings #3, #4).
- [ ] Merge `develop` → `main` as a fast-forward (or via PR), confirming legacy DB scripts are dropped on `main`.
- [ ] Attach / link this report in the merge PR description.

### Suggested merge commands

```bash
# Fast-forward (main becomes identical to develop)
git checkout main
git pull origin main
git merge --ff-only develop
git push origin main
```

If `main` is branch-protected, open a PR (`develop` → `main`) and merge via GitHub instead.
