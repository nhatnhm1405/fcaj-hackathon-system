# ★ AI LOG — Round/Contest Countdown Timer (CONTEST + JUDGING) — FULL-STACK — 2026-06-26

> Phiên xây dựng tính năng **đồng hồ đếm ngược theo vòng thi** cho hệ thống SEAL Hackathon:
> Coordinator cấu hình + điều khiển một bộ đếm thời gian **per-round, theo 2 phase** — `CONTEST`
> (khoá nộp bài của thí sinh) và `JUDGING` (khoá chấm điểm của giám khảo). Thí sinh/giám khảo thấy
> đồng hồ đếm ngược + thông báo theo mốc; Mentor xem read-only.
>
> Log gộp cả **backend (Spring Boot)** lẫn **frontend (React + TS)** vì tính năng xuyên 2 tầng.
> Bản sao của log này cũng nằm ở `front-end/AI logs/`.

---

## 1. Bối cảnh & yêu cầu

Hệ thống đã có chức năng **release đề thi** ("đề thi" per-track, nút **RELEASE ALL** ở
`CoordEventsPage` → tab Problems → `TrackProblemsTab`). Yêu cầu mới của người dùng:

- Một **đồng hồ đếm ngược** bắt đầu khi BTC "publish đề tới thí sinh".
- Thời lượng **cấu hình được**; **stop bất cứ lúc nào**.
- Phía thí sinh: **thông báo theo mốc** (còn 30/15/5 phút) và **đã trôi qua 50%**.
- UX wheel picker giờ/phút/giây giống ảnh iOS đính kèm.
- Mở rộng (trong lúc thảo luận): **timer chấm thi cho Judge** + **hiển thị giờ còn lại cho Mentor**.

Ràng buộc thực tế phát hiện khi khảo sát code:
- Notification hiện tại là **DB + polling 25s** (`NotificationProvider`), **không** WebSocket/SSE,
  backend **chưa** có `@EnableScheduling`.
- `Round` đã có sẵn `submissionDeadline`/`status`; nhưng **nộp trễ KHÔNG bị chặn** —
  `SubmissionService.submit()` chỉ đánh dấu `LATE` (dòng `:59`). `ScoringService.submitScores()`
  **không** gate thời gian gì cả ⇒ "khoá khi hết giờ" là **luật cứng MỚI**.
- DB quản lý thủ công (`spring.jpa.hibernate.ddl-auto=none`) ⇒ thêm bảng phải viết SQL tay.

---

## 2. Quá trình thảo luận & quyết định (Q&A với người dùng)

| Vấn đề | Lựa chọn của người dùng |
|--------|--------------------------|
| **Phạm vi đồng hồ** | **Theo Round** (mỗi vòng 1 đồng hồ) |
| **Hết giờ** | **Khoá nộp bài / khoá chấm** (gate cứng) |
| **Điều khiển** | **Stop + Pause/Resume + Extend** (+ thêm giờ) |
| **Mốc giờ** | **Banner tức thời + lưu chuông** (cả 2) |
| **Bố trí UX (publish↔timer)** | **Option B — panel "Contest Timer" tách riêng per-round**, tự bấm START sau khi cho thí sinh đọc đề |
| **Ai xem đồng hồ** | Participant + Coordinator (+ Judge cho JUDGING, Mentor read-only cho CONTEST) |
| **JUDGING: hết giờ khoá gì** | Khoá **cả draft lẫn final** |
| **JUDGING: bắt đầu** | **Thủ công** (auto-chain để sau) |
| **JUDGING: draft lúc đóng** | **Để nguyên** (không tự chốt) |
| **Mentor** | Chỉ xem **CONTEST** read-only; có banner mốc, **không** ghi chuông |
| **Ngôn ngữ hiển thị** | **Tiếng Anh** (đồng bộ toàn app) — chốt ở cuối phiên |

### Soát "hardcode" (theo yêu cầu người dùng)
- **Mốc nhắc giờ** → **mở cấu hình** ngay từ data model: `milestone_minutes` (CSV) + `notify_at_half`;
  API `start` nhận `milestoneMinutes[]`/`notifyAtHalf`, không truyền thì default `[30,15,5,1]`+half.
- Trần giờ wheel, mức Extend nhanh, thời lượng tối thiểu → để hằng số/props dễ chỉnh, không chôn cứng.

---

## 3. Quyết định kiến trúc then chốt

1. **Thời gian do SERVER quyết định** — lưu `started_at` + `duration` ⇒ suy ra `ends_at`. Client
   tính `remaining = ends_at − (clientNow + skew)` với `skew = serverNow − clientNow` (trả kèm trong
   mọi response). Không bao giờ tin đồng hồ client ⇒ công bằng.
