import { useEffect, useState } from "react";
import { useAuth } from "@/app/providers/AuthProvider";
import { C, GradientText, PixelBadge, PixelCard } from "@/shared/components/PixelComponents";
import { ApiError, TeamHistoryEntry, teamsApi } from "@/shared/apiClient";
import { MemberTextList } from "@/shared/components/MemberTextList";
import { CertificateModal } from "./CertificateModal";

const mono = "'JetBrains Mono', monospace";

const MEDAL_COLOR: Record<number, string> = {
  1: "#FFD24A",
  2: "#CBD5E1",
  3: "#E0915A",
};

function statusColor(status?: string): "green" | "gray" | "yellow" | "red" {
  if (status === "COMPLETED") return "gray";
  if (status === "DISQUALIFIED" || status === "REJECTED") return "red";
  if (status === "PENDING") return "yellow";
  return "green";
}

function standing(entry: TeamHistoryEntry): { text: string; color: string } {
  const rounds = entry.rounds ?? [];

  if (entry.prize) {
    return {
      text: entry.prize.name,
      color: MEDAL_COLOR[entry.prize.rankPosition] ?? C.green,
    };
  }

  if (entry.teamStatus === "DISQUALIFIED") {
    return { text: "Disqualified", color: C.red };
  }

  const finalRound = rounds.find((round) => round.isFinal);
  if (finalRound) {
    return { text: `Finalist #${finalRound.rankPosition}`, color: C.green };
  }

  const lastEliminated = rounds.slice().reverse().find((round) => !round.advanced);
  if (lastEliminated) {
    return { text: `Eliminated - ${lastEliminated.roundName}`, color: C.textMuted };
  }

  const latestRound = rounds[rounds.length - 1];
  if (latestRound) {
    return { text: `${latestRound.roundName} #${latestRound.rankPosition}`, color: C.cyan };
  }

  return { text: entry.teamStatus ?? entry.eventStatus, color: C.textMuted };
}

