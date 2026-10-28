# AI job v1 — contract và fixtures để review

**Đọc [contract triển khai ngày28/10](CURRENT.md) trước khi tích hợp.** Các phần bên dưới giữ nguyên bối cảnh lúc soạn fixture ngày27/10; các câu “chưa implement/chưa bàn giao” không mô tả trạng thái hiện tại. IAM role và Outputs đã được BE2 giao theo tài liệu ngày28/10; prefix/provider và AWS smoke vẫn chờ xác nhận. Fixture cũ dùng mock-source.json, khác key source.json của producer/worker hiện tại.

Cập nhật triển khai: checkout đã có job API/business DB, S3/SQS adapters và worker mock CLI; xem [bàn giao hiện hành](../../report/backend1-local-completion-2026-10-27.md). Mô tả "chưa implement" bên dưới ghi thời điểm soạn fixtures. Bộ fixture vẫn chỉ để review; chưa có xác nhận liên nhóm hay nghiệm thu AWS.

Ngày: 27/10/2026, Asia/Saigon. Source đối chiếu: `f42a168d9cb0613a43801edca632a2de64837099`.

Người dùng đã chọn **PENDING_DISPATCH / QUEUED / SUCCEEDED / FAILED**, mock trước và nhóm review trước tích hợp AWS. Người dùng xác nhận **chưa có môi trường hoặc contract do BE2/AI bàn giao**. Các JSON này là bản đề xuất để review, chưa phải API đã chạy hoặc giao thức đã được FE/AI/BE2 duyệt.

Cập nhật thực thi: đã có [local mock ba API](../../../back-end/src/fcaj-api/src/main/java/com/seal/hackathon/mockdemo/README.md)
trong profile opt-in, có private H2 persistence và quyền event/requester. Response
ghi rõ LOCAL_MOCK; input revision/result là synthetic. Luồng này không phải
implementation contract S3/SQS/worker thật dưới đây. Xem [report/tests](../../report/local-mock-job-completion-2026-10-27.md).

## 1. Bộ mẫu và kiểm tra

| File | Vai trò |
|---|---|
| [job.json](job.json) | Body message gửi jobs queue khi input đã sẵn sàng |
| [success.json](success.json) | Completion message thành công; chỉ trỏ tới output S3 |
| [failure.json](failure.json) | Completion thất bại với mã/lời nhắn an toàn |
| [artifacts/input-manifest.json](artifacts/input-manifest.json) | Bản chụp input, source/rubric refs và processing versions |
| [artifacts/mock-source.json](artifacts/mock-source.json) | Source giả lập có đường dẫn/nội dung để kiểm evidence |
| [artifacts/rubric.json](artifacts/rubric.json) | Một tiêu chí giả lập |
| [artifacts/success-output.json](artifacts/success-output.json) | Object kết quả: provenance và insight để backend validate/lọc |
| [validate-fixtures.mjs](validate-fixtures.mjs) | Kiểm JSON, identities, hashes/sizes, keys và evidence cục bộ |

Chạy từ root repository:

```powershell
node docs/contracts/ai-job-v1/validate-fixtures.mjs
```

Success và failure là **hai terminal outcomes thay thế cho cùng job**. Dùng state mới cho mỗi kịch bản. Gửi outcome trái ngược sau terminal là một ca kiểm conflict riêng.

Commit SHA `0123456789abcdef0123456789abcdef01234567` là **giả lập**, chỉ kiểm hình dạng/đối chiếu. Không có repo thật được tải hay object được upload. `mock-source.json` chưa chốt format archive cho worker thật. Hash và size là giá trị thật từ các file UTF-8/LF không BOM ở đây; không sửa artifact mà quên cập nhật refs.

## 2. Định danh và tham chiếu

