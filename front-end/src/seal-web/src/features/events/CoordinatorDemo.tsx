import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { useNavigate } from "react-router";
import { useNotifications } from "@/app/providers/NotificationProvider";
import {
  apiErrorMessage, demoPhasesApi, demoWavesApi,
  type DemoPhase, type DemoWave, type DemoWaveEvent,
} from "@/shared/apiClient";
import { PixelMenu, type PixelMenuEntry } from "@/shared/components/PixelMenu";

const DemoEventContext = createContext<number | null>(null);

export function useDemoEventId() {
  return useContext(DemoEventContext);
}

const phaseLabels: Record<DemoPhase, string> = {
  DRAFT: "Draft",
  OPEN: "Open registration",
  SETUP: "Setup",
  CONTEST: "In progress · Contest",
  SCORING: "In progress · Scoring & results",
  COMPLETED: "Completed · Awards",
  CANCELLED: "Cancelled",
};

type Selection = { phase: DemoPhase; eventId: number };

const waveLabels: Record<DemoWave, string> = {
  REGISTRATION: "1. Registration: teams, judges, mentors",
  SUBMISSIONS: "2. Submissions from demo teams",
  SCORES: "3. Scores, except the live one",
};

const describeAdded = (added: Record<string, number>) =>
  Object.entries(added).map(([key, count]) => `${key} ${count}`).join(", ");

export function CoordinatorDemo({ children }: { children: ReactNode }) {
  const [phases, setPhases] = useState<DemoPhase[]>([]);
  const [selection, setSelection] = useState<Selection | null>(null);
  const [working, setWorking] = useState(false);
  const [waveEvents, setWaveEvents] = useState<DemoWaveEvent[]>([]);
  // Bumped after a wave so the page underneath remounts and loads the new data.
  const [refresh, setRefresh] = useState(0);
  const navigate = useNavigate();
  const { addToast } = useNotifications();

  useEffect(() => {
    let cancelled = false;
    demoPhasesApi.getAll()
      .then(result => { if (!cancelled) setPhases(result.data); })
      .catch(() => { /* The demo endpoint is only enabled in local demo environments. */ });
    demoWavesApi.getAll()
      .then(result => { if (!cancelled) setWaveEvents(result.data); })
      .catch(() => { /* Demo waves are only enabled for a demo. */ });
    return () => { cancelled = true; };
  }, []);

  async function runWave(eventId: number, wave: DemoWave, blockedBy: string | null) {
    if (blockedBy) {
      addToast({ type: "warning", title: "NOT READY", message: blockedBy });
      return;
    }
    setWorking(true);
    try {
      const { data } = await demoWavesApi.run(eventId, wave);
      addToast({ type: "success", title: "DEMO DATA ADDED", message: describeAdded(data.added) });
      setRefresh(n => n + 1);
    } catch (error) {
      addToast({ type: "warning", title: "DEMO WAVE FAILED", message: apiErrorMessage(error, "Failed to add the demo data.") });
    } finally {
      try {
        setWaveEvents((await demoWavesApi.getAll()).data);
      } catch { /* keep the last list */ }
      setWorking(false);
    }
  }

  async function open(phase: DemoPhase, fresh = false) {
    setWorking(true);
    try {
      const { data } = await demoPhasesApi.open(phase, fresh);
      setSelection({ phase, eventId: data.eventId });
      const path = phase === "SCORING" ? "/coordinator/scoring"
        : phase === "COMPLETED" ? "/coordinator/prizes" : "/coordinator/events";
      navigate(path);
    } catch (error) {
      addToast({ type: "warning", title: "DEMO UNAVAILABLE", message: apiErrorMessage(error, "Failed to open the demo phase.") });
    } finally {
      setWorking(false);
    }
  }

  const items: PixelMenuEntry[] = phases.map(phase => ({
    label: phaseLabels[phase],
    onClick: () => { void open(phase); },
  }));
  if (selection) {
    items.push("divider", {
      label: "Restart this phase",
      onClick: () => { void open(selection.phase, true); },
    }, {
      label: "Exit demo",
      onClick: () => {
        setSelection(null);
        navigate("/coordinator/events");
      },
    });
  }

  const waveItems: PixelMenuEntry[] = waveEvents.flatMap((event, i) => [
    ...(i > 0 ? ["divider" as const] : []),
    ...event.waves.map(state => ({
      label: `${event.eventName} · ${waveLabels[state.wave]}${state.done ? " ✓" : state.blockedBy ? " (not ready)" : ""}`,
      disabled: state.done,
      onClick: () => { void runWave(event.eventId, state.wave, state.blockedBy); },
    })),
  ]);

  return (
    <>
      <DemoEventContext.Provider key={`${selection?.eventId ?? "regular"}-${refresh}`} value={selection?.eventId ?? null}>
        {children}
      </DemoEventContext.Provider>
      {(items.length > 0 || waveItems.length > 0) && createPortal(
        <div className="demo-phase-control" style={{
          position: "fixed", zIndex: 300,
          right: "max(20px, env(safe-area-inset-right))",
          bottom: "max(20px, env(safe-area-inset-bottom))",
          display: "flex", gap: 8,
        }}>
          {waveItems.length > 0 && (
            <PixelMenu
              items={waveItems}
              label={working ? "Adding demo data…" : "Demo data"}
              ariaLabel="Add demo data"
              triggerVariant="secondary"
              size="md"
              disabled={working}
            />
          )}
          {items.length > 0 && <PixelMenu
            items={items}
            label={working ? "Preparing demo…" : selection ? `Demo · ${selection.phase}` : "Demo phases"}
            ariaLabel="Choose a demo phase"
            triggerVariant="secondary"
            size="md"
            disabled={working}
          />}
        </div>,
        document.body,
      )}
    </>
  );
}
