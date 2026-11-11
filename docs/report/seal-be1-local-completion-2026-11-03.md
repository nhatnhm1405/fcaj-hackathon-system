# BE1 local — Hoàn thiện candidate SEAL và bộ bàn giao

Ngày **03/11/2026**, Asia/Saigon. Đã thực hiện phần local AI-00→AI-07 của [plan](../plans/seal-be1-ai-execution-plan-2026-11-03.md). Source, package/tests, MySQL lab, rehearsal API và templates local đã được kiểm. **Chưa commit/push/merge/deploy/gọi AWS hoặc gửi tin cho team; chưa có release SHA mới.**

## 1. Source và ranh giới bằng chứng

Baseline: branch `feature/backend1-be2-handoff`, HEAD `bab44e5fdeb710e6c71982b2e8f5547829fa8e40`. SHA này là source trước sửa, không đại diện candidate SEAL trong working tree. Không có AGENTS.md áp dụng được tìm thấy ở repository/ancestors đã kiểm. Checkout hiện tại được tái sử dụng.

Thay đổi có trước gồm `docs/README.md` và bốn file plan/handoff untracked. Không reset/stash/checkout đè. Bản trước cập nhật được giữ tại `back-end/src/seal-api/target/preexisting-documents/`; hash ở [preexisting-documents.json](../checks/seal-be1-local-2026-11-03/preexisting-documents.json). Các plan ban đầu vẫn giữ nội dung lịch sử; bản phản hồi BE2 và index/current handoff được cập nhật theo kết quả thực.

Evidence mới tại [seal-be1-local-2026-11-03](../checks/seal-be1-local-2026-11-03/README.md). Manifest đọc **working-tree bytes**, không dùng verifier HEAD ngày28 để chứng minh candidate mới. Raw generated logs/JAR/image outputs ở target, không thuộc source bàn giao. Không đưa .env, dump, private truststore hoặc user artifact vào bundle.

## 2. Vấn đề → thay đổi → kiểm chứng

| Task | Thay đổi | Bằng chứng / kết quả |
|---|---|---|
| AI-00 | Inventory/baseline, phiên bản tool, hash migration/contract và giữ trạng thái có trước | Baseline host package: 613 thực thi đạt,9 MySQL-only skipped. Lượt sandbox trước đó có lỗi tạo loopback, được giữ log riêng |
| AI-01 | Module `back-end/src/seal-api`, package main/test `com.seal.hackathon`, groupId `com.seal`; giữ artifactId/JAR name | 418/418 file gốc có path tương ứng; 366 Java file gốc được so với baseline sau normalize rename. JAR có ba SEAL entrypoints, không com/fcaj hoặc H2 runtime |
| AI-02 | Cập nhật CI, Compose dev, năm BAT E2E, launchers/runners/schema tools/current docs và .gitattributes | Không còn executable reference module/package cũ trong scope chạy. Gate chỉ thay `seal-backend` sau init/verify thành công; 8/8 command-order tests |
| AI-03 | Tách Compose proxy-local và đề xuất ALB-direct private interface:8080; chuẩn hóa gợi ý /etc/seal; phân nhóm config và network/IAM matrix | Hai template parse đạt; thiếu direct bind IP bị reject. Probe hai template trong image local giữ nguyên env dollar/quotes, network disabled; không backend/DB/AWS process |
| AI-04 | Runbook hai đội × ba người, cookie/CSRF, grants/assignment/timers/rubric/submission và hai job riêng; sửa lỗi JWT/CSRF đã tái hiện | 100 request qua MockMvc/controllers/security/services thật; success/failure, duplicate, quyền, judging đóng, bootstrap idempotence và logout đạt |
| AI-05 | Clean package/full tests, component/API smoke, MySQL8.4 lab, packaged CLI/worker, validators, syntax, Dockerfile và local docs | Chi tiết mục3; protocol/migration bytes không đổi |
| AI-06 | Báo cáo này, evidence/count/log/hash/checkpoint mới | Phân biệt package local/MySQL/API/template/image với các live checks chưa chạy |
| AI-07 | Cập nhật current handoff/index/phản hồi sáu mục và bảng Q01–Q10 | [BE2 clarifications](../plans/seal-be1-be2-clarifications-2026-11-03.md), chưa gửi team hoặc gán deadline/người cụ thể thay owner |

Không đổi tên catalog/bucket/queue/object key hoặc namespace `fcaj.ai.*`. Các tên lab `fcaj_ai_integration_lab`, biến `FCAJ_AI_TEST_JDBC_URL`, label Docker lab và GitHub repository hiện hữu được giữ có chủ đích; logs/checksum lịch sử không rewrite.

## 3. Kiểm chứng mới, không dùng kết quả cũ thay thế

