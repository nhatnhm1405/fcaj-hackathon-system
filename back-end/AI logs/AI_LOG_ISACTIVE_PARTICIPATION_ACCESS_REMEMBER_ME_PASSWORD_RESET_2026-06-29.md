# AI LOG - isActive Participation Access, Remember Me, Password Reset Schema - 2026-06-29

Ngay ghi log: 2026-06-29

Cong cu ho tro: Codex

Pham vi phien lam viec:

- Doi lai cach dung cot `User.is_active` theo rule moi.
- Them luong participant xin cap quyen tham gia lai sau khi bi read-only.
- Them/hoan thien remember me tren frontend.
- Dong bo schema/seed cho cac bang moi.
- Sua DB local theo du lieu demo hien tai.
- Chay test/build de verify.

---

## 1. Ket luan nghiep vu cuoi cung

Cot DB van la:

- `User.is_active`

Khong them cot `readOnly`.

Rule cuoi cung:

- `isActive = true`: user dang active/writable, participant duoc thao tac binh thuong.
- `isActive = false`: participant o trang thai read-only, chi xem duoc, khong duoc create/update/delete cac chuc nang thi dau.
- Login khong bi chan bang `isActive`; login gate dung `isApproved`.
- Staff/judge/mentor/coordinator/system admin khong bi read-only filter khoa thao tac theo `isActive`.

Ly do chot rule nay:

- Ten cot va UI/API deu la `isActive`, nen `true` nen mang nghia active binh thuong.
- Read-only la trang thai khoa ghi cua participant, tuong ung `isActive=false`.

---

## 2. Backend - User.isActive va read-only write filter

File chinh:

- `back-end/src/seal-api/src/main/java/com/seal/hackathon/entity/User.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/response/UserResponse.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/security/UserPrincipal.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/security/ReadOnlyParticipantWriteFilter.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/config/SecurityConfig.java`

Thay doi:

- Entity `User` giu field `isActive`, map vao cot `is_active`.
- `UserResponse` expose `isActive`, khong expose `readOnly`.
- `UserPrincipal` doc `user.getIsActive()`.
- `isEnabled()` khong dung `isActive` nua, de `isActive=false` khong chan login.
- Them filter `ReadOnlyParticipantWriteFilter`:
  - Cho phep `GET`, `HEAD`, `OPTIONS`.
  - Cho phep `POST /api/auth/logout`.
  - Cho phep `POST /api/participation-requests`.
  - Chan cac write request khac neu principal la participant va `isActive=false`.

Ket qua:

- Participant read-only van login va xem thong tin duoc.
- Participant read-only khong the tao/sua/xoa/join/submit bang cach goi API truc tiep.

---

## 3. Backend - Cac service can dung isActive

File chinh:

- `AuthService.java`
- `AccountService.java`
- `AdminService.java`
- `CustomOAuth2UserService.java`
- `TeamInviteService.java`
- `JoinRequestService.java`
- `UserRepository.java`
- `HackathonEventService.java`

Thay doi:

- Dang ky local/OAuth user moi:
  - `isApproved=false`
  - `isActive=true`
- Approve account:
  - `isApproved=true`
  - `isActive=true`
- Admin tao user:
  - `isApproved=true`
  - `isActive=true`
- Search invitable students chi lay:
  - approved student
  - `isActive=true`
- Invite/join request validation yeu cau:
  - `isApproved=true`
  - `isActive=true`
- Complete event:
  - Khong con quet toan bo student.
  - Chi set `isActive=false` cho student thuoc event vua completed.
  - Neu user do con membership o event khac chua completed thi khong khoa account.

Luu y quan trong:

- Vi `isActive` nam tren bang `User`, no la trang thai account global, khong phai per-event.
- De tranh khoa nham account, logic complete event chi khoa student khong con tham gia event non-completed nao.

---

## 4. Backend - Participation Access Request

File moi/them:

- `ParticipationAccessRequest.java`
- `ParticipationAccessRequestRepository.java`
- `ParticipationAccessRequestResponse.java`
- `ParticipationAccessRequestService.java`
- `ParticipationAccessRequestController.java`

Endpoint:

- Participant:
  - `POST /api/participation-requests`
- System Admin:
  - `GET /api/admin/participation-requests`
  - `POST /api/admin/participation-requests/{requestId}/approve`
  - `POST /api/admin/participation-requests/{requestId}/reject`

Rule:

- Chi student moi duoc request.
- User phai `isApproved=true`.
- Chi user `isActive=false` moi duoc gui request.
- Approve request:
  - set `user.isActive=true`
  - tao notification
  - ghi system log
- Reject request:
  - giu `isActive=false`
  - tao notification
  - ghi system log

---

## 5. Frontend - Remember Me

File chinh:

- `front-end/src/seal-web/src/shared/apiClient.ts`
- `front-end/src/seal-web/src/app/providers/AuthProvider.tsx`
- `front-end/src/seal-web/src/features/auth/LoginPage.tsx`

Thay doi:

- Neu tick remember me:
  - token luu trong `localStorage`
  - active role luu theo local token
  - ton tai den khi logout
