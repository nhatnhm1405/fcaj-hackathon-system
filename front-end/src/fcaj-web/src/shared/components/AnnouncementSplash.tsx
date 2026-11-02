import { Mail } from "lucide-react";
import { C, GradientText, PixelButton } from "@/shared/components/PixelComponents";

const mono = "'JetBrains Mono', monospace";

const KEYFRAMES = `
@keyframes annSplashIn { from { opacity: 0; transform: translateY(10px) scale(0.96); } to { opacity: 1; transform: translateY(0) scale(1); } }
@keyframes annSplashPulse { 0%, 100% { box-shadow: 0 0 0 0 rgba(34,197,94,0.5); } 50% { box-shadow: 0 0 0 10px rgba(34,197,94,0); } }
`;

/**
 * Welcome-style splash that announces freshly-arrived announcement messages.
 * Shown once per batch of new announcements (see NotificationProvider).
 */
export function AnnouncementSplash({
  open, count, from, onView, onClose,
}: {
  open: boolean;
  count: number;
  from: string;
  onView: () => void;
  onClose: () => void;
}) {
  if (!open) return null;

  return (
    <div style={{ position: "fixed", inset: 0, zIndex: 1100, background: "rgba(7,12,15,0.92)", backdropFilter: "blur(6px)", display: "flex", alignItems: "center", justifyContent: "center", padding: 20 }}>
      <style>{KEYFRAMES}</style>
      <div style={{ width: "100%", maxWidth: 460, position: "relative", background: C.surface, border: `1px solid ${C.border}`, padding: "34px 30px", textAlign: "center", overflow: "hidden", animation: "annSplashIn 0.4s ease", boxShadow: "0 0 40px rgba(34,197,94,0.1), 0 20px 60px rgba(0,0,0,0.5)" }}>
        <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${C.green}, ${C.blue}, transparent)`, opacity: 0.8 }} />

        <div style={{ width: 66, height: 66, margin: "0 auto 20px", borderRadius: "50%", display: "flex", alignItems: "center", justifyContent: "center", background: "rgba(34,197,94,0.1)", border: `1px solid rgba(34,197,94,0.4)`, color: C.green, animation: "annSplashPulse 1.8s ease-in-out infinite" }}>
          <Mail size={30} strokeWidth={1.8} />
        </div>

        <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 11, letterSpacing: "0.16em", marginBottom: 8, textTransform: "uppercase" }}>
          New {count === 1 ? "announcement" : "announcements"}
        </div>
        <h2 style={{ fontFamily: mono, fontWeight: 800, fontSize: 24, lineHeight: 1.2, margin: "0 0 10px" }}>
          <GradientText>You have {count} {count === 1 ? "message" : "messages"}</GradientText>
        </h2>
        <p style={{ color: C.text, fontFamily: mono, fontSize: 13, lineHeight: 1.7, margin: "0 auto 22px", maxWidth: 360 }}>
          from <span style={{ color: C.green, fontWeight: 700 }}>{from}</span>
        </p>

        <div style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 10 }}>
          <PixelButton variant="ghost" onClick={onClose}>DISMISS</PixelButton>
          <PixelButton variant="cyber" onClick={onView}>VIEW MESSAGES</PixelButton>
        </div>
      </div>
    </div>
  );
}
