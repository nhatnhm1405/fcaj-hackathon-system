# Entity schema inventory — offline MySQL 8.4 export

Source revision: `f42a168d9cb0613a43801edca632a2de64837099` plus current working tree.

Hibernate: `7.2.12.Final`. Dialect: MySQL 8.4; physical naming: StandardImpl. No connection, Spring startup, DB data or migration execution.

SQL file is a reference for review, not an approved V1. Charset/collation and lower_case_table_names require a real DB check. Java builder defaults and lifecycle callbacks are not SQL defaults.

| Table | Columns | PK | FKs | Unique keys | Declared indexes |
|---|---:|---|---:|---:|---:|
| Announcement | 12 | announcement_id | 3 | 0 | 0 |
| AuditLog | 9 | log_id | 1 | 0 | 0 |
| HackathonEvent | 13 | event_id | 0 | 0 | 0 |
| JoinRequest | 8 | request_id | 2 | 1 | 0 |
| JudgeAssignment | 5 | id | 3 | 1 | 0 |
| MentorAssignment | 4 | id | 2 | 1 | 0 |
| MentorSupportRequest | 10 | request_id | 4 | 0 | 0 |
| Notification | 11 | notification_id | 3 | 0 | 0 |
| ParticipantEventHistory | 6 | id | 1 | 1 | 0 |
| ParticipationAccessRequest | 7 | request_id | 1 | 0 | 0 |
| PasswordResetOtp | 9 | id | 1 | 1 | 0 |
| Prize | 8 | prize_id | 3 | 0 | 0 |
| ReopenRequest | 8 | request_id | 3 | 0 | 0 |
| Role | 3 | role_id | 0 | 1 | 0 |
| Round | 10 | round_id | 1 | 1 | 0 |
| RoundResult | 8 | result_id | 3 | 1 | 0 |
| RoundTimer | 12 | timer_id | 1 | 1 | 0 |
| RoundTimerNotice | 5 | id | 0 | 1 | 0 |
| Score | 9 | score_id | 3 | 1 | 0 |
| ScoringCriteria | 9 | criteria_id | 3 | 0 | 0 |
| ScoringCriteriaTemplate | 5 | template_id | 0 | 0 | 0 |
| Submission | 10 | submission_id | 3 | 1 | 0 |
| SystemLog | 5 | log_id | 1 | 0 | 0 |
| Team | 5 | team_id | 0 | 0 | 0 |
| TeamEventEntry | 8 | id | 3 | 1 | 0 |
| TeamInvite | 9 | invite_id | 3 | 1 | 0 |
| TeamMember | 5 | id | 2 | 1 | 0 |
| TeamRejoinRequest | 8 | request_id | 2 | 0 | 0 |
| Track | 13 | track_id | 1 | 1 | 0 |
| User | 15 | user_id | 0 | 1 | 0 |
| UserEventRole | 4 | id | 2 | 1 | 0 |

Declared indexes exclude MySQL's automatic FK indexes. Nullable columns in MySQL unique keys permit multiple NULL entries.

## Announcement

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| announcement_id | integer | false | true | — |
| event_id | integer | false | false | — |
| recipient_count | integer | false | false | — |
| sender_user_id | integer | false | false | — |
| track_id | integer | true | false | — |
| created_at | datetime(6) | false | false | — |
| audience | varchar(20) | true | false | — |
| scope | varchar(20) | false | false | — |
| sender_role | varchar(20) | false | false | — |
| link_url | varchar(1000) | true | false | — |
| content | TEXT | true | false | — |
| title | varchar(255) | false | false | — |

FK `FKkp485kgr1nixubc12ibr9i059`: event_id → HackathonEvent(event_id). Delete action: default (NO ACTION).

FK `FKj0k10nwdi8xpedni6du4m2rrx`: sender_user_id → User(user_id). Delete action: default (NO ACTION).

FK `FK3mtec5l38b5g7uw09his56yyj`: track_id → Track(track_id). Delete action: default (NO ACTION).

