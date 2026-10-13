import { useState, useEffect } from "react";
import { useNavigate } from "react-router";
import {
  C, GradientText, PixelCard, PixelButton, PixelBadge, PixelTabs,
} from "@/shared/components/PixelComponents";
import { PixelMenu } from "@/shared/components/PixelMenu";
import {
  adminApi, ApiError, apiErrorMessage, UserItem, CreateUserPayload, UpdateUserPayload,
} from "@/shared/apiClient";
import { useNotifications } from "@/app/providers/NotificationProvider";

function fmtDate(iso?: string) {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}

function userTypeBadge(userType: string) {
  if (userType === 'FPT_STUDENT') return <PixelBadge color="green">FPT</PixelBadge>;
  if (userType === 'EXTERNAL_STUDENT') return <PixelBadge color="cyan">EXTERNAL</PixelBadge>;
  if (userType === 'STAFF') return <PixelBadge color="blue">STAFF</PixelBadge>;
  return <PixelBadge color="gray">{userType}</PixelBadge>;
}

function judgeTypeBadge(judgeType?: string) {
  if (judgeType === 'GUEST') return <PixelBadge color="orange">GUEST</PixelBadge>;
  if (judgeType === 'INTERNAL') return <PixelBadge color="green">INTERNAL</PixelBadge>;
  return <span style={{ color: C.textMuted, fontSize: 11 }}>—</span>;
}

function approvedBadge(isApproved: boolean) {
  return isApproved
    ? <PixelBadge color="green">APPROVED</PixelBadge>
    : <PixelBadge color="yellow">PENDING</PixelBadge>;
}

function isActiveBadge(isActive: boolean) {
  return isActive
    ? <PixelBadge color="green">TRUE</PixelBadge>
    : <PixelBadge color="gray">FALSE</PixelBadge>;
}

const inputStyle: React.CSSProperties = {
  width: "100%", boxSizing: "border-box", padding: "10px 12px",
  background: C.surface2, border: `1px solid ${C.border}`, color: C.text,
  fontFamily: "'JetBrains Mono', monospace", fontSize: 12, outline: "none", borderRadius: 0,
};

const labelStyle: React.CSSProperties = {
  display: "block", color: C.greenMuted, fontFamily: "'JetBrains Mono', monospace",
  fontSize: 11, letterSpacing: "0.1em", textTransform: "uppercase", marginBottom: 6,
};

