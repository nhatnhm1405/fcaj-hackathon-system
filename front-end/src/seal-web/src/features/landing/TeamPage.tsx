import { useState } from "react";
import { useNavigate } from "react-router";
import { Github, Mail } from "lucide-react";
import {
  C,
  GradientText,
  PixelButton,
  SectionHeader,
  FloatingParticles,
} from "@/shared/components/PixelComponents";
import { AppFooter } from "@/shared/components/AppFooter";
import { useAuth } from "@/app/providers/AuthProvider";
import brandLogo from "@/imports/seal-logo.svg";

const mono = "'JetBrains Mono', monospace";

// Members and roles follow the GitHub project (nhatnhm1405/fcaj-hackathon-system,
// issues #4–#17). Photos are the members' GitHub avatars, keyed by account id so
// they survive a username change.
interface Member {
  name: string;
  login: string;
  githubId: number;
  role: string;
  desc: string;
  accent: string;
  email?: string;
}

const team: Member[] = [
  {
    name: "Nguyễn Huỳnh Minh Nhật",
    login: "nhatnhm1405",
    githubId: 187357833,
    role: "Team Lead",
    desc: "Sets the project scope, owns the proposal and the AWS architecture, and coordinates the rollout across the team.",
    accent: "#22c55e", // hex: the cards append alpha digits, which a CSS variable cannot take
    email: "nghminhnhat2006@gmail.com",
  },
  {
    name: "Nguyễn Huỳnh Khánh Trang",
    login: "KTrangg",
    githubId: 213317437,
    role: "Frontend",
    desc: "Owns the React app and user flows, its S3 + CloudFront hosting, and the interface for AI judging jobs.",
    accent: C.blue,
    email: "erutrang2004@gmail.com",
  },
  {
    name: "Nguyễn Lê Hữu Khánh",
    login: "HuuKhanh2608",
    githubId: 220602952,
    role: "Backend 1 · Scrum Master",
    desc: "Owns data storage, database migrations, the AI job API and its contract, and keeps the sprint on track.",
    accent: C.cyan,
    email: "nguyenlekhanh2608@gmail.com",
  },
  {
    name: "Trần Vương Quân",
    login: "nauppnouv",
    githubId: 190932859,
    role: "Backend 2 · AWS",
    desc: "Builds the AWS infrastructure and release pipeline: ECR images, SQS messaging, the HTTPS edge and worker deployment.",
    accent: C.purple,
  },
  {
    name: "Thoth3010",
    login: "Thoth3010",
    githubId: 186658249,
    role: "AI Engineer",
    desc: "Builds the AI grading worker: DeepSeek evaluation against the rubric, with evidence traced back to the source code.",
    accent: C.orange,
  },
];

function MemberPhoto({ member }: { member: Member }) {
  const [failed, setFailed] = useState(false);
  if (failed) {
    return (
      <span style={{
        color: member.accent, fontFamily: mono, fontSize: 40, fontWeight: 900,
        opacity: 0.3, letterSpacing: "-0.03em", userSelect: "none",
        textShadow: `0 0 30px ${member.accent}`,
      }}>
        {member.name.split(" ").slice(-2).map((n) => n[0]).join("")}
      </span>
    );
  }
  return (
    <>
      <img
        src={`https://avatars.githubusercontent.com/u/${member.githubId}?v=4&s=460`}
        alt={member.name}
        onError={() => setFailed(true)}
        style={{
          position: "absolute", inset: 0, width: "100%", height: "100%",
          objectFit: "cover", objectPosition: "center",
          display: "block",
        }}
      />
      {/* gradient mờ sang phải để blend với content */}
      <div style={{
        position: "absolute", inset: 0,
        background: `linear-gradient(to right, transparent 82%, ${C.surface} 100%)`,
      }} />
      {/* scanline nhẹ */}
      <div style={{
        position: "absolute", inset: 0,
        backgroundImage: "repeating-linear-gradient(0deg, transparent, transparent 3px, rgba(0,0,0,0.06) 3px, rgba(0,0,0,0.06) 4px)",
        pointerEvents: "none",
      }} />
    </>
  );
}

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

