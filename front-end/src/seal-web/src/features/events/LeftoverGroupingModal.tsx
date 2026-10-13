import { useCallback, useEffect, useState } from "react";
import { useDrag, useDrop } from "react-dnd";
import { C, PixelButton, PixelBadge } from "@/shared/components/PixelComponents";
import {
  teamsApi, apiErrorMessage,
  type GroupingPreview, type GroupingProposedTeam, type GroupingWarning, type GroupingMember,
  type GroupingCommitResult, type Team,
} from "@/shared/apiClient";

const MONO = "'JetBrains Mono', monospace";
const MIN_TEAM_MEMBERS = 3;
const MAX_TEAM_MEMBERS = 5;

// react-dnd payload + type for dragging a leftover person onto a team (or the
// "force new team" zone) — same pattern as the track-assignment drag-and-drop.
const PERSON_DND_TYPE = "LEFTOVER_PERSON";
interface PersonDragItem { userId: number; }

// react-dnd payload + type for dragging a person BETWEEN Proposed Teams cards.
// Deliberately a separate type from PERSON_DND_TYPE so the two drag-and-drop
// systems (warnings vs. proposed teams) never accept each other's drops.
const PROPOSED_MEMBER_DND_TYPE = "PROPOSED_TEAM_MEMBER";
interface ProposedMemberDragItem { userId: number; fromKey: string; }

// A Proposed Teams card, locally editable by drag-and-drop before APPLY is sent.
// `key` is stable across edits (existingTeamId, or `new-{index}` for a brand-new
// team) — member counts change but the set of cards never does.
interface EditableTeam {
  key: string;
  existingTeamId: number | null;
  teamName: string;
  members: GroupingMember[];
  // userIds that are newly merged into this card — every member of a brand-new
  // team, or just the delta absorbed into an existing (GROWN) team's original
  // seed. Dragging a person into a card marks them "new" here too, since
  // they're now an atom the coordinator merged in by hand.
  addedMemberIds: Set<number>;
}

function toEditableTeams(proposedTeams: GroupingProposedTeam[]): EditableTeam[] {
  return proposedTeams.map((t, i) => ({
    key: t.existingTeamId != null ? `existing-${t.existingTeamId}` : `new-${i}`,
    existingTeamId: t.existingTeamId ?? null,
    teamName: t.teamName,
    members: t.members.length ? [...t.members] : [...t.addedMembers],
    addedMemberIds: new Set(t.addedMembers.map(m => m.userId)),
  }));
}

// Shared phrasing for both APPLY GROUPING and a manual placement.
function summarizeGrouping(r: GroupingCommitResult): string {
  return `${r.teamsCreated} team(s) created, ${r.teamsGrown} grown, `
    + `${r.peoplePlaced} participant(s) placed`
    + (r.unresolvedWarnings > 0 ? `, ${r.unresolvedWarnings} left for manual handling.` : ".");
}

interface Props {
  eventId: number;
  eventName: string;
  // The event's current teams — drives the drag-and-drop target zones (existing
  // team cards + "force new team"). Refreshed by the parent after every placement.
  teams: Team[];
  onClose: () => void;
  // Called after a successful full commit so the parent can close the modal + refresh.
  onCommitted: (summary: string) => void;
  // Called after a successful manual placement — the modal stays open (the coordinator
  // may resolve several warnings in one sitting), but the parent should still refresh
  // its roster/teams so the drop-target zones and leftover count stay accurate.
  onManualAssigned: (summary: string) => void;
}

/**
 * Coordinator SETUP tool: preview how leftover (under-sized) teams would be grouped
 * into valid ones, then apply it. Preview is a read-only dry run; APPLY is the
 * destructive step (creates/grows teams, dissolves solo teams), so it sits behind
 * a second confirm inside this modal. Warnings the automatic planner couldn't
 * resolve get a manual escape hatch: drag a person from a warning onto a team (or
 * onto "force new team") in <DropTargetPalette> to place them immediately.
 */
