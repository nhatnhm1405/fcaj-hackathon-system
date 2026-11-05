# Issue #14: thực thi AWS PoC và trạng thái kiểm chứng

## Trạng thái mới nhất (ưu tiên phần này khi đọc các checkpoint bên dưới)

- Worktree: `.worktrees/issue-14-aws-release`, nhánh `feature/issue-14-aws-release`, dựa trên develop `308ee3570c87f40c210bc21922a81fb921441fb9`. Commit candidate `b12c2afc03bb4f3bbd9c03328041566c75d0c320`; chưa push/merge; checkout chính và các thay đổi có sẵn được giữ nguyên.
- Người dùng chốt giữ `app.autowashpro.io.vn` cho web và `api.autowashpro.io.vn` cho API. DNS authoritative là ns1.vclouddns.vn/ns2.vclouddns.vn. Không đổi nameserver. CNAME xác minh ACM vẫn chờ người dùng thêm; chuỗi xác minh không phải địa chỉ truy cập và không được rút ngắn.
- ACM us-east-1: `arn:aws:acm:us-east-1:582129522297:certificate/2816724e-24d6-4d03-adbe-4da76c3a8b32`, lần đọc cuối PENDING_VALIDATION. Certificate được request trực tiếp, chưa thuộc stack CloudFormation.
- Người dùng đã gửi ảnh `DB_IDENTITIES_READY`: tạo hai identity cho catalog `fcaj_poc` và source IP10.20.12.59; agent không nhận mật khẩu admin hoặc đọc các file private. Ảnh inspect trước đó cho thấy MySQL8.4.11/case1, TLS, catalog trống và chưa có Flyway history. Candidate image đã chạy `initialize-empty`, kiểm target/case/charset/collation/catalog trống trước V1-V3 và verify-deploy sau migration. SSM `0b3baaf7-4552-4a58-a12c-3314eaa37632` có marker MIGRATION_INITIALIZED_AND_VERIFIED; phần xác minh host trong cùng lệnh gặp lỗi được mô tả bên dưới.
- Runtime config đã tạo trên EC2 bằng helper `prepare-aws-runtime.py`, SSM command `6c5a41ee-7e33-4284-ab70-e7ea5c92a2e8` Success. Private env/settings root-only0600; Compose parsing pass. AI delivery/bootstrap admin tắt, seedNONE, DB verify-only, backend chỉ bind loopback8080. Truststore public còn cần được trích từ candidate image trước deploy.
- Inline IAM `fcaj-poc-backend-runtime` đã gắn vào role `fcaj-poc-backend-ec2-role`: pull đúng ECR repo, S3 chỉ prefix `poc/*`, backend send jobs/receive results. Access Analyzer findings rỗng. IAM simulation chi tiết cho S3 `ResourceSpecificResults` xác nhận prefixpoc allowed và ngoài prefix implicitDeny; không dùng kết quả top-level template ARN để kết luận quyền S3.
- Frontend đã build với API URL thật và upload vào `fcaj-poc-web-582129522297-ap-southeast-1`. Đã đọc lại: BucketOwnerEnforced, cả4 PublicAccessBlock=true, Versioning=Enabled, SSE-S3 AES256. index.html có no-cache/max-age0/must-revalidate, Content-Type text/html. Chưa có CloudFront/HTTPS/browser acceptance.
- FE dùng Node22.21.1:162/162 tests pass, build pass. Node26 ban đầu lỗi localStorage; không đổi mã ứng dụng để che lỗi môi trường. npm audit báo13 vulnerabilities, chưa xử lý trong phạm vi triển khai.
- Backend full H2 run:633 tests,0 failures,3 errors,9 skipped;3 errors thuộc AwsMockWorkerTest do lần đầu chỉ mount module nên thiếu fixture ở docs/contracts. Rerun với toàn worktree: AwsMockWorkerTest4/4 pass, package thành công. Đây là full run cộng rerun lớp lỗi, chưa phải một full run sạch duy nhất. MySQL8.4 lab riêng:36/36 pass,0 skipped (AI integration21, fresh deployment9, concurrency6); không kết nối RDS. Container lab được tạo riêng, không publish cổng host.
- JAR đã package SHA256 `574f2979b22ad3ee0ae1dfbf82455872bd288cdd050d4777f41d77c568f64d29`. Dockerfile.release đóng gói đúng JAR này; không chạy Maven rebuild trong image release. Docker preflight build pass: public passwordless truststore reload/so sánh118 default JRE roots và3 Singapore RDS CAs; không đưa password vào JAVA_TOOL_OPTIONS. Preflight image chưa có release revision và chưa được dùng triển khai.
- Deployment/SSM Node tests22/22, DB helper Python8/8 (Windows và EC2), Compose examples, Bash syntax và git diff --check pass. Test mới tái hiện lỗi candidate image chỉ được export ở shell con của gate; host gọi Compose lần sau thiếu biến. Đã thấy test đỏ, thêm export ở host, rồi cả22 tests xanh; không đổi schema để vượt gate. CloudFormation validate-template chỉ kiểm frontend-certificate draft; chưa chạy cfn-lint/cfn-guard/actionlint.
- ECR image đã phát hành: `582129522297.dkr.ecr.ap-southeast-1.amazonaws.com/fcaj-poc-backend@sha256:ef13022014d3dc29454825074078e72948860c72114019400024c8a1fb1734eb`, tag bất biến `sha-b12c2afc03bb4f3bbd9c03328041566c75d0c320-operator-20261105`. SHA256 JAR bên trong image khớp artifact đã test. Image release trước bản sửa host runner; commit runtime vẫn là b12c2af, không gán SHA của bản sửa host vào runtime.
- SSM retry bằng driver đã sửa: `b9e52cf0-f00c-4c61-8306-2ad3e4384961`, Success, verifiedAt `2026-11-05T16:27:03.195Z`. Kiểm Config.Image, image ID, /health/ready và /health/version pass. /health/live cũng trả200/UP. Không chạy initialize-empty ở retry; private settings vẫn verify-only. Container quan sát không restart, startup21.626s. Đây là deploy operator qua SSM, chưa phải GitHub Actions run.
- AWS CLI trên EC2 là wrapper `/usr/local/bin/aws` dùng image chính thức `public.ecr.aws/aws-cli/aws-cli@sha256:d557b3524475d70dd3104cf44d96568ee3188897b73a6ece2b37aa50e1af362f` (2.39.3), network host, không mount credentials. STS xác minh role `fcaj-poc-backend-ec2-role`. Không đưa credential local lên EC2. ECR login token theo cơ chế Docker hiện nằm root-only trên host và có hạn12h; chưa cài credential helper.
- Chưa có CloudFront/custom HTTPS, GitHub OIDC release, browser E2E hoặc SQS MOCK round trip. Caddy chưa được cài/chạy. Issue#14 đang closed trên GitHub nhưng chưa đủ bằng chứng nghiệm thu theo checklist của issue.

