# AI Log — Sửa lỗi "xin vào team" + Thiết kế lại Landing Page + Home trong khung Dashboard

**Ngày:** 2026-06-19
**Nhánh:** `develop`
**Phạm vi:** chủ yếu Frontend (`front-end/src/seal-web`); có **2 chỗ null-safe nhỏ ở Backend** (`JoinRequestService.java`) để chữa NPE — không đổi schema, không đổi nghiệp vụ.
**Trạng thái:** Hoàn tất. `tsc --noEmit` sạch sau mỗi hạng mục; BE compile OK (devtools hot-reload), đã test API thật end-to-end.

> Phiên gồm 3 hạng mục lớn, nối tiếp nhau:
> 1. Chữa lỗi participant không xin vào team được (4 nguyên nhân: 2 BE NPE + 2 FE field-mapping).
> 2. Thiết kế lại Landing Page theo dữ liệu thật (live API) + nhiều vòng tinh chỉnh UI.
> 3. Khi đã đăng nhập, trang Home (`/`) hiển thị trong khung Dashboard; sau đó **bỏ link Home** khỏi sidebar.
>
> Người dùng yêu cầu **liên tục Q&A** xuyên suốt và **hạn chế đụng BE** — log ghi lại đầy đủ làm gì / vì sao / thế nào, kèm các quyết định đã chốt.

---

## HẠNG MỤC 1 — Participant không xin vào team được

### Triệu chứng
- Participant (chưa có team) vào màn **Team Invitations → Find a Team**, search team để xin vào → báo đỏ:
  `ERROR: ... Cannot invoke "com.seal.hackathon.entity.Track.getName()" because the return value of "com.seal.hackathon.entity.Team.getTrack()" is null`
- Nhưng được **mời (invite)** vào team thì vẫn vào được bình thường.

### Gốc rễ
Team **được phép tồn tại mà chưa gán track** (`Team.track` nullable — comment trong entity: coordinator bốc track random ở phase SETUP). Nhưng `JoinRequestService` giả định team luôn có track.

Tổng cộng có **4 nguyên nhân** trên cùng một luồng "xin vào team", phát lộ dần:

| # | Tầng | Vị trí | Lỗi | Cách chữa |
|---|------|--------|-----|-----------|
| 1 | BE | `JoinRequestService.matchesQuery()` | `team.getTrack().getName()` khi search (team null track) → NPE | null-safe |
| 2 | BE | `JoinRequestService.mapToJoinableTeamResponse()` | `getTrackId()/getName()` khi map kết quả về FE → NPE | null-safe |
| 3 | FE | `JoinableTeam` interface + `InvitationsDrawer` | field lệch tên + đọc sai cấu trúc response → không hiển thị | sửa field & shape |
| 4 | BE | `JoinRequestService.mapToResponse()` | `getTrackId()/getName()` khi **gửi** join request → NPE | null-safe |

> Vì sao invite không lỗi: luồng invite (`TeamInviteService`) đã có sẵn null-guard (`getTrack() != null ? ... : null`), giống `TeamService`. Chỉ `JoinRequestService` bị sót.

### Chi tiết FE (nguyên nhân 3 — quan trọng nhất khiến "search ra mà không hiện")
Sau khi chữa NPE (1,2), BE trả về đúng nhưng FE vẫn không hiện team:

- **Field lệch tên**: BE trả `teamName`/`teamStatus`, nhưng FE khai báo `name`/`status`
  (`apiClient.ts` interface `JoinableTeam`) và render `t.name` (`InvitationsDrawer.tsx`) → tên team `undefined` (trống).
- **Đọc sai cấu trúc response**: BE trả `data = { totalJoinableTeams, teams: [...] }` (object bọc), nhưng FE
  gán thẳng `setResults(res.data)` (kỳ vọng mảng) → `results.length` = `undefined` → **không render gì cả**,
  và nhánh "No teams match" cũng không chạy → màn hình trống.

