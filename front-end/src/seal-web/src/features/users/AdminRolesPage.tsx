import { useState, useEffect, useMemo } from "react";
import {
  C, GradientText, PixelCard, PixelButton,
} from "@/shared/components/PixelComponents";
import { PixelMenu } from "@/shared/components/PixelMenu";
import {
  adminApi, eventsApi, ApiError, apiErrorMessage,
  RoleGrantItem, UserItem, HackathonEvent, GrantRolePayload,
} from "@/shared/apiClient";
import { useNotifications } from "@/app/providers/NotificationProvider";
import { roleBadge } from "./roleUtils";

const GRANTABLE_ROLES: GrantRolePayload['roleName'][] = [
  'EVENT_COORDINATOR', 'MENTOR', 'JUDGE', 'SYSTEM_ADMIN',
];

// Distance (ms) from today to an event's date window — 0 while it's running,
// otherwise the gap to whichever edge (start/end) is closer.
function eventDistanceToNow(ev: HackathonEvent, now: number): number {
  const start = new Date(ev.startDate).getTime();
  const end = new Date(ev.endDate).getTime();
  if (Number.isNaN(start) || Number.isNaN(end)) return Infinity;
  if (now >= start && now <= end) return 0;
  return now < start ? start - now : now - end;
}

// The event happening now, or the one whose date window is closest to today.
function pickNearestEvent(events: HackathonEvent[]): HackathonEvent | null {
  const now = Date.now();
  let best: HackathonEvent | null = null;
  let bestDist = Infinity;
  for (const ev of events) {
    const dist = eventDistanceToNow(ev, now);
    if (dist < bestDist) { bestDist = dist; best = ev; }
  }
  return best;
}

const inputStyle: React.CSSProperties = {
  width: "100%", boxSizing: "border-box", padding: "10px 12px",
  background: C.surface2, border: `1px solid ${C.border}`, color: C.text,
  fontFamily: "'JetBrains Mono', monospace", fontSize: 12, outline: "none", borderRadius: 0,
};

const labelStyle: React.CSSProperties = {
  display: "block", color: C.greenMuted, fontFamily: "'JetBrains Mono', monospace",
  fontSize: 11, letterSpacing: "0.1em", textTransform: "uppercase", marginBottom: 6,
};

