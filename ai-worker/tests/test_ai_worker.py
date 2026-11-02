"""
Bộ kiểm thử tự động (Unit & Integration Tests) cho AI Evaluation Worker.
Kiểm tra:
1. Đồng bộ hợp đồng dữ liệu (sample_input.json <-> sample_output.json <-> schema.py)
2. Đối chiếu evidence trong sample_output.json với mã nguồn thực tế
3. Luồng chạy Mock Worker (mock_worker.py)
4. Luồng quét, lọc mã nguồn & đánh số dòng (code_cleaner.py)
5. Prompt Engineering, hậu xử lý bảo mật (redact_secrets, strip_line_number_prefix)
   và tích hợp DeepSeek / Gemini API trong ai_grader.py
"""

import io
import json
import os
import sys
import tempfile
import unittest
from unittest.mock import patch, MagicMock

AI_WORKER_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
REPO_ROOT_DIR = os.path.abspath(os.path.join(AI_WORKER_DIR, ".."))
sys.path.insert(0, AI_WORKER_DIR)

from src.schema import (
    GradingJobInput,
    GradingJobOutput,
    CriterionResult,
    CodeEvidence,
    compute_weighted_overall_score,
)
from src.mock_worker import run_mock_evaluation
from src.code_cleaner import scan_and_collect_sources, format_code_with_line_numbers
from src.github_fetcher import parse_github_owner_repo
from src.ai_grader import (
    SYSTEM_PROMPT,
    build_user_prompt,
    strip_line_number_prefix,
    redact_secrets,
    CloudAIGrader,
)


class TestContractsAndSamples(unittest.TestCase):
    def setUp(self):
        self.contracts_dir = os.path.join(AI_WORKER_DIR, "contracts")
        self.sample_input_path = os.path.join(self.contracts_dir, "sample_input.json")
        self.sample_output_path = os.path.join(self.contracts_dir, "sample_output.json")

    def test_sample_input_and_output_are_valid_and_synchronized(self):
        job_input = GradingJobInput.from_json_file(self.sample_input_path)
        job_output = GradingJobOutput.from_json_file(self.sample_output_path)

        # Kiểm tra chéo giữa input và output
        job_output.validate(job_input)
        self.assertEqual(job_output.overall_score, 8.05)

        # Đảm bảo tất cả 4 tiêu chí trong sample_input đều có ít nhất 1 dẫn chứng trong sample_output
        input_criteria_ids = {r.criteria_id for r in job_input.rubrics}
        evidence_criteria_ids = {ev.criteria_id for ev in job_output.evidence}
        self.assertEqual(input_criteria_ids, evidence_criteria_ids)

    def test_mock_worker_matches_sample_output(self):
        job_input = GradingJobInput.from_json_file(self.sample_input_path)
        sample_output = GradingJobOutput.from_json_file(self.sample_output_path)

        mock_result = run_mock_evaluation(job_input, sleep_seconds=0.0)
        self.assertEqual(mock_result.overall_score, sample_output.overall_score)
        self.assertEqual(mock_result.summary, sample_output.summary)
        self.assertEqual(mock_result.criteria_results, sample_output.criteria_results)
        self.assertEqual(mock_result.evidence, sample_output.evidence)

    def test_sample_output_evidence_files_and_lines_exist(self):
        sample_output = GradingJobOutput.from_json_file(self.sample_output_path)
        for ev in sample_output.evidence:
            full_path = os.path.join(REPO_ROOT_DIR, ev.file_path)
            self.assertTrue(
                os.path.isfile(full_path),
                f"Evidence file không tồn tại: {ev.file_path}",
            )
            with open(full_path, "r", encoding="utf-8") as f:
                lines = f.read().splitlines()
            self.assertLessEqual(
                ev.line_end,
                len(lines),
                f"line_end ({ev.line_end}) vượt quá tổng số dòng ({len(lines)}) của {ev.file_path}",
            )


