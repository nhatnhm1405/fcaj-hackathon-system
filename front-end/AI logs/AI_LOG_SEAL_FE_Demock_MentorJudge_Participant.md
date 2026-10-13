# AI Log — SEAL: Demock Mentor/Judge + Participant (FE) & các API backend đi kèm

**Dự án:** SEAL – Software Engineering Hackathon Management System (SU26SWP04)
**Ngày:** 2026-06-14
**Phạm vi phiên:** Gỡ `mockData` khỏi 2 cụm cuối của frontend `seal-web` — **Mentor/Judge** và **Participant/Team** — nối hết sang API thật, redesign các dashboard. Kèm các thay đổi **Backend** (`seal-api`) phát sinh: team-management cho participant, self-edit profile, cap số thành viên. Kết quả: **xóa hẳn `mockData.ts`**, FE 100% chạy API thật.
**Công cụ:** Claude (Opus 4.8). Verify: FE `tsc --noEmit --ignoreDeprecations 6.0`, BE `./mvnw -o compile`.
**Tiền đề:** Nối tiếp [`AI_LOG_SEAL_Admin_Split_FE_Impl.md`](./AI_LOG_SEAL_Admin_Split_FE_Impl.md) (đã làm Admin + Coordinator + Notifications). Phiên này lo Mentor/Judge + Participant.

> ⚙️ **Quy ước đọc nhanh:** mọi mục đánh dấu **[BE]** là thay đổi Backend (`seal-api`). Mục **[FE]** là frontend (`seal-web`). Phần backend cũng được gom riêng ở **mục 5**.

---

## 1. Bối cảnh & vấn đề

Sau khi Admin/Coordinator đã demock (phiên trước), còn lại 2 cụm phụ thuộc `mockData`:

- **Mentor/Judge (5 trang):** `MentorDashboard`, `MentorTracksPage`, `JudgeDashboard`, `JudgeScoringPage`, `JudgeHistoryPage`. Chúng lọc dữ liệu mock theo `currentEvent.event_id` — chính `currentEvent` (shape mock) là "chất keo" khóa luôn `AuthProvider` + `DashboardLayout` vào mock.
- **Participant/Team (8 trang):** `ParticipantDashboard` (monolith ~1200 dòng), `TeamCreate/View/Manage/Submit`, `Leaderboard`, `Profile`, `ForgotPassword`. Backend participant **rất thiếu endpoint** quản lý team.

**Quyết định nghiệp vụ (Q&A với user):**
- 1 event tại 1 thời điểm ⇒ bỏ event-switcher; mentor/judge tự suy event từ assignment của mình.
- Judge: hiện trọng số, weighted total = Σ(value×weight); soft-guard (disable khi round chưa mở, khóa khi đã final). Bỏ calibration.
- Mentor: read-only đợt này (xem submission + feedback = defer backend).
- Participant: gộp View+Manage thành 1 trang "My Team"; leader nộp & sửa trước deadline; lời mời accept/decline ở Dashboard; Profile cho sửa (patch).
- Team-management: **user chọn "thêm trọn backend trước"** ⇒ build đầy đủ BE rồi mới làm FE.

---

## 2. [FE] Cụm Mentor/Judge

### 2.1 apiClient
- Sửa `assignmentsApi`: `/api/mentor|judge/assignments` trả **1 object** `{...Id, ...Name, eventName, teams[]}` (không phải mảng); judge `teams[]` mang `roundId`. Thay shape `MentorAssignment`/`JudgeAssignment` + `AssignmentMember`.

### 2.2 Trang (demock + redesign trên PixelComponents)
- `JudgeDashboard`: stat row + overall progress + per-round cards. Đếm "scored" = số submission có điểm non-draft của mình (`scoringApi.getMyScoresForRound`).
- `JudgeScoringPage`: 2-panel (round/submission ↔ form). Criteria thật (`scoringApi.getCriteria`), nhập value+comment, **weighted total Σ value×weight**; Save Draft / Submit Final; **soft-guard**: disable khi round chưa OPEN, khóa (read-only) khi đã có điểm final.
- `JudgeHistoryPage`: gom điểm của judge qua các round được gán (team, round, weighted total, status).
- `MentorDashboard`: tracks (nhóm theo `trackName`) + team counts.
- `MentorTracksPage`: read-only tabs track → team → members; nút feedback **disabled** chờ backend.

### 2.3 Gỡ event-context mock
- `DashboardLayout`: xóa event-switcher (navbar), `EventContextBlock` (sidebar), `getAvailableEvents`, và toàn bộ import mock ⇒ **file hết mock**.
- `AuthProvider`: xóa `currentEvent` / `setCurrentEvent` / `deriveDefaultEvent` + import mock ⇒ **file hết mock**.

