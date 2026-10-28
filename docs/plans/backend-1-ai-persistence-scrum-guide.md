# Kế hoạch thực hiện Backend 1 và Scrum Master

Ngày lập: 27/10/2026 — múi giờ Asia/Saigon.

**Đính chính cùng ngày:** nguồn schema thiết kế hiện tại là Java entity/Hibernate, không phải SQL legacy. App mặc định `ddl-auto=update`; Java seeders nạp dữ liệu; chưa có Flyway. Các bước baseline/migration trong hướng dẫn này chỉ là phương án nếu nhóm quyết định áp dụng, không phải yêu cầu sửa các SQL cũ. Khi thử RDS/MySQL8.4, ưu tiên cho Hibernate sinh schema ở DB lab mới và kiểm schema thật; validate chỉ dùng sau khi schema đã có. Xem kế hoạch domain/AWS và báo cáo 8.4 đã cập nhật để biết phạm vi hiện tại.

Đây là hướng dẫn thực hiện phần việc cá nhân theo yêu cầu của bạn. Các quyết định trong proposal được dùng làm cơ sở lập kế hoạch; tài liệu này không xác nhận nhóm đã duyệt contract, AWS đã triển khai hoặc kiểm thử đã đạt. Không thực hiện thay đổi backend/hạ tầng hay gửi thông báo cho nhóm trong lần lập kế hoạch này.

## 1. Hiểu đúng nhiệm vụ

Bạn có hai vai trò:

- **Backend 1:** làm cho backend lưu dữ liệu/file bền vững trên AWS và điều phối một yêu cầu AI từ lúc tạo đến lúc đọc kết quả, với quyền truy cập đúng nghiệp vụ.
- **Scrum Master theo phân công nhóm:** giúp mọi người chia nhỏ việc, cập nhật Project, phát hiện phụ thuộc và báo Lead sớm để gỡ vướng.

Kết quả cần chứng minh: một giám khảo hợp lệ tạo yêu cầu AI cho bài nộp; backend lưu job, gửi việc cho worker; worker xử lý rồi trả kết quả; backend lưu trạng thái/kết quả để người còn quyền đọc lại. Thay container backend vẫn đọc được dữ liệu/file.

Bạn phụ trách phần ứng dụng và tính đúng đắn dữ liệu. Backend 2 phụ trách hạ tầng, quyền AWS và vận chuyển message; AI phụ trách worker và suy luận model. Cần ghi rõ owner cho phần giao nhau để không bỏ sót.

## 2. Điểm xuất phát đã đọc từ repository

| Thành phần | Hiện trạng đọc từ source | Ý nghĩa với công việc |
|---|---|---|
| Backend | Java 21, Spring Boot, JPA, MySQL | Tiếp tục dùng cấu trúc Controller → Service → Repository hiện có |
| AI API | `AiController` có POST `/api/ai/submissions/{submissionId}/insights` | Đây là API đồng bộ; API job là phần bổ sung |
| AI service | `AiJudgeAssistantService` gọi Gemini và trả insight trực tiếp | Chuyển luồng mới sang tạo job và xử lý nền; thống nhất với FE cách chuyển API |
| Quyền AI | Judge kiểm assignment/cửa sổ chấm; nhánh coordinator hiện đọc submission theo ID | Cần bổ sung kiểm coordinator đúng event theo policy được Lead xác nhận |
| File đề thi | `TrackProblemService` lưu filesystem ở `protected/problems` | Đây là luồng mẫu thích hợp để chuyển sang S3 mà giữ nghiệp vụ |
| DB config | Datasource local, cấu hình tự cập nhật schema; có giá trị mặc định nhạy cảm | Chuẩn bị cấu hình AWS riêng, secret ngoài source và migration có kiểm soát |
| AWS integration | Chưa thấy AiJob/outbox, S3Client/SqsClient hay dependency Flyway trong phạm vi source/pom đã rà | Các phần này cần thiết kế và triển khai, không coi là đã có |
| Routing | Caddy đang proxy frontend và backend, có cả domain gốc và API subdomain | Khác hướng frontend CloudFront trong proposal; BE2/FE chốt deployment đích trước nghiệm thu auth |

