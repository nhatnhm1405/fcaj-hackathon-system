# AI Session Log (Part 2) — Prize FE, Participant/Mentor History, Printable Certificates

**Date:** 2026-06-26 (tiếp nối cùng ngày)
**Project:** SEAL – Software Engineering Hackathon Management System
**Branch:** `develop`
**Stack chạm tới:** BE (Spring Boot), FE (React/Vite/TS), DB query (MySQL local đang chạy).

> **Phần 1 của ngày** (Prize **backend** + leaderboard per-track + Top N enforcement/tie-break + D1/D2) ở log riêng:
> `2026-06-26-prize-feature-leaderboard-redesign-topn-enforcement.md`. Log này là **phần 2**: làm FE cho trao giải, rồi History (participant + mentor) và Certificate.

**Cách làm xuyên suốt:** Q&A liên tục, đề xuất + chốt qua câu hỏi trước khi code; verify mỗi bước bằng `mvnw -o compile` (BE) và `npx vite build` (FE).

---

## PHẦN 1 — Frontend cho Trao giải (Prize)

User: *"tiếp tục sửa luôn FE của phần trao giải, mà flow hoạt động như nào?"*

**Flow chốt:** Final finalize+publish → Coordinator vào "Awards" → auto-generate top N từ BXH final (hoặc thêm slot tay) → chỉnh tên giải + chọn đội → **ANNOUNCE** (BE gửi notification cho từng đội thắng + audit `AWARD_PRIZE`). Hai trạng thái: draft (`awarded_at` NULL) → announced (public-ish).

**Đã làm:**
- `shared/apiClient.ts`: thêm type `Prize` + `prizesApi` (getAll/create/update/remove/autoGenerate/announce).
- `features/scoring/CoordPrizesPage.tsx` (mới): route `/coordinator/prizes`, sidebar **"Awards"**. Chọn event → auto-generate (nhập topN, yêu cầu final `FINALIZED`) / +Add slot → mỗi giải sửa tên + dropdown chọn đội (teamsApi.getByEvent) → **📣 ANNOUNCE** (confirm).

**Bước hụt + sửa:** ban đầu mình thêm card "Awards" lên `LeaderboardPage` → **user bác bỏ** ("không publish lên leaderboard, mà gửi thông báo riêng + export CSV") → **đã gỡ** khỏi LeaderboardPage. Kênh công bố = **notification** (BE announce) + **CSV**.

**Export CSV (client-side, BOM UTF-8 để Excel không lỗi font):**
- **Winners CSV**: `rank, prize, team, track, final_score, awarded_at, event` + cột **`certificate_statement`** tiếng Anh.
- **Participants CSV** (mail-merge certificate): `full_name, email, team, track, role, event` + `certificate_statement`. Lấy từ `teamsApi.getByEvent` (response có members; **field là `memberRole` không phải `role`** → thêm `memberRole?` vào FE `TeamMember`).
- Yêu cầu sau của user: **certificate tiếng Anh** (đã thêm câu chứng nhận), và **participants chỉ export khi event COMPLETED** (nút disable + hint + guard trong hàm).

---

## PHẦN 2 — Điều tra "login leader1 ra Team Nexus"

User thắc mắc login `leader1@fpt.edu.vn` lại ra **Team Nexus** (không phải Phoenix). Query DB:
- `leader1` là LEADER ở **CẢ HAI**: Team Phoenix (Spring/COMPLETED) **và** Team Nexus (Summer/OPEN) — seed cố ý cho 1 người thi nhiều mùa.
- `getMyTeam` chỉ lấy event status OPEN/SETUP/IN_PROGRESS → dashboard hiện đội mùa đang mở (Nexus).
- **Không phải bug.** Muốn xem Spring → Leaderboard chọn event, hoặc (đề xuất tiếp theo) làm **History**.
- Mật khẩu mọi account seed: `Test@1234`.

---

## PHẦN 3 — Thiết kế History (Q&A, chốt qua AskUserQuestion)

Quyết định của user:
- History participant = **trang riêng** trong sidebar.
- Nội dung mỗi event: **team & thành viên + thứ hạng/kết quả final + giải đã đạt + bài nộp**.
- **Mentor** = có history **read-only theo mùa**.
- **Certificate** = **trang in được (browser → PDF)**, tiếng Anh.

Plan 3 phần: (1) Participant History, (2) Certificate, (3) Mentor History.

---

## PHẦN 4 — Participant History (BE + FE)

**BE:**
- `dto/response/TeamHistoryResponse.java` (mới) — event + team + members + rounds (published) + submissions + prize.
- `service/TeamService.getMyHistory(userId)` — inject thêm `RoundRepository/RoundResultRepository/SubmissionRepository/PrizeRepository`; duyệt `findByUser_UserIdOrderByIdDesc` (mọi event), với mỗi đội: members, kết quả từng vòng **đã publish** (`advanced = rank ≤ topN`), submissions (bỏ DRAFT), giải announced.
- `controller/TeamController`: `GET /api/teams/my/history` (PARTICIPANT).

**FE:**
- `features/teams/HistoryPage.tsx` (mới) — route `/history`, sidebar **"History"** thêm vào **cả 4 biến thể nav participant** + title map. Card mỗi event: chip mùa, tên event, standing pill (🏆 giải / Finalist / Eliminated·round / Disqualified), thanh accent trái đổi màu theo kết quả; mở rộng → members / kết quả từng vòng / submissions (link).
- `shared/apiClient.ts`: `TeamHistoryEntry` + `teamsApi.getMyHistory`.

