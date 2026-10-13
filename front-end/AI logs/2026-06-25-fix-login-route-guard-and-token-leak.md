# AI Session Log — Chặn login chồng account + sửa rò token giữa 2 storage · FRONTEND

**Date:** 2026-06-25
**Project:** SEAL – Software Engineering Hackathon Management System
**Branch:** `develop`
**Scope (FE):** Sửa lỗi logic: user đã đăng nhập gõ thẳng `/login` trên URL vẫn vào lại được form login (không cần logout); nếu login tiếp account khác thì sinh **lỗi JDBC** ở backend do request mang nhầm danh tính. Sửa cả phần **chọn nhầm token** giữa `localStorage`/`sessionStorage`.

---

## PHẦN 0 — CONTEXT

- **Routing** ở `app/routes/index.tsx` dùng `createBrowserRouter`. Các route auth (`/login`, `/register`, `/forgot-password`) là **public** (nằm ngoài cây `RequireAuth`) → user đã đăng nhập vẫn truy cập được.
- **`app/providers/AuthProvider.tsx`**: `login(email, password, rememberMe)` → `apiFetch('/api/auth/login')` lấy token → `setToken()` → `apiFetch('/api/auth/me')` lấy profile/roles → `fetchTeamContext()` (gọi `teamsApi.getMy()` cho PARTICIPANT).
- **`shared/apiClient.ts`** — token helpers:
  - `getToken()` đọc **`localStorage` TRƯỚC**, rồi mới `sessionStorage`.
  - `setToken(t, remember=true)` → `localStorage`; `remember=false` → `sessionStorage`.
  - `clearToken()` xoá cả hai.
  - Token gắn thủ công vào header `Authorization: Bearer ...` ở mỗi `apiFetch`.

### Triệu chứng user báo
> "1 account đã login rồi nhưng gõ `localhost:5173/login` vẫn quay về được trang login mà không cần logout; nếu login tiếp acc khác thì lập tức sinh lỗi JDBC của acc log sau."

---

## PHẦN 1 — CHẨN ĐOÁN (2 lỗi kết hợp)

### Lỗi 1 — Không có guard chặn route auth khi đã đăng nhập
`/login`, `/register`, `/forgot-password` là public route, không kiểm tra `isAuthenticated`. → user đã đăng nhập gõ URL vào lại được form login. Đây là phần "logic" user thấy.

### Lỗi 2 — Rò/chọn nhầm token giữa 2 storage (GỐC của lỗi JDBC)
`login()` gọi `setToken()` **mà không `clearToken()` trước**. Kết hợp với việc `getToken()` ưu tiên `localStorage`:

1. Account **A** đăng nhập **có** "Remember me" → `tokenA` ở **`localStorage`**.
2. Không logout, gõ `/login`, đăng nhập Account **B** **không** tick "Remember me" → `setToken(tokenB, false)` ghi vào **`sessionStorage`**. **`tokenA` vẫn còn trong `localStorage`.**
3. Ngay sau đó `apiFetch('/api/auth/me')` → `getToken()` đọc `localStorage` trước → **trả về `tokenA`**, không phải `tokenB`.

⇒ Token gửi lên server, role/team context và state React **thuộc hai account khác nhau**. `fetchTeamContext()` và các request sau chạy với danh tính lệch ⇒ backend truy vấn với context không khớp → **bung lỗi JDBC** (cái user nhìn thấy).

> Hai lỗi **cộng hưởng**: Lỗi 1 mở đường cho kịch bản login chồng; Lỗi 2 biến kịch bản đó thành lỗi server.

---

## PHẦN 2 — GIẢI PHÁP

Đụng **3 file**, đều nhỏ và an toàn.