Các tài liệu cũ trong `docs/plans` và `docs/proposals` là đầu vào hữu ích. Mốc 21/10 và kết quả khảo sát ở đó không phải deadline hay kết quả kiểm thử mới của kế hoạch này. Lần này chỉ đọc source/tài liệu; chưa chạy API, test, migration hay AWS lab.

## 3. Chốt ranh giới trách nhiệm trước khi code

| Việc | Bạn — Backend 1 | Bên phối hợp / đầu ra cần nhận |
|---|---|---|
| RDS | Schema, migration, cấu hình datasource, repository, kiểm dữ liệu | BE2: endpoint, network/SG, DB version, cách nhận secret và quyền truy cập |
| S3 | Adapter lưu/đọc, metadata DB, kiểm quyền user, chuyển luồng file | BE2: bucket/prefix, versioning/lifecycle, runtime role và policy |
| Snapshot input | Resolve commit SHA, lấy source có giới hạn, lưu snapshot/manifest/rubric | AI: quy tắc chọn file/ngôn ngữ/token; Lead: phạm vi repo cho phép |
| AiJob và REST API | Entity/state, transaction, DTO, quyền, API và idempotency | FE: polling/hiển thị/lỗi; Lead: chính sách tạo và xem |
| SQS | Ghi outbox, nghiệp vụ phát job/nhận result, validate và cập nhật DB | BE2: queue/DLQ/IAM và adapter transport; thống nhất ai code dispatcher/consumer |
| Worker/model | Cung cấp input contract, đọc và kiểm output | AI: worker poll jobs, chạy model, ghi S3, gửi progress/completion |
| Scrum | Board, cập nhật hằng ngày, dependency/blocker, điều phối review | Mỗi người cập nhật task của mình; Lead quyết định ưu tiên/phạm vi/nguồn lực |

Không cần chờ đủ AWS mới bắt đầu. Bạn có thể làm migration trên DB local riêng, mock storage/queue và worker giả theo cùng contract. Nghiệm thu AWS phải dùng RDS/S3/SQS thật sau đó.

## 4. Chặng 1 — Làm một luồng lưu–đọc RDS/S3 có kiểm quyền

**Nên chọn file đề thi của một track** vì backend đã có upload, release/retract và download. Một luồng này đồng thời chứng minh được metadata trong RDS, bytes trong S3 và quyền theo nghiệp vụ.

1. Đọc `TrackProblemController`, `TrackProblemService`, entity `Track` và các guard membership/event đang có. Liệt kê điều kiện quyền trước khi đổi storage.
2. Cùng BE2 lập danh sách cấu hình cần bàn giao: Region, RDS endpoint/database, cách cấp secret, bucket/prefix, IAM role. Không đưa mật khẩu hoặc access key vào issue/source/log.
3. Dùng entity trên commit tích hợp làm nguồn schema; cho Hibernate tạo/cập nhật DB lab riêng, export/diff DB thật khi cần giữ dữ liệu. Không nạp SQL legacy; baseline migration chỉ thêm nếu nhóm chọn công cụ mới.
4. Đề xuất interface `ObjectStorage` có thao tác lưu/đọc; triển khai S3 adapter. Dùng dependency/BOM tương thích với project tại thời điểm implement.
5. RDS giữ liên kết track/event và metadata như object key/version, tên file, MIME, size/checksum, release state. S3 giữ nội dung file. Không lưu bytes file vào bảng nghiệp vụ.
6. Upload: kiểm quyền và giới hạn file → ghi object mới với key do server sinh → transaction cập nhật metadata. Khi DB lỗi, giữ tham chiếu cũ hợp lệ và có cách dọn object mới mồ côi. Object cũ chỉ dọn sau commit theo retention.
7. Download: xác thực → kiểm event/track, release và membership/assignment phù hợp → tự tra key trong DB → đọc S3 và stream qua API. Client không được chọn S3 key bất kỳ.
8. Backend trả download với tên file an toàn, loại nội dung phù hợp và `Cache-Control: private, no-store`. Bucket application private; v1 theo proposal chưa dùng presigned URL cho người dùng.
9. Dùng cấu hình AWS với kết nối RDS có TLS và chính sách tạo/cập nhật schema được chốt. Chỉ chuyển `ddl-auto=validate` khi schema đã được tạo đúng; không bê nguyên datasource local sang RDS.

