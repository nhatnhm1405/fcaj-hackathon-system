# Runbook deploy SEAL theo sơ đồ "SEAL AWS v3" — account trống

Cập nhật 25/11/2026. Nguồn: `aws-architect.drawio`, `origin/main` `7e017fb`, `origin/develop` `237d654`.
Quyết định: dựng ở **account riêng, trống, dùng credit**; không đụng PoC đang chạy ở account khác.
Trạng thái: mục 1–9 và AI worker, alarm, CloudTrail, WAF (Count) ở mục 11 đã chạy trên AWS ngày 24–25/11/2026. **Chưa chạy:** GitHub Actions, OAuth, SES. Không ghi account ID, endpoint, secret thật vào file này; lưu ở `docs/vq/` (đã ignore).

## 0. Đọc trước

### 0.1 Các stack

| Thứ tự | Template | Region | Nội dung | Tính tiền |
|---|---|---|---|---|
| 1 | `infra/cloudformation/poc.yaml` | Singapore | S3 data, SQS + DLQ, ECR, log group | Rất thấp |
| 2 | `infra/v3/base.yaml` | Singapore | VPC, RDS, S3 web, IAM, chứng chỉ ALB | RDS theo giờ |
| 3 | `infra/v3/runtime.yaml` | Singapore | NAT, ALB, Auto Scaling | **Phần đắt; bật/tắt được** |
| 4 | `infra/v3/edge.yaml` | **us-east-1** | CloudFront, WAF, chứng chỉ web | WAF ~9 USD/tháng |
| 5 | `infra/v3/ai-worker.yaml` | Singapore | Lambda AI worker | Theo lượt |
| 6 | `infra/v3/monitoring.yaml`, `audit.yaml`, `cicd.yaml` | Singapore | Alarm/SNS, CloudTrail, quyền GitHub Actions | Rất thấp |

**Database:** nguồn chuẩn là `seal_hackathon.bak` (code-first Hibernate, 31 bảng, 627 dòng, SHA-256 `ab6279c0…f23802`). Không chạy `back-end/database scripts/*.sql`: bản cũ thiếu `mentorsupportrequest`, `hackathonevent.topic`, ba cột `notification` và thừa `accountapproval`. Không tạo/seed tài khoản ứng dụng: dump đã có 62 tài khoản, gồm 1 SYSTEM_ADMIN đang hoạt động. Flyway V1 chính là schema entity; chỉ đánh dấu baseline, sau đó thêm V2 (AI job) và V3 (cột version).

Tên `fcaj-poc-*` được giữ để khớp code và template có sẵn, dù account mới.

### 0.2 Chi phí

Đơn giá Singapore đọc từ AWS Price List ngày 24/11/2026: NAT 0,059/h; ALB 0,0252/h; IPv4 0,005/h mỗi địa chỉ (NAT 1, ALB 2); t3.small 0,0264/h; RDS MySQL db.t4g.micro 0,025/h; EBS gp3 0,096/GB-tháng; WAF 5 + 1/rule. Storage RDS lấy 0,138/GB-tháng theo engine khác.

| Cách chạy | Ước tính USD/tháng | 200 USD dùng được |
|---|---|---|
| 24/7, 2 EC2 | ~147 | ~40 ngày |
| 24/7, 1 EC2 | ~126 | ~47 ngày |
| 8h × 22 ngày, 2 EC2 (xóa runtime khi nghỉ) | ~58 | ~3 tháng |
| **4h × 20 ngày, 1 EC2 (khuyên dùng khi học)** | **~41** | **~4–5 tháng** |

Chưa gồm traffic, thuế, domain, DeepSeek (trả bên ngoài AWS). Quên tắt runtime một tuần với 2 EC2 mất khoảng 26 USD.

### 0.3 Free plan hay Paid plan

Xem ở Billing → Account plan. Theo tài liệu AWS, Free plan:

- Hết 6 tháng **hoặc** hết credit, cái nào đến trước, thì **account đóng**; dữ liệu giữ 90 ngày để nâng cấp. Paid plan không đóng, mà tính tiền phần vượt credit.
- EC2 tối đa cỡ `t3.small`/`c7i-flex.large`…; RDS chỉ `db.t3.micro`/`db.t4g.micro`, **Single-AZ**. Template mặc định theo mức này.
- Sơ đồ ghi Multi-AZ và NAT thứ hai là "contest season", nên bản Single-AZ, 1 NAT vẫn đúng sơ đồ ngày thường.

