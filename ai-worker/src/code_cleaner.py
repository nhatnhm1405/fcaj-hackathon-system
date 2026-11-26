"""
Module quét, lọc và đóng gói mã nguồn (Code Ingestion & Sanitation).
Dùng chung cho cả luồng Production (tải từ GitHub commit SHA)
và luồng Benchmark kiểm thử cục bộ (bài thi của Lead).
"""

import os
from typing import Dict, List, Tuple, Set, Any, Optional

# Danh sách thư mục mặc định cần bỏ qua
DEFAULT_IGNORE_DIRS: Set[str] = {
    ".git",
    ".idea",
    ".vscode",
    "node_modules",
    "target",
    "build",
    "dist",
    "bin",
    "out",
    "__pycache__",
    ".pytest_cache",
    ".gradle",
    ".mvn",
    "vendor",
    "coverage",
}

# Danh sách đuôi file bỏ qua (binary, media, build artifacts, locks)
DEFAULT_IGNORE_EXTENSIONS: Set[str] = {
    # Binaries & archives
    ".class", ".jar", ".war", ".ear", ".exe", ".dll", ".so", ".dylib", ".o", ".a",
    ".zip", ".tar", ".gz", ".7z", ".rar",
    # Media & fonts
    ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg", ".webp", ".mp4", ".mp3",
    ".woff", ".woff2", ".ttf", ".eot",
    # Documents
    ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".drawio",
    # Lockfiles & huge generated files
    ".lock", ".log", ".map",
}

# Tên file chính xác cần bỏ qua
DEFAULT_IGNORE_FILES: Set[str] = {
    "package-lock.json",
    "yarn.lock",
    "pnpm-lock.yaml",
    "composer.lock",
    "Cargo.lock",
    "poetry.lock",
}

# Ánh xạ ngôn ngữ sang đuôi file để ưu tiên thu thập khi repo lớn
LANGUAGE_EXTENSIONS: Dict[str, Set[str]] = {
    "java": {".java", ".xml", ".properties", ".gradle", ".yml", ".yaml", ".sql"},
    "python": {".py", ".toml", ".txt", ".yml", ".yaml", ".ini", ".sql"},
    "typescript": {".ts", ".tsx", ".js", ".jsx", ".json", ".css", ".html"},
    "javascript": {".js", ".jsx", ".ts", ".tsx", ".json", ".css", ".html"},
    "go": {".go", ".mod"},
    "csharp": {".cs", ".csproj", ".sln"},
    "rust": {".rs", ".toml"},
}

# Giới hạn kích thước tối đa cho 1 file mã nguồn (150 KB)
MAX_FILE_SIZE_BYTES = 150 * 1024

# Giới hạn tổng số ký tự mã nguồn để bảo vệ ngữ cảnh (KV cache) cho Local AI trên EC2
DEFAULT_MAX_TOTAL_CHARS = int(os.getenv("CODE_CLEANER_MAX_TOTAL_CHARS", "35000"))

RANKING_ALGORITHM_VERSION = "rank-v1"

_RUBRIC_KEYWORD_BUCKETS: Dict[str, Tuple[str, ...]] = {
    "security": ("security", "auth", "jwt", "oauth", "permission", "bảo mật", "phân quyền", "validation", "xss", "sql"),
    "testing": ("test", "unit", "integration", "coverage", "e2e", "kiểm thử", "ci/cd", "cicd", "workflow", "quality"),
    "frontend": ("ui", "ux", "frontend", "react", "component", "page", "giao diện", "accessibility", "responsive"),
    "architecture": ("architecture", "technical", "design", "database", "schema", "api", "backend", "kiến trúc", "kỹ thuật", "triển khai"),
}

