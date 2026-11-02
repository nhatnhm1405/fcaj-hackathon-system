import { useState, useEffect } from "react";
import { C, PixelBadge, PixelButton } from "@/shared/components/PixelComponents";
import { tracksApi, roundsApi, teamsApi, ApiError, apiErrorMessage, HackathonEvent, Track, Round } from "@/shared/apiClient";
import { useAuth } from "@/app/providers/AuthProvider";
import { useNotifications } from "@/app/providers/NotificationProvider";
import { ConfirmDialog } from "@/shared/components/ConfirmDialog";
import { EventName } from "@/features/events/eventUtils";
import { fmtDate, fmtShort } from "../utils/formatters";

function roundBadgeColor(status?: string) {
    const s = (status ?? "").toUpperCase();
    if (["ACTIVE", "OPEN", "IN_PROGRESS"].includes(s)) return { bg: "rgba(34,197,94,0.14)", color: "#22c55e", shadow: "0 0 8px rgba(34,197,94,0.35)" };
    if (["UPCOMING", "PENDING", "DRAFT"].includes(s)) return { bg: "rgba(234,179,8,0.12)", color: "#eab308", shadow: "none" };
    return { bg: "rgba(239,68,68,0.10)", color: "#ef4444", shadow: "none" };
}

export function EventDetailDrawer({
    event,
    onClose,
    onCreateTeam,
}: {
    event: HackathonEvent;
    onClose: () => void;
    onCreateTeam: (eventId: number) => void;
}) {
    const [tracks, setTracks] = useState<Track[]>([]);
    const [rounds, setRounds] = useState<Round[]>([]);
    const { patchCurrentUser } = useAuth();
    const { addToast } = useNotifications();
    const [confirmingLeave, setConfirmingLeave] = useState(false);
    const [leaving, setLeaving] = useState(false);
    const [leaveError, setLeaveError] = useState<string | null>(null);

    useEffect(() => {
        tracksApi.getAll(event.eventId).then(r => setTracks(r.data ?? [])).catch(() => setTracks([]));
        roundsApi.getAll(event.eventId)
            .then(r => setRounds([...(r.data ?? [])].sort((a, b) => (a.orderNumber ?? a.roundId) - (b.orderNumber ?? b.roundId))))
            .catch(() => setRounds([]));
    }, [event.eventId]);

    // Teamless-only opt-out for the current season — only while registration is
    // still OPEN (mirrors the backend gate). Distinct from leaving a team: this
    // deactivates the account instead of just clearing team membership.
    async function leaveEvent() {
        setLeaving(true);
        setLeaveError(null);
        try {
            await teamsApi.leaveEvent(event.eventId);
            patchCurrentUser({ is_active: false });
            addToast({ type: "info", title: "Left event", message: `You have left "${event.name}" for this season.` });
            setConfirmingLeave(false);
            onClose();
        } catch (err) {
            setLeaveError(err instanceof ApiError ? err.message : "Failed to leave this event.");
            addToast({ type: "warning", title: "Leave failed", message: apiErrorMessage(err, "Failed to leave this event.") });
        } finally {
            setLeaving(false);
        }
    }

    const seasonLabel = `${(event.season ?? "").toUpperCase()} ${event.year ?? ""}`.trim();

    const txt = "#ffffff";
    const mut = "rgba(255,255,255,0.85)";
    // Real event status drives the badge colour (not always green).
    const st = (event.status ?? "").toUpperCase();
    const statusBadgeColor: "green" | "blue" | "red" | "yellow" =
        st === "OPEN" ? "green" : st === "COMPLETED" ? "blue" : ["CLOSED", "CANCELLED"].includes(st) ? "red" : "yellow";

    return (
        <>
            <div onClick={onClose} style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.55)", zIndex: 200, backdropFilter: "blur(2px)" }} />

            <div style={{
                position: "fixed", top: 0, right: 0, bottom: 0, width: "min(540px, 100vw)",
                background: C.surface, borderLeft: `1px solid ${C.border}`, zIndex: 201,
                overflowY: "auto", display: "flex", flexDirection: "column", boxShadow: "-8px 0 40px rgba(0,0,0,0.6)",
            }}>
                {/* Header */}
                <div style={{ padding: "16px 24px", borderBottom: `1px solid ${C.border}`, display: "flex", alignItems: "center", gap: 12, flexShrink: 0, position: "sticky", top: 0, background: C.surface, zIndex: 1 }}>
                    <button
                        onClick={onClose}
                        style={{ background: "none", border: "none", cursor: "pointer", color: C.textMuted, padding: "2px 6px", display: "flex", alignItems: "center", gap: 6, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, transition: "color 0.15s" }}
                        onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.color = C.green; }}
                        onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.color = C.textMuted; }}
                    >
                        ← Back
                    </button>
                    <span style={{ color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 15, fontWeight: 700 }}>Event Detail</span>
                </div>

                <div style={{ padding: "24px", display: "flex", flexDirection: "column", gap: 28 }}>
                    {/* Event heading */}
                    <div>
                        <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 10, flexWrap: "wrap" }}>
                            <span style={{ background: "rgba(34,197,94,0.1)", border: `1px solid rgba(34,197,94,0.3)`, color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 10, letterSpacing: "0.1em", padding: "2px 10px" }}>{seasonLabel}</span>
                            <PixelBadge color={statusBadgeColor}>{event.status}</PixelBadge>
                        </div>
                        <h2 style={{ lineHeight: 1.1, marginBottom: 8 }}>
                            <EventName size={34}>{event.name}</EventName>
                        </h2>
                        {event.topic && (
                            <div style={{ color: txt, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, lineHeight: 1.5, marginBottom: 8 }}>{event.topic}</div>
                        )}
                        <div style={{ color: mut, fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>
                            {fmtShort(event.startDate)} — {fmtShort(event.endDate)}
                        </div>
                    </div>

                    {/* Consolidated note — folds the scattered "track assignment" + "team leader" hints into one short callout. */}
                    <div style={{ display: "flex", gap: 10, alignItems: "flex-start", background: "rgba(59,130,246,0.06)", border: "1px solid rgba(59,130,246,0.25)", borderLeft: `3px solid ${C.blue}`, padding: "12px 14px" }}>
                        <span style={{ color: C.blueBright, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, fontWeight: 900, lineHeight: 1.4, flexShrink: 0 }}>i</span>
                        <div style={{ color: mut, fontFamily: "'JetBrains Mono', monospace", fontSize: 11.5, lineHeight: 1.65 }}>
                            <strong style={{ color: txt }}>Register as a team</strong> — you become its leader. You don't pick a track now; one is <strong style={{ color: txt }}>assigned during Setup</strong> (leader self-selects or coordinator draws).
                        </div>
                    </div>

                    {/* Tracks — informational only. Participants do NOT pick a track when
                        registering; it is assigned during the Setup phase. */}
                    <div>
                        <div style={{ color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 15, fontWeight: 700, marginBottom: 8 }}>Available Tracks</div>
                        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                            {tracks.map(tr => (
                                <div key={tr.trackId}
                                    style={{ background: C.surface, border: `1px solid ${C.border}`, padding: "16px", display: "flex", flexDirection: "column", gap: 8, position: "relative", overflow: "hidden" }}
                                >
                                    <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${C.green}, transparent)`, opacity: 0.5 }} />
                                    <div style={{ color: txt, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, fontWeight: 700 }}>{tr.name}</div>
                                    {tr.description && (
                                        <div style={{ color: mut, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, lineHeight: 1.5 }}>{tr.description}</div>
                                    )}
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Rounds */}
                    <div>
                        <div style={{ color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 15, fontWeight: 700, marginBottom: 14 }}>Rounds</div>
                        <div style={{ display: "flex", flexDirection: "column", gap: 0 }}>
                            {rounds.map((r, i) => {
                                const badge = roundBadgeColor(r.status);
                                const isLast = i === rounds.length - 1;
                                const isActive = ["ACTIVE", "OPEN", "IN_PROGRESS"].includes((r.status ?? "").toUpperCase());
                                return (
                                    <div key={r.roundId} style={{ display: "flex", alignItems: "stretch", gap: 0 }}>
                                        <div style={{ width: 28, display: "flex", flexDirection: "column", alignItems: "center", flexShrink: 0 }}>
                                            <div style={{
                                                width: 10, height: 10, borderRadius: "50%", flexShrink: 0,
                                                background: isActive ? C.green : "transparent",
                                                border: isActive ? `2px solid ${C.green}` : `2px solid rgba(255,255,255,0.2)`,
                                                boxShadow: isActive ? `0 0 8px rgba(34,197,94,0.5)` : "none", marginTop: 14,
                                            }} />
                                            {!isLast && <div style={{ flex: 1, width: 2, background: C.border, minHeight: 24, marginTop: 4 }} />}
                                        </div>
                                        <div style={{ flex: 1, padding: "10px 14px 16px", display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: 12 }}>
                                            <div>
                                                <div style={{ color: isActive ? C.green : txt, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, fontWeight: 700, marginBottom: 4 }}>
                                                    Round {r.orderNumber} — {r.name}{r.isFinal ? " · Final" : ""}
                                                </div>
                                                <div style={{ color: mut, fontFamily: "'JetBrains Mono', monospace", fontSize: 10 }}>Deadline: {fmtDate(r.submissionDeadline)}</div>
                                            </div>
                                            {r.status && (
                                                <div style={{ background: badge.bg, color: badge.color, fontFamily: "'JetBrains Mono', monospace", fontSize: 9, letterSpacing: "0.12em", padding: "3px 10px", flexShrink: 0, boxShadow: badge.shadow }}>{r.status}</div>
                                            )}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    </div>

                    {/* Register CTA — no track choice here; the team picks/gets a track at Setup */}
                    <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                        <PixelButton variant="cyber" fullWidth onClick={() => onCreateTeam(event.eventId)}>
                            REGISTER &amp; CREATE TEAM
                        </PixelButton>
                        {/* Sensitive, low-key opt-out — only while registration is still open.
                            Deliberately understated vs. the primary CTA above. */}
                        {st === "OPEN" && (
                            <button
                                type="button"
                                onClick={() => setConfirmingLeave(true)}
                                style={{
                                    background: "none", border: "none", cursor: "pointer",
                                    color: "rgba(239,68,68,0.75)", fontFamily: "'JetBrains Mono', monospace",
                                    fontSize: 11, padding: "4px 0", textAlign: "center", letterSpacing: "0.04em",
                                }}
                            >
                                Leave this event
                            </button>
                        )}
                    </div>
                </div>
            </div>

            {confirmingLeave && (
                <ConfirmDialog
                    title="Leave this event?"
                    message={
                        <>
                            You'll become inactive for <strong style={{ color: txt }}>{event.name}</strong> and won't be
                            able to register a team this season. You can request to rejoin later if the event is still
                            open, or a future season.
                        </>
                    }
                    confirmLabel="LEAVE EVENT"
                    variant="danger"
                    working={leaving}
                    error={leaveError}
                    onConfirm={leaveEvent}
                    onClose={() => { if (!leaving) { setConfirmingLeave(false); setLeaveError(null); } }}
                />
            )}
        </>
    );
}