Chưa xác nhận Free plan cho phép mọi dịch vụ ở đây (NAT, ALB, WAF, custom parameter group). Nếu stack báo lỗi quyền gói, dừng và cân nhắc nâng Paid plan; nâng xong sẽ bị tính tiền thật khi hết credit.

### 0.4 Khác biệt so với sơ đồ, template đã xử lý

| Vấn đề nếu làm nguyên văn | Cách xử lý |
|---|---|
| Chỉ route `/api/*` làm hỏng OAuth, avatar | Thêm `/oauth2/authorization/*`, `/login/oauth2/*`, `/uploads/*` |
| `/oauth2/*`, `/login/*` che trang React | Không route rộng như vậy |
| Rule 403/404 → `index.html` nuốt lỗi API | CloudFront Function chỉ cho S3 |
| Backend chỉ nghe HTTP 8080 | nginx 8443 → loopback 8080 |
| OAuth giữ state trong RAM | Stickiness ở ALB |
| WAF chặn upload 20MB | `SizeRestrictions_BODY` luôn Count |
| Lambda 180s > visibility 120s | Tham số `JobVisibilityTimeout=1080` |

### 0.5 Còn phải sửa code

| Việc | Ảnh hưởng nếu chưa sửa |
|---|---|
| `EmailService` dùng `MAIL_USERNAME` làm From | SES từ chối; tạm dùng SMTP thường |
| Backend chỉ nhận `mode=MOCK`, không có message FAILED từ Lambda | Kết quả AI ghi MOCK; job lỗi kẹt pending |
| Quota AI, OTP và tái dùng kết quả theo SHA chưa có; `GitHubSourceReader` không gửi `GITHUB_TOKEN` | Cả hai EC2 chung một IP NAT và giới hạn 60 req/h của GitHub; mỗi job dùng 2 request, tối đa khoảng 30 job/giờ |
| `aws-poc-release.yml`, `deploy.yml` gắn cứng account và instance PoC | Dùng `aws-v3-release.yml` (mục 11) hoặc deploy tay (mục 10) |

## 1. Account (làm trước)

1. Gõ `n` ở câu hỏi của `aws login`, rồi `aws logout`. Bật MFA cho root, sau đó không dùng root nữa.
2. Billing → Budgets: cost budget, cảnh báo 25/50/75/90% của 200 USD, **bỏ chọn "credits"** khi tính cost, nếu không biểu đồ luôn là 0. Cảnh báo không tự chặn chi tiêu.
3. IAM → user `seal-admin`: Console access, `AdministratorAccess`, `SignInLocalDevelopmentAccess`, MFA.
4. Đăng nhập lại; ARN phải kết thúc bằng `user/seal-admin`:

```powershell
aws login
```

```powershell
aws sts get-caller-identity --query "[Account,Arn]" --output text
```

## 2. Domain `sealaws.io.vn` (iNET)

Domain mua riêng cho v3 nên giao toàn bộ DNS cho Route 53. Ngày 24/11/2026 domain đã hoạt động, nameserver là `hoalu`/`ninhbinh.vclouddns.com` của iNET. Chỉ tạo hosted zone **một lần**; tạo lần hai sẽ ra bộ nameserver khác.

```powershell
aws route53 create-hosted-zone --name sealaws.io.vn --caller-reference seal-v3-1
```

Ghi lại `HostedZone.Id` (`<ZONE_ID>` là phần `Z…` sau `/hostedzone/`) và 4 dòng `NameServers`.

iNET → quản lý tên miền → `sealaws.io.vn` → đổi **Nameserver** (không phải thêm bản ghi DNS) → thay bằng đủ 4 tên `ns-….awsdns-….` → lưu. Các bản ghi đặt ở DNS iNET sẽ không còn hiệu lực. Chờ lệnh sau trả đúng 4 tên `awsdns` (vài phút tới 24 giờ) rồi mới đi tiếp:

```powershell
nslookup -type=NS sealaws.io.vn 8.8.8.8
```

## 3. Stack dữ liệu và base

Chạy ở thư mục gốc của repo có `infra/v3`.

