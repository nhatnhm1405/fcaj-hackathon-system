# Báo cáo tích hợp `hoangnhat-draft` vào `develop-draft`

Ngày kiểm tra: 2026-06-26

## 1. Phạm vi kiểm tra

- Nhánh hiện tại: `develop-draft`
- Nhánh gốc để mở PR: `develop`
- Nhánh nguồn cần lấy code: `hoangnhat-draft`
- Mục tiêu: tạo một nhánh mới từ `develop`, đưa lại phần code cần giữ của `hoangnhat-draft`, giữ frontend/database mới từ `develop`, sau đó PR `develop-draft -> develop`.

Commit quan trọng hiện tại:

- `develop`: `e96ad20` - Merge pull request #265, revert PR cũ từ `hoangnhat-draft`
- `hoangnhat-draft`: `69d3b26` - Update database scripts from develop
- `develop-draft`: `82f8ece` - Restore OAuth deploy configuration

Lưu ý: merge trực tiếp `hoangnhat-draft` vào `develop-draft` không tự đem lại toàn bộ thay đổi vì `develop` đã từng merge PR #256 rồi revert ở PR #265. Do đó Git coi lịch sử merge đã có, nhưng nội dung đã bị revert. Vì vậy các phần cần giữ phải được restore/cherry-pick lại theo nhóm file.

## 2. Kết luận nhanh

`develop-draft` hiện đã giữ lại các phần quan trọng từ `hoangnhat-draft`:

- Backend validation cho event, submission, scoring, system log.
- Test Java cho các service liên quan.
- File test note `.md` trong `back-end/src/seal-api/Test`.
- Docker/deploy files.
- OAuth deploy config của Hoàng Nhật.
- `OAuth2LoginFailureHandler` được giữ lại.

Các phần đang giữ theo `develop`:

- Frontend application code.
- Database scripts.
- AI repo analysis.
- Criteria template/scoring template.
- Một số cập nhật admin/security/result service từ `develop`.

Frontend hiện tại không lấy lại UI cũ của `hoangnhat-draft`; chỉ thêm 3 file deploy trong frontend:

- `front-end/src/seal-web/.dockerignore`
- `front-end/src/seal-web/Caddyfile`
- `front-end/src/seal-web/Dockerfile`

Database scripts hiện không còn khác giữa `develop`, `hoangnhat-draft`, và `develop-draft`.

## 3. Quyết định OAuth/deploy

Theo quyết định hiện tại:

- Giữ `back-end/src/seal-api/src/main/java/com/seal/hackathon/security/oauth2/OAuth2LoginFailureHandler.java`
- Giữ OAuth deploy config của `hoangnhat-draft`

Các điểm đã có trong `develop-draft`:

- `SecurityConfig.java`
  - inject `OAuth2LoginFailureHandler`
  - dùng `SessionCreationPolicy.IF_REQUIRED` để OAuth2 giữ state giữa các lần redirect
  - thêm `.failureHandler(oAuth2LoginFailureHandler)`
  - CORS cho `http://localhost:5173` và `${app.frontend.url}`

- `application.properties`
  - giữ `app.frontend.url=${APP_FRONTEND_URL:http://localhost:5173}`
  - giữ `server.servlet.session.cookie.same-site=lax`
  - đồng thời giữ thêm cấu hình GitHub repo analysis từ `develop`:
    - `app.github.token=${GITHUB_TOKEN:}`
    - `app.github.api-base-url=${GITHUB_API_BASE_URL:https://api.github.com}`

Kết luận: OAuth deploy đang theo hướng của `hoangnhat-draft`, nhưng không loại bỏ phần cấu hình GitHub repo analysis mới từ `develop`.

## 4. Phần code từ `hoangnhat-draft` đã có trong `develop-draft`

### 4.1 Event validation

Các file liên quan:

- `back-end/src/seal-api/src/main/java/com/seal/hackathon/controller/HackathonEventController.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/request/CreateEventRequest.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/request/UpdateEventRequest.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/repository/HackathonEventRepository.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/service/HackathonEventService.java`
- `back-end/src/seal-api/src/test/java/com/seal/hackathon/service/HackathonEventServiceTest.java`

Nội dung chính:

