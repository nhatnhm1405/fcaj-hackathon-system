import { useState, useEffect, useMemo } from "react";
import { useNavigate } from "react-router";
import { useAuth } from "@/app/providers/AuthProvider";
import {
  ContestCard, DashboardError, DashboardStage, TaskCard, TaskNote, TaskRow,
} from "@/features/dashboard/components/DashboardUI";
import { contestTimerText, eventDateRange, phaseSummary, stepDates } from "@/features/dashboard/components/eventPhase";
import { assignmentsApi, eventsApi, roundsApi, ApiError, HackathonEvent, MentorAssignedTeam, MentorAssignedTrack, Round } from "@/shared/apiClient";
import { useRoundTimer } from "@/shared/hooks/useRoundTimer";

export function MentorDashboard() {
  const navigate = useNavigate();
  const { currentUser } = useAuth();

  const [allTeams, setAllTeams] = useState<MentorAssignedTeam[]>([]);
  const [allTracks, setAllTracks] = useState<MentorAssignedTrack[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    assignmentsApi.getMentorAssignments()
      .then(res => {
        setAllTeams(res.data?.teams ?? []);
        setAllTracks(res.data?.tracks ?? []);
      })
      .catch(err => setError(err instanceof ApiError ? err.message : "Failed to load mentor assignments."))
      .finally(() => setLoading(false));
  }, []);

  // Assigned tracks including empty ones; fall back to synthesising from teams
  // if an older backend omits the `tracks` field.
  const assignedTracks = useMemo<MentorAssignedTrack[]>(() => {
    if (allTracks.length > 0) return allTracks;
    const map = new Map<number, MentorAssignedTrack>();
    allTeams.forEach(t => {
      if (!map.has(t.trackId)) map.set(t.trackId, { trackId: t.trackId, trackName: t.trackName, eventId: t.eventId, eventName: t.eventName, season: t.season, year: t.year, eventStatus: t.eventStatus });
    });
    return [...map.values()];
  }, [allTracks, allTeams]);

  // A mentor may be assigned across several seasons; scope the dashboard to the
  // event they are most likely working on now (IN_PROGRESS → OPEN → newest).
  const activeEvent = useMemo(() => {
    const map = new Map<number, { eventId: number; eventName: string; year?: number; eventStatus?: string }>();
    assignedTracks.forEach(tr => {
      if (!map.has(tr.eventId)) map.set(tr.eventId, { eventId: tr.eventId, eventName: tr.eventName, year: tr.year, eventStatus: tr.eventStatus });
    });
    const events = [...map.values()].sort((a, b) => (b.year ?? 0) - (a.year ?? 0) || b.eventId - a.eventId);
    if (events.length === 0) return null;
    const up = (s?: string) => (s ?? "").toUpperCase();
    return events.find(e => up(e.eventStatus) === "IN_PROGRESS")
      ?? events.find(e => up(e.eventStatus) === "OPEN")
      ?? events[0];
  }, [assignedTracks]);

  const eventName = activeEvent?.eventName ?? "";
  const teams = useMemo(
    () => (activeEvent ? allTeams.filter(t => t.eventId === activeEvent.eventId) : []),
    [allTeams, activeEvent],
  );

  const { event: resolvedEvent, round, timer } = useMentorContest(eventName);
  // Describe only the assigned contest, never the fallback used to find a timer.
  const event = resolvedEvent?.name === eventName ? resolvedEvent : null;

  if (!currentUser) return null;

  // The active event's assigned tracks (incl. empty ones), each with its teams.
  const eventTracks = activeEvent ? assignedTracks.filter(tr => tr.eventId === activeEvent.eventId) : [];
  const byTrack = new Map<string, MentorAssignedTeam[]>();
  eventTracks.forEach(tr => byTrack.set(tr.trackName, teams.filter(t => t.trackId === tr.trackId)));

  const submitted = teams.filter(t => t.submissionCount > 0).length;
  // Teams that still need help come first.
  const orderedTeams = [...teams].sort((a, b) => Number(isWaiting(b)) - Number(isWaiting(a)) || a.teamName.localeCompare(b.teamName));
  const waiting = teams.filter(isWaiting).length;
  const status = event?.status ?? activeEvent?.eventStatus;
  const ended = (status ?? "").toUpperCase() === "COMPLETED";
  const goTracks = () => navigate('/mentor/tracks');

  const summary = !status ? undefined
    : event && timer.isConfigured && !ended ? `${phaseSummary(status)}, ${contestTimerText(timer.status, timer.remainingSeconds)}`
    : phaseSummary(status, event ?? {});

  return (
    <DashboardStage>
      {error && <DashboardError message={error} />}

      <ContestCard
        name={eventName || (loading ? "Loading…" : "No contest assigned")}
        status={status}
        summary={summary}
        dates={event ? stepDates(event.status, event) : undefined}
        facts={!activeEvent ? [] : [
          ...(event?.topic ? [{ label: "Topic", value: event.topic }] : []),
          ...(event ? [{ label: "Dates", value: eventDateRange(event), mobileOnly: true }] : []),
          ...[...byTrack.entries()].map(([trackName, trackTeams]) => ({
            label: byTrack.size === 1 ? "Track" : `Track ${trackName}`,
            value: byTrack.size === 1 ? trackName : `${trackTeams.length} teams`,
          })),
          ...(event && round && !ended ? [{ label: "Round", value: round.name }] : []),
        ]}
      />

      <TaskCard title="Your teams">
        {loading && <TaskNote>Loading…</TaskNote>}
        {!loading && byTrack.size === 0 && !error && <TaskNote>No tracks assigned yet</TaskNote>}
        {byTrack.size > 0 && (
          <TaskRow
            name="All teams"
            state={teams.length === 0 ? "No teams yet" : `${submitted} of ${teams.length} submitted`}
            more={waiting > 0 ? `${waiting} not submitted` : undefined}
            primary
            action="Open my tracks"
            onAction={goTracks}
          />
        )}
        {orderedTeams.map(t => (
          <TaskRow
            key={t.teamId}
            name={t.teamName}
            state={t.eliminated ? "Eliminated" : t.submissionCount > 0 ? "Submitted" : "Not submitted"}
            attention={isWaiting(t)}
            more={byTrack.size > 1 ? t.trackName : undefined}
            action="View team"
            onAction={goTracks}
          />
        ))}
      </TaskCard>
    </DashboardStage>
  );
}

