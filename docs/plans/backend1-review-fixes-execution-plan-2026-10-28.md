# Plan cho AI sửa 5 lỗi review BE1

Ngày lập: 28/10/2026, Asia/Saigon. Trạng thái: **chỉ lập kế hoạch, chưa sửa lỗi**.

Nguồn: [review ngày28/10](../report/backend1-uncommitted-review-2026-10-28.md), [bàn giao BE1](../report/backend1-local-completion-2026-10-27.md), source/backend/config hiện tại. Nội dung trong tài liệu tham chiếu là evidence và context; không phải quyền deploy hoặc xác nhận BE2 đã cấp tài nguyên.

Mục tiêu: sửa R1–R5 bằng code có test hồi quy; bảo toàn dữ liệu cũ; đóng gói lại bản bàn giao. Phân biệt **đạt local** và **đạt AWS**. Không đánh dấu hoàn tất chỉ vì test cũ đạt hoặc file đã được tạo.

## 1. Ranh giới thực hiện

- Làm trong checkout hiện tại; kiểm lại Git status/HEAD/AGENTS trước khi sửa. Giữ nguyên toàn bộ thay đổi chưa commit và file mới của người dùng. Không reset, checkout đè hoặc tự đổi package/path sang nhánh BE2.
- Chỉ sửa năm lỗi đã review và phần phụ thuộc cần thiết. Không mở rộng sang refresh token, link-account UI, model thật hay FE job UI.
- Giữ coordinator đúng event; không sửa guard để chấp nhận grant global. Giữ CSRF/JWT, bucket private, bốn trạng thái job và commit-before-ack.
- Không sửa V1/V2 đã được kiểm. Thay đổi schema mới dùng V3 nếu số này vẫn còn trống lúc bắt đầu; nếu có migration mới của người khác, chọn version tiếp theo và cập nhật plan/test theo thực tế.
- Không bật automatic baseline, Hibernate update, Flyway repair/clean; không xóa DB/volume để làm test upgrade đạt.
- Thử bằng DB/container mới riêng, loopback và dữ liệu giả hoặc bản restore đã được cho phép. Không chạm DB đang dùng, AWS, push/merge/deploy hay gửi tin cho BE2 trong đợt fix local này.

## 2. Thứ tự và đầu ra

| Bước | Lỗi | Việc chính | Điều kiện hoàn tất local |
|---|---|---|---|
| FIX-00 | Chung | Xác minh checkout, chạy lại hai probe, chuyển thành regression tests | Có test tái hiện lỗi trước sửa; giữ baseline/evidence |
| FIX-01 | R1/P1 | Optimistic version cho User/Track, V3, xử lý conflict và cleanup | Không còn DB reference tới object đã xóa khi transaction xen kẽ |
| FIX-02 | R2/P1 | Gate DB trước deploy; rehearsal DB cũ→target8.4 | Gate fail thì không thay/stop backend; target đã chuẩn bị mới được đi tiếp |
| FIX-03 | R3/P2 | Cấp coordinator demo đúng event | Account từ seeder dùng được API đúng event, vẫn bị chặn event khác |
| FIX-04 | R4/P2 | Thống nhất/kiểm giới hạn artifact ở producer và worker | Rubric quá giới hạn bị từ chối trước upload/job/outbox |
| FIX-05 | R5/P2 | Timeout bao phủ body và budget toàn snapshot trên Java21 | Response kẹt body bị hủy đúng hạn, không giữ tài nguyên vô hạn |
| FIX-06 | Chung | Full test, MySQL/restore rehearsal, package và bàn giao | Evidence mới, bảng R1–R5 và phần AWS pending rõ ràng |

Mặc định thực hiện tuần tự trong chat này. FIX-02 phụ thuộc schema cuối của FIX-01. FIX-03/04/05 có thể làm trong khi chờ BE2 phản hồi, không cần gọi AWS để sửa và test local.

## 3. FIX-00 — Preflight và regression baseline

1. Đọc lại review, current diff và các file sắp sửa; không coi HEAD cũ là bản chứa toàn bộ thay đổi local.
2. Kiểm tra Java21/Maven Wrapper và Docker lab. Không in `.env`, password, token hoặc nội dung dump.
3. Đọc hai probe dưới `back-end/src/seal-api/target/review/`: concurrency và job-boundary. Probe là ví dụ tái hiện, cần chuyển thành test chính thức; không đưa thư mục target vào Git.
4. Ghi kết quả baseline. Lần kiểm trước có591 backend tests và19 ca chạy lại trên MySQL8.4; đây không phải số test bắt buộc giữ nguyên sau khi thêm regression.

