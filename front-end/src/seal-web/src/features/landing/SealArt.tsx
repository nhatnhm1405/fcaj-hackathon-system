export type SealFace = "normal" | "happy" | "held" | "dizzy" | "sleep";

export const SEAL_W = 104;
export const SEAL_H = 84;

const INK = "#0f1b2a";
const SPOT = "#9db4c9";

// The seal mascot, drawn facing right. `[data-seal-glint]` is moved by
// SealMascot so the eyes follow the cursor.
export function SealArt({ face }: { face: SealFace }) {
  return (
    <svg viewBox="0 0 104 84" width={SEAL_W} height={SEAL_H} aria-hidden="true" style={{ display: "block", overflow: "visible" }}>
      <defs>
        <linearGradient id="seal-fur" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#ffffff" />
          <stop offset="1" stopColor="#c3d5e6" />
        </linearGradient>
      </defs>

      {/* Tail flippers */}
      <g className="seal-tail">
        <path d="M18 64 C8 60 2 48 3 41 C10 43 17 50 21 60 Z" fill="#b4c8da" stroke={INK} strokeWidth="2" strokeLinejoin="round" />
        <path d="M18 66 C8 68 1 63 -1 58 C7 55 15 58 21 63 Z" fill="#c9d9e8" stroke={INK} strokeWidth="2" strokeLinejoin="round" />
      </g>

      {/* Body, belly, spots */}
      <path d="M14 68 C16 52 32 38 52 34 L84 40 C92 56 88 80 68 81 L30 81 C20 81 13 76 14 68 Z" fill="url(#seal-fur)" stroke={INK} strokeWidth="2.2" strokeLinejoin="round" />
      <path d="M34 80 C44 70 62 68 82 72 C78 78 74 80 68 80 Z" fill="#ffffff" opacity="0.75" />
      <ellipse cx="36" cy="54" rx="3.4" ry="2.3" fill={SPOT} />
      <ellipse cx="28" cy="62" rx="2.2" ry="1.6" fill={SPOT} />
      <ellipse cx="45" cy="60" rx="2" ry="1.4" fill={SPOT} />

      {/* Front flipper */}
      <path className="seal-flipper" d="M60 64 C54 70 46 78 44 82 C53 84 64 80 69 70 Z" fill="#c9d9e8" stroke={INK} strokeWidth="2" strokeLinejoin="round" />

      {/* Event lanyard */}
      <path d="M51 46 C58 59 76 63 90 53" fill="none" stroke="#16a34a" strokeWidth="5" strokeLinecap="round" />
      <path d="M51 46 C58 59 76 63 90 53" fill="none" stroke="#4ade80" strokeWidth="2" strokeLinecap="round" />
      <rect x="66" y="58" width="19" height="14" rx="2" fill="#0d1117" stroke="#22c55e" strokeWidth="1.6" />
      <path d="M72 62.5 L69 65 L72 67.5 M79 62.5 L82 65 L79 67.5 M76.6 62 L74.4 68" fill="none" stroke="#4ade80" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />

      {/* Head */}
      <ellipse cx="73" cy="31" rx="26" ry="24" fill="url(#seal-fur)" stroke={INK} strokeWidth="2.2" />
      <ellipse cx="63" cy="14" rx="3" ry="1.8" fill={SPOT} transform="rotate(-22 63 14)" />
      <ellipse cx="71" cy="11.5" rx="2" ry="1.3" fill={SPOT} />
      <ellipse cx="57" cy="38" rx="4.2" ry="2.8" fill="#fda4af" opacity="0.75" />
      <ellipse cx="94" cy="38" rx="3.6" ry="2.8" fill="#fda4af" opacity="0.75" />

      {/* Muzzle + whiskers */}
      <ellipse cx="72" cy="40" rx="6.5" ry="5" fill="#ffffff" />
      <ellipse cx="82" cy="40" rx="6.5" ry="5" fill="#ffffff" />
      <path d="M66 40 L54 43 M66 42 L56 48 M88 40 L99 43 M88 42 L98 48" stroke="#7f97ad" strokeWidth="1" strokeLinecap="round" />
      <ellipse cx="77" cy="35.5" rx="3.6" ry="2.6" fill={INK} />

      {/* Mouth */}
      {face === "happy" ? (
        <path d="M70 41 Q77 52 84 41 Z" fill="#be123c" stroke={INK} strokeWidth="1.6" strokeLinejoin="round" />
      ) : face === "held" ? (
        <ellipse cx="77" cy="44" rx="3.2" ry="3.8" fill="#be123c" stroke={INK} strokeWidth="1.5" />
      ) : face === "dizzy" ? (
        <path d="M70 44 Q72.5 41 75 44 T80 44 T85 44" fill="none" stroke={INK} strokeWidth="1.6" strokeLinecap="round" />
      ) : (
        <path d="M70.5 41.5 Q73.5 45.5 77 41.5 Q80.5 45.5 83.5 41.5" fill="none" stroke={INK} strokeWidth="1.6" strokeLinecap="round" />
      )}

      {/* Eyes */}
      {face === "happy" ? (
        <path d="M58 30 Q62.5 23 67 30 M84 30 Q88 23 92 30" fill="none" stroke={INK} strokeWidth="2.4" strokeLinecap="round" />
      ) : face === "sleep" ? (
        <path d="M58 28 Q62.5 33 67 28 M84 28 Q88 33 92 28" fill="none" stroke={INK} strokeWidth="2.2" strokeLinecap="round" />
      ) : face === "dizzy" ? (
        <path d="M59 25 L66 32 M66 25 L59 32 M84.5 25 L91.5 32 M91.5 25 L84.5 32" stroke={INK} strokeWidth="2.2" strokeLinecap="round" />
      ) : (
        <g className={face === "normal" ? "seal-eyes" : undefined}>
          <ellipse cx="62.5" cy="28.5" rx="5" ry={face === "held" ? 6.4 : 5.6} fill={INK} />
          <ellipse cx="88" cy="28.5" rx="4.6" ry={face === "held" ? 6.4 : 5.6} fill={INK} />
          <g data-seal-glint>
            <circle cx="63.5" cy="27" r="1.9" fill="#ffffff" />
            <circle cx="89" cy="27" r="1.8" fill="#ffffff" />
          </g>
        </g>
      )}
    </svg>
  );
}
