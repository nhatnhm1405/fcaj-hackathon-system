import { useNavigate } from "react-router";
import { Github, Mail } from "lucide-react";
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
import KhanhNHL from "@/imports/KhannhNLH.jpg";
import NhatNHM from "@/imports/NhatNHM.jpg";
import TrangNHK from "@/imports/TrangNHK.jpg";
import DaoNH from "@/imports/DaoHN.jpg";

const mono = "'JetBrains Mono', monospace";

const team = [
  {
    name: "Nguyễn Huỳnh Minh Nhật",
    role: "Lead Engineer & Architect",
    photo: NhatNHM, // ← CHÈN ẢNH TẠI ĐÂY — VD: "/src/imports/nhat.jpg"
    desc: "Designs the platform's role-based access model, scoring engine, and judge workflows. Loves clean APIs and merciless code reviews.",
    accent: C.green,
    socials: { github: "https://github.com/nhatnhm1405", email: "https://mail.google.com/mail/?view=cm&to=nghminhnhat2006@gmail.com" },
  },
  {
    name: "Nguyễn Huỳnh Khánh Trang",
    role: "Frontend & UI/UX",
    photo: TrangNHK, // ← CHÈN ẢNH TẠI ĐÂY — VD: "/src/imports/khanh.jpg"
    desc: "Owns the cyberpunk visual language and pixel-art component library. Pushes pixels until they glow exactly right.",
    accent: C.blue,
    socials: { github: "https://github.com/KTrangg", email: "https://mail.google.com/mail/?view=cm&to=erutrang2004@gmail.com" },
  },
  {
    name: "Đào Hoàng Nhật",
    role: "Backend & DevOps",
    photo: DaoNH, // ← CHÈN ẢNH TẠI ĐÂY — VD: "/src/imports/hoang.jpg"
    desc: "Keeps the leaderboard live, the submissions stamped, and the deploys boring. Believes in zero-downtime everything.",
    accent: C.cyan,
    socials: { github: "https://github.com/hoangnhat1407", email: "https://mail.google.com/mail/?view=cm&to=daonhat082@gmail.com" },
  },
  {
    name: "Nguyễn Lê Hữu Khánh",
    role: "Frontend & UI/UX",
    photo: KhanhNHL, // ← CHÈN ẢNH TẠI ĐÂY — VD: "/src/imports/trang.jpg"
    desc: "Owns the cyberpunk visual language and pixel-art component library. Pushes pixels until they glow exactly right.",
    accent: C.purple,
    socials: { github: "https://github.com/HuuKhanh2608", email: "https://mail.google.com/mail/?view=cm&to=nguyenlekhanh@gmail.com" },
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
            Four engineers, designers, and operators who built SEAL because we wanted the tool we wished we had.
          </p>
        </div>
      </section>

      <section style={{ background: C.bg, padding: "60px 0 120px" }}>
        <div style={{ maxWidth: 1100, margin: "0 auto", padding: "0 24px" }}>
          <SectionHeader title="The Crew" gradient />
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-5 fade-in-section" style={{ marginTop: 48 }}>
            {team.map((m) => (
              <div
                key={m.name}
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
                  {m.photo ? (
                    <>
                      <img
                        src={m.photo}
                        alt={m.name}
                        style={{
                          width: "100%", height: "100%",
                          objectFit: "cover", objectPosition: "center top",
                          display: "block",
                        }}
                      />
                      {/* gradient mờ sang phải để blend với content */}
                      <div style={{
                        position: "absolute", inset: 0,
                        background: `linear-gradient(to right, transparent 60%, ${C.surface} 100%)`,
                      }} />
                      {/* scanline nhẹ */}
                      <div style={{
                        position: "absolute", inset: 0,
                        backgroundImage: "repeating-linear-gradient(0deg, transparent, transparent 3px, rgba(0,0,0,0.06) 3px, rgba(0,0,0,0.06) 4px)",
                        pointerEvents: "none",
                      }} />
                    </>
                  ) : (
                    /* Fallback initials */
                    <span style={{
                      color: m.accent, fontFamily: mono, fontSize: 40, fontWeight: 900,
                      opacity: 0.3, letterSpacing: "-0.03em", userSelect: "none",
                      textShadow: `0 0 30px ${m.accent}`,
                    }}>
                      {m.name.split(" ").slice(-2).map((n) => n[0]).join("")}
                    </span>
                  )}

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
                    <div style={{ color: C.text, fontFamily: mono, fontSize: 14, fontWeight: 800, marginBottom: 8, lineHeight: 1.3 }}>
                      {m.name}
                    </div>

                    {/* Mô tả */}
                    <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 11, lineHeight: 1.7, marginBottom: 14 }}>
                      {m.desc}
                    </div>
                  </div>

                  {/* Socials */}
                  <div style={{ display: "flex", gap: 8 }}>
                    {[
                      { icon: Github, href: m.socials.github, label: "GitHub" },
                      { icon: Mail,   href: m.socials.email,  label: "Email" },
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

      <SealFooter />
    </div>
  );
}