## 4. FIX-01 — Chống ghi đè file reference khi cập nhật đồng thời

**Thiết kế mặc định: optimistic locking trên cả User và Track.** Không chỉ lock riêng phương thức upload, vì request sửa profile/release đề đã đọc entity cũ vẫn có thể ghi ngược sau đó. Không coi riêng `@DynamicUpdate` là cách sửa đầy đủ.

Các file chính, tính từ `back-end/src/seal-api`:

- `src/main/java/com/seal/hackathon/entity/User.java`, `entity/Track.java`.
- `service/AuthService.java`, `service/TrackProblemService.java`, `service/TrackService.java` và các writer User/Track khác tìm được khi rà source.
- `storage/AvatarStorage.java`, `security/oauth2/CustomOAuth2UserService.java`, các exception handlers liên quan.
- Migration mới dự kiến `src/main/resources/db/migration/V3__add_user_track_row_versions.sql`.

Thực hiện:

1. Rà tất cả writer JPA/JDBC/native/JPQL của User/Track. Bulk update bỏ qua cơ chế version phải được loại bỏ hoặc có conditional version update/increment đúng; không chỉ sửa hai service file.
2. Thêm `@Version` cho thuộc tính `Long rowVersion`, map `row_version` trên cả hai entity. Không serialize trường này qua DTO công khai nếu flow hiện tại không cần. Entity mới để Hibernate khởi tạo version; cập nhật entity phải giữ version đọc từ DB.
3. V3 thêm hai cột `BIGINT NOT NULL DEFAULT 0` cho dữ liệu cũ. Không chạy blanket UPDATE lên các cột nghiệp vụ, không đổi password/roles/key/timestamp/dữ liệu chấm điểm.
4. REST conflict trả409 với mã an toàn `CONCURRENT_UPDATE`. Exception có thể xuất hiện khi flush/commit ngoài thân service: kiểm cả Spring/JPA wrappers và thứ tự advice; không chỉ try/catch quanh `save()`.
5. OAuth callback không được mặc định coi là đã được MVC advice bảo vệ. Flush mutation trong transaction để bắt optimistic conflict và chuyển thành lỗi OAuth an toàn `account_changed_retry`, thêm vào failure-handler allowlist. Không phát JWT cho thao tác identity thất bại, không tự retry một entity cũ hay tự link provider khác.
6. Giữ cleanup sau completion: commit mới dọn object trước đó; rollback chỉ dọn object mới của transaction thua; UNKNOWN không đoán để xóa. Không xóa object của transaction đã thắng. Không tự retry mutation file bằng dữ liệu cũ.

Test bắt buộc, dùng hai transaction/connection thực, điều phối bằng latch/barrier:

- Sửa tên profile xen kẽ thay avatar; thay avatar xen kẽ remove avatar; hai lần thay avatar.
- Thay đề xen kẽ release/retract; đổi tên track xen kẽ thay đề; remove đề xen kẽ thay đề.
- Transaction thua bị conflict/rollback; state và file của transaction thắng còn đọc được; object mới của transaction thua được cleanup; không dọn file live.
- Kiểm REST409 và OAuth failure an toàn. Không chỉ dùng mock repository hoặc gọi trực tiếp callback để coi race đã được sửa.
- Chạy regression trên H2 để feedback nhanh và MySQL8.4 để xác nhận transaction/schema thật.

**Cập nhật lab migration cùng bước này:**

- `Test/schema/DumpMigrationLab.java` hiện validate entity mới ở giai đoạn chỉ có V1 và hash toàn bộ `SELECT *`. Sau khi thêm version, hai phép kiểm đó sẽ không còn dùng nguyên trạng được.
- Giữ kiểm cấu trúc/fingerprint V1 trước baseline; chỉ Hibernate-validate mapping hiện tại sau khi đã áp tới V3. Giữ negative control thiếu cột/checksum, chuyển probe phù hợp tới schema mới.
- So dữ liệu trước/sau V3 theo **các cột gốc**: thêm `row_version=0` làm thay row hash nếu tiếp tục hash `SELECT *`. Kiểm hai cột version riêng; không bỏ kiểm bảo toàn627 dòng hoặc chấp nhận mọi hash khác biệt.
- Giữ nguyên V1/V2 và resource `db/v1-schema-sha256.txt`. Inventory/evidence mới ghi thành bản mới, không ghi đè evidence V1 lịch sử để che schema drift.

