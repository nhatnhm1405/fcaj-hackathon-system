# SEAL — Software Engineering Hackathon Management System
## Traceability Matrix: Lecturer Requirements → Use Cases

**Project code:** SU26SWP04
**Purpose:** Demonstrate that every requirement bullet from the lecturer's specification is covered by at least one Use Case, and conversely that every Use Case traces back to an explicit requirement (or is justified as a supporting/optional capability).

**Legend:**
- **🔬 RBL** = supports the research on inter-rater reliability (research-based learning bonus scope)
- **⭐ Optional** = recommended but not explicitly mandated by the spec
- **REQ-XX** = identifier assigned to a requirement bullet from the spec for cross-reference

---

## Part A — Forward traceability (Requirement → UC)

### A.1 Event & round management / Quản lý sự kiện & vòng thi

| Req ID | Requirement (from spec) | Covered by UC | Notes |
|---|---|---|---|
| REQ-01 | Create and manage hackathon events (Spring/Summer/Fall) | UC02, UC03, UC16 | Event lifecycle: create → configure → close/archive |
| REQ-02 | Configure multiple rounds per event (e.g., Preliminary, Final) | **UC04b** | New UC added explicitly for round structure |
| REQ-03 | Per-round: submission deadline, judge assignment, scoring criteria | UC04b + UC08 + UC05 | UC04b sets per-round config; UC08 assigns judges per round; UC05 sets per-event criteria with optional per-round override |
| REQ-04 | Promotion rule: Top-N teams per Track advance to next round | **UC04c**, UC12b | UC04c defines the rule; UC12b applies it |

### A.2 Criteria management / Quản lý tiêu chí chấm điểm

| Req ID | Requirement | Covered by UC | Notes |
|---|---|---|---|
| REQ-05 | Maintain default criteria template (reused across events) | UC40 | Admin owns the template |
| REQ-06 | Each event inherits template; may add/remove/adjust criteria & weights | UC05 | Event-level override capability |

### A.3 Track management / Quản lý hạng mục

| Req ID | Requirement | Covered by UC | Notes |
|---|---|---|---|
| REQ-07 | Create Tracks (competition categories) within each event | UC04 | Track ≠ Round (separate concepts) |
| REQ-08 | Assign Mentor to a Track; one faculty may mentor Track A and judge Track B in same event | UC09 + UC08 | Dual-role rule explicitly noted in UC09 |

### A.4 Team management / Quản lý đội thi

| Req ID | Requirement | Covered by UC | Notes |
|---|---|---|---|
| REQ-09 | Form team of 3–5 members | UC27 | Size constraint enforced |
| REQ-10 | Team composition: all-FPT, mixed FPT+external, or all-external | UC27 + UC26 | Member classification at UC26 enables composition tracking |
| REQ-11 | Register team to a specific Track | UC30 | One Track per team per event |

### A.5 Registration & authentication / Đăng ký & xác thực

| Req ID | Requirement | Covered by UC | Notes |
|---|---|---|---|
| REQ-12 | Email/Password authentication with JWT | UC26 | Auth mechanism explicit |
| REQ-13 | Self-classification: FPT student (FPT ID) or external (student ID + university) | UC26 | Captured at registration |
| REQ-14 | All accounts require Coordinator approval before competing | UC06 | Approval gate before UC30/UC31 |
| REQ-15 | Guest Judge accounts: temporary, scoped to assigned rounds only | UC07 + UC08 | UC07 creates the account; UC08 binds it to specific rounds |

### A.6 Submission / Nộp bài

| Req ID | Requirement | Covered by UC | Notes |
|---|---|---|---|
| REQ-16 | Team submits per round via URLs (repo, demo, slide/report) | UC31 | URL-based, not file upload |
| REQ-17 | Allow updates before deadline | UC32 | Versioning of submission |
| REQ-18 | ⭐ Auto-fetch repo metadata via GitHub/GitLab API (optional) | **UC31b** | Marked optional in spec |

### A.7 Evaluation / Đánh giá

| Req ID | Requirement | Covered by UC | Notes |
|---|---|---|---|
| REQ-19 | Judges score by per-event criteria | UC17 → UC18 | View → score workflow |
| REQ-20 | 🔬 Each judge's per-criterion score recorded separately (not aggregated) | **UC18** | Critical for RQ1, RQ2, RQ3 |
| REQ-21 | Coordinator assigns Internal & Guest Judges to rounds as needed | UC08 | Both judge types supported |

### A.8 Scoring, ranking & disqualification / Chấm điểm, xếp hạng & loại