2. **Không cần scheduler** (vì backend chưa có `@EnableScheduling`):
   - **Banner mốc**: client tự tick 1s, phát hiện "cạnh" (prev > threshold ≥ now) ⇒ bắn banner ngay
     (chính xác từng giây).
   - **Lưu chuông (exactly-once)**: materialize **lười** khi có client gọi `GET /timer`; mỗi mốc được
     "claim" qua bảng `RoundTimerNotice` (unique key) trong **transaction REQUIRES_NEW**
     (`TimerNoticeClaimer`) ⇒ thắng claim mới fan-out, kẻ thua bị rollback cô lập, không làm hỏng
     transaction đọc. Reach được cả user offline (notification được fan-out cho toàn audience).
3. **Mốc tự thích nghi** — chỉ bắn mốc < tổng thời lượng (+ luôn có 50%). Ví dụ contest 3 phút chỉ
   bắn "50%" và "còn 1 phút", không có mốc "30 phút" vô nghĩa.
4. **Opt-in per round** — round nào không bật timer ⇒ giữ nguyên hành vi cũ (không gate).
5. **1 cơ chế, 2 phase** — thay vì nhồi cột vào `Round`, dùng bảng riêng `RoundTimer(round_id, phase)`;
   cùng state machine cho cả CONTEST và JUDGING.
6. **Sống sót reload/restart** — toàn bộ state nằm DB; countdown luôn tính lại từ `ends_at`; trạng thái
   `EXPIRED` suy lười (và được persist khi đọc).

---

## 4. Data model (SQL — `ddl-auto=none`)

Thêm 2 bảng (`back-end/database scripts/seal_schema.sql` cho cài mới + `seal_roundtimer.sql` migration
cho DB sẵn có; cập nhật header schema 24→26 bảng + changelog).

```sql
CREATE TABLE RoundTimer (
  timer_id, round_id (FK→Round ON DELETE CASCADE), phase ENUM-like(CONTEST|JUDGING),
  status (IDLE|RUNNING|PAUSED|STOPPED|EXPIRED),
  duration_seconds, started_at, ends_at, paused_at, remaining_at_pause,
  milestone_minutes DEFAULT '30,15,5,1', notify_at_half DEFAULT 1, updated_at,
  UNIQUE (round_id, phase)
);
CREATE TABLE RoundTimerNotice (   -- exactly-once ledger cho fan-out mốc
  id, round_id (FK), phase, milestone_key
  (STARTED|REM_30|REM_15|REM_5|REM_1|HALF|EXPIRED|STOPPED),
  created_at, UNIQUE (round_id, phase, milestone_key)
);
```

---

## 5. Backend (Spring Boot)

**Files mới**
- `entity/RoundTimer.java`, `entity/RoundTimerNotice.java`
- `repository/RoundTimerRepository.java`, `repository/RoundTimerNoticeRepository.java`
  (`findByRound_RoundIdAndPhase`, `findByRoundIdAndPhase`, `existsBy…`, `deleteByRoundIdAndPhase`)
- `dto/request/StartTimerRequest.java` (`durationSeconds` `@Positive`, optional `milestoneMinutes[]`,
  `notifyAtHalf`), `dto/request/ExtendTimerRequest.java` (`seconds`)
- `dto/response/RoundTimerResponse.java` (`status, durationSeconds, startedAt, endsAt,
  remainingSeconds, serverNow, milestoneMinutes, notifyAtHalf`)
- `service/RoundTimerService.java` — state machine + materialize + fan-out + gate helpers
- `service/TimerNoticeClaimer.java` — claim exactly-once (REQUIRES_NEW)
- `controller/RoundTimerController.java`

**Files sửa**
- `service/SubmissionService.java` — chèn `roundTimerService.assertContestOpen(roundId)` sau check
  `ACTIVE` ⇒ chặn cứng nộp khi CONTEST không RUNNING.
- `service/ScoringService.java` — chèn `roundTimerService.assertJudgingOpen(submission.getRound()…)`
  ⇒ chặn cứng chấm (cả draft) khi JUDGING không RUNNING.

**State machine** (mọi action ghi `AuditLogService`):