export function HistoryPage() {
  const { currentUser } = useAuth();
  const [entries, setEntries] = useState<TeamHistoryEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState<Set<string>>(new Set());
  const [certificate, setCertificate] = useState<TeamHistoryEntry | null>(null);

  useEffect(() => {
    teamsApi.getMyResultHistory()
      .then((response) => setEntries(response.data ?? []))
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load history."))
      .finally(() => setLoading(false));
  }, []);

  const toggle = (key: string) =>
    setOpen((previous) => {
      const next = new Set(previous);
      next.has(key) ? next.delete(key) : next.add(key);
      return next;
    });

  return (
    <div style={{ padding: 24, display: "flex", flexDirection: "column", gap: 16 }}>
      <div>
        <h1 style={{ fontFamily: mono, fontSize: 28, fontWeight: 800 }}>
          <GradientText>History</GradientText>
        </h1>
      </div>

      {error && (
        <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: mono, fontSize: 11, padding: "10px 14px" }}>
          ERROR: {error}
        </div>
      )}

      {loading ? (
        <PixelCard style={{ padding: 40, textAlign: "center" }}>
          <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 13 }}>Loading...</div>
        </PixelCard>
      ) : entries.length === 0 ? (
        <PixelCard style={{ padding: 40, textAlign: "center" }}>
          <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 13 }}>
            You have not joined any event yet.
          </div>
        </PixelCard>
      ) : (
        entries.map((entry) => {
          const summary = standing(entry);
          const entryKey = `${entry.eventId}-${entry.teamId}`;
          const isOpen = open.has(entryKey);
          const rounds = entry.rounds ?? [];
          const submissions = entry.submissions ?? [];
          const members = entry.members ?? [];
          const completed = entry.eventStatus === "COMPLETED";
          const roleLabel = entry.myRole === "LEADER" ? "Team Leader" : "Member";
          const seasonLabel = (entry.season ?? "-").slice(0, 3);

          return (
            <PixelCard key={entryKey} glow gradient style={{ padding: 0, overflow: "hidden", borderLeft: `4px solid ${summary.color}` }}>
              <div
                onClick={() => toggle(entryKey)}
                style={{ padding: "16px 18px", display: "flex", gap: 14, alignItems: "center", flexWrap: "wrap", cursor: "pointer", borderBottom: isOpen ? `1px solid ${C.border}` : "none" }}
              >
                <div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", minWidth: 64, padding: "6px 8px", background: C.surface2, border: `1px solid ${C.border}` }}>
                  <span style={{ fontFamily: mono, fontSize: 12, fontWeight: 800, color: summary.color, textTransform: "uppercase" }}>
                    {seasonLabel}
                  </span>
                  <span style={{ fontFamily: mono, fontSize: 10, color: C.textMuted }}>
                    {entry.year ?? ""}
                  </span>
                </div>

                <div style={{ flex: "1 1 240px", minWidth: 0 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
                    <span style={{ fontFamily: mono, fontSize: 17, fontWeight: 800, color: C.text }}>{entry.teamName}</span>
                    {entry.trackName && <PixelBadge color="blue">{entry.trackName}</PixelBadge>}
                    <PixelBadge color={entry.myRole === "LEADER" ? "green" : "gray"}>{roleLabel}</PixelBadge>
                  </div>
                  <div style={{ fontFamily: mono, fontSize: 11, color: C.textMuted, marginTop: 6 }}>
                    {entry.eventName}
                  </div>
                </div>

                <span style={{ fontFamily: mono, fontSize: 12, fontWeight: 800, color: summary.color, background: C.surface2, border: `1px solid ${summary.color}`, padding: "5px 12px", letterSpacing: "0.04em" }}>
                  {summary.text}
                </span>

                {completed && (
                  <button
                    type="button"
                    onClick={(event) => {
                      event.stopPropagation();
                      setCertificate(entry);
                    }}
                    style={{ fontFamily: mono, fontSize: 11, fontWeight: 700, letterSpacing: "0.05em", padding: "7px 12px", cursor: "pointer", borderRadius: 0, border: `1px solid ${C.green}`, background: "transparent", color: C.green }}
                  >
                    CERTIFICATE
                  </button>
                )}

                <PixelBadge color={statusColor(entry.eventStatus)}>{entry.eventStatus}</PixelBadge>
                <span style={{ color: C.textMuted, fontFamily: mono, fontSize: 14 }}>{isOpen ? "v" : ">"}</span>
              </div>

              {isOpen && (
                <div style={{ padding: 18, display: "flex", flexDirection: "column", gap: 18 }}>
                  <section>
                    <div style={sectionLabel}>Team members</div>
                    <MemberTextList
                      members={members.map(m => ({ fullName: m.fullName, memberRole: m.role, studentId: m.studentId, userType: m.userType, university: m.university }))}
                    />
                  </section>

                  <section>
                    <div style={sectionLabel}>Round results</div>
                    {rounds.length === 0 ? (
                      <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 12 }}>No round result data.</div>
                    ) : (
                      <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                        {rounds.map((round, index) => (
                          <div key={index} style={{ display: "flex", gap: 12, alignItems: "center", fontFamily: mono, fontSize: 12, flexWrap: "wrap" }}>
                            <span style={{ color: C.text, minWidth: 160 }}>{round.roundName}{round.isFinal ? " (Final)" : ""}</span>
                            <span style={{ color: C.cyan, fontWeight: 700, minWidth: 42 }}>#{round.rankPosition}</span>
                            <span style={{ color: C.green, minWidth: 64 }}>{Number(round.totalScore).toFixed(1)}</span>
                            <PixelBadge color={round.advanced ? "green" : "gray"}>
                              {round.advanced ? (round.isFinal ? "Winner tier" : "Advanced") : (round.isFinal ? "Finalist" : "Eliminated")}
                            </PixelBadge>
                          </div>
                        ))}
                      </div>
                    )}
                  </section>

                  <section>
                    <div style={sectionLabel}>Submissions</div>
                    {submissions.length === 0 ? (
                      <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 12 }}>No submission data.</div>
                    ) : (
                      <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                        {submissions.map((submission, index) => (
                          <div key={index} style={{ display: "flex", gap: 12, alignItems: "center", fontFamily: mono, fontSize: 12, flexWrap: "wrap" }}>
                            <span style={{ color: C.text, minWidth: 160 }}>{submission.roundName}</span>
                            {submission.repoUrl && <a href={submission.repoUrl} target="_blank" rel="noreferrer" style={linkStyle}>repo</a>}
                            {submission.demoUrl && <a href={submission.demoUrl} target="_blank" rel="noreferrer" style={linkStyle}>demo</a>}
                            {submission.slideUrl && <a href={submission.slideUrl} target="_blank" rel="noreferrer" style={linkStyle}>slides</a>}
                            <PixelBadge color={submission.status === "LATE" ? "yellow" : "gray"}>{submission.status}</PixelBadge>
                          </div>
                        ))}
                      </div>
                    )}
                  </section>
                </div>
              )}
            </PixelCard>
          );
        })
      )}

      {certificate && (
        <CertificateModal entry={certificate} recipientName={currentUser?.full_name ?? ""} onClose={() => setCertificate(null)} />
      )}
    </div>
  );
}

const sectionLabel: React.CSSProperties = {
  fontFamily: mono,
  fontSize: 10,
  letterSpacing: "0.12em",
  textTransform: "uppercase",
  color: C.green,
  fontWeight: 700,
  marginBottom: 8,
};

const linkStyle: React.CSSProperties = {
  color: C.cyan,
  fontFamily: mono,
  fontSize: 12,
  textDecoration: "none",
};