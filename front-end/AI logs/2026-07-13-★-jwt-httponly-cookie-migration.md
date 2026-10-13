# ★ AI Log — 2026-07-13 — JWT Storage Migration: localStorage → HttpOnly Cookie

**Scope:** Full-stack (`back-end/src/seal-api` + `front-end/src/seal-web`) — security-critical auth change.
**Branch:** `NhatNHM-jwt-httponly-cookie-migration` (off `develop`)
**Model:** Claude Sonnet 5 (xhigh effort)
**Why starred:** Touches the core authentication mechanism for every user role (participant, mentor, judge, coordinator, admin). Changes CSRF policy, session handling, and the OAuth2 login redirect. High blast radius if misconfigured — worth flagging for review before/after merge.

---

## 1. Starting point

User asked for context on the JWT implementation before starting a task. An `Explore` agent mapped the existing auth stack:

- **Stack:** Spring Boot 4.0.6 + Spring Security 7.0.5 + `jjwt` 0.12.6 (backend), plain-`fetch` API client (frontend, no axios).
- **Token storage (before):** `front-end/src/seal-web/src/shared/apiClient.ts` kept the JWT in `localStorage` (persistent / "remember me") or `sessionStorage` (session-only), attached manually as `Authorization: Bearer <token>` on every `fetch`.
- **Token generation:** `security/JwtService.java` (HMAC-signed, claims: `sub`=email, `userId`, `roles`, `iat`, `exp`, 24h expiry). Issued from `AuthService.login()` and `OAuth2LoginSuccessHandler` (Google/GitHub).
- **Token validation:** `security/JwtAuthenticationFilter.java` read only the `Authorization` header.
- **OAuth2 flow:** token was appended to the redirect URL — `{frontend}/oauth2/redirect?token=JWT` — i.e. leaked into browser history/referrer.
- **Logout:** `POST /api/auth/logout` was a client-side no-op ("JWT is stateless — discard token").
- **Dead config:** `app.jwt.refresh-expiration-ms` existed in `application.properties` but no refresh-token logic anywhere — confirmed out of scope for this session.

## 2. Discussion — should we move to HttpOnly cookies?

User confirmed the localStorage storage and asked to discuss the tradeoffs of switching to `HttpOnly` cookies before planning any code.

**Trade-off summary given to the user:**
- **Gain:** eliminates XSS token theft — JS (malicious or otherwise) can never read the cookie.
- **Cost:** CSRF becomes a live threat again (cookies auto-attach to requests) since CSRF was disabled in `SecurityConfig.java` on the assumption that bearer tokens are immune to it. Re-enabling CSRF requires the double-submit-cookie pattern. Also touches: OAuth2 redirect (must stop leaking the token via URL), the two hand-rolled `fetch()` calls for authenticated file view/download, and logout (must become a real cookie-clearing endpoint).
- **Deployment topology check** (a second `Explore` agent): frontend `sealhackathon.io.vn` and backend `api.sealhackathon.io.vn` are different subdomains of the **same registrable domain**, served via Caddy + Docker Compose on one EC2 host — confirmed via `Caddyfile`, `docker-compose.yml`, and the existing `server.servlet.session.cookie.same-site=lax` convention. This means the relationship is **same-site, cross-origin**, so `SameSite=Lax` is correct and sufficient — `SameSite=None` (and its extra CSRF exposure) is not needed.

Presented three scope options via `AskUserQuestion`:
1. HttpOnly cookie + CSRF protection, no refresh-token pair (recommended)
2. Cookie only, skip CSRF (rejected as unsafe for production)
3. Full cookie + CSRF + access/refresh token pair (rejected as bigger than requested)

**User chose option 1.**

## 3. Planning

