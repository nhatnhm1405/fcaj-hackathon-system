# ★ AI LOG — Code-First Hibernate Migration (BACKEND) — 2026-07-10

> Chuyển quản lý schema từ **schema-first (SQL viết tay)** sang **code-first**:
> entity JPA là **nguồn sự thật duy nhất**, Hibernate tự sinh schema, dữ liệu nạp
> bằng **Java seeder**. Mục tiêu: hết conflict trên `seal_schema.sql` / `seal_seed.sql`.
> Nhánh: `NhatNHM-codefirst-hibernate` (tách từ `develop`).

---

## 1. Bối cảnh & vấn đề

Team liên tục phải sửa **cả** `seal_schema.sql` (627 dòng) **lẫn** entity Java mỗi khi
đổi 1 cột → file SQL chung là điểm nghẽn merge conflict. Giảng viên gợi ý **code-first**.

**Phát hiện then chốt khi rà soát:** dự án **ĐÃ có sẵn 27 `@Entity`** khớp với 28 bảng,
và `ddl-auto=none` → đang duy trì **2 nguồn sự thật song song** (SQL tay + entity tay).
Nghĩa là đã đi được 80% đường — chỉ cần **lật ngược**: để entity làm vua, schema sinh tự động.

---

## 2. Các quyết định thiết kế (Q&A với người dùng)

| Vấn đề | Quyết định |
|--------|-----------|
| Cơ chế schema | `ddl-auto=update` (đơn giản nhất, đúng ý thầy) — KHÔNG Flyway/Liquibase |
| DB khi dev | Mỗi người 1 DB local · "xóa thoải mái" → drop DB là dựng lại sạch |
| Charset tiếng Việt | `createDatabaseIfNotExist=true` + MySQL 8 mặc định **utf8mb4** |
| Seed bắt buộc | **Java seeder** (roles + admin), idempotent — thay phần must-have của `seal_seed.sql` |
| Seed demo | **Java scenario seeder** (không SQL) — tránh vấn đề id + drift khỏi entity |
| Bảng chết `AccountApproval` | Bỏ (không có entity; code-first không tạo) |
| 2 file SQL cũ | **Giữ lại** làm tham khảo — app không đọc nữa |

**Vì sao Java seeder (không giữ `seal_seed.sql`):** trong code-first, SQL seed sẽ **drift**
khỏi entity (đổi cột → INSERT fail lúc chạy). Java seeder dùng **object reference** → hết
lo id, và **lỗi compile ngay** nếu field đổi → luôn khớp entity.

---

## 3. Diff entity ↔ schema tay (dùng profile `ddlgen` tạm)

Tạo profile tạm dump DDL Hibernate sinh từ entity (`generated-schema.sql`) rồi diff với
`seal_schema.sql`. Kết quả: entity annotate **rất kỹ** — đủ mọi unique key, FK, cột TEXT.
Chỉ lệch vài chỗ, và phần lớn **không đáng kể**:

| Thiếu | Xử lý |
|---|---|
| `ON DELETE CASCADE` — hand có 4, nhưng chỉ **2 chỗ có FK thật** trong entity | Thêm 2 `@OnDelete(CASCADE)` |
| `resolved_by`, `RoundTimerNotice.roundId` | Entity map là `Integer` thường (không FK) → bỏ qua, theo entity |
| Index thường, DEFAULT ở DB | Bỏ (Java set default; index thêm sau nếu cần) |
| Charset utf8mb4 | Xử bằng `createDatabaseIfNotExist` + MySQL 8 |

*(Profile `ddlgen` đã xóa sau khi xong việc — commit `72d3de5`.)*

---

## 4. Các thay đổi triển khai

**Schema ownership** (`application.properties`)
- `spring.jpa.hibernate.ddl-auto=none → update`
- JDBC URL `+createDatabaseIfNotExist=true&characterEncoding=UTF-8`

**Cascade parity** (2 entity)
- `ParticipationAccessRequest.user` → `@OnDelete(CASCADE)` (xóa user → xóa access request)
- `RoundTimer.round` → `@OnDelete(CASCADE)` (xóa round → xóa timer)