```powershell
aws cloudformation deploy --region ap-southeast-1 --stack-name fcaj-poc --template-file infra/cloudformation/poc.yaml --parameter-overrides JobVisibilityTimeout=1080
```

```powershell
aws ec2 describe-managed-prefix-lists --region ap-southeast-1 --filters Name=prefix-list-name,Values=com.amazonaws.global.cloudfront.origin-facing --query "PrefixLists[0].PrefixListId" --output text
```

```powershell
aws cloudformation deploy --region ap-southeast-1 --stack-name fcaj-v3-base --template-file infra/v3/base.yaml --capabilities CAPABILITY_IAM --parameter-overrides DomainName=sealaws.io.vn HostedZoneId=<ZONE_ID> CloudFrontPrefixListId=<PL_ID>
```

Khoảng 10–20 phút. Treo ở `RegionalCertificate` = nameserver ở iNET chưa đổi đúng. Từ đây RDS bắt đầu tính tiền.

```powershell
aws cloudformation describe-stacks --region ap-southeast-1 --stack-name fcaj-v3-base --query "Stacks[0].Outputs[].[OutputKey,OutputValue]" --output table
```

## 4. Image backend

Bật Docker Desktop. Working tree của `back-end/src/seal-api` phải sạch. PowerShell 7 hoặc Git Bash.

Build từ `develop` mới nhất (đã bỏ Gemini):

```powershell
git merge --ff-only origin/develop
```

```powershell
$SHA = git rev-parse HEAD; $REPO = aws cloudformation describe-stacks --region ap-southeast-1 --stack-name fcaj-poc --query "Stacks[0].Outputs[?OutputKey=='BackendRepositoryUri'].OutputValue" --output text
```

```powershell
docker build --platform linux/amd64 --provenance=false --label "org.opencontainers.image.revision=$SHA" -t "${REPO}:sha-$SHA" back-end/src/seal-api
```

Token ECR dài hơn giới hạn 2560 byte của Windows Credential Manager, nên dùng cấu hình Docker tạm và xóa sau khi push:

```powershell
$DC = "$env:TEMP\seal-ecr"; New-Item -ItemType Directory -Force $DC | Out-Null; Set-Content "$DC\config.json" ('{"auths":{"' + $REPO.Split('/')[0] + '":{}}}') -Encoding ascii
```

```powershell
cmd /c "aws ecr get-login-password --region ap-southeast-1 | docker --config $DC login --username AWS --password-stdin $($REPO.Split('/')[0])"
```

```powershell
docker --config $DC push "${REPO}:sha-$SHA"; Remove-Item -Recurse -Force "$env:TEMP\seal-ecr"
```

```powershell
$DIGEST = aws ecr describe-images --region ap-southeast-1 --repository-name fcaj-poc-backend --image-ids "imageTag=sha-$SHA" --query "imageDetails[0].imageDigest" --output text; "$REPO@$DIGEST $SHA"
```

Dòng cuối là giá trị `release` ở mục 5.

## 5. Parameter Store

Chạy script (tự sinh mật khẩu, không in ra màn hình, không ghi đè tham số đã có); thêm `-DryRun` để xem trước:

```powershell
.\infra\v3\put-parameters.ps1 -Domain sealaws.io.vn -Release "<dòng cuối mục 4>"
```

Không chép secret vào chat/Git. Giá trị script tạo ra:

| Tên | Loại |
|---|---|
| `/fcaj/v3/backend/runtime-env` | SecureString, dưới 4KB |
| `/fcaj/v3/backend/migration-env` | SecureString |
| `/fcaj/v3/backend/release` | String, dòng ở mục 4 |
| `/fcaj/v3/edge/origin-secret` | SecureString, dùng ở mục 6 và 8 |
| `/fcaj/v3/worker/deepseek-api-key` | SecureString, tự nhập khi làm AI |

`runtime-env` (không đặt dấu nháy; `<DB>` = output `DatabaseAddress`):

