# AI Log — Banner thông báo thống nhất + phủ toast cho toàn bộ CRUD

**Ngày:** 2026-06-19
**Phạm vi:** Frontend React (`front-end/src/seal-web`) — thuần UI/UX + lớp gọi API, **không đụng backend**
**Nhánh:** `develop`
**Trạng thái:** Hoàn tất, đã verify (vitest 28/28 · `tsc` không phát sinh lỗi mới)

> Phiên này gồm 3 việc nối tiếp nhau qua nhiều vòng Q&A:
> 1. **Thiết kế lại** thông báo (toast) cho đẹp & hợp theme pixel/tech.
> 2. **Tự bật banner** cho mọi thông báo hệ thống mới (trước khi nó nằm vào chuông).
> 3. **Đổi icon** sang bộ chuẩn quốc tế (Lucide) + **phủ toast** cho TẤT CẢ tác vụ CRUD
>    (thành công / lỗi / cảnh báo) ở những chỗ còn thiếu — giữ nguyên cái đã có.

---

## Bối cảnh — hiện trạng trước phiên

`NotificationProvider.tsx` có **2 hệ toast tách rời** + 1 inbox chuông:

| Thành phần | Vị trí | Dùng cho |
|------------|--------|----------|
| `addAuthToast` (kiểu iOS bo tròn, blur) | trên-cùng giữa, trượt xuống | chỉ login/logout |
| `addToast` | góc dưới-phải | feedback hành động (lưu/duyệt…) |
| Chuông (`NotificationBell` trong `DashboardLayout`) | navbar | đọc `/api/notifications` |

Vấn đề:
- 2 phong cách toast không đồng nhất, cái bo tròn iOS hơi lệch tông pixel/tech của app.
- Chuông **chỉ fetch lúc login/đổi user** — không poll, **không tự bật banner** khi có thông báo mới.
- Nhiều tác vụ CRUD chỉ báo lỗi **inline** (hoặc im lặng khi thành công), không có thông báo thống nhất.

---

## Quá trình Q&A & quyết định

### Vòng 1 — thiết kế banner

| Quyết định | Lựa chọn cuối |
|------------|---------------|
| Phong cách | **Glass/tech lai**: giữ blur + glow mềm + thanh đếm ngược, nhưng **góc gần như vuông** (radius ~3px), viền trên 2px, icon khung vuông, bớt độ "nảy" iOS |
| Vị trí | **Trên-cùng giữa, trượt xuống** (giữ như auth-toast) |
| Phạm vi gộp | **Gộp tất cả về 1 component banner** (auth + feedback + thông báo hệ thống) |
| Phát hiện thông báo mới | **Poll `/api/notifications` mỗi 25s** ở frontend (không đụng backend) |
| Tự ẩn | 4.5s, **rê chuột → tạm dừng** đếm; click ✕ để đóng |
| Click banner | **mở dropdown chuông** + đánh dấu đã đọc |
| Xếp chồng | tối đa **3** cái, dư hiện "+N thông báo nữa" |
| Màu theo loại | success=green · warning=yellow · info=cyan |

### Vòng 2 — sticker + phủ CRUD

| Quyết định | Lựa chọn cuối |
|------------|---------------|
| "Sticker quốc tế" | **Lucide line-icon** (`CheckCircle2` / `AlertTriangle` / `Info`), đơn sắc theo loại |
| Số loại | **Giữ 3 loại**; **lỗi CRUD dùng `warning` (vàng ⚠️)** — không thêm loại error đỏ |
| Phạm vi quét | **Toàn bộ mọi feature** — thêm success + lỗi + cảnh báo ở chỗ còn thiếu, **giữ nguyên cái đã có** |
| Nội dung lỗi | **Tiếng Anh**, tiêu đề kiểu `... FAILED`, message lấy từ `ApiError.message`, fallback `Something went wrong` |

---

## Phần 1 — Banner thống nhất (`NotificationProvider.tsx`, viết lại)

