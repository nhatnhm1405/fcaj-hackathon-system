import { createContext, useContext, useState, useEffect, useCallback, useRef, ReactNode } from "react";
import { useAuth } from "@/app/providers/AuthProvider";
import { useTour } from "@/app/providers/TourProvider";
import { CompetitionRulesModal } from "@/shared/components/CompetitionRulesModal";
import { RoleGuideModal } from "@/shared/components/RoleGuideModal";

interface RulesContextType {
  openRules: () => void;
  closeRules: () => void;
  // Label for the footer link — varies by role (rules vs role guide).
  rulesLinkLabel: string;
}

const RulesContext = createContext<RulesContextType | null>(null);

export function useRules() {
  const ctx = useContext(RulesContext);
  if (!ctx) throw new Error("useRules must be used within RulesProvider");
  return ctx;
}

// Which roles get an auto-shown popup, and what the footer link is called.
const ROLE_GUIDE_LABEL: Record<string, string> = {
  PARTICIPANT: "Competition Rules",
  MENTOR: "Mentor Guide",
  JUDGE: "Judge Guide",
};

// Persisted flag → the popup auto-shows once, EVER (not every login), keyed by
// user + role so each account sees each of its role guides once on this browser.
// Re-openable anytime from the footer link.
const seenKey = (userId: number, role: string) => `sealRulesSeen:${userId}:${role}`;

export function RulesProvider({ children }: { children: ReactNode }) {
  const { currentUser, availableRoles, activeRole, isLoading } = useAuth();
  const { maybeAutoStartTour } = useTour();
  const [open, setOpen] = useState(false);
  // True only while the currently-open popup was auto-shown on login (not the
  // footer link) — used to hand off to the onboarding tour on close.
  const autoShownRef = useRef(false);

  const role = currentUser?.role ?? "";
  const hasGuide = role in ROLE_GUIDE_LABEL;
  // A multi-role staff member (e.g. an FPT Lecturer with Judge + Mentor) has not
  // committed to a role until they pick one on the Select Role screen. While
  // uncommitted, currentUser.role is a transient default (JUDGE / PARTICIPANT),
  // so suppress the auto-guide until they actually choose — this stops the guide
  // (and the participant rules) from popping on every role switch. Each chosen
  // role still shows its own guide exactly once per login (keyed in sessionStorage).
  const awaitingRoleChoice = availableRoles.length > 1 && activeRole === null;

  const openRules = useCallback(() => setOpen(true), []);

  const closeRules = useCallback(() => {
    setOpen(false);
    if (autoShownRef.current) {
      autoShownRef.current = false;
      // First-time participants with no team flow straight into the onboarding tour.
      if (currentUser?.role === "PARTICIPANT" && currentUser.team_id === null) {
        maybeAutoStartTour();
      }
    }
  }, [currentUser, maybeAutoStartTour]);

  // Auto-open the guide ONCE, ever, for each role that has one. The flag is
  // persisted in localStorage (per user+role), so logging out and back in does
  // NOT re-pop it — the footer link is there to re-open on demand.
  useEffect(() => {
    // While the session is still being restored from a stored token, currentUser
    // is transiently null on every page reload — wait for auth to settle.
    if (isLoading) return;
    if (!currentUser) return;
    if (awaitingRoleChoice) return;
    const key = seenKey(currentUser.user_id, role);
    if (hasGuide && !localStorage.getItem(key)) {
      localStorage.setItem(key, "1");
      autoShownRef.current = true;
      setOpen(true);
    }
  }, [currentUser, role, hasGuide, awaitingRoleChoice, isLoading]);

  return (
    <RulesContext.Provider value={{ openRules, closeRules, rulesLinkLabel: ROLE_GUIDE_LABEL[role] ?? "Competition Rules" }}>
      {children}
      {role === "MENTOR" ? <RoleGuideModal role="MENTOR" open={open} onClose={closeRules} />
        : role === "JUDGE" ? <RoleGuideModal role="JUDGE" open={open} onClose={closeRules} />
        : <CompetitionRulesModal open={open} onClose={closeRules} />}
    </RulesContext.Provider>
  );
}
