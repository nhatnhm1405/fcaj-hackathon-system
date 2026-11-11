import { useState, type Dispatch, type ReactNode, type SetStateAction } from "react";
import { C, PixelButton, PixelInput } from "@/shared/components/PixelComponents";
import { PixelMenu } from "@/shared/components/PixelMenu";
import { apiFetch, ApiError, apiErrorMessage, teamsApi, type Team } from "@/shared/apiClient";
import { useNotifications } from "@/app/providers/NotificationProvider";
import { EventRow, TrackRow, ApiTrack, normalizeTrack, PendingAction } from "@/features/events/eventUtils";
import { maxTeamsPerTrack, countAssigned, countUnassigned, teamsForTrack, isTrackValid, wouldExceedMax, MIN_TEAMS_PER_TRACK } from "@/features/events/trackStats";
import { useDrag, useDrop } from "react-dnd";

// The Tracks tab of CoordEventsPage: per-track team rosters (NV1/NV2), drag-drop
// assignment (PHẦN 4), track CRUD (PHẦN 3), and the SETUP-only leftover-grouping /
// track-draw toolbar. Extracted verbatim out of CoordEventsPage.tsx — behavior,
// API calls, and copy are unchanged.

// Leader's display name for a team, if present in its member list.
function leaderName(team: Team): string | null {
  return team.members?.find(m => m.role === 'LEADER')?.fullName ?? null;
}

// One compact stat in the Tracks-tab overview strip (track statistics — NV1).
function StatCell({ label, value, accent }: { label: string; value: ReactNode; accent?: boolean }) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 4, minWidth: 90 }}>
      <span style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 10, letterSpacing: "0.1em", textTransform: "uppercase" }}>{label}</span>
      <span style={{ color: accent ? C.yellow : C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 22, fontWeight: 800, lineHeight: 1 }}>{value}</span>
    </div>
  );
}

// One team row rendered under a track (or in the Unassigned group) — NV2.
function TeamRow({ team }: { team: Team }) {
  const count = team.members?.length ?? 0;
  const leader = leaderName(team);
  return (
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8 }}>
      <span style={{ color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 13 }}>{team.name}</span>
      <span style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, whiteSpace: "nowrap" }}>
        {count} {count === 1 ? "member" : "members"}{leader ? ` · ${leader}` : ""}
      </span>
    </div>
  );
}

// react-dnd payload + type for dragging a team between tracks / the unassigned pool.
const TEAM_DND_TYPE = "COORD_TEAM";
interface TeamDragItem { teamId: number; fromTrackId: number | null; }

// A TeamRow the coordinator can drag while `enabled` (SETUP). The drag connector
// is only attached when enabled, so rows are static read-only outside SETUP.
function DraggableTeamRow({ team, fromTrackId, enabled }: { team: Team; fromTrackId: number | null; enabled: boolean }) {
  const [{ isDragging }, dragRef] = useDrag(() => ({
    type: TEAM_DND_TYPE,
    item: { teamId: team.teamId, fromTrackId } as TeamDragItem,
    canDrag: enabled,
    collect: (monitor) => ({ isDragging: monitor.isDragging() }),
  }), [team.teamId, fromTrackId, enabled]);

  return (
    <div ref={(node) => { if (enabled) dragRef(node); }}
      style={{ cursor: enabled ? "grab" : "default", opacity: isDragging ? 0.4 : 1 }}>
      <TeamRow team={team} />
    </div>
  );
}

