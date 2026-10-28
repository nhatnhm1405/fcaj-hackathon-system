package com.fcaj.hackathon.aijob;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/** Deterministic mock payload shared by the isolated CLI worker and integration tests. */
public final class MockWorkerOutput {
    private MockWorkerOutput() {}
    public static ObjectNode output(JsonNode request,JsonNode source,JsonNode rubric) {
        ObjectNode output=identity(request).put("schemaVersion","fcaj.ai.output.v1")
                .put("generatedAt",Instant.parse(request.path("occurredAt").asText()).plusSeconds(5).toString());
        output.putObject("provenance").put("mode","MOCK").put("modelId","mock-worker").put("modelRevision","mock-v1").put("adapterVersion","mock-adapter-v1");
        ObjectNode insight=output.putObject("insight").put("summary","Synthetic integration response. This worker does not evaluate source code.")
                .put("disclaimer","Mock assistance only; official scores are unchanged.").put("model","mock-worker");
        insight.putArray("strengths").add("Input artifacts passed transport checks.");
        insight.putArray("concerns").add("A real model has not assessed this submission.");
        var criteria=insight.putArray("criteriaInsights");
        for(JsonNode c:rubric.path("criteria")) {
            var item=criteria.addObject().put("criteriaId",c.path("criteriaId").asInt()).put("criteriaName",c.path("name").asText())
                    .put("assessment","Synthetic note for transport and lifecycle testing.");
            item.putNull("suggestedScoreRange");var evidence=item.putArray("evidence");
            if(!source.path("files").isEmpty()) evidence.addObject().put("path",source.path("files").get(0).path("path").asText()).put("startLine",1).put("endLine",1);
        }
        var repo=insight.putObject("repo").put("analyzed",false).put("note","Source was transported but not evaluated.");
        repo.putArray("techStack");repo.putArray("signals");repo.putArray("redFlags");
        return output;
    }
    public static ObjectNode completion(JsonNode request,boolean succeeded) {
        ObjectNode message=identity(request).put("schemaVersion","fcaj.ai.message.v1").put("eventType","AI_JOB_COMPLETED")
                .put("messageId",UUID.nameUUIDFromBytes((request.path("jobId").asText()+":"+request.path("inputRevision").asText()+":"+succeeded)
                        .getBytes(StandardCharsets.UTF_8)).toString())
                .put("occurredAt",Instant.parse(request.path("occurredAt").asText()).plusSeconds(6).toString()).put("status",succeeded?"SUCCEEDED":"FAILED");
        if(!succeeded) message.putObject("error").put("code","MOCK_PROCESSING_FAILED").put("message","Synthetic failure requested by the mock runner.");
        return message;
    }
    private static ObjectNode identity(JsonNode request) {
        ObjectNode result=JobJson.object();JobResultValidator.IDENTITY.stream().sorted().forEach(name -> result.set(name,request.path(name)));return result;
    }
}
