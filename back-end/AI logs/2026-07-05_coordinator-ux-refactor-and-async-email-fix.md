# AI Session Log — 2026-07-05

**Chủ đề:** Tối ưu UX/UI toàn bộ màn Coordinator (clean & clear actions) + fix hiệu năng backend (async email khi approve/reject account)
**Phạm vi:** Full-stack — `front-end/src/seal-web` (chính) + `back-end/src/seal-api` (fix email)
**Trạng thái cuối phiên:** Toàn bộ thay đổi đã verify chạy được trên app thật, **chưa commit**.

---

## 0. Bối cảnh & mục tiêu

Điểm xuất phát: các màn Coordinator hiển thị quá nhiều nút thao tác cùng lúc (EDIT/DELETE lặp trên mỗi dòng, 2–3 nút lifecycle ở header, form "add" luôn mở) → rối, dễ bấm nhầm các thao tác nguy hiểm. Trong quá trình dọn dẹp còn lộ ra: nhiều hành động phá hủy **chưa có confirm nào**, format ngày hiển thị raw ISO, và cuối phiên là một vấn đề **hiệu năng backend** (approve account rất chậm).

Phương pháp làm việc xuyên suốt: **Q&A trước khi code** (dùng AskUserQuestion để chốt từng quyết định UX), tham khảo chuẩn quốc tế (NN/g, Carbon Design System, Primer, Linear), rồi **verify bằng cách drive app thật** (Playwright headless + đo runtime), không chỉ chạy test.

Stack: React 19 + Vite + TS 6 (pixel/retro design system, inline styles + token `C` theo `--c-*` CSS vars) / Spring Boot 3 + JPA + MySQL.

---

## 1. Component nền dùng chung

### 1.1 `PixelMenu` (mới) — `shared/components/PixelMenu.tsx`
Kebab/overflow menu dùng chung, generalize từ dropdown "MANAGE ▾" cũ trong `TrackProblemPanel`.
- **Render qua `createPortal` vào `document.body`** — vì `PixelCard` có `overflow: hidden` sẽ cắt menu inline-absolute. Position `fixed` tính từ `getBoundingClientRect()`, right-align, flip lên khi thiếu chỗ dưới, `zIndex: 500`.
- Đóng khi: `pointerdown` ngoài (cover touch), `Escape` (trả focus về trigger), click item, `scroll` (capture) + `resize`.
- Keyboard nav đầy đủ (`role="menu"/"menuitem"`, ArrowUp/Down/Home/End, aria-haspopup/expanded).
- API: `items: (PixelMenuItem | "divider")[]`, `label`, `triggerVariant`, `size`, `ariaLabel`, `align`, `minWidth`. Item hỗ trợ `danger` (đỏ), `disabled`, `icon`.
- **Test:** `PixelMenu.test.tsx` — 9 test (mở/đóng, click item, pointerdown ngoài, Escape+refocus, close-on-scroll, disabled inert, danger đỏ, ArrowDown focus item đầu, cycle skip disabled).
- Bug đã bắt & fix khi verify: menu flash 1 frame ở vị trí sai trước khi nhảy về anchor → ẩn panel tới khi đo xong (`pos = null` → visibility hidden).

`PixelButton` (`PixelComponents.tsx`) được thêm 3 prop ARIA optional (`ariaLabel`, `ariaHasPopup`, `ariaExpanded`) — backward compatible, để dùng làm trigger menu.

### 1.2 `ConfirmDialog` mở rộng — `shared/components/ConfirmDialog.tsx`
- Thêm `requireTypedText?: string` (**type-to-confirm** kiểu GitHub: nút confirm khóa tới khi gõ đúng, so sánh sau `.trim()`) + `typedTextLabel?`.
- Thêm **Escape-to-close** (bỏ qua khi `working`), **autofocus**: ô gõ khi có `requireTypedText`, ngược lại nút Cancel (Enter lạc không bao giờ kích hoạt hành động phá hủy).
- **Test:** thêm 5 test vào `ConfirmDialog.test.tsx` (confirm khóa tới khi gõ đúng, gõ sai vẫn khóa, Escape→onClose, Escape bị bỏ khi working, autofocus). 5 test cũ giữ xanh (props mới đều optional).

---

## 2. Refactor action buttons toàn bộ màn Coordinator

**Nguyên tắc thống nhất** (chốt qua nhiều vòng Q&A): *menu ⋯ chỉ dùng khi gom 2+ action; 1 action đơn = nút trực tiếp; hành động phá hủy/phụ thì hover mới hiện; hành động chính của queue thì luôn hiện.*

