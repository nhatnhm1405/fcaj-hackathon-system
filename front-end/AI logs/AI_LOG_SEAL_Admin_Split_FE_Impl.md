# AI Log — SEAL: Dựng màn hình System Admin (Frontend) + Migrate Coordinator khỏi mock

**Dự án:** SEAL – Software Engineering Hackathon Management System (SU26SWP04)
**Ngày:** 2026-06-14
**Phạm vi phiên:** Frontend `seal-web` — dựng cụm màn hình cho actor mới `SYSTEM_ADMIN`, sửa `apiClient` sang nhóm endpoint `/api/admin` & `/api/coordinator`, dọn/migrate các trang Coordinator khỏi `mockData`, gỡ DevToolbar, nối Notifications vào API thật. Kèm một số bổ sung Backend (`seal-api`) phát sinh do FE cần.
**Công cụ:** Claude (Opus 4.8). Verify: FE `tsc --noEmit`, BE `./mvnw -o compile`.
**Tiền đề:** Nối tiếp việc tách role ở [`back-end/AI logs/AI_LOG_SEAL_Admin_Split.md`](../../back-end/AI%20logs/AI_LOG_SEAL_Admin_Split.md) và [`..._BE_Impl.md`](../../back-end/AI%20logs/AI_LOG_SEAL_Admin_Split_BE_Impl.md). Backend đã tách `SYSTEM_ADMIN` khỏi `EVENT_COORDINATOR` và có sẵn `/api/admin/**`, `/api/coordinator/**`; FE chưa hề biết tới Admin.

---

## 1. Bối cảnh & vấn đề

Sau khi backend tách `SYSTEM_ADMIN` (vận hành PLATFORM: tài khoản, role grant, system log) khỏi `EVENT_COORDINATOR` (vận hành 1 sự kiện: duyệt tài khoản, event/round/scoring), frontend bị lệch:

- `AuthProvider` chỉ nhận diện `PARTICIPANT | MENTOR | JUDGE | COORDINATOR` — **không có ADMIN**; `resolveRole`/`STAFF_ROLE_KEYWORDS` bỏ qua `SYSTEM_ADMIN`.
- `apiClient.usersApi` vẫn trỏ endpoint CŨ `/api/users`, `/api/users/staff`, `/api/users/roles`, `/api/users/{id}/activate` — đã bị xóa khỏi backend ⇒ **404**.
- Không có route/nav/màn hình nào cho Admin. `AdminPage.tsx` tồn tại nhưng là rác Figma (PixelComponents + data giả, prop `navigate`, **không mount vào router**).
- Nhiều trang Coordinator (`CoordAccountsPage`, `CoordJudgesPage`...) gọi endpoint user-management cũ đã chết.
- Toàn bộ event-context, notifications, và phần lớn dashboard vẫn render từ `mockData.ts`.

**Định hướng (user chốt qua Q&A):** Admin là **single-role** (login vào thẳng `/admin/dashboard`, không qua `/select-role`); hệ thống mặc định 1 admin + 1 coordinator. Làm **từng bước một**, giữ build luôn xanh.

---

## 2. Các quyết định thiết kế (Q&A với user)

| # | Câu hỏi | Quyết định |
|---|---------|-----------|
| 1 | Event CRUD thuộc ai? | Chuyển sang **Admin** (cần đổi BE) — nhưng **để cuối** (chưa làm phiên này) |
| 2 | Scope màn Admin | Dashboard + Accounts + Role grants + System Logs (+ Events sau) |
| 3 | AdminPage cũ | **Bỏ**, làm mới theo pattern `Coord*` (shadcn-style PixelComponents + apiClient thật) |
| 4 | Admin role model | **Single-role** → vào thẳng dashboard |
| 5 | Thao tác trang Accounts | List + Activate/Deactivate + Create + **Edit** (Edit cần thêm BE) |
| 6 | Dọn Coordinator | Bắt đầu từ `CoordAccountsPage`; sau đó migrate cả nhóm Coordinator |
| 7 | Gap BE coordinator (list staff, list assignment, tạo guest judge) | **Thêm endpoint BE cần thiết** |
| 8 | `CoordPrizesPage` + `CoordAuditPage` (BE chưa có Prize/Audit) | **Tạm ẩn cả 2** (gỡ route/nav + xóa file) |
| 9 | P1 refinement | Làm **Notifications** (nối API thật); **event-context** dừng lại vì bị chặn |

