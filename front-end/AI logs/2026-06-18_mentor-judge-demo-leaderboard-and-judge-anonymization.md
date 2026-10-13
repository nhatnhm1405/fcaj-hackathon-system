# AI Work Log — Mentor/Judge Demo Readiness, Leaderboard Event Selector, Judge 403 Fix & Judge Anonymization

- **Ngày:** 2026-06-18
- **Branch:** `develop`
- **Phạm vi:** Frontend (`front-end/src/seal-web`) + **1 file Backend** (`SecurityConfig.java`). **KHÔNG đụng tới seed / database.**
- **Mục tiêu chung:** Kiểm tra xem 2 role **Mentor** và **Judge** đã demo chính xác chưa; trả lời vì sao Summer 2026 chưa demo được chấm điểm & vì sao leaderboard không hiện top mùa trước; thêm **popup hướng dẫn** cho Mentor/Judge; sửa lỗi judge không thấy submission; **ẩn danh tên team** trong giao diện chấm điểm.
- **Cách làm việc:** Q&A liên tục, chốt quyết định trước khi sửa (theo yêu cầu cố định của user).

---

## 1. Yêu cầu của người dùng (theo từng lượt)

### Lượt 1 — Kiểm tra demo Mentor/Judge + 2 câu hỏi + popup policy
- Kiểm tra role **Mentor** và **Judge** đã có thể demo chính xác chưa.
- Summer 2026 **chưa có submission** → chưa demo được cách chấm điểm.
- Leaderboard **chưa hiện top 1/2/3 của mùa trước** — nguyên nhân?
- Muốn Mentor & Judge có **popup policy** (giống Competition Rules nhưng nội dung là hướng dẫn).

### Lượt 2 — Judge Bình không thấy submission
- Vào **Score Submissions**, judge Lê Văn Bình thấy 3 round (FINALIZED) nhưng list submission trống ("No submissions"). Hỏi vì sao.

### Lượt 3 — Xác nhận cách restart
- Hỏi "chạy lại IntelliJ là được phải không" → xác nhận chỉ cần restart backend.

### Lượt 4 — Ẩn danh tên team khi chấm
- Đã xem được bài Bình chấm ở Spring 2026, nhưng vì chấm điểm mang tính **bảo mật & không thiên vị** nên **không được show tên team** trên UI chấm.

### Lượt 5 — Hỏi có sửa seed không
- Xác nhận từ đầu phiên chỉ sửa FE + API, **không đụng seed**.

### Lượt 6
- Xuất **AI log chi tiết & đầy đủ** phiên này dạng `.md` vào `front-end/AI logs` (file này).

---

## 2. Chẩn đoán (phần giá trị nhất của phiên)

Toàn bộ điều tra dựa trên đọc code FE/BE + đọc `database scripts/seal_seed.sql` + **truy vấn trực tiếp MySQL** + **gọi API thật bằng token của judge Bình**.

### 2.1. Vì sao Summer 2026 chưa demo được chấm điểm
Cả chuỗi dữ liệu chấm điểm của Summer (event 2) còn trống trong seed:
- Team 6–13 của Summer đều `track_id = NULL` (chưa bốc/gán track — để demo bước Setup).
- Chưa có `ScoringCriteria` cho round 4–6 (chỉ event 1 có criteria).
- Chưa có `Submission`, `Score`, `RoundResult` cho Summer.
- Chưa có `JudgeAssignment` cho round Summer; trong `UserEventRole` event 2 **không có ai là JUDGE** (chỉ Coordinator + 2 Mentor).
- ⇒ Judge đăng nhập chỉ thấy assignment của **Spring 2026** (đã `FINALIZED` → form chấm read-only). Không demo được chấm "live".

### 2.2. Vì sao leaderboard không hiện top mùa trước
- `LeaderboardPage` tự chọn event đang `IN_PROGRESS` = Summer 2026 → chưa có `RoundResult` công bố → "Rankings not yet published".
- **Không có bộ chọn event** → không cách nào về Spring 2026 (đã công bố top 1/2/3). Dữ liệu champion có sẵn (`RoundResult` round 3 + bảng `Prize`), chỉ là UI không tới được.

### 2.3. Mentor demo — lỗi nghiệp vụ (đã quyết HOÃN)
- `getMentorAssignments` + `MentorAssignmentRepository.findActiveByMentor` **không scope theo event** — gộp mọi assignment qua mọi mùa.
- Thầy An (mentor cả Spring/Web lẫn Summer/AI): header hiện "Summer 2026" nhưng list team lại là team Web của **Spring** → lẫn 2 mùa.
- Thầy Hùng (mentor chỉ Summer/Web): đúng event nhưng **0 team** (team Summer `track_id = NULL`).
- ⇒ Không mentor nào demo "sạch". **Quyết định: hoãn fix, demo mentor trên Spring.** (Đã ghi memory `mentor-assignment-cross-event`.)

