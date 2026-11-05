-- SEAL/FCAJ: inventory ONLY. No DDL, DML, seeds, routines or dynamic SQL.
-- Open in MySQL Workbench with an authorized read-only connection.
-- Target: seal_hackathon on the local server confirmed by the DB owner.
-- Do not run seal_schema.sql for this task: that file drops the database.
-- Sections B/C assume the core tables exist; inspect section A first.
-- Outputs contain metadata and aggregate counts, not passwords/OTP/PII rows.
-- Do not share unredacted Workbench connection settings/screenshots.

USE seal_hackathon;
START TRANSACTION READ ONLY;

-- A1. Server settings. Client version is NOT server version.
SELECT VERSION() AS server_version,
       DATABASE() AS database_name,
       @@lower_case_table_names AS lower_case_table_names,
       @@global.time_zone AS global_timezone,
       @@session.time_zone AS session_timezone,
       @@sql_mode AS session_sql_mode;

-- A2. All tables; TABLE_ROWS is an ESTIMATE for InnoDB, not an exact count.
SELECT TABLE_NAME, TABLE_TYPE, ENGINE, TABLE_COLLATION,
       TABLE_ROWS AS estimated_rows,
       DATA_LENGTH, INDEX_LENGTH
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME;

-- A3. Compare runtime table names with the 31 current JPA entities.
-- Extra tables (e.g. migration history) are findings, not automatically errors.
WITH expected AS (
    SELECT 'Announcement' AS table_name UNION ALL SELECT 'AuditLog'
    UNION ALL SELECT 'HackathonEvent' UNION ALL SELECT 'JoinRequest'
    UNION ALL SELECT 'JudgeAssignment' UNION ALL SELECT 'MentorAssignment'
    UNION ALL SELECT 'MentorSupportRequest' UNION ALL SELECT 'Notification'
    UNION ALL SELECT 'ParticipantEventHistory' UNION ALL SELECT 'ParticipationAccessRequest'
    UNION ALL SELECT 'PasswordResetOtp' UNION ALL SELECT 'Prize'
    UNION ALL SELECT 'ReopenRequest' UNION ALL SELECT 'Role'
    UNION ALL SELECT 'Round' UNION ALL SELECT 'RoundResult'
    UNION ALL SELECT 'RoundTimer' UNION ALL SELECT 'RoundTimerNotice'
    UNION ALL SELECT 'Score' UNION ALL SELECT 'ScoringCriteria'
    UNION ALL SELECT 'ScoringCriteriaTemplate' UNION ALL SELECT 'Submission'
    UNION ALL SELECT 'SystemLog' UNION ALL SELECT 'Team'
    UNION ALL SELECT 'TeamEventEntry' UNION ALL SELECT 'TeamInvite'
    UNION ALL SELECT 'TeamMember' UNION ALL SELECT 'TeamRejoinRequest'
    UNION ALL SELECT 'Track' UNION ALL SELECT 'User'
    UNION ALL SELECT 'UserEventRole'
)
SELECT e.table_name AS expected_name, t.TABLE_NAME AS actual_name,
       CASE WHEN t.TABLE_NAME IS NULL THEN 'MISSING'
            WHEN BINARY t.TABLE_NAME <> BINARY e.table_name THEN 'CASE_DIFFERENCE'
            ELSE 'PRESENT' END AS finding
FROM expected e
LEFT JOIN information_schema.TABLES t
       ON t.TABLE_SCHEMA = DATABASE() AND LOWER(t.TABLE_NAME) = LOWER(e.table_name)
UNION ALL
SELECT NULL, t.TABLE_NAME, 'EXTRA_TABLE'
FROM information_schema.TABLES t
WHERE t.TABLE_SCHEMA = DATABASE()
  AND NOT EXISTS (SELECT 1 FROM expected e WHERE LOWER(e.table_name) = LOWER(t.TABLE_NAME));

-- A4. Columns/defaults: no user data is selected.
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME, ORDINAL_POSITION;

-- A5. Foreign keys and delete/update actions.
SELECT k.TABLE_NAME, k.CONSTRAINT_NAME, k.COLUMN_NAME,
       k.REFERENCED_TABLE_NAME, k.REFERENCED_COLUMN_NAME,
       r.UPDATE_RULE, r.DELETE_RULE
FROM information_schema.KEY_COLUMN_USAGE k
JOIN information_schema.REFERENTIAL_CONSTRAINTS r
  ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA
 AND r.TABLE_NAME = k.TABLE_NAME AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
WHERE k.CONSTRAINT_SCHEMA = DATABASE() AND k.REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY k.TABLE_NAME, k.CONSTRAINT_NAME, k.ORDINAL_POSITION;

-- A6. Index column order and uniqueness matter, not only the index names.
SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

