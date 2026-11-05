import { useState, useEffect, useCallback } from "react";
import { useNavigate } from "react-router";
import { useAuth } from "@/app/providers/AuthProvider";
import { useNotifications } from "@/app/providers/NotificationProvider";
import {
  C, GradientText, PixelCard, PixelButton, PixelBadge, PixelInput,
} from "@/shared/components/PixelComponents";
import { PixelMenu, type PixelMenuEntry } from "@/shared/components/PixelMenu";
import { ConfirmDialog } from "@/shared/components/ConfirmDialog";
import { teamsApi, invitesApi, joinRequestsApi, supportApi, teamRejoinRequestsApi, ApiError, apiErrorMessage, MyTeam, MyTeamMember, UserItem, JoinRequest, MentorContact, SupportRequest, SupportCategory, ActiveEventWithTracks } from "@/shared/apiClient";
import { isTeamEditable, teamLockReason, MIN_TEAM_SIZE, MAX_TEAM_SIZE } from "@/shared/teamPhase";

// Support-request categories shown to the team leader.
const SUPPORT_CATEGORIES: { value: SupportCategory; label: string }[] = [
  { value: "RULES", label: "Rules / Regulation" },
  { value: "TECHNICAL", label: "Technical" },
  { value: "DIRECTION", label: "Direction / Idea" },
  { value: "OTHER", label: "Other" },
];
function categoryLabel(c: SupportCategory): string {
  return SUPPORT_CATEGORIES.find(x => x.value === c)?.label ?? c;
}

