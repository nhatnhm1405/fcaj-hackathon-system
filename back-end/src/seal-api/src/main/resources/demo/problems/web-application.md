# Web Application — Bảng điều phối thiết bị phòng lab

Phòng lab có nhiều thiết bị đo nhiệt độ và độ ẩm. Nhân viên cần biết thiết bị nào đang gửi dữ liệu, khu vực nào vượt ngưỡng và ai đang xử lý cảnh báo.

## Yêu cầu

- Dựng dashboard hiển thị lần đo gần nhất, trạng thái và lịch sử của từng thiết bị.
- Cảnh báo khi nhiệt độ vượt 30 °C hoặc thiết bị không gửi dữ liệu trong 10 phút. Cho phép lọc và đánh dấu cảnh báo đã xử lý.
- Hiển thị thời điểm cập nhật cuối cùng để người xem phân biệt dữ liệu mới và cũ.

## Dữ liệu và bài nộp

Dùng dữ liệu giả lập với ít nhất ba thiết bị và ba tình huống gồm hoạt động bình thường, vượt ngưỡng và mất tín hiệu. Mỗi bản ghi có `device_id, measured_at, temperature_c, humidity_pct`. Nộp mã nguồn, hướng dẫn chạy và video ngắn trình bày cách xử lý ba tình huống.