### Component & hành vi
- Gộp `Toast`/`AuthToast` thành **một type `Banner`** (có thêm `notificationId?` để liên kết với thông báo thật).
- `addToast` và `addAuthToast` **giữ nguyên chữ ký** → mọi trang đang gọi **không phải sửa**; cả hai cùng đẩy vào 1 stack banner trên-giữa.
- `BannerItem`: nền tối trong mờ (`rgba(13,17,23,0.92)`) + `backdrop-blur`, viền 1px theo loại + **viền trên 2px** accent, `border-radius: 3px`, **icon Lucide trong khung vuông**, **thanh đếm ngược** dưới đáy (keyframe `sealBannerShrink` trong `globals.css`), animation trượt mượt (cubic-bezier, giảm overshoot).
- `BannerContainer`: `position: fixed; top:0` giữa màn hình, newest-on-top, hiển thị tối đa 3 + chip "+N thông báo nữa".
- Tương tác: hover → `paused` (dừng cả timer lẫn animation thanh progress); ✕ → đóng; click thân → `activateBanner` (đánh dấu đã đọc nếu có `notificationId`, bật chuông, rồi đóng).

### Polling + diff (tự bật banner cho thông báo mới)
- `refresh()` gọi `/api/notifications`, sort mới→cũ, set vào state chuông.
- **Lần fetch đầu sau đăng nhập**: chỉ lập "baseline" (ghi nhớ ID đã có) → **không spam** banner cho backlog cũ.
- Các lần sau: ID nào chưa thấy & chưa đọc → **bật banner trước**, rồi vẫn nằm trong chuông như lịch sử. Duyệt oldest-first để cái mới nhất nằm trên cùng.
- `setInterval(refresh, 25000)` khi đang đăng nhập; reset toàn bộ (banner/seen/baseline) khi logout.

### Nối tín hiệu click → mở chuông
- Context thêm `bellOpenSignal: number` + `requestOpenBell()`.
- `NotificationBell` (`DashboardLayout.tsx`) lắng nghe `bellOpenSignal` qua `useEffect` để tự `setOpen(true)`.

---

## Phần 2 — Icon Lucide + helper trích lỗi

- `NotificationProvider.tsx`: thay glyph monospace (`✓ ⚠ ℹ`) bằng `KIND_ICON` (Lucide `CheckCircle2`/`AlertTriangle`/`Info`), tô màu theo accent của loại.
- `apiClient.ts`: thêm `apiErrorMessage(err, fallback)` — rút message từ `ApiError`/`Error`, fallback mặc định `Something went wrong`. Dùng chung cho mọi catch CRUD để nhất quán.

---

## Phần 3 — Phủ toast cho toàn bộ CRUD

**Nguyên tắc:** giữ nguyên mọi toast/banner inline đã có; chỉ **thêm** toast ở chỗ còn thiếu —
thành công (xanh ✓), lỗi (vàng `... FAILED` + message backend), cảnh báo validation (vàng ⚠️).

| Nhóm | File | Đã thêm |
|------|------|---------|
| **Events** | `events/AdminEventsPage.tsx` | lỗi create-event + lỗi action confirm (complete/reopen/approve/reject); cảnh báo thiếu tên |
| | `events/CoordEventsPage.tsx` | success+lỗi cho add track/round/criteria, update mode, round status, draw tracks, action confirm; cảnh báo thiếu tên |
| **Teams** | `teams/TeamViewPage.tsx` | lỗi cho 7 handler (rename/invite/accept/decline/remove/transfer/leave) |
| | `teams/TeamCreatePage.tsx` | cảnh báo thiếu field + success + lỗi tạo team |
| | `dashboard/.../CreateTeamScreen.tsx` | lỗi tạo team (success do `ParticipantDashboard` xử lý) |
| | `dashboard/.../InvitationsDrawer.tsx` | success+lỗi accept/decline invite, request-to-join |
| | `dashboard/.../ExistingTeamDashboard.tsx` | lỗi pick track |
| | `teams/CoordTeamsPage.tsx` | success+lỗi approve/reject/disqualify team |
| **Scoring** | `scoring/CoordJudgesPage.tsx` | success+lỗi assign/remove judge & mentor |
| | `scoring/CoordScoringPage.tsx` | success+lỗi finalize (calculate) & publish results |
| | `scoring/JudgeScoringPage.tsx` | cảnh báo điểm sai range + success+lỗi save draft / submit final |
| **Submissions** | `submissions/TeamSubmitPage.tsx` | cảnh báo thiếu repo URL + success+lỗi nộp/cập nhật bài |
| **Users/Admin** | `users/AdminAccountsPage.tsx` | success+lỗi create/edit user, activate/deactivate; cảnh báo thiếu field |
| | `users/AdminRolesPage.tsx` | success+lỗi grant/revoke role; cảnh báo thiếu user/event |
| | `users/CoordAccountsPage.tsx` | success+lỗi approve/reject account |
| | `users/ProfilePage.tsx` | cảnh báo file ảnh + success+lỗi save profile, upload/remove avatar |
| **Auth** | `auth/CompleteProfilePage.tsx` | cảnh báo validation + success+lỗi hoàn tất hồ sơ |
| | `auth/RegisterPage.tsx` | cảnh báo validation + lỗi đăng ký |

