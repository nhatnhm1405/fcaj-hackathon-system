import { useEffect, useState } from "react";
import {
  C, PixelBadge, PixelCard,
} from "@/shared/components/PixelComponents";
import { PixelMenu } from "@/shared/components/PixelMenu";
import {
  ApiError,
  apiErrorMessage,
  TeamRejoinRequest,
  teamRejoinRequestsApi,
} from "@/shared/apiClient";
import { useNotifications } from "@/app/providers/NotificationProvider";

function fmtDate(iso?: string) {
  if (!iso) return "-";
  return new Date(iso).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}

function statusBadgeColor(status: TeamRejoinRequest["status"]): "green" | "red" | "yellow" {
  if (status === "APPROVED") return "green";
  if (status === "REJECTED") return "red";
  return "yellow";
}

/**
 * Team rejoin requests, embedded as a tab inside the Coordinator Accounts page.
 * A dormant team's leader requests to re-attach the team to a new season;
 * approving creates a new TeamEventEntry and reactivates the team.
 *
 * Unlike the account-approval queues elsewhere on this page, a resolved row
 * here stays put with a status badge instead of disappearing — the team
 * identity keeps mattering for reference even after the request is settled.
 */
export function TeamRejoinRequestsPanel() {
  const { addToast } = useNotifications();
  const [requests, setRequests] = useState<TeamRejoinRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [workingId, setWorkingId] = useState<number | null>(null);
  const [search, setSearch] = useState("");

  function load() {
    setLoading(true);
    setError(null);
    teamRejoinRequestsApi.getPending()
      .then(res => {
        // Newest request first — same "stack" ordering as the other approval queues.
        const list = (res.data ?? []).slice().sort((a, b) =>
          new Date(b.requestedAt ?? 0).getTime() - new Date(a.requestedAt ?? 0).getTime());
        setRequests(list);
      })
      .catch(err => setError(err instanceof ApiError ? err.message : "Failed to load team rejoin requests."))
      .finally(() => setLoading(false));
  }

  useEffect(() => { load(); }, []);

  async function resolveRequest(request: TeamRejoinRequest, approve: boolean) {
    setError(null);
    setWorkingId(request.requestId);
    try {
      const resolved = approve
        ? (await teamRejoinRequestsApi.approve(request.requestId)).data
        : (await teamRejoinRequestsApi.reject(request.requestId)).data;
      // Patch in place — the pending-only GET would drop this row entirely,
      // but here we want the resolved status to stay visible, not vanish.
      setRequests(prev => prev.map(r => r.requestId === request.requestId ? resolved : r));
      addToast({
        type: approve ? "success" : "info",
        title: approve ? "TEAM REJOINED" : "REQUEST REJECTED",
        message: `${request.teamName} ${approve ? `can now compete in ${request.eventName}.` : "stays out of this season."}`,
      });
    } catch (err) {
      const message = apiErrorMessage(err, "Action failed.");
      setError(message);
      addToast({ type: "warning", title: "ACTION FAILED", message });
    } finally {
      setWorkingId(null);
    }
  }

  const query = search.trim().toLowerCase();
  const rows = query
    ? requests.filter(r => r.teamName.toLowerCase().includes(query) || r.requestedByName.toLowerCase().includes(query))
    : requests;
  const pendingCount = requests.filter(r => r.status === "PENDING").length;

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 16, alignItems: "center", justifyContent: "space-between" }}>
        <PixelBadge color="cyan">{pendingCount} PENDING</PixelBadge>
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search by team or leader..."
          style={{ width: 240, padding: "8px 12px", background: C.surface2, border: `1px solid ${C.border}`, color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, outline: "none", borderRadius: 0 }}
        />
      </div>

      <PixelCard glow glowColor="cyan" style={{ padding: 0, overflow: "hidden" }}>
        {error && (
          <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, padding: "10px 14px", margin: 16 }}>
            ERROR: {error}
          </div>
        )}

        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontFamily: "'JetBrains Mono', monospace" }}>
            <thead>
              <tr style={{ background: C.surface2, borderBottom: `1px solid ${C.border}` }}>
                {["Team", "Requested By", "Target Event", "Requested", "Status"].map(h => (
                  <th key={h} style={{ color: C.green, fontSize: 10, letterSpacing: "0.12em", textAlign: "left", padding: "12px 14px", fontWeight: 600, textTransform: "uppercase" }}>
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr><td colSpan={5} style={{ padding: 18, color: C.textMuted, fontSize: 12, textAlign: "center" }}>Loading...</td></tr>
              )}
              {!loading && !error && rows.length === 0 && (
                <tr><td colSpan={5} style={{ padding: 18, color: C.textMuted, fontSize: 12, textAlign: "center" }}>{requests.length === 0 ? "No pending team rejoin requests" : "No matches."}</td></tr>
              )}
              {!loading && rows.map((r, i) => (
                <tr key={r.requestId} className="row-actionable" style={{ borderBottom: `1px solid rgba(34,197,94,0.06)`, background: i % 2 === 0 ? C.surface : C.surface2 }}>
                  <td style={{ color: C.text, fontSize: 12, padding: "12px 14px" }}>{r.teamName}</td>
                  <td style={{ color: C.textMuted, fontSize: 11, padding: "12px 14px" }}>{r.requestedByName}</td>
                  <td style={{ padding: "12px 14px" }}><PixelBadge color="blue">{r.eventName}</PixelBadge></td>
                  <td style={{ color: C.textMuted, fontSize: 11, padding: "12px 14px" }}>{fmtDate(r.requestedAt)}</td>
                  <td onClick={(e) => e.stopPropagation()} style={{ padding: "12px 14px", width: 48 }}>
                    {r.status === "PENDING" ? (
                      <span className="row-action">
                        <PixelMenu
                          ariaLabel={`Actions for ${r.teamName}`}
                          disabled={workingId === r.requestId}
                          items={[
                            { label: "Approve", onClick: () => resolveRequest(r, true) },
                            "divider",
                            { label: "Reject", danger: true, onClick: () => resolveRequest(r, false) },
                          ]}
                        />
                      </span>
                    ) : (
                      <PixelBadge color={statusBadgeColor(r.status)}>{r.status}</PixelBadge>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </PixelCard>
    </div>
  );
}
