import { useCallback, useEffect, useState } from "react";
import { C, GradientText, PixelBadge, PixelButton, PixelCard, PixelInput } from "@/shared/components/PixelComponents";
import { ApiError, apiErrorMessage, MyTeam, Round, Submission, SubmissionEligibility, roundsApi, submissionsApi, teamsApi } from "@/shared/apiClient";
import { useNotifications } from "@/app/providers/NotificationProvider";
import { useAuth } from "@/app/providers/AuthProvider";
import { useRoundTimer } from "@/shared/hooks/useRoundTimer";
import { CountdownDisplay } from "@/shared/components/CountdownDisplay";

const mono = "'JetBrains Mono', monospace";

function fmtDateTime(iso?: string) {
  if (!iso) return "—";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  return `${String(d.getDate()).padStart(2, "0")}/${String(d.getMonth() + 1).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

function eventOptionLabel(team: MyTeam): string {
  const name = team.eventName ?? `Event #${team.eventId ?? "-"}`;
  const status = team.eventStatus
    ? team.eventStatus.toLowerCase().replace(/(^|_)([a-z])/g, (_, sep, char) => `${sep === "_" ? " " : ""}${char.toUpperCase()}`)
    : null;

  return status ? `${name} (${status})` : name;
}

export function TeamSubmitPage() {
  const { addToast } = useNotifications();
  const { clearTeam } = useAuth();

  const [team, setTeam] = useState<MyTeam | null>(null);
  const [teamHistory, setTeamHistory] = useState<MyTeam[]>([]);
  const [selectedEventId, setSelectedEventId] = useState<number | null>(null);
  const [rounds, setRounds] = useState<Round[]>([]);
  const [selectedRoundId, setSelectedRoundId] = useState<number | null>(null);
  const [existing, setExisting] = useState<Submission | null>(null);
  const [eligibility, setEligibility] = useState<SubmissionEligibility | null>(null);
  const [eligibilityLoading, setEligibilityLoading] = useState(false);
  const [eligibilityError, setEligibilityError] = useState<string | null>(null);

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [repoUrl, setRepoUrl] = useState("");
  const [demoUrl, setDemoUrl] = useState("");
  const [slideUrl, setSlideUrl] = useState("");
  const [description, setDescription] = useState("");

  const isLeader = team?.myRole === "LEADER";
  const timer = useRoundTimer(team?.eventId ?? null, selectedRoundId, "CONTEST", { fireBanners: true });

  function clearSubmissionForm() {
    setExisting(null);
    setRepoUrl("");
    setDemoUrl("");
    setSlideUrl("");
    setDescription("");
  }

  async function loadRoundsForTeam(nextTeam: MyTeam, cancelled = false) {
    if (nextTeam.eventId == null) {
      setRounds([]);
      setSelectedRoundId(null);
      return;
    }

    const loadedRounds = await roundsApi.getAll(nextTeam.eventId)
      .then((response) => response.data ?? [])
      .catch(() => []);

    if (cancelled) return;

    const sorted = [...loadedRounds].sort((a, b) => (a.orderNumber ?? a.roundId) - (b.orderNumber ?? b.roundId));
    const open = sorted.find((round) => ["ACTIVE", "OPEN"].includes((round.status ?? "").toUpperCase()));

    setRounds(sorted);
    setSelectedRoundId((open ?? sorted[0])?.roundId ?? null);
  }

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setLoadError(null);

    (async () => {
      try {
        const history = (await teamsApi.getMyHistory()).data ?? [];

        if (cancelled) return;

        setTeamHistory(history);

        const firstTeam = history[0] ?? null;
        setTeam(firstTeam);
        setSelectedEventId(firstTeam?.eventId ?? null);

        if (firstTeam) {
          await loadRoundsForTeam(firstTeam, cancelled);
        } else {
          setRounds([]);
          setSelectedRoundId(null);
        }
      } catch (err) {
        if (!cancelled) {
          if (err instanceof ApiError && err.status === 404) {
            setLoadError("You are not part of any team yet.");
            clearTeam();
          } else {
            setLoadError(err instanceof ApiError ? err.message : "Failed to load.");
          }
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [clearTeam]);

  async function handleEventChange(eventId: number) {
    setLoading(true);
    setLoadError(null);
    setActionError(null);
    setNotice(null);
    clearSubmissionForm();

    try {
      const nextTeam = (await teamsApi.getMyForEvent(eventId)).data;

      setTeam(nextTeam);
      setSelectedEventId(nextTeam.eventId ?? eventId);
      setTeamHistory((previous) => previous.map((item) => item.eventId === eventId ? nextTeam : item));

      await loadRoundsForTeam(nextTeam);
    } catch (err) {
      setLoadError(err instanceof ApiError ? err.message : "Failed to load this event.");
    } finally {
      setLoading(false);
    }
  }

  const loadSubmission = useCallback((roundId: number) => {
    submissionsApi.getMyForRound(roundId)
      .then((response) => {
        const submission = response.data;

        setExisting(submission ?? null);
        setRepoUrl(submission?.repoUrl ?? "");
        setDemoUrl(submission?.demoUrl ?? "");
        setSlideUrl(submission?.slideUrl ?? "");
        setDescription(submission?.description ?? "");
      })
      .catch(() => {
        clearSubmissionForm();
      });
  }, []);

  useEffect(() => {
    let cancelled = false;
    setNotice(null);
    setActionError(null);
    setEligibility(null);
    setEligibilityError(null);

    if (selectedRoundId != null) {
      loadSubmission(selectedRoundId);
      setEligibilityLoading(true);
      submissionsApi.getMyEligibility(selectedRoundId)
        .then((response) => {
          if (!cancelled) setEligibility(response.data);
        })
        .catch((err) => {
          if (!cancelled) {
            setEligibility(null);
            setEligibilityError(apiErrorMessage(err, "Round eligibility could not be verified."));
          }
        })
        .finally(() => {
          if (!cancelled) setEligibilityLoading(false);
        });
    } else {
      clearSubmissionForm();
      setEligibilityLoading(false);
    }

    return () => { cancelled = true; };
  }, [selectedRoundId, loadSubmission]);

  const selectedRound = rounds.find((round) => round.roundId === selectedRoundId) ?? null;
  const deadlinePassed = selectedRound?.submissionDeadline
    ? new Date(selectedRound.submissionDeadline).getTime() < Date.now()
    : false;
  const teamApproved = (team?.status ?? "").toUpperCase() === "APPROVED";
  // No live season entry for the currently-selected team (past event
  // completed, or disqualified) — same condition TeamViewPage/
  // ExistingTeamDashboard gate their dormant notices on. Reached directly by
  // URL since the nav item itself is already hidden for a dormant team.
  const teamDormant = (team?.status ?? "").toUpperCase() === "DISQUALIFIED"
    || (team?.eventStatus ?? "").toUpperCase() === "COMPLETED";
  const roundOpen = ["ACTIVE", "OPEN"].includes((selectedRound?.status ?? "").toUpperCase());
  const eventAllowsSubmit = ["OPEN", "IN_PROGRESS"].includes((team?.eventStatus ?? "").toUpperCase());
  const timerAllowsSubmit = !timer.loading && !timer.loadFailed && timer.isRunning;
  const timerBlocks = !timerAllowsSubmit;
  const eligibilityAllowsSubmit = !eligibilityLoading && !eligibilityError && eligibility?.eligible === true;
  const canSubmit = isLeader && teamApproved && roundOpen && eventAllowsSubmit
    && !deadlinePassed && timerAllowsSubmit && eligibilityAllowsSubmit;
  const timerLockMessage = timer.loading
    ? "Synchronizing the contest timer - submissions remain locked."
    : timer.loadFailed
      ? "The contest timer could not be verified - submissions remain locked."
      : timer.isPaused
        ? "The contest is paused - submissions are temporarily disabled."
        : timer.status === "IDLE"
          ? "Submission time has not started yet."
          : "TIME'S UP - the submission window is closed for this round.";

  async function submit() {
    if (!selectedRoundId) return;

    setActionError(null);
    setNotice(null);

    if (!canSubmit) {
      addToast({ type: "warning", title: "SUBMIT LOCKED", message: "Submission is currently disabled." });
      return;
    }

    if (!repoUrl.trim()) {
      setActionError("Repository URL is required.");
      addToast({ type: "warning", title: "MISSING REPO URL", message: "Repository URL is required." });
      return;
    }

    setBusy(true);

    try {
      await submissionsApi.submit({
        roundId: selectedRoundId,
        repoUrl: repoUrl.trim(),
        demoUrl: demoUrl.trim() || undefined,
        slideUrl: slideUrl.trim() || undefined,
        description: description.trim() || undefined,
      });

      setNotice(existing ? "Submission updated." : "Submission saved.");
      addToast({
        type: "success",
        title: existing ? "SUBMISSION UPDATED" : "SUBMISSION SAVED",
        message: existing ? "Your submission was updated." : "Your project was submitted.",
      });

      loadSubmission(selectedRoundId);
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Failed to submit.");
      addToast({ type: "warning", title: "SUBMIT FAILED", message: apiErrorMessage(err, "Failed to submit.") });
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return (
      <div style={{ padding: 24 }}>
        <PixelCard style={{ padding: 32, textAlign: "center" }}>
          <p style={{ color: C.textMuted, fontFamily: mono, fontSize: 13 }}>Loading...</p>
        </PixelCard>
      </div>
    );
  }

  if (loadError || !team) {
    return (
      <div style={{ padding: 24 }}>
        <PixelCard style={{ padding: 24 }}>
          <p style={{ color: C.textMuted, fontFamily: mono, fontSize: 13 }}>{loadError ?? "No team."}</p>
        </PixelCard>
      </div>
    );
  }

  return (
    <div style={{ padding: 24, display: "flex", flexDirection: "column", gap: 20 }}>
      <div>
        <h1 style={{ fontFamily: mono, fontSize: 28, fontWeight: 800 }}>
          <GradientText>Submit Project</GradientText>
        </h1>
      </div>

      {teamHistory.length > 0 && (
        <div style={{ display: "flex", flexDirection: "column", gap: 8, maxWidth: 520 }}>
          <label style={{ color: C.greenMuted, fontFamily: mono, fontSize: 11, letterSpacing: "0.12em", textTransform: "uppercase" }}>
            Event
          </label>
          {teamHistory.length > 1 ? (
            <select
              value={selectedEventId ?? ""}
              onChange={(event) => handleEventChange(Number(event.target.value))}
              style={{
                width: "100%",
                background: C.surface2,
                border: `1px solid ${C.border}`,
                color: C.text,
                padding: "13px 16px",
                fontFamily: mono,
                fontSize: 13,
                fontWeight: 700,
                borderRadius: 0,
                outline: "none",
              }}
            >
              {teamHistory.map((item) => (
                <option key={item.teamId} value={item.eventId ?? ""}>
                  {eventOptionLabel(item)}
                </option>
              ))}
            </select>
          ) : (
            // Only one event on record — nothing to switch between, so show it
            // as a plain read-only field instead of a dropdown that does nothing.
            <div style={{
              width: "100%",
              background: C.surface2,
              border: `1px solid ${C.border}`,
              color: C.text,
              padding: "13px 16px",
              fontFamily: mono,
              fontSize: 13,
              fontWeight: 700,
            }}>
              {eventOptionLabel(teamHistory[0])}
            </div>
          )}
        </div>
      )}

      {teamDormant ? (
        <div style={{ background: "rgba(107,114,128,0.08)", border: `1px solid ${C.border}`, color: C.textMuted, fontFamily: mono, fontSize: 12, padding: "14px 16px" }}>
          This team isn't part of a running season, so there's nothing to submit right now. Visit My Team to request bringing it back for a new season.
        </div>
      ) : (
      <>
      {!isLeader && (
        <div style={{ background: "rgba(234,179,8,0.08)", border: "1px solid rgba(234,179,8,0.35)", color: C.yellow, fontFamily: mono, fontSize: 12, padding: "10px 14px" }}>
          Only the team leader can submit. You can view the current submission below.
        </div>
      )}

      {!teamApproved && (
        <div style={{ background: "rgba(234,179,8,0.08)", border: "1px solid rgba(234,179,8,0.35)", color: C.yellow, fontFamily: mono, fontSize: 12, padding: "10px 14px" }}>
          Your team is not approved yet - submitting is disabled until a coordinator approves it.
        </div>
      )}

      {teamApproved && !eventAllowsSubmit && (
        <div style={{ background: "rgba(59,130,246,0.08)", border: "1px solid rgba(59,130,246,0.35)", color: "#3b82f6", fontFamily: mono, fontSize: 12, padding: "10px 14px" }}>
          This event is not accepting submissions. You can view existing submission data only.
        </div>
      )}

      {selectedRound && teamApproved && eventAllowsSubmit && !roundOpen && (
        <div style={{ background: "rgba(234,179,8,0.08)", border: "1px solid rgba(234,179,8,0.35)", color: C.yellow, fontFamily: mono, fontSize: 12, padding: "10px 14px" }}>
          This round is not open for submissions.
        </div>
      )}

      {selectedRound && eligibilityLoading && (
        <div style={{ background: "rgba(59,130,246,0.08)", border: "1px solid rgba(59,130,246,0.35)", color: C.blueBright, fontFamily: mono, fontSize: 12, padding: "10px 14px" }}>
          CHECKING ROUND ELIGIBILITY — submission remains locked.
        </div>
      )}

      {selectedRound && !eligibilityLoading && eligibilityError && (
        <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: mono, fontSize: 12, padding: "10px 14px" }}>
          ELIGIBILITY UNAVAILABLE — {eligibilityError} Submission remains locked.
        </div>
      )}

      {selectedRound && !eligibilityLoading && eligibility?.status === "ELIMINATED" && (
        <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.45)", color: C.red, fontFamily: mono, fontSize: 12, padding: "12px 16px" }}>
          <strong>ELIMINATED</strong> — {eligibility.reason}
        </div>
      )}

      {selectedRound && !eligibilityLoading && eligibility?.status === "WAITING_FOR_RESULTS" && (
        <div style={{ background: "rgba(234,179,8,0.08)", border: "1px solid rgba(234,179,8,0.45)", color: C.yellow, fontFamily: mono, fontSize: 12, padding: "12px 16px" }}>
          <strong>WAITING FOR RESULTS</strong> — {eligibility.reason}
        </div>
      )}

      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        {rounds.length === 0 && (
          <span style={{ color: C.textMuted, fontFamily: mono, fontSize: 12 }}>No rounds yet.</span>
        )}

        {rounds.map((round) => {
          const active = selectedRoundId === round.roundId;

          return (
            <button
              key={round.roundId}
              onClick={() => setSelectedRoundId(round.roundId)}
              style={{
                padding: "10px 16px",
                background: active ? "rgba(34,197,94,0.12)" : C.surface2,
                border: active ? `1px solid ${C.green}` : `1px solid ${C.border}`,
                color: active ? C.green : C.text,
                fontFamily: mono,
                fontSize: 12,
                textTransform: "uppercase",
                cursor: "pointer",
                borderRadius: 0,
                display: "flex",
                alignItems: "center",
                gap: 8,
              }}
            >
              {round.name}{round.isFinal ? " - Final" : ""}
              {active && eligibility?.status === "ELIMINATED" ? (
                <PixelBadge color="red">NOT QUALIFIED</PixelBadge>
              ) : active && eligibility?.status === "WAITING_FOR_RESULTS" ? (
                <PixelBadge color="yellow">LOCKED</PixelBadge>
              ) : round.status && (
                <PixelBadge color={["ACTIVE", "OPEN"].includes(round.status.toUpperCase()) ? "green" : "gray"}>
                  {round.status}
                </PixelBadge>
              )}
            </button>
          );
        })}
      </div>

      {selectedRound && (
        <>
          <PixelCard glow style={{ padding: 20 }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 8 }}>
              <div>
                <div style={{ color: C.text, fontFamily: mono, fontSize: 18, fontWeight: 700 }}>{selectedRound.name}</div>
                <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 12, marginTop: 4 }}>
                  Deadline: {fmtDateTime(selectedRound.submissionDeadline)}
                  {existing?.submittedAt ? ` - Last submitted ${fmtDateTime(existing.submittedAt)}` : ""}
                </div>
              </div>

              <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-end", gap: 8 }}>
                {timer.isConfigured && (
                  <CountdownDisplay remainingSeconds={timer.remainingSeconds} status={timer.status} size="sm" icon />
                )}
                {eligibility?.status === "ELIMINATED" ? (
                  <PixelBadge color="red">ELIMINATED</PixelBadge>
                ) : eligibility?.status === "WAITING_FOR_RESULTS" ? (
                  <PixelBadge color="yellow">LOCKED</PixelBadge>
                ) : eligibilityError ? (
                  <PixelBadge color="red">UNAVAILABLE</PixelBadge>
                ) : existing ? <PixelBadge color="green">SUBMITTED</PixelBadge> : <PixelBadge color="gray">NOT SUBMITTED</PixelBadge>}
              </div>
            </div>
          </PixelCard>

          {deadlinePassed && eligibility?.eligible === true && (
            <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, padding: "12px 16px", fontFamily: mono, fontSize: 12 }}>
              DEADLINE PASSED - the submission window is closed for this round.
            </div>
          )}

          {timerBlocks && !deadlinePassed && eligibility?.eligible === true && (
            <div
              style={{
                background: timer.isPaused ? "rgba(234,179,8,0.08)" : "rgba(239,68,68,0.08)",
                border: `1px solid ${timer.isPaused ? "rgba(234,179,8,0.35)" : "rgba(239,68,68,0.35)"}`,
                color: timer.isPaused ? C.yellow : C.red,
                padding: "12px 16px",
                fontFamily: mono,
                fontSize: 12,
              }}
            >
                {timerLockMessage}
            </div>
          )}

          <PixelCard style={{ padding: 20 }}>
            <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
              <PixelInput label="Repository URL" value={repoUrl} onChange={(event) => setRepoUrl(event.target.value)} placeholder="https://github.com/your-team/project" disabled={!canSubmit} />
              <PixelInput label="Demo URL" value={demoUrl} onChange={(event) => setDemoUrl(event.target.value)} placeholder="https://demo.example.com" disabled={!canSubmit} />
              <PixelInput label="Slide Deck URL" value={slideUrl} onChange={(event) => setSlideUrl(event.target.value)} placeholder="https://slides.google.com/..." disabled={!canSubmit} />

              <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                <label style={{ color: C.greenMuted, fontFamily: mono, fontSize: 11, letterSpacing: "0.1em", textTransform: "uppercase" }}>
                  Description (optional)
                </label>
                <textarea
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                  disabled={!canSubmit}
                  placeholder="Brief description of your submission"
                  style={{
                    background: C.surface2,
                    border: `1px solid ${C.border}`,
                    color: C.text,
                    padding: "10px 12px",
                    fontFamily: mono,
                    fontSize: 13,
                    borderRadius: 0,
                    outline: "none",
                    minHeight: 70,
                    resize: "vertical",
                  }}
                />
              </div>

              {actionError && (
                <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: mono, fontSize: 11, padding: "10px 12px" }}>
                  ERROR: {actionError}
                </div>
              )}

              {notice && (
                <div style={{ background: "rgba(34,197,94,0.08)", border: "1px solid rgba(34,197,94,0.35)", color: C.green, fontFamily: mono, fontSize: 12, padding: "10px 12px" }}>
                  {notice}
                </div>
              )}

              {canSubmit && (
                <div>
                  <PixelButton variant="cyber" onClick={submit} disabled={busy}>
                    {busy ? "SAVING..." : existing ? "UPDATE SUBMISSION" : "SUBMIT"}
                  </PixelButton>
                </div>
              )}
            </div>
          </PixelCard>
        </>
      )}
      </>
      )}
    </div>
  );
}
