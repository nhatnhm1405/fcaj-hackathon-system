# BE1 — Plan sửa thiếu coverage session/logout trong kiểm thử JWT

Ngày **03/11/2026**, Asia/Saigon. Trạng thái: **đã chỉnh plan và thực hiện S00→S05 ở local; finding P2 về coverage đã đóng**. Chỉ sửa test/helper/runner và tài liệu; production không đổi. Xem [báo cáo fix](../report/seal-be1-jwt-session-regression-fix-2026-11-03.md) và [checkpoint mới](../checks/jwt-session-regression-2026-11-03/README.md). Nguồn ban đầu: [finding P2 trong review](../report/seal-be1-completed-work-review-2026-11-03.md).

### Q&A và phạm vi đã chốt trong lượt thực hiện

- Người dùng yêu cầu **chỉnh plan rồi thực hiện code**, không chỉ review tài liệu.
- Chưa có phản hồi BE2, kết quả test mới hoặc yêu cầu mở rộng finding. Thực hiện độc lập ở local trên checkout hiện có.
- Giữ policy JWT và session tạm hiện hành. Logout xóa cookie trên client; task này không thêm thu hồi mọi bản sao JWT hoặc cấm session OAuth.
- Ưu tiên test-only. Chỉ sửa production nếu các ca qua security thật tái hiện lỗi; báo ngay phát hiện đó cùng bằng chứng và tiếp tục sửa trong phạm vi finding.
- Báo cáo cuối phải nêu rõ red/green của helper, kết quả AUTH-01…06, production có đổi hay không và checkpoint mới. Các mốc release/AWS giữ riêng.

## 1. Vấn đề và mục tiêu

`ApiSmokeRehearsalTest.Client` hiện chỉ giữ cookie. Helper không chuyển `MockHttpSession` giữa các request. MockMvc không tự khôi phục session từ cookie JSESSIONID, nên test có thể nhận401 vì session đã bị bỏ mất, dù một regression trong ứng dụng có thể vẫn lưu authentication ở session đó.

Probe riêng dưới `back-end/src/seal-api/target/review-session-probe/` đã xác nhận: controller synthetic tạo session mà response không có JSESSIONID Set-Cookie; request chỉ mang cookie trả401, request mang đúng MockHttpSession trả200. Đây là lỗi coverage đã được xác nhận; **chưa xác nhận ứng dụng hiện tại bị session-auth leak**.

Mục tiêu sau sửa:

- Test client giữ đúng session/cookie theo từng người dùng và có đối chứng chứng minh session được truyền thật.
- Các API cần đăng nhập chỉ chấp nhận JWT hợp lệ qua cookie hoặc Bearer theo policy hiện hành. Những ca âm trong plan phải không mang Authorization header để tránh che mất hành vi cần kiểm.
- Khi bỏ JWT hoặc logout, cùng session cũ không được trở thành kênh đăng nhập độc lập.
- Session tạm có sẵn như session phục vụ OAuth vẫn được phép tồn tại; test kiểm quyền đăng nhập được lưu ở đâu, không cấm tất cả session.
- Giữ hành vi cookie/CSRF và luồng API/job đã kiểm. Nếu chỉ thiếu test thì chỉ sửa test; chỉ sửa production khi có ca thất bại tái hiện được.

## 2. Phạm vi và file dự kiến

| File / thành phần | Thay đổi dự kiến |
|---|---|
| `back-end/src/seal-api/src/test/java/com/seal/hackathon/aijob/ApiSmokeRehearsalTest.java` | Dùng client có session continuity; thay assertion chỉ dựa vào JSESSIONID cookie; giữ luồng chuẩn bị dữ liệu/API và trace đã lọc |
| Test-only helper, dự kiến `src/test/java/com/seal/hackathon/testsupport/CookieSessionClient.java` | Logic cookie/session dùng chung giữa API rehearsal và các test auth mới; không đưa vào main/runtime |
| Test auth mới, dự kiến `src/test/java/com/seal/hackathon/security/JwtCookieSessionIsolationTest.java` | Kiểm riêng JWT/session/logout bằng Spring context và security filters/controllers thật, H2 riêng, không AWS/OAuth provider thật |
| Test đối chứng helper trong test source | Controller/harness synthetic cố ý giữ một marker trong session; chứng minh request sau nhận đúng marker qua cùng session |
| `Test/run-integration-smoke.ps1` | Bổ sung test auth mới vào nhóm kiểm phù hợp, nếu tách thành class riêng; CI full suite tự nhận class có hậu tố Test |
| `JwtAuthenticationFilter`, `SecurityConfig`, `AuthController` | **Chỉ sửa khi test production tái hiện lỗi**. Không mặc định sửa cả ba hoặc invalidate mọi session |
| Báo cáo/evidence mới | Ghi kết quả và mức chứng minh mới; cập nhật trạng thái finding/bàn giao theo đúng checkpoint sau sửa |

