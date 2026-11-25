import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { useNavigate } from "react-router";
import { useNotifications } from "@/app/providers/NotificationProvider";
import { apiErrorMessage, demoPhasesApi, type DemoPhase } from "@/shared/apiClient";
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

export function CoordinatorDemo({ children }: { children: ReactNode }) {
  const [phases, setPhases] = useState<DemoPhase[]>([]);
  const [selection, setSelection] = useState<Selection | null>(null);
  const [working, setWorking] = useState(false);
  const navigate = useNavigate();
  const { addToast } = useNotifications();

  useEffect(() => {
    let cancelled = false;
    demoPhasesApi.getAll()
      .then(result => { if (!cancelled) setPhases(result.data); })
      .catch(() => { /* The demo endpoint is only enabled in local demo environments. */ });
    return () => { cancelled = true; };
  }, []);

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

  return (
    <>
      <DemoEventContext.Provider key={selection?.eventId ?? "regular"} value={selection?.eventId ?? null}>
        {children}
      </DemoEventContext.Provider>
      {items.length > 0 && createPortal(
        <div className="demo-phase-control" style={{
          position: "fixed", zIndex: 300,
          right: "max(20px, env(safe-area-inset-right))",
          bottom: "max(20px, env(safe-area-inset-bottom))",
        }}>
          <PixelMenu
            items={items}
            label={working ? "Preparing demo…" : selection ? `Demo · ${selection.phase}` : "Demo phases"}
            ariaLabel="Choose a demo phase"
            triggerVariant="secondary"
            size="md"
            disabled={working}
          />
        </div>,
        document.body,
      )}
    </>
  );
}
