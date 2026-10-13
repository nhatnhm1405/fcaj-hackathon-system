-- =====================================================
-- SEAL Hackathon Management System — USEFUL QUERIES
-- File: seal_scripts.sql
-- Chạy sau khi đã có schema + seed (seal_schema.sql, seal_seed.sql).
--
-- Cấu trúc:
--   PHẦN A — SELECT * cho toàn bộ 23 bảng (quick inspect).
--   PHẦN B — Queries nghiệp vụ hữu ích (ranking, scoring, phân công, RBL...).
--
-- Mỗi query nghiệp vụ độc lập — chạy riêng từng cái để xem kết quả.
-- =====================================================

USE seal_hackathon;


-- #####################################################
-- PHẦN A — SELECT * TOÀN BỘ BẢNG
-- #####################################################

-- 1. User & Role
SELECT 'Role' AS tbl;                    SELECT * FROM Role;
SELECT 'User' AS tbl;                    SELECT * FROM `User`;
SELECT 'PasswordResetOtp' AS tbl;        SELECT * FROM PasswordResetOtp;

-- 2. Event structure
SELECT 'HackathonEvent' AS tbl;          SELECT * FROM HackathonEvent;
SELECT 'Track' AS tbl;                   SELECT * FROM Track;
SELECT 'Round' AS tbl;                   SELECT * FROM Round;

-- 3. Role & assignments
SELECT 'UserEventRole' AS tbl;           SELECT * FROM UserEventRole;
SELECT 'JudgeAssignment' AS tbl;         SELECT * FROM JudgeAssignment;
SELECT 'MentorAssignment' AS tbl;        SELECT * FROM MentorAssignment;

-- 4. Team
SELECT 'Team' AS tbl;                     SELECT * FROM Team;
SELECT 'TeamMember' AS tbl;              SELECT * FROM TeamMember;

-- 5. Submission & scoring
SELECT 'Submission' AS tbl;              SELECT * FROM Submission;
SELECT 'ScoringCriteriaTemplate' AS tbl; SELECT * FROM ScoringCriteriaTemplate;
SELECT 'ScoringCriteria' AS tbl;         SELECT * FROM ScoringCriteria;
SELECT 'Score' AS tbl;                    SELECT * FROM Score;

-- 6. Results & prize
SELECT 'RoundResult' AS tbl;             SELECT * FROM RoundResult;
SELECT 'Prize' AS tbl;                    SELECT * FROM Prize;

-- 7. Approval & invite
SELECT 'AccountApproval' AS tbl;         SELECT * FROM AccountApproval;
SELECT 'ParticipationAccessRequest' AS tbl; SELECT * FROM ParticipationAccessRequest;
SELECT 'TeamInvite' AS tbl;              SELECT * FROM TeamInvite;
SELECT 'JoinRequest' AS tbl;             SELECT * FROM JoinRequest;
SELECT 'ReopenRequest' AS tbl;           SELECT * FROM ReopenRequest;

-- 8. Communication & audit
SELECT 'Notification' AS tbl;            SELECT * FROM Notification;
SELECT 'AuditLog' AS tbl;                 SELECT * FROM AuditLog;
SELECT 'SystemLog' AS tbl;               SELECT * FROM SystemLog;


-- #####################################################
-- PHẦN B — QUERIES NGHIỆP VỤ HỮU ÍCH
-- #####################################################

-- -----------------------------------------------------
-- B1. USER & ROLE
-- -----------------------------------------------------

-- B1.1 — Danh sách user kèm role trong từng event
SELECT u.user_id, u.full_name, u.user_type, e.name AS event, r.role_name
FROM UserEventRole uer
JOIN `User` u          ON u.user_id  = uer.user_id
JOIN Role r            ON r.role_id  = uer.role_id
LEFT JOIN HackathonEvent e ON e.event_id = uer.event_id
ORDER BY e.event_id, r.role_name, u.full_name;

-- B1.2 — User giữ NHIỀU role trong cùng 1 event (vd vừa Mentor vừa Judge)
SELECT u.full_name, e.name AS event,
       GROUP_CONCAT(r.role_name ORDER BY r.role_name SEPARATOR ', ') AS roles,
       COUNT(*) AS role_count
FROM UserEventRole uer
JOIN `User` u          ON u.user_id  = uer.user_id
JOIN Role r            ON r.role_id  = uer.role_id
JOIN HackathonEvent e  ON e.event_id = uer.event_id
GROUP BY u.user_id, e.event_id
HAVING role_count > 1;

-- B1.3 — User chưa được duyệt (pending approval)
SELECT u.user_id, u.full_name, u.email, u.user_type, u.university
FROM `User` u
WHERE u.is_approved = FALSE;


