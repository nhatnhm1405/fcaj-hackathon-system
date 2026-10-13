# AI Work Log — Participant/Profile UI cleanup & Avatar feature

- **Ngày:** 2026-06-16
- **Branch:** `develop`
- **Phạm vi:** Frontend (`front-end/src/seal-web`) + Backend (`back-end/src/seal-api`)
- **Mục tiêu chung:** Tối ưu các trang Participant (và mở rộng sang Coordinator/Judge/Mentor/Admin), thêm chức năng up/xóa ảnh đại diện, đổi mật khẩu (UI), dọn các nhãn `//` và note thừa, và một số fix UI.

---

## 1. Yêu cầu của người dùng (theo thứ tự)

### Lượt 1 — yêu cầu gốc
1. Profile của participant hiện chỉ sửa được **tên** → cần thêm **đổi mật khẩu** (UI, chưa cần chạy thật, tương tự forgot-password ở login).
2. Các chỗ text dạng `//` (vd `// team_info`) nhìn xấu → đổi sang **text rõ ràng, to & đậm hơn**.
3. Trong các trang **participant + coordinator**: bỏ các **dòng note** mô tả "chức năng chưa có" hoặc "mô tả section đó làm gì" (và **báo lại đã bỏ những gì**).
4. Thêm chức năng **up ảnh đại diện**.
5. Nút **remove thành viên** của leader đang xóa thẳng → cần **hỏi confirm** trước.
6. Participant chưa có team: các **placeholder "Max teams"** thừa → bỏ.
7. Liên tục Q&A để tránh xóa nhầm ý.

### Lượt 2 — sau khi test
8. **Event Detail** của participant: các card track **chưa cao bằng nhau** (chỗ cao chỗ thấp) → căn đều.
9. Profile: **Student ID phải read-only** (không cho participant sửa).
10. Đổi nhãn `//` ở **Judge/Mentor/Admin** tương tự participant. Giữ lại các câu hướng dẫn hữu ích.

### Lượt 3
11. Avatar up được rồi nhưng **góc trên bên phải (topbar) chưa hiện ảnh** → wiring.

### Lượt 4
12. Mới có up ảnh, **chưa có xóa ảnh** → thêm chức năng xóa.

### Lượt 5
13. Xuất **AI log chi tiết** dạng `.md` vào `front-end/AI logs` (file này).

---

## 2. Các quyết định đã chốt với người dùng (qua hỏi đáp)

| Vấn đề | Phương án đã chọn |
|---|---|
| Up ảnh đại diện | **Làm chạy thật (có backend)** — không chỉ preview |
| Style nhãn `//` | **Heading đậm, to** (font ~15px, weight 700, màu xanh accent); bỏ tiền tố `//` |
| Phạm vi bỏ note | **Bỏ cả** note "chưa có/placeholder" **và** note "mô tả section" |
| Câu hướng dẫn hữu ích | **Giữ lại** (vd "team sẽ PENDING tới khi coordinator duyệt") |

**Quy ước xử lý nhãn `//`:**
- Nhãn là **section đứng độc lập** (vd `// members`, `// team_info`) → đổi thành heading Title Case đậm.
- Nhãn **eyebrow nằm ngay trên một tiêu đề `<h1>/<h2>` lớn** (trùng lặp) → **bỏ hẳn**.
- `//` là **comment code thật** (không render ra UI) → **không đụng**.

---

## 3. Thay đổi Backend (`back-end/src/seal-api`)

### 3.1 Avatar upload — lưu file vào đĩa, serve tĩnh
- **`application.properties`**: thêm cấu hình upload
  ```properties
  app.upload.dir=${UPLOAD_DIR:uploads}
  spring.servlet.multipart.max-file-size=5MB
  spring.servlet.multipart.max-request-size=6MB
  ```
- **`config/WebMvcConfig.java`** *(mới)*: map `/uploads/**` → thư mục `app.upload.dir` (dùng URI tuyệt đối, không phụ thuộc working dir).
- **`config/SecurityConfig.java`**: thêm `.requestMatchers("/uploads/**").permitAll()` (cho phép xem file công khai).
- **`controller/AuthController.java`**:
  - `POST /api/auth/me/avatar` (multipart `file`) → `authService.updateAvatar`.
  - `DELETE /api/auth/me/avatar` → `authService.removeAvatar`.
  - Thêm import `MediaType`, `MultipartFile`.
- **`service/AuthService.java`**:
  - Inject `@Value("${app.upload.dir:uploads}") String uploadDir`.
  - `updateAvatar(email, file)`: validate là ảnh + ≤ 5MB, lưu `uploads/avatars/avatar_<userId>_<timestamp>.<ext>`, set `avatarUrl = "/uploads/avatars/<file>"`.
  - `removeAvatar(email)`: clear `avatarUrl`, best-effort xóa file dưới `/uploads/`.
  - Helper `extensionFor(contentType)` (png/gif/webp/jpg).

