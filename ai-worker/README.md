# AI Evaluation Worker (SEAL Hackathon System)

Module AI độc lập thực hiện tự động đánh giá mã nguồn repository GitHub (tại commit SHA chỉ định) theo bộ tiêu chí (Rubric) kỹ thuật của ban giám khảo.

Hiện hỗ trợ 2 chế độ vận hành:
1. **AWS Lambda AI Worker v3 (`src/lambda_handler.py` — Kiến trúc mục tiêu AWS v3):**
   - Trigger từ **Amazon SQS (Jobs)** chứa envelope `fcaj.ai.message.v1` (`AI_JOB_REQUESTED`).
   - Đọc và kiểm tra SHA-256 + `sizeBytes` của `input-manifest.json`, `source.json`, `rubric.json` trên **S3 (Application)** (không cần gọi GitHub trực tiếp, không cần nằm trong VPC).
   - Đọc `DEEPSEEK_API_KEY` từ **SSM Parameter Store** (hoặc biến môi trường), gọi **DeepSeek API** (`AI_PROVIDER=deepseek`, `deepseek-chat`).
   - Hậu kiểm chặt `safeText` (loại bỏ secret, URL, email, NUL) và giới hạn dòng khớp `JobResultValidator` của Backend.
   - Ghi kết quả `fcaj.ai.output.v1` lên S3 (`results/{jobId}/output.json`) với `If-None-Match: "*"` (idempotent khi retry) và gửi `AI_JOB_COMPLETED` vào **Amazon SQS (Results)**.
2. **CLI / Local AI (`src/ai_grader.py`, `src/mock_worker.py`):** Giữ tương thích cho chạy thử cục bộ với Ollama/Qwen3 hoặc Cloud AI fallback.

---

## 📁 Cấu trúc Thư mục

```text
ai-worker/
├── contracts/
│   ├── input_schema.json     # JSON Schema tham khảo cho CLI cục bộ
│   ├── output_schema.json    # JSON Schema tham khảo cho CLI cục bộ
│   ├── sample_input.json     # Dữ liệu mẫu CLI
│   └── sample_output.json    # Kết quả mẫu CLI
├── src/
│   ├── __init__.py
│   ├── lambda_handler.py     # AWS Lambda handler chuẩn contract fcaj.ai.message.v1 / v1 output
│   ├── schema.py             # Data models & validation cho CLI cục bộ
│   ├── code_cleaner.py       # Quét, lọc mã nguồn, giới hạn ngữ cảnh & đánh số dòng
│   ├── github_fetcher.py     # Tải snapshot repository cho luồng CLI cục bộ
│   ├── ai_grader.py          # Chấm điểm DeepSeek / Local AI / Gemini + sanitize safeText
│   └── mock_worker.py        # Worker giả lập CLI
├── tests/
│   ├── __init__.py
│   ├── test_ai_worker.py     # Unit tests core grader, code cleaner & CLI contracts
│   └── test_lambda_handler_v1.py # Integration tests Lambda worker theo contract fcaj.ai.message.v1
├── .env.example              # Mẫu biến môi trường (Local AI, DeepSeek & AWS Lambda v3)
└── requirements.txt
```

---

## ☁️ Cấu hình AWS Lambda AI Worker (v3)

- **Handler:** `src.lambda_handler.lambda_handler`
- **Runtime:** Python 3.12 (đã tích hợp sẵn `boto3`, không cần đóng gói thêm thư viện ngoài cho core worker)
- **Vị trí mạng:** Ngoài VPC (có sẵn kết nối Internet tới DeepSeek API, không tốn NAT Gateway và không có quyền vào RDS)
- **Concurrency & Trigger:**
  - Trigger từ `SQS (Jobs)` với `BatchSize = 1`, bật `ReportBatchItemFailures`
  - `MaximumConcurrency = 2` (hoặc `ReservedConcurrentExecutions = 2`) để kiểm soát chi phí và tránh rate limit của DeepSeek
  - **Timeout:** đặt khoảng `120s`–`180s` (lưu ý `VisibilityTimeout` của SQS Jobs phải `>= 6 ×` timeout thực tế nếu kéo dài timeout hàm, hoặc giữ timeout hàm `<= 20s` khi `VisibilityTimeout = 120s` và tăng `VisibilityTimeout` lên `>= 720s` nếu đặt timeout Lambda `120s`)
- **Biến môi trường Lambda:**
  - `AI_PROVIDER=deepseek`
  - `DEEPSEEK_MODEL=deepseek-chat`
  - `DEEPSEEK_API_KEY_PARAMETER=/fcaj/poc/deepseek-api-key` (hoặc truyền `DEEPSEEK_API_KEY` trực tiếp khi test)
  - `APP_STORAGE_BUCKET=<tên-bucket-application>`
  - `APP_STORAGE_PREFIX=poc`
  - `APP_AI_JOBS_RESULTS_QUEUE_URL=<url-sqs-results>`
  - `LAMBDA_MAX_SOURCE_CHARS=120000` (repo lớn hơn mức này được đọc đều giữa các khu vực code, tài liệu tối đa khoảng 15%; `repo.note` ghi rõ đã đọc bao nhiêu)
- **Quyền IAM tối thiểu cho Lambda Execution Role:**
  - `s3:GetObject`, `s3:GetObjectVersion` trên `<bucket>/<prefix>/inputs/*`, `<bucket>/<prefix>/rubrics/*`, `<bucket>/<prefix>/results/*`
  - `s3:PutObject` trên `<bucket>/<prefix>/results/*`
  - `sqs:ReceiveMessage`, `sqs:DeleteMessage`, `sqs:GetQueueAttributes` trên `SQS (Jobs)`
  - `sqs:SendMessage` trên `SQS (Results)`
  - `ssm:GetParameter` (và `kms:Decrypt` nếu dùng CMK) trên parameter `DEEPSEEK_API_KEY_PARAMETER`
  - Ghi log CloudWatch (`AWSLambdaBasicExecutionRole`)

### Đóng gói Zip Deploy lên AWS Lambda
```bash
cd ai-worker
zip -r lambda-ai-worker.zip src/
```

---

## 🚀 Hướng dẫn Chạy Thử & Kiểm thử

### 1. Chạy Kiểm thử Tự động (Unit & Contract Tests)
```bash
cd ai-worker
python -m unittest discover -s tests -v
```

### 2. Chạy Mock Worker CLI (Cục bộ)
```bash
cd ai-worker
python src/mock_worker.py --input contracts/sample_input.json --output contracts/test_output.json
```

### 3. Cấu hình Local AI (Ollama + Qwen3 trên AWS EC2)
Sao chép `.env.example` sang `.env` và cấu hình endpoint Ollama trên EC2:
```bash
AI_PROVIDER=local_ai
LOCAL_AI_BASE_URL=http://localhost:11434/v1/chat/completions
LOCAL_AI_MODEL=qwen3:4b
LOCAL_AI_THINK_MODE=false
```
