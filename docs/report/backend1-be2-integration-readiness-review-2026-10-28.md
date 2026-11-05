# Review và triển khai tích hợp BE1–BE2 — 28/10/2026

Cập nhật sau review cùng người dùng: bổ sung đường khởi tạo RDS rỗng có chọn rõ `initialize-empty` trước gate, deploy templates và [hướng dẫn bàn giao hiện hành](../plans/backend1-be2-current-handoff-2026-10-28.md). Phần dưới giữ kết quả612/27 của bản rename lúc11:54; xem [bằng chứng khởi tạo DB mới](../checks/be1-be2-integration-2026-10-28/fresh-db-verification.md) cho bản sửa tiếp theo. Chưa commit/push/deploy/AWS.

## Phạm vi và kết luận

Người dùng yêu cầu review và thực hiện các task trong kế hoạch, sau đó xác nhận đổi ngay module/package sang **back-end/src/fcaj-api / com.fcaj**. Yêu cầu mới cho phép sửa local, thay phạm vi “chỉ review” của tài liệu kế hoạch. Chưa thực hiện commit/push/deploy/gọi AWS/gửi tin nhóm. Giữ nguyên các thay đổi có trước; không stage/reset/stash. Không sửa V1/V2/V3 hoặc DB nghiệp vụ.

| Mức | Kết luận hiện tại |
|---|---|
| Source local | Build/package/test đạt sau rename; chưa có revision phát hành hoặc branch/PR để BE2 checkout |
| Smoke production components local | Đạt trên H2 và MySQL8.4 riêng, gồm worker thực của implementation; GitHub/S3/SQS giả lập, source synthetic |
| S3/SQS AWS | Chưa chạy; đã nhận IAM role/16 Outputs theo nguồn BE2, còn prefix/provider/worker owner/queue window cần chốt |
| EC2/RDS deploy | Chưa đủ đầu vào: BE2 báo chưa tạo EC2/RDS, chưa có compose production, TLS/DB identity/runtime target/image digest |
| Model thật/FE UI | Chưa nghiệm thu; ngoài đợt triển khai local này |

Nguồn BE2 đã đọc đầy đủ và đưa bản sao portable vào [be2-handoff-source.md](../checks/be1-be2-integration-2026-10-28/be2-handoff-source.md). Bản sao giữ nguyên nội dung ngày28/10, không xem đây là kiểm AWS live. Đã đọc báo cáo BE1 ngày27/28, runbook R1–R5, contract/fixtures/validator, source job/storage/security, migrations, runner, Dockerfile, compose, workflow và test liên quan.

Checkout: branch codex/backend1-foundation, HEAD nền f42a168d9cb0613a43801edca632a2de64837099; Git root E:/FA26/AWS/fcaj-hackathon-system. Lúc bắt đầu có25 tracked files thay đổi và nhiều thư mục mới chưa track. Không có AGENTS.md trong cây workspace qua rg -uu. Không thể quy mọi diff đã có cho phiên này hoặc xác định tác giả chỉ qua status. Sau rename Git hiển thị source cũ deleted và module mới untracked đến khi chuẩn bị revision; đó không phải mất source. HEAD nền không đại diện JAR mới.

## Ma trận RV-00 → RV-07 và yêu cầu BE2

