import { useState, useEffect } from "react";
import {
  C, GradientText, PixelCard, PixelButton, PixelBadge, PixelTabs,
} from "@/shared/components/PixelComponents";
import { PixelMenu } from "@/shared/components/PixelMenu";
import { ConfirmDialog } from "@/shared/components/ConfirmDialog";
import { accountApprovalsApi, participationRequestsApi, teamRejoinRequestsApi, ApiError, apiErrorMessage, PendingAccount, UserItem } from "@/shared/apiClient";
import { usePendingAccounts } from "@/app/providers/PendingAccountsProvider";
import { universityLabel } from "@/shared/userDisplay";
import { useNotifications } from "@/app/providers/NotificationProvider";
import { ParticipationRequestsPanel } from "./CoordParticipationRequestsPage";
import { TeamRejoinRequestsPanel } from "./TeamRejoinRequestsPanel";

// After the platform split, full account management (create/edit, global role
// grants) still belongs to the System Admin under /api/admin. The coordinator
// gets a scoped, read-only slice of it here: the approval queue (their one
// actionable responsibility), participation requests, and two read-only
// headcount views — every active participant, and every active judge/mentor-
// eligible staff member — via /api/account-approvals.
//
// Pending approvals is a "waiting on you" queue, so amber (the PENDING colour)
// accents that table (headers, card glow, selection state) while the page title
// stays the standard app-wide gradient. Per-row actions live in a hover ⋯ menu
// (like the Event track/round rows); bulk selection + APPROVE/REJECT SELECTED
// clears the queue fast. Clicking a row selects it (no checkbox needed to start);
// the tick-boxes themselves only appear once at least one row is selected, to
// fine-tune the set — they stay invisible at rest instead of reveal-on-hover.
// Approved/rejected rows leave the list immediately: this tab only ever shows
// accounts still awaiting a decision, newest applicant first.

const MONO = "'JetBrains Mono', monospace";
const AMBER = "#eab308";        // C.yellow — frame / headers / selection accent

function fmtDate(iso?: string) {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}

function studentTypeBadge(userType: string) {
  if (userType === 'FPT_STUDENT') return <PixelBadge color="green">FPT</PixelBadge>;
  if (userType === 'EXTERNAL_STUDENT') return <PixelBadge color="cyan">EXTERNAL</PixelBadge>;
  if (userType === 'STAFF') return <PixelBadge color="blue">STAFF</PixelBadge>;
  return <PixelBadge color="gray">{userType}</PixelBadge>;
}

