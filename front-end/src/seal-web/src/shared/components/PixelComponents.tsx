import { useState, useEffect, useRef } from "react";

// ── Color tokens ─────────────────────────────────────────────────
export const C = {
  // Backgrounds
  bg:       "var(--c-bg)",
  surface:  "var(--c-surface)",
  surface2: "var(--c-surface2)",
  surface3: "var(--c-surface3)",

  // Green (primary accent)
  green:         "var(--c-accent)",
  greenBright:   "var(--c-accent-bright)",
  greenDim:      "var(--c-accent-dim)",
  greenMuted:    "var(--c-text-muted)",
  greenGlow:     "var(--c-glow)",
  greenGlowFaint:"var(--c-glow-faint)",

  // Blue (secondary cyber accent)
  blue:          "#3b82f6",
  blueBright:    "#60a5fa",
  blueDim:       "#1d4ed8",
  blueGlow:      "rgba(59,130,246,0.3)",
  blueGlowFaint: "rgba(59,130,246,0.1)",

  // Cyan (innovation highlight)
  cyan:      "#06b6d4",
  cyanBright:"#22d3ee",
  cyanGlow:  "rgba(6,182,212,0.35)",

  // Purple (AI / ML accent)
  purple:    "#8b5cf6",
  purpleGlow:"rgba(139,92,246,0.3)",

  // Text
  text:     "var(--c-text)",
  textMuted:"var(--c-text-muted)",
  textDim:  "var(--c-text-dim)",
  textBlue: "var(--c-text-blue)",

  // Borders
  border:       "var(--c-border)",
  borderBright: "var(--c-border-bright)",
  borderBlue:   "var(--c-border-blue)",

  // Status
  red:    "#ef4444",
  yellow: "#eab308",
  orange: "#f97316",

  // Text placed on accent backgrounds
  onAccent: "var(--c-on-accent)",

  // Special backgrounds
  navbarBg:  "var(--c-navbar-bg)",
  footerBg:  "var(--c-footer-bg)",
  copyright: "var(--c-copyright)",

  // Gradients
  gradientPrimary: "linear-gradient(135deg, #22c55e 0%, #3b82f6 100%)",
  gradientCyber:   "linear-gradient(135deg, var(--c-surface) 0%, var(--c-surface2) 100%)",
  gradientCard:    "linear-gradient(135deg, rgba(34,197,94,0.06) 0%, rgba(59,130,246,0.04) 100%)",
  gradientHero:    "linear-gradient(160deg, rgba(34,197,94,0.08) 0%, rgba(59,130,246,0.06) 50%, rgba(6,182,212,0.04) 100%)",
};

// ── Gradient text helper ─────────────────────────────────────────
interface GradientTextProps {
  children: React.ReactNode;
  from?: string;
  to?: string;
  style?: React.CSSProperties;
  className?: string;
}
export function GradientText({ children, from = C.green, to = C.blue, style, className = "" }: GradientTextProps) {
  return (
    <span
      style={{
        background: `linear-gradient(135deg, ${from} 0%, ${to} 100%)`,
        WebkitBackgroundClip: "text",
        WebkitTextFillColor: "transparent",
        backgroundClip: "text",
        ...style,
      }}
      className={className}
    >{children}</span>
  );
}

// ── PixelButton ──────────────────────────────────────────────────
interface PixelButtonProps {
  children: React.ReactNode;
  onClick?: () => void;
  variant?: "primary" | "secondary" | "ghost" | "danger" | "cyber" | "warning";
  size?: "sm" | "md" | "lg";
  className?: string;
  disabled?: boolean;
  type?: "button" | "submit" | "reset";
  fullWidth?: boolean;
  // Accessibility passthroughs (used by menu triggers such as PixelMenu).
  ariaLabel?: string;
  ariaHasPopup?: React.AriaAttributes["aria-haspopup"];
  ariaExpanded?: boolean;
}

