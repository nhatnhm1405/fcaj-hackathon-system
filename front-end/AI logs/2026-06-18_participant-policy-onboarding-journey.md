# AI Work Log — Participant: Competition Rules, Status Gating, Onboarding Tour & Live Journey

- **Ngày:** 2026-06-18
- **Branch:** `develop`
- **Phạm vi:** Frontend (`front-end/src/seal-web`) — chỉ role **Participant** (leader / member / no-team). **Không** đụng tới System Admin, Event Coordinator, Mentor, Judge.
- **Mục tiêu chung:** Bổ sung **quy chế thi (policy)** cho participant, sửa nghiệp vụ track (không chọn track khi đăng ký), **gating thao tác team theo `eventStatus`**, thêm **onboarding tour** + **thanh tiến trình sống**, và sửa chỗ chọn track sai trong Event Detail.
- **Cách làm việc:** Q&A liên tục với người dùng, chốt quyết định trước khi sửa (theo yêu cầu cố định của user).

---

## 1. Yêu cầu của người dùng (theo từng lượt)

### Lượt 1 — Quy chế + sửa nghiệp vụ track + gating
- Hệ thống thiếu **quy chế thi** cho participant (cách đăng ký, chấm điểm, luật team 3–5, team thiếu người / participant lẻ sẽ được coordinator **gộp ngẫu nhiên**…).
- Nghiệp vụ participant đang sai: Admin/Coordinator mới là người quản lý cách chia track (self-select / random). Participant **không được chọn track ngay từ đầu**, phải qua giai đoạn **Setup** mới biết track.
- Khi participant login → hiện **popup quy chế**; **footer** thêm link tới quy định.
- Participant login vào **không còn chọn track để đăng ký**, chỉ **tạo team** hoặc **tham gia team**.
- Theo **status của event** mà các chức năng hiện/ẩn: rời team, thêm thành viên, transfer lead, đổi track, đổi tên team.

### Lượt 2 — Tinh chỉnh popup quy chế
- Các mục La Mã (I, II, III…) phải **màu khác & nổi bật hơn** (chữ nội dung đang khó nhìn).
- Bỏ các text dạng `//`, thay bằng **chữ to & rõ ràng hơn**.

### Lượt 3 — Onboarding tour (hướng dẫn)
- Tạo luồng hướng dẫn cho participant vừa đăng ký (no-team): màn giữa hiện **"Welcome our hacker!"** → fade → chuyển animation sang các **ô tròn đánh số 1→2→3→4→5** minh họa từng bước Create team / Wait for invite.

### Lượt 4 — Thanh tiến trình "sống"
- Cần thêm 1 tour **thật sự chạy song song với thao tác người dùng**: stepper 1-2-3-4-5 đặt **phía trên** component "Join an Event", làm tới bước nào thì bước đó sáng để minh họa rõ ràng.

### Lượt 5 — Sửa Event Detail
- Ở View Detail, mỗi track có nút **"JOIN THIS TRACK → CREATE TEAM"** là sai — giai đoạn đăng ký chưa biết track để chọn.

### Lượt 6
- Xuất **AI log chi tiết & đầy đủ** phiên hôm nay dạng `.md` vào `front-end/AI logs` (file này).

---

## 2. Các quyết định đã chốt (qua Q&A)

