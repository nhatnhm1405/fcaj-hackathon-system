import { useEffect, useState } from "react";
import {
  C, PixelBadge, PixelButton, PixelCard,
} from "@/shared/components/PixelComponents";
import {
  ApiError,
  apiErrorMessage,
  ParticipationAccessRequest,
  participationRequestsApi,
} from "@/shared/apiClient";
import { useNotifications } from "@/app/providers/NotificationProvider";

function fmtDate(iso?: string) {
  if (!iso) return "-";
  return new Date(iso).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}

function userTypeBadge(userType: string) {
  if (userType === "FPT_STUDENT") return <PixelBadge color="green">FPT</PixelBadge>;
  if (userType === "EXTERNAL_STUDENT") return <PixelBadge color="cyan">EXTERNAL</PixelBadge>;
  if (userType === "STAFF") return <PixelBadge color="blue">STAFF</PixelBadge>;
  return <PixelBadge color="gray">{userType}</PixelBadge>;
}

/**
 * Participation (reactivation) requests, embedded as a tab inside the Coordinator
 * Accounts page. Inactive participants request to rejoin; approving reactivates them.
 */
export function ParticipationRequestsPanel() {
  const { addToast } = useNotifications();
  const [requests, setRequests] = useState<ParticipationAccessRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [workingId, setWorkingId] = useState<number | null>(null);
  const [search, setSearch] = useState("");

  function load() {
    setLoading(true);
    setError(null);
    participationRequestsApi.getPending()
      .then(res => {
        // Newest request first — same "stack" ordering as the Approvals queue.
        const list = (res.data ?? []).slice().sort((a, b) =>
          new Date(b.requestedAt ?? 0).getTime() - new Date(a.requestedAt ?? 0).getTime());
        setRequests(list);
      })
      .catch(err => setError(err instanceof ApiError ? err.message : "Failed to load participation requests."))
      .finally(() => setLoading(false));
  }

  useEffect(() => { load(); }, []);

  async function resolveRequest(request: ParticipationAccessRequest, approve: boolean) {
    setError(null);
    setWorkingId(request.requestId);
    try {
      if (approve) {
        await participationRequestsApi.approve(request.requestId);
      } else {
        await participationRequestsApi.reject(request.requestId);
      }
      setRequests(prev => prev.filter(r => r.requestId !== request.requestId));
      addToast({
        type: approve ? "success" : "info",
        title: approve ? "PARTICIPANT ACTIVATED" : "REQUEST REJECTED",
        message: `${request.fullName} (${request.email}) ${approve ? "can compete this season." : "stays inactive."}`,
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
    ? requests.filter(r => r.fullName.toLowerCase().includes(query) || r.email.toLowerCase().includes(query))
    : requests;

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 16, alignItems: "center", justifyContent: "space-between" }}>
        <PixelBadge color="cyan">{requests.length} PENDING</PixelBadge>
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search by name or email..."
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
                {["Full Name", "Email", "Type", "Requested", "Actions"].map(h => (
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
                <tr><td colSpan={5} style={{ padding: 18, color: C.textMuted, fontSize: 12, textAlign: "center" }}>{requests.length === 0 ? "No pending participation requests" : "No matches."}</td></tr>
              )}
              {!loading && rows.map((r, i) => (
                <tr key={r.requestId} style={{ borderBottom: `1px solid rgba(34,197,94,0.06)`, background: i % 2 === 0 ? C.surface : C.surface2 }}>
                  <td style={{ color: C.text, fontSize: 12, padding: "12px 14px" }}>{r.fullName}</td>
                  <td style={{ color: C.textMuted, fontSize: 11, padding: "12px 14px" }}>{r.email}</td>
                  <td style={{ padding: "12px 14px" }}>{userTypeBadge(r.userType)}</td>
                  <td style={{ color: C.textMuted, fontSize: 11, padding: "12px 14px" }}>{fmtDate(r.requestedAt)}</td>
                  <td style={{ padding: "12px 14px" }}>
                    {/* Reject on the left, Approve on the right — keeps the
                        destructive choice away from where a quick, confident
                        approve click naturally lands. */}
                    <div style={{ display: "flex", gap: 6 }}>
                      <PixelButton size="sm" variant="danger" disabled={workingId === r.requestId} onClick={() => resolveRequest(r, false)}>REJECT</PixelButton>
                      <PixelButton size="sm" variant="cyber" disabled={workingId === r.requestId} onClick={() => resolveRequest(r, true)}>APPROVE</PixelButton>
                    </div>
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
