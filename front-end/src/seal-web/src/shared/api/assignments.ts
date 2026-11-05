import { apiFetch, type ApiResponse } from './core';

// ── Team Assignments ──────────────────────────────────────────────

// Both endpoints return a SINGLE object (the logged-in mentor/judge) whose
// `teams` is the work list. Judge entries carry the roundId to score.
export interface AssignmentMember {
  userId: number;
  fullName: string;
  email: string;
  memberRole: 'LEADER' | 'MEMBER';
  // Full member detail for the team-detail modal (mentor + judge views).
  studentId?: string | null;
  userType?: string | null;   // FPT_STUDENT | EXTERNAL_STUDENT | STAFF
  university?: string | null;
}

export interface MentorAssignedTeam {
  teamId: number;
  teamName: string;
  trackId: number;
  trackName: string;
  // Event that this track/team belongs to — lets the mentor view group tracks
  // per event (a mentor may be assigned across multiple hackathon seasons).
  eventId: number;
  eventName: string;
  season?: string;
  year?: number;
  eventStatus?: string;
  members: AssignmentMember[];
  submissionCount: number;
  lastSubmittedAt: string | null;
  // Furthest round the team is still in; eliminated = knocked out at that round.
  currentRoundName?: string | null;
  eliminated?: boolean;
}

export interface JudgeAssignedTeam {
  teamId: number;
  teamName: string;
  trackName: string;
  roundId: number;
  assignedJudgeCount?: number;
  members: AssignmentMember[];
}

export interface MentorAssignedTrack {
  trackId: number;
  trackName: string;
  eventId: number;
  eventName: string;
  season?: string;
  year?: number;
  eventStatus?: string;
}

export interface MentorAssignment {
  mentorId: number;
  mentorName: string;
  eventName: string;
  teams: MentorAssignedTeam[];
  // Every track the mentor is assigned to, including tracks with no approved teams
  // yet — lets the UI list all assigned events/tracks, not only populated ones.
  tracks?: MentorAssignedTrack[];
}

export interface JudgeAssignment {
  judgeId: number;
  judgeName: string;
  eventId: number | null;
  eventName: string;
  teams: JudgeAssignedTeam[];
}

// Read-only mentor history (GET /api/mentor/assignments/history).
export interface MentorHistoryEntry {
  eventId: number;
  eventName: string;
  season?: string;
  year?: number;
  eventStatus: string;
  tracks: {
    trackId: number;
    trackName: string;
    teams: {
      teamId: number; teamName: string; teamStatus: string;
      finalRank?: number | null; prizeName?: string | null;
      memberCount?: number;
      members?: { fullName: string; memberRole: string; studentId?: string | null; userType?: string | null; university?: string | null }[];
    }[];
  }[];
}

export const assignmentsApi = {
  getMentorAssignments: () =>
    apiFetch<ApiResponse<MentorAssignment>>('/api/mentor/assignments'),

  getMentorHistory: () =>
    apiFetch<ApiResponse<MentorHistoryEntry[]>>('/api/mentor/assignments/history'),

  getJudgeAssignments: () =>
    apiFetch<ApiResponse<JudgeAssignment>>('/api/judge/assignments'),
};
