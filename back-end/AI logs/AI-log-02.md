# AI Session Log — FE/BE Data Sync (mockData.ts ↔ DB Schema)

**Date:** 2026-06-09
**Project:** SEAL – Software Engineering Hackathon Management System
**Branch:** `NhatNHM-AddNavbarlogodesign`
**Scope:** Đồng bộ `mockData.ts` (Frontend) với `seal_hackathon.sql` + `seal_seed.sql` (Backend)
**Source of truth:** DB schema — ngoại trừ `TeamInvite` và `AccountApproval` (FE đã build UI trước)

---

## Bối cảnh

FE đi nhanh hơn BE, dẫn đến data model bị lệch: tên field sai, enum value không khớp, một số bảng chỉ có ở FE mock mà chưa có trong DB schema. Toàn bộ thay đổi dưới đây là để đưa hai bên về cùng một source of truth.

---

## P0 — User Model & Enum Fixes

**File:** `src/shared/mocks/mockData.ts`

### Interface `User`
| Thay đổi | Chi tiết |
|----------|----------|
| REMOVED | `role`, `student_type`, `university` |
| ADDED | `user_type: 'FPT_STUDENT' \| 'EXTERNAL_STUDENT' \| 'STAFF'` |
| ADDED | `university_name: string \| null` |
| ADDED | `is_approved: boolean`, `is_active: boolean` |
| RENAMED | `name` → `full_name` |

### Các interface khác
| Interface | Thay đổi |
|-----------|----------|
| `HackathonEvent` | `event_name` → `name`; thêm `'CANCELLED'` |
| `Track` | `track_name` → `name`; xoá `max_teams` |
| `Round` | `round_name` → `name`; `round_order` → `order_number`; `'UPCOMING'` → `'PENDING'`; thêm `'FINALIZED'` |
| `Team` | `team_name` → `name`; thêm `event_id`; xoá `leader_id`; `'ELIMINATED'` → `'DISQUALIFIED'` |
| `TeamMember` | `is_leader: boolean` → `member_role: 'LEADER' \| 'MEMBER'` |
| `ScoringCriteria` | `criteria_name` → `name` |
| `Score` | `judge_id` → `judge_user_id`; `score_value` → `value` |
| `Prize` | `prize_name` → `name`; `rank_position` bỏ `null` (NOT NULL theo DB) |
| `AuditLog` | `performed_by` → `actor_user_id`; `action_type` → `action`; `entity_type` → `target_type`; `entity_id` → `target_id`; `details` → `metadata_json` |
| `AppNotification` | `user_id` → `recipient_user_id`; `message` → `content`; type `'info'\|'success'\|'warning'` → `'ANNOUNCEMENT'\|'RESULT'\|'REMINDER'\|'ASSIGNMENT'\|'APPROVAL'` |

### Interface mới: `UserEventRole`
Thay thế `JudgeAssignment` + `MentorAssignment` — ánh xạ trực tiếp với bảng `UserEventRole` trong DB:
```typescript
export interface UserEventRole {
  id: number;
  user_id: number;
  role_name: 'EVENT_COORDINATOR' | 'MENTOR' | 'JUDGE';
  event_id: number | null;
  track_id: number | null;
  round_id: number | null;
  judge_type: 'INTERNAL' | 'GUEST' | null;
  assigned_at: string;
  assigned_by: number | null;
}
```

**File:** `src/app/providers/AuthProvider.tsx`

- `buildAuthUser()`: derive `role` từ `user.user_type` + `userEventRoles` (thay vì `user.role`)
- `deriveDefaultEvent()`: dùng `userEventRoles` thay vì `judgeAssignments`/`mentorAssignments`
- `AuthUser.is_leader` — **giữ nguyên** (FE routing concept, không phải DB field)

---

## P1 — Field Renames Across 23 Component Files

Batch rename qua PowerShell. Các pattern chính:

| Old | New |
|-----|-----|
| `.event_name` | `.name` |
| `.track_name` | `.name` |
| `.round_name` | `.name` |
| `.round_order` | `.order_number` |
| `.team_name` | `.name` |
| `.criteria_name` | `.name` |
| `.judge_id` | `.judge_user_id` |
| `.score_value` | `.value` |
| `.prize_name` | `.name` |
| `.performed_by` | `.actor_user_id` |
| `.action_type` | `.action` |
| `.entity_type` | `.target_type` |
| `.entity_id` | `.target_id` |
| `.details` | `.metadata_json` |
| `.message` (notification) | `.content` |

**Files:** `DashboardLayout`, `CoordinatorDashboard`, `JudgeDashboard`, `MentorDashboard`, `ParticipantDashboard`, `TeamLeaderDashboard`, `TeamMemberDashboard`, `CoordEventsPage`, `CoordPrizesPage`, `CoordJudgesPage`, `CoordScoringPage`, `JudgeHistoryPage`, `JudgeScoringPage`, `LeaderboardPage`, `TeamSubmitPage`, `CoordTeamsPage`, `TeamCreatePage`, `TeamManagePage`, `TeamViewPage`, `MentorTracksPage`, `CoordAccountsPage`, `CoordAuditPage`, `ProfilePage`

