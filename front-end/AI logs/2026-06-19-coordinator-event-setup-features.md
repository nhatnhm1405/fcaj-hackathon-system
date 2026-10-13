# AI Log — Màn hình Event (Admin & Coordinator)

**Ngày:** 2026-06-18 → 2026-06-19
**Phạm vi:** Frontend React (`front-end/src/seal-web`) + Backend Spring Boot (`back-end/src/seal-api`)
**Nhánh:** `develop`
**Trạng thái:** Hoàn tất, đã verify (FE: tsc sạch · vitest 28/28 · vite build OK — BE: 171/171 test, BUILD SUCCESS)

> Tài liệu này ghi lại 3 nhóm việc đã làm trong phiên, giải thích **làm gì / vì sao / làm thế nào**, kèm danh sách file và cách kiểm chứng. Mục tiêu để người đọc sau (hoặc chính bạn) hiểu nhanh thay đổi mà không cần đọc lại toàn bộ diff.

---

## Bối cảnh hệ thống (tóm tắt)

- **Vòng đời event:** `DRAFT → OPEN → SETUP → IN_PROGRESS → COMPLETED` (+`CANCELLED`). Định nghĩa ở FE `features/events/eventUtils.tsx` (`EventStatus`) và BE `HackathonEventService` (`TRANSITIONS`).
- **SETUP = đóng đăng ký, chốt danh sách đội, xếp đội vào track.** Khi **vào** SETUP, BE `computeTrackCapacities()` đóng băng roster và tính `capacity` mỗi track (chia đều floor/floor+1 trên số đội **APPROVED**).
- **Chế độ phân track (`trackSelectionMode`):** `SELF_SELECT` (leader tự chọn) hoặc `RANDOM` (coordinator bốc).
- **Quan hệ đội↔track:** `Team.trackId` (null = chưa xếp / "Unassigned"). Lấy đội theo event: `GET /api/teams/event/{eventId}` (coordinator).
- **Phân quyền 2 tầng:** `SYSTEM_ADMIN` vận hành platform (tạo/hoàn tất/mở lại event); `EVENT_COORDINATOR` vận hành từng event (track, round, criteria, chấm điểm).
- **"Roster" dùng cho mọi tính toán = đội APPROVED** (nhất quán với cách BE đóng băng capacity).

---

## TASK 1 — Màn hình Admin: gọn form tạo event + đổi tiêu đề notification

### Làm gì
1. Bỏ phần **Tracks** và **Rounds** khỏi form "Create Event" của Admin — vì việc set-up track/round thuộc về Event Coordinator (giai đoạn SETUP), không phải Admin.
2. Đổi tiêu đề dropdown thông báo từ `// notifications` (kiểu comment) thành **`Notifications`** (tiêu đề thật), giữ hậu tố `· N unread`.

### Vì sao
- Admin chỉ nên: **tạo event**, **COMPLETE**, **REOPEN**, và duyệt reopen-request. Track/Round là cấu hình cuộc thi do Coordinator quản lý ở `CoordEventsPage.tsx` (đã có sẵn đầy đủ) → bỏ khỏi Admin không mất chức năng, chỉ đặt đúng vai trò.
- Trường **Track Assignment** (Teams self-select / Random draw) **giữ lại** ở Admin vì đó là metadata cấu hình event, không phải "tạo track cụ thể".

### Files
| File | Thay đổi |
|------|----------|
| `front-end/.../features/events/AdminEventsPage.tsx` | Xóa state nháp track/round, helper `addDraftTrack/...`, 2 vòng lặp POST `/tracks` + `/rounds`, và 2 khối UI Tracks/Rounds. Thêm 1 dòng ghi chú "Tracks and rounds are set up by the Event Coordinator…". |
| `front-end/.../app/layouts/DashboardLayout.tsx` | `// notifications` → `Notifications` (font 13px, bold). |

---

## TASK 2 — Màn hình Coordinator: số liệu track + danh sách đội + khóa tạo track ở SETUP

