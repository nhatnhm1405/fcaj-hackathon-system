"""
AWS Lambda AI Worker handler cho kiến trúc SEAL Hackathon AWS v3.

Luồng xử lý theo sơ đồ AWS v3:
- ⑧ Trigger từ Amazon SQS (Jobs) chứa message `fcaj.ai.message.v1` (`AI_JOB_REQUESTED`)
- ⑯ Đọc `DEEPSEEK_API_KEY` từ AWS Systems Manager Parameter Store (cache warm start)
- ⑨ Đọc và kiểm tra checksum/size của `input-manifest.json`, `source.json`, `rubric.json` trên S3
- ⑩ Gọi DeepSeek API (qua `AIGrader`) để phân tích mã nguồn và hậu kiểm `safeText` / line range
- ⑨ Ghi kết quả `fcaj.ai.output.v1` lên S3 (`results/{jobId}/output.json`) với `If-None-Match: "*"`
- ⑪ Gửi message `AI_JOB_COMPLETED` (`fcaj.ai.message.v1`) vào Amazon SQS (Results)
"""

from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
import json
import logging
import os
import re
import sys
import time
from typing import Any, Dict, List, Optional, Tuple
import uuid

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from src.ai_grader import AIGrader, ModelBudgetExhaustedError, sanitize_safe_text

logger = logging.getLogger(__name__)
if not logger.handlers:
    logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))

# Giới hạn kích thước bytes chia sẻ với Backend (`JobLimits.java`)
MAX_MESSAGE_BYTES = 32_768
MAX_MANIFEST_BYTES = 262_144
MAX_RUBRIC_BYTES = 262_144
MAX_OUTPUT_BYTES = 262_144
MAX_SOURCE_BYTES = 8_000_000
DEFAULT_LAMBDA_MAX_OUTPUT_TOKENS = 4096
DEFAULT_LAMBDA_MAX_SOURCE_CHARS = 120_000

IDENTITY_FIELDS = (
    "jobId",
    "submissionId",
    "inputRevision",
    "commitSha",
    "processingVersion",
    "modelConfigVersion",
    "promptVersion",
    "rubricVersion",
)

_HEX40_RE = re.compile(r"^[a-f0-9]{40}$")
_HEX64_RE = re.compile(r"^[a-f0-9]{64}$")
_ERROR_CODE_RE = re.compile(r"^[A-Z0-9_]{1,60}$")
_OUTPUT_KEY_NAME = "output.json"

# Cache key ở mức module để tái sử dụng giữa các lần gọi warm của AWS Lambda
_CACHED_DEEPSEEK_API_KEY: Optional[str] = None
_CACHED_DEEPSEEK_PARAM_NAME: Optional[str] = None


class ContractValidationError(ValueError):
    """Lỗi vi phạm hợp đồng dữ liệu hoặc checksum đầu vào/đầu ra."""


