# Sprint 3 — Merge Report (`develop` → `main`)

**Project:** SEAL Hackathon Management System
**Prepared for:** merge review before promoting `develop` into `main` at the end of Sprint 3 (final sprint)
**Branch state:** `main` (`d9dce0f`, PR #348, merged 2026-06-27) is the exact merge-base of `develop` (`296e03a`, PR #387, merged 2026-07-18) — main is 0 commits ahead, develop is 232 commits ahead → the merge is a clean **fast-forward**, no textual conflicts (`git merge-tree` confirms).

| Metric | Value |
|---|---|
| Commits (`main..develop`) | 232 |
| Files changed | 308 |
| Lines added / removed | +34,035 / −7,911 |
| Merge type | Fast-forward (main becomes byte-for-byte identical to develop) |
| Textual conflicts | None |
| **Operational risk** | **Not** low-risk despite the clean fast-forward — see §6. Two findings (DB `ddl-auto`, cross-event `Team.isActive`) should be fixed *before* merging, not after. |

---

## 1. Executive Summary

Sprint 3 is the final sprint: it turns the season/event model from single-event-per-team into a genuine **multi-season platform**, migrates authentication to a hardened cookie-based scheme, and — in its last two PRs — pays down the god-class debt accumulated over the previous sprints. Headline outcomes:

1. **Team/TeamEventEntry split** — `Team` becomes a persistent identity (name, roster) while everything season-specific (track, approval status, disqualification) moves to a new `TeamEventEntry` row per `(team, event)`. This is what makes team **rejoin across seasons** possible, mirroring the `User`/`UserEventRole` split from Sprint 2.
2. **Code-first DB schema (Hibernate)** — replaces the hand-maintained `seal_schema.sql` rebuild strategy from Sprint 1/2. `@Entity` classes are now the single source of truth; `docker-compose.yml` no longer mounts any `.sql` file into `docker-entrypoint-initdb.d`.
3. **Auth hardening** — JWT moved from client-stored token to an **HttpOnly cookie** with **CSRF double-submit** protection; bearer-token API clients are still supported via an explicit CSRF exemption.
4. **Leftover-team grouping** — a real algorithm (`LeftoverGroupingPlanner`) that auto-groups teamless registrants and undersized teams into valid teams at SETUP time, plus a coordinator drag-and-drop UI to manually override the plan before committing.
5. **New participant lifecycle features** — team rejoin requests, participation access requests (admin-approved read access for otherwise-locked accounts), OTP-based password reset, and a denormalized `ParticipantEventHistory` snapshot so a participant's results survive leaving/losing their team.
6. **Mentor support workflow & scoring polish** — leaders can request mentor help in person; rankings redesigned into per-track, prize-highlighted cards; default scoring criteria template seeding.
7. **End-of-sprint refactor (PRs #386–#387)** — the two largest classes in the codebase, `AssignmentService` and `TeamService`, were split into focused services (`JudgeAssignmentService`/`MentorAssignmentService`, `TeamQueryService`/`TeamModerationService`/`TeamMembershipService`/`TeamTrackAssignmentService`/`TeamAccessGuard`/`TeamResponseMapper`), and the frontend's `apiClient.ts` and `CoordEventsPage` were similarly decomposed.

**Why this merge needs more scrutiny than Sprint 2's:** Sprint 2's report could call its fast-forward "technically low-risk" because the DB was still rebuilt from a reviewed SQL file every time. Sprint 3 removes that safety net (Hibernate now applies schema changes live via `ddl-auto=update`, on every boot, in every environment including the prod `docker-compose.yml` target) at the same time as it changes the core season/team data model. Two findings below (§6, #1 and #2) are consequences of exactly that combination and are worth fixing pre-merge rather than post-merge.

---

## 2. Sprint 3 Scope (Feature Map)

| # | Feature area | Backend | Frontend | DB |
|---|---|---|---|---|
| 1 | Team/event multi-season split | `TeamEventEntry` entity, `TeamTrackAssignmentService`, rewritten `TeamService` → `TeamQueryService`/`TeamModerationService`/`TeamMembershipService` | Team workspace redesign, dormant-team awareness | `TeamEventEntry` (new), `Team` (event/track/status columns removed → `isActive` only) |
| 2 | Team rejoin | `TeamRejoinRequestController/Service` | "request rejoin" UI for a dormant team's leader | `TeamRejoinRequest` (new) |
| 3 | Participant history | `ParticipantHistoryController`, `ParticipantHistorySnapshotService` | read-only per-entry history view | `ParticipantEventHistory` (new, denormalized snapshot) |
| 4 | Participation access requests | `ParticipationAccessRequestController/Service` | admin queue to grant read access | `ParticipationAccessRequest` (new) |
| 5 | Password reset (OTP) | `PasswordResetService`, `AuthController` additions | reset-password flow screens | `PasswordResetOtp` (new) |
| 6 | Leftover-team grouping | `LeftoverGroupingService` + `service/grouping/*` (`Atom`, `GroupingPlan`, `LeftoverGroupingPlanner`, `ProposedTeam`, `SettledTeam`, `GroupingWarning`) | drag-and-drop Proposed Teams UI, manual leftover-assign | uses existing `Team`/`TeamMember`/`TeamEventEntry` — no new tables |
| 7 | Mentor support | `MentorSupportRequestController/Service`, `SupportRequestController` | leader "request help" button, mentor resolve-in-person UI | `MentorSupportRequest` (new) |
| 8 | Auth hardening | `CsrfController`, cookie-based JWT filter, CSRF double-submit filter | cookie-based session in `AuthProvider`, CSRF-aware mutations | — |
| 9 | Code-first schema | — | — | `ddl-auto=update`, `seal_schema.sql`/`seal_seed.sql` retired, gated `DemoSeeder` (S1/S2.5/S3 scenarios) |
| 10 | Assignment/Team god-class split | `JudgeAssignmentService`, `MentorAssignmentService`, `AssignableStaffService`, `TeamAccessGuard`, `TeamResponseMapper` | `apiClient.ts` split into per-domain modules, `CoordEventsPage` split into tab components | — |
| 11 | Scoring/ranking polish | `JudgeScoringCompletenessService`, default criteria template seed | per-track ranking cards, prize-based highlighting | — |
| 12 | Misc UX | event `topic` field, navbar role countdown timer, university autocomplete, student-ID uniqueness check | `NavbarRoleTimer`, `PixelMenu`, `UniversitySelect`, dashboard redesigns | `HackathonEvent.topic` (new column) |

---

## 3. Database & Migration

### 3.1 Strategy shift (the biggest operational change this sprint)

Sprint 1/2 owned the schema through two long-lived, hand-maintained files (`seal_schema.sql`, `seal_seed.sql`), rebuilt on every change — every schema edit was a reviewed diff to a SQL file. Sprint 3 (commit `e2dbbe8`) replaces this with **Hibernate code-first schema management**:

```diff
# application.properties
- spring.jpa.hibernate.ddl-auto=none
+ spring.jpa.hibernate.ddl-auto=update
```

```diff
# docker-compose.yml (mysql-db service)
- - "./back-end/database scripts/seal_schema.sql:/docker-entrypoint-initdb.d/01_seal_schema.sql:ro"
- - "./back-end/database scripts/seal_seed.sql:/docker-entrypoint-initdb.d/02_seal_seed.sql:ro"
- - "./back-end/database scripts/seal_scripts.sql:/docker-entrypoint-initdb.d/03_seal_scripts.sql:ro"
+ # Code-first: Hibernate (ddl-auto=update) generates the schema from entities;
+ # DataSeeder / DemoSeeder load data — seal_*.sql is no longer mounted.
```

Data seeding is now handled in code: essential roles + a bootstrap admin always seed on startup; an optional `app.seed.scenario` (`NONE|S1|S25|S3`, env `SEED_SCENARIO`) gates demo data for local/demo environments only.

**This is a net simplification for dev velocity (no more manually keeping SQL and entities in sync) but it removes the reviewed-diff safety net for every environment, including the one `docker-compose.yml` deploys to production.** See finding §6.1 for why this needs a guard before merging.

### 3.2 Structural changes vs `main`

| Table | Change | Type |
|---|---|---|
| `TeamEventEntry` | **New.** One row per `(team, event)`: `status`, `track_id`, `disqualified_reason/at`. `UNIQUE(team_id, event_id)`. | New |
| `Team` | Dropped `event_id` (FK, was `NOT NULL`), `track_id`, `status`, `disqualified_reason`, `disqualified_at` — all moved to `TeamEventEntry`. Added `is_active` (mirrors `User.isActive`). | Breaking (moved to new table) |
| `TeamRejoinRequest` | **New.** Leader-initiated request for a dormant team to rejoin a new season. No DB uniqueness on `(team, status)` — see finding §6.5. | New |
| `ParticipantEventHistory` | **New.** Denormalized snapshot of a participant's result per event so history survives leaving/losing a team. `UNIQUE(user_id, event_id)`, no live FK to `Team`. | New |
| `ParticipationAccessRequest` | **New.** Admin-approved read-access grant for inactive/locked participants. | New |
| `PasswordResetOtp` | **New.** OTP + expiry for the self-service password reset flow (`app.password-reset.otp-expiration-minutes`, default 10 min). | New |
| `MentorSupportRequest` | **New.** Team-leader → mentor help request/resolution record. | New |
| `JoinRequest` | `+event_id` (`NOT NULL`) — pins the request to the season it was created in, since `Team` no longer carries a single event. | Additive (required by the Team split) |
| `TeamInvite` | `+event_id` (`NOT NULL`), same reason as `JoinRequest`. | Additive |
| `HackathonEvent` | `+topic` (nullable, 500 char) — event-wide theme, admin-configurable. | Additive |
| `Notification` | `+sender_user_id` (FK, nullable), `+sender_role`, `+scope_label` — lets non-announcement notifications (e.g. mentor support requests) show a "From" and detail popup. | Additive |
| `RoundTimer` | `round_id` FK gained `ON DELETE CASCADE` (was previously enforced only in app code). | Relaxing (DB now matches app-level cascade) |
| `User` | `isActive` semantics documented/reused: `false` now means "read-only" for a participant, not just "deactivated/rejected" (overloads the same flag for a new meaning). | Semantic-only |
| `Prize` | Doc-comment/import cleanup only, no column change. | Cosmetic |

### 3.3 Deploy config changes

- `docker-compose.yml`: prod backend service now sets `JWT_COOKIE_SECURE=true` (Caddy fronts the stack with HTTPS, so the auth/CSRF cookies can require Secure) and adds a commented `SEED_SCENARIO` line for demo deployments.
- `spring.datasource.url` gained `createDatabaseIfNotExist=true&characterEncoding=UTF-8` — needed because Hibernate no longer relies on the SQL init scripts to create the database first.

---

## 4. Backend API

### 4.1 New controllers

| Controller | Purpose | Authority |
|---|---|---|
| `CsrfController` | Issues/refreshes the CSRF cookie for the double-submit pattern | any |
| `TeamRejoinRequestController` | Leader requests rejoin, coordinator approves/rejects | PARTICIPANT (leader) / COORDINATOR |
| `ParticipantHistoryController` | Read-only per-event history for a participant | PARTICIPANT (own), COORDINATOR |
| `ParticipationAccessRequestController` | Request/approve read access for locked accounts | PARTICIPANT / ADMIN |
| `MentorSupportRequestController`, `SupportRequestController` | Leader requests help, mentor resolves in person | PARTICIPANT (leader) / MENTOR |

`TeamController` grew the most in this sprint (+173/−? lines) as it absorbed the season-scoped roster/track endpoints that moved with the `Team`→`TeamEventEntry` split; `AuthController` grew (+92) for the cookie/CSRF/OTP flows.

### 4.2 Auth model change

- JWT is now issued as an **HttpOnly cookie** (`a1c98aa`) instead of returned to client JS; the frontend no longer stores a bearer token itself for browser sessions.
- CSRF protection re-enabled via **double-submit cookie** (`a7db020`); bearer-token requests (e.g. Postman, service-to-service) are explicitly exempted by a narrowly-scoped `bearerAuthRequestMatcher` that only matches requests actually carrying `Authorization: Bearer` — confirmed not overly broad.
- `/api/auth/**` returns 401 instead of 500 for unauthenticated calls (`0296c4e`); logout/expiry no longer bypassed by a stale session cookie (`a2314f0`).

### 4.3 Service-layer refactor (PRs #386/#387)

`AssignmentService` and `TeamService` had grown into god-classes over three sprints. This sprint's closing PRs split them:

- `AssignmentService` → `JudgeAssignmentService`, `MentorAssignmentService`, `AssignableStaffService`
- `TeamService` → `TeamQueryService`, `TeamModerationService`, `TeamMembershipService`, `TeamTrackAssignmentService`, `TeamAccessGuard`, `TeamResponseMapper`

Verified: no leftover references to the deleted `AssignmentService.java`/`TeamService.java`, controllers now depend on the narrower services directly.

---

## 5. Frontend

- **`apiClient.ts` split** into per-domain modules (mirrors the Sprint 2 "17 API groups" structure, now physically separated instead of one file).
- **`CoordEventsPage` split** into per-tab components — was one of the largest FE files.
- **New shared components**: `PixelMenu` (+ tests), `NavbarRoleTimer` (+ `useRoundTimer` tests), `TeamDetailModal`, `UniversitySelect`, `MemberTextList`.
- **Dashboard redesigns**: coordinator console (event-scoped KPIs, needs-attention zone), participant no-team/existing-team dashboards (dark-mode, track picker modal), landing page scroll animations.
- **Test coverage added late in the sprint**: `providers`, `eventUtils`, `useRoundTimer`, `PixelMenu` unit tests (commit `de62fab`) — a direct response to the god-class split needing regression coverage.

---

## 6. Risks, Findings & Recommendations

Unlike Sprint 2 (clean fast-forward *and* low operational risk because the DB was rebuilt from reviewed SQL), Sprint 3's fast-forward is clean but two findings below are real regressions worth fixing **before** merging to `main`, since this is the last sprint and there's no Sprint 4 to catch them in.

| # | Severity | Finding | Recommended fix |
|---|---|---|---|
| 1 | **High** — **Fixed** | `spring.jpa.hibernate.ddl-auto=update` (`application.properties:23`) was unconditional — no dev/prod profile split. `docker-compose.yml`'s prod-facing backend service used the same value. Every boot silently applied whatever schema the current `@Entity` classes implied, live, with no review or rollback step. | Fixed: `spring.jpa.hibernate.ddl-auto=${DDL_AUTO:update}` — default behavior unchanged (still `update` everywhere, so local dev and the demo-scenario workflow on the deployed server are unaffected). `docker-compose.yml` gained a commented-out `# - DDL_AUTO=validate` switch on the `seal-backend` service, ready to enable once the deployment holds data that must not be auto-altered on redeploy (e.g. after demo scenarios are retired in favor of a real live event). No bootstrap-step change needed since the default didn't change. |
| 2 | **Medium-High** — **Fixed** | `HackathonEventService.lockCompletedEventParticipantsReadOnly` deactivated `Team.isActive` for **every** team in the completed event unconditionally, while the parallel `User` deactivation correctly excluded users via `hasNonCompletedMembership`. Note: event date ranges can't overlap (`validateNoOverlappingActiveEvent`), so the trigger isn't two events literally running at once — it's status/date decoupling (`status-driven, not date-driven` lifecycle): a next season can already be opened (non-`COMPLETED` status) before an admin gets around to clicking "Complete" on the previous one. A team with a live entry in that next season got wrongly deactivated when the old event was finally completed, which then let it slip past `TeamRejoinRequestService`'s `team.getIsActive()` guard — a workflow meant only for teams that are truly done for the season. | Fixed: added `hasNonCompletedEntryElsewhere(Team, completedEventId)` (`HackathonEventService.java`, mirrors `hasNonCompletedMembership`) and filtered the team-deactivation list with it before flipping `isActive`. Verified: compiles, all 24 existing `HackathonEventServiceTest` cases still pass. |
| 3 | Medium | `LeftoverGroupingService.commit`/`manualAssign`/`applyPlan` (lines 99, 156, 202, all plain `@Transactional`, default `READ_COMMITTED`, no locking) read the free-agent/undersized-team pool and write a new grouping in the same transaction with no pessimistic lock. Two near-simultaneous coordinator actions (double-submit, or two coordinators) can both read the same free agent as available and place them on two different teams; `TeamMember`'s only constraint (`uq_team_member_team_user` on `team_id, user_id`) does not prevent one user landing on two different teams for the same event. | Cheapest pre-merge mitigation: make the read step use a pessimistic write lock on the involved `TeamMember`/`TeamEventEntry` rows (`@Lock(LockModeType.PESSIMISTIC_WRITE)` on the repository query, or wrap in `SELECT ... FOR UPDATE`). A real fix — add a DB-level `UNIQUE(event_id, user_id)` (requires denormalizing `event_id` onto `TeamMember`, similar to what was just done for `JoinRequest`/`TeamInvite` in this same sprint) — is a bigger change; acceptable as a fast-follow immediately after merge rather than blocking it, since this is a low-frequency coordinator-only action. |
| 4 | Low-Medium | `LeftoverGroupingService.java:147-153` — self-documented in a comment: a manually force-approved 1-2 person team looks identical to an organic leftover atom to `buildContext`, so re-running `commit`/`preview` after a manual override can sweep it back into automatic grouping and silently undo the coordinator's edit. | Add a `manuallyFinalized` boolean (on `TeamEventEntry` or tracked via the grouping plan) so `buildContext` can exclude manually-settled teams from re-sweep. Track as a fast-follow; low blast radius (coordinator-visible, re-doable) but worth fixing before it bites during a live SETUP session. |
| 5 | Low | `TeamRejoinRequestService.requestRejoin` (lines 56-71) does check-then-insert (`findByTeam_TeamIdAndStatus` → `orElseGet(...save(...))`) with no DB uniqueness on `TeamRejoinRequest` and no lock — a double-click from the leader can insert two PENDING rows for the same team. Low impact: `TeamEventEntry`'s `uq_tee_team_event` constraint would reject a second actual approval, but the duplicate PENDING row lingers and clutters the coordinator's queue. | Add `@UniqueConstraint(columnNames = {"team_id", "status"})`... MySQL doesn't support partial-unique-on-value directly, so simplest fix is a `synchronized`/pessimistic-lock guard in the service, or reuse-and-update the existing PENDING row instead of `orElseGet(...save(...))` when one is found (which the current code already does correctly for the *read* path — just needs the write path serialized). Low priority; fine as a fast-follow. |
| 6 | Low | `SecurityConfig.java:141` and `:160` both declare `.requestMatchers("/api/join-requests/**").hasRole("PARTICIPANT")` — identical duplicate rule under two different section comments, harmless (first match wins) but dead, likely left over from the CSRF/cookie migration merge. | Delete one of the two lines. Trivial, do it in the same PR as finding #1/#2 fixes. |
| 7 | Info | `/api/events/**` is `permitAll()` at the URL-matcher layer (`SecurityConfig.java:131`); every actual write endpoint under it (`HackathonEventController`, `TrackController`, `RoundController`, `PrizeController`, `RoundResultController`, `RoundTimerController`, `TrackProblemController`) does carry an explicit `@PreAuthorize`, confirmed — no active hole today. | No fix needed now. Structural note only: any *new* `/api/events/**` write endpoint added without an explicit `@PreAuthorize` defaults to public. Worth a one-line reminder in the controller package's contribution notes. |
| 8 | Info | `User.isActive`'s meaning was silently overloaded this sprint (was "deactivated/rejected", now also means "participant read-only lock"), per the updated doc-comment in `User.java`. Not a bug — just flagged because two different concepts now share one boolean, which is exactly the kind of ambiguity that produced finding #2 on the `Team` side. | No fix required pre-merge; consider renaming or splitting the flag in a future sprint if a third meaning ever gets added to the same field. |

**Categories checked and found clean:** the `AssignmentService`/`TeamService`/`apiClient.ts`/`CoordEventsPage` splits have no leftover references to the deleted originals; CSRF bearer-token exemption is narrowly scoped; no `TODO`/`FIXME`/`HACK` markers anywhere in `back-end/src` or `front-end/src`; `ParticipantEventHistory` has no live FK to `Team`/`User` so the cascade-delete risk that pattern usually implies doesn't apply (and no hard-delete path exists for either entity anyway); `.env` is gitignored and not tracked.

**Security posture:** sound overall — the auth hardening this sprint (HttpOnly cookie, CSRF double-submit, 401-not-500 on unauthenticated calls) is a net improvement over Sprint 2. The findings above are data-integrity/operational risks, not auth bypasses.

---

## 7. Pre-Merge Checklist

- [x] Fix finding #1 (`ddl-auto` prod guard) — done, `DDL_AUTO` env var added (default unchanged, opt-in `validate` switch in `docker-compose.yml`).
- [x] Fix finding #2 (`Team.isActive` cross-event blindness) — done, `hasNonCompletedEntryElsewhere` guard added, plus two regression tests in `HackathonEventServiceTest` (`completeEvent_shouldDeactivateTeam_whenTeamHasNoEntryInAnotherNonCompletedEvent`, `completeEvent_shouldKeepTeamActive_whenTeamHasEntryInAnotherNonCompletedEvent`) — verified the second test fails without the fix.
- [ ] Decide whether findings #3–#6 are merge-blockers or fast-follows (recommendation above: fast-follow, they're low-frequency/coordinator-only and not data-loss risks).
- [ ] `develop` builds and tests pass (backend + frontend) — 20 backend test files touched this sprint including new `TeamRejoinRequestServiceTest`, `TeamTrackAssignmentServiceTest`, `LeftoverGroupingPlannerTest`.
- [ ] Fresh DB boots cleanly from entities alone (drop DB, start backend with `ddl-auto=update`, confirm essential roles + bootstrap admin seed).
- [ ] `JWT_COOKIE_SECURE=true` and HTTPS (Caddy) confirmed on the target prod environment — a `Secure` cookie silently fails to set over plain HTTP.
- [ ] `SEED_SCENARIO` left unset (`NONE`) for the prod deploy.
- [ ] Merge `develop` → `main` as a fast-forward (or via PR), attach/link this report in the PR description.

### Suggested merge commands

```bash
# Fast-forward (main becomes identical to develop)
git checkout main
git pull origin main
git merge --ff-only develop
git push origin main
```

If `main` is branch-protected, open a PR (`develop` → `main`) and merge via GitHub instead.