### Người dùng đang tìm trang nhập DNS

Đối chiếu [hướng dẫn OnePortal](https://developers.inet.vn/docs/oneportal/quan-ly-ban-ghi-dns): chọn nút Bản ghi trên dòng domain hoặc Cập nhật bản ghi trong trang chi tiết. Cập nhật DNS là thay nameserver, không dùng cho thao tác này. Nếu domain thuộc OneShield, nút Bản ghi chuyển sang trang OneShield; có thể thử `https://portal.inet.vn/list-service/one-shield/dns/autowashpro.io.vn` hoặc mở danh sách OneShield/DNS Proxy rồi chọn domain. Chưa xác minh được giao diện tài khoản người dùng hoặc xác nhận record đã thêm. Cần chọn Chỉ DNS/tắt proxy cho CNAME ACM nếu giao diện có công tắc Bảo vệ; không đổi chế độ bảo vệ toàn domain.

Các mục bên dưới là checkpoint theo thời gian; câu “chưa tạo/chờ” trong checkpoint cũ được cập nhật bởi trạng thái mới nhất ở trên. Không dùng chúng như snapshot AWS hiện tại.

Ngày 05/11/2026, Asia/Saigon. Nhánh `feature/issue-14-aws-release`, dựa trên develop `308ee3570c87f40c210bc21922a81fb921441fb9`. Các thay đổi dưới đây đang local, chưa commit/push/merge hoặc triển khai ứng dụng. Giữ nguyên checkout develop cũ và thay đổi của người dùng.

## Đã chuẩn bị trong repo

- `deploy-verified.sh` hỗ trợ `DEPLOY_IMAGE=ECR_URI@sha256:...`, pull và kiểm OCI revision trước chạy DB gate; cùng image dùng cho CLI migration và backend. Vẫn giữ local-build mode cho operator.
- Dockerfile đóng gói deploy gate để SSM trích từ image; host không cần git checkout/build lại source.
- Workflow `aws-poc-release.yml`: backend H2/MySQL lab, FE test/build đúng API domain, build/push ECR bằng OIDC, SSM deploy và lưu manifest.
- SSM runner giới hạn đúng EC2/account/repo, kiểm digest/container image ID, health và revision. Manifest không chứa stdout/stderr của SSM hoặc env private. Failure trước DB gate không thay backend; failure sau khi đã thay container cần operator xử lý, không có rollback DB tự động.
- Template certificate frontend dùng CloudFormation ở us-east-1, DNS validation bên ngoài. Chưa tạo stack hoặc certificate.

Workflow chỉ release từ develop khi repository variable `AWS_POC_RELEASE_ENABLED=true`; để trống/false đến khi mọi điều kiện dưới đây được kiểm. Đây chưa phải pipeline đã chạy thành công.

## Điều kiện trước bật release

1. Tạo role OIDC/policies có scope đúng; EC2 role có quyền pull ECR, S3 prefix và queue theo matrix BE1. Workflow dùng GitHub environment `aws-poc`: OIDC `sub` phải là `repo:nhatnhm1405/fcaj-hackathon-system:environment:aws-poc`, `aud=sts.amazonaws.com`. Áp deployment branch rule chỉ cho develop trong environment. Không dùng trust subject branch cùng lúc với environment, vì GitHub token sẽ mang subject environment. IAM policy/role và environment chưa được tạo trong lượt này.
2. Đặt `AWS_POC_DEPLOY_ROLE_ARN` ở repository/environment variable. Không dùng access keys dài hạn. Tùy chọn release-enabled dùng repository variable vì job-level `if` được đánh giá trước environment.
3. Provision private files trên EC2: runtime.env, migration.env, compose và deploy-settings. Không đưa secret vào repo, logs hoặc chat; cấu hình chứa giá trị thật chỉ do operator nhập trực tiếp hoặc resolver đã review. Chưa có automation secret resolver trong PR này.
4. Xử lý truststore launcher cùng BE1. Không đưa password bí mật vào JAVA_TOOL_OPTIONS/JDK_JAVA_OPTIONS có thể auto-echo. Template cũ còn gợi ý cách truyền này; không bật release và thu startup logs trước khi cấu hình được sửa/review.
5. Kiểm schema RDS thật bằng kết nối TLS. Nếu cần initialize-empty, operator làm rõ ràng bằng đúng candidate image sau khi xác minh catalog trống. CI mới chỉ cho verify-only; không reset/clean/baseline để vượt gate.
6. Compose loopback, Caddy/API HTTPS và domain phải được cấu hình. FE artifact được build; workflow chưa upload S3/invalidate CloudFront. Hosting FE và worker MOCK vẫn là bước cần làm, không coi source có workflow là nghiệm thu Issue #14.

Không push main để thử: workflow SSH cũ vẫn trigger từ main. Nút workflow_dispatch chỉ có theo cơ chế GitHub khi workflow hiện diện trên default branch; không đổi main chỉ để có nút chạy.

## Chủ tài khoản chỉ cần thao tác phần riêng tư/quyết định dữ liệu

- Đăng nhập/2FA AWS hoặc iNET khi phiên yêu cầu. Không gửi mật khẩu, token hoặc access key cho agent.
- Người dùng xác nhận có mật khẩu admin RDS và sẽ nhập trực tiếp trong phiên riêng. API metadata hiện MasterUserSecret=null, chỉ có nghĩa RDS chưa công bố master secret do dịch vụ quản lý, không kết luận account không có secret lưu riêng.
- Xác nhận phương án init/upgrade nếu kiểm SQL thấy DB có schema/dữ liệu. Agent chưa query SQL hoặc đổi DB.
- Thêm DNS records do agent cung cấp sau khi tài nguyên đích/certificate được tạo. DNS authoritative hiện ns1.vclouddns.vn/ns2.vclouddns.vn, dù domain được người dùng quản lý tại portal.inet.vn. Cần xác nhận giao diện DNS iNET có cập nhật hệ thống authoritative đó trước khi nhập; không đổi nameservers.

EC2 public IPv4 kiểm lại là `52.77.54.249`; record dự kiến `A api -> 52.77.54.249` sau khi chốt địa chỉ. Chưa xác minh đây là Elastic IP, nên không coi địa chỉ này được giữ qua stop/start. CNAME app và validation ACM chưa có giá trị thực; không nhập placeholder.

## Kiểm tra đã chạy

- 21/21 test deployment gate/SSM driver pass, gồm tag mutable, sai revision, lỗi pull, lỗi gate, sai EC2, command injection và lọc private output khỏi manifest.
- Bash syntax cho gate và host runner pass.
- Compose parsing hai mẫu loopback/ALB-direct bằng dữ liệu giả pass; missing interface bị từ chối. Không chạy container/app khi kiểm này.
- AWS CloudFormation validate-template cho frontend-certificate.yaml pass. Chưa có cfn-lint/cfn-guard trong các công cụ đã kiểm; chưa chạy hai lớp kiểm này hoặc actionlint. Không coi validate-template là kiểm đủ security/resource schema.
- Git whitespace check pass. Chưa chạy lại full backend/FE/MySQL lab hoặc browser E2E trong lượt sửa deployment này; workflow sẽ chạy chúng sau khi được phát hành.

Ở lượt chuẩn bị pipeline, AWS chỉ được đọc: STS, EC2, RDS metadata và CloudFormation validate-template. Chưa tạo IAM/certificate/CloudFront, sửa DNS, lấy secret, chạy SQL, deploy container hoặc phát sinh tài nguyên mới.

## Cập nhật: bước người dùng nhập mật khẩu

- EC2 có MySQL client 8.0.46 và đường TCP đến RDS hoạt động. Master username là `fcaj_admin`.
- Đã chuẩn bị public Singapore CA bundle tại `/etc/seal/rds-ca-bundle.pem` và helper metadata-only `/usr/local/sbin/fcaj-inspect-rds-20261105.sh` trên EC2 qua SSM. Không cài thêm packages hoặc chạy SQL. Không ghi đè cấu hình riêng của người dùng.
- Lần đầu kiểm tra helper vướng option `--no-login-paths` không được client hỗ trợ. Đã thay bằng `MYSQL_TEST_LOGIN_FILE=/dev/null`, bỏ MYSQL_PWD khỏi môi trường client và giữ `--no-defaults`; cập nhật helper sau khi đối chiếu checksum bản vừa tạo. SSM command `6ca41a3f-3363-4ac7-b7fa-45d39492ac1f` thành công, xác nhận helper sẵn sàng; đây không phải bằng chứng login/SQL đã thành công.
- CA SHA256 `3c696020a3b7c6721085d182211c28024ab01873ade35dcc7eeebb89c20ee979`; helper SHA256 `3e7f763d8c56e921b8435757b86ff5954978e8a9d69a128da3ad55f974d2a0db`.
- Người dùng mở EC2 -> Connect -> Session Manager và chạy `sudo /usr/local/sbin/fcaj-inspect-rds-20261105.sh`. Client hỏi mật khẩu bằng prompt ẩn. Script dùng VERIFY_IDENTITY và transaction READ ONLY, chỉ lấy phiên bản/case/TLS, metadata catalog/objects và trạng thái Flyway; không đọc dữ liệu nghiệp vụ hoặc chạy migration.
- Chờ output metadata trước khi kết luận DB trống/current hoặc đề nghị init/upgrade. Agent chưa truy cập mật khẩu hoặc chạy SQL trên RDS.

## Cập nhật: bằng chứng DB trống và helper tạo identities

Người dùng đã chạy inspect helper và gửi ảnh kết quả ngày05/11. Ảnh hiển thị MySQL8.4.11, lower_case_table_names=1, TLS_AES_256_GCM_SHA384, catalog fcaj_poc với utf8mb4/utf8mb4_0900_ai_ci. Hai truy vấn TABLES không trả dòng; routines/triggers/events đều0, FLYWAY_HISTORY_ABSENT. Đây là bằng chứng từ phiên riêng của người dùng tại thời điểm chụp; agent chưa tự đăng nhập SQL hoặc đọc mật khẩu.

Đã chuẩn bị `Test/provision-aws-db-users.py` và cài tại `/usr/local/sbin/fcaj-provision-db-users-20261105.py` trên EC2. SSM install command `088a5853-4880-47e0-b9d7-127448675619` thành công. SHA256 helper `852caa95465ad1e6f618d875a0328ead856328c36a7cea93b38a78d0822bc6d6`. Đã chạy8 tests mock trên Windows và Linux EC2; chưa thực thi provisioning thật. Tests kiểm guard DB sai/không trống, quyền runtime bị giới hạn, xử lý underscore trong GRANT, không ghi đè config và không lộ raw SQL/password trong lỗi.

Người dùng tự chạy bằng `sudo python3 /usr/local/sbin/fcaj-provision-db-users-20261105.py` trong Session Manager và nhập mật khẩu admin qua getpass. Tác động khi chạy:

- Kiểm lại engine/case/charset/collation và catalog vẫn trống trước thay đổi.
- Tạo fcaj_runtime và fcaj_migration cho đúng source IP10.20.12.59, REQUIRE SSL. Runtime SELECT/INSERT/UPDATE/DELETE; migration thêm CREATE/ALTER/INDEX/REFERENCES phục vụ migrations hiện tại. Không GRANT OPTION/global admin/DROP hoặc đổi mật khẩu identity đã có.
- Giới hạn catalog fcaj_poc; kiểm partial_revokes để escape underscore đúng theo MySQL GRANT. Không đọc dữ liệu nghiệp vụ, tạo application tables hoặc chạy migrations.
- Sinh mật khẩu ứng dụng riêng và giữ state/config dưới `/etc/seal/db-identities/` root-only. Mật khẩu admin chỉ tồn tại trong tiến trình/file option tạm0600 được xóa sau mỗi subprocess, không lưu vào state. Không đọc/in các file secret này qua agent.
- Nếu bước giữa chừng lỗi: giữ state private để chạy lại cùng helper, không tự reset users hoặc schema. Helper chỉ retry được khi catalog vẫn trống; lỗi phải được đối chiếu trước thao tác khác.

Chờ dòng `DB_IDENTITIES_READY` từ người dùng trước coi identities đã được tạo. Sau đó còn cấu hình CA cho JVM, private runtime đầy đủ, IAM/ECR, migration từ candidate image, frontend/HTTPS và SQS MOCK; chưa gọi app đã chạy hoặc Issue#14 hoàn tất.
