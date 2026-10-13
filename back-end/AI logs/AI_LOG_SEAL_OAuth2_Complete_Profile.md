# AI Log — SEAL: OAuth2 (Google/GitHub) & Complete-Profile cho sinh viên

**Dự án:** SEAL – Software Engineering Hackathon Management System (SU26SWP04)
**Ngày:** 2026-06-15
**Phạm vi phiên:** Rà soát scaffolding OAuth2 sẵn có, hướng dẫn đăng ký OAuth app (Google + GitHub), và bổ sung luồng **complete-profile** để **sinh viên** đăng nhập OAuth lần đầu hoàn tất hồ sơ trước khi chờ duyệt. Kèm sửa lại thiết kế: OAuth self-signup **chỉ cho sinh viên** (staff do admin tạo).
**Công cụ:** Claude (Opus 4.8). Verify: BE `./mvnw -o compile`, FE `tsc --noEmit --ignoreDeprecations 6.0`.

> ⚙️ **[BE]** = Backend (`seal-api`), **[FE]** = Frontend (`seal-web`).

---

## 1. Khảo sát: OAuth2 ĐÃ có sẵn, không phải rác

User tưởng phải làm OAuth từ đầu + lo có "file rác". Thực tế scaffolding đã đầy đủ & mạch lạc:

**[BE]** `config/SecurityConfig` (oauth2Login), `security/oauth2/CustomOAuth2UserService`, `OAuth2LoginSuccessHandler`, `OAuth2UserInfo` + `GoogleOAuth2UserInfo` + `GithubOAuth2UserInfo`, `entity/User` (`provider/providerId/avatarUrl`), `UserRepository.findByProviderAndProviderId`, `security/UserPrincipal`.
**[FE]** `features/auth/SocialAuthButtons` (redirect `{api}/oauth2/authorization/{provider}`), `OAuth2RedirectPage` (đọc `?token` → setToken → /dashboard), route `/oauth2/redirect`.
**Config:** `application.properties` đã có đủ google/github registration (`${GOOGLE_CLIENT_ID:...}`...) + `app.frontend.url`.

**Kiểm tra "file rác":** `git ls-files` — `target/` không bị track, working tree sạch, không có file thừa. ⇒ **Không có rác.** "Thiếu" duy nhất là **client-id/secret thật** (việc đăng ký ngoài code).

---

## 2. Quyết định (Q&A với user)

| # | Hỏi | Chốt |
|---|-----|------|
| 1 | Đã có credentials chưa? | Chưa → cần hướng dẫn đăng ký |
| 2 | Provider nào | **Cả Google + GitHub** |
| 3 | Account OAuth có cần duyệt? | **Có, vẫn chờ Coordinator** (code đã đúng: `isApproved=false`) |
| 4 | Màn consent "dùng tên/email" — ai làm? | **Provider tự hiện**, mình KHÔNG dựng page |
| 5 | OAuth dành cho ai? | **Sinh viên** (ban đầu chọn "cả student" → sau sửa: **chỉ sinh viên**, staff do admin tạo) |

**Phát hiện thiết kế (user bắt lỗi):** ban đầu OAuth user mặc định `userType=STAFF` → sai, vì **staff do Admin/Coordinator cấp**, không tự đăng ký. ⇒ OAuth = self-signup **chỉ cho sinh viên**; account staff có sẵn thì OAuth chỉ **link provider** (qua `updateExistingUser` khi trùng email).

---

## 3. Vấn đề kỹ thuật cần giải

OAuth chỉ trả về **tên + email + avatar**, nhưng hệ thống cần `userType` (+ `studentId`/`university`). ⇒ Cần bước **complete-profile** sau lần đầu OAuth.

Hai điểm chặn đã kiểm tra:
- `JwtAuthenticationFilter` chỉ validate token, **không** chặn theo approval ⇒ user OAuth chưa duyệt vẫn gọi được API để complete-profile. ✅
- `OAuth2LoginSuccessHandler` đang chặn user chưa approve **trước** khi họ kịp hoàn tất hồ sơ ⇒ phải nới cho user "profile chưa hoàn tất".

---

## 4. 🔧 [BE] Thay đổi backend (`seal-api`)

