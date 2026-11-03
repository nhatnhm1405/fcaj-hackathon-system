# BE1 → BE2 — Phản hồi sáu mục trước deploy

Ngày **03/11/2026**, Asia/Saigon. Đối chiếu file `be2-hoi-be1-truoc-deploy-2026-11-01.md` do BE2 gửi với source, evidence local và Git remote hiện tại. File được cung cấp có SHA256 khớp [bản nguồn đã lưu trong repo](../checks/be1-be2-handoff-review-2026-11-03/be2-question-source-2026-11-01.md).

**Kết luận:** phần code/test/runbook BE1 cho luồng **Java worker MOCK** đã hoàn thiện local, gồm finding P2 session/logout vừa đóng. **Đã commit và push candidate lên nhánh riêng `feature/seal-be1-handoff-2026-11-03`. Source SHA để BE2 build: `3eda1fb189a972b9a5b5c85ce482900a1c66ac42`.** Commit tài liệu tiếp theo chỉ cập nhật báo cáo/checkpoint; dùng source SHA này khi chạy gate. Chưa merge `main`, deploy AWS hoặc gửi tin cho team; các xác nhận target/worker/SEAL–FCAJ vẫn còn mở.

## 1. Bản code BE2 cần lấy

Branch bàn giao mới: [feature/seal-be1-handoff-2026-11-03](https://github.com/nhatnhm1405/fcaj-hackathon-system/tree/feature/seal-be1-handoff-2026-11-03). Source commit: [3eda1fb189a972b9a5b5c85ce482900a1c66ac42](https://github.com/nhatnhm1405/fcaj-hackathon-system/commit/3eda1fb189a972b9a5b5c85ce482900a1c66ac42).

| Mốc | Trạng thái đã kiểm |
|---|---|
| Baseline cũ, không dùng build candidate SEAL | `bab44e5fdeb710e6c71982b2e8f5547829fa8e40`; branch cũ `feature/backend1-be2-handoff` giữ nguyên |
| Candidate SEAL đã kiểm và phát hành source | Đã commit/push ở `3eda1fb189a972b9a5b5c85ce482900a1c66ac42` trên branch mới |
| Full source SHA BE2 cần checkout/build | **`3eda1fb189a972b9a5b5c85ce482900a1c66ac42`**; commit03/11 lúc14:52:36 giờ Việt Nam |
| Remote `main` | `a52bf7a1f80df9e3cd162ced8b541f74c543bba4`, đã fetch chỉ để đối chiếu, không merge vào checkout |

BE2 lấy code trong một checkout sạch riêng (không checkout đè máy đang có sửa local):

```bash
git fetch origin feature/seal-be1-handoff-2026-11-03
git checkout --detach 3eda1fb189a972b9a5b5c85ce482900a1c66ac42
test "$(git rev-parse HEAD)" = "3eda1fb189a972b9a5b5c85ce482900a1c66ac42"
```

Branch có thêm commit tài liệu chứa báo cáo cập nhật; **source SHA phía trên là mốc build cố định**. Không dùng baseline cũ hoặc tip main thay thế. Muốn build cả upstream mới phải tích hợp/review/kiểm lại và giao SHA khác. Release verifier nằm ở tip branch tài liệu, đọc Git objects của source SHA nên không phụ thuộc CRLF/LF của checkout.

Candidate có migrations V1/V2/V3, cấu hình AWS/storage/jobs, deploy gate/templates, test runners và worker MOCK. Tên hiện hành phía BE1 theo quyết định03/11:

| Thành phần | Candidate BE1 | Khác file BE2 ngày01/11 |
|---|---|---|
| Backend | `back-end/src/seal-api` | `back-end/src/fcaj-api` |
| Package | `com.seal.hackathon` | `com.fcaj.hackathon` |
| Frontend | `front-end/src/seal-web` | `front-end/src/fcaj-web` |
| Backend service | `seal-backend` | `fcaj-backend` |
| Settings workflow đọc | `/etc/seal/deploy-settings.sh` | `/etc/fcaj/deploy-settings.sh` |

Nhờ BE2 xác nhận mapping này và gửi phần infra/rename đang giữ local để ghép đúng file. Không đổi catalog `fcaj_poc`, tên bucket/queue, logical keys hoặc namespace `fcaj.ai.*` chỉ vì Java/source mang tên SEAL.

**Phát hiện khi kiểm remote:** `main` đã có rename FCAJ và `ai-worker/` sau các PR tích hợp mới. Tại checkpoint trước phát hành, baseline BE1 và `main` có1/13 commit riêng; các kết quả local không chứng minh bản đã merge với `main` mới. Workflow trên `main` vẫn gọi root `docker compose up -d --build --remove-orphans`, chưa có init/verify gate của BE1; root stack có MySQL8.0/FE/proxy. Vì vậy cần review workflow/Compose/names khi tích hợp, không dùng nguyên stack dev đó thay RDS8.4 + FE S3 + private EC2.

**Cách giao đã thực hiện:** source được commit/push lên branch riêng, cả418 file module cũ đều có file SEAL thay thế. Đã kiểm staged payload, không có private/generated paths hoặc credential pattern đã rà. Không cần merge/push `main` để bắt đầu phối hợp; merge `main` là mốc tích hợp riêng. Nếu nhóm muốn bản deploy phải gồm upstream mới, ghép/review và chạy kiểm phù hợp trước khi chốt SHA. Không rebase/reset đè working tree hiện có.

Workflow hiện tại chỉ trigger khi push `main` hoặc PR vào `main`, deploy job chỉ chạy trên push. Push branch bàn giao riêng không khớp trigger deploy đó; mở PR vào `main` sẽ chạy checks theo workflow, không tự chứng minh đã deploy. Kết quả nêu trong báo cáo là kiểm local gắn với source đã phát hành, không gán thành CI pass.

## 2. Kết quả test và hai lỗi dispatch BE2 hỏi

Hai test sau **đã đạt trong full package mới**, kiểm trực tiếp testcase XML đã lưu, không chỉ suy từ tổng suite:

- `AiJobIntegrationTest.productionWorkerRoundTripSuccessFailureAndDuplicate`.
- `AiJobIntegrationTest.roundTripCommitsBeforeAckAndKeepsScoresUntouched`.

| Kiểm tra | Kết quả | Mức chứng minh |
|---|---|---|
| Full backend/package cuối | **633 thống kê:624 thực thi đạt,0 failure/error,9 MySQL-only skipped**; kết thúc14:33:53 ngày03/11, giờ Việt Nam | Java21/H2/mocks; source đã đối chiếu với commit `3eda1fb…`; image target cần BE2 build và ghi identity |
| Component smoke cuối | **36/36**,0 failure/error/skipped |21 job +4 worker +1 API +7 auth +3 helper |
| API/security targeted | **15/15** | Cookie/CSRF/API + SecurityConfig/JwtService |
| MySQL8.4 lab đã lưu | **36/36** trên8.4.13 |21 job +9 fresh deployment +6 file concurrency; checkpoint local trước fix test-only |
| Contract kiểm lại trong lượt soạn phản hồi | **Đạt** | Current synthetic examples khớp identity, HTTP, refs và artifact bytes |
| Hash/checkpoint trước soạn phản hồi | **Đạt**:904 original files preserved,6 existing edits,11 additions | Production Java/resources, migrations/contracts không đổi sau fix test-only |

Các suite có ca trùng, không cộng thành một tổng test mới. MySQL36/36 không phải lượt chạy lại trong buổi soạn phản hồi; fix vừa rồi chỉ đổi test/helper/runner, production vẫn khớp checkpoint đã kiểm MySQL. BE2 báo RDS8.4.11, lab BE1 là8.4.13: cần init/verify trên target, không gọi hai môi trường đã được kiểm giống hệt nhau.

Full suite trong sandbox từng lỗi tạo loopback ở `BoundedHttpTest`; cùng lệnh ngoài sandbox đạt toàn bộ. Đây không phải hai lỗi dispatch BE2 nêu. Chưa có full log/môi trường/lệnh của lượt fail cũ BE2, nên **chưa kết luận root cause của lượt đó** hoặc gán lỗi cho rename/JWT.

Finding P2 test logout/session đã đóng: có đối chứng helper cũ đỏ→helper mới xanh, kiểm thiếu/sai/hết hạn JWT với cùng session, context cũ trong session, logout và CSRF. Production không cần sửa. Xem [báo cáo fix](seal-be1-jwt-session-regression-fix-2026-11-03.md), [evidence mới](../checks/jwt-session-regression-2026-11-03/README.md) và [evidence MySQL](../checks/seal-be1-local-2026-11-03/mysql-tests.json).

**Source/evidence mục2 đã có:** full SHA mới và [release manifest theo Git bytes](../checks/seal-be1-release-2026-11-03/README.md). BE2 còn build/ghi image identity và kiểm trên revision đó. Nếu lỗi dispatch tái hiện, gửi SHA/lệnh/JDK/OS/profile/H2–MySQL/log đã lọc; không chạy hai Maven build/test chung `target`.

## 3. RDS rỗng: migration và dữ liệu ban đầu

**BE1 đã chốt dùng dữ liệu mẫu trước; chưa cần import `seal_hackathon.bak`.** Restore dữ liệu cũ thành đợt riêng với backup/schema review, không gộp vào buổi smoke đầu.

- Chạy Flyway theo chuỗi **V1→V2→V3** từ candidate. V1 tạo31 bảng nghiệp vụ, V2 thêm3 bảng job, V3 bổ sung row versions User/Track. Không chạy riêng V3, không baseline DB rỗng.
- BE2 xác nhận catalog vẫn rỗng, MySQL8.4, `lower_case_table_names=1`, `utf8mb4/utf8mb4_0900_ai_ci`. Provision CA/truststore và hai identities; cả migration JVM và runtime JVM phải kiểm `sslMode=VERIFY_IDENTITY`.
- Migration identity có quyền DDL/metadata cần thiết trong đúng catalog, được BE2 review. Runtime dùng SELECT/INSERT/UPDATE/DELETE và đọc Flyway history, không cần CREATE/ALTER/DROP khi DB đã currentV3. Hibernate dùng `validate`; Flyway startup trên DB current phải no-op.

Script deploy chính: [Test/deploy-verified.sh](../../back-end/src/seal-api/Test/deploy-verified.sh). Sau khi có release SHA, private settings và topology đã chốt, operator dùng checkout đúng SHA:

```bash
export CANDIDATE_REVISION='<FULL_RELEASE_SHA_DA_BAN_GIAO>'
test "$(git rev-parse HEAD)" = "$CANDIDATE_REVISION"
set -a
. /etc/seal/deploy-settings.sh
set +a
# Lần đầu: operator đã chọn DEPLOY_DB_MODE=initialize-empty trong private settings.
bash back-end/src/seal-api/Test/deploy-verified.sh
```

Script **build candidate image → initialize-empty khi được chọn → verify-deploy → start/replace riêng `seal-backend`**. Không phải lệnh chỉ đọc. Gate kiểm trùng runtime/migration URL trước ghi, engine/case/catalog, history/checksum V3, fingerprint, entity mapping và JDBC job columns. Gate lỗi thì không thay backend. Sau init thành công chuyển settings về `verify-only`; script vẫn start/replace backend sau bước gate dù DB mode là verify-only.

Nếu chỉ muốn kiểm DB, CLI đóng trong cùng candidate JAR dùng action `verify-deploy`:

```bash
# Operator cấp BE1_MIGRATION_* và BE1_EXPECTED_RUNTIME_JDBC_URL qua environment private,
# cùng JAVA_TOOL_OPTIONS/truststore phù hợp; không chép secret lên command/chat.
java -Dloader.main=com.seal.hackathon.db.DatabaseMigrationMain \
  -cp target/hackathon-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher verify-deploy fcaj_poc
```

Lệnh CLI trên chạy từ module/JAR đã giao, không tự build hoặc start backend. Operator có thể dùng JAR trong candidate container theo deploy script để bảo đảm cùng artifact. Launcher PowerShell có các action tương ứng trong [Test/run-db-migration.ps1](../../back-end/src/seal-api/Test/run-db-migration.ps1).

DB nonempty/không rõ nguồn/V1/V2 không đi theo auto initialize-empty. DDL failure có thể để lại phần đã chạy; không tự repair/clean/baseline hoặc hứa rollback tự động. Chi tiết ở [current handoff](../plans/backend1-be2-current-handoff-2026-10-28.md) và [operator runbook](../plans/backend1-review-fixes-operator-runbook-2026-10-28.md).

## 4. Cấu hình và quyền AWS

Templates đã có trong [module SEAL](../../back-end/src/seal-api/.env.aws.example) và [deploy/](../../back-end/src/seal-api/deploy/), khớp working tree đã kiểm. Đã có trong source commit `3eda1fb189a972b9a5b5c85ce482900a1c66ac42`.

| Nhóm | Cấu hình BE2 cần cấp |
|---|---|
| DB/runtime | `SPRING_PROFILES_ACTIVE=aws`, `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, URL đúng catalog và VERIFY_IDENTITY; truststore/JAVA_TOOL_OPTIONS private |
| Auth/FE | `JWT_SECRET` Base64≥32 decoded bytes, `JWT_COOKIE_SECURE=true`, HTTPS `APP_FRONTEND_URL`; CORS/CSRF cookie domain theo FE/API domains thật |
| Storage bắt buộc kể cả jobs tắt | `AWS_REGION`, `APP_STORAGE_BUCKET`, `APP_STORAGE_PREFIX`; credential provider/role thực trong container |
| Seed/bootstrap | `SEED_SCENARIO=NONE`; bootstrap mặc định false, chỉ bật có chủ đích với private email/password để tạo admin rồi tắt |
| Job lúc đầu | `APP_AI_JOBS_ENABLED=false`, `APP_AI_JOBS_DELIVERY_ENABLED=false`; bật cả hai theo window khi queue/worker được chốt |
| Job khi thử | Jobs/results queue URLs, cùng region/bucket/prefix backend–worker; `APP_BUILD_REVISION` bằng release SHA thật |
| Operator | Compose/settings/runtime.env/migration.env/CA paths; `DEPLOY_DB_CATALOG`, `DEPLOY_RUNTIME_JDBC_URL`, `DEPLOY_DB_TRUSTSTORE_FILE`, revision và DB mode |

OAuth có thể chưa bật: dùng profile `aws` và không thêm oauth-google/github khi thiếu credentials. SMTP không bắt buộc boot, nhưng các luồng email sẽ chưa được nghiệm thu; **không có mail-disable flag mới**. API model key không cần cho Java MOCK worker. AWS profile không tự load `.env` local.

Hai Compose templates: `compose.aws.example.yml` cho reverse proxy local/loopback và `compose.aws.alb-direct.example.yml` cho đề xuất ALB→private EC2 HTTP8080. Chọn một; direct bắt buộc private `DEPLOY_TARGET_BIND_IP`. BE2 chốt port/protocol/TLS/SG/health; FE S3 HTTPS/domain/CloudFront và browser cookie/CORS còn cần FE phối hợp. Compose2.30+ cần cho `env_file.format: raw`.

**Nhu cầu quyền từ code — chưa phải kết luận role thực đủ/thiếu:**

| Identity | S3 theo bucket/prefix/logical key | SQS |
|---|---|---|
| Backend | Put/Get/Delete cho avatars/problems/inputs/rubrics theo luồng; đọc results; GetObjectVersion/DeleteObjectVersion khi tham chiếu version cần | SendMessage jobs; ReceiveMessage/DeleteMessage results |
| Worker | Get inputs/rubrics; Put/Get results để reuse output bất biến; đọc version khi ref có version | ReceiveMessage/DeleteMessage jobs; SendMessage results |
| Migration CLI | Không cần S3 | Không cần SQS |

BE2 gửi policy/resources đã lọc, provider/role thực, prefix/versioning/KMS và queue metadata để đối chiếu. Hiện chưa có policy live trong bộ đầu vào để chỉ đích danh action nào role đang thiếu. Không bổ sung ListBucket/PurgeQueue chỉ để smoke. Code receive dùng visibility120s, long poll20s, mỗi lượt1 message; BE2 đối chiếu queue defaults, DLQ/backlog/consumers và KMS nếu có. Outbox lease300s là cơ chế riêng.

Private EC2/container còn cần route/DNS tới RDS, S3/SQS và `api.github.com`; build-on-host cần đường Git/Maven/registry. Role host không tự chứng minh container nhận được credentials. Session Manager/Docker/RDS TLS mà BE2 đã báo không cần dựng lại; cần kiểm còn phù hợp tại window và kiểm TLS bằng JVM/container. Xem [runtime/network/IAM runbook](../plans/seal-be1-runtime-config-runbook.md).

## 5. Dữ liệu giả để tạo job

Đã có [runbook API đầy đủ](../plans/seal-be1-smoke-data-runbook.md), [request placeholders](../checks/seal-be1-local-2026-11-03/request-templates.json) và [trace100 request sau fix session](../checks/jwt-session-regression-2026-11-03/api-rehearsal.json). Trace là lab synthetic, không dùng ID hoặc repo giả đó trên RDS.

Thứ tự trên target:

1. V3/reference roles/template → bootstrap admin có chủ đích → tạo accounts test qua API; tắt bootstrap sau provisioning và áp config vào JVM.
2. Admin tạo event và cấp coordinator đúng event; coordinator tạo track/round rồi mở `OPEN`.
3. Chuẩn bị hai đội×ba người, leader tạo đội/mời, thành viên nhận, coordinator approve. Vào `SETUP` không còn PENDING; gán track; vào `IN_PROGRESS` cần mỗi track≥2 đội APPROVED và không còn đội APPROVED unassigned.
4. Tạo rubric thật cho round; gán judge theo round/track. Có role JUDGE đơn lẻ chưa đủ; coordinator grant null cũng không đủ để tạo job.
5. Start CONTEST → leader nộp submission hợp lệ → stop/expire CONTEST → start JUDGING RUNNING còn thời gian. Dùng public GitHub repo nhỏ được phép kiểm, collector từ private container phải truy cập được GitHub.
6. Mỗi người có cookie jar riêng: `GET /api/csrf`204 → login cookie200 → prime CSRF; mutation gửi `X-XSRF-TOKEN` cùng cookie. Login JSON không có bearer token.

Ghi IDs từ target responses: event/track/round/team/user/assignment/submission và hai jobId. Không insert SQL để vượt guards, không dùng `run-demo`/reset/import `.bak` cho buổi này. BE2/người dùng chỉ định operator provisioning/cleanup; credential tài khoản test đi kênh private. Hiện chưa có IDs RDS hoặc owner/window cụ thể.

## 6. Chạy đầu-cuối MOCK

[CURRENT.md](../contracts/ai-job-v1/CURRENT.md) đúng với code MOCK của candidate; contract bytes không đổi sau fix test. FE/model thật chưa được suy thành đã nghiệm thu.

| Bước | API / kết quả |
|---|---|
| Tạo job bằng judge có assignment hoặc coordinator grant đúng event | POST `/api/ai/submissions/{submissionId}/jobs`, không body, cookie+CSRF →202; lấy `data.jobId` |
| Xem trạng thái | GET `/api/ai/jobs/{jobId}` →200; PENDING_DISPATCH/QUEUED/SUCCEEDED/FAILED, mode MOCK |
| Lấy kết quả | GET `/api/ai/jobs/{jobId}/result`; pending409 `RESULT_NOT_READY`; success200 |
| Kiểm failure | Tạo job thứ hai, worker failure →FAILED, result409 `MOCK_PROCESSING_FAILED` |

Operator bật jobs/delivery trong private runtime config rồi recreate/restart có phối hợp, vì các conditional beans được chọn khi JVM khởi động. `DELIVERY_ENABLED=false` cố ý dừng dispatcher và result poller; worker offline giữ pending, chưa phải lỗi model. Không tự retry POST vì chưa có HTTP idempotency key.

**Worker cho buổi này:** Java `com.seal.hackathon.aijob.AwsMockWorkerMain` từ **cùng candidate JAR/image**. BE2 chốt người/nơi chạy/identity/window; chưa tự gán người hoặc thời gian. Worker env mẫu: [worker.env.example](../../back-end/src/seal-api/deploy/worker.env.example); chỉ cần region/bucket/prefix/queue pair và scoped identity, không DB/JWT/model key.

```bash
# Environment/identity được operator cấp qua nguồn private; JRE21.
java -Dloader.main=com.seal.hackathon.aijob.AwsMockWorkerMain \
  -cp target/hackathon-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher --one-success
```

Chạy `--one-failure` trên **job mới**. Worker nhận một message khả dụng, không chọn jobId trực tiếp; cần chốt backlog/consumer và tránh nhiều worker tranh queue. Không purge queue. Script PowerShell tương ứng: [run-aws-mock-worker.ps1](../../back-end/src/seal-api/Test/run-aws-mock-worker.ps1). Lệnh này thao tác queue thật, không chạy chỉ để kiểm syntax.

**Không dùng Python `ai-worker/src/mock_worker.py` trên `main` thay thẳng Java worker:** Python CLI hiện đọc/ghi JSON file bằng `--input/--output`; schema dùng `job_id`, `rubrics`, `status=SUCCESS`, `overall_score`, `criteria_results`. BE1 transport yêu cầu envelope `fcaj.ai.message.v1`, camelCase identity/refs/hash và output `fcaj.ai.output.v1` với provenance/insight. Chưa có bằng chứng adapter hoặc SQS/S3 round-trip nối hai contract. Đây là phần phối hợp BE1–AI trước model integration, không cản việc chọn Java MOCK đã kiểm cho smoke hạ tầng. Không tự ghi Score/RoundResult từ output AI.

Tiêu chí buổi smoke: backend lưu RDS→outbox/SQS→Java MOCK/S3→completion/results consumer→RDS→API đúng terminal/result; success và failure trên hai job riêng; quyền/revoke/closed judging đúng contract; Score/RoundResult giữ nguyên. Ghi release SHA, image ID/digest thực, jobIds/timestamps và log đã lọc. File persistence/restart/browser theo scope được chốt; chưa có kết quả live thì giữ pending.

## Việc hai bên cần chốt để tiếp tục

| Việc | Người phụ trách / đầu vào |
|---|---|
| Commit/push candidate và gửi full SHA40 — **đã xong** | BE1 đã phát hành source `3eda1fb189a972b9a5b5c85ce482900a1c66ac42` trên branch mới; đủ source/tests/templates/contracts/evidence, không kèm target/private env/dump/user files |
| Đồng ý SEAL/FCAJ và ghép infra/upstream | BE1+BE2; xác nhận mapping và cách dùng `main` mới, không tự bỏ deploy gate hoặc overwrite AI module |
| Cách build/giao image | BE2+BE1; ECR pipeline đã hoãn theo quyết định03/11. Gate hiện tự build-on-host; nếu giao prebuilt image cần quy trình/gate được sửa/review/test tương ứng |
| Topology, DB identities/CA, runtime private config, network/provider/policy/prefix/queues | BE2; FE phối hợp HTTPS/domain/CORS/cookies. Session Manager là đường operator đã có; không mặc định SSH từ Actions tới private EC2 |
| Provision dataset, worker operator/window/consumer và cleanup | BE1+BE2/người được chỉ định; dùng Java MOCK cho scope hiện tại |
| Run init/gate/start và hai job live, ghi nhận bàn giao | Operator thực hiện cùng candidate; BE1 hỗ trợ API/guards; nghiệm thu dựa evidence target |

**Có thể làm ngay:** BE2 tiếp tục chuẩn bị private config/network/policy và phản hồi mapping/build/window; BE2 có thể fetch/checkout source SHA đã giao trên branch riêng. **Chưa nên làm:** build từ `bab44e5…` rồi coi là candidate SEAL đã kiểm, merge/push `main` để thử deploy, hoặc dùng Python worker làm drop-in khi contract chưa nối.

Các kiểm tra trong lượt soạn phản hồi: đọc nguồn BE2 gốc, xác nhận hash, kiểm checkpoint, kiểm testcase XML của hai ca BE2 nêu, đọc MySQL evidence/templates/gate/contract, validate current examples, `git ls-remote` và fetch `main` chỉ để review. Sau khi người dùng giao phát hành, đã commit/push source lên branch riêng và cập nhật báo cáo bằng commit tài liệu. Không chạy lại full suite/MySQL/AWS vì source test/runtime không đổi sau các lượt kiểm; các kết quả có timestamp/scope riêng. [Checkpoint phản hồi](../checks/be2-response-review-2026-11-03/response-review.json) ghi nguồn/hash/remote snapshot; đó là checkpoint trước phát hành, được giữ nguyên. Sau commit/tài liệu mới dùng verifier release ở dưới, không sửa hash hoặc HEAD baseline của verifier lịch sử.

## Xác minh source đã phát hành

Từ repository có tip branch tài liệu và source commit trong Git:

```text
node docs/checks/seal-be1-release-2026-11-03/verify-release.mjs
```

Verifier kiểm Git blob bytes theo source SHA, đối chiếu code/runtime/test hiện hành với source commit và kiểm links báo cáo. Các verifier local/review trước đó mô tả working tree trên baseline cũ, có thể báo HEAD/inventory/EOL drift sau commit hoặc trên clone Linux; không sửa manifest lịch sử để né sai khác. Bộ source chứa evidence test đã lọc; raw logs/XML/JAR/image dưới target không được push. BE2 tự build image từ source SHA và ghi image identity trước smoke.
