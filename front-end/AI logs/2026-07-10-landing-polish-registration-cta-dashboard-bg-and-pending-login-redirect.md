# AI Log — 2026-07-10

**Scope:** Front-end only (`front-end/src/seal-web`)
**Branch:** `develop`
**Model:** Claude Opus 4.8
**Verification for every change:** `npx tsc --noEmit -p tsconfig.app.json --ignoreDeprecations 6.0` → **exit 0** (no new errors; team builds with Vite, tsc needs the `ignoreDeprecations` flag for TS 6.0's `baseUrl` deprecation).

## Files touched
| File | Tasks |
|------|-------|
| `src/features/landing/LandingPage.tsx` | Tasks 1, 2 |
| `src/app/layouts/DashboardLayout.tsx` | Task 3 |
| `src/features/auth/LoginPage.tsx` | Task 4 |

---

## Task 1 — Landing page polish (4 fixes)

User reported, from a screenshot of the landing hero: a draggable horizontal bar at the page bottom, a scroll-progress strip above the header, an unfinished-looking terminal, and two hero buttons that drifted toward the cursor.

### 1.1 Horizontal overflow (draggable bottom scrollbar)
- **Root cause:** full-width sections wrapped in `<Reveal direction="left"/"right">` apply `transform: translateX(±44px)` before they scroll into view (`hiddenTransform`), pushing the document wider than the viewport → page-wide horizontal scrollbar.
- **Fix:** added `overflowX: "clip"` to the `LandingPage` root `<div>`. `clip` (not `hidden`) does not force the other axis to `auto`, so vertical scroll is untouched; the root has no `transform/filter/will-change`, so it is **not** a containing block for the `position: fixed` navbar/overlays — they stay anchored to the viewport and are not clipped.

### 1.2 Removed the scroll-progress bar
- Removed the `{!hideChrome && <ScrollProgressBar />}` render **and** the now-unused `ScrollProgressBar` component definition (the neon fixed strip at the top of the page).

### 1.3 Terminal "live-dashboard" ending
- The right-hand `TerminalWindow` ended on a bare blinking prompt (`→ █`) which looked unfinished.
- After a follow-up, the animated `TypingText` (`$ MANAGE_EVENTS.run()` … cycling) was **moved out of the hero's left column and into the terminal** as its final line: `→ $ <TypingText …>`. `TypingText` renders its own blinking cursor, so the standalone `cursor-blink` span was dropped. The left column now goes straight from the "Agile League" heading to the CTA buttons (the cycling typing effect now exists in exactly one place — the liveboard).

### 1.4 Static hero/CTA buttons
- `GET STARTED FREE` and `EVENT REGISTRATION` were wrapped in `<Magnetic>`, which pulled them toward the cursor on hover. `PixelButton` itself only changes glow/brightness on hover (no transform).
- **Fix:** removed the `<Magnetic>` wrappers in **both** the hero and the bottom CTA section, and deleted the unused `Magnetic` component. Buttons now stay put; hover glow retained.

---

## Task 2 — Event registration flow on the landing page

**Problem:** clicking `EVENT REGISTRATION` only scrolled to `#events`. The "Ongoing" event card (e.g. `SEAL Summer 2026 · OPEN`) was a pure display `<div>` with no interaction — a dead end. The user could see an OPEN event but had no way to proceed to registration.

**Context discovered:**
- Real "register for a hackathon" = create a team in an event. Entry points: `/dashboard` (`RoleDashboardPage` → `NoTeamDashboard` lists open events + create-team CTA) or the standalone `/team/create` (`TeamCreatePage`, which auto-selects the event when only one is open).
- The landing `navigate` prop (`routes/index.tsx`) only maps `auth/register/dashboard`, so `EventsSection` now uses `useNavigate()` + `useAuth()` directly.

**Iteration (Q&A with the user):**
1. First pass added a `REGISTER NOW` button **and** a click-to-open `EventDetailDrawer` (reused from `features/dashboard/.../participant/components/EventDetailDrawer.tsx`, which takes a `HackathonEvent` and fetches tracks/rounds via public endpoints) + a "View event details" hint.
2. User found that "cluttered" and asked to make the **whole card a single link** that redirects by role.

**Final design (shipped):**
- The ongoing card is a **single clickable link** while registration is OPEN (`role="button"`, `tabIndex`, Enter/Space key handling, `cursor: pointer`), with one subtle in-card CTA line `Register for this event →`.
- Routing (`goRegister`): **logged in → `/dashboard`** (`RoleDashboardPage` renders the correct dashboard per role); **not logged in → `/login`** (login links onward to register).
- Gating: card is a link only when `current.status === "OPEN"`; otherwise it is a static info card.
- Removed the `REGISTER NOW` button, the details hint, the whole `EventDetailDrawer` usage + its state + import — leaving a clean single-action card.
- Hero/CTA `EVENT REGISTRATION` buttons unchanged (still scroll to `#events`), per the user's choice.

```ts
// EventsSection
const registrationOpen = current?.status === "OPEN";
const { isAuthenticated } = useAuth();
function goRegister() {
  routerNavigate(isAuthenticated ? "/dashboard" : "/login");
}
```

---

## Task 3 — Dashboard background (sidebar + content)

**Problem:** the dashboard sidebar (`background: C.surface`) read as a flat, hard block ("1 cục cứng"). User wanted a look closer to the pending-approval page (grid + glow).

**Iteration (Q&A):**
1. Started with a subtle two-glow wash on the sidebar only (green at top, blue at bottom) — no grid, no particles, all in the `background` layer so it never overlays text and is fully static.
2. User asked to make it a bit stronger, apply it to the **content area** too, and **add a grid**.
3. That version (uniform grid + corner glows) looked "thô và khó nhìn" (rough). User noted the pending page is bright in the **center** and its grid **fades out** (isn't uniformly bright).

**Final technique (shipped) — applied to both the sidebar `<aside>` and the `<main>`:**
- A **same-colour radial vignette** layer (`transparent` in the centre → `${C.surface}` / `${C.bg}` at the rim) painted **on top of the grid lines**, so the grid is brightest in the middle and **dissolves toward the edges**.
- A **soft central spotlight** glow (green, near the upper-centre) for the "bright in the middle" feel.
- Faint grid lines (`~0.045–0.05` alpha, 28px cells).
- Everything lives in the element's own `background` (via `backgroundColor` + `backgroundImage` + `backgroundSize` longhands so the `background` shorthand doesn't reset the grid image). It stays under the content, never over text, and — being the element's background — stays fixed as `<main>` scrolls.
- `C.bg` / `C.surface` are CSS vars (`var(--c-bg)` / `var(--c-surface)`), so the vignette tracks the light/dark theme automatically.

Example (`<main>`):
```
backgroundColor: C.bg,
backgroundImage:
  radial-gradient(60% 50% at 50% 34%, rgba(34,197,94,0.06), transparent 72%),   // central spotlight
  radial-gradient(85% 75% at 50% 40%, transparent 42%, ${C.bg} 90%),            // vignette masks grid at edges
  linear-gradient(rgba(59,130,246,0.045) 1px, transparent 1px),                 // grid H
  linear-gradient(90deg, rgba(34,197,94,0.05) 1px, transparent 1px),            // grid V
backgroundSize: "100% 100%, 100% 100%, 28px 28px, 28px 28px",
```

**Dials left in comments:** vignette radius (`42%`/`90%`), grid alpha, cell size (`28px`), spotlight position/alpha.

---

## Task 4 — Login redirect for unapproved accounts

**Problem:** a newly-created account is pending coordinator approval. If the user closes the pending screen and logs in again while still unapproved, `LoginPage` showed a red error (`ERROR: Your account is awaiting coordinator approval…`) instead of the pending waiting page.

**Decision (Q&A):** redirect **silently** (no toast) — the `/pending-approval` page is itself a full explanation ("Awaiting Coordinator Approval / under review / notified by email"), so a toast would be redundant. Apply to **both** email/password and Google OAuth.

**Fix in `LoginPage.tsx`:**
- Email/password: `result === 'pending_approval'` → `navigate('/pending-approval')` instead of `setError(...)`.
- Google/OAuth: in the `?error=` effect, `ACCOUNT_NOT_APPROVED` → clear the query param then `navigate('/pending-approval')`; all other OAuth errors still render inline as before.
- `/pending-approval` is a public route (not behind `RequireAuth`), so the redirect works even though a pending login returns no session.

**Open follow-up (offered, not done):** the pending page title is `Application Submitted`, which reads slightly oddly for a returning login (vs. a fresh submit); could be made context-aware ("Account Pending Approval") if desired.

---

## Notes / conventions
- All changes are UI/flow only; no backend or API changes.
- Each edit was type-checked (`tsc` exit 0) and confirmed to leave no dangling references (removed components: `ScrollProgressBar`, `Magnetic`; removed import: `EventDetailDrawer`).
- Visual verification (running the dev server) was left to the user per their preference; changes are low-risk CSS/JSX.
