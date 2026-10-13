# AI Session Log — Bấm logo SEAL quay về trang Home đầy đủ · FRONTEND

**Date:** 2026-06-25
**Project:** SEAL – Software Engineering Hackathon Management System
**Branch:** `KhanhNLH-track-problem-import`
**Scope (FE):** Khi user đã đăng nhập **bấm logo SEAL trên navbar dashboard** → quay về **trang landing/home đầy đủ** (giữ nguyên mọi thành phần của trang home, **không** hiển thị sidebar dashboard).

> Thay đổi này **đảo lại** quyết định trong log `front-end/AI logs/2026-06-19-join-team-fix-landing-redesign-home-in-dashboard.md` (vốn cho user đã đăng nhập xem Home *bên trong* khung dashboard).

---

## PHẦN 0 — CONTEXT

- **Route landing `/`** xử lý bởi `LandingPageWrapper` trong `app/routes/index.tsx`. Đây là **public route** (nằm ngoài cây `RequireAuth`), nên user đã đăng nhập vẫn truy cập được.
- **`app/layouts/DashboardLayout.tsx`**: khung dashboard = `TopNavbar` (logo + brand + theme + chuông + user menu) + `<aside>` sidebar + `<main>` + `SealFooter`. Có state `collapsed` (thu/giãn sidebar) và biến sẵn `isDashboardRoute = location.pathname !== '/'`.
- **`features/landing/LandingPage.tsx`**: trang home công khai. Có **navbar riêng** (`NavBar`, các anchor Home/About/Events/Timeline/Gallery/FAQ) + đủ section (Hero/Features/Events/Timeline/Gallery/Sponsors/FAQ/CTA) + `SealFooter`. Prop `hideChrome?: boolean` để **ẩn navbar + footer riêng** khi nhúng vào khung khác. NavBar **tự xử lý trạng thái đăng nhập**: hiện nút **"Go to Dashboard"** khi `isAuthenticated`, ngược lại hiện **Login/Register**.

### Trạng thái trước khi sửa (2 vấn đề)
1. **Logo chưa hề clickable** — trong `TopNavbar`, logo chỉ là `<img>` + `<span>`, không có `onClick` → bấm không làm gì.
2. **Dù cho logo `navigate('/')` thì vẫn ra sidebar** — `LandingPageWrapper` bọc landing trong khung dashboard cho user đã đăng nhập:
   ```tsx
   const landing = <LandingPage hideChrome={isAuthenticated} navigate={...} />;
   return isAuthenticated ? <DashboardLayout>{landing}</DashboardLayout> : landing;
   ```
   → user đã đăng nhập vào `/` thấy Home **kèm navbar + sidebar dashboard**, và navbar riêng của Home bị ẩn (`hideChrome=true`).

---

## PHẦN 1 — QUÁ TRÌNH THẢO LUẬN & QUYẾT ĐỊNH (nhiều vòng)

Đây là phiên có **đổi quyết định giữa chừng** — ghi lại đầy đủ để truy vết:

1. **Đề xuất 2 phương án layout cho Home của user đã đăng nhập:**
   - **Option 1 — Landing công khai đầy đủ:** bỏ hẳn khung dashboard, render đúng Home như khách xem (navbar riêng + đủ section + footer). Quay lại app bằng nút **"Go to Dashboard"** có sẵn.
   - **Option 2 — Giữ top-navbar dashboard, chỉ ẩn sidebar:** vẫn giữ navbar dashboard, chỉ bỏ sidebar khi ở `/`.
2. **User chọn Option 2** (qua preview ASCII). → Triển khai Option 2.
3. **User phản hồi "không đúng logic" + "muốn quay về landing vẫn giữ nguyên các thành phần của trang home".** Nguyên nhân: Option 2 bọc Home trong khung dashboard với `hideChrome=true` ⇒ **navbar riêng của Home (các link Home/About/…) bị ẩn** ⇒ trang Home **không còn nguyên vẹn**.
4. **Đổi sang Option 1** — đúng yêu cầu "giữ nguyên thành phần trang home". → **Gỡ** phần Option 2 đã làm trong `DashboardLayout`, **giữ** phần logo clickable, và sửa `LandingPageWrapper` để **luôn** render Home standalone.

**Bài học:** "giữ nguyên các thành phần của trang home" = phải render `LandingPage` **với `hideChrome=false`** (có navbar + footer riêng), không nhúng vào khung dashboard. Option 2 (giữ navbar dashboard) mâu thuẫn với yêu cầu này vì nó **thay** navbar Home bằng navbar dashboard.