## AuditLog

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| actor_user_id | integer | false | false | — |
| log_id | integer | false | true | — |
| target_id | integer | true | false | — |
| created_at | datetime(6) | false | false | — |
| ip_address | varchar(45) | true | false | — |
| action | varchar(50) | false | false | — |
| target_type | varchar(50) | true | false | — |
| metadata_json | TEXT | true | false | — |
| reason | TEXT | true | false | — |

FK `FKi7uor6rk4v34axo46thhsx10w`: actor_user_id → User(user_id). Delete action: default (NO ACTION).

## HackathonEvent

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| event_id | integer | false | true | — |
| year | integer | false | false | — |
| created_at | datetime(6) | false | false | — |
| end_date | datetime(6) | false | false | — |
| registration_end | datetime(6) | true | false | — |
| registration_start | datetime(6) | true | false | — |
| start_date | datetime(6) | false | false | — |
| season | varchar(20) | false | false | — |
| status | varchar(20) | false | false | — |
| track_selection_mode | varchar(20) | false | false | — |
| topic | varchar(500) | true | false | — |
| description | TEXT | true | false | — |
| name | varchar(255) | false | false | — |

## JoinRequest

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| event_id | integer | false | false | — |
| request_id | integer | false | true | — |
| requester_user_id | integer | false | false | — |
| team_id | integer | false | false | — |
| created_at | datetime(6) | false | false | — |
| responded_at | datetime(6) | true | false | — |
| status | varchar(20) | false | false | — |
| message | TEXT | true | false | — |

Unique `uq_join_team_user`: team_id, requester_user_id.

FK `FKs42l2j040sjh2a3k9joqodw6a`: requester_user_id → User(user_id). Delete action: default (NO ACTION).

FK `FKtmp21ie4q8uojv33ix1vbagpp`: team_id → Team(team_id). Delete action: default (NO ACTION).

## JudgeAssignment

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| id | integer | false | true | — |
| is_active | bit | false | false | — |
| judge_user_id | integer | false | false | — |
| round_id | integer | false | false | — |
| track_id | integer | true | false | — |

Unique `uq_judge_round_track`: judge_user_id, round_id, track_id.

FK `FKm268eq8al1se053sjsa7auqy7`: judge_user_id → User(user_id). Delete action: default (NO ACTION).

FK `FK6bi8qe9mhtcx5b0jws0tdkobi`: round_id → Round(round_id). Delete action: default (NO ACTION).

FK `FKrf86e6sjwu6oqs0nrd7lw6liv`: track_id → Track(track_id). Delete action: default (NO ACTION).

## MentorAssignment

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| id | integer | false | true | — |
| is_active | bit | false | false | — |
| mentor_user_id | integer | false | false | — |
| track_id | integer | false | false | — |

Unique `uq_mentor_track`: mentor_user_id, track_id.

FK `FKq9sd0su4lj1969bt7ogpvem5b`: mentor_user_id → User(user_id). Delete action: default (NO ACTION).

FK `FKkiwcdcu71rnos0jctlgo9uy9e`: track_id → Track(track_id). Delete action: default (NO ACTION).

## MentorSupportRequest

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| request_id | integer | false | true | — |
| requester_user_id | integer | false | false | — |
| resolved_by_user_id | integer | true | false | — |
| team_id | integer | false | false | — |
| track_id | integer | false | false | — |
| created_at | datetime(6) | false | false | — |
| resolved_at | datetime(6) | true | false | — |
| category | varchar(20) | false | false | — |
| status | varchar(20) | false | false | — |
| description | TEXT | true | false | — |

FK `FK7el1ecfk3g260ji7o7kru5q4y`: requester_user_id → User(user_id). Delete action: default (NO ACTION).

FK `FK1l6t2rhp5vanxti1vvpb5ar1g`: resolved_by_user_id → User(user_id). Delete action: default (NO ACTION).

FK `FKff40y7exvt6hgt7vhj9qdbvoj`: team_id → Team(team_id). Delete action: default (NO ACTION).

