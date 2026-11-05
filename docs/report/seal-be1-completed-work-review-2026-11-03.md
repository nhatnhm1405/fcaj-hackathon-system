# Review phần BE1 đã hoàn thành — 03/11/2026

**Cập nhật sau xử lý finding:** P2 thiếu coverage session/logout đã được đóng bằng sửa test/helper, không đổi production. Xem [báo cáo fix](seal-be1-jwt-session-regression-fix-2026-11-03.md) và [checkpoint mới](../checks/jwt-session-regression-2026-11-03/README.md). Nội dung review/probe và số614/36/26 bên dưới giữ bối cảnh trước fix; full package mới là624 thực thi đạt/9 MySQL-only skipped, component36/36. Không cộng các suite có ca trùng.

Phạm vi: đổi module/package sang SEAL, sửa JWT/CSRF, POM/runners/deploy gate/templates, evidence và plan bàn giao BE2. Lượt này không sửa application code/config/test hoặc chạy AWS; chỉ review và tạo probe riêng dưới ignored target.

## Finding cần bổ sung

### [P2] Test logout chưa giữ session giữa các request

Plan xử lý riêng: [JWT/session regression fix plan](../plans/seal-be1-jwt-session-regression-fix-plan-2026-11-03.md). Tại checkpoint review ban đầu, plan mới được lập, chưa sửa test hoặc production code; kết quả xử lý sau đó nằm trong cập nhật phía trên.

Vị trí: [ApiSmokeRehearsalTest.java — client helper](../../back-end/src/seal-api/src/test/java/com/seal/hackathon/aijob/ApiSmokeRehearsalTest.java), dòng247–267; assertion liên quan ở dòng78 và192–193.

`Client` chỉ giữ cookie. `call()` không lấy session từ request đã thực thi và không truyền `.session(...)` vào request kế tiếp. Với MockMvc, JSESSIONID cookie không tự ánh xạ sang `MockHttpSession`; response cũng không tự có Set-Cookie JSESSIONID chỉ vì controller/filter đã tạo session.

Vì vậy assertion “cookie jar không có JSESSIONID” và GET `/me`401 sau logout chưa chứng minh authentication không được lưu trong session. Nếu một regression tạo authenticated HttpSession, helper hiện tại vẫn bỏ session đó ở request kế tiếp và ca401 có thể tiếp tục đạt.

**Probe đã chạy:** một controller synthetic cố ý giữ trạng thái xác thực trong session. Response tạo session không có JSESSIONID Set-Cookie; request chỉ mang JSESSIONID trả401, nhưng request mang đúng `.session(mockSession)` trả200. Source/log tại `back-end/src/seal-api/target/review-session-probe/`. Đây là bằng chứng thiếu coverage của test harness, **không phải bằng chứng ứng dụng hiện có session-auth leak**.

**Đề nghị:** giữ `MockHttpSession` riêng theo từng client hoặc dùng HTTP test có cookie/session thực. Kiểm session không chứa `SPRING_SECURITY_CONTEXT`, rồi bỏ JWT cookie và gọi `/me` với cùng session để kỳ vọng401; kiểm logout theo cùng cách. Một session có sẵn như session OAuth cũng phải không biến thành kênh đăng nhập JWT độc lập. Chạy lại test liên quan sau khi bổ sung; không sửa assertions để bỏ qua session.

## Phần đã đối chiếu và chưa thấy lỗi chặn local

- Đủ418 file gốc sau đổi tên;366 Java gốc được đối chiếu. JWT filter là thay đổi logic production duy nhất ngoài rename; dùng request-only repository tương ứng SecurityConfig, không thêm HttpSession persistence.
- 30 migration/contract files giữ nguyên bytes. V1/V2/V3, namespace `fcaj.ai.*`, object keys và quyền nghiệp vụ không bị đổi theo tên package.
- POM/group/main/launcher, root Compose, CI, BAT E2E và deploy gate gọi path/classes SEAL. Gate vẫn verify trước replace và chỉ thay `seal-backend`, không tự thay dependencies.
- Hai template phân biệt loopback/proxy với ALB direct private interface; direct vẫn ghi rõ giả định cần BE2 xác nhận.
- Verifier review đạt:485 file gốc ngoài bốn tài liệu cập nhật giữ hash;9 review hashes và65 local links hợp lệ. Manifest local trước đó giữ nguyên.

Kết quả full package614 thực thi đạt/9 MySQL-only skipped, MySQL36/36 và component/API26/26 là **bằng chứng đã lưu từ lượt thực hiện**, không phải full suite chạy lại trong lượt review này. Source/config tương ứng chưa đổi. Không cộng các suite có ca trùng thành tổng mới. Finding P2 làm hẹp mức chứng minh của phần session/logout, không phủ nhận các ca cookie/CSRF và API/job còn lại.

## Các mục còn mở đã được ghi đúng trong plan

Các mục sau là công việc bàn giao/target chưa hoàn tất, không phải lỗi mới phát hiện trong code:

- Chưa có release SHA mới; working tree chưa stage/commit/push. Khi phát hành cần đưa cả phần xóa path cũ và thêm module mới, không chỉ một phía.
- Mapping SEAL so với bảng FCAJ cũ và cách giao/build image khi ECR hoãn cần BE2 xác nhận.
- ALB/port/protocol/TLS/domain, private network, policy/prefix/provider thực và JVM TLS/RDS chưa được nghiệm thu.
- Runbook/rehearsal local đã có, nhưng RDS IDs, worker owner/window và hai job MOCK AWS success/failure chưa thực hiện.
- Dữ liệu mẫu trước, restore `.bak` riêng đã được người dùng chốt phía BE1; còn operator/provisioning/cleanup target.

Xem [plan bàn giao tổng hợp](../plans/seal-be1-be2-consolidated-handoff-plan-2026-11-03.md) để theo dõi các mốc. Chưa thấy căn cứ đánh dấu sẵn sàng triển khai hoặc nghiệm thu AWS từ kết quả local.
