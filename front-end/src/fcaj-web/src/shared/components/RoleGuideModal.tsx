import { ReactNode, useEffect } from "react";
import { C, GradientText, PixelButton } from "@/shared/components/PixelComponents";

const mono = "'JetBrains Mono', monospace";

/**
 * Guidance popups for staff roles (Mentor / Judge). Mirrors CompetitionRulesModal
 * in look & behaviour (auto-shown once per session via RulesProvider, re-openable
 * from the footer link), but the content is a how-to guide for the role rather
 * than competition regulation.
 */
export type GuideRole = "MENTOR" | "JUDGE";

export function RoleGuideModal({ role, open, onClose }: { role: GuideRole; open: boolean; onClose: () => void }) {
  // Close on Escape; lock body scroll while open.
  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) { if (e.key === "Escape") onClose(); }
    document.addEventListener("keydown", onKey);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => { document.removeEventListener("keydown", onKey); document.body.style.overflow = prevOverflow; };
  }, [open, onClose]);

  if (!open) return null;

  const cfg = role === "MENTOR" ? MENTOR_GUIDE : JUDGE_GUIDE;

  return (
    <div
      onClick={onClose}
      style={{
        position: "fixed", inset: 0, zIndex: 1000,
        background: "rgba(7,12,15,0.88)", backdropFilter: "blur(5px)",
        display: "flex", alignItems: "center", justifyContent: "center", padding: 20,
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          position: "relative",
          width: "100%", maxWidth: 760, maxHeight: "88vh",
          background: C.surface, border: `1px solid ${C.border}`,
          boxShadow: "0 0 40px rgba(34,197,94,0.12), 0 20px 60px rgba(0,0,0,0.6)",
          display: "flex", flexDirection: "column", overflow: "hidden",
        }}
      >
        <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${C.green}, ${C.blue}, transparent)`, opacity: 0.8 }} />

        {/* Header */}
        <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: 12, padding: "22px 26px 14px", borderBottom: `1px solid ${C.border}`, flexShrink: 0 }}>
          <div>
            <div style={{ color: C.green, fontFamily: mono, fontSize: 13, fontWeight: 800, letterSpacing: "0.14em", textTransform: "uppercase", marginBottom: 8 }}>{cfg.eyebrow}</div>
            <h2 style={{ fontFamily: mono, fontWeight: 900, fontSize: 24, lineHeight: 1.1, margin: 0 }}>
              <GradientText>{cfg.title}</GradientText>
            </h2>
          </div>
          <button
            onClick={onClose}
            aria-label="Close"
            style={{ background: "transparent", border: `1px solid ${C.border}`, color: C.textMuted, cursor: "pointer", width: 34, height: 34, flexShrink: 0, fontFamily: mono, fontSize: 16, lineHeight: 1 }}
            onMouseEnter={(e) => { const el = e.currentTarget; el.style.color = C.red; el.style.borderColor = "rgba(239,68,68,0.45)"; }}
            onMouseLeave={(e) => { const el = e.currentTarget; el.style.color = C.textMuted; el.style.borderColor = C.border; }}
          >
            ✕
          </button>
        </div>

        {/* Scrollable body */}
        <div style={{ overflowY: "auto", padding: "20px 26px 8px", display: "flex", flexDirection: "column", gap: 22 }}>
          <P>{cfg.intro}</P>
          {cfg.sections.map((s, i) => (
            <Section key={i} title={s.title} accent={s.accent}>
              {s.body && <P>{s.body}</P>}
              {s.items && <List items={s.items} />}
            </Section>
          ))}
          <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 11, letterSpacing: "0.08em", textAlign: "center", padding: "6px 0 4px" }}>
            — SEAL HACKATHON ORGANIZING COMMITTEE —
          </div>
        </div>

        {/* Footer */}
        <div style={{ padding: "14px 26px", borderTop: `1px solid ${C.border}`, display: "flex", justifyContent: "flex-end", flexShrink: 0 }}>
          <PixelButton variant="cyber" onClick={onClose}>GOT IT</PixelButton>
        </div>
      </div>
    </div>
  );
}

// ── Content ───────────────────────────────────────────────────────────────

interface GuideSection { title: string; accent?: boolean; body?: ReactNode; items?: ReactNode[] }
interface GuideConfig { eyebrow: string; title: string; intro: ReactNode; sections: GuideSection[] }

const MENTOR_GUIDE: GuideConfig = {
  eyebrow: "Mentor Guide",
  title: "Mentoring at SEAL Hackathon",
  intro: (
    <>
      As a <B>Mentor</B> you guide and support teams in the <B>tracks you are assigned to</B> for the whole event.
      Mentoring is advisory — you help teams sharpen their direction, but you do <B>not</B> score submissions.
    </>
  ),
  sections: [
    {
      title: "I. What you can do",
      items: [
        "Open the Mentor Console to see your assigned tracks and the approved teams in each.",
        "Drill into a team to view its members and reach out to support them.",
        "Advise teams on requirements, design, technical approach, demo and presentation.",
      ],
    },
    {
      title: "II. How assignment works",
      items: [
        "You are assigned per TRACK, for the entire event — not per round.",
        "A track's teams appear once they are APPROVED and assigned to that track (during the Setup phase).",
        "Seeing no teams yet usually means tracks haven't been drawn/assigned, or no team in your track is approved yet.",
      ],
    },
    {
      title: "III. Mentoring etiquette",
      accent: true,
      items: [
        "Be fair and consistent — give every team you support the same level of attention.",
        "Guide, don't build: help teams think, but the work and decisions stay theirs.",
        "Keep what teams share with you confidential; never pass ideas between teams.",
        "Respect the schedule — support happens within the event's working windows.",
      ],
    },
  ],
};

const JUDGE_GUIDE: GuideConfig = {
  eyebrow: "Judge Guide",
  title: "Judging at SEAL Hackathon",
  intro: (
    <>
      As a <B>Judge</B> you evaluate and score the submissions for the <B>rounds and tracks you are assigned to</B>.
      Scores feed the leaderboard, decide who advances, and ultimately the awards — so consistency and fairness matter.
    </>
  ),
  sections: [
    {
      title: "I. How to score",
      items: [
        <>Open Score Submissions, pick an assigned round, then pick a submission. To keep judging impartial, teams are shown by an anonymous code (e.g. <B>WEB-1</B>), never by name.</>,
        "Review the submission's repo / demo / slides via the links on its card.",
        "Enter a score for each criterion (0 to its max) and an optional comment explaining it.",
        "Save Draft to come back later, or Submit Final when you're done.",
      ],
    },
    {
      title: "II. Scoring rules",
      items: [
        <>The total is <B>weighted</B>: Σ (criterion value × criterion weight).</>,
        "You can only score rounds that are currently OPEN/ACTIVE; finalized rounds are read-only.",
        "You only see the teams in your assigned track — the Final round lets you score all teams of the event.",
        "Once you Submit Final, your scores lock. Ask a Coordinator to reopen if a correction is needed.",
      ],
    },
    {
      title: "III. Judging etiquette",
      accent: true,
      items: [
        "Be objective and consistent — apply the same standard across every team.",
        "Use comments to justify scores; they help teams improve and keep results transparent.",
        "Declare any conflict of interest to the Coordinator rather than scoring a team you're tied to.",
        "Judge only what's submitted within the round's deadline.",
      ],
    },
  ],
};

// ── Shared presentational helpers (mirrors CompetitionRulesModal) ───────────

function Section({ title, accent, children }: { title: string; accent?: boolean; children: ReactNode }) {
  const color = accent ? "#60a5fa" : "#4ade80";
  return (
    <div>
      <div style={{
        display: "flex", alignItems: "center", gap: 12,
        marginBottom: 12, paddingBottom: 8,
        borderBottom: `1px solid ${accent ? "rgba(96,165,250,0.25)" : "rgba(74,222,128,0.25)"}`,
      }}>
        <span style={{ width: 4, height: 20, background: color, boxShadow: `0 0 10px ${color}`, flexShrink: 0 }} />
        <span style={{ color, fontFamily: mono, fontSize: 17, fontWeight: 800, letterSpacing: "0.03em", textShadow: `0 0 12px ${accent ? "rgba(96,165,250,0.35)" : "rgba(74,222,128,0.35)"}` }}>{title}</span>
      </div>
      <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>{children}</div>
    </div>
  );
}

function P({ children, style }: { children: ReactNode; style?: React.CSSProperties }) {
  return <p style={{ color: C.text, fontFamily: mono, fontSize: 13, lineHeight: 1.8, margin: 0, ...style }}>{children}</p>;
}

function B({ children }: { children: ReactNode }) {
  return <strong style={{ color: C.green, fontWeight: 800 }}>{children}</strong>;
}

function List({ items }: { items: ReactNode[] }) {
  return (
    <ul style={{ margin: 0, paddingLeft: 0, listStyle: "none", display: "flex", flexDirection: "column", gap: 8 }}>
      {items.map((it, i) => (
        <li key={i} style={{ display: "flex", gap: 10, color: C.text, fontFamily: mono, fontSize: 13, lineHeight: 1.65 }}>
          <span style={{ color: "#4ade80", flexShrink: 0, fontWeight: 700 }}>›</span>
          <span>{it}</span>
        </li>
      ))}
    </ul>
  );
}
