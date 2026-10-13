import { useCallback, useEffect, useMemo, useState } from "react";
import { C, GradientText, PixelBadge, PixelButton, PixelCard, PixelInput } from "@/shared/components/PixelComponents";
import { PixelMenu } from "@/shared/components/PixelMenu";
import { ApiError, apiErrorMessage, eventsApi, HackathonEvent, Prize, prizesApi, Round, roundsApi, Team, teamsApi } from "@/shared/apiClient";
import { ConfirmDialog } from "@/shared/components/ConfirmDialog";
import { useNotifications } from "@/app/providers/NotificationProvider";

const mono = "'JetBrains Mono', monospace";

// TOP | PRIZE | TEAM | STATUS
const PRIZE_COLS = ["10%", "32%", "32%", "26%"];

const MEDAL_COLOR: Record<number, string> = {
  1: "#FFD24A",
  2: "#CBD5E1",
  3: "#E0915A",
};

const medalColor = (rank: number) => MEDAL_COLOR[rank] ?? C.green;

// No manual event switcher here — unlike Assignments/Scoring (strictly "the
// one live event", COMPLETED moves to History), this page's own workflow
// still needs a COMPLETED event: participant certificates only export once
// the event has ended (see exportParticipantsCsv below). So prefer the live
// event, but fall back to the most recently completed one rather than
// hiding it — that's the event a coordinator would come back here for.
function pickCurrentEvent(events: HackathonEvent[]): HackathonEvent | null {
  if (events.length === 0) return null;
  const active = events.find((event) => event.status === "IN_PROGRESS")
    ?? events.find((event) => event.status === "COMPLETED");
  return active ?? events[events.length - 1];
}