| Trường | Quy tắc đề xuất |
|---|---|
| `schemaVersion` | Message dùng `fcaj.ai.message.v1`; manifest/output/rubric có version riêng. Version không biết phải bị từ chối có kiểm soát |
| `messageId` | UUID của event ứng dụng, producer lưu bền và giữ nguyên khi retry cùng event. Khác SQS MessageId/receipt handle |
| `eventType` | `AI_JOB_REQUESTED` hoặc `AI_JOB_COMPLETED`; không phải trạng thái cuộc thi |
| `jobId` | UUID do backend cấp cho một lượt đánh giá bất biến. Retry vận chuyển giữ ID; lượt đánh giá mới dùng ID mới |
| `submissionId` | ID nghiệp vụ; số 101 ở fixture không chứng minh DB row tồn tại |
| `inputRevision` | SHA-256 của đúng bytes manifest đã lưu; không phải `Submission.version` vì entity hiện chưa có trường đó |
| `commitSha` | Full Git SHA-1 40 ký tự trong v1. Repo thật phải resolve/verify; thuật toán khác cần contract phù hợp |
| `processingVersion` | Phiên bản pipeline/input selection/validation |
| `modelConfigVersion`, `promptVersion`, `rubricVersion` | Phiên bản expected ghi ở job; worker echo đúng identity. Actual model/adapter ghi trong output provenance |
| `occurredAt`, `generatedAt` | UTC RFC3339 có Z. Backend lưu riêng thời điểm nhận/commit, không tin đồng hồ worker để đổi quyền/deadline |
| `key`, `versionId` | Ref nội bộ trong bucket cấu hình. Version null chỉ dùng với key bất biến, không overwrite |
| `sha256`, `sizeBytes`, `contentType` | Validate bytes tải về. Không coi S3 ETag là SHA-256; giới hạn download trong khi stream trước parse |

Tuple identity phải khớp DB/job/completion/output: jobId, submissionId, inputRevision, commitSha, processingVersion, modelConfigVersion, promptVersion, rubricVersion. Backend giữ expected refs và identity; không cập nhật chúng bằng giá trị do completion tự khai báo.

Manifest dùng competitionEventId/roundId/trackId để tránh nhầm ID cuộc thi với messageId. Backend resolve context từ submission và TeamEventEntry đúng mùa; ID client gửi không là bằng chứng quyền. Không đưa requester email/team name vào worker message. Repo URL nếu cần trong snapshot nội bộ phải được kiểm tra và không trả cho FE judge.

Revision không tự giải quyết concurrent update. BE1-12/13 cần đọc snapshot nhất quán, chuẩn bị object ngoài transaction, rồi lock/recheck input và quyền trong transaction ghi AiJob+outbox. Submission chưa có @Version; cần lock/recheck thật hoặc quyết định thêm version. Chỉ so submittedAt không chứng minh toàn bộ rubric/input còn nguyên.

Key mẫu nằm dưới inputs/{jobId}/, rubrics/{jobId}/, results/{jobId}/. Bucket/prefix thực nhận từ BE2 và cấu hình được. Message không mang access key/DB credential/presigned URL; worker/backend bind vào bucket được phép, không đọc bucket/URL tùy ý do message chỉ định.

## 3. State, delivery và duplicate

| Sự kiện | Trạng thái cũ | Kết quả |
|---|---|---|
| Input sẵn sàng; commit AiJob+outbox nguyên tử | Chưa có | PENDING_DISPATCH; trả API202 sau commit |
| SendMessage thành công; conditional mark | PENDING_DISPATCH | QUEUED |
| SendMessage lỗi | PENDING_DISPATCH | Giữ nguyên và retry outbox |
| Completion hợp lệ, output đã kiểm | PENDING_DISPATCH hoặc QUEUED | SUCCEEDED hoặc FAILED |
| Dispatcher mark đến sau completion | Terminal | Giữ terminal; vẫn đánh dấu outbox sent |
| Cùng messageId và cùng nội dung được gửi lại | Event đã xử lý | No-op, ack sau khi xác nhận state bền |
| Cùng messageId khác payload hoặc outcome khác terminal đã lưu | Bất kỳ | Conflict, không ghi đè; xử lý/replay theo runbook |

Consumer transaction ghi processed-event và state/result nguyên tử; **commit DB trước DeleteMessage**. DB lỗi thì chưa ack. Crash sau commit trước ack phải xử lý bản giao lại bằng no-op. Inference và delivery có thể lặp; không hứa exactly-once inference.

Dedup cần messageId unique và conditional update theo jobId/expected identity. Worker lưu/reuse completion identity khi retry. Event ID mới nhưng cùng semantic terminal có thể ack sau khi đối chiếu; payload trái ngược không ghi đè. Unknown job, sai revision/hash/key/schema không được tự làm job hợp lệ thành FAILED.

Job cũ hoàn tất chỉ cập nhật row job cũ. Nếu có latest-result pointer, gắn với thứ tự tạo lượt theo transaction, không chọn lượt hoàn tất muộn nhất. Completion cũ không đẩy pointer ngược; lịch sử hiển thị mỗi job/revision theo quyền.

