# AI Session Log — Gọn hoá `database scripts` về đúng 3 file · DATABASE

**Date:** 2026-06-26
**Project:** SEAL – Software Engineering Hackathon Management System
**Branch:** `develop`
**Scope (DB):** Refactor thư mục `back-end/database scripts/` từ **10 file → đúng 3 file** (`seal_schema.sql`, `seal_seed.sql`, `seal_scripts.sql`). Nguyên tắc: các file SQL "thêm sau" chỉ dùng để **update DB đang chạy phục vụ test**; trước khi xoá phải **verify** rằng schema + seed đã hấp thụ đầy đủ thay đổi của chúng, chỗ nào còn thiếu thì **gộp vào** rồi mới xoá.

> Tài liệu đọc để lấy context: toàn bộ 10 file trong `back-end/database scripts/` (schema, seed, scripts, 4 migration, 3 seed/demo phụ).

---

## PHẦN 0 — CONTEXT & CHỐT YÊU CẦU

### 0.1. Hiện trạng thư mục (10 file, 2127 dòng)
| File | Dòng | Vai trò |
|---|---|---|
| `seal_schema.sql` | 502 | DDL chính, idempotent (DROP & recreate DB), khai báo "23 tables" |
| `seal_seed.sql` | 858 | Seed hợp nhất — sở hữu toàn bộ user rows, event/track/round/team/score… |
| `seal_scripts.sql` | 368 | Query inspect + nghiệp vụ (PHẦN A `SELECT *`, PHẦN B query) |
| `migration_announcement.sql` | 57 | Thêm bảng `Announcement` + `Notification.announcement_id` |
| `migration_track_problem.sql` | 27 | Thêm 7 cột `problem_*` vào `Track` |
| `migration_criteria_template.sql` | 42 | Seed 2 template + 9 item tái dùng |
| `migration_systemlog_drop_ip_address.sql` | 4 | Bỏ cột `ip_address` khỏi `SystemLog` |
| `seal_seed_4teams.sql` | 102 | Add-on: user 28-43, team 14-17 (event 2) |
| `seal_seed_ai_demo.sql` | 88 | Backfill `description` cho submission 1-12 (event 1) |
| `seal_demo_ai_repo.sql` | 79 | Overlay demo: trỏ sub 1/2/4 sang repo GitHub thật |

### 0.2. Bản chất "10 file" — vì sao có migration & seed phụ
`application.properties` đặt `ddl-auto=none` ⇒ **DB là source of truth**, Hibernate không tự đổi schema. Mỗi feature thêm sau (announcement, track-problem, criteria-template…) phát sinh **một file migration non-destructive** để áp lên DB đang chạy mà không phải DROP & re-seed. Các file `seal_seed_*`/`seal_demo_*` là dữ liệu test bổ sung tích luỹ theo thời gian. Hệ quả: thư mục phình ra, chồng chéo, khó biết "chạy file nào, theo thứ tự nào".

### 0.3. Yêu cầu chốt
- **Đích:** còn **đúng 3 file** `seal_schema.sql`, `seal_seed.sql`, `seal_scripts.sql`.
- `seal_scripts.sql` **chưa cần sửa** lần này.
- **Điều kiện trước khi xoá:** verify schema + seed đã được update theo từng file thêm sau; thiếu thì gộp.

---

## PHẦN 1 — VERIFY COVERAGE (cốt lõi của phiên)

Đối chiếu từng file "thêm sau" với nội dung hiện tại của `seal_schema.sql` / `seal_seed.sql`:

