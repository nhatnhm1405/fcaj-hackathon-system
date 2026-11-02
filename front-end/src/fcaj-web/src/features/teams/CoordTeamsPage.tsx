import { useEffect, useMemo, useState } from "react";
import { List } from "lucide-react";
import {
  C, GradientText, PixelCard, PixelButton, PixelBadge,
} from "@/shared/components/PixelComponents";
import { apiFetch, ApiError, apiErrorMessage } from "@/shared/apiClient";
import { useNotifications } from "@/app/providers/NotificationProvider";
import { usePendingTeams } from "@/app/providers/PendingTeamsProvider";
import { TeamDetailModal, type TeamInfoRow } from "@/shared/components/TeamDetailModal";

// ── API shapes (tolerate camelCase + snake_case from the backend) ─────
interface ApiTrack {
  id?: number; track_id?: number; trackId?: number;
  name?: string;
}

interface ApiEvent {
  id?: number; event_id?: number; eventId?: number;
  name?: string;
  season?: string;
  year?: number;
  status?: string;
}

interface ApiTeamMember {
  userId?: number; user_id?: number;
  fullName?: string; full_name?: string;
  email?: string;
  memberRole?: string; role?: string; member_role?: string;
  studentId?: string | null;
  userType?: string | null;
  university?: string | null;
  isActive?: boolean | null;
}

interface ApiTeam {
  id?: number; teamId?: number; team_id?: number;
  eventId?: number; event_id?: number;
  trackId?: number; track_id?: number;
  trackName?: string | null; track_name?: string | null;
  eventName?: string | null; event_name?: string | null;
  name?: string; teamName?: string; team_name?: string;
  description?: string | null;
  status?: string;
  disqualifiedReason?: string | null; disqualified_reason?: string | null;
  createdAt?: string | null; created_at?: string | null;
  members?: ApiTeamMember[];
}

// ── Normalized rows the table renders ─────────────────────────────────
interface MemberRow {
  userId: number;
  fullName: string;
  email: string;
  memberRole: 'LEADER' | 'MEMBER';
  studentId: string | null;
  userType: string | null;
  university: string | null;
  isActive: boolean | null;
}

interface TrackRow {
  trackId: number;
  name: string;
}

interface EventRow {
  eventId: number;
  name: string;
  status: string;
}

interface TeamRow {
  teamId: number;
  eventId: number;
  trackId: number;
  name: string;
  description: string | null;
  status: string;
  disqualifiedReason: string | null;
  createdAt: string | null;
  members: MemberRow[];
}

function normalizeEvent(item: ApiEvent): EventRow {
  const name = item.name ?? '';
  const suffix = [item.season, item.year].filter(Boolean).join(' ');
  return {
    eventId: item.id ?? item.eventId ?? item.event_id ?? 0,
    name: name || (suffix ? suffix : `Event ${item.id ?? item.eventId ?? item.event_id ?? ''}`),
    status: (item.status ?? '').toUpperCase(),
  };
}

function normalizeTrack(item: ApiTrack): TrackRow {
  return {
    trackId: item.id ?? item.trackId ?? item.track_id ?? 0,
    name: item.name ?? '',
  };
}

function normalizeMember(item: ApiTeamMember): MemberRow {
  const role = (item.memberRole ?? item.role ?? item.member_role ?? 'MEMBER').toUpperCase();
  return {
    userId: item.userId ?? item.user_id ?? 0,
    fullName: item.fullName ?? item.full_name ?? '',
    email: item.email ?? '',
    memberRole: role === 'LEADER' ? 'LEADER' : 'MEMBER',
    studentId: item.studentId ?? null,
    userType: item.userType ?? null,
    university: item.university ?? null,
    isActive: item.isActive ?? null,
  };
}

function normalizeTeam(item: ApiTeam): TeamRow {
  return {
    teamId: item.id ?? item.teamId ?? item.team_id ?? 0,
    eventId: item.eventId ?? item.event_id ?? 0,
    trackId: item.trackId ?? item.track_id ?? 0,
    name: item.name ?? item.teamName ?? item.team_name ?? '',
    description: item.description ?? null,
    status: (item.status ?? 'PENDING').toUpperCase(),
    disqualifiedReason: item.disqualifiedReason ?? item.disqualified_reason ?? null,
    createdAt: item.createdAt ?? item.created_at ?? null,
    members: (item.members ?? []).map(normalizeMember),
  };
}

