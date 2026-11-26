package com.seal.hackathon.config.seed;

import com.seal.hackathon.config.seed.DemoWaves.Wave;
import com.seal.hackathon.config.seed.DemoWaves.WaveState;
import com.seal.hackathon.dto.request.CreateGuestJudgeRequest;
import com.seal.hackathon.dto.request.StartTimerRequest;
import com.seal.hackathon.dto.request.SubmitRequest;
import com.seal.hackathon.dto.request.SubmitScoresRequest;
import com.seal.hackathon.dto.request.UpdateEventRequest;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.Round;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.repository.JudgeAssignmentRepository;
import com.seal.hackathon.repository.RoundRepository;
import com.seal.hackathon.repository.ScoreRepository;
import com.seal.hackathon.repository.ScoringCriteriaRepository;
import com.seal.hackathon.repository.SubmissionRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TrackRepository;
import com.seal.hackathon.repository.UserRepository;
import com.seal.hackathon.service.HackathonEventService;
import com.seal.hackathon.service.JudgeAssignmentService;
import com.seal.hackathon.service.JudgeScoringCompletenessService;
import com.seal.hackathon.service.LeftoverGroupingService;
import com.seal.hackathon.service.RoundResultService;
import com.seal.hackathon.service.RoundService;
import com.seal.hackathon.service.RoundTimerService;
import com.seal.hackathon.service.ScoringService;
import com.seal.hackathon.service.SubmissionService;
import com.seal.hackathon.service.TeamModerationService;
import com.seal.hackathon.service.TeamTrackAssignmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Walks one event from registration to the final ranking, as the live demo does. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:demo_waves;MODE=MySQL;NON_KEYWORDS=YEAR,VALUE")
@ActiveProfiles("test")
@Transactional
class DemoWavesTest {

    @Autowired DemoWaves waves;
    @Autowired DemoFixtures fixtures;
    @Autowired HackathonEventService lifecycle;
    @Autowired RoundService roundService;
    @Autowired TeamModerationService moderation;
    @Autowired LeftoverGroupingService grouping;
    @Autowired TeamTrackAssignmentService trackDraw;
    @Autowired JudgeAssignmentService judgeService;
    @Autowired RoundTimerService timers;
    @Autowired SubmissionService submissionService;
    @Autowired ScoringService scoringService;
    @Autowired JudgeScoringCompletenessService completeness;
    @Autowired RoundResultService results;
    @Autowired UserRepository users;
    @Autowired TrackRepository tracks;
    @Autowired RoundRepository rounds;
    @Autowired ScoringCriteriaRepository criteria;
    @Autowired TeamEventEntryRepository entries;
    @Autowired TeamMemberRepository members;
    @Autowired JudgeAssignmentRepository judgeAssignments;
    @Autowired SubmissionRepository submissions;
    @Autowired ScoreRepository scores;
    @MockitoBean PasswordEncoder encoder;

