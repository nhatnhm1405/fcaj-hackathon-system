# AI Work Log — AI Judge Assistant đọc & phân tích Git Repo của participant

- **Ngày:** 2026-06-25
- **Branch:** `develop`
- **Phạm vi:** Backend (`back-end/src/seal-api`) + Frontend (`front-end/src/seal-web`) + DB scripts (`back-end/database scripts`). Có chỉnh `.env` (gitignored, không commit).
- **Mục tiêu chung:** Nâng cấp **AI Judge Assistant**. Trước đây AI chỉ "chấm" dựa trên **mô tả + link dạng text** (repoUrl chỉ là một chuỗi, Gemini KHÔNG đọc nội dung repo). Yêu cầu: cho AI **thực sự đọc & phân tích git repo** của participant (bài nộp gồm git repo + video + presentation).
- **Cách làm việc:** Q&A liên tục, chốt từng quyết định trước khi code (theo yêu cầu cố định của user).

---

## 1. Yêu cầu của người dùng (theo từng lượt)

### Lượt 1 — Yêu cầu chính
- Lấy context dự án qua `back-end/Postman/Postman_Full_Collection.json` + `docs/.../ProjectRequirements.md`.
- AI hiện chỉ chấm dựa trên "script có sẵn" (mô tả). Muốn AI **hỗ trợ chấm dựa trên git repo** của participant.

### Lượt 2–4 — Cấu hình & vận hành
- Hỏi `.env` cần bổ sung gì.
- Hỏi cách lấy GitHub token.
- Hỏi không có token thì AI Assist còn chạy không.

### Lượt 5–8 — Demo & sự cố quota Gemini
- Login judge.binh mở repo submission nào cũng 404.
- Liên tiếp các lỗi Gemini: 503 (quá tải) → 429 (hết quota) qua nhiều model.
- Nghi ngờ repo demo "quá nặng" gây lỗi.
- Hỏi có cần đổi Gemini API key không; model mới có giới hạn quota không (kèm screenshot dashboard).

### Lượt 9 — Logic chấm
- Hỏi AI có dựa theo topic track + criteria để kết luận không; repo lệch topic có bị chấm "không đúng topic"?

### Lượt 10 — Chuẩn bị commit
- Yêu cầu thêm **script MySQL** để member khác chạy mỗi lần demo.

### Lượt 11
- Xuất **AI log chi tiết & đầy đủ** phiên này dạng `.md` vào `front-end/AI logs` (file này).

---

## 2. Hiện trạng trước khi sửa (điểm xuất phát)

`AiJudgeAssistantService.buildPrompt()` chỉ đưa vào prompt Gemini phần **text**:
`description + repoUrl + demoUrl + slideUrl` + tiêu chí của vòng. `repoUrl` chỉ là chuỗi URL — Gemini **không fetch, không đọc** nội dung repo. Output `AiInsightResponse` gồm `summary / strengths / concerns / criteriaInsights / disclaimer / model`.

Thiết kế cũ có ràng buộc **ẩn danh** (prompt cấm lộ tên đội/thành viên/trường) để hợp với luồng chấm điểm ẩn danh.

---

## 3. Các quyết định đã chốt (qua Q&A)

| # | Vấn đề | Quyết định |
|---|--------|-----------|
| 1 | Cách AI đọc repo | **GitHub REST API** (metadata + file chọn lọc), KHÔNG git clone, KHÔNG để Gemini tự đọc URL |
| 2 | Ẩn danh | **Lọc/ẩn danh trước khi gửi** Gemini (giữ ràng buộc ẩn danh) |
| 3 | Git host | **Chỉ GitHub** |
| 4 | Trọng tâm phân tích | Chất lượng/cấu trúc code · khớp tiêu chí · dấu hiệu nghi vấn (bỏ activity/collaboration vì gắn danh tính) |
| 5 | GitHub rate-limit/private | Thêm **`GITHUB_TOKEN`** trong `.env` (trống vẫn chạy repo public, ~60 req/h; có token → ~5000 req/h + private) |
| 6 | Phạm vi | **BE + FE đầy đủ** |
| 7 | `repo` block | Build **deterministic trong Java** từ dữ liệu GitHub thật (KHÔNG để Gemini bịa techStack/signals/redFlags) |

**Lý do (7):** giám khảo cần tin được tech stack/signals/red flags như "sự thật", nên các trường này tính bằng code từ dữ liệu GitHub. Còn `summary/strengths/concerns/criteriaInsights` thì Gemini viết nhưng **được nuôi bằng nội dung repo thật** → bám sát code.

---

## 4. Thay đổi Backend

