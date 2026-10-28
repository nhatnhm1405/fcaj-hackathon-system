# Proposal chi tiết — Dữ liệu RDS MySQL, S3 và AI job

Ngày lập: 20/10/2026 · Cập nhật: 21/10/2026 · Hạn bàn giao: 12:00 ngày 21/10/2026, Asia/Saigon.

**Trạng thái:** bản đề xuất để review, không phải biên bản nghiệm thu triển khai. Source đối chiếu: `main@f42a168`. Đã kiểm tra chỉ đọc qua phiên MySQL Workbench local do user mở ngày 21/10; DB có 31 bảng nhưng lệch entity hiện tại. Action Output ghi DB vừa được xóa/tạo lại từ script SQL trước lần kiểm tra; không coi đây là snapshot DB trước reset hoặc schema code-first đã nghiệm thu. Không sửa backend/DB; 195 unit tests liên quan đã pass ngày 20/10. AWS và FE/AI sign-off vẫn chưa nghiệm thu.

## 1. Mục tiêu, phạm vi và đầu ra

Phần việc này chuẩn bị phương án dữ liệu cho workshop modernization của hệ thống SEAL/FCAJ. Giữ Spring Boot/JPA và nghiệp vụ hiện có; chuyển dữ liệu quan hệ sang RDS MySQL, file cần lưu bền sang S3 và thiết kế metadata cho AI worker local chạy bất đồng bộ.

| Yêu cầu được giao | Nội dung bàn giao trong tài liệu |
|---|---|
| Khảo sát dữ liệu, avatar và đề thi | Mục 2; báo cáo kiểm tra source/runtime riêng |
| Chọn RDS/S3 và bảng quyền | Mục 3–4 |
| Giữ quyền đề thi theo team/track/release | Mục 5 |
| Metadata AI job và quyền kết quả | Mục 6–8; hợp đồng dự thảo để FE/AI review |
| Migration, backup/restore | Mục 9–10 |
| JWT secret, bootstrap admin, schema | Mục 11 |
| Đề xuất 3–5 task backend tuần 2 | Mục 12, gồm 5 work packages có acceptance |
| Bản đóng góp ngắn cho proposal nhóm | [Bản tóm tắt](data-storage-access-proposal.md) |

Không nằm trong lần bàn giao này: sửa code backend, chạy migration, bật/tắt service MySQL, deploy AWS, thay quy tắc chấm điểm hoặc tự xác nhận thay các thành viên. Kiểm tra chỉ đọc và chạy test biệt lập phục vụ khảo sát nằm trong phạm vi.

## 2. Kết quả khảo sát backend hiện tại

### 2.1. Dữ liệu quan hệ: 31 entity JPA

| Nhóm | Entity hiện có |
|---|---|
| Tài khoản và quyền | User, Role, UserEventRole, ParticipationAccessRequest, PasswordResetOtp |
| Sự kiện/vòng thi | HackathonEvent, Track, Round, RoundTimer, RoundTimerNotice |
| Đội và membership | Team, TeamEventEntry, TeamMember, TeamInvite, JoinRequest, TeamRejoinRequest |
| Chấm thi | Submission, JudgeAssignment, ScoringCriteriaTemplate, ScoringCriteria, Score, RoundResult |
| Mentor | MentorAssignment, MentorSupportRequest |
| Giải thưởng/lịch sử | Prize, ParticipantEventHistory |
| Thông báo | Announcement, Notification |
| Kiểm soát và nhật ký | ReopenRequest, AuditLog, SystemLog |

Đây là danh sách entity. DB local sau reset cũng có 31 bảng, nhưng tập bảng khác entity: có AccountApproval, thiếu MentorSupportRequest. Trùng số lượng không chứng minh schema đồng bộ; xem mục 2.5.

### 2.2. Các quan hệ phải bảo toàn

- `Team` giữ identity của đội; `TeamMember` nối User với Team và vai trò thành viên hiện tại.
- `TeamEventEntry` nối Team với HackathonEvent; giữ track nullable, trạng thái duyệt/loại đội. Unique `(team_id, event_id)`. Không lấy track/status trực tiếp từ Team.
- `Submission` nối Team và Round; event suy ra qua Round. Unique `(team_id, round_id)`. Nộp lại cập nhật bản ghi hiện có; chưa có lịch sử phiên bản hay commit SHA riêng trong entity.
- `Score` nối Submission, judge User và ScoringCriteria; unique `(submission_id, judge_user_id, criteria_id)`. Điểm từng tiêu chí khác với tổng hợp/xếp hạng ở RoundResult.
- `JudgeAssignment` cấp phạm vi round/track, có `isActive`; track null dùng cho phạm vi all-track theo quy tắc vòng chung kết của service.
- `ParticipantEventHistory.resultJson` là snapshot nghiệp vụ dạng TEXT trong SQL. JSON không đồng nghĩa phải đưa sang S3.
- Một số `eventId`/`resolvedBy` trong entity là số nguyên, không phải quan hệ JPA; không tuyên bố tất cả liên kết đều đã có FK ở DB.

