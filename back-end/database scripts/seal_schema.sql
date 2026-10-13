-- =====================================================
-- SEAL Hackathon Management System
-- MySQL DDL Script  (idempotent — safe to re-run)
-- 28 tables
--
-- CHANGELOG (assignment redesign):
--   - Removed TeamAssignment (duplicate + wrong business unit).
--   - Slimmed UserEventRole to pure N-N "who is allowed to be what role".
--   - Added JudgeAssignment (judge scores per round + track).
--   - Added MentorAssignment (mentor supports per track).
--   - Added Round.is_final flag to distinguish final round (judge-all)
--     from preliminary rounds (judge-per-track).
--
-- CHANGELOG (admin / coordinator split):
--   - Added SYSTEM_ADMIN role (role_id = 1; existing roles shifted down).
--     System Admin runs the PLATFORM (users, role grants, global templates,
--     system logs). Event Coordinator runs the COMPETITION (events, rounds,
--     scoring, ranking) scoped to an event.
--   - UserEventRole.event_id is NULL for system-wide roles (SYSTEM_ADMIN).
--     NOTE: MySQL treats NULL as distinct in UNIQUE keys, so the
--     (user_id, role_id, event_id) unique key does NOT block duplicate
--     admin grants. The service layer must guard against that (MVP choice).
--   - Added index idx_uer_role_systemwide for fast system-wide role lookup.
--   - Added SystemLog table (minimal: action + detail) for admin/platform
--     events — kept separate from AuditLog, which stays for competition
--     business actions (scoring, disqualify, publish).
--   - Seeded one bootstrap SYSTEM_ADMIN and one EVENT_COORDINATOR account.
--
-- CHANGELOG (announcements — folds in migration_announcement.sql):
--   - Added Announcement table: a Mentor (scoped to a track) or a Coordinator
--     (scoped to the whole event) broadcasts a message; it is the source of
--     truth for "sent history" and the email-style popup.
--   - Added Notification.announcement_id back-link (NULL for non-announcement
--     notifications) so the popup can resolve sender + scope.
--
-- CHANGELOG (round timers — folds in seal_roundtimer.sql):
--   - Added RoundTimer: a live, server-authoritative countdown per (round, phase).
--     CONTEST phase gates team submission; JUDGING phase gates judge scoring.
--     Coordinator controls start/pause/resume/extend/stop; remaining time is
--     derived from ends_at so reloads/restarts recompute the same value.
--   - Added RoundTimerNotice: exactly-once ledger so milestone reminders are
--     fanned out to the audience only once (no scheduler; materialised lazily on read).
-- =====================================================

-- Drop and recreate so this script is always safe to re-run.
-- WARNING: this wipes all existing data. For production use a
-- proper migration tool (Flyway / Liquibase) instead.
DROP DATABASE IF EXISTS seal_hackathon;

CREATE DATABASE seal_hackathon
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE seal_hackathon;

-- =====================================================
-- USER & ROLE MANAGEMENT
-- =====================================================

