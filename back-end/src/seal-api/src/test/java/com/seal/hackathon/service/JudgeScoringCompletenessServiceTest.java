package com.seal.hackathon.service;

import com.seal.hackathon.dto.response.SubmissionScoringProgressResponse;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.JudgeAssignment;
import com.seal.hackathon.entity.Round;
import com.seal.hackathon.entity.Score;
import com.seal.hackathon.entity.ScoringCriteria;
import com.seal.hackathon.entity.Submission;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.Track;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.repository.JudgeAssignmentRepository;
import com.seal.hackathon.repository.RoundRepository;
import com.seal.hackathon.repository.ScoreRepository;
import com.seal.hackathon.repository.ScoringCriteriaRepository;
import com.seal.hackathon.repository.SubmissionRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JudgeScoringCompletenessServiceTest {

    @Mock private RoundRepository roundRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private JudgeAssignmentRepository assignmentRepository;
    @Mock private ScoringCriteriaRepository criteriaRepository;
    @Mock private ScoreRepository scoreRepository;
    @Mock private TeamEventEntryRepository teamEventEntryRepository;

    @InjectMocks private JudgeScoringCompletenessService service;

    private Round round;
    private Submission submission;
    private List<JudgeAssignment> assignments;
    private List<ScoringCriteria> criteria;
    private List<Score> scores;

    @BeforeEach
    void setUp() {
        HackathonEvent event = HackathonEvent.builder().eventId(1).name("SEAL").build();
        round = Round.builder().roundId(2).event(event).name("Preliminary").build();
        Track track = Track.builder().trackId(3).event(event).name("AI").build();
        Team team = Team.builder().teamId(4).name("Arsenal").build();
        TeamEventEntry entry = TeamEventEntry.builder().id(4).team(team).event(event).track(track).build();
        submission = Submission.builder().submissionId(5).round(round).team(team).build();
        when(teamEventEntryRepository.findByTeam_TeamIdAndEvent_EventId(4, 1)).thenReturn(Optional.of(entry));

        User a = User.builder().userId(10).fullName("Judge A").build();
        User b = User.builder().userId(11).fullName("Judge B").build();
        User c = User.builder().userId(12).fullName("Judge C").build();
        assignments = List.of(
                assignment(a, track), assignment(b, track), assignment(c, track));

        ScoringCriteria innovation = ScoringCriteria.builder()
                .criteriaId(20).round(round).name("Innovation")
                .maxScore(BigDecimal.TEN).weight(BigDecimal.ONE).build();
        ScoringCriteria technical = ScoringCriteria.builder()
                .criteriaId(21).round(round).name("Technical")
                .maxScore(BigDecimal.TEN).weight(BigDecimal.ONE).build();
        criteria = List.of(innovation, technical);
        scores = new ArrayList<>();
        scores.add(finalScore(submission, a, innovation));
        scores.add(finalScore(submission, a, technical));
        scores.add(finalScore(submission, b, innovation));
        scores.add(finalScore(submission, b, technical));

        when(submissionRepository.findAllByRound_RoundId(2)).thenReturn(List.of(submission));
        when(assignmentRepository.findAllByRound_RoundIdAndIsActiveTrue(2)).thenReturn(assignments);
        when(criteriaRepository.findAllByRound_RoundIdOrderByOrderNumber(2)).thenReturn(criteria);
        when(scoreRepository.findAllBySubmission_Round_RoundId(2)).thenAnswer(ignored -> scores);
    }

    @Test
    void progress_shouldRemainIncompleteWhenOneOfThreeAssignedJudgesHasNoFinalScores() {
        when(roundRepository.findByIdAndEventId(2, 1)).thenReturn(Optional.of(round));

        SubmissionScoringProgressResponse progress = service.getProgress(1, 2).get(0);

        assertEquals(3, progress.getAssignedJudgeCount());
        assertEquals(2, progress.getCompletedJudgeCount());
        assertFalse(progress.getComplete());
        assertEquals("NOT_STARTED", progress.getJudges().get(2).getStatus());
        assertTrue(progress.getJudges().get(2).getMissingCriteria().contains("Technical"));
    }

    @Test
    void assertRoundComplete_shouldRejectMissingJudgeInsteadOfTreatingThemAsZero() {
        when(roundRepository.findById(2)).thenReturn(Optional.of(round));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.assertRoundComplete(2));

        assertTrue(error.getMessage().contains("2/3"));
        assertTrue(error.getMessage().contains("Judge C"));
    }

    @Test
    void progress_shouldCompleteOnlyAfterThirdJudgeFinalizesEveryCriteria() {
        User c = assignments.get(2).getJudge();
        scores.add(finalScore(submission, c, criteria.get(0)));
        scores.add(finalScore(submission, c, criteria.get(1)));
        when(roundRepository.findByIdAndEventId(2, 1)).thenReturn(Optional.of(round));

        SubmissionScoringProgressResponse progress = service.getProgress(1, 2).get(0);

        assertEquals(3, progress.getCompletedJudgeCount());
        assertTrue(progress.getComplete());
    }

    private JudgeAssignment assignment(User judge, Track track) {
        return JudgeAssignment.builder().judge(judge).round(round).track(track).isActive(true).build();
    }

    private Score finalScore(Submission target, User judge, ScoringCriteria item) {
        return Score.builder().submission(target).judge(judge).criteria(item)
                .value(BigDecimal.valueOf(8)).isDraft(false).build();
    }
}
