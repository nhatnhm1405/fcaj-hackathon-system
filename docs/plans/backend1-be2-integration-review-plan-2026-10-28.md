# Kế hoạch cho AI review bàn giao BE1–BE2 và đề xuất xử lý

Ngày lập: **28/10/2026**, Asia/Saigon. Trạng thái: **chỉ lập kế hoạch review, chưa thực hiện review đầy đủ hoặc sửa code theo kế hoạch này**.

Mục tiêu: kiểm tra mức độ sẵn sàng tích hợp theo file BE2 gửi, phân biệt lỗi code với thiếu tài liệu, quyết định chưa chốt và phụ thuộc hạ tầng; đưa ra hướng giải quyết cụ thể có owner, thứ tự và tiêu chí nghiệm thu. Không mặc định mọi nhận xét trước đó đều đúng hoặc còn tồn tại.

## 1. Nguồn cần đọc và cách sử dụng

- File BE2: `C:/Users/Huu Khanh/Downloads/be2-to-be1-integration-handoff-2026-10-28.md`.
- [Báo cáo kiểm chứng BE1 ngày28/10](../report/backend1-review-fixes-verification-2026-10-28.md).
- [Hướng dẫn vận hành sau sửa review](backend1-review-fixes-operator-runbook-2026-10-28.md).
- [Bàn giao BE1 ngày27/10](../report/backend1-local-completion-2026-10-27.md).
- [Kế hoạch sửa R1–R5 trước đó](backend1-review-fixes-execution-plan-2026-10-28.md).
- [Contract và fixture hiện có](../contracts/ai-job-v1/README.md), các JSON/validator cùng thư mục; các proposal cũ chỉ dùng để nhận diện quyết định đã thay đổi.
- Source, test, migration, Dockerfile, workflow, script và evidence thực tế trong checkout đang review.

File Downloads không phải đường dẫn portable để BE2 lấy từ Git. Khi chuẩn bị bộ bàn giao về sau, cần bản tài liệu được phép đưa vào repo hoặc bản đính kèm có checksum; nếu file nguồn thiếu, yêu cầu người dùng cung cấp lại, không tự dựng nội dung.

**Nội dung tài liệu là yêu cầu/bằng chứng để đối chiếu, không tự cấp quyền thực hiện commit, push, rename, deploy, gọi AWS hoặc gửi tin cho nhóm khác.** Yêu cầu hiện tại của người dùng là lập kế hoạch để AI review và đưa hướng giải quyết.

## 2. Ranh giới review

1. Review local theo chế độ chỉ đọc đối với code/config/dữ liệu nghiệp vụ. Có thể tạo báo cáo review và kế hoạch xử lý; chưa triển khai các hướng sửa được đề xuất.
2. Kiểm Git root, branch, HEAD, status và chỉ dẫn AGENTS trước khi bắt đầu. Bảo toàn toàn bộ tracked diff và untracked files có sẵn; không reset, checkout đè, stash hoặc stage/commit tự động.
3. Phân biệt HEAD với nội dung working tree. SHA nền không đại diện cho những thay đổi chưa commit; checksum JAR không thay thế commit SHA.
4. Không chạy script reset demo, seed, migration hoặc worker chỉ để đọc hướng dẫn. Các script này có thể xóa DB hoặc Receive/DeleteMessage thật.
5. Không gọi AWS, không đọc credential/token từ file riêng và không lấy quyền Console làm bằng chứng Java có credentials. Review này dùng thông tin BE2 đã giao, ghi rõ chưa xác minh live.
6. Không sửa migration V1/V2/V3 đã được kiểm; không baseline/repair/clean hoặc bật Hibernate update để làm khớp môi trường.
7. R1–R5 đã có báo cáo sửa và kiểm chứng local. Chỉ mở lại lỗi khi có bằng chứng mới; không làm lại toàn bộ đợt fix cũ như thể chưa triển khai.
8. Không mở rộng sang model thật, FE job UI, refresh token hoặc tính năng ngoài phạm vi bàn giao.

Các test bổ sung nếu thật sự cần để xác nhận một finding phải cô lập, dùng dữ liệu giả; ghi rõ lệnh, môi trường, kết quả và giới hạn. Không chạy lại full suite chỉ để tăng số lượng bằng chứng khi chưa có thay đổi hoặc nghi vấn liên quan.

