# AI Session Log — Track Problem Import ("Đề thi") · BACKEND

**Date:** 2026-06-24
**Project:** SEAL – Software Engineering Hackathon Management System
**Branch:** `KhanhNLH-track-problem-import`
**Scope (BE):** Nghiệp vụ **import đề thi cho từng track**. Event Coordinator upload 1 file đề/track khi event ở `SETUP`/`IN_PROGRESS`, **công bố (release)** có chủ đích; chỉ thành viên đội **APPROVED** thuộc đúng track mới **tải đề** khi đã công bố. File được lưu **ngoài** thư mục public `/uploads` và **stream qua endpoint có kiểm soát quyền**.

> Tài liệu đọc để lấy context: `back-end/Postman/Postman_Full_Collection.json` (toàn bộ API hiện có) và `docs/documents/ProjectRequirements.md` (6 actor; cấu trúc Event → Track → Round; vòng đời event).

---

## PHẦN 0 — CONTEXT & CHỐT YÊU CẦU (Q&A)

### 0.1. Hiện trạng hệ thống (đã khảo sát)
- **Vòng đời event** (`HackathonEventService`): `DRAFT ↔ OPEN ↔ SETUP ↔ IN_PROGRESS → COMPLETED` (+`CANCELLED`). **`SETUP`** = đóng đăng ký, coordinator khóa/bốc đội vào track trước khi thi → đúng thời điểm import đề.
- **`Track`** entity ban đầu chỉ có `name`, `description`, `capacity`. Chưa có gì cho đề thi.
- **Đã có sẵn pattern upload file**: `AuthService.updateAvatar()` lưu vào `app.upload.dir` (`uploads/`) và **serve công khai** tại `/uploads/**` qua `WebMvcConfig`. Giới hạn multipart cũ: 5MB.
- **`Submission`** dùng URL (repo/demo/slide), không upload file.
- **Audit**: có `AuditLogService.record(actorId, action, targetType, targetId, reason, metadata)` cho **business action** (khác `SystemLogService` chỉ cho admin).

### 0.2. Quyết định chốt với người dùng
| Câu hỏi | Lựa chọn |
|---|---|
| Định dạng đề | **Upload file** (PDF/DOC/DOCX/ZIP) |
| Cơ chế công bố | **Có nút Release riêng**: upload → `released=false` (ẩn) → Release → `released=true` |
| Mức độ chi tiết | **1 đề / track** cho cả sự kiện |
| Ai tải được | **Chỉ thành viên đội APPROVED thuộc track đó** |
| Trạng thái cho upload/sửa | **SETUP và IN_PROGRESS** |
| Sau release | **Được thay file + thu hồi (retract)** |
| Giới hạn file | **20MB**, PDF/DOC/DOCX/ZIP |
| Release/Retract API | **2 endpoint riêng** (theo convention `activate`/`deactivate`, `approve`/`reject` sẵn có) |

### 0.3. ⚠️ Phát hiện bảo mật then chốt (điều chỉnh so với ý tưởng ban đầu)
Ý tưởng đầu: lưu đề vào `/uploads/problems/`. **KHÔNG được** — `WebMvcConfig` serve **toàn bộ `/uploads/**` công khai, không auth** ⇒ ai đoán URL cũng tải được ⇒ **phá vỡ cả 2 luật** (chưa release vẫn tải, không thuộc track vẫn tải).
→ **Giải pháp**: đề lưu ở **thư mục riêng `app.problem.dir` (mặc định `protected/problems`)**, **DB chỉ giữ storage key** (không phải public URL), tải qua **endpoint download có check quyền**. Avatar vẫn dùng `/uploads` vì nó công khai; đề thì không.

---

## PHẦN 1 — DATABASE

`ddl-auto=none` (DB là source of truth, scripts ở `back-end/database scripts/`). Vì vậy **phải chạy migration tay** — Hibernate KHÔNG tự tạo cột (lỗi "Unknown column" nếu quên chạy).

**7 cột thêm vào `Track`** (`migration_track_problem.sql` + đồng bộ trong `seal_schema.sql`):

