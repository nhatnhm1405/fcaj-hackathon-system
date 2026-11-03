# BE1 → BE2: hướng dẫn tích hợp hiện hành

**Mốc phát hành source03/11:** đã commit/push lên branch riêng `feature/seal-be1-handoff-2026-11-03`, full source SHA `3eda1fb189a972b9a5b5c85ce482900a1c66ac42`. Dùng [báo cáo trả lời BE2](../report/be1-tra-loi-be2-truoc-deploy-2026-11-03.md) cho link/lệnh checkout và [release verifier](../checks/seal-be1-release-2026-11-03/README.md). Commit tài liệu sau đó không đổi code. Các trạng thái branch/baseline/chưa commit và manifest working-tree bên dưới giữ context trước phát hành; source SHA mới thay chúng cho việc build, không thay kết quả target còn pending. Chưa merge main/deploy AWS; `main` mới có rename FCAJ/Python worker cần phối hợp riêng.

**Cập nhật sau fix coverage JWT/session03/11:** [báo cáo fix](../report/seal-be1-jwt-session-regression-fix-2026-11-03.md), [checkpoint mới](../checks/jwt-session-regression-2026-11-03/README.md). P2 test logout/session đã đóng; production/migrations/contracts không đổi. Full package mới633 thống kê:624 thực thi đạt,9 MySQL-only skipped; component36/36. Các số614/26 và manifest local/review trước fix bên dưới là checkpoint lịch sử, không đại diện test source mới. Dùng verifier checkpoint mới cho working tree sau fix; không refresh manifest cũ. Candidate vẫn chưa có release SHA mới hoặc nghiệm thu AWS.

Cập nhật **03/11/2026**, Asia/Saigon. Candidate local đã chuyển module/package về SEAL và được kiểm lại theo [báo cáo mới](../report/seal-be1-local-completion-2026-11-03.md). Nhánh vẫn là **feature/backend1-be2-handoff**, nhưng HEAD `bab44e5fdeb710e6c71982b2e8f5547829fa8e40` chỉ là baseline trước sửa; **working tree mới chưa commit/push, chưa có release SHA mới để BE2 checkout**. Không dùng HEAD cũ để build rồi gọi đó là candidate SEAL đã kiểm. Pipeline ECR đã hoãn; cách giao candidate/private EC2 cần BE2 xác nhận.

Các phần target/AWS trong tài liệu này là runbook để phối hợp, không phải việc đã triển khai. Người dùng xác nhận chưa có đầu vào mới từ BE2; ALB direct/port/TLS/domain vẫn đang thảo luận. Xem [runtime/ALB matrix](seal-be1-runtime-config-runbook.md), [runbook dữ liệu/API](seal-be1-smoke-data-runbook.md), và [bảng BE2 cần trả lời](seal-be1-be2-clarifications-2026-11-03.md).

Sau khi đối chiếu trực tiếp file hỏi BE2 ngày01/11, dùng [checklist/plan tổng hợp](seal-be1-be2-consolidated-handoff-plan-2026-11-03.md) cho buổi bàn giao. Phía BE1 đã chốt dữ liệu mẫu trước, restore `.bak` riêng. File gốc BE2 đã báo Session Manager/Docker/RDS TLS; các đầu vào đó không còn là câu hỏi “đã có chưa”, nhưng target/container acceptance vẫn chưa chạy. Checkpoint review tài liệu tách khỏi manifest local trước đó để không viết lại bằng chứng lịch sử.

## 1. Phạm vi sẵn sàng

BE1 cung cấp migration V1–V3, storage local/S3, ba API AI job, outbox/results consumer, worker MOCK, kiểm quyền và hướng dẫn vận hành. Candidate SEAL local đạt 614 tests thực thi/9 MySQL-only skipped ở full package, 36/36 MySQL lab, 26/26 component/API smoke. Xem [evidence ngày03/11](../checks/seal-be1-local-2026-11-03/README.md); các suite có ca trùng, không cộng tổng. Model thật, FE job UI, OAuth/SMTP qua browser và nghiệm thu AWS còn là phần phối hợp. [Fresh DB verification ngày28](../checks/be1-be2-integration-2026-10-28/fresh-db-verification.md) là checkpoint lịch sử.

Đã nhận IAM role và16 Outputs theo [nguồn BE2 ngày28](../checks/be1-be2-integration-2026-10-28/be2-handoff-source.md); chưa xác minh live. Phản hồi tiếp theo của BE2 đề xuất RDS mới cho PoC, Flyway và migration trước phục vụ request. Implementation bổ sung chạy migration từ candidate trong một process riêng trước gate rồi khởi động backend; BE2 cần xác nhận cách tích hợp này trong quy trình của họ. Không mở endpoint migration hoặc tự baseline DB không rõ nguồn.

