import { createPortal } from "react-dom";
import { C, PixelButton } from "@/shared/components/PixelComponents";
import type { UINotification } from "@/app/providers/NotificationProvider";

const mono = "'JetBrains Mono', monospace";

function kindAccent(type: UINotification["type"]): string {
  if (type === "success") return C.green;
  if (type === "warning") return "#eab308";
  return "#06b6d4";
}

function fmtFull(iso: string): string {
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  return `${String(d.getDate()).padStart(2, "0")}/${String(d.getMonth() + 1).padStart(2, "0")}/${d.getFullYear()} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

function roleLabel(role?: string | null): string {
  if (!role) return "";
  const r = role.toUpperCase();
  if (r === "MENTOR") return "Mentor";
  if (r === "COORDINATOR") return "Coordinator";
  return role;
}

/** Email-style detail popup shown when a participant opens a notification. */
export function NotificationDetailModal({
  notification,
  onClose,
}: {
  notification: UINotification | null;
  onClose: () => void;
}) {
  if (!notification) return null;
  const accent = kindAccent(notification.type);

  // "From" line — announcements carry a human sender; system notifications don't.
  const fromText = notification.from
    ? `${notification.from}${notification.sender_role ? ` · ${roleLabel(notification.sender_role)}` : ""}${notification.scope_label ? ` · ${notification.scope_label}` : ""}`
    : "SEAL Hackathon · System";

  // Portal to <body> so the fixed overlay escapes the navbar's backdrop-filter
  // containing block (otherwise the modal anchors to the 60px navbar and clips).
  return createPortal(
    <>
      <div
        onClick={onClose}
        style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.65)", zIndex: 1000, backdropFilter: "blur(2px)" }}
      />
      <div
        role="dialog"
        aria-modal="true"
        style={{
          position: "fixed", top: "50%", left: "50%", transform: "translate(-50%, -50%)", zIndex: 1001,
          width: "min(560px, calc(100vw - 32px))", maxHeight: "calc(100vh - 64px)", overflow: "auto",
          background: C.surface, border: `1px solid ${accent}66`,
          boxShadow: `0 0 40px ${accent}22, 0 16px 48px rgba(0,0,0,0.4)`, padding: 28,
        }}
      >
        <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${accent}, transparent)` }} />

        {/* Subject */}
        <h2 style={{ fontFamily: mono, fontSize: 18, fontWeight: 800, color: C.text, marginBottom: 14, lineHeight: 1.3, paddingRight: 24 }}>
          {notification.title}
        </h2>

        {/* From / date — email header block */}
        <div style={{ display: "flex", flexDirection: "column", gap: 4, paddingBottom: 14, marginBottom: 16, borderBottom: `1px solid ${C.border}` }}>
          <div style={{ display: "flex", gap: 8 }}>
            <span style={{ color: C.textDim, fontFamily: mono, fontSize: 11, minWidth: 46 }}>From</span>
            <span style={{ color: C.text, fontFamily: mono, fontSize: 12, fontWeight: 600 }}>{fromText}</span>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <span style={{ color: C.textDim, fontFamily: mono, fontSize: 11, minWidth: 46 }}>Date</span>
            <span style={{ color: C.textMuted, fontFamily: mono, fontSize: 12 }}>{fmtFull(notification.created_at)}</span>
          </div>
        </div>

        {/* Body */}
        <div style={{ color: C.text, fontFamily: mono, fontSize: 13, lineHeight: 1.8, whiteSpace: "pre-wrap", wordBreak: "break-word", marginBottom: notification.link_url ? 14 : 24 }}>
          {notification.message}
        </div>

        {/* Attachment link */}
        {notification.link_url && (
          <a
            href={notification.link_url}
            target="_blank"
            rel="noopener noreferrer"
            style={{
              display: "inline-flex", alignItems: "center", gap: 8, marginBottom: 24,
              padding: "8px 12px", background: C.surface2, border: `1px solid ${accent}55`,
              color: accent, fontFamily: mono, fontSize: 12, textDecoration: "none", wordBreak: "break-all",
            }}
          >
            🔗 {notification.link_url}
          </a>
        )}

        <div style={{ display: "flex", justifyContent: "flex-end" }}>
          <PixelButton variant="secondary" onClick={onClose}>CLOSE</PixelButton>
        </div>
      </div>
    </>,
    document.body,
  );
}
