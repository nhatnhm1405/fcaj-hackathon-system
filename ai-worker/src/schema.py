"""
Data models and validation for AI Worker Input/Output contracts.
Supports Python standard dataclasses for zero-dependency execution.
"""

from dataclasses import dataclass, field, asdict
from typing import List, Optional, Dict, Any
import json
import re


VALID_EVIDENCE_TYPES = {"PENALTY", "POSITIVE", "NEUTRAL"}
VALID_JOB_STATUSES = {"SUCCESS", "FAILED"}


@dataclass
class RepositoryInfo:
    url: str
    commit_sha: str
    branch: str = "main"
    access_token: Optional[str] = None

    def validate(self):
        if not self.url:
            raise ValueError("repository.url is required")
        if not self.commit_sha or not re.match(r"^[0-9a-fA-F]{40}$", self.commit_sha):
            raise ValueError("repository.commit_sha must be a 40-character hex string")


@dataclass
class RubricCriterion:
    criteria_id: int
    name: str
    description: str
    weight: float
    max_score: float = 10.0

    def validate(self):
        if self.weight <= 0 or self.weight > 1.0:
            raise ValueError(f"Rubric {self.name}: weight must be in (0.0, 1.0]")
        if self.max_score < 1.0:
            raise ValueError(f"Rubric {self.name}: max_score must be >= 1.0")


@dataclass
class GradingOptions:
    target_languages: List[str] = field(default_factory=lambda: ["java", "typescript", "python"])
    ignore_paths: List[str] = field(
        default_factory=lambda: ["node_modules", "dist", "target", ".git", "build"]
    )


@dataclass
class GradingJobInput:
    job_id: str
    submission_id: int
    repository: RepositoryInfo
    rubrics: List[RubricCriterion]
    round_id: Optional[int] = None
    options: GradingOptions = field(default_factory=GradingOptions)

    def validate(self):
        if not self.job_id or not str(self.job_id).strip():
            raise ValueError("job_id is required")
        if not isinstance(self.submission_id, int) or self.submission_id <= 0:
            raise ValueError("submission_id must be a positive integer")
        self.repository.validate()
        if not self.rubrics:
            raise ValueError("rubrics must contain at least 1 criterion")
        seen_ids = set()
        for r in self.rubrics:
            r.validate()
            if r.criteria_id in seen_ids:
                raise ValueError(f"Duplicate criteria_id in rubrics: {r.criteria_id}")
            seen_ids.add(r.criteria_id)

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "GradingJobInput":
        repo_data = data.get("repository", {})
        repo = RepositoryInfo(
            url=repo_data.get("url", ""),
            commit_sha=repo_data.get("commit_sha", ""),
            branch=repo_data.get("branch", "main"),
            access_token=repo_data.get("access_token"),
        )

        rubrics = []
        for r in data.get("rubrics", []):
            crit = RubricCriterion(
                criteria_id=int(r["criteria_id"]),
                name=r["name"],
                description=r.get("description", ""),
                weight=float(r["weight"]),
                max_score=float(r.get("max_score", 10.0)),
            )
            rubrics.append(crit)

        opts_data = data.get("options", {})
        options = GradingOptions(
            target_languages=opts_data.get("target_languages", ["java", "typescript", "python"]),
            ignore_paths=opts_data.get("ignore_paths", ["node_modules", "dist", "target", ".git"]),
        )

        instance = cls(
            job_id=data["job_id"],
            submission_id=int(data["submission_id"]),
            round_id=data.get("round_id"),
            repository=repo,
            rubrics=rubrics,
            options=options,
        )
        instance.validate()
        return instance

    @classmethod
    def from_json_file(cls, path: str) -> "GradingJobInput":
        with open(path, "r", encoding="utf-8") as f:
            return cls.from_dict(json.load(f))


@dataclass
class CriterionResult:
    criteria_id: int
    score: float
    feedback: str

    def validate(self, max_score: float = 10.0):
        if self.score < 0.0 or self.score > max_score:
            raise ValueError(
                f"Criterion {self.criteria_id}: score {self.score} out of range [0.0, {max_score}]"
            )
        if not self.feedback or not self.feedback.strip():
            raise ValueError(f"Criterion {self.criteria_id}: feedback must not be empty")


@dataclass
class CodeEvidence:
    criteria_id: int
    type: str  # "PENALTY" | "POSITIVE" | "NEUTRAL"
    file_path: str
    line_start: int
    line_end: int
    snippet: str
    comment: str

    def validate(self):
        if self.type not in VALID_EVIDENCE_TYPES:
            raise ValueError(
                f"Evidence type '{self.type}' must be one of {sorted(VALID_EVIDENCE_TYPES)}"
            )
        if not self.file_path or not self.file_path.strip():
            raise ValueError("Evidence file_path must not be empty")
        if self.line_start < 1:
            raise ValueError(f"Evidence line_start must be >= 1, got {self.line_start}")
        if self.line_end < self.line_start:
            raise ValueError(
                f"Evidence line_end ({self.line_end}) must be >= line_start ({self.line_start})"
            )
        if not self.snippet or not self.snippet.strip():
            raise ValueError("Evidence snippet must not be empty")
        if not self.comment or not self.comment.strip():
            raise ValueError("Evidence comment must not be empty")


