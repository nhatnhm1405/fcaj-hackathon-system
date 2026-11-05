import { ReactNode, useEffect } from "react";
import { C, GradientText, PixelButton } from "@/shared/components/PixelComponents";

const mono = "'JetBrains Mono', monospace";

/**
 * Static competition-rules document shown to participants on every login
 * (once per session) and re-openable from the footer "Competition Rules" link.
 * Content is the SEAL Hackathon Summer 2026 regulation, English translation.
 */
export function CompetitionRulesModal({ open, onClose }: { open: boolean; onClose: () => void }) {
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
            <div style={{ color: C.green, fontFamily: mono, fontSize: 13, fontWeight: 800, letterSpacing: "0.14em", textTransform: "uppercase", marginBottom: 8 }}>Official Competition Rules</div>
            <h2 style={{ fontFamily: mono, fontWeight: 900, fontSize: 24, lineHeight: 1.1, margin: 0 }}>
              <GradientText>SEAL Hackathon — Summer 2026</GradientText>
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
          <P>
            SEAL Hackathon is an academic and technology playground for IT students at FPT University HCMC campus
            and other universities in Ho Chi Minh City. Each year SEAL runs <B>3 Hackathons</B> by semester —
            Spring (SDLC &amp; Professional Working), <B>Summer (Emerging Technologies)</B>, and Fall (Product &amp;
            User Experience). Results feed <B>3 leaderboards</B>: Chapter (year-long), Team (per Hackathon), and
            Individual (accumulated across the year).
          </P>

          <Section title="I. About">
            <P>
              Summer 2026 theme: <B>“AI Agents for Software Innovation.”</B> Teams apply AI across the software
              development life cycle (SDLC) — requirements, design, development, testing, deployment and operations.
            </P>
          </Section>

          <Section title="II. Schedule">
            <List items={[
              "Registration: 1–19 Jun 2026 (closes early once enough teams register).",
              "Workshop “Unleashing AI Agents in Software Engineering”: 29 Jun 2026, 19:30–21:30.",
              "Opening · bracket draw · team meeting: 4 Jul 2026, 14:00–17:00.",
              "Code + Preliminary & Final presentations + Closing: 5 Jul 2026, 06:00–21:00.",
            ]} />
          </Section>

          <Section title="III. Structure">
            <P>Each team has <B>3–5 members</B>. The bracket draw on 4 Jul splits the Preliminary round into:</P>
            <List items={[
              "Bracket A: AI for Requirements & Design.",
              "Bracket B: AI for Development, Testing & Operations.",
            ]} />
            <P>
              <B>Preliminary:</B> 8 hours of coding from the Organizers' brief, then presentations by bracket —
              5 teams per bracket advance. <B>Final:</B> teams present the product against the given criteria; once
              the judges agree on scores, the Organizers announce results and award the winners.
            </P>
          </Section>

          <Section title="IV. Scoring criteria">
            <P style={{ marginBottom: 4 }}><B>Preliminary round</B> (10 teams across both brackets advance):</P>
            <List items={[
              "Correctness & feature completeness.",
              "AI application in the solution.",
              "Software design & architecture.",
              "Presentation & demo.",
              "Teamwork & working spirit.",
            ]} />
            <P style={{ margin: "10px 0 4px" }}><B>Final round</B> (6 teams awarded):</P>
            <List items={[
              "Product completeness & quality.",
              "Creativity & innovation.",
              "Applicability & deployability.",
              "Presentation & product demo.",
              "Teamwork & handling Q&A.",
            ]} />
          </Section>

          <Section title="V. Eligibility & rules">
            <List items={[
              "Open to IT students at FPT University HCMC and invited HCMC universities. Graduates may not participate.",
              "A contestant may join only one team.",
              "Languages and tools are flexible per the team's capability.",
              "Contestants must attend all parts: Workshop, Opening & team meeting, Code–presentation, and Closing.",
              "During the competition, contestants may not leave the competition area without Organizer approval.",
              "Cheating or copying other entries leads to disqualification.",
              "After registration closes, teams cannot change members.",
              "Violations may void a contestant's or team's results, at the Organizers' discretion.",
              "The Organizers may use contestants' images for communications before, during and after the event.",
              "Product copyright belongs to the Team; the Organizers may use it for communications and training.",
            ]} />
          </Section>

          <Section title="VI. Awards">
            <List items={[
              "Certificate of participation for every contestant.",
              "1st prize: 7,000,000₫ + certificate + flowers.",
              "2nd prize: 5,000,000₫ + certificate + flowers.",
              "3rd prize: 3,000,000₫ + certificate + flowers.",
              "Creative Idea prize: 1,000,000₫ + certificate.",
              "Practical Application prize: 1,000,000₫ + certificate.",
              "Outstanding Individual prize: 1,000,000₫ + certificate.",
            ]} />
          </Section>

          <Section title="Platform notes" accent>
            <List items={[
              "Register during the OPEN phase by creating a team or joining an existing one.",
              "You do NOT pick a track at registration — tracks are assigned during the Setup phase (the leader self-selects, or the coordinator draws randomly).",
              "Teams with fewer than 3 members, and participants without a team, may be randomly merged by a coordinator.",
              "Team name, members and leadership can only be changed during Registration & Setup. Once the contest starts (In Progress), your team is locked.",
            ]} />
          </Section>

          <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 11, letterSpacing: "0.08em", textAlign: "center", padding: "6px 0 4px" }}>
            — SEAL HACKATHON ORGANIZING COMMITTEE —
          </div>
        </div>

        {/* Footer */}
        <div style={{ padding: "14px 26px", borderTop: `1px solid ${C.border}`, display: "flex", justifyContent: "flex-end", flexShrink: 0 }}>
          <PixelButton variant="cyber" onClick={onClose}>I HAVE READ THE RULES</PixelButton>
        </div>
      </div>
    </div>
  );
}

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

function List({ items }: { items: string[] }) {
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
