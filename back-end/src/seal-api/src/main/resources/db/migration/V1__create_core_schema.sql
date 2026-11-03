create table `User` (
        is_active bit not null,
        is_approved bit not null,
        user_id integer not null auto_increment,
        created_at datetime(6) not null,
        expired_at datetime(6),
        judge_type varchar(20),
        provider varchar(20) not null,
        user_type varchar(20) not null,
        student_id varchar(50),
        avatar_url varchar(500),
        email varchar(255) not null,
        full_name varchar(255) not null,
        password_hash varchar(255),
        provider_id varchar(255),
        university varchar(255),
        primary key (user_id)
    ) engine=InnoDB;

    create table Announcement (
        announcement_id integer not null auto_increment,
        event_id integer not null,
        recipient_count integer not null,
        sender_user_id integer not null,
        track_id integer,
        created_at datetime(6) not null,
        audience varchar(20),
        scope varchar(20) not null,
        sender_role varchar(20) not null,
        link_url varchar(1000),
        content TEXT,
        title varchar(255) not null,
        primary key (announcement_id)
    ) engine=InnoDB;

    create table AuditLog (
        actor_user_id integer not null,
        log_id integer not null auto_increment,
        target_id integer,
        created_at datetime(6) not null,
        ip_address varchar(45),
        action varchar(50) not null,
        target_type varchar(50),
        metadata_json TEXT,
        reason TEXT,
        primary key (log_id)
    ) engine=InnoDB;

    create table HackathonEvent (
        event_id integer not null auto_increment,
        year integer not null,
        created_at datetime(6) not null,
        end_date datetime(6) not null,
        registration_end datetime(6),
        registration_start datetime(6),
        start_date datetime(6) not null,
        season varchar(20) not null,
        status varchar(20) not null,
        track_selection_mode varchar(20) not null,
        topic varchar(500),
        description TEXT,
        name varchar(255) not null,
        primary key (event_id)
    ) engine=InnoDB;

    create table JoinRequest (
        event_id integer not null,
        request_id integer not null auto_increment,
        requester_user_id integer not null,
        team_id integer not null,
        created_at datetime(6) not null,
        responded_at datetime(6),
        status varchar(20) not null,
        message TEXT,
        primary key (request_id)
    ) engine=InnoDB;

    create table JudgeAssignment (
        id integer not null auto_increment,
        is_active bit not null,
        judge_user_id integer not null,
        round_id integer not null,
        track_id integer,
        primary key (id)
    ) engine=InnoDB;

    create table MentorAssignment (
        id integer not null auto_increment,
        is_active bit not null,
        mentor_user_id integer not null,
        track_id integer not null,
        primary key (id)
    ) engine=InnoDB;

    create table MentorSupportRequest (
        request_id integer not null auto_increment,
        requester_user_id integer not null,
        resolved_by_user_id integer,
        team_id integer not null,
        track_id integer not null,
        created_at datetime(6) not null,
        resolved_at datetime(6),
        category varchar(20) not null,
        status varchar(20) not null,
        description TEXT,
        primary key (request_id)
    ) engine=InnoDB;

    create table Notification (
        announcement_id integer,
        is_read bit not null,
        notification_id integer not null auto_increment,
        recipient_user_id integer not null,
        sender_user_id integer,
        created_at datetime(6) not null,
        type varchar(50),
        sender_role varchar(100),
        content TEXT,
        scope_label varchar(255),
        title varchar(255) not null,
        primary key (notification_id)
    ) engine=InnoDB;

    create table ParticipantEventHistory (
        event_id integer not null,
        id integer not null auto_increment,
        user_id integer not null,
        snapshot_at datetime(6) not null,
        snapshot_reason varchar(30) not null,
        result_json TEXT not null,
        primary key (id)
    ) engine=InnoDB;

    create table ParticipationAccessRequest (
        request_id integer not null auto_increment,
        resolved_by integer,
        user_id integer not null,
        requested_at datetime(6) not null,
        resolved_at datetime(6),
        status varchar(20) not null,
        email varchar(255) not null,
        primary key (request_id)
    ) engine=InnoDB;

    create table PasswordResetOtp (
        attempt_count integer not null,
        user_id integer not null,
        created_at datetime(6) not null,
        expires_at datetime(6) not null,
        id bigint not null auto_increment,
        used_at datetime(6),
        verified_at datetime(6),
        otp_hash varchar(255) not null,
        reset_token_hash varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table Prize (
        event_id integer not null,
        prize_id integer not null auto_increment,
        rank_position integer not null,
        team_id integer,
        track_id integer,
        awarded_at datetime(6),
        description TEXT,
        name varchar(255) not null,
        primary key (prize_id)
    ) engine=InnoDB;

    create table ReopenRequest (
        event_id integer not null,
        request_id integer not null auto_increment,
        requested_by integer not null,
        resolved_by integer,
        created_at datetime(6) not null,
        resolved_at datetime(6),
        status varchar(20) not null,
        reason TEXT,
        primary key (request_id)
    ) engine=InnoDB;

    create table Role (
        role_id integer not null auto_increment,
        role_name varchar(50) not null,
        description varchar(255),
        primary key (role_id)
    ) engine=InnoDB;

    create table Round (
        event_id integer not null,
        is_final bit not null,
        order_number integer not null,
        round_id integer not null auto_increment,
        top_n_advance integer,
        end_time datetime(6) not null,
        start_time datetime(6) not null,
        submission_deadline datetime(6) not null,
        status varchar(20) not null,
        name varchar(255) not null,
        primary key (round_id)
    ) engine=InnoDB;

    create table RoundResult (
        finalized_by integer,
        is_published bit not null,
        rank_position integer not null,
        result_id integer not null auto_increment,
        round_id integer not null,
        team_id integer not null,
        total_score decimal(7,2) not null,
        finalized_at datetime(6),
        primary key (result_id)
    ) engine=InnoDB;

    create table RoundTimer (
        duration_seconds integer,
        notify_at_half bit not null,
        remaining_at_pause integer,
        round_id integer not null,
        timer_id integer not null auto_increment,
        ends_at datetime(6),
        paused_at datetime(6),
        started_at datetime(6),
        updated_at datetime(6),
        phase varchar(20) not null,
        status varchar(20) not null,
        milestone_minutes varchar(100) not null,
        primary key (timer_id)
    ) engine=InnoDB;

    create table RoundTimerNotice (
        id integer not null auto_increment,
        round_id integer not null,
        created_at datetime(6) not null,
        phase varchar(20) not null,
        milestone_key varchar(30) not null,
        primary key (id)
    ) engine=InnoDB;

    create table Score (
        criteria_id integer not null,
        is_draft bit not null,
        judge_user_id integer not null,
        score_id integer not null auto_increment,
        submission_id integer not null,
        value decimal(5,2) not null,
        scored_at datetime(6) not null,
        updated_at datetime(6),
        comment TEXT,
        primary key (score_id)
    ) engine=InnoDB;

    create table ScoringCriteria (
        criteria_id integer not null auto_increment,
        event_id integer,
        max_score decimal(5,2) not null,
        order_number integer,
        round_id integer,
        template_id integer,
        weight decimal(5,2) not null,
        description TEXT,
        name varchar(255) not null,
        primary key (criteria_id)
    ) engine=InnoDB;

    create table ScoringCriteriaTemplate (
        is_default bit not null,
        template_id integer not null auto_increment,
        created_at datetime(6) not null,
        description TEXT,
        name varchar(255) not null,
        primary key (template_id)
    ) engine=InnoDB;

    create table Submission (
        round_id integer not null,
        submission_id integer not null auto_increment,
        submitted_by integer not null,
        team_id integer not null,
        submitted_at datetime(6) not null,
        status varchar(20) not null,
        demo_url varchar(500),
        repo_url varchar(500),
        slide_url varchar(500),
        description TEXT,
        primary key (submission_id)
    ) engine=InnoDB;

    create table SystemLog (
        actor_user_id integer not null,
        log_id integer not null auto_increment,
        created_at datetime(6) not null,
        action varchar(50) not null,
        detail TEXT,
        primary key (log_id)
    ) engine=InnoDB;

    create table Team (
        is_active bit not null,
        team_id integer not null auto_increment,
        created_at datetime(6) not null,
        description TEXT,
        name varchar(255) not null,
        primary key (team_id)
    ) engine=InnoDB;

    create table TeamEventEntry (
        event_id integer not null,
        id integer not null auto_increment,
        team_id integer not null,
        track_id integer,
        created_at datetime(6) not null,
        disqualified_at datetime(6),
        status varchar(20) not null,
        disqualified_reason TEXT,
        primary key (id)
    ) engine=InnoDB;

    create table TeamInvite (
        event_id integer not null,
        invite_id integer not null auto_increment,
        invited_by integer not null,
        invited_user_id integer not null,
        team_id integer not null,
        created_at datetime(6) not null,
        responded_at datetime(6),
        status varchar(20) not null,
        message TEXT,
        primary key (invite_id)
    ) engine=InnoDB;

    create table TeamMember (
        id integer not null auto_increment,
        team_id integer not null,
        user_id integer not null,
        joined_at datetime(6) not null,
        member_role varchar(20) not null,
        primary key (id)
    ) engine=InnoDB;

    create table TeamRejoinRequest (
        event_id integer not null,
        request_id integer not null auto_increment,
        requested_by integer not null,
        resolved_by integer,
        team_id integer not null,
        requested_at datetime(6) not null,
        resolved_at datetime(6),
        status varchar(20) not null,
        primary key (request_id)
    ) engine=InnoDB;

    create table Track (
        capacity integer,
        event_id integer not null,
        problem_released bit not null,
        track_id integer not null auto_increment,
        created_at datetime(6) not null,
        problem_file_size bigint,
        problem_released_at datetime(6),
        problem_uploaded_at datetime(6),
        problem_content_type varchar(100),
        problem_storage_key varchar(500),
        description TEXT,
        name varchar(255) not null,
        problem_file_name varchar(255),
        primary key (track_id)
    ) engine=InnoDB;

    create table UserEventRole (
        event_id integer,
        id integer not null auto_increment,
        role_id integer not null,
        user_id integer not null,
        primary key (id)
    ) engine=InnoDB;

    alter table `User`
       add constraint UKt7gueymdut91qb606y2pyjpq0 unique (email);

    alter table JoinRequest
       add constraint uq_join_team_user unique (team_id, requester_user_id);

    alter table JudgeAssignment
       add constraint uq_judge_round_track unique (judge_user_id, round_id, track_id);

    alter table MentorAssignment
       add constraint uq_mentor_track unique (mentor_user_id, track_id);

    alter table ParticipantEventHistory
       add constraint uq_participant_event_history_user_event unique (user_id, event_id);

    alter table PasswordResetOtp
       add constraint UKst11t2d3pha8h90na5bytbc48 unique (reset_token_hash);

    alter table Role
       add constraint UKltwrim5wenkon6cb48qt459ax unique (role_name);

    alter table Round
       add constraint uq_round_event_order unique (event_id, order_number);

    alter table RoundResult
       add constraint uq_result_team_round unique (team_id, round_id);

    alter table RoundTimer
       add constraint uq_roundtimer_round_phase unique (round_id, phase);

    alter table RoundTimerNotice
       add constraint uq_timer_notice unique (round_id, phase, milestone_key);

    alter table Score
       add constraint uq_score unique (submission_id, judge_user_id, criteria_id);

    alter table Submission
       add constraint uq_submission_team_round unique (team_id, round_id);

    alter table TeamEventEntry
       add constraint uq_tee_team_event unique (team_id, event_id);

    alter table TeamInvite
       add constraint uq_invite_team_user unique (team_id, invited_user_id);

    alter table TeamMember
       add constraint uq_team_member_team_user unique (team_id, user_id);

    alter table Track
       add constraint uq_track_event_name unique (event_id, name);

    alter table UserEventRole
       add constraint uq_user_role_event unique (user_id, role_id, event_id);

    alter table Announcement
       add constraint FKkp485kgr1nixubc12ibr9i059
       foreign key (event_id)
       references HackathonEvent (event_id);

    alter table Announcement
       add constraint FKj0k10nwdi8xpedni6du4m2rrx
       foreign key (sender_user_id)
       references `User` (user_id);

    alter table Announcement
       add constraint FK3mtec5l38b5g7uw09his56yyj
       foreign key (track_id)
       references Track (track_id);

    alter table AuditLog
       add constraint FKi7uor6rk4v34axo46thhsx10w
       foreign key (actor_user_id)
       references `User` (user_id);

    alter table JoinRequest
       add constraint FKs42l2j040sjh2a3k9joqodw6a
       foreign key (requester_user_id)
       references `User` (user_id);

    alter table JoinRequest
       add constraint FKtmp21ie4q8uojv33ix1vbagpp
       foreign key (team_id)
       references Team (team_id);

    alter table JudgeAssignment
       add constraint FKm268eq8al1se053sjsa7auqy7
       foreign key (judge_user_id)
       references `User` (user_id);

    alter table JudgeAssignment
       add constraint FK6bi8qe9mhtcx5b0jws0tdkobi
       foreign key (round_id)
       references Round (round_id);

    alter table JudgeAssignment
       add constraint FKrf86e6sjwu6oqs0nrd7lw6liv
       foreign key (track_id)
       references Track (track_id);

    alter table MentorAssignment
       add constraint FKq9sd0su4lj1969bt7ogpvem5b
       foreign key (mentor_user_id)
       references `User` (user_id);

    alter table MentorAssignment
       add constraint FKkiwcdcu71rnos0jctlgo9uy9e
       foreign key (track_id)
       references Track (track_id);

    alter table MentorSupportRequest
       add constraint FK7el1ecfk3g260ji7o7kru5q4y
       foreign key (requester_user_id)
       references `User` (user_id);

    alter table MentorSupportRequest
       add constraint FK1l6t2rhp5vanxti1vvpb5ar1g
       foreign key (resolved_by_user_id)
       references `User` (user_id);

    alter table MentorSupportRequest
       add constraint FKff40y7exvt6hgt7vhj9qdbvoj
       foreign key (team_id)
       references Team (team_id);

    alter table MentorSupportRequest
       add constraint FKjky434awiq0nu43annqe3xhxh
       foreign key (track_id)
       references Track (track_id);

    alter table Notification
       add constraint FK7sas2p5j2mb5gisnegngnpjb8
       foreign key (announcement_id)
       references Announcement (announcement_id);

    alter table Notification
       add constraint FKcvnonqg7yg3mrlkydfvjvt3di
       foreign key (recipient_user_id)
       references `User` (user_id);

    alter table Notification
       add constraint FK20te7jl42tf6w1abcffpw153t
       foreign key (sender_user_id)
       references `User` (user_id);

    alter table ParticipantEventHistory
       add constraint FKariy39l7cod4nf3hlxh0d20ja
       foreign key (user_id)
       references `User` (user_id);

    alter table ParticipationAccessRequest
       add constraint FK9kno78sch4goccowagto4aygt
       foreign key (user_id)
       references `User` (user_id)
       on delete cascade;

    alter table PasswordResetOtp
       add constraint FKpiqeucp8x5mrnuqs0c3eohvpv
       foreign key (user_id)
       references `User` (user_id);

    alter table Prize
       add constraint FK93wg612ms7p9hd8lp1ahay301
       foreign key (event_id)
       references HackathonEvent (event_id);

    alter table Prize
       add constraint FK6iygdf9934cr4mn5dqanr6f8s
       foreign key (team_id)
       references Team (team_id);

    alter table Prize
       add constraint FK913rn0qcx0q38dtm72mri45fn
       foreign key (track_id)
       references Track (track_id);

    alter table ReopenRequest
       add constraint FKio550gla34778eve8tkiuletp
       foreign key (event_id)
       references HackathonEvent (event_id);

    alter table ReopenRequest
       add constraint FKjy1uwt40cor4e96afd4ks7h2q
       foreign key (requested_by)
       references `User` (user_id);

    alter table ReopenRequest
       add constraint FK165du6lxgyrdya321e1um8tuh
       foreign key (resolved_by)
       references `User` (user_id);

    alter table Round
       add constraint FKdgf3751t8wfh3hkmltyjevhc8
       foreign key (event_id)
       references HackathonEvent (event_id);

    alter table RoundResult
       add constraint FKmd7uuu6ixvknevm39kfkx2u6f
       foreign key (finalized_by)
       references `User` (user_id);

    alter table RoundResult
       add constraint FKohqls7rnxs9x5msrl3vq76pq2
       foreign key (round_id)
       references Round (round_id);

    alter table RoundResult
       add constraint FK1eejyb5tvcx442hha705jd0w4
       foreign key (team_id)
       references Team (team_id);

    alter table RoundTimer
       add constraint FKm36f2fvhsgpap07wpb522ym15
       foreign key (round_id)
       references Round (round_id)
       on delete cascade;

    alter table Score
       add constraint FK7kp7i8c97684tv6jbhjqks48o
       foreign key (criteria_id)
       references ScoringCriteria (criteria_id);

    alter table Score
       add constraint FKbxxbncqgno1lcu91xqo7n7csk
       foreign key (judge_user_id)
       references `User` (user_id);

    alter table Score
       add constraint FKg47s9rijyf8ujs2tloei056p8
       foreign key (submission_id)
       references Submission (submission_id);

    alter table ScoringCriteria
       add constraint FK3qsf5n0rfejc9so1cks80f0fy
       foreign key (event_id)
       references HackathonEvent (event_id);

    alter table ScoringCriteria
       add constraint FK7jy31rdkfb6cvcslgr0ga0835
       foreign key (round_id)
       references Round (round_id);

    alter table ScoringCriteria
       add constraint FKqveh4955pei2y7qcqp0rsxruf
       foreign key (template_id)
       references ScoringCriteriaTemplate (template_id);

    alter table Submission
       add constraint FKqrruus73tf74hnri4387i6q2p
       foreign key (round_id)
       references Round (round_id);

    alter table Submission
       add constraint FK6qe3kyog6fe83efmkh9hps6xt
       foreign key (submitted_by)
       references `User` (user_id);

    alter table Submission
       add constraint FK5kbfk4gxt8fers03qw0o7akbe
       foreign key (team_id)
       references Team (team_id);

    alter table SystemLog
       add constraint FKe0wtoxt9slsgqc5i7m8quveyo
       foreign key (actor_user_id)
       references `User` (user_id);

    alter table TeamEventEntry
       add constraint FKac2o5ovxqd9clht8tjgrjgft6
       foreign key (event_id)
       references HackathonEvent (event_id);

    alter table TeamEventEntry
       add constraint FKgq997g7yajhpw0uo2ceiv566o
       foreign key (team_id)
       references Team (team_id);

    alter table TeamEventEntry
       add constraint FKl4sllugxk9sh1oqdrgj1aeihw
       foreign key (track_id)
       references Track (track_id);

    alter table TeamInvite
       add constraint FK5uuvddwplxs6v9ry0nkvgle0y
       foreign key (invited_by)
       references `User` (user_id);

    alter table TeamInvite
       add constraint FKspj1xmhtn0km9dqs4qhuob5kj
       foreign key (invited_user_id)
       references `User` (user_id);

    alter table TeamInvite
       add constraint FKjgmru8jywyf8kkcnw5xcwysty
       foreign key (team_id)
       references Team (team_id);

    alter table TeamMember
       add constraint FKc020iwyqywwr93x2wrkfyfhko
       foreign key (team_id)
       references Team (team_id);

    alter table TeamMember
       add constraint FK82rcm62g6rh3gloopd8p2ta5m
       foreign key (user_id)
       references `User` (user_id);

    alter table TeamRejoinRequest
       add constraint FKeuvldwv8du0f3yarntm1v6t2f
       foreign key (requested_by)
       references `User` (user_id);

    alter table TeamRejoinRequest
       add constraint FK78yskuuek1ri8ruj7a3vmxyio
       foreign key (team_id)
       references Team (team_id)
       on delete cascade;

    alter table Track
       add constraint FKge2tytdcmioo46uvot1bu0yrq
       foreign key (event_id)
       references HackathonEvent (event_id);

    alter table UserEventRole
       add constraint FK8jbgdgswj5yag5eyi5reb2rwa
       foreign key (role_id)
       references Role (role_id);

    alter table UserEventRole
       add constraint FK4fvvy2709kduvt6oby6ih9ywm
       foreign key (user_id)
       references `User` (user_id);
