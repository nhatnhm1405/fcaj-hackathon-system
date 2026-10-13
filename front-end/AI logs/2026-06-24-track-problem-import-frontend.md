# AI Session Log — Track Problem Import ("Đề thi") · FRONTEND

**Date:** 2026-06-24
**Project:** SEAL – Software Engineering Hackathon Management System
**Branch:** `KhanhNLH-track-problem-import`
**Scope (FE):** UI import đề thi. **Coordinator**: tab **"Problems"** riêng trong event console để upload/công bố đề từng track + nút **RELEASE ALL / RETRACT ALL** (đảm bảo công bằng). **Participant**: card đề trên dashboard để **xem** (mở tab) và **tải** đề khi đã công bố.

> BE liên quan: log `back-end/AI logs/2026-06-24-track-problem-import-backend.md`.

---

## PHẦN 0 — CONTEXT
- Coordinator event console: `features/events/CoordEventsPage.tsx` (tabs `Tracks/Rounds/Criteria/Audit`, dùng `PixelTabs`, `detailTab`).
- Participant: `dashboard/.../participant/screens/ExistingTeamDashboard.tsx` (gọi `teamsApi.getMy()` → `MyTeam`).
- `shared/apiClient.ts`: wrapper `apiFetch` (tự gắn `Authorization`, tự set multipart cho `FormData`). Có pattern `uploadAvatar` dùng `FormData`.
- `shared/components/ConfirmDialog.tsx`: popup xác nhận controlled (`title/message/warning/confirmLabel/variant/working/onConfirm/onClose`).
- `shared/components/PixelComponents.tsx`: palette `C` (`surface/surface2/border/text/textMuted/textDim/green/yellow/cyan/cyanBright`), `PixelButton` (`variant: primary|secondary|ghost|danger|cyber`, `size`, `disabled`), `PixelBadge`.

---

## PHẦN 1 — `shared/apiClient.ts`

Thêm interface `TrackProblem` + `MyTeam.trackId` + object `problemsApi`:

| Hàm | Endpoint | Ghi chú |
|---|---|---|
| `listForEvent(eventId)` | `GET /events/{e}/problems` | coordinator: trạng thái mọi track |
| `get(eventId, trackId)` | `GET /events/{e}/tracks/{t}/problem` | metadata (participant chỉ thấy khi released) |
| `upload(eventId, trackId, file)` | `POST …/problem` (FormData) | upload/thay |
| `release` / `retract` | `PUT …/problem/release` `/retract` | công bố / thu hồi |
| `remove` | `DELETE …/problem` | gỡ đề |
| `view(eventId, trackId)` | `GET …/problem/download` | **mở tab xem** (blob) |
| `download(eventId, trackId, fallbackName)` | `GET …/problem/download` | **tải về** (blob → "Save as") |

**Điểm kỹ thuật quan trọng — tải/ xem file có kiểm soát quyền:** file cần `Authorization` header nên **không thể** mở thẳng URL trong tab mới. Phải `fetch` kèm token → `blob` → object URL:
- `download`: tạo `<a download>` rồi click; tên file lấy từ `Content-Disposition` (`filenameFromContentDisposition`, ưu tiên RFC 5987 `filename*`), fallback `problem`.
- `view`: **mở `window.open('', '_blank')` TRƯỚC** (giữ "user gesture" tránh popup-blocker), `fetch` xong mới gán `win.location.href = objectURL`. PDF/ảnh → preview inline; docx/zip trình duyệt không xem được → tải về. `revokeObjectURL` sau 60s để tab kịp nạp.

---

## PHẦN 2 — `features/events/TrackProblemPanel.tsx` (mới)

Export **2 component**:

### 2.1. `TrackProblemsTab` (container — sở hữu state)
- `useEffect` tải `problemsApi.listForEvent(eventId)` → `items: TrackProblem[]`.
- Dẫn xuất: `allHaveProblem`, `releasableCount` (có đề & chưa release), `releasedCount`, `missingNames` (track chưa có đề).
- **Toolbar dùng chung** (chỉ khi `canManage`): 2 nút
  - `RELEASE ALL` — enabled khi `releasableCount>0`; bấm → mở `ConfirmDialog`. Nếu **chưa đủ đề** (`!allHaveProblem`) → dialog kèm **cảnh báo vàng** liệt kê track còn thiếu, nút `RELEASE ANYWAY`; đủ đề → confirm thường `RELEASE ALL`.
  - `RETRACT ALL` — enabled khi `releasedCount>0`; confirm `variant="danger"`.
  - `doReleaseAll/doRetractAll`: `Promise.all` gọi release/retract cho các track mục tiêu, **patch** từng item trong list, đóng dialog.
- Render mỗi track = card (header `trackName`) + `<TrackProblemPanel … />` (controlled).

