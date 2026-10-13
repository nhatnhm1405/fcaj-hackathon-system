# AI Changelog — Coordinator Events Screen

> **Branch:** `KhanhNLH-fix-coordinator-screen`
> **Commit:** `ff53823` — _"fix: delete dropdown in sidebar and navbar of event, map api of event track round and fix flow status event"_
> **Ngày:** 2026-06-09
> **Phạm vi:** 3 files · +585 / −140 dòng
> **Công cụ:** Claude Code (AI assistant)

Tài liệu này ghi lại toàn bộ thay đổi do AI thực hiện trên màn hình **Coordinator**, kèm lý do và cách hoạt động.

---

## 1. Tổng quan các việc đã làm

| # | Việc | File chính |
|---|------|-----------|
| 1 | Xóa dropdown chọn event ở navbar & sidebar (coordinator) | `DashboardLayout.tsx` |
| 2 | Map API thật cho Events / Tracks / Rounds / Criteria (bỏ mock) | `CoordEventsPage.tsx` |
| 3 | Thêm tracks & rounds vào luồng Create Event | `CoordEventsPage.tsx` |
| 4 | Sửa status Event/Round khớp schema backend thật | `CoordEventsPage.tsx`, `apiClient.ts` |

---

## 2. Chi tiết từng thay đổi

### 2.1 — Xóa dropdown chọn event (`src/app/layouts/DashboardLayout.tsx`)

**Vấn đề:** Coordinator có dropdown "switch event" ở giữa navbar và ở đầu sidebar → sai flow quản lý event (coordinator quản lý event qua trang Events, không phải chọn 1 event context).

**Thay đổi:**
- Navbar: thêm điều kiện loại trừ COORDINATOR khỏi event switcher.
  ```ts
  const hasEventSwitcher = !isParticipant && currentUser.role !== "COORDINATOR";
  ```
- Sidebar (`EventContextBlock`): return null sớm cho COORDINATOR.
  ```ts
  if (role === 'COORDINATOR') return null;
  ```

**Không ảnh hưởng:** MENTOR và JUDGE vẫn giữ event switcher như cũ (họ cần chuyển đổi theo phân quyền).

---

### 2.2 — Map API cho trang Events (`src/features/events/CoordEventsPage.tsx`)

**Trước:** Trang chạy hoàn toàn bằng mock data (`@/shared/mocks/mockData`).

**Sau:** Gọi API thật qua `apiFetch`, có `loading` / `error` / `empty` states, theo đúng pattern của trang Accounts & Teams. Dữ liệu API được chuẩn hóa (normalize) để chịu được cả **camelCase** lẫn **snake_case** từ backend.

**Các endpoint được map:**

| Đối tượng | Method & Endpoint |
|-----------|-------------------|
| Events (list) | `GET /api/events` |
| Event (tạo) | `POST /api/events` |
| Event (đổi status) | `PUT /api/events/{eventId}` |
| Tracks | `GET` / `POST /api/events/{eventId}/tracks` |
| Rounds | `GET` / `POST /api/events/{eventId}/rounds` |
| Round (đổi status) | `PUT /api/events/{eventId}/rounds/{roundId}` |
| Criteria | `GET` / `POST /api/events/{eventId}/rounds/{roundId}/criteria` |

**Thay đổi cấu trúc đáng chú ý:**
- **Criteria chuyển sang per-round**: trong API mới, scoring criteria thuộc về từng round (không còn global như mock). UI thêm bộ chọn round trong tab Criteria.
- Bỏ field `max_teams` của Track (API `Track` không có field này).
- Luồng load dữ liệu:
  - `useEffect` mount → load danh sách events.
  - `useEffect` theo `selectedEventId` → load tracks + rounds của event đang chọn.
  - `useEffect` theo `selectedRoundId` → load criteria của round đang chọn.

---

### 2.3 — Thêm Tracks & Rounds vào luồng Create Event

**Mục tiêu:** Cho phép coordinator khai báo luôn tracks và rounds ngay khi tạo event.

**Cách hoạt động:**
1. Form Create Event có thêm 2 mục **staging**: `// tracks` và `// rounds` — thêm/xóa nhiều mục trước khi submit (lưu tạm ở state `draftTracks`, `draftRounds`).
2. Khi bấm **ADD EVENT** (`addEvent`):
   - `POST /api/events` → lấy `event_id` từ response.
   - Lần lượt `POST .../tracks` cho từng track đã staging.
   - Lần lượt `POST .../rounds` cho từng round (tự gán `orderNumber = i + 1`).
   - Chọn event vừa tạo → effect tự fetch lại tracks/rounds hiển thị ở detail panel.
3. Có cờ `creating` chống double-submit (nút hiện "CREATING..."), nút **CANCEL** reset form.

**Lưu ý:** Tracks/rounds là tùy chọn — vẫn tạo được event "trống" rồi thêm sau ở detail panel.

---

### 2.4 — Sửa Status khớp schema backend (quan trọng)

> ⚠️ **Bài học:** Có 2 bộ file schema trong repo và chúng **khác nhau**. Phải dùng đúng schema mà backend đang chạy.

**Nguồn chuẩn (ĐÚNG):** `back-end/database scripts/seal_hackathon.sql` (+ data từ `seal_seed.sql`)

**Nguồn gây nhầm (SAI cho app này):** `docs/database/MySQL|SQL server/01_seal_hackathon_ddl.sql`

| Đối tượng | Enum đúng (backend thật) |
|-----------|--------------------------|
| **Event.status** | `DRAFT`, `OPEN`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |
| **Round.status** | `PENDING`, `ACTIVE`, `CLOSED`, `FINALIZED` |

**Triệu chứng đã gặp:** Event `SEAL FUHCM Summer 2026` hiển thị **DRAFT** thay vì **IN_PROGRESS**, vì `normalizeEvent` ban đầu chỉ chấp nhận `[DRAFT, OPEN, ACTIVE, CLOSED]` (theo docs DDL) → giá trị `IN_PROGRESS` bị reject → fallback về `DRAFT`.

**Đã sửa:**
- `EventStatus` type + danh sách status hợp lệ trong `normalizeEvent`.
- Badge màu: `OPEN` (green), `IN_PROGRESS` (cyan), `COMPLETED` (blue), `CANCELLED` (red), `DRAFT` (gray).
- Vòng đời nút hành động: `DRAFT → OPEN → IN_PROGRESS → COMPLETED`, kèm **CANCEL** ở các trạng thái chưa kết thúc và **REOPEN** khi `CANCELLED`.
- Round badge/normalize về đúng `PENDING, ACTIVE, CLOSED, FINALIZED`; nút **ACTIVATE** chỉ hiện khi `PENDING`.
- `apiClient.ts`: sửa `HackathonEvent.status` type tương ứng.

---

## 3. Trạng thái & việc cần làm tiếp

- ✅ Type-check (`tsc`) sạch cho các file đã sửa.
- ✅ Đã commit (`ff53823`).
- ⬜ **Chưa chạy app verify trực quan** luồng Events (tạo event kèm tracks/rounds, đổi status).
- ⬜ Cần backend chạy thật để kiểm tra response shape (normalize đã chịu cả camelCase/snake_case nhưng nên xác nhận).

---

## 4. Ghi chú cho lần sau

- Mọi câu hỏi về **status/enum** → grep `back-end/database scripts/seal_hackathon.sql` **trước tiên**, đừng tin `docs/database/`.
- `src/shared/mocks/mockData.ts` thực ra đã khớp enum backend từ đầu — có thể tham chiếu khi nghi ngờ.
