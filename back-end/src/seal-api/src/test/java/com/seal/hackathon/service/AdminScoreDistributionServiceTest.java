package com.seal.hackathon.service;

import com.seal.hackathon.dto.response.AdminScoreDistributionResponse;
import com.seal.hackathon.entity.*;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminScoreDistributionServiceTest {

    @Mock private HackathonEventRepository eventRepository;
    @Mock private RoundRepository roundRepository;
    @Mock private TrackRepository trackRepository;
    @Mock private ScoringCriteriaRepository criteriaRepository;
    @Mock private ScoreRepository scoreRepository;
    @Mock private RoundResultRepository resultRepository;
    @Mock private TeamEventEntryRepository teamEventEntryRepository;

    @InjectMocks
    private AdminScoreDistributionService service;

    @Test
    void judgeEvaluation_shouldUseRankingNormalizationAndBuildHistogram() {
        Fixture fixture = completedFixture();
        stubContext(fixture);
        when(scoreRepository.findAllFinalizedByRoundWithDetails(fixture.round().getRoundId()))
                .thenReturn(fixture.scores());

        AdminScoreDistributionResponse response = service.getDistribution(
                fixture.event().getEventId(), null, null, "JUDGE_EVALUATION", null);

        assertEquals(fixture.round().getRoundId(), response.getSelectedRoundId());
        assertEquals(2, response.getObservations().size());
        assertEquals(new BigDecimal("100.00"), response.getObservations().get(0).getScore());
        assertEquals(new BigDecimal("57.50"), response.getObservations().get(1).getScore());
        assertEquals(new BigDecimal("78.75"), response.getStatistics().getAverage());
        assertEquals(new BigDecimal("78.75"), response.getStatistics().getMedian());
        assertEquals(new BigDecimal("21.25"), response.getStatistics().getStandardDeviation());
        assertEquals(1, response.getStatistics().getTeamCount());
        assertEquals(2, response.getStatistics().getJudgeCount());
        assertEquals(1, response.getBins().get(5).getCount());
        assertEquals(1, response.getBins().get(9).getCount());
        assertEquals(new BigDecimal("50.00"), response.getStatistics().getAtOrAboveEightyPercentage());
    }

    @Test
    void criteriaScore_shouldNormalizeOnlyTheSelectedCriterion() {
        Fixture fixture = completedFixture();
        stubContext(fixture);
        when(scoreRepository.findAllFinalizedByRoundWithDetails(fixture.round().getRoundId()))
                .thenReturn(fixture.scores());

        AdminScoreDistributionResponse response = service.getDistribution(
                fixture.event().getEventId(), fixture.round().getRoundId(), null,
                "CRITERIA_SCORE", fixture.criteria().getFirst().getCriteriaId());

        assertEquals(fixture.criteria().getFirst().getCriteriaId(), response.getSelectedCriteriaId());
        assertEquals(2, response.getStatistics().getSampleCount());
        assertEquals(new BigDecimal("90.00"), response.getStatistics().getAverage());
        assertEquals(List.of(new BigDecimal("100.00"), new BigDecimal("80.00")),
                response.getObservations().stream()
                        .map(AdminScoreDistributionResponse.Observation::getScore).toList());
    }

    @Test
    void getDistribution_shouldRejectEventThatIsNotCompleted() {
        HackathonEvent event = HackathonEvent.builder()
                .eventId(1).name("Running event").status("IN_PROGRESS").build();
        when(eventRepository.findById(event.getEventId())).thenReturn(Optional.of(event));

        assertThrows(BadRequestException.class, () -> service.getDistribution(
                event.getEventId(), null, null, "JUDGE_EVALUATION", null));
    }

    private void stubContext(Fixture fixture) {
        when(eventRepository.findById(fixture.event().getEventId()))
                .thenReturn(Optional.of(fixture.event()));
        when(roundRepository.findAllByEvent_EventIdOrderByOrderNumber(fixture.event().getEventId()))
                .thenReturn(List.of(fixture.round()));
        when(trackRepository.findAllByEvent_EventId(fixture.event().getEventId()))
                .thenReturn(List.of(fixture.track()));
        when(teamEventEntryRepository.findAllByEvent_EventId(fixture.event().getEventId()))
                .thenReturn(List.of(fixture.entry()));
        when(criteriaRepository.findAllByRound_RoundIdOrderByOrderNumber(fixture.round().getRoundId()))
                .thenReturn(fixture.criteria());
    }

    private Fixture completedFixture() {
        HackathonEvent event = HackathonEvent.builder()
                .eventId(1).name("SEAL Summer 2026").status("COMPLETED").build();
        Round round = Round.builder()
                .roundId(2).event(event).name("Final").orderNumber(2).isFinal(true).build();
        Track track = Track.builder().trackId(3).event(event).name("AI").build();
        Team team = Team.builder().teamId(4).name("Byte Builders").build();
        TeamEventEntry entry = TeamEventEntry.builder()
                .id(5).event(event).team(team).track(track).status("APPROVED").build();
        User judgeOne = User.builder().userId(10).fullName("Judge One").build();
        User judgeTwo = User.builder().userId(11).fullName("Judge Two").build();
        Submission submission = Submission.builder()
                .submissionId(20).round(round).team(team).submittedBy(judgeOne).build();
        ScoringCriteria quality = ScoringCriteria.builder()
                .criteriaId(30).round(round).event(event).name("Quality")
                .weight(BigDecimal.ONE).maxScore(BigDecimal.TEN).orderNumber(1).build();
        ScoringCriteria impact = ScoringCriteria.builder()
                .criteriaId(31).round(round).event(event).name("Impact")
                .weight(BigDecimal.valueOf(3)).maxScore(BigDecimal.valueOf(20)).orderNumber(2).build();
        List<Score> scores = List.of(
                score(40, submission, judgeOne, quality, "8"),
                score(41, submission, judgeOne, impact, "10"),
                score(42, submission, judgeTwo, quality, "10"),
                score(43, submission, judgeTwo, impact, "20"));
        return new Fixture(event, round, track, team, entry, List.of(quality, impact), scores);
    }

    private Score score(
            Integer id, Submission submission, User judge,
            ScoringCriteria criteria, String value) {
        return Score.builder()
                .scoreId(id).submission(submission).judge(judge).criteria(criteria)
                .value(new BigDecimal(value)).isDraft(false).build();
    }

    private record Fixture(
            HackathonEvent event, Round round, Track track, Team team,
            TeamEventEntry entry, List<ScoringCriteria> criteria, List<Score> scores) {
    }
}