@dataclass(frozen=True)
class LambdaRuntimeConfig:
    aws_region: str
    storage_bucket: str
    storage_prefix: str
    results_queue_url: str
    ai_provider: str
    deepseek_model: str
    deepseek_api_url: str
    deepseek_api_key_parameter: Optional[str]
    max_source_chars: int
    max_output_tokens: int = DEFAULT_LAMBDA_MAX_OUTPUT_TOKENS
    budget_exhausted_error_code: str = "MOCK_PROCESSING_FAILED"
    deepseek_thinking_mode: str = "disabled"
    deepseek_reasoning_effort: str = "none"
    http_timeout_seconds: int = 75

    @classmethod
    def from_env(cls) -> "LambdaRuntimeConfig":
        region = (os.getenv("AWS_REGION") or os.getenv("AWS_DEFAULT_REGION") or "ap-southeast-1").strip()
        bucket = (os.getenv("APP_STORAGE_BUCKET") or "").strip()
        prefix = (os.getenv("APP_STORAGE_PREFIX") or "").strip().strip("/")
        results_queue_url = (os.getenv("APP_AI_JOBS_RESULTS_QUEUE_URL") or "").strip()
        if not bucket:
            raise ValueError("Missing required runtime setting: APP_STORAGE_BUCKET")
        if not results_queue_url:
            raise ValueError("Missing required runtime setting: APP_AI_JOBS_RESULTS_QUEUE_URL")

        param_name = (
            os.getenv("DEEPSEEK_API_KEY_PARAMETER")
            or os.getenv("SSM_DEEPSEEK_API_KEY_PARAM")
            or ""
        ).strip() or None

        max_chars_raw = (
            os.getenv("LAMBDA_MAX_SOURCE_CHARS")
            or os.getenv("CODE_CLEANER_MAX_TOTAL_CHARS")
            or str(DEFAULT_LAMBDA_MAX_SOURCE_CHARS)
        )
        # Giới hạn <= 240,000 ký tự để nằm gọn dưới ngưỡng 60k input token của AI-02
        max_source_chars = max(1000, min(240_000, int(max_chars_raw)))

        max_out_raw = os.getenv("LAMBDA_MAX_OUTPUT_TOKENS") or str(DEFAULT_LAMBDA_MAX_OUTPUT_TOKENS)
        max_output_tokens = max(256, min(8192, int(max_out_raw)))

        timeout_raw = os.getenv("LAMBDA_HTTP_TIMEOUT_SECONDS") or "75"
        http_timeout_seconds = max(10, min(120, int(timeout_raw)))

        thinking_mode = (os.getenv("DEEPSEEK_THINKING_MODE") or "disabled").strip().lower()
        if thinking_mode not in {"disabled", "enabled"}:
            raise ValueError("Invalid DEEPSEEK_THINKING_MODE; expected 'disabled' or 'enabled'")

        reasoning_effort = (os.getenv("DEEPSEEK_REASONING_EFFORT") or "none").strip().lower()
        if reasoning_effort not in {"none", "low", "high", "max"}:
            raise ValueError(
                "Invalid DEEPSEEK_REASONING_EFFORT; expected 'none', 'low', 'high', or 'max'"
            )

        budget_code = (
            os.getenv("AI_BUDGET_EXHAUSTED_ERROR_CODE") or "MOCK_PROCESSING_FAILED"
        ).strip()
        if not _ERROR_CODE_RE.match(budget_code):
            raise ValueError(
                "Invalid AI_BUDGET_EXHAUSTED_ERROR_CODE; must match ^[A-Z0-9_]{1,60}$"
            )

        return cls(
            aws_region=region,
            storage_bucket=bucket,
            storage_prefix=prefix,
            results_queue_url=results_queue_url,
            ai_provider=(os.getenv("AI_PROVIDER") or "deepseek").strip().lower(),
            deepseek_model=(os.getenv("DEEPSEEK_MODEL") or "deepseek-flash").strip(),
            deepseek_api_url=(
                os.getenv("DEEPSEEK_API_URL") or "https://api.deepseek.com/chat/completions"
            ).strip(),
            deepseek_api_key_parameter=param_name,
            max_source_chars=max_source_chars,
            max_output_tokens=max_output_tokens,
            budget_exhausted_error_code=budget_code,
            deepseek_thinking_mode=thinking_mode,
            deepseek_reasoning_effort=reasoning_effort,
            http_timeout_seconds=http_timeout_seconds,
        )


def _no_duplicate_keys_hook(pairs: List[Tuple[str, Any]]) -> Dict[str, Any]:
    obj: Dict[str, Any] = {}
    for key, value in pairs:
        if key in obj:
            raise ContractValidationError(f"Duplicate JSON key: {key}")
        obj[key] = value
    return obj


def _strict_json_loads(raw_text: str) -> Any:
    if not isinstance(raw_text, str) or not raw_text.strip():
        raise ContractValidationError("Empty JSON payload")
    try:
        return json.loads(raw_text, object_pairs_hook=_no_duplicate_keys_hook)
    except ContractValidationError:
        raise
    except Exception as exc:
        raise ContractValidationError(f"Invalid JSON payload: {exc}") from exc


