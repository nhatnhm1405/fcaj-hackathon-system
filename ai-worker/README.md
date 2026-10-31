# AI Evaluation Worker (FCAJ Hackathon System)

Module AI độc lập thực hiện tự động đánh giá và chấm điểm mã nguồn repository GitHub (tại commit SHA chỉ định) theo bộ tiêu chí (Rubric) kỹ thuật của ban giám khảo.

---

## 📁 Cấu trúc Thư mục

```text
ai-worker/
├── contracts/
│   ├── input_schema.json     # JSON Schema chuẩn đầu vào từ Backend/SQS
│   ├── output_schema.json    # JSON Schema chuẩn kết quả đầu ra lưu S3
│   ├── sample_input.json     # Dữ liệu mẫu thực tế của 1 job chấm thi
│   └── sample_output.json    # Kết quả mẫu thực tế kèm điểm và evidence
├── src/
│   ├── __init__.py
│   ├── schema.py             # Data models & validation
│   └── mock_worker.py        # Worker giả lập để BE test luồng AWS độc lập
├── tests/
└── requirements.txt
```

---

## 🚀 Hướng dẫn Chạy Thử Mock Worker

Mock Worker không yêu cầu cài thêm thư viện bên ngoài (dùng Python standard library):

```bash
# Di chuyển vào thư mục ai-worker
cd ai-worker

# Chạy mock worker với sample input
python src/mock_worker.py --input contracts/sample_input.json --output contracts/test_output.json
```
