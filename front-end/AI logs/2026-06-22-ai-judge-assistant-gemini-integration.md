# AI Session Log — AI Judge Assistant (Google Gemini) Integration

**Date:** 2026-06-22
**Project:** SEAL – Software Engineering Hackathon Management System
**Branch:** `TrangNHK-lightmode-and-announcements`
**Scope:** Tích hợp **tính năng AI đầu tiên** của hệ thống — *AI Judge Assistant*: hỗ trợ giám khảo đọc nhanh một bài nộp (tóm tắt + điểm mạnh/yếu + gợi ý điểm theo tiêu chí) bằng **Google Gemini**. Bao gồm cả backend, frontend, seed dữ liệu demo, và xử lý 4 lỗi phát sinh khi chạy thử.

> Lưu ý phạm vi: phiên này **chỉ làm AI Judge Assistant**. Chức năng *"phát đề"* (coordinator import & gửi đề cho leader) được **chủ động hoãn** (xem Phần 7).

---

## PHẦN 0 — Lấy context & chốt yêu cầu qua Q&A

### 0.1. Tài liệu đã đọc để hiểu hệ thống
- `back-end/Postman/Postman_Full_Collection.json` — toàn bộ API: Auth, Admin, Event/Track/Round, Team, Submission, **Scoring (criteria + scores)**, Results, Notifications.
- `docs/documents/ProjectRequirements.md` — đề bài SWP391; đặc biệt **mục 12 (RBL)** về nghiên cứu *độ nhất quán chấm điểm giữa giám khảo* → đây là điểm "ăn" nhất với AI.
- Backend: `SubmissionController/Service`, `ScoringCriteria` entity + repo, `ApiResponse`, `SecurityConfig`, `GlobalExceptionHandler`, `pom.xml`, `application.properties`.
- Frontend: `shared/apiClient.ts`, `features/scoring/JudgeScoringPage.tsx`, `PixelComponents.tsx`.
- DB: `database scripts/seal_seed.sql`, `seal_schema.sql`.

### 0.2. Quyết định chốt với người dùng

| Câu hỏi | Lựa chọn |
|---|---|
| Tính năng AI nào trước? | **AI Judge Assistant** |
| Nhà cung cấp AI | **Google Gemini** |
| Demo neo trên event nào | **Backfill Spring 2026** (đổi từ ý định "Summer" ban đầu, lý do ở 0.3) |
| Tính năng "phát đề" | **Để sau, làm AI trước** |

### 0.3. Phát hiện quan trọng định hình thiết kế

1. **Ràng buộc ẩn danh:** trang chấm điểm của Judge cố tình **ẩn danh đội** (mã `WEB-1`…, ẩn tên đội/thành viên — xem `anon.ts`/`buildTeamCodeMap`). ⇒ AI Assistant **bắt buộc phải giữ ẩn danh**: prompt chỉ chứa *mô tả + link + tiêu chí*, **không** tên đội/thành viên.

2. **Vướng dữ liệu Summer (lý do đổi sang Spring):** các team Summer (6–13) đang **cố tình để `track_id = NULL`** (seed dành cho demo "draw-tracks"), round Preliminary của Summer **chưa có criteria, chưa phân công judge**. Trang chấm chỉ hiện bài của team mà judge được phân công theo track ⇒ seed bài cho Summer mà không wiring thì **judge không thấy bài để bấm AI**. Trong khi **Spring (event 1) đã wiring đầy đủ** (track + criteria + judge binh/an), chỉ thiếu `description`. ⇒ Chọn **backfill mô tả cho bài Spring** = ít rủi ro nhất, không phá demo draw-tracks.

3. **Submission Spring thiếu mô tả:** seed gốc chèn submission chỉ có URL, **không có `description`** và repo URL là giả ⇒ AI không có nội dung thật để đọc. Phải backfill `description`.

---

## PHẦN 1 — Thiết kế tính năng

**Nguyên tắc cốt lõi (phải giữ):**
- **Ẩn danh (anonymity-safe):** không lộ danh tính đội trong prompt.
- **Chỉ tham khảo (advisory only):** AI **không** tự chấm; chỉ gợi ý *khoảng điểm* (vd `7-8 / 10`). Giám khảo tự nhập điểm.
- **Tách biệt:** module AI độc lập, **không đụng** vào logic chấm điểm/xếp hạng hiện có.
- **Suy biến mềm (graceful):** thiếu API key → trả lỗi rõ ràng, **không** crash.

