package com.fcaj.hackathon.aijob;

import com.fasterxml.jackson.databind.JsonNode;
import com.fcaj.hackathon.storage.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AwsMockWorkerTest {
    @Test void bothConsumersUseAgreedReceivePolicy() {
        var sqs=mock(SqsClient.class);
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder().build());
        AwsMockWorkerMain.receive(sqs,"jobs");
        new JobDelivery(null,null,sqs,"jobs","results",true).pollOnce();
        var requests=org.mockito.ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqs,times(2)).receiveMessage(requests.capture());
        assertThat(requests.getAllValues()).extracting(ReceiveMessageRequest::queueUrl).containsExactly("jobs","results");
        for(var request:requests.getAllValues()) {
            assertThat(request.visibilityTimeout()).isEqualTo(120);
            assertThat(request.waitTimeSeconds()).isEqualTo(20);
            assertThat(request.maxNumberOfMessages()).isEqualTo(1);
        }
    }
    private record Fixture(JsonNode request,ObjectStorage storage,SqsClient sqs) {}
    private Fixture fixture() throws Exception {
        var storage=mock(ObjectStorage.class);var sqs=mock(SqsClient.class);
        var root=java.nio.file.Path.of("../../../docs/contracts/ai-job-v1");
        var request=JobJson.parse(java.nio.file.Files.readString(root.resolve("job.json")));
        // Use the same contract fixture bytes, with the implemented source.json key.
        ((com.fasterxml.jackson.databind.node.ObjectNode)request.path("input").path("source")).put("key","inputs/"+request.path("jobId").asText()+"/source.json");
        var manifest=JobJson.parse(java.nio.file.Files.readString(root.resolve("artifacts/input-manifest.json")));
        ((com.fasterxml.jackson.databind.node.ObjectNode)manifest).set("source",request.path("input").path("source"));
        byte[] manifestBytes=JobJson.bytes(manifest);
        ((com.fasterxml.jackson.databind.node.ObjectNode)request.path("input").path("manifest")).put("sha256",ObjectKeys.sha256(manifestBytes)).put("sizeBytes",manifestBytes.length);
        ((com.fasterxml.jackson.databind.node.ObjectNode)request).put("inputRevision",ObjectKeys.sha256(manifestBytes));
        Map<String,byte[]> objects=new HashMap<>();objects.put(request.path("input").path("manifest").path("key").asText(),manifestBytes);
        objects.put(request.path("input").path("source").path("key").asText(),java.nio.file.Files.readAllBytes(root.resolve("artifacts/mock-source.json")));
        objects.put(request.path("input").path("rubric").path("key").asText(),java.nio.file.Files.readAllBytes(root.resolve("artifacts/rubric.json")));
        when(storage.read(anyString(),nullable(String.class),anyLong())).thenAnswer(c -> objects.get(c.getArgument(0)));
        when(storage.put(anyString(),any(byte[].class),anyString())).thenAnswer(c -> {
            byte[] bytes=c.getArgument(1);return new ObjectStorage.Ref(c.getArgument(0),ObjectKeys.sha256(bytes),bytes.length,"application/json","version-first-put");
        });
        return new Fixture(request,storage,sqs);
    }
    @Test void resultIsWrittenThenCompletionSentThenInputAcknowledged() throws Exception {
        var f=fixture();var message=Message.builder().body(f.request.toString()).receiptHandle("receipt").build();
        AwsMockWorkerMain.process(message,f.storage,f.sqs,"jobs","results",true);
        var order=inOrder(f.storage,f.sqs);
        order.verify(f.storage).put(startsWith("results/"),any(byte[].class),eq("application/json"));
        var sent=org.mockito.ArgumentCaptor.forClass(SendMessageRequest.class);order.verify(f.sqs).sendMessage(sent.capture());
        order.verify(f.sqs).deleteMessage(any(DeleteMessageRequest.class));
        JsonNode completion=JobJson.parse(sent.getValue().messageBody());assertThat(completion.path("result").path("versionId").isNull()).isTrue();
        assertThat(completion.path("messageId")).isEqualTo(MockWorkerOutput.completion(f.request,true).path("messageId"));
    }
    @Test void failedCompletionSendDoesNotDeleteJob() throws Exception {
        var f=fixture();when(f.sqs.sendMessage(any(SendMessageRequest.class))).thenThrow(new IllegalStateException("send failure"));
        assertThatThrownBy(() -> AwsMockWorkerMain.process(Message.builder().body(f.request.toString()).receiptHandle("receipt").build(),f.storage,f.sqs,"jobs","results",false)).isInstanceOf(IllegalStateException.class);
        verify(f.sqs,never()).deleteMessage(any(DeleteMessageRequest.class));
    }
    @Test void invalidInputChecksumCannotProduceCompletionOrAck() throws Exception {
        var f=fixture();when(f.storage.read(contains("input-manifest"),nullable(String.class),anyLong())).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> AwsMockWorkerMain.process(Message.builder().body(f.request.toString()).build(),f.storage,f.sqs,"jobs","results",true)).isInstanceOf(JobException.class);
        verifyNoInteractions(f.sqs);
    }
}
