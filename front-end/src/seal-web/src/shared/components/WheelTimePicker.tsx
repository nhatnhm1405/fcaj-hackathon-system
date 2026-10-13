import { useEffect, useRef } from "react";
import { C } from "@/shared/components/PixelComponents";

// iOS-style drum picker for hours / minutes, PLUS a compact type-in row so
// users can enter values fast without scrolling. Seconds were dropped — nobody
// schedules a contest window to the second (backend still receives seconds=0).
// Emits the total as seconds so callers stay unit-agnostic.
const MONO = "'JetBrains Mono', monospace";
const ITEM_H = 40;
const VISIBLE = 3;               // odd → one centered row; 3 keeps the drum compact
const PAD = ((VISIBLE - 1) / 2) * ITEM_H;

function range(maxInclusive: number): number[] {
  return Array.from({ length: maxInclusive + 1 }, (_, i) => i);
}

function clamp(n: number, min: number, max: number): number {
  if (Number.isNaN(n)) return min;
  return Math.min(max, Math.max(min, n));
}

function WheelColumn({
  values, value, unit, disabled, onChange,
}: {
  values: number[];
  value: number;
  unit: string;
  disabled: boolean;
  onChange: (v: number) => void;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const settleRef = useRef<number>(0);
  const idx = Math.max(0, values.indexOf(value));

  // Sync scroll position to the selected value (external changes / typing / reset).
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const target = idx * ITEM_H;
    if (Math.abs(el.scrollTop - target) > 2) el.scrollTop = target;
  }, [idx]);

  function onScroll() {
    const el = ref.current;
    if (!el) return;
    window.clearTimeout(settleRef.current);
    settleRef.current = window.setTimeout(() => {
      const i = Math.min(values.length - 1, Math.max(0, Math.round(el.scrollTop / ITEM_H)));
      if (values[i] !== value) onChange(values[i]);
      const target = i * ITEM_H;
      if (Math.abs(el.scrollTop - target) > 1) el.scrollTo({ top: target, behavior: "smooth" });
    }, 100);
  }

  return (
    <div style={{ position: "relative", height: VISIBLE * ITEM_H, flex: 1, minWidth: 0 }}>
      <div
        ref={ref}
        onScroll={disabled ? undefined : onScroll}
        className="seal-wheel-col"
        style={{
          height: "100%",
          overflowY: disabled ? "hidden" : "scroll",
          scrollSnapType: "y mandatory",
          scrollbarWidth: "none",
          msOverflowStyle: "none",
          opacity: disabled ? 0.5 : 1,
        }}
      >
        <div style={{ height: PAD }} />
        {values.map((v) => {
          const selected = v === value;
          return (
            <div
              key={v}
              style={{
                height: ITEM_H,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                gap: 6,
                scrollSnapAlign: "center",
                fontFamily: MONO,
                fontSize: selected ? 22 : 17,
                fontWeight: selected ? 800 : 500,
                color: selected ? C.text : C.textDim,
                transition: "color 0.12s, font-size 0.12s",
              }}
            >
              <span style={{ minWidth: 30, textAlign: "right" }}>{v}</span>
              <span style={{ fontSize: 12, color: C.textMuted }}>{unit}</span>
            </div>
          );
        })}
        <div style={{ height: PAD }} />
      </div>
      {/* Center selection band */}
      <div
        style={{
          position: "absolute", left: 0, right: 0, top: PAD, height: ITEM_H,
          pointerEvents: "none",
          borderTop: `1px solid ${C.border}`,
          borderBottom: `1px solid ${C.border}`,
          background: "rgba(34,197,94,0.06)",
        }}
      />
    </div>
  );
}

function NumberField({
  label, value, max, disabled, onChange,
}: {
  label: string;
  value: number;
  max: number;
  disabled: boolean;
  onChange: (v: number) => void;
}) {
  return (
    <label style={{ display: "flex", alignItems: "center", gap: 5, flex: 1 }}>
      <span style={{ color: C.textMuted, fontFamily: MONO, fontSize: 11, width: 12 }}>{label}</span>
      <input
        type="number"
        min={0}
        max={max}
        value={value}
        disabled={disabled}
        onFocus={(e) => e.currentTarget.select()}
        onChange={(e) => onChange(clamp(parseInt(e.target.value, 10), 0, max))}
        style={{
          width: "100%", minWidth: 0,
          background: C.surface, border: `1px solid ${C.border}`, color: C.text,
          fontFamily: MONO, fontSize: 13, padding: "5px 6px", borderRadius: 0,
          outline: "none", textAlign: "center",
        }}
      />
    </label>
  );
}

export function WheelTimePicker({
  valueSeconds, onChange, maxHours = 24, disabled = false,
}: {
  valueSeconds: number;
  onChange: (seconds: number) => void;
  maxHours?: number;
  disabled?: boolean;
}) {
  const total = Math.max(0, Math.floor(valueSeconds));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const set = (nh: number, nm: number) =>
    onChange(clamp(nh, 0, maxHours) * 3600 + clamp(nm, 0, 59) * 60);

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        gap: 6,
        background: C.surface2,
        border: `1px solid ${C.border}`,
        padding: "6px 8px",
        maxWidth: 360,
      }}
    >
      {/* Hide the scrollbars on WebKit (inline styles can't target ::-webkit-scrollbar). */}
      <style>{`.seal-wheel-col::-webkit-scrollbar{display:none}`}</style>

      {/* Scroll wheels */}
      <div style={{ display: "flex", gap: 4 }}>
        <WheelColumn values={range(maxHours)} value={h} unit="hours" disabled={disabled} onChange={(v) => set(v, m)} />
        <WheelColumn values={range(59)} value={m} unit="min" disabled={disabled} onChange={(v) => set(h, v)} />
      </div>

      {/* Fast type-in row */}
      <div style={{ display: "flex", gap: 8, paddingTop: 6, borderTop: `1px solid ${C.border}` }}>
        <NumberField label="H" value={h} max={maxHours} disabled={disabled} onChange={(v) => set(v, m)} />
        <NumberField label="M" value={m} max={59} disabled={disabled} onChange={(v) => set(h, v)} />
      </div>
    </div>
  );
}