def compute_weighted_overall_score(
    criteria_results: List[CriterionResult],
    rubrics: List[RubricCriterion],
) -> float:
    """
    Tính điểm tổng kết (thang 10, làm tròn 2 chữ số thập phân) từ điểm từng tiêu chí
    và trọng số trong rubric. Đảm bảo kết quả nhất quán bằng Python, không phụ thuộc
    vào phép tính nhẩm của LLM.
    """
    score_by_id = {cr.criteria_id: cr.score for cr in criteria_results}
    total_weighted = 0.0
    total_weight = 0.0

    for r in rubrics:
        if r.criteria_id in score_by_id:
            # Quy đổi về thang 10 nếu max_score khác 10.0
            normalized_score = (score_by_id[r.criteria_id] / r.max_score) * 10.0
            total_weighted += normalized_score * r.weight
            total_weight += r.weight

    if total_weight <= 0.0:
        return 0.0
    return round(total_weighted / total_weight, 2)


@dataclass
class GradingJobOutput:
    job_id: str
    submission_id: int
    status: str  # "SUCCESS" | "FAILED"
    evaluated_at: str
    execution_time_seconds: float
    model_name: str
    overall_score: float
    criteria_results: List[CriterionResult]
    evidence: List[CodeEvidence]
    summary: str
    error_message: Optional[str] = None

    def validate(self, job_input: Optional[GradingJobInput] = None):
        if not self.job_id or not str(self.job_id).strip():
            raise ValueError("job_id is required")
        if self.status not in VALID_JOB_STATUSES:
            raise ValueError(f"status must be one of {sorted(VALID_JOB_STATUSES)}")
        if self.overall_score < 0.0 or self.overall_score > 10.0:
            raise ValueError(f"overall_score {self.overall_score} must be in [0.0, 10.0]")
        if not self.model_name or not self.model_name.strip():
            raise ValueError("model_name is required")

        max_score_map = {}
        if job_input is not None:
            if self.job_id != job_input.job_id:
                raise ValueError("output.job_id does not match input.job_id")
            if self.submission_id != job_input.submission_id:
                raise ValueError("output.submission_id does not match input.submission_id")
            max_score_map = {r.criteria_id: r.max_score for r in job_input.rubrics}

        if self.status == "SUCCESS":
            if not self.criteria_results:
                raise ValueError("criteria_results must not be empty when status is SUCCESS")
            result_ids = set()
            for cr in self.criteria_results:
                cr.validate(max_score=max_score_map.get(cr.criteria_id, 10.0))
                if cr.criteria_id in result_ids:
                    raise ValueError(f"Duplicate criteria_id in criteria_results: {cr.criteria_id}")
                result_ids.add(cr.criteria_id)

            if job_input is not None:
                expected_ids = set(max_score_map.keys())
                if result_ids != expected_ids:
                    raise ValueError(
                        f"criteria_results IDs {sorted(result_ids)} do not match rubric IDs {sorted(expected_ids)}"
                    )
                expected_overall = compute_weighted_overall_score(
                    self.criteria_results, job_input.rubrics
                )
                if abs(self.overall_score - expected_overall) > 0.01:
                    raise ValueError(
                        f"overall_score ({self.overall_score}) does not match weighted calculation ({expected_overall})"
                    )

            for ev in self.evidence:
                ev.validate()
                if job_input is not None and ev.criteria_id not in max_score_map:
                    raise ValueError(
                        f"Evidence criteria_id {ev.criteria_id} is not in rubric criteria"
                    )

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "GradingJobOutput":
        criteria_results = [
            CriterionResult(
                criteria_id=int(cr["criteria_id"]),
                score=float(cr["score"]),
                feedback=cr["feedback"],
            )
            for cr in data.get("criteria_results", [])
        ]
        evidence = [
            CodeEvidence(
                criteria_id=int(ev["criteria_id"]),
                type=ev["type"],
                file_path=ev["file_path"],
                line_start=int(ev["line_start"]),
                line_end=int(ev["line_end"]),
                snippet=ev["snippet"],
                comment=ev["comment"],
            )
            for ev in data.get("evidence", [])
        ]
        instance = cls(
            job_id=data["job_id"],
            submission_id=int(data["submission_id"]),
            status=data["status"],
            evaluated_at=data["evaluated_at"],
            execution_time_seconds=float(data["execution_time_seconds"]),
            model_name=data.get("model_name", "unknown"),
            overall_score=float(data["overall_score"]),
            criteria_results=criteria_results,
            evidence=evidence,
            summary=data.get("summary", ""),
            error_message=data.get("error_message"),
        )
        instance.validate()
        return instance

    @classmethod
    def from_json_file(cls, path: str) -> "GradingJobOutput":
        with open(path, "r", encoding="utf-8") as f:
            return cls.from_dict(json.load(f))

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)

    def to_json_file(self, path: str, indent: int = 2):
        with open(path, "w", encoding="utf-8") as f:
            json.dump(self.to_dict(), f, indent=indent, ensure_ascii=False)
            f.write("\n")
