import { useState, useEffect } from "react";
import { C, GradientText, PixelButton, FloatingParticles } from "@/shared/components/PixelComponents";
import { teamsApi, ApiError, apiErrorMessage, ActiveEventWithTracks } from "@/shared/apiClient";
import { useAuth } from "@/app/providers/AuthProvider";
import { useNotifications } from "@/app/providers/NotificationProvider";

const FIELD_BORDER = "rgba(148,163,184,0.18)";

export function CreateTeamScreen({
    initialEventId,
    initialTrackId,
    onBack,
    onSubmit,
}: {
    initialEventId: number | null;
    initialTrackId: number | null;
    onBack: () => void;
    onSubmit: (teamName: string) => void;
}) {
    const { refreshTeamContext } = useAuth();
    const { addToast } = useNotifications();

    const [activeEvents, setActiveEvents] = useState<ActiveEventWithTracks[]>([]);
    const [loadError, setLoadError] = useState<string | null>(null);

    const [teamName, setTeamName] = useState("");
    const [description, setDescription] = useState("");
    const [eventId, setEventId] = useState<number | null>(initialEventId);
    const [showInfo, setShowInfo] = useState(false);
    const [nameTaken, setNameTaken] = useState(false);

    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        teamsApi.getActiveEvents()
            .then(res => {
                const evs = res.data ?? [];
                setActiveEvents(evs);
                // No event picker anymore — default to the event the user came from,
                // falling back to the sole open event when there is exactly one.
                if (initialEventId == null && evs.length === 1) setEventId(evs[0].eventId);
            })
            .catch(err => setLoadError(err instanceof ApiError ? err.message : "Failed to load open events."));
    }, [initialEventId]);

    const selectedEvent = eventId != null ? activeEvents.find(e => e.eventId === eventId) : null;
    const canSubmit = teamName.trim() !== "" && eventId !== null && !nameTaken;

    const inputStyle: React.CSSProperties = {
        width: "100%",
        padding: "10px 14px",
        background: "rgba(8,13,20,0.72)",
        border: `1px solid ${FIELD_BORDER}`,
        color: C.text,
        fontFamily: "'JetBrains Mono', monospace",
        fontSize: 13,
        borderRadius: 0,
        outline: "none",
        boxSizing: "border-box",
        transition: "border-color 0.15s, box-shadow 0.15s",
    };

    function handleFocus(e: React.FocusEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) {
        e.currentTarget.style.borderColor = C.green;
        e.currentTarget.style.boxShadow = `0 0 0 1px rgba(34,197,94,0.35)`;
    }
    function handleBlur(e: React.FocusEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) {
        e.currentTarget.style.borderColor = FIELD_BORDER;
        e.currentTarget.style.boxShadow = "none";
    }

    // Live duplicate-name check on blur — same UX as the register-page student-id
    // check. Advisory only; the backend still enforces uniqueness on submit.
    async function checkTeamNameExists() {
        const name = teamName.trim();
        if (!name || eventId == null) { setNameTaken(false); return; }
        try {
            const res = await teamsApi.checkName(eventId, name);
            const taken = Boolean(res.data);
            setNameTaken(taken);
            if (taken) {
                addToast({ type: "warning", title: "Name taken", message: `A team named "${name}" already exists in this event.` });
            }
        } catch {
            // silently ignore — the create call will enforce uniqueness anyway
        }
    }

    async function handleCreate() {
        if (!canSubmit || eventId == null) return;
        setSubmitting(true);
        setError(null);
        try {
            await teamsApi.create({
                eventId,
                name: teamName.trim(),
                description: description.trim() || undefined,
            });
            await refreshTeamContext();
            onSubmit(teamName.trim());
        } catch (err) {
            // Backend enforces a unique (case/space-insensitive) team name per event
            // and other rules — surface its message inline and as a toast.
            setError(err instanceof ApiError ? err.message : "Failed to create team.");
            addToast({ type: "warning", title: "Create failed", message: apiErrorMessage(err, "Failed to create team.") });
        } finally {
            setSubmitting(false);
        }
    }

    const labelStyle: React.CSSProperties = {
        color: C.greenMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 11,
        letterSpacing: "0.1em", textTransform: "uppercase", display: "block", marginBottom: 8,
    };

    // Liquid-glass card matching the dashboard tiles — translucent gradient film,
    // soft accent border, backdrop blur and a gentle glow.
    const glassCard: React.CSSProperties = {
        // Accent gradient film over a mostly-opaque dark base so the card reads as
        // a distinct panel against the grid background instead of blending in.
        background: "linear-gradient(135deg, rgba(34,197,94,0.10) 0%, rgba(59,130,246,0.06) 100%), rgba(11,17,26,0.85)",
        border: "1px solid rgba(34,197,94,0.32)",
        backdropFilter: "blur(10px)",
        WebkitBackdropFilter: "blur(10px)",
        boxShadow: "0 8px 32px rgba(0,0,0,0.45), 0 0 28px rgba(34,197,94,0.12), inset 0 0 48px rgba(34,197,94,0.03)",
        padding: 28,
        display: "flex",
        flexDirection: "column",
        gap: 20,
        position: "relative",
        overflow: "hidden",
    };

    return (
        <div className="cyber-grid-bg" style={{ position: "relative", minHeight: "100%", padding: "28px 24px" }}>
            {/* Same ambient backdrop as the dashboard — cyber grid, floating
                particles and a soft radial glow. */}
            <FloatingParticles count={24} />
            <div style={{ position: "absolute", top: "4%", left: "50%", transform: "translateX(-50%)", width: 480, height: 480, borderRadius: "50%", background: "radial-gradient(circle, rgba(59,130,246,0.06), transparent 70%)", pointerEvents: "none" }} />
            <div style={{ position: "relative", zIndex: 1, maxWidth: 560, margin: "0 auto" }}>
            <button
                onClick={onBack}
                style={{
                    background: "none", border: "none", cursor: "pointer",
                    color: C.textMuted, fontFamily: "'JetBrains Mono', monospace",
                    fontSize: 12, letterSpacing: "0.04em", marginBottom: 28,
                    display: "flex", alignItems: "center", gap: 6,
                    padding: 0, transition: "color 0.15s",
                }}
                onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.color = C.green; }}
                onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.color = C.textMuted; }}
            >
                ← Back to Events
            </button>

            {/* Heading + a single "i" that holds every "good to know" note, so the
                form itself stays clean instead of scattering callouts everywhere. */}
            <div style={{ marginBottom: 32, position: "relative", display: "flex", alignItems: "center", gap: 12 }}>
                <h1 style={{ fontFamily: "'JetBrains Mono', monospace", fontWeight: 900, fontSize: 32, lineHeight: 1.1, margin: 0 }}>
                    <GradientText>Create Your Team</GradientText>
                </h1>
                <button
                    type="button"
                    aria-label="About creating a team"
                    aria-expanded={showInfo}
                    onClick={() => setShowInfo(v => !v)}
                    style={{
                        flexShrink: 0, width: 24, height: 24, borderRadius: "50%",
                        border: `1px solid ${showInfo ? C.green : "rgba(34,197,94,0.5)"}`,
                        background: showInfo ? "rgba(34,197,94,0.16)" : "rgba(34,197,94,0.06)",
                        color: showInfo ? C.green : C.greenMuted,
                        fontFamily: "'JetBrains Mono', monospace", fontSize: 13, fontWeight: 900,
                        fontStyle: "italic", cursor: "pointer", lineHeight: 1,
                        display: "flex", alignItems: "center", justifyContent: "center",
                        transition: "all 0.15s ease",
                    }}
                >
                    i
                </button>

                {showInfo && (
                    <>
                        {/* click-away layer */}
                        <div onClick={() => setShowInfo(false)} style={{ position: "fixed", inset: 0, zIndex: 40 }} />
                        <div style={{
                            position: "absolute", top: "calc(100% + 8px)", left: 0, zIndex: 50, width: 300,
                            background: "rgba(8,14,22,0.94)", border: "1px solid rgba(34,197,94,0.3)",
                            backdropFilter: "blur(10px)", WebkitBackdropFilter: "blur(10px)",
                            boxShadow: "0 8px 32px rgba(0,0,0,0.5), 0 0 20px rgba(34,197,94,0.08)",
                            padding: "14px 16px",
                        }}>
                            <div style={{ color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 10, letterSpacing: "0.14em", marginBottom: 10 }}>
                                GOOD TO KNOW
                            </div>
                            <ul style={{ margin: 0, padding: 0, listStyle: "none", display: "flex", flexDirection: "column", gap: 9 }}>
                                {[
                                    "You become the Team Leader.",
                                    "Team stays PENDING until a coordinator approves it.",
                                    "Track is assigned later, during the setup phase.",
                                    "Team name must be unique within this event.",
                                ].map((note) => (
                                    <li key={note} style={{ display: "flex", gap: 8, color: "#ffffff", fontFamily: "'JetBrains Mono', monospace", fontSize: 11.5, lineHeight: 1.5 }}>
                                        <span style={{ color: C.green, flexShrink: 0 }}>›</span>
                                        <span>{note}</span>
                                    </li>
                                ))}
                            </ul>
                        </div>
                    </>
                )}
            </div>

            {loadError && (
                <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, padding: "10px 14px", marginBottom: 16 }}>
                    ERROR: {loadError}
                </div>
            )}

            {activeEvents.length === 0 && !loadError ? (
                <div style={{ background: C.surface, border: `1px solid ${C.border}`, padding: 24 }}>
                    <p style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 13 }}>
                        No events are open for registration right now.
                    </p>
                </div>
            ) : (
                <div style={glassCard}>
                    <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${C.green}, ${C.blue}, transparent)`, opacity: 0.6 }} />

                    {/* Team Name */}
                    <div>
                        <label style={labelStyle}>Team Name</label>
                        <input
                            style={{ ...inputStyle, ...(nameTaken ? { borderColor: "rgba(239,68,68,0.6)" } : {}) }}
                            placeholder="e.g. ByteBuilders"
                            value={teamName}
                            onChange={(e) => { setTeamName(e.target.value); if (nameTaken) setNameTaken(false); }}
                            onFocus={handleFocus}
                            onBlur={(e) => { handleBlur(e); checkTeamNameExists(); }}
                        />
                        {nameTaken && (
                            <div style={{ color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 10.5, letterSpacing: "0.02em", marginTop: 6 }}>
                                This name is already taken in this event — pick another.
                            </div>
                        )}
                    </div>

                    {/* Event — fixed to the event the user is registering for (no picker). */}
                    <div>
                        <label style={labelStyle}>Event</label>
                        <div style={{
                            ...inputStyle,
                            display: "flex", alignItems: "center", justifyContent: "space-between",
                            cursor: "default", color: selectedEvent ? C.text : C.textMuted,
                        }}>
                            <span>{selectedEvent ? selectedEvent.name : "Loading event…"}</span>
                            <span style={{ color: C.greenMuted, fontSize: 10, letterSpacing: "0.08em" }}>CURRENT</span>
                        </div>
                    </div>

                    {/* Description */}
                    <div>
                        <label style={labelStyle}>Description (optional)</label>
                        <textarea
                            style={{ ...inputStyle, minHeight: 70, resize: "vertical" }}
                            placeholder="What is your team about?"
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                            onFocus={handleFocus}
                            onBlur={handleBlur}
                        />
                    </div>

                    {error && (
                        <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.35)", color: C.red, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, padding: "10px 12px" }}>
                            ERROR: {error}
                        </div>
                    )}

                    {/* Submit */}
                    <div style={{ marginTop: 4 }}>
                        <PixelButton variant="cyber" size="lg" fullWidth disabled={!canSubmit || submitting} onClick={handleCreate}>
                            {submitting ? "CREATING…" : "CREATE TEAM"}
                        </PixelButton>
                    </div>
                </div>
            )}
            </div>
        </div>
    );
}
