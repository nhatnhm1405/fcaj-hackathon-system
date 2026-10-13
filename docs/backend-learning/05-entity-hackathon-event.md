# Bài 05 - Entity HackathonEvent.java

## Phạm vi

Bài này giải thích file:

```text
back-end/src/seal-api/src/main/java/com/seal/hackathon/entity/HackathonEvent.java
```

Source này dài 74 dòng theo lần đọc hiện tại.

## Vai trò của HackathonEvent

`HackathonEvent` đại diện cho một mùa/sự kiện hackathon.

Ví dụ:

```text
SEAL Hackathon Spring 2026
SEAL Hackathon Summer 2026
```

Entity này lưu:

- Tên event.
- Mùa.
- Năm.
- Topic.
- Thời gian đăng ký.
- Thời gian bắt đầu/kết thúc thi.
- Trạng thái event.
- Cách chọn track.

## Giải thích từng dòng/khối

### Dòng 1

```java
package com.seal.hackathon.entity;
```

Class nằm trong package `entity`, nghĩa là đây là model map với database.

### Dòng 3

```java
import jakarta.persistence.*;
```

Import annotation JPA:

- `@Entity`
- `@Table`
- `@Id`
- `@GeneratedValue`
- `@Column`
- `@PrePersist`

### Dòng 4

```java
import lombok.*;
```

Import annotation Lombok:

- `@Getter`
- `@Setter`
- `@NoArgsConstructor`
- `@AllArgsConstructor`
- `@Builder`

### Dòng 6

```java
import java.time.LocalDateTime;
```

Dùng để lưu các mốc thời gian:

- registration start/end.
- event start/end.
- created at.

### Dòng 8

```java
@Entity
```

Báo với JPA/Hibernate rằng class này là entity.

### Dòng 9

```java
@Table(name = "HackathonEvent")
```

Map class này với bảng DB `HackathonEvent`.