### 4.1. File mới
- **`service/GitHubRepoService.java`** — gọi GitHub REST API, dựng `RepoDigest` đã ẩn danh.
  - Parse URL GitHub (`owner/repo`) cho mọi dạng: https, `.git`, `/tree/<branch>/...`, trailing slash, SSH `git@github.com:owner/repo`. Non-GitHub → trả `analyzed=false` + note.
  - Fetch (đều best-effort, không bao giờ throw): `/repos/{o}/{r}` (description, default_branch, fork, archived, size, license, topics), `/languages`, `/git/trees/{branch}?recursive=1` (phát hiện test / CI / docs / manifest, đếm file, top-level entries), `/readme` (decode base64), commits (chỉ **số đếm + ngày**, đọc page count qua header `Link rel="last"`).
  - **Ẩn danh:** KHÔNG đọc tên/email author commit; `scrub()` xóa email + `@handle` khỏi README; cắt README còn 4000 ký tự, tree 4000 entry, top-level 40, languages 8.
  - `describeError()`: phân biệt 403/429 rate-limit (đọc header `X-RateLimit-Remaining`), 404 (private/không tồn tại), 401/403.
- **`dto/RepoDigest.java`** — model nội bộ (đã ẩn danh) đưa vào prompt.

### 4.2. File sửa
- **`dto/response/AiInsightResponse.java`** — thêm nested `RepoAnalysis repo` (`analyzed`, `note`, `techStack`, `signals`, `redFlags`).
- **`service/AiJudgeAssistantService.java`**:
  - Inject `GitHubRepoService`; trong `analyzeSubmission` gọi `analyze(repoUrl)` → graceful (lỗi → analyzed=false + note, vẫn chạy text-only).
  - `buildPrompt(..., RepoDigest)` + `appendRepoSection()` bơm digest repo thật vào prompt; nhắc Gemini "nếu code không khớp mô tả → nêu ở concerns" và tuyệt đối không nhắc tên người.
  - `buildRepoAnalysis(RepoDigest)` (static, package-private) map digest → `RepoAnalysis` **deterministic**. redFlags: fork, archived, repo gần trống (≤2 file / size 0), thiếu README, dồn ≤2 commit, tất cả commit cùng 1 ngày.
  - **Sửa retry:** trước đây retry cả 503 lẫn 429 (×3) → 429 bị retry **đốt 3× quota** mỗi lần bấm. Đổi: **chỉ retry 503**, 429 fail-fast.
- **`resources/application.properties`** — thêm `app.github.token=${GITHUB_TOKEN:}` + `app.github.api-base-url=${GITHUB_API_BASE_URL:https://api.github.com}`.

---

## 5. Thay đổi Frontend

- **`shared/apiClient.ts`** — thêm interface `AiRepoAnalysis` + field `repo?: AiRepoAnalysis | null` trong `AiInsight`.
- **`features/scoring/JudgeScoringPage.tsx`** — thêm khối **"REPOSITORY ANALYSIS"** trong panel AI (sau summary): tech-stack badge (màu purple), danh sách signals, box đỏ red-flags, và hiển thị `note` khi `analyzed=false`.

---

## 6. Kiểm thử

- **`test/.../GitHubRepoServiceTest.java`** (14 test): parse URL nhiều dạng + non-GitHub, `totalFromLinkHeader`, `scrub` (xóa email/@handle, cắt dài, null-safe).
- **`test/.../AiJudgeAssistantRepoAnalysisTest.java`** (8 test): map digest → RepoAnalysis (analyzed=false passthrough, null, repo lành mạnh không red flag, fork, repo trống, dồn 1 commit, cùng 1 ngày, thiếu README).
- Kết quả: **22/22 PASS**. Backend `mvnw compile` + Frontend `vite build` đều **SUCCESS**.

---

## 7. Sự cố Gemini 503/429 — chẩn đoán (phần giá trị)

Triệu chứng: bấm 1 lần (không dồn dập) vẫn lỗi, xoay qua nhiều model: `2.5-flash`→503, `2.0-flash`→429, `2.5-flash-lite`→503, `flash-latest`→503. Nghi repo nặng → **đã loại trừ** (repo giả prompt nhỏ vẫn 503; 503/429 không phải lỗi kích thước, nếu nặng sẽ là 400).

**Test thẳng Gemini API bằng curl + key của user:**
- `GET /v1beta/models?key=...` → **200** (key hợp lệ, dạng `AQ.Ab8...` lạ nhưng vẫn auth được).
- `generateContent` từng model:
  - `gemini-2.0-flash` / `2.0-flash-lite` → **429 `limit: 0`** = free tier **không cấp quota** cho model này (retry vô ích).
  - `gemini-2.5-*`, `flash-latest` → **503** (nghẽn thật).
  - **`gemini-3.1-flash-lite` → 200** ✓ (và `gemini-3-flash-preview`, `gemini-flash-lite-latest` cũng 200).

