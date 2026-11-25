import { useNavigate } from "react-router";
import {
  C,
  GradientText,
  PixelBadge,
  PixelButton,
  SectionHeader,
  FloatingParticles,
} from "@/shared/components/PixelComponents";
import { AppFooter } from "@/shared/components/AppFooter";
import { useAuth } from "@/app/providers/AuthProvider";
import brandLogo from "@/imports/seal-logo.svg";

const mono = "'JetBrains Mono', monospace";

// Hex, not C.green: the cards append alpha digits, which a CSS variable cannot take.
const GREEN = "#22c55e";

const stats = [
  { v: "5", l: "Builders", c: GREEN },
  { v: "5", l: "User roles", c: C.blue },
  { v: "0", l: "Scores set by AI", c: C.cyan },
];

const aiSteps = [
  { title: "01 / Request", desc: "A judge assigned to the submission asks for an analysis. The API captures the code, returns a job without waiting for the model, and the judge keeps scoring.", accent: GREEN },
  { title: "02 / Queue", desc: "The source snapshot and rubric are stored in S3 and the job waits in SQS. If the worker is offline, the request is kept, not lost.", accent: C.blue },
  { title: "03 / Analyse", desc: "The worker reads the code at one fixed commit, evaluates it against the round's criteria, and any evidence must point to files and lines it was given.", accent: C.cyan },
  { title: "04 / Decide", desc: "The backend validates the result before showing it. It is advice only: the judge sets every score.", accent: C.purple },
];

// Keep the status in step with the project: "Verified" means checked on the AWS
// proof of concept (docs/report/issue-14-execution-status-2026-11-05.md).
type Status = "Verified in PoC" | "In progress";

const architecture: { title: string; desc: string; status: Status; accent: string }[] = [
  { title: "Frontend", desc: "React + Vite single-page app on a private S3 bucket, served over HTTPS through CloudFront.", status: "Verified in PoC", accent: GREEN },
  { title: "API", desc: "Java 21 + Spring Boot in Docker on EC2, behind an HTTPS edge with CORS and CSRF protection.", status: "Verified in PoC", accent: C.blue },
  { title: "Data", desc: "MySQL on Amazon RDS with versioned Flyway migrations and a schema gate that runs before the backend is replaced.", status: "Verified in PoC", accent: C.cyan },
  { title: "Messaging", desc: "SQS queues carry AI jobs out and results back. One S3 and SQS round trip has been checked with a mock worker.", status: "Verified in PoC", accent: C.purple },
  { title: "AI worker", desc: "Python on AWS Lambda, calling DeepSeek. The worker is written; connecting it to the live job flow is under way.", status: "In progress", accent: C.orange },
  { title: "Delivery", desc: "GitHub Actions tests and builds immutable images for ECR, with releases through Systems Manager behind a gate.", status: "In progress", accent: C.blueBright },
];