**Sự cố ngoài lề:** build BE báo lỗi ở `HackathonApplication.java` — file **bị hỏng sẵn từ trước** (`public class` bị cắt thành `public cl` + xuống dòng). Không phải do mình; đã sửa lại để build.

---

## PHẦN 5 — Certificate (trang in được)

- `features/teams/CertificateModal.tsx` (mới) — giấy chứng nhận **tiếng Anh**, nền kem, viền kép, serif. **Achievement** (đội có giải, viền gold theo hạng) vs **Participation**. Mở từ History, **chỉ event COMPLETED**. Người nhận = `currentUser.full_name` (auth context dùng snake_case).

**Chỉnh theo yêu cầu user (vòng review):**
- **Bỏ icon** trên giấy; **font to/đẹp hơn**: thêm **Playfair Display** + **EB Garamond** vào `styles/index.css` (qua Google Fonts, app vốn đã dùng) — tiêu đề/tên dùng Playfair (tên 56px).
- **Achievement BỎ track** (giải là toàn sự kiện): *"for achieving Champion (Rank #1) with team "Team Phoenix" at SEAL Spring 2026."*
- **Participation có track + vòng hoàn thành**: *"for participating in … as Member of team "…" in the Web Application track, completing the Semi-final round."*
- **Logo FPT** (`@/imports/fpt-logo.png`) căn giữa trên dòng "SEAL Hackathon · …".
- **Fix lỗi in ra 2 bản:** nguyên nhân `position: fixed` lặp trên mỗi trang khi app ẩn vẫn chiếm 2 trang. Sửa: render certificate qua **`createPortal(document.body)`**; print CSS **ẩn `#root`** + backdrop về tĩnh → còn đúng **1 trang**.

**Làm rõ với user:** certificate **không tự sinh/tự gửi**. Là self-service — nút chỉ **mở khóa tự động khi event COMPLETED** (cùng lúc participants-CSV bên coordinator mở khóa). Khác với giải thưởng (announce thủ công → notification).

---

## PHẦN 6 — Mentor History (BE + FE, read-only theo mùa)

**BE:**
- `dto/response/MentorHistoryResponse.java` (mới) — event → tracks → teams (finalRank + prizeName + status).
- `service/AssignmentService.getMentorHistory(userId)` — inject `RoundResultRepository + PrizeRepository`; dùng `findActiveByMentor` (trả mọi event, isActive=true), gom theo event → track → đội APPROVED, kèm thứ hạng final (published) + giải announced; sort event mới nhất trước.
- `controller/AssignmentController`: `GET /api/mentor/assignments/history` (MENTOR).

**FE:**
- `features/tracks/MentorHistoryPage.tsx` (mới) — route `/mentor/history`, sidebar **"History"** cho MENTOR + title map. Card mỗi event → track + bảng đội (Final #rank, 🏆 giải, status), đội sắp theo hạng.
- `shared/apiClient.ts`: `MentorHistoryEntry` + `assignmentsApi.getMentorHistory`.

---

## Tổng hợp file đã chạm (phần 2)

**BE mới:** `dto/response/TeamHistoryResponse.java`, `dto/response/MentorHistoryResponse.java`.
**BE sửa:** `service/TeamService.java` (+4 repo, getMyHistory), `controller/TeamController.java` (endpoint), `service/AssignmentService.java` (+2 repo, getMentorHistory), `controller/AssignmentController.java` (endpoint), `HackathonApplication.java` (sửa file hỏng).

**FE mới:** `features/scoring/CoordPrizesPage.tsx`, `features/teams/HistoryPage.tsx`, `features/teams/CertificateModal.tsx`, `features/tracks/MentorHistoryPage.tsx`.
**FE sửa:** `shared/apiClient.ts` (prizesApi/Prize, TeamMember.memberRole, TeamHistoryEntry+getMyHistory, MentorHistoryEntry+getMentorHistory), `features/scoring/LeaderboardPage.tsx` (thêm rồi gỡ awards card — net không đổi), `app/routes/index.tsx` (3 route + import), `app/layouts/DashboardLayout.tsx` (nav Awards + History participant/mentor + title map), `styles/index.css` (2 font certificate).

**DB:** chỉ query đọc (không sửa schema/seed ở phần 2).

---

## Trạng thái & việc còn mở
- Participant History + Certificate + Mentor History: **xong**, BE `BUILD SUCCESS`, FE `vite build` exit 0. Cần **restart backend** để có endpoint mới.
- Test nhanh: participant `leader1@fpt.edu.vn` → History (thấy Spring/Phoenix + Summer/Nexus) → event COMPLETED bấm 🎓 Certificate → in 1 trang có logo FPT. Mentor `mentor.an@fpt.edu.vn` → History.
- **Đề xuất chưa làm (chờ duyệt):** tự gửi notification "Your certificate is ready" cho toàn bộ participant khi event chuyển COMPLETED (sửa nhỏ ở BE chỗ complete-event).
- Badge giải ở team dashboard, render-PDF certificate phía server: vẫn là hướng mở rộng.