Không đổi migrations V1/V2/V3, schema, contract AI job, quyền nghiệp vụ, module/package, storage hay hạ tầng. Không thêm blacklist JWT/global token revocation hoặc thay flow OAuth trong task này. Không commit/push/deploy/gọi AWS/gửi team khi thực thi scope local của plan.

Chi tiết triển khai đã làm rõ:

- Helper dùng chung `CookieSessionClient`; đối chứng `CookieSessionClientTest` kiểm session server tạo, tách client, invalidate và cookie deletion. Helper không tự thêm CSRF header/Bearer để các ca thiếu token có ý nghĩa.
- Không giữ kiểu lỗi assertion in toàn bộ login response/cookie/token. Chỉ ghi method/path/status và tên cookie hoặc mã ca đã lọc.
- AUTH-03 kiểm token hết hạn được ký bằng đúng **test key** ứng dụng đang dùng; kiểm chữ ký/expiration riêng để tránh ca đạt do sai key thay vì hết hạn.
- AUTH-06 dùng mutation profile thật, không gửi email hoặc gọi AWS. Giữ marker/session qua login lại và kiểm session ở chính request 401/403.
- Output làm việc riêng: `back-end/src/seal-api/target/jwt-session-fix-20261103/`. Checkpoint chia sẻ mới: `docs/checks/jwt-session-regression-2026-11-03/`; giữ nguyên các manifest lịch sử.

## 3. Trình tự thực hiện

### S00 — Kiểm checkpoint và giữ bằng chứng trước sửa

1. Đọc hướng dẫn repository áp dụng, Git status/branch/HEAD. Giữ các thay đổi có trước, không reset/stash/checkout đè.
2. Baseline hiện tại là `bab44e5fdeb710e6c71982b2e8f5547829fa8e40` trên `feature/backend1-be2-handoff`; kiểm lại khi bắt đầu. Đây không phải release SHA của candidate SEAL chưa commit.
3. Chạy verifier review hiện hành, lưu hash các file sắp sửa và kết quả probe coverage. Nếu source đã drift, xác định thay đổi trước khi dùng evidence cũ.
4. Lưu log/summary mới dưới output riêng của lượt fix; không để Maven clean hoặc suite mới làm mất bằng chứng trước sửa. Không chạy hai Maven build/test cùng ghi target.

**Đạt khi:** xác định được source trước sửa và các thay đổi có trước; probe/chứng cứ coverage được giữ lại.

### S01 — Tạo đối chứng khiến helper cũ thất bại

1. Nếu tách helper để dùng chung, ban đầu giữ nguyên hành vi cookie-only của helper cũ; không thay production để tạo lỗi giả.
2. Trong harness test-only, tạo session chứa marker synthetic. Request thứ hai phải nhìn thấy marker của cùng session qua client đang kiểm.
3. Xác nhận ca này thất bại với client cookie-only và nguyên nhân là session không được truyền. Không coi việc absence của Set-Cookie JSESSIONID là bằng chứng không có session.
4. Harness đối chứng không thay controller/security của ứng dụng và không được dùng làm bằng chứng ứng dụng đã có leak.

**Đạt khi:** có ca đỏ có ý nghĩa cho thiếu sót helper, không chỉ assert cấu trúc code hoặc tên field.

### S02 — Sửa client giữ đúng session/cookie

1. Mỗi client giữ cookie jar và MockHttpSession riêng; không dùng chung session cho admin/coordinator/judge/participant.
2. Gắn session hợp lệ vào request trước khi gửi. Sau khi thực thi, lấy session từ **request đã chạy**, không tạo request mới hoặc gọi getSession(true) chỉ để thu thập state.
3. Xử lý cookie Max-Age=0 bằng cách xóa khỏi jar; giữ Secure/HttpOnly và hành vi XSRF hiện có.
4. Nếu server invalidate session thì client bỏ reference không còn hợp lệ. Nếu session vẫn còn, tiếp tục gửi chính session đó. Không ép giữ một session đã invalidated.
5. Cung cấp thao tác bỏ riêng auth cookie cho ca âm; không vô tình xóa session/XSRF cùng lúc, không tự thêm Bearer token.
6. Đối chứng S01 phải xanh. Thay oracle `jar không có JSESSIONID` bằng quan sát session/authentication trực tiếp.