// ── Approve / Reject confirmation modal (single account) ─────────────
function ApprovalModal({ account, reject, onClose, onConfirm, working, error }: {
  account: PendingAccount;
  reject: boolean;
  onClose: () => void;
  onConfirm: () => void;
  working: boolean;
  error: string | null;
}) {
  const accent = reject ? "#ef4444" : "#22c55e";
  const name = <span style={{ color: C.text, fontWeight: 700 }}>"{account.fullName}"</span>;
  return (
    <>
      <div onClick={onClose} style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.65)", zIndex: 400, backdropFilter: "blur(2px)" }} />
      <div style={{
        position: "fixed", top: "50%", left: "50%", transform: "translate(-50%, -50%)", zIndex: 401,
        width: "min(460px, calc(100vw - 32px))", background: C.surface, border: `1px solid ${accent}66`,
        boxShadow: `0 0 40px ${accent}22, 0 16px 48px rgba(0,0,0,0.4)`, padding: 32,
      }}>
        <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${accent}, transparent)` }} />
        <h2 style={{ fontFamily: MONO, fontSize: 20, fontWeight: 800, color: C.text, marginBottom: 12, lineHeight: 1.2 }}>
          {reject ? "Reject this account?" : "Approve this account?"}
        </h2>
        <p style={{ color: C.textMuted, fontFamily: MONO, fontSize: 12, lineHeight: 1.8, marginBottom: 24 }}>
          {reject
            ? <>You are about to reject {name} ({account.email}). Their pending account will be permanently deleted.</>
            : <>You are about to approve {name} ({account.email}). They will be able to log in once approved.</>}
        </p>
        {error && (
          <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: MONO, fontSize: 11, padding: "8px 12px", marginBottom: 16 }}>
            ERROR: {error}
          </div>
        )}
        <div style={{ display: "flex", gap: 10 }}>
          <PixelButton variant={reject ? "danger" : "cyber"} onClick={onConfirm} disabled={working}>
            {working ? "WORKING…" : reject ? "REJECT ACCOUNT" : "APPROVE ACCOUNT"}
          </PixelButton>
          <PixelButton variant="secondary" onClick={onClose} disabled={working}>CANCEL</PixelButton>
        </div>
      </div>
    </>
  );
}

const HEADERS = ["Full Name", "Email", "Student Type", "Student ID", "University", "Applied"];

function roleBadges(roles?: string[]) {
  if (!roles || roles.length === 0) return <span style={{ color: C.textMuted, fontFamily: MONO, fontSize: 11 }}>—</span>;
  return (
    <span style={{ display: "inline-flex", gap: 6, flexWrap: "wrap" }}>
      {roles.map(r => <PixelBadge key={r} color={r === "JUDGE" ? "blue" : r === "MENTOR" ? "cyan" : "gray"}>{r}</PixelBadge>)}
    </span>
  );
}

// Read-only headcount view — "All Participant" / "Judge & Mentor" tabs. No
// selection, no per-row actions: these are informational, not a queue to act on.
function ReadOnlyAccountsTable({ rows, loading, error, countLabel, emptyLabel, showRoles }: {
  rows: UserItem[];
  loading: boolean;
  error: string | null;
  countLabel: string;
  emptyLabel: string;
  showRoles?: boolean;
}) {
  const [search, setSearch] = useState("");
  const query = search.trim().toLowerCase();
  const filtered = query
    ? rows.filter(u => u.fullName.toLowerCase().includes(query) || u.email.toLowerCase().includes(query))
    : rows;
  const headers = showRoles
    ? ["Full Name", "Email", "Type", "Roles", "Joined"]
    : ["Full Name", "Email", "Student Type", "Student ID", "University", "Joined"];

  return (
    <>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 16, alignItems: "center", justifyContent: "space-between" }}>
        <PixelBadge color="green">{rows.length} {countLabel}</PixelBadge>
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search by name or email..."
          style={{ width: 240, padding: "8px 12px", background: C.surface2, border: `1px solid ${C.border}`, color: C.text, fontFamily: MONO, fontSize: 12, outline: "none", borderRadius: 0 }}
        />
      </div>

      <PixelCard glow style={{ padding: 0, overflow: "hidden" }}>
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontFamily: MONO }}>
            <thead>
              <tr style={{ background: C.surface2, borderBottom: `1px solid ${C.border}` }}>
                {headers.map(h => (
                  <th key={h} style={{ color: C.green, fontSize: 10, letterSpacing: "0.12em", textAlign: "left", padding: "12px 14px", fontWeight: 600, textTransform: "uppercase" }}>
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr><td colSpan={headers.length} style={{ padding: 20, color: C.textMuted, fontSize: 12, textAlign: "center" }}>Loading...</td></tr>
              )}
              {!loading && error && (
                <tr><td colSpan={headers.length} style={{ padding: 20, color: C.red, fontSize: 12, textAlign: "center" }}>{error}</td></tr>
              )}
              {!loading && !error && filtered.length === 0 && (
                <tr><td colSpan={headers.length} style={{ padding: 20, color: C.textMuted, fontSize: 12, textAlign: "center" }}>{emptyLabel}</td></tr>
              )}
              {!loading && filtered.map((u, i) => (
                <tr key={u.userId} style={{ background: i % 2 === 0 ? C.surface : C.surface2 }}>
                  <td style={{ color: C.text, fontSize: 12, padding: "12px 14px" }}>{u.fullName}</td>
                  <td style={{ color: C.textMuted, fontSize: 11, padding: "12px 14px" }}>{u.email}</td>
                  {showRoles ? (
                    <>
                      <td style={{ padding: "12px 14px" }}>{u.judgeType ? <PixelBadge color={u.judgeType === "GUEST" ? "cyan" : "blue"}>{u.judgeType}</PixelBadge> : <span style={{ color: C.textMuted, fontFamily: MONO, fontSize: 11 }}>—</span>}</td>
                      <td style={{ padding: "12px 14px" }}>{roleBadges(u.roles)}</td>
                    </>
                  ) : (
                    <>
                      <td style={{ padding: "12px 14px" }}>{studentTypeBadge(u.userType)}</td>
                      <td style={{ color: C.textMuted, fontSize: 11, padding: "12px 14px" }}>{u.studentId ?? "—"}</td>
                      <td style={{ color: C.textMuted, fontSize: 11, padding: "12px 14px" }}>{universityLabel(u.userType, u.university) ?? "—"}</td>
                    </>
                  )}
                  <td style={{ color: C.textMuted, fontSize: 11, padding: "12px 14px" }}>{fmtDate(u.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </PixelCard>
    </>
  );
}

type AccountsTab = "approvals" | "participation" | "rejoin" | "participants" | "staff";

export function CoordAccountsPage() {
  const [tab, setTab] = useState<AccountsTab>("approvals");
  const [participants, setParticipants] = useState<UserItem[]>([]);
  const [staffList, setStaffList] = useState<UserItem[]>([]);
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState<string | null>(null);
  const [loadedLists, setLoadedLists] = useState<Set<AccountsTab>>(new Set());
  const [accounts, setAccounts] = useState<PendingAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [confirmTarget, setConfirmTarget] = useState<{ account: PendingAccount; reject: boolean } | null>(null);
  const [working, setWorking] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  // Bulk selection (fast queue clearing).
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [bulkAction, setBulkAction] = useState<null | "approve" | "reject">(null);
  const [bulkWorking, setBulkWorking] = useState(false);
  // Badge on the "Resolve Request" tab, visible without switching to it.
  const [participationPendingCount, setParticipationPendingCount] = useState(0);
  // Badge on the "Team Rejoin" tab, visible without switching to it.
  const [rejoinPendingCount, setRejoinPendingCount] = useState(0);

  const { setPendingCount } = usePendingAccounts();
  const { addToast } = useNotifications();

  useEffect(() => {
    setLoading(true);
    setFetchError(null);
    accountApprovalsApi.getPending()
      .then(res => {
        // Newest applicant first.
        const list = (res.data ?? []).slice().sort((a, b) =>
          new Date(b.createdAt ?? 0).getTime() - new Date(a.createdAt ?? 0).getTime());
        setAccounts(list);
        setPendingCount(list.length);
      })
      .catch(err => setFetchError(err instanceof ApiError ? err.message : "Failed to load pending accounts."))
      .finally(() => setLoading(false));
  }, [setPendingCount]);

  useEffect(() => {
    participationRequestsApi.getPending()
      .then(res => setParticipationPendingCount((res.data ?? []).length))
      .catch(() => { /* non-blocking — the tab's own panel surfaces load errors */ });
  }, []);

  useEffect(() => {
    teamRejoinRequestsApi.getPending()
      .then(res => setRejoinPendingCount((res.data ?? []).length))
      .catch(() => { /* non-blocking — the tab's own panel surfaces load errors */ });
  }, []);

  // Participants / Staff are read-only headcount views — fetched once, on first visit to each tab.
  useEffect(() => {
    if (tab !== "participants" && tab !== "staff") return;
    if (loadedLists.has(tab)) return;

    setListLoading(true);
    setListError(null);
    const request = tab === "participants" ? accountApprovalsApi.getActiveParticipants() : accountApprovalsApi.getActiveJudgeMentorStaff();
    request
      .then(res => {
        // Newest account first — same "stack" ordering as the Approvals queue.
        const list = (res.data ?? []).slice().sort((a, b) =>
          new Date(b.createdAt ?? 0).getTime() - new Date(a.createdAt ?? 0).getTime());
        if (tab === "participants") setParticipants(list);
        else setStaffList(list);
        setLoadedLists(prev => new Set(prev).add(tab));
      })
      .catch(err => setListError(err instanceof ApiError ? err.message : "Failed to load accounts."))
      .finally(() => setListLoading(false));
  }, [tab, loadedLists]);

  const query = search.trim().toLowerCase();
  const rows = query
    ? accounts.filter(a => a.fullName.toLowerCase().includes(query) || a.email.toLowerCase().includes(query))
    : accounts;

  // `accounts` is pending-only (approved/rejected rows are removed immediately), so its length is the total.
  const pendingTotal = accounts.length;

  const selectedCount = rows.reduce((n, a) => n + (selectedIds.has(a.userId) ? 1 : 0), 0);
  const allSelected = rows.length > 0 && selectedCount === rows.length;
  const someSelected = selectedCount > 0 && !allSelected;

  function toggleOne(id: number) {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }
  function toggleAll() {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (allSelected) rows.forEach(a => next.delete(a.userId));
      else rows.forEach(a => next.add(a.userId));
      return next;
    });
  }

  // Approve or reject both remove the account from the queue immediately — this
  // tab only ever shows accounts still awaiting a decision.
  function removeFromQueue(ids: Set<number>) {
    setAccounts(prev => {
      const next = prev.filter(a => !ids.has(a.userId));
      setPendingCount(next.length);
      return next;
    });
    setSelectedIds(prev => { const n = new Set(prev); ids.forEach(id => n.delete(id)); return n; });
  }

  // ── Single approve / reject (from a row's ⋯ menu) ──
  async function handleConfirm() {
    if (!confirmTarget) return;
    const { account, reject } = confirmTarget;
    setActionError(null);
    setWorking(true);
    try {
      if (reject) {
        await accountApprovalsApi.reject(account.userId);
      } else {
        await accountApprovalsApi.approve(account.userId);
      }
      removeFromQueue(new Set([account.userId]));
      addToast({
        type: reject ? "info" : "success",
        title: reject ? "ACCOUNT REJECTED" : "ACCOUNT APPROVED",
        message: `"${account.fullName}" ${reject ? "was rejected." : "can now log in."}`,
      });
      setConfirmTarget(null);
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Action failed.");
      addToast({ type: "warning", title: "ACTION FAILED", message: apiErrorMessage(err, "Action failed.") });
    } finally {
      setWorking(false);
    }
  }

  // ── Bulk approve / reject the selected rows ──
  async function runBulk() {
    if (!bulkAction) return;
    const ids = rows.filter(a => selectedIds.has(a.userId)).map(a => a.userId);
    if (ids.length === 0) { setBulkAction(null); return; }
    const reject = bulkAction === "reject";
    setBulkWorking(true);
    try {
      const results = await Promise.allSettled(
        ids.map(id => reject ? accountApprovalsApi.reject(id) : accountApprovalsApi.approve(id)),
      );
      const okIds = new Set(ids.filter((_, i) => results[i].status === "fulfilled"));
      const failed = ids.length - okIds.size;
      removeFromQueue(okIds);
      setSelectedIds(new Set());
      setBulkAction(null);
      addToast({
        type: failed ? "warning" : reject ? "info" : "success",
        title: reject ? "ACCOUNTS REJECTED" : "ACCOUNTS APPROVED",
        message: `${okIds.size} account(s) ${reject ? "rejected" : "approved"}${failed ? ` · ${failed} failed` : ""}.`,
      });
    } finally {
      setBulkWorking(false);
    }
  }

  const cellMuted: React.CSSProperties = { color: C.textMuted, fontSize: 11, padding: "12px 14px" };

  return (
    <div style={{ padding: 24, display: "flex", flexDirection: "column", gap: 20 }}>
      <div>
        <h1 style={{ fontFamily: MONO, fontSize: 28, fontWeight: 800 }}>
          <GradientText>Accounts</GradientText>
        </h1>
      </div>

      <PixelTabs
        tabs={[
          { id: "approvals", label: "Approvals", badge: pendingTotal },
          { id: "participation", label: "Resolve Request", badge: participationPendingCount },
          { id: "rejoin", label: "Team Rejoin", badge: rejoinPendingCount },
          { id: "participants", label: "Participant" },
          { id: "staff", label: "Judge & Mentor" },
        ]}
        active={tab}
        onChange={(id) => setTab(id as AccountsTab)}
      />

      {tab === "participation" ? (
        <ParticipationRequestsPanel />
      ) : tab === "rejoin" ? (
        <TeamRejoinRequestsPanel />
      ) : tab === "participants" ? (
        <ReadOnlyAccountsTable
          rows={participants}
          loading={listLoading}
          error={listError}
          countLabel="ACTIVE PARTICIPANT"
          emptyLabel="No active participants."
        />
      ) : tab === "staff" ? (
        <ReadOnlyAccountsTable
          rows={staffList}
          loading={listLoading}
          error={listError}
          countLabel="ACTIVE JUDGE/MENTOR"
          emptyLabel="No active judge/mentor staff."
          showRoles
        />
      ) : (
      <>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 16, alignItems: "center", justifyContent: "space-between" }}>
        <PixelBadge color="yellow">{pendingTotal} PENDING</PixelBadge>
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search by name or email..."
          style={{ width: 240, padding: "8px 12px", background: C.surface2, border: `1px solid ${C.border}`, color: C.text, fontFamily: MONO, fontSize: 12, outline: "none", borderRadius: 0 }}
        />
      </div>

      {/* Bulk action bar — appears once at least one row is selected. */}
      {selectedCount > 0 && (
        <div style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap", padding: "10px 14px", background: "rgba(234,179,8,0.08)", border: `1px solid rgba(234,179,8,0.4)` }}>
          <span style={{ color: AMBER, fontFamily: MONO, fontSize: 12, fontWeight: 700 }}>{selectedCount} selected</span>
          <div style={{ flex: 1 }} />
          <PixelButton size="sm" variant="danger" onClick={() => setBulkAction("reject")}>REJECT SELECTED ({selectedCount})</PixelButton>
          <PixelButton size="sm" variant="cyber" onClick={() => setBulkAction("approve")}>APPROVE SELECTED ({selectedCount})</PixelButton>
          <PixelButton size="sm" variant="ghost" onClick={() => setSelectedIds(new Set())}>CLEAR</PixelButton>
        </div>
      )}

      <PixelCard glow glowColor="amber" style={{ padding: 0, overflow: "hidden" }}>
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontFamily: MONO }}>
            <thead>
              <tr className="row-actionable" style={{ background: C.surface2, borderBottom: `1px solid rgba(234,179,8,0.3)` }}>
                <th style={{ width: 44, padding: "12px 14px", textAlign: "left" }}>
                  <input
                    type="checkbox"
                    checked={allSelected}
                    ref={(el) => { if (el) el.indeterminate = someSelected; }}
                    onChange={toggleAll}
                    aria-label="Select all"
                    style={{ width: 15, height: 15, accentColor: AMBER, cursor: "pointer", opacity: selectedCount > 0 ? 1 : 0, pointerEvents: selectedCount > 0 ? "auto" : "none", transition: "opacity 0.12s ease" }}
                  />
                </th>
                {HEADERS.map(h => (
                  <th key={h} style={{ color: AMBER, fontSize: 10, letterSpacing: "0.12em", textAlign: "left", padding: "12px 14px", fontWeight: 600, textTransform: "uppercase" }}>
                    {h}
                  </th>
                ))}
                <th style={{ width: 48, padding: "12px 14px" }} />
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr><td colSpan={8} style={{ padding: 20, color: C.textMuted, fontSize: 12, textAlign: "center" }}>Loading...</td></tr>
              )}
              {!loading && fetchError && (
                <tr><td colSpan={8} style={{ padding: 20, color: C.red, fontSize: 12, textAlign: "center" }}>{fetchError}</td></tr>
              )}
              {!loading && !fetchError && rows.length === 0 && (
                <tr><td colSpan={8} style={{ padding: 20, color: C.textMuted, fontSize: 12, textAlign: "center" }}>No pending accounts</td></tr>
              )}
              {!loading && rows.map((a, i) => {
                const selected = selectedIds.has(a.userId);
                return (
                  <tr
                    key={a.userId}
                    className="row-actionable"
                    onClick={() => toggleOne(a.userId)}
                    style={{
                      cursor: "pointer",
                      background: selected ? "rgba(234,179,8,0.10)" : (i % 2 === 0 ? C.surface : C.surface2),
                      transition: "background-color 0.2s ease",
                    }}
                  >
                    {/* Checkbox — stopPropagation so its own toggle isn't doubled by the row
                        click. Hidden until at least one row is selected (selecting a row is
                        done by clicking it; the checkbox then appears to fine-tune the set). */}
                    <td onClick={(e) => e.stopPropagation()} style={{ padding: "12px 14px", borderLeft: `3px solid ${selected ? AMBER : "transparent"}`, transition: "border-color 0.2s ease" }}>
                      <input
                        type="checkbox"
                        checked={selected}
                        onChange={() => toggleOne(a.userId)}
                        aria-label={`Select ${a.fullName}`}
                        style={{ width: 15, height: 15, accentColor: AMBER, cursor: "pointer", opacity: selectedCount > 0 ? 1 : 0, pointerEvents: selectedCount > 0 ? "auto" : "none", transition: "opacity 0.12s ease" }}
                      />
                    </td>
                    <td style={{ color: C.text, fontSize: 12, padding: "12px 14px" }}>{a.fullName}</td>
                    <td style={cellMuted}>{a.email}</td>
                    <td style={{ padding: "12px 14px" }}>{studentTypeBadge(a.userType)}</td>
                    <td style={cellMuted}>{a.studentId ?? "—"}</td>
                    <td style={cellMuted}>{universityLabel(a.userType, a.university) ?? "—"}</td>
                    <td style={cellMuted}>{fmtDate(a.createdAt)}</td>
                    {/* Per-row actions in a hover ⋯ menu (like the Event track/round rows). */}
                    <td onClick={(e) => e.stopPropagation()} style={{ padding: "12px 14px", width: 48 }}>
                      <span className="row-action">
                        <PixelMenu
                          ariaLabel={`Actions for ${a.fullName}`}
                          items={[
                            { label: "Approve", onClick: () => { setActionError(null); setConfirmTarget({ account: a, reject: false }); } },
                            "divider",
                            { label: "Reject", danger: true, onClick: () => { setActionError(null); setConfirmTarget({ account: a, reject: true }); } },
                          ]}
                        />
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </PixelCard>
      </>
      )}

      {confirmTarget && (
        <ApprovalModal
          account={confirmTarget.account}
          reject={confirmTarget.reject}
          working={working}
          error={actionError}
          onClose={() => { setConfirmTarget(null); setActionError(null); }}
          onConfirm={handleConfirm}
        />
      )}

      {bulkAction && (
        <ConfirmDialog
          title={bulkAction === "approve" ? `Approve ${selectedCount} account(s)?` : `Reject ${selectedCount} account(s)?`}
          message={bulkAction === "approve"
            ? `${selectedCount} selected account(s) will be able to log in.`
            : `${selectedCount} selected account(s) will be deactivated and will not be able to log in.`}
          warning={bulkAction === "reject" ? "This deactivates every selected account at once." : undefined}
          confirmLabel={bulkAction === "approve" ? `APPROVE ${selectedCount}` : `REJECT ${selectedCount}`}
          variant={bulkAction === "approve" ? "cyber" : "danger"}
          working={bulkWorking}
          onConfirm={runBulk}
          onClose={() => { if (!bulkWorking) setBulkAction(null); }}
        />
      )}
    </div>
  );
}