## 2. Source và chuẩn phiên bản chung

| Thành phần | Giá trị hiện hành |
|---|---|
| Module | back-end/src/seal-api |
| Maven group/package | com.seal / com.seal.hackathon |
| Main | com.seal.hackathon.HackathonApplication |
| Migration main | com.seal.hackathon.db.DatabaseMigrationMain |
| Worker main | com.seal.hackathon.aijob.AwsMockWorkerMain |
| Artifact | target/hackathon-0.0.1-SNAPSHOT.jar |
| Compose service mà gate kiểm | seal-backend |
| Host settings mà workflow đọc | /etc/seal/deploy-settings.sh |

DB chính thức ở mức V3 là cấu trúc cộng dồn V1+V2+V3, không phải chỉ chạy riêng file V3. V1 tạo31 bảng nghiệp vụ;V2 thêm3 bảng job;V3 thêm row_version cho User/Track. Không sửa file đã áp dụng; thay đổi tương lai dùng migration mới và review gate/fingerprint tương ứng. Gate hiện yêu cầu đúngV3, nên muốn phát hànhV4 phải cập nhật kiểm chứng/gate, không chỉ thêm SQL rồi deploy.

Đề xuất DB dev dùng chung: một bộ migration đã review trong Git, một operator/pipeline chịu trách nhiệm DDL; các thành viên dùng code tương thích và tài khoản runtime đọc/ghi dữ liệu. Flyway startup trên DB đã current phải là no-op. Không chạy nhánh migration chưa duyệt hoặc run-demo reset vào DB chung. Dữ liệu local cũ không tự có trên RDS mới; cần bước chuyển riêng nếu nhóm muốn mang dữ liệu sang.

## 3. Kiểm local từ repository đầy đủ

