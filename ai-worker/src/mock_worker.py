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
    compute_weighted_overall_score,
)


def run_mock_evaluation(job_input: GradingJobInput, sleep_seconds: float = 1.0) -> GradingJobOutput:
    start_time = time.time()

    # Mô phỏng thời gian suy luận
    if sleep_seconds > 0:
        time.sleep(sleep_seconds)

    criteria_results = []

    # Mock kết quả cho từng tiêu chí trong rubric (đồng bộ 100% với sample_output.json)
    mock_feedback_map = {
        1: (
            8.5,
            "Cấu trúc Spring Boot phân tầng rõ ràng (controller, service, repository, entity). DTO được tách biệt tốt với JPA Entity.",
        ),
        2: (
            7.0,
            "Cấu hình AWS đọc thông tin database từ biến môi trường. Điểm mock cố định phục vụ kiểm thử, không phải kết luận đánh giá bảo mật thực tế.",
        ),
        3: (
            8.0,
            "Có viết service test với Mockito khá đầy đủ cho luồng chấm điểm (AdminScoreDistributionServiceTest).",
        ),
        4: (
            9.5,
            "Quy chuẩn commit message theo Conventional Commits, có ghi chú chi tiết và tài liệu Markdown hướng dẫn kiểm thử rõ ràng.",
        ),
    }

    for crit in job_input.rubrics:
        score, feedback = mock_feedback_map.get(
            crit.criteria_id,
            (8.0, f"Đánh giá tiêu chí {crit.name}: Đạt yêu cầu kỹ thuật cơ bản."),
        )
        # Giới hạn điểm không vượt quá max_score
        score = min(score, crit.max_score)

        criteria_results.append(
            CriterionResult(
                criteria_id=crit.criteria_id,
                score=score,
                feedback=feedback,
            )
        )

    overall_score = compute_weighted_overall_score(criteria_results, job_input.rubrics)

    # Synthetic evidence for the SEAL integration fixture; no repository is evaluated.
    all_mock_evidence = [
        CodeEvidence(
            criteria_id=1,
            type="POSITIVE",
            file_path="back-end/src/seal-api/src/main/java/com/seal/hackathon/entity/Score.java",
            line_start=10,
            line_end=12,
            snippet='@Table(name = "Score", uniqueConstraints = {\n    @UniqueConstraint(name = "uq_score", columnNames = {"submission_id", "judge_user_id", "criteria_id"})\n})',
            comment="Cộng điểm: Đã thiết lập ràng buộc duy nhất (Unique Constraint) tại tầng Entity đảm bảo tính toàn vẹn dữ liệu cho việc chấm điểm.",
        ),
        CodeEvidence(
            criteria_id=2,
            type="NEUTRAL",
            file_path="back-end/src/seal-api/src/main/resources/application-aws.properties",
            line_start=3,
            line_end=5,
            snippet="spring.datasource.url=${SPRING_DATASOURCE_URL:}\nspring.datasource.username=${DB_USERNAME:}\nspring.datasource.password=${DB_PASSWORD:}",
            comment="Dẫn chứng cấu hình AWS nhận datasource từ môi trường, không dùng mật khẩu fallback. Đây là fixture mock, không phải kết quả kiểm toán bảo mật.",
        ),
        CodeEvidence(
            criteria_id=3,
            type="POSITIVE",
            file_path="back-end/src/seal-api/src/test/java/com/seal/hackathon/service/AdminScoreDistributionServiceTest.java",
            line_start=21,
            line_end=28,
            snippet="@ExtendWith(MockitoExtension.class)\nclass AdminScoreDistributionServiceTest {\n\n    @Mock private HackathonEventRepository eventRepository;\n    @Mock private RoundRepository roundRepository;\n    @Mock private TrackRepository trackRepository;\n    @Mock private ScoringCriteriaRepository criteriaRepository;\n    @Mock private ScoreRepository scoreRepository;",
            comment="Cộng điểm: Sử dụng Mockito (@ExtendWith, @Mock) để viết unit test độc lập cho business logic tính toán phân phối điểm.",
        ),
        CodeEvidence(
            criteria_id=4,
            type="POSITIVE",
            file_path="back-end/src/seal-api/Test/ScoringServiceTest.md",
            line_start=1,
            line_end=5,
            snippet="# ScoringService Validation & Test Report\n\nFile service: `src/main/java/com/seal/hackathon/service/ScoringService.java`\n\nFile test: `src/test/java/com/seal/hackathon/service/ScoringServiceTest.java`",
            comment="Cộng điểm: Tài liệu hóa rõ ràng các kịch bản kiểm thử và quy ước nghiệp vụ đi kèm từng service trong mã nguồn.",
        ),
    ]

    valid_criteria_ids = {c.criteria_id for c in job_input.rubrics}
    evidence = [ev for ev in all_mock_evidence if ev.criteria_id in valid_criteria_ids]

    # Nếu có tiêu chí ngoài bộ 1..4 chưa có trong all_mock_evidence thì sinh evidence mặc định
    covered_ids = {ev.criteria_id for ev in evidence}
    for crit in job_input.rubrics:
        if crit.criteria_id not in covered_ids:
            evidence.append(
                CodeEvidence(
                    criteria_id=crit.criteria_id,
                    type="NEUTRAL",
                    file_path="README.md",
                    line_start=1,
                    line_end=1,
                    snippet="# SEAL Hackathon System",
                    comment=f"Ghi chú tham khảo cho tiêu chí {crit.name}.",
                )
            )

    summary = (
        "Repository thể hiện kỹ năng tổ chức backend Spring Boot rất tốt, tuân thủ đúng kiến trúc chuẩn "
        "và có unit test kèm theo. Đây là kết quả mock cố định để kiểm thử tích hợp, không phải đánh giá repository thực tế."
    )

    elapsed_time = round(time.time() - start_time, 2)
    evaluated_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    output = GradingJobOutput(
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
        error_message=None,
    )
    output.validate(job_input)
    return output


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
