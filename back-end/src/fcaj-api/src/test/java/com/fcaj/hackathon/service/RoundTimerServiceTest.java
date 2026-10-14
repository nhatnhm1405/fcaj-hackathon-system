package com.fcaj.hackathon.service;

import com.fcaj.hackathon.dto.request.StartTimerRequest;
import com.fcaj.hackathon.entity.HackathonEvent;
import com.fcaj.hackathon.entity.JudgeAssignment;
import com.fcaj.hackathon.entity.Round;
import com.fcaj.hackathon.entity.RoundTimer;
import com.fcaj.hackathon.entity.ScoringCriteria;
import com.fcaj.hackathon.entity.Submission;
import com.fcaj.hackathon.entity.Team;
import com.fcaj.hackathon.entity.TeamEventEntry;
import com.fcaj.hackathon.entity.Track;
import com.fcaj.hackathon.exception.BadRequestException;
import com.fcaj.hackathon.repository.JudgeAssignmentRepository;
import com.fcaj.hackathon.repository.RoundRepository;
import com.fcaj.hackathon.repository.RoundTimerNoticeRepository;
import com.fcaj.hackathon.repository.RoundTimerRepository;
import com.fcaj.hackathon.repository.ScoringCriteriaRepository;
import com.fcaj.hackathon.repository.SubmissionRepository;
import com.fcaj.hackathon.repository.TeamEventEntryRepository;
import com.fcaj.hackathon.repository.TeamMemberRepository;
import com.fcaj.hackathon.repository.TeamRepository;
import com.fcaj.hackathon.repository.TrackRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoundTimerServiceTest {

    @Mock RoundTimerRepository timerRepository;
    @Mock RoundTimerNoticeRepository noticeRepository;
    @Mock RoundRepository roundRepository;
    @Mock TrackRepository trackRepository;
    @Mock TeamRepository teamRepository;
    @Mock TeamEventEntryRepository teamEventEntryRepository;
    @Mock TeamMemberRepository teamMemberRepository;
    @Mock JudgeAssignmentRepository judgeAssignmentRepository;
    @Mock ScoringCriteriaRepository scoringCriteriaRepository;
    @Mock SubmissionRepository submissionRepository;
    @Mock NotificationService notificationService;
    @Mock AuditLogService auditLogService;
    @Mock TimerNoticeClaimer noticeClaimer;

    @InjectMocks RoundTimerService service;

    @Test
    void judgingWriteGateFailsClosedWhenTimerWasNeverStarted() {
        when(timerRepository.findByRound_RoundIdAndPhase(2, "JUDGING")).thenReturn(Optional.empty());

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.assertJudgingOpen(2));

        assertTrue(error.getMessage().contains("not started"));
    }

    @Test
    void judgingWriteGateOnlyAllowsRunningTimerWithRemainingTime() {
        Round round = round();
        RoundTimer running = timer(round, "JUDGING", "RUNNING", LocalDateTime.now().plusMinutes(5));
        when(timerRepository.findByRound_RoundIdAndPhase(2, "JUDGING"))
                .thenReturn(Optional.of(running));

        assertDoesNotThrow(() -> service.assertJudgingOpen(2));

        running.setStatus("PAUSED");
        assertThrows(BadRequestException.class, () -> service.assertJudgingOpen(2));

        running.setStatus("RUNNING");
        running.setEndsAt(LocalDateTime.now().minusSeconds(1));
        assertThrows(BadRequestException.class, () -> service.assertJudgingOpen(2));
    }

    @Test
    void judgingReadGateAllowsReadOnlyAccessAfterTimerHasStarted() {
        RoundTimer paused = timer(round(), "JUDGING", "PAUSED", LocalDateTime.now().plusMinutes(5));
        when(timerRepository.findByRound_RoundIdAndPhase(2, "JUDGING"))
                .thenReturn(Optional.of(paused));

        assertDoesNotThrow(() -> service.assertJudgingStarted(2));
    }

    @Test
    void normalJudgeAssignmentMutationIsLockedAfterJudgingStarts() {
        RoundTimer paused = timer(round(), "JUDGING", "PAUSED", LocalDateTime.now().plusMinutes(5));
        when(timerRepository.findByRound_RoundIdAndPhase(2, "JUDGING"))
                .thenReturn(Optional.of(paused));

        assertThrows(BadRequestException.class,
                () -> service.assertJudgeAssignmentsMutable(2));
    }

    @Test
    void controlledJudgeReplacementRequiresCountdownToStopRunning() {
        RoundTimer timer = timer(round(), "JUDGING", "RUNNING", LocalDateTime.now().plusMinutes(5));
        when(timerRepository.findByRound_RoundIdAndPhase(2, "JUDGING"))
                .thenReturn(Optional.of(timer));

        assertThrows(BadRequestException.class,
                () -> service.assertJudgeReplacementAllowed(2));

        timer.setStatus("PAUSED");
        assertDoesNotThrow(() -> service.assertJudgeReplacementAllowed(2));
    }

    @Test
    void startJudgingRequiresCompletedContestTimer() {
        Round round = round();
        when(roundRepository.findByIdAndEventId(2, 1)).thenReturn(Optional.of(round));
        when(timerRepository.findByRound_RoundIdAndPhase(2, "JUDGING")).thenReturn(Optional.empty());
        when(timerRepository.findByRound_RoundIdAndPhase(2, "CONTEST")).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class,
                () -> service.start(10, 1, 2, "JUDGING", startRequest()));

        verify(timerRepository, never()).save(any());
    }

    @Test
    void startJudgingSucceedsWhenStrictPrerequisitesAreSatisfied() {
        Round round = round();
        Track track = Track.builder().trackId(7).event(round.getEvent()).name("Web").build();
        Team team = Team.builder().teamId(8).name("Seal").build();
        TeamEventEntry entry = TeamEventEntry.builder().id(8).team(team).event(round.getEvent()).track(track).build();
        Submission submission = Submission.builder().submissionId(9).round(round).team(team).build();
        when(teamEventEntryRepository.findByTeam_TeamIdAndEvent_EventId(8, 1)).thenReturn(Optional.of(entry));
        JudgeAssignment assignment = JudgeAssignment.builder().round(round).track(track).isActive(true).build();
        RoundTimer contest = timer(round, "CONTEST", "EXPIRED", LocalDateTime.now().minusMinutes(1));

        when(roundRepository.findByIdAndEventId(2, 1)).thenReturn(Optional.of(round));
        when(timerRepository.findByRound_RoundIdAndPhase(2, "JUDGING")).thenReturn(Optional.empty());
        when(timerRepository.findByRound_RoundIdAndPhase(2, "CONTEST")).thenReturn(Optional.of(contest));
        when(scoringCriteriaRepository.findAllByRound_RoundIdOrderByOrderNumber(2))
                .thenReturn(List.of(ScoringCriteria.builder().criteriaId(11).round(round).build()));
        when(submissionRepository.findAllByRound_RoundId(2)).thenReturn(List.of(submission));
        when(judgeAssignmentRepository.findAllByRound_RoundIdAndIsActiveTrue(2))
                .thenReturn(List.of(assignment));

        assertDoesNotThrow(() -> service.start(10, 1, 2, "JUDGING", startRequest()));

        verify(timerRepository).save(any(RoundTimer.class));
    }

    private StartTimerRequest startRequest() {
        StartTimerRequest request = new StartTimerRequest();
        request.setDurationSeconds(300);
        return request;
    }

    private Round round() {
        HackathonEvent event = HackathonEvent.builder()
                .eventId(1).name("Hackathon").status("IN_PROGRESS").build();
        return Round.builder().roundId(2).event(event).name("Round 1").status("ACTIVE").build();
    }

    private RoundTimer timer(Round round, String phase, String status, LocalDateTime endsAt) {
        return RoundTimer.builder()
                .round(round)
                .phase(phase)
                .status(status)
                .durationSeconds(300)
                .startedAt(endsAt.minusMinutes(5))
                .endsAt(endsAt)
                .build();
    }
}