```text
SPRING_PROFILES_ACTIVE=aws
SPRING_DATASOURCE_URL=jdbc:mysql://<DB>:3306/seal_hackathon?sslMode=VERIFY_IDENTITY&serverTimezone=UTC&connectTimeout=5000&socketTimeout=30000
SPRING_DATASOURCE_USERNAME=fcaj_v3_runtime
SPRING_DATASOURCE_PASSWORD=<RUNTIME_PASSWORD>
JWT_SECRET=<base64 của ít nhất 32 byte ngẫu nhiên>
JWT_COOKIE_SECURE=true
APP_FRONTEND_URL=https://sealaws.io.vn
CSRF_COOKIE_DOMAIN=
SEED_SCENARIO=NONE
BOOTSTRAP_ADMIN_ENABLED=false
AWS_REGION=ap-southeast-1
APP_STORAGE_BUCKET=fcaj-poc-data-<ACCOUNT_ID>-ap-southeast-1
APP_STORAGE_PREFIX=poc
APP_AI_JOBS_ENABLED=false
APP_AI_JOBS_DELIVERY_ENABLED=false
APP_AI_JOBS_JOBS_QUEUE_URL=https://sqs.ap-southeast-1.amazonaws.com/<ACCOUNT_ID>/fcaj-poc-ai-jobs
APP_AI_JOBS_RESULTS_QUEUE_URL=https://sqs.ap-southeast-1.amazonaws.com/<ACCOUNT_ID>/fcaj-poc-ai-results
```

`migration-env`:

```text
BE1_MIGRATION_JDBC_URL=<giống hệt SPRING_DATASOURCE_URL>
BE1_MIGRATION_USERNAME=fcaj_v3_migration
BE1_MIGRATION_PASSWORD=<MIGRATION_PASSWORD>
```

## 6. Runtime (bật phần tính tiền theo giờ)

Lấy `<ORIGIN_SECRET>` vào biến, không in ra:

```powershell
$ORIGIN = aws ssm get-parameter --region ap-southeast-1 --with-decryption --name /fcaj/v3/edge/origin-secret --query Parameter.Value --output text
```

```powershell
aws cloudformation deploy --region ap-southeast-1 --stack-name fcaj-v3-runtime --template-file infra/v3/runtime.yaml --parameter-overrides DomainName=sealaws.io.vn HostedZoneId=<ZONE_ID> OriginSecret=$ORIGIN
```

Lần đầu máy sẽ báo lỗi ở bước release vì DB chưa có user. **Đúng như dự kiến**; stack vẫn hoàn tất.

## 7. Nạp database từ `seal_hackathon.bak` (một lần)

Dump chứa email và hash mật khẩu: **không đưa vào Git** (repo public), không gửi qua chat.

1. Từ máy cá nhân, đưa file lên bucket private:

```powershell
aws s3 cp "$env:USERPROFILE\Downloads\seal_hackathon.bak" s3://fcaj-poc-data-<ACCOUNT_ID>-ap-southeast-1/poc/restore/seal_hackathon.bak
```

2. Đưa script nạp lên cùng chỗ:

```powershell
aws s3 cp infra/v3/restore-db.sh s3://fcaj-poc-data-<ACCOUNT_ID>-ap-southeast-1/poc/restore/restore-db.sh
```

3. EC2 → `fcaj-v3-api` → Connect → Session Manager:

```bash
sudo /snap/bin/aws s3 cp s3://fcaj-poc-data-<ACCOUNT_ID>-ap-southeast-1/poc/restore/restore-db.sh /root/restore-db.sh
```

```bash
sudo bash /root/restore-db.sh
```

Script kiểm checksum, hỏi mật khẩu `fcaj_admin` (Secrets Manager, output `MasterSecretArn`), chỉ chạy khi database trống, nạp dump, tạo hai login MySQL `fcaj_v3_runtime`/`fcaj_v3_migration` (mật khẩu lấy từ Parameter Store), rồi baseline V1, thêm V2/V3 và khởi động backend. Không dùng `fcaj_admin` cho app vì AWS tự xoay mật khẩu đó và nó có toàn quyền.

4. Cần thấy `RESTORE_OK 31 tables` rồi `RELEASE_OK`. Nếu dừng sau khi đã migrate, chạy lại `sudo /usr/local/bin/seal-release`, không chạy script lần hai.

5. Sau khi đăng nhập thử được: xóa `/etc/seal/seal_hackathon.bak` và `/root/restore-db.sh` trên EC2, object S3 **cùng mọi version**; giữ bản gốc ở máy bạn.

