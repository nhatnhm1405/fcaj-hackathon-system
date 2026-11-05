package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.CreateTeamRequest;
import com.seal.hackathon.dto.request.UpdateTeamRequest;
import com.seal.hackathon.dto.response.MyTeamResponse;
import com.seal.hackathon.dto.response.TeamResponse;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.entity.Track;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.HackathonEventRepository;
import com.seal.hackathon.repository.JoinRequestRepository;
import com.seal.hackathon.repository.RoundRepository;
import com.seal.hackathon.repository.RoundResultRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamInviteRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TeamRejoinRequestRepository;
import com.seal.hackathon.repository.TeamRepository;
import com.seal.hackathon.repository.TrackRepository;
import com.seal.hackathon.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamMembershipServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamEventEntryRepository teamEventEntryRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private HackathonEventRepository eventRepository;

    @Mock
    private TrackRepository trackRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoundResultRepository roundResultRepository;

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private JoinRequestRepository joinRequestRepository;

    @Mock
    private TeamInviteRepository teamInviteRepository;

    @Mock
    private TeamRejoinRequestRepository teamRejoinRequestRepository;

    @Mock
    private ParticipantHistorySnapshotService participantHistorySnapshotService;

    @Mock
    private NotificationService notificationService;

    private TeamMembershipService teamService;

    private final Map<Integer, TeamEventEntry> entriesByTeamId = new HashMap<>();

    @BeforeEach
    void setUp() {
        TeamAccessGuard teamAccessGuard = new TeamAccessGuard(teamRepository, teamMemberRepository, teamEventEntryRepository);
        TeamResponseMapper teamResponseMapper = new TeamResponseMapper(
                teamMemberRepository, roundRepository, roundResultRepository, teamRejoinRequestRepository, teamAccessGuard);
        TeamQueryService teamQueryService = new TeamQueryService(
                teamRepository, teamEventEntryRepository, teamMemberRepository, eventRepository, trackRepository,
                userRepository, participantHistorySnapshotService, teamAccessGuard, teamResponseMapper);
        teamService = new TeamMembershipService(
                teamRepository, teamEventEntryRepository, teamMemberRepository, eventRepository, userRepository,
                joinRequestRepository, teamInviteRepository, participantHistorySnapshotService, teamAccessGuard,
                teamResponseMapper, teamQueryService, notificationService);
    }

    @Test
    void createTeam_shouldCreatePendingTeamAndLeaderMember_whenRequestIsValid() {
        User user = user(100, "Leader");
        HackathonEvent event = event(1, "OPEN");
        CreateTeamRequest request = createTeamRequest(" Seal Team ");

        when(userRepository.findById(100)).thenReturn(Optional.of(user));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(teamEventEntryRepository.existsByEventIdAndNormalizedName(1, "SEAL TEAM")).thenReturn(false);
        when(teamMemberRepository.existsByUser_UserIdAndTeam_Event_EventId(100, 1)).thenReturn(false);
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> {
            Team team = invocation.getArgument(0);
            team.setTeamId(99);
            return team;
        });
        when(teamEventEntryRepository.save(any(TeamEventEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TeamResponse response = teamService.createTeam(100, request);

        assertEquals(99, response.getTeamId());
        assertEquals("Seal Team", response.getName());
        assertNull(response.getTrackId());
        assertNull(response.getTrackName());
        assertEquals("PENDING", response.getStatus());
        verify(teamRepository).save(any(Team.class));
        verify(teamMemberRepository).save(any(TeamMember.class));
    }

    @Test
    void createTeam_shouldThrowBadRequest_whenEventIsNotOpen() {
        CreateTeamRequest request = createTeamRequest("Seal Team");

        when(userRepository.findById(100)).thenReturn(Optional.of(user(100, "Leader")));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event(1, "CLOSED")));

        assertThrows(BadRequestException.class, () -> teamService.createTeam(100, request));

        verify(teamRepository, never()).save(any());
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void createTeam_shouldThrowBadRequest_whenUserIsReadOnly() {
        CreateTeamRequest request = createTeamRequest("Seal Team");
        User user = user(100, "Leader");
        user.setIsActive(false);

        when(userRepository.findById(100)).thenReturn(Optional.of(user));

        assertThrows(BadRequestException.class, () -> teamService.createTeam(100, request));

        verify(eventRepository, never()).findById(anyInt());
        verify(teamRepository, never()).save(any());
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void createTeam_shouldThrowBadRequest_whenNormalizedTeamNameAlreadyExists() {
        User user = user(100, "Leader");
        HackathonEvent event = event(1, "OPEN");

        when(userRepository.findById(100)).thenReturn(Optional.of(user));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(teamEventEntryRepository.existsByEventIdAndNormalizedName(1, "SEAL TEAM")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> teamService.createTeam(100, createTeamRequest(" seal team ")));

        verify(teamRepository, never()).save(any());
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void createTeam_shouldThrowResourceNotFound_whenEventDoesNotExist() {
        when(userRepository.findById(100)).thenReturn(Optional.of(user(100, "Leader")));
        when(eventRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamService.createTeam(100, createTeamRequest("Seal Team")));

        verify(trackRepository, never()).findById(anyInt());
        verify(teamRepository, never()).save(any());
    }

    @Test
    void createTeam_shouldThrowBadRequest_whenUserAlreadyHasTeamInEvent() {
        User user = user(100, "Leader");
        HackathonEvent event = event(1, "OPEN");

        when(userRepository.findById(100)).thenReturn(Optional.of(user));
        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(teamEventEntryRepository.existsByEventIdAndNormalizedName(1, "SEAL TEAM")).thenReturn(false);
        when(teamMemberRepository.existsByUser_UserIdAndTeam_Event_EventId(100, 1)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> teamService.createTeam(100, createTeamRequest("Seal Team")));

        verify(teamRepository, never()).save(any());
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void createTeam_shouldThrowResourceNotFound_whenUserDoesNotExist() {
        when(userRepository.findById(100)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamService.createTeam(100, createTeamRequest("Seal Team")));

        verify(eventRepository, never()).findById(anyInt());
        verify(teamRepository, never()).save(any());
    }

    @Test
    void updateTeam_shouldUpdateNameAndDescription_whenUserIsLeader() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, track(10, event), "Old Name", "PENDING");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");
        UpdateTeamRequest request = new UpdateTeamRequest();
        request.setName(" New Name ");
        request.setDescription(" Updated description ");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader));
        when(teamEventEntryRepository.existsByEventIdAndNormalizedName(1, "NEW NAME")).thenReturn(false);
        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of(leader));
        when(teamRepository.save(team)).thenReturn(team);

        MyTeamResponse response = teamService.updateTeam(100, 99, request);

        assertEquals("New Name", team.getName());
        assertEquals("Updated description", team.getDescription());
        assertEquals("New Name", response.getName());
        verify(teamRepository).save(team);
    }

    @Test
    void updateTeam_shouldThrowBadRequest_whenUserIsNotLeader() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "PENDING");
        TeamMember member = member(1, team, user(100, "Member"), "MEMBER");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(member));

        assertThrows(BadRequestException.class, () -> teamService.updateTeam(100, 99, new UpdateTeamRequest()));

        verify(teamRepository, never()).save(any());
    }

    @Test
    void updateTeam_shouldThrowResourceNotFound_whenTeamDoesNotExist() {
        when(teamRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamService.updateTeam(100, 99, new UpdateTeamRequest()));

        verify(teamMemberRepository, never()).findByTeam_TeamId(anyInt());
        verify(teamRepository, never()).save(any());
    }

    @Test
    void updateTeam_shouldThrowBadRequest_whenUserIsNotTeamMember() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "PENDING");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of());

        assertThrows(BadRequestException.class, () -> teamService.updateTeam(100, 99, new UpdateTeamRequest()));

        verify(teamRepository, never()).save(any());
    }

    @Test
    void updateTeam_shouldThrowBadRequest_whenTeamIsRejected() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "REJECTED");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader));

        assertThrows(BadRequestException.class, () -> teamService.updateTeam(100, 99, new UpdateTeamRequest()));

        verify(teamRepository, never()).save(any());
    }

    @Test
    void updateTeam_shouldThrowBadRequest_whenTeamIsDisqualified() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "DISQUALIFIED");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader));

        assertThrows(BadRequestException.class, () -> teamService.updateTeam(100, 99, new UpdateTeamRequest()));

        verify(teamRepository, never()).save(any());
    }

    @Test
    void updateTeam_shouldThrowBadRequest_whenNormalizedNewNameAlreadyExists() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, track(10, event), "Old Name", "PENDING");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");
        UpdateTeamRequest request = new UpdateTeamRequest();
        request.setName(" Existing Name ");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader));
        when(teamEventEntryRepository.existsByEventIdAndNormalizedName(1, "EXISTING NAME")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> teamService.updateTeam(100, 99, request));

        verify(teamRepository, never()).save(any());
    }

    @Test
    void updateTeam_shouldNotCheckDuplicate_whenNormalizedNameDoesNotChange() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, track(10, event), "Seal Team", "PENDING");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");
        UpdateTeamRequest request = new UpdateTeamRequest();
        request.setName(" seal team ");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader));
        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of(leader));
        when(teamRepository.save(team)).thenReturn(team);

        MyTeamResponse response = teamService.updateTeam(100, 99, request);

        assertEquals("seal team", response.getName());
        verify(teamEventEntryRepository, never()).existsByEventIdAndNormalizedName(anyInt(), any());
        verify(teamRepository).save(team);
    }

    @Test
    void updateTeam_shouldIgnoreBlankName() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, track(10, event), "Seal Team", "PENDING");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");
        UpdateTeamRequest request = new UpdateTeamRequest();
        request.setName("   ");
        request.setDescription("New description");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader));
        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of(leader));
        when(teamRepository.save(team)).thenReturn(team);

        teamService.updateTeam(100, 99, request);

        assertEquals("Seal Team", team.getName());
        assertEquals("New description", team.getDescription());
        verify(teamEventEntryRepository, never()).existsByEventIdAndNormalizedName(anyInt(), any());
    }

    @Test
    void updateTeam_shouldClearDescription_whenDescriptionIsBlank() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, track(10, event), "Seal Team", "PENDING");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");
        UpdateTeamRequest request = new UpdateTeamRequest();
        request.setDescription("   ");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader));
        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of(leader));
        when(teamRepository.save(team)).thenReturn(team);

        teamService.updateTeam(100, 99, request);

        assertNull(team.getDescription());
    }

    @Test
    void removeMember_shouldDeleteTargetMember_whenUserIsLeader() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "PENDING");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");
        TeamMember target = member(2, team, user(101, "Member"), "MEMBER");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99))
                .thenReturn(List.of(leader, target), List.of(leader, target), List.of(leader));
        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of(leader));

        MyTeamResponse response = teamService.removeMember(100, 99, 101);

        assertEquals(1, response.getMembers().size());
        verify(teamMemberRepository).delete(target);
        verify(participantHistorySnapshotService).snapshotDeparture(target, "REMOVED_BY_LEADER");
        verify(notificationService).createNotification(
                eq(101), eq("Removed from the team"), any(), eq("TEAM_MEMBER_REMOVED"));
    }

    @Test
    void removeMember_shouldThrowBadRequest_whenLeaderRemovesSelf() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "PENDING");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader));

        assertThrows(BadRequestException.class, () -> teamService.removeMember(100, 99, 100));

        verify(teamMemberRepository, never()).delete(any());
    }

    @Test
    void removeMember_shouldThrowResourceNotFound_whenTeamDoesNotExist() {
        when(teamRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamService.removeMember(100, 99, 101));

        verify(teamMemberRepository, never()).delete(any());
    }

    @Test
    void removeMember_shouldThrowBadRequest_whenLeaderIsNotTeamMember() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "PENDING");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of());

        assertThrows(BadRequestException.class, () -> teamService.removeMember(100, 99, 101));

        verify(teamMemberRepository, never()).delete(any());
    }

    @Test
    void removeMember_shouldThrowBadRequest_whenCallerIsNotLeader() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "PENDING");
        TeamMember member = member(1, team, user(100, "Member"), "MEMBER");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(member));

        assertThrows(BadRequestException.class, () -> teamService.removeMember(100, 99, 101));

        verify(teamMemberRepository, never()).delete(any());
    }

    @Test
    void removeMember_shouldThrowBadRequest_whenTeamIsRejected() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "REJECTED");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader));

        assertThrows(BadRequestException.class, () -> teamService.removeMember(100, 99, 101));

        verify(teamMemberRepository, never()).delete(any());
    }

    @Test
    void removeMember_shouldThrowBadRequest_whenTargetIsNotTeamMember() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "PENDING");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader));

        assertThrows(BadRequestException.class, () -> teamService.removeMember(100, 99, 101));

        verify(teamMemberRepository, never()).delete(any());
    }

    @Test
    void removeMember_shouldThrowBadRequest_whenTargetIsLeader() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "PENDING");
        TeamMember caller = member(1, team, user(100, "Leader"), "LEADER");
        TeamMember target = member(2, team, user(101, "Other Leader"), "LEADER");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(caller, target));

        assertThrows(BadRequestException.class, () -> teamService.removeMember(100, 99, 101));

        verify(teamMemberRepository, never()).delete(any());
    }

    @Test
    void transferLeadership_shouldSwapRoles_whenNewLeaderIsMember() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "PENDING");
        TeamMember oldLeader = member(1, team, user(100, "Leader"), "LEADER");
        TeamMember newLeader = member(2, team, user(101, "Member"), "MEMBER");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(oldLeader, newLeader));
        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of(oldLeader));

        MyTeamResponse response = teamService.transferLeadership(100, 99, 101);

        assertEquals("MEMBER", oldLeader.getMemberRole());
        assertEquals("LEADER", newLeader.getMemberRole());
        assertEquals("MEMBER", response.getMyRole());
        verify(teamMemberRepository).save(oldLeader);
        verify(teamMemberRepository).save(newLeader);
    }

    @Test
    void transferLeadership_shouldThrowBadRequest_whenLeaderTransfersToSelf() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "PENDING");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader));

        assertThrows(BadRequestException.class, () -> teamService.transferLeadership(100, 99, 100));

        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void transferLeadership_shouldThrowResourceNotFound_whenTeamDoesNotExist() {
        when(teamRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamService.transferLeadership(100, 99, 101));

        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void transferLeadership_shouldThrowBadRequest_whenLeaderIsNotTeamMember() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "PENDING");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of());

        assertThrows(BadRequestException.class, () -> teamService.transferLeadership(100, 99, 101));

        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void transferLeadership_shouldThrowBadRequest_whenCallerIsNotLeader() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "PENDING");
        TeamMember member = member(1, team, user(100, "Member"), "MEMBER");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(member));

        assertThrows(BadRequestException.class, () -> teamService.transferLeadership(100, 99, 101));

        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void transferLeadership_shouldThrowBadRequest_whenTeamIsDisqualified() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "DISQUALIFIED");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader));

        assertThrows(BadRequestException.class, () -> teamService.transferLeadership(100, 99, 101));

        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void transferLeadership_shouldThrowBadRequest_whenNewLeaderIsNotTeamMember() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "PENDING");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader));

        assertThrows(BadRequestException.class, () -> teamService.transferLeadership(100, 99, 101));

        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void leaveTeam_shouldDeleteMember_whenUserIsNotLeader() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "PENDING");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");
        TeamMember member = member(2, team, user(101, "Member"), "MEMBER");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader, member));

        teamService.leaveTeam(101, 99);

        verify(teamMemberRepository).delete(member);
        verify(teamRepository, never()).delete(any());
        verify(participantHistorySnapshotService).snapshotDeparture(member, "LEFT_TEAM");
    }

    @Test
    void leaveTeam_shouldDeleteOnlyTheSeasonEntry_whenOnlyLeaderLeaves() {
        // Team identity persists across seasons — only this season's entry is
        // removed, never the Team row itself.
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "PENDING");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader));

        teamService.leaveTeam(100, 99);

        verify(teamMemberRepository).delete(leader);
        verify(teamEventEntryRepository).delete(entryOf(99));
        verify(teamRepository, never()).delete(any());
        // Snapshotted BEFORE the entry itself is deleted — this is the one path
        // where the season's data would otherwise be unrecoverable, not just hidden.
        verify(participantHistorySnapshotService).snapshotDeparture(leader, "LEFT_TEAM");
    }

    @Test
    void leaveTeam_shouldThrowBadRequest_whenLeaderLeavesTeamWithOtherMembers() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "PENDING");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");
        TeamMember member = member(2, team, user(101, "Member"), "MEMBER");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader, member));

        assertThrows(BadRequestException.class, () -> teamService.leaveTeam(100, 99));

        verify(teamMemberRepository, never()).delete(any());
        verify(teamRepository, never()).delete(any());
    }

    @Test
    void leaveTeam_shouldThrowResourceNotFound_whenTeamDoesNotExist() {
        when(teamRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamService.leaveTeam(100, 99));

        verify(teamMemberRepository, never()).delete(any());
        verify(teamRepository, never()).delete(any());
    }

    @Test
    void leaveTeam_shouldThrowBadRequest_whenTeamIsRejected() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "REJECTED");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));

        assertThrows(BadRequestException.class, () -> teamService.leaveTeam(100, 99));

        verify(teamMemberRepository, never()).findByTeam_TeamId(anyInt());
        verify(teamMemberRepository, never()).delete(any());
        verify(teamRepository, never()).delete(any());
    }

    @Test
    void leaveTeam_shouldThrowBadRequest_whenUserIsNotTeamMember() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "PENDING");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of());

        assertThrows(BadRequestException.class, () -> teamService.leaveTeam(100, 99));

        verify(teamMemberRepository, never()).delete(any());
        verify(teamRepository, never()).delete(any());
    }

    private static CreateTeamRequest createTeamRequest(String name) {
        CreateTeamRequest request = new CreateTeamRequest();
        request.setEventId(1);
        request.setName(name);
        request.setDescription("Demo team");
        return request;
    }

    private static HackathonEvent event(Integer eventId, String status) {
        return HackathonEvent.builder()
                .eventId(eventId)
                .name("SEAL Hackathon " + eventId)
                .season("Spring")
                .year(2026)
                .description("Hackathon event")
                .startDate(LocalDateTime.now().plusDays(10))
                .endDate(LocalDateTime.now().plusDays(12))
                .status(status)
                .build();
    }

    private static Track track(Integer trackId, HackathonEvent event) {
        return Track.builder()
                .trackId(trackId)
                .event(event)
                .name("AI")
                .description("AI track")
                .build();
    }

    /**
     * Builds a thin Team plus its (only, in these tests) TeamEventEntry, and
     * lenient-stubs the "current entry" resolver every non-trivial service
     * method now goes through — lenient because plenty of tests build a team but
     * short-circuit before ever resolving its entry (e.g. ResourceNotFound cases).
     */
    private Team team(Integer teamId, HackathonEvent event, Track track, String name, String status) {
        Team team = Team.builder()
                .teamId(teamId)
                .name(name)
                .description("Team description")
                .createdAt(LocalDateTime.now())
                .build();
        TeamEventEntry entry = TeamEventEntry.builder()
                .id(teamId)
                .team(team)
                .event(event)
                .track(track)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
        entriesByTeamId.put(teamId, entry);
        lenient().when(teamEventEntryRepository.findTopByTeam_TeamIdOrderByIdDesc(teamId))
                .thenReturn(Optional.of(entry));
        return team;
    }

    /** The TeamEventEntry built alongside {@link #team} for the given teamId. */
    private TeamEventEntry entryOf(Integer teamId) {
        return entriesByTeamId.get(teamId);
    }

    private static TeamMember member(Integer id, Team team, User user, String role) {
        return TeamMember.builder()
                .id(id)
                .team(team)
                .user(user)
                .memberRole(role)
                .joinedAt(LocalDateTime.now())
                .build();
    }

    private static User user(Integer userId, String fullName) {
        return User.builder()
                .userId(userId)
                .email("user" + userId + "@example.com")
                .fullName(fullName)
                .userType("FPT_STUDENT")
                .studentId("SE" + userId)
                .university("FPT University")
                .isActive(true)
                .isApproved(true)
                .build();
    }
}
