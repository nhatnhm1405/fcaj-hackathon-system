"""
Unit & Integration tests cho AWS Lambda AI Worker v3 (`src/lambda_handler.py`).
Đối chiếu trực tiếp với bộ fixture chuẩn của Backend tại `docs/contracts/ai-job-v1/current-examples/success/`
và các quy tắc hậu kiểm trong `JobResultValidator.java`.
"""

import hashlib
import io
import json
import os
import re
import sys
import unittest
from unittest.mock import MagicMock, patch

AI_WORKER_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
REPO_ROOT_DIR = os.path.abspath(os.path.join(AI_WORKER_DIR, ".."))
EXAMPLES_SUCCESS_DIR = os.path.join(
    REPO_ROOT_DIR, "docs", "contracts", "ai-job-v1", "current-examples", "success"
)
sys.path.insert(0, AI_WORKER_DIR)

from src.lambda_handler import (  # noqa: E402
    IDENTITY_FIELDS,
    LambdaRuntimeConfig,
    clear_secret_cache,
    get_deepseek_api_key,
    lambda_handler,
)

_SAFE_TEXT_FORBIDDEN_RE = re.compile(
    r"(https?://|[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,})", flags=re.IGNORECASE
)


class _FakePreconditionFailed(Exception):
    def __init__(self):
        super().__init__("PreconditionFailed")
        self.response = {
            "Error": {"Code": "PreconditionFailed"},
            "ResponseMetadata": {"HTTPStatusCode": 412},
        }


class _InMemoryS3Client:
    """S3 mock nhẹ trong bộ nhớ, hỗ trợ prefix, IfNoneMatch='*' và get_object."""

    def __init__(self, fail_next_put_412: bool = False):
        self.objects = {}
        self.put_calls = []
        self.fail_next_put_412 = fail_next_put_412

    def seed(self, key: str, raw_bytes: bytes):
        self.objects[key] = raw_bytes

    def get_object(self, **kwargs):
        key = kwargs["Key"]
        if key not in self.objects:
            raise KeyError(f"Missing S3 object: {key}")
        data = self.objects[key]
        return {"ContentLength": len(data), "Body": io.BytesIO(data)}

    def put_object(self, **kwargs):
        self.put_calls.append(kwargs)
        key = kwargs["Key"]
        if self.fail_next_put_412:
            self.fail_next_put_412 = False
            raise _FakePreconditionFailed()
        if kwargs.get("IfNoneMatch") == "*" and key in self.objects:
            raise _FakePreconditionFailed()
        self.objects[key] = kwargs["Body"]
        return {"VersionId": "v1-test-version"}


