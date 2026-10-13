# Bài 04 - Entity User.java

## Phạm vi

Bài này giải thích file:

```text
back-end/src/seal-api/src/main/java/com/seal/hackathon/entity/User.java
```

Source này dài 97 dòng theo lần đọc hiện tại.

## Vai trò của User trong hệ thống

`User` đại diện cho người dùng trong hệ thống.

Người dùng có thể là:

- Sinh viên FPT.
- Sinh viên ngoài.
- Staff.
- Mentor.
- Judge.
- Event coordinator.
- System admin.

Bạn cần phân biệt:

```text
User = thông tin tài khoản và profile
Role/UserEventRole = quyền của user
TeamMember = user nằm trong team nào
```

## Giải thích từng dòng/khối

### Dòng 1

```java
package com.seal.hackathon.entity;
```

Class `User` nằm trong package `entity`, nghĩa là nó thuộc tầng model/database.

### Dòng 3

```java
import jakarta.persistence.*;
```

Import tất cả annotation/class JPA cần dùng:

- `@Entity`
- `@Table`
- `@Id`
- `@GeneratedValue`
- `@Column`
- `@OneToMany`
- `@PrePersist`

### Dòng 4

```java
import lombok.*;
```

Import các annotation Lombok:

- `@Getter`
- `@Setter`
- `@NoArgsConstructor`
- `@AllArgsConstructor`
- `@Builder`

### Dòng 6-8

```java
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
```

Ý nghĩa:

- `LocalDateTime`: lưu thời gian như `createdAt`, `expiredAt`.
- `List`: danh sách role của user.
- `ArrayList`: giá trị default cho danh sách role.

### Dòng 10-14

```java
/**
 * Maps to the `User` table in seal_hackathon schema.
 * password_hash is nullable to support OAuth2 users who have no local password.
 * provider tracks which auth method was used (LOCAL, GOOGLE, GITHUB).
 */
```

Comment này nói rõ:

- Entity này map với bảng `User`.
- `password_hash` có thể null vì user OAuth2 không có password local.
- `provider` cho biết user đăng ký bằng LOCAL, GOOGLE hay GITHUB.

### Dòng 15

```java
@Entity
```

Nói với JPA/Hibernate: class này là entity, cần map với database.

### Dòng 16

```java
@Table(name = "`User`")
```

Map class `User` với bảng DB tên `User`.

Backtick trong tên bảng dùng để tránh xung đột vì `USER` có thể là keyword/tên đặc biệt trong một số database.