**Luồng:** Judge mở submission → bấm **"✨ AI ASSIST"** → BE gom (mô tả + link + tiêu chí của round) → gọi Gemini → trả về JSON {summary, strengths, concerns, criteriaInsights[], disclaimer, model} → FE hiển thị panel.

---

## PHẦN 2 — BACK-END

**Stack:** Spring Boot **4.0.6**, Java 21, starter `webmvc`. Gọi Gemini qua **`RestClient`** (có sẵn trong spring-web) — không thêm dependency.

### 2.1. File mới

| File | Vai trò |
|---|---|
| `dto/response/AiInsightResponse.java` | DTO kết quả: `summary`, `strengths[]`, `concerns[]`, `criteriaInsights[]` (`{criteriaName, assessment, suggestedScoreRange}`), `disclaimer`, `model` |
| `service/AiJudgeAssistantService.java` | Dựng prompt (ẩn danh), gọi Gemini (có retry), parse JSON, gắn disclaimer |
| `controller/AiController.java` | `POST /api/ai/submissions/{submissionId}/insights` — `@PreAuthorize hasAnyRole('JUDGE','EVENT_COORDINATOR')` |

### 2.2. Endpoint & bảo mật
- `SecurityConfig`: thêm rule `.requestMatchers("/api/ai/**").hasAnyRole("JUDGE", "EVENT_COORDINATOR")`.
- Lưu ý token role: code dùng `JUDGE` / `EVENT_COORDINATOR` (đúng chuẩn `@PreAuthorize` của hệ thống, không phải `COORDINATOR`/`ADMIN` của FE).

### 2.3. Cấu hình (`application.properties` + `.env`)
```properties
app.ai.gemini.api-key=${GEMINI_API_KEY:}
app.ai.gemini.model=${GEMINI_MODEL:gemini-2.5-flash}
app.ai.gemini.base-url=${GEMINI_BASE_URL:https://generativelanguage.googleapis.com/v1beta}
```
- Thêm dòng `GEMINI_API_KEY=` vào `.env` (gitignored). Trống = tính năng tắt êm.

### 2.4. Prompt (rút gọn)
- Tiếng Việt, nêu rõ: **không bịa danh tính**, **không tự quyết điểm cuối**, thiếu dữ liệu thì nói rõ.
- Đưa: tên vòng + mô tả + repo/demo/slide + danh sách tiêu chí (tên/điểm tối đa/trọng số).
- Bắt Gemini trả **đúng 1 JSON** đúng schema; mỗi tiêu chí đúng 1 mục trong `criteriaInsights`, `suggestedScoreRange` nằm trong [0, maxScore].
- Gọi Gemini với `generationConfig.responseMimeType = "application/json"` để nhận JSON sạch.

---

## PHẦN 3 — FRONT-END

| File | Thay đổi |
|---|---|
| `shared/apiClient.ts` | Thêm interface `AiInsight` / `AiCriteriaInsight` + `aiApi.getSubmissionInsights(submissionId)` (POST) |
| `features/scoring/JudgeScoringPage.tsx` | Thêm state `aiInsight/aiLoading/aiError`; nút **"✨ AI ASSIST"** ở card header; panel hiển thị summary + STRENGTHS + POINTS TO PROBE + PER-CRITERIA NOTES (kèm badge khoảng điểm) + disclaimer; reset khi đổi submission |

- Dùng đúng design system có sẵn: `PixelCard glow glowColor="cyan"`, `PixelButton variant="cyber"`, `PixelBadge color="cyan"/"blue"`.
- `npx tsc --noEmit` ⇒ không lỗi ở file đã sửa (chỉ còn cảnh báo `baseUrl` deprecated có sẵn trong `tsconfig.json`).

---

## PHẦN 4 — Seed dữ liệu demo

