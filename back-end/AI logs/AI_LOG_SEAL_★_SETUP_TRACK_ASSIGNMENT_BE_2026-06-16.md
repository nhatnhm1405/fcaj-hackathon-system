# ★ AI LOG — SETUP Status & Track Assignment (BACKEND) — 2026-06-16

> Phiên làm việc thêm **trạng thái event `SETUP`** + **cơ chế gán track theo từng event**
> (SELF_SELECT vs RANDOM) với **giới hạn slot mỗi track tự cân bằng**.
> File song song bên FE: `front-end/AI logs/AI_LOG_SEAL_★_SETUP_TRACK_ASSIGNMENT_FE_2026-06-16.md`

---

## 1. Bối cảnh & vấn đề

Thực tế event có nhiều trạng thái hơn DB đang mô hình hoá. Cụ thể cần thêm một giai đoạn
**"đóng đơn đăng ký → bốc thăm/xếp team vào track"** nằm giữa lúc mở đăng ký và lúc thi đấu.

Khi rà soát codebase, phát hiện các vấn đề nền tảng:

- `HackathonEvent.status` là **String tự do**, không enum, không CHECK constraint trong DB.
- State machine nằm ở `HackathonEventService` (`VALID_STATUSES` + `TRANSITIONS`) — đổi status **thủ công**.
- **Không** có `@Scheduled`/cron nào tự đổi status theo ngày.
- Nhưng **đăng ký team lại bị gate theo NGÀY** (`TeamService` so `now` với `registrationStart/End`) — độc lập với status → đây là thứ khiến "phải chỉnh ngày để demo".
- Nợ kỹ thuật: `TrackService` cho phép status ma `PUBLISHED` (không có trong `VALID_STATUSES`); `AuthService`, `TeamMemberRepository` hard-code danh sách status rải rác.

---

## 2. Các quyết định thiết kế (Q&A với người dùng)

| Vấn đề | Quyết định |
|--------|-----------|
| Cơ chế đổi status | **Thủ công** (giữ `TRANSITIONS`), không suy ra từ ngày |
| Tên trạng thái mới | `SETUP` (đã loại ALLOCATION/REGISTRATION_CLOSED) |
| Gating đăng ký | Chuyển từ **theo ngày → theo status** (`status == OPEN`); ngày chỉ còn là metadata |
| Cơ chế gán track | **Theo từng event**: `track_selection_mode` = `SELF_SELECT` (mặc định) \| `RANDOM` |
| Track chọn khi nào | **Trong phase SETUP** (không chọn lúc đăng ký nữa, cả 2 mode) |
| Giới hạn team/track | **Tự tính** khi vào SETUP — không nhập tay |
| Đếm team để chốt slot | Chỉ **team APPROVED** |
| Track đầy (SELF_SELECT) | **Chặn**, buộc chọn track khác |
| Ai gán track (SELF_SELECT) | Leader tự chọn; coordinator **bốc nốt** team chưa chọn |

---

## 3. Vòng đời event (sau thay đổi)

```
DRAFT ──► OPEN ──► SETUP ──► IN_PROGRESS ──► COMPLETED
            ▲        │
            └────────┘  (SETUP → OPEN: mở lại đăng ký)
CANCELLED: từ mọi state không-terminal;  CANCELLED → DRAFT (mở lại)
```

- **OPEN** = đang nhận đăng ký team (team tạo **không** kèm track).
- **SETUP** = đóng đơn → freeze số team APPROVED, tính slot mỗi track, "mở cổng gán track".
- **IN_PROGRESS** = thi đấu (track đã chốt).

---

## 4. Thuật toán chia slot (max lệch ≤ 1 — đảm bảo toán học)

Tính khi event chuyển **OPEN → SETUP** (`HackathonEventService.computeTrackCapacities`):

```
T = số team APPROVED của event (đông cứng tại thời điểm này)
N = số track của event
floor = T / N   (chia nguyên)
r     = T % N   (phần dư)

→ r track đầu nhận capacity = floor + 1
→ (N − r) track còn lại nhận capacity = floor
→ Tổng slot = đúng T
```

Vì **tổng slot = đúng số team**, khi mọi team lấp đầy thì mỗi track đạt đúng capacity của nó →
số team mỗi track **chênh nhau tối đa 1**. Lưu vào `Track.capacity`.

> Ví dụ event 2: T=5 (APPROVED), N=4 → floor=1, r=1 → slots = **2 / 1 / 1 / 1**.

