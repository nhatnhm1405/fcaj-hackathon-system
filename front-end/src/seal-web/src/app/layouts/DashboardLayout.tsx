import { ReactNode, useState, useRef, useEffect } from "react";
import { useNavigate, useLocation } from "react-router";
import { C, PixelBadge } from "@/shared/components/PixelComponents";
import { useAuth } from "@/app/providers/AuthProvider";
import { useNotifications, UINotification } from "@/app/providers/NotificationProvider";
import { usePendingAccounts } from "@/app/providers/PendingAccountsProvider";
import { usePendingTeams } from "@/app/providers/PendingTeamsProvider";
import { API_BASE_URL } from "@/shared/apiClient";
import { SealFooter } from "@/shared/components/SealFooter";
import { NotificationDetailModal } from "@/shared/components/NotificationDetailModal";
import { NavbarRoleTimer } from "@/shared/components/NavbarRoleTimer";
import sealLogo from "@/imports/image.png";

const NAVBAR_H = 60;

// Every role's "Dashboard" sidebar item points at the shared "/dashboard" route
// (RoleDashboardPage renders the right console per role). The role-specific URLs
// below are aliases the RoleSelector / older links may land on — we treat them
// all as "the Dashboard page" so the item stays highlighted regardless of which
// one the user arrived through.
const DASHBOARD_PATHS = new Set([
  "/dashboard",
  "/coordinator/dashboard",
  "/admin/dashboard",
  "/dashboard/coordinator",
  "/dashboard/judge",
  "/dashboard/mentor",
]);

interface NavItem {
  path: string;
  label: string;
  badge?: number;
}

function buildNav(role: string, isLeader: boolean, teamId: number | null, pendingCount: number, pendingTeamsCount: number, teamDormant: boolean): NavItem[] {
  if (role === "PARTICIPANT") {
    if (teamId === null) {
      const base: NavItem[] = [{ path: "/dashboard", label: "Dashboard" }, { path: "/leaderboard", label: "Leaderboard" }, { path: "/history", label: "History" }, { path: "/profile", label: "Profile" }];
      if (isLeader) return [{ path: "/dashboard", label: "Dashboard" }, { path: "/team/create", label: "Create Team" }, { path: "/leaderboard", label: "Leaderboard" }, { path: "/history", label: "History" }, { path: "/profile", label: "Profile" }];
      return base;
    }
    if (isLeader) {
      return [
        { path: "/dashboard", label: "Dashboard" },
        { path: "/team/view", label: "My Team" },
        // A dormant team (past season, no live TeamEventEntry) has nothing to
        // submit — hide the nav item rather than show a locked form.
        ...(teamDormant ? [] : [{ path: "/team/submit", label: "Submit Project" }]),
        { path: "/leaderboard", label: "Leaderboard"    },
        { path: "/history",     label: "History"        },
        { path: "/profile",     label: "Profile"        },
      ];
    }
    return [
      { path: "/dashboard", label: "Dashboard" },
      { path: "/team/view", label: "My Team" },
      { path: "/leaderboard", label: "Leaderboard" },
      { path: "/history", label: "History" },
      { path: "/profile", label: "Profile" },
    ];
  }
  if (role === "MENTOR") {
    return [
      { path: "/dashboard",     label: "Dashboard"  },
      { path: "/mentor/tracks", label: "My Tracks"  },
      { path: "/mentor/history",label: "History"    },
      { path: "/leaderboard",   label: "Leaderboard"},
      { path: "/profile",       label: "Profile"    },
    ];
  }
  if (role === "JUDGE") {
    return [
      { path: "/dashboard", label: "Dashboard" },
      { path: "/judge/score", label: "Score Submissions" },
      { path: "/judge/history", label: "Scoring History" },
      { path: "/profile", label: "Profile" },
    ];
  }
  if (role === "ADMIN") {
    return [
      { path: "/dashboard",       label: "Dashboard"   },
      { path: "/admin/events",    label: "Events"      },
      { path: "/admin/accounts",  label: "Accounts"    },
      { path: "/admin/roles",     label: "Role Grants" },
      { path: "/admin/logs",      label: "System Logs" },
      { path: "/profile",         label: "Profile"     },
    ];
  }
  if (role === "COORDINATOR") {
    return [
      { path: "/dashboard",             label: "Dashboard"         },
      { path: "/coordinator/events",    label: "Events"            },
      { path: "/coordinator/accounts",  label: "Accounts", badge: pendingCount },
      { path: "/coordinator/teams",     label: "Teams", badge: pendingTeamsCount },
      { path: "/coordinator/judges",    label: "Assignments"       },
      { path: "/coordinator/scoring",   label: "Scoring & Results" },
      { path: "/coordinator/prizes",    label: "Awards"            },
      { path: "/coordinator/judges/history", label: "History" },
      { path: "/profile",               label: "Profile"           },
    ];
  }
  return [{ path: "/dashboard", label: "Dashboard" }];
}