- Neu khong tick remember me:
  - token luu trong `sessionStorage`
  - dong tab/browser thi mat session
- Khi login account moi:
  - clear token/activeRole/user cu truoc
  - tranh loi token account A bleed sang account B

---

## 6. Frontend - isActive/read-only UX

File chinh:

- `AuthProvider.tsx`
- `apiClient.ts`
- `ParticipantDashboard.tsx`
- `ExistingTeamDashboard.tsx`
- `AdminDashboard.tsx`
- `AdminAccountsPage.tsx`
- `AdminParticipationRequestsPage.tsx`
- `routes/index.tsx`
- `DashboardLayout.tsx`

Thay doi:

- Type frontend dung `isActive` / `is_active`, bo `readOnly` / `read_only`.
- `AuthUser.is_active=false` moi hien trang/panel read-only.
- Leader co `is_active=false` bi an:
  - manage team
  - submit project
  - select track
- Accounts page:
  - Cot `Access` doi thanh `isActive`.
  - Gia tri hien `TRUE/FALSE`.
  - Khong con button activate/deactivate account theo nghia cu.
- Participation requests:
  - Tach khoi Accounts page.
  - Route rieng: `/admin/participation-requests`.
  - Khong them vao left sidebar, di qua nut tren Accounts page.

---

## 7. DB schema va seed

File chinh:

- `back-end/database scripts/seal_schema.sql`
- `back-end/database scripts/seal_seed.sql`
- `back-end/database scripts/seal_scripts.sql`

Thay doi schema:

- `User.is_active` default ve `TRUE`.
- Comment cot:
  - `FALSE = participant read-only, TRUE = writable/active`
- Them bang `ParticipationAccessRequest` vao `seal_schema.sql`.
- Giu bang `PasswordResetOtp` trong `seal_schema.sql`.
- Xoa cac migration SQL rieng:
  - `is_active_participation_access_migration.sql`
  - `password_reset_otp_migration.sql`

Thay doi seed:

- Toan bo password hash cua 43 seed users da doi sang hash moi do user cung cap.
- Staff/system admin/judge/mentor/coordinator: `is_active=TRUE`.
- Student dang tham gia event dang mo/dang dien ra: `is_active=TRUE`.
- Student chi thuoc event da completed: `is_active=FALSE`.

Trang thai seed dang chot:

- `leader1@fpt.edu.vn`: `is_active=TRUE` vi vua thuoc Spring completed vua thuoc Summer OPEN.
- `leader4@fpt.edu.vn`: `is_active=FALSE` vi chi thuoc Spring completed.
- `member6@uit.edu.vn`: `is_active=FALSE` vi chi thuoc Spring completed.

---

## 8. Password reset OTP

File/backend lien quan:

- `PasswordResetOtp.java`
- `PasswordResetOtpRepository.java`
- `PasswordResetService.java`
- `ForgotPasswordRequest.java`
- `VerifyResetOtpRequest.java`
- `ResetPasswordRequest.java`
- `ResetOtpResponse.java`
- `AuthController.java`
- `EmailService.java`
- `ForgotPasswordPage.tsx`
- `apiClient.ts`

Thay doi:

- Them OTP password reset flow.
- Bang `PasswordResetOtp` nam trong `seal_schema.sql`.
- Khong giu migration SQL rieng nua.
- Frontend co flow forgot password/verify OTP/reset password.

---

## 9. DB local da thao tac truc tiep

Da cap nhat DB local `seal_hackathon` bang MySQL CLI.

Da thuc hien:

- Update password hash cho 43 users.
- Sua lai `is_active` sau khi rule bi dao nguoc:
  - Tat ca student/staff ve `TRUE`.
  - Chi set `FALSE` cho student chi thuoc event completed.

Ket qua query cuoi:

- `leader1@fpt.edu.vn`: `is_active=1`
- `leader4@fpt.edu.vn`: `is_active=0`
- `member6@uit.edu.vn`: `is_active=0`
- Tong student:
  - `is_active=0`: 2
  - `is_active=1`: 35

---

## 10. Verification

Backend:

- Lenh:
  - `mvn test`
- Ket qua:
  - `317` tests pass
  - `0` failures
  - `0` errors

Frontend:

- Lenh:
  - `npm.cmd run build`
- Ket qua:
  - build pass
  - con warning chunk size lon cua Vite, khong phai loi compile

---

## 11. Luu y khi test lai tren UI

Sau khi sua code backend/frontend:

- Restart backend de filter moi co hieu luc.
- Refresh frontend hoac logout/login lai de `AuthProvider` lay lai `/api/auth/me`.
- Neu can recreate DB:
  - chay `seal_schema.sql`
  - chay `seal_seed.sql`

Case nen test:

- Login `leader1@fpt.edu.vn`:
  - `isActive=true`
  - van thay Manage Team / Submit Project neu la leader.
- Login `leader4@fpt.edu.vn`:
  - `isActive=false`
  - thay read-only panel
  - khong thao tac write duoc.
- System Admin vao:
  - `/admin/accounts`
  - `/admin/participation-requests`

