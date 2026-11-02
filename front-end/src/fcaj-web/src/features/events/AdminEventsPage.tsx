import { useEffect, useState } from "react";
import {
  C, GradientText, PixelCard, PixelButton, PixelInput,
} from "@/shared/components/PixelComponents";
import { apiFetch, ApiError, apiErrorMessage, adminApi, eventsApi, reopenRequestsApi, type ReopenRequest } from "@/shared/apiClient";
import { ConfirmDialog } from "@/shared/components/ConfirmDialog";
import { EventScoreDistribution } from "@/features/events/EventScoreDistribution";
import { PixelMenu, type PixelMenuEntry } from "@/shared/components/PixelMenu";
import { usePermissions } from "@/shared/permissions";
import { useNotifications } from "@/app/providers/NotificationProvider";
import {
  TrackMode, EventRow, ApiEvent, PendingAction,
  normalizeEvent, eventStatusBadge, EventDateBadge, EventName, pickDefaultEvent, EventsListCard,
  parseDDMM, toDDMM, fmtDT,
} from "@/features/events/eventUtils";

// System Admin's event console. The Admin is the only role that can CREATE an
// event, COMPLETE a running one, and REOPEN a completed one — and reviews the
// reopen requests filed by Coordinators. Every state change is confirmed first.

type EventSeason = 'SPRING' | 'SUMMER' | 'FALL';

function dateToLocalDateTime(date: string, time = "08:00:00") {
  if (!date) return undefined;
  return `${date}T${time}`;
}

// Proposed dates per season (DD/MM), offered as a starting point when the
// Admin picks a season on the create form — registration spans the two middle
// months of the season's window, the event itself runs 2 days early in the
// last month. Only fills fields the Admin hasn't already typed into.
const SEASON_DATE_DEFAULTS: Record<EventSeason, { regStart: string; regEnd: string; start: string; end: string }> = {
  SPRING: { regStart: "01/02", regEnd: "31/03", start: "10/04", end: "11/04" },
  SUMMER: { regStart: "01/06", regEnd: "31/07", start: "10/08", end: "11/08" },
  FALL:   { regStart: "01/10", regEnd: "30/11", start: "10/12", end: "11/12" },
};

function seasonWindow(season: EventSeason, yearValue: string) {
  const year = Number(yearValue) || new Date().getFullYear();
  const bounds: Record<EventSeason, { start: string; end: string }> = {
    SPRING: { start: "01-01", end: "04-30" },
    SUMMER: { start: "05-01", end: "08-31" },
    FALL: { start: "09-01", end: "12-31" },
  };
  const bound = bounds[season];
  return {
    label: `${season} ${year}`,
    start: `${year}-${bound.start}`,
    end: `${year}-${bound.end}`,
  };
}

// Accepts DD/MM strings for date fields; year is a separate param.
function createEventDateErrors(
  season: EventSeason | "",
  year: string,
  registrationStart: string,
  registrationEnd: string,
  startDate: string,
  endDate: string,
) {
  const errors: string[] = [];
  if (season === "") {
    errors.push("Please select a season.");
    return errors;
  }
  const numericYear = Number(year);
  if (!Number.isInteger(numericYear) || numericYear < 2026 || numericYear > 3000) {
    errors.push("Year must be between 2026 and 3000.");
    return errors;
  }

  const w = seasonWindow(season, year);
  const parsed = [
    ["Registration start date", parseDDMM(registrationStart, year)],
    ["Registration end date", parseDDMM(registrationEnd, year)],
    ["Start date", parseDDMM(startDate, year)],
    ["End date", parseDDMM(endDate, year)],
  ] as const;

  const missing = parsed.filter(([, v]) => !v).map(([label]) => label);
  if (missing.length > 0) {
    errors.push(`${missing.join(", ")} ${missing.length === 1 ? "is" : "are"} invalid — use DD/MM format (e.g. 05/03).`);
    return errors;
  }

  const [rsDate, reDate, sDate, eDate] = parsed.map(([, v]) => v!);

  const outside = parsed.filter(([, v]) => v! < w.start || v! > w.end).map(([label]) => label);
  if (outside.length > 0) {
    errors.push(`${outside.join(", ")} must be within ${w.label} (${toDDMM(w.start)} → ${toDDMM(w.end)}).`);
  }
  if (reDate < rsDate) errors.push("Registration end must be on or after registration start.");
  if (sDate < reDate) errors.push("The competition must start after registration closes.");
  if (eDate < sDate) errors.push("End date must be on or after start date.");
  return errors;
}