_PRIMARY_CODE_EXTS: Set[str] = {
    ".java", ".kt", ".py", ".ts", ".tsx", ".js", ".jsx", ".go", ".cs", ".rs", ".sql",
}
_ROOT_HIGH_SIGNAL_NAMES: Set[str] = {
    "readme", "readme.md", "readme.txt", "pom.xml", "build.gradle", "build.gradle.kts",
    "package.json", "pyproject.toml", "requirements.txt", "go.mod", "cargo.toml",
    "dockerfile", "docker-compose.yml", "docker-compose.yaml", "compose.yml", "compose.yaml",
    ".env.example",
}
_CORE_PATH_TOKENS: Tuple[str, ...] = (
    "/controller", "/controllers", "/service", "/services", "/security", "/auth",
    "/domain", "/entity", "/entities", "/model", "/models", "/repository", "/repositories",
    "/api/", "/routes", "/router", "/aijob/", "/lambda_handler", "/sqs_worker",
    "/db/migration", "controller.", "service.", "securityconfig",
)
_ENTRY_FILENAMES: Set[str] = {
    "main.py", "app.py", "server.py", "lambda_handler.py", "main.java", "application.java",
    "index.ts", "index.tsx", "app.ts", "app.tsx", "main.ts", "main.tsx", "server.ts", "server.js",
}
_UI_STYLE_EXTS: Set[str] = {".css", ".scss", ".sass", ".less", ".svg"}
_DATA_EXTS: Set[str] = {".json", ".csv", ".xml", ".txt"}


def extract_rubric_signal_buckets(
    rubric_criteria: Optional[List[Dict[str, Any]]],
) -> Set[str]:
    """Rút trích tập nhóm tín hiệu tất định từ tên & mô tả các tiêu chí Rubric."""
    if not rubric_criteria:
        return set()
    parts: List[str] = []
    for item in rubric_criteria:
        if not isinstance(item, dict):
            continue
        name = str(item.get("name") or "")
        desc = str(item.get("description") or "")
        parts.append(f"{name} {desc}".lower())
    haystack = " ".join(parts)
    matched: Set[str] = set()
    for bucket, keywords in _RUBRIC_KEYWORD_BUCKETS.items():
        if any(kw in haystack for kw in keywords):
            matched.add(bucket)
    return matched


def compute_file_rank_score(
    rel_path: str,
    content_len: int = 0,
    rubric_buckets: Optional[Set[str]] = None,
) -> Tuple[int, str]:
    """
    Tính khóa sắp xếp tất định (-score, rel_path) để chọn các file giàu tín hiệu nhất
    trong giới hạn token budget. Cùng đầu vào luôn cho cùng một thứ tự 100%.
    """
    norm = rel_path.replace("\\", "/").strip("/").lower()
    base = norm.rsplit("/", 1)[-1]
    _, ext = os.path.splitext(base)
    buckets = rubric_buckets or set()
    score = 0

    is_root = "/" not in norm
    if base in _ROOT_HIGH_SIGNAL_NAMES or norm.startswith(".github/workflows/"):
        score += 1000 if (is_root or base.startswith("readme") or norm.startswith(".github/workflows/")) else 850
    elif base.startswith("readme"):
        score += 900

    padded_norm = f"/{norm}"
    is_test_file = (
        "/test/" in padded_norm
        or "/tests/" in padded_norm
        or "/__tests__/" in padded_norm
        or base.endswith((".test.ts", ".test.tsx", ".test.js", ".test.jsx", ".spec.ts", ".spec.tsx"))
        or base.startswith("test_")
        or base.endswith(("test.java", "tests.java", "_test.py", "_test.go"))
    )
    is_core_domain = (
        not is_test_file
        and (
            any(tok in padded_norm for tok in _CORE_PATH_TOKENS)
            or ext == ".sql"
            or base in _ENTRY_FILENAMES
        )
    )

    if is_core_domain:
        score += 700
    elif is_test_file:
        score += 420

    is_core_dir = any(
        tok in norm for tok in ("src/", "app/", "back-end/", "front-end/", "backend/", "frontend/", "ai-worker/")
    )
    if is_core_dir and ext in _PRIMARY_CODE_EXTS:
        score += 500
    elif ext in _PRIMARY_CODE_EXTS:
        score += 300
    elif is_core_dir:
        score += 150

    # Cộng điểm tất định theo nhóm tiêu chí xuất hiện trong Rubric (tối đa +240)
    rubric_bonus = 0
    if "security" in buckets and any(k in padded_norm for k in ("/security", "/auth", "jwt", "oauth", "validator", "guard")):
        rubric_bonus += 80
    if "testing" in buckets and (is_test_file or norm.startswith(".github/workflows/")):
        rubric_bonus += 80
    if "frontend" in buckets and any(k in norm for k in ("front-end/", "frontend/", "/pages/", "/features/", "/components/")) and ext in {".tsx", ".jsx", ".ts", ".js"}:
        rubric_bonus += 80
    if "architecture" in buckets and (is_core_domain or base in _ROOT_HIGH_SIGNAL_NAMES):
        rubric_bonus += 80
    score += min(240, rubric_bonus)

    # Phạt nhẹ boilerplate UI / dữ liệu tĩnh / file quá dài để nhường token cho logic lõi
    if "/components/ui/" in padded_norm or ext in _UI_STYLE_EXTS:
        score -= 150
    if (ext in _DATA_EXTS or any(k in padded_norm for k in ("/seed", "/fixtures", "/mock", "/mocks", "/sample"))) and base not in _ROOT_HIGH_SIGNAL_NAMES and base != "tsconfig.json":
        score -= 200
    if content_len > 40_000:
        score -= 50

    return (-score, rel_path)


