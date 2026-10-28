# Checklist review cùng người dùng trước commit/push BE1

Cập nhật sau review: người dùng đã xác nhận OK và yêu cầu commit/push nhánh mới,không dùng tên codex/openai. Nhánh bàn giao được chọn là feature/backend1-be2-handoff. Các trạng thái chưa commit/push bên dưới là checkpoint lúc lập checklist; trạng thái hiện hành và SHA lấy từ Git của nhánh bàn giao. Các mục AWS/FE/AI chưa nghiệm thu vẫn giữ nguyên.

Ngày28/10/2026, Asia/Saigon. Người dùng yêu cầu review kỹ và chỉ push sau khi xác nhận OK. **Chưa stage, commit, push hoặc mở PR.** Checklist này không phải xác nhận người dùng đã duyệt. Dấu[x] là phần agent đã kiểm; dấu[ ] là phần còn cần người dùng/bên liên quan xác nhận hoặc kiểm thực tế.

Kết luận kỹ thuật hiện tại: đủ để chuẩn bị source bàn giao trên branch riêng; chưa nghiệm thu AWS/deploy. Source snapshot421 paths vẫn là bản đã kiểm612 test backend/package và27 test MySQL riêng. Không chạy lại toàn suite trong lượt review này vì chưa thay source thực thi.

Cập nhật ở lượt sửa tiếp theo: đã bổ sung code/test cho RDS rỗng, deploy templates và [điểm bắt đầu bàn giao](backend1-be2-current-handoff-2026-10-28.md). Các số421/612/27 trên là checkpoint trước sửa; manifest và [bằng chứng mới](../checks/be1-be2-integration-2026-10-28/fresh-db-verification.md) được cập nhật riêng, không dùng checkpoint cũ để xác nhận code mới đã test. Chưa có xác nhận OK commit/push của người dùng.

## 1. Phạm vi thay đổi trên Git

- [x] Branch codex/backend1-foundation; HEAD nền f42a168d9cb0613a43801edca632a2de64837099. SHA này chưa chứa bộ BE1 local.
- [x]314 tracked file paths dưới module cũ hiện đánh dấu deleted đều có file tương ứng ở module mới. Source mới hiện untracked; không stage nên Git chưa trình bày rename hoàn chỉnh.
- [x] Ngoài source, có8 tracked files sửa: workflow,compose,5 demo bat và1 Playwright spec. Đã đọc diff: demo/Playwright chỉ thay đường dẫn module; compose thay build context/env_file path.
- [ ] Người dùng duyệt phạm vi cả BE1 nền đã làm trước và các sửa tích hợp/rename mới. Đây là gói có thay đổi migration/storage/auth/jobs/config, không chỉ một lần đổi tên.

Giải thích: khi chuẩn bị commit cần đưa cả xóa module cũ và thêm module mới, nếu chỉ đưa một phía thì BE2 sẽ nhận source thiếu hoặc hai module trùng. Sau khi stage theo scope được duyệt phải đọc diff có nhận diện rename và kiểm file bị loại; chưa làm bước này trong lượt review.

## 2. Đổi tên và tương thích lệnh chạy

- [x] Đã chốt với người dùng: back-end/src/fcaj-api; Java com.fcaj.hackathon; Maven groupId com.fcaj.
- [x] Main, migration main, worker main, entity scanner, source/import/test, Docker context, CI, runner và E2E references đã đổi.
- [x] JAR local: Start-Class com.fcaj.hackathon.HackathonApplication; không có class com/seal. Build/test sau rename đạt.
- [ ] BE2 cập nhật checkout/scripts của họ theo path/package mới.
- [ ] Người dùng biết một số tên vận hành vẫn còn seal-backend,seal_backend,seal-hackathon,artifactId hackathon và /etc/seal/deploy-settings.sh. Đây là các tên riêng được giữ trong mapping, không cản compile. Đợt này chưa đổi giao diện/logo/domain hoặc toàn bộ tên tài nguyên AWS.

