package com.fcaj.hackathon.mockdemo;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/** A durable local simulator, deliberately separate from the competition datasource. */
@Repository
@Profile("mock-demo")
public class LocalMockJobStore {
    public enum Completion { APPLIED, DUPLICATE, CONFLICT, STALE, NOT_FOUND }
    public record Job(UUID jobId, int requesterId, int submissionId, int eventId, int roundId,
                      String inputRevision, String status, OffsetDateTime createdAt,
                      OffsetDateTime finishedAt, String resultPayload) { }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private static final RowMapper<Job> MAPPER = (rs, row) -> new Job(
            UUID.fromString(rs.getString("job_id")), rs.getInt("requester_id"),
            rs.getInt("submission_id"), rs.getInt("event_id"), rs.getInt("round_id"),
            rs.getString("input_revision"), rs.getString("status"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("finished_at", OffsetDateTime.class), rs.getString("result_payload"));

    public LocalMockJobStore(@Value("${app.ai.jobs.mock-store-url}") String url) {
        // Do not publish a second DataSource bean: that would disable Boot's core DB auto-configuration.
        var source = new DriverManagerDataSource(url, "sa", "");
        source.setDriverClassName("org.h2.Driver");
        Flyway.configure().dataSource(source).locations("classpath:db/mock-demo")
                .baselineOnMigrate(false).cleanDisabled(true).validateOnMigrate(true).load().migrate();
        jdbc = new JdbcTemplate(source);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    public Job create(int requesterId, int submissionId, int eventId, int roundId, String revision) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO MockAiJob(job_id,requester_id,submission_id,event_id,round_id,input_revision,status,created_at)
                VALUES (?,?,?,?,?,?,'PENDING_DISPATCH',?)
                """, id.toString(), requesterId, submissionId, eventId, roundId, revision, now());
        return find(id).orElseThrow();
    }

    public Optional<Job> find(UUID id) {
        return jdbc.query("SELECT * FROM MockAiJob WHERE job_id=?", MAPPER, id.toString()).stream().findFirst();
    }

    public List<Job> work(String status) {
        return jdbc.query("SELECT * FROM MockAiJob WHERE status=? ORDER BY created_at,job_id LIMIT 1", MAPPER, status);
    }

    public void markDispatched(UUID id) {
        jdbc.update("""
                UPDATE MockAiJob SET dispatched_at=COALESCE(dispatched_at,?),
                status=CASE WHEN status='PENDING_DISPATCH' THEN 'QUEUED' ELSE status END WHERE job_id=?
                """, now(), id.toString());
    }

    public Completion complete(UUID id, UUID messageId, String revision, boolean success, String payload) {
        String outcome = success ? "SUCCEEDED" : "FAILED";
        String digest = LocalMockJobService.sha256(success ? payload : "MOCK_PROCESSING_FAILED");
        try {
            return transactions.execute(transaction -> {
                var jobs = jdbc.query("SELECT * FROM MockAiJob WHERE job_id=? FOR UPDATE", MAPPER, id.toString());
                if (jobs.isEmpty()) return Completion.NOT_FOUND;
                Job job = jobs.getFirst();
                if (!job.inputRevision().equals(revision)) return Completion.STALE;
                var prior = jdbc.queryForList("SELECT job_id,input_revision,outcome,payload_digest FROM MockAiProcessedResult WHERE message_id=?", messageId.toString());
                if (!prior.isEmpty()) {
                    var event = prior.getFirst();
                    return id.toString().equals(event.get("JOB_ID")) && revision.equals(event.get("INPUT_REVISION"))
                            && outcome.equals(event.get("OUTCOME")) && digest.equals(event.get("PAYLOAD_DIGEST"))
                            ? Completion.DUPLICATE : Completion.CONFLICT;
                }
                boolean terminal = List.of("SUCCEEDED", "FAILED").contains(job.status());
                if (terminal && !outcome.equals(job.status())) return Completion.CONFLICT;
                if (terminal && success && !java.util.Objects.equals(job.resultPayload(), payload)) return Completion.CONFLICT;
                jdbc.update("INSERT INTO MockAiProcessedResult(message_id,job_id,input_revision,outcome,payload_digest,processed_at) VALUES (?,?,?,?,?,?)",
                        messageId.toString(), id.toString(), revision, outcome, digest, now());
                if (terminal) return Completion.DUPLICATE;
                jdbc.update("UPDATE MockAiJob SET status=?,finished_at=?,result_payload=? WHERE job_id=?",
                        outcome, now(), success ? payload : null, id.toString());
                return Completion.APPLIED;
            });
        } catch (DuplicateKeyException concurrentMessageReuse) {
            // A different job may race for the globally unique completion identity.
            // The local transaction rolled back; no terminal state was published.
            return Completion.CONFLICT;
        }
    }

    private static OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
}