**File mới:** `back-end/database scripts/seal_seed_ai_demo.sql`
- **Backfill `description`** (tiếng Việt có dấu, DB là `utf8mb4`) cho **12 submission Spring (id 1–12)** — mỗi bài là một pitch thật (vấn đề/giải pháp/stack/tình trạng), vòng sau (semi/final) phản ánh tiến độ.
- **Idempotent** (`UPDATE ... WHERE submission_id = N`), chạy lại nhiều lần OK.
- **Không đụng** event Summer ⇒ demo draw-tracks còn nguyên.
- Chạy sau seed gốc:
  ```bash
  mysqlsh --mysql -u root -p --schema=seal_hackathon --sql -f "back-end/database scripts/seal_seed_ai_demo.sql"
  ```

---

## PHẦN 5 — Các lỗi phát sinh khi chạy thử & cách sửa

### 5.1. `APPLICATION FAILED TO START` — thiếu bean `ObjectMapper`

**Triệu chứng:** IntelliJ không run được; log:
```
Parameter 2 of constructor in com.seal.hackathon.service.AiJudgeAssistantService
required a bean of type 'com.fasterxml.jackson.databind.ObjectMapper' that could not be found.
```

**Nguyên nhân gốc:** dự án ở **Spring Boot 4 (dùng Jackson 3 = `tools.jackson.databind.ObjectMapper`)**. Bean `ObjectMapper` mà Spring tự cấu hình là **Jackson 3**, không phải Jackson 2 (`com.fasterxml.jackson...`). `AiJudgeAssistantService` lại khai báo `private final ObjectMapper objectMapper;` kiểu **Jackson 2** (field `final` chưa khởi tạo) ⇒ Lombok `@RequiredArgsConstructor` đòi Spring inject một bean Jackson-2 không tồn tại ⇒ chết lúc dựng context. (Compile vẫn OK vì lớp Jackson 2 có trên classpath qua `jjwt-jackson`.) — **Cùng loại lỗi & cùng cách sửa** đã ghi nhận trước đó cho `AuditLogService`.

**Cách sửa (đúng pattern có sẵn trong dự án — `JwtAuthenticationEntryPoint`):**
```diff
- private final ObjectMapper objectMapper;
+ private final ObjectMapper objectMapper = new ObjectMapper();
```
Field `final` khởi tạo inline ⇒ Lombok **loại khỏi constructor** ⇒ Spring không tìm bean nữa. Mapper mặc định đủ để `readTree`/`readValue` JSON từ Gemini.

**Bài học rút ra:** `mvn compile` PASS nhưng lỗi chỉ lộ lúc **chạy** (runtime bean wiring). ⇒ Từ nay khi thêm code đụng Spring beans, **chạy thử `spring-boot:run`** để xác nhận khởi động trước khi báo "xong".

**Kiểm chứng:** `./mvnw spring-boot:run` → `Started HackathonApplication in 6.6s`, Tomcat lên port 8080. ✅

### 5.2. Gemini **429** — `limit: 0` cho `gemini-2.0-flash`

**Triệu chứng:** bấm AI Assist → `429 RESOURCE_EXHAUSTED`, `generate_content_free_tier_requests, limit: 0, model: gemini-2.0-flash`.

**Chẩn đoán:** key **hợp lệ** (sai key sẽ là 401; đây là 429 = đã xác thực). Model `gemini-2.0-flash` **không có hạn mức free** trên project của key này. Test trực tiếp key với nhiều model:

| Model | HTTP |
|---|---|
| `gemini-2.5-flash` | **200** ✅ |
| `gemini-flash-latest` | **200** ✅ |
| `gemini-2.0-flash-lite` | 429 |
| `gemini-1.5-flash` | 404 (không khả dụng) |

**Cách sửa:** đổi **model mặc định** `gemini-2.0-flash` → **`gemini-2.5-flash`** (trong `application.properties`).

### 5.3. Thông báo lỗi đổ nguyên cục JSON thô ra UI

**Triệu chứng:** popup "AI ASSIST FAILED" hiện toàn bộ JSON lỗi của Gemini (xấu, khó đọc).

**Cách sửa:** trong `callGemini`, bắt riêng `RestClientResponseException` và thêm `describeGeminiError(...)` — parse `error.message`, trả câu ngắn theo status:
- `429` → "Gemini đã hết hạn mức… đổi GEMINI_MODEL hoặc bật billing."
- `404` → "Model không khả dụng với key này… đổi GEMINI_MODEL."
- `400/401/403` (chứa "api key") → "API key không hợp lệ."
- `503` → "Model đang quá tải… thử lại sau ít giây."

