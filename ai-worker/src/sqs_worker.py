"""
Standalone SQS Polling AI Worker cho kiểm thử tích hợp E2E trên AWS (Kiến trúc v3).

Tái sử dụng 100% logic kiểm định contract, đọc/ghi S3 bất biến và chấm điểm AI
từ `src/lambda_handler.py` (`process_job_message`), bổ sung vòng lặp long-poll SQS
theo đúng đặc tả `docs/contracts/ai-job-v1/CURRENT.md`:
- `ReceiveMessage` trên SQS (Jobs): `WaitTimeSeconds=20`, `VisibilityTimeout=120`, `MaxNumberOfMessages=1`
- Ghi `results/{jobId}/output.json` (`IfNoneMatch="*"`) -> gửi `AI_JOB_COMPLETED` vào SQS (Results)
- Chỉ `DeleteMessage` khỏi SQS (Jobs) sau khi gửi completion thành công (lỗi giữ nguyên để retry/DLQ)

Hỗ trợ credential ngắn hạn qua `aws login --profile fcaj-Thoth3010` (`--profile` hoặc `AWS_PROFILE`).
"""

import argparse
from dataclasses import dataclass
import json
import logging
import os
import signal
import sys
import time
from typing import Any, Dict, Optional

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from src.ai_grader import AIGrader
from src.lambda_handler import (
    LambdaRuntimeConfig,
    S3ArtifactStore,
    get_deepseek_api_key,
    process_job_message,
)

logger = logging.getLogger(__name__)
if not logger.handlers:
    logging.basicConfig(
        level=os.getenv("LOG_LEVEL", "INFO"),
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )

DEFAULT_WAIT_SECONDS = 20
DEFAULT_VISIBILITY_TIMEOUT = 120


@dataclass(frozen=True)
class SqsPollerConfig:
    runtime: LambdaRuntimeConfig
    jobs_queue_url: str
    aws_profile: Optional[str] = None
    wait_seconds: int = DEFAULT_WAIT_SECONDS
    visibility_timeout: int = DEFAULT_VISIBILITY_TIMEOUT

    @classmethod
    def from_env(
        cls,
        *,
        aws_profile: Optional[str] = None,
        wait_seconds: int = DEFAULT_WAIT_SECONDS,
        visibility_timeout: int = DEFAULT_VISIBILITY_TIMEOUT,
    ) -> "SqsPollerConfig":
        runtime = LambdaRuntimeConfig.from_env()
        jobs_queue_url = (os.getenv("APP_AI_JOBS_JOBS_QUEUE_URL") or "").strip()
        if not jobs_queue_url:
            raise ValueError("Missing required runtime setting: APP_AI_JOBS_JOBS_QUEUE_URL")
        if jobs_queue_url == runtime.results_queue_url:
            raise ValueError("APP_AI_JOBS_JOBS_QUEUE_URL and APP_AI_JOBS_RESULTS_QUEUE_URL must differ")
        resolved_profile = (aws_profile or os.getenv("AWS_PROFILE") or "").strip() or None
        return cls(
            runtime=runtime,
            jobs_queue_url=jobs_queue_url,
            aws_profile=resolved_profile,
            wait_seconds=max(0, min(20, int(wait_seconds))),
            visibility_timeout=max(30, int(visibility_timeout)),
        )


def load_env_file(env_path: str, *, override: bool = False) -> int:
    """Nạp biến môi trường từ file `.env` cục bộ bằng thư viện chuẩn Python."""
    if not env_path or not os.path.isfile(env_path):
        return 0
    loaded = 0
    with open(env_path, "r", encoding="utf-8") as fh:
        for raw_line in fh:
            line = raw_line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, val = line.split("=", 1)
            key = key.strip()
            if key.startswith("export "):
                key = key[7:].strip()
            if not key:
                continue
            val = val.strip()
            if len(val) >= 2 and val[0] == val[-1] and val[0] in ("'", '"'):
                val = val[1:-1]
            if override or not os.getenv(key):
                os.environ[key] = val
                loaded += 1
    return loaded


def create_aws_clients(
    region: str, aws_profile: Optional[str] = None
) -> Dict[str, Any]:
    """Khởi tạo boto3 Session (hỗ trợ profile từ `aws login`) và các client cần thiết."""
    import boto3  # Lazy import so CI unit tests run without boto3 installed

    session = boto3.Session(profile_name=aws_profile, region_name=region)
    return {
        "sts": session.client("sts", region_name=region),
        "s3": session.client("s3", region_name=region),
        "sqs": session.client("sqs", region_name=region),
        "ssm": session.client("ssm", region_name=region),
    }