### 2.2. `TrackProblemPanel` (controlled row — data từ props)
- Props: `eventId, trackId, problem, canManage, disabled, onChange, onRemoved`. **Không tự fetch** (parent sở hữu state) → nút dùng chung và từng dòng luôn nhất quán.
- Hàng: `PROBLEM` · **tên file (link → view)** · size · badge `RELEASED`/`NOT RELEASED`.
- Khi chưa có đề + canManage: nút `UPLOAD`.
- Khi có đề + canManage: nút chính `RELEASE`/`RETRACT` + dropdown **`MANAGE ▾`** (Replace file / Remove file). Dropdown tự đóng khi click ra ngoài (listener `mousedown`).
- `disabled` (= `bulkBusy` của parent) khóa nút khi đang chạy bulk.

---

## PHẦN 3 — `participant/screens/ParticipantProblemCard.tsx` (mới)
- Tự fetch `problemsApi.get(eventId, trackId)`.
- `released` (hasProblem && released) → tên file (link **view**) + size + badge `RELEASED` + nút **`DOWNLOAD`** (tải về làm bài).
- Chưa công bố → dòng mờ "Your track's problem hasn't been released yet…".
- Render trong `ExistingTeamDashboard` khi `team.status==='APPROVED' && team.trackId!=null` (lúc đó BE chắc chắn cho phép membership).

---

## PHẦN 4 — TÍCH HỢP `CoordEventsPage.tsx`
- Thêm tab `{ id: "problems", label: "Problems" }`, thứ tự cuối: **Tracks · Rounds · Problems · Criteria · Audit**.
- Block `{detailTab === "problems" && …}`: nếu `!showProblems` (event DRAFT/OPEN) → note "unlocks once registration closes (SETUP)"; ngược lại render `<TrackProblemsTab eventId canManage={canManageProblems} />`.
- Cờ: `showProblems = showTrackStats` (SETUP/IN_PROGRESS/COMPLETED), `canManageProblems = SETUP || IN_PROGRESS`.
- Tab **Tracks giữ nguyên sạch** (không nhúng panel đề vào card team).

---

## PHẦN 5 — LỊCH SỬ TINH CHỈNH UX (nhiều vòng theo phản hồi)
1. **v1**: panel đề nhúng trong **từng track card** ở tab Tracks, 4 nút (Download/Replace/Release-Retract/Remove), nhãn **tiếng Việt**.
2. **Đồng bộ ngôn ngữ** → đổi toàn bộ nhãn sang **tiếng Anh** (app vốn EN). `GỠ` → `REMOVE FILE` (tránh trùng nút REMOVE của track).
3. **Bỏ nút Download phía coordinator** → **tên file thành link bấm-để-xem**.
4. **Option B**: 1 nút chính + **MANAGE ▾** (gộp Replace/Remove) thay vì rải nút.
5. **Tách tab Problems riêng** (giống Tracks/Rounds) → tab Tracks hết rối; panel có không gian.
6. **Nút dùng chung RELEASE ALL/RETRACT ALL** (đảm bảo công bằng) — refactor panel thành **controlled** + container `TrackProblemsTab` dùng endpoint bulk.
7. **Popup confirm** cho Release/Retract All; **cảnh báo** khi release lúc chưa đủ đề.
8. **Xóa text mô tả/hint**: bỏ tiêu đề "Release all tracks together", bộ đếm "X/Y tracks…", và dòng hint dưới panel → toolbar chỉ còn 2 nút.
9. **`view` = mở tab xem** áp dụng cả 2 màn hình.

---

## PHẦN 6 — KIỂM THỬ
- `npx tsc --noEmit -p tsconfig.app.json --ignoreDeprecations 6.0` (tại `front-end/src/seal-web`) → các file phiên này **không lỗi**. 8 lỗi còn lại là **pre-existing** ở `DashboardLayout/InvitationsDrawer/ProfilePage` (không liên quan). Cờ `--ignoreDeprecations 6.0` cần vì `tsconfig` còn `baseUrl` (deprecated TS7) — vấn đề config có sẵn; `build` = `vite build` (esbuild, không typecheck).
- Trực quan: người dùng tự F5 dev server.

## PHẦN 7 — FILE THAY ĐỔI (FE)
| File | Loại |
|---|---|
| `shared/apiClient.ts` | sửa (+`problemsApi`, `TrackProblem`, `MyTeam.trackId`) |
| `features/events/TrackProblemPanel.tsx` | mới (`TrackProblemsTab` + `TrackProblemPanel`) |
| `features/events/CoordEventsPage.tsx` | sửa (tab Problems) |
| `dashboard/.../participant/screens/ParticipantProblemCard.tsx` | mới |
| `dashboard/.../participant/screens/ExistingTeamDashboard.tsx` | sửa (render card) |

---

