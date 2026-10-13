USE seal_hackathon;

-- =====================================================
-- SEAL Hackathon Management System — CONSOLIDATED SEED DATA
-- Run this AFTER seal_schema.sql (schema).
--
-- BUSINESS RULE — ONE EVENT PER SEASON:
--   Each (season, year) has exactly ONE event, and events never overlap in
--   time. A year therefore holds at most 3 events: Spring, Summer, Fall.
--   There is a single host location (no per-campus duplicates).
--   Enforced by the service layer on event create/update:
--     • reject if another event already exists for the same (season, year);
--     • reject if [start_date, end_date] overlaps an existing event.
--   (Kept as an application rule, not a DB CHECK, because overlap checks
--    span rows and are awkward to express as a column constraint in MySQL.)
--
--   Seed events (one per season, non-overlapping):
--     event 1: SEAL Spring 2026  → COMPLETED    (Feb–Apr)
--     event 2: SEAL Summer 2026  → IN_PROGRESS  (Jun–Aug, Preliminary ACTIVE)
--     event 3: SEAL Fall 2026    → DRAFT        (Oct–Dec)
--
-- CHANGELOG vs previous seed:
--   • Removed the duplicate Summer event (old "FUHN Summer") to satisfy the
--     one-event-per-season rule; renumbered events to 1/2/3.
--   • Dropped per-campus naming (FUHCM / FUHN) — names are now just
--     "SEAL <Season> 2026".
--   • Synced to the new schema:
--       - SYSTEM_ADMIN role added (role_id 1; COORDINATOR=2, MENTOR=3, JUDGE=4).
--       - Seeded a SYSTEM_ADMIN account (user_id 1).
--       - HackathonEvent.created_by removed.
--       - Round.is_calibration removed.
--       - UserEventRole.assigned_by / assigned_at removed.
--       - JudgeAssignment.judge_type / assigned_by / assigned_at removed;
--         judge_type now lives on User.judge_type.
--       - MentorAssignment.assigned_by / assigned_at removed.
--       - RoundResult.advanced removed (derived from rank vs top_n_advance).
--       - Notification.related_event_id removed.
--
-- ALL seed users share the same test password: Test@1234 (BCrypt cost 10).
-- =====================================================


-- =====================================================
-- 1. ROLE
-- Schema (seal_schema.sql) already inserts the 4 roles. INSERT IGNORE keeps
-- this seed runnable on its own without duplicating them.
-- role_id: 1=SYSTEM_ADMIN, 2=EVENT_COORDINATOR, 3=MENTOR, 4=JUDGE
-- =====================================================
INSERT IGNORE INTO Role (role_id, role_name, description) VALUES
  (1, 'SYSTEM_ADMIN',      'Platform operator — accounts, role grants, templates, system logs'),
  (2, 'EVENT_COORDINATOR', 'SE Dept / PDP staff — runs a hackathon event'),
  (3, 'MENTOR',            'Faculty mentor assigned to a track'),
  (4, 'JUDGE',             'Judge who scores submissions — INTERNAL or GUEST');


