# Báo cáo kiểm chứng sửa lỗi review BE1 — 28/10/2026

Phạm vi: R1–R5 trong báo cáo review và kế hoạch thực hiện. Công việc được làm trên checkout hiện có tại HEAD `f42a168d9cb0613a43801edca632a2de64837099`, giữ nguyên các thay đổi chưa commit đã có từ trước. HEAD này không đại diện cho phiên bản phát hành của artifact sau sửa. Không thực hiện commit, push, deploy, gọi AWS hoặc ghi vào DB nghiệp vụ đang dùng. Mọi thao tác ghi DB đều diễn ra trên Docker lab mới chỉ mở loopback hoặc DB H2 dành cho test.

## Kết quả

| Lỗi review | Thay đổi | Đã kiểm chứng local |
|---|---|---|
| R1 | Thêm `@Version Long rowVersion` cho User/Track và migration V3 chỉ bổ sung cột; REST trả 409 `CONCURRENT_UPDATE`, kể cả lỗi được bọc khi commit và lỗi qua advice của AI controller; OAuth flush trước khi tạo principal, ánh xạ lỗi an toàn `account_changed_retry` và thêm vào danh sách cho phép của failure handler | Sáu ca ghi xen kẽ có điều phối trên H2 và MySQL 8.4: sửa profile/đổi avatar, đổi/xóa avatar, hai lần đổi avatar, release/thay đề, đổi tên track/thay đề, xóa/thay đề. Transaction dùng dữ liệu cũ bị từ chối; file của transaction thắng vẫn đọc được; object mới của transaction thua được dọn. Test REST conflict trực tiếp, conflict được bọc và lỗi OAuth đều đạt. |
| R2 | Thêm CLI chỉ đọc `verify-deploy`, kiểm đầy đủ migration/history, fingerprint cấu trúc V3 đã duyệt, ORM mapping và các cột bảng job chỉ dùng JDBC; nối script kiểm image dự định deploy vào workflow hiện có trước compose up | Backup từ MySQL 8.0.46 được restore sang server 8.4.13 riêng; kiểm baseline/V2/V3 và restart đạt. Gate từ chối DB chưa có history, chỉ có baseline, chưa tới V3, thiếu cột version, sai checksum và engine 8.0 thật. Gate chạy từ JAR đã đóng gói đạt trên DB restore V3. Bộ ghi nhận lệnh chứng minh gate thất bại không gọi thay thế/dừng/xóa backend; gate thành công mới được thay backend. Deploy/cutover thật còn chờ BE2. |
| R3 | DemoScenario cấp quyền coordinator đúng event vừa tạo; helper cấp quyền trong DemoFixtures gọi lại không tạo trùng và đồng bộ quan hệ trong bộ nhớ | Tài khoản thật từ seeder S1/S25/S3 đọc được đề thi có bảo vệ của event mình và bị chặn ở event khác. Coordinator từ seed vượt qua kiểm tra quyền tạo job khi test chuẩn bị timer đang mở ở S1/S25; S3 đã hoàn tất vẫn bị khóa chấm. Các test chặn grant global-only và truy cập chéo event hiện có vẫn được giữ. |
| R4 | Producer/worker/consumer dùng chung `JobLimits`; kiểm byte UTF-8 của source/rubric trước upload; kiểm manifest/request cuối trước commit DB; giới hạn output/message | Năm description dài 60000 ký tự tái hiện lỗi cũ và nay trả 400 `RUBRIC_TOO_LARGE`, không put object, không tạo job/outbox, không gửi message. Các ca rubric ngay dưới/đúng/trên ngưỡng byte với tiếng Việt/emoji đều đạt. Manifest cuối quá lớn bị từ chối sau bước chuẩn bị input; các object đã chuẩn bị được giữ để đối soát object mồ côi dựa trên tham chiếu DB. |
| R5 | HTTP bất đồng bộ với body subscriber giới hạn dung lượng; timeout bao phủ toàn body, hủy cả subscription và future; mỗi lần fetch bị giới hạn bởi ngân sách 45 giây dùng đồng hồ monotonic còn lại và tối đa 15 giây/lần; HTTP client được dừng cùng ứng dụng | Server loopback trả header rồi ngừng truyền/truyền nhỏ giọt: trả 504 `REPOSITORY_FETCH_TIMEOUT` đúng hạn test; request tiếp theo vẫn hoạt động. Body nhận thực tế vượt giới hạn bị chặn. Khi interrupt, giữ interrupt flag và hủy subscription. Hết ngân sách snapshot thì không gửi HTTP request tiếp theo. |

