import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import {
  C, GradientText, PixelCard, PixelButton,
} from "@/shared/components/PixelComponents";
import { adminApi, eventsApi, ApiError, UserItem, RoleGrantItem } from "@/shared/apiClient";
import { EventRow, normalizeEvent, pickDefaultEvent, EventName, EventDateBadge, eventStatusBadge } from "@/features/events/eventUtils";
import { roleBadge } from "@/features/users/roleUtils";

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

  const stats = [
    { label: "Total Accounts",  value: total,    color: C.text },
    { label: "isActive",        value: isActive, color: "#06b6d4" },
    { label: "Staff Accounts",  value: staff,    color: "#c4b5fd" },
  ];

  return (
    <div style={{ padding: 24, display: "flex", flexDirection: "column", gap: 20 }}>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <div style={{ color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 15, fontWeight: 700, marginBottom: 4 }}>
            System Administration
          </div>
          <h1 style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 28, fontWeight: 800 }}>
            <GradientText>Platform Console</GradientText>
          </h1>
        </div>
      </div>

      {error && (
        <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, padding: "10px 14px" }}>
          ERROR: {error}
        </div>
      )}

      {/* Stat cards — Current Event carries a name + status + date, so it gets
          a wider tile (see .admin-stat-grid) instead of being squeezed to fit
          the same column width as the plain number tiles beside it. */}
      <div className="admin-stat-grid">
        <div className="event-card-grid-bg" style={{ backgroundColor: C.surface, border: `1px solid ${C.border}`, padding: "16px 18px", fontFamily: "'JetBrains Mono', monospace", display: "flex", flexDirection: "column", gap: 10, position: "relative", overflow: "hidden" }}>
          {loading ? (
            <div style={{ color: C.textMuted, fontSize: 18, fontWeight: 700 }}>…</div>
          ) : currentEvent ? (
            <>
              <EventName size={18}>{currentEvent.name}</EventName>
              <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                {eventStatusBadge(currentEvent.status)}
                <EventDateBadge ev={currentEvent} />
              </div>
            </>
          ) : (
            <div style={{ color: C.textMuted, fontSize: 18, fontWeight: 700 }}>—</div>
          )}
          <div style={{ color: C.textMuted, fontSize: 10, textTransform: "uppercase", letterSpacing: "0.08em", marginTop: "auto" }}>Current Event</div>
        </div>
        {stats.map(s => (
          <div key={s.label} style={{ background: C.surface, border: `1px solid ${C.border}`, padding: "16px 18px", fontFamily: "'JetBrains Mono', monospace", display: "flex", flexDirection: "column", justifyContent: "space-between" }}>
            <div style={{ color: s.color, fontSize: 28, fontWeight: 700 }}>{loading ? "…" : s.value}</div>
            <div style={{ color: C.textMuted, fontSize: 10, textTransform: "uppercase", letterSpacing: "0.08em", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Role grant distribution */}
        <PixelCard className="p-5">
          <div style={{ color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 15, fontWeight: 700, marginBottom: 16 }}>
            Role Grants
          </div>
          {loading ? (
            <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>Loading…</div>
          ) : Object.keys(grantsByRole).length === 0 ? (
            <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>No role grants yet.</div>
          ) : (
            <div className="flex flex-col gap-3">
              {Object.entries(grantsByRole).map(([role, count]) => (
                <div key={role} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", paddingBottom: 8, borderBottom: `1px solid ${C.border}` }}>
                  {roleBadge(role)}
                  <span style={{ color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 14, fontWeight: 700 }}>{count}</span>
                </div>
              ))}
            </div>
          )}
        </PixelCard>

        {/* Quick actions */}
        <PixelCard className="p-5">
          <div style={{ color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 15, fontWeight: 700, marginBottom: 16 }}>
            Admin Actions
          </div>
          <div className="flex flex-col gap-3">
            <PixelButton variant="primary" size="sm" className="justify-start" onClick={() => navigate("/admin/accounts")}>
              Manage Accounts
            </PixelButton>
            <PixelButton variant="secondary" size="sm" className="justify-start" onClick={() => navigate("/admin/roles")}>
              Manage Role Grants
            </PixelButton>
            <PixelButton variant="ghost" size="sm" className="justify-start" onClick={() => navigate("/admin/logs")}>
              View System Logs
            </PixelButton>
          </div>
        </PixelCard>
      </div>
    </div>
  );
}