| Kiểm tra | Kết quả | Phạm vi chính xác |
|---|---|---|
| Baseline sandbox trước rename | 622 thống kê;0 assertion failure,1 loopback error,9 skipped | `BoundedHttpTest` không tạo được loopback trong sandbox; không sửa assertion để vượt |
| Baseline ngoài sandbox | 622 thống kê;**613 thực thi đạt**,9 skipped | Java21/H2, compile/package baseline đạt |
| **Clean package SEAL** | 623 thống kê;**614 thực thi đạt**,0 failures/errors,9 MySQL-only skipped | `mvnw.cmd -o -B -Dspring.profiles.active=test clean package`; output mới, không dùng target com.fcaj cũ |
| **MySQL8.4** | **36/36**,0 failures/errors/skipped |21 AiJobIntegration +9 FreshDatabaseDeployment +6 FileReferenceConcurrency; migration V1/V2/V3 thật, isolated loopback container |
| **Production-component/API smoke** | **26/26**,0 failures/errors/skipped |21 integration +4 worker +1 API rehearsal; final rehearsal bổ sung record IDs đã được kiểm qua runner |
| Deploy command ordering | **8/8** | Init/gate/precheck fail không replace; thành công chỉ `up -d --no-deps --no-build seal-backend` |
| Dump parser | **7/7** | Parser fixtures, không chạy dump thật |
| Contract | Current samples pass;7 legacy fixture checks pass | Bytes/identity/refs, không gửi synthetic fixture vào queue runtime |
| Demo preflight | **19/19** | Pure parser/helper checks, không chạy reset/demo |
| Compose và Docker | Hai variant parse; missing-bind reject; Dockerfile build thành công; hai env probes pass | Probe Java nhỏ trong container network none, không app/RDS/TLS/AWS smoke |
| Archive/launchers | SEAL main/migration/worker có trong JAR; worker launcher đến argument validation trước env/AWS | Packaged migration init/verify đã chạy thật trong MySQL9 cases; worker process round-trip được harness kiểm |
| Syntax/docs | PowerShell/Bash syntax, local links và whitespace checks | Xem [verification-summary.json](../checks/seal-be1-local-2026-11-03/verification-summary.json) |

Các suite có test trùng nhau, **không cộng** 614+36+26 thành số ca độc lập. Hai ca `productionWorkerRoundTripSuccessFailureAndDuplicate` và `roundTripCommitsBeforeAckAndKeepsScoresUntouched` đạt trong candidate local. Chưa có log đầy đủ từ BE2 để kết luận root cause trên máy BE2.

Tool host: Oracle Java21.0.10, Maven Wrapper3.9.16, Node24.15.0, Docker29.5.3, Compose5.1.4. Dockerfile build dùng Maven3.9.6/JDK21, runtime JRE21. Lab MySQL8.4.13 từ image digest đã pin; container `fcaj-ai-lab-337a2d553fcd` **đã dừng và giữ lại**. Runners dùng mật khẩu ngẫu nhiên, không dùng DB/.env đang làm việc. Flyway có warning metadata rằng8.4 mới hơn họ đã verified8.1; các36 ca vẫn chạy thật đạt, không tự nâng dependency ngoài scope để giấu warning.

Test logs đã lọc và SHA ở [filtered-test-logs.json](../checks/seal-be1-local-2026-11-03/filtered-test-logs.json); từng suite ở clean-package-tests.json/mysql-tests.json/component-smoke-tests.json trong evidence. Git Bash sandbox gặp lỗi IPC; cùng gate suite ngoài sandbox đạt8/8. POM hiện nạp Mockito agent ngay khi JVM test khởi động và đặt Unix-domain socket temp dưới target; không cần agent copy từ output cũ.

## 4. Lỗi JWT cookie/CSRF phát hiện trong rehearsal

**Trigger tái hiện:** lấy XSRF cookie → login cookie thành công → GET có JWT → XSRF cookie bị xóa (`Max-Age=0`) → request ghi tiếp theo403. Các test cũ chủ yếu dùng Bearer hoặc chỉ kiểm cookie thiếu CSRF nên chưa đi hết chuỗi này.

**Nguyên nhân:** `JwtAuthenticationFilter` chỉ set SecurityContextHolder; request-only repository chưa ghi context. `SessionManagementFilter` coi việc phục hồi JWT ở request kế tiếp là đăng nhập mới, chạy CSRF authentication strategy và xóa token sau filter đã prime cookie.

**Sửa tối thiểu:** sau khi validate JWT, lưu context vào `RequestAttributeSecurityContextRepository`, cùng cơ chế request-only của SecurityConfig. Không lưu authentication vào HttpSession, không tắt CSRF/session-fixation strategy cho OAuth và không đổi grant/assignment. Đây là **Java production file gốc duy nhất** thay logic ngoài rename.