### Các thay đổi
**Backend** — `back-end/.../service/JoinRequestService.java` (3 chỗ, chỉ thêm null-check):
```java
// matchesQuery()
String trackName = (team.getTrack() == null || team.getTrack().getName() == null)
        ? "" : team.getTrack().getName().toLowerCase(Locale.ROOT);

// mapToJoinableTeamResponse() và mapToResponse()
.trackId(team.getTrack() != null ? team.getTrack().getTrackId() : null)
.trackName(team.getTrack() != null ? team.getTrack().getName() : null)
```

**Frontend**:
- `shared/apiClient.ts` — interface `JoinableTeam`: `name → teamName`, `status → teamStatus`, `alreadyRequested?` optional; thêm interface `JoinableTeamList { totalJoinableTeams; teams: JoinableTeam[] }`; `getJoinableTeams` trả `ApiResponse<JoinableTeamList>`.
- `features/dashboard/dashboards/participant/components/InvitationsDrawer.tsx` — `setResults(res.data?.teams ?? [])`; render `t.teamName`.

### Verify
- DB: Team Nexus (id 7) APPROVED, `track_id = NULL`, event Summer 2026 OPEN; user "Nguyen Van Test" (id 28) chưa thuộc team nào trong event → đủ điều kiện hiện.
- Gọi API thật (không/đã login): `GET /api/join-requests/joinable-teams?query=nexus` → trả Team Nexus (`trackName: null`).
- `POST /api/join-requests/teams/7` → 200 OK (`trackName: null`, status PENDING) — đã **hủy request** sau test để DB sạch.

---

## HẠNG MỤC 2 — Thiết kế lại Landing Page (live data)

**File chính:** `features/landing/LandingPage.tsx` (+ `styles/globals.css` cho animation; thêm asset `imports/fpt-logo.png`).

### Quyết định đã chốt (Q&A)
| Quyết định | Lựa chọn |
|------------|----------|
| Nguồn dữ liệu | **Live API** (gọi `/api/events` public; tự cập nhật theo DB) |
| Ảnh trên live-dashboard | **Khung neon kiểu Gallery** (corner brackets + scanline + glow) |
| Timeline tick theo | **Round + lifecycle event** (`FINALIZED→tick`, `ACTIVE→sáng`, `PENDING→mờ`) |
| Terminal events list | Hiện **cả 3 event** với badge status thật |
| Card Fall "bí ẩn" | **Che toàn bộ + glitch "?"** |

### Cơ sở dữ liệu thật (đã kiểm tra)
- Events: **Spring 2026** (COMPLETED) · **Summer 2026** (OPEN) · **Fall 2026** (DRAFT).
- `/api/events/**` là `permitAll` (public) → landing lấy được status thật + leaderboard mà không cần login.
  (Trong `SecurityConfig`, rule `/api/events/**` đứng trước rule results nên results cũng public.)
- Spring 2026 round Final (FINALIZED) → leaderboard thật: **#1 Team Phoenix 65.50 · #2 Team Eagle 62.00 · #3 Team Tiger 61.00**.
- Summer 2026 rounds: Preliminary=ACTIVE, Semi-final=PENDING, Final=PENDING.

### Lớp dữ liệu mới
- Hook `useLandingData()`: gọi `eventsApi.getAll()` → tách `current`(Summer)/`past`(Spring)/`upcoming`(Fall) theo season (fallback theo status); `roundsApi.getAll(current)` cho timeline; `roundsApi.getAll(past)` → tìm round `isFinal` → `resultsApi.getPublished(...)` → top 3. Có fallback khi API lỗi.
- Helper: `eventStatusBadge(status)`, `roundPhase(status)`, `fmtDate(iso)`, component `NeonImageFrame`, `MysteryEventCard`, `buildMilestones(data)`.