**Tiêu chí xong:** coordinator đúng quyền upload/release được; thành viên team APPROVED đúng track tải được sau release; người sai team/event hoặc trước release bị chặn. Sau retract, request tải mới bị chặn. Restart/thay container vẫn tải đúng file và checksum. Không cần tạo endpoint demo riêng nếu luồng hiện có đáp ứng.

## 5. Chặng 2 — Chốt contract trước khi tích hợp model

Contract là thỏa thuận về API, message và dữ liệu để FE/backend/worker có thể làm riêng nhưng ghép được. Cần có mẫu JSON hợp lệ, mẫu lỗi và người xác nhận từng phần.

### 5.1. API dự kiến

Các endpoint dưới đây kế thừa bản handoff trong repository, vẫn cần nhóm xác nhận:

| API | Bạn cần thực hiện | Phản hồi đề xuất |
|---|---|---|
| POST `/api/ai/submissions/{id}/jobs` | Kiểm quyền; chuẩn bị snapshot; transaction tạo job/outbox | `202 Accepted`, jobId, status và URL đọc trạng thái |
| GET `/api/ai/jobs/{jobId}` | Kiểm quyền hiện tại rồi trả status DTO | `200 OK`, trạng thái, timestamps, lỗi an toàn nếu có |
| GET `/api/ai/jobs/{jobId}/result` | Kiểm quyền, trạng thái và result tồn tại; lọc DTO | `200 OK` khi sẵn sàng |

Chốt thêm mã lỗi: chưa đăng nhập; mất quyền/không tồn tại; input sai; result chưa sẵn sàng; job failed/expired; dữ liệu đã hết retention. Có thể dùng `409` khi result chưa sẵn sàng, nhưng FE/backend phải dùng chung một quy ước.

Requester lấy từ phiên đăng nhập, không nhận userId đáng tin cậy từ request body. DTO dành cho judge không trả raw S3 key, secret, stack trace hay danh tính không cần thiết. Không đưa toàn bộ entity ra API.

### 5.2. Nội dung buổi chốt contract

| Nhóm | Cần thống nhất |
|---|---|
| Nhận diện | schemaVersion, jobId, submissionId/version, event của cuộc thi, message/event ID dùng chống trùng, full commit SHA |
| Input | Reference S3 gồm key/version/hash, rubricVersion và rubric object version/hash, modelConfigVersion, processingVersion |
| Thời gian | createdAt, deadlineAt, timezone/định dạng; lịch worker online; timeout hiển thị phía FE |
| Progress | Có STARTED để lên RUNNING hay không; event đến trễ/đảo thứ tự xử lý thế nào |
| Completion | SUCCEEDED/FAILED, reference result S3 hoặc errorCode/retryable, versions/SHA tương ứng |
| Result | Nhận xét theo tiêu chí, evidence path/dòng, limitations, model thực dùng, file đã đọc/bỏ qua; thiếu bằng chứng biểu diễn thế nào |
| Quyền | Ai tạo, ai xem, assignment bị thu hồi, hết giờ chấm, coordinator đúng event, có được tạo lại không |
| Vận hành | Retry, visibility, retention, DLQ, kết quả đến muộn, cách replay và ai xử lý |

Lưu ý tên `eventId`: cần phân biệt ID cuộc thi với ID một sự kiện/message xử lý. Có thể đặt `hackathonEventId` và `messageId` trong bản nháp rồi thống nhất tên chính thức.

