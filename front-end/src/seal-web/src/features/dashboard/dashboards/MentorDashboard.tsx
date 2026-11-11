import { useState, useEffect, useMemo } from "react";
import { useNavigate } from "react-router";
import { useAuth } from "@/app/providers/AuthProvider";
import {
  C, GradientText, PixelCard, PixelButton, PixelBadge, CyberStatCard,
} from "@/shared/components/PixelComponents";
import { assignmentsApi, eventsApi, roundsApi, ApiError, MentorAssignedTeam, MentorAssignedTrack } from "@/shared/apiClient";
import { useRoundTimer } from "@/shared/hooks/useRoundTimer";
import { CountdownDisplay } from "@/shared/components/CountdownDisplay";

export function MentorDashboard() {
  const navigate = useNavigate();
  const { currentUser } = useAuth();

  const [allTeams, setAllTeams] = useState<MentorAssignedTeam[]>([]);
  const [allTracks, setAllTracks] = useState<MentorAssignedTrack[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    assignmentsApi.getMentorAssignments()
      .then(res => {
        setAllTeams(res.data?.teams ?? []);
        setAllTracks(res.data?.tracks ?? []);
      })
      .catch(err => setError(err instanceof ApiError ? err.message : "Failed to load mentor assignments."))
      .finally(() => setLoading(false));
  }, []);

  // Assigned tracks including empty ones; fall back to synthesising from teams
  // if an older backend omits the `tracks` field.
  const assignedTracks = useMemo<MentorAssignedTrack[]>(() => {
    if (allTracks.length > 0) return allTracks;
    const map = new Map<number, MentorAssignedTrack>();
    allTeams.forEach(t => {
      if (!map.has(t.trackId)) map.set(t.trackId, { trackId: t.trackId, trackName: t.trackName, eventId: t.eventId, eventName: t.eventName, season: t.season, year: t.year, eventStatus: t.eventStatus });
    });
    return [...map.values()];
  }, [allTracks, allTeams]);

  // A mentor may be assigned across several seasons; scope the dashboard to the
  // event they are most likely working on now (IN_PROGRESS → OPEN → newest).
  const activeEvent = useMemo(() => {
    const map = new Map<number, { eventId: number; eventName: string; year?: number; eventStatus?: string }>();
    assignedTracks.forEach(tr => {
      if (!map.has(tr.eventId)) map.set(tr.eventId, { eventId: tr.eventId, eventName: tr.eventName, year: tr.year, eventStatus: tr.eventStatus });
    });
    const events = [...map.values()].sort((a, b) => (b.year ?? 0) - (a.year ?? 0) || b.eventId - a.eventId);
    if (events.length === 0) return null;
    const up = (s?: string) => (s ?? "").toUpperCase();
    return events.find(e => up(e.eventStatus) === "IN_PROGRESS")
      ?? events.find(e => up(e.eventStatus) === "OPEN")
      ?? events[0];
  }, [assignedTracks]);

  const eventName = activeEvent?.eventName ?? "";
  const teams = useMemo(
    () => (activeEvent ? allTeams.filter(t => t.eventId === activeEvent.eventId) : []),
    [allTeams, activeEvent],
  );

  if (!currentUser) return null;

  // The active event's assigned tracks (incl. empty ones), each with its teams.
  const eventTracks = activeEvent ? assignedTracks.filter(tr => tr.eventId === activeEvent.eventId) : [];
  const byTrack = new Map<string, MentorAssignedTeam[]>();
  eventTracks.forEach(tr => byTrack.set(tr.trackName, teams.filter(t => t.trackId === tr.trackId)));

  // First card surfaces WHAT the mentor manages: the current event and the
  // track(s), both shown as clearly-labelled fields (see MentorContextCard).
  const trackNames = eventTracks.map(tr => tr.trackName);

  return (
    <div style={{ padding: 24, display: "flex", flexDirection: "column", gap: 20 }}>
      <PixelCard glow gradient style={{ padding: 24 }}>
        <div style={{ color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 15, fontWeight: 700, marginBottom: 6 }}>
          Mentor Console
        </div>
        <h1 style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 26, fontWeight: 800, lineHeight: 1.2 }}>
          <GradientText>Good day, {currentUser.full_name}</GradientText>
        </h1>
        <p style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, marginTop: 6 }}>
          {eventName ? <>Supporting teams in <span style={{ color: C.text }}>{eventName}</span>.</> : "Guide and support teams across your assigned tracks."}
        </p>
      </PixelCard>

      <MentorContestTimer eventName={eventName} />

      {error && (
        <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, padding: "10px 14px" }}>
          ERROR: {error}
        </div>
      )}

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: 16 }}>
        <MentorContextCard eventName={eventName} trackNames={trackNames} />
        <CyberStatCard value={teams.length} label="Teams" accent="blue" />
        <CyberStatCard
          value={`${teams.filter(t => t.submissionCount > 0).length}/${teams.length}`}
          label="Teams Submitted"
          accent="cyan"
        />
      </div>

      <PixelCard style={{ padding: 20 }}>
        <div style={{ color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 15, fontWeight: 700, marginBottom: 12 }}>
          My Tracks
        </div>
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {loading && <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, padding: 8 }}>Loading...</div>}
          {!loading && byTrack.size === 0 && !error && (
            <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, padding: 16 }}>
              No tracks assigned yet.
            </div>
          )}
          {[...byTrack.entries()].map(([trackName, trackTeams]) => (
            <div key={trackName} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "12px 14px", background: C.surface2, border: `1px solid ${C.border}` }}>
              <div style={{ color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, fontWeight: 600 }}>{trackName}</div>
              <PixelBadge color="blue">{trackTeams.length} TEAMS</PixelBadge>
            </div>
          ))}
        </div>
        {byTrack.size > 0 && (
          <div style={{ marginTop: 16 }}>
            <PixelButton variant="cyber" onClick={() => navigate('/mentor/tracks')}>VIEW MY TRACKS</PixelButton>
          </div>
        )}
      </PixelCard>
    </div>
  );
}

