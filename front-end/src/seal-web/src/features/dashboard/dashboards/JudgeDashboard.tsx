import { useState, useEffect } from "react";
import { useNavigate } from "react-router";
import { useAuth } from "@/app/providers/AuthProvider";
import {
  ContestCard, DashboardError, DashboardStage, TaskCard, TaskNote, TaskRow,
} from "@/features/dashboard/components/DashboardUI";
import { eventDateRange, phaseSummary, stepDates } from "@/features/dashboard/components/eventPhase";
import {
  assignmentsApi, eventsApi, roundsApi, scoringApi, ApiError,
  HackathonEvent, JudgeAssignedTeam, Round,
} from "@/shared/apiClient";

function fmtDate(iso?: string) {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}

// "IN_PROGRESS" → "In progress"
function statusLabel(status?: string): string {
  const t = (status ?? "").replace(/_/g, " ").toLowerCase();
  return t ? t[0].toUpperCase() + t.slice(1) : "Status unknown";
}

interface RoundStat {
  round: Round | null;
  roundId: number;
  total: number;   // assigned teams in this round
  scored: number;  // distinct submissions I have finalised
}

export function JudgeDashboard() {
  const navigate = useNavigate();
  const { currentUser } = useAuth();

  const [eventName, setEventName] = useState<string>("");
  const [event, setEvent] = useState<HackathonEvent | null>(null);
  const [roundStats, setRoundStats] = useState<RoundStat[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    (async () => {
      try {
        const assignRes = await assignmentsApi.getJudgeAssignments();
        const assignment = assignRes.data;
        const teams: JudgeAssignedTeam[] = assignment?.teams ?? [];
        if (cancelled) return;
        setEventName(assignment?.eventName ?? "");

        // Group assigned teams by round.
        const byRound = new Map<number, number>();
        teams.forEach(t => byRound.set(t.roundId, (byRound.get(t.roundId) ?? 0) + 1));

        // Resolve the (single active) event to fetch round details.
        const events = (await eventsApi.getAll().then(r => r.data ?? []).catch(() => []));
        const event = events.find(e => e.name === assignment?.eventName)
          ?? events.find(e => e.status === 'IN_PROGRESS' || e.status === 'OPEN')
          ?? events[events.length - 1];
        const rounds = event
          ? await roundsApi.getAll(event.eventId).then(r => r.data ?? []).catch(() => [])
          : [];

        // Per round: how many submissions I have finalised (non-draft).
        const stats = await Promise.all([...byRound.entries()].map(async ([roundId, total]) => {
          const myScores = await scoringApi.getMyScoresForRound(roundId).then(r => r.data ?? []).catch(() => []);
          const finalisedSubs = new Set(myScores.filter(s => !s.isDraft).map(s => s.submissionId));
          return {
            round: rounds.find(r => r.roundId === roundId) ?? null,
            roundId,
            total,
            scored: finalisedSubs.size,
          } as RoundStat;
        }));

        if (cancelled) return;
        // Display only the assigned contest, never the fallback used to resolve rounds.
        setEvent(event && event.name === assignment?.eventName ? event : null);
        // Stable order: by round orderNumber when known, else roundId.
        stats.sort((a, b) => (a.round?.orderNumber ?? a.roundId) - (b.round?.orderNumber ?? b.roundId));
        setRoundStats(stats);
      } catch (err) {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Failed to load judge assignments.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => { cancelled = true; };
  }, []);

  if (!currentUser) return null;

  const totalTeams = roundStats.reduce((a, r) => a + r.total, 0);
  const totalScored = roundStats.reduce((a, r) => a + r.scored, 0);
  const isOpen = (r: RoundStat) => ["ACTIVE", "OPEN"].includes((r.round?.status ?? "").toUpperCase());
  const openRounds = roundStats.filter(isOpen);
  const openLeft = openRounds.reduce((n, r) => n + Math.max(0, r.total - r.scored), 0);
  const goScore = () => navigate('/judge/score');

  const leftText = `${openLeft} submission${openLeft === 1 ? "" : "s"} left`;
  const roundName = (r: RoundStat) => r.round?.name ?? `Round #${r.roundId}`;

  const summary = !event ? undefined
    : openRounds.length === 1 ? `${roundName(openRounds[0])} open, ${openLeft > 0 ? leftText : "everything is scored"}`
    : openRounds.length > 1 ? `${openRounds.length} rounds open, ${openLeft > 0 ? leftText : "everything is scored"}`
    : phaseSummary(event.status, event);

  return (
    <DashboardStage>
      {error && <DashboardError message={error} />}

      <ContestCard
        name={eventName || (loading ? "Loading…" : "No contest assigned")}
        status={event?.status}
        summary={summary}
        dates={event ? stepDates(event.status, event) : undefined}
        facts={roundStats.length === 0 ? [] : [
          ...(event?.topic ? [{ label: "Topic", value: event.topic }] : []),
          ...(event ? [{ label: "Dates", value: eventDateRange(event), mobileOnly: true }] : []),
          { label: "Rounds assigned", value: roundStats.length },
          { label: "Scored", value: `${totalScored} of ${totalTeams}` },
        ]}
      />

      <TaskCard title="Your scoring">
        {loading && <TaskNote>Loading…</TaskNote>}
        {!loading && roundStats.length === 0 && !error && <TaskNote>No rounds assigned to you yet</TaskNote>}
        {!loading && openLeft > 0 && (
          <TaskRow name="Next submission" state={`${leftText} to score`} attention primary action="Score next submission" onAction={goScore} />
        )}
        {!loading && roundStats.map(r => (
          <TaskRow
            key={r.roundId}
            name={roundName(r)}
            state={`${statusLabel(r.round?.status)}, ${r.scored} of ${r.total} scored, deadline ${fmtDate(r.round?.submissionDeadline)}`}
            action={isOpen(r) ? "Score round" : "View scores"}
            onAction={goScore}
          />
        ))}
      </TaskCard>
    </DashboardStage>
  );
}
