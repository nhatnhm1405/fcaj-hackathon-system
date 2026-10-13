import { ReactNode, useEffect, useRef, useState } from "react";
import { C, PixelButton } from "@/shared/components/PixelComponents";

// Reusable confirmation pop-up, generalized from the account Approve/Reject
// modal (CoordAccountsPage.ApprovalModal) so every destructive / status-changing
// action shares the same look. Nothing happens unless the user clicks Confirm;
// clicking Cancel, the backdrop, or pressing Escape calls onClose only.
//
// For the rare highest-impact irreversible actions (cancel event, announce
// prizes, …) pass `requireTypedText`: the confirm button stays disabled until
// the user types that exact text (GitHub-style), so the dialog cannot be
// clicked through on autopilot.

export type ConfirmVariant = "cyber" | "danger" | "secondary";

const MONO = "'JetBrains Mono', monospace";

interface ConfirmDialogProps {
  // When false, renders nothing. Defaults to true so a parent can just
  // conditionally mount the component (`{target && <ConfirmDialog .../>}`).
  open?: boolean;
  title: string;
  message: ReactNode;
  // Extra controls rendered below the message (e.g. an optional reason field).
  children?: ReactNode;
  // Highlighted "cannot be undone" style note.
  warning?: ReactNode;
  confirmLabel: string;
  cancelLabel?: string;
  variant?: ConfirmVariant;
  working?: boolean;
  error?: string | null;
  // Type-to-confirm gate: confirm stays disabled until this exact text is typed.
  requireTypedText?: string;
  typedTextLabel?: ReactNode;
  onConfirm: () => void;
  onClose: () => void;
}

function accentFor(variant: ConfirmVariant): string {
  if (variant === "danger") return "#ef4444";
  if (variant === "secondary") return "#3b82f6";
  return "#22c55e"; // cyber
}

export function ConfirmDialog({
  open = true,
  title,
  message,
  children,
  warning,
  confirmLabel,
  cancelLabel = "CANCEL",
  variant = "cyber",
  working = false,
  error = null,
  requireTypedText,
  typedTextLabel,
  onConfirm,
  onClose,
}: ConfirmDialogProps) {
  const [typed, setTyped] = useState("");
  const [inputFocused, setInputFocused] = useState(false);
  const inputRef = useRef<HTMLInputElement | null>(null);
  const cancelWrapRef = useRef<HTMLSpanElement | null>(null);

  const typedOk = requireTypedText == null || typed.trim() === requireTypedText;
  const confirmDisabled = working || !typedOk;

  // Reset the typed gate whenever the dialog (re)opens or targets new text.
  useEffect(() => {
    setTyped("");
  }, [open, requireTypedText]);

  // Escape dismisses (never confirms); ignored while a request is in flight.
  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape" && !working) onClose();
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open, working, onClose]);

  // Initial focus: the typed-confirmation input when present, otherwise the
  // Cancel button — a stray Enter must never trigger a destructive action.
  useEffect(() => {
    if (!open) return;
    if (requireTypedText != null) inputRef.current?.focus();
    else cancelWrapRef.current?.querySelector("button")?.focus();
  }, [open, requireTypedText]);

  if (!open) return null;
  const accent = accentFor(variant);

  return (
    <>
      {/* Backdrop — click to dismiss (no-op on the action) */}
      <div
        onClick={() => { if (!working) onClose(); }}
        style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.65)", zIndex: 400, backdropFilter: "blur(2px)" }}
      />
      <div
        role="dialog"
        aria-modal="true"
        style={{
          position: "fixed", top: "50%", left: "50%", transform: "translate(-50%, -50%)", zIndex: 401,
          width: "min(460px, calc(100vw - 32px))", background: C.surface, border: `1px solid ${accent}66`,
          boxShadow: `0 0 40px ${accent}22, 0 16px 48px rgba(0,0,0,0.4)`, padding: 32,
        }}
      >
        <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${accent}, transparent)` }} />
        <h2 style={{ fontFamily: MONO, fontSize: 20, fontWeight: 800, color: C.text, marginBottom: 12, lineHeight: 1.2 }}>
          {title}
        </h2>
        <div style={{ color: C.textMuted, fontFamily: MONO, fontSize: 12, lineHeight: 1.8, marginBottom: warning || children || requireTypedText != null ? 16 : 24 }}>
          {message}
        </div>

        {warning && (
          <div style={{ background: "rgba(234,179,8,0.08)", border: "1px solid rgba(234,179,8,0.4)", color: "#eab308", fontFamily: MONO, fontSize: 11, lineHeight: 1.7, padding: "10px 12px", marginBottom: 16 }}>
            ⚠ {warning}
          </div>
        )}

        {children && <div style={{ marginBottom: 16 }}>{children}</div>}

        {requireTypedText != null && (
          <div style={{ marginBottom: 16 }}>
            <label style={{ display: "block", fontFamily: MONO, fontSize: 11, color: C.textMuted, marginBottom: 6 }}>
              {typedTextLabel ?? (
                <>Type <b style={{ color: accent }}>{requireTypedText}</b> to confirm</>
              )}
            </label>
            <input
              ref={inputRef}
              type="text"
              value={typed}
              disabled={working}
              placeholder={requireTypedText}
              aria-label="Confirmation text"
              onChange={(e) => setTyped(e.target.value)}
              onFocus={() => setInputFocused(true)}
              onBlur={() => setInputFocused(false)}
              onKeyDown={(e) => { if (e.key === "Enter" && !confirmDisabled) onConfirm(); }}
              style={{
                width: "100%", background: C.surface2, outline: "none",
                border: inputFocused ? `1px solid ${accent}` : `1px solid ${C.border}`,
                borderRadius: 0, padding: "10px 12px", color: C.text,
                fontFamily: MONO, fontSize: 13, caretColor: accent,
                boxShadow: inputFocused ? `0 0 12px ${accent}22` : "none",
                transition: "all 0.15s ease",
              }}
            />
          </div>
        )}

        {error && (
          <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: MONO, fontSize: 11, padding: "8px 12px", marginBottom: 16 }}>
            ERROR: {error}
          </div>
        )}

        <div style={{ display: "flex", gap: 10 }}>
          <PixelButton variant={variant} onClick={onConfirm} disabled={confirmDisabled}>
            {working ? "WORKING…" : confirmLabel}
          </PixelButton>
          <span ref={cancelWrapRef} style={{ display: "inline-flex" }}>
            <PixelButton variant="secondary" onClick={onClose} disabled={working}>{cancelLabel}</PixelButton>
          </span>
        </div>
      </div>
    </>
  );
}
