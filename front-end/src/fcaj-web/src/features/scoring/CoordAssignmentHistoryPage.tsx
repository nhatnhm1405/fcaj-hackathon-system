import { useEffect, useState } from "react";
import { C, GradientText, PixelBadge, PixelCard } from "@/shared/components/PixelComponents";
import { MemberTextList } from "@/shared/components/MemberTextList";
import {
  eventsApi, coordinatorApi, ApiError,
  HackathonEvent, CoordinatorEventHistory,
} from "@/shared/apiClient";
import { mono } from "./assignmentBoardStyles";

const PRIZE_COLOR = "#FFD24A";

function eventStatusColor(status?: string): "green" | "gray" | "yellow" | "red" {
  if (status === "CANCELLED") return "red";
  return "gray";
}

/** "Mentor: X, Y  ·  Round 1 Judges: A, B  ·  Final Judges: C" — one muted line under a track header. */
function StaffLine({ mentorNames, roundJudges }: { mentorNames: string[]; roundJudges: { roundName: string; judgeNames: string[] }[] }) {
  const parts = [
    `Mentor: ${mentorNames.length > 0 ? mentorNames.join(", ") : "—"}`,
    ...roundJudges.map(rj => `${rj.roundName} Judges: ${rj.judgeNames.length > 0 ? rj.judgeNames.join(", ") : "—"}`),
  ];
  return (
    <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 11, marginBottom: 12 }}>
      {parts.join("  ·  ")}
    </div>
  );
}

function EventHistoryDetail({ detail }: { detail: CoordinatorEventHistory }) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
      <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 12 }}>
        {detail.submittedTeams}/{detail.totalTeams} team{detail.totalTeams === 1 ? "" : "s"} submitted
        {detail.finalRoundJudges.length > 0 && (
          <span> &nbsp;·&nbsp; {detail.finalRoundJudges.map(rj => `${rj.roundName} Judges: ${rj.judgeNames.join(", ") || "—"}`).join("  ·  ")}</span>
        )}
      </div>

      {detail.prizes.length > 0 && (
        <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
          {detail.prizes.map((p, i) => (
            <div key={i} style={{ fontFamily: mono, fontSize: 12.5 }}>
              <span style={{ color: PRIZE_COLOR, fontWeight: 700 }}>#{p.rankPosition} {p.prizeName}</span>
              <span style={{ color: C.textMuted }}> — {p.teamName ?? "—"}</span>
            </div>
          ))}
        </div>
      )}

      {detail.tracks.length === 0 ? (
        <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 12 }}>No track data.</div>
      ) : detail.tracks.map(track => (
        <section key={track.trackId}>
          <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 10 }}>
            <span style={{ width: 5, height: 18, background: C.green }} />
            <span style={{ fontFamily: mono, fontSize: 14, fontWeight: 800, color: C.text, textTransform: "uppercase", letterSpacing: "0.04em" }}>
              {track.trackName}
            </span>
          </div>

          <StaffLine mentorNames={track.mentorNames} roundJudges={track.roundJudges} />

          {track.teams.length === 0 ? (
            <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 12 }}>No approved teams.</div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              {track.teams
                .slice()
                .sort((a, b) => (a.finalRank ?? Number.MAX_SAFE_INTEGER) - (b.finalRank ?? Number.MAX_SAFE_INTEGER) || a.teamName.localeCompare(b.teamName))
                .map(team => (
                  <div key={team.teamId} style={{ padding: "12px 14px", background: C.surface2, border: `1px solid ${C.border}`, display: "flex", flexDirection: "column", gap: 10 }}>
                    <div style={{ display: "flex", gap: 12, alignItems: "center", fontFamily: mono, fontSize: 13, flexWrap: "wrap" }}>
                      <span style={{ color: C.text, fontWeight: 700 }}>{team.teamName}</span>
                      {team.finalRank != null && <span style={{ color: C.cyan }}>Final #{team.finalRank}</span>}
                      {team.prizeName && <span style={{ color: PRIZE_COLOR, fontWeight: 700 }}>Prize: {team.prizeName}</span>}
                      <PixelBadge color={team.teamStatus === "DISQUALIFIED" ? "red" : "gray"}>{team.teamStatus}</PixelBadge>
                    </div>
                    <MemberTextList members={team.members} />
                  </div>
                ))}
            </div>
          )}
        </section>
      ))}
    </div>
  );
}

