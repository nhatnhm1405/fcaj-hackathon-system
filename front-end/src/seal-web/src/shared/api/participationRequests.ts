import { apiFetch, type ApiResponse } from './core';

export interface ParticipationAccessRequest {
  requestId: number;
  userId: number;
  email: string;
  fullName: string;
  userType: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  requestedAt: string;
  resolvedAt?: string;
  resolvedBy?: number;
}

export const participationRequestsApi = {
  request: () =>
    apiFetch<ApiResponse<ParticipationAccessRequest>>('/api/participation-requests', {
      method: 'POST',
    }),

  // Coordinator-facing review endpoints (moved from admin).
  getPending: () =>
    apiFetch<ApiResponse<ParticipationAccessRequest[]>>('/api/coordinator/participation-requests'),

  approve: (requestId: number) =>
    apiFetch<ApiResponse<ParticipationAccessRequest>>(`/api/coordinator/participation-requests/${requestId}/approve`, {
      method: 'POST',
    }),

  reject: (requestId: number) =>
    apiFetch<ApiResponse<ParticipationAccessRequest>>(`/api/coordinator/participation-requests/${requestId}/reject`, {
      method: 'POST',
    }),
};
