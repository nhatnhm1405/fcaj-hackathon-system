// Display rules for an event's phase, shared by every role dashboard. Event status
// is the single source of truth; dates only add context.

import type { TimerStatus } from "@/shared/apiClient";

const MS_PER_DAY = 24 * 60 * 60 * 1000;

export type PhaseBadge = "green" | "cyan" | "blue" | "gray" | "red" | "yellow";

// Visual identity for each event phase (event.status) — drives the phase banner
// label + colour and the header status badge, so a card never contradicts the
// real phase (e.g. showing "REGISTRATION OPEN" during the competition).
export function eventPhaseBanner(phase: string): { label: string; color: string; bg: string; border: string; glow: string; badge: PhaseBadge } {
  switch (phase) {
    case "OPEN":        return { label: "REGISTRATION OPEN", color: "#22c55e", bg: "linear-gradient(90deg, rgba(34,197,94,0.18), rgba(59,130,246,0.10))", border: "rgba(34,197,94,0.45)", glow: "0 0 24px rgba(34,197,94,0.12)", badge: "green" };
    case "SETUP":       return { label: "SETUP PHASE", color: "#22d3ee", bg: "linear-gradient(90deg, rgba(6,182,212,0.18), rgba(59,130,246,0.10))", border: "rgba(6,182,212,0.45)", glow: "0 0 24px rgba(6,182,212,0.12)", badge: "cyan" };
    case "IN_PROGRESS": return { label: "COMPETITION LIVE", color: "#60a5fa", bg: "linear-gradient(90deg, rgba(59,130,246,0.20), rgba(139,92,246,0.10))", border: "rgba(59,130,246,0.5)", glow: "0 0 24px rgba(59,130,246,0.15)", badge: "blue" };
    case "COMPLETED":   return { label: "EVENT COMPLETED", color: "#cbd5e1", bg: "linear-gradient(90deg, rgba(148,163,184,0.14), rgba(100,116,139,0.06))", border: "rgba(148,163,184,0.35)", glow: "none", badge: "gray" };
    case "CANCELLED":   return { label: "EVENT CANCELLED", color: "#f87171", bg: "linear-gradient(90deg, rgba(239,68,68,0.16), rgba(239,68,68,0.06))", border: "rgba(239,68,68,0.4)", glow: "0 0 24px rgba(239,68,68,0.10)", badge: "red" };
    case "DRAFT":       return { label: "DRAFT", color: "#facc15", bg: "linear-gradient(90deg, rgba(234,179,8,0.14), rgba(234,179,8,0.06))", border: "rgba(234,179,8,0.35)", glow: "none", badge: "yellow" };
    default:            return { label: phase || "EVENT", color: "#cbd5e1", bg: "linear-gradient(90deg, rgba(148,163,184,0.12), rgba(100,116,139,0.05))", border: "rgba(148,163,184,0.3)", glow: "none", badge: "gray" };
  }
}

interface EventDates {
  registrationEnd?: string | null;
  startDate?: string | null;
  endDate?: string | null;
}

function parse(iso?: string | null): Date | null {
  if (!iso) return null;
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? null : d;
}

const dayMonth = (d: Date) => d.toLocaleDateString("en-US", { month: "short", day: "numeric" });

/** "Dec 10 - Dec 11, 2026"; empty when the event has no usable dates. */
export function eventDateRange(ev: EventDates): string {
  const start = parse(ev.startDate);
  const end = parse(ev.endDate);
  if (start && end) {
    return start.getFullYear() === end.getFullYear()
      ? `${dayMonth(start)} - ${dayMonth(end)}, ${end.getFullYear()}`
      : `${dayMonth(start)}, ${start.getFullYear()} - ${dayMonth(end)}, ${end.getFullYear()}`;
  }
  const only = start ?? end;
  return only ? `${dayMonth(only)}, ${only.getFullYear()}` : "";
}

