# AI Log — Làm đẹp Light Mode (kiểu HackerRank) + Hệ thống Announcement (Mentor & Coordinator)

**Ngày:** 2026-06-21
**Nhánh:** `develop`
**Phạm vi:** Frontend (`front-end/src/seal-web`) **và** Backend (`back-end/src/seal-api`) + 1 migration DB (`back-end/database scripts`).
**Trạng thái:** Hoàn tất. Sau mỗi hạng mục: `npx tsc --noEmit` sạch (chỉ còn cảnh báo `baseUrl` deprecation có sẵn), BE `mvnw compile` EXIT=0. Migration đã chạy trực tiếp trên DB local; đã query DB xác minh.

> Phiên gồm 2 hạng mục lớn nối tiếp:
> 1. **Light mode** đang xấu/khó nhìn → làm lại theo phong cách HackerRank (trung tính, tắt glow), nhiều vòng tinh chỉnh. Kèm fix bug "Back to Home" của các trang footer.
> 2. **Vai trò Mentor**: thêm trạng thái submission + thay "add feedback" (chưa làm) bằng **chức năng announcement**; mở rộng cho **Coordinator** announce theo audience; popup kiểu email; đính kèm link; chọn event; lọc người nhận.
>
> Người dùng yêu cầu **liên tục Q&A** và **hỏi trước khi sửa**. Log ghi lại làm gì / vì sao / thế nào + các quyết định đã chốt.

---

## HẠNG MỤC 1 — Light mode kiểu HackerRank

### Bối cảnh kiến trúc theme
- Light mode **chỉ áp dụng cho khu vực dashboard** (đã đăng nhập). Trang public (landing/login/about…) gọi `useForceDark()` (đặt DOM `data-theme="dark"`) nên luôn tối — **không đụng tới**.
- Màu nền/chữ/viền dashboard lấy từ CSS variables `--c-*`; định nghĩa light nằm ở `:root[data-theme="light"]` trong `styles/index.css`. Object `C` trong `PixelComponents.tsx` trỏ vào các biến này (`bg: var(--c-bg)`, `text: var(--c-text)`…).
- Tồn tại 2 lớp token: shadcn (`--background`…, trong `default_theme.css`) và bộ token app (`--c-*`). Dashboard dùng `--c-*`.

### Vấn đề
1. Bảng màu light cũ ngả **xanh mint + chữ xanh rừng** → đục, lạ mắt, khó đọc.
2. Rất nhiều **glow/shadow neon xanh lá** và giá trị tối hardcode (scrollbar `#0a0f0a`, `boxShadow rgba(0,0,0,.3)`, text-glow) không đổi theo theme → trên nền sáng trông bẩn.

### Quyết định đã chốt (Q&A)
| Quyết định | Lựa chọn |
|---|---|
| Bảng màu | **Trung tính kiểu HackerRank**: nền trắng/xám rất nhạt, chữ slate/navy, viền xám nhạt, xanh lá chỉ làm accent |
| Hiệu ứng glow | **Tắt glow neon**, thay bằng shadow xám tinh tế |
| Cách làm | **Sửa token toàn cục trước**, review trên 1 trang, rồi tinh chỉnh |

### Kỹ thuật cốt lõi
Vì màu đã đi qua biến, chỉ cần đổi token + thêm biến cho phần inline vốn hardcode:
- Viết lại `:root[data-theme="light"]`: nền `#f7f8fa`, surface `#ffffff`, chữ `#1b2733`, muted `#41505f`, viền `#e2e6ea`.
- Thêm biến **theme-aware** để hiệu ứng inline cũng theo theme (dark giữ nguyên giá trị cũ → dark không đổi):
  - `--c-accent` / `--c-accent-bright` / `--c-accent-dim` (light = xanh đậm `#157f3c` để đọc rõ + làm nền nút).
  - `--c-glow` / `--c-glow-faint` (light = `rgba(...,0)` → tắt glow inline trên `PixelButton`/`PixelCard`).
  - `--c-card-shadow` (light = shadow xám nhẹ).
  - `--c-on-accent` (light = `#ffffff`: nút primary chữ trắng trên nền xanh đậm; dark = `#030b06`).
- `C` (PixelComponents): `green/greenBright/greenDim/greenGlow/greenGlowFaint` → trỏ vào các biến trên; `PixelButton` primary `color: C.onAccent`; `PixelCard` boxShadow `var(--c-card-shadow)`; thêm `C.onAccent`.
- Override CSS scope `:root[data-theme="light"]` cho: scrollbar sáng, các class glow (`.pixel-glow`, `.text-glow`, `.blue-glow`, `.glow-pulse`, `.cyber-pulse`…) → shadow xám / `animation:none`.

