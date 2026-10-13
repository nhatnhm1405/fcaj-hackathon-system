# AI Session Log — Round Qualification (Top N), CRUD hoàn thiện, Audit collapse, Announcement popup

**Date:** 2026-06-23 → 2026-06-24
**Project:** SEAL – Software Engineering Hackathon Management System
**Branch:** `develop`
**Scope tổng quát:** Một chuỗi yêu cầu liên tiếp, làm theo kiểu Q&A — duyệt từng phần trước khi code:
1. Cho **coordinator cấu hình số đội đi tiếp (Top N)** mỗi round + trực quan hoá "đường cắt".
2. Rà & hoàn thiện **CRUD cho Track / Round / Criteria**.
3. **Top N theo từng track** cho vòng thường; **Top N gộp toàn vòng (winners)** cho vòng Final.
4. **Audit có nút sổ ra/đóng lại** (Event Audit + Admin System Logs).
5. **Popup announcement** kiểu welcome khi nhận tin nhắn mới.
6. Sửa bug **round CLOSED không mở lại được**.

> **Ràng buộc xuyên suốt do người dùng đặt ra:**
> - Ban đầu: *không được sửa `schema` và `seed` SQL, và không đụng BE*.
> - Về sau nới: *chỉ cấm sửa `schema`/`seed` SQL*; các file BE khác được đụng **nhưng phải báo trước** những file sẽ chạm.
> - Coordinator **chỉ được sửa phạm vi DƯỚI event** (track / round / criteria); **không đụng scope của admin** (tạo/sửa event name/dates).

---

## PHẦN 0 — Lấy context

Tài liệu đọc đầu phiên (theo yêu cầu để tiết kiệm usage):
- `back-end/Postman/Postman_Full_Collection.json` — toàn bộ API.
- `docs/documents/ProjectRequirements.md` — đề SWP391. Mục **6.1 / 6.8 / UC12** nói rõ về quy tắc **thăng vòng top N mỗi hạng mục**.

**Phát hiện then chốt:** backend **đã có sẵn** hạ tầng Top N từ trước:
- `Round.top_n_advance` (cột nullable) + `CreateRoundRequest`/`UpdateRoundRequest` đều nhận `topNAdvance`.
- `RoundResultService.isAdvanced()` suy ra **live**: `advanced = rankPosition <= topNAdvance` (không lưu cứng).
- `RoundResultResponse.advanced` đã trả về FE; `LeaderboardPage`/`CoordScoringPage` đã render badge "Advanced".

⇒ Phần lớn việc là **FE**; logic BE chỉ cần đụng khi đổi cách tính rank (per-track) và thêm CRUD còn thiếu.

---

## PHẦN 1 — Top N config + trực quan hoá (FE-only)

**Vấn đề:** form *tạo* round đã có ô Top N, nhưng sau khi tạo **không sửa được**; `changeRoundStatus` chỉ gửi `{status}`. Trang results chưa thể hiện rõ "đội nào đi tiếp / bị loại".

**Đã làm:**
- `features/events/CoordEventsPage.tsx`
  - Nút **EDIT** trên mỗi round card → mở lại form đầy đủ (prefill Name/Order/Start/End/Deadline/Top N), nút **SAVE/CANCEL** → `PUT /rounds/{id}`.
  - Mở rộng `ApiRound`/`RoundRow`/`normalizeRound` mang thêm `startTime`/`endTime`/`isFinal`.
  - Cảnh báo amber khi round chưa đặt cut-off.
- `features/scoring/CoordScoringPage.tsx` — banner summary "Top N · X/Y advance" + **đường cắt** giữa đậu/rớt + highlight nền xanh + cảnh báo "Top N ≥ số đội".
- `features/scoring/LeaderboardPage.tsx` — tương tự cho view participant (giữ highlight "YOU").