def _assert_matches_job_result_validator(
    testcase: unittest.TestCase,
    output_doc: dict,
    request_doc: dict,
    rubric_doc: dict,
    source_doc: dict,
):
    """Kiểm định output JSON theo đúng tập luật trong JobResultValidator.java."""
    expected_root_keys = set(IDENTITY_FIELDS) | {
        "schemaVersion",
        "generatedAt",
        "provenance",
        "insight",
    }
    testcase.assertEqual(set(output_doc.keys()), expected_root_keys)
    testcase.assertEqual(output_doc["schemaVersion"], "fcaj.ai.output.v1")
    for field in IDENTITY_FIELDS:
        testcase.assertEqual(output_doc[field], request_doc[field])

    prov = output_doc["provenance"]
    testcase.assertEqual(
        set(prov.keys()), {"mode", "modelId", "modelRevision", "adapterVersion"}
    )
    testcase.assertEqual(prov["mode"], request_doc["mode"])
    for name in ("modelId", "modelRevision", "adapterVersion"):
        testcase.assertRegex(prov[name], r"^[a-zA-Z0-9_.-]{1,100}$")

    insight = output_doc["insight"]
    testcase.assertEqual(
        set(insight.keys()),
        {
            "summary",
            "strengths",
            "concerns",
            "criteriaInsights",
            "repo",
            "disclaimer",
            "model",
        },
    )
    testcase.assertEqual(insight["model"], prov["modelId"])

    def _assert_safe_str(val: str, max_len: int):
        testcase.assertIsInstance(val, str)
        testcase.assertTrue(bool(val.strip()))
        testcase.assertLessEqual(len(val), max_len)
        testcase.assertNotIn("\x00", val)
        testcase.assertIsNone(_SAFE_TEXT_FORBIDDEN_RE.search(val), f"Unsafe text: {val}")

    _assert_safe_str(insight["summary"], 4000)
    _assert_safe_str(insight["disclaimer"], 2000)
    for arr_field in ("strengths", "concerns"):
        arr = insight[arr_field]
        testcase.assertIsInstance(arr, list)
        testcase.assertLessEqual(len(arr), 20)
        for item in arr:
            _assert_safe_str(item, 2000)

    rubric_by_id = {c["criteriaId"]: c for c in rubric_doc["criteria"]}
    line_counts = {
        f["path"]: len(f["content"].split("\n")) for f in source_doc["files"]
    }
    items = insight["criteriaInsights"]
    testcase.assertEqual(len(items), len(rubric_by_id))
    seen_ids = set()
    for item in items:
        testcase.assertEqual(
            set(item.keys()),
            {
                "criteriaId",
                "criteriaName",
                "assessment",
                "suggestedScoreRange",
                "evidence",
            },
        )
        cid = item["criteriaId"]
        testcase.assertIn(cid, rubric_by_id)
        testcase.assertNotIn(cid, seen_ids)
        seen_ids.add(cid)
        testcase.assertEqual(item["criteriaName"], rubric_by_id[cid]["name"])
        testcase.assertIsNone(item["suggestedScoreRange"])
        _assert_safe_str(item["assessment"], 4000)

        ev_list = item["evidence"]
        testcase.assertIsInstance(ev_list, list)
        testcase.assertLessEqual(len(ev_list), 20)
        for ev in ev_list:
            testcase.assertEqual(set(ev.keys()), {"path", "startLine", "endLine"})
            testcase.assertIn(ev["path"], line_counts)
            testcase.assertGreaterEqual(ev["startLine"], 1)
            testcase.assertGreaterEqual(ev["endLine"], ev["startLine"])
            testcase.assertLessEqual(ev["endLine"], line_counts[ev["path"]])

    repo = insight["repo"]
    testcase.assertEqual(
        set(repo.keys()), {"analyzed", "note", "techStack", "signals", "redFlags"}
    )
    testcase.assertIs(repo["analyzed"], False)
    _assert_safe_str(repo["note"], 1000)
    for repo_arr in ("techStack", "signals", "redFlags"):
        testcase.assertIsInstance(repo[repo_arr], list)
        testcase.assertLessEqual(len(repo[repo_arr]), 20)
        for item in repo[repo_arr]:
            _assert_safe_str(item, 1000)