Xem [mapping đầy đủ](../report/backend1-be2-integration-readiness-review-2026-10-28.md). Khi kiểm tay, mở [pom.xml](../../back-end/src/fcaj-api/pom.xml),[Dockerfile](../../back-end/src/fcaj-api/Dockerfile),[workflow](../../.github/workflows/deploy.yml).

## 3. Database và bảo toàn dữ liệu

- [x] V1 tạo core schema,V2 thêm AiJob/AiJobOutbox/AiProcessedEvent,V3 thêm row_version cho User/Track. Checksum giữ nguyên.
- [x] Hibernate validate; Flyway quản lý thay đổi schema; automatic baseline/clean bị chặn.
- [x] Đã có evidence lab627 dòng cũ giữ fingerprint cột gốc; đây là kết quả rehearsal đã lưu, không phải kiểm DB hiện dùng hoặc RDS.
- [x]27 test MySQL8.4 riêng sau rename đạt; gồm V1–V3 và luồng job/concurrency.
- [ ] Người dùng hiểu DB cũ không có Flyway history có thể từ chối boot ngay cả khi bật profile local. Cần backup/rehearsal/baseline có chủ đích theo runbook; không bật Hibernate update để vượt lỗi.
- [ ] BE2 tạo MySQL8.4 với lower_case_table_names=1 ngay khi tạo DB,utf8mb4/utf8mb4_0900_ai_ci,CA/TLS/runtime-migration identity.
- [ ] Khi chuyển DB cũ thật: xác định target,backup,window dừng ghi,rollback và file vật lý trước cutover.

V3 tăng version khi entity thay đổi để từ chối ghi từ dữ liệu cũ; API conflict trả409 CONCURRENT_UPDATE. Giúp tránh profile update khôi phục avatar key đã xóa hoặc release đề ghi ngược file cũ. Không tự retry bằng entity cũ. Test đã bao phủ6 race trên H2/MySQL.

Nguồn: [migration directory](../../back-end/src/fcaj-api/src/main/resources/db/migration/),[DatabaseMigrationMain](../../back-end/src/fcaj-api/src/main/java/com/fcaj/hackathon/db/DatabaseMigrationMain.java),[runbook](backend1-review-fixes-operator-runbook-2026-10-28.md). Runbook cũ có tên module lịch sử; dùng fcaj-api/com.fcaj theo mapping mới.

## 4. Admin, demo và cách khởi động

- [x] Không tự tạo admin với mật khẩu mặc định khi boot. BootstrapAdminSeeder chỉ chạy khi bật cấu hình và có email/password operator cấp.
- [x] Nếu email đã thuộc tài khoản không phải admin, bootstrap từ chối thay vì tự nâng quyền. Admin có sẵn không bị đổi password bởi bootstrap.
- [x] Role/template tham chiếu vẫn được seed; demo data là opt-in,SEED_SCENARIO mặc định NONE.
- [x] Demo coordinator mới được grant đúng event; dữ liệu demo đã tồn tại không tự backfill.
- [ ] Người dùng chấp nhận cần chuẩn bị admin ban đầu cho DB trắng; không dựa tài khoản/mật khẩu demo cũ để vào môi trường AWS.
- [ ] Demo đã có quyền thiếu: cấp bằng System Admin cho đúng event như quyết định trước; không thêm tool backfill.
- [ ] Biết run-demo.ps1 là runner reset DB local có xác nhận; không dùng để migrate/triển khai DB đang giữ dữ liệu. Lượt review này không chạy runner reset.

Nguồn: [BootstrapAdminSeeder](../../back-end/src/fcaj-api/src/main/java/com/fcaj/hackathon/config/BootstrapAdminSeeder.java),[DataSeeder](../../back-end/src/fcaj-api/src/main/java/com/fcaj/hackathon/config/DataSeeder.java),[run-demo.ps1](../../back-end/src/fcaj-api/run-demo.ps1).

## 5. File đề thi và avatar