**Quy ước biểu diễn role:** BE trả `roles: List<String>`; admin = `["SYSTEM_ADMIN"]`. FE map keyword `ADMIN` → `AuthUser.role = 'ADMIN'`. Ưu tiên: **ADMIN > COORDINATOR > JUDGE > MENTOR > PARTICIPANT**.

---

## 3. Thay đổi Frontend

### 3.1 Nền role — `app/providers/AuthProvider.tsx`
- `AuthUser['role']` **+** `'ADMIN'`.
- `STAFF_ROLE_KEYWORDS` **+** `'ADMIN'` (khớp `SYSTEM_ADMIN`).
- `resolveRole` / `mapBackendRole` **+** nhánh ADMIN (ưu tiên cao nhất).
- Admin single-role: `resolveAllRoles` trả `['SYSTEM_ADMIN']`, length=1 ⇒ vào thẳng, không select-role.
- **Gỡ** `switchUser` + `buildAuthUser` (mock helper — chỉ DevToolbar dùng); thu gọn import mock còn `events, tracks, userEventRoles, teams, HackathonEvent` (cho `deriveDefaultEvent`).

### 3.2 `shared/apiClient.ts`
**Thay `usersApi` → `adminApi`** (`/api/admin/*`):

| Hàm | Method + Path |
|-----|---------------|
| `getUsers` / `getUserById` | `GET /api/admin/users` · `/{id}` |
| `createUser` | `POST /api/admin/users` (bỏ `roleName` sai — grant là bước riêng) |
| `updateUser` | `PUT /api/admin/users/{id}` (mới) |
| `activateUser` / `deactivateUser` | `PUT /api/admin/users/{id}/activate|deactivate` |
| `getRoleGrants` | `GET /api/admin/roles` |
| `grantRole` / `revokeRole` | `POST /api/admin/roles/grant` · `DELETE .../revoke` |
| `getSystemLogs` | `GET /api/admin/system-logs` |

**Thêm `coordinatorApi`:**

| Hàm | Method + Path |
|-----|---------------|
| `getStaff` | `GET /api/coordinator/staff` |
| `getJudgeRoster` | `GET /api/coordinator/assignments/judges?eventId=` |
| `assignJudge` | `POST /api/coordinator/assignments/judges` |
| `removeJudgeAssignment` | `DELETE /api/coordinator/assignments/judges/{id}` |
| `createGuestJudge` | `POST /api/coordinator/guest-judges` |

**Sửa hàng loạt bug type cho khớp DTO backend** (trước đó nhiều type dùng `id` chung chung, sai cả tên field lẫn shape; các api này chưa page nào dùng nên sửa an toàn):

| Type | Trước | Sau (khớp BE) |
|------|-------|---------------|
| `HackathonEvent` | `id` | `eventId` |
| `Track` | `id` | `trackId` |
| `Round` | `id`, `isCalibration` | `roundId`, `isFinal`, `+eventName?` |
| `CreateRoundPayload` | `isCalibration?` | `isFinal?` |
| `Submission` | `id` | `submissionId`, `+roundName?/submittedByName?/status?` |
| `ScoringCriteria` | `id`, `roundId` | `criteriaId` (bỏ `roundId`) |
| `ScoreRecord` | nested `scores[]` | **FLAT**: `scoreId/judgeUserId/criteriaId/value/isDraft/...` |
| `RoundResult` | `rank`, `submissionId` | `resultId/rankPosition/isPublished/trackName/...` |
| `Team` | `id`, `members` | `teamId`, `+eventName?/trackName?/createdAt?`, `members?` |
| `Notification` | `id/message/read` | `notificationId/content/type?/isRead` |