---

## PHẦN 2 — GIẢI PHÁP CUỐI (Option 1)

Chỉ đụng **2 file**.

### 2.1. `app/routes/index.tsx` — `LandingPageWrapper`
**Bỏ bọc `DashboardLayout`; luôn render Home standalone (`hideChrome` mặc định = false).**

```tsx
function LandingPageWrapper() {
  const navigate = useNavigate();
  // Everyone — visitors and logged-in users — gets the full standalone home page
  // with its own navbar + footer intact. The home navbar shows a "Go to Dashboard"
  // button when authenticated, so logged-in users can jump back into the app.
  return (
    <LandingPage
      navigate={(page) => {
        if (page === "auth") navigate("/login");
        else if (page === "register") navigate("/register");
        else if (page === "dashboard") navigate("/dashboard");
      }}
    />
  );
}
```
- Bỏ `const { isAuthenticated } = useAuth();` (không cần nữa — quyết định wrapping đã biến mất). `useAuth`/`DashboardLayout` vẫn được import vì còn dùng ở `RequireAuth`/`RoleGate`/`DashboardWrapper`.

### 2.2. `app/layouts/DashboardLayout.tsx` — logo clickable
**Bọc logo + brand trong `<button>` → `onNavigate("/")`.**

```tsx
{/* Logo + brand — clicking returns to the landing page ('/') */}
<button
  type="button"
  onClick={() => onNavigate("/")}
  title="Về trang chủ"
  style={{ display: "flex", alignItems: "center", gap: 10, background: "transparent", border: "none", padding: 0, cursor: "pointer" }}
>
  <div style={{ height: 72, overflow: "visible", flexShrink: 0, display: "flex", alignItems: "center" }}>
    <img src={sealLogo} alt="SEAL" style={{ height: 144, width: "auto", objectFit: "contain", filter: "drop-shadow(0 0 6px rgba(34,197,94,0.4))" }} />
  </div>
  <span style={{ /* …gradient brand text… */ whiteSpace: "nowrap" }}>
    SEAL Hackathon
  </span>
</button>
```
- `onNavigate` là prop sẵn có của `TopNavbar` (chính là `navigate` của react-router truyền từ `DashboardLayout`).
- `pageTitle` ("| Home") để **ngoài** vùng bấm — chỉ logo + brand mới điều hướng.

---

## PHẦN 3 — DỌN PHẦN OPTION 2 ĐÃ THỬ (revert)

Để không để lại code chết, đã **gỡ toàn bộ** phần ẩn-sidebar của Option 2 từng thêm vào `DashboardLayout.tsx` (vì `/` không còn đi qua layout này nữa):

| Đã thêm rồi gỡ | Trạng thái cuối |
|---|---|
| Prop `hideSidebar?: boolean` trong `TopNavbarProps` + destructure | đã gỡ |
| Bọc nút hamburger toggle bằng `{!hideSidebar && ( … )}` | đã gỡ → toggle về như cũ |
| `hideSidebar={!isDashboardRoute}` ở chỗ dùng `<TopNavbar>` | đã gỡ |
| Bọc `<aside>` bằng `{isDashboardRoute && ( … )}` | đã gỡ → sidebar render như cũ |

> `isDashboardRoute` là biến **có sẵn từ trước** (dùng cho highlight nav item), **không** xóa. Sau thay đổi, vì `/` không qua `DashboardLayout` nữa nên trong layout `isDashboardRoute` luôn `true` → hành vi y hệt bản gốc, không hại.

---

## PHẦN 4 — LUỒNG HOẠT ĐỘNG SAU KHI SỬA

```
[trang dashboard bất kỳ]
   └─ bấm logo SEAL (navbar) → navigate('/')
        └─ LandingPageWrapper → <LandingPage/> standalone (hideChrome=false)
             • navbar RIÊNG của Home (Home/About/Events/Timeline/Gallery/FAQ)
             • đủ section + footer riêng của Home
             • KHÔNG sidebar, KHÔNG navbar dashboard
             • navbar Home hiện nút "Go to Dashboard" (vì đã đăng nhập)
                  └─ bấm → navigate('/dashboard') → RequireAuth → dashboard theo role
```
- **Khách chưa đăng nhập:** không thay đổi — vốn đã nhận Home standalone (navbar hiện Login/Register).
- **Không dead-end:** đường quay lại app rõ ràng qua nút "Go to Dashboard" trên navbar Home.

---

## PHẦN 5 — KIỂM THỬ

