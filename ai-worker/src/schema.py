"""
Data models and validation for AI Worker Input/Output contracts.
Supports Python standard dataclasses for zero-dependency execution.
"""

from dataclasses import dataclass, field, asdict
from typing import List, Optional, Dict, Any
import json
import re


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
        if self.weight <= 0:
            raise ValueError(f"Rubric {self.name}: weight must be > 0")
        if self.max_score <= 0:
            raise ValueError(f"Rubric {self.name}: max_score must be > 0")


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

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "GradingJobInput":
        repo_data = data.get("repository", {})
        repo = RepositoryInfo(
            url=repo_data.get("url", ""),
            commit_sha=repo_data.get("commit_sha", ""),
            branch=repo_data.get("branch", "main"),
            access_token=repo_data.get("access_token"),
        )
        repo.validate()

        rubrics = []
        for r in data.get("rubrics", []):
            crit = RubricCriterion(
                criteria_id=r["criteria_id"],
                name=r["name"],
                description=r.get("description", ""),
                weight=float(r["weight"]),
                max_score=float(r.get("max_score", 10.0)),
            )
            crit.validate()
            rubrics.append(crit)

        opts_data = data.get("options", {})
        options = GradingOptions(
            target_languages=opts_data.get("target_languages", ["java", "typescript", "python"]),
            ignore_paths=opts_data.get("ignore_paths", ["node_modules", "dist", "target", ".git"]),
        )

        return cls(
            job_id=data["job_id"],
            submission_id=data["submission_id"],
            round_id=data.get("round_id"),
            repository=repo,
            rubrics=rubrics,
            options=options,
        )

    @classmethod
    def from_json_file(cls, path: str) -> "GradingJobInput":
        with open(path, "r", encoding="utf-8") as f:
            return cls.from_dict(json.load(f))


@dataclass
class CriterionResult:
    criteria_id: int
    score: float
    feedback: str


@dataclass
class CodeEvidence:
    criteria_id: int
    type: str  # "PENALTY" | "POSITIVE" | "NEUTRAL"
    file_path: str
    line_start: int
    line_end: int
    snippet: str
    comment: str


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

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)

    def to_json_file(self, path: str, indent: int = 2):
        with open(path, "w", encoding="utf-8") as f:
            json.dump(self.to_dict(), f, indent=indent, ensure_ascii=False)