Thêm các payload/type mới: `UpdateUserPayload`, `JudgeRosterItem`, `AssignJudgePayload`, `CreateGuestJudgePayload`.

### 3.3 Routing & Layout
- `features/dashboard/RoleDashboardPage.tsx`: **+** `case 'ADMIN' → <AdminDashboard/>`.
- `app/routes/index.tsx`:
  - Route group `/admin/*` guard `allowedRoles={["ADMIN"]}`, đặt **ngoài** `RoleGate` (admin single-role): `/admin/dashboard|accounts|roles|logs`.
  - **Gỡ** `DevToolbar` khỏi `RootLayout`.
  - **Gỡ** route `/coordinator/prizes` + `/coordinator/audit`.
- `app/layouts/DashboardLayout.tsx`:
  - `buildNav` **+** nhánh ADMIN (Dashboard/Accounts/Role Grants/System Logs/Profile).
  - `getPageTitle` + `roleBadgeStyle` (**badge đỏ** SYSTEM ADMIN).
  - Loại ADMIN khỏi event-switcher (navbar `hasEventSwitcher` + `EventContextBlock`).
  - Gỡ nav Prizes/Audit của Coordinator.
  - Import `UINotification` từ `NotificationProvider` thay vì `AppNotification` (mock).

### 3.4 Màn hình Admin (mới — pattern `Coord*`: PixelComponents + bảng raw + modal confirm + `adminApi`)
- `features/dashboard/dashboards/AdminDashboard.tsx`: stat cards (total/pending/inactive/staff), phân bố role grants, quick actions. Gọi `adminApi.getUsers()` + `getRoleGrants()`.
- `features/users/AdminAccountsPage.tsx`: tabs All/Students/Staff/Pending/Inactive + search; **Create modal** (email/password/fullName/userType/judgeType), **Edit modal** (fullName/studentId/university/judgeType), **Activate/Deactivate confirm**.
- `features/users/AdminRolesPage.tsx`: bảng role grant; **Grant modal** (user/role/event-scope; SYSTEM_ADMIN & EVENT_COORDINATOR là system-wide); **Revoke confirm**.
- `features/users/AdminSystemLogsPage.tsx`: bảng log read-only, màu theo action, search, refresh.

### 3.5 Dọn & migrate Coordinator
- `features/users/CoordAccountsPage.tsx`: **viết lại** thành hàng đợi duyệt — chỉ `accountApprovalsApi` (`/api/account-approvals/pending|approve|reject`). Reject **không** gửi body (BE set `is_active=false`). Bỏ tabs all/approved/rejected, participants, staff-roles, activate/deactivate (đều thuộc Admin).
- `app/providers/PendingAccountsProvider.tsx`: `/api/users` (chết) → `/api/account-approvals/pending` (count = list length).
- `features/scoring/CoordJudgesPage.tsx`: **demock** — load events/rounds/tracks/staff/roster thật; assign (`POST`), remove (`DELETE`), create guest (`POST /guest-judges`); roster nhóm theo round; xử lý quy tắc `isFinal` (final → track null).
- `features/scoring/CoordScoringPage.tsx`: **demock** — event selector + round tabs; bảng submission + độ hoàn thành chấm điểm (đếm distinct judge non-draft / số judge gán); finalize/publish/export CSV; bảng kết quả từ `resultsApi.getAll`.
- `features/dashboard/dashboards/CoordinatorDashboard.tsx`: **demock** — events/teams/rounds/submissions thật; `pendingCount` qua `usePendingAccounts`.

