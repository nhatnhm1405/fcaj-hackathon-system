"""
Unit tests cho Standalone SQS Poller (`src/sqs_worker.py`).
Chạy hoàn toàn offline với mock SQS/S3/STS/DeepSeek (không cần boto3 trong CI).
"""

import io
import json
import os
import sys
import tempfile
import unittest
from unittest.mock import MagicMock, patch

AI_WORKER_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
REPO_ROOT_DIR = os.path.abspath(os.path.join(AI_WORKER_DIR, ".."))
EXAMPLES_SUCCESS_DIR = os.path.join(
    REPO_ROOT_DIR, "docs", "contracts", "ai-job-v1", "current-examples", "success"
)
sys.path.insert(0, AI_WORKER_DIR)

from src.ai_grader import AIGrader  # noqa: E402
from src.lambda_handler import LambdaRuntimeConfig, S3ArtifactStore  # noqa: E402
from src.sqs_worker import (  # noqa: E402
    SqsPollerConfig,
    load_env_file,
    poll_once,
    run_preflight,
)


class _InMemoryS3:
    def __init__(self):
        self.objects = {}

    def seed(self, key: str, raw: bytes):
        self.objects[key] = raw

    def get_object(self, **kwargs):
        data = self.objects[kwargs["Key"]]
        return {"ContentLength": len(data), "Body": io.BytesIO(data)}

    def put_object(self, **kwargs):
        self.objects[kwargs["Key"]] = kwargs["Body"]
        return {"ETag": '"etag-test"'}


class _FakeHTTPResponse:
    def __init__(self, payload: dict):
        self._raw = json.dumps(payload).encode("utf-8")

    def read(self):
        return self._raw

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False