Về quyền, dùng baseline proposal để review: tạo job theo assignment và cửa sổ chấm; người yêu cầu còn quyền hoặc coordinator đúng event được đọc. Hết giờ chấm không tự đồng nghĩa mất quyền xem lịch sử; quy tắc này cần Lead xác nhận. Không tự thêm ngoại lệ coordinator tạo ngoài giờ.

**Tiêu chí xong:** AI, BE2, FE và Lead xác nhận phần liên quan bằng phản hồi/biên bản thật; có fixtures input/STARTED/success/failure dùng chung. Chưa có phản hồi thì ghi Pending kèm owner và hạn phản hồi.

## 6. Chặng 3 — Thiết kế AiJob và xử lý nền

### 6.1. Mô hình dữ liệu đề xuất

Không cần dùng đúng tên class dưới đây; mục tiêu là trách nhiệm rõ ràng.

| Thành phần | Dữ liệu/trách nhiệm chính |
|---|---|
| `AiJob` | jobId; submission/event/requester; input/submission version và SHA; references snapshot/rubric; model/processing config versions; status; timestamps/deadline; result reference; safe error; revision dùng kiểm soát cập nhật đồng thời |
| `OutboxEvent` | ID sự kiện, jobId/type/schemaVersion, payload metadata, trạng thái phát, số lần thử và lần thử tiếp theo |
| Dấu nhận event đã xử lý | ID duy nhất để chống xử lý lặp; có thể là bảng inbox hoặc cơ chế tương đương phối hợp với cập nhật state nguyên tử |

Tạo index theo truy vấn thực tế. Nếu hỗ trợ `Idempotency-Key` cho POST, ràng buộc unique trong DB theo requester/phạm vi và kiểm payload tương ứng để thao tác bấm lại không tạo job mới ngoài ý muốn. Đây là quyết định cần chốt; nó khác với chống message trùng từ SQS.

Trạng thái:

```text
QUEUED ──STARTED──> RUNNING ──> SUCCEEDED / FAILED
QUEUED ──completion─────────> SUCCEEDED / FAILED
QUEUED hoặc RUNNING ──quá deadline──> EXPIRED
```

Completion có thể đến trước STARTED. SUCCEEDED/FAILED/EXPIRED là trạng thái kết thúc; không hạ về RUNNING. Giữ mỗi lần đánh giá thành job riêng, không để result job cũ ghi đè kết quả job mới.

### 6.2. Trình tự thực thi

1. **Backend nhận POST:** kiểm quyền và input; resolve repo tới full SHA; lấy snapshot có giới hạn dung lượng/path/redirect; upload snapshot/rubric và manifest/hash lên S3. Không chạy script/build của repo.
2. **Tạo job:** kiểm lại điều kiện có thể thay đổi trong lúc chuẩn bị input; khi snapshot sẵn sàng, transaction ngắn ghi `AiJob(QUEUED)` và outbox cùng nhau. Không giữ transaction DB xuyên suốt tải repo/gọi S3. Trả 202 sau commit; nếu bước chuẩn bị lỗi thì trả lỗi rõ ràng.
3. **Dispatcher:** lấy outbox đã commit, gửi metadata tới SQS jobs rồi đánh dấu đã gửi; có retry/backoff. Gửi thành công nhưng chưa đánh dấu vẫn có thể gửi lại.
4. **AI worker:** chủ động poll queue; kiểm version/hash/deadline; gửi STARTED nếu contract yêu cầu; tải input S3; chạy model local. Worker không có quyền DB.
5. **AI worker hoàn tất:** ghi result S3 → gửi completion lên SQS results → xóa job message. Khi crash/lỗi gửi, retry theo contract và tái sử dụng output durable nếu phù hợp.
6. **Backend consumer:** kiểm schema/provenance/job/SHA/versions, reference phải thuộc prefix/job hợp lệ; kiểm schema nội dung, evidence path/dòng theo snapshot. Output không hợp lệ không được đánh dấu SUCCEEDED.
7. **Lưu kết quả:** transaction chống trùng và cập nhật state/result reference nguyên tử → commit → xóa result message. Không chỉ check tồn tại rồi insert vì có thể chạy đồng thời.
8. **FE đọc lại:** poll status, dừng khi terminal và đọc result khi sẵn sàng; mỗi API kiểm lại quyền. Kết quả AI là tham khảo, không ghi vào Score chính thức.

