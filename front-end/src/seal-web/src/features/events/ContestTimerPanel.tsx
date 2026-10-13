import { useState } from "react";
import { C, PixelButton, PixelCard } from "@/shared/components/PixelComponents";
import { ConfirmDialog } from "@/shared/components/ConfirmDialog";
import { CountdownDisplay } from "@/shared/components/CountdownDisplay";
import { WheelTimePicker } from "@/shared/components/WheelTimePicker";
import { useRoundTimer } from "@/shared/hooks/useRoundTimer";
import { timersApi, apiErrorMessage, type TimerPhase, type RoundTimerState } from "@/shared/apiClient";
import { useNotifications } from "@/app/providers/NotificationProvider";

// Coordinator control for a round's two countdowns:
//   CONTEST → submission window (participants)   ·   JUDGING → scoring window (judges)
// Option B: each is configured + started here, independently, AFTER the đề is
// released. Time is server-authoritative; this panel just drives the API and
// reflects the live state from useRoundTimer.
const MONO = "'JetBrains Mono', monospace";
const MIN_DURATION = 30; // mirrors backend MIN_DURATION_SECONDS

const PHASES: { phase: TimerPhase; title: string }[] = [
  { phase: "CONTEST", title: "Contest — Submission window" },
  { phase: "JUDGING", title: "Judging — Scoring window" },
];

export function ContestTimerPanel({ eventId, roundId, eventStatus }: { eventId: number; roundId: number | null; eventStatus: string }) {
  if (roundId == null) {
    return (
      <div style={{ color: C.textMuted, fontFamily: MONO, fontSize: 12 }}>
        Select a round above to configure its timers.
      </div>
    );
  }
  const eventInProgress = eventStatus === "IN_PROGRESS";
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
      {!eventInProgress && (
        <div style={{ padding: 12, background: C.surface, border: `1px dashed ${C.border}`, color: C.textMuted, fontFamily: MONO, fontSize: 11, lineHeight: 1.6 }}>
          {`Timers can only be operated while the event is IN_PROGRESS. Current: ${eventStatus}.`}
        </div>
      )}
      {PHASES.map(p => (
        <PhaseTimerControl key={p.phase} eventId={eventId} roundId={roundId} phase={p.phase} title={p.title} disabled={!eventInProgress} />
      ))}
    </div>
  );
}

