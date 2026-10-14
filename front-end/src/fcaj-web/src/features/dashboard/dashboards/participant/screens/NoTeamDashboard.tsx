import { useState, useEffect, CSSProperties } from "react";
import { C, PixelButton, PixelBadge, PixelCard, FloatingParticles } from "@/shared/components/PixelComponents";
import { teamsApi, roundsApi, HackathonEvent, ActiveEventWithTracks, Round } from "@/shared/apiClient";
import { useTour } from "@/app/providers/TourProvider";
import { ParticipantJourneyBar } from "@/shared/components/ParticipantJourneyBar";
import { EventName } from "@/features/events/eventUtils";
import { fmtShort } from "../utils/formatters";

const MS_PER_DAY = 24 * 60 * 60 * 1000;

type DeadlineTone = "muted" | "red" | "green";

function registrationDeadlineLabel(ev: ActiveEventWithTracks): { primary: string; secondary: string; tone: DeadlineTone } {
    const deadlineIso = ev.registrationEnd || ev.startDate;
    const deadline = new Date(deadlineIso ?? "");
    if (!deadlineIso || Number.isNaN(deadline.getTime())) {
        return { primary: "Deadline TBA", secondary: "Registration deadline not set", tone: "muted" };
    }

    const now = new Date();
    const diffMs = deadline.getTime() - now.getTime();

    if (diffMs < 0) {
        // The event is still OPEN and status is the single source of truth for
        // registration (dates are informational only, per the backend). A passed
        // deadline must not claim "closed" while teams are still being accepted.
        return { primary: "Open now", secondary: "Accepting teams", tone: "green" };
    }
    if (deadline.toDateString() === now.toDateString()) {
        return { primary: "Closes today", secondary: `Deadline: ${fmtShort(deadlineIso)}`, tone: "red" };
    }

    const diffDays = Math.ceil(diffMs / MS_PER_DAY);
    return {
        primary: `${diffDays} day${diffDays === 1 ? "" : "s"} left`,
        secondary: `Deadline: ${fmtShort(deadlineIso)}`,
        tone: "green",
    };
}

type PhaseBadge = "green" | "cyan" | "blue" | "gray" | "red" | "yellow";

// Visual identity for each event phase (event.status) — drives the phase banner
// label + colour and the header status badge, so the card never contradicts the
// real phase (e.g. showing "REGISTRATION OPEN" during the competition).
function eventPhaseBanner(phase: string): { label: string; color: string; bg: string; border: string; glow: string; badge: PhaseBadge } {
    switch (phase) {
        case "OPEN":        return { label: "REGISTRATION OPEN", color: "#22c55e", bg: "linear-gradient(90deg, rgba(34,197,94,0.18), rgba(59,130,246,0.10))", border: "rgba(34,197,94,0.45)", glow: "0 0 24px rgba(34,197,94,0.12)", badge: "green" };
        case "SETUP":       return { label: "SETUP PHASE", color: "#22d3ee", bg: "linear-gradient(90deg, rgba(6,182,212,0.18), rgba(59,130,246,0.10))", border: "rgba(6,182,212,0.45)", glow: "0 0 24px rgba(6,182,212,0.12)", badge: "cyan" };
        case "IN_PROGRESS": return { label: "COMPETITION LIVE", color: "#60a5fa", bg: "linear-gradient(90deg, rgba(59,130,246,0.20), rgba(139,92,246,0.10))", border: "rgba(59,130,246,0.5)", glow: "0 0 24px rgba(59,130,246,0.15)", badge: "blue" };
        case "COMPLETED":   return { label: "EVENT COMPLETED", color: "#cbd5e1", bg: "linear-gradient(90deg, rgba(148,163,184,0.14), rgba(100,116,139,0.06))", border: "rgba(148,163,184,0.35)", glow: "none", badge: "gray" };
        case "CANCELLED":   return { label: "EVENT CANCELLED", color: "#f87171", bg: "linear-gradient(90deg, rgba(239,68,68,0.16), rgba(239,68,68,0.06))", border: "rgba(239,68,68,0.4)", glow: "0 0 24px rgba(239,68,68,0.10)", badge: "red" };
        case "DRAFT":       return { label: "DRAFT", color: "#facc15", bg: "linear-gradient(90deg, rgba(234,179,8,0.14), rgba(234,179,8,0.06))", border: "rgba(234,179,8,0.35)", glow: "none", badge: "yellow" };
        default:            return { label: phase || "EVENT", color: "#cbd5e1", bg: "linear-gradient(90deg, rgba(148,163,184,0.12), rgba(100,116,139,0.05))", border: "rgba(148,163,184,0.3)", glow: "none", badge: "gray" };
    }
}