| Cột | Kiểu | Ý nghĩa |
|---|---|---|
| `problem_storage_key` | VARCHAR(500) | đường dẫn file nội bộ; NULL = chưa có đề |
| `problem_file_name` | VARCHAR(255) | tên file gốc (hiện cho participant) |
| `problem_file_size` | BIGINT | dung lượng (byte) |
| `problem_content_type` | VARCHAR(100) | MIME khi download |
| `problem_released` | TINYINT(1) NOT NULL DEFAULT 0 | đã công bố? |
| `problem_uploaded_at` | DATETIME | mốc upload/thay |
| `problem_released_at` | DATETIME | mốc công bố (NULL khi ẩn) |

Migration là **non-destructive** (`ALTER TABLE … ADD COLUMN …`), chạy một lần; re-run báo "Duplicate column" vô hại.

---

## PHẦN 2 — BACKEND: CÁC FILE

### 2.1. `entity/Track.java`
Thêm 7 field map đúng 7 cột. `problemReleased` là `Boolean` với `@Builder.Default = false`, `@Column(nullable=false)`.

### 2.2. `dto/response/TrackProblemResponse.java` (mới)
`trackId, trackName, hasProblem, fileName, fileSize, contentType, released, uploadedAt, releasedAt`. **Không bao giờ inline nội dung file** — chỉ metadata; tải qua endpoint download.

### 2.3. `repository/TeamMemberRepository.java`
Thêm derived query gate quyền tải:
```java
boolean existsByUser_UserIdAndTeam_Track_TrackIdAndTeam_StatusIgnoreCase(
        Integer userId, Integer trackId, String status);
```
→ "user có thuộc một team **status=APPROVED** đã gán vào **track này** không?".

### 2.4. `service/TrackProblemService.java` (mới — lõi nghiệp vụ)
Hằng số: `PROBLEM_MUTATION_ALLOWED_EVENT_STATUSES = {SETUP, IN_PROGRESS}`, `ALLOWED_EXTENSIONS = {pdf, doc, docx, zip}`, `MAX_FILE_SIZE = 20MB`. `@Value app.problem.dir`.

Phương thức:
- `uploadProblem(actorId, eventId, trackId, file)` — gate trạng thái event; validate rỗng/size/đuôi; tạo `protected/problems`; lưu `track_{id}_{ts}.{ext}`; **xóa file cũ** (best-effort) khi thay; set metadata + `uploadedAt`; **giữ nguyên `released`** (thay file không vô tình ẩn/hiện); audit `UPLOAD_PROBLEM`.
- `releaseProblem` — yêu cầu có file; set `released=true`, `releasedAt=now`; audit `RELEASE_PROBLEM` (idempotent).
- `retractProblem` — `released=false`, `releasedAt=null`; audit `RETRACT_PROBLEM`.
- `removeProblem` — gate trạng thái; xóa file + clear toàn bộ field; audit `REMOVE_PROBLEM`.
- `listProblemsForEvent(eventId)` — coordinator: mọi track (sort theo tên) → `List<TrackProblemResponse>`.
- `getProblem(eventId, trackId, userId, privileged)` — **gated metadata**: privileged → đầy đủ; participant phải là thành viên APPROVED của track **và** chỉ thấy khi `released` (chưa release → trả `hasProblem=false`, giấu hẳn).
- `downloadProblem(...)` → record `ProblemDownload(Resource, fileName, contentType, size)`. Privileged tải bất kỳ lúc nào; participant phải thuộc track **và** `released`, ngược lại `ForbiddenException`. File thiếu trên đĩa → `ResourceNotFoundException`.

Helper an toàn: `resolveStoredFile` **chống path-traversal** (resolve+normalize, kiểm tra `startsWith(base)`); `sanitizeFileName` chỉ lấy phần tên (bỏ thư mục client gửi); `resolveContentType` map đuôi → MIME khi client gửi octet-stream.

### 2.5. `controller/TrackProblemController.java` (mới)
`@RequestMapping("/api/events/{eventId}")`. `PRIVILEGED_ROLES = {ROLE_EVENT_COORDINATOR, ROLE_SYSTEM_ADMIN}`; `isPrivileged(auth)` quét authorities.

| Method | Path | Quyền |
|---|---|---|
| POST (multipart `file`) | `/tracks/{t}/problem` | `EVENT_COORDINATOR` |
| PUT | `/tracks/{t}/problem/release` | `EVENT_COORDINATOR` |
| PUT | `/tracks/{t}/problem/retract` | `EVENT_COORDINATOR` |
| DELETE | `/tracks/{t}/problem` | `EVENT_COORDINATOR` |
| GET | `/problems` | `EVENT_COORDINATOR` |
| GET | `/tracks/{t}/problem` | `isAuthenticated()` (gate trong service) |
| GET | `/tracks/{t}/problem/download` | `isAuthenticated()` (gate trong service) → `ResponseEntity<Resource>` |

