"""
Mock Worker for AI Grading Job
Mô phỏng toàn bộ luồng xử lý chấm điểm theo Rubric và trích xuất Evidence.
Sử dụng để Backend kiểm thử luồng tích hợp AWS SQS/S3 độc lập trước khi gắn model AI thật.
"""

import argparse
import sys
import time
from datetime import datetime, timezone
import os
import io

# Đảm bảo UTF-8 output trên Windows console
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

# Cho phép import schema khi chạy trực tiếp từ CLI
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from src.schema import (
    GradingJobInput,
    GradingJobOutput,
    CriterionResult,
    CodeEvidence,
)


def run_mock_evaluation(job_input: GradingJobInput) -> GradingJobOutput:
    start_time = time.time()
    
    # Mô phỏng thời gian suy luận (1 giây)
    time.sleep(1.0)
    
    total_weighted_score = 0.0
    total_weight = 0.0
    criteria_results = []
    
    # Mock kết quả cho từng tiêu chí trong rubric
    mock_feedback_map = {
        1: (8.5, "Cấu trúc mã nguồn phân tầng rõ ràng (controller, service, repository, entity), tuân thủ Clean Architecture."),
        2: (7.0, "Cần bảo mật các secret (database password, JWT secret); nên dùng biến môi trường thay vì commit trực tiếp."),
        3: (8.0, "Có viết unit test và integration test cho các service quan trọng; mock phụ thuộc hợp lý."),
        4: (9.5, "Quy chuẩn commit message theo Conventional Commits, có tài liệu hướng dẫn và cấu hình rõ ràng.")
    }

    for crit in job_input.rubrics:
        score, feedback = mock_feedback_map.get(
            crit.criteria_id, 
            (8.0, f"Đánh giá tiêu chí {crit.name}: Đạt yêu cầu kỹ thuật cơ bản.")
        )
        # Giới hạn điểm không vượt quá max_score
        score = min(score, crit.max_score)
        
        criteria_results.append(CriterionResult(
            criteria_id=crit.criteria_id,
            score=score,
            feedback=feedback
        ))
        total_weighted_score += score * crit.weight
        total_weight += crit.weight

    overall_score = round(total_weighted_score / total_weight, 2) if total_weight > 0 else 0.0

    # Mock evidence có dẫn chứng file, line range, snippet
    evidence = [
        CodeEvidence(
            criteria_id=2,
            type="PENALTY",
            file_path="back-end/src/seal-api/src/main/resources/application.properties",
            line_start=10,
            line_end=12,
            snippet="spring.datasource.password=secret123\njwt.token.secret=base64SecretKeyHere...",
            comment="Trừ 3.0 điểm: Mật khẩu và JWT Secret được khai báo trực tiếp trong file cấu hình, có nguy cơ lộ lọt khi push mã nguồn."
        ),
        CodeEvidence(
            criteria_id=1,
            type="POSITIVE",
            file_path="back-end/src/seal-api/src/main/java/com/seal/hackathon/entity/Score.java",
            line_start=10,
            line_end=12,
            snippet="@Table(name = \"Score\", uniqueConstraints = {\n    @UniqueConstraint(name = \"uq_score\", columnNames = {\"submission_id\", \"judge_user_id\", \"criteria_id\"})\n})",
            comment="Cộng điểm: Đã thiết lập ràng buộc duy nhất (Unique Constraint) đảm bảo tính toàn vẹn dữ liệu cho việc chấm điểm."
        ),
        CodeEvidence(
            criteria_id=3,
            type="POSITIVE",
            file_path="back-end/src/seal-api/src/test/java/com/seal/hackathon/service/AdminScoreDistributionServiceTest.java",
            line_start=25,
            line_end=35,
            snippet="@ExtendWith(MockitoExtension.class)\nclass AdminScoreDistributionServiceTest {\n    @Mock\n    private ScoreRepository scoreRepository;",
            comment="Cộng điểm: Sử dụng Mockito để viết unit test độc lập cho business logic tính toán phân phối điểm."
        )
    ]

    summary = (
        f"Đánh giá bài nộp #{job_input.submission_id} tại commit {job_input.repository.commit_sha[:7]}: "
        f"Chất lượng code khá tốt ({overall_score}/10). Kiến trúc phân tầng rõ ràng, có unit test. "
        "Điểm cần cải thiện chính là vấn đề bảo mật và quản lý bí mật cấu hình."
    )

    elapsed_time = round(time.time() - start_time, 2)
    evaluated_at = datetime.now(timezone.utc).isoformat()

    return GradingJobOutput(
        job_id=job_input.job_id,
        submission_id=job_input.submission_id,
        status="SUCCESS",
        evaluated_at=evaluated_at,
        execution_time_seconds=elapsed_time,
        model_name="mock-evaluator-v1",
        overall_score=overall_score,
        criteria_results=criteria_results,
        evidence=evidence,
        summary=summary,
        error_message=None
    )


def main():
    parser = argparse.ArgumentParser(description="Chạy Mock Worker chấm điểm AI")
    parser.add_argument("--input", "-i", required=True, help="Đường dẫn file input JSON")
    parser.add_argument("--output", "-o", required=True, help="Đường dẫn file output JSON lưu kết quả")
    args = parser.parse_args()

    print(f"[INFO] Bắt đầu đọc input: {args.input}")
    try:
        job_input = GradingJobInput.from_json_file(args.input)
        print(f"[INFO] Đã nhận job {job_input.job_id} cho submission {job_input.submission_id}")
        print(f"[INFO] Repo: {job_input.repository.url} @ commit {job_input.repository.commit_sha}")
        print(f"[INFO] Số lượng tiêu chí rubric: {len(job_input.rubrics)}")

        result = run_mock_evaluation(job_input)
        result.to_json_file(args.output)
        
        print(f"[SUCCESS] Đã hoàn thành chấm điểm! Điểm tổng: {result.overall_score}/10")
        print(f"[SUCCESS] Kết quả đã ghi ra: {args.output}")
        print(f"[SUCCESS] Thời gian thực thi: {result.execution_time_seconds}s")
    except Exception as e:
        print(f"[ERROR] Lỗi xử lý job: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
