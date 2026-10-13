# ★ AI LOG — SETUP Status & Track Assignment (FRONT-END) — 2026-06-16

> Phiên FE đi kèm thay đổi backend: thêm trạng thái **`SETUP`**, cấu hình **mode gán track
> theo từng event** (SELF_SELECT vs RANDOM), **bốc thăm** và **leader tự chọn track** trong SETUP.
> File song song bên BE: `back-end/AI logs/AI_LOG_SEAL_★_SETUP_TRACK_ASSIGNMENT_BE_2026-06-16.md`

---

## 1. Tóm tắt yêu cầu (góc nhìn FE)

- Coordinator phải **đổi status** được, gồm trạng thái mới `SETUP` (đóng đăng ký → gán track).
- Ngày giờ chỉ còn **tượng trưng** (backend gate theo status, không theo ngày).
- Mỗi event chọn **mode gán track**: team tự chọn (`SELF_SELECT`) hay bốc thăm (`RANDOM`).
- Team **không** chọn track lúc đăng ký nữa — track gán ở phase SETUP.
- SELF_SELECT: leader tự chọn track (track đầy thì bị chặn).
- RANDOM / bù: coordinator bấm **bốc thăm**.
- Hiển thị được team đã vào track nào + slot mỗi track.

---

## 2. Stack & vị trí

- React + Vite + TypeScript, thư mục `front-end/src/seal-web`.
- Style: bộ `PixelComponents` (PixelCard/PixelButton/PixelBadge/PixelInput…), JetBrains Mono.
- API layer tập trung ở `src/shared/apiClient.ts`.

---

## 3. Thay đổi theo file

### 3.1 `src/shared/apiClient.ts` (types + API helpers)
- `HackathonEvent.status`: thêm `'SETUP'`; thêm field `trackSelectionMode?: 'SELF_SELECT' | 'RANDOM'`.
- `CreateEventPayload` / `UpdateEventPayload`: thêm `trackSelectionMode?`.
- `Track`: thêm `capacity?: number | null`.
- `CreateTeamPayload`: **bỏ** `trackId` (không chọn track lúc tạo team).
- `MyTeam`: thêm `eventStatus?`, `trackSelectionMode?` (để dashboard biết khi nào hiện picker).
- `teamsApi`:
  - **`selectTrack(teamId, trackId)`** → `PUT /api/teams/{teamId}/track`.
  - **`drawTracks(eventId, includeAssigned=false)`** → `POST /api/teams/event/{eventId}/draw-tracks`.

### 3.2 `src/features/events/CoordEventsPage.tsx` (màn coordinator quản lý event)
- Type `EventStatus` += `'SETUP'`; thêm type `TrackMode`.
- `ApiEvent`/`EventRow` + `trackSelectionMode`; `ApiTrack`/`TrackRow` + `capacity`.
- `normalizeEvent`/`normalizeTrack` đọc field mới (tolerate camel + snake_case).
- **Badge** SETUP (màu vàng).
- **Lifecycle buttons** (`nextStatusActions`):
  - OPEN → **CLOSE REGISTRATION** (→ SETUP)
  - SETUP → **START EVENT** (→ IN_PROGRESS) + **REOPEN REGISTRATION** (→ OPEN) + CANCEL
- **Form tạo event**: thêm selector **Track Assignment** (Teams self-select / Random draw), gửi `trackSelectionMode`.
- **Panel chi tiết**: hiển thị mode; khi DRAFT/OPEN cho **đổi mode** (`updateEventMode` → PUT), khác thì hiện badge.
- **Nút bốc thăm** khi status = SETUP: **DRAW TRACKS** (bù team chưa có track) + **REDRAW ALL** (xáo lại tất cả) → banner xanh báo số team được gán.
- **Tab Tracks**: hiển thị `capacity` dạng badge **"N SLOTS"**.

