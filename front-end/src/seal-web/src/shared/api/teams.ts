import { apiFetch, type ApiResponse } from './core';
import type { UserItem } from './admin';

// ── Teams ─────────────────────────────────────────────────────────

export interface Team {
  teamId: number;
  eventId: number;
  eventName?: string;
  trackId: number;
  trackName?: string;
  name: string;
  description?: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'DISQUALIFIED';
  createdAt?: string;
  members?: TeamMember[];
}

export interface TeamMember {
  userId: number;
  fullName: string;
  email: string;
  role?: 'LEADER' | 'MEMBER';
  memberRole?: 'LEADER' | 'MEMBER'; // field name returned by GET /api/teams/event/{id}
  joinedAt?: string;
}

// GET /api/teams/my — the current user's team in the active event.
export interface MyTeamMember {
  userId: number;
  memberName: string;
  email?: string;
  studentType?: string;
  studentId?: string;
  role: 'LEADER' | 'MEMBER';
  joinedAt?: string;
  /** Whether this member's own account is currently active — a member can be
   *  on the roster (e.g. after a team rejoin) but still need their own
   *  separate reactivation via the participation-access-request flow. */
  isActive?: boolean;
}

export interface MyTeamRound {
  roundId: number;
  name: string;
  orderNumber: number;
  status?: string;
  isFinal: boolean;
  startTime: string;
  endTime: string;
  submissionDeadline: string;
}

export interface MyTeam {
  teamId: number;
  eventId?: number;
  eventName?: string;
  eventTopic?: string;
  trackId?: number | null;
  trackName?: string;
  trackDescription?: string | null;
  name: string;
  eventStatus?: 'DRAFT' | 'OPEN' | 'SETUP' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
  trackSelectionMode?: 'SELF_SELECT' | 'RANDOM';
  status?: 'PENDING' | 'APPROVED' | 'REJECTED' | 'DISQUALIFIED';
  round?: MyTeamRound | null;
  myRole?: 'LEADER' | 'MEMBER';
  members: MyTeamMember[];
  /** True if this team has a PENDING TeamRejoinRequest awaiting coordinator review. */
  hasPendingRejoinRequest?: boolean;
}

export interface UpdateTeamPayload {
  name?: string;
  description?: string;
}

export interface ActiveEventWithTracks {
  eventId: number;
  name: string;
  season?: string;
  year?: number;
  description?: string;
  registrationStart?: string;
  registrationEnd?: string;
  startDate?: string;
  endDate?: string;
  status?: string;
  tracks: { trackId: number; name: string; description?: string }[];
}

export interface CreateTeamPayload {
  eventId: number;
  name: string;
  description?: string;
}

// One past/present team the participant has been on (GET /api/teams/my/history).
export interface TeamHistoryEntry {
  eventId: number;
  eventName: string;
  season?: string;
  year?: number;
  eventStatus: string;
  teamId: number;
  teamName: string;
  trackName?: string | null;
  teamStatus: string;
  myRole?: string;
  members: { fullName: string; role: string; studentId?: string | null; userType?: string | null; university?: string | null }[];
  rounds: { roundName: string; isFinal: boolean; rankPosition: number; advanced: boolean; totalScore: number }[];
  submissions: { roundName: string; repoUrl?: string; demoUrl?: string; slideUrl?: string; submittedAt?: string; status: string }[];
  prize: { name: string; rankPosition: number; awardedAt?: string } | null;
}

// ── Leftover-team grouping (coordinator, SETUP phase, before the track draw) ──
// Preview is a read-only dry run; commit applies the (deterministic) plan.
export interface GroupingMember {
  userId: number;
  fullName: string;
}

export interface GroupingProposedTeam {
  origin: 'NEW' | 'EXISTING';
  existingTeamId?: number | null;
  teamName: string;
  size: number;
  members: GroupingMember[];
  addedMembers: GroupingMember[];
}

export interface GroupingWarning {
  type: 'UNPLACEABLE_LEFTOVER' | 'DEFICIENT_TEAM_UNRESCUED';
  peopleCount: number;
  message: string;
  people: GroupingMember[];
}

export interface GroupingPreview {
  leftoverPeople: number;
  soloCount: number;   // teams of one (movable free agents)
  pairCount: number;   // teams of two (kept together)
  proposedTeams: GroupingProposedTeam[];
  warnings: GroupingWarning[];
}

export interface GroupingCommitResult {
  teamsCreated: number;
  teamsGrown: number;
  peoplePlaced: number;
  unresolvedWarnings: number;
  warnings: GroupingWarning[];
}

// Manual override for the leftover pool the planner couldn't place: place userIds
// onto targetTeamId, or force-create a new team from them when targetTeamId is null.
export interface ManualAssignLeftoverPayload {
  userIds: number[];
  targetTeamId?: number | null;
  reason?: string;
}