Worker kiểm input, ghi output bất biến, gửi completion rồi mới ack jobs. Gửi completion lỗi thì retry cùng identity. Mock xử lý một job/lần trong ca tích hợp cô lập.

## 4. Quyền đã được người dùng chốt cho job API mới

Các quyết định sau được người dùng xác nhận trong chat ngày 27/10; vẫn cần đưa FE/AI/BE2 review trước tích hợp:

- **Coordinator chỉ đúng event được phân công.** Grant eventId=null không đủ quyền cho job API mới. Có role authority đơn thuần cũng không đủ. Tạo job phải trong cửa sổ judging mở.
- **Judge tạo job khi còn assignment hợp lệ đúng submission/round/track và judging mở.** Account/role hiện tại phải hợp lệ.
- **Judge đọc lịch sử của chính requester**, khi account/assignment vẫn hợp lệ. Hết giờ judging vẫn được đọc lịch sử riêng; revoke chặn request đọc tiếp theo. Judge thay thế không thừa hưởng job của judge cũ.
- **Coordinator được đọc job thuộc event được phân công.** System Admin, Mentor, Participant không mặc nhiên có raw AI access chỉ vì role đó.
- Mock bốn state **giữ pending khi worker offline**, để điều tra vận hành; không tự bịa timeout hoặc EXPIRED. Timeout/late-result policy phải chốt trước tích hợp thật.

| Source hiện tại | Hệ quả khi implement |
|---|---|
| /insights cho JUDGE/EVENT_COORDINATOR; coordinator chỉ findById, không event-scope/timer guard | Job API mới cần guard cụ thể; không sao chép nguyên nhánh coordinator cũ |
| Judge query kiểm JudgeAssignment active đúng round/track rồi assertJudgingOpen | Tái sử dụng ý nghĩa, kiểm quyền hiện tại trước tạo/đọc theo chính sách trên |
| UserPrincipal flatten UserEventRole thành authority và bỏ eventId | Phải query DB event grant, không chỉ nhìn ROLE_EVENT_COORDINATOR |
| User.isActive là participation access, không phải khóa staff toàn hệ thống | Không tự áp luật inactive participant cho staff; kiểm approved/account/role đúng nghiệp vụ |
| Chưa có job/history API | Policy mới cần tests revoke, wrong-event, other-requester, judging-closed/read-history |

Không dùng snapshot quyền lúc tạo job để bỏ qua quyền hiện tại. Quyền tạo và quyền đọc lịch sử là hai phép kiểm riêng; đóng judging không tự thu hồi lịch sử.

## 5. DTO và HTTP đề xuất để FE review

Các route này chưa được implement trong đợt chuẩn bị fixture:

| Request | Response dự kiến |
|---|---|
| POST /api/ai/submissions/{id}/jobs | 202, jobId/status/createdAt/resultAvailable=false. Requester từ phiên; không nhận arbitrary key/revision từ client |
| GET /api/ai/jobs/{jobId} | 200 với jobId/status/createdAt/finishedAt/resultAvailable và safe error khi có |
| GET /api/ai/jobs/{jobId}/result | 200 insight đã lọc khi SUCCEEDED; 409 RESULT_NOT_READY khi pending; FAILED trả safe error |

Đề xuất 401 chưa xác thực; 404 thống nhất cho job không tồn tại/không được xem; 403 role không được tạo; 409 JUDGING_CLOSED; input sai dùng400 có mã ổn định. FE phải review vì ApiResponse/exception hiện chưa có contract mã lỗi job. HTTP POST idempotency còn chờ chốt; SQS dedup không chống tạo hai job do POST retry.

FE không nhận requester identity, raw S3 refs/manifest/source, repo URL/owner, thông tin đội/thành viên, stack trace hay raw worker message. Không serialize toàn output S3 về FE; map allowlist. criteriaId/evidence trong mẫu là trường đề xuất, DTO hiện chỉ có criteriaName/assessment/suggestedScoreRange; cần version/review trước expose.

Mock dùng suggestedScoreRange=null vì không đánh giá code thật. Model thật phải validate range theo rubric; giữ string tương thích DTO hay đổi structured range cần chốt. Output AI không ghi Score/RoundResult chính thức. Việc loại field định danh không bảo đảm free text hoàn toàn ẩn danh; README/code/model text vẫn cần kiểm/lọc.

## 6. Lỗi và phần còn Pending

