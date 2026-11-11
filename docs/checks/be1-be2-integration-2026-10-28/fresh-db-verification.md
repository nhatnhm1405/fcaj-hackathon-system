# Kiểm chứng bổ sung: RDS rỗng và bộ bàn giao BE2

Ngày28/10/2026, Asia/Saigon. Kết quả local trên working tree chưa commit; không có release SHA/ECR digest. Chưa gọi AWS, chạy migration lên RDS/DB nghiệp vụ hoặc deploy server.

## Thay đổi được kiểm

- CLI initialize-empty bắt buộc runtime URL chính xác trước kết nối/ghi; kiểm catalog/MySQL8.4/case1/charset/collation. Chỉ migrate catalog rỗng; nonempty phải đã đạt gate V3. Không tự baseline/repair/clean/upgrade DB V1/V2.
- Deploy mode verify-only là mặc định; operator chọn initialize-empty cho lần khởi tạo. Compose precheck → initializer trong candidate → read-only gate → compose up. Không thay backend trước khi cả hai CLI đạt.
- Template Compose/settings/migration/worker env; truststore path giống nhau giữa CLI/runtime,raw env giữ nguyên ký tự dollar,bind loopback cho proxy. Private env/settings/truststore/user uploads bị loại khỏi Git và Docker context.
- Một hướng dẫn bàn giao hiện hành,README index,runbook cập nhật path fcaj-api/com.fcaj. Không sửa migration V1/V2/V3.

## Kết quả thực tế

| Kiểm tra | Kết quả và giới hạn |
|---|---|
| Backend package với Java21.0.10 | BUILD SUCCESS lúc14:12:43+07;621 test được thống kê,613 thực thi đạt,8 MySQL-only cases skip vì không có lab URL ở lượt đó. Sau đó thêm1 MySQL-only runtime privilege case,không đổi production Java |
| MySQL lab cuối | **36 tests,0 failure/error/skipped** lúc14:30:42+07;21 AiJobIntegrationTest +9 FreshDatabaseDeploymentTest +6 FileReferenceConcurrencyTest;không cộng27 test lặp vào613 |
| FreshDatabaseDeploymentTest |9/9 đạt trên MySQL8.4.13 thật, gồm JAR operator thật và quyền runtime DML-only. Lượt này kiểm thêm9 ca mới ngoài bộ613 thông thường |
| Deploy shell |8/8 command-recording tests đạt: verify-only,gate fail,init success,init fail,gate fail sau init,unknown mode,Compose precheck fail,dirty candidate |
| Dockerfile thật |docker build -t fcaj-backend-handoff:local back-end/src/fcaj-api đạt;Maven trong build stage và JRE runtime image build thành công. Không push image |
| Compose template thật |Compose5.1.4 parse được mapping image/revision/profile/loopback/read-only truststore. config JSON serialize dollar thành dạng escaped;đã kiểm thêm container shell thật nhận đúng synthetic$literal,không khởi động backend/DB/AWS. Project fcaj-template-check được dọn |
| Fixtures/checks |7 parser tests đạt;7 JSON lịch sử và17 current-examples validate đạt. PowerShell parser/Bash syntax và git diff --check được kiểm |

9 ca DB mới: DB rỗng lênV3/rerun giữ sentinel;schema không có history không bị sửa;DBV2 không tự upgrade;baseline3 giả bị chặn;schema drift không tự repair;checksum sai không bị sửa lại;collation sai bị chặn trước tạo bảng;packaged initializer chạy đủ;runtime DML-only chạy Flyway no-op và INSERT nhưng không được CREATE TABLE.

Lab cuối fcaj-ai-lab-87453d46b16f đã dừng và giữ. Log local target/fcaj-ai-lab-87453d46b16f-test.log;36 case kết thúc trong1:10. Job synthetic trong lượt cuối:success f17cd16a-4b57-4210-a13b-f5d64a124215,failure9573ae22-5117-466b-b767-a73dc0377ecc. Không gửi message thật hoặc đổi điểm chính thức.

Lượt đầu chưa tạo được lab vì Docker đang tắt;đã khởi động Docker Desktop. Lượt lab fcaj-ai-lab-99d3af6a03f3 chạy đồng thời với Docker image build đạt35/36,ca packaged initializer bị giới hạn60s khi đang migrateV1. Rerun riêng cùng assertion đạt trong41.73s. Giới hạn process tạo schema được đổi thành180s để bao phủ cold-VM DDL fsync;không đổi timeout15s/request,45s/snapshot hoặc assertion dữ liệu. Sau đó chạy lại toàn36 trên lab mới đạt như trên. Không bỏ/skip ca lỗi để nghiệm thu. Lab đầu cũng đã dừng và giữ.

Host JAR sau runner package SHA-256:fedb8c219d0aa2eef4ed611ad7e52e8a994a8aa2afe1f97e7932c33f360e14f3. Local Docker image config ID:sha256:8f6d9ef3d90d0a68a78b645f4c91e4d2a270d3298382d075f77c2a8955daf32b;local build manifest list sha256:1257cf5e392eb08567d5916b85a0ce9acb89e047d1bbdb5e9d089600745c9f7d. Đây là artifact working tree,không phải digest image BE2 đã nhận trên ECR. Thay đổi cuối sau image build chỉ thuộc test harness/deploy templates/docs/context ignore;production Java trong image là bản đã kiểm initializer.

## Những phần không được suy ra từ kết quả này

- Lab dùng root migration identity và generated runtime identity trên server riêng;chưa chứng minh CA/TLS RDS,network/IAM/secret provisioning thật.
- Compose container probe chỉ chạy shell với synthetic env và dummy truststore để kiểm truyền cấu hình,không chứng minh certificate trust hay ứng dụng production đã boot.
- Source migration không đổi nên evidence627 dòng cũ được giữ từ rehearsal trước;không chạy lại dump restore trong lượt này.
- CI remote,backend application restart qua browser,OAuth/SMTP,FE job UI,model thật và transport S3/SQS còn chờ phối hợp.
- Source đủ chuẩn bị bàn giao sau review;release SHA/link branch/PR chỉ có sau khi người dùng duyệt commit/push. Chưa stage/commit/push tự động.
