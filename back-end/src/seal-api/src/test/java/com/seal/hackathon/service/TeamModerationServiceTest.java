package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.RejectTeamRequest;
import com.seal.hackathon.dto.response.TeamDetailResponse;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.RoundResult;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.entity.Track;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.RoundRepository;
import com.seal.hackathon.repository.RoundResultRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TeamRejoinRequestRepository;
import com.seal.hackathon.repository.TeamRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamModerationServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamEventEntryRepository teamEventEntryRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoundResultRepository roundResultRepository;

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private TeamRejoinRequestRepository teamRejoinRequestRepository;

    @Mock
    private ParticipantHistorySnapshotService participantHistorySnapshotService;

    private TeamModerationService teamService;

    private final Map<Integer, TeamEventEntry> entriesByTeamId = new HashMap<>();

    @BeforeEach
    void setUp() {
        TeamAccessGuard teamAccessGuard = new TeamAccessGuard(teamRepository, teamMemberRepository, teamEventEntryRepository);
        TeamResponseMapper teamResponseMapper = new TeamResponseMapper(
                teamMemberRepository, roundRepository, roundResultRepository, teamRejoinRequestRepository, teamAccessGuard);
        teamService = new TeamModerationService(
                teamRepository, teamEventEntryRepository, teamMemberRepository, userRepository, notificationService,
                auditLogService, participantHistorySnapshotService, teamAccessGuard, teamResponseMapper,
                roundResultRepository);
    }

    @Test
    void coordinatorRemoveMember_shouldSnapshotDeparture_beforeDeletingMember() {
        HackathonEvent event = event(1, "IN_PROGRESS");
        Team team = team(99, event, null, "Seal Team", "APPROVED");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");
        TeamMember target = member(2, team, user(101, "Member"), "MEMBER");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99))
                .thenReturn(List.of(leader, target), List.of(leader));

        teamService.coordinatorRemoveMember(500, 99, 101, "No-show");

        verify(participantHistorySnapshotService).snapshotDeparture(target, "REMOVED_BY_COORDINATOR");
        verify(teamMemberRepository).delete(target);
    }

    @Test
    void coordinatorRemoveMember_shouldDeleteRoundResults_whenLastMemberRemovedDisqualifiesTeam() {
        HackathonEvent event = event(1, "IN_PROGRESS");
        Team team = team(99, event, null, "Seal Team", "APPROVED");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");
        RoundResult stale = RoundResult.builder().resultId(500).team(team).build();

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader), List.of());
        when(roundResultRepository.findAllByTeam_TeamIdAndRound_Event_EventId(99, 1))
                .thenReturn(List.of(stale));

        TeamDetailResponse response = teamService.coordinatorRemoveMember(500, 99, 100, "No-show");

        assertEquals("DISQUALIFIED", response.getStatus());
        verify(roundResultRepository).deleteAll(List.of(stale));
    }

    @Test
    void approveTeam_shouldApproveTeamAndNotifyMembers() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, track(10, event), "Seal Team", "PENDING");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");
        TeamMember member = member(2, team, user(101, "Member"), "MEMBER");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader, member), List.of(leader, member));

        TeamDetailResponse response = teamService.approveTeam(99);

        assertEquals("APPROVED", response.getStatus());
        verify(notificationService).createNotification(eq(100), eq("Team approved"), any(), eq("TEAM_APPROVED"));
        verify(notificationService).createNotification(eq(101), eq("Team approved"), any(), eq("TEAM_APPROVED"));
    }

    @Test
    void approveTeam_shouldThrowBadRequest_whenTeamAlreadyApproved() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "APPROVED");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));

        assertThrows(BadRequestException.class, () -> teamService.approveTeam(99));

        verify(teamRepository, never()).save(any());
        verify(notificationService, never()).createNotification(anyInt(), any(), any(), any());
    }

    @Test
    void approveTeam_shouldThrowResourceNotFound_whenTeamDoesNotExist() {
        when(teamRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamService.approveTeam(99));

        verify(teamRepository, never()).save(any());
        verify(notificationService, never()).createNotification(anyInt(), any(), any(), any());
    }

    @Test
    void approveTeam_shouldThrowBadRequest_whenTeamIsRejected() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "REJECTED");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));

        assertThrows(BadRequestException.class, () -> teamService.approveTeam(99));

        verify(teamRepository, never()).save(any());
    }

    @Test
    void approveTeam_shouldThrowBadRequest_whenTeamIsDisqualified() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "DISQUALIFIED");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));

        assertThrows(BadRequestException.class, () -> teamService.approveTeam(99));

        verify(teamRepository, never()).save(any());
        verify(notificationService, never()).createNotification(anyInt(), any(), any(), any());
    }

    @Test
    void rejectTeam_shouldRejectTeamStoreReasonAndNotifyMembers() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, track(10, event), "Seal Team", "PENDING");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");
        RejectTeamRequest request = new RejectTeamRequest();
        request.setReason(" Invalid information ");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader), List.of(leader));

        TeamDetailResponse response = teamService.rejectTeam(99, request);

        assertEquals("REJECTED", response.getStatus());
        assertEquals("Invalid information", response.getDisqualifiedReason());
        verify(notificationService).createNotification(eq(100), eq("Team rejected"), any(), eq("TEAM_REJECTED"));
    }

    @Test
    void rejectTeam_shouldRejectTeamWithNullReason_whenRequestIsNull() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, track(10, event), "Seal Team", "PENDING");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of());

        TeamDetailResponse response = teamService.rejectTeam(99, null);

        assertEquals("REJECTED", response.getStatus());
        assertNull(response.getDisqualifiedReason());
    }

    @Test
    void rejectTeam_shouldThrowResourceNotFound_whenTeamDoesNotExist() {
        when(teamRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamService.rejectTeam(99, new RejectTeamRequest()));

        verify(teamRepository, never()).save(any());
        verify(notificationService, never()).createNotification(anyInt(), any(), any(), any());
    }

    @Test
    void rejectTeam_shouldThrowBadRequest_whenTeamIsApproved() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "APPROVED");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));

        assertThrows(BadRequestException.class, () -> teamService.rejectTeam(99, new RejectTeamRequest()));

        verify(teamRepository, never()).save(any());
    }

    @Test
    void rejectTeam_shouldThrowBadRequest_whenTeamIsRejected() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "REJECTED");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));

        assertThrows(BadRequestException.class, () -> teamService.rejectTeam(99, new RejectTeamRequest()));

        verify(teamRepository, never()).save(any());
    }

    @Test
    void rejectTeam_shouldThrowBadRequest_whenTeamIsDisqualified() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "DISQUALIFIED");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));

        assertThrows(BadRequestException.class, () -> teamService.rejectTeam(99, new RejectTeamRequest()));

        verify(teamRepository, never()).save(any());
    }

    @Test
    void rejectTeam_shouldSetReasonNull_whenReasonIsBlank() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, track(10, event), "Seal Team", "PENDING");
        RejectTeamRequest request = new RejectTeamRequest();
        request.setReason("   ");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of());

        TeamDetailResponse response = teamService.rejectTeam(99, request);

        assertNull(response.getDisqualifiedReason());
    }

    @Test
    void disqualifyTeam_shouldDisqualifyTeamStoreReasonAndTimestamp() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, track(10, event), "Seal Team", "APPROVED");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");
        RejectTeamRequest request = new RejectTeamRequest();
        request.setReason(" Rule violation ");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamRepository.save(team)).thenReturn(team);
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader), List.of(leader));

        TeamDetailResponse response = teamService.disqualifyTeam(99, request);

        assertEquals("DISQUALIFIED", response.getStatus());
        assertEquals("Rule violation", response.getDisqualifiedReason());
        assertNotNull(response.getDisqualifiedAt());
        verify(notificationService).createNotification(eq(100), eq("Team disqualified"), any(), eq("TEAM_DISQUALIFIED"));
    }

    @Test
    void disqualifyTeam_shouldDisqualifyTeamWithNullReason_whenRequestIsNull() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, track(10, event), "Seal Team", "APPROVED");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamRepository.save(team)).thenReturn(team);
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of());

        TeamDetailResponse response = teamService.disqualifyTeam(99, null);

        assertEquals("DISQUALIFIED", response.getStatus());
        assertNull(response.getDisqualifiedReason());
        assertNotNull(response.getDisqualifiedAt());
    }

    @Test
    void disqualifyTeam_shouldDeleteTeamsRoundResultsForThisEvent() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, track(10, event), "Seal Team", "APPROVED");
        RoundResult stale = RoundResult.builder().resultId(500).team(team).build();

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamRepository.save(team)).thenReturn(team);
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of());
        when(roundResultRepository.findAllByTeam_TeamIdAndRound_Event_EventId(99, 1))
                .thenReturn(List.of(stale));

        teamService.disqualifyTeam(99, new RejectTeamRequest());

        verify(roundResultRepository).deleteAll(List.of(stale));
    }

    @Test
    void disqualifyTeam_shouldThrowResourceNotFound_whenTeamDoesNotExist() {
        when(teamRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamService.disqualifyTeam(99, new RejectTeamRequest()));

        verify(teamRepository, never()).save(any());
        verify(notificationService, never()).createNotification(anyInt(), any(), any(), any());
    }

    @Test
    void disqualifyTeam_shouldThrowBadRequest_whenTeamIsPending() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "PENDING");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));

        assertThrows(BadRequestException.class, () -> teamService.disqualifyTeam(99, new RejectTeamRequest()));

        verify(teamRepository, never()).save(any());
    }

    @Test
    void disqualifyTeam_shouldThrowBadRequest_whenTeamIsRejected() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "REJECTED");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));

        assertThrows(BadRequestException.class, () -> teamService.disqualifyTeam(99, new RejectTeamRequest()));

        verify(teamRepository, never()).save(any());
    }

    @Test
    void disqualifyTeam_shouldThrowBadRequest_whenTeamIsAlreadyDisqualified() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "DISQUALIFIED");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));

        assertThrows(BadRequestException.class, () -> teamService.disqualifyTeam(99, new RejectTeamRequest()));

        verify(teamRepository, never()).save(any());
    }

    @Test
    void disqualifyTeam_shouldSetReasonNull_whenReasonIsBlank() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, track(10, event), "Seal Team", "APPROVED");
        RejectTeamRequest request = new RejectTeamRequest();
        request.setReason("   ");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamRepository.save(team)).thenReturn(team);
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of());

        TeamDetailResponse response = teamService.disqualifyTeam(99, request);

        assertNull(response.getDisqualifiedReason());
        assertEquals("DISQUALIFIED", response.getStatus());
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
