package com.fcaj.hackathon.mockdemo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcaj.hackathon.entity.*;
import com.fcaj.hackathon.repository.*;
import com.fcaj.hackathon.security.*;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
        "spring.datasource.url=jdbc:h2:mem:mock_core;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=YEAR,VALUE",
        "app.ai.jobs.mock-store-url=jdbc:h2:mem:mock_api;DB_CLOSE_DELAY=-1",
        "app.ai.jobs.mock-worker.enabled=false"})
@ActiveProfiles({"test","mock-demo"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class LocalMockJobApiTest {
    @Autowired WebApplicationContext context;
    @Autowired LocalMockJobService jobs;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserEventRoleRepository grants;
    @Autowired HackathonEventRepository events;
    @Autowired RoundRepository rounds;
    @Autowired RoundTimerRepository timers;
    @Autowired TeamRepository teams;
    @Autowired TeamEventEntryRepository entries;
    @Autowired TrackRepository tracks;
    @Autowired SubmissionRepository submissions;
    @Autowired JudgeAssignmentRepository assignments;
    @Autowired ScoreRepository scores;
    @Autowired JwtService jwt;
    private MockMvc mvc;
    private User judge,other,coordinator,participant;
    private Submission submission;
    private RoundTimer timer;
    private JudgeAssignment assignment;

    @BeforeEach void setup() {
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var mockJdbc=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:mock_api;DB_CLOSE_DELAY=-1","sa",""));
        mockJdbc.update("DELETE FROM MockAiProcessedResult");
        mockJdbc.update("DELETE FROM MockAiJob");
        var now=LocalDateTime.now();
        var event=events.save(HackathonEvent.builder().name("Mock event").season("Fall").year(2026)
                .status("IN_PROGRESS").startDate(now.minusDays(1)).endDate(now.plusDays(1)).build());
        judge=user("STAFF"); other=user("STAFF"); coordinator=user("STAFF"); participant=user("FPT_STUDENT");
        grant(judge,"JUDGE",event.getEventId()); grant(other,"JUDGE",event.getEventId());
        grant(coordinator,"EVENT_COORDINATOR",event.getEventId());
        var round=rounds.save(Round.builder().event(event).name("Mock round").orderNumber(1).status("ACTIVE")
                .startTime(now.minusHours(1)).endTime(now.plusHours(1)).submissionDeadline(now.minusMinutes(1)).build());
        var track=tracks.save(Track.builder().event(event).name("Mock track").build());
        var team=teams.save(Team.builder().name("DO_NOT_EXPOSE_TEAM").build());
        entries.save(TeamEventEntry.builder().team(team).event(event).track(track).status("APPROVED").build());
        submission=submissions.save(Submission.builder().team(team).round(round).submittedBy(participant)
                .repoUrl("https://github.com/DO_NOT_EXPOSE_OWNER/repository").build());
        assignment=assignments.save(JudgeAssignment.builder().judge(judge).round(round).track(track).build());
        assignments.save(JudgeAssignment.builder().judge(other).round(round).track(track).build());
        timer=timers.save(RoundTimer.builder().round(round).phase("JUDGING").status("RUNNING")
                .durationSeconds(600).startedAt(now).endsAt(now.plusMinutes(10)).build());
    }
    private User user(String type) {
        return users.save(User.builder().email(UUID.randomUUID()+"@example.test").fullName("DO_NOT_EXPOSE_PERSON")
                .userType(type).isApproved(true).build());
    }
    private void grant(User user,String role,int event) {
        grants.save(UserEventRole.builder().user(user).role(roles.findByRoleName(role).orElseThrow()).eventId(event).build());
    }
    private String token(User user) {
        var principal=new UserPrincipal(users.findByIdWithRoles(user.getUserId()).orElseThrow());
        return jwt.generateToken(principal,user.getUserId(),principal.getAuthorities().stream().map(a->a.getAuthority()).toList());
    }
    private UUID create(User user) throws Exception {
        var response=mvc.perform(post("/api/ai/submissions/{id}/jobs",submission.getSubmissionId())
                .header("Authorization","Bearer "+token(user))).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.mode").value("LOCAL_MOCK"))
                .andExpect(jsonPath("$.data.status").value("PENDING_DISPATCH")).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("DO_NOT_EXPOSE", "requesterId", "inputRevision", "repoUrl");
        return UUID.fromString(new ObjectMapper().readTree(response).path("data").path("jobId").asText());
    }

    @Test void completeRoundTripUsesRealAuthAndDoesNotWriteScores() throws Exception {
        long before=scores.count(); UUID id=create(judge);
        var worker=new LocalMockJobWorker(jobs,"SUCCEEDED");
        worker.tick();
        mvc.perform(get("/api/ai/jobs/{id}",id).header("Authorization","Bearer "+token(judge)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("QUEUED"));
        worker.tick();
        mvc.perform(get("/api/ai/jobs/{id}/result",id).header("Authorization","Bearer "+token(judge)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.model").value("LOCAL_MOCK_V1"))
                .andExpect(jsonPath("$.data.criteriaInsights").isEmpty());
        assertThat(scores.count()).isEqualTo(before);
    }

    @Test void historySurvivesClosedTimerButNotRevokedAssignment() throws Exception {
        UUID id=create(judge);
        timer.setStatus("STOPPED"); timers.save(timer);
        mvc.perform(get("/api/ai/jobs/{id}",id).header("Authorization","Bearer "+token(judge))).andExpect(status().isOk());
        mvc.perform(post("/api/ai/submissions/{id}/jobs",submission.getSubmissionId()).header("Authorization","Bearer "+token(judge)))
                .andExpect(status().isConflict());
        assignment.setIsActive(false); assignments.save(assignment);
        mvc.perform(get("/api/ai/jobs/{id}",id).header("Authorization","Bearer "+token(judge))).andExpect(status().isNotFound());
    }

    @Test void otherJudgeCannotReadAndCoordinatorCanReadOnlyAssignedEvent() throws Exception {
        UUID id=create(judge);
        mvc.perform(get("/api/ai/jobs/{id}",id).header("Authorization","Bearer "+token(other))).andExpect(status().isNotFound());
        mvc.perform(get("/api/ai/jobs/{id}",id).header("Authorization","Bearer "+token(coordinator))).andExpect(status().isOk());
        var grant=grants.findAllByUser_UserId(coordinator.getUserId()).getFirst();
        grant.setEventId(null); grants.save(grant);
        mvc.perform(get("/api/ai/jobs/{id}",id).header("Authorization","Bearer "+token(coordinator))).andExpect(status().isNotFound());
        mvc.perform(post("/api/ai/submissions/{id}/jobs",submission.getSubmissionId()).header("Authorization","Bearer "+token(coordinator)))
                .andExpect(status().isForbidden());
    }

    @Test void pendingAndFailedResultsReturnSafeConflictCodes() throws Exception {
        UUID id=create(coordinator);
        mvc.perform(get("/api/ai/jobs/{id}/result",id).header("Authorization","Bearer "+token(coordinator)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("RESULT_NOT_READY"));
        var work=jobs.pendingWork().getFirst();
        assertThat(work.jobId()).isEqualTo(id);
        jobs.complete(id,UUID.randomUUID(),work.inputRevision(),false);
        mvc.perform(get("/api/ai/jobs/{id}/result",id).header("Authorization","Bearer "+token(coordinator)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("MOCK_PROCESSING_FAILED"));
    }

    @Test void anonymousParticipantInvalidUuidAndCookieCsrfAreEnforced() throws Exception {
        mvc.perform(get("/api/ai/jobs/{id}",UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ai/jobs/{id}",UUID.randomUUID()).header("Authorization","Bearer "+token(participant))).andExpect(status().isForbidden());
        mvc.perform(get("/api/ai/jobs/not-a-uuid").header("Authorization","Bearer "+token(judge))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/ai/submissions/{id}/jobs",submission.getSubmissionId())
                .cookie(new Cookie(JwtCookieFactory.COOKIE_NAME,token(judge)))).andExpect(status().isForbidden());
    }

    @Test void jobBeansAndWorkerAreIsolatedFromCoreDatasource() {
        assertThat(context.getBeansOfType(javax.sql.DataSource.class)).hasSize(1);
        assertThat(context.getBeansOfType(LocalMockJobWorker.class)).isEmpty();
    }
}
