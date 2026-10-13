import { apiFetch, type ApiResponse } from './core';

export interface RoundResult {
  resultId: number;
  teamId: number;
  teamName: string;
  trackName?: string;
  roundId: number;
  roundName?: string;
  totalScore: number;
  rankPosition: number;
  advanced: boolean;
  isPublished?: boolean;
  finalizedAt?: string;
}

export const resultsApi = {
  getPublished: (eventId: number, roundId: number) =>
    apiFetch<ApiResponse<RoundResult[]>>(`/api/events/${eventId}/rounds/${roundId}/results`),

  getAll: (eventId: number, roundId: number) =>
    apiFetch<ApiResponse<RoundResult[]>>(`/api/events/${eventId}/rounds/${roundId}/results/all`),

  finalize: (eventId: number, roundId: number) =>
    apiFetch<ApiResponse<void>>(`/api/events/${eventId}/rounds/${roundId}/results/finalize`, {
      method: 'POST',
    }),

  publish: (eventId: number, roundId: number) =>
    apiFetch<ApiResponse<void>>(`/api/events/${eventId}/rounds/${roundId}/results/publish`, {
      method: 'POST',
    }),
};
