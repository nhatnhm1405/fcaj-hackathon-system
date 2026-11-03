package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.CreateCriteriaRequest;
import com.seal.hackathon.dto.request.SubmitScoresRequest;
import com.seal.hackathon.dto.response.ScoreResponse;
import com.seal.hackathon.dto.response.ScoringCriteriaResponse;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.Round;
import com.seal.hackathon.entity.Score;
import com.seal.hackathon.entity.ScoringCriteria;
import com.seal.hackathon.entity.Submission;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ForbiddenException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.HackathonEventRepository;
import com.seal.hackathon.repository.RoundRepository;
import com.seal.hackathon.repository.ScoreRepository;
import com.seal.hackathon.repository.ScoringCriteriaRepository;
import com.seal.hackathon.repository.ScoringCriteriaTemplateRepository;
import com.seal.hackathon.repository.SubmissionRepository;
import com.seal.hackathon.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoringServiceTest {

    @Mock
    private ScoringCriteriaRepository criteriaRepository;

    @Mock
    private ScoringCriteriaTemplateRepository templateRepository;

    @Mock
    private ScoreRepository scoreRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HackathonEventRepository eventRepository;

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private RoundTimerService roundTimerService;

    @InjectMocks
    private ScoringService scoringService;

    // ── getCriteriaByRound ───────────────────────────────────────────

    @Test
    void getCriteriaByRound_shouldReturnAllCriteriaForRound() {
        Round round = round(2);
        ScoringCriteria c1 = criteria(1, round, "Innovation", BigDecimal.TEN, BigDecimal.valueOf(0.4), 1);
        ScoringCriteria c2 = criteria(2, round, "Technical", BigDecimal.TEN, BigDecimal.valueOf(0.6), 2);

        when(criteriaRepository.findAllByRound_RoundIdOrderByOrderNumber(2)).thenReturn(List.of(c1, c2));

        List<ScoringCriteriaResponse> responses = scoringService.getCriteriaByRound(2);

        assertEquals(2, responses.size());
        assertEquals("Innovation", responses.get(0).getName());
        assertEquals("Technical", responses.get(1).getName());
        assertEquals(1, responses.get(0).getOrderNumber());
        assertEquals(2, responses.get(1).getOrderNumber());
    }

    @Test
    void getCriteriaByRound_shouldReturnEmptyList_whenNoCriteriaExist() {
        when(criteriaRepository.findAllByRound_RoundIdOrderByOrderNumber(2)).thenReturn(List.of());

        List<ScoringCriteriaResponse> responses = scoringService.getCriteriaByRound(2);

        assertTrue(responses.isEmpty());
    }

    // ── createCriteria ───────────────────────────────────────────────

    @Test
    void createCriteria_shouldSaveCriteria_whenEventAndRoundExist() {
        HackathonEvent event = event(1);
        Round round = round(2);
        CreateCriteriaRequest request = new CreateCriteriaRequest();
        request.setName(" Innovation ");
        request.setDescription("How innovative the solution is");
        request.setWeight(BigDecimal.valueOf(0.4));
        request.setMaxScore(BigDecimal.TEN);
        request.setOrderNumber(1);

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(roundRepository.findByIdAndEventId(2, 1)).thenReturn(Optional.of(round));
        when(criteriaRepository.save(any(ScoringCriteria.class))).thenAnswer(inv -> {
            ScoringCriteria c = inv.getArgument(0);
            c.setCriteriaId(10);
            return c;
        });

        ScoringCriteriaResponse response = scoringService.createCriteria(1, 2, request);

        assertEquals(10, response.getCriteriaId());
        assertEquals("Innovation", response.getName());
        assertEquals(BigDecimal.valueOf(0.4), response.getWeight());
        verify(criteriaRepository).save(any(ScoringCriteria.class));
    }

    @Test
    void createCriteria_shouldThrowNotFound_whenEventDoesNotExist() {
        when(eventRepository.findById(1)).thenReturn(Optional.empty());

        CreateCriteriaRequest request = new CreateCriteriaRequest();
        request.setName("Innovation");

        assertThrows(ResourceNotFoundException.class, () -> scoringService.createCriteria(1, 2, request));

        verify(criteriaRepository, never()).save(any());
    }

    @Test
    void createCriteria_shouldThrowNotFound_whenRoundDoesNotBelongToEvent() {
        when(eventRepository.findById(1)).thenReturn(Optional.of(event(1)));
        when(roundRepository.findByIdAndEventId(2, 1)).thenReturn(Optional.empty());

        CreateCriteriaRequest request = new CreateCriteriaRequest();
        request.setName("Innovation");

        assertThrows(ResourceNotFoundException.class, () -> scoringService.createCriteria(1, 2, request));

        verify(criteriaRepository, never()).save(any());
    }

    // ── submitScores: valid cases ────────────────────────────────────

    @Test
    void submitScores_shouldSaveScores_whenJudgeIsAssignedToSubmission() {
        User judge = user(20, "Judge");
        Submission submission = submission(99);
        ScoringCriteria crit = criteria(7, submission.getRound(), "Innovation", BigDecimal.TEN, BigDecimal.ONE, 1);
        SubmitScoresRequest request = submitScoresRequest(99, 7, BigDecimal.valueOf(8));

        when(userRepository.findById(20)).thenReturn(Optional.of(judge));
        when(submissionRepository.findBySubmissionIdAndJudgeId(99, 20)).thenReturn(Optional.of(submission));
        when(criteriaRepository.findById(7)).thenReturn(Optional.of(crit));
        when(criteriaRepository.findAllByRound_RoundIdOrderByOrderNumber(2)).thenReturn(List.of(crit));
        when(scoreRepository.findBySubmission_SubmissionIdAndJudge_UserIdAndCriteria_CriteriaId(99, 20, 7))
                .thenReturn(Optional.empty());
        when(scoreRepository.save(any(Score.class))).thenAnswer(invocation -> {
            Score score = invocation.getArgument(0);
            score.setScoreId(123);
            return score;
        });

        List<ScoreResponse> responses = scoringService.submitScores(20, request);

        assertEquals(1, responses.size());
        assertEquals(123, responses.get(0).getScoreId());
        verify(submissionRepository).findBySubmissionIdAndJudgeId(99, 20);
        verify(scoreRepository).save(any(Score.class));
    }

    @Test
    void submitScores_shouldUpdateExistingScore_whenScoreAlreadyExists() {
        User judge = user(20, "Judge");
        Submission submission = submission(99);
        ScoringCriteria crit = criteria(7, submission.getRound(), "Innovation", BigDecimal.TEN, BigDecimal.ONE, 1);
        Score existingScore = Score.builder()
                .scoreId(50)
                .submission(submission)
                .judge(judge)
                .criteria(crit)
                .value(BigDecimal.valueOf(5))
                .build();

        when(userRepository.findById(20)).thenReturn(Optional.of(judge));
        when(submissionRepository.findBySubmissionIdAndJudgeId(99, 20)).thenReturn(Optional.of(submission));
        when(criteriaRepository.findById(7)).thenReturn(Optional.of(crit));
        when(criteriaRepository.findAllByRound_RoundIdOrderByOrderNumber(2)).thenReturn(List.of(crit));
        when(scoreRepository.findBySubmission_SubmissionIdAndJudge_UserIdAndCriteria_CriteriaId(99, 20, 7))
                .thenReturn(Optional.of(existingScore));
        when(scoreRepository.save(any(Score.class))).thenAnswer(inv -> inv.getArgument(0));

        SubmitScoresRequest request = submitScoresRequest(99, 7, BigDecimal.valueOf(9));
        List<ScoreResponse> responses = scoringService.submitScores(20, request);

        assertEquals(1, responses.size());
        assertEquals(50, responses.get(0).getScoreId());
        assertEquals(BigDecimal.valueOf(9), responses.get(0).getValue());
    }

    @Test
    void submitScores_shouldSetScoreAtExactMaxScore() {
        User judge = user(20, "Judge");
        Submission submission = submission(99);
        ScoringCriteria crit = criteria(7, submission.getRound(), "Innovation", BigDecimal.TEN, BigDecimal.ONE, 1);

        when(userRepository.findById(20)).thenReturn(Optional.of(judge));
        when(submissionRepository.findBySubmissionIdAndJudgeId(99, 20)).thenReturn(Optional.of(submission));
        when(criteriaRepository.findById(7)).thenReturn(Optional.of(crit));
        when(criteriaRepository.findAllByRound_RoundIdOrderByOrderNumber(2)).thenReturn(List.of(crit));
        when(scoreRepository.findBySubmission_SubmissionIdAndJudge_UserIdAndCriteria_CriteriaId(99, 20, 7))
                .thenReturn(Optional.empty());
        when(scoreRepository.save(any(Score.class))).thenAnswer(inv -> {
            Score s = inv.getArgument(0);
            s.setScoreId(1);
            return s;
        });

        List<ScoreResponse> responses = scoringService.submitScores(20, submitScoresRequest(99, 7, BigDecimal.TEN));

        assertEquals(1, responses.size());
        assertEquals(BigDecimal.TEN, responses.get(0).getValue());
    }

    @Test
    void submitScores_shouldSetScoreAtZero() {
        User judge = user(20, "Judge");
        Submission submission = submission(99);
        ScoringCriteria crit = criteria(7, submission.getRound(), "Innovation", BigDecimal.TEN, BigDecimal.ONE, 1);

        when(userRepository.findById(20)).thenReturn(Optional.of(judge));
        when(submissionRepository.findBySubmissionIdAndJudgeId(99, 20)).thenReturn(Optional.of(submission));
        when(criteriaRepository.findById(7)).thenReturn(Optional.of(crit));
        when(criteriaRepository.findAllByRound_RoundIdOrderByOrderNumber(2)).thenReturn(List.of(crit));
        when(scoreRepository.findBySubmission_SubmissionIdAndJudge_UserIdAndCriteria_CriteriaId(99, 20, 7))
                .thenReturn(Optional.empty());
        when(scoreRepository.save(any(Score.class))).thenAnswer(inv -> {
            Score s = inv.getArgument(0);
            s.setScoreId(1);
            return s;
        });

        List<ScoreResponse> responses = scoringService.submitScores(20, submitScoresRequest(99, 7, BigDecimal.ZERO));

        assertEquals(1, responses.size());
        assertEquals(BigDecimal.ZERO, responses.get(0).getValue());
    }

    @Test
    void submitScores_shouldPersistDraftFlagAndComment() {
        User judge = user(20, "Judge");
        Submission submission = submission(99);
        ScoringCriteria crit = criteria(7, submission.getRound(), "Innovation", BigDecimal.TEN, BigDecimal.ONE, 1);
        SubmitScoresRequest request = submitScoresRequest(99, 7, BigDecimal.valueOf(8));
        request.setDraft(true);
        request.getScores().get(0).setComment("Strong prototype");

        when(userRepository.findById(20)).thenReturn(Optional.of(judge));
        when(submissionRepository.findBySubmissionIdAndJudgeId(99, 20)).thenReturn(Optional.of(submission));
        when(criteriaRepository.findById(7)).thenReturn(Optional.of(crit));
        when(scoreRepository.findBySubmission_SubmissionIdAndJudge_UserIdAndCriteria_CriteriaId(99, 20, 7))
                .thenReturn(Optional.empty());
        when(scoreRepository.save(any(Score.class))).thenAnswer(inv -> {
            Score s = inv.getArgument(0);
            s.setScoreId(1);
            return s;
        });

        scoringService.submitScores(20, request);

        ArgumentCaptor<Score> captor = ArgumentCaptor.forClass(Score.class);
        verify(scoreRepository).save(captor.capture());
        assertEquals(true, captor.getValue().getIsDraft());
        assertEquals("Strong prototype", captor.getValue().getComment());
    }

    @Test
    void submitScores_shouldSaveMultipleEntriesInOneRequest() {
        User judge = user(20, "Judge");
        Submission submission = submission(99);
        ScoringCriteria innovation = criteria(7, submission.getRound(), "Innovation", BigDecimal.TEN, BigDecimal.ONE, 1);
        ScoringCriteria technical = criteria(8, submission.getRound(), "Technical", BigDecimal.TEN, BigDecimal.ONE, 2);
        SubmitScoresRequest request = new SubmitScoresRequest();
        request.setSubmissionId(99);
        SubmitScoresRequest.ScoreEntry entry1 = new SubmitScoresRequest.ScoreEntry();
        entry1.setCriteriaId(7);
        entry1.setValue(BigDecimal.valueOf(8));
        SubmitScoresRequest.ScoreEntry entry2 = new SubmitScoresRequest.ScoreEntry();
        entry2.setCriteriaId(8);
        entry2.setValue(BigDecimal.valueOf(9));
        request.setScores(List.of(entry1, entry2));

        when(userRepository.findById(20)).thenReturn(Optional.of(judge));
        when(submissionRepository.findBySubmissionIdAndJudgeId(99, 20)).thenReturn(Optional.of(submission));
        when(criteriaRepository.findById(7)).thenReturn(Optional.of(innovation));
        when(criteriaRepository.findById(8)).thenReturn(Optional.of(technical));
        when(criteriaRepository.findAllByRound_RoundIdOrderByOrderNumber(2))
                .thenReturn(List.of(innovation, technical));
        when(scoreRepository.findBySubmission_SubmissionIdAndJudge_UserIdAndCriteria_CriteriaId(99, 20, 7))
                .thenReturn(Optional.empty());
        when(scoreRepository.findBySubmission_SubmissionIdAndJudge_UserIdAndCriteria_CriteriaId(99, 20, 8))
                .thenReturn(Optional.empty());
        when(scoreRepository.save(any(Score.class))).thenAnswer(inv -> {
            Score s = inv.getArgument(0);
            s.setScoreId(s.getCriteria().getCriteriaId());
            return s;
        });

        List<ScoreResponse> responses = scoringService.submitScores(20, request);

        assertEquals(2, responses.size());
        verify(scoreRepository, times(2)).save(any(Score.class));
    }

    @Test
    void submitScores_shouldRejectFinalSubmissionWhenAnyRoundCriteriaIsMissing() {
        User judge = user(20, "Judge");
        Submission submission = submission(99);
        ScoringCriteria innovation = criteria(7, submission.getRound(), "Innovation", BigDecimal.TEN, BigDecimal.ONE, 1);
        ScoringCriteria technical = criteria(8, submission.getRound(), "Technical", BigDecimal.TEN, BigDecimal.ONE, 2);
        SubmitScoresRequest request = submitScoresRequest(99, 7, BigDecimal.valueOf(8));

        when(userRepository.findById(20)).thenReturn(Optional.of(judge));
        when(submissionRepository.findBySubmissionIdAndJudgeId(99, 20)).thenReturn(Optional.of(submission));
        when(criteriaRepository.findById(7)).thenReturn(Optional.of(innovation));
        when(criteriaRepository.findAllByRound_RoundIdOrderByOrderNumber(2))
                .thenReturn(List.of(innovation, technical));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> scoringService.submitScores(20, request));

        assertTrue(error.getMessage().contains("Technical"));
        verify(scoreRepository, never()).save(any());
    }

    // ── submitScores: invalid cases ──────────────────────────────────

    @Test
    void submitScores_shouldThrowBadRequest_whenRequestIsNull() {
        assertThrows(BadRequestException.class, () -> scoringService.submitScores(20, null));

        verify(userRepository, never()).findById(any());
        verify(scoreRepository, never()).save(any());
    }

    @Test
    void submitScores_shouldThrowBadRequest_whenSubmissionIdIsNull() {
        SubmitScoresRequest request = new SubmitScoresRequest();
        request.setSubmissionId(null);
        SubmitScoresRequest.ScoreEntry entry = new SubmitScoresRequest.ScoreEntry();
        entry.setCriteriaId(1);
        entry.setValue(BigDecimal.ONE);
        request.setScores(List.of(entry));

        assertThrows(BadRequestException.class, () -> scoringService.submitScores(20, request));

        verify(userRepository, never()).findById(any());
        verify(scoreRepository, never()).save(any());
    }

    @Test
    void submitScores_shouldThrowBadRequest_whenScoresListIsEmpty() {
        SubmitScoresRequest request = new SubmitScoresRequest();
        request.setSubmissionId(99);
        request.setScores(List.of());

        assertThrows(BadRequestException.class, () -> scoringService.submitScores(20, request));

        verify(userRepository, never()).findById(any());
        verify(scoreRepository, never()).save(any());
    }

    @Test
    void submitScores_shouldThrowBadRequest_whenScoresListIsNull() {
        SubmitScoresRequest request = new SubmitScoresRequest();
        request.setSubmissionId(99);
        request.setScores(null);

        assertThrows(BadRequestException.class, () -> scoringService.submitScores(20, request));

        verify(userRepository, never()).findById(any());
    }

    @Test
    void submitScores_shouldThrowNotFound_whenJudgeDoesNotExist() {
        when(userRepository.findById(20)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> scoringService.submitScores(20, submitScoresRequest(99, 7, BigDecimal.ONE)));

        verify(scoreRepository, never()).save(any());
    }

    @Test
    void submitScores_shouldThrowNotFound_whenSubmissionIsNotAssignedToJudge() {
        when(userRepository.findById(20)).thenReturn(Optional.of(user(20, "Judge")));
        when(submissionRepository.findBySubmissionIdAndJudgeId(99, 20)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> scoringService.submitScores(20, submitScoresRequest(99, 7, BigDecimal.ONE)));

        assertTrue(ex.getMessage().contains("not assigned"));
        verify(scoreRepository, never()).save(any());
    }

    @Test
    void submitScores_shouldRejectEveryWriteAfterFinalSubmission() {
        User judge = user(20, "Judge");
        Submission submission = submission(99);
        when(userRepository.findById(20)).thenReturn(Optional.of(judge));
        when(submissionRepository.findBySubmissionIdAndJudgeId(99, 20)).thenReturn(Optional.of(submission));
        when(scoreRepository.existsBySubmission_SubmissionIdAndJudge_UserIdAndIsDraftFalse(99, 20))
                .thenReturn(true);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> scoringService.submitScores(20, submitScoresRequest(99, 7, BigDecimal.ONE)));

        assertTrue(error.getMessage().contains("cannot be changed"));
        verify(roundTimerService).assertJudgingOpen(2);
        verify(scoreRepository, never()).save(any());
    }

    @Test
    void submitScores_shouldThrowBadRequest_whenScoreEntryHasNullCriteriaId() {
        User judge = user(20, "Judge");
        Submission submission = submission(99);

        when(userRepository.findById(20)).thenReturn(Optional.of(judge));
        when(submissionRepository.findBySubmissionIdAndJudgeId(99, 20)).thenReturn(Optional.of(submission));

        SubmitScoresRequest request = new SubmitScoresRequest();
        request.setSubmissionId(99);
        SubmitScoresRequest.ScoreEntry entry = new SubmitScoresRequest.ScoreEntry();
        entry.setCriteriaId(null);
        entry.setValue(BigDecimal.ONE);
        request.setScores(List.of(entry));

        assertThrows(BadRequestException.class, () -> scoringService.submitScores(20, request));

        verify(scoreRepository, never()).save(any());
    }

    @Test
    void submitScores_shouldThrowBadRequest_whenScoreEntryIsNull() {
        User judge = user(20, "Judge");
        Submission submission = submission(99);
        SubmitScoresRequest request = new SubmitScoresRequest();
        request.setSubmissionId(99);
        request.setScores(java.util.Collections.singletonList(null));

        when(userRepository.findById(20)).thenReturn(Optional.of(judge));
        when(submissionRepository.findBySubmissionIdAndJudgeId(99, 20)).thenReturn(Optional.of(submission));

        assertThrows(BadRequestException.class, () -> scoringService.submitScores(20, request));

        verify(criteriaRepository, never()).findById(any());
        verify(scoreRepository, never()).save(any());
    }

    @Test
    void submitScores_shouldThrowBadRequest_whenScoreEntryHasNullValue() {
        User judge = user(20, "Judge");
        Submission submission = submission(99);

        when(userRepository.findById(20)).thenReturn(Optional.of(judge));
        when(submissionRepository.findBySubmissionIdAndJudgeId(99, 20)).thenReturn(Optional.of(submission));

        SubmitScoresRequest request = new SubmitScoresRequest();
        request.setSubmissionId(99);
        SubmitScoresRequest.ScoreEntry entry = new SubmitScoresRequest.ScoreEntry();
        entry.setCriteriaId(7);
        entry.setValue(null);
        request.setScores(List.of(entry));

        assertThrows(BadRequestException.class, () -> scoringService.submitScores(20, request));

        verify(scoreRepository, never()).save(any());
    }

    @Test
    void submitScores_shouldThrowNotFound_whenCriteriaDoesNotExist() {
        User judge = user(20, "Judge");
        Submission submission = submission(99);

        when(userRepository.findById(20)).thenReturn(Optional.of(judge));
        when(submissionRepository.findBySubmissionIdAndJudgeId(99, 20)).thenReturn(Optional.of(submission));
        when(criteriaRepository.findById(7)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> scoringService.submitScores(20, submitScoresRequest(99, 7, BigDecimal.ONE)));

        verify(scoreRepository, never()).save(any());
    }

    @Test
    void submitScores_shouldThrowBadRequest_whenCriteriaDoesNotBelongToSubmissionRound() {
        User judge = user(20, "Judge");
        Submission submission = submission(99);
        Round otherRound = Round.builder().roundId(999).event(event(1)).name("Other").status("ACTIVE").build();
        ScoringCriteria wrongCrit = criteria(7, otherRound, "Innovation", BigDecimal.TEN, BigDecimal.ONE, 1);

        when(userRepository.findById(20)).thenReturn(Optional.of(judge));
        when(submissionRepository.findBySubmissionIdAndJudgeId(99, 20)).thenReturn(Optional.of(submission));
        when(criteriaRepository.findById(7)).thenReturn(Optional.of(wrongCrit));

        assertThrows(BadRequestException.class,
                () -> scoringService.submitScores(20, submitScoresRequest(99, 7, BigDecimal.ONE)));

        verify(scoreRepository, never()).save(any());
    }

    @Test
    void submitScores_shouldThrowBadRequest_whenCriteriaRoundIsNull() {
        User judge = user(20, "Judge");
        Submission submission = submission(99);
        ScoringCriteria crit = criteria(7, null, "Innovation", BigDecimal.TEN, BigDecimal.ONE, 1);

        when(userRepository.findById(20)).thenReturn(Optional.of(judge));
        when(submissionRepository.findBySubmissionIdAndJudgeId(99, 20)).thenReturn(Optional.of(submission));
        when(criteriaRepository.findById(7)).thenReturn(Optional.of(crit));

        assertThrows(BadRequestException.class,
                () -> scoringService.submitScores(20, submitScoresRequest(99, 7, BigDecimal.ONE)));

        verify(scoreRepository, never()).save(any());
    }

    @Test
    void submitScores_shouldThrowBadRequest_whenScoreIsNegative() {
        User judge = user(20, "Judge");
        Submission submission = submission(99);
        ScoringCriteria crit = criteria(7, submission.getRound(), "Innovation", BigDecimal.TEN, BigDecimal.ONE, 1);

        when(userRepository.findById(20)).thenReturn(Optional.of(judge));
        when(submissionRepository.findBySubmissionIdAndJudgeId(99, 20)).thenReturn(Optional.of(submission));
        when(criteriaRepository.findById(7)).thenReturn(Optional.of(crit));

        assertThrows(BadRequestException.class,
                () -> scoringService.submitScores(20, submitScoresRequest(99, 7, BigDecimal.valueOf(-1))));

        verify(scoreRepository, never()).save(any());
    }

    @Test
    void submitScores_shouldThrowBadRequest_whenScoreExceedsMaxScore() {
        User judge = user(20, "Judge");
        Submission submission = submission(99);
        ScoringCriteria crit = criteria(7, submission.getRound(), "Innovation", BigDecimal.TEN, BigDecimal.ONE, 1);

        when(userRepository.findById(20)).thenReturn(Optional.of(judge));
        when(submissionRepository.findBySubmissionIdAndJudgeId(99, 20)).thenReturn(Optional.of(submission));
        when(criteriaRepository.findById(7)).thenReturn(Optional.of(crit));

        assertThrows(BadRequestException.class,
                () -> scoringService.submitScores(20, submitScoresRequest(99, 7, BigDecimal.valueOf(11))));

        verify(scoreRepository, never()).save(any());
    }

    // ── getScoresBySubmission ─────────────────────────────────────────

    @Test
    void getScoresBySubmission_shouldReturnScores_whenCoordinatorRequests() {
        Submission submission = submission(99);
        Score score = Score.builder()
                .scoreId(1).submission(submission).judge(user(20, "Judge"))
                .criteria(criteria(7, submission.getRound(), "Innovation", BigDecimal.TEN, BigDecimal.ONE, 1))
                .value(BigDecimal.valueOf(8)).build();

        when(submissionRepository.findById(99)).thenReturn(Optional.of(submission));
        when(scoreRepository.findAllBySubmission_SubmissionId(99)).thenReturn(List.of(score));

        List<ScoreResponse> responses = scoringService.getScoresBySubmission(
                10, Set.of("ROLE_EVENT_COORDINATOR"), 99);

        assertEquals(1, responses.size());
        verify(submissionRepository).findById(99);
    }

    @Test
    void getScoresBySubmission_shouldRequireJudgeAssignmentForJudgeRole() {
        Submission submission = submission(99);
        when(submissionRepository.findBySubmissionIdAndJudgeId(99, 20)).thenReturn(Optional.of(submission));
        when(scoreRepository.findAllBySubmission_SubmissionId(99)).thenReturn(List.of());

        scoringService.getScoresBySubmission(20, Set.of("ROLE_JUDGE"), 99);

        verify(submissionRepository).findBySubmissionIdAndJudgeId(99, 20);
    }

    @Test
    void getScoresBySubmission_shouldThrowNotFound_whenCoordinatorAndSubmissionNotFound() {
        when(submissionRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> scoringService.getScoresBySubmission(10, Set.of("ROLE_EVENT_COORDINATOR"), 99));
    }

    @Test
    void getScoresBySubmission_shouldThrowNotFound_whenJudgeNotAssigned() {
        when(submissionRepository.findBySubmissionIdAndJudgeId(99, 20)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> scoringService.getScoresBySubmission(20, Set.of("ROLE_JUDGE"), 99));
    }

    @Test
    void getScoresBySubmission_shouldThrowForbidden_whenOtherRole() {
        assertThrows(ForbiddenException.class,
                () -> scoringService.getScoresBySubmission(10, Set.of("ROLE_PARTICIPANT"), 99));

        verify(submissionRepository, never()).findById(any());
        verify(scoreRepository, never()).findAllBySubmission_SubmissionId(anyInt());
    }

    @Test
    void getScoresBySubmission_shouldThrowForbidden_whenAuthoritiesAreNull() {
        assertThrows(ForbiddenException.class,
                () -> scoringService.getScoresBySubmission(10, null, 99));

        verify(submissionRepository, never()).findById(any());
        verify(submissionRepository, never()).findBySubmissionIdAndJudgeId(anyInt(), anyInt());
        verify(scoreRepository, never()).findAllBySubmission_SubmissionId(anyInt());
    }

    // ── getMyScoresByRound ───────────────────────────────────────────

    @Test
    void getMyScoresByRound_shouldReturnScoresForJudgeAndRound() {
        Submission submission = submission(99);
        ScoringCriteria crit = criteria(7, submission.getRound(), "Innovation", BigDecimal.TEN, BigDecimal.ONE, 1);
        Score score = Score.builder()
                .scoreId(1).submission(submission).judge(user(20, "Judge"))
                .criteria(crit).value(BigDecimal.valueOf(8)).build();

        when(scoreRepository.findAllByJudge_UserIdAndSubmission_Round_RoundId(20, 2)).thenReturn(List.of(score));

        List<ScoreResponse> responses = scoringService.getMyScoresByRound(20, 2);

        assertEquals(1, responses.size());
        assertEquals(BigDecimal.valueOf(8), responses.get(0).getValue());
    }

    @Test
    void getMyScoresByRound_shouldReturnEmptyList_whenNoScoresExist() {
        when(scoreRepository.findAllByJudge_UserIdAndSubmission_Round_RoundId(20, 2)).thenReturn(List.of());

        List<ScoreResponse> responses = scoringService.getMyScoresByRound(20, 2);

        assertTrue(responses.isEmpty());
    }

    // ── Helper methods ───────────────────────────────────────────────

    private SubmitScoresRequest submitScoresRequest(Integer submissionId, Integer criteriaId, BigDecimal value) {
        SubmitScoresRequest request = new SubmitScoresRequest();
        request.setSubmissionId(submissionId);
        SubmitScoresRequest.ScoreEntry entry = new SubmitScoresRequest.ScoreEntry();
        entry.setCriteriaId(criteriaId);
        entry.setValue(value);
        request.setScores(List.of(entry));
        return request;
    }

    private User user(Integer id, String fullName) {
        return User.builder()
                .userId(id)
                .fullName(fullName)
                .email(id + "@seal.test")
                .userType("STAFF")
                .build();
    }

    private HackathonEvent event(Integer eventId) {
        return HackathonEvent.builder()
                .eventId(eventId)
                .name("Hackathon")
                .status("IN_PROGRESS")
                .build();
    }

    private Round round(Integer roundId) {
        return Round.builder()
                .roundId(roundId)
                .event(event(1))
                .name("Round 1")
                .status("ACTIVE")
                .build();
    }

    private Submission submission(Integer id) {
        HackathonEvent event = event(1);
        Round round = Round.builder()
                .roundId(2)
                .event(event)
                .name("Round 1")
                .status("ACTIVE")
                .build();
        Team team = Team.builder()
                .teamId(3)
                .name("Seal Team")
                .build();
        return Submission.builder()
                .submissionId(id)
                .team(team)
                .round(round)
                .submittedBy(user(10, "Submitter"))
                .repoUrl("https://repo.test")
                .submittedAt(LocalDateTime.now())
                .status("SUBMITTED")
                .build();
    }

    private ScoringCriteria criteria(Integer id, Round round, String name, BigDecimal maxScore,
                                     BigDecimal weight, Integer orderNumber) {
        return ScoringCriteria.builder()
                .criteriaId(id)
                .round(round)
                .name(name)
                .maxScore(maxScore)
                .weight(weight)
                .orderNumber(orderNumber)
                .build();
    }
}