### 5.4. Gemini **503** — model overloaded (tạm thời)

**Triệu chứng:** `Gemini lỗi (503): This model is currently experiencing high demand…` (thông báo giờ đã gọn nhờ 5.3).

**Chẩn đoán:** 503 là **transient** (probe ngay sau đó: `gemini-2.5-flash` trả `200 200 200`). Lỗi chớp nhoáng phía Gemini.

**Cách sửa:** thêm **retry + backoff** trong `callGemini` — thử tối đa `MAX_ATTEMPTS = 3` lần khi gặp **503/429 tạm thời**, giãn cách `attempt * 1500ms`; hết lượt mới trả lỗi gọn. Người dùng bấm 1 lần, BE tự xoay xở.

---

## Tổng hợp file thay đổi

| File | Thay đổi |
|---|---|
| `back-end/.../dto/response/AiInsightResponse.java` | **MỚI** — DTO kết quả AI |
| `back-end/.../service/AiJudgeAssistantService.java` | **MỚI** — gọi Gemini (ẩn danh, advisory, retry 503/429, thông báo lỗi gọn) |
| `back-end/.../controller/AiController.java` | **MỚI** — `POST /api/ai/submissions/{id}/insights` |
| `back-end/.../config/SecurityConfig.java` | Thêm rule `/api/ai/**` → JUDGE/EVENT_COORDINATOR |
| `back-end/.../resources/application.properties` | Thêm block `app.ai.gemini.*` (model mặc định `gemini-2.5-flash`) |
| `back-end/src/seal-api/.env` | Thêm dòng `GEMINI_API_KEY=` (secret, gitignored) |
| `back-end/database scripts/seal_seed_ai_demo.sql` | **MỚI** — backfill mô tả 12 bài Spring (idempotent) |
| `front-end/.../shared/apiClient.ts` | Thêm `AiInsight` + `aiApi.getSubmissionInsights` |
| `front-end/.../features/scoring/JudgeScoringPage.tsx` | Nút "✨ AI ASSIST" + panel kết quả AI |

---

## Cách chạy / kiểm thử

1. **API key:** lấy key Gemini tại https://aistudio.google.com/app/apikey → dán vào `back-end/src/seal-api/.env`: `GEMINI_API_KEY=...` (key hiện tại đã chạy với `gemini-2.5-flash`).
2. **Seed demo:** chạy `seal_seed_ai_demo.sql` (lệnh ở Phần 4).
3. **Backend:** chạy từ IntelliJ hoặc `./mvnw spring-boot:run` (cần MySQL `seal_hackathon`). Restart sau khi đổi `.env`.
4. **Frontend:** `cd front-end/src/seal-web && npm run dev`.
5. **Demo:** đăng nhập Judge (`judge.binh@fpt.edu.vn` / `Test@1234`) → **Score Submissions** → chọn round Spring → chọn 1 bài → **✨ AI ASSIST**.
   - Chưa có key → báo "AI Judge Assistant chưa được cấu hình…" (không crash).
   - Hiếm khi 503 → BE tự retry; nếu vẫn lỗi, bấm lại hoặc đặt `GEMINI_MODEL=gemini-flash-latest` trong `.env`.

---

## PHẦN 7 — Hoãn lại / việc còn mở

- **Chức năng "phát đề"** (coordinator import đề & gửi cho leader): **CHƯA làm**, chủ động hoãn. Đây là feature lớn, **ngoài requirement gốc** (đề gốc dùng mô hình "track mở", không có "đề bài" giao sẵn) — cần thêm entity `ProblemStatement`, import, gán đề, notification → đụng data model & nhiều luồng. Khi triển khai sẽ lập kế hoạch + cảnh báo thay đổi data model trước.
- **Mở rộng AI khả dĩ về sau:** AI cho coordinator (sinh tiêu chí/mô tả event), chatbot thí sinh, và đặc biệt **phân tích độ nhất quán chấm điểm (RBL)** — đúng tâm điểm nghiên cứu của đề tài.
