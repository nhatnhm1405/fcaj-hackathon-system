# Contract triển khai BE1 — 28/10/2026

Đặc tả để FE/AI/BE2 review theo working tree BE1; chưa có sign-off liên nhóm. Các fixture cũ ở thư mục cha là ví dụ lịch sử. Bộ `current-examples/` được xuất từ test `productionWorkerRoundTripSuccessFailureAndDuplicate`, dùng producer, worker và validator production với GitHub/S3/SQS giả lập. Source có `fixtureOnly=true`, ID/SHA/repository đều synthetic; không gửi mẫu vào queue nghiệp vụ. Test tạo job khác nhau cho success và failure.

## HTTP

Profile phải bật `app.ai.jobs.enabled=true`. Cả ba API yêu cầu đăng nhập và authority JUDGE hoặc EVENT_COORDINATOR. Với JWT cookie, POST còn yêu cầu CSRF theo cấu hình chung; không bỏ CSRF để tích hợp. Requester lấy từ phiên. POST không cần body, không nhận key/revision do FE chỉ định.

| API | HTTP | Body mẫu |
|---|---:|---|
| POST `/api/ai/submissions/{submissionId}/jobs` | 202 | `current-examples/success/http-create.json` |
| GET `/api/ai/jobs/{jobId}` | 200 | `current-examples/success/http-status.json` |
| GET `/api/ai/jobs/{jobId}/result` | 200 | `current-examples/success/http-result.json` |
| GET result của job FAILED | 409 | `current-examples/failure/http-result.json` |

Wrapper thành công là `{success:true,message:string,data:object}`; lỗi job là `{success:false,message:SAFE_CODE}`, không có data. Mã lỗi nằm trong `message`, không có field `errorCode` ở wrapper. Status data có đúng `jobId,status,createdAt,finishedAt,resultAvailable,errorCode,mode`; hai field thời gian kết thúc/lỗi nullable. mode luôn MOCK; bốn state PENDING_DISPATCH, QUEUED, SUCCEEDED, FAILED. Chưa có RUNNING/EXPIRED. Poll có thể bỏ qua trạng thái trung gian.

| HTTP | Mã / tình huống |
|---:|---|
| 400 | INVALID_JOB_IDENTIFIER; SUBMISSION_NOT_READY; RUBRIC_NOT_READY; RUBRIC_TOO_LARGE; SOURCE_TOO_LARGE; MANIFEST_TOO_LARGE; JOB_MESSAGE_TOO_LARGE |
| 400 | PUBLIC_GITHUB_REPOSITORY_REQUIRED; INVALID_REPOSITORY_REVISION; REPOSITORY_LIMIT_EXCEEDED; INVALID_REPOSITORY_SOURCE; SUBMODULES_NOT_SUPPORTED; GIT_LFS_NOT_SUPPORTED; REPOSITORY_HAS_NO_SUPPORTED_SOURCE; REPOSITORY_FETCH_UNAVAILABLE |
| 401 | Chưa xác thực; security filter xử lý trước controller |
| 403 | JOB_CREATE_FORBIDDEN / JOB_ACCESS_FORBIDDEN; security filter/CSRF có thể trả response khác wrapper job |
| 404 | JOB_NOT_FOUND: không tồn tại hoặc bị ẩn do quyền/approval hiện tại |
| 409 | JUDGING_CLOSED; SUBMISSION_INPUT_CHANGED; CONCURRENT_UPDATE; RESULT_NOT_READY; MOCK_PROCESSING_FAILED |
| 504 | REPOSITORY_FETCH_TIMEOUT |
| 503 | JOB_SERVICE_UNAVAILABLE; FE xử lý safe error và không tự retry POST |

Coordinator cần grant đúng event, grant null không đủ. Judge cần assignment hiện tại; lịch sử chỉ của requester đó. Đóng judging vẫn đọc được lịch sử, revoke/approval thay đổi chặn lần đọc tiếp theo. POST retry tạo lượt mới: HTTP idempotency key chưa triển khai.

Result data có summary, strengths, concerns, criteriaInsights, repo, disclaimer, model. Criteria chỉ có criteriaName, assessment, suggestedScoreRange=null. Repo có analyzed=false, note, techStack, signals, redFlags. Không trả criteriaId/evidence/provenance/source/refs/requester/repository cho FE. Disclaimer do backend thay bằng câu cố định. Không cập nhật Score/RoundResult.

## Message và artifact

Đặc tả trường dưới đây dùng cùng validator production và các mẫu được test; chưa cung cấp JSON Schema độc lập. `JobJson` từ chối duplicate key/trailing JSON. Trường bắt buộc phải hiện diện kể cả nullable; validator result từ chối field thừa ở mọi object được kiểm. Worker MOCK kiểm envelope/refs và tính nhất quán manifest, chưa là strict validator toàn bộ source/rubric/manifest; không coi fixture checker hoặc MOCK worker là validator đầy đủ cho model thật.