### 3.6 Notifications (P1)
- `app/providers/NotificationProvider.tsx`: **bỏ mock seed**; fetch `/api/notifications` (`notificationsApi.getAll`), map `NotificationResponse → UINotification` (`{notification_id,title,message,is_read,type,created_at}`), `toKind()` đổi `type` free-form của BE thành `info|success|warning`; `markAllRead` gọi `markAllAsRead`; thêm `refresh`. Giữ nguyên toàn bộ hệ thống Toast (`addToast`/`addAuthToast`).

### 3.7 Gỡ DevToolbar & dọn file
- **Xóa:** `shared/components/DevToolbar.tsx`, `features/users/AdminPage.tsx` (Figma cũ), `features/events/CoordPrizesPage.tsx`, `features/users/CoordAuditPage.tsx`.

---

## 4. Thay đổi Backend (`seal-api`) phát sinh

### 4.1 Edit user (cho `AdminAccountsPage`)
- **DTO** `request/UpdateUserRequest` — patch: `fullName/studentId/university/judgeType` (null = giữ nguyên). **Không** cho sửa `email` (login identity) & `userType` (loại tài khoản).
- `AdminService.updateUser(id, req, adminId)` — patch + ghi `SystemLog` `UPDATE_USER`.
- `AdminController` **+** `PUT /api/admin/users/{id}`.

### 4.2 Coordinator lookups & roster (cho `CoordJudgesPage`)
- **Controller mới** `CoordinatorController` `@RequestMapping("/api/coordinator")` `@PreAuthorize("hasRole('EVENT_COORDINATOR')")`:
  - `GET /staff` → `List<UserResponse>` (STAFF approved+active).
  - `POST /guest-judges` → tạo guest judge + gán round (1 bước).
- `CoordinatorAssignmentController` **+** `GET /judges?eventId=` (roster) + `DELETE /judges/{id}`.
- `AssignmentService` **+** `PasswordEncoder` và 4 method:
  - `listApprovedStaff()`
  - `listJudgeAssignmentsByEvent(eventId)` (góc nhìn coordinator — khác `JudgeAssignmentResponse` của judge)
  - `removeJudgeAssignment(id)` (**hard delete** để re-assign được; unique key không tính `is_active`)
  - `createGuestJudge(req)` (tạo User STAFF/GUEST approved+active → `assignJudge`)
- `JudgeAssignmentRepository` **+** `findActiveByEvent(eventId)` (JOIN FETCH judge/round/event/track).
- **DTO mới:** `response/JudgeRosterItemResponse`, `request/CreateGuestJudgeRequest`.
- *Security:* `/api/coordinator/**` đã được `SecurityConfig` map sẵn cho `EVENT_COORDINATOR` ⇒ không phải sửa.

---

## 5. Verify

| Mốc | FE `tsc` | BE `mvnw compile` |
|-----|----------|-------------------|
| Nền role + apiClient | ✅ EXIT 0 | — |
| Màn Admin (Dashboard/Accounts/Roles/Logs) + Edit-user BE | ✅ | ✅ EXIT 0 |
| Dọn CoordAccounts + provider | ✅ | — |
| CoordJudges (FE+BE) | ✅ | ✅ EXIT 0 |
| CoordScoring + CoordinatorDashboard | ✅ | — |
| Ẩn Prizes/Audit | ✅ | — |
| Notifications | ✅ | — |

> Lưu ý: `tsconfig.json` dùng `baseUrl` đã deprecated trên TS 6 ⇒ chạy check kèm `--ignoreDeprecations 6.0`.
> **Chưa chạy live** (mới typecheck/compile). Để test thật cần reseed `seal_schema.sql`+`seal_seed.sql` (có `SYSTEM_ADMIN` role_id=1 + account admin) vì `ddl-auto=none`, rồi `pnpm dev` + login `admin@fpt.edu.vn` / `coordinator@fpt.edu.vn`.

---

## 6. Phát hiện phụ đáng chú ý

