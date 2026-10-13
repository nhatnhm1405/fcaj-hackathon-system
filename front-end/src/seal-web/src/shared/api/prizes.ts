import { apiFetch, type ApiResponse } from './core';

// ── Prizes (event-wide awards) ────────────────────────────────────

export interface Prize {
  prizeId: number;
  eventId: number;
  name: string;
  description?: string;
  rankPosition: number;
  teamId?: number | null;
  teamName?: string | null;
  teamTrackName?: string | null;
  finalScore?: number | null;
  awardedAt?: string | null;
  announced: boolean;
}

// A prize's winning team and rank always come from `autoGenerate` (the final
// ranking) — a coordinator may only rename a slot before it's announced.
export interface UpdatePrizePayload {
  name: string;
}

export const prizesApi = {
  // Public sees announced only; coordinator token returns drafts too.
  getAll: (eventId: number) =>
    apiFetch<ApiResponse<Prize[]>>(`/api/events/${eventId}/prizes`),

  update: (eventId: number, prizeId: number, payload: UpdatePrizePayload) =>
    apiFetch<ApiResponse<Prize>>(`/api/events/${eventId}/prizes/${prizeId}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  remove: (eventId: number, prizeId: number) =>
    apiFetch<ApiResponse<void>>(`/api/events/${eventId}/prizes/${prizeId}`, {
      method: 'DELETE',
    }),

  autoGenerate: (eventId: number, topN: number) =>
    apiFetch<ApiResponse<Prize[]>>(`/api/events/${eventId}/prizes/auto-generate`, {
      method: 'POST',
      body: JSON.stringify({ topN }),
    }),

  announce: (eventId: number) =>
    apiFetch<ApiResponse<Prize[]>>(`/api/events/${eventId}/prizes/announce`, {
      method: 'POST',
    }),
};
