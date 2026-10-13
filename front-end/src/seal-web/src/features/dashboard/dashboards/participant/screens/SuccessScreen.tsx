import { C, GradientText, PixelButton } from "@/shared/components/PixelComponents";

export function SuccessScreen({
    teamName,
    onDashboard,
    onViewTeam,
}: {
    teamName: string;
    onDashboard: () => void;
    onViewTeam: () => void;
}) {
    return (
        <div style={{ padding: "60px 24px", display: "flex", justifyContent: "center" }}>
            <div style={{ maxWidth: 480, width: "100%" }}>
                <div style={{
                    position: "relative",
                    padding: 40,
                    textAlign: "center",
                    overflow: "hidden",
                    border: "1px solid transparent",
                    background: "linear-gradient(#0d1117, #0d1117) padding-box, linear-gradient(135deg, rgba(34,197,94,0.5), rgba(59,130,246,0.4), rgba(34,197,94,0.2)) border-box",
                    boxShadow: "0 0 40px rgba(34,197,94,0.12), 0 0 80px rgba(59,130,246,0.08)",
                }}>
                    <div style={{ position: "absolute", top: 0, left: 0, width: 16, height: 16, borderTop: `2px solid ${C.green}`, borderLeft: `2px solid ${C.green}` }} />
                    <div style={{ position: "absolute", bottom: 0, right: 0, width: 16, height: 16, borderBottom: `2px solid ${C.blue}`, borderRight: `2px solid ${C.blue}` }} />
                    <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${C.green}, ${C.blue}, transparent)`, opacity: 0.7 }} />

                    <div style={{
                        width: 72, height: 72, borderRadius: "50%",
                        background: "rgba(34,197,94,0.1)",
                        border: `2px solid rgba(34,197,94,0.5)`,
                        boxShadow: "0 0 20px rgba(34,197,94,0.3), 0 0 40px rgba(34,197,94,0.12)",
                        display: "flex", alignItems: "center", justifyContent: "center",
                        margin: "0 auto 24px",
                    }}>
                        <svg width="32" height="32" viewBox="0 0 24 24" fill="none">
                            <path d="M4 12l5 5L20 7" stroke="#22c55e" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
                        </svg>
                    </div>

                    <h1 style={{ fontFamily: "'JetBrains Mono', monospace", fontWeight: 900, fontSize: 30, lineHeight: 1.1, marginBottom: 14 }}>
                        <GradientText>Team Created!</GradientText>
                    </h1>

                    <div style={{ color: C.green, fontFamily: "'JetBrains Mono', monospace", fontSize: 13, fontWeight: 700, letterSpacing: "0.06em", marginBottom: 12 }}>
                        {teamName}
                    </div>

                    <p style={{ color: C.textMuted, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, lineHeight: 1.8, marginBottom: 24 }}>
                        Your team is now pending coordinator approval. You can submit your project once approved.
                    </p>

                    <div style={{
                        display: "inline-flex", alignItems: "center", gap: 6,
                        background: "rgba(234,179,8,0.1)", border: "1px solid rgba(234,179,8,0.4)",
                        color: "#eab308", fontFamily: "'JetBrains Mono', monospace",
                        fontSize: 10, letterSpacing: "0.14em", padding: "5px 16px", marginBottom: 32,
                    }}>
                        <span style={{ width: 6, height: 6, background: "#eab308", borderRadius: "50%", display: "inline-block" }} />
                        PENDING APPROVAL
                    </div>

                    <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                        <PixelButton variant="cyber" size="lg" fullWidth onClick={onDashboard}>
                            GO TO MY DASHBOARD
                        </PixelButton>
                        <PixelButton variant="secondary" size="lg" fullWidth onClick={onViewTeam}>
                            VIEW TEAM
                        </PixelButton>
                    </div>
                </div>
            </div>
        </div>
    );
}
