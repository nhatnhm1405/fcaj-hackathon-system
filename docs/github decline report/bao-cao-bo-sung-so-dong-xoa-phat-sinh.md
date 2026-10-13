# GIẢI TRÌNH BỔ SUNG: SỐ DÒNG XÓA PHÁT SINH THÊM SAU 23/06/2026

**Dự án:** SEAL Hackathon System
**Phạm vi báo cáo:** phần **tăng thêm** trên biểu đồ Contributors kể từ mốc đã giải trình (23/06/2026) đến commit gần nhất trên `main` (18/07/2026). Báo cáo trước (`bao-cao-tinh-xac-thuc-ma-nguon.md`) đã giải trình xong phần dòng xóa tính đến 23/06/2026 — tài liệu này **chỉ giải trình phần chênh lệch phát sinh thêm**, không lặp lại nội dung cũ.
**Cơ sở dữ liệu:** `git log` trên nhánh `main`, kiểm chứng lại được bằng các lệnh ở Mục 5.

---

## 1. Số liệu hiện tại so với phần đã giải trình

| Tác giả (GitHub) | Đã giải trình (tính đến 23/06) −− | Hiện tại trên biểu đồ Contributors −− | Phát sinh thêm cần giải trình −− |
|---|---:|---:|---:|
| nhatnhm1405 | 33,039 | 51,339 | **+18,300** |
| KTrangg | 36,907 | 53,619 | **+16,712** |
| HuuKhanh2608 | 15,012 | 16,915 | **+1,903** |
| hoangnhat1407 | ~1,063 | 13,515 | **+12,452** |

→ Phần lớn số phát sinh thêm (đặc biệt của **KTrangg** và **hoangnhat1407**) đến từ **một sự kiện duy nhất, có chủ đích và có ghi chú rõ ràng trong commit message**: revert PR #256 (`hoangnhat-draft`) — xem Mục 2.

---

## 2. Nguyên nhân chính: revert PR #256 (`hoangnhat-draft`) ngày 26/06/2026

PR #256 từng merge một nhánh draft (`hoangnhat-draft`) vào `develop`, nhưng nhánh này **ghi đè tiến độ front-end hiện có của nhóm** và mang theo code backend/auth/docker/deploy không mong muốn. Nhóm quyết định revert toàn bộ thay vì reset, để không phá lịch sử của 4 merge sau đó (#259, #262, #260, #263) đang phụ thuộc vào nó. Trích nguyên văn commit message:

> **`d8e46df`** — *"Revert 'Merge pull request #256 from nhatnhm1405/hoangnhat-draft'"*
> "This reverts merge d90f49d (PR #256, hoangnhat-draft), which overwrote the team's frontend progress and introduced backend/auth/docker/deploy code that we are rolling back in full. [...] Reverting keeps all later work intact with no history rewrite — teammates only need to pull."

### 2.1. Tác động lên KTrangg — người thực hiện revert

| Ngày | Commit | Dòng xóa | Nội dung |
|---|---|---:|---|
| 26/06/2026 | `d8e46df` | **13,566** | Revert toàn bộ PR #256 — hoàn tác code đã ghi đè front-end/mang backend không mong muốn |

→ Riêng commit này chiếm **~86%** số dòng xóa phát sinh thêm của KTrangg (13,566 / 15,771 dòng xóa thực tế theo `git log` kể từ 24/06). Đây là một hành động **dọn dẹp có chủ đích, được giải thích rõ trong chính commit**, không phải mất mã nguồn ngẫu nhiên.

### 2.2. Tác động lên hoangnhat1407 — đồng bộ lại nhánh cá nhân sau revert