| Action | Điều kiện | Hiệu ứng |
|--------|-----------|----------|
| `start(phase, req)` | IDLE/STOPPED/EXPIRED | RUNNING; `ends_at=now+duration`; xoá notice cũ; nếu CONTEST → `round.status=ACTIVE` + `submissionDeadline=ends_at`; fan-out STARTED |
| `pause` | RUNNING | PAUSED; lưu `remaining_at_pause` |
| `resume` | PAUSED | RUNNING; `ends_at=now+remaining` |
| `extend(s)` | RUNNING/PAUSED | cộng `ends_at`/`remaining` + `duration`; hồi sinh nếu đã hết |
| `stop` | RUNNING/PAUSED | STOPPED; `ends_at=now`; fan-out STOPPED |
| `getState` (đọc) | — | tính remaining; materialize mốc đã qua; persist EXPIRED khi remaining≤0 |

**Audience fan-out**: CONTEST → thành viên team APPROVED của mọi track trong event;
JUDGING → judge được phân công round (`findAllByRound_RoundIdAndIsActiveTrue`). Notification type = `TIMER`.

---

## 6. Frontend (React + TypeScript, `front-end/src/seal-web/src`)

**Files mới**
- `shared/hooks/useRoundTimer.ts` — fetch + re-sync 20s, bù skew, tick 1s, phát hiện cạnh mốc → bắn
  `addToast` (adaptive). Trả `{status, remainingSeconds, isConfigured, isRunning, isPaused, …}`.
- `shared/components/CountdownDisplay.tsx` — `hh:mm:ss` đổi màu (green→orange ≤5p→red ≤1p) + badge +
  **icon đồng hồ** (prop `icon`).
- `shared/components/WheelTimePicker.tsx` — drum picker H/M/S (scroll-snap, theme pixel) **+ ô gõ số
  nhanh** H/M/S.
- `features/events/ContestTimerPanel.tsx` — điều khiển Coordinator cho 2 phase (START/PAUSE/RESUME/
  EXTEND +5m hoặc wheel/STOP).

**Files sửa**
- `shared/apiClient.ts` — `timersApi` + types `RoundTimerState`/`StartTimerPayload`/`TimerPhase`.
- `features/events/CoordEventsPage.tsx` — thêm **tab "Timers"** (round selector kiểu tab Criteria →
  `ContestTimerPanel`).
- `features/submissions/TeamSubmitPage.tsx` — countdown CONTEST + fold `timerBlocks` vào `canSubmit` +
  banner trạng thái.
- `features/scoring/JudgeScoringPage.tsx` — countdown JUDGING + fold vào `open` (disable nút lưu) + banner.
- `features/dashboard/dashboards/MentorDashboard.tsx` — `MentorContestTimer` read-only (resolve event
  theo tên/`IN_PROGRESS` → round ACTIVE → CONTEST).
- `app/providers/NotificationProvider.tsx` — thêm `rawType`; **bỏ auto-banner type `TIMER`** (tránh
  banner đúp; vẫn lưu vào chuông).

---

## 7. API surface (mới)

Base: `/api/events/{eventId}/rounds/{roundId}/timer/{phase}` (`phase` = `CONTEST` | `JUDGING`)

| Method | Path | Quyền | Ghi chú |
|--------|------|-------|---------|
| POST | `/start` | EVENT_COORDINATOR | body `{durationSeconds, milestoneMinutes?, notifyAtHalf?}` |
| POST | `/pause` `/resume` `/stop` | EVENT_COORDINATOR | |
| POST | `/extend` | EVENT_COORDINATOR | body `{seconds}` |
| GET | `` (state) | isAuthenticated | trả state + `serverNow`; materialize mốc |

Postman: thêm folder **"17. ROUND TIMERS"** (11 request: start/get/extend/pause/resume/stop CONTEST,
start/get JUDGING, + 3 ca 403/400) vào `back-end/Postman/Postman_Full_Collection.json`.

---

## 8. Điều chỉnh trong lúc người dùng test (cùng phiên)

1. **`Table 'seal_hackathon.roundtimer' doesn't exist`** → chạy migration `seal_roundtimer.sql` vào DB
   (MySQL 8.0, `root`); `ddl-auto=none` nên backend không cần restart.
2. **Wheel chỉ cuộn được** → thêm **3 ô nhập số** H/M/S (gõ nhanh + đồng bộ 2 chiều với wheel).
3. **Cho set tới 99 giờ** → `maxHours=99` cho picker START (Extend lên 12h). Giữ phút/giây 0–59.
4. **Icon đồng hồ** cho Participant/Judge/Mentor (Coordinator không icon) — lucide `Clock`.
5. **Xoá note theo yêu cầu** (Option A cho team-lock):
   - `TeamViewPage.tsx`: bỏ **banner** "🔒 The contest is in progress — your team is locked…" nhưng
     **giữ khoá** (`isTeamEditable`); dọn `lockReason` + import `teamLockReason`.
   - Bỏ subtitle: `CoordAccountsPage` ("Review and approve accounts awaiting access…"),
     `CoordinatorDashboard` ("System overview for hackathon coordination."),
     `CoordJudgesPage` ("Mentor theo hạng mục · Judge theo vòng × hạng mục").