### NGHIỆP VỤ 1 — Số liệu trực quan trên track
- Dải **Overview** đầu tab Tracks (hiện **từ SETUP trở đi**): `Total teams`, `Max / track`; thêm `Assigned` + `Unassigned` cho chế độ **SELF_SELECT**.
- **Công thức (pure function):** `maxTeamsPerTrack(total, trackCount) = trackCount<=0 ? 0 : Math.ceil(total/trackCount)` — `Math.ceil` (làm tròn lên), guard chia 0.

### NGHIỆP VỤ 2 — Danh sách đội trong track
- Dưới mỗi track: danh sách đội thuộc track (tên + số thành viên + leader), kèm empty state *"No teams in this track yet"*.
- Nhóm **"Unassigned teams"** cho đội APPROVED chưa có track.

### NGHIỆP VỤ 3 — Khóa tạo track trong SETUP
- **FE:** form/nút ADD track chỉ hiện ở DRAFT/OPEN; ở SETUP+ thay bằng thông báo lý do.
- **BE:** `TrackService` tách `TRACK_CREATE_ALLOWED_EVENT_STATUSES = {DRAFT, OPEN}` cho `createTrack` (update/delete vẫn cho ở SETUP). Request tạo track ở SETUP → `BadRequestException`.
  - *Bonus:* vá bug tiềm ẩn — track thêm giữa SETUP sẽ có `capacity = null` → `drawTracks` coi như vô hạn. Khóa tạo track ở SETUP giúp set track ổn định sau khi capacity đã đóng băng.

### Quyết định (đã hỏi & chốt)
- Roster = **chỉ đội APPROVED**.
- Số liệu hiện **từ SETUP trở đi** (roster đã đóng băng ngay khi vào SETUP).
- Self-select: có **nhóm Unassigned + danh sách**.

### Files
| File | Thay đổi |
|------|----------|
| `front-end/.../features/events/trackStats.ts` *(mới)* | `maxTeamsPerTrack`, `countAssigned`, `countUnassigned`, `teamsForTrack`. |
| `front-end/.../features/events/trackStats.test.ts` *(mới)* | Test ceil (chia hết/không chia hết), 0 track, 0 team, assigned/unassigned. |
| `front-end/.../features/events/CoordEventsPage.tsx` | Fetch teams (effect riêng + loading/error); Overview; danh sách đội/track; nhóm Unassigned; khóa form ADD track. |
| `back-end/.../service/TrackService.java` | Tách quy tắc cho phép tạo track. |
| `back-end/.../service/TrackServiceTest.java` | Thêm case create ở SETUP bị chặn + OPEN vẫn được. |

---

## TASK 3 — SETUP: ẩn helper text, quy tắc ≥2 đội, dọn track, kéo-thả, chặn hoàn tất

Đây là feature lớn nhất, gồm 5 phần.

### PHẦN 1 — Ẩn helper text khi vào SETUP
- Trong SETUP, **không render** dòng *"Track creation is locked during SETUP — the track list is final once registration closes. You can still rename or remove empty tracks."* (các phase khác giữ message generic).
- *Giả định:* PHẦN 1 thuần frontend (chỉ là chuỗi hiển thị).

### PHẦN 2 — Quy tắc track tối thiểu 2 đội
- Track có **< 2 đội APPROVED là KHÔNG hợp lệ** → viền đỏ + badge số `TEAMS` màu đỏ + badge `⚠ MIN 2`.
- Pure function `isTrackValid(teamCount) = teamCount >= MIN_TEAMS_PER_TRACK (=2)`.