### Chữ gradient (vòng Q&A tiếp)
Người dùng phản ánh: **chữ gradient xanh→lam chỉ hợp dark**, sang light khó đọc.
- **Lưu ý forced-dark:** trang public bị `useForceDark` đặt DOM `data-theme=dark` nhưng **state React có thể là "light"** → phải dùng **CSS theo `data-theme`** (đúng theo DOM), không đọc state React, tránh phá gradient trên landing.
- 45 file dùng gradient → không sửa inline từng file. Dùng **1 quy tắc CSS** bắt mọi chữ gradient và ép về slate đặc:
```css
:root[data-theme="light"] [style*="text-fill-color: transparent"],
:root[data-theme="light"] [style*="background-clip: text"],
:root[data-theme="light"] .gradient-text,
:root[data-theme="light"] .gradient-text-rev {
  background: none !important;
  -webkit-text-fill-color: var(--c-text) !important;
  color: var(--c-text) !important;
}
```
(React serialize `WebkitTextFillColor:"transparent"` → thuộc tính style chứa `-webkit-text-fill-color: transparent` nên `[style*=...]` bắt được.)

### Đậm accent + badge + header/footer xanh (vòng Q&A tiếp)
Người dùng: cho xanh đậm hơn, chữ sidebar đậm hơn; status/badge (vd EXTERNAL/INTERNAL/FPT) đang nhạt khó nhìn; header & footer **đừng trắng**, đổi xanh lá dịu mắt.
- **Accent đậm**: `--c-accent #1ba94c → #157f3c`; muted/dim đậm hơn (`#5c6b7a → #41505f`, `#8895a3 → #6b7888`) → sidebar + chữ phụ rõ.
- **Badge**: `PixelBadge` gắn class `pixel-badge pixel-badge--{color}`; thêm bảng override 8 màu ở light mode (chữ đậm + nền nhạt đặc + viền rõ). Dark không đổi.
- **Header/Footer xanh**: `--c-navbar-bg` mint `#cdebd7` (vẫn blur), `--c-footer-bg` gradient `#d2ecd9 → #b7e0c2`. Sửa 2 chỗ chữ xanh nhạt hardcode trong `SealFooter` (dòng "Software Engineering Agile League" + copyright) → dùng biến theme (`C.textMuted`, `C.copyright`).

### Bug "Back to Home" (các trang footer)
**Triệu chứng:** đã đăng nhập → bấm footer (About/Team/Contact) → bấm "← Back Home" thì vẫn ra **landing page** thay vì dashboard.
**Gốc rễ:** `/about`, `/team`, `/contact` là route public độc lập (forced dark, navbar riêng, ngoài `RequireAuth`/`DashboardLayout`). Nút "Back Home" gọi `navigate("/")` → route `/` (`LandingPageWrapper`) hiển thị nội dung Landing/marketing.
**Quyết định (Q&A): Option A** (tối giản): nút Back Home + click logo → `navigate(isAuthenticated ? "/dashboard" : "/")`. Sửa cả 6 chỗ trong 3 file (thêm `useAuth`).

### File đã sửa — Hạng mục 1
- `styles/index.css` — token light mới + biến theme-aware + override glow/scrollbar/gradient/badge.
- `shared/components/PixelComponents.tsx` — `C` trỏ biến; `onAccent`; `PixelButton`/`PixelCard`/`PixelBadge`.
- `shared/components/SealFooter.tsx` — 2 chỗ chữ hardcode → biến theme.
- `features/landing/{AboutPage,TeamPage,ContactPage}.tsx` — Back Home/logo → `/dashboard` khi đã login.

---

## HẠNG MỤC 2 — Mentor: submission status + Announcement system

### 2.1. Yêu cầu ban đầu
- Mentor mới chỉ xem được team thuộc track, **chưa tra được team đã nộp submission hay chưa**.
- Nút "ADD FEEDBACK" của mentor **chưa làm** (disabled) → người dùng muốn đổi thành **chức năng thông báo cho các team trong track** (mentor announce lại cho participant).

