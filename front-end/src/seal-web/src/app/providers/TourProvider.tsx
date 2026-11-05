import { createContext, useContext, useState, useCallback, ReactNode } from "react";
import { OnboardingTour } from "@/shared/components/OnboardingTour";

interface TourContextType {
  /** Open the onboarding tour immediately (used by the "How it works" button). */
  openTour: () => void;
  /** Open the tour only if the user hasn't completed it before. Returns true if it opened. */
  maybeAutoStartTour: () => boolean;
}

const TourContext = createContext<TourContextType | null>(null);

export function useTour() {
  const ctx = useContext(TourContext);
  if (!ctx) throw new Error("useTour must be used within TourProvider");
  return ctx;
}

// Persisted across logins — the no-team onboarding tour auto-shows only once, ever.
const DONE_KEY = "sealOnboardingDone";

export function TourProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false);

  const openTour = useCallback(() => setOpen(true), []);

  const maybeAutoStartTour = useCallback(() => {
    if (localStorage.getItem(DONE_KEY)) return false;
    setOpen(true);
    return true;
  }, []);

  // Finishing or skipping marks the tour done so it never auto-shows again.
  const close = useCallback(() => {
    localStorage.setItem(DONE_KEY, "1");
    setOpen(false);
  }, []);

  return (
    <TourContext.Provider value={{ openTour, maybeAutoStartTour }}>
      {children}
      <OnboardingTour open={open} onClose={close} />
    </TourContext.Provider>
  );
}
