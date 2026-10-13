# Giải trình số dòng code thêm/xóa (May 22 – Jun 22, 2026)

> Toàn bộ số liệu dưới đây trích trực tiếp từ `git log` của repo và **có thể kiểm chứng lại** bằng các lệnh kèm theo ở cuối. Hội đồng có thể tự chạy để xác nhận.

## TL;DR (kết luận nhanh)

1. **Số dòng xóa lớn KHÔNG phải mất code.** ~31% số dòng xóa là **file tự sinh / thư viện third-party** (npm `package-lock.json`, thư viện UI `shadcn`, Figma export), không ai gõ tay.
2. **Phần lớn còn lại là REWRITE, không phải xóa trắng** — front-end vibe-code sai giao diện ở vòng đầu nên được viết lại; file bị xóa rồi viết lại ngay trong cùng commit (số xóa ≈ số thêm).
3. **Repo tăng ròng:** ~125,000 dòng thêm vs ~86,000 dòng xóa → codebase lớn hơn, không bị "phá".
4. **Cảnh báo về biểu đồ Contributors của GitHub:** số liệu trên biểu đồ bị **thổi phồng và gán sai** do GitHub tính cả merge commit (xem mục 4). Nguồn đáng tin là `git log`, không phải biểu đồ.

---

## 1. Phân loại dòng XÓA theo loại file

(tất cả nhánh, trong kỳ; `git log --numstat`)

| Loại file | Dòng xóa | Bản chất |
|---|---:|---|
| `package-lock.json` | ~18,349 | **File tự sinh** bởi npm — không gõ tay |
| `components/ui/` (shadcn) | ~7,183 | **Thư viện UI third-party** sinh sẵn |
| Figma import (`imports/`) | ~1,272 | **Code/asset Figma export tự động** |
| Ảnh (svg/png/jpg…) | ~54 | Binary asset |
| **→ Cộng "KHÔNG phải code tay"** | **~26,858 (~31%)** | |
| SQL / DB scripts | ~1,381 | Dọn script DB trùng/cũ |
| Docs (md/txt) | ~1,588 | Dọn tài liệu trùng |
| JSON khác (config/Postman) | ~1,003 | Collection/config |
| Code tay (.tsx/.ts/.java…) | ~57,014 | **Phần lớn là rewrite** (xem mục 2) |

---

## 2. Bằng chứng "đập đi xây lại" là REWRITE (xóa ≈ thêm trong cùng file)

Commit rebuild front-end `fb4ddb3` ("feat: mapping API frontend"):

| File | Xóa | Thêm | Nhận xét |
|---|---:|---:|---|
| `LandingPage.tsx` | 805 | 805 | viết lại nguyên file |
| `PixelComponents.tsx` | 569 | 569 | viết lại nguyên file |
| `SealFooter.tsx` | 155 | 155 | viết lại nguyên file |
| `LoginPage.tsx` | 141 | 134 | viết lại nguyên file |

→ Mẫu hình kinh điển của refactor/rewrite: giao diện cũ sai bị thay bằng bản mới trong cùng file.

---

## 3. Các commit "dọn dẹp" lớn — đều có chủ đích & ghi rõ lý do

| Commit | Ngày | Xóa | Lý do (commit message) |
|---|---|---:|---|
| `238816b` | 09/06/2026 | 5,606 | replace `package-lock.json` (file auto) |
| `373cd02` | 29/05/2026 | 4,763 | xóa folder cũ |
| `cb66700` | 19/06/2026 | 4,652 | remove 40 **unused shadcn/ui components** |
| `087a4ce` | 19/06/2026 | 2,378 | remove pages **superseded by the router** |
| `3b397b7` | 19/06/2026 | 1,272 | remove **orphan Figma import assets** |
| `9602767` | 01/06/2026 | 1,145 | delete **old SpringBoot project**, restructure |
| `b34cb6b` | 19/06/2026 | 192 | remove redundant migration & demo scripts |

---

## 4. Các commit có số dòng BẤT THƯỜNG (và lý do hợp lệ)

