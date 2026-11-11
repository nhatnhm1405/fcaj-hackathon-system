# Bài 02 - Giải thích pom.xml và application.properties

## Phạm vi

Bài này giải thích hai file:

```text
back-end/src/seal-api/pom.xml
back-end/src/seal-api/src/main/resources/application.properties
```

Hai file này không chứa nghiệp vụ hackathon trực tiếp. Chúng trả lời hai câu hỏi nền tảng:

```text
pom.xml: dự án dùng thư viện nào, build bằng công cụ nào?
application.properties: khi chạy app thì kết nối DB nào, port nào, JWT/mail/OAuth cấu hình ra sao?
```

## 1. pom.xml là gì?

Nếu bạn đã từng làm Servlet MVC, có thể bạn đã từng tự add file `.jar` vào project. Maven giải quyết việc đó bằng `pom.xml`.

`pom.xml` là file cấu hình Maven. Nó khai báo:

- Tên project.
- Version Java.
- Thư viện cần dùng.
- Plugin build.
- Cách compile/test/package app.

## 2. pom.xml - giải thích theo từng khối dòng

### Dòng 1

```xml
<?xml version="1.0" encoding="UTF-8"?>
```

Đây là khai báo file XML. Maven đọc được nội dung file theo chuẩn XML và encoding UTF-8.

### Dòng 2-3

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
```

Mở thẻ gốc `<project>`.

Ý nghĩa:

- File này là Maven POM.
- Khai báo namespace XML cho Maven.
- Khai báo schema để Maven/IDE validate cấu trúc file.

Bạn không cần sửa phần này nếu không đổi cấu trúc Maven.

### Dòng 4

```xml
<modelVersion>4.0.0</modelVersion>
```

Khai báo version model của Maven POM. Gần như mọi project Maven hiện đại đều dùng `4.0.0`.

### Dòng 5-10: Spring Boot parent

```xml
<parent>
	<groupId>org.springframework.boot</groupId>
	<artifactId>spring-boot-starter-parent</artifactId>
	<version>4.0.6</version>
	<relativePath/>
</parent>
```

Đây là project cha của Spring Boot.

Tác dụng:

- Quản lý version mặc định của nhiều dependency Spring.
- Cấu hình sẵn plugin build phù hợp Spring Boot.
- Giảm việc phải tự khai báo version cho từng starter.

Dòng cần nhớ nhất:

```xml
<version>4.0.6</version>
```

Source đang khai báo Spring Boot `4.0.6`. Tài liệu này chỉ ghi nhận đúng theo source hiện tại.

### Dòng 12-16: thông tin project

```xml
<groupId>com.seal</groupId>
<artifactId>hackathon</artifactId>
<version>0.0.1-SNAPSHOT</version>
<name>seal-hackathon</name>
<description>SEAL Software Engineering Hackathon Management System</description>
```

Ý nghĩa:

- `groupId`: nhóm/tổ chức của project, ở đây là `com.seal`.
- `artifactId`: tên artifact khi build, ở đây là `hackathon`.
- `version`: version project, `SNAPSHOT` nghĩa là bản đang phát triển.
- `name`: tên hiển thị của project.
- `description`: mô tả project.

### Dòng 18-22: properties

```xml
<properties>
	<java.version>21</java.version>
	<jjwt.version>0.12.6</jjwt.version>
	<lombok.version>1.18.46</lombok.version>
</properties>
```

Đây là các biến dùng lại trong file Maven.

- `java.version=21`: project compile bằng Java 21.
- `jjwt.version=0.12.6`: version thư viện JWT.
- `lombok.version=1.18.46`: version Lombok.

Ví dụ dòng sau:

```xml
<version>${jjwt.version}</version>
```

sẽ lấy giá trị `0.12.6`.

### Dòng 24-121: dependencies

`dependencies` là danh sách thư viện project cần.

#### Dòng 25-29: Spring Web MVC

```xml
<dependency>
	<groupId>org.springframework.boot</groupId>
	<artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>
