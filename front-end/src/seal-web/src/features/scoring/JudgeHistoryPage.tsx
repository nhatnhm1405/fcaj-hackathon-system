import { useState, useEffect } from "react";
import {
  C, GradientText, PixelCard, PixelBadge,
} from "@/shared/components/PixelComponents";
import {
  assignmentsApi, eventsApi, roundsApi, submissionsApi, scoringApi, ApiError, JudgeAssignedTeam,
} from "@/shared/apiClient";
import { MemberTextList } from "@/shared/components/MemberTextList";

function fmtDateTime(iso?: string) {
  if (!iso) return "—";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  return `${String(d.getDate()).padStart(2, "0")}/${String(d.getMonth() + 1).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

interface HistoryRow {
  submissionId: number;
  teamId: number | null;
  teamName: string;
  trackName: string | null;
  roundName: string;
  roundOrder: number;
  total: number;
  isDraft: boolean;
  scoredAt?: string;
}

export function JudgeHistoryPage() {
  const [eventName, setEventName] = useState("");
  const [rows, setRows] = useState<HistoryRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  // Full team profiles keyed by teamId — real names + members shown as text.
  const [teamsById, setTeamsById] = useState<Map<number, JudgeAssignedTeam>>(new Map());

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    (async () => {
      try {
        const assignment = (await assignmentsApi.getJudgeAssignments()).data;
        const assignedTeams = assignment?.teams ?? [];
        const roundIds = [...new Set(assignedTeams.map(t => t.roundId))];
        const byTeam = new Map<number, JudgeAssignedTeam>();
        assignedTeams.forEach(t => { if (!byTeam.has(t.teamId)) byTeam.set(t.teamId, t); });
        if (!cancelled) { setEventName(assignment?.eventName ?? ""); setTeamsById(byTeam); }

        const events = await eventsApi.getAll().then(r => r.data ?? []).catch(() => []);
        const event = events.find(e => e.name === assignment?.eventName)
          ?? events.find(e => e.status === 'IN_PROGRESS' || e.status === 'OPEN')
          ?? events[events.length - 1];
        const rounds = event ? await roundsApi.getAll(event.eventId).then(r => r.data ?? []).catch(() => []) : [];
        const eventId = event?.eventId ?? null;

        const perRound = await Promise.all(roundIds.map(async roundId => {
          const [myScores, crit, subs] = await Promise.all([
            scoringApi.getMyScoresForRound(roundId).then(r => r.data ?? []).catch(() => []),
            eventId != null ? scoringApi.getCriteria(eventId, roundId).then(r => r.data ?? []).catch(() => []) : Promise.resolve([]),
            submissionsApi.getAllForRound(roundId).then(r => r.data ?? []).catch(() => []),
          ]);
          const weightByCrit = new Map(crit.map(c => [c.criteriaId, Number(c.weight)]));
          const teamIdBySub = new Map(subs.map(s => [s.submissionId, s.teamId]));
          const roundName = rounds.find(r => r.roundId === roundId)?.name ?? `Round #${roundId}`;
          const roundOrder = rounds.find(r => r.roundId === roundId)?.orderNumber ?? roundId;
          // Weighted max for this round → lets us show every score on a 0–100 scale.
          const weightedMax = crit.reduce((acc, c) => acc + Number(c.maxScore) * Number(c.weight), 0);

          // Group my scores by submission.
          const bySub = new Map<number, typeof myScores>();
          myScores.forEach(s => { const a = bySub.get(s.submissionId) ?? []; a.push(s); bySub.set(s.submissionId, a); });

          return [...bySub.entries()].map(([submissionId, scs]) => {
            const raw = scs.reduce((acc, sc) => acc + Number(sc.value) * (weightByCrit.get(sc.criteriaId) ?? 0), 0);
            const total = weightedMax > 0 ? (raw / weightedMax) * 100 : 0;
            const isDraft = scs.some(s => s.isDraft);
            const scoredAt = scs.map(s => s.updatedAt ?? s.scoredAt).filter(Boolean).sort().pop();
            const teamId = teamIdBySub.get(submissionId) ?? null;
            const teamName = (teamId != null ? byTeam.get(teamId)?.teamName : undefined) ?? `#${submissionId}`;
            const trackName = (teamId != null ? byTeam.get(teamId)?.trackName : undefined) ?? null;
            return { submissionId, teamId, teamName, trackName, roundName, roundOrder, total, isDraft, scoredAt } as HistoryRow;
          });
        }));

        if (cancelled) return;
        const flat = perRound.flat().sort((a, b) => (b.scoredAt ?? "").localeCompare(a.scoredAt ?? ""));
        setRows(flat);
      } catch (err) {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Failed to load scoring history.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => { cancelled = true; };
  }, []);

  const finalCount = rows.filter(r => !r.isDraft).length;
  const draftCount = rows.filter(r => r.isDraft).length;
  const [openRounds, setOpenRounds] = useState<Set<string>>(new Set());
  const toggleRound = (name: string) => setOpenRounds(prev => {
    const next = new Set(prev);
    next.has(name) ? next.delete(name) : next.add(name);
    return next;
  });

  // Group my scored teams by round (highest score first within a round).
  const roundGroups = (() => {
    const m = new Map<string, HistoryRow[]>();
    rows.forEach(r => { const a = m.get(r.roundName) ?? []; a.push(r); m.set(r.roundName, a); });
    return [...m.entries()]
      .map(([roundName, items]) => ({ roundName, roundOrder: items[0].roundOrder, items: [...items].sort((a, b) => b.total - a.total) }))
      .sort((a, b) => a.roundOrder - b.roundOrder);
  })();

  return (
    <div style={{ padding: 24, display: "flex", flexDirection: "column", gap: 20 }}>
      <div>
        <h1 style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 28, fontWeight: 800 }}>
          <GradientText>Scoring History</GradientText>
        </h1>
        <p style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, marginTop: 6 }}>
          {eventName ? <>{eventName} · </> : null}{finalCount} final · {draftCount} draft
        </p>
      </div>

      {error && (
        <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, padding: "10px 14px" }}>
          ERROR: {error}
        </div>
      )}

      {loading ? (
        <PixelCard style={{ padding: 40, textAlign: "center" }}>
          <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 13 }}>Loading...</div>
        </PixelCard>
      ) : rows.length === 0 ? (
        <PixelCard style={{ padding: 40, textAlign: "center" }}>
          <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 13 }}>No scoring history yet.</div>
        </PixelCard>
      ) : (
        roundGroups.map(group => {
          const isOpen = openRounds.has(group.roundName);
          return (
            <PixelCard key={group.roundName} style={{ padding: 0, overflow: "hidden", borderLeft: `4px solid ${C.green}` }}>
              {/* Round header — collapsible */}
              <button
                type="button"
                onClick={() => toggleRound(group.roundName)}
                style={{ width: "100%", display: "flex", alignItems: "center", gap: 12, padding: "16px 18px", background: "none", border: "none", cursor: "pointer", textAlign: "left", borderBottom: isOpen ? `1px solid ${C.border}` : "none" }}
              >
                <span style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 14 }}>{isOpen ? "v" : ">"}</span>
                <span style={{ flex: 1, color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 16, fontWeight: 800 }}>{group.roundName}</span>
                <PixelBadge color="blue">{group.items.length} TEAM{group.items.length === 1 ? "" : "S"}</PixelBadge>
              </button>

              {isOpen && (
                <div style={{ padding: 18, display: "flex", flexDirection: "column", gap: 12 }}>
                  {group.items.map(r => {
                    const team = r.teamId != null ? teamsById.get(r.teamId) : undefined;
                    return (
                      <div key={r.submissionId} style={{ padding: "12px 14px", background: C.surface2, border: `1px solid ${C.border}`, display: "flex", flexDirection: "column", gap: 10 }}>
                        <div style={{ display: "flex", gap: 12, alignItems: "center", fontFamily: "'JetBrains Mono', monospace", fontSize: 13, flexWrap: "wrap" }}>
                          <span style={{ color: C.text, fontWeight: 700 }}>{r.teamName}</span>
                          {r.trackName && <PixelBadge color="gray">{r.trackName}</PixelBadge>}
                          <span style={{ color: C.cyan, fontWeight: 700 }}>{r.total.toFixed(2)}<span style={{ color: C.textMuted, fontSize: 11 }}> /100</span></span>
                          <PixelBadge color={r.isDraft ? "yellow" : "green"}>{r.isDraft ? "Draft" : "Final"}</PixelBadge>
                          <span style={{ color: C.textDim, fontSize: 10, marginLeft: "auto" }}>{fmtDateTime(r.scoredAt)}</span>
                        </div>
                        <MemberTextList members={team?.members ?? []} />
                      </div>
                    );
                  })}
                </div>
              )}
            </PixelCard>
          );
        })
      )}
    </div>
  );
}