# PHỤ LỤC A — GIẢI THÍCH CHI TIẾT CODE

## A.1. Vì sao tải/xem file phải qua blob (không mở URL trực tiếp)
`apiFetch` gắn `Authorization: Bearer <token>` từ localStorage. Nhưng `window.open(url)` hay `<img src>`/`<a href>` trỏ thẳng endpoint **không gửi header này** → BE trả 403. Nên phải:
```ts
const res = await fetch(url, { headers: { Authorization: `Bearer ${getToken()}` } });
const blob = await res.blob();          // blob.type = Content-Type từ response (vd application/pdf)
const objUrl = URL.createObjectURL(blob);
```
Sau đó `download` thì click `<a download>`, `view` thì điều hướng tab mới tới `objUrl`.

## A.2. `view` chống popup-blocker
```ts
const win = window.open('', '_blank');   // MỞ NGAY trong cùng nhịp click (user gesture)
const res = await fetch(...);            // await làm "mất" gesture nếu mở sau
const objUrl = URL.createObjectURL(await res.blob());
if (win) win.location.href = objUrl; else window.open(objUrl, '_blank', 'noopener');
setTimeout(() => URL.revokeObjectURL(objUrl), 60_000);  // tab cần thời gian nạp
```
Mở tab **trước** rồi mới `fetch` là điểm mấu chốt — nhiều trình duyệt chặn `window.open` nếu nó nằm sau `await` (không còn là phản hồi trực tiếp của click). PDF/ảnh: `blob.type` đúng MIME → tab preview inline; docx/zip: trình duyệt không render được → tự tải.

## A.3. Vì sao tách "controlled row" + "container"
Nút **RELEASE ALL** cần biết **tất cả track đã có đề chưa** → phải có cái nhìn tổng. Nếu mỗi panel tự fetch state riêng thì container không biết tổng thể. Giải pháp: **nâng state lên** `TrackProblemsTab` (một lần `listForEvent`), panel nhận `problem` qua props và báo thay đổi qua `onChange/onRemoved`:
```ts
function patch(updated: TrackProblem) {
  setItems(prev => prev?.map(it => it.trackId === updated.trackId ? updated : it) ?? prev);
}
```
→ Bulk action và thao tác từng dòng **cùng cập nhật một nguồn** `items` → UI luôn đồng bộ (badge từng dòng đổi ngay sau Release All).

## A.4. Logic công bằng của RELEASE ALL
```ts
const allHaveProblem  = total > 0 && withProblem === total;
const releasableCount = tracks.filter(t => t.hasProblem && !t.released).length;
```
- Nút enabled khi `releasableCount>0` (có cái để release) — **cố tình cho bấm cả khi thiếu đề** theo yêu cầu, để hiện cảnh báo.
- Trong `ConfirmDialog`: nếu `!allHaveProblem` → prop `warning` liệt kê `missingNames`, `confirmLabel="RELEASE ANYWAY"`; đủ đề → `confirmLabel="RELEASE ALL"`, không warning.
- `doReleaseAll` chỉ release các track `hasProblem && !released` (track chưa có đề bị bỏ qua tự nhiên).

## A.5. Dropdown MANAGE tự đóng
```ts
useEffect(() => {
  if (!menuOpen) return;
  const onDocClick = (e) => { if (menuRef.current && !menuRef.current.contains(e.target)) setMenuOpen(false); };
  document.addEventListener("mousedown", onDocClick);
  return () => document.removeEventListener("mousedown", onDocClick);
}, [menuOpen]);
```
Dropdown tự dựng (không kéo radix) để khớp style nút-chữ; chỉ gắn listener khi đang mở.

## A.6. Gate hiển thị tab Problems
`showProblems` (SETUP/IN_PROGRESS/COMPLETED) quyết định render tab; `canManageProblems` (SETUP/IN_PROGRESS) quyết định **hiện nút sửa**. Ở COMPLETED: tab vẫn mở nhưng `canManage=false` → toolbar ẩn, mỗi dòng chỉ còn link xem + badge (read-only) — coordinator là privileged nên `listForEvent`/download vẫn chạy. Khớp đúng gate phía BE.

## A.7. Vòng đời end-to-end (coordinator)
```
mở tab Problems → listForEvent → render toolbar + rows
upload (panel)   → upload → onChange(patch) → row đổi badge NOT RELEASED
RELEASE (panel)  → release → onChange(patch) → badge RELEASED
RELEASE ALL      → confirm (warning nếu thiếu) → Promise.all release → patch nhiều dòng
remove           → onRemoved → clear item (hasProblem=false)
```
**Nguyên tắc:** mọi giá trị dẫn xuất (`allHaveProblem`, `releasableCount`, `missingNames`) tính từ `items` → chỉ cần cập nhật `items` là toolbar + mọi dòng tự đồng bộ.