Hầu hết các "đỉnh" số dòng là **file tự sinh** (`package-lock.json`, Postman collection) hoặc **file test / thư viện**, KHÔNG phải logic viết tay:

| Commit | Ngày | Tác giả | Thêm | Xóa | File | Bản chất |
|---|---|---|---:|---:|---|---|
| `9684b59` | 17/06/2026 | KhanhNLH | 7,966 | 2 | `package-lock.json` | auto (vitest setup) |
| `de9750e` | 29/05/2026 | KTrangg | 5,876 | 337 | `package-lock.json` | auto (shadcn setup) |
| `fb4ddb3` | 03/06/2026 | Nhat | 3,164 | 8,407 | `package-lock.json` | auto (rebuild FE) |
| `73095d5` | 29/05/2026 | KhanhNLH | 2,768 | 0 | `package-lock.json` | auto (init Vite) |
| `f0a6066` | 29/05/2026 | KhanhNLH | 2,504 | 362 | `package-lock.json` | auto (install deps) |
| `bdfe0d4` | 12/06/2026 | Nhat | 1,674 | 412 | `Postman_Full_Collection.json` | auto (API collection export) |
| `7e2c879` | 18/06/2026 | Đào Hoàng Nhật | 1,169 | 0 | `TeamServiceTest.java` | file **test** (hợp lệ) |
| `eebbda5` | 09/06/2026 | KhanhNLH | 1,031 | 0 | `Postman_Full_Collection.json` | auto (export) |
| `fb4ddb3` | 03/06/2026 | Nhat | 951 | 74 | `ParticipantDashboard.tsx` | feature mới |
| `d80f23a` | 21/06/2026 | Đào Hoàng Nhật | 726 | 0 | `ui/sidebar.tsx` | thư viện shadcn |

→ Trong top "đỉnh dòng", **5/6 cái lớn nhất là `package-lock.json`** (npm tự sinh). Đây là nguyên nhân chính làm con số phình to.

---

## 5. ⚠️ Vì sao biểu đồ Contributors của GitHub gây hiểu lầm

Biểu đồ **Contributors** (ví dụ ghi `KTrangg` xóa **36,907** dòng) **không khớp** với `git log`:

| Tác giả | Xóa theo `git log` (thực) | Xóa trên biểu đồ GitHub |
|---|---:|---:|
| KTrangg | **~2,179** | 36,907 ❌ |

**Nguyên nhân:** GitHub tính cả **merge commit**. Khi `KTrangg` merge `develop` vào nhánh feature (commit `aa3cd62` "merge: integrate origin/develop"), GitHub gán **9,239 dòng xóa của người khác** cho cô ấy. Cộng dồn nhiều merge → con số bị thổi phồng và **gán sai người**.

→ **Nguồn số liệu đáng tin là `git log` (bảng dưới), không phải biểu đồ Contributors.**

### Số dòng thêm/xóa theo từng người (git log, tất cả nhánh, loại bỏ merge)

| Tác giả | Thêm | Xóa |
|---|---:|---:|
| Nhat Nguyen | 37,807 | 38,319 |
| KhanhNLH | 22,918 | 13,392 |
| KTrangg | 22,498 | 2,179 |
| Đào Hoàng Nhật | 18,634 | 1,822 |
| hoangnhat1407 | 5,939 | 14 |

---

## 6. Lệnh để hội đồng tự kiểm chứng

```bash
# Tổng thêm/xóa trong kỳ
git log --since="2026-05-22" --until="2026-06-23" --pretty=tformat: --numstat \
  | awk '{add+=$1; del+=$2} END {printf "add=%d del=%d\n", add, del}'

# Commit rebuild FE: thấy file bị viết lại (xóa ≈ thêm)
git show fb4ddb3 --numstat | sort -k2 -rn | head

# Các commit dọn dẹp có chủ đích
git log --oneline --grep="remove\|delete\|refactor\|unused"

# Chứng minh merge commit thổi phồng số xóa của KTrangg
git show aa3cd62 --numstat | awk '{d+=$2} END {print "del trong merge =", d}'
```
