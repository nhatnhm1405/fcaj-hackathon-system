import { createContext, useContext, useState, useEffect, useCallback, ReactNode } from "react";
import { teamsApi } from "@/shared/apiClient";
import { useAuth } from "@/app/providers/AuthProvider";

// Shared source of truth for the coordinator sidebar's "pending teams" badge.
// CoordTeamsPage and the sidebar both read this so an approve/reject/disqualify
// on the page updates the badge immediately — no page refresh needed. Mirrors
// PendingAccountsProvider, but backed by a cross-event count (GET
// /api/teams/pending-count) since Teams is scoped to one event at a time while
// the badge should reflect pending teams across every event.
interface PendingTeamsContextType {
  pendingCount: number;
  setPendingCount: (n: number) => void;
  refreshPendingCount: () => Promise<void>;
}

const PendingTeamsContext = createContext<PendingTeamsContextType | null>(null);

export function PendingTeamsProvider({ children }: { children: ReactNode }) {
  const { currentUser } = useAuth();
  const role = currentUser?.role;
  const [pendingCount, setPendingCount] = useState(0);

  const refreshPendingCount = useCallback(async () => {
    try {
      const res = await teamsApi.getPendingCount();
      setPendingCount(res.data?.count ?? 0);
    } catch {
      /* keep last known count on failure */
    }
  }, []);

  // Seed the badge on login so it's correct before the Teams page is opened.
  useEffect(() => {
    if (role !== 'COORDINATOR') { setPendingCount(0); return; }
    refreshPendingCount();
  }, [role, refreshPendingCount]);

  return (
    <PendingTeamsContext.Provider value={{ pendingCount, setPendingCount, refreshPendingCount }}>
      {children}
    </PendingTeamsContext.Provider>
  );
}

export function usePendingTeams() {
  const ctx = useContext(PendingTeamsContext);
  if (!ctx) throw new Error('usePendingTeams must be used within PendingTeamsProvider');
  return ctx;
}
