# Nhật Ký Refactor — Dọn dead code phát sinh SAU MERGE — Ngày 19/06/2026

Hậu đợt dọn rác 3 phase (DB/BE/FE). Sau khi `refactor/cleanup` được **merge vào `develop`** (commit `7fb33b1`, đã push lên origin) cùng với việc teammate pull về trước đó, phát sinh dead code mới. Xem thêm: `AI_LOG_SEAL_REFACTOR_CLEANUP_{DB,BE,FE}_2026-06-19.md`.

---

## 1. Bối cảnh & câu hỏi
User hỏi: sau merge develop có còn "sạch" như ngay sau lúc refactor không? Build PASS chỉ chứng minh **không vỡ**, không chứng minh **hết dead code**. → Chạy lại bộ phát hiện dead-code trên develop hiện tại.

## 2. Phát hiện: "semantic merge" tạo dead code (không báo conflict)
- Lúc refactor (trên develop cũ), 7 component shadcn `ui/*` còn được feature dùng (button 4 refs…) → **cố tình giữ lại 8 file** (7 + `utils`).
- Phía teammate (nhánh kia của merge) đã **migrate toàn bộ feature sang `PixelComponents.tsx`** (PixelButton/PixelInput/PixelBadge…), gỡ sạch import `ui/*`.
- Khi merge: git ghép êm "tôi giữ button.tsx" + "teammate bỏ người dùng button" = **button.tsx tồn tại nhưng 0 người dùng**, KHÔNG có conflict văn bản → lọt qua build.
- Xác nhận hiện trạng: `components/ui/` có **0 file import** trong toàn `src`; `cn()` không còn ai dùng ngoài thư mục ui; `PixelComponents` không import ui; `@mui` cũng 0 importer.

**Bài học:** merge giữa "xóa/giữ file" (một nhánh) và "đổi consumer" (nhánh kia) có thể regress độ sạch mà build vẫn xanh. Sau mỗi lần merge nhánh refactor, phải **chạy lại dead-code detection**, không chỉ dựa vào build.

## 3. Quyết định: KHÔNG rollback merge
User cân nhắc rollback. Tôi khuyến nghị KHÔNG, vì:
1. Merge `7fb33b1` **đã push lên `origin/develop`** → rollback = viết lại history chung, phá teammates đã pull.
2. Mất cân xứng: rollback vứt luôn **79 file / −9287 dòng** refactor chỉ để xử lý 9 file.
3. Merge không hỏng (build/test PASS); chỉ là 9 file mồ côi → việc xóa 1 commit.
(`git revert -m 1` cũng không hợp: nó hoàn tác chính refactor.)

## 4. Đã xóa (9 file) — commit trên `develop`
`shared/components/ui/{badge,button,card,input,select,separator,textarea}.tsx` + `ui/utils.ts` (thư mục `ui/` biến mất) + `shared/components/ImageWithFallback.tsx`.
- Commit: `refactor(web): remove shadcn/ui leftovers orphaned by Pixel migration` (không Co-Authored-By, chưa push).
- Verify: `vite build` PASS, `tsc` vẫn **7 lỗi CÓ SẴN** (DashboardLayout/ProfilePage — avatar_url/studentId/university/null-check), `vitest` 28/28 PASS.

## 5. Còn lại
- develop giờ đã sạch dead-code trở lại (đã quét: 0 orphan ngoài 9 file vừa xóa).
- Chưa push commit dọn này — user tự push.
- Việc lớn còn lại: release `develop → main`. Follow-up: 7 lỗi type có sẵn; (BE) tách TeamService / đổi tên AssignmentController.