FK `FKjky434awiq0nu43annqe3xhxh`: track_id → Track(track_id). Delete action: default (NO ACTION).

## Notification

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| announcement_id | integer | true | false | — |
| is_read | bit | false | false | — |
| notification_id | integer | false | true | — |
| recipient_user_id | integer | false | false | — |
| sender_user_id | integer | true | false | — |
| created_at | datetime(6) | false | false | — |
| type | varchar(50) | true | false | — |
| sender_role | varchar(100) | true | false | — |
| content | TEXT | true | false | — |
| scope_label | varchar(255) | true | false | — |
| title | varchar(255) | false | false | — |

FK `FK7sas2p5j2mb5gisnegngnpjb8`: announcement_id → Announcement(announcement_id). Delete action: default (NO ACTION).

FK `FKcvnonqg7yg3mrlkydfvjvt3di`: recipient_user_id → User(user_id). Delete action: default (NO ACTION).

FK `FK20te7jl42tf6w1abcffpw153t`: sender_user_id → User(user_id). Delete action: default (NO ACTION).

## ParticipantEventHistory

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| event_id | integer | false | false | — |
| id | integer | false | true | — |
| user_id | integer | false | false | — |
| snapshot_at | datetime(6) | false | false | — |
| snapshot_reason | varchar(30) | false | false | — |
| result_json | TEXT | false | false | — |

Unique `uq_participant_event_history_user_event`: user_id, event_id.

FK `FKariy39l7cod4nf3hlxh0d20ja`: user_id → User(user_id). Delete action: default (NO ACTION).

## ParticipationAccessRequest

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| request_id | integer | false | true | — |
| resolved_by | integer | true | false | — |
| user_id | integer | false | false | — |
| requested_at | datetime(6) | false | false | — |
| resolved_at | datetime(6) | true | false | — |
| status | varchar(20) | false | false | — |
| email | varchar(255) | false | false | — |

FK `FK9kno78sch4goccowagto4aygt`: user_id → User(user_id). Delete action: CASCADE.

## PasswordResetOtp

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| attempt_count | integer | false | false | — |
| user_id | integer | false | false | — |
| created_at | datetime(6) | false | false | — |
| expires_at | datetime(6) | false | false | — |
| id | bigint | false | true | — |
| used_at | datetime(6) | true | false | — |
| verified_at | datetime(6) | true | false | — |
| otp_hash | varchar(255) | false | false | — |
| reset_token_hash | varchar(255) | true | false | — |

Unique `UKst11t2d3pha8h90na5bytbc48`: reset_token_hash.

FK `FKpiqeucp8x5mrnuqs0c3eohvpv`: user_id → User(user_id). Delete action: default (NO ACTION).

## Prize

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| event_id | integer | false | false | — |
| prize_id | integer | false | true | — |
| rank_position | integer | false | false | — |
| team_id | integer | true | false | — |
| track_id | integer | true | false | — |
| awarded_at | datetime(6) | true | false | — |
| description | TEXT | true | false | — |
| name | varchar(255) | false | false | — |

FK `FK93wg612ms7p9hd8lp1ahay301`: event_id → HackathonEvent(event_id). Delete action: default (NO ACTION).

FK `FK6iygdf9934cr4mn5dqanr6f8s`: team_id → Team(team_id). Delete action: default (NO ACTION).

FK `FK913rn0qcx0q38dtm72mri45fn`: track_id → Track(track_id). Delete action: default (NO ACTION).

## ReopenRequest

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| event_id | integer | false | false | — |
| request_id | integer | false | true | — |
| requested_by | integer | false | false | — |
| resolved_by | integer | true | false | — |
| created_at | datetime(6) | false | false | — |
| resolved_at | datetime(6) | true | false | — |
| status | varchar(20) | false | false | — |
| reason | TEXT | true | false | — |

FK `FKio550gla34778eve8tkiuletp`: event_id → HackathonEvent(event_id). Delete action: default (NO ACTION).

FK `FKjy1uwt40cor4e96afd4ks7h2q`: requested_by → User(user_id). Delete action: default (NO ACTION).

