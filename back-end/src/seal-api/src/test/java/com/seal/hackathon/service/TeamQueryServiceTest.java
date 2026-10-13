package com.seal.hackathon.service;

import com.seal.hackathon.dto.response.ActiveEventResponse;
import com.seal.hackathon.dto.response.MyTeamResponse;
import com.seal.hackathon.dto.response.TeamDetailResponse;
import com.seal.hackathon.dto.response.TeamHistoryResponse;
import com.seal.hackathon.dto.response.UserResponse;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.Round;
import com.seal.hackathon.entity.RoundResult;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.entity.Track;
import com.seal.hackathon.entity.User;
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

import java.math.BigDecimal;
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
class TeamQueryServiceTest {

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
    private TeamRejoinRequestRepository teamRejoinRequestRepository;

    @Mock
    private ParticipantHistorySnapshotService participantHistorySnapshotService;

    private TeamQueryService teamQueryService;

    private final Map<Integer, TeamEventEntry> entriesByTeamId = new HashMap<>();

    @BeforeEach
    void setUp() {
        TeamAccessGuard teamAccessGuard = new TeamAccessGuard(teamRepository, teamMemberRepository, teamEventEntryRepository);
        TeamResponseMapper teamResponseMapper = new TeamResponseMapper(
                teamMemberRepository, roundRepository, roundResultRepository, teamRejoinRequestRepository, teamAccessGuard);
        teamQueryService = new TeamQueryService(
                teamRepository, teamEventEntryRepository, teamMemberRepository, eventRepository, trackRepository,
                userRepository, participantHistorySnapshotService, teamAccessGuard, teamResponseMapper);
    }

