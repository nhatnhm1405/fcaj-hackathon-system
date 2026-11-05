# BE1 — Sửa coverage JWT/session/logout

Ngày **03/11/2026**, Asia/Saigon. Theo Q&A, người dùng yêu cầu chỉnh plan rồi thực hiện code; chưa có đầu vào BE2 hoặc phạm vi bổ sung. Đã thực hiện S00→S05 trong [plan](../plans/seal-be1-jwt-session-regression-fix-plan-2026-11-03.md). **Đóng finding P2 về coverage bằng thay đổi test/helper; không sửa production.**

## Vấn đề và thay đổi

Test client cũ chỉ giữ cookie, không truyền `MockHttpSession`. Vì MockMvc không khôi phục session từ JSESSIONID, GET401 sau logout có thể đạt dù request đã bỏ mất session cần kiểm. Điều này làm thiếu bằng chứng, chưa chứng minh ứng dụng có session-auth leak.

- Thêm [CookieSessionClient](../../back-end/src/seal-api/src/test/java/com/seal/hackathon/testsupport/CookieSessionClient.java): mỗi client giữ cookie/session riêng; truyền session hợp lệ; lấy session từ request đã thực thi bằng `getSession(false)`; bỏ session invalidated và cookie Max-Age0. Helper không tự thêm CSRF/Bearer.
- [CookieSessionClientTest](../../back-end/src/seal-api/src/test/java/com/seal/hackathon/testsupport/CookieSessionClientTest.java) kiểm session server tạo không có Set-Cookie JSESSIONID, isolation giữa hai client, invalidate/recreate và cookie deletion/attributes.
- [JwtCookieSessionIsolationTest](../../back-end/src/seal-api/src/test/java/com/seal/hackathon/security/JwtCookieSessionIsolationTest.java) dùng Spring context/security/controllers thật, login cookie và CSRF thật; bootstrap tài khoản synthetic trên H2 riêng. Bảy test phủ sáu nhóm AUTH, tách token hỏng và hết hạn.
- [ApiSmokeRehearsalTest](../../back-end/src/seal-api/src/test/java/com/seal/hackathon/aijob/ApiSmokeRehearsalTest.java) dùng chung helper, mỗi tài khoản có session marker riêng, kiểm không có `SPRING_SECURITY_CONTEXT` và session continuity qua request/logout; bỏ oracle chỉ dựa vào JSESSIONID. Assertion không in toàn bộ response login/cookie/token.
- [Smoke runner](../../back-end/src/seal-api/Test/run-integration-smoke.ps1) chạy thêm hai test class mới. Luồng100 request/hai đội×ba người/hai job success–failure và guards giữ nguyên.

## Đối chứng đỏ → xanh

1. Chạy `CookieSessionClientTest.retainsServerCreatedSessionAcrossRequests` với helper cookie-only, tương ứng hành vi cũ: session có ở request đầu nhưng request kế tiếp trả401 thay vì200; **1 failure,0 error**. Source cookie-only, log và XML được giữ riêng dưới ignored target trước khi sửa.
2. Sửa helper để giữ/truyền session; đối chứng cũ xanh, cả ba test helper đạt. Kiểm request sau mang chính session của request trước, không chỉ status200.
3. Bảy test auth qua production hiện tại đều đạt, nên không có căn cứ sửa JWT filter/SecurityConfig/AuthController trong finding này.

## Kết quả AUTH