def build_grader(runtime: LambdaRuntimeConfig, ssm_client: Optional[Any] = None) -> AIGrader:
    """Khởi tạo AIGrader từ biến môi trường hoặc SSM Parameter Store."""
    api_key = None
    if runtime.ai_provider == "deepseek":
        api_key = get_deepseek_api_key(runtime, ssm_client=ssm_client)
    elif runtime.ai_provider == "gemini":
        api_key = (os.getenv("GEMINI_API_KEY") or "").strip() or None

    return AIGrader(
        api_key=api_key,
        model_name=runtime.deepseek_model if runtime.ai_provider == "deepseek" else None,
        provider=runtime.ai_provider,
        base_url=runtime.deepseek_api_url if runtime.ai_provider == "deepseek" else None,
        max_tokens=runtime.max_output_tokens,
    )


def run_preflight(
    config: SqsPollerConfig,
    *,
    sts_client: Any,
    sqs_client: Any,
) -> Dict[str, Any]:
    """Kiểm tra read-only IAM identity, thuộc tính 2 hàng đợi SQS và sẵn sàng API key."""
    identity = sts_client.get_caller_identity()
    jobs_attrs = sqs_client.get_queue_attributes(
        QueueUrl=config.jobs_queue_url,
        AttributeNames=[
            "ApproximateNumberOfMessages",
            "ApproximateNumberOfMessagesNotVisible",
            "VisibilityTimeout",
            "RedrivePolicy",
        ],
    ).get("Attributes", {})
    results_attrs = sqs_client.get_queue_attributes(
        QueueUrl=config.runtime.results_queue_url,
        AttributeNames=[
            "ApproximateNumberOfMessages",
            "ApproximateNumberOfMessagesNotVisible",
            "VisibilityTimeout",
        ],
    ).get("Attributes", {})

    key_source = "missing"
    if config.runtime.ai_provider == "deepseek":
        if (os.getenv("DEEPSEEK_API_KEY") or "").strip():
            key_source = "env:DEEPSEEK_API_KEY"
        elif config.runtime.deepseek_api_key_parameter:
            key_source = f"ssm:{config.runtime.deepseek_api_key_parameter}"
    elif config.runtime.ai_provider == "gemini":
        key_source = "env:GEMINI_API_KEY" if (os.getenv("GEMINI_API_KEY") or "").strip() else "missing"
    else:
        key_source = f"provider:{config.runtime.ai_provider}"

    return {
        "arn": identity.get("Arn"),
        "region": config.runtime.aws_region,
        "profile": config.aws_profile,
        "storageBucket": config.runtime.storage_bucket,
        "storagePrefix": config.runtime.storage_prefix,
        "aiProvider": config.runtime.ai_provider,
        "apiKeySource": key_source,
        "jobsQueue": {
            "visible": jobs_attrs.get("ApproximateNumberOfMessages"),
            "inFlight": jobs_attrs.get("ApproximateNumberOfMessagesNotVisible"),
            "visibilityTimeout": jobs_attrs.get("VisibilityTimeout"),
            "hasRedrivePolicy": bool(jobs_attrs.get("RedrivePolicy")),
        },
        "resultsQueue": {
            "visible": results_attrs.get("ApproximateNumberOfMessages"),
            "inFlight": results_attrs.get("ApproximateNumberOfMessagesNotVisible"),
            "visibilityTimeout": results_attrs.get("VisibilityTimeout"),
        },
    }


def poll_once(
    config: SqsPollerConfig,
    *,
    store: S3ArtifactStore,
    sqs_client: Any,
    grader: AIGrader,
) -> Optional[Dict[str, Any]]:
    """
    Nhận tối đa 1 message từ SQS (Jobs), xử lý trọn vẹn rồi mới xóa message.
    Trả về None nếu không có message; ném ngoại lệ (và KHÔNG xóa message) nếu lỗi.
    """
    resp = sqs_client.receive_message(
        QueueUrl=config.jobs_queue_url,
        MaxNumberOfMessages=1,
        WaitTimeSeconds=config.wait_seconds,
        VisibilityTimeout=config.visibility_timeout,
        MessageSystemAttributeNames=["ApproximateReceiveCount", "SentTimestamp"],
    )
    messages = resp.get("Messages") or []
    if not messages:
        return None

    msg = messages[0]
    sqs_msg_id = str(msg.get("MessageId", ""))
    receipt_handle = msg.get("ReceiptHandle")
    body = msg.get("Body", "")
    receive_count = (msg.get("Attributes") or {}).get("ApproximateReceiveCount", "?")

    logger.info(
        "Received SQS messageId=%s (receiveCount=%s)",
        sqs_msg_id,
        receive_count,
    )

    outcome = process_job_message(body, config.runtime, store, sqs_client, grader)

    if not receipt_handle:
        raise RuntimeError(f"Missing ReceiptHandle for SQS messageId={sqs_msg_id}")

    sqs_client.delete_message(
        QueueUrl=config.jobs_queue_url,
        ReceiptHandle=receipt_handle,
    )
    logger.info(
        "Completed & deleted SQS messageId=%s jobId=%s resultKey=%s completionMessageId=%s",
        sqs_msg_id,
        outcome["jobId"],
        outcome["resultKey"],
        outcome["messageId"],
    )
    return {
        "sqsMessageId": sqs_msg_id,
        **outcome,
    }