**Kết luận:** không phải lỗi code/repo/usage. Mấy model 2.x bị khóa quota (limit 0) hoặc quá tải; model 3.x lite còn rảnh.

**Khắc phục:** đặt `GEMINI_MODEL=gemini-3.1-flash-lite` trong `.env`.

**Đối chiếu dashboard quota** (screenshot user gửi, Free level, peak 28 ngày) — model đang dùng `gemini-3.1-flash-lite`: RPM 2/**15**, TPM 2.13K/**250K**, RPD 5/**500**. → còn rất nhiều quota (RPD 500/ngày, gấp ~25× các model 20/ngày). `0/0` của "Gemini 2 Flash" trùng khớp phát hiện `limit:0`.

→ **Không cần đổi key, không cần billing** cho demo.

---

## 8. Logic chấm điểm — trả lời "có bị chấm sai topic?"

- AI so sánh repo với **`description` của submission** + **scoring criteria** của vòng. **KHÔNG** có trường "track/topic" riêng truyền vào prompt — "topic" chính là description.
- Nếu repo không khớp mô tả → AI nêu ở **`concerns` (POINTS TO PROBE)**, **không tự cho điểm 0**. Advisory only — giám khảo quyết định.

---

## 9. Dữ liệu demo & script cho member

**Quyết định demo:** làm **2 bài đối lập, cùng track Web (round 1)** để 1 judge login thấy cả hai.

| Bài | Mô tả | Repo trỏ tới | AI |
|-----|-------|--------------|-----|
| sub 1 (Phoenix) | quản lý phòng khám (đã chỉnh khớp) | `spring-projects/spring-petclinic` | ✅ Khớp, khen |
| sub 2 (Dragon) | nền tảng học trực tuyến (Node.js) | `pallets/flask` (Python) | ⚠️ Lệch domain + tech |
| sub 4 (Eagle, bonus) | AutoGrade (Python FastAPI) | `fastapi/full-stack-fastapi-template` | (AI track) |

**File:**
- `seal_seed_ai_demo.sql` — trả về đúng vai trò "backfill mô tả 12 bài" (sub 1 = ký túc xá gốc); gỡ phần repo-URL; thêm note trỏ sang file overlay.
- **`seal_demo_ai_repo.sql` (MỚI)** — overlay độc lập, **idempotent**, member chạy **mỗi lần trước demo**. Tự set mô tả + repo_url cho sub 1/2/4, có header tài liệu đầy đủ (prerequisites, config `.env`, kịch bản demo từng bước, verify SELECT).

**Thứ tự seed:** `seal_schema.sql` → `seal_seed.sql` → `seal_seed_ai_demo.sql` → `seal_demo_ai_repo.sql`.

---

## 10. Cấu hình `.env` (gitignored — không commit)

```
GEMINI_API_KEY=<key của bạn>
GEMINI_MODEL=gemini-3.1-flash-lite        # tránh 503/429 của model 2.x
GITHUB_TOKEN=<PAT, optional>              # trống vẫn chạy repo public (~60 req/h)
```
Token GitHub là **Personal Access Token** (Settings → Developer settings → Tokens classic → scope `public_repo`/`repo`), **khác** với `GITHUB_CLIENT_ID/SECRET` (OAuth login). Đổi `.env` xong phải **restart backend**.

---

## 11. Tài khoản demo

- Tất cả seed user dùng chung mật khẩu **`Test@1234`**.
- **judge.binh@fpt.edu.vn** — judge track Web (cả 3 vòng) → thấy sub 1/2/5. Dùng cho demo chính.
- guest.judge@gmail.com / Thầy An (user 3) — track AI (sub 4).
- coordinator@fpt.edu.vn — EVENT_COORDINATOR (cũng gọi được endpoint AI).

---

## 12. Trạng thái cuối phiên

- ✅ BE compile + 22 unit test pass; FE vite build pass.
- ✅ Feature hoàn chỉnh BE + FE; graceful khi repo private/404/non-GitHub/rate-limit.
- ✅ Script demo `seal_demo_ai_repo.sql` sẵn sàng commit.
- ⏸️ **Chưa git commit** (chờ user tự commit). `.env` gitignored, secrets không lên GitHub.
- 📝 Memory `ai-judge-assistant` đã cập nhật (repo-analysis upgrade, working model, demo script).

### Đề xuất tiếp theo (chưa làm)
- Thêm hướng dẫn seed + config AI vào README chính.
- (Tùy chọn) trỏ thêm submission khác sang repo thật nếu cần nhiều ví dụ.
