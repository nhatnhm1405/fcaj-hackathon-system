# BE1 → BE2 — Checklist đối chiếu và plan bàn giao tổng hợp

Ngày **03/11/2026**, Asia/Saigon. Đối chiếu trực tiếp [sáu mục BE2 hỏi ngày01/11](../checks/be1-be2-handoff-review-2026-11-03/be2-question-source-2026-11-01.md) với source và [evidence local](../checks/seal-be1-local-2026-11-03/README.md). Đây là plan để BE1/người dùng review và thảo luận với BE2; chưa gửi team, commit/push hoặc triển khai AWS.

**Kết luận:** phần BE1 local đã chuẩn bị và kiểm được hầu hết nội dung kỹ thuật. Chưa hoàn tất đầu ra “bản tích hợp có full commit SHA + image được BE2 nhận”, chưa đối chiếu policy thực và chưa nghiệm thu trên AWS. Vì vậy không đánh dấu cả sáu mục đã đóng chỉ từ kết quả local.

## 1. Thông tin đầu vào và quyết định hiện hành

| Nội dung | Đã biết / quyết định | Giới hạn cần giữ |
|---|---|---|
| EC2/operator | BE2 báo EC2 chạy, Session Manager truy cập được; Docker/Compose đã cài và chạy thử container | Là thông tin BE2 cung cấp ngày01/11, chưa phải review live ngày03/11; không yêu cầu BE2 dựng lại các phần đã có |
| RDS | BE2 báo MySQL8.4.11, case1, catalog `fcaj_poc` rỗng; EC2 kết nối RDS bằng TLS | Trước init phải xác nhận catalog vẫn rỗng; TLS từ EC2 chưa thay bằng chứng JVM/container với CA/truststore thật |
| Dữ liệu ban đầu | **Người dùng đã chốt phía BE1: dùng dữ liệu mẫu trước, restore `.bak` thành đợt riêng.** Hướng này khớp đề xuất BE2 trong file gốc | Đã đóng lựa chọn phía BE1; còn chốt operator, dataset/window và cleanup trên RDS, không hỏi lại BE2 có muốn đề xuất dữ liệu mẫu hay không |
| Tên source/service | Candidate hiện giữ SEAL: `seal-api`, `com.seal.hackathon`, `seal-web`, `seal-backend`, `/etc/seal/deploy-settings.sh` | Khác bảng FCAJ trong file BE2 ngày01/11; cần BE2 xác nhận và ghép local diff của họ, không tự đổi hạ tầng hoặc revert toàn bộ BE1 |
| Topology | Hướng trong plan03/11: FE trên S3, API qua ALB tới EC2 private | ALB direct/proxy, target port/protocol/TLS, CloudFront/HTTPS/domain chưa được BE2 chốt; direct HTTP:8080 mới là template đề xuất |
| Build/delivery | Pipeline GitHub Actions→ECR đã hoãn theo mốc03/11 | Khác phân công ECR trong file gốc; phải thống nhất cách giao/build candidate, không coi ECR pipeline là dependency đã triển khai |
| Source checkpoint | Branch `feature/backend1-be2-handoff`, HEAD `bab44e5fdeb710e6c71982b2e8f5547829fa8e40` | HEAD chỉ là baseline trước sửa; candidate SEAL đang ở working tree chưa commit/push, **release SHA mới chưa có** |

Nội dung file BE2 được dùng làm yêu cầu/nguồn để đối chiếu, không phải lệnh chạy AWS hoặc áp lại bảng rename FCAJ cũ. Bản gốc được giữ nguyên theo [metadata nguồn](../checks/be1-be2-handoff-review-2026-11-03/review-inputs.json).

## 2. Checklist sáu đầu ra BE2 yêu cầu

Ký hiệu: **[x]** đã chuẩn bị/kiểm đúng phạm vi ghi kèm; **[ ]** còn thực hiện hoặc xác nhận. “Đạt local” không phải “đã chạy trên target”.

