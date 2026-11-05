import { useEffect, useState } from "react";
import { C, PixelButton, PixelBadge, PixelInput } from "@/shared/components/PixelComponents";
import { PixelMenu } from "@/shared/components/PixelMenu";
import { apiFetch, ApiError, apiErrorMessage } from "@/shared/apiClient";
import { useNotifications } from "@/app/providers/NotificationProvider";
import {
  RoundRow, CriteriaRow, ApiCriteria, CriteriaTemplate, normalizeCriteria, PendingAction,
} from "@/features/events/eventUtils";

// The Criteria tab of CoordEventsPage: per-round scoring criteria CRUD, plus
// reusable criteria templates (apply a saved set / save this round's criteria
// as a new one). Extracted verbatim out of CoordEventsPage.tsx — behavior, API
// calls, and copy are unchanged. Self-fetches its own criteria/templates
// (previously fetched by the parent), matching the pattern TrackProblemsTab
// already uses for its own data.

export function CriteriaTab({
  eventId, rounds, selectedRoundId, setSelectedRoundId, openConfirm, setActionError,
}: {
  eventId: number;
  rounds: RoundRow[];
  selectedRoundId: number | null;
  setSelectedRoundId: React.Dispatch<React.SetStateAction<number | null>>;
  openConfirm: (action: PendingAction) => void;
  setActionError: (msg: string | null) => void;
}) {
  const { addToast } = useNotifications();

  // Criteria are per-round in the API — load them for the selected round.
  const [criteria, setCriteria] = useState<CriteriaRow[]>([]);
  const [criteriaLoading, setCriteriaLoading] = useState(false);
  const [criteriaError, setCriteriaError] = useState<string | null>(null);

  // Reusable criteria templates (global, not per-event) for the apply/save UI.
  const [templates, setTemplates] = useState<CriteriaTemplate[]>([]);
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(null);
  const [templateBusy, setTemplateBusy] = useState(false);
  const [savingTemplate, setSavingTemplate] = useState(false);
  const [newTemplateName, setNewTemplateName] = useState("");

  // Criteria form (collapsed behind "+ ADD CRITERIA" until needed)
  // Max/Weight are kept as raw text while typing (not Number()) so a trailing
  // "." or partial decimal ("5.") isn't stripped by a Number→String round-trip
  // on every keystroke — they're only parsed to Number() at submit time.
  const [showAddCriteria, setShowAddCriteria] = useState(false);
  const [crName, setCrName] = useState("");
  const [crDesc, setCrDesc] = useState("");
  const [crMax, setCrMax] = useState("10");
  const [crWeight, setCrWeight] = useState("1.0");
  // Inline criteria edit (separate from the create form so the two never clash)
  const [editingCriteriaId, setEditingCriteriaId] = useState<number | null>(null);
  const [ecName, setEcName] = useState("");
  const [ecDesc, setEcDesc] = useState("");
  const [ecMax, setEcMax] = useState("10");
  const [ecWeight, setEcWeight] = useState("1.0");

  // ── Load criteria when the selected round changes ─────────────────
  useEffect(() => {
    if (selectedRoundId == null) {
      setCriteria([]);
      return;
    }
    setCriteriaLoading(true);
    setCriteriaError(null);
    apiFetch<{ data: ApiCriteria[] }>(`/api/events/${eventId}/rounds/${selectedRoundId}/criteria`)
      .then(res => setCriteria((res.data ?? []).map(normalizeCriteria).sort((a, b) => a.orderNumber - b.orderNumber)))
      .catch(err => setCriteriaError(err instanceof ApiError ? err.message : "Failed to load criteria."))
      .finally(() => setCriteriaLoading(false));
  }, [eventId, selectedRoundId]);

  // ── Load reusable criteria templates once (global list) ───────────
  useEffect(() => {
    apiFetch<{ data: CriteriaTemplate[] }>('/api/criteria-templates')
      .then(res => setTemplates(res.data ?? []))
      .catch(() => { /* non-fatal — the template picker just stays empty */ });
  }, []);

  async function addCriteria() {
    if (selectedRoundId == null) return;
    const name = crName.trim();
    if (!name) {
      addToast({ type: 'warning', title: 'MISSING NAME', message: 'Please enter a criteria name.' });
      return;
    }
    const weight = Number(crWeight);
    const maxScore = Number(crMax);
    if (!Number.isFinite(weight) || !Number.isFinite(maxScore)) {
      addToast({ type: 'warning', title: 'INVALID NUMBER', message: 'Max and Weight must be valid numbers.' });
      return;
    }
    setActionError(null);
    try {
      const res = await apiFetch<{ data: ApiCriteria }>(`/api/events/${eventId}/rounds/${selectedRoundId}/criteria`, {
        method: 'POST',
        body: JSON.stringify({
          name,
          description: crDesc || undefined,
          weight,
          maxScore,
          orderNumber: criteria.length + 1,
        }),
      });
      setCriteria(prev => [...prev, normalizeCriteria(res.data)].sort((a, b) => a.orderNumber - b.orderNumber));
      setCrName(""); setCrDesc("");
      addToast({ type: 'success', title: 'CRITERIA ADDED', message: `"${name}" added to this round.` });
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Failed to add criteria.");
      addToast({ type: 'warning', title: 'CREATE FAILED', message: apiErrorMessage(err, 'Failed to add criteria.') });
    }
  }

  // Apply a saved template's criteria to the current round (appends; the backend
  // skips items whose name already exists, so re-applying never duplicates).
  async function applyTemplate() {
    if (selectedRoundId == null || selectedTemplateId == null) {
      addToast({ type: 'warning', title: 'NO TEMPLATE', message: 'Pick a template to apply.' });
      return;
    }
    setTemplateBusy(true);
    setActionError(null);
    try {
      const res = await apiFetch<{ data: ApiCriteria[] }>(
        `/api/events/${eventId}/rounds/${selectedRoundId}/criteria/apply-template/${selectedTemplateId}`,
        { method: 'POST' },
      );
      setCriteria((res.data ?? []).map(normalizeCriteria).sort((a, b) => a.orderNumber - b.orderNumber));
      const tpl = templates.find(t => t.templateId === selectedTemplateId);
      addToast({ type: 'success', title: 'TEMPLATE APPLIED', message: `"${tpl?.name ?? 'Template'}" applied to this round.` });
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Failed to apply template.");
      addToast({ type: 'warning', title: 'APPLY FAILED', message: apiErrorMessage(err, 'Failed to apply template.') });
    } finally {
      setTemplateBusy(false);
    }
  }

  // Save the current round's criteria as a new reusable template.
  async function saveAsTemplate() {
    if (selectedRoundId == null) return;
    const name = newTemplateName.trim();
    if (!name) {
      addToast({ type: 'warning', title: 'MISSING NAME', message: 'Enter a name for the template.' });
      return;
    }
    setTemplateBusy(true);
    setActionError(null);
    try {
      const res = await apiFetch<{ data: CriteriaTemplate }>(
        `/api/events/${eventId}/rounds/${selectedRoundId}/criteria/save-as-template`,
        { method: 'POST', body: JSON.stringify({ name }) },
      );
      setTemplates(prev => [...prev, res.data]);
      setSavingTemplate(false);
      setNewTemplateName("");
      addToast({ type: 'success', title: 'TEMPLATE SAVED', message: `"${name}" saved from this round's criteria.` });
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Failed to save template.");
      addToast({ type: 'warning', title: 'SAVE FAILED', message: apiErrorMessage(err, 'Failed to save template.') });
    } finally {
      setTemplateBusy(false);
    }
  }

  function startEditCriteria(c: CriteriaRow) {
    setEditingCriteriaId(c.criteriaId);
    setEcName(c.name);
    setEcDesc(c.description ?? "");
    setEcMax(String(c.maxScore));
    setEcWeight(String(c.weight));
  }

  function cancelCriteriaEdit() {
    setEditingCriteriaId(null);
    setEcName(""); setEcDesc(""); setEcMax("10"); setEcWeight("1.0");
  }

  async function saveCriteriaEdit(original: CriteriaRow) {
    if (selectedRoundId == null || editingCriteriaId == null) return;
    const name = ecName.trim();
    if (!name) {
      addToast({ type: 'warning', title: 'MISSING NAME', message: 'Please enter a criteria name.' });
      return;
    }
    const weight = Number(ecWeight);
    const maxScore = Number(ecMax);
    if (!Number.isFinite(weight) || !Number.isFinite(maxScore)) {
      addToast({ type: 'warning', title: 'INVALID NUMBER', message: 'Max and Weight must be valid numbers.' });
      return;
    }
    setActionError(null);
    try {
      const res = await apiFetch<{ data: ApiCriteria }>(`/api/events/${eventId}/rounds/${selectedRoundId}/criteria/${editingCriteriaId}`, {
        method: 'PUT',
        body: JSON.stringify({
          name,
          description: ecDesc || undefined,
          weight,
          maxScore,
          orderNumber: original.orderNumber,
        }),
      });
      const updated = normalizeCriteria(res.data);
      setCriteria(prev => prev.map(c => c.criteriaId === editingCriteriaId ? updated : c)
        .sort((a, b) => a.orderNumber - b.orderNumber));
      cancelCriteriaEdit();
      addToast({ type: 'success', title: 'CRITERIA UPDATED', message: `"${name}" saved.` });
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Failed to update criteria.");
      addToast({ type: 'warning', title: 'UPDATE FAILED', message: apiErrorMessage(err, 'Failed to update criteria.') });
    }
  }

  function requestDeleteCriteria(c: CriteriaRow) {
    if (selectedRoundId == null) return;
    const roundId = selectedRoundId;
    openConfirm({
      title: 'Remove this criteria?',
      message: (
        <div>
          Criteria <span style={{ color: C.text, fontWeight: 700 }}>"{c.name}"</span> will be deleted from this round.
        </div>
      ),
      warning: 'If judges have already scored this criteria, the delete is blocked — remove the scores first.',
      confirmLabel: 'DELETE CRITERIA',
      variant: 'danger',
      run: async () => {
        await apiFetch(`/api/events/${eventId}/rounds/${roundId}/criteria/${c.criteriaId}`, { method: 'DELETE' });
        setCriteria(prev => prev.filter(x => x.criteriaId !== c.criteriaId));
        addToast({ type: 'success', title: 'CRITERIA DELETED', message: `"${c.name}" removed.` });
      },
    });
  }

  if (rounds.length === 0) {
    return <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>Add a round first — scoring criteria are configured per round.</div>;
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
      {/* Round selector */}
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        {rounds.map(r => {
          const active = selectedRoundId === r.roundId;
          return (
            <button key={r.roundId} onClick={() => setSelectedRoundId(r.roundId)}
              style={{
                padding: "6px 12px",
                background: active ? "rgba(34,197,94,0.12)" : C.surface2,
                border: active ? `1px solid ${C.green}` : `1px solid ${C.border}`,
                color: active ? C.green : C.textMuted,
                fontFamily: "'JetBrains Mono', monospace", fontSize: 11, cursor: "pointer", borderRadius: 0,
              }}>
              {r.orderNumber}. {r.name}
            </button>
          );
        })}
      </div>

      {/* Criteria template: apply a saved set, or save this round's criteria as a new one */}
      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", padding: 10, background: C.surface, border: `1px solid ${C.border}` }}>
        <span style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, letterSpacing: "0.05em" }}>TEMPLATE:</span>
        <select
          value={selectedTemplateId ?? ""}
          onChange={(e) => setSelectedTemplateId(e.target.value ? Number(e.target.value) : null)}
          style={{ padding: "4px 8px", background: C.surface2, border: `1px solid ${C.border}`, color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, borderRadius: 0, outline: "none" }}
        >
          <option value="">Select a template…</option>
          {templates.map(t => (
            <option key={t.templateId} value={t.templateId}>{t.name} ({t.items?.length ?? 0})</option>
          ))}
        </select>
        <PixelButton size="sm" variant="cyber" onClick={applyTemplate} disabled={templateBusy || selectedTemplateId == null}>APPLY</PixelButton>
        <div style={{ flex: 1 }} />
        {savingTemplate ? (
          <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
            <PixelInput placeholder="Template name" value={newTemplateName} onChange={(e) => setNewTemplateName(e.target.value)} />
            <PixelButton size="sm" variant="secondary" onClick={saveAsTemplate} disabled={templateBusy}>SAVE</PixelButton>
            <PixelButton size="sm" variant="ghost" onClick={() => { setSavingTemplate(false); setNewTemplateName(""); }}>CANCEL</PixelButton>
          </div>
        ) : (
          <PixelButton size="sm" variant="ghost" onClick={() => setSavingTemplate(true)} disabled={criteria.length === 0}>SAVE AS TEMPLATE</PixelButton>
        )}
      </div>

      {criteriaLoading && <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>Loading...</div>}
      {criteriaError && <div style={{ color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 11 }}>{criteriaError}</div>}
      {!criteriaLoading && !criteriaError && criteria.length === 0 && (
        <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>No criteria for this round yet</div>
      )}
      {criteria.map(c => (
        <div key={c.criteriaId} className="row-actionable" style={{ padding: 12, background: C.surface2, border: `1px solid ${editingCriteriaId === c.criteriaId ? C.green : C.border}` }}>
          {editingCriteriaId === c.criteriaId ? (
            <div style={{ display: "grid", gridTemplateColumns: "1fr 2fr 80px 80px auto", gap: 10, alignItems: "end" }}>
              <PixelInput label="Name" value={ecName} onChange={(e) => setEcName(e.target.value)} />
              <PixelInput label="Description" value={ecDesc} onChange={(e) => setEcDesc(e.target.value)} />
              <PixelInput label="Max" type="number" value={ecMax} onChange={(e) => setEcMax(e.target.value)} />
              <PixelInput label="Weight" type="number" value={ecWeight} onChange={(e) => setEcWeight(e.target.value)} />
              <div style={{ display: "flex", gap: 8 }}>
                <PixelButton size="sm" variant="cyber" onClick={() => saveCriteriaEdit(c)}>SAVE</PixelButton>
                <PixelButton size="sm" variant="ghost" onClick={cancelCriteriaEdit}>CANCEL</PixelButton>
              </div>
            </div>
          ) : (
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12 }}>
              <div style={{ minWidth: 0, flex: 1 }}>
                <div style={{ color: C.text, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, fontWeight: 600 }}>{c.name}</div>
                <div style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, marginTop: 2 }}>{c.description || "—"}</div>
              </div>
              {/* Fixed-width cells so MAX / W / ⋯ line up across every row */}
              <div style={{ display: "flex", gap: 8, alignItems: "center", flexShrink: 0 }}>
                <div style={{ width: 84, display: "flex", justifyContent: "center" }}><PixelBadge color="cyan">MAX {c.maxScore}</PixelBadge></div>
                <div style={{ width: 64, display: "flex", justifyContent: "center" }}><PixelBadge color="blue">W {c.weight}</PixelBadge></div>
                <div className="row-action" style={{ width: 44, display: "flex", justifyContent: "flex-end" }}>
                  <PixelMenu
                    ariaLabel={`Actions for criteria ${c.name}`}
                    items={[
                      { label: "Edit", onClick: () => startEditCriteria(c) },
                      "divider",
                      { label: "Delete", danger: true, onClick: () => requestDeleteCriteria(c) },
                    ]}
                  />
                </div>
              </div>
            </div>
          )}
        </div>
      ))}
      {/* Add form — collapsed behind "+ ADD CRITERIA" so the list stays clean. */}
      {showAddCriteria ? (
        <div style={{ padding: 14, background: C.surface, border: `1px solid ${C.border}` }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 2fr 80px 80px auto", gap: 10, alignItems: "end" }}>
            <PixelInput label="Name" value={crName} onChange={(e) => setCrName(e.target.value)} />
            <PixelInput label="Description" value={crDesc} onChange={(e) => setCrDesc(e.target.value)} />
            <PixelInput label="Max" type="number" value={crMax} onChange={(e) => setCrMax(e.target.value)} />
            <PixelInput label="Weight" type="number" value={crWeight} onChange={(e) => setCrWeight(e.target.value)} />
            <div style={{ display: "flex", gap: 8 }}>
              <PixelButton variant="secondary" onClick={addCriteria}>ADD</PixelButton>
              <PixelButton variant="ghost" onClick={() => { setShowAddCriteria(false); setCrName(""); setCrDesc(""); }}>CANCEL</PixelButton>
            </div>
          </div>
        </div>
      ) : (
        <div>
          <PixelButton variant="secondary" onClick={() => setShowAddCriteria(true)}>+ ADD CRITERIA</PixelButton>
        </div>
      )}
    </div>
  );
}