**Quyết định chốt với user:** nút Edit dạng "mở form đầy đủ" (không inline); cảnh báo "Top N ≥ số đội" đặt ở trang Scoring (nơi có sẵn số đội đã rank).

---

## PHẦN 2 — Audit CRUD & sửa Track

Người dùng hỏi *"track chưa có CRUD?"*. Rà soát kết quả:

| Entity | Create | Read | Update | Delete | Ghi chú lúc đó |
|---|:---:|:---:|:---:|:---:|---|
| Event | ✅ | ✅ | ⚠️ chỉ status+mode (FE) | ❌ BE không có | name/dates là **scope admin** |
| Track | ✅ | ✅ | **❌ FE thiếu** | ✅ | BE có `PUT` sẵn |
| Round | ✅ | ✅ | ✅ | ❌ BE không có DELETE | |
| Criteria | ✅ | ✅ | ❌ BE không có | ❌ BE không có | |

**Track — thêm Edit (FE-only):** `CoordEventsPage.tsx` thêm **form edit inline** trong track card (state riêng `etName`/`etDesc`, không đụng form create), gọi `PUT /tracks/{id}`.

---

## PHẦN 3 — Criteria Edit/Delete (đụng BE — đã báo trước)

**File BE đã báo & sửa (không đụng schema/seed):**

| File | Thay đổi |
|---|---|
| `controller/ScoringController.java` | `@PutMapping` + `@DeleteMapping` cho `/events/{eventId}/rounds/{roundId}/criteria/{criteriaId}` (`@PreAuthorize EVENT_COORDINATOR`) |
| `service/ScoringService.java` | `updateCriteria` (partial update) + `deleteCriteria` (guard) + helper `findCriteriaInRound` (xác thực criteria thuộc đúng round+event) |
| `repository/ScoreRepository.java` | `existsByCriteria_CriteriaId(...)` để chặn xoá |

- Tái dùng `CreateCriteriaRequest` cho PUT → **không tạo DTO mới**.
- **Delete bị chặn** nếu criteria đã có điểm chấm → `BadRequestException` (400) với message rõ ràng (lựa chọn của user: "Chặn xoá").

**FE — `CoordEventsPage.tsx` tab Criteria:** mỗi criteria có nút **EDIT** (form inline name/desc/max/weight) + **DELETE** (qua `openConfirm`). Lỗi 400 khi bị chặn được surface vào dialog + toast (`handleConfirmAction` đã catch sẵn).

---

## PHẦN 4 — Top N theo TRACK (vòng thường) vs gộp toàn vòng (Final)

**Câu hỏi nghiệp vụ người dùng nêu:** vòng thi theo track thì "top N đi tiếp" tính kiểu gì? Vòng Final thì sao?

**Phát hiện (lỗ hổng thiết kế):** `finalizeRound` cũ xếp hạng **GLOBAL toàn vòng** rồi lấy top N — kể cả với vòng theo track. ⇒ một track mạnh có thể chiếm hết suất, track khác không có ai qua.

**Quyết định chốt:**
- Vòng **non-final** → **Top N MỖI track** (dùng chung 1 số N, không đụng schema).
- Vòng **Final** → Top N **gộp toàn vòng** (winners).

**Ý tưởng không-đụng-schema:** vì `advanced = rankPosition <= topNAdvance` là **derived**, chỉ cần đổi cách tính `rankPosition` khi finalize:

**BE — `service/RoundResultService.java`, method `finalizeRound`:**
- `round.isFinal == true` → xếp hạng global như cũ.
- ngược lại → **group submissions theo `team.track.trackId`** (null = 1 bucket riêng), sort + đánh `rankPosition` **1..n trong mỗi track**.
- Thêm helper `saveResult(...)` để tránh lặp code.
- ⇒ `advanced` tự động thành "top N mỗi track" mà **không thêm field/cột nào**.