Cần Java21,Node.js,Docker Linux Engine cho MySQL lab. Từ module backend:

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21.0.10'
./Test/run-integration-smoke.ps1
./Test/run-ai-mysql-tests.ps1
```

Runner PowerShell dùng Maven offline vì máy BE1 đã có cache. Máy mới cần tải dependency bằng Maven trước, ví dụ `./mvnw.cmd -B -DskipTests package`; sau đó chạy script. Linux CI có `bash Test/run-ai-mysql-tests.sh`, tự package candidate trước test. Script tạo MySQL8.4 mới chỉ bind127.0.0.1, mật khẩu ngẫu nhiên, không dùng .env/DB đang dùng; dừng và giữ container để xem bằng chứng. FreshDatabaseDeploymentTest chỉ bật khi có URL lab đúng pattern, không trỏ nó tới RDS.

POM nạp Mockito agent lúc JVM test khởi động và đặt socket temp dưới target; không cần tự copy agent từ target cũ. Integration smoke còn chạy `ApiSmokeRehearsalTest` với login cookie/CSRF và hai đội × ba thành viên qua controllers thật. Không chạy hai Maven build/test ghi chung target.

Từ repository root:

```sh
node --test back-end/src/seal-api/Test/deploy-verified.test.mjs
node --test back-end/src/seal-api/Test/schema/inspect-dump.test.mjs
node docs/contracts/ai-job-v1/validate-current-examples.mjs
node back-end/src/seal-api/Test/verify-deploy-templates.mjs
node docs/checks/seal-be1-local-2026-11-03/verify-source-preservation.mjs
node docs/checks/be1-be2-handoff-review-2026-11-03/verify-review.mjs
```

Các test cần cả docs/contracts/ai-job-v1 fixtures. Dump lab còn cần docs/checks/schema-inventory-2026-10-27/dump-static-comparison.json. Không chỉ copy module riêng rồi coi đó là checkout đầy đủ để test.

Verifier review đọc source/config theo snapshot local đã kiểm, đồng thời kiểm hash tài liệu cập nhật ở checkpoint review riêng; manifest local489 files trước review được giữ nguyên và sẽ báo doc drift nếu dùng để kiểm tài liệu mới. Cả hai checkpoint đều mô tả working tree, không phải release. Verifier ngày28 đọc HEAD và chỉ chứng minh commit lịch sử. Khi có release SHA được chốt, tạo checkpoint release tương ứng. SQL migrations và contract artifacts được cố định LF bằng .gitattributes đã cập nhật path SEAL.

## 4. BE2 tạo DB mới

BE2 tạo RDS MySQL8.4, gắn parameter group lower_case_table_names=1 ngay từ lúc tạo; tạo catalog dự kiến fcaj_poc với utf8mb4/utf8mb4_0900_ai_ci. Giao endpoint/port/catalog/engine thực,network,CA/trust và identity qua kênh operator. Không dùng volume MySQL8.0 cũ hoặc compose dev trong repo thay cho RDS.

Tách migration identity có quyền DDL/metadata trong catalog với runtime identity SELECT/INSERT/UPDATE/DELETE trên catalog, bao gồm đọc Flyway history. Các quyền cụ thể phải được BE2 review theo target; không cấp quyền tạo/xóa toàn server cho ứng dụng. Test lab có ca runtime DML-only chạy Flyway no-op và CRUD nhưng bị từ chối CREATE TABLE; đây không thay kiểm IAM/network/TLS RDS thật.

Không cần baseline cho DB rỗng. Với DB đã có dữ liệu và chưa có history: dùng quy trình backup/restore/schema review/baseline1 riêng trong [runbook](backend1-review-fixes-operator-runbook-2026-10-28.md). initialize-empty không nâng cấp V1/V2 đang tồn tại, không tự repair/baseline/clean.

## 5. Cấu hình deploy bằng đúng candidate

Các template nằm trong [deploy/](../../back-end/src/seal-api/deploy/):

- compose.aws.example.yml: backend-only, image candidate, aws profile, revision, bind loopback cho reverse proxy, truststore readonly. Cần Docker Compose2.30+ vì env_file format raw giữ nguyên giá trị chứa dấu dollar/quotes.
- compose.aws.alb-direct.example.yml: phương án **đề xuất** ALB direct HTTP:8080, publish trên `DEPLOY_TARGET_BIND_IP` private interface bắt buộc. BE2 chốt SG target từ SG ALB, protocol/TLS/health; không dùng loopback template cho direct target.
- deploy-settings.sh.example: đường dẫn operator,URL/catalog/truststore và mode mặc định verify-only.
- migration.env.example: URL/user/password migration và hướng dẫn JAVA_TOOL_OPTIONS cho truststore. Không load .env local.
- worker.env.example: chỉ region/bucket/prefix/cặp queue, không cần DB/JWT; dùng identity worker và lịch riêng.
- Runtime dùng [.env.aws.example](../../back-end/src/seal-api/.env.aws.example); copy vào file private, điền DB/JWT/HTTPS/bucket/prefix. APP_AI_JOBS_ENABLED=false ban đầu,SEED_SCENARIO=NONE. Nếu cần tạo admin trên DB trắng, bật BOOTSTRAP_ADMIN_ENABLED có chủ đích với email/password riêng; sau provisioning tắt lại.

Gợi ý copy template vào /etc/seal hoặc nơi operator quản lý; settings mà workflow đọc là /etc/seal/deploy-settings.sh. BE2 xác nhận paths thực, không tự move file trên host. Giữ private env/settings/truststore ngoài Git; module cũng ignore deploy/*.env,deploy/deploy-settings.sh,deploy/*.p12,deploy/*.jks. Không chép secret vào PR/chat/log. Khi kiểm Compose thủ công dùng `config -q`, không lưu output config đã expand secret. Gate chỉ pipe config JSON vào checker, không in ra stdout.

Provision truststore từ RDS CA được duyệt; cả migration.env và runtime.env dùng JAVA_TOOL_OPTIONS trỏ /run/secrets/rds-truststore.p12 và password trong file private. Cả CLI containers và runtime được mount cùng truststore ở path đó. Driver dùng sslMode=VERIFY_IDENTITY. Không tự tắt TLS khi certificate/hostname sai.

Nếu test OAuth, operator đổi DEPLOY_SPRING_PROFILES_ACTIVE thành aws,oauth-google hoặc aws,oauth-github phù hợp và cấp credentials tương ứng trong runtime.env. Template mặc định chỉ aws; không bật provider thiếu cấu hình.

## 6. Lần deploy đầu vào RDS rỗng

Chỉ sau khi đã có revision được duyệt, target/config/identity rõ ràng, BE2 đặt `DEPLOY_DB_MODE=initialize-empty` trong settings cho lần khởi tạo. Workflow/host chạy Test/deploy-verified.sh bằng đúng checkout/revision:

1. Kiểm Git clean/revision/mode và build candidate image.
2. Kiểm Compose chọn image đó,aws profile và đúng runtime URL.
3. Chạy CLI initialize-empty từ chính candidate; bắt buộc migration URL trùng runtime URL trước mọi kết nối/ghi; kiểm MySQL8.4/case1/catalog/charset/collation.
4. Nếu catalog rỗng: Flyway chạy V1→V2→V3. Nếu không rỗng: chỉ chấp nhận khi gate V3 đầy đủ đã đạt, không áp thêm migration.
5. Chạy verify-deploy riêng; kiểm history/checksum/fingerprint/entity mapping và JDBC job columns.
6. Chỉ sau thành công mới `compose up -d --no-deps --no-build seal-backend`; các dependencies do operator chuẩn bị trước, không thay DB/FE/proxy. Sau khởi tạo operator chuyển DEPLOY_DB_MODE về verify-only.

Nếu giữ initialize-empty và chạy lại trên DB V3 hợp lệ, dữ liệu/history giữ nguyên. Mode verify-only mặc định không ghi DB. DB V1/V2,history sai,schema lạ,baseline3 giả hoặc khác target bị từ chối; operator xử lý riêng trước deploy.

Migration failure có thể để lại DDL/history đã chạy một phần: quy trình dừng trước thay backend nhưng **không hứa tự rollback DB**. Kiểm nguyên nhân/backup/recovery; không sửa checksum/repair/clean để vượt gate. Đây là lý do lần khởi tạo được chọn rõ ràng và DB upgrade có dữ liệu phải có window/backup riêng. Rollback image không tự undo DDL.

Có thể chạy operator local bằng Test/run-db-migration.ps1 -Action initialize-empty -Catalog TARGET khi operator cấp BE1_MIGRATION_* và BE1_EXPECTED_RUNTIME_JDBC_URL đúng; quyền chạy lệnh không được suy ra từ tài liệu. Đường deploy chính dùng JAR trong candidate image để không lệch source.

## 7. Smoke API,file và jobs trên AWS

Chốt [các đầu vào còn thiếu](backend1-be2-aws-smoke-inputs-2026-10-28.md): prefix thực,credential provider dùng role đã giao,worker owner/cặp queue/window và queue defaults120s. Dùng cùng bucket/prefix, logical key resolve prefix/key. Backend needs problems/avatars và inputs/rubrics/results; worker cần input/rubric read,result put/read và queue actions đúng vai trò.

Sau deploy kiểm health/live,ready,version; ready chỉ kiểm DB. Login tài khoản test đã approve,grant coordinator đúng event hoặc judge assignment,timer JUDGING RUNNING,rubric/submission hợp lệ. Kiểm avatar và đề thi theo quyền,read sau restart. Chạy POST job202,poll status,worker MOCK success/failure trên hai job riêng,result đã lọc,duplicate/retry/commit-before-ack. Worker CLI dưới Test/run-aws-mock-worker.ps1 chỉ xử lý1 message và dùng queue thật nên phải có cửa sổ test trước.

[Contract hiện hành](../contracts/ai-job-v1/CURRENT.md) có mẫu3 API,states,safe errors,limits,refs và validator. Không gửi current-examples hoặc fixture cũ vào queue nghiệp vụ; chúng là synthetic. FE không tự retry POST vì chưa có HTTP idempotency key. Worker offline giữ pending; model thật/FE UI/late-result/deadline policy là đợt nghiệm thu riêng.

Trên Linux hoặc môi trường chỉ có JRE21, sau khi operator cấp environment/identity worker từ nguồn an toàn, dùng chính JAR đã đóng gói thay vì biên dịch lại:

```sh
java -Dloader.main=com.seal.hackathon.aijob.AwsMockWorkerMain \
  -cp target/hackathon-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher --one-success
