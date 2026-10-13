# AI Work Log - 2026-07-05 - Participant Dashboard, Team History, Invite/Join UX

## Session Scope

This session focused on participant-facing hackathon workflows in the SEAL Hackathon system, especially:

- Understanding project context before editing.
- Preserving correct behavior for "view history" and old competitions.
- Refining the no-team participant dashboard during open registration.
- Refining My Team for both leader and member roles.
- Redesigning team invitations / join request discovery.
- Restricting leaderboard visibility based on participant access.
- Fixing stale team/session context issues, especially after Google OAuth accounts are invited, removed, or accepted into teams.

Primary frontend app:

- `front-end/src/seal-web`

Primary files touched:

- `front-end/src/seal-web/src/features/dashboard/dashboards/participant/screens/NoTeamDashboard.tsx`
- `front-end/src/seal-web/src/features/dashboard/dashboards/participant/components/InvitationsDrawer.tsx`
- `front-end/src/seal-web/src/features/dashboard/dashboards/participant/screens/ExistingTeamDashboard.tsx`
- `front-end/src/seal-web/src/features/dashboard/dashboards/ParticipantDashboard.tsx`
- `front-end/src/seal-web/src/features/teams/TeamViewPage.tsx`
- `front-end/src/seal-web/src/features/scoring/LeaderboardPage.tsx`
- `front-end/src/seal-web/src/features/submissions/TeamSubmitPage.tsx`
- `front-end/src/seal-web/src/app/providers/AuthProvider.tsx`
- `front-end/src/seal-web/src/features/auth/OAuth2RedirectPage.tsx`
- `front-end/src/seal-web/src/shared/apiClient.ts`

## Initial Context Gathering

Before starting UI work, project context was gathered from:

- `back-end/Postman/Postman_Full_Collection.json`
- `docs/documents/ProjectRequirements.md`

Note: the user initially referenced `docs/ProjectRequirements.md`, but the actual project file was under `docs/documents/ProjectRequirements.md`.

Important context identified:

- Participant history is served by `GET /api/teams/my/result-history`.
- Frontend participant history page is `features/teams/HistoryPage.tsx`.
- Backend participant history logic is handled by `TeamService.getMyResultHistory`.
- Mentor history is served by `GET /api/mentor/assignments/history`.
- Frontend mentor history page is `features/tracks/MentorHistoryPage.tsx`.
- Judge history appeared to be frontend-composed rather than backed by a dedicated history endpoint.

Key domain observation:

- Historical participation should remain available through History rather than by switching current My Team state between events.
- Draft events must not be visible to ordinary participants. Coordinators/admins may still see draft/internal states where relevant.

## No-Team Dashboard Changes

File:

- `front-end/src/seal-web/src/features/dashboard/dashboards/participant/screens/NoTeamDashboard.tsx`

### Removed Redundant Join Component

Problem:

- The no-team dashboard showed both a "Join an Event" block and an "Open Events" block.
- "Join an Event" remained visible even when event status changed to states like `SETUP` or `IN_PROGRESS`.
- This was incorrect because the real business workflow should be driven by the open event card and the event lifecycle.

Change:

- Removed the redundant "Join an Event" component.
- Kept the actionable open event card as the main entry point.

### Moved Wait For Invite

Problem:

- `WAIT FOR INVITE` was placed inside the redundant Join component.

Change:

- Moved `WAIT FOR INVITE` into the open event card action row, next to:
  - `REGISTER & CREATE TEAM`
  - `VIEW DETAILS`

Result:

- Users now see all relevant event actions in one place.

### How It Works Button

Problem:

- The previous "How it works" text was too prominent in the flow and later the icon became too subtle.

Changes:

- Reduced "How it works" to a fixed `?` button.
- Positioned it fixed at bottom-right.
- Increased visual prominence using a green/blue gradient, stronger shadow, circular shape, hover transform, and tooltip.

### Open Events Refinement

Problem:

- The open event card was too small and not visually clear enough for open registration.

Changes:

- Enlarged the event card.
- Added a visible registration strip:
  - `REGISTRATION OPEN`
  - remaining days until registration deadline
  - deadline text
- Remaining days are computed from `registrationEnd`, falling back to `startDate` if needed.

Important clarification:

- The days-left value is not fake data. It is computed from event date fields returned by the API.

### Track And Round Expanders

Problem:

- Tracks and rounds were not visually intuitive.
- The first version showed all track names inline, which felt crowded.

Changes:

- Converted Tracks and Rounds into clickable expandable cards.
- Added smooth expand/collapse animation using max-height, opacity, and transform.
- Removed helper copy such as "Choose one when creating a team".
- Removed ordinal labels like `#1 #2 #3 #4`.
- Later removed visible toggle glyphs (`>`, `v`) based on user feedback.

