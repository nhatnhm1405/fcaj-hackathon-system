import { useNavigate } from "react-router";
import {
  C,
  GradientText,
  PixelButton,
  SectionHeader,
  FloatingParticles,
} from "@/shared/components/PixelComponents";
import { SealFooter } from "@/shared/components/SealFooter";
import { useAuth } from "@/app/providers/AuthProvider";
import sealLogo from "@/imports/image.png";

const mono = "'JetBrains Mono', monospace";

const values = [
  {
    title: "Open by Default",
    desc: "Participation is free and open to all developers. No paywalls, no gatekeeping — just code.",
    accent: C.green,
  },
  {
    title: "Fair Judging",
    desc: "Blind review panels, configurable scoring criteria, and tamper-proof timestamps on every submission.",
    accent: C.blue,
  },
  {
    title: "Built for Builders",
    desc: "Every feature on this platform was shaped by real organizer and hacker feedback from past events.",
    accent: C.cyan,
  },
  {
    title: "Open Source",
    desc: "The entire codebase is public. Fork it, self-host it, or contribute a PR. The community owns the platform.",
    accent: C.purple,
  },
];

const stats = [
  { v: "2,400+", l: "Hackers", c: C.green },
  { v: "320+", l: "Teams", c: C.blue },
  { v: "$50K", l: "Prizes", c: C.cyan },
  { v: "6", l: "Seasons", c: C.purple },
];