---

## Danh sách file đã thay đổi

**Hạ tầng / dùng chung**
- `src/app/providers/NotificationProvider.tsx` — viết lại (banner thống nhất + Lucide + polling/diff + bellOpenSignal)
- `src/app/layouts/DashboardLayout.tsx` — `NotificationBell` lắng nghe `bellOpenSignal`
- `src/shared/apiClient.ts` — thêm `apiErrorMessage()`
- `src/styles/globals.css` — thêm keyframe `sealBannerShrink`

**Feature (toast CRUD)** — 17 file: AdminEventsPage, CoordEventsPage, TeamViewPage, TeamCreatePage, CreateTeamScreen, InvitationsDrawer, ExistingTeamDashboard, CoordTeamsPage, CoordJudgesPage, CoordScoringPage, JudgeScoringPage, TeamSubmitPage, AdminAccountsPage, AdminRolesPage, CoordAccountsPage, ProfilePage, CompleteProfilePage, RegisterPage.

---

## Kiểm thử (verification)

- `npx tsc --noEmit -p tsconfig.app.json --ignoreDeprecations 6.0` → **không phát sinh lỗi mới**.
  - Còn lại đúng **7 lỗi có sẵn từ trước** (đã đối chiếu baseline bằng `git stash`): `DashboardLayout` (`avatar_url`, null-check logout) và `ProfilePage` (`UserProfile.studentId/university`) — **không liên quan** thay đổi phiên này.
- `npx vitest run` → **28/28 pass** (trackStats 16 · permissions 7 · ConfirmDialog 5).

---

## Cách test thủ công

Tất cả tài khoản seed dùng chung mật khẩu **`Test@1234`**.

| Vai trò | Email |
|--------|-------|
| Admin | `admin@fpt.edu.vn` |
| Coordinator | `coordinator@fpt.edu.vn` |
| Judge | `judge.binh@fpt.edu.vn` |
| Participant (leader) | `leader1@fpt.edu.vn` |

1. **Backend:** tạo+seed DB (`seal_schema.sql` → `seal_seed.sql`), rồi `cd back-end/src/seal-api && ./mvnw.cmd spring-boot:run` (cổng 8080).
2. **Frontend:** `cd front-end/src/seal-web && npm run dev` (cổng 5173).
3. **Kịch bản:**
   - **Auth banner:** đăng nhập → "WELCOME BACK"; đăng xuất → "LOGGED OUT".
   - **Cảnh báo validation:** Register để trống → "MISSING FIELDS"; 2 mật khẩu khác → "PASSWORD MISMATCH".
   - **CRUD success:** Admin tạo event; Coordinator tạo track/round/criteria, duyệt team; Judge chấm điểm → banner xanh.
   - **CRUD lỗi:** tạo track trùng tên (hoặc tắt backend giữa chừng) → banner vàng "... FAILED".
   - **Thông báo mới tự bật (polling 25s):** cửa sổ A đăng nhập `leader1`; cửa sổ B (`coordinator`) duyệt team của leader1 → trong ≤25s cửa sổ A tự bật banner, rồi vào chuông.
   - **Tương tác:** hover dừng đếm; ✕ đóng; click thân → mở chuông + đánh dấu đã đọc; tạo nhanh ≥4 cái → xếp chồng 3 + "+N thông báo nữa".

---

## Ghi chú / việc còn lại (đề xuất)

- **Chưa có test tự động** cho `NotificationProvider` (polling/diff/không spam baseline). Có thể bổ sung bằng `@testing-library/react` + mock `notificationsApi`.
- Polling 25s là giải pháp FE-only; nếu cần real-time thật, sau này có thể thêm SSE/WebSocket ở backend.
- 7 lỗi `tsc` có sẵn (avatar_url / UserProfile fields / null-check) là nợ kỹ thuật cũ — nên dọn trong một phiên riêng.
