# SEAL BE1 — Chuẩn bị dữ liệu và kiểm luồng MOCK qua API

Ngày 03/11/2026, Asia/Saigon. “Smoke” là lượt kiểm các chức năng chính nối được với nhau. Bộ này dùng **hai đội × ba thành viên**, một track, một round, một submission và hai job riêng. Không phải script reset/demo hay hướng dẫn tự ghi RDS.

## 1. Phạm vi đã kiểm và cách chạy lại local

Từ `back-end/src/seal-api`, Java 21 và Maven dependency cache:

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21.0.10'
./mvnw.cmd -o -B '-Dspring.profiles.active=test' '-Dtest=ApiSmokeRehearsalTest' test
```

Linux: `sh ./mvnw -B -Dspring.profiles.active=test -Dtest=ApiSmokeRehearsalTest test`.

Rehearsal dùng MockMvc với controllers, security filters, services, repositories thật; H2 riêng, schema core do ORM test tạo và migration V2 cho bảng JDBC jobs. Chỉ GitHub/S3/SQS được mock trong test. Migrations V1→V2→V3 và CLI/runtime no-DDL được kiểm riêng trong MySQL 8.4 lab. Đây không phải browser, HTTP socket, ALB, RDS hay AWS live acceptance. Không có endpoint giả mới trong production.

Test tự tạo lab, lấy cookie từ login thật, không tự tạo JWT hoặc insert entity để chuẩn bị dữ liệu. Bootstrap/reference seed là policy ứng dụng. Evidence chỉ ghi IDs giả, HTTP method/path/status, thời gian và scope tại `target/api-rehearsal/evidence.json`; không ghi password, token, cookie hoặc body tài khoản.

## 2. Trước khi dùng trên target do BE2 quản lý

BE2 xác nhận target/catalog/schema V3, revision/image, identities/TLS, API HTTPS origin, prefix/queue pair và người chạy. Nếu catalog rỗng, operator dùng `initialize-empty` rồi `verify-deploy` từ đúng candidate trước khi backend nhận request. Không chạy `run-demo`, không import `.bak`, không baseline/repair/clean để làm nhanh. Restore dữ liệu cũ là đợt riêng còn cần thống nhất.

Runtime: `SEED_SCENARIO=NONE`. Chủ động bật `BOOTSTRAP_ADMIN_ENABLED=true` với email/password **private**, chạy một lần để provisioning rồi tắt flag. Reference roles và criteria template được seed idempotent. Bootstrap tắt thì không tự sinh admin; bật lại không đổi mật khẩu account admin đã tồn tại, và từ chối nâng account thường thành admin. Test `BootstrapAdminSeederTest` kiểm các điều kiện này; rehearsal còn kiểm hash/count của admin không đổi khi chạy lại bootstrap.

Đặt tên tài nguyên kèm run ID riêng; ghi IDs từ response. Chỉ dùng tài khoản/dữ liệu giả đã được owner chấp thuận trên target. Bộ JSON placeholder ở [request-templates.json](../checks/seal-be1-local-2026-11-03/request-templates.json) phải được điền ở máy operator. Không gửi file chứa secret vào Git/chat/log.

## 3. Cookie và CSRF cho từng người dùng

Mỗi tài khoản dùng cookie jar riêng. Không dùng chung jar admin/coordinator/judge/leader.

1. `GET /api/csrf` → **204**, nhận `XSRF-TOKEN`.
2. `POST /api/auth/login` với email/password private, gửi cookie và header `X-XSRF-TOKEN` bằng giá trị cookie XSRF → **200**. Login đặt cookie `seal_auth_token` HttpOnly/Secure; JSON không trả `token`.
3. Lấy lại `GET /api/csrf` sau login. Với mọi POST/PUT/DELETE, gửi auth cookie + XSRF cookie + header. GET dùng auth cookie.

Ví dụ curl cho operator Bash (chỉ chạy khi target/window đã được cấp):

```bash
# API_ORIGIN là HTTPS origin thật. auth.json là file PRIVATE với email/password.
curl --fail-with-body --silent --show-error -c private.cookies "$API_ORIGIN/api/csrf"
xsrf="$(awk '$6=="XSRF-TOKEN" {print $7}' private.cookies | tail -n 1)"
curl --fail-with-body --silent --show-error -b private.cookies -c private.cookies \
  -H "X-XSRF-TOKEN: $xsrf" -H 'Content-Type: application/json' \
  --data-binary @auth.json "$API_ORIGIN/api/auth/login"
