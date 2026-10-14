import { apiFetch, type ApiResponse } from './core';
import type { UserItem } from './admin';

// ── Coordinator lookups & assignments ─────────────────────────────

// Roster row: one judge assigned to one round (+track for preliminary rounds).
export interface JudgeRosterItem {
  id: number;
  judgeUserId: number;
  judgeName: string;
  judgeType?: string;
  roundId: number;
  roundName: string;
  isFinal?: boolean;
  trackId?: number;
  trackName?: string;
}

export interface AssignJudgePayload {
  judgeUserId: number;
  roundId: number;
  trackId?: number | null;
}

// Mentor roster row: one mentor assigned to one track (whole event).
export interface MentorRosterItem {
  id: number;
  mentorUserId: number;
  mentorName: string;
  trackId: number;
  trackName: string;
}

export interface AssignMentorPayload {
  mentorUserId: number;
  trackId: number;
}

export interface CreateGuestJudgePayload {
  fullName: string;
  email: string;
  password: string;
  roundId: number;
  trackId?: number | null;
}

// Full coordinator retrospective of one past event — teams/results/prizes plus
// the mentors/judges who worked each track/round. Used by the "History" page.
export interface CoordinatorHistoryMember {
  fullName: string;
  memberRole?: string | null;
  studentId?: string | null;
  userType?: string | null;
  university?: string | null;
}

export interface CoordinatorHistoryTeam {
  teamId: number;
  teamName: string;
  teamStatus: string;
  finalRank?: number | null;
  prizeName?: string | null;
  memberCount: number;
  members: CoordinatorHistoryMember[];
}

export interface CoordinatorHistoryRoundJudges {
  roundName: string;
  judgeNames: string[];
}

export interface CoordinatorHistoryTrack {
  trackId: number;
  trackName: string;
  mentorNames: string[];
  roundJudges: CoordinatorHistoryRoundJudges[];
  teams: CoordinatorHistoryTeam[];
}

export interface CoordinatorHistoryPrize {
  rankPosition: number;
  prizeName: string;
  teamName?: string | null;
}

export interface CoordinatorEventHistory {
  totalTeams: number;
  submittedTeams: number;
  prizes: CoordinatorHistoryPrize[];
  tracks: CoordinatorHistoryTrack[];
  finalRoundJudges: CoordinatorHistoryRoundJudges[];
}

export const coordinatorApi = {
  // Approved STAFF pool (no longer available under /api/admin after the split)
  getStaff: () =>
    apiFetch<ApiResponse<UserItem[]>>('/api/coordinator/staff'),

  getEventHistory: (eventId: number) =>
    apiFetch<ApiResponse<CoordinatorEventHistory>>(`/api/coordinator/history/${eventId}`),

  // Judge roster for an event
  getJudgeRoster: (eventId: number) =>
    apiFetch<ApiResponse<JudgeRosterItem[]>>(`/api/coordinator/assignments/judges?eventId=${eventId}`),

  assignJudge: (payload: AssignJudgePayload) =>
    apiFetch<ApiResponse<unknown>>('/api/coordinator/assignments/judges', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  removeJudgeAssignment: (assignmentId: number) =>
    apiFetch<ApiResponse<void>>(`/api/coordinator/assignments/judges/${assignmentId}`, { method: 'DELETE' }),

  replaceJudgeAssignment: (assignmentId: number, payload: { judgeUserId: number; reason: string }) =>
    apiFetch<ApiResponse<unknown>>(`/api/coordinator/assignments/judges/${assignmentId}/replace`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  // Mentor assignments (mentor -> track, whole event)
  getMentorRoster: (eventId: number) =>
    apiFetch<ApiResponse<MentorRosterItem[]>>(`/api/coordinator/assignments/mentors?eventId=${eventId}`),

  assignMentor: (payload: AssignMentorPayload) =>
    apiFetch<ApiResponse<unknown>>('/api/coordinator/assignments/mentors', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  removeMentorAssignment: (assignmentId: number) =>
    apiFetch<ApiResponse<void>>(`/api/coordinator/assignments/mentors/${assignmentId}`, { method: 'DELETE' }),

  createGuestJudge: (payload: CreateGuestJudgePayload) =>
    apiFetch<ApiResponse<unknown>>('/api/coordinator/guest-judges', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
};
