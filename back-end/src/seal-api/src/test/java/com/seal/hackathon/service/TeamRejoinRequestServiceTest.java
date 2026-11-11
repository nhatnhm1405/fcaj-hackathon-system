package com.seal.hackathon.service;

import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.entity.TeamRejoinRequest;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.HackathonEventRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TeamRejoinRequestRepository;
import com.seal.hackathon.repository.TeamRepository;
import com.seal.hackathon.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamRejoinRequestServiceTest {

    @Mock private TeamRejoinRequestRepository requestRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private TeamEventEntryRepository teamEventEntryRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private HackathonEventRepository eventRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private TeamRejoinRequestService service;

    // ── requestRejoin ────────────────────────────────────────────────

    @Test
    void requestRejoin_shouldCreatePendingRequest_whenLeaderAndTeamInactiveAndEventOpen() {
        Team team = team(99, false);
        HackathonEvent event = event(1, "OPEN");
        User leader = user(100, "Leader");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(member(1, team, leader, "LEADER")));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(teamEventEntryRepository.existsByTeam_TeamIdAndEvent_EventId(99, 1)).thenReturn(false);
        when(requestRepository.findByTeam_TeamIdAndStatus(99, "PENDING")).thenReturn(Optional.empty());
        when(userRepository.findById(100)).thenReturn(Optional.of(leader));
        when(requestRepository.save(any(TeamRejoinRequest.class))).thenAnswer(inv -> {
            TeamRejoinRequest r = inv.getArgument(0);
            r.setRequestId(500);
            return r;
        });

        var response = service.requestRejoin(100, 99, 1);

        assertEquals(500, response.getRequestId());
        assertEquals("PENDING", response.getStatus());
        assertEquals(1, response.getEventId());
        verify(requestRepository).save(any(TeamRejoinRequest.class));
    }

    @Test
    void requestRejoin_shouldReuseExistingPendingRequest_whenAlreadyOneOutstanding() {
        Team team = team(99, false);
        HackathonEvent event = event(1, "OPEN");
        User leader = user(100, "Leader");
        TeamRejoinRequest existing = TeamRejoinRequest.builder()
                .requestId(400).team(team).eventId(1).requestedBy(leader).status("PENDING")
                .requestedAt(LocalDateTime.now()).build();

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(member(1, team, leader, "LEADER")));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(teamEventEntryRepository.existsByTeam_TeamIdAndEvent_EventId(99, 1)).thenReturn(false);
        when(requestRepository.findByTeam_TeamIdAndStatus(99, "PENDING")).thenReturn(Optional.of(existing));

        var response = service.requestRejoin(100, 99, 1);

        assertEquals(400, response.getRequestId());
        verify(requestRepository, never()).save(any());
    }

    @Test
    void requestRejoin_shouldThrowBadRequest_whenCallerIsNotLeader() {
        Team team = team(99, false);
        User memberUser = user(101, "Member");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(member(1, team, memberUser, "MEMBER")));

        assertThrows(BadRequestException.class, () -> service.requestRejoin(101, 99, 1));

        verify(requestRepository, never()).save(any());
    }

    @Test
    void requestRejoin_shouldThrowBadRequest_whenCallerIsNotTeamMember() {
        Team team = team(99, false);

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of());

        assertThrows(BadRequestException.class, () -> service.requestRejoin(101, 99, 1));

        verify(requestRepository, never()).save(any());
    }

    @Test
    void requestRejoin_shouldThrowResourceNotFound_whenTeamDoesNotExist() {
        when(teamRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.requestRejoin(100, 99, 1));
    }

    @Test
    void requestRejoin_shouldThrowBadRequest_whenTeamIsAlreadyActive() {
        Team team = team(99, true);
        User leader = user(100, "Leader");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(member(1, team, leader, "LEADER")));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event(1, "OPEN")));

        assertThrows(BadRequestException.class, () -> service.requestRejoin(100, 99, 1));

        verify(requestRepository, never()).save(any());
    }

    @Test
    void requestRejoin_shouldThrowBadRequest_whenTargetEventIsNotOpen() {
        Team team = team(99, false);
        User leader = user(100, "Leader");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(member(1, team, leader, "LEADER")));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event(1, "SETUP")));

        assertThrows(BadRequestException.class, () -> service.requestRejoin(100, 99, 1));

        verify(requestRepository, never()).save(any());
    }

    @Test
    void requestRejoin_shouldThrowBadRequest_whenTeamAlreadyHasEntryForTargetEvent() {
        Team team = team(99, false);
        User leader = user(100, "Leader");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(member(1, team, leader, "LEADER")));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event(1, "OPEN")));
        when(teamEventEntryRepository.existsByTeam_TeamIdAndEvent_EventId(99, 1)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.requestRejoin(100, 99, 1));

        verify(requestRepository, never()).save(any());
    }

    // ── approve ──────────────────────────────────────────────────────

    @Test
    void approve_shouldCreateApprovedEntryAndActivateTeamAndNotifyRoster() {
        Team team = team(99, false);
        HackathonEvent event = event(1, "OPEN");
        User leader = user(100, "Leader");
        User memberUser = user(101, "Member");
        TeamRejoinRequest request = TeamRejoinRequest.builder()
                .requestId(500).team(team).eventId(1).requestedBy(leader).status("PENDING")
                .requestedAt(LocalDateTime.now()).build();

        when(requestRepository.findById(500)).thenReturn(Optional.of(request));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(teamEventEntryRepository.save(any(TeamEventEntry.class))).thenAnswer(inv -> {
            TeamEventEntry e = inv.getArgument(0);
            e.setId(900);
            return e;
        });
        when(teamMemberRepository.findByTeam_TeamId(99))
                .thenReturn(List.of(member(1, team, leader, "LEADER"), member(2, team, memberUser, "MEMBER")));

        var response = service.approve(500, 7);

        assertEquals("APPROVED", response.getStatus());
        assertTrue(team.getIsActive());
        verify(teamEventEntryRepository).save(any(TeamEventEntry.class));
        verify(teamRepository).save(team);
        verify(notificationService).createNotification(eq(100), any(), any(), eq("TEAM_REJOIN_APPROVED"));
        verify(notificationService).createNotification(eq(101), any(), any(), eq("TEAM_REJOIN_APPROVED"));
        verify(auditLogService).record(eq(7), eq("APPROVE_TEAM_REJOIN"), eq("TEAM"), eq(99), any(), any());
    }

    @Test
    void approve_shouldThrowResourceNotFound_whenRequestDoesNotExist() {
        when(requestRepository.findById(500)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.approve(500, 7));

        verify(teamEventEntryRepository, never()).save(any());
    }

    @Test
    void approve_shouldThrowBadRequest_whenRequestAlreadyResolved() {
        Team team = team(99, false);
        TeamRejoinRequest request = TeamRejoinRequest.builder()
                .requestId(500).team(team).eventId(1).requestedBy(user(100, "Leader")).status("APPROVED")
                .requestedAt(LocalDateTime.now()).build();

        when(requestRepository.findById(500)).thenReturn(Optional.of(request));

        assertThrows(BadRequestException.class, () -> service.approve(500, 7));

        verify(teamEventEntryRepository, never()).save(any());
    }

    // ── reject ───────────────────────────────────────────────────────

    @Test
    void reject_shouldResolveRequestAndNotifyRequesterOnly_withoutTouchingTeam() {
        Team team = team(99, false);
        HackathonEvent event = event(1, "OPEN");
        User leader = user(100, "Leader");
        TeamRejoinRequest request = TeamRejoinRequest.builder()
                .requestId(500).team(team).eventId(1).requestedBy(leader).status("PENDING")
                .requestedAt(LocalDateTime.now()).build();

        when(requestRepository.findById(500)).thenReturn(Optional.of(request));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event));

        var response = service.reject(500, 7);

        assertEquals("REJECTED", response.getStatus());
        assertEquals(7, request.getResolvedBy());
        assertTrue(!team.getIsActive());
        verify(teamEventEntryRepository, never()).save(any());
        verify(notificationService).createNotification(eq(100), any(), any(), eq("TEAM_REJOIN_REJECTED"));
        verify(notificationService, times(1)).createNotification(any(), any(), any(), eq("TEAM_REJOIN_REJECTED"));
        verify(auditLogService).record(eq(7), eq("REJECT_TEAM_REJOIN"), eq("TEAM"), eq(99), any(), any());
    }

    // ── listPending ──────────────────────────────────────────────────

    @Test
    void listPending_shouldMapEachRequestWithItsEvent() {
        Team team = team(99, false);
        HackathonEvent event = event(1, "OPEN");
        TeamRejoinRequest request = TeamRejoinRequest.builder()
                .requestId(500).team(team).eventId(1).requestedBy(user(100, "Leader")).status("PENDING")
                .requestedAt(LocalDateTime.now()).build();

        when(requestRepository.findByStatusOrderByRequestedAtDesc("PENDING")).thenReturn(List.of(request));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event));

        var response = service.listPending();

        assertEquals(1, response.size());
        assertEquals("SEAL Hackathon 1", response.get(0).getEventName());
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static HackathonEvent event(Integer id, String status) {
        return HackathonEvent.builder()
                .eventId(id).name("SEAL Hackathon " + id).season("SPRING").year(2026).status(status)
                .build();
    }

    private static Team team(Integer id, boolean isActive) {
        return Team.builder().teamId(id).name("Seal Team").isActive(isActive).build();
    }

    private static User user(Integer id, String name) {
        return User.builder()
                .userId(id).email(name.toLowerCase() + "@example.com").fullName(name)
                .userType("FPT_STUDENT").isApproved(true).isActive(true)
                .build();
    }

    private static TeamMember member(Integer id, Team team, User user, String role) {
        return TeamMember.builder().id(id).team(team).user(user).memberRole(role).build();
    }
}