| Mục gốc BE2 | Phần đã hoàn thành | Phần còn thiếu trước khi đóng mục |
|---|---|---|
| **1 — Bản code chính xác** | [x] Module/package SEAL, migrations/config/runners/worker có trong source; mapping và JAR entrypoints đã kiểm | [ ] BE2 đồng ý mapping tên; [ ] nhận/ghép infra diff; [ ] chốt commit SHA40 mới và branch/PR chứa candidate; [ ] BE2 xác nhận nhận đúng revision/image |
| **2 — Kết quả test gắn bản giao** | [x] Full package623 thống kê:614 thực thi đạt,9 MySQL-only skipped; [x] MySQL36/36; [x] component/API26/26; [x] hai ca dispatch gốc đạt local; [x] gate8/8 | [ ] Gắn evidence với release SHA mới sau khi source được phát hành; [ ] BE2 chạy kiểm tương ứng trên revision đó; [ ] nhận log cũ nếu cần điều tra root cause khác môi trường; chưa kết luận lỗi BE2 đã được sửa |
| **3 — RDS rỗng/migration/dữ liệu** | [x] V1→V2→V3, initialize-empty/verify-deploy, guard target URL, runtime DML-only và CLI trong JAR đã kiểm MySQL lab; [x] phía BE1 chốt dữ liệu mẫu, restore riêng | [ ] BE2 xác nhận trạng thái RDS hiện tại, CA/two identities; [ ] operator init/gate bằng candidate trên target; [ ] JVM/container TLS và runtime startup current V3 |
| **4 — Cấu hình/quyền AWS** | [x] Env/settings/migration/worker templates; [x] bắt buộc/tùy chọn; [x] hai topology Compose; [x] network/IAM action matrix; [x] parse và synthetic env probe | [ ] Điền config private thật; [ ] chốt ALB/FE/API/TLS/network/build access; [ ] đối chiếu policy/prefix/provider thực. Hiện **chưa đủ dữ liệu để chỉ đích danh action nào role đang thiếu** |
| **5 — Dữ liệu tạo job** | [x] Runbook và request placeholders; [x] hai đội×ba người qua API; [x] bootstrap/login/CSRF/grants/assignment/rubric/timer/submission; [x]100 request rehearsal | [ ] Operator provisioning qua API trên RDS trong scope đã chốt; [ ] ghi IDs/tài khoản test thật qua kênh private; [ ] repo public được phép dùng và collector từ container target |
| **6 — Job đầu-cuối MOCK** | [x] Ba API/worker CLI/expected outcomes; [x] success và failure trên hai job riêng, duplicate/ack ordering/quyền đã kiểm local | [ ] Chốt worker owner/nơi chạy/identity/window và backlog/consumer; [ ] live RDS→SQS→worker/S3→results→RDS→API; [ ] evidence target, restart/file persistence theo scope |

Các kết quả test là các suite có ca trùng, không cộng thành tổng mới. Lượt review này kiểm lại checksum/source và đọc summary đã lưu; **không chạy lại full suite** vì không sửa application code/config. Chi tiết lỗi JWT/CSRF đã tái hiện và sửa nằm trong [báo cáo local](../report/seal-be1-local-completion-2026-11-03.md); lỗi đó không được suy thành nguyên nhân hai lỗi dispatch BE2 nêu.

## 3. Nội dung cần thảo luận lại, theo thứ tự ưu tiên

