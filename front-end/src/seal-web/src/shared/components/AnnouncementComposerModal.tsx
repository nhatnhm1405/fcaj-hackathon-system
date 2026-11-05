import { useState } from "react";
import { createPortal } from "react-dom";
import { C, PixelButton } from "@/shared/components/PixelComponents";
import { ApiError } from "@/shared/apiClient";

const mono = "'JetBrains Mono', monospace";

interface Props {
  open: boolean;
  /** Where it goes, e.g. "AI Track" or "SEAL Spring 2026". */
  scopeLabel: string;
  /** Small audience hint shown under the header, e.g. "5 teams · all participants". */
  audienceHint?: string;
  /** Optional event picker (coordinator: choose which event to announce to). */
  events?: { value: number; label: string }[];
  eventId?: number;
  onEventChange?: (value: number) => void;
  /** Optional audience picker (coordinator: Participants / Judges / Mentors). */
  audiences?: { value: string; label: string }[];
  audience?: string;
  onAudienceChange?: (value: string) => void;
  /** Sends the announcement; resolves with the recipient count. */
  onSend: (title: string, content: string, linkUrl?: string) => Promise<number>;
  /** Called after a successful send (e.g. to refresh history). */
  onSent?: (recipientCount: number) => void;
  onClose: () => void;
}

export function AnnouncementComposerModal({ open, scopeLabel, events, eventId, onEventChange, audiences, audience, onAudienceChange, onSend, onSent, onClose }: Props) {
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [link, setLink] = useState("");
  const [sending, setSending] = useState(false);
  const [msg, setMsg] = useState<{ type: "success" | "warning" | "error"; text: string } | null>(null);

  if (!open) return null;
  const accent = "#22c55e";

  const inputStyle: React.CSSProperties = {
    background: C.surface2,
    border: `1px solid ${C.border}`,
    color: C.text,
    fontFamily: mono,
    fontSize: 13,
    padding: "10px 12px",
    width: "100%",
    borderRadius: 0,
    outline: "none",
  };

  function reset() {
    setTitle("");
    setContent("");
    setLink("");
    setMsg(null);
  }

  function close() {
    if (sending) return;
    reset();
    onClose();
  }

  async function handleSend() {
    if (!title.trim() || !content.trim()) {
      setMsg({ type: "error", text: "Title and message are required." });
      return;
    }
    if (link.trim() && !/^https?:\/\//i.test(link.trim())) {
      setMsg({ type: "error", text: "Link must start with http:// or https://" });
      return;
    }
    setSending(true);
    setMsg(null);
    try {
      const count = await onSend(title.trim(), content.trim(), link.trim() || undefined);
      if (count === 0) {
        setMsg({ type: "warning", text: "Sent — but 0 recipients. No one matches this audience in the selected event yet." });
      } else {
        setMsg({ type: "success", text: `Sent to ${count} recipient(s).` });
      }
      setTitle("");
      setContent("");
      setLink("");
      onSent?.(count);
    } catch (err) {
      setMsg({ type: "error", text: err instanceof ApiError ? err.message : "Failed to send announcement." });
    } finally {
      setSending(false);
    }
  }

  return createPortal(
    <>
      <div
        onClick={close}
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

        <h2 style={{ fontFamily: mono, fontSize: 18, fontWeight: 800, color: C.text, marginBottom: 18, lineHeight: 1.3 }}>
          Announce to {scopeLabel}
        </h2>

        {/* Event picker (coordinator) */}
        {events && events.length > 0 && (
          <div style={{ marginBottom: 14 }}>
            <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 10, letterSpacing: "0.1em", marginBottom: 6 }}>EVENT</div>
            <select
              value={eventId}
              onChange={(e) => onEventChange?.(Number(e.target.value))}
              disabled={sending}
              style={{ ...inputStyle, cursor: sending ? "default" : "pointer" }}
            >
              {events.map(ev => (
                <option key={ev.value} value={ev.value}>{ev.label}</option>
              ))}
            </select>
          </div>
        )}

        {/* Audience picker (coordinator) */}
        {audiences && audiences.length > 0 && (
          <div style={{ marginBottom: 16 }}>
            <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 10, letterSpacing: "0.1em", marginBottom: 6 }}>SEND TO</div>
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              {audiences.map(a => {
                const selected = audience === a.value;
                return (
                  <button
                    key={a.value}
                    type="button"
                    onClick={() => !sending && onAudienceChange?.(a.value)}
                    style={{
                      fontFamily: mono, fontSize: 12, padding: "6px 14px", cursor: sending ? "default" : "pointer",
                      borderRadius: 0,
                      background: selected ? "rgba(34,197,94,0.12)" : C.surface2,
                      border: `1px solid ${selected ? C.green : C.border}`,
                      color: selected ? C.green : C.textMuted,
                      transition: "all 0.15s",
                    }}
                  >
                    {a.label}
                  </button>
                );
              })}
            </div>
          </div>
        )}

        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          <input
            style={inputStyle}
            placeholder="Title (e.g. Round 1 deadline extended)"
            value={title}
            maxLength={255}
            onChange={(e) => setTitle(e.target.value)}
            disabled={sending}
            autoFocus
          />
          <textarea
            style={{ ...inputStyle, minHeight: 130, resize: "vertical", lineHeight: 1.6 }}
            placeholder="Message to participants…"
            value={content}
            maxLength={5000}
            onChange={(e) => setContent(e.target.value)}
            disabled={sending}
          />
          <input
            style={inputStyle}
            placeholder="Attachment link (optional) — https://…"
            value={link}
            maxLength={1000}
            onChange={(e) => setLink(e.target.value)}
            disabled={sending}
          />

          {msg && (
            <div style={{
              fontFamily: mono, fontSize: 11, padding: "8px 12px",
              background: msg.type === "success" ? "rgba(34,197,94,0.08)" : msg.type === "warning" ? "rgba(234,179,8,0.08)" : "rgba(239,68,68,0.08)",
              border: `1px solid ${msg.type === "success" ? "rgba(34,197,94,0.35)" : msg.type === "warning" ? "rgba(234,179,8,0.4)" : "rgba(239,68,68,0.35)"}`,
              color: msg.type === "success" ? C.green : msg.type === "warning" ? "#eab308" : C.red,
            }}>
              {msg.text}
            </div>
          )}

          <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 4 }}>
            <PixelButton variant="secondary" onClick={close} disabled={sending}>
              {msg && msg.type !== "error" ? "DONE" : "CANCEL"}
            </PixelButton>
            <PixelButton variant="cyber" onClick={handleSend} disabled={sending}>
              {sending ? "SENDING…" : "SEND"}
            </PixelButton>
          </div>
        </div>
      </div>
    </>,
    document.body,
  );
}