function isDashboardPath(pathname: string): boolean {
  return DASHBOARD_PATHS.has(pathname);
}

function getPageTitle(pathname: string): string {
  const map: Record<string, string> = {
    "/": "Home",
    "/dashboard": "Dashboard",
    "/dashboard/judge": "Dashboard",
    "/dashboard/mentor": "Dashboard",
    "/dashboard/coordinator": "Dashboard",
    "/admin/dashboard": "Dashboard",
    "/admin/events": "Events",
    "/admin/accounts": "Accounts",
    "/admin/roles": "Role Grants",
    "/admin/logs": "System Logs",
    "/leaderboard": "Leaderboard",
    "/history": "History",
    "/profile": "Profile",
    "/team/create": "Create Team",
    "/team/view": "My Team",
    "/team/manage": "My Team",
    "/team/submit": "Submit Project",
    "/mentor/tracks": "My Tracks",
    "/mentor/history": "Mentoring History",
    "/judge/score": "Score Submissions",
    "/judge/history": "Scoring History",
    "/coordinator/dashboard": "Dashboard",
    "/coordinator/events": "Events",
    "/coordinator/accounts": "Accounts",
    "/coordinator/teams": "Teams",
    "/coordinator/judges": "Assignments",
    "/coordinator/judges/history": "History",
    "/coordinator/scoring": "Scoring & Results",
    "/coordinator/prizes": "Awards",
  };
  return map[pathname] || "Console";
}

function roleBadgeStyle(role: string): { bg: string; color: string } {
  switch (role) {
    case "ADMIN": return { bg: "rgba(239,68,68,0.15)", color: "#ef4444" };
    case "COORDINATOR": return { bg: "rgba(234,179,8,0.15)", color: "#eab308" };
    case "MENTOR": return { bg: "rgba(6,182,212,0.15)", color: "#06b6d4" };
    case "JUDGE": return { bg: "rgba(59,130,246,0.15)", color: "#3b82f6" };
    default: return { bg: "rgba(34,197,94,0.15)", color: "#22c55e" };
  }
}