6. **i18n → tiếng Anh** (đồng bộ app): toàn bộ chuỗi notification backend (`RoundTimerService.messageFor`
   + `formatDuration`), banner mốc trong `useRoundTimer`, 2 banner gating ở `TeamSubmitPage`/
   `JudgeScoringPage`. (Notification CŨ đã lưu trong DB vẫn là tiếng Việt — chỉ bản MỚI là EN.)

---

## 9. Kiểm thử / Verify

- **Backend**: `mvnw -o compile` → **BUILD SUCCESS** (nhiều lần qua phiên).
- **Frontend**: `npm run build` (vite) → **✓ built** (lần cuối ~3.97s, exit 0).
- **Typecheck `tsc`**: các file của feature **sạch**. Có **8 lỗi type CÓ SẴN từ trước** không thuộc
  feature (`DashboardLayout.tsx`, `InvitationsDrawer.tsx`, `ProfilePage.tsx`) + lỗi `vite.config.ts`
  (xung đột vite/vitest) + deprecation `baseUrl` — đều **pre-existing**, build thật dùng esbuild nên
  không chạy `tsc`.

---

## 10. Caveats & việc còn lại

- **Backend devtools** có trong pom ⇒ nếu chạy `mvnw spring-boot:run`/IDE thì auto-restart khi recompile;
  nếu chạy `.jar` đóng gói thì **restart tay** để áp text EN mới.
- **Mentor** resolve event/round **phía frontend** (find theo tên/`IN_PROGRESS`) để khỏi đổi backend —
  giả định "một event IN_PROGRESS tại một thời điểm".
- **Option B (team-unlock)** chưa làm — người dùng chọn Option A (chỉ ẩn note). Nếu sau muốn cho sửa
  đội giữa contest cần verify guard backend trong `TeamService`.
- **Auto-chain** (contest hết giờ → tự mở JUDGING) chưa làm (chốt thủ công cho MVP).
- Cân nhắc tương lai: nếu thêm `@EnableScheduling`, có thể chuyển materialize mốc sang cron cho "sạch"
  hơn (bỏ side-effect trên `GET`).

---

## 11. Danh sách file

**Backend — mới (9):** `entity/RoundTimer.java`, `entity/RoundTimerNotice.java`,
`repository/RoundTimerRepository.java`, `repository/RoundTimerNoticeRepository.java`,
`dto/request/StartTimerRequest.java`, `dto/request/ExtendTimerRequest.java`,
`dto/response/RoundTimerResponse.java`, `service/RoundTimerService.java`,
`service/TimerNoticeClaimer.java`, `controller/RoundTimerController.java`
+ SQL: `database scripts/seal_roundtimer.sql` (mới), `database scripts/seal_schema.sql` (sửa),
+ `Postman/Postman_Full_Collection.json` (folder 17).

**Backend — sửa (2):** `service/SubmissionService.java`, `service/ScoringService.java`.

**Frontend — mới (4):** `shared/hooks/useRoundTimer.ts`, `shared/components/CountdownDisplay.tsx`,
`shared/components/WheelTimePicker.tsx`, `features/events/ContestTimerPanel.tsx`.

**Frontend — sửa (8):** `shared/apiClient.ts`, `features/events/CoordEventsPage.tsx`,
`features/submissions/TeamSubmitPage.tsx`, `features/scoring/JudgeScoringPage.tsx`,
`features/dashboard/dashboards/MentorDashboard.tsx`, `app/providers/NotificationProvider.tsx`,
`features/users/CoordAccountsPage.tsx`, `features/dashboard/dashboards/CoordinatorDashboard.tsx`,
`features/scoring/CoordJudgesPage.tsx`, `features/teams/TeamViewPage.tsx`.

---

## 12. Timeline tóm tắt

1. Khảo sát code (release đề, notification polling, round/submission/scoring, theme pixel).
2. Thảo luận & chốt 4 quyết định chính + Option B + Judge/Mentor + soát hardcode.
3. Implement backend (SQL → entity/repo → DTO → service/state machine → controller → gating →
   fan-out) → compile OK.
4. Thêm Postman folder 17 để test.
5. Plan + implement frontend (apiClient → hook → components → coordinator tab → role integration →
   dedup) → build OK.
6. Người dùng test → fix: migration, wheel type-in, 99h, clock icon, xoá notes (Option A), i18n EN.
7. Verify lại BE compile + FE build → viết log này.
