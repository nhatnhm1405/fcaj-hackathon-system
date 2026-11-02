import { Clock } from "lucide-react";
import { C, PixelBadge } from "@/shared/components/PixelComponents";
import type { TimerStatus } from "@/shared/apiClient";

// Big, monospace countdown read-out + status chip. Pure presentation — the live
// number comes from useRoundTimer. Color shifts green → orange (≤5m) → red (≤1m)
// as time runs out; flat/dim when paused, stopped, expired or not started.
const MONO = "'JetBrains Mono', monospace";

type BadgeColor = "green" | "yellow" | "red" | "gray";

const STATUS_BADGE: Record<TimerStatus, { color: BadgeColor; label: string }> = {
  IDLE:    { color: "gray",   label: "NOT STARTED" },
  RUNNING: { color: "green",  label: "RUNNING" },
  PAUSED:  { color: "yellow", label: "PAUSED" },
  STOPPED: { color: "red",    label: "STOPPED" },
  EXPIRED: { color: "red",    label: "TIME UP" },
};

function format(total: number): string {
  const t = Math.max(0, Math.floor(total));
  const h = Math.floor(t / 3600);
  const m = Math.floor((t % 3600) / 60);
  const s = t % 60;
  const pad = (n: number) => String(n).padStart(2, "0");
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
}

export function CountdownDisplay({
  remainingSeconds,
  status,
  size = "md",
  label,
  icon = false,
}: {
  remainingSeconds: number;
  status: TimerStatus;
  size?: "sm" | "md" | "lg";
  label?: string;
  icon?: boolean;
}) {
  const running = status === "RUNNING" && remainingSeconds > 0;
  const critical = running && remainingSeconds <= 60;

  let color = C.textDim;
  if (running) color = critical ? C.red : remainingSeconds <= 300 ? C.orange : C.green;
  else if (status === "PAUSED") color = C.yellow;
  else if (status === "EXPIRED" || status === "STOPPED") color = C.red;

  const fontSize = size === "lg" ? 46 : size === "sm" ? 22 : 34;
  const iconSize = Math.round(fontSize * 0.66);
  const badge = STATUS_BADGE[status];

  return (
    <div style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
        {icon && (
          <Clock size={iconSize} color={color} strokeWidth={2.5} style={{ flexShrink: 0 }} />
        )}
        <div
          style={{
            fontFamily: MONO,
            fontSize,
            fontWeight: 800,
            lineHeight: 1,
            color,
            letterSpacing: "0.04em",
            fontVariantNumeric: "tabular-nums",
            textShadow: critical ? `0 0 14px ${C.red}66` : undefined,
          }}
        >
          {status === "IDLE" ? "--:--" : format(remainingSeconds)}
        </div>
      </div>
      <PixelBadge color={badge.color}>{label ?? badge.label}</PixelBadge>
    </div>
  );
}
