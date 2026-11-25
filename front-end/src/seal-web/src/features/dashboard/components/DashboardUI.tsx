import type { ReactNode } from "react";
import { C, FloatingParticles, GradientText, PixelButton, PixelCard } from "@/shared/components/PixelComponents";
import { eventPhaseBanner, PROGRESS_STEPS, progressIndex } from "./eventPhase";

// Presentational pieces shared by the role dashboards. No data or navigation logic
// here. The staff dashboards reuse the participant look: cyber grid, floating
// pixels, and two cards per page (the contest, then the work list).
// Green marks progress and buttons, yellow marks what needs attention.

export const MONO = "'JetBrains Mono', monospace";

interface DashboardHeroProps {
  eyebrow: string;
  title: ReactNode;
  subtitle?: ReactNode;
  glowColor?: "green" | "blue";
  children?: ReactNode;
}

export function DashboardHero({ eyebrow, title, subtitle, glowColor = "green", children }: DashboardHeroProps) {
  return (
    <PixelCard glow glowColor={glowColor} gradient className="dash-card dash-card--hero">
      <div style={{ color: C.green, fontFamily: MONO, fontSize: 14, fontWeight: 700, marginBottom: 6 }}>{eyebrow}</div>
      <h1 className="dash-hero-title" style={{ fontFamily: MONO, fontWeight: 800, lineHeight: 1.2, overflowWrap: "anywhere" }}>
        <GradientText>{title}</GradientText>
      </h1>
      {subtitle && (
        <p style={{ color: C.textMuted, fontFamily: MONO, fontSize: 13, lineHeight: 1.6, marginTop: 6 }}>{subtitle}</p>
      )}
      {children}
    </PixelCard>
  );
}

interface SectionTitleProps {
  children: ReactNode;
  color?: string;
  /** Badge or button shown on the right of the heading. */
  action?: ReactNode;
}

export function SectionTitle({ children, color = C.green, action }: SectionTitleProps) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap", marginBottom: 14 }}>
      <h2 style={{ color, fontFamily: MONO, fontSize: 16, fontWeight: 700, lineHeight: 1.3 }}>{children}</h2>
      {action}
    </div>
  );
}

export function DashboardError({ message }: { message: string }) {
  return (
    <div role="alert" style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: MONO, fontSize: 12, padding: "10px 14px", overflowWrap: "anywhere" }}>
      ERROR: {message}
    </div>
  );
}

// ── Staff dashboards ─────────────────────────────────────────────

/** Full-height stage behind a staff dashboard. */
export function DashboardStage({ children }: { children: ReactNode }) {
  return (
    <div className="dash-stage">
      <FloatingParticles count={30} />
      <div className="dash-page" style={{ position: "relative", zIndex: 1 }}>{children}</div>
    </div>
  );
}

/** Reserved for things that need the user's attention. */
export const ATTENTION = "#facc15";

function ContestSteps({ status, dates }: { status: string; dates: string[] }) {
  const active = progressIndex(status);
  const last = PROGRESS_STEPS.length - 1;
  const line = (on: boolean) => ({ flex: 1, height: 2, background: on ? C.green : C.border });
  return (
    <ol aria-label="Contest progress" style={{ listStyle: "none", margin: 0, padding: 0, display: "flex" }}>
      {PROGRESS_STEPS.map(({ label, short }, i) => {
        const done = i < active;
        const current = i === active;
        return (
          <li key={label} aria-current={current ? "step" : undefined} style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", alignItems: "center", gap: 8 }}>
            <span style={{ display: "flex", alignItems: "center", width: "100%" }}>
              <span style={i > 0 ? line(i <= active) : { flex: 1 }} />
              <span
                className={current ? "dash-step--active" : undefined}
                style={{
                  width: 32, height: 32, borderRadius: "50%", flexShrink: 0, boxSizing: "border-box",
                  display: "flex", alignItems: "center", justifyContent: "center",
                  color: "#ffffff", fontFamily: MONO, fontSize: 14, fontWeight: 700,
                  border: `2px solid ${done || current ? C.green : C.border}`,
                  background: current ? "rgba(34,197,94,0.2)" : done ? "rgba(34,197,94,0.1)" : "transparent",
                  boxShadow: done ? "0 0 8px rgba(34,197,94,0.25)" : undefined,
                }}
              >
                {done ? "✓" : i + 1}
              </span>
              <span style={i < last ? line(i < active) : { flex: 1 }} />
            </span>
            <span className="dash-step__label">
              <b className="dash-step__long">{label}</b>
              <b className="dash-step__short">{short}</b>
              {dates[i] && <span className="dash-step__long">, {dates[i]}</span>}
            </span>
          </li>
        );
      })}
    </ol>
  );
}