## 5. FIX-02 — Chặn deploy khi DB chưa sẵn sàng

Mặc định: bổ sung **gate chỉ đọc, fail closed** vào workflow hiện hữu; không xây pipeline deploy song song và không tự cutover DB thật.

File chính: `.github/workflows/deploy.yml`, runner DB trong `src/main/java/com/seal/hackathon/db/`, script operator/test và runbook. `docker-compose.yml` hiện dùng MySQL8.0/volume cũ; không tự đổi image lên8.4 hoặc đổi lower_case_table_names trên volume đó.

1. Thêm action như `verify-deploy` cho runner DB. `inspect` hiện chỉ liệt kê trạng thái, nên exit0 của inspect chưa đủ để cho deploy đi tiếp.
2. Gate kiểm đúng target catalog, MySQL8.4/case1, TLS cho remote, migration history/checksum và không còn migration pending/failed. Kiểm mapping/schema cần cho candidate, gồm cột version và các bảng job dùng JDBC. Không baseline/migrate/create schema/seed trong gate.
3. Chạy gate bằng **đúng candidate revision/artifact** dự định deploy. Chứng minh cách gọi CLI trong artifact/container đã đóng gói hoạt động; không nhầm `java -jar` sẽ tự chọn main migration.
4. Workflow không được stop/replace/remove backend hoặc chạy `compose up` trước khi gate đạt. Lỗi gate phải propagate thành failure, không bị `|| true` hay bước chỉ in cảnh báo nuốt mất.
5. Khi chưa có cấu hình target BE2, dừng deploy với lỗi rõ và giữ service hiện hữu; không fallback sang DB local hoặc khóa dev. Local có thể mock shell commands và dùng DB lab để kiểm logic này.
6. Runbook upgrade: backup đã thử restore → target8.4/case1 mới riêng → đối chiếu schema/data → baseline1 có kiểm → V2/V3 → kiểm candidate → cutover có lịch. Quiesce writer của bản cũ trong cửa sổ chuyển; tránh để bản cũ không biết version ghi đồng thời với bản mới.
7. Rollback deployment phải theo artifact/schema đã kiểm tương thích; không tự downgrade DDL, xóa cột version hay repair history. Khả năng rollback/cutover thật cần chốt với BE2 trước thực hiện.

Test/gate acceptance:

- DB có dữ liệu không history, chỉ baseline1, thiếu V3, checksum sai, sai catalog/TLS/engine/case: gate fail; spy/command recorder chứng minh không gọi replace/stop backend.
- Target lab đã migrate đủ và validate đúng: gate pass, rồi mới được gọi bước deploy giả lập. Gate pass/fail không làm thay dữ liệu/history.
- Rehearsal MySQL8.0 legacy→target8.4 riêng bằng backup/restore; không dùng chung volume giữa hai engine. Pin image/digest đã xác minh. Nếu không chạy được ca engine8.0 thật, báo rõ chưa kiểm, không ghi đã đạt.
- Chạy fresh V1→V2→V3, restore→baseline1→V2→V3 và restart no-op trên MySQL8.4; đối chiếu các cột gốc của627 dòng dump.

Phần code gate/test/runbook có thể hoàn tất local. RDS endpoint/identity, target deploy thực và nghiệm thu cutover vẫn là Pending BE2, không phải lý do bỏ qua gate hoặc tự xin quyền cloud khác.

## 6. FIX-03 — Coordinator demo đúng event

File chính: `config/seed/DemoScenario.java`, `DemoFixtures.java`, `DemoSeeder.java` nếu cần và tests seed/API.

1. Sau khi có demo event ID, cấp coordinator grant cho chính event đó. Có thể giữ grant global đang phục vụ flow demo cũ, nhưng grant global không được làm guard đề thi/job mới pass.
2. Helper cấp event grant phải idempotent, không tạo trùng khi được gọi lại. S1/S25/S3 đều phải nhất quán, không làm seeder chạy trong scenario NONE.
3. Lưu ý DemoSeeder đang skip khi demo account đã tồn tại. Viết hướng dẫn sửa **demo đã có** bằng thao tác quản trị cấp đúng event, có xác minh account/event; không reset DB và không tự backfill tất cả global coordinator vào mọi event thật. Nếu cần tool sửa demo riêng, mặc định dry-run và yêu cầu ID target rõ ràng.
4. Test lấy chính account/grants từ seeder, không tự tạo một coordinator đúng quyền khác để thay thế dữ liệu seed.