| Vấn đề | Phương án đã chọn |
|---|---|
| Khóa thao tác sửa team theo phase | **Khóa từ `IN_PROGRESS`** (cho phép ở `OPEN` + `SETUP`) |
| Phạm vi gating | **Chỉ Frontend** (ẩn/disable nút + toast) — không thêm backend guard |
| Tần suất popup quy chế | **Hiện mỗi lần login** (1 lần/phiên qua `sessionStorage`) |
| Trang quy chế | **Chỉ popup**, footer mở lại đúng popup (không tạo route riêng) |
| Ngôn ngữ quy chế | **Tiếng Anh** |
| Luật team 3 người tối thiểu | **Banner cảnh báo** trên trang team + ghi trong quy chế |
| Nội dung quy chế | Dịch doc chính thức **SEAL Hackathon** sang tiếng Anh, mốc thời gian **Summer 2026** |
| Phản hồi thao tác | **Mọi thao tác → toast popup** xác nhận "đã thay đổi" |
| Onboarding tour — khi nào | **Chỉ lần đầu** (localStorage), **sau khi đóng popup quy chế**; có nút xem lại |
| Onboarding tour — kiểu | **Overlay giữa màn hình + stepper số**, điều hướng **Next/Back + Skip**, **5 bước** |
| Thanh tiến trình — phạm vi | **Xuyên suốt hành trình** (No-team + team console) |
| Thanh tiến trình — bước | Map **trạng thái thật**: Joined → Team → Approved → Track → Compete |
| Thanh tiến trình — tương tác | **Chỉ hiển thị** (pulse bước hiện tại, ✓ bước xong) |

---

## 3. Tổng quan file đã tạo / sửa

### File mới
| File | Vai trò |
|---|---|
| `src/shared/components/CompetitionRulesModal.tsx` | Popup quy chế (English, Summer 2026), scrollable |
| `src/app/providers/RulesProvider.tsx` | `useRules()`; auto-mở quy chế mỗi phiên cho participant; handoff sang tour |
| `src/shared/teamPhase.ts` | Helper gating: `isTeamEditable`, `teamLockReason`, `canPickTrack`, `MIN/MAX_TEAM_SIZE` |
| `src/shared/components/OnboardingTour.tsx` | Modal hướng dẫn: splash "Welcome our hacker!" + 5 bước |
| `src/app/providers/TourProvider.tsx` | `useTour()`; quản lý localStorage done flag; render tour |
| `src/shared/components/ParticipantJourneyBar.tsx` | Thanh tiến trình sống theo state thật |

### File sửa
| File | Thay đổi |
|---|---|
| `src/app/App.tsx` | Bọc `TourProvider` → `RulesProvider` quanh `RouterProvider` |
| `src/shared/components/SealFooter.tsx` | Thêm link **"Competition Rules"** (action mở popup) |
| `src/features/teams/TeamViewPage.tsx` | Gating theo status + banner khóa + banner min-3 + toast mọi action |
| `src/features/dashboard/dashboards/participant/screens/ExistingTeamDashboard.tsx` | Toast khi chọn track + gắn `ParticipantJourneyBar` |
| `src/features/dashboard/dashboards/participant/screens/NoTeamDashboard.tsx` | Nút "? How it works" + gắn `ParticipantJourneyBar` |
| `src/features/dashboard/dashboards/participant/components/EventDetailDrawer.tsx` | Bỏ nút chọn-track, track thành info, thêm CTA tạo team chung |
| `src/features/dashboard/dashboards/ParticipantDashboard.tsx` | Cập nhật caller `EventDetailDrawer.onCreateTeam` (bỏ trackId) |

> **Lưu ý:** `CreateTeamScreen` đã **không** cho chọn track từ trước phiên này (track gán ở Setup) — không phải sửa.

---

## 4. Chi tiết kỹ thuật

### 4.1 Popup quy chế — `CompetitionRulesModal.tsx`
- Modal full-screen overlay, đóng bằng nút ✕ / click nền / phím **Escape**; khóa scroll body khi mở.
- Nội dung: bản dịch tiếng Anh doc chính thức **SEAL Hackathon → Summer 2026**:
  - About (3 Hackathon Spring/Summer/Fall + 3 leaderboard).
  - I. About — theme "AI Agents for Software Innovation".
  - II. Schedule — Registration 1–19 Jun 2026, Workshop 29 Jun, Opening/draw 4 Jul, Code/Final/Closing 5 Jul.
  - III. Structure — team 3–5, Bracket A/B, vòng Sơ loại (8h code) → Chung kết.
  - IV. Scoring criteria — Preliminary (5 tiêu chí) / Final (5 tiêu chí).
  - V. Eligibility & rules — 1 thí sinh 1 đội, sau khi đóng đăng ký không đổi thành viên, gian lận → loại…
  - VI. Awards — Nhất 7tr / Nhì 5tr / Ba 3tr / Ý tưởng / Ứng dụng / Cá nhân.
  - **Platform notes** — đăng ký = tạo/join team, **không chọn track lúc đăng ký**, team < 3 bị gộp, team khóa khi IN_PROGRESS.