- `npx tsc --noEmit --ignoreDeprecations 6.0` (tại `front-end/src/seal-web`) → **EXIT 0**, không lỗi.
  - Cần cờ `--ignoreDeprecations 6.0` vì `tsconfig.json` còn option `baseUrl` (deprecated, TS5101) — **vấn đề config có sẵn**, chặn `tsc` chạy nếu không có cờ. `build` = `vite build` (esbuild, không typecheck).
- Trực quan: chưa chạy dev server (đã đề nghị user nếu muốn xem trực tiếp).

---

## PHẦN 6 — FILE THAY ĐỔI (FE)

| File | Loại | Nội dung |
|---|---|---|
| `app/routes/index.tsx` | sửa | `LandingPageWrapper` luôn render `LandingPage` standalone (bỏ bọc `DashboardLayout`, bỏ `hideChrome`/`isAuthenticated`) |
| `app/layouts/DashboardLayout.tsx` | sửa | Logo + brand thành `<button>` → `onNavigate("/")` (giữ); đã gỡ hết phần ẩn-sidebar của Option 2 |

---

# PHỤ LỤC A — GIẢI THÍCH CHI TIẾT

## A.1. Vì sao Option 2 không thỏa "giữ nguyên thành phần trang home"
Trang Home gồm **navbar riêng + sections + footer**. Option 2 nhúng Home vào `DashboardLayout` và truyền `hideChrome={true}` ⇒ `LandingPage` **ẩn `<NavBar/>` và `<SealFooter/>` của nó** (xem `LandingPage.tsx`: `{!hideChrome && <NavBar … />}`). Khi đó user thấy **navbar dashboard thay cho navbar Home** → mất dải link điều hướng đặc trưng của Home. Các *section* vẫn render, nhưng "bộ mặt" Home (navbar) không còn ⇒ user đánh giá "không đúng logic / không nguyên vẹn".

Option 1 render `LandingPage` với `hideChrome=false` (mặc định) ⇒ giữ **đủ** navbar + footer riêng ⇒ đúng yêu cầu.

## A.2. Vì sao logo dùng `onNavigate` chứ không tự `useNavigate`
`TopNavbar` đã nhận sẵn `onNavigate` (map từ `navigate` của react-router ở `DashboardLayout`). Dùng lại prop này nhất quán với cách user-menu điều hướng ("Profile" cũng gọi `onNavigate('/profile')`), tránh gọi hook thừa trong component con.

## A.3. Routing — vì sao đổi `LandingPageWrapper` là đủ
`/` luôn map tới `LandingPageWrapper` (cấu trúc router **không đổi**). Trước đây wrapper *phân nhánh* theo `isAuthenticated` để bọc hay không bọc `DashboardLayout`. Bỏ nhánh đó ⇒ **mọi** lượt vào `/` đều ra Home standalone. Vì `/` nằm ngoài `RequireAuth`, user đã đăng nhập vẫn vào được bình thường.

## A.4. Tại sao không xóa luôn prop `hideChrome`
Sau thay đổi, **không còn nơi nào truyền `hideChrome={true}`** ⇒ prop trở nên "thừa" (luôn false). Cố ý **giữ nguyên** prop trong `LandingPage.tsx` (file ~1329 dòng) để **giảm rủi ro/churn**; có thể dọn sau nếu muốn. Không ảnh hưởng hành vi.

## A.5. Khách chưa đăng nhập — không hồi quy
`LandingPageWrapper` bản cũ trả `landing` (standalone) cho khách. Bản mới cũng trả `LandingPage` standalone cho **tất cả**. Với khách, NavBar của Home tự render Login/Register (nhánh `!isAuthenticated` bên trong `NavBar`). ⇒ trải nghiệm khách **giữ nguyên**.

---

# PHỤ LỤC B — TRƯỚC / SAU (tóm tắt hành vi)

| Tình huống | Trước | Sau |
|---|---|---|
| Bấm logo (navbar dashboard) | không phản hồi | → `/` (Home đầy đủ) |
| User đã đăng nhập vào `/` | Home **bên trong** khung dashboard (sidebar + navbar dashboard, navbar Home bị ẩn) | Home **standalone** (navbar + footer riêng, không sidebar) |
| Quay lại app từ Home | (không có nút rõ ràng) | nút **"Go to Dashboard"** trên navbar Home |
| Khách chưa đăng nhập vào `/` | Home standalone (Login/Register) | **không đổi** |
| Các trang dashboard khác | sidebar + navbar dashboard | **không đổi** |
