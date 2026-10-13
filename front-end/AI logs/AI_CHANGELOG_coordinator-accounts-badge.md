# AI Changelog — Coordinator Accounts Badge (Pending Count)

> **Branch:** `KhanhNLH-fix-coordinator-screen`
> **Ngày:** 2026-06-09
> **Phạm vi:** 4 files (1 file mới) · màn hình Coordinator → tab Accounts + sidebar
> **Công cụ:** Claude Code (AI assistant)

Tài liệu này ghi lại việc sửa lỗi **badge số "pending accounts"** ở sidebar coordinator, kèm lý do và cách hoạt động.

---

## 1. Bối cảnh & 2 lỗi đã gặp

### Lỗi 1 — Badge hiển thị sai số (1 thay vì 3)
Ở màn Coordinator, mục **Accounts** trong sidebar hiện badge `1`, trong khi dữ liệu thật có **3** account đang PENDING (tab "Pending (3)" trên trang).

**Nguyên nhân:** Badge ở sidebar đếm từ **mock data tĩnh** (`accountApprovals` trong `@/shared/mocks/mockData`), còn trang `CoordAccountsPage` lấy **dữ liệu thật** từ API `GET /api/users`. Mock chỉ có 1 pending → badge ra `1`.

### Lỗi 2 — Badge không cập nhật sau khi Approve (sai nguyên lý React)
Sau khi sửa lỗi 1 (cho sidebar tự fetch API), lại phát sinh lỗi: bấm **APPROVE** một account thì badge **không đổi**, phải **refresh trang** mới thấy số mới.

**Nguyên nhân:** Sidebar và trang Accounts mỗi bên **fetch và giữ một bản sao dữ liệu riêng**. Khi approve, trang chỉ cập nhật state cục bộ của nó; sidebar không hề biết. Đây đúng là vi phạm nguyên lý React: **2 nguồn dữ liệu (state) trùng lặp cho cùng một con số** → không đồng bộ được.

---

## 2. Giải pháp — Single Source of Truth

Đưa `pendingCount` lên **một context dùng chung** để cả sidebar lẫn trang Accounts cùng đọc/ghi một state duy nhất. Khi trang mutate (approve/reject/restore), nó cập nhật context → sidebar re-render badge **ngay lập tức**, không cần refresh.

```
App
 └─ AuthProvider
     └─ NotificationProvider
         └─ PendingAccountsProvider   ← state dùng chung: pendingCount
             └─ RouterProvider
                 ├─ DashboardLayout (sidebar)  → đọc  pendingCount
                 └─ CoordAccountsPage          → ghi  pendingCount
```

---

## 3. Chi tiết từng thay đổi

### 3.1 — `src/app/providers/PendingAccountsProvider.tsx` (FILE MỚI)

Context giữ nguồn dữ liệu chung cho badge.

- **State:** `pendingCount`.
- **`refreshPendingCount()`**: gọi `GET /api/users`, đếm số PENDING rồi set state.
- **`setPendingCount()`**: cho phép trang Accounts đẩy con số chính xác của nó vào.
- **Seed khi đăng nhập:** `useEffect` theo `role` — nếu là `COORDINATOR` thì gọi `refreshPendingCount()` một lần, để badge **đúng ngay cả khi chưa mở trang Accounts**. Role khác → reset về 0.

Logic đếm PENDING **khớp đúng** `deriveStatus()` của `CoordAccountsPage`:
```ts
// PENDING = đã đăng ký nhưng chưa duyệt, và vẫn còn active (chưa bị reject)
const isApproved = u.isApproved ?? u.is_approved ?? false;
const isActive   = u.isActive   ?? u.is_active   ?? true;
return !isApproved && isActive;
```
Hỗ trợ cả **camelCase** lẫn **snake_case** từ backend.

### 3.2 — `src/app/App.tsx`

Bọc `<PendingAccountsProvider>` quanh `RouterProvider`, đặt **bên trong** `AuthProvider` (vì provider dùng `useAuth()` để biết role).

### 3.3 — `src/app/layouts/DashboardLayout.tsx`

- Bỏ đếm từ mock `accountApprovals`.
- Badge đọc thẳng từ context:
  ```ts
  const { pendingCount } = usePendingAccounts();
  ```

### 3.4 — `src/features/users/CoordAccountsPage.tsx`

Trang vẫn giữ `accounts` của riêng nó (vì cần đủ row để render bảng), nhưng **đẩy con số authoritative vào context** mỗi khi dữ liệu đổi:
```ts
const { setPendingCount } = usePendingAccounts();
useEffect(() => {
  if (!loading && !fetchError) setPendingCount(pendingCount);
}, [pendingCount, loading, fetchError, setPendingCount]);
```
Effect này chạy khi: **load xong lần đầu** và sau **mỗi** approve / reject / restore (vì các action đó cập nhật `accounts` → `pendingCount` tính lại → effect fire). Có guard `!loading && !fetchError` để không ghi đè 0 lúc đang tải.

---

## 4. Luồng hoạt động sau khi sửa

1. Coordinator đăng nhập → `PendingAccountsProvider` fetch `/api/users`, badge hiện đúng (vd: `3`).
2. Mở trang Accounts → trang load dữ liệu, đẩy lại con số (vẫn `3`, đồng bộ).
3. Bấm **APPROVE** 1 account → `accounts` cập nhật → `pendingCount` còn `2` → effect đẩy `2` vào context → **badge sidebar đổi ngay sang `2`**.
4. Không còn lệch số, không cần refresh.

---

## 5. Trạng thái & việc cần làm tiếp

- ✅ Type-check (`tsc --noEmit`) sạch (chỉ còn cảnh báo `baseUrl` deprecation có sẵn, không liên quan).
- ⬜ **Chưa commit.**
- ⬜ **Chưa chạy app verify trực quan** luồng approve/reject xem badge cập nhật tức thời.

---

## 6. Ghi chú cho lần sau

- Khi một con số xuất hiện ở **2 nơi** (badge + trang), đừng cho mỗi nơi tự fetch/giữ state riêng → sẽ lệch. Hãy **lift state lên context** làm single source of truth.
- Logic suy ra trạng thái PENDING/APPROVED/REJECTED nằm ở `deriveStatus()` (`CoordAccountsPage.tsx`) và đã được nhân bản trong `PendingAccountsProvider` — nếu đổi rule, sửa **cả hai** chỗ.