### 2.1. `shared/apiClient.ts` — `setToken` xoá storage còn lại trước khi ghi
```ts
export function setToken(token: string, remember = true): void {
  // Clear the *other* store first. getToken() reads localStorage before
  // sessionStorage, so a leftover token in localStorage (e.g. a previous
  // "remember me" session) would otherwise shadow a new sessionStorage token
  // and make every request carry the wrong identity.
  if (remember) {
    sessionStorage.removeItem(TOKEN_KEY);
    localStorage.setItem(TOKEN_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_KEY);
    sessionStorage.setItem(TOKEN_KEY, token);
  }
}
```
→ Không bao giờ còn 2 token song song ⇒ `getToken()` luôn trả đúng token vừa set.

### 2.2. `app/providers/AuthProvider.tsx` — `login()` xoá sạch phiên cũ trước khi xác thực
```ts
// Wipe any prior session before authenticating a new one — otherwise a
// leftover token / activeRole / user from the previous account can bleed
// into the new login and make subsequent requests carry the wrong identity.
clearToken();
localStorage.removeItem(ACTIVE_ROLE_KEY);
setCurrentUser(null);
setAvailableRoles([]);
setActiveRoleState(null);
// ...rồi mới apiFetch('/api/auth/login')
```
→ Kể cả vào được form login, phiên A bị xoá dứt khoát trước khi xác thực B.

### 2.3. `app/routes/index.tsx` — guard mới `RedirectIfAuthenticated`
```tsx
// Keeps already-authenticated users off the public auth screens (/login,
// /register, /forgot-password). Without this, an active session could open
// /login by URL and sign into a second account on top of the first.
function RedirectIfAuthenticated() {
  const { isAuthenticated, isLoading } = useAuth();
  if (isLoading) return null;
  if (isAuthenticated) return <Navigate to="/dashboard" replace />;
  return <Outlet />;
}
```
Bọc 3 route:
```tsx
{
  element: <RedirectIfAuthenticated />,
  children: [
    { path: "/login", Component: LoginPage },
    { path: "/register", Component: RegisterPage },
    { path: "/forgot-password", Component: ForgotPasswordPage },
  ],
},
```
> `/pending-approval` **không** bọc — trang này dành cho user đã đăng nhập nhưng chưa được duyệt, cần truy cập được.

---

## PHẦN 3 — PHẦN ĐÃ THỬ RỒI REVERT (`?switch_account=1`)

Trong phiên có thử thêm **cửa hậu** `/login?switch_account=1` để user *cố ý* đổi account mà không cần logout (mô phỏng Google/Slack). Guard đọc `useSearchParams`, nếu `switch_account === "1"` thì cho qua.

Sau khi cân nhắc, **user quyết định bỏ** vì:
- Hệ thống hackathon mỗi người 1 account, đổi account là hiếm.
- Luồng chuẩn **logout → login** đã đủ, rõ ràng và an toàn hơn.
- `switch_account` là cửa hậu bỏ qua chính cái guard vừa dựng → tăng edge case không cần thiết.

→ Đã **revert sạch**: gỡ logic `wantsSwitch` trong guard và bỏ import `useSearchParams`. Không để lại dấu vết.

---

## PHẦN 4 — LUỒNG SAU KHI SỬA

```
[đã đăng nhập] gõ /login trên URL
   └─ RedirectIfAuthenticated: isAuthenticated = true
        └─ <Navigate to="/dashboard" replace/>   ← không vào được form login

[muốn đổi account]
   └─ Logout (xoá token + state) → /login (giờ vào được vì isAuthenticated = false)
        └─ login(B): clearToken() + reset state TRƯỚC → /api/auth/login → setToken(tokenB, …)
             (sessionStorage/localStorage chỉ còn tokenB) → /api/auth/me dùng đúng tokenB
                  → không còn lẫn danh tính → KHÔNG lỗi JDBC
```

| Tình huống | Trước | Sau |
|---|---|---|
| Đã login, gõ `/login` | Vào lại form login | → redirect `/dashboard` |
| Login đè account khác | Token A che token B → lỗi JDBC | Bị chặn ở guard; nếu logout rồi login thì sạch sẽ |
| Remember-me A rồi login B (không remember) | `getToken()` trả tokenA | trả đúng tokenB |
| `/pending-approval` | Truy cập được | **không đổi** |
| Khách chưa đăng nhập | Vào `/login` bình thường | **không đổi** |