### Thay đổi theo từng vùng
**HeroSection**
- Bỏ: đoạn "The complete… in one place"; hàng stats (2,400+ Hackers / 320+ Teams / $50K / 48h); 4 ô metric (Uptime/Active Teams/Submissions/Avg Score).
- Ảnh hero → `NeonImageFrame` (khung neon).
- Terminal `live-dashboard`: `events --list` = 3 event thật + badge status thật; `leaderboard --top 3` = Spring 2026 top 3 thật.

**EventsSection**
- Ongoing = **Summer 2026 thật**.
- Coming Soon = **Fall 2026 dạng bí ẩn** (`MysteryEventCard`): "?" lớn, tên che `SEAL ███ 20██`, track/mô tả thành thanh ▓, badge COMING SOON.

**TimelineSection**
- `buildMilestones()` dựng động: **Registration** (theo lifecycle + cửa sổ đăng ký) + **mỗi round** (tick theo status) + **Results & Awards** (tick khi event COMPLETED).
- Hình học stepper (ngang & dọc) chuyển sang **động theo số node** (`repeat(N,1fr)`, vị trí track/fill tính theo N) — trước đó hardcode 6 node.

**Dọn dẹp:** xóa `ongoingEvents`, `upcomingEvents`, hàm `ImagePlaceholder`, import `PixelCard` (đều không còn dùng).

### Vòng tinh chỉnh UI (Q&A tiếp)
1. **Glitch động**: thêm keyframe `glitchText` (RGB-split + jitter) trong `globals.css`; áp class `.glitch-text` cho **tên Summer**, **dấu "?"** và **tên che của Fall**.
2. **Summer card**: bỏ 2 dòng mốc thời gian (Registration closes / Event window), giữ mùa·năm·round đang chạy.
3. **Bỏ các text định dạng `//`** (`// SEAL_HACKATHON`, `// SUMMER_2026`, `// details classified`) — `NeonImageFrame` cho `label` optional, ẩn khi rỗng.
4. **Navbar đúng thứ tự**: Home → **About** → Events → Timeline → Gallery → FAQ (khớp thứ tự cuộn; trước đó About bị đặt sau Gallery).
5. **Badge status (live-dashboard)**: `COMPLETED→yellow`, `DRAFT→gray`; map đầy đủ để khi coordinator đổi status thì hiện đồng bộ: `OPEN→green open`, `SETUP→cyan setup`, `IN_PROGRESS→blue in progress`, `CANCELLED→red`.
6. **"Everything You Need"**: rút 6 mô tả + subtitle thành one-liner commercial.

### Badge "FPT University" (thay "LIVE · 2026 SEASON OPEN")
- Ban đầu: chữ F/P/T màu logo FPT + "University" trắng.
- Sau đó **bỏ khung badge** (viền + nền + chấm pulse).
- Dựng lại kiểu logo: 3 khối nghiêng (`skewX -11°`) màu xanh dương / cam / xanh lá, chữ trắng; rồi chỉnh ngang bằng nhau, khối **P cao hơn** (dôi 2 đầu).
- **Cuối cùng dùng ảnh logo FPT thật**: lưu ảnh người dùng cung cấp vào `imports/fpt-logo.png`, **xử lý nền đen → trong suốt** + cắt sát logo (PowerShell + System.Drawing LockBits, threshold RGB < 50 → alpha 0), render `<img height=50>` + "University" 26px.

---

## HẠNG MỤC 3 — Home hiển thị trong khung Dashboard, rồi bỏ Home khỏi sidebar

### Vấn đề
Khi user đã đăng nhập, bấm **Home** ở sidebar → điều hướng tới `/` (LandingPage trần, **ngoài** `DashboardLayout`) → mất sidebar, mất user menu góc phải, bị thay bằng nút **"Go to Dashboard"**.

Người dùng muốn: khi vào Home (đã login) vẫn **hiện sidebar** và **thay "Go to Dashboard" bằng user menu**.

