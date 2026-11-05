# BE2 → BE1: Các mục cần chốt trước khi triển khai backend lên AWS

Ngày 01/11/2026, Asia/Saigon. Phục vụ phối hợp [Issue #14 — Backend 2](https://github.com/nhatnhm1405/fcaj-hackathon-system/issues/14) và [Issue #13 — Backend 1](https://github.com/nhatnhm1405/fcaj-hackathon-system/issues/13).

Cảm ơn bạn. Hiện mình đã chuẩn bị phía BE2 như sau:

- EC2 đã chạy, truy cập được bằng Session Manager.
- Docker và Docker Compose đã cài, chạy thử container thành công.
- EC2 kết nối được RDS bằng TLS.
- RDS đang dùng **MySQL 8.4.11**, `lower_case_table_names=1`.
- Database **`fcaj_poc` đã tạo nhưng chưa có bảng/dữ liệu**.
- File dữ liệu cũ `seal_hackathon.bak` chưa được import.

Các trạng thái trên được ghi nhận từ kết quả thao tác và ảnh của BE2 trong phiên làm việc; chưa phải nghiệm thu backend hoặc luồng S3/SQS.

Để mình đưa backend lên EC2 và chạy thử cùng bạn, cần chốt các mục dưới đây. Không cần viết lại tài liệu đã bàn giao; có thể xác nhận và trỏ tới đúng file trong commit được chốt.

## 1. Chính xác mình phải lấy bản code nào?

Bạn gửi giúp mình:

- Link branch hoặc PR dùng để triển khai.
- **Commit SHA đầy đủ**, tức mã commit 40 ký tự, để mình build đúng bản bạn đã kiểm tra.
- Xác nhận commit đó đã có đủ migration, cấu hình AWS, script chạy và worker MOCK.

Phần đổi tên `seal-web`/`seal-backend` sang FCAJ hiện có thay đổi ở local BE2. Hai bên cần ghép các thay đổi này vào một commit thống nhất, để code mình lấy trên EC2 có cùng đường dẫn và tên service:

| Thành phần | Tên/đường dẫn thống nhất |
|---|---|
| Backend source | `back-end/src/fcaj-api` |
| Java package | `com.fcaj.hackathon` |
| Frontend source | `front-end/src/fcaj-web` |
| Backend service | `fcaj-backend` |
| Deploy settings trên EC2 | `/etc/fcaj/deploy-settings.sh` |

Chưa merge/push `main` để thử vì có thể kích hoạt deploy.

**Đầu ra cần nhận:** link branch/PR và full commit SHA chứa bản tích hợp đã thống nhất.

## 2. Bản code đó đã chạy test đạt chưa?

Lượt kiểm ngày 28/10 có hai test liên quan đến gửi job bị lỗi:

- Tạo job nhưng chưa thấy gửi message sang SQS: `AiJobIntegrationTest.productionWorkerRoundTripSuccessFailureAndDuplicate`.
- Trạng thái job còn `PENDING_DISPATCH`, chưa chuyển sang `QUEUED`: `AiJobIntegrationTest.roundTripCommitsBeforeAckAndKeepsScoresUntouched`.

Chưa xác định nguyên nhân; không kết luận hai lỗi này do rename. Bạn kiểm tra lại giúp mình trên bản code sẽ giao:

- Hai lỗi này đã xử lý được chưa?
- Backend test và test MySQL 8.4 có đạt không?
- Nếu còn lỗi, lỗi đó ảnh hưởng bước nào khi chạy thật?

Bạn gửi kết quả test hoặc link CI tương ứng với commit ở mục 1. Mình cần biết bản nào đủ điều kiện đưa lên máy để tránh debug nhầm phiên bản.

**Đầu ra cần nhận:** kết quả kiểm tra gắn với commit triển khai, cùng danh sách lỗi còn lại và người phụ trách nếu có.

## 3. Với database RDS đang rỗng, mình phải tạo bảng bằng cách nào?

Bạn xác nhận giúp:

- Có phải chạy lần lượt migration **V1 → V2 → V3** không? Migration ở đây là các script tạo/nâng cấu trúc bảng.
- Dùng script/lệnh cụ thể nào trong repo?
- Chạy xong thì kiểm bằng lệnh nào để biết cấu trúc bảng đã đúng?
- Backend khi khởi động có cần quyền tạo/sửa bảng nữa không, hay chỉ dùng tài khoản đọc/ghi dữ liệu?

Mình sẽ chuẩn bị tài khoản migration riêng và tài khoản runtime riêng theo yêu cầu. Tài khoản runtime là tài khoản DB mà backend dùng khi phục vụ request.

Về dữ liệu cũ: **đề xuất trước mắt dùng dữ liệu mẫu tối thiểu để hoàn thành luồng AWS**. Bạn xác nhận có bắt buộc import `seal_hackathon.bak` ngay không. Nếu chưa cần, mình để việc restore dữ liệu cũ thành bước riêng.

Nếu chọn dùng dữ liệu cũ ngay, hai bên cần chốt quy trình restore và kiểm schema trước khi baseline V1 rồi nâng V2/V3; không chạy V1 tạo lại bảng lên dữ liệu đã restore.

**Đầu ra cần nhận:** lựa chọn dữ liệu ban đầu, script/lệnh migration và kiểm chứng, yêu cầu quyền DB cho từng tài khoản.

## 4. Backend cần những cấu hình nào để khởi động?

Trong commit đã chốt, bạn xác nhận file `.env.aws.example` và các template trong thư mục `back-end/src/fcaj-api/deploy/` đã cập nhật đầy đủ.

Bạn chỉ rõ giúp mình:

- Những biến bắt buộc phải điền để backend khởi động.
- Những tính năng có thể tắt lúc đầu, như OAuth, gửi mail hoặc AI jobs.
- Có cấu hình nào cần thêm ngoài các template hiện tại không?
- Backend cần quyền S3/SQS nào mà role đang có chưa đáp ứng? Đối chiếu với policy thực, không mặc định role đã giao đủ mọi action hoặc prefix.

Mình sẽ điền endpoint RDS, tài khoản DB, bucket/prefix, queue URLs, domain và cấu hình private. Backend và worker phải dùng cùng bucket/prefix; queue visibility đã chọn là **120 giây**, cần đối chiếu cả cấu hình AWS và override trong ứng dụng.

Không cần bạn gửi secret vào chat hoặc đưa vào Git. Mật khẩu DB, JWT secret và thông tin truy cập được quản lý qua kênh private của nhóm.

**Đầu ra cần nhận:** xác nhận bộ template đúng revision, danh sách biến bắt buộc và các quyền/cấu hình còn thiếu.

## 5. Khi backend chạy được, cần chuẩn bị dữ liệu gì để thử tạo một job?

Bạn hướng dẫn cụ thể cách chuẩn bị trên DB mới:

- Tạo tài khoản quản trị ban đầu thế nào?
- Tài khoản tạo job cần vai trò và quyền gì?
- Cần tạo event, round, team, submission và rubric theo thứ tự nào?
- Round/timer phải ở trạng thái nào để API cho phép tạo job?
- Có script hoặc bộ bước qua API/UI nào sẵn trong repo để làm việc này không?

Mục tiêu là mình có **một tài khoản test và một submission hợp lệ** để gọi API tạo job. Không dùng script reset/demo trên RDS nếu script đó chưa được kiểm tra phù hợp.

**Đầu ra cần nhận:** một bộ bước chuẩn bị dữ liệu giả, quyền cần cấp và các ID cần dùng cho buổi thử; credential tài khoản test qua kênh private.

## 6. Chạy thử một job từ đầu đến cuối như thế nào?

Mình đã thấy contract trong `docs/contracts/ai-job-v1/CURRENT.md`. Bạn xác nhận tài liệu đó đúng với commit giao ở mục 1, rồi chỉ rõ:

- Gọi API nào để tạo job và lấy `jobId`.
- Ai chạy worker MOCK trong buổi test; hai bên phối hợp với AI nếu cần.
- Worker dùng file/JAR nào, chạy lệnh nào và cần cấu hình gì.
- Gọi API nào để xem trạng thái và kết quả.
- Kết quả mong đợi của một job thành công và một job thất bại.

Mình muốn chốt một buổi chạy thử theo luồng:

```text
Tạo job qua API
→ backend lưu RDS và gửi SQS
→ worker MOCK xử lý
→ gửi kết quả về
→ backend lưu RDS
→ đọc được kết quả qua API
```

Chốt lịch và người chạy để tránh nhiều worker/consumer tranh cùng queue. Ghi SHA/image digest, jobId và log đã lọc để làm minh chứng; chưa coi thử MOCK là nghiệm thu model thật hoặc FE job UI.

**Đầu ra cần nhận:** bộ bước chạy thử, người chạy worker, thời gian phối hợp và kết quả mong đợi để đối chiếu.

## Phân công để hai bên làm song song

| BE2 phụ trách | BE1 phụ trách |
|---|---|
| EC2/RDS, mạng, runtime role | Chốt bản code và kết quả test |
| Endpoint, TLS/CA và cấu hình private | Xác nhận cách tạo/nâng bảng và quyền DB |
| Bucket/prefix, queue configuration, quyền AWS theo scope | Hướng dẫn chuẩn bị dữ liệu và quyền nghiệp vụ để tạo job |
| Pipeline build/push ECR và deploy đúng image lên EC2 | Hướng dẫn API/worker MOCK, phối hợp kiểm tra chức năng |

Hai bên cùng chốt revision tích hợp, lịch smoke và bằng chứng nghiệm thu. Phần chưa rõ cần ghi người phụ trách và đầu vào còn chờ, tránh coi tài nguyên đã tạo là backend đã chạy hoàn chỉnh.