- [x] Có abstraction local/S3; S3 private,bucket/prefix do cấu hình; key logical resolve thành prefix/key.
- [x] Đề thi tải qua guard: coordinator đúng event; participant thuộc đội được duyệt,đúng track và đề đã phát.
- [x] Avatar vẫn giữ URL /uploads/avatars/...; kiểm signature ảnh/size,không chấp nhận SVG/HTML.
- [x] File mới bất biến; cleanup file cũ sau DB commit; rollback dọn object mới,giữ file cũ. Optimistic version bảo vệ writer User/Track.
- [ ] File cũ cần inventory trước chuyển: DB dump không mang file vật lý; local avatar tên cũ chỉ có fallback khi dùng local storage. Chưa có runner di chuyển file cũ.
- [ ] Chưa kiểm S3/IAM thật,upload/read/replace/restart trên AWS. BE2 cần prefix và quyền problems/avatars ngoài inputs/rubrics/results.

Kiểm tay sau khi có môi trường test: đổi avatar→đọc lại; thay đề→phát đề→tải bằng user đúng/sai track,event; hai lần cập nhật xen kẽ phải có conflict hợp lệ. Không dùng tài khoản nghiệp vụ thật để thử trước khi xác nhận dữ liệu test.

Nguồn: [AvatarStorage](../../back-end/src/fcaj-api/src/main/java/com/fcaj/hackathon/storage/AvatarStorage.java),[TrackProblemService](../../back-end/src/fcaj-api/src/main/java/com/fcaj/hackathon/service/TrackProblemService.java),[S3ObjectStorage](../../back-end/src/fcaj-api/src/main/java/com/fcaj/hackathon/storage/S3ObjectStorage.java).

## 6. Quyền truy cập,JWT và OAuth

- [x] Job mới kiểm approval/quyền hiện tại từ DB,không chỉ tin role trong token. Coordinator cần event grant; judge cần assignment; lịch sử judge chỉ của chính requester khi assignment còn hiệu lực.
- [x] AI insights API cũ cũng được kiểm coordinator đúng event/approval và judging đang mở. Đây là thay đổi hành vi có thể khiến tài khoản từng gọi được nay bị403/đóng judging.
- [x] JWT key kiểm Base64,tối thiểu32 bytes khi decode; AWS không có signing key mặc định. Cookie secure/CSRF/CORS và request-scoped security context được giữ.
- [x] Google cần email_verified; chặn đổi provider/subject đã liên kết; email bên thứ ba không authoritative cần link-account check; giữ avatar tự upload khi OAuth login.
- [ ] Người dùng review các thay đổi auth này như một phần của bộ BE1,cùng nhận biết tài khoản demo grant global có thể không đủ quyền.
- [ ] Browser OAuth/callback/domain/SMTP thật chưa kiểm; chưa có flow UI/OTP link-account riêng hoặc refresh-token mới.

Nguồn: [JobAccess](../../back-end/src/fcaj-api/src/main/java/com/fcaj/hackathon/aijob/JobAccess.java),[AiJudgeAssistantService](../../back-end/src/fcaj-api/src/main/java/com/fcaj/hackathon/service/AiJudgeAssistantService.java),[JwtService](../../back-end/src/fcaj-api/src/main/java/com/fcaj/hackathon/security/JwtService.java),[OAuth user service](../../back-end/src/fcaj-api/src/main/java/com/fcaj/hackathon/security/oauth2/CustomOAuth2UserService.java).

## 7. AI job,SQS và contract FE/AI

