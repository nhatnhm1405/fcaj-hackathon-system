# AI Log — Mentor "My Tracks" event-scoping, round status & dashboard sync

**Date:** 2026-07-05
**Branch:** `TrangNHK-mentor-event-scoping` (off `TrangNHK-landing-scroll-animations`)
**Role/area:** Mentor console (FE) + `AssignmentService` (BE)
**Author:** Trang (KTrangg)

---

## 1. Bối cảnh & vấn đề

Mentor console hiển thị **sai/gây rối** phần "các track/team mình quản lý":

- `GET /api/mentor/assignments` (`AssignmentService.getMentorAssignments`) dùng
  `MentorAssignmentRepository.findActiveByMentor` — **không scope theo event**. Nó gom
  hết mọi track mentor từng quản lý ở **tất cả các mùa** (Spring, Summer, …) rồi flatMap
  teams ra một danh sách phẳng. Field `eventName` cấp trên cùng chỉ lấy event của
  assignment đầu tiên → hiển thị sai mùa.
- FE `MentorTracksPage.tsx` gom danh sách phẳng đó theo `trackName` thành các `PixelTabs`
  → track của nhiều mùa nằm chung một hàng tab, không có ranh giới event.

Đây chính là lỗi cross-event đã được ghi nhận trước đó và **defer từ 2026-06-18**; lần này
xử lý dứt điểm.

Yêu cầu người dùng:
1. Hiển thị track/team mà mentor quản lý **theo từng event**.
2. Hiển thị team thuộc track theo **format bảng** giống bên role Coordinator.
3. (bổ sung trong phiên) Đồng bộ luôn **MentorDashboard** theo event đang active.
4. (bổ sung) Thêm thông tin **team đang ở round nào** (advancement) vào My Tracks.
5. (bổ sung) Hiện đủ **event/track kể cả khi chưa có team** — nhưng **không** đụng
   schema / seed / mysql script.
6. (bổ sung) Card đầu dashboard hiển thị **tên track / event** thay vì con số, với
   thẩm mỹ màu sắc hợp lý.

---

## 2. Quyết định thiết kế (Q&A với người dùng)

| Câu hỏi | Lựa chọn chốt |
|---|---|
| Phạm vi sửa để nhóm theo event | **Backend + FE**: BE trả kèm event info theo từng team |
| Bố cục hiển thị | **Dropdown chọn Event** (mặc định event đang active), giống Coordinator |
| Cột bảng team | **Cột cơ bản** giống `CoordTeamsPage`: Team / Leader / Members / Submission (+ Round bổ sung sau) |
| Vị trí nút Announce khi có nhiều track | **Theo từng track** (mỗi bảng track một nút + lịch sử riêng) |
| Event/track chưa có team | **Hiện tất cả** (phương án 2), không sửa schema/seed/SQL |

Định nghĩa **"team đang ở round nào"**: đi qua các round của event theo thứ tự —
- round chưa `FINALIZED` → đó là round hiện tại;
- round đã `FINALIZED`, không có cut-off (`topNAdvance` null) → mọi team qua vòng;
- round đã `FINALIZED`, có cut-off → qua nếu `rankPosition <= topNAdvance`, nếu không thì
  dừng tại round đó và đánh dấu **eliminated**.

---

## 3. Thay đổi Backend

### `dto/response/MentorAssignmentResponse.java`
- `AssignedTeamInfo` thêm: `eventId`, `eventName`, `season`, `year`, `eventStatus`,
  `currentRoundName`, `eliminated`.
- Thêm list mới `tracks` (`AssignedTrackInfo`: `trackId`, `trackName`, `eventId`,
  `eventName`, `season`, `year`, `eventStatus`) — chứa **mọi track được phân công kể cả
  track rỗng**, để FE liệt kê đủ event/track trong dropdown.
- `eventName` cấp trên **giữ nguyên** để `MentorDashboard` cũ không vỡ.

### `service/AssignmentService.java`
- Viết lại thân `getMentorAssignments`: đổi từ nested stream sang **vòng lặp** để:
  - cache `rounds` theo `eventId` (`Map<Integer, List<Round>>`) → tránh N+1;
  - build `trackInfos` (dedupe theo `trackId` bằng `HashSet`), **độc lập với việc team có
    tồn tại hay không** → surface các dòng `MentorAssignment` vốn đã có trong DB;
  - với mỗi team, load `RoundResult` (`findAllByTeamIdOrderByRoundOrder`) map theo
    `roundId`, rồi tính round hiện tại qua helper.
