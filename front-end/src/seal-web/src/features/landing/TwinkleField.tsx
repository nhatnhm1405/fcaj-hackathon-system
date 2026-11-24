import { useState, type CSSProperties } from "react";

// Blinking background dust for the landing sections: pixels and four-point
// sparkles that fade in and out, code glyphs that flicker, and grid cells that
// light up. Sits behind the section content; the parent must be positioned.
type TwinkleKind = "pixel" | "sparkle" | "glyph" | "cell";

const COLORS = ["#22c55e", "#4ade80", "#3b82f6", "#22d3ee", "#a78bfa", "#f0fdf4", "#f0fdf4"];
const GLYPHS = ["0", "1", "{ }", "</>", "//", "&&", "=>", "++"];
const CELL = 28; // matches the .cyber-grid-bg cell size

function pick<T>(items: T[]): T {
  return items[Math.floor(Math.random() * items.length)];
}

function rand(min: number, max: number): number {
  return min + Math.random() * (max - min);
}

function makeStar(id: number, kind: TwinkleKind) {
  return {
    id, kind,
    x: rand(1, 98), y: rand(2, 97),
    size: kind === "sparkle" ? rand(12, 24) : Math.round(rand(2, 5)),
    color: pick(COLORS),
    glyph: pick(GLYPHS),
    peak: kind === "cell" ? rand(0.12, 0.24) : kind === "glyph" ? rand(0.35, 0.6) : rand(0.7, 1),
    duration: rand(1.8, 4.2),
    // Negative delay, so the field is already mid-twinkle on first paint.
    delay: -rand(0, 5.5),
  };
}

function starStyle(s: ReturnType<typeof makeStar>): CSSProperties {
  const base = {
    position: "absolute", left: `${s.x}%`, top: `${s.y}%`,
    animationDuration: `${s.duration}s`, animationDelay: `${s.delay}s`,
    "--twinkle-peak": s.peak,
  } as CSSProperties;
  switch (s.kind) {
    case "sparkle":
      return {
        ...base, width: s.size, height: s.size, background: s.color,
        clipPath: "polygon(50% 0%, 57% 43%, 100% 50%, 57% 57%, 50% 100%, 43% 57%, 0% 50%, 43% 43%)",
      };
    case "glyph":
      return { ...base, color: s.color, fontFamily: "'JetBrains Mono', monospace", fontSize: 11 };
    case "cell":
      return {
        ...base, width: CELL - 1, height: CELL - 1, background: s.color,
        // Snap to the grid lines (percent offsets land between cells).
        left: `round(down, ${s.x}%, ${CELL}px)`, top: `round(down, ${s.y}%, ${CELL}px)`,
        margin: "1px 0 0 1px",
      };
    default:
      return { ...base, width: s.size, height: s.size, background: s.color, boxShadow: `0 0 ${s.size * 3}px ${s.color}` };
  }
}

export function TwinkleField({ count = 36 }: { count?: number }) {
  const [stars] = useState(() => Array.from({ length: count }, (_, i) => {
    const slot = i % 9;
    return makeStar(i, slot < 4 ? "pixel" : slot < 7 ? "sparkle" : slot === 7 ? "glyph" : "cell");
  }));

  return (
    <div aria-hidden="true" style={{ position: "absolute", inset: 0, overflow: "hidden", pointerEvents: "none", zIndex: 0 }}>
      {stars.map(s => (
        <span key={s.id} className={s.kind === "glyph" ? "landing-blink" : "landing-twinkle"} style={starStyle(s)}>
          {s.kind === "glyph" ? s.glyph : null}
        </span>
      ))}
    </div>
  );
}
