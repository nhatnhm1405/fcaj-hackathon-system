package com.seal.hackathon.aijob;

import com.fasterxml.jackson.databind.JsonNode;
import com.seal.hackathon.config.BootstrapAdminSeeder;
import com.seal.hackathon.testsupport.CookieSessionClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import com.seal.hackathon.storage.ObjectKeys;
import com.seal.hackathon.storage.ObjectStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/** A fresh synthetic lab, real controllers/security/services; only external IO is mocked. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:api_rehearsal;DB_CLOSE_DELAY=-1;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;NON_KEYWORDS=YEAR,VALUE",
        "app.bootstrap.admin.enabled=true", "app.bootstrap.admin.email=rehearsal-admin@example.test",
        "app.bootstrap.admin.password=Synthetic-Only-482!", "app.seed.scenario=NONE", "app.jwt.cookie.secure=true",
        "app.ai.jobs.enabled=true", "app.ai.jobs.delivery.enabled=false", "app.aws.region=ap-southeast-1",
        "app.ai.jobs.jobs-queue-url=https://sqs.ap-southeast-1.amazonaws.com/123456789012/rehearsal-jobs",
        "app.ai.jobs.results-queue-url=https://sqs.ap-southeast-1.amazonaws.com/123456789012/rehearsal-results"})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApiSmokeRehearsalTest {
    private static final String PASSWORD = "Synthetic-Only-482!";
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired BootstrapAdminSeeder bootstrap;
    @Autowired JobDelivery delivery;
    @Autowired javax.sql.DataSource dataSource;
    @MockitoBean ObjectStorage storage;
    @MockitoBean GitHubSourceReader github;
    @MockitoBean SqsClient sqs;
    private MockMvc mvc;
    private final List<Map<String, Object>> steps = new ArrayList<>();
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Test void provisionsThroughCookieCsrfApisAndCompletesIndependentMockJobs() throws Exception {
        // H2 ORM creates core tables; JDBC-only V2 tables are installed before API use.
        // Real V1/V2/V3 + least-privilege startup are separately tested in the MySQL lab.
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V2__create_ai_job_delivery.sql")).execute(dataSource);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        assertThat(jdbc.queryForObject("select count(*) from `User`", Integer.class)).isEqualTo(1);
        String originalHash = jdbc.queryForObject("select password_hash from `User`", String.class);
        bootstrap.run();
        assertThat(jdbc.queryForObject("select password_hash from `User`", String.class)).isEqualTo(originalHash);
        assertThat(jdbc.queryForObject("select count(*) from `User`", Integer.class)).isEqualTo(1);
        externalAdapters();

        CookieSessionClient admin = login("rehearsal-admin@example.test");
        // Regression: restoring JWT auth must not clear CSRF or create a session login.
        String xsrf = admin.cookie("XSRF-TOKEN").getValue();
        call(admin, "GET", "/api/auth/me", null, 200);
        call(admin, "GET", "/api/csrf", null, 204);
        assertThat(admin.cookie("XSRF-TOKEN").getValue()).isEqualTo(xsrf);
        assertRequestOnlySession(admin);
        var today = LocalDate.now();
        String season = today.getMonthValue() <= 4 ? "SPRING" : today.getMonthValue() <= 8 ? "SUMMER" : "FALL";
        // All dates remain in today's season/year, including the last day of a season.
        String day = today.toString();
        int event = id(call(admin, "POST", "/api/events", Map.of(
                "name", "SEAL API rehearsal " + UUID.randomUUID(), "season", season, "year", today.getYear(),
                "registrationStart", day + "T00:00:00", "registrationEnd", day + "T01:00:00",
                "startDate", day + "T02:00:00", "endDate", day + "T23:59:59", "trackSelectionMode", "SELF_SELECT"), 200), "eventId");
        int coordinatorId = account(admin, "coordinator", "STAFF");
        grant(admin, coordinatorId, "EVENT_COORDINATOR", event);
        CookieSessionClient coordinator = login("coordinator@example.test");
        int judgeId = account(admin, "judge", "STAFF");
        int otherId = account(admin, "other-judge", "STAFF");
        grant(admin, otherId, "JUDGE", event);
        int globalId = account(admin, "global-coordinator", "STAFF");
        Map<String, Object> globalGrant = new LinkedHashMap<>();
        globalGrant.put("userId", globalId); globalGrant.put("roleName", "EVENT_COORDINATOR"); globalGrant.put("eventId", null);
        call(admin, "POST", "/api/admin/roles/grant", globalGrant, 201);
        CookieSessionClient global = login("global-coordinator@example.test");

        String base = "/api/events/" + event;
        int track = id(call(coordinator, "POST", base + "/tracks", Map.of("name", "Rehearsal track"), 200), "trackId");
        int round = id(call(coordinator, "POST", base + "/rounds", Map.of(
                "name", "Rehearsal round", "orderNumber", 1, "startTime", day + "T02:00:00",
                "endTime", day + "T23:59:59", "submissionDeadline", day + "T23:00:00", "isFinal", false), 200), "roundId");
        call(coordinator, "PUT", base, Map.of("status", "OPEN"), 200);
        List<Integer> teamIds = new ArrayList<>();
        List<Integer> rosterUserIds = new ArrayList<>();
        List<CookieSessionClient> leaders = new ArrayList<>();
        for (int team = 0; team < 2; team++) {
            List<CookieSessionClient> roster = new ArrayList<>();
            List<Integer> userIds = new ArrayList<>();
            for (int member = 0; member < 3; member++) {
                String name = "team" + team + "-member" + member;
                userIds.add(account(admin, name, "EXTERNAL_STUDENT"));
                rosterUserIds.add(userIds.getLast());
                roster.add(login(name + "@example.test"));
            }
            CookieSessionClient leader = roster.getFirst(); leaders.add(leader);
            int teamId = id(call(leader, "POST", "/api/teams", Map.of("eventId", event, "name", "Rehearsal team " + team), 200), "teamId");
            teamIds.add(teamId);
            // Roster can be assembled while PENDING; SETUP must reject unresolved teams.
            call(coordinator, "PUT", base, Map.of("status", "SETUP"), 400);
            for (int member = 1; member < 3; member++) {
                int invite = id(call(leader, "POST", "/api/invites/teams/" + teamId,
                        Map.of("invitedUserId", userIds.get(member)), 200), "inviteId");
                call(roster.get(member), "PUT", "/api/invites/" + invite + "/accept", null, 200);
            }
            call(coordinator, "PUT", "/api/teams/" + teamId + "/approve", null, 200);
        }
        call(coordinator, "PUT", base, Map.of("status", "SETUP"), 200);
        call(coordinator, "PUT", base, Map.of("status", "IN_PROGRESS"), 400); // Unassigned teams.
        for (int team = 0; team < 2; team++)
            call(leaders.get(team), "PUT", "/api/teams/" + teamIds.get(team) + "/track", Map.of("trackId", track), 200);
        call(coordinator, "PUT", base, Map.of("status", "IN_PROGRESS"), 200);
        String roundBase = base + "/rounds/" + round;
        call(coordinator, "POST", roundBase + "/criteria", Map.of(
                "name", "Implementation", "weight", 1, "maxScore", 10, "orderNumber", 1), 200);
        call(coordinator, "POST", "/api/coordinator/assignments/judges",
                Map.of("judgeUserId", judgeId, "roundId", round, "trackId", track), 201);
        int assignmentId = call(coordinator, "GET", "/api/coordinator/assignments/judges?eventId=" + event, null, 200)
                .path("data").get(0).path("id").asInt();
        assertThat(assignmentId).isPositive();
        CookieSessionClient judge = login("judge@example.test");
        CookieSessionClient other = login("other-judge@example.test");
        String contest = roundBase + "/timer/CONTEST";
        String judging = roundBase + "/timer/JUDGING";
        call(coordinator, "POST", contest + "/start", Map.of("durationSeconds", 600), 200);
        int submission = id(call(leaders.getFirst(), "POST", "/api/submissions", Map.of(
                "roundId", round, "repoUrl", "https://github.com/example/seal-rehearsal",
                "description", "Synthetic API rehearsal"), 200), "submissionId");
        call(coordinator, "POST", judging + "/start", Map.of("durationSeconds", 600), 400);
        call(coordinator, "POST", contest + "/stop", null, 200);
        call(coordinator, "POST", judging + "/start", Map.of("durationSeconds", 600), 200);

        String create = "/api/ai/submissions/" + submission + "/jobs";
        call(admin, "POST", create, null, 403); // Admin role alone is insufficient.
        call(global, "POST", create, null, 403); // Null event grant is insufficient.
        call(other, "POST", create, null, 403); // Role is insufficient without assignment.
        var noCsrf = judge.perform(request(HttpMethod.POST, create)).getResponse();
        assertThat(noCsrf.getStatus()).isEqualTo(403);
        steps.add(Map.of("method", "POST", "path", create, "http", 403, "case", "missing-csrf"));
        long scoreCount = jdbc.queryForObject("select count(*) from Score", Long.class);
        List<String> jobIds = new ArrayList<>();
        for (boolean success : List.of(true, false)) {
            String job = call(judge, "POST", create, null, 202).path("data").path("jobId").asText();
            jobIds.add(job);
            call(judge, "GET", "/api/ai/jobs/" + job + "/result", null, 409);
            clearInvocations(sqs);
            delivery.dispatchOne();
            var dispatched = org.mockito.ArgumentCaptor.forClass(SendMessageRequest.class);
            verify(sqs).sendMessage(dispatched.capture());
            clearInvocations(sqs);
            AwsMockWorkerMain.process(Message.builder().body(dispatched.getValue().messageBody()).receiptHandle("synthetic-job").build(),
                    storage, sqs, "jobs", "results", success);
            var completed = org.mockito.ArgumentCaptor.forClass(SendMessageRequest.class);
            var order = inOrder(sqs);
            order.verify(sqs).sendMessage(completed.capture());
            order.verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
            var message = Message.builder().body(completed.getValue().messageBody()).receiptHandle("synthetic-result").build();
            delivery.consume(message); delivery.consume(message);
            assertThat(call(judge, "GET", "/api/ai/jobs/" + job, null, 200).path("data").path("status").asText())
                    .isEqualTo(success ? "SUCCEEDED" : "FAILED");
            var result = call(judge, "GET", "/api/ai/jobs/" + job + "/result", null, success ? 200 : 409);
            if (success) assertThat(result.toString()).doesNotContain("inputRevision", "requesterId", "criteriaId", "provenance", "evidence");
            else assertThat(result.path("message").asText()).isEqualTo("MOCK_PROCESSING_FAILED");
            call(other, "GET", "/api/ai/jobs/" + job, null, 404);
        }
        call(coordinator, "POST", judging + "/stop", null, 200);
        assertThat(call(judge, "POST", create, null, 409).path("message").asText()).isEqualTo("JUDGING_CLOSED");
        call(judge, "GET", "/api/ai/jobs/" + jobIds.getFirst() + "/result", null, 200);
        call(admin, "DELETE", "/api/admin/roles/revoke", Map.of("userId", judgeId, "roleName", "JUDGE", "eventId", event), 200);
        call(judge, "GET", "/api/ai/jobs/" + jobIds.getFirst(), null, 403); // All eligible authorities revoked.
        MockHttpSession logoutSession = other.session();
        call(other, "POST", "/api/auth/logout", null, 200);
        call(other, "GET", "/api/auth/me", null, 401);
        if (!logoutSession.isInvalid()) assertThat(other.session()).isSameAs(logoutSession);
        assertRequestOnlySession(other);
        assertThat(jdbc.queryForObject("select count(*) from Score", Long.class)).isEqualTo(scoreCount);
        var evidence = JobJson.object().put("mode", "MOCK").put("executedAt", Instant.now().toString())
                .put("scope", "MockMvc; H2 core ORM plus V2; external GitHub/S3/SQS mocked; no browser or AWS")
                .put("eventId", event).put("trackId", track).put("roundId", round).put("submissionId", submission)
                .put("coordinatorUserId", coordinatorId).put("judgeUserId", judgeId).put("assignmentId", assignmentId)
                .put("sessionContinuityChecked", true).put("logoutWithSessionChecked", true)
                .put("teams", 2).put("membersPerTeam", 3).put("bootstrapPasswordUnchanged", true).put("scoresUnchanged", true);
        evidence.set("teamIds", JobJson.MAPPER.valueToTree(teamIds));
        evidence.set("rosterUserIds", JobJson.MAPPER.valueToTree(rosterUserIds));
        // Entry IDs are internal (not exposed by Team DTOs); this query records IDs only,
        // after all provisioning has been performed through HTTP controllers.
        evidence.set("entryIds", JobJson.MAPPER.valueToTree(jdbc.queryForList(
                "select id from TeamEventEntry where event_id=? order by id", Integer.class, event)));
        evidence.set("jobIds", JobJson.MAPPER.valueToTree(jobIds));
        evidence.set("steps", JobJson.MAPPER.valueToTree(steps));
        Path output = Path.of("target/api-rehearsal/evidence.json");
        Files.createDirectories(output.getParent()); Files.write(output, JobJson.bytes(evidence));
    }

    private void externalAdapters() {
        when(storage.put(anyString(), any(byte[].class), anyString())).thenAnswer(call -> {
            String key = call.getArgument(0); byte[] bytes = call.getArgument(1);
            if (objects.putIfAbsent(key, bytes.clone()) != null) throw new IllegalStateException("immutable object");
            return new ObjectStorage.Ref(key, ObjectKeys.sha256(bytes), bytes.length, call.getArgument(2), null);
        });
        when(storage.read(anyString(), nullable(String.class), anyLong())).thenAnswer(call -> {
            byte[] bytes = Objects.requireNonNull(objects.get(call.getArgument(0)));
            if (bytes.length > (long) call.getArgument(2)) throw new IllegalArgumentException("too large");
            return bytes.clone();
        });
        var source = JobJson.object().put("schemaVersion", "fcaj.ai.source.github.v1").put("fixtureOnly", true).put("commitSha", "b".repeat(40));
        source.putArray("files").addObject().put("path", "src/example.txt").put("content", "Synthetic rehearsal source\n");
        when(github.collect(anyString())).thenReturn(new GitHubSourceReader.Source("b".repeat(40), source));
        when(sqs.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().messageId("synthetic-message").build());
    }

    private int account(CookieSessionClient admin, String name, String type) throws Exception {
        return id(call(admin, "POST", "/api/admin/users", Map.of("email", name + "@example.test", "password", PASSWORD,
                "fullName", "Rehearsal " + name, "userType", type), 201), "userId");
    }
    private void grant(CookieSessionClient admin, int user, String role, int event) throws Exception {
        call(admin, "POST", "/api/admin/roles/grant", Map.of("userId", user, "roleName", role, "eventId", event), 201);
    }
    private int id(JsonNode response, String field) {
        int id = response.path("data").path(field).asInt(); assertThat(id).as(field).isPositive(); return id;
    }
    private CookieSessionClient login(String email) throws Exception {
        CookieSessionClient client = new CookieSessionClient(mvc);
        MockHttpSession session = new MockHttpSession(context.getServletContext());
        session.setAttribute("rehearsal-marker", "synthetic-transient-state");
        client.useSession(session);
        call(client, "GET", "/api/csrf", null, 204);
        var response = call(client, "POST", "/api/auth/login", Map.of("email", email, "password", PASSWORD), 200);
        assertThat(response.path("data").has("token")).isFalse();
        assertThat(client.cookie("seal_auth_token").isHttpOnly()).isTrue();
        assertThat(client.cookie("seal_auth_token").getSecure()).isTrue();
        call(client, "GET", "/api/csrf", null, 204); return client;
    }
    private JsonNode call(CookieSessionClient client, String method, String path, Object body, int expected) throws Exception {
        MockHttpServletRequestBuilder request = request(HttpMethod.valueOf(method), path).secure(true);
        if (!method.equals("GET")) request.header("X-XSRF-TOKEN", client.cookie("XSRF-TOKEN").getValue());
        if (body != null) request.contentType("application/json").content(JobJson.MAPPER.writeValueAsBytes(body));
        MockHttpSession previous = client.session();
        var exchange = client.perform(request);
        var response = exchange.getResponse();
        if (previous != null && !previous.isInvalid())
            assertThat(exchange.getRequest().getSession(false)).isSameAs(previous);
        assertRequestOnlySession(client);
        steps.add(Map.of("method", method, "path", path, "http", response.getStatus()));
        assertThat(response.getStatus()).as(method + " " + path + " cookie names=" + client.cookieNames())
                .isEqualTo(expected);
        return response.getContentAsByteArray().length == 0 ? JobJson.object() : JobJson.parse(response.getContentAsString());
    }
    private void assertRequestOnlySession(CookieSessionClient client) {
        MockHttpSession session = client.session();
        if (session != null) {
            assertThat(session.getAttribute("rehearsal-marker")).isEqualTo("synthetic-transient-state");
            assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
        }
    }

}
