import { useEffect, useState } from "react";
import { C, PixelBadge } from "@/shared/components/PixelComponents";
import { auditLogsApi, ApiError, type AuditLogEntry } from "@/shared/apiClient";
import { fmtDT } from "@/features/events/eventUtils";

// The Audit tab of CoordEventsPage: the event's audit trail. Extracted verbatim
// out of CoordEventsPage.tsx — behavior, API calls, and copy are unchanged.
// Self-fetches on mount, which replicates the parent's previous
// `detailTab === 'audit'`-keyed effect: this component only mounts once the
// Audit tab is actually selected, so mounting already means "tab opened".

// Audit metadata is persisted as a JSON blob; render it as readable "Label value"
// rows instead of dumping the raw JSON string. Falls back to the raw text for
// anything that isn't a flat object.
function humanizeAuditKey(key: string): string {
  const s = key.replace(/[_-]+/g, " ").trim();
  return s.charAt(0).toUpperCase() + s.slice(1);
}

function formatAuditValue(value: unknown): string {
  if (value == null) return "—";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

function AuditMetadata({ json }: { json: string }) {
  const mono = "'JetBrains Mono', monospace";
  let parsed: unknown = null;
  try { parsed = JSON.parse(json); } catch { /* not JSON — fall through to raw */ }
  if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
    const entries = Object.entries(parsed as Record<string, unknown>);
    if (entries.length > 0) {
      return (
        <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
          {entries.map(([k, v]) => (
            <div key={k} style={{ display: "flex", gap: 10, fontFamily: mono, fontSize: 11, lineHeight: 1.5 }}>
              <span style={{ color: C.textMuted, minWidth: 130, flexShrink: 0 }}>{humanizeAuditKey(k)}</span>
              <span style={{ color: C.text, wordBreak: "break-word" }}>{formatAuditValue(v)}</span>
            </div>
          ))}
        </div>
      );
    }
  }
  return <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 10, opacity: 0.8, wordBreak: "break-all" }}>{json}</div>;
}

export function AuditTab({ eventId }: { eventId: number }) {
  const [auditLogs, setAuditLogs] = useState<AuditLogEntry[]>([]);
  const [auditLoading, setAuditLoading] = useState(false);
  const [auditError, setAuditError] = useState<string | null>(null);

  useEffect(() => {
    setAuditLoading(true);
    setAuditError(null);
    auditLogsApi.getForEvent(eventId)
      .then(res => setAuditLogs(res.data ?? []))
      .catch(err => setAuditError(err instanceof ApiError ? err.message : "Failed to load audit log."))
      .finally(() => setAuditLoading(false));
  }, [eventId]);

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
      {auditLoading && <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>Loading...</div>}
      {auditError && <div style={{ color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 11 }}>{auditError}</div>}
      {!auditLoading && !auditError && auditLogs.length === 0 && (
        <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>No audit entries for this event yet.</div>
      )}
      {auditLogs.map(log => {
        const hasDetail = Boolean(log.reason || log.metadataJson);
        return (
        <div key={log.logId} style={{ padding: 12, background: C.surface2, border: `1px solid ${C.border}` }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 10, minWidth: 0 }}>
              <PixelBadge color="cyan">{log.action}</PixelBadge>
              <span style={{ color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>
                {log.actorName ?? `User#${log.actorUserId}`}
                {log.targetType && (
                  <span style={{ color: C.textMuted }}> · {log.targetType}{log.targetId != null ? `#${log.targetId}` : ""}</span>
                )}
              </span>
            </div>
            <span style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, flexShrink: 0 }}>
              {fmtDT(log.createdAt)}
            </span>
          </div>
          {hasDetail && (
            <div style={{ marginTop: 8, paddingTop: 8, borderTop: `1px solid ${C.border}`, display: "flex", flexDirection: "column", gap: 6 }}>
              {log.reason && (
                <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, fontStyle: "italic" }}>"{log.reason}"</div>
              )}
              {log.metadataJson && <AuditMetadata json={log.metadataJson} />}
            </div>
          )}
        </div>
        );
      })}
    </div>
  );
}