### 3.3 `src/features/teams/CoordTeamsPage.tsx` (coordinator xem team)
- `trackName()`: team chưa có track (`trackId = 0/null`) hiển thị **"Unassigned"** (trước là "—") →
  nhìn rõ trạng thái trước/sau khi bốc thăm. Bảng có sẵn cột **Track** + filter **All Tracks**
  để xem danh sách team trong từng track.

### 3.4 `src/features/teams/TeamCreatePage.tsx` (participant tạo team)
- **Bỏ** ô chọn Track (state, select, validate, payload).
- Thêm ghi chú: track sẽ được gán sau khi đóng đăng ký (tự chọn hoặc bốc thăm tuỳ event).

### 3.5 `src/features/dashboard/dashboards/participant/screens/CreateTeamScreen.tsx`
- Màn tạo team thứ 2 (trong ParticipantDashboard) — cũng **bỏ** chọn Track + summary "Track" +
  placeholder capacity. `prop initialTrackId` giữ lại nhưng không dùng (noUnusedLocals/Parameters = false).

### 3.6 `src/features/dashboard/dashboards/participant/screens/ExistingTeamDashboard.tsx`
- **MỚI: picker chọn track cho leader** khi:
  `isLeader && eventStatus === 'SETUP' && trackSelectionMode === 'SELF_SELECT' && status === 'APPROVED' && !trackName`.
- Tải tracks qua `tracksApi.getAll(eventId)`, render nút từng track kèm `· N slots`.
- Bấm → `teamsApi.selectTrack(teamId, trackId)` → reload. Track đầy → backend trả lỗi, hiển thị ở banner.

---

## 4. Kiểm thử / verify

- Typecheck: `npx tsc --noEmit --ignoreDeprecations 6.0 -p tsconfig.app.json`.
- **Toàn bộ file đã sửa typecheck sạch.**
- Còn 3 lỗi **có sẵn từ trước**, ở file **không đụng tới**:
  - `app/layouts/DashboardLayout.tsx(506)` — `currentUser` possibly null.
  - `features/users/ProfilePage.tsx(43,44)` — `studentId`/`university` thiếu trên `UserProfile`.
- (Lưu ý cấu hình: `tsconfig.app.json` có cảnh báo deprecation `baseUrl` — dùng cờ `--ignoreDeprecations 6.0` để typecheck.)

---

## 5. Cách kiểm tra team đã vào track (cho người dùng)

1. **FE**: `/coordinator/teams` → chọn event → cột **Track** + filter **All Tracks** (nhớ refresh sau khi DRAW).
2. **API**: `GET /api/teams/event/{eventId}` → xem `trackName` từng team (response lệnh draw cũng trả list đã gán).
3. **DB**: query `Team LEFT JOIN Track`.

---

## 6. Luồng demo end-to-end (FE)

**SELF_SELECT (mặc định):**
1. `/coordinator/events` → event 2 (OPEN) → duyệt team PENDING (ở `/coordinator/teams`).
2. **CLOSE REGISTRATION** → SETUP (tab Tracks hiện "2 SLOTS"/"1 SLOTS"…).
3. Leader login → dashboard hiện **picker** → chọn track (đầy thì bị chặn).
4. Coordinator **DRAW TRACKS** bù team chưa chọn → **START EVENT**.

**RANDOM:**
1. Event 2 lúc OPEN → đổi **Track Assignment = Random draw**.
2. **CLOSE REGISTRATION** → SETUP → **DRAW TRACKS** (chia đều) → **START EVENT**.

---

## 7. Việc còn lại / ghi chú

- Picker SELF_SELECT chưa disable trực tiếp track đầy (chỉ chặn ở backend + báo lỗi) — đủ cho demo;
  muốn polish thì cần API trả số slot còn trống realtime mỗi track.
- 3 lỗi tsc tồn đọng (DashboardLayout, ProfilePage) là nợ cũ, ngoài phạm vi phiên này.
- Ngày giờ vẫn nhập khi tạo event (cột `startDate/endDate` NOT NULL) nhưng **không** ảnh hưởng hành vi.