### 2.1 Events — `CoordEventsPage.tsx` (file lớn ~1600 dòng)
- **Header lifecycle:** từ 2–3 nút → **1 nút primary** (action "tiến", variant cyber) + **menu ⋯** chứa action lùi + CANCEL (đỏ, divider). Dùng `nextStatusActions()` (action tiến luôn đứng đầu). **CANCEL EVENT gõ tên event** (type-to-confirm).
- **Tracks/Rounds/Criteria rows:** EDIT+DELETE → menu ⋯ hover. Rounds giữ nút transition (ACTIVATE/CLOSE/REOPEN) hiện sẵn (là "next step" của dòng). **CLOSE round thêm confirm** (trước fire ngay). **Xóa track đang có team → gõ tên track**.
- **Form add thu gọn** sau nút `+ ADD TRACK / + ADD ROUND / + ADD CRITERIA`.

### 2.2 Problems — `TrackProblemPanel.tsx`
- Menu "MANAGE ▾" cũ → `PixelMenu`. Thêm confirm cho **Remove file** và **row RELEASE/RETRACT** (trước fire ngay). RELEASE ALL/RETRACT ALL giữ nguyên (đã có confirm).

### 2.3 Timers — `ContestTimerPanel.tsx`
- **STOP timer thêm confirm** danger (trước bấm là chết timer ngay, không resume được). PAUSE/RESUME/+5/EXTEND giữ fire ngay.

### 2.4 Scoring — `CoordScoringPage.tsx`
- **CALCULATE RANKINGS** thêm confirm thường. **PUBLISH RESULTS** type-to-confirm (**gõ tên round**) — trước cả hai fire ngay.

### 2.5 Prizes — `CoordPrizesPage.tsx`
- **ANNOUNCE** thay `window.confirm` bằng type-to-confirm (**gõ tên event**). **Delete prize** thêm confirm (trước xóa ngay).

### 2.6 Judges — `CoordJudgesPage.tsx`
- Chip ✕ gỡ mentor/judge **thêm confirm** (trước gỡ ngay không hỏi).

### Tier type-to-confirm (giữ hiếm để không "nhờn" — theo NN/g)
CANCEL EVENT · ANNOUNCE PRIZES · PUBLISH RESULTS · DELETE TRACK (khi có team). Còn lại dùng ConfirmDialog thường / danger.

---

## 3. Dọn nhiễu thị giác (đợt 2)

Sau khi dọn nút, lớp nhiễu tiếp theo là **thẻ trạng thái lặp**. Quyết định qua Q&A:
- **Track card:** gộp 3 tín hiệu ("0 teams" + "Needs 2 teams" + viền đỏ) → **1 chip `0/2 teams`** (đỏ↔xanh) + thêm ô stats tổng **`TRACKS READY 0/4`**.
- **Rounds row:** bỏ badge trạng thái (trùng ý với nút transition), thay bằng **viền trái màu** (xanh=active / vàng=pending / đỏ=closed / xanh dương=finalized); chỉ trạng thái không-có-action mới hiện chữ inline (`· FINALIZED`).
- **Track card thu gọn được** (accordion, kiểu "hamburger"): 1 dòng gọn, click để xổ description + team list; **drop zone bọc cả card** (kéo team thả vào card gọn vẫn nhận, tự mở ra).

### 3.1 Đổi icon menu + mặc định expand
- Icon menu `⋮` (MoreVertical) → **glyph panel/list** (khớp phong cách nút sidebar) trong `PixelMenu`.
- Bỏ nút **EXPAND ALL / COLLAPSE ALL** ở tab Tracks; đảo logic state `expandedTracks` → `collapsedTracks` để **mặc định mở hết** (giữ EXPAND/COLLAPSE của tab Audit).

### 3.2 Date format — `eventUtils.tsx`
- `eventMeta` sửa 1 chỗ (áp cho cả header lẫn list): raw `2026-08-14T15:00:00 → ...` → **`14 Aug 2026, 15:00 → 16 Aug 2026, 06:59`** (`en-GB`, 24h, fallback nếu parse lỗi).

---

## 4. Hover-reveal (chuẩn Carbon/Primer/Linear)

Vấn đề: icon menu vẫn lặp trên mọi dòng. Chuẩn quốc tế = **hover/focus mới hiện**, kèm fallback bắt buộc cho touch/keyboard.
- CSS dùng lại được trong `styles/index.css`: `.row-action { opacity: 0 }`; hiện khi `.row-actionable:hover` **hoặc** `:focus-within`; `@media (hover: none)` → luôn hiện (touch). Dùng opacity (giữ chỗ) nên không nhảy layout.
- Áp cho tracks/rounds/criteria (Events) + Problems.
- **Verify (Playwright, đo computed opacity):** rest `0` → hover `1` → rời `0` → keyboard focus `1` → touch context `1`. Con trỏ ảo Playwright còn ở vị trí cũ gây "hover ảo" → fix test bằng `mouse.move(3,3)` trước khi đo.