**RANDOM draw** dùng greedy "nhét vào track còn nhiều slot trống nhất" → tự nhiên ra phân bố
floor/floor+1 đúng như trên, đồng thời không bao giờ vượt `capacity`.

---

## 5. Thay đổi theo file

### 5.1 Entities
- **`entity/HackathonEvent.java`**
  - Thêm cột `status` giá trị `SETUP` (chỉ mở rộng comment, status vốn là String).
  - **Thêm field** `track_selection_mode` (`VARCHAR(20)`, default `"SELF_SELECT"`).
- **`entity/Track.java`**
  - **Thêm field** `capacity` (`Integer`, nullable — tự tính lúc vào SETUP, NULL = chưa tính/không giới hạn).
- **`entity/Team.java`**
  - `track_id` chuyển **nullable** (bỏ `nullable=false`) — team đăng ký chưa có track.

### 5.2 DTOs
- **`dto/request/CreateEventRequest.java`** — thêm `trackSelectionMode` (optional, default SELF_SELECT).
- **`dto/request/UpdateEventRequest.java`** — thêm `trackSelectionMode` (chỉ đổi được trước SETUP).
- **`dto/response/HackathonEventResponse.java`** — thêm `trackSelectionMode`.
- **`dto/response/TrackResponse.java`** — thêm `capacity`.
- **`dto/request/CreateTeamRequest.java`** — **bỏ** `trackId` (track không chọn lúc đăng ký).
- **`dto/request/SelectTrackRequest.java`** — **MỚI**: `{ @NotNull Integer trackId }`.
- **`dto/response/MyTeamResponse.java`** — thêm `eventStatus` + `trackSelectionMode` (để FE participant biết khi nào hiện picker).

### 5.3 Services
- **`service/HackathonEventService.java`**
  - `VALID_STATUSES` += `SETUP`; `TRANSITIONS` cập nhật (OPEN→SETUP→IN_PROGRESS, SETUP→OPEN, …).
  - `VALID_TRACK_MODES = {SELF_SELECT, RANDOM}` + `requireValidTrackMode()`.
  - Inject `TrackRepository`, `TeamRepository`.
  - `createEvent`: set `trackSelectionMode`.
  - `updateEvent`:
    - Đổi mode **chỉ khi** status đang DRAFT/OPEN.
    - Khi chuyển **vào SETUP** → gọi `computeTrackCapacities(event)`.
  - `computeTrackCapacities()`: thuật toán mục 4 (yêu cầu event có ≥1 track, nếu không → 400).
  - `mapToResponse`: trả `trackSelectionMode`.
- **`service/TeamService.java`**
  - `createTeam`: **bỏ** check ngày + **bỏ** chọn track → team luôn `track = null`, gate `status == OPEN`.
  - `getMyTeam`: activeStatuses += SETUP; trả thêm `eventStatus` + `trackSelectionMode`; null-guard track.
  - `getActiveEventsWithTracks`: bỏ filter ngày (chỉ lọc `status == OPEN`); `TrackResponse` trả `capacity`.
  - `drawTracks(eventId, includeAssigned)` — **viết lại capacity-aware**:
    - chỉ xếp team **APPROVED**; `includeAssigned=true` thì reset toàn bộ track về null trước.
    - greedy: mỗi team vào track có **nhiều slot trống nhất**, không vượt `capacity`; báo lỗi nếu hết slot.
  - `selectTrack(userId, teamId, trackId)` — **MỚI** (SELF_SELECT): yêu cầu leader, event SETUP, mode SELF_SELECT, team APPROVED, track thuộc event, **track chưa đầy** (đếm team APPROVED hiện có < capacity).
- **`service/TrackService.java`**
  - `TRACK_CREATION_ALLOWED_EVENT_STATUSES`: bỏ ghost `PUBLISHED` → `{DRAFT, OPEN, SETUP}`.
  - `mapToResponse`: trả `capacity`.
- **`service/AuthService.java`**
  - `isActiveEventMembership`: "active" += `SETUP` (OPEN/SETUP/IN_PROGRESS).

### 5.4 Controller
- **`controller/TeamController.java`**
  - `POST /api/teams/event/{eventId}/draw-tracks?includeAssigned=false` (EVENT_COORDINATOR).
  - `PUT  /api/teams/{teamId}/track` body `SelectTrackRequest` (PARTICIPANT — leader).

