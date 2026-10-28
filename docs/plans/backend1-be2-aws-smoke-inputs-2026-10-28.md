# Đầu vào còn cần cho smoke AWS — 28/10/2026

Hướng dẫn tích hợp mới nhất, gồm DB rỗng V1–V3 và deploy templates: [current handoff](backend1-be2-current-handoff-2026-10-28.md). Các số612/27 bên dưới ghi nhận bản rename trước khi bổ sung khởi tạo DB; xem [bằng chứng mới](../checks/be1-be2-integration-2026-10-28/fresh-db-verification.md) khi nghiệm thu bản mới.

BE1 đã đổi module sang `back-end/src/fcaj-api`, package `com.fcaj.hackathon`;612 test backend/package và27 test MySQL8.4 riêng đạt. Contract hiện hành: [CURRENT.md](../contracts/ai-job-v1/CURRENT.md). Báo cáo: [review tích hợp](../report/backend1-be2-integration-readiness-review-2026-10-28.md). Tài liệu này để người dùng chuyển cho BE2 khi phù hợp; chưa gửi tự động.

## Đã nhận, không cần cấp lại từ đầu

- IAM role đã được BE2 giao theo xác nhận28/10; chưa kiểm từ tiến trình Java.
- Region ap-southeast-1; bucket fcaj-poc-data-582129522297-ap-southeast-1.
- Jobs fcaj-poc-ai-jobs, results fcaj-poc-ai-results; URL/ARN đủ trong [nguồn BE2](../checks/be1-be2-integration-2026-10-28/be2-handoff-source.md).
- Quyết định queue visibility120s và target MySQL8.4; không đồng nghĩa đã áp lên AWS.

## Cần BE2 xác nhận trước Receive/DeleteMessage thật

| Đầu vào | Cần trả lời | Khi chưa có |
|---|---|---|
| APP_STORAGE_PREFIX | Prefix thực được role cho phép; backend/worker dùng chung. Ví dụ về cấu trúc, không phải giá trị đã chọn: prefix/inputs/{jobId}/source.json | Không đoán hoặc thử ghi S3 vào prefix bất kỳ |
| Credential provider cho Java local | Hướng dẫn dùng role đã giao: profile/SSO/assume-role theo setup nhóm; tên profile/region/role có thể cung cấp, secret qua kênh operator an toàn | Tiếp tục smoke local giả lập; Console login chưa thay thế credentials ứng dụng |
| Quyền namespace | Xác nhận inputs/rubrics/results cho jobs và problems/avatars cho file. Backend/worker actions theo báo cáo BE1, cùng bucket/prefix | Ghi rõ phạm vi smoke có thể chạy sau khi xác nhận, không mở rộng IAM tự động |
| Worker và cửa sổ test | Ai chạy MOCK CLI, identity nào; xác nhận dùng cặp queue đã nhận, thời gian bắt đầu/kết thúc, consumer khác đã được điều phối | Không nhận/xóa message; backend job mock giữ pending khi thiếu worker |
| Queue defaults | BE2 áp120s cho hai queue chính, xác nhận long poll20s,retention4 ngày,DLQ14 ngày,maxReceiveCount5 | Local overrides đã120s; chưa đánh dấu AWS config đạt |

Không cần gửi access key/token/password vào chat. Không cần EC2/RDS để bắt đầu giai đoạn transport: có thể dùng backend/worker local và MySQL8.4 lab riêng sau khi đủ các đầu vào trên. Test application trên EC2/RDS là giai đoạn khác.

## Khi chưa có đầu vào

BE1 bàn giao source/contract/evidence/local runner để review; APP_AI_JOBS_ENABLED vẫn mặc định false trong cấu hình AWS mẫu. Mock smoke trong test chạy riêng bằng dữ liệu giả; không mở runtime endpoint tùy ý để thay GitHub và không bỏ kiểm quyền/CSRF. Chưa ghi AWS pass, chưa deploy, chưa chạy CLI worker với queue thật.

Khi đủ cấu hình, hai bên ghi identity/account/region/prefix/queue pair/revision/test data trước buổi smoke; bật consumer theo lịch, tạo job mới, chạy worker success/failure trên hai job riêng, kiểm duplicate/retry/DB commit trước ack. Lưu evidence đã lọc, không purge hoặc replay DLQ để dọn test. Prefix và provider có thể chốt trước EC2/RDS.

## Đầu vào cho giai đoạn deploy sau

BE2 dựng EC2/RDS8.4, case1 ngay khi tạo DB, TLS VERIFY_IDENTITY, catalog/user/runtime-migration identities, CA/trust/network/HTTPS và compose production. Dùng V1→V2→V3; DB cũ qua backup/rehearsal/baseline1 được kiểm. Xác nhận mapping fcaj-api/com.fcaj, service gate hiện còn seal-backend và settings /etc/seal/deploy-settings.sh. Không dùng compose MySQL8.0 hiện có làm target AWS.
