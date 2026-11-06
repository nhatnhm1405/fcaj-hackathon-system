# CloudFormation cho Issue #14

Đã đối chiếu tài nguyên tại account triển khai của operator, Singapore, ngày06/11/2026. Các file dưới đây là source để review; chưa áp update/import hoặc tạo role release trong lượt bổ sung này.

| File | Phạm vi và cách dùng |
| --- | --- |
| `poc.yaml` | Lấy từ template đang chạy của stack `fcaj-poc`, giữ Logical IDs của S3, SQS/DLQ, ECR và Logs. Chỉnh source visibility từ60 lên120 giây cho hai queue chính để khớp runtime; hai DLQ giữ60 giây theo API đã đọc. Phải đọc lại cả4 queue và review UPDATE change set trước áp; không create lại stack. |
| `runtime-import.yaml` | Candidate import cho SG backend/RDS, instance role/profile, EC2, DB subnet group và RDS hiện hữu. VPC/subnets/route tables/NAT/endpoints và DB parameter group vẫn do owner bên ngoài quản lý. Chưa phải template dựng lại toàn bộ môi trường từ đầu. |
| `github-release-role.yaml` | Role OIDC chỉ push đúng ECR repo và gửi SSM đến đúng EC2. Cần OIDC provider hiện hữu và GitHub environment được bảo vệ trước khi tạo role. |
| `frontend-certificate.yaml` | Template cấp certificate mới; certificate live đã được request trực tiếp nên không chạy template này để tạo bản trùng. |

Tên cố định trong import candidate là identity đang chạy, không phải defaults cho môi trường mới. Template không chứa mật khẩu DB, JWT, AWS keys hoặc nội dung private env. RDS thiếu master credentials có chủ đích vì đây là import, không dùng để tạo DB mới. EC2 metadata IMDSv2/hop2 đã kiểm qua API nhưng chưa được template này quản lý; không đổi launch properties/volumes khi import.

## Import tài nguyên đang chạy

Chưa tạo hoặc thực thi IMPORT change set. Trước khi làm, owner review quyền sở hữu VPC/subnet/route và tài nguyên nào đã thuộc stack khác. Lấy metadata mới và dùng `get-template-summary` để kiểm identifier/import support của từng type; không suy ra import được chỉ từ template validation. Cần `CAPABILITY_NAMED_IAM` cho role/profile.

| Logical ID | Tài nguyên hiện hữu cần đối chiếu |
| --- | --- |
| BackendSecurityGroup | Đọc GroupId của group backend trong phiên AWS riêng |
| DatabaseSecurityGroup | Đọc GroupId của group RDS trong phiên AWS riêng |
| BackendRole | `fcaj-poc-backend-ec2-role` |
| BackendInstanceProfile | `fcaj-poc-backend-ec2-role` |
| BackendInstance | Đọc instance ID từ metadata operator riêng |
| DatabaseSubnetGroup | `fcaj-poc-db-subnets` |
| Database | `fcaj-poc-mysql` |

IMPORT phải chỉ nhận tài nguyên hiện hữu, không tạo/xóa/thay thế tài nguyên hoặc sửa thuộc tính đang chạy. Retain không ngăn replacement và không thay backup. Sau import phải kiểm drift; mọi update sau đó cần xem riêng change set, đặc biệt EC2 IP/private settings, EBS, RDS engine/storage/credentials, SG và parameter group. Không import VPC/routes/NAT một cách suy đoán từ vài ID. Toàn bộ mạng còn cần template/ownership review để hoàn tất mục CloudFormation của issue.

VPC/subnet IDs, private IP, key-pair name và operator SSH CIDR là required parameters không có default công khai. Owner nhập trực tiếp từ metadata đã xác minh trong phiên riêng; không commit file parameters chứa các giá trị này hoặc số liệu billing toàn tài khoản.

## Bật GitHub Actions release

Workflow tại `.github/workflows/aws-poc-release.yml` chỉ release khi ref là `develop` và repository variable `AWS_POC_RELEASE_ENABLED=true`; mặc định chưa bật. Backend đã deploy operator thành công không chứng minh pipeline release đã chạy.

1. Maintainer tạo environment `aws-poc`, deployment branch rule **chỉ develop**, review protection phù hợp. Token OIDC dùng subject environment, nên trust policy không thể tự phân biệt branch. Không tạo trust trước khi kiểm rule này.
2. Kiểm OIDC provider toàn account. Nếu cần tạo mới: URL `https://token.actions.githubusercontent.com`, audience `sts.amazonaws.com`. Không tạo provider trùng hoặc dùng access key lâu dài.
3. Review/deploy `github-release-role.yaml` tại Singapore với ARN provider thật, `CAPABILITY_NAMED_IAM`. Role có quyền chạy shell trên EC2; chỉ merge workflow đã review.
4. Đặt `AWS_POC_DEPLOY_ROLE_ARN` theo output. Kiểm runtime private files, DB verify-only, Caddy, S3 prefix và hai queue vẫn đúng.
5. Review và merge PR vào develop theo quyền maintainer; không merge main hoặc đổi default branch để thử workflow. Chỉ bật opt-in khi sẵn sàng phát hành; dùng develop push/dispatch hợp lệ để chạy.
6. Lưu Actions run URL, commit, immutable digest và SSM manifest. Đối chiếu `/health/version`, image digest trên EC2 và HTTPS readiness. Nếu lỗi DB gate thì điều tra, không reset/baseline/repair. Rollback image không rollback schema hoặc file.

Workflow hiện build frontend artifact nhưng chưa tự publish S3/invalidate CloudFront. Frontend live đã được phát hành operator; automation frontend là phần riêng cần nối trước khi coi pipeline bao phủ cả web. Worker mock đã kiểm một lượt SQS/S3 trong cửa sổ riêng; không bật business AI delivery chỉ để lặp smoke này.

Kiểm local: `cfn-lint --template infra/cloudformation/*.yaml --regions ap-southeast-1`, AWS `validate-template` từng file và `git diff --check`. Validation không chứng minh import, quyền runtime, thay đổi không gây gián đoạn hoặc Actions release thành công.