def _compact_json_bytes(payload: Dict[str, Any]) -> bytes:
    return json.dumps(payload, separators=(",", ":"), ensure_ascii=False).encode("utf-8")


def _sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _is_int(value: Any) -> bool:
    return isinstance(value, int) and not isinstance(value, bool)


def _assert_exact_fields(obj: Any, required: set, optional: Optional[set] = None) -> Dict[str, Any]:
    if not isinstance(obj, dict):
        raise ContractValidationError("Expected JSON object")
    opt = optional or set()
    keys = set(obj.keys())
    if not required.issubset(keys):
        missing = sorted(required - keys)
        raise ContractValidationError(f"Missing required fields: {missing}")
    extra = keys - required - opt
    if extra:
        raise ContractValidationError(f"Unexpected fields: {sorted(extra)}")
    return obj


def _require_text(obj: Dict[str, Any], name: str, max_len: int) -> str:
    val = obj.get(name)
    if not isinstance(val, str) or not val.strip() or len(val) > max_len:
        raise ContractValidationError(f"Invalid or blank field '{name}'")
    return val


def _require_uuid(obj: Dict[str, Any], name: str) -> str:
    val = _require_text(obj, name, 36)
    try:
        parsed = uuid.UUID(val)
    except ValueError as exc:
        raise ContractValidationError(f"Invalid UUID in '{name}'") from exc
    if str(parsed) != val:
        raise ContractValidationError(f"Non-canonical UUID in '{name}'")
    return val


def _require_iso_timestamp(obj: Dict[str, Any], name: str) -> str:
    val = _require_text(obj, name, 40)
    normalized = val[:-1] + "+00:00" if val.endswith("Z") else val
    try:
        datetime.fromisoformat(normalized)
    except ValueError as exc:
        raise ContractValidationError(f"Invalid timestamp in '{name}'") from exc
    return val


def _validate_logical_key(logical_key: str) -> str:
    if (
        not logical_key
        or logical_key.startswith("/")
        or "\\" in logical_key
        or ".." in logical_key
        or not re.match(r"^[a-zA-Z0-9_./-]+$", logical_key)
    ):
        raise ContractValidationError(f"Invalid logical key: {logical_key}")
    return logical_key


def _physical_s3_key(prefix: str, logical_key: str) -> str:
    clean_key = _validate_logical_key(logical_key)
    clean_prefix = prefix.strip("/")
    return f"{clean_prefix}/{clean_key}" if clean_prefix else clean_key


def clear_secret_cache() -> None:
    """Xóa cache secret (chủ yếu dùng trong unit tests)."""
    global _CACHED_DEEPSEEK_API_KEY, _CACHED_DEEPSEEK_PARAM_NAME
    _CACHED_DEEPSEEK_API_KEY = None
    _CACHED_DEEPSEEK_PARAM_NAME = None


def _create_aws_client(service_name: str, region: str):
    import boto3  # Lazy import so local unit tests without boto3 can mock clients cleanly

    return boto3.client(service_name, region_name=region)


def get_deepseek_api_key(config: LambdaRuntimeConfig, ssm_client: Optional[Any] = None) -> str:
    """
    Lấy DEEPSEEK_API_KEY từ biến môi trường hoặc AWS SSM Parameter Store (luồng ⑯).
    Cache lại theo tên parameter trong suốt vòng đời warm container của Lambda.
    """
    global _CACHED_DEEPSEEK_API_KEY, _CACHED_DEEPSEEK_PARAM_NAME

    env_key = (os.getenv("DEEPSEEK_API_KEY") or "").strip()
    if env_key:
        return env_key

    if not config.deepseek_api_key_parameter:
        raise RuntimeError(
            "Missing DEEPSEEK_API_KEY and DEEPSEEK_API_KEY_PARAMETER; cannot call DeepSeek API."
        )

    if (
        _CACHED_DEEPSEEK_API_KEY
        and _CACHED_DEEPSEEK_PARAM_NAME == config.deepseek_api_key_parameter
    ):
        return _CACHED_DEEPSEEK_API_KEY

    client = ssm_client or _create_aws_client("ssm", config.aws_region)
    resp = client.get_parameter(Name=config.deepseek_api_key_parameter, WithDecryption=True)
    value = str(resp.get("Parameter", {}).get("Value", "")).strip()
    if not value:
        raise RuntimeError(
            f"SSM Parameter '{config.deepseek_api_key_parameter}' returned an empty value."
        )
    _CACHED_DEEPSEEK_API_KEY = value
    _CACHED_DEEPSEEK_PARAM_NAME = config.deepseek_api_key_parameter
    return value


