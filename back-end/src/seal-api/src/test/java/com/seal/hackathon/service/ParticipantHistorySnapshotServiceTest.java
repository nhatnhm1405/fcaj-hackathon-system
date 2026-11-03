package com.seal.hackathon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.seal.hackathon.dto.response.TeamHistoryResponse;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.ParticipantEventHistory;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.repository.HackathonEventRepository;
import com.seal.hackathon.repository.ParticipantEventHistoryRepository;
import com.seal.hackathon.repository.PrizeRepository;
import com.seal.hackathon.repository.RoundResultRepository;
import com.seal.hackathon.repository.SubmissionRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParticipantHistorySnapshotServiceTest {

    @Mock
    private ParticipantEventHistoryRepository participantEventHistoryRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private TeamEventEntryRepository teamEventEntryRepository;

    @Mock
    private RoundResultRepository roundResultRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private PrizeRepository prizeRepository;

    @Mock
    private HackathonEventRepository hackathonEventRepository;

    // The service builds its own ObjectMapper internally (no Spring bean to
    // inject here — see ParticipantHistorySnapshotService's field comment).
    // A second, independent instance for round-tripping test fixtures.
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private ParticipantHistorySnapshotService service;

    @BeforeEach
    void setUp() {
        service = new ParticipantHistorySnapshotService(
                participantEventHistoryRepository, teamMemberRepository, teamEventEntryRepository,
                roundResultRepository, submissionRepository, prizeRepository, hackathonEventRepository);
    }

    @Test
    void snapshotDeparture_shouldInsert_thenOverwriteOnSecondCall() {
        HackathonEvent event = HackathonEvent.builder().eventId(1).name("Fall").season("FALL").year(2026)
                .status("IN_PROGRESS").build();
        Team team = Team.builder().teamId(9).name("PSG").build();
        TeamEventEntry entry = TeamEventEntry.builder().id(50).team(team).event(event).status("APPROVED").build();
        User user = User.builder().userId(100).fullName("Leader").build();
        TeamMember membership = TeamMember.builder().id(1).team(team).user(user).memberRole("LEADER")
                .joinedAt(LocalDateTime.now().minusDays(1)).build();

        lenient().when(teamEventEntryRepository.findAllByTeam_TeamId(9)).thenReturn(List.of(entry));
        lenient().when(teamMemberRepository.findByTeam_TeamId(9)).thenReturn(List.of(membership));
        lenient().when(roundResultRepository.findAllByTeamIdOrderByRoundOrder(9)).thenReturn(List.of());
        lenient().when(submissionRepository.findAllByTeam_TeamId(9)).thenReturn(List.of());
        lenient().when(prizeRepository.findAllByEvent_EventIdAndAwardedAtIsNotNullOrderByRankPosition(1)).thenReturn(List.of());

        // First call: no existing row -> insert.
        when(participantEventHistoryRepository.findByUser_UserIdAndEventId(100, 1)).thenReturn(Optional.empty());
        service.snapshotDeparture(membership, "LEFT_TEAM");

        ArgumentCaptor<ParticipantEventHistory> captor = ArgumentCaptor.forClass(ParticipantEventHistory.class);
        verify(participantEventHistoryRepository, times(1)).save(captor.capture());
        ParticipantEventHistory inserted = captor.getValue();
        assertEquals(1, inserted.getEventId());
        assertEquals("LEFT_TEAM", inserted.getSnapshotReason());
        assertNotNull(inserted.getResultJson());

        // Second call: existing row found -> the SAME row is updated, not a new one.
        ParticipantEventHistory existing = ParticipantEventHistory.builder()
                .id(77).user(user).eventId(1).snapshotReason("LEFT_TEAM")
                .snapshotAt(LocalDateTime.now().minusDays(1)).resultJson("{}").build();
        when(participantEventHistoryRepository.findByUser_UserIdAndEventId(100, 1)).thenReturn(Optional.of(existing));
        service.snapshotDeparture(membership, "REMOVED_BY_LEADER");

        verify(participantEventHistoryRepository, times(2)).save(any());
        assertEquals(77, existing.getId());
        assertEquals("REMOVED_BY_LEADER", existing.getSnapshotReason());
    }

    @Test
    void snapshotDeparture_shouldCoverEveryEntry_notJustTheNewestOne() {
        // Regression: a rejoined team has 2+ TeamEventEntry rows sharing ONE
        // TeamMember row. Deleting that row (which the caller does right
        // after snapshotDeparture returns) must not leave an older,
        // never-snapshotted season unreachable.
        HackathonEvent oldEvent = HackathonEvent.builder().eventId(1).name("Summer").season("SUMMER").year(2026)
                .status("COMPLETED").build();
        HackathonEvent newEvent = HackathonEvent.builder().eventId(2).name("Fall").season("FALL").year(2026)
                .status("OPEN").build();
        Team team = Team.builder().teamId(9).name("PSG").build();
        TeamEventEntry oldEntry = TeamEventEntry.builder().id(50).team(team).event(oldEvent).status("APPROVED")
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0)).build();
        TeamEventEntry newEntry = TeamEventEntry.builder().id(51).team(team).event(newEvent).status("APPROVED")
                .createdAt(LocalDateTime.of(2026, 7, 1, 0, 0)).build();
        User user = User.builder().userId(100).fullName("Member").build();
        TeamMember membership = TeamMember.builder().id(1).team(team).user(user).memberRole("MEMBER")
                .joinedAt(LocalDateTime.of(2025, 12, 1, 0, 0)).build(); // present for both seasons

        when(teamEventEntryRepository.findAllByTeam_TeamId(9)).thenReturn(List.of(oldEntry, newEntry));
        lenient().when(teamMemberRepository.findByTeam_TeamId(9)).thenReturn(List.of(membership));
        lenient().when(roundResultRepository.findAllByTeamIdOrderByRoundOrder(9)).thenReturn(List.of());
        lenient().when(submissionRepository.findAllByTeam_TeamId(9)).thenReturn(List.of());
        lenient().when(prizeRepository.findAllByEvent_EventIdAndAwardedAtIsNotNullOrderByRankPosition(any())).thenReturn(List.of());
        when(participantEventHistoryRepository.findByUser_UserIdAndEventId(any(), any())).thenReturn(Optional.empty());

        service.snapshotDeparture(membership, "LEFT_TEAM");

        ArgumentCaptor<ParticipantEventHistory> captor = ArgumentCaptor.forClass(ParticipantEventHistory.class);
        verify(participantEventHistoryRepository, times(2)).save(captor.capture());
        List<Integer> snapshottedEventIds = captor.getAllValues().stream()
                .map(ParticipantEventHistory::getEventId).sorted().toList();
        assertEquals(List.of(1, 2), snapshottedEventIds);
    }

    @Test
    void snapshotEventCompletion_shouldSnapshotEveryMember_ofEveryEntryInTheEvent() {
        HackathonEvent event = HackathonEvent.builder().eventId(3).name("Summer").season("SUMMER").year(2026)
                .status("COMPLETED").build();
        Team teamA = Team.builder().teamId(10).name("Arsenal").build();
        Team teamB = Team.builder().teamId(11).name("Chelsea").build();
        TeamEventEntry entryA = TeamEventEntry.builder().id(60).team(teamA).event(event).status("APPROVED").build();
        TeamEventEntry entryB = TeamEventEntry.builder().id(61).team(teamB).event(event).status("APPROVED").build();
        User userA1 = User.builder().userId(200).fullName("A Leader").build();
        User userB1 = User.builder().userId(300).fullName("B Leader").build();
        TeamMember memberA1 = TeamMember.builder().id(2).team(teamA).user(userA1).memberRole("LEADER")
                .joinedAt(LocalDateTime.now()).build();
        TeamMember memberB1 = TeamMember.builder().id(3).team(teamB).user(userB1).memberRole("LEADER")
                .joinedAt(LocalDateTime.now()).build();

        when(teamEventEntryRepository.findAllByEvent_EventId(3)).thenReturn(List.of(entryA, entryB));
        when(teamMemberRepository.findByTeam_TeamId(10)).thenReturn(List.of(memberA1));
        when(teamMemberRepository.findByTeam_TeamId(11)).thenReturn(List.of(memberB1));
        lenient().when(roundResultRepository.findAllByTeamIdOrderByRoundOrder(any())).thenReturn(List.of());
        lenient().when(submissionRepository.findAllByTeam_TeamId(any())).thenReturn(List.of());
        lenient().when(prizeRepository.findAllByEvent_EventIdAndAwardedAtIsNotNullOrderByRankPosition(3)).thenReturn(List.of());
        when(participantEventHistoryRepository.findByUser_UserIdAndEventId(any(), any())).thenReturn(Optional.empty());

        service.snapshotEventCompletion(3);

        ArgumentCaptor<ParticipantEventHistory> captor = ArgumentCaptor.forClass(ParticipantEventHistory.class);
        verify(participantEventHistoryRepository, times(2)).save(captor.capture());
        List<Integer> snapshottedUserIds = captor.getAllValues().stream()
                .map(row -> row.getUser().getUserId()).sorted().toList();
        assertEquals(List.of(200, 300), snapshottedUserIds);
        captor.getAllValues().forEach(row -> assertEquals("COMPLETED", row.getSnapshotReason()));
    }

    @Test
    void getSnapshotsForUser_shouldRoundTripFullResponse_includingLocalDateTimeFields() {
        TeamHistoryResponse original = TeamHistoryResponse.builder()
                .eventId(7).eventName("SEAL Demo Summer 2026").season("SUMMER").year(2026).eventStatus("COMPLETED")
                .teamId(9).teamName("PSG").trackName("AI Solution").teamStatus("APPROVED").myRole("LEADER")
                .members(List.of(TeamHistoryResponse.MemberInfo.builder().fullName("Leader").role("LEADER").build()))
                .rounds(List.of(TeamHistoryResponse.RoundInfo.builder().roundName("Final").isFinal(true)
                        .rankPosition(1).advanced(true).totalScore(BigDecimal.valueOf(92.5)).build()))
                .submissions(List.of(TeamHistoryResponse.SubmissionInfo.builder().roundName("Final")
                        .repoUrl("https://github.com/x").submittedAt(LocalDateTime.of(2026, 7, 1, 10, 0))
                        .status("SUBMITTED").build()))
                .prize(TeamHistoryResponse.PrizeInfo.builder().name("Champion").rankPosition(1)
                        .awardedAt(LocalDateTime.of(2026, 7, 2, 9, 0)).build())
                .build();

        ParticipantEventHistory row;
        try {
            row = ParticipantEventHistory.builder()
                    .id(1).user(User.builder().userId(100).build()).eventId(7)
                    .snapshotReason("COMPLETED").snapshotAt(LocalDateTime.now())
                    .resultJson(objectMapper.writeValueAsString(original))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(participantEventHistoryRepository.findByUser_UserId(100)).thenReturn(List.of(row));

        Map<Integer, TeamHistoryResponse> result = service.getSnapshotsForUser(100);

        assertEquals(1, result.size());
        TeamHistoryResponse deserialized = result.get(7);
        assertEquals("PSG", deserialized.getTeamName());
        assertEquals(1, deserialized.getSubmissions().size());
        assertEquals(LocalDateTime.of(2026, 7, 1, 10, 0), deserialized.getSubmissions().get(0).getSubmittedAt());
        assertEquals(LocalDateTime.of(2026, 7, 2, 9, 0), deserialized.getPrize().getAwardedAt());
    }

    @Test
    void backfillCompletedEvents_shouldOnlyProcessCompletedEvents() {
        HackathonEvent completed = HackathonEvent.builder().eventId(1).status("COMPLETED").build();
        HackathonEvent open = HackathonEvent.builder().eventId(2).status("OPEN").build();
        when(hackathonEventRepository.findAll()).thenReturn(List.of(completed, open));
        when(teamEventEntryRepository.findAllByEvent_EventId(1)).thenReturn(List.of());

        int processed = service.backfillCompletedEvents();

        assertEquals(1, processed);
        verify(teamEventEntryRepository, never()).findAllByEvent_EventId(2);
    }
}