```

Thư viện này giúp project viết REST API bằng Spring MVC.

Nó cung cấp:

- `@RestController`
- `@RequestMapping`
- `@GetMapping`
- `@PostMapping`
- JSON request/response
- Embedded web server theo cấu hình Spring Boot

So với Servlet MVC:

```text
Servlet/Controller cũ -> @RestController trong Spring Boot
```

#### Dòng 31-35: Spring Data JPA

```xml
<artifactId>spring-boot-starter-data-jpa</artifactId>
```

Thư viện này giúp làm việc với database thông qua JPA/Hibernate.

Nó cung cấp:

- `@Entity`
- `@Table`
- `@Id`
- `JpaRepository`
- `@Transactional`
- Hibernate ORM

So với Servlet MVC:

```text
DAO tự viết SQL -> Repository/JPA
```

#### Dòng 37-41: Spring Security

```xml
<artifactId>spring-boot-starter-security</artifactId>
```

Thư viện này xử lý bảo mật:

- Đăng nhập/đăng xuất.
- Authentication: user là ai?
- Authorization: user có quyền gì?
- Filter security.
- `@PreAuthorize`.

Dự án của bạn dùng nó kết hợp JWT và role.

#### Dòng 43-47: OAuth2 Client

```xml
<artifactId>spring-boot-starter-security-oauth2-client</artifactId>
```

Thư viện này phục vụ đăng nhập bằng bên thứ ba:

- Google.
- GitHub.

Trong source, cấu hình OAuth2 nằm trong `application.properties` và logic xử lý nằm trong package `security/oauth2`.

#### Dòng 49-53: Bean Validation

```xml
<artifactId>spring-boot-starter-validation</artifactId>
```

Thư viện này cho phép validate DTO bằng annotation.

Ví dụ:

```java
@NotBlank
@Email
@Size(min = 8)
```

Nếu request từ React thiếu field hoặc sai format, Spring có thể trả lỗi trước khi vào service.

#### Dòng 55-59: Mail

```xml
<artifactId>spring-boot-starter-mail</artifactId>
```

Dùng để gửi email qua SMTP.

Dự án có chức năng:

- Gửi mail duyệt/từ chối account.
- Gửi OTP reset password.

#### Dòng 61-66: MySQL Driver

```xml
<groupId>com.mysql</groupId>
<artifactId>mysql-connector-j</artifactId>
<scope>runtime</scope>
```

Driver này giúp Java kết nối MySQL.

`scope=runtime` nghĩa là:

- Lúc compile không cần trực tiếp.
- Lúc chạy app thì cần để kết nối DB.

#### Dòng 68-74: Lombok

```xml
<artifactId>lombok</artifactId>
<version>${lombok.version}</version>
<optional>true</optional>
```

Lombok giúp giảm boilerplate code.

Ví dụ trong entity có:

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
```

Lombok sẽ tự sinh getter/setter/constructor/builder khi compile.

Nếu bạn chỉ học OOP, cần nhớ:

```text
Trong source không thấy getter/setter, nhưng khi compile Lombok sẽ sinh ra.
```

#### Dòng 76-82: DevTools

```xml
<artifactId>spring-boot-devtools</artifactId>
<scope>runtime</scope>
<optional>true</optional>
```

Hỗ trợ hot reload khi dev local. Đây không phải nghiệp vụ chính.

#### Dòng 84-101: JWT library

```xml
<artifactId>jjwt-api</artifactId>
<artifactId>jjwt-impl</artifactId>
<artifactId>jjwt-jackson</artifactId>
```

Ba dependency này đi cùng nhau để tạo và đọc JWT token.

- `jjwt-api`: API để code gọi.
- `jjwt-impl`: implementation chạy runtime.
- `jjwt-jackson`: xử lý JSON trong JWT.

Trong source, JWT được xử lý chủ yếu ở:

```text
security/JwtService.java
security/JwtAuthenticationFilter.java
security/JwtCookieFactory.java
```

#### Dòng 103-108: Swagger/OpenAPI

```xml
<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
<version>2.8.8</version>
```

Thư viện này tạo UI xem/test API.

Theo `application.properties`, URL là:

```text
http://localhost:8080/swagger-ui.html
```

#### Dòng 110-120: Testing

```xml
<artifactId>spring-boot-starter-test</artifactId>
<artifactId>spring-security-test</artifactId>
<scope>test</scope>
```

Dùng cho test.

- `spring-boot-starter-test`: JUnit, Mockito, assertion, Spring test.
- `spring-security-test`: test API/security với user/role giả lập.

`scope=test` nghĩa là chỉ dùng khi chạy test, không đóng vào app production.

### Dòng 123-164: build plugins

Phần này cấu hình Maven build.

#### Spring Boot Maven Plugin

```xml
<artifactId>spring-boot-maven-plugin</artifactId>
```

Plugin này giúp build/chạy Spring Boot app bằng Maven.

Phần exclude Lombok:

```xml
<excludes>
	<exclude>
		<groupId>org.projectlombok</groupId>
		<artifactId>lombok</artifactId>
	</exclude>
</excludes>
```

Ý nghĩa: Lombok chỉ cần lúc compile, không cần đóng vào file app runtime.

#### Maven Compiler Plugin

```xml
<artifactId>maven-compiler-plugin</artifactId>
```

Plugin này cấu hình compile Java.

Dòng:

```xml
<proc>full</proc>
```

cho phép annotation processing đầy đủ, cần cho Lombok sinh code.

Phần:

```xml
<annotationProcessorPaths>
	<path>
		<groupId>org.projectlombok</groupId>
		<artifactId>lombok</artifactId>
		<version>${lombok.version}</version>
	</path>
</annotationProcessorPaths>
```

nói compiler dùng Lombok làm annotation processor.

Hai execution:

```xml
<phase>compile</phase>
<goal>compile</goal>
```

và

```xml
<phase>test-compile</phase>
<goal>testCompile</goal>
```

đảm bảo compile source chính và source test.

## 3. application.properties là gì?

`application.properties` là file cấu hình runtime của Spring Boot.

Nếu `pom.xml` trả lời "dự án có thư viện gì", thì `application.properties` trả lời:

```text
App chạy với tên gì?
Kết nối DB nào?
Port nào?
JWT sống bao lâu?
OAuth2 dùng client nào?
File upload lưu ở đâu?
Mail gửi bằng host nào?
```

## 4. application.properties - giải thích theo từng khối

### Dòng 1

```properties
spring.application.name=seal-hackathon
```

Đặt tên ứng dụng Spring Boot là `seal-hackathon`.

Tên này có thể xuất hiện trong log, monitoring, hoặc cấu hình Spring.

### Dòng 3-5: đọc file .env

```properties
spring.config.import=optional:file:./.env[.properties]
```

App sẽ thử đọc file `.env` trong folder chạy app.

- `optional`: không có file `.env` thì app vẫn có thể chạy.
- `.env[.properties]`: file có dạng key=value.

Ví dụ:

```properties
DB_USERNAME=root
DB_PASSWORD=...
GOOGLE_CLIENT_ID=...
```

Những giá trị này được dùng để thay vào các placeholder `${...}` bên dưới.

### Dòng 7-15: Database

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/seal_hackathon?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true&characterEncoding=UTF-8
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:TrangNhi2004}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

Ý nghĩa:

- App kết nối MySQL trên máy local, port `3306`.
- Database tên `seal_hackathon`.
- Nếu không có env `DB_USERNAME`, dùng default `root`.
- Nếu không có env `DB_PASSWORD`, dùng default trong file.
- Driver MySQL là `com.mysql.cj.jdbc.Driver`.

Cú pháp:

```properties
${DB_USERNAME:root}
```

nghĩa là:

```text
Nếu có biến DB_USERNAME thì dùng biến đó.
Nếu không có thì dùng root.
```

Cần lưu ý bảo mật: mật khẩu default nằm trong file source không nên dùng cho production.

### Dòng 17-29: JPA/Hibernate

```properties
spring.jpa.hibernate.ddl-auto=${DDL_AUTO:update}
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
```

Ý nghĩa:

- `ddl-auto=update`: Hibernate dựa vào entity để cập nhật schema DB khi app start.
- `show-sql=true`: in SQL ra console.
- `format_sql=true`: format SQL để dễ đọc.
- `PhysicalNamingStrategyStandardImpl`: giữ nguyên tên bảng/cột như trong `@Table`, `@Column`.

Quan trọng:

```text
Dự án này đang theo hướng code-first.
Entity là nguồn chính để tạo/cập nhật schema.
```

### Dòng 31-39: Demo seed

```properties
app.seed.scenario=${SEED_SCENARIO:NONE}
```

Cấu hình có seed demo data hay không.

Giá trị hợp lệ theo comment source:

- `NONE`: không seed demo data.
- `S1`: registration open.
- `S25`: final round scored, sẵn sàng rank/award.
- `S3`: completed.

Ngay cả khi `NONE`, source vẫn có `DataSeeder` seed role/admin cần thiết.

### Dòng 41-52: JWT

```properties
app.jwt.secret=${JWT_SECRET:3cfa76ef14937c1c0ea519f8fc057a80fcd04a7420f8e8bcd0a7567c272e007b}
app.jwt.expiration-ms=86400000
app.jwt.refresh-expiration-ms=604800000
app.jwt.cookie.secure=${JWT_COOKIE_SECURE:false}
```

JWT là token dùng để backend biết user đã đăng nhập là ai.

- `secret`: khóa ký JWT.
- `expiration-ms=86400000`: token sống 24 giờ.
- `refresh-expiration-ms=604800000`: remember-me/refresh logic sống 7 ngày.
- `cookie.secure=false`: local dev dùng HTTP nên cookie không bắt buộc HTTPS.

Production nên set:

```properties
JWT_COOKIE_SECURE=true
```

nếu dùng HTTPS.

### Dòng 54-71: OAuth2 Google/GitHub

Google:

```properties
spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID:your-google-client-id}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET:your-google-client-secret}
spring.security.oauth2.client.registration.google.scope=email,profile
spring.security.oauth2.client.registration.google.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}
```

GitHub:

```properties
spring.security.oauth2.client.registration.github.client-id=${GITHUB_CLIENT_ID:your-github-client-id}
spring.security.oauth2.client.registration.github.client-secret=${GITHUB_CLIENT_SECRET:your-github-client-secret}
spring.security.oauth2.client.registration.github.scope=user:email
spring.security.oauth2.client.registration.github.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}
```

Ý nghĩa:

- App hỗ trợ login bằng Google/GitHub.
- `client-id`, `client-secret` nên lấy từ `.env`.
- `scope` nói ứng dụng xin quyền lấy email/profile.
- `redirect-uri` là URL provider gọi lại backend sau khi user login thành công.

### Dòng 73-80: App config

```properties
app.frontend.url=${APP_FRONTEND_URL:http://localhost:5173}
app.password-reset.otp-expiration-minutes=${PASSWORD_RESET_OTP_EXPIRATION_MINUTES:10}
```

- Frontend React chạy mặc định ở `http://localhost:5173`.
- OTP reset password sống 10 phút nếu không override.

### Dòng 82-88: Swagger/OpenAPI

```properties
springdoc.api-docs.path=/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.operationsSorter=method
```

Dùng để xem/test REST API.

URL:

```text
http://localhost:8080/swagger-ui.html
```

### Dòng 90-96: Server

```properties
server.port=8080
server.error.include-message=always
server.servlet.session.cookie.same-site=lax
```

- Backend chạy port `8080`.
- Lỗi server có include message.
- OAuth2 cần servlet session để giữ state giữa các lần redirect.
- SameSite Lax giúp cookie hoạt động tốt hơn với redirect OAuth2 mà vẫn giảm rủi ro CSRF.

### Dòng 98-110: Upload file

```properties
app.upload.dir=${UPLOAD_DIR:uploads}
spring.servlet.multipart.max-file-size=20MB
spring.servlet.multipart.max-request-size=21MB
app.problem.dir=${PROBLEM_DIR:protected/problems}
```

Ý nghĩa:

