# AI LOG — Demo Scenarios: bàn "hoàn thiện main flow" + seed S2.5 (award demo) — BACKEND — 2026-07-14

> Phiên tập trung vào bộ demo scenario code-first (`DemoScenario`/`DemoSeeder`/`run-demo.ps1`).
> Hai phần: (1) **thảo luận** các điểm bất hợp lý của luồng demo S0/S1/S2 để hoàn thiện main
> flow (chốt hướng, chưa code phần S0/S1); (2) **deliverable thực tế**: seed thêm **scenario
> S2.5 (`S25`)** cho màn demo trao giải (award).
> Nối tiếp: [[AI_LOG_SEAL_DEMO_SCENARIO_REVAMP_BE_2026-07-12]], [[AI_LOG_SEAL_RUN_DEMO_SCRIPT_BE_2026-07-11]].

---

## 1. Bối cảnh & phạm vi

Bộ demo hiện có 4 cut-point `S0..S3` (code-first, drop DB → reseed). Yêu cầu ban đầu: lấy
context kỹ `run-demo.ps1` + cây seed, tập trung **S1/S2** (2 luồng demo "chưa hoàn chỉnh"),
rồi thảo luận điểm bất hợp lý để hoàn thiện main flow. Cuối phiên chuyển sang seed **S2.5**.

**Nguyên tắc phiên (người dùng chốt):** thảo luận **chốt ý tưởng từng scenario một** trước
khi code; và **báo defect ngoài dự kiến** khi seed.

## 2. Context đã đọc

- Seed: `config/seed/DemoScenario.java`, `DemoFixtures.java`, `DemoSeeder.java`, `run-demo.ps1`,
  `application.properties`.
- Service liên quan main flow / award: `HackathonEventService` (state machine + gate SETUP/START),
  `LeftoverGroupingService` + `grouping/LeftoverGroupingPlanner`, `TeamService` (draw/self-select),
  `RoundResultService` (**finalizeRound = calculate ranking**), `PrizeService` (autoGenerate + announce = **award**),
  `JudgeScoringCompletenessService` (**gate của finalize**), `ScoringService` (gate `assertJudgingOpen`),
  `RoundService`, `RoundTimerService`.
- Entities: `RoundTimer`, `JudgeAssignment`, `Submission`.
- Requirements: `docs/documents/ProjectRequirements.md` (đặc biệt **§14 CORE FLOW DEMO**, §4.5/4.6, §6.1-6.3, UC02).
- AI logs cũ: revamp 07-12, leftover grouping 07-10/07-11, team lifecycle 07-12, code-first 07-10, run-demo 07-11, round timer 06-26.

## 3. Thảo luận: điểm bất hợp lý S1/S2 (để hoàn thiện main flow)

State machine main flow: `DRAFT → OPEN → SETUP → IN_PROGRESS → COMPLETED`. Khúc nặng nhất là
**SETUP** (duyệt team PENDING → đóng đăng ký → gom người lẻ → gán/bốc track → START).

Các điểm bất hợp lý phát hiện (thời điểm đầu phiên):

| # | Điểm | Chi tiết / nguồn |
|---|------|------------------|
| 1 | **S1 không đi tới IN_PROGRESS được** | Gate `requireSetupComplete` đòi **mỗi track ≥ 2 team** (`MIN_TEAMS_PER_TRACK=2`) × 4 track ⇒ cần ≥ 8 team; S1 (bản cũ) gom xong chỉ ra ≤ 6 → dead-end. Regression giữa gate 07-10 và thiết kế track 06-16. |
| 2 | **Spare `leader1@`/`member1@` bị grouping nuốt** | Là student approved+active không team ⇒ lọt `findGroupableFreeAgents` → vi phạm "cut-line" seed/live ([[AI_LOG_SEAL_★_SETUP_LEFTOVER_TEAM_GROUPING_FULLSTACK_2026-07-10]]). |
| 3 | **Hardcode `RANDOM`** | Mode mặc định app là `SELF_SELECT` (§ track-assignment); demo không chạm được luồng leader tự chọn track. |
| 4 | **S1/S2 rời rạc** | Không nối thành 1 event; khúc SETUP không demo end-to-end bằng seed. |

**Quyết định đã chốt trong thảo luận:** (A) làm S1 đi trọn tới IN_PROGRESS; track mode = **SELF_SELECT**;
sau đó chốt thêm: **3 scenario S1/S2/S3 cùng số = 15 team / 45 người** (số "đông cứng" từ lúc đóng đăng ký).