function PageHeader() {
  const navigate = useNavigate();
  // Người đã đăng nhập: "Home" là dashboard của họ, không phải trang landing.
  const { isAuthenticated } = useAuth();
  const homePath = isAuthenticated ? "/dashboard" : "/";
  return (
    <nav style={{
      position: "fixed", top: 0, left: 0, right: 0, zIndex: 100, height: 60,
      background: "rgba(7,12,15,0.88)", backdropFilter: "blur(16px)",
      borderBottom: "1px solid rgba(34,197,94,0.12)",
    }}>
      <div style={{ maxWidth: 1200, margin: "0 auto", padding: "0 24px", display: "flex", alignItems: "center", justifyContent: "space-between", height: "100%" }}>
        <button onClick={() => navigate(homePath)} style={{ background: "none", border: "none", cursor: "pointer", display: "flex", alignItems: "center", gap: 10 }}>
          <img src={sealLogo} alt="SEAL" style={{ height: 80, width: "auto", objectFit: "contain", filter: "drop-shadow(0 0 8px rgba(34,197,94,0.35))" }} />
          <span style={{ background: C.gradientPrimary, WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent", fontFamily: mono, fontWeight: 700, fontSize: 14, letterSpacing: "0.06em" }}>
            SEAL Hackathon
          </span>
        </button>
        <PixelButton variant="ghost" size="sm" onClick={() => navigate(homePath)}>← Back Home</PixelButton>
      </div>
    </nav>
  );
}

export function AboutPage() {
  return (
    <div style={{ background: C.bg, minHeight: "100vh" }}>
      <PageHeader />

      {/* Hero */}
      <section style={{ paddingTop: 140, paddingBottom: 60, position: "relative", overflow: "hidden" }} className="cyber-grid-bg">
        <FloatingParticles count={18} />
        <div style={{ maxWidth: 1160, margin: "0 auto", padding: "0 24px", textAlign: "center", position: "relative", zIndex: 1 }} className="fade-in-section">
          <div style={{ display: "inline-flex", alignItems: "center", gap: 8, background: "rgba(34,197,94,0.06)", border: "1px solid rgba(34,197,94,0.25)", padding: "5px 14px", marginBottom: 24 }}>
            <span style={{ width: 7, height: 7, borderRadius: "50%", background: C.green, boxShadow: `0 0 8px ${C.green}` }} className="cyber-pulse" />
            <span style={{ color: C.green, fontFamily: mono, fontSize: 10, letterSpacing: "0.15em" }}>ABOUT_PROJECT</span>
          </div>
          <h1 style={{ fontFamily: mono, fontWeight: 900, lineHeight: 1.05, fontSize: "clamp(36px,5vw,60px)", letterSpacing: "-0.025em", marginBottom: 20 }}>
            <GradientText from={C.green} to={C.blue}>About</GradientText>
            <br /><span style={{ color: C.text }}>SEAL Hackathon</span>
          </h1>
          <p style={{ color: C.textMuted, fontFamily: mono, fontSize: 15, lineHeight: 1.8, maxWidth: 640, margin: "0 auto" }}>
            SEAL — Software Engineering Agile League — is a community-run hackathon platform built to make competitive programming events fair, transparent, and genuinely fun to run.
          </p>
        </div>
      </section>

      {/* Stats */}
      <section style={{ background: "#070b12", borderTop: `1px solid ${C.border}`, borderBottom: `1px solid ${C.border}`, padding: "48px 24px" }}>
        <div style={{ maxWidth: 800, margin: "0 auto", display: "flex", justifyContent: "center", flexWrap: "wrap", gap: 40 }}>
          {stats.map((s) => (
            <div key={s.l} style={{ textAlign: "center", fontFamily: mono }}>
              <div style={{ color: s.c, fontSize: 32, fontWeight: 900, textShadow: `0 0 18px ${s.c}` }}>{s.v}</div>
              <div style={{ color: C.textMuted, fontSize: 11, textTransform: "uppercase", letterSpacing: "0.1em", marginTop: 4 }}>{s.l}</div>
            </div>
          ))}
        </div>
      </section>

      {/* Mission */}
      <section style={{ background: C.bg, padding: "80px 0" }} className="cyber-grid-bg">
        <div style={{ maxWidth: 1100, margin: "0 auto", padding: "0 24px" }}>
          <SectionHeader title="Our Mission" gradient />
          <div style={{ maxWidth: 720, margin: "40px auto 0", textAlign: "center" }}>
            <p style={{ color: C.textMuted, fontFamily: mono, fontSize: 15, lineHeight: 1.9 }}>
              We started SEAL because every hackathon we participated in was managed with a patchwork of spreadsheets, Google Forms, and Slack threads. Scores got lost. Deadlines were unclear. Judges didn't know what criteria they were scoring on.
            </p>
            <p style={{ color: C.textMuted, fontFamily: mono, fontSize: 15, lineHeight: 1.9, marginTop: 20 }}>
              SEAL replaces all of that with one cohesive platform — built in public, free to use, and designed to handle every phase of a hackathon from registration to the award ceremony.
            </p>
          </div>
        </div>
      </section>

      {/* Values */}
      <section style={{ background: "#070b12", padding: "80px 0", borderTop: `1px solid ${C.border}` }}>
        <div style={{ maxWidth: 1100, margin: "0 auto", padding: "0 24px" }}>
          <SectionHeader title="What We Stand For" gradient />
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-5 fade-in-section" style={{ marginTop: 48 }}>
            {values.map((val) => (
              <div
                key={val.title}
                style={{
                  background: C.surface,
                  border: `1px solid ${val.accent}22`,
                  padding: "24px",
                  position: "relative",
                  overflow: "hidden",
                  transition: "transform 0.2s ease, box-shadow 0.2s ease",
                  boxShadow: `0 0 20px ${val.accent}0a`,
                }}
                onMouseEnter={(e) => {
                  const el = e.currentTarget as HTMLElement;
                  el.style.boxShadow = `0 0 30px ${val.accent}25, 0 8px 24px rgba(0,0,0,0.4)`;
                  el.style.transform = "translateY(-2px)";
                  el.style.borderColor = `${val.accent}44`;
                }}
                onMouseLeave={(e) => {
                  const el = e.currentTarget as HTMLElement;
                  el.style.boxShadow = `0 0 20px ${val.accent}0a`;
                  el.style.transform = "translateY(0)";
                  el.style.borderColor = `${val.accent}22`;
                }}
              >
                <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${val.accent}, transparent)`, opacity: 0.7 }} />
                <div style={{ position: "absolute", top: 0, left: 0, width: 10, height: 10, borderTop: `2px solid ${val.accent}`, borderLeft: `2px solid ${val.accent}` }} />
                <div style={{ color: val.accent, fontFamily: mono, fontSize: 12, fontWeight: 700, letterSpacing: "0.06em", marginBottom: 10 }}>{val.title}</div>
                <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 13, lineHeight: 1.7 }}>{val.desc}</div>
              </div>
            ))}
          </div>
        </div>
      </section>

      <SealFooter />
    </div>
  );
}