| Task | Trạng thái | Owner / đầu ra / evidence |
|---|---|---|
| RV-00 nguồn đầu vào | Đạt local | BE1: đọc nguồn BE2 và sao chép nguyên bản + checksum; chưa có infra/cloudformation/poc.yaml trong checkout này |
| RV-01 revision | Có một phần | BE1/người dùng: source build được, còn commit SHA/branch/PR. Danh sách nhóm file bên dưới; không phát hành SHA nền |
| RV-02 package/path | Đạt local theo quyết định người dùng | BE1: fcaj-api/com.fcaj; main/migration/worker/scanner/CI/Docker context/E2E đường dẫn đã đổi. BE2 cần áp mapping vào checkout/infra của mình |
| RV-02 gate/runtime | Có một phần | Gate chạy candidate image trước compose up; BE2 chưa giao production compose/target. Không dùng compose MySQL8.0 hiện tại để deploy AWS |
| RV-03 migration | Đạt local | V1→V2→V3; checksum không đổi. Đã mang evidence V3 ngày28 ra target; lab mới MySQL8.4 chạy27 test đạt sau rename |
| RV-03 portability | Đạt local cho bộ bổ sung | Bản nguồn BE2, lab V3 đã lọc, contract hiện hành, sample/validator, smoke runner và checksum nằm trong docs/source; chưa có revision chia sẻ |
| RV-04 API/worker contract | Đạt local; chờ review liên nhóm | BE1: CURRENT.md + current-examples (17 JSON); Java smoke validate worker/result, Node đối chiếu exact bytes; FE/AI/BE2 chưa sign-off |
| RV-05 visibility | Đạt local | BE1: cả jobs/results120s,long poll20s. BE2 cập nhật template/default AWS từ60→120 và ghi evidence live |
| RV-06 smoke | Đạt local ở mức components | Test productionWorkerRoundTripSuccessFailureAndDuplicate nối API/dispatcher/worker/validator/DB/result. Runner Test/run-integration-smoke.ps1 không cần secret |
| RV-06 chuyển file cũ | Có một phần | BE1+operator: chưa có inventory DB refs và file vật lý cùng target; không có runner chuyển file. Không ghi “không áp dụng toàn hệ thống” |
| RV-07 AWS inputs | Chờ bên ngoài | IAM role/Outputs đã giao theo BE2; prefix và credential provider Java còn thiếu; worker/queue window cần chốt; EC2/RDS chưa có |
| FE nhận API | Chờ review FE | DTO/errors mẫu đã có; không tự retry POST vì chưa có HTTP idempotency key |
| Model thật | Chờ AI/BE1/BE2 | Benchmark, full input validation, model version, timeout/late result/deadline/IAM extension cần đợt riêng |

## Findings xác nhận và đã xử lý

**INT-01 — P2, lỗi cấu hình code: jobs receive override300s trái lựa chọn120s.** Worker cũ AwsMockWorkerMain:35 đặt300 trong khi results JobDelivery:67–68 đặt120. Đã chuyển cả hai sang JobQueuePolicy:8 dùng120/20/1 và test cả hai request thật gửi vào SqsClient mock trong AwsMockWorkerTest. Giữ outbox lease300s tại AiJobStore:60 vì đây là claim DB. Owner BE1 đã xử lý local; BE2 còn cập nhật queue default/template. Nghiệm thu AWS cần GetQueueAttributes từ đúng identity trong cửa sổ đã thống nhất; local test không thay thế bước đó.

**INT-02 — P2, thiếu đầu ra: contract hiện có còn câu chưa implement, mock source key khác producer.** README cũ/fixture dùng mock-source.json và fcaj.ai.source.mock.v1; producer ghi source.json/fcaj.ai.source.github.v1. Test worker cũ phải sửa key/manifest fixture trong bộ nhớ trước khi dùng. Giữ fixture lịch sử; thêm [CURRENT.md](../contracts/ai-job-v1/CURRENT.md), bộ current-examples xuất qua smoke production components và validator mới. Hash/size lấy đúng bytes sample. Không âm thầm nâng limits hoặc gửi synthetic sample vào AWS. Owner BE1 đã xử lý tài liệu/sample; FE/AI/BE2 cần duyệt protocol. Chưa có JSON Schema độc lập: dùng đặc tả bảng + Java validators/test executable và checker mẫu; checker không phải schema validator tổng quát.

**INT-03 — P2, thiếu smoke nối worker.** AiJobIntegrationTest:160 trước đây tự dựng completion qua helper, AwsMockWorkerTest chạy worker riêng. Đã thêm test nối producer request thật → AwsMockWorkerMain.process → completion → JobDelivery.consume → HTTP status/result, success/failure ở job riêng, duplicate không ghi đè, Score giữ nguyên. Cả H2 và MySQL8.4 đạt. Source/GitHub/S3/SQS giả lập có chủ đích. Test repository restart không phải application restart; chưa tuyên bố restart toàn process đạt. Owner BE1 xử lý local.

