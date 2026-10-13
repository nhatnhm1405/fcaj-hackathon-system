# Nhật Ký Refactor — Dọn "Code Rác" Toàn Hệ Thống (Phase 1: Database Scripts) — Ngày 19/06/2026

Tài liệu ghi nhận phiên làm việc khởi động đợt **refactor dọn dẹp toàn hệ thống** sau khi nhánh `develop` đã hoàn thiện về tính năng nhưng còn đọng nhiều file/định nghĩa thừa. Đợt refactor chia **3 phase theo thứ tự ưu tiên**: **Database → Back-end → Front-end** (FE là phần sai nhiều nhất). Phiên này hoàn thành **Phase 1 — Database scripts**.

---

## 0. Bối cảnh & khảo sát ban đầu

### 0.1. Lấy context dự án
- Đọc `back-end/Postman/Postman_Full_Collection.json` để dựng bản đồ API (14 nhóm: Auth, Account Approval, User Management, Events, Tracks, Rounds, Teams, Team Invites, Submissions, Scoring, Round Results, Notifications, Team Assignments, Security Tests).
- Base URL `http://localhost:8080`; response format chuẩn `{ data: {...} }`; 4 token theo role (coordinator/participant/judge/mentor); tài khoản phải được duyệt trước khi login.

### 0.2. So sánh `develop` vs `main`
- `develop` đi trước `main` **129 commits**, **282 files** (+33k / −13.7k); `main` **không** có commit riêng → merge sẽ fast-forward.
- Back-end: thêm các module Scoring, Round Results, Submissions, Team Invites, Join Request, Reopen Request, Notifications, Audit/System Log, Admin split (SYSTEM_ADMIN ⟂ COORDINATOR), OAuth2 + Complete Profile, Email.
- Database: thêm 3 bảng `JoinRequest`, `ReopenRequest`, `SystemLog`; sửa cấu trúc nhiều bảng; đã xóa thư mục `docs/database/{MySQL,SQL server}` cũ.
- **Kết luận quan trọng:** working tree lúc bắt đầu = `main` (code cũ), KHÔNG phải `develop`. Code "đã ổn" cần refactor nằm ở `develop`.

---

## 1. Quyết định chiến lược nhánh

**Ràng buộc:** một số thành viên team vẫn đang làm việc trên `develop` (moving target). Không được để `main` và `develop` **phân kỳ cấu trúc** — vì refactor lớn = đổi tên/xóa/di chuyển file, nếu chỉ sửa riêng `main` thì mọi lần `develop → main` sau này sẽ xung đột nặng vĩnh viễn.

**Phương án đã chốt:**
```
refactor/cleanup (tách từ develop HEAD)
   └─ refactor theo phase: DB → BE → FE
        └─ rebase lên develop mới nhất khi team đẩy xong việc
             └─ merge → develop
                  └─ release: develop → main
```
- Refactor đi vào `develop` (qua nhánh `refactor/cleanup`), `main` sạch theo đường `develop → main` chứ không sửa tay riêng.
- Thứ tự *ưu tiên sửa* vẫn DB→BE→FE; bổ sung yếu tố *canh thời điểm merge* theo rủi ro xung đột (DB thấp → làm trước; FE churn cao → làm cuối, cần freeze ngắn).

**Thao tác git đã thực hiện:**
- Stash 2 file config local trên `main` (`HackathonApplication.java` whitespace, `application.properties` đổi default DB password) — message: `local main config (db pw default, whitespace)`. Khôi phục bằng `git stash pop`.
- `git switch -c refactor/cleanup develop` → working tree sạch.
- Xác nhận `.env` được `.gitignore` trên baseline `develop` (trên `main` thì CHƯA) → an toàn không lỡ commit secrets.

---

## 2. Phase 1 — Database Scripts

### 2.1. Mục tiêu (theo yêu cầu)
Thư mục `back-end/database scripts/` **chỉ còn đúng 3 file**: `seal_schema.sql`, `seal_seed.sql`, `seal_scripts.sql`. Các file còn lại xóa, tích hợp nội dung hữu ích (nếu có) vào 1 trong 3 file.

### 2.2. Hiện trạng trước khi sửa (6 file)
```
demo_scoring.sql
migration_audit_log.sql
migration_reopen_request.sql
seal_schema.sql
seal_scripts.sql
seal_seed.sql
```

### 2.3. Khảo sát "code rác" tầng DB
Tầng DB của `develop` thực ra **khá sạch** (schema idempotent, comment + CHANGELOG đầy đủ, đã bỏ `TeamAssignment`). Vấn đề chủ yếu là **bất nhất tài liệu** và **file thừa**:

| # | File | Vấn đề | Xử lý |
|---|------|--------|-------|
| 1 | `seal_schema.sql` | Header ghi `19 tables` nhưng thực tế **23** `CREATE TABLE` | Sửa số liệu |
| 2 | `seal_scripts.sql` | PHẦN A ghi `20 bảng`, liệt kê 21, **thiếu `JoinRequest` + `SystemLog`** dù tự nhận "SELECT * toàn bộ bảng" | Bổ sung + sửa count |
| 3 | `migration_audit_log.sql` | Định nghĩa `AuditLog` **trùng** với schema gộp | Xóa |
| 4 | `migration_reopen_request.sql` | Định nghĩa `ReopenRequest` **trùng** với schema gộp | Xóa |
| 5 | `demo_scoring.sql` | Dataset demo scoring theo ngày — **team đã bỏ** (demo scoring nay dựa trên `status`, không dựa thay đổi ngày) | Xóa hẳn |

### 2.4. Verify trước khi xóa (để không mất cột/index)
- So sánh **thân bảng** `AuditLog` và `ReopenRequest` giữa file migration và `seal_schema.sql`: **giống hệt** (chỉ khác `CREATE TABLE IF NOT EXISTS` vs `CREATE TABLE`) → schema gộp đã phủ đủ.
- `grep` toàn repo tham chiếu `demo_scoring` / `migration_audit_log` / `migration_reopen_request`: **không có tham chiếu ngoài** → xóa không gãy build/doc.
- Đếm `CREATE TABLE` trong `seal_schema.sql` = **23** (xác nhận con số mới).

### 2.5. Thay đổi đã áp dụng

**Xóa (3 file — `git rm`):**
- `back-end/database scripts/demo_scoring.sql`
- `back-end/database scripts/migration_audit_log.sql`
- `back-end/database scripts/migration_reopen_request.sql`

**Sửa `seal_schema.sql`:**
- Header `-- 19 tables` → `-- 23 tables`.

**Sửa `seal_scripts.sql`:**
- Mô tả `PHẦN A — SELECT * cho toàn bộ 20 bảng` → `... 23 bảng`.
- Thêm `SELECT 'JoinRequest' AS tbl; SELECT * FROM JoinRequest;` (nhóm 7, sau `TeamInvite`, trước `ReopenRequest` — đúng thứ tự schema).
- Thêm `SELECT 'SystemLog' AS tbl; SELECT * FROM SystemLog;` (nhóm 8, sau `AuditLog`).

### 2.6. Danh sách 23 bảng (chuẩn hóa)
`Role`, `User`, `HackathonEvent`, `Track`, `Round`, `UserEventRole`, `JudgeAssignment`, `MentorAssignment`, `Team`, `TeamMember`, `Submission`, `ScoringCriteriaTemplate`, `ScoringCriteria`, `Score`, `RoundResult`, `Prize`, `AccountApproval`, `TeamInvite`, **`JoinRequest`**, `ReopenRequest`, `Notification`, `AuditLog`, **`SystemLog`**.

### 2.7. Nguyên tắc giữ an toàn
KHÔNG đụng: cấu trúc bảng, FK, index, nội dung seed thật. Chỉ dọn file thừa + đồng bộ số liệu/độ phủ tài liệu — phần đang đúng thì không refactor thêm để tránh rủi ro.

---

## 3. Trạng thái cuối phiên

- Nhánh: `refactor/cleanup` (tách từ `develop`).
- `back-end/database scripts/` còn đúng 3 file: `seal_schema.sql`, `seal_seed.sql`, `seal_scripts.sql`.
- **Chưa commit** (theo quy trình duyệt từng phase). Commit dự kiến:
  `refactor(db): consolidate to 3 scripts, drop redundant migrations & demo`

---

## 4. Việc còn lại (các phase sau)

- **Phase 2 — Back-end:** khảo sát & dọn `.idea/` lỡ commit (`back-end/src/.idea/{vcs,workspace}.xml`), `.env` chưa track trên một số nhánh, đống `*.md` review/AI log rải trong `src/seal-api/`, DTO/service trùng lặp, code chết sau đợt admin-split & assignment redesign.
- **Phase 3 — Front-end (sai nhiều nhất):** làm cuối, canh lúc ít người sửa hoặc freeze ngắn để giảm xung đột.
- Trước khi merge `refactor/cleanup → develop`: đợi team đẩy xong việc dở, rebase lên `develop` mới nhất.