def rank_source_entries(
    file_entries: List[Tuple[str, str]],
    rubric_criteria: Optional[List[Dict[str, Any]]] = None,
) -> List[Tuple[str, str]]:
    """Sắp xếp danh sách `(path, content)` theo Smart Ranking tất định (tie-break theo path)."""
    buckets = extract_rubric_signal_buckets(rubric_criteria)
    return sorted(
        file_entries,
        key=lambda item: compute_file_rank_score(item[0], len(item[1]), rubric_buckets=buckets),
    )


def is_binary_string(bytes_content: bytes) -> bool:
    """Kiểm tra nhanh xem file có phải dạng nhị phân không (chứa null byte)."""
    return b"\x00" in bytes_content[:1024]


def _should_ignore_rel_path(rel_path: str, ignore_paths: Set[str]) -> bool:
    """Kiểm tra xem đường dẫn tương đối có khớp với bất kỳ pattern ignore nào không."""
    norm_rel = rel_path.strip("/").replace("\\", "/")
    parts = norm_rel.split("/")
    for ig in ignore_paths:
        norm_ig = ig.strip("/").replace("\\", "/")
        if not norm_ig:
            continue
        if norm_ig in parts:
            return True
        if norm_rel == norm_ig or norm_rel.startswith(norm_ig + "/"):
            return True
    return False


def _file_priority_key(rel_path: str, preferred_exts: Set[str]) -> Tuple[int, str]:
    """
    Sắp xếp độ ưu tiên của file để nếu chạm ngưỡng max_total_files hoặc max_total_chars
    thì vẫn giữ được code nghiệp vụ chính (src/, app/, back-end/, front-end/) trước các file tài liệu phụ.
    """
    _, ext = os.path.splitext(rel_path)
    ext_lower = ext.lower()
    norm = rel_path.lower()

    # Ưu tiên 0: File mã nguồn đúng ngôn ngữ mục tiêu trong thư mục code chính
    is_core_dir = any(
        token in norm for token in ("src/", "app/", "back-end/", "front-end/", "backend/", "frontend/")
    )
    is_preferred_ext = bool(preferred_exts and ext_lower in preferred_exts)

    if is_core_dir and is_preferred_ext:
        priority = 0
    elif is_preferred_ext:
        priority = 1
    elif is_core_dir:
        priority = 2
    else:
        priority = 3

    return (priority, rel_path)


