# AI Log — Coordinator console UI cleanups (dashboard, teams, accounts, rounds, timers, audit)

**Date:** 2026-07-05
**Branch:** `KhanhNLH-coordinator-ux-and-async-email`
**Role/area:** Coordinator console + shared UI components (Frontend `seal-web` only — không đụng backend)
**Author:** KhanhNLH (nguyenlekhanh2608@gmail.com)

---

## 0. Tổng quan phiên

Phiên gồm **6 task UI độc lập** trên console Coordinator (và vài shared component), làm nối tiếp theo yêu cầu người dùng qua từng screenshot. Mỗi task đều theo cùng quy trình: đọc code → Q&A chốt hướng (khi có nhiều cách) → sửa → `tsc` typecheck (scratch config `ignoreDeprecations: "6.0"`, exit 0) → drive UI bằng Playwright (login `coordinator@fpt.edu.vn` / `Test@1234`) + screenshot xác nhận.

| # | Screen | Việc chính | File |
|---|--------|-----------|------|
| 1 | Dashboard (Coordinator/Judge/Mentor) | Xóa đốm sáng radial (blob) trong thẻ số | `PixelComponents.tsx` |
| 2 | Teams | Thêm caret ▸/▾ mở rộng cho mỗi dòng | `CoordTeamsPage.tsx` |
| 3 | Account Approvals | Giữ tài khoản vừa approve ở **đầu list** | `CoordAccountsPage.tsx` |
| 4 | Events › Rounds | Gộp OPEN↔CLOSE + confirm, fix form Edit, xóa mô tả rác | `CoordEventsPage.tsx` |
| 5 | Events › Timers | Wheel gọn, bỏ +5 MIN, đồng hồ to hơn, bỏ blurb | `ContestTimerPanel.tsx`, `WheelTimePicker.tsx` |
| 6 | Events › Audit | Bỏ Expand/Collapse All, luôn hiện detail, render metadata đẹp | `CoordEventsPage.tsx` |

---

## 1. Dashboard — xóa đốm sáng radial trong thẻ số

**Bối cảnh:** Các dashboard (Coordinator/Judge/Mentor) dùng chung `CyberStatCard` với nhiều hiệu ứng cyber. Người dùng thấy rối mắt.

**Diễn biến quan trọng (ghi lại trung thực):** Ban đầu mình hiểu rộng và đã tinh giản hàng loạt (bỏ ngoặc góc L, glow box-shadow, gradient text, thanh cuộn gradient, glow ở button/progress) trên `PixelComponents.tsx` + 3 dashboard + `globals.css`. **Người dùng chỉnh lại phạm vi:** chỉ muốn **xóa đúng đốm sáng radial (blob) trong thẻ số**. → Mình **revert toàn bộ** các thay đổi thừa (không dùng `git checkout` vì các file đã có sửa từ trước phiên — đảo ngược thủ công từng edit) và **chỉ giữ 1 thay đổi**.

**Thay đổi cuối cùng — `shared/components/PixelComponents.tsx`:**
- `CyberStatCard`: xóa đúng `<div>` "BG radial blob" (`radial-gradient` góc dưới-phải). Mọi thứ khác (ngoặc góc, glow border, text-shadow số, gradient text tiêu đề, thanh cuộn) **giữ nguyên bản gốc**.

**Ghi chú:** `MentorContextCard` (thẻ EVENT/TRACK trên Mentor dashboard) cũng có blob tương tự nhưng **giữ nguyên** vì không phải "thẻ số" — đã báo người dùng, chờ nếu muốn đồng bộ sau.

---

## 2. Teams — caret mở rộng cho mỗi dòng

**Bối cảnh:** `CoordTeamsPage.tsx` cho click dòng để bung MEMBERS/DETAILS nhưng **không có chỉ báo trực quan**. Người dùng muốn nút "|>" giống mục Events.

**Nguồn tái sử dụng:** `CoordEventsPage.tsx` (track cards) dùng caret `{expanded ? "▾" : "▸"}`.

**Thay đổi — `features/teams/CoordTeamsPage.tsx`:**
- Ô Team đổi từ `{t.name}` → flex-row inline: caret `{expanded ? "▾" : "▸"}` (style khớp Events: `color C.textMuted, fontSize 11, width 10, flexShrink 0`) + tên team. Dùng lại biến `expanded` và `onClick` toggle đã có sẵn, không thêm state.

**Kiểm thử:** thu gọn → tất cả dòng `▸`; click 1 dòng → `▾` + xổ chi tiết.

---

## 3. Account Approvals — giữ tài khoản vừa approve ở đầu list

**Bối cảnh:** `CoordAccountsPage.tsx` là **hàng đợi PENDING** (`getPending()`); approve xong tài khoản **bị xóa khỏi list** (`dropFromQueue`).

**Q&A chốt:** *Giữ tài khoản vừa approve ở đầu list* — sau Approve không biến mất mà nhảy lên đầu với nhãn APPROVED; pending ở dưới. Reload thì rời queue (đúng bản chất hàng đợi — chấp nhận).

