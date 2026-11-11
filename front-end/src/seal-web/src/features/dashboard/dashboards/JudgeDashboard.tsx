import { useState, useEffect } from "react";
import { useNavigate } from "react-router";
import { useAuth } from "@/app/providers/AuthProvider";
import {
  C, GradientText, PixelCard, PixelButton, PixelBadge, PixelProgress, CyberStatCard,
} from "@/shared/components/PixelComponents";
import {
  assignmentsApi, eventsApi, roundsApi, scoringApi, ApiError,
  JudgeAssignedTeam, Round,
} from "@/shared/apiClient";

function fmtDate(iso?: string) {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}

function roundStatusColor(status?: string): "green" | "yellow" | "gray" {
  const s = (status ?? "").toUpperCase();
  if (s === "ACTIVE" || s === "OPEN") return "green";
  if (s === "UPCOMING" || s === "PENDING" || s === "DRAFT") return "yellow";
  return "gray";
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
  const pending = Math.max(0, totalTeams - totalScored);

  return (
    <div style={{ padding: 24, display: "flex", flexDirection: "column", gap: 20 }}>
      {/* Header */}
      <PixelCard glow gradient style={{ padding: 24 }}>
        <div style={{ color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 15, fontWeight: 700, marginBottom: 6 }}>
          Judge Console
        </div>
        <h1 style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 26, fontWeight: 800, lineHeight: 1.2 }}>
          <GradientText>Good day, {currentUser.full_name}</GradientText>
        </h1>
        <p style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, marginTop: 6 }}>
          {eventName ? <>Evaluating submissions for <span style={{ color: C.text }}>{eventName}</span>.</> : "Evaluate hackathon submissions across your assigned rounds."}
        </p>
      </PixelCard>

      {error && (
        <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, padding: "10px 14px" }}>
          ERROR: {error}
        </div>
      )}

      {/* Stat row */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 16 }}>
        <CyberStatCard value={roundStats.length} label="Rounds Assigned" accent="blue" />
        <CyberStatCard value={totalTeams} label="Teams to Score" accent="cyan" />
        <CyberStatCard value={totalScored} label="Scored" accent="green" />
        <CyberStatCard value={pending} label="Pending" accent="purple" />
      </div>

      {/* Overall progress */}
      <PixelCard style={{ padding: 20 }}>
        <div style={{ color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 15, fontWeight: 700, marginBottom: 12 }}>
          Overall Progress
        </div>
        <PixelProgress value={totalScored} max={totalTeams || 1} label={`${totalScored}/${totalTeams} submissions finalised`} gradient />
      </PixelCard>

      {/* Per-round cards */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: 16 }}>
        {loading && (
          <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>Loading...</div>
        )}
        {!loading && roundStats.length === 0 && !error && (
          <PixelCard style={{ padding: 20 }}>
            <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>
              No rounds assigned to you yet.
            </div>
          </PixelCard>
        )}
        {!loading && roundStats.map(({ round, roundId, total, scored }) => {
          const open = (round?.status ?? "").toUpperCase() === "ACTIVE" || (round?.status ?? "").toUpperCase() === "OPEN";
          return (
            <PixelCard key={roundId} glow glowColor="blue" style={{ padding: 20 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
                <div style={{ color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 14, fontWeight: 700 }}>
                  {round?.name ?? `Round #${roundId}`}{round?.isFinal ? " · Final" : ""}
                </div>
                <PixelBadge color={roundStatusColor(round?.status)}>{round?.status ?? "—"}</PixelBadge>
              </div>
              <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, marginBottom: 12 }}>
                Deadline: {fmtDate(round?.submissionDeadline)}
              </div>
              <PixelProgress value={scored} max={total || 1} label={`${scored}/${total} scored`} />
              <div style={{ marginTop: 14 }}>
                <PixelButton variant={open ? "cyber" : "secondary"} size="sm" onClick={() => navigate('/judge/score')}>
                  {open ? "SCORE NOW" : "VIEW"}
                </PixelButton>
              </div>
            </PixelCard>
          );
        })}
      </div>
    </div>
  );
}