-- =====================================================
-- 2. USER  (user_id 1-43)
-- user_type : 'FPT_STUDENT' | 'EXTERNAL_STUDENT' | 'STAFF'
-- judge_type: 'INTERNAL' | 'GUEST' | NULL   (only set for users who judge)
-- =====================================================
INSERT INTO `User` (user_id, email, password_hash, full_name, user_type, judge_type, student_id, university, is_approved, is_active) VALUES
  -- user_id 1: STAFF — System Administrator (platform owner; not tied to any event)
  (1, 'admin@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'SEAL System Admin', 'STAFF', NULL, NULL, NULL, TRUE, TRUE),

  -- user_id 2: STAFF — Event Coordinator
  (2, 'coordinator@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Nguyen Van Coordinator', 'STAFF', NULL, NULL, NULL, TRUE, TRUE),

  -- user_id 3: STAFF — Internal mentor / judge (Thay An)
  (3, 'mentor.an@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Tran Van An', 'STAFF', 'INTERNAL', NULL, NULL, TRUE, TRUE),

  -- user_id 4: STAFF — Internal judge (Thay Binh)
  (4, 'judge.binh@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Le Van Binh', 'STAFF', 'INTERNAL', NULL, NULL, TRUE, TRUE),

  -- user_id 5: STAFF — Guest judge (pre-approved)
  (5, 'guest.judge@gmail.com',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Pham Thi Guest', 'STAFF', 'GUEST', NULL, NULL, TRUE, TRUE),

  -- user_id 6: FPT_STUDENT — Team leader (multi-event)
  (6, 'leader1@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Hoang Van Leader', 'FPT_STUDENT', NULL, 'SE211234', NULL, TRUE, TRUE),

  -- user_id 7: FPT_STUDENT — Team member
  (7, 'member1@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Nguyen Thi Lan', 'FPT_STUDENT', NULL, 'HE195678', NULL, TRUE, TRUE),

  -- user_id 8: FPT_STUDENT — Team member
  (8, 'member2@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Tran Van Duc', 'FPT_STUDENT', NULL, 'DE209012', NULL, TRUE, TRUE),

  -- user_id 9: EXTERNAL_STUDENT — Team leader from HCMUT (multi-event)
  (9, 'leader2@hcmut.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Do Van Long', 'EXTERNAL_STUDENT', NULL, 'BK2100123', 'HCMUT', TRUE, TRUE),

  -- user_id 10: EXTERNAL_STUDENT — Team member from HCMUT
  (10, 'member3@hcmut.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Vo Thi Hoa', 'EXTERNAL_STUDENT', NULL, 'BK2100456', 'HCMUT', TRUE, TRUE),

  -- user_id 11: EXTERNAL_STUDENT — Not yet approved (tests pending-approval flow)
  (11, 'member4@hcmut.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Ly Van Minh', 'EXTERNAL_STUDENT', NULL, 'BK2100789', 'HCMUT', FALSE, TRUE),

  -- user_id 12: FPT_STUDENT — Team leader (multi-event)
  (12, 'student.ce@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Pham Thi Bich', 'FPT_STUDENT', NULL, 'CE213456', NULL, TRUE, TRUE),

  -- user_id 13: FPT_STUDENT — Team member
  (13, 'student.qe@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Nguyen Van Khoa', 'FPT_STUDENT', NULL, 'QE207890', NULL, TRUE, TRUE),

  -- ── Event 1 (Spring 2026 - COMPLETED) participants ──
  -- user_id 14: FPT_STUDENT member
  (14, 'leader3@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Bui Thi Thu', 'FPT_STUDENT', NULL, 'SE211001', NULL, TRUE, TRUE),

  -- user_id 15: FPT_STUDENT member
  (15, 'member5@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Ngo Van Hieu', 'FPT_STUDENT', NULL, 'HE201002', NULL, TRUE, TRUE),

  -- user_id 16: FPT_STUDENT leader
  (16, 'leader4@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Tran Thi Mai', 'FPT_STUDENT', NULL, 'DE211003', NULL, TRUE, FALSE),

  -- user_id 17: EXTERNAL_STUDENT member (UIT)
  (17, 'member6@uit.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Dang Van Tuan', 'EXTERNAL_STUDENT', NULL, 'UIT2100301', 'UIT', TRUE, FALSE),

  -- user_id 18: FPT_STUDENT leader (top winner event 1)
  (18, 'leader5@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Le Van Nam', 'FPT_STUDENT', NULL, 'CE211004', NULL, TRUE, TRUE),

  -- user_id 19: FPT_STUDENT member
  (19, 'member7@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Hoang Thi Ly', 'FPT_STUDENT', NULL, 'SE201005', NULL, TRUE, TRUE),

  -- user_id 20: STAFF — Internal judge for event 1 (Co Cam)
  (20, 'judge.cam@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Nguyen Thi Cam', 'STAFF', 'INTERNAL', NULL, NULL, TRUE, TRUE),

  -- ── Event 2 (Summer 2026 - IN_PROGRESS) participants ──
  -- user_id 21: FPT_STUDENT leader
  (21, 'leader6@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Pham Van Dat', 'FPT_STUDENT', NULL, 'SE211006', NULL, TRUE, TRUE),

  -- user_id 22: FPT_STUDENT member
  (22, 'member8@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Vo Thi Lan', 'FPT_STUDENT', NULL, 'HE211007', NULL, TRUE, TRUE),

  -- user_id 23: FPT_STUDENT member
  (23, 'member9@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Nguyen Van Phuc', 'FPT_STUDENT', NULL, 'AI211008', NULL, TRUE, TRUE),

  -- user_id 24: EXTERNAL_STUDENT leader (not approved — tests pending flow)
  (24, 'leader7@hust.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Dinh Van Khang', 'EXTERNAL_STUDENT', NULL, 'HUST2100901', 'HUST', FALSE, TRUE),

  -- user_id 25: FPT_STUDENT leader
  (25, 'leader8@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Tran Van Quan', 'FPT_STUDENT', NULL, 'SE211010', NULL, TRUE, TRUE),

  -- user_id 26: FPT_STUDENT member
  (26, 'member10@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Nguyen Thi Bich', 'FPT_STUDENT', NULL, 'HE211011', NULL, TRUE, TRUE),

  -- user_id 27: STAFF — Mentor for event 2
  (27, 'mentor.hung@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Nguyen Minh Hung', 'STAFF', NULL, NULL, NULL, TRUE, TRUE),

  -- ── Event 2 extra teams (14-17) participants ──
  -- Team Aurora (team 14)
  -- user_id 28: FPT_STUDENT leader
  (28, 'leader9@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Tran Van Son', 'FPT_STUDENT', NULL, 'SE211020', NULL, TRUE, TRUE),

  -- user_id 29: FPT_STUDENT member
  (29, 'member11@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Nguyen Thi Hang', 'FPT_STUDENT', NULL, 'HE211021', NULL, TRUE, TRUE),

  -- user_id 30: EXTERNAL_STUDENT member (HCMUT)
  (30, 'member12@hcmut.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Le Van Khoa', 'EXTERNAL_STUDENT', NULL, 'BK2100222', 'HCMUT', TRUE, TRUE),

  -- user_id 31: FPT_STUDENT member
  (31, 'member13@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Pham Thi Yen', 'FPT_STUDENT', NULL, 'DE211022', NULL, TRUE, TRUE),

  -- user_id 32: EXTERNAL_STUDENT member (UIT)
  (32, 'member14@uit.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Vo Van Tai', 'EXTERNAL_STUDENT', NULL, 'UIT2100302', 'UIT', TRUE, TRUE),

  -- Team Specter (team 15)
  -- user_id 33: FPT_STUDENT leader
  (33, 'leader10@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Hoang Van Tan', 'FPT_STUDENT', NULL, 'SE211023', NULL, TRUE, TRUE),

  -- user_id 34: FPT_STUDENT member
  (34, 'member15@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Do Thi Mai', 'FPT_STUDENT', NULL, 'HE211024', NULL, TRUE, TRUE),

  -- user_id 35: EXTERNAL_STUDENT member (HCMUT)
  (35, 'member16@hcmut.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Ngo Van Hai', 'EXTERNAL_STUDENT', NULL, 'BK2100223', 'HCMUT', TRUE, TRUE),

  -- user_id 36: FPT_STUDENT member
  (36, 'member17@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Bui Van Long', 'FPT_STUDENT', NULL, 'CE211025', NULL, TRUE, TRUE),

  -- Team Quantum (team 16)
  -- user_id 37: EXTERNAL_STUDENT leader (HCMUT)
  (37, 'leader11@hcmut.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Tran Van Phu', 'EXTERNAL_STUDENT', NULL, 'BK2100224', 'HCMUT', TRUE, TRUE),

  -- user_id 38: EXTERNAL_STUDENT member (HCMUT)
  (38, 'member18@hcmut.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Le Thi Thao', 'EXTERNAL_STUDENT', NULL, 'BK2100225', 'HCMUT', TRUE, TRUE),

  -- user_id 39: FPT_STUDENT member
  (39, 'member19@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Nguyen Van Vu', 'FPT_STUDENT', NULL, 'SE211026', NULL, TRUE, TRUE),

  -- Team Zephyr (team 17)
  -- user_id 40: FPT_STUDENT leader
  (40, 'leader12@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Dang Van Hieu', 'FPT_STUDENT', NULL, 'SE211027', NULL, TRUE, TRUE),

  -- user_id 41: FPT_STUDENT member
  (41, 'member20@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Pham Thi Linh', 'FPT_STUDENT', NULL, 'HE211028', NULL, TRUE, TRUE),

  -- user_id 42: EXTERNAL_STUDENT member (UIT)
  (42, 'member21@uit.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Vo Thi Ngoc', 'EXTERNAL_STUDENT', NULL, 'UIT2100303', 'UIT', TRUE, TRUE),

  -- user_id 43: FPT_STUDENT member
  (43, 'member22@fpt.edu.vn',
   '$2a$10$0ck6TfX5CqlTb8NFxY63guW52UpO3kFCbJYtKOA5a6/iqPzuB/Veq',
   'Nguyen Van Dung', 'FPT_STUDENT', NULL, 'CE211029', NULL, TRUE, TRUE);


-- =====================================================
-- 3. HACKATHON EVENT  (event_id 1-3) — one per season, non-overlapping
-- =====================================================
INSERT INTO HackathonEvent
  (event_id, name, season, year, description,
   registration_start, registration_end,
   start_date, end_date, status)
VALUES
  -- ── event 1 ── Spring — COMPLETED (15 Jan – 15 Apr 2026)
  (1, 'SEAL Spring 2026', 'SPRING', 2026,
   'Su kien SEAL Spring 2026 — da ket thuc. Mo cho sinh vien FPT va cac truong doi tac.',
   '2026-01-15 00:00:00', '2026-02-28 23:59:59',
   '2026-04-14 08:00:00', '2026-04-15 23:59:59',
   'COMPLETED'),

  -- ── event 2 ── Summer — OPEN for registration (15 May – 15 Aug 2026)
  --     Registration window covers "now" so teams can register during testing.
  (2, 'SEAL Summer 2026', 'SUMMER', 2026,
   'Su kien SEAL Summer 2026 — dang mo dang ky. '
   'Chu de: "Build for Tomorrow" — khuyen khich giai phap AI va ben vung.',
   '2026-05-15 00:00:00', '2026-06-30 23:59:59',
   '2026-08-14 08:00:00', '2026-08-15 23:59:59',
   'OPEN'),

  -- ── event 3 ── Fall — DRAFT (15 Sep – 15 Dec 2026)
  (3, 'SEAL Fall 2026', 'FALL', 2026,
   'Su kien SEAL Fall 2026 — dang trong giai doan len ke hoach. '
   'Chu de va the le se duoc cong bo sau.',
   '2026-09-15 00:00:00', '2026-10-31 23:59:59',
   '2026-12-14 08:00:00', '2026-12-15 23:59:59',
   'DRAFT');


-- =====================================================
-- 4. TRACK  (track_id 1-7)   event 3 (DRAFT) has no tracks yet.
-- =====================================================
-- Event 1 — Spring (track 1-3)
INSERT INTO Track (event_id, name, description) VALUES
  (1, 'Web Application', 'Ung dung web full-stack'),               -- track 1
  (1, 'Mobile App',      'Ung dung di dong iOS/Android'),          -- track 2
  (1, 'AI Solution',     'San pham ung dung AI/ML');               -- track 3

-- Event 2 — Summer (track 4-7)
INSERT INTO Track (event_id, name, description) VALUES
  (2, 'Web Application', 'Ung dung web full-stack'),               -- track 4
  (2, 'AI Solution',     'San pham ung dung AI/ML'),               -- track 5
  (2, 'Social Impact',   'Giai phap cong nghe cho van de xa hoi'), -- track 6
  (2, 'Green Tech',      'Giai phap cong nghe xanh, ben vung');    -- track 7


-- =====================================================
-- 5. ROUND  (round_id 1-6)
-- =====================================================
-- Event 1 — Spring: COMPLETED → all 3 rounds FINALIZED (round 1-3)
INSERT INTO Round
  (event_id, name, order_number, start_time, end_time, submission_deadline,
   top_n_advance, is_final, status)
VALUES
  (1, 'Preliminary', 1,
   '2026-02-01 08:00:00', '2026-02-28 23:59:59', '2026-02-28 23:59:59',
   2, FALSE, 'FINALIZED'),   -- Top 2 PER TRACK advance (Web: Phoenix,Dragon; Mobile: Tiger; AI: Eagle)
  (1, 'Semi-final', 2,
   '2026-03-10 08:00:00', '2026-03-31 23:59:59', '2026-03-31 23:59:59',
   1, FALSE, 'FINALIZED'),   -- Top 1 PER TRACK advance (Phoenix, Tiger, Eagle -> Final)
  (1, 'Final', 3,
   '2026-04-10 08:00:00', '2026-04-30 23:59:59', '2026-04-30 23:59:59',
   NULL, TRUE, 'FINALIZED');

-- Event 2 — Summer: IN_PROGRESS → Preliminary ACTIVE, rest PENDING (round 4-6)
INSERT INTO Round
  (event_id, name, order_number, start_time, end_time, submission_deadline,
   top_n_advance, is_final, status)
VALUES
  (2, 'Preliminary', 1,
   '2026-06-01 08:00:00', '2026-06-30 23:59:59', '2026-06-30 23:59:59',
   5, FALSE, 'ACTIVE'),
  (2, 'Semi-final', 2,
   '2026-07-10 08:00:00', '2026-07-31 23:59:59', '2026-07-31 23:59:59',
   2, FALSE, 'PENDING'),
  (2, 'Final', 3,
   '2026-08-10 08:00:00', '2026-08-31 23:59:59', '2026-08-31 23:59:59',
   NULL, TRUE, 'PENDING');


-- =====================================================
-- 6. USER EVENT ROLE  (who is ALLOWED to be what role, per event)
-- role_id: 1=SYSTEM_ADMIN, 2=EVENT_COORDINATOR, 3=MENTOR, 4=JUDGE
-- event_id = NULL for system-wide roles (SYSTEM_ADMIN).
-- One user may hold several roles in one event (e.g. user 3 = MENTOR + JUDGE
-- in event 1) → one row per role.
-- =====================================================
INSERT INTO UserEventRole (user_id, role_id, event_id) VALUES
  -- ── System-wide ──
  (1, 1, NULL),    -- Admin: SYSTEM_ADMIN (no event)

  -- ── EVENT 1 (COMPLETED) ──
  (2,  2, 1),   -- Coordinator
  (3,  3, 1),   -- Thay An: MENTOR (Web App)
  (3,  4, 1),   -- Thay An: also JUDGE (AI Solution) — Mentor + Judge case
  (4,  4, 1),   -- Thay Binh: JUDGE
  (20, 4, 1),   -- Co Cam: JUDGE
  (5,  4, 1),   -- Guest judge: JUDGE

  -- ── EVENT 2 (OPEN — registration) ──
  (2,  2, 2),   -- Coordinator
  (27, 3, 2),   -- Thay Hung: MENTOR
  (3,  3, 2),   -- Thay An: MENTOR

  -- ── EVENT 3 (DRAFT) ──
  (2,  2, 3);   -- Coordinator


-- =====================================================
-- 6b. JUDGE ASSIGNMENT  (which round/track a judge may score)
-- Event 1 tracks: 1=Web, 2=Mobile, 3=AI | rounds: 1=Prelim, 2=Semi, 3=Final
-- Final (round 3) is is_final=TRUE, but this dataset scored it per-track
-- (one champion per track), so track_id is kept to match the seeded Scores.
-- Who/when assigned is captured in AuditLog (ASSIGN_JUDGE), not here.
-- =====================================================
INSERT INTO JudgeAssignment (judge_user_id, round_id, track_id) VALUES
  -- Thay Binh (4): Web App (track 1) — all 3 rounds
  (4,  1, 1),
  (4,  2, 1),
  (4,  3, 1),
  -- Co Cam (20): Mobile App (track 2) — all 3 rounds
  (20, 1, 2),
  (20, 2, 2),
  (20, 3, 2),
  -- Thay An (3): AI Solution (track 3) — Prelim + Semi + Final
  (3,  1, 3),
  (3,  2, 3),
  (3,  3, 3),
  -- Guest judge (5): AI Solution (track 3) — Prelim + Final
  (5,  1, 3),
  (5,  3, 3);


-- =====================================================
-- 6c. MENTOR ASSIGNMENT  (which track a mentor supports — whole event)
-- Event 1 tracks: 1=Web, 3=AI | Event 2: 4=Web, 5=AI
-- Who/when assigned is captured in AuditLog (ASSIGN_MENTOR).
-- =====================================================
INSERT INTO MentorAssignment (mentor_user_id, track_id) VALUES
  -- Event 1
  (3,  1),    -- Thay An: Web App
  -- Event 2
  (27, 4),    -- Thay Hung: Web App
  (3,  5);    -- Thay An: AI Solution


-- =====================================================
-- 7. TEAM  (team_id 1-17) + TEAM EVENT ENTRY (one entry per team here, since
-- no team re-enters a second season in this seed — Team is now a stable
-- identity, season facts (event/track/status) live on TeamEventEntry).
-- =====================================================
-- Event 1 — Spring (team 1-5). track 1=Web, 2=Mobile, 3=AI
INSERT INTO Team (name, description) VALUES
  ('Team Phoenix', 'Web app quan ly ky tuc xa thong minh'),  -- team 1
  ('Team Dragon',  'Nen tang hoc tap truc tuyen'),           -- team 2
  ('Team Tiger',   'App theo doi suc khoe sinh vien'),       -- team 3
  ('Team Eagle',   'AI cham diem bai tap tu dong'),          -- team 4
  ('Team Falcon',  'Web marketplace trao doi sach cu');      -- team 5

-- Event 2 — Summer (team 6-17): ALL register WITHOUT a track (track_id = NULL).
-- Track is assigned during SETUP: leaders self-select (SELF_SELECT mode) or the
-- coordinator draws (RANDOM mode) via POST /api/teams/event/2/draw-tracks.
-- Mix of APPROVED/PENDING so the approval step can be demoed — only APPROVED teams
-- count toward per-track slots and may pick / be drawn. (event tracks: 4-7)
INSERT INTO Team (name, description) VALUES
  ('Team Horizon',  'Web app ho tro tim viec lam'),           -- team 6
  ('Team Nexus',    'AI tom tat tai lieu hoc tap'),           -- team 7
  ('Team Verde',    'App ket noi tinh nguyen vien'),          -- team 8
  ('Team EcoSmart', 'IoT monitoring chat luong khong khi'),   -- team 9
  ('Team Pixel',    'Platform thiet ke portfolio sinh vien'), -- team 10
  ('Team Comet',    'Web app cong dong sinh vien'),           -- team 11
  ('Team Vortex',   'Nen tang AI ho tro hoc tap'),            -- team 12
  ('Team Lumen',    'Giai phap cong nghe xanh'),               -- team 13
  ('Team Aurora',   'Web app ho tro on tap thi cu'),          -- team 14
  ('Team Specter',  'AI phat hien dao van bai nop'),          -- team 15
  ('Team Quantum',  'Nen tang quan ly du an nhom sinh vien'), -- team 16
  ('Team Zephyr',   'Giai phap IoT tiet kiem nang luong');    -- team 17

INSERT INTO TeamEventEntry (team_id, event_id, track_id, status) VALUES
  (1,  1, 1,    'APPROVED'),
  (2,  1, 1,    'APPROVED'),
  (3,  1, 2,    'APPROVED'),
  (4,  1, 3,    'APPROVED'),
  (5,  1, 1,    'APPROVED'),
  (6,  2, NULL, 'APPROVED'),
  (7,  2, NULL, 'APPROVED'),
  (8,  2, NULL, 'APPROVED'),
  (9,  2, NULL, 'PENDING'),
  (10, 2, NULL, 'PENDING'),
  (11, 2, NULL, 'APPROVED'),
  (12, 2, NULL, 'APPROVED'),
  (13, 2, NULL, 'PENDING'),
  (14, 2, NULL, 'APPROVED'),
  (15, 2, NULL, 'APPROVED'),
  (16, 2, NULL, 'PENDING'),
  (17, 2, NULL, 'APPROVED');


-- =====================================================
-- 8. TEAM MEMBER   member_role: 'LEADER' | 'MEMBER'
-- =====================================================
-- Event 1 teams
INSERT INTO TeamMember (team_id, user_id, member_role) VALUES
  (1, 6,  'LEADER'), -- Hoang Van Leader
  (1, 14, 'MEMBER'), -- Bui Thi Thu
  (1, 15, 'MEMBER'), -- Ngo Van Hieu
  (2, 16, 'LEADER'), -- Tran Thi Mai
  (2, 17, 'MEMBER'), -- Dang Van Tuan
  (3, 12, 'LEADER'), -- Pham Thi Bich
  (3, 13, 'MEMBER'), -- Nguyen Van Khoa
  (4, 18, 'LEADER'), -- Le Van Nam
  (4, 19, 'MEMBER'), -- Hoang Thi Ly
  (5, 9,  'LEADER'), -- Do Van Long
  (5, 10, 'MEMBER'); -- Vo Thi Hoa

-- Event 2 teams
INSERT INTO TeamMember (team_id, user_id, member_role) VALUES
  (6,  21, 'LEADER'), -- Pham Van Dat
  (6,  22, 'MEMBER'), -- Vo Thi Lan
  (6,  23, 'MEMBER'), -- Nguyen Van Phuc
  (7,  6,  'LEADER'), -- Hoang Van Leader (multi-event)
  (7,  7,  'MEMBER'), -- Nguyen Thi Lan
  (8,  12, 'LEADER'), -- Pham Thi Bich (multi-event)
  (8,  13, 'MEMBER'), -- Nguyen Van Khoa
  (9,  24, 'LEADER'), -- Dinh Van Khang (not approved)
  (10, 18, 'LEADER'), -- Le Van Nam
  (10, 19, 'MEMBER'); -- Hoang Thi Ly

-- Event 2 teams 11-13 (NULL-track, for draw demo). Users free in event 2.
INSERT INTO TeamMember (team_id, user_id, member_role) VALUES
  (11, 8,  'LEADER'), -- Tran Van Duc
  (11, 14, 'MEMBER'), -- Bui Thi Thu
  (12, 9,  'LEADER'), -- Do Van Long
  (12, 15, 'MEMBER'), -- Ngo Van Hieu
  (13, 10, 'LEADER'); -- Vo Thi Hoa

-- Event 2 extra teams 14-17 (NULL-track). Brand-new users 28-43.
INSERT INTO TeamMember (team_id, user_id, member_role) VALUES
  -- Team Aurora (5)
  (14, 28, 'LEADER'), -- Tran Van Son
  (14, 29, 'MEMBER'), -- Nguyen Thi Hang
  (14, 30, 'MEMBER'), -- Le Van Khoa
  (14, 31, 'MEMBER'), -- Pham Thi Yen
  (14, 32, 'MEMBER'), -- Vo Van Tai
  -- Team Specter (4)
  (15, 33, 'LEADER'), -- Hoang Van Tan
  (15, 34, 'MEMBER'), -- Do Thi Mai
  (15, 35, 'MEMBER'), -- Ngo Van Hai
  (15, 36, 'MEMBER'), -- Bui Van Long
  -- Team Quantum (3)
  (16, 37, 'LEADER'), -- Tran Van Phu
  (16, 38, 'MEMBER'), -- Le Thi Thao
  (16, 39, 'MEMBER'), -- Nguyen Van Vu
  -- Team Zephyr (4)
  (17, 40, 'LEADER'), -- Dang Van Hieu
  (17, 41, 'MEMBER'), -- Pham Thi Linh
  (17, 42, 'MEMBER'), -- Vo Thi Ngoc
  (17, 43, 'MEMBER'); -- Nguyen Van Dung


-- =====================================================
-- 9. SCORING CRITERIA TEMPLATE  (template_id 1-2)
-- Reusable criteria sets a coordinator can apply to any round. Their items
-- live in section 10b (template-only ScoringCriteria rows).
-- =====================================================
INSERT INTO ScoringCriteriaTemplate (template_id, name, description, is_default) VALUES
  (1, 'Standard Hackathon Criteria', 'Default SEAL set: innovation, technical, UI/UX, presentation, completeness', TRUE),
  (2, 'Pitch & Demo Day',            'Lightweight set for pitch / demo-day rounds',                                FALSE);


-- =====================================================
-- 10. SCORING CRITERIA  (criteria_id 1-15) — event 1 (COMPLETED)
-- Round 1 Prelim: 1-5 | Round 2 Semi: 6-10 | Round 3 Final: 11-15
-- =====================================================
INSERT INTO ScoringCriteria (event_id, round_id, template_id, name, description, weight, max_score, order_number) VALUES
  -- Round 1 (Preliminary)
  (1, 1, 1, 'Innovation',   'Tinh sang tao va doc dao cua y tuong',   1.5, 10.0, 1),
  (1, 1, 1, 'Technical',    'Chat luong ky thuat va implementation',  2.0, 10.0, 2),
  (1, 1, 1, 'UI/UX',        'Giao dien va trai nghiem nguoi dung',    1.0, 10.0, 3),
  (1, 1, 1, 'Presentation', 'Ky nang thuyet trinh va demo',           1.0, 10.0, 4),
  (1, 1, 1, 'Completeness', 'Muc do hoan thien cua san pham',         1.5, 10.0, 5),
  -- Round 2 (Semi-final) — adds Market Viability
  (1, 2, 1, 'Innovation',   'Tinh sang tao va doc dao cua y tuong',   1.5, 10.0, 1),
  (1, 2, 1, 'Technical',    'Chat luong ky thuat va implementation',  2.0, 10.0, 2),
  (1, 2, 1, 'UI/UX',        'Giao dien va trai nghiem nguoi dung',    1.0, 10.0, 3),
  (1, 2, 1, 'Presentation', 'Ky nang thuyet trinh va demo',           1.5, 10.0, 4),
  (1, 2, 1, 'Market',       'Tiem nang thi truong va kha nang scale', 1.0, 10.0, 5),
  -- Round 3 (Final)
  (1, 3, 1, 'Innovation',   'Tinh sang tao va doc dao cua y tuong',   1.5, 10.0, 1),
  (1, 3, 1, 'Technical',    'Chat luong ky thuat va implementation',  2.0, 10.0, 2),
  (1, 3, 1, 'UI/UX',        'Giao dien va trai nghiem nguoi dung',    1.0, 10.0, 3),
  (1, 3, 1, 'Presentation', 'Ky nang thuyet trinh va demo',           1.5, 10.0, 4),
  (1, 3, 1, 'Market',       'Tiem nang thi truong va kha nang scale', 1.0, 10.0, 5);


-- =====================================================
-- 10b. SCORING CRITERIA TEMPLATE ITEMS  (template-only: event_id/round_id NULL)
-- The reusable items a coordinator applies to a round via "Apply template".
-- New criteria_ids continue after the 15 event-1 rows above.
-- =====================================================
INSERT INTO ScoringCriteria (event_id, round_id, template_id, name, description, weight, max_score, order_number) VALUES
  -- Template 1 — Standard Hackathon Criteria
  (NULL, NULL, 1, 'Innovation',   'Originality and creativity of the idea',  1.5, 10.0, 1),
  (NULL, NULL, 1, 'Technical',    'Engineering quality and implementation',  2.0, 10.0, 2),
  (NULL, NULL, 1, 'UI/UX',        'Interface design and user experience',    1.0, 10.0, 3),
  (NULL, NULL, 1, 'Presentation', 'Pitching and demo skills',                1.0, 10.0, 4),
  (NULL, NULL, 1, 'Completeness', 'How finished and usable the product is',  1.5, 10.0, 5),
  -- Template 2 — Pitch & Demo Day
  (NULL, NULL, 2, 'Problem & Solution', 'Clarity of the problem and the proposed solution', 1.5, 10.0, 1),
  (NULL, NULL, 2, 'Demo',               'Quality and impact of the live demo',              2.0, 10.0, 2),
  (NULL, NULL, 2, 'Market Potential',   'Market viability and ability to scale',            1.0, 10.0, 3),
  (NULL, NULL, 2, 'Teamwork',           'Team collaboration and role clarity',              1.0, 10.0, 4);


-- =====================================================
-- 11. SUBMISSION  (submission_id 1-12) — event 1
-- Prelim (r1): all 5 teams | Semi (r2): Phoenix,Dragon,Tiger,Eagle | Final (r3): Phoenix,Tiger,Eagle
--
-- Descriptions feed the AI Judge Assistant (it reads description + repo to give
-- advisory notes). Sub 1/2/4 point at REAL public GitHub repos so the
-- "Repository Analysis" block has live data in round 1 (Web + AI tracks):
--   • sub 1 (MATCH)    — spring-petclinic: description + repo agree.
--   • sub 2 (MISMATCH) — desc says Node.js, repo is Flask (Python) → AI flags it.
--   • sub 4 (BONUS)    — AutoGrade pitched as FastAPI → FastAPI template repo.
-- The other subs keep placeholder repos; their descriptions still drive AI text.
-- =====================================================
INSERT INTO Submission (team_id, round_id, repo_url, demo_url, slide_url, description, submitted_by, status) VALUES
  -- Preliminary (round 1)
  (1, 1, 'https://github.com/spring-projects/spring-petclinic', 'https://demo.teamphoenix.io', 'https://slides.com/phoenix',
   'PetClinic Manager là hệ thống web quản lý phòng khám thú y. Chức năng gồm quản lý hồ sơ chủ nuôi và thú cưng, đặt và theo dõi lịch hẹn khám, quản lý thông tin bác sĩ thú y theo chuyên khoa, và ghi nhận lịch sử khám chữa của từng thú cưng. Backend Spring Boot (Java) với cơ sở dữ liệu quan hệ, giao diện web theo mô hình MVC. Bản nộp vòng sơ khảo đã chạy được CRUD chủ nuôi/thú cưng và luồng đặt lịch khám; phần thống kê báo cáo còn đang phát triển.',
   6,  'SUBMITTED'),  -- sub 1 · MATCH (real repo)
  (2, 1, 'https://github.com/pallets/flask',  'https://demo.teamdragon.io',  'https://slides.com/dragon',
   'EduDragon là nền tảng học tập trực tuyến cho phép giảng viên tạo khoá học, tải video bài giảng và ra quiz tự chấm. Sinh viên theo dõi tiến độ học và nhận gợi ý bài tiếp theo. Stack Node.js + PostgreSQL + React, đã có xác thực JWT và phân quyền giảng viên/sinh viên. Vòng sơ khảo demo được tạo khoá học và làm quiz; tính năng thanh toán khoá học trả phí chưa hoàn thiện.',
   16, 'SUBMITTED'),  -- sub 2 · MISMATCH (repo is Flask)
  (3, 1, 'https://github.com/team-tiger/seal',   'https://demo.teamtiger.io',   'https://slides.com/tiger',
   'HealthTiger là ứng dụng di động theo dõi sức khoẻ sinh viên: ghi nhận số bước chân, giấc ngủ, nhịp tim và nhắc nhở uống nước. App native (Kotlin/Swift) kết nối cảm biến điện thoại, hiển thị biểu đồ tuần/tháng và đặt mục tiêu cá nhân. Vòng sơ khảo chạy mượt phần thu thập dữ liệu và biểu đồ; chưa có đồng bộ dữ liệu lên cloud nên đổi máy sẽ mất lịch sử.',
   12, 'SUBMITTED'),  -- sub 3
  (4, 1, 'https://github.com/fastapi/full-stack-fastapi-template',   'https://demo.teameagle.io',   'https://slides.com/eagle',
   'AutoGrade dùng AI để chấm điểm bài tập lập trình và bài luận ngắn tự động. Hệ thống chạy test case cho bài code và dùng mô hình NLP đánh giá bài luận theo rubric, sinh nhận xét gợi ý cho sinh viên. Backend Python FastAPI + mô hình fine-tune, dashboard cho giảng viên xem phân bố điểm. Vòng sơ khảo demo chấm code khá tốt; phần NLP chấm luận đôi khi lệch với giảng viên và chưa có báo cáo tổng hợp.',
   18, 'SUBMITTED'),  -- sub 4 · BONUS (FastAPI template repo)
  (5, 1, 'https://github.com/team-falcon/seal',  'https://demo.teamfalcon.io',  'https://slides.com/falcon',
   'BookFalcon là sàn trao đổi và mua bán sách cũ giữa sinh viên trong trường. Người dùng đăng sách, tìm theo môn học, nhắn tin thoả thuận và đánh giá người bán. Web full-stack React + Express + MongoDB, có tìm kiếm và lọc theo danh mục. Vòng sơ khảo mới hoàn thiện đăng tin và tìm kiếm; phần chat và đánh giá còn dở dang, UI cần trau chuốt thêm.',
   9,  'SUBMITTED'),  -- sub 5
  -- Semi-final (round 2)
  (1, 2, 'https://github.com/team-phoenix/seal', 'https://demo.teamphoenix.io/v2', 'https://slides.com/phoenix-semi',
   'SmartDorm bản vòng bán kết: bổ sung thanh toán phí KTX qua cổng giả lập, dashboard thống kê tỉ lệ lấp đầy phòng cho ban quản lý, và thông báo realtime khi yêu cầu báo hỏng được xử lý. Đã viết unit test cho module phòng và tích hợp CI. Trải nghiệm người dùng được làm lại gọn gàng hơn so với vòng sơ khảo.',
   6,  'SUBMITTED'),  -- sub 6
  (2, 2, 'https://github.com/team-dragon/seal',  'https://demo.teamdragon.io/v2',  'https://slides.com/dragon-semi',
   'EduDragon bản vòng bán kết: thêm lộ trình học cá nhân hoá gợi ý bài tiếp theo dựa trên kết quả quiz, diễn đàn hỏi đáp theo khoá học và xuất chứng chỉ hoàn thành. Tối ưu truy vấn báo cáo tiến độ và bổ sung phân trang. Pitch deck trình bày rõ mô hình doanh thu freemium.',
   16, 'SUBMITTED'),  -- sub 7
  (3, 2, 'https://github.com/team-tiger/seal',   'https://demo.teamtiger.io/v2',   'https://slides.com/tiger-semi',
   'HealthTiger bản vòng bán kết: đã thêm đồng bộ dữ liệu lên cloud (Firebase), đăng nhập đa thiết bị và tính năng thử thách nhóm để bạn bè cùng vận động. Cải thiện hiệu năng vẽ biểu đồ và giảm hao pin khi chạy nền. Giao diện mobile được đánh giá tự nhiên, mượt.',
   12, 'SUBMITTED'),  -- sub 8
  (4, 2, 'https://github.com/team-eagle/seal',   'https://demo.teameagle.io/v2',   'https://slides.com/eagle-semi',
   'AutoGrade bản vòng bán kết: bổ sung dashboard phân tích cho giảng viên (phân bố điểm, câu sai nhiều nhất), cho phép giảng viên ghi đè điểm AI và lưu lại lịch sử chỉnh sửa. Mô hình NLP được hiệu chỉnh lại rubric nên bám sát giảng viên hơn. Vẫn cần thêm dữ liệu huấn luyện cho môn tự luận dài.',
   18, 'SUBMITTED'),  -- sub 9
  -- Final (round 3)
  (1, 3, 'https://github.com/team-phoenix/seal', 'https://demo.teamphoenix.io/final', 'https://slides.com/phoenix-final',
   'SmartDorm bản chung kết: sản phẩm gần như hoàn chỉnh — quản lý phòng, báo hỏng, thanh toán, điểm danh QR và báo cáo cho ban quản lý đều hoạt động ổn định. Đã triển khai thử nghiệm cho một toà KTX với dữ liệu thật, có tài liệu hướng dẫn và phân tích khả năng nhân rộng ra nhiều cơ sở.',
   6,  'SUBMITTED'),  -- sub 10
  (3, 3, 'https://github.com/team-tiger/seal',   'https://demo.teamtiger.io/final',   'https://slides.com/tiger-final',
   'HealthTiger bản chung kết: hoàn thiện đồng bộ cloud, thử thách nhóm và tích hợp nhắc nhở sức khoẻ thông minh theo thói quen người dùng. Có khảo sát người dùng thật cho thấy mức độ giữ chân tốt. Định hướng mở rộng sang hợp tác với phòng y tế trường để theo dõi sức khoẻ tổng thể.',
   12, 'SUBMITTED'),  -- sub 11
  (4, 3, 'https://github.com/team-eagle/seal',   'https://demo.teameagle.io/final',   'https://slides.com/eagle-final',
   'AutoGrade bản chung kết: nền tảng chấm tự động cho cả code và tự luận, kèm dashboard phân tích và cơ chế giám sát của giảng viên để đảm bảo công bằng. Đã đo độ đồng thuận giữa AI và giảng viên trên tập bài thật và trình bày hướng cải thiện. Tiềm năng áp dụng cho các môn lập trình quy mô lớn.',
   18, 'SUBMITTED');  -- sub 12


-- =====================================================
-- 12. SCORE — event 1
-- judge_user_id: 4=Binh, 20=Cam, 3=An, 5=Guest
-- criteria_id: Prelim=1-5, Semi=6-10, Final=11-15
-- =====================================================
-- ── Preliminary: Binh scores Web (sub 1=Phoenix, 2=Dragon, 5=Falcon)
INSERT INTO Score (submission_id, judge_user_id, criteria_id, value, comment, is_draft) VALUES
  (1, 4, 1, 9.0, 'Y tuong KTX thong minh rat thuc tien', FALSE),
  (1, 4, 2, 9.5, 'Code sach, kien truc tot, co CI/CD',    FALSE),
  (1, 4, 3, 8.5, 'UI hien dai, UX muot ma',               FALSE),
  (1, 4, 4, 9.0, 'Demo live rat an tuong',                FALSE),
  (1, 4, 5, 9.0, 'San pham gan nhu hoan chinh',           FALSE),
  (2, 4, 1, 7.5, 'Y tuong hoc tap truc tuyen kha pho bien', FALSE),
  (2, 4, 2, 8.0, 'Backend on dinh, co authentication',    FALSE),
  (2, 4, 3, 8.5, 'UI dep, responsive tot',                FALSE),
  (2, 4, 4, 7.5, 'Trinh bay on',                          FALSE),
  (2, 4, 5, 7.5, 'Con thieu tinh nang thanh toan',        FALSE),
  (5, 4, 1, 7.0, 'Y tuong marketplace binh thuong',       FALSE),
  (5, 4, 2, 7.5, 'Ky thuat co ban, thieu toi uu',         FALSE),
  (5, 4, 3, 7.0, 'UI can cai thien them',                 FALSE),
  (5, 4, 4, 7.5, 'Demo kha on',                           FALSE),
  (5, 4, 5, 6.5, 'Con nhieu tinh nang do dang',           FALSE);

-- ── Preliminary: Cam scores Mobile (sub 3=Tiger)
INSERT INTO Score (submission_id, judge_user_id, criteria_id, value, comment, is_draft) VALUES
  (3, 20, 1, 8.0, 'Y tuong health tracking thuc te',  FALSE),
  (3, 20, 2, 8.5, 'Native app, performance tot',      FALSE),
  (3, 20, 3, 9.0, 'UI mobile rat dep, UX tu nhien',   FALSE),
  (3, 20, 4, 8.0, 'Thuyet trinh tu tin',              FALSE),
  (3, 20, 5, 7.5, 'Thieu tinh nang sync cloud',       FALSE);

-- ── Preliminary: An + Guest score AI (sub 4=Eagle)
INSERT INTO Score (submission_id, judge_user_id, criteria_id, value, comment, is_draft) VALUES
  (4, 3, 1, 8.5, 'Ung dung AI vao cham bai rat sang tao', FALSE),
  (4, 3, 2, 8.0, 'Model NLP dat accuracy kha',            FALSE),
  (4, 3, 3, 7.5, 'UI teacher dashboard can cai thien',    FALSE),
  (4, 3, 4, 8.0, 'Demo live AI chay tot',                 FALSE),
  (4, 3, 5, 7.0, 'Chua co tinh nang phan tich bao cao',   FALSE),
  (4, 5, 1, 9.0, 'Very innovative use of NLP', FALSE),
  (4, 5, 2, 8.0, 'Good model performance',      FALSE),
  (4, 5, 3, 7.0, 'UI needs improvement',        FALSE),
  (4, 5, 4, 8.5, 'Great live demo',             FALSE),
  (4, 5, 5, 7.5, 'Core features solid',         FALSE);

-- ── Semi-final: Binh(6,7), Cam(8), An(9)
INSERT INTO Score (submission_id, judge_user_id, criteria_id, value, comment, is_draft) VALUES
  (6,  4, 6,  9.0, 'Phat trien them nhieu tinh nang moi', FALSE),
  (6,  4, 7,  9.5, 'Refactor tot, them unit test',        FALSE),
  (6,  4, 8,  9.0, 'UX cai thien ro ret',                 FALSE),
  (6,  4, 9,  9.0, 'Pitch deck chuyen nghiep',            FALSE),
  (6,  4, 10, 8.5, 'Co ke hoach trien khai thuc te',      FALSE),
  (7,  4, 6,  8.0, 'Bo sung them tinh nang quiz',         FALSE),
  (7,  4, 7,  8.5, 'Them payment integration',            FALSE),
  (7,  4, 8,  8.5, 'Dark mode moi trong chuyen nghiep',   FALSE),
  (7,  4, 9,  8.0, 'Cai thien so voi vong truoc',         FALSE),
  (7,  4, 10, 7.5, 'Thi truong canh tranh cao',           FALSE),
  (8, 20, 6,  8.5, 'Them tich hop wearable device',       FALSE),
  (8, 20, 7,  9.0, 'Performance cai thien dang ke',       FALSE),
  (8, 20, 8,  9.0, 'UI duoc polish ky',                   FALSE),
  (8, 20, 9,  8.0, 'Demo wearable rat an tuong',          FALSE),
  (8, 20, 10, 8.0, 'Tiem nang mo rong tot',               FALSE),
  (9, 3, 6,  9.0, 'Model accuracy tang len 92%',          FALSE),
  (9, 3, 7,  8.5, 'Them API integration voi LMS',         FALSE),
  (9, 3, 8,  8.0, 'Dashboard analytics moi rat huu ich',  FALSE),
  (9, 3, 9,  8.5, 'Giai thich model AI rat thuyet phuc',  FALSE),
  (9, 3, 10, 8.0, 'Thi truong EdTech tiem nang',          FALSE);

-- ── Final: Binh(10), Cam(11), An+Guest(12)
INSERT INTO Score (submission_id, judge_user_id, criteria_id, value, comment, is_draft) VALUES
  (10, 4, 11, 9.5, 'San pham xuat sac, rat sang tao',     FALSE),
  (10, 4, 12, 9.5, 'Kien truc microservice hoan chinh',   FALSE),
  (10, 4, 13, 9.0, 'UI/UX dat chuan production',          FALSE),
  (10, 4, 14, 9.5, 'Pitch hoan hao, Q&A tra loi tot',     FALSE),
  (10, 4, 15, 9.0, 'Ke hoach go-to-market chi tiet',      FALSE),
  (11, 20, 11, 8.5, 'San pham health tech hoan thien',    FALSE),
  (11, 20, 12, 9.0, 'Native app performance xuat sac',    FALSE),
  (11, 20, 13, 9.5, 'UX mobile tot nhat trong tat ca',    FALSE),
  (11, 20, 14, 8.5, 'Demo wearable truc tiep an tuong',   FALSE),
  (11, 20, 15, 8.0, 'Thi truong rong nhung canh tranh cao', FALSE),
  (12, 3, 11, 9.0, 'AI grading la giai phap rat can thiet', FALSE),
  (12, 3, 12, 8.5, 'Model fine-tuned tot, latency thap',  FALSE),
  (12, 3, 13, 8.5, 'Teacher UX cai thien nhieu',          FALSE),
  (12, 3, 14, 9.0, 'Thuyet trinh mach lac, co so lieu',   FALSE),
  (12, 3, 15, 9.0, 'EdTech market rat tiem nang',         FALSE),
  (12, 5, 11, 9.5, 'Most innovative solution overall',    FALSE),
  (12, 5, 12, 8.5, 'Robust API and model pipeline',       FALSE),
  (12, 5, 13, 8.0, 'Good but could be more intuitive',    FALSE),
  (12, 5, 14, 9.5, 'Best presentation of the final round', FALSE),
  (12, 5, 15, 9.0, 'Clear monetization strategy',         FALSE);


-- =====================================================
-- 13. ROUND RESULT - event 1
-- "advanced" is NOT stored; derive it as rank_position <= Round.top_n_advance
-- (per track for preliminary rounds).
-- =====================================================
-- NOTE: preliminary/semi rank_position is PER TRACK (rank restarts at 1 within each
-- track), matching how the backend finalizes non-final rounds. Final (round 3) is global.
INSERT INTO RoundResult (team_id, round_id, total_score, rank_position, is_published, finalized_at, finalized_by) VALUES
  -- Preliminary (round 1) - per track. Web(t1): Phoenix,Dragon,Falcon | Mobile(t2): Tiger | AI(t3): Eagle
  (1, 1, 63.50, 1, TRUE, '2026-02-28 22:00:00', 2),  -- Phoenix  Web   #1  (advance)
  (2, 1, 54.50, 2, TRUE, '2026-02-28 22:00:00', 2),  -- Dragon   Web   #2  (advance)
  (5, 1, 49.75, 3, TRUE, '2026-02-28 22:00:00', 2),  -- Falcon   Web   #3  (eliminated, topN=2)
  (3, 1, 57.25, 1, TRUE, '2026-02-28 22:00:00', 2),  -- Tiger    Mobile #1 (advance)
  (4, 1, 55.50, 1, TRUE, '2026-02-28 22:00:00', 2),  -- Eagle    AI    #1  (advance)
  -- Semi-final (round 2) - per track. Web(t1): Phoenix,Dragon | Mobile(t2): Tiger | AI(t3): Eagle
  (1, 2, 63.50, 1, TRUE, '2026-03-31 22:00:00', 2),  -- Phoenix  Web   #1  (advance)
  (2, 2, 57.00, 2, TRUE, '2026-03-31 22:00:00', 2),  -- Dragon   Web   #2  (eliminated, topN=1)
  (3, 2, 59.75, 1, TRUE, '2026-03-31 22:00:00', 2),  -- Tiger    Mobile #1 (advance)
  (4, 2, 59.25, 1, TRUE, '2026-03-31 22:00:00', 2),  -- Eagle    AI    #1  (advance)
  -- Final (round 3) - GLOBAL ranking, no track
  (1, 3, 65.50, 1, TRUE, '2026-04-30 20:00:00', 2),  -- Phoenix - Web Champion
  (4, 3, 62.00, 2, TRUE, '2026-04-30 20:00:00', 2),  -- Eagle - AI Champion
  (3, 3, 61.00, 3, TRUE, '2026-04-30 20:00:00', 2);  -- Tiger - Mobile Champion


-- =====================================================
-- 14. PRIZE
-- Prizes are EVENT-WIDE: top N of the FINAL round's GLOBAL ranking (all tracks
-- combined), so track_id is always NULL. See Final (round 3) RoundResult above.
-- Event 1 (COMPLETED): announced top 3. Event 2 (running): no prizes yet - they
-- get generated live via POST /api/events/2/prizes/auto-generate in the demo.
-- =====================================================
-- Event 1 - Final ranking: Phoenix(1)=65.50, Eagle(4)=62.00, Tiger(3)=61.00
INSERT INTO Prize (event_id, track_id, name, description, rank_position, team_id, awarded_at) VALUES
  (1, NULL, 'Champion',      'Top 1 - Spring 2026', 1, 1, '2026-04-30 20:00:00'),  -- Phoenix
  (1, NULL, '1st Runner-up', 'Top 2 - Spring 2026', 2, 4, '2026-04-30 20:00:00'),  -- Eagle
  (1, NULL, '2nd Runner-up', 'Top 3 - Spring 2026', 3, 3, '2026-04-30 20:00:00');  -- Tiger

-- 15. ACCOUNT APPROVAL
-- Approved by coordinator (user_id 2). 2 users left PENDING to test the flow.
-- =====================================================
INSERT INTO AccountApproval (user_id, reviewed_by, status, note, reviewed_at) VALUES
  (3,  2, 'APPROVED', NULL, '2026-01-05 09:10:00'),
  (4,  2, 'APPROVED', NULL, '2026-01-05 09:12:00'),
  (7,  2, 'APPROVED', NULL, '2026-01-05 09:15:00'),
  (8,  2, 'APPROVED', NULL, '2026-01-05 09:18:00'),
  (9,  2, 'APPROVED', NULL, '2026-02-10 10:00:00'),
  (10, 2, 'APPROVED', NULL, '2026-02-10 10:05:00'),
  (12, 2, 'APPROVED', NULL, '2026-02-10 10:10:00'),
  (13, 2, 'APPROVED', NULL, '2026-02-10 10:12:00'),
  (14, 2, 'APPROVED', NULL, '2026-05-20 09:00:00'),
  (15, 2, 'APPROVED', NULL, '2026-05-20 09:05:00'),
  (16, 2, 'APPROVED', NULL, '2026-05-20 09:08:00'),
  (17, 2, 'APPROVED', NULL, '2026-05-20 09:10:00'),
  (18, 2, 'APPROVED', NULL, '2026-05-20 09:12:00'),
  -- Pending (self-registered, awaiting review)
  (11, NULL, 'PENDING', NULL, NULL),
  (24, NULL, 'PENDING', NULL, NULL);


-- =====================================================
-- 16. TEAM INVITE
-- =====================================================
INSERT INTO TeamInvite (team_id, event_id, invited_user_id, invited_by, message, status) VALUES
  -- Team Horizon (event 2) invites user 24 (external, pending approval)
  (6, 2, 24, 21, 'Chao ban! Team Horizon muon moi ban tham gia nhe.', 'PENDING'),
  -- Team Verde (event 2) invites user 13 — accepted (now a member)
  (8, 2, 13, 12, NULL, 'ACCEPTED');


-- =====================================================
-- 17. NOTIFICATION
-- =====================================================
INSERT INTO Notification (recipient_user_id, title, content, type) VALUES
  -- Event 1 finished — results
  (6,  'SEAL Spring 2026 da ket thuc — Team Phoenix doat giai Quan quan!',
       'Chuc mung Team Phoenix da gianh giai Champion hang Web Application va Best Overall!', 'RESULT'),
  (18, 'SEAL Spring 2026 da ket thuc — Team Eagle doat giai AI Champion!',
       'Chuc mung Team Eagle da gianh giai Champion hang AI Solution!', 'RESULT'),
  (12, 'SEAL Spring 2026 da ket thuc — Team Tiger doat giai Mobile Champion!',
       'Chuc mung Team Tiger da gianh giai Champion hang Mobile App!', 'RESULT'),
  -- Event 2 in progress — Preliminary
  (21, 'SEAL Summer 2026 dang dien ra',
       'Vong Preliminary dang mo. Han nop bai: 30/06/2026. Dang nhap de nop bai!', 'ANNOUNCEMENT'),
  (6,  'SEAL Summer 2026 dang dien ra',
       'Vong Preliminary dang mo. Han nop bai: 30/06/2026. Dang nhap de nop bai!', 'ANNOUNCEMENT'),
  (12, 'SEAL Summer 2026 dang dien ra',
       'Vong Preliminary dang mo. Han nop bai: 30/06/2026. Dang nhap de nop bai!', 'ANNOUNCEMENT'),
  -- Event 2 teams approved
  (21, 'Team Horizon da duoc duyet',
       'Team cua ban da duoc ban to chuc phe duyet tham gia SEAL Summer 2026.', 'APPROVAL'),
  (6,  'Team Nexus da duoc duyet',
       'Team cua ban da duoc ban to chuc phe duyet tham gia SEAL Summer 2026.', 'APPROVAL'),
  (12, 'Team Verde da duoc duyet',
       'Team cua ban da duoc ban to chuc phe duyet tham gia SEAL Summer 2026.', 'APPROVAL');


-- =====================================================
-- 18. AUDIT LOG  (event business actions)
-- =====================================================
INSERT INTO AuditLog (actor_user_id, action, target_type, target_id, reason, metadata_json, ip_address) VALUES
  -- Event lifecycle
  (2, 'CREATE_EVENT',   'EVENT', 1, NULL,
   '{"event_name":"SEAL Spring 2026","status":"DRAFT"}', '192.168.1.1'),
  (2, 'COMPLETE_EVENT', 'EVENT', 1, 'Su kien da ket thuc theo lich',
   '{"event_name":"SEAL Spring 2026","completed_at":"2026-04-30T23:59:59"}', '192.168.1.1'),
  (2, 'CREATE_EVENT',   'EVENT', 2, NULL,
   '{"event_name":"SEAL Summer 2026","status":"OPEN"}', '192.168.1.1'),
  (2, 'START_EVENT',    'EVENT', 2, 'Dong dang ky, khoi dong vong Preliminary',
   '{"event_name":"SEAL Summer 2026","before":"OPEN","after":"IN_PROGRESS","started_at":"2026-06-01T08:00:00"}', '192.168.1.1'),
  (2, 'CREATE_EVENT',   'EVENT', 3, NULL,
   '{"event_name":"SEAL Fall 2026","status":"DRAFT"}', '192.168.1.1'),
  -- Event 1 team approvals
  (2, 'APPROVE_TEAM', 'TEAM', 1, NULL,
   '{"team_name":"Team Phoenix","event_id":1,"before":"PENDING","after":"APPROVED"}', '192.168.1.1'),
  (2, 'APPROVE_TEAM', 'TEAM', 2, NULL,
   '{"team_name":"Team Dragon","event_id":1,"before":"PENDING","after":"APPROVED"}', '192.168.1.1'),
  (2, 'APPROVE_TEAM', 'TEAM', 3, NULL,
   '{"team_name":"Team Tiger","event_id":1,"before":"PENDING","after":"APPROVED"}', '192.168.1.1'),
  (2, 'APPROVE_TEAM', 'TEAM', 4, NULL,
   '{"team_name":"Team Eagle","event_id":1,"before":"PENDING","after":"APPROVED"}', '192.168.1.1'),
  (2, 'APPROVE_TEAM', 'TEAM', 5, NULL,
   '{"team_name":"Team Falcon","event_id":1,"before":"PENDING","after":"APPROVED"}', '192.168.1.1'),
  -- Judge assignment (now that assigned_by is no longer on the table)
  (2, 'ASSIGN_JUDGE', 'ROUND', 1, NULL,
   '{"event_id":1,"judges":["Binh->Web","Cam->Mobile","An->AI","Guest->AI"]}', '192.168.1.1'),
  -- Event 1 result publishing
  (2, 'PUBLISH_RESULT', 'ROUND', 1, NULL,
   '{"round_name":"Preliminary","event_id":1,"published_at":"2026-02-28T22:00:00"}', '192.168.1.1'),
  (2, 'PUBLISH_RESULT', 'ROUND', 2, NULL,
   '{"round_name":"Semi-final","event_id":1,"published_at":"2026-03-31T22:00:00"}', '192.168.1.1'),
  (2, 'PUBLISH_RESULT', 'ROUND', 3, NULL,
   '{"round_name":"Final","event_id":1,"published_at":"2026-04-30T20:00:00"}', '192.168.1.1'),
  (2, 'AWARD_PRIZE', 'PRIZE', 1, NULL,
   '{"eventId":1,"winners":["#1 Champion -> Team Phoenix","#2 1st Runner-up -> Team Eagle","#3 2nd Runner-up -> Team Tiger"]}', '192.168.1.1'),
  (4, 'UPDATE_SCORE', 'SCORE', 1, 'Xem lai demo, dieu chinh diem Innovation',
   '{"before":7.5,"after":9.0,"criteria":"Innovation","submission_id":1}', '192.168.1.2'),
  -- Event 2 team approvals
  (2, 'APPROVE_TEAM', 'TEAM', 6, NULL,
   '{"team_name":"Team Horizon","event_id":2,"before":"PENDING","after":"APPROVED"}', '192.168.1.1'),
  (2, 'APPROVE_TEAM', 'TEAM', 7, NULL,
   '{"team_name":"Team Nexus","event_id":2,"before":"PENDING","after":"APPROVED"}', '192.168.1.1'),
  (2, 'APPROVE_TEAM', 'TEAM', 8, NULL,
   '{"team_name":"Team Verde","event_id":2,"before":"PENDING","after":"APPROVED"}', '192.168.1.1');


-- =====================================================
-- 19. SYSTEM LOG  (admin/platform actions)
-- =====================================================
INSERT INTO SystemLog (actor_user_id, action, detail, ip_address) VALUES
  (1, 'CREATE_USER',  'Created coordinator account user#2 (coordinator@fpt.edu.vn)', '10.0.0.1'),
  (1, 'GRANT_ROLE',   'Granted EVENT_COORDINATOR to user#2',                          '10.0.0.1'),
  (1, 'CREATE_USER',  'Created guest judge account user#5 (guest.judge@gmail.com)',   '10.0.0.1'),
  (2, 'LOGIN_FAILED', 'Failed login attempt for coordinator@fpt.edu.vn (bad password)', '192.168.1.1');


