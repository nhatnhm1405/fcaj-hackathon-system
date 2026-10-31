"""
Module AI Grader - Tích hợp Cloud AI API (Google Gemini API / OpenAI API).
Thực hiện đánh giá source code đã đánh số dòng theo bộ tiêu chí Rubric,
và trả về kết quả có cấu trúc kèm Dẫn chứng (Evidence).
"""

import json
import os
import sys
import time
import urllib.request
import urllib.error
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional

# Cho phép import schema
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from src.schema import (
    GradingJobInput,
    GradingJobOutput,
    CriterionResult,
    CodeEvidence,
)

SYSTEM_PROMPT = """Bạn là một Chuyên gia Kỹ thuật và Giám khảo Chấm thi Hackathon cấp cao (Principal Software Architect & Lead Judge).
Nhiệm vụ của bạn là đánh giá mã nguồn của thí sinh tại commit chỉ định dựa trên BỘ TIÊU CHÍ (RUBRIC) được cung cấp.

QUY TẮC BẮT BUỘC KHI CHẤM:
1. TÍNH CÔNG TÂM & CHÍNH XÁC:
   - Điểm số của từng tiêu chí phải nằm trong khoảng từ 0.0 đến max_score của tiêu chí đó (thường là 10.0).
   - Tuyệt đối không nhận xét chung chung hay chấm cảm tính.

2. QUY CHUẨN DẪN CHỨNG (EVIDENCE) - BẮT BUỘC:
   - Mọi lời khen (cộng điểm) hoặc lời phê (trừ điểm) đều phải có ít nhất 1 DẪN CHỨNG (evidence) trích trực tiếp từ source code.
   - Mỗi dẫn chứng phải chỉ rõ:
     + criteria_id: ID tiêu chí liên quan
     + type: "PENALTY" (nếu là lỗi/trừ điểm), "POSITIVE" (nếu là điểm sáng/cộng điểm), hoặc "NEUTRAL"
     + file_path: Đường dẫn chính xác của file
     + line_start & line_end: Số dòng bắt đầu và kết thúc (dựa trên số dòng đã được đánh số ở đầu mỗi dòng trong ngữ cảnh mã nguồn)
     + snippet: Đoạn mã thực tế (không chế code, trích đúng code của thí sinh)
     + comment: Lời giải thích kỹ thuật tại sao đoạn mã này vi phạm hoặc đạt chuẩn

3. ĐỊNH DẠNG ĐẦU RA:
   - Bắt buộc trả về đúng định dạng JSON tuân thủ schema được yêu cầu. Không thêm bất kỳ văn bản nào ngoài JSON.
"""

def build_user_prompt(job_input: GradingJobInput, code_context: str, stats: Dict[str, Any]) -> str:
    rubrics_text = []
    for r in job_input.rubrics:
        rubrics_text.append(
            f"- Tiêu chí #{r.criteria_id}: {r.name}\n"
            f"  + Mô tả: {r.description}\n"
            f"  + Trọng số (Weight): {r.weight}\n"
            f"  + Điểm tối đa (Max Score): {r.max_score}"
        )
    rubrics_str = "\n".join(rubrics_text)

    prompt = f"""Hãy đánh giá bài nộp sau đây:

THÔNG TIN BÀI THI:
- Submission ID: {job_input.submission_id}
- Repository URL: {job_input.repository.url}
- Commit SHA: {job_input.repository.commit_sha}
- Thống kê mã nguồn: {stats.get('total_files', 0)} files, {stats.get('total_lines', 0)} dòng code.

DANH SÁCH TIÊU CHÍ RUBRIC:
{rubrics_str}

======================================================================
MÃ NGUỒN CỦA BÀI THI (ĐÃ ĐƯỢC ĐÁNH SỐ DÒNG: 'số_dòng: code'):
======================================================================
{code_context}

======================================================================
YÊU CẦU ĐẦU RA:
Trả về duy nhất 1 JSON Object có cấu trúc chính xác như sau:
{{
  "overall_score": <float, điểm tổng có trọng số trên thang 10>,
  "criteria_results": [
    {{
      "criteria_id": <int>,
      "score": <float, điểm đạt được>,
      "feedback": "<string, nhận xét chi tiết>"
    }}
  ],
  "evidence": [
    {{
      "criteria_id": <int>,
      "type": "PENALTY" | "POSITIVE" | "NEUTRAL",
      "file_path": "<string, đường dẫn file>",
      "line_start": <int, số dòng bắt đầu>,
      "line_end": <int, số dòng kết thúc>,
      "snippet": "<string, đoạn code trích dẫn>",
      "comment": "<string, giải thích>"
    }}
  ],
  "summary": "<string, nhận xét tổng kết bài thi>"
}}
"""
    return prompt