    @Test
    void walksOneEventFromRegistrationToTheFinalRanking() {
        when(encoder.encode(anyString())).thenReturn("test hash");
        User coordinator = fixtures.user("coordinator@example.test", "Coordinator", "STAFF", null, 0);
        int actor = coordinator.getUserId();
        LocalDateTime now = LocalDateTime.now();
        HackathonEvent event = fixtures.event("SEAL AWS", "SPRING", 2027, "OPEN", "RANDOM",
                now.minusDays(1), now.plusDays(10), now.plusDays(12), now.plusDays(14));
        int eventId = event.getEventId();
        for (String name : List.of("Track 1", "Track 2", "Track 3")) {
            fixtures.track(event, name);
        }
        Round prelim = fixtures.round(event, 1, "Vòng Loại", false, "PENDING",
                now.plusDays(12), now.plusDays(13), now.plusDays(13), 10);
        Round round = fixtures.round(event, 2, "Chung Kết", false, "PENDING",
                now.plusDays(13), now.plusDays(14), now.plusDays(14), null);

        // A real team, registered through OAuth in the demo.
        User realLeader = fixtures.user("real.leader@example.test", "Real Leader", "FPT_STUDENT", null, 901);
        fixtures.team(event, null, "Real Team", "APPROVED", realLeader, List.of(
                fixtures.user("real.a@example.test", "Real A", "FPT_STUDENT", null, 902),
                fixtures.user("real.b@example.test", "Real B", "FPT_STUDENT", null, 903)));

        assertThat(stateOf(eventId, Wave.REGISTRATION).blockedBy()).contains("exactly one round");
        roundService.deleteRound(eventId, prelim.getRoundId());
        assertThat(stateOf(eventId, Wave.REGISTRATION).blockedBy()).isNull();
        assertThat(stateOf(eventId, Wave.SUBMISSIONS).blockedBy()).contains("registration wave first");

        // Wave 1: registration.
        var registration = waves.run(eventId, Wave.REGISTRATION, actor).added();
        assertThat(registration).containsEntry("teams", 12).containsEntry("judges", 3).containsEntry("mentors", 3)
                .containsEntry("criteria", 5).doesNotContainKey("tracks").doesNotContainKey("rounds");
        assertThat(stateOf(eventId, Wave.REGISTRATION).done()).isTrue();
        assertThatThrownBy(() -> waves.run(eventId, Wave.REGISTRATION, actor))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("already run");
        assertThat(rounds.findById(round.getRoundId()).orElseThrow().getIsFinal()).isTrue();
        assertThat(criteria.findAllByRound_RoundIdOrderByOrderNumber(round.getRoundId())).hasSize(5);
        List<User> demoUsers = users.findAll().stream().filter(DemoWaves::isDemo).toList();
        assertThat(demoUsers).isNotEmpty().allSatisfy(user -> {
            assertThat(user.getPasswordHash()).isNull();
            assertThat(user.getEmail()).endsWith("+event" + eventId + "@seal-demo.invalid");
        });
        assertThat(users.findGroupableFreeAgents(eventId)).filteredOn(DemoWaves::isDemo).hasSize(2);

        // Live: approve, close registration, group leftovers, draw tracks, add the live judge, start.
        entries.findAllByEvent_EventIdAndStatus(eventId, "PENDING")
                .forEach(entry -> moderation.approveTeam(entry.getTeam().getTeamId()));
        setStatus(eventId, "SETUP");
        grouping.commit(eventId, actor, null);
        trackDraw.drawTracks(eventId, false, actor, null);
        CreateGuestJudgeRequest guest = new CreateGuestJudgeRequest();
        guest.setFullName("Live Judge");
        guest.setEmail("live.judge@example.test");
        guest.setPassword("Live@12345");
        guest.setRoundId(round.getRoundId());
        judgeService.createGuestJudge(guest, actor);
        User liveJudge = users.findByEmail("live.judge@example.test").orElseThrow();
        setStatus(eventId, "IN_PROGRESS");

        // Wave 2: the contest is running, the real team submits live.
        assertThat(stateOf(eventId, Wave.SUBMISSIONS).blockedBy()).contains("contest timer");
        timers.start(actor, eventId, round.getRoundId(), "CONTEST", timer(3600));
        SubmitRequest submit = new SubmitRequest();
        submit.setRoundId(round.getRoundId());
        submit.setRepoUrl("https://github.com/expressjs/cors");
        submissionService.submit(realLeader.getUserId(), submit);
        long approvedTeams = entries.findAllByEvent_EventIdAndStatus(eventId, "APPROVED").size();
        var submitted = waves.run(eventId, Wave.SUBMISSIONS, actor).added();
        assertThat(submitted).containsEntry("submissions", (int) approvedTeams - 1).containsEntry("realTeamsLeft", 0);
        assertThat(submissions.findAllByRound_RoundId(round.getRoundId())).hasSize((int) approvedTeams);

        // Wave 3: judging is running. Only the live judge's score for the real team is left.
        timers.stop(actor, eventId, round.getRoundId(), "CONTEST");
        assertThat(stateOf(eventId, Wave.SCORES).blockedBy()).contains("judging timer");
        timers.start(actor, eventId, round.getRoundId(), "JUDGING", timer(3600));
        var scored = waves.run(eventId, Wave.SCORES, actor).added();
        assertThat(scored).containsEntry("leftForLiveJudging", 1)
                .containsEntry("scoredSubmissionJudgePairs", (int) approvedTeams * 4 - 1);
        assertThatThrownBy(() -> completeness.assertRoundComplete(round.getRoundId()))
                .hasMessageContaining("Real Team");

        var realSubmission = submissions.findAllByRound_RoundId(round.getRoundId()).stream()
                .filter(s -> s.getTeam().getName().equals("Real Team")).findFirst().orElseThrow();
        SubmitScoresRequest request = new SubmitScoresRequest();
        request.setSubmissionId(realSubmission.getSubmissionId());
        request.setScores(criteria.findAllByRound_RoundIdOrderByOrderNumber(round.getRoundId()).stream().map(c -> {
            var entry = new SubmitScoresRequest.ScoreEntry();
            entry.setCriteriaId(c.getCriteriaId());
            entry.setValue(BigDecimal.valueOf(8));
            return entry;
        }).toList());
        scoringService.submitScores(liveJudge.getUserId(), request);

        completeness.assertRoundComplete(round.getRoundId());
        assertThat(results.finalizeRound(eventId, round.getRoundId(), actor)).hasSize((int) approvedTeams);
        assertThat(waves.list()).singleElement().satisfies(event1 ->
                assertThat(event1.waves()).allSatisfy(state -> assertThat(state.done()).isTrue()));
    }