Đã rà các writer User/Track trong production để tìm native SQL, JPQL và bulk update. Không tìm thấy bulk update User/Track bỏ qua version; các mutation JPA chịu cơ chế version của entity. Giữ nguyên V1/V2 và resource fingerprint V1 lịch sử. Trạng thái completion UNKNOWN giữ lại object theo chính sách cleanup hiện có. Không tự retry thao tác file/entity bằng dữ liệu cũ.

Test race dùng Hibernate session, connection và transaction thật, điều phối bằng latch. Test sử dụng phần đăng ký cleanup của production và chủ động gọi hoàn tất đồng bộ Spring sau các transaction Hibernate đó; chưa phải một lượt end-to-end đồng thời qua browser/OAuth. Test ánh xạ conflict OAuth giả lập lỗi tại repository. Nghiệm thu với provider/browser thật là phần riêng.

## Bằng chứng trước và sau sửa

Trước khi biên dịch lại entity đã sửa, đã chạy lại hai probe gốc trong `target/review` trên bản compiled baseline hiện có. Cả bốn cờ avatar/problem bị ghi ngược hoặc mất object đều là true. Probe giới hạn tạo rubric 300485 byte, worker từ chối bằng `INVALID_INPUT_REFERENCE`, số completion bằng 0. Các test hồi quy chính thức hiện đã bao phủ những lỗi này; source/object của probe vẫn nằm trong target được Git ignore.

Full backend suite cuối: **610 tests, 0 failures, 0 errors, 0 skipped**. Lệnh `mvnw.cmd -o -q -Dspring.profiles.active=test package` đạt với Java 21.0.10. Trên Windows cần tùy chọn JVM test dưới đây vì đường dẫn socket tạm mặc định có dấu cách làm Java NIO khởi tạo loopback thất bại; không nới assertion hoặc timeout:

```powershell
'-DargLine=-Djdk.net.unixdomain.tmpdir=E:/FA26/AWS/fcaj-hackathon-system/back-end/src/seal-api/target/socket-tmp'
```

Các kiểm tra bổ sung:

- `Test/run-ai-mysql-tests.ps1`: **26 tests đạt**, gồm 20 test tích hợp job và 6 test concurrency, trên MySQL 8.4.13 với V1/V2/V3 thật; S3/SQS/GitHub được mock. Log mới nhất: `target/fcaj-ai-lab-dc21483415be-test.log`.
- `node --test Test/deploy-verified.test.mjs`: 2 test đạt. Test ghi nhận thứ tự lệnh shell bằng lệnh giả lập; không chạy compose deploy lên server thật.
- `node --test Test/schema/inspect-dump.test.mjs`: 7 test đạt.
- `node docs/contracts/ai-job-v1/validate-fixtures.mjs` chạy từ gốc repository: 7 kiểm tra fixture đạt.
- `Test/run-demo-preflight-tests.ps1`: 19 kiểm tra đạt.
- Kiểm cú pháp bằng PowerShell parser, kiểm cú pháp Bash cho runner deploy/MySQL và `git diff --check` đều đạt.

Log full suite: `back-end/src/seal-api/target/review-fixes-release-check.log`. Kết quả được đọc từ Surefire XML sau khi package thành công. Các lượt chạy lại trên MySQL bao phủ cùng những test đó, không được cộng thành số test độc lập mới.

## Backup, nâng cấp và artifact

Lab nghiệm thu cuối: `fcaj-migration-lab-eb66ed843c67`, server nguồn `fcaj-migration-lab-eb66ed843c67-legacy80`; cả hai đã dừng và được giữ lại. Bằng chứng chi tiết đã lọc thông tin nhạy cảm và log CLI từ JAR nằm tại `back-end/src/seal-api/target/fcaj-migration-lab-eb66ed843c67/`. Ca giả lập lách kiểm tra bằng baseline3 cũng bị từ chối trong lượt này. Backup thô và các câu lệnh restore sinh ra vẫn nằm trong thư mục target được Git ignore. Không sửa dump nguồn.

Chạy lại từ module backend:

```powershell
./Test/schema/run-dump-lab.ps1 -DumpPath 'E:/FA26/AWS/seal_hackathon.bak' -JdkHome 'C:/Program Files/Java/jdk-21.0.10' -Legacy80Image 'mysql@sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b'
```

Image 8.4: `mysql@sha256:76f0358884cb92d9a086d24e5671c38048d3fef40f2588703d73eb55392d6b69`. Các server dùng storage riêng; không dùng chung volume giữa các phiên bản engine. Server 8.0 nguồn trước tiên restore dump 31 bảng/627 dòng đã duyệt; backup bằng mysqldump thật từ server này được restore sang 8.4 và khớp fingerprint. Các luồng DB trắng V1→V2→V3 và DB restore→baseline1→V2→V3, restart không thay đổi, cùng kiểm schema/index/FK/unique/auto-increment đều đạt. Cả 627 dòng giữ nguyên hash tính trên các cột gốc; cột version mới được kiểm riêng là0. Kiểm 55 FK ở dữ liệu nguồn và 61 FK sau migration đều đạt.

| Hạng mục | SHA-256 |
|---|---|
| Dump gốc | `ab6279c0ad77be4405ca1505c1da8b7db2a1a7c6b9e052db5b672790e1f23802` |
| V1 giữ nguyên | `91a3b9b0cb4d41ce5027fa850200848126bdfe57ba0ceef97defa1ff7928ea9d` |
| V2 giữ nguyên | `4e40d1f4370c2324e90cd5d1c7e7396c2ce068e2331682421fb5f1e0a3aa4ea7` |
| V3 | `8e67ee1a45bdbd23724cc96bfd26df81f10e0a8cb84be955d37a998ee820ce49` |
| Fingerprint cấu trúc V3 tham chiếu | `e3cb4adfa088a91be970d9c0b6fb7b85bda427803b526d7be72a288c1e0f343d` |
| JAR thường | `339e579534107299cddc57a08c9c3efc5c8a224cfbd0f3e650a99fed1c63d9b7` |

JAR: `back-end/src/seal-api/target/hackathon-0.0.1-SNAPSHOT.jar`. Manifest có Start-Class là `com.seal.hackathon.HackathonApplication`; chứa V1/V2/V3 và resource tham chiếu V3, không có JAR H2 runtime. PropertiesLauncher đã chọn thành công `DatabaseMigrationMain` từ chính fat JAR này để chạy ca gate DB hợp lệ, không khởi động Spring/seeder. Chưa xác nhận đã kiểm bước build Docker image hoặc thực thi trên server deploy thật.

## Bàn giao và phần còn phụ thuộc bên ngoài

- [x] R1: version/migration/conflict và test hồi quy cleanup trên H2, MySQL.
- [x] R2: gate chỉ đọc local, thứ tự lệnh shell, CLI trong JAR và rehearsal backup/nâng cấp/restart.
- [x] R3: seeder cấp quyền đúng event và hướng dẫn vận hành cho demo hiện có.
- [x] R4: giới hạn artifact chung producer/worker và API từ chối input quá lớn mà không tạo dữ liệu job.
- [x] R5: hủy body khi timeout và test ngân sách toàn snapshot.
- [x] Full suite, package thường, checksum migration và bảo toàn dữ liệu trên các cột gốc.
- [ ] BE2 xác nhận revision/package/path deploy chính xác, compose/cấu hình production, identity/TLS/IAM của target và khả năng cutover/rollback; chạy CI/deploy từ xa và smoke test AWS/browser.

Q&A: người dùng chọn hướng dẫn thao tác cho tài khoản demo đã có. Không thêm công cụ sửa/backfill. Chưa nhận câu trả lời hoặc cấu hình target deploy, nên workflow dừng khi thiếu cấu hình bắt buộc trên host. Không dùng compose cũ để bỏ qua bước này.

Xem [hướng dẫn vận hành](../plans/backend1-review-fixes-operator-runbook-2026-10-28.md) để biết cấu hình deploy, CLI chạy đúng artifact, cutover bảo toàn dữ liệu và cấp quyền demo cũ. Không sửa lại bằng chứng tháng7/tháng10 hoặc kết quả 591 test trước đây để khiến chúng trông như đã bao phủ các ca mới. Việc duyệt liên nhóm về source/protocol/worker thật và nghiệm thu AWS vẫn còn chờ.