// New/pending teams surface first — like a stack, newest arrival on top —
// so the coordinator immediately sees what needs a decision, mirroring the
// Accounts queue's "newest applicant first" ordering.
function sortTeamsForQueue(teams: TeamRow[]): TeamRow[] {
  return [...teams].sort((a, b) => {
    const pendingRank = (t: TeamRow) => (t.status === 'PENDING' ? 0 : 1);
    const rankDiff = pendingRank(a) - pendingRank(b);
    if (rankDiff !== 0) return rankDiff;
    return new Date(b.createdAt ?? 0).getTime() - new Date(a.createdAt ?? 0).getTime();
  });
}

// Info block shown at the top of the team-detail modal: Status/Track/Event/
// Members, plus Description and (if applicable) the disqualification reason.
function teamInfoRows(t: TeamRow, trackNameStr: string, eventName: string): TeamInfoRow[] {
  const rows: TeamInfoRow[] = [
    { label: "Status", value: statusBadge(t.status) },
    { label: "Track", value: trackNameStr },
    { label: "Event", value: eventName },
    { label: "Members", value: t.members.length },
  ];
  if (t.description) rows.push({ label: "Description", value: t.description });
  if (t.status === 'DISQUALIFIED' && t.disqualifiedReason) {
    rows.push({ label: "Reason", value: <span style={{ color: C.red }}>{t.disqualifiedReason}</span> });
  }
  return rows;
}

function statusBadge(status: string) {
  if (status === 'APPROVED') return <PixelBadge color="green">APPROVED</PixelBadge>;
  if (status === 'PENDING') return <PixelBadge color="yellow">PENDING</PixelBadge>;
  if (status === 'REJECTED') return <PixelBadge color="red">REJECTED</PixelBadge>;
  if (status === 'DISQUALIFIED') return <PixelBadge color="red">DISQUALIFIED</PixelBadge>;
  return <PixelBadge color="gray">{status}</PixelBadge>;
}

// ── Approve / Reject / Disqualify confirmation modal ──────────────────
type TeamActionType = 'approve' | 'reject' | 'disqualify';

interface TeamActionConfig {
  accent: string;
  buttonVariant: "primary" | "secondary" | "ghost" | "danger" | "cyber";
  title: string;
  message: React.ReactNode;
  confirmLabel: string;
  // reject/disqualify capture a reason; disqualify persists it to the
  // Team.disqualified_reason column.
  reasonLabel?: string;
  reasonRequired?: boolean;
}

function getTeamActionConfig(type: TeamActionType, team: TeamRow): TeamActionConfig {
  const name = <span style={{ color: C.text, fontWeight: 700 }}>"{team.name}"</span>;
  switch (type) {
    case 'approve':
      return {
        accent: "#22c55e",
        buttonVariant: "cyber",
        title: "Approve this team?",
        message: <>You are about to approve {name}. The team will be able to participate and make submissions.</>,
        confirmLabel: "APPROVE TEAM",
      };
    case 'reject':
      return {
        accent: "#ef4444",
        buttonVariant: "danger",
        title: "Reject this team?",
        message: <>You are about to reject {name}. The team will not be able to participate in the event.</>,
        confirmLabel: "REJECT TEAM",
        reasonLabel: "Reason",
        reasonRequired: true,
      };
    case 'disqualify':
      return {
        accent: "#ef4444",
        buttonVariant: "danger",
        title: "Disqualify this team?",
        message: <>You are about to disqualify {name}. They will be removed from the competition. This reason is logged against the team.</>,
        confirmLabel: "DISQUALIFY TEAM",
        reasonLabel: "Disqualification reason",
        reasonRequired: true,
      };
  }
}

