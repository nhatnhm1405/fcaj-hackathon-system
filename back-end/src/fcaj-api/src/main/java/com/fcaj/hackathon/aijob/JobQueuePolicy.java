package com.fcaj.hackathon.aijob;

import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

/** Shared PoC MOCK receive policy; unrelated to the outbox claim lease. */
final class JobQueuePolicy {
    private JobQueuePolicy() {}
    static ReceiveMessageRequest receive(String queue) {
        return ReceiveMessageRequest.builder().queueUrl(queue).maxNumberOfMessages(1)
                .waitTimeSeconds(20).visibilityTimeout(120).build();
    }
}
