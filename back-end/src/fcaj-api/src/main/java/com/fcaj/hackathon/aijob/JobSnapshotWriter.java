package com.fcaj.hackathon.aijob;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fcaj.hackathon.storage.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="app.ai.jobs.enabled", havingValue="true")
public class JobSnapshotWriter {
    private final ObjectStorage storage;
    private final String processing, model, prompt;
    public JobSnapshotWriter(ObjectStorage storage,
            @Value("${app.ai.jobs.processing-version:mock-pipeline-v1}") String processing,
            @Value("${app.ai.jobs.model-config-version:mock-model-config-v1}") String model,
            @Value("${app.ai.jobs.prompt-version:mock-prompt-v1}") String prompt) {
        this.storage=storage; this.processing=version(processing); this.model=version(model); this.prompt=version(prompt);
    }
    private String version(String value) {
        if(!value.matches("[a-zA-Z0-9_.-]{1,100}")) throw new IllegalArgumentException("Invalid AI version setting");
        return value;
    }
    public ObjectNode prepare(UUID id, JobSnapshotReader.Input input, GitHubSourceReader.Source source) {
        byte[] sourceBytes=JobLimits.checked(JobJson.bytes(source.document()),JobLimits.SOURCE,"SOURCE_TOO_LARGE");
        byte[] rubricBytes=JobLimits.checked(JobJson.bytes(input.rubric()),JobLimits.RUBRIC,"RUBRIC_TOO_LARGE");
        var sourceRef=put("inputs/"+id+"/source.json",sourceBytes);
        var rubricRef=put("rubrics/"+id+"/rubric.json",rubricBytes);
        String rubricVersion=input.rubric().path("rubricVersion").asText();
        ObjectNode manifest=JobJson.object().put("schemaVersion","fcaj.ai.input-manifest.v1").put("fixtureOnly",false)
                .put("submissionId",input.submissionId()).put("competitionEventId",input.eventId()).put("roundId",input.roundId())
                .put("trackId",input.trackId()).put("commitSha",source.commitSha());
        manifest.set("submissionSnapshot",input.submission()); manifest.set("source",sourceRef); manifest.set("rubric",rubricRef);
        versions(manifest,rubricVersion);
        // Prepared immutable inputs may remain orphaned on failure. Reconciliation must check DB references.
        var manifestRef=put("inputs/"+id+"/input-manifest.json",
                JobLimits.checked(JobJson.bytes(manifest),JobLimits.MANIFEST,"MANIFEST_TOO_LARGE"));
        ObjectNode request=JobJson.object().put("schemaVersion","fcaj.ai.message.v1").put("eventType","AI_JOB_REQUESTED")
                .put("messageId",UUID.randomUUID().toString()).put("occurredAt",Instant.now().toString()).put("jobId",id.toString())
                .put("submissionId",input.submissionId()).put("inputRevision",manifestRef.path("sha256").asText())
                .put("commitSha",source.commitSha()).put("mode","MOCK");
        versions(request,rubricVersion); request.putNull("deadlineAt");
        ObjectNode refs=request.putObject("input"); refs.set("manifest",manifestRef); refs.set("source",sourceRef); refs.set("rubric",rubricRef);
        JobLimits.checked(JobJson.bytes(request),JobLimits.MESSAGE,"JOB_MESSAGE_TOO_LARGE");
        return request;
    }
    private void versions(ObjectNode node,String rubric) {
        node.put("processingVersion",processing).put("modelConfigVersion",model).put("promptVersion",prompt).put("rubricVersion",rubric);
    }
    private JsonNode put(String key, byte[] body) {
        ObjectStorage.Ref ref=storage.put(key,body,"application/json");
        return JobJson.MAPPER.valueToTree(ref);
    }
}
