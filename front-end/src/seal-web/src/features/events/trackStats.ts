// Pure, dependency-free helpers for the Coordinator's track statistics shown on
// the Events screen (Tracks tab). Kept separate from CoordEventsPage so the ceil
// formula and the assigned/unassigned counts are trivial to unit-test.
//
// The "roster" these operate on is the event's APPROVED teams — the same set the
// backend freezes and distributes into track slots on entering SETUP
// (HackathonEventService.computeTrackCapacities). The page filters to APPROVED
// before calling these; the functions themselves stay status-agnostic.

// Minimal shape these helpers need from a team. `trackId` is null/undefined when
// the team has not been assigned to a track yet. (Note: the apiClient `Team` type
// declares trackId as `number`, but the API returns null for unassigned teams, so
// every check here is null-safe.)
export interface TrackStatsTeam {
  trackId?: number | null;
}

/**
 * Max teams a single track can hold = ceil(totalTeams / trackCount), rounded UP
 * so every team is guaranteed a slot. Returns 0 when there are no tracks, which
 * both avoids a division by zero and reads sensibly ("no tracks → 0 per track").
 */
export function maxTeamsPerTrack(totalTeams: number, trackCount: number): number {
  if (trackCount <= 0) return 0;
  return Math.ceil(totalTeams / trackCount);
}

/** Teams that have been placed into a track (trackId is set). */
export function countAssigned(teams: TrackStatsTeam[]): number {
  return teams.filter(t => t.trackId != null).length;
}

/** Teams still waiting for a track (trackId is null/undefined). */
export function countUnassigned(teams: TrackStatsTeam[]): number {
  return teams.filter(t => t.trackId == null).length;
}

/** The subset of teams placed into a specific track. */
export function teamsForTrack<T extends TrackStatsTeam>(teams: T[], trackId: number): T[] {
  return teams.filter(t => t.trackId === trackId);
}

// A track needs at least this many teams to be a valid competition track. A track
// with 0 or 1 team is invalid and must be cleaned up before the event can start.
export const MIN_TEAMS_PER_TRACK = 2;

/** Whether a track has enough teams to compete (>= MIN_TEAMS_PER_TRACK). */
export function isTrackValid(teamCount: number): boolean {
  return teamCount >= MIN_TEAMS_PER_TRACK;
}

/**
 * Whether dropping one more team into a track that currently holds `currentCount`
 * would push it past the recommended per-track maximum. A max of 0 (or less) means
 * "no limit known" (e.g. no tracks yet) → never flagged as exceeding.
 */
export function wouldExceedMax(currentCount: number, max: number): boolean {
  if (max <= 0) return false;
  return currentCount + 1 > max;
}

// Input/Output for the "can we leave SETUP?" gate. Kept as plain data so the
// check is a pure function the FE and tests share, mirroring the backend rule
// (HackathonEventService.requireSetupComplete).
export interface SetupGateTrack {
  trackId: number;
  name: string;
  teamCount: number;
}

export interface SetupGateInput {
  tracks: SetupGateTrack[];
  unassignedCount: number;
}

export interface SetupGateResult {
  ok: boolean;
  invalidTracks: SetupGateTrack[];   // tracks with fewer than MIN_TEAMS_PER_TRACK teams
  unassignedCount: number;
  reasons: string[];                 // human-readable, ready to show in a dialog
}

/**
 * Whether the SETUP phase may be left (event started). Requires every track to
 * have at least MIN_TEAMS_PER_TRACK teams AND no team left unassigned. Returns the
 * blocking reasons so the UI can explain exactly what to fix.
 */
export function canCompleteSetup(input: SetupGateInput): SetupGateResult {
  const invalidTracks = input.tracks.filter(t => !isTrackValid(t.teamCount));
  const unassignedCount = input.unassignedCount;
  const reasons: string[] = [];

  if (input.tracks.length === 0) {
    reasons.push("Add at least one track before starting the event.");
  }
  for (const t of invalidTracks) {
    reasons.push(`Track "${t.name}" has ${t.teamCount} team${t.teamCount === 1 ? "" : "s"} — needs at least ${MIN_TEAMS_PER_TRACK}.`);
  }
  if (unassignedCount > 0) {
    reasons.push(`${unassignedCount} team${unassignedCount === 1 ? " is" : "s are"} still unassigned.`);
  }

  return { ok: reasons.length === 0, invalidTracks, unassignedCount, reasons };
}
