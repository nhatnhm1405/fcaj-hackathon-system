import { useNavigate } from "react-router";
import {
  C, GradientText, PixelButton, PixelCard, FloatingParticles,
} from "@/shared/components/PixelComponents";
import { SealFooter } from "@/shared/components/SealFooter";
import { useAuth } from "@/app/providers/AuthProvider";
import sealLogo from "@/imports/image.png";

export function PendingApprovalPage() {
  const navigate = useNavigate();
  const { isAuthenticated, logout } = useAuth();

  return (
    <div style={{ background: C.bg, display: "flex", flexDirection: "column", minHeight: "100vh" }}>
    <div
      style={{
        flex: 1,
        position: "relative",
        overflow: "hidden",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: 24,
      }}
      className="cyber-grid-bg"
    >
      <FloatingParticles count={30} />
      <div style={{ position: "absolute", top: "20%", left: "50%", transform: "translateX(-50%)", width: 500, height: 500, borderRadius: "50%", background: "radial-gradient(circle, rgba(234,179,8,0.06), transparent 70%)", pointerEvents: "none" }} />

      <div style={{ position: "relative", zIndex: 2, maxWidth: 540, width: "100%", textAlign: "center" }}>
        <div style={{ height: 120, overflow: "visible", margin: "0 auto 24px", display: "flex", alignItems: "center", justifyContent: "center" }}>
          <img src={sealLogo} alt="HMS" style={{ height: 240, width: "auto", objectFit: "contain", filter: "drop-shadow(0 0 12px rgba(34,197,94,0.4))" }} />
        </div>

        <PixelCard glow glowColor="blue" gradient style={{ padding: 36 }}>
          <h1 style={{ fontFamily: "'JetBrains Mono', monospace", fontWeight: 900, fontSize: 32, lineHeight: 1.15, marginBottom: 16 }}>
            <GradientText>Application Submitted</GradientText>
          </h1>

          <div style={{
            background: "rgba(234,179,8,0.06)",
            border: "1px solid rgba(234,179,8,0.3)",
            padding: "14px 18px",
            margin: "20px 0",
          }}>
            <p style={{ color: C.yellow, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, letterSpacing: "0.06em", textTransform: "uppercase" }}>
              Awaiting Coordinator Approval
            </p>
          </div>

          <p style={{ color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, lineHeight: 1.8, marginBottom: 12 }}>
            Your account is under review. You will be notified by email once approved.
          </p>
          <p style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, lineHeight: 1.7, marginBottom: 24 }}>
            This process typically takes 1-2 business days.
          </p>

          {isAuthenticated ? (
            <PixelButton variant="cyber" size="lg" onClick={() => { logout(); navigate('/login', { replace: true }); }}>
              SIGN OUT
            </PixelButton>
          ) : (
            <PixelButton variant="cyber" size="lg" onClick={() => navigate('/')}>
              BACK TO HOME
            </PixelButton>
          )}
        </PixelCard>
      </div>
    </div>
    <SealFooter />
    </div>
  );
}
