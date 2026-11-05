import { useState, useEffect, useCallback } from "react";
import { useNavigate } from "react-router";
import { useAuth } from "@/app/providers/AuthProvider";
import { usePendingAccounts } from "@/app/providers/PendingAccountsProvider";
import {
  C, GradientText, PixelCard, PixelButton, PixelBadge, CyberStatCard, PixelProgress,
} from "@/shared/components/PixelComponents";
import { AnnouncementComposerModal } from "@/shared/components/AnnouncementComposerModal";
import { EventName } from "@/features/events/eventUtils";
import {
  eventsApi, roundsApi, teamsApi, submissionsApi, announcementsApi, accountApprovalsApi, ApiError,
  HackathonEvent, Team, AnnouncementItem, PendingAccount,
} from "@/shared/apiClient";

const MONO = "'JetBrains Mono', monospace";

function fmtDT(iso?: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  return `${String(d.getDate()).padStart(2, "0")}/${String(d.getMonth() + 1).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

function audienceLabel(a?: string | null): string {
  if (a === "JUDGE") return "Judges";
  if (a === "MENTOR") return "Mentors";
  if (a === "PARTICIPANT") return "Participants";
  if (a === "ALL") return "Everyone";
  return a ?? "—";
}

// Event Overview shows only an on-going event. Prefer a running one (IN_PROGRESS),
// then an active one (OPEN / SETUP). DRAFT, COMPLETED and CANCELLED events are
// never surfaced here — return null so the overview stays hidden instead.
function pickDefaultEvent(events: HackathonEvent[]): HackathonEvent | null {
  return events.find(e => e.status === 'IN_PROGRESS')
    ?? events.find(e => e.status === 'OPEN' || e.status === 'SETUP')
    ?? null;
}

// Distinct, vivid badge colour per status so SETUP stands out (not muted grey).
function statusBadgeColor(status: string): "green" | "cyan" | "blue" | "gray" | "purple" | "red" {
  switch (status) {
    case 'IN_PROGRESS': return 'green';
    case 'OPEN':        return 'cyan';
    case 'SETUP':       return 'blue';
    case 'DRAFT':       return 'gray';
    case 'COMPLETED':   return 'purple';
    default:            return 'red';   // CANCELLED
  }
}

export function CoordinatorDashboard() {
  const navigate = useNavigate();
  const { currentUser } = useAuth();
  const { pendingCount } = usePendingAccounts();

  const [events, setEvents] = useState<HackathonEvent[]>([]);
  const [event, setEvent] = useState<HackathonEvent | null>(null);
  const [teams, setTeams] = useState<Team[]>([]);
  const [roundCount, setRoundCount] = useState(0);
  const [closedRounds, setClosedRounds] = useState(0);
  const [totalSubs, setTotalSubs] = useState(0);
  const [error, setError] = useState<string | null>(null);

  // Pending queue preview (Needs Attention) — display-only; actions live on the
  // dedicated Accounts / Teams pages (this dashboard just links there).
  const [pendingAccounts, setPendingAccounts] = useState<PendingAccount[]>([]);

  const [composerOpen, setComposerOpen] = useState(false);
  const [annHistory, setAnnHistory] = useState<AnnouncementItem[]>([]);
  // Sent-announcement history is collapsed by default to reduce clutter.
  const [annHistoryOpen, setAnnHistoryOpen] = useState(false);
  const [audience, setAudience] = useState("PARTICIPANT");
  // Which event the announcement targets (coordinator can change it in the composer).
  const [announceEventId, setAnnounceEventId] = useState<number | null>(null);

  const loadAnnHistory = useCallback(() => {
    announcementsApi.listCoordinator()
      .then(res => setAnnHistory(res.data ?? []))
      .catch(() => { /* non-blocking */ });
  }, []);

  const loadPendingAccounts = useCallback(() => {
    accountApprovalsApi.getPending()
      .then(res => setPendingAccounts(res.data ?? []))
      .catch(() => setPendingAccounts([]));
  }, []);

  useEffect(() => {
    eventsApi.getAll()
      .then(res => {
        const evs = res.data ?? [];
        setEvents(evs);
        setEvent(pickDefaultEvent(evs));
      })
      .catch(err => setError(err instanceof ApiError ? err.message : "Failed to load events."));
    loadAnnHistory();
    loadPendingAccounts();
  }, [loadAnnHistory, loadPendingAccounts]);

  useEffect(() => {
    if (!event) { setTeams([]); setRoundCount(0); setClosedRounds(0); setTotalSubs(0); return; }
    const eventId = event.eventId;

    teamsApi.getByEvent(eventId).then(res => setTeams(res.data ?? [])).catch(() => setTeams([]));

    roundsApi.getAll(eventId)
      .then(res => {
        const rs = res.data ?? [];
        setRoundCount(rs.length);
        setClosedRounds(rs.filter(r => r.status === 'CLOSED' || r.status === 'COMPLETED').length);
        // Total submissions across the event = sum per round.
        return Promise.all(rs.map(r =>
          submissionsApi.getAllForRound(r.roundId).then(sr => (sr.data ?? []).length).catch(() => 0)));
      })
      .then(counts => setTotalSubs((counts ?? []).reduce((a, b) => a + b, 0)))
      .catch(() => { setRoundCount(0); setClosedRounds(0); setTotalSubs(0); });
  }, [event]);

  if (!currentUser) return null;

  const approvedTeamList = teams.filter(t => t.status === 'APPROVED');
  const approvedTeams = approvedTeamList.length;
  const participants = approvedTeamList.reduce((n, t) => n + (t.members?.length ?? 0), 0);
  const pendingTeams = teams.filter(t => t.status === 'PENDING');
  const attentionCount = pendingCount + pendingTeams.length;
  // Announcements can only target a live event (SETUP / OPEN / IN_PROGRESS) — the
  // composer's event picker is limited to these.
  const announceableEvents = events.filter(e =>
    e.status === 'SETUP' || e.status === 'OPEN' || e.status === 'IN_PROGRESS');
  // Sent history is scoped to the event currently shown in the overview.
  const eventAnnHistory = event ? annHistory.filter(a => a.eventId === event.eventId) : [];
  // Event the announce composer targets — the dropdown choice, falling back to the
  // overview event when the coordinator hasn't picked one yet.
  const announceEvent = events.find(e => e.eventId === announceEventId) ?? event;

  return (
    <div style={{ padding: 24, display: "flex", flexDirection: "column", gap: 20 }}>
      <PixelCard glow gradient style={{ padding: 24 }}>
        <div style={{ color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, letterSpacing: "0.15em", marginBottom: 6 }}>
          COORDINATOR CONSOLE
        </div>
        <h1 style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 26, fontWeight: 800, lineHeight: 1.2 }}>
          <GradientText>Good day, {currentUser.full_name}</GradientText>
        </h1>
      </PixelCard>

      {error && (
        <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, padding: "10px 14px" }}>
          ERROR: {error}
        </div>
      )}

      {/* KPI strip — event-scoped, flat (glow off) */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 16 }}>
        <CyberStatCard glow={false} value={approvedTeams} label="Teams" accent="blue" />
        <CyberStatCard glow={false} value={participants} label="Participants" accent="green" />
        <CyberStatCard glow={false} value={totalSubs} label="Submissions" accent="cyan" />
        <CyberStatCard glow={false} value={roundCount} label="Rounds" accent="purple" />
      </div>

      {/* Event overview — below the KPI strip */}
      {event && (
        <PixelCard style={{ padding: 20 }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, marginBottom: 12, flexWrap: "wrap" }}>
            <div style={{ color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, letterSpacing: "0.15em" }}>
              EVENT OVERVIEW
            </div>
            <PixelButton variant="secondary" size="sm" onClick={() => navigate('/coordinator/events')}>
              MANAGE →
            </PixelButton>
          </div>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 4 }}>
            <EventName size={20}>{event.name}</EventName>
            <PixelBadge color={statusBadgeColor(event.status)}>
              {event.status}
            </PixelBadge>
          </div>
          {event.topic && (
            <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, marginBottom: 12 }}>{event.topic}</div>
          )}
          <PixelProgress value={closedRounds} max={roundCount || 1} label="Round progress" gradient />
        </PixelCard>
      )}

      {/* Announcements — moved up for quick access */}
      <PixelCard style={{ padding: 20 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, marginBottom: 12, flexWrap: "wrap" }}>
          <div style={{ color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, letterSpacing: "0.15em" }}>
            ANNOUNCEMENTS
          </div>
          <PixelButton
            variant="cyber"
            onClick={() => setComposerOpen(true)}
            disabled={!event}
          >
            ANNOUNCE TO EVENT
          </PixelButton>
        </div>
        {!event ? (
          <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>Select an event first.</div>
        ) : eventAnnHistory.length === 0 ? (
          <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>
            No announcements sent yet.
          </div>
        ) : (
          <>
            <button
              type="button"
              onClick={() => setAnnHistoryOpen(o => !o)}
              style={{
                display: "flex", alignItems: "center", gap: 8, width: "100%",
                background: "none", border: "none", cursor: "pointer", padding: 0, marginBottom: annHistoryOpen ? 10 : 0,
                color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, letterSpacing: "0.08em", textAlign: "left",
              }}
            >
              <span style={{ fontSize: 12 }}>{annHistoryOpen ? "▾" : "▸"}</span>
              <span>SENT HISTORY · {eventAnnHistory.length}</span>
            </button>
            {annHistoryOpen && (
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {eventAnnHistory.map(a => (
              <div key={a.announcementId} style={{ padding: "10px 12px", background: C.surface2, border: `1px solid ${C.border}` }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10 }}>
                  <span style={{ color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, fontWeight: 700 }}>{a.title}</span>
                  <span style={{ color: C.textDim, fontFamily: "'JetBrains Mono', monospace", fontSize: 10, whiteSpace: "nowrap" }}>
                    {fmtDT(a.createdAt)}
                  </span>
                </div>
                <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, lineHeight: 1.6, marginTop: 4, whiteSpace: "pre-wrap", wordBreak: "break-word" }}>{a.content}</div>
                <div style={{ color: C.textDim, fontFamily: "'JetBrains Mono', monospace", fontSize: 10, marginTop: 6 }}>
                  {audienceLabel(a.audience)} · {a.scopeLabel} · sent to {a.recipientCount} recipient(s)
                </div>
              </div>
            ))}
          </div>
            )}
          </>
        )}
      </PixelCard>

      {/* Needs Attention — radar + entry point; actions on dedicated pages */}
      <PixelCard glowColor="amber" style={{ padding: 20 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
          <div style={{ color: C.yellow, fontFamily: MONO, fontSize: 11, letterSpacing: "0.15em" }}>
            NEEDS ATTENTION
          </div>
          {attentionCount > 0 && <PixelBadge color="yellow">{attentionCount} PENDING</PixelBadge>}
        </div>

        {attentionCount === 0 ? (
          <div style={{ color: C.textMuted, fontFamily: MONO, fontSize: 13, display: "flex", alignItems: "center", gap: 8, padding: "6px 0" }}>
            <span style={{ color: C.green, fontSize: 16 }}></span>All caught up — no pending accounts or teams.
          </div>
        ) : (
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: 20 }}>
            {/* Accounts */}
            <div>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
                <span style={{ color: C.purple, fontFamily: MONO, fontSize: 11, letterSpacing: "0.1em" }}>ACCOUNTS</span>
                {pendingCount > 0 && (
                  <PixelButton variant="secondary" size="sm" onClick={() => navigate('/coordinator/accounts')}>Review →</PixelButton>
                )}
              </div>
              {pendingAccounts.length === 0 ? (
                <div style={emptyRow}>No pending accounts.</div>
              ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                  {pendingAccounts.slice(0, 4).map(a => (
                    <div key={a.userId} style={queueRow}>
                      <div style={{ minWidth: 0, flex: 1 }}>
                        <div style={{ color: C.text, fontFamily: MONO, fontSize: 12, fontWeight: 700, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{a.fullName}</div>
                        <div style={{ color: C.textDim, fontFamily: MONO, fontSize: 10, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{a.email} · {a.userType.replace('_', ' ')}</div>
                      </div>
                    </div>
                  ))}
                  {pendingCount > 4 && <div style={{ color: C.textDim, fontFamily: MONO, fontSize: 10 }}>+{pendingCount - 4} more</div>}
                </div>
              )}
            </div>

            {/* Teams */}
            <div>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
                <span style={{ color: C.cyan, fontFamily: MONO, fontSize: 11, letterSpacing: "0.1em" }}>TEAMS</span>
                {pendingTeams.length > 0 && (
                  <PixelButton variant="secondary" size="sm" onClick={() => navigate('/coordinator/teams')}>Review →</PixelButton>
                )}
              </div>
              {pendingTeams.length === 0 ? (
                <div style={emptyRow}>{event ? "No pending teams." : "Select an active event."}</div>
              ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                  {pendingTeams.slice(0, 4).map(t => (
                    <div key={t.teamId} style={queueRow}>
                      <div style={{ minWidth: 0, flex: 1 }}>
                        <div style={{ color: C.text, fontFamily: MONO, fontSize: 12, fontWeight: 700, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{t.name}</div>
                        <div style={{ color: C.textDim, fontFamily: MONO, fontSize: 10 }}>{t.trackName ?? "—"} · {t.members?.length ?? 0} members</div>
                      </div>
                    </div>
                  ))}
                  {pendingTeams.length > 4 && <div style={{ color: C.textDim, fontFamily: MONO, fontSize: 10 }}>+{pendingTeams.length - 4} more</div>}
                </div>
              )}
            </div>
          </div>
        )}
      </PixelCard>

      {announceEvent && (
        <AnnouncementComposerModal
          open={composerOpen}
          scopeLabel={announceEvent.name}
          events={announceableEvents.map(e => ({ value: e.eventId, label: `${e.name} (${e.status})` }))}
          eventId={announceEvent.eventId}
          onEventChange={setAnnounceEventId}
          audiences={[
            { value: "PARTICIPANT", label: "Participants" },
            { value: "JUDGE", label: "Judges" },
            { value: "MENTOR", label: "Mentors" },
            { value: "ALL", label: "Everyone" },
          ]}
          audience={audience}
          onAudienceChange={setAudience}
          onSend={(title, content, linkUrl) =>
            announcementsApi.createCoordinator({ eventId: announceEvent.eventId, audience, title, content, linkUrl })
              .then(res => res.data?.recipientCount ?? 0)}
          onSent={loadAnnHistory}
          onClose={() => setComposerOpen(false)}
        />
      )}
    </div>
  );
}

// ── Needs Attention: shared read-only row styles ──
const queueRow: React.CSSProperties = {
  display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10,
  padding: "8px 10px", background: C.surface2, border: `1px solid ${C.border}`,
};
const emptyRow: React.CSSProperties = { color: C.textDim, fontFamily: MONO, fontSize: 11, padding: "8px 0" };