---

## 5. Fix anti-pattern "menu 1 item" + duyệt nhanh

User nhận ra: menu ⋯ chứa **đúng 1 action** (Teams Disqualify, Accounts Reject, Prizes Delete) là anti-pattern (giấu 1 action sau click thừa). Chốt qua Q&A:
- **Accounts / Teams (chờ duyệt):** bỏ menu — Approve hiện sẵn, **Reject nút đỏ hover mới hiện**.
- **Teams (đã duyệt):** bỏ menu — **Disqualify đưa vào panel chi tiết khi mở row** (hiện thẳng, không hover — vì mở detail đã là chủ đích).
- **Prizes:** Save hiện, **Delete nút đỏ hover mới hiện**.
- **Problems:** giữ menu (2 item Replace/Remove) + thêm hover.
- **Verify:** Accounts 0 menu sót, Reject opacity 0→1; Teams approved cột Actions trống, `DISQUALIFY TEAM` trong detail; opening ⋯ không toggle selection (stopPropagation OK).

---

## 6. Redesign màn Account Approvals

Yêu cầu: lấy **màu vàng (amber)** làm theme; click record → xổ detail + full action; record được chọn sáng viền + fade.

**Vòng 1 (đã làm rồi revert phần expand):** amber theme (title gradient vàng, header/khung/góc/viền amber — thêm `glowColor="amber"` vào `PixelCard`) + click-to-expand panel chi tiết (fade `pixelFadeIn`).

**Nhận xét của user → revert expand:** `PendingAccount` chỉ có 6 trường, **cả 6 đã nằm trên cột** → panel xổ ra gần như trùng, không đáng click. Bỏ expand.

**Vòng 2 (chốt cuối):**
- Giữ **theme amber**.
- Mỗi dòng: **menu ⋯ hover** (Approve + Reject) — giống Tracks/Rounds.
- **Duyệt nhanh = bulk select:** thêm cột **checkbox** (+ select-all header), click thân dòng cũng toggle chọn; khi có selection hiện **bulk bar** amber: `APPROVE SELECTED (N)` / `REJECT SELECTED (N)` / `CLEAR`, đi qua `ConfirmDialog` xác nhận. Bulk chạy `Promise.allSettled` (một vài lỗi vẫn xử lý phần còn lại, toast "N approved · M failed").
- File `CoordAccountsPage.tsx` được viết lại sạch (bỏ `expand`, `Field`, `fmtDateTime`).
- **Verify:** header amber `rgb(234,179,8)`; ⋯ hover 0→1; click dòng → "1 selected"; select-all → "4 selected"; `APPROVE SELECTED` mở confirm → cancel → PENDING không đổi (không mutate seed).

---

## 7. Fix hiệu năng backend — approve/reject account rất chậm

**Triệu chứng (user báo):** bấm approve xong request rất lâu.

**Root cause (đọc code):**
- `event/AccountApprovalEmailListener.java` dùng `@TransactionalEventListener(AFTER_COMMIT)` nhưng **KHÔNG `@Async`**, và cả project **không có `@EnableAsync`**.
- → `emailService...` → `mailSender.send()` (Gmail SMTP, **blocking**, `connectiontimeout=5000`) chạy **đồng bộ ngay trên thread request**. Mail creds không cấu hình (placeholder) → mỗi lần connect + fail mất ~5s.

**Fix:**
- Mới `config/AsyncConfig.java`: `@EnableAsync` + `ThreadPoolTaskExecutor` bean `emailExecutor` (core 2 / max 4 / queue 100, prefix `email-`).
- `AccountApprovalEmailListener` thêm `@Async("emailExecutor")` → email gửi trên pool nền sau khi transaction commit; request trả về ngay.

**Verify (before/after runtime — 2 instance):**
| | Endpoint | Latency |
|---|---|---|
| BEFORE (8080, code cũ) | `PUT /api/account-approvals/{id}/approve` | **5908 ms** |
| AFTER (8081, code fix) | idem | **189 → 42 → 53 ms** (~30–140× nhanh hơn) |

- Chạy instance đã-fix trên port 8081 (`mvnw spring-boot:run --server.port=8081`, `ddl-auto=none` nên an toàn, không đụng 8080 của user).
- **Bằng chứng async thực chạy (không mất email):** thread dump JVM 8081 (`jcmd <pid> Thread.print`) cho thấy **`email-1` / `email-2`** threads tồn tại, đã tiêu CPU 140–170ms rồi **idle/completed** → email gửi nền, không treo, không bị bỏ.

---

## 8. Files thay đổi

