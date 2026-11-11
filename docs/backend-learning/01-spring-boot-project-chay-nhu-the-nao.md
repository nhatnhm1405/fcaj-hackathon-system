# Bài 01 - Spring Boot project này chạy như thế nào

## Phạm vi

Bài này giải thích file:

```text
back-end/src/seal-api/src/main/java/com/seal/hackathon/HackathonApplication.java
```

Source thực tế:

```java
package com.seal.hackathon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HackathonApplication {

	public static void main(String[] args) {
		SpringApplication.run(HackathonApplication.class, args);
	}
}
```

## Ý tưởng lớn

Nếu bạn từng học Servlet MVC, bạn có thể nhớ rằng một ứng dụng web cần có điểm khởi động: server chạy, load servlet, load config, rồi mới nhận request.

Với Spring Boot, file `HackathonApplication.java` là điểm khởi động đó.

Nó làm 3 việc lớn:

1. Khởi động Spring Boot app.
2. Báo Spring quét các package bên dưới `com.seal.hackathon`.
3. Tạo và quản lý các object như controller, service, repository, security config.

## Giải thích từng dòng

### Dòng 1

```java
package com.seal.hackathon;
```

Dòng này khai báo class `HackathonApplication` thuộc package `com.seal.hackathon`.

Trong Java, `package` giống như folder logic để sắp xếp class. Dự án này đặt package gốc là `com.seal.hackathon`, nên các package con như sau đều nằm bên dưới nó:

```text
com.seal.hackathon.config
com.seal.hackathon.controller
com.seal.hackathon.entity
com.seal.hackathon.repository
com.seal.hackathon.service
com.seal.hackathon.security
```

Đây là điểm rất quan trọng. Vì class main nằm ở package gốc, Spring Boot có thể tự động scan các class trong package con.

### Dòng 3

```java
import org.springframework.boot.SpringApplication;
```

Dòng này import class `SpringApplication` của Spring Boot.

`SpringApplication` là class có nhiệm vụ chạy ứng dụng Spring Boot. Bạn có thể hiểu nó như bộ máy khởi động server và khởi tạo toàn bộ Spring container.

So với Servlet MVC cũ:

```text
Servlet MVC: Tomcat load web.xml / servlet / listener
Spring Boot: SpringApplication.run(...) khởi động app và embedded server
```

### Dòng 4

```java
import org.springframework.boot.autoconfigure.SpringBootApplication;
```

Dòng này import annotation `@SpringBootApplication`.

Annotation là metadata gắn vào class. Spring đọc annotation này để biết class nào là class cấu hình chính của ứng dụng.

### Dòng 6

```java
@SpringBootApplication
```

Đây là annotation quan trọng nhất trong file này.

`@SpringBootApplication` gồm 3 ý nghĩa lớn:

```text
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan
```

Bạn không cần học thuộc tên annotation con ngay lập tức, nhưng cần hiểu ý nghĩa:

- Nó nói đây là class chính của Spring Boot app.
- Nó bật auto-configuration: Spring tự cấu hình nhiều thứ dựa trên dependency trong `pom.xml`.
- Nó bật component scanning: Spring tìm các class có `@Controller`, `@Service`, `@Repository`, `@Component`, `@Configuration`.

Vì class này nằm ở `com.seal.hackathon`, Spring sẽ scan các package con bên dưới package này.

Ví dụ:

```text
com.seal.hackathon.service.AuthService
com.seal.hackathon.controller.AuthController
com.seal.hackathon.repository.UserRepository
```

Nếu một class nằm ngoài package gốc, Spring có thể không tự scan thấy.

### Dòng 7

```java
public class HackathonApplication {
```

Đây là khai báo class Java bình thường.

- `public`: class có thể được truy cập từ bên ngoài package.
- `class`: khai báo class.
- `HackathonApplication`: tên class.

Trong Spring Boot, tên class main thường có dạng `TênDựÁnApplication`.

### Dòng 9

```java
public static void main(String[] args) {
```

Đây là hàm main của Java.

Trong Java core, chương trình bắt đầu từ:

```java
public static void main(String[] args)
```

Giải thích từng phần:

- `public`: JVM có thể gọi được method này.
- `static`: JVM gọi method mà không cần tạo object `HackathonApplication`.
- `void`: method không trả về giá trị.
- `main`: tên method đặc biệt được JVM nhận diện.
- `String[] args`: mảng tham số dòng lệnh nếu chạy app kèm argument.

### Dòng 10

```java
SpringApplication.run(HackathonApplication.class, args);
```

Đây là dòng thực sự khởi động Spring Boot.

Ý nghĩa:

- `SpringApplication.run(...)`: chạy ứng dụng Spring Boot.
- `HackathonApplication.class`: truyền vào class cấu hình chính.
- `args`: truyền tham số dòng lệnh nếu có.

Sau dòng này, Spring Boot sẽ:

1. Đọc `pom.xml` để biết có các starter nào.
2. Đọc `application.properties` để lấy cấu hình.
3. Khởi tạo embedded web server.
4. Tạo Spring ApplicationContext.
5. Scan các package con của `com.seal.hackathon`.
6. Tạo bean cho controller, service, repository, config, security.
7. Kết nối database nếu có JPA/DataSource.
8. Sẵn sàng nhận HTTP request.

Nói ngắn gọn:

```text
Dòng này biến code Java của bạn thành một backend server đang chạy.
```

### Dòng 11-12

```java
	}
}
```

Dòng 11 đóng method `main`.

Dòng 12 đóng class `HackathonApplication`.

Không có logic nghiệp vụ nào nằm ở hai dòng này.

## So sánh với Servlet MVC

Nếu Servlet MVC cũ có flow:

```text
Tomcat chạy
-> load web.xml
-> load Servlet
-> map URL vào Servlet
```

Thì Spring Boot có flow:

```text
main()
-> SpringApplication.run()
-> auto config
-> scan controller/service/repository
-> nhận request REST API
```

## Câu giáo viên có thể hỏi

### Vì sao class main đặt ở package `com.seal.hackathon`?

Vì đây là package gốc. Đặt class main ở đây giúp Spring Boot component-scan được các package con như `controller`, `service`, `repository`, `security`.

### `@SpringBootApplication` có tác dụng gì?

Nó đánh dấu đây là class khởi động Spring Boot, bật auto configuration và component scanning.

### `SpringApplication.run(...)` làm gì?

Nó khởi động toàn bộ Spring Boot app, tạo Spring container, khởi tạo server, đọc cấu hình và sẵn sàng nhận request.

### File này có chứa nghiệp vụ không?

Không. File này chỉ là entry point. Nghiệp vụ nằm chủ yếu trong package `service`.

## Kiểm tra độ đầy đủ

Đã giải thích:

- Package declaration.
- Import.
- Annotation.
- Class declaration.
- Method `main`.
- Lệnh `SpringApplication.run`.
- Liên hệ với Servlet MVC.

Không giải thích thừa:

- Không đi sâu vào từng service/controller vì bài này chỉ phụ trách entry point.
- Không giải thích chi tiết JPA/security vì các phần đó ở bài riêng.