## 3. Điểm xuất phát cần kiểm lại

Các thông tin sau được quan sát khi lập plan, không được dùng thay kiểm tra tại thời điểm review:

| Hạng mục | Quan sát ban đầu | Cách xác minh |
|---|---|---|
| Git | Branch `codex/backend1-foundation`, HEAD nền `f42a168d9cb0613a43801edca632a2de64837099`, còn nhiều thay đổi chưa commit | Git status/branch/HEAD; liệt kê phạm vi thay đổi và file mới |
| Kiểm thử | Báo cáo mới và Surefire đã lưu ghi610 tests đạt; lượt MySQL riêng26 tests đạt | Đối chiếu XML/log, thời điểm, môi trường; phân biệt kết quả đã lưu với test mới chạy |
| Schema | BE1 có V3 thêm version cho User/Track; BE2 còn mô tả V1→V2 | Đọc migration, entity và deploy gate hiện tại |
| Package/path | Checkout BE1 dùng `back-end/src/seal-api`, `com.seal`; BE2 đề nghị thống nhất với `fcaj-api/com.fcaj` | Kiểm source/build/script/workflow thật; chưa thực hiện rename |
| Visibility | `AwsMockWorkerMain` override jobs300 giây; `JobDelivery` override results120 giây; BE2 chọn120 giây cho cả hai | Đọc toàn đường ReceiveMessage và test; không nhầm với lease outbox |
| Contract | Có fixture `fcaj.ai.source.mock.v1`; collector tạo `fcaj.ai.source.github.v1`; README có đoạn lịch sử chưa cập nhật | Đối chiếu writer/reader/validator/worker/API/test với từng mẫu |
| AWS theo BE2 | IAM role đã giao; có16 Outputs từ nguồn ngày27/10; chưa tạo EC2/RDS; prefix chưa xác nhận | Ghi nguồn/thời điểm; chưa coi là kiểm AWS live hoặc đã có credentials cho Java |
| Evidence bàn giao | Một phần tài liệu có trong docs; evidence mới nhất chi tiết còn dưới target bị ignore | Kiểm đường dẫn thực, tính portable, nội dung và dữ liệu cần loại khỏi gói |

## 4. Quy trình review và đầu ra

### RV-00 — Xác minh đầu vào và lập ma trận task

- Đọc đầy đủ file BE2 và các tài liệu hiện hành; lập danh sách từng yêu cầu, owner và điều kiện hoàn tất.
- Chụp trạng thái checkout bằng thông tin không nhạy cảm. Không coi file tồn tại là bằng chứng task đã đạt.
- Với mỗi task, chọn một trạng thái: **Đạt local**, **Có một phần**, **Chưa làm**, **Cần quyết định**, **Chờ bên ngoài**, hoặc **Không áp dụng có bằng chứng**.
- Ghi riêng bằng chứng trực tiếp từ source/test, kết quả do BE1 báo và thông tin BE2 cung cấp. Không trộn thành một trạng thái đã nghiệm thu tích hợp.

Đầu ra: ma trận BE1/BE2/FE/AI, kèm đường dẫn và dòng source được kiểm.

### RV-01 — Revision, phạm vi source và khả năng tái lập build

**Cần kiểm:**

- Những thay đổi nào thuộc BE1; những file nào là thay đổi người dùng có trước; file nào mới nhưng chưa track.
- Tài liệu, migrations, scripts, resources và tests cần thiết có nằm trong phạm vi bàn giao không. Kiểm .gitignore: có vô tình loại file cần giao hoặc để lọt secret/dump/raw source không.
- JAR hiện có chứa source/resources tương ứng tới đâu; kiểm timestamp/checksum/manifest và log build. Không gắn SHA nền vào artifact chưa commit như SHA phát hành.
- Rủi ro push `main` kích hoạt workflow và khả năng BE2 checkout đúng revision trên branch riêng.

**Hướng xử lý đề xuất:** lập danh sách file dự kiến đưa vào commit theo nhóm và danh sách loại trừ, bước build/test tại revision cuối, cách ghi SHA/tag image/digest sau khi có revision. Chỉ đưa kế hoạch commit/PR; chưa thực hiện.