---

## PHẦN 5 — KIỂM THỬ

- `npx vite build` (tại `front-end/src/seal-web`) → **✓ built**, không lỗi. Chạy lại sau mỗi lần sửa và sau khi revert `switch_account` → đều pass.
- `npx tsc --noEmit` báo `TS5101: baseUrl deprecated` — **cảnh báo tsconfig có sẵn từ trước**, không liên quan thay đổi này (cần cờ `--ignoreDeprecations 6.0` để bỏ qua). `vite build` dùng esbuild nên không vướng.
- Trực quan: **chưa** chạy dev server. Đề nghị user test thủ công: login A (tick Remember me) → gõ `/login` (phải bị đẩy về dashboard) → logout → login B.

---

## PHẦN 6 — FILE THAY ĐỔI (FE)

| File | Loại | Nội dung |
|---|---|---|
| `shared/apiClient.ts` | sửa | `setToken` xoá token ở storage còn lại trước khi ghi → triệt rò token |
| `app/providers/AuthProvider.tsx` | sửa | `login()` `clearToken()` + reset `currentUser`/`availableRoles`/`activeRole` ngay đầu hàm |
| `app/routes/index.tsx` | sửa | Thêm guard `RedirectIfAuthenticated`, bọc `/login` `/register` `/forgot-password` |

> Tất cả mới ở **working tree, chưa commit**.

---

# PHỤ LỤC A — GIẢI THÍCH CHI TIẾT

## A.1. Vì sao "lỗi JDBC" lại do frontend?
Backend `AuthService.login` là stateless (JWT), `@Transactional(readOnly = true)` — bản thân nó không sai. Nhưng khi frontend gửi **token của account A** kèm các thao tác đang nghĩ là **account B** (role/team context lệch), các query phía sau chạy trên dữ liệu không khớp ngữ cảnh → phát sinh lỗi tầng JDBC. Gốc rễ là **chọn nhầm token ở client**, không phải logic SQL.

## A.2. Vì sao chỉ sửa `setToken` là chưa đủ, cần thêm `clearToken` trong `login()`?
`setToken` mới đã đảm bảo không còn 2 token. Nhưng `login()` còn giữ **state React cũ** (`currentUser`, `availableRoles`, `activeRole` trong localStorage) của account A trong lúc xác thực B. Reset sạch ở đầu hàm để không có mẩu nào của A "rò" sang B (vd `activeRole` của A áp lên B đa-role).

## A.3. Vì sao tách `/pending-approval` ra khỏi guard?
Trang này phục vụ user **đã đăng nhập nhưng `approved = false`** — đúng đối tượng cần thấy nó. Nếu bọc trong `RedirectIfAuthenticated` thì user pending sẽ bị đá về `/dashboard` → sai luồng `RequireAuth` (vốn điều hướng họ tới `/pending-approval`).

## A.4. Hành vi này có đúng chuẩn ngành không?
Có. Google/Facebook/GitHub/AWS… khi đã đăng nhập mà gõ `/login` đều redirect khỏi form, không cho login chồng. Đổi account phải **logout trước**, hoặc dùng tính năng *multi-account* được thiết kế riêng (không phải gõ lại `/login`).

---

# PHỤ LỤC B — GHI CHÚ BẢO MẬT (ngoài phạm vi sửa)

Trong phiên có thảo luận: JWT đang lưu ở `localStorage`/`sessionStorage` → tiện nhưng **dễ bị XSS đọc token**. Chuẩn an toàn hơn là cookie `HttpOnly; Secure; SameSite` (đụng CORS/CSRF/`SecurityConfig`/`JwtAuthenticationFilter` + toàn bộ `apiFetch`). **Không** thực hiện trong phiên này — chỉ ghi nhận để cân nhắc sau. Việc này **độc lập** với bug đã sửa (bug là *chọn nhầm token*, không phải *nơi lưu token*).