| Req ID | Requirement | Covered by UC | Notes |
|---|---|---|---|
| REQ-22 | Auto-rank teams per round, per Track, overall event | UC12 | System-computed ranking |
| REQ-23 | Compute promotion eligibility for next round | UC12b | Applies REQ-04 rule |
| REQ-24 | Disqualify violating team/submission; cancel results, record reason | UC11 | Includes audit log write |
| REQ-25 | Audit log for all scoring and disqualification actions | UC15 | UC18 and UC11 both `«include»` UC15 |

### A.9 Research data collection (RBL) / Thu thập dữ liệu nghiên cứu

| Req ID | Requirement | Covered by UC | Notes |
|---|---|---|---|
| REQ-26 | 🔬 Record per-judge per-criterion scores for each submission (no aggregation) | UC18 | Same as REQ-20 — research depends on it |
| REQ-27 | 🔬 Calibration round: judges score sample submissions; display score distribution | UC20 | Supports RQ1 baseline |
| REQ-28 | 🔬 Export anonymized scoring dataset (CSV) for IRR analysis | **UC14b** | Separate from general report export |
| REQ-29 | 🔬 Dashboard: inter-judge variance per criterion | UC21 | Visualizes data for RQ2 |

### A.10 Prizes & notifications / Giải thưởng & thông báo

| Req ID | Requirement | Covered by UC | Notes |
|---|---|---|---|
| REQ-30 | Award prizes based on ranking | UC13b | `«extend»` of UC13 |
| REQ-31 | Notify & publish results to all participants | UC13 + UC34 | UC13 `«include»` UC34 |
| REQ-32 | Export ranking & score reports as CSV/Excel | UC14 | Distinct from UC14b (research export) |

### A.11 Problems explicitly called out in spec / Vấn đề hệ thống cần giải quyết

| Spec problem | Solved by UC | How |
|---|---|---|
| Manual registration → delays & data errors | UC26 + UC06 + UC30 | Digital registration with approval workflow |
| Excel-based judging per judge → manual aggregation, errors | UC17 + UC18 + UC12 | Centralized scoring with automatic ranking |
| Limited communication between organizers/mentors/teams | UC34 + UC22–25 + UC10 | In-app notifications + mentor scope views + Coordinator dashboard |
| No audit log for scoring decisions | UC15 (incl. by UC18, UC11) | All scoring & DQ actions logged |

---

## Part B — Reverse traceability (UC → Requirement)

Every UC should have a "home" — either a direct spec requirement or a justified support role.

| UC ID | Use Case | Traces to | Justification if no direct req |
|---|---|---|---|
| UC02 | Create hackathon event | REQ-01 | — |
| UC03 | Configure event settings | REQ-01 | — |
| UC04 | Create & manage Tracks | REQ-07 | — |
| UC04b | Configure rounds within event | REQ-02, REQ-03 | — |
| UC04c | Define promotion rule | REQ-04 | — |
| UC05 | Set/adjust event criteria & weights | REQ-06 | — |
| UC06 | Approve participant accounts | REQ-14 | — |
| UC07 | Create temporary Guest Judge account | REQ-15 | — |
| UC08 | Assign judges to rounds | REQ-03, REQ-21 | — |
| UC09 | Assign Mentor to Track | REQ-08 | — |
| UC10 | Monitor event progress | Problem: limited communication | Coordinator's operational dashboard |
| UC11 | Disqualify violating team | REQ-24 | — |
| UC12 | Compute & finalize ranking | REQ-22 | — |
| UC12b | Compute promotion | REQ-04, REQ-23 | — |
| UC13 | Publish & notify results | REQ-31 | — |
| UC13b | Award prizes | REQ-30 | — |
| UC14 | Export ranking/score reports (CSV/Excel) | REQ-32 | — |
| UC14b | 🔬 Export anonymized scoring dataset (CSV) | REQ-28 | — |
| UC15 | View audit log | REQ-25 | — |
| UC16 | Close & archive event | REQ-01 | Event lifecycle closure |
| UC17 | View assigned submissions | REQ-19 | — |
| UC18 | 🔬 Score per criterion (separate storage) | REQ-20, REQ-26 | — |
| UC19 | Review & revise own scores | REQ-19 | Quality-of-life within scoring window |
| UC20 | 🔬 Calibration round | REQ-27 | — |
| UC21 | 🔬 Inter-rater variance dashboard | REQ-29 | — |
| UC22 | Mentor: view assigned Tracks | REQ-08 (mentor scope) | — |
| UC23 | Mentor: view teams in scope | REQ-08 (mentor scope) | — |
| UC24 | Mentor: view submissions of mentored teams | Problem: limited communication | Mentor support function |
| UC25 | Mentor: view results in scope | Problem: limited communication | Mentor support function |
| UC26 | Register account (JWT, self-classify) | REQ-12, REQ-13 | — |
| UC27 | Create team (3–5 members) | REQ-09, REQ-10 | — |
| UC28 | Invite members & manage roster | REQ-09 | Operational support for team formation |
| UC29 | Edit team profile | Problem: manual data errors | Self-service correction |
| UC30 | Register team for Track | REQ-11 | — |
| UC31 | Submit project (URLs) | REQ-16 | — |
| UC31b | ⭐ Auto-fetch repo metadata | REQ-18 | Optional per spec |
| UC32 | Update submission before deadline | REQ-17 | — |
| UC33 | View results | REQ-31 (recipient side) | — |
| UC34 | Receive notifications | REQ-31 | — |
| UC35 | Join team via invitation | REQ-09 | Support for UC27/UC28 |
| UC36 | View team information | REQ-09 | Team Member visibility |
| UC37 | Manage user accounts | REQ-14 | Admin function backing UC06 |
| UC38 | Manage roles & permissions | REQ-15 | Admin function backing UC07/UC08 |
| UC39 | View system statistics | Problem: lack of transparency | Operational health metric |
| UC40 | Manage default criteria template | REQ-05 | — |
| UC41 | ⭐ Public event landing page | — | Optional; aligned with platform norms (Devpost/HackerEarth) |
| UC45 | ⭐ Public leaderboard | — | Optional; engagement feature |
| UC47 | ⭐ Auto-validate submission eligibility | — | Optional; reduces manual error (supports problem statement) |

