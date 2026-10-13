import {
  createContext, useContext, useState, useEffect, useCallback,
  ReactNode, useRef,
} from "react";
import { CheckCircle2, AlertTriangle, Info, LucideIcon } from "lucide-react";
import { useAuth } from "@/app/providers/AuthProvider";
import { notificationsApi, Notification as ApiNotification } from "@/shared/apiClient";
import { C } from "@/shared/components/PixelComponents";
import { AnnouncementSplash } from "@/shared/components/AnnouncementSplash";
import { NotificationDetailModal } from "@/shared/components/NotificationDetailModal";

// ── UI notification (mapped from the API NotificationResponse) ───────
export type NotifKind = "info" | "success" | "warning";

export interface UINotification {
  notification_id: number;
  title: string;
  message: string;
  is_read: boolean;
  type: NotifKind;
  // Raw backend type (e.g. TIMER, ANNOUNCEMENT) — kept so the poll can decide
  // whether to auto-banner. type (above) is only the 3 visual kinds.
  rawType?: string;
  created_at: string;
  // Announcement-only: source info for the email-style detail popup.
  from?: string | null;
  sender_role?: string | null;
  scope_label?: string | null;
  link_url?: string | null;
}

// Backend `type` is a free-form string (e.g. TEAM_APPROVED, ACCOUNT_REJECTED);
// fold it into the three visual kinds the bell/banner styling understands.
export function toKind(type?: string): NotifKind {
  const t = (type ?? "").toUpperCase();
  if (/SUCCESS|APPROV|ACCEPT|PUBLISH|WIN|ADVANCE/.test(t)) return "success";
  if (/WARN|REJECT|DISQUALIF|FAIL|REMOV|DECLINE/.test(t)) return "warning";
  return "info";
}

export function mapNotification(n: ApiNotification): UINotification {
  return {
    notification_id: n.notificationId,
    title: n.title,
    message: n.content,
    is_read: Boolean(n.isRead),
    type: toKind(n.type),
    rawType: n.type,
    created_at: n.createdAt,
    from: n.senderName ?? null,
    sender_role: n.senderRole ?? null,
    scope_label: n.scopeLabel ?? null,
    link_url: n.linkUrl ?? null,
  };
}

// ── Banner ───────────────────────────────────────────────────────────
// ONE unified ephemeral banner for the whole app: auth events (login/logout),
// action feedback (save/delete/approve...) and — auto-surfaced — every NEW
// persistent notification arriving from /api/notifications. It slides in from
// the top-center, glass + tech (squared corners) to match the pixel theme.
export interface Banner {
  id: string;
  type: NotifKind;
  title: string;
  message: string;
  // Set when the banner mirrors a persistent notification — lets a click
  // mark that notification read and pop open the bell.
  notificationId?: number;
}

// Backward-compatible aliases — existing call sites pass {type,title,message}.
export type Toast = Banner;
export type AuthToast = Banner;

// ── Context type ────────────────────────────────────────────────────
interface NotificationContextType {
  userNotifications: UINotification[];
  unreadCount: number;
  markAllRead: () => void;
  markRead: (id: number) => void;
  refresh: () => void;
  addToast: (t: Omit<Toast, "id">) => void;
  /** Banner + session-local bell history for actionable UI failures. */
  addActionNotification: (t: Omit<Toast, "id" | "notificationId">) => void;
  addAuthToast: (t: Omit<AuthToast, "id">) => void;
  // Cross-component signal: bumped when a banner asks to open the bell dropdown.
  bellOpenSignal: number;
  requestOpenBell: () => void;
}

const NotificationContext = createContext<NotificationContextType | null>(null);

export function useNotifications() {
  const ctx = useContext(NotificationContext);
  if (!ctx) throw new Error("useNotifications must be used within NotificationProvider");
  return ctx;
}

// How long a banner stays before auto-dismissing (ms). Paused on hover.
const BANNER_DURATION = 4500;
// Poll cadence for surfacing brand-new persistent notifications (ms).
const POLL_INTERVAL = 25000;
// Once-per-login-session flag: since the system is polling-only (no live push),
// unread announcements that arrived before this page load are surfaced ONCE as a
// splash on the first fetch of the session — mirroring how the role guide auto-
// shows once per session. Cleared on logout so the next login shows them again.
const BASELINE_SPLASH_KEY = "sealAnnounceSplashSeen";