// A coordinator-edited version of one Proposed Teams card, submitted in bulk to
// /leftover-grouping/apply — memberUserIds is the team's COMPLETE final roster
// (not just newcomers), after freely dragging people between cards.
export interface ApplyLeftoverGroupingPayload {
  teams: { existingTeamId: number | null; memberUserIds: number[] }[];
  reason?: string;
}

export const teamsApi = {
  getActiveEvents: () =>
    apiFetch<ApiResponse<ActiveEventWithTracks[]>>('/api/teams/active-events'),

  create: (payload: CreateTeamPayload) =>
    apiFetch<ApiResponse<Team>>('/api/teams', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  checkName: (eventId: number, name: string) =>
    apiFetch<ApiResponse<boolean>>(
      `/api/teams/check-name?eventId=${eventId}&name=${encodeURIComponent(name)}`,
    ),

  getMy: () =>
    apiFetch<ApiResponse<MyTeam>>('/api/teams/my'),

  getMyHistory: () =>
    apiFetch<ApiResponse<MyTeam[]>>('/api/teams/my/history'),

  getMyResultHistory: () =>
    apiFetch<ApiResponse<TeamHistoryEntry[]>>('/api/teams/my/result-history'),

  getMyForEvent: (eventId: number) =>
    apiFetch<ApiResponse<MyTeam>>(`/api/teams/my/event/${eventId}`),

  getByEvent: (eventId: number) =>
    apiFetch<ApiResponse<Team[]>>(`/api/teams/event/${eventId}`),

  // Cross-event count backing the Coordinator sidebar's "Teams" badge.
  getPendingCount: () =>
    apiFetch<ApiResponse<{ count: number }>>('/api/teams/pending-count'),

  getById: (teamId: number) =>
    apiFetch<ApiResponse<Team>>(`/api/teams/${teamId}`),

  approve: (teamId: number) =>
    apiFetch<ApiResponse<void>>(`/api/teams/${teamId}/approve`, { method: 'PUT' }),

  reject: (teamId: number, reason: string) =>
    apiFetch<ApiResponse<void>>(`/api/teams/${teamId}/reject`, {
      method: 'PUT',
      body: JSON.stringify({ reason }),
    }),

  disqualify: (teamId: number) =>
    apiFetch<ApiResponse<void>>(`/api/teams/${teamId}/disqualify`, { method: 'PUT' }),

  // Participant team management
  searchUsers: (query: string) =>
    apiFetch<ApiResponse<UserItem[]>>(`/api/teams/search-users?query=${encodeURIComponent(query)}`),

  update: (teamId: number, payload: UpdateTeamPayload) =>
    apiFetch<ApiResponse<MyTeam>>(`/api/teams/${teamId}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  removeMember: (teamId: number, userId: number) =>
    apiFetch<ApiResponse<MyTeam>>(`/api/teams/${teamId}/members/${userId}`, { method: 'DELETE' }),

  transferLeadership: (teamId: number, newLeaderUserId: number) =>
    apiFetch<ApiResponse<MyTeam>>(`/api/teams/${teamId}/transfer/${newLeaderUserId}`, { method: 'PUT' }),

  leave: (teamId: number) =>
    apiFetch<ApiResponse<void>>(`/api/teams/${teamId}/leave`, { method: 'POST' }),

  // Teamless-only: opt out of the current season entirely (event must be OPEN).
  // Distinct from `leave` above, which only leaves the team and stays active.
  leaveEvent: (eventId: number) =>
    apiFetch<ApiResponse<void>>(`/api/teams/event/${eventId}/leave-event`, { method: 'POST' }),

  // SELF_SELECT events: leader picks the team's track during SETUP.
  selectTrack: (teamId: number, trackId: number) =>
    apiFetch<ApiResponse<MyTeam>>(`/api/teams/${teamId}/track`, {
      method: 'PUT',
      body: JSON.stringify({ trackId }),
    }),

  // RANDOM events (or to fill stragglers): coordinator draws teams into tracks.
  drawTracks: (eventId: number, includeAssigned = false) =>
    apiFetch<ApiResponse<Team[]>>(`/api/teams/event/${eventId}/draw-tracks?includeAssigned=${includeAssigned}`, {
      method: 'POST',
    }),

  // SETUP only: coordinator drags a team into a track, or to the unassigned pool
  // (trackId = null). Capacity is NOT hard-capped here (soft warning on the UI).
  assignTrack: (teamId: number, trackId: number | null) =>
    apiFetch<ApiResponse<Team>>(`/api/teams/${teamId}/track-assignment`, {
      method: 'PUT',
      body: JSON.stringify({ trackId }),
    }),

  // SETUP only: dry-run of grouping leftover (under-sized) teams into valid ones.
  leftoverGroupingPreview: (eventId: number) =>
    apiFetch<ApiResponse<GroupingPreview>>(`/api/teams/event/${eventId}/leftover-grouping/preview`),

  // SETUP only: apply the grouping plan (creates/grows teams, dissolves solo teams).
  leftoverGroupingCommit: (eventId: number, reason?: string) =>
    apiFetch<ApiResponse<GroupingCommitResult>>(
      `/api/teams/event/${eventId}/leftover-grouping/commit${reason ? `?reason=${encodeURIComponent(reason)}` : ''}`,
      { method: 'POST' },
    ),

  // SETUP only: manual escape hatch for people the planner couldn't place — put
  // specific userIds onto an existing team, or force-approve them as a new one.
  leftoverGroupingManualAssign: (eventId: number, payload: ManualAssignLeftoverPayload) =>
    apiFetch<ApiResponse<GroupingCommitResult>>(
      `/api/teams/event/${eventId}/leftover-grouping/manual-assign`,
      { method: 'POST', body: JSON.stringify(payload) },
    ),

  // SETUP only: apply a coordinator-edited version of the preview's Proposed Teams
  // (people dragged between team cards) instead of blindly re-running the planner.
  leftoverGroupingApplyPlan: (eventId: number, payload: ApplyLeftoverGroupingPayload) =>
    apiFetch<ApiResponse<GroupingCommitResult>>(
      `/api/teams/event/${eventId}/leftover-grouping/apply`,
      { method: 'POST', body: JSON.stringify(payload) },
    ),
};

// ── Team Invites ──────────────────────────────────────────────────

export interface TeamInvite {
  inviteId: number;
  teamId: number;
  teamName: string;
  eventName?: string;
  trackName?: string;
  teamStatus?: string;
  invitedUserId: number;
  invitedUserName?: string;
  invitedById: number;
  invitedByName: string;
  message?: string;
  status: 'PENDING' | 'ACCEPTED' | 'DECLINED';
  createdAt: string;
  respondedAt?: string;
}

export interface SendInvitePayload {
  invitedUserId: number;
  message?: string;
}

export const invitesApi = {
  send: (teamId: number, payload: SendInvitePayload) =>
    apiFetch<ApiResponse<TeamInvite>>(`/api/invites/teams/${teamId}`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  getPending: () =>
    apiFetch<ApiResponse<TeamInvite[]>>('/api/invites/pending'),

  accept: (inviteId: number) =>
    apiFetch<ApiResponse<void>>(`/api/invites/${inviteId}/accept`, { method: 'PUT' }),

  decline: (inviteId: number) =>
    apiFetch<ApiResponse<void>>(`/api/invites/${inviteId}/decline`, { method: 'PUT' }),
};

// ── Join Requests (participant asks to join a team) ────────────────

export interface JoinableTeam {
  teamId: number;
  teamName: string;
  eventId: number;
  eventName: string;
  trackId?: number;
  trackName?: string;
  teamStatus: string;
  memberCount: number;
  leaderName?: string;
  alreadyRequested?: boolean;
}

export interface JoinableTeamList {
  totalJoinableTeams: number;
  teams: JoinableTeam[];
}

export interface JoinRequest {
  requestId: number;
  teamId: number;
  teamName: string;
  eventName?: string;
  trackName?: string;
  requesterId: number;
  requesterName: string;
  requesterEmail?: string;
  message?: string;
  status: 'PENDING' | 'ACCEPTED' | 'DECLINED';
  createdAt: string;
  respondedAt?: string;
}

export const joinRequestsApi = {
  // Browse / search teams the participant can request to join.
  getJoinableTeams: (params: { eventId?: number; query?: string } = {}) => {
    const qs = new URLSearchParams();
    if (params.eventId != null) qs.set('eventId', String(params.eventId));
    if (params.query) qs.set('query', params.query);
    const suffix = qs.toString() ? `?${qs.toString()}` : '';
    return apiFetch<ApiResponse<JoinableTeamList>>(`/api/join-requests/joinable-teams${suffix}`);
  },

  send: (teamId: number, message?: string) =>
    apiFetch<ApiResponse<JoinRequest>>(`/api/join-requests/teams/${teamId}`, {
      method: 'POST',
      body: JSON.stringify({ message }),
    }),

  // Requests the current participant has sent.
  getMine: () =>
    apiFetch<ApiResponse<JoinRequest[]>>('/api/join-requests/my'),

  cancel: (requestId: number) =>
    apiFetch<ApiResponse<void>>(`/api/join-requests/${requestId}`, { method: 'DELETE' }),

  // Leader inbox: pending requests for a team they lead.
  getForTeam: (teamId: number) =>
    apiFetch<ApiResponse<JoinRequest[]>>(`/api/join-requests/teams/${teamId}`),

  accept: (requestId: number) =>
    apiFetch<ApiResponse<JoinRequest>>(`/api/join-requests/${requestId}/accept`, { method: 'PUT' }),

  decline: (requestId: number) =>
    apiFetch<ApiResponse<JoinRequest>>(`/api/join-requests/${requestId}/decline`, { method: 'PUT' }),
};