---

## 3. [FE] Cụm Participant/Team

### 3.1 Team-context trong AuthProvider
- `fetchTeamContext(role, fullName)` gọi `/api/teams/my` → `team_id` + `is_leader` (lấy từ `myRole` BE trả; fallback match tên).
- Thêm `refreshTeamContext()` (cập nhật sau tạo/join/rời team) và `patchCurrentUser(fields)` (cập nhật sau sửa profile).

### 3.2 Trang
- `TeamCreatePage`: load `/api/teams/active-events`, `POST /api/teams`, chặn nếu đã có team, `refreshTeamContext` → Manage.
- `TeamViewPage` = **gộp "My Team"**: hiển thị team (event/track/status/role/members) + thao tác leader: **đổi tên**, **mời** (search-users → send invite), **xoá member**, **chuyển lead** (modal), **rời team**. `/team/manage` → redirect `/team/view`; **xóa** `TeamManagePage`.
- `TeamSubmitPage`: leader nộp; chọn round; prefill submission cũ (`getMyForRound`); **soft-guard**: disable khi không phải leader / team chưa APPROVED / quá deadline; submit = upsert.
- `ParticipantDashboard`: **viết mới gọn** (bỏ monolith): greeting + **lời mời accept/decline** (`/api/invites/pending`) + team status card + danh sách rounds.
- `LeaderboardPage`: chọn round → `resultsApi.getPublished(eventId, roundId)`; lọc theo track; highlight team của mình.
- `ProfilePage`: overview (từ `/me` + `/api/teams/my`) + Settings **sửa fullName/studentId/university** qua `PUT /api/auth/me` → `patchCurrentUser`. Change-password = placeholder (chưa có BE).
- `ForgotPasswordPage`: **vỏ tĩnh trung thực** (thông báo chung, không lộ email tồn tại; không thực sự gửi vì chưa có BE).

### 3.3 apiClient (participant)
- `MyTeam` nâng cấp (eventId/eventName/status/myRole + member.userId) + `UpdateTeamPayload`.
- `teamsApi` + `searchUsers`, `update`, `removeMember`, `transferLeadership`, `leave`.
- Fix type cho khớp BE: `ActiveEventWithTracks` (eventId/trackId), `TeamInvite` (inviteId/invitedUserId/...).
- `authApi.updateMe` + `UpdateProfilePayload`.

### 3.4 Dọn file
- **Xóa:** `TeamLeaderDashboard.tsx`, `TeamMemberDashboard.tsx` (file chết, không importer), `TeamManagePage.tsx` (gộp vào View).
- **🗑️ Xóa `src/shared/mocks/` (`mockData.ts`)** — không còn importer nào. **FE 100% mock-free.**

---

## 4. ⭐ Mốc: xóa `mockData.ts`

`mockData.ts` từ **~24 importer → 0** qua các phiên. Toàn bộ Admin / Coordinator / Mentor / Judge / Participant đều chạy API thật. Cây thư mục `src/shared/mocks/` đã bị xóa.

---

## 5. 🔧 [BE] TỔNG HỢP THAY ĐỔI BACKEND (`seal-api`)

> Mục này gom riêng để dễ phân biệt. Tất cả endpoint participant đều `@PreAuthorize("hasRole('PARTICIPANT')")`; security path `/api/teams/**`, `/api/auth/**` đã có sẵn.

### 5.1 Team-management cho Participant
| File | Loại | Thay đổi |
|------|------|----------|
| `dto/response/MyTeamResponse.java` | **sửa** | **+** `eventId`, `eventName`, `status`, `myRole`; `TeamMemberInfo` **+** `userId` |
| `dto/request/UpdateTeamRequest.java` | **mới** | `{ name?, description? }` (patch) |
| `service/TeamService.java` | **sửa** | `getMyTeam` populate field mới; **+** `updateTeam`, `removeMember`, `transferLeadership`, `leaveTeam`, `searchInvitableUsers`, helper `requireLeader` |
| `repository/UserRepository.java` | **sửa** | **+** `searchInvitableStudents(q)` (JPQL: student active, match email/studentId/fullName) |
| `controller/TeamController.java` | **sửa** | **+** `GET /api/teams/search-users?query=`, `PUT /api/teams/{teamId}`, `DELETE /api/teams/{teamId}/members/{userId}`, `PUT /api/teams/{teamId}/transfer/{newLeaderUserId}`, `POST /api/teams/{teamId}/leave` |