**Đạt khi:** marker sống qua request đúng client, các client độc lập và session không bị vô tình bỏ ở bước kiểm 401.

### S03 — Bổ sung các ca hồi quy qua security thật

Dùng tài khoản synthetic tạo theo bootstrap policy/test setup, real login cookie, real CSRF và real security filters/controllers. Không dùng `with(user())` hoặc CSRF postprocessor để thay cho cơ chế đang kiểm. Dữ liệu lưu H2 test riêng, không MySQL/RDS đang làm việc.

| Ca | Chuẩn bị và thao tác | Kết quả bắt buộc |
|---|---|---|
| AUTH-01 — JWT hợp lệ và session có sẵn | Tạo MockHttpSession chứa marker vô hại mô phỏng state tạm; login thật rồi gọi `/api/auth/me` nhiều lần bằng cùng client |200 khi JWT hợp lệ; marker/session được giữ; không lưu authenticated `SPRING_SECURITY_CONTEXT` trong HttpSession |
| AUTH-02 — Bỏ riêng JWT | Sau AUTH-01, bỏ `seal_auth_token`, giữ session còn hợp lệ, không Authorization header | `/api/auth/me`401. Ghi nhận session vẫn được mang vào request, không chỉ kiểm status |
| AUTH-03 — JWT sai/hết hạn | Gửi cùng session với token hỏng và token test ký hợp lệ nhưng exp đã qua, không Bearer fallback |401 ở cả hai. Token hết hạn dùng fixture với key chỉ dành cho test; không sleep hoặc sửa clock production |
| AUTH-04 — Logout | Login thật, logout với cookie/header CSRF đúng; kiểm response yêu cầu xóa auth/XSRF cookie; tiếp tục gọi `/me` với session cũ nếu còn hợp lệ | Logout200; auth cookie bị xóa; `/me`401. Cho phép server giữ session vô hại hoặc invalidate; không yêu cầu invalidate toàn bộ session mới coi là pass |
| AUTH-05 — Session có authentication cũ | Trong test-only setup, đặt SecurityContext synthetic đã authenticated vào MockHttpSession; gọi `/me` bằng session đó nhưng không JWT/Bearer |401 qua security thật; chứng minh session context không tự thay JWT. Không set authentication trực tiếp vào SecurityContextHolder để bypass filter |
| AUTH-06 — CSRF và đăng nhập lại | Với JWT/session hợp lệ, PUT `/api/auth/me` thiếu XSRF header; sau đó dùng đúng cookie/header; logout rồi prime CSRF và login lại | Thiếu CSRF403; mutation hợp lệ đạt; login lại200 và các GET/mutation kế tiếp không bị mất CSRF ngoài ý muốn |

Session có sẵn chỉ mô phỏng state tạm; các ca này không được ghi thành đã nghiệm thu OAuth Google/GitHub hoặc browser qua ALB. Kết quả S03 có thể đã đạt trên production code hiện tại; điều đó phù hợp với finding là coverage gap.

**Đạt khi:** các ca âm vẫn mang đúng session tại điểm kiểm; quyền truy cập phụ thuộc JWT hợp lệ và policy hiện hành, không phụ thuộc session auth cũ.

### S04 — Nhánh quyết định sửa production

- **Nếu S03 đạt:** giữ nguyên production code; đóng phần sửa ở test helper/oracle và bổ sung coverage. Không sửa auth chỉ để tạo thêm diff.
- **Nếu S03 thất bại:** lưu request/session/cookie state đã lọc, status và vị trí security context được đọc/ghi. Tái hiện bằng test tập trung trước khi sửa.
- Chỉ sửa nơi đã xác định nguyên nhân trong JWT filter, SecurityContext repository hoặc logout handling. Giữ request-scoped authentication cho API JWT, CSRF, Secure/HttpOnly cookies, checks approval/grants/assignment và nhu cầu session tạm của OAuth.
- Chạy lại ca lỗi rồi nhóm hồi quy phù hợp. Không bỏ assertion, dùng Bearer để né cookie/CSRF hoặc xóa session ngay trước request 401 để làm test đạt.

**Đạt khi:** mỗi thay đổi production đều gắn với failure tái hiện; hoặc có kết luận rõ production không cần sửa trong finding này.

### S05 — Kiểm chứng và cập nhật evidence/bàn giao

Chạy tuần tự, không cộng ca trùng giữa các suite:

| Lượt kiểm | Phạm vi / thời điểm |
|---|---|
| Helper đối chứng + auth/session mới | Red trước sửa helper, green sau sửa; chạy trước khi mở rộng suite |
| API rehearsal + SecurityConfig/JwtService tests | Kiểm CSRF, cookie, session isolation, logout và luồng API đang có |
| Component smoke runner | Chạy nhóm worker/job/API/auth đã chọn; ghi counts thực tế sau bổ sung test, không tiếp tục gán cứng số26 cũ |
| Full backend profile test/package | Chạy một lượt trên source cuối sau sửa; ghi executed/skipped/failure/error và checkpoint rõ ràng |
| MySQL integration/Docker package | Nếu production auth/security wiring thay đổi, đánh giá và chạy lại kiểm integration/package bị ảnh hưởng. Nếu chỉ test/helper đổi, giữ kết quả MySQL/image cũ ở đúng scope runtime không đổi; không gọi chúng là lượt kiểm mới |
| PS syntax / docs links / diff whitespace | Kiểm khi runner/tài liệu tương ứng đổi. Không chạy frontend/Playwright/AWS chỉ vì thêm test backend |

Các lệnh khởi đầu dự kiến, chạy tại `back-end/src/seal-api` sau khi test class/helper đã được tạo:

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21.0.10'
./mvnw.cmd -o -B '-Dspring.profiles.active=test' '-Dtest=JwtCookieSessionIsolationTest' test
./Test/run-integration-smoke.ps1
./mvnw.cmd -o -B '-Dspring.profiles.active=test' package
```

Thêm class đối chứng helper vào lệnh targeted theo tên thực tế. Maven offline chỉ dùng khi dependencies đã có cache. Nếu sandbox chặn loopback/Mockito/Git Bash, phân loại lỗi môi trường, giữ log và dùng cơ chế approval của công cụ khi cần; không sửa test để né lỗi môi trường.

Output dự kiến:

- Báo cáo fix ghi vấn đề → thay đổi → red/green proof → kết quả → giới hạn; nêu rõ có sửa production hay không.
- Evidence mới chứa source hashes, commands/tool versions, per-suite counts và log đã lọc. Không ghi JWT/password/cookie values, private env hoặc session authentication đầy đủ vào artifact chia sẻ.
- Cập nhật finding P2 và ghi chú bàn giao BE2 theo kết quả thực. Nếu helper vẫn chỉ mô phỏng một host, tiếp tục ghi phạm vi MockMvc thay vì browser acceptance.
- Giữ nguyên manifest/evidence local và review trước sửa. Tạo checkpoint/verifier mới đọc source/test hiện hành; không sửa checksum lịch sử để giả rằng test mới đã ở lượt cũ. Test-only helper thay đổi cũng làm source snapshot thay đổi và phải được ghi nhận.

**Đạt khi:** người review nhìn được test cũ thiếu gì, đối chứng đỏ/xanh, các ca auth với session thật trong harness, source/checkpoint sau sửa và giới hạn còn lại.

## 4. Tiêu chí đóng finding P2

- [x] Đối chứng chứng minh client cookie-only cũ bỏ session và client mới giữ đúng session.
- [x] Client có state riêng, xử lý session bị invalidate và cookie bị xóa đúng.
- [x] Valid JWT truy cập được; thiếu/sai/hết hạn JWT với cùng session bị401.
- [x] Authentication đặt sẵn trong session không cấp quyền API khi thiếu JWT.
- [x] Logout và login lại được kiểm bằng cùng session khi hợp lệ, không chỉ cookie jar.
- [x] CSRF vẫn chặn request thiếu token; Secure/HttpOnly và API/job guards giữ nguyên.
- [x] Các kiểm tra phù hợp source cuối đạt, counts/logs/checkpoint mới rõ; không mượn kết quả cũ để ghi test mới đã pass.
- [x] Báo cáo kết luận chính xác: test-only fix hoặc production bug đã tái hiện/sửa, không suy diễn session leak khi chưa có bằng chứng.

Kết quả lượt thực hiện: helper-red 1 failure đúng đối chứng → helper-green3/3; auth/helper10/10; API/security15/15; component36/36; full package633 thống kê,624 thực thi đạt,9 MySQL-only skipped. Full package sandbox trước đó lỗi loopback `Permission denied`, cùng lệnh ngoài sandbox đạt. Các suite có ca trùng, không cộng tổng. Chi tiết từng bước, log/hash và giới hạn nằm trong báo cáo/checkpoint mới.

Không phụ thuộc đầu vào BE2 để sửa/test local phần này. Release SHA, deploy AWS và nghiệm thu browser/ALB vẫn là các mốc riêng trong [plan bàn giao tổng hợp](seal-be1-be2-consolidated-handoff-plan-2026-11-03.md).
