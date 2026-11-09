# Frontend AWS PoC

Bucket hiện hữu: `<FRONTEND_BUCKET>`, region `ap-southeast-1`. Chỉ sử dụng S3 REST origin; không bật public bucket/website endpoint.

- `origin-access-control.json`: OAC ký mọi request bằng SigV4. Lấy OAC ID đã xác minh từ hồ sơ operator riêng.
- `distribution.template.json`: cấu hình đầu vào cho AWS CLI, chưa phải stack CloudFormation. Thay đúng `REPLACE_OAC_ID` và caller reference duy nhất ở bản render evidence trước sử dụng. Certificate us-east-1 phải ISSUED trước tạo distribution với alias. Môi trường operator đã có distribution; đọc ID/domain từ hồ sơ riêng và không chạy create lần nữa để tạo trùng.
- Chỉ cấp `s3:GetObject` cho principal CloudFront, điều kiện `AWS:SourceArn` bằng ARN distribution thực. Giữ cả4 PublicAccessBlock=true và BucketOwnerEnforced. Không ghi đè policy có sẵn mà chưa review.
- SPA fallback403/404 về index.html áp dụng riêng frontend; API dùng hostname riêng và không đi qua fallback. Phải kiểm nội dung JS/CSS thật, không coi index200 là đủ chứng minh OAC đúng.
- Managed-CachingOptimized và Managed-SecurityHeadersPolicy đã đối chiếu bằng API và tài liệu AWS. CachingOptimized có MinTTL1 giây; index được upload no-cache, asset có tên hash dùng cache immutable. Sau phát hành, invalidate `/` và `/index.html` nếu cần cập nhật ngay.
- SecurityHeadersPolicy không đặt CSP. Chưa có CSP được kiểm thử với frontend, request access logs hoặc WAF trong PoC này; không coi cấu hình là hardening production hoàn chỉnh.
- Chưa import bucket/certificate/OAC/distribution vào CloudFormation. Template CloudFormation bao phủ hạ tầng và quy trình import tài nguyên hiện hữu vẫn là phần Issue#14 cần review; không chạy create-stack để tạo lại bucket/RDS/queue đang dùng.

API edge nằm ở `../edge/`. Caddy bind80/443, backend chỉ bind127.0.0.1:8080, giữ nguyên đường dẫn API khi proxy. Hai Docker volumes giữ chứng chỉ qua recreate container; không xóa chúng khi cập nhật. Chỉ khởi động sau khi record `A api` được lưu với proxy/OneShield protection tắt cho riêng record và phân giải đúng EC2 hiện tại. Không tự thay nameserver hoặc root/www/MX của domain.

Nguồn: [CloudFront OAC](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-restricting-access-to-s3.html), [managed response headers](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/using-managed-response-headers-policies.html).