    @Test
    void getMyTeam_shouldReturnCurrentTeamWithMembers() {
        Team team = team(99, event(1, "OPEN"), track(10, event(1, "OPEN")), "Seal Team", "APPROVED");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");
        TeamMember member = member(2, team, user(101, "Member"), "MEMBER");

        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of(leader));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader, member));

        MyTeamResponse response = teamQueryService.getMyTeam(100);

        assertEquals(99, response.getTeamId());
        assertEquals("Seal Team", response.getName());
        assertEquals("LEADER", response.getMyRole());
        assertEquals(2, response.getMembers().size());
    }

    @Test
    void getMyTeam_shouldReturnRound_whenTeamCanParticipateInActiveRound() {
        HackathonEvent event = event(1, "IN_PROGRESS");
        Team team = team(99, event, track(10, event), "Seal Team", "APPROVED");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");
        Round round1 = round(11, event, 1, "Round 1", "FINALIZED");
        round1.setTopNAdvance(3);
        Round round2 = round(12, event, 2, "Round 2", "ACTIVE");

        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of(leader));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader));
        when(roundRepository.findAllByEvent_EventIdOrderByOrderNumber(1)).thenReturn(List.of(round1, round2));
        when(roundResultRepository.findByTeam_TeamIdAndRound_RoundId(99, 11))
                .thenReturn(Optional.of(roundResult(team, round1, 2)));

        MyTeamResponse response = teamQueryService.getMyTeam(100);

        assertNotNull(response.getRound());
        assertEquals(12, response.getRound().getRoundId());
        assertEquals("Round 2", response.getRound().getName());
    }

    @Test
    void getMyTeam_shouldReturnDisqualifiedRound_whenTeamWasDisqualified() {
        HackathonEvent event = event(1, "IN_PROGRESS");
        Team team = team(99, event, track(10, event), "Seal Team", "DISQUALIFIED");
        LocalDateTime disqualifiedAt = LocalDateTime.of(2026, 7, 4, 10, 30);
        entryOf(99).setDisqualifiedAt(disqualifiedAt);
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");
        Round round1 = round(11, event, 1, "Round 1", "FINALIZED");
        round1.setStartTime(LocalDateTime.of(2026, 7, 4, 8, 0));
        round1.setEndTime(LocalDateTime.of(2026, 7, 4, 9, 0));
        Round round2 = round(12, event, 2, "Round 2", "ACTIVE");
        round2.setStartTime(LocalDateTime.of(2026, 7, 4, 10, 0));
        round2.setEndTime(LocalDateTime.of(2026, 7, 4, 12, 0));

        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of(leader));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader));
        when(roundRepository.findAllByEvent_EventIdOrderByOrderNumber(1)).thenReturn(List.of(round1, round2));

        MyTeamResponse response = teamQueryService.getMyTeam(100);

        assertNotNull(response.getRound());
        assertEquals(12, response.getRound().getRoundId());
        assertEquals("Round 2", response.getRound().getName());
    }

    @Test
    void getMyTeam_shouldThrowResourceNotFound_whenUserHasNoActiveTeam() {
        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> teamQueryService.getMyTeam(100));
    }

    @Test
    void getMyTeam_shouldReturnDormantTeam_whenNoLiveEntryButHistoricalMembershipExists() {
        // Rejoin fallback: the team's only entry belongs to a COMPLETED event —
        // getMyTeam must still surface the team read-only instead of 404ing.
        HackathonEvent completedEvent = event(1, "COMPLETED");
        Team team = team(99, completedEvent, null, "Seal Team", "APPROVED");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");

        when(teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(eq(100), anyList()))
                .thenReturn(List.of());
        when(teamMemberRepository.findByUser_UserIdOrderByIdDesc(100)).thenReturn(List.of(leader));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader));

        MyTeamResponse response = teamQueryService.getMyTeam(100);

        assertEquals(99, response.getTeamId());
        assertEquals("COMPLETED", response.getEventStatus());
        assertEquals("LEADER", response.getMyRole());
    }

    @Test
    void getMyResultHistory_shouldReturnOneRowPerEntry_scopedToItsOwnEvent_whenTeamHasRejoined() {
        HackathonEvent event1 = event(1, "COMPLETED");
        HackathonEvent event2 = event(2, "OPEN");
        Team team = Team.builder().teamId(99).name("Seal Team").description("d").createdAt(LocalDateTime.now()).build();
        TeamEventEntry entry1 = TeamEventEntry.builder().id(201).team(team).event(event1).status("APPROVED")
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0)).build();
        TeamEventEntry entry2 = TeamEventEntry.builder().id(202).team(team).event(event2).status("APPROVED")
                .createdAt(LocalDateTime.of(2026, 7, 1, 0, 0)).build();
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");
        leader.setJoinedAt(LocalDateTime.of(2025, 12, 1, 0, 0)); // present for both seasons

        // buildHistoryView's own round/submission/prize event-scoping is
        // ParticipantHistorySnapshotService's responsibility now (tested in
        // ParticipantHistorySnapshotServiceTest) — here we only verify
        // computeLiveHistory picks the right (membership, entry) pairs and
        // getMyResultHistory orders/merges them correctly.
        TeamHistoryResponse view1 = TeamHistoryResponse.builder().eventId(1).eventStatus("COMPLETED").teamId(99).build();
        TeamHistoryResponse view2 = TeamHistoryResponse.builder().eventId(2).eventStatus("OPEN").teamId(99).build();

        when(teamMemberRepository.findByUser_UserIdOrderByIdDesc(100)).thenReturn(List.of(leader));
        when(participantHistorySnapshotService.resolveEntriesForMembership(leader)).thenReturn(List.of(entry2, entry1));
        when(participantHistorySnapshotService.buildHistoryView(leader, entry1)).thenReturn(view1);
        when(participantHistorySnapshotService.buildHistoryView(leader, entry2)).thenReturn(view2);
        when(participantHistorySnapshotService.getSnapshotsForUser(100)).thenReturn(Map.of());

        List<TeamHistoryResponse> history = teamQueryService.getMyResultHistory(100);

        assertEquals(2, history.size());
        // Newest entry (event 2) first.
        assertEquals(2, history.get(0).getEventId());
        assertEquals(1, history.get(1).getEventId());
    }

    @Test
    void getMyResultHistory_shouldExcludeEntry_whenMemberJoinedAfterThatEntryWasCreated() {
        HackathonEvent event1 = event(1, "COMPLETED");
        HackathonEvent event2 = event(2, "OPEN");
        Team team = Team.builder().teamId(99).name("Seal Team").description("d").createdAt(LocalDateTime.now()).build();
        TeamEventEntry entry1 = TeamEventEntry.builder().id(201).team(team).event(event1).status("APPROVED")
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0)).build();
        TeamEventEntry entry2 = TeamEventEntry.builder().id(202).team(team).event(event2).status("APPROVED")
                .createdAt(LocalDateTime.of(2026, 7, 1, 0, 0)).build();
        // Joined after the rejoin (entry2) was created — never part of the entry1 season.
        TeamMember freshMember = member(2, team, user(101, "Newcomer"), "MEMBER");
        freshMember.setJoinedAt(LocalDateTime.of(2026, 7, 2, 0, 0));

        TeamHistoryResponse view2 = TeamHistoryResponse.builder().eventId(2).eventStatus("OPEN").teamId(99).build();

        when(teamMemberRepository.findByUser_UserIdOrderByIdDesc(101)).thenReturn(List.of(freshMember));
        when(participantHistorySnapshotService.resolveEntriesForMembership(freshMember)).thenReturn(List.of(entry2));
        when(participantHistorySnapshotService.buildHistoryView(freshMember, entry2)).thenReturn(view2);
        when(participantHistorySnapshotService.getSnapshotsForUser(101)).thenReturn(Map.of());

        List<TeamHistoryResponse> history = teamQueryService.getMyResultHistory(101);

        assertEquals(1, history.size());
        assertEquals(2, history.get(0).getEventId());
        verify(participantHistorySnapshotService, never()).buildHistoryView(eq(freshMember), eq(entry1));
    }

    @Test
    void getMyResultHistory_shouldReturnSingleRow_whenTeamHasOnlyOneEntry() {
        HackathonEvent event = event(1, "IN_PROGRESS");
        Team team = team(99, event, null, "Seal Team", "APPROVED");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");
        TeamEventEntry entry = entryOf(99);

        TeamHistoryResponse view = TeamHistoryResponse.builder().eventId(1).eventStatus("IN_PROGRESS").teamId(99).build();

        when(teamMemberRepository.findByUser_UserIdOrderByIdDesc(100)).thenReturn(List.of(leader));
        when(participantHistorySnapshotService.resolveEntriesForMembership(leader)).thenReturn(List.of(entry));
        when(participantHistorySnapshotService.buildHistoryView(leader, entry)).thenReturn(view);
        when(participantHistorySnapshotService.getSnapshotsForUser(100)).thenReturn(Map.of());

        List<TeamHistoryResponse> history = teamQueryService.getMyResultHistory(100);

        assertEquals(1, history.size());
        assertEquals(1, history.get(0).getEventId());
        assertEquals(99, history.get(0).getTeamId());
    }

    @Test
    void getMyResultHistory_shouldPreferLiveOverSnapshot_whenEventIsNotCompleted() {
        // Event was reopened after a previous completion — a stale snapshot
        // exists, but the live, up-to-date view should win while it's running.
        HackathonEvent event = event(2, "IN_PROGRESS");
        Team team = team(99, event, null, "Seal Team", "APPROVED");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");
        TeamEventEntry entry = entryOf(99);

        TeamHistoryResponse liveView = TeamHistoryResponse.builder().eventId(2).eventStatus("IN_PROGRESS").teamName("LIVE").build();
        TeamHistoryResponse staleSnapshot = TeamHistoryResponse.builder().eventId(2).eventStatus("COMPLETED").teamName("STALE").build();

        when(teamMemberRepository.findByUser_UserIdOrderByIdDesc(100)).thenReturn(List.of(leader));
        when(participantHistorySnapshotService.resolveEntriesForMembership(leader)).thenReturn(List.of(entry));
        when(participantHistorySnapshotService.buildHistoryView(leader, entry)).thenReturn(liveView);
        when(participantHistorySnapshotService.getSnapshotsForUser(100)).thenReturn(Map.of(2, staleSnapshot));

        List<TeamHistoryResponse> history = teamQueryService.getMyResultHistory(100);

        assertEquals(1, history.size());
        assertEquals("LIVE", history.get(0).getTeamName());
    }

    @Test
    void getMyResultHistory_shouldPreferSnapshot_whenLiveEventIsCompleted() {
        HackathonEvent event = event(3, "COMPLETED");
        Team team = team(99, event, null, "Seal Team", "APPROVED");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");
        TeamEventEntry entry = entryOf(99);

        TeamHistoryResponse liveView = TeamHistoryResponse.builder().eventId(3).eventStatus("COMPLETED").teamName("LIVE_STALE_ROSTER").build();
        TeamHistoryResponse snapshot = TeamHistoryResponse.builder().eventId(3).eventStatus("COMPLETED").teamName("FROZEN").build();

        when(teamMemberRepository.findByUser_UserIdOrderByIdDesc(100)).thenReturn(List.of(leader));
        when(participantHistorySnapshotService.resolveEntriesForMembership(leader)).thenReturn(List.of(entry));
        when(participantHistorySnapshotService.buildHistoryView(leader, entry)).thenReturn(liveView);
        when(participantHistorySnapshotService.getSnapshotsForUser(100)).thenReturn(Map.of(3, snapshot));

        List<TeamHistoryResponse> history = teamQueryService.getMyResultHistory(100);

        assertEquals(1, history.size());
        assertEquals("FROZEN", history.get(0).getTeamName());
    }

    @Test
    void getMyResultHistory_shouldUseSnapshot_whenDepartedMemberHasNoLiveEntry() {
        // Departed member — no TeamMember rows left at all, only a frozen snapshot.
        TeamHistoryResponse snapshot = TeamHistoryResponse.builder().eventId(5).eventStatus("COMPLETED").teamName("DEPARTED").build();

        when(teamMemberRepository.findByUser_UserIdOrderByIdDesc(100)).thenReturn(List.of());
        when(participantHistorySnapshotService.getSnapshotsForUser(100)).thenReturn(Map.of(5, snapshot));

        List<TeamHistoryResponse> history = teamQueryService.getMyResultHistory(100);

        assertEquals(1, history.size());
        assertEquals("DEPARTED", history.get(0).getTeamName());
    }

    @Test
    void searchInvitableUsers_shouldReturnEmptyList_whenQueryIsTooShort() {
        List<UserResponse> response = teamQueryService.searchInvitableUsers(" a ");

        assertTrue(response.isEmpty());
        verify(userRepository, never()).searchInvitableStudents(any());
    }

    @Test
    void searchInvitableUsers_shouldReturnEmptyList_whenQueryIsNull() {
        List<UserResponse> response = teamQueryService.searchInvitableUsers(null);

        assertTrue(response.isEmpty());
        verify(userRepository, never()).searchInvitableStudents(any());
    }

    @Test
    void searchInvitableUsers_shouldReturnAtMostTenUsers() {
        List<User> users = java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(i -> user(i, "Student " + i))
                .toList();
        when(userRepository.searchInvitableStudents("student")).thenReturn(users);

        List<UserResponse> response = teamQueryService.searchInvitableUsers(" Student ");

        assertEquals(10, response.size());
        assertEquals("Student 1", response.get(0).getFullName());
    }

    @Test
    void searchInvitableUsers_shouldReturnEmptyList_whenRepositoryFindsNoUsers() {
        when(userRepository.searchInvitableStudents("student")).thenReturn(List.of());

        List<UserResponse> response = teamQueryService.searchInvitableUsers(" Student ");

        assertTrue(response.isEmpty());
        verify(userRepository).searchInvitableStudents("student");
    }

    @Test
    void getTeamsByEvent_shouldReturnAllTeamsForEvent() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, track(10, event), "Seal Team", "APPROVED");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(teamEventEntryRepository.findAllByEvent_EventId(1)).thenReturn(List.of(entryOf(99)));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader));

        List<TeamDetailResponse> response = teamQueryService.getTeamsByEvent(1);

        assertEquals(1, response.size());
        assertEquals("Seal Team", response.get(0).getName());
        assertEquals(1, response.get(0).getMembers().size());
    }

    @Test
    void getTeamsByEvent_shouldThrowResourceNotFound_whenEventDoesNotExist() {
        when(eventRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamQueryService.getTeamsByEvent(1));

        verify(teamEventEntryRepository, never()).findAllByEvent_EventId(anyInt());
    }

    @Test
    void getTeamsByEvent_shouldReturnEmptyList_whenEventHasNoTeams() {
        HackathonEvent event = event(1, "OPEN");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(teamEventEntryRepository.findAllByEvent_EventId(1)).thenReturn(List.of());

        List<TeamDetailResponse> response = teamQueryService.getTeamsByEvent(1);

        assertTrue(response.isEmpty());
    }

    @Test
    void getTeamById_shouldReturnTeamDetail() {
        HackathonEvent event = event(1, "OPEN");
        Team team = team(99, event, track(10, event), "Seal Team", "APPROVED");
        TeamMember leader = member(1, team, user(100, "Leader"), "LEADER");

        when(teamRepository.findById(99)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_TeamId(99)).thenReturn(List.of(leader));

        TeamDetailResponse response = teamQueryService.getTeamById(99);

        assertEquals(99, response.getTeamId());
        assertEquals("Seal Team", response.getName());
        assertEquals("APPROVED", response.getStatus());
    }

    @Test
    void getTeamById_shouldThrowResourceNotFound_whenTeamDoesNotExist() {
        when(teamRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamQueryService.getTeamById(99));

        verify(teamMemberRepository, never()).findByTeam_TeamId(anyInt());
    }

    @Test
    void getActiveEventsWithTracks_shouldReturnAllOpenEventsRegardlessRegistrationWindow() {
        LocalDateTime now = LocalDateTime.now();
        HackathonEvent activeEvent = event(1, "OPEN");
        activeEvent.setRegistrationStart(now.minusDays(1));
        activeEvent.setRegistrationEnd(now.plusDays(1));
        HackathonEvent futureEvent = event(2, "OPEN");
        futureEvent.setRegistrationStart(now.plusDays(1));

        when(eventRepository.findAllByStatus("OPEN")).thenReturn(List.of(activeEvent, futureEvent));
        when(trackRepository.findAllByEvent_EventId(1)).thenReturn(List.of(track(10, activeEvent)));
        when(trackRepository.findAllByEvent_EventId(2)).thenReturn(List.of(track(20, futureEvent)));

        List<ActiveEventResponse> response = teamQueryService.getActiveEventsWithTracks();

        assertEquals(2, response.size());
        assertEquals(1, response.get(0).getEventId());
        assertFalse(response.get(0).getTracks().isEmpty());
        assertEquals(2, response.get(1).getEventId());
        assertFalse(response.get(1).getTracks().isEmpty());
    }

    @Test
    void getActiveEventsWithTracks_shouldReturnOpenEvent_whenRegistrationWindowIsNull() {
        HackathonEvent event = event(1, "OPEN");
        event.setRegistrationStart(null);
        event.setRegistrationEnd(null);

        when(eventRepository.findAllByStatus("OPEN")).thenReturn(List.of(event));
        when(trackRepository.findAllByEvent_EventId(1)).thenReturn(List.of(track(10, event)));

        List<ActiveEventResponse> response = teamQueryService.getActiveEventsWithTracks();

        assertEquals(1, response.size());
        assertEquals(1, response.get(0).getEventId());
        assertEquals(1, response.get(0).getTracks().size());
    }

    @Test
    void getActiveEventsWithTracks_shouldReturnEmptyList_whenThereAreNoOpenEvents() {
        when(eventRepository.findAllByStatus("OPEN")).thenReturn(List.of());

        List<ActiveEventResponse> response = teamQueryService.getActiveEventsWithTracks();

        assertTrue(response.isEmpty());
        verify(trackRepository, never()).findAllByEvent_EventId(anyInt());
    }

    @Test
    void getActiveEventsWithTracks_shouldReturnExpiredOpenEventBecauseStatusIsSourceOfTruth() {
        LocalDateTime now = LocalDateTime.now();
        HackathonEvent expiredEvent = event(1, "OPEN");
        expiredEvent.setRegistrationEnd(now.minusDays(1));

        when(eventRepository.findAllByStatus("OPEN")).thenReturn(List.of(expiredEvent));
        when(trackRepository.findAllByEvent_EventId(1)).thenReturn(List.of(track(10, expiredEvent)));

        List<ActiveEventResponse> response = teamQueryService.getActiveEventsWithTracks();

        assertEquals(1, response.size());
        assertEquals(1, response.get(0).getEventId());
        assertFalse(response.get(0).getTracks().isEmpty());
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

    private static Round round(Integer roundId, HackathonEvent event, Integer orderNumber, String name, String status) {
        LocalDateTime start = LocalDateTime.of(2026, 7, 4, 8, 0).plusDays(orderNumber - 1L);
        return Round.builder()
                .roundId(roundId)
                .event(event)
                .name(name)
                .orderNumber(orderNumber)
                .startTime(start)
                .endTime(start.plusHours(2))
                .submissionDeadline(start.plusHours(3))
                .isFinal(false)
                .status(status)
                .build();
    }

    private static RoundResult roundResult(Team team, Round round, Integer rankPosition) {
        return RoundResult.builder()
                .resultId(700 + round.getRoundId())
                .team(team)
                .round(round)
                .rankPosition(rankPosition)
                .totalScore(BigDecimal.valueOf(90))
                .isPublished(true)
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