// ── Shared modal shell ───────────────────────────────────────────────
function ModalShell({ accent, tag, title, children, onCancel }: {
  accent: string; tag?: string; title: string; children: React.ReactNode; onCancel: () => void;
}) {
  return (
    <>
      <div onClick={onCancel} style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.65)", zIndex: 400, backdropFilter: "blur(2px)" }} />
      <div style={{
        position: "fixed", top: "50%", left: "50%", transform: "translate(-50%, -50%)", zIndex: 401,
        width: "min(480px, calc(100vw - 32px))", maxHeight: "calc(100vh - 48px)", overflowY: "auto",
        background: C.surface, border: `1px solid ${accent}66`,
        boxShadow: `0 0 40px ${accent}22, 0 16px 48px rgba(0,0,0,0.4)`, padding: 32,
      }}>
        <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${accent}, transparent)` }} />
        {tag && (
          <div style={{ color: accent, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, letterSpacing: "0.1em", marginBottom: 12 }}>
            // {tag}
          </div>
        )}
        <h2 style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 20, fontWeight: 800, color: C.text, marginBottom: 20, lineHeight: 1.2 }}>
          {title}
        </h2>
        {children}
      </div>
    </>
  );
}

// ── Create account modal ─────────────────────────────────────────────
function CreateAccountModal({ onClose, onCreated }: { onClose: () => void; onCreated: (u: UserItem) => void }) {
  const { addToast } = useNotifications();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fullName, setFullName] = useState("");
  const [userType, setUserType] = useState<CreateUserPayload['userType']>('STAFF');
  const [judgeType, setJudgeType] = useState<'' | 'INTERNAL' | 'GUEST'>('');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function submit() {
    setError(null);
    if (!email.trim() || !password || !fullName.trim()) {
      setError("Email, password and full name are required.");
      addToast({ type: "warning", title: "MISSING FIELDS", message: "Email, password and full name are required." });
      return;
    }
    setSaving(true);
    try {
      const payload: CreateUserPayload = {
        email: email.trim(), password, fullName: fullName.trim(), userType,
        ...(judgeType ? { judgeType } : {}),
      };
      const res = await adminApi.createUser(payload);
      addToast({ type: "success", title: "ACCOUNT CREATED", message: `"${res.data.fullName}" was created.` });
      onCreated(res.data);
      onClose();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create account.");
      addToast({ type: "warning", title: "CREATE FAILED", message: apiErrorMessage(err, "Failed to create account.") });
    } finally {
      setSaving(false);
    }
  }

  return (
    <ModalShell accent="#22c55e" title="Create account" onCancel={onClose}>
      <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
        <div>
          <label style={labelStyle}>Email</label>
          <input style={inputStyle} value={email} onChange={(e) => setEmail(e.target.value)} placeholder="user@fpt.edu.vn" autoComplete="off" name="new-account-email" />
        </div>
        <div>
          <label style={labelStyle}>Password</label>
          <input style={inputStyle} type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="min 8 characters" autoComplete="new-password" name="new-account-password" />
        </div>
        <div>
          <label style={labelStyle}>Full name</label>
          <input style={inputStyle} value={fullName} onChange={(e) => setFullName(e.target.value)} placeholder="Nguyen Van A" />
        </div>
        <div style={{ display: "flex", gap: 12 }}>
          <div style={{ flex: 1 }}>
            <label style={labelStyle}>User type</label>
            <select style={inputStyle} value={userType} onChange={(e) => setUserType(e.target.value as CreateUserPayload['userType'])}>
              <option value="STAFF">STAFF</option>
              <option value="FPT_STUDENT">FPT_STUDENT</option>
              <option value="EXTERNAL_STUDENT">EXTERNAL_STUDENT</option>
            </select>
          </div>
          <div style={{ flex: 1 }}>
            <label style={labelStyle}>Judge type</label>
            <select style={inputStyle} value={judgeType} onChange={(e) => setJudgeType(e.target.value as '' | 'INTERNAL' | 'GUEST')}>
              <option value="">— none —</option>
              <option value="INTERNAL">INTERNAL</option>
              <option value="GUEST">GUEST</option>
            </select>
          </div>
        </div>
        {error && (
          <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, padding: "8px 12px" }}>
            ERROR: {error}
          </div>
        )}
        <div style={{ display: "flex", gap: 10, marginTop: 4 }}>
          <PixelButton variant="cyber" onClick={submit} disabled={saving}>
            {saving ? "CREATING…" : "CREATE ACCOUNT"}
          </PixelButton>
          <PixelButton variant="secondary" onClick={onClose} disabled={saving}>CANCEL</PixelButton>
        </div>
      </div>
    </ModalShell>
  );
}

// ── Edit account modal ───────────────────────────────────────────────
function EditAccountModal({ user, onClose, onUpdated }: { user: UserItem; onClose: () => void; onUpdated: (u: UserItem) => void }) {
  const { addToast } = useNotifications();
  const [fullName, setFullName] = useState(user.fullName);
  const [studentId, setStudentId] = useState(user.studentId ?? "");
  const [university, setUniversity] = useState(user.university ?? "");
  const [judgeType, setJudgeType] = useState<'' | 'INTERNAL' | 'GUEST'>((user.judgeType as '' | 'INTERNAL' | 'GUEST') ?? "");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function submit() {
    setError(null);
    setSaving(true);
    try {
      const payload: UpdateUserPayload = {
        fullName: fullName.trim(),
        studentId,
        university,
        judgeType,
      };
      const res = await adminApi.updateUser(user.userId, payload);
      addToast({ type: "success", title: "ACCOUNT UPDATED", message: `"${res.data.fullName}" was updated.` });
      onUpdated(res.data);
      onClose();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to update account.");
      addToast({ type: "warning", title: "UPDATE FAILED", message: apiErrorMessage(err, "Failed to update account.") });
    } finally {
      setSaving(false);
    }
  }

  return (
    <ModalShell accent="#3b82f6" tag={`edit_user#${user.userId}`} title={`Edit ${user.fullName}`} onCancel={onClose}>
      <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
        <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11 }}>
          {user.email} · {user.userType}
        </div>
        <div>
          <label style={labelStyle}>Full name</label>
          <input style={inputStyle} value={fullName} onChange={(e) => setFullName(e.target.value)} />
        </div>
        <div style={{ display: "flex", gap: 12 }}>
          <div style={{ flex: 1 }}>
            <label style={labelStyle}>Student ID</label>
            <input style={inputStyle} value={studentId} onChange={(e) => setStudentId(e.target.value)} placeholder="—" />
          </div>
          <div style={{ flex: 1 }}>
            <label style={labelStyle}>University</label>
            <input style={inputStyle} value={university} onChange={(e) => setUniversity(e.target.value)} placeholder="—" />
          </div>
        </div>
        <div>
          <label style={labelStyle}>Judge type</label>
          <select style={inputStyle} value={judgeType} onChange={(e) => setJudgeType(e.target.value as '' | 'INTERNAL' | 'GUEST')}>
            <option value="">— none —</option>
            <option value="INTERNAL">INTERNAL</option>
            <option value="GUEST">GUEST</option>
          </select>
        </div>
        {error && (
          <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, padding: "8px 12px" }}>
            ERROR: {error}
          </div>
        )}
        <div style={{ display: "flex", gap: 10, marginTop: 4 }}>
          <PixelButton variant="primary" onClick={submit} disabled={saving}>
            {saving ? "SAVING…" : "SAVE CHANGES"}
          </PixelButton>
          <PixelButton variant="secondary" onClick={onClose} disabled={saving}>CANCEL</PixelButton>
        </div>
      </div>
    </ModalShell>
  );
}

