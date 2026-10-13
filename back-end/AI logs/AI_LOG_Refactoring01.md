# Nhật Ký Refactor Backend — Đồng Bộ Theo Schema Mới (Ngày 12/06/2026)

Tài liệu này ghi nhận toàn bộ quá trình **refactor backend** (module `src/seal-api`) để đồng bộ với bộ database scripts mới (`seal_schema.sql`, `seal_seed.sql`, `seal_scripts.sql`). Schema đã được thiết kế lại đáng kể (assignment redesign), khiến nhiều entity, repository, service và DTO trong source code bị lệch so với cấu trúc bảng thực tế — do project cấu hình `spring.jpa.hibernate.ddl-auto=none` (DB là nguồn chân lý), các điểm lệch này **không gây lỗi compile** mà sẽ **gây lỗi runtime** khi Hibernate query vào cột/bảng không còn tồn tại.

---

## 0. Bối cảnh: Schema đã thay đổi những gì?

So sánh schema cũ (`seal_hackathon.sql` — 16 bảng) với schema mới (`seal_schema.sql` — 17 tables), có đúng **4 thay đổi cấu trúc** theo CHANGELOG "assignment redesign":

| # | Thay đổi | Lý do thiết kế |
|---|----------|----------------|
| 1 | **Xóa bảng `TeamAssignment`** | Trùng lặp chức năng + sai đơn vị nghiệp vụ: mentor/judge không được gán theo *team* mà theo *track/round*. |
| 2 | **Slim bảng `UserEventRole`**: bỏ 3 cột `track_id`, `round_id`, `judge_type`; thêm `UNIQUE KEY uq_user_role_event (user_id, role_id, event_id)` | Bảng này giờ là **pure N-N**: chỉ trả lời câu hỏi *"ai ĐƯỢC PHÉP giữ role gì trong event nào"*. Phân công công việc cụ thể tách ra bảng riêng. |
| 3 | **Thêm 2 bảng mới `JudgeAssignment` + `MentorAssignment`** | `JudgeAssignment`: judge chấm **round + track** (track NULL = chấm tất cả ở vòng Final). `MentorAssignment`: mentor hỗ trợ **track** (cả event, không theo round). |
| 4 | **Thêm cột `Round.is_final`** (BOOLEAN, default FALSE) | Phân biệt vòng Final (judge chấm tất cả team, không chia track) với vòng loại (judge chấm theo track). Schema yêu cầu rõ: *"Service layer must enforce: round.is_final = FALSE → track_id REQUIRED; round.is_final = TRUE → track_id must be NULL"*. |

Ngoài ra `seal_seed.sql` được gộp lại từ 3 file seed cũ, đánh số lại event 1–4 và bổ sung dữ liệu mẫu cho 2 bảng mới (mục 6b `JudgeAssignment`, 6c `MentorAssignment`) — trong đó **1 judge có nhiều dòng JudgeAssignment** (ví dụ Thầy Binh chấm track Web ở cả 3 round). Chi tiết này ảnh hưởng trực tiếp đến logic chống trùng lặp khi gán role (xem mục 4.2).

---

## 1. Xóa toàn bộ module `TeamAssignment` (bảng đã bị drop)

### Vấn đề
Backend còn nguyên 4 file map vào bảng `TeamAssignment` đã không còn tồn tại — mọi request tới `GET /api/mentor/assignments` hoặc `GET /api/judge/assignments` sẽ ném SQL error `Table 'seal_hackathon.TeamAssignment' doesn't exist` ngay lần query đầu tiên.

### Các file đã XÓA
* **[DELETE] `entity/TeamAssignment.java`** — entity map bảng `TeamAssignment` (team_id, user_id, assignment_type 'MENTOR'/'JUDGE', is_active…).
* **[DELETE] `repository/TeamAssignmentRepository.java`** — chứa query `findActiveAssignmentsByUserAndType` (JOIN FETCH team → event/track).
* **[DELETE] `service/TeamAssignmentService.java`** — build response từ danh sách team được gán trực tiếp.
* **[DELETE] `controller/TeamAssignmentController.java`** — host 2 endpoint mentor/judge assignments.

---

## 2. Tạo mới 2 entity + 2 repository cho bảng `JudgeAssignment` / `MentorAssignment`