def poll_loop(
    config: SqsPollerConfig,
    *,
    store: S3ArtifactStore,
    sqs_client: Any,
    grader: AIGrader,
    max_jobs: Optional[int] = None,
    error_backoff_seconds: float = 3.0,
) -> int:
    """Chạy vòng lặp long-poll liên tục cho tới khi nhận tín hiệu dừng hoặc đủ `max_jobs`."""
    stop_requested = False

    def _handle_stop(signum, _frame):
        nonlocal stop_requested
        logger.info("Received signal %s; stopping after current poll...", signum)
        stop_requested = True

    prev_sigint = signal.getsignal(signal.SIGINT)
    prev_sigterm = signal.getsignal(signal.SIGTERM)
    signal.signal(signal.SIGINT, _handle_stop)
    if hasattr(signal, "SIGTERM"):
        signal.signal(signal.SIGTERM, _handle_stop)

    processed = 0
    try:
        while not stop_requested:
            try:
                outcome = poll_once(
                    config, store=store, sqs_client=sqs_client, grader=grader
                )
                if outcome is not None:
                    processed += 1
                    if max_jobs is not None and processed >= max_jobs:
                        logger.info("Reached max_jobs=%d; exiting cleanly.", max_jobs)
                        break
            except KeyboardInterrupt:
                break
            except Exception as exc:
                logger.exception(
                    "Job processing failed (message NOT deleted; will retry after visibility timeout): %s",
                    type(exc).__name__,
                )
                if not stop_requested and error_backoff_seconds > 0:
                    time.sleep(error_backoff_seconds)
    finally:
        signal.signal(signal.SIGINT, prev_sigint)
        if hasattr(signal, "SIGTERM"):
            signal.signal(signal.SIGTERM, prev_sigterm)
    return processed


def main(argv: Optional[list] = None) -> int:
    parser = argparse.ArgumentParser(
        description="FCAJ AI Worker — Standalone SQS Poller cho kiểm thử tích hợp AWS v3"
    )
    mode_group = parser.add_mutually_exclusive_group(required=True)
    mode_group.add_argument(
        "--preflight",
        action="store_true",
        help="Kiểm tra read-only IAM identity, SQS queues và cấu hình env (không nhận message)",
    )
    mode_group.add_argument(
        "--once",
        action="store_true",
        help="Long-poll tối đa 1 message từ SQS Jobs, xử lý và xóa message nếu thành công",
    )
    mode_group.add_argument(
        "--poll",
        action="store_true",
        help="Chạy vòng lặp long-poll liên tục cho tới khi nhấn Ctrl+C",
    )
    parser.add_argument(
        "--profile",
        default=None,
        help="AWS CLI profile (vd: fcaj-Thoth3010 từ `aws login --profile fcaj-Thoth3010`)",
    )
    parser.add_argument(
        "--env-file",
        default=os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".env")),
        help="Đường dẫn file .env cục bộ (mặc định: ai-worker/.env)",
    )
    parser.add_argument(
        "--wait-seconds",
        type=int,
        default=DEFAULT_WAIT_SECONDS,
        help="SQS long-poll WaitTimeSeconds (0-20, mặc định 20)",
    )
    parser.add_argument(
        "--visibility-timeout",
        type=int,
        default=DEFAULT_VISIBILITY_TIMEOUT,
        help="SQS VisibilityTimeout mỗi lần nhận (giây, mặc định 120)",
    )
    parser.add_argument(
        "--max-jobs",
        type=int,
        default=None,
        help="Số job tối đa xử lý trong chế độ --poll trước khi tự thoát",
    )
    args = parser.parse_args(argv)

    loaded_count = load_env_file(args.env_file)
    if loaded_count:
        logger.info("Loaded %d settings from %s", loaded_count, args.env_file)

    config = SqsPollerConfig.from_env(
        aws_profile=args.profile,
        wait_seconds=args.wait_seconds,
        visibility_timeout=args.visibility_timeout,
    )
    clients = create_aws_clients(config.runtime.aws_region, config.aws_profile)

    if args.preflight:
        report = run_preflight(
            config, sts_client=clients["sts"], sqs_client=clients["sqs"]
        )
        print(json.dumps(report, indent=2, ensure_ascii=False))
        return 0

    store = S3ArtifactStore(
        clients["s3"], config.runtime.storage_bucket, config.runtime.storage_prefix
    )
    grader = build_grader(config.runtime, ssm_client=clients["ssm"])

    if args.once:
        outcome = poll_once(
            config, store=store, sqs_client=clients["sqs"], grader=grader
        )
        if outcome is None:
            logger.info(
                "No AI job message received within %ds.", config.wait_seconds
            )
            return 0
        print(json.dumps(outcome, indent=2, ensure_ascii=False))
        return 0

    processed = poll_loop(
        config,
        store=store,
        sqs_client=clients["sqs"],
        grader=grader,
        max_jobs=args.max_jobs,
    )
    logger.info("Worker stopped cleanly. Processed jobs=%d", processed)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