// Context card giving EVENT and TRACK(s) equal billing — same cyber frame as
// CyberStatCard, but two clearly-labelled fields instead of one big number.
function MentorContextCard({ eventName, trackNames }: { eventName: string; trackNames: string[] }) {
  const mono = "'JetBrains Mono', monospace";
  const trackText = trackNames.length === 0 ? "—" : trackNames.join(", ");
  const EVENT_COLOR = "#a78bfa";  // purple — the card's own accent
  const TRACK_COLOR = C.blueBright; // blue — distinct from event, still cool-toned
  const field = (fieldLabel: string, fieldValue: string, valueColor: string, dotGlow: string) => (
    <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
      <span style={{ display: "flex", alignItems: "center", gap: 7, color: C.textMuted, fontFamily: mono, fontSize: 10, fontWeight: 600, letterSpacing: "0.12em" }}>
        <span style={{ width: 6, height: 6, borderRadius: "50%", background: valueColor, boxShadow: `0 0 8px ${dotGlow}`, flexShrink: 0 }} />
        {fieldLabel}
      </span>
      <span style={{ color: valueColor, fontFamily: mono, fontSize: 17, fontWeight: 800, lineHeight: 1.2, textShadow: `0 0 20px ${dotGlow}`, wordBreak: "break-word" }}>{fieldValue}</span>
    </div>
  );
  return (
    <div style={{ background: C.surface, border: `1px solid ${C.purple}22`, borderRadius: 0, padding: 20, position: "relative", overflow: "hidden", boxShadow: `0 0 20px rgba(139,92,246,0.08), inset 0 0 30px rgba(139,92,246,0.06)` }}>
      <div style={{ position: "absolute", bottom: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${C.purple}, ${C.blueBright}, transparent)`, opacity: 0.65 }} />
      <div style={{ position: "absolute", bottom: -20, right: -20, width: 80, height: 80, borderRadius: "50%", background: `radial-gradient(circle, ${C.purpleGlow} 0%, transparent 70%)`, pointerEvents: "none" }} />
      <div style={{ position: "relative", zIndex: 1, display: "flex", flexDirection: "column", gap: 14 }}>
        {field("EVENT", eventName || "—", EVENT_COLOR, C.purpleGlow)}
        {field(trackNames.length > 1 ? "TRACKS" : "TRACK", trackText, TRACK_COLOR, C.blueGlow)}
      </div>
      <div style={{ position: "absolute", top: 0, left: 0, width: 12, height: 12, borderTop: `2px solid ${C.purple}`, borderLeft: `2px solid ${C.purple}`, opacity: 0.8 }} />
      <div style={{ position: "absolute", top: 0, right: 0, width: 12, height: 12, borderTop: `2px solid ${C.purple}`, borderRight: `2px solid ${C.purple}`, opacity: 0.4 }} />
    </div>
  );
}

// Read-only CONTEST countdown for a mentor: resolves their event (by name, else
// the IN_PROGRESS one) → its ACTIVE round → the live timer. Mentors aren't in the
// server fan-out, so they get milestone banners but no bell entries. Renders
// nothing until a timer is actually configured for the active round.
function MentorContestTimer({ eventName }: { eventName: string }) {
  const [eventId, setEventId] = useState<number | null>(null);
  const [roundId, setRoundId] = useState<number | null>(null);

  useEffect(() => {
    if (!eventName) { setEventId(null); setRoundId(null); return; }
    let active = true;
    (async () => {
      try {
        const events = (await eventsApi.getAll()).data ?? [];
        const ev = events.find(e => e.name === eventName)
          ?? events.find(e => e.status === "IN_PROGRESS")
          ?? null;
        if (!ev) { if (active) { setEventId(null); setRoundId(null); } return; }
        const rs = (await roundsApi.getAll(ev.eventId)).data ?? [];
        const activeRound = rs.find(r => (r.status ?? "").toUpperCase() === "ACTIVE")
          ?? [...rs].sort((a, b) => (a.orderNumber ?? 0) - (b.orderNumber ?? 0))[0]
          ?? null;
        if (active) { setEventId(ev.eventId); setRoundId(activeRound?.roundId ?? null); }
      } catch {
        if (active) { setEventId(null); setRoundId(null); }
      }
    })();
    return () => { active = false; };
  }, [eventName]);

  const timer = useRoundTimer(eventId, roundId, "CONTEST", { fireBanners: true });
  if (!timer.isConfigured) return null;

  return (
    <PixelCard glow glowColor="cyan" style={{ padding: 20 }}>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
        <div>
          <div style={{ color: C.cyan, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, fontWeight: 700 }}>Contest time remaining</div>
          <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, marginTop: 4 }}>
            Time left for your teams to submit in the current round.
          </div>
        </div>
        <CountdownDisplay remainingSeconds={timer.remainingSeconds} status={timer.status} icon />
      </div>
    </PixelCard>
  );
}