export function PixelButton({
  children, onClick, variant = "primary", size = "md",
  className = "", disabled = false, type = "button", fullWidth = false,
  ariaLabel, ariaHasPopup, ariaExpanded,
}: PixelButtonProps) {
  const sizeClasses = { sm: "px-3 py-1.5 text-xs", md: "px-5 py-2.5 text-sm", lg: "px-8 py-3.5 text-base" };

  const baseStyle: React.CSSProperties = {
    fontFamily: "'JetBrains Mono', monospace",
    borderRadius: 0,
    cursor: disabled ? "not-allowed" : "pointer",
    opacity: disabled ? 0.45 : 1,
    transition: "all 0.18s ease",
    position: "relative",
    overflow: "hidden",
    letterSpacing: "0.06em",
    textTransform: "uppercase",
  };

  const variantStyles: Record<string, React.CSSProperties> = {
    primary: {
      background: C.green,
      color: C.onAccent,
      border: `1px solid ${C.green}`,
      boxShadow: `0 0 16px ${C.greenGlow}, inset 0 1px 0 rgba(255,255,255,0.1)`,
      fontWeight: 700,
    },
    cyber: {
      background: "linear-gradient(135deg, #22c55e 0%, #3b82f6 100%)",
      color: "#ffffff",
      border: "none",
      boxShadow: `0 0 20px rgba(34,197,94,0.25), 0 0 40px rgba(59,130,246,0.15)`,
      fontWeight: 700,
    },
    secondary: {
      background: "rgba(34,197,94,0.06)",
      color: C.green,
      border: `1px solid rgba(34,197,94,0.35)`,
      boxShadow: `inset 0 0 20px rgba(34,197,94,0.04)`,
    },
    ghost: {
      background: "transparent",
      color: C.textMuted,
      border: `1px solid ${C.border}`,
      boxShadow: "none",
    },
    danger: {
      background: "rgba(239,68,68,0.06)",
      color: C.red,
      border: `1px solid rgba(239,68,68,0.35)`,
      boxShadow: "none",
    },
    warning: {
      background: "rgba(234,179,8,0.08)",
      color: "#facc15",
      border: `1px solid rgba(234,179,8,0.4)`,
      boxShadow: "inset 0 0 20px rgba(234,179,8,0.04)",
    },
  };

  const hoverStyles: Record<string, React.CSSProperties> = {
    primary: { background: C.greenBright, boxShadow: `0 0 28px rgba(34,197,94,0.7), 0 4px 16px rgba(0,0,0,0.4)` },
    cyber:   { boxShadow: `0 0 30px rgba(34,197,94,0.5), 0 0 60px rgba(59,130,246,0.3)`, filter: "brightness(1.1)" },
    secondary: { background: "rgba(34,197,94,0.12)", borderColor: C.green, boxShadow: `0 0 16px ${C.greenGlow}` },
    ghost:   { borderColor: C.green, color: C.green, background: "rgba(34,197,94,0.04)" },
    danger:  { background: "rgba(239,68,68,0.12)", boxShadow: "0 0 12px rgba(239,68,68,0.3)" },
    warning: { background: "rgba(234,179,8,0.16)", borderColor: C.yellow, boxShadow: "0 0 14px rgba(234,179,8,0.35)" },
  };

  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      aria-label={ariaLabel}
      aria-haspopup={ariaHasPopup}
      aria-expanded={ariaExpanded}
      style={{ ...baseStyle, ...variantStyles[variant], ...(fullWidth ? { width: "100%", display: "flex", justifyContent: "center" } : {}) }}
      className={`inline-flex items-center justify-center gap-2 font-mono ${sizeClasses[size]} ${className}`}
      onMouseEnter={(e) => { if (!disabled) Object.assign((e.currentTarget as HTMLElement).style, hoverStyles[variant]); }}
      onMouseLeave={(e) => { if (!disabled) Object.assign((e.currentTarget as HTMLElement).style, variantStyles[variant]); }}
    >{children}</button>
  );
}

// ── PixelCard ────────────────────────────────────────────────────
interface PixelCardProps {
  children: React.ReactNode;
  className?: string;
  glow?: boolean;
  glowColor?: "green" | "blue" | "cyan" | "purple" | "amber";
  gradient?: boolean;
  style?: React.CSSProperties;
  onClick?: () => void;
}