- [x]3 API:create202,status200,result200 khi thành công; lỗi safe code ở wrapper message. State:PENDING_DISPATCH,QUEUED,SUCCEEDED,FAILED.
- [x] Snapshot repo GitHub public pin commit,verify blobs,giới hạn files/bytes/time; không chạy code bài thi. Rubric/source/manifest kiểm giới hạn trước khi commit job/outbox.
- [x] Job+outbox atomic; worker lưu output→gửi completion→ack jobs; consumer commit result+ledger→ack results; duplicate/conflict được kiểm.
- [x] Jobs/results receive120s,long poll20s; outbox lease300s là cơ chế riêng,không đổi.
- [x] Smoke nối API→dispatcher→worker implementation→consumer→HTTP result,success/failure/duplicate,đã đạt trên H2/MySQL riêng; official scores không đổi.
- [x] Contract CURRENT.md và17 JSON sample khớp actual bytes/HTTP; fixture cũ được giữ như lịch sử. Mẫu synthetic không gửi vào queue nghiệp vụ.
- [ ] Người dùng hiểu MOCK chưa đánh giá code bằng model thật; worker offline giữ pending; chưa có RUNNING/EXPIRED,deadline terminal hoặc HTTP idempotency key. FE không tự retry POST khi chưa rõ request trước đã tạo job hay chưa.
- [ ] FE/AI/BE2 review DTO/protocol; chưa có JSON Schema độc lập hoặc sign-off liên nhóm. Worker MOCK không phải strict input validator đầy đủ cho model thật.
- [ ] BE2 áp queue defaults120s; IAM/transport/redrive/DLQ/application restart và thời gian xử lý thật còn cần test.

Nguồn: [CURRENT.md](../contracts/ai-job-v1/CURRENT.md),[sample HTTP create](../contracts/ai-job-v1/current-examples/success/http-create.json),[sample result](../contracts/ai-job-v1/current-examples/success/http-result.json),[JobDelivery](../../back-end/src/fcaj-api/src/main/java/com/fcaj/hackathon/aijob/JobDelivery.java).

## 8. Cấu hình AWS,health và log

- [x] Profile aws không import .env local; yêu cầu HTTPS origins,secure cookies,DB TLS VERIFY_IDENTITY; Flyway+validate; storage bucket/region/prefix bắt buộc.
- [x] APP_AI_JOBS_ENABLED mặc định false; bật khi chuẩn bị worker và consumer window. SDK dùng default credential chain; không đọc phiên Console.
- [x] X-Request-ID và health/live,ready,version đã có;ready chỉ kiểm DB;version lấy APP_BUILD_REVISION hợp lệ hoặc unknown.
- [ ] BE2 xác nhận prefix/provider Java,worker owner/queue window; role/16 Outputs đã giao theo nguồn,chưa verify live.
- [ ] BE2 cấu hình nơi thu log,network,IAM,TLS và thực sự kiểm log/health trên runtime. Có log group không chứng minh backend đã gửi log.

Nguồn: [.env.aws.example](../../back-end/src/fcaj-api/.env.aws.example),[application-aws.properties](../../back-end/src/fcaj-api/src/main/resources/application-aws.properties),[đầu vào AWS](backend1-be2-aws-smoke-inputs-2026-10-28.md).

## 9. CI và đường deploy

- [x] CI backend dùng path fcaj-api,JUnit+MySQL8.4+deploy gate tests. sh ./mvnw tránh phụ thuộc executable bit; LF cho script Linux.
- [x] Workflow chỉ deploy trên push main; PR target main chạy test. Push branch riêng hiện tại không kích hoạt workflow này nếu không có PR phù hợp.
- [x] Gate chọn đúng candidate artifact,kiểm schema/history V3 trước compose up;2 command-recording tests pass.
- [x] Đã sửa đường khởi tạo catalog rỗng: mode initialize-empty có kiểm target trước ghi, V1→V2→V3 rồi gate; verify-only vẫn mặc định. Test deploy được mở rộng lên8 ca; DB không rõ nguồn/pre-V3 vẫn bị chặn.
- [ ] Người dùng duyệt mục tiêu push chỉ là branch codex/backend1-foundation; không merge/push main hoặc deploy trong lần bàn giao.
- [ ] Docker image build/CI remote chưa chạy. Dockerfile đóng gói với skipTests,dựa vào CI đã chạy test; local JAR pass chưa chứng minh image build pass.
- [x] Ở lượt bổ sung RDS rỗng: Dockerfile thật build đạt;Compose template được parse và probe container shell với env giả đạt. CI remote và ứng dụng trên AWS vẫn chưa chạy; xem bằng chứng mới.
- [ ] Compose hiện có vẫn MySQL8.0/dev env,không phải production AWS compose. BE2 phải cung cấp production compose/service mapping/settings trước deploy.

