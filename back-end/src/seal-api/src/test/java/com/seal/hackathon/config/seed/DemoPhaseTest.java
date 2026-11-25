package com.seal.hackathon.config.seed;

import com.seal.hackathon.config.seed.DemoScenario.Phase;
import com.seal.hackathon.dto.request.UpdateEventRequest;
import com.seal.hackathon.entity.AuditLog;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.repository.AuditLogRepository;
import com.seal.hackathon.repository.HackathonEventRepository;
import com.seal.hackathon.repository.PrizeRepository;
import com.seal.hackathon.repository.RoundRepository;
import com.seal.hackathon.repository.RoundResultRepository;
import com.seal.hackathon.repository.RoundTimerRepository;
import com.seal.hackathon.repository.ScoringCriteriaRepository;
import com.seal.hackathon.repository.SubmissionRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TrackRepository;
import com.seal.hackathon.repository.UserRepository;
import com.seal.hackathon.service.DemoPhaseService;
import com.seal.hackathon.service.HackathonEventService;
import com.seal.hackathon.service.TrackProblemService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:demo_phases;MODE=MySQL;NON_KEYWORDS=YEAR,VALUE")
@ActiveProfiles("test")
@Transactional
class DemoPhaseTest {

    @TempDir static Path problemDir;

    @DynamicPropertySource
    static void usePrivateTestStorage(DynamicPropertyRegistry registry) {
        registry.add("app.problem.dir", () -> problemDir.toString());
    }

    @Autowired DemoPhaseService demos;
    @Autowired DemoScenario scenario;
    @Autowired AuditLogRepository auditLogs;
    @Autowired TrackProblemService problems;
    @Autowired TeamMemberRepository members;
    @Autowired DemoFixtures fixtures;
    @Autowired HackathonEventService lifecycle;
    @Autowired HackathonEventRepository events;
    @Autowired UserRepository users;
    @Autowired TrackRepository tracks;
    @Autowired RoundRepository rounds;
    @Autowired ScoringCriteriaRepository criteria;
    @Autowired TeamEventEntryRepository teams;
    @Autowired SubmissionRepository submissions;
    @Autowired RoundTimerRepository timers;
    @Autowired RoundResultRepository results;
    @Autowired PrizeRepository prizes;
    @MockitoBean PasswordEncoder encoder;

    @Test
    void opensEveryPhaseWithDataAndPreservesNormalEventSelection() {
        when(encoder.encode(DemoFixtures.DEMO_PASSWORD)).thenReturn("test hash");
        User coordinator = fixtures.user("coordinator@example.test", "Coordinator", "STAFF", null, 0);
        int actorId = coordinator.getUserId();
        for (Phase phase : Phase.values()) {
            int eventId = demos.openPhase(phase, actorId, false);
            assertThat(demos.openPhase(phase, actorId, false)).isEqualTo(eventId);
            var event = lifecycle.getEventById(eventId);
            assertThat(event.getName()).isEqualTo("SEAL AWS");
            assertThat(event.getStatus()).isEqualTo(phase.status());
            assertThat(tracks.findAllByEvent_EventId(eventId)).hasSize(4);
            var eventRounds = rounds.findAllByEvent_EventIdOrderByOrderNumber(eventId);
            assertThat(eventRounds).hasSize(2).allSatisfy(round ->
                    assertThat(criteria.findAllByRound_RoundIdOrderByOrderNumber(round.getRoundId())).hasSize(5));
            var prelim = eventRounds.getFirst();
            var finalRound = eventRounds.getLast();
            if (phase == Phase.OPEN) {
                assertThat(teams.findAllByEvent_EventId(eventId)).hasSize(15);
                assertThat(users.findGroupableFreeAgents(eventId)).hasSize(3);
                assertThat(events.findAllByStatus("OPEN")).isEmpty();
            }
            if (phase == Phase.SETUP) {
                assertThat(teams.findAllByEvent_EventIdAndStatus(eventId, "APPROVED"))
                        .hasSize(15).allSatisfy(team -> assertThat(team.getTrack()).isNotNull());
                assertThat(users.findGroupableFreeAgents(eventId)).isEmpty();
                var start = new UpdateEventRequest();
                start.setStatus("IN_PROGRESS");
                assertThat(lifecycle.updateEvent(eventId, start).getStatus()).isEqualTo("IN_PROGRESS");
                assertThat(demos.openPhase(phase, actorId, false)).isNotEqualTo(eventId);
            }
            if (phase == Phase.CONTEST) {
                assertThat(problems.listProblemsForEvent(eventId, actorId)).hasSize(4).allSatisfy(problem -> {
                    assertThat(problem.isHasProblem()).isTrue();
                    assertThat(problem.isReleased()).isTrue();
                });
                assertThat(submissions.findAllByRound_RoundId(prelim.getRoundId())).hasSize(15);
                assertThat(timers.findByRound_RoundIdAndPhase(prelim.getRoundId(), "CONTEST"))
                        .hasValueSatisfying(timer -> assertThat(timer.getStatus()).isEqualTo("RUNNING"));
            }
            if (phase == Phase.SCORING) {
                assertThat(submissions.findAllByRound_RoundId(finalRound.getRoundId())).hasSize(8);
                assertThat(results.findAllByRound_RoundIdOrderByRankPosition(finalRound.getRoundId())).isEmpty();
            }
            if (phase == Phase.COMPLETED) {
                assertThat(results.findAllByRound_RoundIdOrderByRankPosition(finalRound.getRoundId()))
                        .hasSize(8).allSatisfy(result -> assertThat(result.getIsPublished()).isTrue());
                assertThat(prizes.findAllByEvent_EventIdAndAwardedAtIsNotNullOrderByRankPosition(eventId)).hasSize(3);
            }
        }
        int oldDraft = demos.openPhase(Phase.DRAFT, actorId, false);
        int freshDraft = demos.openPhase(Phase.DRAFT, actorId, true);
        assertThat(freshDraft).isNotEqualTo(oldDraft);
        assertThat(demos.openPhase(Phase.DRAFT, actorId, false)).isEqualTo(freshDraft);
        assertThat(lifecycle.getAllHackathonEvents()).isEmpty();
        assertThat(events.existsByYearAndSeasonIgnoreCaseAndStatusNotIgnoreCase(2026, "SUMMER", "CANCELLED")).isFalse();
        var demo = events.findById(freshDraft).orElseThrow();
        assertThat(events.existsOverlappingActiveEvent(demo.getStartDate(), demo.getEndDate(), null, "CANCELLED")).isFalse();
        assertThat(users.findGroupableFreeAgents(0)).isEmpty();
    }