FK `FK165du6lxgyrdya321e1um8tuh`: resolved_by → User(user_id). Delete action: default (NO ACTION).

## Role

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| role_id | integer | false | true | — |
| role_name | varchar(50) | false | false | — |
| description | varchar(255) | true | false | — |

Unique `UKltwrim5wenkon6cb48qt459ax`: role_name.

## Round

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| event_id | integer | false | false | — |
| is_final | bit | false | false | — |
| order_number | integer | false | false | — |
| round_id | integer | false | true | — |
| top_n_advance | integer | true | false | — |
| end_time | datetime(6) | false | false | — |
| start_time | datetime(6) | false | false | — |
| submission_deadline | datetime(6) | false | false | — |
| status | varchar(20) | false | false | — |
| name | varchar(255) | false | false | — |

Unique `uq_round_event_order`: event_id, order_number.

FK `FKdgf3751t8wfh3hkmltyjevhc8`: event_id → HackathonEvent(event_id). Delete action: default (NO ACTION).

## RoundResult

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| finalized_by | integer | true | false | — |
| is_published | bit | false | false | — |
| rank_position | integer | false | false | — |
| result_id | integer | false | true | — |
| round_id | integer | false | false | — |
| team_id | integer | false | false | — |
| total_score | decimal(7,2) | false | false | — |
| finalized_at | datetime(6) | true | false | — |

Unique `uq_result_team_round`: team_id, round_id.

FK `FKmd7uuu6ixvknevm39kfkx2u6f`: finalized_by → User(user_id). Delete action: default (NO ACTION).

FK `FKohqls7rnxs9x5msrl3vq76pq2`: round_id → Round(round_id). Delete action: default (NO ACTION).

FK `FK1eejyb5tvcx442hha705jd0w4`: team_id → Team(team_id). Delete action: default (NO ACTION).

## RoundTimer

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| duration_seconds | integer | true | false | — |
| notify_at_half | bit | false | false | — |
| remaining_at_pause | integer | true | false | — |
| round_id | integer | false | false | — |
| timer_id | integer | false | true | — |
| ends_at | datetime(6) | true | false | — |
| paused_at | datetime(6) | true | false | — |
| started_at | datetime(6) | true | false | — |
| updated_at | datetime(6) | true | false | — |
| phase | varchar(20) | false | false | — |
| status | varchar(20) | false | false | — |
| milestone_minutes | varchar(100) | false | false | — |

Unique `uq_roundtimer_round_phase`: round_id, phase.

FK `FKm36f2fvhsgpap07wpb522ym15`: round_id → Round(round_id). Delete action: CASCADE.

## RoundTimerNotice

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| id | integer | false | true | — |
| round_id | integer | false | false | — |
| created_at | datetime(6) | false | false | — |
| phase | varchar(20) | false | false | — |
| milestone_key | varchar(30) | false | false | — |

Unique `uq_timer_notice`: round_id, phase, milestone_key.

## Score

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| criteria_id | integer | false | false | — |
| is_draft | bit | false | false | — |
| judge_user_id | integer | false | false | — |
| score_id | integer | false | true | — |
| submission_id | integer | false | false | — |
| value | decimal(5,2) | false | false | — |
| scored_at | datetime(6) | false | false | — |
| updated_at | datetime(6) | true | false | — |
| comment | TEXT | true | false | — |

Unique `uq_score`: submission_id, judge_user_id, criteria_id.

FK `FK7kp7i8c97684tv6jbhjqks48o`: criteria_id → ScoringCriteria(criteria_id). Delete action: default (NO ACTION).

FK `FKbxxbncqgno1lcu91xqo7n7csk`: judge_user_id → User(user_id). Delete action: default (NO ACTION).

FK `FKg47s9rijyf8ujs2tloei056p8`: submission_id → Submission(submission_id). Delete action: default (NO ACTION).

