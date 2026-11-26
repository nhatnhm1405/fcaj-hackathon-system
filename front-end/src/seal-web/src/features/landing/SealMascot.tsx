import { useCallback, useEffect, useRef, useState, type CSSProperties, type MouseEvent as ReactMouseEvent, type PointerEvent as ReactPointerEvent } from "react";
import { C } from "@/shared/components/PixelComponents";
import { SealArt, SEAL_H as H, SEAL_W as W, type SealFace } from "./SealArt";
import { prefersReducedMotion } from "./motion";

// Interactive seal that roams the bottom of the landing page. Click (or Enter)
// pets it, five quick pets make it spin, and it can be picked up, carried and
// thrown. Motion is imperative (one rAF loop, no re-render per frame); React
// state only holds what it looks like: face, speech bubble and particles.
type Mode = "walk" | "idle" | "sleep" | "held" | "air" | "dizzy" | "spin";

const FLOOR_GAP = 2;
const EDGE = 8;
const RIGHT_GUTTER = 76;       // keeps the walk clear of the scroll arrows
const NAV_HEIGHT = 64;
const WALK_SPEED = 62;         // px/s
const GRAVITY = 2300;          // px/s²
const MAX_THROW = 2200;        // px/s
const MAX_STEP = 0.02;         // s, physics sub-step
const DRAG_THRESHOLD = 5;      // px before a press becomes a pick-up
const HOLD_MS = 220;           // …or this long without moving
const BOUNCE_IMPACT = 520;     // px/s at landing
const DIZZY_IMPACT = 1250;     // px/s at landing
const COMBO_WINDOW_MS = 900;
const COMBO_SPIN = 5;
const SPIN_MS = 1100;
const SLEEP_AFTER_MS = 25_000;

const PET_LINES = [
  "Arf! Ready to hack?",
  "git commit -m \"arf\"",
  "Works on my iceberg.",
  "404: fish not found",
  "Will debug for fish.",
  "Ship it! Ship it!",
  "Save early, save often.",
];
const HELD_LINES = ["Wheee!", "Hey, put me down!", "I can see my code from here!"];
const DIZZY_LINES = ["Oof... segfault.", "Everything is spinning..."];
const SPIN_LINE = "SEAL OF APPROVAL!";
const WAKE_LINE = "Huh? Is it demo time?";
const HELLO_LINE = "Click me, or pick me up!";

const PARTICLE_COLORS = ["#22c55e", "#4ade80", "#3b82f6", "#22d3ee", "#f472b6"];

interface Particle { id: number; dx: number; dy: number; color: string; glyph: string; }

let nextParticleId = 0;

function pick<T>(items: T[]): T {
  return items[Math.floor(Math.random() * items.length)];
}

function clamp(v: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, v));
}

// Wrap to [-180, 180) so a tumbling seal rights itself the short way round.
function wrapAngle(deg: number): number {
  return (((deg + 180) % 360) + 360) % 360 - 180;
}

function floorY(): number {
  return window.innerHeight - H - FLOOR_GAP;
}