    @Test
    void reopeningAnOldContestBackfillsMissingProblemsAndPreservesUploadsAndEdits() throws Exception {
        when(encoder.encode(DemoFixtures.DEMO_PASSWORD)).thenReturn("test hash");
        User coordinator = fixtures.user("contest@example.test", "Coordinator", "STAFF", null, 0);
        int actorId = coordinator.getUserId();
        int eventId = scenario.seedPhase(Phase.CONTEST, coordinator);
        events.getReferenceById(eventId).setName("SEAL Summer Demo — CONTEST");
        auditLogs.save(AuditLog.builder().actor(coordinator).action(HackathonEventRepository.DEMO_PHASE_ACTION)
                .targetType("EVENT").targetId(eventId).reason(Phase.CONTEST.name()).build());

        var originalTracks = problems.listProblemsForEvent(eventId, actorId);
        int editedTrack = originalTracks.getFirst().getTrackId();
        int backfilledTrack = originalTracks.get(1).getTrackId();
        problems.uploadProblem(actorId, eventId, editedTrack,
                new MockMultipartFile("file", "custom.pdf", "application/pdf", new byte[] {1}));

        assertThat(demos.openPhase(Phase.CONTEST, actorId, false)).isEqualTo(eventId);
        assertThat(lifecycle.getEventById(eventId).getName()).isEqualTo("SEAL AWS");
        assertThat(problems.getProblem(eventId, editedTrack, actorId, true).getFileName()).isEqualTo("custom.pdf");
        assertThat(problems.getProblem(eventId, editedTrack, actorId, true).isReleased()).isFalse();

        var entry = teams.findAllByEvent_EventId(eventId).stream()
                .filter(team -> team.getTrack().getTrackId().equals(backfilledTrack))
                .findFirst().orElseThrow();
        int participantId = members.findByTeam_TeamId(entry.getTeam().getTeamId()).getFirst().getUser().getUserId();
        var download = problems.downloadProblem(eventId, backfilledTrack, participantId, false);
        assertThat(download.contentType()).isEqualTo("application/zip");
        try (var zip = new ZipInputStream(download.resource().getInputStream())) {
            assertThat(zip.getNextEntry().getName()).isEqualTo("DE-BAI.md");
            assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).contains("Yêu cầu", "Dữ liệu và bài nộp");
        }

        problems.removeProblem(actorId, eventId, backfilledTrack);
        assertThat(demos.openPhase(Phase.CONTEST, actorId, false)).isEqualTo(eventId);
        assertThat(problems.getProblem(eventId, backfilledTrack, actorId, true).isHasProblem()).isFalse();
    }
}