| File thêm sau | Đích | Đã có sẵn? | Bằng chứng |
|---|---|---|---|
| `migration_track_problem.sql` | cột `problem_*` của `Track` | ✅ **Có** trong schema | `seal_schema.sql` L104-115 |
| `migration_systemlog_drop_ip_address.sql` | bỏ `ip_address` khỏi `SystemLog` | ✅ **Có** trong schema | `SystemLog` (L465-476) không còn cột; `ip_address` còn lại ở L448 là của **AuditLog** (cố ý giữ) |
| `migration_criteria_template.sql` | template 1-2 + 9 item | ✅ **Có** trong seed | seed §9 (L533-535) + §10b (L568-579) |
| `seal_seed_4teams.sql` | user 28-43, team 14-17 | ✅ **Có** trong seed | seed §2/§7/§8; chính file add-on cũng ghi "seal_seed.sql now ALREADY contains these" |
| `migration_announcement.sql` | bảng `Announcement` + `Notification.announcement_id` | ❌ **THIẾU** ở schema | grep `Announcement`/`announcement_id` trong schema = 0 match |
| `seal_seed_ai_demo.sql` | `description` cho submission 1-12 | ❌ **Chưa** ở seed | seed §11 `INSERT INTO Submission(... )` không có cột `description` |
| `seal_demo_ai_repo.sql` | repo thật cho sub 1/2/4 | ❌ **Chưa** ở seed, và **xung đột** với `ai_demo` (cùng ghi đè sub 1/2/4) | — |

**Kết luận verify:** 4/7 đã hấp thụ đầy đủ → xoá an toàn. 3/7 còn thiếu → phải gộp trước khi xoá.

### 1.1. Điểm cần người dùng quyết — xung đột 2 file AI demo
`seal_seed_ai_demo.sql` và `seal_demo_ai_repo.sql` **không thể gộp cả hai** vào một seed thống nhất vì chúng cùng ghi đè `description` của sub 1/2/4 theo hai hướng khác nhau:
- `ai_demo`: mô tả **nhất quán narrative seed** (sub 1 = Team Phoenix "SmartDorm" — app KTX).
- `demo_ai_repo`: cố ý đổi sub 1 thành **"PetClinic"** + trỏ repo GitHub thật để khối **"Repository Analysis"** của AI Judge Assistant có dữ liệu sống.

→ Đã hỏi người dùng (`AskUserQuestion`). **Lựa chọn: "Demo-ready (repo thật)"** — gộp `demo_ai_repo` cho sub 1/2/4 + `ai_demo` cho sub 3, 5-12.

**Đánh đổi đã được chấp nhận:** sub 1 ở vòng sơ khảo mang mô tả **PetClinic**, trong khi sub 6/10 (Phoenix semi/final, lấy từ `ai_demo`) vẫn là **SmartDorm** → lệch narrative cố hữu khi dùng repo thật. Chấp nhận vì demo repo-analysis chỉ dùng round 1.

---

## PHẦN 2 — THAY ĐỔI `seal_schema.sql` (gộp `migration_announcement.sql`)

### 2.1. Header & changelog
- `-- 23 tables` → `-- 24 tables`.
- Thêm một block `CHANGELOG (announcements — folds in migration_announcement.sql)` mô tả lý do thêm `Announcement` + `Notification.announcement_id` (giữ dấu vết nguồn gốc cho người đọc sau).

### 2.2. Thêm bảng `Announcement` — **đặt TRƯỚC `Notification`**
Thứ tự quan trọng: `Notification.announcement_id` là FK trỏ tới `Announcement`, nên `Announcement` phải được `CREATE` trước. Đặt ngay sau header section **COMMUNICATION & AUDIT**, trước `CREATE TABLE Notification`.

