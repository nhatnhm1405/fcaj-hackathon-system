# Nhật Ký Refactor — Dọn "Code Rác" (Phase 2: Back-end) — Ngày 19/06/2026

Tiếp nối Phase 1 (database scripts), phiên này thực hiện **Phase 2 — dọn code rác back-end** trên nhánh `refactor/cleanup` (tách từ `develop`). Xem thêm: `AI_LOG_SEAL_REFACTOR_CLEANUP_DB_2026-06-19.md`.

---

## 0. Phương châm
Dọn theo chuẩn một developer thật sự: chỉ loại bỏ thứ **rõ ràng là rác / sai chỗ**, KHÔNG nhét refactor logic rủi ro vào một pass "dọn rác". Mọi nghi vấn đều được verify trước khi kết luận.

---

## 1. Khảo sát & loại trừ nghi vấn

Back-end trên `develop` thực ra **khá sạch ở mức cấu trúc**. Các nghi vấn đã kiểm và **bác bỏ**:

| Nghi vấn | Cách kiểm | Kết luận |
|----------|-----------|----------|
| Service chết | Đếm tham chiếu mỗi service ngoài file của nó | Tất cả ≥1 → không chết |
| DTO chết | Quét 54 DTO request/response | 0 cái không dùng |
| 2 controller assignment trùng | So sánh `@RequestMapping`/mappings | Khác vai trò: `AssignmentController` (`/api/mentor\|judge/assignments`, để mentor/judge tự xem) vs `CoordinatorAssignmentController` (`/api/coordinator/assignments/*`, coordinator quản lý) → giữ cả 2 |
| Import mồ côi tới class đã xóa (TeamAssignment, UserRoleService…) | Grep toàn package | Không còn (develop đã dọn) |
| Code comment-out | Grep mẫu `// if/for/return/...` | ~1 dòng, không đáng kể |
| Junk build (target/.class/.log/.DS_Store) | `git ls-files` | Không có file nào bị track |

---

## 2. "Code rác" thật sự & cách xử lý

| # | Mục | Vấn đề | Xử lý |
|---|-----|--------|-------|
| 1 | `back-end/src/.idea/vcs.xml`, `workspace.xml` | File IDE bị commit; `workspace.xml` (≈205 dòng) là state riêng máy | `git rm --cached` + gitignore |
| 2 | `.gitignore` thiếu phủ | Chỉ có `seal-api/.gitignore`, ignore `.idea` **sai cấp** → `.idea` ở `back-end/src/` lọt lưới | Thêm `back-end/.gitignore` |
| 3 | `back-end/src/seal-api/EVENT_VALIDATION_REVIEW.md` | Báo cáo review lẻ (2026-06-13, 372 dòng) nằm trong gốc module source, lệch quy ước | `git mv` → `back-end/AI logs/` |
| 4 | `HackathonApplication.java` | `System.out.println("Run successfully")` + dòng trống thừa trong entrypoint | Xóa |

### 2.1. Nội dung `back-end/.gitignore` (mới)
```
# IDE — JetBrains
.idea/
*.iml
*.iws
*.ipr

# Build output
target/
build/

# OS
.DS_Store
Thumbs.db
```
Đặt ở cấp `back-end/` nên pattern `.idea/` phủ mọi cấp con (kể cả `back-end/src/.idea/`). Đã verify bằng `git check-ignore`.

---

## 3. Bẫy gặp khi commit (ghi lại để tránh lặp)
- Lần đầu commit dùng `git commit -- <paths>` (partial commit theo **working tree**). Vì file `.idea` đã `git rm --cached` nhưng **vẫn còn trên đĩa**, partial commit "không thấy xóa" → commit hụt, `.idea` vẫn tracked.
- **Khắc phục:** unstage rename → `git rm --cached` lại 2 file `.idea` → `git commit --amend` (lúc này index chỉ còn đúng phần xóa `.idea`). Bài học: với file còn tồn tại trên đĩa nhưng muốn untrack, **đừng** dùng dạng `git commit -- <path>`; hãy commit theo index đã stage.

---

## 4. Các commit Phase 2 (nhánh `refactor/cleanup`, không kèm Co-Authored-By)
- `chore(gitignore): stop tracking IDE files` — gỡ track `.idea` + thêm `back-end/.gitignore`.
- `docs: move EVENT_VALIDATION_REVIEW into AI logs/` — git nhận diện 100% rename.
- `chore(app): remove debug println from entrypoint`.

## 5. Việc để lại (follow-up tùy chọn, KHÔNG làm trong pass này)
- Đổi tên `AssignmentController` cho bớt nhầm với `CoordinatorAssignmentController` (đụng route/import nhiều file).
- Tách `TeamService.java` (611 LOC) — refactor logic, cần test kỹ.
Hai việc này là refactor *logic/cấu trúc*, không phải dọn rác → tách riêng nếu team thấy cần.

## 6. Tiếp theo
**Phase 3 — Front-end** (phần sai nhiều nhất): làm cuối, canh lúc ít người sửa hoặc freeze ngắn để giảm xung đột; rebase `refactor/cleanup` lên `develop` mới nhất trước khi merge.