**Tiêu chí:** reviewer biết chính xác cần giao những file nào và kiểm gì trước khi phát hành revision. Khi triển khai sau này, BE2 phải lấy/build được từ revision mới; không phụ thuộc target, file trên máy cá nhân hoặc source chưa commit.

### RV-02 — Package/path và deploy gate

**Cần kiểm:**

- Tìm mọi tham chiếu `seal-api`, `com.seal`, `fcaj-api`, `com.fcaj` trong source/test, Maven main class, Dockerfile, launcher, script, CI, compose và tài liệu chạy lại.
- Phân biệt tên hạ tầng `fcaj-*` với package Java: tên bucket/repository không tự buộc package phải đổi.
- Kiểm workflow/script hiện tại có yêu cầu cấu hình host, runtime URL và tên service nào; BE2 cần chuẩn bị gì trước khi dùng. Checkout này chưa có template infra BE2 thì ghi thiếu nguồn, không kết luận đã kiểm template.
- Gate phải chạy bằng đúng artifact dự định deploy trước thay service, kiểm MySQL8.4/case1/TLS/catalog/history/schema tới V3; không fallback sang compose MySQL8.0.

**Hướng xử lý đề xuất:** đưa hai phương án có trade-off: giữ package/path BE1 và điều chỉnh tích hợp, hoặc rename thống nhất toàn bộ. Đề xuất một phương án dựa trên churn và trạng thái merge, nhưng ghi rõ quyết định chung cần BE1+BE2 chốt trước thực hiện. Nếu rename, liệt kê toàn bộ nơi phải đổi và các kiểm build/test/CLI sau đổi.

**Tiêu chí:** có một mapping path/package/main/service duy nhất cho bản tích hợp; launcher không gọi class cũ; gate không thay backend khi target chưa sẵn sàng.

### RV-03 — Migration, evidence và tài liệu bàn giao

**Cần kiểm:**

- Luồng đúng hiện tại: DB trắng V1→V2→V3; DB restore được đối chiếu→baseline1 có chủ đích→V2→V3. Không phát hành hướng dẫn chỉ tới V2 khi entity đã yêu cầu row_version.
- Checksum migration và fingerprint lịch sử còn nguyên; đối chiếu evidence bảo toàn627 dòng theo cột gốc và kiểm riêng version=0.
- Cách phân biệt kết quả610/26 hiện hành với số591/19 trong tài liệu BE2, tránh cộng các lượt test chạy lại thành số test mới.
- Evidence mới có thể gửi cho BE2 ở đâu; những link nào chỉ đúng trên máy BE1 hoặc trỏ target bị Git ignore.

**Hướng xử lý đề xuất:** tạo bộ evidence đã lọc có thể đưa vào revision/gói bàn giao, manifest liệt kê file và checksum; giữ backup, SQL chứa dữ liệu, credential, source/output nhạy cảm ngoài gói. Thêm tài liệu hiện hành hoặc ghi chú thay thế, không sửa lịch sử để giả vờ kết quả cũ đã bao phủ ca mới.

**Tiêu chí:** BE2 mở được file/config/runner/evidence được dẫn chiếu và biết rõ bằng chứng nào local, bằng chứng nào còn chờ AWS.

### RV-04 — Contract thực tế cho FE/AI/worker

**Cần kiểm:**

- Ba API: POST tạo job; GET trạng thái; GET kết quả. Đối chiếu wrapper ApiResponse, trường JSON thực, HTTP status, safe error và quyền.
- Bốn state hiện hành: PENDING_DISPATCH, QUEUED, SUCCEEDED, FAILED. Tìm proposal cũ còn RUNNING/EXPIRED hoặc metadata không được phép trả FE.
- Source/manifest/rubric/request/completion/output: schemaVersion, trường bắt buộc/không cho phép, nullable, limits UTF-8, identity, hashes/sizes, versionId, timestamp, evidence và key mapping.
- Phân biệt source mock fixture với source GitHub của implementation; fixture hợp lệ cục bộ không chứng minh là message được phép đưa vào queue nghiệp vụ.
- Kiểm tài liệu có JSON Schema máy đọc được hay chỉ bảng mô tả/validator code; không tự coi một ví dụ JSON là schema đầy đủ.
- Kiểm các lỗi mới từ R4/R5 và CONCURRENT_UPDATE; POST retry hiện tạo lượt mới, chưa có HTTP idempotency key.