| File | Loại | Thay đổi |
|------|------|----------|
| `security/UserPrincipal.java` | sửa | **+** field `userType` (để success-handler nhận biết hồ sơ chưa hoàn tất) |
| `security/oauth2/CustomOAuth2UserService.java` | sửa | `createNewUser`: `userType` `"STAFF"` → **`"PENDING_PROFILE"`** (sentinel) |
| `security/oauth2/OAuth2LoginSuccessHandler.java` | sửa | Nếu `userType=="PENDING_PROFILE"` → **bỏ qua** check active/approved, vẫn cấp token (để vào complete-profile). Approve chỉ áp dụng khi hồ sơ đã hoàn tất |
| `dto/request/CompleteProfileRequest.java` | **mới** | `{ userType, studentId?, university? }` |
| `service/AuthService.java` | sửa | **+** `completeProfile(email, req)`: chỉ chạy khi `userType==PENDING_PROFILE`; **chỉ nhận FPT_STUDENT/EXTERNAL_STUDENT** (STAFF → 400); studentId bắt buộc; EXTERNAL cần university |
| `controller/AuthController.java` | sửa | **+** `PUT /api/auth/complete-profile` |

> Account sau complete-profile vẫn `isApproved=false` ⇒ tiếp tục chờ Coordinator duyệt (đúng yêu cầu #3).

---

## 5. 🎨 [FE] Thay đổi frontend (`seal-web`)

| File | Loại | Thay đổi |
|------|------|----------|
| `shared/apiClient.ts` | sửa | **+** `authApi.completeProfile` + `CompleteProfilePayload` (chỉ FPT/EXTERNAL) + hằng `PENDING_PROFILE` |
| `app/providers/AuthProvider.tsx` | sửa | `AuthUser` **+** `profile_incomplete`; `mapApiUser` set `= userType==='PENDING_PROFILE'` |
| `app/routes/index.tsx` | sửa | **+** route `/complete-profile`; **guard** trong `RequireAuth`: user `profile_incomplete` bị ép về `/complete-profile` trước khi vào bất kỳ màn nào |
| `features/auth/CompleteProfilePage.tsx` | **mới** | Form chọn Student type (FPT/External) + studentId/university → `completeProfile` → **logout + `/pending-approval`** (vì vẫn chờ duyệt) |

---

## 6. Luồng hoàn chỉnh

```
Continue with Google/GitHub
  → CustomOAuth2UserService: user mới (userType=PENDING_PROFILE, isApproved=false)
  → OAuth2LoginSuccessHandler: profile chưa hoàn tất ⇒ cấp token, redirect /oauth2/redirect?token
  → FE setToken → /dashboard
  → AuthProvider /me ⇒ profile_incomplete=true
  → RequireAuth guard ⇒ ép sang /complete-profile
  → User chọn FPT/External + studentId  →  PUT /api/auth/complete-profile
  → logout + /pending-approval  (chờ Coordinator duyệt)
  → Coordinator duyệt → đăng nhập OAuth lại → vào bình thường
```
Staff: account do Admin tạo (email+password); nếu bấm OAuth trùng email → `updateExistingUser` link provider, KHÔNG tạo mới.

---

## 7. Verify
| | Kết quả |
|---|---------|
| BE `mvnw compile` | ✅ EXIT 0 |
| FE `tsc` | ✅ EXIT 0 |

> Chưa test live — cần credentials thật.

---

## 8. Việc ngoài code (user tự làm) — đăng ký OAuth app

**Google** (console.cloud.google.com → Google Auth Platform):
- **Audience → Test users**: thêm email test (app ở Testing chỉ cho test users).
- **Clients → Create OAuth client → Web application** → Authorized redirect URI: `http://localhost:8080/login/oauth2/code/google` → lấy Client ID + Secret.
- **Data Access → Add or Remove Scopes** `email/profile` — *không bắt buộc* (backend đã khai `scope=email,profile`).

**GitHub** (Settings → Developer settings → OAuth Apps → New):
- Homepage `http://localhost:5173`, Callback `http://localhost:8080/login/oauth2/code/github` → Client ID + generate secret.

**Cấp secret (KHÔNG commit — git lưu vĩnh viễn, repo public bị provider thu hồi):** env var trong terminal chạy BE:
```powershell
$env:GOOGLE_CLIENT_ID="..."; $env:GOOGLE_CLIENT_SECRET="..."
$env:GITHUB_CLIENT_ID="..."; $env:GITHUB_CLIENT_SECRET="..."
./mvnw spring-boot:run
```

**Lỗi hay gặp:** `redirect_uri_mismatch` (URI sai tuyệt đối), `access_denied` (chưa thêm Test user), `?error=ACCOUNT_NOT_APPROVED` (đúng luồng — chờ duyệt).

---

## 9. Còn DEFER (note)
- Endpoint **forgot-password** (+email), **change-password** — đang là vỏ/placeholder (từ phiên trước).
- OAuth: **credentials thật + test live**.
- (Mentor/judge & team defers ở các log trước vẫn còn.)

---

*Log sinh bởi Claude (Opus 4.8) — phiên 2026-06-15.*