export function SealMascot() {
  const rootRef = useRef<HTMLDivElement>(null);
  const swingRef = useRef<HTMLDivElement>(null);
  const poseRef = useRef<HTMLDivElement>(null);
  const bubbleRef = useRef<HTMLDivElement>(null);

  const [face, setFace] = useState<SealFace>("normal");
  const [held, setHeld] = useState(false);
  const [line, setLine] = useState<string | null>(null);
  const [particles, setParticles] = useState<Particle[]>([]);

  const sim = useRef({
    mode: "air" as Mode,
    until: 0,                      // when the current timed mode ends
    x: 48, y: -H, vx: 0, vy: 0,
    angle: 0, spin: 0,             // degrees, degrees/s
    dir: 1 as 1 | -1,
    hardHit: false,                // this fall includes a dizzying impact
    greeted: false,
    happyUntil: 0, lineUntil: 0,
    lastTouch: 0, lastPet: 0, combo: 0,
    // Press / pick-up. The grab offset inside the box doubles as the swing pivot.
    pointerId: -1, pressAt: 0, pressX: 0, pressY: 0,
    grabX: W / 2, grabY: H / 2,
    pointerX: 0, pointerY: 0,
    suppressClick: false,
  });

  const say = useCallback((text: string, ms = 2400) => {
    sim.current.lineUntil = performance.now() + ms;
    setLine(text);
  }, []);

  const burst = useCallback((count: number) => {
    if (prefersReducedMotion()) return;
    setParticles(prev => [...prev, ...Array.from({ length: count }, () => ({
      id: nextParticleId++,
      dx: (Math.random() - 0.5) * 130,
      dy: -40 - Math.random() * 80,
      color: pick(PARTICLE_COLORS),
      glyph: pick(["♥", "✦", "■", "+"]),
    }))]);
  }, []);

  useEffect(() => {
    const s = sim.current;
    const reduced = prefersReducedMotion();
    const maxX = () => Math.max(EDGE, window.innerWidth - W - EDGE);
    const walkMaxX = () => Math.max(EDGE, window.innerWidth - W - RIGHT_GUTTER);

    function enter(mode: Mode, ms = 0) {
      s.mode = mode;
      s.until = performance.now() + ms;
    }

    function land() {
      s.vx = 0; s.vy = 0; s.spin = 0;
      s.angle = wrapAngle(s.angle);
      if (s.hardHit) {
        s.hardHit = false;
        enter("dizzy", 2000);
        say(pick(DIZZY_LINES));
      } else {
        enter("idle", 700);
        if (!s.greeted) say(HELLO_LINE, 4500);
      }
      s.greeted = true;
    }

    function pickUp() {
      s.mode = "held";
      s.vx = 0; s.vy = 0; s.spin = 0; s.angle = wrapAngle(s.angle);
      s.hardHit = false;
      s.suppressClick = true;
      say(pick(HELD_LINES));
    }

    function stepHeld(dt: number) {
      const follow = 1 - Math.exp(-dt * 30);
      const vx = ((s.pointerX - s.grabX - s.x) * follow) / dt;
      const vy = ((s.pointerY - s.grabY - s.y) * follow) / dt;
      const accel = vx - s.vx;
      s.vx = vx; s.vy = vy;
      s.x += vx * dt; s.y += vy * dt;
      if (reduced) { s.angle = 0; return; }
      // Hang so the body's centre falls below the grabbed point, then let
      // sideways acceleration swing it like a pendulum.
      const cx = W / 2 - s.grabX, cy = H / 2 - s.grabY;
      const reach = Math.min(1, Math.hypot(cx, cy) / 36);
      const rest = wrapAngle(90 - Math.atan2(cy, cx) * 180 / Math.PI) * reach * 0.7;
      s.spin += ((rest - s.angle) * 90 - s.spin * 7) * dt + accel * 0.12;
      s.spin = clamp(s.spin, -900, 900);
      s.angle = clamp(s.angle + s.spin * dt, -170, 170);
    }

    function stepAir(dt: number) {
      s.vy += GRAVITY * dt;
      s.x += s.vx * dt; s.y += s.vy * dt;
      s.angle += s.spin * dt;
      if (s.x < EDGE) { s.x = EDGE; s.vx = Math.abs(s.vx) * 0.6; s.spin *= -0.6; }
      if (s.x > maxX()) { s.x = maxX(); s.vx = -Math.abs(s.vx) * 0.6; s.spin *= -0.6; }
      if (s.y < floorY()) return;
      s.y = floorY();
      if (s.vy > DIZZY_IMPACT && s.greeted) s.hardHit = true;
      if (s.vy > BOUNCE_IMPACT) {
        s.vy *= -0.36; s.vx *= 0.7; s.spin *= 0.6;
      } else {
        land();
      }
    }

    function step(now: number, dt: number) {
      switch (s.mode) {
        case "held": stepHeld(dt); return;
        case "air": stepAir(dt); return;
        case "walk":
          s.x += s.dir * WALK_SPEED * dt;
          if (s.x <= EDGE) s.dir = 1;
          if (s.x >= walkMaxX()) s.dir = -1;
          if (now > s.until) enter("idle", 1200 + Math.random() * 2200);
          break;
        case "idle":
          if (now <= s.until || reduced) break;
          if (now - s.lastTouch > SLEEP_AFTER_MS && Math.random() < 0.4) {
            enter("sleep", 9000);
          } else {
            s.dir = Math.random() < 0.5 ? 1 : -1;
            enter("walk", 3500 + Math.random() * 5000);
          }
          break;
        case "sleep":
        case "dizzy":
          if (now > s.until) enter("idle", 600);
          break;
        case "spin": {
          const left = Math.max(0, (s.until - now) / SPIN_MS);
          s.angle = (1 - left) * 720 * s.dir;
          if (left === 0) { s.angle = 0; enter("idle", 900); }
          break;
        }
      }
      s.y = floorY();
      s.x = clamp(s.x, EDGE, maxX());
      if (s.mode !== "spin") s.angle *= Math.exp(-dt * 10);
    }

    function paintPose(now: number, pose: HTMLDivElement) {
      let hop = 0, tilt = 0, sx = 1, sy = 1;
      if (!reduced) {
        if (s.mode === "walk") {
          const p = now / 150;
          hop = Math.abs(Math.sin(p)) * 8;
          tilt = Math.cos(p * 2) * 3;
          sy = 1 + (hop / 8 - 0.5) * 0.08; sx = 2 - sy;
        } else if (s.mode === "idle" || s.mode === "sleep") {
          sy = 1 + Math.sin(now / (s.mode === "sleep" ? 900 : 480)) * 0.025; sx = 2 - sy;
        } else if (s.mode === "spin") {
          hop = Math.sin(clamp((s.until - now) / SPIN_MS, 0, 1) * Math.PI) * 46;
        } else if (s.mode === "dizzy") {
          tilt = Math.sin(now / 160) * 6;
        }
      }
      pose.style.transform = `scaleX(${s.dir}) rotate(${tilt}deg) scale(${sx}, ${sy})`;

      // Glints drift toward the cursor (mirrored while it faces left).
      const glint = pose.querySelector("[data-seal-glint]");
      if (glint) {
        const dx = s.pointerX - (s.x + W / 2 + s.dir * W * 0.2), dy = s.pointerY - (s.y + H * 0.35);
        const d = Math.hypot(dx, dy) || 1;
        const reach = Math.min(1, d / 160) * 2;
        glint.setAttribute("transform", `translate(${(dx / d) * reach * s.dir} ${(dy / d) * reach})`);
      }
      return hop;
    }

    function paint(now: number) {
      const root = rootRef.current, swing = swingRef.current, pose = poseRef.current;
      if (!root || !swing || !pose) return;
      const hop = paintPose(now, pose);
      root.style.transform = `translate3d(${s.x}px, ${s.y}px, 0)`;
      swing.style.transformOrigin = s.mode === "held" ? `${s.grabX}px ${s.grabY}px` : "50% 50%";
      swing.style.transform = `translateY(${-hop}px) rotate(${s.angle}deg)`;

      // Keep the bubble on screen: clamp sideways, flip below when held up high.
      const bubble = bubbleRef.current;
      if (bubble) {
        const bw = bubble.offsetWidth, bh = bubble.offsetHeight;
        const left = clamp(s.x + W / 2 - bw / 2, EDGE, Math.max(EDGE, window.innerWidth - bw - EDGE)) - s.x;
        const top = s.y - bh - 8 < NAV_HEIGHT ? H + 24 : -bh - 8;
        bubble.style.transform = `translate(${left}px, ${top}px)`;
      }
    }

    function currentFace(now: number): SealFace {
      if (s.mode === "held" || s.mode === "dizzy" || s.mode === "sleep") return s.mode;
      return s.mode === "spin" || now < s.happyUntil ? "happy" : "normal";
    }

    let raf = 0;
    let last = performance.now();
    function frame(now: number) {
      const elapsed = clamp((now - last) / 1000, 0.001, 0.1);
      last = now;
      if (s.pointerId !== -1 && s.mode !== "held" && now - s.pressAt > HOLD_MS) pickUp();
      // Sub-step long frames so slow devices get the same speeds, not slow motion.
      const steps = Math.ceil(elapsed / MAX_STEP);
      for (let i = 0; i < steps; i++) step(now, elapsed / steps);
      paint(now);
      setFace(currentFace(now));
      setHeld(s.mode === "held");
      if (s.lineUntil && now > s.lineUntil) { s.lineUntil = 0; setLine(null); }
      raf = requestAnimationFrame(frame);
    }

    function onMove(e: PointerEvent) {
      if (s.pointerId !== -1 && e.pointerId !== s.pointerId) return;
      s.pointerX = e.clientX; s.pointerY = e.clientY;
      if (s.pointerId !== -1 && s.mode !== "held" && Math.hypot(e.clientX - s.pressX, e.clientY - s.pressY) > DRAG_THRESHOLD) pickUp();
    }

    function onRelease(e: PointerEvent) {
      if (e.pointerId !== s.pointerId) return;
      s.pointerId = -1;
      s.lastTouch = performance.now();
      if (s.mode !== "held") return;
      if (reduced) { s.angle = 0; enter("idle"); return; }
      // Held, it turns about the grab point; loose, about its middle. Shift the
      // box by the difference so letting go does not make it jump.
      const a = s.angle * Math.PI / 180;
      const cx = W / 2 - s.grabX, cy = H / 2 - s.grabY;
      s.x += cx * Math.cos(a) - cy * Math.sin(a) - cx;
      s.y += cx * Math.sin(a) + cy * Math.cos(a) - cy;
      s.vx = clamp(s.vx, -MAX_THROW, MAX_THROW);
      s.vy = clamp(s.vy, -MAX_THROW, MAX_THROW);
      if (Math.abs(s.vx) > 60) s.dir = s.vx > 0 ? 1 : -1;
      s.spin = clamp(s.vx * 0.35, -540, 540);
      s.mode = "air";
    }

    s.lastTouch = performance.now();
    if (reduced) { s.y = floorY(); enter("idle"); }
    window.addEventListener("pointermove", onMove, { passive: true });
    window.addEventListener("pointerup", onRelease);
    window.addEventListener("pointercancel", onRelease);
    raf = requestAnimationFrame(frame);
    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerup", onRelease);
      window.removeEventListener("pointercancel", onRelease);
    };
  }, [say]);

  function onPointerDown(e: ReactPointerEvent) {
    const s = sim.current;
    if (e.button !== 0 || s.pointerId !== -1) return;
    s.pointerId = e.pointerId;
    s.pressAt = performance.now();
    s.pressX = s.pointerX = e.clientX;
    s.pressY = s.pointerY = e.clientY;
    s.grabX = clamp(e.clientX - s.x, 0, W);
    s.grabY = clamp(e.clientY - s.y, 0, H);
    s.suppressClick = false;
  }

  function onClick(e: ReactMouseEvent) {
    const s = sim.current;
    // detail === 0 is a keyboard click, which never follows a drag.
    if (s.suppressClick && e.detail !== 0) { s.suppressClick = false; return; }
    if (s.mode === "held" || s.mode === "spin") return;
    const reduced = prefersReducedMotion();
    const now = performance.now();
    const wasAsleep = s.mode === "sleep";
    s.combo = now - s.lastPet < COMBO_WINDOW_MS ? s.combo + 1 : 1;
    s.lastPet = s.lastTouch = now;
    s.happyUntil = now + 1400;
    if (reduced) s.mode = "idle";

    if (s.combo >= COMBO_SPIN) {
      s.combo = 0;
      burst(18);
      say(SPIN_LINE);
      if (!reduced) { s.vx = 0; s.vy = 0; s.mode = "spin"; s.until = now + SPIN_MS; }
      return;
    }
    burst(7);
    say(wasAsleep ? WAKE_LINE : pick(PET_LINES));
    if (!reduced) { s.vx = 0; s.vy = -500; s.spin = 0; s.mode = "air"; }
  }

  return (
    <div
      ref={rootRef}
      style={{ position: "fixed", left: 0, top: 0, width: W, height: H, zIndex: held ? 120 : 85, pointerEvents: "none", willChange: "transform", transform: "translate3d(0, -200px, 0)" }}
    >
      <div ref={swingRef}>
        <div ref={poseRef} style={{ transformOrigin: "50% 100%" }}>
          <button
            type="button"
            className="seal-mascot"
            aria-label="Seal mascot. Click to pet, drag to pick up."
            onPointerDown={onPointerDown}
            onClick={onClick}
            onContextMenu={(e) => e.preventDefault()}
            style={{
              display: "block", width: W, height: H, padding: 0, border: "none", background: "none",
              pointerEvents: "auto", touchAction: "none", userSelect: "none",
              WebkitTapHighlightColor: "transparent",
              cursor: held ? "grabbing" : "grab",
            }}
          >
            <SealArt face={face} />
          </button>
        </div>
      </div>

      {face === "sleep" && (
        <div aria-hidden="true" style={{ position: "absolute", left: W * 0.4, top: -20, color: C.cyanBright, fontFamily: "'JetBrains Mono', monospace", fontWeight: 700 }}>
          <span className="seal-zzz" style={{ fontSize: 11 }}>z</span>
          <span className="seal-zzz" style={{ fontSize: 14, animationDelay: "0.5s" }}>z</span>
          <span className="seal-zzz" style={{ fontSize: 17, animationDelay: "1s" }}>z</span>
        </div>
      )}

      {particles.map(p => (
        <span
          key={p.id}
          aria-hidden="true"
          className="seal-particle"
          onAnimationEnd={() => setParticles(prev => prev.filter(q => q.id !== p.id))}
          style={{
            position: "absolute", left: "50%", top: "30%", color: p.color, fontSize: 14, lineHeight: 1,
            textShadow: `0 0 8px ${p.color}`,
            "--dx": `${p.dx}px`, "--dy": `${p.dy}px`,
          } as CSSProperties}
        >
          {p.glyph}
        </span>
      ))}

      <div role="status" aria-live="polite">
        {line && (
          <div
            ref={bubbleRef}
            style={{
              position: "absolute", left: 0, top: 0, width: "max-content", maxWidth: 230,
              background: "#0d1117", border: "1px solid rgba(34,197,94,0.5)",
              boxShadow: "0 0 18px rgba(34,197,94,0.25)",
              color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, lineHeight: 1.5,
              padding: "7px 12px",
            }}
          >
            {line}
          </div>
        )}
      </div>
    </div>
  );
}