Sau khi `develop` được revert, hoangnhat1407 phải cập nhật lại nhánh làm việc của mình để khớp với `develop` đã dọn sạch — quá trình này xóa các file/thư viện cũ đã bị thay thế trên `develop` (rác còn sót lại từ PR #256):

| Ngày | Commit | Dòng xóa | Nội dung |
|---|---|---:|---|
| 26/06/2026 | `dd7126c` | 9,198 | *"Update frontend from develop"* — đồng bộ nhánh cá nhân, xóa các file/thư viện cũ đã bị `develop` thay thế (`ui/sidebar.tsx`, `ui/chart.tsx`, `ui/menubar.tsx`, các trang cũ `AuthPage.tsx`/`JudgePage.tsx`/`DashboardHome.tsx`, tài liệu `imports/pasted_text/*` không dùng) |
| 26/06/2026 | `69d3b26` | 204 | *"Update database scripts from develop"* — bỏ script SQL/migration trùng đã có bản mới trên `develop` |
| 26/06/2026 | `7db188a` | 72 | *"Restore backend validation from draft"* — khôi phục lại phần validate hợp lệ, bỏ phần dư thừa |

→ Ba commit đồng bộ này chiếm **~93%** số dòng xóa phát sinh thêm của hoangnhat1407 (9,474 / 11,612 dòng xóa thực tế theo `git log` kể từ 24/06). Đây là hệ quả trực tiếp, có chủ đích của việc revert PR #256, không phải mất mã nguồn.

---

## 3. Nguyên nhân phụ: refactor tách nhỏ file (nhatnhm1405, tuần 18/07/2026)

Phần còn lại của số dòng xóa phát sinh của nhatnhm1405 đến từ việc **tách nhỏ các file/service quá lớn** thành nhiều module chuyên biệt — số dòng xóa và số dòng thêm gần tương đương (đặc trưng của rewrite/refactor, không phải mất code):

| Ngày | Commit | Dòng xóa | Dòng thêm | Nội dung |
|---|---|---:|---:|---|
| 18/07/2026 | `bf2844e` | 2,598 | 3,292 | Tách `TeamService` thành các service chuyên biệt hơn |
| 18/07/2026 | `7a924fc` | 1,888 | 1,925 | Tách `apiClient.ts` thành các module theo domain |
| 18/07/2026 | `93791be` | 1,441 | 1,599 | Tách `CoordEventsPage` thành các tab component riêng |
| 18/07/2026 | `817a7ba` | 885 | 991 | Tách `AssignmentService` thành service staff/mentor/judge riêng |

→ 4 commit này chiếm phần lớn 8,652 dòng xóa thực tế (theo `git log`, không tính merge) của nhatnhm1405 kể từ 24/06 — đều là refactor có chủ đích, dòng xóa ≈ dòng thêm.

**Về khoảng cách với con số trên biểu đồ (18,300):** nhatnhm1405 là người thực hiện phần lớn việc merge PR/nhánh `develop` vào `main` trong repo (bao gồm merge PR #265 chứa chính bản revert nói ở Mục 2, và các lần merge `develop` → `main` sau đó như PR #348, #389). Biểu đồ Contributors của GitHub cộng dồn cả diff của các merge commit này vào tên người merge — cùng cơ chế thổi phồng số liệu đã nêu ở báo cáo trước (Mục 6, trường hợp KTrangg với merge `aa3cd62`). Số liệu đáng tin cậy là tổng từ `git log --no-merges` (8,652 dòng), không phải số trên biểu đồ.

---

## 4. HuuKhanh2608 — không có sự kiện bất thường

Phần phát sinh thêm của HuuKhanh2608 (~1,903 theo biểu đồ, 1,463 theo `git log` thực tế) là các thay đổi nhỏ, rải rác trong quá trình phát triển tính năng bình thường — không có một commit dọn dẹp lớn nào chi phối:

| Ngày | Commit | Dòng xóa | Nội dung |
|---|---|---:|---|
| 05/07/2026 | `62355ff` | 163 | Tinh gọn thao tác trên trang Events của Coordinator |
| 13/07/2026 | `3f25db4` | 101 | Thêm view chỉ đọc cho participant/judge-mentor, dọn hàng đợi duyệt |
| 14/07/2026 | `7e9353e` | 100 | Hiển thị panel judge và tiến độ chấm điểm |
| 12/07/2026 | `ed5237e` | 107 | Redesign coordinator dashboard |

→ Đây là churn thông thường của việc phát triển/redesign UI, không cần giải trình đặc biệt.

---

## 5. Cách kiểm chứng độc lập

```bash
# Số dòng xóa thực tế theo tác giả, kể từ mốc đã giải trình (24/06 -> nay)
git log --since="2026-06-24" --no-merges --author="<email>" --pretty=tformat: --numstat main \
  | awk '{add+=$1; del+=$2} END {printf "add=%d del=%d\n", add, del}'

# Đọc lý do revert trực tiếp từ commit message
git show d8e46df --stat --pretty=fuller

# Xác nhận PR #256 và nhánh revert
git log --merges --all --grep="256" --oneline

# Các commit đồng bộ lại nhánh hoangnhat1407 sau revert
git show dd7126c --numstat | sort -t$'\t' -k2 -rn | head
git show 69d3b26 --numstat
git show 7db188a --numstat

# Các commit refactor tách file của nhatnhm1405 (xóa ~ thêm => rewrite, không mất code)
git show bf2844e --numstat
git show 7a924fc --numstat
```

---

## 6. Kết luận

Phần lớn (>85% ở cả hai tác giả bị ảnh hưởng nhiều nhất) số dòng xóa phát sinh thêm sau 23/06/2026 bắt nguồn từ **một quyết định kỹ thuật duy nhất, có chủ đích và được ghi chú rõ ràng**: revert PR #256 (`hoangnhat-draft`) vào ngày 26/06/2026, vì nhánh này ghi đè tiến độ front-end và mang theo code không mong muốn. Phần còn lại là refactor tách nhỏ file (dòng xóa ≈ dòng thêm) và churn phát triển tính năng bình thường. Không có dấu hiệu bất thường hay dữ liệu tạo từ bên ngoài.
