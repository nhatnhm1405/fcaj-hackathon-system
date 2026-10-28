package com.fcaj.hackathon.config.seed;

import com.fcaj.hackathon.entity.*;
import com.fcaj.hackathon.repository.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:demo_grant;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=YEAR,VALUE",
        "app.bootstrap.admin.email=demo-admin@test.invalid"})
@ActiveProfiles("test")
@Transactional
class DemoEventGrantTest {
    @Autowired DemoScenario scenario;
    @Autowired DemoFixtures fixtures;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserEventRoleRepository grants;
    @Autowired HackathonEventRepository events;
    @Autowired com.fcaj.hackathon.service.TrackProblemService problems;
    @Autowired SubmissionRepository submissions;
    @Autowired TeamRepository teams;
    @Autowired RoundRepository rounds;
    @Autowired RoundTimerRepository timers;
    @Autowired com.fcaj.hackathon.service.RoundTimerService timerService;
    @ParameterizedTest @ValueSource(strings={"S1","S25","S3"})
    void seededCoordinatorReceivesOnlyItsEventGrantAndHelperIsIdempotent(String mode) {
        for(String name:new String[]{"SYSTEM_ADMIN","EVENT_COORDINATOR","JUDGE","MENTOR"}) {
            if(roles.findByRoleName(name).isEmpty())roles.save(Role.builder().roleName(name).build());
        }
        User admin=users.save(User.builder().email("demo-admin@test.invalid").fullName("Admin").userType("STAFF").isApproved(true).build());
        fixtures.grant(admin,"SYSTEM_ADMIN",null);
        scenario.seed(mode);
        User coordinator=users.findByEmail(DemoScenario.COORDINATOR_EMAIL).orElseThrow();
        var owned=events.findAll().stream().filter(e->DemoScenario.DEMO_EVENT_NAME.equals(e.getName())).findFirst().orElseThrow();
        fixtures.grant(coordinator,"EVENT_COORDINATOR",owned.getEventId());
        var eventGrants=grants.findAllByUser_UserId(coordinator.getUserId()).stream().filter(g->g.getEventId()!=null).toList();
        assertThat(eventGrants).hasSize(1);
        assertThat(eventGrants.getFirst().getEventId()).isEqualTo(owned.getEventId());
        assertThat(eventGrants.getFirst().getRole().getRoleName()).isEqualTo("EVENT_COORDINATOR");
        assertThat(problems.listProblemsForEvent(owned.getEventId(),coordinator.getUserId())).hasSize(4);
        var alien=events.save(HackathonEvent.builder().name("Other").season("FALL").year(2027).status("IN_PROGRESS")
                .startDate(java.time.LocalDateTime.now()).endDate(java.time.LocalDateTime.now().plusDays(1)).build());
        assertThatThrownBy(()->problems.listProblemsForEvent(alien.getEventId(),coordinator.getUserId())).isInstanceOf(com.fcaj.hackathon.exception.ForbiddenException.class);
        var round=rounds.findAll().stream().filter(r->r.getEvent().getEventId().equals(owned.getEventId())).findFirst().orElseThrow();
        var submission=submissions.findAll().stream().filter(s->s.getRound().getRoundId().equals(round.getRoundId())).findFirst()
                .orElseGet(()->submissions.save(Submission.builder().team(teams.findAll().getFirst()).round(round).submittedBy(coordinator)
                        .repoUrl("https://github.com/example/demo").status("SUBMITTED").build()));
        var access=new com.fcaj.hackathon.aijob.JobAccess(users,submissions,timerService);
        if("S3".equals(mode)) {
            assertThatThrownBy(()->access.create(coordinator.getUserId(),submission.getSubmissionId())).hasMessage("JUDGING_CLOSED");
            assertThat(owned.getStatus()).isEqualTo("COMPLETED");
        } else {
            var timer=timers.findByRound_RoundIdAndPhase(round.getRoundId(),"JUDGING").orElseGet(()->RoundTimer.builder().round(round).phase("JUDGING").build());
            timer.setStatus("RUNNING");timer.setEndsAt(java.time.LocalDateTime.now().plusHours(1));timers.save(timer);
            assertThat(access.create(coordinator.getUserId(),submission.getSubmissionId()).getSubmissionId()).isEqualTo(submission.getSubmissionId());
        }
        assertThat(grants.existsByUser_UserIdAndRole_RoleNameAndEventId(coordinator.getUserId(),"EVENT_COORDINATOR",owned.getEventId()+10000)).isFalse();
    }
}
