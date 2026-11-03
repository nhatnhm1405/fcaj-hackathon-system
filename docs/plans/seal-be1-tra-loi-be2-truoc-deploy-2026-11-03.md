# BE1 → BE2: Phản hồi trước deploy SEAL Hackathon

Cập nhật 03/11/2026, Asia/Saigon, sau khi hoàn thiện và kiểm candidate local. Chưa commit/push/deploy hoặc gửi file này cho BE2. Người dùng xác nhận chưa có thông tin mới từ BE2; hạ tầng vẫn đang thảo luận.

Đã đối chiếu trực tiếp file BE2 ngày01/11: [checklist và plan tổng hợp](seal-be1-be2-consolidated-handoff-plan-2026-11-03.md) ghi đủ sáu đầu ra, khác biệt SEAL/ECR và thông tin hạ tầng BE2 đã cung cấp. Người dùng đã chốt phía BE1 **dữ liệu mẫu trước, restore `.bak` riêng**. Không coi đây là quyền tự provisioning RDS.

## 1. BE2 lấy code nào?

Nhánh hiện tại `feature/backend1-be2-handoff`; HEAD `bab44e5fdeb710e6c71982b2e8f5547829fa8e40` là **baseline trước sửa**, không chứa candidate SEAL chưa commit. Module working tree đã là `back-end/src/seal-api`, Maven group `com.seal`, package `com.seal.hackathon`. Main/CLI tương ứng:

- `com.seal.hackathon.HackathonApplication`
- `com.seal.hackathon.db.DatabaseMigrationMain`
- `com.seal.hackathon.aijob.AwsMockWorkerMain`

Artifact giữ `target/hackathon-0.0.1-SNAPSHOT.jar`; service `seal-backend`; host settings `/etc/seal/deploy-settings.sh`. Migration/protocol/storage không đổi theo tên Java: giữ V1/V2/V3, catalog/tài nguyên AWS, `fcaj.ai.*`, object keys và business guards.

**Chưa có release SHA mới cho BE2 checkout.** Source/image identity local và working-tree manifest ở [bộ evidence](../checks/seal-be1-local-2026-11-03/README.md). Cách build/giao candidate/private EC2 do BE2 xác nhận; ECR pipeline vẫn hoãn. Không dùng push/merge main để thử deploy.

## 2. Đã kiểm được gì?

| Lượt kiểm mới ngày03/11 | Kết quả | Giới hạn |
|---|---|---|
| Clean backend package Java21 | 623 được thống kê: **614 thực thi đạt, 9 MySQL-only skipped**, không failure/error | H2/mocks; MySQL chạy riêng |
| MySQL8.4 lab | **36/36**, không skip: 21 job + 9 fresh DB/packaged CLI + 6 concurrency | Container mới loopback; không RDS/TLS/IAM live |
| Production component + API rehearsal | **26/26** | H2, GitHub/S3/SQS mock; bao gồm hai ca dispatch BE2 từng báo lỗi |
| Deploy command ordering | **8/8** | Không replace backend khi init/gate fail; thành công chỉ thay seal-backend |
| Contract/parser/config | Current samples và 7 fixture cũ hợp lệ; parser7/7; hai Compose variants parse đạt | Không nghiệm thu liên nhóm hoặc AWS |
| Dockerfile và env probe | Image local build đạt; hai template giữ nguyên dollar/quotes trong Java container, network disabled | Không khởi động backend trên RDS hoặc triển khai ALB |

Không cộng các lượt có ca trùng thành tổng test độc lập. Log/count/checksum mới xem [báo cáo BE1](../report/seal-be1-local-completion-2026-11-03.md).

Rehearsal phát hiện và sửa lỗi **JWT cookie làm CSRF bị xóa ở request xác thực tiếp theo**: filter giờ lưu context vào repository chỉ sống trong request, không HttpSession. Ca hồi quy kiểm cookie login/CSRF thực, thiếu token vẫn403 và logout rồi `/me`401. Đây là lỗi riêng được tái hiện local, **không kết luận là root cause hai test dispatch trên máy BE2**. Cần BE2 cung cấp full log/SHA/lệnh/JDK/OS/profile/DB của lượt lỗi cũ để điều tra chênh môi trường.

## 3. RDS rỗng tạo bảng và dữ liệu cũ thế nào?

RDS do BE2 quản lý. CLI hiện hỗ trợ kiểm target/runtime URL trước ghi, MySQL8.4/case1/catalog/charset/collation rồi:

```text
initialize-empty: V1 → V2 → V3
→ verify-deploy: history/checksum/fingerprint/mapping
→ khởi động backend bằng runtime identity
```

DB rỗng không baseline. `initialize-empty` có chọn rõ cho lần đầu, sau đó `verify-only`; không tự upgrade DB V1/V2, baseline/repair/clean schema lạ. Migration identity riêng với runtime DML-only; JVM dùng RDS CA và `sslMode=VERIFY_IDENTITY`. CLI/gate/runtime cùng candidate.

