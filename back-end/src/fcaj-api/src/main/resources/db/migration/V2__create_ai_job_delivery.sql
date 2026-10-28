-- Durable delivery uses the business datasource. UTC timestamps are written by the application.
create table AiJob (
    job_id varchar(36) primary key,
    requester_id integer not null,
    submission_id integer not null,
    event_id integer not null,
    round_id integer not null,
    input_revision varchar(64) not null,
    status varchar(20) not null,
    created_at datetime(6) not null,
    finished_at datetime(6),
    request_json mediumtext not null,
    source_json mediumtext not null,
    rubric_json mediumtext not null,
    result_json mediumtext,
    terminal_digest varchar(64),
    error_code varchar(60),
    constraint fk_ai_job_requester foreign key (requester_id) references `User`(user_id),
    constraint fk_ai_job_submission foreign key (submission_id) references Submission(submission_id),
    constraint fk_ai_job_event foreign key (event_id) references HackathonEvent(event_id),
    constraint fk_ai_job_round foreign key (round_id) references Round(round_id),
    constraint ck_ai_job_status check (status in ('PENDING_DISPATCH','QUEUED','SUCCEEDED','FAILED')),
    constraint ck_ai_job_terminal check (
        (status in ('PENDING_DISPATCH','QUEUED') and finished_at is null and terminal_digest is null and result_json is null and error_code is null)
        or (status = 'SUCCEEDED' and finished_at is not null and terminal_digest is not null and result_json is not null and error_code is null)
        or (status = 'FAILED' and finished_at is not null and terminal_digest is not null and result_json is null and error_code is not null)
    )
);
create index ix_ai_job_submission_created on AiJob(submission_id, created_at);
create index ix_ai_job_requester on AiJob(requester_id, event_id);

create table AiJobOutbox (
    message_id varchar(36) primary key,
    job_id varchar(36) not null unique,
    payload_json mediumtext not null,
    created_at datetime(6) not null,
    next_attempt_at datetime(6) not null,
    attempts integer not null default 0,
    claim_token varchar(36),
    claimed_until datetime(6),
    sent_at datetime(6),
    constraint fk_ai_outbox_job foreign key (job_id) references AiJob(job_id)
);
create index ix_ai_outbox_pending on AiJobOutbox(sent_at, next_attempt_at, claimed_until);

create table AiProcessedEvent (
    message_id varchar(36) primary key,
    job_id varchar(36) not null,
    payload_digest varchar(64) not null,
    processed_at datetime(6) not null,
    constraint fk_ai_processed_job foreign key (job_id) references AiJob(job_id)
);
