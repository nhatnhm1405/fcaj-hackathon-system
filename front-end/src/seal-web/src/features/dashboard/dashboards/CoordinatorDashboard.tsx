import { useState, useEffect, useCallback } from "react";
import { useNavigate } from "react-router";
import { useAuth } from "@/app/providers/AuthProvider";
import { usePendingAccounts } from "@/app/providers/PendingAccountsProvider";
import { AnnouncementComposerModal } from "@/shared/components/AnnouncementComposerModal";
import {
  ContestCard, DashboardError, DashboardStage, MONO, TaskCard, TaskRow,
} from "@/features/dashboard/components/DashboardUI";
import { eventDateRange, phaseSummary, stepDates } from "@/features/dashboard/components/eventPhase";
import {
  eventsApi, roundsApi, teamsApi, submissionsApi, announcementsApi, accountApprovalsApi, ApiError,
  HackathonEvent, Team, AnnouncementItem, PendingAccount,
} from "@/shared/apiClient";

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
  const namesPreview = (names: string[]) => names.slice(0, 3).join(", ") + (names.length > 3 ? ", …" : "");
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
    <DashboardStage>
      {error && <DashboardError message={error} />}

      <ContestCard
        name={event?.name ?? "No open contest"}
        status={event?.status}
        summary={event ? phaseSummary(event.status, event) : undefined}
        dates={event ? stepDates(event.status, event) : undefined}
        facts={event ? [
          ...(event.topic ? [{ label: "Topic", value: event.topic }] : []),
          { label: "Dates", value: eventDateRange(event), mobileOnly: true },
          { label: "Approved teams", value: approvedTeams },
          { label: "Participants", value: participants },
          { label: "Submissions", value: totalSubs },
          roundCount === 0
            ? { label: "Rounds", value: "Not set up", attention: true }
            : { label: "Rounds closed", value: `${closedRounds} of ${roundCount}` },
        ] : []}
      />

      <TaskCard title="Your tasks">
        <TaskRow
          name="Accounts"
          state={pendingCount > 0 ? `${pendingCount} new account${pendingCount === 1 ? "" : "s"} waiting` : "None waiting"}
          attention={pendingCount > 0}
          more={pendingCount > 0 ? namesPreview(pendingAccounts.map(a => a.fullName)) : undefined}
          action={pendingCount > 0 ? "Review accounts" : "View accounts"}
          primary={pendingCount > 0}
          onAction={() => navigate('/coordinator/accounts')}
        />
        <TaskRow
          name="Teams"
          state={pendingTeams.length > 0 ? `${pendingTeams.length} new team${pendingTeams.length === 1 ? "" : "s"} waiting` : "None waiting"}
          attention={pendingTeams.length > 0}
          more={namesPreview(pendingTeams.map(t => t.name))}
          action={pendingTeams.length > 0 ? "Review teams" : "View teams"}
          primary={pendingTeams.length > 0}
          onAction={() => navigate('/coordinator/teams')}
        />
        <TaskRow
          name="Announcements"
          state={!event ? "Needs an open contest" : eventAnnHistory.length === 0 ? "None sent yet" : (
            <button type="button" className="dash-toggle" onClick={() => setAnnHistoryOpen(o => !o)} aria-expanded={annHistoryOpen}>
              {eventAnnHistory.length} sent, {annHistoryOpen ? "hide" : "show"}
            </button>
          )}
          action="Write announcement"
          onAction={() => setComposerOpen(true)}
          disabled={!event}
        />
        {event && annHistoryOpen && eventAnnHistory.map(a => (
          <div key={a.announcementId} className="dash-history" style={{ fontFamily: MONO }}>
            <div style={{ display: "flex", flexWrap: "wrap", gap: "2px 16px" }}>
              <b>{a.title}</b>
              <span>{fmtDT(a.createdAt)}</span>
            </div>
            <div style={{ whiteSpace: "pre-wrap", wordBreak: "break-word" }}>{a.content}</div>
            <div>{audienceLabel(a.audience)} · {a.scopeLabel} · sent to {a.recipientCount} recipient(s)</div>
          </div>
        ))}
        <TaskRow name="Contest setup" state="Rounds, tracks and dates" action="Manage events" onAction={() => navigate('/coordinator/events')} />
      </TaskCard>

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
    </DashboardStage>
  );
}