function TeamActionModal({
  team,
  type,
  reason,
  onReasonChange,
  onConfirm,
  onCancel,
  busy,
  error,
}: {
  team: TeamRow;
  type: TeamActionType;
  reason: string;
  onReasonChange: (value: string) => void;
  onConfirm: () => void;
  onCancel: () => void;
  busy: boolean;
  error: string | null;
}) {
  const { accent, buttonVariant, title, message, confirmLabel, reasonLabel, reasonRequired } = getTeamActionConfig(type, team);
  const disabled = busy || (reasonRequired ? reason.trim().length === 0 : false);

  return (
    <>
      <div
        onClick={onCancel}
        style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.65)", zIndex: 1100, backdropFilter: "blur(2px)" }}
      />
      <div style={{
        position: "fixed",
        top: "50%", left: "50%",
        transform: "translate(-50%, -50%)",
        zIndex: 1101,
        width: "min(460px, calc(100vw - 32px))",
        background: C.surface,
        border: `1px solid ${accent}66`,
        boxShadow: `0 0 40px ${accent}22, 0 16px 48px rgba(0,0,0,0.4)`,
        padding: 32,
      }}>
        <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${accent}, transparent)` }} />

        <h2 style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 20, fontWeight: 800, color: C.text, marginBottom: 12, lineHeight: 1.2 }}>
          {title}
        </h2>
        <p style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, lineHeight: 1.8, marginBottom: reasonLabel ? 16 : 24 }}>
          {message}
        </p>

        {reasonLabel && (
          <div style={{ marginBottom: 20 }}>
            <label style={{ display: "block", color: C.greenMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, letterSpacing: "0.1em", textTransform: "uppercase", marginBottom: 8 }}>
              {reasonLabel}{reasonRequired ? " *" : " (optional)"}
            </label>
            <textarea
              value={reason}
              onChange={(e) => onReasonChange(e.target.value)}
              placeholder="Explain the reason…"
              rows={3}
              style={{ width: "100%", boxSizing: "border-box", padding: "10px 12px", background: C.surface2, border: `1px solid ${C.border}`, color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, outline: "none", borderRadius: 0, resize: "vertical" }}
            />
          </div>
        )}

        {error && (
          <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, padding: "8px 12px", marginBottom: 16 }}>
            ERROR: {error}
          </div>
        )}

        <div style={{ display: "flex", gap: 10 }}>
          <PixelButton variant={buttonVariant} disabled={disabled} onClick={onConfirm}>
            {busy ? "WORKING…" : confirmLabel}
          </PixelButton>
          <PixelButton variant="secondary" disabled={busy} onClick={onCancel}>
            CANCEL
          </PixelButton>
        </div>
      </div>
    </>
  );
}

// ── Coordinator removes one member during a live (IN_PROGRESS) competition ──
function RemoveMemberModal({
  team, member, reason, onReasonChange, onConfirm, onCancel, busy, error,
}: {
  team: TeamRow;
  member: MemberRow;
  reason: string;
  onReasonChange: (value: string) => void;
  onConfirm: () => void;
  onCancel: () => void;
  busy: boolean;
  error: string | null;
}) {
  const accent = "#ef4444";
  const disabled = busy || reason.trim().length === 0;
  const message = team.members.length === 1
    ? <>You are about to remove <b style={{ color: C.text }}>{member.fullName}</b> — the only remaining member. The team will be disqualified.</>
    : member.memberRole === 'LEADER'
      ? <>You are about to remove <b style={{ color: C.text }}>{member.fullName}</b>, the team leader. The earliest-joined remaining member will automatically become leader.</>
      : <>You are about to remove <b style={{ color: C.text }}>{member.fullName}</b> from the competition.</>;

  return (
    <>
      <div
        onClick={onCancel}
        style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.65)", zIndex: 1100, backdropFilter: "blur(2px)" }}
      />
      <div style={{
        position: "fixed",
        top: "50%", left: "50%",
        transform: "translate(-50%, -50%)",
        zIndex: 1101,
        width: "min(460px, calc(100vw - 32px))",
        background: C.surface,
        border: `1px solid ${accent}66`,
        boxShadow: `0 0 40px ${accent}22, 0 16px 48px rgba(0,0,0,0.4)`,
        padding: 32,
      }}>
        <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${accent}, transparent)` }} />

        <h2 style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 20, fontWeight: 800, color: C.text, marginBottom: 12, lineHeight: 1.2 }}>
          Remove from competition?
        </h2>
        <p style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, lineHeight: 1.8, marginBottom: 16 }}>
          {message}
        </p>

        <div style={{ marginBottom: 20 }}>
          <label style={{ display: "block", color: C.greenMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, letterSpacing: "0.1em", textTransform: "uppercase", marginBottom: 8 }}>
            Reason *
          </label>
          <textarea
            value={reason}
            onChange={(e) => onReasonChange(e.target.value)}
            placeholder="e.g. Absent at round roll-call…"
            rows={3}
            style={{ width: "100%", boxSizing: "border-box", padding: "10px 12px", background: C.surface2, border: `1px solid ${C.border}`, color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, outline: "none", borderRadius: 0, resize: "vertical" }}
          />
        </div>

        {error && (
          <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, padding: "8px 12px", marginBottom: 16 }}>
            ERROR: {error}
          </div>
        )}

        <div style={{ display: "flex", gap: 10 }}>
          <PixelButton variant="danger" disabled={disabled} onClick={onConfirm}>
            {busy ? "WORKING…" : "REMOVE FROM COMPETITION"}
          </PixelButton>
          <PixelButton variant="secondary" disabled={busy} onClick={onCancel}>
            CANCEL
          </PixelButton>
        </div>
      </div>
    </>
  );
}

