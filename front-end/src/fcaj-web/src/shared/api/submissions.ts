import { apiFetch, type ApiResponse } from './core';

export interface Submission {
  submissionId: number;
  teamId: number;
  teamName: string;
  roundId: number;
  roundName?: string;
  repoUrl: string;
  demoUrl?: string;
  slideUrl?: string;
  description?: string;
  submittedAt: string;
  submittedByName?: string;
  status?: string;
}

export interface CreateSubmissionPayload {
  roundId: number;
  repoUrl: string;
  demoUrl?: string;
  slideUrl?: string;
  description?: string;
}

export type SubmissionEligibilityStatus = "ELIGIBLE" | "ADVANCED" | "ELIMINATED" | "WAITING_FOR_RESULTS";

export interface SubmissionEligibility {
  roundId: number;
  roundName: string;
  eligible: boolean;
  status: SubmissionEligibilityStatus;
  reason: string;
  previousRoundId?: number | null;
  previousRoundName?: string | null;
  rankPosition?: number | null;
  topNAdvance?: number | null;
}

export const submissionsApi = {
  submit: (payload: CreateSubmissionPayload) =>
    apiFetch<ApiResponse<Submission>>('/api/submissions', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  getMyForRound: (roundId: number) =>
    apiFetch<ApiResponse<Submission>>(`/api/submissions/my/round/${roundId}`),

  getMyEligibility: (roundId: number) =>
    apiFetch<ApiResponse<SubmissionEligibility>>(`/api/submissions/my/round/${roundId}/eligibility`),

  getAllForRound: (roundId: number) =>
    apiFetch<ApiResponse<Submission[]>>(`/api/submissions/round/${roundId}`),

  getById: (submissionId: number) =>
    apiFetch<ApiResponse<Submission>>(`/api/submissions/${submissionId}`),
};