export function CoordAssignmentHistoryPage() {
  const [events, setEvents] = useState<HackathonEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState<Set<number>>(new Set());
  const [details, setDetails] = useState<Record<number, CoordinatorEventHistory>>({});
  const [loadingDetail, setLoadingDetail] = useState<Set<number>>(new Set());

  useEffect(() => {
    eventsApi.getAll()
      .then(response => {
        const ended = (response.data ?? [])
          .filter(e => e.status === "COMPLETED" || e.status === "CANCELLED")
          .sort((a, b) => (b.year ?? 0) - (a.year ?? 0) || b.eventId - a.eventId);
        setEvents(ended);
      })
      .catch(err => setError(err instanceof ApiError ? err.message : "Failed to load event history."))
      .finally(() => setLoading(false));
  }, []);

  function toggle(eventId: number) {
    setOpen(previous => {
      const next = new Set(previous);
      next.has(eventId) ? next.delete(eventId) : next.add(eventId);
      return next;
    });

    if (!details[eventId] && !loadingDetail.has(eventId)) {
      setLoadingDetail(previous => new Set(previous).add(eventId));
      coordinatorApi.getEventHistory(eventId)
        .then(response => {
          if (response.data) {
            setDetails(previous => ({ ...previous, [eventId]: response.data as CoordinatorEventHistory }));
          }
        })
        .finally(() => {
          setLoadingDetail(previous => { const next = new Set(previous); next.delete(eventId); return next; });
        });
    }
  }

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
      ) : events.length === 0 ? (
        <PixelCard style={{ padding: 40, textAlign: "center" }}>
          <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 13 }}>No past events yet.</div>
        </PixelCard>
      ) : (
        events.map(event => {
          const isOpen = open.has(event.eventId);
          const detail = details[event.eventId];
          const isLoadingDetail = loadingDetail.has(event.eventId);
          const seasonLabel = (event.season ?? "-").slice(0, 3);

          return (
            <PixelCard key={event.eventId} glow gradient style={{ padding: 0, overflow: "hidden", borderLeft: `4px solid ${C.green}` }}>
              <div
                onClick={() => toggle(event.eventId)}
                style={{ padding: "16px 18px", display: "flex", gap: 14, alignItems: "center", flexWrap: "wrap", cursor: "pointer", borderBottom: isOpen ? `1px solid ${C.border}` : "none" }}
              >
                <div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", minWidth: 64, padding: "6px 8px", background: C.surface2, border: `1px solid ${C.border}` }}>
                  <span style={{ fontFamily: mono, fontSize: 12, fontWeight: 800, color: C.green, textTransform: "uppercase" }}>{seasonLabel}</span>
                  <span style={{ fontFamily: mono, fontSize: 10, color: C.textMuted }}>{event.year ?? ""}</span>
                </div>

                <div style={{ flex: "1 1 220px" }}>
                  <div style={{ fontFamily: mono, fontSize: 17, fontWeight: 800, color: C.text }}>{event.name}</div>
                </div>

                <PixelBadge color={eventStatusColor(event.status)}>{event.status}</PixelBadge>
                <span style={{ color: C.textMuted, fontFamily: mono, fontSize: 14 }}>{isOpen ? "v" : ">"}</span>
              </div>

              {isOpen && (
                <div style={{ padding: 18 }}>
                  {isLoadingDetail || !detail
                    ? <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 12 }}>Loading...</div>
                    : <EventHistoryDetail detail={detail} />}
                </div>
              )}
            </PixelCard>
          );
        })
      )}
    </div>
  );
}