function fmtDT(iso?: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  return `${String(d.getDate()).padStart(2, "0")}/${String(d.getMonth() + 1).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

function statusBadgeColor(status?: string): "green" | "yellow" | "red" | "gray" {
  const s = (status ?? "").toUpperCase();
  if (s === "APPROVED") return "green";
  if (s === "PENDING") return "yellow";
  if (s === "REJECTED" || s === "DISQUALIFIED") return "red";
  return "gray";
}

function eventStatusBadgeColor(status?: string): "green" | "yellow" | "red" | "gray" {
  const s = (status ?? "").toUpperCase();
  if (s === "OPEN" || s === "SETUP" || s === "IN_PROGRESS") return "green";
  if (s === "DRAFT") return "yellow";
  if (s === "CANCELLED") return "red";
  return "gray";
}

function roundStatusColor(status?: string): "green" | "yellow" | "red" | "gray" {
  const s = (status ?? "").toUpperCase();
  if (["ACTIVE", "OPEN", "IN_PROGRESS"].includes(s)) return "green";
  if (["UPCOMING", "PENDING", "DRAFT"].includes(s)) return "yellow";
  if (["CLOSED", "CANCELLED"].includes(s)) return "red";
  return "gray";
}

export function TeamViewPage() {
  const navigate = useNavigate();
  const { currentUser, refreshTeamContext, clearTeam } = useAuth();
  const { addToast } = useNotifications();

  const [team, setTeam] = useState<MyTeam | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [editingName, setEditingName] = useState(false);
  const [nameInput, setNameInput] = useState("");

  const [teamPanel, setTeamPanel] = useState<"invite" | "requests" | "member" | "mentor" | null>(null);

  // Mentor support: the track's mentor(s) + this team's requests.
  const [mentors, setMentors] = useState<MentorContact[]>([]);
  const [myRequests, setMyRequests] = useState<SupportRequest[]>([]);
  const [reqCategory, setReqCategory] = useState<SupportCategory>("TECHNICAL");
  const [reqDesc, setReqDesc] = useState("");
  const [reqBusy, setReqBusy] = useState(false);
  const [reqError, setReqError] = useState<string | null>(null);
  const [inviteQuery, setInviteQuery] = useState("");
  const [inviteResults, setInviteResults] = useState<UserItem[]>([]);
  const [inviteSearchMessage, setInviteSearchMessage] = useState<string | null>(null);
  const [inviteSendingId, setInviteSendingId] = useState<number | null>(null);
  const [inviteConfirmTarget, setInviteConfirmTarget] = useState<UserItem | null>(null);
  const [searching, setSearching] = useState(false);

  const [transferTarget, setTransferTarget] = useState<MyTeamMember | null>(null);
  const [removeTarget, setRemoveTarget] = useState<MyTeamMember | null>(null);
  const [selectedMemberId, setSelectedMemberId] = useState<number | null>(null);
  const [hoveredActionUserId, setHoveredActionUserId] = useState<number | null>(null);

  const [joinRequests, setJoinRequests] = useState<JoinRequest[]>([]);
  const [busyReq, setBusyReq] = useState<number | null>(null);
  const [confirmLeave, setConfirmLeave] = useState(false);

  // Rejoin: a dormant team's leader asking a coordinator to re-attach the
  // team to a new season (see TeamRejoinRequest on the backend).
  const [activeEvents, setActiveEvents] = useState<ActiveEventWithTracks[]>([]);
  const [rejoinEventId, setRejoinEventId] = useState<number | null>(null);
  const [confirmRejoin, setConfirmRejoin] = useState(false);
  const [requestingRejoin, setRequestingRejoin] = useState(false);
  const [rejoinRequested, setRejoinRequested] = useState(false);
  const [rejoinError, setRejoinError] = useState<string | null>(null);

  const loadJoinRequests = useCallback((teamId: number) => {
    joinRequestsApi.getForTeam(teamId).then(r => setJoinRequests(r.data ?? [])).catch(() => setJoinRequests([]));
  }, []);

  const loadSupport = useCallback(() => {
    supportApi.getMyMentors().then(r => setMentors(r.data ?? [])).catch(() => setMentors([]));
    supportApi.getMine().then(r => setMyRequests(r.data ?? [])).catch(() => setMyRequests([]));
  }, []);

  const applyTeam = useCallback((nextTeam: MyTeam | null) => {
    setTeam(nextTeam);
    if (nextTeam?.myRole === 'LEADER') {
      loadJoinRequests(nextTeam.teamId);
    } else {
      setJoinRequests([]);
    }
    if (nextTeam) loadSupport(); else { setMentors([]); setMyRequests([]); }
  }, [loadJoinRequests, loadSupport]);

  const load = useCallback(() => {
    setLoading(true);
    setLoadError(null);
    teamsApi.getMy()
      .then(res => {
        applyTeam(res.data ?? null);
      })
      .catch(err => {
        if (err instanceof ApiError && err.status === 404) {
          setTeam(null);
          clearTeam();
        } else setLoadError(err instanceof ApiError ? err.message : "Failed to load your team.");
      })
      .finally(() => setLoading(false));
  }, [applyTeam, clearTeam]);

  useEffect(() => { load(); }, [load]);

  const isLeader = team?.myRole === 'LEADER';
  const editable = isTeamEditable(team?.eventStatus);
  const openRequest = myRequests.find(r => r.status === 'OPEN') ?? null;
  const canEditTeam = isLeader && editable;
  const canManageMembers = canEditTeam;
  const canLeaveTeam = editable;
  // Dormant = no live season entry — the same condition the backend gates a
  // rejoin request on (Team.isActive goes false either way).
  const isDormant = team?.status === 'DISQUALIFIED' || team?.eventStatus === 'COMPLETED';
  const lockReason = teamLockReason(team?.eventStatus);

  useEffect(() => {
    if (isLeader && isDormant && !team?.hasPendingRejoinRequest) {
      teamsApi.getActiveEvents().then(r => setActiveEvents(r.data ?? [])).catch(() => setActiveEvents([]));
    }
  }, [isLeader, isDormant, team?.hasPendingRejoinRequest]);

  async function requestRejoin() {
    if (!team || rejoinEventId == null) return;
    setRequestingRejoin(true); setRejoinError(null);
    try {
      await teamRejoinRequestsApi.request(team.teamId, rejoinEventId);
      setRejoinRequested(true);
      setTeam(prev => (prev ? { ...prev, hasPendingRejoinRequest: true } : prev));
      addToast({ type: "success", title: "Rejoin request sent", message: "The coordinator will review your team's request to rejoin." });
    } catch (err) {
      const message = apiErrorMessage(err, "Failed to send rejoin request.");
      setRejoinError(message);
      addToast({ type: "warning", title: "Request failed", message });
    } finally {
      setRequestingRejoin(false);
      setConfirmRejoin(false);
    }
  }

  if (loading) {
    return <div style={{ padding: 24 }}><PixelCard style={{ padding: 32, textAlign: "center" }}>
      <p style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 13 }}>Loading...</p>
    </PixelCard></div>;
  }

  if (!team) {
    return (
      <div style={{ padding: 24 }}>
        <PixelCard style={{ padding: 32, textAlign: "center" }}>
          <p style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, marginBottom: 20 }}>
            {loadError ?? "You are not part of any team yet."}
          </p>
          {!loadError && <PixelButton variant="cyber" onClick={() => navigate('/team/create')}>CREATE A TEAM</PixelButton>}
        </PixelCard>
      </div>
    );
  }

  async function saveName() {
    if (!team || !nameInput.trim() || nameInput.trim() === team.name) { setEditingName(false); return; }
    setBusy(true); setActionError(null);
    try {
      const res = await teamsApi.update(team.teamId, { name: nameInput.trim() });
      setTeam(res.data);
      setEditingName(false);
      addToast({ type: "success", title: "Team renamed", message: `Your team is now "${res.data.name}".` });
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Failed to rename team.");
      addToast({ type: "warning", title: "Rename failed", message: apiErrorMessage(err, "Failed to rename team.") });
    } finally { setBusy(false); }
  }

  async function doSearch() {
    const q = inviteQuery.trim();
    setActionError(null);
    setNotice(null);
    setInviteSearchMessage(null);
    if (q.length < 2) {
      const message = "Enter at least 2 characters to search for an eligible participant.";
      setInviteResults([]);
      setInviteSearchMessage(message);
      addToast({ type: "warning", title: "Search needed", message });
      return;
    }
    setSearching(true);
    try {
      const res = await teamsApi.searchUsers(q);
      const results = res.data ?? [];
      setInviteResults(results);
      if (results.length === 0) {
        const message = "No eligible participant found. The account may be inactive, unapproved, not a student participant, or already unavailable for invitation.";
        setInviteSearchMessage(message);
        addToast({ type: "warning", title: "No eligible participant", message });
      }
    } catch (err) {
      const message = apiErrorMessage(err, "Failed to search participants.");
      setInviteResults([]);
      setInviteSearchMessage(message);
      addToast({ type: "warning", title: "Search failed", message });
    }
    finally { setSearching(false); }
  }

  async function sendInvite(user: UserItem) {
    if (!team) return;
    setActionError(null); setNotice(null); setInviteSearchMessage(null);
    setInviteSendingId(user.userId);
    try {
      await invitesApi.send(team.teamId, { invitedUserId: user.userId });
      setNotice(`Invitation sent to ${user.fullName}. They will appear once they accept.`);
      addToast({ type: "success", title: "Invitation sent", message: `${user.fullName} has been invited to your team.` });
      setInviteQuery(""); setInviteResults([]); setTeamPanel(null);
      setInviteConfirmTarget(null);
    } catch (err) {
      const message = apiErrorMessage(err, "Failed to send invite.");
      setActionError(message);
      setInviteSearchMessage(message);
      addToast({ type: "warning", title: "Invite failed", message });
    } finally {
      setInviteSendingId(null);
    }
  }

  async function acceptJoin(r: JoinRequest) {
    if (!team) return;
    setBusyReq(r.requestId); setActionError(null); setNotice(null);
    try {
      await joinRequestsApi.accept(r.requestId);
      const res = team.eventId != null ? await teamsApi.getMyForEvent(team.eventId) : await teamsApi.getMy();
      setTeam(res.data);
      loadJoinRequests(team.teamId);
      setNotice(`${r.requesterName} has joined the team.`);
      addToast({ type: "success", title: "Member added", message: `${r.requesterName} has joined your team.` });
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Failed to accept request.");
      addToast({ type: "warning", title: "Accept failed", message: apiErrorMessage(err, "Failed to accept request.") });
    } finally { setBusyReq(null); }
  }

  async function declineJoin(r: JoinRequest) {
    setBusyReq(r.requestId); setActionError(null);
    try {
      await joinRequestsApi.decline(r.requestId);
      setJoinRequests(prev => prev.filter(x => x.requestId !== r.requestId));
      addToast({ type: "info", title: "Request declined", message: `${r.requesterName}'s join request was declined.` });
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Failed to decline request.");
      addToast({ type: "warning", title: "Decline failed", message: apiErrorMessage(err, "Failed to decline request.") });
    } finally { setBusyReq(null); }
  }

  async function removeMember(m: MyTeamMember) {
    if (!team) return;
    setBusy(true); setActionError(null);
    try {
      const res = await teamsApi.removeMember(team.teamId, m.userId);
      setTeam(res.data);
      setRemoveTarget(null);
      addToast({ type: "warning", title: "Member removed", message: `${m.memberName} was removed from the team.` });
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Failed to remove member.");
      addToast({ type: "warning", title: "Remove failed", message: apiErrorMessage(err, "Failed to remove member.") });
    } finally { setBusy(false); }
  }

  async function confirmTransfer() {
    if (!team || !transferTarget) return;
    setBusy(true); setActionError(null);
    try {
      const res = await teamsApi.transferLeadership(team.teamId, transferTarget.userId);
      setTeam(res.data);
      await refreshTeamContext();
      addToast({ type: "success", title: "Leadership transferred", message: `${transferTarget.memberName} is now the team leader.` });
      setTransferTarget(null);
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Failed to transfer leadership.");
      addToast({ type: "warning", title: "Transfer failed", message: apiErrorMessage(err, "Failed to transfer leadership.") });
    } finally { setBusy(false); }
  }

  async function leaveTeam() {
    if (!team) return;
    setBusy(true); setActionError(null);
    try {
      const leftName = team.name;
      await teamsApi.leave(team.teamId);
      await refreshTeamContext();
      addToast({ type: "info", title: "Left team", message: `You have left "${leftName}".` });
      navigate('/dashboard');
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Failed to leave team.");
      addToast({ type: "warning", title: "Leave failed", message: apiErrorMessage(err, "Failed to leave team.") });
    } finally { setBusy(false); }
  }

  async function submitSupportRequest() {
    if (!reqDesc.trim()) {
      setReqError("Please describe what you need help with.");
      return;
    }
    setReqBusy(true); setReqError(null);
    try {
      await supportApi.create({ category: reqCategory, description: reqDesc.trim() });
      setReqDesc("");
      loadSupport();
      addToast({ type: "success", title: "Request sent", message: "Your mentor has been notified and will come to help." });
    } catch (err) {
      setReqError(err instanceof ApiError ? err.message : "Failed to send request.");
      addToast({ type: "warning", title: "Request failed", message: apiErrorMessage(err, "Failed to send request.") });
    } finally { setReqBusy(false); }
  }

  async function cancelSupportRequest(requestId: number) {
    setReqBusy(true); setReqError(null);
    try {
      await supportApi.cancel(requestId);
      loadSupport();
      addToast({ type: "info", title: "Request cancelled", message: "Your support request has been cancelled." });
    } catch (err) {
      setReqError(err instanceof ApiError ? err.message : "Failed to cancel request.");
      addToast({ type: "warning", title: "Cancel failed", message: apiErrorMessage(err, "Failed to cancel request.") });
    } finally { setReqBusy(false); }
  }

  const memberRows = team.members ?? [];
  const selectedMember = memberRows.find(m => m.userId === selectedMemberId) ?? null;
  const leaderMember = memberRows.find(m => m.role === "LEADER") ?? null;
  const selfMember = memberRows.find(m => m.userId === currentUser?.user_id) ?? null;
  const panelMember = selectedMember ?? selfMember ?? leaderMember ?? memberRows[0] ?? null;
  const effectivePanel = teamPanel ?? "member";

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) minmax(360px, 420px)", gap: 20, alignItems: "start" }}>
        <div style={{ display: "flex", flexDirection: "column", gap: 20, minWidth: 0 }}>
      <PixelCard glow gradient style={{ padding: "22px 24px", display: "flex", flexDirection: "column", gap: 18 }}>
        <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: 16, flexWrap: "wrap" }}>
          <div>
            <div style={{ color: C.greenMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 10, letterSpacing: "0.16em", textTransform: "uppercase", marginBottom: 8 }}>
              Current Event
            </div>
            <div style={{ color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 20, fontWeight: 900, lineHeight: 1.2 }}>
              {team.eventName ?? "—"}
            </div>
            {team.eventTopic && (
              <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, marginTop: 4 }}>{team.eventTopic}</div>
            )}
          </div>
          <PixelBadge color={eventStatusBadgeColor(team.eventStatus)}>{team.eventStatus ?? "—"}</PixelBadge>
        </div>

        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 16, flexWrap: "wrap" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap", minWidth: 0 }}>
            {editingName ? (
              <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                <input
                  value={nameInput} onChange={e => setNameInput(e.target.value)} autoFocus
                  onKeyDown={e => { if (e.key === 'Enter') saveName(); if (e.key === 'Escape') setEditingName(false); }}
                  style={{ background: C.surface2, border: `1px solid ${C.green}`, color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 24, fontWeight: 800, padding: "4px 10px", outline: "none", borderRadius: 0 }}
                />
                <PixelButton size="sm" variant="cyber" onClick={saveName} disabled={busy}>SAVE</PixelButton>
                <PixelButton size="sm" variant="ghost" onClick={() => setEditingName(false)}>CANCEL</PixelButton>
              </div>
            ) : (
              <h1 style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 30, fontWeight: 900, lineHeight: 1.1, margin: 0 }}>
                <GradientText>{team.name}</GradientText>
              </h1>
            )}
            <PixelBadge color={statusBadgeColor(team.status)}>{team.status ?? "—"}</PixelBadge>
            {isLeader && canEditTeam && !editingName && (
              <button onClick={() => { setNameInput(team.name); setEditingName(true); }}
                style={{ background: "transparent", border: `1px solid ${C.border}`, color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 10, padding: "4px 8px", cursor: "pointer", borderRadius: 0, letterSpacing: "0.1em", textTransform: "uppercase" }}>
                EDIT NAME
              </button>
            )}
          </div>
        </div>
      </PixelCard>

      {team.status === 'PENDING' && (
        <div style={{ background: "rgba(234,179,8,0.08)", border: "1px solid rgba(234,179,8,0.4)", color: "#eab308", fontFamily: "'JetBrains Mono', monospace", fontSize: 12, padding: "12px 16px" }}>
          Your team is awaiting coordinator approval. You cannot submit until approved.
        </div>
      )}

      {lockReason && (
        <div style={{ background: "rgba(107,114,128,0.08)", border: `1px solid ${C.border}`, color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, padding: "12px 16px" }}>
          {lockReason}
        </div>
      )}

      {isDormant && isLeader && (
        <div style={{ background: "rgba(6,182,212,0.08)", border: "1px solid rgba(6,182,212,0.35)", color: C.cyan, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, padding: "14px 16px", display: "flex", flexDirection: "column", gap: 10 }}>
          {team.hasPendingRejoinRequest ? (
            <span>Your request to rejoin is awaiting coordinator review.</span>
          ) : rejoinRequested ? (
            <span>Your request to rejoin has been sent.</span>
          ) : activeEvents.length === 0 ? (
            <span style={{ color: C.textMuted }}>Your team can rejoin once a new event opens registration.</span>
          ) : (
            <>
              <span>Your team&apos;s season is over, but its identity and roster are still here. Request to bring it back for a new season.</span>
              <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
                {activeEvents.length > 1 && (
                  <select
                    value={rejoinEventId ?? activeEvents[0]?.eventId ?? ""}
                    onChange={e => setRejoinEventId(Number(e.target.value))}
                    style={{ background: C.surface2, border: `1px solid ${C.border}`, color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, padding: "8px 10px", borderRadius: 0 }}
                  >
                    {activeEvents.map(e => <option key={e.eventId} value={e.eventId}>{e.name}</option>)}
                  </select>
                )}
                <PixelButton
                  size="sm"
                  variant="cyber"
                  onClick={() => { setRejoinEventId(rejoinEventId ?? activeEvents[0]?.eventId ?? null); setConfirmRejoin(true); }}
                >
                  {activeEvents.length === 1 ? `REQUEST TO REJOIN ${activeEvents[0].name.toUpperCase()}` : "REQUEST TO REJOIN"}
                </PixelButton>
              </div>
            </>
          )}
        </div>
      )}

      {canEditTeam && memberRows.length < MIN_TEAM_SIZE && (
        <div style={{ background: "rgba(234,179,8,0.08)", border: "1px solid rgba(234,179,8,0.4)", color: "#eab308", fontFamily: "'JetBrains Mono', monospace", fontSize: 12, padding: "12px 16px" }}>
          Your team has {memberRows.length}/{MIN_TEAM_SIZE} minimum members. Teams with fewer than {MIN_TEAM_SIZE} members may be merged by a coordinator.
        </div>
      )}

      {actionError && (
        <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, padding: "10px 14px" }}>ERROR: {actionError}</div>
      )}
      {notice && (
        <div style={{ background: "rgba(34,197,94,0.08)", border: "1px solid rgba(34,197,94,0.35)", color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, padding: "10px 14px" }}>{notice}</div>
      )}

      {/* Info */}
      <PixelCard style={{ padding: 20 }}>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 16 }}>
          <InfoCell label="Track" value={team.trackName ?? "—"} />
          <InfoCell label={team.status === 'DISQUALIFIED' ? "Disqualified round" : "Current round"} value={team.round?.name ?? "—"} badge={team.round?.status} />
          <InfoCell label="Members" value={`${memberRows.length}/${MAX_TEAM_SIZE}`} accent />
          <InfoCell label="Your role" value={team.myRole ?? "—"} />
        </div>
      </PixelCard>

      {/* Mentor — Request help opens the support panel on the right */}
      <PixelCard style={{ padding: 0, overflow: "hidden" }}>
        <div style={{ padding: "14px 18px", borderBottom: `1px solid ${C.border}`, display: "flex", alignItems: "center", justifyContent: "space-between", gap: 10, flexWrap: "wrap" }}>
          <span style={{ color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 15, fontWeight: 700 }}>Your Mentor</span>
          <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
            {openRequest && <PixelBadge color="cyan">SUPPORT OPEN</PixelBadge>}
            {/* Only the leader can raise a request — members only get the entry
                point once one is already open, so they can view (not create) it. */}
            {mentors.length > 0 && (isLeader || openRequest) && (
              <PixelButton size="sm" variant={teamPanel === "mentor" ? "secondary" : "cyber"} onClick={() => setTeamPanel("mentor")}>
                {openRequest ? "VIEW REQUEST" : "REQUEST HELP"}
              </PixelButton>
            )}
          </div>
        </div>
        <div style={{ padding: "14px 18px" }}>
          {mentors.length === 0 ? (
            <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>
              No mentor is assigned to your track yet.
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {mentors.map(m => (
                <div key={m.userId} style={{ background: C.surface2, border: `1px solid ${C.border}`, padding: "12px 14px" }}>
                  <div style={{ color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 14, fontWeight: 700 }}>{m.fullName}</div>
                  <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, marginTop: 3 }}>
                    {m.trackName} mentor{m.email ? ` · ${m.email}` : ""}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </PixelCard>

      {/* Members */}
      <PixelCard style={{ padding: 0, overflow: "hidden" }}>
        <div style={{ padding: "14px 18px", borderBottom: `1px solid ${C.border}`, display: "flex", alignItems: "center", justifyContent: "space-between", gap: 10, flexWrap: "wrap" }}>
          <span style={{ color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 15, fontWeight: 700 }}>Members</span>
          {isLeader && (
            <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
              {canManageMembers && memberRows.length < MAX_TEAM_SIZE && (
                <PixelButton
                  size="sm"
                  variant="cyber"
                  onClick={() => {
                    setTeamPanel("invite");
                    setInviteSearchMessage(null);
                  }}
                  disabled={busy || inviteSendingId != null}
                >
                  INVITE MEMBER
                </PixelButton>
              )}
              <PixelButton
                size="sm"
                variant={teamPanel === "requests" ? "secondary" : "ghost"}
                onClick={() => setTeamPanel("requests")}
              >
                JOIN REQUESTS{joinRequests.length > 0 ? ` (${joinRequests.length})` : ""}
              </PixelButton>
            </div>
          )}
        </div>

        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontFamily: "'JetBrains Mono', monospace" }}>
            <thead>
              <tr style={{ background: C.surface2, borderBottom: `1px solid ${C.border}` }}>
                {["Member", "Role", ...(canManageMembers ? ["Actions"] : [])].map(h => (
                  <th key={h} style={{ color: C.green, fontSize: 10, letterSpacing: "0.12em", textAlign: "left", padding: "11px 14px", fontWeight: 600, textTransform: "uppercase" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {memberRows.map((m, i) => {
                const isSelf = currentUser?.user_id === m.userId;
                const selected = selectedMemberId === m.userId;
                return (
                  <tr
                    key={m.userId}
                    onClick={() => { setSelectedMemberId(m.userId); setTeamPanel("member"); }}
                    onMouseEnter={() => setHoveredActionUserId(m.userId)}
                    onMouseLeave={() => setHoveredActionUserId(null)}
                    style={{
                      borderBottom: `1px solid rgba(34,197,94,0.06)`,
                      background: selected ? "rgba(34,197,94,0.12)" : i % 2 === 0 ? C.surface : C.surface2,
                      boxShadow: selected ? `inset 3px 0 0 ${C.green}, 0 0 18px rgba(34,197,94,0.14)` : "none",
                      cursor: "pointer",
                      transition: "background 0.15s ease, box-shadow 0.15s ease",
                    }}
                  >
                    <td style={{ padding: "11px 14px" }}>
                      <span style={{ color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, fontWeight: m.role === 'LEADER' ? 700 : 400 }}>{m.memberName}</span>
                      {isSelf && <span style={{ color: C.textMuted, fontSize: 10, marginLeft: 6 }}>(you)</span>}
                    </td>
                    <td style={{ padding: "11px 14px" }}>
                      <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                        <PixelBadge color={m.role === 'LEADER' ? 'cyan' : 'blue'}>{m.role === 'LEADER' ? "Leader" : "Member"}</PixelBadge>
                        {m.isActive === false && <PixelBadge color="red">INACTIVE</PixelBadge>}
                      </div>
                    </td>
                    {canManageMembers && (
                      <td style={{ padding: "11px 14px", position: "relative" }} onClick={(event) => event.stopPropagation()}>
                        {m.role === 'MEMBER' && (
                          <div style={{ position: "relative", display: "inline-flex", justifyContent: "flex-end", width: "100%" }}>
                            <span style={{ opacity: hoveredActionUserId === m.userId ? 1 : 0, pointerEvents: hoveredActionUserId === m.userId ? "auto" : "none", transition: "opacity 0.12s ease" }}>
                              <PixelMenu
                                ariaLabel={`Open actions for ${m.memberName}`}
                                disabled={busy}
                                align="right"
                                minWidth={176}
                                items={[
                                  { label: "Transfer lead", onClick: () => setTransferTarget(m) },
                                  "divider",
                                  { label: "Remove", danger: true, onClick: () => setRemoveTarget(m) },
                                ] satisfies PixelMenuEntry[]}
                              />
                            </span>
                          </div>
                        )}
                      </td>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </PixelCard>

      {/* Leave */}
      {canLeaveTeam && (
        <div>
          <PixelButton variant="danger" onClick={() => setConfirmLeave(true)} disabled={busy}>LEAVE TEAM</PixelButton>
          {isLeader && memberRows.length > 1 && (
            <span style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, marginLeft: 12 }}>
            </span>
          )}
        </div>
      )}
        </div>

        <aside style={{ position: "sticky", top: 92, minHeight: 360 }}>
          <PixelCard glow={effectivePanel != null} glowColor={effectivePanel === "requests" ? "cyan" : "green"} style={{ padding: 0, overflow: "hidden", minHeight: 360 }}>
            <div style={{ padding: "18px 20px", borderBottom: `1px solid ${C.border}`, display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12 }}>
              <div>
                <div style={{ color: effectivePanel === "requests" ? C.cyan : C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, fontWeight: 900, letterSpacing: "0.14em", textTransform: "uppercase" }}>
                  {effectivePanel === "invite" ? "Invite Member" : effectivePanel === "requests" ? "Join Requests" : effectivePanel === "mentor" ? "Mentor Support" : "Member Info"}
                </div>
              </div>
            </div>

            <div style={{ padding: 20 }}>
              {effectivePanel === "mentor" ? (
                <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
                  {mentors.length === 0 && (
                    <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, border: `1px solid ${C.border}`, background: C.surface2, padding: 14 }}>
                      No mentor is assigned to your track yet.
                    </div>
                  )}
                  {mentors.length > 0 && openRequest ? (
                    /* Current open request — status + cancel */
                    <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                      <div style={{ background: C.surface2, border: `1px solid ${C.border}`, padding: 14, display: "flex", flexDirection: "column", gap: 8 }}>
                        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
                          <PixelBadge color="cyan">{categoryLabel(openRequest.category)}</PixelBadge>
                          <PixelBadge color="yellow">OPEN</PixelBadge>
                        </div>
                        <div style={{ color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, lineHeight: 1.7, whiteSpace: "pre-wrap", wordBreak: "break-word" }}>{openRequest.description}</div>
                        <div style={{ color: C.textDim, fontFamily: "'JetBrains Mono', monospace", fontSize: 10 }}>Sent {fmtDT(openRequest.createdAt)} · by {openRequest.requesterName ?? "—"}</div>
                      </div>
                      <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, lineHeight: 1.6 }}>
                        Your mentor has been notified and will come to help in person.
                      </div>
                      {isLeader ? (
                        <PixelButton size="sm" variant="danger" onClick={() => cancelSupportRequest(openRequest.requestId)} disabled={reqBusy}>
                          {reqBusy ? "..." : "CANCEL REQUEST"}
                        </PixelButton>
                      ) : (
                        <div style={{ color: C.textDim, fontFamily: "'JetBrains Mono', monospace", fontSize: 10 }}>Only the team leader can cancel this request.</div>
                      )}
                    </div>
                  ) : mentors.length > 0 && (
                    /* No open request — the leader can raise one */
                    isLeader ? (
                      <>
                        <div>
                          <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 10, letterSpacing: "0.1em", marginBottom: 8 }}>WHAT DO YOU NEED HELP WITH?</div>
                          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                            {SUPPORT_CATEGORIES.map(c => {
                              const on = reqCategory === c.value;
                              return (
                                <button key={c.value} type="button" onClick={() => setReqCategory(c.value)}
                                  style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 11, padding: "6px 12px", cursor: "pointer", borderRadius: 0,
                                    background: on ? "rgba(34,197,94,0.12)" : C.surface2, border: `1px solid ${on ? C.green : C.border}`, color: on ? C.green : C.textMuted }}>
                                  {c.label}
                                </button>
                              );
                            })}
                          </div>
                        </div>
                        <textarea
                          value={reqDesc} onChange={e => setReqDesc(e.target.value)} maxLength={2000} disabled={reqBusy}
                          placeholder="Describe your question so the mentor knows how to help…"
                          style={{ background: C.surface2, border: `1px solid ${C.border}`, color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, padding: "10px 12px", minHeight: 110, resize: "vertical", lineHeight: 1.6, outline: "none", borderRadius: 0 }}
                        />
                        {reqError && (
                          <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, padding: "8px 12px" }}>
                            {reqError}
                          </div>
                        )}
                        <PixelButton variant="cyber" onClick={submitSupportRequest} disabled={reqBusy}>
                          {reqBusy ? "SENDING…" : "REQUEST SUPPORT"}
                        </PixelButton>
                      </>
                    ) : (
                      <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, border: `1px solid ${C.border}`, background: C.surface2, padding: 14, lineHeight: 1.6 }}>
                        Only the team leader can send a mentor support request. Ask your leader if the team needs help.
                      </div>
                    )
                  )}
                </div>
              ) : effectivePanel === "member" ? (
                panelMember ? (
                  <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
                    <div style={{ background: C.surface2, border: `1px solid ${C.border}`, padding: 16 }}>
                      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 10, flexWrap: "wrap" }}>
                        <div style={{ color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 17, fontWeight: 900 }}>
                          {panelMember.memberName}
                          {panelMember.userId === currentUser?.user_id && <span style={{ color: C.green, fontSize: 11, marginLeft: 8 }}>(YOU)</span>}
                        </div>
                        <PixelBadge color={panelMember.role === "LEADER" ? "cyan" : "blue"}>{panelMember.role}</PixelBadge>
                      </div>
                    </div>

                    <div style={{ display: "grid", gridTemplateColumns: "1fr", gap: 10 }}>
                      <PanelInfo label="Email" value={panelMember.email ?? "—"} />
                      <PanelInfo label="Student ID" value={panelMember.studentId ?? "—"} />
                      <PanelInfo label="Student Type" value={panelMember.studentType ?? "—"} />
                      <PanelInfo label="Joined At" value={fmtDT(panelMember.joinedAt)} />
                    </div>
                  </div>
                ) : (
                  <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, border: `1px solid ${C.border}`, background: C.surface2, padding: 14 }}>
                    Select a member to view details.
                  </div>
                )
              ) : effectivePanel === "invite" ? (
                <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
                  <div style={{ display: "flex", gap: 10, alignItems: "flex-end" }}>
                    <div style={{ flex: 1 }}>
                      <PixelInput label="Search by name, email or student ID" placeholder="min 2 characters"
                        value={inviteQuery}
                        onChange={e => setInviteQuery(e.target.value)}
                        onKeyDown={e => { if (e.key === 'Enter') doSearch(); }}
                      />
                    </div>
                    <PixelButton size="sm" variant="secondary" onClick={doSearch} disabled={searching || inviteSendingId != null}>{searching ? "..." : "SEARCH"}</PixelButton>
                  </div>

                  {inviteSearchMessage && (
                    <div style={{ color: "#eab308", fontFamily: "'JetBrains Mono', monospace", fontSize: 11, lineHeight: 1.6 }}>
                      {inviteSearchMessage}
                    </div>
                  )}
                  
                  {inviteResults.length > 0 && (
                    <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                      {inviteResults.map(u => (
                        <div key={u.userId} style={{ display: "flex", flexDirection: "column", gap: 10, padding: "12px", background: C.surface2, border: `1px solid ${C.border}` }}>
                          <div style={{ minWidth: 0 }}>
                            <span style={{ color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, fontWeight: 700 }}>{u.fullName}</span>
                            <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 10, marginTop: 3 }}>{u.email}{u.studentId ? ` - ${u.studentId}` : ""}</div>
                          </div>
                          <PixelButton
                            size="sm"
                            variant="cyber"
                            onClick={() => {
                              setActionError(null);
                              setInviteConfirmTarget(u);
                            }}
                            disabled={inviteSendingId != null}
                          >
                            {inviteSendingId === u.userId ? "SENDING..." : "INVITE"}
                          </PixelButton>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              ) : joinRequests.length === 0 ? (
                <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, border: `1px solid ${C.border}`, background: C.surface2, padding: 14 }}>
                  No pending requests.
                </div>
              ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                  {joinRequests.map(r => (
                    <div key={r.requestId} style={{ display: "flex", flexDirection: "column", gap: 12, padding: "14px", background: C.surface2, border: `1px solid ${C.border}` }}>
                      <div>
                        <div style={{ color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, fontWeight: 700 }}>{r.requesterName}</div>
                        <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, marginTop: 4, lineHeight: 1.5 }}>
                          {r.requesterEmail ?? ""}{r.message ? ` - "${r.message}"` : ""}
                        </div>
                      </div>
                      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                        <PixelButton size="sm" variant="danger" onClick={() => declineJoin(r)} disabled={busyReq === r.requestId}>DECLINE</PixelButton>
                        <PixelButton size="sm" variant="cyber" onClick={() => acceptJoin(r)} disabled={busyReq === r.requestId || memberRows.length >= MAX_TEAM_SIZE || !editable}>ACCEPT</PixelButton>
                      </div>
                    </div>
                  ))}
                  {!editable && (
                    <div style={{ color: "#3b82f6", fontFamily: "'JetBrains Mono', monospace", fontSize: 10, lineHeight: 1.6 }}>
                      The team is locked for this phase - you can no longer accept new members.
                    </div>
                  )}
                  {editable && memberRows.length >= MAX_TEAM_SIZE && (
                    <div style={{ color: "#eab308", fontFamily: "'JetBrains Mono', monospace", fontSize: 10, lineHeight: 1.6 }}>
                      Team is full ({MAX_TEAM_SIZE}/{MAX_TEAM_SIZE}) - remove a member before accepting new requests.
                    </div>
                  )}
                </div>
              )}
            </div>
          </PixelCard>
        </aside>
      </div>

      {/* Leave confirmation modal */}
      {confirmLeave && (
        <div style={{ position: "fixed", inset: 0, zIndex: 200, background: "rgba(7,12,15,0.85)", backdropFilter: "blur(4px)", display: "flex", alignItems: "center", justifyContent: "center" }}>
          <PixelCard glow style={{ padding: 32, maxWidth: 440, width: "90%", display: "flex", flexDirection: "column", gap: 20 }}>
            <div>
              <div style={{ color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 15, fontWeight: 700, lineHeight: 1.5 }}>
                Leave <span style={{ color: C.red }}>{team.name}</span>?
              </div>
              <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, marginTop: 10, lineHeight: 1.7 }}>
                {isLeader && memberRows.length > 1
                  ? "As leader you must transfer leadership before leaving."
                  : isLeader
                    ? "You are the only member — leaving will disband this team."
                    : "You will be removed from the team and can be re-invited later."}
              </div>
            </div>
            <div style={{ display: "flex", gap: 10 }}>
              <PixelButton
                variant="danger"
                onClick={async () => { await leaveTeam(); setConfirmLeave(false); }}
                disabled={busy || (isLeader && memberRows.length > 1)}
              >
                CONFIRM LEAVE
              </PixelButton>
              <PixelButton variant="ghost" onClick={() => setConfirmLeave(false)}>CANCEL</PixelButton>
            </div>
          </PixelCard>
        </div>
      )}

      {/* Remove member confirmation modal */}
      {removeTarget && (
        <div style={{ position: "fixed", inset: 0, zIndex: 200, background: "rgba(7,12,15,0.85)", backdropFilter: "blur(4px)", display: "flex", alignItems: "center", justifyContent: "center" }}>
          <PixelCard glow style={{ padding: 32, maxWidth: 440, width: "90%", display: "flex", flexDirection: "column", gap: 20 }}>
            <div>
              <div style={{ color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 15, fontWeight: 700, lineHeight: 1.5 }}>
                Remove <span style={{ color: C.red }}>{removeTarget.memberName}</span> from the team?
              </div>
              <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, marginTop: 10, lineHeight: 1.7 }}>
                They will lose access to this team and can be re-invited later.
              </div>
            </div>
            <div style={{ display: "flex", gap: 10 }}>
              <PixelButton variant="danger" onClick={() => removeMember(removeTarget)} disabled={busy}>CONFIRM REMOVE</PixelButton>
              <PixelButton variant="ghost" onClick={() => setRemoveTarget(null)} disabled={busy}>CANCEL</PixelButton>
            </div>
          </PixelCard>
        </div>
      )}

      {/* Transfer modal */}
      {transferTarget && (
        <div style={{ position: "fixed", inset: 0, zIndex: 200, background: "rgba(7,12,15,0.85)", backdropFilter: "blur(4px)", display: "flex", alignItems: "center", justifyContent: "center" }}>
          <PixelCard glow style={{ padding: 32, maxWidth: 440, width: "90%", display: "flex", flexDirection: "column", gap: 20 }}>
            <div>
              <div style={{ color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 15, fontWeight: 700, lineHeight: 1.5 }}>
                Transfer leadership to <span style={{ color: C.green }}>{transferTarget.memberName}</span>?
              </div>
              <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, marginTop: 10, lineHeight: 1.7 }}>
                You will become a regular member and lose management, invite and submit controls.
              </div>
            </div>
            <div style={{ display: "flex", gap: 10 }}>
              <PixelButton variant="danger" onClick={confirmTransfer} disabled={busy}>CONFIRM TRANSFER</PixelButton>
              <PixelButton variant="ghost" onClick={() => setTransferTarget(null)}>CANCEL</PixelButton>
            </div>
          </PixelCard>
        </div>
      )}

      {confirmRejoin && rejoinEventId != null && (
        <ConfirmDialog
          title="Request to rejoin this season?"
          message={
            <>
              Ask the coordinator to bring <strong style={{ color: C.text }}>{team.name}</strong> back for{" "}
              <strong style={{ color: C.text }}>
                {activeEvents.find(e => e.eventId === rejoinEventId)?.name ?? "this event"}
              </strong>.
            </>
          }
          warning="Each roster member still needs their own account reactivated separately before they can act on the new season."
          confirmLabel="REQUEST REJOIN"
          variant="cyber"
          working={requestingRejoin}
          error={rejoinError}
          onConfirm={requestRejoin}
          onClose={() => { if (!requestingRejoin) { setConfirmRejoin(false); setRejoinError(null); } }}
        />
      )}

      {inviteConfirmTarget && (
        <ConfirmDialog
          title="Send team invitation?"
          message={
            <>
              Invite <strong style={{ color: C.text }}>{inviteConfirmTarget.fullName}</strong> to join{" "}
              <strong style={{ color: C.text }}>{team.name}</strong>.
              {inviteConfirmTarget.email && (
                <span style={{ display: "block", marginTop: 8 }}>
                  Email: <span style={{ color: C.text }}>{inviteConfirmTarget.email}</span>
                </span>
              )}
            </>
          }
          warning="The participant will receive an invitation and can join your team after accepting it."
          confirmLabel="SEND INVITE"
          variant="cyber"
          working={inviteSendingId === inviteConfirmTarget.userId}
          error={actionError}
          onConfirm={() => sendInvite(inviteConfirmTarget)}
          onClose={() => {
            if (inviteSendingId == null) {
              setInviteConfirmTarget(null);
              setActionError(null);
            }
          }}
        />
      )}
    </div>
  );
}

function InfoCell({ label, value, accent, badge }: { label: string; value: string; accent?: boolean; badge?: string }) {
  return (
    <div>
      <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 10, letterSpacing: "0.1em", textTransform: "uppercase", marginBottom: 4 }}>{label}</div>
      <div style={{ color: accent ? C.green : C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 15, fontWeight: 600, display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
        {value}
        {badge && <PixelBadge color={roundStatusColor(badge)}>{badge}</PixelBadge>}
      </div>
    </div>
  );
}

function PanelInfo({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ background: C.surface2, border: `1px solid ${C.border}`, padding: "11px 12px" }}>
      <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 10, letterSpacing: "0.1em", textTransform: "uppercase", marginBottom: 5 }}>
        {label}
      </div>
      <div style={{ color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, fontWeight: 700, lineHeight: 1.5, overflowWrap: "anywhere" }}>
        {value}
      </div>
    </div>
  );
}
