Bổ sung backend handoff cho AWS và tích hợp các cập nhật từ `develop`, thống nhất module/package/service về SEAL. Sửa đường dẫn E2E Windows và bổ sung CI riêng cho PR vào `develop` để kiểm tra bản tích hợp trước khi merge.

### Thay đổi

- Bổ sung Flyway V1→V2→V3, migration CLI và deploy gate kiểm schema trước khi thay backend; tách profiles local/test/aws, bootstrap admin có cấu hình và storage local/S3.
- Bổ sung API AI jobs, snapshot, outbox/dispatcher/result consumer và Java MOCK worker; giữ kiểm quyền theo event/assignment, xử lý duplicate/stale results và không ghi điểm chính thức từ AI output.
- Giữ sửa JWT cookie/CSRF và bổ sung kiểm chứng session isolation/logout.
- Resolve xung đột với `develop`, giữ các đường dẫn SEAL và quyền thực thi Maven Wrapper trên Linux. Bảo toàn core Python grader/cleaner/fetcher/schema; đồng bộ mock evidence với source SEAL.
- Sửa 5 BAT launchers về `seal-api`/`seal-web`; thêm checker và regression tests cho cả Windows/POSIX, kể cả trường hợp thư mục cũ còn sót.
- Thêm workflow PR validation vào `develop` với các jobs integration, frontend, backend/MySQL8.4 và Python. Dùng quyền đọc và concurrency riêng, tách khỏi deploy `main`; bước đối chiếu source lịch sử chỉ áp dụng cho nhánh bàn giao BE1.

### Kiểm chứng

- Backend local: 624 test thực thi đạt, 9 MySQL-only skipped; package/JAR kiểm đúng entry points SEAL.
- Frontend local: 162 test đạt, production build thành công.
- Python: 10/10; E2E path regression: 5/5; deploy gate: 8/8; dump parser: 7/7.
- Workflow lint, contract fixtures, Compose parsing và source-preservation checks đều đạt.

Các số backend/frontend là kết quả trên bản tích hợp trước commit CI; source ứng dụng không đổi trong các commit sửa launcher/CI tiếp theo. Các suite có ca trùng, không cộng thành tổng test độc lập.

### Phần còn chờ xác minh

- MySQL8.4 local chưa chạy lại vì Docker Desktop không khởi động được. Cần kết quả job MySQL trên candidate hoặc chạy lại lab riêng khi Docker hoạt động.
- CI GitHub cần chạy trên head SHA sau khi cập nhật PR; kiểm local chưa thay thế kết quả CI remote.
- Chưa nghiệm thu browser E2E đầy đủ, AWS hoặc model thật. Python worker chưa nối trực tiếp với contract/transport S3/SQS của Java backend; Java MOCK vẫn phục vụ smoke hiện tại.