function ModalShell({ accent, title, children, onCancel }: {
  accent: string; title: string; children: React.ReactNode; onCancel: () => void;
}) {
  return (
    <>
      <div onClick={onCancel} style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.65)", zIndex: 400, backdropFilter: "blur(2px)" }} />
      <div style={{
        position: "fixed", top: "50%", left: "50%", transform: "translate(-50%, -50%)", zIndex: 401,
        width: "min(480px, calc(100vw - 32px))", maxHeight: "calc(100vh - 48px)", overflowY: "auto",
        background: C.surface, border: `1px solid ${accent}66`,
        boxShadow: `0 0 40px ${accent}22, 0 16px 48px rgba(0,0,0,0.4)`, padding: 32,
      }}>
        <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${accent}, transparent)` }} />
        <h2 style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 20, fontWeight: 800, color: C.text, marginBottom: 20, lineHeight: 1.2 }}>
          {title}
        </h2>
        {children}
      </div>
    </>
  );
}

// ── Grant role panel ─────────────────────────────────────────────────
// Always-visible inline card docked beside the table (not a toggled modal) —
// the table is permanently narrower to make room for it.
function GrantRoleForm({ users, events, onGranted }: {
  users: UserItem[]; events: HackathonEvent[]; onGranted: () => void;
}) {
  const { addToast } = useNotifications();
  // Auto-select the event happening now (or nearest to today) for event-scoped
  // roles; the user can still pick any other event from the dropdown.
  const recommendedEventId = useMemo(() => pickNearestEvent(events)?.eventId ?? null, [events]);
  const [userId, setUserId] = useState<number | "">("");
  const [roleName, setRoleName] = useState<GrantRolePayload['roleName']>('EVENT_COORDINATOR');
  const [eventId, setEventId] = useState<number | "">(recommendedEventId ?? "");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // SYSTEM_ADMIN and EVENT_COORDINATOR are system-wide (no event scope).
  const systemWide = roleName === 'SYSTEM_ADMIN' || roleName === 'EVENT_COORDINATOR';

  function resetForm() {
    setUserId("");
    setRoleName('EVENT_COORDINATOR');
    setEventId(recommendedEventId ?? "");
    setError(null);
  }

  async function submit() {
    setError(null);
    if (userId === "") {
      setError("Please select a user.");
      addToast({ type: 'warning', title: 'SELECT USER', message: 'Please select a user.' });
      return;
    }
    if (!systemWide && eventId === "") {
      setError("This role requires an event scope.");
      addToast({ type: 'warning', title: 'EVENT REQUIRED', message: 'This role requires an event scope.' });
      return;
    }
    setSaving(true);
    try {
      await adminApi.grantRole({
        userId: Number(userId),
        roleName,
        eventId: systemWide ? null : Number(eventId),
      });
      addToast({ type: 'success', title: 'ROLE GRANTED', message: `${roleName} granted.` });
      onGranted();
      resetForm();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to grant role.");
      addToast({ type: 'warning', title: 'GRANT FAILED', message: apiErrorMessage(err, 'Failed to grant role.') });
    } finally {
      setSaving(false);
    }
  }

  return (
    <PixelCard glow style={{ padding: 24, position: "sticky", top: 24 }}>
      <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, #22c55e, transparent)` }} />
      <h2 style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 18, fontWeight: 800, color: C.text, marginBottom: 20, lineHeight: 1.2 }}>
        Grant role
      </h2>
      <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
        <div>
          <label style={labelStyle}>User</label>
          <select style={inputStyle} value={userId} onChange={(e) => setUserId(e.target.value === "" ? "" : Number(e.target.value))}>
            <option value="">— select user —</option>
            {users.map(u => (
              <option key={u.userId} value={u.userId}>{u.fullName} ({u.email})</option>
            ))}
          </select>
        </div>
        <div>
          <label style={labelStyle}>Role</label>
          <select style={inputStyle} value={roleName} onChange={(e) => setRoleName(e.target.value as GrantRolePayload['roleName'])}>
            {GRANTABLE_ROLES.map(r => <option key={r} value={r}>{r}</option>)}
          </select>
        </div>
        <div>
          <label style={labelStyle}>Event scope</label>
          <select style={inputStyle} value={systemWide ? "" : eventId} disabled={systemWide}
            onChange={(e) => setEventId(e.target.value === "" ? "" : Number(e.target.value))}>
            <option value="">{systemWide ? "System (no event)" : "— select event —"}</option>
            {!systemWide && events.map(ev => (
              <option key={ev.eventId} value={ev.eventId}>{ev.name}</option>
            ))}
          </select>
        </div>
        {error && (
          <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, padding: "8px 12px" }}>
            ERROR: {error}
          </div>
        )}
        <div style={{ display: "flex", gap: 10, marginTop: 4 }}>
          <PixelButton variant="cyber" onClick={submit} disabled={saving}>
            {saving ? "GRANTING…" : "GRANT ROLE"}
          </PixelButton>
          <PixelButton variant="secondary" onClick={resetForm} disabled={saving}>CLEAR</PixelButton>
        </div>
      </div>
    </PixelCard>
  );
}