**FE — hiển thị nhóm theo track:**
- `CoordScoringPage.tsx` + `LeaderboardPage.tsx`: dựng `rankGroups` — vòng final = 1 nhóm global; non-final = nhóm theo `trackName`, mỗi nhóm có sub-header + summary + cut-line riêng.
- `CoordEventsPage.tsx`: round card ghi "Top N **per track** advance"; thêm dòng hint dưới form.

---

## PHẦN 5 — Đổi note sang tiếng Anh

Theo yêu cầu, đổi dòng hint tiếng Việt dưới form round sang tiếng Anh. (Hai dòng tiếng Việt ở `CoordJudgesPage.tsx` là **có sẵn từ trước**, không phải của phiên này → giữ nguyên.)

---

## PHẦN 6 — Final round = Top N OVERALL (winners) + sửa hiển thị

Người dùng xác nhận: **Final round vẫn config được số đội đạt giải, tính GỘP tất cả track**. BE đã đúng (final = rank global). **Nhưng FE đang hiển thị nhầm final là "no cut-off"** (do PHẦN 4 coi final = không có cut).

**Đã sửa FE:**
- `CoordScoringPage.tsx` + `LeaderboardPage.tsx`: final round hiện **"Winners: Top N · X of Y win (overall)"** + đường cắt + badge **WINNER** (thay vì ẩn). Cảnh báo no-cutoff / too-large áp dụng cho cả 2 loại.
- `CoordEventsPage.tsx`: round card final ghi "Top N **overall (winners)**"; hint form cập nhật: *"per track for normal rounds, or overall winners for the Final round"*.

---

## PHẦN 7 — Nút round thẳng hàng + Round DELETE (CRUD đủ)

**7.1. Nút thẳng hàng:** action area của round card dùng **slot width cố định**: `[EDIT] [DELETE(72px)] [transition(84px)] [status badge(86px)]` → các nút/badge thẳng cột giữa mọi round, kể cả round không có nút transition.

**7.2. Round DELETE (đụng BE — đã báo & user chốt "chặn khi đã finalize"):**

| File BE | Thay đổi |
|---|---|
| `controller/RoundController.java` | `@DeleteMapping("/{roundId}")` (`@PreAuthorize EVENT_COORDINATOR`) |
| `service/RoundService.java` | inject `SubmissionRepository`/`ScoringCriteriaRepository`/`RoundResultRepository`; `deleteRound(...)` |

`deleteRound` logic:
- Chặn nếu `status == FINALIZED` → 400.
- Chặn nếu round **đã có submission** → 400.
- Nếu an toàn → xoá criteria + results phụ thuộc trước (tránh lỗi FK), rồi xoá round.

**FE:** nút **DELETE** trên round card (ẩn khi FINALIZED / khi đang edit) → `requestDeleteRound` qua `openConfirm`.

⇒ Round CRUD giờ **đủ C/R/U/D**.

---

## PHẦN 8 — Audit sổ ra / đóng lại (collapse)

User chốt: áp dụng cho **cả hai** nơi.

- **Event Audit** (`CoordEventsPage.tsx`, dạng card): mỗi entry mặc định gọn (action + actor + giờ), bấm để xổ `reason`/`metadata`. State `expandedAudit: Record<number, boolean>`. Có nút **EXPAND ALL / COLLAPSE ALL**. Chỉ entry có detail mới hiện chevron ▸/▾.
- **Admin System Logs** (`features/users/AdminSystemLogsPage.tsx`, dạng bảng): thêm cột chevron, hàng bấm để xổ **detail row** full-width; cột Detail bị cắt (`ellipsis`) khi đóng. Import `Fragment`. Cũng có **EXPAND/COLLAPSE ALL**. `colSpan` các hàng trạng thái chỉnh 5→6 do thêm cột chevron.

---

## PHẦN 9 — Popup announcement (kiểu welcome)

**Yêu cầu:** khi coordinator announce, người nhận thấy popup *"You have N messages from X"* giống pop-up Welcome (`OnboardingTour`).

