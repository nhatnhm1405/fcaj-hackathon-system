# BE1 → BE2 — Đầu vào cần chốt sau candidate local SEAL

Ngày 03/11/2026, Asia/Saigon. Người dùng xác nhận chưa có cập nhật mới từ BE2; các phương án mới còn đang thảo luận. Bản này để người dùng review và mang vào buổi trao đổi, **chưa được gửi team**.

Cập nhật sau khi đọc file BE2 gốc: xem [plan bàn giao tổng hợp](seal-be1-be2-consolidated-handoff-plan-2026-11-03.md). BE2 đã báo có Session Manager/Docker/RDS TLS; không hỏi lại các việc đã báo xong. Người dùng đã chốt phía BE1 **dữ liệu mẫu trước, restore `.bak` riêng**, đúng hướng BE2 đề xuất; chỉ còn operator/provisioning/cleanup trên target. Tên SEAL và việc hoãn ECR khác bảng/phân công ngày01/11 nên cần xác nhận lại với BE2.

BE1 đã chuẩn bị module/package SEAL, gate/runners, templates tách topology, runtime/network/IAM matrix, runbook và rehearsal API local. Kết quả, source checkpoint và giới hạn xem [báo cáo local](../report/seal-be1-local-completion-2026-11-03.md). Working tree chưa commit/push, chưa có release SHA mới. SHA `bab44e5fdeb710e6c71982b2e8f5547829fa8e40` chỉ là baseline trước sửa.

| ID | Thông tin cần nhận | Vì sao / phần live phụ thuộc | Người trả lời | Trạng thái |
|---|---|---|---|---|
| Q01 | Branch/full SHA/diff infra/deploy hiện tại; ai sửa file dùng chung? | Ghép source SEAL với hạ tầng đúng revision, tránh lặp rename cũ | BE2 | Pending |
| Q02 | ALB direct hay proxy; target port/protocol, listener/TLS, health path và scheme/host? | Chọn đúng Compose, publish interface/SG, kiểm login/redirect qua ALB. BE1 có cả template loopback và direct:8080 để review | BE2 | Pending; direct là đề xuất |
| Q03 | FE S3 phục vụ HTTPS/domain thế nào; có CloudFront không; API domain/VITE_API_URL? | CORS/credentials/cookie/CSRF và OAuth callback browser; raw domain khác gốc chưa được nghiệm thu | FE + BE2 | Pending |
| Q04 | Private EC2/container có DNS/route tới RDS, api.github.com, S3/SQS, SSM và nguồn build bằng cách nào? | Collector/SDK/provision/build từ máy private. Matrix đã có, không tự chọn NAT/proxy/endpoints thay BE2 | BE2 | Pending |
| Q05 | RDS còn rỗng không; endpoint/port/catalog/engine/case/charset/collation, CA/trust và hai DB identities đã sẵn sàng? | Operator init V1→V2→V3/gate/JVM VERIFY_IDENTITY đúng target; runtime DML-only. Không gửi password trong bảng trả lời | BE2 | Pending |
| Q06 | Bucket/prefix, policy đã lọc, provider/role thực cho container/worker; KMS/versioning nếu có? | Đối chiếu actions/resource và live file/job storage. Mẫu không cấp IAM | BE2 | Pending |
| Q07 | Jobs/results queue URLs/region/default visibility120s, long poll20s, DLQ/consumer/backlog? | Delivery/read/delete đúng queue pair; không purge queue để làm demo | BE2 | Pending |
| Q08 | ECR pipeline hoãn: có chọn build-on-host từ release qua Session Manager hiện có không; nếu giao image sẵn thì gate nào? | Script hiện tự build, cần Git/Python3/registry access trên host; chưa có mode nhận prebuilt image. Không tạo SSM/ECR pipeline mới trong lượt review | BE2+BE1 | Pending phương án giao/build |
| Q09 | Với dữ liệu mẫu đã được BE1 chấp thuận, ai provisioning/cleanup trên RDS và dataset/window nào? | BE1 có runbook hai đội × ba người và rehearsal qua API. Không dùng script reset/demo trên RDS | BE2 + operator được chỉ định | Lựa chọn dữ liệu đã chốt phía BE1; vận hành target pending |
| Q10 | Ai chạy worker, ở đâu, bằng identity nào và window nào; full log/SHA/lệnh/JDK/OS/profile/DB của hai test lỗi cũ? | Chạy success/failure hai job riêng; điều tra chênh môi trường. Candidate local pass không chứng minh root cause lỗi BE2 đã sửa | BE2 + worker operator | Pending |

Tên người phụ trách và thời hạn do owner xác nhận, chưa tự điền thay. Private env/secret/CA password đi qua kênh operator riêng; chỉ chia sẻ metadata và log đã lọc.

## Bộ BE1 cung cấp để review

- Source local: `back-end/src/seal-api`, package `com.seal.hackathon`; artifact vẫn `hackathon-0.0.1-SNAPSHOT.jar`.
- [Runbook runtime/ALB và matrix quyền/network](seal-be1-runtime-config-runbook.md), templates trong `back-end/src/seal-api/deploy/`.
- [Runbook dữ liệu/API/worker](seal-be1-smoke-data-runbook.md), [request JSON placeholders](../checks/seal-be1-local-2026-11-03/request-templates.json).
- [Bộ evidence/checkpoint mới](../checks/seal-be1-local-2026-11-03/README.md), verifier đọc working-tree bytes. Không dùng manifest HEAD ngày28 để chứng minh source chưa commit.
- Contract [MOCK hiện hành](../contracts/ai-job-v1/CURRENT.md): giữ ba API/bốn state, namespace `fcaj.ai.*`, object keys và policy quyền. AI model thật và FE job UI là mốc khác.

## Mốc phối hợp tiếp theo

1. Người dùng review candidate local và bộ evidence. Khi có yêu cầu commit/push riêng mới chốt release SHA; không coi local image là image BE2 đã nhận.
2. BE2 trả lời phần target/topology/identity/delivery để chọn cấu hình và người chạy; câu hỏi chưa trả lời chỉ giữ pending các live checks liên quan.
3. Operator kiểm source/image/target, migration/gate/runtime/TLS/ALB; sau đó file persistence và hai job success/failure MOCK trong window.
4. Ghi evidence target theo SHA/image/job/time và các ca quyền, restart/browser trong acceptance scope. Chưa có kết quả thì không đánh dấu đã deploy hoặc đã nghiệm thu AWS.