**⚠️ Ghi chú thực tế:** trong lúc bàn, `DemoScenario` **bị rework song song** (đồng đội) — S1 nay
= 15 team qua hằng `S1_EXTRA_APPROVED_TEAMS` (vẫn `RANDOM`), S2 đổi thành "prelim chấm xong, chờ
calculate ranking (live)" + seed **timer EXPIRED**. Vì vậy các sửa S1 (SELF_SELECT/45-người) làm ở
đầu phiên **đã bị ghi đè** và **phần S1 vẫn còn ngỏ** (chưa áp SELF_SELECT + walk-to-IN_PROGRESS).

## 4. Chốt lại cách làm: thảo luận từng scenario, bắt đầu S0

### 4.1. Sửa khung S0 theo đúng §14 (đây là chỗ mình từng hiểu sai)

Ban đầu mình khung S0 = "kịch bản admin quản lý account". **Sai.** `ProjectRequirements.md`:

- **§14 CORE FLOW DEMO** mở màn: (1) Coordinator đăng nhập → (2) **tạo event** → (3) tạo hạng mục & vòng thi → (4) tạo bộ tiêu chí. ⇒ **bước 1-4 = giai đoạn DRAFT + OPEN**, actor chính là **Event Coordinator**, không phải admin.
- **UC02 "Duyệt tài khoản" → Event Coordinator**; §6.5 "ban tổ chức phê duyệt". §4.6 Admin chỉ "quản lý tài khoản hệ thống, phân quyền, cấu hình" — **không duyệt participant**.

**Hệ quả (đã chốt):** S0 = **Event ở giai đoạn Draft + Open** (coordinator dựng & mở event), KHÔNG
phải màn admin. Bộ account seed sẵn (coordinator/mentor/judge) là "bàn khởi đầu". Còn
**pending/inactive account = luồng duyệt/kích hoạt của Coordinator (gắn event)** → thuộc S1, không phải S0.
(Riêng `AdminService.setUserActive` chỉ là lever kích hoạt lại **guest judge** — guest judge không có luồng self-service.)

Còn ngỏ ở S0: chốt mức seed **(A) live 100% / (B) seed event DRAFT trơ / (C) seed nguyên event DRAFT rồi chỉ OPEN**. (Đề xuất C; chưa chốt.)

### 4.2. Mẹo vận hành: resume không drop DB

Hỏi: lỡ `Ctrl+C` backend sau khi seed S0, muốn chạy tiếp mà không drop + reseed?
→ **`./run-demo.ps1 S0 -NoDrop`**. `Ctrl+C` chỉ tắt app, **không** đụng MySQL; `-NoDrop` bỏ bước
drop; `DemoSeeder` guard (`existsByEmail(coordinator@)`) thấy data cũ → **skip seed** → boot tiếp.
Thậm chí chỉ cần Run lại từ IntelliJ (DB persist sẵn). Ngoại lệ: Ctrl+C **đúng lúc đang seed** →
transaction rollback/dở → lúc đó mới cần drop.

## 5. ★ Deliverable: seed scenario S2.5 (`S25`) — demo trao giải

### 5.1. Mục tiêu

Seed state **"tới ngay trước bước calculate ranking cuối cùng cho final round"**: đủ mọi thông
tin + điểm đã chấm của final submission, **chờ coordinator bấm Calculate ranking → Award**.

### 5.2. Verify luồng award TRƯỚC khi seed (đọc code, không đoán)

| Bước (coordinator) | Service | Điều kiện đầu vào |
|---|---|---|
| **Calculate ranking** | `RoundResultService.finalizeRound` | round chưa FINALIZED; có submission; **`JudgeScoringCompletenessService.assertRoundComplete` PASS**; **không** gate theo timer |
| Auto-generate prizes | `PrizeService.autoGenerate` | final round **FINALIZED** + có RoundResult |
| **Announce = Award** | `PrizeService.announce` | có prize + mỗi prize có team → set `awardedAt`, notify, audit `AWARD_PRIZE` |

**Gate then chốt = completeness** (S3 KHÔNG kiểm vì ghi `RoundResult` thẳng qua fixture; còn S2.5
để coordinator bấm LIVE → phải thoả gate): mỗi submission, mọi `JudgeAssignment` **active** của
(round, track) — final assign `track=null` nên phủ mọi track — phải FINAL đủ mọi criteria (non-draft).

**Verify default để seed thoả gate:**
- `JudgeAssignment.isActive` default `true` (`@Builder.Default`) ✓ (nếu null thì query `...AndIsActiveTrue` loại hết → gate fail).
- `Submission.submittedAt` tự set `@PrePersist` ✓ (tie-break ranking).
- `fx.score(...)` set `isDraft=false` ✓.
- **`ScoringService.submitScores` gate `roundTimerService.assertJudgingOpen`** ("no timer/pause/stop/expiry đều chặn ghi điểm") → nên seed **ghi điểm trực tiếp (bypass) + seed timer EXPIRED** = đúng pattern S2.