def scan_and_collect_sources(
    root_dir: str,
    custom_ignore_dirs: Optional[List[str]] = None,
    target_languages: Optional[List[str]] = None,
    max_total_files: int = 100,
    max_total_chars: Optional[int] = None,
) -> Tuple[Dict[str, str], Dict[str, Any]]:
    """
    Quét thư mục gốc và thu thập toàn bộ file source code hợp lệ.
    Trả về:
      - files_dict: { relative_path: file_content_str }
      - stats: Thống kê số lượng file, số dòng code, dung lượng
    """
    ignore_dirs = set(DEFAULT_IGNORE_DIRS)
    if custom_ignore_dirs:
        ignore_dirs.update(custom_ignore_dirs)

    real_root = os.path.realpath(root_dir)
    preferred_exts: Set[str] = set()
    if target_languages:
        for lang in target_languages:
            preferred_exts.update(LANGUAGE_EXTENSIONS.get(lang.lower().strip(), set()))

    candidate_paths: List[Tuple[str, str]] = []
    skipped_files_count = 0

    for dirpath, dirnames, filenames in os.walk(root_dir, followlinks=False):
        rel_dir = os.path.relpath(dirpath, root_dir).replace("\\", "/")
        if rel_dir != "." and _should_ignore_rel_path(rel_dir, ignore_dirs):
            dirnames[:] = []
            continue

        # Lọc bỏ thư mục nằm trong danh sách ignore và mọi thư mục symlink ngay tại walk
        dirnames[:] = sorted(
            d
            for d in dirnames
            if d not in ignore_dirs
            and not d.startswith(".")
            and not os.path.islink(os.path.join(dirpath, d))
            and not _should_ignore_rel_path(
                f"{rel_dir}/{d}" if rel_dir != "." else d, ignore_dirs
            )
        )

        for filename in sorted(filenames):
            full_path = os.path.join(dirpath, filename)
            if os.path.islink(full_path):
                skipped_files_count += 1
                continue

            try:
                real_file = os.path.realpath(full_path)
                if os.path.commonpath([real_root, real_file]) != real_root:
                    skipped_files_count += 1
                    continue
            except ValueError:
                skipped_files_count += 1
                continue

            if filename in DEFAULT_IGNORE_FILES:
                skipped_files_count += 1
                continue

            _, ext = os.path.splitext(filename)
            if ext.lower() in DEFAULT_IGNORE_EXTENSIONS:
                skipped_files_count += 1
                continue

            rel_path = os.path.relpath(full_path, root_dir).replace("\\", "/")

            if _should_ignore_rel_path(rel_path, ignore_dirs):
                skipped_files_count += 1
                continue

            candidate_paths.append((rel_path, full_path))

    # Sắp xếp ưu tiên file code chính trước khi đọc và giới hạn max_total_files / max_total_chars
    candidate_paths.sort(key=lambda item: _file_priority_key(item[0], preferred_exts))

    collected_files: Dict[str, str] = {}
    total_lines = 0
    total_bytes = 0
    total_chars = 0

    for rel_path, full_path in candidate_paths:
        if len(collected_files) >= max_total_files:
            skipped_files_count += 1
            continue

        try:
            if os.path.islink(full_path):
                skipped_files_count += 1
                continue
            real_file = os.path.realpath(full_path)
            if os.path.commonpath([real_root, real_file]) != real_root:
                skipped_files_count += 1
                continue

            file_size = os.path.getsize(full_path)
            if file_size > MAX_FILE_SIZE_BYTES or file_size == 0:
                skipped_files_count += 1
                continue

            with open(full_path, "rb") as bf:
                raw_bytes = bf.read()
                if is_binary_string(raw_bytes):
                    skipped_files_count += 1
                    continue

            text_content = raw_bytes.decode("utf-8", errors="replace")
            if max_total_chars is not None and max_total_chars > 0:
                if total_chars >= max_total_chars:
                    skipped_files_count += 1
                    continue
                remaining_chars = max_total_chars - total_chars
                if len(text_content) > remaining_chars:
                    # Chỉ giữ các dòng nguyên vẹn nằm trong giới hạn remaining_chars
                    full_lines = text_content.splitlines()
                    kept_lines: List[str] = []
                    used_chars = 0
                    for ln in full_lines:
                        add_len = len(ln) + (1 if kept_lines else 0)
                        if used_chars + add_len > remaining_chars:
                            break
                        kept_lines.append(ln)
                        used_chars += add_len
                    if not kept_lines:
                        skipped_files_count += 1
                        continue
                    text_content = "\n".join(kept_lines)

            line_count = len(text_content.splitlines())

            collected_files[rel_path] = text_content
            total_lines += line_count
            total_bytes += len(text_content.encode("utf-8"))
            total_chars += len(text_content)
        except Exception:
            skipped_files_count += 1
            continue

    stats = {
        "total_files": len(collected_files),
        "total_lines": total_lines,
        "total_bytes": total_bytes,
        "total_chars": total_chars,
        "skipped_files": skipped_files_count,
        "approx_tokens": int(total_bytes / 3.5),
    }

    return collected_files, stats


def format_code_with_line_numbers(files_dict: Dict[str, str]) -> str:
    """
    Đóng gói mã nguồn thành Markdown kèm số thứ tự dòng cho từng file.
    Ví dụ:
    ==================== FILE: src/Main.java ====================
       1: public class Main {
       2:     public static void main(String[] args) {
    ...
    """
    output_blocks = []

    for rel_path, content in sorted(files_dict.items()):
        lines = content.splitlines()
        numbered_lines = [f"{idx + 1:4d}: {line}" for idx, line in enumerate(lines)]
        code_block = (
            f"\n\n{'=' * 20} FILE: {rel_path} {'=' * 20}\n"
            + "\n".join(numbered_lines)
            + f"\n{'=' * 50}\n"
        )
        output_blocks.append(code_block)

    return "".join(output_blocks)