```

Đổi sang --one-failure chỉ trên job mới để thử lỗi có chủ đích. Đây là lệnh SQS thật, không chạy chỉ để kiểm syntax. Nếu chạy container, dùng cùng candidate image và --env-file worker.env do operator cấp; credentials host không tự xuất hiện trong container, BE2 cần kiểm default credential chain/role thật trước.

## 8. Bộ bàn giao và thông tin còn chờ

BE1: source/migrations/tests/runners/templates/contracts/evidence/checklist và manifest đã lọc. Candidate SEAL hiện là working tree chưa commit/push; hash JAR/image local trong evidence không phải release SHA hoặc image BE2 đã nhận. Không đưa target,.env,backup/dump,file người dùng,raw source/output thật vào Git. Bản gốc các tài liệu có trước được giữ dưới target/preexisting-documents khi cập nhật mốc này.

BE2: xác nhận revision/path/package,create RDS/EC2,network/TLS/identity,production compose/proxy,config secret,prefix/provider,queue defaults,worker window,ECR tag/digest và nơi thu log. Template ở đây là starting point, không phải template CloudFormation của BE2 đã được review hoặc deploy production hoàn chỉnh.

Người dùng/FE/AI: duyệt scope source,sign-off contract và lịch nghiệm thu. Nếu cần dữ liệu/file cũ, cung cấp inventory và nguồn file được phép kiểm; DB dump không chứa avatar/đề thi vật lý. Không đánh dấu những đầu vào bên ngoài chưa có là code đã nghiệm thu AWS.