// ── Revoke confirm modal ─────────────────────────────────────────────
function RevokeModal({ grant, onClose, onDone }: { grant: RoleGrantItem; onClose: () => void; onDone: () => void }) {
  const { addToast } = useNotifications();
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function confirm() {
    setError(null);
    setSaving(true);
    try {
      await adminApi.revokeRole({
        userId: grant.userId,
        roleName: grant.roleName as GrantRolePayload['roleName'],
        eventId: grant.eventId ?? null,
      });
      addToast({ type: 'info', title: 'ROLE REVOKED', message: `${grant.roleName} revoked from ${grant.userFullName}.` });
      onDone();
      onClose();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to revoke role.");
      addToast({ type: 'warning', title: 'REVOKE FAILED', message: apiErrorMessage(err, 'Failed to revoke role.') });
    } finally {
      setSaving(false);
    }
  }

  return (
    <ModalShell accent="#ef4444" title="Revoke this role?" onCancel={onClose}>
      <p style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, lineHeight: 1.8, marginBottom: 20 }}>
        Revoke <span style={{ color: C.text, fontWeight: 700 }}>{grant.roleName}</span> from{" "}
        <span style={{ color: C.text, fontWeight: 700 }}>{grant.userFullName}</span>
        {grant.eventName ? <> for event <span style={{ color: C.text, fontWeight: 700 }}>{grant.eventName}</span></> : <> (system)</>}?
      </p>
      {error && (
        <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, padding: "8px 12px", marginBottom: 16 }}>
          ERROR: {error}
        </div>
      )}
      <div style={{ display: "flex", gap: 10 }}>
        <PixelButton variant="danger" onClick={confirm} disabled={saving}>
          {saving ? "WORKING…" : "REVOKE ROLE"}
        </PixelButton>
        <PixelButton variant="secondary" onClick={onClose} disabled={saving}>CANCEL</PixelButton>
      </div>
    </ModalShell>
  );
}

