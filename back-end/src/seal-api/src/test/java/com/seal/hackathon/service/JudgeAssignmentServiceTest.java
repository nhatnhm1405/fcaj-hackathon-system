package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.AssignJudgeRequest;
import com.seal.hackathon.dto.request.ReplaceJudgeRequest;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.JudgeAssignment;
import com.seal.hackathon.entity.Round;
import com.seal.hackathon.entity.Track;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.repository.JudgeAssignmentRepository;
import com.seal.hackathon.repository.RoundRepository;
import com.seal.hackathon.repository.ScoreRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TrackRepository;
import com.seal.hackathon.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JudgeAssignmentServiceTest {

    @Mock
    private JudgeAssignmentRepository judgeAssignmentRepository;

    @Mock
    private TeamEventEntryRepository teamEventEntryRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private TrackRepository trackRepository;

    @Mock
    private ScoreRepository scoreRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private RoundTimerService roundTimerService;

    @Mock
    private EventRoleGranter eventRoleGranter;

    @InjectMocks
    private JudgeAssignmentService judgeAssignmentService;

    @Test
    void assignJudge_shouldRejectWhenEventIsNotSetupOrInProgress() {
        HackathonEvent event = event(1, "OPEN");
        User judge = judge(20, "Judge A");
        Round round = round(2, event, false);
        Track track = track(11, event, "Education Tech");

        AssignJudgeRequest request = assignJudgeRequest(judge.getUserId(), round.getRoundId(), track.getTrackId());

        when(userRepository.findByIdWithRoles(judge.getUserId())).thenReturn(Optional.of(judge));
        when(roundRepository.findById(round.getRoundId())).thenReturn(Optional.of(round));
        when(trackRepository.findById(track.getTrackId())).thenReturn(Optional.of(track));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> judgeAssignmentService.assignJudge(request, 99));

        assertTrue(error.getMessage().contains("SETUP or IN_PROGRESS"));
        verify(judgeAssignmentRepository, never()).save(any(JudgeAssignment.class));
        verify(eventRoleGranter, never()).ensureRole(any(User.class), anyString(), anyInt());
        verify(roundTimerService, never()).assertJudgeAssignmentsMutable(anyInt());
    }

    @Test
    void assignJudge_shouldRejectWhenJudgeAlreadyHasActiveAssignmentInSameRound() {
        HackathonEvent event = event(1);
        User judge = judge(20, "Judge A");
        Round round = round(2, event, false);
        Track targetTrack = track(11, event, "Education Tech");

        AssignJudgeRequest request = assignJudgeRequest(judge.getUserId(), round.getRoundId(), targetTrack.getTrackId());

        when(userRepository.findByIdWithRoles(judge.getUserId())).thenReturn(Optional.of(judge));
        when(roundRepository.findById(round.getRoundId())).thenReturn(Optional.of(round));
        when(trackRepository.findById(targetTrack.getTrackId())).thenReturn(Optional.of(targetTrack));
        when(judgeAssignmentRepository.findByJudge_UserIdAndRound_RoundIdAndTrack_TrackId(
                judge.getUserId(), round.getRoundId(), targetTrack.getTrackId())).thenReturn(Optional.empty());
        when(judgeAssignmentRepository.existsByJudge_UserIdAndRound_RoundIdAndIsActiveTrue(
                judge.getUserId(), round.getRoundId())).thenReturn(true);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> judgeAssignmentService.assignJudge(request, 99));

        assertTrue(error.getMessage().contains("only one track per round"));
        verify(judgeAssignmentRepository, never()).save(any(JudgeAssignment.class));
        verify(eventRoleGranter, never()).ensureRole(any(User.class), anyString(), anyInt());
    }

    @Test
    void assignJudge_shouldAllowSameJudgeOnDifferentRound() {
        HackathonEvent event = event(1);
        User judge = judge(20, "Judge A");
        Round round = round(3, event, false);
        Track track = track(12, event, "Web Application");

        AssignJudgeRequest request = assignJudgeRequest(judge.getUserId(), round.getRoundId(), track.getTrackId());

        when(userRepository.findByIdWithRoles(judge.getUserId())).thenReturn(Optional.of(judge));
        when(roundRepository.findById(round.getRoundId())).thenReturn(Optional.of(round));
        when(trackRepository.findById(track.getTrackId())).thenReturn(Optional.of(track));
        when(judgeAssignmentRepository.findByJudge_UserIdAndRound_RoundIdAndTrack_TrackId(
                judge.getUserId(), round.getRoundId(), track.getTrackId())).thenReturn(Optional.empty());
        when(judgeAssignmentRepository.existsByJudge_UserIdAndRound_RoundIdAndIsActiveTrue(
                judge.getUserId(), round.getRoundId())).thenReturn(false);
        when(judgeAssignmentRepository.save(any(JudgeAssignment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(judge.getUserId())).thenReturn(Optional.of(judge));
        when(judgeAssignmentRepository.findActiveByJudge(judge.getUserId())).thenReturn(List.of());

        judgeAssignmentService.assignJudge(request, 99);

        ArgumentCaptor<JudgeAssignment> captor = ArgumentCaptor.forClass(JudgeAssignment.class);
        verify(judgeAssignmentRepository).save(captor.capture());
        assertEquals(round.getRoundId(), captor.getValue().getRound().getRoundId());
        assertEquals(track.getTrackId(), captor.getValue().getTrack().getTrackId());
        verify(eventRoleGranter).ensureRole(judge, "JUDGE", event.getEventId());
    }

    @Test
    void replaceJudgeAssignment_shouldRejectReplacementAlreadyAssignedInSameRound() {
        HackathonEvent event = event(1);
        User oldJudge = judge(20, "Judge A");
        User replacement = judge(21, "Judge B");
        Round round = round(2, event, false);
        Track targetTrack = track(11, event, "Education Tech");
        JudgeAssignment oldAssignment = JudgeAssignment.builder()
                .id(100)
                .judge(oldJudge)
                .round(round)
                .track(targetTrack)
                .isActive(true)
                .build();

        ReplaceJudgeRequest request = new ReplaceJudgeRequest();
        request.setJudgeUserId(replacement.getUserId());
        request.setReason("Unavailable");

        when(judgeAssignmentRepository.findById(oldAssignment.getId())).thenReturn(Optional.of(oldAssignment));
        when(scoreRepository.findAllByJudge_UserIdAndSubmission_Round_RoundIdAndIsDraftFalse(
                oldJudge.getUserId(), round.getRoundId())).thenReturn(List.of());
        when(userRepository.findByIdWithRoles(replacement.getUserId())).thenReturn(Optional.of(replacement));
        when(judgeAssignmentRepository.findByJudge_UserIdAndRound_RoundIdAndTrack_TrackId(
                replacement.getUserId(), round.getRoundId(), targetTrack.getTrackId())).thenReturn(Optional.empty());
        when(judgeAssignmentRepository.existsByJudge_UserIdAndRound_RoundIdAndIsActiveTrue(
                replacement.getUserId(), round.getRoundId())).thenReturn(true);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> judgeAssignmentService.replaceJudgeAssignment(oldAssignment.getId(), request, 99));

        assertTrue(error.getMessage().contains("only one track per round"));
        assertEquals(true, oldAssignment.getIsActive());
        verify(judgeAssignmentRepository, never()).save(any(JudgeAssignment.class));
    }

    private AssignJudgeRequest assignJudgeRequest(Integer judgeUserId, Integer roundId, Integer trackId) {
        AssignJudgeRequest request = new AssignJudgeRequest();
        request.setJudgeUserId(judgeUserId);
        request.setRoundId(roundId);
        request.setTrackId(trackId);
        return request;
    }

    private HackathonEvent event(Integer eventId) {
        return event(eventId, "SETUP");
    }

    private HackathonEvent event(Integer eventId, String status) {
        return HackathonEvent.builder()
                .eventId(eventId)
                .name("SEAL Hackathon")
                .status(status)
                .build();
    }

    private Round round(Integer roundId, HackathonEvent event, boolean isFinal) {
        return Round.builder()
                .roundId(roundId)
                .event(event)
                .name("Round " + roundId)
                .orderNumber(roundId)
                .isFinal(isFinal)
                .status("PENDING")
                .build();
    }

    private Track track(Integer trackId, HackathonEvent event, String name) {
        return Track.builder()
                .trackId(trackId)
                .event(event)
                .name(name)
                .build();
    }

    private User judge(Integer userId, String fullName) {
        return User.builder()
                .userId(userId)
                .email(userId + "@seal.test")
                .fullName(fullName)
                .userType("STAFF")
                .isApproved(true)
                .isActive(true)
                .build();
    }
}
