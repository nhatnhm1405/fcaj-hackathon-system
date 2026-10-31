# Báo cáo Đánh giá Mô hình & Quyết định Chuyển dịch Kiến trúc AI (Week 2)

**Dự án:** FCAJ Hackathon System  
**Nhiệm vụ:** Benchmark Model & Triển khai Worker chấm điểm tự động (Issue #15)  
**Tác giả:** AI Engineer  
**Ngày lập:** 28/10/2026  

---

## 1. Bối cảnh & Yêu cầu Nghiệm thu
Theo nội dung Issue #15 (Week 2):
- Yêu cầu thử nghiệm và benchmark mô hình mã nguồn mở cục bộ (Qwen3-8B + Ollama trên máy trạm 8GB VRAM).
- Tiêu chí hoàn thành nêu rõ: *"Nếu model chưa đáp ứng, ghi nguyên nhân cùng phương án tiếp theo; fine-tuning chưa bắt buộc."*

---

## 2. Kết quả Thử nghiệm & Giới hạn của Model Local (8GB VRAM)

| Tiêu chí | Qwen-8B (Quantized Q4_K_M) trên 8GB VRAM | Đòi hỏi thực tế của bài toán Chấm Repo | Đánh giá |
| :--- | :--- | :--- | :--- |
| **Dung lượng Context** | Tối đa ~8k - 16k tokens (bị nghẽn do VRAM giới hạn) | Một repo mini đã có từ 30k đến 100k+ tokens (bao gồm cả mã nguồn, cấu hình, test). | ❌ **Không đáp ứng**: Bị tràn context, phải cắt xén file làm mất bối cảnh kiến trúc tổng thể. |
| **Độ trễ xử lý (Latency)** | 45s - 90s cho prompt ~4k tokens | Worker cần phản hồi trong vòng 5s - 15s để đảm bảo throughput trên SQS. | ❌ **Quá chậm**: Gây nghẽn hàng đợi (queue backlog) khi nhiều đội nộp bài cùng lúc. |
| **Khả năng sinh Structured Output & Evidence** | Độ chính xác ~65-75% với JSON Schema phức tạp, thường trích sai số dòng (`line_start`, `line_end`). | Cần 100% tuân thủ JSON Contract để Backend ghi trực tiếp vào Database mà không crash parser. | ❌ **Rủi ro cao**: Model 8B quantization dễ bị ảo giác số dòng code khi đọc ngữ cảnh dài. |
| **Tài nguyên phần cứng** | Chiếm 100% 8GB VRAM, dễ OOM khi chạy đồng thời luồng nén file/parse git. | Server triển khai cần chạy ổn định 24/7. | ❌ **Khó scale**: Cần GPU server đắt đỏ (A10G/T4) trên AWS nếu deploy local worker. |

---

## 3. Quyết định Kiến trúc: Chuyển dịch sang Cloud AI API

Team thống nhất phương án: **Dừng triển khai Qwen-8B local trên GPU 8GB và chuyển dịch sang Cloud AI API.**

### Lợi ích thu được:
1. **Context Window khổng lồ:** Các model hiện đại (như Google Gemini 1.5 Pro / Flash với 1M tokens) cho phép nạp toàn bộ cấu trúc repo, file cấu hình và code vào một lượt gọi duy nhất mà không cần kỹ thuật chunking phức tạp.
2. **Cam kết Structured Outputs:** Hỗ trợ chuẩn JSON Schema ở tầng API, đảm bảo 100% output khớp với hợp đồng dữ liệu của Backend.
3. **Chi phí & Vận hành:** Worker chỉ là một process Python lightweight (chạy mượt trên AWS ECS/Fargate hoặc EC2 t3.medium giá rẻ), không cần GPU đắt đỏ.
4. **Trích xuất Evidence chính xác:** Khả năng định vị số dòng (line numbers) và đánh giá logic code vượt trội.

---

## 4. Kế hoạch Tiếp theo (Next Steps)
1. Bàn giao bộ Contract `input_schema.json` & `output_schema.json` cùng `mock_worker.py` cho BE1 và BE2.
2. Xây dựng module `github_fetcher.py`: Tải archive source code tại commit SHA trực tiếp qua GitHub API (lọc bỏ `node_modules`, binary, build artifacts).
3. Tích hợp Cloud AI API Client (Gemini API) với System Prompt chuyên dụng cho chấm Rubric.
