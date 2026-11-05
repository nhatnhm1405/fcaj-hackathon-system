# Evidence BE1 local — 03/11/2026

Candidate working tree trên baseline `bab44e5fdeb710e6c71982b2e8f5547829fa8e40`, branch `feature/backend1-be2-handoff`. Chưa có release SHA/commit/push/deploy/AWS smoke mới. Xem [báo cáo kết quả](../../report/seal-be1-local-completion-2026-11-03.md).

| File | Nội dung |
|---|---|
| baseline-files.json / baseline-status.txt | Inventory hash trước rename và trạng thái có trước; không phải manifest release |
| preexisting-documents.json | Hash năm tài liệu có trước; bản nội dung giữ dưới ignored target/preexisting-documents |
| clean-package-tests.json | Per-suite count full clean package:623 thống kê,614 thực thi đạt,9 MySQL-only skipped |
| mysql-tests.json |36/36 MySQL8.4:21 job +9 fresh deploy +6 concurrency; container đã dừng/giữ |
| component-smoke-tests.json |26/26 H2 production-component + API rehearsal; có ca trùng suite khác |
| api-rehearsal.json |100 HTTP requests, IDs giả, hai job riêng, phạm vi MockMvc/H2/mocks rõ; không credential/token/body tài khoản |
| request-templates.json | Request placeholders để operator điền riêng; không gửi thẳng như request thật |
| filtered-test-logs.json | Summary đã lọc và hash raw logs dưới ignored target; không copy raw logs/private data |
| source-preservation.json |418 file gốc còn đủ,366 Java gốc đối chiếu rename;30 frozen migration/contract files giữ bytes; JWT filter là ngoại lệ logic được review |
| jar-identity.json / image-identity.json | Hash/JAR entrypoints/migrations và Docker image local, không phải release/ECR receipt |
| verification-summary.json | Tool versions, checks bổ sung, giới hạn và trạng thái local |
| working-tree-manifest.json | Hash bytes hiện hành trong scope source/config/current docs/evidence; không chứa target/private files |

Từ repository root:

```text
node docs/checks/seal-be1-local-2026-11-03/verify-source-preservation.mjs
node docs/checks/seal-be1-local-2026-11-03/verify-working-tree.mjs
```

Verifier preservation đọc baseline inventory và Git source tại baseline; đối chiếu main/test Java sau rename, giữ một ngoại lệ JWT/CSRF được báo cáo. Verifier working-tree đọc nội dung file hiện tại, không đọc staged/HEAD thay cho candidate chưa commit. Hash khớp chỉ chứng minh bytes; kết quả test nằm trong các summary riêng.

Các manifest/checksum ngày28 không sửa để giả thành evidence ngày03/11. Khi source thay đổi, chạy check phù hợp và tạo checkpoint mới; khi có release SHA được người dùng chốt, tạo manifest release riêng. Raw generated artifacts được giữ dưới `back-end/src/seal-api/target/`; không stage chúng.

Local checks không nghiệm thu live ALB/RDS/JVM TLS/IAM/S3/SQS/browser/restart/model thật. CloudFront/domain/ALB-direct và operator window vẫn cần FE/BE2 xác nhận.