```sql
CREATE TABLE Announcement (
  announcement_id INT          NOT NULL AUTO_INCREMENT,
  sender_user_id  INT          NOT NULL,
  sender_role     VARCHAR(20)  NOT NULL COMMENT 'MENTOR, COORDINATOR',
  scope           VARCHAR(20)  NOT NULL COMMENT 'TRACK, EVENT',
  audience        VARCHAR(20)           COMMENT 'PARTICIPANT, JUDGE, MENTOR',
  event_id        INT          NOT NULL,
  track_id        INT                   COMMENT 'NULL when scope = EVENT',
  title           VARCHAR(255) NOT NULL,
  content         TEXT,
  link_url        VARCHAR(1000)         COMMENT 'Optional attachment link',
  recipient_count INT          NOT NULL DEFAULT 0,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (announcement_id),
  KEY idx_ann_sender (sender_user_id),
  KEY idx_ann_event  (event_id),
  KEY idx_ann_track  (track_id),
  CONSTRAINT fk_ann_sender FOREIGN KEY (sender_user_id) REFERENCES `User` (user_id),
  CONSTRAINT fk_ann_event  FOREIGN KEY (event_id)       REFERENCES HackathonEvent (event_id),
  CONSTRAINT fk_ann_track  FOREIGN KEY (track_id)       REFERENCES Track (track_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

> **Lưu ý gộp:** trong file migration gốc, cột `audience` và `link_url` được thêm bằng các `ALTER TABLE` phụ (để vá DB cũ đã tạo bảng thiếu cột). Khi gộp vào schema canonical, hai cột này được đưa **thẳng vào `CREATE TABLE`** — không cần `ALTER` riêng.

### 2.3. Sửa `Notification` — thêm back-link
Thêm cột `announcement_id` (sau `type`), index, và FK:
```sql
  announcement_id   INT  COMMENT 'Set only for ANNOUNCEMENT notifications; ... NULL otherwise',
  ...
  KEY idx_notif_announcement (announcement_id),
  CONSTRAINT fk_notif_announcement FOREIGN KEY (announcement_id) REFERENCES Announcement (announcement_id)
```

---

## PHẦN 3 — THAY ĐỔI `seal_seed.sql` (gộp `ai_demo` + `demo_ai_repo`)

Block §11 `INSERT INTO Submission` được viết lại: thêm cột **`description`** vào danh sách cột và mỗi VALUES; cập nhật `repo_url` cho 3 submission demo. Mapping cuối cùng (12 submission):

| sub | Team / round | repo_url | Nguồn description |
|---|---|---|---|
| 1 | Phoenix · prelim | `spring-projects/spring-petclinic` (real) | `demo_ai_repo` — **MATCH** |
| 2 | Dragon · prelim | `pallets/flask` (real) | `demo_ai_repo` — **MISMATCH** (desc Node.js ⟂ repo Flask) |
| 3 | Tiger · prelim | `team-tiger/seal` (placeholder) | `ai_demo` |
| 4 | Eagle · prelim | `fastapi/full-stack-fastapi-template` (real) | `demo_ai_repo` — **BONUS** |
| 5 | Falcon · prelim | `team-falcon/seal` | `ai_demo` |
| 6-9 | semi (Phoenix/Dragon/Tiger/Eagle) | placeholder | `ai_demo` |
| 10-12 | final (Phoenix/Tiger/Eagle) | placeholder | `ai_demo` |

Thêm comment giải thích 3 ca demo (MATCH/MISMATCH/BONUS) ngay trên block.

> **An toàn cú pháp:** đã rà các đoạn description tiếng Việt — không chứa dấu nháy đơn `'` lẻ nên không phá literal SQL.

---

## PHẦN 4 — XOÁ FILE & DỌN

`git rm` 7 file (đã stage luôn vì repo git):
```
migration_announcement.sql            migration_criteria_template.sql
migration_systemlog_drop_ip_address.sql  migration_track_problem.sql
seal_demo_ai_repo.sql                 seal_seed_4teams.sql
seal_seed_ai_demo.sql
```
→ Thư mục còn lại: `seal_schema.sql`, `seal_scripts.sql`, `seal_seed.sql`. ✅ Đúng 3 file.

### 4.1. Quét tham chiếu treo
`grep` toàn repo tên 7 file vừa xoá → 7 match, **tất cả vô hại**:
- `seal_schema.sql`: dòng changelog tôi vừa thêm ("folds in migration_announcement.sql") — ghi chú nguồn gốc, giữ lại.
- 6 file `AI logs/` (BE + FE): log lịch sử các phiên trước, append-only — **không sửa**.