function trackSummary(track: { name: string; description?: string }) {
    if (track.description?.trim()) return track.description.trim();
    const name = track.name.toLowerCase();
    if (name.includes("ai")) return "Build intelligent solutions with data, automation, or machine learning.";
    if (name.includes("green")) return "Create sustainable products for energy, environment, or climate impact.";
    if (name.includes("social")) return "Solve community problems with accessible, practical technology.";
    if (name.includes("web")) return "Deliver a polished web product with strong UX and reliable engineering.";
    return "Explore this challenge track and shape a focused hackathon solution.";
}

// Liquid-glass tile tinted by an accent colour (rgb triplet, e.g. "6,182,212").
function glassTile(rgb: string): CSSProperties {
    return {
        background: `linear-gradient(135deg, rgba(${rgb},0.14) 0%, rgba(${rgb},0.05) 100%)`,
        border: `1px solid rgba(${rgb},0.34)`,
        backdropFilter: "blur(8px)",
        WebkitBackdropFilter: "blur(8px)",
        boxShadow: `inset 0 1px 0 rgba(255,255,255,0.08), 0 4px 20px rgba(0,0,0,0.18)`,
    };
}

export function NoTeamDashboard({
    onCreateTeam,
    onViewDetails,
    onWaitForInvite,
    pendingTeamName,
    pendingInviteCount,
    inactive = false,
    requestingActive = false,
    activeRequested = false,
    onRequestActive,
}: {
    onCreateTeam: (eventId?: number, trackId?: number) => void;
    onViewDetails: (event: HackathonEvent) => void;
    onWaitForInvite: () => void;
    pendingTeamName: string | null;
    pendingInviteCount: number;
    inactive?: boolean;
    requestingActive?: boolean;
    activeRequested?: boolean;
    onRequestActive?: () => void;
}) {
    const { openTour } = useTour();
    const txt = "#ffffff";
    const mut = "rgba(255,255,255,0.85)";
    const statPanelBorder = "rgba(34,197,94,0.24)";
    const detailPanelBg = "rgba(8, 18, 24, 0.28)";
    const detailToggleBg = "rgba(59,130,246,0.08)";
    const titleStyle: CSSProperties = { background: "linear-gradient(135deg, #22c55e 0%, #3b82f6 100%)", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent", backgroundClip: "text" };
    const [events, setEvents] = useState<ActiveEventWithTracks[]>([]);
    const [roundsByEvent, setRoundsByEvent] = useState<Record<number, Round[]>>({});
    const [expanded, setExpanded] = useState<Record<string, boolean>>({});

    useEffect(() => {
        teamsApi.getActiveEvents().then(res => {
            const evs = res.data ?? [];
            setEvents(evs);
            evs.forEach(ev => {
                roundsApi.getAll(ev.eventId)
                    .then(r => setRoundsByEvent(prev => ({ ...prev, [ev.eventId]: r.data ?? [] })))
                    .catch(() => {});
            });
        }).catch(() => setEvents([]));
    }, []);

    // ActiveEventWithTracks → HackathonEvent for the detail drawer.
    function toEvent(ev: ActiveEventWithTracks): HackathonEvent {
        return {
            eventId: ev.eventId, name: ev.name, season: ev.season ?? "", year: ev.year ?? new Date().getFullYear(),
            description: ev.description, registrationStart: ev.registrationStart ?? "", registrationEnd: ev.registrationEnd ?? "",
            startDate: ev.startDate ?? "", endDate: ev.endDate ?? "", status: (ev.status as HackathonEvent["status"]) ?? "OPEN",
        };
    }

    function toggleExpanded(key: string) {
        setExpanded(previous => ({ ...previous, [key]: !previous[key] }));
    }

    return (
        <div className="cyber-grid-bg" style={{ position: "relative", padding: "24px", minHeight: "100%" }}>
            {/* Ambient background — same cyber grid + floating particles as the pending-approval page. */}
            <FloatingParticles count={30} />
            <div style={{ position: "absolute", top: "6%", left: "50%", transform: "translateX(-50%)", width: 520, height: 520, borderRadius: "50%", background: "radial-gradient(circle, rgba(59,130,246,0.06), transparent 70%)", pointerEvents: "none" }} />
            <div style={{ position: "relative", zIndex: 1, display: "flex", flexDirection: "column", gap: 20 }}>
            {/* Live journey progress */}
            <ParticipantJourneyBar team={null} highlight />

            {/* Open events */}
            <div style={{ display: "flex", alignItems: "flex-end", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
                <div>
                    <div style={{ ...titleStyle, fontFamily: "'JetBrains Mono', monospace", fontSize: 22, fontWeight: 900, letterSpacing: "0.02em" }}>Open Events</div>
                </div>
                <PixelBadge color="green">{events.length} OPEN</PixelBadge>
            </div>

            {/* Inactive participant (finished a past event) + a competition is open →
                offer to rejoin. Between seasons (no open event) we show nothing here. */}
            {inactive && events.length > 0 && (
                <PixelCard glow glowColor="blue" gradient style={{ padding: 16 }}>
                    <div style={{ display: "flex", gap: 12, alignItems: "center", justifyContent: "space-between", flexWrap: "wrap" }}>
                        <p style={{ color: mut, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, margin: 0, lineHeight: 1.6 }}>
                            You're not in the current competition yet. Request to join this season to start registering a team.
                        </p>
                        <PixelButton variant="cyber" disabled={requestingActive || activeRequested} onClick={onRequestActive}>
                            {activeRequested ? "REQUEST SENT" : requestingActive ? "SENDING..." : "REQUEST TO COMPETE THIS SEASON"}
                        </PixelButton>
                    </div>
                </PixelCard>
            )}

            {pendingTeamName && (
                <PixelCard glow glowColor="blue" gradient style={{ padding: 16 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
                        <span style={{ color: mut, fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>
                            Your team <strong style={{ color: txt }}>{pendingTeamName}</strong> is waiting for coordinator approval.
                        </span>
                        <span style={{ background: "rgba(234,179,8,0.1)", border: "1px solid rgba(234,179,8,0.4)", color: "#eab308", fontFamily: "'JetBrains Mono', monospace", fontSize: 9, letterSpacing: "0.12em", padding: "2px 10px", flexShrink: 0 }}>PENDING</span>
                    </div>
                </PixelCard>
            )}

            {events.length === 0 ? (
                <PixelCard glow glowColor="blue" gradient style={{ padding: 20 }}>
                    <p style={{ color: mut, fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>No open events at this time. Check back later.</p>
                </PixelCard>
            ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
                    {events.map(ev => {
                        const evTracks = ev.tracks ?? [];
                        const evRounds = roundsByEvent[ev.eventId] ?? [];
                        // A round is only "current/active" once the event has actually entered the
                        // competition phase. While the event is still OPEN (registration) or in SETUP,
                        // no round is live yet — even if seed data marks one ACTIVE.
                        const phase = (ev.status ?? "").toUpperCase();
                        const activeRound = phase === "IN_PROGRESS"
                            ? evRounds.find(r => ["ACTIVE", "OPEN", "IN_PROGRESS"].includes((r.status ?? "").toUpperCase()))
                            : undefined;
                        const registrationDeadline = registrationDeadlineLabel(ev);
                        const banner = eventPhaseBanner(phase);
                        // The registration countdown only applies while the event is OPEN; once past
                        // registration (SETUP / IN_PROGRESS / COMPLETED) it is simply closed.
                        const regInfo = phase === "OPEN"
                            ? registrationDeadline
                            : { primary: "Registration closed", secondary: registrationDeadline.secondary, tone: "muted" as DeadlineTone };
                        const detailKey = `${ev.eventId}:detail`;
                        const detailOpen = Boolean(expanded[detailKey]);
                        return (
                            <PixelCard key={ev.eventId}
                                glow
                                glowColor="blue"
                                gradient
                                style={{
                                    padding: "28px 30px", minHeight: 236, display: "flex", flexDirection: "column", gap: 22,
                                    borderColor: "rgba(59,130,246,0.2)",
                                    // Inactive participants can't register yet — dim the whole event card and
                                    // block interaction; their only action is the "Request to compete" banner above.
                                    ...(inactive ? { opacity: 0.45, pointerEvents: "none" as const, filter: "grayscale(0.4)" } : {}),
                                }}
                            >
                                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 18, flexWrap: "wrap" }}>
                                    <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                                        <EventName size={28}>{ev.name}</EventName>
                                        {ev.topic && (
                                            <div style={{ color: txt, fontFamily: "'JetBrains Mono', monospace", fontSize: 12.5, opacity: 0.8 }}>{ev.topic}</div>
                                        )}
                                        <div style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
                                            <span style={{ color: txt, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, fontWeight: 800 }}>
                                                {fmtShort(ev.startDate)} - {fmtShort(ev.endDate)}
                                            </span>
                                            <PixelButton variant="secondary" size="sm" onClick={() => onViewDetails(toEvent(ev))}>VIEW DETAILS</PixelButton>
                                        </div>
                                    </div>
                                    <PixelBadge color={banner.badge}>{ev.status ?? "OPEN"}</PixelBadge>
                                </div>

                                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 14, flexWrap: "wrap", background: banner.bg, border: `1px solid ${banner.border}`, padding: "16px 18px", boxShadow: banner.glow }}>
                                    <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                                        <span style={{ width: 9, height: 9, borderRadius: "50%", background: banner.color, boxShadow: `0 0 10px ${banner.color}`, flexShrink: 0 }} />
                                        <span style={{ color: banner.color, fontFamily: "'JetBrains Mono', monospace", fontSize: 18, fontWeight: 900, letterSpacing: "0.1em", textShadow: banner.glow !== "none" ? `0 0 14px ${banner.color}66` : undefined }}>
                                            {banner.label}
                                        </span>
                                    </div>
                                    <div style={{ textAlign: "right" }}>
                                        <div style={{ color: regInfo.tone === "red" ? "#f59e0b" : regInfo.tone === "green" ? C.greenBright : txt, textShadow: regInfo.tone === "red" ? "0 0 14px rgba(245,158,11,0.55)" : regInfo.tone === "green" ? "0 0 12px rgba(34,197,94,0.35)" : undefined, fontFamily: "'JetBrains Mono', monospace", fontSize: 22, fontWeight: 900, lineHeight: 1.1 }}>
                                            {regInfo.primary}
                                        </div>
                                        <div style={{ color: mut, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, marginTop: 4 }}>
                                            {regInfo.secondary}
                                        </div>
                                    </div>
                                </div>

                                <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))", gap: 10 }}>
                                    <div style={{ ...glassTile("6,182,212"), padding: "12px 14px" }}>
                                        <div style={{ color: mut, fontFamily: "'JetBrains Mono', monospace", fontSize: 10, letterSpacing: "0.1em", textTransform: "uppercase" }}>Tracks</div>
                                        <div style={{ color: C.cyan, fontFamily: "'JetBrains Mono', monospace", fontSize: 22, fontWeight: 900, marginTop: 4 }}>{evTracks.length}</div>
                                    </div>
                                    <div style={{ ...glassTile("59,130,246"), padding: "12px 14px" }}>
                                        <div style={{ color: mut, fontFamily: "'JetBrains Mono', monospace", fontSize: 10, letterSpacing: "0.1em", textTransform: "uppercase" }}>Rounds</div>
                                        <div style={{ color: C.blueBright, fontFamily: "'JetBrains Mono', monospace", fontSize: 22, fontWeight: 900, marginTop: 4 }}>{evRounds.length}</div>
                                    </div>
                                    <div style={{ ...glassTile("34,197,94"), padding: "12px 14px" }}>
                                        <div style={{ color: mut, fontFamily: "'JetBrains Mono', monospace", fontSize: 10, letterSpacing: "0.1em", textTransform: "uppercase" }}>Current Round</div>
                                        <div style={{ color: activeRound ? C.green : mut, fontFamily: "'JetBrains Mono', monospace", fontSize: 15, fontWeight: 900, marginTop: 7 }}>
                                            {activeRound
                                                ? `${activeRound.name ?? "Qualifier"} ACTIVE`
                                                : phase === "SETUP" ? "Setup phase"
                                                : phase === "IN_PROGRESS" ? "Between rounds"
                                                : phase === "COMPLETED" ? "Event completed"
                                                : "Not started yet"}
                                        </div>
                                    </div>
                                </div>

                                <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                                    <div
                                        role="button"
                                        tabIndex={0}
                                        aria-expanded={detailOpen}
                                        onClick={() => toggleExpanded(detailKey)}
                                        onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); toggleExpanded(detailKey); } }}
                                        style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, background: detailOpen ? "rgba(59,130,246,0.1)" : detailToggleBg, border: `1px solid ${detailOpen ? "rgba(59,130,246,0.5)" : statPanelBorder}`, backdropFilter: "blur(2px)", padding: "10px 16px", cursor: "pointer", transition: "all 0.2s ease" }}
                                    >
                                        <span style={{ color: detailOpen ? C.blueBright : mut, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, fontWeight: 700, letterSpacing: "0.1em", textTransform: "uppercase" }}>Tracks &amp; Rounds details</span>
                                        <span style={{ display: "flex", alignItems: "center", gap: 8, flexShrink: 0 }}>
                                            <span style={{ color: mut, fontFamily: "'JetBrains Mono', monospace", fontSize: 10, letterSpacing: "0.08em", textTransform: "uppercase" }}>{detailOpen ? "Hide" : "Show"}</span>
                                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke={detailOpen ? C.blueBright : mut} strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={{ transform: detailOpen ? "rotate(180deg)" : "rotate(0deg)", transition: "transform 0.25s ease", pointerEvents: "none" }}>
                                                <polyline points="6 9 12 15 18 9" />
                                            </svg>
                                        </span>
                                    </div>

                                <div style={{ maxHeight: detailOpen ? 1200 : 0, opacity: detailOpen ? 1 : 0, transform: detailOpen ? "translateY(0)" : "translateY(-8px)", overflow: "hidden", transition: "max-height 0.32s ease, opacity 0.22s ease, transform 0.25s ease" }}>
                                    <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                                        <div style={{ background: detailPanelBg, border: "1px solid rgba(6,182,212,0.35)", backdropFilter: "blur(2px)", padding: "14px 16px" }}>
                                            <div style={{ color: C.cyan, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, fontWeight: 900, letterSpacing: "0.12em", textTransform: "uppercase", marginBottom: 10 }}>
                                                Tracks
                                            </div>
                                            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 10 }}>
                                                {evTracks.length === 0 ? (
                                                    <span style={{ color: mut, fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>No tracks configured.</span>
                                                ) : evTracks.map(t => (
                                                    <div key={t.trackId} style={{ background: "linear-gradient(135deg, rgba(6,182,212,0.16), rgba(59,130,246,0.1))", border: "1px solid rgba(6,182,212,0.42)", padding: "12px 14px", minHeight: 82, display: "flex", flexDirection: "column", justifyContent: "center", gap: 6 }}>
                                                        <div style={{ color: "#e0faff", fontFamily: "'JetBrains Mono', monospace", fontSize: 13, letterSpacing: "0.03em", fontWeight: 900 }}>
                                                            {t.name}
                                                        </div>
                                                        <div style={{ color: mut, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, lineHeight: 1.45 }}>
                                                            {trackSummary(t)}
                                                        </div>
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                        <div style={{ background: detailPanelBg, border: "1px solid rgba(59,130,246,0.35)", backdropFilter: "blur(2px)", padding: "14px 16px" }}>
                                            <div style={{ color: C.blueBright, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, fontWeight: 900, letterSpacing: "0.12em", textTransform: "uppercase", marginBottom: 10 }}>
                                                Rounds
                                            </div>
                                            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: 10 }}>
                                                {evRounds.length === 0 ? (
                                                    <span style={{ color: mut, fontFamily: "'JetBrains Mono', monospace", fontSize: 12 }}>No rounds configured.</span>
                                                ) : evRounds.map(round => {
                                                    const rActive = ["ACTIVE", "OPEN", "IN_PROGRESS"].includes((round.status ?? "").toUpperCase());
                                                    return (
                                                    <div key={round.roundId} style={{ background: "linear-gradient(135deg, rgba(59,130,246,0.14), rgba(34,197,94,0.08))", border: "1px solid rgba(59,130,246,0.38)", padding: "10px 12px", minHeight: 58 }}>
                                                        <div style={{ color: txt, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, fontWeight: 800 }}>
                                                            {round.name}
                                                        </div>
                                                        <div style={{ color: mut, fontFamily: "'JetBrains Mono', monospace", fontSize: 11, marginTop: 5 }}>
                                                            <span style={{ color: rActive ? C.green : mut, fontWeight: rActive ? 800 : 400 }}>{round.status ?? "TBA"}</span> · Deadline {fmtShort(round.submissionDeadline)}
                                                        </div>
                                                    </div>
                                                    );
                                                })}
                                            </div>
                                        </div>
                                    </div>
                                </div>
                                </div>

                                <div style={{ display: "flex", gap: 12, flexWrap: "wrap", alignItems: "center", marginTop: "auto" }}>
                                    <PixelButton variant="cyber" size="md" onClick={() => onCreateTeam(ev.eventId)}>
                                        REGISTER &amp; CREATE TEAM
                                    </PixelButton>
                                    <div style={{ position: "relative", display: "inline-flex" }}>
                                        <button
                                            type="button"
                                            onClick={onWaitForInvite}
                                            style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: 14, fontWeight: 700, letterSpacing: "0.06em", textTransform: "uppercase", color: "#ffffff", background: "rgba(34,197,94,0.08)", border: "1px solid rgba(34,197,94,0.5)", boxShadow: "inset 0 0 20px rgba(34,197,94,0.05)", padding: "10px 20px", borderRadius: 0, cursor: "pointer", transition: "all 0.18s ease" }}
                                            onMouseEnter={(e) => { const el = e.currentTarget as HTMLElement; el.style.background = "rgba(34,197,94,0.16)"; el.style.borderColor = C.green; el.style.boxShadow = "0 0 16px rgba(34,197,94,0.3)"; }}
                                            onMouseLeave={(e) => { const el = e.currentTarget as HTMLElement; el.style.background = "rgba(34,197,94,0.08)"; el.style.borderColor = "rgba(34,197,94,0.5)"; el.style.boxShadow = "inset 0 0 20px rgba(34,197,94,0.05)"; }}
                                        >
                                            WAIT FOR INVITE
                                        </button>
                                        {pendingInviteCount > 0 && (
                                            <span style={{ position: "absolute", top: -8, right: -8, minWidth: 20, height: 20, borderRadius: "50%", background: C.blue, color: "#fff", fontFamily: "'JetBrains Mono', monospace", fontSize: 11, fontWeight: 800, display: "flex", alignItems: "center", justifyContent: "center", boxShadow: `0 0 10px rgba(59,130,246,0.75)`, pointerEvents: "none" }}>
                                                {pendingInviteCount}
                                            </span>
                                        )}
                                    </div>
                                </div>
                            </PixelCard>
                        );
                    })}
                </div>
            )}

            <button
                type="button"
                aria-label="How it works"
                title="How it works"
                onClick={openTour}
                style={{ position: "fixed", right: 24, bottom: 24, zIndex: 50, width: 52, height: 52, borderRadius: "50%", background: `linear-gradient(135deg, ${C.green}, ${C.blue})`, border: "1px solid rgba(255,255,255,0.28)", cursor: "pointer", color: "#fff", fontFamily: "'JetBrains Mono', monospace", fontSize: 24, fontWeight: 900, display: "flex", alignItems: "center", justifyContent: "center", boxShadow: "0 0 18px rgba(34,197,94,0.55), 0 0 34px rgba(59,130,246,0.35)" }}
                onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.transform = "translateY(-2px) scale(1.04)"; }}
                onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.transform = "none"; }}
            >
                ?
            </button>
            </div>
        </div>
    );
}