class TestLambdaHandlerV1(unittest.TestCase):
    def setUp(self):
        clear_secret_cache()
        self.config = LambdaRuntimeConfig(
            aws_region="ap-southeast-1",
            storage_bucket="fcaj-poc-data-test",
            storage_prefix="poc",
            results_queue_url="https://sqs.ap-southeast-1.amazonaws.com/582129522297/fcaj-poc-ai-results",
            ai_provider="deepseek",
            deepseek_model="deepseek-flash",
            deepseek_api_url="https://api.deepseek.com/chat/completions",
            deepseek_api_key_parameter="/fcaj/poc/deepseek-api-key",
            max_source_chars=120000,
        )

        with open(os.path.join(EXAMPLES_SUCCESS_DIR, "job.json"), "rb") as f:
            self.job_bytes = f.read()
        with open(os.path.join(EXAMPLES_SUCCESS_DIR, "manifest.json"), "rb") as f:
            self.manifest_bytes = f.read()
        with open(os.path.join(EXAMPLES_SUCCESS_DIR, "source.json"), "rb") as f:
            self.source_bytes = f.read()
        with open(os.path.join(EXAMPLES_SUCCESS_DIR, "rubric.json"), "rb") as f:
            self.rubric_bytes = f.read()

        self.job_doc = json.loads(self.job_bytes.decode("utf-8"))
        self.source_doc = json.loads(self.source_bytes.decode("utf-8"))
        self.rubric_doc = json.loads(self.rubric_bytes.decode("utf-8"))
        self.job_id = self.job_doc["jobId"]

    def _seed_s3(self) -> _InMemoryS3Client:
        s3 = _InMemoryS3Client()
        s3.seed(f"poc/inputs/{self.job_id}/input-manifest.json", self.manifest_bytes)
        s3.seed(f"poc/inputs/{self.job_id}/source.json", self.source_bytes)
        s3.seed(f"poc/rubrics/{self.job_id}/rubric.json", self.rubric_bytes)
        return s3

    @patch.dict(os.environ, {}, clear=True)
    @patch("urllib.request.urlopen")
    def test_lambda_handler_roundtrip_matches_backend_validator(self, mock_urlopen):
        s3 = self._seed_s3()
        sqs = MagicMock()
        ssm = MagicMock()
        ssm.get_parameter.return_value = {"Parameter": {"Value": "sk-deepseek-ssm-secret"}}

        # Giả lập DeepSeek trả về có lẫn secret, URL, email, NUL, <think> và evidence ảo giác
        fake_llm_json = {
            "overall_score": 8.5,
            "criteria_results": [
                {
                    "criteria_id": 8,
                    "score": 8.5,
                    "feedback": (
                        "Xem chi tiết tại https://evil.example.com/leak hoặc "
                        "liên hệ student@fpt.edu.vn; password=\"MySecret12345\"\x00."
                    ),
                }
            ],
            "evidence": [
                {
                    "criteria_id": 8,
                    "type": "POSITIVE",
                    "file_path": "/src/example.txt",
                    "line_start": 1,
                    "line_end": 2,
                    "snippet": "   1: first line\n   2: second line",
                    "comment": "Code rõ ràng, tham khảo https://docs.example.com.",
                },
                {
                    "criteria_id": 8,
                    "type": "PENALTY",
                    "file_path": "src/non_existent_hallucinated.java",
                    "line_start": 10,
                    "line_end": 20,
                    "snippet": "class Fake {}",
                    "comment": "File bịa.",
                },
                {
                    "criteria_id": 8,
                    "type": "NEUTRAL",
                    "file_path": "src/example.txt",
                    "line_start": 99,
                    "line_end": 120,
                    "snippet": "out of range",
                    "comment": "Dòng vượt quá tổng số dòng thực.",
                },
            ],
            "summary": "Tóm tắt có link HTTP://example.org và email lead@fpt.edu.vn.",
            "strengths": ["Cấu trúc sạch sẽ (xem https://repo.example.com)"],
            "concerns": ["Cần bổ sung test (báo cho dev@fpt.edu.vn)"],
            "repo_analysis": {
                "note": "Phân tích repo tại https://github.com/org/repo",
                "tech_stack": ["Plain Text"],
                "signals": ["Có file mẫu rõ ràng"],
                "red_flags": [],
            },
        }
        fake_api_resp = {
            "id": "chatcmpl-v3",
            "model": "deepseek-flash",
            "choices": [
                {
                    "index": 0,
                    "finish_reason": "stop",
                    "message": {
                        "role": "assistant",
                        "content": "<think>Analyze</think>" + json.dumps(fake_llm_json),
                    },
                }
            ],
        }
        mock_resp = MagicMock()
        mock_resp.read.return_value = json.dumps(fake_api_resp).encode("utf-8")
        mock_resp.__enter__.return_value = mock_resp
        mock_urlopen.return_value = mock_resp

        event = {
            "Records": [
                {
                    "messageId": "sqs-msg-001",
                    "body": self.job_bytes.decode("utf-8"),
                }
            ]
        }

        result = lambda_handler(
            event,
            s3_client=s3,
            sqs_client=sqs,
            ssm_client=ssm,
            config=self.config,
        )

        self.assertEqual(result, {"batchItemFailures": []})
        ssm.get_parameter.assert_called_once_with(
            Name="/fcaj/poc/deepseek-api-key", WithDecryption=True
        )

        # Kiểm tra ghi S3 bất biến bằng IfNoneMatch='*'
        self.assertEqual(len(s3.put_calls), 1)
        put_call = s3.put_calls[0]
        expected_physical_key = f"poc/results/{self.job_id}/output.json"
        self.assertEqual(put_call["Bucket"], "fcaj-poc-data-test")
        self.assertEqual(put_call["Key"], expected_physical_key)
        self.assertEqual(put_call["IfNoneMatch"], "*")
        self.assertEqual(put_call["ContentType"], "application/json")

        output_bytes = s3.objects[expected_physical_key]
        output_doc = json.loads(output_bytes.decode("utf-8"))
        _assert_matches_job_result_validator(
            self, output_doc, self.job_doc, self.rubric_doc, self.source_doc
        )

        # Kiểm tra loại bỏ evidence bịa file và dòng vượt quá giới hạn
        ev_items = output_doc["insight"]["criteriaInsights"][0]["evidence"]
        self.assertEqual(ev_items, [{"path": "src/example.txt", "startLine": 1, "endLine": 2}])

        # Kiểm tra không lộ secret
        serialized_insight = json.dumps(output_doc["insight"], ensure_ascii=False)
        self.assertNotIn("MySecret12345", serialized_insight)
        self.assertNotIn("sk-deepseek-ssm-secret", serialized_insight)

        # Kiểm tra completion message gửi lên SQS Results
        sqs.send_message.assert_called_once()
        sent_kwargs = sqs.send_message.call_args.kwargs
        self.assertEqual(sent_kwargs["QueueUrl"], self.config.results_queue_url)
        completion = json.loads(sent_kwargs["MessageBody"])
        self.assertEqual(completion["schemaVersion"], "fcaj.ai.message.v1")
        self.assertEqual(completion["eventType"], "AI_JOB_COMPLETED")
        self.assertEqual(completion["status"], "SUCCEEDED")
        for field in IDENTITY_FIELDS:
            self.assertEqual(completion[field], self.job_doc[field])
        self.assertEqual(
            completion["result"],
            {
                "key": f"results/{self.job_id}/output.json",
                "sha256": hashlib.sha256(output_bytes).hexdigest(),
                "sizeBytes": len(output_bytes),
                "contentType": "application/json",
                "versionId": None,
            },
        )

    @patch.dict(os.environ, {"DEEPSEEK_API_KEY": "sk-env-key"}, clear=True)
    @patch("urllib.request.urlopen")
    def test_idempotent_retry_reuses_existing_s3_output_on_412(self, mock_urlopen):
        s3 = self._seed_s3()
        existing_output = {
            **{f: self.job_doc[f] for f in IDENTITY_FIELDS},
            "schemaVersion": "fcaj.ai.output.v1",
            "generatedAt": "2026-10-28T04:55:00Z",
            "provenance": {
                "mode": "MOCK",
                "modelId": "deepseek-flash",
                "modelRevision": "deepseek-v3",
                "adapterVersion": "fcaj-lambda-worker-v1",
            },
            "insight": {
                "summary": "Cached previous result.",
                "strengths": ["OK"],
                "concerns": ["None"],
                "criteriaInsights": [
                    {
                        "criteriaId": 8,
                        "criteriaName": "Technical implementation",
                        "assessment": "Cached",
                        "suggestedScoreRange": None,
                        "evidence": [{"path": "src/example.txt", "startLine": 1, "endLine": 1}],
                    }
                ],
                "repo": {
                    "analyzed": False,
                    "note": "Cached",
                    "techStack": [],
                    "signals": [],
                    "redFlags": [],
                },
                "disclaimer": "Reference only.",
                "model": "deepseek-flash",
            },
        }
        existing_bytes = json.dumps(existing_output, separators=(",", ":")).encode("utf-8")
        s3.seed(f"poc/results/{self.job_id}/output.json", existing_bytes)

        fake_api_resp = {
            "model": "deepseek-flash",
            "choices": [
                {
                    "finish_reason": "stop",
                    "message": {
                        "content": json.dumps(
                            {
                                "criteria_results": [
                                    {"criteria_id": 8, "score": 7.0, "feedback": "Retry run."}
                                ],
                                "evidence": [],
                                "summary": "New summary on retry.",
                            }
                        )
                    },
                }
            ],
        }
        mock_resp = MagicMock()
        mock_resp.read.return_value = json.dumps(fake_api_resp).encode("utf-8")
        mock_resp.__enter__.return_value = mock_resp
        mock_urlopen.return_value = mock_resp

        sqs = MagicMock()
        event = {"Records": [{"messageId": "sqs-retry-1", "body": self.job_bytes.decode("utf-8")}]}
        result = lambda_handler(event, s3_client=s3, sqs_client=sqs, config=self.config)

        self.assertEqual(result, {"batchItemFailures": []})
        completion = json.loads(sqs.send_message.call_args.kwargs["MessageBody"])
        self.assertEqual(completion["result"]["sha256"], hashlib.sha256(existing_bytes).hexdigest())
        self.assertEqual(completion["result"]["sizeBytes"], len(existing_bytes))
        self.assertIsNone(completion["result"]["versionId"])

    @patch.dict(os.environ, {"DEEPSEEK_API_KEY": "sk-env-key"}, clear=True)
    @patch("urllib.request.urlopen")
    def test_checksum_mismatch_marks_batch_item_failure_for_dlq(self, mock_urlopen):
        s3 = self._seed_s3()
        # Làm hỏng bytes source.json trên S3
        s3.seed(f"poc/inputs/{self.job_id}/source.json", b'{"tampered":true}')
        sqs = MagicMock()

        event = {"Records": [{"messageId": "sqs-bad-1", "body": self.job_bytes.decode("utf-8")}]}
        result = lambda_handler(event, s3_client=s3, sqs_client=sqs, config=self.config)

        self.assertEqual(result, {"batchItemFailures": [{"itemIdentifier": "sqs-bad-1"}]})
        mock_urlopen.assert_not_called()
        sqs.send_message.assert_not_called()
        self.assertEqual(len(s3.put_calls), 0)

    @patch.dict(os.environ, {"DEEPSEEK_API_KEY": "sk-env-key"}, clear=True)
    @patch("urllib.request.urlopen")
    def test_immutable_output_conflict_with_wrong_identity_fails_batch(self, mock_urlopen):
        s3 = self._seed_s3()
        # Ghi sẵn output giả mạo với jobId khác tại cùng key
        s3.seed(
            f"poc/results/{self.job_id}/output.json",
            json.dumps({"schemaVersion": "fcaj.ai.output.v1", "jobId": "wrong-id"}).encode("utf-8"),
        )
        fake_api_resp = {
            "model": "deepseek-flash",
            "choices": [
                {
                    "finish_reason": "stop",
                    "message": {
                        "content": json.dumps(
                            {
                                "criteria_results": [
                                    {"criteria_id": 8, "score": 8.0, "feedback": "Good."}
                                ],
                                "evidence": [],
                                "summary": "Good summary.",
                            }
                        )
                    },
                }
            ],
        }
        mock_resp = MagicMock()
        mock_resp.read.return_value = json.dumps(fake_api_resp).encode("utf-8")
        mock_resp.__enter__.return_value = mock_resp
        mock_urlopen.return_value = mock_resp

        sqs = MagicMock()
        event = {"Records": [{"messageId": "sqs-conflict-1", "body": self.job_bytes.decode("utf-8")}]}
        result = lambda_handler(event, s3_client=s3, sqs_client=sqs, config=self.config)

        self.assertEqual(result, {"batchItemFailures": [{"itemIdentifier": "sqs-conflict-1"}]})
        sqs.send_message.assert_not_called()

    @patch.dict(os.environ, {}, clear=True)
    def test_ssm_parameter_key_is_cached_across_warm_invocations(self):
        ssm = MagicMock()
        ssm.get_parameter.return_value = {"Parameter": {"Value": "sk-ssm-cached"}}

        first = get_deepseek_api_key(self.config, ssm_client=ssm)
        second = get_deepseek_api_key(self.config, ssm_client=ssm)

        self.assertEqual(first, "sk-ssm-cached")
        self.assertEqual(second, "sk-ssm-cached")
        ssm.get_parameter.assert_called_once_with(
            Name="/fcaj/poc/deepseek-api-key", WithDecryption=True
        )


if __name__ == "__main__":
    unittest.main()
