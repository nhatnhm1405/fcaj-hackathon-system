import { useState, useEffect, useCallback, useMemo } from "react";
import { useAuth } from "@/app/providers/AuthProvider";
import { C, PixelButton, PixelInput } from "@/shared/components/PixelComponents";
import {
    invitesApi, joinRequestsApi,
    TeamInvite, JoinableTeam, JoinRequest, ApiError, apiErrorMessage,
} from "@/shared/apiClient";
import { useNotifications } from "@/app/providers/NotificationProvider";
import { ConfirmDialog } from "@/shared/components/ConfirmDialog";
import { MAX_TEAM_SIZE } from "@/shared/teamPhase";
import { fmtShort } from "../utils/formatters";

const mono = "'JetBrains Mono', monospace";
const DRAWER_TRANSITION_MS = 220;

export function InvitationsDrawer({ onClose }: { onClose: () => void }) {
    const { refreshTeamContext } = useAuth();
    const { addToast } = useNotifications();
    const [entered, setEntered] = useState(false);
    const [closing, setClosing] = useState(false);

    const [invites, setInvites] = useState<TeamInvite[]>([]);
    const [localStatus, setLocalStatus] = useState<Record<number, 'ACCEPTED' | 'DECLINED'>>({});
    const [busy, setBusy] = useState<number | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [acceptConfirmTarget, setAcceptConfirmTarget] = useState<TeamInvite | null>(null);

    // Find-a-team search
    const [query, setQuery] = useState("");
    const [results, setResults] = useState<JoinableTeam[]>([]);
    const [myRequests, setMyRequests] = useState<JoinRequest[]>([]);
    const [searching, setSearching] = useState(false);
    const [searched, setSearched] = useState(false);
    const [selectedTeamId, setSelectedTeamId] = useState<number | null>(null);

    const loadInvites = useCallback(() => {
        invitesApi.getPending().then(r => setInvites(r.data ?? [])).catch(() => setInvites([]));
    }, []);
    const loadRequests = useCallback(() => {
        joinRequestsApi.getMine()
            .then(r => setMyRequests((r.data ?? []).filter(req => req.status === 'PENDING')))
            .catch(() => setMyRequests([]));
    }, []);

    useEffect(() => { loadInvites(); loadRequests(); }, [loadInvites, loadRequests]);

    useEffect(() => {
        const frame = window.requestAnimationFrame(() => setEntered(true));
        return () => window.cancelAnimationFrame(frame);
    }, []);

    useEffect(() => {
        void doSearch();
        // Load all joinable teams when the drawer opens. Search remains manual
        // so typing does not spam the endpoint.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    function getStatus(inv: TeamInvite): 'PENDING' | 'ACCEPTED' | 'DECLINED' {
        return localStatus[inv.inviteId] ?? (inv.status as 'PENDING' | 'ACCEPTED' | 'DECLINED');
    }

    async function handleAccept(inv: TeamInvite) {
        setBusy(inv.inviteId); setError(null);
        try {
            await invitesApi.accept(inv.inviteId);
            setLocalStatus(s => ({ ...s, [inv.inviteId]: 'ACCEPTED' }));
            await refreshTeamContext();
            setAcceptConfirmTarget(null);
            addToast({ type: "success", title: "Invite accepted", message: `You've joined "${inv.teamName}".` });
        } catch (err) {
            setError(err instanceof ApiError ? err.message : "Failed to accept invite.");
            addToast({ type: "warning", title: "Accept failed", message: apiErrorMessage(err, "Failed to accept invite.") });
        } finally { setBusy(null); }
    }

    async function handleDecline(inv: TeamInvite) {
        setBusy(inv.inviteId); setError(null);
        try {
            await invitesApi.decline(inv.inviteId);
            setLocalStatus(s => ({ ...s, [inv.inviteId]: 'DECLINED' }));
            addToast({ type: "info", title: "Invite declined", message: `You declined the invite from "${inv.teamName}".` });
        } catch (err) {
            setError(err instanceof ApiError ? err.message : "Failed to decline invite.");
            addToast({ type: "warning", title: "Decline failed", message: apiErrorMessage(err, "Failed to decline invite.") });
        } finally { setBusy(null); }
    }

    async function doSearch() {
        setSearching(true); setError(null);
        try {
            const res = await joinRequestsApi.getJoinableTeams({ query: query.trim() || undefined });
            const teams = res.data?.teams ?? [];
            setResults(teams);
            setSelectedTeamId(prev => (prev && teams.some(t => t.teamId === prev)) ? prev : null);
            setSearched(true);
        } catch (err) {
            setError(err instanceof ApiError ? err.message : "Search failed.");
        } finally { setSearching(false); }
    }

    async function requestToJoin(team: JoinableTeam) {
        setBusy(team.teamId); setError(null);
        try {
            await joinRequestsApi.send(team.teamId);
            setResults(prev => prev.map(t => t.teamId === team.teamId ? { ...t, alreadyRequested: true } : t));
            loadRequests();
            addToast({ type: "success", title: "Request sent", message: `Your request to join "${team.teamName}" was sent.` });
        } catch (err) {
            setError(err instanceof ApiError ? err.message : "Failed to send join request.");
            addToast({ type: "warning", title: "Request failed", message: apiErrorMessage(err, "Failed to send join request.") });
        } finally { setBusy(null); }
    }

    const pendingCount = invites.filter(inv => getStatus(inv) === 'PENDING').length;
    const sortedResults = useMemo(() => {
        return [...results].sort((a, b) => {
            const missingA = Math.max(0, MAX_TEAM_SIZE - a.memberCount);
            const missingB = Math.max(0, MAX_TEAM_SIZE - b.memberCount);
            if (missingA !== missingB) return missingB - missingA;
            if (a.memberCount !== b.memberCount) return a.memberCount - b.memberCount;
            return a.teamName.localeCompare(b.teamName);
        });
    }, [results]);

    const statusStyle: Record<string, { bg: string; border: string; color: string }> = {
        PENDING: { bg: "rgba(59,130,246,0.08)", border: "rgba(59,130,246,0.25)", color: C.blue },
        ACCEPTED: { bg: "rgba(34,197,94,0.08)", border: "rgba(34,197,94,0.3)", color: C.green },
        DECLINED: { bg: "rgba(239,68,68,0.08)", border: "rgba(239,68,68,0.25)", color: "#ef4444" },
    };
    const visible = entered && !closing;

    function requestClose() {
        if (closing) return;
        setClosing(true);
        window.setTimeout(onClose, DRAWER_TRANSITION_MS);
    }

    return (
        <>
            {/* Backdrop */}
            <div
                onClick={requestClose}
                style={{
                    position: "fixed",
                    inset: 0,
                    background: "rgba(0,0,0,0.55)",
                    zIndex: 200,
                    backdropFilter: "blur(2px)",
                    opacity: visible ? 1 : 0,
                    transition: `opacity ${DRAWER_TRANSITION_MS}ms ease`,
                }}
            />

            {/* Drawer */}
            <div style={{
                position: "fixed", top: 0, right: 0, bottom: 0, width: "min(600px, 100vw)",
                background: C.surface, borderLeft: `1px solid ${C.border}`, zIndex: 201,
                overflowY: "auto", display: "flex", flexDirection: "column", boxShadow: "-8px 0 40px rgba(0,0,0,0.6)",
                transform: visible ? "translateX(0)" : "translateX(100%)",
                opacity: visible ? 1 : 0,
                transition: `transform ${DRAWER_TRANSITION_MS}ms ease, opacity ${DRAWER_TRANSITION_MS}ms ease`,
            }}>
                {/* Header */}
                <div style={{
                    padding: "16px 24px", borderBottom: `1px solid ${C.border}`, display: "flex",
                    alignItems: "center", justifyContent: "space-between", flexShrink: 0,
                    position: "sticky", top: 0, background: C.surface, zIndex: 1,
                }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                        <button
                            onClick={requestClose}
                            style={{ background: "none", border: "none", cursor: "pointer", color: C.textMuted, padding: "2px 6px", display: "flex", alignItems: "center", gap: 6, fontFamily: mono, fontSize: 12, transition: "color 0.15s" }}
                            onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.color = C.green; }}
                            onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.color = C.textMuted; }}
                        >
                            ← Back
                        </button>
                        <span style={{ color: C.green, fontFamily: mono, fontSize: 15, fontWeight: 700 }}>Team Invitations</span>
                    </div>
                    <div style={{ background: "rgba(59,130,246,0.12)", border: "1px solid rgba(59,130,246,0.3)", color: C.blue, fontFamily: mono, fontSize: 10, letterSpacing: "0.1em", padding: "2px 10px" }}>
                        {pendingCount} PENDING
                    </div>
                </div>

                <div style={{ padding: "24px", display: "flex", flexDirection: "column", gap: 20 }}>
                    {error && (
                        <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: mono, fontSize: 11, padding: "10px 14px" }}>ERROR: {error}</div>
                    )}

                    {/* Received invites */}
                    {invites.length === 0 ? (
                        <div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", padding: "40px 24px", gap: 16, textAlign: "center" }}>
                            <div style={{ width: 56, height: 56, borderRadius: "50%", background: "rgba(134,239,172,0.06)", border: `1px solid ${C.border}`, display: "flex", alignItems: "center", justifyContent: "center" }}>
                                <svg width="24" height="24" fill="none" viewBox="0 0 24 24">
                                    <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" stroke={C.textMuted} strokeWidth="1.5" strokeLinecap="round" />
                                    <circle cx="9" cy="7" r="4" stroke={C.textMuted} strokeWidth="1.5" />
                                    <path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" stroke={C.textMuted} strokeWidth="1.5" strokeLinecap="round" />
                                </svg>
                            </div>
                            <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 12 }}>No invitations yet</div>
                        </div>
                    ) : (
                        invites.map(inv => {
                            const status = getStatus(inv);
                            const s = statusStyle[status];
                            return (
                                <div key={inv.inviteId} style={{
                                    background: C.surface2,
                                    border: `1px solid ${status === 'ACCEPTED' ? 'rgba(34,197,94,0.3)' : status === 'DECLINED' ? 'rgba(239,68,68,0.2)' : C.border}`,
                                    padding: "20px", display: "flex", flexDirection: "column", gap: 14,
                                    position: "relative", overflow: "hidden",
                                    opacity: status !== 'PENDING' ? 0.7 : 1, transition: "opacity 0.2s, border-color 0.2s",
                                }}>
                                    <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: status === 'ACCEPTED' ? `linear-gradient(90deg, ${C.green}, transparent)` : status === 'DECLINED' ? "linear-gradient(90deg, #ef4444, transparent)" : `linear-gradient(90deg, ${C.blue}, transparent)`, opacity: 0.6 }} />

                                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 10 }}>
                                        <div>
                                            <div style={{ color: C.text, fontFamily: mono, fontSize: 15, fontWeight: 700, marginBottom: 4 }}>{inv.teamName}</div>
                                            <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 11 }}>{inv.eventName ?? "—"} · {inv.trackName ?? "—"}</div>
                                        </div>
                                        <div style={{ background: s.bg, border: `1px solid ${s.border}`, color: s.color, fontFamily: mono, fontSize: 9, letterSpacing: "0.12em", padding: "3px 10px", flexShrink: 0 }}>{status}</div>
                                    </div>

                                    <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                                        <span style={{
                                            background: inv.teamStatus === 'APPROVED' ? "rgba(34,197,94,0.08)" : "rgba(234,179,8,0.08)",
                                            border: `1px solid ${inv.teamStatus === 'APPROVED' ? "rgba(34,197,94,0.25)" : "rgba(234,179,8,0.25)"}`,
                                            color: inv.teamStatus === 'APPROVED' ? C.green : "#eab308",
                                            fontFamily: mono, fontSize: 9, letterSpacing: "0.1em", padding: "2px 10px",
                                        }}>
                                            TEAM {inv.teamStatus ?? "—"}
                                        </span>
                                        {inv.trackName && (
                                            <span style={{ background: "rgba(6,182,212,0.08)", border: "1px solid rgba(6,182,212,0.2)", color: "#06b6d4", fontFamily: mono, fontSize: 9, letterSpacing: "0.1em", padding: "2px 10px" }}>{inv.trackName}</span>
                                        )}
                                    </div>

                                    {inv.message && (
                                        <div style={{ background: "rgba(0,0,0,0.2)", border: `1px solid ${C.border}`, padding: "10px 14px", color: C.textMuted, fontFamily: mono, fontSize: 11, lineHeight: 1.7, fontStyle: "italic" }}>
                                            "{inv.message}"
                                        </div>
                                    )}

                                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                                        <div style={{ color: "rgba(134,239,172,0.5)", fontFamily: mono, fontSize: 10 }}>
                                            From <span style={{ color: C.textMuted }}>{inv.invitedByName}</span>
                                        </div>
                                        <div style={{ color: "rgba(134,239,172,0.35)", fontFamily: mono, fontSize: 10 }}>{fmtShort(inv.createdAt)}</div>
                                    </div>

                                    {status === 'PENDING' && (
                                        // Decline on the left, Accept on the right — keeps the
                                        // destructive choice away from where a quick, confident
                                        // accept click naturally lands.
                                        <div style={{ display: "flex", gap: 10 }}>
                                            <button
                                                onClick={() => handleDecline(inv)}
                                                disabled={busy === inv.inviteId}
                                                style={{ padding: "9px 16px", background: "rgba(239,68,68,0.06)", border: "1px solid rgba(239,68,68,0.25)", color: "#ef4444", fontFamily: mono, fontSize: 11, letterSpacing: "0.06em", cursor: "pointer", borderRadius: 0 }}
                                            >
                                                DECLINE
                                            </button>
                                            <button
                                                onClick={() => {
                                                    setError(null);
                                                    setAcceptConfirmTarget(inv);
                                                }}
                                                disabled={busy === inv.inviteId}
                                                style={{ flex: 1, padding: "9px 12px", background: "rgba(34,197,94,0.1)", border: `1px solid rgba(34,197,94,0.4)`, color: C.green, fontFamily: mono, fontSize: 11, letterSpacing: "0.06em", cursor: "pointer", borderRadius: 0, fontWeight: 700 }}
                                            >
                                                ACCEPT INVITE
                                            </button>
                                        </div>
                                    )}

                                    {status === 'ACCEPTED' && (
                                        <div style={{ display: "flex", alignItems: "center", gap: 8, color: C.green, fontFamily: mono, fontSize: 11, fontWeight: 700 }}>
                                            <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M4 12l5 5L20 7" stroke="#22c55e" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" /></svg>
                                            Invite accepted — welcome to the team!
                                        </div>
                                    )}
                                    {status === 'DECLINED' && (
                                        <div style={{ color: "rgba(239,68,68,0.7)", fontFamily: mono, fontSize: 11 }}>Invitation declined.</div>
                                    )}
                                </div>
                            );
                        })
                    )}

                    {/* Find a team to join */}
                    <div style={{ borderTop: `1px solid ${C.border}`, paddingTop: 20 }}>
                        <div style={{ display: "flex", alignItems: "flex-end", justifyContent: "space-between", gap: 12, marginBottom: 12 }}>
                            <div>
                                <div style={{ color: C.green, fontFamily: mono, fontSize: 15, fontWeight: 700 }}>Find a Team</div>
                            </div>
                            <div style={{ color: C.cyan, border: "1px solid rgba(6,182,212,0.25)", background: "rgba(6,182,212,0.06)", fontFamily: mono, fontSize: 10, letterSpacing: "0.08em", padding: "4px 9px", whiteSpace: "nowrap" }}>
                                {sortedResults.length} OPEN
                            </div>
                        </div>
                        <div style={{ display: "flex", gap: 10, alignItems: "flex-end" }}>
                            <div style={{ flex: 1 }}>
                                <PixelInput placeholder="Team name, leader, track..." value={query} onChange={e => setQuery(e.target.value)} />
                            </div>
                            <PixelButton size="sm" variant="secondary" onClick={doSearch} disabled={searching}>{searching ? "…" : "SEARCH"}</PixelButton>
                        </div>

                        {searched && sortedResults.length === 0 && (
                            <p style={{ color: C.textMuted, fontFamily: mono, fontSize: 11, marginTop: 12 }}>No teams with open seats match.</p>
                        )}

                        {sortedResults.length > 0 && (
                            <div style={{ marginTop: 14, border: `1px solid ${C.border}`, background: "rgba(15,23,42,0.34)" }}>
                                {sortedResults.map(t => {
                                    const selected = selectedTeamId === t.teamId;
                                    const requested = t.alreadyRequested || myRequests.some(r => r.teamId === t.teamId);
                                    const openSeats = Math.max(0, MAX_TEAM_SIZE - t.memberCount);
                                    const fillPercent = Math.min(100, Math.max(0, (t.memberCount / MAX_TEAM_SIZE) * 100));
                                    return (
                                        <div
                                            key={t.teamId}
                                            style={{
                                                borderBottom: `1px solid rgba(34,197,94,0.08)`,
                                                background: selected ? "rgba(34,197,94,0.12)" : "rgba(15,23,42,0.18)",
                                                boxShadow: selected ? `inset 3px 0 0 ${C.green}, 0 0 18px rgba(34,197,94,0.12)` : "inset 0 1px 0 rgba(255,255,255,0.015)",
                                                transition: "background 0.15s ease, box-shadow 0.15s ease",
                                            }}
                                        >
                                            <button
                                                type="button"
                                                onClick={() => setSelectedTeamId(selected ? null : t.teamId)}
                                                style={{
                                                    width: "100%",
                                                    border: 0,
                                                    background: "transparent",
                                                    color: C.text,
                                                    cursor: "pointer",
                                                    display: "grid",
                                                    gridTemplateColumns: "minmax(0, 1fr) auto",
                                                    gap: 14,
                                                    alignItems: "center",
                                                    padding: "15px 16px",
                                                    textAlign: "left",
                                                }}
                                            >
                                                <span style={{ minWidth: 0 }}>
                                                    <span style={{ display: "block", color: selected ? "#ffffff" : C.text, fontFamily: mono, fontSize: 13, fontWeight: 900, lineHeight: 1.35, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", letterSpacing: "0.01em" }}>
                                                        {t.teamName}
                                                    </span>
                                                </span>
                                                <span style={{
                                                    border: `1px solid ${requested ? "rgba(59,130,246,0.35)" : openSeats >= 3 ? "rgba(34,197,94,0.3)" : "rgba(234,179,8,0.28)"}`,
                                                    background: requested ? "rgba(59,130,246,0.08)" : openSeats >= 3 ? "rgba(34,197,94,0.08)" : "rgba(234,179,8,0.08)",
                                                    color: requested ? C.blue : openSeats >= 3 ? C.green : "#eab308",
                                                    fontFamily: mono,
                                                    fontSize: 12,
                                                    fontWeight: 800,
                                                    letterSpacing: "0.08em",
                                                    padding: "5px 9px",
                                                    minWidth: 46,
                                                    textAlign: "center",
                                                    whiteSpace: "nowrap",
                                                    boxShadow: selected ? "0 0 14px rgba(34,197,94,0.12)" : "none",
                                                }}>
                                                    {requested ? "REQ" : `${t.memberCount}/${MAX_TEAM_SIZE}`}
                                                </span>
                                            </button>

                                            <div style={{
                                                maxHeight: selected ? 360 : 0,
                                                opacity: selected ? 1 : 0,
                                                overflow: "hidden",
                                                transition: "max-height 0.22s ease, opacity 0.18s ease",
                                            }}>
                                                <div style={{ padding: "0 12px 14px 12px", display: "flex", flexDirection: "column", gap: 13 }}>
                                                    <div>
                                                        <div style={{ display: "flex", justifyContent: "space-between", color: C.green, fontFamily: mono, fontSize: 10, marginBottom: 6 }}>
                                                            <span>Members</span>
                                                            <span>{t.memberCount}/{MAX_TEAM_SIZE}</span>
                                                        </div>
                                                        <div style={{ height: 7, background: "rgba(15,23,42,0.9)", border: `1px solid ${C.border}`, overflow: "hidden" }}>
                                                            <div style={{
                                                                height: "100%",
                                                                width: `${fillPercent}%`,
                                                                background: `linear-gradient(90deg, ${C.green}, ${C.blue})`,
                                                                boxShadow: "0 0 14px rgba(34,197,94,0.35)",
                                                            }} />
                                                        </div>
                                                    </div>

                                                    <div style={{ display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: 8 }}>
                                                        <TeamMeta label="Event" value={t.eventName} />
                                                        <TeamMeta label="Track" value={t.trackName ?? "No track"} accent />
                                                        <TeamMeta label="Leader" value={t.leaderName ?? "—"} />
                                                        <TeamMeta label="Status" value={t.teamStatus} />
                                                    </div>

                                                    <PixelButton size="sm" variant={requested ? "ghost" : "cyber"} disabled={requested || busy === t.teamId} onClick={() => requestToJoin(t)}>
                                                        {requested ? "REQUEST ALREADY SENT" : busy === t.teamId ? "…" : "REQUEST TO JOIN"}
                                                    </PixelButton>
                                                </div>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </div>
                </div>
            </div>

            {acceptConfirmTarget && (
                <ConfirmDialog
                    title="Accept team invitation?"
                    message={
                        <>
                            Join <strong style={{ color: C.text }}>{acceptConfirmTarget.teamName}</strong>
                            {acceptConfirmTarget.invitedByName && (
                                <>
                                    {" "}from <strong style={{ color: C.text }}>{acceptConfirmTarget.invitedByName}</strong>
                                </>
                            )}
                            .
                            <span style={{ display: "block", marginTop: 8 }}>
                                {acceptConfirmTarget.eventName ?? "Event TBA"} · {acceptConfirmTarget.trackName ?? "Track TBA"}
                            </span>
                        </>
                    }
                    warning="After accepting, your account will be attached to this team for the event."
                    confirmLabel="ACCEPT INVITE"
                    variant="cyber"
                    working={busy === acceptConfirmTarget.inviteId}
                    error={error}
                    onConfirm={() => handleAccept(acceptConfirmTarget)}
                    onClose={() => {
                        if (busy == null) {
                            setAcceptConfirmTarget(null);
                            setError(null);
                        }
                    }}
                />
            )}
        </>
    );
}

function TeamMeta({ label, value, accent }: { label: string; value: string; accent?: boolean }) {
    return (
        <div style={{ border: `1px solid ${C.border}`, background: "rgba(15,23,42,0.5)", padding: "9px 10px", minWidth: 0 }}>
            <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 9, letterSpacing: "0.1em", textTransform: "uppercase", marginBottom: 4 }}>
                {label}
            </div>
            <div style={{ color: accent ? C.cyan : C.text, fontFamily: mono, fontSize: 11, fontWeight: 800, lineHeight: 1.4, overflowWrap: "anywhere" }}>
                {value}
            </div>
        </div>
    );
}