function isWaiting(t: MentorAssignedTeam): boolean {
  return t.submissionCount === 0 && !t.eliminated;
}

// Read-only contest context for a mentor: resolves their event (by name, else the
// IN_PROGRESS one) → its ACTIVE round → the live CONTEST timer. Mentors aren't in the
// server fan-out, so they get milestone banners but no bell entries.
function useMentorContest(eventName: string) {
  const [event, setEvent] = useState<HackathonEvent | null>(null);
  const [round, setRound] = useState<Round | null>(null);

  useEffect(() => {
    if (!eventName) { setEvent(null); setRound(null); return; }
    let active = true;
    (async () => {
      try {
        const events = (await eventsApi.getAll()).data ?? [];
        const ev = events.find(e => e.name === eventName)
          ?? events.find(e => e.status === "IN_PROGRESS")
          ?? null;
        if (!ev) { if (active) { setEvent(null); setRound(null); } return; }
        const rs = (await roundsApi.getAll(ev.eventId)).data ?? [];
        const activeRound = rs.find(r => (r.status ?? "").toUpperCase() === "ACTIVE")
          ?? [...rs].sort((a, b) => (a.orderNumber ?? 0) - (b.orderNumber ?? 0))[0]
          ?? null;
        if (active) { setEvent(ev); setRound(activeRound); }
      } catch {
        if (active) { setEvent(null); setRound(null); }
      }
    })();
    return () => { active = false; };
  }, [eventName]);

  const timer = useRoundTimer(event?.eventId ?? null, round?.roundId ?? null, "CONTEST", { fireBanners: true });
  return { event, round, timer };
}
