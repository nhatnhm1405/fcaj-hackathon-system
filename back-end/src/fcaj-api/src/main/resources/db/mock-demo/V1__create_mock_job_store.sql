CREATE TABLE MockAiJob (
    job_id VARCHAR(36) PRIMARY KEY,
    requester_id INTEGER NOT NULL,
    submission_id INTEGER NOT NULL,
    event_id INTEGER NOT NULL,
    round_id INTEGER NOT NULL,
    input_revision CHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING_DISPATCH', 'QUEUED', 'SUCCEEDED', 'FAILED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    dispatched_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    result_payload CLOB,
    CONSTRAINT ck_mock_terminal CHECK (
        (status IN ('PENDING_DISPATCH', 'QUEUED') AND finished_at IS NULL AND result_payload IS NULL)
        OR (status = 'SUCCEEDED' AND finished_at IS NOT NULL AND result_payload IS NOT NULL)
        OR (status = 'FAILED' AND finished_at IS NOT NULL AND result_payload IS NULL)
    )
);
CREATE INDEX idx_mock_work ON MockAiJob(status, created_at, job_id);
CREATE INDEX idx_mock_requester ON MockAiJob(requester_id, created_at);

CREATE TABLE MockAiProcessedResult (
    message_id VARCHAR(36) PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL,
    input_revision CHAR(64) NOT NULL,
    outcome VARCHAR(24) NOT NULL CHECK (outcome IN ('SUCCEEDED', 'FAILED')),
    payload_digest CHAR(64) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_mock_processed_job FOREIGN KEY (job_id) REFERENCES MockAiJob(job_id)
);