// A drop zone (a track body or the unassigned pool) that accepts dragged teams.
// targetTrackId = null means the unassigned pool. Dropping onto the team's current
// location is rejected so a no-op drag doesn't fire a request.
function TeamDropZone({ enabled, targetTrackId, onDropTeam, children, flush = false }: {
  enabled: boolean;
  targetTrackId: number | null;
  onDropTeam: (item: TeamDragItem, targetTrackId: number | null) => void;
  children: ReactNode;
  // flush: no top border — used when the zone wraps a whole card (so a collapsed
  // track card still accepts drops) rather than sitting below a header.
  flush?: boolean;
}) {
  const [{ isOver, canDrop }, dropRef] = useDrop(() => ({
    accept: TEAM_DND_TYPE,
    canDrop: (item: TeamDragItem) => enabled && item.fromTrackId !== targetTrackId,
    drop: (item: TeamDragItem) => onDropTeam(item, targetTrackId),
    collect: (monitor) => ({ isOver: monitor.isOver(), canDrop: monitor.canDrop() }),
  }), [enabled, targetTrackId, onDropTeam]);

  const active = isOver && canDrop;
  return (
    <div ref={(node) => { if (enabled) dropRef(node); }}
      style={{
        borderTop: flush ? "none" : `1px solid ${C.border}`,
        background: active ? "rgba(34,197,94,0.10)" : "transparent",
        outline: active ? `1px dashed ${C.green}` : "none",
        transition: "background 0.12s",
      }}>
      {children}
    </div>
  );
}

// A small framed chip used in the track header (team count + status). `tone` drives
// the border / background / text colour. Text is NOT uppercased (unlike PixelBadge),
// so labels read as "Ready" / "Needs 2 teams to run".
function TrackChip({ tone, children, title }: { tone: "green" | "red" | "amber"; children: ReactNode; title?: string }) {
  const tones = {
    green: { border: "rgba(34,197,94,0.45)", bg: "rgba(34,197,94,0.08)", color: "#4ade80" },
    red:   { border: "rgba(239,68,68,0.45)", bg: "rgba(239,68,68,0.08)", color: "#f87171" },
    amber: { border: "rgba(234,179,8,0.45)", bg: "rgba(234,179,8,0.08)", color: "#facc15" },
  };
  const t = tones[tone];
  return (
    <span title={title} style={{
      display: "inline-flex", alignItems: "center", whiteSpace: "nowrap",
      border: `1px solid ${t.border}`, background: t.bg, color: t.color,
      borderRadius: 0, padding: "4px 10px",
      fontFamily: "'JetBrains Mono', monospace", fontSize: 12, lineHeight: 1,
    }}>{children}</span>
  );
}

