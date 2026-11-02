import { useState, useEffect, Fragment } from "react";
import {
  C, GradientText, PixelCard, PixelButton,
} from "@/shared/components/PixelComponents";
import { adminApi, ApiError, SystemLogItem } from "@/shared/apiClient";

function fmtDateTime(iso?: string) {
  if (!iso) return "—";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  return `${String(d.getDate()).padStart(2, "0")}/${String(d.getMonth() + 1).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}:${String(d.getSeconds()).padStart(2, "0")}`;
}

// Colour the action token so destructive / sensitive events stand out in the log.
function actionColor(action: string): string {
  const a = action.toUpperCase();
  if (a.includes("DEACTIVATE") || a.includes("REVOKE") || a.includes("DELETE") || a.includes("REJECT")) return "#ef4444";
  if (a.includes("CREATE") || a.includes("GRANT") || a.includes("ACTIVATE") || a.includes("APPROVE")) return C.green;
  if (a.includes("UPDATE")) return "#facc15";
  return "#60a5fa";
}

export function AdminSystemLogsPage() {
  const [logs, setLogs] = useState<SystemLogItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [expanded, setExpanded] = useState<Record<number, boolean>>({});

  function load() {
    setLoading(true);
    setFetchError(null);
    adminApi.getSystemLogs()
      .then(res => setLogs(res.data ?? []))
      .catch(err => setFetchError(err instanceof ApiError ? err.message : "Failed to load system logs."))
      .finally(() => setLoading(false));
  }

  useEffect(() => { load(); }, []);

  const query = search.trim().toLowerCase();
  const rows = query
    ? logs.filter(l =>
        l.action.toLowerCase().includes(query) ||
        (l.detail ?? "").toLowerCase().includes(query) ||
        (l.actorName ?? "").toLowerCase().includes(query))
    : logs;

  return (
    <div style={{ padding: 24, display: "flex", flexDirection: "column", gap: 20 }}>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 28, fontWeight: 800 }}>
          <GradientText>System Logs</GradientText>
        </h1>
        <PixelButton variant="secondary" size="sm" onClick={load} disabled={loading}>
          {loading ? "…" : "↻ REFRESH"}
        </PixelButton>
      </div>

      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, alignItems: "center" }}>
        <PixelButton size="sm" variant="ghost" onClick={() => setExpanded(Object.fromEntries(rows.map(l => [l.logId, true])))}>EXPAND ALL</PixelButton>
        <PixelButton size="sm" variant="ghost" onClick={() => setExpanded({})}>COLLAPSE ALL</PixelButton>
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search action, actor or detail..."
          style={{ width: 280, padding: "8px 12px", background: C.surface2, border: `1px solid ${C.border}`, color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, outline: "none", borderRadius: 0 }}
        />
      </div>

      <PixelCard style={{ padding: 0, overflow: "hidden" }}>
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontFamily: "'JetBrains Mono', monospace" }}>
            <thead>
              <tr style={{ background: C.surface2, borderBottom: `1px solid ${C.border}` }}>
                {["", "Time", "Actor", "Action", "Detail", "IP"].map((h, idx) => (
                  <th key={idx} style={{ color: C.green, fontSize: 10, letterSpacing: "0.12em", textAlign: "left", padding: "12px 14px", fontWeight: 600, textTransform: "uppercase" }}>
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr><td colSpan={6} style={{ padding: 20, color: C.textMuted, fontSize: 12, textAlign: "center" }}>Loading...</td></tr>
              )}
              {!loading && fetchError && (
                <tr><td colSpan={6} style={{ padding: 20, color: C.red, fontSize: 12, textAlign: "center" }}>{fetchError}</td></tr>
              )}
              {!loading && !fetchError && rows.length === 0 && (
                <tr><td colSpan={6} style={{ padding: 20, color: C.textMuted, fontSize: 12, textAlign: "center" }}>No log entries</td></tr>
              )}
              {!loading && rows.map((l, i) => {
                const open = !!expanded[l.logId];
                const hasDetail = Boolean(l.detail);
                return (
                <Fragment key={l.logId}>
                <tr
                  onClick={() => hasDetail && setExpanded(p => ({ ...p, [l.logId]: !open }))}
                  style={{ borderBottom: `1px solid rgba(34,197,94,0.06)`, background: i % 2 === 0 ? C.surface : C.surface2, cursor: hasDetail ? "pointer" : "default" }}
                >
                  <td style={{ color: C.textMuted, fontSize: 11, padding: "12px 14px", width: 18 }}>{hasDetail ? (open ? "▾" : "▸") : ""}</td>
                  <td style={{ color: C.textMuted, fontSize: 11, padding: "12px 14px", whiteSpace: "nowrap" }}>{fmtDateTime(l.createdAt)}</td>
                  <td style={{ color: C.text, fontSize: 11, padding: "12px 14px" }}>{l.actorName ?? (l.actorUserId ? `user#${l.actorUserId}` : "—")}</td>
                  <td style={{ fontSize: 11, padding: "12px 14px", fontWeight: 700, color: actionColor(l.action), whiteSpace: "nowrap" }}>{l.action}</td>
                  <td style={{ color: C.textMuted, fontSize: 11, padding: "12px 14px", maxWidth: 360, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: open ? "normal" : "nowrap" }}>{l.detail ?? "—"}</td>
                  <td style={{ color: C.textDim, fontSize: 11, padding: "12px 14px" }}>{l.ipAddress ?? "—"}</td>
                </tr>
                {open && hasDetail && (
                  <tr style={{ background: i % 2 === 0 ? C.surface : C.surface2 }}>
                    <td />
                    <td colSpan={5} style={{ color: C.textMuted, fontSize: 11, padding: "0 14px 12px", wordBreak: "break-all", lineHeight: 1.6 }}>{l.detail}</td>
                  </tr>
                )}
                </Fragment>
                );
              })}
            </tbody>
          </table>
        </div>
      </PixelCard>
    </div>
  );
}