Không bắt buộc import `.bak` để boot/MOCK. **Phía BE1 đã chốt dữ liệu mẫu trước và restore riêng**, khớp đề xuất BE2 trong file gốc. Còn chốt operator/provisioning/cleanup và window trên RDS; không ghi RDS trong lượt review này. Đợt restore riêng sau này cần backup/restore/schema review/baseline1 rồi V2/V3 theo runbook, không chạy V1 tạo lại trên dump.

## 4. Cần cấu hình và topology nào?

[Runtime/ALB runbook](seal-be1-runtime-config-runbook.md) có nhóm biến boot, auth, storage, jobs/delivery, bootstrap, OAuth/mail/worker và matrix network/IAM từ code.

- AWS profile vẫn cần S3 khi jobs tắt. `APP_AI_JOBS_DELIVERY_ENABLED=false` chủ động tắt dispatcher/result poller; bật trong queue window.
- Template `deploy/compose.aws.example.yml` dùng proxy local, bind loopback.
- Template `deploy/compose.aws.alb-direct.example.yml` là **đề xuất** HTTP:8080, bắt buộc private `DEPLOY_TARGET_BIND_IP`; BE2 chốt ALB/target/TLS/SG. Không coi template được tạo là topology đã duyệt.
- FE S3, CloudFront/domain/HTTPS/API origin/CORS/cookie/CSRF cần FE/BE2 xác nhận. Source đã hỗ trợ forwarded headers; browser qua ALB thật còn pending.
- Private EC2 cần đường tới public `api.github.com`, RDS/S3/SQS, SSM nếu chọn và nguồn build nếu build tại EC2. Role host không tự chứng minh credentials trong container.
- Paths `/etc/seal/` chỉ là gợi ý; không move private files trên host. Không gửi secret/private env trong phản hồi.

Workflow SSH cũ chưa có đường từ GitHub-hosted runner tới private EC2 được nghiệm thu. Không tự bổ sung ECR/SSM pipeline thay BE2.

## 5. Dữ liệu để tạo job đã chuẩn bị chưa?

Có [runbook API](seal-be1-smoke-data-runbook.md) và [JSON placeholder](../checks/seal-be1-local-2026-11-03/request-templates.json). Rehearsal mới đã đi **100 request qua controllers/security/services thật**:

```text
reference/bootstrap → cookie login + CSRF → event/accounts/grants
→ một track/round → hai đội × ba thành viên bằng invite/accept
→ approve → SETUP → chọn track → IN_PROGRESS
→ rubric + judge assignment → CONTEST → leader submission
→ stop CONTEST → JUDGING → hai job MOCK riêng
```

IDs lấy từ API response; entry IDs nội bộ chỉ được ghi bằng query đọc sau khi provisioning qua API hoàn tất, không insert SQL để vượt guard. Rehearsal dùng H2 schema test + V2; migrations V1–V3 được kiểm riêng trong lab MySQL. Không gọi đó là browser/RDS acceptance.

Runbook mô tả rerun/stop và nhận biết tài nguyên theo run ID; không reset DB hoặc retry POST mù. Repo trong test là synthetic; smoke live phải chọn repo public nhỏ được phép dùng. Provisioning/cleanup trên RDS cần operator BE2 và scope riêng.

## 6. Chạy MOCK worker và nghiệm thu AWS ra sao?

Khi có candidate release, environment/role và window được chốt, operator dùng cùng candidate JAR/image:

```sh
java -Dloader.main=com.seal.hackathon.aijob.AwsMockWorkerMain \
  -cp target/hackathon-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher --one-success
```

Đổi `--one-failure` trên **job mới** để kiểm failure. Worker cần region/bucket/prefix/jobs/results queue và scoped credential provider, không DB/JWT/model thật. Worker một lượt nhận một message khả dụng, không chọn jobId; kiểm backlog/consumer, không Purge queue.

API POST job không body →202; status/result theo [contract MOCK](../contracts/ai-job-v1/CURRENT.md). Success SUCCEEDED/result200; failure FAILED/result409 MOCK_PROCESSING_FAILED. Không đổi Score/RoundResult; không tự retry POST vì chưa có HTTP idempotency key. Giữ visibility120s/long poll20s theo code và đối chiếu AWS config.

Chưa có owner/window hoặc bằng chứng live. Cần kiểm ALB/RDS TLS/identity/version trước, file/job persistence sau restart, hai job success/failure và quyền; lưu SHA/image/job/time/log đã lọc. MOCK pass không phải model thật hoặc FE job UI pass.

## Các phản hồi còn cần

[Bảng Q01–Q10 và phân công đề xuất](seal-be1-be2-clarifications-2026-11-03.md) ghi rõ thông tin → lý do → live check phụ thuộc → người trả lời → pending. Không dùng thiếu đầu vào BE2 để phủ nhận phần BE1 local đã kiểm, và không dùng local pass để xác nhận AWS chưa chạy.

Bản nội dung trước cập nhật được giữ tại `back-end/src/seal-api/target/preexisting-documents/`; các plan thảo luận/AI ban đầu vẫn giữ để truy lịch sử.