-- B1. Exact counts for core tables, unlike information_schema.TABLE_ROWS.
SELECT 'User' AS table_name, COUNT(*) AS exact_rows FROM `User`
UNION ALL SELECT 'Team', COUNT(*) FROM Team
UNION ALL SELECT 'TeamMember', COUNT(*) FROM TeamMember
UNION ALL SELECT 'TeamEventEntry', COUNT(*) FROM TeamEventEntry
UNION ALL SELECT 'HackathonEvent', COUNT(*) FROM HackathonEvent
UNION ALL SELECT 'Track', COUNT(*) FROM Track
UNION ALL SELECT 'Round', COUNT(*) FROM `Round`
UNION ALL SELECT 'Submission', COUNT(*) FROM Submission
UNION ALL SELECT 'Score', COUNT(*) FROM Score
UNION ALL SELECT 'RoundResult', COUNT(*) FROM RoundResult;

-- B2. Duplicate business keys. All counts should normally be zero.
SELECT 'duplicate_team_event' AS check_name, COUNT(*) AS duplicate_groups
FROM (SELECT team_id, event_id FROM TeamEventEntry
      GROUP BY team_id, event_id HAVING COUNT(*) > 1) d
UNION ALL
SELECT 'duplicate_team_round', COUNT(*)
FROM (SELECT team_id, round_id FROM Submission
      GROUP BY team_id, round_id HAVING COUNT(*) > 1) d
UNION ALL
SELECT 'duplicate_score_key', COUNT(*)
FROM (SELECT submission_id, judge_user_id, criteria_id FROM Score
      GROUP BY submission_id, judge_user_id, criteria_id HAVING COUNT(*) > 1) d;

-- B3. Cross-event consistency and orphan relations. Do not repair automatically.
SELECT 'entry_track_event_mismatch' AS check_name, COUNT(*) AS issue_rows
FROM TeamEventEntry te JOIN Track tr ON tr.track_id = te.track_id
WHERE te.event_id <> tr.event_id
UNION ALL
SELECT 'submission_missing_team_event_entry', COUNT(*)
FROM Submission s JOIN `Round` r ON r.round_id = s.round_id
LEFT JOIN TeamEventEntry te ON te.team_id = s.team_id AND te.event_id = r.event_id
WHERE te.id IS NULL
UNION ALL
SELECT 'submission_missing_team_or_round', COUNT(*)
FROM Submission s LEFT JOIN Team t ON t.team_id = s.team_id
LEFT JOIN `Round` r ON r.round_id = s.round_id
WHERE t.team_id IS NULL OR r.round_id IS NULL
UNION ALL
SELECT 'score_missing_submission_judge_or_criteria', COUNT(*)
FROM Score sc LEFT JOIN Submission s ON s.submission_id = sc.submission_id
LEFT JOIN `User` u ON u.user_id = sc.judge_user_id
LEFT JOIN ScoringCriteria c ON c.criteria_id = sc.criteria_id
WHERE s.submission_id IS NULL OR u.user_id IS NULL OR c.criteria_id IS NULL
UNION ALL
SELECT 'score_criteria_round_mismatch', COUNT(*)
FROM Score sc JOIN Submission s ON s.submission_id = sc.submission_id
JOIN ScoringCriteria c ON c.criteria_id = sc.criteria_id
WHERE c.round_id IS NULL OR c.round_id <> s.round_id;

-- C1. File reference categories: no avatar URL or problem key is printed.
SELECT CASE
         WHEN avatar_url IS NULL OR TRIM(avatar_url) = '' THEN 'EMPTY'
         WHEN avatar_url LIKE '/uploads/avatars/%' THEN 'LOCAL_HOSTED'
         WHEN avatar_url LIKE 'https://%' OR avatar_url LIKE 'http://%' THEN 'EXTERNAL_URL_OR_MIGRATED_URL_REVIEW'
         ELSE 'OTHER_REVIEW'
       END AS avatar_category, COUNT(*) AS row_count
FROM `User`
GROUP BY avatar_category;

SELECT COUNT(*) AS track_count,
       SUM(CASE WHEN problem_storage_key IS NOT NULL THEN 1 ELSE 0 END) AS has_reference,
       SUM(CASE WHEN problem_released = TRUE THEN 1 ELSE 0 END) AS released_count,
       SUM(CASE WHEN problem_released = TRUE AND problem_storage_key IS NULL THEN 1 ELSE 0 END)
           AS released_without_reference,
       SUM(COALESCE(problem_file_size, 0)) AS referenced_problem_bytes
FROM Track;

SELECT COUNT(*) AS submission_count,
       SUM(CASE WHEN repo_url IS NOT NULL AND TRIM(repo_url) <> '' THEN 1 ELSE 0 END) AS repo_links,
       SUM(CASE WHEN demo_url IS NOT NULL AND TRIM(demo_url) <> '' THEN 1 ELSE 0 END) AS demo_links,
       SUM(CASE WHEN slide_url IS NOT NULL AND TRIM(slide_url) <> '' THEN 1 ELSE 0 END) AS slide_links
FROM Submission;

COMMIT;
-- This inventory cannot prove that referenced files exist, are safe or accessible.
-- Those require a separately authorized filesystem/S3 manifest and API tests.
