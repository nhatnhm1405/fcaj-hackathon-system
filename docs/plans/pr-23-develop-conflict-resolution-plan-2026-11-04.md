# PR #23 — Scan conflict và kế hoạch tích hợp develop, giữ SEAL

Ngày 04/11/2026, Asia/Saigon. Phạm vi hiện tại: scan và lên kế hoạch cùng người dùng; chưa resolve, commit, push hoặc merge PR.

**Cập nhật thực thi:** người dùng đã yêu cầu bắt đầu sửa. Resolution và kiểm chứng được ghi riêng trong [báo cáo tích hợp](../report/pr-23-develop-conflict-resolution-2026-11-04.md); các mô tả chưa sửa bên dưới là checkpoint của lượt scan ban đầu.

## 1. PR và revision đã khảo sát

- PR: [#23 — Feature/seal be1 handoff 2026 11 03](https://github.com/nhatnhm1405/fcaj-hackathon-system/pull/23), head `feature/seal-be1-handoff-2026-11-03`, base `develop`.
- Feature HEAD: `4049d107a78235be9e95efa9185e2b034b07c9ec`.
- `origin/develop` đã fetch: `18d1783a9d428466c166c00c1595bc1f45b59388`.
- Merge base: `f42a168d9cb0613a43801edca632a2de64837099`.
- Sau merge base, develop có 14 commit riêng; feature có 3 commit riêng. Feature có 262 file thay đổi theo `git diff develop...feature`; con số này không phải số conflict.
- GitHub API lúc tìm PR trả base SHA `b980a2c…`; scan dùng revision develop đã fetch ở trên. Phải fetch và kiểm lại head/base khi bắt đầu sửa, vì target có thể tiếp tục thay đổi.

Working tree có sẵn thay đổi ở `docs/report/be1-tra-loi-be2-truoc-deploy-2026-11-03.md`. Giữ nguyên thay đổi này; chưa đưa nó vào phạm vi resolution hay commit. Hash trước/sau scan được lưu trong `scan-summary.json` dưới thư mục evidence local.

## 2. Quyết định đã được người dùng xác nhận

**Giữ SEAL và xử lý việc đổi tên từ develop trong chính PR này.**

Đích tích hợp đề xuất:

| Thành phần | Tên sau resolution |
| --- | --- |
| Backend module | `back-end/src/seal-api` |
| Java production/test namespace | `com.seal.hackathon` |
| Maven group / application | `com.seal` / `seal-hackathon` |
| Frontend module | `front-end/src/seal-web` |
| Compose app services | `seal-backend`, `seal-frontend` |
| Compose container/network names | SEAL, đồng bộ với service và Caddy |
| Java migration / worker entry points | `com.seal.hackathon.db.DatabaseMigrationMain`, `com.seal.hackathon.aijob.AwsMockWorkerMain` |

Đưa module/package/service về SEAL không yêu cầu phục hồi mọi tên component/CSS cũ. Các tên trung tính đang hoạt động như `AppFooter`, `brandLogo` có thể giữ, tránh sửa giao diện ngoài nhu cầu tích hợp. Metadata npm nếu chuẩn hóa thành `seal-web` phải đồng bộ `package.json` và lockfile, giữ nguyên dependency versions.

Không thay tên các resource đã được cấp hoặc định danh persisted chỉ vì có chữ FCAJ: DB/catalog, bucket, queues, IAM, domains/cookies, browser storage keys, các biến môi trường đang được code đọc và `fcaj.ai.*` trong contract phải được đối chiếu riêng. Không thay chuỗi hàng loạt trên toàn repository.

## 3. Kết quả mô phỏng merge

Đã chạy `git merge-tree --write-tree --messages --name-only origin/develop origin/feature/seal-be1-handoff-2026-11-03`. Exit 1 xác nhận conflict. Lệnh tạo Git objects phục vụ phân tích, không đổi index/working tree, branch hoặc tạo commit.

- **115 đường dẫn conflict**: **111 `file location`**, **4 `content`**.
- 111 file mới trên feature nằm trong các thư mục mà develop đã đổi SEAL → FCAJ. Git gợi ý chuyển chúng sang FCAJ; đây là quyết định về vị trí file, không phải 111 đoạn logic cạnh tranh.
- 4 conflict nội dung được mô tả ở bảng dưới.
- Phân nhóm 115 đường dẫn: 46 production Java, 31 test Java, 19 script/test utilities, 13 resources/migrations, 5 deploy templates, 1 config khác.
- Tree mô phỏng: `1a35cbd1af3b3511249c493dc1dafb758e34790e`; tree này còn marker và namespace sai, không phải candidate build.

Evidence local, được Git ignore:

- `back-end/src/seal-api/target/merge-conflict-scan-2026-11-04/merge-tree.txt`: đủ danh sách 115 đường dẫn và messages.
- `back-end/src/seal-api/target/merge-conflict-scan-2026-11-04/scan-summary.json`: SHA, danh sách conflict, quyết định và hash file local có sẵn.

Nguyên nhân chính là commit `3c0de1e` trên develop đổi tên kỹ thuật trên 505 file; feature tiếp tục bổ sung backend dưới SEAL. Các commit AI mới trên develop cần được giữ cùng lịch sử merge.

## 4. Cách xử lý từng conflict nội dung

Các đường dẫn ở bảng là đích SEAL. Trong output merge-tree, Git đang hiển thị phiên bản tương ứng dưới `fcaj-api/com/fcaj`.

| File | Xung đột thực tế | Hướng resolution |
| --- | --- | --- |
| `config/DataSeeder.java` | Block imports FCAJ cũ còn User/admin dependencies đối lập với imports SEAL sau khi tách bootstrap | Giữ logic reference seeding của feature; imports SEAL đúng dependencies thực dùng. Giữ `BootstrapAdminSeeder` opt-in, không phục hồi tài khoản bootstrap mặc định từ develop. |
| `config/seed/DemoFixtures.java` | `adminActor()` dùng `DataSeeder.ADMIN_EMAIL` đối lập với email bootstrap có cấu hình và kiểm grant | Giữ email cấu hình, normalize và kiểm SYSTEM_ADMIN từ feature; loại tham chiếu constant cũ. Giữ đúng thứ tự bootstrap/demo. |
| `service/TrackProblemService.java` | Imports FCAJ cũ đối lập với SEAL và thêm `UserRepository`/`ObjectStorage` | Giữ storage adapter, kiểm quyền, transaction/concurrency của feature; imports thống nhất SEAL. Không quay lại file storage cũ chỉ để hết conflict. |
| `resources/application.properties` | Tên application FCAJ đối lập với SEAL và `spring.profiles.default=local` | Dùng `seal-hackathon`, giữ tách local/test/aws và các policy Flyway/storage/jobs/bootstrap của feature. Không đưa `.env`/datasource local trở lại common config. |

Không dùng chọn toàn bộ ours/theirs cho repository: một phía chứa backend handoff, phía kia chứa worker mới và rename đã lan sang cấu hình.

## 5. Các vấn đề Git không đánh dấu conflict

1. **Namespace và JAR entry point:** tree tự merge có 73 Java file còn khai báo `package com.seal` dù đã bị đặt dưới `com/fcaj`; 84 file backend chứa tham chiếu `com.seal`. Main application lại là `com.fcaj`, nhưng POM ép `mainClass=com.seal…`. Chỉ xóa marker chưa tạo được một ứng dụng nhất quán. Với quyết định giữ SEAL, phải đưa cả file cũ từ develop về đường dẫn/namespace SEAL; kiểm cả imports, reflection `Class.forName`, entity scan, tests và launchers.
2. **CI/deploy:** workflow tự merge giữ working directories FCAJ nhưng lệnh deploy lại gọi `back-end/src/seal-api/Test/deploy-verified.sh`. Cần đồng bộ CI, `.gitattributes`, Docker build context, Compose, Caddy, E2E BAT scripts và command examples về SEAL. Giữ executable bit `100755` của `mvnw`, LF cho shell scripts/migrations và deploy gate initialize → verify → replace. Không thay gate bằng root dev stack.
3. **Frontend:** develop đã chuyển cả module sang `fcaj-web`, còn feature có thay đổi E2E tại `seal-web`. Khi giữ SEAL, đưa module về một đường dẫn duy nhất và giữ thay đổi E2E của feature. Việc này làm diff PR lớn hơn phần backend, cần review rename riêng với thay đổi hành vi.
4. **Python worker:** develop có `ai-worker/` cùng grader, cleaner, fetcher, schemas và tests. Tree mô phỏng giữ nguyên tree worker `89bf676a257b0bfb1e5d1c5e6cca936fb657792e`. Khi resolve theo SEAL, cần sửa có chủ đích các reference path trong `contracts/sample_input.json`, `contracts/sample_output.json`, `src/mock_worker.py` và các nơi liên quan. Evidence mẫu về `application.properties` đang mô tả config cũ; cần đối chiếu lại file, số dòng, snippet và nhận xét, không chỉ đổi tên thư mục.
5. **Hai contract AI chưa tương thích:** Python dùng `job_id`, `rubrics`, `SUCCESS`, `criteria_results` và CLI file input/output; Java dùng envelope `fcaj.ai.message.v1`, refs/hash, `SUCCEEDED`, output/provenance/insight. Giữ cả hai module; smoke transport của backend vẫn dùng Java MOCK cùng candidate. Adapter/model integration là task riêng, không tự ghi AI output thành Score/RoundResult trong resolution này.
6. **Bằng chứng release cũ:** verifier 03/11 kiểm source SHA cố định và yêu cầu code hiện tại khớp source đó. Sau tích hợp upstream, nó có thể fail phần so sánh current code dù bytes lịch sử vẫn đúng. Giữ manifests/evidence lịch sử; ghi checkpoint tích hợp mới, không sửa checksum để biến kết quả cũ thành bằng chứng cho code mới.

## 6. Thứ tự thực hiện khi chuyển sang sửa

### Bước 1 — Chốt snapshot và bảo toàn thay đổi có sẵn

- Fetch lại head/base; nếu SHA đổi thì mô phỏng và cập nhật inventory trước.
- Giữ nguyên file báo cáo đang sửa; chuẩn bị bản sao/patch và kiểm hash trước khi đổi branch hoặc merge.
- Làm trên nhánh tích hợp từ feature, ví dụ `codex/pr-23-keep-seal`. Merge `origin/develop` vào nhánh đó để cập nhật ancestry PR; chưa merge vào develop/main hay push trong bước chuẩn bị.

### Bước 2 — Resolve và thống nhất tên SEAL

- Merge với `--no-commit`; giải quyết các file-location conflict về SEAL. Đưa các module và namespace được develop đổi sang FCAJ về SEAL, không để tồn tại song song hai bản backend/frontend.
- Resolve 4 content conflicts theo bảng mục 4.
- Rà toàn bộ file tự merge: POM, main/migration/worker launchers, security/JWT, auth, storage, seeders, profiles, entity versions và tests. Giữ logic bổ sung của feature và mọi thay đổi upstream độc lập.
- Đồng bộ workflow/Compose/Caddy/E2E và metadata cần thiết. Giữ nguyên volume names/persisted identities và resource config ngoài phạm vi tên kỹ thuật source.

### Bước 3 — Giữ và hiệu chỉnh worker upstream

- Giữ `ai-worker` và các chức năng/test từ develop; không checkout nguyên feature đè phần này.
- Hiệu chỉnh paths/evidence mẫu theo source sau merge, đồng bộ sample với mock output; không thay protocol chỉ để Python trông giống Java.
- Rà test evidence hiện tại: mới kiểm file tồn tại và số dòng không vượt file, chưa chứng minh snippet/nhận xét khớp nội dung. Review trực tiếp các evidence bị ảnh hưởng.

### Bước 4 — Kiểm tra trước commit

| Nhóm | Kiểm chứng cần chạy |
| --- | --- |
| Git/static | Không còn unmerged entries hoặc marker; chỉ một cặp module SEAL; không còn tham chiếu runtime tới module/package/service FCAJ; `git diff --check`; kiểm LF/mode của wrapper/scripts. Những định danh contract/resource được giữ phải có lý do rõ ràng. |
| Backend | Java21, Maven profile test, package đầy đủ; component smoke có API/cookie/CSRF/session và AI jobs; kiểm JAR Start-Class, migration main và worker main đều SEAL. Không build Maven song song vào cùng target. |
| DB | MySQL8.4 suite riêng, Flyway V1→V2→V3/fingerprint/runtime-DML/file-concurrency. So bytes SQL và fingerprint với feature trước merge; không sửa migrations đã đóng băng. Không dùng RDS/DB đang làm việc để thử resolution. |
| Scripts/contracts | Deployment gate tests, dump-parser tests, deploy-template validation, demo-preflight parser tests, current contract/legacy fixture validators; PowerShell/Bash syntax theo các file bị đổi. Không gọi worker queue thật hoặc demo reset chỉ để kiểm syntax. |
| Frontend | Vitest và production build từ `front-end/src/seal-web`; đối chiếu launcher/E2E path và discovery, không reset dataset đang dùng. |
| Python AI | Unit tests hiện có từ `ai-worker`; kiểm sample input/output/mock parity và evidence path/line/snippet. Dùng mock, không gọi model thật. |
| Compose | Parse root dev stack và hai AWS templates bằng placeholder/env giả phù hợp; đối chiếu service/upstream và gate; không start/replace stack thật. |

Không lấy số test cũ 624/36 làm kết quả mới; ghi số thực tế trên candidate tích hợp. Các suite trùng không cộng thành tổng độc lập.

### Bước 5 — Giao bản resolution để review

- Review diff so với cả feature cũ và develop, tách rõ: cập nhật upstream, đảo tên kỹ thuật về SEAL, giữ logic BE1, hiệu chỉnh Python fixtures và tài liệu hiện hành.
- Cập nhật ghi chú tên kỹ thuật hiện hành; đánh dấu tài liệu FCAJ rename là checkpoint lịch sử nếu cần. Không viết lại tất cả logs cũ.
- Tạo bằng chứng tích hợp mới: head/base, inventory bảo toàn, checksum migrations/contracts, kết quả tests, JAR identity và giới hạn kiểm chứng.
- Khi có yêu cầu triển khai tiếp: commit resolution, cập nhật head branch của PR #23 bằng thao tác không force-push, rồi kiểm mergeability mới. Merge PR vào develop và deploy là các bước sau; phạm vi hiện tại chỉ scan/plan.

## 7. Điều kiện kết thúc resolution

- PR #23 cập nhật ancestry develop và không còn conflict với target SHA đã kiểm.
- Backend/frontend/runtime scripts thống nhất SEAL; các resource/contract identities ngoài phạm vi vẫn tương thích.
- Feature handoff, JWT/session fix, Flyway/storage/job delivery được bảo toàn; Python worker upstream còn đủ chức năng và tests.
- Các kiểm tra phù hợp ở trên đạt trên source sau resolution; file local của người dùng được bảo toàn.
- Có revision/evidence mới cho candidate tích hợp; không đồng nhất local pass với AWS/browser/model acceptance.

## 8. Trạng thái cuối lượt scan

Đã xác định PR, fetch branches, đọc lịch sử/diff, mô phỏng merge, phân loại conflict và rà các lỗi namespace/workflow/contracts/evidence ngoài marker. Đã nhận quyết định giữ SEAL. Chỉ thêm plan này và evidence local ignored; chưa sửa runtime/tests, chưa tạo merge state/commit/push/deploy và chưa chạy test ứng dụng.