### 2.2. Khảo sát hệ thống
- `GET /api/mentor/assignments` (`AssignmentService.getMentorAssignments`) → track của mentor → team APPROVED + member.
- Submission lưu theo (team, round); `SubmissionRepository.findAllByTeam_TeamId` → đếm được đã nộp/lần cuối.
- Notification: hạ tầng đủ — `createNotification(recipientUserId, title, content, type)`, có type `ANNOUNCEMENT`, chuông FE đã hiển thị. Mỗi notification 1 dòng/người nhận.
- Coordinator **chưa có** chức năng announce tổng quát.
- DB: `ddl-auto=none` → schema quản lý thủ công bằng file `migration_*.sql` (`CREATE TABLE IF NOT EXISTS`, chạy `mysql < file`).

### 2.3. Quyết định (Q&A, tham chiếu `docs/ProjectRequirements.md`)
Requirements mục 2 nêu vấn đề "kênh liên lạc giữa ban tổ chức/mentor/đội thi còn hạn chế"; mục 4.3 mentor = hỗ trợ + theo dõi tiến độ team trong track; mục 4.5/6.9 coordinator broadcast cấp event.

| Quyết định | Lựa chọn |
|---|---|
| Phạm vi gửi của mentor | **Theo track đang chọn** |
| Trạng thái submission | **Đã/Chưa nộp + số lần + lần cuối** |
| Kênh gửi | **Chỉ in-app** (chuông) |
| Phạm vi của Coordinator | **Toàn event; mentor lo track** — 2 kênh khác mục đích, mentor không thừa (giải thích: coordinator broadcast cấp event, mentor là kênh chuyên môn theo track + theo dõi nộp bài) |
| Popup khi click thông báo | **Mọi notification** → popup kiểu email + đánh dấu đã đọc |
| Form soạn | **Cả mentor & coordinator dùng popup** (đồng nhất) |
| Tiền tố nguồn | Có: `[Mentor · <track>]` / `[Coordinator]` |
| Lịch sử đã gửi | Có cho cả mentor & coordinator |

### 2.4. Thiết kế dữ liệu
- Bảng mới **`Announcement`** (sender, sender_role, scope TRACK/EVENT, **audience**, event_id, track_id, title, content, **link_url**, recipient_count, created_at) — nguồn cho lịch sử + dòng "From/subject" của popup.
- Thêm cột **`Notification.announcement_id`** (nullable FK) → notification participant nhận biết thuộc announcement nào để popup resolve người gửi/scope/link.
- Migration: `back-end/database scripts/migration_announcement.sql` (Announcement + ALTER Notification + cột `audience` + cột `link_url`). **Đã chạy trực tiếp trên DB local** bằng `mysql.exe` (`C:\Program Files\MySQL\MySQL Server 8.0\bin`).

### 2.5. Backend
- Entity `Announcement` + `AnnouncementRepository` (`findBySender_UserIdOrderByCreatedAtDesc`).
- `Notification` entity thêm `@ManyToOne Announcement announcement` (nullable).
- `NotificationService.createNotification(...)` **overload** thêm tham số `Announcement` (10 chỗ gọi cũ giữ nguyên, announcement=null). `NotificationResponse` thêm `senderName/senderRole/scopeLabel/linkUrl` (map từ announcement khi có).
- `AnnouncementService`:
  - `createMentorAnnouncement(mentorId, trackId, title, content, linkUrl)`: kiểm tra mentor được gán track (`existsByMentor_UserIdAndTrack_TrackId`, sai → 403); recipients = member approved+active của team APPROVED trong track; lưu announcement + fan-out notification (prefix `[Mentor · track]`); audit `MENTOR_ANNOUNCE`.
  - `createCoordinatorAnnouncement(coordId, eventId, audience, title, content, linkUrl)`: validate audience ∈ {PARTICIPANT, JUDGE, MENTOR, ALL}; resolve recipients theo audience; audit `COORD_ANNOUNCE`.
  - `listBySender(senderId)` → lịch sử.
- `AnnouncementController`: `POST/GET /api/mentor/announcements` (`hasRole('MENTOR')`), `POST/GET /api/coordinator/announcements` (`hasRole('EVENT_COORDINATOR')`).
- Đã **gỡ** endpoint announce tạm trong `AssignmentController`/`AssignmentService` (chuyển hẳn sang AnnouncementService); giữ phần submission status trong `MentorAssignmentResponse` (`trackId`, `submissionCount`, `lastSubmittedAt`, bỏ DRAFT).

