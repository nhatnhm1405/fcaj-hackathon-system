import { apiFetch, type ApiResponse } from './core';

// ── AI Judge Assistant ────────────────────────────────────────────
// Advisory, anonymity-safe reading of a submission to help a judge orient
// before scoring. Never writes scores — suggestions are reference-only.

export interface AiCriteriaInsight {
  criteriaName: string;
  assessment: string;
  suggestedScoreRange: string;
}

// Anonymized analysis of the submission's GitHub repository. Facts here are read
// straight from GitHub (not the model), so techStack/signals/redFlags are reliable.
export interface AiRepoAnalysis {
  analyzed: boolean;
  note: string | null;
  techStack: string[];
  signals: string[];
  redFlags: string[];
}

export interface AiInsight {
  summary: string;
  strengths: string[];
  concerns: string[];
  criteriaInsights: AiCriteriaInsight[];
  repo?: AiRepoAnalysis | null;
  disclaimer: string;
  model: string;
}

export const aiApi = {
  // JUDGE / EVENT_COORDINATOR only. May take a few seconds (calls Gemini).
  getSubmissionInsights: (submissionId: number) =>
    apiFetch<ApiResponse<AiInsight>>(`/api/ai/submissions/${submissionId}/insights`, {
      method: 'POST',
    }),
};
