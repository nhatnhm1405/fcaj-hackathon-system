import { apiFetch, type ApiResponse } from './core';

// A team leader's request to re-attach their existing team to a new season
// (creates a new TeamEventEntry on approval) — mirrors ParticipationAccessRequest,
// scoped to a team instead of a user.
export interface TeamRejoinRequest {
  requestId: number;
  teamId: number;
  teamName: string;
  eventId: number;
  eventName: string;
  requestedByUserId: number;
  requestedByName: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  requestedAt: string;
  resolvedAt?: string;
  resolvedBy?: number;
}

export const teamRejoinRequestsApi = {
  request: (teamId: number, eventId: number) =>
    apiFetch<ApiResponse<TeamRejoinRequest>>(`/api/teams/${teamId}/rejoin-requests`, {
      method: 'POST',
      body: JSON.stringify({ eventId }),
    }),

  // Coordinator-facing review endpoints.
  getPending: () =>
    apiFetch<ApiResponse<TeamRejoinRequest[]>>('/api/coordinator/team-rejoin-requests'),

  approve: (requestId: number) =>
    apiFetch<ApiResponse<TeamRejoinRequest>>(`/api/coordinator/team-rejoin-requests/${requestId}/approve`, {
      method: 'POST',
    }),

  reject: (requestId: number) =>
    apiFetch<ApiResponse<TeamRejoinRequest>>(`/api/coordinator/team-rejoin-requests/${requestId}/reject`, {
      method: 'POST',
    }),
};
