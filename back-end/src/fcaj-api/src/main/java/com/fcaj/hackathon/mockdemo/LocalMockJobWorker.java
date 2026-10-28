package com.fcaj.hackathon.mockdemo;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Demonstration transport: no AWS clients, network inference or official scores. */
@Component
@Profile("mock-demo")
@EnableScheduling
@ConditionalOnProperty(name = "app.ai.jobs.mock-worker.enabled", havingValue = "true", matchIfMissing = true)
public class LocalMockJobWorker {
    private static final Logger log = LoggerFactory.getLogger(LocalMockJobWorker.class);
    private final LocalMockJobService jobs;
    private final boolean success;

    public LocalMockJobWorker(LocalMockJobService jobs,
                              @Value("${app.ai.jobs.mock-outcome:SUCCEEDED}") String outcome) {
        this.jobs = jobs;
        if (!List.of("SUCCEEDED", "FAILED").contains(outcome)) {
            throw new IllegalStateException("Mock outcome must be SUCCEEDED or FAILED");
        }
        this.success = "SUCCEEDED".equals(outcome);
    }

    @Scheduled(initialDelayString = "${app.ai.jobs.mock-initial-delay-ms:5000}",
            fixedDelayString = "${app.ai.jobs.mock-poll-ms:5000}")
    public void tick() {
        // Complete only work queued before this tick so QUEUED stays visible.
        var queued = jobs.queuedWork();
        for (var work : jobs.pendingWork()) {
            try {
                jobs.markDispatched(work.jobId());
                log.info("[LOCAL_MOCK] jobId={} step=dispatch", work.jobId());
            } catch (RuntimeException error) {
                log.warn("[LOCAL_MOCK] jobId={} step=dispatch errorType={}",
                        work.jobId(), error.getClass().getSimpleName());
            }
        }
        for (var work : queued) {
            try {
                UUID messageId = UUID.nameUUIDFromBytes((work.jobId() + ":" + work.inputRevision()
                        + ":" + success).getBytes(StandardCharsets.UTF_8));
                var first = jobs.complete(work.jobId(), messageId, work.inputRevision(), success);
                var repeated = jobs.complete(work.jobId(), messageId, work.inputRevision(), success);
                log.info("[LOCAL_MOCK] jobId={} step=complete outcome={} first={} redelivery={}",
                        work.jobId(), success ? "SUCCEEDED" : "FAILED", first, repeated);
            } catch (RuntimeException error) {
                log.warn("[LOCAL_MOCK] jobId={} step=complete errorType={}",
                        work.jobId(), error.getClass().getSimpleName());
            }
        }
    }
}