## ScoringCriteria

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| criteria_id | integer | false | true | — |
| event_id | integer | true | false | — |
| max_score | decimal(5,2) | false | false | — |
| order_number | integer | true | false | — |
| round_id | integer | true | false | — |
| template_id | integer | true | false | — |
| weight | decimal(5,2) | false | false | — |
| description | TEXT | true | false | — |
| name | varchar(255) | false | false | — |

FK `FK3qsf5n0rfejc9so1cks80f0fy`: event_id → HackathonEvent(event_id). Delete action: default (NO ACTION).

FK `FK7jy31rdkfb6cvcslgr0ga0835`: round_id → Round(round_id). Delete action: default (NO ACTION).

FK `FKqveh4955pei2y7qcqp0rsxruf`: template_id → ScoringCriteriaTemplate(template_id). Delete action: default (NO ACTION).

## ScoringCriteriaTemplate

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| is_default | bit | false | false | — |
| template_id | integer | false | true | — |
| created_at | datetime(6) | false | false | — |
| description | TEXT | true | false | — |
| name | varchar(255) | false | false | — |

## Submission

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| round_id | integer | false | false | — |
| submission_id | integer | false | true | — |
| submitted_by | integer | false | false | — |
| team_id | integer | false | false | — |
| submitted_at | datetime(6) | false | false | — |
| status | varchar(20) | false | false | — |
| demo_url | varchar(500) | true | false | — |
| repo_url | varchar(500) | true | false | — |
| slide_url | varchar(500) | true | false | — |
| description | TEXT | true | false | — |

Unique `uq_submission_team_round`: team_id, round_id.

FK `FKqrruus73tf74hnri4387i6q2p`: round_id → Round(round_id). Delete action: default (NO ACTION).

FK `FK6qe3kyog6fe83efmkh9hps6xt`: submitted_by → User(user_id). Delete action: default (NO ACTION).

FK `FK5kbfk4gxt8fers03qw0o7akbe`: team_id → Team(team_id). Delete action: default (NO ACTION).

## SystemLog

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| actor_user_id | integer | false | false | — |
| log_id | integer | false | true | — |
| created_at | datetime(6) | false | false | — |
| action | varchar(50) | false | false | — |
| detail | TEXT | true | false | — |

FK `FKe0wtoxt9slsgqc5i7m8quveyo`: actor_user_id → User(user_id). Delete action: default (NO ACTION).

## Team

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| is_active | bit | false | false | — |
| team_id | integer | false | true | — |
| created_at | datetime(6) | false | false | — |
| description | TEXT | true | false | — |
| name | varchar(255) | false | false | — |

## TeamEventEntry

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| event_id | integer | false | false | — |
| id | integer | false | true | — |
| team_id | integer | false | false | — |
| track_id | integer | true | false | — |
| created_at | datetime(6) | false | false | — |
| disqualified_at | datetime(6) | true | false | — |
| status | varchar(20) | false | false | — |
| disqualified_reason | TEXT | true | false | — |

Unique `uq_tee_team_event`: team_id, event_id.

FK `FKac2o5ovxqd9clht8tjgrjgft6`: event_id → HackathonEvent(event_id). Delete action: default (NO ACTION).

FK `FKgq997g7yajhpw0uo2ceiv566o`: team_id → Team(team_id). Delete action: default (NO ACTION).

FK `FKl4sllugxk9sh1oqdrgj1aeihw`: track_id → Track(track_id). Delete action: default (NO ACTION).

## TeamInvite

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| event_id | integer | false | false | — |
| invite_id | integer | false | true | — |
| invited_by | integer | false | false | — |
| invited_user_id | integer | false | false | — |
| team_id | integer | false | false | — |
| created_at | datetime(6) | false | false | — |
| responded_at | datetime(6) | true | false | — |
| status | varchar(20) | false | false | — |
| message | TEXT | true | false | — |

Unique `uq_invite_team_user`: team_id, invited_user_id.

FK `FK5uuvddwplxs6v9ry0nkvgle0y`: invited_by → User(user_id). Delete action: default (NO ACTION).

FK `FKspj1xmhtn0km9dqs4qhuob5kj`: invited_user_id → User(user_id). Delete action: default (NO ACTION).

