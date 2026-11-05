# Bằng chứng local sau tích hợp và rename

Checkpoint rename trước bổ sung RDS rỗng. Xem [fresh-db-verification.md](fresh-db-verification.md) cho code/test/deploy templates hiện hành;giữ nguyên các số612/27 bên dưới để truy lịch sử. Manifest source-files.sha256/SHA256SUMS đã được refresh theo bộ bàn giao hiện hành,không chỉ checkpoint cũ.

Khi chuẩn bị commit,checksum được chuẩn hóa theo bytes Git thay vì bytes working copy Windows. Chạy verify-bundle.mjs để kiểm HEAD (hoặc --staged trước commit);không dùng hash CRLF của working copy để so trực tiếp với manifest Git.

Ngày28/10/2026, Asia/Saigon. Working tree chưa commit trên codex/backend1-foundation, HEAD nền f42a168d9cb0613a43801edca632a2de64837099. Source fcaj-api/com.fcaj; SHA nền không đại diện artifact.

| Kiểm tra | Kết quả |
|---|---|
| Java21.0.10 Maven package, profile test |612 tests,0 failure/error/skipped;50.971s;kết thúc11:53:59+07 |
| MySQL8.4.13 lab mới,các migration V1/V2/V3 |27 tests,0 failure/error/skipped;kết thúc11:54:28+07;container fcaj-ai-lab-c507a3d76a92 đã dừng và giữ |
| Worker production components synthetic round trip |success b792d77f-ea54-41e8-af42-778a18c4934f;failure591b87d0-00e6-4014-b76f-c41a16303935;duplicate accepted;S3/SQS/GitHub mocked |
| JAR |Start-Class com.fcaj.hackathon.HackathonApplication;508 entries com/fcaj,0 com/seal |
| JAR SHA-256 |3ea7af471241f72c76e4a8b10d05f145c9c81fd4f200d0cf951a9e20e0ec91c1 |
| PropertiesLauncher migration main |com.fcaj.hackathon.db.DatabaseMigrationMain nạp được từ fat JAR;không args trả lỗi kiểm input trước kết nối DB |
| Node deploy gate tests |2 đạt sau khi cho phép Git Bash chạy trên Windows;lệnh docker/git giả lập |
| Parser tests / fixture cũ / sample mới |7 parser tests;7 JSON cũ;17 JSON hiện hành khớp HTTP/identity/keys/hash/size |
| PowerShell/Bash parser / diff |đạt;Linux CI remote chưa chạy |

Log gốc local nằm dưới back-end/src/fcaj-api/target: integration-rename-verification.log, fcaj-ai-lab-c507a3d76a92-test.log và Surefire XML. Không đóng gói toàn log vì có thể có thêm output ngoài phạm vi. Đây là bản tóm tắt đã lọc từ lệnh thực sự chạy, không phải chứng nhận AWS.

mysql84-v3-lab-evidence.md trong cùng thư mục là bản sao nguyên evidence rehearsal cũ ngày28, không phải restore mới sau rename.627 dòng cũ giữ fingerprint cột gốc và version=0 là kết quả đó;27 test mới dùng DB synthetic riêng. Không cộng27 test MySQL vào612.

SHA256SUMS dùng đường dẫn tương đối từ repository root cho bộ tài liệu bổ sung. source-files.sha256 ghi bytes source/config/scripts hiện tại để đối chiếu working tree trước revision; không chứa target/.env/dữ liệu người dùng. SHA-256 không thay thế commit SHA hoặc image digest. Khi source thay đổi cần tạo lại manifest trước bàn giao.