export function PixelCard({ children, className = "", glow = false, glowColor = "green", gradient = false, style = {}, onClick }: PixelCardProps) {
  const glowMap = {
    green:  `0 0 0 1px rgba(34,197,94,0.12), 0 0 24px rgba(34,197,94,0.14), inset 0 0 40px rgba(34,197,94,0.03)`,
    blue:   `0 0 0 1px rgba(59,130,246,0.15), 0 0 24px rgba(59,130,246,0.12), inset 0 0 40px rgba(59,130,246,0.03)`,
    cyan:   `0 0 0 1px rgba(6,182,212,0.15), 0 0 20px rgba(6,182,212,0.14)`,
    purple: `0 0 0 1px rgba(139,92,246,0.15), 0 0 20px rgba(139,92,246,0.14)`,
    amber:  `0 0 0 1px rgba(234,179,8,0.18), 0 0 24px rgba(234,179,8,0.14), inset 0 0 40px rgba(234,179,8,0.03)`,
  };
  const accentMap = { green: C.green, blue: C.blue, cyan: C.cyan, purple: C.purple, amber: C.yellow };

  return (
    <div
      onClick={onClick}
      style={{
        background: gradient ? C.gradientCard : C.surface,
        border: `1px solid ${glow ? accentMap[glowColor] + "33" : C.border}`,
        borderRadius: 0,
        boxShadow: glow ? glowMap[glowColor] : `var(--c-card-shadow)`,
        position: "relative",
        overflow: "hidden",
        transition: "box-shadow 0.2s ease",
        ...style,
      }}
      className={`${onClick ? "cursor-pointer" : ""} ${className}`}
    >
      {/* Gradient top accent bar */}
      {gradient && (
        <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 1, background: C.gradientPrimary, opacity: 0.6 }} />
      )}
      {/* Corner accents */}
      <div style={{ position: "absolute", top: 0, left: 0, width: 10, height: 10, borderTop: `2px solid ${accentMap[glowColor]}`, borderLeft: `2px solid ${accentMap[glowColor]}`, opacity: 0.7 }} />
      <div style={{ position: "absolute", top: 0, right: 0, width: 10, height: 10, borderTop: `2px solid ${accentMap[glowColor]}`, borderRight: `2px solid ${accentMap[glowColor]}`, opacity: 0.7 }} />
      <div style={{ position: "absolute", bottom: 0, left: 0, width: 10, height: 10, borderBottom: `2px solid ${accentMap[glowColor]}`, borderLeft: `2px solid ${accentMap[glowColor]}`, opacity: 0.4 }} />
      <div style={{ position: "absolute", bottom: 0, right: 0, width: 10, height: 10, borderBottom: `2px solid ${accentMap[glowColor]}`, borderRight: `2px solid ${accentMap[glowColor]}`, opacity: 0.4 }} />
      {children}
    </div>
  );
}

// ── CyberStatCard ────────────────────────────────────────────────
interface CyberStatCardProps {
  value: string | number;
  label: string;
  icon?: string;
  trend?: string;
  accent?: "green" | "blue" | "cyan" | "purple";
  sublabel?: string;
  /** Glow halo around the card + number. Off for a calmer, flatter tile. */
  glow?: boolean;
}