### 2.6. Frontend
- `apiClient`: `announcementsApi` (createMentor/listMentor/createCoordinator/listCoordinator); `Notification` + `AnnouncementItem` thêm field người gửi/audience/linkUrl.
- `NotificationProvider`: `UINotification` thêm `from/sender_role/scope_label/link_url`; thêm `markRead(id)`.
- `NotificationDetailModal` (popup kiểu email): Subject / From / Date / nội dung / **nút link 🔗** — render qua **`createPortal(document.body)`**.
- `AnnouncementComposerModal` (popup soạn, dùng chung): tiêu đề + nội dung + **ô link** + (coordinator) **dropdown chọn event** + **chip chọn audience**; thông báo success/warning/error; portal ra body.
- `DashboardLayout` (NotificationBell): click item → mở popup + `markRead`.
- `MentorTracksPage`: badge **SUBMITTED/NOT SUBMITTED** + chi tiết submission; nút "ANNOUNCE TO <track>" → composer; mục "Sent announcements".
- `CoordinatorDashboard`: card Announcements + nút "ANNOUNCE TO EVENT" → composer (chọn event + audience) + lịch sử.
- `MentorDashboard`: thẻ "Teams Submitted" (X/Y).

---

## BUG & DIAGNOSIS phát sinh trong quá trình (theo thứ tự)

### B1 — Coordinator announce báo 403 "you do not have permission"
**Gốc rễ:** token role trong Spring Security là **`EVENT_COORDINATOR`**, không phải `COORDINATOR` (FE hiển thị "COORDINATOR" nhưng authority = `ROLE_EVENT_COORDINATOR`). Tôi viết nhầm `hasRole('COORDINATOR')`.
**Fix:** đổi 2 endpoint coordinator → `hasRole('EVENT_COORDINATOR')`. (Đã lưu memory `security-role-tokens`: `EVENT_COORDINATOR`/`SYSTEM_ADMIN` ≠ FE `COORDINATOR`/`ADMIN`.)

### B2 — `Table 'seal_hackathon.announcement' doesn't exist`
**Gốc rễ:** migration chưa chạy. **Fix:** chạy `migration_announcement.sql` trực tiếp trên DB local + query xác minh bảng/cột.

### B3 — Popup bị navbar che mất nửa trên
**Gốc rễ:** `NotificationDetailModal` render **bên trong navbar** (`<header>` có `backdrop-filter`) → `backdrop-filter` tạo *containing block* nên `position:fixed` neo theo header 60px, không theo viewport → clip.
**Fix:** **`createPortal` ra `document.body`** (cả `AnnouncementComposerModal`), nâng zIndex 1000/1001.

### B4 — Chỉ gửi cho người approved & active + Coordinator chọn audience
**Quyết định (Q&A):** PARTICIPANT = tất cả student approved+active (gồm chưa có team); thêm trường **Link**; thêm audience **"All"**.
- Lọc `isApproved && isActive` mọi recipient.
- Coordinator chọn **Participants / Judges / Mentors / Everyone**; lưu `audience`; thêm cột `link_url` (popup hiện link clickable).

### B5 — Judge & participant-no-team không nhận
**Gốc rễ (qua đọc schema):**
- `UserEventRole` chỉ chứa **EVENT_COORDINATOR/MENTOR/JUDGE** — **participant KHÔNG** nằm trong đây (xác định qua `User.user_type` + TeamMember). → participant chưa có team không có liên kết event → bị sót.
- JUDGE đang lấy qua `JudgeAssignment.findActiveByEvent` (cần phân công **round**) → judge chưa gán round bị sót.
**Fix:**
- JUDGE/MENTOR → `UserEventRoleRepository.findByRole_RoleNameAndEventId(role, eventId)`.
- PARTICIPANT → `UserRepository.findActiveApprovedStudents()` (tất cả student approved+active).
- ALL → hợp 3 nhóm.

### B6 — "Judge vẫn không nhận" (sau B5)
**Chẩn đoán bằng query DB (không phải bug code):**
| Bằng chứng | Kết quả |
|---|---|
| JudgeAssignment trên **Summer 2026 (event 2)** | **0** (toàn bộ 11 judge ở **Spring 2026 / event 1**) |
| UserEventRole role JUDGE cho event 2 | **0 dòng** |
| Announcement JUDGE → event 2 | `recipient_count = 0` |
Coordinator dashboard mặc định chọn **Summer (OPEN)** — event **chưa có judge nào** → audience rỗng. Mentor có 2 người ở Summer nên gửi được.
**Quyết định (Q&A): thêm bộ chọn event + cảnh báo 0 người** (FE-only):
- Composer coordinator có **dropdown EVENT** (liệt kê mọi event + status) → announce sang đúng event có judge (vd Spring).
- Khi `recipientCount === 0` → hiện **cảnh báo vàng** rõ ràng thay vì tưởng lỗi.