**Hướng xử lý đề xuất:** một bộ contract hiện hành có HTTP request/response mẫu, bảng lỗi, enum/DTO, schema hoặc đặc tả chặt tương đương, mẫu synthetic đúng phiên bản và lệnh validate. Mọi hash/size phải tính lại từ đúng bytes mẫu. Ghi trạng thái chờ duyệt FE/AI/BE2; không tự ghi đã được liên nhóm chấp thuận.

**Tiêu chí:** mẫu khớp code/validator; FE không nhận repo/requester/raw refs; backend và worker resolve cùng `{prefix}/{logicalKey}`; không âm thầm đổi protocol/limits để hợp thức hóa fixture cũ.

### RV-05 — SQS visibility và ngữ nghĩa retry

**Cần kiểm:**

- Tất cả ReceiveMessage overrides, queue defaults được cung cấp trong nguồn BE2, long poll, retention, DLQ và maxReceiveCount.
- Khoảng300 giây ở đâu là visibility của jobs, ở đâu là lease outbox; không đổi tất cả số300 bằng tìm/thay toàn cục.
- Worker gửi completion rồi ack jobs; consumer commit DB trước ack results; timeout và thời gian xử lý MOCK có phù hợp visibility đã chốt không.

**Hướng xử lý mặc định:** jobs/results receive overrides cùng120 giây theo bàn giao BE2; dùng hằng số/cấu hình chung nếu phù hợp để tránh drift, giữ lease có mục đích khác. BE2 cập nhật template/queue thực; review local không tự làm việc đó. Nếu cần gia hạn visibility cho model thật, tách quyết định cùng IAM action và benchmark.

**Tiêu chí:** test xác minh request jobs/results đều dùng120 giây, long poll20 giây; giữ duplicate/retry/commit-before-ack. Không dùng kết quả mock request để ghi đã cập nhật queue AWS.

### RV-06 — Smoke local và chuyển file cũ

**Cần kiểm:**

- Có quy trình từ một lệnh/hướng dẫn rõ để chuẩn bị DB8.4 riêng, tài khoản giả theo role, grant đúng event, assignment, submission/rubric và timer JUDGING RUNNING không.
- Có chạy được một vòng job tới MOCK result bằng cùng producer/worker/validator/delivery của implementation không, hay mới chỉ có profile LOCAL_MOCK khác đường xử lý.
- Cách chạy SUCCEEDED, FAILED có chủ đích, worker offline, duplicate, revoke/wrong-event và restart; evidence gắn được jobId/requestId.
- Các thao tác local có vô tình nối DB đang dùng hoặc queue thật không. Đọc runner trước khi đề nghị chạy.
- Có inventory tham chiếu file cũ, kiểm file thực, kích thước/hash, key mapping, quyền truy cập và đối soát sau chuyển không. Dump DB không chứa file vật lý.

**Hướng xử lý đề xuất:** ưu tiên test harness cô lập dùng các thành phần job production và storage/queue giả lập phù hợp; đánh dấu source synthetic. Nếu cần demo tương tác, ghi riêng cách khởi chạy và giới hạn. Không mở runtime endpoint tùy ý hoặc làm yếu GitHub host guard để dễ test.

Với file cũ, đề xuất inventory/dry-run trước. Nếu có file cần chuyển, thiết kế runner yêu cầu target rõ, upload bất biến, kiểm checksum và đối soát DB, có resume/rollback an toàn; không xóa file nguồn trước nghiệm thu. Nếu không có tham chiếu/file cần chuyển, ghi không áp dụng cho bộ dữ liệu đó kèm bằng chứng, không mặc định toàn hệ thống không có file.

**Tiêu chí:** BE2 chạy lại được bằng dữ liệu giả mà không cần secret của BE1, không reset DB dùng chung; phân biệt rõ LOCAL_MOCK, smoke production components local, transport AWS và smoke EC2/RDS.

