# Full AI Session Log — SEAL Hackathon Auth Module

**Date:** 2026-06-01  
**Project:** SEAL – Software Engineering Hackathon Management System  
**Scope:** Authentication & Authorization backend module (Spring Boot)

---

## Phase 1 — Project Exploration

**Actions:**

- Read `ProjectRequirements.md` (Vietnamese, 504 lines) — extracted actors, use cases, MVP phases, tech stack
- Read `seal_hackathon.sql` — mapped all 16 tables and 31 foreign keys
- Read existing Spring Boot project structure at `D:\code\Spring Boot\seal-api`
- Read `pom.xml` — identified Spring Boot 4.0.6, Java 21, missing JWT dependency, broken test artifact IDs
- Read `application.properties` — found existing DB config and JWT placeholders
- Read all existing stub files (`AuthController`, `AuthService`, `RegisterRequest`, `User`, `Role`) — all empty

**Findings:**

- JWT library completely missing from `pom.xml`
- Test dependencies used non-existent artifact IDs (`spring-boot-starter-data-jpa-test`, etc.)
- `User.password_hash` was `NOT NULL` — problem for OAuth2 users
- `features/auth/` package structure was empty stubs — needed full replacement
- No OAuth2 config in `application.properties`

---

## Phase 2 — Database Analysis & Schema Decision

**Problem identified:** `password_hash NOT NULL` blocks OAuth2 users.

**Decision:** Option C (simplified) — make `password_hash` nullable, add `provider`, `provider_id`, `avatar_url` columns directly into `CREATE TABLE User`.

**ALTER TABLE proposed:**

```sql
ALTER TABLE `User` MODIFY COLUMN password_hash VARCHAR(255) NULL;
ALTER TABLE `User` ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL' ...
ALTER TABLE `User` ADD COLUMN provider_id VARCHAR(255) NULL ...
ALTER TABLE `User` ADD COLUMN avatar_url VARCHAR(500) NULL ...
```

**Table-to-entity mapping confirmed:**

| Table | Notes |
|---|---|
| `Role` | Staff roles only: `EVENT_COORDINATOR`, `MENTOR`, `JUDGE` |
| `User` | `user_type`: `FPT_STUDENT`, `EXTERNAL_STUDENT`, `STAFF` |
| `UserEventRole` | Event-scoped role assignments for staff |

---

## Phase 3 — Full Auth Module Implementation

**Files created (34 total):**

### `pom.xml`
- Removed 5 non-existent test artifact IDs
- Added `jjwt-api`, `jjwt-impl`, `jjwt-jackson` (version 0.12.6)
- Added `springdoc-openapi-starter-webmvc-ui` (Swagger)
- Kept standard `spring-boot-starter-test` + `spring-security-test`

### `application.properties`
- Added Google OAuth2 config (client-id, client-secret, scope)
- Added GitHub OAuth2 config
- Added `app.frontend.url=http://localhost:5173`
- Used `${ENV_VAR:default}` pattern for secrets

### Entities