-- -----------------------------------------------------
-- B2. PHÂN CÔNG JUDGE / MENTOR
-- -----------------------------------------------------

-- B2.1 — Bảng phân công Judge: ai chấm round/track nào
SELECT e.name AS event, ro.name AS round,
       COALESCE(t.name, '(ALL TRACKS - final)') AS track,
       u.full_name AS judge, u.judge_type
FROM JudgeAssignment ja
JOIN `User` u          ON u.user_id  = ja.judge_user_id
JOIN Round ro          ON ro.round_id = ja.round_id
JOIN HackathonEvent e  ON e.event_id  = ro.event_id
LEFT JOIN Track t      ON t.track_id  = ja.track_id
WHERE ja.is_active = TRUE
ORDER BY e.event_id, ro.order_number, track, judge;

-- B2.2 — Bảng phân công Mentor: ai hỗ trợ track nào
SELECT e.name AS event, t.name AS track, u.full_name AS mentor
FROM MentorAssignment ma
JOIN `User` u          ON u.user_id  = ma.mentor_user_id
JOIN Track t           ON t.track_id = ma.track_id
JOIN HackathonEvent e  ON e.event_id = t.event_id
WHERE ma.is_active = TRUE
ORDER BY e.event_id, t.name;

-- B2.3 — CONFLICT OF INTEREST: user vừa là Judge vừa là Mentor CÙNG 1 track
--        (mentor track nào thì không nên chấm track đó)
SELECT u.full_name, t.name AS track, e.name AS event
FROM MentorAssignment ma
JOIN JudgeAssignment ja ON ja.judge_user_id = ma.mentor_user_id
                       AND ja.track_id      = ma.track_id
JOIN `User` u          ON u.user_id  = ma.mentor_user_id
JOIN Track t           ON t.track_id = ma.track_id
JOIN HackathonEvent e  ON e.event_id = t.event_id
WHERE ma.is_active = TRUE AND ja.is_active = TRUE;


-- -----------------------------------------------------
-- B3. SUBMISSION & TIẾN ĐỘ CHẤM
-- -----------------------------------------------------

-- B3.1 — Danh sách bài nộp 1 Judge cần chấm (thay @judge, @round)
--        Khớp theo phân công round + track của judge.
SET @judge = 3;   -- Thầy Binh
SET @round = 1;   -- Preliminary event 1
SELECT s.submission_id, tm.name AS team, t.name AS track,
       s.repo_url, s.demo_url, s.status
FROM JudgeAssignment ja
JOIN Round ro          ON ro.round_id = ja.round_id
JOIN Team tm           ON tm.event_id = ro.event_id
                      AND (ja.track_id IS NULL OR tm.track_id = ja.track_id)
JOIN Track t           ON t.track_id = tm.track_id
JOIN Submission s      ON s.team_id = tm.team_id AND s.round_id = ja.round_id
WHERE ja.judge_user_id = @judge AND ja.round_id = @round AND ja.is_active = TRUE
ORDER BY t.name, tm.name;

-- B3.2 — Tiến độ chấm của từng Judge: đã chấm bao nhiêu / tổng bài được giao
SELECT u.full_name AS judge, ro.name AS round,
       COUNT(DISTINCT s.submission_id) AS scored_submissions
FROM Score sc
JOIN `User` u     ON u.user_id = sc.judge_user_id
JOIN Submission s ON s.submission_id = sc.submission_id
JOIN Round ro     ON ro.round_id = s.round_id
WHERE sc.is_draft = FALSE
GROUP BY u.user_id, ro.round_id
ORDER BY ro.round_id, judge;

-- B3.3 — Team chưa nộp bài ở 1 round (thay @round)
SET @round = 1;
SELECT tm.team_id, tm.name AS team, t.name AS track
FROM Team tm
JOIN Round ro ON ro.event_id = tm.event_id AND ro.round_id = @round
JOIN Track t  ON t.track_id = tm.track_id
WHERE tm.status = 'APPROVED'
  AND NOT EXISTS (
    SELECT 1 FROM Submission s
    WHERE s.team_id = tm.team_id AND s.round_id = @round
  );


-- -----------------------------------------------------
-- B4. SCORING & RANKING
-- -----------------------------------------------------

-- B4.1 — Điểm CÓ TRỌNG SỐ của từng team theo round
--        weighted = SUM(value * weight) / SUM(weight) trung bình qua các judge
SELECT ro.name AS round, tm.name AS team,
       ROUND(SUM(sc.value * crit.weight) / NULLIF(SUM(crit.weight),0), 2) AS weighted_avg,
       COUNT(DISTINCT sc.judge_user_id) AS judge_count