> Ghi chú: cột `avatar_url` (length 500) đã tồn tại sẵn trong entity `User` và `UserResponse` (vốn dùng cho avatar OAuth Google/GitHub) nên **không cần migration DB**.

> ⚠️ **Cần restart backend** sau các thay đổi này.

---

## 4. Thay đổi Frontend (`front-end/src/seal-web`)

### 4.1 Hạ tầng auth/avatar
- **`shared/apiClient.ts`**:
  - `apiFetch` không ép `Content-Type: application/json` khi body là `FormData` (để browser tự set multipart boundary).
  - `UserProfile` thêm `avatarUrl?: string | null`.
  - `authApi.uploadAvatar(file)` (POST multipart) và `authApi.deleteAvatar()` (DELETE).
- **`app/providers/AuthProvider.tsx`**:
  - `AuthUser` thêm `avatar_url: string | null`.
  - `ApiUserProfile` thêm `avatarUrl` / `avatar_url`.
  - `mapApiUser` map `avatar_url`.

### 4.2 Trang Profile — `features/users/ProfilePage.tsx` (viết lại)
- **Tab Overview**: thêm khối avatar (ảnh tròn/ô vuông + fallback chữ cái đầu) cạnh tên & email.
- **Tab Settings**:
  - **Profile Photo**: CHOOSE IMAGE → preview local → UPLOAD / CANCEL; có **REMOVE PHOTO** khi đang có ảnh. Giới hạn PNG/JPG/GIF/WEBP ≤ 5MB.
  - **Profile Info**: Full Name (sửa được); **Student ID = read-only (`disabled`)**, không gửi lên server khi save; University (chỉ external student).
  - **Change Password**: form 3 ô (current/new/confirm) + validate; bấm UPDATE PASSWORD hiện thông báo "sẽ bật khi server hỗ trợ" (UI-only theo yêu cầu).
- Đổi nhãn: `// profile_info` → **Profile Info**, `// change_password` → **Change Password**, thêm **Profile Photo**.

### 4.3 Topbar avatar — `app/layouts/DashboardLayout.tsx`
- Import `API_BASE_URL`.
- Tính `avatarUrl` (URL OAuth dùng nguyên; đường dẫn `/uploads/...` ghép `API_BASE_URL`).
- Góc trên bên phải: có ảnh → `<img>` tròn 32×32; chưa có → vẫn dùng initials.
- Cập nhật tức thì nhờ `patchCurrentUser` khi upload/xóa.

### 4.4 Quản lý team — `features/teams/TeamViewPage.tsx`
- Thêm state `removeTarget` + **modal confirm xóa thành viên** ("Remove X from the team? … CONFIRM REMOVE / CANCEL"). Nút REMOVE giờ mở modal thay vì xóa thẳng.
- Đổi nhãn: `// members` → **Members**, `// join_requests` → **Join Requests**.
- Trim note: "No pending requests. *Participants who ask to join will appear here.*" → chỉ còn "**No pending requests.**"

### 4.5 Tạo team — `features/dashboard/.../screens/CreateTeamScreen.tsx`
- **Bỏ** badge `Max teams: 5 · 5 spots left` + comment `PLACEHOLDER`.
- **Bỏ** eyebrow `// create_team` (trùng h1 "Create Your Team").

### 4.6 Event Detail Drawer — `features/dashboard/.../components/EventDetailDrawer.tsx`
- **Bỏ** badge `Max teams: 5` + hằng số/comment `PLACEHOLDER_MAX_TEAMS`.
- Đổi nhãn: `// event_detail` → **Event Detail**, `// available_tracks` → **Available Tracks**, `// rounds` → **Rounds**.
- **Fix card track đồng đều**: nút "JOIN THIS TRACK → CREATE TEAM" đổi `marginTop: 4` → `marginTop: "auto"` để các card cùng hàng cao bằng nhau và nút sát đáy.

