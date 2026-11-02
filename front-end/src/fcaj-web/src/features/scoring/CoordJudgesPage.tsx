import { useState, useEffect, useCallback, useMemo } from "react";
import { createPortal } from "react-dom";
import {
  C, GradientText, PixelBadge,
} from "@/shared/components/PixelComponents";
import { ConfirmDialog } from "@/shared/components/ConfirmDialog";
import {
  eventsApi, roundsApi, tracksApi, teamsApi, coordinatorApi, ApiError, apiErrorMessage,
  HackathonEvent, Round, Track, UserItem, JudgeRosterItem, MentorRosterItem, Team,
} from "@/shared/apiClient";
import { useNotifications } from "@/app/providers/NotificationProvider";
import {
  mono, HEAD_BG, HEAD_BORDER, MENTOR_HEAD_BG, MENTOR_HEAD_BORDER, MENTOR_CELL_BG, MENTOR_TEXT,
  FINAL_BG, FINAL_BORDER, TRACK_COL, CELL_COL,
} from "./assignmentBoardStyles";

// Events already over don't belong here — they move to the read-only
// "Assignment History" tab. This page only ever works the one event still
// being configured/run (the "one active event" model).
const ENDED_STATUSES = new Set(["COMPLETED", "CANCELLED"]);
const ASSIGNMENT_MUTABLE_STATUSES = new Set(["SETUP", "IN_PROGRESS"]);

/** The event currently being configured, if any: furthest along its lifecycle wins. */
function pickCurrentEvent(events: HackathonEvent[]): HackathonEvent | null {
  const live = events.filter(e => !ENDED_STATUSES.has((e.status ?? "").toUpperCase()));
  if (live.length === 0) return null;
  const priority = ["IN_PROGRESS", "SETUP", "OPEN", "DRAFT"];
  for (const status of priority) {
    const match = live.find(e => (e.status ?? "").toUpperCase() === status);
    if (match) return match;
  }
  return live[live.length - 1];
}

type Active =
  | { kind: 'mentor'; trackId: number }
  | { kind: 'judge'; roundId: number; trackId: number }
  | { kind: 'final'; roundId: number }
  | null;