- Avatar và file public lưu ở folder `uploads`.
- Giới hạn file upload 20MB.
- Tổng request upload tối đa 21MB.
- Đề bài của track lưu ở `protected/problems`.

Khác biệt quan trọng:

```text
uploads: có thể serve public qua /uploads/**
protected/problems: không public, phải qua API có auth check
```

### Dòng 112-119: Gemini AI

```properties
app.ai.gemini.api-key=${GEMINI_API_KEY:}
app.ai.gemini.model=${GEMINI_MODEL:gemini-2.5-flash}
app.ai.gemini.base-url=${GEMINI_BASE_URL:https://generativelanguage.googleapis.com/v1beta}
```

Dùng cho AI Judge Assistant.

Nếu không có `GEMINI_API_KEY`, endpoint AI sẽ báo lỗi cấu hình rõ ràng, không tự chấm điểm.

### Dòng 121-131: GitHub repo analysis

```properties
app.github.token=${GITHUB_TOKEN:}
app.github.api-base-url=${GITHUB_API_BASE_URL:https://api.github.com}
```

Dùng để đọc repo GitHub của bài nộp khi AI assistant phân tích submission.

Nếu không có token:

- Vẫn đọc được public repo.
- Bị rate limit thấp hơn.
- Không đọc được private repo nếu token không có quyền.

### Dòng 133-147: Mail SMTP

```properties
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME:your-email@gmail.com}
spring.mail.password=${MAIL_PASSWORD:your-app-password}

spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

spring.mail.properties.mail.smtp.connectiontimeout=5000
spring.mail.properties.mail.smtp.timeout=3000
spring.mail.properties.mail.smtp.writetimeout=5000
spring.mail.default-encoding=UTF-8
```

Dùng Gmail SMTP để gửi mail.

- Port `587` là port SMTP với STARTTLS.
- `auth=true`: cần username/password.
- `starttls.enable=true`: bật mã hóa TLS sau khi kết nối.
- Timeout để tránh app đợi quá lâu nếu mail server lỗi.
- Encoding UTF-8 để gửi nội dung tiếng Việt/an toàn Unicode.

## 5. Tổng kết liên hệ với Servlet MVC

Nếu bạn từng cấu hình JDBC/DAO/Servlet thủ công, trong dự án này:

```text
JAR thủ công -> pom.xml dependency
web.xml -> Spring Boot auto config + config classes
DAO config -> Spring Data JPA + datasource properties
Servlet URL mapping -> @RestController + @RequestMapping
Filter login thủ công -> Spring Security filter chain
JSP view -> React render UI, backend trả JSON
```

## Câu giáo viên có thể hỏi

### `pom.xml` dùng để làm gì?

Dùng để Maven biết project là gì, dùng Java version nào, cần dependency nào và build bằng plugin nào.

### `application.properties` dùng để làm gì?

Dùng để cấu hình app lúc runtime: database, JPA, JWT, OAuth2, port, upload, AI, GitHub, mail.

### Vì sao cần `spring-boot-starter-data-jpa`?

Để dùng entity/repository và Hibernate ORM thay cho việc tự viết DAO SQL thủ công.

### Vì sao có `mysql-connector-j`?

Để Java kết nối được MySQL.

### Vì sao cần `.env`?

Để đưa secret như DB password, OAuth secret, mail password, Gemini key ra ngoài source code.

### `ddl-auto=update` có nghĩa gì?

Hibernate dựa vào entity để cập nhật schema DB khi app start. Tiện cho dev, nhưng production nên cẩn thận và có thể dùng `validate`.

## Kiểm tra độ đầy đủ

Đã giải thích:

- Các khối chính trong `pom.xml`.
- Tất cả dependency và plugin có trong source.
- Các khối chính trong `application.properties`.
- Ý nghĩa các placeholder `${KEY:default}`.
- Mapping sang kiến thức Servlet MVC.

Không giải thích thừa:

- Không đi sâu vào code `JwtService`, `SecurityConfig`, `AuthService` vì chúng thuộc bài security/service riêng.
- Không giải thích chi tiết từng entity vì đó thuộc bài entity.