Track detail behavior:

- Track cards show name and a brief description.
- If backend provides `track.description`, it is used.
- Otherwise a fallback description is generated based on track name:
  - AI
  - Green Tech
  - Social Impact
  - Web Application
  - generic fallback

Round detail behavior:

- Round list shows:
  - round name
  - round status
  - submission deadline

Important business clarification:

- Round display uses status returned from the rounds API.
- The UI does not derive round status from dates.
- This matches the current system behavior where events and rounds can be manually edited by status.

## Leaderboard Visibility Discussion And Implementation

File:

- `front-end/src/seal-web/src/features/scoring/LeaderboardPage.tsx`

### Business Discussion Outcome

The user clarified:

- Public showcase is valid for old completed seasons.
- Participants should be able to see results for old events that are published/showcase-ready.
- Draft events must not show to ordinary participants.
- Coordinators can see more internal states.
- The user did not want to expose all system events to ordinary users.

### Implemented Filtering

Implemented participant-safe frontend filtering:

- Coordinators/admins retain broader visibility.
- Non-coordinator/admin users:
  - cannot see draft events
  - can see completed public showcase events if published results exist
  - can see events they participated in
  - round dropdown only includes rounds with published results

Published result validation:

- Uses `resultsApi.getPublished`.
- Does not show unpublished result data to participants.

Residual risk:

- This is frontend filtering only. True security for event/leaderboard visibility should also be enforced on the backend if sensitive unpublished data exists behind accessible APIs.

## My Team Redesign

File:

- `front-end/src/seal-web/src/features/teams/TeamViewPage.tsx`

### Removed Event Dropdown

Problem:

- My Team had an event dropdown that did not work well.
- It implied users could switch current team view between old events.
- This conflicted with the History concept.

Decision:

- Remove event dropdown from My Team.
- Treat My Team as current participation only.
- Historical team details should live in History.

Change:

- Replaced dropdown with a current event/team header:
  - `Current Event`
  - event name
  - event status badge
  - team name
  - team status badge
  - edit team name when allowed
  - view history button

### Removed Duplicate Event Cell

Problem:

- Event name was shown both in the top header and in the info card.

Change:

- Removed Event from the info card.
- Info card now focuses on:
  - Track
  - Current round
  - Members
  - Your role

### Two-Column Layout

Problem:

- Wide full-width rows looked too stretched.
- A right-side utility panel was initially too empty for regular members.

Design direction:

- Use a two-column layout:
  - left column: current team summary and member roster
  - right column: contextual panel

Right panel behavior:

- Defaults to `Member Info`.
- No separate `Clear` button.
- Clicking a member row keeps `Member Info` active and updates details.
- Selected member row glows.
- Leader-only actions can switch right panel to:
  - Invite Member
  - Join Requests

### Member Info Panel

Added member detail display in the right column:

- member name
- role badge
- email
- student ID
- student type
- joined at

API type extended:

File:

- `front-end/src/seal-web/src/shared/apiClient.ts`

`MyTeamMember` now supports optional fields:

- `email`
- `studentType`
- `studentId`
- `joinedAt`

### Member Row Actions

Problem:

- Inline `TRANSFER LEAD` and `REMOVE` actions made rows noisy.
- Menu could be clipped due to fixed row/card height.

Changes:

- Replaced inline action buttons with `PixelMenu`.
- Actions are hidden until row hover.
- Menu trigger uses existing PixelMenu icon style.
- Menu is portaled, preventing clipping by table/card overflow.
- Actions available:
  - Transfer lead
  - Remove

### Invite Member And Join Requests Panel

Problem:

- Invite and join-request workflows were originally separate sections or full-width blocks.
- User preferred actions beside Members with forms appearing in the right column.

Changes:

- Added `INVITE MEMBER` button in Members card header for leaders.
- Added `JOIN REQUESTS` button in Members card header for leaders.
- Clicking either button changes the right panel:
  - `Invite Member` panel
  - `Join Requests` panel

Invite flow:

- Search by name, email, or student ID.
- Search result card shows participant name/email/student ID.
- `INVITE` opens a confirmation dialog.
- Confirming sends invite via API.
- Success and failure toasts are shown through the existing notification system.

Join request flow:

- Shows pending requests in the right panel.
- Leader can accept/decline.
- Existing locks remain respected:
  - read-only account
  - non-editable event phase
  - team full

### Leave Team Button For Participant Members

Problem:

- `LEAVE TEAM` was gated by `canEditTeam`, which only leaders have.
- Regular participant members could not see the leave button.

Fix:

- Added `canLeaveTeam = !readOnly && editable`.
- Leave button now appears for regular members when the team/event phase allows leaving.

Leader rule retained:

- A leader with more than one member must transfer leadership before leaving.

## Team Invitations Drawer Redesign

File:

- `front-end/src/seal-web/src/features/dashboard/dashboards/participant/components/InvitationsDrawer.tsx`

### Initial Problems

- Drawer was visually dense and repetitive.
- Team cards repeated the same action/info too much.
- User had to search with a blank query to load all teams.
- Team ordering did not prioritize teams most in need of members.
- Drawer open/close had no slide animation.
- Drawer was too narrow.

### Auto-Load Joinable Teams

Change:

- Drawer now loads joinable teams immediately when opened.
- Blank query is no longer required.
- Search remains manual to avoid spamming API on every keystroke.

### Sorting

Change:

- Joinable teams are sorted by missing member count descending.
- Example:
  - `2/5` appears before `3/5`
  - teams missing more members are pushed up

Tie-breakers:

- lower `memberCount`
- team name alphabetical

### Accordion Record UI

After several iterations, final behavior:

- Each team is a compact row.
- Compact row shows:
  - team name
  - member count badge such as `2/5`, `3/5`, `4/5`
- The old `NEEDS X` text was removed.
- Badge color remains based on missing seat severity.
- Clicking a row expands that exact row inline.
- Clicking it again collapses it.
- Expanded detail includes:
  - member progress bar
  - event
  - track
  - leader
  - status
  - `REQUEST TO JOIN` button

Removed based on feedback:

- row-level repeated metadata under team name
- per-row repeated request button in compact view
- visible expand glyphs (`>`, `v`)

### Drawer Animation And Width

Changes:

- Drawer width increased from `520px` to `600px`.
- Added enter/exit animation:
  - drawer slides in from the right
  - drawer slides out to the right
  - backdrop fades in/out
- Implemented internal `entered` / `closing` state and delayed unmount via timeout.

### Accept Invite Confirmation

Problem:

- Accepting an invite happened immediately.

Change:

- Added shared `ConfirmDialog` before accepting an invite.
- Confirm message includes:
  - team name
  - inviter name
  - event / track
- Success/failure still uses global toast notification.

## Confirmation And Notification Flow

Shared components used:

- `ConfirmDialog`
- `useNotifications().addToast`

Implemented confirmations:

1. Leader sending invite to member
   - Button: `INVITE`
   - Opens `ConfirmDialog`
   - Confirm label: `SEND INVITE`
   - After success: toast `Invitation sent`

2. Participant accepting team invite
   - Button: `ACCEPT INVITE`
   - Opens `ConfirmDialog`
   - Confirm label: `ACCEPT INVITE`
   - After success: toast `Invite accepted`

Existing toasts kept:

- invite success
- invite failure
- accept invite success
- accept invite failure
- request to join success/failure
- member removal success/failure
- leadership transfer success/failure

## OAuth / Team Context Stability Fix

Files:

- `front-end/src/seal-web/src/app/providers/AuthProvider.tsx`
- `front-end/src/seal-web/src/features/auth/OAuth2RedirectPage.tsx`
- `front-end/src/seal-web/src/features/dashboard/dashboards/participant/screens/ExistingTeamDashboard.tsx`
- `front-end/src/seal-web/src/features/teams/TeamViewPage.tsx`
- `front-end/src/seal-web/src/features/submissions/TeamSubmitPage.tsx`

### Observed Issue

User reported:

- With Google accounts, after invite/remove operations, login sometimes appeared broken.
- User sometimes had to wait and click login again.

Analysis:

- This looked like stale frontend team/session context rather than Google API failure.
- `team_id` is not reliably provided by `/api/auth/me`.
- Frontend derives team context via `/api/teams/my`.
- When a member is removed from a team in another session, their local frontend may still hold old `team_id`.
- OAuth login also reused stored `activeRole`, which could bleed across sessions.

### AuthProvider Fix

Problem:

- Team leadership inference used `fullName` fallback.
- OAuth names can vary, and duplicate names are possible.

Change:

- `fetchTeamContext` now accepts `userId`.
- Role inference priority:
  1. `myRole` from API
  2. matching team member by `userId`
  3. fallback matching by `memberName`

This makes Google/OAuth accounts less fragile.

### OAuth Redirect Fix

Problem:

- OAuth redirect set new token without clearing old token/role state first.

Change:

- Before storing new OAuth token:
  - `clearToken()`
  - remove `activeRole` from `localStorage`
  - remove `activeRole` from `sessionStorage`

This reduces stale role/session bleed when switching accounts or re-authenticating through Google.

### Clear Team Context On 404

Problem:

- If backend returned `404` for current team, UI sometimes stayed in a stale team route.

Changes:

- Existing participant team dashboard:
  - if `/api/teams/my` returns `404`, call `clearTeam()`
  - show toast: team context updated

