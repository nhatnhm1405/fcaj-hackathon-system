package com.fcaj.hackathon.aijob;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

@Component
@ConditionalOnProperty(name="app.ai.jobs.enabled", havingValue="true")
public class JobDelivery {
    private static final Logger LOG=LoggerFactory.getLogger(JobDelivery.class);
    private final AiJobStore store;
    private final JobResultValidator validator;
    private final SqsClient sqs;
    private final String jobs,results;
    private final boolean enabled;
    public JobDelivery(AiJobStore store,JobResultValidator validator,SqsClient sqs,
            @Value("${app.ai.jobs.jobs-queue-url}") String jobs,@Value("${app.ai.jobs.results-queue-url}") String results,
            @Value("${app.ai.jobs.delivery.enabled:true}") boolean enabled) {
        this.store=store;this.validator=validator;this.sqs=sqs;this.jobs=jobs;this.results=results;this.enabled=enabled;
    }
    public void dispatchTick() { if(enabled) dispatchOne(); }
    public void dispatchOne() {
        store.claim().ifPresent(d -> {
            try {
                var sent=sqs.sendMessage(SendMessageRequest.builder().queueUrl(jobs).messageBody(d.payload()).build());
                if(sent==null || sent.messageId()==null || sent.messageId().isBlank()) throw new IllegalStateException("No SQS send confirmation");
                store.sent(d);
                LOG.info("ai_job jobId={} delivery=sent attempt={}",d.jobId(),d.attempts());
            } catch(RuntimeException failed) {
                store.retry(d);
                LOG.warn("ai_job jobId={} delivery=retry attempt={}",d.jobId(),d.attempts());
            }
        });
    }
    /** Returns only after durable commit. Invalid input or any DB error never reaches DeleteMessage. */
    public void consume(Message message) {
        if(message.body()==null || message.body().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>JobLimits.MESSAGE) throw JobException.invalid("INVALID_WORKER_RESULT");
        var node=JobJson.parse(message.body());
        UUID id;
        try { id=UUID.fromString(JobJson.text(node,"jobId",36)); }
        catch(IllegalArgumentException bad) { throw JobException.invalid("INVALID_WORKER_RESULT"); }
        var job=store.find(id).orElseThrow(JobException::hidden);
        UUID messageId;
        try { messageId=UUID.fromString(JobJson.text(node,"messageId",36)); }
        catch(IllegalArgumentException bad) { throw JobException.invalid("INVALID_WORKER_RESULT"); }
        var seen=store.processed(id,messageId,com.fcaj.hackathon.storage.ObjectKeys.sha256(JobJson.bytes(node)));
        if(seen.isPresent()) {
            if(seen.get()!=AiJobStore.Completion.DUPLICATE) throw JobException.invalid("WORKER_RESULT_CONFLICT");
            acknowledge(message.receiptHandle());
            LOG.info("ai_job jobId={} completion=DUPLICATE",id);return;
        }
        var v=validator.validate(message.body(),job);
        var outcome=store.complete(id,v.messageId(),v.messageDigest(),v.terminalDigest(),job.inputRevision(),v.status(),v.result(),v.error());
        if(outcome!=AiJobStore.Completion.APPLIED && outcome!=AiJobStore.Completion.DUPLICATE) throw JobException.invalid("WORKER_RESULT_CONFLICT");
        acknowledge(message.receiptHandle());
        LOG.info("ai_job jobId={} completion={}",id,outcome);
    }
    private void acknowledge(String receipt) {
        sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(results).receiptHandle(receipt).build());
    }
    public void pollOnce() {
        var messages=sqs.receiveMessage(JobQueuePolicy.receive(results)).messages();
        for(Message message:messages) {
            try { consume(message); }
            catch(RuntimeException failed) { LOG.warn("ai_result processing=deferred reason={}",failed instanceof JobException?failed.getMessage():"DEPENDENCY_UNAVAILABLE"); }
        }
    }
}