**Thay đổi — `features/users/CoordAccountsPage.tsx`:**
- Thêm state `approvedIds: Set<number>` (theo dõi account approve trong phiên).
- `approveLocally(ids)`: giữ account trong `accounts`, **reorder đưa lên đầu** (`[...moved, ...rest]`), thêm vào `approvedIds`, recompute pending count. `removeFromQueue(ids)`: dùng cho **reject** (xóa hẳn). Thay `dropFromQueue` cũ.
- `handleConfirm` (đơn) + `runBulk` (hàng loạt): approve → `approveLocally`, reject → `removeFromQueue`.
- `pendingTotal` = số account **không** thuộc `approvedIds`; badge "N PENDING" + `setPendingCount` (badge sidebar) đều chỉ đếm pending.
- Selection chỉ áp dụng cho pending: `selectableRows`, `selectedCount`, `allSelected`, `toggleAll` đều lọc bỏ approved.
- Render dòng approved: nền xanh nhạt + rail xanh trái + badge **APPROVED** sau tên, **ẩn checkbox + menu ⋯**, không selectable/không click toggle.

**Kiểm thử end-to-end:** đăng ký 3 account pending qua `POST /api/auth/register` (AAA, BBB, CCC) → approve **CCC** (đăng ký cuối) → CCC nhảy lên đầu với APPROVED, badge **3 → 2 PENDING**. Sau đó **dọn dữ liệu test**: reject AAA/BBB qua API để trả queue về trạng thái cũ (CCC đã approved, để lại — không có endpoint xóa).

---

## 4. Events › Rounds — OPEN↔CLOSE + confirm, fix form Edit, xóa mô tả rác

**Bối cảnh (3 vấn đề người dùng nêu):**
1. Bấm EDIT "lỗi": form dùng grid cứng `"2fr 70px 1fr 1fr 1fr 70px auto"` — 3 ô `datetime-local` (min-width ~170px) ép ô **Name còn ~2 ký tự** ("Pr"); form lại nằm cuối list không auto-scroll → tưởng không có gì xảy ra.
2. 3 nhãn trạng thái ACTIVATE / CLOSE / REOPEN rối — ACTIVATE và REOPEN gọi **cùng** `changeRoundStatus(id,'ACTIVE')`, chỉ khác chữ.
3. Mô tả rác: `⚠ No cut-off set — no team is marked…` và `Top N = teams advancing per track…`.
> Backend `RoundService.updateRound` không chặn gì — lỗi thuần UI.

**Q&A chốt:** gộp thành **OPEN ↔ CLOSE**; fix Edit bằng **wrap layout + auto-scroll + đổi "EDITING…" → "CANCEL"**.

**Thay đổi — `features/events/CoordEventsPage.tsx`:**
- **Trạng thái:** PENDING/CLOSED → nút **OPEN**; ACTIVE → nút **CLOSE**; FINALIZED không nút. Bỏ hẳn nhãn ACTIVATE/REOPEN. Cập nhật comment liên quan.
- **Confirm cho OPEN (bổ sung sau khi user báo "open chưa có popup"):** thêm `requestOpenRound(r)` dùng chung `openConfirm`/`ConfirmDialog` (giống `requestCloseRound`), variant `cyber`; nếu round đang CLOSED thì hiện warning "opening it lets teams submit again". Nút OPEN gọi `requestOpenRound` thay vì `changeRoundStatus` trực tiếp → **cả OPEN lẫn CLOSE đều confirm**.
- **Fix form Edit:** grid → `repeat(auto-fit, minmax(160px, 1fr))` (ô tự wrap, Name không bị bóp); cụm nút SAVE/CANCEL (ADD/CANCEL) tách xuống hàng flex riêng `marginTop: 12`. Thêm `useRef` (`roundFormRef`) + `useEffect([editingRoundId, showAddRound])` gọi `scrollIntoView({ behavior: "smooth", block: "nearest" })`. Nút row khi đang edit "EDITING…" → **"CANCEL"**.
- **Xóa rác:** bỏ biến `noCutoff` + block cảnh báo; bỏ div explainer "Top N = …". Giữ placeholder `"Empty = no cut-off"` của ô Top N.
- Import: thêm `useRef`.

**Kiểm thử:** 3 round hiện nút OPEN (đang CLOSED); Edit round 1 → cuộn tới form, Name = "Preliminary" (rộng 169px, sửa được), row hiện CANCEL; không còn 2 đoạn rác. OPEN → popup "Open this round?" (dismiss bằng Escape, không confirm thật).

---

## 5. Events › Timers — wheel gọn, nút đơn giản, đồng hồ to

**Bối cảnh:** tab TIMERS lệch/rối: `WheelTimePicker` (drum 5 hàng × 3 cột H/M/S) chiếm ~300px, lệch trái; bộ nút khi chạy có PAUSE · **+5 MIN** · EXTEND… · STOP (user thấy +5 MIN thừa).

**Q&A chốt:** *Giữ wheel nhưng thu nhỏ* (3 hàng, bỏ giây, căn giữa); *bộ nút = PAUSE · EXTEND · STOP* (bỏ +5 MIN, EXTEND mở ô nhập phút).