### Quan sát kiến trúc
- `App.tsx`: mọi Provider (Auth/Notification/PendingAccounts/Theme/…) bọc toàn bộ router → render `DashboardLayout` ở `/` an toàn.
- `DashboardLayout` đã có sẵn dấu hiệu được thiết kế cho việc này: `const isDashboardRoute = location.pathname !== '/'`.
- `AuthProvider`: `isAuthenticated = !!currentUser` → không có khoảng trống auth=true mà user=null → không flash/blank.

### Quyết định (Q&A): **Option A** — dùng khung dashboard sẵn có
Tái dùng `DashboardLayout` (sidebar + user menu) thay vì nhân bản component vào landing.

### Thay đổi
- `LandingPage.tsx`: thêm prop `hideChrome?` → khi nhúng trong dashboard thì **ẩn NavBar landing + SealFooter riêng** (DashboardLayout đã có top navbar + footer).
- `app/routes/index.tsx` — `LandingPageWrapper`: nếu `isAuthenticated` → `<DashboardLayout><LandingPage hideChrome/></DashboardLayout>`; khách → landing thường (không đổi).
- `DashboardLayout.tsx`: thêm `"/" → "Home"` vào `getPageTitle`.

### Chốt cuối: bỏ luôn link Home
Người dùng hỏi quan điểm; cả hai thống nhất: đã vào hệ thống thì **Dashboard là trung tâm**, link Home xem landing/marketing là **thừa và gây rối**. → **Bỏ link Home khỏi sidebar cho tất cả role** (`DashboardLayout.tsx`), dọn `homeActive` + import `Link` không còn dùng.

> Giữ lại cơ chế "/" hiển thị khung dashboard khi đã login (phòng khi user vào `/` qua URL hoặc link footer) để trải nghiệm vẫn nhất quán.

---

## Tổng hợp file đã sửa

**Backend**
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/service/JoinRequestService.java` — 3 chỗ null-safe track.

**Frontend**
- `src/seal-web/src/shared/apiClient.ts` — interface `JoinableTeam` + `JoinableTeamList` + kiểu trả về.
- `src/seal-web/src/features/dashboard/dashboards/participant/components/InvitationsDrawer.tsx` — đọc đúng `.teams` + `teamName`.
- `src/seal-web/src/features/landing/LandingPage.tsx` — thiết kế lại toàn bộ (live data, hero, events, timeline, glitch, FPT logo, navbar order, prop `hideChrome`).
- `src/seal-web/src/styles/globals.css` — keyframe `glitchText` + class `.glitch-text`.
- `src/seal-web/src/imports/fpt-logo.png` — asset logo FPT đã xử lý nền trong suốt (mới).
- `src/seal-web/src/app/routes/index.tsx` — render Home trong DashboardLayout khi đã login.
- `src/seal-web/src/app/layouts/DashboardLayout.tsx` — bỏ link Home; thêm tiêu đề "Home".

## Kiểm thử
- `npx tsc --noEmit` **sạch** sau mỗi hạng mục (project bật cờ `--ignoreDeprecations 6.0` do tsconfig dùng `baseUrl`).
- BE: compile OK, devtools tự restart; test API thật `/api/join-requests/*`, `/api/events`, `/api/events/1/rounds/3/results` đều OK (đã dọn dữ liệu test).
- Vite dev server (5173) đang chạy → HMR tự nạp; kiểm tra trực quan trên trình duyệt.

## Ghi chú / việc còn ngỏ
- Ở Home (đã login), Hero vẫn còn 2 nút "GET STARTED FREE" / "EVENT REGISTRATION" (dành cho khách) — **chưa đổi**, có thể ẩn/đổi cho user đã login nếu muốn.
- Glitch áp cả lên tên event thật (Summer) theo yêu cầu "thử" — có thể giảm biên độ hoặc chỉ glitch dấu "?" nếu thấy rối.
- Gốc rễ "team null track" mới được vá theo hướng null-safe ở mọi nơi đọc track; nếu muốn triệt để có thể buộc team có track trước khi APPROVED (chưa làm).
