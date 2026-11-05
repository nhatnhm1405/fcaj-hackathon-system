package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.CreateInviteRequest;
import com.seal.hackathon.dto.response.TeamInviteResponse;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.TeamInvite;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.entity.Track;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ForbiddenException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamInviteRepository;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamInviteServiceTest {

    @Mock
    private TeamInviteRepository inviteRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamEventEntryRepository teamEventEntryRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    private TeamInviteService teamInviteService;

    @BeforeEach
    void setUp() {
        TeamAccessGuard teamAccessGuard = new TeamAccessGuard(teamRepository, teamMemberRepository, teamEventEntryRepository);
        teamInviteService = new TeamInviteService(
                inviteRepository, teamRepository, teamEventEntryRepository, teamMemberRepository,
                userRepository, notificationService, teamAccessGuard);
    }

    @Test
    void createInvite_shouldCreatePendingInviteWithTrimmedMessage_whenRequestIsValid() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, "APPROVED");
        User inviter = user(100, "Leader", "FPT_STUDENT", true, true);
        User invited = user(101, "Member", "EXTERNAL_STUDENT", true, true);
        CreateInviteRequest request = inviteRequest(101, "  Please join  ");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(userRepository.findById(100)).thenReturn(Optional.of(inviter));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(member(1, team, inviter, "LEADER")));
        when(teamMemberRepository.countByTeam_TeamId(99)).thenReturn(1L);
        when(userRepository.findById(101)).thenReturn(Optional.of(invited));
        when(teamMemberRepository.existsByUser_UserIdAndTeam_Event_EventId(101, 1)).thenReturn(false);
        when(inviteRepository.findByTeamIdAndInvitedUserIdForUpdate(99, 101)).thenReturn(Optional.empty());
        when(inviteRepository.saveAndFlush(any(TeamInvite.class))).thenAnswer(invocation -> {
            TeamInvite invite = invocation.getArgument(0);
            invite.setInviteId(500);
            return invite;
        });

        TeamInviteResponse response = teamInviteService.createInvite(100, 99, request);

        assertEquals(500, response.getInviteId());
        assertEquals("Please join", response.getMessage());
        assertEquals("PENDING", response.getStatus());
        verify(inviteRepository).saveAndFlush(any(TeamInvite.class));
        verify(notificationService).createNotification(eq(101), eq("Team invitation"), any(), eq("TEAM_INVITE"));
    }

    @Test
    void createInvite_shouldNormalizeBlankMessageToNull() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, "APPROVED");
        User inviter = user(100, "Leader", "FPT_STUDENT", true, true);
        User invited = user(101, "Member", "FPT_STUDENT", true, true);

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(userRepository.findById(100)).thenReturn(Optional.of(inviter));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(member(1, team, inviter, "LEADER")));
        when(teamMemberRepository.countByTeam_TeamId(99)).thenReturn(1L);
        when(userRepository.findById(101)).thenReturn(Optional.of(invited));
        when(teamMemberRepository.existsByUser_UserIdAndTeam_Event_EventId(101, 1)).thenReturn(false);
        when(inviteRepository.findByTeamIdAndInvitedUserIdForUpdate(99, 101)).thenReturn(Optional.empty());
        when(inviteRepository.saveAndFlush(any(TeamInvite.class))).thenAnswer(invocation -> {
            TeamInvite invite = invocation.getArgument(0);
            invite.setInviteId(500);
            return invite;
        });

        TeamInviteResponse response = teamInviteService.createInvite(100, 99, inviteRequest(101, "   "));

        assertNull(response.getMessage());
    }

    @Test
    void createInvite_shouldCreatePendingInvite_whenEventIsSetup() {
        HackathonEvent event = event(1, "SETUP");
        Team team = team(99, event, "APPROVED");
        User inviter = user(100, "Leader", "FPT_STUDENT", true, true);
        User invited = user(101, "Member", "FPT_STUDENT", true, true);

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(userRepository.findById(100)).thenReturn(Optional.of(inviter));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(member(1, team, inviter, "LEADER")));
        when(teamMemberRepository.countByTeam_TeamId(99)).thenReturn(1L);
        when(userRepository.findById(101)).thenReturn(Optional.of(invited));
        when(teamMemberRepository.existsByUser_UserIdAndTeam_Event_EventId(101, 1)).thenReturn(false);
        when(inviteRepository.findByTeamIdAndInvitedUserIdForUpdate(99, 101)).thenReturn(Optional.empty());
        when(inviteRepository.saveAndFlush(any(TeamInvite.class))).thenAnswer(invocation -> {
            TeamInvite invite = invocation.getArgument(0);
            invite.setInviteId(500);
            return invite;
        });

        TeamInviteResponse response = teamInviteService.createInvite(100, 99, inviteRequest(101, null));

        assertEquals(500, response.getInviteId());
        assertEquals("PENDING", response.getStatus());
        verify(notificationService).createNotification(eq(101), eq("Team invitation"), any(), eq("TEAM_INVITE"));
    }

    @Test
    void createInvite_shouldThrowBadRequest_whenInputIsNull() {
        assertThrows(BadRequestException.class, () -> teamInviteService.createInvite(null, 99, inviteRequest(101, null)));
        assertThrows(BadRequestException.class, () -> teamInviteService.createInvite(100, null, inviteRequest(101, null)));
        assertThrows(BadRequestException.class, () -> teamInviteService.createInvite(100, 99, null));
        assertThrows(BadRequestException.class, () -> teamInviteService.createInvite(100, 99, inviteRequest(null, null)));

        verifyNoInteractions(teamRepository, userRepository, inviteRepository, notificationService);
    }

    @Test
    void createInvite_shouldThrowBadRequest_whenMessageIsTooLong() {
        String message = "x".repeat(1001);

        assertThrows(BadRequestException.class,
                () -> teamInviteService.createInvite(100, 99, inviteRequest(101, message)));

        verifyNoInteractions(teamRepository, userRepository, inviteRepository, notificationService);
    }

    @Test
    void createInvite_shouldThrowResourceNotFound_whenTeamDoesNotExist() {
        when(teamRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> teamInviteService.createInvite(100, 99, inviteRequest(101, null)));

        verify(userRepository, never()).findById(anyInt());
        verify(inviteRepository, never()).saveAndFlush(any());
    }

    @Test
    void createInvite_shouldThrowBadRequest_whenInviterIsNotApprovedOrWritable() {
        Team team = team(99, event(1, "OPEN"), "APPROVED");
        User readOnlyInviter = user(100, "Leader", "FPT_STUDENT", true, false);

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(userRepository.findById(100)).thenReturn(Optional.of(readOnlyInviter));

        assertThrows(BadRequestException.class,
                () -> teamInviteService.createInvite(100, 99, inviteRequest(101, null)));

        verify(teamMemberRepository, never()).findByTeam_TeamId(anyInt());
        verify(inviteRepository, never()).saveAndFlush(any());
    }

    @Test
    void createInvite_shouldThrowForbidden_whenInviterIsNotLeader() {
        Team team = team(99, event(1, "OPEN"), "APPROVED");
        User inviter = user(100, "Member", "FPT_STUDENT", true, true);

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(userRepository.findById(100)).thenReturn(Optional.of(inviter));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(member(1, team, inviter, "MEMBER")));

        assertThrows(ForbiddenException.class,
                () -> teamInviteService.createInvite(100, 99, inviteRequest(101, null)));

        verify(inviteRepository, never()).saveAndFlush(any());
    }

    @Test
    void createInvite_shouldCreatePendingInvite_whenTeamIsPendingApproval() {
        // A not-yet-approved team may still build its roster (approval only gates track select).
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, "PENDING");
        User inviter = user(100, "Leader", "FPT_STUDENT", true, true);
        User invited = user(101, "Member", "FPT_STUDENT", true, true);

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(userRepository.findById(100)).thenReturn(Optional.of(inviter));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(member(1, team, inviter, "LEADER")));
        when(teamMemberRepository.countByTeam_TeamId(99)).thenReturn(1L);
        when(userRepository.findById(101)).thenReturn(Optional.of(invited));
        when(teamMemberRepository.existsByUser_UserIdAndTeam_Event_EventId(101, 1)).thenReturn(false);
        when(inviteRepository.findByTeamIdAndInvitedUserIdForUpdate(99, 101)).thenReturn(Optional.empty());
        when(inviteRepository.saveAndFlush(any(TeamInvite.class))).thenAnswer(invocation -> {
            TeamInvite invite = invocation.getArgument(0);
            invite.setInviteId(500);
            return invite;
        });

        TeamInviteResponse response = teamInviteService.createInvite(100, 99, inviteRequest(101, null));

        assertEquals("PENDING", response.getStatus());
        verify(inviteRepository).saveAndFlush(any(TeamInvite.class));
    }

    @Test
    void createInvite_shouldThrowBadRequest_whenTeamIsRejected() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, "REJECTED");
        User inviter = user(100, "Leader", "FPT_STUDENT", true, true);

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(userRepository.findById(100)).thenReturn(Optional.of(inviter));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(member(1, team, inviter, "LEADER")));

        assertThrows(BadRequestException.class,
                () -> teamInviteService.createInvite(100, 99, inviteRequest(101, null)));

        verify(userRepository, never()).findById(101);
        verify(inviteRepository, never()).saveAndFlush(any());
    }

    @Test
    void createInvite_shouldThrowBadRequest_whenEventIsNotOpen() {
        HackathonEvent event = event(1, "CLOSED");
        Team team = team(99, event, "APPROVED");
        User inviter = user(100, "Leader", "FPT_STUDENT", true, true);

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(userRepository.findById(100)).thenReturn(Optional.of(inviter));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(member(1, team, inviter, "LEADER")));

        assertThrows(BadRequestException.class,
                () -> teamInviteService.createInvite(100, 99, inviteRequest(101, null)));

        verify(userRepository, never()).findById(101);
        verify(inviteRepository, never()).saveAndFlush(any());
    }

    @Test
    void createInvite_shouldThrowBadRequest_whenTeamIsFull() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, "APPROVED");
        User inviter = user(100, "Leader", "FPT_STUDENT", true, true);

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(userRepository.findById(100)).thenReturn(Optional.of(inviter));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(member(1, team, inviter, "LEADER")));
        when(teamMemberRepository.countByTeam_TeamId(99)).thenReturn(5L);

        assertThrows(BadRequestException.class,
                () -> teamInviteService.createInvite(100, 99, inviteRequest(101, null)));

        verify(userRepository, never()).findById(101);
        verify(inviteRepository, never()).saveAndFlush(any());
    }

    @Test
    void createInvite_shouldThrowBadRequest_whenInvitedUserIsNotActiveStudentParticipant() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, "APPROVED");
        User inviter = user(100, "Leader", "FPT_STUDENT", true, true);
        User staff = user(101, "Staff", "STAFF", true, true);

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(userRepository.findById(100)).thenReturn(Optional.of(inviter));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(member(1, team, inviter, "LEADER")));
        when(teamMemberRepository.countByTeam_TeamId(99)).thenReturn(1L);
        when(userRepository.findById(101)).thenReturn(Optional.of(staff));

        assertThrows(BadRequestException.class,
                () -> teamInviteService.createInvite(100, 99, inviteRequest(101, null)));

        verify(inviteRepository, never()).saveAndFlush(any());
    }

    @Test
    void createInvite_shouldThrowBadRequest_whenInvitedUserAlreadyBelongsToEventTeam() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, "APPROVED");
        User inviter = user(100, "Leader", "FPT_STUDENT", true, true);
        User invited = user(101, "Member", "FPT_STUDENT", true, true);

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(userRepository.findById(100)).thenReturn(Optional.of(inviter));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(member(1, team, inviter, "LEADER")));
        when(teamMemberRepository.countByTeam_TeamId(99)).thenReturn(1L);
        when(userRepository.findById(101)).thenReturn(Optional.of(invited));
        when(teamMemberRepository.existsByUser_UserIdAndTeam_Event_EventId(101, 1)).thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> teamInviteService.createInvite(100, 99, inviteRequest(101, null)));

        verify(inviteRepository, never()).saveAndFlush(any());
    }

    @Test
    void createInvite_shouldThrowBadRequest_whenPendingInviteAlreadyExists() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, "APPROVED");
        User inviter = user(100, "Leader", "FPT_STUDENT", true, true);
        User invited = user(101, "Member", "FPT_STUDENT", true, true);

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(userRepository.findById(100)).thenReturn(Optional.of(inviter));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(member(1, team, inviter, "LEADER")));
        when(teamMemberRepository.countByTeam_TeamId(99)).thenReturn(1L);
        when(userRepository.findById(101)).thenReturn(Optional.of(invited));
        when(teamMemberRepository.existsByUser_UserIdAndTeam_Event_EventId(101, 1)).thenReturn(false);
        when(inviteRepository.findByTeamIdAndInvitedUserIdForUpdate(99, 101))
                .thenReturn(Optional.of(invite(500, team, invited, inviter, "PENDING")));

        assertThrows(BadRequestException.class,
                () -> teamInviteService.createInvite(100, 99, inviteRequest(101, null)));

        verify(inviteRepository, never()).saveAndFlush(any());
    }

    @Test
    void getPendingInvites_shouldReturnMappedPendingInvites() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, "APPROVED");
        User invited = user(101, "Member", "FPT_STUDENT", true, true);
        User inviter = user(100, "Leader", "FPT_STUDENT", true, true);
        TeamInvite invite = invite(500, team, invited, inviter, "PENDING");

        when(userRepository.findById(101)).thenReturn(Optional.of(invited));
        when(inviteRepository.findByInvitedUser_UserIdAndStatus(101, "PENDING")).thenReturn(List.of(invite));

        List<TeamInviteResponse> response = teamInviteService.getPendingInvites(101);

        assertEquals(1, response.size());
        assertEquals(500, response.get(0).getInviteId());
    }

    @Test
    void getPendingInvites_shouldThrowBadRequest_whenUserIdIsNull() {
        assertThrows(BadRequestException.class, () -> teamInviteService.getPendingInvites(null));

        verifyNoInteractions(userRepository, inviteRepository);
    }

    @Test
    void getPendingInvites_shouldThrowResourceNotFound_whenUserDoesNotExist() {
        when(userRepository.findById(101)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamInviteService.getPendingInvites(101));

        verify(inviteRepository, never()).findByInvitedUser_UserIdAndStatus(anyInt(), any());
    }

    @Test
    void acceptInvite_shouldAcceptInviteAndDeclineOtherPendingInvites_whenRequestIsValid() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, "APPROVED");
        User leader = user(100, "Leader", "FPT_STUDENT", true, true);
        User invited = user(101, "Member", "FPT_STUDENT", true, true);
        TeamInvite invite = invite(500, team, invited, leader, "PENDING");
        TeamInvite other = invite(501, team(1000, event, "APPROVED"), invited, leader, "PENDING");

        when(userRepository.findById(101)).thenReturn(Optional.of(invited));
        when(inviteRepository.findByIdForUpdate(500)).thenReturn(Optional.of(invite));
        when(teamRepository.findByIdForUpdate(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.countByTeam_TeamId(99)).thenReturn(4L);
        when(teamMemberRepository.existsByUser_UserIdAndTeam_Event_EventId(101, 1)).thenReturn(false);
        when(inviteRepository.saveAndFlush(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(member(1, team, leader, "LEADER")));
        when(inviteRepository.findByInvitedUser_UserIdAndStatusAndEventId(101, "PENDING", 1))
                .thenReturn(List.of(invite, other));

        TeamInviteResponse response = teamInviteService.acceptInvite(101, 500);

        assertEquals("ACCEPTED", response.getStatus());
        assertEquals("DECLINED", other.getStatus());
        verify(teamMemberRepository).save(any(TeamMember.class));
        verify(inviteRepository).saveAll(List.of(other));
        verify(notificationService).createNotification(eq(100), eq("Invitation accepted"), any(), eq("TEAM_INVITE_ACCEPTED"));
    }

    @Test
    void acceptInvite_shouldThrowBadRequest_whenInviteIdIsNull() {
        assertThrows(BadRequestException.class, () -> teamInviteService.acceptInvite(101, null));

        verifyNoInteractions(userRepository, inviteRepository, teamRepository);
    }

    @Test
    void acceptInvite_shouldThrowForbidden_whenInviteBelongsToAnotherUser() {
        Team team = team(99, event(1, "OPEN"), "APPROVED");
        User caller = user(101, "Caller", "FPT_STUDENT", true, true);
        User otherUser = user(102, "Other", "FPT_STUDENT", true, true);
        User leader = user(100, "Leader", "FPT_STUDENT", true, true);

        when(userRepository.findById(101)).thenReturn(Optional.of(caller));
        when(inviteRepository.findByIdForUpdate(500)).thenReturn(Optional.of(invite(500, team, otherUser, leader, "PENDING")));

        assertThrows(ForbiddenException.class, () -> teamInviteService.acceptInvite(101, 500));

        verify(teamRepository, never()).findByIdForUpdate(anyInt());
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void acceptInvite_shouldThrowBadRequest_whenInviteIsNotPending() {
        Team team = team(99, event(1, "OPEN"), "APPROVED");
        User invited = user(101, "Member", "FPT_STUDENT", true, true);
        User leader = user(100, "Leader", "FPT_STUDENT", true, true);

        when(userRepository.findById(101)).thenReturn(Optional.of(invited));
        when(inviteRepository.findByIdForUpdate(500)).thenReturn(Optional.of(invite(500, team, invited, leader, "ACCEPTED")));

        assertThrows(BadRequestException.class, () -> teamInviteService.acceptInvite(101, 500));

        verify(teamRepository, never()).findByIdForUpdate(anyInt());
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void acceptInvite_shouldThrowBadRequest_whenTeamIsFullAtAcceptTime() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, "APPROVED");
        User invited = user(101, "Member", "FPT_STUDENT", true, true);
        User leader = user(100, "Leader", "FPT_STUDENT", true, true);

        when(userRepository.findById(101)).thenReturn(Optional.of(invited));
        when(inviteRepository.findByIdForUpdate(500)).thenReturn(Optional.of(invite(500, team, invited, leader, "PENDING")));
        when(teamRepository.findByIdForUpdate(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.countByTeam_TeamId(99)).thenReturn(5L);

        assertThrows(BadRequestException.class, () -> teamInviteService.acceptInvite(101, 500));

        verify(inviteRepository, never()).saveAndFlush(any());
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void declineInvite_shouldDeclinePendingInvite_whenRequestIsValid() {
        Team team = team(99, event(1, "OPEN"), "APPROVED");
        User invited = user(101, "Member", "FPT_STUDENT", true, true);
        User leader = user(100, "Leader", "FPT_STUDENT", true, true);
        TeamInvite invite = invite(500, team, invited, leader, "PENDING");

        when(userRepository.findById(101)).thenReturn(Optional.of(invited));
        when(inviteRepository.findByIdForUpdate(500)).thenReturn(Optional.of(invite));
        when(inviteRepository.saveAndFlush(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TeamInviteResponse response = teamInviteService.declineInvite(101, 500);

        assertEquals("DECLINED", response.getStatus());
        verify(inviteRepository).saveAndFlush(invite);
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void declineInvite_shouldThrowBadRequest_whenInviteIdIsNull() {
        assertThrows(BadRequestException.class, () -> teamInviteService.declineInvite(101, null));

        verifyNoInteractions(userRepository, inviteRepository);
    }

    @Test
    void declineInvite_shouldThrowResourceNotFound_whenInviteDoesNotExist() {
        User invited = user(101, "Member", "FPT_STUDENT", true, true);

        when(userRepository.findById(101)).thenReturn(Optional.of(invited));
        when(inviteRepository.findByIdForUpdate(500)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamInviteService.declineInvite(101, 500));

        verify(inviteRepository, never()).saveAndFlush(any());
    }

    private static CreateInviteRequest inviteRequest(Integer invitedUserId, String message) {
        CreateInviteRequest request = new CreateInviteRequest();
        request.setInvitedUserId(invitedUserId);
        request.setMessage(message);
        return request;
    }

    private static HackathonEvent event(Integer id, String status) {
        LocalDateTime now = LocalDateTime.now();
        return HackathonEvent.builder()
                .eventId(id)
                .name("Hackathon")
                .season("SPRING")
                .year(2026)
                .status(status)
                .registrationStart(now.minusDays(1))
                .registrationEnd(now.plusDays(1))
                .startDate(now.plusDays(2))
                .endDate(now.plusDays(3))
                .build();
    }

    private Team team(Integer id, HackathonEvent event, String status) {
        Team team = Team.builder()
                .teamId(id)
                .name("Seal Team")
                .build();
        TeamEventEntry entry = TeamEventEntry.builder()
                .id(id)
                .team(team)
                .event(event)
                .track(track(10, event))
                .status(status)
                .build();
        lenient().when(teamEventEntryRepository.findTopByTeam_TeamIdOrderByIdDesc(id))
                .thenReturn(Optional.of(entry));
        lenient().when(teamEventEntryRepository.findByTeam_TeamIdAndEvent_EventId(id, event.getEventId()))
                .thenReturn(Optional.of(entry));
        return team;
    }

    private static Track track(Integer id, HackathonEvent event) {
        return Track.builder()
                .trackId(id)
                .event(event)
                .name("AI")
                .build();
    }

    private static User user(Integer id, String name, String userType, Boolean approved, Boolean active) {
        return User.builder()
                .userId(id)
                .email(name.toLowerCase() + "@example.com")
                .fullName(name)
                .userType(userType)
                .isApproved(approved)
                .isActive(active)
                .build();
    }

    private static TeamMember member(Integer id, Team team, User user, String role) {
        return TeamMember.builder()
                .id(id)
                .team(team)
                .user(user)
                .memberRole(role)
                .build();
    }

    private static TeamInvite invite(Integer id, Team team, User invitedUser, User invitedBy, String status) {
        return TeamInvite.builder()
                .inviteId(id)
                .team(team)
                .eventId(1)
                .invitedUser(invitedUser)
                .invitedBy(invitedBy)
                .message("hello")
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
