import { createPortal } from "react-dom";
import type { TeamHistoryEntry } from "@/shared/apiClient";
import fptLogo from "@/imports/fpt-logo.png";

const MEDAL_COLOR: Record<number, string> = {
  1: "#C9A227",
  2: "#8A8F98",
  3: "#B06A3B",
};

const today = () =>
  new Date().toLocaleDateString("en-GB", { day: "2-digit", month: "long", year: "numeric" });

const mono = "'JetBrains Mono', monospace";
const display = "'Playfair Display', Georgia, serif";
const body = "'EB Garamond', Georgia, 'Times New Roman', serif";

interface Props {
  entry: TeamHistoryEntry;
  recipientName: string;
  onClose: () => void;
}

export function CertificateModal({ entry, recipientName, onClose }: Props) {
  const prize = entry.prize;
  const isAchievement = prize != null;
  const accent = isAchievement ? (MEDAL_COLOR[prize.rankPosition] ?? "#C9A227") : "#1f6feb";
  const roleLabel = entry.myRole === "LEADER" ? "Team Leader" : "Member";
  const track = entry.trackName ? ` in the ${entry.trackName} track` : "";
  const rounds = entry.rounds ?? [];
  const roundReached = rounds.length > 0 ? rounds[rounds.length - 1].roundName : null;
  const displayRecipientName = recipientName.trim() || "Participant";

  const statement = isAchievement
    ? `for achieving ${prize.name} (Rank #${prize.rankPosition}) with team "${entry.teamName}" at ${entry.eventName}.`
    : `for participating in ${entry.eventName} as ${roleLabel} of team "${entry.teamName}"${track}` +
      `${roundReached ? `, completing the ${roundReached} round` : ""}.`;

  return createPortal(
    <div
      className="cert-backdrop"
      onClick={onClose}
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(0,0,0,0.7)",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "flex-start",
        padding: 24,
        overflow: "auto",
        zIndex: 1000,
      }}
    >
      <style>{`
        @media print {
          #root { display: none !important; }
          .no-print-hide { display: none !important; }
          .cert-backdrop {
            position: static !important;
            background: #fff !important;
            padding: 0 !important;
            overflow: visible !important;
            display: block !important;
          }
          .seal-cert {
            width: 100% !important;
            box-shadow: none !important;
            margin: 0 !important;
          }
          @page { size: landscape; margin: 8mm; }
        }
      `}</style>

      <div className="no-print-hide" onClick={(event) => event.stopPropagation()} style={{ display: "flex", gap: 10, marginBottom: 14 }}>
        <button type="button" onClick={() => window.print()} style={buttonStyle(accent, true)}>Print / Save as PDF</button>
        <button type="button" onClick={onClose} style={buttonStyle("#6b7280", false)}>Close</button>
      </div>

      <div
        className="seal-cert"
        onClick={(event) => event.stopPropagation()}
        style={{
          width: "min(1000px, 96vw)",
          aspectRatio: "1.414 / 1",
          background: "#fbf8f0",
          color: "#1a1a1a",
          boxShadow: "0 20px 60px rgba(0,0,0,0.5)",
          position: "relative",
        }}
      >
        <div style={{ position: "absolute", inset: 18, border: `3px solid ${accent}` }} />
        <div style={{ position: "absolute", inset: 26, border: `1px solid ${accent}88` }} />

        <div
          style={{
            position: "relative",
            height: "100%",
            boxSizing: "border-box",
            padding: "5.5% 11% 7%",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            textAlign: "center",
          }}
        >
          <img src={fptLogo} alt="FPT University" style={{ height: 46, objectFit: "contain", marginBottom: 12 }} />

          <div style={{ fontFamily: body, letterSpacing: "0.42em", fontSize: 16, fontWeight: 700, color: accent, textTransform: "uppercase" }}>
            SEAL Hackathon{entry.season ? ` - ${entry.season} ${entry.year ?? ""}` : ""}
          </div>

          <div style={{ fontFamily: display, marginTop: 16, fontSize: 44, fontWeight: 800, letterSpacing: "0.02em" }}>
            {isAchievement ? "Certificate of Achievement" : "Certificate of Participation"}
          </div>

          <div style={{ width: 110, height: 3, background: accent, margin: "16px 0 22px" }} />

          <div style={{ fontFamily: body, fontSize: 19, fontStyle: "italic", color: "#555" }}>
            This certificate is proudly presented to
          </div>

          <div style={{ fontFamily: display, marginTop: 12, fontSize: 56, fontWeight: 700, color: accent, lineHeight: 1.1 }}>
            {displayRecipientName}
          </div>

          <div style={{ width: 60, height: 1, background: "#bbb", margin: "18px 0" }} />

          <div style={{ fontFamily: body, fontSize: 22, lineHeight: 1.7, maxWidth: "82%", color: "#222" }}>
            {statement}
          </div>

          <div style={{ marginTop: "auto", width: "100%", display: "flex", justifyContent: "space-between", alignItems: "flex-end", paddingTop: 24 }}>
            <div style={{ textAlign: "center", minWidth: 200 }}>
              <div style={{ fontFamily: body, fontSize: 18, fontWeight: 600 }}>{today()}</div>
              <div style={{ borderTop: "1px solid #1a1a1a", marginTop: 6, paddingTop: 6, fontFamily: body, fontSize: 12, letterSpacing: "0.16em", textTransform: "uppercase", color: "#555" }}>
                Date Issued
              </div>
            </div>

            <div style={{ textAlign: "center", minWidth: 200 }}>
              <div style={{ fontFamily: display, fontStyle: "italic", fontSize: 24 }}>SEAL Organizing Committee</div>
              <div style={{ borderTop: "1px solid #1a1a1a", marginTop: 6, paddingTop: 6, fontFamily: body, fontSize: 12, letterSpacing: "0.16em", textTransform: "uppercase", color: "#555" }}>
                Event Coordinator
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>,
    document.body,
  );
}

const buttonStyle = (color: string, filled: boolean): React.CSSProperties => ({
  fontFamily: mono,
  fontSize: 12,
  fontWeight: 700,
  letterSpacing: "0.06em",
  padding: "10px 16px",
  cursor: "pointer",
  borderRadius: 0,
  border: `1px solid ${color}`,
  background: filled ? color : "transparent",
  color: filled ? "#0d1117" : "#e5e7eb",
});