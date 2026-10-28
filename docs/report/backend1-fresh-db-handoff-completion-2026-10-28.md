# Hoàn thiện code và bộ bàn giao BE1 cho RDS mới

Cập nhật bàn giao: sau báo cáo này người dùng đã duyệt commit/push lên nhánh mới feature/backend1-be2-handoff. Xem Git HEAD của nhánh để lấy revision hiện hành; không dùng SHA nền hoặc hash artifact local làm release revision. Phạm vi AWS/deploy chưa nghiệm thu vẫn như báo cáo.

Ngày28/10/2026. Người dùng yêu cầu kiểm phần còn thiếu và sửa để bàn giao BE2;vẫn giữ điều kiện review/OK trước commit/push. Không stage/commit/push/deploy/AWS trong lượt này.

## Đã xử lý

| Khoảng trống | Thay đổi và nghiệm thu |
|---|---|
| Gate yêu cầu V3 khiến DB rỗng không deploy lần đầu được | Thêm CLI initialize-empty và DEPLOY_DB_MODE có chọn rõ. Đúng target/engine/case/catalog/charset trước ghi,V1–V3 từ candidate,gate đầy đủ trước compose up. Verify-only vẫn mặc định |
| Rủi ro migrate nhầm target rồi mới phát hiện URL khác | So migration URL với runtime URL trước kết nối/ghi;test không kết nối xác nhận reject khi thiếu/sai expected target |
| DB cũ không rõ nguồn bị đưa vào đường auto-init | Unknown schema,V1/V2,baseline3 giả,history/checksum/schema drift bị từ chối;không tự baseline/repair/clean |
| Chưa có nghiệm thu JAR khởi tạo DB và runtime ít quyền | Thêm9 MySQL cases, gồm packaged CLI thật,runtime DML-only Flyway no-op/CRUD và CREATE TABLE bị chặn |
| Thiếu cấu hình deploy có thể điền | Thêm Compose backend-only,settings,migration.env,worker.env templates. Mount CA truststore giống nhau cho CLI/runtime;env_file raw,revision,loopback cho proxy |
| Tài liệu rời rạc và tên launcher cũ | Thêm current-handoff làm entry point từ root README/docs README;cập nhật operator runbook sang fcaj-api/com.fcaj,DB mới và DB chung có owner migration |
| Private deploy files có thể vào Git/Docker context | Bổ sung ignore runtime/migration env,settings,p12/jks và runtime upload directories;templates vẫn có trong source |

Giữ nguyên migration V1/V2/V3 và protocol/limits/quyền đã chốt. Không thêm model thật,FE job UI hoặc tool backfill demo. Bộ nguồn BE2 cũ giữ nguyên như bằng chứng nhận được;hướng dẫn mới ghi rõ thay đổi tích hợp cần BE2 xác nhận.

## Kiểm chứng

- 613 backend tests thực thi đạt trong lượt full package;8 MySQL-only cases skip ở lượt đó,sau đó9 cases phiên bản cuối đều chạy thật trong lab. Chi tiết nguyên nhân/số liệu/checkpoint tại [evidence mới](../checks/be1-be2-integration-2026-10-28/fresh-db-verification.md).
- 36/36 MySQL8.4 cases đạt,0 skipped:21 job integration+9 fresh deploy+6 concurrency. Các test chạy lại không cộng trùng thành số test độc lập.
- 8/8 deploy command-order tests và7/7 parser tests đạt;fixtures cũ/current samples validate đạt;PowerShell/Bash syntax/git diff --check đạt.
- Dockerfile thật build thành công;Compose template parse và synthetic container env probe đạt. Không phải smoke ứng dụng trên RDS/AWS.

Lượt MySQL đầu có1 ca timeout migration process;rerun riêng đạt,cập nhật bound riêng provisioning DDL rồi chạy lại toàn36 đạt. Không nới timeout nghiệp vụ,không bỏ assertion/case để nghiệm thu. Docker ban đầu tắt đã được khởi động để chạy lab;containers lab dừng/giữ sau kiểm.

## Trạng thái bàn giao

Phần code/local/docs sẵn sàng cho người dùng review để chuẩn bị source revision. Chưa có commit SHA/image digest phát hành;artifact working tree/hash ở evidence không thay thế revision. Không thể đánh dấu BE2 đã nhận source qua Git khi chưa push.

Điểm bắt đầu cho BE2: [hướng dẫn hiện hành](../plans/backend1-be2-current-handoff-2026-10-28.md). Các đầu vào còn thiếu:target RDS/EC2 và CA/network/identities/private config,prefix/provider AWS,worker/queue window,queue defaults120s,revision/ECR/proxy/log pipeline. DB PoC mới được hướng dẫn init V1→V2→V3;dữ liệu/file local cũ chỉ chuyển sau inventory/backup riêng. FE/AI cần duyệt contract;AWS/browser/model thật chưa nghiệm thu.