**Rule đã code (user duyệt — Cách A linh hoạt):** leader không tự xoá mình / không xoá leader; transfer cho 1 member; leave: member rời tự do, leader phải transfer trước HOẶC nếu là thành viên duy nhất → giải tán team; **không** chặn min 3 khi xoá/rời (để cổng duyệt Coordinator lo).

### 5.2 Cap số thành viên (max 5)
| File | Loại | Thay đổi |
|------|------|----------|
| `service/TeamInviteService.java` | **sửa** | `acceptInvite`: chặn nếu team đã đủ **5** thành viên ("Team is full") |

### 5.3 Self-edit profile
| File | Loại | Thay đổi |
|------|------|----------|
| `dto/request/UpdateProfileRequest.java` | **mới** | `{ fullName?, studentId?, university? }` (patch; KHÔNG cho sửa email/userType/judgeType) |
| `service/AuthService.java` | **sửa** | **+** `updateOwnProfile(email, request)` |
| `controller/AuthController.java` | **sửa** | **+** `PUT /api/auth/me` |

> Lưu ý: phần BE cho **Edit user (admin)** và **Coordinator staff/roster/guest-judge** đã làm & log ở phiên trước (`AI_LOG_SEAL_Admin_Split_FE_Impl.md` mục 4) — KHÔNG lặp lại ở đây.

---

## 6. Verify

| Mốc | FE `tsc` | BE `mvnw compile` |
|-----|----------|-------------------|
| Mentor/Judge demock + gỡ event-context | ✅ | — |
| BE team-management | — | ✅ |
| BE max-5 + self-edit profile | — | ✅ |
| Participant pages (Create/View/Submit/Dashboard/Leaderboard/Profile/Forgot) | ✅ | — |
| Sau khi **xóa mockData.ts** | ✅ | — |

> Chưa chạy live — mới typecheck/compile. Test thật cần reseed DB + tài khoản được gán judge/mentor / có team.

---

## 7. Backend còn DEFER (note để làm sau)

**Participant:**
- Endpoint **forgot-password** (+ gửi email) — `ForgotPasswordPage` đang là vỏ tĩnh.
- Endpoint **change-password** — Settings/Profile đang là placeholder.

**Mentor/Judge (từ phiên này):**
- Khóa điểm sau final + endpoint Coordinator **reopen scoring** (`submitScores` hiện upsert, không khóa).
- Enforce **round-open/deadline** khi submit điểm.
- **Verify judge được gán** vào submission khi chấm (hiện bất kỳ judge chấm bất kỳ bài).
- Mentor: endpoint **xem submission** team mình mentor (hiện `/api/submissions/**` chỉ PARTICIPANT/COORDINATOR → mentor 403) + **write feedback**.
- (Tùy) gỡ `UserEventRole` khi xoá assignment cuối.

**Khác:** Event-migration `/api/events` sang `SYSTEM_ADMIN` (kế hoạch cũ, chưa làm).

---

## 8. Tổng kết file đụng tới

### Frontend (`seal-web`)
**Sửa:** `apiClient.ts`, `app/providers/AuthProvider.tsx`, `app/layouts/DashboardLayout.tsx`, `app/routes/index.tsx`, `features/dashboard/dashboards/JudgeDashboard.tsx`, `MentorDashboard.tsx`, `ParticipantDashboard.tsx`, `features/scoring/JudgeScoringPage.tsx`, `JudgeHistoryPage.tsx`, `LeaderboardPage.tsx`, `features/tracks/MentorTracksPage.tsx`, `features/teams/TeamCreatePage.tsx`, `TeamViewPage.tsx`, `features/submissions/TeamSubmitPage.tsx`, `features/users/ProfilePage.tsx`, `features/auth/ForgotPasswordPage.tsx`.
**Xóa:** `features/dashboard/dashboards/TeamLeaderDashboard.tsx`, `TeamMemberDashboard.tsx`, `features/teams/TeamManagePage.tsx`, **`shared/mocks/mockData.ts` (+ folder)**.

### Backend (`seal-api`)
**Mới:** `dto/request/UpdateTeamRequest.java`, `dto/request/UpdateProfileRequest.java`.
**Sửa:** `dto/response/MyTeamResponse.java`, `service/TeamService.java`, `service/TeamInviteService.java`, `service/AuthService.java`, `repository/UserRepository.java`, `controller/TeamController.java`, `controller/AuthController.java`.

---

*Log sinh bởi Claude (Opus 4.8) — phiên 2026-06-14.*