**Tinh chỉnh hiển thị (Lượt 2):**
- Bỏ eyebrow `// competition_rules` → thay **"OFFICIAL COMPETITION RULES"** (13px, đậm, in hoa).
- Mục La Mã (`Section`): màu sáng riêng — **`#4ade80`** (mục thường) / **`#60a5fa`** (Platform notes), **17px / weight 800**, có **thanh accent dọc phát sáng** + đường kẻ phân cách + glow.
- Nội dung (`P`, `List`): đổi `C.textMuted` → **`C.text`** (tương phản đủ, theo theme), 13px, line-height thoáng hơn.
- Từ in đậm (`B`): **`C.green`** weight 800 (đọc tốt cả light/dark).

### 4.2 Provider quy chế — `RulesProvider.tsx`
- `useRules() → { openRules, closeRules }`.
- Auto-mở **1 lần/phiên** cho `role === 'PARTICIPANT'` (cờ `sessionStorage['sealRulesSeen']`); reset cờ khi `currentUser === null` (logout) → login lại hiện tiếp.
- `autoShownRef`: đánh dấu lần mở là **auto trên login** (không phải mở từ footer) → dùng để handoff sang tour.
- Khi `closeRules`: nếu là auto-shown **và** `currentUser.team_id === null` → gọi `maybeAutoStartTour()`.

### 4.3 Helper gating — `teamPhase.ts`
```ts
MIN_TEAM_SIZE = 3; MAX_TEAM_SIZE = 5;
isTeamEditable(status): OPEN | SETUP → true; rỗng/undefined → true (permissive); còn lại false
teamLockReason(status): câu giải thích khi bị khóa (IN_PROGRESS/COMPLETED/CANCELLED)
canPickTrack(status, mode): SETUP + SELF_SELECT
```

### 4.4 Gating + toast — `TeamViewPage.tsx`
- `editable = isTeamEditable(team.eventStatus)`, `lockReason = teamLockReason(...)`.
- **Ẩn/disable khi `!editable`:** EDIT NAME, INVITE MEMBER, cột Actions (TRANSFER LEAD / REMOVE), ACCEPT join-request, LEAVE TEAM.
- **Banner khóa** (xanh dương + 🔒) khi `lockReason`.
- **Banner min-3** (vàng) khi `editable && members < MIN_TEAM_SIZE`.
- Thay hardcode `/5` → `MAX_TEAM_SIZE`.
- **Toast cho mọi action:** rename (success), invite (success), accept join (success), decline (info), remove (warning), transfer (success), leave (info).

### 4.5 Onboarding tour — `OnboardingTour.tsx` + `TourProvider.tsx`
- **Splash:** giữa màn hình, chữ gradient **"Welcome our hacker!"** + 3 chấm pulse, animation `tourWelcome` (fade-in→hold→fade-out) **2s** → tự chuyển sang `steps`.
- **Stepper số 1→2→3→4→5:** ô hiện tại sáng + glow + pulse, ô đã qua xanh, đường nối đổi màu; bấm ô để nhảy bước.
- **5 bước** (icon lucide + tiêu đề gradient + mô tả + chip minh họa nút thật):
  1. 🚀 Welcome aboard — cần team 3–5, có 2 cách.
  2. 👥 Create a team — `↳ CREATE A TEAM` (thành leader).
  3. ✉️ …or wait for an invite — `↳ WAIT FOR INVITE`.
  4. 🛡️ Coordinator approval — team PENDING tới khi duyệt.
  5. 🏆 Track & compete — track gán ở Setup rồi thi.
- Điều hướng **Back / Next**, bước cuối **"LET'S GO 🚀"**; góc trái **SKIP TOUR**.
- `TourProvider`: `useTour() → { openTour, maybeAutoStartTour }`. `maybeAutoStartTour` chỉ mở khi chưa có `localStorage['sealOnboardingDone']`. Đóng/skip/finish → set cờ done. Bọc **ngoài** `RulesProvider` để Rules có thể `useTour`.
- **Re-watch:** nút **"? How it works"** trên `NoTeamDashboard` gọi `openTour()`.

