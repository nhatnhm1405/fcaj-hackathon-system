import { apiFetch, type ApiResponse } from './core';

export interface ScoringCriteria {
  criteriaId: number;
  name: string;
  description?: string;
  weight: number;
  maxScore: number;
  orderNumber: number;
}

export interface CreateCriteriaPayload {
  name: string;
  description?: string;
  weight: number;
  maxScore: number;
  orderNumber: number;
}

export interface ScoreEntry {
  criteriaId: number;
  value: number;
  comment?: string;
}

export interface SubmitScoresPayload {
  submissionId: number;
  draft: boolean;
  scores: ScoreEntry[];
}

// Backend returns a FLAT list — one row per (judge, criteria) — not grouped.
export interface ScoreRecord {
  scoreId: number;
  submissionId: number;
  judgeUserId: number;
  judgeName: string;
  criteriaId: number;
  criteriaName: string;
  value: number;
  comment?: string;
  isDraft: boolean;
  scoredAt?: string;
  updatedAt?: string;
}

export interface JudgeScoringProgress {
  judgeUserId: number;
  judgeName: string;
  status: 'NOT_STARTED' | 'DRAFT' | 'INCOMPLETE' | 'FINAL';
  missingCriteria: string[];
}

export interface SubmissionScoringProgress {
  submissionId: number;
  teamId: number;
  teamName: string;
  trackId?: number | null;
  trackName?: string | null;
  assignedJudgeCount: number;
  completedJudgeCount: number;
  complete: boolean;
  judges: JudgeScoringProgress[];
}

export const scoringApi = {
  getProgress: (eventId: number, roundId: number) =>
    apiFetch<ApiResponse<SubmissionScoringProgress[]>>(
      `/api/events/${eventId}/rounds/${roundId}/scoring-progress`),

  getCriteria: (eventId: number, roundId: number) =>
    apiFetch<ApiResponse<ScoringCriteria[]>>(`/api/events/${eventId}/rounds/${roundId}/criteria`),

  createCriteria: (eventId: number, roundId: number, payload: CreateCriteriaPayload) =>
    apiFetch<ApiResponse<ScoringCriteria>>(`/api/events/${eventId}/rounds/${roundId}/criteria`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  submitScores: (payload: SubmitScoresPayload) =>
    apiFetch<ApiResponse<unknown>>('/api/scores', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  getScoresForSubmission: (submissionId: number) =>
    apiFetch<ApiResponse<ScoreRecord[]>>(`/api/scores/submission/${submissionId}`),

  getMyScoresForRound: (roundId: number) =>
    apiFetch<ApiResponse<ScoreRecord[]>>(`/api/scores/my/round/${roundId}`),
};