    @Test
    void createsTheStructureForAnEmptyEvent() {
        when(encoder.encode(anyString())).thenReturn("test hash");
        User coordinator = fixtures.user("coordinator2@example.test", "Coordinator", "STAFF", null, 0);
        LocalDateTime now = LocalDateTime.now();
        HackathonEvent event = fixtures.event("Empty", "SPRING", 2027, "OPEN", "RANDOM",
                now.minusDays(1), now.plusDays(10), now.plusDays(12), now.plusDays(14));

        var added = waves.run(event.getEventId(), Wave.REGISTRATION, coordinator.getUserId()).added();

        assertThat(added).containsEntry("tracks", 4).containsEntry("rounds", 1).containsEntry("teams", 16);
        assertThat(tracks.findAllByEvent_EventId(event.getEventId())).hasSize(4);
        var only = rounds.findAllByEvent_EventIdOrderByOrderNumber(event.getEventId());
        assertThat(only).singleElement().satisfies(r -> {
            assertThat(r.getIsFinal()).isTrue();
            assertThat(r.getName()).isEqualTo(DemoWaves.ROUND_NAME);
        });
        assertThat(judgeAssignments.findAllByRound_RoundIdAndIsActiveTrue(only.getFirst().getRoundId()))
                .hasSize(3).allSatisfy(a -> assertThat(a.getTrack()).isNull());
    }

    @Test
    void leavesDraftEventsAlone() {
        User coordinator = fixtures.user("coordinator3@example.test", "Coordinator", "STAFF", null, 0);
        LocalDateTime now = LocalDateTime.now();
        HackathonEvent draft = fixtures.event("Draft", "FALL", 2027, "DRAFT", "RANDOM",
                now.minusDays(1), now.plusDays(10), now.plusDays(12), now.plusDays(14));

        assertThat(waves.list()).noneMatch(e -> e.eventId() == draft.getEventId());
        assertThatThrownBy(() -> waves.run(draft.getEventId(), Wave.REGISTRATION, coordinator.getUserId()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("DRAFT");
        assertThat(tracks.findAllByEvent_EventId(draft.getEventId())).isEmpty();
    }

    private WaveState stateOf(int eventId, Wave wave) {
        return waves.list().stream().filter(e -> e.eventId() == eventId).findFirst().orElseThrow()
                .waves().stream().filter(s -> s.wave() == wave).findFirst().orElseThrow();
    }

    private void setStatus(int eventId, String status) {
        UpdateEventRequest request = new UpdateEventRequest();
        request.setStatus(status);
        lifecycle.updateEvent(eventId, request);
    }

    private static StartTimerRequest timer(int seconds) {
        StartTimerRequest request = new StartTimerRequest();
        request.setDurationSeconds(seconds);
        return request;
    }
}