- Validate `name`, `season`, `year`.
- Chặn year ngoài khoảng `2026..3000`.
- Validate đầy đủ timeline của event.
- Chặn ngày bắt đầu/kết thúc sai thứ tự.
- Chặn tạo/update event có ngày trong quá khứ.
- Validate season theo khoảng tháng:
  - Spring: tháng 1 đến tháng 4
  - Summer: tháng 5 đến tháng 8
  - Fall: tháng 9 đến tháng 12
- Chặn duplicate event đang active theo `(season, year)`.
- Chặn overlap event đang active.
- Có rule riêng khi reactivate event `CANCELLED`.

### 4.2 Submission validation

Các file liên quan:

- `back-end/src/seal-api/src/main/java/com/seal/hackathon/controller/SubmissionController.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/request/SubmitRequest.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/repository/SubmissionRepository.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/repository/TeamMemberRepository.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/service/SubmissionService.java`
- `back-end/src/seal-api/src/test/java/com/seal/hackathon/service/SubmissionServiceTest.java`

Nội dung chính:

- Validate `repoUrl` bắt buộc, chỉ nhận HTTP/HTTPS, tối đa 500 ký tự, không chứa whitespace.
- Validate optional URL như demo/slide nếu có.
- Chuẩn hóa blank string thành `null`.
- Validate description tối đa 5000 ký tự.
- Chỉ student/team leader được submit hoặc update submission.
- Chặn submit/update khi deadline đã qua.
- Hỗ trợ round `ACTIVE` hoặc `OPEN`.
- Phân quyền đọc submission:
  - coordinator xem được tất cả
  - judge xem submission được assign
  - participant xem submission của team mình

### 4.3 Scoring validation

Các file liên quan:

- `back-end/src/seal-api/src/main/java/com/seal/hackathon/controller/ScoringController.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/repository/SubmissionRepository.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/service/ScoringService.java`
- `back-end/src/seal-api/src/test/java/com/seal/hackathon/service/ScoringServiceTest.java`

Nội dung chính từ `hoangnhat-draft`:

- Validate request scoring null/thiếu `submissionId`/thiếu score list.
- Validate từng score entry phải có `criteriaId` và `value`.
- Judge chỉ được chấm submission đã assign cho mình.
- Criteria phải thuộc đúng round của submission.
- Score không được âm.
- Score không được vượt `maxScore`.
- `getScoresBySubmission` có phân quyền:
  - coordinator xem được
  - judge chỉ xem submission được assign
  - role khác bị chặn

Nội dung giữ thêm từ `develop`:

- Criteria template.
- Apply template vào round.
- Create template from round.
- Các cập nhật scoring/template test từ `develop`.

Kết luận: `ScoringService.java` là file đã merge logic. Nó không giống y hệt `hoangnhat-draft` vì cần giữ thêm criteria template của `develop`, nhưng phần validation/scoring guard của Hoàng Nhật vẫn còn.

### 4.4 System log hardening

Các file liên quan:

- `back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/response/SystemLogResponse.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/entity/SystemLog.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/service/SystemLogService.java`
- `back-end/src/seal-api/src/test/java/com/seal/hackathon/service/SystemLogServiceTest.java`

Nội dung chính:

- Remove `ipAddress`.
- Normalize action: trim, uppercase, whitespace thành underscore.
- Chặn action null/blank/quá 50 ký tự.
- Detail trim, blank thành `null`, quá 5000 ký tự thì truncate.
- Actor null/missing thì không ghi log.

### 4.5 Docker/deploy

Các file đã thêm lại từ `hoangnhat-draft`:

- `.github/workflows/deploy.yml`
- `Caddyfile`
- `docker-compose.yml`
- `back-end/src/seal-api/.dockerignore`
- `back-end/src/seal-api/Dockerfile`
- `front-end/src/seal-web/.dockerignore`
- `front-end/src/seal-web/Caddyfile`
- `front-end/src/seal-web/Dockerfile`

Nội dung chính:

- GitHub Actions deploy khi push lên `develop`.
- SSH vào server, `git pull origin develop`, rồi `sudo docker compose up -d --build`.
- Root domain reverse proxy frontend.
- `/api/*` reverse proxy backend.
- OAuth path từ root domain redirect sang `https://api.sealhackathon.io.vn`.
- `api.sealhackathon.io.vn` reverse proxy backend.
- MySQL dùng init scripts:
  - `01_seal_schema.sql`
  - `02_seal_seed.sql`
  - `03_seal_scripts.sql`
