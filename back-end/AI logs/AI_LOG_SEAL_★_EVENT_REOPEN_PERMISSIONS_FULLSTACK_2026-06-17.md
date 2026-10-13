# ★ AI LOG — Phân quyền & Quản lý trạng thái Event: Admin System vs Coordinator (FULL-STACK) — 2026-06-17

> Phiên làm việc tách quyền vòng đời event giữa **System Admin** và **Event Coordinator**,
> bổ sung cơ chế **Reopen Request** (Coordinator xin mở lại → Admin duyệt), pop-up xác nhận
> bắt buộc cho mọi thao tác đổi status, và bộ test cho logic phân quyền + dialog.
>
> Log này gộp cả **backend (Spring)** lẫn **frontend (React)** vì tính năng xuyên suốt 2 tầng.

---

## 1. Bối cảnh & vấn đề

Trước phiên này, **Coordinator** (`EVENT_COORDINATOR`) nắm toàn bộ vòng đời event: tạo event và
đổi mọi trạng thái (kể cả Complete) qua `CoordEventsPage.tsx` → `PUT /api/events/{id}`. **Admin**
(`SYSTEM_ADMIN`) **không** có quyền chạm event (`@PreAuthorize("hasRole('EVENT_COORDINATOR')")`
trên cả `POST`/`PUT`), và backend chặn cứng reopen cho **mọi** role (transition `COMPLETED → {}`).
Chưa có cơ chế "yêu cầu mở lại".

Yêu cầu mới:
- **Admin**: Create / Complete / Reopen event, và duyệt các yêu cầu mở lại.
- **Coordinator**: KHÔNG create, KHÔNG complete, KHÔNG tự reopen; với event `COMPLETED` chỉ được
  **gửi yêu cầu mở lại** (không tự đổi status).
- Mọi thao tác đổi status phía Coordinator (và Request Reopen) phải qua **pop-up xác nhận**
  (kiểu giống `ApprovalModal` lúc duyệt account).
- Chặn **thật** ở backend (403), không chỉ ẩn/disable ở FE.

---

## 2. Quyết định thiết kế (Q&A với người dùng)

| Vấn đề | Quyết định |
|--------|-----------|
| Phạm vi | Làm **cả frontend + backend** (enforce 403 thật) |
| Lưu reopen-request | **Entity + REST endpoint mới** (`ReopenRequest`) |
| Màn quản lý event của Admin | **Trang mới** `/admin/events` (+ panel duyệt request) |
| Component xác nhận | Tái dùng **kiểu `ApprovalModal`** → trích thành `ConfirmDialog` chung |
| Auto-select khi mở trang Events | Sáng **event mới-tạo-nhất, trừ DRAFT** — áp dụng **cả Admin & Coordinator** |
| Định nghĩa "mới nhất" | Theo `createdAt` mới nhất (khớp sort mặc định của API) |
| Quyền **Complete** | **Chỉ Admin** (chốt ở vòng Q&A 2 — ban đầu để Coordinator vẫn complete) |
| Ngôn ngữ UI | **Tiếng Anh** cho toàn bộ chuỗi hiển thị trong các component đã sửa |
| Test | **Có** — thêm `vitest` + `@testing-library` + viết test phân quyền & dialog |

---

## 3. Mô hình quyền & vòng đời (sau thay đổi)

```
DRAFT ──► OPEN ──► SETUP ──► IN_PROGRESS ──► COMPLETED
                                  │  (Complete: Admin-only, endpoint riêng)
                                  ▼
COMPLETED ──► IN_PROGRESS   (Reopen: Admin-only, endpoint riêng)
Coordinator @ COMPLETED ──► gửi Reopen Request ──► Admin Approve ⇒ reopen
```

| Hành động | Admin | Coordinator |
|---|:---:|:---:|
| Create event | ✓ | ✗ (403) |
| OPEN / SETUP / START / CANCEL (PUT) | ✓ (cho phép) | ✓ (vận hành chính) |
| **Complete** (IN_PROGRESS→COMPLETED) | ✓ | ✗ (UI ẩn + 403/400) |
| **Reopen** (COMPLETED→IN_PROGRESS) | ✓ | ✗ (UI ẩn + 403/400) |
| **Request Reopen** | – | ✓ |
| Approve / Reject reopen-request | ✓ | ✗ |

Phòng thủ 2 lớp cho Complete & Reopen: (a) `@PreAuthorize` trên endpoint riêng (Admin) → Coordinator
**403**; (b) các transition này **không** nằm trong `TRANSITIONS` map nên PUT chung của Coordinator
cũng **400**.

---

## 4. Thay đổi theo file

### 4.1 Backend (`back-end/src/seal-api/.../com/seal/hackathon/`)

**Entity / Repository**
- `entity/ReopenRequest.java` — **MỚI**. Bảng `ReopenRequest`: `event` (FK), `requestedBy` (FK User),
  `reason?`, `status` (PENDING|APPROVED|REJECTED, default PENDING), `resolvedBy?`, `createdAt`, `resolvedAt?`.
