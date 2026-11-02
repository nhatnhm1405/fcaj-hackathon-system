import { apiFetch, type ApiResponse } from './core';

// ── Mentor support requests ───────────────────────────────────────────
export type SupportCategory = 'RULES' | 'TECHNICAL' | 'DIRECTION' | 'OTHER';
export type SupportStatus = 'OPEN' | 'RESOLVED' | 'CANCELLED';

export interface MentorContact {
  userId: number;
  fullName: string;
  email?: string | null;
  trackId: number;
  trackName: string;
}

export interface SupportRequest {
  requestId: number;
  teamId: number;
  teamName: string;
  trackId: number;
  trackName: string;
  category: SupportCategory;
  description: string;
  status: SupportStatus;
  requesterName?: string | null;
  createdAt: string;
  resolvedByName?: string | null;
  resolvedAt?: string | null;
}

export const supportApi = {
  // Participant (team leader raises; any member can view).
  getMyMentors: () =>
    apiFetch<ApiResponse<MentorContact[]>>('/api/support-requests/my-mentors'),
  getMine: () =>
    apiFetch<ApiResponse<SupportRequest[]>>('/api/support-requests/mine'),
  create: (payload: { category: SupportCategory; description: string }) =>
    apiFetch<ApiResponse<SupportRequest>>('/api/support-requests', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  cancel: (requestId: number) =>
    apiFetch<ApiResponse<SupportRequest>>(`/api/support-requests/${requestId}/cancel`, { method: 'PUT' }),

  // Mentor.
  listForMentor: () =>
    apiFetch<ApiResponse<SupportRequest[]>>('/api/mentor/support-requests'),
  resolve: (requestId: number) =>
    apiFetch<ApiResponse<SupportRequest>>(`/api/mentor/support-requests/${requestId}/resolve`, { method: 'PUT' }),
};