- **apiClient viết "đầu cơ" với `id` ở mọi nơi** trong khi BE dùng PK riêng (`eventId/trackId/roundId/submissionId/criteriaId/teamId/notificationId`). `ScoreRecord` còn sai cả cấu trúc (BE trả FLAT per-(judge,criteria), không nhóm). Đã sửa các type bị dùng; còn lại sẽ lộ dần khi nối trang.
- **`currentEvent` (shape mock `event_id`) là "chất keo"** giữa `AuthProvider` và 5 trang mentor/judge còn mock (`MentorDashboard`, `JudgeDashboard`, `MentorTracksPage`, `JudgeScoringPage`, `JudgeHistoryPage`). Không thể đổi sang dữ liệu thật mà không vỡ 5 trang đó. Coordinator/admin không dùng `currentEvent` ⇒ không phải bug với luồng đang chạy.
- **Gap quyền BE:** coordinator không có endpoint list-user (đã thêm `/api/coordinator/staff`); không có "list judge assignment" (đã thêm roster); không có endpoint tạo user (đã thêm `/guest-judges`).
- **`/api/coordinator/assignments/judges` chưa scope theo event-ownership** — coordinator nào cũng xem/sửa mọi event. OK với "1 coordinator" nhưng cần ghi chú cho tương lai.
- **`removeJudgeAssignment` hard-delete** có thể để score mồ côi nếu judge đã chấm — nên cân nhắc chặn.

---

## 7. Việc còn nợ (đề xuất ưu tiên)

1. **Event-context + cụm mentor/judge:** migrate `MentorDashboard/JudgeDashboard/MentorTracksPage/JudgeScoringPage/JudgeHistoryPage` sang `/api/mentor/assignments`, `/api/judge/assignments`, `/api/scores/*`; bỏ `currentEvent` mock. (Đây là cách đúng để hoàn tất event-context.)
2. **Xóa `mockData.ts`:** còn ~20 importer (AuthProvider + DashboardLayout event-switcher, các dashboard participant/judge/mentor, team pages). Chỉ xóa được khi hết importer.
3. **Prize + AuditLog backend:** hiện không có entity/controller/bảng ⇒ `CoordPrizesPage`/`CoordAuditPage` đang tạm ẩn.
4. **Event-migration sang `SYSTEM_ADMIN`:** chuyển ghi `/api/events`, thêm `AdminEventsPage`, gỡ Events khỏi Coordinator.
5. **Verify live + smoke test** trước khi tin các migration.
6. **Type apiClient còn lại** (`MentorAssignment/JudgeAssignment/TeamInvite/PendingAccount`) verify khi nối trang tương ứng.

---

## 8. Tổng kết file đụng tới

**FE thêm mới:** `AdminDashboard.tsx`, `AdminAccountsPage.tsx`, `AdminRolesPage.tsx`, `AdminSystemLogsPage.tsx`.
**FE sửa:** `AuthProvider.tsx`, `apiClient.ts`, `RoleDashboardPage.tsx`, `routes/index.tsx`, `DashboardLayout.tsx`, `CoordAccountsPage.tsx`, `PendingAccountsProvider.tsx`, `CoordJudgesPage.tsx`, `CoordScoringPage.tsx`, `CoordinatorDashboard.tsx`, `NotificationProvider.tsx`.
**FE xóa:** `AdminPage.tsx`, `DevToolbar.tsx`, `CoordPrizesPage.tsx`, `CoordAuditPage.tsx`.

**BE thêm mới:** `CoordinatorController.java`, `JudgeRosterItemResponse.java`, `CreateGuestJudgeRequest.java`, `UpdateUserRequest.java`.
**BE sửa:** `AdminController.java`, `AdminService.java`, `CoordinatorAssignmentController.java`, `AssignmentService.java`, `JudgeAssignmentRepository.java`.

---

*Log sinh bởi Claude (Opus 4.8) — phiên 2026-06-14.*
