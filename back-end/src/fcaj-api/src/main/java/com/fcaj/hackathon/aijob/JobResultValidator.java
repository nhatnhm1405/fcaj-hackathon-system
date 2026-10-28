package com.fcaj.hackathon.aijob;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fcaj.hackathon.storage.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Validates the worker boundary and maps a small allowlist into the existing FE insight shape. */
@Component
@ConditionalOnProperty(name="app.ai.jobs.enabled", havingValue="true")
public class JobResultValidator {
    static final Set<String> IDENTITY=Set.of("jobId","submissionId","inputRevision","commitSha","processingVersion","modelConfigVersion","promptVersion","rubricVersion");
    private final ObjectStorage storage;
    public JobResultValidator(ObjectStorage storage) { this.storage=storage; }
    public record Validated(UUID messageId, String messageDigest, String terminalDigest, String status, String result, String error) {}
    public Validated validate(String raw,AiJobStore.Job job) {
        if(raw==null || raw.getBytes(StandardCharsets.UTF_8).length>JobLimits.MESSAGE) throw invalid();
        JsonNode message=JobJson.parse(raw), request=JobJson.parse(job.requestJson());
        var required=new HashSet<>(IDENTITY);required.addAll(Set.of("schemaVersion","eventType","messageId","occurredAt","status"));
        String status=JobJson.text(message,"status",20);
        if(!Set.of("SUCCEEDED","FAILED").contains(status)) throw invalid();
        required.add("SUCCEEDED".equals(status)?"result":"error"); fields(message,required,Set.of());
        if(!"fcaj.ai.message.v1".equals(message.path("schemaVersion").asText()) || !"AI_JOB_COMPLETED".equals(message.path("eventType").asText())) throw invalid();
        identity(message,request); timestamp(message,"occurredAt");
        UUID messageId;
        try { messageId=UUID.fromString(JobJson.text(message,"messageId",36)); }
        catch(IllegalArgumentException bad) { throw invalid(); }
        if(!messageId.toString().equals(message.path("messageId").asText())) throw invalid();
        String result=null,error=null,semantic;
        if("FAILED".equals(status)) {
            JsonNode failure=message.path("error"); fields(failure,Set.of("code","message"),Set.of());
            String code=JobJson.text(failure,"code",60);JobJson.text(failure,"message",1000);
            if(!"MOCK_PROCESSING_FAILED".equals(code)) throw invalid();
            error="MOCK_PROCESSING_FAILED";semantic=status+":"+error;
        } else {
            JsonNode ref=message.path("result");fields(ref,Set.of("key","sha256","sizeBytes","contentType","versionId"),Set.of());
            String key=JobJson.text(ref,"key",500),hash=JobJson.text(ref,"sha256",64);
            if(!key.startsWith("results/"+job.jobId()+"/") || !key.substring(("results/"+job.jobId()+"/").length()).matches("[a-zA-Z0-9_-]+\\.json")
                    || !hash.matches("[a-f0-9]{64}") || !"application/json".equals(ref.path("contentType").asText())
                    || !ref.path("sizeBytes").isIntegralNumber() || !ref.path("sizeBytes").canConvertToLong()
                    || ref.path("sizeBytes").asLong()<1 || ref.path("sizeBytes").asLong()>JobLimits.OUTPUT) throw invalid();
            String version=ref.path("versionId").isNull()?null:JobJson.text(ref,"versionId",1024);
            byte[] bytes=storage.read(key,version,JobLimits.OUTPUT);
            if(bytes.length!=ref.path("sizeBytes").asLong() || !ObjectKeys.sha256(bytes).equals(hash)) throw invalid();
            JsonNode output=JobJson.parse(new String(bytes,StandardCharsets.UTF_8));
            var outputFields=new HashSet<>(IDENTITY);outputFields.addAll(Set.of("schemaVersion","generatedAt","provenance","insight"));
            fields(output,outputFields,Set.of()); identity(output,request); timestamp(output,"generatedAt");
            if(!"fcaj.ai.output.v1".equals(output.path("schemaVersion").asText())) throw invalid();
            JsonNode provenance=output.path("provenance");fields(provenance,Set.of("mode","modelId","modelRevision","adapterVersion"),Set.of());
            if(!request.path("mode").asText().equals(provenance.path("mode").asText()) || !"MOCK".equals(provenance.path("mode").asText())) throw invalid();
            for(String name:List.of("modelId","modelRevision","adapterVersion"))
                if(!JobJson.text(provenance,name,100).matches("[a-zA-Z0-9_.-]+")) throw invalid();
            result=insight(output.path("insight"),job,provenance.path("modelId").asText()).toString();
            semantic=status+":"+hash;
        }
        return new Validated(messageId,ObjectKeys.sha256(JobJson.bytes(message)),ObjectKeys.sha256(semantic.getBytes(StandardCharsets.UTF_8)),status,result,error);
    }
    private ObjectNode insight(JsonNode input,AiJobStore.Job job,String model) {
        fields(input,Set.of("summary","strengths","concerns","criteriaInsights","repo","disclaimer","model"),Set.of());
        ObjectNode safe=JobJson.object().put("summary",text(input,"summary",4000));
        safe.set("strengths",strings(input.path("strengths"),20,2000));safe.set("concerns",strings(input.path("concerns"),20,2000));
        Map<Integer,JsonNode> rubric=new HashMap<>();
        for(JsonNode c:JobJson.parse(job.rubricJson()).path("criteria")) rubric.put(c.path("criteriaId").asInt(),c);
        Map<String,Integer> lines=new HashMap<>();
        for(JsonNode f:JobJson.parse(job.sourceJson()).path("files")) lines.put(f.path("path").asText(),f.path("content").asText().split("\n",-1).length);
        JsonNode items=input.path("criteriaInsights");
        if(!items.isArray() || items.size()!=rubric.size()) throw invalid();
        Set<Integer> seen=new HashSet<>();var list=safe.putArray("criteriaInsights");
        for(JsonNode item:items) {
            fields(item,Set.of("criteriaId","criteriaName","assessment","suggestedScoreRange","evidence"),Set.of());
            if(!item.path("criteriaId").isInt()) throw invalid();
            int id=item.path("criteriaId").asInt();JsonNode expected=rubric.get(id);
            if(expected==null || !seen.add(id) || !expected.path("name").equals(item.path("criteriaName")) || !item.path("suggestedScoreRange").isNull()) throw invalid();
            var entry=list.addObject().put("criteriaName",expected.path("name").asText()).put("assessment",text(item,"assessment",4000));
            entry.putNull("suggestedScoreRange");
            JsonNode evidence=item.path("evidence");if(!evidence.isArray() || evidence.size()>20) throw invalid();
            for(JsonNode e:evidence) {
                fields(e,Set.of("path","startLine","endLine"),Set.of());
                String path=JobJson.text(e,"path",300);Integer count=lines.get(path);
                if(count==null || !e.path("startLine").isInt() || !e.path("endLine").isInt()
                        || e.path("startLine").asInt()<1 || e.path("endLine").asInt()<e.path("startLine").asInt()
                        || e.path("endLine").asInt()>count) throw invalid();
            }
        }
        JsonNode repo=input.path("repo");fields(repo,Set.of("analyzed","note","techStack","signals","redFlags"),Set.of());
        if(!repo.path("analyzed").isBoolean() || repo.path("analyzed").asBoolean()) throw invalid();
        ObjectNode safeRepo=safe.putObject("repo").put("analyzed",false).put("note",text(repo,"note",1000));
        for(String name:List.of("techStack","signals","redFlags")) safeRepo.set(name,strings(repo.path(name),20,1000));
        text(input,"disclaimer",2000);
        if(!model.equals(JobJson.text(input,"model",100))) throw invalid();
        safe.put("model",model).put("disclaimer","Mock assistance for integration testing only. It does not assign or change official scores.");
        return safe;
    }
    private JsonNode strings(JsonNode value,int count,int length) {
        if(!value.isArray() || value.size()>count) throw invalid();
        for(JsonNode item:value) {
            if(!item.isTextual() || item.asText().length()>length || item.asText().isBlank()) throw invalid();
            safeText(item.asText());
        }
        return value.deepCopy();
    }
    private String text(JsonNode input,String field,int max) { String value=JobJson.text(input,field,max);safeText(value);return value; }
    private void safeText(String text) {
        if(text.matches("(?is).*(https?://|[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}).*") || text.indexOf('\0')>=0) throw invalid();
    }
    static void fields(JsonNode object,Set<String> required,Set<String> optional) {
        if(!object.isObject()) throw invalid();
        Set<String> actual=new HashSet<>();object.fieldNames().forEachRemaining(actual::add);
        if(!actual.containsAll(required)) throw invalid();actual.removeAll(required);actual.removeAll(optional);
        if(!actual.isEmpty()) throw invalid();
    }
    static void identity(JsonNode candidate,JsonNode request) {
        for(String name:IDENTITY) if(!request.path(name).equals(candidate.path(name))) throw invalid();
    }
    private static void timestamp(JsonNode node,String name) {
        try { Instant.parse(JobJson.text(node,name,40)); }
        catch(Exception bad) { throw invalid(); }
    }
    private static JobException invalid() { return JobException.invalid("INVALID_WORKER_RESULT"); }
}