| ID | Câu hỏi/đề xuất cụ thể cần trả lời | Người chốt | Bằng chứng/đầu ra cần nhận | Tác động |
|---|---|---|---|---|
| D01 | BE2 đồng ý giữ SEAL theo candidate hiện tại chưa? Gửi branch/full SHA hoặc patch của local rename/infra để ghép đúng file | BE1+BE2 | Mapping thống nhất và diff infra; không đổi catalog/bucket/queues/namespace `fcaj.ai.*` theo tên Java | Đóng bản tích hợp và tránh hai bộ tên |
| D02 | ECR hoãn: có chọn build từ source release ngay trên EC2 qua Session Manager không? Nếu giao image sẵn thì quy trình/gate nào sẽ dùng? | BE2 vận hành, BE1 gate | Cách build/giao image, operator, lệnh/đường lấy log; Git/Python3/Docker/Compose và đường tải dependencies/base images nếu build trên EC2 | Script gate hiện **tự build** từ source; chưa có mode nhận prebuilt image. Chọn phương án khác cần sửa/review/test gate trước |
| D03 | ALB gọi trực tiếp backend hay proxy; private target interface/port/protocol, TLS/health? FE S3 HTTPS/domain/API origin và CloudFront thế nào? | BE2+FE | Tham số topology/routing và config browser; chọn một Compose template | Chặn apply container/network và browser acceptance; không chặn source local đã hoàn thiện |
| D04 | RDS còn rỗng? Hai identity, CA/truststore và runtime URL đã sẵn sàng? Private container có route/credentials tới RDS, GitHub, S3/SQS? | BE2 | Metadata target đã lọc, người init/gate, probes từ JVM/container; không yêu cầu gửi password | Chặn init và kiểm DB/collector/storage thật |
| D05 | Cung cấp policy đã lọc, bucket/prefix/versioning/KMS nếu có, queue pair và provider/role thực. Visibility120s đã áp dụng trên AWS và khớp override code chưa? | BE2; BE1 đối chiếu | Ma trận action/resource thực so với [nhu cầu từ code](seal-be1-runtime-config-runbook.md); consumer/backlog/DLQ metadata | Kết luận đủ/thiếu quyền và live delivery. Giá trị120s đã được chọn; cần kiểm cấu hình, không chọn lại |
| D06 | Với dữ liệu mẫu đã được BE1 chấp thuận, ai provisioning/cleanup RDS; ai chạy worker, ở đâu và khi nào? Các bước restart/browser/file có thuộc buổi nghiệm thu này không? | BE2+BE1+worker operator; FE nếu browser | Owner cụ thể, window, dataset/repo, consumer control và acceptance scope | Chạy đúng target, tránh worker tranh queue và có kết quả kiểm được |
| D07 | BE2 còn cần điều tra hai lỗi ngày28/10 không? Gửi full log/SHA/lệnh/JDK/OS/profile/H2 hay MySQL, và có chạy Maven đồng thời không | BE2 gửi; BE1 phân tích | Log đã lọc hoặc kết quả kiểm trên release mới | Không mặc định do rename. Nếu ca tương ứng fail lại ở revision mới, dừng mốc bị ảnh hưởng để điều tra |

Không hỏi lại BE2 đã cài Docker hoặc có Session Manager chưa; file gốc đã trả lời. Chỉ kiểm chúng còn phù hợp tại window và phiên bản Compose hỗ trợ `env_file.format: raw` (2.30+). TLS native EC2/RDS và tài nguyên đã tạo không tự chứng minh ứng dụng/SDK đã chạy.

## 4. Bộ hồ sơ BE1 sẽ bàn giao

| Gói | File/nguồn cụ thể | Trạng thái |
|---|---|---|
| Điểm vào và phản hồi sáu mục | Plan này; [current handoff](backend1-be2-current-handoff-2026-10-28.md); [phản hồi BE2](seal-be1-tra-loi-be2-truoc-deploy-2026-11-03.md) | Đã có; bản tổng hợp này là điểm review mới nhất |
| Release card | Branch/PR URL, **release SHA40**, image ID/digest theo cách giao, người build/nhận và timestamp | Chưa có release SHA mới; không điền baseline hoặc hash JAR thay cho commit |
| Source/backend | `back-end/src/seal-api`, POM/Dockerfile, `com.seal.hackathon.*`, worker MOCK | Đạt local, chưa phát hành source mới |
| DB/migration | `src/main/resources/db/migration/V1__create_core_schema.sql`, V2, V3; `Test/deploy-verified.sh`, `Test/run-db-migration.ps1`; [operator runbook](backend1-review-fixes-operator-runbook-2026-10-28.md) | Đạt MySQL lab; giữ migration bytes, chưa apply RDS |
| Runtime | [runtime/ALB runbook](seal-be1-runtime-config-runbook.md), `.env.aws.example`, `deploy/compose.aws*.example.yml`, `deploy-settings.sh.example`, `migration.env.example`, `worker.env.example` | Templates/nhu cầu đã kiểm; actual values/topology/policy pending |
| Dữ liệu/API | [data runbook](seal-be1-smoke-data-runbook.md), [request templates](../checks/seal-be1-local-2026-11-03/request-templates.json), [trace100 request](../checks/seal-be1-local-2026-11-03/api-rehearsal.json) | Đạt rehearsal H2/MockMvc với GitHub/S3/SQS giả; IDs trong trace không dùng cho RDS |
| Contract/test evidence | [CURRENT.md](../contracts/ai-job-v1/CURRENT.md), [báo cáo](../report/seal-be1-local-completion-2026-11-03.md), [evidence](../checks/seal-be1-local-2026-11-03/README.md), [review checkpoint](../checks/be1-be2-handoff-review-2026-11-03/README.md) | Contract/mock semantics giữ nguyên; checkpoint mới chỉ đối chiếu và bổ sung quyết định tài liệu |