### 2.4. Judge Bình "No submissions" — ROOT CAUSE (lỗi Backend thật)
Reproduce bằng API thật (login `judge.binh@fpt.edu.vn` / `Test@1234`):
- `GET /api/judge/assignments` → **200** ✓ (trả đúng team Phoenix/Dragon/… track Web).
- `GET /api/submissions/round/1` → **403 Forbidden** ✗

Truy vấn DB xác nhận data đúng: round 1 có **5 submission**, Bình được gán round 1/track 1 (`is_active=1`), track 1 có 3 team APPROVED (1/2/5).

Nguyên nhân: `SecurityConfig.java` chặn `/api/submissions/**` ở **tầng URL** chỉ cho `PARTICIPANT` + `EVENT_COORDINATOR`, **thiếu JUDGE**. Rule URL chạy **trước** `@PreAuthorize` của controller (vốn đã cho JUDGE) ⇒ judge luôn 403 ⇒ FE `getAllForRound` catch lỗi → trả `[]` → "No submissions".

---

## 3. Các quyết định đã chốt (qua Q&A)

| Vấn đề | Phương án đã chọn |
|---|---|
| Demo chấm điểm | **Read-only trên Spring 2026** (không seed Summer) |
| Nguồn DB nếu cần đổi data | Re-run file seed SQL (nhưng cuối cùng **không cần** vì chọn read-only) |
| Leaderboard | **Thêm dropdown Event** (mặc định event active, cho chọn mùa trước) |
| Popup policy | **2 popup riêng** Mentor / Judge |
| Mức demo chấm điểm | **Chỉ cần xem** (read-only Spring), không cần gõ điểm live |
| Mentor demo lẫn 2 mùa | **Demo mentor trên Spring**, hoãn fix backend |
| Ngôn ngữ popup | **Tiếng Anh** (đồng bộ Competition Rules) |
| Restart backend (fix 403) | **User tự restart từ IDE** |
| Nhãn ẩn danh team | **Mã theo track** (vd `WEB-1`) |
| Mức bảo mật ẩn danh | **Chỉ ẩn ở UI (FE-only)** — không gỡ khỏi response API |

---

## 4. Tổng quan file đã tạo / sửa

