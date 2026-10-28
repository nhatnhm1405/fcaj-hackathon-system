# MySQL8.4 V2 và job integration — evidence đã lọc

Ngày 27/10/2026, Asia/Saigon. Không có row values, password hay token trong tài liệu này.

| Kiểm tra | Kết quả |
|---|---|
| Backend package | 591 tests,0 failures,0 errors,0 skipped; BUILD SUCCESS,23:07:33+07:00 |
| MySQL job/API integration | 19 tests,0 failures/errors/skipped; BUILD SUCCESS,23:06:18+07:00 |
| Server | MySQL8.4.13, pinned image `mysql@sha256:76f0358884cb92d9a086d24e5671c38048d3fef40f2588703d73eb55392d6b69` |
| Driver/runner | Connector/J9.7.0; Flyway11.14.1; Java21; Boot4.0.6 |
| Case/charset | lower_case_table_names=1; utf8mb4/utf8mb4_0900_ai_ci |
| Restore | 31 core tables,253 columns,627 rows; từng table count đúng dump; FK/unique/auto-increment checks đạt |
| Baseline runner | Kiểm đúng catalog/server/case, backup SHA và structural fingerprint V1 trước marker; không tự baseline khi boot |
| V2 restore | 34 business/delivery tables,627 legacy rows giữ nguyên fingerprint;61 FK checks đạt; ba bảng mới rỗng |
| V2 new schema | V1→V2 thành công; Hibernate validate và API/JPA/JDBC hoạt động; restart/repeat migration no-op |
| Negative controls | Checksum mismatch và thiếu cột bị chặn; unknown non-empty DB bị chặn trước explicit baseline |
| JSON/static tools | 7 fixture JSON hợp lệ;7 parser tests đạt;19 demo preflight checks đạt |
| Artifact | JAR Spring Boot đóng gói đúng application main; có V1/V2/baseline fingerprint/job APIs;0 H2 runtime libraries |

V1 SHA-256 `91a3b9b0cb4d41ce5027fa850200848126bdfe57ba0ceef97defa1ff7928ea9d`; Flyway checksum `-732525868`.

V2 SHA-256 `4e40d1f4370c2324e90cd5d1c7e7396c2ce068e2331682421fb5f1e0a3aa4ea7`.

Backup SHA-256 `ab6279c0ad77be4405ca1505c1da8b7db2a1a7c6b9e052db5b672790e1f23802`.

V1 structural fingerprint `6a18f5a3f982daab7257cc0a72441840d64a5c171be51835b10ee79c14466c6e`.

JAR SHA-256 `c352fee336dbedec34e5ee237a87063885cff4bfbf7aa26ebb339e08e3da3d5a`.

Evidence local trong `back-end/src/seal-api/target/`: `be1-package.log`, `fcaj-ai-lab-c6c804351ddc-test.log`, `fcaj-migration-lab-339ddbccfa7c/lab-evidence.md` và `operator-baseline.log`. Cả hai container cuối đã Exited(0); các container rehearsal trước cũng đã dừng, dữ liệu được giữ để kiểm tra.

19 integration cases gồm API/JWT, scope/requester/revoke, snapshot recheck, job+outbox rollback, send retry, concurrent claim/lease recovery, success/failure, identity/checksum/rubric/evidence rejection, terminal conflict, duplicate ledger khi S3 lỗi, DB fail trước ack, late dispatcher, restart đọc lại và job cũ không thay job mới. S3/SQS/GitHub trong test dùng mocks; MySQL và migrations là thật. Worker CLI có tests riêng cho write→send→ack, send failure không ack và checksum sai.

Flyway11.14.1 cảnh báo MySQL8.4 mới hơn engine đã verified8.1; các kiểm tra nêu trên vẫn đạt. Không suy ra RDS/IAM/queue hoặc model thật đã đạt. Script CI Linux đã kiểm cú pháp; workflow remote chưa chạy. Không sửa dump gốc, DB đang dùng hay tài nguyên AWS.