**Essential seeder** (`config/DataSeeder.java`, `@Order(1)`)
- Idempotent: 4 role (SYSTEM_ADMIN/EVENT_COORDINATOR/MENTOR/JUDGE) + 1 admin bootstrap
  (`admin@fpt.edu.vn` / `Test@1234`, BCrypt) + grant SYSTEM_ADMIN. Chạy mọi lần, chỉ thêm cái thiếu.

**Docker** (`docker-compose.yml`)
- Bỏ nạp `seal_*.sql` qua `initdb.d` → Hibernate + Java seeder lo (khớp local).

---

## 5. Demo scenario seeder (`config/seed/`)

Gate bằng `app.seed.scenario = NONE|S0|S1|S2|S3` (mặc định **NONE** → chạy thật không data giả).

- `DemoFixtures` — ~15 helper builder, **object reference (không id)**: user/grant/event/track/
  round/criteria/team/assignJudge/assignMentor/submission/score/result/prize.
- `DemoScenario` — build **xếp tầng** 1 event demo, dừng ở "điểm cắt":
  ```
  S0 accounts only            (no event)
  S1 + OPEN + structure + forming teams (có solo/pair cho tính năng ghép người lẻ)
  S2 + approved teams in tracks + assignments + submissions   (IN_PROGRESS)
  S3 + scores + ranked results + prizes                       (COMPLETED)
  ```
  Status set khớp từng mốc → luôn nhất quán state machine. **Chấm điểm deterministic**
  (hàm thuần theo "strength" của team) → ranking/prize tái lập được.
- `DemoSeeder` — runner `@Order(2)`, guard theo account demo → không seed trùng (drop DB để reseed).

Tài khoản demo: `demo.*@fpt.edu.vn` / **`Test@1234`** (coordinator, judge1/2, guestjudge,
mentor1/2, p1…, spare1/2).

---

## 6. Verify end-to-end (trên DB **trắng** throwaway, không đụng DB seed thật)

| Kiểm tra | Kết quả |
|---|---|
| Hibernate tự tạo bảng | **27** ✓ |
| Charset DB | **utf8mb4** ✓ |
| Essential seed | 4 role + admin + grant SYSTEM_ADMIN ✓ |
| FK cascade | `ParticipationAccessRequest.user_id = CASCADE` ✓ |
| **Demo S3** | 1 event COMPLETED · 6 team trong track · **120 score** · 10 result published · **3 prize** đúng top-3 · không lỗi ✓ |

→ **Drop DB → chạy app → schema + data tự có.** Đúng nghĩa code-first.

---

## 7. Commits (nhánh `NhatNHM-codefirst-hibernate`)
```
e2dbbe8 feat(db): adopt code-first schema management via Hibernate
207c065 feat(entity): keep ON DELETE CASCADE on access requests and round timers
b8f6728 feat(db): seed essential roles and bootstrap admin on startup
72d3de5 chore(db): remove temporary ddlgen schema-dump profile
975bb8e feat(db): add gated demo-data seeder with S0-S3 scenarios
2c2fcb2 chore(docker): align compose with code-first schema management
```

---

## 8. Còn lại (việc người dùng, không phải code)
- [ ] Push + **merge** nhánh vào `develop` → cả project mới "lên" code-first (nhánh khác vẫn `none`).
- [ ] Chạy sạch lần đầu: **drop `seal_hackathon` cũ** rồi chạy app (tránh gợn khi `update` hoà giải schema tay). Muốn demo đầy đủ: `SEED_SCENARIO=S3`.
- [ ] `seal_schema.sql` / `seal_seed.sql`: giữ làm tham khảo, app không đọc.

## 9. Danh sách file
**Mới:** `config/DataSeeder.java`, `config/seed/{DemoSeeder,DemoScenario,DemoFixtures}.java`
**Sửa:** `application.properties`, `entity/ParticipationAccessRequest.java`, `entity/RoundTimer.java`, `docker-compose.yml`
**Xóa:** `resources/application-ddlgen.properties` (profile tạm)

---

*Lưu ý `ddl-auto=update`: tự thêm bảng/cột, KHÔNG tự đổi tên/xóa cột/đổi kiểu — gặp mấy ca đó thì drop DB chạy lại (DB disposable). Không cần migration tool.*
