import { useState, useEffect, ComponentType } from "react";
import { Rocket, Users, Mail, ShieldCheck, Trophy, ArrowLeft, ArrowRight } from "lucide-react";
import { C, GradientText, PixelButton } from "@/shared/components/PixelComponents";

const mono = "'JetBrains Mono', monospace";

type Step = {
  icon: ComponentType<{ size?: number; strokeWidth?: number }>;
  title: string;
  body: string;
  highlight?: string;
};

const STEPS: Step[] = [
  {
    icon: Rocket,
    title: "Welcome aboard",
    body: "This is your hacker dashboard. To compete you need a team of 3–5 members. There are two ways to get in — here's how.",
  },
  {
    icon: Users,
    title: "Create a team",
    body: "Hit CREATE A TEAM to start your own squad and become its leader. You can then invite 2–4 teammates to fill it up.",
    highlight: "CREATE A TEAM",
  },
  {
    icon: Mail,
    title: "…or wait for an invite",
    body: "Prefer to join someone else? Open WAIT FOR INVITE to see invitations sent to you, or search for a team and request to join.",
    highlight: "WAIT FOR INVITE",
  },
  {
    icon: ShieldCheck,
    title: "Coordinator approval",
    body: "A new team stays PENDING until a coordinator approves it. You'll get a notification the moment your team is approved.",
  },
  {
    icon: Trophy,
    title: "Track & compete",
    body: "Tracks are assigned during the Setup phase — the leader self-selects, or the coordinator draws one. After that: code, submit, and climb the leaderboard!",
  },
];

const KEYFRAMES = `
@keyframes tourWelcome { 0% { opacity: 0; transform: scale(0.9); } 18% { opacity: 1; transform: scale(1); } 78% { opacity: 1; transform: scale(1); } 100% { opacity: 0; transform: scale(1.04); } }
@keyframes tourStepsIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
@keyframes tourCardIn { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
@keyframes tourPulse { 0%, 100% { box-shadow: 0 0 0 0 rgba(34,197,94,0.5); } 50% { box-shadow: 0 0 0 8px rgba(34,197,94,0); } }
`;

