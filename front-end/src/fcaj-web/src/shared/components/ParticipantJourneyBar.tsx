import { useAuth } from "@/app/providers/AuthProvider";
import { MyTeam } from "@/shared/apiClient";
import { C } from "@/shared/components/PixelComponents";

const mono = "'JetBrains Mono', monospace";

const STEPS = [
  { label: "Joined", hint: "Account approved" },
  { label: "Team", hint: "Create or join a team" },
  { label: "Approved", hint: "Coordinator approval" },
  { label: "Track", hint: "Assigned during Setup" },
  { label: "Compete", hint: "Submit & get scored" },
];

const PULSE = `@keyframes pjPulse { 0%, 100% { box-shadow: 0 0 0 0 rgba(34,197,94,0.5); } 50% { box-shadow: 0 0 0 7px rgba(34,197,94,0); } }`;

/**
 * Live progress tracker for the participant journey. Derives the active step from
 * real state (team membership, approval, track, event phase) so it advances in
 * parallel with what the user actually does. Display-only.
 *
 * Pass the loaded MyTeam on the team console; pass null/undefined on the no-team
 * screen (membership is then read from the auth user's team_id).
 */
export function ParticipantJourneyBar({ team, highlight = false }: { team?: MyTeam | null; highlight?: boolean }) {
  const { currentUser } = useAuth();
  if (!currentUser) return null;

  const active = activeIndex(currentUser.team_id, team);
  const last = STEPS.length - 1;
  const allDone = active >= STEPS.length;

  // Dark no-team screen: match the "pending approval" gradient card + white text.
  const boxBg = highlight ? "linear-gradient(135deg, rgba(34,197,94,0.06) 0%, rgba(59,130,246,0.04) 100%)" : C.surface;
  const boxBorder = highlight ? "rgba(59,130,246,0.2)" : C.border;
  // Same blue glow halo as the "pending approval" PixelCard.
  const boxShadow = highlight ? "0 0 0 1px rgba(59,130,246,0.15), 0 0 24px rgba(59,130,246,0.12), inset 0 0 40px rgba(59,130,246,0.03)" : undefined;
  const headText = highlight ? "#ffffff" : C.green;
  const strongText = highlight ? "#ffffff" : C.text;
  const mutedText = highlight ? "rgba(255,255,255,0.85)" : C.textMuted;

  return (
    <div style={{ position: "relative", background: boxBg, border: `1px solid ${boxBorder}`, boxShadow, padding: "18px 20px 14px", overflow: "hidden" }}>
      <style>{PULSE}</style>
      <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${C.green}, ${C.blue}, transparent)`, opacity: 0.7 }} />
      {highlight && (
        <>
          <div style={{ position: "absolute", top: 0, left: 0, width: 10, height: 10, borderTop: "2px solid #3b82f6", borderLeft: "2px solid #3b82f6", opacity: 0.7 }} />
          <div style={{ position: "absolute", top: 0, right: 0, width: 10, height: 10, borderTop: "2px solid #3b82f6", borderRight: "2px solid #3b82f6", opacity: 0.7 }} />
          <div style={{ position: "absolute", bottom: 0, left: 0, width: 10, height: 10, borderBottom: "2px solid #3b82f6", borderLeft: "2px solid #3b82f6", opacity: 0.4 }} />
          <div style={{ position: "absolute", bottom: 0, right: 0, width: 10, height: 10, borderBottom: "2px solid #3b82f6", borderRight: "2px solid #3b82f6", opacity: 0.4 }} />
        </>
      )}

      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 14, gap: 10, flexWrap: "wrap" }}>
        <span style={{ color: headText, fontFamily: mono, fontSize: 11, fontWeight: 800, letterSpacing: "0.16em", textTransform: "uppercase" }}>Your Progress</span>
        <span style={{ color: mutedText, fontFamily: mono, fontSize: 10, letterSpacing: "0.08em" }}>
          {allDone ? "All steps complete" : `Step ${active + 1} of ${STEPS.length} · ${STEPS[active].hint}`}
        </span>
      </div>

      <div style={{ display: "flex" }}>
        {STEPS.map((s, i) => {
          const done = i < active;
          const isActive = i === active && !allDone;
          const dotColor = done || isActive ? C.green : C.border;
          const leftOn = i <= active;   // segment entering step i
          const rightOn = i < active;   // segment leaving step i
          return (
            <div key={i} style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", minWidth: 0 }}>
              {/* circle + connectors */}
              <div style={{ display: "flex", alignItems: "center", width: "100%" }}>
                <span style={{ flex: 1, height: 2, background: i > 0 ? (leftOn ? C.green : C.border) : "transparent" }} />
                <div
                  style={{
                    width: 30, height: 30, borderRadius: "50%", flexShrink: 0,
                    background: isActive ? "rgba(34,197,94,0.15)" : done ? "rgba(34,197,94,0.1)" : "transparent",
                    border: `2px solid ${dotColor}`,
                    color: done || isActive ? C.green : C.textMuted,
                    fontFamily: mono, fontSize: 12, fontWeight: 700,
                    display: "flex", alignItems: "center", justifyContent: "center",
                    transition: "all 0.3s ease",
                    animation: isActive ? "pjPulse 1.6s ease-in-out infinite" : "none",
                    boxShadow: done ? `0 0 8px rgba(34,197,94,0.25)` : "none",
                  }}
                >
                  {done ? "✓" : i + 1}
                </div>
                <span style={{ flex: 1, height: 2, background: i < last ? (rightOn ? C.green : C.border) : "transparent" }} />
              </div>
              {/* label */}
              <span style={{ marginTop: 8, color: done || isActive ? strongText : mutedText, fontFamily: mono, fontSize: 10.5, fontWeight: isActive ? 700 : 500, letterSpacing: "0.04em", textAlign: "center", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis", maxWidth: "100%" }}>
                {s.label}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

/** First incomplete step index (= the active step); STEPS.length when all done. */
function activeIndex(teamId: number | null, team?: MyTeam | null): number {
  if (teamId == null) return 1;                                          // 0 done → joining a team
  if ((team?.status ?? "").toUpperCase() !== "APPROVED") return 2;       // awaiting approval
  if (!team?.trackName) return 3;                                        // awaiting track
  if ((team?.eventStatus ?? "").toUpperCase() !== "COMPLETED") return 4; // competing
  return 5;                                                             // all done
}