function PhaseTimerControl({
  eventId, roundId, phase, title, disabled,
}: {
  eventId: number;
  roundId: number;
  phase: TimerPhase;
  title: string;
  disabled: boolean;
}) {
  const { addToast } = useNotifications();
  const timer = useRoundTimer(eventId, roundId, phase, { fireBanners: false });
  const contestGate = useRoundTimer(eventId, roundId, "CONTEST", {
    fireBanners: false,
    enabled: phase === "JUDGING",
  });
  const [durationSec, setDurationSec] = useState(30 * 60);
  const [extendMin, setExtendMin] = useState(5);
  const [showExtend, setShowExtend] = useState(false);
  const [busy, setBusy] = useState(false);
  const [confirmStop, setConfirmStop] = useState(false);

  const idle = !timer.isConfigured || timer.status === "STOPPED" || timer.status === "EXPIRED";
  const contestFinished = contestGate.status === "STOPPED" || contestGate.status === "EXPIRED";
  const startBlocked = disabled
    || timer.loading
    || timer.loadFailed
    || (phase === "JUDGING" && (contestGate.loading || contestGate.loadFailed || !contestFinished));

  async function run(action: () => Promise<{ data: RoundTimerState }>, okTitle: string, okMsg: string) {
    setBusy(true);
    try {
      await action();
      timer.refetch();
      addToast({ type: "success", title: okTitle, message: okMsg });
    } catch (err) {
      addToast({ type: "warning", title: "TIMER ACTION FAILED", message: apiErrorMessage(err, "Could not update the timer.") });
    } finally {
      setBusy(false);
    }
  }

  const start = () =>
    run(() => timersApi.start(eventId, roundId, phase, { durationSeconds: durationSec }), "TIMER STARTED", `${title} is now running.`);
  const pause = () => run(() => timersApi.pause(eventId, roundId, phase), "TIMER PAUSED", "The countdown is frozen.");
  const resume = () => run(() => timersApi.resume(eventId, roundId, phase), "TIMER RESUMED", "The countdown is running again.");
  const stop = async () => {
    await run(() => timersApi.stop(eventId, roundId, phase), "TIMER STOPPED", "The window is now closed.");
    setConfirmStop(false);
  };
  const extend = (sec: number) =>
    run(() => timersApi.extend(eventId, roundId, phase, sec), "TIME EXTENDED", `Added ${Math.round(sec / 60)} min.`);

  return (
    <PixelCard style={{ padding: 16 }}>
      <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
        {/* Header: title + live read-out */}
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
          <div style={{ color: C.text, fontFamily: MONO, fontSize: 15, fontWeight: 700 }}>{title}</div>
          <CountdownDisplay remainingSeconds={timer.remainingSeconds} status={timer.status} size="lg" />
        </div>

        {/* Controls */}
        {idle ? (
          // Compact drum picker (hours/min, 3 rows) centered in the card.
          <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 10 }}>
            <WheelTimePicker valueSeconds={durationSec} onChange={setDurationSec} maxHours={99} disabled={busy || startBlocked} />
            {phase === "JUDGING" && !contestGate.loading && !contestGate.loadFailed && !contestFinished && (
              <div style={{ color: C.textMuted, fontFamily: MONO, fontSize: 11, textAlign: "center" }}>
                Finish the CONTEST timer before starting JUDGING.
              </div>
            )}
            {(timer.loadFailed || (phase === "JUDGING" && contestGate.loadFailed)) && (
              <div style={{ color: C.red, fontFamily: MONO, fontSize: 11, textAlign: "center" }}>
                Timer state could not be verified. Controls are locked.
              </div>
            )}
            <PixelButton variant="cyber" disabled={busy || startBlocked || durationSec < MIN_DURATION} onClick={start}>
              START
            </PixelButton>
          </div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            {/* One button per job: PAUSE/RESUME · EXTEND · STOP. */}
            <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
              {timer.status === "PAUSED" ? (
                <PixelButton size="sm" variant="cyber" disabled={disabled || busy || timer.loadFailed} onClick={resume}>RESUME</PixelButton>
              ) : (
                <PixelButton size="sm" variant="secondary" disabled={disabled || busy || timer.loadFailed} onClick={pause}>PAUSE</PixelButton>
              )}
              <PixelButton size="sm" variant="secondary" disabled={disabled || busy || timer.loadFailed} onClick={() => setShowExtend(v => !v)}>EXTEND</PixelButton>
              {/* STOP ends the window for everyone at once — confirmed first. */}
              <PixelButton size="sm" variant="danger" disabled={disabled || busy || timer.loadFailed} onClick={() => setConfirmStop(true)}>STOP</PixelButton>
            </div>
            {showExtend && (
              <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
                <label style={{ display: "flex", alignItems: "center", gap: 8, color: C.textMuted, fontFamily: MONO, fontSize: 11 }}>
                  Add
                  <input
                    type="number"
                    min={1}
                    max={720}
                    value={extendMin}
                    disabled={busy}
                    onFocus={(e) => e.currentTarget.select()}
                    onChange={(e) => setExtendMin(Math.min(720, Math.max(1, parseInt(e.target.value, 10) || 1)))}
                    style={{ width: 64, background: C.surface2, border: `1px solid ${C.border}`, color: C.text, fontFamily: MONO, fontSize: 13, padding: "5px 6px", borderRadius: 0, outline: "none", textAlign: "center" }}
                  />
                  min
                </label>
                <PixelButton size="sm" variant="cyber" disabled={disabled || busy}
                  onClick={() => { extend(extendMin * 60); setShowExtend(false); }}>
                  ADD TIME
                </PixelButton>
              </div>
            )}
          </div>
        )}
      </div>

      {confirmStop && (
        <ConfirmDialog
          title="Stop this timer?"
          message={`"${title}" ends immediately for everyone.`}
          warning="A stopped timer cannot be resumed — you would have to start a new countdown."
          confirmLabel="STOP TIMER"
          variant="danger"
          working={busy}
          onConfirm={stop}
          onClose={() => { if (!busy) setConfirmStop(false); }}
        />
      )}
    </PixelCard>
  );
}