class TestSqsWorker(unittest.TestCase):
    def _make_config(self) -> SqsPollerConfig:
        runtime = LambdaRuntimeConfig(
            aws_region="ap-southeast-1",
            storage_bucket="test-bucket",
            storage_prefix="poc",
            results_queue_url="https://sqs.ap-southeast-1.amazonaws.com/123456789012/fcaj-results",
            ai_provider="deepseek",
            deepseek_model="deepseek-flash",
            deepseek_api_url="https://api.deepseek.com/chat/completions",
            deepseek_api_key_parameter=None,
            max_source_chars=120000,
        )
        return SqsPollerConfig(
            runtime=runtime,
            jobs_queue_url="https://sqs.ap-southeast-1.amazonaws.com/123456789012/fcaj-jobs",
            aws_profile="fcaj-Thoth3010",
            wait_seconds=20,
            visibility_timeout=120,
        )

    def _seed_success_example(self):
        with open(os.path.join(EXAMPLES_SUCCESS_DIR, "job.json"), "rb") as fh:
            job_raw = fh.read().decode("utf-8")
        with open(os.path.join(EXAMPLES_SUCCESS_DIR, "manifest.json"), "rb") as fh:
            manifest_bytes = fh.read()
        with open(os.path.join(EXAMPLES_SUCCESS_DIR, "source.json"), "rb") as fh:
            source_bytes = fh.read()
        with open(os.path.join(EXAMPLES_SUCCESS_DIR, "rubric.json"), "rb") as fh:
            rubric_bytes = fh.read()

        job_obj = json.loads(job_raw)
        job_id = job_obj["jobId"]
        s3 = _InMemoryS3()
        s3.seed(f"poc/inputs/{job_id}/input-manifest.json", manifest_bytes)
        s3.seed(f"poc/inputs/{job_id}/source.json", source_bytes)
        s3.seed(f"poc/rubrics/{job_id}/rubric.json", rubric_bytes)
        return job_raw, job_id, s3

    def test_poll_once_empty_queue_returns_none(self):
        cfg = self._make_config()
        sqs = MagicMock()
        sqs.receive_message.return_value = {}
        store = S3ArtifactStore(_InMemoryS3(), "test-bucket", "poc")
        grader = AIGrader(api_key="test-key", model_name="deepseek-flash", provider="deepseek")

        result = poll_once(cfg, store=store, sqs_client=sqs, grader=grader)
        self.assertIsNone(result)
        sqs.receive_message.assert_called_once_with(
            QueueUrl=cfg.jobs_queue_url,
            MaxNumberOfMessages=1,
            WaitTimeSeconds=20,
            VisibilityTimeout=120,
            MessageSystemAttributeNames=["ApproximateReceiveCount", "SentTimestamp"],
        )
        sqs.delete_message.assert_not_called()

    def test_poll_once_processes_sends_result_then_deletes_job(self):
        cfg = self._make_config()
        job_raw, job_id, s3 = self._seed_success_example()
        sqs = MagicMock()
        sqs.receive_message.return_value = {
            "Messages": [
                {
                    "MessageId": "sqs-msg-1",
                    "ReceiptHandle": "rh-valid-1",
                    "Body": job_raw,
                    "Attributes": {"ApproximateReceiveCount": "1"},
                }
            ]
        }
        store = S3ArtifactStore(s3, "test-bucket", "poc")
        grader = AIGrader(api_key="test-key", model_name="deepseek-flash", provider="deepseek")

        ai_payload = {
            "choices": [
                {
                    "finish_reason": "stop",
                    "message": {
                        "content": json.dumps(
                            {
                                "summary": "Source snapshot reviewed cleanly.",
                                "strengths": ["Clear file structure."],
                                "concerns": ["Add automated tests."],
                                "criteria_results": [
                                    {
                                        "criteria_id": 1,
                                        "score": 8.5,
                                        "comment": "Implementation matches rubric expectations.",
                                    }
                                ],
                                "evidence": [
                                    {
                                        "criteria_id": 1,
                                        "file_path": "src/example.txt",
                                        "line_start": 1,
                                        "line_end": 1,
                                        "type": "POSITIVE",
                                        "snippet": "example",
                                        "comment": "Primary source entry.",
                                    }
                                ],
                                "repo": {
                                    "note": "Analyzed 1 file.",
                                    "tech_stack": ["Text"],
                                    "signals": ["Clean layout"],
                                    "red_flags": [],
                                },
                            }
                        )
                    },
                }
            ]
        }

        with patch("src.ai_grader.urllib.request.urlopen", return_value=_FakeHTTPResponse(ai_payload)):
            outcome = poll_once(cfg, store=store, sqs_client=sqs, grader=grader)

        self.assertIsNotNone(outcome)
        self.assertEqual(outcome["jobId"], job_id)
        self.assertEqual(outcome["status"], "SUCCEEDED")
        self.assertEqual(outcome["sqsMessageId"], "sqs-msg-1")
        self.assertIn(f"poc/results/{job_id}/output.json", s3.objects)

        sqs.send_message.assert_called_once()
        sqs.delete_message.assert_called_once_with(
            QueueUrl=cfg.jobs_queue_url,
            ReceiptHandle="rh-valid-1",
        )

    def test_poll_once_does_not_delete_message_when_processing_fails(self):
        cfg = self._make_config()
        sqs = MagicMock()
        sqs.receive_message.return_value = {
            "Messages": [
                {
                    "MessageId": "sqs-bad-1",
                    "ReceiptHandle": "rh-bad-1",
                    "Body": '{"broken": true}',
                }
            ]
        }
        store = S3ArtifactStore(_InMemoryS3(), "test-bucket", "poc")
        grader = AIGrader(api_key="test-key", model_name="deepseek-flash", provider="deepseek")

        with self.assertRaises(Exception):
            poll_once(cfg, store=store, sqs_client=sqs, grader=grader)

        sqs.send_message.assert_not_called()
        sqs.delete_message.assert_not_called()

    def test_run_preflight_reports_identity_and_queue_health(self):
        cfg = self._make_config()
        sts = MagicMock()
        sts.get_caller_identity.return_value = {
            "Arn": "arn:aws:iam::123456789012:user/Thoth3010"
        }
        sqs = MagicMock()
        sqs.get_queue_attributes.side_effect = [
            {
                "Attributes": {
                    "ApproximateNumberOfMessages": "0",
                    "ApproximateNumberOfMessagesNotVisible": "0",
                    "VisibilityTimeout": "120",
                    "RedrivePolicy": '{"deadLetterTargetArn":"arn:aws:sqs:..."}',
                }
            },
            {
                "Attributes": {
                    "ApproximateNumberOfMessages": "0",
                    "ApproximateNumberOfMessagesNotVisible": "0",
                    "VisibilityTimeout": "120",
                }
            },
        ]
        with patch.dict(os.environ, {"DEEPSEEK_API_KEY": "sk-test-dummy"}, clear=False):
            report = run_preflight(cfg, sts_client=sts, sqs_client=sqs)

        self.assertEqual(report["profile"], "fcaj-Thoth3010")
        self.assertEqual(report["apiKeySource"], "env:DEEPSEEK_API_KEY")
        self.assertTrue(report["jobsQueue"]["hasRedrivePolicy"])
        self.assertEqual(report["resultsQueue"]["visibilityTimeout"], "120")

    def test_load_env_file_parses_values_without_overwriting_existing(self):
        with tempfile.NamedTemporaryFile("w", delete=False, encoding="utf-8") as tmp:
            tmp.write("# comment\n")
            tmp.write('FCAJ_TEST_A="hello"\n')
            tmp.write("export FCAJ_TEST_B=world\n")
            tmp_path = tmp.name
        try:
            with patch.dict(os.environ, {"FCAJ_TEST_A": "preset"}, clear=False):
                os.environ.pop("FCAJ_TEST_B", None)
                loaded = load_env_file(tmp_path, override=False)
                self.assertEqual(loaded, 1)
                self.assertEqual(os.environ["FCAJ_TEST_A"], "preset")
                self.assertEqual(os.environ["FCAJ_TEST_B"], "world")
        finally:
            os.remove(tmp_path)


if __name__ == "__main__":
    unittest.main()
