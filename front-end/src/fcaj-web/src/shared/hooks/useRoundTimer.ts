import { useCallback, useEffect, useRef, useState } from "react";
import { timersApi, type RoundTimerState, type TimerPhase, type TimerStatus } from "@/shared/apiClient";
import { useNotifications } from "@/app/providers/NotificationProvider";

// Keep coordinator actions (especially PAUSE/STOP) visible quickly. The backend
// remains authoritative for writes, so even this short UI delay cannot bypass it.
const RESYNC_MS = 5_000;
const DEFAULT_MILESTONES = [30, 15, 5, 1];

export interface RoundTimerView {
  status: TimerStatus;
  remainingSeconds: number;
  durationSeconds: number;
  endsAt: string | null;
  isConfigured: boolean;
  isRunning: boolean;
  isPaused: boolean;
  isExpired: boolean;
  loading: boolean;
  loadFailed: boolean;
  refetch: () => void;
}

interface UseRoundTimerOptions {
  fireBanners?: boolean;
  enabled?: boolean;
}

function phaseWord(phase: TimerPhase): string {
  return phase === "JUDGING" ? "scoring" : "submission";
}

export function useRoundTimer(
  eventId: number | null | undefined,
  roundId: number | null | undefined,
  phase: TimerPhase,
  options: UseRoundTimerOptions = {},
): RoundTimerView {
  const { addToast } = useNotifications();
  const fireBanners = options.fireBanners ?? false;
  const enabled = (options.enabled ?? true) && eventId != null && roundId != null;

  const [state, setState] = useState<RoundTimerState | null>(null);
  const [loading, setLoading] = useState<boolean>(enabled);
  const [loadFailed, setLoadFailed] = useState(false);
  const [remaining, setRemaining] = useState<number>(0);

  const skewRef = useRef(0);
  const stateRef = useRef<RoundTimerState | null>(null);
  const firedRef = useRef<Set<string>>(new Set());
  const runKeyRef = useRef<string>("");
  const prevRemainingRef = useRef<number | null>(null);

  const applyState = useCallback((nextState: RoundTimerState) => {
    skewRef.current = (nextState.serverNow ? Date.parse(nextState.serverNow) : Date.now()) - Date.now();

    const runKey = `${nextState.status}|${nextState.startedAt ?? ""}`;
    if (runKey !== runKeyRef.current) {
      runKeyRef.current = runKey;
      firedRef.current = new Set();
      prevRemainingRef.current = null;
    }

    stateRef.current = nextState;
    setState(nextState);
    setLoadFailed(false);
  }, []);

  const clearState = useCallback(() => {
    stateRef.current = null;
    setState(null);
    setRemaining(0);
    firedRef.current = new Set();
    prevRemainingRef.current = null;
  }, []);

  const refetch = useCallback(() => {
    if (!enabled) return;

    timersApi.get(eventId as number, roundId as number, phase)
      .then((response) => applyState(response.data))
      .catch(() => {
        clearState();
        setLoadFailed(true);
      });
  }, [enabled, eventId, roundId, phase, applyState, clearState]);

  useEffect(() => {
    if (!enabled) {
      clearState();
      setLoadFailed(false);
      setLoading(false);
      return;
    }

    let active = true;
    clearState();
    setLoadFailed(false);
    setLoading(true);

    timersApi.get(eventId as number, roundId as number, phase)
      .then((response) => {
        if (active) applyState(response.data);
      })
      .catch(() => {
        if (active) {
          clearState();
          setLoadFailed(true);
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    const intervalId = window.setInterval(() => {
      timersApi.get(eventId as number, roundId as number, phase)
        .then((response) => {
          if (active) applyState(response.data);
        })
        .catch(() => {
          if (active) {
            clearState();
            setLoadFailed(true);
          }
        });
    }, RESYNC_MS);

    return () => {
      active = false;
      window.clearInterval(intervalId);
    };
  }, [enabled, eventId, roundId, phase, applyState, clearState]);

  useEffect(() => {
    if (!enabled) return;

    const tick = () => {
      const currentState = stateRef.current;

      if (!currentState) {
        setRemaining(0);
        return;
      }

      let nextRemaining: number;

      if (currentState.status === "RUNNING" && currentState.endsAt) {
        nextRemaining = Math.max(
          0,
          Math.round((Date.parse(currentState.endsAt) - (Date.now() + skewRef.current)) / 1000),
        );
      } else if (currentState.status === "PAUSED") {
        nextRemaining = Math.max(0, currentState.remainingSeconds ?? 0);
      } else {
        nextRemaining = 0;
      }

      setRemaining(nextRemaining);

      if (fireBanners && currentState.status === "RUNNING") {
        const previousRemaining = prevRemainingRef.current;

        if (previousRemaining != null) {
          const duration = currentState.durationSeconds ?? 0;
          const minutes = currentState.milestoneMinutes?.length
            ? currentState.milestoneMinutes
            : DEFAULT_MILESTONES;
          const word = phaseWord(phase);

          const marks: Array<{
            key: string;
            seconds: number;
            title: string;
            message: string;
            type: "info" | "warning";
          }> = [];

          for (const minute of minutes) {
            const seconds = minute * 60;
            if (seconds < duration) {
              marks.push({
                key: `REM_${minute}`,
                seconds,
                title: `${minute} min left`,
                message: `${minute} minutes left in the ${word} window.`,
                type: minute <= 5 ? "warning" : "info",
              });
            }
          }

          if ((currentState.notifyAtHalf ?? true) && duration > 0) {
            marks.push({
              key: "HALF",
              seconds: Math.floor(duration / 2),
              title: "Halfway point",
              message: `Half of the ${word} time has elapsed.`,
              type: "info",
            });
          }

          marks.push({
            key: "EXPIRED",
            seconds: 0,
            title: "Time's up",
            message: `The ${word} window has closed.`,
            type: "warning",
          });

          for (const mark of marks) {
            if (
              previousRemaining > mark.seconds
              && nextRemaining <= mark.seconds
              && !firedRef.current.has(mark.key)
            ) {
              firedRef.current.add(mark.key);
              addToast({ type: mark.type, title: mark.title, message: mark.message });
            }
          }
        }
      }

      prevRemainingRef.current = nextRemaining;
    };

    tick();
    const intervalId = window.setInterval(tick, 1000);
    return () => window.clearInterval(intervalId);
  }, [enabled, state, fireBanners, phase, addToast]);

  useEffect(() => {
    if (!enabled) return;
    const syncWhenVisible = () => {
      if (document.visibilityState === "visible") refetch();
    };
    window.addEventListener("focus", syncWhenVisible);
    document.addEventListener("visibilitychange", syncWhenVisible);
    return () => {
      window.removeEventListener("focus", syncWhenVisible);
      document.removeEventListener("visibilitychange", syncWhenVisible);
    };
  }, [enabled, refetch]);

  const stateMatchesSelection = state?.roundId === roundId && state?.phase === phase;
  const visibleState = stateMatchesSelection ? state : null;
  const status: TimerStatus = visibleState?.status ?? "IDLE";
  const isRunning = status === "RUNNING" && remaining > 0;

  return {
    status,
    remainingSeconds: remaining,
    durationSeconds: visibleState?.durationSeconds ?? 0,
    endsAt: visibleState?.endsAt ?? null,
    isConfigured: status !== "IDLE",
    isRunning,
    isPaused: status === "PAUSED",
    isExpired: status === "EXPIRED" || (status === "RUNNING" && remaining <= 0),
    loading: loading || (enabled && !stateMatchesSelection && !loadFailed),
    loadFailed,
    refetch,
  };
}
