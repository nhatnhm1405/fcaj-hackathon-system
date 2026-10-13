# BÁO CÁO GIẢI TRÌNH: TÍNH XÁC THỰC CỦA MÃ NGUỒN & CHÊNH LỆCH SỐ DÒNG CODE

**Dự án:** SEAL Hackathon System
**Khoảng thời gian thống kê:** 22/05/2026 – 22/06/2026
**Người lập báo cáo:** _____________________
**Ngày lập:** _____________________

---

## 1. Bối cảnh

Trong kỳ thống kê, dự án ghi nhận số dòng code thêm/xóa ở mức cao (khoảng 125,000 dòng thêm và 86,000 dòng xóa). Báo cáo này giải trình nguồn gốc của các con số đó, đồng thời cung cấp bằng chứng cho thấy toàn bộ mã nguồn do chính nhóm phát triển thực hiện, không có sự can thiệp tạo dữ liệu từ bên ngoài.

Nhóm xác nhận có sử dụng công cụ hỗ trợ lập trình (AI-assisted / vibe coding) trong quá trình phát triển. Đây là phương pháp làm việc bình thường và được áp dụng nhất quán; mọi kết quả từ công cụ đều do thành viên nhóm trực tiếp điều hướng, kiểm tra và commit dưới tài khoản cá nhân.

---

## 2. Tóm tắt kết luận

1. Số dòng xóa lớn **không đồng nghĩa với mất mã nguồn**. Khoảng **31%** số dòng xóa thuộc về **file tự sinh và thư viện bên thứ ba** (không do người viết tay).
2. Phần lớn số dòng còn lại là **viết lại (rewrite)** giao diện front-end ở vòng đầu — file cũ bị xóa và được viết lại ngay trong cùng một commit, thể hiện qua việc số dòng xóa xấp xỉ số dòng thêm.
3. Quy mô mã nguồn **tăng ròng** (~125k thêm so với ~86k xóa), tức codebase phát triển thêm chứ không bị thu hẹp.
4. Một số chỉ số hiển thị trên biểu đồ thống kê tự động của nền tảng (Contributors) bị **thổi phồng và gán nhầm tác giả** do cách nền tảng tính merge commit. Số liệu chính xác cần lấy từ lịch sử `git log`.
5. Toàn bộ số liệu trong báo cáo **có thể kiểm chứng lại** bằng các lệnh git được liệt kê ở Mục 7.

---

## 3. Phân loại số dòng XÓA theo loại file

| Loại file | Dòng xóa | Bản chất |
|---|---:|---|
| `package-lock.json` | ~18,349 | File quản lý phụ thuộc do npm **tự sinh** |
| Thư viện UI `shadcn` (`components/ui/`) | ~7,183 | Mã thư viện bên thứ ba sinh sẵn |
| Tài nguyên import từ Figma (`imports/`) | ~1,272 | Mã/asset export tự động |
| Ảnh (svg/png/jpg…) | ~54 | Tệp nhị phân |
| **Cộng phần không do viết tay** | **~26,858 (~31%)** | |
| SQL / script DB | ~1,381 | Dọn script trùng lặp |
| Tài liệu (md/txt) | ~1,588 | Dọn tài liệu trùng lặp |
| JSON khác (config/Postman) | ~1,003 | Tệp cấu hình / collection |
| Mã viết tay (.tsx/.ts/.java) | ~57,014 | Phần lớn là viết lại (xem Mục 4) |

---

## 4. Bằng chứng "viết lại" thay vì "xóa bỏ"

Commit tái cấu trúc front-end `fb4ddb3` (03/06/2026) cho thấy nhiều file bị xóa và viết lại với số dòng tương đương — đặc trưng của hoạt động refactor/rewrite, không phải xóa mất nội dung:

| File | Dòng xóa | Dòng thêm |
|---|---:|---:|
| `LandingPage.tsx` | 805 | 805 |
| `PixelComponents.tsx` | 569 | 569 |
| `SealFooter.tsx` | 155 | 155 |
| `LoginPage.tsx` | 141 | 134 |

Lý do: giao diện ở vòng phát triển đầu chưa đạt yêu cầu nên được thiết kế và viết lại. Đây là quy trình cải tiến chủ động của nhóm.

---

## 5. Giải trình các commit có số dòng bất thường

Hầu hết các commit có số dòng lớn bất thường đều liên quan đến **tệp tự sinh** (lockfile, collection export) hoặc **tệp test**, không phải mã logic viết tay:

| Commit | Ngày | Tác giả | Thêm | Xóa | Tệp | Bản chất |
|---|---|---|---:|---:|---|---|
| `9684b59` | 17/06/2026 | KhanhNLH | 7,966 | 2 | `package-lock.json` | npm tự sinh (cấu hình test) |
| `de9750e` | 29/05/2026 | KTrangg | 5,876 | 337 | `package-lock.json` | npm tự sinh (cài shadcn) |
| `fb4ddb3` | 03/06/2026 | Nhat | 3,164 | 8,407 | `package-lock.json` | npm tự sinh (rebuild FE) |
| `73095d5` | 29/05/2026 | KhanhNLH | 2,768 | 0 | `package-lock.json` | npm tự sinh (khởi tạo Vite) |
| `f0a6066` | 29/05/2026 | KhanhNLH | 2,504 | 362 | `package-lock.json` | npm tự sinh (cài phụ thuộc) |
| `bdfe0d4` | 12/06/2026 | Nhat | 1,674 | 412 | `Postman_Full_Collection.json` | export tự động |
| `7e2c879` | 18/06/2026 | Đào Hoàng Nhật | 1,169 | 0 | `TeamServiceTest.java` | tệp kiểm thử hợp lệ |
| `eebbda5` | 09/06/2026 | KhanhNLH | 1,031 | 0 | `Postman_Full_Collection.json` | export tự động |
| `d80f23a` | 21/06/2026 | Đào Hoàng Nhật | 726 | 0 | `ui/sidebar.tsx` | thư viện shadcn |

Nhận xét: trong nhóm commit có số dòng lớn nhất, phần lớn là `package-lock.json` — tệp do trình quản lý gói tự tạo, không phản ánh khối lượng code do người viết.

---

## 6. Giải trình chênh lệch trên biểu đồ thống kê tự động

Biểu đồ Contributors của nền tảng hiển thị số liệu lệch đáng kể so với lịch sử git thực tế. Ví dụ điển hình:

| Tác giả | Số dòng xóa (theo `git log`) | Số dòng xóa (biểu đồ tự động) |
|---|---:|---:|
| KTrangg | ~2,179 | 36,907 |

**Nguyên nhân:** biểu đồ tính cả **merge commit**. Khi một thành viên merge nhánh chung vào nhánh tính năng (ví dụ commit `aa3cd62` ngày 21/06/2026), toàn bộ phần xóa do người khác thực hiện bị cộng và gán cho người đứng tên merge (riêng commit này là ~9,239 dòng xóa). Việc này lặp lại qua nhiều merge khiến số liệu bị thổi phồng và gán nhầm tác giả.

Số liệu thêm/xóa chính xác theo từng thành viên (lấy từ `git log`, loại trừ merge commit):

| Tác giả | Dòng thêm | Dòng xóa |
|---|---:|---:|
| Nhat Nguyen | 37,807 | 38,319 |
| KhanhNLH | 22,918 | 13,392 |
| KTrangg | 22,498 | 2,179 |
| Đào Hoàng Nhật | 18,634 | 1,822 |
| hoangnhat1407 | 5,939 | 14 |

---

## 7. Cách kiểm chứng độc lập

Mọi số liệu trên có thể tái lập bằng các lệnh sau trên repo:

```bash
# Tổng số dòng thêm/xóa trong kỳ
git log --since="2026-05-22" --until="2026-06-23" --pretty=tformat: --numstat \
  | awk '{add+=$1; del+=$2} END {printf "add=%d del=%d\n", add, del}'

# Xác nhận commit rebuild FE là viết lại (xóa xấp xỉ thêm)
git show fb4ddb3 --numstat | sort -k2 -rn | head

# Liệt kê các commit dọn dẹp có chủ đích
git log --oneline --grep="remove\|delete\|refactor\|unused"

# Chứng minh merge commit làm phình số dòng xóa
git show aa3cd62 --numstat | awk '{d+=$2} END {print "del trong merge =", d}'
```

---

## 8. Kết luận

Số dòng code thêm/xóa lớn trong kỳ phản ánh đúng quá trình phát triển thực tế của nhóm: khởi tạo dự án, cài đặt phụ thuộc (tệp tự sinh), tái cấu trúc giao diện front-end, và dọn dẹp mã/thư viện không dùng. Việc sử dụng công cụ hỗ trợ lập trình được áp dụng minh bạch và do chính các thành viên điều hướng. Toàn bộ lịch sử commit gắn với tài khoản từng thành viên và có thể kiểm chứng độc lập qua git. Không có dấu hiệu của dữ liệu được tạo hoặc can thiệp từ bên ngoài.

---

## Phụ lục

- `docs/github issue 2306 report/commit-stats-evidence.md` — bảng số liệu chi tiết
- `docs/github issue 2306 report/commit-stats-evidence.csv` — số liệu thô dạng bảng tính