Đã diễn tập ngày 24/11/2026 trên MySQL 8.4.13 tạm ở máy: nạp 31 bảng, baseline + 2 migration, `verify-deploy` PASS với đúng quyền ở trên; baseline lần hai bị từ chối. Đã chạy trên RDS cùng ngày.

Lưu ý dữ liệu: 45/62 tài khoản đang `is_active=0`; đăng nhập bằng mật khẩu cũ. 61 email thuộc `fpt.edu.vn`, nên **đừng bật SMTP/SES** trước khi chắc không gửi nhầm cho người thật.

| Lỗi | Nguyên nhân |
|---|---|
| Máy không hiện ở Session Manager | Chờ thêm 5 phút; kiểm NAT route |
| `AccessDenied` ssm/ecr | Sai tên parameter |
| `Access denied for user`, timeout 3306 | Sai user/mật khẩu hoặc SG |
| `verify-deploy` từ chối | Không reset/baseline; gửi log (không kèm env) để xem |

## 8. Frontend và edge

```powershell
cd front-end/src/seal-web; npm ci; $env:VITE_API_URL='https://sealaws.io.vn'; npm run build
```

```powershell
aws s3 sync dist s3://fcaj-v3-web-<ACCOUNT_ID>-ap-southeast-1 --exclude index.html --cache-control "public,max-age=31536000,immutable"
```

```powershell
aws s3 cp dist/index.html s3://fcaj-v3-web-<ACCOUNT_ID>-ap-southeast-1/index.html --cache-control "no-cache,max-age=0,must-revalidate"
```

Về thư mục gốc repo, deploy edge (`<WEB_BUCKET_DOMAIN>` = output `WebBucketDomain`). Thêm `EnableWaf=false` nếu muốn để WAF tới lúc nghiệm thu:

```powershell
aws cloudformation deploy --region us-east-1 --stack-name fcaj-v3-edge --template-file infra/v3/edge.yaml --parameter-overrides DomainName=sealaws.io.vn HostedZoneId=<ZONE_ID> WebBucketDomain=<WEB_BUCKET_DOMAIN> OriginSecret=$ORIGIN
```

WAF bắt đầu ở Count. Xem sampled requests sau một vòng test rồi mới thêm `WafMode=Block`.

## 9. Nghiệm thu

```powershell
curl.exe -si https://sealaws.io.vn/api/khong-ton-tai
```

Phải là JSON 401/404, không phải HTML 200.

| Luồng | Kiểm | Đạt khi |
|---|---|---|
| ①② | F5 tại `/`, `/login`, `/dashboard`, `/oauth2/redirect` | Trang React |
| ③ | `https://sealaws.io.vn/api/csrf` | 204, cookie `XSRF-TOKEN` Secure |
| ③ | `https://origin.sealaws.io.vn/` từ máy cá nhân | Timeout |
| ④ | Đăng nhập bằng tài khoản trong dump, tạo/sửa dữ liệu | Không 403 |
| ⑤ | Upload avatar và đề ~15MB, refresh instance | File còn |
| ⑥–⑫⑯ | Một AI job (mục 11) | SUCCEEDED, DLQ 0 |
| ⑭ | Google và GitHub, mỗi loại 10 lần | Không lỗi state |
| HA | Đặt 2 máy (dưới), terminate 1 máy khi đang dùng | Không gián đoạn |

Khi máy đầu healthy, chuyển sang 2 máy:

```powershell
aws cloudformation deploy --region ap-southeast-1 --stack-name fcaj-v3-runtime --template-file infra/v3/runtime.yaml --parameter-overrides DesiredCapacity=2 HealthCheckType=ELB
```

## 10. Phát hành bản mới và rollback

Làm lại mục 4, cập nhật `/fcaj/v3/backend/release`, rồi:

```powershell
aws ssm send-command --region ap-southeast-1 --document-name AWS-RunShellScript --targets "Key=tag:SealRole,Values=api" --max-concurrency 1 --max-errors 0 --parameters "commands=/usr/local/bin/seal-release"
```

Frontend: lặp build và sync, sau đó:

```powershell
aws cloudfront create-invalidation --distribution-id <DISTRIBUTION_ID> --paths "/" "/index.html"
```

Rollback = ghi lại `release` cũ và gửi lại lệnh. Không rollback schema.

## 11. AI worker, OAuth, email, alarm

