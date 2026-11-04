# AI Evaluation Worker (SEAL Hackathon System)

Module AI độc lập thực hiện tự động đánh giá và chấm điểm mã nguồn repository GitHub (tại commit SHA chỉ định) theo bộ tiêu chí (Rubric) kỹ thuật của ban giám khảo.

`mock_worker.py` dùng fixture tổng hợp với điểm cố định; không đánh giá repository thực tế. Các evidence mẫu hiện tham chiếu source SEAL của bản tích hợp local, không phải nội dung tại commit lịch sử trong `sample_input.json`.

CLI Python này đọc/ghi JSON file. Contract `job_id`/`criteria_results` chưa nối trực tiếp với transport S3/SQS và envelope `fcaj.ai.message.v1` của Java backend. Smoke AWS hiện dùng Java MOCK worker cùng candidate; tích hợp Python/model cần adapter và kiểm chứng riêng.

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
│   ├── schema.py             # Data models & validation (Input/Output contracts)
│   ├── code_cleaner.py       # Quét, lọc mã nguồn & đánh số thứ tự dòng
│   ├── github_fetcher.py     # Tải snapshot repository tại commit SHA cố định
│   ├── ai_grader.py          # Chấm điểm qua Cloud AI API (DeepSeek / Gemini)
│   └── mock_worker.py        # Worker giả lập để BE test luồng AWS độc lập
├── tests/
│   ├── __init__.py
│   └── test_ai_worker.py     # Bộ unit test kiểm định contract, prompt & grader
└── requirements.txt
```

---

## 🚀 Hướng dẫn Chạy Thử

### 1. Chạy Mock Worker (Không cần API Key)
```bash
cd ai-worker
python src/mock_worker.py --input contracts/sample_input.json --output contracts/test_output.json
```

### 2. Chạy Kiểm thử Tự động (Unit Tests)
```bash
cd ai-worker
pytest -v
```
