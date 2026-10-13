// Participant-facing rules for when a team can still be edited, derived from the
// event's lifecycle status. Team composition (name, members, leadership) may only
// change during Registration (OPEN) and Setup (SETUP). Once the contest starts
// (IN_PROGRESS) — and through COMPLETED / CANCELLED — the team is locked.

export const MIN_TEAM_SIZE = 3;
export const MAX_TEAM_SIZE = 5;

/** True while team composition can still be changed. Unknown status → permissive. */
export function isTeamEditable(status?: string): boolean {
  const s = (status ?? "").toUpperCase();
  if (!s) return true;
  return s === "OPEN" || s === "SETUP";
}

/** Human-readable reason the team is locked, or null when still editable. */
export function teamLockReason(status?: string): string | null {
  if (isTeamEditable(status)) return null;
  switch ((status ?? "").toUpperCase()) {
    case "IN_PROGRESS":
      return "The contest is in progress — your team is locked. The team name, members and leadership can no longer be changed.";
    case "COMPLETED":
      return "This event has ended — your team is locked.";
    case "CANCELLED":
      return "This event was cancelled — team actions are disabled.";
    default:
      return "Team editing is currently locked.";
  }
}

/** Leader may pick/change the track only during SETUP with SELF_SELECT mode. */
export function canPickTrack(status?: string, mode?: string): boolean {
  return (status ?? "").toUpperCase() === "SETUP" && (mode ?? "").toUpperCase() === "SELF_SELECT";
}