### 2.1. [NEW] `entity/JudgeAssignment.java`
Map đúng DDL bảng `JudgeAssignment`:
* `id` (PK, auto increment).
* `judge` — `@ManyToOne User` qua cột `judge_user_id` (NOT NULL).
* `round` — `@ManyToOne Round` qua cột `round_id` (NOT NULL).
* `track` — `@ManyToOne Track` qua cột `track_id`, **nullable** (NULL = chấm tất cả track ở vòng Final).
* `judgeType` — `judge_type` VARCHAR(20) NOT NULL (`INTERNAL` | `GUEST`). Lưu ý: trường này **chuyển từ UserEventRole sang đây** theo schema mới.
* `assignedAt` (`@PrePersist` tự set), `assignedBy` (Integer — user_id coordinator), `isActive` (default `true`).
* Khai báo `@UniqueConstraint uq_judge_round_track (judge_user_id, round_id, track_id)` khớp DDL.

### 2.2. [NEW] `entity/MentorAssignment.java`
Map đúng DDL bảng `MentorAssignment`:
* `id` (PK), `mentor` — `@ManyToOne User` qua `mentor_user_id`, `track` — `@ManyToOne Track` qua `track_id` (NOT NULL — mentor luôn gán theo track).
* `assignedAt`, `assignedBy`, `isActive` (default `true`).
* `@UniqueConstraint uq_mentor_track (mentor_user_id, track_id)`.

### 2.3. [NEW] `repository/JudgeAssignmentRepository.java`
* `findActiveByJudge(judgeUserId)` — JPQL `JOIN FETCH ja.round r JOIN FETCH r.event e LEFT JOIN FETCH ja.track t` để load đủ Round/Event/Track trong **1 query** (tránh N+1, giữ đúng pattern tối ưu của repo cũ). Track phải `LEFT JOIN FETCH` vì có thể NULL (vòng Final).
* 2 method kiểm tra trùng theo unique key: `existsByJudge_UserIdAndRound_RoundIdAndTrack_TrackId(...)` và `existsByJudge_UserIdAndRound_RoundIdAndTrackIsNull(...)` (cần tách riêng vì track nullable).
* `findAllByRound_RoundIdAndIsActiveTrue(roundId)` — phục vụ tra cứu phân công theo round.

### 2.4. [NEW] `repository/MentorAssignmentRepository.java`
* `findActiveByMentor(mentorUserId)` — JPQL `JOIN FETCH ma.track t JOIN FETCH t.event e`.
* `existsByMentor_UserIdAndTrack_TrackId(...)` — duplicate check theo unique key.
* `findAllByTrack_TrackIdAndIsActiveTrue(trackId)`.

---

## 3. Viết lại API phân công Mentor / Judge theo mô hình mới

### 3.1. [NEW] `service/AssignmentService.java` (thay thế `TeamAssignmentService`)
**Giữ nguyên contract API** (URL + shape JSON của `MentorAssignmentResponse` / `JudgeAssignmentResponse`), nhưng **đổi hoàn toàn nguồn dữ liệu và ngữ nghĩa**:

| | Logic cũ (bảng TeamAssignment) | Logic mới (schema redesign) |
|---|---|---|
| Mentor | Lấy danh sách **team được gán trực tiếp** cho mentor | Lấy các **track** trong `MentorAssignment` (is_active = true) → trả về **các team APPROVED thuộc track đó** |
| Judge | Lấy danh sách team được gán trực tiếp, kèm `roundId` lưu trên dòng assignment | Lấy các dòng `JudgeAssignment` (round + track) → nếu `track != NULL`: team APPROVED **trong track**; nếu `track == NULL` (vòng Final): **toàn bộ team APPROVED của event** |

Chi tiết triển khai:
* Mỗi dòng assignment được `flatMap` thành nhiều `AssignedTeamInfo` (1 track có nhiều team; 1 judge chấm nhiều round → mỗi round là 1 nhóm team với `roundId` tương ứng).
* Chỉ lấy team `status = 'APPROVED'` — team PENDING/REJECTED/DISQUALIFIED không thuộc diện được mentor hỗ trợ hay judge chấm.
* Thành viên team vẫn load qua `TeamMemberRepository.findByTeam_TeamId(...)` như cũ (LEADER/MEMBER từ `TeamMember.member_role`).
* `eventName` lấy từ `track.event` (mentor) hoặc `round.event` (judge) — quan hệ đã được fetch sẵn trong repository.

