package com.fcaj.hackathon.aijob;

import com.fasterxml.jackson.databind.JsonNode;
import com.fcaj.hackathon.storage.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

/** Explicit, one-message mock runner. Never started by Spring or the production backend. */
public final class AwsMockWorkerMain {
    private AwsMockWorkerMain() {}
    public static void main(String[] args) {
        if(args.length!=1 || !Set.of("--one-success","--one-failure").contains(args[0]))
            throw new IllegalArgumentException("Use --one-success or --one-failure against an isolated agreed mock queue pair");
        String region=required("AWS_REGION"),jobs=required("APP_AI_JOBS_JOBS_QUEUE_URL"),results=required("APP_AI_JOBS_RESULTS_QUEUE_URL");
        var env=new org.springframework.core.env.StandardEnvironment();
        // Validate URLs without loading the application, its secrets or its business DB.
        var props=new java.util.HashMap<String,Object>();props.put("app.aws.region",region);props.put("app.ai.jobs.jobs-queue-url",jobs);
        props.put("app.ai.jobs.results-queue-url",results);props.put("app.storage.provider","s3");props.put("spring.flyway.enabled","true");
        props.put("spring.jpa.hibernate.ddl-auto","validate");env.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("worker",props));
        JobEnvironmentConfiguration.validate(env);
        var limits=ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(40)).apiCallAttemptTimeout(Duration.ofSeconds(30)).build();
        try(var s3=S3Client.builder().region(Region.of(region)).credentialsProvider(DefaultCredentialsProvider.builder().build())
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(25))).overrideConfiguration(limits).build();
            var sqs=SqsClient.builder().region(Region.of(region)).credentialsProvider(DefaultCredentialsProvider.builder().build())
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(25))).overrideConfiguration(limits).build()) {
            var storage=new S3ObjectStorage(s3,required("APP_STORAGE_BUCKET"),required("APP_STORAGE_PREFIX"));
            var messages=receive(sqs,jobs);
            if(messages.isEmpty()) { System.out.println("No pending mock job.");return; }
            process(messages.getFirst(),storage,sqs,jobs,results,"--one-success".equals(args[0]));
        } catch(RuntimeException failed) {
            System.err.println("Mock worker failed; job was not acknowledged. Check queue/DLQ and scoped runtime permissions.");System.exit(1);
        }
    }
    static List<Message> receive(SqsClient sqs,String jobs) {
        return sqs.receiveMessage(JobQueuePolicy.receive(jobs)).messages();
    }
    static void process(Message delivery,ObjectStorage storage,SqsClient sqs,String jobs,String results,boolean success) {
        if(delivery.body()==null || delivery.body().getBytes(StandardCharsets.UTF_8).length>JobLimits.MESSAGE) throw JobException.invalid("INVALID_JOB_MESSAGE");
        var request=JobJson.parse(delivery.body());
        var requestFields=new HashSet<>(JobResultValidator.IDENTITY);
        requestFields.addAll(Set.of("schemaVersion","eventType","messageId","occurredAt","mode","deadlineAt","input"));
        JobResultValidator.fields(request,requestFields,Set.of());
        if(!"fcaj.ai.message.v1".equals(request.path("schemaVersion").asText()) || !"AI_JOB_REQUESTED".equals(request.path("eventType").asText())
                || !"MOCK".equals(request.path("mode").asText())) throw JobException.invalid("MOCK_MODE_REQUIRED");
        UUID.fromString(JobJson.text(request,"messageId",36));
        if(!request.path("submissionId").isInt() || request.path("submissionId").asInt()<1
                || !JobJson.text(request,"commitSha",40).matches("[a-f0-9]{40}")
                || !JobJson.text(request,"inputRevision",64).matches("[a-f0-9]{64}") || !request.path("deadlineAt").isNull())
            throw JobException.invalid("INVALID_JOB_MESSAGE");
        for(String version:List.of("processingVersion","modelConfigVersion","promptVersion","rubricVersion")) JobJson.text(request,version,100);
        JobResultValidator.fields(request.path("input"),Set.of("manifest","source","rubric"),Set.of());
        UUID id=UUID.fromString(JobJson.text(request,"jobId",36));
        JsonNode manifest=read(storage,request.path("input").path("manifest"),"inputs/"+id+"/input-manifest.json",JobLimits.MANIFEST);
        if(!manifest.path("schemaVersion").asText().equals("fcaj.ai.input-manifest.v1") || !request.path("inputRevision").equals(request.path("input").path("manifest").path("sha256")))
            throw JobException.invalid("INVALID_INPUT_MANIFEST");
        for(String name:List.of("submissionId","commitSha","processingVersion","modelConfigVersion","promptVersion","rubricVersion"))
            if(!request.path(name).equals(manifest.path(name))) throw JobException.invalid("INVALID_INPUT_MANIFEST");
        if(!manifest.path("source").equals(request.path("input").path("source")) || !manifest.path("rubric").equals(request.path("input").path("rubric")))
            throw JobException.invalid("INVALID_INPUT_MANIFEST");
        JsonNode source=read(storage,manifest.path("source"),"inputs/"+id+"/source.json",JobLimits.SOURCE);
        JsonNode rubric=read(storage,manifest.path("rubric"),"rubrics/"+id+"/rubric.json",JobLimits.RUBRIC);
        if(!request.path("commitSha").equals(source.path("commitSha")) || !request.path("rubricVersion").equals(rubric.path("rubricVersion")))
            throw JobException.invalid("INVALID_INPUT_MANIFEST");
        var completion=MockWorkerOutput.completion(request,success);
        if(success) {
            byte[] bytes=JobJson.bytes(MockWorkerOutput.output(request,source,rubric));
            JobLimits.checked(bytes,JobLimits.OUTPUT,"OUTPUT_TOO_LARGE");
            String key="results/"+id+"/mock-output.json";ObjectStorage.Ref ref;
            try { ref=storage.put(key,bytes,"application/json"); }
            catch(software.amazon.awssdk.services.s3.model.S3Exception exists) {
                if(exists.statusCode()!=412) throw exists;
                if(!Arrays.equals(bytes,storage.read(key,null,JobLimits.OUTPUT))) throw JobException.invalid("IMMUTABLE_OUTPUT_CONFLICT");
                ref=new ObjectStorage.Ref(key,ObjectKeys.sha256(bytes),bytes.length,"application/json",null);
            }
            // The immutable job key is sufficient. Keep the completion byte identity stable on redelivery,
            // including when the first PutObject returned a bucket version and a retry only read it.
            ref=new ObjectStorage.Ref(ref.key(),ref.sha256(),ref.sizeBytes(),ref.contentType(),null);
            completion.set("result",JobJson.MAPPER.valueToTree(ref));
        }
        JobLimits.checked(JobJson.bytes(completion),JobLimits.MESSAGE,"JOB_MESSAGE_TOO_LARGE");
        sqs.sendMessage(SendMessageRequest.builder().queueUrl(results).messageBody(completion.toString()).build());
        sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(jobs).receiptHandle(delivery.receiptHandle()).build());
        System.out.println("Mock completion sent for jobId="+id+"; official scores unchanged.");
    }
    private static JsonNode read(ObjectStorage storage,JsonNode ref,String expected,long max) {
        JobResultValidator.fields(ref,Set.of("key","sha256","sizeBytes","contentType","versionId"),Set.of());
        if(!expected.equals(ref.path("key").asText()) || !"application/json".equals(ref.path("contentType").asText())
                || !ref.path("sizeBytes").isIntegralNumber() || ref.path("sizeBytes").asLong()<1 || ref.path("sizeBytes").asLong()>max)
            throw JobException.invalid("INVALID_INPUT_REFERENCE");
        String version=ref.path("versionId").isNull()?null:JobJson.text(ref,"versionId",1024);
        byte[] bytes=storage.read(expected,version,max);
        if(bytes.length!=ref.path("sizeBytes").asLong() || !ObjectKeys.sha256(bytes).equals(ref.path("sha256").asText()))
            throw JobException.invalid("INVALID_INPUT_CHECKSUM");
        return JobJson.parse(new String(bytes,StandardCharsets.UTF_8));
    }
    private static String required(String name) {
        String value=System.getenv(name);if(value==null || value.isBlank()) throw new IllegalArgumentException("Missing runtime setting "+name);return value;
    }
}
