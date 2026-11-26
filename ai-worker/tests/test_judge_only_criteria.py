"""Tiêu chí thuyết trình do giám khảo chấm: AI không chấm và không trừ điểm vì thiếu slide/video."""

import json
import os
import sys
import unittest
from unittest.mock import patch

AI_WORKER_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, AI_WORKER_DIR)

from src.ai_grader import (  # noqa: E402
    AIGrader,
    JUDGE_ONLY_ASSESSMENT,
    _format_rubric_lines,
    is_judge_only_criterion,
)
from tests.test_smart_ranking_and_flash import _FakeHTTPResponse  # noqa: E402

RUBRIC = [
    {"criteriaId": 1, "name": "Technical", "maxScore": 10, "weight": 1.0},
    {"criteriaId": 2, "name": "Presentation", "maxScore": 10, "weight": 1.0},
]


class TestJudgeOnlyCriteria(unittest.TestCase):
    def test_detects_presentation_criteria_by_name(self):
        for name in ["Presentation", "Pitch", "Thuyết trình", "Trình bày & demo", "Video demo"]:
            self.assertTrue(is_judge_only_criterion({"name": name}), name)
        for name in ["Technical", "Idea", "UI/UX", "Completeness"]:
            self.assertFalse(is_judge_only_criterion({"name": name}), name)

    def test_prompt_marks_presentation_as_judge_only(self):
        lines = _format_rubric_lines(RUBRIC)
        self.assertIn("Presentation [GIÁM KHẢO CHẤM TRỰC TIẾP", lines)
        self.assertNotIn("Technical [GIÁM KHẢO", lines)

    def test_presentation_is_not_scored_and_not_a_concern(self):
        fake = {
            "model": "deepseek-flash",
            "choices": [{
                "finish_reason": "stop",
                "message": {"content": json.dumps({
                    "criteria_results": [
                        {"criteria_id": 1, "score": 8.0, "feedback": "Kiến trúc tốt."},
                        {"criteria_id": 2, "score": 2.0, "feedback": "THIEU_DU_LIEU: không có slide."},
                    ],
                    "evidence": [{"criteria_id": 2, "type": "PENALTY", "file_path": "src/app.py",
                                  "line_start": 1, "line_end": 1, "snippet": "x = 1", "comment": "Thiếu video."}],
                    "concerns": ["Thiếu video demo và slide pitch", "Không có unit test"],
                    "summary": "Ổn.",
                })},
            }],
        }
        grader = AIGrader(api_key="sk-test", provider="deepseek")
        with patch("src.ai_grader.urllib.request.urlopen", return_value=_FakeHTTPResponse(fake)):
            result = grader.evaluate_v1_artifacts(
                submission_id=1, commit_sha="a" * 40,
                source_files=[{"path": "src/app.py", "content": "x = 1\n"}],
                rubric_criteria=RUBRIC,
            )
        insight = result["insight"]
        text = json.dumps(insight, ensure_ascii=False)
        insights = {i["criteriaId"]: i for i in insight["criteriaInsights"]}
        self.assertEqual(insights[2]["assessment"], JUDGE_ONLY_ASSESSMENT)
        self.assertEqual(insights[2]["evidence"], [])
        self.assertTrue(insights[1]["assessment"].startswith("[Tham khảo: 8.0/"))
        self.assertIn("Không có unit test", insight["concerns"])
        self.assertNotIn("Thiếu video demo và slide pitch", text)


if __name__ == "__main__":
    unittest.main()