type Tab = 'ALL' | 'STUDENTS' | 'STAFF' | 'IS_ACTIVE';

export function AdminAccountsPage() {
  const navigate = useNavigate();
  const { addToast } = useNotifications();
  const [users, setUsers] = useState<UserItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<Tab>('ALL');
  const [search, setSearch] = useState("");
  const [togglingId, setTogglingId] = useState<number | null>(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<UserItem | null>(null);

  useEffect(() => {
    setLoading(true);
    setFetchError(null);
    adminApi.getUsers()
      .then(res => setUsers(res.data ?? []))
      .catch(err => setFetchError(err instanceof ApiError ? err.message : "Failed to load accounts."))
      .finally(() => setLoading(false));
  }, []);

  async function toggleActive(u: UserItem) {
    setTogglingId(u.userId);
    try {
      const res = u.isActive
        ? await adminApi.deactivateUser(u.userId)
        : await adminApi.activateUser(u.userId);
      if (res.data) upsert(res.data);
      addToast({
        type: "success",
        title: u.isActive ? "ACCOUNT DEACTIVATED" : "ACCOUNT ACTIVATED",
        message: `"${u.fullName}" is now ${u.isActive ? "inactive" : "active"}.`,
      });
    } catch (err) {
      addToast({ type: "warning", title: "ACTION FAILED", message: apiErrorMessage(err, "Failed to update account.") });
    } finally {
      setTogglingId(null);
    }
  }

  function upsert(u: UserItem) {
    setUsers(prev => {
      const idx = prev.findIndex(x => x.userId === u.userId);
      if (idx === -1) return [u, ...prev];
      const next = prev.slice();
      next[idx] = u;
      return next;
    });
  }

  const allCount      = users.length;
  const studentCount  = users.filter(u => u.userType !== 'STAFF').length;
  const staffCount    = users.filter(u => u.userType === 'STAFF').length;
  const isActiveCount = users.filter(u => u.isActive).length;

  const tabFiltered = users.filter(u => {
    switch (activeTab) {
      case 'STUDENTS': return u.userType !== 'STAFF';
      case 'STAFF':    return u.userType === 'STAFF';
      case 'IS_ACTIVE': return u.isActive;
      default:         return true;
    }
  });
  const query = search.trim().toLowerCase();
  const rows = query
    ? tabFiltered.filter(u => u.fullName.toLowerCase().includes(query) || u.email.toLowerCase().includes(query))
    : tabFiltered;

  return (
    <div style={{ padding: 24, display: "flex", flexDirection: "column", gap: 20 }}>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 28, fontWeight: 800 }}>
          <GradientText>Accounts</GradientText>
        </h1>
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
          <PixelButton variant="cyber" size="sm" onClick={() => setCreateOpen(true)}>+ CREATE ACCOUNT</PixelButton>
        </div>
      </div>

      <div style={{ display: "flex", flexWrap: "wrap", gap: 16, alignItems: "center", justifyContent: "space-between" }}>
        <PixelTabs
          tabs={[
            { id: "ALL",      label: `All (${allCount})` },
            { id: "STUDENTS", label: `Students (${studentCount})` },
            { id: "STAFF",    label: `Staff (${staffCount})` },
            { id: "IS_ACTIVE", label: `isActive (${isActiveCount})` },
          ]}
          active={activeTab}
          onChange={(id) => setActiveTab(id as Tab)}
        />
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search name or email..."
          style={{ width: 240, padding: "8px 12px", background: C.surface2, border: `1px solid ${C.border}`, color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, outline: "none", borderRadius: 0 }}
        />
      </div>

      <PixelCard style={{ padding: 0, overflow: "hidden" }}>
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontFamily: "'JetBrains Mono', monospace" }}>
            <thead>
              <tr style={{ background: C.surface2, borderBottom: `1px solid ${C.border}` }}>
                {["Full Name", "Email", "Type", "Judge", "Roles", "Status", "isActive", "Created", "Actions"].map(h => (
                  <th key={h} style={{ color: C.green, fontSize: 10, letterSpacing: "0.12em", textAlign: "left", padding: "12px 14px", fontWeight: 600, textTransform: "uppercase" }}>
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr><td colSpan={9} style={{ padding: 20, color: C.textMuted, fontSize: 12, textAlign: "center" }}>Loading...</td></tr>
              )}
              {!loading && fetchError && (
                <tr><td colSpan={9} style={{ padding: 20, color: C.red, fontSize: 12, textAlign: "center" }}>{fetchError}</td></tr>
              )}
              {!loading && !fetchError && rows.length === 0 && (
                <tr><td colSpan={9} style={{ padding: 20, color: C.textMuted, fontSize: 12, textAlign: "center" }}>No accounts</td></tr>
              )}
              {!loading && rows.map((u, i) => (
                <tr key={u.userId} className="row-actionable" style={{ borderBottom: `1px solid rgba(34,197,94,0.06)`, background: i % 2 === 0 ? C.surface : C.surface2 }}>
                  <td style={{ color: C.text, fontSize: 12, padding: "12px 14px" }}>{u.fullName}</td>
                  <td style={{ color: C.textMuted, fontSize: 11, padding: "12px 14px" }}>{u.email}</td>
                  <td style={{ padding: "12px 14px" }}>{userTypeBadge(u.userType)}</td>
                  <td style={{ padding: "12px 14px" }}>{judgeTypeBadge(u.judgeType)}</td>
                  <td style={{ color: C.textMuted, fontSize: 11, padding: "12px 14px" }}>{u.roles && u.roles.length > 0 ? u.roles.join(", ") : "—"}</td>
                  <td style={{ padding: "12px 14px" }}>{approvedBadge(u.isApproved)}</td>
                  <td style={{ padding: "12px 14px" }}>{isActiveBadge(u.isActive)}</td>
                  <td style={{ color: C.textMuted, fontSize: 11, padding: "12px 14px" }}>{fmtDate(u.createdAt)}</td>
                  <td style={{ padding: "12px 14px", width: 48 }}>
                    <span className="row-action">
                      <PixelMenu
                        ariaLabel={`Actions for ${u.fullName}`}
                        items={[
                          { label: "Edit", onClick: () => setEditTarget(u) },
                          "divider",
                          {
                            label: u.isActive ? "Deactivate" : "Activate",
                            danger: u.isActive,
                            disabled: togglingId === u.userId,
                            onClick: () => toggleActive(u),
                          },
                        ]}
                      />
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </PixelCard>

      {createOpen && <CreateAccountModal onClose={() => setCreateOpen(false)} onCreated={upsert} />}
      {editTarget && <EditAccountModal user={editTarget} onClose={() => setEditTarget(null)} onUpdated={upsert} />}
    </div>
  );
}