### 3.2. [NEW] `controller/AssignmentController.java` (thay thế `TeamAssignmentController`)
* Giữ nguyên 2 endpoint và phân quyền:
  * `GET /api/mentor/assignments` — `@PreAuthorize("hasRole('MENTOR')")`
  * `GET /api/judge/assignments` — `@PreAuthorize("hasRole('JUDGE')")`
* Chỉ đổi tên class và inject `AssignmentService` mới. Client (Postman collection / frontend) **không cần đổi gì**.

### 3.3. [MODIFY] `repository/TeamRepository.java`
* Bổ sung `List<Team> findAllByTrack_TrackIdAndStatus(Integer trackId, String status)` — query nền tảng cho cả 2 luồng trên (lấy team theo track). Luồng vòng Final tái sử dụng `findAllByEvent_EventIdAndStatus(...)` có sẵn.

---

## 4. Refactor `UserEventRole` và toàn bộ luồng gán role

### 4.1. [MODIFY] `entity/UserEventRole.java`
* **Xóa 3 field** không còn cột tương ứng: `trackId`, `roundId`, `judgeType` — nếu giữ lại, mọi câu INSERT/SELECT của Hibernate vào bảng này đều fail (`Unknown column 'track_id'`).
* Thêm `@UniqueConstraint uq_user_role_event (user_id, role_id, event_id)` khớp DDL mới.
* Cập nhật Javadoc: bảng này giờ là pure N-N quyền role; phân công cụ thể nằm ở `JudgeAssignment` / `MentorAssignment`.

### 4.2. [MODIFY] `service/UserRoleService.java` — thiết kế lại luồng nghiệp vụ
Đây là thay đổi nghiệp vụ quan trọng nhất. Nguyên tắc mới: **tách "cấp quyền role" khỏi "phân công công việc"**, đúng tinh thần schema.

**`createStaffAccount(...)` (POST /api/users/staff):**
* Vẫn tạo user STAFF pre-approved như cũ.
* `UserEventRole` giờ chỉ ghi `(user, role, eventId, assignedBy)` — không còn nhét track/round/judgeType vào.
* Bỏ validation cũ "JUDGE bắt buộc phải có judgeType ngay khi tạo account" — vì judgeType giờ thuộc về JudgeAssignment, chỉ bắt buộc khi thực sự phân công chấm (có `roundId`).
* Sau khi cấp role, gọi helper `createWorkAssignmentIfRequested(...)` (mô tả bên dưới) để tạo phân công cụ thể nếu request có kèm scope.

**`assignRole(...)` (POST /api/users/{id}/roles):**
* Giữ duplicate-check role theo scope `(user, role, eventId)` như cũ, **nhưng nới logic**: nếu role đã tồn tại **và** request có kèm work assignment (`roundId`/`trackId`) thì **không ném lỗi** — chỉ bỏ qua bước cấp role và tạo thêm assignment mới. Lý do: theo seed data mới, 1 judge được gán nhiều round (Thầy Binh có 3 dòng JudgeAssignment cho round 1/2/3) — nếu giữ logic cũ, coordinator không thể gán judge cho round thứ hai trở đi.
* Chỉ ném `BadRequestException "User already has role … at this scope"` khi đây là **pure role grant** bị trùng.

**[NEW] helper `createWorkAssignmentIfRequested(...)`** — nơi enforce toàn bộ rule mà schema giao cho service layer:
* **JUDGE + `roundId`:**
  1. Round phải tồn tại; nếu request có `eventId` thì round phải thuộc đúng event đó.
  2. `judgeType` bắt buộc, normalize uppercase, chỉ chấp nhận `INTERNAL` | `GUEST` (cột NOT NULL trong DB).
  3. **Enforce rule `is_final`:**
     * `round.isFinal == TRUE` → `trackId` **phải NULL** (judge chấm tất cả team) — nếu client gửi trackId thì trả 400.
     * `round.isFinal == FALSE` → `trackId` **bắt buộc**; track phải tồn tại và thuộc cùng event với round.
  4. Duplicate check theo unique key `(judge, round, track)` — dùng đúng 1 trong 2 method exists tùy track NULL hay không — rồi mới `save` JudgeAssignment.
