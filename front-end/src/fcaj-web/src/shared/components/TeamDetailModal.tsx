import React from "react";
import { createPortal } from "react-dom";
import { C, PixelBadge } from "@/shared/components/PixelComponents";
import { universityLabel } from "@/shared/userDisplay";

const mono = "'JetBrains Mono', monospace";

/** One member's full profile, shared by mentor + judge team-detail views. */
export interface TeamDetailMember {
  userId: number;
  fullName: string;
  email: string;
  memberRole: "LEADER" | "MEMBER";
  studentId?: string | null;
  userType?: string | null;   // FPT_STUDENT | EXTERNAL_STUDENT | STAFF
  university?: string | null;
  /** Whether this member's own account is currently active — a member can be
   *  on the roster (e.g. after a team rejoin) but still need their own
   *  separate reactivation. Undefined callers show no indicator at all. */
  isActive?: boolean | null;
}

/** A labelled fact shown in the modal's info block (Round / Status / Track / …). */
export interface TeamInfoRow {
  label: string;
  value: React.ReactNode;
}

interface Props {
  open: boolean;
  /** Team display name (real name — the judge view is de-anonymized on purpose). */
  teamName: string;
  /** Ordered Label: value facts shown at the top (Round, Status, Track, Members…). */
  infoRows: TeamInfoRow[];
  members: TeamDetailMember[];
  /** Extra sections below the roster (rarely needed now info lives up top). */
  children?: React.ReactNode;
  /** Optional per-member action rendered next to their role badge (e.g. the
   *  Coordinator's "Remove from competition"). Omitted by mentor/judge callers,
   *  who show no per-member actions. */
  renderMemberAction?: (member: TeamDetailMember) => React.ReactNode;
  onClose: () => void;
}

/** Leader first, then members in their given order — so #1 is always the leader. */
function orderedMembers(members: TeamDetailMember[]): TeamDetailMember[] {
  return [...members].sort((a, b) => {
    const rank = (m: TeamDetailMember) => (m.memberRole === "LEADER" ? 0 : 1);
    return rank(a) - rank(b);
  });
}

/** Human label for the raw user_type token. */
function userTypeLabel(t?: string | null): string {
  switch ((t ?? "").toUpperCase()) {
    case "FPT_STUDENT": return "FPT Student";
    case "EXTERNAL_STUDENT": return "External Student";
    case "STAFF": return "Staff";
    default: return t || "—";
  }
}

/** Small labelled field inside a member card. */
function Field({ label, value }: { label: string; value: React.ReactNode }) {
  const empty = value === undefined || value === null || value === "";
  return (
    <div style={{ minWidth: 0 }}>
      <div style={{ color: C.greenMuted, fontFamily: mono, fontSize: 9, letterSpacing: "0.12em", textTransform: "uppercase" }}>{label}</div>
      <div style={{ color: empty ? C.textDim : C.text, fontFamily: mono, fontSize: 12.5, marginTop: 3, wordBreak: "break-word" }}>{empty ? "—" : value}</div>
    </div>
  );
}

function MemberCard({ m, index, action }: { m: TeamDetailMember; index: number; action?: React.ReactNode }) {
  const isLeader = m.memberRole === "LEADER";
  const accent = "#22c55e";
  return (
    <div style={{ padding: "14px 16px", background: C.surface2, border: `1px solid ${isLeader ? "rgba(34,197,94,0.4)" : C.border}` }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10, marginBottom: 12 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12, minWidth: 0 }}>
          {/* Position in the team: #1, #2, … */}
          <span style={{
            flexShrink: 0, display: "inline-flex", alignItems: "center", justifyContent: "center",
            minWidth: 30, height: 30, padding: "0 6px",
            fontFamily: mono, fontSize: 13, fontWeight: 800,
            color: isLeader ? accent : C.textMuted,
            background: isLeader ? "rgba(34,197,94,0.14)" : C.surface,
            border: `1px solid ${isLeader ? accent : C.border}`,
          }}>#{index}</span>
          <span style={{ color: isLeader ? accent : C.text, fontFamily: mono, fontSize: 17, fontWeight: 800, letterSpacing: "0.01em", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
            {m.fullName}
          </span>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 10, flexShrink: 0 }}>
          <PixelBadge color={isLeader ? "green" : "gray"}>{m.memberRole}</PixelBadge>
          {m.isActive === false && <PixelBadge color="red">INACTIVE</PixelBadge>}
          {action}
        </div>
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, minmax(0, 1fr))", gap: 14 }}>
        <Field label="MSSV" value={m.studentId} />
        <Field label="Type" value={userTypeLabel(m.userType)} />
        <Field label="University" value={universityLabel(m.userType, m.university)} />
        <Field label="Email" value={m.email} />
      </div>
    </div>
  );
}

