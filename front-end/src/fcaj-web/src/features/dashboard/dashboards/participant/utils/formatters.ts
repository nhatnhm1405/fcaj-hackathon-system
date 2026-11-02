export function fmtDate(iso?: string) {
    return iso ? new Date(iso).toLocaleDateString("en-US", { month: "long", day: "numeric", year: "numeric" }) : "—";
}

export function fmtDT(iso?: string | null): string {
    if (!iso) return "—";
    const d = new Date(iso);
    if (isNaN(d.getTime())) return iso;
    return `${String(d.getDate()).padStart(2, "0")}/${String(d.getMonth() + 1).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

export function fmtShort(iso?: string) {
    return iso ? new Date(iso).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" }) : "—";
}

export function roundStatusColor(status?: string): "green" | "yellow" | "red" | "gray" {
    const s = (status ?? "").toUpperCase();
    if (["ACTIVE", "OPEN", "IN_PROGRESS", "ADVANCED", "ELIGIBLE"].includes(s)) return "green";
    if (["UPCOMING", "PENDING", "DRAFT", "WAITING", "WAITING_FOR_RESULTS", "LOCKED"].includes(s)) return "yellow";
    if (["CLOSED", "CANCELLED", "ELIMINATED"].includes(s)) return "red";
    return "gray";
}

export function teamStatusColor(status?: string): "green" | "yellow" | "red" | "gray" {
    const s = (status ?? "").toUpperCase();
    if (s === "APPROVED") return "green";
    if (s === "PENDING") return "yellow";
    if (s === "REJECTED" || s === "DISQUALIFIED") return "red";
    return "gray";
}