// Human "from" summary for a batch of announcement notifications.
export function summariseSenders(senders: string[]): string {
  if (senders.length <= 1) return senders[0] ?? "a coordinator";
  if (senders.length === 2) return `${senders[0]} and ${senders[1]}`;
  return `${senders[0]} and ${senders.length - 1} others`;
}

// Build the splash payload from a list of announcement notifications (null = none).
// Keeps the actual items so "View" can open the message detail directly.
export function announceSplashFor(items: UINotification[]): { items: UINotification[]; from: string } | null {
  if (items.length === 0) return null;
  const senders = [...new Set(items.map(a => a.from).filter(Boolean))] as string[];
  return { items, from: summariseSenders(senders) };
}

// ── Per-type visual tokens ──────────────────────────────────────────
// Internationally-recognised line icons (lucide) keyed by kind. Note: CRUD
// errors map to the "warning" kind (yellow) — the app keeps 3 visual kinds.
const KIND_ICON: Record<NotifKind, LucideIcon> = {
  success: CheckCircle2,
  warning: AlertTriangle,
  info: Info,
};

function bannerStyle(type: NotifKind) {
  if (type === "success") {
    return { accent: C.green, border: "rgba(34,197,94,0.45)", glow: "rgba(34,197,94,0.20)", chipBg: "rgba(34,197,94,0.14)" };
  }
  if (type === "warning") {
    return { accent: C.yellow, border: "rgba(234,179,8,0.45)", glow: "rgba(234,179,8,0.20)", chipBg: "rgba(234,179,8,0.14)" };
  }
  return { accent: C.cyan, border: "rgba(6,182,212,0.45)", glow: "rgba(6,182,212,0.20)", chipBg: "rgba(6,182,212,0.14)" };
}