Bộ chia sẻ chỉ chứa source, templates trống, fixture/trace synthetic và log/count đã lọc. Credentials/filled env/CA password/user artifacts/dump/raw target output không đưa vào Git hoặc bản gửi BE2. Worker chỉ cần region/bucket/prefix/jobs/results queues và identity phù hợp, không DB/JWT.

## 5. Plan thực hiện từ review đến nghiệm thu

| Mốc | Người phụ trách | Việc cần làm | Điều kiện hoàn tất | Trạng thái hiện tại |
|---|---|---|---|---|
| **M0 — Chốt khác biệt và cách giao** | BE1+BE2, FE cho domain | Trả lời D01–D06 theo phần liên quan; ghi owner và file chung. Ghi nhận data mẫu trước/restore riêng đã chốt phía BE1 | Có mapping tên, cách giao/build, target/topology/identity và window cho bước sắp chạy | Chưa chốt liên nhóm; checklist/source/evidence đã đối chiếu |
| **M1 — Chốt release source** | BE1, người dùng review; BE2 ghép infra | Review diff SEAL và phần tích hợp. Khi người dùng giao commit/push riêng, phát hành branch/PR và SHA40. Chỉ chạy lại checks bị thay đổi bởi integration; gắn evidence với đúng checkpoint | BE2 checkout được revision có source/config/docs nhất quán; source/image mapping rõ. Không push main để thử | Chưa có release SHA mới; không commit/push trong lượt lập plan |
| **M2 — Backend lên target** | BE2 operator; BE1 hỗ trợ | Private config/CA/identities; build/giao candidate theo D02; init/gate từ cùng candidate; backend runtime ít quyền; kiểm health/version/ALB và JVM TLS | V3 đúng, no-DDL runtime, target healthy và version đúng; chưa gọi đây là AI end-to-end | Chưa chạy AWS |
| **M3 — Provision dữ liệu mẫu** | Operator do BE2/người dùng chỉ định; BE1 runbook | Bootstrap có chủ đích rồi tắt; tạo accounts/grants/teams/round/rubric/timers/submission qua API; dùng dates/IDs thật, repo public được phép | Có judge/coordinator đúng quyền và submission hợp lệ; roster/track/CONTEST/JUDGING đúng guards | Runbook và rehearsal local đã đạt; RDS IDs chưa có |
| **M4 — Kiểm file/job MOCK thật** | BE2 runtime + worker operator; BE1 API | Kiểm S3 file theo quyền, success/failure trên hai job riêng, poll/result, persistence sau restart trong scope; log đã lọc | Đường RDS/S3/SQS/worker/API hoạt động, terminal/result đúng, quyền đúng, không thay điểm chính thức | Chưa có window/operator và live evidence |
| **M5 — Chốt biên bản bàn giao** | BE1+BE2; FE/AI theo scope | Điền release/image/job/time/outcomes, phần chưa chạy, owner và bước tiếp theo; người nhận xác nhận | Phân biệt local/AWS/browser/model thật; không đánh dấu model thật/FE job UI pass từ MOCK | Mẫu biên bản ở mục8; chưa có nghiệm thu live |

BE1 có thể chuẩn bị release review, bảng trả lời và bằng chứng trong khi BE2 làm rõ infra/config. Không bắt BE2 làm lại local tests thay BE1; không chờ đủ mọi câu trả lời để xử lý việc độc lập. Không cam kết ngày hoặc tên người khi owner chưa xác nhận.

## 6. Quy trình DB và boot để đưa vào buổi bàn giao

Các lệnh dưới đây là **hướng dẫn cho operator khi đã có release/target/window được chốt**, không được thực thi trong lượt review này. Chọn đúng topology template; không dùng root Compose dev MySQL8.0 thay RDS.