function getInitials(fullName: string): string {
  const parts = fullName.trim().split(/\s+/);
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

// ── Notification Bell ──────────────────────────────────────────────
function typeColor(type: UINotification["type"]) {
  if (type === "success") return C.green;
  if (type === "warning") return "#eab308";
  return "#06b6d4";
}

function NotificationBell() {
  const { userNotifications, unreadCount, markAllRead, markRead, bellOpenSignal } = useNotifications();
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState<UINotification | null>(null);
  const ref = useRef<HTMLDivElement>(null);

  function openDetail(n: UINotification) {
    setSelected(n);
    markRead(n.notification_id);
    setOpen(false);
  }

  useEffect(() => {
    function handler(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  // A banner click asks us to reveal the full notification history.
  useEffect(() => {
    if (bellOpenSignal > 0) setOpen(true);
  }, [bellOpenSignal]);

  function fmtTime(iso: string) {
    const d = new Date(iso);
    if (isNaN(d.getTime())) return iso;
    return `${String(d.getDate()).padStart(2, "0")}/${String(d.getMonth() + 1).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
  }

  return (
    <>
      <div ref={ref} style={{ position: "relative" }}>
        <button
          onClick={() => setOpen(o => !o)}
          style={{
            position: "relative",
            background: open ? "rgba(34,197,94,0.08)" : "none",
            border: open ? `1px solid ${C.border}` : "1px solid transparent",
            cursor: "pointer",
            padding: "6px 8px",
            borderRadius: 0,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            transition: "all 0.15s",
          }}
          title="Notifications"
          onMouseEnter={(e) => { if (!open) (e.currentTarget as HTMLElement).style.background = "rgba(34,197,94,0.06)"; }}
          onMouseLeave={(e) => { if (!open) (e.currentTarget as HTMLElement).style.background = "none"; }}
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={unreadCount > 0 ? C.green : C.textMuted} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
            <path d="M13.73 21a2 2 0 0 1-3.46 0" />
          </svg>
          {unreadCount > 0 && (
            <span style={{
              position: "absolute", top: 2, right: 2,
              background: C.red, color: "#fff",
              fontFamily: "'JetBrains Mono', monospace", fontSize: 8, fontWeight: 700,
              borderRadius: "50%", width: 14, height: 14,
              display: "flex", alignItems: "center", justifyContent: "center",
            }}>
              {unreadCount > 9 ? "9+" : unreadCount}
            </span>
          )}
        </button>

        {open && (
          <div style={{
            position: "absolute", top: "calc(100% + 6px)", right: 0,
            width: 340,
            background: C.surface,
            border: `1px solid ${C.border}`,
            boxShadow: "0 8px 32px rgba(0,0,0,0.4)",
            zIndex: 300,
            display: "flex",
            flexDirection: "column",
            maxHeight: 420,
            overflow: "hidden",
          }}>
            {/* Header */}
            <div style={{
              display: "flex", alignItems: "center", justifyContent: "space-between",
              padding: "12px 16px",
              borderBottom: `1px solid ${C.border}`,
              flexShrink: 0,
            }}>
              <span style={{ color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, fontWeight: 700, letterSpacing: "0.05em" }}>
                Notifications
                {unreadCount > 0 && (
                  <span style={{ color: C.textMuted }}> · {unreadCount} unread</span>
                )}
              </span>
              {unreadCount > 0 && (
                <button
                  onClick={markAllRead}
                  style={{
                    background: "none", border: "none", cursor: "pointer",
                    color: C.textMuted, fontFamily: "'JetBrains Mono', monospace",
                    fontSize: 9, letterSpacing: "0.08em", padding: 0,
                    transition: "color 0.15s",
                  }}
                  onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.color = C.green; }}
                  onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.color = C.textMuted; }}
                >
                  MARK ALL READ
                </button>
              )}
            </div>

            {/* List */}
            <div style={{ overflowY: "auto", flex: 1 }}>
              {userNotifications.length === 0 ? (
                <div style={{ padding: "24px 16px", color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, textAlign: "center" }}>
                  No notifications
                </div>
              ) : (
                userNotifications.map((n) => (
                  <div
                    key={n.notification_id}
                    onClick={() => openDetail(n)}
                    style={{
                      padding: "12px 16px",
                      borderBottom: `1px solid rgba(34,197,94,0.05)`,
                      background: n.is_read ? "transparent" : "rgba(34,197,94,0.03)",
                      display: "flex",
                      gap: 10,
                      alignItems: "flex-start",
                      cursor: "pointer",
                      transition: "background 0.12s",
                    }}
                    onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.background = "rgba(34,197,94,0.07)"; }}
                    onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.background = n.is_read ? "transparent" : "rgba(34,197,94,0.03)"; }}
                  >
                    {/* Unread dot */}
                    <div style={{
                      width: 6, height: 6, borderRadius: "50%", flexShrink: 0, marginTop: 5,
                      background: n.is_read ? "transparent" : typeColor(n.type),
                      boxShadow: n.is_read ? "none" : `0 0 6px ${typeColor(n.type)}`,
                    }} />
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 3 }}>
                        <span style={{ color: n.is_read ? C.text : typeColor(n.type), fontFamily: "'JetBrains Mono', monospace", fontSize: 11, fontWeight: 700 }}>
                          {n.title}
                        </span>
                      </div>
                      <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 10, lineHeight: 1.5, marginBottom: 4 }}>
                        {n.message}
                      </div>
                      <div style={{ color: C.textDim, fontFamily: "'JetBrains Mono', monospace", fontSize: 9, letterSpacing: "0.06em" }}>
                        {fmtTime(n.created_at)}
                      </div>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        )}
      </div>
      <NotificationDetailModal notification={selected} onClose={() => setSelected(null)} />
    </>
  );
}

// ── Top Navbar ─────────────────────────────────────────────────────
interface TopNavbarProps {
  pageTitle: string;
  collapsed: boolean;
  onToggleCollapse: () => void;
  currentUser: { user_id: number; full_name: string; role: string; is_leader: boolean; team_id: number | null; avatar_url: string | null };
  onLogout: () => void;
  onNavigate: (path: string) => void;
  canSwitchRole: boolean;
  onSwitchRole: () => void;
}

function TopNavbar({ pageTitle, collapsed, onToggleCollapse, currentUser, onLogout, onNavigate, canSwitchRole, onSwitchRole }: TopNavbarProps) {
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const userMenuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (userMenuRef.current && !userMenuRef.current.contains(e.target as Node)) setUserMenuOpen(false);
    }
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, []);

  const badge = roleBadgeStyle(currentUser.role);
  const initials = getInitials(currentUser.full_name);
  const avatarUrl = currentUser.avatar_url
    ? (currentUser.avatar_url.startsWith("http") ? currentUser.avatar_url : `${API_BASE_URL}${currentUser.avatar_url}`)
    : null;

  return (
    <header style={{
      position: "sticky",
      top: 0,
      zIndex: 60,
      height: NAVBAR_H,
      background: C.navbarBg,
      backdropFilter: "blur(12px)",
      borderBottom: `1px solid ${C.border}`,
      display: "flex",
      alignItems: "center",
      padding: "0 20px",
      gap: 16,
      flexShrink: 0,
    }}>
      {/* LEFT */}
      <div style={{ display: "flex", alignItems: "center", gap: 10, flexShrink: 0 }}>
        {/* Sidebar toggle button */}
        <button
          type="button"
          onClick={onToggleCollapse}
          title={collapsed ? "Open sidebar" : "Collapse sidebar"}
          style={{
            background: collapsed ? "rgba(34,197,94,0.08)" : "transparent",
            border: `1px solid ${collapsed ? "rgba(34,197,94,0.4)" : "rgba(34,197,94,0.15)"}`,
            color: collapsed ? C.green : C.textMuted,
            cursor: "pointer",
            width: 38,
            height: 38,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            flexShrink: 0,
            padding: 0,
            transition: "all 0.15s",
            boxShadow: collapsed ? "0 0 12px rgba(34,197,94,0.2)" : "none",
          }}
          onMouseEnter={(e) => { const el = e.currentTarget as HTMLElement; el.style.color = C.green; el.style.borderColor = "rgba(34,197,94,0.5)"; el.style.background = "rgba(34,197,94,0.08)"; }}
          onMouseLeave={(e) => { const el = e.currentTarget as HTMLElement; el.style.color = collapsed ? C.green : C.textMuted; el.style.borderColor = collapsed ? "rgba(34,197,94,0.4)" : "rgba(34,197,94,0.15)"; el.style.background = collapsed ? "rgba(34,197,94,0.08)" : "transparent"; }}
        >
          {collapsed ? (
            /* Hamburger (mở) */
            <svg width="17" height="17" viewBox="0 0 17 17" fill="none" style={{ pointerEvents: "none" }}>
              <line x1="2" y1="4.5" x2="15" y2="4.5" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
              <line x1="2" y1="8.5" x2="15" y2="8.5" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
              <line x1="2" y1="12.5" x2="15" y2="12.5" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
            </svg>
          ) : (
            /* Arrow-left (đóng) */
            <svg width="17" height="17" viewBox="0 0 17 17" fill="none" style={{ pointerEvents: "none" }}>
              <rect x="2" y="3" width="3.5" height="11" rx="0.5" fill="currentColor" opacity="0.35" />
              <line x1="8" y1="4.5" x2="15" y2="4.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
              <line x1="8" y1="8.5" x2="15" y2="8.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
              <line x1="8" y1="12.5" x2="15" y2="12.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
            </svg>
          )}
        </button>
        {/* Logo + brand — clicking returns to the landing page ('/') */}
        <button
          type="button"
          onClick={() => onNavigate("/")}
          title="Go to home"
          style={{ display: "flex", alignItems: "center", gap: 10, height: 44, overflow: "hidden", background: "transparent", border: "none", padding: 0, cursor: "pointer" }}
        >
          <div style={{ width: 96, height: 44, overflow: "hidden", flexShrink: 0, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <img src={sealLogo} alt="SEAL" style={{ height: 96, width: "auto", objectFit: "contain", filter: "drop-shadow(0 0 6px rgba(34,197,94,0.4))", pointerEvents: "none" }} />
          </div>
          <span style={{ color: C.text, fontFamily: "'JetBrains Mono', monospace", fontWeight: 700, fontSize: 13, letterSpacing: "0.06em", background: "linear-gradient(135deg, #22c55e 0%, #3b82f6 100%)", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent", backgroundClip: "text", whiteSpace: "nowrap" }}>
            SEAL Hackathon
          </span>
        </button>
        <span style={{ color: C.border, fontFamily: "'JetBrains Mono', monospace", fontSize: 16, margin: "0 4px" }}>|</span>
        <span style={{ color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 14, fontWeight: 600 }}>{pageTitle}</span>
      </div>

      {/* CENTER — spacer */}
      <div style={{ flex: 1 }} />

      {/* RIGHT — bell + user menu */}
      <div style={{ display: "flex", alignItems: "center", gap: 12, flexShrink: 0 }}>
        <NavbarRoleTimer role={currentUser.role} teamId={currentUser.team_id} />

        {/* Bell */}
        <NotificationBell />

        {/* User menu */}
        <div ref={userMenuRef} style={{ position: "relative" }}>
          <button
            onClick={() => setUserMenuOpen(o => !o)}
            style={{
              display: "flex", alignItems: "center", gap: 8,
              background: userMenuOpen ? "rgba(34,197,94,0.06)" : "transparent",
              border: `1px solid ${userMenuOpen ? C.border : "transparent"}`,
              padding: "6px 10px",
              cursor: "pointer",
              borderRadius: 0,
            }}
          >
            {/* Avatar */}
            {avatarUrl ? (
              <img
                src={avatarUrl}
                alt={currentUser.full_name}
                style={{ width: 32, height: 32, borderRadius: "50%", objectFit: "cover", flexShrink: 0, border: `1px solid ${C.border}` }}
              />
            ) : (
              <div style={{
                width: 32, height: 32, borderRadius: "50%",
                background: "linear-gradient(135deg, #22c55e 0%, #3b82f6 100%)",
                display: "flex", alignItems: "center", justifyContent: "center",
                fontFamily: "'JetBrains Mono', monospace", fontSize: 11, fontWeight: 700, color: "#070c0f",
                flexShrink: 0,
              }}>
                {initials}
              </div>
            )}
            <div style={{ textAlign: "left", display: "flex", flexDirection: "column", gap: 1 }}>
              <span style={{ color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, fontWeight: 700, whiteSpace: "nowrap" }}>
                {currentUser.full_name}
              </span>
              <span style={{
                display: "inline-block",
                background: badge.bg, color: badge.color,
                fontFamily: "'JetBrains Mono', monospace", fontSize: 8, fontWeight: 700,
                letterSpacing: "0.1em", padding: "1px 6px",
              }}>
                {currentUser.role}
              </span>
            </div>
          </button>

          {userMenuOpen && (
            <div style={{
              position: "absolute", top: "100%", right: 0,
              minWidth: 180, background: C.surface, border: `1px solid ${C.border}`,
              borderTop: "none", zIndex: 200, boxShadow: "0 8px 24px rgba(0,0,0,0.5)",
            }}>
              <button
                onClick={() => { onNavigate("/profile"); setUserMenuOpen(false); }}
                style={{ width: "100%", padding: "10px 14px", background: "transparent", border: "none", cursor: "pointer", textAlign: "left", color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, borderRadius: 0, display: "flex", alignItems: "center", gap: 10 }}
                onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.background = "rgba(34,197,94,0.06)"; }}
                onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.background = "transparent"; }}
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0, pointerEvents: "none" }}>
                  <circle cx="12" cy="8" r="4" />
                  <path d="M4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1" />
                </svg>
                Profile
              </button>
              {canSwitchRole && (
                <button
                  onClick={() => { onSwitchRole(); setUserMenuOpen(false); }}
                  style={{ width: "100%", padding: "10px 14px", background: "transparent", border: "none", cursor: "pointer", textAlign: "left", color: "#3b82f6", fontFamily: "'JetBrains Mono', monospace", fontSize: 13, borderRadius: 0, display: "flex", alignItems: "center", gap: 10 }}
                  onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.background = "rgba(59,130,246,0.08)"; }}
                  onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.background = "transparent"; }}
                >
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0, pointerEvents: "none" }}>
                    <polyline points="17 1 21 5 17 9" />
                    <path d="M3 11V9a4 4 0 0 1 4-4h14" />
                    <polyline points="7 23 3 19 7 15" />
                    <path d="M21 13v2a4 4 0 0 1-4 4H3" />
                  </svg>
                  Switch Role
                </button>
              )}
              <div style={{ height: 1, background: C.border, margin: "0 14px" }} />
              <button
                onClick={() => { onLogout(); setUserMenuOpen(false); }}
                style={{ width: "100%", padding: "10px 14px", background: "transparent", border: "none", cursor: "pointer", textAlign: "left", color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, borderRadius: 0 }}
                onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.background = "rgba(239,68,68,0.08)"; }}
                onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.background = "transparent"; }}
              >
                Logout
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}

// ── Dashboard Layout ───────────────────────────────────────────────
export function DashboardLayout({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const location = useLocation();
  const { currentUser, logout, availableRoles, setActiveRole } = useAuth();
  const { addAuthToast } = useNotifications();
  const [collapsed, setCollapsed] = useState(false);
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);
  // Coordinator badges read the shared pending counts so they stay in sync with
  // approve/reject actions on the Accounts/Teams pages (single source of truth).
  const { pendingCount } = usePendingAccounts();
  const { pendingCount: pendingTeamsCount } = usePendingTeams();

  // The scrollable content region is <main> (overflow:auto), not the window, so a
  // route change would otherwise keep the previous page's scroll offset. Reset it
  // (and the window, for safety) to the top whenever the path changes.
  const mainRef = useRef<HTMLElement>(null);
  useEffect(() => {
    mainRef.current?.scrollTo({ top: 0, left: 0 });
    window.scrollTo(0, 0);
  }, [location.pathname]);

  if (!currentUser) return null;

  const nav = buildNav(currentUser.role, currentUser.is_leader, currentUser.team_id, pendingCount, pendingTeamsCount, currentUser.team_dormant);
  const sidebarWidth = collapsed ? 0 : 248;
  const pageTitle = getPageTitle(location.pathname);

  // Team-less participant dashboard owns its own full-screen cyber backdrop.
  const sealNoTeam =
    currentUser.role === "PARTICIPANT" &&
    currentUser.team_id === null &&
    isDashboardPath(location.pathname);
  // System Admin screens skip the main-content grid backdrop — flat background,
  // any grid texture now lives on individual cards (e.g. the dashboard's
  // "Current Event" tile) instead of behind the whole page. currentUser.role
  // is "ADMIN" even on shared routes like /dashboard (RoleDashboardPage
  // resolves the concrete screen internally, no /admin/... redirect), so this
  // can't be a pathname check alone.
  const isAdminRoute = currentUser.role === "ADMIN";
  const sideText = C.textMuted;
  const sideStrong = C.text;
  const dashboardSidebarGrid = `radial-gradient(100% 46% at 50% 20%, rgba(34,197,94,0.10) 0%, transparent 72%), radial-gradient(130% 60% at 50% 46%, transparent 0%, transparent 32%, ${C.surface} 92%), linear-gradient(rgba(59,130,246,0.045) 1px, transparent 1px), linear-gradient(90deg, rgba(34,197,94,0.05) 1px, transparent 1px)`;
  const dashboardMainGrid = `radial-gradient(60% 50% at 50% 34%, rgba(34,197,94,0.06) 0%, transparent 72%), radial-gradient(85% 75% at 50% 40%, transparent 0%, transparent 42%, ${C.bg} 90%), linear-gradient(rgba(59,130,246,0.045) 1px, transparent 1px), linear-gradient(90deg, rgba(34,197,94,0.05) 1px, transparent 1px)`;

  // Logout is confirmed through a themed modal — handleLogout only opens it, the
  // real sign-out happens in performLogout once the user confirms.
  function handleLogout() {
    setShowLogoutConfirm(true);
  }

  function performLogout() {
    const name = currentUser?.full_name ?? 'User';
    setShowLogoutConfirm(false);
    addAuthToast({ type: 'info', title: 'LOGGED OUT', message: `Goodbye, ${name}. See you next time!` });
    logout();
    navigate('/');
  }

  function handleSwitchRole() {
    setActiveRole(null);
    navigate('/select-role');
  }

  return (
    <div style={{ minHeight: "100vh", background: C.bg, display: "flex", flexDirection: "column", color: C.text }}>
      {/* Full-width top navbar */}
      <TopNavbar
        pageTitle={pageTitle}
        collapsed={collapsed}
        onToggleCollapse={() => setCollapsed(c => !c)}
        currentUser={currentUser}
        onLogout={handleLogout}
        onNavigate={navigate}
        canSwitchRole={availableRoles.length > 1}
        onSwitchRole={handleSwitchRole}
      />

      {/* Sidebar + main content row */}
      <div style={{ display: "flex", flex: 1, minHeight: 0 }}>
        <aside
          style={{
            width: sidebarWidth,
            flexShrink: 0,
            // A soft central spotlight + a grid that is bright in the middle and dissolves
            // toward the edges — a same-colour radial vignette (transparent centre → surface
            // at the rim) erases the grid lines near the borders, so the rail reads as a lit
            // panel instead of a flat, uniformly-meshed block. All static, under the nav.
            backgroundColor: C.surface,
            backgroundImage: dashboardSidebarGrid,
            backgroundSize: "100% 100%, 100% 100%, 28px 28px, 28px 28px",
            borderRight: collapsed ? "none" : `1px solid ${C.border}`,
            display: "flex",
            flexDirection: "column",
            transition: "width 0.25s ease",
            position: "sticky",
            top: NAVBAR_H,
            height: `calc(100vh - ${NAVBAR_H}px)`,
            overflow: "hidden",
          }}
        >
          <nav style={{ flex: 1, overflowY: "auto", padding: collapsed ? "12px 6px" : "16px 10px", display: "flex", flexDirection: "column", gap: 2 }}>
            {/* Home — leaves the app chrome and returns to the public landing page */}
            <button
              onClick={() => navigate("/")}
              title={collapsed ? "Home" : undefined}
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: collapsed ? "center" : "flex-start",
                gap: 8,
                padding: collapsed ? "10px 6px" : "10px 12px",
                background: "transparent",
                border: "1px solid transparent",
                borderLeft: "2px solid transparent",
                color: sideText,
                cursor: "pointer",
                fontFamily: "'JetBrains Mono', monospace",
                fontSize: 13,
                letterSpacing: "0.01em",
                textAlign: "left",
                borderRadius: 0,
                transition: "all 0.15s ease",
                width: "100%",
              }}
              onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.color = C.green; }}
              onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.color = sideText; }}
            >
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0, pointerEvents: "none" }}>
                <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
                <polyline points="9 22 9 12 15 12 15 22" />
              </svg>
              {!collapsed && <span style={{ whiteSpace: "nowrap" }}>Home</span>}
            </button>
            {/* Separator between the public-site link and in-app navigation */}
            <div style={{ height: 1, background: C.border, margin: collapsed ? "4px 2px 6px" : "4px 4px 6px" }} />
            {nav.map((item) => {
              const active = item.label === "Dashboard" ? isDashboardPath(location.pathname) : location.pathname === item.path;
              return (
                <button
                  key={item.path}
                  onClick={() => navigate(item.path)}
                  title={collapsed ? item.label : undefined}
                  style={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: collapsed ? "center" : "space-between",
                    gap: 8,
                    padding: collapsed ? "10px 6px" : "10px 12px",
                    background: active ? "rgba(34,197,94,0.1)" : "transparent",
                    border: active ? `1px solid rgba(34,197,94,0.35)` : `1px solid transparent`,
                    borderLeft: active ? `2px solid ${C.green}` : `2px solid transparent`,
                    color: active ? C.green : sideText,
                    cursor: "pointer",
                    fontFamily: "'JetBrains Mono', monospace",
                    fontSize: 13,
                    letterSpacing: "0.01em",
                    textAlign: "left",
                    borderRadius: 0,
                    transition: "all 0.15s ease",
                    boxShadow: active ? `0 0 12px rgba(34,197,94,0.15)` : "none",
                    width: "100%",
                  }}
                  onMouseEnter={(e) => { if (!active) (e.currentTarget as HTMLElement).style.color = C.green; }}
                  onMouseLeave={(e) => { if (!active) (e.currentTarget as HTMLElement).style.color = sideText; }}
                >
                  <span style={{ display: "flex", alignItems: "center", gap: 8, minWidth: 0 }}>
                    {!collapsed && (
                      <span style={{ whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{item.label}</span>
                    )}
                  </span>
                  {!collapsed && item.badge !== undefined && item.badge > 0 && (
                    <PixelBadge color="yellow">{item.badge}</PixelBadge>
                  )}
                </button>
              );
            })}
          </nav>

          <div style={{ borderTop: `1px solid ${C.border}`, padding: collapsed ? "10px 6px" : "14px 14px", display: "flex", flexDirection: "column", gap: 8 }}>
            {!collapsed && (
              <div>
                <div style={{ color: sideText, fontFamily: "'JetBrains Mono', monospace", fontSize: 10, marginBottom: 2 }}>
                  Logged in as
                </div>
                <div style={{ color: sideStrong, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, fontWeight: 700, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                  {currentUser.full_name}
                </div>
              </div>
            )}
            <button
              onClick={handleLogout}
              title="Log out"
              style={{
                padding: "8px",
                background: "transparent",
                border: `1px solid rgba(239,68,68,0.35)`,
                color: C.red,
                cursor: "pointer",
                borderRadius: 0,
                width: "100%",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                transition: "all 0.15s",
              }}
              onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.background = "rgba(239,68,68,0.12)"; }}
              onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.background = "transparent"; }}
            >
              {/* Power icon */}
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ pointerEvents: "none" }}>
                <path d="M18.36 6.64a9 9 0 1 1-12.73 0" />
                <line x1="12" y1="2" x2="12" y2="12" />
              </svg>
            </button>
          </div>
        </aside>

        {/* Main content */}
        <main ref={mainRef} style={{
          flex: 1, overflow: "auto", minWidth: 0,
          // One cohesive lit backdrop shared with the sidebar: a soft central spotlight and a
          // grid that is brightest in the middle and dissolves toward the edges (a same-colour
          // radial vignette erases the grid at the rim). Cards paint opaquely on top; the
          // backdrop stays fixed as the content scrolls.
          backgroundColor: sealNoTeam ? undefined : C.bg,
          backgroundImage: (sealNoTeam || isAdminRoute) ? undefined : dashboardMainGrid,
          backgroundSize: (sealNoTeam || isAdminRoute) ? undefined : "100% 100%, 100% 100%, 28px 28px, 28px 28px",
        }}>
          {children}
        </main>
      </div>

      {/* Full-width footer — outside the sidebar+content row */}
      <SealFooter />

      <LogoutConfirmModal
        open={showLogoutConfirm}
        userName={currentUser.full_name}
        onCancel={() => setShowLogoutConfirm(false)}
        onConfirm={performLogout}
      />
    </div>
  );
}

// ── Logout confirmation modal ──────────────────────────────────────
function LogoutConfirmModal({ open, userName, onCancel, onConfirm }: { open: boolean; userName: string; onCancel: () => void; onConfirm: () => void }) {
  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) { if (e.key === "Escape") onCancel(); }
    document.addEventListener("keydown", onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => { document.removeEventListener("keydown", onKey); document.body.style.overflow = prev; };
  }, [open, onCancel]);

  if (!open) return null;

  const mono = "'JetBrains Mono', monospace";

  return (
    <div
      onClick={onCancel}
      style={{
        position: "fixed", inset: 0, zIndex: 1000,
        background: "rgba(7,12,15,0.85)", backdropFilter: "blur(4px)",
        display: "flex", alignItems: "center", justifyContent: "center", padding: 20,
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          position: "relative", width: "100%", maxWidth: 400,
          background: C.surface, border: `1px solid ${C.border}`,
          boxShadow: "0 0 40px rgba(239,68,68,0.1), 0 20px 60px rgba(0,0,0,0.6)",
          padding: "26px 26px 22px",
        }}
      >
        <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${C.red}, transparent)`, opacity: 0.7 }} />

        <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 14 }}>
          <div style={{ width: 40, height: 40, borderRadius: "50%", border: `1px solid rgba(239,68,68,0.4)`, background: "rgba(239,68,68,0.06)", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={C.red} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M18.36 6.64a9 9 0 1 1-12.73 0" />
              <line x1="12" y1="2" x2="12" y2="12" />
            </svg>
          </div>
          <h2 style={{ fontFamily: mono, fontWeight: 800, fontSize: 18, letterSpacing: "0.04em", color: C.text, margin: 0 }}>Log out?</h2>
        </div>

        <p style={{ color: C.textMuted, fontFamily: mono, fontSize: 12, lineHeight: 1.7, margin: "0 0 22px" }}>
          You're signed in as <span style={{ color: C.text, fontWeight: 700 }}>{userName}</span>. You'll need to log in again to continue.
        </p>

        <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
          <button
            onClick={onCancel}
            style={{ padding: "9px 18px", background: "transparent", border: `1px solid ${C.border}`, color: C.textMuted, cursor: "pointer", fontFamily: mono, fontSize: 12, letterSpacing: "0.06em", borderRadius: 0, transition: "all 0.15s" }}
            onMouseEnter={(e) => { const el = e.currentTarget as HTMLElement; el.style.color = C.text; el.style.borderColor = C.borderBright; }}
            onMouseLeave={(e) => { const el = e.currentTarget as HTMLElement; el.style.color = C.textMuted; el.style.borderColor = C.border; }}
          >
            Cancel
          </button>
          <button
            onClick={onConfirm}
            style={{ padding: "9px 18px", background: "rgba(239,68,68,0.1)", border: `1px solid rgba(239,68,68,0.5)`, color: C.red, cursor: "pointer", fontFamily: mono, fontSize: 12, fontWeight: 700, letterSpacing: "0.08em", textTransform: "uppercase", borderRadius: 0, transition: "all 0.15s" }}
            onMouseEnter={(e) => { const el = e.currentTarget as HTMLElement; el.style.background = "rgba(239,68,68,0.2)"; }}
            onMouseLeave={(e) => { const el = e.currentTarget as HTMLElement; el.style.background = "rgba(239,68,68,0.1)"; }}
          >
            Logout
          </button>
        </div>
      </div>
    </div>
  );
}