Download set `Content-Disposition: attachment` với **RFC 5987** `filename*=UTF-8''…` (giữ tên Unicode) + `filename="ascii-fallback"`.

### 2.6. `config/SecurityConfig.java`
`/api/events/**` vốn `permitAll` ở URL-level, nhưng **`@EnableMethodSecurity` bật** nên `@PreAuthorize` của controller **vẫn enforce** (coordinator-only và isAuthenticated). → **Không cần** đổi rule URL. Chỉ thêm `config.setExposedHeaders(List.of("Content-Disposition"))` để JS đọc được tên file khi download cross-origin (FE :5173 ↔ BE :8080).

### 2.7. `application.properties`
`spring.servlet.multipart.max-file-size=20MB`, `max-request-size=21MB`; thêm `app.problem.dir=${PROBLEM_DIR:protected/problems}`.

### 2.8. `dto/response/MyTeamResponse.java` + `service/TeamService.java`
Thêm `trackId` vào response `GET /api/teams/my` (FE participant cần `trackId` để gọi API đề; trước chỉ có `trackName`).

---

## PHẦN 3 — FLOW NGHIỆP VỤ

**Coordinator (SETUP/IN_PROGRESS):**
```
upload file  → Track.problem_* set, released=false → audit UPLOAD_PROBLEM
release      → released=true, releasedAt=now        → audit RELEASE_PROBLEM
(retract     → released=false                       → audit RETRACT_PROBLEM)
(replace     → ghi đè file, GIỮ released            → audit UPLOAD_PROBLEM)
(remove      → xóa file + clear field               → audit REMOVE_PROBLEM)
```

**Participant tải đề:**
```
GET /tracks/{t}/problem/download (kèm JWT)
 → service: privileged? → coordinator/admin: cho qua
          : else → membership APPROVED ở track? AND released=true?
                    → đúng: stream file (FileSystemResource)
                    → sai : 403 Forbidden
```

**Lý do an toàn:** file nằm ngoài `/uploads` (không bị static handler lộ), nên **mọi** đường tải đều bắt buộc qua endpoint này và phải qua 2 cửa gate (membership + released).

---

## PHẦN 4 — KIỂM THỬ
- `mvnw -q -DskipTests compile` → **BUILD OK** sau mỗi thay đổi BE.
- Postman: thêm nhóm **"16. TRACK PROBLEMS"** (upload/list/meta/release/retract/download/remove + test 403 khi participant tải đề chưa công bố).
- Lỗi runtime "Unknown column 'problem_content_type'" xảy ra do **chưa chạy migration** (ddl-auto=none) → chạy `migration_track_problem.sql` là hết, **không cần restart**.

## PHẦN 5 — FILE THAY ĐỔI (BE)
| File | Loại |
|---|---|
| `database scripts/migration_track_problem.sql` | mới |
| `database scripts/seal_schema.sql` | sửa (cột Track) |
| `entity/Track.java` | sửa (+7 field) |
| `dto/response/TrackProblemResponse.java` | mới |
| `service/TrackProblemService.java` | mới |
| `controller/TrackProblemController.java` | mới |
| `repository/TeamMemberRepository.java` | sửa (+1 query) |
| `config/SecurityConfig.java` | sửa (exposed header) |
| `resources/application.properties` | sửa (multipart 20MB + problem.dir) |
| `dto/response/MyTeamResponse.java` + `service/TeamService.java` | sửa (+trackId) |
| `.gitignore` | sửa (ignore `uploads/`, `protected/`) |
| `Postman/Postman_Full_Collection.json` | sửa (nhóm 16) |

---

# PHỤ LỤC A — GIẢI THÍCH CHI TIẾT CODE

> Đọc theo dòng dữ liệu: **request → gate quyền/trạng thái → đụng đĩa → cập nhật DB → audit → response**.