FROM Score sc
JOIN Submission s     ON s.submission_id = sc.submission_id
JOIN Team tm          ON tm.team_id = s.team_id
JOIN Round ro         ON ro.round_id = s.round_id
JOIN ScoringCriteria crit ON crit.criteria_id = sc.criteria_id
WHERE sc.is_draft = FALSE
GROUP BY ro.round_id, tm.team_id
ORDER BY ro.round_id, weighted_avg DESC;

-- B4.2 — Bảng xếp hạng đã chốt (RoundResult) kèm trạng thái thăng vòng
SELECT e.name AS event, ro.name AS round, rr.rank_position AS rnk,
       tm.name AS team, t.name AS track, rr.total_score,
       CASE
         WHEN ro.top_n_advance IS NOT NULL AND rr.rank_position <= ro.top_n_advance
           THEN '✓ Advanced'
         ELSE '✗ Eliminated'
       END AS result,
       CASE WHEN rr.is_published THEN 'Published' ELSE 'Draft' END AS visibility
FROM RoundResult rr
JOIN Team tm          ON tm.team_id = rr.team_id
JOIN Track t          ON t.track_id = tm.track_id
JOIN Round ro         ON ro.round_id = rr.round_id
JOIN HackathonEvent e ON e.event_id = ro.event_id
ORDER BY e.event_id, ro.order_number, rr.rank_position;

-- B4.3 — Chi tiết điểm từng tiêu chí của 1 submission (thay @sub)
SET @sub = 1;
SELECT u.full_name AS judge, crit.name AS criteria,
       crit.weight, sc.value, sc.comment
FROM Score sc
JOIN `User` u             ON u.user_id = sc.judge_user_id
JOIN ScoringCriteria crit ON crit.criteria_id = sc.criteria_id
WHERE sc.submission_id = @sub
ORDER BY u.full_name, crit.order_number;

-- B4.4 — Top N team mỗi track ở 1 round (vd top 2 — dùng cho thăng vòng)
SET @round = 1;
SELECT round, track, team, total_score, rk
FROM (
  SELECT ro.name AS round, t.name AS track, tm.name AS team, rr.total_score,
         RANK() OVER (PARTITION BY t.track_id ORDER BY rr.total_score DESC) AS rk
  FROM RoundResult rr
  JOIN Team tm  ON tm.team_id = rr.team_id
  JOIN Track t  ON t.track_id = tm.track_id
  JOIN Round ro ON ro.round_id = rr.round_id
  WHERE rr.round_id = @round
) ranked
WHERE rk <= 2
ORDER BY track, rk;


-- -----------------------------------------------------
-- B5. PRIZE & KẾT QUẢ
-- -----------------------------------------------------

-- B5.1 — Danh sách giải thưởng đã trao (kèm team đoạt giải)
SELECT e.name AS event, COALESCE(t.name,'(Overall)') AS track,
       p.name AS prize, p.rank_position AS rnk,
       tm.name AS winner, p.awarded_at
FROM Prize p
JOIN HackathonEvent e ON e.event_id = p.event_id
LEFT JOIN Track t     ON t.track_id = p.track_id
LEFT JOIN Team tm     ON tm.team_id = p.team_id
WHERE p.team_id IS NOT NULL
ORDER BY e.event_id, track, p.rank_position;

-- B5.2 — Giải thưởng chưa trao (event đang diễn ra / sắp tới)
SELECT e.name AS event, COALESCE(t.name,'(Overall)') AS track, p.name AS prize
FROM Prize p
JOIN HackathonEvent e ON e.event_id = p.event_id
LEFT JOIN Track t     ON t.track_id = p.track_id
WHERE p.team_id IS NULL
ORDER BY e.event_id, track;


-- -----------------------------------------------------
-- B6. TEAM & THÀNH VIÊN
-- -----------------------------------------------------

-- B6.1 — Team kèm leader, số thành viên, track, event
SELECT e.name AS event, t.name AS track, tm.name AS team, tm.status,
       MAX(CASE WHEN m.member_role='LEADER' THEN u.full_name END) AS leader,
       COUNT(m.user_id) AS members
FROM Team tm
JOIN HackathonEvent e ON e.event_id = tm.event_id
JOIN Track t          ON t.track_id = tm.track_id
LEFT JOIN TeamMember m ON m.team_id = tm.team_id
LEFT JOIN `User` u     ON u.user_id = m.user_id
GROUP BY tm.team_id
ORDER BY e.event_id, t.name, tm.name;