FK `FKjgmru8jywyf8kkcnw5xcwysty`: team_id → Team(team_id). Delete action: default (NO ACTION).

## TeamMember

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| id | integer | false | true | — |
| team_id | integer | false | false | — |
| user_id | integer | false | false | — |
| joined_at | datetime(6) | false | false | — |
| member_role | varchar(20) | false | false | — |

Unique `uq_team_member_team_user`: team_id, user_id.

FK `FKc020iwyqywwr93x2wrkfyfhko`: team_id → Team(team_id). Delete action: default (NO ACTION).

FK `FK82rcm62g6rh3gloopd8p2ta5m`: user_id → User(user_id). Delete action: default (NO ACTION).

## TeamRejoinRequest

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| event_id | integer | false | false | — |
| request_id | integer | false | true | — |
| requested_by | integer | false | false | — |
| resolved_by | integer | true | false | — |
| team_id | integer | false | false | — |
| requested_at | datetime(6) | false | false | — |
| resolved_at | datetime(6) | true | false | — |
| status | varchar(20) | false | false | — |

FK `FKeuvldwv8du0f3yarntm1v6t2f`: requested_by → User(user_id). Delete action: default (NO ACTION).

FK `FK78yskuuek1ri8ruj7a3vmxyio`: team_id → Team(team_id). Delete action: CASCADE.

## Track

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| capacity | integer | true | false | — |
| event_id | integer | false | false | — |
| problem_released | bit | false | false | — |
| track_id | integer | false | true | — |
| created_at | datetime(6) | false | false | — |
| problem_file_size | bigint | true | false | — |
| problem_released_at | datetime(6) | true | false | — |
| problem_uploaded_at | datetime(6) | true | false | — |
| problem_content_type | varchar(100) | true | false | — |
| problem_storage_key | varchar(500) | true | false | — |
| description | TEXT | true | false | — |
| name | varchar(255) | false | false | — |
| problem_file_name | varchar(255) | true | false | — |

Unique `uq_track_event_name`: event_id, name.

FK `FKge2tytdcmioo46uvot1bu0yrq`: event_id → HackathonEvent(event_id). Delete action: default (NO ACTION).

## User

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| is_active | bit | false | false | — |
| is_approved | bit | false | false | — |
| user_id | integer | false | true | — |
| created_at | datetime(6) | false | false | — |
| expired_at | datetime(6) | true | false | — |
| judge_type | varchar(20) | true | false | — |
| provider | varchar(20) | false | false | — |
| user_type | varchar(20) | false | false | — |
| student_id | varchar(50) | true | false | — |
| avatar_url | varchar(500) | true | false | — |
| email | varchar(255) | false | false | — |
| full_name | varchar(255) | false | false | — |
| password_hash | varchar(255) | true | false | — |
| provider_id | varchar(255) | true | false | — |
| university | varchar(255) | true | false | — |

Unique `UKt7gueymdut91qb606y2pyjpq0`: email.

## UserEventRole

| Column | SQL type | Nullable | Identity | SQL default |
|---|---|---|---|---|
| event_id | integer | true | false | — |
| id | integer | false | true | — |
| role_id | integer | false | false | — |
| user_id | integer | false | false | — |

Unique `uq_user_role_event`: user_id, role_id, event_id.

FK `FK8jbgdgswj5yag5eyi5reb2rwa`: role_id → Role(role_id). Delete action: default (NO ACTION).

FK `FK4fvvy2709kduvt6oby6ih9ywm`: user_id → User(user_id). Delete action: default (NO ACTION).

## Source manifest

SHA-256 hashes capture uncommitted entity changes too.