Nguồn: [workflow](../../.github/workflows/deploy.yml),[deploy gate](../../back-end/src/fcaj-api/Test/deploy-verified.sh),[compose](../../docker-compose.yml).

## 10. Bằng chứng kiểm thử và tính đầy đủ của gói

- [x]612 backend tests/package đạt;27 MySQL integration tests là lượt riêng trên cùng tập test liên quan,không cộng thành639.
- [x]421 source/config paths khớp reviewed snapshot;314 file cũ đủ counterpart mới;git diff --check đạt.
- [x]7 parser tests,2 deploy tests,fixture cũ và sample mới có bằng chứng. Lượt cuối này đối chiếu evidence có sẵn thay vì nhận là đã chạy lại toàn suite.
- [x] .env,target,uploads,protected bị Git ignore;không stage chúng. Quét candidate source/docs theo mẫu AWS access-key identifier/private-key block không có hit;đây là pattern scan có giới hạn.
- [x] Test worker cần docs/contracts/ai-job-v1 fixture;dump runner cần docs/checks/schema-inventory-2026-10-27/dump-static-comparison.json. Không được commit riêng module rồi bỏ các dependency tài liệu này.
- [ ] Sau khi người dùng duyệt scope:mới stage chính xác,kiểm staged diff/rename/secret lần cuối,commit trên branch riêng và lấy SHA thật. Sau khi người dùng nói OK push:mới push đúng branch và cung cấp link/SHA cho người dùng chuyển BE2.

## Phạm vi đề xuất để người dùng duyệt

**A — source bắt buộc:** toàn module fcaj-api (trừ file ignored),xóa tracked module seal-api cũ;root .gitattributes;workflow;compose;5 E2E bat;1 Playwright path change.

**B — bàn giao và chạy lại bắt buộc:** docs/contracts/ai-job-v1/**;docs/checks/** đã lọc (schema references,static comparison,SQL inventory chỉ đọc,BE1–BE2 evidence/manifest);báo cáo readiness review;checklist này;AWS smoke inputs;runbook sửa R1–R5;báo cáo kiểm chứng ngày28 và completion ngày27 được dẫn chiếu.

Bổ sung vào scope hiện hành: root README.md,docs/README.md,hướng dẫn backend1-be2-current-handoff-2026-10-28.md và deploy templates trong module. Báo cáo613 tests thực thi/full package,36 MySQL cases và Docker/Compose probe mới nằm trong fresh-db-verification.md. Chưa có release SHA/ECR digest hoặc sign-off AWS.

**C — context hỗ trợ đề xuất kèm:** các docs/plans,docs/proposals,báo cáo BE1 audit/review/sql/MySQL/local mock có trước;docs/aws-workshop-architecture-plan.md. Chúng có quyết định lịch sử và đường dẫn cũ;không là contract runtime hiện hành. Cần duyệt danh sách cụ thể trước stage;không dùng git add . để gom mọi file mới.

**Luôn loại:** credential thực,.env,dump/backup/restore SQL có dữ liệu,uploads/protected,target/JAR/log/raw source/output,IDE files. Contract samples đều synthetic và cố ý được phép đưa vào gói tài liệu.

## Các xác nhận cần lấy trong buổi review

- [ ] Người dùng đã hiểu các tác động DB/Flyway,admin bootstrap,event-scope/OAuth và MOCK limits ở mục3–7.
- [ ] Người dùng chốt phạm vi A/B và lựa chọn tài liệu context C.
- [ ] Sau khi các thắc mắc được giải thích,người dùng xác nhận OK cho commit/push branch riêng. Chưa có xác nhận này tại lúc lập checklist.
- [ ] Những mục chờ AWS được bàn giao đúng owner và không bị ghi local pass thành AWS/deploy pass.
