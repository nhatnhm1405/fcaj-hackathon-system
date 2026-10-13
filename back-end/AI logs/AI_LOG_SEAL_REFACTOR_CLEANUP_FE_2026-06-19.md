# Nhật Ký Refactor — Dọn "Code Rác" (Phase 3: Front-end) — Ngày 19/06/2026

Phase cuối của đợt dọn rác trên nhánh `refactor/cleanup`. FE đúng là **phần rác nhất**: **53/130 module (≈41%) là dead code**, chủ yếu do code Figma-generated + các page cũ đã bị router thay. Xem thêm: `AI_LOG_SEAL_REFACTOR_CLEANUP_DB_2026-06-19.md`, `..._BE_2026-06-19.md`.

---

## 0. Bài học quan trọng nhất: detector dead-code phải verify kỹ
Lần khảo sát đầu, regex tìm orphan **sai** vì:
- **Khớp chuỗi con**: `EventsPage` khớp luôn `CoordEventsPage`/`AdminEventsPage`; `Footer` khớp `SealFooter`; `App` đếm lung tung.
- **Import có đuôi `.tsx`**: `main.tsx` viết `import App from "./app/App.tsx"` → regex `["'/]App["']` không khớp → `App.tsx` bị gắn cờ orphan oan.

→ Sửa thành regex theo **path-segment + đuôi tùy chọn**: `/<name>(\.tsx|\.ts)?["']`, đối chiếu với `app/routes/index.tsx` (router thật) và tính **transitive closure** của shadcn ui. Mọi kết luận xóa đều được verify lại bằng build/test. **Không tin detector thô khi xóa hàng loạt.**

---

## 1. Phương pháp xác định dead code (đã verify)

### 1.1. shadcn UI components
- Quét file nào import `ui/<name>` (bắt được cả `@/shared/components/ui/x` lẫn `../ui/x`).
- 7 component được feature dùng: `badge, button, card, input, select, separator, textarea`.
- Kiểm import nội bộ `./` của 7 cái + transitive: chúng **chỉ** kéo thêm `./utils` (chứa `cn()`).
- Xác nhận **không có barrel** `ui/index.ts` và **không** ai import từ folder root `.../ui` → đếm chính xác.
- ⇒ Bộ sống = 8 file; **40/48 chết**.

### 1.2. Pages/sections
- Đối chiếu `app/routes/index.tsx`: router dùng bản mới (LandingPage, LoginPage, RegisterPage, AboutPage, ContactPage, TeamPage, Coord/AdminEventsPage, JudgeScoringPage, TeamSubmitPage, CoordTeamsPage/TeamViewPage…).
- `LandingPage.tsx` **tự định nghĩa `HeroSection` inline** (dòng 283) + dùng `SealFooter` → các file landing `*Section`/`Navigation`/`Footer` cũ là rác.
- ⇒ **13 file** page/section cũ chết.

### 1.3. Assets
- Quét tham chiếu từng file trong `src/imports/`. Ảnh team/Hero/Hackathon*.jpg + `image.png` **đang dùng** (LandingPage/TeamPage) → giữ.
- ⇒ **4 orphan**: `pasted_text/{hackathon-management,hackathon-roles,navbar-and-footer-updates}.md` + 1 jpg lạ.

### 1.4. Đã loại trừ (không rác)
0 `console.log`/`TODO`/`debugger`; `dist/` không track; có sẵn FE `.gitignore`; mọi shared non-ui component + page trong router đều sống.

---

## 2. Đã xóa (57 file, −8302 dòng)
| Nhóm | Số file | Ghi chú |
|------|---------|---------|
| shadcn UI không dùng | 40 | giữ 8: badge/button/card/input/select/separator/textarea/utils |
| Page/section cũ bị router thay | 13 | xem mapping ở §1.2 |
| Asset Figma mồ côi | 4 | pasted_text/*.md + 1 jpg |

---

## 3. Verify sau khi xóa (chốt chặn an toàn)
- `tsc -p tsconfig.app.json --noEmit`: **0 lỗi mới**. (Lần đầu bị TS5101 `baseUrl` deprecated làm abort; chạy lại với `--ignoreDeprecations 6.0` lộ ra **7 lỗi CÓ SẴN** ở `DashboardLayout.tsx`/`ProfilePage.tsx` — `avatar_url`/`studentId`/`university`/null-check, không liên quan refactor.)
- **Verify lỗi có sẵn**: stash phần xóa → chạy tsc baseline cũng đúng **7 lỗi** → xóa của tôi không thêm lỗi nào.
- `npm run build` (vite): **PASS** (1787 modules, không import treo).
- `npm test` (vitest): **28/28 PASS** (trackStats 16, permissions 7, ConfirmDialog 5).

> Ghi chú: 7 lỗi type có sẵn nằm ngoài phạm vi dọn rác → để lại; có thể xử lý ở task sửa type API riêng.

---

## 4. Các commit Phase 3 (nhánh `refactor/cleanup`, không kèm Co-Authored-By)
- `refactor(web): remove 40 unused shadcn/ui components`
- `chore(web): remove orphan Figma import assets`
- `refactor(web): remove pages/sections superseded by the router`

---

## 5. Trạng thái & việc còn lại
- Working tree sạch; 3 phase (DB/BE/FE) đã xong trên `refactor/cleanup`.
- **Trước khi merge về `develop`**: rebase lên `develop` mới nhất (FE churn cao — canh lúc team ít sửa để giảm xung đột), rồi `develop → main`.
- Follow-up tùy chọn (ngoài dọn rác): sửa 7 lỗi type có sẵn; tách `TeamService` (BE); đổi tên `AssignmentController` (BE); cân nhắc Figma leftover docs (`Attributions.md` x2, `guidelines/Guidelines.md`, `default_shadcn_theme.css`).