| Source | SHA-256 |
|---|---|
| Announcement.java | 0ccf20e631f3a7cab3c0f34671651b8734edb7ee515ddc4e6f1af82631b723ef |
| AuditLog.java | b7b32c1aaa8641e18ddc0cdc9c602718867d805aa67839d8d3d324e1f0c382c9 |
| HackathonEvent.java | a5cc3db2c09f301aa1041dc1e4b7b1a738e1960a2c7a9d45a8783c08c87d1231 |
| JoinRequest.java | 62426455df4a4f5027193a21b29f4b1754ecb15370373a31e3514443f8799d56 |
| JudgeAssignment.java | 6984bcc7cf1f8c751537a1638d9c6b1bf161810a07778fa07e2903ebf21833ef |
| MentorAssignment.java | 283c34b4e7a9976c5d28db33a7b83ee21e6f6c413a361ecddb26b43f3b402c49 |
| MentorSupportRequest.java | c46bfdaa0424a606bfad3bbb6b6d5fdb06b92340a3bd9634aad861d9abfe4fab |
| Notification.java | 3cf1d044b8af8f7826816b44424333792cc5be9ef01a7a0c6a1bbf856f03b037 |
| ParticipantEventHistory.java | 13cc6377e840a58c50df938ff2dd0ac90d0a7a71b50f1979b8a3c3eae36c6b56 |
| ParticipationAccessRequest.java | b9c59a087e8d7d847315f445a21d0ca8fcb76bc3e149bb5596488f1f68cb0e6b |
| PasswordResetOtp.java | c57125022bc69b6d47133cf18642a919bf2ecd25b9b793acaaa5ae1f746de7fc |
| Prize.java | fc75b63fd89d7a60838fe09d55bfd8dc1a6b5ef9f88ad78c13073fa74d6e1c86 |
| ReopenRequest.java | 1ba43af3013036afcc941af2ce1aa748d2a1c110714364f841c6afde3d971374 |
| Role.java | 49d24c6a7afc59a3e123e186fee342b47eba3beb57f2923dc8994bbc8b5304da |
| Round.java | 5db7b3266ae1970019f9e1c57b2da83009919bfc3ce9160183d3337ecb06faf0 |
| RoundResult.java | 60164bf77a9bf982bf56960a71ceee5331c10522b7636b81405c940dc2810b63 |
| RoundTimer.java | feaf84d1b3f1a476da4354eb821fdb3b537802bee282b108d51415a94603fd49 |
| RoundTimerNotice.java | 97881949d11ffcf44204cd8e361d17b71cb2f288309160a7b6a4adbec5058196 |
| Score.java | ca573ab87a4cdab29d4e66a1cfb27cc7fcb1d0ccc38eabccdd5c91fe97035553 |
| ScoringCriteria.java | c5b88e6f8e2291d8da1226a4876385c64ac9d749670ea4107e1191718721f3d3 |
| ScoringCriteriaTemplate.java | c126c63b63a5e0e7bb8f19628451b33cdc66f4b68f7404b9f75e66fd13f3380f |
| Submission.java | 75272eb0c9cc6c818faa0ce8aad6badcdc7e60d40c00506f350c85cb158020db |
| SystemLog.java | d936c661c01939ec269c7bb7fcaca87f28e2ad6d60e303c34fe2a809c570be0f |
| Team.java | 4a4a89f5494e9847012ea277e5dff4b53a319d9386eaa257f70d703056fb1f82 |
| TeamEventEntry.java | 38de329e6ff4670f80d3f8225a6305094925862e32d5586220f7ec36c0ad4485 |
| TeamInvite.java | e33ba68ec547bb9eebf118a746e7f49dd9144c4afd357b4b0ab964275c281237 |
| TeamMember.java | fdff5864ccf256ae264ff4946d859ed6fdc6081e290717a84cb832f0f57025fa |
| TeamRejoinRequest.java | 0279da7815c46e8f43a4773fb965a183f45f4ac18f0f6a3a3199a8b9913820ca |
| Track.java | 9b9bc0a5fb84f4e38c0f82395acc2b71a6ee1068096532a4243834740283b4fd |
| User.java | f1b25e96ee84f7dca07be92007ea2ccc309259fdf23fe69cc333e11c4a017321 |
| UserEventRole.java | 3cc9bea775438c9c0de89710ffc45278dc2fb83ffdd7a2a1678686813187279f |
