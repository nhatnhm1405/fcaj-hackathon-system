# AI LOG — Thêm cổng test (vitest + JUnit) trước khi deploy EC2 qua GitHub Actions — 2026-07-23

> Phiên này gồm 2 việc nhỏ không liên quan rồi tới 1 việc chính:
>
> 1. **Kiểm tra `.env.example`** cho `back-end/src/seal-api` — hoá ra đã tồn
>    tại sẵn và khớp với `.env` thật, không cần tạo gì mới.
> 2. **Việc chính: thêm cổng test trước deploy** — sửa
>    `.github/workflows/deploy.yml` để bắt buộc vitest (frontend) và JUnit
>    (backend) phải xanh thì mới cho chạy job SSH deploy lên EC2. Thảo luận
>    kỹ trước (đúng yêu cầu "bàn trước, khoan code"), qua Plan Mode, rồi mới
>    code.
> 3. Trong lúc verify, **tự phát hiện 2 vấn đề có thật** không nằm trong kế
>    hoạch ban đầu: `mvnw` thiếu quyền thực thi trong git index (sẽ vỡ trên
>    Linux runner), và `npm test` đang **fail sẵn trên `develop` hiện tại**
>    vì vitest vô tình nuốt luôn spec Playwright trong thư mục `e2e/`. Cả 2
>    đã tìm ra nguyên nhân và sửa, verify lại xanh.
>
> **Đã code xong, verify xong (406/406 backend test xanh, 147/147 frontend
> test xanh) — chưa commit**, user chưa yêu cầu.

---

## 1. Việc phụ — `.env.example` cho `seal-api`

User hỏi "sinh cho tôi 1 file `.env-example` từ `.env`". Kiểm tra
`back-end/src/seal-api/` thì thấy **`.env.example` đã tồn tại sẵn**, và nội
dung đã khớp đầy đủ các key có trong `.env` local hiện tại (DB, OAuth
Google/GitHub, Mail SMTP, Gemini, GitHub token) — cộng thêm vài biến khác
(`JWT_SECRET`, `SEED_SCENARIO`, `APP_FRONTEND_URL`,
`PASSWORD_RESET_OTP_EXPIRATION_MINUTES`, `UPLOAD_DIR`, `PROBLEM_DIR`) mà
`application.properties` có dùng nhưng `.env` local của user không set (dùng
default). Không có gì để tạo mới — báo lại cho user, không tự ý sửa gì.

---

## 2. Bối cảnh việc chính

`.github/workflows/deploy.yml` trước đó: `on: push` vào `develop` → SSH
thẳng vào EC2, `docker compose up -d --build`. **Không có bước test nào cả**
trước khi deploy. User muốn thêm cổng vitest + JUnit "2 cục đó qua rồi mới
tới EC2", yêu cầu tường minh **"bàn trước, khoan code"**.

---

## 3. Thảo luận (trước khi code)

### 3.1. Khảo sát hiện trạng

- Frontend (`front-end/src/seal-web/package.json`): có sẵn
  `"test": "vitest run"`, dùng RTL + jsdom + `@testing-library/*`.
- Backend (`back-end/src/seal-api/pom.xml`): Java 21, Spring Boot 4.0.6,
  ~25 file test JUnit dưới `src/test/java` — hầu hết là service test dùng
  Mockito, **không** đụng DB thật.
- Phát hiện quan trọng: **`HackathonApplicationTests`** là file test duy
  nhất dùng `@SpringBootTest` (load toàn bộ Spring context) — mà
  `application.properties` trỏ cứng
  `jdbc:mysql://localhost:3306/seal_hackathon?...` với
  `DB_USERNAME`/`DB_PASSWORD` mặc định `root`/`TrangNhi2004`, **không hề có**
  `application-test.properties` hay profile H2 nào. Chạy `mvn test` trên
  runner GitHub Actions trần (không có MySQL) sẽ fail ngay ở test này.

### 3.2. Các điểm cần chốt với user

Đặt câu hỏi qua `AskUserQuestion`, user chốt:

| Câu hỏi | Lựa chọn user chốt |
|---|---|
| Fix DB-dependent test bằng cách nào? | **(b)** H2 in-memory profile riêng cho test, không đụng gì code thật/`HackathonApplicationTests.java` |
| Trigger: chỉ push hay cả PR? | **Cả push lẫn pull_request** — PR vào `develop` cũng chạy test (fail fast trước khi merge) |
| Kiến trúc file: gộp hay tách? | **Gộp chung 1 file `deploy.yml`** với 3 job, đơn giản dễ đọc |
| Playwright e2e có chạy trong CI không? | **Không** — chỉ vitest unit, e2e cần app chạy thật, nặng và phức tạp hơn nhiều |

### 3.3. Thiết kế chốt cho phần H2

- Thêm dependency `com.h2database:h2` scope `test` vào `pom.xml` — chỉ nằm
  trong test classpath.
- File **mới hoàn toàn** `src/test/resources/application-test.properties`
  — không sửa file prod nào.
- Kích hoạt qua `-Dspring.profiles.active=test` khi chạy `mvn test` trong
  workflow — **không sửa** `HackathonApplicationTests.java`.

---

## 4. Plan Mode

Chuyển sang Plan Mode (`ExitPlanMode`), viết plan chi tiết tại
`l-m-i-spicy-honey.md`, đọc thêm `application.properties`,
`HackathonApplicationTests.java`, kiểm tra `mvnw`/`package-lock.json` tồn
tại. Plan được **user approve** nguyên trạng, không có điều chỉnh thêm.

---

## 5. Code

### 5.1. `back-end/src/seal-api/pom.xml`

Thêm vào cuối khối `<dependencies>` (Spring Boot BOM tự quản lý version,
không cần khai báo `<version>`):

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

### 5.2. File mới `back-end/src/seal-api/src/test/resources/application-test.properties`

```properties
spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
```

Dùng H2 `MODE=MySQL` để giảm ma sát cú pháp giữa 2 dialect. Chỉ được đọc khi
profile `test` active — không ảnh hưởng dev/prod.

### 5.3. `.github/workflows/deploy.yml` — viết lại thành 3 job

- **Trigger**: `push` (branches: `develop`) **+** `pull_request` (branches:
  `develop`).
- **`test-frontend`**: checkout → `actions/setup-node@v4` (Node 20,
  `cache: npm`, `cache-dependency-path:
  front-end/src/seal-web/package-lock.json`) → `npm ci` → `npm test`, tất cả
  với `working-directory: front-end/src/seal-web`.
- **`test-backend`**: checkout → `actions/setup-java@v4` (`java-version: 21`,
  `distribution: temurin`, `cache: maven`) → `./mvnw test
  -Dspring.profiles.active=test`, `working-directory: back-end/src/seal-api`.
- **`deploy`**: giữ nguyên toàn bộ step SSH cũ, chỉ thêm
  `needs: [test-frontend, test-backend]` và
  `if: github.event_name == 'push'` — đảm bảo **không bao giờ** chạy trên
  sự kiện `pull_request`, và chỉ chạy sau khi cả 2 job test xanh.

---

## 6. 2 vấn đề tự phát hiện khi verify (ngoài kế hoạch ban đầu)

### 6.1. `mvnw` thiếu quyền thực thi trong git index

`git ls-files -s back-end/src/seal-api/mvnw` cho mode `100644` (không phải
`100755`). Trên Linux runner, bước `./mvnw test` sẽ fail với "Permission
denied". Sửa bằng `git update-index --chmod=+x back-end/src/seal-api/mvnw`
— chỉ đổi mode trong git index, không cần quyền chmod thật trên filesystem
Windows.

### 6.2. `npm test` đang fail sẵn trên code hiện tại — không liên quan gì tới thay đổi hôm nay

Khi verify local, `npx vitest run` báo lỗi:

```
Error: Playwright Test did not expect test.describe.configure() to be called here.
❯ e2e/scenario-1/00_open_account_team_flow.spec.ts:298:15
```