Snapshot được tạo trước transaction có thể thành object mồ côi; cần cleanup policy. RDS transaction không tự rollback được thao tác S3/SQS.

Nếu chuẩn bị snapshot quá chậm cho HTTP request, phải chốt một bước chuẩn bị bất đồng bộ và trạng thái/API phù hợp; không trả một job QUEUED runnable khi input chưa có chỉ để né timeout. Đo repo nhỏ trước khi mở rộng thiết kế.

### 6.3. Giải thích ba khái niệm cần nắm

- **Outbox:** ghi job và “việc cần gửi” cùng transaction DB. Nếu queue lỗi, backend còn bản ghi để gửi lại; tránh lưu job xong nhưng làm mất thông báo cho worker. [AWS: Transactional outbox](https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html).
- **Idempotency:** cùng message được xử lý lại vẫn chỉ có một tác dụng nghiệp vụ. Cần vì SQS Standard có thể phát trùng. [AWS: At-least-once delivery](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues-at-least-once-delivery.html).
- **Visibility timeout:** khi nhận message, worker có một khoảng thời gian xử lý trước khi message có thể được nhận lại; job dài cần gia hạn. Nó khác deadline nghiệp vụ và retention. [AWS: Visibility timeout](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html).

**Tiêu chí xong:** round trip đi hết bằng worker giả trước, rồi thay model thật mà không đổi contract; restart không mất job/result; duplicate không tạo tác dụng phụ; mọi request đọc đều kiểm quyền.

## 7. Chặng 4 — Tình huống lỗi và bằng chứng nghiệm thu

| Tình huống | Kết quả cần chứng minh |
|---|---|
| Người không được phân công tạo job | Bị chặn, không tạo job/outbox |
| Judge khác hoặc coordinator sai event đọc job/result | Bị chặn theo quy ước lỗi đã chốt |
| Assignment bị thu hồi sau khi tạo | Request đọc tiếp theo bị chặn |
| Worker offline | Job chờ; API nghiệp vụ vẫn hoạt động khi backend online |
| Backend chết sau khi DB commit, trước gửi SQS | Outbox gửi lại được sau khi backend hoạt động |
| Worker crash sau khi nhận job | Message có thể nhận lại; kết quả không nhân đôi tác dụng |
| Result trùng hoặc STARTED đến sau SUCCEEDED | Không tạo bản ghi/tác dụng phụ trùng; state không lùi |
| SHA/version/hash sai hoặc evidence sai | Không chốt thành công; có log và xử lý theo loại lỗi |
| DB commit result lỗi | Chưa xóa result message để còn retry |
| Quá deadline / message vào DLQ | Backend có cơ chế đối soát; không giả định DLQ tự đổi status DB |
| Result đến sau EXPIRED | Không mở lại job; giữ thông tin đối soát theo policy |
| Restart/redeploy backend | Metadata RDS và file/result S3 còn đọc được |

Chọn test unit cho quyền và state transitions; integration cho transaction/idempotency; kiểm end-to-end cho AWS và worker. Không chỉ kiểm happy path bằng Swagger. Lưu jobId, commit/version, log đã lọc và phản hồi API làm bằng chứng.

Phối hợp BE2/AI viết runbook ngắn: tìm job theo ID, kiểm outbox/queue/DLQ, phân loại lỗi, ai được replay và cách tránh tạo điểm chính thức. Restore DB lab phải kiểm cả reference tới S3 còn tồn tại/đúng version.

## 8. Backlog đề xuất để đưa lên Project

Ước lượng là ngày công tập trung của bạn, chưa gồm thời gian chờ review/hạ tầng; điều chỉnh theo năng lực và lịch thực tế. Mỗi dòng có thể là issue cha, tách checklist thành đầu việc nửa ngày đến một ngày.