export function LeftoverGroupingModal({ eventId, teams, onClose, onCommitted, onManualAssigned }: Props) {
  const [preview, setPreview] = useState<GroupingPreview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [committing, setCommitting] = useState(false);
  const [confirming, setConfirming] = useState(false);
  // Drag-and-drop manual placement: which person (if any) is currently in flight,
  // and the last placement error. Only one placement runs at a time — chips are
  // disabled while placingUserId is set, so drops can't race each other.
  const [placingUserId, setPlacingUserId] = useState<number | null>(null);
  const [placeError, setPlaceError] = useState<string | null>(null);
  // The coordinator's locally-edited copy of preview.proposedTeams — re-seeded
  // from the server every time the preview reloads (a Warnings-flow placement can
  // change the underlying pool, so in-progress card edits don't survive that).
  const [editedTeams, setEditedTeams] = useState<EditableTeam[]>([]);

  const load = useCallback((opts?: { silent?: boolean }) => {
    if (!opts?.silent) setLoading(true);
    setError(null);
    return teamsApi.leftoverGroupingPreview(eventId)
      .then(res => setPreview(res.data))
      .catch(err => setError(apiErrorMessage(err, "Failed to load grouping preview.")))
      .finally(() => { if (!opts?.silent) setLoading(false); });
  }, [eventId]);

  useEffect(() => { load(); }, [load]);

  useEffect(() => {
    setEditedTeams(preview ? toEditableTeams(preview.proposedTeams) : []);
  }, [preview]);

  // Moves a person between two Proposed Teams cards, purely client-side — nothing
  // is persisted until CONFIRM APPLY sends the whole edited plan in one call.
  const moveMember = useCallback((userId: number, fromKey: string, toKey: string) => {
    if (fromKey === toKey) return;
    setEditedTeams(prev => {
      const next = prev.map(t => ({ ...t, members: t.members.slice(), addedMemberIds: new Set(t.addedMemberIds) }));
      const from = next.find(t => t.key === fromKey);
      const to = next.find(t => t.key === toKey);
      if (!from || !to) return prev;
      const idx = from.members.findIndex(m => m.userId === userId);
      if (idx === -1) return prev;
      const [person] = from.members.splice(idx, 1);
      from.addedMemberIds.delete(userId);
      to.members.push(person);
      to.addedMemberIds.add(userId); // now an atom merged into this card by hand
      return next;
    });
  }, []);

  // Escape closes (never commits) while nothing is in flight.
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape" && !committing) onClose();
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [committing, onClose]);

  async function commit() {
    setCommitting(true);
    setError(null);
    try {
      const teams = editedTeams
        .filter(t => t.members.length > 0)
        .map(t => ({ existingTeamId: t.existingTeamId, memberUserIds: t.members.map(m => m.userId) }));
      const res = await teamsApi.leftoverGroupingApplyPlan(eventId, { teams });
      onCommitted(summarizeGrouping(res.data));
    } catch (err) {
      setError(apiErrorMessage(err, "Failed to apply grouping."));
      setConfirming(false);
      setCommitting(false);
    }
  }

  // Drop handler shared by every team zone + the "force new team" zone: places one
  // dragged person immediately (targetTeamId = null force-approves them solo).
  async function placePerson(userId: number, targetTeamId: number | null) {
    setPlacingUserId(userId);
    setPlaceError(null);
    try {
      const res = await teamsApi.leftoverGroupingManualAssign(eventId, { userIds: [userId], targetTeamId });
      onManualAssigned(summarizeGrouping(res.data));
      load({ silent: true });
    } catch (err) {
      setPlaceError(apiErrorMessage(err, "Failed to place the participant."));
    } finally {
      setPlacingUserId(null);
    }
  }

  const nothingToDo = !loading && !error && preview
    && preview.proposedTeams.length === 0 && preview.warnings.length === 0;
  const nonEmptyTeams = editedTeams.filter(t => t.members.length > 0);
  const invalidTeams = nonEmptyTeams.filter(t => t.members.length < MIN_TEAM_MEMBERS);
  const canApply = !loading && !error && nonEmptyTeams.length > 0 && invalidTeams.length === 0;

  return (
    <>
      <div
        onClick={() => { if (!committing) onClose(); }}
        style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.65)", zIndex: 400, backdropFilter: "blur(2px)" }}
      />
      <div
        role="dialog"
        aria-modal="true"
        style={{
          position: "fixed", top: "50%", left: "50%", transform: "translate(-50%, -50%)", zIndex: 401,
          width: "min(640px, calc(100vw - 32px))", maxHeight: "calc(100vh - 64px)", overflowY: "auto",
          background: C.surface, border: `1px solid ${C.green}66`,
          boxShadow: `0 0 40px ${C.greenGlow}, 0 16px 48px rgba(0,0,0,0.4)`, padding: 28,
        }}
      >
        <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${C.green}, transparent)` }} />

        <h2 style={{
          fontFamily: MONO, fontSize: 18, fontWeight: 800, marginBottom: 18, width: "fit-content",
          background: "linear-gradient(135deg, #22c55e 0%, #3b82f6 100%)",
          WebkitBackgroundClip: "text", backgroundClip: "text",
          WebkitTextFillColor: "transparent", color: "transparent",
        }}>
          Group Leftover Configuration
        </h2>

        {loading && (
          <div style={{ color: C.textMuted, fontFamily: MONO, fontSize: 12, padding: "24px 0" }}>Loading preview…</div>
        )}

        {error && (
          <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: MONO, fontSize: 11, padding: "8px 12px", marginBottom: 16 }}>
            ERROR: {error}
          </div>
        )}

        {nothingToDo && (
          <div style={{ color: C.textMuted, fontFamily: MONO, fontSize: 12, padding: "16px 0", lineHeight: 1.7 }}>
            ✓ No leftover participants — every team already has at least 3 members. Nothing to group.
          </div>
        )}

        {!loading && preview && !nothingToDo && (
          <>
            <div style={{ display: "flex", gap: 24, flexWrap: "wrap", marginBottom: 18 }}>
              <Stat label="Leftover people" value={preview.leftoverPeople} />
              <Stat label="Solo entrants" value={preview.soloCount} />
              <Stat label="Pairs kept" value={preview.pairCount} />
            </div>

            {editedTeams.length > 0 && (
              <div style={{ marginBottom: preview.warnings.length ? 18 : 8 }}>
                <SectionLabel>Proposed teams ({editedTeams.length})</SectionLabel>
                <div style={{ fontFamily: MONO, fontSize: 10, color: C.textMuted, marginBottom: 8, lineHeight: 1.6 }}>
                  Drag a person between teams to rearrange before applying. A team must end up with 0
                  or at least {MIN_TEAM_MEMBERS} members.
                </div>
                {invalidTeams.length > 0 && (
                  <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: MONO, fontSize: 11, padding: "6px 10px", marginBottom: 8, lineHeight: 1.6 }}>
                    ⚠ {invalidTeams.map(t => `'${t.teamName}' has only ${t.members.length}`).join(", ")}
                    {" "}— move people in or out (0 or {MIN_TEAM_MEMBERS}+) before applying.
                  </div>
                )}
                <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                  {editedTeams.map(t => (
                    <ProposedTeamCard key={t.key} team={t} onMoveMember={moveMember} disabled={committing} />
                  ))}
                </div>
              </div>
            )}

            {preview.warnings.length > 0 && (
              <div style={{ marginBottom: 8 }}>
                <SectionLabel>Needs your attention ({preview.warnings.length})</SectionLabel>
                <div style={{ fontFamily: MONO, fontSize: 10, color: C.textMuted, marginBottom: 8, lineHeight: 1.6 }}>
                  Drag a person onto a team to place them there, or onto "+ Force new team" to approve them solo.
                </div>
                <DropTargetPalette teams={teams} onDrop={placePerson} />
                {placeError && (
                  <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: MONO, fontSize: 11, padding: "6px 10px", margin: "8px 0" }}>
                    {placeError}
                  </div>
                )}
                <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                  {preview.warnings.map((w, i) => (
                    <WarningRow key={i} warning={w} disabled={placingUserId !== null} />
                  ))}
                </div>
              </div>
            )}
          </>
        )}

        {confirming && !nothingToDo && (
          <div style={{ background: "rgba(234,179,8,0.08)", border: "1px solid rgba(234,179,8,0.4)", color: C.yellow, fontFamily: MONO, fontSize: 11, lineHeight: 1.7, padding: "10px 12px", margin: "16px 0" }}>
            ⚠ This will create/grow the teams shown above (including any rearranging you did) and
            dissolve any team left with 0 members. It can't be auto-undone. Apply?
          </div>
        )}

        <div style={{ display: "flex", gap: 10, marginTop: 20 }}>
          {!nothingToDo && !confirming && (
            <PixelButton variant="cyber" onClick={() => setConfirming(true)} disabled={!canApply}>
              APPLY GROUPING
            </PixelButton>
          )}
          {!nothingToDo && confirming && (
            <PixelButton variant="cyber" onClick={commit} disabled={committing || !canApply}>
              {committing ? "APPLYING…" : "CONFIRM APPLY"}
            </PixelButton>
          )}
          <PixelButton variant="secondary" onClick={onClose} disabled={committing}>
            {nothingToDo ? "CLOSE" : "CANCEL"}
          </PixelButton>
        </div>
      </div>
    </>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <div style={{ fontFamily: MONO, fontSize: 22, fontWeight: 800, color: C.green, lineHeight: 1 }}>{value}</div>
      <div style={{ fontFamily: MONO, fontSize: 10, color: C.textMuted, marginTop: 4, letterSpacing: "0.05em", textTransform: "uppercase" }}>{label}</div>
    </div>
  );
}

function SectionLabel({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ fontFamily: MONO, fontSize: 11, fontWeight: 700, color: C.textMuted, textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 8 }}>
      {children}
    </div>
  );
}

// One Proposed Teams card: both a drag source (each member row) and a drop target
// (accepts a member dragged from a different card). Purely local state — nothing
// is sent to the server until CONFIRM APPLY.
function ProposedTeamCard({ team, onMoveMember, disabled }: {
  team: EditableTeam;
  onMoveMember: (userId: number, fromKey: string, toKey: string) => void;
  disabled: boolean;
}) {
  const room = MAX_TEAM_MEMBERS - team.members.length;
  const [{ isOver, canDrop }, dropRef] = useDrop(() => ({
    accept: PROPOSED_MEMBER_DND_TYPE,
    canDrop: (item: ProposedMemberDragItem) => !disabled && (item.fromKey === team.key || room > 0),
    drop: (item: ProposedMemberDragItem) => onMoveMember(item.userId, item.fromKey, team.key),
    collect: (monitor) => ({ isOver: monitor.isOver(), canDrop: monitor.canDrop() }),
  }), [team.key, room, disabled, onMoveMember]);

  const invalid = team.members.length > 0 && team.members.length < MIN_TEAM_MEMBERS;
  const active = isOver && canDrop;

  return (
    <div ref={(node) => { dropRef(node); }}
      style={{
        padding: "10px 12px", background: C.surface2,
        border: `1px solid ${invalid ? C.red : active ? C.green : C.border}`,
        outline: active ? `1px dashed ${C.green}` : "none",
        transition: "background 0.12s",
      }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6 }}>
        <PixelBadge color={team.existingTeamId == null ? "blue" : "green"}>
          {team.existingTeamId == null ? "NEW" : "GROWN"}
        </PixelBadge>
        <span style={{ fontFamily: MONO, fontSize: 13, fontWeight: 700, color: C.text }}>{team.teamName}</span>
        <span style={{ fontFamily: MONO, fontSize: 11, color: invalid ? C.red : C.textMuted }}>
          · {team.members.length} member{team.members.length === 1 ? "" : "s"}{invalid ? ` (below ${MIN_TEAM_MEMBERS})` : ""}
        </span>
      </div>
      {team.members.length === 0 ? (
        <div style={{ fontFamily: MONO, fontSize: 10, color: C.textMuted, fontStyle: "italic" }}>
          Empty — won't be created.
        </div>
      ) : (
        <table style={{ width: "100%", borderCollapse: "collapse", fontFamily: MONO, fontSize: 11 }}>
          <tbody>
            {team.members.map((m, i) => (
              <DraggableProposedMemberRow
                key={m.userId} member={m} index={i} teamKey={team.key} disabled={disabled}
                isNew={team.addedMemberIds.has(m.userId)}
              />
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function DraggableProposedMemberRow({ member, index, teamKey, disabled, isNew }: {
  member: GroupingMember; index: number; teamKey: string; disabled: boolean; isNew: boolean;
}) {
  const [{ isDragging }, dragRef] = useDrag(() => ({
    type: PROPOSED_MEMBER_DND_TYPE,
    item: { userId: member.userId, fromKey: teamKey } as ProposedMemberDragItem,
    canDrag: !disabled,
    collect: (monitor) => ({ isDragging: monitor.isDragging() }),
  }), [member.userId, teamKey, disabled]);

  return (
    <tr ref={(node) => { if (!disabled) dragRef(node); }}
      style={{
        borderTop: index === 0 ? "none" : `1px solid ${C.border}`,
        cursor: disabled ? "default" : "grab", opacity: isDragging ? 0.35 : 1,
      }}>
      <td style={{ color: C.textMuted, padding: "5px 8px 5px 0", width: 22, textAlign: "right" }}>
        {index + 1}
      </td>
      <td style={{ color: C.text, padding: "5px 0" }}>
        <span style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
          {member.fullName}
          {isNew && (
            <span style={{
              fontFamily: MONO, fontSize: 9, fontWeight: 700, letterSpacing: "0.05em",
              color: "#60a5fa", background: "rgba(59,130,246,0.1)", border: "1px solid rgba(59,130,246,0.35)",
              padding: "1px 5px",
            }}>
              NEW
            </span>
          )}
        </span>
      </td>
    </tr>
  );
}

// A warning the automatic planner couldn't resolve. Each listed person is a
// draggable chip — drag one onto a zone in <DropTargetPalette> to place them.
function WarningRow({ warning, disabled }: { warning: GroupingWarning; disabled: boolean }) {
  return (
    <div style={{ padding: "10px 12px", background: "rgba(234,179,8,0.06)", border: "1px solid rgba(234,179,8,0.35)" }}>
      <div style={{ fontFamily: MONO, fontSize: 11, color: C.yellow, lineHeight: 1.7, marginBottom: warning.people.length ? 8 : 0 }}>⚠ {warning.message}</div>
      {warning.people.length > 0 && (
        <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
          {warning.people.map(m => <DraggablePersonChip key={m.userId} member={m} disabled={disabled} />)}
        </div>
      )}
    </div>
  );
}

// A leftover person's name, draggable onto any zone in <DropTargetPalette>.
function DraggablePersonChip({ member, disabled }: { member: GroupingMember; disabled: boolean }) {
  const [{ isDragging }, dragRef] = useDrag(() => ({
    type: PERSON_DND_TYPE,
    item: { userId: member.userId } as PersonDragItem,
    canDrag: !disabled,
    collect: (monitor) => ({ isDragging: monitor.isDragging() }),
  }), [member.userId, disabled]);

  return (
    <div ref={(node) => { if (!disabled) dragRef(node); }}
      style={{
        display: "inline-flex", alignItems: "center", gap: 6,
        padding: "4px 10px", background: C.surface2, border: `1px solid ${C.border}`,
        fontFamily: MONO, fontSize: 11, color: C.text, userSelect: "none",
        cursor: disabled ? "default" : "grab", opacity: isDragging ? 0.35 : disabled ? 0.5 : 1,
      }}>
      <span style={{ color: C.textMuted }}>⠿</span>{member.fullName}
    </div>
  );
}

// The full set of drop targets a dragged person can land on: every approved team
// of the event (greyed out + undroppable once full) plus a "force new team" zone.
function DropTargetPalette({ teams, onDrop }: { teams: Team[]; onDrop: (userId: number, targetTeamId: number | null) => void }) {
  const approved = teams.filter(t => t.status === "APPROVED");
  return (
    <div style={{ display: "flex", gap: 8, flexWrap: "wrap", padding: "10px 12px", background: C.surface, border: `1px solid ${C.border}`, marginBottom: 10 }}>
      <NewTeamDropZone onDrop={onDrop} />
      {approved.map(t => <TeamDropZone key={t.teamId} team={t} onDrop={onDrop} />)}
    </div>
  );
}

function TeamDropZone({ team, onDrop }: { team: Team; onDrop: (userId: number, targetTeamId: number | null) => void }) {
  const size = team.members?.length ?? 0;
  const room = MAX_TEAM_MEMBERS - size;
  const [{ isOver, canDrop }, dropRef] = useDrop(() => ({
    accept: PERSON_DND_TYPE,
    canDrop: () => room > 0,
    drop: (item: PersonDragItem) => onDrop(item.userId, team.teamId),
    collect: (monitor) => ({ isOver: monitor.isOver(), canDrop: monitor.canDrop() }),
  }), [team.teamId, room, onDrop]);

  const active = isOver && canDrop;
  return (
    <div ref={(node) => { dropRef(node); }}
      title={room > 0 ? undefined : "Team is full"}
      style={{
        padding: "6px 10px", fontFamily: MONO, fontSize: 11, whiteSpace: "nowrap",
        border: `1px dashed ${active ? C.green : C.border}`,
        background: active ? "rgba(34,197,94,0.12)" : C.surface2,
        color: room > 0 ? C.text : C.textMuted, opacity: room > 0 ? 1 : 0.45,
        transition: "background 0.12s",
      }}>
      {team.name} <span style={{ color: C.textMuted }}>({size}/{MAX_TEAM_MEMBERS})</span>
    </div>
  );
}

// Dropping here force-approves the dragged person as a solo team (targetTeamId
// null) — the intended way to bypass the recommended MIN for a lone straggler.
function NewTeamDropZone({ onDrop }: { onDrop: (userId: number, targetTeamId: number | null) => void }) {
  const [{ isOver }, dropRef] = useDrop(() => ({
    accept: PERSON_DND_TYPE,
    drop: (item: PersonDragItem) => onDrop(item.userId, null),
    collect: (monitor) => ({ isOver: monitor.isOver() }),
  }), [onDrop]);

  return (
    <div ref={(node) => { dropRef(node); }}
      style={{
        padding: "6px 10px", fontFamily: MONO, fontSize: 11, fontWeight: 700, whiteSpace: "nowrap",
        border: `1px dashed ${isOver ? "#60a5fa" : C.border}`,
        background: isOver ? "rgba(96,165,250,0.14)" : C.surface2,
        color: isOver ? "#60a5fa" : C.textMuted,
        transition: "background 0.12s",
      }}>
      + Force new team
    </div>
  );
}