export function CoordJudgesPage() {
  const { addToast, addActionNotification } = useNotifications();
  const [events, setEvents] = useState<HackathonEvent[]>([]);
  const currentEvent = useMemo(() => pickCurrentEvent(events), [events]);
  const selectedEventId = currentEvent?.eventId ?? null;
  const assignmentMutable = ASSIGNMENT_MUTABLE_STATUSES.has((currentEvent?.status ?? "").toUpperCase());
  const [rounds, setRounds] = useState<Round[]>([]);
  const [tracks, setTracks] = useState<Track[]>([]);
  const [teams, setTeams] = useState<Team[]>([]);
  const [staff, setStaff] = useState<UserItem[]>([]);
  const [judges, setJudges] = useState<JudgeRosterItem[]>([]);
  const [mentors, setMentors] = useState<MentorRosterItem[]>([]);

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [active, setActive] = useState<Active>(null);
  const [pickerAnchor, setPickerAnchor] = useState<DOMRect | null>(null);
  const [query, setQuery] = useState("");
  const [busy, setBusy] = useState(false);
  // Unassign queued behind a confirm — the ✕ chips are small and easy to mis-hit.
  const [confirmRemove, setConfirmRemove] = useState<null | { kind: 'mentor' | 'judge'; id: number; name: string }>(null);
  const [removeBusy, setRemoveBusy] = useState(false);
  // Mentor/judge role-overlap and guest/non-final conflicts are soft warnings,
  // not hard blocks (a small event may genuinely need to double someone up) —
  // queued behind a confirm so a coordinator can't add them by mistake.
  const [pendingAdd, setPendingAdd] = useState<null | { userId: number; name: string; warning: string }>(null);
  const [replaceTarget, setReplaceTarget] = useState<JudgeRosterItem | null>(null);
  const [replacementJudgeId, setReplacementJudgeId] = useState<number | null>(null);
  const [replacementReason, setReplacementReason] = useState("");
  const [replaceBusy, setReplaceBusy] = useState(false);
  const [replaceError, setReplaceError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    Promise.all([
      eventsApi.getAll().then(r => r.data ?? []),
      coordinatorApi.getStaff().then(r => r.data ?? []).catch(() => [] as UserItem[]),
    ])
      .then(([evs, st]) => { setEvents(evs); setStaff(st); })
      .catch(err => setLoadError(err instanceof ApiError ? err.message : "Failed to load events."))
      .finally(() => setLoading(false));
  }, []);

  const reloadJudges = useCallback((eventId: number) =>
    coordinatorApi.getJudgeRoster(eventId).then(r => setJudges(r.data ?? [])).catch(() => setJudges([])), []);
  const reloadMentors = useCallback((eventId: number) =>
    coordinatorApi.getMentorRoster(eventId).then(r => setMentors(r.data ?? [])).catch(() => setMentors([])), []);

  useEffect(() => {
    if (selectedEventId == null) { setRounds([]); setTracks([]); setTeams([]); setJudges([]); setMentors([]); return; }
    setActive(null); setPickerAnchor(null);
    roundsApi.getAll(selectedEventId).then(r => setRounds(r.data ?? [])).catch(() => setRounds([]));
    tracksApi.getAll(selectedEventId).then(r => setTracks(r.data ?? [])).catch(() => setTracks([]));
    teamsApi.getByEvent(selectedEventId).then(r => setTeams(r.data ?? [])).catch(() => setTeams([]));
    reloadJudges(selectedEventId);
    reloadMentors(selectedEventId);
  }, [selectedEventId, reloadJudges, reloadMentors]);

  const prelimRounds = [...rounds].filter(r => !r.isFinal).sort((a, b) => (a.orderNumber ?? a.roundId) - (b.orderNumber ?? b.roundId));
  const finalRounds = [...rounds].filter(r => r.isFinal).sort((a, b) => (a.orderNumber ?? a.roundId) - (b.orderNumber ?? b.roundId));

  const mentorsOf = (trackId: number) => mentors.filter(m => m.trackId === trackId);
  const judgesOf = (roundId: number, trackId: number | null) =>
    judges.filter(j => j.roundId === roundId && (trackId == null ? j.trackId == null : j.trackId === trackId));
  const teamCountOf = (trackId: number) => teams.filter(t => t.trackId === trackId).length;

  function assignedUserIds(): Set<number> {
    if (!active) return new Set();
    if (active.kind === 'mentor') return new Set(mentorsOf(active.trackId).map(m => m.mentorUserId));
    if (active.kind === 'judge') return new Set(judgesOf(active.roundId, active.trackId).map(j => j.judgeUserId));
    return new Set(judgesOf(active.roundId, null).map(j => j.judgeUserId));
  }

  // Soft conflict-of-interest checks for the currently-open picker. Both are
  // warnings, not blocks — and neither applies to the Final round: it pools
  // every track's finalists together, so a track's own mentor isn't scoring
  // their mentee team in isolation there, and guest judges are expected at
  // Final (that's their whole reason for being on the roster).
  function conflictWarning(userId: number): string | null {
    if (!active) return null;
    if (active.kind === 'judge') {
      const reasons: string[] = [];
      if (mentorsOf(active.trackId).some(m => m.mentorUserId === userId)) {
        reasons.push("Already mentors this track — scoring their own mentee team.");
      }
      if (staff.find(u => u.userId === userId)?.judgeType === 'GUEST') {
        reasons.push("Guest judge — normally only assigned to the Final round.");
      }
      return reasons.length > 0 ? reasons.join(" ") : null;
    }
    if (active.kind === 'mentor') {
      const alreadyJudgesTrack = prelimRounds.some(r => judgesOf(r.roundId, active.trackId).some(j => j.judgeUserId === userId));
      return alreadyJudgesTrack ? "Already judges this track in a preliminary round — scoring their own mentee team." : null;
    }
    return null; // 'final' — no conflict checks
  }

  async function add(userId: number) {
    if (!active || selectedEventId == null) return;
    setBusy(true);
    try {
      if (active.kind === 'mentor') { await coordinatorApi.assignMentor({ mentorUserId: userId, trackId: active.trackId }); await reloadMentors(selectedEventId); addToast({ type: 'success', title: 'MENTOR ASSIGNED', message: 'Mentor added to the track.' }); }
      else if (active.kind === 'judge') { await coordinatorApi.assignJudge({ judgeUserId: userId, roundId: active.roundId, trackId: active.trackId }); await reloadJudges(selectedEventId); addToast({ type: 'success', title: 'JUDGE ASSIGNED', message: 'Judge added to this round & track.' }); }
      else { await coordinatorApi.assignJudge({ judgeUserId: userId, roundId: active.roundId, trackId: null }); await reloadJudges(selectedEventId); addToast({ type: 'success', title: 'JUDGE ASSIGNED', message: 'Judge added to the final round.' }); }
    } catch (err) {
      addActionNotification({ type: 'warning', title: 'ASSIGN FAILED', message: apiErrorMessage(err, 'Failed to assign.') });
    } finally { setBusy(false); }
  }

  async function removeMentor(id: number) {
    try { await coordinatorApi.removeMentorAssignment(id); setMentors(prev => prev.filter(m => m.id !== id)); addToast({ type: 'info', title: 'MENTOR REMOVED', message: 'Mentor unassigned from the track.' }); }
    catch (err) { addActionNotification({ type: 'warning', title: 'REMOVE FAILED', message: apiErrorMessage(err, 'Failed to remove.') }); }
  }
  async function removeJudge(id: number) {
    try { await coordinatorApi.removeJudgeAssignment(id); setJudges(prev => prev.filter(j => j.id !== id)); addToast({ type: 'info', title: 'JUDGE REMOVED', message: 'Judge unassigned.' }); }
    catch (err) { addActionNotification({ type: 'warning', title: 'REMOVE FAILED', message: apiErrorMessage(err, 'Failed to remove.') }); }
  }

  // The chip ✕ only queues the removal; the shared dialog below commits it.
  function requestRemoveMentor(id: number) {
    const m = mentors.find(x => x.id === id);
    setConfirmRemove({ kind: 'mentor', id, name: m?.mentorName ?? 'this mentor' });
  }
  function requestRemoveJudge(id: number) {
    const j = judges.find(x => x.id === id);
    setConfirmRemove({ kind: 'judge', id, name: j?.judgeName ?? 'this judge' });
  }
  async function runConfirmedRemove() {
    if (!confirmRemove) return;
    setRemoveBusy(true);
    try {
      if (confirmRemove.kind === 'mentor') await removeMentor(confirmRemove.id);
      else await removeJudge(confirmRemove.id);
    } finally {
      setRemoveBusy(false);
      setConfirmRemove(null);
    }
  }

  function requestReplaceJudge(assignment: JudgeRosterItem) {
    setReplaceTarget(assignment);
    setReplacementJudgeId(null);
    setReplacementReason("");
    setReplaceError(null);
  }

  async function runReplaceJudge() {
    if (!replaceTarget || selectedEventId == null) return;
    if (replacementJudgeId == null) { setReplaceError("Select a replacement judge."); return; }
    if (!replacementReason.trim()) { setReplaceError("A replacement reason is required for the audit log."); return; }
    setReplaceBusy(true); setReplaceError(null);
    try {
      await coordinatorApi.replaceJudgeAssignment(replaceTarget.id, {
        judgeUserId: replacementJudgeId,
        reason: replacementReason.trim(),
      });
      await reloadJudges(selectedEventId);
      addToast({ type: 'success', title: 'JUDGE REPLACED', message: `${replaceTarget.judgeName} was replaced with an audit record.` });
      setReplaceTarget(null);
    } catch (err) {
      setReplaceError(apiErrorMessage(err, 'Failed to replace judge.'));
      addActionNotification({ type: 'warning', title: 'REPLACE FAILED', message: apiErrorMessage(err, 'Failed to replace judge.') });
    } finally { setReplaceBusy(false); }
  }

  const addedIds = assignedUserIds();
  const staffPool = staff.filter(u => query === "" || u.fullName.toLowerCase().includes(query.toLowerCase()) || (u.email ?? "").toLowerCase().includes(query.toLowerCase()));

  function Chip({ name, judgeType, onRemove, onReplace }: { name: string; judgeType?: string; onRemove: () => void; onReplace?: () => void }) {
    return (
      <span style={{ display: "inline-flex", alignItems: "center", gap: 7, background: "rgba(34,197,94,0.14)", border: "1px solid rgba(34,197,94,0.45)", borderRadius: 4, color: C.text, fontFamily: mono, fontSize: 12.5, fontWeight: 600, padding: "5px 8px 5px 10px", maxWidth: "100%" }}>
        <span style={{ width: 6, height: 6, borderRadius: "50%", background: C.green, boxShadow: `0 0 6px ${C.green}`, flexShrink: 0 }} />
        <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{name}</span>
        {judgeType && <span style={{ color: judgeType === 'GUEST' ? C.cyan : C.blueBright, fontSize: 9, fontWeight: 800, letterSpacing: "0.06em" }}>{judgeType === 'GUEST' ? "GUEST" : "INT"}</span>}
        {onReplace && <button onClick={onReplace} title="Replace judge" style={{ background: "none", border: "none", color: C.cyan, cursor: "pointer", fontSize: 13, lineHeight: 1, padding: 0 }}>↻</button>}
        <button onClick={onRemove} title="Remove" style={{ background: "none", border: "none", color: C.textMuted, cursor: "pointer", fontSize: 13, lineHeight: 1, padding: 0 }}
          onMouseEnter={e => { (e.currentTarget as HTMLElement).style.color = C.red; }}
          onMouseLeave={e => { (e.currentTarget as HTMLElement).style.color = C.textMuted; }}>✕</button>
      </span>
    );
  }

  function AddButton({ onClick, isActive }: { onClick: (anchor: DOMRect) => void; isActive: boolean }) {
    return (
      <button
        onClick={e => { if (assignmentMutable) onClick(e.currentTarget.getBoundingClientRect()); }}
        disabled={!assignmentMutable}
        title={assignmentMutable ? "Add assignment" : "Assignments can only be changed in SETUP or IN_PROGRESS."}
        style={{ alignSelf: "flex-start", background: isActive ? "rgba(34,197,94,0.16)" : "transparent", border: `1px dashed ${isActive ? C.green : C.border}`, borderRadius: 4, color: isActive ? C.green : C.textMuted, fontFamily: mono, fontSize: 11, fontWeight: 600, letterSpacing: "0.06em", padding: "5px 11px", cursor: assignmentMutable ? "pointer" : "not-allowed", opacity: assignmentMutable ? 1 : 0.45, transition: "color .12s, border-color .12s" }}
        onMouseEnter={e => { if (assignmentMutable) { (e.currentTarget as HTMLElement).style.borderColor = C.green; (e.currentTarget as HTMLElement).style.color = C.green; } }}
        onMouseLeave={e => { if (!isActive) { (e.currentTarget as HTMLElement).style.borderColor = C.border; (e.currentTarget as HTMLElement).style.color = C.textMuted; } }}>
        + add
      </button>
    );
  }

  function AddPopover({ title }: { title: string }) {
    if (!pickerAnchor) return null;
    const viewportPadding = 12;
    const gap = 6;
    const width = Math.min(300, window.innerWidth - viewportPadding * 2);
    const spaceBelow = window.innerHeight - pickerAnchor.bottom - viewportPadding;
    const spaceAbove = pickerAnchor.top - viewportPadding;
    const openAbove = spaceBelow < 320 && spaceAbove > spaceBelow;
    const maxHeight = Math.max(180, Math.min(360, (openAbove ? spaceAbove : spaceBelow) - gap));
    const left = Math.min(
      Math.max(viewportPadding, pickerAnchor.left),
      window.innerWidth - width - viewportPadding,
    );

    return createPortal(
      <div style={{
        position: "fixed",
        ...(openAbove
          ? { bottom: window.innerHeight - pickerAnchor.top + gap }
          : { top: pickerAnchor.bottom + gap }),
        left,
        zIndex: 1000,
        width,
        maxHeight,
        display: "flex",
        flexDirection: "column",
        background: C.surface2,
        border: `1px solid ${C.green}`,
        borderRadius: 4,
        boxShadow: "0 12px 32px rgba(0,0,0,0.6)",
      }}
        onClick={e => e.stopPropagation()}>
        <div style={{ padding: "9px 12px", borderBottom: `1px solid ${C.border}`, color: C.green, fontFamily: mono, fontSize: 10.5, fontWeight: 700, letterSpacing: "0.06em" }}>
          Add to: {title}
        </div>
        <div style={{ padding: 9 }}>
          <input autoFocus value={query} onChange={e => setQuery(e.target.value)} placeholder="search staff…"
            style={{ width: "100%", boxSizing: "border-box", background: C.surface, border: `1px solid ${C.border}`, borderRadius: 4, color: C.text, fontFamily: mono, fontSize: 12.5, padding: "8px 10px", outline: "none" }} />
        </div>
        <div style={{ minHeight: 0, overflowY: "auto" }}>
          {staffPool.length === 0 && <div style={{ padding: "9px 13px", color: C.textMuted, fontFamily: mono, fontSize: 12 }}>No staff found.</div>}
          {staffPool.map(u => {
            const already = addedIds.has(u.userId);
            const otherMentorAssignment = active?.kind === 'mentor'
              ? mentors.find(m => m.mentorUserId === u.userId && m.trackId !== active.trackId)
              : undefined;
            const otherJudgeRoundAssignment = active?.kind === 'judge' || active?.kind === 'final'
              ? judges.find(j => j.judgeUserId === u.userId
                && j.roundId === active.roundId
                && (active.kind === 'final' ? j.trackId != null : j.trackId !== active.trackId))
              : undefined;
            // Guest judges are one-off external scorers, not embedded staff —
            // they can't take on a mentor's ongoing track responsibility.
            const isGuestMentorAttempt = active?.kind === 'mentor' && u.judgeType === 'GUEST';
            const unavailableReason = isGuestMentorAttempt
              ? "Guest judges cannot be assigned as mentors."
              : otherMentorAssignment
              ? `Already manages ${otherMentorAssignment.trackName} in this event.`
              : otherJudgeRoundAssignment
              ? `Already judges ${otherJudgeRoundAssignment.trackName ?? "all tracks"} in this round.`
              : null;
            const warning = already || unavailableReason ? null : conflictWarning(u.userId);
            return (
              <div key={u.userId} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 8, padding: "8px 12px", borderTop: `1px solid ${C.border}` }}>
                <div style={{ minWidth: 0 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                    <div style={{ color: C.text, fontFamily: mono, fontSize: 12.5, fontWeight: 600, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{u.fullName}</div>
                    {warning && <span title={warning} style={{ fontSize: 12, flexShrink: 0 }}>⚠️</span>}
                  </div>
                  <div style={{ color: unavailableReason ? C.red : warning ? "#eab308" : C.textMuted, fontFamily: mono, fontSize: 10, lineHeight: 1.4 }}>
                    {unavailableReason ?? warning ?? `${u.judgeType ?? "STAFF"}${u.email ? ` · ${u.email}` : ""}`}
                  </div>
                </div>
                {already ? <span style={{ color: C.green, fontFamily: mono, fontSize: 11, fontWeight: 700 }}>✓ added</span>
                  : unavailableReason ? <span title={unavailableReason} style={{ color: C.red, fontFamily: mono, fontSize: 9.5, fontWeight: 700 }}>UNAVAILABLE</span>
                  : (
                    <button
                      onClick={() => warning ? setPendingAdd({ userId: u.userId, name: u.fullName, warning }) : add(u.userId)}
                      disabled={busy}
                      style={{ background: warning ? "rgba(234,179,8,0.16)" : "rgba(34,197,94,0.16)", border: `1px solid ${warning ? "rgba(234,179,8,0.5)" : "rgba(34,197,94,0.5)"}`, borderRadius: 4, color: warning ? "#eab308" : C.green, fontFamily: mono, fontSize: 11, fontWeight: 700, padding: "4px 10px", cursor: "pointer", flexShrink: 0 }}
                    >
                      add +
                    </button>
                  )}
              </div>
            );
          })}
        </div>
      </div>,
      document.body,
    );
  }

  const cellBase: React.CSSProperties = { position: "relative", background: C.surface2, border: `1px solid ${C.border}`, borderRadius: 4, padding: 12, display: "flex", flexWrap: "wrap", gap: 8, alignItems: "flex-start", minHeight: 80 };
  const headCell: React.CSSProperties = { background: HEAD_BG, border: `1px solid ${HEAD_BORDER}`, borderRadius: 4, padding: "11px 14px", fontFamily: mono, fontSize: 12.5, fontWeight: 700, color: C.green, letterSpacing: "0.06em", display: "flex", flexDirection: "column", gap: 3, justifyContent: "center" };

  const gridCols = `${TRACK_COL}px ${TRACK_COL}px ${prelimRounds.map(() => CELL_COL).join(" ")}`;

  return (
    <div style={{ padding: 24, display: "flex", flexDirection: "column", gap: 18 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", flexWrap: "wrap", gap: 12 }}>
        <div>
          <h1 style={{ fontFamily: mono, fontSize: 28, fontWeight: 800 }}><GradientText>Assignments</GradientText></h1>
          <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 11, marginTop: 5 }}>
            Every judge in a round × track cell scores every submission in that cell.
          </div>
        </div>
        {currentEvent && (
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <span style={{ color: C.text, fontFamily: mono, fontSize: 13, fontWeight: 700 }}>{currentEvent.name}</span>
            <PixelBadge color={currentEvent.status === "IN_PROGRESS" ? "green" : "gray"}>{currentEvent.status}</PixelBadge>
          </div>
        )}
      </div>

      {loadError && <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: mono, fontSize: 11, padding: "10px 14px" }}>ERROR: {loadError}</div>}

      {loading && <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 12 }}>Loading…</div>}

      {!loading && currentEvent == null && (
        <div style={{ background: C.surface, border: `1px solid ${C.border}`, borderRadius: 6, padding: 18, color: C.textMuted, fontFamily: mono, fontSize: 13 }}>
          No event is currently open for configuration. Past events have moved to History.
        </div>
      )}

      {!loading && currentEvent != null && tracks.length === 0 && (
        <div style={{ background: C.surface, border: `1px solid ${C.border}`, borderRadius: 6, padding: 18, color: C.textMuted, fontFamily: mono, fontSize: 13 }}>
          This event has no tracks yet — create tracks &amp; rounds first.
        </div>
      )}

      {!loading && currentEvent != null && tracks.length > 0 && (
        <div style={{
          background: C.surface, border: `1px solid ${C.borderBright}`, borderRadius: 8, padding: 16, overflowX: "auto",
          boxShadow: "0 0 0 1px rgba(34,197,94,0.06), 0 10px 30px rgba(0,0,0,0.35)",
          position: "relative",
        }}>
          <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${C.green}, ${C.blue}, transparent)`, opacity: 0.6, borderTopLeftRadius: 8, borderTopRightRadius: 8 }} />
          <div style={{ display: "grid", gridTemplateColumns: gridCols, gap: 8, minWidth: 820 }}>
            <div style={headCell}>TRACK</div>
            <div style={{ ...headCell, background: MENTOR_HEAD_BG, border: `1px solid ${MENTOR_HEAD_BORDER}`, color: MENTOR_TEXT }}>
              MENTORS<span style={{ fontWeight: 400, fontSize: 10, color: C.textMuted }}></span>
            </div>
            {prelimRounds.map(r => (
              <div key={r.roundId} style={headCell}>
                {r.name}<span style={{ fontWeight: 400, fontSize: 10, color: C.textMuted }}></span>
              </div>
            ))}

            {tracks.map(track => (
              <RowFragment key={track.trackId}
                track={track} teamCount={teamCountOf(track.trackId)} prelimRounds={prelimRounds}
                cellBase={cellBase} mentorCellBg={MENTOR_CELL_BG} mentorsOf={mentorsOf} judgesOf={judgesOf}
                active={active} setActive={(a, anchor) => { setActive(a); setPickerAnchor(a ? anchor ?? null : null); setQuery(""); }}
                Chip={Chip} AddButton={AddButton} AddPopover={AddPopover}
                removeMentor={requestRemoveMentor} removeJudge={requestRemoveJudge} replaceJudge={requestReplaceJudge}
              />
            ))}
          </div>

          {finalRounds.map(fr => {
            const fJudges = judgesOf(fr.roundId, null);
            const isActive = active?.kind === 'final' && active.roundId === fr.roundId;
            return (
              <div key={fr.roundId} style={{ marginTop: 14, background: FINAL_BG, border: `1px solid ${FINAL_BORDER}`, borderRadius: 6, padding: 14 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 12, flexWrap: "wrap" }}>
                  <span style={{ color: C.yellow, fontFamily: mono, fontSize: 13.5, fontWeight: 800, letterSpacing: "0.05em" }}> {fr.name} · FINAL</span>
                  {fr.status && <PixelBadge color={["ACTIVE", "OPEN", "IN_PROGRESS"].includes((fr.status).toUpperCase()) ? "green" : "gray"}>{fr.status}</PixelBadge>}
                  <PixelBadge color={fJudges.length > 0 ? "green" : "red"}>{fJudges.length} JUDGE{fJudges.length === 1 ? "" : "S"}</PixelBadge>
                </div>
                <div style={{ display: "flex", flexWrap: "wrap", gap: 8, alignItems: "flex-start" }}>
                  {fJudges.map(j => <Chip key={j.id} name={j.judgeName} judgeType={j.judgeType} onRemove={() => requestRemoveJudge(j.id)} onReplace={() => requestReplaceJudge(j)} />)}
                  <div style={{ position: "relative" }}>
                    <AddButton isActive={isActive} onClick={(anchor) => { setActive(isActive ? null : { kind: 'final', roundId: fr.roundId }); setPickerAnchor(isActive ? null : anchor); setQuery(""); }} />
                    {isActive && <AddPopover title={`${fr.name} · all tracks`} />}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {active && <div onClick={() => { setActive(null); setPickerAnchor(null); }} style={{ position: "fixed", inset: 0, zIndex: 40 }} />}

      {confirmRemove && (
        <ConfirmDialog
          title={confirmRemove.kind === 'mentor' ? "Remove this mentor?" : "Remove this judge?"}
          message={confirmRemove.kind === 'mentor'
            ? `Remove ${confirmRemove.name} from this assignment. If this is a mistake, re-adding takes one click.`
            : `Remove ${confirmRemove.name} from this assignment. Direct removal is allowed only before judging starts; use Replace Judge once the roster is locked.`}
          confirmLabel="REMOVE"
          variant="danger"
          working={removeBusy}
          onConfirm={runConfirmedRemove}
          onClose={() => { if (!removeBusy) setConfirmRemove(null); }}
        />
      )}

      {replaceTarget && (
        <ConfirmDialog
          title="Replace this judge?"
          message={`Replace ${replaceTarget.judgeName} in ${replaceTarget.roundName}${replaceTarget.trackName ? ` · ${replaceTarget.trackName}` : " · all tracks"}. The panel size will stay unchanged.`}
          warning="Judging must be paused or stopped, and the current judge must not have submitted any final score in this cell."
          confirmLabel="REPLACE JUDGE"
          variant="cyber"
          working={replaceBusy}
          error={replaceError}
          onConfirm={runReplaceJudge}
          onClose={() => { if (!replaceBusy) setReplaceTarget(null); }}
        >
          <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            <label style={{ color: C.textMuted, fontFamily: mono, fontSize: 11 }}>
              Replacement judge
              <select value={replacementJudgeId ?? ""} onChange={e => setReplacementJudgeId(e.target.value ? Number(e.target.value) : null)}
                style={{ display: "block", width: "100%", marginTop: 6, background: C.surface2, border: `1px solid ${C.border}`, color: C.text, fontFamily: mono, padding: "9px 10px" }}>
                <option value="">Select staff…</option>
                {staff.filter(user => user.userId !== replaceTarget.judgeUserId
                  && !judges.some(judge => judge.roundId === replaceTarget.roundId && judge.judgeUserId === user.userId))
                  .map(user => <option key={user.userId} value={user.userId}>{user.fullName}</option>)}
              </select>
            </label>
            <label style={{ color: C.textMuted, fontFamily: mono, fontSize: 11 }}>
              Reason (audit log)
              <textarea value={replacementReason} maxLength={500} onChange={e => setReplacementReason(e.target.value)} placeholder="e.g. Judge unavailable after judging started"
                style={{ display: "block", width: "100%", minHeight: 76, marginTop: 6, resize: "vertical", background: C.surface2, border: `1px solid ${C.border}`, color: C.text, fontFamily: mono, padding: "9px 10px" }} />
            </label>
          </div>
        </ConfirmDialog>
      )}

      {pendingAdd && (
        <ConfirmDialog
          title="Assign anyway?"
          message={`${pendingAdd.name}: ${pendingAdd.warning} Assign them anyway?`}
          confirmLabel="ASSIGN ANYWAY"
          variant="danger"
          working={busy}
          onConfirm={async () => { await add(pendingAdd.userId); setPendingAdd(null); }}
          onClose={() => { if (!busy) setPendingAdd(null); }}
        />
      )}
    </div>
  );
}

function RowFragment(props: {
  track: Track; teamCount: number; prelimRounds: Round[];
  cellBase: React.CSSProperties; mentorCellBg: string;
  mentorsOf: (trackId: number) => MentorRosterItem[];
  judgesOf: (roundId: number, trackId: number | null) => JudgeRosterItem[];
  active: Active; setActive: (a: Active, anchor?: DOMRect) => void;
  Chip: (p: { name: string; judgeType?: string; onRemove: () => void; onReplace?: () => void }) => React.JSX.Element;
  AddButton: (p: { onClick: (anchor: DOMRect) => void; isActive: boolean }) => React.JSX.Element;
  AddPopover: (p: { title: string }) => React.JSX.Element;
  removeMentor: (id: number) => void; removeJudge: (id: number) => void; replaceJudge: (assignment: JudgeRosterItem) => void;
}) {
  const { track, teamCount, prelimRounds, cellBase, mentorCellBg, mentorsOf, judgesOf, active, setActive, Chip, AddButton, AddPopover, removeMentor, removeJudge, replaceJudge } = props;
  const mentorActive = active?.kind === 'mentor' && active.trackId === track.trackId;
  const trackMentors = mentorsOf(track.trackId);

  return (
    <>
      <div style={{ background: C.surface3, border: `1px solid ${C.border}`, borderLeft: `3px solid ${C.green}`, borderRadius: 4, padding: "12px 14px", display: "flex", flexDirection: "column", gap: 5, justifyContent: "center" }}>
        <span style={{ color: C.text, fontFamily: mono, fontSize: 14.5, fontWeight: 700 }}>{track.name}</span>
        <span style={{ color: C.cyan, fontFamily: mono, fontSize: 11, fontWeight: 600 }}>{teamCount} team{teamCount === 1 ? "" : "s"}</span>
      </div>

      <div style={{ ...cellBase, background: mentorCellBg }}>
        {trackMentors.map(m => <Chip key={m.id} name={m.mentorName} onRemove={() => removeMentor(m.id)} />)}
        <div style={{ position: "relative" }}>
          <AddButton isActive={mentorActive} onClick={(anchor) => setActive(mentorActive ? null : { kind: 'mentor', trackId: track.trackId }, anchor)} />
          {mentorActive && <AddPopover title={`${track.name} · mentor`} />}
        </div>
      </div>

      {prelimRounds.map(r => {
        const cellJudges = judgesOf(r.roundId, track.trackId);
        const isActive = active?.kind === 'judge' && active.roundId === r.roundId && active.trackId === track.trackId;
        return (
          <div key={r.roundId} style={cellBase}>
            <div style={{ width: "100%", display: "flex", justifyContent: "flex-end" }}>
              <PixelBadge color={cellJudges.length > 0 ? "green" : "red"}>{cellJudges.length} JUDGE{cellJudges.length === 1 ? "" : "S"}</PixelBadge>
            </div>
            {cellJudges.map(j => <Chip key={j.id} name={j.judgeName} judgeType={j.judgeType} onRemove={() => removeJudge(j.id)} onReplace={() => replaceJudge(j)} />)}
            <div style={{ position: "relative" }}>
              <AddButton isActive={isActive} onClick={(anchor) => setActive(isActive ? null : { kind: 'judge', roundId: r.roundId, trackId: track.trackId }, anchor)} />
              {isActive && <AddPopover title={`${track.name} · ${r.name}`} />}
            </div>
          </div>
        );
      })}
    </>
  );
}