### 4.7 Các màn participant khác
- **`screens/NoTeamDashboard.tsx`**: bỏ eyebrow `// join_an_event` (trùng h1); `// open_events` → **Open Events**.
- **`screens/ExistingTeamDashboard.tsx`**: `// team_leader_console`/`// participant_console` → **Team Leader Console**/**Participant Console**; `// team_info` → **Team Info**; `// activity_feed` → **Activity Feed**.
- **`components/InvitationsDrawer.tsx`**: `// team_invitations` → **Team Invitations**; `// find_a_team` → **Find a Team**.
- **`submissions/TeamSubmitPage.tsx`**: bỏ eyebrow `// submit_project` (trùng h1).

### 4.8 Coordinator
- **`events/CoordEventsPage.tsx`**: `// tracks` → **Tracks**; `// rounds` → **Rounds**.
- **`users/CoordAccountsPage.tsx`**: bỏ eyebrow `// confirm_{...}` trong modal (đã có `<h2>` tiêu đề).
- **`teams/CoordTeamsPage.tsx`**: bỏ eyebrow `// confirm_{type}` trong modal.

### 4.9 Judge / Mentor / Admin
- **`dashboards/MentorDashboard.tsx`**: `// mentor_console` → **Mentor Console**; `// my_tracks` → **My Tracks**.
- **`dashboards/JudgeDashboard.tsx`**: `// judge_console` → **Judge Console**; `// overall_progress` → **Overall Progress**.
- **`scoring/JudgePage.tsx`**: `// judge_dashboard` → **Judge Dashboard**; `// review_queue` → **Review Queue**; `// completed` → **Completed**; `// scoring_criteria` → **Scoring Criteria**; `// judge_feedback` → **Judge Feedback**.
- **`scoring/JudgeScoringPage.tsx`**: `// rounds` → **Rounds**.
- **`scoring/LeaderboardPage.tsx`**: bỏ eyebrow `// leaderboard` (trùng h1).
- **`dashboards/AdminDashboard.tsx`**: `// system_administration` → **System Administration**; `// role_grants` → **Role Grants**; `// admin_actions` → **Admin Actions**.

### 4.10 Auth flow (dọn nốt cho đồng bộ)
- **`auth/ForgotPasswordPage.tsx`**: bỏ eyebrow `// password_recovery` (trùng h1 "Forgot Password").
- **`auth/CompleteProfilePage.tsx`**: bỏ eyebrow `// complete_profile` (trùng h1 "Almost there").

> Sau các thay đổi: **không còn nhãn dạng `//` render ra UI** trong toàn app.

---

## 5. Danh sách note/placeholder ĐÃ BỎ (theo yêu cầu báo lại)

1. ProfilePage: "Password change isn't available yet — it requires a backend endpoint…"
2. ProfilePage: "Email and account type are managed by the administrator and cannot be changed here."
3. CreateTeamScreen: badge "Max teams: 5 · 5 spots left" + comment placeholder.
4. EventDetailDrawer: badge "Max teams: 5" (mỗi track) + hằng số/comment `PLACEHOLDER_MAX_TEAMS`.
5. TeamViewPage: cắt câu "Participants who ask to join will appear here." (giữ "No pending requests.").

**Giữ lại có chủ đích** (hướng dẫn hữu ích): CreateTeamScreen "team sẽ PENDING tới khi coordinator duyệt", các câu hướng dẫn ở NoTeamDashboard/ExistingTeamDashboard.

---

## 6. Kiểm tra

- **Backend:** `mvnw -q compile` → exit 0 (sạch).
- **Frontend:** `tsc --noEmit --ignoreDeprecations 6.0` → exit 0 (sạch). *(build chính của project là `vite build` không chạy typecheck; dùng tsc local 6.0.3 để kiểm.)*

---

## 7. Việc còn lại / cần lưu ý

- ⚠️ **Restart backend** để có 2 endpoint avatar (POST/DELETE). Thư mục `uploads/` tự tạo ở working dir khi chạy app.
- **Đổi mật khẩu** hiện chỉ là UI (validate + thông báo), **chưa nối backend** — cần thêm endpoint đổi mật khẩu khi muốn chạy thật.
- **Forgot password** ở login: trang đã có sẵn (`ForgotPasswordPage`), gửi reset link vẫn là deferred backend task.
- Nếu muốn thay initials bằng ảnh ở các chỗ khác (vd menu xổ xuống) thì làm thêm.

---

## 8. Tổng hợp file đã thay đổi

**Backend (mới/sửa):**
- `config/WebMvcConfig.java` *(mới)*
- `config/SecurityConfig.java`
- `controller/AuthController.java`
- `service/AuthService.java`
- `src/main/resources/application.properties`

**Frontend:**
- `shared/apiClient.ts`
- `app/providers/AuthProvider.tsx`
- `app/layouts/DashboardLayout.tsx`
- `features/users/ProfilePage.tsx`
- `features/teams/TeamViewPage.tsx`
- `features/teams/CoordTeamsPage.tsx`
- `features/users/CoordAccountsPage.tsx`
- `features/events/CoordEventsPage.tsx`
- `features/submissions/TeamSubmitPage.tsx`
- `features/scoring/JudgePage.tsx`
- `features/scoring/JudgeScoringPage.tsx`
- `features/scoring/LeaderboardPage.tsx`
- `features/dashboard/dashboards/MentorDashboard.tsx`
- `features/dashboard/dashboards/JudgeDashboard.tsx`
- `features/dashboard/dashboards/AdminDashboard.tsx`
- `features/dashboard/dashboards/participant/screens/NoTeamDashboard.tsx`
- `features/dashboard/dashboards/participant/screens/ExistingTeamDashboard.tsx`
- `features/dashboard/dashboards/participant/screens/CreateTeamScreen.tsx`
- `features/dashboard/dashboards/participant/components/EventDetailDrawer.tsx`
- `features/dashboard/dashboards/participant/components/InvitationsDrawer.tsx`
- `features/auth/ForgotPasswordPage.tsx`
- `features/auth/CompleteProfilePage.tsx`