export interface ContestFact {
  label: string;
  value: ReactNode;
  /** Highlight a value the user still has to deal with. */
  attention?: boolean;
  /** Already shown elsewhere on wide screens (step dates). */
  mobileOnly?: boolean;
}

interface ContestCardProps {
  name: string;
  status?: string | null;
  /** Phase and time in one phrase, shown beside the name. */
  summary?: string;
  dates?: string[];
  facts?: ContestFact[];
}

/** Everything about the contest in one block: name, phase, timeline, key facts. */
export function ContestCard({ name, status, summary, dates = [], facts = [] }: ContestCardProps) {
  const phase = status ? eventPhaseBanner(status.toUpperCase()) : null;
  return (
    <PixelCard glow glowColor="blue" gradient className="dash-panel">
      <div style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: "12px 20px", fontFamily: MONO }}>
        <h1 style={{ margin: 0, color: "#ffffff", fontFamily: MONO, fontSize: 26, fontWeight: 800, lineHeight: 1.2, overflowWrap: "anywhere" }}>{name}</h1>
        {phase && summary && (
          <span className="dash-chip" style={{ background: phase.bg, borderColor: phase.border }}>{summary}</span>
        )}
      </div>
      {status && <ContestSteps status={status} dates={dates} />}
      {facts.length > 0 && (
        <div className="dash-facts">
          <dl>
            {facts.map(f => (
              <div key={f.label} className={f.mobileOnly ? "dash-facts__mobile" : undefined}>
                <dt>{f.label}</dt>
                <dd style={f.attention ? { color: ATTENTION } : undefined}>{f.value}</dd>
              </div>
            ))}
          </dl>
        </div>
      )}
    </PixelCard>
  );
}

/** The work list: one row per function, with name, state and button in columns. */
export function TaskCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <PixelCard glow glowColor="blue" gradient className="dash-panel dash-panel--tasks">
      <h2 style={{ margin: 0, color: "#ffffff", fontFamily: MONO, fontSize: 18, fontWeight: 800, lineHeight: 1.3 }}>{title}</h2>
      <div>{children}</div>
    </PixelCard>
  );
}

interface TaskRowProps {
  name: string;
  state: ReactNode;
  attention?: boolean;
  /** Extra plain text after the state. */
  more?: string;
  action: string;
  onAction: () => void;
  /** The thing to do first on this page. */
  primary?: boolean;
  disabled?: boolean;
}

export function TaskRow({ name, state, attention = false, more, action, onAction, primary = false, disabled = false }: TaskRowProps) {
  return (
    <div className="dash-task">
      <b className="dash-task__name">{name}</b>
      <div className="dash-task__state">
        <span style={attention ? { color: ATTENTION, fontWeight: 700 } : undefined}>{state}</span>
        {more && <span>{more}</span>}
      </div>
      <PixelButton variant={primary ? "cyber" : "secondary"} size="sm" className="dash-action" onClick={onAction} disabled={disabled}>
        {action}
      </PixelButton>
    </div>
  );
}

export function TaskNote({ children }: { children: ReactNode }) {
  return <div className="dash-task dash-task--note">{children}</div>;
}