### RV-07 — Phụ thuộc BE2 và điều kiện smoke AWS

**Cần kiểm từ tài liệu đã giao:**

- Không tiếp tục ghi IAM role/Outputs là chưa được giao. Ghi đúng trạng thái: đã có thông tin từ BE2 nhưng chưa xác minh bằng tiến trình Java và identity thực.
- Prefix cụ thể, quyền với inputs/rubrics/results/problems/avatars và credential provider nào còn thiếu. Không đoán prefix hoặc yêu cầu gửi access key/token/password vào chat.
- Người chạy worker, queue pair, thời gian test và ai kiểm soát consumer; không coi queue hiện hữu là được phép Receive/DeleteMessage ngay.
- EC2/RDS, catalog thực, CA/TLS, runtime/migration users, network, compose production, revision/image digest và API HTTPS còn ai phụ trách.

**Hướng xử lý đề xuất:** bảng owner/input/output và checklist điều kiện trước smoke. Chỉ yêu cầu thông tin còn thiếu; không bắt BE2 cấp lại IAM từ đầu. Chuẩn bị các bước kiểm S3/SQS trước EC2/RDS như một giai đoạn riêng, chưa tự chạy.

**Tiêu chí:** phân biệt phần BE1 có thể làm ngay với phần chờ BE2/FE/AI; không ghi local pass thành AWS pass hoặc fixture pass thành nghiệm thu model thật.

## 5. Q&A cần AI chuẩn bị sau khi có bằng chứng

Không hỏi lại điều đã được người dùng chốt. Trong đợt trước, người dùng chọn **hướng dẫn cấp quyền cho demo hiện có**, không thêm tool sửa/backfill demo.

Chỉ hỏi khi câu trả lời thực sự thay đổi hướng triển khai:

| Quyết định/đầu vào | Bên cần xác nhận | Cách hỏi và phần vẫn có thể tiếp tục |
|---|---|---|
| Package/path tích hợp cuối | Người dùng + BE2 | Trình bày phương án và tác động cụ thể sau khi rà tham chiếu; vẫn review contract/evidence/visibility |
| Prefix và credential provider cho Java | Người dùng + BE2 | Xin tên prefix và cách dùng role đã giao, không xin secret trong chat; vẫn chuẩn bị smoke local |
| Worker owner, queue pair và cửa sổ test | BE2 + AI + người dùng | Chốt trước thao tác SQS thật; vẫn hoàn thiện mẫu/validator/hướng dẫn |
| Phạm vi source được đưa vào revision | Người dùng | Đưa danh sách file cụ thể đã review và loại trừ dữ liệu nhạy cảm; không hỏi chung chung “có commit tất cả không” |
| Input/result contract còn thay đổi | FE + AI + BE1 | Nêu đúng trường/version/trade-off cần duyệt; không tự tuyên bố sign-off |

Với review chưa triển khai, các câu hỏi này có thể được ghi là quyết định còn mở trong báo cáo; không cần dừng toàn bộ review để chờ trả lời.

## 6. Cách ghi finding và đánh giá ưu tiên

Mỗi finding phải có: ID, loại, độ ưu tiên, bằng chứng source kèm dòng, điều kiện gây lỗi/không thể bàn giao, ảnh hưởng, hướng xử lý, owner, dependency và cách nghiệm thu.

- **Lỗi code:** hành vi thực tế trái quyết định đã chốt; cần test tái hiện thích hợp.
- **Thiếu đầu ra bàn giao:** file/source/evidence/hướng dẫn chưa đủ để bên nhận chạy lại.
- **Tài liệu lệch:** mô tả cũ hoặc số liệu/schema không còn đúng với bản hiện tại.
- **Cần quyết định:** có nhiều phương án hợp lệ, chưa đủ cơ sở chọn thay nhóm.
- **Phụ thuộc bên ngoài:** chưa có đầu vào/hạ tầng hoặc lịch phối hợp; không gán thành lỗi code BE1.

Chỉ dùng P1 khi có ảnh hưởng rõ tới dữ liệu/quyền hoặc làm hỏng đường tích hợp/deploy trong điều kiện cụ thể. Không gọi mọi mục chưa làm là P1. Finding không còn đúng phải được bác bỏ bằng bằng chứng, không giữ lại để đủ số lượng.