-- B6.2 — User tham gia nhiều event (đa sự kiện)
SELECT u.full_name,
       COUNT(DISTINCT tm.event_id) AS events_joined,
       GROUP_CONCAT(DISTINCT e.name ORDER BY e.event_id SEPARATOR ' | ') AS events
FROM TeamMember m
JOIN Team tm          ON tm.team_id = m.team_id
JOIN HackathonEvent e ON e.event_id = tm.event_id
JOIN `User` u         ON u.user_id = m.user_id
GROUP BY u.user_id
HAVING events_joined > 1;


-- -----------------------------------------------------
-- B7. AUDIT & MINH BẠCH
-- -----------------------------------------------------

-- B7.1 — Lịch sử chỉnh sửa điểm (minh bạch scoring)
SELECT a.created_at, u.full_name AS actor, a.action,
       a.target_id AS score_id, a.reason, a.metadata_json
FROM AuditLog a
JOIN `User` u ON u.user_id = a.actor_user_id
WHERE a.action = 'UPDATE_SCORE'
ORDER BY a.created_at DESC;

-- B7.2 — Toàn bộ audit log theo 1 event (thay @event)
SET @event = 1;
SELECT a.created_at, u.full_name AS actor, a.action, a.target_type, a.target_id, a.reason
FROM AuditLog a
JOIN `User` u ON u.user_id = a.actor_user_id
WHERE a.metadata_json LIKE CONCAT('%"event_id":', @event, '%')
   OR (a.target_type = 'EVENT' AND a.target_id = @event)
ORDER BY a.created_at;


-- -----------------------------------------------------
-- B8. RBL — INTER-RATER RELIABILITY (dữ liệu nghiên cứu)
-- -----------------------------------------------------

-- B8.1 — Dữ liệu chấm điểm ĐÃ ẨN DANH (export cho phân tích ICC/Krippendorff)
--        Judge ID thay bằng mã ẩn danh, kèm judge_type.
SELECT s.round_id, tm.track_id, s.team_id, sc.submission_id,
       CONCAT('J', LPAD(sc.judge_user_id, 3, '0')) AS anon_judge_id,
       u.judge_type,
       sc.criteria_id, sc.value AS score, sc.scored_at AS ts
FROM Score sc
JOIN Submission s ON s.submission_id = sc.submission_id
JOIN Team tm      ON tm.team_id = s.team_id
JOIN `User` u     ON u.user_id = sc.judge_user_id
LEFT JOIN JudgeAssignment ja
       ON ja.judge_user_id = sc.judge_user_id
      AND ja.round_id      = s.round_id
WHERE sc.is_draft = FALSE
ORDER BY s.round_id, sc.submission_id, anon_judge_id, sc.criteria_id;

-- B8.2 — Phương sai điểm giữa các Judge cho cùng 1 submission + tiêu chí
--        (submission có >= 2 judge mới tính được độ lệch)
SELECT s.submission_id, tm.name AS team, crit.name AS criteria,
       COUNT(*) AS num_judges,
       ROUND(MIN(sc.value),2) AS min_score,
       ROUND(MAX(sc.value),2) AS max_score,
       ROUND(MAX(sc.value)-MIN(sc.value),2) AS score_range,
       ROUND(STDDEV_SAMP(sc.value),3) AS std_dev
FROM Score sc
JOIN Submission s         ON s.submission_id = sc.submission_id
JOIN Team tm              ON tm.team_id = s.team_id
JOIN ScoringCriteria crit ON crit.criteria_id = sc.criteria_id
WHERE sc.is_draft = FALSE
GROUP BY sc.submission_id, sc.criteria_id
HAVING num_judges >= 2
ORDER BY score_range DESC;

-- B8.3 — So sánh độ nhất quán theo loại Judge (Internal vs Guest)
SELECT u.judge_type,
       COUNT(*) AS num_scores,
       ROUND(AVG(sc.value),2) AS avg_score,
       ROUND(STDDEV_SAMP(sc.value),3) AS std_dev
FROM Score sc
JOIN Submission s ON s.submission_id = sc.submission_id
JOIN `User` u     ON u.user_id = sc.judge_user_id
JOIN JudgeAssignment ja
      ON ja.judge_user_id = sc.judge_user_id
     AND ja.round_id      = s.round_id
WHERE sc.is_draft = FALSE
GROUP BY u.judge_type;


-- -----------------------------------------------------
-- B9. NOTIFICATION
-- -----------------------------------------------------

-- B9.1 — Thông báo chưa đọc của 1 user (thay @user)
SET @user = 5;
SELECT n.notification_id, n.title, n.content, n.type, n.created_at
FROM Notification n
WHERE n.recipient_user_id = @user AND n.is_read = FALSE
ORDER BY n.created_at DESC;
