# Ghi chú học backend

Mục tiêu của bộ tài liệu này là giúp bạn học backend của dự án theo cách đi từ nền Servlet MVC sang Spring Boot + React + REST API.

Quy tắc biên soạn:

- Mỗi bài là một file `.md`.
- Mỗi bài có phần "Phạm vi" để biết chính xác bài đó giải thích file nào.
- Không gộp quá nhiều source file vào một bài, vì như vậy sẽ khó học và dễ lặp lại.
- Với file nền tảng nhỏ, tài liệu giải thích gần như từng dòng.
- Với file lớn, tài liệu đi theo từng khối code, từng method, sau đó mới drill tiếp nếu cần.
- Không coi SQL script là nguồn nghiệp vụ chính vì backend đang cấu hình JPA code-first.

Thứ tự học hiện tại:

1. [Bài 01 - Spring Boot project này chạy như thế nào](./01-spring-boot-project-chay-nhu-the-nao.md)
2. [Bài 02 - Giải thích pom.xml và application.properties](./02-pom-xml-application-properties.md)
3. [Bài 03 - Package entity và cách đọc entity](./03-package-entity-cach-doc.md)
4. [Bài 04 - Entity User.java](./04-entity-user.md)
5. [Bài 05 - Entity HackathonEvent.java](./05-entity-hackathon-event.md)

Nhận định đã xác minh từ source:

- Backend Spring Boot nằm ở `back-end/src/seal-api`.
- Package gốc là `com.seal.hackathon`.
- Có 261 file Java production trong `src/main/java`.
- Có 22 file test trong `src/test/java`.
- Backend dùng Java 21, Spring Boot, JPA/Hibernate, Spring Security, OAuth2, MySQL, JWT, Mail, Swagger/OpenAPI.
- Entry point của app là `HackathonApplication.java`.

Lưu ý về cách học:

Nếu đọc theo alphabet, bạn sẽ rất dễ lạc. Cách đọc đúng là:

```text
Entity -> Repository -> Service -> Controller -> DTO -> Security/Test
```

Với mỗi chức năng, đọc theo flow:

```text
React gọi API
-> Controller nhận request
-> DTO giữ dữ liệu request
-> Service xử lý nghiệp vụ
-> Repository lấy/lưu DB
-> Entity đại diện dữ liệu
-> Response DTO trả JSON về React
```