Nguồn: [TeamEventEntry](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/entity/TeamEventEntry.java), [Submission](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/entity/Submission.java), [SubmissionService](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/service/SubmissionService.java#L94), [Score](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/entity/Score.java), [ParticipantEventHistory](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/entity/ParticipantEventHistory.java).

### 2.3. SQL, file local và URL ngoài hệ thống

| Nội dung | Hiện lưu ở đâu? | Nhận xét từ source |
|---|---|---|
| Dữ liệu nghiệp vụ, điểm, audit, snapshot lịch sử | MySQL | Tiếp tục lưu quan hệ; không chuyển điểm chính thức thành file AI |
| Avatar upload | `{UPLOAD_DIR}/avatars`; mặc định `uploads/avatars` | User.avatarUrl giữ URL `/uploads/avatars/...`; service giới hạn 5 MiB, kiểm MIME prefix `image/` |
| Avatar OAuth | URL ngoài hệ thống trong User.avatarUrl | Không phải file do app quản lý; không tự tải/copy khi migration |
| Đề thi | `{PROBLEM_DIR}`; mặc định `protected/problems` | Một file/track; PDF/DOC/DOCX/ZIP, tối đa 20 MiB; metadata/release trong Track |
| Repo/demo/slide | URL trong Submission | Backend không lưu bản đầy đủ của repo/demo/slide như file bài nộp |
| AI insight | Luồng Gemini đồng bộ, trả DTO | Chưa có AiJob entity hoặc lưu kết quả trong luồng hiện tại |

Avatar được phục vụ public qua `/uploads/**`; đề thi đi qua API có guard, không nằm dưới thư mục public. Upload avatar mới chưa thấy bước dọn ảnh cũ ngay trong `updateAvatar`; remove avatar chỉ cố xóa ảnh đang được tham chiếu. Với đề thi, thao tác xóa file cũ diễn ra trước khi transaction metadata hoàn tất: có rủi ro lệch file/DB khi rollback.

Nguồn: [AuthService](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/service/AuthService.java#L252), [WebMvcConfig](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/config/WebMvcConfig.java), [SecurityConfig](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/config/SecurityConfig.java#L132), [TrackProblemService](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/service/TrackProblemService.java#L71), [AI service](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/service/AiJudgeAssistantService.java#L69).

### 2.4. Cấu hình cần chú ý

- URL mặc định dùng MySQL local, database `seal_hackathon`; Compose có URL override riêng. Chưa coi URL mặc định là cấu hình runtime thật.
- `spring.jpa.hibernate.ddl-auto=${DDL_AUTO:update}`; chưa có Flyway/Liquibase trong pom được rà.
- Naming strategy giữ đúng tên khai báo. Phải đối chiếu case tên bảng khi chuyển MySQL Windows sang RDS, không giả định môi trường có cùng `lower_case_table_names`.
- Source dùng LocalDateTime. `serverTimezone=UTC` trong JDBC URL không tự chứng minh dữ liệu lịch sử đã lưu theo UTC.
- Compose có volume MySQL nhưng chưa có volume backend cho avatar/đề thi. Rủi ro này thuộc phương án deploy Compose, không phải khẳng định local MySQL hiện mất file.
- SQL `seal_schema.sql` có `DROP DATABASE`; chỉ tham khảo, tuyệt đối không chạy để inventory/migrate dữ liệu cần giữ.
- `run-demo.ps1` mặc định cũng có bước DROP DATABASE trước khi chạy backend nếu không có `-NoDrop`. Không dùng script reset/demo làm quy trình migration hoặc khôi phục dữ liệu thật. Dùng `-NoDrop` vẫn không làm backend thành read-only vì Hibernate/seeder còn có thể ghi.

### 2.5. Kết quả MySQL thực tế — ngày 21/10/2026

Đã dùng kết nối Workbench đang đăng nhập, chỉ chạy SELECT trong tab truy vấn riêng. Không lấy/xuất mật khẩu; không chạy hai tab seal_schema/seal_seed đang mở.

| Nội dung | Kết quả thực tế | Ý nghĩa cho proposal |
|---|---|---|
| Server / port | MySQL 8.0.46 / 3306 | Đã xác nhận server, không chỉ client |
| Tables / engine / FK | 31 bảng InnoDB, 58 khóa ngoại | Mới kiểm tổng số FK, chưa đối chiếu đầy đủ từng definition/delete rule |
| Case tên bảng | lower_case_table_names = 1 | Chốt naming/case khi dựng schema đích; không import dump Windows rồi mặc định khớp @Table trên môi trường khác |
| Charset / collation | Server utf8mb4 / utf8mb4_0900_ai_ci; database utf8mb4_unicode_ci | Server default và database collation không giống nhau; cần giữ/chốt rõ ở migration |
| Timezone global/session | SYSTEM / SYSTEM | Không khẳng định timestamp lịch sử là UTC |
| Unique chính | team_id+event_id; team_id+round_id; submission_id+judge_user_id+criteria_id | Đã kiểm unique index đúng thứ tự cột ở ba quan hệ trọng yếu |
| Role | 4 bản ghi | Có role seed; không đồng nghĩa có tài khoản admin |
| 10 bảng nghiệp vụ đã đếm | User, HackathonEvent, Team, TeamMember, TeamEventEntry, Track, Round, Submission, Score, RoundResult: mỗi bảng 0 bản ghi | Chưa có dữ liệu ở các bảng này tại thời điểm kiểm tra; không kết luận mọi bảng/môi trường khác đều rỗng |
| MentorSupportRequest | Không có bảng | Lệch backend hiện tại |
| AccountApproval | Có bảng | Không có entity tương ứng trong inventory source; không tự xóa |
| Notification | Thiếu sender_user_id, sender_role, scope_label | Lệch mapping hiện tại, có thể gây lỗi các luồng sử dụng cột này |
| AiJob / flyway_schema_history | Chưa có | AI job và quản lý migration vẫn là phần mới |

Action Output hiển thị DROP/CREATE DATABASE và tạo bảng lúc 23:59:28–30 trước khi agent thao tác; UI không gắn ngày ở từng dòng lịch sử này. Truy vấn kiểm tra mới trả kết quả lúc 00:05:18 và 00:06:09 ngày 21/10 theo phiên quan sát. Không có bằng chứng để tái dựng dữ liệu trước reset, hoặc khẳng định reset do ai thực hiện.

**Kết luận:** có thể hoàn thành phần khảo sát hiện trạng với giới hạn trên, nhưng **chưa chốt schema baseline**. Chưa export schema thực tế, chưa so đầy đủ cột/index/FK và chưa chạy backend validate với schema này. Chi tiết evidence: [báo cáo kiểm tra](../report/data-storage-backend-audit-2026-10-20.md).

## 3. Lựa chọn công nghệ và ranh giới kiến trúc

| Thành phần | Chọn cho workshop v1 | Lý do / giới hạn |
|---|---|---|
| Dữ liệu quan hệ | Amazon RDS for MySQL, Single-AZ | Giữ SQL/JPA, backup có quản lý; v1 không có HA database |
| Phiên bản DB đích | MySQL 8.4 là ứng viên, chốt minor sau compatibility test | Local server đã xác nhận 8.0.46; không bê nguyên phiên bản/schema lệch lên RDS; kiểm support khi provision |
| Migration | Flyway; migration bằng danh tính riêng | Version hóa thay đổi; app runtime `DDL_AUTO=validate` |
| File | Amazon S3 private, S3 Standard, AWS SDK for Java v2 | Tách khỏi filesystem container; giới hạn quyền theo bucket/prefix |
| Metadata AI | Bảng AiJob mới trong RDS | State, ownership, version, tham chiếu input/result; không lưu điểm chính thức từ AI |
| Điều phối AI | SQS jobs/results và DLQ; local worker | BE2/AI phụ trách phần messaging/worker; BE1 phụ trách metadata và kiểm quyền |
| Secret v1 | Parameter Store SecureString theo IAM/KMS policy của Lead | Secrets Manager khi cần cơ chế quản lý/rotation đã được nhóm chọn; không mặc định dùng cả hai |
| Theo dõi | Log có cấu trúc, metrics/alarms qua CloudWatch | Không log secret, raw source hay dữ liệu nhận diện không cần thiết |

Lưu ý phiên bản/chi phí: tài liệu AWS hiện ghi RDS MySQL 8.0 hết standard support ngày 31/07/2026. Không chọn 8.0 chỉ để giống local rồi bỏ qua Extended Support; ưu tiên kiểm thử 8.4 và xác nhận minor khả dụng tại Region trước tạo DB. Xem [AWS MySQL version management](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/MySQL.Concepts.VersionMgmt.html).

Luồng dữ liệu bằng lời: người dùng gọi HTTPS API → backend kiểm quyền → đọc/ghi RDS hoặc S3 bằng IAM role. Backend phát job đã ghi bền → SQS → worker local đọc input, phân tích đúng commit → ghi S3 result → gửi result event → backend kiểm chứng rồi cập nhật RDS → frontend đọc qua API có kiểm quyền.

Backend có thể tiếp tục chạy EC2/Docker theo kế hoạch nhóm. RDS nằm trong private DB subnets, `PubliclyAccessible=false`, Security Group chỉ nhận MySQL từ SG backend; local worker không kết nối RDS. Một DB subnet group trải nhiều AZ không biến Single-AZ thành Multi-AZ.

Tài liệu này không tạo sơ đồ kiến trúc nộp chấm. Nhóm tự vẽ/review bằng icon AWS chính thức và bảo đảm bản deploy khớp sơ đồ theo barem đã cung cấp.

### 3.1. Thay đổi schema/storage interface cần thiết

Không tạo bảng file tổng quát chỉ để tăng số thành phần. Với hai loại upload hiện có, đề xuất mở rộng metadata trực tiếp; DDL cuối phải dựa trên inventory DB thật.

| Đối tượng | Thay đổi đề xuất | Tương thích dữ liệu cũ |
|---|---|---|
| User | Thêm `avatarStorageKey`, MIME/size/checksum và object version nếu sử dụng | Giữ avatarUrl cho OAuth/legacy. Có S3 key thì DTO sinh URL đọc qua app; không có key thì giữ URL ngoài hoặc local adapter của dev |
| Track | Giữ problemStorageKey/name/size/MIME/released/timestamps; thêm provider LOCAL/S3, checksum và object version khi cần | Provider cho phép chuyển từng record mà không đoán loại storage từ tên key; backfill LOCAL trước khi copy rồi đổi sang S3 sau xác minh |
| AiJob | Bảng mới theo mục 6, index/FK/retention và dispatch metadata | Không sửa Score/Submission thành nơi chứa kết quả AI; liên kết bằng ID |
| Dispatch/outbox | Field trạng thái dispatch trong AiJob hoặc bảng outbox riêng do BE2 review | Không duy trì hai cơ chế publish cạnh tranh; một owner cập nhật state |

Storage interface cung cấp put/get/delete và metadata theo provider; service nghiệp vụ vẫn sở hữu guard, transaction và release. Không trả filesystem path/S3 key cho FE. Đề xuất giữ field `avatarUrl` trong UserResponse để không phá FE; route đọc avatar do BE/FE chốt, có fallback cho URL `/uploads/avatars/...` cũ trong giai đoạn chuyển đổi. Khi thay avatar OAuth bằng upload S3, cập nhật source/reference nhất quán; khi xóa upload không được xóa tài nguyên bên ngoài.

## 4. Bảng lưu trữ và phân quyền đề xuất

### 4.1. Quyền nghiệp vụ qua API

| Loại dữ liệu | Nơi lưu | Người được đọc | Người được ghi / điều kiện |
|---|---|---|---|
| Hồ sơ và thông tin đăng nhập | RDS | Chủ tài khoản và chức năng quản trị được cấp quyền; public DTO chỉ trường cho phép | Chủ tài khoản sửa trường được phép; admin quản trị theo API, không trả hash/OTP/token |
| Role/grant | RDS | Theo chức năng quản trị; user xem quyền cần cho phiên | System Admin theo quy trình; không để client tự cấp role |
| Event/track/round/timer/criteria | RDS | DTO công khai hoặc staff/participant đúng phạm vi; không public mặc định toàn entity | Coordinator đúng event; global template thuộc luồng quản trị riêng |
| Team/TeamEventEntry/TeamMember | RDS | Theo membership, event và API hiện hữu | Leader/coordinator theo hành động; không cấp quyền trực tiếp bằng ID client gửi |
| Submission và URL ngoài | RDS | Đội của bài, judge đang được phân công, coordinator có scope; DTO theo người xem | Leader hợp lệ, team APPROVED, cửa sổ thi mở và còn hạn theo guard hiện tại |
| Score/RoundResult/Prize | RDS | Judge xem phần được cấp; participant/public chỉ dữ liệu đã công bố qua API | Judge ghi điểm của mình khi được phép; coordinator tổng hợp/công bố theo nghiệp vụ |
| Snapshot lịch sử/audit/log | RDS | Lịch sử cá nhân theo owner; audit/log theo chức năng quản trị tương ứng | Backend/service; không cho client sửa nhật ký hoặc snapshot tùy ý |
| Avatar upload | S3 assets private; RDS giữ key/MIME/size/checksum | Đề xuất giữ public-read qua API avatar của ứng dụng, không public bucket | Chủ tài khoản upload/xóa; guard xác thực, giới hạn và kiểm định file |
| Avatar OAuth | URL ngoài trong RDS | Theo DTO hồ sơ | Giữ nguyên URL; không coi là object để app xóa |
| Đề thi | S3 protected private; Track giữ metadata và release | Participant có membership APPROVED đúng track và đã release; staff preview đúng quyền | Coordinator đúng event; giữ lifecycle nghiệp vụ mục 5 |
| Input AI snapshot | S3 protected `ai-inputs/`; RDS giữ key/hash và snapshot nhỏ | Backend và worker; không trả raw input cho màn judge | Backend dispatcher, không để browser chọn key |
| Metadata job | AiJob trong RDS | Requester còn quyền hoặc coordinator đúng scope; DTO lọc trường | Backend duy nhất; worker gửi event, không cập nhật DB trực tiếp |
| AI result JSON | S3 protected `ai-results/`; AiJob giữ key/version/hash | Qua backend sau khi kiểm lại quyền; không participant/mentor/public mặc định | Worker ghi kết quả; backend xác thực trước khi nhận thành công |
| Secrets | Parameter Store SecureString hoặc nơi Lead phê duyệt | Chỉ runtime/deploy identity cần dùng | Người vận hành có quyền; không nằm trong bảng nghiệp vụ/Git/image/frontend |

Đây là **policy mục tiêu**, không khẳng định mọi endpoint hiện đã thực thi đầy đủ. Đặc biệt phải bổ sung/kiểm thử staff scope theo event; role `EVENT_COORDINATOR` đơn lẻ không đủ để chứng minh quyền với mọi event.

### 4.2. IAM khác với quyền người dùng

Đề xuất hai bucket ứng dụng: `assets` cho avatar và `protected` cho đề thi/input/result. Tên thực tế do IaC cấp, không hard-code; bucket frontend build là tài nguyên khác.

| Principal | Quyền tối thiểu mục tiêu |
|---|---|
| Backend EC2 role | Avatar/problem Get/Put và cleanup có kiểm soát; ai-inputs Put/Get; ai-results Get; jobs SendMessage; results Receive/Delete/visibility |
| Local worker session | jobs Receive/Delete/ChangeMessageVisibility; ai-inputs Get; ai-results Put; results SendMessage; không quyền RDS, avatar, đề thi, IAM hay bucket policy |
| Migration identity | DDL cần cho schema, khác runtime DB user; chỉ từ đường quản trị được cấp |
| Cleanup/restore operator | Quyền object version/delete hoặc restore theo runbook; không cấp DeleteObjectVersion mặc định cho worker |
| Browser | Không có AWS credential; không truy cập trực tiếp private problem/result |

IAM prefix không tự giới hạn một worker vào duy nhất một job trong mọi trường hợp. Với v1, worker là thành phần tin cậy của nhóm, có thể đọc các AI input trong prefix được cấp; cần nêu rõ ranh giới này. Không mô tả nó là cách ly tenant tuyệt đối.

Bật Block Public Access cho bucket; không dùng public ACL. Dùng encryption at rest và HTTPS; SSE-S3 là phương án nền cho object v1, SSE-KMS cần bổ sung key policy/chi phí nếu được yêu cầu. Không gắn public CloudFront behavior tới protected bucket. Xem [AWS Block Public Access](https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-control-block-public-access.html).

## 5. Giữ quyền tải đề thi khi chuyển S3

### 5.1. Quy tắc từ source cần bảo toàn

1. Xác thực người dùng; xác minh track thuộc event trong URL.
2. Coordinator/System Admin có preview theo role hiện tại; đề xuất kiểm thêm scope event phù hợp, cần Lead duyệt cách xử lý global admin.
3. User không có quyền preview phải là TeamMember của team có TeamEventEntry APPROVED đúng track. Query hiện tại đã join TeamEventEntry dù tên method còn mang cấu trúc cũ.
4. Với participant, file phải có tham chiếu và `problemReleased=true`.
5. Upload/replace/remove chỉ SETUP hoặc IN_PROGRESS; release chỉ IN_PROGRESS.
6. Replace file đang release hiện giữ cờ release; retract hiện không có event-status guard riêng. Không tự thay hai hành vi này trong một task đổi storage.
7. Mentor/Judge không có quyền tải đề chỉ nhờ hai role này; nếu đồng thời có quyền hợp lệ khác thì xét quyền đó.

Nguồn: [Controller](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/controller/TrackProblemController.java), [Download guard](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/service/TrackProblemService.java#L210), [Membership query](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/repository/TeamMemberRepository.java#L47).

### 5.2. Luồng đọc/ghi mục tiêu

- Backend resolve key từ Track; không nhận key tùy ý từ client. Key ví dụ: `problems/events/{eventId}/tracks/{trackId}/{uuid}.pdf`.
- Mỗi request tải kiểm tra quyền và release từ dữ liệu hiện tại, sau đó stream S3 GetObject. Trả MIME an toàn, Content-Disposition và `Cache-Control: private, no-store`.
- Retract chặn các lượt kiểm quyền sau khi trạng thái đã commit; không thu hồi bản đã tải hoặc stream đã được cấp. Đây không phải cơ chế DRM.
- V1 không cấp presigned URL cho đề thi/result. URL đã cấp có thể dùng đến hết hạn theo điều kiện AWS, còn đổi cờ release trong DB không tự thu hồi URL. Xem [AWS presigned URLs](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html).
- Upload/replace: validate → upload object mới bằng key mới → transaction cập nhật metadata → sau commit đưa object cũ vào hàng dọn. Nếu DB rollback, giữ tham chiếu cũ, dọn object mới bằng cơ chế retry/orphan scan.
- Remove: commit bỏ reference và release → dọn object sau commit. Không xóa object trước rồi trông chờ DB rollback khôi phục file.
- Dọn orphan chỉ sau grace period và kiểm lại reference/version; không quét xóa mọi object không xuất hiện trong một snapshot DB cũ.
- Kiểm tra file không dừng ở MIME do client khai báo/đuôi file. Thêm kiểm chữ ký định dạng, tên file, giới hạn kích thước; không tự giải nén ZIP hoặc thực thi nội dung.

### 5.3. Ma trận nghiệm thu

| Case | Kỳ vọng khi triển khai |
|---|---|
| Anonymous / user khác track / URL event-track không khớp | Không có file bytes; 401/403/404 theo contract |
| Thành viên đúng track, APPROVED, released | Đúng file/checksum, headers tải đúng |
| PENDING/REJECTED/DISQUALIFIED hoặc đã rời team | Không tải theo membership đó |
| Chưa release hoặc sau retract | Request mới không có file bytes |
| Judge/Mentor không có quyền khác | Không được đặc cách tải |
| Coordinator event khác | Bị chặn theo policy scope mục tiêu |
| System Admin preview trước release | Theo quyết định preview; không tự có endpoint quản lý |
| Replace đang released | Giữ hành vi hiện tại; file mới xuất hiện sau commit |
| S3 lỗi / object thiếu / DB rollback sau upload | Lỗi an toàn; không mất reference cũ do xóa sớm |
| Truy cập S3 anonymous / truyền key track khác | Bị chặn; browser không vượt API guard |
| Redeploy backend | File vẫn tồn tại, đọc đúng reference |

Chưa case nào ở bảng này được ghi là đã pass trên AWS.

## 6. Metadata AI job đề xuất

### 6.1. Tối thiểu và version để tái lập

Tên bảng/cột sau đây là thiết kế, chưa phải migration đã chạy.

| Field | Kiểu logic / null | Quy tắc |
|---|---|---|
| `jobId` | UUID, bắt buộc | Backend tạo; khóa chính, không phải bằng chứng quyền |
| `submissionId` | FK, bắt buộc | Xác minh quyền tạo trước khi nhận yêu cầu |
| `requesterUserId` | FK, bắt buộc | Lấy từ principal; chỉ audit/owner, không quyền vĩnh viễn |
| `repoUrlSnapshot` | URL nội bộ, bắt buộc cho repo-analysis | Repo đã validate tại lúc chụp input; không lấy lại URL mutable khi worker chạy |
| `commitSha` | Full hash, bắt buộc cho repo-analysis | V1 resolve trước khi nhận job; không dùng tên branch làm phiên bản |
| `status` | QUEUED/RUNNING/SUCCEEDED/FAILED/EXPIRED | Backend quyết định, không nhận state trực tiếp từ browser |
| `createdAt` | UTC instant, bắt buộc | Giờ server |
| `startedAt` | UTC instant, nullable | Có sau tín hiệu worker nhận; không suy ra từ lúc frontend polling |
| `finishedAt` | UTC instant, nullable | Có ở trạng thái cuối |
| `resultKey` | String, nullable | Có khi SUCCEEDED; chỉ key do backend kiểm chứng |
| `errorCode` | Enum/string, nullable | Mã ổn định, không có secret |
| `errorMessage` | String giới hạn độ dài, nullable | Thông báo đã làm sạch; không stack trace/path nội bộ |
| `analysisVersion` | String, bắt buộc | Phiên bản input/cấu hình phân tích; không đồng nghĩa model version |
| `inputSnapshotJson`, `inputHash` | JSON/TEXT nhỏ và SHA-256 | Description/link/rubric snapshot để dispatcher tái tạo input; không chứa whole repo/credential |
| `inputKey` | String, nullable trước dispatch | Private S3 JSON input được worker đọc |
| `schemaVersion`, `promptVersion`, `rubricVersion` | String, bắt buộc | Worker/backend kiểm tra hợp đồng |
| `requestedModelVersion`, `actualModelVersion` | String | Có revision/adapter nếu dùng; backend đối chiếu model worker thực sự chạy |
| `deadlineAt` | UTC instant, bắt buộc | Hạn xử lý job, khác hạn judging |
| `resultVersionId`, `resultChecksum` | Nullable | Ghim chính xác result đã kiểm chứng; version ID khi bật Versioning |

Các field vận hành để BE2 cùng chốt: `dispatchStatus/dispatchedAt`, `idempotencyKey`, `attemptId` của event/result, `acceptedAttemptId`, `rowVersion`, `resultExpiresAt`. Index theo `(requesterUserId, createdAt)`, `(submissionId, createdAt)` và `(status, deadlineAt)`; ràng buộc idempotency theo requester + key. Không tạo unique theo submission đơn lẻ vì nhiều judge/lần chạy hợp lệ.

V1 chỉ nhận GitHub repo thuộc phạm vi cho phép và commit resolve được; chưa hỗ trợ private repo nếu chưa có cơ chế cấp quyền an toàn. Không resolve được thì trả lỗi trước khi nhận job. Nếu nhóm cần text-only hoặc resolve bất đồng bộ, phải mở rộng contract/state rõ, không giả vờ commit đã được ghim.

Snapshot phải bao gồm nội dung mô tả và rubric thực tế, không chỉ số version. Revalidate quyền và phiên bản submission/criteria trước khi commit tạo job để tránh nhận snapshot đã thay đổi trong lúc resolve SHA. Bài nộp/rubric thay đổi tạo analysisVersion mới; không ghi đè kết quả cũ.

### 6.2. State machine và nhất quán

```text
QUEUED → RUNNING → SUCCEEDED
   │         ├──→ FAILED
   ├─────────┴──→ EXPIRED
   └────────────→ FAILED (lỗi không thể xử lý/phát job)
```

- API trả 202 khi job/snapshot đã ghi bền; QUEUED có thể đang chờ dispatcher gửi SQS. Dispatcher phải khôi phục được sau restart.
- Lưu job và trạng thái dispatch trong cùng DB transaction; dispatcher upload input từ snapshot bền rồi gửi SQS; chỉ đánh dấu dispatched sau khi gửi thành công. Crash giữa send và đánh dấu có thể gửi trùng, không làm mất job. Có thể thay bằng transactional outbox riêng nếu BE2 chọn.
- Standard SQS có thể giao trùng; xử lý idempotent theo job/version, không tuyên bố exactly-once. Xem [AWS at-least-once delivery](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues-at-least-once-delivery.html).
- Worker ghi result bằng key có `jobId/analysisVersion/attemptId`; không cùng ghi đè một key chung. Backend chỉ nhận result đúng job, input hash, SHA, schema/model/rubric và prefix; kiểm size/checksum/schema trước SUCCEEDED.
- STARTED có thể đến sau kết quả do khác thứ tự giao; không làm SUCCEEDED quay về RUNNING. Chấp nhận kết quả hợp lệ từ QUEUED nếu STARTED chưa tới; lưu startedAt từ event có kiểm tra, không bịa thời gian.
- Lỗi tạm thời được retry có giới hạn/visibility timeout; không đóng FAILED ngay khi còn retry. Lỗi vĩnh viễn hoặc hết chính sách retry mới FAILED; quá deadline EXPIRED. Consumer đối soát DLQ/deadline, không để job treo mãi.
- Cập nhật terminal bằng conditional update/optimistic lock. Event trùng không tạo side effect trùng; terminal đã chốt không bị ghi đè bởi event trễ. Retry chủ động sau terminal tạo job mới liên kết job cũ nếu cần.
- Job nhận trước hạn judging được phép hoàn tất sau hạn; kết quả chỉ đọc, không kéo dài cửa sổ chấm. Job timeout khác với judging closed.
- Hết retention result không đổi SUCCEEDED thành EXPIRED: EXPIRED là timeout thực thi. API dùng `resultAvailability=PURGED` và trả 410 cho nội dung đã dọn theo policy.
- Không cascade xóa lịch sử AI khi xóa submission/user mà chưa có retention/legal decision. Ưu tiên restrict/soft-delete và quy trình purge có audit.

## 7. Chính sách xem AI và giữ chấm ẩn danh — chờ duyệt

| Vấn đề | Phương án khuyến nghị v1 | Trade-off |
|---|---|---|
| Chia sẻ panel | Chỉ requester còn quyền và coordinator có scope; judge khác không tự xem | Giữ đánh giá riêng, dễ kiểm quyền; có thể tốn nhiều lượt inference |
| Assignment bị thu hồi | Chặn đọc/tạo từ request tiếp theo; giữ job/result cho audit theo retention | Bảo vệ quyền hiện tại; không thu hồi được bản đã đọc/tải |
| Hết giờ hoặc tạm dừng | Cho xem lịch sử riêng nếu tài khoản/assignment còn hợp lệ; không tạo job mới | Phù hợp read-only history; phải phân biệt với tài khoản bị vô hiệu hóa |
| Chia sẻ sau chấm | Ngoài scope v1; chỉ xét sau khi khóa kết quả chính thức | Hết timer không đồng nghĩa finalization, timer có thể được mở lại |
| Judge thay thế | Không tự nhận quyền đọc job của người cũ | Nếu cần chuyển giao phải có quyết định và audit riêng |
| Điểm chính thức | AI không ghi Score/RoundResult, không auto-fill điểm final | Judge chịu trách nhiệm đánh giá; vẫn cần cảnh báo ảnh hưởng từ gợi ý AI |

Create job của judge: user hợp lệ → submission tồn tại trong scope → assignment active đúng round/track → judging open → kiểm quota/snapshot. Đề xuất áp dụng judging-open cả coordinator tạo job v1; đây là **siết thêm so với nhánh coordinator hiện tại** và phải được Lead xác nhận. Nếu cần chạy thử trước chấm, dùng luồng demo riêng, không nới quyền ngầm.

Read status/result: user hợp lệ → load job → kiểm quyền hiện tại trên submission → requester hoặc coordinator đúng scope → trả DTO phù hợp. Role System Admin/Mentor/Participant không tự cấp quyền đọc raw AI result. Global audit access nếu cần phải có grant rõ.

**Phát hiện cần xử lý nhất quán:** `ScoringService.getScoresBySubmission` hiện lấy toàn bộ scores của submission sau guard assignment, DTO có judgeName và isDraft. Nếu chọn chấm độc lập, phải lọc quyền xem điểm của judge, không chỉ giữ AI riêng. `getMyScoresByRound` cũng cần review chính sách đọc sau thu hồi assignment. Đây là phát hiện từ source, chưa phải kết luận khai thác endpoint/UI. Nguồn: [ScoringService](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/service/ScoringService.java#L323).

Frontend chỉ nhận allowlist: jobId, anonymous submission code, status, timestamps cần thiết, resultAvailability, safe error, summary/strengths/concerns/criteriaInsights, repo facts đã làm sạch, disclaimer và model display version. Không trả requester identity, tên đội/thành viên, email, GitHub owner/repo URL, commit author, bucket/key/version nội bộ, token hoặc stack trace.

Ẩn field tên đội chưa đảm bảo blind hoàn toàn: README, đường dẫn, code snippet và đầu ra model có thể chứa danh tính. Dùng input sanitization + output filtering/validation, không render HTML model, không tạo link repo nhận diện. Nếu chưa kiểm chứng đầy đủ, gọi là **ẩn danh ở mức ứng dụng**, không cam kết ẩn danh tuyệt đối. Xem [DTO hiện tại](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/dto/response/AiInsightResponse.java), [OWASP authorization](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html).

## 8. Hợp đồng cần Frontend / AI / BE2 review

Các API sau là **route đề xuất mới**, không phải route đang có. FE hiện dùng POST `/api/ai/submissions/{id}/insights` đồng bộ.

| API | Kết quả / quy tắc |
|---|---|
| POST `/api/ai/submissions/{id}/jobs` | 202 + jobId/status sau khi ghi bền; requester lấy từ phiên, client không chọn SHA/resultKey/status |
| GET `/api/ai/jobs/{jobId}` | Status DTO; kiểm quyền mọi lần, không chỉ khi tạo |
| GET `/api/ai/jobs/{jobId}/result` | 200 JSON đã lọc khi thành công/còn lưu; 409 khi chưa sẵn sàng; 410 nếu đã purge và caller còn quyền |

Áp dụng wrapper ApiResponse hiện có khi triển khai; mẫu bên dưới là phần `data` để review. Với job không thuộc scope, đề xuất 404 để không xác nhận sự tồn tại; 401 khi chưa đăng nhập. POST vẫn phải qua cookie/CSRF flow hiện hữu, không mở bypass riêng cho AI.

```json
{
  "jobId": "6c30f603-8312-4cf9-aef8-c7033f551a60",
  "submissionCode": "SUB-024",
  "status": "QUEUED",
  "createdAt": "2026-10-20T08:00:00Z",
  "startedAt": null,
  "finishedAt": null,
  "resultAvailability": "NOT_READY",
  "error": null
}
```

Thông điệp job nội bộ tối thiểu, không đưa requester identity/secret/whole repo vào queue:

```json
{
  "schemaVersion": "ai-job.v1",
  "jobId": "6c30f603-8312-4cf9-aef8-c7033f551a60",
  "analysisVersion": "analysis-001",
  "inputKey": "ai-inputs/6c30f603-8312-4cf9-aef8-c7033f551a60/input.json",
  "inputSha256": "<sha256-cua-input-snapshot>",
  "deadlineAt": "2026-10-20T08:20:00Z"
}
```

Input JSON private chứa repo URL đã validate, full SHA, description/rubric snapshot đã làm sạch và model/prompt versions. Mẫu deadline 20 phút chỉ minh họa cấu hình chờ benchmark, không phải SLA đã đo.

Result event thành công:

```json
{
  "schemaVersion": "ai-event.v1",
  "eventType": "SUCCEEDED",
  "jobId": "6c30f603-8312-4cf9-aef8-c7033f551a60",
  "analysisVersion": "analysis-001",
  "attemptId": "attempt-001",
  "inputSha256": "<sha256-cua-input-snapshot>",
  "resultKey": "ai-results/6c30f603-8312-4cf9-aef8-c7033f551a60/analysis-001/attempt-001.json",
  "resultSha256": "<sha256-cua-result>",
  "startedAt": "2026-10-20T08:00:10Z",
  "finishedAt": "2026-10-20T08:02:30Z"
}
```

Result object có job/version/input hash/commit SHA/model/rubric/schema provenance và payload tương thích AiInsight đã lọc. Provenance nội bộ không được trả nguyên cho judge. Nếu giữ `suggestedScoreRange` dạng chuỗi như DTO hiện tại thì validate định dạng/range theo maxScore; thay sang số min/max phải version hóa contract, không tự phá FE.

FE: polling khởi đầu 3 giây, backoff tối đa 15 giây, dừng khi terminal/tab ẩn/mất quyền; không hiển thị percentage nếu worker chưa đo được. Lỗi an toàn gồm REPO_UNAVAILABLE, INPUT_INVALID, WORKER_TIMEOUT, MODEL_UNAVAILABLE, RESULT_INVALID, STORAGE_UNAVAILABLE; FE xử lý 401/404/409/410/429 và retry có kiểm soát.

AI: chỉ đọc code tĩnh theo allowlist/giới hạn dung lượng; không chạy build, hook hay script repo. Nội dung repo là dữ liệu, không được đổi rubric/hệ thống theo prompt injection. Credentials worker dùng phiên ngắn hạn theo phương án Lead cấp, không đóng gói access key lâu dài trong source/image. Worker offline được mô tả thành QUEUED/timeout, không trình diễn kết quả lưu sẵn như một job vừa chạy.

### 8.1. Ma trận kiểm thử hợp đồng AI khi triển khai

| Tình huống | Kết quả mục tiêu |
|---|---|
| Judge được phân công, judging mở, input hợp lệ | 202 sau ghi bền; job gắn requester từ principal, SHA/snapshot đúng |
| Judge khác đoán jobId | Không đọc status/result; không lộ team/requester |
| Coordinator event khác | Không được đọc/tạo chỉ vì có role coordinator |
| Requester bị revoke sau khi tạo, worker vẫn đang chạy | Requester bị chặn ở poll/result; dữ liệu audit vẫn giữ; worker không cấp lại quyền |
| Judging đóng, requester còn quyền | Job cũ chỉ đọc; tạo mới bị chặn; không sửa điểm theo kết quả đến muộn |
| Guest judge/account không còn hợp lệ | Bị chặn theo policy account, kể cả job do chính họ từng tạo |
| Client gửi requesterId/status/resultKey hoặc SHA tùy ý | Không tin các giá trị để cấp quyền hoặc cập nhật state; DTO request không cho chọn |
| Lặp POST cùng requester/idempotency key và cùng payload | Không tạo job/side effect trùng; cùng key khác payload bị từ chối |
| Submission/rubric/commit thay đổi | Input/version mới; result cũ không giả là phân tích mới nhất |
| Crash sau DB commit, trước gửi queue; hoặc gửi xong trước đánh dấu | Dispatcher phục hồi; có thể gửi trùng nhưng không mất job hoặc nhận kết quả hai lần |
| STARTED đến sau SUCCEEDED; result trùng | Không regress terminal; không ghi Score hoặc phát side effect trùng |
| Result key khác job, hash/SHA/version sai, JSON sai hoặc object quá lớn | Không chấp nhận SUCCEEDED; lỗi an toàn/quarantine theo chính sách |
| Worker offline, retry hết hoặc DLQ | Có timeout/terminal được đối soát; không treo RUNNING mãi |
| Result đã purge theo retention | Caller còn quyền nhận 410; state thực thi vẫn SUCCEEDED, không nhầm timeout |
| README/code chứa danh tính hoặc prompt injection | Không đổi rubric/hệ thống, không render HTML/link nhận diện; ghi giới hạn nếu chưa lọc đủ |

Đây là tiêu chí tuần 2, **chưa chạy** trên stack AWS/local worker mới. Bộ 195 unit tests hiện có không thay cho bảng này.

## 9. Migration từ MySQL local và file local

### 9.1. Điều kiện trước khi thực hiện

- Đã đọc được metadata và core counts qua Workbench local. Để kiểm tiếp dùng kết nối được cấp, ưu tiên read-only; không thử nhiều mật khẩu hoặc yêu cầu gửi secret trong chat.
- Xác nhận dữ liệu thật phải giữ hay chỉ demo; người cho phép cutover; nơi backup bảo mật; maintenance window.
- Server/case/timezone/counts/ba unique đã xác nhận ở mục 2.5. Phần còn thiếu: schema-only export, diff đầy đủ cột/FK/index/delete rules, inventory các DB cần giữ và orphan checks khi có dữ liệu. [SQL kiểm tra chỉ đọc](../checks/data-storage-runtime-audit.sql) là công cụ bổ sung, chưa được chạy toàn bộ.
- Kiểm tra đường UPLOAD_DIR/PROBLEM_DIR runtime, phân loại avatar hosted/OAuth và lập manifest object. Không thấy thư mục mặc định không đồng nghĩa server không có file.

Migration = chuyển cấu trúc/dữ liệu và cách tham chiếu sang môi trường đích có kiểm soát. Backup = giữ bản sao phục hồi; restore = dùng bản sao đó dựng lại. Schema-only export chỉ có cấu trúc, **không phải backup dữ liệu**. Baseline = mốc cấu trúc đã đối chiếu để bắt đầu quản lý thay đổi; không phải thao tác reset DB.

### 9.2. Nhánh DB mới/demo và DB có dữ liệu

| Bước | DB mới, không cần giữ demo | DB có dữ liệu cần giữ |
|---|---|---|
| Baseline | Tạo migration schema từ model đã review; thử trên DB rỗng | Dump/snapshot và restore sang DB thử; so schema/data thực tế với entity trước baseline |
| Import | Không nạp SQL seed cũ tự động | Import schema/data đã kiểm tra tương thích; giữ PK/FK và auto-increment hợp lệ |
| Migration history | Chạy versioned migration từ đầu | Baseline có chủ đích trên schema đã xác minh rồi chạy migration mới; không bật baselineOnMigrate để che drift |
| Runtime | App user DML, `DDL_AUTO=validate`, seed demo NONE | Như DB mới; không cho app tự ALTER dữ liệu thật |
| Nghiệm thu | Luồng nghiệp vụ và bootstrap an toàn | Thêm đối chiếu counts, constraints, Unicode, timestamps, hashes và file references |

Chọn phiên bản RDS còn hỗ trợ sau compatibility test; không nâng major trực tiếp trên DB gốc. Tên database/table, SQL mode, charset/collation và reserved words cần giữ/điều chỉnh bằng migration có review. Dùng kết nối TLS có xác minh server/CA; không bê `useSSL=false` từ local lên RDS. Xem [AWS RDS MySQL TLS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/MySQL.Concepts.SSLSupport.html).

**Lựa chọn cho dự án:** nghiêng về nhánh DB mới cho môi trường workshop nếu nhóm xác nhận không có dữ liệu cần giữ. Counts rỗng ở local sau reset chưa đủ để quyết định thay dữ liệu trên máy/deploy khác. Nếu còn dữ liệu cũ ở nơi khác hoặc chưa biết, giữ nhánh bảo toàn và không reset/ghi đè nguồn.

### 9.3. Di chuyển file và cutover

1. Dừng hoặc chặn ghi nghiệp vụ trong maintenance window nhỏ đã duyệt; bảo đảm không có upload nửa chừng. Phương án demo chấp nhận downtime, không hứa zero-downtime.
2. Backup DB và file; manifest gồm loại file, owner/track ID, đường local, key mới, size, SHA-256, metadata/release và version ID khi có.
3. Copy file vào private S3 bằng key mới. Đối chiếu checksum/size; không dùng ETag như SHA-256 mặc định. Avatar URL ngoài không copy.
4. Cập nhật reference bằng migration/chương trình có dry-run và audit; giữ nguyên release/membership. Chỉ cập nhật sau khi object đã xác minh.
5. Trỏ backend tới RDS/S3 bằng cấu hình được cấp, chạy validate và smoke test trước mở ghi. RDS private không được mở 3306 ra Internet chỉ để import từ laptop; dùng đường quản trị được Lead cấp.
6. Kiểm login, team theo mùa, nộp bài, chấm, upload/download, retract, AI job khi đã triển khai; kiểm lại sau redeploy.
7. Giữ DB/file cũ read-only tới hết thời hạn rollback được Lead duyệt. Không xóa gốc chỉ vì copy đã hoàn tất.

Rollback trước khi mở ghi: về endpoint/image/reference cũ đã kiểm. Nếu hệ mới đã nhận ghi, phải đóng ghi và reconcile/export phần phát sinh trước khi quay lại; rollback image không tự undo migration hoặc đồng bộ dữ liệu.

### 9.4. Quy trình chốt baseline sau phát hiện lệch schema

| Thứ tự | Việc cần thực hiện | Đầu ra / điều kiện qua bước |
|---|---|---|
| 1 — Chụp hiện trạng | Export schema-only DB local vào file mới; lưu query results và mốc sau reset; backup data/file riêng nếu có dữ liệu cần giữ | Không ghi đè seal_schema.sql; không thực thi dump; biết rõ đây là snapshot nào |
| 2 — Chọn nguồn dữ liệu | Lead/owner xác nhận chỉ demo hay còn DB cần giữ ở máy/deploy khác | Danh sách môi trường + quyết định bảo toàn; không suy từ local rỗng |
| 3 — Dựng schema ứng viên | Review DDL theo entity, hoặc sinh trên DB thử riêng với cấu hình cô lập, không trỏ app vào DB gốc; kiểm soát seed/side effects | Có candidate schema; không chạy run-demo để làm sạch DB nguồn |
| 4 — Diff và review | So candidate với export; xử lý MentorSupportRequest, ba cột Notification, bảng legacy, indexes/FKs, default/nullability, naming/case và timestamps | Bảng diff đầy đủ được BE/Lead review; không chỉ sửa bốn điểm đã thấy rồi gọi là hoàn tất |
| 5 — Chọn cơ chế history | DB mới dùng initial migration; DB cũ chọn mốc phản ánh chính xác schema đang có, thêm migration nâng cấp sau mốc | Không đánh dấu baseline v1 cho schema lệch rồi coi như đã đồng bộ |
| 6 — Thử DB rỗng | Chạy migration bằng identity riêng; kiểm backend validate và luồng nghiệp vụ bằng dữ liệu thử | Migration dựng đúng schema, test rerun không áp dụng lại version đã chạy |
| 7 — Thử DB có dữ liệu | Restore backup sang DB thử, chạy chuỗi migration và so dữ liệu/quyền/file references | Không mất dữ liệu cần giữ; đo restore/rollback; nếu chưa có dataset thì dùng fixture và ghi đúng giới hạn |
| 8 — Duyệt triển khai | Lead review schema/data diff, test, backup/rollback và estimate | Mới được cutover RDS; ứng dụng runtime chỉ validate schema |

Ví dụ version để nhóm review, chưa tạo file SQL chạy thật:

```text
V1__initial_schema.sql         Cấu trúc khởi đầu đã review
V2__add_ai_job.sql             Metadata AI job
V3__add_s3_file_metadata.sql   Metadata/provider cho file
```

V1 có thể chứa schema hiện tại đã sửa đủ cho backend; khi đó chỉ được đánh dấu DB đang có ở version 1 sau khi nó thực sự tương ứng. Nếu schema legacy cần bảo toàn khác V1, chọn baseline phản ánh legacy rồi dùng migration nâng cấp riêng; số version trên chỉ minh họa, không ép mọi DB vào cùng mốc sai.

Lệnh Flyway `baseline` ghi mốc vào history, không chạy lại migration tới mốc đó và không tự xác minh/sửa toàn bộ schema. `V1` là versioned initial migration; file `B...` là cơ chế baseline migration tích lũy khác, chưa cần cho workshop đầu tiên. Tham khảo [baseline state](https://documentation.red-gate.com/flyway/flyway-concepts/baselines), [baseline command](https://documentation.red-gate.com/fd/baseline-277578867.html), [B migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/baseline-migrations).

## 10. Backup, retention và restore drill

| Thành phần | Đề xuất v1 | Cần minh chứng |
|---|---|---|
| RDS | Automated backups retention 7 ngày; snapshot trước migration/cutover; encryption và giới hạn quyền restore | Retention thực tế, snapshot ID/time, restore log và smoke test |
| Avatar/đề thi | Versioning; không expire current object còn được DB tham chiếu | Khôi phục bản trước và mapping version/reference đúng |
| AI input/result | Đề xuất giữ 30 ngày sau job terminal, cần Lead duyệt; purge có phối hợp metadata và job chưa chạy | Quyền trước/sau purge; không tự xóa input của job còn active |
| Noncurrent versions | Lifecycle sau thời hạn rollback/retention đã duyệt; không coi Versioning là miễn phí | Dung lượng current/noncurrent và kết quả restore |
| Log/metadata/audit | Log demo 14 ngày là ứng viên; thời hạn audit/metadata chốt riêng, không mặc định cascade | Không log secret/PII dư thừa; deletion có người chịu trách nhiệm |

RDS point-in-time restore tạo **DB instance mới**, không overwrite nguồn; cần kiểm endpoint, SG, parameter và TLS sau restore. RDS restore không đưa bucket S3 về cùng thời điểm. Dùng immutable object keys/version IDs và manifest để đối soát; giữ object đủ lâu cho cửa sổ DB restore. Xem [AWS PITR](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_PIT.html), [S3 Versioning](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Versioning.html).

Restore drill: ghi mốc bắt đầu → restore sang DB riêng → gắn cấu hình test → kiểm counts/FK và Unicode → đọc một avatar, đề thi released/unreleased, một result → kiểm quyền → ghi thời gian và dữ liệu mất thực tế. Không test restore lên DB gốc và không ghi ảnh/chụp chứa credentials.

Mục tiêu demo đề xuất để đo: RPO ≤ 24 giờ, RTO ≤ 2 giờ trong phạm vi dữ liệu đã backup; đây không phải SLA hay kết quả đã đạt. Lead phải chấp nhận mục tiêu/chi phí và đo lại bằng drill. V1 Single-AZ không được trình bày là hệ thống HA.

### 10.1. Backup trước khi rời MySQL local

Một bộ phục hồi tối thiểu cần có: (a) bản backup schema + data của DB nguồn theo quyền được cấp; (b) file avatar/đề thi app sở hữu; (c) manifest key/reference/size/checksum; (d) image commit và migration version; (e) hướng dẫn cấu hình, còn secrets giữ ở kho riêng. File SQL trong Git và code entity không phục hồi được submission/score đã mất.

Giữ backup ngoài thư mục build/container và ngoài Git; mã hóa/giới hạn người đọc vì dump có thể chứa thông tin cá nhân và password hash. Ghi mốc chụp, source DB, phiên bản, checksum và người tạo; không công khai dump hoặc thông tin đăng nhập. Schema-only export hiện còn chờ thực hiện, backup dữ liệu/file và restore thử cũng chưa làm.

Với DB nhỏ của workshop, đề xuất chặn ghi trong khoảng chuyển dữ liệu đã duyệt để chụp DB và file cùng mốc. Chưa có yêu cầu downtime rất thấp nên không thêm DMS/replication chỉ để tăng số dịch vụ.

### 10.2. Kịch bản restore cụ thể để demo

Ví dụ thao tác nhầm xảy ra sau khi hệ thống đã chạy trên AWS:

1. Ghi nhận thời điểm/sự cố và chặn ghi theo quyết định owner; không tiếp tục reset DB. Chọn snapshot hoặc thời điểm phục hồi nằm trong cửa sổ backup thực tế.
2. RDS restore sang **instance mới**, không khôi phục đè DB nguồn; chờ available và kiểm SG/parameter/TLS/endpoint bằng cấu hình đã duyệt.
3. Dùng bản app phù hợp với schema đã restore. Chạy migration thử nếu cần trên DB phục hồi; không để app `update` tự thay schema trước khi đối soát.
4. Đối chiếu dữ liệu với mốc backup; kiểm User/TeamEventEntry/Submission/Score và dữ liệu tiếng Việt. DB mới rỗng để demo không chứng minh giữ được dữ liệu thật: cần fixture nghiệp vụ nếu chưa có data cũ.
5. Kiểm manifest S3 và version từng object: file đề thi/AI result có tồn tại, checksum và release đúng không. Object còn ở S3 chưa đủ nếu reference DB sai; DB restore cũng không làm object bị xóa xuất hiện trở lại.
6. Chạy app thử: login, tải đề đúng track/release, chặn khác track và xem AI đúng quyền. Chỉ sau nghiệm thu mới đổi endpoint cho app dùng thật, theo authority của Lead.
7. Ghi RTO/RPO thực đo, giữ nguồn cũ theo thời hạn rollback được duyệt và cleanup tài nguyên thử khi được cho phép để tránh phí kéo dài.

Automated backups/snapshot là cơ chế backup RDS; restore mới là bước chứng minh dùng được. Versioning giúp giữ các phiên bản object nhưng không thay cho quyền IAM, retention và đối soát DB. Xem [RDS backups](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_WorkingWithAutomatedBackups.html).

### 10.3. Phân biệt restore, rollback và mục tiêu đo

| Thuật ngữ | Ví dụ trong hệ thống |
|---|---|
| Migration | Chuyển MySQL local sang RDS và avatar/đề thi sang S3; thay đổi schema có version |
| Backup | Bản dump/snapshot và bản sao/version file có thể phục hồi |
| Restore | Dựng DB riêng từ backup, đọc lại dữ liệu/file và kiểm quyền |
| Rollback | Quay app/config/luồng phục vụ về trạng thái đã kiểm; có thể cần restore hoặc reconcile, không chỉ đổi image |
| RPO | Mức dữ liệu mất chấp nhận được: ví dụ tối đa 24 giờ, không phải chu kỳ retention 7 ngày |
| RTO | Thời gian từ lúc bắt đầu xử lý đến khi dịch vụ dùng lại được: ví dụ mục tiêu 2 giờ, phải đo |

Không tự replay/requeue AI jobs khi restore: đối soát jobId, deadline, terminal state, queue messages và result objects trước. DB cũ được restore có thể thấy QUEUED trong khi worker đã làm xong; idempotency và reconcile ở BE2/AI phải tránh tác dụng trùng.

## 11. Các phát hiện và thay đổi backend cần thực hiện

| ID / ưu tiên | Hiện trạng đã đọc | Thay đổi đề xuất / nghiệm thu |
|---|---|---|
| SEC-01 / P0 | JWT secret có fallback trong application.properties | AWS profile thiếu/sai định dạng secret phải fail-fast; không log giá trị; rotate secret đã lộ theo owner, xác định ảnh hưởng phiên |
| SEC-02 / P0 | DataSeeder có bootstrap credential cố định và luôn được gọi | Tách bootstrap một lần có opt-in, nguồn secret riêng; không tạo lại/đặt lại password sau restart; rotate/disable account default đã tồn tại theo quyết định |
| DB-01 / P0 | DDL_AUTO mặc định update; runtime sau reset thiếu bảng/cột backend cần; chưa Flyway | Export/diff đầy đủ, review baseline; migration identity riêng; app DML/validate; không đánh dấu baseline để bỏ qua drift |
| DB-02 / P0 trước thao tác dữ liệu thật | run-demo.ps1 có reset DB mặc định; Workbench history đã có DROP/CREATE trước lần khảo sát | Tách script reset demo khỏi deploy/restore; không chạy reset trên DB cần giữ; backup/restore là quy trình riêng |
| STO-01 / P1 | Avatar/problem local, Compose không mount backend file volume | Storage interface + S3 adapter; local adapter cho dev; DB key/metadata/URL ngoài tách rõ |
| STO-02 / P1 | Xóa đề cũ trong transaction trước commit metadata | Upload immutable key; cleanup sau commit, retry/orphan grace period; test rollback không mất file đang tham chiếu |
| STO-03 / P1 | Avatar update chưa dọn ảnh cũ; MIME/đuôi file là kiểm tra chính | Dọn có kiểm soát sau commit; validate định dạng, kích thước và tên file; không thực thi ZIP |
| AUTH-01 / P0 trước public demo | Problem/AI coordinator guard đang chủ yếu theo role ở nhánh đã khảo sát | Guard staff scope event và global role rõ; kiểm cross-event ở service/API |
| AUTH-02 / P1 | Score endpoint lấy scores của mọi judge sau assignment guard | Nếu duyệt chấm độc lập: judge chỉ thấy dữ liệu được cấp; test draft/điểm người khác và revoked history |
| AI-01 / P1 | AI đồng bộ, chưa job persistence | AiJob/repository/API, snapshot, UTC/version, DTO và kiểm quyền mọi lần đọc |
| AI-02 / P1 | Chưa có durable dispatch/result handling trong luồng hiện tại | BE2 phối hợp dispatcher/outbox, SQS/DLQ, validator, idempotency, timeout và số đo |

P0 là mức ưu tiên trong proposal trước khi public demo, không phải kết luận CVSS hoặc bằng chứng khai thác. Vị trí SEC-01: application.properties dòng 45; SEC-02: [DataSeeder](../../back-end/src/seal-api/src/main/java/com/seal/hackathon/config/DataSeeder.java#L42). Không đưa giá trị credential vào tài liệu.

## 12. Năm task backend cho tuần 2

Ước lượng theo giờ tập trung, không phải cam kết tiến độ; 34–48 giờ công BE1 cộng phối hợp/review. Nếu quỹ thời gian thấp hơn, B5 hoàn tất happy path trước, fault hardening chuyển tuần 3 theo Lead duyệt; không gọi demo happy path là đã production-ready.

| Task | Owner / reviewer đề xuất | Công việc và dependency | Ước lượng | Acceptance |
|---|---|---|---|---|
| B1 — Cấu hình an toàn | BE1 / Lead | SEC-01/02 và staff scope P0; Lead cấp nơi giữ secret | 5–7h | Test thiếu secret, bootstrap opt-in/restart; không default credential; cross-event bị chặn |
| B2 — Baseline RDS + restore | BE1 / BE2, Lead | Export/diff sau phát hiện runtime, xác nhận dữ liệu nguồn cần giữ và target version; phụ thuộc B1 | 7–10h | Sửa thiếu MentorSupportRequest/Notification bằng schema đã review; không xóa legacy tùy ý; migration DB rỗng + restore có fixture/data; validate/TLS, app không DDL |
| B3 — S3 avatar/đề thi | BE1 / FE, Lead | Storage adapter, metadata migration, B2 khi đổi schema, bucket/IAM | 8–11h | Pass matrix mục 5; rollback upload an toàn; file tồn tại sau redeploy; private S3 |
| B4 — AI metadata/API/access | BE1 / FE, AI | Schema/API snapshot/state/DTO; B2 và policy được duyệt | 7–10h | Tạo/poll/result; quyền requester/revocation/history; ẩn danh; idempotency và versions |
| B5 — Tích hợp worker/result | BE1 phối hợp BE2 + AI / Lead | B4, input/event schema, worker có sẵn, dispatcher/queue của BE2 | 7–10h BE1 | Một job thật web→worker→S3→RDS; duplicate/out-of-order/timeout/output sai có test; không ghi Score |

Thứ tự: B1 và chốt contract → B2 → B3/B4 khi dependency sẵn sàng → B5. Đây là các work packages để chia PR nhỏ; không giao toàn bộ IaC, GPU/training và SQS consumer cho BE1.

## 13. COST và tối ưu — đầu vào có thể tính, chưa chốt báo giá

Phần đóng góp này không thay cost sheet toàn hệ thống. Chưa có instance size/lịch chạy/account credit được duyệt nên **không đưa số USD giả hoặc khẳng định dưới 500.000 VND/tháng**. Nhóm phải có estimate Calculator trước khi nộp phần trình bày tổng thể.

Giả định workload để nhập Calculator, không phải số liệu production: 200 user, 50 team, 4 track, 2 round, 3 judge/bài, tối đa 300 job cho một lượt mỗi judge/bài. Avatar 0,5 MiB/user ≈ 100 MiB; đề thi 20 MiB/track ≈ 80 MiB; input và result mỗi loại 200 KiB/job ≈ 58,6 MiB/loại. Tổng raw objects ≈ 0,29 GiB trước version/log/overhead; dự trù 1 GiB hiện hành + 1 GiB noncurrent cho estimate ban đầu. Repo/model local không nằm trong con số S3 này.

| Khoản | Đầu vào tính đề xuất | Công thức / kiểm soát |
|---|---|---|
| RDS compute | So sánh class nhỏ tương thích, Single-AZ; 160h demo và 730h liên tục | Giá giờ tại Singapore × giờ chạy; capacity phải benchmark |
| RDS storage/backup | 20 GiB ứng viên ban đầu, retention 7 ngày, snapshot và DB restore tạm | Storage-month + backup/snapshot billable + thời gian restore instance; không tính storage chỉ theo giờ DB chạy |
| S3 | Current/noncurrent GiB-month, GET/PUT/LIST, downloads và result reads | Tính cả version/lifecycle requests và egress tới worker local |
| SQS | Jobs, STARTED/result, deletes, retries, visibility và empty receives | Tính request thật; long polling/backoff; không coi một job là một request |
| Secret/log/KMS | Số parameter/key, API calls, log ingest/retention | Giới hạn log; SSE-KMS nếu dùng có chi phí riêng |
| Ngoài phần dữ liệu | EC2/EBS/IPv4/CloudFront/SES/DNS | Lead cộng vào tổng, tránh bỏ sót vì BE1 chỉ báo RDS/S3 |

Tối ưu v1: không thêm Aurora/Redis/OpenSearch/DMS khi chưa có nhu cầu; dùng dump/restore có maintenance window cho DB nhỏ; S3 Standard trước, lifecycle sau số đo; quota job và reuse đúng owner/snapshot; Single-AZ minh bạch giới hạn. Credit không phải giảm giá dịch vụ: tách chi phí trước credit/sau credit, kiểm điều kiện/hạn dùng thực tế. Ngân sách cảnh báo không phải hard cap.

Nguồn giá cần dùng khi chốt: [AWS Pricing Calculator](https://calculator.aws/), [RDS MySQL pricing](https://aws.amazon.com/rds/mysql/pricing/), [S3 pricing](https://aws.amazon.com/s3/pricing/), [SQS pricing](https://aws.amazon.com/sqs/pricing/). Đã đối chiếu nguồn ngày 20/10/2026; chưa tạo Calculator estimate được nhóm phê duyệt.

## 14. Quyết định còn mở và nghiệm thu bàn giao

| ID | Cần quyết định | Khuyến nghị / trạng thái | Người cần xác nhận |
|---|---|---|---|
| Q-DB | Schema đích và dữ liệu cần giữ | Đã đọc qua Workbench: DB sau reset lệch backend, core counts rỗng; còn chờ full export/diff và xác nhận data ở môi trường khác. User yêu cầu giải thích migration/backup trước khi chọn nhánh | User / DB owner / Lead |
| Q-AV | Avatar public qua app? | Giữ hành vi public qua API; bucket private | FE / Lead |
| Q-PR | Retract/replace và staff scope | Stream; giữ release semantics hiện tại; siết scope staff rõ | Lead / BE / FE |
| Q-AI | Requester/panel, revoke, history | Theo mục 7; đã thảo luận với user, chưa có xác nhận nhóm | Lead / FE / AI |
| Q-SHA | Ai resolve SHA và input contract? | BE resolve trước nhận job; AI chạy đúng snapshot; mẫu mục 8 | BE1 / AI / BE2 |
| Q-RET | Retention và xóa dữ liệu | RDS 7 ngày backup, AI objects 30 ngày là đề xuất, không tự áp dụng | Lead / DB owner |
| Q-COST | Size, lịch chạy, credit, báo giá | Phải chốt Calculator estimate toàn hệ thống trước presentation | Lead / BE2 |

Checklist bàn giao tài liệu: có kết quả khảo sát source + runtime một phần, bảng quyền, metadata, luồng đề thi, migration/restore, security backlog, 5 task và nguồn. Chưa nghiệm thu: full schema export/diff, schema baseline khớp backend, dữ liệu cần giữ ở môi trường khác, file manifest, FE/AI sign-off, test AWS, restore drill và cost estimate.

Bản để ghép vào proposal chung: [đóng góp Backend 1](data-storage-access-proposal.md). Phần metadata/quyền cần đưa tới nhóm bằng [phiếu review Frontend/AI](ai-job-review-handoff.md); hiện chưa có xác nhận, không đánh dấu hoàn tất tiêu chí “đã trao đổi với FE/AI”.

Để trình bày: (1) SQL giữ nghiệp vụ/quyền, S3 giữ bytes; (2) đổi storage không bỏ guard team/track/release; (3) job ghim snapshot/SHA, quyền đọc được kiểm lại; (4) migration có backup và rollback; (5) minh bạch giới hạn Single-AZ/local worker và những việc còn chờ nhóm duyệt.

## 15. Đoạn trình bày về migration và khôi phục

“Qua source và kiểm tra MySQL local, nhóm xác nhận dữ liệu quan hệ sẽ giữ trong RDS, còn nội dung avatar/đề thi và output AI lưu ở S3. DB local vừa được tạo lại từ SQL cũ và còn lệch entity, nên nhóm chưa dùng ngay nó làm baseline. Nhóm sẽ export, đối chiếu schema, review cấu trúc chuẩn rồi thử migration trên DB riêng. Nếu chỉ cần demo, dựng DB mới bằng migration và dữ liệu thử có kiểm soát; nếu có dữ liệu cũ cần giữ, backup và thử restore trước khi chuyển. Sau triển khai, dùng backup RDS, snapshot trước thay đổi và versioning cho object cần phục hồi. Khi có lỗi, restore DB sang instance mới và kiểm lại cả dữ liệu, file và phân quyền trước khi đổi endpoint. Hiện đây là kế hoạch, chưa có kết quả restore drill hoặc sign-off triển khai.”