**INT-04 — P2, tài liệu lệch: BE2 còn hướng dẫn tới V2 và dùng số591/19.** Code gate DatabaseMigrationMain:83 yêu cầu current version3; User/Track cần row_version. Hướng hiện hành: DB trắng V1→V2→V3; DB cũ restore rehearsal8.4→đối chiếu/backup→baseline1→V2→V3. Giữ nguyên nguồn BE2 và báo cáo cũ như lịch sử, dùng tài liệu này làm bản bổ sung. Owner BE1 bàn giao bản bổ sung, BE2 cập nhật runtime/runbook trước deploy. Số610/26 là lượt cũ ngày28; lượt sau rename612/27, không cộng các lượt MySQL vào tổng612.

**INT-05 — P2, thiếu revision và evidence portable.** Code local chưa commit nên BE2 không thể checkout đúng artifact bằng SHA nền. Đã đưa nguồn BE2/evidence V3 ra docs, thêm manifest checksum, kiểm JAR mới. Owner người dùng+BE1 chuẩn bị revision theo nhóm sau cùng; BE2 build lại từ revision đó. Không suy ra source của mọi file mới thuộc BE1 chỉ từ tên thư mục. Không tự commit mọi file untracked.

**INT-06 — cần quyết định, đã chốt tại chat:** người dùng chọn rename ngay fcaj-api/com.fcaj. Đã sửa source/test/Maven/main/scanner và các runner/CI/compose/E2E reference. Không đổi tên service/container/resource chỉ vì package đổi. Mapping duy nhất hiện hành ở phần tiếp theo. Cần BE2 xác nhận source bên họ sẽ ghép theo mapping này; chưa có checkout BE2 để kiểm merge/churn/conflict.

**INT-07 — phụ thuộc ngoài: AWS và file cũ.** Không phải lỗi code P1. Role/Outputs đã được giao theo nguồn; chưa có prefix/provider Java và target EC2/RDS. Không đọc credential file, không coi Console session là credentials Java. Dump không chứa file vật lý. Local không có file dưới uploads/protected khi inventory, nhưng chưa query DB đang dùng hoặc kiểm nơi operator giữ file nên chưa kết luận không cần chuyển. Nếu DB có legacy avatar tên không theo UUID, AvatarStorage chỉ hỗ trợ local fallback (read tại AvatarStorage:49); chuyển thẳng AWS có thể làm link cũ không đọc được nếu chưa remap. Owner BE1+operator cần inventory/dry-run trước cutover.

Không mở lại R1–R5 như lỗi chưa sửa: V1/V2/V3 giữ checksum, version/conflict/limits/body timeout và grant mới đã có source/test. Gate không được chứng minh deploy live; mới có giả lập thứ tự lệnh và evidence lab cũ, không có claim “AWS ready”. Không phát hiện thêm P1 được chứng minh trong phạm vi review này.

**INT-08 — P2, CI launcher không có executable bit:** `git ls-files --stage` cho thấy wrapper cũ mode100644 trong khi workflow/Linux runner gọi trực tiếp ./mvnw. Đã đổi cả hai callsite sang `sh ./mvnw`, thêm .gitattributes giữ LF cho wrapper/shell runners ở path mới. Không cần stage/chmod index để chạy CI. Kiểm Bash syntax local; CI remote vẫn chưa chạy. Đây là bằng chứng mới ở đường CI hiện hành, độc lập với R1–R5.

## Mapping chạy hiện hành

| Thành phần | Giá trị |
|---|---|
| Module/build context | back-end/src/fcaj-api |
| Java package / Maven groupId | com.fcaj.hackathon / com.fcaj |
| Main trong JAR | com.fcaj.hackathon.HackathonApplication |
| Migration main | com.fcaj.hackathon.db.DatabaseMigrationMain |
| Worker main | com.fcaj.hackathon.aijob.AwsMockWorkerMain |
| JAR | target/hackathon-0.0.1-SNAPSHOT.jar; artifactId giữ hackathon |
| Compose service gate hiện hành | seal-backend; BE2 phải dùng tên này hoặc đổi đồng bộ gate/test khi cung cấp compose |
| Candidate image local | seal-backend-candidate:{CANDIDATE_REVISION}; khác ECR URI/tag/digest cần BE2 chốt |
| Host settings | /etc/seal/deploy-settings.sh; tên hiện hành giữ nguyên, BE2 chuẩn bị file riêng qua kênh an toàn |

