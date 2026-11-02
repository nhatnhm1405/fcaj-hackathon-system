import { useState, useEffect, useMemo, useCallback } from "react";
import { List, ChevronDown, ChevronRight } from "lucide-react";
import {
  C, GradientText, PixelCard, PixelBadge, PixelButton,
} from "@/shared/components/PixelComponents";
import { AnnouncementComposerModal } from "@/shared/components/AnnouncementComposerModal";
import { TeamDetailModal } from "@/shared/components/TeamDetailModal";
import { assignmentsApi, announcementsApi, supportApi, ApiError, MentorAssignedTeam, MentorAssignedTrack, AnnouncementItem, SupportRequest, SupportCategory } from "@/shared/apiClient";

const mono = "'JetBrains Mono', monospace";

/** Short human label for a support-request category. */
function supportCatLabel(c: SupportCategory): string {
  switch (c) {
    case "RULES": return "Rules";
    case "TECHNICAL": return "Technical";
    case "DIRECTION": return "Direction";
    default: return "Other";
  }
}

function fmtDateTime(iso: string | null): string {
  if (!iso) return "";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  return `${String(d.getDate()).padStart(2, "0")}/${String(d.getMonth() + 1).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

/** One event the mentor is assigned to, derived from the flat team list. */
interface MentorEvent {
  eventId: number;
  eventName: string;
  season?: string;
  year?: number;
  eventStatus?: string;
}

/** Fields every event-bearing row (track or team) shares. */
type EventBearing = { eventId: number; eventName: string; season?: string; year?: number; eventStatus?: string };

/** Distinct events across the mentor's assigned rows, newest-ish first. */
function deriveEvents(rows: EventBearing[]): MentorEvent[] {
  const map = new Map<number, MentorEvent>();
  rows.forEach(r => {
    if (!map.has(r.eventId)) {
      map.set(r.eventId, {
        eventId: r.eventId, eventName: r.eventName,
        season: r.season, year: r.year, eventStatus: r.eventStatus,
      });
    }
  });
  return [...map.values()].sort((a, b) => (b.year ?? 0) - (a.year ?? 0) || b.eventId - a.eventId);
}

/** Default to the event the mentor is most likely working on right now. */
function pickDefaultEvent(events: MentorEvent[]): number | null {
  if (events.length === 0) return null;
  const up = (s?: string) => (s ?? "").toUpperCase();
  const active = events.find(e => up(e.eventStatus) === "IN_PROGRESS")
    ?? events.find(e => up(e.eventStatus) === "OPEN");
  return (active ?? events[0]).eventId;
}

/** Badge "đã nộp / chưa nộp" cho 1 team. */
function SubmissionBadge({ team }: { team: MentorAssignedTeam }) {
  return team.submissionCount > 0
    ? <PixelBadge color="green">SUBMITTED</PixelBadge>
    : <PixelBadge color="orange">NOT SUBMITTED</PixelBadge>;
}

/** Vòng team đang tham gia — hoặc "đã bị loại" tại vòng đó. */
function RoundStatus({ team }: { team: MentorAssignedTeam }) {
  if (!team.currentRoundName) return <span style={{ color: C.textDim, fontFamily: mono, fontSize: 11 }}>—</span>;
  if (team.eliminated) {
    return (
      <span style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
        <PixelBadge color="red">ELIMINATED</PixelBadge>
        <span style={{ color: C.textDim, fontFamily: mono, fontSize: 10 }}>{team.currentRoundName}</span>
      </span>
    );
  }
  return <PixelBadge color="cyan">{team.currentRoundName}</PixelBadge>;
}

export function MentorTracksPage() {
  const [teams, setTeams] = useState<MentorAssignedTeam[]>([]);
  const [tracks, setTracks] = useState<MentorAssignedTrack[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [selectedEventId, setSelectedEventId] = useState<number | null>(null);
  // The team whose full-detail modal is open (null = closed).
  const [detailTeam, setDetailTeam] = useState<MentorAssignedTeam | null>(null);

  // The track whose announcement composer is currently open (null = closed).
  const [composerTrack, setComposerTrack] = useState<{ trackId: number; trackName: string; teamCount: number } | null>(null);
  const [history, setHistory] = useState<AnnouncementItem[]>([]);
  const [supportReqs, setSupportReqs] = useState<SupportRequest[]>([]);
  const [resolvingId, setResolvingId] = useState<number | null>(null);

  const loadHistory = useCallback(() => {
    announcementsApi.listMentor()
      .then(res => setHistory(res.data ?? []))
      .catch(() => { /* non-blocking */ });
  }, []);

  const loadSupport = useCallback(() => {
    supportApi.listForMentor()
      .then(res => setSupportReqs(res.data ?? []))
      .catch(() => { /* non-blocking */ });
  }, []);

  const resolveRequest = useCallback((requestId: number) => {
    setResolvingId(requestId);
    supportApi.resolve(requestId)
      .then(() => loadSupport())
      .finally(() => setResolvingId(null));
  }, [loadSupport]);

  useEffect(() => {
    setLoading(true);
    setError(null);
    assignmentsApi.getMentorAssignments()
      .then(res => {
        setTeams(res.data?.teams ?? []);
        setTracks(res.data?.tracks ?? []);
      })
      .catch(err => setError(err instanceof ApiError ? err.message : "Failed to load mentor assignments."))
      .finally(() => setLoading(false));
    loadHistory();
    loadSupport();
  }, [loadHistory, loadSupport]);

  // All assigned tracks (incl. empty ones). Fall back to synthesising them from
  // the team list if an older backend doesn't return the `tracks` field.
  const assignedTracks = useMemo<MentorAssignedTrack[]>(() => {
    if (tracks.length > 0) return tracks;
    const map = new Map<number, MentorAssignedTrack>();
    teams.forEach(t => {
      if (!map.has(t.trackId)) {
        map.set(t.trackId, {
          trackId: t.trackId, trackName: t.trackName,
          eventId: t.eventId, eventName: t.eventName,
          season: t.season, year: t.year, eventStatus: t.eventStatus,
        });
      }
    });
    return [...map.values()];
  }, [tracks, teams]);

  // Events come from the assigned tracks, so an event whose track has no approved
  // team yet still shows up in the dropdown.
  const events = useMemo(() => deriveEvents(assignedTracks), [assignedTracks]);

  // Pick a sensible default event once assignments arrive.
  useEffect(() => {
    if (selectedEventId == null && events.length > 0) {
      setSelectedEventId(pickDefaultEvent(events));
    }
  }, [events, selectedEventId]);

  // Tracks in the selected event, each with its (possibly empty) team list.
  const trackGroups = useMemo(() => {
    if (selectedEventId == null) return [];
    return assignedTracks
      .filter(tr => tr.eventId === selectedEventId)
      .map(tr => ({
        trackId: tr.trackId,
        trackName: tr.trackName,
        teams: teams.filter(t => t.trackId === tr.trackId),
      }));
  }, [assignedTracks, teams, selectedEventId]);

  function switchEvent(id: number) {
    setSelectedEventId(id);
    setDetailTeam(null);
  }

  if (loading) {
    return <div style={{ padding: 24 }}><PixelCard style={{ padding: 32, textAlign: "center" }}>
      <p style={{ color: C.textMuted, fontFamily: mono, fontSize: 13 }}>Loading...</p>
    </PixelCard></div>;
  }

  if (error) {
    return <div style={{ padding: 24 }}>
      <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: mono, fontSize: 11, padding: "10px 14px" }}>ERROR: {error}</div>
    </div>;
  }

  if (events.length === 0) {
    return <div style={{ padding: 24 }}><PixelCard style={{ padding: 32, textAlign: "center" }}>
      <p style={{ color: C.textMuted, fontFamily: mono, fontSize: 13 }}>No tracks assigned to you.</p>
    </PixelCard></div>;
  }

  return (
    <div style={{ padding: 24, display: "flex", flexDirection: "column", gap: 20 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", gap: 12, flexWrap: "wrap" }}>
        <h1 style={{ fontFamily: mono, fontSize: 28, fontWeight: 800 }}>
          <GradientText>My Tracks</GradientText>
        </h1>
        <div>
          <label style={{ color: C.greenMuted, fontFamily: mono, fontSize: 11, letterSpacing: "0.1em", textTransform: "uppercase" }}>Event</label>
          <select
            value={selectedEventId ?? 0}
            onChange={e => switchEvent(Number(e.target.value))}
            style={{ marginTop: 6, padding: "10px 12px", background: C.surface2, border: `1px solid ${C.border}`, color: C.text, fontFamily: mono, fontSize: 12, width: 260, display: "block", outline: "none", borderRadius: 0 }}
          >
            {events.map(ev => (
              <option key={ev.eventId} value={ev.eventId}>
                {ev.eventName}{ev.eventStatus ? ` · ${ev.eventStatus}` : ""}
              </option>
            ))}
          </select>
        </div>
      </div>

      {trackGroups.length === 0 && (
        <PixelCard style={{ padding: 28, textAlign: "center" }}>
          <p style={{ color: C.textMuted, fontFamily: mono, fontSize: 13 }}>No tracks assigned to you in this event.</p>
        </PixelCard>
      )}

      {trackGroups.map(group => (
        <TrackSection
          key={group.trackId}
          trackName={group.trackName}
          teams={group.teams}
          history={history}
          requests={supportReqs.filter(r => r.trackId === group.trackId)}
          resolvingId={resolvingId}
          onResolve={resolveRequest}
          onOpenTeam={setDetailTeam}
          onAnnounce={() => setComposerTrack({ trackId: group.trackId, trackName: group.trackName, teamCount: group.teams.length })}
        />
      ))}

      {detailTeam && (
        <TeamDetailModal
          open
          teamName={detailTeam.teamName}
          infoRows={[
            { label: "Round", value: <RoundStatus team={detailTeam} /> },
            {
              label: "Status",
              value: (
                <>
                  <SubmissionBadge team={detailTeam} />
                  {detailTeam.submissionCount > 0 && (
                    <span style={{ color: C.textMuted, fontFamily: mono, fontSize: 11 }}>
                      {detailTeam.submissionCount} submission{detailTeam.submissionCount > 1 ? "s" : ""}
                      {detailTeam.lastSubmittedAt ? ` · last ${fmtDateTime(detailTeam.lastSubmittedAt)}` : ""}
                    </span>
                  )}
                </>
              ),
            },
            { label: "Track", value: detailTeam.trackName },
            { label: "Members", value: detailTeam.members.length },
          ]}
          members={detailTeam.members}
          onClose={() => setDetailTeam(null)}
        />
      )}

      {composerTrack && (
        <AnnouncementComposerModal
          open
          scopeLabel={composerTrack.trackName}
          audienceHint={`${composerTrack.teamCount} team(s) in this track`}
          onSend={(title, content, linkUrl) =>
            announcementsApi.createMentor({ trackId: composerTrack.trackId, title, content, linkUrl })
              .then(res => res.data?.recipientCount ?? 0)}
          onSent={loadHistory}
          onClose={() => setComposerTrack(null)}
        />
      )}
    </div>
  );
}

/** One track: header + team table + this track's sent-announcement history. */
function TrackSection({
  trackName, teams, history, requests, resolvingId, onResolve, onOpenTeam, onAnnounce,
}: {
  trackName: string;
  teams: MentorAssignedTeam[];
  history: AnnouncementItem[];
  requests: SupportRequest[];
  resolvingId: number | null;
  onResolve: (requestId: number) => void;
  onOpenTeam: (team: MentorAssignedTeam) => void;
  onAnnounce: () => void;
}) {
  const submittedCount = teams.filter(t => t.submissionCount > 0).length;
  const trackHistory = history.filter(h => h.scope === "TRACK" && h.scopeLabel === trackName);
  const openRequests = requests.filter(r => r.status === "OPEN");
  const [hoveredTeamId, setHoveredTeamId] = useState<number | null>(null);
  // Sent-announcement history is collapsed by default to reduce clutter.
  const [historyOpen, setHistoryOpen] = useState(false);
  // Support requests: expanded by default when there is something open to handle.
  const [reqOpen, setReqOpen] = useState(false);

  return (
    <PixelCard style={{ padding: 0, overflow: "hidden" }}>
      {/* Section header */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap", padding: "16px 18px", borderBottom: `1px solid ${C.border}`, background: C.surface2 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
          <span style={{ color: C.text, fontFamily: mono, fontSize: 16, fontWeight: 700 }}>{trackName}</span>
          <PixelBadge color="blue">{teams.length} TEAM{teams.length === 1 ? "" : "S"}</PixelBadge>
          <span style={{ color: C.textMuted, fontFamily: mono, fontSize: 11 }}>{submittedCount}/{teams.length} submitted</span>
        </div>
        <PixelButton variant="cyber" disabled={teams.length === 0} onClick={onAnnounce}>
          ANNOUNCE TO {trackName}
        </PixelButton>
      </div>

      {/* Team table */}
      <div style={{ overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontFamily: mono }}>
          <thead>
            <tr style={{ background: C.surface, borderBottom: `1px solid ${C.border}` }}>
              {["Team", "Leader", "Members", "Round", "Submission"].map(h => (
                <th key={h} style={{ color: C.green, fontSize: 10, letterSpacing: "0.12em", textAlign: "left", padding: "11px 16px", fontWeight: 600, textTransform: "uppercase" }}>{h}</th>
              ))}
              <th style={{ width: 48, padding: "11px 16px" }} aria-label="Details" />
            </tr>
          </thead>
          <tbody>
            {teams.length === 0 && (
              <tr><td colSpan={6} style={{ padding: 18, color: C.textMuted, fontSize: 12, textAlign: "center" }}>No teams in this track.</td></tr>
            )}
            {teams.map((team, i) => {
              const leader = team.members.find(m => m.memberRole === "LEADER");
              const hovered = hoveredTeamId === team.teamId;
              return (
                <tr
                  key={team.teamId}
                  onClick={() => onOpenTeam(team)}
                  onMouseEnter={() => setHoveredTeamId(team.teamId)}
                  onMouseLeave={() => setHoveredTeamId(prev => (prev === team.teamId ? null : prev))}
                  title="View team & member details"
                  style={{ borderBottom: `1px solid rgba(34,197,94,0.06)`, background: hovered ? "rgba(34,197,94,0.08)" : i % 2 === 0 ? C.surface : C.surface2, cursor: "pointer", transition: "background 0.12s" }}
                >
                  <td style={{ color: hovered ? C.green : C.text, fontSize: 13, fontWeight: 600, padding: "12px 16px", transition: "color 0.12s" }}>
                    {team.teamName}
                  </td>
                  <td style={{ color: C.textMuted, fontSize: 12, padding: "12px 16px" }}>{leader?.fullName ?? "—"}</td>
                  <td style={{ color: C.textMuted, fontSize: 12, padding: "12px 16px" }}>{team.members.length}</td>
                  <td style={{ padding: "12px 16px" }}><RoundStatus team={team} /></td>
                  <td style={{ padding: "12px 16px" }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
                      <SubmissionBadge team={team} />
                      {team.submissionCount > 0 && team.lastSubmittedAt && (
                        <span style={{ color: C.textDim, fontSize: 10 }}>last {fmtDateTime(team.lastSubmittedAt)}</span>
                      )}
                    </div>
                  </td>
                  {/* Details button — appears only while hovering this team row. */}
                  <td style={{ padding: "8px 12px", textAlign: "right" }}>
                    <span
                      title="View details"
                      style={{
                        display: "inline-flex", alignItems: "center", justifyContent: "center",
                        width: 30, height: 30,
                        border: `1px solid ${C.green}`,
                        background: "rgba(34,197,94,0.14)",
                        color: C.green,
                        opacity: hovered ? 1 : 0,
                        pointerEvents: hovered ? "auto" : "none",
                        transition: "opacity 0.12s",
                      }}
                    >
                      <List size={15} strokeWidth={2.25} />
                    </span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* Support requests from teams in this track — collapsible */}
      <div style={{ padding: "14px 18px", borderTop: `1px solid ${C.border}` }}>
        <button
          type="button"
          onClick={() => setReqOpen(o => !o)}
          style={{
            display: "flex", alignItems: "center", gap: 8, width: "100%",
            background: "none", border: "none", cursor: "pointer", padding: 0,
            color: openRequests.length > 0 ? "#eab308" : C.green, fontFamily: mono, fontSize: 11, fontWeight: 700, letterSpacing: "0.05em", textAlign: "left",
          }}
        >
          <span style={{ display: "inline-flex", color: "inherit" }}>
            {reqOpen ? <ChevronDown size={15} strokeWidth={2.5} /> : <ChevronRight size={15} strokeWidth={2.5} />}
          </span>
          <span>SUPPORT REQUESTS · {trackName}</span>
          {openRequests.length > 0
            ? <PixelBadge color="orange">{openRequests.length} OPEN</PixelBadge>
            : <PixelBadge color="gray">{requests.length}</PixelBadge>}
        </button>
        {reqOpen && (
          requests.length === 0 ? (
            <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 11, marginTop: 10 }}>
              No support requests from this track yet.
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 8, marginTop: 10 }}>
              {requests.map(r => (
                <div key={r.requestId} style={{ padding: "10px 12px", background: C.surface2, border: `1px solid ${r.status === "OPEN" ? "rgba(234,179,8,0.4)" : C.border}` }}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
                      <span style={{ color: C.text, fontFamily: mono, fontSize: 12, fontWeight: 700 }}>{r.teamName}</span>
                      <PixelBadge color="cyan">{supportCatLabel(r.category)}</PixelBadge>
                      <PixelBadge color={r.status === "OPEN" ? "orange" : r.status === "RESOLVED" ? "green" : "gray"}>{r.status}</PixelBadge>
                    </div>
                    <span style={{ color: C.textDim, fontFamily: mono, fontSize: 10, whiteSpace: "nowrap" }}>{fmtDateTime(r.createdAt)}</span>
                  </div>
                  <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 11, lineHeight: 1.6, marginTop: 4, whiteSpace: "pre-wrap", wordBreak: "break-word" }}>{r.description}</div>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10, marginTop: 8, flexWrap: "wrap" }}>
                    <span style={{ color: C.textDim, fontFamily: mono, fontSize: 10 }}>from {r.requesterName ?? "—"}</span>
                    {r.status === "OPEN" && (
                      <PixelButton size="sm" variant="cyber" disabled={resolvingId === r.requestId} onClick={() => onResolve(r.requestId)}>
                        {resolvingId === r.requestId ? "..." : "MARK RESOLVED"}
                      </PixelButton>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )
        )}
      </div>

      {/* Sent announcements for this track — collapsible to reduce clutter */}
      <div style={{ padding: "14px 18px", borderTop: `1px solid ${C.border}` }}>
        <button
          type="button"
          onClick={() => setHistoryOpen(o => !o)}
          style={{
            display: "flex", alignItems: "center", gap: 8, width: "100%",
            background: "none", border: "none", cursor: "pointer", padding: 0,
            color: C.green, fontFamily: mono, fontSize: 11, fontWeight: 700, letterSpacing: "0.05em",
            textAlign: "left",
          }}
        >
          <span style={{ display: "inline-flex", color: C.green }}>
            {historyOpen ? <ChevronDown size={15} strokeWidth={2.5} /> : <ChevronRight size={15} strokeWidth={2.5} />}
          </span>
          <span>SENT ANNOUNCEMENTS · {trackName}</span>
          <PixelBadge color="gray">{trackHistory.length}</PixelBadge>
        </button>
        {historyOpen && (
          trackHistory.length === 0 ? (
            <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 11, marginTop: 10 }}>
              No announcements sent to this track yet.
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 8, marginTop: 10 }}>
              {trackHistory.map(a => (
                <div key={a.announcementId} style={{ padding: "10px 12px", background: C.surface2, border: `1px solid ${C.border}` }}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10 }}>
                    <span style={{ color: C.text, fontFamily: mono, fontSize: 12, fontWeight: 700 }}>{a.title}</span>
                    <span style={{ color: C.textDim, fontFamily: mono, fontSize: 10, whiteSpace: "nowrap" }}>{fmtDateTime(a.createdAt)}</span>
                  </div>
                  <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 11, lineHeight: 1.6, marginTop: 4, whiteSpace: "pre-wrap", wordBreak: "break-word" }}>{a.content}</div>
                  <div style={{ color: C.textDim, fontFamily: mono, fontSize: 10, marginTop: 6 }}>Sent to {a.recipientCount} participant(s)</div>
                </div>
              ))}
            </div>
          )
        )}
      </div>
    </PixelCard>
  );
}