| ID | Việc / đầu ra review được | Phụ thuộc | Ước lượng |
|---|---|---|---|
| BE1-01 | Rà schema/quyền hiện tại, ghi quyết định dữ liệu cần giữ và commit tích hợp | Lead, BE2 | 0,5 ngày |
| BE1-02 | Contract API/message/result, fixtures, policy matrix được xác nhận | AI, BE2, FE, Lead | 0,5–1 ngày, chưa gồm chờ phản hồi |
| BE1-03 | Migration baseline và AiJob/outbox; kiểm trên DB lab | BE1-01; phần schema phụ thuộc BE1-02 | 1 ngày |
| BE1-04 | S3 adapter, luồng đề thi upload/release/download và kiểm sai quyền | BE2 bàn giao S3/IAM/RDS cho AWS lab | 1–1,5 ngày |
| BE1-05 | Snapshot SHA/manifest với giới hạn input và cấu hình rubric | BE1-02/04, AI | 1–1,5 ngày |
| BE1-06 | API tạo/status/result, access service và DTO | BE1-02/03; mock snapshot trước | 1–1,5 ngày |
| BE1-07 | Outbox dispatcher + result consumer, mock worker round trip | BE1-02/03/06; BE2 transport | 1–1,5 ngày |
| BE1-08 | AWS round trip model thật, kiểm lỗi/quyền/restart; runbook | BE1-04/05/07, BE2, AI, FE | 1–2 ngày |

Gợi ý milestone theo lịch proposal, cần Lead xác nhận lại vì hiện đã là 27/10:

- **27/10–01/11:** chốt contract/owner; schema/migration lab; luồng mẫu RDS/S3; API/mock theo mức tiến độ thực tế.
- **02–08/11:** ghép queue + worker giả rồi model thật; FE poll; xử lý duplicate/offline/quyền.
- **09–15/11:** demo xuyên suốt, thử restart/restore và bàn giao bằng chứng.

Nếu sức chứa không đủ, báo Lead điều chỉnh mốc/phạm vi trước khi nhận cam kết. Lambda/SES/fine-tuning không phải điều kiện hoàn thành phần core này.

## 9. Vai trò Scrum Master: làm gì mỗi ngày

Đây là quy trình nhẹ phù hợp phân công của nhóm; Lead vẫn quyết định ưu tiên và thay đổi phạm vi.

### Thiết lập Project

Gợi ý cột: Backlog → Ready → In progress → Review/Test → Done; có thể dùng nhãn Blocked thay vì thêm cột. Mỗi task có owner, reviewer, đầu ra, tiêu chí xong, hạn dự kiến và dependency.

Ví dụ task đủ nhỏ: “Viết GET status, kiểm requester/mất assignment, có test và phản hồi JSON mẫu”. Task “Làm AI backend” quá lớn để biết đang vướng ở đâu. Mỗi người ưu tiên hoàn tất một việc chính trước khi kéo thêm việc mới; đây là quy ước nhóm cần thống nhất.

### Nhịp hằng ngày

Chọn một giờ cập nhật chung, ví dụ 09:00 hoặc trước buổi họp. Mỗi người cập nhật trực tiếp trên task:

```text
Đã xong: [kết quả + link PR/test/demo]
Tiếp theo: [một đầu ra cụ thể hôm nay]
Blocker: [vướng gì, từ khi nào, cần ai làm gì]
Dự kiến hoàn thành: [mốc mới nếu thay đổi]
```

Bạn dành khoảng 15–20 phút mỗi ngày để rà board, nhắc cập nhật, nối đúng người xử lý phụ thuộc và đặt thời điểm kiểm tra lại. Nếu cần trao đổi kỹ thuật dài, tách buổi riêng cho người liên quan. Không biến daily thành buổi kiểm tra bài hay chỉ hỏi phần trăm hoàn thành.

### Khi nào báo Lead

