import { useEffect, useState } from "react";
import { C, PixelButton, PixelBadge } from "@/shared/components/PixelComponents";
import { problemsApi, apiErrorMessage, type TrackProblem } from "@/shared/apiClient";
import { useNotifications } from "@/app/providers/NotificationProvider";

// Participant-facing problem resource: shows the released problem of the team's track
// with a download button. Embedded in Team Info when the team is APPROVED and has a track;
// while the problem is unreleased the backend reports hasProblem = false, so we show
// a muted "not released yet" note instead of a download.
const MONO = "'JetBrains Mono', monospace";

function formatSize(bytes?: number | null): string {
  if (!bytes || bytes <= 0) return "";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function ParticipantProblemCard({ eventId, trackId }: { eventId: number; trackId: number }) {
  const { addToast } = useNotifications();
  const [problem, setProblem] = useState<TrackProblem | null>(null);
  const [loading, setLoading] = useState(true);
  const [downloading, setDownloading] = useState(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    problemsApi.get(eventId, trackId)
      .then(res => { if (active) setProblem(res.data); })
      .catch(() => { if (active) setProblem(null); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [eventId, trackId]);

  async function onDownload() {
    setDownloading(true);
    try {
      await problemsApi.download(eventId, trackId, problem?.fileName ?? 'problem');
    } catch (err) {
      addToast({ type: 'warning', title: 'DOWNLOAD FAILED', message: apiErrorMessage(err, 'Failed to download the problem.') });
    } finally {
      setDownloading(false);
    }
  }

  async function onView() {
    try {
      await problemsApi.view(eventId, trackId);
    } catch (err) {
      addToast({ type: 'warning', title: 'CANNOT OPEN', message: apiErrorMessage(err, 'Failed to open the problem file.') });
    }
  }

  const released = problem?.hasProblem && problem?.released;

  return (
    <div style={{
      marginTop: 18,
      padding: "16px 18px",
      background: "rgba(6,182,212,0.045)",
      border: `1px solid ${C.border}`,
      borderLeft: `3px solid ${C.cyan}`,
    }}>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
        <div style={{ minWidth: 0 }}>
          <div style={{ color: C.green, fontFamily: MONO, fontSize: 15, fontWeight: 700, marginBottom: 6 }}>Problem Statement</div>
          {loading ? (
            <div style={{ color: C.textMuted, fontFamily: MONO, fontSize: 12 }}>Loading…</div>
          ) : released ? (
            <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
              <button
                type="button"
                onClick={onView}
                title="Open to view"
                style={{
                  background: "none", border: "none", padding: 0, cursor: "pointer",
                  color: C.cyanBright, fontFamily: MONO, fontSize: 13, textDecoration: "underline",
                  overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", maxWidth: 320,
                }}
              >
                {problem?.fileName}
              </button>
              {problem?.fileSize ? (
                <span style={{ color: C.textDim, fontFamily: MONO, fontSize: 11 }}>{formatSize(problem.fileSize)}</span>
              ) : null}
              <PixelBadge color="green">RELEASED</PixelBadge>
            </div>
          ) : (
            <div style={{ color: C.textMuted, fontFamily: MONO, fontSize: 12 }}>
              Your track's problem hasn't been released yet. It will appear here once the organizers publish it.
            </div>
          )}
        </div>
        {released && (
          <PixelButton variant="cyber" onClick={onDownload} disabled={downloading}>
            {downloading ? "DOWNLOADING…" : "DOWNLOAD"}
          </PixelButton>
        )}
      </div>
    </div>
  );
}
