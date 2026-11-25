"""
Module GitHub Fetcher - Tải và giải nén source code tại Commit SHA từ GitHub API.
Sử dụng trong môi trường Production khi nhận job từ SQS/Backend.
"""

import io
import os
import re
import stat
import sys
import tempfile
import urllib.request
import urllib.error
import zipfile
from typing import Dict, Tuple, Any, Optional

# Import code_cleaner
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from src.code_cleaner import scan_and_collect_sources, format_code_with_line_numbers
from src.schema import RepositoryInfo, GradingOptions

MAX_ZIP_DOWNLOAD_BYTES = 50 * 1024 * 1024   # 50 MiB max downloaded zipball
MAX_ZIP_EXTRACT_BYTES = 100 * 1024 * 1024   # 100 MiB max total decompressed bytes (anti Zip Bomb)
MAX_ZIP_ENTRIES = 5000
_EXTRACT_CHUNK_BYTES = 64 * 1024


def parse_github_owner_repo(repo_url: str) -> Tuple[str, str]:
    """Trích xuất owner và repo name từ GitHub URL."""
    match = re.search(r"github\.com[/:]([\w.-]+)/([\w.-]+?)(?:\.git)?/?$", repo_url)
    if not match:
        raise ValueError(f"Đường dẫn GitHub không hợp lệ: {repo_url}")
    owner, repo_name = match.group(1), match.group(2)
    if owner in {".", ".."} or repo_name in {".", ".."} or ".." in owner or ".." in repo_name:
        raise ValueError(f"Đường dẫn GitHub không an toàn: {repo_url}")
    return owner, repo_name


def _safe_extract_zip(
    z: zipfile.ZipFile,
    dest_dir: str,
    *,
    max_total_bytes: int = MAX_ZIP_EXTRACT_BYTES,
    max_entries: int = MAX_ZIP_ENTRIES,
) -> None:
    """
    Giải nén an toàn chống Zip Slip (path traversal), symlink escape và Zip Bomb.
    """
    infos = z.infolist()
    if len(infos) > max_entries:
        raise ValueError(f"Zip archive contains too many entries ({len(infos)} > {max_entries})")

    real_dest = os.path.realpath(dest_dir)
    total_extracted = 0

    for info in infos:
        # Bỏ qua mọi entry là symlink (Unix external_attr)
        if stat.S_ISLNK(info.external_attr >> 16):
            continue

        raw_name = (info.filename or "").replace("\\", "/")
        if not raw_name or raw_name.startswith("/"):
            raise ValueError(f"Unsafe zip entry path detected (Zip Slip): {info.filename}")
        parts = [p for p in raw_name.split("/") if p]
        if any(p == ".." for p in parts) or (len(parts) > 0 and ":" in parts[0]):
            raise ValueError(f"Unsafe zip entry path detected (Zip Slip): {info.filename}")

        target_path = os.path.realpath(os.path.join(real_dest, *parts))
        try:
            in_dest = os.path.commonpath([real_dest, target_path]) == real_dest
        except ValueError:
            in_dest = False
        if not in_dest or target_path == real_dest:
            if not info.is_dir():
                raise ValueError(f"Unsafe zip entry path detected (Zip Slip): {info.filename}")
            continue

        if info.is_dir():
            os.makedirs(target_path, exist_ok=True)
            continue

        os.makedirs(os.path.dirname(target_path), exist_ok=True)
        with z.open(info, "r") as src, open(target_path, "wb") as dst:
            while True:
                chunk = src.read(_EXTRACT_CHUNK_BYTES)
                if not chunk:
                    break
                total_extracted += len(chunk)
                if total_extracted > max_total_bytes:
                    raise ValueError(
                        f"Decompressed archive exceeds maximum allowed size ({max_total_bytes} bytes)"
                    )
                dst.write(chunk)


def fetch_repository_at_commit(
    repo_info: RepositoryInfo,
    options: Optional[GradingOptions] = None,
    max_total_files: int = 100,
) -> Tuple[Dict[str, str], Dict[str, Any], str]:
    """
    Tải zipball của repository tại đúng commit SHA qua GitHub REST API,
    sau đó lọc sạch (áp dụng options.ignore_paths và options.target_languages)
    và đánh số dòng.

    Trả về:
      - files_dict: { rel_path: file_content }
      - stats: Thống kê số lượng file, số dòng, tokens
      - code_context: Chuỗi Markdown chứa toàn bộ mã nguồn đã đánh số dòng
    """
    repo_info.validate()
    owner, repo_name = parse_github_owner_repo(repo_info.url)

    # API endpoint tải zipball tại commit SHA cố định
    zipball_url = f"https://api.github.com/repos/{owner}/{repo_name}/zipball/{repo_info.commit_sha}"

    headers = {
        "User-Agent": "FCAJ-AI-Evaluator",
        "Accept": "application/vnd.github+json",
    }
    if repo_info.access_token:
        headers["Authorization"] = f"Bearer {repo_info.access_token}"

    req = urllib.request.Request(zipball_url, headers=headers)

    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            zip_bytes = resp.read(MAX_ZIP_DOWNLOAD_BYTES + 1)
    except urllib.error.HTTPError as e:
        err_msg = e.read(1024).decode("utf-8", errors="replace")[:500]
        raise RuntimeError(f"Không thể tải code từ GitHub (HTTP {e.code}): {err_msg}")

    if len(zip_bytes) > MAX_ZIP_DOWNLOAD_BYTES:
        raise ValueError(
            f"GitHub zipball exceeds maximum allowed download size ({MAX_ZIP_DOWNLOAD_BYTES} bytes)"
        )

    # Giải nén ra thư mục tạm thời một cách an toàn
    with tempfile.TemporaryDirectory() as temp_dir:
        with zipfile.ZipFile(io.BytesIO(zip_bytes)) as z:
            _safe_extract_zip(z, temp_dir)

        # GitHub zipball thường bọc bên trong 1 thư mục gốc dạng: owner-repo-sha/
        subdirs = [
            os.path.join(temp_dir, d)
            for d in os.listdir(temp_dir)
            if os.path.isdir(os.path.join(temp_dir, d))
        ]
        extract_root = subdirs[0] if subdirs else temp_dir

        custom_ignore = options.ignore_paths if options else None
        target_langs = options.target_languages if options else None

        # Quét và lọc file
        files_dict, stats = scan_and_collect_sources(
            extract_root,
            custom_ignore_dirs=custom_ignore,
            target_languages=target_langs,
            max_total_files=max_total_files,
        )
        code_context = format_code_with_line_numbers(files_dict)

    return files_dict, stats, code_context