Nguyên nhân: `vite.config.ts` (khối `test:`) **không hề có `exclude`** cho
thư mục `e2e/` — vitest default exclude chỉ chừa `node_modules`, `dist`,
`.git`... không tự loại `e2e/**`. Trong khi `playwright.config.ts` đã khai
`testDir: './e2e'` riêng cho Playwright. Kết quả: `vitest run` tự nhặt luôn
file spec Playwright rồi crash vì `test.describe.configure()` không hợp lệ
ngoài Playwright runner.

Đây là bug **có sẵn từ trước**, không phải do các thay đổi hôm nay gây ra
(xác nhận qua `git status` sạch trước khi bắt đầu) — nhưng nếu không sửa thì
cổng CI mới thêm sẽ **luôn luôn đỏ**, vô dụng ngay từ lần chạy đầu tiên.

Sửa: thêm `exclude` vào `vite.config.ts`:

```ts
test: {
  globals: true,
  environment: 'jsdom',
  setupFiles: './src/test/setup.ts',
  css: false,
  exclude: ['**/node_modules/**', '**/dist/**', './e2e/**'],
},
```

---

## 7. Verify

### 7.1. Backend

```
./mvnw test -Dspring.profiles.active=test
Tests run: 406, Failures: 0, Errors: 0 — BUILD SUCCESS
```

Bao gồm cả `HackathonApplicationTests` (load context Spring thật qua H2) —
xanh, xác nhận profile test hoạt động đúng, `DataSeeder` chạy được (seed 4
role + tài khoản admin bootstrap) trên DB H2 in-memory.

### 7.2. Frontend

`npm ci` tại chỗ bị `EPERM` khi unlink `lightningcss-win32-x64-msvc` — do 2
process `node.exe` đang chạy (dev server) giữ file, **không đụng vào** các
process này (có thể là dev server đang chạy của user). Thay vào đó: copy
toàn bộ project (trừ `node_modules`) qua thư mục scratchpad bằng
`robocopy /XD node_modules`, `npm ci` sạch ở đó, verify xong thì xoá thư
mục tạm.

- Lần 1 (trước khi sửa `vite.config.ts`): 1 suite fail (`e2e/scenario-1/...`),
  147 test còn lại pass.
- Lần 2 (sau khi thêm `exclude`): **147/147 pass**, không còn đụng tới
  `e2e/`.

### 7.3. Cách user tự test thật (đã trả lời nhanh theo yêu cầu)

Workflow GitHub Actions chỉ chạy trên cloud khi có sự kiện `push`/
`pull_request` thật — không test được tại local (trừ dùng `act`, nhưng job
`deploy` cần secrets thật nên không verify được đầy đủ qua đó). Cách khuyến
nghị: commit + push → mở PR vào `develop` → 2 job test chạy (job `deploy`
không chạy nhờ điều kiện `if: push`, an toàn không đụng EC2) → xem tab
Actions confirm xanh → merge vào `develop` mới thấy `deploy` chạy sau khi
test pass.

---

## 8. Danh sách file thay đổi

```
.github/workflows/deploy.yml                                     (sửa — 3 job)
back-end/src/seal-api/pom.xml                                     (sửa — +h2 test dep)
back-end/src/seal-api/mvnw                                        (sửa mode — +x trong git index)
back-end/src/seal-api/src/test/resources/application-test.properties  (mới)
front-end/src/seal-web/vite.config.ts                             (sửa — +exclude e2e/)
```

---

## 9. Còn lại / cố tình chưa làm

- **Chưa commit** — toàn bộ thay đổi vẫn ở working tree, `git status` xác
  nhận (mode-change của `mvnw` đã tự stage do `git update-index --chmod`,
  các file khác chưa `git add`).
- **Chưa bật branch protection** trên GitHub (Require status checks) — nếu
  muốn PR *bắt buộc* xanh mới merge được thì cần bật thêm trong GitHub
  Settings, việc này nằm ngoài phạm vi file YAML.
- **Chưa chạy thật trên GitHub Actions** — mới verify local (backend qua
  `mvnw` trực tiếp, frontend qua bản copy scratchpad); cần push/mở PR thật
  để xác nhận `actions/setup-node`, `actions/setup-java`, cache, và
  `needs`/`if` hoạt động đúng trên runner thật.