class S3ArtifactStore:
    """Adapter đọc/ghi bất biến JSON artifacts trên private S3 bucket."""

    def __init__(self, s3_client: Any, bucket: str, prefix: str):
        self.s3 = s3_client
        self.bucket = bucket
        self.prefix = prefix.strip("/")

    def read_bytes(
        self, logical_key: str, version_id: Optional[str], max_bytes: int
    ) -> bytes:
        full_key = _physical_s3_key(self.prefix, logical_key)
        params: Dict[str, Any] = {"Bucket": self.bucket, "Key": full_key}
        if version_id is not None:
            params["VersionId"] = version_id

        response = self.s3.get_object(**params)
        content_len = response.get("ContentLength")
        if content_len is not None and int(content_len) > max_bytes:
            raise ContractValidationError(
                f"Object '{logical_key}' exceeds max size limit ({content_len} > {max_bytes})"
            )

        body = response["Body"]
        try:
            data = body.read(max_bytes + 1)
        finally:
            if hasattr(body, "close"):
                body.close()

        if len(data) > max_bytes:
            raise ContractValidationError(
                f"Object '{logical_key}' exceeds max size limit ({len(data)} > {max_bytes})"
            )
        return data

    def put_immutable_json(
        self, logical_key: str, data: bytes, expected_identity: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        if len(data) < 1 or len(data) > MAX_OUTPUT_BYTES:
            raise ContractValidationError(
                f"Output size {len(data)} out of range [1, {MAX_OUTPUT_BYTES}]"
            )
        full_key = _physical_s3_key(self.prefix, logical_key)
        sha_hex = _sha256_hex(data)
        size_bytes = len(data)
        try:
            self.s3.put_object(
                Bucket=self.bucket,
                Key=full_key,
                Body=data,
                ContentType="application/json",
                IfNoneMatch="*",
                Metadata={"sha256": sha_hex},
            )
        except Exception as exc:
            if not _is_precondition_failed(exc):
                raise
            # Idempotent retry: dùng lại output.json đã ghi lên S3 trước đó nếu khớp identity của job
            existing = self.read_bytes(logical_key, None, MAX_OUTPUT_BYTES)
            if existing != data:
                if expected_identity is None:
                    raise ContractValidationError(
                        f"IMMUTABLE_OUTPUT_CONFLICT at '{logical_key}'"
                    ) from exc
                existing_doc = _strict_json_loads(existing.decode("utf-8"))
                if (
                    not isinstance(existing_doc, dict)
                    or existing_doc.get("schemaVersion") != "fcaj.ai.output.v1"
                    or any(existing_doc.get(f) != expected_identity.get(f) for f in IDENTITY_FIELDS)
                ):
                    raise ContractValidationError(
                        f"IMMUTABLE_OUTPUT_CONFLICT at '{logical_key}'"
                    ) from exc
            sha_hex = _sha256_hex(existing)
            size_bytes = len(existing)

        # Giữ versionId=None để completion message ổn định byte-for-byte khi retry
        return {
            "key": logical_key,
            "sha256": sha_hex,
            "sizeBytes": size_bytes,
            "contentType": "application/json",
            "versionId": None,
        }


def _is_precondition_failed(exc: Exception) -> bool:
    response = getattr(exc, "response", None)
    if isinstance(response, dict):
        code = str(response.get("Error", {}).get("Code", ""))
        status = response.get("ResponseMetadata", {}).get("HTTPStatusCode")
        if code in {"PreconditionFailed", "ConditionalRequestConflict"} or status == 412:
            return True
    return False


def _validate_and_read_ref(
    store: S3ArtifactStore,
    ref_obj: Any,
    expected_key: str,
    max_bytes: int,
) -> Dict[str, Any]:
    ref = _assert_exact_fields(
        ref_obj, {"key", "sha256", "sizeBytes", "contentType", "versionId"}
    )
    key = _require_text(ref, "key", 500)
    sha = _require_text(ref, "sha256", 64)
    size_bytes = ref.get("sizeBytes")
    content_type = ref.get("contentType")
    version_raw = ref.get("versionId")

    if (
        key != expected_key
        or not _HEX64_RE.match(sha)
        or content_type != "application/json"
        or not _is_int(size_bytes)
        or size_bytes < 1
        or size_bytes > max_bytes
    ):
        raise ContractValidationError(f"Invalid S3 Ref for '{expected_key}'")

    version_id: Optional[str] = None
    if version_raw is not None:
        version_id = _require_text(ref, "versionId", 1024)

    raw_bytes = store.read_bytes(expected_key, version_id, max_bytes)
    if len(raw_bytes) != size_bytes or _sha256_hex(raw_bytes) != sha:
        raise ContractValidationError(f"Checksum or size mismatch for '{expected_key}'")

    parsed = _strict_json_loads(raw_bytes.decode("utf-8"))
    if not isinstance(parsed, dict):
        raise ContractValidationError(f"Artifact '{expected_key}' must be a JSON object")
    return parsed


def validate_and_load_job_inputs(
    raw_message_body: str,
    store: S3ArtifactStore,
) -> Tuple[Dict[str, Any], Dict[str, Any], Dict[str, Any], Dict[str, Any]]:
    """
    Kiểm định envelope `AI_JOB_REQUESTED` và đọc/đối chiếu 3 artifacts trên S3:
    input-manifest.json, source.json, rubric.json.
    """
    if not isinstance(raw_message_body, str):
        raise ContractValidationError("SQS body must be a string")
    if len(raw_message_body.encode("utf-8")) > MAX_MESSAGE_BYTES:
        raise ContractValidationError("AI_JOB_REQUESTED message exceeds MAX_MESSAGE_BYTES")

    request = _assert_exact_fields(
        _strict_json_loads(raw_message_body),
        set(IDENTITY_FIELDS)
        | {"schemaVersion", "eventType", "messageId", "occurredAt", "mode", "deadlineAt", "input"},
    )
    if (
        request.get("schemaVersion") != "fcaj.ai.message.v1"
        or request.get("eventType") != "AI_JOB_REQUESTED"
        or request.get("mode") != "MOCK"
    ):
        raise ContractValidationError("Unsupported schemaVersion, eventType, or mode")

    _require_uuid(request, "messageId")
    _require_iso_timestamp(request, "occurredAt")
    job_id = _require_uuid(request, "jobId")
    if not _is_int(request.get("submissionId")) or request["submissionId"] < 1:
        raise ContractValidationError("Invalid submissionId")
    if not _HEX40_RE.match(_require_text(request, "commitSha", 40)):
        raise ContractValidationError("Invalid commitSha")
    if not _HEX64_RE.match(_require_text(request, "inputRevision", 64)):
        raise ContractValidationError("Invalid inputRevision")
    if request.get("deadlineAt") is not None:
        raise ContractValidationError("deadlineAt must be null in v1 contract")
    for field in ("processingVersion", "modelConfigVersion", "promptVersion", "rubricVersion"):
        _require_text(request, field, 100)

    input_refs = _assert_exact_fields(request.get("input"), {"manifest", "source", "rubric"})

    manifest = _validate_and_read_ref(
        store,
        input_refs["manifest"],
        f"inputs/{job_id}/input-manifest.json",
        MAX_MANIFEST_BYTES,
    )
    if manifest.get("schemaVersion") != "fcaj.ai.input-manifest.v1":
        raise ContractValidationError("Invalid manifest schemaVersion")
    if request["inputRevision"] != input_refs["manifest"]["sha256"]:
        raise ContractValidationError("inputRevision does not match manifest sha256")
    for field in (
        "submissionId",
        "commitSha",
        "processingVersion",
        "modelConfigVersion",
        "promptVersion",
        "rubricVersion",
    ):
        if manifest.get(field) != request.get(field):
            raise ContractValidationError(f"Manifest field mismatch: '{field}'")
    if manifest.get("source") != input_refs["source"] or manifest.get("rubric") != input_refs["rubric"]:
        raise ContractValidationError("Manifest source/rubric refs differ from request refs")

    source = _validate_and_read_ref(
        store,
        input_refs["source"],
        f"inputs/{job_id}/source.json",
        MAX_SOURCE_BYTES,
    )
    if source.get("schemaVersion") != "fcaj.ai.source.github.v1":
        raise ContractValidationError("Invalid source schemaVersion")
    if source.get("commitSha") != request["commitSha"]:
        raise ContractValidationError("Source commitSha mismatch")
    if not isinstance(source.get("files"), list) or not source["files"]:
        raise ContractValidationError("Source files must be a non-empty list")

    repo_tree = source.get("repoTree")
    if repo_tree is not None:
        if not isinstance(repo_tree, dict):
            raise ContractValidationError("Source repoTree must be a JSON object")
        entries = repo_tree.get("entries")
        if entries is not None:
            if not isinstance(entries, list) or len(entries) > 5000:
                raise ContractValidationError("Source repoTree.entries exceeds bounds")
            for entry in entries:
                if not isinstance(entry, dict):
                    raise ContractValidationError("Source repoTree entry must be an object")
                epath = entry.get("path")
                if (
                    not isinstance(epath, str)
                    or not epath.strip()
                    or any(ch in epath for ch in ("\x00", "\r", "\n"))
                    or epath.startswith(("/", "\\"))
                    or ".." in epath.replace("\\", "/").split("/")
                ):
                    raise ContractValidationError("Invalid path in source repoTree entry")

    rubric = _validate_and_read_ref(
        store,
        input_refs["rubric"],
        f"rubrics/{job_id}/rubric.json",
        MAX_RUBRIC_BYTES,
    )
    if rubric.get("schemaVersion") != "fcaj.ai.rubric.v1":
        raise ContractValidationError("Invalid rubric schemaVersion")
    if rubric.get("rubricVersion") != request["rubricVersion"]:
        raise ContractValidationError("Rubric version mismatch")
    if not isinstance(rubric.get("criteria"), list) or not rubric["criteria"]:
        raise ContractValidationError("Rubric criteria must be a non-empty list")

    return request, manifest, source, rubric


def build_v1_output_document(
    request: Dict[str, Any],
    evaluation: Dict[str, Any],
    generated_at: Optional[str] = None,
) -> Dict[str, Any]:
    """Đóng gói document `fcaj.ai.output.v1` khớp bộ validator ở Backend."""
    ts = generated_at or datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    output: Dict[str, Any] = {field: request[field] for field in IDENTITY_FIELDS}
    output["schemaVersion"] = "fcaj.ai.output.v1"
    output["generatedAt"] = ts
    output["provenance"] = {
        "mode": request["mode"],
        "modelId": evaluation["modelId"],
        "modelRevision": evaluation["modelRevision"],
        "adapterVersion": evaluation["adapterVersion"],
    }
    output["insight"] = evaluation["insight"]
    return output


def build_v1_completion_message(
    request: Dict[str, Any],
    result_ref: Dict[str, Any],
    occurred_at: Optional[str] = None,
) -> Dict[str, Any]:
    """Đóng gói completion message `AI_JOB_COMPLETED` (`fcaj.ai.message.v1`)."""
    ts = occurred_at or datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    message_id = str(
        uuid.uuid5(
            uuid.NAMESPACE_URL,
            f"fcaj-ai-completion:{request['jobId']}:{request['inputRevision']}:SUCCEEDED",
        )
    )
    msg: Dict[str, Any] = {field: request[field] for field in IDENTITY_FIELDS}
    msg["schemaVersion"] = "fcaj.ai.message.v1"
    msg["eventType"] = "AI_JOB_COMPLETED"
    msg["messageId"] = message_id
    msg["occurredAt"] = ts
    msg["status"] = "SUCCEEDED"
    msg["result"] = result_ref
    return msg


def build_v1_failure_message(
    request: Dict[str, Any],
    error_code: str,
    error_message: str,
    occurred_at: Optional[str] = None,
) -> Dict[str, Any]:
    """Đóng gói completion message `AI_JOB_COMPLETED` trạng thái `FAILED` (không đính kèm `result`)."""
    ts = occurred_at or datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    message_id = str(
        uuid.uuid5(
            uuid.NAMESPACE_URL,
            f"fcaj-ai-completion:{request['jobId']}:{request['inputRevision']}:FAILED",
        )
    )
    msg: Dict[str, Any] = {field: request[field] for field in IDENTITY_FIELDS}
    msg["schemaVersion"] = "fcaj.ai.message.v1"
    msg["eventType"] = "AI_JOB_COMPLETED"
    msg["messageId"] = message_id
    msg["occurredAt"] = ts
    msg["status"] = "FAILED"
    msg["error"] = {
        "code": error_code,
        "message": sanitize_safe_text(
            error_message,
            max_len=1000,
            fallback="AI provider budget exhausted.",
        ),
    }
    return msg


def process_job_message(
    raw_message_body: str,
    config: LambdaRuntimeConfig,
    store: S3ArtifactStore,
    sqs_client: Any,
    grader: AIGrader,
) -> Dict[str, Any]:
    """Xử lý trọn vẹn 1 job: đọc/verify S3 -> chấm AI -> ghi S3 bất biến -> gửi SQS Results."""
    started = time.monotonic()
    request, manifest, source, rubric = validate_and_load_job_inputs(raw_message_body, store)
    job_id = request["jobId"]

    snap = manifest.get("submissionSnapshot")
    description = snap.get("description") if isinstance(snap, dict) else None

    try:
        evaluation = grader.evaluate_v1_artifacts(
            submission_id=request["submissionId"],
            commit_sha=request["commitSha"],
            source_files=source["files"],
            rubric_criteria=rubric["criteria"],
            submission_description=description,
            max_total_chars=config.max_source_chars,
            repo_tree=source.get("repoTree"),
        )
    except ModelBudgetExhaustedError:
        failure_doc = build_v1_failure_message(
            request,
            config.budget_exhausted_error_code,
            "AI provider budget exhausted; stopped without infinite retry.",
        )
        failure_bytes = _compact_json_bytes(failure_doc)
        if len(failure_bytes) > MAX_MESSAGE_BYTES:
            raise ContractValidationError("AI_JOB_COMPLETED failure message exceeds MAX_MESSAGE_BYTES")
        sqs_client.send_message(
            QueueUrl=config.results_queue_url,
            MessageBody=failure_bytes.decode("utf-8"),
        )
        return {
            "jobId": job_id,
            "status": "FAILED",
            "errorCode": config.budget_exhausted_error_code,
            "messageId": failure_doc["messageId"],
            "durationMs": int((time.monotonic() - started) * 1000),
        }

    output_doc = build_v1_output_document(request, evaluation)
    output_bytes = _compact_json_bytes(output_doc)
    result_ref = store.put_immutable_json(
        f"results/{job_id}/{_OUTPUT_KEY_NAME}", output_bytes, expected_identity=request
    )

    completion_doc = build_v1_completion_message(request, result_ref)
    completion_bytes = _compact_json_bytes(completion_doc)
    if len(completion_bytes) > MAX_MESSAGE_BYTES:
        raise ContractValidationError("AI_JOB_COMPLETED message exceeds MAX_MESSAGE_BYTES")

    sqs_client.send_message(
        QueueUrl=config.results_queue_url,
        MessageBody=completion_bytes.decode("utf-8"),
    )
    return {
        "jobId": job_id,
        "status": "SUCCEEDED",
        "resultKey": result_ref["key"],
        "messageId": completion_doc["messageId"],
        "durationMs": int((time.monotonic() - started) * 1000),
        "usage": evaluation.get("usage", {}),
        "coverage": evaluation.get("coverage", {}),
    }


def _build_remaining_time_fn(context: Optional[Any]):
    if context is None or not hasattr(context, "get_remaining_time_in_millis"):
        return None

    def _remaining() -> Optional[float]:
        try:
            ms = context.get_remaining_time_in_millis()
            return float(ms) / 1000.0 if ms is not None else None
        except Exception:
            return None

    return _remaining


def lambda_handler(
    event: Dict[str, Any],
    context: Optional[Any] = None,
    *,
    s3_client: Optional[Any] = None,
    sqs_client: Optional[Any] = None,
    ssm_client: Optional[Any] = None,
    config: Optional[LambdaRuntimeConfig] = None,
) -> Dict[str, Any]:
    """
    AWS Lambda entrypoint cho SQS Event Source Mapping (hỗ trợ ReportBatchItemFailures).
    Nếu có record lỗi (S3/checksum/DeepSeek), giữ lại itemIdentifier trong batchItemFailures
    để SQS retry sau 120s hoặc chuyển sang Job DLQ khi vượt maxReceiveCount.
    """
    records = event.get("Records") if isinstance(event, dict) else None
    if not isinstance(records, list):
        raise ValueError("Invalid Lambda SQS event: missing 'Records' list")
    if not records:
        return {"batchItemFailures": []}

    runtime_cfg = config or LambdaRuntimeConfig.from_env()
    s3 = s3_client or _create_aws_client("s3", runtime_cfg.aws_region)
    sqs = sqs_client or _create_aws_client("sqs", runtime_cfg.aws_region)
    store = S3ArtifactStore(s3, runtime_cfg.storage_bucket, runtime_cfg.storage_prefix)

    api_key = None
    if runtime_cfg.ai_provider == "deepseek":
        api_key = get_deepseek_api_key(runtime_cfg, ssm_client=ssm_client)
    elif runtime_cfg.ai_provider == "gemini":
        api_key = (os.getenv("GEMINI_API_KEY") or "").strip() or None

    grader = AIGrader(
        api_key=api_key,
        model_name=runtime_cfg.deepseek_model if runtime_cfg.ai_provider == "deepseek" else None,
        provider=runtime_cfg.ai_provider,
        base_url=runtime_cfg.deepseek_api_url if runtime_cfg.ai_provider == "deepseek" else None,
        max_tokens=runtime_cfg.max_output_tokens,
        timeout=runtime_cfg.http_timeout_seconds if runtime_cfg.ai_provider == "deepseek" else None,
        deepseek_thinking=runtime_cfg.deepseek_thinking_mode,
        deepseek_reasoning_effort=runtime_cfg.deepseek_reasoning_effort,
        remaining_time_fn=_build_remaining_time_fn(context),
    )

    failures: List[Dict[str, str]] = []
    for record in records:
        sqs_msg_id = str(record.get("messageId", ""))
        body = record.get("body", "")
        try:
            outcome = process_job_message(body, runtime_cfg, store, sqs, grader)
            logger.info(
                "AI_JOB_METRIC %s",
                json.dumps(
                    {
                        "jobId": outcome["jobId"],
                        "status": outcome["status"],
                        "durationMs": outcome.get("durationMs"),
                        "usage": outcome.get("usage"),
                        "coverage": outcome.get("coverage"),
                    },
                    separators=(",", ":"),
                    ensure_ascii=False,
                ),
            )
        except Exception as exc:
            logger.exception("Failed SQS messageId=%s: %s", sqs_msg_id, type(exc).__name__)
            if sqs_msg_id:
                failures.append({"itemIdentifier": sqs_msg_id})
            else:
                raise

    return {"batchItemFailures": failures}