- `repository/ReopenRequestRepository.java` — **MỚI**. `findByStatusOrderByCreatedAtDesc`,
  `existsByEvent_EventIdAndStatus`, `findFirstByEvent_EventIdOrderByCreatedAtDesc`.

**DTO**
- `dto/request/CreateReopenRequestRequest.java` — **MỚI** (`reason?`, `@Size(max=1000)`).
- `dto/response/ReopenRequestResponse.java` — **MỚI** (id/event/requester/reason/status/resolver/timestamps).

**Service**
- `service/ReopenRequestService.java` — **MỚI**: `create` (event phải COMPLETED, chặn trùng PENDING),
  `getForEvent`, `listPending`, `approve` (→ gọi `reopenEvent` + notify coordinator), `reject` (+ notify).
- `service/HackathonEventService.java` — **SỬA**:
  - `TRANSITIONS`: **bỏ** `IN_PROGRESS → COMPLETED` (giờ `IN_PROGRESS → {CANCELLED}`); giữ `COMPLETED → {}`.
  - **Thêm** `completeEvent(eventId)` (yêu cầu IN_PROGRESS → COMPLETED).
  - **Thêm** `reopenEvent(eventId)` (yêu cầu COMPLETED → IN_PROGRESS).

**Controller**
- `controller/HackathonEventController.java` — **SỬA**:
  - `POST /api/events` → `@PreAuthorize("hasRole('SYSTEM_ADMIN')")` (create = Admin-only).
  - `PUT /api/events/{id}` → `hasAnyRole('EVENT_COORDINATOR','SYSTEM_ADMIN')`.
  - **Thêm** `POST /api/events/{id}/complete` (SYSTEM_ADMIN).
  - **Thêm** `POST /api/events/{id}/reopen` (SYSTEM_ADMIN).
- `controller/ReopenRequestController.java` — **MỚI** (guard từng method, không class-level mapping).

**Database scripts** (`back-end/database scripts/`)
- `seal_schema.sql` — **thêm** `CREATE TABLE ReopenRequest (...)`.
- `migration_reopen_request.sql` — **MỚI**, `CREATE TABLE IF NOT EXISTS` (áp không phá dữ liệu).
- `seal_scripts.sql` — thêm dòng `SELECT * FROM ReopenRequest`.

### 4.2 Frontend (`front-end/src/seal-web/src/`)

- `shared/permissions.ts` — **MỚI**. Pure functions + hook `usePermissions()`:
  `canCreateEvent` (ADMIN), `canReopenEvent` (ADMIN), `canCompleteEvent` (**ADMIN**),
  `canRequestReopen` (COORDINATOR), `canChangeEventStatus` (ADMIN|COORDINATOR),
  `canManageReopenRequests` (ADMIN).
- `shared/components/ConfirmDialog.tsx` — **MỚI**. Pop-up xác nhận tái dùng (overlay + PixelCard +
  PixelButton), props: `title/message/warning/confirmLabel/variant/working/error/onConfirm/onClose`
  + `children` (vd ô nhập lý do) + `open`.
- `features/events/eventUtils.tsx` — **MỚI**. Type/normalize/badge/`eventMeta`/`nextStatusActions`
  (đã **bỏ** COMPLETED khỏi transitions) + `statusChangeCopy` (nội dung "hiện tại → đích" + cảnh báo).
- `shared/apiClient.ts` — **SỬA**: thêm `eventsApi.complete`, `eventsApi.reopen`, `reopenRequestsApi`
  (create/getForEvent/getPending/approve/reject) + type `ReopenRequest`.
- `features/events/CoordEventsPage.tsx` — **VIẾT LẠI**: bỏ form Create; confirm-gate mọi đổi status;
  **ẩn** nút COMPLETE (lọc theo `canCompleteEvent`); event COMPLETED hiện **"REQUEST REOPEN"** (kèm ô
  lý do) → confirm → gửi request → toast + badge **"AWAITING ADMIN REVIEW"**; auto-select latest-non-DRAFT.
- `features/events/AdminEventsPage.tsx` — **MỚI**: Create + Complete + Reopen + panel **Reopen Requests**
  (Approve/Reject qua confirm); auto-select latest-non-DRAFT.
- `app/routes/index.tsx` — **SỬA**: thêm route `/admin/events` (trong block `allowedRoles={["ADMIN"]}`).
- `app/layouts/DashboardLayout.tsx` — **SỬA**: thêm mục nav `Events` cho Admin + `getPageTitle`.
- UI: toàn bộ chuỗi hiển thị trong các component trên đã chuyển sang **tiếng Anh**.

### 4.3 Tests (frontend)

- Cài devDeps: `vitest`, `@testing-library/react`, `@testing-library/jest-dom`,
  `@testing-library/user-event`, `jsdom`.