### Dòng 10-14

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
```

Lombok tự sinh:

- Getter/setter.
- Constructor rỗng.
- Constructor đầy đủ field.
- Builder pattern.

### Dòng 15

```java
public class HackathonEvent {
```

Khai báo class `HackathonEvent`.

### Dòng 17-20: eventId

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
@Column(name = "event_id")
private Integer eventId;
```

Ý nghĩa:

- `eventId` là primary key.
- ID do DB tự tăng.
- Cột DB tên `event_id`.

### Dòng 22-23: name

```java
@Column(name = "name", nullable = false, length = 255)
private String name;
```

Tên event.

Bắt buộc có và tối đa 255 ký tự.

### Dòng 25-26: season

```java
@Column(name = "season", nullable = false, length = 20)
private String season;
```

Mùa/tổ chức theo đợt.

Theo service đã đọc, season hợp lệ gồm:

```text
SPRING
SUMMER
FALL
```

Entity chỉ lưu string. Logic validate season nằm trong `HackathonEventService`.

### Dòng 28-29: year

```java
@Column(name = "year", nullable = false)
private Integer year;
```

Năm tổ chức event.

Logic validate năm nằm trong service, không nằm trong entity.

### Dòng 31-32: description

```java
@Column(name = "description", columnDefinition = "TEXT")
private String description;
```

Mô tả event.

`columnDefinition = "TEXT"` nghĩa là DB dùng kiểu text dài hơn varchar thông thường.

### Dòng 34-38: topic

```java
// The overall competition theme for this hackathon ...
@Column(name = "topic", length = 500)
private String topic;
```

Chủ đề tổng của event.

Ví dụ comment nói có thể là:

```text
AI-Driven Smart Operations: Turning Real-Time IoT Data into Intelligent Actions
```

Topic khác với track. Topic là chủ đề chung của event, track là bảng/chủ đề con.

### Dòng 40-44: registration time

```java
@Column(name = "registration_start")
private LocalDateTime registrationStart;

@Column(name = "registration_end")
private LocalDateTime registrationEnd;
```

Thời gian bắt đầu và kết thúc đăng ký.

Trong source, một số logic lấy `status=OPEN` làm source of truth thay vì chỉ dựa vào registration window.

### Dòng 46-50: competition time

```java
@Column(name = "start_date", nullable = false)
private LocalDateTime startDate;

@Column(name = "end_date", nullable = false)
private LocalDateTime endDate;
```

Thời gian bắt đầu và kết thúc event.

Hai field này bắt buộc có.

### Dòng 52-54: status

```java
@Column(name = "status", nullable = false, length = 20)
@Builder.Default
private String status = "DRAFT";
```

Trạng thái event.

Mặc định là `DRAFT`.

Theo service đã đọc, các status chính:

```text
DRAFT
OPEN
SETUP
IN_PROGRESS
COMPLETED
CANCELLED
```

Logic chuyển trạng thái không nằm trong entity. Nó nằm trong `HackathonEventService`.

### Dòng 56-60: trackSelectionMode

```java
// How teams get a track: SELF_SELECT ... or RANDOM ...
@Column(name = "track_selection_mode", nullable = false, length = 20)
@Builder.Default
private String trackSelectionMode = "SELF_SELECT";
```

Quyết định cách team được gán track.

Có hai kiểu theo comment:

- `SELF_SELECT`: leader tự chọn track trong giai đoạn SETUP.
- `RANDOM`: coordinator bốc/thực hiện draw track.

Mặc định là `SELF_SELECT`.

### Dòng 62-63

```java
// created_by removed from schema who created the event is traceable via
// AuditLog (action = CREATE_EVENT) if needed.
```

Comment này nói entity không còn field `createdBy`.

Nếu cần biết ai tạo event, xem `AuditLog` với action `CREATE_EVENT`.

### Dòng 65-66: createdAt

```java
@Column(name = "created_at", nullable = false, updatable = false)
private LocalDateTime createdAt;
```

Thời điểm tạo event.

Không được update sau khi tạo.

### Dòng 68-73: onCreate

```java
@PrePersist
protected void onCreate() {
    if (createdAt == null) {
        createdAt = LocalDateTime.now();
    }
}
```

Trước khi insert event vào DB, JPA gọi method này.

Nếu `createdAt` chưa có, set bằng thời điểm hiện tại.

### Dòng 74

```java
}
```

Kết thúc class.

## Trạng thái event cần học kỹ

`HackathonEvent` chỉ lưu `status`, nhưng service mới xử lý logic.

Flow đã đọc trong `HackathonEventService`:

```text
DRAFT -> OPEN
OPEN -> SETUP
SETUP -> IN_PROGRESS
IN_PROGRESS -> COMPLETED
```

Ngoài ra có `CANCELLED` và reopen từ `COMPLETED`.

Điều kiện ví dụ:

- Muốn sang `SETUP`: không còn team pending.
- Muốn sang `IN_PROGRESS`: mỗi track phải có ít nhất 2 approved teams và không có approved team chưa gán track.
- Muốn complete event: dùng hàm riêng, không update status trực tiếp.

## Câu giáo viên có thể hỏi

### `HackathonEvent` có tự validate season/status không?

Không. Entity chỉ lưu dữ liệu. Validate nằm trong `HackathonEventService`.

### Vì sao `status` default là `DRAFT`?

Event mới tạo nên ở trạng thái nháp/thiết lập ban đầu, chưa mở đăng ký.

### `topic` khác `Track.name` như thế nào?

`topic` là chủ đề chung của toàn event. `Track.name` là tên từng bảng/chủ đề con.

### Vì sao không có `createdBy`?

Theo comment source, người tạo event được trace qua `AuditLog` với action `CREATE_EVENT`.

## Kiểm tra độ đầy đủ

Đã giải thích:

- Tất cả import.
- Tất cả annotation class.
- Tất cả field.
- Method `onCreate`.
- Ý nghĩa nghiệp vụ của status và track selection mode.
- Ranh giới giữa entity và service.

Không giải thích thừa:

- Không đi sâu vào toàn bộ `HackathonEventService` vì sẽ là bài service riêng.
- Không giải thích track/round chi tiết vì đó là entity riêng.