## 7. Deliverable sau review

Tạo **`docs/report/backend1-be2-integration-readiness-review-2026-10-28.md`**; đổi ngày nếu review thực hiện ngày khác. Báo cáo cần có:

1. Checkout/revision/working-tree và phạm vi đã đọc; tài nguyên chưa truy cập.
2. Kết luận có đủ điều kiện bàn giao source, smoke local, smoke S3/SQS và deploy EC2/RDS hay chưa — đánh giá riêng từng mức.
3. Ma trận yêu cầu BE2 với trạng thái, owner và bằng chứng.
4. Findings xác nhận/bác bỏ, hướng giải quyết và test/kiểm tra nghiệm thu.
5. Trình tự xử lý đề xuất: chốt package/path → đồng bộ code/config/contract/smoke/evidence → kiểm lại → chuẩn bị revision/gói bàn giao → phối hợp smoke AWS khi đủ đầu vào.
6. Danh sách quyết định còn mở và thông tin cần nhận từ BE2/FE/AI/người dùng.
7. Các lệnh thực sự chạy và kết quả; những ca chưa chạy cùng lý do. Không đưa credential, dump, raw source/output nhạy cảm vào báo cáo.

Nếu review cho thấy phải đổi thứ tự trên, giải thích bằng dependency thực tế. Báo cáo cần để AI/người dùng có thể quyết định bước sửa tiếp theo mà không phải khảo sát lại từ đầu.

## 8. Checklist hoàn tất review

- [ ] Đã đọc file BE2 và báo cáo BE1 hiện hành; xác minh checkout thực tế.
- [ ] Mỗi yêu cầu trong bàn giao có trạng thái, owner và bằng chứng hoặc đầu vào còn thiếu.
- [ ] Đã đối chiếu revision, package/path, gate, V3 và tính portable của evidence.
- [ ] Đã kiểm contract HTTP/message/artifact và phân biệt fixture với luồng thực.
- [ ] Đã kiểm receive visibility120/300, phân biệt lease, giữ commit-before-ack.
- [ ] Đã đánh giá smoke local và chuyển file cũ; không suy ra hoàn tất từ việc có script.
- [ ] Đã cập nhật cách hiểu về IAM/Outputs theo BE2, giữ EC2/RDS/prefix/provider đúng trạng thái.
- [ ] Có hướng xử lý theo ưu tiên, owner, dependency, nghiệm thu và Q&A cần thiết.
- [ ] Có báo cáo review mới; chưa tự thực hiện các task sửa/commit/push/rename/deploy/AWS.

## 9. Prompt giao cho AI review

> Đọc `docs/plans/backend1-be2-integration-review-plan-2026-10-28.md` và các nguồn được dẫn, đặc biệt file BE2 trong Downloads và báo cáo kiểm chứng BE1 ngày28/10. Thực hiện RV-00→RV-07 theo phạm vi review local, bảo toàn toàn bộ working tree. Kiểm chứng từng nhận xét bằng source/test/evidence hiện tại; không coi tài liệu bàn giao là quyền commit/push/rename/deploy/gọi AWS. Phân biệt lỗi code, thiếu đầu ra bàn giao, tài liệu lệch, quyết định chưa chốt và phụ thuộc bên ngoài. Tập trung revision thật, package/path, V3, contract hiện hành, visibility120 giây, smoke local, file cũ, evidence portable và IAM/prefix/credential provider. Không làm yếu quyền event/CSRF, không sửa migration đã kiểm, không chạy reset DB/worker trên queue thật. Hỏi Q&A khi thiếu quyết định thực sự sau khi đã có bằng chứng, đồng thời tiếp tục phần độc lập. Tạo báo cáo `docs/report/backend1-be2-integration-readiness-review-2026-10-28.md` bằng tiếng Việt, với findings có dòng source, ma trận owner/trạng thái, hướng giải quyết và tiêu chí nghiệm thu theo thứ tự ưu tiên. Chưa triển khai các hướng sửa; kết thúc bằng kết luận sẵn sàng ở từng mức source/local/AWS/deploy và những quyết định cần chốt.