**Lựa chọn của user:** chỉ popup khi có announce **MỚI** đến (không popup lại backlog lúc login).

**Đã làm:**
- File mới `shared/components/AnnouncementSplash.tsx` — splash giữa màn hình kiểu welcome (icon Mail, GradientText "You have N messages", "from X", nút **VIEW MESSAGES** / **DISMISS**).
- `app/providers/NotificationProvider.tsx`:
  - Announcement nhận diện qua việc notification có `from` (senderName chỉ set cho ANNOUNCEMENT).
  - Trong `refresh()`: tách `fresh` thành announcement (gom lại) vs thường (vẫn ra banner như cũ). Sau vòng lặp, nếu có announcement mới → set `announceSplash` (count + label sender: "X", "X and Y", hoặc "X and N others").
  - Baseline lần đầu (login) **không** trigger splash (giữ đúng "chỉ announce mới").
  - Render `<AnnouncementSplash>`; nút VIEW gọi `requestOpenBell()`.

---

## PHẦN 10 — Bugfix: round CLOSED không mở lại được

**Nguyên nhân:** round card chỉ có ACTIVATE (PENDING→ACTIVE) và CLOSE (ACTIVE→CLOSED); CLOSED **không có nút nào** → kẹt.

**Fix (FE-only):** thêm nút **REOPEN** cho round `CLOSED` → `changeRoundStatus(roundId, 'ACTIVE')`. `RoundService.updateRound` set status thẳng, **không validate transition** nên CLOSED→ACTIVE chạy được, không cần đụng BE.

> Round `FINALIZED` vẫn cố ý không có nút mở lại (un-finalize cần xử lý dữ liệu kết quả — để dành nếu user yêu cầu).

---

## TỔNG HỢP FILE THAY ĐỔI

**Backend (KHÔNG đụng `schema`/`seed`):**
- `controller/ScoringController.java` — PUT/DELETE criteria.
- `service/ScoringService.java` — update/delete criteria + helper.
- `repository/ScoreRepository.java` — `existsByCriteria_CriteriaId`.
- `controller/RoundController.java` — DELETE round.
- `service/RoundService.java` — `deleteRound` + 3 repository injection.
- `service/RoundResultService.java` — rank theo track cho vòng non-final + helper `saveResult`.

**Frontend:**
- `features/events/CoordEventsPage.tsx` — round edit/delete + REOPEN + nút thẳng hàng + topN label; track edit/delete; criteria edit/delete; audit collapse.
- `features/scoring/CoordScoringPage.tsx` — nhóm theo track + cut-line + final winners.
- `features/scoring/LeaderboardPage.tsx` — nhóm theo track + cut-line + final winners.
- `features/users/AdminSystemLogsPage.tsx` — bảng log collapse.
- `app/providers/NotificationProvider.tsx` — gom announcement → splash.
- `shared/components/AnnouncementSplash.tsx` — **file mới**.

---

## KIỂM THỬ (chạy sau mỗi mốc)

| Lệnh | Kết quả |
|---|---|
| `mvnw -o compile` (BE) | **exit 0** |
| `npx tsc --noEmit --ignoreDeprecations 6.0` (FE) | **exit 0** |
| `npm test` (vitest) | **28/28 pass** (3 test file) |

> Lưu ý môi trường: `tsc --noEmit` thuần báo lỗi do `tsconfig.json` dùng `baseUrl` (deprecated ở TypeScript 6) → bypass bằng `--ignoreDeprecations 6.0` để typecheck thật sự chạy.

---

## LƯU Ý VẬN HÀNH

- Round **đã finalize trước đây** cần bấm lại **CALCULATE RANKINGS** để rank được tính lại theo track (thay đổi ở PHẦN 4).
- **Postman collection chưa bổ sung** các endpoint mới (PUT/DELETE criteria, DELETE round) — chưa được yêu cầu thêm.
- Toàn bộ phiên **chưa commit** — đang ở branch `develop`.
