"""
Module quét, lọc và đóng gói mã nguồn (Code Ingestion & Sanitation).
Dùng chung cho cả luồng Production (tải từ GitHub commit SHA) 
và luồng Benchmark kiểm thử cục bộ (bài thi của Lead).
"""

import os
from typing import Dict, List, Tuple, Set, Any

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

# Giới hạn kích thước tối đa cho 1 file mã nguồn (150 KB)
MAX_FILE_SIZE_BYTES = 150 * 1024


def is_binary_string(bytes_content: bytes) -> bool:
    """Kiểm tra nhanh xem file có phải dạng nhị phân không (chứa null byte)."""
    return b"\x00" in bytes_content[:1024]


def scan_and_collect_sources(
    root_dir: str,
    custom_ignore_dirs: List[str] = None,
    max_total_files: int = 100
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

    collected_files: Dict[str, str] = {}
    total_lines = 0
    total_bytes = 0
    skipped_files_count = 0

    for dirpath, dirnames, filenames in os.walk(root_dir):
        # Lọc bỏ thư mục nằm trong danh sách ignore ngay tại walk
        dirnames[:] = [d for d in dirnames if d not in ignore_dirs and not d.startswith(".")]

        for filename in filenames:
            # Kiểm tra tên file và extension
            if filename in DEFAULT_IGNORE_FILES:
                skipped_files_count += 1
                continue

            _, ext = os.path.splitext(filename)
            if ext.lower() in DEFAULT_IGNORE_EXTENSIONS:
                skipped_files_count += 1
                continue

            full_path = os.path.join(dirpath, filename)
            rel_path = os.path.relpath(full_path, root_dir).replace("\\", "/")

            try:
                # Kiểm tra dung lượng
                file_size = os.path.getsize(full_path)
                if file_size > MAX_FILE_SIZE_BYTES or file_size == 0:
                    skipped_files_count += 1
                    continue

                # Đọc byte để check binary
                with open(full_path, "rb") as bf:
                    raw_bytes = bf.read()
                    if is_binary_string(raw_bytes):
                        skipped_files_count += 1
                        continue

                # Đọc text theo utf-8
                text_content = raw_bytes.decode("utf-8", errors="replace")
                line_count = len(text_content.splitlines())

                collected_files[rel_path] = text_content
                total_lines += line_count
                total_bytes += file_size

                if len(collected_files) >= max_total_files:
                    break
            except Exception:
                skipped_files_count += 1
                continue

        if len(collected_files) >= max_total_files:
            break

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
    === File: src/Main.java ===
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