// ── Single banner (glass + tech, squared corners) ───────────────────
function BannerItem({
  banner, onDismiss, onActivate,
}: {
  banner: Banner;
  onDismiss: (id: string) => void;
  onActivate: (b: Banner) => void;
}) {
  const [entered, setEntered] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const [paused, setPaused] = useState(false);
  const remainingRef = useRef(BANNER_DURATION);
  const startRef = useRef(0);
  const timerRef = useRef<number>(0);

  const s = bannerStyle(banner.type);
  const Icon = KIND_ICON[banner.type];

  // Enter animation: two RAFs so the initial off-screen transform paints first.
  useEffect(() => {
    const raf = requestAnimationFrame(() => requestAnimationFrame(() => setEntered(true)));
    return () => cancelAnimationFrame(raf);
  }, []);

  const beginLeave = useCallback(() => {
    setLeaving(true);
    window.setTimeout(() => onDismiss(banner.id), 300);
  }, [banner.id, onDismiss]);

  // Auto-dismiss countdown with hover-to-pause (keeps the progress bar in sync).
  useEffect(() => {
    if (leaving) return;
    if (paused) {
      remainingRef.current -= Date.now() - startRef.current;
      if (timerRef.current) window.clearTimeout(timerRef.current);
      return;
    }
    startRef.current = Date.now();
    timerRef.current = window.setTimeout(beginLeave, Math.max(0, remainingRef.current));
    return () => { if (timerRef.current) window.clearTimeout(timerRef.current); };
  }, [paused, leaving, beginLeave]);

  const isVisible = entered && !leaving;

  return (
    <div
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onClick={() => onActivate(banner)}
      style={{
        position: "relative",
        overflow: "hidden",
        width: "100%",
        boxSizing: "border-box",
        background: "rgba(13, 17, 23, 0.92)",
        backdropFilter: "blur(20px)",
        WebkitBackdropFilter: "blur(20px)",
        border: `1px solid ${s.border}`,
        borderTop: `2px solid ${s.accent}`,
        borderRadius: 3,
        boxShadow: `0 10px 34px rgba(0,0,0,0.55), 0 0 18px ${s.glow}`,
        padding: "12px 16px 13px 13px",
        display: "flex",
        alignItems: "flex-start",
        gap: 11,
        cursor: "pointer",
        userSelect: "none",
        transform: isVisible ? "translateY(0)" : "translateY(-115%)",
        opacity: isVisible ? 1 : 0,
        transition: leaving
          ? "transform 0.28s ease-in, opacity 0.24s ease-in"
          : "transform 0.4s cubic-bezier(0.22, 1, 0.36, 1), opacity 0.3s ease",
        fontFamily: "'JetBrains Mono', monospace",
      }}
    >
      {/* Square icon chip with an internationally-recognised line icon */}
      <div style={{
        width: 26, height: 26, borderRadius: 2,
        background: s.chipBg,
        border: `1px solid ${s.border}`,
        display: "flex", alignItems: "center", justifyContent: "center",
        flexShrink: 0, marginTop: 1,
        boxShadow: `0 0 8px ${s.glow}`,
      }}>
        <Icon size={15} strokeWidth={2.5} color={s.accent} />
      </div>

      {/* Text */}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          color: "#eef2ff", fontSize: 12, fontWeight: 700,
          letterSpacing: "0.06em", marginBottom: 3, textTransform: "uppercase",
        }}>
          {banner.title}
        </div>
        <div style={{ color: "rgba(148,163,184,0.92)", fontSize: 11, lineHeight: 1.5, wordBreak: "break-word" }}>
          {banner.message}
        </div>
      </div>

      {/* Close */}
      <button
        onClick={(e) => { e.stopPropagation(); beginLeave(); }}
        style={{
          background: "none", border: "none", cursor: "pointer",
          color: "rgba(100,116,139,0.85)", fontSize: 13, padding: 0,
          flexShrink: 0, lineHeight: 1, marginTop: 2,
          transition: "color 0.15s",
        }}
        onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.color = "#94a3b8"; }}
        onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.color = "rgba(100,116,139,0.85)"; }}
      >
        ✕
      </button>

      {/* Auto-dismiss countdown bar */}
      <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, height: 2, background: "rgba(255,255,255,0.06)" }}>
        <div style={{
          height: "100%", width: "100%",
          background: s.accent,
          transformOrigin: "left",
          boxShadow: `0 0 6px ${s.glow}`,
          animation: `sealBannerShrink ${BANNER_DURATION}ms linear forwards`,
          animationPlayState: paused ? "paused" : "running",
        }} />
      </div>
    </div>
  );
}

// ── Banner stack (fixed top-center, newest on top, max 3 + overflow) ─
function BannerContainer({
  banners, onDismiss, onActivate,
}: {
  banners: Banner[];
  onDismiss: (id: string) => void;
  onActivate: (b: Banner) => void;
}) {
  if (banners.length === 0) return null;
  const MAX_VISIBLE = 3;
  const visible = [...banners.slice(-MAX_VISIBLE)].reverse(); // newest first
  const overflow = banners.length - visible.length;

  return (
    <div style={{
      position: "fixed",
      top: 0, left: 0, right: 0,
      zIndex: 9999,
      display: "flex",
      flexDirection: "column",
      alignItems: "center",
      padding: "14px 16px 0",
      gap: 8,
      pointerEvents: "none",
    }}>
      {visible.map(b => (
        <div key={b.id} style={{ pointerEvents: "auto", width: "min(420px, calc(100vw - 32px))" }}>
          <BannerItem banner={b} onDismiss={onDismiss} onActivate={onActivate} />
        </div>
      ))}
      {overflow > 0 && (
        <div style={{
          pointerEvents: "none",
          fontFamily: "'JetBrains Mono', monospace",
          fontSize: 10, letterSpacing: "0.08em",
          color: "rgba(148,163,184,0.85)",
          background: "rgba(13,17,23,0.85)",
          backdropFilter: "blur(8px)",
          WebkitBackdropFilter: "blur(8px)",
          border: "1px solid rgba(148,163,184,0.18)",
          borderRadius: 2,
          padding: "3px 10px",
        }}>
          +{overflow} more
        </div>
      )}
    </div>
  );
}