### Backend — sửa (1 file)
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/config/SecurityConfig.java`
  - Tách `/api/teams/**` khỏi `/api/submissions/**`; thêm role `JUDGE` cho `/api/submissions/**`.
  - An toàn vì các endpoint chỉ-participant (POST nộp bài, `GET /my/round/**`) vẫn được `@PreAuthorize("hasRole('PARTICIPANT')")` chặn riêng ở method-level.

### Frontend — tạo mới (2 file)
- `front-end/src/seal-web/src/shared/components/RoleGuideModal.tsx` — popup hướng dẫn Mentor/Judge (tái dùng chrome của `CompetitionRulesModal`), nội dung how-to + quy tắc ứng xử theo role.
- `front-end/src/seal-web/src/features/scoring/anon.ts` — helper sinh mã ẩn danh team theo track (`WEB-1`…).

### Frontend — sửa (5 file)
- `front-end/src/seal-web/src/features/scoring/LeaderboardPage.tsx` — thêm dropdown **Event**.
- `front-end/src/seal-web/src/app/providers/RulesProvider.tsx` — chọn popup theo role + auto-show 1 lần/phiên theo role + label footer động.
- `front-end/src/seal-web/src/shared/components/SealFooter.tsx` — link footer dùng `rulesLinkLabel` động ("Mentor Guide" / "Judge Guide" / "Competition Rules").
- `front-end/src/seal-web/src/features/scoring/JudgeScoringPage.tsx` — ẩn danh tên team (list + panel) bằng `codeOf()`.
- `front-end/src/seal-web/src/features/scoring/JudgeHistoryPage.tsx` — cột "Team" → "Submission", hiện mã ẩn danh.

### KHÔNG đụng tới
- `database scripts/seal_seed.sql` — **giữ nguyên hoàn toàn.**
- `CompetitionRulesModal.tsx`, `CoordScoringPage.tsx`, `LeaderboardPage` (phần tên team công khai) — giữ tên team vì là kết quả công bố / khâu coordinator.

> Lưu ý: `application.properties` và `OnboardingTour.tsx` xuất hiện `M` trong git status nhưng **đã modified từ trước phiên này** (theo snapshot git lúc mở phiên), không phải do phiên này tạo ra.

---

## 5. Chi tiết kỹ thuật

### 5.1. Leaderboard — bộ chọn Event (`LeaderboardPage.tsx`)
- Thêm state `events` + `eventId`; load toàn bộ event qua `eventsApi.getAll()`.
- Mặc định: ưu tiên event của team (participant) → event `IN_PROGRESS`/`OPEN` → event `COMPLETED` gần nhất → event đầu danh sách.
- Tách logic load round ra effect riêng theo `eventId`; mặc định chọn **round Final** để champion hiện ngay.
- UI: grid `repeat(auto-fit, minmax(180px,1fr))`, thêm `<select>` Event với nhãn trạng thái `(Completed)` / `(Live)`.

### 5.2. Popup hướng dẫn Mentor/Judge (`RoleGuideModal.tsx` + `RulesProvider.tsx`)
- `RoleGuideModal` nhận prop `role: "MENTOR" | "JUDGE"`, render nội dung từ `MENTOR_GUIDE` / `JUDGE_GUIDE` (mục I/II/III + danh sách).
- `RulesProvider` tổng quát hóa:
  - `ROLE_GUIDE_LABEL` map role → nhãn footer.
  - Auto-show 1 lần/phiên theo từng role bằng `sessionStorage` key `sealRulesSeen:<ROLE>`.
  - Render: `MENTOR` → mentor guide; `JUDGE` → judge guide; còn lại (Participant/Coordinator/Admin) → `CompetitionRulesModal` (giữ hành vi cũ).
  - Giữ nguyên handoff sang onboarding tour cho Participant no-team.
- Footer dùng `rulesLinkLabel` từ context.

### 5.3. Fix 403 Judge (`SecurityConfig.java`)
```java
// trước
.requestMatchers("/api/teams/**", "/api/submissions/**")
    .hasAnyRole("PARTICIPANT", "EVENT_COORDINATOR")
// sau
.requestMatchers("/api/teams/**")
    .hasAnyRole("PARTICIPANT", "EVENT_COORDINATOR")
.requestMatchers("/api/submissions/**")
    .hasAnyRole("PARTICIPANT", "EVENT_COORDINATOR", "JUDGE")
```
- Cần **restart backend** mới có hiệu lực (đã xác nhận user tự restart từ IDE).

### 5.4. Ẩn danh team phía Judge (`anon.ts`, `JudgeScoringPage.tsx`, `JudgeHistoryPage.tsx`)
- `trackAbbrev(trackName)` = từ đầu tiên viết hoa ("Web Application" → `WEB`).
- `buildTeamCodeMap(roster)`: dedupe team, nhóm theo track abbrev, sort `teamId` tăng dần, đánh `WEB-1, WEB-2…`. Số ổn định theo `teamId` ⇒ cùng team luôn cùng mã, **nhất quán giữa Scoring & History, không đổi theo round**.
- Nguồn roster = `assignment.teams` (có `teamId` + `trackName`). Lưu ý: bản thân `Submission` DTO **không có** `trackName`, nên phải lấy từ assignment.
- `JudgeScoringPage`: state `teamCodes`; `codeOf(sub) = teamCodes.get(sub.teamId) ?? '#'+submissionId`; thay 2 chỗ `teamName`.
- `JudgeHistoryPage`: `teamIdBySub` thay `teamBySub`; field `teamName` → `code`; header "Team" → "Submission".

---

## 6. Kiểm thử & xác minh

- **DB query** (MySQL 8.0): xác nhận round/submission/judge-assignment/team đúng như seed.
- **API thật** với token Bình: chứng minh `/api/judge/assignments` = 200 còn `/api/submissions/round/1` = 403 (root cause).
- **TypeScript:** `tsc --noEmit` (với `--ignoreDeprecations 6.0`) — **sạch, không lỗi**.
- **Unit test:** `vitest run` — **12/12 pass** (2 lần, trước và sau thay đổi ẩn danh).
- FE thay đổi → Vite tự hot-reload, **không cần restart backend** (trừ fix 403).

---

## 7. Việc còn mở / lưu ý cho lần sau

1. **Mentor cross-event** (mục 2.3) — chưa fix. Cách đúng: scope `getMentorAssignments` về 1 event (mới nhất chưa COMPLETED/DRAFT). Đã ghi memory `mentor-assignment-cross-event`.
2. **Ẩn danh FE-only** — tên team + tên thành viên vẫn nằm trong response `/api/submissions/round/...` và `/api/judge/assignments`. Muốn bảo mật thật: gỡ field ở DTO/service phía backend (sẽ cần restart). Hiện đủ cho demo.
3. **Demo leaderboard:** mặc định vào Summer (trống) → khi demo nhớ chọn "SEAL Spring 2026 (Completed)" để hiện top 1/2/3.
4. **Tài khoản demo gợi ý:** Judge = `judge.binh@fpt.edu.vn`; mentor demo dùng tài khoản mentor Spring; mật khẩu seed chung `Test@1234`.

---

_— Hết log —_