CREATE TABLE Role (
  role_id     INT          NOT NULL AUTO_INCREMENT,
  role_name   VARCHAR(50)  NOT NULL COMMENT 'SYSTEM_ADMIN, EVENT_COORDINATOR, MENTOR, JUDGE',
  description VARCHAR(255),
  PRIMARY KEY (role_id),
  UNIQUE KEY uq_role_name (role_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- password_hash is NULL-able: OAuth2 users (Google/GitHub) have no local password.
-- provider tracks which auth method was used: LOCAL | GOOGLE | GITHUB
CREATE TABLE `User` (
  user_id       INT          NOT NULL AUTO_INCREMENT,
  email         VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255)          COMMENT 'NULL for OAuth2 users',
  full_name     VARCHAR(255) NOT NULL,
  user_type     VARCHAR(20)  NOT NULL COMMENT 'FPT_STUDENT, EXTERNAL_STUDENT, STAFF',
  provider      VARCHAR(20)  NOT NULL DEFAULT 'LOCAL' COMMENT 'LOCAL, GOOGLE, GITHUB',
  provider_id   VARCHAR(255)          COMMENT 'Unique user ID from the OAuth2 provider',
  avatar_url    VARCHAR(500)          COMMENT 'Profile picture URL from OAuth2 provider',
  student_id    VARCHAR(50)           COMMENT 'FPT student ID or external student ID',
  university    VARCHAR(255)          COMMENT 'For external students only',
  judge_type    VARCHAR(20)           COMMENT 'INTERNAL or GUEST — only set for users who act as judges; NULL otherwise',
  is_approved   BOOLEAN      NOT NULL DEFAULT FALSE,
  is_active     BOOLEAN      NOT NULL DEFAULT TRUE COMMENT 'FALSE = participant read-only, TRUE = writable/active',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expired_at    DATETIME              COMMENT 'Account expiry (set manually), e.g. guest judge valid until event end. NULL = never expires',
  PRIMARY KEY (user_id),
  UNIQUE KEY uq_email (email),
  KEY idx_user_type (user_type),
  KEY idx_user_provider (provider, provider_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ParticipationAccessRequest (
  request_id    INT          NOT NULL AUTO_INCREMENT,
  user_id       INT          NOT NULL,
  email         VARCHAR(255) NOT NULL,
  status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, APPROVED, REJECTED',
  requested_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resolved_at   DATETIME              NULL,
  resolved_by   INT                   NULL,
  PRIMARY KEY (request_id),
  KEY idx_participation_access_status (status, requested_at),
  KEY idx_participation_access_user_status (user_id, status),
  CONSTRAINT fk_participation_access_user
    FOREIGN KEY (user_id) REFERENCES `User`(user_id)
    ON DELETE CASCADE,
  CONSTRAINT fk_participation_access_resolved_by
    FOREIGN KEY (resolved_by) REFERENCES `User`(user_id)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE PasswordResetOtp (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  user_id          INT          NOT NULL,
  otp_hash         VARCHAR(255) NOT NULL COMMENT 'PasswordEncoder hash of the 6-digit OTP',
  reset_token_hash VARCHAR(255)          COMMENT 'SHA-256 hash of the temporary token issued after OTP verification',
  expires_at       DATETIME     NOT NULL,
  verified_at      DATETIME,
  used_at          DATETIME,
  attempt_count    INT          NOT NULL DEFAULT 0,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_password_reset_token_hash (reset_token_hash),
  KEY idx_password_reset_user_active (user_id, used_at, created_at),
  KEY idx_password_reset_expires_at (expires_at),
  CONSTRAINT fk_password_reset_otp_user FOREIGN KEY (user_id) REFERENCES `User` (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- EVENT STRUCTURE
-- =====================================================

CREATE TABLE HackathonEvent (
  event_id           INT          NOT NULL AUTO_INCREMENT,
  name               VARCHAR(255) NOT NULL COMMENT 'e.g. SEAL Spring 2026',
  season             VARCHAR(20)  NOT NULL COMMENT 'SPRING, SUMMER, FALL',
  year               INT          NOT NULL,
  description        TEXT,
  registration_start DATETIME,
  registration_end   DATETIME,
  start_date         DATETIME     NOT NULL,
  end_date           DATETIME     NOT NULL,
  status             VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT, OPEN, SETUP, IN_PROGRESS, COMPLETED, CANCELLED',
  track_selection_mode VARCHAR(20) NOT NULL DEFAULT 'SELF_SELECT' COMMENT 'SELF_SELECT (leaders pick during SETUP) or RANDOM (coordinator draws)',
  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (event_id),
  KEY idx_event_season_year (season, year),
  KEY idx_event_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE Track (
  track_id    INT          NOT NULL AUTO_INCREMENT,
  event_id    INT          NOT NULL,
  name        VARCHAR(255) NOT NULL COMMENT 'e.g. Web Application, AI Solution',
  description TEXT,
  capacity    INT                   COMMENT 'Max teams; auto-computed when event enters SETUP. NULL = unlimited',
  -- "Đề thi" (problem statement): ONE file per track. Coordinator uploads while
  -- the event is SETUP/IN_PROGRESS, then explicitly releases it. The file lives
  -- OUTSIDE the public /uploads dir and is streamed via an access-controlled
  -- endpoint, so only the storage key is kept here (not a public URL).
  problem_storage_key  VARCHAR(500)          COMMENT 'Internal path/key of the stored problem file. NULL = no problem uploaded yet',
  problem_file_name    VARCHAR(255)          COMMENT 'Original file name, shown to participants on download',
  problem_file_size    BIGINT                COMMENT 'File size in bytes (for display)',
  problem_content_type VARCHAR(100)          COMMENT 'MIME type, returned on download',
  problem_released     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1 = published; participants in the track can download',
  problem_uploaded_at  DATETIME              COMMENT 'When the problem file was uploaded / last replaced',
  problem_released_at  DATETIME              COMMENT 'When the problem was released (NULL while hidden)',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (track_id),
  UNIQUE KEY uq_track_event_name (event_id, name),
  CONSTRAINT fk_track_event FOREIGN KEY (event_id) REFERENCES HackathonEvent (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE Round (
  round_id            INT          NOT NULL AUTO_INCREMENT,
  event_id            INT          NOT NULL,
  name                VARCHAR(255) NOT NULL COMMENT 'e.g. Preliminary, Semi-final, Final',
  order_number        INT          NOT NULL COMMENT 'Round sequence: 1, 2, 3...',
  start_time          DATETIME     NOT NULL,
  end_time            DATETIME     NOT NULL,
  submission_deadline DATETIME     NOT NULL,
  top_n_advance       INT                   COMMENT 'Top N teams per track that advance to next round',
  is_final            BOOLEAN      NOT NULL DEFAULT FALSE COMMENT 'TRUE = final round (judges score all, no per-track split)',
  status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, ACTIVE, CLOSED, FINALIZED',
  PRIMARY KEY (round_id),
  UNIQUE KEY uq_round_event_order (event_id, order_number),
  CONSTRAINT fk_round_event FOREIGN KEY (event_id) REFERENCES HackathonEvent (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- ROUND TIMERS  (must come after Round)
-- A live, server-authoritative countdown per round, one row per (round, phase):
--   CONSTEST phase  -> gates team SUBMISSION  (participants)
--   JUDGING  phase  -> gates judge SCORING    (judges)
-- The coordinator starts/pauses/resumes/extends/stops it; remaining time is
-- derived from ends_at (never the client clock). State is kept in the DB so a
-- reload / backend restart recomputes the same countdown. Milestone reminders
-- are configurable per timer (milestone_minutes + notify_at_half).
-- =====================================================

CREATE TABLE RoundTimer (
  timer_id           INT          NOT NULL AUTO_INCREMENT,
  round_id           INT          NOT NULL,
  phase              VARCHAR(20)  NOT NULL COMMENT 'CONTEST (gates submission) | JUDGING (gates scoring)',
  status             VARCHAR(20)  NOT NULL DEFAULT 'IDLE' COMMENT 'IDLE, RUNNING, PAUSED, STOPPED, EXPIRED',
  duration_seconds   INT                   COMMENT 'Configured length of the run, in seconds',
  started_at         DATETIME              COMMENT 'When the current run started (last start/resume)',
  ends_at            DATETIME              COMMENT 'Source of truth for the countdown; remaining = ends_at - now',
  paused_at          DATETIME              COMMENT 'When paused (NULL while running)',
  remaining_at_pause INT                   COMMENT 'Seconds left captured at pause; ends_at = now + this on resume',
  milestone_minutes  VARCHAR(100) NOT NULL DEFAULT '30,15,5,1' COMMENT 'CSV of "minutes remaining" reminder marks; only marks < duration fire',
  notify_at_half     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '1 = also notify when 50% of the time has elapsed',
  updated_at         DATETIME              ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (timer_id),
  UNIQUE KEY uq_roundtimer_round_phase (round_id, phase),
  CONSTRAINT fk_roundtimer_round FOREIGN KEY (round_id) REFERENCES Round (round_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Exactly-once ledger for milestone fan-out. Without a scheduler, milestone
-- notifications are materialised lazily when any client reads the timer state;
-- this unique key guarantees each (round, phase, mark) is fanned out to the
-- audience only once, regardless of how many clients trigger the read.
CREATE TABLE RoundTimerNotice (
  id            INT          NOT NULL AUTO_INCREMENT,
  round_id      INT          NOT NULL,
  phase         VARCHAR(20)  NOT NULL,
  milestone_key VARCHAR(30)  NOT NULL COMMENT 'STARTED, REM_30, REM_15, REM_5, REM_1, HALF, EXPIRED, STOPPED',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_timer_notice (round_id, phase, milestone_key),
  CONSTRAINT fk_timer_notice_round FOREIGN KEY (round_id) REFERENCES Round (round_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- USER EVENT ROLE  (must come after Event)
-- Pure N-N: lets one user hold multiple roles (e.g. JUDGE + MENTOR)
-- in the same event. This table answers "WHO IS ALLOWED to be what".
-- The actual work assignment lives in JudgeAssignment / MentorAssignment.
-- =====================================================

CREATE TABLE UserEventRole (
  id          INT       NOT NULL AUTO_INCREMENT,
  user_id     INT       NOT NULL,
  role_id     INT       NOT NULL,
  event_id    INT                COMMENT 'NULL for system-wide roles (SYSTEM_ADMIN)',
  PRIMARY KEY (id),
  UNIQUE KEY uq_user_role_event (user_id, role_id, event_id),
  KEY idx_uer_user_event (user_id, event_id),
  KEY idx_uer_event_role (event_id, role_id),
  KEY idx_uer_role_systemwide (role_id, event_id) COMMENT 'Fast lookup for system-wide roles (event_id IS NULL, e.g. SYSTEM_ADMIN)',
  CONSTRAINT fk_uer_user  FOREIGN KEY (user_id)  REFERENCES `User` (user_id),
  CONSTRAINT fk_uer_role  FOREIGN KEY (role_id)  REFERENCES Role (role_id),
  CONSTRAINT fk_uer_event FOREIGN KEY (event_id) REFERENCES HackathonEvent (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- WORK ASSIGNMENTS  (must come after Track & Round)
-- =====================================================

-- JudgeAssignment — which submissions a judge may score.
--   Preliminary round: track_id is set  -> judge scores that track only.
--   Final round:       track_id is NULL -> judge scores all teams.
-- Service layer must enforce:
--   round.is_final = FALSE  -> track_id REQUIRED
--   round.is_final = TRUE   -> track_id must be NULL
CREATE TABLE JudgeAssignment (
  id            INT          NOT NULL AUTO_INCREMENT,
  judge_user_id INT          NOT NULL,
  round_id      INT          NOT NULL,
  track_id      INT                   COMMENT 'NULL = score all (final round)',
  is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
  -- Who assigned this and when is captured in AuditLog (action = ASSIGN_JUDGE),
  -- since assigning a judge is an event business action, not a system action.
  PRIMARY KEY (id),
  UNIQUE KEY uq_judge_round_track (judge_user_id, round_id, track_id),
  KEY idx_ja_round_track (round_id, track_id),
  KEY idx_ja_judge (judge_user_id),
  CONSTRAINT fk_ja_judge FOREIGN KEY (judge_user_id) REFERENCES `User` (user_id),
  CONSTRAINT fk_ja_round FOREIGN KEY (round_id)      REFERENCES Round (round_id),
  CONSTRAINT fk_ja_track FOREIGN KEY (track_id)      REFERENCES Track (track_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- MentorAssignment — which track a mentor supports (whole event, not per round).
CREATE TABLE MentorAssignment (
  id             INT       NOT NULL AUTO_INCREMENT,
  mentor_user_id INT       NOT NULL,
  track_id       INT       NOT NULL,
  is_active      BOOLEAN   NOT NULL DEFAULT TRUE,
  -- Who assigned this and when is captured in AuditLog (action = ASSIGN_MENTOR).
  PRIMARY KEY (id),
  UNIQUE KEY uq_mentor_track (mentor_user_id, track_id),
  KEY idx_ma_track (track_id),
  CONSTRAINT fk_ma_mentor FOREIGN KEY (mentor_user_id) REFERENCES `User` (user_id),
  CONSTRAINT fk_ma_track  FOREIGN KEY (track_id)       REFERENCES Track (track_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- TEAM MANAGEMENT
-- =====================================================

-- Team is a stable identity that persists across seasons (mirrors User) —
-- season-specific facts (event, track, approval status, disqualification)
-- live on TeamEventEntry instead, one row per (team, event).
CREATE TABLE Team (
  team_id     INT          NOT NULL AUTO_INCREMENT,
  name        VARCHAR(255) NOT NULL,
  description TEXT,
  is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE TeamEventEntry (
  id                  INT          NOT NULL AUTO_INCREMENT,
  team_id             INT          NOT NULL,
  event_id            INT          NOT NULL,
  track_id            INT                   COMMENT 'NULL until assigned (self-selected at registration or drawn during SETUP)',
  status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, APPROVED, REJECTED, DISQUALIFIED',
  disqualified_reason TEXT,
  disqualified_at     DATETIME,
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_tee_team_event (team_id, event_id),
  KEY idx_tee_track (track_id),
  KEY idx_tee_status (status),
  CONSTRAINT fk_tee_team  FOREIGN KEY (team_id)  REFERENCES Team (team_id),
  CONSTRAINT fk_tee_event FOREIGN KEY (event_id) REFERENCES HackathonEvent (event_id),
  CONSTRAINT fk_tee_track FOREIGN KEY (track_id) REFERENCES Track (track_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE TeamMember (
  id          INT         NOT NULL AUTO_INCREMENT,
  team_id     INT         NOT NULL,
  user_id     INT         NOT NULL,
  member_role VARCHAR(20) NOT NULL DEFAULT 'MEMBER' COMMENT 'LEADER or MEMBER',
  joined_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_team_member (team_id, user_id),
  KEY idx_team_member_user (user_id),
  CONSTRAINT fk_tm_team FOREIGN KEY (team_id) REFERENCES Team (team_id),
  CONSTRAINT fk_tm_user FOREIGN KEY (user_id) REFERENCES `User` (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A team leader's request to re-attach the team's persistent identity to a
-- new season (creates a new TeamEventEntry on approval) — mirrors
-- ParticipationAccessRequest, scoped to a team instead of a user.
CREATE TABLE TeamRejoinRequest (
  request_id   INT          NOT NULL AUTO_INCREMENT,
  team_id      INT          NOT NULL,
  event_id     INT          NOT NULL COMMENT 'Target event for the new TeamEventEntry',
  requested_by INT          NOT NULL COMMENT 'The leader who filed the request',
  status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, APPROVED, REJECTED',
  requested_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resolved_at  DATETIME              NULL,
  resolved_by  INT                   NULL,
  PRIMARY KEY (request_id),
  KEY idx_team_rejoin_status (status, requested_at),
  KEY idx_team_rejoin_team_status (team_id, status),
  CONSTRAINT fk_team_rejoin_team
    FOREIGN KEY (team_id) REFERENCES Team (team_id)
    ON DELETE CASCADE,
  CONSTRAINT fk_team_rejoin_event FOREIGN KEY (event_id) REFERENCES HackathonEvent (event_id),
  CONSTRAINT fk_team_rejoin_requested_by
    FOREIGN KEY (requested_by) REFERENCES `User`(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A frozen, per-participant snapshot of one event's result — NOT a live
-- reference (no FK to Team; event_id is plain, used only to compare against
-- the event's current live status, never for display). Written when a
-- participant leaves/is removed from their team early (before their
-- TeamMember row is hard-deleted, otherwise that season's result would be
-- lost) or when the event completes (for whoever is still on a team then).
-- One row per (user_id, event_id), upserted on every write.
CREATE TABLE ParticipantEventHistory (
  id             INT          NOT NULL AUTO_INCREMENT,
  user_id        INT          NOT NULL,
  event_id       INT          NOT NULL COMMENT 'Not a FK — display data lives in result_json',
  snapshot_reason VARCHAR(30) NOT NULL COMMENT 'COMPLETED, LEFT_TEAM, REMOVED_BY_LEADER, REMOVED_BY_COORDINATOR',
  snapshot_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  result_json    TEXT         NOT NULL COMMENT 'Serialized TeamHistoryResponse',
  PRIMARY KEY (id),
  UNIQUE KEY uq_participant_event_history_user_event (user_id, event_id),
  CONSTRAINT fk_peh_user FOREIGN KEY (user_id) REFERENCES `User` (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- SUBMISSION
-- =====================================================

CREATE TABLE Submission (
  submission_id INT          NOT NULL AUTO_INCREMENT,
  team_id       INT          NOT NULL,
  round_id      INT          NOT NULL,
  repo_url      VARCHAR(500),
  demo_url      VARCHAR(500),
  slide_url     VARCHAR(500),
  description   TEXT,
  submitted_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  submitted_by  INT          NOT NULL,
  status        VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED' COMMENT 'DRAFT, SUBMITTED, LATE, INVALID',
  PRIMARY KEY (submission_id),
  UNIQUE KEY uq_submission_team_round (team_id, round_id),
  KEY idx_submission_round (round_id),
  CONSTRAINT fk_sub_team      FOREIGN KEY (team_id)     REFERENCES Team (team_id),
  CONSTRAINT fk_sub_round     FOREIGN KEY (round_id)    REFERENCES Round (round_id),
  CONSTRAINT fk_sub_submitted FOREIGN KEY (submitted_by) REFERENCES `User` (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- SCORING
-- =====================================================

CREATE TABLE ScoringCriteriaTemplate (
  template_id INT          NOT NULL AUTO_INCREMENT,
  name        VARCHAR(255) NOT NULL COMMENT 'e.g. Standard Hackathon Criteria',
  description TEXT,
  is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ScoringCriteria (
  criteria_id  INT           NOT NULL AUTO_INCREMENT,
  event_id     INT                    COMMENT 'NULL if criteria belongs to a template only',
  round_id     INT                    COMMENT 'Set for round-specific criteria',
  template_id  INT                    COMMENT 'Source template if inherited',
  name         VARCHAR(255)  NOT NULL COMMENT 'e.g. Innovation, Technical, UI/UX',
  description  TEXT,
  weight       DECIMAL(5,2)  NOT NULL DEFAULT 1.0,
  max_score    DECIMAL(5,2)  NOT NULL DEFAULT 10.0,
  order_number INT,
  PRIMARY KEY (criteria_id),
  KEY idx_criteria_event (event_id),
  KEY idx_criteria_round (round_id),
  CONSTRAINT fk_sc_event    FOREIGN KEY (event_id)    REFERENCES HackathonEvent (event_id),
  CONSTRAINT fk_sc_round    FOREIGN KEY (round_id)    REFERENCES Round (round_id),
  CONSTRAINT fk_sc_template FOREIGN KEY (template_id) REFERENCES ScoringCriteriaTemplate (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE Score (
  score_id      INT           NOT NULL AUTO_INCREMENT,
  submission_id INT           NOT NULL,
  judge_user_id INT           NOT NULL,
  criteria_id   INT           NOT NULL,
  value         DECIMAL(5,2)  NOT NULL,
  comment       TEXT,
  is_draft      BOOLEAN       NOT NULL DEFAULT TRUE,
  scored_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME               ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (score_id),
  UNIQUE KEY uq_score (submission_id, judge_user_id, criteria_id),
  KEY idx_score_judge (judge_user_id),
  KEY idx_score_submission (submission_id),
  CONSTRAINT fk_score_submission FOREIGN KEY (submission_id) REFERENCES Submission (submission_id),
  CONSTRAINT fk_score_judge      FOREIGN KEY (judge_user_id) REFERENCES `User` (user_id),
  CONSTRAINT fk_score_criteria   FOREIGN KEY (criteria_id)   REFERENCES ScoringCriteria (criteria_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- RESULTS & RANKING
-- =====================================================

CREATE TABLE RoundResult (
  result_id     INT           NOT NULL AUTO_INCREMENT,
  team_id       INT           NOT NULL,
  round_id      INT           NOT NULL,
  total_score   DECIMAL(7,2)  NOT NULL,
  rank_position INT           NOT NULL,
  -- "Advanced" is NOT stored: it is derived as
  --   rank_position <= Round.top_n_advance  (per track for preliminary rounds).
  -- Compute it in the service/query layer when listing who moves on.
  is_published  BOOLEAN       NOT NULL DEFAULT FALSE,
  finalized_at  DATETIME,
  finalized_by  INT,
  PRIMARY KEY (result_id),
  UNIQUE KEY uq_result_team_round (team_id, round_id),
  KEY idx_result_round_rank (round_id, rank_position),
  CONSTRAINT fk_rr_team      FOREIGN KEY (team_id)     REFERENCES Team (team_id),
  CONSTRAINT fk_rr_round     FOREIGN KEY (round_id)    REFERENCES Round (round_id),
  CONSTRAINT fk_rr_finalized FOREIGN KEY (finalized_by) REFERENCES `User` (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE Prize (
  prize_id      INT          NOT NULL AUTO_INCREMENT,
  event_id      INT          NOT NULL,
  track_id      INT                   COMMENT 'NULL for event-wide prizes',
  name          VARCHAR(255) NOT NULL COMMENT 'e.g. Champion, 1st Runner-up',
  description   TEXT,
  rank_position INT          NOT NULL,
  team_id       INT                   COMMENT 'Set after announcement',
  awarded_at    DATETIME,
  PRIMARY KEY (prize_id),
  CONSTRAINT fk_prize_event FOREIGN KEY (event_id) REFERENCES HackathonEvent (event_id),
  CONSTRAINT fk_prize_track FOREIGN KEY (track_id) REFERENCES Track (track_id),
  CONSTRAINT fk_prize_team  FOREIGN KEY (team_id)  REFERENCES Team (team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- ACCOUNT APPROVAL & TEAM INVITES
-- =====================================================

CREATE TABLE AccountApproval (
  approval_id  INT          NOT NULL AUTO_INCREMENT,
  user_id      INT          NOT NULL,
  reviewed_by  INT                   COMMENT 'Coordinator who approved/rejected',
  status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, APPROVED, REJECTED',
  note         TEXT,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_at  DATETIME,
  PRIMARY KEY (approval_id),
  KEY idx_approval_user   (user_id),
  KEY idx_approval_status (status),
  CONSTRAINT fk_approval_user     FOREIGN KEY (user_id)     REFERENCES `User` (user_id),
  CONSTRAINT fk_approval_reviewer FOREIGN KEY (reviewed_by) REFERENCES `User` (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE TeamInvite (
  invite_id        INT          NOT NULL AUTO_INCREMENT,
  team_id          INT          NOT NULL,
  event_id         INT          NOT NULL COMMENT 'Which season this invite is for — Team no longer carries a single event',
  invited_user_id  INT          NOT NULL,
  invited_by       INT          NOT NULL,
  message          TEXT,
  status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, ACCEPTED, DECLINED',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  responded_at     DATETIME,
  PRIMARY KEY (invite_id),
  UNIQUE KEY uq_invite_team_user (team_id, invited_user_id),
  KEY idx_invite_user (invited_user_id),
  KEY idx_invite_event (event_id),
  CONSTRAINT fk_invite_team    FOREIGN KEY (team_id)         REFERENCES Team (team_id),
  CONSTRAINT fk_invite_event   FOREIGN KEY (event_id)        REFERENCES HackathonEvent (event_id),
  CONSTRAINT fk_invite_invitee FOREIGN KEY (invited_user_id) REFERENCES `User` (user_id),
  CONSTRAINT fk_invite_inviter FOREIGN KEY (invited_by)      REFERENCES `User` (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE JoinRequest (
  request_id        INT          NOT NULL AUTO_INCREMENT,
  team_id           INT          NOT NULL,
  event_id          INT          NOT NULL COMMENT 'Which season this request is for — Team no longer carries a single event',
  requester_user_id INT          NOT NULL,
  message           TEXT,
  status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, ACCEPTED, DECLINED',
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  responded_at      DATETIME,
  PRIMARY KEY (request_id),
  UNIQUE KEY uq_join_team_user (team_id, requester_user_id),
  KEY idx_join_requester (requester_user_id),
  KEY idx_join_event (event_id),
  CONSTRAINT fk_join_team      FOREIGN KEY (team_id)           REFERENCES Team (team_id),
  CONSTRAINT fk_join_event     FOREIGN KEY (event_id)          REFERENCES HackathonEvent (event_id),
  CONSTRAINT fk_join_requester FOREIGN KEY (requester_user_id) REFERENCES `User` (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- EVENT REOPEN REQUESTS
-- A Coordinator cannot reopen a COMPLETED event themselves; they file a
-- request here that a SYSTEM_ADMIN approves (which performs the reopen) or
-- rejects. The service layer enforces "at most one PENDING request per event".
-- =====================================================

CREATE TABLE ReopenRequest (
  request_id   INT          NOT NULL AUTO_INCREMENT,
  event_id     INT          NOT NULL,
  requested_by INT          NOT NULL COMMENT 'Coordinator who asked to reopen',
  reason       TEXT                  COMMENT 'Optional explanation for the admin',
  status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, APPROVED, REJECTED',
  resolved_by  INT                   COMMENT 'Admin who approved/rejected',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resolved_at  DATETIME,
  PRIMARY KEY (request_id),
  KEY idx_reopen_event  (event_id),
  KEY idx_reopen_status (status),
  CONSTRAINT fk_reopen_event     FOREIGN KEY (event_id)     REFERENCES HackathonEvent (event_id),
  CONSTRAINT fk_reopen_requester FOREIGN KEY (requested_by) REFERENCES `User` (user_id),
  CONSTRAINT fk_reopen_resolver  FOREIGN KEY (resolved_by)  REFERENCES `User` (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- COMMUNICATION & AUDIT
-- =====================================================

-- Announcements composed by a Mentor (scoped to a track) or a Coordinator
-- (scoped to the whole event). Source of truth for the "sent history" views
-- and for the "From / subject / date" shown in the email-style popup.
-- Must be defined before Notification, which back-links to it.
CREATE TABLE Announcement (
  announcement_id INT          NOT NULL AUTO_INCREMENT,
  sender_user_id  INT          NOT NULL,
  sender_role     VARCHAR(20)  NOT NULL COMMENT 'MENTOR, COORDINATOR',
  scope           VARCHAR(20)  NOT NULL COMMENT 'TRACK, EVENT',
  audience        VARCHAR(20)           COMMENT 'PARTICIPANT, JUDGE, MENTOR (who the coordinator targeted)',
  event_id        INT          NOT NULL,
  track_id        INT                   COMMENT 'NULL when scope = EVENT',
  title           VARCHAR(255) NOT NULL,
  content         TEXT,
  link_url        VARCHAR(1000)         COMMENT 'Optional attachment link (Drive/Form/Repo...)',
  recipient_count INT          NOT NULL DEFAULT 0,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (announcement_id),
  KEY idx_ann_sender (sender_user_id),
  KEY idx_ann_event  (event_id),
  KEY idx_ann_track  (track_id),
  CONSTRAINT fk_ann_sender FOREIGN KEY (sender_user_id) REFERENCES `User` (user_id),
  CONSTRAINT fk_ann_event  FOREIGN KEY (event_id)       REFERENCES HackathonEvent (event_id),
  CONSTRAINT fk_ann_track  FOREIGN KEY (track_id)       REFERENCES Track (track_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE Notification (
  notification_id   INT          NOT NULL AUTO_INCREMENT,
  recipient_user_id INT          NOT NULL,
  title             VARCHAR(255) NOT NULL,
  content           TEXT,
  type              VARCHAR(50)           COMMENT 'ANNOUNCEMENT, RESULT, REMINDER, ASSIGNMENT, APPROVAL',
  announcement_id   INT                   COMMENT 'Set only for ANNOUNCEMENT notifications; links back to the source Announcement. NULL otherwise',
  is_read           BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (notification_id),
  KEY idx_notif_recipient_read (recipient_user_id, is_read),
  KEY idx_notif_announcement (announcement_id),
  CONSTRAINT fk_notif_recipient     FOREIGN KEY (recipient_user_id) REFERENCES `User` (user_id),
  CONSTRAINT fk_notif_announcement  FOREIGN KEY (announcement_id)   REFERENCES Announcement (announcement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE AuditLog (
  log_id        INT         NOT NULL AUTO_INCREMENT,
  actor_user_id INT         NOT NULL,
  action        VARCHAR(50) NOT NULL COMMENT 'CREATE_EVENT, UPDATE_SCORE, DISQUALIFY_TEAM, PUBLISH_RESULT...',
  target_type   VARCHAR(50)          COMMENT 'TEAM, SUBMISSION, SCORE, EVENT...',
  target_id     INT,
  reason        TEXT,
  metadata_json TEXT                 COMMENT 'JSON snapshot of before/after state',
  ip_address    VARCHAR(45),
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (log_id),
  KEY idx_audit_target  (target_type, target_id),
  KEY idx_audit_actor   (actor_user_id),
  KEY idx_audit_created (created_at),
  CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES `User` (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- SystemLog — platform/admin events, kept SEPARATE from AuditLog.
--   AuditLog  = competition business actions (scored, disqualified, published);
--               needs target_type/target_id, reason, before/after snapshot.
--   SystemLog = admin actions on the platform itself (user mgmt, role grants,
--               template changes, auth events). Minimal by design: the human-
--               readable context goes into `detail`, no entity FK fan-out.
-- Visibility: SystemLog is SYSTEM_ADMIN-only; AuditLog is also readable by the
-- owning event's coordinator.
CREATE TABLE SystemLog (
  log_id        INT         NOT NULL AUTO_INCREMENT,
  actor_user_id INT         NOT NULL,
  action        VARCHAR(50) NOT NULL COMMENT 'CREATE_USER, LOCK_USER, RESET_PASSWORD, GRANT_ROLE, REVOKE_ROLE, UPDATE_TEMPLATE, LOGIN_FAILED...',
  detail        TEXT                 COMMENT 'Human-readable context, e.g. "granted EVENT_COORDINATOR to user#5"',
  ip_address    VARCHAR(45),
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (log_id),
  KEY idx_syslog_actor   (actor_user_id),
  KEY idx_syslog_action  (action),
  KEY idx_syslog_created (created_at),
  CONSTRAINT fk_syslog_actor FOREIGN KEY (actor_user_id) REFERENCES `User` (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Roles are for staff / platform operators only.
-- Participants (FPT_STUDENT, EXTERNAL_STUDENT) are identified by User.user_type,
-- not by this table. Team LEADER/MEMBER distinction lives in TeamMember.member_role.
--
-- Order matters: SYSTEM_ADMIN is inserted first so it lands on role_id = 1,
-- with the competition-staff roles shifted down to 2/3/4. This makes the
-- platform owner the lowest, most stable id — handy for the bootstrap seed
-- and for code that references the admin role.
INSERT INTO Role (role_name, description) VALUES
  ('SYSTEM_ADMIN',      'Platform operator — manages all accounts, grants COORDINATOR role, owns global criteria templates, reads system logs. Not tied to any event.'),
  ('EVENT_COORDINATOR', 'SE Dept / PDP staff — runs a hackathon event: rounds, tracks, criteria, account approval, judge/mentor assignment, ranking, results.'),
  ('MENTOR',            'Faculty mentor assigned to a track'),
  ('JUDGE',             'Judge who scores submissions — INTERNAL or GUEST');

-- =====================================================
-- BOOTSTRAP ACCOUNTS
-- =====================================================
-- All seed accounts (including the SYSTEM_ADMIN and the demo coordinator)
-- live in seal_seed.sql, so there is a SINGLE place that owns user rows and
-- user_id values stay stable for every foreign key. The platform still needs
-- an admin to function (only SYSTEM_ADMIN can grant EVENT_COORDINATOR), so
-- seal_seed.sql seeds user_id 1 = admin and grants the role there.
--
-- If you run the schema WITHOUT the seed (e.g. fresh prod), create your first
-- admin manually, then grant SYSTEM_ADMIN via UserEventRole (event_id = NULL).