### PHẦN 3 — Dọn track thủ công
- Nút **REMOVE** trên **mọi track** (trong SETUP).
- Bấm REMOVE → **pop-up xác nhận** (`ConfirmDialog`) hiển thị **preview**: tên track sẽ xóa + danh sách đội sẽ chuyển về **Unassigned**. Có Xác nhận / Hủy; chỉ thực thi khi Xác nhận.
- Xóa track **KHÔNG** tự phân bổ đội sang track khác — đội về pool Unassigned, Coordinator tự kéo lại.
- **BE:** `TrackService.deleteTrack` đổi từ "chặn nếu có đội" → **dời mọi đội của track về `track=null` rồi xóa** (trong SETUP). DRAFT/OPEN là no-op vì lúc đó chưa đội nào có track.

### PHẦN 4 — Kéo-thả đội vào track & cảnh báo vượt max
- Dùng **react-dnd 16 + HTML5Backend** (đã có sẵn trong deps, trước đó chưa dùng — **không thêm thư viện mới**).
- Trong SETUP: hàng đội **draggable**; mỗi track + pool **Unassigned** là **drop target** (cho **cả 2 chế độ** SELF_SELECT và RANDOM).
- Thả làm vượt `max = ceil(tổng đội APPROVED / số track hiện tại)` → **pop-up CẢNH BÁO** *"ASSIGN ANYWAY"* — **vẫn cho phép** vượt, hoặc Hủy.
- Pure function `wouldExceedMax(currentCount, max) = max<=0 ? false : currentCount+1 > max`.
- **BE:** endpoint mới `PUT /api/teams/{teamId}/track-assignment` (coordinator) → `TeamService.assignTeamToTrack`:
  - SETUP-only, chỉ đội APPROVED.
  - **KHÔNG hard-cap capacity** (khác `selectTrack` của participant) — cho phép vượt max có chủ đích; cảnh báo là "soft" ở FE.
  - `trackId = null` ⇒ chuyển đội về Unassigned. Ghi audit `ASSIGN_TEAM_TRACK`.

### PHẦN 5 — Chặn hoàn tất khi chưa hợp lệ (validation gate)
- **Không cho rời SETUP về phía trước** (nút **START EVENT** = `SETUP → IN_PROGRESS`) nếu:
  (a) còn track < 2 đội, **hoặc** (b) còn đội chưa xếp.
- UX: nút vẫn bấm được → mở **dialog liệt kê lý do** (track nào thiếu, số đội chưa xếp), không gọi API.
- Pure function `canCompleteSetup({tracks:[{trackId,name,teamCount}], unassignedCount}) → {ok, invalidTracks, reasons[]}`.
- **BE enforce song song:** `HackathonEventService.requireSetupComplete()` chạy khi transition `SETUP → IN_PROGRESS`; fail → `BadRequestException` kèm danh sách vấn đề. Validate **trước** khi đổi status (fail thì không thay đổi gì).
- *Giả định:* "Hoàn tất" = **START EVENT**; REOPEN REGISTRATION (lùi về OPEN) và CANCEL **không** bị chặn. Gate chỉ tính trên đội APPROVED.

### Quyết định (đã hỏi & chốt)
- REMOVE hiển thị trên **mọi track** trong SETUP.
- Kéo-thả áp dụng **cả SELF_SELECT và RANDOM**.
- START EVENT bị chặn → **vẫn cho bấm, hiện dialog lý do**.

### Files
**Frontend**
| File | Thay đổi |
|------|----------|
| `features/events/trackStats.ts` | Thêm `isTrackValid`, `wouldExceedMax`, `canCompleteSetup`, `MIN_TEAMS_PER_TRACK`. |
| `features/events/trackStats.test.ts` | Thêm test cho 3 hàm trên (đủ/thiếu team, còn unassigned, 0 track). |
| `features/events/CoordEventsPage.tsx` | `DndProvider`; `DraggableTeamRow` + `TeamDropZone`; handlers `performAssign`/`onDropTeam`/`requestDeleteTrack`; gate trong `requestStatusChange`; PHẦN 1–4 trong JSX tab Tracks. |
| `shared/apiClient.ts` | `teamsApi.assignTrack(teamId, trackId\|null)`. |