const values = [
  {
    title: "Judges Decide",
    desc: "AI output is guidance only. It never writes a score or a ranking.",
    accent: GREEN,
  },
  {
    title: "Access by Assignment",
    desc: "Judges and coordinators can only request and read analyses for the events and submissions they are assigned to.",
    accent: C.blue,
  },
  {
    title: "Traceable Results",
    desc: "Every job is pinned to a commit, a rubric version and a model configuration, so a result can be traced back to its input.",
    accent: C.cyan,
  },
  {
    title: "Built in the Open",
    desc: "Code, contracts, plans and review notes are public on GitHub, including what is not finished yet.",
    accent: C.purple,
  },
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
          <img src={brandLogo} alt="SEAL" style={{ height: 42, width: "auto", objectFit: "contain", filter: "drop-shadow(0 0 8px rgba(34,197,94,0.35))" }} />
          <span style={{ background: C.gradientPrimary, WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent", fontFamily: mono, fontWeight: 700, fontSize: 14, letterSpacing: "0.06em" }}>
            SEAL Hackathon
          </span>
        </button>
        <PixelButton variant="ghost" size="sm" onClick={() => navigate(homePath)}>← Back Home</PixelButton>
      </div>
    </nav>
  );
}

function InfoCard({ title, desc, accent, status }: { title: string; desc: string; accent: string; status?: Status }) {
  return (
    <div
      style={{
        background: C.surface,
        border: `1px solid color-mix(in srgb, ${accent} 13%, transparent)`,
        padding: "24px",
        position: "relative",
        overflow: "hidden",
        transition: "transform 0.2s ease, box-shadow 0.2s ease",
        boxShadow: `0 0 20px color-mix(in srgb, ${accent} 4%, transparent)`,
      }}
      onMouseEnter={(e) => {
        const el = e.currentTarget as HTMLElement;
        el.style.boxShadow = `0 0 30px color-mix(in srgb, ${accent} 15%, transparent), 0 8px 24px rgba(0,0,0,0.4)`;
        el.style.transform = "translateY(-2px)";
        el.style.borderColor = `color-mix(in srgb, ${accent} 27%, transparent)`;
      }}
      onMouseLeave={(e) => {
        const el = e.currentTarget as HTMLElement;
        el.style.boxShadow = `0 0 20px color-mix(in srgb, ${accent} 4%, transparent)`;
        el.style.transform = "translateY(0)";
        el.style.borderColor = `color-mix(in srgb, ${accent} 13%, transparent)`;
      }}
    >
      <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${accent}, transparent)`, opacity: 0.7 }} />
      <div style={{ position: "absolute", top: 0, left: 0, width: 10, height: 10, borderTop: `2px solid ${accent}`, borderLeft: `2px solid ${accent}` }} />
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: 8, marginBottom: 10 }}>
        <div style={{ color: accent, fontFamily: mono, fontSize: 12, fontWeight: 700, letterSpacing: "0.06em" }}>{title}</div>
        {status && <PixelBadge color={status === "Verified in PoC" ? "green" : "yellow"}>{status}</PixelBadge>}
      </div>
      <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 13, lineHeight: 1.7 }}>{desc}</div>
    </div>
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
            <br /><span style={{ color: C.text }}>FCAJ Hackathon</span>
          </h1>
          <p style={{ color: C.textMuted, fontFamily: mono, fontSize: 15, lineHeight: 1.8, maxWidth: 680, margin: "0 auto" }}>
            Our FCAJ — First Cloud AI Journey — project: taking SEAL, a hackathon management platform, to AWS and giving its judges an AI assistant whose findings are checked against the code.
          </p>
        </div>
      </section>

      {/* Stats */}
      <section style={{ background: "#070b12", borderTop: `1px solid ${C.border}`, borderBottom: `1px solid ${C.border}`, padding: "48px 24px" }}>
        <div style={{ maxWidth: 800, margin: "0 auto", display: "flex", justifyContent: "center", flexWrap: "wrap", gap: "40px 72px" }}>
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
              SEAL — Software Engineering Agile League — runs a hackathon from registration and teams to submissions, multi-round judging and awards, with a workspace for participants, mentors, judges, coordinators and admins.
            </p>
            <p style={{ color: C.textMuted, fontFamily: mono, fontSize: 15, lineHeight: 1.9, marginTop: 20 }}>
              For FCAJ we keep that product and modernize what is underneath: managed AWS services for hosting, data and messaging, and AI analysis that runs as a background job so judging never has to wait for a model.
            </p>
          </div>
        </div>
      </section>

      {/* AI judging */}
      <section style={{ background: "#070b12", padding: "80px 0", borderTop: `1px solid ${C.border}` }}>
        <div style={{ maxWidth: 1100, margin: "0 auto", padding: "0 24px" }}>
          <SectionHeader title="How AI Judging Works" subtitle="The design we are building toward." gradient />
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-5 fade-in-section" style={{ marginTop: 48 }}>
            {aiSteps.map((step) => <InfoCard key={step.title} {...step} />)}
          </div>
        </div>
      </section>

      {/* Architecture */}
      <section style={{ background: C.bg, padding: "80px 0", borderTop: `1px solid ${C.border}` }} className="cyber-grid-bg">
        <div style={{ maxWidth: 1100, margin: "0 auto", padding: "0 24px" }}>
          <SectionHeader title="Built on AWS" subtitle="A proof of concept in the Singapore region, and where each part stands." gradient />
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5 fade-in-section" style={{ marginTop: 48 }}>
            {architecture.map((part) => <InfoCard key={part.title} {...part} />)}
          </div>
        </div>
      </section>

      {/* Values */}
      <section style={{ background: "#070b12", padding: "80px 0", borderTop: `1px solid ${C.border}` }}>
        <div style={{ maxWidth: 1100, margin: "0 auto", padding: "0 24px" }}>
          <SectionHeader title="What We Stand For" gradient />
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-5 fade-in-section" style={{ marginTop: 48 }}>
            {values.map((val) => <InfoCard key={val.title} {...val} />)}
          </div>
        </div>
      </section>

      <AppFooter />
    </div>
  );
}