function csvCell(value: unknown) {
  const text = value == null ? "" : String(value);
  return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

function downloadCsv(filename: string, header: string[], rows: unknown[][]) {
  const lines = [header.join(","), ...rows.map((row) => row.map(csvCell).join(","))];
  const blob = new Blob(["\ufeff" + lines.join("\r\n")], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");

  anchor.href = url;
  anchor.download = filename;
  anchor.click();

  URL.revokeObjectURL(url);
}

// Local edit draft, decoupled from `prize.name` so we can tell "actually
// changed" apart from "just re-rendered with the same server value" on blur.
function PrizeNameField({ prize, disabled, onSave }: { prize: Prize; disabled: boolean; onSave: (name: string) => void }) {
  const [draft, setDraft] = useState(prize.name);

  useEffect(() => setDraft(prize.name), [prize.name]);

  return (
    <PixelInput
      label=""
      value={draft}
      disabled={disabled}
      onChange={(event) => setDraft(event.target.value)}
      onBlur={() => {
        const trimmed = draft.trim();
        if (trimmed && trimmed !== prize.name) onSave(trimmed);
        else setDraft(prize.name);
      }}
    />
  );
}

export function CoordPrizesPage() {
  const { addToast } = useNotifications();
  const [events, setEvents] = useState<HackathonEvent[]>([]);
  const currentEvent = useMemo(() => pickCurrentEvent(events), [events]);
  const selectedEventId = currentEvent?.eventId ?? null;
  const [finalRound, setFinalRound] = useState<Round | null>(null);
  const [teams, setTeams] = useState<Team[]>([]);
  const [prizes, setPrizes] = useState<Prize[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [topNInput, setTopNInput] = useState(3);
  const [confirmAnnounce, setConfirmAnnounce] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState<Prize | null>(null);

  useEffect(() => {
    setLoading(true);
    setLoadError(null);

    eventsApi.getAll()
      .then((response) => setEvents(response.data ?? []))
      .catch((err) => setLoadError(err instanceof ApiError ? err.message : "Failed to load events."))
      .finally(() => setLoading(false));
  }, []);

  const reloadPrizes = useCallback((eventId: number) =>
    prizesApi.getAll(eventId)
      .then((response) => setPrizes(response.data ?? []))
      .catch(() => setPrizes([])), []);

  useEffect(() => {
    if (selectedEventId == null) {
      setFinalRound(null);
      setTeams([]);
      setPrizes([]);
      return;
    }

    const eventId = selectedEventId;

    roundsApi.getAll(eventId)
      .then((response) => setFinalRound((response.data ?? []).find((round) => round.isFinal) ?? null))
      .catch(() => setFinalRound(null));

    teamsApi.getByEvent(eventId)
      .then((response) => setTeams(response.data ?? []))
      .catch(() => setTeams([]));

    reloadPrizes(eventId);
  }, [selectedEventId, reloadPrizes]);

  const selectedEvent = currentEvent;
  const finalReady = finalRound?.status === "FINALIZED";
  const sortedPrizes = prizes.slice().sort((a, b) => a.rankPosition - b.rankPosition);
  const announced = sortedPrizes.length > 0 && sortedPrizes.every((prize) => prize.announced);
  const allHaveTeam = sortedPrizes.length > 0 && sortedPrizes.every((prize) => prize.teamId != null);

  async function run(action: () => Promise<unknown>, success?: string) {
    if (selectedEventId == null) return;

    setBusy(true);
    try {
      await action();
      await reloadPrizes(selectedEventId);

      if (success) {
        addToast({ type: "success", title: "DONE", message: success });
      }
    } catch (err) {
      addToast({ type: "warning", title: "FAILED", message: apiErrorMessage(err, "Action failed.") });
    } finally {
      setBusy(false);
    }
  }

  function autoGenerate() {
    if (selectedEventId == null) return;

    if (!finalReady) {
      addToast({ type: "warning", title: "FINAL NOT READY", message: "Finalize the final round first." });
      return;
    }

    if (finalRound?.topNAdvance != null && topNInput > finalRound.topNAdvance) {
      addToast({ type: "warning", title: "TOP N TOO HIGH", message: `Top N (${topNInput}) exceeds the final round's winner cutoff (${finalRound.topNAdvance}). Lower Top N or update the round configuration.` });
      return;
    }

    run(() => prizesApi.autoGenerate(selectedEventId, topNInput), `Generated top ${topNInput} from the final ranking.`);
  }

  // The only editable field — a prize's winning team and rank always come
  // from AUTO-GENERATE (the final ranking), never a manual pick.
  function renamePrize(prize: Prize, nextName: string) {
    if (selectedEventId == null) return;

    const trimmed = nextName.trim();
    if (!trimmed || trimmed === prize.name) return;

    run(() => prizesApi.update(selectedEventId, prize.prizeId, { name: trimmed }), `"${prize.name}" renamed to "${trimmed}".`);
  }

  async function deletePrize(prizeId: number) {
    if (selectedEventId == null) return;

    await run(() => prizesApi.remove(selectedEventId, prizeId), "Slot removed.");
    setConfirmDelete(null);
  }

  // Pre-check, then hand over to the type-to-confirm dialog.
  function requestAnnounce() {
    if (selectedEventId == null) return;

    if (!allHaveTeam) {
      addToast({ type: "warning", title: "INCOMPLETE", message: "Every prize must have a winning team first." });
      return;
    }

    setConfirmAnnounce(true);
  }

  async function announce() {
    if (selectedEventId == null) return;

    await run(() => prizesApi.announce(selectedEventId), "Prizes announced - winners notified.");
    setConfirmAnnounce(false);
  }

  const eventSlug = (selectedEvent?.name ?? "event").replace(/\s+/g, "_");

  function exportWinnersCsv() {
    const eventName = selectedEvent?.name ?? "";
    const rows = sortedPrizes
      .filter((prize) => prize.teamId != null)
      .map((prize) => [
        prize.rankPosition,
        prize.name,
        prize.teamName ?? "",
        prize.teamTrackName ?? "",
        prize.finalScore ?? "",
        prize.awardedAt ?? "",
        eventName,
        `This certifies that team ${prize.teamName ?? ""} received the ${prize.name} award at ${eventName}.`,
      ]);

    if (rows.length === 0) return;

    downloadCsv(
      `winners-${eventSlug}.csv`,
      ["rank", "prize", "team", "track", "final_score", "awarded_at", "event", "certificate_statement"],
      rows,
    );
  }

  function exportParticipantsCsv() {
    if (selectedEvent?.status !== "COMPLETED") {
      addToast({ type: "warning", title: "EVENT NOT ENDED", message: "Participant certificates are available only after the event has ended." });
      return;
    }

    const eventName = selectedEvent.name;
    const rows = teams.flatMap((team) => (team.members ?? []).map((member) => {
      const role = (member.memberRole ?? member.role) === "LEADER" ? "Team Leader" : "Member";

      return [
        member.fullName,
        member.email,
        team.name,
        team.trackName ?? "",
        role,
        eventName,
        `This is to certify that ${member.fullName} participated in ${eventName} as ${role} of team ${team.name}.`,
      ];
    }));

    if (rows.length === 0) {
      addToast({ type: "warning", title: "NO PARTICIPANTS", message: "This event has no team members yet." });
      return;
    }

    downloadCsv(
      `participants-${eventSlug}.csv`,
      ["full_name", "email", "team", "track", "role", "event", "certificate_statement"],
      rows,
    );
  }

  return (
    <div style={{ padding: 24, display: "flex", flexDirection: "column", gap: 20 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", flexWrap: "wrap", gap: 12 }}>
        <h1 style={{ fontFamily: mono, fontSize: 28, fontWeight: 800 }}>
          <GradientText>Awards</GradientText>
        </h1>
        {selectedEvent && (
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <span style={{ color: C.text, fontFamily: mono, fontSize: 13, fontWeight: 700 }}>{selectedEvent.name}</span>
            <PixelBadge color={selectedEvent.status === "IN_PROGRESS" ? "green" : "gray"}>{selectedEvent.status}</PixelBadge>
            <PixelBadge color={announced ? "green" : "gray"}>{announced ? "ANNOUNCED" : "DRAFT"}</PixelBadge>
          </div>
        )}
      </div>

      {loadError && (
        <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: mono, fontSize: 11, padding: "10px 14px" }}>
          ERROR: {loadError}
        </div>
      )}

      {!loading && selectedEvent == null && (
        <div style={{ background: C.surface, border: `1px solid ${C.border}`, borderRadius: 6, padding: 18, color: C.textMuted, fontFamily: mono, fontSize: 13 }}>
          No event to show awards for yet.
        </div>
      )}

      {loading ? (
        <PixelCard style={{ padding: 40, textAlign: "center" }}>
          <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 13 }}>Loading...</div>
        </PixelCard>
      ) : selectedEvent == null ? null : (
        <>
          <PixelCard style={{ padding: 16, display: "flex", flexDirection: "column", gap: 14 }}>
            <GradientText style={{ fontFamily: mono, fontSize: 22, fontWeight: 800, letterSpacing: "0.04em" }}>
              PRIZE AWARDS
            </GradientText>

            {!finalReady && (
              <div style={{ color: C.yellow, fontFamily: mono, fontSize: 12 }}>
                The final round is not finalized yet - finalize it before generating prizes.
              </div>
            )}

            {finalReady && !announced && (
              <div style={{ display: "flex", gap: 16, alignItems: "flex-end", flexWrap: "wrap" }}>
                <div style={{ maxWidth: 140 }}>
                  <PixelInput label="Top N" type="number" value={String(topNInput)} min="1" onChange={(event) => setTopNInput(Math.max(1, Number(event.target.value) || 1))} />
                </div>
                <PixelButton variant="cyber" onClick={autoGenerate} disabled={busy}>AUTO-GENERATE FROM FINAL</PixelButton>
              </div>
            )}

            {announced && (
              <div style={{ color: C.green, fontFamily: mono, fontSize: 12 }}>
                Prizes announced - each winning team has been notified.
              </div>
            )}
          </PixelCard>

          {sortedPrizes.length === 0 ? (
            <PixelCard style={{ padding: 40, textAlign: "center" }}>
              <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 13 }}>
                No prizes yet. Use Auto-generate from final.
              </div>
            </PixelCard>
          ) : (
            <PixelCard glow gradient glowColor="amber" style={{ padding: 0, overflow: "hidden" }}>
              <div style={{ overflowX: "auto" }}>
                <table style={{ width: "100%", tableLayout: "fixed", borderCollapse: "collapse", fontFamily: mono }}>
                  <thead>
                    <tr style={{ background: C.surface2, borderBottom: `1px solid ${C.border}` }}>
                      {["Top", "Prize", "Team", "Status"].map((h, i) => (
                        <th key={h} style={{ width: PRIZE_COLS[i], color: C.green, fontSize: 10, letterSpacing: "0.12em", textAlign: "left", padding: "12px 14px", fontWeight: 600, textTransform: "uppercase" }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {sortedPrizes.map((prize, ri) => {
                      const locked = prize.announced;

                      return (
                        <tr key={prize.prizeId} className="row-actionable" style={{ borderBottom: "1px solid rgba(34,197,94,0.06)", background: ri % 2 === 0 ? C.surface : C.surface2 }}>
                          <td style={{ padding: "12px 14px" }}>
                            <span style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", width: 30, height: 30, borderRadius: "50%", background: medalColor(prize.rankPosition), color: "#0d1117", fontWeight: 800, fontFamily: mono, fontSize: 13 }}>
                              {prize.rankPosition}
                            </span>
                          </td>

                          <td style={{ padding: "12px 14px" }}>
                            {locked ? (
                              <div style={{ color: C.text, fontFamily: mono, fontSize: 14, fontWeight: 700 }}>{prize.name}</div>
                            ) : (
                              <PrizeNameField prize={prize} disabled={busy} onSave={(name) => renamePrize(prize, name)} />
                            )}
                          </td>

                          {/* Winning team is read-only everywhere — it only ever comes
                              from AUTO-GENERATE FROM FINAL, never a manual pick. Just the
                              name, bold and bright — the score isn't the point here. */}
                          <td style={{ padding: "12px 14px" }}>
                            <div style={{ color: prize.teamId ? C.text : C.textMuted, fontFamily: mono, fontSize: 14, fontWeight: 800 }}>
                              {prize.teamName ?? "-"}
                            </div>
                          </td>

                          <td style={{ padding: "12px 14px" }}>
                            <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                              <PixelBadge color={prize.announced ? "green" : "gray"}>{prize.announced ? "ANNOUNCED" : "DRAFT"}</PixelBadge>
                              {!locked && (
                                <span className="row-action">
                                  <PixelButton variant="danger" size="sm" onClick={() => setConfirmDelete(prize)} disabled={busy}>DELETE</PixelButton>
                                </span>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </PixelCard>
          )}

          {/* Functional actions live below the table: finalizing (Announce) and
              exporting are things you do once the list above is settled. The two
              CSV exports share one menu trigger just to cut visual clutter — they
              stay two separate actions underneath. */}
          <div style={{ display: "flex", gap: 12, alignItems: "center", justifyContent: "flex-end", flexWrap: "wrap" }}>
            {finalReady && !announced && (
              <PixelButton variant="primary" size="md" onClick={requestAnnounce} disabled={busy || sortedPrizes.length === 0 || !allHaveTeam}>ANNOUNCE</PixelButton>
            )}
            <PixelMenu
              label="EXPORT CSV"
              triggerVariant="secondary"
              size="md"
              ariaLabel="Export CSV"
              align="right"
              disabled={busy}
              items={[
                { label: "Export winners CSV", onClick: exportWinnersCsv, disabled: !sortedPrizes.some((prize) => prize.teamId != null) },
                { label: "Export participants CSV", onClick: exportParticipantsCsv, disabled: teams.length === 0 || selectedEvent.status !== "COMPLETED" },
              ]}
            />
          </div>
        </>
      )}

      {confirmAnnounce && selectedEvent && (
        <ConfirmDialog
          title="Announce these prizes?"
          message={`Publish all ${sortedPrizes.length} prize(s) of "${selectedEvent.name}" and notify every winning team.`}
          warning="Cannot be undone - every winning team is notified immediately and the prize list locks."
          confirmLabel="ANNOUNCE PRIZES"
          variant="cyber"
          requireTypedText={selectedEvent.name}
          working={busy}
          onConfirm={announce}
          onClose={() => { if (!busy) setConfirmAnnounce(false); }}
        />
      )}
      {confirmDelete && (
        <ConfirmDialog
          title="Delete this prize slot?"
          message={`"${confirmDelete.name}" (rank #${confirmDelete.rankPosition}) will be removed from the prize list.`}
          confirmLabel="DELETE SLOT"
          variant="danger"
          working={busy}
          onConfirm={() => deletePrize(confirmDelete.prizeId)}
          onClose={() => { if (!busy) setConfirmDelete(null); }}
        />
      )}
    </div>
  );
}