### Frontend (`front-end/src/seal-web/src`)
| File | Thay đổi |
|---|---|
| `shared/components/PixelMenu.tsx` | **MỚI** — overflow menu dùng chung |
| `shared/components/PixelMenu.test.tsx` | **MỚI** — 9 test |
| `shared/components/ConfirmDialog.tsx` | type-to-confirm, Escape, autofocus |
| `shared/components/ConfirmDialog.test.tsx` | +5 test |
| `shared/components/PixelComponents.tsx` | PixelButton ARIA props; PixelCard `glowColor="amber"` |
| `styles/index.css` | CSS hover-reveal (`.row-action`/`.row-actionable`) |
| `features/events/CoordEventsPage.tsx` | header primary+overflow, kebab rows, collapse cards, chip gộp, viền màu rounds, +ADD toggles, confirm CLOSE, typed cancel/delete-track |
| `features/events/eventUtils.tsx` | date format `eventMeta` |
| `features/events/TrackProblemPanel.tsx` | PixelMenu + confirms + hover |
| `features/events/ContestTimerPanel.tsx` | confirm STOP |
| `features/scoring/CoordScoringPage.tsx` | confirm CALCULATE, typed PUBLISH |
| `features/scoring/CoordPrizesPage.tsx` | typed ANNOUNCE, delete confirm + hover |
| `features/scoring/CoordJudgesPage.tsx` | confirm gỡ mentor/judge |
| `features/teams/CoordTeamsPage.tsx` | Reject hover, Disqualify vào detail |
| `features/users/CoordAccountsPage.tsx` | **viết lại** — amber theme, menu ⋯ hover, bulk select |
| `.claude/skills/verify/SKILL.md` | **MỚI** — repo verify skill cho seal-web |

### Backend (`back-end/src/seal-api/src/main/java/com/seal/hackathon`)
| File | Thay đổi |
|---|---|
| `config/AsyncConfig.java` | **MỚI** — `@EnableAsync` + `emailExecutor` |
| `event/AccountApprovalEmailListener.java` | thêm `@Async("emailExecutor")` |

---

## 9. Kiểm thử tổng hợp

- **Unit test:** 42/42 xanh (`npm test` trong `front-end/src/seal-web`).
- **Typecheck:** 0 lỗi mới. Baseline có ~5–11 lỗi **pre-existing** (TS6 `baseUrl` deprecation cần `ignoreDeprecations: "6.0"`; `.at()` vs lib ES2020; `ProfilePage.studentId/university`; `DashboardLayout.avatar_url`) — không phải do phiên này.
- **Verify runtime:** nhiều lượt Playwright headless drive các màn Events/Accounts/Teams/Scoring/Prizes/Judges (hover-reveal, kebab, bulk select, typed-confirm, date, collapse) + đo latency backend before/after + thread dump.
- Login seed: `coordinator@fpt.edu.vn` / `Test@1234`.

---

## 10. Việc còn lại / bàn giao

1. **CHƯA COMMIT** — toàn bộ diff (FE + BE) còn ở working tree. Đề xuất gom commit gọn theo nhóm: shared components → Events → các màn queue → backend async fix.
2. **Restart backend 8080** để nhận fix async (có devtools nên rebuild/save trong IDE tự restart). Instance đang chạy vẫn là code cũ (chậm) tới khi restart.
3. **Revert 4 account test** đã bị approve khi đo latency — `userId IN (11, 24, 45, 47)`. (Thao tác ghi thẳng DB của AI bị guardrail chặn đúng.) User chạy:
   ```sql
   UPDATE users SET is_approved = 0 WHERE user_id IN (11,24,45,47);
   ```
4. **Bug phụ nên fix:** `AccountService.approveUser` gọi `createNotification(...)` **2 lần** (nội dung "Account approved" gần trùng) → user nhận 2 thông báo + 2 ghi DB thừa. Nên bỏ 1.
5. **Có thể mở rộng thêm** (chờ user quyết): áp theme amber + bulk-select cho màn Teams; đồng bộ hover cho các màn còn lại nếu muốn.

---

## 11. Ghi chú môi trường (cho phiên sau)

- `npx tsc --noEmit` fail ở TS 6.0.3 vì `baseUrl` deprecation → dùng scratch tsconfig extends `tsconfig.app.json` + `"ignoreDeprecations": "6.0"`. Xem `front-end/src/seal-web/.claude/skills/verify/SKILL.md`.
- Dev server thường đã chạy sẵn: Vite 5173, Spring 8080. DB MySQL `seal_hackathon` (creds trong `back-end/src/seal-api/.env`).
- `npm test` phải chạy từ `front-end/src/seal-web` (không phải repo root).
- Gotcha: `PixelMenu` đóng khi scroll; async load resize trang gây scroll → chờ ~500ms trước khi mở menu trong test.

---
*Log tạo tự động bởi Claude (Opus 4.8) — phiên 2026-07-05. Lưu ở cả `front-end/AI logs` và `back-end/AI logs`.*