---

## Part C — Research traceability (Research Question → UC)

The spec includes a research component on inter-rater reliability. Each Sub-RQ must be answerable from data captured by the system.

| Research Question | Required data | UCs that capture it |
|---|---|---|
| **RQ1.** Overall IRR (ICC, Krippendorff's α) of SEAL scoring | Per-judge per-criterion scores for every (submission × judge) pair | UC18 (capture) → UC14b (export) → UC21 (visualize) |
| **RQ2.** Which criteria show highest/lowest agreement (technical vs subjective) | Same as RQ1, segmented by criterion type | UC18 (per-criterion storage) → UC14b → UC21 (per-criterion variance view) |
| **RQ3.** Does judge type (Internal vs Guest) affect consistency? | Per-judge scores tagged with judge type | UC18 + UC07 (Guest tagging) + UC06/UC37 (Internal tagging) → UC14b |
| **Calibration baseline** for the study | Sample-submission scores from each judge before live judging | UC20 → exported via UC14b |

---

## Part D — Coverage check

| Category | Total requirements | Total UCs traced | Gap |
|---|---|---|---|
| Event & round management | 4 (REQ-01 to REQ-04) | UC02, UC03, UC04b, UC04c, UC16 | None |
| Criteria management | 2 (REQ-05, REQ-06) | UC40, UC05 | None |
| Track management | 2 (REQ-07, REQ-08) | UC04, UC09 | None |
| Team management | 3 (REQ-09 to REQ-11) | UC27, UC30 | None |
| Registration & auth | 4 (REQ-12 to REQ-15) | UC26, UC06, UC07 | None |
| Submission | 3 (REQ-16 to REQ-18) | UC31, UC32, UC31b | None (REQ-18 optional) |
| Evaluation | 3 (REQ-19 to REQ-21) | UC17, UC18, UC08 | None |
| Scoring/ranking/DQ | 4 (REQ-22 to REQ-25) | UC12, UC12b, UC11, UC15 | None |
| RBL research | 4 (REQ-26 to REQ-29) | UC18, UC20, UC14b, UC21 | None |
| Prizes & notifications | 3 (REQ-30 to REQ-32) | UC13b, UC13, UC34, UC14 | None |
| **Total** | **32** | **All covered** | **0** |

✅ 100% requirement coverage. No requirement is orphaned; no critical UC is without a requirement anchor.

---

## Appendix — Notation reminders

- `«include»` — base UC always invokes the included UC (e.g., UC18 always writes UC15)
- `«extend»` — extending UC conditionally adds behavior to the base UC (e.g., UC13b only runs when there are winners)
- 🔬 UCs are critical to the research component; losing any of them invalidates Sub-RQ data
- ⭐ UCs are optional/recommended; can be descoped if timeline is tight, without breaking the core operational system