export function AdminRolesPage() {
  const [grants, setGrants] = useState<RoleGrantItem[]>([]);
  const [users, setUsers] = useState<UserItem[]>([]);
  const [events, setEvents] = useState<HackathonEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [search, setSearch] = useState("");

  const [revokeTarget, setRevokeTarget] = useState<RoleGrantItem | null>(null);

  function loadGrants() {
    return adminApi.getRoleGrants()
      .then(res => setGrants(res.data ?? []))
      .catch(err => setFetchError(err instanceof ApiError ? err.message : "Failed to load role grants."));
  }

  useEffect(() => {
    setLoading(true);
    setFetchError(null);
    Promise.all([
      adminApi.getRoleGrants().then(res => setGrants(res.data ?? [])),
      adminApi.getUsers().then(res => setUsers(res.data ?? [])).catch(() => setUsers([])),
      eventsApi.getAll().then(res => setEvents(res.data ?? [])).catch(() => setEvents([])),
    ])
      .catch(err => setFetchError(err instanceof ApiError ? err.message : "Failed to load role grants."))
      .finally(() => setLoading(false));
  }, []);

  const query = search.trim().toLowerCase();
  const filtered = query
    ? grants.filter(g =>
        g.userFullName.toLowerCase().includes(query) ||
        g.userEmail.toLowerCase().includes(query) ||
        g.roleName.toLowerCase().includes(query))
    : grants;

  // Group by user → one row per user carrying all their roles in this scope.
  const groupedRows = (() => {
    const map = new Map<number, { userId: number; userFullName: string; userEmail: string; grants: RoleGrantItem[] }>();
    for (const g of filtered) {
      const row = map.get(g.userId);
      if (row) row.grants.push(g);
      else map.set(g.userId, { userId: g.userId, userFullName: g.userFullName, userEmail: g.userEmail, grants: [g] });
    }
    return [...map.values()].sort((a, b) => a.userFullName.localeCompare(b.userFullName));
  })();

  const mono = "'JetBrains Mono', monospace";
  const searchInputStyle: React.CSSProperties = {
    width: 240, padding: "8px 12px", background: C.surface2, border: `1px solid ${C.border}`,
    color: C.text, fontFamily: mono, fontSize: 12, outline: "none", borderRadius: 0, boxSizing: "border-box",
  };

  return (
    <div style={{ padding: 24, display: "flex", flexDirection: "column", gap: 20 }}>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 style={{ fontFamily: mono, fontSize: 28, fontWeight: 800 }}>
          <GradientText>Role Grants</GradientText>
        </h1>
      </div>

      <div style={{ display: "flex", gap: 20, alignItems: "flex-start", flexWrap: "wrap" }}>
        <div style={{ flex: "1 1 480px", minWidth: 0, display: "flex", flexDirection: "column", gap: 12 }}>
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search user or role..."
          style={searchInputStyle}
        />
        <PixelCard style={{ padding: 0, overflow: "hidden" }}>
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontFamily: mono }}>
              <thead>
                <tr style={{ background: C.surface2, borderBottom: `1px solid ${C.border}` }}>
                  {["User", "Roles", "Actions"].map(h => (
                    <th key={h} style={{ color: C.green, fontSize: 10, letterSpacing: "0.12em", textAlign: "left", padding: "12px 14px", fontWeight: 600, textTransform: "uppercase" }}>
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {loading && (
                  <tr><td colSpan={3} style={{ padding: 20, color: C.textMuted, fontSize: 12, textAlign: "center" }}>Loading...</td></tr>
                )}
                {!loading && fetchError && (
                  <tr><td colSpan={3} style={{ padding: 20, color: C.red, fontSize: 12, textAlign: "center" }}>{fetchError}</td></tr>
                )}
                {!loading && !fetchError && groupedRows.length === 0 && (
                  <tr><td colSpan={3} style={{ padding: 20, color: C.textMuted, fontSize: 12, textAlign: "center" }}>No role grants</td></tr>
                )}
                {!loading && groupedRows.map((row, i) => (
                  <tr key={row.userId} className="row-actionable" style={{ borderBottom: `1px solid rgba(34,197,94,0.06)`, background: i % 2 === 0 ? C.surface : C.surface2 }}>
                    <td style={{ padding: "14px", verticalAlign: "top" }}>
                      <div style={{ color: C.text, fontSize: 13, fontWeight: 700 }}>{row.userFullName}</div>
                      <div style={{ color: C.textMuted, fontSize: 11, marginTop: 3 }}>{row.userEmail}</div>
                    </td>
                    <td style={{ padding: "14px", verticalAlign: "top" }}>
                      <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                        {row.grants.map(g => (
                          <div key={g.id} style={{ minHeight: 30, display: "flex", alignItems: "center" }}>
                            {roleBadge(g.roleName)}
                          </div>
                        ))}
                      </div>
                    </td>
                    <td style={{ padding: "14px", verticalAlign: "top", width: 48 }}>
                      <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                        {row.grants.map(g => (
                          <div key={g.id} style={{ minHeight: 30, display: "flex", alignItems: "center" }}>
                            <span className="row-action">
                              <PixelMenu
                                ariaLabel={`Revoke ${g.roleName} from ${row.userFullName}`}
                                items={[{ label: "Revoke", danger: true, onClick: () => setRevokeTarget(g) }]}
                              />
                            </span>
                          </div>
                        ))}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </PixelCard>
        </div>

        <div style={{ flex: "0 0 360px", width: 360, display: "flex", flexDirection: "column", gap: 12 }}>
          {/* Invisible spacer matching the search input's box exactly, so the
              form's top edge lines up with the table's top edge rather than
              the search bar's. */}
          <div aria-hidden style={{ ...searchInputStyle, width: "100%", visibility: "hidden" }}>&nbsp;</div>
          <GrantRoleForm
            users={users}
            events={events}
            onGranted={() => { setLoading(true); loadGrants().finally(() => setLoading(false)); }}
          />
        </div>
      </div>

      {revokeTarget && (
        <RevokeModal
          grant={revokeTarget}
          onClose={() => setRevokeTarget(null)}
          onDone={() => { setLoading(true); loadGrants().finally(() => setLoading(false)); }}
        />
      )}
    </div>
  );
}