export function AdminEventsPage() {
  const { canCreateEvent, canCompleteEvent, canReopenEvent, canManageReopenRequests } = usePermissions();
  const { addToast } = useNotifications();

  const [events, setEvents] = useState<EventRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [selectedEventId, setSelectedEventId] = useState<number | null>(null);
  const [exportingEventId, setExportingEventId] = useState<number | null>(null);

  // Reopen-request review queue
  const [requests, setRequests] = useState<ReopenRequest[]>([]);

  // Create-event form
  const [showCreate, setShowCreate] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [evName, setEvName] = useState("");
  const [evTopic, setEvTopic] = useState("");
  const [evSeason, setEvSeason] = useState<EventSeason | "">("");
  const [evYear, setEvYear] = useState(String(new Date().getFullYear()));
  const [evRegStart, setEvRegStart] = useState("");
  const [evRegEnd, setEvRegEnd] = useState("");
  const [evStart, setEvStart] = useState("");
  const [evEnd, setEvEnd] = useState("");
  const [evMode, setEvMode] = useState<TrackMode>("SELF_SELECT");

  // Edit-event form state
  const [showEdit, setShowEdit] = useState(false);
  const [editSaving, setEditSaving] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);
  const [editName, setEditName] = useState("");
  const [editTopic, setEditTopic] = useState("");
  const [editSeason, setEditSeason] = useState<EventSeason>("SPRING");
  const [editYear, setEditYear] = useState(String(new Date().getFullYear()));
  const [editRegStart, setEditRegStart] = useState("");
  const [editRegEnd, setEditRegEnd] = useState("");
  const [editStart, setEditStart] = useState("");
  const [editEnd, setEditEnd] = useState("");
  const [editMode, setEditMode] = useState<TrackMode>("SELF_SELECT");

  // Confirmation dialog (complete / reopen / approve / reject / cancel)
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null);
  const [actionWorking, setActionWorking] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);

  const selectedEvent = selectedEventId ? events.find(e => e.eventId === selectedEventId) ?? null : null;

  // Close edit form when user switches to a different event.
  useEffect(() => { setShowEdit(false); }, [selectedEventId]);

  // ── Load events ───────────────────────────────────────────────────
  function loadEvents() {
    setLoading(true);
    setFetchError(null);
    apiFetch<{ data: ApiEvent[] }>('/api/events')
      .then(res => {
        const rows = (res.data ?? []).map(normalizeEvent);
        setEvents(rows);
        // Default-highlight the running event (or the most recently finished one);
        // only on first load — `prev ??` keeps the actor's manual selection.
        setSelectedEventId(prev => prev ?? pickDefaultEvent(rows)?.eventId ?? null);
      })
      .catch(err => setFetchError(err instanceof ApiError ? err.message : "Failed to load events."))
      .finally(() => setLoading(false));
  }

  function loadRequests() {
    if (!canManageReopenRequests) return;
    reopenRequestsApi.getPending()
      .then(res => setRequests(res.data ?? []))
      .catch(() => { /* non-fatal */ });
  }

  useEffect(() => { loadEvents(); loadRequests(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, []);

  // ── Confirmation plumbing ─────────────────────────────────────────
  function openConfirm(action: PendingAction) {
    setDialogError(null);
    setPendingAction(action);
  }
  function closeConfirm() {
    setPendingAction(null);
    setDialogError(null);
  }
  async function handleConfirmAction() {
    if (!pendingAction) return;
    setActionWorking(true);
    setDialogError(null);
    try {
      await pendingAction.run();
      closeConfirm();
    } catch (err) {
      setDialogError(err instanceof ApiError ? err.message : "Action failed.");
      addToast({ type: 'warning', title: 'ACTION FAILED', message: apiErrorMessage(err, 'Action failed.') });
    } finally {
      setActionWorking(false);
    }
  }

  async function exportEventCsv(event: EventRow) {
    if (exportingEventId !== null) return;
    if (event.status !== 'COMPLETED') {
      addToast({ type: 'warning', title: 'EXPORT LOCKED', message: 'CSV export is only available after the event is completed.' });
      return;
    }
    setExportingEventId(event.eventId);
    try {
      await adminApi.exportEventCsv(event.eventId);
      addToast({ type: 'success', title: 'CSV EXPORTED', message: `"${event.name}" export started.` });
    } catch (err) {
      addToast({ type: 'warning', title: 'EXPORT FAILED', message: apiErrorMessage(err, 'Failed to export event CSV.') });
    } finally {
      setExportingEventId(null);
    }
  }

  // ── Lifecycle mutations ───────────────────────────────────────────
  function confirmComplete() {
    if (!selectedEvent) return;
    openConfirm({
      title: 'Complete this event?',
      message: `Mark "${selectedEvent.name}" as Completed (IN_PROGRESS → COMPLETED).`,
      warning: 'Once completed, only System Admin can reopen the event.',
      confirmLabel: 'CONFIRM COMPLETE',
      variant: 'cyber',
      run: async () => {
        await eventsApi.complete(selectedEvent.eventId);
        setEvents(prev => prev.map(e => e.eventId === selectedEvent.eventId ? { ...e, status: 'COMPLETED' } : e));
        addToast({ type: 'success', title: 'EVENT COMPLETED', message: `"${selectedEvent.name}" moved to COMPLETED.` });
      },
    });
  }

  function confirmReopen(ev: EventRow) {
    openConfirm({
      title: 'Reopen this event?',
      message: `Reopen "${ev.name}" (COMPLETED → IN_PROGRESS). The event will become active again.`,
      confirmLabel: 'CONFIRM REOPEN',
      variant: 'cyber',
      run: async () => {
        await eventsApi.reopen(ev.eventId);
        setEvents(prev => prev.map(e => e.eventId === ev.eventId ? { ...e, status: 'IN_PROGRESS' } : e));
        addToast({ type: 'success', title: 'EVENT REOPENED', message: `"${ev.name}" is active again.` });
      },
    });
  }

  // ── Reopen-request review ─────────────────────────────────────────
  function confirmApprove(req: ReopenRequest) {
    openConfirm({
      title: 'Approve reopen request?',
      message: `Approve the request to reopen "${req.eventName}" from ${req.requesterName ?? 'Coordinator'}. The event will be reopened (IN_PROGRESS).`,
      confirmLabel: 'APPROVE & REOPEN',
      variant: 'cyber',
      run: async () => {
        await reopenRequestsApi.approve(req.requestId);
        setRequests(prev => prev.filter(r => r.requestId !== req.requestId));
        setEvents(prev => prev.map(e => e.eventId === req.eventId ? { ...e, status: 'IN_PROGRESS' } : e));
        addToast({ type: 'success', title: 'APPROVED', message: `Reopened "${req.eventName}".` });
      },
    });
  }

  function confirmReject(req: ReopenRequest) {
    openConfirm({
      title: 'Reject reopen request?',
      message: `Reject the request to reopen "${req.eventName}" from ${req.requesterName ?? 'Coordinator'}. The event status will not change.`,
      confirmLabel: 'REJECT',
      variant: 'danger',
      run: async () => {
        await reopenRequestsApi.reject(req.requestId);
        setRequests(prev => prev.filter(r => r.requestId !== req.requestId));
        addToast({ type: 'warning', title: 'REJECTED', message: `Rejected the reopen request for "${req.eventName}".` });
      },
    });
  }

  // ── Cancel event ─────────────────────────────────────────────────
  function confirmCancelEvent() {
    if (!selectedEvent) return;
    openConfirm({
      title: 'Cancel this event?',
      message: `"${selectedEvent.name}" will be permanently cancelled.`,
      warning: 'This cannot be undone. All participants and coordinators will lose access to the event.',
      confirmLabel: 'CANCEL EVENT',
      variant: 'danger',
      requireTypedText: selectedEvent.name,
      run: async () => {
        await apiFetch(`/api/events/${selectedEvent.eventId}`, { method: 'PUT', body: JSON.stringify({ status: 'CANCELLED' }) });
        setEvents(prev => prev.map(e => e.eventId === selectedEvent.eventId ? { ...e, status: 'CANCELLED' } : e));
        addToast({ type: 'warning', title: 'EVENT CANCELLED', message: `"${selectedEvent.name}" has been cancelled.` });
      },
    });
  }

  // ── Edit event ────────────────────────────────────────────────────
  function openEditForm() {
    if (!selectedEvent) return;
    setEditName(selectedEvent.name);
    setEditTopic(selectedEvent.topic ?? "");
    setEditSeason((selectedEvent.season as EventSeason) || "SPRING");
    setEditYear(String(selectedEvent.year ?? new Date().getFullYear()));
    setEditRegStart(toDDMM(selectedEvent.registrationStart));
    setEditRegEnd(toDDMM(selectedEvent.registrationEnd));
    setEditStart(toDDMM(selectedEvent.startDate));
    setEditEnd(toDDMM(selectedEvent.endDate));
    setEditMode(selectedEvent.trackSelectionMode);
    setEditError(null);
    setShowEdit(true);
  }

  async function saveEdit() {
    if (!selectedEvent || editSaving) return;
    if (!editName.trim()) {
      addToast({ type: 'warning', title: 'MISSING NAME', message: 'Please enter an event name.' });
      return;
    }
    const dateErrors = createEventDateErrors(editSeason, editYear, editRegStart, editRegEnd, editStart, editEnd);
    if (dateErrors.length > 0) {
      const message = dateErrors.join(" ");
      setEditError(message);
      addToast({ type: 'warning', title: 'CHECK DATES', message });
      return;
    }
    setEditError(null);
    setEditSaving(true);
    try {
      const res = await apiFetch<{ data: ApiEvent }>(`/api/events/${selectedEvent.eventId}`, {
        method: 'PUT',
        body: JSON.stringify({
          name: editName,
          topic: editTopic,
          season: editSeason,
          year: Number(editYear),
          registrationStart: dateToLocalDateTime(parseDDMM(editRegStart, editYear)!, "00:00:00"),
          registrationEnd: dateToLocalDateTime(parseDDMM(editRegEnd, editYear)!, "23:59:59"),
          startDate: dateToLocalDateTime(parseDDMM(editStart, editYear)!),
          endDate: dateToLocalDateTime(parseDDMM(editEnd, editYear)!, "23:59:59"),
          trackSelectionMode: editMode,
        }),
      });
      const updated = normalizeEvent(res.data);
      setEvents(prev => prev.map(e => e.eventId === updated.eventId ? updated : e));
      setShowEdit(false);
      addToast({ type: 'success', title: 'EVENT UPDATED', message: `"${updated.name}" saved.` });
    } catch (err) {
      setEditError(err instanceof ApiError ? err.message : "Failed to update event.");
      addToast({ type: 'warning', title: 'UPDATE FAILED', message: apiErrorMessage(err, 'Failed to update event.') });
    } finally {
      setEditSaving(false);
    }
  }

  // ── Create-form helpers ───────────────────────────────────────────
  // Tracks & rounds are NOT configured here — the Event Coordinator sets them up
  // during the event's SETUP phase. The Admin only creates the event shell.
  function resetCreateForm() {
    setEvName(""); setEvTopic(""); setEvSeason(""); setEvRegStart(""); setEvRegEnd(""); setEvStart(""); setEvEnd(""); setEvMode("SELF_SELECT");
  }

  // Autofill the create form's date fields with the season's proposed dates —
  // only into fields the Admin hasn't already typed into.
  function handleCreateSeasonChange(season: EventSeason | "") {
    setEvSeason(season);
    if (season === "") return;
    const d = SEASON_DATE_DEFAULTS[season];
    setEvRegStart(prev => prev || d.regStart);
    setEvRegEnd(prev => prev || d.regEnd);
    setEvStart(prev => prev || d.start);
    setEvEnd(prev => prev || d.end);
  }

  async function addEvent(e: React.FormEvent) {
    e.preventDefault();
    if (creating) return;
    if (!evName.trim()) {
      addToast({ type: 'warning', title: 'MISSING NAME', message: 'Please enter an event name.' });
      return;
    }
    const dateErrors = createEventDateErrors(evSeason, evYear, evRegStart, evRegEnd, evStart, evEnd);
    if (dateErrors.length > 0) {
      const message = dateErrors.join(" ");
      setCreateError(message);
      addToast({ type: 'warning', title: 'CHECK DATES', message });
      return;
    }
    if (evSeason === "") return; // unreachable — createEventDateErrors already rejects "", this just satisfies TS narrowing
    setCreateError(null);
    setCreating(true);
    try {
      const res = await apiFetch<{ data: ApiEvent }>('/api/events', {
        method: 'POST',
        body: JSON.stringify({
          name: evName,
          topic: evTopic,
          season: evSeason,
          year: Number(evYear) || new Date().getFullYear(),
          registrationStart: dateToLocalDateTime(parseDDMM(evRegStart, evYear)!, "00:00:00"),
          registrationEnd: dateToLocalDateTime(parseDDMM(evRegEnd, evYear)!, "23:59:59"),
          startDate: dateToLocalDateTime(parseDDMM(evStart, evYear)!),
          endDate: dateToLocalDateTime(parseDDMM(evEnd, evYear)!, "23:59:59"),
          status: 'DRAFT',
          trackSelectionMode: evMode,
        }),
      });
      const created = normalizeEvent(res.data);

      setEvents(prev => [...prev, created]);
      setSelectedEventId(created.eventId);
      resetCreateForm();
      setShowCreate(false);
      addToast({ type: 'success', title: 'EVENT CREATED', message: `"${created.name}" has been created (DRAFT).` });
    } catch (err) {
      setCreateError(err instanceof ApiError ? err.message : "Failed to create event.");
      addToast({ type: 'warning', title: 'CREATE FAILED', message: apiErrorMessage(err, 'Failed to create event.') });
    } finally {
      setCreating(false);
    }
  }

  return (
    <div style={{ padding: 24, display: "flex", flexDirection: "column", gap: 20 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end" }}>
        <h1 style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 28, fontWeight: 800 }}>
          <GradientText>Events</GradientText>
        </h1>
        {canCreateEvent && (
          <PixelButton variant="cyber" onClick={() => setShowCreate(s => !s)}>CREATE EVENT</PixelButton>
        )}
      </div>

      {/* Reopen request review queue — only shown when there is at least one pending request */}
      {canManageReopenRequests && requests.length > 0 && (
        <PixelCard style={{ padding: 18 }}>
          <div style={{ color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, fontWeight: 700, marginBottom: 12, letterSpacing: "0.05em" }}>
            Reopen Requests <span style={{ color: C.textMuted }}>· {requests.length} pending</span>
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            {requests.map(req => (
              <div key={req.requestId} style={{ padding: 12, background: C.surface2, border: `1px solid ${C.border}`, display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
                <div style={{ minWidth: 0 }}>
                  <div style={{ color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, fontWeight: 700 }}>{req.eventName}</div>
                  <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, marginTop: 3 }}>
                    {req.requesterName ?? 'Coordinator'} · {fmtDT(req.createdAt)}
                  </div>
                  {req.reason && (
                    <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, marginTop: 4, fontStyle: "italic" }}>"{req.reason}"</div>
                  )}
                </div>
                <div style={{ display: "flex", gap: 8 }}>
                  <PixelButton size="sm" variant="danger" onClick={() => confirmReject(req)}>REJECT</PixelButton>
                  <PixelButton size="sm" variant="cyber" onClick={() => confirmApprove(req)}>APPROVE</PixelButton>
                </div>
              </div>
            ))}
          </div>
        </PixelCard>
      )}

      {/* Create form */}
      {showCreate && canCreateEvent && (
        <PixelCard style={{ padding: 20 }}>
          <form onSubmit={addEvent} style={{ display: "flex", flexDirection: "column", gap: 18 }}>
            {createError && (
              <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, padding: "10px 14px" }}>
                ERROR: {createError}
              </div>
            )}
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 14 }}>
              <PixelInput label="Event Name" value={evName} onChange={(e) => setEvName(e.target.value)} placeholder="SEAL Fall 2026" />
              <div style={{ gridColumn: "span 2" }}>
                <PixelInput
                  label="Topic" value={evTopic} onChange={(e) => setEvTopic(e.target.value)}
                  placeholder="The overall competition theme"
                />
              </div>
              <div>
                <label style={{ color: C.greenMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, letterSpacing: "0.1em", textTransform: "uppercase" }}>Season</label>
                <select value={evSeason} onChange={(e) => handleCreateSeasonChange(e.target.value as EventSeason | "")} style={{ width: "100%", marginTop: 6, padding: "10px 12px", background: C.surface2, border: `1px solid ${C.border}`, color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, borderRadius: 0, outline: "none" }}>
                  <option value="">— select season —</option>
                  <option value="SPRING">Spring</option>
                  <option value="SUMMER">Summer</option>
                  <option value="FALL">Fall</option>
                </select>
              </div>
              <PixelInput label="Year" type="number" value={evYear} onChange={(e) => setEvYear(e.target.value)} />
              <PixelInput label="Registration Start" type="text" placeholder="DD/MM" value={evRegStart} onChange={(e) => setEvRegStart(e.target.value)} />
              <PixelInput label="Registration End" type="text" placeholder="DD/MM" value={evRegEnd} onChange={(e) => setEvRegEnd(e.target.value)} />
              <div>
                <label style={{ color: C.greenMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, letterSpacing: "0.1em", textTransform: "uppercase" }}>Track Assignment</label>
                <select value={evMode} onChange={(e) => setEvMode(e.target.value as TrackMode)} style={{ width: "100%", marginTop: 6, padding: "10px 12px", background: C.surface2, border: `1px solid ${C.border}`, color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, borderRadius: 0, outline: "none" }}>
                  <option value="SELF_SELECT">Teams self-select</option>
                  <option value="RANDOM">Random draw</option>
                </select>
              </div>
              <PixelInput label="Start Date" type="text" placeholder="DD/MM" value={evStart} onChange={(e) => setEvStart(e.target.value)} />
              <PixelInput label="End Date" type="text" placeholder="DD/MM" value={evEnd} onChange={(e) => setEvEnd(e.target.value)} />
            </div>

            <div style={{ borderTop: `1px solid ${C.border}`, paddingTop: 14, display: "flex", gap: 10 }}>
              <PixelButton type="submit" variant="cyber">{creating ? "CREATING..." : "ADD EVENT"}</PixelButton>
              <PixelButton type="button" variant="secondary" onClick={() => { resetCreateForm(); setShowCreate(false); }}>CANCEL</PixelButton>
            </div>
          </form>
        </PixelCard>
      )}

      {/* Detail panel — Admin lifecycle actions; on top, above the all-events list */}
      {selectedEvent && (
        <PixelCard glow gradient style={{ padding: 20 }}>
          {showEdit ? (
            <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
              <div style={{ color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 14, fontWeight: 700 }}>EDIT EVENT</div>
              {editError && (
                <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, padding: "10px 14px" }}>
                  ERROR: {editError}
                </div>
              )}
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 14 }}>
                <PixelInput label="Event Name" value={editName} onChange={(e) => setEditName(e.target.value)} />
                <div style={{ gridColumn: "span 2" }}>
                  <PixelInput
                    label="Topic" value={editTopic} onChange={(e) => setEditTopic(e.target.value)}
                    placeholder="The overall competition theme"
                  />
                </div>
                <div>
                  <label style={{ color: C.greenMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, letterSpacing: "0.1em", textTransform: "uppercase" }}>Season</label>
                  <select value={editSeason} onChange={(e) => setEditSeason(e.target.value as EventSeason)} style={{ width: "100%", marginTop: 6, padding: "10px 12px", background: C.surface2, border: `1px solid ${C.border}`, color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, borderRadius: 0, outline: "none" }}>
                    <option value="SPRING">Spring</option>
                    <option value="SUMMER">Summer</option>
                    <option value="FALL">Fall</option>
                  </select>
                </div>
                <PixelInput label="Year" type="number" value={editYear} onChange={(e) => setEditYear(e.target.value)} />
                <PixelInput label="Reg. Start (DD/MM)" type="text" placeholder="e.g. 05/01" value={editRegStart} onChange={(e) => setEditRegStart(e.target.value)} />
                <PixelInput label="Reg. End (DD/MM)" type="text" placeholder="e.g. 28/02" value={editRegEnd} onChange={(e) => setEditRegEnd(e.target.value)} />
                <div>
                  <label style={{ color: C.greenMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, letterSpacing: "0.1em", textTransform: "uppercase" }}>Track Assignment</label>
                  <select value={editMode} onChange={(e) => setEditMode(e.target.value as TrackMode)} style={{ width: "100%", marginTop: 6, padding: "10px 12px", background: C.surface2, border: `1px solid ${C.border}`, color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, borderRadius: 0, outline: "none" }}>
                    <option value="SELF_SELECT">Teams self-select</option>
                    <option value="RANDOM">Random draw</option>
                  </select>
                </div>
                <PixelInput label="Start Date (DD/MM)" type="text" placeholder="e.g. 01/03" value={editStart} onChange={(e) => setEditStart(e.target.value)} />
                <PixelInput label="End Date (DD/MM)" type="text" placeholder="e.g. 30/04" value={editEnd} onChange={(e) => setEditEnd(e.target.value)} />
              </div>
              <div style={{ display: "flex", gap: 10, borderTop: `1px solid ${C.border}`, paddingTop: 14 }}>
                <PixelButton variant="cyber" onClick={saveEdit} disabled={editSaving}>{editSaving ? "SAVING..." : "SAVE CHANGES"}</PixelButton>
                <PixelButton variant="ghost" onClick={() => setShowEdit(false)} disabled={editSaving}>CANCEL</PixelButton>
              </div>
            </div>
          ) : (
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
              <div>
                <div><EventName>{selectedEvent.name}</EventName></div>
                {selectedEvent.topic && (
                  <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, marginTop: 4 }}>{selectedEvent.topic}</div>
                )}
                <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", marginTop: 8 }}>
                  {eventStatusBadge(selectedEvent.status)}
                  <EventDateBadge ev={selectedEvent} />
                </div>
              </div>
              <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                {selectedEvent.status === 'IN_PROGRESS' && canCompleteEvent && (
                  <PixelButton variant="cyber" onClick={confirmComplete}>COMPLETE EVENT</PixelButton>
                )}
                {(() => {
                  // Edit/Cancel only make sense before the event is done; Reopen
                  // only makes sense once it's COMPLETED — the two sets never
                  // overlap, so one menu covers every non-CANCELLED status.
                  const items: PixelMenuEntry[] = [];
                  if (selectedEvent.status !== 'CANCELLED' && selectedEvent.status !== 'COMPLETED') {
                    items.push({ label: "Edit", onClick: openEditForm });
                    items.push("divider");
                    items.push({ label: "Cancel Event", danger: true, onClick: confirmCancelEvent });
                  }
                  if (selectedEvent.status === 'COMPLETED' && canReopenEvent) {
                    items.push({ label: "Reopen Event", onClick: () => confirmReopen(selectedEvent) });
                  }
                  return items.length > 0 && (
                    <PixelMenu ariaLabel={`More actions for ${selectedEvent.name}`} items={items} />
                  );
                })()}
              </div>
            </div>
          )}
        </PixelCard>
      )}

      {selectedEvent?.status === 'COMPLETED' && (
        <EventScoreDistribution key={selectedEvent.eventId} eventId={selectedEvent.eventId} />
      )}

      {/* All-events summary list with find filter — below the detail panel */}
      <EventsListCard
        events={events}
        loading={loading}
        error={fetchError}
        selectedEventId={selectedEventId}
        onSelect={setSelectedEventId}
        renderRowAction={(event) => {
          if (event.status !== 'COMPLETED') return null;
          const exportingThisEvent = exportingEventId === event.eventId;
          return (
            <span
              title="Export completed event CSV"
              onClick={(clickEvent) => clickEvent.stopPropagation()}
              onKeyDown={(keyEvent) => keyEvent.stopPropagation()}
              style={{ display: "inline-flex" }}
            >
              <PixelButton
                size="sm"
                variant="secondary"
                disabled={exportingEventId !== null}
                onClick={() => exportEventCsv(event)}
              >
                {exportingThisEvent ? "EXPORTING..." : "EXPORT CSV"}
              </PixelButton>
            </span>
          );
        }}
      />

      {/* Shared confirmation dialog */}
      {pendingAction && (
        <ConfirmDialog
          title={pendingAction.title}
          message={pendingAction.message}
          warning={pendingAction.warning}
          confirmLabel={pendingAction.confirmLabel}
          variant={pendingAction.variant}
          working={actionWorking}
          error={dialogError}
          requireTypedText={pendingAction.requireTypedText}
          onConfirm={handleConfirmAction}
          onClose={closeConfirm}
        />
      )}
    </div>
  );
}