* **MENTOR + `trackId`:** track phải tồn tại (và khớp `eventId` nếu có); duplicate check `(mentor, track)`; `save` MentorAssignment.
* **Không có scope** (chỉ `roleName` + `eventId`): chỉ cấp role — judge/mentor có thể được phân công sau. EVENT_COORDINATOR không có bảng work assignment nên không làm gì thêm.

**`getAllStaffRoleAssignments()` (GET /api/users/roles):**
* Bỏ toàn bộ phần resolve `trackNames` / `roundNames` / `judgeType` (đọc từ các field đã xóa). Giờ chỉ resolve tên event + tên người gán (`assignedBy`) bằng 2 query batch `findAllById` như pattern cũ.

### 4.3. [MODIFY] `dto/response/UserEventRoleResponse.java`
* Xóa 5 field: `trackId`, `trackName`, `roundId`, `roundName`, `judgeType`. Response giờ phản ánh đúng 1 dòng role grant: user + role + event + assignedAt + assignedBy.

### 4.4. [MODIFY] `dto/request/AssignRoleRequest.java` + `dto/request/CreateStaffRequest.java`
* **Giữ nguyên field** `eventId` / `trackId` / `roundId` / `judgeType` để **không phá vỡ API contract** với client hiện có.
* Cập nhật lại toàn bộ comment mô tả ngữ nghĩa mới: các field này không còn lưu vào `UserEventRole` mà là **đầu vào để tạo `JudgeAssignment` / `MentorAssignment`** trong cùng 1 bước.

### 4.5. [MODIFY] `controller/UserController.java`
* Cập nhật Javadoc 2 endpoint cho khớp hành vi mới; ví dụ request mẫu của `POST /api/users/{id}/roles` bổ sung `trackId` (vì gán judge vào round thường giờ **bắt buộc** có track).

---

## 5. Bổ sung `Round.is_final` xuyên suốt luồng Round

### 5.1. [MODIFY] `entity/Round.java`
* Thêm field `isFinal` map cột `is_final` (`@Builder.Default = false`, NOT NULL) — thiếu field này thì INSERT round vẫn chạy (DB có default) nhưng backend **không thể đọc/ghi cờ Final**, làm rule phân công judge ở mục 4.2 không thể enforce được.

### 5.2. [MODIFY] các DTO + Service liên quan
* **`CreateRoundRequest.java`**: thêm `isFinal` (default `false`).
* **`UpdateRoundRequest.java`**: thêm `isFinal` (nullable — chỉ update khi client gửi, theo pattern partial-update sẵn có).
* **`RoundResponse.java`** + **`RoundDetailResponse.java`**: thêm `isFinal` vào response.
* **`RoundService.java`**: nối `isFinal` vào cả 4 điểm — `createRound` (builder), `updateRound` (set có điều kiện), `mapToResponse`, `mapToDetailResponse`.

---

## 6. Các phần đã rà soát và KHÔNG cần sửa (kèm lý do)

* **`AuthService` / `UserPrincipal` / JWT roles:** chỉ đọc `userEventRoles → role.roleName`, không đụng các cột đã xóa → login/phân quyền không ảnh hưởng.
* **`ScoringService`, `SubmissionService`, `RoundResultService`, `TeamService`, `TeamInviteService`, `NotificationService`, `AccountApprovalService`:** các bảng Submission/Score/RoundResult/Team/TeamInvite/Notification/AccountApproval **không đổi cấu trúc** trong schema mới.
* **Entity `User`:** cột `expired_at` đã có sẵn trong cả schema cũ và mới, entity đã map đúng.
* **`application.properties`:** giữ `ddl-auto=none` — đúng chủ trương DB-first; schema/seed được quản lý bằng SQL scripts.

---

## 7. Kiểm chứng