Acceptance: coordinator demo truy cập đúng event được; event khác và user chỉ có grant null vẫn403/hidden theo contract. S1 có thể chưa mở judging: tách test quyền đề thi và test job với round/timer hợp lệ do test setup chuẩn bị. S3 event đã hoàn tất vẫn tuân thủ event/judging guards, không bật lại business state chỉ để test pass.

## 7. FIX-04 — Chung giới hạn producer/worker

File chính: `aijob/JobSnapshotWriter.java`, `JobSnapshotReader.java`, `AwsMockWorkerMain.java`, `JobResultValidator.java`, error contract và tests.

1. Tạo một nơi khai báo các limit đang có: rubric/manifest/output262144 byte, source artifact8000000 byte, message32768 byte. Phân biệt source artifact JSON với giới hạn text đầu vào1MB của collector. Không tự nâng giới hạn để bỏ qua lỗi.
2. Serialize source/rubric thành byte UTF-8 thực tế và kiểm **trước bất kỳ upload nào**. Dùng chính bytes đó để hash/put, tránh kiểm String.length rồi gửi bytes khác.
3. Rubric quá giới hạn trả400 `RUBRIC_TOO_LARGE`; source dùng mã an toàn tương ứng. Không commit AiJob/outbox và không gửi SQS. Không truncate description, bỏ criteria hay thay đổi rubric chấm điểm chính thức một cách âm thầm.
4. Manifest cuối có version refs nhận từ storage, nên phải kiểm bytes sau khi ghép refs và trước put manifest/commit DB. Nếu lỗi ở bước này, không tạo job; quản lý object đã chuẩn bị theo chính sách orphan/cleanup có đối chiếu references, không hứa mọi lỗi manifest đều xảy ra trước mọi S3 write.
5. Worker vẫn kiểm lại bytes/hash/schema và dùng cùng constants. Cập nhật tài liệu contract; thay đổi protocol/limit khác phải được review liên nhóm, không tự gắn nhãn đã duyệt.

Tests: ngay dưới/đúng/trên ngưỡng; tiếng Việt/emoji; năm description60000 ký tự như probe. Với rubric vượt ngưỡng, assert API error rõ,0 PutObject,0 job,0 outbox,0 SendMessage. Với manifest vượt ngưỡng sau final refs, assert không commit job/outbox và cleanup/orphan policy đúng. Giữ checks fixture/hash và completion validation hiện có.

## 8. FIX-05 — Body timeout và budget snapshot thật trên Java21

File chính: `aijob/GitHubSourceReader.java`, helper HTTP có giới hạn nếu tách, error mapping và tests.

Thiết kế mặc định: fetch async với subscriber giới hạn bytes, completion chỉ khi nhận đủ body; timeout phải cancel request/subscription và giải phóng tài nguyên. Không chỉ thêm `orTimeout` lên một tác vụ vẫn chặn ở `InputStream.readNBytes`.

1. Giữ Java21 và fixed HTTPS GitHub host/no redirects; không thêm cấu hình arbitrary URL vào API để dễ test.
2. Một deadline monotonic45s áp cho commit resolution, tree và tất cả blobs. Mỗi fetch có tối đa min(15s, budget còn lại), gồm nhận headers và toàn body. Hết budget thì không gửi request tiếp.
3. Hủy/đóng luồng khi timeout, quá bytes, interrupt hoặc application shutdown; giữ interrupt flag. Không tạo executor/thread không giới hạn cho mỗi file/request.
4. Giới hạn trên actual received bytes; không chỉ tin Content-Length. Chuyển timeout thành504 `REPOSITORY_FETCH_TIMEOUT` an toàn qua error contract; không tạo job/outbox. Không tự retry POST bằng một vòng lặp vô hạn.
5. Có thể tách bounded HTTP fetcher để test bằng server loopback; production reader vẫn tự xây URI GitHub an toàn. Test constructor không được biến thành khả năng đổi endpoint tùy ý trong runtime.

Tests: headers về ngay nhưng body không hoàn tất; body truyền nhỏ giọt; body vượt giới hạn; client interrupt; budget tổng hết giữa nhiều fetch; request kế tiếp vẫn hoạt động sau timeout. Test chứng minh cancellation/resource release, không chỉ mock ném TimeoutException. Dùng timeout nhỏ cấu hình trong test, latch và cleanup finally; không chờ15/45s cho từng ca.