export function TracksTab({
  event, tracks, setTracks, teams, teamsLoading, teamsError, leftoverCount,
  refreshTeams, openConfirm, onOpenGrouping, detailLoading, setActionError, setSuccessMsg,
}: {
  event: EventRow;
  tracks: TrackRow[];
  setTracks: Dispatch<SetStateAction<TrackRow[]>>;
  teams: Team[];
  teamsLoading: boolean;
  teamsError: string | null;
  leftoverCount: number | null;
  refreshTeams: () => void;
  openConfirm: (action: PendingAction) => void;
  onOpenGrouping: () => void;
  detailLoading: boolean;
  setActionError: (msg: string | null) => void;
  setSuccessMsg: (msg: string | null) => void;
}) {
  const { addToast } = useNotifications();

  const [drawing, setDrawing] = useState(false);
  // Per-track expand/collapse. Cards default to EXPANDED (description + team
  // list visible); tracking the collapsed ones means the empty map = all open,
  // so no per-track init is needed when the list loads. Clicking a card header
  // collapses it to a single line when the coordinator wants to focus elsewhere.
  const [collapsedTracks, setCollapsedTracks] = useState<Record<number, boolean>>({});

  // Track form (collapsed behind "+ ADD TRACK" until needed)
  const [showAddTrack, setShowAddTrack] = useState(false);
  const [trkName, setTrkName] = useState("");
  const [trkDesc, setTrkDesc] = useState("");
  // Inline track edit (separate from the create form so the two never clash)
  const [editingTrackId, setEditingTrackId] = useState<number | null>(null);
  const [etName, setEtName] = useState("");
  const [etDesc, setEtDesc] = useState("");

  // Random track draw — only meaningful while the event is in SETUP. includeAssigned
  // false → only teams without a track are drawn; true → re-shuffle every team.
  async function drawTracks(includeAssigned: boolean) {
    if (drawing) return;
    setActionError(null);
    setSuccessMsg(null);
    setDrawing(true);
    try {
      const res = await apiFetch<{ data: unknown[] }>(
        `/api/teams/event/${event.eventId}/draw-tracks?includeAssigned=${includeAssigned}`,
        { method: 'POST' },
      );
      const count = (res.data ?? []).length;
      setSuccessMsg(`Track draw complete — ${count} team(s) assigned to tracks.`);
      refreshTeams();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Failed to draw tracks.");
      addToast({ type: 'warning', title: 'DRAW FAILED', message: apiErrorMessage(err, 'Failed to draw tracks.') });
    } finally {
      setDrawing(false);
    }
  }

  // REDRAW ALL is destructive (wipes every assignment) and a fairness risk if used
  // to re-roll until satisfied — so, unlike the additive DRAW TRACKS, it is gated
  // behind a confirmation that spells out the fairness caveat. Errors surface in the
  // dialog (the run() rethrows) rather than as a page-level banner.
  function requestRedrawAll() {
    openConfirm({
      title: 'Redraw ALL track assignments?',
      message: `Clear every team's track in "${event.name}" and reshuffle from scratch.`,
      warning: 'A single random draw is already fair. Re-rolling until you like the result undermines that — only redraw to fix a setup mistake (wrong tracks or capacities).',
      confirmLabel: 'CONFIRM REDRAW',
      variant: 'danger',
      withReason: true,
      reasonPlaceholder: 'Why redraw? e.g. fixed track capacities / added a track',
      run: async (reasonText) => {
        const qs = reasonText ? `&reason=${encodeURIComponent(reasonText)}` : '';
        const res = await apiFetch<{ data: unknown[] }>(
          `/api/teams/event/${event.eventId}/draw-tracks?includeAssigned=true${qs}`,
          { method: 'POST' },
        );
        const count = (res.data ?? []).length;
        setActionError(null);
        setSuccessMsg(`Redraw complete — ${count} team(s) reshuffled across tracks.`);
        refreshTeams();
      },
    });
  }

  async function addTrack() {
    const name = trkName.trim();
    if (!name) {
      addToast({ type: 'warning', title: 'MISSING NAME', message: 'Please enter a track name.' });
      return;
    }
    setActionError(null);
    try {
      const res = await apiFetch<{ data: ApiTrack }>(`/api/events/${event.eventId}/tracks`, {
        method: 'POST',
        body: JSON.stringify({ name, description: trkDesc || undefined }),
      });
      setTracks(prev => [...prev, normalizeTrack(res.data)]);
      setTrkName(""); setTrkDesc("");
      addToast({ type: 'success', title: 'TRACK ADDED', message: `"${name}" created.` });
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Failed to add track.");
      addToast({ type: 'warning', title: 'CREATE FAILED', message: apiErrorMessage(err, 'Failed to add track.') });
    }
  }

  function startEditTrack(track: TrackRow) {
    setEditingTrackId(track.trackId);
    setEtName(track.name);
    setEtDesc(track.description ?? "");
  }

  function cancelTrackEdit() {
    setEditingTrackId(null);
    setEtName(""); setEtDesc("");
  }

  async function saveTrackEdit() {
    if (editingTrackId == null) return;
    const name = etName.trim();
    if (!name) {
      addToast({ type: 'warning', title: 'MISSING NAME', message: 'Please enter a track name.' });
      return;
    }
    setActionError(null);
    try {
      const res = await apiFetch<{ data: ApiTrack }>(`/api/events/${event.eventId}/tracks/${editingTrackId}`, {
        method: 'PUT',
        body: JSON.stringify({ name, description: etDesc || undefined }),
      });
      const updated = normalizeTrack(res.data);
      setTracks(prev => prev.map(t => t.trackId === editingTrackId ? updated : t));
      cancelTrackEdit();
      addToast({ type: 'success', title: 'TRACK UPDATED', message: `"${name}" saved.` });
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Failed to update track.");
      addToast({ type: 'warning', title: 'UPDATE FAILED', message: apiErrorMessage(err, 'Failed to update track.') });
    }
  }

  // PHẦN 4 — commit a drag-drop assignment (or unassign when targetTrackId is null).
  // Toasts the outcome and re-pulls the roster so all counts/lists stay in sync.
  async function performAssign(teamId: number, targetTrackId: number | null) {
    try {
      await teamsApi.assignTrack(teamId, targetTrackId);
      // Make sure the receiving card is open so the drop's result is visible.
      if (targetTrackId != null) setCollapsedTracks(p => ({ ...p, [targetTrackId]: false }));
      refreshTeams();
      addToast({
        type: 'success',
        title: targetTrackId == null ? 'TEAM UNASSIGNED' : 'TEAM ASSIGNED',
        message: targetTrackId == null ? 'Team moved to the unassigned pool.' : 'Team moved into the track.',
      });
    } catch (err) {
      addToast({ type: 'warning', title: 'ASSIGN FAILED', message: err instanceof ApiError ? err.message : 'Failed to assign team.' });
    }
  }

  // PHẦN 4 — handle a drop. Dropping a team where it already is, is ignored. If the
  // drop would push a track past the recommended max we still allow it, but ask for
  // confirmation first (soft cap); otherwise assign immediately.
  function onDropTeam(item: TeamDragItem, targetTrackId: number | null) {
    if (item.fromTrackId === targetTrackId) return;
    if (targetTrackId != null) {
      const approved = teams.filter(t => t.status === 'APPROVED');
      const currentCount = teamsForTrack(approved, targetTrackId).length;
      const targetTrack = tracks.find(t => t.trackId === targetTrackId);
      // Always compare against the uniform ceiling (e.g. 4/4 everywhere), not each
      // track's own backend-assigned capacity — the actual split can be uneven
      // (4/4/3/3) but the displayed/enforced max must read the same on every track.
      const max = Math.max(
        maxTeamsPerTrack(approved.length, tracks.length),
        MIN_TEAMS_PER_TRACK,
      );
      if (wouldExceedMax(currentCount, max)) {
        openConfirm({
          title: 'Track over recommended max',
          message: `Assigning this team to "${targetTrack?.name ?? 'this track'}" makes ${currentCount + 1} teams — above this track's maximum of ${max}.`,
          warning: 'You can proceed; this track will simply exceed the recommended maximum.',
          confirmLabel: 'ASSIGN ANYWAY',
          variant: 'cyber',
          run: async () => { await performAssign(item.teamId, targetTrackId); },
        });
        return;
      }
    }
    performAssign(item.teamId, targetTrackId);
  }

  // PHẦN 3 — manual track cleanup. Confirms with a preview of which teams will move
  // back to the unassigned pool, then deletes the track. Teams are NOT redistributed.
  function requestDeleteTrack(track: TrackRow) {
    const eventId = event.eventId;
    const trackTeams = teamsForTrack(teams.filter(t => t.status === 'APPROVED'), track.trackId);
    openConfirm({
      title: 'Remove this track?',
      message: (
        <div>
          Track <span style={{ color: C.text, fontWeight: 700 }}>"{track.name}"</span> will be deleted.
          {trackTeams.length > 0 ? (
            <div style={{ marginTop: 10 }}>
              These {trackTeams.length} team{trackTeams.length === 1 ? "" : "s"} will move to the <b>Unassigned</b> pool:
              <ul style={{ margin: "8px 0 0", paddingLeft: 18, lineHeight: 1.7 }}>
                {trackTeams.map(t => <li key={t.teamId}>{t.name}</li>)}
              </ul>
            </div>
          ) : (
            <div style={{ marginTop: 10 }}>This track has no teams.</div>
          )}
        </div>
      ),
      warning: trackTeams.length > 0
        ? 'Teams are NOT auto-distributed — drag them from the Unassigned pool into a valid track.'
        : undefined,
      confirmLabel: 'DELETE TRACK',
      variant: 'danger',
      // Deleting a track that already has registered teams is high-impact —
      // require typing the track name; an empty track keeps the plain confirm.
      requireTypedText: trackTeams.length > 0 ? track.name : undefined,
      run: async () => {
        await apiFetch(`/api/events/${eventId}/tracks/${track.trackId}`, { method: 'DELETE' });
        setTracks(prev => prev.filter(t => t.trackId !== track.trackId));
        refreshTeams();
        addToast({ type: 'success', title: 'TRACK REMOVED', message: `"${track.name}" deleted; its teams moved to Unassigned.` });
      },
    });
  }

  // ── Track statistics (NV1) + per-track rosters (NV2) ──────────────
  // Roster = APPROVED teams only (the set the backend freezes into track slots
  // on SETUP entry). Stats are shown from SETUP onward, once the roster is locked
  // and track assignment is under way.
  const approvedTeams = teams.filter(t => t.status === 'APPROVED');
  const trackCount = tracks.length;
  const totalTeams = approvedTeams.length;
  const maxPerTrack = maxTeamsPerTrack(totalTeams, trackCount);
  const assignedCount = countAssigned(approvedTeams);
  const unassignedCount = countUnassigned(approvedTeams);
  // How many tracks already meet the minimum team count — one aggregate number
  // on the stats bar instead of a warning chip repeated on every card.
  const readyTracks = tracks.filter(t => isTrackValid(teamsForTrack(approvedTeams, t.trackId).length)).length;
  const unassignedTeams = approvedTeams.filter(t => t.trackId == null);
  const isSelfSelect = event.trackSelectionMode === 'SELF_SELECT';
  const showTrackStats = event.status === 'SETUP' || event.status === 'IN_PROGRESS' || event.status === 'COMPLETED';
  // Creating a track is locked once registration closes — DRAFT/OPEN only
  // (mirrors TrackService.TRACK_CREATE_ALLOWED_EVENT_STATUSES on the backend).
  const trackCreationAllowed = event.status === 'DRAFT' || event.status === 'OPEN';
  // SETUP is the only phase where the coordinator manually moves teams: drag-drop,
  // team-shuffle and the start-event gate all key off this.
  const isSetup = event.status === 'SETUP';
  // Editing OR removing a track is allowed in DRAFT/OPEN/SETUP — mirrors
  // TrackService.TRACK_MUTATION_ALLOWED_EVENT_STATUSES. Locked once the event runs
  // (IN_PROGRESS/COMPLETED) so the EDIT/DELETE buttons hide there instead of 400-ing.
  const trackMutationAllowed = event.status === 'DRAFT' || event.status === 'OPEN' || event.status === 'SETUP';

  return (
      <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
        {/* SETUP coordinator toolbar — leftover grouping (only while people are
            still ungrouped; run BEFORE the draw) + random track draw, one panel. */}
        {event.status === 'SETUP' && ((leftoverCount ?? 0) > 0 || tracks.length > 0) && (
          <div style={{ padding: 14, background: C.surface, border: `1px solid ${C.border}`, display: "flex", gap: 24, alignItems: "flex-end", flexWrap: "wrap" }}>
            {(leftoverCount ?? 0) > 0 && (
              <div>
                <div style={{ color: "#facc15", fontFamily: "'JetBrains Mono', monospace", fontSize: 14, fontWeight: 700, marginBottom: 12, display: "flex", alignItems: "center", gap: 8 }}>
                  <span style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", width: 18, height: 18, border: "1.5px solid #facc15", fontSize: 12, fontWeight: 800, lineHeight: 1, flexShrink: 0 }}>!</span>
                  {leftoverCount} leftover participant{leftoverCount === 1 ? '' : 's'} not grouped yet!
                </div>
                <PixelButton variant="warning" onClick={onOpenGrouping}>
                  GROUP LEFTOVERS
                </PixelButton>
              </div>
            )}
            {(leftoverCount ?? 0) > 0 && tracks.length > 0 && (
              <div style={{ alignSelf: "stretch", width: 1, background: C.border }} />
            )}
            {tracks.length > 0 && (
              <div>
                  <div style={{ color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, fontWeight: 700, marginBottom: 10, letterSpacing: "0.05em" }}>
                    {event.trackSelectionMode === 'RANDOM' ? 'Random track draw' : 'Fill unassigned tracks'}
                  </div>
                  <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                    <PixelButton variant="cyber" onClick={() => drawTracks(false)}>
                      {drawing ? "DRAWING..." : "DRAW TRACKS"}
                    </PixelButton>
                    {/* REDRAW ALL wipes every assignment — only offered for RANDOM events
                        (it would destroy team self-selections) and gated behind a
                        fairness-warning confirm so it isn't used to re-roll until "happy". */}
                    {event.trackSelectionMode === 'RANDOM' && (
                      <PixelButton variant="secondary" onClick={requestRedrawAll}>
                        REDRAW ALL
                      </PixelButton>
                    )}
                  </div>
                </div>
            )}
          </div>
        )}
        {/* Track-statistics overview (NV1) — shown from SETUP onward, when
            the roster is frozen. Total + max/track for every mode; assigned
            + unassigned added for SELF_SELECT. */}
        {showTrackStats && (
          <div style={{ padding: 16, background: C.surface, border: `1px solid ${C.border}` }}>
            {teamsError ? (
              <div style={{ color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 11 }}>{teamsError}</div>
            ) : (
              <div style={{ display: "flex", gap: 28, flexWrap: "wrap", alignItems: "center" }}>
                <StatCell label="Total teams" value={teamsLoading ? "…" : totalTeams} />
                <StatCell label="Max / track" value={teamsLoading ? "…" : maxPerTrack} />
                {isSelfSelect && <StatCell label="Assigned" value={teamsLoading ? "…" : assignedCount} />}
                {isSelfSelect && <StatCell label="Unassigned" value={teamsLoading ? "…" : unassignedCount} accent={unassignedCount > 0} />}
                <StatCell label="Tracks ready" value={teamsLoading ? "…" : `${readyTracks}/${trackCount}`} accent={readyTracks < trackCount} />
              </div>
            )}
          </div>
        )}
        {detailLoading && <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>Loading...</div>}
        {!detailLoading && tracks.length === 0 && <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>No tracks yet</div>}
        {tracks.map(t => {
          const trackTeams = teamsForTrack(approvedTeams, t.trackId);
          // PHẦN 2 — a track needs >= MIN_TEAMS_PER_TRACK teams to be valid.
          const underMinimum = showTrackStats && !isTrackValid(trackTeams.length);
          // The backend distributes teams unevenly per track (e.g. 15 teams across
          // 4 tracks => 4/4/4/3 actual counts), but the displayed max stays the
          // same uniform ceiling on every track card — never below the minimum.
          const trackMax = Math.max(maxPerTrack, MIN_TEAMS_PER_TRACK);
          const overCapacity = showTrackStats && trackTeams.length > trackMax;
          const trackTone = underMinimum ? "red" : overCapacity ? "amber" : "green";
          // Shared team list. In SETUP each row is draggable (PHẦN 4); the
          // empty state doubles as a drop hint.
          const teamList = (
            <div style={{ padding: "10px 12px", display: "flex", flexDirection: "column", gap: 8 }}>
              {teamsLoading ? (
                <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11 }}>Loading teams…</div>
              ) : trackTeams.length === 0 ? (
                <div style={{ color: C.textDim, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, fontStyle: "italic" }}>
                  {isSetup ? "No teams yet — drag a team here" : "No teams in this track yet"}
                </div>
              ) : (
                trackTeams.map(tm => <DraggableTeamRow key={tm.teamId} team={tm} fromTrackId={t.trackId} enabled={isSetup} />)
              )}
            </div>
          );
          const isEditingThis = editingTrackId === t.trackId;
          const expanded = isEditingThis || !collapsedTracks[t.trackId];
          // Collapsed = one line: caret + name + readiness chip + ⋯ menu.
          // Expanded adds the description and (from SETUP) the team list.
          const cardInner = (
            <>
              <div
                onClick={() => { if (!isEditingThis) setCollapsedTracks(p => ({ ...p, [t.trackId]: !p[t.trackId] })); }}
                style={{ padding: "12px 14px", display: "flex", justifyContent: "space-between", alignItems: isEditingThis ? "flex-start" : "center", gap: 12, cursor: isEditingThis ? "default" : "pointer" }}
              >
                <div style={{ minWidth: 0, flex: 1 }}>
                  {isEditingThis ? (
                    <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                      <PixelInput label="Name" value={etName} onChange={(e) => setEtName(e.target.value)} placeholder="Track name" />
                      <PixelInput label="Description" value={etDesc} onChange={(e) => setEtDesc(e.target.value)} placeholder="What is this track about?" />
                      <div style={{ display: "flex", gap: 8 }}>
                        <PixelButton size="sm" variant="cyber" onClick={saveTrackEdit}>SAVE</PixelButton>
                        <PixelButton size="sm" variant="ghost" onClick={cancelTrackEdit}>CANCEL</PixelButton>
                      </div>
                    </div>
                  ) : (
                    <div style={{ display: "flex", alignItems: "center", gap: 10, minWidth: 0 }}>
                      <span style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, width: 10, flexShrink: 0 }}>{expanded ? "▾" : "▸"}</span>
                      <span style={{ color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 14, fontWeight: 700, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{t.name}</span>
                    </div>
                  )}
                </div>
                {/* Right side — ONE capacity chip (current/max teams) + the ⋯ menu.
                    stopPropagation so chip/menu clicks don't toggle the card. */}
                {(showTrackStats || (trackMutationAllowed && !isEditingThis)) && (
                  <div style={{ display: "flex", alignItems: "center", gap: 8, flexShrink: 0 }} onClick={(e) => e.stopPropagation()}>
                    {showTrackStats && (
                      <TrackChip
                        tone={trackTone}
                        title={underMinimum
                          ? `Needs at least ${MIN_TEAMS_PER_TRACK} teams to start the event`
                          : overCapacity
                            ? `Above this track's maximum capacity of ${trackMax}`
                            : `${trackTeams.length} of ${trackMax} team slots used`}
                      >
                        <b style={{ fontWeight: 800, fontSize: 13 }}>{trackTeams.length}/{trackMax}</b>
                        <span style={{ marginLeft: 5 }}>teams</span>
                      </TrackChip>
                    )}
                    {trackMutationAllowed && !isEditingThis && (
                      <span className="row-action">
                        <PixelMenu
                          ariaLabel={`Actions for track ${t.name}`}
                          items={[
                            { label: "Edit", onClick: () => startEditTrack(t) },
                            "divider",
                            { label: "Delete", danger: true, onClick: () => requestDeleteTrack(t) },
                          ]}
                        />
                      </span>
                    )}
                  </div>
                )}
              </div>
              {expanded && !isEditingThis && (
                <div style={{ padding: "0 14px 12px 34px", color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>
                  {t.description || "—"}
                </div>
              )}
              {/* Per-track team list (NV2), only while expanded. */}
              {expanded && showTrackStats && (
                <div style={{ borderTop: `1px solid ${C.border}` }}>{teamList}</div>
              )}
            </>
          );
          return (
            <div key={t.trackId} className="row-actionable" style={{
              background: C.surface2,
              border: `1px solid ${C.border}`,
              // PHẦN 2 — track validity reads as a left accent bar: red =
              // under minimum, amber = over capacity, green = within range.
              // Neutral before SETUP, when no team is assigned yet.
              borderLeft: `3px solid ${showTrackStats ? (underMinimum ? C.red : overCapacity ? C.yellow : C.green) : C.border}`,
            }}>
              {/* In SETUP the WHOLE card is the drop target, so a collapsed
                  card still accepts a dragged team (it auto-expands on drop). */}
              {isSetup && showTrackStats
                ? <TeamDropZone flush enabled targetTrackId={t.trackId} onDropTeam={onDropTeam}>{cardInner}</TeamDropZone>
                : cardInner}
            </div>
          );
        })}
        {/* Unassigned pool. In SETUP it is a drop target for BOTH modes (drag a
            team here to pull it off a track — PHẦN 4); outside SETUP it stays the
            read-only self-select view. */}
        {isSetup && showTrackStats ? (
          <div style={{ background: C.surface, border: `1px solid ${unassignedTeams.length > 0 ? `${C.yellow}55` : C.border}` }}>
            <div style={{ padding: 12, color: unassignedTeams.length > 0 ? C.yellow : C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, fontWeight: 700, letterSpacing: "0.05em" }}>
              Unassigned teams <span style={{ color: C.textMuted }}>· {unassignedTeams.length}</span>
            </div>
            <TeamDropZone enabled targetTrackId={null} onDropTeam={onDropTeam}>
              <div style={{ padding: "10px 12px", display: "flex", flexDirection: "column", gap: 8 }}>
                {teamsLoading ? (
                  <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11 }}>Loading teams…</div>
                ) : unassignedTeams.length === 0 ? (
                  <div style={{ color: C.textDim, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, fontStyle: "italic" }}>No unassigned teams — drag a team here to remove it from its track</div>
                ) : (
                  unassignedTeams.map(tm => <DraggableTeamRow key={tm.teamId} team={tm} fromTrackId={null} enabled />)
                )}
              </div>
            </TeamDropZone>
          </div>
        ) : (showTrackStats && isSelfSelect && !teamsLoading && unassignedTeams.length > 0 && (
          <div style={{ padding: 12, background: C.surface, border: `1px solid ${C.yellow}55` }}>
            <div style={{ color: C.yellow, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, fontWeight: 700, letterSpacing: "0.05em", marginBottom: 8 }}>
              Unassigned teams <span style={{ color: C.textMuted }}>· {unassignedTeams.length}</span>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {unassignedTeams.map(tm => <TeamRow key={tm.teamId} team={tm} />)}
            </div>
          </div>
        ))}
        {/* Create-track form — NV3: locked once registration closes. Shown only
            in DRAFT/OPEN. PHẦN 1: in SETUP we render nothing (no helper text);
            other locked phases keep a short explanation. Collapsed behind
            "+ ADD TRACK" so the list stays clean; kept open after a successful
            ADD since tracks are usually created in batches. */}
        {trackCreationAllowed ? (
          showAddTrack ? (
            <div style={{ padding: 14, background: C.surface, border: `1px solid ${C.border}` }}>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 2fr auto auto", gap: 10, alignItems: "end" }}>
                <PixelInput label="Name" value={trkName} onChange={(e) => setTrkName(e.target.value)} placeholder="Track name" />
                <PixelInput label="Description" value={trkDesc} onChange={(e) => setTrkDesc(e.target.value)} placeholder="What is this track about?" />
                <PixelButton variant="secondary" onClick={addTrack}>ADD</PixelButton>
                <PixelButton variant="ghost" onClick={() => { setShowAddTrack(false); setTrkName(""); setTrkDesc(""); }}>CANCEL</PixelButton>
              </div>
            </div>
          ) : (
            <div>
              <PixelButton variant="secondary" onClick={() => setShowAddTrack(true)}>+ ADD TRACK</PixelButton>
            </div>
          )
        ) : event.status === 'SETUP' ? null : (
          <div style={{ padding: 12, background: C.surface, border: `1px dashed ${C.border}`, color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, lineHeight: 1.6 }}>
            {`Tracks can only be created while the event is in DRAFT or OPEN (current: ${event.status}).`}
          </div>
        )}
      </div>
  );
}
