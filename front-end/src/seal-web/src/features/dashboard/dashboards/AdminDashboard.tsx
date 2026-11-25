import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { adminApi, eventsApi, ApiError, UserItem, RoleGrantItem } from "@/shared/apiClient";
import { EventRow, normalizeEvent, pickDefaultEvent } from "@/features/events/eventUtils";
import { ContestCard, DashboardError, DashboardStage, TaskCard, TaskRow } from "@/features/dashboard/components/DashboardUI";
import { eventDateRange, phaseSummary, stepDates } from "@/features/dashboard/components/eventPhase";

const ROLE_LABELS: Record<string, string> = {
  SYSTEM_ADMIN: "Admin", EVENT_COORDINATOR: "Coordinator", MENTOR: "Mentor", JUDGE: "Judge",
};

// Platform-level overview for the System Administrator. Unlike the Coordinator
// dashboard (which is scoped to a single event), this aggregates global account
// and role-grant data straight from the /api/admin endpoints.
export function AdminDashboard() {
  const navigate = useNavigate();
  const [users, setUsers] = useState<UserItem[]>([]);
  const [grants, setGrants] = useState<RoleGrantItem[]>([]);
  const [events, setEvents] = useState<EventRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    Promise.all([adminApi.getUsers(), adminApi.getRoleGrants(), eventsApi.getAll()])
      .then(([u, g, e]) => {
        setUsers(u.data ?? []);
        setGrants(g.data ?? []);
        setEvents((e.data ?? []).map(normalizeEvent));
      })
      .catch(err => setError(err instanceof ApiError ? err.message : "Failed to load admin data."))
      .finally(() => setLoading(false));
  }, []);

  const total    = users.length;
  const isActive = users.filter(u => u.isActive).length;
  const staff    = users.filter(u => u.userType === 'STAFF').length;
  const currentEvent = pickDefaultEvent(events);

  // Count active grants per role name (e.g. EVENT_COORDINATOR, JUDGE, MENTOR).
  const grantsByRole = grants.reduce<Record<string, number>>((acc, g) => {
    acc[g.roleName] = (acc[g.roleName] ?? 0) + 1;
    return acc;
  }, {});

  return (
    <DashboardStage>
      {error && <DashboardError message={error} />}

      <ContestCard
        name={currentEvent?.name ?? (loading ? "Loading…" : "No current contest")}
        status={currentEvent?.status}
        summary={currentEvent ? phaseSummary(currentEvent.status, currentEvent) : undefined}
        dates={currentEvent ? stepDates(currentEvent.status, currentEvent) : undefined}
        facts={currentEvent ? [
          ...(currentEvent.topic ? [{ label: "Topic", value: currentEvent.topic }] : []),
          { label: "Dates", value: eventDateRange(currentEvent), mobileOnly: true },
        ] : []}
      />

      <TaskCard title="Manage the platform">
        <TaskRow
          name="Accounts"
          state={loading ? "Loading…" : `${total} accounts, ${isActive} active, ${staff} staff`}
          primary
          action="Manage accounts"
          onAction={() => navigate("/admin/accounts")}
        />
        <TaskRow
          name="Role grants"
          state={loading ? "Loading…" : grants.length === 0 ? "None yet" : Object.entries(grantsByRole).map(([role, count]) => `${count} ${ROLE_LABELS[role] ?? role}`).join(", ")}
          action="Manage role grants"
          onAction={() => navigate("/admin/roles")}
        />
        <TaskRow name="System logs" state="All system activity" action="View system logs" onAction={() => navigate("/admin/logs")} />
      </TaskCard>
    </DashboardStage>
  );
}