const PHASE_LABEL: Record<string, string> = {
  DRAFT: "Draft",
  OPEN: "Registration open",
  SETUP: "Setup phase",
  IN_PROGRESS: "Competition live",
  COMPLETED: "Event completed",
  CANCELLED: "Event cancelled",
};

function phaseTime(status: string, ev: EventDates, now: Date): string {
  const start = parse(ev.startDate);
  const end = parse(ev.endDate);
  switch (status) {
    case "OPEN": {
      const deadline = parse(ev.registrationEnd) ?? start;
      if (!deadline) return "deadline TBA";
      // Still OPEN after the date: teams are accepted, so never claim "closed".
      if (deadline.getTime() < now.getTime()) return "accepting teams";
      if (deadline.toDateString() === now.toDateString()) return "closes today";
      const days = Math.ceil((deadline.getTime() - now.getTime()) / MS_PER_DAY);
      return `${days} day${days === 1 ? "" : "s"} left`;
    }
    case "SETUP":       return start ? `planned start ${dayMonth(start)}` : "";
    case "IN_PROGRESS": return end ? `planned end ${dayMonth(end)}` : "";
    case "COMPLETED":   return end ? `ended ${dayMonth(end)}` : "";
    default:            return "";
  }
}

/** Phase and its time context in one phrase: "Registration open, 6 days left". */
export function phaseSummary(status: string, ev: EventDates = {}, now: Date = new Date()): string {
  const key = status.toUpperCase();
  return [PHASE_LABEL[key] ?? status, phaseTime(key, ev, now)].filter(Boolean).join(", ");
}

/** When each progress step happens, aligned with PROGRESS_STEPS; "" when unknown. */
export function stepDates(status: string, ev: EventDates, now: Date = new Date()): string[] {
  const active = progressIndex(status);
  const deadline = parse(ev.registrationEnd);
  const start = parse(ev.startDate);
  const end = parse(ev.endDate);
  const competition = start && end
    ? (start.getMonth() === end.getMonth() && start.getFullYear() === end.getFullYear()
      ? `${dayMonth(start)} - ${end.getDate()}`
      : `${dayMonth(start)} - ${dayMonth(end)}`)
    : "";
  return [
    active > 0 ? "closed" : deadline && deadline.getTime() >= now.getTime() ? `until ${dayMonth(deadline)}` : "",
    active > 1 ? "done" : start && start.getTime() >= now.getTime() ? `before ${dayMonth(start)}` : "",
    competition,
    end ? `after ${dayMonth(end)}` : "",
  ];
}

/** Contest timer as plain text, e.g. "2:14:36 left to submit". */
export function contestTimerText(status: TimerStatus, remainingSeconds: number): string {
  const total = Math.max(0, Math.floor(remainingSeconds));
  const pad = (n: number) => String(n).padStart(2, "0");
  const h = Math.floor(total / 3600);
  const clock = h > 0
    ? `${h}:${pad(Math.floor((total % 3600) / 60))}:${pad(total % 60)}`
    : `${pad(Math.floor(total / 60))}:${pad(total % 60)}`;
  switch (status) {
    case "RUNNING": return total > 0 ? `${clock} left to submit` : "time is up";
    case "PAUSED":  return `paused with ${clock} left`;
    case "STOPPED": return "submissions stopped";
    case "EXPIRED": return "time is up";
    default:        return "timer not started";
  }
}

export const PROGRESS_STEPS = [
  { label: "Registration", short: "Register" },
  { label: "Setup", short: "Setup" },
  { label: "Competition", short: "Compete" },
  { label: "Completed", short: "Completed" },
] as const;

/** Active step for a status; -1 before it starts, PROGRESS_STEPS.length when all done. */
export function progressIndex(status?: string | null): number {
  switch ((status ?? "").toUpperCase()) {
    case "OPEN":        return 0;
    case "SETUP":       return 1;
    case "IN_PROGRESS": return 2;
    case "COMPLETED":   return PROGRESS_STEPS.length;
    default:            return -1; // DRAFT, CANCELLED or no event
  }
}