curl --fail-with-body --silent --show-error -b private.cookies -c private.cookies "$API_ORIGIN/api/csrf"
```

Không bật `curl -v` hoặc lưu headers/auth response vào bộ evidence chia sẻ. Không retry POST tự động. Cookie domain/CORS/FE origin cần được FE/BE2 xác nhận riêng cho browser.

## 4. Tạo dữ liệu theo thứ tự

Mỗi response thành công có wrapper `{success,message,data}`. Lấy ID theo tên field trong bảng; không dùng ID từ lab hoặc môi trường khác.

| Bước / tài khoản | API và body | HTTP / dữ liệu cần ghi |
|---|---|---|
| Admin tạo event | `POST /api/events`, mẫu `createEvent` | 200; `data.eventId` |
| Admin tạo accounts | `POST /api/admin/users`, mẫu `createUser`, lần lượt coordinator, judge, tài khoản âm, sáu sinh viên | 201; `data.userId`; các account được admin tạo đã approved/active |
| Admin cấp coordinator | `POST /api/admin/roles/grant`, `{userId,roleName:"EVENT_COORDINATOR",eventId}` | 201; grant phải đúng event |
| Coordinator tạo track | `POST /api/events/{eventId}/tracks`, `{name}` | 200; `data.trackId` |
| Coordinator tạo round | `POST /api/events/{eventId}/rounds`, mẫu `createRound` | 200; `data.roundId`; non-final để kiểm assignment theo track |
| Coordinator mở đăng ký | `PUT /api/events/{eventId}`, `{status:"OPEN"}` | 200 |
| Mỗi leader tạo đội | `POST /api/teams`, `{eventId,name}` | 200; `data.teamId`; trạng thái PENDING |
| Leader mời hai người | `POST /api/invites/teams/{teamId}`, `{invitedUserId}` | 200; `data.inviteId` |
| Người được mời nhận | `PUT /api/invites/{inviteId}/accept`, không body | 200; roster của từng đội đủ ba người |
| Coordinator duyệt cả hai | `PUT /api/teams/{teamId}/approve`, không body | 200; APPROVED |
| Coordinator đóng đăng ký | `PUT /api/events/{eventId}`, `{status:"SETUP"}` | 200; không được còn PENDING |
| Mỗi leader chọn cùng track | `PUT /api/teams/{teamId}/track`, `{trackId}` | 200; event dùng SELF_SELECT |
| Coordinator bắt đầu event | `PUT /api/events/{eventId}`, `{status:"IN_PROGRESS"}` | 200; mỗi track ≥2 đội APPROVED, không đội APPROVED bị unassigned |
| Coordinator tạo rubric của round | `POST /api/events/{eventId}/rounds/{roundId}/criteria`, mẫu `createCriterion` | 200; template có sẵn chưa đủ, round phải có criteria |
| Coordinator gán judge | `POST /api/coordinator/assignments/judges`, `{judgeUserId,roundId,trackId}` | 201; `GET` cùng path với `?eventId=...` để lấy assignment ID; assignment tự bảo đảm role JUDGE đúng event |
| Coordinator mở CONTEST | `POST /api/events/{eventId}/rounds/{roundId}/timer/CONTEST/start`, `{durationSeconds:600}` | 200; round ACTIVE, deadline được timer đồng bộ |
| Leader nộp bài | `POST /api/submissions`, `{roundId,repoUrl,description}` | 200; `data.submissionId`; một đội nộp đủ để kiểm luồng này |
| Coordinator dừng CONTEST | `POST .../timer/CONTEST/stop`, không body | 200 |
| Coordinator mở JUDGING | `POST .../timer/JUDGING/start`, `{durationSeconds:600}` | 200; round ACTIVE, rubric và submission được judge assignment bao phủ |

Ngày phải phù hợp **ngày chạy**, cùng season/year, đúng thứ tự `registrationStart ≤ registrationEnd ≤ startDate ≤ endDate`; không copy ngày demo cũ. `SPRING` tháng 1–4, `SUMMER` 5–8, `FALL` 9–12. Source hiện gate đăng ký bằng status OPEN; cửa sổ ngày mang thông tin lịch. Rehearsal chọn các mốc trong cùng ngày hợp lệ với validation và dùng timer thật cho thời gian contest/judging, không sửa clock production.

Hai đội/track là guard thật trước IN_PROGRESS. Approve hiện không bắt tối thiểu ba thành viên; ba người ở đây là dataset kiểm roster, không phải luật mới. Không dùng SQL để vượt guard.

Repo live cần là **public GitHub**, nhỏ, có source extension collector hỗ trợ và được phép dùng. `https://github.com/example/seal-rehearsal` trong test là synthetic vì collector được mock; không dùng URL đó làm repo live. Backend private EC2 phải truy cập public GitHub được.

