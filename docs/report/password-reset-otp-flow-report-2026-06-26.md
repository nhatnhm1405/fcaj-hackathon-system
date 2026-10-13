# Báo Cáo Flow Reset Password Bằng OTP

Ngày: 2026-06-26

## Tóm Tắt

Đã triển khai flow quên mật khẩu bằng OTP gửi qua email:

1. User bấm `Forgot Password`.
2. User nhập email.
3. Backend kiểm tra email có tồn tại không, tài khoản có active không, đã được approved chưa, và có hỗ trợ đăng nhập local không.
4. Backend tạo OTP gồm 6 chữ số, chỉ lưu hash bằng `PasswordEncoder`, rồi gửi OTP thật qua email.
5. User nhập OTP trên web.
6. Backend so sánh OTP user nhập với hash đã lưu, kiểm tra thời hạn, kiểm tra giới hạn số lần nhập sai, rồi trả về reset token tạm thời nếu OTP hợp lệ.
7. User nhập mật khẩu mới.
8. Backend cập nhật `User.password_hash`, đánh dấu phiên OTP đã được sử dụng, và frontend quay về landing page.

## Rule Đã Đáp Ứng Theo Yêu Cầu

- Email phải tồn tại. Nếu không tồn tại, backend trả về `mail invalid`.
- Tài khoản phải active. Nếu không active, backend trả về `account inactive`.
- Tài khoản phải được approved. Nếu chưa approved, backend trả về `account not approved`.
- OTP chỉ được gửi email sau khi toàn bộ điều kiện email/account hợp lệ.
- OTP mặc định hết hạn sau 10 phút.
- OTP được phép nhập sai tối đa 10 lần.
- OTP và reset token không được lưu dạng raw trong database. OTP dùng `PasswordEncoder`; reset token dùng SHA-256.
- Sau khi đổi mật khẩu thành công, phiên OTP được đánh dấu đã sử dụng và không thể dùng lại.
- Frontend quay về `/` sau khi reset password thành công.

## Thay Đổi Backend

### Entity Mới

Đã thêm:

`back-end/src/seal-api/src/main/java/com/seal/hackathon/entity/PasswordResetOtp.java`

Entity này map tới bảng mới `PasswordResetOtp` và lưu:

- `user_id`: user đang yêu cầu reset password.
- `otp_hash`: hash OTP bằng `PasswordEncoder`.
- `reset_token_hash`: hash SHA-256 của token tạm thời được cấp sau khi verify OTP thành công.
- `expires_at`: thời điểm OTP hết hạn.
- `verified_at`: thời điểm OTP được xác thực thành công.
- `used_at`: thời điểm reset session đã được dùng hoặc bị vô hiệu hóa.
- `attempt_count`: số lần nhập OTP sai.
- `created_at`: thời điểm tạo reset session.

### Repository Mới

Đã thêm:

`back-end/src/seal-api/src/main/java/com/seal/hackathon/repository/PasswordResetOtpRepository.java`

Trách nhiệm chính:

- Tìm OTP session active mới nhất theo user.
- Tìm reset session active theo hash của reset token.
- Đánh dấu các session active cũ là đã dùng khi user yêu cầu OTP mới.

### DTO Mới

Đã thêm:

- `ForgotPasswordRequest.java`
- `VerifyResetOtpRequest.java`
- `ResetPasswordRequest.java`
- `ResetOtpResponse.java`

Các DTO này hỗ trợ 3 bước API:

- yêu cầu gửi OTP
- xác thực OTP
- đổi mật khẩu

### Service Mới

Đã thêm:

`back-end/src/seal-api/src/main/java/com/seal/hackathon/service/PasswordResetService.java`

Hành vi quan trọng:

- Chuẩn hóa email trước khi tìm user.
- Từ chối email không tồn tại bằng `mail invalid`.
- Từ chối tài khoản inactive bằng `account inactive`.
- Từ chối tài khoản chưa approved bằng `account not approved`.
- Từ chối tài khoản chỉ đăng nhập OAuth vì các tài khoản này không có local password.
- Tạo OTP bảo mật gồm 6 chữ số.
- Lưu `otp_hash` bằng `PasswordEncoder`, không lưu OTP thật.
- Vô hiệu hóa các OTP session active cũ của cùng user khi user yêu cầu OTP mới.
- Giới hạn số lần nhập sai OTP là 10.
- Chỉ tạo reset token tạm thời sau khi OTP đã được verify thành công.
- Dùng `PasswordEncoder` để lưu hash của mật khẩu mới.

### Endpoint Trong Auth Controller

Đã cập nhật:

`back-end/src/seal-api/src/main/java/com/seal/hackathon/controller/AuthController.java`

Đã thêm các endpoint public:

```http
POST /api/auth/forgot-password
```

Request:

```json
{
  "email": "user@example.com"
}
```

Success:

```json
{
  "success": true,
  "message": "OTP sent to your email."
}
```

```http
POST /api/auth/verify-reset-otp
```

Request:

```json
{
  "email": "user@example.com",
  "otp": "123456"
}
```

Success:

```json
{
  "success": true,
  "message": "OTP verified successfully.",
  "data": {
    "resetToken": "temporary-token"
  }
}
```

```http
POST /api/auth/reset-password
```

Request:

```json
{
  "resetToken": "temporary-token",
  "newPassword": "NewPassword123"
}
```

Success:

```json
{
  "success": true,
  "message": "Password reset successful."
}
```

### Email Service

Đã cập nhật:

`back-end/src/seal-api/src/main/java/com/seal/hackathon/service/EmailService.java`

Đã thêm:

`sendPasswordResetOtpEmail(String to, String fullName, String otp, long expiresInMinutes)`

Email gửi cho user bao gồm:

- lời chào người nhận
- mã OTP
- thời hạn hiệu lực của OTP
- ghi chú OTP bị giới hạn tối đa 10 lần nhập
- thông báo bỏ qua email nếu user không yêu cầu reset password

### Cấu Hình

Đã cập nhật:

`back-end/src/seal-api/src/main/resources/application.properties`

Đã thêm:

```properties
app.password-reset.otp-expiration-minutes=${PASSWORD_RESET_OTP_EXPIRATION_MINUTES:10}
```

Thời gian sống mặc định của OTP là 10 phút. Có thể thay đổi bằng environment variable:

```text
PASSWORD_RESET_OTP_EXPIRATION_MINUTES
```

## Thay Đổi Database

Đã cập nhật:

`back-end/database scripts/seal_schema.sql`

Đã thêm bảng `PasswordResetOtp` vào schema chính.

Đã thêm:

`back-end/database scripts/password_reset_otp_migration.sql`

Dùng file migration này nếu database local hiện tại đã được tạo trước khi có thay đổi này.

```sql
USE seal_hackathon;

CREATE TABLE IF NOT EXISTS PasswordResetOtp (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  user_id          INT          NOT NULL,
  otp_hash         VARCHAR(255) NOT NULL,
  reset_token_hash VARCHAR(255),
  expires_at       DATETIME     NOT NULL,
  verified_at      DATETIME,
  used_at          DATETIME,
  attempt_count    INT          NOT NULL DEFAULT 0,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_password_reset_token_hash (reset_token_hash),
  KEY idx_password_reset_user_active (user_id, used_at, created_at),
  KEY idx_password_reset_expires_at (expires_at),
  CONSTRAINT fk_password_reset_otp_user FOREIGN KEY (user_id) REFERENCES `User` (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

Cũng đã cập nhật:

`back-end/database scripts/seal_scripts.sql`

Đã thêm query kiểm tra nhanh:

```sql
SELECT * FROM PasswordResetOtp;
```

## Thay Đổi Frontend

### API Client

Đã cập nhật:

`front-end/src/seal-web/src/shared/apiClient.ts`

Đã thêm:

- `authApi.requestPasswordReset(payload)`
- `authApi.verifyResetOtp(payload)`
- `authApi.resetPassword(payload)`

### Trang Forgot Password

Đã cập nhật:

`front-end/src/seal-web/src/features/auth/ForgotPasswordPage.tsx`

Trang hiện có flow 3 bước:

1. Bước email: nhập email và yêu cầu gửi OTP.
2. Bước OTP: nhập OTP nhận qua email, verify OTP, gửi lại OTP, hoặc đổi email.
3. Bước mật khẩu: nhập và xác nhận mật khẩu mới.

Khi reset password thành công, frontend điều hướng về:

```text
/
```

## Thông Báo Lỗi

Các message backend dự kiến:

- `mail invalid`
- `account inactive`
- `account not approved`
- `This account uses Google/GitHub login.`
- `No password reset request found.`
- `OTP invalid.`
- `OTP attempts exceeded. Please request a new code.`
- `OTP expired. Please request a new code.`
- `Reset session invalid. Please verify OTP again.`

## Checklist Review Thủ Công

1. Chạy migration DB nếu đang dùng database đã tồn tại:

```sql
SOURCE back-end/database scripts/password_reset_otp_migration.sql;
```

2. Start backend và frontend.
3. Mở `/login`.
4. Bấm `FORGOT PASSWORD?`.
5. Thử email không tồn tại và xác nhận nhận được `mail invalid`.
6. Thử tài khoản inactive/chưa approved và xác nhận bị chặn.
7. Thử tài khoản local đã approved và active, rồi xác nhận OTP được gửi qua email.
8. Nhập sai OTP và xác nhận có lỗi hiển thị.
9. Nhập sai OTP 10 lần và xác nhận OTP session bị khóa.
10. Yêu cầu OTP mới.
11. Nhập đúng OTP và xác nhận trang chuyển sang bước nhập mật khẩu mới.
12. Nhập mật khẩu mới và xác nhận trang quay về `/`.
13. Đăng nhập bằng mật khẩu mới.

## Kiểm Tra Tự Động

Đã thêm:

`back-end/src/seal-api/src/test/java/com/seal/hackathon/service/PasswordResetServiceTest.java`

Các case đã cover:

- email không tồn tại trả về `mail invalid`
- tài khoản inactive trả về `account inactive`
- tài khoản chưa approved trả về `account not approved`
- tài khoản hợp lệ lưu hash OTP bằng `PasswordEncoder` và gửi OTP thật qua `EmailService`
- nhập sai OTP làm tăng `attempt_count`
- lần nhập sai thứ 10 khóa session
- OTP đúng trả về reset token và reset password sẽ consume session

Command đã chạy:

```powershell
& "$HOME\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd" test
```

Kết quả:

```text
Tests run: 314, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

```powershell
npm.cmd run build
```

Kết quả:

```text
vite build completed successfully
```

## Ghi Chú

- Vì project đang dùng `spring.jpa.hibernate.ddl-auto=none`, chỉ thêm entity là chưa đủ. Bảng database phải tồn tại thật.
- Implementation này không lưu OTP thật và reset token thật trong database.
- Khi user yêu cầu OTP mới, các reset session active cũ của cùng user sẽ bị vô hiệu hóa.
- Flow này được giới hạn cho tài khoản local. Tài khoản OAuth nên dùng cơ chế khôi phục tài khoản của Google/GitHub.