class CloudAIGrader:
    def __init__(self, api_key: Optional[str] = None, model_name: str = "gemini-2.5-flash"):
        self.api_key = api_key or os.getenv("GEMINI_API_KEY")
        self.model_name = model_name
        
    def evaluate(
        self,
        job_input: GradingJobInput,
        code_context: str,
        stats: Dict[str, Any]
    ) -> GradingJobOutput:
        """Thực hiện gọi Cloud AI API để chấm điểm."""
        if not self.api_key:
            raise ValueError(
                "Chưa tìm thấy GEMINI_API_KEY! Vui lòng set biến môi trường GEMINI_API_KEY "
                "hoặc truyền trực tiếp vào CloudAIGrader(api_key=...)"
            )

        start_time = time.time()
        user_prompt = build_user_prompt(job_input, code_context, stats)

        endpoint_url = (
            f"https://generativelanguage.googleapis.com/v1beta/models/{self.model_name}:generateContent"
            f"?key={self.api_key}"
        )

        payload = {
            "system_instruction": {
                "parts": [{"text": SYSTEM_PROMPT}]
            },
            "contents": [
                {
                    "role": "user",
                    "parts": [{"text": user_prompt}]
                }
            ],
            "generationConfig": {
                "temperature": 0.2,
                "response_mime_type": "application/json"
            }
        }

        req = urllib.request.Request(
            endpoint_url,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"}
        )

        try:
            with urllib.request.urlopen(req, timeout=120) as response:
                res_data = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            err_body = e.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"Lỗi gọi Gemini API (HTTP {e.code}): {err_body}")

        # Trích xuất JSON từ kết quả Gemini
        candidates = res_data.get("candidates", [])
        if not candidates:
            raise RuntimeError(f"Gemini API không trả về candidate nào: {res_data}")

        raw_json_str = candidates[0].get("content", {}).get("parts", [{}])[0].get("text", "{}")
        parsed_result = json.loads(raw_json_str)

        elapsed_time = round(time.time() - start_time, 2)
        evaluated_at = datetime.now(timezone.utc).isoformat()

        # Ánh xạ criteria_results
        criteria_results = [
            CriterionResult(
                criteria_id=cr["criteria_id"],
                score=float(cr["score"]),
                feedback=cr["feedback"]
            )
            for cr in parsed_result.get("criteria_results", [])
        ]

        # Ánh xạ evidence
        evidence_list = [
            CodeEvidence(
                criteria_id=ev["criteria_id"],
                type=ev.get("type", "NEUTRAL"),
                file_path=ev["file_path"],
                line_start=int(ev["line_start"]),
                line_end=int(ev["line_end"]),
                snippet=ev["snippet"],
                comment=ev["comment"]
            )
            for ev in parsed_result.get("evidence", [])
        ]

        return GradingJobOutput(
            job_id=job_input.job_id,
            submission_id=job_input.submission_id,
            status="SUCCESS",
            evaluated_at=evaluated_at,
            execution_time_seconds=elapsed_time,
            model_name=self.model_name,
            overall_score=float(parsed_result.get("overall_score", 0.0)),
            criteria_results=criteria_results,
            evidence=evidence_list,
            summary=parsed_result.get("summary", ""),
            error_message=None
        )
