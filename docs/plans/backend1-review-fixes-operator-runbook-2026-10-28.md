# Bàn giao vận hành sau sửa lỗi review BE1

Các sửa lỗi local chưa phải sự cho phép triển khai lên cloud. Target, identity, compose production và thời điểm cutover/rollback vẫn chờ BE2 xác nhận.

Cập nhật bàn giao RDS mới: module hiện là `back-end/src/fcaj-api`, package `com.fcaj.hackathon`. Xem [hướng dẫn tích hợp hiện hành](backend1-be2-current-handoff-2026-10-28.md) và templates trong `back-end/src/fcaj-api/deploy/` để chuẩn bị runtime/migration env, Compose và CA truststore. Bộ migration vẫn V1→V2→V3.

## Kiểm tra artifact trước deploy

Workflow hiện có yêu cầu file `/etc/seal/deploy-settings.sh` trên host. File phải cung cấp `DEPLOY_COMPOSE_FILE` (đường dẫn tuyệt đối tới compose production của BE2), `DEPLOY_MIGRATION_ENV_FILE` (file biến môi trường riêng), `DEPLOY_DB_CATALOG` và `DEPLOY_RUNTIME_JDBC_URL`. Không dùng compose cũ trong repository vì nó trỏ tới MySQL 8.0. Không có cấu hình dự phòng tự động.

File biến môi trường riêng cung cấp `BE1_MIGRATION_JDBC_URL`, `BE1_MIGRATION_USERNAME`, `BE1_MIGRATION_PASSWORD`. URL phải trùng URL runtime đã duyệt; tài khoản migration và runtime có thể khác nhau. URL kết nối từ xa phải dùng `sslMode=VERIFY_IDENTITY`, đồng thời artifact phải có cấu hình tin cậy CA phù hợp. Không đặt thông tin đăng nhập trong URL hoặc Git. Compose production phải dùng `${CANDIDATE_BACKEND_IMAGE}` tại `seal-backend.image`, chọn profile `aws` và khai báo rõ cùng URL runtime đó. Các image khác phải được chuẩn bị sẵn; lệnh thay service dùng `--no-build`.

`Test/deploy-verified.sh` build image dự định deploy từ SHA đã xác minh. Mặc định `DEPLOY_DB_MODE=verify-only` chỉ chạy gate đọc trước compose up. Operator chọn `initialize-empty` cho lần đầu vào RDS/catalog rỗng: CLI cùng candidate kiểm đúng runtime URL, engine/case/catalog/charset/collation rồi chạy V1→V2→V3; gate đầy đủ phải đạt trước thay service. Nếu DB không rỗng, mode này chỉ nhận DB đã đạt V3, không tự migrate DB cũ. Không tự baseline/repair/clean. DDL lỗi có thể để lại schema/history một phần và không tự rollback; cần đối soát/recovery riêng. Fingerprint cấu trúc V3 cố định là resource đã duyệt cho artifact; thay đổi schema sau này phải được review và cập nhật tham chiếu tương ứng, không bỏ qua phép kiểm này.

Lệnh CLI đã đóng gói, chạy bên trong image dự định deploy:

```sh
java -Dloader.main=com.fcaj.hackathon.db.DatabaseMigrationMain \
  -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher \
  verify-deploy TARGET_CATALOG
```

## Chuyển đổi bảo toàn dữ liệu

1. Xác định chính xác engine/catalog/artifact cũ và tất cả thành phần đang ghi dữ liệu. Tạo backup, kiểm chứng restore được, bao gồm các object avatar/đề thi lưu ngoài DB.
2. Chuẩn bị server MySQL 8.4 riêng với `lower_case_table_names=1` và charset/collation đã duyệt. Không dùng lại volume MySQL 8.0 trên engine khác hoặc đổi cấu hình phân biệt hoa/thường ngay trên volume đang dùng.
3. Rehearsal restore, đối chiếu cấu trúc, FK/unique key, số dòng và fingerprint tính trên các cột gốc. Chỉ baseline1 có chủ đích sau khi kiểm cấu trúc V1 và backup; sau đó áp V2 rồi V3. V3 chỉ khởi tạo các cột version mới bằng 0.
4. Chạy gate với artifact dự định deploy. Sắp xếp cửa sổ dừng ghi cho backup/restore/cutover cuối; artifact cũ không tăng version nên không được tiếp tục ghi đồng thời với artifact mới.
5. Chỉ cutover sau khi BE2 xác nhận target, identity, image và khả năng rollback tương thích. Giữ server/backup gốc có thể khôi phục. Khi rollback, chọn artifact đã được kiểm tương thích với schema; không xóa cột version, hạ DDL, repair checksum hoặc clean history để ép quy trình chạy.

Restore dump đã duyệt trên 8.4 và rehearsal chuyển từ server 8.0 sang server 8.4 là hai phép kiểm riêng. Xem báo cáo kiểm chứng để biết những phần đã thực sự chạy.

## Coordinator trong dữ liệu demo hiện có

Seed mới S1/S25/S3 cấp coordinator quyền đúng event vừa sinh. Nếu tài khoản demo đã tồn tại thì DemoSeeder bỏ qua; restart không tự sửa quyền của những tài khoản này.

Dùng luồng quản lý role của System Admin, trước tiên xác minh user ID/email của coordinator và đúng event ID/tên demo. Cấp EVENT_COORDINATOR chỉ cho event đó, sau đó đăng nhập lại và kiểm các API đề thi có bảo vệ của event. Cấp lại không được tạo bản ghi trùng. Grant coordinator null/global vẫn không đủ quyền với đề thi/job có bảo vệ; không tự backfill quyền trên các event thật. Giữ S3 ở trạng thái hoàn tất và đóng chấm điểm. Để smoke test job, dùng coordinator đã được duyệt và có quyền đúng event, submission hợp lệ, cùng judging timer được mở có chủ đích trên event test.

## Quy ước artifact và timeout

`JobLimits` dùng chung giới hạn theo byte UTF-8 sau serialize: rubric/manifest/output 262144 byte, source artifact JSON 8000000 byte, queue message 32768 byte. Text do collector thu thập vẫn có giới hạn riêng 1MB. Rubric/source quá lớn bị chặn trước mọi thao tác put; manifest cuối quá lớn có thể để lại input bất biến đã chuẩn bị, cần đối soát object mồ côi dựa trên tham chiếu DB. Cả hai trường hợp đều không commit job/outbox. Không xóa cả prefix object mồ côi chỉ dựa vào tuổi.

Fetch GitHub chỉ hoàn tất khi nhận xong toàn bộ body. Mỗi request có tối đa 15 giây và không vượt phần còn lại của ngân sách 45 giây toàn snapshot, tính bằng đồng hồ monotonic. Timeout hủy cả request và body subscription, trả 504 REPOSITORY_FETCH_TIMEOUT. Production không cung cấp khả năng đổi endpoint tùy ý.
