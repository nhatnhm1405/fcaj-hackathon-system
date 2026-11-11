# Bài 03 - Package entity và cách đọc entity

## Phạm vi

Bài này giải thích package:

```text
back-end/src/seal-api/src/main/java/com/seal/hackathon/entity
```

Đã xác minh package này có 31 file entity:

```text
Announcement.java
AuditLog.java
HackathonEvent.java
JoinRequest.java
JudgeAssignment.java
MentorAssignment.java
MentorSupportRequest.java
Notification.java
ParticipantEventHistory.java
ParticipationAccessRequest.java
PasswordResetOtp.java
Prize.java
ReopenRequest.java
Role.java
Round.java
RoundResult.java
RoundTimer.java
RoundTimerNotice.java
Score.java
ScoringCriteria.java
ScoringCriteriaTemplate.java
Submission.java
SystemLog.java
Team.java
TeamEventEntry.java
TeamInvite.java
TeamMember.java
TeamRejoinRequest.java
Track.java
User.java
UserEventRole.java
```

Bài này là bản đồ entity. Các bài sau sẽ drill từng entity quan trọng theo từng dòng.

## 1. Entity là gì?

Trong Servlet MVC cũ, bạn có thể từng có class model như:

```java
public class User {
    private int id;
    private String email;
}
```

Và DAO lấy dữ liệu từ DB rồi đổ vào object `User`.

Trong Spring Boot + JPA, model gắn với DB được gọi là `Entity`.

Ví dụ:

```java
@Entity
@Table(name = "`User`")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;
}
```

Ý nghĩa:

- `@Entity`: class này được Hibernate/JPA quản lý.
- `@Table`: class này map với bảng nào trong DB.
- `@Id`: field khóa chính.
- `@Column`: field map với cột nào.
- Object Java đại diện cho một dòng trong bảng.

## 2. Cách đọc một entity

Khi mở một entity, dùng checklist này:

1. Tên class là gì?
2. `@Table` map với bảng nào?
3. Field nào là primary key?
4. Field nào bắt buộc `nullable=false`?
5. Field nào unique?
6. Field nào là status?
7. Field nào là timestamp?
8. Entity này có quan hệ với entity nào?
9. Có `@PrePersist` hay logic default nào không?
10. Service nào có thể thay đổi entity này?

Dùng cách này sẽ giúp bạn không đọc lan man.

## 3. Các annotation JPA hay gặp

### `@Entity`

Đánh dấu class là bảng/đối tượng DB.

### `@Table`

Khai báo tên bảng và unique constraint.

Ví dụ:

```java
@Table(name = "TeamEventEntry", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"team_id", "event_id"})
})
```

Nghĩa là một team không được có hai entry trùng event.

### `@Id`

Khai báo primary key.

### `@GeneratedValue(strategy = GenerationType.IDENTITY)`

ID do database tự tăng. Đây tương đương `AUTO_INCREMENT` trong MySQL.

### `@Column`

Map field Java với column DB.

Ví dụ:

```java
@Column(name = "email", nullable = false, unique = true, length = 255)
private String email;
```

Nghĩa là:

- Cột DB tên `email`.
- Không được null.
- Giá trị phải unique.
- Độ dài tối đa 255.

### `@ManyToOne`

Nhiều object hiện tại thuộc về một object khác.

Ví dụ nhiều `TeamMember` thuộc một `Team`.

### `@OneToMany`

Một object có danh sách object con.

Ví dụ một `User` có nhiều `UserEventRole`.

### `@JoinColumn`

Khai báo foreign key column.

### `@PrePersist`

Method được gọi trước khi entity được insert lần đầu vào DB.

Hay dùng để set `createdAt`.

## 4. Các annotation Lombok hay gặp

Package entity dùng Lombok để giảm code lặp.

### `@Getter`

Tự sinh getter.

### `@Setter`

Tự sinh setter.

### `@NoArgsConstructor`

Tự sinh constructor không tham số. JPA cần constructor này để tạo object.

### `@AllArgsConstructor`

Tự sinh constructor có tất cả field.

### `@Builder`

Cho phép tạo object theo builder pattern.

Ví dụ:

```java
User user = User.builder()
        .email("a@b.com")
        .fullName("A")
        .build();
```

### `@Builder.Default`

Đặt giá trị default khi dùng builder.

Nếu không có `@Builder.Default`, builder có thể bỏ qua giá trị khởi tạo tại field.

## 5. Nhóm entity theo nghiệp vụ

### Nhóm user/account/role

```text
User
Role
UserEventRole
ParticipationAccessRequest
PasswordResetOtp
```

Nhiệm vụ:

- Lưu user.
- Lưu role.
- Lưu quyền của user theo event.
- Lưu request xin kích hoạt lại participant.
- Lưu OTP reset password.

### Nhóm event/track/round

```text
HackathonEvent
Track
Round
RoundTimer
RoundTimerNotice
```

Nhiệm vụ:

- Lưu thông tin mùa hackathon.
- Lưu track/chủ đề.
- Lưu round.
- Lưu timer contest/judging.
- Chống gửi trùng notification milestone timer.

### Nhóm team

```text
Team
TeamEventEntry
TeamMember
TeamInvite
JoinRequest
TeamRejoinRequest
```

Nhiệm vụ:

- Lưu team.
- Lưu việc team tham gia event nào.
- Lưu thành viên.
- Lưu lời mời vào team.
- Lưu yêu cầu xin vào team.
- Lưu yêu cầu team cũ tham gia lại event mới.

Đây là nhóm rất quan trọng vì nghiệp vụ team trong dự án không đơn giản.

### Nhóm submission/scoring/result

```text
Submission
ScoringCriteria
ScoringCriteriaTemplate
Score
RoundResult
Prize
```

Nhiệm vụ:

- Lưu bài nộp.
- Lưu tiêu chí chấm điểm.
- Lưu template tiêu chí.
- Lưu điểm judge chấm.
- Lưu kết quả round.
- Lưu giải thưởng.

### Nhóm assignment/support/communication

```text
JudgeAssignment
MentorAssignment
MentorSupportRequest
Announcement
Notification
```

Nhiệm vụ:

- Gán judge vào round/track.
- Gán mentor vào track.
- Participant xin mentor support.
- Gửi announcement.
- Gửi notification.

### Nhóm audit/history/system

```text
AuditLog
SystemLog
ParticipantEventHistory
ReopenRequest
```

Nhiệm vụ:

- Lưu log nghiệp vụ trong event.
- Lưu log hệ thống/admin.
- Lưu lịch sử participant sau khi event kết thúc hoặc user rời team.
- Lưu yêu cầu mở lại event đã completed.

## 6. Quan hệ dữ liệu quan trọng nhất

### User - TeamMember - Team

```text
User
 -> TeamMember
 -> Team
```

`User` không nằm trực tiếp trong `Team`. Phải qua `TeamMember`.

Lý do:

- Một team có nhiều user.
- Một user theo lịch sử có thể liên quan nhiều team/event.
- `TeamMember` lưu thêm `memberRole`, `joinedAt`.

### Team - TeamEventEntry - HackathonEvent

```text
Team
 -> TeamEventEntry
 -> HackathonEvent
```

Đây là quan hệ cần nhớ kỹ.

`Team` là đội.

`TeamEventEntry` là việc đội đó tham gia một event cụ thể.

Ví dụ:

```text
Team "Alpha"
 -> tham gia Spring 2026: APPROVED
 -> tham gia Summer 2026: PENDING
```

Nếu chỉ đặt `event_id` và `status` trực tiếp trong `Team`, team sẽ khó tái sử dụng qua nhiều event.

### Event - Track - Round

```text
HackathonEvent
 -> Track
 -> Round
```

Một event có nhiều track và nhiều round.

Track là bảng/chủ đề.

Round là vòng thi.

### Submission - Score - RoundResult

```text
Submission
 -> Score
 -> RoundResult
```

Team nộp bài (`Submission`), judge chấm điểm (`Score`), coordinator finalize thành kết quả (`RoundResult`).

## 7. Thứ tự học entity

Không học alphabet. Học theo thứ tự sau:

1. `User`
2. `Role`
3. `UserEventRole`
4. `HackathonEvent`
5. `Track`
6. `Round`
7. `Team`
8. `TeamEventEntry`
9. `TeamMember`
10. `TeamInvite`
11. `JoinRequest`
12. `Submission`
13. `ScoringCriteria`
14. `Score`
15. `RoundResult`
16. `Prize`
17. Các entity support/log/history còn lại.

## 8. Câu giáo viên có thể hỏi

### Entity khác DTO ở đâu?

Entity map với database. DTO map với request/response API.

Không nên trả thẳng entity cho frontend nếu response cần format/lọc field riêng.

### Entity khác Repository ở đâu?

Entity là object dữ liệu. Repository là object/interface dùng để lấy/lưu entity.

### Vì sao cần `TeamEventEntry`?

Vì một team có thể có lịch sử/tham gia theo từng event. Trạng thái team trong event (`PENDING`, `APPROVED`, `REJECTED`, `DISQUALIFIED`) thuộc về entry, không nên gắn cứng vào `Team`.

### Vì sao hay có `createdAt` và `@PrePersist`?

Để tự set thời điểm tạo bản ghi trước khi insert vào DB.

## Kiểm tra độ đầy đủ

Đã giải thích:

- Package entity có bao nhiêu file.
- Entity là gì theo nền Servlet MVC.
- Annotation JPA/Lombok hay gặp.
- Nhóm entity theo nghiệp vụ.
- Quan hệ entity quan trọng.
- Thứ tự học entity.

Không giải thích thừa:

- Chưa drill từng dòng của 31 entity trong một bài vì như vậy sẽ quá dài và khó học.
- Từng entity quan trọng được tách sang bài riêng bắt đầu từ `User.java` và `HackathonEvent.java`.

