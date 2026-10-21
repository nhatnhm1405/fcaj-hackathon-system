import { useEffect, useState } from "react";
import { C, GradientText, PixelBadge, PixelCard } from "@/shared/components/PixelComponents";
import { MemberTextList } from "@/shared/components/MemberTextList";
import { ApiError, assignmentsApi, MentorHistoryEntry } from "@/shared/apiClient";

const mono = "'JetBrains Mono', monospace";
const PRIZE_COLOR = "#FFD24A";

function eventStatusColor(status?: string): "green" | "gray" | "yellow" | "red" {
  if (status === "COMPLETED") return "gray";
  if (status === "CANCELLED") return "red";
  if (status === "DRAFT" || status === "SETUP") return "yellow";
  return "green";
}

export function MentorHistoryPage() {
  const [entries, setEntries] = useState<MentorHistoryEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState<Set<number>>(new Set());

  useEffect(() => {
    assignmentsApi.getMentorHistory()
      .then((response) => setEntries(response.data ?? []))
      .catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load mentoring history."))
      .finally(() => setLoading(false));
  }, []);

  const toggle = (eventId: number) =>
    setOpen((previous) => {
      const next = new Set(previous);
      next.has(eventId) ? next.delete(eventId) : next.add(eventId);
      return next;
    });

  return (
    <div style={{ padding: 24, display: "flex", flexDirection: "column", gap: 16 }}>
      <div>
        <h1 style={{ fontFamily: mono, fontSize: 28, fontWeight: 800 }}>
          <GradientText>Mentoring History</GradientText>
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
            No mentoring history yet.
          </div>
        </PixelCard>
      ) : (
        entries.map((entry) => {
          const tracks = entry.tracks ?? [];
          const isOpen = open.has(entry.eventId);
          const teamCount = tracks.reduce((total, track) => total + (track.teams ?? []).length, 0);
          const seasonLabel = (entry.season ?? "-").slice(0, 3);

          return (
            <PixelCard key={entry.eventId} glow gradient style={{ padding: 0, overflow: "hidden", borderLeft: `4px solid ${C.green}` }}>
              <div
                onClick={() => toggle(entry.eventId)}
                style={{ padding: "16px 18px", display: "flex", gap: 14, alignItems: "center", flexWrap: "wrap", cursor: "pointer", borderBottom: isOpen ? `1px solid ${C.border}` : "none" }}
              >
                <div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", minWidth: 64, padding: "6px 8px", background: C.surface2, border: `1px solid ${C.border}` }}>
                  <span style={{ fontFamily: mono, fontSize: 12, fontWeight: 800, color: C.green, textTransform: "uppercase" }}>
                    {seasonLabel}
                  </span>
                  <span style={{ fontFamily: mono, fontSize: 10, color: C.textMuted }}>
                    {entry.year ?? ""}
                  </span>
                </div>

                <div style={{ flex: "1 1 220px" }}>
                  <div style={{ fontFamily: mono, fontSize: 17, fontWeight: 800, color: C.text }}>
                    {entry.eventName}
                  </div>
                  <div style={{ fontFamily: mono, fontSize: 11, color: C.textMuted, marginTop: 5 }}>
                    {tracks.length} track{tracks.length === 1 ? "" : "s"} - {teamCount} team{teamCount === 1 ? "" : "s"} mentored
                  </div>
                </div>

                <PixelBadge color={eventStatusColor(entry.eventStatus)}>
                  {entry.eventStatus}
                </PixelBadge>
                <span style={{ color: C.textMuted, fontFamily: mono, fontSize: 14 }}>
                  {isOpen ? "v" : ">"}
                </span>
              </div>

              {isOpen && (
                <div style={{ padding: 18, display: "flex", flexDirection: "column", gap: 18 }}>
                  {tracks.length === 0 ? (
                    <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 12 }}>
                      No track data.
                    </div>
                  ) : tracks.map((track) => {
                    const teams = track.teams ?? [];

                    return (
                      <section key={track.trackId}>
                        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 10 }}>
                          <span style={{ width: 5, height: 18, background: C.green }} />
                          <span style={{ fontFamily: mono, fontSize: 14, fontWeight: 800, color: C.text, textTransform: "uppercase", letterSpacing: "0.04em" }}>
                            {track.trackName}
                          </span>
                        </div>

                        {teams.length === 0 ? (
                          <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 12 }}>No approved teams.</div>
                        ) : (
                          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                            {teams
                              .slice()
                              .sort((a, b) => {
                                const rankDiff = (a.finalRank ?? Number.MAX_SAFE_INTEGER) - (b.finalRank ?? Number.MAX_SAFE_INTEGER);
                                return rankDiff !== 0 ? rankDiff : a.teamName.localeCompare(b.teamName);
                              })
                              .map((team) => (
                                <div key={team.teamId} style={{ padding: "12px 14px", background: C.surface2, border: `1px solid ${C.border}`, display: "flex", flexDirection: "column", gap: 10 }}>
                                  <div style={{ display: "flex", gap: 12, alignItems: "center", fontFamily: mono, fontSize: 13, flexWrap: "wrap" }}>
                                    <span style={{ color: C.text, fontWeight: 700 }}>{team.teamName}</span>
                                    <span style={{ color: C.cyan }}>
                                      {team.finalRank != null ? `Final #${team.finalRank}` : "—"}
                                    </span>
                                    {team.prizeName && (
                                      <span style={{ color: PRIZE_COLOR, fontWeight: 700 }}>Prize: {team.prizeName}</span>
                                    )}
                                    <PixelBadge color={team.teamStatus === "DISQUALIFIED" ? "red" : "gray"}>
                                      {team.teamStatus}
                                    </PixelBadge>
                                  </div>
                                  <MemberTextList members={team.members ?? []} />
                                </div>
                              ))}
                          </div>
                        )}
                      </section>
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