Đề xuất quy tắc để nhóm thống nhất:

- Báo ngay khi dự báo trễ milestone hoặc blocker chặn luồng demo chung; không đợi đến hạn mới nói.
- Blocker nhỏ chưa có owner/cách gỡ sau một ngày làm việc thì chuyển Lead; phụ thuộc khẩn không cần đợi một ngày.
- Bất đồng contract/quyền/phạm vi mà các bên không tự chốt được phải có người quyết định và hạn quyết định.

Nội dung báo cáo cần đủ: task, tác động, bên phụ thuộc, các cách đã thử, phương án đề xuất và hạn cần quyết định. Ví dụ:

> BE1-07 đang chờ BE2 cấp quyền đọc result queue từ ngày …; việc này chặn demo round trip ngày …. Phần xử lý DB đã kiểm bằng mock. Đề nghị chốt người cập nhật policy trước …; nếu chưa có thì giữ demo mock và điều chỉnh mốc AWS với Lead.

Mỗi tuần cùng nhóm xem lại việc hoàn thành và blocker lặp lại; chọn một cải tiến nhỏ cho tuần tiếp theo. Không tự ghi Done chỉ vì code xong nếu chưa có review và bằng chứng theo acceptance.

## 10. Bạn nên bắt đầu trong buổi làm việc kế tiếp

1. Dành 30–45 phút đọc các file ở mục 11; ghi lại một sơ đồ Controller → Service → DB/file cho AI và đề thi.
2. Soạn danh sách quyết định còn thiếu, ưu tiên quyền job/result, message fixtures, owner dispatcher/consumer và thông tin RDS/S3.
3. Dành một buổi trao đổi ngắn với AI/BE2, có FE/Lead xác nhận phần liên quan; ghi kết luận và điểm Pending vào handoff sẵn có.
4. Đưa BE1-01…08 lên Project, chia task nhỏ và gắn dependency; nhờ mỗi thành viên cập nhật tương tự.
5. Bắt đầu luồng đề thi với storage adapter và migration lab; đồng thời dùng fixture/mock để làm API job. Ghép AWS thật khi BE2 bàn giao môi trường.

Bạn đã hoàn thành nhiệm vụ khi có: contract được xác nhận, luồng RDS/S3 có quyền, ba API job, round trip qua queue/worker, bằng chứng xử lý lỗi và board được cập nhật đều. Tài liệu thiết kế riêng chưa đủ để nghiệm thu phần triển khai.

## 11. File nên đọc theo thứ tự

Đường dẫn dưới đây tính từ root repository:

1. `back-end/src/seal-api/src/main/java/com/seal/hackathon/controller/AiController.java`
2. `back-end/src/seal-api/src/main/java/com/seal/hackathon/service/AiJudgeAssistantService.java`
3. `back-end/src/seal-api/src/main/java/com/seal/hackathon/repository/SubmissionRepository.java` và `service/RoundTimerService.java`
4. `back-end/src/seal-api/src/main/java/com/seal/hackathon/controller/TrackProblemController.java`
5. `back-end/src/seal-api/src/main/java/com/seal/hackathon/service/TrackProblemService.java`
6. `back-end/src/seal-api/src/main/java/com/seal/hackathon/entity/Track.java`, `Submission.java`, `TeamEventEntry.java`
7. `back-end/src/seal-api/src/main/java/com/seal/hackathon/config/SecurityConfig.java`
8. `back-end/src/seal-api/src/main/resources/application.properties`, `pom.xml`, `docker-compose.yml`, `Caddyfile`
9. `docs/proposals/ai-job-review-handoff.md`, `data-platform-proposal-detailed.md`, `docs/plans/data-storage-ai-job-checklist.md`

Tài liệu định hướng được đối chiếu: `E:/FA26/AWS/aws-proposal-v1.md` và `E:/FA26/AWS/aws-architecture-drawing-plan.md`, cùng ngày 26/10/2026. Các mốc/quy tắc còn đề xuất phải được nhóm xác nhận trước cam kết triển khai.