### 5.5 Database scripts
- **`database scripts/seal_schema.sql`**
  - `HackathonEvent.status` comment += SETUP; **thêm cột** `track_selection_mode VARCHAR(20) NOT NULL DEFAULT 'SELF_SELECT'`.
  - `Track`: **thêm cột** `capacity INT NULL`.
  - `Team.track_id` → **nullable**.
- **`database scripts/seal_seed.sql`**
  - **Toàn bộ team event 2 (team 6–13) `track_id = NULL`** (gộp 1 block) để demo trọn luồng.
  - Thêm team 11–13 (Comet/Vortex/Lumen) + TeamMember (user 8,9,10,14,15 — rảnh ở event 2).
  - Mix APPROVED/PENDING (5 APPROVED / 3 PENDING) để demo bước duyệt.
  - Sửa comment sai "EVENT 2 (IN_PROGRESS)" → OPEN.
- **`database scripts/migration_setup_status_and_track_draw.sql`** — đã tạo rồi **xoá** (người dùng chạy lại schema+seed nên không cần).

---

## 6. Hợp đồng API mới / thay đổi

| Method & path | Role | Mô tả |
|---|---|---|
| `POST /api/teams/event/{eventId}/draw-tracks?includeAssigned=` | COORDINATOR | Bốc thăm xếp team APPROVED vào track (chỉ khi SETUP). `false`=chỉ team chưa có track; `true`=xáo lại tất cả. Trả list team đã gán. |
| `PUT /api/teams/{teamId}/track` body `{trackId}` | PARTICIPANT (leader) | SELF_SELECT: leader chọn track (chỉ khi SETUP, team APPROVED, track chưa đầy). Trả `MyTeamResponse`. |
| `PUT /api/events/{eventId}` body `{trackSelectionMode}` | COORDINATOR | Đổi mode (chỉ khi DRAFT/OPEN). |
| `PUT /api/events/{eventId}` body `{status: "SETUP"}` | COORDINATOR | Đóng đơn → tính capacity tự động. |
| `GET /api/teams/my` | PARTICIPANT | Nay trả thêm `eventStatus`, `trackSelectionMode`. |
| `GET /api/events/{eventId}/tracks` | * | Track nay có `capacity`. |

---

## 7. Kiểm thử / verify

- `mvnw -o compile` → **BUILD SUCCESS** (nhiều lần trong phiên, lần cuối sau toàn bộ thay đổi).
- Chỉ còn cảnh báo deprecation ở `JwtAuthenticationFilter` (có sẵn, không liên quan).
- Đã rà data thi đấu trong seed: toàn bộ Submission/Score/RoundResult/Prize thuộc **event 1** →
  set team event 2 về null-track **an toàn**, không vỡ ràng buộc.

### Cách check kết quả gán track
```sql
-- team nào ở track nào
SELECT t.team_id, t.name, t.status, tr.name AS track
FROM Team t LEFT JOIN Track tr ON tr.track_id = t.track_id
WHERE t.event_id = 2 ORDER BY tr.name;

-- đếm team mỗi track (kiểm tra chia đều)
SELECT tr.name, COUNT(t.team_id)
FROM Track tr LEFT JOIN Team t ON t.track_id = tr.track_id AND t.status='APPROVED'
WHERE tr.event_id = 2 GROUP BY tr.track_id;
```
Hoặc `GET /api/teams/event/2` (xem `trackName` từng team).

---

## 8. Luồng demo (chỉ cần chạy lại `seal_schema.sql` + `seal_seed.sql`)

**SELF_SELECT (mặc định):** OPEN → duyệt team → `CLOSE REGISTRATION` (SETUP, slot tự tính) →
leader vào dashboard chọn track (đầy thì bị chặn) → coordinator `DRAW TRACKS` bù → `START EVENT`.

**RANDOM:** lúc OPEN đổi mode = Random → SETUP → `DRAW TRACKS` (chia đều 2/1/1/1) → `START EVENT`.

---

## 9. Việc còn lại / lưu ý

- Đổi mode bị khoá sau SETUP (cố ý). Nếu cần đổi, đưa event về OPEN trước.
- Vào SETUP nếu event **chưa có track** → backend trả 400 (phải tạo track trước).
- Khi `SETUP → OPEN` (reopen) hiện **không** xoá capacity/track đã gán; vào SETUP lại sẽ tính đè capacity.
- Status/mode vẫn là String + VARCHAR(20) không CHECK → thêm giá trị mới không cần migrate constraint
  (đổi lại bằng app-level validation).