export function CoordTeamsPage() {
  const { addToast } = useNotifications();
  // Sidebar's "Teams" badge — refreshed after every approve/reject/disqualify
  // so it stays in sync without a page reload (mirrors PendingAccountsProvider).
  const { refreshPendingCount: refreshPendingTeamsCount } = usePendingTeams();
  const [events, setEvents] = useState<EventRow[]>([]);
  const [eventsLoading, setEventsLoading] = useState(true);
  const [eventsError, setEventsError] = useState<string | null>(null);

  const [selectedEventId, setSelectedEventId] = useState<number>(0);
  const [tracks, setTracks] = useState<TrackRow[]>([]);
  const [teams, setTeams] = useState<TeamRow[]>([]);
  const [teamsLoading, setTeamsLoading] = useState(false);
  const [teamsError, setTeamsError] = useState<string | null>(null);

  const [filterTrack, setFilterTrack] = useState<number>(0);
  const [filterStatus, setFilterStatus] = useState<string>("ALL");
  // The team whose full-detail modal is open (null = closed).
  const [detailTeam, setDetailTeam] = useState<TeamRow | null>(null);

  const [actionError, setActionError] = useState<string | null>(null);
  const [confirmTarget, setConfirmTarget] = useState<{ type: TeamActionType; team: TeamRow } | null>(null);
  const [reasonInput, setReasonInput] = useState("");
  const [actionBusy, setActionBusy] = useState(false);

  // Coordinator removes one member during a live (IN_PROGRESS) competition.
  const [removeMemberTarget, setRemoveMemberTarget] = useState<{ team: TeamRow; member: MemberRow } | null>(null);
  const [removeReason, setRemoveReason] = useState("");
  const [removeBusy, setRemoveBusy] = useState(false);
  const [removeError, setRemoveError] = useState<string | null>(null);

  // Load every event (any status) so the coordinator can browse teams
  // across all of them, not just the currently-active one.
  useEffect(() => {
    setEventsLoading(true);
    setEventsError(null);
    apiFetch<{ data: ApiEvent[] }>('/api/events')
      .then(res => {
        const rows = (res.data ?? []).map(normalizeEvent);
        setEvents(rows);
        if (rows.length > 0) setSelectedEventId(rows[0].eventId);
      })
      .catch(err => {
        setEventsError(err instanceof ApiError ? err.message : "Failed to load events.");
      })
      .finally(() => setEventsLoading(false));
  }, []);

  // Tracks and teams are both scoped per-event on the backend, so refetch
  // whenever the selected event changes.
  useEffect(() => {
    if (!selectedEventId) {
      setTracks([]);
      setTeams([]);
      return;
    }
    setTeamsLoading(true);
    setTeamsError(null);
    setDetailTeam(null);
    setFilterTrack(0);

    apiFetch<{ data: ApiTrack[] }>(`/api/events/${selectedEventId}/tracks`)
      .then(res => setTracks((res.data ?? []).map(normalizeTrack)))
      .catch(() => setTracks([]));

    apiFetch<{ data: ApiTeam[] }>(`/api/teams/event/${selectedEventId}`)
      .then(res => {
        setTeams((res.data ?? []).map(normalizeTeam));
      })
      .catch(err => {
        setTeamsError(err instanceof ApiError ? err.message : "Failed to load teams.");
      })
      .finally(() => setTeamsLoading(false));
  }, [selectedEventId]);

  const selectedEvent = useMemo(
    () => events.find(e => e.eventId === selectedEventId) ?? null,
    [events, selectedEventId],
  );

  const trackName = useMemo(() => {
    const map = new Map<number, string>();
    tracks.forEach(t => map.set(t.trackId, t.name));
    // trackId 0 = no track yet (team awaiting the SETUP-phase random draw).
    return (id: number) => (id ? map.get(id) ?? "—" : "Unassigned");
  }, [tracks]);

  const filtered = sortTeamsForQueue(teams.filter(t => {
    if (filterTrack && t.trackId !== filterTrack) return false;
    if (filterStatus !== "ALL" && t.status !== filterStatus) return false;
    return true;
  }));

  function requestAction(type: TeamActionType, team: TeamRow) {
    setActionError(null);
    setReasonInput("");
    setConfirmTarget({ type, team });
  }

  function closeConfirm() {
    setConfirmTarget(null);
    setReasonInput("");
    setActionError(null);
  }

  async function handleConfirmAction() {
    if (!confirmTarget) return;
    const { type, team } = confirmTarget;
    const id = team.teamId;
    const reason = reasonInput.trim();
    setActionError(null);
    setActionBusy(true);
    try {
      if (type === 'approve') {
        await apiFetch(`/api/teams/${id}/approve`, { method: 'PUT' });
        setTeams(prev => prev.map(t => t.teamId === id ? { ...t, status: 'APPROVED' } : t));
        addToast({ type: 'success', title: 'TEAM APPROVED', message: `"${team.name}" can now participate.` });
      } else if (type === 'reject') {
        await apiFetch(`/api/teams/${id}/reject`, { method: 'PUT', body: JSON.stringify({ reason }) });
        setTeams(prev => prev.map(t => t.teamId === id ? { ...t, status: 'REJECTED' } : t));
        addToast({ type: 'warning', title: 'TEAM REJECTED', message: `"${team.name}" was rejected.` });
      } else {
        // Persisted to Team.disqualified_reason on the backend.
        await apiFetch(`/api/teams/${id}/disqualify`, { method: 'PUT', body: JSON.stringify({ reason }) });
        setTeams(prev => prev.map(t => t.teamId === id ? { ...t, status: 'DISQUALIFIED', disqualifiedReason: reason } : t));
        addToast({ type: 'warning', title: 'TEAM DISQUALIFIED', message: `"${team.name}" was disqualified.` });
      }
      // All actions now live inside the detail modal, and each one moves the
      // team out of the status that made the action available in the first
      // place — so there's nothing left to do in there once it succeeds.
      setDetailTeam(null);
      closeConfirm();
      refreshPendingTeamsCount();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Action failed.");
      addToast({ type: 'warning', title: 'ACTION FAILED', message: apiErrorMessage(err, 'Action failed.') });
    } finally {
      setActionBusy(false);
    }
  }

  function closeRemoveMember() {
    setRemoveMemberTarget(null);
    setRemoveReason("");
    setRemoveError(null);
  }

  async function handleRemoveMember() {
    if (!removeMemberTarget) return;
    const { team, member } = removeMemberTarget;
    setRemoveError(null);
    setRemoveBusy(true);
    try {
      const res = await apiFetch<{ data: ApiTeam }>(
        `/api/teams/${team.teamId}/members/${member.userId}/coordinator-remove`,
        { method: 'PUT', body: JSON.stringify({ reason: removeReason.trim() }) },
      );
      const updated = normalizeTeam(res.data);
      setTeams(prev => prev.map(t => t.teamId === updated.teamId ? updated : t));
      // Nothing left to manage once the last member is gone (team is now
      // disqualified) — otherwise keep the modal open on the refreshed roster
      // so the coordinator can remove another absent member in one sitting.
      setDetailTeam(updated.members.length === 0 ? null : updated);
      addToast({
        type: 'warning',
        title: 'MEMBER REMOVED',
        message: updated.status === 'DISQUALIFIED'
          ? `"${member.fullName}" was removed — "${team.name}" has no remaining members and was disqualified.`
          : `"${member.fullName}" was removed from "${team.name}".`,
      });
      closeRemoveMember();
    } catch (err) {
      setRemoveError(err instanceof ApiError ? err.message : "Failed to remove member.");
      addToast({ type: 'warning', title: 'REMOVE FAILED', message: apiErrorMessage(err, 'Failed to remove member.') });
    } finally {
      setRemoveBusy(false);
    }
  }

  return (
    <div style={{ padding: 24, display: "flex", flexDirection: "column", gap: 20 }}>
      <div>
        <h1 style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 28, fontWeight: 800 }}>
          <GradientText>Teams</GradientText>
        </h1>
      </div>

      {eventsError && (
        <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, padding: "10px 14px" }}>
          ERROR: {eventsError}
        </div>
      )}

      {/* Filters */}
      <PixelCard style={{ padding: 16 }}>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 12 }}>
          <select
            value={selectedEventId}
            onChange={(e) => setSelectedEventId(Number(e.target.value))}
            style={selectStyle}
            disabled={eventsLoading || events.length === 0}
          >
            {eventsLoading && <option value={0}>Loading events…</option>}
            {!eventsLoading && events.length === 0 && <option value={0}>No events</option>}
            {events.map(ev => <option key={ev.eventId} value={ev.eventId}>{ev.name}</option>)}
          </select>
          <select value={filterTrack} onChange={(e) => setFilterTrack(Number(e.target.value))} style={selectStyle}>
            <option value={0}>All Tracks</option>
            {tracks.map(t => <option key={t.trackId} value={t.trackId}>{t.name}</option>)}
          </select>
          <select value={filterStatus} onChange={(e) => setFilterStatus(e.target.value)} style={selectStyle}>
            <option value="ALL">All Status</option>
            <option value="PENDING">Pending</option>
            <option value="APPROVED">Approved</option>
            <option value="REJECTED">Rejected</option>
            <option value="DISQUALIFIED">Disqualified</option>
          </select>
        </div>
      </PixelCard>

      <PixelCard style={{ padding: 0, overflow: "hidden" }}>
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontFamily: "'JetBrains Mono', monospace" }}>
            <thead>
              <tr style={{ background: C.surface2, borderBottom: `1px solid ${C.border}` }}>
                {["Team", "Track", "Leader", "Members", "Status"].map(h => (
                  <th key={h} style={{ color: C.green, fontSize: 10, letterSpacing: "0.12em", textAlign: "left", padding: "12px 14px", fontWeight: 600, textTransform: "uppercase" }}>{h}</th>
                ))}
                <th style={{ width: 48, padding: "12px 14px" }} aria-label="Details" />
              </tr>
            </thead>
            <tbody>
              {teamsLoading && (
                <tr><td colSpan={6} style={{ padding: 20, color: C.textMuted, fontSize: 12, textAlign: "center" }}>Loading…</td></tr>
              )}
              {!teamsLoading && teamsError && (
                <tr><td colSpan={6} style={{ padding: 20, color: C.red, fontSize: 12, textAlign: "center" }}>{teamsError}</td></tr>
              )}
              {!teamsLoading && !teamsError && filtered.length === 0 && (
                <tr><td colSpan={6} style={{ padding: 20, color: C.textMuted, fontSize: 12, textAlign: "center" }}>No teams</td></tr>
              )}
              {!teamsLoading && filtered.map((t, i) => {
                const leader = t.members.find(m => m.memberRole === 'LEADER');
                return (
                  <tr key={t.teamId} onClick={() => setDetailTeam(t)}
                    title="View team & member details"
                    className="row-actionable"
                    style={{ borderBottom: `1px solid rgba(34,197,94,0.06)`, background: i % 2 === 0 ? C.surface : C.surface2, cursor: "pointer" }}>
                    <td style={{ color: C.text, fontSize: 13, padding: "12px 14px" }}>{t.name}</td>
                    <td style={{ color: C.textMuted, fontSize: 12, padding: "12px 14px" }}>{trackName(t.trackId)}</td>
                    <td style={{ color: C.textMuted, fontSize: 12, padding: "12px 14px" }}>{leader?.fullName ?? "—"}</td>
                    <td style={{ color: C.textMuted, fontSize: 12, padding: "12px 14px" }}>{t.members.length}</td>
                    <td style={{ padding: "12px 14px" }}>{statusBadge(t.status)}</td>
                    {/* Approve/Reject/Disqualify all live inside the detail modal now
                        (opened by clicking anywhere on the row) — matching the Mentor
                        "My Tracks" row, which is pure data plus this same hover-reveal
                        details affordance at the far right. */}
                    <td style={{ padding: "8px 12px", textAlign: "right" }}>
                      <span className="row-action" style={{
                        display: "inline-flex", alignItems: "center", justifyContent: "center",
                        width: 30, height: 30, border: `1px solid ${C.green}`,
                        background: "rgba(34,197,94,0.14)", color: C.green,
                      }}>
                        <List size={15} strokeWidth={2.25} />
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </PixelCard>

      {detailTeam && (
        <TeamDetailModal
          open
          teamName={detailTeam.name}
          infoRows={teamInfoRows(detailTeam, trackName(detailTeam.trackId), selectedEvent?.name ?? "—")}
          members={detailTeam.members}
          onClose={() => setDetailTeam(null)}
          renderMemberAction={(m) => {
            if (detailTeam.status !== 'APPROVED' || selectedEvent?.status !== 'IN_PROGRESS') return null;
            const target = detailTeam.members.find(x => x.userId === m.userId);
            if (!target) return null;
            return (
              <button
                type="button"
                onClick={() => setRemoveMemberTarget({ team: detailTeam, member: target })}
                style={{
                  fontFamily: "'JetBrains Mono', monospace", fontSize: 9, letterSpacing: "0.06em", textTransform: "uppercase",
                  color: C.red, background: "transparent", border: "1px solid rgba(239,68,68,0.4)",
                  padding: "4px 8px", cursor: "pointer", borderRadius: 0,
                }}
              >
                Remove
              </button>
            );
          }}
        >
          {detailTeam.status === 'PENDING' && (
            // Reject on the left, Approve on the right — the destructive choice
            // sits furthest from where the eye/cursor naturally lands after
            // reading top-to-bottom, so a quick, confident approve is harder to
            // fat-finger into a reject.
            <div style={{ paddingTop: 14, borderTop: `1px solid ${C.border}`, display: "flex", gap: 8, justifyContent: "flex-end" }}>
              <PixelButton size="sm" variant="danger" onClick={() => requestAction('reject', detailTeam)}>REJECT TEAM</PixelButton>
              <PixelButton size="sm" variant="cyber" onClick={() => requestAction('approve', detailTeam)}>APPROVE TEAM</PixelButton>
            </div>
          )}
          {detailTeam.status === 'APPROVED' && (
            <div style={{ paddingTop: 14, borderTop: `1px solid ${C.border}`, display: "flex", justifyContent: "flex-end" }}>
              <PixelButton size="sm" variant="danger" onClick={() => requestAction('disqualify', detailTeam)}>DISQUALIFY TEAM</PixelButton>
            </div>
          )}
        </TeamDetailModal>
      )}

      {confirmTarget && (
        <TeamActionModal
          team={confirmTarget.team}
          type={confirmTarget.type}
          reason={reasonInput}
          onReasonChange={setReasonInput}
          onConfirm={handleConfirmAction}
          onCancel={closeConfirm}
          busy={actionBusy}
          error={actionError}
        />
      )}

      {removeMemberTarget && (
        <RemoveMemberModal
          team={removeMemberTarget.team}
          member={removeMemberTarget.member}
          reason={removeReason}
          onReasonChange={setRemoveReason}
          onConfirm={handleRemoveMember}
          onCancel={closeRemoveMember}
          busy={removeBusy}
          error={removeError}
        />
      )}
    </div>
  );
}

const selectStyle: React.CSSProperties = {
  padding: "10px 12px",
  background: C.surface2,
  border: `1px solid ${C.border}`,
  color: C.text,
  fontFamily: "'JetBrains Mono', monospace",
  fontSize: 12,
  borderRadius: 0,
  outline: "none",
};