Không dùng docker-compose.yml hiện có làm cấu hình AWS: vẫn là MySQL8.0/dev .env. Gate yêu cầu DEPLOY_COMPOSE_FILE, DEPLOY_MIGRATION_ENV_FILE, DEPLOY_DB_CATALOG, DEPLOY_RUNTIME_JDBC_URL và đúng candidate revision; kiểm image, aws profile, runtime URL bằng nhau, migration/history/schema8.4/case1/V3 trước compose up. DEPLOY settings không chứa trong gói public. Workflow vẫn chỉ deploy khi push main; PR chạy test. Không push main trong bước chuẩn bị source.

Từ module mới với Java21:

```powershell
./Test/run-integration-smoke.ps1
./Test/run-ai-mysql-tests.ps1
# Operator: chỉ dùng target/secret đã chốt; không load .env tự động
./Test/run-db-migration.ps1 -Action inspect -Catalog fcaj_poc
./Test/run-db-migration.ps1 -Action verify-deploy -Catalog fcaj_poc
```

Inspect/verify-deploy là lệnh operator cần đúng target/config; không chạy lệnh này với DB chưa được cấp. Migrate/baseline chỉ khi backup/rehearsal/cutover được review. Script demo reset không phải runner migration. Cấp quyền demo đã có bằng luồng System Admin đúng event theo runbook trước; không thêm tool backfill.

## Bằng chứng và lệnh thực sự đã chạy

- Git root/branch/HEAD/status, rg inventory và đối chiếu source/config; không truy cập repo BE2/AWS/credential.
- `mvnw.cmd -o -B -Dspring.profiles.active=test -DargLine=-Djdk.net.unixdomain.tmpdir=.../fcaj-api/target/socket-tmp package`: **612 tests,0 failure/error/skipped**, BUILD SUCCESS, Java21.0.10; kết thúc11:53:59+07 ngày28/10. Gồm21 test AiJobIntegrationTest và4 AwsMockWorkerTest.
- `Test/run-ai-mysql-tests.ps1`: **27 tests đạt** (21 job +6 concurrency), MySQL8.4 riêng, V1/V2/V3, S3/SQS/GitHub mock. Container fcaj-ai-lab-c507a3d76a92 đã dừng/giữ; log target/fcaj-ai-lab-c507a3d76a92-test.log; kết thúc11:54:28+07. Không cộng27 vào612.
- `node --test .../Test/deploy-verified.test.mjs`:2 đạt. Lần đầu Git Bash bị Windows từ chối khởi động; chạy lại với quyền môi trường phù hợp đạt, vẫn chỉ commands giả/temp files. Không thay assertion.
- `node --test .../Test/schema/inspect-dump.test.mjs`:7 đạt. `node docs/contracts/ai-job-v1/validate-fixtures.mjs`:7 JSON cũ hợp lệ nội bộ. `node docs/contracts/ai-job-v1/validate-current-examples.mjs`:17 JSON mới khớp HTTP/identity/keys/hash/size.
- `git diff --check` đạt sau đổi tên. Rà tham chiếu runtime không còn seal-api/com.seal/com/seal trong module/scripts/CI/compose/E2E được cập nhật. Tài liệu lịch sử có thể vẫn ghi tên cũ; dùng mapping ở trên.
- Kiểm ZIP JAR: Start-Class com.fcaj.hackathon.HackathonApplication;508 entries dưới com/fcaj,0 dưới com/seal. PropertiesLauncher chọn được DatabaseMigrationMain mới; chạy không args bị từ chối trước kết nối DB như thiết kế. Chưa chạy packaged CLI với DB live sau rename; không nhầm kiểm startup này với gate DB pass.
- JAR SHA-256 **3ea7af471241f72c76e4a8b10d05f145c9c81fd4f200d0cf951a9e20e0ec91c1**, artifact local chưa có release SHA/image digest. Không phụ thuộc JAR này trong gói source.
- V1/V2/V3 SHA-256 giữ nguyên so báo cáo ngày28; [migrations.sha256](../checks/be1-be2-integration-2026-10-28/migrations.sha256). [Evidence V3 đã lọc](../checks/be1-be2-integration-2026-10-28/mysql84-v3-lab-evidence.md) là bản sao evidence lab cũ, không trình bày như lượt restore mới sau rename.627 dòng gốc/hash/version=0 là kết quả lab đó; lượt này không chạy lại dump restore vì migration không đổi.