Entered plan mode, read every relevant file in full (`JwtService`, `JwtAuthenticationFilter`, `AuthController`, `AuthService`, `AuthResponse`, `LoginRequest`, `OAuth2LoginSuccessHandler`, `SecurityConfig`, `application.properties`, frontend `apiClient.ts`, `AuthProvider.tsx`, `OAuth2RedirectPage.tsx`), then delegated a `Plan` agent (with the full file contents and deployment facts pre-loaded, so it didn't need to re-explore) to produce a concrete file-by-file implementation plan. The agent additionally decompiled the actual `spring-security-web-7.0.5.jar` on disk to verify the exact class/method signatures used in the plan before proposing them (`CookieCsrfTokenRepository`, `XorCsrfTokenRequestAttributeHandler`, `CsrfTokenRequestAttributeHandler`, `CsrfFilter`) — all confirmed present and API-compatible.

User then interrupted mid-plan-review with **"chốt plan luôn đi, khỏi cần chờ agent nữa"** (lock in the plan, stop waiting) — plan mode was exited and implementation started directly from the agent's plan rather than doing a further manual review pass.

## 4. Implementation

### Backend (`back-end/src/seal-api`)

| File | Change |
|---|---|
| `application.properties` | Added `app.jwt.cookie.secure=${JWT_COOKIE_SECURE:false}` — Secure flag must be off for local plain-HTTP dev, on for prod HTTPS. |
| `security/JwtCookieFactory.java` **(new)** | Single place that builds the auth cookie (`seal_auth_token`): `HttpOnly`, `Secure` (env-driven), `SameSite=Lax`, `Path=/`. `Max-Age` set only when `rememberMe=true` (persistent); omitted otherwise (session cookie). `clearAuthCookie()` for logout. Used by both the login path and the OAuth2 path so the two never drift apart. |
| `security/JwtAuthenticationFilter.java` | `resolveToken()`: reads the JWT from the `seal_auth_token` cookie first, falls back to `Authorization: Bearer` header (kept for Swagger/Postman/tooling — no security cost since nothing in the SPA sends that header anymore). |
| `dto/request/LoginRequest.java` | Added `boolean rememberMe` — previously a frontend-only concept (controlled which Web Storage bucket got the token); now the backend needs it to decide the cookie's `Max-Age`. |
| `controller/AuthController.java` | `login()`: builds the cookie via `JwtCookieFactory`, adds it as a `Set-Cookie` header, then **nulls `token`/`tokenType` on the response DTO** before returning — `@JsonInclude(NON_NULL)` drops both fields from the JSON wire format entirely, so the token is never visible to page JS. `logout()`: now actually clears the auth cookie (`Max-Age=0`) and clears the CSRF cookie via `csrfTokenRepository.saveToken(null, ...)` — previously a pure no-op. |
| `security/oauth2/OAuth2LoginSuccessHandler.java` | Sets the auth cookie directly on the response (always `rememberMe=true`, matching the old `setToken(token, true)` behavior for OAuth) and redirects to `{frontend}/oauth2/redirect` with **no `?token=` param** — closes the token-in-URL leak. |
| `security/SpaCsrfTokenRequestHandler.java` **(new)** | Spring's documented SPA pattern: delegates to `XorCsrfTokenRequestAttributeHandler` for `handle()` (BREACH-safe masking when rendering into HTML forms), but resolves the raw header value directly when the request carries `X-XSRF-TOKEN` (SPA calls never render into HTML, so BREACH doesn't apply there). |
| `security/CsrfCookieFilter.java` **(new)** | Spring Security 6/7's CSRF token is resolved **lazily** (`Supplier<CsrfToken>`) — nothing ever calls `.getToken()` unless a form/handler asks for it, so `CookieCsrfTokenRepository` would never actually write the `XSRF-TOKEN` cookie for a plain API. This filter forces eager resolution on every request so the SPA always has a fresh CSRF cookie before its first state-changing call. |
| `config/SecurityConfig.java` | Re-enabled CSRF (`CookieCsrfTokenRepository.withHttpOnlyFalse()` + `SpaCsrfTokenRequestHandler`, cookie customized to `Secure`/`SameSite=Lax`/`Path=/`), wired `CsrfCookieFilter` after Spring's built-in `CsrfFilter`. Updated the class-level Javadoc (previously described the CSRF-disabled/token-in-URL design). Also fixed two bugs found during live verification (§5), and later exempted bearer-token requests from CSRF (§6). |
| `docker-compose.yml` | Added `JWT_COOKIE_SECURE=true` to the `seal-backend` service env — Caddy always fronts the stack with HTTPS in prod. |

### Frontend (`front-end/src/seal-web`)