---

## Migration & DB (đã áp dụng trên local)
File `back-end/database scripts/migration_announcement.sql`:
- `CREATE TABLE Announcement (... audience, ... link_url, ...)`.
- `ALTER TABLE Notification ADD COLUMN announcement_id ... + FK`.
- (cho DB đã tạo bảng trước đó) `ALTER TABLE Announcement ADD COLUMN audience ...` + `ADD COLUMN link_url ...`.

> Lưu ý vận hành: `CREATE TABLE IF NOT EXISTS` an toàn re-run; các `ALTER ADD COLUMN` chạy **một lần** (re-run báo "Duplicate column" — vô hại). Môi trường khác cần chạy file này + **restart backend** (vì `ddl-auto=none`, thiếu cột sẽ lỗi khi load notifications).

---

## Tổng hợp file đã thêm/sửa

**Backend**
- *(mới)* `entity/Announcement.java`, `repository/AnnouncementRepository.java`, `service/AnnouncementService.java`, `controller/AnnouncementController.java`, `dto/response/AnnouncementResponse.java`, `dto/request/MentorAnnouncementRequest.java`, `dto/request/CoordinatorAnnouncementRequest.java`.
- `entity/Notification.java` — FK `announcement`.
- `service/NotificationService.java` — overload + map field người gửi/link.
- `dto/response/NotificationResponse.java` — `senderName/senderRole/scopeLabel/linkUrl`.
- `dto/response/MentorAssignmentResponse.java` — `trackId/submissionCount/lastSubmittedAt`.
- `service/AssignmentService.java` — submission status; gỡ announce tạm.
- `controller/AssignmentController.java` — gỡ endpoint announce tạm.
- `repository/UserEventRoleRepository.java` — `findByRole_RoleNameAndEventId`.
- `repository/UserRepository.java` — `findActiveApprovedStudents`.

**Database**
- *(mới)* `back-end/database scripts/migration_announcement.sql`.

**Frontend**
- `styles/index.css` — token light + biến theme-aware + override (glow/scrollbar/gradient/badge).
- `shared/components/PixelComponents.tsx` — `C` theo biến; `onAccent`; `PixelButton/PixelCard/PixelBadge`.
- `shared/components/SealFooter.tsx` — chữ hardcode → biến theme.
- `features/landing/{AboutPage,TeamPage,ContactPage}.tsx` — Back Home → `/dashboard` khi login.
- `shared/apiClient.ts` — `announcementsApi`; field người gửi/audience/linkUrl.
- `app/providers/NotificationProvider.tsx` — UINotification mở rộng + `markRead`.
- *(mới)* `shared/components/NotificationDetailModal.tsx`, `shared/components/AnnouncementComposerModal.tsx`.
- `app/layouts/DashboardLayout.tsx` — bell click → popup + markRead (portal).
- `features/tracks/MentorTracksPage.tsx` — submission badge + composer + lịch sử.
- `features/dashboard/dashboards/CoordinatorDashboard.tsx` — announce card + chọn event/audience + lịch sử.
- `features/dashboard/dashboards/MentorDashboard.tsx` — thẻ Teams Submitted.

## Kiểm thử
- `npx tsc --noEmit` **sạch** ở các file đã đụng sau mỗi bước (chỉ còn cảnh báo `baseUrl` deprecation có sẵn của tsconfig).
- BE `./mvnw -o compile -q` **EXIT=0** sau mỗi đợt đổi BE.
- Query DB xác minh: roles, UserEventRole theo event, JudgeAssignment theo event, recipient_count của các announcement.

## Ghi chú / việc còn ngỏ
- **Participant không gắn event** trong schema → audience "Participants" gửi tới **mọi tài khoản student** (nếu nhiều mùa cùng lúc thì cân nhắc thêm bảng đăng ký event).
- **Upload file** thật cho announcement chưa làm (mới có trường Link). Có thể tái dùng cơ chế upload avatar.
- **Auto-linkify** URL trong nội dung (ngoài ô link riêng) — chưa làm.
- **Trang "Announcements" riêng** cho participant xem lại ngoài chuông — đề xuất, chưa làm.
- Judge của event **Summer** hiện rỗng (chưa phân công judge) — đó là dữ liệu, không phải lỗi; cần Assignments gán judge trước khi announce cho Summer.
