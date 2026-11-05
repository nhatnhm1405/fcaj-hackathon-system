# Checkpoint sửa coverage JWT/session — 03/11/2026

Working tree trên `feature/backend1-be2-handoff`, baseline `bab44e5fdeb710e6c71982b2e8f5547829fa8e40`. Đây là checkpoint sau sửa test, chưa phải release SHA hoặc nghiệm thu AWS. Xem [báo cáo fix](../../report/seal-be1-jwt-session-regression-fix-2026-11-03.md) và [plan](../../plans/seal-be1-jwt-session-regression-fix-plan-2026-11-03.md).

| File | Nội dung |
|---|---|
| `before-files.json` | Hash các file hiện hữu trước lượt fix, gồm source, config, docs và evidence lịch sử; không dùng HEAD thay cho working tree chưa commit |
| `checkpoint.json` | Danh sách thay đổi/thêm và hash sau sửa; các file không nằm trong phạm vi thay đổi phải giữ bytes ban đầu |
| `test-results.json` | Commands, tool versions, per-suite counts và hash log/XML thô dưới ignored target; phân biệt helper-red, lỗi loopback sandbox và full package thành công |
| `auth-traces.json` | Method/path/status, session presence và không có Authorization header; không chứa token/cookie/password hoặc session ID |
| `api-rehearsal.json` | IDs synthetic và 100 request qua API sau khi helper giữ session; H2/GitHub/S3/SQS mocks theo scope test |
| `verify-checkpoint.mjs` | Verifier chỉ đọc: inventory, hash, production không đổi, tổng test và link tài liệu |

Từ repository root:

```text
node docs/checks/jwt-session-regression-2026-11-03/verify-checkpoint.mjs
```

Log/XML/JAR thô, bản helper cookie-only dùng cho đối chứng đỏ và probe cũ được giữ tại `back-end/src/seal-api/target/jwt-session-fix-20261103/`; không đưa vào bundle. Báo cáo đã lọc ghi đường dẫn/hash để đối soát khi còn output local.

Giữ nguyên manifest local và review trước fix. Các verifier cũ có thể báo drift vì source test/runner/docs đã thay đổi có chủ đích; dùng checkpoint mới để kiểm trạng thái này, không sửa checksum cũ để giả rằng test mới đã nằm trong lượt kiểm trước.

Test dùng session thật trong harness MockMvc; chưa phải browser hoặc OAuth provider. Production/migrations/contracts không đổi. MySQL/image local trước đó giữ phạm vi lịch sử; không chạy lại hoặc tính vào kết quả mới. AWS/ALB/RDS/IAM/S3/SQS live, restart toàn process và release vẫn là các mốc riêng.