export function TeamPage() {
  return (
    <div style={{ background: C.bg, minHeight: "100vh" }}>
      <PageHeader />

      <section style={{ paddingTop: 140, paddingBottom: 60, position: "relative", overflow: "hidden" }} className="cyber-grid-bg">
        <FloatingParticles count={18} />
        <div style={{ maxWidth: 1160, margin: "0 auto", padding: "0 24px", textAlign: "center", position: "relative", zIndex: 1 }} className="fade-in-section">
          <div style={{ display: "inline-flex", alignItems: "center", gap: 8, background: "rgba(34,197,94,0.06)", border: "1px solid rgba(34,197,94,0.25)", padding: "5px 14px", marginBottom: 24 }}>
            <span style={{ width: 7, height: 7, borderRadius: "50%", background: C.green, boxShadow: `0 0 8px ${C.green}` }} className="cyber-pulse" />
            <span style={{ color: C.green, fontFamily: mono, fontSize: 10, letterSpacing: "0.15em" }}>OUR_TEAM</span>
          </div>
          <h1 style={{ fontFamily: mono, fontWeight: 900, lineHeight: 1.05, fontSize: "clamp(36px,5vw,60px)", letterSpacing: "-0.025em", marginBottom: 20 }}>
            <GradientText from={C.green} to={C.blue}>Meet The</GradientText>
            <br /><span style={{ color: C.text }}>Builders</span>
          </h1>
          <p style={{ color: C.textMuted, fontFamily: mono, fontSize: 15, lineHeight: 1.8, maxWidth: 640, margin: "0 auto" }}>
            Five builders bringing the SEAL hackathon platform to AWS for FCAJ — First Cloud AI Journey.
          </p>
        </div>
      </section>

      <section style={{ background: C.bg, padding: "60px 0 120px" }}>
        <div style={{ maxWidth: 1100, margin: "0 auto", padding: "0 24px" }}>
          <SectionHeader title="The Crew" gradient />
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-5 fade-in-section" style={{ marginTop: 48 }}>
            {team.map((m, i) => (
              <div
                key={m.login}
                // Odd headcount: the lead takes the first row, centred at one column.
                className={i === 0 && team.length % 2 === 1 ? "sm:col-span-2 sm:w-[calc(50%-10px)] sm:justify-self-center" : undefined}
                style={{
                  background: C.surface,
                  border: `1px solid ${m.accent}30`,
                  position: "relative",
                  overflow: "hidden",
                  display: "flex",
                  flexDirection: "row",
                  transition: "transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease",
                  boxShadow: `0 2px 24px rgba(0,0,0,0.35)`,
                }}
                onMouseEnter={(e) => {
                  const el = e.currentTarget as HTMLElement;
                  el.style.transform = "translateY(-3px)";
                  el.style.boxShadow = `0 8px 32px rgba(0,0,0,0.5), 0 0 0 1px ${m.accent}55`;
                  el.style.borderColor = `${m.accent}66`;
                }}
                onMouseLeave={(e) => {
                  const el = e.currentTarget as HTMLElement;
                  el.style.transform = "translateY(0)";
                  el.style.boxShadow = `0 2px 24px rgba(0,0,0,0.35)`;
                  el.style.borderColor = `${m.accent}30`;
                }}
              >
                {/* Accent line trên cùng */}
                <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${m.accent}cc, ${m.accent}11)` }} />

                {/* ── Cột ảnh bên trái ── */}
                <div style={{
                  width: 130, minWidth: 130, position: "relative",
                  background: `linear-gradient(160deg, ${m.accent}18 0%, ${C.bg} 100%)`,
                  borderRight: `1px solid ${m.accent}20`,
                  flexShrink: 0, overflow: "hidden",
                  display: "flex", alignItems: "center", justifyContent: "center",
                }}>
                  <MemberPhoto member={m} />

                  {/* Góc bracket trên trái */}
                  <div style={{ position: "absolute", top: 7, left: 7, width: 12, height: 12, borderTop: `2px solid ${m.accent}99`, borderLeft: `2px solid ${m.accent}99` }} />
                  {/* Góc bracket dưới trái */}
                  <div style={{ position: "absolute", bottom: 7, left: 7, width: 12, height: 12, borderBottom: `2px solid ${m.accent}99`, borderLeft: `2px solid ${m.accent}99` }} />
                </div>

                {/* ── Cột nội dung bên phải ── */}
                <div style={{ flex: 1, padding: "18px 18px 16px", display: "flex", flexDirection: "column", justifyContent: "space-between", minWidth: 0 }}>
                  <div>
                    {/* Role badge */}
                    <div style={{
                      display: "inline-block",
                      background: `${m.accent}15`,
                      border: `1px solid ${m.accent}40`,
                      padding: "2px 8px", marginBottom: 8,
                    }}>
                      <span style={{ color: m.accent, fontFamily: mono, fontSize: 9, letterSpacing: "0.14em", textTransform: "uppercase" }}>
                        {m.role}
                      </span>
                    </div>

                    {/* Tên */}
                    <div style={{ color: C.text, fontFamily: mono, fontSize: 14, fontWeight: 800, marginBottom: 2, lineHeight: 1.3 }}>
                      {m.name}
                    </div>
                    <div style={{ color: m.accent, fontFamily: mono, fontSize: 11, marginBottom: 8 }}>
                      @{m.login}
                    </div>

                    {/* Mô tả */}
                    <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 11, lineHeight: 1.7, marginBottom: 14 }}>
                      {m.desc}
                    </div>
                  </div>

                  {/* Socials */}
                  <div style={{ display: "flex", gap: 8 }}>
                    {[
                      { icon: Github, href: `https://github.com/${m.login}`, label: "GitHub" },
                      ...(m.email ? [{ icon: Mail, href: `https://mail.google.com/mail/?view=cm&to=${m.email}`, label: "Email" }] : []),
                    ].map(({ icon: Icon, href, label }) => (
                      <a key={label} href={href} target="_blank" rel="noopener noreferrer" aria-label={label}
                        style={{ width: 28, height: 28, display: "flex", alignItems: "center", justifyContent: "center", border: `1px solid ${C.border}`, color: C.textMuted, transition: "all 0.15s" }}
                        onMouseEnter={(e) => { const el = e.currentTarget as HTMLElement; el.style.color = m.accent; el.style.borderColor = `${m.accent}88`; el.style.background = `${m.accent}10`; }}
                        onMouseLeave={(e) => { const el = e.currentTarget as HTMLElement; el.style.color = C.textMuted; el.style.borderColor = C.border; el.style.background = "transparent"; }}
                      >
                        <Icon size={13} />
                      </a>
                    ))}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      <AppFooter />
    </div>
  );
}
