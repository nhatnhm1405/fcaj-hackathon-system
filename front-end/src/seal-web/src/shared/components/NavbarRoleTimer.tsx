import { useCallback, useEffect, useState } from "react";
import {
  assignmentsApi,
  eventsApi,
  roundsApi,
  teamsApi,
  type TimerPhase,
  type TimerStatus,
} from "@/shared/apiClient";
import { useRoundTimer } from "@/shared/hooks/useRoundTimer";
import { C } from "@/shared/components/PixelComponents";

const CONTEXT_RESYNC_MS = 30_000;
const mono = "'JetBrains Mono', monospace";

type TimerRole = "PARTICIPANT" | "JUDGE";

interface TimerTarget {
  ownerRole: TimerRole;
  eventId: number;
  roundId: number;
  roundName: string;
}

interface NavbarRoleTimerProps {
  role: string;
  teamId: number | null;
}

function formatRemaining(totalSeconds: number): string {
  const safeSeconds = Math.max(0, Math.floor(totalSeconds));
  const hours = Math.floor(safeSeconds / 3600);
  const minutes = Math.floor((safeSeconds % 3600) / 60);
  const seconds = safeSeconds % 60;
  return `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

function statusPresentation(
  status: TimerStatus,
  remainingSeconds: number,
  loading: boolean,
  failed: boolean,
  hasTarget: boolean,
): { label: string; color: string; glow: string } {
  if (loading) return { label: "SYNCING", color: C.textMuted, glow: "none" };
  if (failed) return { label: "CONNECTION LOST", color: C.red, glow: "0 0 10px rgba(239,68,68,0.2)" };
  if (!hasTarget) return { label: "NO ACTIVE ROUND", color: C.textMuted, glow: "none" };
  if (status === "PAUSED") return { label: "PAUSED", color: C.yellow, glow: "0 0 10px rgba(234,179,8,0.2)" };
  if (status === "STOPPED") return { label: "LOCKED", color: C.red, glow: "0 0 10px rgba(239,68,68,0.2)" };
  if (status === "EXPIRED" || (status === "RUNNING" && remainingSeconds <= 0)) {
    return { label: "TIME'S UP", color: C.red, glow: "0 0 10px rgba(239,68,68,0.2)" };
  }
  if (status === "IDLE") return { label: "NOT STARTED", color: C.textMuted, glow: "none" };
  if (remainingSeconds <= 60) return { label: "RUNNING", color: C.red, glow: "0 0 12px rgba(239,68,68,0.28)" };
  if (remainingSeconds <= 5 * 60) return { label: "RUNNING", color: C.yellow, glow: "0 0 12px rgba(234,179,8,0.22)" };
  return { label: "RUNNING", color: C.green, glow: "0 0 12px rgba(34,197,94,0.2)" };
}

export function NavbarRoleTimer({ role, teamId }: NavbarRoleTimerProps) {
  const supportedRole = role === "PARTICIPANT" || role === "JUDGE";
  const timerRole = supportedRole ? role as TimerRole : null;
  const phase: TimerPhase = role === "JUDGE" ? "JUDGING" : "CONTEST";
  const [target, setTarget] = useState<TimerTarget | null>(null);
  const [contextLoading, setContextLoading] = useState(supportedRole);
  const [contextFailed, setContextFailed] = useState(false);
  // The round timer must only surface while the owner's event is actually
  // running (IN_PROGRESS); before/after that the whole badge stays hidden.
  const [eventInProgress, setEventInProgress] = useState(false);

  const loadContext = useCallback(async () => {
    if (!timerRole) {
      setTarget(null);
      setEventInProgress(false);
      setContextLoading(false);
      setContextFailed(false);
      return;
    }

    if (timerRole === "PARTICIPANT" && teamId == null) {
      setTarget(null);
      setEventInProgress(false);
      setContextLoading(false);
      setContextFailed(false);
      return;
    }

    setContextFailed(false);
    try {
      if (timerRole === "PARTICIPANT") {
        const response = await teamsApi.getMy();
        const team = response.data;
        const round = team.round;
        const isInProgress = team.eventStatus === "IN_PROGRESS";
        setEventInProgress(isInProgress);
        setTarget(isInProgress && team.eventId != null && round
          ? { ownerRole: timerRole, eventId: team.eventId, roundId: round.roundId, roundName: round.name }
          : null);
      } else {
        const assignmentResponse = await assignmentsApi.getJudgeAssignments();
        const assignment = assignmentResponse.data;
        if (assignment.eventId == null) {
          setEventInProgress(false);
          setTarget(null);
        } else {
          const [roundsResponse, events] = await Promise.all([
            roundsApi.getAll(assignment.eventId),
            eventsApi.getAll().then((r) => r.data ?? []).catch(() => []),
          ]);
          const isInProgress =
            events.find((e) => e.eventId === assignment.eventId)?.status === "IN_PROGRESS";
          setEventInProgress(isInProgress);

          const assignedRoundIds = new Set(assignment.teams.map((team) => team.roundId));
          const activeRound = roundsResponse.data
            .filter((round) => assignedRoundIds.has(round.roundId))
            .filter((round) => ["ACTIVE", "OPEN"].includes((round.status ?? "").toUpperCase()))
            .sort((a, b) => a.orderNumber - b.orderNumber)[0];

          setTarget(isInProgress && activeRound
            ? {
                ownerRole: timerRole,
                eventId: assignment.eventId,
                roundId: activeRound.roundId,
                roundName: activeRound.name,
              }
            : null);
        }
      }
    } catch {
      setTarget(null);
      setEventInProgress(false);
      setContextFailed(true);
    } finally {
      setContextLoading(false);
    }
  }, [teamId, timerRole]);

  useEffect(() => {
    setTarget(null);
    setEventInProgress(false);
    setContextLoading(Boolean(timerRole));
    void loadContext();

    if (!timerRole) return;
    const intervalId = window.setInterval(() => void loadContext(), CONTEXT_RESYNC_MS);
    const syncWhenVisible = () => {
      if (document.visibilityState === "visible") void loadContext();
    };
    window.addEventListener("focus", syncWhenVisible);
    document.addEventListener("visibilitychange", syncWhenVisible);
    return () => {
      window.clearInterval(intervalId);
      window.removeEventListener("focus", syncWhenVisible);
      document.removeEventListener("visibilitychange", syncWhenVisible);
    };
  }, [loadContext, timerRole]);

  const visibleTarget = target?.ownerRole === timerRole ? target : null;
  const timer = useRoundTimer(
    visibleTarget?.eventId ?? null,
    visibleTarget?.roundId ?? null,
    phase,
    { enabled: Boolean(timerRole && visibleTarget), fireBanners: false },
  );

  if (!timerRole) return null;
  // Badge stays hidden unless the owner's event is currently IN_PROGRESS.
  if (!eventInProgress) return null;

  const failed = contextFailed || timer.loadFailed;
  const loading = contextLoading || timer.loading;
  const presentation = statusPresentation(
    timer.status,
    timer.remainingSeconds,
    loading,
    failed,
    Boolean(visibleTarget),
  );
  const timeText = loading ? "--:--:--" : formatRemaining(timer.remainingSeconds);
  const phaseLabel = phase;
  const roundLabel = visibleTarget?.roundName ?? "No active round";
  const title = `${phaseLabel} · ${roundLabel} · ${presentation.label}`;

  return (
    <div
      className="navbar-role-timer"
      title={title}
      aria-label={`${title}. Remaining time ${timeText}`}
      style={{
        border: `1px solid color-mix(in srgb, ${presentation.color} 35%, transparent)`,
        background: `color-mix(in srgb, ${presentation.color} 5%, transparent)`,
        boxShadow: presentation.glow,
      }}
    >
      <svg
        width="15"
        height="15"
        viewBox="0 0 24 24"
        fill="none"
        stroke={presentation.color}
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        style={{ flexShrink: 0 }}
        aria-hidden="true"
      >
        <circle cx="12" cy="13" r="8" />
        <path d="M12 9v4l2.5 1.5M9 2h6M12 2v3" />
      </svg>

      <span
        className="navbar-role-timer__time"
        style={{ color: presentation.color, fontFamily: mono }}
      >
        {timeText}
      </span>
    </div>
  );
}