Hai lượt Maven trong giới hạn Windows ban đầu dừng khi đang khởi tạo Mockito, đã chủ động ngắt và chạy lại build trong môi trường cho phép test hoạt động. Không ghi hai lượt chưa hoàn tất đó là pass. Không chạy full suite thêm sau khi612 đạt. Chưa build Docker image/CI remote/browser OAuth/SMTP/application restart/AWS/model thật.

## Bộ source dự kiến giao và bước còn lại

Các nhóm cần review đưa vào revision: (1) toàn module fcaj-api và xóa tracked module seal-api cũ, bao gồm code,resources,migrations,test,runners,.env examples,Dockerfile,Maven wrapper; (2) workflow/compose build path và5 E2E bat +1 Playwright reference path; (3) docs contracts,current samples,review/checks/runbook/proposal liên quan BE1 đã có trước nếu người dùng xác nhận thuộc phạm vi phát hành. Chưa stage. BE2 lấy revision branch riêng, không dựa file Downloads/target. Không tự phát hành frontend feature hoặc tài liệu ngoài BE1 chỉ vì chúng untracked.

Loại khỏi gói: .env/secret thực, target, uploads/protected và file người dùng, raw backup/restore statements/source/output thật, IDE metadata. .gitignore module đã chuyển đi cùng module; file .env/target vẫn bị ignore. Manifest chỉ liệt kê file đã lọc trong bộ bổ sung, không phải chứng nhận mọi file working tree an toàn để commit.

Thứ tự còn lại:

1. BE2 nhận mapping mới và contract, xác nhận source path/package của nhánh họ; BE1 chuẩn bị revision với phạm vi cụ thể. Có thể review contract/evidence ngay khi còn chờ revision.
2. BE2+người dùng xác nhận APP_STORAGE_PREFIX, credential provider Java dùng role đã giao (ví dụ profile/assume-role theo cấu hình operator), scope inputs/rubrics/results/problems/avatars. Không gửi access key/password vào chat.
3. AI/BE2 chốt người/identity worker, queue pair và cửa sổ receive/delete. BE2 đổi queue default120s; giữ long poll20s,retention chính4 ngày,DLQ14 ngày,maxReceiveCount5 theo nguồn. Xác minh live trước smoke.
4. BE1+operator inventory file cũ: export refs DB chỉ đọc được phép, đối chiếu root thực, tồn tại/size/SHA-256/type/mapping/quyền; dry-run; chỉ khi có file cần chuyển mới thiết kế runner explicit target, upload bất biến, verify, cập nhật DB có kiểm soát, resume/rollback; không xóa nguồn trước nghiệm thu. Thiếu input này nên chưa viết công cụ di chuyển mù.
5. Smoke S3/SQS với DB lab riêng và role/prefix đã thống nhất; ghi jobId/requestId/messageId/ref/hash/time/identity đã lọc. Không dùng LOCAL_MOCK thay production path, không replay/purge DLQ để dọn test.
6. BE2 dựng EC2/RDS8.4/case1 và đưa endpoint/catalog/CA/TLS VERIFY_IDENTITY/runtime-migration users/network/HTTPS/compose/image digest; BE1 chạy gate từ candidate trước cutover theo runbook. Tách smoke transport và smoke EC2/RDS.

Những câu hỏi còn mở thay đổi hướng thực hiện: prefix thực và provider Java; ai chạy worker/cặp queue/lịch; scope source cuối để tạo revision; sign-off FE/AI về DTO/protocol. Quyết định package/path đã chốt, không cần hỏi lại.