- Thêm helper `resolveCurrentRound(orderedRounds, resultByRound)` trả về
  `TeamRoundStatus { roundName, eliminated }` theo logic ở mục 2.
- Import thêm: `RoundResult`, `HashMap`, `HashSet`.
- **Không** đổi query repository, entity, schema, seed hay SQL — chỉ đọc dữ liệu sẵn có.

---

## 4. Thay đổi Frontend

### `shared/apiClient.ts`
- `MentorAssignedTeam` thêm: `eventId`, `eventName`, `season?`, `year?`, `eventStatus?`,
  `currentRoundName?`, `eliminated?`.
- Thêm interface `MentorAssignedTrack` và field `tracks?: MentorAssignedTrack[]` trong
  `MentorAssignment`.

### `features/tracks/MentorTracksPage.tsx` (viết lại)
- **Dropdown Event** ở đầu trang. Danh sách event suy ra từ `tracks` (fallback: tổng hợp
  từ `teams` nếu backend cũ). Mặc định chọn event `IN_PROGRESS` → `OPEN` → mới nhất
  (`pickDefaultEvent`).
- Mỗi track trong event đã chọn là một **section** (`TrackSection`):
  - header: tên track + số team + `x/y submitted` + nút **`ANNOUNCE TO {track}`** riêng;
  - **`<table>`** cột `Team | Leader | Members | Round | Submission`, click hàng để mở
    panel chi tiết (members + trạng thái nộp) — mirror `CoordTeamsPage`;
  - cột **Round**: badge cyan tên vòng hiện tại, hoặc badge đỏ `ELIMINATED` + tên vòng bị
    loại, hoặc `—`;
  - **Sent announcements** của track hiển thị gọn dưới mỗi section.
- Track rỗng vẫn hiện section (bảng "No teams in this track.", nút Announce disabled).

### `features/dashboard/dashboards/MentorDashboard.tsx`
- Scope theo event đang active (suy từ `tracks`, fallback `teams`): stats *Teams*,
  *Teams Submitted*, danh sách track, header "Supporting teams in …" và contest timer đều
  dùng đúng event hiện tại thay vì gộp tất cả mùa.
- Danh sách track & đếm bao gồm cả **track rỗng**.
- Card đầu đổi từ con số → **`MentorContextCard`**: hiển thị **EVENT** và **TRACK(s)** ngang
  hàng, mỗi trường có nhãn + chấm dẫn màu. Khung **tím**, EVENT màu tím (`#a78bfa`), TRACK
  màu xanh dương sáng (`#60a5fa`) — đồng bộ dải màu lạnh với 2 card blue/cyan bên cạnh
  (đã qua vài vòng chỉnh: số → tên track → tên event nổi bật → 2 trường rõ ràng → 2 màu).

---

## 5. Kiểm thử

- **Backend:** `./mvnw -q -o compile` → exit 0 (nhiều lần trong phiên).
- **Frontend:** `npx tsc -p tsconfig.app.json --noEmit` → các file đã sửa
  (`MentorTracksPage`, `MentorDashboard`, `apiClient`) **zero type-error**. (Còn 2 lỗi có
  sẵn ở `TeamViewPage.tsx` — ngoài phạm vi phiên này, không đụng tới.)
- Xác nhận trực quan bằng screenshot: mentor Tran Van An (Summer 2026, track AI Solution,
  0 team) — Summer hiển thị đúng dù chưa có team.

## 6. Ghi chú / follow-up

- Với team chưa bị loại nhưng round đầu **chưa finalized**, "round hiện tại" = round đầu
  (đúng — mọi team đang thi ở đó). Có thể tách "Chưa có kết quả" vs "Đang thi Vòng 1" nếu
  cần chi tiết hơn.
- Cặp màu card ngữ cảnh nằm ở 2 biến `EVENT_COLOR` / `TRACK_COLOR` — đổi nhanh nếu muốn
  bảng màu khác.

## 7. Files changed

```
back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/response/MentorAssignmentResponse.java
back-end/src/seal-api/src/main/java/com/seal/hackathon/service/AssignmentService.java
front-end/src/seal-web/src/shared/apiClient.ts
front-end/src/seal-web/src/features/tracks/MentorTracksPage.tsx
front-end/src/seal-web/src/features/dashboard/dashboards/MentorDashboard.tsx
front-end/AI logs/2026-07-05_mentor-tracks-event-scoping-round-status.md  (this file)
```
