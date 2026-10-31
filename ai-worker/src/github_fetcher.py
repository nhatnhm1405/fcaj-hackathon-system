"""
Module GitHub Fetcher - Tải và giải nén source code tại Commit SHA từ GitHub API.
Sử dụng trong môi trường Production khi nhận job từ SQS/Backend.
"""

import io
import os
import re
import sys
import tempfile
import urllib.request
import urllib.error
import zipfile
from typing import Dict, Tuple, Any, Optional

# Import code_cleaner
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from src.code_cleaner import scan_and_collect_sources, format_code_with_line_numbers
from src.schema import RepositoryInfo


def parse_github_owner_repo(repo_url: str) -> Tuple[str, str]:
    """Trích xuất owner và repo name từ GitHub URL."""
    match = re.search(r"github\.com[/:]([\w.-]+)/([\w.-]+?)(?:\.git)?/?$", repo_url)
    if not match:
        raise ValueError(f"Đường dẫn GitHub không hợp lệ: {repo_url}")
    return match.group(1), match.group(2)


def fetch_repository_at_commit(
    repo_info: RepositoryInfo,
    max_total_files: int = 100
) -> Tuple[Dict[str, str], Dict[str, Any], str]:
    """
    Tải zipball của repository tại đúng commit SHA qua GitHub REST API,
    sau đó lọc sạch và đánh số dòng.
    
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
            zip_bytes = resp.read()
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Không thể tải code từ GitHub (HTTP {e.code}): {err_msg}")

    # Giải nén ra thư mục tạm thời
    with tempfile.TemporaryDirectory() as temp_dir:
        with zipfile.ZipFile(io.BytesIO(zip_bytes)) as z:
            z.extractall(temp_dir)

        # GitHub zipball thường bọc bên trong 1 thư mục gốc dạng: owner-repo-sha/
        subdirs = [os.path.join(temp_dir, d) for d in os.listdir(temp_dir) if os.path.isdir(os.path.join(temp_dir, d))]
        extract_root = subdirs[0] if subdirs else temp_dir

        # Quét và lọc file
        files_dict, stats = scan_and_collect_sources(extract_root, max_total_files=max_total_files)
        code_context = format_code_with_line_numbers(files_dict)

    return files_dict, stats, code_context