- MOCK_PROCESSING_FAILED là lỗi giả lập có chủ đích. Backend dùng allowlist code và message do ứng dụng kiểm soát; không gửi nguyên error.message do worker cung cấp ra FE/log công khai.
- Lỗi transport/S3/DB có thể retry theo giới hạn được chốt. Sai schema/identity/hash là validation failure. DLQ không tự đổi DB state; replay phải có runbook.
- deadlineAt=null nghĩa fixture chưa bật deadline. Không tự chuyển pending thành FAILED/EXPIRED; thời lượng, nguồn timeout, retry limit và late-result policy phải được chốt trước tích hợp thật/model thật.
- Production validator cần strict schema, size/text/array limits, criteria count/range và evidence path-line thật. Fixture checker chỉ kiểm bộ ví dụ này, không thay validator production.
- Snapshot thật còn cần public repo được đồng ý, SHA thật, archive format, bytes/files/uncompressed limits, file selection, private/submodule/LFS và quota. Không chạy build/script của bài thi.

## 7. Bàn giao BE1-11/19

| Đầu ra/owner | Trạng thái |
|---|---|
| BE1: ba queue messages + artifacts/checksums | Đã chuẩn bị trong thư mục này |
| Người dùng: bốn state, quyền event/requester/history, pending mock | Đã xác nhận chat27/10; cần review liên nhóm |
| FE/AI/BE2: schema/version, DTO/errors, IDs/dedup | Chờ review, chưa gửi tự động |
| AI: source format/limits, mock owner/runner/lịch online | Chưa bàn giao |
| BE2: Region, bucket/prefix/versioning, queue/DLQ, identities, runtime/log/API/digest | Chưa bàn giao; tên trong proposal chỉ là dự kiến |
| BE1+BE2: test scope riêng hoặc lịch cô lập một job/lần | Chờ xác nhận trước Receive/DeleteMessage thật |
| RDS/schema | Đã có V1/Flyway và lab MySQL8.4 restore/validate; runtime rollout và RDS còn pending. Xem [báo cáo dump](../../report/sql-dump-review-2026-10-27.md) |

Nghiệm thu cần round trip mock; duplicate/conflict; completion trước mark-dispatched; DB lỗi chưa ack; result cũ không ghi đè lượt mới; revoke/wrong-event; restart đọc lại. Bộ JSON không chứng minh các ca ứng dụng/AWS đã chạy.

## 8. Nguồn đã đối chiếu

Implementation update 28/10/2026 (BE1 local; inter-team protocol review remains pending): producer and worker share `JobLimits`. Limits count actual serialized UTF-8 bytes: rubric, manifest and output 262144; source artifact JSON 8000000; queue message 32768. The collector's 1MB text budget is separate. Oversized rubric/source returns400 `RUBRIC_TOO_LARGE`/`SOURCE_TOO_LARGE` before uploads or job/outbox commit. A final manifest over its limit returns400 `MANIFEST_TOO_LARGE`; already-prepared immutable inputs require reference-aware orphan reconciliation. Nothing silently truncates the official rubric. GitHub body/snapshot timeout returns504 `REPOSITORY_FETCH_TIMEOUT`, cancels the request/subscription, and commits no job/outbox. These limits preserve the existing worker ceiling; no protocol limit increase has been approved.

- [Kế hoạch Backend1](../../plans/backend-1-implementation-plan.md)
- [AiController](../../../back-end/src/fcaj-api/src/main/java/com/seal/hackathon/controller/AiController.java) và [AiJudgeAssistantService](../../../back-end/src/fcaj-api/src/main/java/com/seal/hackathon/service/AiJudgeAssistantService.java)
- [Submission](../../../back-end/src/fcaj-api/src/main/java/com/seal/hackathon/entity/Submission.java) và [SubmissionRepository](../../../back-end/src/fcaj-api/src/main/java/com/seal/hackathon/repository/SubmissionRepository.java)
- [UserPrincipal](../../../back-end/src/fcaj-api/src/main/java/com/seal/hackathon/security/UserPrincipal.java), [UserEventRole](../../../back-end/src/fcaj-api/src/main/java/com/seal/hackathon/entity/UserEventRole.java), [UserEventRoleRepository](../../../back-end/src/fcaj-api/src/main/java/com/seal/hackathon/repository/UserEventRoleRepository.java)
- [AiInsightResponse](../../../back-end/src/fcaj-api/src/main/java/com/seal/hackathon/dto/response/AiInsightResponse.java)