## 5. Hai lượt job riêng, success và failure

1. Đúng judge/assignment hoặc coordinator grant event: `POST /api/ai/submissions/{submissionId}/jobs`, **không body** → 202. Lưu `data.jobId`. Ban đầu PENDING_DISPATCH, dispatcher đưa QUEUED; poll có thể bỏ qua trạng thái giữa.
2. `GET /api/ai/jobs/{jobId}` → 200. Chưa có result: `GET .../result` → 409 `RESULT_NOT_READY`.
3. Local test gọi dispatcher → worker production → consumer bằng S3/SQS mock. Trên AWS, operator bật delivery trong window và chạy **cùng candidate JAR/image** với `com.seal.hackathon.aijob.AwsMockWorkerMain --one-success`. Xem launcher đầy đủ trong [current handoff](backend1-be2-current-handoff-2026-10-28.md).
4. Success: SUCCEEDED và result 200, `mode=MOCK`, DTO đã lọc; không thay Score/RoundResult.
5. Tạo **job mới** bằng POST, chạy worker `--one-failure`: FAILED; result 409 `MOCK_PROCESSING_FAILED`.

Worker không chọn trực tiếp jobId mà nhận một message khả dụng. BE2 chốt queue/backlog/consumer/operator/window; không Purge queue để tiện thử. `APP_AI_JOBS_DELIVERY_ENABLED=false` cố ý giữ pending, chưa phải lỗi model. Worker offline không tự biến job thành timeout. Không gửi fixture `current-examples` vào queue runtime.

## 6. Các ca chặn cần giữ

- Admin đơn thuần, coordinator chỉ có grant null, judge có role nhưng thiếu assignment: tạo job bị 403.
- Cookie POST thiếu XSRF header: 403; không bỏ CSRF để test.
- OPEN→SETUP khi còn PENDING: 400; SETUP→IN_PROGRESS khi thiếu track assignment/đội: 400.
- Mở JUDGING khi CONTEST còn RUNNING: 400.
- Judge khác requester không đọc lịch sử của job: 404 dù có role JUDGE.
- Đóng JUDGING: tạo job trả 409 `JUDGING_CLOSED`; requester vẫn đọc lịch sử nếu còn quyền.
- Revoke quyền/assignment: lần đọc tiếp theo bị chặn. Nếu mất toàn bộ authority JUDGE/EVENT_COORDINATOR, security có thể trả 403 trước controller; nếu còn authority nhưng sai scope, job access 404 theo contract.
- Worker hoàn thành lặp cùng message: giữ terminal state, không nhân đôi điểm. Transport/DB/S3 failure không được ghi thành model failure.

## 7. Chạy lại, dừng và evidence

Local test mỗi process dùng H2 in-memory riêng, không reset DB dùng chung. Khi operator làm trên target: ghi run ID, event/track/round/team/entry/user/assignment/submission/job IDs từ response hoặc API đọc; lưu revision/image và thời gian, trạng thái, checksum/log đã lọc. Không tự tạo lại tài nguyên vì request timeout; kiểm API list/status trước. Không ghi password/token/raw source vào bundle.

Nếu hết timer, dùng API start/stop hợp lệ theo trạng thái và ghi job mới; không sửa row DB. Khi dừng window, operator phối hợp tắt delivery/worker theo scope; không dừng backend hoặc xóa dữ liệu người khác. Dọn tài nguyên test theo danh sách và thỏa thuận riêng của owner, không có lệnh xóa/reset tự động trong runbook này.

Restart backend rồi đọc lại job/result/avatar/problem, ALB header/cookie/redirect, RDS TLS/IAM và live S3/SQS vẫn là bước nghiệm thu target riêng. Không suy chúng từ MockMvc pass.