**Hồi quy:** XSRF giữ được qua các GET xác thực và các mutation liên tiếp; Secure/HttpOnly auth cookie, login JSON không token, POST thiếu CSRF403, revoke chặn đọc, logout rồi `/me`401. Full backend và component/API suites đạt sau sửa. OAuth/provider/browser qua ALB vẫn chưa nghiệm thu; không gọi lỗi này là nguyên nhân dispatch BE2 khi chưa có bằng chứng.

## 5. Rehearsal dữ liệu và mức chứng minh

[Runbook](../plans/seal-be1-smoke-data-runbook.md) + [request templates](../checks/seal-be1-local-2026-11-03/request-templates.json) đã mô tả từ reference/bootstrap đến job. Hai đội ba người được tạo/mời/nhận qua API trong trạng thái PENDING, approve rồi SETUP, cùng track trước IN_PROGRESS. Rubric đúng round, judge assignment trước judging, CONTEST chạy rồi dừng trước JUDGING; leader submit hợp lệ.

[api-rehearsal.json](../checks/seal-be1-local-2026-11-03/api-rehearsal.json) có100 HTTP requests, IDs giả và hai job độc lập. Account/event/track/team/round/submission/assignment IDs lấy qua API; entry IDs nội bộ được query đọc sau provisioning, không insert SQL để tạo dữ liệu. Test còn xác nhận bootstrap không đổi hash/count admin khi gọi lại và Score count giữ nguyên. Tests bootstrap hiện có kiểm flag off không tạo admin và từ chối nâng account thường.

Core schema H2 do ORM test tạo, bảng JDBC jobs từ V2; không giả rằng H2 này đã chạy bộ MySQL V1/V3. MySQL suite kiểm riêng migrations/CLI/schema/runtime không DDL. Rehearsal chưa phải browser/HTTP socket, full process restart, S3/SQS/GitHub live hoặc RDS provisioning. Source của các mock không được đưa thành endpoint production.

## 6. Artifact và bảo toàn source

- [source-preservation.json](../checks/seal-be1-local-2026-11-03/source-preservation.json):418 file gốc còn đủ,366 Java gốc được đối chiếu; chỉ JWT filter có sửa logic. Test rehearsal và template/verifier là file mới.
- **30 file migration/contract được đóng băng** khớp bytes baseline; gồm V1/V2/V3 và toàn bộ contract trừ README chỉ sửa link. V1 `91a3b9b0…8ea9d`, V2 `4e40d1f4…a4ea7`, V3 `8e67ee1a…0ce49`; hash đầy đủ trong [jar-identity.json](../checks/seal-be1-local-2026-11-03/jar-identity.json) và baseline inventory.
- Host JAR: `target/hackathon-0.0.1-SNAPSHOT.jar`, SHA256 `0c9c5225bf87d5b4711cb15726f34cda5c75a32ee599d92dad0a8b734c819658`. Start-Class `com.seal.hackathon.HackathonApplication`, không com/fcaj/H2 runtime.
- Image local: `seal-backend-local:be1-20261103-01`, ID `sha256:625f94d99c41ecdcca9ee9f46e9f42c3f50a9da744ed373e05ee84851c21bb99`; [image-identity.json](../checks/seal-be1-local-2026-11-03/image-identity.json). Build từ working tree, không published ECR, không image BE2 đã nhận. Không suy image identity từ host JAR hash.

Thay đổi ngoài module tập trung `.gitattributes`, `.github/workflows/deploy.yml`, root Compose, năm BAT E2E và current docs. Frontend runtime/build source không đổi; không chạy lại npm/full Playwright chỉ vì đổi backend path. Không claim browser acceptance. Các script E2E chỉ được đổi module path, chưa chạy scenario reset/live.

## 7. Phần còn phụ thuộc bên ngoài

Theo [Q01–Q10](../plans/seal-be1-be2-clarifications-2026-11-03.md): current infra/deploy revision; chọn ALB direct/proxy/port/TLS/health; FE/API domains/HTTPS/CloudFront/CORS/cookie; private network/credential provider; RDS target/CA/identities; bucket/prefix/policy/queue pair; cách build/giao image; provisioning/cleanup; worker owner/window/backlog và log dispatch lỗi cũ.

FE trên S3 và API qua ALB tới private EC2 là hướng đã trao đổi. ALB-direct HTTP:8080, CloudFront và paths private thật **chưa được BE2 xác nhận**. Đã chuẩn bị template/matrix và hoàn tất phần local độc lập; chưa tự chọn hoặc apply hạ tầng.

Nghiệm thu ALB/RDS TLS/IAM/S3/SQS/file persistence sau restart/browser/model thật/FE job UI còn pending theo scope thực. Không gọi local pass là “sẵn sàng deploy AWS”. Bước tiếp theo là người dùng review candidate/bộ bằng chứng và thảo luận các đầu vào target; phát hành source revision chỉ khi có yêu cầu riêng.
