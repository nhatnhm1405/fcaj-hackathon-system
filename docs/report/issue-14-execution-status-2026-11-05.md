# Issue #14: phạm vi PR và điều kiện phát hành

Cập nhật ngày 06/11/2026, Asia/Saigon. Tài liệu công khai chỉ giữ phạm vi,
kết quả tổng hợp và việc còn thiếu. Account ID, ARN, endpoint, địa chỉ IP,
resource ID và bản ghi vận hành của môi trường cụ thể được lưu riêng ngoài Git
trong `docs/vq/`. Không dùng tài liệu này làm file cấu hình chạy thật.

## Thay đổi trong PR

- Workflow kiểm backend, MySQL lab và frontend; đóng gói JAR đã kiểm thử thành
  ECR image, ghim digest, deploy qua SSM và lưu manifest.
- Deploy gate kiểm đúng revision, target DB và schema trước khi thay backend.
  Khởi tạo database là thao tác operator riêng; CI dùng verify-only.
- Cấu hình Caddy cho HTTPS API, backend loopback và volumes giữ TLS state;
  cấu hình frontend private S3/CloudFront dùng OAC.
- CloudFormation foundation và các candidate import/runtime/OIDC để review.
  Import candidate không phải bộ dựng môi trường mới hoàn chỉnh.

## Kết quả đã ghi nhận

- Operator đã kiểm HTTPS frontend/API, anonymous browser smoke, CORS và CSRF.
- Một lượt SQS/S3 transport với mock worker đã thành công; chưa phải nghiệm
  thu toàn luồng HTTP, outbox, worker, DB và frontend bằng tài khoản thật.
- Đã nhập dữ liệu backup được owner cho phép bằng transaction data-only,
  giữ schema migration và dữ liệu tham chiếu hiện hữu. Kiểm dữ liệu, khóa ngoại,
  schema gate và readiness sau restart đạt; đã giữ backup trước nhập.
- CI trên head trước lượt dọn tài liệu đã thành công. Lượt dọn này cần kiểm
  lại tài liệu và CI của commit mới; không dùng kết quả cũ thay cho head mới.

Chi tiết theo từng môi trường và manifest gốc được operator giữ riêng.
Chuyển bản ghi sang thư mục ignored không xóa nội dung khỏi lịch sử commit
đã công bố trước đó. Workflow/template vẫn chứa cấu hình của môi trường đích;
việc tham số hóa cấu hình để bàn giao từng trường là phạm vi riêng.

## Trước khi bật phát hành hoặc nghiệm thu Issue #14

1. Maintainer review PR, GitHub environment chỉ cho develop, OIDC trust và
   quyền release. Giữ `AWS_POC_RELEASE_ENABLED` tắt đến khi đủ điều kiện.
2. Kiểm các công cụ cần thiết trên EC2, private config, quyền runtime,
   truststore và DB gate; lưu một Actions release thành công bằng đúng artifact.
3. Nối phát hành frontend vào S3/CloudFront. Workflow hiện chỉ lưu frontend
   build artifact, chưa tự publish hoặc invalidate CDN.
4. Review ownership và change set trước mọi CloudFormation import/update.
   Network và một số tài nguyên live chưa thuộc candidate hiện tại.
5. Owner kiểm đăng nhập và các luồng nghiệp vụ bằng tài khoản thật. Mức sẵn
   sàng dùng chính thức còn cần kiểm thử tải, phục hồi backup và vận hành riêng.

Merge source không đồng nghĩa đã phát hành AWS hoặc nghiệm thu production.
