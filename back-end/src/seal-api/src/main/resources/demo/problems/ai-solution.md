# AI Solution — Phát hiện bất thường ở hệ thống làm mát

Một hệ thống làm mát ghi liên tục nhiệt độ và điện năng. Người vận hành muốn nhận cảnh báo khi các số đo không còn giống hoạt động bình thường.

## Yêu cầu

- Dùng các bản ghi trước đó để phát hiện hai kiểu bất thường gồm nhiệt độ tăng dần và điện năng đột biến.
- Với mỗi cảnh báo, hiển thị thời gian, số đo liên quan và lý do ngắn gọn.
- So sánh kết quả với một quy tắc ngưỡng đơn giản. Báo số cảnh báo đúng và sai trên một khoảng thời gian kiểm thử riêng.

## Dữ liệu và bài nộp

Tạo chuỗi giả lập có các cột `recorded_at, temperature_c, power_kw, anomaly_label`. Chỉ dùng nhãn của tập kiểm thử để đánh giá. Nộp mã nguồn, hướng dẫn chạy và một bảng so sánh hai cách phát hiện.
