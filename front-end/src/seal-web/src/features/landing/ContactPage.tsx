import { useNavigate } from "react-router";
import { Mail } from "lucide-react";
import {
  C,
  GradientText,
  PixelButton,
  FloatingParticles,
} from "@/shared/components/PixelComponents";
import { SealFooter } from "@/shared/components/SealFooter";
import { useAuth } from "@/app/providers/AuthProvider";
import sealLogo from "@/imports/image.png";

const mono = "'JetBrains Mono', monospace";
const EMAIL = "contact@sealhackathon.com";

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

export function ContactPage() {
  return (
    <div style={{ background: C.bg, minHeight: "100vh", display: "flex", flexDirection: "column" }}>
      <PageHeader />

      <section
        style={{
          flex: 1,
          paddingTop: 140,
          paddingBottom: 100,
          position: "relative",
          overflow: "hidden",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}
        className="cyber-grid-bg"
      >
        <FloatingParticles count={18} />

        {/* Ambient glow */}
        <div style={{
          position: "absolute", top: "50%", left: "50%",
          transform: "translate(-50%,-50%)",
          width: 520, height: 320,
          background: "radial-gradient(ellipse, rgba(34,197,94,0.08) 0%, transparent 70%)",
          pointerEvents: "none",
        }} />

        <div style={{
          maxWidth: 640, margin: "0 auto", padding: "0 24px",
          textAlign: "center", position: "relative", zIndex: 1,
        }} className="fade-in-section">
          <div style={{
            display: "inline-flex", alignItems: "center", gap: 8,
            background: "rgba(34,197,94,0.06)",
            border: "1px solid rgba(34,197,94,0.25)",
            padding: "5px 14px", marginBottom: 24,
          }}>
            <span style={{ width: 7, height: 7, borderRadius: "50%", background: C.green, boxShadow: `0 0 8px ${C.green}` }} className="cyber-pulse" />
            <span style={{ color: C.green, fontFamily: mono, fontSize: 10, letterSpacing: "0.15em" }}>
              CONTACT
            </span>
          </div>

          <h1 style={{
            fontFamily: mono, fontWeight: 900, lineHeight: 1.05,
            fontSize: "clamp(36px,5vw,60px)", letterSpacing: "-0.025em",
            marginBottom: 24,
          }}>
            <GradientText from={C.green} to={C.blue}>Contact</GradientText>
          </h1>

          <p style={{
            color: C.textMuted, fontFamily: mono, fontSize: 14,
            letterSpacing: "0.04em", marginBottom: 40,
          }}>
            → reach us at the address below
          </p>

          {/* Email terminal panel */}
          <div style={{
            display: "inline-block",
            background: C.surface,
            border: `1px solid ${C.green}33`,
            padding: "28px 36px",
            position: "relative",
            overflow: "hidden",
            boxShadow: `0 0 24px ${C.green}11`,
          }}>
            <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${C.green}, transparent)`, opacity: 0.7 }} />
            <div style={{ position: "absolute", top: 0, left: 0, width: 12, height: 12, borderTop: `2px solid ${C.green}`, borderLeft: `2px solid ${C.green}` }} />
            <div style={{ position: "absolute", bottom: 0, right: 0, width: 12, height: 12, borderBottom: `2px solid ${C.green}`, borderRight: `2px solid ${C.green}` }} />

            <div style={{
              color: C.green, fontFamily: mono, fontSize: 10,
              letterSpacing: "0.18em", marginBottom: 12, textAlign: "left",
            }}>
              // EMAIL
            </div>

            <a
              href={`mailto:${EMAIL}`}
              style={{
                display: "inline-flex",
                alignItems: "center",
                gap: 12,
                color: C.green,
                fontFamily: mono,
                fontSize: "clamp(18px, 2.4vw, 24px)",
                fontWeight: 700,
                letterSpacing: "0.02em",
                textDecoration: "none",
                padding: "6px 4px",
                borderBottom: `1px solid transparent`,
                transition: "color 0.2s, text-shadow 0.2s, border-color 0.2s, transform 0.2s",
              }}
              onMouseEnter={(e) => {
                const el = e.currentTarget as HTMLElement;
                el.style.textShadow = `0 0 12px ${C.green}, 0 0 24px ${C.green}88`;
                el.style.borderBottomColor = C.green;
                el.style.transform = "translateY(-1px)";
              }}
              onMouseLeave={(e) => {
                const el = e.currentTarget as HTMLElement;
                el.style.textShadow = "none";
                el.style.borderBottomColor = "transparent";
                el.style.transform = "translateY(0)";
              }}
            >
              <Mail size={20} />
              {EMAIL}
            </a>
          </div>

          <div style={{
            marginTop: 28,
            color: "rgba(134,239,172,0.4)",
            fontFamily: mono, fontSize: 11, letterSpacing: "0.1em",
          }}>
            ↳ click to open in your mail app
          </div>
        </div>
      </section>

      <SealFooter />
    </div>
  );
}