Nguồn hành vi Java: [Oracle Java team về phạm vi HTTP timeout](https://inside.java/2026/03/04/jdk-26-http-client/#timeout-update). Plan không đề xuất nâng Java26 để né kiểm soát timeout trong code.

## 9. FIX-06 — Kiểm chứng và bàn giao sau sửa

Thứ tự kiểm:

1. Regression tests từng lỗi; cả H2 và MySQL8.4 cho race và migrations.
2. Full backend suite, fixtures JSON, parser tests và demo preflight tests; các ca bị ảnh hưởng phải được giữ hoặc thay bằng test tương đương có giải thích.
3. Lab dữ liệu cũ/V1-baseline/V2/V3, checksum-negative/schema-negative/restart và bảo toàn các cột gốc. Không ghi đè dump/evidence lịch sử.
4. Gate fail/pass và rehearsal legacy upgrade; script syntax trên hệ điều hành tương ứng. Local pass không đồng nghĩa workflow remote đã chạy.
5. Package JAR thường, kiểm main class, V1/V2/V3/resources, không có H2 runtime ngoài Maven profile mock-demo, ghi SHA256 mới.
6. `git diff --check`; review diff cuối để tránh thay đổi ngoài scope, secret hoặc dữ liệu thật lọt vào Git.

Ghi report mới `docs/report/backend1-review-fixes-verification-2026-10-28.md` (đổi ngày theo ngày chạy thực tế), gồm từng R1–R5: file đổi, regression trước/sau, lệnh chạy, kết quả/count thật, migration/artifact checksum và giới hạn còn lại. Cập nhật bàn giao/checklist; không sửa báo cáo cũ để khiến lần test trước trông như đã bao phủ regression mới.

Nếu môi trường chặn một bài test, ghi rõ tên test, nguyên nhân và phần chưa kiểm. Không tăng timeout, skip test hay bỏ assert để làm suite xanh. Không mark một lỗi đã đóng nếu mới sửa một nửa đường gọi.

## 10. Checklist nghiệm thu cho AI và người dùng

- [ ] R1: optimistic version + migration + conflict mapping + tests race/cleanup đạt trên MySQL.
- [ ] R2: gate fail trước container replacement; upgrade/backup/restart rehearsal có evidence; AWS/cutover thật đánh dấu riêng.
- [ ] R3: demo coordinator có đúng event grant; demo cũ có cách sửa không mất dữ liệu; cross-event/global-only vẫn bị chặn.
- [ ] R4: producer/worker dùng chung byte limits; input quá lớn không sinh job treo; UTF-8/boundary tests đạt.
- [ ] R5: body stall bị cancel, budget toàn snapshot có hiệu lực và không rò thread/request.
- [ ] V1/V2/baseline fingerprint cũ không đổi; dữ liệu cũ được đối chiếu theo đúng cột gốc sau V3.
- [ ] Full suite/package đạt, có artifact checksum và review diff cuối.
- [ ] Không có commit/push/deploy/cloud mutation ngoài phạm vi được người dùng cấp sau đó.

## 11. Prompt giao cho AI thực hiện

> Đọc `docs/plans/backend1-review-fixes-execution-plan-2026-10-28.md` và review được dẫn chiếu, kiểm AGENTS/Git status rồi thực hiện FIX-00→FIX-06. Bảo toàn working tree hiện có và dữ liệu cũ; không sửa V1/V2, không làm yếu quyền event, không dùng automatic baseline hoặc reset DB. Bắt đầu bằng regression tái hiện R1, rồi thêm optimistic version/V3 và cập nhật lab bảo toàn dữ liệu. Triển khai gate deploy chỉ đọc và kiểm bằng môi trường riêng. Tiếp tục sửa R3/R4/R5, chạy các kiểm tra cần thiết và ghi evidence thật. Chủ động giải quyết lựa chọn kỹ thuật trong phạm vi plan; chỉ hỏi khi thiếu quyết định nghiệp vụ/đầu vào thực sự, vẫn tiếp tục phần độc lập. Chưa tự commit/push/deploy, gọi AWS hoặc sửa DB hiện hữu. Khi kết thúc báo từng lỗi đã/chưa đạt, các lệnh kiểm, migration/artifact hash và phần còn chờ BE2.
