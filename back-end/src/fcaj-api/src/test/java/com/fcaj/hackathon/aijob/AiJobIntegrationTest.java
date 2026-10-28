package com.fcaj.hackathon.aijob;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fcaj.hackathon.entity.*;
import com.fcaj.hackathon.repository.*;
import com.fcaj.hackathon.security.*;
import com.fcaj.hackathon.storage.*;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:ai_jobs;DB_CLOSE_DELAY=-1;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;NON_KEYWORDS=YEAR,VALUE",
        "app.ai.jobs.enabled=true","app.ai.jobs.delivery.enabled=false","app.aws.region=ap-southeast-1",
        "app.ai.jobs.jobs-queue-url=https://sqs.ap-southeast-1.amazonaws.com/123456789012/local-jobs",
        "app.ai.jobs.results-queue-url=https://sqs.ap-southeast-1.amazonaws.com/123456789012/local-results"})
@ActiveProfiles("test")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiJobIntegrationTest {
    @DynamicPropertySource static void dedicatedMysql(DynamicPropertyRegistry properties) {
        String url=System.getenv("FCAJ_AI_TEST_JDBC_URL");
        if(url==null || url.isBlank()) return;
        if(!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/fcaj_ai_integration_lab\\?.*"))
            throw new IllegalStateException("Only a dedicated loopback integration lab is allowed");
        properties.add("spring.datasource.url",() -> url);
        properties.add("spring.datasource.username",() -> "root");
        properties.add("spring.datasource.password",() -> System.getenv("MYSQL_ROOT_PASSWORD"));
        properties.add("spring.datasource.driver-class-name",() -> "com.mysql.cj.jdbc.Driver");
        properties.add("spring.jpa.database-platform",() -> "org.hibernate.dialect.MySQLDialect");
        properties.add("spring.jpa.hibernate.ddl-auto",() -> "validate");
        properties.add("spring.flyway.enabled",() -> "true");
        properties.add("spring.datasource.hikari.connection-init-sql",() -> "SET time_zone = '+00:00'");
    }
    @Autowired WebApplicationContext context;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired AiJobService jobs;
    @Autowired JobDelivery delivery;
    @Autowired JobResultValidator validator;
    @MockitoSpyBean AiJobStore store;
    @MockitoBean ObjectStorage storage;
    @MockitoBean GitHubSourceReader github;
    @MockitoBean SqsClient sqs;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserEventRoleRepository grants;
    @Autowired HackathonEventRepository events;
    @Autowired RoundRepository rounds;
    @Autowired RoundTimerRepository timers;
    @Autowired TrackRepository tracks;
    @Autowired TeamRepository teams;
    @Autowired TeamEventEntryRepository entries;
    @Autowired SubmissionRepository submissions;
    @Autowired JudgeAssignmentRepository assignments;
    @Autowired ScoringCriteriaRepository criteria;
    @Autowired ScoreRepository scores;
    @Autowired JwtService jwt;
    private final Map<String,byte[]> objects=new ConcurrentHashMap<>();
    private MockMvc mvc;
    private User judge,other,coordinator,participant;
    private Submission submission;
    private RoundTimer timer;
    private JudgeAssignment assignment;
    private ObjectNode source;
    private String lastCreateResponse;
    @BeforeAll void schema() {
        if(System.getenv("FCAJ_AI_TEST_JDBC_URL")==null)
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V2__create_ai_job_delivery.sql")).execute(dataSource);
    }
    @BeforeEach void setup() {
        jdbc.update("delete from AiProcessedEvent");jdbc.update("delete from AiJobOutbox");jdbc.update("delete from AiJob");objects.clear();
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(storage.put(anyString(),any(byte[].class),anyString())).thenAnswer(call -> {
            String key=call.getArgument(0);byte[] bytes=call.getArgument(1);
            if(objects.putIfAbsent(key,bytes.clone())!=null) throw new IllegalStateException("immutable");
            return new ObjectStorage.Ref(key,ObjectKeys.sha256(bytes),bytes.length,call.getArgument(2),null);
        });
        when(storage.read(anyString(),nullable(String.class),anyLong())).thenAnswer(call -> {
            byte[] bytes=objects.get(call.getArgument(0));if(bytes==null) throw new IllegalStateException("missing");
            if(bytes.length>(long)call.getArgument(2)) throw new IllegalArgumentException("too large");return bytes.clone();
        });
        source=JobJson.object().put("schemaVersion","fcaj.ai.source.github.v1").put("commitSha","a".repeat(40)).put("fixtureOnly",true);
        source.putArray("files").addObject().put("path","src/example.txt").put("content","first line\nsecond line\n");
        when(github.collect(anyString())).thenReturn(new GitHubSourceReader.Source("a".repeat(40),source));
        when(sqs.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().messageId("transport-id").build());
        var now=LocalDateTime.now();
        var event=events.save(HackathonEvent.builder().name("AI integration").season("Fall").year(2026).status("IN_PROGRESS")
                .startDate(now.minusDays(1)).endDate(now.plusDays(1)).build());
        judge=user("STAFF");other=user("STAFF");coordinator=user("STAFF");participant=user("FPT_STUDENT");
        grant(judge,"JUDGE",event.getEventId());grant(other,"JUDGE",event.getEventId());grant(coordinator,"EVENT_COORDINATOR",event.getEventId());
        var round=rounds.save(Round.builder().event(event).name("AI round").orderNumber(1).status("ACTIVE")
                .startTime(now.minusHours(1)).endTime(now.plusHours(1)).submissionDeadline(now.minusMinutes(1)).build());
        var track=tracks.save(Track.builder().event(event).name("AI track").build());
        var team=teams.save(Team.builder().name("DO_NOT_EXPOSE_TEAM").build());
        entries.save(TeamEventEntry.builder().team(team).event(event).track(track).status("APPROVED").build());
        submission=submissions.save(Submission.builder().team(team).round(round).submittedBy(participant).status("SUBMITTED")
                .repoUrl("https://github.com/DO_NOT_EXPOSE_OWNER/repository").description("Synthetic test").build());
        assignment=assignments.save(JudgeAssignment.builder().judge(judge).round(round).track(track).build());
        assignments.save(JudgeAssignment.builder().judge(other).round(round).track(track).build());
        timer=timers.save(RoundTimer.builder().round(round).phase("JUDGING").status("RUNNING").durationSeconds(600).startedAt(now).endsAt(now.plusMinutes(10)).build());
        criteria.save(ScoringCriteria.builder().event(event).round(round).name("Technical implementation").orderNumber(1).build());
    }
    private User user(String type) { return users.save(User.builder().email(UUID.randomUUID()+"@example.test").fullName("DO_NOT_EXPOSE_PERSON").userType(type).isApproved(true).build()); }
    private void grant(User user,String role,int event) { grants.save(UserEventRole.builder().user(user).role(roles.findByRoleName(role).orElseThrow()).eventId(event).build()); }
    private String token(User user) {
        var principal=new UserPrincipal(users.findByIdWithRoles(user.getUserId()).orElseThrow());
        return jwt.generateToken(principal,user.getUserId(),principal.getAuthorities().stream().map(a -> a.getAuthority()).toList());
    }
    private UUID create() throws Exception {
        var body=mvc.perform(post("/api/ai/submissions/{id}/jobs",submission.getSubmissionId()).header("Authorization","Bearer "+token(judge)))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("PENDING_DISPATCH"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("DO_NOT_EXPOSE","inputRevision","requesterId","repoUrl");
        lastCreateResponse=body;
        return UUID.fromString(JobJson.parse(body).path("data").path("jobId").asText());
    }
    @Test void oversizedRubricReturnsSafeErrorWithoutStorageJobOutboxOrQueue() throws Exception {
        for(int i=0;i<5;i++) criteria.save(ScoringCriteria.builder().event(submission.getRound().getEvent()).round(submission.getRound())
                .name("large "+i).description("a".repeat(60000)).weight(java.math.BigDecimal.ONE).maxScore(java.math.BigDecimal.TEN).orderNumber(100+i).build());
        clearInvocations(storage,sqs);
        mvc.perform(post("/api/ai/submissions/{id}/jobs",submission.getSubmissionId()).header("Authorization","Bearer "+token(judge)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("RUBRIC_TOO_LARGE"));
        verify(storage,never()).put(anyString(),any(),anyString());verify(sqs,never()).sendMessage(any(SendMessageRequest.class));
        assertThat(jdbc.queryForObject("select count(*) from AiJob",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from AiJobOutbox",Integer.class)).isZero();
    }
    private ObjectNode success(UUID id) {
        var job=store.find(id).orElseThrow();var request=JobJson.parse(job.requestJson());
        var output=MockWorkerOutput.output(request,JobJson.parse(job.sourceJson()),JobJson.parse(job.rubricJson()));
        return withOutput(job,output,"mock-output");
    }
    private ObjectNode withOutput(AiJobStore.Job job,ObjectNode output,String name) {
        var message=MockWorkerOutput.completion(JobJson.parse(job.requestJson()),true);
        var ref=storage.put("results/"+job.jobId()+"/"+name+".json",JobJson.bytes(output),"application/json");
        message.set("result",JobJson.MAPPER.valueToTree(ref));return message;
    }
    private Message message(ObjectNode body) { return Message.builder().body(body.toString()).receiptHandle("receipt").build(); }
    @Test void productionWorkerRoundTripSuccessFailureAndDuplicate() throws Exception {
        long before=scores.count();
        for(boolean success:List.of(true,false)) {
            UUID id=create();clearInvocations(sqs);delivery.dispatchOne();
            var requested=org.mockito.ArgumentCaptor.forClass(SendMessageRequest.class);
            verify(sqs).sendMessage(requested.capture());
            assertThat(store.find(id).orElseThrow().status()).isEqualTo("QUEUED");
            clearInvocations(sqs);
            AwsMockWorkerMain.process(Message.builder().body(requested.getValue().messageBody()).receiptHandle("job-receipt").build(),storage,sqs,"jobs","results",success);
            var completed=org.mockito.ArgumentCaptor.forClass(SendMessageRequest.class);
            var order=inOrder(sqs);order.verify(sqs).sendMessage(completed.capture());
            order.verify(sqs).deleteMessage(argThat((DeleteMessageRequest r) -> r.queueUrl().equals("jobs")));
            assertThat(completed.getValue().queueUrl()).isEqualTo("results");
            var resultMessage=Message.builder().body(completed.getValue().messageBody()).receiptHandle("result-receipt").build();
            delivery.consume(resultMessage);delivery.consume(resultMessage);
            assertThat(store.find(id).orElseThrow().status()).isEqualTo(success?"SUCCEEDED":"FAILED");
            mvc.perform(get("/api/ai/jobs/{id}",id).header("Authorization","Bearer "+token(judge)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(success?"SUCCEEDED":"FAILED"));
            if(success) mvc.perform(get("/api/ai/jobs/{id}/result",id).header("Authorization","Bearer "+token(judge)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.model").value("mock-worker"));
            else mvc.perform(get("/api/ai/jobs/{id}/result",id).header("Authorization","Bearer "+token(judge)))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("MOCK_PROCESSING_FAILED"));
            // Only synthetic artifacts and public response bodies; never serialize users, JWTs or DB rows.
            var evidence=java.nio.file.Path.of("target/integration-contract",success?"success":"failure");
            java.nio.file.Files.createDirectories(evidence);
            java.nio.file.Files.writeString(evidence.resolve("http-create.json"),lastCreateResponse);
            java.nio.file.Files.writeString(evidence.resolve("job.json"),requested.getValue().messageBody());
            java.nio.file.Files.writeString(evidence.resolve("completion.json"),completed.getValue().messageBody());
            var request=JobJson.parse(requested.getValue().messageBody());
            for(String name:List.of("source","rubric","manifest"))
                java.nio.file.Files.write(evidence.resolve(name+".json"),objects.get(request.path("input").path(name).path("key").asText()));
            if(success) java.nio.file.Files.write(evidence.resolve("output.json"),objects.get(JobJson.parse(completed.getValue().messageBody()).path("result").path("key").asText()));
            for(String suffix:List.of("","/result")) {
                var response=mvc.perform(get("/api/ai/jobs/"+id+suffix).header("Authorization","Bearer "+token(judge))).andReturn().getResponse();
                java.nio.file.Files.writeString(evidence.resolve(suffix.isEmpty()?"http-status.json":"http-result.json"),response.getContentAsString());
            }
            System.out.println("LOCAL_COMPONENT_SMOKE jobId="+id+" outcome="+(success?"SUCCEEDED":"FAILED")+" duplicate=accepted source=synthetic");
        }
        assertThat(scores.count()).isEqualTo(before);
    }
    @Test void roundTripCommitsBeforeAckAndKeepsScoresUntouched() throws Exception {
        long before=scores.count();UUID id=create();delivery.dispatchOne();
        assertThat(store.find(id).orElseThrow().status()).isEqualTo("QUEUED");
        doAnswer(call -> {
            assertThat(jdbc.queryForObject("select status from AiJob where job_id=?",String.class,id.toString())).isEqualTo("SUCCEEDED");
            assertThat(jdbc.queryForObject("select count(*) from AiProcessedEvent",Integer.class)).isEqualTo(1);
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return DeleteMessageResponse.builder().build();
        }).when(sqs).deleteMessage(any(DeleteMessageRequest.class));
        delivery.consume(message(success(id)));
        var response=mvc.perform(get("/api/ai/jobs/{id}/result",id).header("Authorization","Bearer "+token(judge)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.model").value("mock-worker"))
                .andExpect(jsonPath("$.data.criteriaInsights[0].criteriaName").value("Technical implementation"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("inputRevision","evidence","criteriaId","DO_NOT_EXPOSE","results/","provenance");
        assertThat(scores.count()).isEqualTo(before);
    }
    @Test void duplicateRedeliveryAndLateDispatchPreserveTerminalState() throws Exception {
        UUID id=create();var completion=success(id);delivery.consume(message(completion));delivery.consume(message(completion));
        delivery.dispatchOne();
        assertThat(store.find(id).orElseThrow().status()).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("select count(*) from AiProcessedEvent",Integer.class)).isEqualTo(1);
        verify(sqs,times(2)).deleteMessage(any(DeleteMessageRequest.class));
    }
    @Test void committedDuplicateCanBeAcknowledgedWhenS3IsTemporarilyUnavailable() throws Exception {
        UUID id=create();var completion=success(id);delivery.consume(message(completion));clearInvocations(storage,sqs);
        doThrow(new IllegalStateException("temporary S3 outage")).when(storage).read(anyString(),nullable(String.class),anyLong());
        delivery.consume(message(completion));
        verify(storage,never()).read(anyString(),nullable(String.class),anyLong());verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
    }
    @Test void reusedMessageIdentityWithChangedPayloadIsRejectedBeforeS3() throws Exception {
        UUID id=create();var completion=success(id);delivery.consume(message(completion));clearInvocations(storage,sqs);
        completion.put("status","FAILED");
        assertThatThrownBy(() -> delivery.consume(message(completion))).hasMessage("WORKER_RESULT_CONFLICT");
        verify(storage,never()).read(anyString(),nullable(String.class),anyLong());verify(sqs,never()).deleteMessage(any(DeleteMessageRequest.class));
    }
    @Test void conflictingCompletionDoesNotAckOrOverwrite() throws Exception {
        UUID id=create();delivery.consume(message(success(id)));clearInvocations(sqs);
        var failure=MockWorkerOutput.completion(JobJson.parse(store.find(id).orElseThrow().requestJson()),false);
        assertThatThrownBy(() -> delivery.consume(message(failure))).isInstanceOf(JobException.class);
        verify(sqs,never()).deleteMessage(any(DeleteMessageRequest.class));assertThat(store.find(id).orElseThrow().status()).isEqualTo("SUCCEEDED");
    }
    @Test void badRevisionIsRejectedBeforeReadingS3() throws Exception {
        UUID id=create();var body=success(id).put("inputRevision","b".repeat(64));clearInvocations(storage);
        assertThatThrownBy(() -> delivery.consume(message(body))).isInstanceOf(JobException.class);
        verify(storage,never()).read(anyString(),nullable(String.class),anyLong());verify(sqs,never()).deleteMessage(any(DeleteMessageRequest.class));
    }
    @Test void badChecksumCannotFailTheLegitimateJob() throws Exception {
        UUID id=create();var body=success(id);((ObjectNode)body.path("result")).put("sha256","b".repeat(64));
        assertThatThrownBy(() -> delivery.consume(message(body))).isInstanceOf(JobException.class);
        assertThat(store.find(id).orElseThrow().status()).isEqualTo("PENDING_DISPATCH");verify(sqs,never()).deleteMessage(any(DeleteMessageRequest.class));
    }
    @Test void staleOutputAndUnknownCriterionAndInvalidEvidenceAreRejected() throws Exception {
        UUID id=create();var job=store.find(id).orElseThrow();var request=JobJson.parse(job.requestJson());
        var original=MockWorkerOutput.output(request,source,JobJson.parse(job.rubricJson()));
        var stale=original.deepCopy().put("commitSha","c".repeat(40));
        assertThatThrownBy(() -> validator.validate(withOutput(job,stale,"stale").toString(),job)).isInstanceOf(JobException.class);
        var badCriteria=original.deepCopy();((ObjectNode)badCriteria.path("insight").path("criteriaInsights").get(0)).put("criteriaId",-1);
        assertThatThrownBy(() -> validator.validate(withOutput(job,badCriteria,"criteria").toString(),job)).isInstanceOf(JobException.class);
        var badEvidence=original.deepCopy();((ObjectNode)badEvidence.path("insight").path("criteriaInsights").get(0).path("evidence").get(0)).put("endLine",999);
        assertThatThrownBy(() -> validator.validate(withOutput(job,badEvidence,"evidence").toString(),job)).isInstanceOf(JobException.class);
    }
    @Test void failedAndPendingResultsHaveSafeErrors() throws Exception {
        UUID id=create();
        mvc.perform(get("/api/ai/jobs/{id}/result",id).header("Authorization","Bearer "+token(judge))).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("RESULT_NOT_READY"));
        var failure=MockWorkerOutput.completion(JobJson.parse(store.find(id).orElseThrow().requestJson()),false);
        delivery.consume(message(failure));
        mvc.perform(get("/api/ai/jobs/{id}/result",id).header("Authorization","Bearer "+token(judge))).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("MOCK_PROCESSING_FAILED"));
    }
    @Test void closedJudgingAllowsOwnHistoryUntilAssignmentRevoked() throws Exception {
        UUID id=create();timer.setStatus("STOPPED");timers.save(timer);
        assertThat(jobs.get(judge.getUserId(),id).jobId()).isEqualTo(id);
        assertThatThrownBy(() -> jobs.create(judge.getUserId(),submission.getSubmissionId())).isInstanceOf(JobException.class).hasMessage("JUDGING_CLOSED");
        assignment.setIsActive(false);assignments.save(assignment);
        assertThatThrownBy(() -> jobs.get(judge.getUserId(),id)).isInstanceOf(JobException.class).hasMessage("JOB_NOT_FOUND");
    }
    @Test void eventScopeRequesterAndCurrentApprovalAreRequired() throws Exception {
        UUID id=create();assertThatThrownBy(() -> jobs.get(other.getUserId(),id)).hasMessage("JOB_NOT_FOUND");
        assertThat(jobs.get(coordinator.getUserId(),id).jobId()).isEqualTo(id);
        var grant=grants.findAllByUser_UserId(coordinator.getUserId()).getFirst();grant.setEventId(null);grants.save(grant);
        assertThatThrownBy(() -> jobs.get(coordinator.getUserId(),id)).hasMessage("JOB_NOT_FOUND");
        judge.setIsApproved(false);users.save(judge);assertThatThrownBy(() -> jobs.get(judge.getUserId(),id)).hasMessage("JOB_NOT_FOUND");
    }
    @Test void realAuthCookieCsrfAndAnonymousAccessAreEnforced() throws Exception {
        mvc.perform(get("/api/ai/jobs/{id}",UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ai/submissions/{id}/jobs",submission.getSubmissionId()).cookie(new Cookie(JwtCookieFactory.COOKIE_NAME,token(judge)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/ai/jobs/{id}",UUID.randomUUID()).header("Authorization","Bearer "+token(participant))).andExpect(status().isForbidden());
    }
    @Test void changedSubmissionDuringSnapshotCreatesNoJobOrOutbox() {
        when(github.collect(anyString())).thenAnswer(call -> {submission.setDescription("changed concurrently");submissions.save(submission);return new GitHubSourceReader.Source("a".repeat(40),source);});
        assertThatThrownBy(() -> jobs.create(judge.getUserId(),submission.getSubmissionId())).hasMessage("SUBMISSION_INPUT_CHANGED");
        assertThat(jdbc.queryForObject("select count(*) from AiJob",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from AiJobOutbox",Integer.class)).isZero();
    }
    @Test void outboxInsertFailureRollsBackJobTogether() {
        jdbc.execute("alter table AiJobOutbox add constraint reject_outbox check (attempts<0)");
        try { assertThatThrownBy(() -> jobs.create(judge.getUserId(),submission.getSubmissionId())).isInstanceOf(RuntimeException.class); }
        finally { jdbc.execute("alter table AiJobOutbox drop constraint reject_outbox"); }
        assertThat(jdbc.queryForObject("select count(*) from AiJob",Integer.class)).isZero();
    }
    @Test void databaseFailureBeforeCommitDoesNotAck() throws Exception {
        UUID id=create();var body=success(id);
        doThrow(new org.springframework.dao.TransientDataAccessResourceException("private SQL detail"))
                .when(store).complete(any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),nullable(String.class));
        assertThatThrownBy(() -> delivery.consume(message(body))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        verify(sqs,never()).deleteMessage(any(DeleteMessageRequest.class));assertThat(store.find(id).orElseThrow().status()).isEqualTo("PENDING_DISPATCH");
    }
    @Test void sendFailureRetainsPendingAndStableMessageIdentity() throws Exception {
        UUID id=create();String original=store.find(id).orElseThrow().requestJson();
        when(sqs.sendMessage(any(SendMessageRequest.class))).thenThrow(SqsException.builder().message("private transport detail").build());
        delivery.dispatchOne();assertThat(store.find(id).orElseThrow().status()).isEqualTo("PENDING_DISPATCH");
        assertThat(jdbc.queryForObject("select payload_json from AiJobOutbox",String.class)).isEqualTo(original);
        assertThat(jdbc.queryForObject("select attempts from AiJobOutbox",Integer.class)).isEqualTo(1);
    }
    @Test void concurrentClaimsHaveSingleOwnerAndExpiredLeaseIsRecoverable() throws Exception {
        create();try(var pool=Executors.newFixedThreadPool(2)) {
            var one=pool.submit(() -> store.claim());var two=pool.submit(() -> store.claim());
            assertThat((one.get().isPresent()?1:0)+(two.get().isPresent()?1:0)).isEqualTo(1);
        }
        jdbc.update("update AiJobOutbox set claimed_until=?",AiJobStore.stamp(Instant.now().minusSeconds(1)));
        assertThat(store.claim()).isPresent();
    }
    @Test void restartingRepositorySeesDurableJobAndDuplicateLedger() throws Exception {
        UUID id=create();var body=success(id);delivery.consume(message(body));
        var restarted=new AiJobStore(jdbc,context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        assertThat(restarted.find(id).orElseThrow().status()).isEqualTo("SUCCEEDED");
        var v=validator.validate(body.toString(),restarted.find(id).orElseThrow());
        assertThat(restarted.complete(id,v.messageId(),v.messageDigest(),v.terminalDigest(),store.find(id).orElseThrow().inputRevision(),v.status(),v.result(),v.error())).isEqualTo(AiJobStore.Completion.DUPLICATE);
    }
    @Test void oldJobCompletionDoesNotModifyNewerJob() throws Exception {
        UUID old=create(),newer=create();delivery.consume(message(success(old)));
        assertThat(store.find(newer).orElseThrow().status()).isEqualTo("PENDING_DISPATCH");
    }
}
