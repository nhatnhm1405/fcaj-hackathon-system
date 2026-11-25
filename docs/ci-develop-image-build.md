# Build image của develop trên GitHub Actions

Workflow: [aws-poc-release.yml](../.github/workflows/aws-poc-release.yml).

## Luồng build và phát hành

Mỗi lần push `develop`, GitHub Actions chạy:

1. `backend`: test profile `test`, đóng gói JAR, kiểm MySQL 8.4 trong DB
   dùng một lần và các deploy gate. Lưu JAR thành artifact của lần chạy.
2. `frontend`: test và build frontend, lưu `dist` thành artifact.
3. `image`: chờ cả hai job thành công, tải JAR và build image Linux AMD64
   bằng `Dockerfile.release`. Lưu image nén cùng image ID để job release dùng lại.
4. `release`: chỉ chạy trên `develop` khi `AWS_POC_RELEASE_ENABLED=true`
   và environment `aws-poc` cho phép. Tải image vừa build, kiểm image ID và
   revision, gắn tag rồi push ECR. Gửi lệnh qua SSM để EC2 pull đúng digest.

```text
GitHub Actions: test/package -> build image -> image artifact
                                               |
                              release được bật |
                                               v
                                              ECR
                                               ^
                                               | EC2 pull image
GitHub Actions ----------------> SSM ---------> EC2
```

Job `image` không cần quyền AWS hoặc environment `aws-poc`. Tắt release
vẫn kiểm được Docker build; không push ECR hoặc deploy EC2 trong trường hợp đó.
Image artifact được giữ một ngày để hạn chế dung lượng GitHub Actions.
Job release dùng image của cùng workflow run, không rebuild image. Nếu artifact
đã hết hạn khi chạy lại riêng job release, chạy lại toàn bộ workflow để tạo
artifact mới rồi phát hành. Chính sách giữ artifact của repo có thể cần được
đối chiếu trước khi dùng.

## Quyền và điều kiện release

Giữ cơ chế đã có của Issue #14: OIDC với deploy role, ECR login,
environment `aws-poc`, biến `AWS_POC_DEPLOY_ROLE_ARN` và
`AWS_POC_RELEASE_ENABLED`. Workflow này không tự tạo quyền hoặc đổi các biến.
Sau khi nhận quyền OIDC, release kiểm account thực tế bằng STS trước khi
đăng nhập ECR hoặc push image. `configure-aws-credentials@v4` không hỗ trợ
input `allowed-account-ids`, nên dùng bước kiểm tra tường minh.
Các điều kiện host, cấu hình riêng, schema/DB gate và TLS vẫn áp dụng;
xem [trạng thái Issue #14](report/issue-14-execution-status-2026-11-05.md).

EC2 nhận image từ ECR; luồng release này không lấy source về để build trên EC2.
Workflow SSH cũ trong `deploy.yml` vẫn chỉ áp dụng cho `main`.
Frontend hiện chỉ được build/lưu artifact, chưa tự publish S3/CloudFront.

## Kiểm tra trước khi merge

PR vào `develop` chạy [pr-validation.yml](../.github/workflows/pr-validation.yml).
Ngoài các kiểm tra hiện có, job backend build image từ JAR sau test, thực hiện
`docker save`/nén, xóa tag local rồi `docker load`; đối chiếu image ID và
revision sau khi tải lại. Luồng kiểm PR không cần đăng nhập AWS và không deploy.

Kiểm tra này chứng minh đóng gói và chuyển image bằng archive. Nó chưa chứng
minh push ECR, quyền OIDC/SSM, kết nối EC2, readiness hay nghiệp vụ trên AWS.