### 5.3. Thiết kế state S2.5

- Event **IN_PROGRESS**; 15 team / 4 track (4-4-4-3) — tái dùng nguyên khối roster của S2/S3.
- **Vòng loại: FINALIZED** — đã chấm + `RoundResult` (published) + xác định 8 finalist (top-2/track).
- **Vòng chung kết: ACTIVE** — 8 finalist đã nộp + **đã chấm đủ** (3 judge × 5 criteria, non-draft), **chưa** có `RoundResult`, **chưa** có prize.
- **Timer**: cả 2 vòng seed CONTEST + JUDGING = **EXPIRED** (khớp production + FE, mirror S2).

Điểm cắt: khối `S3` chạy tới sau `writeScores(finalSubs, ...)` thì **return** cho `S25` (trước khi
tạo final `RoundResult`/prize/deactivate/systemlog).

### 5.4. Thay đổi code

**`config/seed/DemoScenario.java`**
- `eventStatusFor`: `S25 → IN_PROGRESS`.
- `roundStatusFor`: `S25 → prelim FINALIZED, final ACTIVE`.
- `windowFor`: cửa sổ `S25` (reg −45/−25, event −16 → +5; ⇒ prelim & final đều đã đóng, event còn chạy).
- Chèn block `if ("S25")` sau `writeScores(finalSubs, ...)`: seed **4 timer EXPIRED** (prelim/final × CONTEST/JUDGING) + log + `return`.
- Javadoc lớp: thêm dòng S25.

**`config/seed/DemoSeeder.java`** — `VALID += "S25"`.

**`run-demo.ps1`** — `ValidateSet += 'S25'` + help param + list scenario.

**`application.properties`** — comment `app.seed.scenario` thêm S25.

### 5.5. Luồng demo (chạy `./run-demo.ps1 S25`)

1. Coordinator → final round → **Calculate ranking** (`finalizeRound`) → xếp hạng 8 finalist (top 3 = 3 track khác nhau, tất định).
2. (tuỳ) Publish results.
3. **Auto-generate prizes** (top 3) → **Announce (Award)** → winner nhận notification + audit `AWARD_PRIZE`.

## 6. Defect check (theo yêu cầu "báo defect ngoài dự kiến")

- **Không thấy defect trong đường award của S2.5.** Gate completeness thoả; `finalizeRound` không
  gate timer nên backend award chạy kể cả thiếu timer (vẫn seed timer cho khớp FE/production).
- **Latent (không dính S2.5, báo để biết):** `RoundResultService.mapToResponse` gọi
  `r.getTeam().getTrack().getName()` **không null-check** → NPE nếu có đội xếp hạng mà `track == null`.
  S2.5 an toàn (mọi đội đều có track), nhưng là chỗ giòn nếu sau này rank đội không track.

## 7. Files

**Sửa:**
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/config/seed/DemoScenario.java`
- `back-end/src/seal-api/src/main/java/com/seal/hackathon/config/seed/DemoSeeder.java`
- `back-end/src/seal-api/run-demo.ps1`
- `back-end/src/seal-api/src/main/resources/application.properties`

## 8. Verify

- `./mvnw.cmd -o clean compile` → **BUILD SUCCESS** (exit 0).
- **CHƯA** boot end-to-end (script drop DB → người dùng tự chạy). Toán completeness/ranking đã trace tay:
  8 finalist × 3 judge × 5 criteria non-draft → `assertRoundComplete` PASS → ranking 8 đội → award top 3.

## 9. Còn ngỏ

- **S0**: chốt mức seed (A/B/C) rồi mới code phần "Coordinator dựng event Draft+Open".
- **S1**: vẫn ngỏ SELF_SELECT + walk-to-IN_PROGRESS (bản hiện tại là rework 15-team/`RANDOM` của đồng đội; các sửa S1 đầu phiên đã bị ghi đè).
- (tuỳ) Unit test tự động: seed `S25` → giả lập calculate final → assert 8 result + 3 prize.
- (tuỳ) Vá null-check track ở `RoundResultService.mapToResponse`.

## 10. Liên quan
[[AI_LOG_SEAL_DEMO_SCENARIO_REVAMP_BE_2026-07-12]] · [[AI_LOG_SEAL_RUN_DEMO_SCRIPT_BE_2026-07-11]] ·
[[AI_LOG_SEAL_★_SETUP_LEFTOVER_TEAM_GROUPING_FULLSTACK_2026-07-10]] ·
[[AI_LOG_SEAL_TEAM_LIFECYCLE_RULES_BE_2026-07-12]] · [[AI_LOG_SEAL_ROUND_TIMER_FULLSTACK_2026-06-26]]