| File | Key Decisions |
|---|---|
| `User.java` | `password_hash` nullable, `provider` default `LOCAL`, `@PrePersist` for `createdAt` |
| `Role.java` | Simple mapping, `role_name` unique |
| `UserEventRole.java` | `event_id`/`track_id`/`round_id` as plain `Integer` (no JPA relation — those entities don't exist yet) |

### Repositories

| File | Notable Queries |
|---|---|
| `UserRepository` | `findByEmailWithRoles()` and `findByIdWithRoles()` using `JOIN FETCH` to avoid N+1 |
| `RoleRepository` | `findByRoleName()` |
| `UserEventRoleRepository` | Duplicate-check queries for role assignment |

### DTOs

- `RegisterRequest` — `@NotBlank`, `@Email`, `@Size` validation
- `LoginRequest`
- `AssignRoleRequest` — role + scope (`eventId`, `trackId`, `roundId`, `judgeType`)
- `CreateStaffRequest` — for coordinator-created staff accounts
- `ApiResponse<T>` — generic wrapper with `success`, `message`, `data`
- `AuthResponse` — token nullable (null on register, populated on login)
- `UserResponse` — never exposes `password_hash`

### Security Layer

| File | Purpose |
|---|---|
| `JwtService` | JJWT 0.12.x API — `generateToken()`, `validateToken()`, `extractEmail()`, `extractUserId()`, `extractRoles()` |
| `JwtAuthenticationFilter` | `OncePerRequestFilter` — extracts Bearer token, validates, sets `SecurityContext` |
| `JwtAuthenticationEntryPoint` | Returns JSON 401 instead of HTML redirect |
| `UserPrincipal` | Implements both `UserDetails` AND `OAuth2User` |
| `CustomUserDetailsService` | `loadUserByUsername()` using `JOIN FETCH` query |

### OAuth2 Layer

| File | Purpose |
|---|---|
| `OAuth2UserInfo` | Interface abstracting Google vs GitHub attribute keys |
| `GoogleOAuth2UserInfo` | Reads `sub`, `email`, `name`, `picture` |
| `GithubOAuth2UserInfo` | Reads `id`, `email`, `name`/`login`, `avatar_url` |
| `CustomOAuth2UserService` | Extends `DefaultOAuth2UserService` — creates/updates `User` on OAuth2 login |
| `OAuth2LoginSuccessHandler` | Checks `is_approved`/`is_active` → generates JWT → redirects to frontend |

### Services

| File | Key Logic |
|---|---|
| `AuthService` | `register()` — validate, hash, save; `login()` — 4-step check chain; `mapToUserResponse()` reused everywhere |
| `AccountApprovalService` | `approve()`, `reject()` (sets `is_active=false`), `activate()`, `deactivate()` |
| `UserRoleService` | `assignRole()` with duplicate prevention; `createStaffAccount()` for coordinator-created accounts |

### Controllers

| File | Endpoints |
|---|---|
| `AuthController` | `POST /register`, `POST /login`, `GET /me`, `POST /logout` |
| `AccountApprovalController` | `GET /pending`, `PUT /{id}/approve`, `PUT /{id}/reject` |
| `UserController` | `POST /staff`, `GET /`, `GET /{id}`, `PUT /{id}/activate`, `PUT /{id}/deactivate`, `POST /{id}/roles` |

### Config

| File | Key Decisions |
|---|---|
| `AppConfig` | `PasswordEncoder` and `AuthenticationManager` beans here (not in `SecurityConfig`) — avoids circular dependency |
| `SecurityConfig` | CSRF disabled, STATELESS sessions, JWT filter before `UsernamePasswordAuthenticationFilter`, CORS for `localhost:5173` |

### Exceptions

- `BadRequestException` → 400
- `UnauthorizedException` → 401
- `ForbiddenException` → 403
- `ResourceNotFoundException` → 404
- `GlobalExceptionHandler` → catches all, returns JSON

---

## Phase 4 — Design Revisions (3 rounds)

### Revision 1 — Remove ADMIN role, merge with EVENT_COORDINATOR

**Decision:** `Role` table has exactly 3 roles. `ADMIN` merged into `EVENT_COORDINATOR`.

**Files changed:**
- `seal_hackathon.sql` — seed data: removed `ADMIN`
- `SecurityConfig` — all `hasRole('ADMIN')` → `hasRole('EVENT_COORDINATOR')`
- `AccountApprovalController` — `hasAnyRole('ADMIN', 'EVENT_COORDINATOR')` → `hasRole('EVENT_COORDINATOR')`
- `UserController` — same
- `AssignRoleRequest`, `Role.java`, `UserEventRole.java` — comments updated

### Revision 2 — Remove TEAM_LEADER and TEAM_MEMBER from Role table

**Decision:** Participants identified by `User.user_type`, not `Role` table. `TeamMember.member_role` handles `LEADER`/`MEMBER` distinction.

**Files changed:**
- `seal_hackathon.sql` — seed: only 3 roles remain
- `RegisterRequest` — removed `requestedRole` field
- `AuthService` — removed `SELF_REGISTERABLE_ROLES` constant, removed `UserEventRole` assignment on register
- `UserPrincipal` — `buildAuthorities()` grants `ROLE_PARTICIPANT` for `FPT_STUDENT`/`EXTERNAL_STUDENT` from `user_type`
- `SecurityConfig` — added `ROLE_PARTICIPANT` path rules

### Revision 3 — Consolidate user_type to FPT_STUDENT / EXTERNAL_STUDENT / STAFF

**Decision:** Remove `FACULTY` and `GUEST` user types. All staff = `STAFF`. Guest judges distinguished via `UserEventRole.judge_type`.

**Files changed:**
- `AuthService.validateUserTypeFields()` — case `"FACULTY"`, `"GUEST"` → case `"STAFF"`
- `User.java`, `RegisterRequest.java` — updated comment
- `CustomOAuth2UserService` — default `userType` for new OAuth2 users: `"GUEST"` → `"STAFF"`
- `UserPrincipal` — updated Javadoc
- `seal_hackathon.sql` — column `COMMENT` updated

---

## Phase 5 — SQL File Issues & Fixes

**`seal_hackathon.sql` — "cannot run" error**

**Root causes found:**
- `CREATE TABLE` had no guard — failed on re-run
- `ALTER TABLE` at end never ran (crashed before reaching it)
- Seed `INSERT INTO Role` data was missing

**Fix:** Rewrote entire file with `DROP DATABASE IF EXISTS` + `CREATE DATABASE`, baked OAuth2 columns directly into `CREATE TABLE User`, restored seed data.

---

## Phase 6 — Requirements Cross-Check

Read `ProjectRequirements.md` in full.

**Gaps found vs requirements:**

| Gap | Severity | Fix |
|---|---|---|
| Coordinator-created guest judge accounts (§6.5) | Critical | Added `POST /api/users/staff` endpoint + `CreateStaffRequest` DTO + `UserRoleService.createStaffAccount()` |
| Bootstrap coordinator account | Critical | Added seed coordinator to `seal_hackathon.sql` (later moved to `seal_seed.sql`) |
| No `requestedRole` for STAFF registration | Logic | Confirmed correct — STAFF gets roles assigned by coordinator later |

**All business flows documented:**

1. Participant self-registration
2. STAFF self-registration
3. Coordinator creates staff (guest judge)
4. Local login (4-step chain)
5. OAuth2 login (Google/GitHub)
6. Account approval
7. Role assignment
8. View own profile
9. Logout
10. Activate/deactivate user

Frontend pages mapped to APIs — 4 groups: Auth, Common, Coordinator, Participant/Judge/Mentor (future modules).

---

## Phase 7 — Seed Data Audit (`seal_seed.sql`)

| # | Severity | Issue | Fix |
|---|---|---|---|
| 1 | Fatal | `student_code` column doesn't exist → should be `student_id` | Renamed in all `INSERT` rows |
| 2 | High | Fake BCrypt hashes (`$2a$10$hashed_*`) — `matches()` always returns false, no login possible | Replaced with properly-formatted 60-char BCrypt hash; documented how to regenerate |
| 3 | Structural | Duplicate coordinator in `seal_hackathon.sql` shifted all `user_id` values by 1 | Removed coordinator seed from `seal_hackathon.sql`, kept exclusively in `seal_seed.sql` |
| 4 | OK | `INSERT IGNORE INTO Role` handles duplicate roles gracefully | No change needed |

---

## Phase 8 — Compile Error Fix

**Error:**
```
incompatible types: UserPrincipal cannot be converted to OAuth2User
```

**Root cause:** `CustomOAuth2UserService.loadUser()` return type is `OAuth2User`. `UserPrincipal` only implemented `UserDetails`.

**Fix:**
- `UserPrincipal` now implements both `UserDetails` and `OAuth2User`
- Added `Map<String, Object> attributes` field
- Added second constructor `UserPrincipal(User user, Map<String, Object> attributes)`
- `CustomOAuth2UserService` uses `new UserPrincipal(user, oAuth2User.getAttributes())`

---

## Phase 9 — Postman Collection

Generated full Postman Collection JSON (v2.1.0) with:

- 4 folders: Auth, Account Approval, User Management, Security Tests
- Auto-save tokens via `pm.collectionVariables.set()` in test scripts
- Collection-level variables: `baseUrl`, `coordinatorToken`, `participantToken`, `userId`
- 16 requests covering all endpoints
- Documented run order for correct test flow

---

## Final State Summary

**Database:** `seal_hackathon.sql` (DDL) + `seal_seed.sql` (data) — run in order

**Role model:**

| Layer | Values |
|---|---|
| `Role` table | `EVENT_COORDINATOR` \| `MENTOR` \| `JUDGE` |
| `user_type` | `FPT_STUDENT` \| `EXTERNAL_STUDENT` \| `STAFF` |
| Spring roles | `ROLE_PARTICIPANT` (auto) \| `ROLE_EVENT_COORDINATOR` \| `ROLE_MENTOR` \| `ROLE_JUDGE` |

**API endpoints delivered:**

| Method | Endpoint | Access |
|---|---|---|
| `POST` | `/api/auth/register` | Public |
| `POST` | `/api/auth/login` | Public |
| `GET` | `/api/auth/me` | Authenticated |
| `POST` | `/api/auth/logout` | Authenticated |
| `GET` | `/api/account-approvals/pending` | EVENT_COORDINATOR |
| `PUT` | `/api/account-approvals/{id}/approve` | EVENT_COORDINATOR |
| `PUT` | `/api/account-approvals/{id}/reject` | EVENT_COORDINATOR |
| `POST` | `/api/users/staff` | EVENT_COORDINATOR |
| `GET` | `/api/users` | EVENT_COORDINATOR |
| `GET` | `/api/users/{id}` | EVENT_COORDINATOR |
| `PUT` | `/api/users/{id}/activate` | EVENT_COORDINATOR |
| `PUT` | `/api/users/{id}/deactivate` | EVENT_COORDINATOR |
| `POST` | `/api/users/{id}/roles` | EVENT_COORDINATOR |
| `GET` | `/oauth2/authorization/google` | Public (browser) |
| `GET` | `/oauth2/authorization/github` | Public (browser) |

**Total files created/modified:** 36 Java files + 2 SQL files + `pom.xml` + `application.properties`