export function CyberStatCard({ value, label, icon, trend, accent = "green", sublabel, glow = true }: CyberStatCardProps) {
  const accentColors = {
    green:  { main: C.green, glow: C.greenGlow, dim: "rgba(34,197,94,0.08)" },
    blue:   { main: C.blue, glow: C.blueGlow, dim: "rgba(59,130,246,0.08)" },
    cyan:   { main: C.cyan, glow: C.cyanGlow, dim: "rgba(6,182,212,0.08)" },
    purple: { main: C.purple, glow: C.purpleGlow, dim: "rgba(139,92,246,0.08)" },
  };
  const a = accentColors[accent];

  return (
    <div
      style={{
        background: C.surface,
        border: `1px solid ${a.main}22`,
        borderRadius: 0,
        padding: "20px",
        position: "relative",
        overflow: "hidden",
        boxShadow: glow ? `0 0 20px ${a.dim}, inset 0 0 30px ${a.dim}` : "var(--c-card-shadow)",
      }}
    >
      {/* Gradient fill bottom accent */}
      <div style={{ position: "absolute", bottom: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${a.main}, transparent)`, opacity: 0.6 }} />

      <div style={{ position: "relative", zIndex: 1, display: "flex", flexDirection: "column", gap: 4 }}>
        <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between" }}>
          {icon && <span style={{ fontSize: 18, opacity: 0.9 }}>{icon}</span>}
          {trend && (
            <span style={{ color: trend.startsWith("+") ? C.green : C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 10, letterSpacing: "0.05em" }}>
              {trend}
            </span>
          )}
        </div>
        <div style={{ color: a.main, fontFamily: "'JetBrains Mono', monospace", fontSize: 30, fontWeight: 800, textShadow: glow ? `0 0 20px ${a.glow}` : "none", lineHeight: 1.1, marginTop: 6 }}>
          {value}
        </div>
        <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, fontWeight: 500 }}>
          {label}
        </div>
        {sublabel && (
          <div style={{ color: C.textBlue, fontFamily: "'JetBrains Mono', monospace", fontSize: 11 }}>{sublabel}</div>
        )}
      </div>

      {/* Top-left corner accent */}
      <div style={{ position: "absolute", top: 0, left: 0, width: 12, height: 12, borderTop: `2px solid ${a.main}`, borderLeft: `2px solid ${a.main}`, opacity: 0.8 }} />
      <div style={{ position: "absolute", top: 0, right: 0, width: 12, height: 12, borderTop: `2px solid ${a.main}`, borderRight: `2px solid ${a.main}`, opacity: 0.4 }} />
    </div>
  );
}

// ── PixelInput ───────────────────────────────────────────────────
interface PixelInputProps {
  label?: string;
  placeholder?: string;
  type?: string;
  value?: string;
  onChange?: (e: React.ChangeEvent<HTMLInputElement>) => void;
  onKeyDown?: (e: React.KeyboardEvent<HTMLInputElement>) => void;
  onBlur?: (e: React.FocusEvent<HTMLInputElement>) => void;
  min?: string;
  max?: string;
  prefix?: string;
  className?: string;
  disabled?: boolean;
  showToggle?: boolean;
  lang?: string;
  name?: string;
  autoComplete?: string;
}

function EyeOpen() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}

function EyeClosed() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" />
      <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" />
      <line x1="1" y1="1" x2="23" y2="23" />
    </svg>
  );
}

export function PixelInput({ label, placeholder, type = "text", value, onChange, onKeyDown, onBlur, min, max, prefix, className = "", disabled = false, showToggle = false, lang, name, autoComplete }: PixelInputProps) {
  const [focused, setFocused] = useState(false);
  const [visible, setVisible] = useState(false);
  const [hoverEye, setHoverEye] = useState(false);
  const isPassword = type === "password";
  const resolvedType = isPassword && showToggle ? (visible ? "text" : "password") : type;
  return (
    <div className={`flex flex-col gap-1.5 ${className}`}>
      {label && (
        <label style={{ fontFamily: "'JetBrains Mono', monospace", color: C.greenMuted, fontSize: 12, letterSpacing: "0.04em", fontWeight: 500 }}>
          {label}
        </label>
      )}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          background: C.surface2,
          border: focused ? `1px solid ${C.green}` : `1px solid ${C.border}`,
          borderRadius: 0,
          boxShadow: focused ? `0 0 12px rgba(34,197,94,0.15), 0 0 20px rgba(59,130,246,0.08)` : "none",
          transition: "all 0.15s ease",
          position: "relative",
        }}
      >
        <input
          type={resolvedType} value={value} onChange={onChange} onKeyDown={onKeyDown} placeholder={placeholder} disabled={disabled} min={min} max={max} lang={lang} name={name} autoComplete={autoComplete}
          onFocus={() => setFocused(true)} onBlur={(e) => { setFocused(false); onBlur?.(e); }}
          style={{
            background: "transparent", border: "none", outline: "none",
            color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 14,
            padding: prefix ? "10px 12px 10px 0" : "10px 12px", width: "100%", caretColor: C.green,
            paddingRight: isPassword && showToggle ? 40 : undefined,
          }}
        />
        {isPassword && showToggle && (
          <button
            type="button"
            onClick={() => setVisible((v) => !v)}
            onMouseEnter={() => setHoverEye(true)}
            onMouseLeave={() => setHoverEye(false)}
            style={{
              position: "absolute", right: 10, top: "50%", transform: "translateY(-50%)",
              background: "none", border: "none", cursor: "pointer", padding: 4,
              display: "flex", alignItems: "center",
              color: hoverEye ? C.text : C.textMuted,
              transition: "color 0.15s",
            }}
          >
            {visible ? <EyeOpen /> : <EyeClosed />}
          </button>
        )}
      </div>
    </div>
  );
}

// ── PixelBadge ───────────────────────────────────────────────────
interface PixelBadgeProps {
  children: React.ReactNode;
  color?: "green" | "yellow" | "red" | "blue" | "orange" | "gray" | "cyan" | "purple";
  /** Adds a colored glow halo around the badge, for content that should stand
   * out from surrounding plain text rather than read as a routine status tag. */
  glow?: boolean;
}

export function PixelBadge({ children, color = "green", glow = false }: PixelBadgeProps) {
  const colors = {
    green:  { bg: "rgba(34,197,94,0.1)",   border: "rgba(34,197,94,0.35)",  text: "#4ade80", glow: "rgba(34,197,94,0.45)" },
    yellow: { bg: "rgba(234,179,8,0.1)",   border: "rgba(234,179,8,0.35)",  text: "#facc15", glow: "rgba(234,179,8,0.45)" },
    red:    { bg: "rgba(239,68,68,0.1)",   border: "rgba(239,68,68,0.35)",  text: "#f87171", glow: "rgba(239,68,68,0.45)" },
    blue:   { bg: "rgba(59,130,246,0.1)",  border: "rgba(59,130,246,0.35)", text: "#60a5fa", glow: "rgba(59,130,246,0.45)" },
    orange: { bg: "rgba(249,115,22,0.1)",  border: "rgba(249,115,22,0.35)", text: "#fb923c", glow: "rgba(249,115,22,0.45)" },
    gray:   { bg: "rgba(107,114,128,0.1)", border: "rgba(107,114,128,0.3)", text: "#9ca3af", glow: "rgba(107,114,128,0.35)" },
    cyan:   { bg: "rgba(6,182,212,0.1)",   border: "rgba(6,182,212,0.35)",  text: "#22d3ee", glow: "rgba(6,182,212,0.5)" },
    purple: { bg: "rgba(139,92,246,0.1)",  border: "rgba(139,92,246,0.35)", text: "#a78bfa", glow: "rgba(139,92,246,0.45)" },
  };
  const c = colors[color];
  return (
    <span
      style={{
        background: c.bg, border: `1px solid ${c.border}`, color: c.text,
        fontFamily: "'JetBrains Mono', monospace", fontSize: 11,
        padding: "3px 9px", letterSpacing: "0.06em", borderRadius: 0,
        boxShadow: glow ? `0 0 10px ${c.glow}, inset 0 0 14px ${c.bg}` : undefined,
      }}
      className={`pixel-badge pixel-badge--${color} uppercase inline-flex items-center gap-1`}
    >
      {children}
    </span>
  );
}

// ── PixelProgress ────────────────────────────────────────────────
interface PixelProgressProps {
  value: number;
  max?: number;
  label?: string;
  color?: string;
  showValue?: boolean;
  gradient?: boolean;
}

export function PixelProgress({ value, max = 100, label, color = C.green, showValue = true, gradient = false }: PixelProgressProps) {
  const pct = Math.min(100, (value / max) * 100);
  const fill = gradient ? "linear-gradient(90deg, #22c55e, #3b82f6)" : color;
  return (
    <div className="flex flex-col gap-1">
      {(label || showValue) && (
        <div className="flex justify-between items-center">
          {label && <span style={{ color: C.textMuted, fontSize: 11, fontFamily: "'JetBrains Mono', monospace" }}>{label}</span>}
          {showValue && <span style={{ color: C.green, fontSize: 11, fontFamily: "'JetBrains Mono', monospace" }}>{value}/{max}</span>}
        </div>
      )}
      <div style={{ height: 5, background: C.surface3, borderRadius: 0, overflow: "hidden", border: `1px solid ${C.border}` }}>
        <div style={{ height: "100%", width: `${pct}%`, background: fill, boxShadow: `0 0 6px ${color}`, transition: "width 0.8s ease" }} />
      </div>
    </div>
  );
}

// ── PixelStat (legacy, kept for compatibility) ───────────────────
interface PixelStatProps {
  value: string | number;
  label: string;
  icon?: string;
  trend?: string;
  color?: string;
}
export function PixelStat({ value, label, icon, trend, color = C.green }: PixelStatProps) {
  return <CyberStatCard value={value} label={label} icon={icon} trend={trend} accent={color === C.blue ? "blue" : color === C.cyan ? "cyan" : "green"} />;
}

// ── TerminalWindow ───────────────────────────────────────────────
interface TerminalWindowProps {
  title?: string;
  children: React.ReactNode;
  className?: string;
}

export function TerminalWindow({ title = "terminal", children, className = "" }: TerminalWindowProps) {
  return (
    <div
      style={{
        background: "#050c07",
        border: `1px solid rgba(34,197,94,0.2)`,
        borderRadius: 0,
        boxShadow: `0 0 30px rgba(34,197,94,0.08), 0 0 60px rgba(59,130,246,0.04)`,
      }}
      className={className}
    >
      <div
        style={{
          background: "linear-gradient(90deg, #0d1117, #0a1020)",
          borderBottom: `1px solid rgba(34,197,94,0.15)`,
          padding: "7px 14px",
          display: "flex",
          alignItems: "center",
          gap: 8,
        }}
      >
        <div style={{ display: "flex", gap: 6 }}>
          <div style={{ width: 8, height: 8, background: C.red, borderRadius: 0 }} />
          <div style={{ width: 8, height: 8, background: C.yellow, borderRadius: 0 }} />
          <div style={{ width: 8, height: 8, background: C.green, borderRadius: 0 }} />
        </div>
        <span style={{ color: "rgba(134,239,172,0.5)", fontSize: 10, fontFamily: "'JetBrains Mono', monospace", marginLeft: 6, letterSpacing: "0.08em" }}>
          {title}
        </span>
        {/* Fake data-flow line */}
        <div style={{ flex: 1, height: 1, background: "linear-gradient(90deg, transparent, rgba(59,130,246,0.3), transparent)", overflow: "hidden", position: "relative" }}>
          <div className="data-flow" style={{ position: "absolute", inset: 0, background: "linear-gradient(90deg, transparent, #60a5fa, transparent)", height: "100%" }} />
        </div>
      </div>
      <div style={{ padding: "16px", fontFamily: "'JetBrains Mono', monospace" }}>{children}</div>
    </div>
  );
}

// ── TypingText ───────────────────────────────────────────────────
interface TypingTextProps {
  phrases: string[];
  speed?: number;
  deleteSpeed?: number;
  pauseMs?: number;
  style?: React.CSSProperties;
}

export function TypingText({ phrases, speed = 60, deleteSpeed = 30, pauseMs = 2000, style }: TypingTextProps) {
  const [displayed, setDisplayed] = useState("");
  const [phraseIdx, setPhraseIdx] = useState(0);
  const [typing, setTyping] = useState(true);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const current = phrases[phraseIdx % phrases.length];
    if (typing) {
      if (displayed.length < current.length) {
        timeoutRef.current = setTimeout(() => setDisplayed(current.slice(0, displayed.length + 1)), speed);
      } else {
        timeoutRef.current = setTimeout(() => setTyping(false), pauseMs);
      }
    } else {
      if (displayed.length > 0) {
        timeoutRef.current = setTimeout(() => setDisplayed(displayed.slice(0, -1)), deleteSpeed);
      } else {
        setPhraseIdx((i) => i + 1);
        setTyping(true);
      }
    }
    return () => { if (timeoutRef.current) clearTimeout(timeoutRef.current); };
  }, [displayed, typing, phraseIdx, phrases, speed, deleteSpeed, pauseMs]);

  return (
    <span style={{ fontFamily: "'JetBrains Mono', monospace", ...style }}>
      {displayed}
      <span className="cursor-blink" style={{ color: C.green }} />
    </span>
  );
}

// ── PixelTable ───────────────────────────────────────────────────
interface Column<T> {
  key: keyof T;
  header: string;
  render?: (val: T[keyof T], row: T) => React.ReactNode;
  width?: string;
}
interface PixelTableProps<T> {
  columns: Column<T>[];
  data: T[];
  className?: string;
}
export function PixelTable<T extends Record<string, unknown>>({ columns, data, className = "" }: PixelTableProps<T>) {
  return (
    <div style={{ border: `1px solid ${C.border}`, borderRadius: 0, overflow: "hidden" }} className={className}>
      <div style={{ overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead>
            <tr style={{ background: C.surface2, borderBottom: `1px solid ${C.border}` }}>
              {columns.map((col) => (
                <th key={String(col.key)} style={{ color: C.green, fontSize: 10, letterSpacing: "0.12em", textAlign: "left", padding: "10px 16px", fontWeight: 600, width: col.width, textTransform: "uppercase" }}>
                  {col.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {data.map((row, i) => (
              <tr key={i} style={{ borderBottom: `1px solid rgba(34,197,94,0.06)`, background: i % 2 === 0 ? C.surface : C.surface2 }}
                className="hover:bg-[rgba(34,197,94,0.03)] transition-colors">
                {columns.map((col) => (
                  <td key={String(col.key)} style={{ color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, padding: "10px 16px" }}>
                    {col.render ? col.render(row[col.key], row) : String(row[col.key] ?? "")}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

// ── PixelTabs ────────────────────────────────────────────────────
interface Tab { id: string; label: string; icon?: string; badge?: number; }
interface PixelTabsProps { tabs: Tab[]; active: string; onChange: (id: string) => void; className?: string; }

export function PixelTabs({ tabs, active, onChange, className = "" }: PixelTabsProps) {
  return (
    <div className={`flex ${className}`} style={{ borderBottom: `1px solid ${C.border}` }}>
      {tabs.map((tab) => (
        <button
          key={tab.id}
          onClick={() => onChange(tab.id)}
          style={{
            fontFamily: "'JetBrains Mono', monospace",
            fontSize: 11,
            letterSpacing: "0.08em",
            padding: "10px 18px",
            borderTop: "none",
            borderLeft: "none",
            borderRight: "none",
            borderBottom: active === tab.id ? `2px solid ${C.green}` : "2px solid transparent",
            color: active === tab.id ? C.green : C.textMuted,
            background: active === tab.id ? "rgba(34,197,94,0.04)" : "transparent",
            cursor: "pointer",
            textTransform: "uppercase",
            transition: "all 0.15s ease",
            boxShadow: active === tab.id ? `inset 0 -2px 0 ${C.green}` : "none",
            display: "inline-flex",
            alignItems: "center",
            gap: 7,
          }}
        >
          {tab.icon && <span className="mr-1">{tab.icon}</span>}
          {tab.label}
          {!!tab.badge && (
            <span style={{
              display: "inline-flex", alignItems: "center", justifyContent: "center",
              minWidth: 16, height: 16, padding: "0 5px", borderRadius: 8,
              background: "#eab308", color: "#1a1a1a", fontSize: 10, fontWeight: 800,
              lineHeight: 1, fontFamily: "'JetBrains Mono', monospace",
            }}>
              {tab.badge}
            </span>
          )}
        </button>
      ))}
    </div>
  );
}

// ── FloatingParticles ────────────────────────────────────────────
interface Particle {
  id: number; x: number; y: number; size: number; delay: number;
  duration: number; opacity: number; color: string; shape: "square" | "diamond";
}

interface FloatingParticlesProps { count?: number; className?: string; }

export function FloatingParticles({ count = 28, className = "" }: FloatingParticlesProps) {
  const palette = [C.green, C.green, C.green, C.blue, C.cyan, C.blue];
  const [particles] = useState<Particle[]>(() =>
    Array.from({ length: count }, (_, i) => ({
      id: i,
      x: Math.random() * 100,
      y: Math.random() * 100,
      size: (Math.floor(Math.random() * 3) + 1) * 3,
      delay: Math.random() * 6,
      duration: Math.random() * 4 + 3,
      opacity: Math.random() * 0.35 + 0.04,
      color: palette[Math.floor(Math.random() * palette.length)],
      shape: Math.random() > 0.7 ? "diamond" : "square",
    }))
  );

  return (
    <div className={`pointer-events-none absolute inset-0 overflow-hidden ${className}`}>
      {particles.map((p) => (
        <div
          key={p.id}
          style={{
            position: "absolute",
            left: `${p.x}%`,
            top: `${p.y}%`,
            width: p.size,
            height: p.size,
            background: p.color,
            opacity: p.opacity,
            animationName: "pixelFloat",
            animationDuration: `${p.duration}s`,
            animationDelay: `${p.delay}s`,
            animationTimingFunction: "ease-in-out",
            animationIterationCount: "infinite",
            borderRadius: 0,
            transform: p.shape === "diamond" ? "rotate(45deg)" : "none",
            boxShadow: `0 0 ${p.size * 2}px ${p.color}`,
          }}
        />
      ))}
    </div>
  );
}

// ── SectionHeader ────────────────────────────────────────────────
interface SectionHeaderProps {
  prefix?: string;
  title: string;
  subtitle?: string;
  align?: "left" | "center";
  gradient?: boolean;
}

export function SectionHeader({ prefix = "//", title, subtitle, align = "center", gradient = false }: SectionHeaderProps) {
  return (
    <div style={{ textAlign: align }}>
      <h2 style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: "clamp(22px, 3vw, 32px)", fontWeight: 800, lineHeight: 1.2 }}>
        {gradient ? (
          <GradientText>{title}</GradientText>
        ) : (
          <span style={{ color: C.text }}>{title}</span>
        )}
      </h2>
      {subtitle && (
        <p style={{
          color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 14, lineHeight: 1.75,
          maxWidth: align === "center" ? 560 : "none",
          margin: align === "center" ? "14px auto 0" : "14px 0 0",
        }}>
          {subtitle}
        </p>
      )}
    </div>
  );
}

// ── CircuitLines (decorative) ────────────────────────────────────
export function CircuitLines({ className = "" }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 400 200"
      className={`pointer-events-none ${className}`}
      style={{ opacity: 0.12 }}
      fill="none"
    >
      {/* Horizontal lines */}
      <line x1="0" y1="40" x2="160" y2="40" stroke="#22c55e" strokeWidth="1" />
      <line x1="180" y1="40" x2="280" y2="40" stroke="#3b82f6" strokeWidth="1" />
      <line x1="300" y1="40" x2="400" y2="40" stroke="#06b6d4" strokeWidth="1" />
      <line x1="0" y1="120" x2="80" y2="120" stroke="#3b82f6" strokeWidth="1" />
      <line x1="100" y1="120" x2="260" y2="120" stroke="#22c55e" strokeWidth="1" />
      <line x1="280" y1="120" x2="400" y2="120" stroke="#3b82f6" strokeWidth="1" />
      {/* Vertical connectors */}
      <line x1="160" y1="40" x2="160" y2="120" stroke="#22c55e" strokeWidth="1" />
      <line x1="280" y1="40" x2="280" y2="120" stroke="#3b82f6" strokeWidth="1" />
      <line x1="80" y1="120" x2="80" y2="180" stroke="#06b6d4" strokeWidth="1" />
      <line x1="260" y1="120" x2="260" y2="60" stroke="#22c55e" strokeWidth="1" />
      {/* Nodes */}
      {[
        [160, 40, "#22c55e"], [280, 40, "#3b82f6"],
        [160, 120, "#22c55e"], [280, 120, "#3b82f6"],
        [80, 120, "#06b6d4"], [260, 120, "#22c55e"],
      ].map(([x, y, color], i) => (
        <rect key={i} x={Number(x) - 3} y={Number(y) - 3} width="6" height="6" fill={String(color)} />
      ))}
    </svg>
  );
}