1. **Lambda**: tự nhập key vào `/fcaj/v3/worker/deepseek-api-key` (SecureString, Console). Đóng gói từ `develop`, rồi deploy (Python 3.12, 512MB, timeout 180s, không VPC, tối đa 2 job cùng lúc):

```powershell
$AISHA = git rev-parse HEAD; git archive --format=zip -o "$env:TEMP\ai-worker-$AISHA.zip" HEAD:ai-worker src
```

```powershell
aws s3 cp "$env:TEMP\ai-worker-$AISHA.zip" "s3://fcaj-poc-data-<ACCOUNT_ID>-ap-southeast-1/lambda/ai-worker-$AISHA.zip"
```

```powershell
aws cloudformation deploy --region ap-southeast-1 --stack-name fcaj-v3-ai-worker --template-file infra/v3/ai-worker.yaml --capabilities CAPABILITY_IAM --parameter-overrides "CodeKey=lambda/ai-worker-$AISHA.zip"
```

`deepseek-chat` bị DeepSeek ngừng từ 24/07/2026, nên dùng `deepseek-flash`. Model mới bật thinking mặc định, token suy nghĩ tính vào giới hạn output; `ai_grader.py` gửi `"thinking": {"type": "disabled"}` (bản phải có dòng này). Sau đó bật hai cờ `APP_AI_JOBS_*ENABLED` trong `runtime-env` và chạy mục 10. Source bài thi gửi sang DeepSeek cần được ban tổ chức đồng ý.

2. **OAuth**: tạo app riêng, callback `https://sealaws.io.vn/login/oauth2/code/google` và `/github`; thêm client ID/secret và `SPRING_PROFILES_ACTIVE=aws,oauth-google,oauth-github`.
3. **SES**: chỉ sau khi sửa code; verify DKIM, xin production access, host `email-smtp.ap-southeast-1.amazonaws.com:587`.
4. **CloudWatch/Budgets → SNS email**: `infra/v3/monitoring.yaml` (stack `fcaj-v3-monitoring`, 7 alarm và 1 budget). Email phải bấm xác nhận thì mới nhận cảnh báo. Runtime tạo lại thì ALB/target group đổi tên: deploy lại với `LoadBalancerName` (phần `app/...` của ARN) và `TargetGroupName` (phần `targetgroup/...`) mới. Trước khi xoá runtime, deploy với hai giá trị rỗng để không báo động nhầm.
5. **CloudTrail → S3**: deploy lại `fcaj-poc` (quyền ghi `cloudtrail/`, tự xoá sau 90 ngày) rồi mới deploy `infra/v3/audit.yaml` (stack `fcaj-v3-audit`).
6. **GitHub Actions**: deploy `infra/v3/cicd.yaml` (stack `fcaj-v3-cicd`, ap-southeast-1, `CAPABILITY_NAMED_IAM`, tham số `DistributionId`). Trên GitHub tạo environment `aws-v3` chỉ cho nhánh `main`, và hai repository variable `AWS_V3_ROLE_ARN`, `AWS_V3_DISTRIBUTION_ID`. Workflow `aws-v3-release.yml` chạy khi có code mới vào `main`: test, đẩy image, đổi `release`, Run Command từng máy, frontend, invalidation; backend lỗi thì tự quay lại bản trước. Không tự migrate schema, không deploy Lambda.

## 12. Tắt và bật lại

Hết buổi, xóa runtime (NAT, ALB, EC2). Dữ liệu nằm ở base/poc nên không mất. Frontend vẫn mở được, API trả lỗi 502/504:

```powershell
aws cloudformation delete-stack --region ap-southeast-1 --stack-name fcaj-v3-runtime
```

Nghỉ vài ngày thì dừng RDS (tự bật lại sau 7 ngày):

```powershell
aws rds stop-db-instance --region ap-southeast-1 --db-instance-identifier fcaj-v3-mysql
```

Bật lại: start RDS, rồi chạy lệnh deploy ở mục 6 với `DesiredCapacity=2 HealthCheckType=ELB`. DB đã có user và schema nên máy tự release.

Kết thúc hẳn: xóa runtime, edge, base (tắt deletion protection RDS trước; stack để lại snapshot), poc. Bucket, queue, ECR có `Retain` phải xóa riêng sau khi sao lưu. Xóa là không lấy lại được, nên xác nhận với nhóm trước.