**Special case — `TeamViewPage.tsx`**: `m.is_leader` (TeamMember) đổi tay từng dòng vì `currentUser.is_leader` (AuthUser) phải giữ nguyên:
```
m.is_leader: false            → member_role: 'MEMBER' as const
{ ...m, is_leader: true }     → { ...m, member_role: 'LEADER' as const }
m.is_leader ? 'cyan' : 'blue' → m.member_role === 'LEADER' ? 'cyan' : 'blue'
!m.is_leader && (             → m.member_role === 'MEMBER' && (
```

---

## P2 — Ranking → RoundResult

**Lý do:** Interface `Ranking` trong FE không khớp với bảng `RoundResult` trong DB.

### `mockData.ts`
```
interface Ranking      → interface RoundResult
ranking_id             → result_id
position               → rank_position
is_advanced            → advanced
rankings: Ranking[]    → rankings: RoundResult[]  (export name giữ nguyên)
```

### `CoordScoringPage.tsx`
```
import judgeAssignments → import userEventRoles  (judgeAssignments đã xoá ở P0)
import Ranking          → import RoundResult
useState<Ranking[]>     → useState<RoundResult[]>
judgeAssignments.filter(j => j.round_id === ...)
  → userEventRoles.filter(r => r.role_name === 'JUDGE' && r.round_id === ...)
{ ...r, is_advanced: r.position <= n }
  → { ...r, advanced: r.rank_position <= n }
CSV header "position,...,is_advanced" → "rank_position,...,advanced"
r.status === 'UPCOMING' → 'PENDING'   ← bỏ sót từ P0, fix luôn
key={r.ranking_id}, #{r.position}, r.is_advanced → result_id, rank_position, advanced
```

### `LeaderboardPage.tsx`
```
a.position - b.position       → a.rank_position - b.rank_position
team?.status === 'ELIMINATED' → 'DISQUALIFIED'   ← bỏ sót từ P0, fix luôn
r.is_advanced                 → r.advanced
key={r.ranking_id}            → key={r.result_id}
#{r.position}                 → #{r.rank_position}
status === 'Eliminated'       → 'Disqualified'
```

### `ParticipantDashboard` / `TeamLeaderDashboard` / `TeamMemberDashboard`
```
round1Rank.position → round1Rank.rank_position  (1 dòng mỗi file)
```

---

## Bước 1 — Thêm bảng thiếu vào DB Schema

### `back-end/database scripts/seal_hackathon.sql`

**Thêm bảng `AccountApproval`:**
```sql
CREATE TABLE AccountApproval (
  approval_id  INT          NOT NULL AUTO_INCREMENT,
  user_id      INT          NOT NULL,
  reviewed_by  INT,
  status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING, APPROVED, REJECTED
  note         TEXT,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_at  DATETIME,
  PRIMARY KEY (approval_id),
  FK: user_id → User, reviewed_by → User
)
```

**Thêm bảng `TeamInvite`:**
```sql
CREATE TABLE TeamInvite (
  invite_id        INT          NOT NULL AUTO_INCREMENT,
  team_id          INT          NOT NULL,
  invited_user_id  INT          NOT NULL,
  invited_by       INT          NOT NULL,
  message          TEXT,
  status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING, ACCEPTED, DECLINED
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  responded_at     DATETIME,
  UNIQUE (team_id, invited_user_id),
  FK: team_id → Team, invited_user_id → User, invited_by → User
)
```

**Bonus fix:** Xoá duplicate `scored_at` trong bảng `Score` (syntax error có sẵn trong file gốc, line 248).

### `back-end/database scripts/seal_seed.sql`

**Section 17 — AccountApproval:** 14 records (13 `APPROVED` cho các sinh viên đã đăng ký, 1 `PENDING` cho external student chưa được duyệt)

**Section 18 — TeamInvite:** 2 records demo (1 `PENDING`, 1 `ACCEPTED`)

---

## Kết quả kiểm tra

```
npx tsc --noEmit --ignoreDeprecations 6.0
→ 0 errors
```

---

## Tổng quan thay đổi

| Phase | Mô tả | Files thay đổi |
|-------|-------|----------------|
| P0 | User model + enum sync | `mockData.ts`, `AuthProvider.tsx` |
| P1 | Field renames toàn bộ interfaces | 23 component files |
| P2 | `Ranking` → `RoundResult` | `mockData.ts` + 5 files |
| Bước 1 | Thêm `AccountApproval` + `TeamInvite` vào DB | `seal_hackathon.sql`, `seal_seed.sql` |
