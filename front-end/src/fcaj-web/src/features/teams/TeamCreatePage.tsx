import { useState, useEffect } from "react";
import { useNavigate } from "react-router";
import {
  C, GradientText, PixelCard, PixelButton, PixelInput,
} from "@/shared/components/PixelComponents";
import { teamsApi, ApiError, apiErrorMessage, ActiveEventWithTracks } from "@/shared/apiClient";
import { useAuth } from "@/app/providers/AuthProvider";
import { useNotifications } from "@/app/providers/NotificationProvider";

export function TeamCreatePage() {
  const navigate = useNavigate();
  const { currentUser, refreshTeamContext } = useAuth();
  const { addToast } = useNotifications();

  const [activeEvents, setActiveEvents] = useState<ActiveEventWithTracks[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [teamName, setTeamName] = useState("");
  const [description, setDescription] = useState("");
  const [eventId, setEventId] = useState<number>(0);

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState(false);

  useEffect(() => {
    teamsApi.getActiveEvents()
      .then(res => {
        const evs = res.data ?? [];
        setActiveEvents(evs);
        if (evs.length === 1) setEventId(evs[0].eventId);
      })
      .catch(err => setLoadError(err instanceof ApiError ? err.message : "Failed to load open events."));
  }, []);

  // Already in a team → can't create another.
  if (currentUser && currentUser.team_id !== null) {
    return (
      <div style={{ padding: 24, display: "flex", justifyContent: "center" }}>
        <PixelCard style={{ padding: 32, maxWidth: 480, width: "100%", textAlign: "center" }}>
          <p style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, marginBottom: 20 }}>
            You are already in a team for this event.
          </p>
          <PixelButton variant="cyber" onClick={() => navigate('/team/view')}>VIEW MY TEAM</PixelButton>
        </PixelCard>
      </div>
    );
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!teamName.trim() || !eventId) {
      setError("Team name and event are required.");
      addToast({ type: "warning", title: "Missing fields", message: "Team name and event are required." });
      return;
    }
    setSubmitting(true);
    try {
      await teamsApi.create({
        eventId,
        name: teamName.trim(),
        description: description.trim() || undefined,
      });
      await refreshTeamContext();
      setCreated(true);
      addToast({ type: "success", title: "Team created", message: `"${teamName.trim()}" is pending coordinator approval.` });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create team.");
      addToast({ type: "warning", title: "Create failed", message: apiErrorMessage(err, "Failed to create team.") });
    } finally {
      setSubmitting(false);
    }
  }

  if (created) {
    return (
      <div style={{ padding: 24, display: "flex", justifyContent: "center" }}>
        <PixelCard glow gradient style={{ padding: 32, maxWidth: 480, width: "100%", textAlign: "center" }}>
          <h2 style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 24, fontWeight: 800, lineHeight: 1.2 }}>
            <GradientText>Team Created!</GradientText>
          </h2>
          <p style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, marginTop: 12, marginBottom: 24 }}>
            You are now the Team Leader. Your team is awaiting coordinator approval — invite members from the manage page.
          </p>
          <div style={{ display: "flex", gap: 10, justifyContent: "center" }}>
            <PixelButton variant="cyber" onClick={() => navigate('/team/view')}>MANAGE TEAM</PixelButton>
            <PixelButton variant="secondary" onClick={() => navigate('/dashboard')}>DASHBOARD</PixelButton>
          </div>
        </PixelCard>
      </div>
    );
  }

  const selectStyle: React.CSSProperties = {
    background: C.surface2, border: `1px solid ${C.border}`, color: C.text,
    padding: "10px 12px", fontFamily: "'JetBrains Mono', monospace", fontSize: 13, borderRadius: 0, outline: "none",
  };
  const labelStyle: React.CSSProperties = {
    color: C.greenMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, letterSpacing: "0.1em", textTransform: "uppercase",
  };

  return (
    <div style={{ padding: 24, display: "flex", justifyContent: "center" }}>
      <div style={{ width: "100%", maxWidth: 560 }}>
        <div style={{ marginBottom: 20 }}>
          <h1 style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 28, fontWeight: 800 }}>
            <GradientText>Create Team</GradientText>
          </h1>
        </div>

        {loadError && (
          <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, padding: "10px 14px", marginBottom: 16 }}>
            ERROR: {loadError}
          </div>
        )}

        {activeEvents.length === 0 && !loadError ? (
          <PixelCard style={{ padding: 24 }}>
            <p style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 13 }}>
              No events are open for registration right now.
            </p>
          </PixelCard>
        ) : (
          <PixelCard glow style={{ padding: 24 }}>
            <form onSubmit={handleCreate} style={{ display: "flex", flexDirection: "column", gap: 18 }}>
              <PixelInput label="Team Name" placeholder="e.g. ByteBuilders" value={teamName} onChange={(e) => setTeamName(e.target.value)} />

              <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                <label style={labelStyle}>Event</label>
                <select value={eventId} onChange={(e) => setEventId(Number(e.target.value))} style={selectStyle}>
                  <option value={0}>Select event...</option>
                  {activeEvents.map(ev => <option key={ev.eventId} value={ev.eventId}>{ev.name}</option>)}
                </select>
              </div>

              <div style={{ background: "rgba(56,189,248,0.06)", border: "1px solid rgba(56,189,248,0.30)", color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, padding: "10px 12px", lineHeight: 1.5 }}>
                Your track is assigned after registration closes — either you pick it
                during the setup phase, or the coordinator draws it randomly, depending
                on the event.
              </div>

              <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                <label style={labelStyle}>Description (optional)</label>
                <textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="What is your team about?"
                  style={{ ...selectStyle, minHeight: 70, resize: "vertical" }}
                />
              </div>

              {error && (
                <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, padding: "10px 12px" }}>
                  ERROR: {error}
                </div>
              )}

              <PixelButton type="submit" variant="cyber" size="lg" fullWidth disabled={submitting}>
                {submitting ? "CREATING…" : "CREATE TEAM"}
              </PixelButton>
            </form>
          </PixelCard>
        )}
      </div>
    </div>
  );
}