→ Không có script/build nào tham chiếu file đã xoá. An toàn.

---

## PHẦN 5 — NỢ KỸ THUẬT / VIỆC CÒN LẠI

1. **`seal_scripts.sql` chưa sửa** (theo yêu cầu). Vẫn ghi "23 bảng" và PHẦN A `SELECT *` **chưa có `Announcement`**. Khi cần: thêm `SELECT 'Announcement'; SELECT * FROM Announcement;` + sửa "23 → 24 bảng".
2. **Seed chưa có dữ liệu mẫu `Announcement`** (đúng như migration gốc — chỉ tạo bảng, không seed row). Nếu cần data cho màn announcement khi test → bổ sung §mới trong seed.
3. **Lệch narrative sub 1** (PetClinic prelim vs SmartDorm semi/final) — hệ quả đã chấp nhận của phương án demo-ready.
4. **Chưa validate bằng MySQL thật** (chưa chạy `mysql < seal_schema.sql`). Chưa commit (người dùng chưa yêu cầu).

---

## PHẦN 6 — FILE THAY ĐỔI
| File | Loại |
|---|---|
| `database scripts/seal_schema.sql` | sửa (+bảng `Announcement`, +`Notification.announcement_id`, header 24 tables) |
| `database scripts/seal_seed.sql` | sửa (+cột `description` cho 12 submission, +repo thật sub 1/2/4) |
| `database scripts/migration_announcement.sql` | **xoá** (đã gộp vào schema) |
| `database scripts/migration_track_problem.sql` | **xoá** (đã có sẵn trong schema) |
| `database scripts/migration_criteria_template.sql` | **xoá** (đã có sẵn trong seed) |
| `database scripts/migration_systemlog_drop_ip_address.sql` | **xoá** (đã có sẵn trong schema) |
| `database scripts/seal_seed_4teams.sql` | **xoá** (đã có sẵn trong seed) |
| `database scripts/seal_seed_ai_demo.sql` | **xoá** (đã gộp vào seed) |
| `database scripts/seal_demo_ai_repo.sql` | **xoá** (đã gộp vào seed, phương án demo-ready) |

---

# PHỤ LỤC A — NGUYÊN TẮC RÚT RA

## A.1. Verify trước, xoá sau
Mỗi file "migration/seed phụ" chỉ được xoá khi xác minh **bằng bằng chứng dòng cụ thể** rằng nội dung của nó đã nằm trong file canonical (schema/seed). Tránh xoá theo cảm tính rồi mất dữ liệu.

## A.2. Phân loại "migration" vs "overlay demo"
- **Migration** (`announcement`, `track_problem`, `systemlog`, `criteria_template`): thay đổi **schema/seed canonical** → phải fold vào file chính.
- **Overlay demo** (`demo_ai_repo`): cố ý **diverge** khỏi narrative để phục vụ một kịch bản demo cụ thể → khi fold sẽ tạo bất nhất; đây là quyết định nghiệp vụ, cần hỏi người dùng thay vì tự quyết.

## A.3. Thứ tự FK khi gộp DDL
Bảng được tham chiếu (`Announcement`) phải `CREATE` trước bảng tham chiếu (`Notification`). Khi chèn bảng mới vào schema idempotent, đặt đúng vị trí topo để `DROP & re-run` không vỡ.

## A.4. Gộp `ALTER` phụ vào `CREATE` gốc
Migration đời thực hay có `CREATE TABLE` tối thiểu + vài `ALTER ADD COLUMN` vá dần (audience, link_url). Khi đưa về schema canonical, hợp nhất tất cả vào một `CREATE TABLE` đầy đủ — bỏ các `ALTER` lịch sử.

## A.5. `git rm` thay vì xoá tay
Dùng `git rm` để việc xoá được **stage** ngay trong cùng changeset với các sửa đổi schema/seed → diff mạch lạc, review một lần thấy đủ "gộp gì, xoá gì".