- Frontend build arg mặc định:
  - `VITE_API_URL=https://api.sealhackathon.io.vn`

## 5. Phần đang giữ từ `develop`

Các nhóm code vẫn theo `develop` để tránh mất thay đổi của người khác:

- Frontend app source hiện tại.
- Database scripts hiện tại.
- AI Judge repo analysis:
  - `RepoDigest.java`
  - `GitHubRepoService.java`
  - `AiJudgeAssistantRepoAnalysisTest.java`
  - `GitHubRepoServiceTest.java`
  - config `app.github.*`
- Criteria template/scoring template:
  - `CreateTemplateRequest.java`
  - `ScoringCriteriaTemplateResponse.java`
  - update trong `ScoringController.java`
  - update trong `ScoringService.java`
  - update trong `ScoringCriteriaRepository.java`
- Một số cập nhật từ `develop`:
  - `AdminService.java`
  - `RoundResultService.java`
  - `.gitignore`
  - AI log/report nội bộ của develop

## 6. Khác biệt `develop -> develop-draft`

Đây là nội dung hiện sẽ xuất hiện trong PR từ `develop-draft` vào `develop`:

```text
A .github/workflows/deploy.yml
A Caddyfile
A back-end/src/seal-api/.dockerignore
A back-end/src/seal-api/Dockerfile
A back-end/src/seal-api/Test/HackathonEventServiceTest.md
A back-end/src/seal-api/Test/ScoringServiceTest.md
A back-end/src/seal-api/Test/SubmissionServiceTest.md
A back-end/src/seal-api/Test/SystemLogServiceTest.md
A back-end/src/seal-api/Test/TeamInviteServiceTest.md
A back-end/src/seal-api/Test/TeamServiceTest.md
A back-end/src/seal-api/Test/trackServiceTest.md
M back-end/src/seal-api/src/main/java/com/seal/hackathon/config/SecurityConfig.java
M back-end/src/seal-api/src/main/java/com/seal/hackathon/controller/HackathonEventController.java
M back-end/src/seal-api/src/main/java/com/seal/hackathon/controller/ScoringController.java
M back-end/src/seal-api/src/main/java/com/seal/hackathon/controller/SubmissionController.java
M back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/request/CreateEventRequest.java
M back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/request/SubmitRequest.java
M back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/request/UpdateEventRequest.java
M back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/response/SystemLogResponse.java
M back-end/src/seal-api/src/main/java/com/seal/hackathon/entity/SystemLog.java
M back-end/src/seal-api/src/main/java/com/seal/hackathon/repository/HackathonEventRepository.java
M back-end/src/seal-api/src/main/java/com/seal/hackathon/repository/SubmissionRepository.java
M back-end/src/seal-api/src/main/java/com/seal/hackathon/repository/TeamMemberRepository.java
A back-end/src/seal-api/src/main/java/com/seal/hackathon/security/oauth2/OAuth2LoginFailureHandler.java
M back-end/src/seal-api/src/main/java/com/seal/hackathon/service/HackathonEventService.java
M back-end/src/seal-api/src/main/java/com/seal/hackathon/service/ScoringService.java
M back-end/src/seal-api/src/main/java/com/seal/hackathon/service/SubmissionService.java
M back-end/src/seal-api/src/main/java/com/seal/hackathon/service/SystemLogService.java
M back-end/src/seal-api/src/main/resources/application.properties
M back-end/src/seal-api/src/test/java/com/seal/hackathon/service/HackathonEventServiceTest.java
A back-end/src/seal-api/src/test/java/com/seal/hackathon/service/ScoringServiceTest.java
A back-end/src/seal-api/src/test/java/com/seal/hackathon/service/SubmissionServiceTest.java
A back-end/src/seal-api/src/test/java/com/seal/hackathon/service/SystemLogServiceTest.java
A docker-compose.yml
A front-end/src/seal-web/.dockerignore
A front-end/src/seal-web/Caddyfile
A front-end/src/seal-web/Dockerfile
```

Tổng quan thống kê: 37 files changed, khoảng 3735 insertions và 75 deletions.

## 7. Khác biệt còn lại giữa `hoangnhat-draft` và `develop-draft`

