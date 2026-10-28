package com.fcaj.hackathon.mockdemo;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.*;

class LocalMockJobStoreTest {
    @TempDir Path temporary;
    private LocalMockJobStore store;
    private JdbcTemplate jdbc;
    private String url;
    private static final String REV = "a".repeat(64);

    @BeforeEach void setup() {
        url = "jdbc:h2:mem:mock_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
        store = new LocalMockJobStore(url);
        jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
    }

    private LocalMockJobStore.Job job() { return store.create(10, 20, 30, 40, REV); }

    @Test void completionAndDedupSurviveNewStoreInstance() {
        String file = "jdbc:h2:file:" + temporary.resolve("jobs").toString().replace('\\','/') + ";DB_CLOSE_ON_EXIT=FALSE";
        var first = new LocalMockJobStore(file);
        var job = first.create(10,20,30,40,REV);
        UUID message = UUID.randomUUID();
        assertThat(first.complete(job.jobId(),message,REV,true,"{}")).isEqualTo(LocalMockJobStore.Completion.APPLIED);
        var restarted = new LocalMockJobStore(file);
        assertThat(restarted.find(job.jobId()).orElseThrow().status()).isEqualTo("SUCCEEDED");
        assertThat(restarted.complete(job.jobId(),message,REV,true,"{}")).isEqualTo(LocalMockJobStore.Completion.DUPLICATE);
    }

    @Test void lateDispatchCannotOverwriteEarlyCompletion() {
        var job = job();
        store.complete(job.jobId(),UUID.randomUUID(),REV,true,"{}");
        store.markDispatched(job.jobId());
        assertThat(store.find(job.jobId()).orElseThrow().status()).isEqualTo("SUCCEEDED");
        assertThat(store.work("PENDING_DISPATCH")).isEmpty();
    }

    @Test void repeatedOrConflictingMessagesAreNotNewResults() {
        var job = job();
        UUID message = UUID.randomUUID();
        assertThat(store.complete(job.jobId(),message,REV,true,"{}")).isEqualTo(LocalMockJobStore.Completion.APPLIED);
        var finished = store.find(job.jobId()).orElseThrow().finishedAt();
        assertThat(store.complete(job.jobId(),message,REV,true,"{}")).isEqualTo(LocalMockJobStore.Completion.DUPLICATE);
        assertThat(store.complete(job.jobId(),message,REV,true,"{\"different\":true}")).isEqualTo(LocalMockJobStore.Completion.CONFLICT);
        assertThat(store.complete(job.jobId(),UUID.randomUUID(),REV,false,null)).isEqualTo(LocalMockJobStore.Completion.CONFLICT);
        assertThat(store.complete(job.jobId(),UUID.randomUUID(),REV,true,"{}")).isEqualTo(LocalMockJobStore.Completion.DUPLICATE);
        assertThat(store.find(job.jobId()).orElseThrow().finishedAt()).isEqualTo(finished);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM MockAiProcessedResult",Integer.class)).isEqualTo(2);
    }

    @Test void staleAndUnknownCompletionsDoNotChangeJobs() {
        var job = job();
        assertThat(store.complete(job.jobId(),UUID.randomUUID(),"b".repeat(64),true,"{}")).isEqualTo(LocalMockJobStore.Completion.STALE);
        assertThat(store.complete(UUID.randomUUID(),UUID.randomUUID(),REV,false,null)).isEqualTo(LocalMockJobStore.Completion.NOT_FOUND);
        assertThat(store.find(job.jobId()).orElseThrow().status()).isEqualTo("PENDING_DISPATCH");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM MockAiProcessedResult",Integer.class)).isZero();
    }

    @Test void failedStateIsDurableWithoutResultOrAutomaticTimeout() {
        var job = job();
        store.markDispatched(job.jobId());
        assertThat(store.find(job.jobId()).orElseThrow().status()).isEqualTo("QUEUED");
        assertThat(store.complete(job.jobId(),UUID.randomUUID(),REV,false,null)).isEqualTo(LocalMockJobStore.Completion.APPLIED);
        var result = store.find(job.jobId()).orElseThrow();
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.resultPayload()).isNull();
        assertThat(result.finishedAt()).isNotNull();
    }

    @Test void failedStateWriteRollsBackProcessedMessage() {
        var job = job();
        assertThatThrownBy(() -> store.complete(job.jobId(),UUID.randomUUID(),REV,true,null)).isInstanceOf(RuntimeException.class);
        assertThat(store.find(job.jobId()).orElseThrow().status()).isEqualTo("PENDING_DISPATCH");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM MockAiProcessedResult",Integer.class)).isZero();
    }

    @Test void reusedMessageCannotCompleteAnotherJob() {
        var first = job(); var second = job(); UUID message = UUID.randomUUID();
        store.complete(first.jobId(),message,REV,true,"{}");
        assertThat(store.complete(second.jobId(),message,REV,true,"{}")).isEqualTo(LocalMockJobStore.Completion.CONFLICT);
        assertThat(store.find(second.jobId()).orElseThrow().status()).isEqualTo("PENDING_DISPATCH");
    }

    @Test void concurrentRedeliveryAppliesOnlyOnce() throws Exception {
        var job = job(); UUID message = UUID.randomUUID();
        Callable<LocalMockJobStore.Completion> complete = () -> store.complete(job.jobId(),message,REV,true,"{}");
        try(var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(java.util.List.of(complete,complete));
            assertThat(results.stream().map(future -> { try {return future.get();} catch(Exception e){throw new RuntimeException(e);} }).toList())
                    .containsExactlyInAnyOrder(LocalMockJobStore.Completion.APPLIED,LocalMockJobStore.Completion.DUPLICATE);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM MockAiProcessedResult",Integer.class)).isEqualTo(1);
    }

    @Test void workerBatchesAreBoundedAndOldJobNeverOverridesNewJob() {
        var first=job(); var second=job();
        assertThat(store.work("PENDING_DISPATCH")).hasSize(1);
        store.complete(second.jobId(),UUID.randomUUID(),REV,true,"{\"new\":true}");
        store.complete(first.jobId(),UUID.randomUUID(),REV,false,null);
        assertThat(store.find(second.jobId()).orElseThrow().resultPayload()).isEqualTo("{\"new\":true}");
    }
}
