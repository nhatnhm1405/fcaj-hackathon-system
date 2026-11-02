import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { useAuth } from "@/app/providers/AuthProvider";
import { C, GradientText } from "@/shared/components/PixelComponents";

const mono = "'JetBrains Mono', monospace";

interface RoleConfig {
  label: string;
  description: string;
  path: string;
  accentColor: string;
}

const ROLE_CONFIG: Record<string, RoleConfig> = {
  // Actual backend role names (Role.roleName values from DB)
  JUDGE: {
    label: "Judge",
    description: "Score submissions",
    path: "/dashboard/judge",
    accentColor: "#3b82f6",
  },
  MENTOR: {
    label: "Mentor",
    description: "Guide teams",
    path: "/dashboard/mentor",
    accentColor: "#06b6d4",
  },
  EVENT_COORDINATOR: {
    label: "Coordinator",
    description: "Manage events",
    path: "/dashboard/coordinator",
    accentColor: "#eab308",
  },
  // Aliases kept for backward compatibility
  COORDINATOR: {
    label: "Coordinator",
    description: "Manage events",
    path: "/dashboard/coordinator",
    accentColor: "#eab308",
  },
  JUDGE_INTERNAL: {
    label: "Judge",
    description: "Score submissions",
    path: "/dashboard/judge",
    accentColor: "#3b82f6",
  },
};

function getRoleConfig(role: string): RoleConfig {
  const exact = ROLE_CONFIG[role];
  if (exact) return exact;
  // Fallback for partial matches (e.g. EVENT_COORDINATOR)
  const upper = role.toUpperCase();
  if (upper.includes("COORDINATOR")) return ROLE_CONFIG["COORDINATOR"];
  if (upper.includes("JUDGE"))       return ROLE_CONFIG["JUDGE_INTERNAL"];
  if (upper.includes("MENTOR"))      return ROLE_CONFIG["MENTOR"];
  return { label: role, description: "", path: "/dashboard", accentColor: C.green };
}

const BRIEF =
  "As a FPT Lecturer, you are an internal judge — so you are able to switch between " +
  "either Mentor or Judge if granted by the administrator.";

// Role glyph — a recognisable icon per role so a card reads at a glance:
// Judge → balance scale (fair scoring), Mentor → lightbulb (guidance/ideas),
// anything else → a generic person.
function RoleIcon({ label, color }: { label: string; color: string }) {
  const common = {
    width: 27, height: 27, viewBox: "0 0 24 24", fill: "none",
    stroke: color, strokeWidth: 1.7, strokeLinecap: "round" as const, strokeLinejoin: "round" as const,
    style: { pointerEvents: "none" as const },
  };
  const key = label.toLowerCase();
  if (key.includes("judge")) {
    // Clipboard with a checkmark — scoring / grading submissions
    return (
      <svg {...common}>
        <rect x="8" y="2" width="8" height="4" rx="1" ry="1" />
        <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2" />
        <path d="m9 14 2 2 4-4" />
      </svg>
    );
  }
  if (key.includes("mentor")) {
    // Graduation cap — teacher / lecturer
    return (
      <svg {...common}>
        <path d="M22 10 12 5 2 10l10 5 10-5Z" />
        <path d="M6 12v5c0 1 2.5 2.5 6 2.5s6-1.5 6-2.5v-5" />
        <path d="M22 10v6" />
      </svg>
    );
  }
  // Fallback — person
  return (
    <svg {...common}>
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1" />
    </svg>
  );
}