- `vite.config.ts` — import từ `vitest/config` + khối `test` (jsdom, globals, setupFiles, css:false).
- `src/test/setup.ts` — **MỚI** (jest-dom matchers + cleanup).
- `package.json` — thêm script `test` (`vitest run`) + `test:watch`.
- `src/shared/permissions.test.ts` — **MỚI** (7 test: từng `canX()` theo mọi role + null/undefined).
- `src/shared/components/ConfirmDialog.test.tsx` — **MỚI** (5 test: confirm gọi onConfirm; cancel gọi
  onClose; `open=false` không render; hiện warning; `working` disable nút → onConfirm không chạy).

---

## 5. Hợp đồng API (mới / thay đổi)

| Method & path | Role | Mô tả |
|---|---|---|
| `POST /api/events` | **SYSTEM_ADMIN** | Tạo event (trước là EVENT_COORDINATOR) |
| `PUT /api/events/{id}` | COORDINATOR + ADMIN | Đổi status (trừ Complete/Reopen) / dates / mode / name |
| `POST /api/events/{id}/complete` | **SYSTEM_ADMIN** | IN_PROGRESS → COMPLETED (mới) |
| `POST /api/events/{id}/reopen` | **SYSTEM_ADMIN** | COMPLETED → IN_PROGRESS (mới) |
| `POST /api/events/{id}/reopen-requests` | EVENT_COORDINATOR | Gửi yêu cầu mở lại `{reason?}` (mới) |
| `GET /api/events/{id}/reopen-requests` | COORDINATOR + ADMIN | Request mới nhất của event (mới) |
| `GET /api/admin/reopen-requests` | SYSTEM_ADMIN | Hàng đợi PENDING (mới) |
| `POST /api/admin/reopen-requests/{id}/approve` | SYSTEM_ADMIN | Duyệt → reopen event (mới) |
| `POST /api/admin/reopen-requests/{id}/reject` | SYSTEM_ADMIN | Từ chối (mới) |

---

## 6. Kiểm thử / verify

- Backend: `./mvnw -DskipTests compile` → **BUILD SUCCESS** (sau toàn bộ thay đổi).
- Frontend: `npx vite build` → **EXIT 0** (1675 modules; chỉ còn warning chunk-size có sẵn).
- Tests: `npx vitest run` → **12 passed** (`permissions` 7 + `ConfirmDialog` 5).
- `tsc --noEmit` còn vài lỗi **có sẵn từ trước** (DashboardLayout `avatar_url`, ProfilePage
  `studentId/university`) — không thuộc file của phiên này; project không dùng `tsc` trong build.

### Kịch bản verify thủ công (gợi ý)
- Token Coordinator: `POST /api/events` → **403**; `POST .../complete` & `.../reopen` → **403**;
  `PUT` status `COMPLETED`/reopen → **400**; `POST .../reopen-requests` (event COMPLETED) → **200**,
  gửi lần 2 → **400** (chặn trùng).
- Token Admin: create / complete / reopen → **200**; `GET /api/admin/reopen-requests` thấy PENDING;
  approve → event về IN_PROGRESS, request APPROVED.

---

## 7. Luồng demo

1. **DB**: chạy `migration_reopen_request.sql` (giữ data) hoặc chạy lại `seal_schema.sql` (xoá data)
   để có bảng `ReopenRequest`.
2. **Admin** `/admin/events`: tạo event → (Coordinator vận hành tới IN_PROGRESS) → Admin `COMPLETE EVENT`
   → `REOPEN EVENT` hoặc duyệt request ở panel **Reopen Requests**. Mở trang sáng sẵn event mới nhất (trừ DRAFT).
3. **Coordinator** `/coordinator/events`: không có nút Create/Complete; mỗi đổi status đều bật `ConfirmDialog`;
   event COMPLETED → **REQUEST REOPEN** (kèm lý do) → confirm → toast *"Your reopen request was sent to
   System Admin."* + badge **AWAITING ADMIN REVIEW**.

---

## 8. Việc còn lại / lưu ý

- Đã thêm **devDependency mới** (vitest, @testing-library, jsdom) → `package.json` + `package-lock.json`
  thay đổi. `npm audit` cảnh báo vài lỗ hổng nhưng đều thuộc **devDeps test**, không vào bundle production.
- Notification khi có request **chưa** gửi tới Admin (Admin xem ở panel); chỉ notify Coordinator khi
  được duyệt/từ chối. Có thể bổ sung notify Admin sau (cần truy vấn danh sách SYSTEM_ADMIN).
- (Tuỳ chọn, chưa làm) refactor `ApprovalModal` trong `CoordAccountsPage` để dùng chung `ConfirmDialog`.
- Admin trang Events chỉ thao tác Create / Complete / Reopen + duyệt request; vận hành OPEN→SETUP→START
  vẫn do Coordinator (đúng phân vai).