### Dòng 17-21

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
```

Lombok sẽ sinh code:

- Getter cho tất cả field.
- Setter cho tất cả field.
- Constructor rỗng.
- Constructor đủ tất cả field.
- Builder pattern.

Nghĩa là dù source không viết:

```java
getEmail()
setEmail(...)
```

nhưng khi compile vẫn có.

### Dòng 22

```java
public class User {
```

Khai báo class `User`.

### Dòng 24-27: primary key

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
@Column(name = "user_id")
private Integer userId;
```

Ý nghĩa:

- `@Id`: `userId` là khóa chính.
- `@GeneratedValue(strategy = GenerationType.IDENTITY)`: DB tự tăng ID.
- `@Column(name = "user_id")`: map với cột `user_id`.
- `Integer userId`: ID của user.

Tương đương MySQL:

```sql
user_id INT AUTO_INCREMENT PRIMARY KEY
```

### Dòng 29-30: email

```java
@Column(name = "email", nullable = false, unique = true, length = 255)
private String email;
```

Ý nghĩa:

- Cột DB tên `email`.
- Bắt buộc có giá trị.
- Không được trùng.
- Tối đa 255 ký tự.

Email là định danh đăng nhập chính.

### Dòng 32-34: password hash

```java
// Nullable: OAuth2 users do not have a local password
@Column(name = "password_hash", length = 255)
private String passwordHash;
```

Không lưu password plain text. Chỉ lưu password đã hash.

Field này nullable vì user đăng nhập bằng Google/GitHub có thể không có password local.

### Dòng 36-37: full name

```java
@Column(name = "full_name", nullable = false, length = 255)
private String fullName;
```

Tên đầy đủ của user.

`nullable=false` nghĩa là bắt buộc có.

### Dòng 39-41: user type

```java
// FPT_STUDENT | EXTERNAL_STUDENT | STAFF
@Column(name = "user_type", nullable = false, length = 20)
private String userType;
```

Phân loại user.

Theo comment có các giá trị:

- `FPT_STUDENT`
- `EXTERNAL_STUDENT`
- `STAFF`

Đây không phải role trực tiếp. Nó là loại user/profile. Role nằm ở `UserEventRole`.

### Dòng 43-45: judge type

```java
// INTERNAL | GUEST; only set for users who act as judges
@Column(name = "judge_type", length = 20)
private String judgeType;
```

Chỉ có ý nghĩa nếu user làm judge.

Có thể là:

- `INTERNAL`
- `GUEST`

### Dòng 47-48: student id

```java
@Column(name = "student_id", length = 50)
private String studentId;
```

Mã sinh viên. Có thể null với staff/guest.

### Dòng 50-51: university

```java
@Column(name = "university", length = 255)
private String university;
```

Trường đại học của user, đặc biệt hữu ích với external student.

### Dòng 53-56: approved flag

```java
// false = pending approval, true = approved and can log in
@Column(name = "is_approved", nullable = false)
@Builder.Default
private Boolean isApproved = false;
```

Ý nghĩa:

- `isApproved=false`: tài khoản đang chờ duyệt.
- `isApproved=true`: tài khoản đã được duyệt.

`@Builder.Default` đảm bảo khi tạo user bằng builder, nếu không set thì mặc định là `false`.

### Dòng 58-61: active flag

```java
// Reused flag: false means a participant is read-only; true means writable/active.
@Column(name = "is_active", nullable = false)
@Builder.Default
private Boolean isActive = true;
```

Ý nghĩa:

- `true`: user active.
- `false`: với participant, user có thể bị đưa vào trạng thái read-only.

Trong source, security có `InactiveParticipantWriteFilter` để chặn inactive participant ghi dữ liệu.

### Dòng 63-64: createdAt

```java
@Column(name = "created_at", nullable = false, updatable = false)
private LocalDateTime createdAt;
```

Thời điểm tạo user.

- `nullable=false`: bắt buộc có.
- `updatable=false`: sau khi insert thì Hibernate không update cột này.

### Dòng 66-68: expiredAt

```java
// MySQL ON UPDATE CURRENT_TIMESTAMP managed by DB, not Hibernate
@Column(name = "expired_at")
private LocalDateTime expiredAt;
```

Theo comment, cột này do DB quản lý khi update timestamp.

Trong entity, Hibernate chỉ map field này, không có logic riêng ở đây.

### Dòng 70-73: provider

```java
// LOCAL | GOOGLE | GITHUB  (added via ALTER TABLE)
@Column(name = "provider", length = 20, nullable = false)
@Builder.Default
private String provider = "LOCAL";
```

Cho biết user đăng ký/đăng nhập bằng nguồn nào:

- `LOCAL`
- `GOOGLE`
- `GITHUB`

Mặc định là `LOCAL`.

### Dòng 75-77: providerId

```java
// Unique ID from the OAuth2 provider (added via ALTER TABLE)
@Column(name = "provider_id", length = 255)
private String providerId;
```

ID của user bên Google/GitHub.

Ví dụ Google trả về `sub`, GitHub trả về `id`.

### Dòng 79-81: avatarUrl

```java
// Profile picture URL from OAuth2 provider (added via ALTER TABLE)
@Column(name = "avatar_url", length = 500)
private String avatarUrl;
```

URL ảnh đại diện của user.

Có thể đến từ OAuth2 provider hoặc upload avatar.

### Dòng 83-86: userEventRoles

```java
// Roles assigned to this user (across all events)
@OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
@Builder.Default
private List<UserEventRole> userEventRoles = new ArrayList<>();
```

Đây là quan hệ:

```text
Một User có nhiều UserEventRole
```

Ý nghĩa chi tiết:

- `@OneToMany`: một user có nhiều role assignment.
- `mappedBy = "user"`: phía `UserEventRole` mới là phía giữ foreign key.
- `fetch = FetchType.LAZY`: không tự load role nếu chưa cần.
- `cascade = CascadeType.ALL`: thao tác trên user có thể cascade sang role assignment.
- Default là list rỗng để tránh null.

### Dòng 88-96: onCreate

```java
@PrePersist
protected void onCreate() {
    if (createdAt == null) {
        createdAt = LocalDateTime.now();
    }
    if (provider == null) {
        provider = "LOCAL";
    }
}
```

`@PrePersist` nói với JPA: trước khi insert user vào DB, hãy gọi method này.

Logic:

1. Nếu `createdAt` chưa có, set bằng thời điểm hiện tại.
2. Nếu `provider` chưa có, set thành `LOCAL`.

Lý do:

- Đảm bảo user mới luôn có thời điểm tạo.
- Đảm bảo user mới luôn có provider.

### Dòng 97

```java
}
```

Kết thúc class `User`.

## Tóm tắt nghiệp vụ của User

`User` không tự xử lý đăng nhập. Nó chỉ lưu dữ liệu.

Nghiệp vụ liên quan nằm chủ yếu ở:

```text
AuthService
AccountService
AdminService
PasswordResetService
CustomUserDetailsService
UserPrincipal
```

## Câu giáo viên có thể hỏi

### Vì sao `passwordHash` nullable?

Vì user đăng nhập bằng OAuth2 Google/GitHub có thể không có password local.

### Vì sao có cả `userType` và role?

`userType` là loại profile: student/staff. Role là quyền hệ thống/event, nằm trong `UserEventRole`.

### Vì sao `isApproved` default false?

Tài khoản mới cần được duyệt trước khi sử dụng đầy đủ.

### Vì sao `isActive` khác `isApproved`?

`isApproved` nói tài khoản đã được duyệt chưa. `isActive` nói user hiện có được hoạt động/ghi dữ liệu không.

### Vì sao `User` có `List<UserEventRole>`?

Vì một user có thể có nhiều role, và role có thể gắn theo event.

## Kiểm tra độ đầy đủ

Đã giải thích:

- Tất cả import.
- Tất cả annotation class.
- Tất cả field.
- Quan hệ `User -> UserEventRole`.
- Method `onCreate`.
- Ý nghĩa nghiệp vụ của từng field.

Không giải thích thừa:

- Không đi vào logic login chi tiết vì logic nằm trong `AuthService`.
- Không đi vào JWT/Security filter vì thuộc bài security.