export function RoleSelector() {
  const navigate = useNavigate();
  const { availableRoles, setActiveRole } = useAuth();
  const [briefOpen, setBriefOpen] = useState(false);

  // Single role — skip selector and redirect immediately
  useEffect(() => {
    if (availableRoles.length === 1) {
      const cfg = getRoleConfig(availableRoles[0]);
      setActiveRole(availableRoles[0]);
      navigate(cfg.path, { replace: true });
    }
  }, [availableRoles, navigate, setActiveRole]);

  if (availableRoles.length === 0 || availableRoles.length === 1) return null;

  function handleSelect(role: string) {
    const cfg = getRoleConfig(role);
    setActiveRole(role);
    navigate(cfg.path);
  }

  return (
    <div style={{
      minHeight: "100vh",
      background: C.bg,
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      padding: "32px 16px",
    }}>
      <div style={{ width: "100%", maxWidth: 640 }}>
        {/* Header */}
        <div style={{ textAlign: "center", marginBottom: 40 }}>
          <h1 style={{
            fontFamily: mono,
            fontWeight: 900,
            fontSize: 28,
            lineHeight: 1.1,
            marginBottom: 12,
            display: "inline-flex",
            alignItems: "center",
            gap: 10,
          }}>
            <GradientText>Welcome back FPT Lecturer!</GradientText>
            {/* ? brief — hover to reveal */}
            <span
              onMouseEnter={() => setBriefOpen(true)}
              onMouseLeave={() => setBriefOpen(false)}
              style={{ position: "relative", display: "inline-flex", alignItems: "center" }}
            >
              <span
                aria-label="About your roles"
                style={{
                  width: 22,
                  height: 22,
                  borderRadius: "50%",
                  border: `1px solid ${C.border}`,
                  color: C.textMuted,
                  fontFamily: mono,
                  fontSize: 13,
                  fontWeight: 700,
                  display: "inline-flex",
                  alignItems: "center",
                  justifyContent: "center",
                  cursor: "help",
                  transition: "all 0.15s",
                  ...(briefOpen ? { color: C.green, borderColor: "rgba(34,197,94,0.5)" } : {}),
                }}
              >
                ?
              </span>
              {briefOpen && (
                <div
                  role="tooltip"
                  style={{
                    position: "absolute",
                    top: "calc(100% + 10px)",
                    left: "50%",
                    transform: "translateX(-50%)",
                    width: 280,
                    background: C.surface,
                    border: `1px solid ${C.border}`,
                    boxShadow: "0 8px 28px rgba(0,0,0,0.5)",
                    padding: "12px 14px",
                    color: C.textMuted,
                    fontFamily: mono,
                    fontSize: 11,
                    fontWeight: 400,
                    lineHeight: 1.7,
                    letterSpacing: "0.01em",
                    textAlign: "left",
                    zIndex: 50,
                    whiteSpace: "normal",
                  }}
                >
                  {BRIEF}
                </div>
              )}
            </span>
          </h1>
          <p style={{
            color: C.textMuted,
            fontFamily: mono,
            fontSize: 12,
            lineHeight: 1.7,
          }}>
            Select the role you want to use for this session.
          </p>
        </div>

        {/* Role cards — side by side */}
        <div style={{ display: "flex", flexWrap: "wrap", gap: 16, justifyContent: "center" }}>
          {availableRoles.map((role) => {
            const cfg = getRoleConfig(role);
            return (
              <button
                key={role}
                onClick={() => handleSelect(role)}
                style={{
                  flex: "1 1 220px",
                  maxWidth: 300,
                  minWidth: 200,
                  background: "rgba(34,197,94,0.03)",
                  border: `1px solid ${C.border}`,
                  borderTop: `3px solid ${cfg.accentColor}`,
                  padding: "28px 24px 26px",
                  cursor: "pointer",
                  textAlign: "center",
                  borderRadius: 0,
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "center",
                  gap: 12,
                  transition: "all 0.15s ease",
                }}
                onMouseEnter={(e) => {
                  const el = e.currentTarget as HTMLElement;
                  el.style.background = `rgba(34,197,94,0.07)`;
                  el.style.borderColor = cfg.accentColor;
                  el.style.borderTopColor = cfg.accentColor;
                  el.style.boxShadow = `0 0 20px rgba(34,197,94,0.1)`;
                  el.style.transform = "translateY(-2px)";
                }}
                onMouseLeave={(e) => {
                  const el = e.currentTarget as HTMLElement;
                  el.style.background = "rgba(34,197,94,0.03)";
                  el.style.borderColor = C.border;
                  el.style.borderTopColor = cfg.accentColor;
                  el.style.boxShadow = "none";
                  el.style.transform = "translateY(0)";
                }}
              >
                {/* Person icon in a circular chip — like a profile avatar */}
                <div style={{
                  width: 56,
                  height: 56,
                  borderRadius: "50%",
                  background: "rgba(34,197,94,0.04)",
                  border: `1px solid ${cfg.accentColor}55`,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  flexShrink: 0,
                }}>
                  <RoleIcon label={cfg.label} color={cfg.accentColor} />
                </div>
                <div style={{
                  color: cfg.accentColor,
                  fontFamily: mono,
                  fontSize: 16,
                  fontWeight: 700,
                  letterSpacing: "0.06em",
                }}>
                  {cfg.label}
                </div>
                {cfg.description && (
                  <div style={{
                    color: C.textMuted,
                    fontFamily: mono,
                    fontSize: 11,
                    letterSpacing: "0.02em",
                  }}>
                    {cfg.description}
                  </div>
                )}
              </button>
            );
          })}
        </div>

        <p style={{
          marginTop: 24,
          textAlign: "center",
          color: C.textDim,
          fontFamily: mono,
          fontSize: 10,
          letterSpacing: "0.06em",
        }}>
          You can switch roles at any time from the profile menu
        </p>
      </div>
    </div>
  );
}