// ── Provider ────────────────────────────────────────────────────────
export function NotificationProvider({ children }: { children: ReactNode }) {
  const { currentUser, patchCurrentUser, refreshTeamContext, isLoading } = useAuth();
  const [notifications, setNotifications] = useState<UINotification[]>([]);
  // UI action failures are not backend domain notifications, but users still
  // need to revisit them from the bell during the current signed-in session.
  const [localNotifications, setLocalNotifications] = useState<UINotification[]>([]);
  const [banners, setBanners] = useState<Banner[]>([]);
  // Welcome-style splash for freshly-arrived announcement messages.
  const [announceSplash, setAnnounceSplash] = useState<{ items: UINotification[]; from: string } | null>(null);
  // Email-style detail popup opened from the splash's "View message" button.
  const [detailNotif, setDetailNotif] = useState<UINotification | null>(null);
  const [bellOpenSignal, setBellOpenSignal] = useState(0);
  const counterRef = useRef(0);
  const localNotificationIdRef = useRef(-1);
  // IDs already accounted for, so polling only banners genuinely new arrivals.
  const seenIdsRef = useRef<Set<number>>(new Set());
  // First fetch just establishes a baseline — it must NOT banner the backlog.
  const baselineSetRef = useRef(false);

  const dismissBanner = useCallback((id: string) => {
    setBanners(prev => prev.filter(b => b.id !== id));
  }, []);

  const pushBanner = useCallback((t: Omit<Banner, "id">) => {
    const id = `banner-${Date.now()}-${counterRef.current++}`;
    setBanners(prev => [...prev, { ...t, id }].slice(-6));
  }, []);

  const requestOpenBell = useCallback(() => setBellOpenSignal(s => s + 1), []);

  const markOneRead = useCallback((notificationId: number) => {
    if (notificationId < 0) {
      setLocalNotifications(prev => prev.map(n =>
        n.notification_id === notificationId ? { ...n, is_read: true } : n,
      ));
      return;
    }
    setNotifications(prev => prev.map(n =>
      n.notification_id === notificationId ? { ...n, is_read: true } : n,
    ));
    notificationsApi.markAsRead(notificationId).catch(() => {});
  }, []);

  // Click a banner: if it mirrors a real notification, mark it read and pop the
  // bell open so the user lands on the full history; then clear the banner.
  const activateBanner = useCallback((banner: Banner) => {
    if (banner.notificationId != null) {
      markOneRead(banner.notificationId);
      requestOpenBell();
    }
    dismissBanner(banner.id);
  }, [markOneRead, requestOpenBell, dismissBanner]);

  // The /api/notifications endpoint already scopes to the authenticated user.
  const refresh = useCallback(() => {
    notificationsApi.getAll()
      .then(res => {
        const list = (res.data ?? [])
          .map(mapNotification)
          .sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime());
        setNotifications(list);

        // First load after sign-in: remember what's already there, no banners.
        if (!baselineSetRef.current) {
          seenIdsRef.current = new Set(list.map(n => n.notification_id));
          baselineSetRef.current = true;
          // Polling-only: surface pre-existing UNREAD announcements as a splash
          // ONCE per login session (so a reload doesn't nag; a new login does).
          if (!sessionStorage.getItem(BASELINE_SPLASH_KEY)) {
            sessionStorage.setItem(BASELINE_SPLASH_KEY, "1");
            const splash = announceSplashFor(list.filter(n => !n.is_read && n.from));
            if (splash) setAnnounceSplash(splash);
          }
          return;
        }

        // Surface every NEW arrival. Announcements (carry a sender) are aggregated
        // into ONE welcome-style splash; everything else slides in as a banner.
        // Iterate oldest-first so the newest ends up on top of the stack.
        const fresh = list.filter(n => !seenIdsRef.current.has(n.notification_id));
        const freshAnnouncements: UINotification[] = [];
        [...fresh].reverse().forEach(n => {
          seenIdsRef.current.add(n.notification_id);
          if (n.is_read) return;
          if (n.rawType === "PARTICIPATION_ACCESS_APPROVED") {
            // Re-resolve team context instead of hardcoding "no team" — a
            // reactivated leader may already have a dormant team waiting
            // (rejoin), which a blind team_id:null reset would hide.
            patchCurrentUser({ is_active: true });
            refreshTeamContext();
          }
          if (n.from) {
            freshAnnouncements.push(n);
          } else if (n.rawType === "TIMER") {
            // Timer milestones are bannered instantly client-side by useRoundTimer;
            // keep them in the bell history but don't double-banner from the poll.
          } else {
            pushBanner({
              type: n.type, title: n.title, message: n.message,
              notificationId: n.notification_id,
            });
          }
        });
        const liveSplash = announceSplashFor(freshAnnouncements);
        if (liveSplash) setAnnounceSplash(liveSplash);
      })
      .catch(() => { /* keep last known list on failure */ });
  }, [pushBanner]);

  // Initial load + reset everything on sign-out / user switch.
  useEffect(() => {
    if (!currentUser) {
      setNotifications([]);
      setLocalNotifications([]);
      setBanners([]);
      seenIdsRef.current = new Set();
      baselineSetRef.current = false;
      // Only a genuine logout resets the once-per-session splash flag. A page
      // reload transiently nulls currentUser while auth restores (isLoading) —
      // resetting then would re-fire the splash on every reload.
      if (!isLoading) sessionStorage.removeItem(BASELINE_SPLASH_KEY);
      return;
    }
    refresh();
  }, [currentUser, isLoading, refresh]);

  // Poll for brand-new notifications while signed in.
  useEffect(() => {
    if (!currentUser) return;
    const id = window.setInterval(refresh, POLL_INTERVAL);
    return () => window.clearInterval(id);
  }, [currentUser, refresh]);

  const userNotifications = [...localNotifications, ...notifications]
    .sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime());
  const unreadCount = userNotifications.filter(n => !n.is_read).length;

  const markAllRead = useCallback(() => {
    notificationsApi.markAllAsRead().catch(() => {});
    setNotifications(prev => prev.map(n => ({ ...n, is_read: true })));
    setLocalNotifications(prev => prev.map(n => ({ ...n, is_read: true })));
  }, []);

  const markRead = useCallback((id: number) => {
    if (id < 0) {
      setLocalNotifications(prev => prev.map(n =>
        n.notification_id === id ? { ...n, is_read: true } : n,
      ));
      return;
    }
    setNotifications(prev => {
      const target = prev.find(n => n.notification_id === id);
      if (!target || target.is_read) return prev; // already read → no API call
      notificationsApi.markAsRead(id).catch(() => {});
      return prev.map(n => (n.notification_id === id ? { ...n, is_read: true } : n));
    });
  }, []);

  // Both legacy entry points now feed the single unified banner stack.
  const addToast = useCallback((t: Omit<Toast, "id">) => pushBanner(t), [pushBanner]);
  const addActionNotification = useCallback((t: Omit<Toast, "id" | "notificationId">) => {
    const notificationId = localNotificationIdRef.current--;
    setLocalNotifications(prev => [{
      notification_id: notificationId,
      title: t.title,
      message: t.message,
      is_read: false,
      type: t.type,
      rawType: "UI_ACTION",
      created_at: new Date().toISOString(),
    }, ...prev].slice(0, 30));
    pushBanner({ ...t, notificationId });
  }, [pushBanner]);
  const addAuthToast = useCallback((t: Omit<AuthToast, "id">) => pushBanner(t), [pushBanner]);

  return (
    <NotificationContext.Provider value={{
      userNotifications, unreadCount, markAllRead, markRead, refresh,
      addToast, addActionNotification, addAuthToast, bellOpenSignal, requestOpenBell,
    }}>

      {children}
      <BannerContainer banners={banners} onDismiss={dismissBanner} onActivate={activateBanner} />
      <AnnouncementSplash
        open={announceSplash != null}
        count={announceSplash?.items.length ?? 0}
        from={announceSplash?.from ?? ""}
        onView={() => {
          const items = announceSplash?.items ?? [];
          // One message → open it directly (email-style detail). Several → open
          // the bell list so the user can pick which one to read.
          if (items.length === 1) {
            markOneRead(items[0].notification_id);
            setDetailNotif(items[0]);
          } else {
            requestOpenBell();
          }
          setAnnounceSplash(null);
        }}
        onClose={() => setAnnounceSplash(null)}
      />
      <NotificationDetailModal notification={detailNotif} onClose={() => setDetailNotif(null)} />
    </NotificationContext.Provider>
  );
}