export function OnboardingTour({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [phase, setPhase] = useState<"welcome" | "steps">("welcome");
  const [step, setStep] = useState(0);

  // Reset to the welcome splash each time the tour opens; auto-advance to steps.
  useEffect(() => {
    if (!open) return;
    setPhase("welcome");
    setStep(0);
    const t = setTimeout(() => setPhase("steps"), 2000);
    return () => clearTimeout(t);
  }, [open]);

  if (!open) return null;

  const last = STEPS.length - 1;
  const current = STEPS[step];
  const Icon = current.icon;

  return (
    <div style={{ position: "fixed", inset: 0, zIndex: 1100, background: "rgba(7,12,15,0.92)", backdropFilter: "blur(6px)", display: "flex", alignItems: "center", justifyContent: "center", padding: 20 }}>
      <style>{KEYFRAMES}</style>

      {phase === "welcome" ? (
        <div style={{ textAlign: "center", animation: "tourWelcome 2s ease forwards" }}>
          <div style={{ color: C.green, fontFamily: mono, fontSize: 12, letterSpacing: "0.3em", textTransform: "uppercase", marginBottom: 18 }}>
            SEAL Hackathon
          </div>
          <h1 style={{ fontFamily: mono, fontWeight: 900, fontSize: "clamp(34px, 7vw, 64px)", lineHeight: 1.05, margin: 0 }}>
            <GradientText>Welcome our hacker!</GradientText>
          </h1>
          <div style={{ marginTop: 22, display: "flex", justifyContent: "center", gap: 8 }}>
            {[0, 1, 2].map(i => (
              <span key={i} style={{ width: 7, height: 7, borderRadius: "50%", background: C.green, opacity: 0.5, animation: `tourPulse 1.2s ${i * 0.2}s ease-in-out infinite` }} />
            ))}
          </div>
        </div>
      ) : (
        <div style={{ width: "100%", maxWidth: 560, animation: "tourStepsIn 0.4s ease" }}>
          {/* Numbered stepper */}
          <div style={{ display: "flex", alignItems: "center", justifyContent: "center", marginBottom: 28 }}>
            {STEPS.map((_, i) => {
              const done = i < step;
              const active = i === step;
              const color = active || done ? C.green : C.border;
              return (
                <div key={i} style={{ display: "flex", alignItems: "center" }}>
                  <button
                    onClick={() => setStep(i)}
                    aria-label={`Step ${i + 1}`}
                    style={{
                      width: 36, height: 36, borderRadius: "50%", flexShrink: 0, cursor: "pointer",
                      background: active ? "rgba(34,197,94,0.15)" : done ? "rgba(34,197,94,0.08)" : "transparent",
                      border: `2px solid ${color}`,
                      color: active || done ? C.green : C.textMuted,
                      fontFamily: mono, fontSize: 14, fontWeight: 700,
                      display: "flex", alignItems: "center", justifyContent: "center",
                      transition: "all 0.25s ease",
                      animation: active ? "tourPulse 1.6s ease-in-out infinite" : "none",
                      boxShadow: active ? `0 0 14px rgba(34,197,94,0.4)` : "none",
                    }}
                  >
                    {i + 1}
                  </button>
                  {i < last && (
                    <span style={{ width: 28, height: 2, background: i < step ? C.green : C.border, transition: "background 0.25s ease" }} />
                  )}
                </div>
              );
            })}
          </div>

          {/* Step card */}
          <div key={step} style={{ position: "relative", background: C.surface, border: `1px solid ${C.border}`, padding: "32px 30px", textAlign: "center", overflow: "hidden", animation: "tourCardIn 0.3s ease", boxShadow: "0 0 40px rgba(34,197,94,0.1), 0 20px 60px rgba(0,0,0,0.5)" }}>
            <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${C.green}, ${C.blue}, transparent)`, opacity: 0.8 }} />

            <div style={{ width: 66, height: 66, margin: "0 auto 20px", borderRadius: "50%", display: "flex", alignItems: "center", justifyContent: "center", background: "rgba(34,197,94,0.1)", border: `1px solid rgba(34,197,94,0.4)`, color: C.green, boxShadow: "0 0 24px rgba(34,197,94,0.2)" }}>
              <Icon size={30} strokeWidth={1.8} />
            </div>

            <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 11, letterSpacing: "0.16em", marginBottom: 8 }}>
              STEP {step + 1} / {STEPS.length}
            </div>
            <h2 style={{ fontFamily: mono, fontWeight: 800, fontSize: 22, lineHeight: 1.2, margin: "0 0 14px" }}>
              <GradientText>{current.title}</GradientText>
            </h2>
            <p style={{ color: C.text, fontFamily: mono, fontSize: 13.5, lineHeight: 1.8, margin: "0 auto", maxWidth: 440 }}>
              {current.body}
            </p>

            {current.highlight && (
              <div style={{ display: "inline-block", marginTop: 18, padding: "7px 16px", background: "rgba(34,197,94,0.08)", border: `1px solid rgba(34,197,94,0.45)`, color: C.green, fontFamily: mono, fontSize: 11, fontWeight: 700, letterSpacing: "0.1em" }}>
                ↳ {current.highlight}
              </div>
            )}
          </div>

          {/* Controls */}
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginTop: 22, gap: 12 }}>
            <button
              onClick={onClose}
              style={{ background: "transparent", border: "none", cursor: "pointer", color: C.textMuted, fontFamily: mono, fontSize: 12, letterSpacing: "0.08em", padding: "8px 4px", transition: "color 0.15s" }}
              onMouseEnter={(e) => { e.currentTarget.style.color = C.text; }}
              onMouseLeave={(e) => { e.currentTarget.style.color = C.textMuted; }}
            >
              SKIP TOUR
            </button>

            <div style={{ display: "flex", gap: 10 }}>
              {step > 0 && (
                <PixelButton variant="ghost" onClick={() => setStep(s => Math.max(0, s - 1))}>
                  <span style={{ display: "inline-flex", alignItems: "center", gap: 6 }}><ArrowLeft size={14} /> BACK</span>
                </PixelButton>
              )}
              {step < last ? (
                <PixelButton variant="cyber" onClick={() => setStep(s => Math.min(last, s + 1))}>
                  <span style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>NEXT <ArrowRight size={14} /></span>
                </PixelButton>
              ) : (
                <PixelButton variant="cyber" onClick={onClose}>LET'S GO 🚀</PixelButton>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
