package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.CreateJoinRequestRequest;
import com.seal.hackathon.dto.response.JoinRequestResponse;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.JoinRequest;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.repository.JoinRequestRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TeamRepository;
import com.seal.hackathon.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the roster-building rule for join requests: a not-yet-approved (PENDING) team
 * may still receive join requests; only rejected/disqualified teams are closed off.
 */
@ExtendWith(MockitoExtension.class)
class JoinRequestServiceTest {

    @Mock private JoinRequestRepository joinRequestRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private TeamEventEntryRepository teamEventEntryRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    private JoinRequestService joinRequestService;

    @BeforeEach
    void setUp() {
        TeamAccessGuard teamAccessGuard = new TeamAccessGuard(teamRepository, teamMemberRepository, teamEventEntryRepository);
        joinRequestService = new JoinRequestService(
                joinRequestRepository, teamRepository, teamEventEntryRepository, teamMemberRepository,
                userRepository, notificationService, teamAccessGuard);
    }

    @Test
    void createJoinRequest_shouldSucceed_whenTeamIsPendingApproval() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, "PENDING");
        User requester = user(101, "Member", "FPT_STUDENT", true, true);
        User leader = user(100, "Leader", "FPT_STUDENT", true, true);

        when(userRepository.findById(101)).thenReturn(Optional.of(requester));
        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.countByTeam_TeamId(99)).thenReturn(1L);
        when(teamMemberRepository.existsByUser_UserIdAndTeam_Event_EventId(101, 1)).thenReturn(false);
        when(joinRequestRepository.findByTeam_TeamIdAndRequester_UserId(99, 101)).thenReturn(Optional.empty());
        when(joinRequestRepository.save(any(JoinRequest.class))).thenAnswer(invocation -> {
            JoinRequest jr = invocation.getArgument(0);
            jr.setRequestId(500);
            return jr;
        });
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(member(1, team, leader, "LEADER")));

        JoinRequestResponse response = joinRequestService.createJoinRequest(101, 99, request("let me in"));

        assertEquals("PENDING", response.getStatus());
        verify(joinRequestRepository).save(any(JoinRequest.class));
        verify(notificationService).createNotification(eq(100), eq("New join request"), any(), eq("JOIN_REQUEST"));
    }

    @Test
    void createJoinRequest_shouldThrow_whenTeamIsRejected() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, "REJECTED");
        User requester = user(101, "Member", "FPT_STUDENT", true, true);

        when(userRepository.findById(101)).thenReturn(Optional.of(requester));
        when(teamRepository.findById(99)).thenReturn(Optional.of(team));

        assertThrows(BadRequestException.class,
                () -> joinRequestService.createJoinRequest(101, 99, request(null)));

        verify(joinRequestRepository, never()).save(any());
    }

    // Helpers

    private static CreateJoinRequestRequest request(String message) {
        CreateJoinRequestRequest r = new CreateJoinRequestRequest();
        r.setMessage(message);
        return r;
    }

    private static HackathonEvent event(Integer id, String status) {
        return HackathonEvent.builder()
                .eventId(id).name("Hackathon").season("SPRING").year(2026).status(status)
                .build();
    }

    private Team team(Integer id, HackathonEvent event, String status) {
        Team team = Team.builder().teamId(id).name("Seal Team").build();
        TeamEventEntry entry = TeamEventEntry.builder()
                .id(id).team(team).event(event).status(status).build();
        lenient().when(teamEventEntryRepository.findTopByTeam_TeamIdOrderByIdDesc(id))
                .thenReturn(Optional.of(entry));
        return team;
    }

    private static User user(Integer id, String name, String userType, Boolean approved, Boolean active) {
        return User.builder()
                .userId(id).email(name.toLowerCase() + "@example.com").fullName(name)
                .userType(userType).isApproved(approved).isActive(active)
                .build();
    }

    private static TeamMember member(Integer id, Team team, User user, String role) {
        return TeamMember.builder().id(id).team(team).user(user).memberRole(role).build();
    }
}
