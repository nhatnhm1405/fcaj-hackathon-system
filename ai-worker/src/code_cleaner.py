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
    Sắp xếp độ ưu tiên của file để nếu chạm ngưỡng max_total_files thì vẫn giữ được
    code nghiệp vụ chính (src/, app/, back-end/, front-end/) trước các file tài liệu phụ.
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

    preferred_exts: Set[str] = set()
    if target_languages:
        for lang in target_languages:
            preferred_exts.update(LANGUAGE_EXTENSIONS.get(lang.lower().strip(), set()))

    candidate_paths: List[Tuple[str, str]] = []
    skipped_files_count = 0

    for dirpath, dirnames, filenames in os.walk(root_dir):
        rel_dir = os.path.relpath(dirpath, root_dir).replace("\\", "/")
        if rel_dir != "." and _should_ignore_rel_path(rel_dir, ignore_dirs):
            dirnames[:] = []
            continue

        # Lọc bỏ thư mục nằm trong danh sách ignore ngay tại walk và sắp xếp cố định
        dirnames[:] = sorted(
            d
            for d in dirnames
            if d not in ignore_dirs
            and not d.startswith(".")
            and not _should_ignore_rel_path(
                f"{rel_dir}/{d}" if rel_dir != "." else d, ignore_dirs
            )
        )

        for filename in sorted(filenames):
            if filename in DEFAULT_IGNORE_FILES:
                skipped_files_count += 1
                continue

            _, ext = os.path.splitext(filename)
            if ext.lower() in DEFAULT_IGNORE_EXTENSIONS:
                skipped_files_count += 1
                continue

            full_path = os.path.join(dirpath, filename)
            rel_path = os.path.relpath(full_path, root_dir).replace("\\", "/")

            if _should_ignore_rel_path(rel_path, ignore_dirs):
                skipped_files_count += 1
                continue

            candidate_paths.append((rel_path, full_path))

    # Sắp xếp ưu tiên file code chính trước khi đọc và giới hạn max_total_files
    candidate_paths.sort(key=lambda item: _file_priority_key(item[0], preferred_exts))

    collected_files: Dict[str, str] = {}
    total_lines = 0
    total_bytes = 0

    for rel_path, full_path in candidate_paths:
        if len(collected_files) >= max_total_files:
            skipped_files_count += 1
            continue

        try:
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
            line_count = len(text_content.splitlines())

            collected_files[rel_path] = text_content
            total_lines += line_count
            total_bytes += file_size
        except Exception:
            skipped_files_count += 1
            continue

    stats = {
        "total_files": len(collected_files),
        "total_lines": total_lines,
        "total_bytes": total_bytes,
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