**Thay đổi — `shared/components/WheelTimePicker.tsx`:**
- `VISIBLE 5 → 3` (drum cao 120px thay vì 200px).
- **Bỏ cột giây** (cả drum lẫn ô type-in H/M/S → chỉ H/M); `set(nh, nm)` (bỏ tham số giây); backend vẫn nhận seconds=0. (Component này chỉ dùng ở tab TIMERS → an toàn.)

**Thay đổi — `features/events/ContestTimerPanel.tsx`:**
- Idle block **căn giữa** (`alignItems: center`).
- Bộ nút chạy: bỏ **+5 MIN**; `EXTEND…` → **EXTEND** mở 1 hàng nhỏ `Add [n] min` + **ADD TIME** (ô `<input type=number>` 1–720, thay vì bung wheel thứ hai); state `extendSec` → `extendMin`.
- (Bổ sung theo yêu cầu sau) **Bỏ blurb** "While running… locked." (gỡ khỏi `PHASES`, prop, và JSX) và hint **"Set a duration first."**; đồng hồ `CountdownDisplay size="lg"` (46px, trước 34px), tiêu đề 14→15px cho cân đối.
- (Bổ sung) Nút start luôn là **"START"** (bỏ nhánh "START AGAIN" khi STOPPED/EXPIRED).

**Kiểm thử:** không còn "+5 MIN", không còn cột "sec"; hàng EXTEND hiện "Add … min / ADD TIME"; đồng hồ to rõ; cả 2 card chỉ còn tiêu đề (không blurb).

---

## 6. Events › Audit — bỏ Expand/Collapse All, luôn hiện detail, render metadata đẹp

**Bối cảnh:** người dùng muốn (1) bỏ nút EXPAND ALL / COLLAPSE ALL, (2) luôn hiện detail (bỏ drop-down), (3) phần dữ liệu hiện dạng `{"event_name":"SEAL Spring 2026","status":"DRAFT"}` — **không phải bug backend** mà do code đổ **nguyên chuỗi JSON thô** `log.metadataJson` ra màn hình.

**Thay đổi — `features/events/CoordEventsPage.tsx`:**
- Xóa khối 2 nút EXPAND ALL / COLLAPSE ALL + state `expandedAudit` (không còn dùng) + caret ▸/▾ + cơ chế click-toggle.
- Mỗi audit entry **luôn hiển thị detail** (reason + metadata) ngay dưới header.
- Thêm helper module-level:
  - `humanizeAuditKey(key)` — `event_name` → "Event name".
  - `formatAuditValue(value)` — null → "—", object → `JSON.stringify`, còn lại → `String`.
  - `AuditMetadata({ json })` — `JSON.parse`; nếu là **object phẳng** → render các dòng "Nhãn  giá trị"; nếu không parse được / không phải object → **fallback về chuỗi gốc** (an toàn).

**Kiểm thử:** không còn EXPAND/COLLAPSE, không còn caret, không còn `{"` JSON thô; hiển thị đúng `Event name → SEAL Summer 2026`, `Status → OPEN`, `Before → OPEN`, `After → IN_PROGRESS`, `Started at → …` + reason in nghiêng.

---

## 7. Kiểm thử chung

- **Typecheck:** mỗi task chạy `npx tsc -p <scratch tsconfig extends tsconfig.app.json + ignoreDeprecations "6.0">` → **exit 0**, không thêm lỗi mới.
- **Drive UI:** Playwright headless (viewport 1440×1024), login Coordinator, điều hướng tới từng screen, assert text + chụp screenshot đối chiếu. Vite (5173) + backend (8080) đã chạy sẵn.
- **Không thao tác phá hủy** trên DB dev chung: các popup confirm (OPEN/CLOSE round, STOP timer, bulk approve) chỉ mở rồi Escape/Cancel; riêng luồng Account Approvals có tạo 3 account test rồi dọn lại.

## 8. Ghi chú / follow-up

- `MentorContextCard` vẫn còn blob radial (Task 1) — có thể bỏ cho đồng bộ nếu muốn.
- `WheelTimePicker` giờ chỉ H/M; nếu sau này cần đặt thời lượng theo giây thì phải khôi phục cột giây.
- Account Approvals: trạng thái "approved ở đầu list" chỉ tồn tại trong phiên (reload rời queue) — đúng thiết kế đã chốt.
- Còn 1 account test `CCC SortTest …` đã được approve trên DB dev (không có endpoint xóa) — vô hại.

## 9. Files changed

```
front-end/src/seal-web/src/shared/components/PixelComponents.tsx        (Task 1)
front-end/src/seal-web/src/shared/components/WheelTimePicker.tsx         (Task 5)
front-end/src/seal-web/src/features/teams/CoordTeamsPage.tsx            (Task 2)
front-end/src/seal-web/src/features/users/CoordAccountsPage.tsx         (Task 3)
front-end/src/seal-web/src/features/events/CoordEventsPage.tsx          (Task 4 + 6)
front-end/src/seal-web/src/features/events/ContestTimerPanel.tsx        (Task 5)
front-end/AI logs/2026-07-05-coordinator-console-ui-cleanups.md          (this file)
```