### 4.6 Thanh tiến trình sống — `ParticipantJourneyBar.tsx`
- Display-only, đặt **phía trên** ở cả `NoTeamDashboard` (`team={null}`) và `ExistingTeamDashboard` (`team={team}`).
- **5 bước:** Joined → Team → Approved → Track → Compete; ô hiện tại **pulse**, ô xong **✓**, nhãn ngắn dưới mỗi ô + dòng phụ "Step X of 5 · <hint>".
- Suy ra bước active từ state thật:
```ts
team_id == null               → active = 1 (Team)
status !== 'APPROVED'         → active = 2 (Approved)
!trackName                    → active = 3 (Track)
eventStatus !== 'COMPLETED'   → active = 4 (Compete)
else                          → 5 (all done)
```

### 4.7 Sửa Event Detail — `EventDetailDrawer.tsx`
- **Bỏ** nút per-track "JOIN THIS TRACK → CREATE TEAM" (và hover clickable).
- Track section → **chỉ xem** (tên + mô tả) + **ghi chú**: track gán ở Setup (leader self-select / coordinator draw).
- Thêm **CTA chung** cuối drawer: **"REGISTER & CREATE TEAM"** (không kèm track) + dòng phụ "Your track is assigned later during Setup."
- Đổi prop `onCreateTeam: (eventId, trackId)` → `(eventId)`; cập nhật caller trong `ParticipantDashboard.tsx`.

---

## 5. Cấu trúc provider sau phiên (trong `App.tsx`)

```
ThemeProvider
  AuthProvider
    NotificationProvider
      PendingAccountsProvider
        TourProvider          ← mới (ngoài Rules để Rules dùng useTour)
          RulesProvider       ← mới
            RouterProvider
```

---

## 6. Kiểm thử

- `npx tsc --noEmit --ignoreDeprecations 6.0` → **exit 0** ở mọi mốc thay đổi.
  - (tsconfig có sẵn cảnh báo `baseUrl` deprecation làm `tsc` thường dừng — phải truyền `--ignoreDeprecations 6.0` để typecheck thật.)
- `npm test` (vitest) → **12/12 pass** (`permissions.test.ts` 7, `ConfirmDialog.test.tsx` 5) ở mọi mốc.

---

## 7. Cách test thủ công nhanh

1. Login participant **chưa có team** → popup quy chế hiện → đóng → **onboarding tour** chạy (chỉ lần đầu). Footer có **Competition Rules**; dashboard có **? How it works** để xem lại tour.
2. Xem **thanh tiến trình** trên cùng cập nhật theo: tạo team → bước Team ✓; coordinator duyệt → Approved ✓; track gán ở Setup → Track ✓.
3. Vào **My Team** ở event `IN_PROGRESS` → các nút sửa team biến mất + banner khóa; ở `OPEN`/`SETUP` thì đầy đủ + toast khi thao tác; team < 3 người → banner vàng.
4. Mở **View Detail** một event → track chỉ để xem + CTA "REGISTER & CREATE TEAM" (không chọn track).
5. Reset thử: xóa `localStorage['sealOnboardingDone']` để xem lại tour; xóa `sessionStorage['sealRulesSeen']` để popup quy chế hiện lại.

---

## 8. Ghi chú & việc còn lại

- **Gộp team ngẫu nhiên** hiện mới là **nội dung quy chế + banner cảnh báo** phía participant; hành động gộp thực tế thuộc nghiệp vụ **coordinator** (ngoài phạm vi phiên này).
- Gating là **FE-only** theo yêu cầu — backend chưa chặn các endpoint participant (update/invite/leave/transfer/removeMember/selectTrack). Nếu cần chặt chẽ, có thể thêm guard ở backend sau.
- `NoTeamDashboard` vẫn hiển thị **track chips** của event như **thông tin** (không click) — đã thống nhất giữ lại.