| Nhóm | Đã kiểm và kết quả |
|---|---|
| AUTH-01 | Login và nhiều GET bằng JWT cookie đạt200; cùng session/marker tồn tại, không persist authentication vào HttpSession; CSRF ổn định |
| AUTH-02 | Bỏ riêng auth cookie, giữ session/XSRF, không Authorization header → `/me`401; xác nhận session hiện diện tại request đó |
| AUTH-03 | Token malformed và token ký đúng test key nhưng hết hạn đều401 với cùng session. Kiểm độc lập chữ ký/expiration qua `ExpiredJwtException` trước khi gọi filter |
| AUTH-04 | Logout200, auth/XSRF cookies được yêu cầu xóa; `/me`401 với session cũ còn hợp lệ. Helper cho phép server invalidate, không ép xóa hoặc giữ session sai lifecycle |
| AUTH-05 | SecurityContext authenticated đặt sẵn trong session không thay được JWT: `/me`401; request vẫn mang session và harness không xóa context để làm test đạt |
| AUTH-06 | PUT profile thiếu XSRF header403; đúng cookie/header200; logout/prime CSRF/login lại và GET/mutation tiếp theo đạt với session marker |

## Kiểm chứng trên source cuối

| Lượt | Kết quả | Phạm vi |
|---|---|---|
| Helper-red |1 test,1 failure có chủ đích,0 error | Xác nhận thiếu continuity của helper cookie-only |
| Helper-green |3/3 đạt | Đối chứng và lifecycle/isolation/cookies |
| Auth + helper targeted |10/10 đạt |7 auth +3 helper |
| API + SecurityConfig + JwtService targeted |15/15 đạt |1 API rehearsal +4 config +10 JWT |
| Component smoke qua runner |36/36 đạt |21 job +4 worker +1 API +7 auth +3 helper; H2/mocks |
| Full package trong sandbox |633 thống kê;0 failure,1 error,9 skipped | `BoundedHttpTest` không tạo được loopback: `SocketException: Permission denied: connect` |
| **Full package ngoài sandbox** |**633 thống kê,624 thực thi đạt,0 failure/error,9 MySQL-only skipped** | Cùng Maven offline/profile test; loopback test đạt, JAR được đóng gói |
| Runner syntax / diff / checkpoint | Đạt | PowerShell parser, whitespace và verifier/hash/link sau cập nhật tài liệu |

Không cộng các suite có test trùng. Full package được chạy tuần tự, không `clean` để giữ output/probe trước sửa. Java21.0.10, Maven Wrapper3.9.16, Node24.15.0. Commands, per-suite counts, thời gian và hash logs/XML nằm trong [evidence mới](../checks/jwt-session-regression-2026-11-03/README.md).

## Source và giới hạn

Working tree trên `feature/backend1-be2-handoff`, baseline `bab44e5fdeb710e6c71982b2e8f5547829fa8e40`; chưa stage/commit/push/deploy/AWS/gửi team hoặc có release SHA mới. Giữ nguyên các thay đổi SEAL có trước.

Checkpoint mới so với hash910 file hiện hữu trước fix: chỉ cho phép sửa API test, smoke runner, plan, review note, docs index và current handoff; thêm ba test/helper files cùng báo cáo/evidence mới. Toàn bộ production Java/resources, POM/Dockerfile, migrations V1/V2/V3, contracts và các evidence/manifest lịch sử giữ bytes ban đầu. Verifier review trước sửa đã đạt485 file/9 review hashes/65 links; các verifier cũ sẽ báo drift có chủ đích khi kiểm test/runner/docs mới. Không sửa manifest cũ.

Vì chỉ sửa test/helper, không chạy lại MySQL lab hoặc build Docker image. Kết quả MySQL36/36 và image trước đó giữ đúng phạm vi lịch sử, không gọi là lượt kiểm mới. Output JAR/log/XML sinh lại dưới target là artifact local, không phải release/image BE2 đã nhận.

Các test chứng minh session isolation trong **MockMvc/H2**, chưa nghiệm thu browser/OAuth Google–GitHub, ALB headers/cookies, RDS TLS/IAM/S3/SQS live hoặc restart toàn process. Logout hiện xóa cookie, task này không thêm cơ chế revoke mọi bản sao JWT. Release và các đầu vào BE2 tiếp tục theo [plan bàn giao tổng hợp](../plans/seal-be1-be2-consolidated-handoff-plan-2026-11-03.md).
