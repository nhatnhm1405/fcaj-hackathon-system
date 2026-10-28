package com.fcaj.hackathon.mockdemo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcaj.hackathon.dto.response.AiInsightResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/** Synthetic local job lifecycle, not a repository analysis or AWS transport. */
@Service
@Profile("mock-demo")
@RequiredArgsConstructor
public class LocalMockJobService {
    private final LocalMockJobStore store;
    private final LocalMockJobAccess access;
    private final ObjectMapper mapper = new ObjectMapper();
    public record Work(UUID jobId, String inputRevision) { }
    public record Status(UUID jobId, String status, OffsetDateTime createdAt, OffsetDateTime finishedAt,
                         boolean resultAvailable, String errorCode, String mode) { }

    public Status create(int requesterId, int submissionId) {
        var submission = access.forCreate(requesterId, submissionId);
        int eventId = submission.getRound().getEvent().getEventId();
        int roundId = submission.getRound().getRoundId();
        // A synthetic input fingerprint only. No repo is fetched or commit SHA claimed.
        String revision = sha256("LOCAL_MOCK_V1:" + UUID.randomUUID() + ":" + submissionId + ":" + roundId);
        return status(store.create(requesterId, submissionId, eventId, roundId, revision));
    }

    public Status get(int requesterId, UUID jobId) { return status(readable(requesterId, jobId)); }

    public AiInsightResponse result(int requesterId, UUID jobId) {
        var job = readable(requesterId, jobId);
        if ("FAILED".equals(job.status())) throw new MockJobException(HttpStatus.CONFLICT, "MOCK_PROCESSING_FAILED");
        if (!"SUCCEEDED".equals(job.status())) throw new MockJobException(HttpStatus.CONFLICT, "RESULT_NOT_READY");
        try { return mapper.readValue(job.resultPayload(), AiInsightResponse.class); }
        catch (JsonProcessingException invalid) { throw new MockJobException(HttpStatus.INTERNAL_SERVER_ERROR, "MOCK_RESULT_UNAVAILABLE"); }
    }

    public List<Work> pendingWork() { return work("PENDING_DISPATCH"); }
    public List<Work> queuedWork() { return work("QUEUED"); }
    public void markDispatched(UUID jobId) { store.markDispatched(jobId); }

    public LocalMockJobStore.Completion complete(UUID jobId, UUID messageId, String revision, boolean success) {
        if (jobId == null || messageId == null || revision == null) throw new IllegalArgumentException("Mock completion identity is required");
        String payload;
        try { payload = success ? mapper.writeValueAsString(syntheticResult()) : null; }
        catch (JsonProcessingException unexpected) { throw new IllegalStateException("Cannot encode local mock result"); }
        return store.complete(jobId, messageId, revision, success, payload);
    }

    private LocalMockJobStore.Job readable(int requesterId, UUID jobId) {
        var job = store.find(jobId).orElseThrow(MockJobException::hidden);
        access.forRead(requesterId, job);
        return job;
    }

    private List<Work> work(String state) {
        return store.work(state).stream().map(job -> new Work(job.jobId(), job.inputRevision())).toList();
    }

    private Status status(LocalMockJobStore.Job job) {
        return new Status(job.jobId(), job.status(), job.createdAt(), job.finishedAt(), "SUCCEEDED".equals(job.status()),
                "FAILED".equals(job.status()) ? "MOCK_PROCESSING_FAILED" : null, "LOCAL_MOCK");
    }

    private AiInsightResponse syntheticResult() {
        return AiInsightResponse.builder().summary("Local mock completed. No repository or source code was analyzed.")
                .strengths(List.of()).concerns(List.of("Use the judge's own review; this result is synthetic."))
                .criteriaInsights(List.of()).model("LOCAL_MOCK_V1")
                .disclaimer("LOCAL MOCK ONLY — no AWS, model inference, suggested scores or official score writes.").build();
    }

    static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
