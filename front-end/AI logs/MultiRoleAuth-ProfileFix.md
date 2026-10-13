# AI Fix Log — `NhatNHM-MultiRoleAuth-ProfileFix`

Branch: `NhatNHM-MultiRoleAuth-ProfileFix`  
Base commit: `ee64428` (Merge PR #34)

---

## 1. Initial codebase read

**Files read:**
- `AuthProvider.tsx` — Auth context, login/logout, role resolution
- `App.tsx` — Provider tree: ThemeProvider → AuthProvider → NotificationProvider → RouterProvider
- `routes/index.tsx` — Router with `RequireAuth` guard
- `DashboardLayout.tsx` — Sidebar + TopNavbar, `buildNav()` per role

**Findings:**
- `AuthUser.role` is a single string: `'PARTICIPANT' | 'MENTOR' | 'JUDGE' | 'COORDINATOR'`
- `resolveRole()` used `r === 'JUDGE'` (exact match) → misses `JUDGE_INTERNAL` and variants
- All role-based routing depended on `currentUser.role` as a single string
- No mechanism for a multi-role user to select their active role

---

## 2. Task breakdown

| # | Description |
|---|-------------|
| 1 | AuthProvider: parse `roles[]` from API, store `availableRoles` + `activeRole` |
| 2 | Create `RoleSelector.tsx` — card UI for role selection |
| 3 | Create `RoleGate` — guard that redirects to `/select-role` if no active role |
| 4 | Update router: add `/select-role`, `/dashboard/judge`, `/dashboard/mentor`, `/dashboard/coordinator` |
| 5 | Fix all places reading `user.role` as a String |
| 6 | Add "Switch Role" button to Sidebar |

---

## 3. Additional files read

- `LoginPage.tsx` — hardcoded `navigate('/dashboard')` after login
- `RoleDashboardPage.tsx` — switch on `currentUser.role` to render correct dashboard component
- `apiClient.ts` — token helpers (localStorage / sessionStorage)
- `NotificationProvider.tsx` — toast system

---

## 4. Solution design

### Stale closure problem

If `LoginPage` read `availableRoles` from React state immediately after `await login()`, it
would get the old value — the state update has been queued but the component has not re-rendered yet.

**Fix:** Add a new return value `'ok:select-role'` to `login()` so routing intent is
returned directly from the Provider, independent of state update timing.

```
login() returns:
  'ok'               → PARTICIPANT or single-role staff (navigate /dashboard)
  'ok:select-role'   → multi-role user with no saved role (navigate /select-role)
  'pending_approval' | 'invalid_credentials'  → unchanged
```

### New state shape in AuthProvider

```typescript
availableRoles: string[]    // raw from backend: ["EVENT_COORDINATOR", "JUDGE"]
activeRole: string | null   // user-selected: "JUDGE"
                            // persisted under localStorage key 'activeRole'
```

`currentUser.role` remains the frontend type (`'JUDGE'`, `'COORDINATOR'`, …), derived from
`activeRole` via `mapBackendRole()`. All existing code reading `currentUser.role` continues
to work without changes.

### RoleGate logic

```
availableRoles.length > 1  AND  activeRole === null
  → <Navigate to="/select-role" replace />
else
  → <Outlet />
```

PARTICIPANTs (`availableRoles = []`) and single-role staff (`activeRole` auto-set) both pass through.

---

## 5. AuthProvider.tsx changes

- Added `ACTIVE_ROLE_KEY = 'activeRole'`
- Added `resolveAllRoles(profile)` — collects all staff roles from the API response into `string[]`,
  filtered by keywords `['JUDGE', 'MENTOR', 'COORDINATOR']`
- Added `mapBackendRole(str)` — maps raw string → `AuthUser['role']` frontend type
- Fixed `resolveRole()`: `r === 'JUDGE'` → `r.includes('JUDGE')` to catch `JUDGE_INTERNAL` and variants
- New state: `availableRoles`, `activeRole` (initialised from `localStorage.getItem(ACTIVE_ROLE_KEY)`)
- `setActiveRole(role)`: updates state + syncs `currentUser.role` + persists to localStorage
- Session restore (`useEffect`): after fetching `/api/auth/me`, restores `activeRole` from
  localStorage if the saved value is still in `availableRoles`
- `login()`: after fetching `/api/auth/me`, computes `resolvedActive`; returns `'ok:select-role'`
  when multi-role user has no valid saved role
- `logout()`: clears `localStorage.removeItem(ACTIVE_ROLE_KEY)`, resets `availableRoles = []`,
  `activeRole = null`
- `AuthUser` + `mapApiUser()`: added `student_id` and `university` fields (needed for ProfilePage)
- `buildAuthUser()` (DevToolbar mock): added `student_id: user.student_id`,
  `university: user.university_name`
- Removed unused imports: `accountApprovals`, `MOCK_CREDENTIALS`

---

## 6. RoleSelector.tsx (new file)

Initial `ROLE_CONFIG` used keys `JUDGE_INTERNAL` and `COORDINATOR` — incorrect against the
actual backend. After reading backend source, discovered real role names are `JUDGE`, `MENTOR`,
`EVENT_COORDINATOR`. Updated config:

```typescript
ROLE_CONFIG = {
  JUDGE:             { label: "Judge",       path: "/dashboard/judge",       accentColor: "#3b82f6" },
  MENTOR:            { label: "Mentor",      path: "/dashboard/mentor",      accentColor: "#06b6d4" },
  EVENT_COORDINATOR: { label: "Coordinator", path: "/dashboard/coordinator", accentColor: "#eab308" },
  // Aliases kept for backward compatibility
  COORDINATOR:    { … },
  JUDGE_INTERNAL: { … },
}
```

`getRoleConfig(role)` includes keyword-match fallback (`includes('JUDGE')`,
`includes('COORDINATOR')`) for any naming variation from the backend.

**Auto-redirect logic** (single-role users skip the selector entirely):

```typescript
useEffect(() => {
  if (availableRoles.length === 1) {
    setActiveRole(availableRoles[0]);
    navigate(cfg.path, { replace: true });
  }
}, [availableRoles]);
```

---

## 7. routes/index.tsx changes

- Imported `RoleSelector`, `JudgeDashboard`, `MentorDashboard`, `CoordinatorDashboard`
- Added inline `RoleGate` component (~5 lines)
- Added `/select-role` route → `RoleSelector` (inside `RequireAuth` + `DashboardWrapper`)
- Added new routes inside `RoleGate`:
  - `/dashboard/judge` → `<JudgeDashboard />`
  - `/dashboard/mentor` → `<MentorDashboard />`
  - `/dashboard/coordinator` → `<CoordinatorDashboard />`
- Wrapped all staff routes (`/judge/*`, `/mentor/*`, `/coordinator/*`) inside `RoleGate`
- PARTICIPANT routes and shared routes (`/dashboard`, `/leaderboard`, `/profile`) are **not**
  wrapped by RoleGate

---

## 8. LoginPage.tsx changes

Before:
```typescript
if (result === 'ok') navigate('/dashboard');
```

After:
```typescript
if (result === 'ok' || result === 'ok:select-role') {
  navigate(result === 'ok:select-role' ? '/select-role' : '/dashboard');
}
```

---

## 9. DashboardLayout.tsx changes

```typescript
const { currentUser, logout, currentEvent, setCurrentEvent,
        availableRoles, setActiveRole } = useAuth();
```

Added Switch Role button in sidebar bottom section, rendered only when `availableRoles.length > 1`:

```typescript
onClick={() => { setActiveRole(null); navigate("/select-role"); }}
// Collapsed sidebar: "⇄"   Expanded sidebar: "SWITCH ROLE"
```

---

## 10. Additional bugs found (second review pass)

After reading `ProfilePage.tsx`, `AuthController.java`, `AuthService.java`,
`UserPrincipal.java`, `Role.java`, `UserEventRole.java`:

### Bug A — ProfilePage returned null for all real API users

```typescript
// Looked up user in MOCK DATA using the real API user_id
const userRecord = users.find(u => u.user_id === currentUser.user_id);

// Real API users are never in the mock array → userRecord = null → blank page
if (!currentUser || !userRecord) return null;

// Even when IDs coincidentally matched → read the MOCK role
<Field label="Role">{userRecord.role}</Field>  // hardcoded 'PARTICIPANT'
```

### Bug B — Actual backend role strings

From `UserPrincipal.java`:
```java
// FPT_STUDENT / EXTERNAL_STUDENT → ROLE_PARTICIPANT
// STAFF with UserEventRole       → ROLE_EVENT_COORDINATOR / ROLE_MENTOR / ROLE_JUDGE
```

From `AuthService.mapToUserResponse()`:
```java
List<String> roles = user.getUserEventRoles().stream()
    .map(uer -> uer.getRole().getRoleName())
    // → "EVENT_COORDINATOR", "MENTOR", "JUDGE"
    .collect(Collectors.toList());
```

**Conclusion:** Backend returns `["JUDGE", "MENTOR"]` or `["EVENT_COORDINATOR"]`, **not**
`["JUDGE_INTERNAL"]` as the task description stated. The `resolveAllRoles` filter using
`includes()` handles both correctly.

### Bug C — `AuthUser` missing `student_id` and `university`

`UserResponse` from the backend includes both fields, but the `AuthUser` interface and
`mapApiUser()` did not populate them → ProfilePage could not display them.

---

## 11. Fixes for additional bugs

**AuthProvider.tsx:**
```typescript
// Added to AuthUser interface
student_id: string | null;
university: string | null;

// Added to mapApiUser()
student_id: profile.studentId ?? profile.student_id ?? null,
university: profile.university ?? null,

// Added to buildAuthUser() (DevToolbar)
student_id: user.student_id,
university: user.university_name,
```

**ProfilePage.tsx:**
- Removed `import { users }` from mock data
- Removed `userRecord` lookup entirely
- Changed `if (!currentUser || !userRecord)` → `if (!currentUser)`
- Replaced all `userRecord.*` with `currentUser.*`:

| Before | After |
|--------|-------|
| `userRecord.full_name` | `currentUser.full_name` |
| `userRecord.email` | `currentUser.email` |
| `userRecord.role.replace("_", " ")` | `currentUser.role.replace("_", " ")` |
| `userRecord.student_type` | `currentUser.student_type` |
| `userRecord.student_id` | `currentUser.student_id` |
| `userRecord.university_name` | `currentUser.university` |

**RoleSelector.tsx:**
- Changed all user-facing strings from Vietnamese to English
- Added `JUDGE` and `EVENT_COORDINATOR` as primary keys in `ROLE_CONFIG`

---

## 12. Files changed summary

| File | Change | Description |
|------|--------|-------------|
| `app/providers/AuthProvider.tsx` | Modified | Multi-role state, resolveAllRoles, mapBackendRole, fix resolveRole, student_id/university |
| `app/routes/index.tsx` | Modified | RoleGate, /select-role, role dashboard routes, wrap staff routes |
| `app/layouts/DashboardLayout.tsx` | Modified | Switch Role button |
| `features/auth/LoginPage.tsx` | Modified | Handle `'ok:select-role'` return value |
| `features/auth/RoleSelector.tsx` | **New** | Card UI for role selection, auto-redirect, EN text |
| `features/users/ProfilePage.tsx` | Modified | Use `currentUser` directly instead of mock `userRecord` |
| `app/providers/NotificationProvider.tsx` | Modified | AuthToast iPhone-style banner (pre-existing) |
| `features/auth/RegisterPage.tsx` | Modified | Use `addAuthToast` on register (pre-existing) |
| `shared/apiClient.ts` | Modified | rememberMe sessionStorage support (pre-existing) |
| `back-end/.../application.properties` | Modified | Local DB password (pre-existing) |
