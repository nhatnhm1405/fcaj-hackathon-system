import { ReactNode, useEffect, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { C, PixelButton } from "@/shared/components/PixelComponents";

// Overflow / actions dropdown menu, generalized from the ad-hoc "MANAGE ▾" menu
// in TrackProblemPanel so every screen shares one pattern for secondary and
// destructive row actions. Destructive entries (danger) render red and should
// be separated from ordinary ones with a "divider" entry.
//
// The panel is portaled to <body> and positioned from the trigger's rect:
// several parents (PixelCard) clip with overflow:hidden, which would swallow
// an inline-absolute dropdown. A fixed-position portal doesn't track page
// scroll, so the menu simply closes on scroll/resize.

const MONO = "'JetBrains Mono', monospace";

// Default trigger glyph — a compact "list/panel" mark drawn in the same SVG
// language as the sidebar toggle (a left block + three lines) so the app's
// menu affordances read as one family.
const MenuGlyph = () => (
  <svg width="15" height="15" viewBox="0 0 17 17" fill="none" style={{ pointerEvents: "none" }} aria-hidden="true">
    <rect x="2" y="3" width="3.2" height="11" rx="0.5" fill="currentColor" opacity="0.4" />
    <line x1="7.5" y1="4.5"  x2="15" y2="4.5"  stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
    <line x1="7.5" y1="8.5"  x2="15" y2="8.5"  stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
    <line x1="7.5" y1="12.5" x2="15" y2="12.5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
  </svg>
);

export interface PixelMenuItem {
  label: ReactNode;
  onClick: () => void;
  danger?: boolean;
  disabled?: boolean;
  icon?: ReactNode;
}

export type PixelMenuEntry = PixelMenuItem | "divider";

interface PixelMenuProps {
  items: PixelMenuEntry[];
  // Trigger content. Defaults to a list/panel glyph (see MenuGlyph).
  label?: ReactNode;
  triggerVariant?: "ghost" | "secondary";
  size?: "sm" | "md";
  disabled?: boolean;
  ariaLabel?: string;
  align?: "right" | "left";
  minWidth?: number;
}

const EST_ITEM_HEIGHT = 36;

export function PixelMenu({
  items,
  label,
  triggerVariant = "ghost",
  size = "sm",
  disabled = false,
  ariaLabel = "More actions",
  align = "right",
  minWidth = 160,
}: PixelMenuProps) {
  const [open, setOpen] = useState(false);
  // null until measured — the panel stays invisible for that first commit so it
  // never flashes at the document-flow position before jumping to the anchor.
  const [pos, setPos] = useState<React.CSSProperties | null>(null);
  const anchorRef = useRef<HTMLSpanElement | null>(null);
  const panelRef = useRef<HTMLDivElement | null>(null);

  function focusTrigger() {
    anchorRef.current?.querySelector("button")?.focus();
  }

  // Compute the fixed position from the trigger rect; flip upward when the
  // estimated height would overflow the viewport bottom.
  useLayoutEffect(() => {
    if (!open) { setPos(null); return; }
    const rect = anchorRef.current?.getBoundingClientRect();
    if (!rect) return;
    const estHeight = items.length * EST_ITEM_HEIGHT + 16;
    const openUp = rect.bottom + estHeight > window.innerHeight && rect.top > estHeight;
    const style: React.CSSProperties = { position: "fixed", zIndex: 500, minWidth };
    if (openUp) style.bottom = window.innerHeight - rect.top + 4;
    else style.top = rect.bottom + 4;
    if (align === "right") style.right = Math.max(8, window.innerWidth - rect.right);
    else style.left = rect.left;
    setPos(style);
  }, [open, items.length, align, minWidth]);

  // Focus the first enabled item once the panel is mounted.
  useEffect(() => {
    if (!open) return;
    panelRef.current?.querySelector<HTMLButtonElement>("button:not(:disabled)")?.focus();
  }, [open]);

  // Dismissal: outside pointerdown (covers touch), Escape (refocus trigger),
  // any scroll (capture — the fixed panel would detach from its anchor), resize.
  useEffect(() => {
    if (!open) return;
    function onPointerDown(e: PointerEvent) {
      const target = e.target as Node;
      if (anchorRef.current?.contains(target) || panelRef.current?.contains(target)) return;
      setOpen(false);
    }
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") {
        setOpen(false);
        focusTrigger();
      }
    }
    function close() {
      setOpen(false);
    }
    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    window.addEventListener("scroll", close, true);
    window.addEventListener("resize", close);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("scroll", close, true);
      window.removeEventListener("resize", close);
    };
  }, [open]);

  function moveFocus(delta: 1 | -1) {
    const panel = panelRef.current;
    if (!panel) return;
    const buttons = Array.from(panel.querySelectorAll<HTMLButtonElement>("button:not(:disabled)"));
    if (buttons.length === 0) return;
    const current = buttons.indexOf(document.activeElement as HTMLButtonElement);
    const next = current === -1 ? 0 : (current + delta + buttons.length) % buttons.length;
    buttons[next].focus();
  }

  function onPanelKeyDown(e: React.KeyboardEvent) {
    if (e.key === "ArrowDown") { e.preventDefault(); moveFocus(1); }
    else if (e.key === "ArrowUp") { e.preventDefault(); moveFocus(-1); }
    else if (e.key === "Home" || e.key === "End") {
      e.preventDefault();
      const buttons = panelRef.current?.querySelectorAll<HTMLButtonElement>("button:not(:disabled)");
      if (buttons && buttons.length > 0) buttons[e.key === "Home" ? 0 : buttons.length - 1].focus();
    }
  }

  // ArrowDown on the closed trigger opens the menu (first item then receives focus).
  function onAnchorKeyDown(e: React.KeyboardEvent) {
    if (!open && e.key === "ArrowDown") {
      e.preventDefault();
      setOpen(true);
    }
  }

  return (
    <span ref={anchorRef} style={{ display: "inline-flex" }} onKeyDown={onAnchorKeyDown}>
      <PixelButton
        size={size}
        variant={triggerVariant}
        disabled={disabled}
        onClick={() => setOpen(o => !o)}
        ariaLabel={ariaLabel}
        ariaHasPopup="menu"
        ariaExpanded={open}
      >
        {label ?? <MenuGlyph />}
      </PixelButton>
      {open && createPortal(
        <div
          ref={panelRef}
          role="menu"
          aria-label={ariaLabel}
          onKeyDown={onPanelKeyDown}
          style={{
            ...(pos ?? { position: "fixed", top: 0, left: 0, visibility: "hidden" }),
            background: C.surface2,
            border: `1px solid ${C.border}`,
            boxShadow: "0 6px 18px rgba(0,0,0,0.4)",
            borderRadius: 0,
            padding: "4px 0",
          }}
        >
          {items.map((entry, i) =>
            entry === "divider" ? (
              <div key={`div-${i}`} role="separator" style={{ height: 1, background: C.border, margin: "4px 0" }} />
            ) : (
              <button
                key={i}
                type="button"
                role="menuitem"
                disabled={entry.disabled}
                onClick={() => { setOpen(false); entry.onClick(); }}
                style={{
                  display: "flex", alignItems: "center", gap: 8, width: "100%", textAlign: "left",
                  cursor: entry.disabled ? "not-allowed" : "pointer",
                  background: "none", border: "none", padding: "8px 12px",
                  color: entry.danger ? "#f87171" : C.text,
                  opacity: entry.disabled ? 0.45 : 1,
                  fontFamily: MONO, fontSize: 12, whiteSpace: "nowrap",
                }}
                onMouseEnter={(e) => { if (!entry.disabled) (e.currentTarget as HTMLElement).style.background = "rgba(255,255,255,0.05)"; }}
                onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.background = "none"; }}
                onFocus={(e) => { (e.currentTarget as HTMLElement).style.background = "rgba(255,255,255,0.05)"; }}
                onBlur={(e) => { (e.currentTarget as HTMLElement).style.background = "none"; }}
              >
                {entry.icon}
                {entry.label}
              </button>
            )
          )}
        </div>,
        document.body,
      )}
    </span>
  );
}
