# ★ AI LOG — Auto-select event mặc định ở trang Events (Admin & Coordinator) — 2026-06-17

> Phiên làm việc **frontend-only**: đổi quy tắc highlight event mặc định khi mở trang
> Events. Thay vì sáng "event mới-tạo-nhất (trừ DRAFT)", giờ **ưu tiên event đang chạy
> (`IN_PROGRESS`)**, không có thì lấy **event `COMPLETED` vừa kết thúc gần nhất**.
> Áp dụng cho **cả** `AdminEventsPage` và `CoordEventsPage`.

---

## 1. Bối cảnh & vấn đề

Cả 2 trang Events dùng chung 1 logic chọn mặc định (trong `loadEvents` / `useEffect` mount):

```js
// API trả newest-first theo createdAt → lấy event non-DRAFT mới tạo nhất
const latest = rows.find(e => e.status !== 'DRAFT') ?? rows[0];
setSelectedEventId(prev => prev ?? latest?.eventId ?? null);
```

Hệ quả: trang luôn sáng event **mới tạo gần đây nhất**, kể cả khi đó chỉ là một event
`OPEN`/`SETUP` còn xa, trong khi event **đang thật sự diễn ra** (`IN_PROGRESS`) lại không
được chọn. Người vận hành (Admin / Coordinator) thường quan tâm event đang chạy hoặc
event vừa kết thúc — nên muốn khung mặc định rơi đúng vào đó.

**Yêu cầu:** khi bấm vào mục Events, mặc định chọn:
1. Event đang `IN_PROGRESS`; nếu không có →
2. Event `COMPLETED` có **ngày gần nhất**.

---

## 2. Quyết định thiết kế (Q&A với người dùng)

| Vấn đề | Quyết định |
|--------|-----------|
| "Ngày gần nhất" của COMPLETED dựa trên trường nào | **`endDate`** muộn nhất (event vừa kết thúc gần đây nhất) |
| Có nhiều event cùng `IN_PROGRESS` thì chọn cái nào | Cái có **`endDate` muộn nhất** (cùng quy tắc với COMPLETED) |
| Không có cả IN_PROGRESS lẫn COMPLETED | **Giữ behavior cũ**: non-DRAFT mới nhất, else event đầu danh sách |
| Auto-select áp dụng khi nào | **Chỉ lần load đầu** (`prev ?? ...`) — người dùng tự chọn xong thì giữ nguyên |
| Phạm vi | **Cả** `AdminEventsPage` + `CoordEventsPage`, dùng chung 1 helper (tránh lặp logic) |

Lý do chọn "chỉ lần load đầu": nếu re-tính mỗi lần list reload, thao tác như **Complete
event** (làm mất IN_PROGRESS) có thể khiến khung **nhảy** sang event khác ngay dưới tay
người dùng → khó chịu. `prev ??` đảm bảo tôn trọng lựa chọn thủ công.

---

## 3. Quy tắc chọn mặc định (sau thay đổi)

```
rows (đã normalize)
  │
  ├─ có IN_PROGRESS?  ──► chọn cái endDate muộn nhất
  │
  ├─ có COMPLETED?    ──► chọn cái endDate muộn nhất (vừa kết thúc gần nhất)
  │
  └─ còn lại          ──► non-DRAFT đầu tiên (API newest-first) ?? rows[0]
```

- `endDate` rỗng / không hợp lệ → xếp xuống cuối (`-Infinity`), không gây crash.
- List rỗng → trả `undefined` → `selectedEventId = null` (không highlight gì).

---

## 4. Thay đổi theo file

Tất cả nằm trong `front-end/src/seal-web/src/`.

### `features/events/eventUtils.tsx` — **THÊM** helper dùng chung
- `pickDefaultEvent(rows: EventRow[]): EventRow | undefined` — triển khai quy tắc ở mục 3.
- Hàm phụ private: `endTime(ev)` (đổi `endDate` → timestamp, invalid = `-Infinity`) và
  `latestByEndDate(rows)` (reduce lấy `endDate` lớn nhất).

### `features/events/AdminEventsPage.tsx` — **SỬA**
- Import thêm `pickDefaultEvent`.
- Trong `loadEvents`, thay 2 dòng tính `latest` cũ bằng:
  ```js
  setSelectedEventId(prev => prev ?? pickDefaultEvent(rows)?.eventId ?? null);
  ```

### `features/events/CoordEventsPage.tsx` — **SỬA**
- Import thêm `pickDefaultEvent`.
- Trong `useEffect` load mount, thay logic `latest` cũ bằng cùng một dòng như trên.

> Lưu ý: ở Admin, nhánh tạo event mới vẫn `setSelectedEventId(created.eventId)` để nhảy
> sang event vừa tạo — hành vi này **không đổi** (độc lập với default-select).

---

## 5. Kiểm thử / verify

- `tsc --noEmit --ignoreDeprecations 6.0 -p tsconfig.app.json`:
  - **3 file của phiên này (`eventUtils.tsx`, `AdminEventsPage.tsx`, `CoordEventsPage.tsx`)
    không phát sinh lỗi nào.**
  - Còn vài lỗi **có sẵn từ trước**, không thuộc phiên này: `DashboardLayout.tsx`
    (`avatar_url`, `currentUser` possibly null) và `ProfilePage.tsx`
    (`studentId`/`university`). Project không chạy `tsc` trong build (`vite build`).
  - `tsconfig.app.json` báo deprecate `baseUrl` (TS6) → cần cờ `--ignoreDeprecations 6.0`
    khi typecheck thủ công; không ảnh hưởng build.

### Kịch bản verify thủ công (gợi ý)
Dữ liệu mẫu: Spring=`COMPLETED`, Summer=`IN_PROGRESS`, Fall=`DRAFT`.
1. Mở **Events** (Admin và Coordinator) → mặc định sáng **SEAL Summer 2026 (IN_PROGRESS)**.
2. Complete nốt Summer (không còn IN_PROGRESS) → reload trang → mặc định nhảy về
   **event COMPLETED có `endDate` muộn nhất**.
3. Bấm chọn event khác rồi thao tác khiến list reload → khung **giữ nguyên** lựa chọn
   thủ công (không bị auto ghi đè).

---

## 6. Việc còn lại / lưu ý

- Đây là thay đổi **thuần FE, không đụng backend / DB / API**.
- Lỗi `tsc` ở `DashboardLayout`/`ProfilePage` là **WIP có sẵn**, nằm ngoài phạm vi phiên này
  — chưa xử lý.
- Nếu sau này muốn auto-select **re-tính mỗi lần reload**, chỉ cần bỏ `prev ??` ở 2 chỗ gọi
  `pickDefaultEvent` (đã cân nhắc nhưng chốt không làm vì gây "nhảy khung").
