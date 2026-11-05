import { C } from "@/shared/components/PixelComponents";
import { universityLabel } from "@/shared/userDisplay";

const mono = "'JetBrains Mono', monospace";

/** A team member as shown in any history page (text form, no modal). */
export interface HistoryMember {
  fullName: string;
  memberRole?: string | null;   // LEADER | MEMBER
  studentId?: string | null;    // MSSV
  userType?: string | null;     // FPT_STUDENT | EXTERNAL_STUDENT | STAFF
  university?: string | null;
}

function userTypeLabel(t?: string | null): string {
  switch ((t ?? "").toUpperCase()) {
    case "FPT_STUDENT": return "FPT Student";
    case "EXTERNAL_STUDENT": return "External Student";
    case "STAFF": return "Staff";
    default: return t || "";
  }
}

/**
 * Consistent, text-only member roster used across the participant / mentor /
 * judge history pages: "MEMBERS (n)" then one line each —
 * "Name — ROLE · MSSV · Type · University".
 */
export function MemberTextList({ members, label = "Members" }: { members: HistoryMember[]; label?: string }) {
  if (!members || members.length === 0) {
    return <div style={{ color: C.textDim, fontFamily: mono, fontSize: 11 }}>No members.</div>;
  }
  return (
    <div>
      <div style={{ color: C.greenMuted, fontFamily: mono, fontSize: 10, letterSpacing: "0.12em", textTransform: "uppercase", marginBottom: 6 }}>
        {label} ({members.length})
      </div>
      <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
        {members.map((m, i) => {
          const isLeader = (m.memberRole ?? "").toUpperCase() === "LEADER";
          const meta = [
            m.memberRole ?? null,
            m.studentId ?? null,
            userTypeLabel(m.userType) || null,
            universityLabel(m.userType, m.university),
          ].filter(Boolean).join(" · ");
          return (
            <div key={i} style={{ fontFamily: mono, fontSize: 12, lineHeight: 1.5, wordBreak: "break-word" }}>
              <span style={{ color: isLeader ? C.green : C.text, fontWeight: 600 }}>{m.fullName}</span>
              {meta && <span style={{ color: C.textMuted }}>{"  —  " + meta}</span>}
            </div>
          );
        })}
      </div>
    </div>
  );
}