## A.1. Vì sao file phải nằm ngoài `/uploads`
`WebMvcConfig.addResourceHandlers` map `/uploads/**` → `app.upload.dir` **không qua filter auth**. Nếu đề nằm trong đó, URL `/uploads/problems/track_3_xxx.pdf` ai cũng GET được. Đề là tài nguyên **có điều kiện** (released + đúng track) nên bắt buộc:
1. Lưu ở `app.problem.dir` (KHÔNG nằm dưới `app.upload.dir`).
2. DB chỉ lưu **storage key** (`track_3_1718...pdf`), không phải `/uploads/...`.
3. Tải qua `GET …/problem/download` — nơi `@PreAuthorize("isAuthenticated()")` + gate trong service quyết định.

## A.2. Gate quyền tải — `downloadProblem`
```java
Track track = requireTrackInEvent(eventId, trackId);   // track tồn tại & thuộc event
if (track.getProblemStorageKey() == null) throw 404;   // chưa có đề
if (!privileged) {                                     // không phải coordinator/admin
    requireTrackMembership(userId, trackId);           // phải là thành viên APPROVED của track
    if (!Boolean.TRUE.equals(track.getProblemReleased()))
        throw new ForbiddenException(...);             // chưa công bố → cấm
}
Path file = resolveStoredFile(track.getProblemStorageKey());  // chống traversal
return new ProblemDownload(new FileSystemResource(file), name, contentType, size);
```
`privileged` được controller tính từ authorities (`ROLE_EVENT_COORDINATOR`/`ROLE_SYSTEM_ADMIN`) rồi truyền xuống — service không phụ thuộc SecurityContext, dễ test.

## A.3. `requireTrackMembership` — câu derived query
```java
teamMemberRepository.existsByUser_UserIdAndTeam_Track_TrackIdAndTeam_StatusIgnoreCase(
        userId, trackId, "APPROVED");
```
Spring Data dịch thành EXISTS join `TeamMember → Team → Track`, lọc `team.status='APPROVED'` và `team.track.trackId=?`. Một câu, không tải entity — chỉ trả boolean.

## A.4. Chống path-traversal — `resolveStoredFile`
```java
Path base = Paths.get(problemDir).toAbsolutePath().normalize();
Path resolved = base.resolve(storageKey).normalize();
if (!resolved.startsWith(base)) throw new BadRequestException("Invalid problem file reference.");
```
Dù `storageKey` do hệ thống sinh (an toàn), vẫn normalize + kiểm tra prefix để phòng dữ liệu DB bị can thiệp (vd `../../etc/...`).

## A.5. Thay file giữ nguyên `released`
`uploadProblem` cố tình **không đụng** `problemReleased`. Lý do: coordinator sửa typo của đề **đang công bố** thì không nên vô tình ẩn nó; muốn ẩn phải bấm Retract riêng. Lần upload đầu thì `released` vốn đã `false` (default), nên hành vi vẫn đúng "upload xong là ẩn".

## A.6. Audit log — best-effort, không chặn nghiệp vụ
Mỗi action gọi `auditLogService.record(actorId, "UPLOAD_PROBLEM"/"RELEASE_PROBLEM"/..., "TRACK", trackId, null, Map.of("eventId", eventId, ...))`. `AuditLogService` đã thiết kế best-effort (actor lạ → no-op, không ném lỗi) nên việc ghi log không bao giờ làm hỏng hành động chính.

## A.7. `@PreAuthorize` vẫn enforce dù URL permitAll
`SecurityConfig` có `.requestMatchers("/api/events/**").permitAll()`. Nhưng `@EnableMethodSecurity` khiến **method-level** `@PreAuthorize` chạy độc lập với rule URL: coordinator-only endpoint vẫn 403 với người khác; endpoint `isAuthenticated()` vẫn chặn ẩn danh. JWT filter vẫn parse token nếu có để set authentication. → Không phải nới lỏng gì ở URL-level.

## A.8. Download header Unicode-safe
```java
String encoded = URLEncoder.encode(name, UTF_8).replace("+", "%20");
"attachment; filename=\"" + asciiFallback(name) + "\"; filename*=UTF-8''" + encoded;
```
Trình duyệt hiện đại đọc `filename*` (giữ tên tiếng Việt/Unicode); client cũ rơi về `filename=` ASCII. `setExposedHeaders(Content-Disposition)` ở CORS để FE đọc được header này (nếu không vẫn có fallback tên file từ metadata phía FE).
