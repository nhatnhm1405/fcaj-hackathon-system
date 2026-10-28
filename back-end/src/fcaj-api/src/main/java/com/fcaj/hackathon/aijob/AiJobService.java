package com.fcaj.hackathon.aijob;

import com.fcaj.hackathon.dto.response.AiInsightResponse;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name="app.ai.jobs.enabled", havingValue="true")
public class AiJobService {
    private final AiJobStore store;
    private final JobAccess access;
    private final JobSnapshotReader reader;
    private final GitHubSourceReader github;
    private final JobSnapshotWriter writer;
    private final JdbcTemplate jdbc;
    private final EntityManager entities;
    private final TransactionTemplate tx;
    public record Status(UUID jobId,String status,Instant createdAt,Instant finishedAt,boolean resultAvailable,String errorCode,String mode) {}
    public AiJobService(AiJobStore store,JobAccess access,JobSnapshotReader reader,GitHubSourceReader github,
                        JobSnapshotWriter writer,JdbcTemplate jdbc,EntityManager entities,PlatformTransactionManager manager) {
        this.store=store;this.access=access;this.reader=reader;this.github=github;this.writer=writer;
        this.jdbc=jdbc;this.entities=entities;this.tx=new TransactionTemplate(manager);
    }
    public Status create(int requester,int submission) {
        var input=reader.capture(requester,submission);
        var source=github.collect(input.repoUrl());
        UUID id=UUID.randomUUID();
        // Network and immutable S3 writes happen outside the business transaction.
        var request=writer.prepare(id,input,source);
        tx.executeWithoutResult(status -> {
            // Lock mutable inputs before reloading and rechecking permissions. Do not compare timestamps alone.
            jdbc.queryForList("select submission_id from Submission where submission_id=? for update",submission);
            jdbc.queryForList("select round_id from Round where round_id=? for update",input.roundId());
            jdbc.queryForList("select timer_id from RoundTimer where round_id=? order by timer_id for update",input.roundId());
            jdbc.queryForList("select criteria_id from ScoringCriteria where round_id=? order by criteria_id for update",input.roundId());
            jdbc.queryForList("select id from TeamEventEntry where event_id=? and team_id=(select team_id from Submission where submission_id=?) for update",input.eventId(),submission);
            jdbc.queryForList("select user_id from `User` where user_id=? for update",requester);
            jdbc.queryForList("select id from UserEventRole where user_id=? order by id for update",requester);
            jdbc.queryForList("select id from JudgeAssignment where judge_user_id=? order by id for update",requester);
            entities.clear();
            var current=reader.capture(requester,submission);
            if(!input.fingerprint().equals(current.fingerprint())) throw new JobException(HttpStatus.CONFLICT,"SUBMISSION_INPUT_CHANGED");
            store.create(id,requester,input,request.toString(),source.document().toString(),input.rubric().toString());
        });
        return status(store.find(id).orElseThrow(JobException::hidden));
    }
    public Status get(int requester,UUID id) { return status(visible(requester,id)); }
    public AiInsightResponse result(int requester,UUID id) {
        var job=visible(requester,id);
        if("FAILED".equals(job.status())) throw new JobException(HttpStatus.CONFLICT,job.errorCode());
        if(!"SUCCEEDED".equals(job.status())) throw new JobException(HttpStatus.CONFLICT,"RESULT_NOT_READY");
        try { return JobJson.MAPPER.readValue(job.resultJson(),AiInsightResponse.class); }
        catch(java.io.IOException invalid) { throw new IllegalStateException("Stored result is invalid"); }
    }
    private AiJobStore.Job visible(int requester,UUID id) {
        var job=store.find(id).orElseThrow(JobException::hidden);access.read(requester,job);return job;
    }
    private Status status(AiJobStore.Job j) { return new Status(j.jobId(),j.status(),j.createdAt(),j.finishedAt(),"SUCCEEDED".equals(j.status()),j.errorCode(),"MOCK"); }
}
