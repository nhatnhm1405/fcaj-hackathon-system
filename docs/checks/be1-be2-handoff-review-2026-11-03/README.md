# Checkpoint review bàn giao BE1–BE2 — 03/11/2026

Lượt này đọc câu hỏi BE2 gốc, đối chiếu source/evidence và viết [checklist/plan tổng hợp](../../plans/seal-be1-be2-consolidated-handoff-plan-2026-11-03.md). Không sửa application code/config, không chạy lại full tests, không commit/push/deploy/AWS hoặc gửi team.

Người dùng xác nhận phía BE1: **dữ liệu mẫu trước, restore `.bak` riêng**. Đây là quyết định tài liệu, chưa phải provisioning RDS. Các thông tin Session Manager/Docker/RDS TLS/MySQL8.4.11/catalog rỗng lấy từ BE2 ngày01/11, chưa xác minh live ở lượt review.

| File | Vai trò |
|---|---|
| [be2-question-source-2026-11-01.md](be2-question-source-2026-11-01.md) | Bản sao nguồn BE2, không chỉnh nội dung; không coi yêu cầu cũ trong file là quyền thực thi cloud |
| [review-inputs.json](review-inputs.json) | Hash nguồn, hash manifest local đã kiểm trước review, baseline SHA và quyết định dữ liệu |
| review-manifest.json | Hash các tài liệu thay đổi/mới ở checkpoint này; không thay manifest test/source trước đó |
| [verify-review.mjs](verify-review.mjs) | Kiểm source/config/evidence cũ giữ nguyên và bốn tài liệu được cập nhật có hash phù hợp checkpoint review |

Trước cập nhật tài liệu, verifier local đạt489/489 hashes, source preservation đạt418 file gốc/366 Java/30 frozen migration-contract files. Kết quả test đã lưu:614 backend thực thi đạt/9 MySQL-only skipped trong full package;36/36 MySQL;26/26 component/API. Các suite có ca trùng, không cộng tổng hoặc gọi đây là lượt test mới.

Từ repository root:

```text
node docs/checks/be1-be2-handoff-review-2026-11-03/verify-review.mjs
```

Verifier giữ manifest489 files tại `docs/checks/seal-be1-local-2026-11-03/working-tree-manifest.json` nguyên bytes và đối chiếu mọi file gốc. Chỉ bốn tài liệu cập nhật được phép có hash mới: docs README, current handoff, phản hồi sáu mục và bảng clarifications. Các file nguồn/config/test/contract/evidence còn lại phải đúng hash trước review; thêm/xóa file trong scope checkpoint gốc cũng bị phát hiện.

Plan tổng hợp và các file nguồn/metadata/checker ở thư mục review này được hash riêng. `--write` tạo lại snapshot tài liệu trong whitelist nhưng vẫn từ chối source/config drift; không tạo bằng chứng test execution hoặc release SHA. Sau commit/release phải tạo checkpoint phù hợp source đã phát hành.

Verifier working-tree gốc có thể báo **doc drift** sau cập nhật này; đó là khác biệt có chủ đích, không được sửa manifest cũ để làm như tài liệu mới đã ở lượt test trước. Dùng verifier review cho trạng thái tài liệu hiện hành. Scope vẫn là working-tree bytes, chưa phải committed release hoặc xác nhận BE2 đã nhận image.