| Object | Trường |
|---|---|
| Identity (I) | jobId UUID; submissionId integer>0; inputRevision SHA-256 lowercase64 của đúng bytes manifest; commitSha lowercase40; processingVersion, modelConfigVersion, promptVersion, rubricVersion chuỗi tối đa100 |
| Request `fcaj.ai.message.v1` | I + schemaVersion,eventType=AI_JOB_REQUESTED,messageId UUID,occurredAt timestamp,mode=MOCK,deadlineAt=null,input |
| input | manifest,source,rubric: mỗi giá trị là Ref |
| Ref | key chuỗi,sha256 lowercase64,sizeBytes integer>0,contentType=application/json,versionId nullable string tối đa1024 |
| Manifest `fcaj.ai.input-manifest.v1` | schemaVersion,fixtureOnly,submissionId,competitionEventId,roundId,trackId nullable integer,commitSha,submissionSnapshot,source,rubric,processingVersion,modelConfigVersion,promptVersion,rubricVersion |
| submissionSnapshot | description,repoUrl,demoUrl,slideUrl: chuỗi nullable theo submission |
| Source `fcaj.ai.source.github.v1` | schemaVersion,fixtureOnly=false ở collector thật,commitSha,files [{path,content}] |
| Rubric `fcaj.ai.rubric.v1` | schemaVersion,criteria [{criteriaId,name,description nullable,maxScore,weight}],rubricVersion |
| Completion `fcaj.ai.message.v1` | I + schemaVersion,eventType=AI_JOB_COMPLETED,messageId,occurredAt,status; SUCCEEDED bắt buộc result Ref và không có error; FAILED bắt buộc error và không có result |
| error | code=MOCK_PROCESSING_FAILED,message chuỗi không rỗng tối đa1000; FE chỉ nhận mã allowlist |
| Output `fcaj.ai.output.v1` | I + schemaVersion,generatedAt,provenance,insight |
| provenance | mode=MOCK,modelId,modelRevision,adapterVersion; ba tên khớp `[a-zA-Z0-9_.-]+`, tối đa100 |
| insight | summary,strengths,concerns,criteriaInsights,repo,disclaimer,model |
| criteriaInsights item | criteriaId integer thuộc rubric,criteriaName khớp tên rubric,assessment,suggestedScoreRange=null,evidence [{path,startLine,endLine}] |

Identity phải echo chính xác request/job đã lưu. Timestamp result parse được bằng Instant; producer/worker ghi UTC Z. Model insight khớp provenance modelId. Mỗi criterion xuất hiện đúng một lần; evidence tối đa20/criterion, path thuộc source, số dòng integer từ1 đến số dòng thực. Summary/assessment tối đa4000 ký tự; strengths/concerns tối đa20 chuỗi, mỗi chuỗi2000; repo lists tối đa20 chuỗi, mỗi chuỗi1000; repo note1000; disclaimer2000. Safe text không nhận URL/email/NUL. Free text vẫn cần policy riêng trước model thật.

Rubric version do producer tính `sha256-` + SHA-256 serialized rubric trước khi thêm rubricVersion. Artifact bytes cuối cùng dùng để tính Ref là UTF-8 sau serialize, không dùng ETag hay hash bản pretty-print khác. Queue message <=32768 bytes; manifest/rubric/output <=262144; source JSON <=8000000. Rubric criteria1..50. Collector giới hạn riêng:40 file,100000 bytes/file,1000000 bytes text,2000 tree entries; ngân sách snapshot45s và tối đa15s/request gồm body.

Key logical producer: `inputs/{jobId}/source.json`, `inputs/{jobId}/input-manifest.json`, `rubrics/{jobId}/rubric.json`. Worker ghi `results/{jobId}/mock-output.json`; validator cho phép một tên `[a-zA-Z0-9_-]+.json` dưới đúng job. Object S3 thật là `{APP_STORAGE_PREFIX}/{logicalKey}`, bucket lấy từ config. Không nhân đôi prefix, không nhận bucket/URL tùy ý từ message. Backend/worker phải dùng cùng bucket/prefix. Version null chỉ đọc key bất biến; PutObject dùng If-None-Match. Worker bỏ versionId của output thành null để completion byte identity ổn định khi retry.

Worker lưu output → gửi completion → ack jobs. Consumer validate → commit state+ledger → ack results. Duplicate cùng messageId+payload đã commit có thể ack khi S3 tạm lỗi; conflict không ghi đè. Jobs/results receive120s,long poll20s,mỗi lượt1 message; outbox lease300s là cơ chế riêng. Worker offline giữ pending; DB/S3/transport failure không tự biến thành lỗi model. DLQ/replay và deadline model thật cần review riêng.

## Kiểm lại

Từ module backend: `./Test/run-integration-smoke.ps1`. Script dùng H2 bộ nhớ, user/role/assignment/event/timer/submission/rubric synthetic do test tự chuẩn bị; không cần secret, không reset DB dùng chung. Cần Java21 và Maven dependencies đã có cache (bỏ `-o` thủ công nếu cần tải dependency). Trên môi trường Linux chạy Maven Wrapper cùng hai test class với profile test.

Test ghi sample mới dưới target/integration-contract. Bộ current-examples là snapshot đã review; thay sample phải chạy lại worker và JobResultValidator, cập nhật manifest checksum. Test còn kiểm pending/offline, quyền, duplicate, DB-before-ack, conflict, dữ liệu bền khi tạo repository mới. Chưa kiểm restart toàn process, TLS/IAM/S3/SQS live hoặc FE UI.