Sau khi đã restore OAuth comment/config, Docker, FE deploy, DB và validation, các khác biệt còn lại là phần `develop-draft` giữ thêm từ `develop`:

```text
A back-end/AI logs/2026-06-25-criteria-templates-scoring-normalization-and-judge-i18n.md
A back-end/AI logs/2026-06-26-IMPORTANT-revert-hoangnhat-draft-pr256.md
A back-end/AI logs/2026-06-26-database-scripts-refactor-to-3-files.md
M back-end/src/seal-api/.gitignore
M back-end/src/seal-api/src/main/java/com/seal/hackathon/controller/AiController.java
M back-end/src/seal-api/src/main/java/com/seal/hackathon/controller/ScoringController.java
A back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/RepoDigest.java
A back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/request/CreateTemplateRequest.java
M back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/response/AiInsightResponse.java
A back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/response/ScoringCriteriaTemplateResponse.java
M back-end/src/seal-api/src/main/java/com/seal/hackathon/repository/ScoringCriteriaRepository.java
M back-end/src/seal-api/src/main/java/com/seal/hackathon/service/AdminService.java
M back-end/src/seal-api/src/main/java/com/seal/hackathon/service/AiJudgeAssistantService.java
A back-end/src/seal-api/src/main/java/com/seal/hackathon/service/GitHubRepoService.java
M back-end/src/seal-api/src/main/java/com/seal/hackathon/service/RoundResultService.java
M back-end/src/seal-api/src/main/java/com/seal/hackathon/service/ScoringService.java
M back-end/src/seal-api/src/main/resources/application.properties
A back-end/src/seal-api/src/test/java/com/seal/hackathon/service/AiJudgeAssistantRepoAnalysisTest.java
A back-end/src/seal-api/src/test/java/com/seal/hackathon/service/GitHubRepoServiceTest.java
M back-end/src/seal-api/src/test/java/com/seal/hackathon/service/ScoringServiceTest.java
D docs/deploy-session-2026-06-21.md
```

Giải thích:

- Những file `A` là feature/log/test mới từ `develop`, không phải conflict chưa xử lý.
- `ScoringService.java` khác vì `develop-draft` đang giữ cả validation của Hoàng Nhật và criteria template từ `develop`.
- `application.properties` khác vì `develop-draft` giữ thêm `app.github.*` từ `develop`.
- `SecurityConfig.java` hiện đã khớp logic OAuth của `hoangnhat-draft`.
- Docker, frontend deploy files, database scripts hiện không còn khác theo nhóm kiểm tra chính.

## 8. File chưa được commit

Các file `??` đang cố ý chưa add:

```text
BUSINESS_RULES.md
back-end/AI logs/AI_LOG_SUBMISSION_SCORING_SYSTEMLOG_VALIDATION_TESTS_2026-06-25.md
back-end/src/seal-api/hs_err_pid4464.log
docs/report/hackathon-event-validation-review.md
docs/report/merge-develop-conflict-resolution-review-2026-06-25.md
docs/report/systemlog-submission-validation-review.md
```

Khuyến nghị:

- Không commit `hs_err_pid4464.log`.
- Chỉ add các report nếu thật sự muốn lưu vào repo.
- Không dùng `git add .` ở bước này để tránh đưa nhầm file `??` vào PR.

## 9. Kết quả test

Đã chạy backend test sau khi restore validation/OAuth:

```text
Tests run: 307, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Command:

```powershell
mvn test
```

Working directory:

```text
back-end/src/seal-api
```

## 10. Kết luận cuối

`develop-draft` hiện là nhánh phù hợp để mở PR vào `develop`.

Nội dung PR nên mô tả là:

- Khôi phục backend validation/submission/scoring/system log hardening từ `hoangnhat-draft`.
- Khôi phục OAuth deploy behavior từ `hoangnhat-draft`.
- Thêm Docker/deploy files để deploy server.
- Giữ frontend/database/code mới từ `develop`.
- Giữ các feature mới của `develop` như AI repo analysis và criteria template.

Trước khi PR, nên commit riêng phần comment/report mới nhất, sau đó push:

```powershell
git add back-end/src/seal-api/src/main/java/com/seal/hackathon/config/SecurityConfig.java docs/report/develop-draft-integration-report-2026-06-26.md
git commit -m "Document develop draft integration"
git push -u origin develop-draft
```