* **Grep toàn bộ source** xác nhận không còn bất kỳ tham chiếu nào tới `TeamAssignment`, `UserEventRole.trackId/roundId/judgeType`.
* **Build:** `mvnw compile` và `mvnw package -DskipTests` đều **PASS** (project chưa có test tự động).
* **Đối chiếu query mẫu** trong `seal_scripts.sql` (B2.1, B3.1): logic suy ra danh sách team judge cần chấm trong `AssignmentService` khớp với JOIN pattern của query chuẩn — `track_id IS NULL OR tm.track_id = ja.track_id` trên team của event thuộc round.
* **Gợi ý test thủ công:** chạy `seal_schema.sql` + `seal_seed.sql`, khởi động app, login `judge.binh@fpt.edu.vn / Test@1234` → `GET /api/judge/assignments` phải trả các team APPROVED track Web (Phoenix, Dragon, Falcon) lặp theo 3 round; login `mentor.hung@fpt.edu.vn` → `GET /api/mentor/assignments` trả team track Web của event 2 và 3.

---

## 8. [MODIFY] `Postman/Postman_Full_Collection.json` — đồng bộ collection với schema & seed mới

* **Sửa email login khớp seed mới:** `judge@fpt.edu.vn` → `judge.binh@fpt.edu.vn`; `mentor@fpt.edu.vn` → `mentor.an@fpt.edu.vn` (2 user cũ không tồn tại trong `seal_seed.sql` mới → login fail, token judge/mentor không bao giờ được lưu).
* **"POST create staff (Guest Judge)":** bổ sung `"trackId": 1` — round 1 là vòng thường (`is_final = FALSE`) nên rule mới bắt buộc track; body cũ sẽ bị 400.
* **Assign role:** đổi tên request cũ thành "(Mentor – gán theo track)"; thêm 2 request mới minh họa nghiệp vụ mới: gán Judge round thường (kèm `trackId` + `judgeType`) và case lỗi gán Judge round thường nhưng thiếu `trackId` (expect 400 — minh họa rule `is_final`).
* **Rounds:** thêm `"isFinal": false` vào body create/update round; thêm request mới "POST create round (Final – isFinal=true)".
* **Sửa bug có sẵn ở toàn bộ test script lưu biến:** script đọc `res.data.id` trong khi các response DTO trả về `eventId` / `trackId` / `roundId` / `teamId` / `submissionId` / `criteriaId` / `inviteId` / `notificationId` → biến chain không bao giờ được set, các request phía sau luôn chạy với giá trị mặc định. Đã sửa 8 script về đúng tên field.
* **"POST send invite":** đổi `invitedUserId` 5 → 6 (user 5 chính là participant đang đăng nhập — tự mời mình sẽ lỗi).
* Đổi tên folder "13. TEAM ASSIGNMENTS" → "13. ASSIGNMENTS (MENTOR / JUDGE)" theo mô hình mới (URL endpoint không đổi).
* Đã validate JSON sau khi sửa (parse OK, đủ 14 folder).

---

## 9. Tổng hợp file thay đổi

| Loại | File |
|------|------|
| [DELETE] | `entity/TeamAssignment.java`, `repository/TeamAssignmentRepository.java`, `service/TeamAssignmentService.java`, `controller/TeamAssignmentController.java` |
| [NEW] | `entity/JudgeAssignment.java`, `entity/MentorAssignment.java`, `repository/JudgeAssignmentRepository.java`, `repository/MentorAssignmentRepository.java`, `service/AssignmentService.java`, `controller/AssignmentController.java` |
| [MODIFY] | `entity/UserEventRole.java`, `entity/Round.java`, `repository/TeamRepository.java`, `service/UserRoleService.java`, `service/RoundService.java`, `controller/UserController.java`, `dto/request/AssignRoleRequest.java`, `dto/request/CreateStaffRequest.java`, `dto/request/CreateRoundRequest.java`, `dto/request/UpdateRoundRequest.java`, `dto/response/UserEventRoleResponse.java`, `dto/response/RoundResponse.java`, `dto/response/RoundDetailResponse.java`, `Postman/Postman_Full_Collection.json` |

**Ghi chú tương thích API:** toàn bộ URL endpoint, phân quyền và shape JSON request/response được giữ nguyên; thay đổi duy nhất client cần biết là (1) `GET /api/users/roles` không còn trả `trackId/trackName/roundId/roundName/judgeType`, và (2) gán JUDGE vào round thường giờ bắt buộc kèm `trackId` (round Final thì ngược lại — không được gửi `trackId`).