- My Team page:
  - if `teamsApi.getMy()` returns `404`, call `clearTeam()`

- Submit Project page:
  - if team history/current team returns `404`, call `clearTeam()`

Result:

- Removed users should be moved back to no-team state without having to re-login.

## Build Verification

Command run repeatedly after meaningful changes:

```bash
npm run build
```

Working directory:

```text
front-end/src/seal-web
```

Final result:

- Build passed.
- Vite continued to report large chunk warnings, which already existed and were not introduced by this task.

Example warning category:

- chunks larger than 500 kB after minification

No blocking TypeScript or Vite build errors remained.

## Notable UX Decisions

### Current Team vs History

Decision:

- My Team should represent the active/current team only.
- Old team/event details belong in History.

Reason:

- Letting users switch My Team across old events conflates current operational workflows with historical records.

### Leader vs Member Layout

Decision:

- Preserve a two-column design for My Team.
- Left column remains roster and team facts.
- Right column is contextual:
  - member info by default
  - invite form for leaders
  - join request panel for leaders

Reason:

- Full-width member records looked too stretched.
- A right panel only makes sense if it always has useful role-appropriate content.

### Team Discovery Drawer

Decision:

- Use inline accordion rows instead of a separate detail panel.

Reason:

- The user wanted details to expand directly from the clicked record.
- This reduced repeated components and made the interaction more direct.

### Leaderboard Exposure

Decision:

- Participants should not see all events blindly.
- Old completed/public events may be shown if results are published.
- Draft events are hidden from ordinary participants.

Reason:

- Balances public showcase with access control expectations.

## Files Changed Summary

### `NoTeamDashboard.tsx`

- Removed redundant Join an Event component.
- Moved Wait For Invite into event action row.
- Added prominent fixed help button.
- Enlarged open event card.
- Added registration deadline display.
- Added expandable Tracks and Rounds.
- Added track descriptions.
- Removed toggle glyphs.

### `InvitationsDrawer.tsx`

- Added auto-load all joinable teams on open.
- Sorted joinable teams by missing seats.
- Added accordion-style team rows.
- Removed repeated row metadata.
- Changed badge from `NEEDS X` to `memberCount/MAX_TEAM_SIZE`.
- Added drawer slide/fade animation.
- Increased drawer width to `600px`.
- Added accept invite confirmation dialog.
- Kept toast notifications.

### `TeamViewPage.tsx`

- Removed event dropdown.
- Added current event/team header.
- Removed duplicate Event info card field.
- Added two-column layout.
- Added member info right panel.
- Added Invite Member and Join Requests buttons in Members header.
- Added right-panel invite search/results.
- Added right-panel join request review.
- Replaced member row actions with hover PixelMenu.
- Added row selection glow.
- Added invite confirmation dialog.
- Made Leave Team visible to regular members.
- Clear stale team context on 404.

### `LeaderboardPage.tsx`

- Added participant-safe event filtering.
- Hid drafts for non-coordinator/admin users.
- Limited non-admin view to joined events and completed/published showcase events.
- Limited round dropdown to rounds with published results.

### `apiClient.ts`

- Extended `MyTeamMember`:
  - `email`
  - `studentType`
  - `studentId`
  - `joinedAt`

### `AuthProvider.tsx`

- Made team context inference use `userId` before name fallback.
- Refresh team context now passes `currentUser.user_id`.

### `OAuth2RedirectPage.tsx`

- Clears previous token and active role before setting OAuth token.

### `ExistingTeamDashboard.tsx`

- Clears team context when backend says current team no longer exists.

### `TeamSubmitPage.tsx`

- Clears team context when team history/current team is no longer available.

## Known Residual Risks / Follow-Up Suggestions

1. Backend access control for leaderboard

Frontend filtering now matches expected UX, but if unpublished/draft data is sensitive, backend endpoints should enforce the same policy.

2. Team context still depends on `/api/teams/my`

This is acceptable, but the best long-term backend contract would include current team context in `/api/auth/me` or provide a dedicated lightweight `/api/auth/context`.

3. OAuth flow still relies on full page redirect

The redirect now clears old state first. If issues persist, inspect browser devtools for:

- stale `Authorization` header
- old token in local/session storage
- OAuth callback errors
- backend security logs

4. History page remains the correct place for old event participation

If future UX needs old team details inside My Team, it should be read-only and clearly labeled as history, not mixed with current team management.

## Final State

The participant dashboard, My Team, invitation drawer, and leaderboard now better match the agreed business rules:

- current team management is separate from history
- no-team users see open registration clearly
- invitations and join requests are contextual and confirm before changing state
- participant team discovery is compact and expandable
- stale team context is cleared automatically when membership changes
- regular members can leave teams when allowed by event phase

