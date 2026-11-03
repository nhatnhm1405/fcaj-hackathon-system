# SEAL Hackathon System

Backend: `back-end/src/seal-api`, Java21/Spring Boot, package `com.seal.hackathon`.

- [Bắt đầu bàn giao BE1–BE2](docs/plans/backend1-be2-current-handoff-2026-10-28.md).
- [Review trước commit/push](docs/plans/backend1-final-pre-push-review-checklist-2026-10-28.md).
- [Contract AI job](docs/contracts/ai-job-v1/CURRENT.md).
- [Kết quả BE1 local ngày 03/11](docs/report/seal-be1-local-completion-2026-11-03.md).
- [Runbook dữ liệu và kiểm luồng API MOCK](docs/plans/seal-be1-smoke-data-runbook.md).

Database chuẩn hiện tại dùng Flyway V1→V2→V3. RDS mới cần catalog rỗng đã cấu hình đúng; DB cũ cần quy trình backup/review riêng trước baseline. `docker-compose.yml` ở root vẫn phục vụ môi trường dev MySQL8.0; xem hướng dẫn bàn giao để chuẩn bị cấu hình AWS.