**Backend**
| File | Thay đổi |
|------|----------|
| `repository/TeamRepository.java` | `findAllByTrack_TrackId`. |
| `service/TeamService.java` | `assignTeamToTrack` (SETUP-only, cho vượt max, audit). |
| `controller/TeamController.java` | `PUT /api/teams/{teamId}/track-assignment`. |
| `dto/request/AssignTeamTrackRequest.java` *(mới)* | `trackId` nullable. |
| `service/TrackService.java` | `deleteTrack` → unassign rồi xóa. |
| `service/HackathonEventService.java` | `requireSetupComplete` gate. |
| `service/TeamServiceTest.java` | +mock `AuditLogService`; 7 test `assignTeamToTrack`. |
| `service/TrackServiceTest.java` | Sửa case delete-has-teams → unassign+delete. |
| `service/HackathonEventServiceTest.java` *(mới)* | 4 test gate SETUP→IN_PROGRESS. |

---

## Các điểm thiết kế đáng nhớ

1. **FE chỉ là lớp tiện ích — BE là cổng kiểm soát thật.** Mọi ràng buộc nghiệp vụ (khóa tạo track ở SETUP, gate hoàn tất, dời đội khi xóa track) đều **enforce ở backend**, FE chỉ ẩn/disable + giải thích.
2. **`max/track` tính LIVE** theo số track hiện tại — xóa 1 track thì max tăng (vì `số track` giảm).
3. **`capacity` (X SLOTS) ≠ `max/track`.** `capacity` là số chia đều cố định lúc vào SETUP (per-track, lệch nhau ≤1); `max/track` là số `ceil(total/trackCount)` đồng nhất, tính live — dùng làm ngưỡng cảnh báo khi kéo-thả.
4. **Pool Unassigned là drop target ở cả 2 chế độ** trong SETUP — cần thiết để sau khi xóa track (đội rơi về Unassigned ngay cả ở RANDOM) Coordinator kéo lại được.
5. **react-dnd connector không gán trực tiếp vào `ref`** — phải bọc callback: `ref={(node) => { if (enabled) dragRef(node); }}` (lỗi TS2322 nếu gán thẳng).
6. **`Team.trackId` khai trong apiClient là `number` nhưng runtime có thể `null`** → mọi check dùng `== null` (an toàn, đúng idiom đã có sẵn trong codebase).

---

## Kiểm chứng

| Hạng mục | Lệnh | Kết quả |
|----------|------|---------|
| Backend test | `./mvnw test` | **171/171** pass · BUILD SUCCESS · context load OK |
| FE typecheck | `npx tsc -p tsconfig.app.json --noEmit` | Sạch (các file đã sửa; lỗi còn lại là pre-existing ở `DashboardLayout`/`ProfilePage`) |
| FE unit test | `npx vitest run` | **28/28** (trackStats 16, permissions 7, ConfirmDialog 5) |
| FE build | `npx vite build` | Thành công (react-dnd bundle OK) |

> **Lưu ý lỗi TS có sẵn (không phải do phiên này):** `DashboardLayout.tsx` (`avatar_url`, `currentUser` possibly null) và `ProfilePage.tsx` (`studentId`, `university`). Chưa xử lý vì ngoài phạm vi yêu cầu.

## Việc nên kiểm thử thủ công (khó test tự động)
Drag & drop chưa có e2e test. Nên chạy `npm run dev` và thử tay:
1. Xóa 1 track → các đội của nó về **Unassigned** (không tự phân bổ).
2. Kéo đội từ Unassigned vào 1 track hợp lệ.
3. Kéo làm 1 track vượt `Max / track` → hiện cảnh báo, vẫn cho "ASSIGN ANYWAY".
4. Còn track <2 đội hoặc còn đội chưa xếp → bấm **START EVENT** bị chặn, dialog liệt kê lý do.
5. Đủ điều kiện (mọi track ≥2, không còn chưa xếp) → START EVENT thành công.