class TestCodeCleanerAndFetcher(unittest.TestCase):
    def test_parse_github_owner_repo(self):
        owner, repo = parse_github_owner_repo(
            "https://github.com/nhatnhm1405/fcaj-hackathon-system.git"
        )
        self.assertEqual(owner, "nhatnhm1405")
        self.assertEqual(repo, "fcaj-hackathon-system")

    def test_scan_and_collect_sources_respects_ignore_and_priority(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            os.makedirs(os.path.join(tmpdir, "src"))
            os.makedirs(os.path.join(tmpdir, "docs", "draw io files"))
            os.makedirs(os.path.join(tmpdir, "node_modules", "pkg"))

            with open(os.path.join(tmpdir, "src", "App.java"), "w", encoding="utf-8") as f:
                f.write("public class App {\n    public static void main(String[] a) {}\n}\n")
            with open(
                os.path.join(tmpdir, "docs", "draw io files", "diagram.xml"),
                "w",
                encoding="utf-8",
            ) as f:
                f.write("<xml>ignored</xml>")
            with open(
                os.path.join(tmpdir, "node_modules", "pkg", "index.js"),
                "w",
                encoding="utf-8",
            ) as f:
                f.write("console.log('ignored');")

            files_dict, stats = scan_and_collect_sources(
                tmpdir,
                custom_ignore_dirs=["docs/draw io files"],
                target_languages=["java"],
                max_total_files=10,
            )

            self.assertIn("src/App.java", files_dict)
            self.assertNotIn("docs/draw io files/diagram.xml", files_dict)
            self.assertNotIn("node_modules/pkg/index.js", files_dict)
            self.assertEqual(stats["total_files"], 1)
            self.assertEqual(stats["total_lines"], 3)

            formatted = format_code_with_line_numbers(files_dict)
            self.assertIn("==================== FILE: src/App.java ====================", formatted)
            self.assertIn("   1: public class App {", formatted)


class TestPromptAndAIGrader(unittest.TestCase):
    def setUp(self):
        self.contracts_dir = os.path.join(AI_WORKER_DIR, "contracts")
        self.job_input = GradingJobInput.from_json_file(
            os.path.join(self.contracts_dir, "sample_input.json")
        )

    def test_prompt_contains_security_boundaries_and_rules(self):
        prompt = build_user_prompt(
            self.job_input,
            "   1: public class Main {}",
            {"total_files": 1, "total_lines": 1, "skipped_files": 0},
        )
        self.assertIn("<CANDIDATE_SOURCE_CODE>", prompt)
        self.assertIn("</CANDIDATE_SOURCE_CODE>", prompt)
        self.assertIn("THIEU_DU_LIEU", SYSTEM_PROMPT)
        self.assertIn("***REDACTED***", SYSTEM_PROMPT)

    def test_strip_line_number_prefix(self):
        raw_snippet = "  10: @Entity\n  11: @Table(name = \"Score\")\n  12: public class Score {"
        cleaned = strip_line_number_prefix(raw_snippet)
        self.assertEqual(cleaned, '@Entity\n@Table(name = "Score")\npublic class Score {')

        # Không làm hỏng code bình thường không có số dòng
        normal_code = 'const obj = {\n  key: "value"\n};'
        self.assertEqual(strip_line_number_prefix(normal_code), normal_code)

    def test_redact_secrets(self):
        raw = (
            'PASSWORD = os.getenv("MQTT_PASSWORD", "mq_2fXRJxD_lAVgHEXTMF33JQ")\n'
            "spring.datasource.password=${DB_PASSWORD:TrangNhi2004}\n"
            'api_key = "sk-1234567890abcdef12345678"'
        )
        redacted = redact_secrets(raw)
        self.assertNotIn("mq_2fXRJxD_lAVgHEXTMF33JQ", redacted)
        self.assertNotIn("TrangNhi2004", redacted)
        self.assertNotIn("sk-1234567890abcdef12345678", redacted)
        self.assertIn("***REDACTED***", redacted)

    @patch("urllib.request.urlopen")
    def test_deepseek_grader_recomputes_score_and_cleans_output(self, mock_urlopen):
        # Giả lập LLM trả về overall_score tính nhầm (9.99) và snippet dính số dòng + lộ secret
        fake_llm_json = {
            "overall_score": 9.99,
            "criteria_results": [
                {"criteria_id": 1, "score": 8.5, "feedback": "Kiến trúc tốt."},
                {"criteria_id": 2, "score": 7.0, "feedback": "Lộ password=SuperSecret12345 trong config."},
                {"criteria_id": 3, "score": 8.0, "feedback": "Unit test đầy đủ."},
                {"criteria_id": 4, "score": 9.5, "feedback": "Tài liệu rõ ràng."},
            ],
            "evidence": [
                {
                    "criteria_id": 2,
                    "type": "PENALTY",
                    "file_path": "/src/main/resources/application.properties",
                    "line_start": 13,
                    "line_end": 14,
                    "snippet": "  13: spring.datasource.username=root\n  14: spring.datasource.password=${DB_PASSWORD:TrangNhi2004}",
                    "comment": "Hardcode password trong file cấu hình.",
                }
            ],
            "summary": "Tổng kết bài thi tốt.",
        }

        fake_api_response = {
            "id": "chatcmpl-test",
            "model": "deepseek-v4.1-flash-1017",
            "choices": [
                {
                    "index": 0,
                    "finish_reason": "stop",
                    "message": {"role": "assistant", "content": json.dumps(fake_llm_json)},
                }
            ],
        }

        mock_resp = MagicMock()
        mock_resp.read.return_value = json.dumps(fake_api_response).encode("utf-8")
        mock_resp.__enter__.return_value = mock_resp
        mock_urlopen.return_value = mock_resp

        grader = CloudAIGrader(api_key="test-key", provider="deepseek")
        output = grader.evaluate(
            self.job_input,
            "=== FILE: src/Main.java ===\n   1: class Main {}",
            {"total_files": 1, "total_lines": 1},
        )

        # Kiểm tra Python đã tự tính lại overall_score = 8.05 thay vì tin con số 9.99 của LLM
        self.assertEqual(output.overall_score, 8.05)
        # Kiểm tra ghi nhận đúng model thực tế từ server response
        self.assertEqual(output.model_name, "deepseek-v4.1-flash-1017")
        # Kiểm tra đã strip tiền tố '  13: ' và che mật khẩu 'TrangNhi2004'
        ev = output.evidence[0]
        self.assertEqual(ev.file_path, "src/main/resources/application.properties")
        self.assertFalse(ev.snippet.startswith("  13:"))
        self.assertNotIn("TrangNhi2004", ev.snippet)
        self.assertIn("***REDACTED***", ev.snippet)
        self.assertNotIn("SuperSecret12345", output.criteria_results[1].feedback)

    @patch("urllib.request.urlopen")
    def test_deepseek_grader_rejects_truncated_length_response(self, mock_urlopen):
        fake_api_response = {
            "id": "chatcmpl-truncated",
            "model": "deepseek-chat",
            "choices": [
                {
                    "index": 0,
                    "finish_reason": "length",
                    "message": {"role": "assistant", "content": '{"overall_score": 7.5'},
                }
            ],
        }
        mock_resp = MagicMock()
        mock_resp.read.return_value = json.dumps(fake_api_response).encode("utf-8")
        mock_resp.__enter__.return_value = mock_resp
        mock_urlopen.return_value = mock_resp

        grader = CloudAIGrader(api_key="test-key", provider="deepseek")
        with self.assertRaises(RuntimeError) as ctx:
            grader.evaluate(self.job_input, "code", {"total_files": 1, "total_lines": 1})
        self.assertIn("finish_reason='length'", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