1. BE2 kiểm RDS vẫn là catalog rỗng MySQL8.4/case1, `utf8mb4`/`utf8mb4_0900_ai_ci`. Lab BE1 dùng8.4.13, BE2 báo8.4.11: cần gate trên target, không suy minor version đã được chạy local giống nhau.
2. Tách migration identity có DDL trong catalog với runtime SELECT/INSERT/UPDATE/DELETE và đọc Flyway history; runtime current V3 không cần DDL. Cấp CA/truststore cho **cả migration JVM và runtime JVM**, URL `sslMode=VERIFY_IDENTITY`.
3. Điền private env/settings theo templates. AWS profile không tự load `.env`; Compose/runner inject đúng file. Boot cần DB, JWT secret Base64≥32 decoded bytes, HTTPS frontend origin, Secure cookies, region/bucket/prefix. S3 vẫn cần khi jobs tắt. OAuth có thể chưa bật; SMTP không bắt buộc boot nhưng email flow chưa hoạt động; không có mail-disable flag mới.
4. Ban đầu `APP_AI_JOBS_ENABLED=false`, `APP_AI_JOBS_DELIVERY_ENABLED=false`, `SEED_SCENARIO=NONE`; bootstrap chỉ bật có chủ đích cho provisioning. Jobs/delivery bật riêng lúc kiểm sau khi queue/worker được chốt.
5. Nếu D02 chọn đúng quy trình build-on-host hiện có, operator ở repository checkout release dùng:

```bash
# FULL_RELEASE_SHA là commit thực được BE1 bàn giao, không dùng SHA baseline trong plan.
export CANDIDATE_REVISION='<FULL_RELEASE_SHA>'
test "$(git rev-parse HEAD)" = "$CANDIDATE_REVISION"
set -a
. /etc/seal/deploy-settings.sh
set +a
# Lần đầu vào catalog rỗng: operator đã chọn DEPLOY_DB_MODE=initialize-empty trong settings.
bash back-end/src/seal-api/Test/deploy-verified.sh
```

Script này **build image, init khi được chọn, verify rồi start/replace backend**; không phải lệnh verify chỉ đọc. Mode mặc định `verify-only` không ghi DB ở bước gate, nhưng script vẫn thay backend sau khi gate đạt. CLI `DatabaseMigrationMain verify-deploy` riêng mới là thao tác gate DB chỉ đọc; xem operator runbook để chọn đúng lệnh/phạm vi.

6. Khởi tạo đúng V1→V2→V3, không chạy riêng V3 và không baseline DB rỗng. DB nonempty/unknown/V1/V2 không đi vào đường tự init. DDL có thể để lại phần đã chạy nếu lỗi; không hứa rollback tự động hoặc dùng repair/clean để vượt gate. Sau lần đầu chuyển settings về `verify-only`.
7. Gate thành công mới `up -d --no-deps --no-build seal-backend`; dependencies do operator chuẩn bị. Kiểm `/health/live`, `/health/ready` (DB), `/health/version` (revision), ALB và scheme/cookie/redirect theo scope. Ready không chứng minh S3/SQS/GitHub hoạt động.

Nếu D02 chọn giao image sẵn, **không dùng nguyên lệnh này với kỳ vọng nó bỏ qua build**. BE1/BE2 phải thống nhất và kiểm quy trình tương ứng trước. Session Manager đã có là đường operator đề xuất dùng tiếp; workflow SSH từ GitHub-hosted runner chưa được xác nhận có route tới EC2 private.

## 7. Chuẩn bị dữ liệu và chạy hai job trong window

Thực hiện theo [runbook đầy đủ](seal-be1-smoke-data-runbook.md); tóm tắt để hai bên thống nhất:

1. V3/reference roles/template → bootstrap admin có chủ đích → tắt bootstrap. Mỗi tài khoản có cookie jar riêng; `GET /api/csrf`, login lấy HttpOnly auth cookie, gửi XSRF cookie/header cho mutations. Login JSON không có bearer token.
2. Admin tạo event/accounts; cấp coordinator đúng event. Coordinator tạo một track/round, mở event; hai đội×ba người được tạo/mời/nhận và approve. SETUP không còn PENDING; mỗi track≥2 đội APPROVED và không APPROVED nào unassigned trước IN_PROGRESS.
3. Rubric đúng round, judge assignment round/track. CONTEST chạy để leader nộp bài; stop/expire CONTEST rồi mở JUDGING RUNNING còn thời gian. Một submission hợp lệ đủ cho hai lượt job riêng; IDs lấy từ target responses, không copy ID lab.
4. Bật jobs/delivery trong private runtime config và recreate/restart backend có phối hợp trong window; các conditional beans được chọn khi JVM khởi động, không tự đổi vì vừa sửa env file. Tắt bootstrap sau provisioning cũng cần áp cấu hình vào process theo cách này. `POST /api/ai/submissions/{submissionId}/jobs`, không body →202/jobId. `GET /api/ai/jobs/{jobId}` lấy status; `GET .../result` chỉ200 khi SUCCEEDED, còn pending409.
5. Worker operator dùng **cùng candidate JAR/image** và private worker env/identity. Ví dụ JAR trên máy có JRE21:

```bash
java -Dloader.main=com.seal.hackathon.aijob.AwsMockWorkerMain \
  -cp target/hackathon-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher --one-success
```

Container trong candidate dùng JAR `/app/app.jar` và private `worker.env` do operator cung cấp; credentials host không tự chứng minh có trong container. Không chạy CLI chỉ để kiểm syntax: `--one-success`/`--one-failure` nhận/xóa message queue thật.

6. Job success: SUCCEEDED/result200, mode MOCK, DTO lọc thông tin nội bộ. Tạo **job mới**, chạy `--one-failure`: FAILED/result409 `MOCK_PROCESSING_FAILED`. Score/RoundResult giữ nguyên. Worker nhận một message khả dụng chứ không chọn jobId; kiểm consumer/backlog, không Purge queue và không tự retry POST tạo job.
7. Kiểm sai role/event/requester, revoke và judging đóng theo contract; sau đóng judging vẫn đọc được lịch sử nếu còn quyền. Kiểm file/avatar/problem và dữ liệu sau restart có phối hợp. Fault injection/DLQ replay chỉ trong scope riêng, không ngầm đưa vào buổi đầu.

Mục tiêu M4 là **kiểm luồng chính đầu-cuối (“smoke”) trên AWS bằng MOCK**. Model thật, FE job UI và OAuth/SMTP/browser đầy đủ không nằm trong kết quả MOCK trừ khi nhóm giao thêm scope cụ thể.

## 8. Phiếu giao nhận và nghiệm thu

| Trường cần ghi | Giá trị hiện tại / cách điền |
|---|---|
| Release branch/PR + SHA40 | `feature/backend1-be2-handoff` là branch tham chiếu; **release mới chưa có** |
| Source/image mapping | Điền build operator, timestamp, image ID/digest thực theo D02. Local image BE1 là evidence riêng, chưa phải image BE2 đã nhận |
| Target | RDS/EC2/region/catalog metadata do BE2 xác nhận tại window; không ghi password/secret |
| Topology và config version | D01–D05, template được chọn, private file paths, CA/roles/prefix/queue metadata |
| Operator/window | Người cụ thể phụ trách migration/runtime/provision/worker/log và giờ Asia/Saigon; chưa chốt |
| Dữ liệu | Run ID, event/track/round/team/user/assignment/submission IDs thật; internal entry IDs nếu operator cần đối soát đọc |
| Job success/failure | Hai jobId riêng + timestamps/status/result HTTP/mode; log/S3 refs/checksums đã lọc theo quyền |
| Quyền và persistence | Kết quả ca chặn, file read sau restart, job/result đọc lại; ca không chạy ghi pending và owner |
| Hạn chế còn lại | Root cause lỗi BE2 cũ nếu chưa rõ; browser/model thật/FE UI và phần chưa nằm scope |
| Xác nhận nhận source và nghiệm thu | BE1/BE2 ghi tên, ngày, link evidence sau khi thực sự hoàn tất; không tự ký thay |

## 9. Việc tiếp theo và tiêu chí kết thúc bàn giao

- [x] Đọc nguồn BE2 gốc và đối chiếu đủ sáu đầu ra với source/evidence hiện tại.
- [x] Kiểm lại489 checksum của checkpoint local trước khi cập nhật tài liệu;418 file gốc/366 Java/30 frozen files vẫn phù hợp.
- [x] Chốt phía BE1: dữ liệu mẫu trước, restore riêng; đồng bộ câu hỏi còn lại theo quyết định này.
- [x] Tạo plan tổng hợp, mapping tài liệu và review checkpoint; giữ evidence test/source cũ nguyên trạng.
- [ ] Thảo luận D01–D06 và nhận inputs/owner tương ứng; D07 điều tra log khi có dữ liệu.
- [ ] Người dùng review và giao phát hành source riêng → có full SHA/image mapping cho BE2.
- [ ] Operator thực hiện M2–M4 và ghi biên bản M5.

**Chỉ đóng bàn giao triển khai** khi BE2 nhận đúng revision/artifact, cấu hình/target/quyền được đối chiếu, backend chạy trên target và hai job MOCK đầu-cuối có evidence cùng owner ký nhận. Nếu mới có hồ sơ local, ghi “sẵn sàng review phần BE1 local”, không ghi “AWS đã nghiệm thu”.
