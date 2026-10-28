package com.fcaj.hackathon.aijob;

import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
@ConditionalOnProperty(name="app.ai.jobs.enabled", havingValue="true")
public class AiJobStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public record Job(UUID jobId, int requesterId, int submissionId, int eventId, int roundId, String inputRevision,
                      String status, Instant createdAt, Instant finishedAt, String requestJson, String sourceJson,
                      String rubricJson, String resultJson, String errorCode, String terminalDigest) {}
    public record Delivery(UUID messageId, UUID jobId, String payload, String claimToken, int attempts) {}
    public enum Completion { APPLIED, DUPLICATE, CONFLICT, NOT_FOUND }
    public AiJobStore(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.tx=new TransactionTemplate(manager);this.tx.setTimeout(30);
    }
    private Job map(ResultSet rs, int row) throws SQLException {
        Timestamp finished=rs.getTimestamp("finished_at");
        return new Job(UUID.fromString(rs.getString("job_id")),rs.getInt("requester_id"),rs.getInt("submission_id"),
                rs.getInt("event_id"),rs.getInt("round_id"),rs.getString("input_revision"),rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime().toInstant(ZoneOffset.UTC),
                finished==null?null:finished.toLocalDateTime().toInstant(ZoneOffset.UTC),rs.getString("request_json"),
                rs.getString("source_json"),rs.getString("rubric_json"),rs.getString("result_json"),rs.getString("error_code"),rs.getString("terminal_digest"));
    }
    static Timestamp stamp(Instant instant) { return Timestamp.valueOf(LocalDateTime.ofInstant(instant,ZoneOffset.UTC)); }
    public Optional<Job> find(UUID id) { return jdbc.query("select * from AiJob where job_id=?",this::map,id.toString()).stream().findFirst(); }
    public void create(UUID jobId, int requester, JobSnapshotReader.Input input, String request, String source, String rubric) {
        tx.executeWithoutResult(status -> {
            var json=JobJson.parse(request);
            Instant now=Instant.now();
            jdbc.update("""
                    insert into AiJob(job_id,requester_id,submission_id,event_id,round_id,input_revision,status,created_at,request_json,source_json,rubric_json)
                    values(?,?,?,?,?,?,'PENDING_DISPATCH',?,?,?,?)
                    """,jobId.toString(),requester,input.submissionId(),input.eventId(),input.roundId(),json.get("inputRevision").asText(),stamp(now),request,source,rubric);
            jdbc.update("insert into AiJobOutbox(message_id,job_id,payload_json,created_at,next_attempt_at) values(?,?,?,?,?)",
                    json.get("messageId").asText(),jobId.toString(),request,stamp(now),stamp(now));
        });
    }
    /** Lease acquisition is conditional, so multiple backend replicas cannot own the same delivery. */
    public Optional<Delivery> claim() {
        return tx.execute(status -> {
            Instant now=Instant.now();
            var ids=jdbc.queryForList("""
                    select message_id from AiJobOutbox where sent_at is null and next_attempt_at<=?
                    and (claimed_until is null or claimed_until<=?) order by created_at,message_id limit 1
                    """,String.class,stamp(now),stamp(now));
            if(ids.isEmpty()) return Optional.empty();
            String id=ids.getFirst(), token=UUID.randomUUID().toString();
            int changed=jdbc.update("""
                    update AiJobOutbox set claim_token=?,claimed_until=?,attempts=attempts+1 where message_id=? and sent_at is null
                    and (claimed_until is null or claimed_until<=?)
                    """,token,stamp(now.plusSeconds(300)),id,stamp(now));
            if(changed!=1) return Optional.empty();
            return Optional.of(jdbc.queryForObject("select * from AiJobOutbox where message_id=?",(rs,row) -> new Delivery(
                    UUID.fromString(id),UUID.fromString(rs.getString("job_id")),rs.getString("payload_json"),token,rs.getInt("attempts")),id));
        });
    }
    public void sent(Delivery d) {
        tx.executeWithoutResult(status -> {
            // Lock the job first: completion follows the same lock order.
            jdbc.queryForList("select job_id from AiJob where job_id=? for update",d.jobId().toString());
            int changed=jdbc.update("update AiJobOutbox set sent_at=?,claim_token=null,claimed_until=null where message_id=? and claim_token=? and sent_at is null",
                    stamp(Instant.now()),d.messageId().toString(),d.claimToken());
            if(changed==1) jdbc.update("update AiJob set status='QUEUED' where job_id=? and status='PENDING_DISPATCH'",d.jobId().toString());
        });
    }
    public void retry(Delivery d) {
        long delay=Math.min(60,1L << Math.min(6,d.attempts()));
        jdbc.update("update AiJobOutbox set claim_token=null,claimed_until=null,next_attempt_at=? where message_id=? and claim_token=? and sent_at is null",
                stamp(Instant.now().plusSeconds(delay)),d.messageId().toString(),d.claimToken());
    }
    /** A committed ledger entry proves this exact message was validated before; no S3 reread is needed. */
    public Optional<Completion> processed(UUID jobId,UUID messageId,String digest) {
        var seen=jdbc.queryForList("select job_id,payload_digest from AiProcessedEvent where message_id=?",messageId.toString());
        if(seen.isEmpty()) return Optional.empty();
        return Optional.of(jobId.toString().equals(seen.getFirst().get("job_id")) && digest.equals(seen.getFirst().get("payload_digest"))
                ? Completion.DUPLICATE : Completion.CONFLICT);
    }
    public Completion complete(UUID jobId, UUID messageId, String messageDigest, String terminalDigest, String expectedRevision,
                               String outcome, String resultJson, String safeError) {
        return tx.execute(status -> {
            var list=jdbc.query("select * from AiJob where job_id=? for update",this::map,jobId.toString());
            if(list.isEmpty()) return Completion.NOT_FOUND;
            Job job=list.getFirst();
            if(!job.inputRevision().equals(expectedRevision)) return Completion.CONFLICT;
            var seen=jdbc.queryForList("select job_id,payload_digest from AiProcessedEvent where message_id=?",messageId.toString());
            if(!seen.isEmpty()) return jobId.toString().equals(seen.getFirst().get("job_id"))
                    && messageDigest.equals(seen.getFirst().get("payload_digest")) ? Completion.DUPLICATE : Completion.CONFLICT;
            boolean terminal=Set.of("SUCCEEDED","FAILED").contains(job.status());
            if(terminal && !terminalDigest.equals(job.terminalDigest())) return Completion.CONFLICT;
            jdbc.update("insert into AiProcessedEvent(message_id,job_id,payload_digest,processed_at) values(?,?,?,?)",
                    messageId.toString(),jobId.toString(),messageDigest,stamp(Instant.now()));
            if(terminal) return Completion.DUPLICATE;
            jdbc.update("update AiJob set status=?,finished_at=?,terminal_digest=?,result_json=?,error_code=? where job_id=?",
                    outcome,stamp(Instant.now()),terminalDigest,resultJson,safeError,jobId.toString());
            return Completion.APPLIED;
        });
    }
}
