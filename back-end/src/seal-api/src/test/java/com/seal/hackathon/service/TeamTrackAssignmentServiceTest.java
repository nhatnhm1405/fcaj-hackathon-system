package com.seal.hackathon.service;

import com.seal.hackathon.dto.response.TeamDetailResponse;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.entity.Track;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.HackathonEventRepository;
import com.seal.hackathon.repository.RoundRepository;
import com.seal.hackathon.repository.RoundResultRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamTrackAssignmentServiceTest {

    @Mock
    private HackathonEventRepository eventRepository;

    @Mock
    private TrackRepository trackRepository;

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
    private AuditLogService auditLogService;

    @Mock
    private TeamRejoinRequestRepository teamRejoinRequestRepository;

    @Mock
    private ParticipantHistorySnapshotService participantHistorySnapshotService;

    private TeamTrackAssignmentService teamTrackAssignmentService;

    private final Map<Integer, TeamEventEntry> entriesByTeamId = new HashMap<>();

    @BeforeEach
    void setUp() {
        TeamAccessGuard teamAccessGuard = new TeamAccessGuard(teamRepository, teamMemberRepository, teamEventEntryRepository);
        TeamResponseMapper teamResponseMapper = new TeamResponseMapper(
                teamMemberRepository, roundRepository, roundResultRepository, teamRejoinRequestRepository, teamAccessGuard);
        TeamQueryService teamQueryService = new TeamQueryService(
                teamRepository, teamEventEntryRepository, teamMemberRepository, eventRepository, trackRepository,
                userRepository, participantHistorySnapshotService, teamAccessGuard, teamResponseMapper);
        teamTrackAssignmentService = new TeamTrackAssignmentService(
                eventRepository, trackRepository, teamRepository, teamEventEntryRepository, auditLogService,
                teamAccessGuard, teamResponseMapper, teamQueryService);
    }

    @Test
    void assignTeamToTrack_shouldPlaceTeam_whenSetupAndApproved() {
        HackathonEvent event = event(1, "SETUP");
        Team team = team(100, event, null, "Alpha", "APPROVED");
        Track track = track(10, event);

        when(teamRepository.findById(100)).thenReturn(Optional.of(team));
        when(trackRepository.findById(10)).thenReturn(Optional.of(track));
        when(teamMemberRepository.findByTeam_TeamId(100)).thenReturn(List.of());

        TeamDetailResponse response = teamTrackAssignmentService.assignTeamToTrack(5, 100, 10);

        assertEquals(track, entryOf(100).getTrack());
        assertEquals(10, response.getTrackId());
        verify(teamEventEntryRepository).save(entryOf(100));
    }

    @Test
    void assignTeamToTrack_shouldUnassign_whenTrackIdIsNull() {
        HackathonEvent event = event(1, "SETUP");
        Track current = track(10, event);
        Team team = team(100, event, current, "Alpha", "APPROVED");

        when(teamRepository.findById(100)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(100)).thenReturn(List.of());

        TeamDetailResponse response = teamTrackAssignmentService.assignTeamToTrack(5, 100, null);

        assertNull(entryOf(100).getTrack());
        assertNull(response.getTrackId());
        verify(trackRepository, never()).findById(anyInt());
        verify(teamEventEntryRepository).save(entryOf(100));
    }

    @Test
    void assignTeamToTrack_shouldAllowExceedingCapacity() {
        // Coordinator placement deliberately ignores capacity (soft cap on the UI),
        // unlike participant selectTrack which hard-caps.
        HackathonEvent event = event(1, "SETUP");
        Team team = team(100, event, null, "Alpha", "APPROVED");
        Track full = track(10, event);
        full.setCapacity(1); // already "full" — must still accept the placement

        when(teamRepository.findById(100)).thenReturn(Optional.of(team));
        when(trackRepository.findById(10)).thenReturn(Optional.of(full));
        when(teamMemberRepository.findByTeam_TeamId(100)).thenReturn(List.of());

        teamTrackAssignmentService.assignTeamToTrack(5, 100, 10);

        assertEquals(full, entryOf(100).getTrack());
        verify(teamEventEntryRepository).save(entryOf(100));
    }

    @Test
    void assignTeamToTrack_shouldThrow_whenEventNotInSetup() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(100, event, null, "Alpha", "APPROVED");

        when(teamRepository.findById(100)).thenReturn(Optional.of(team));

        assertThrows(BadRequestException.class, () -> teamTrackAssignmentService.assignTeamToTrack(5, 100, 10));

        verify(teamRepository, never()).save(any());
    }

    @Test
    void assignTeamToTrack_shouldThrow_whenTeamNotApproved() {
        HackathonEvent event = event(1, "SETUP");
        Team team = team(100, event, null, "Alpha", "PENDING");

        when(teamRepository.findById(100)).thenReturn(Optional.of(team));

        assertThrows(BadRequestException.class, () -> teamTrackAssignmentService.assignTeamToTrack(5, 100, 10));

        verify(teamRepository, never()).save(any());
    }

    @Test
    void assignTeamToTrack_shouldThrow_whenTrackBelongsToAnotherEvent() {
        HackathonEvent event = event(1, "SETUP");
        HackathonEvent otherEvent = event(2, "SETUP");
        Team team = team(100, event, null, "Alpha", "APPROVED");
        Track foreignTrack = track(10, otherEvent);

        when(teamRepository.findById(100)).thenReturn(Optional.of(team));
        when(trackRepository.findById(10)).thenReturn(Optional.of(foreignTrack));

        assertThrows(BadRequestException.class, () -> teamTrackAssignmentService.assignTeamToTrack(5, 100, 10));

        verify(teamRepository, never()).save(any());
    }

    @Test
    void assignTeamToTrack_shouldThrowResourceNotFound_whenTeamMissing() {
        when(teamRepository.findById(100)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamTrackAssignmentService.assignTeamToTrack(5, 100, 10));

        verify(teamRepository, never()).save(any());
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
