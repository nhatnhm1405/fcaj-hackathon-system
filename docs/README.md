# Tài liệu SEAL Hackathon

**Cập nhật coverage JWT/session03/11:** [báo cáo fix](report/seal-be1-jwt-session-regression-fix-2026-11-03.md) và [checkpoint sau sửa](checks/jwt-session-regression-2026-11-03/README.md). P2 đã đóng bằng test/helper; production không đổi. Full package624 thực thi đạt/9 MySQL-only skipped, component36/36; chưa có release/AWS acceptance mới.

Điểm bắt đầu bàn giao BE1–BE2: [hướng dẫn tích hợp hiện hành, cập nhật03/11](plans/backend1-be2-current-handoff-2026-10-28.md). Candidate SEAL mới đang ở working tree, chưa có release SHA mới.

**Review theo câu hỏi gốc BE2:** [checklist và plan bàn giao tổng hợp](plans/seal-be1-be2-consolidated-handoff-plan-2026-11-03.md). Phía BE1 đã chốt dữ liệu mẫu trước, restore `.bak` riêng; mốc release/target/worker vẫn cần phối hợp. [Checkpoint review tài liệu](checks/be1-be2-handoff-review-2026-11-03/README.md) giữ nguyên bằng chứng test/source local và kiểm phần tài liệu cập nhật riêng.

- [Checklist review trước commit/push](plans/backend1-final-pre-push-review-checklist-2026-10-28.md).
- [Contract AI job hiện hành](contracts/ai-job-v1/CURRENT.md).
- [Kết quả thực hiện BE1 local ngày03/11](report/seal-be1-local-completion-2026-11-03.md).
- [Evidence và verifier working-tree ngày03/11](checks/seal-be1-local-2026-11-03/README.md).
- [Runbook dữ liệu và rehearsal API](plans/seal-be1-smoke-data-runbook.md).
- [Runbook runtime, ALB và matrix network/IAM](plans/seal-be1-runtime-config-runbook.md).
- [Bảng BE2 cần làm rõ Q01–Q10](plans/seal-be1-be2-clarifications-2026-11-03.md).
- [Plan thảo luận và bàn giao với AI Engineering](plans/backend1-ai-engineering-discussion-handoff-plan-2026-10-28.md).
- [Plan sửa và thảo luận BE1–BE2 trước deploy AWS ngày 03/11](plans/backend1-be2-predeploy-execution-discussion-plan-2026-11-03.md).
- [Plan giao AI thực hiện BE1 trước báo cáo và bàn giao BE2 ngày 03/11](plans/seal-be1-ai-execution-plan-2026-11-03.md).
- [Bản phản hồi BE1 gửi thảo luận với BE2 trước deploy ngày 03/11](plans/seal-be1-tra-loi-be2-truoc-deploy-2026-11-03.md).
- [Đầu vào AWS cần BE2 xác nhận](plans/backend1-be2-aws-smoke-inputs-2026-10-28.md).
- [Báo cáo review tích hợp](report/backend1-be2-integration-readiness-review-2026-10-28.md).

Tài liệu cũ được giữ để truy lịch sử; số test/path/quyết định lịch sử không thay thế hướng dẫn hiện hành hoặc xác nhận deploy AWS.