/** One `LABEL   value` line in the top info block, colours kept high-contrast. */
function InfoLine({ label, value }: TeamInfoRow) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
      <span style={{ flex: "0 0 88px", color: C.greenMuted, fontFamily: mono, fontSize: 11, letterSpacing: "0.08em", textTransform: "uppercase" }}>{label}</span>
      <span style={{ color: C.text, fontFamily: mono, fontSize: 13, display: "inline-flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>{value}</span>
    </div>
  );
}

/**
 * Shared modal that shows a team and the full profile of every member
 * (name, role, MSSV, user type, university, email), with a clear
 * `Round / Status / Track / Members` info block up top.
 *
 * Used by the mentor "My Tracks" view and the judge scoring view. Note: on the
 * judge side this intentionally lifts team anonymisation (see scoring/anon.ts).
 */
export function TeamDetailModal({ open, teamName, infoRows, members, children, renderMemberAction, onClose }: Props) {
  if (!open) return null;
  const accent = "#22c55e";

  return createPortal(
    <>
      <div
        onClick={onClose}
        style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.65)", zIndex: 1000, backdropFilter: "blur(2px)" }}
      />
      <div
        role="dialog"
        aria-modal="true"
        style={{
          position: "fixed", top: "50%", left: "50%", transform: "translate(-50%, -50%)", zIndex: 1001,
          width: "min(880px, calc(100vw - 32px))", maxHeight: "calc(100vh - 64px)", overflow: "auto",
          background: C.surface, border: `1px solid ${accent}66`,
          boxShadow: `0 0 40px ${accent}22, 0 16px 48px rgba(0,0,0,0.4)`, padding: 28,
        }}
      >
        <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 2, background: `linear-gradient(90deg, ${accent}, transparent)` }} />

        {/* Title */}
        <h2 style={{ fontFamily: mono, fontSize: 22, fontWeight: 800, color: C.text, lineHeight: 1.25, wordBreak: "break-word", marginBottom: 16 }}>{teamName}</h2>

        {/* Info block: Round / Status / Track / Members … */}
        {infoRows.length > 0 && (
          <div style={{ display: "flex", flexDirection: "column", gap: 9, padding: "14px 16px", background: C.surface2, border: `1px solid ${C.border}`, marginBottom: 20 }}>
            {infoRows.map((r, i) => <InfoLine key={i} label={r.label} value={r.value} />)}
          </div>
        )}

        {/* Members */}
        <div style={{ color: C.green, fontFamily: mono, fontSize: 11, fontWeight: 700, letterSpacing: "0.12em", marginBottom: 12 }}>
          MEMBERS · {members.length}
        </div>
        {members.length === 0 ? (
          <div style={{ color: C.textMuted, fontFamily: mono, fontSize: 12 }}>No members in this team.</div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            {orderedMembers(members).map((m, i) => (
              <MemberCard key={m.userId} m={m} index={i + 1} action={renderMemberAction?.(m)} />
            ))}
          </div>
        )}

        {children && <div style={{ marginTop: 18 }}>{children}</div>}

        <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 22 }}>
          <button
            type="button"
            onClick={onClose}
            style={{
              fontFamily: mono, fontSize: 12, padding: "8px 18px", cursor: "pointer",
              background: C.surface2, border: `1px solid ${C.border}`, color: C.textMuted, borderRadius: 0,
            }}
          >
            CLOSE
          </button>
        </div>
      </div>
    </>,
    document.body,
  );
}