| File | Change |
|---|---|
| `shared/apiClient.ts` | Removed `TOKEN_KEY`, `getToken()`, `getTokenStorage()`, `setToken()`, `clearToken()` entirely. Added `getCsrfToken()` (reads the JS-visible `XSRF-TOKEN` cookie via `document.cookie`). `apiFetch()` now sends `credentials: 'include'` on every call and attaches `X-XSRF-TOKEN` on non-GET/HEAD requests. The two hand-rolled `fetch()` calls in `problemsApi.view`/`download` (authenticated file view/download — these bypass `apiFetch`) switched from manual `Authorization` headers to `credentials: 'include'`. `AuthTokenData`/`LoginPayload` types updated (`token` field removed, `rememberMe?` added). |
| `app/providers/AuthProvider.tsx` | Mount effect now unconditionally calls `GET /api/auth/me` (can't inspect an HttpOnly cookie from JS — a 401 just means "not logged in") instead of gating on `getToken()`. `login()` posts `{ email, password, rememberMe }` and no longer touches any token in JS. `logout()` always fires the API call (no cookie presence check possible) and clears local UI state. `ACTIVE_ROLE_KEY` (the "which role tab is active" UI preference — not the auth token) simplified from a dual localStorage/sessionStorage mirror of the token's storage location down to plain localStorage, and **exported** so `OAuth2RedirectPage.tsx` can reuse the same key instead of a hardcoded string literal. |
| `features/auth/OAuth2RedirectPage.tsx` | Dropped the `?token=` URL-param handling entirely (the cookie is already set server-side by the time this page loads). **Behavior fix required, not just deletion:** the old `.catch()` on `authApi.me()` unconditionally treated any failure as "you're signed in anyway, go to `/dashboard`" — safe only because the code above it had already proven a token existed. With that gate gone, a direct/stray visit to `/oauth2/redirect` would 401 and hit the same catch, incorrectly routing an anonymous visitor into the dashboard. Changed the catch branch to redirect to `/login` with an error toast instead. |

## 5. Two additional bugs found (and fixed) during live verification

Neither of these was in the original plan — both surfaced only once the changes were exercised against the real dev backend, and both were **pre-existing latent bugs** that live browser cookie flow (specifically `credentials: 'include'`) newly made reachable/visible.

### 5.1 Session-based auth bypass (JSESSIONID backdoor)

**Symptom:** after `POST /api/auth/logout` correctly cleared the `seal_auth_token` cookie, `GET /api/auth/me` still returned `200` with the full user profile.

**Root cause:** `SecurityConfig.java` sets `sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)` (needed so Spring's OAuth2 client can stash `authorization_request` state in the session between the Google/GitHub redirect round-trip). Spring Security's *default* `SecurityContextRepository` under that policy is `HttpSessionSecurityContextRepository`, which transparently persists whatever `Authentication` `JwtAuthenticationFilter` puts on the `SecurityContextHolder` into the `HttpSession` (`JSESSIONID`) on every request. That meant a session cookie became a **second, independent, un-revocable credential** — completely bypassing the JWT's expiry and the logout endpoint's cookie-clearing.

This bug predates this session's changes; it was invisible before because the frontend never sent cookies at all (`apiFetch` had no `credentials: 'include'`), so the browser never sent `JSESSIONID` back. Adding `credentials: 'include'` as part of this migration is what turned it into a live, exploitable gap.

**Fix:** `.securityContext(securityContext -> securityContext.securityContextRepository(new RequestAttributeSecurityContextRepository()))` in `SecurityConfig.java` — scopes the `SecurityContext` to a single request (a plain request attribute, never written to the session), so every request must re-authenticate from the JWT cookie. Verified via `unzip -l` against the actual `spring-security-web-7.0.5.jar` in the local Maven repo that this class exists with the expected API before writing the fix.

### 5.2 `500` instead of `401` for `GET /api/auth/me` when logged out

**Symptom:** after fixing §5.1, an anonymous `GET /api/auth/me` returned `500 Internal Server Error`: `Cannot invoke "Authentication.getPrincipal()" because "authentication" is null`.

**Root cause:** `SecurityConfig.java`'s authorization rules had `.requestMatchers("/api/auth/**").permitAll()` — a blanket rule meant to cover the genuinely public endpoints (`/register`, `/login`, `/forgot-password`, etc.), but it also swept in `/me`, `/complete-profile`, `/me/password`, `/me/avatar`, none of which are actually public. Those controller methods unconditionally cast `((UserDetails) authentication.getPrincipal())`, assuming Spring Security's authorization layer had already rejected unauthenticated calls — but `permitAll()` skips that layer entirely, so the null-checking/rejection never happened and the request fell straight into the controller.

Also pre-existing and predating this session — invisible before because the frontend's old mount-effect *only* called `/api/auth/me` when a token was already present in Web Storage (`if (!token) return;`). This session's intentional change — always calling `/api/auth/me` on mount to check for a valid session cookie — is exactly what exposed it: every anonymous visitor to the site would have hit this `500` on page load.

**Fix:** narrowed the `permitAll()` matcher in `SecurityConfig.java` to the exact public endpoints (`POST /register|login|forgot-password|verify-reset-otp|reset-password|logout`, `GET /check-student-id`); everything else under `/api/auth/**` now falls through to the existing `.anyRequest().authenticated()` rule at the bottom, so Spring Security rejects unauthenticated calls at the authorization layer and returns a clean JSON `401` via the existing `jwtAuthenticationEntryPoint`, before the controller is ever reached. No controller code was touched.

## 6. Follow-up — CSRF friction for Postman/Swagger, resolved with a bearer-token exemption

After the migration landed, user asked a practical question: *if the JWT is HttpOnly now, how do you even get it into Postman?* This surfaced a real usability regression from §4/§5: CSRF was now enforced uniformly on every non-GET request, **regardless of how the request authenticated** — so a Postman/Swagger call using only `Authorization: Bearer <token>` (no cookies, no `X-XSRF-TOKEN`) would 403 on any write. This matters concretely here: `OpenApiConfig.java` already declares a `bearerAuth` HTTP scheme for Swagger UI's "Authorize" button, so this broke "Try it out" for every non-GET endpoint in Swagger too.

User's first instinct was "just disable CSRF" — clarified that this would reopen the original vulnerability for the real SPA (cookie-authenticated requests), not just fix tooling. The two auth paths needed different treatment, not a single on/off switch.

**Why a bearer-only exemption is safe:** a browser can never be tricked into attaching a custom `Authorization` header to a cross-site request the way it auto-attaches cookies — that's the original justification for CSRF being skippable for bearer tokens in the first place. And `JwtAuthenticationFilter.resolveToken()` gives the header priority over the cookie, so there's no way to combine "send a throwaway bearer header to dodge CSRF" with "still get authenticated via the ambient cookie" — an invalid bearer value just fails auth outright, it doesn't fall back to the cookie.

Before implementing, checked whether this had any test/demo impact:
- Real SPA / browser demo: **zero impact** — the frontend never sends an `Authorization` header anymore, always goes through the cookie path, so it keeps full CSRF protection.
- Swagger UI: **fixed** — `bearerAuth` scheme now works for write endpoints again.
- Existing backend test suite (`src/test/java/.../service/*ServiceTest.java`): all Mockito service-layer unit tests, none go through `SecurityConfig`/the filter chain — unaffected either way.

**Implementation** (`SecurityConfig.java`): added `bearerAuthRequestMatcher()` (a `RequestMatcher` lambda checking for a request header starting with `"Bearer "`) and wired it via `.csrf(csrf -> csrf....ignoringRequestMatchers(bearerAuthRequestMatcher()))`.

**Verified with curl:**
- Cookie-authenticated `POST /api/auth/logout` without `X-XSRF-TOKEN` → still `403` (SPA protection intact).
- The same endpoint called with only `Authorization: Bearer <token>` and no CSRF token at all → `200` (tooling friction resolved).

## 7. Verification

- **Backend compile:** `mvnw compile` (offline) — `BUILD SUCCESS` at every stage, no errors (one pre-existing-pattern deprecation warning on `org.springframework.lang.NonNull`, consistent with other filters already in the codebase — not a regression).
- **Frontend typecheck:** `npx tsc --noEmit -p tsconfig.json` — exit 0, no errors.
- **Live backend testing via `curl`** (both dev servers were already running; Spring Boot Devtools hot-reloaded each change):
  - Unauthenticated `GET /api/auth/me` → `401` with clean JSON body (post-fix; was `500` before §5.2).
  - `GET /api/events` (public, primes CSRF) → `Set-Cookie: XSRF-TOKEN=...`.
  - `POST /api/auth/login` **without** `X-XSRF-TOKEN` header → `403` (proves CSRF protection is live).
  - `POST /api/auth/login` **with** the header, `rememberMe:true` → `200`, `Set-Cookie: seal_auth_token=...; HttpOnly; SameSite=Lax; Path=/; Max-Age=86400`, response body has no `token`/`tokenType` fields.
  - Same login with `rememberMe:false` → cookie has **no** `Max-Age` (session-only), confirmed.
  - `GET /api/auth/me` with the cookie → `200`, correct profile.
  - `POST /api/auth/logout` → clears both `seal_auth_token` and `XSRF-TOKEN`.
  - `GET /api/auth/me` after logout → `401` (post-fix; was `200` before §5.1 — the JSESSIONID backdoor).
  - CORS headers on a cross-origin request (`Origin: http://localhost:5173`) → `Access-Control-Allow-Origin` echoes the origin, `Access-Control-Allow-Credentials: true`.
  - (§6) Cookie-auth write without CSRF header → `403`; bearer-auth write without any CSRF header → `200`.
- **Live browser testing via Playwright** (installed Chromium into the scratch dir since it wasn't cached locally, drove the real Vite dev server):
  - Visited `/login` while logged out → `XSRF-TOKEN` (JS-readable) + `JSESSIONID` cookies present; `localStorage` has only the theme preference, **no auth token anywhere**.
  - Logged in through the real form → redirected to `/dashboard`; `seal_auth_token` cookie present with `httpOnly: true`, `sameSite: 'Lax'`, `expires: -1` (session cookie, matching the "remember me" checkbox left unchecked); `localStorage` still has no token (only theme + the non-sensitive `activeRole` UI preference).
  - Reloaded the page → session persisted via the cookie, stayed on `/dashboard`.
  - Fresh anonymous visit to `/` → `GET /api/auth/me` returns `401` cleanly, zero JS runtime errors (`pageerror`), only the browser's routine "non-2xx network response" console line, which is expected/harmless.

## 8. Git workflow

Created a dedicated branch, `NhatNHM-jwt-httponly-cookie-migration`, off `develop`, and split the change into 7 commits following Conventional Commits, per explicit instruction to use `feat`/`fix`/`docs`/etc. and to **never** include a `Co-authored-by` trailer (verified with `git log --format=%B | grep -i co-authored` across every commit on the branch — none found):

```
a1c98aa  feat(auth): issue JWT as an HttpOnly cookie instead of client-side storage
a7db020  feat(auth): re-enable CSRF protection with the double-submit cookie pattern
a2314f0  fix(auth): stop the session cookie from bypassing JWT logout and expiry
0296c4e  fix(auth): return 401 instead of 500 for unauthenticated /api/auth/** calls
d2c8647  feat(auth): switch the frontend to cookie-based sessions
ce6588f  docs: add AI log for the JWT HttpOnly cookie migration
85b9f11  feat(auth): exempt bearer-token requests from CSRF protection
```

`SecurityConfig.java` is touched by four separate commits (a7db020, a2314f0, 0296c4e, 85b9f11), each covering a distinct concern (CSRF setup, session-bypass fix, permitAll-narrowing fix, bearer exemption). Since these are non-contiguous hunks within one file that was already fully edited to its final state, each commit was produced by temporarily reconstructing the file's intermediate content (original HEAD version + only that commit's hunks), staging, committing, then advancing to the next intermediate state — rather than committing the whole diff at once — so each commit's diff is scoped exactly to its stated concern. Verified with `git diff --cached` before every commit in the sequence.

Backend re-compiled successfully (`BUILD SUCCESS`) after the full commit sequence to confirm the reconstruction didn't corrupt anything.

Also generated (on request, not committed to the repo) an English PR title and a Vietnamese PR description for this branch, covering the same content as this log in a condensed, review-ready format.

## 9. Files changed (full list)

**Backend:**
- `back-end/src/seal-api/src/main/resources/application.properties`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/config/SecurityConfig.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/controller/AuthController.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/request/LoginRequest.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/security/JwtAuthenticationFilter.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/security/oauth2/OAuth2LoginSuccessHandler.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/security/JwtCookieFactory.java` **(new)**
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/security/SpaCsrfTokenRequestHandler.java` **(new)**
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/security/CsrfCookieFilter.java` **(new)**
- `docker-compose.yml`

**Frontend:**
- `front-end/src/seal-web/src/shared/apiClient.ts`
- `front-end/src/seal-web/src/app/providers/AuthProvider.tsx`
- `front-end/src/seal-web/src/features/auth/OAuth2RedirectPage.tsx`

## 10. Explicitly out of scope (by user's own choice, §2)

- Access/refresh token pair — the JWT is still a single 24h-lifetime token; `app.jwt.refresh-expiration-ms` remains unused dead config, untouched.
- `SameSite=None` support — not needed given the confirmed same-site (subdomain) production topology; would only become necessary if frontend/backend ever move to genuinely different registrable domains.

## 11. Post-implementation housekeeping

A `find / -iname "spring-security-web-*.jar"` command run early in the session (before switching to a scoped `~/.m2` search) was auto-backgrounded due to scanning the whole filesystem and was left running, unnoticed, for the rest of the session. User asked what the idle shell was doing; identified and killed it (`kill`) — unrelated to any of the auth changes, just an unclosed scratch command.
