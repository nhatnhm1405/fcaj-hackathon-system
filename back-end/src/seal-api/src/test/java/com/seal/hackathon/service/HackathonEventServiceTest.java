package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.CreateEventRequest;
import com.seal.hackathon.dto.request.UpdateEventRequest;
import com.seal.hackathon.dto.response.HackathonEventResponse;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.Track;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.repository.HackathonEventRepository;
import com.seal.hackathon.repository.JudgeAssignmentRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TeamRepository;
import com.seal.hackathon.repository.TrackRepository;
import com.seal.hackathon.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers event schedule validation plus the SETUP -> IN_PROGRESS start gate.
 */
@ExtendWith(MockitoExtension.class)
class HackathonEventServiceTest {

    @Mock
    private HackathonEventRepository hackathonEventRepository;

    @Mock
    private TrackRepository trackRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamEventEntryRepository teamEventEntryRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JudgeAssignmentRepository judgeAssignmentRepository;

    @Mock
    private ParticipantHistorySnapshotService participantHistorySnapshotService;

    @InjectMocks
    private HackathonEventService eventService;

    @Test
    void createEvent_shouldSucceed_whenFutureDatesMatchSeasonAndNoDuplicateOrOverlap() {
        int year = futureYear();
        LocalDateTime registrationStart = date(year, 9, 1);
        LocalDateTime registrationEnd = date(year, 10, 15);
        LocalDateTime startDate = date(year, 12, 14);
        LocalDateTime endDate = date(year, 12, 15);
        CreateEventRequest request = createEventRequest(" fall ", year,
                registrationStart, registrationEnd, startDate, endDate);

        when(hackathonEventRepository.existsByYearAndSeasonIgnoreCaseAndStatusNotIgnoreCase(
                year, "FALL", "CANCELLED")).thenReturn(false);
        when(hackathonEventRepository.existsOverlappingActiveEvent(
                startDate, endDate, null, "CANCELLED")).thenReturn(false);
        when(hackathonEventRepository.save(any(HackathonEvent.class))).thenAnswer(invocation -> {
            HackathonEvent saved = invocation.getArgument(0);
            saved.setEventId(99);
            return saved;
        });

        HackathonEventResponse response = eventService.createEvent(request, 7);

        assertEquals("FALL", response.getSeason());
        assertEquals(year, response.getYear());
        assertEquals("DRAFT", response.getStatus());
        verify(hackathonEventRepository).save(any(HackathonEvent.class));
    }

    @Test
    void createEvent_shouldThrow_whenAnyDateIsInThePast() {
        int year = 2026;
        CreateEventRequest request = createEventRequest("SPRING", year,
                date(year, 1, 15), date(year, 2, 15), date(year, 4, 14), date(year, 4, 15));

        assertThrows(BadRequestException.class, () -> eventService.createEvent(request, 7));

        verify(hackathonEventRepository, never()).save(any());
    }

    @Test
    void createEvent_shouldThrow_whenDateDoesNotMatchSeason() {
        int year = futureYear();
        CreateEventRequest request = createEventRequest("SPRING", year,
                date(year, 5, 1), date(year, 5, 2), date(year, 5, 3), date(year, 5, 4));

        assertThrows(BadRequestException.class, () -> eventService.createEvent(request, 7));

        verify(hackathonEventRepository, never()).save(any());
    }

    @Test
    void createEvent_shouldThrow_whenActiveSeasonYearAlreadyExists() {
        int year = futureYear();
        CreateEventRequest request = createEventRequest("FALL", year,
                date(year, 9, 1), date(year, 10, 15), date(year, 12, 14), date(year, 12, 15));

        when(hackathonEventRepository.existsByYearAndSeasonIgnoreCaseAndStatusNotIgnoreCase(
                year, "FALL", "CANCELLED")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> eventService.createEvent(request, 7));

        verify(hackathonEventRepository, never()).save(any());
    }

    @Test
    void createEvent_shouldThrow_whenActiveEventDatesOverlap() {
        int year = futureYear();
        LocalDateTime startDate = date(year, 12, 14);
        LocalDateTime endDate = date(year, 12, 15);
        CreateEventRequest request = createEventRequest("FALL", year,
                date(year, 9, 1), date(year, 10, 15), startDate, endDate);

        when(hackathonEventRepository.existsByYearAndSeasonIgnoreCaseAndStatusNotIgnoreCase(
                year, "FALL", "CANCELLED")).thenReturn(false);
        when(hackathonEventRepository.existsOverlappingActiveEvent(
                startDate, endDate, null, "CANCELLED")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> eventService.createEvent(request, 7));

        verify(hackathonEventRepository, never()).save(any());
    }

    @Test
    void createEvent_shouldThrow_whenYearIsBefore2026() {
        int year = 2025;
        CreateEventRequest request = createEventRequest("FALL", year,
                date(year, 9, 1), date(year, 10, 15), date(year, 12, 14), date(year, 12, 15));

        assertThrows(BadRequestException.class, () -> eventService.createEvent(request, 7));

        verify(hackathonEventRepository, never()).save(any());
    }

    @Test
    void updateEvent_shouldThrow_whenSeasonIsInvalid() {
        HackathonEvent event = fallEvent(1, "DRAFT", futureYear());
        UpdateEventRequest request = new UpdateEventRequest();
        request.setSeason("WINTER");

        when(hackathonEventRepository.findById(1)).thenReturn(Optional.of(event));

        assertThrows(BadRequestException.class, () -> eventService.updateEvent(1, request));

        verify(hackathonEventRepository, never()).save(any());
    }

    @Test
    void updateEvent_shouldValidateRequestedDateAgainstExistingEventDates() {
        HackathonEvent event = fallEvent(1, "DRAFT", futureYear());
        UpdateEventRequest request = new UpdateEventRequest();
        request.setRegistrationStart(event.getRegistrationEnd().plusDays(1));

        when(hackathonEventRepository.findById(1)).thenReturn(Optional.of(event));

        assertThrows(BadRequestException.class, () -> eventService.updateEvent(1, request));

        verify(hackathonEventRepository, never()).save(any());
    }

    @Test
    void updateEvent_shouldThrow_whenRequestedDateIsInThePast() {
        int year = 2026;
        HackathonEvent event = springEvent(1, "DRAFT", year);
        UpdateEventRequest request = new UpdateEventRequest();
        request.setRegistrationStart(date(year, 1, 16));

        when(hackathonEventRepository.findById(1)).thenReturn(Optional.of(event));

        assertThrows(BadRequestException.class, () -> eventService.updateEvent(1, request));

        verify(hackathonEventRepository, never()).save(any());
    }

    @Test
    void updateEvent_shouldThrow_whenYearIsAfter3000() {
        HackathonEvent event = fallEvent(1, "DRAFT", futureYear());
        UpdateEventRequest request = new UpdateEventRequest();
        request.setYear(3001);

        when(hackathonEventRepository.findById(1)).thenReturn(Optional.of(event));

        assertThrows(BadRequestException.class, () -> eventService.updateEvent(1, request));

        verify(hackathonEventRepository, never()).save(any());
    }

    @Test
    void updateEvent_shouldThrow_whenSeasonChangeDoesNotMatchExistingDates() {
        HackathonEvent event = fallEvent(1, "DRAFT", futureYear());
        UpdateEventRequest request = new UpdateEventRequest();
        request.setSeason("SPRING");

        when(hackathonEventRepository.findById(1)).thenReturn(Optional.of(event));

        assertThrows(BadRequestException.class, () -> eventService.updateEvent(1, request));

        verify(hackathonEventRepository, never()).save(any());
    }

    @Test
    void updateEvent_shouldThrow_whenYearUpdateTargetsExistingActiveSeasonEvent() {
        HackathonEvent event = fallEvent(1, "DRAFT", futureYear());
        int newYear = event.getYear() + 1;
        UpdateEventRequest request = new UpdateEventRequest();
        request.setYear(newYear);
        request.setRegistrationStart(date(newYear, 9, 1));
        request.setRegistrationEnd(date(newYear, 10, 15));
        request.setStartDate(date(newYear, 12, 14));
        request.setEndDate(date(newYear, 12, 15));

        when(hackathonEventRepository.findById(1)).thenReturn(Optional.of(event));
        when(hackathonEventRepository.existsByYearAndSeasonIgnoreCaseAndStatusNotIgnoreCaseAndEventIdNot(
                newYear, "FALL", "CANCELLED", 1)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> eventService.updateEvent(1, request));

        verify(hackathonEventRepository, never()).save(any());
    }

    @Test
    void updateEvent_shouldSucceed_whenSeasonUpdateOnlyConflictsWithCancelledEvent() {
        HackathonEvent event = fallEvent(1, "DRAFT", futureYear());
        UpdateEventRequest request = new UpdateEventRequest();
        request.setSeason("SUMMER");
        request.setRegistrationStart(date(event.getYear(), 5, 1));
        request.setRegistrationEnd(date(event.getYear(), 6, 15));
        request.setStartDate(date(event.getYear(), 8, 14));
        request.setEndDate(date(event.getYear(), 8, 15));

        when(hackathonEventRepository.findById(1)).thenReturn(Optional.of(event));
        when(hackathonEventRepository.existsByYearAndSeasonIgnoreCaseAndStatusNotIgnoreCaseAndEventIdNot(
                event.getYear(), "SUMMER", "CANCELLED", 1)).thenReturn(false);
        when(hackathonEventRepository.existsOverlappingActiveEvent(
                request.getStartDate(), request.getEndDate(), 1, "CANCELLED")).thenReturn(false);
        when(hackathonEventRepository.save(event)).thenReturn(event);

        HackathonEventResponse response = eventService.updateEvent(1, request);

        assertEquals("SUMMER", response.getSeason());
        verify(hackathonEventRepository).save(event);
    }

    @Test
    void updateEvent_shouldThrow_whenReactivatingCancelledEventAndActiveSeasonYearExists() {
        HackathonEvent event = fallEvent(1, "CANCELLED", futureYear());

        when(hackathonEventRepository.findById(1)).thenReturn(Optional.of(event));
        when(hackathonEventRepository.existsByYearAndSeasonIgnoreCaseAndStatusNotIgnoreCaseAndEventIdNot(
                event.getYear(), "FALL", "CANCELLED", 1)).thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> eventService.updateEvent(1, statusRequest("DRAFT")));

        verify(hackathonEventRepository, never()).save(any());
    }

    @Test
    void updateEvent_shouldSucceed_whenReactivatingCancelledEventAndNoActiveSeasonYearExists() {
        HackathonEvent event = fallEvent(1, "CANCELLED", futureYear());

        when(hackathonEventRepository.findById(1)).thenReturn(Optional.of(event));
        when(hackathonEventRepository.existsByYearAndSeasonIgnoreCaseAndStatusNotIgnoreCaseAndEventIdNot(
                event.getYear(), "FALL", "CANCELLED", 1)).thenReturn(false);
        when(hackathonEventRepository.existsOverlappingActiveEvent(
                event.getStartDate(), event.getEndDate(), 1, "CANCELLED")).thenReturn(false);
        when(hackathonEventRepository.save(event)).thenReturn(event);

        HackathonEventResponse response = eventService.updateEvent(1, statusRequest("DRAFT"));

        assertEquals("DRAFT", response.getStatus());
        verify(hackathonEventRepository).save(event);
    }

    @Test
    void updateEvent_shouldThrow_whenUpdatedCompetitionDatesOverlapAnotherActiveEvent() {
        HackathonEvent event = fallEvent(1, "DRAFT", futureYear());
        LocalDateTime newStartDate = event.getStartDate().plusHours(2);
        UpdateEventRequest request = new UpdateEventRequest();
        request.setStartDate(newStartDate);

        when(hackathonEventRepository.findById(1)).thenReturn(Optional.of(event));
        when(hackathonEventRepository.existsByYearAndSeasonIgnoreCaseAndStatusNotIgnoreCaseAndEventIdNot(
                event.getYear(), "FALL", "CANCELLED", 1)).thenReturn(false);
        when(hackathonEventRepository.existsOverlappingActiveEvent(
                newStartDate, event.getEndDate(), 1, "CANCELLED")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> eventService.updateEvent(1, request));

        verify(hackathonEventRepository, never()).save(any());
    }

    @Test
    void updateEvent_shouldSucceed_whenOnlyOneRequestedDateKeepsEffectiveScheduleValid() {
        HackathonEvent event = fallEvent(1, "DRAFT", futureYear());
        LocalDateTime newRegistrationEnd = event.getRegistrationEnd().plusDays(1);
        UpdateEventRequest request = new UpdateEventRequest();
        request.setRegistrationEnd(newRegistrationEnd);

        when(hackathonEventRepository.findById(1)).thenReturn(Optional.of(event));
        when(hackathonEventRepository.save(event)).thenReturn(event);

        HackathonEventResponse response = eventService.updateEvent(1, request);

        assertEquals(newRegistrationEnd, response.getRegistrationEnd());
        verify(hackathonEventRepository).save(event);
    }

    @Test
    void startEvent_shouldSucceed_whenEveryTrackHasTwoTeamsAndNoneUnassigned() {
        HackathonEvent event = event(1, "SETUP");
        Track ai = track(10, event);
        Track web = track(20, event);

        when(hackathonEventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.findAllByEvent_EventId(1)).thenReturn(List.of(ai, web));
        when(teamEventEntryRepository.findAllByEvent_EventIdAndStatus(1, "APPROVED")).thenReturn(List.of(
                teamOnTrack(100, event, ai), teamOnTrack(101, event, ai),
                teamOnTrack(102, event, web), teamOnTrack(103, event, web)));
        when(hackathonEventRepository.save(event)).thenReturn(event);

        HackathonEventResponse response = eventService.updateEvent(1, statusRequest("IN_PROGRESS"));

        assertEquals("IN_PROGRESS", response.getStatus());
        verify(hackathonEventRepository).save(event);
    }

    @Test
    void startEvent_shouldThrow_whenATrackHasFewerThanTwoTeams() {
        HackathonEvent event = event(1, "SETUP");
        Track ai = track(10, event);

        when(hackathonEventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.findAllByEvent_EventId(1)).thenReturn(List.of(ai));
        when(teamEventEntryRepository.findAllByEvent_EventIdAndStatus(1, "APPROVED"))
                .thenReturn(List.of(teamOnTrack(100, event, ai)));

        assertThrows(BadRequestException.class,
                () -> eventService.updateEvent(1, statusRequest("IN_PROGRESS")));

        verify(hackathonEventRepository, never()).save(any());
    }

    @Test
    void startEvent_shouldThrow_whenTeamsAreUnassigned() {
        HackathonEvent event = event(1, "SETUP");
        Track ai = track(10, event);

        when(hackathonEventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.findAllByEvent_EventId(1)).thenReturn(List.of(ai));
        when(teamEventEntryRepository.findAllByEvent_EventIdAndStatus(1, "APPROVED")).thenReturn(List.of(
                teamOnTrack(100, event, ai), teamOnTrack(101, event, ai),
                unassignedTeam(102, event)));

        assertThrows(BadRequestException.class,
                () -> eventService.updateEvent(1, statusRequest("IN_PROGRESS")));

        verify(hackathonEventRepository, never()).save(any());
    }

    @Test
    void startEvent_shouldThrow_whenEventHasNoTracks() {
        HackathonEvent event = event(1, "SETUP");

        when(hackathonEventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.findAllByEvent_EventId(1)).thenReturn(List.of());
        when(teamEventEntryRepository.findAllByEvent_EventIdAndStatus(1, "APPROVED")).thenReturn(List.of());

        assertThrows(BadRequestException.class,
                () -> eventService.updateEvent(1, statusRequest("IN_PROGRESS")));

        verify(hackathonEventRepository, never()).save(any());
    }

    @Test
    void moveToSetup_shouldThrow_whenTeamsAreStillPendingApproval() {
        HackathonEvent event = fallEvent(1, "OPEN", futureYear());

        when(hackathonEventRepository.findById(1)).thenReturn(Optional.of(event));
        when(teamEventEntryRepository.countByEvent_EventIdAndStatus(1, "PENDING")).thenReturn(2L);

        assertThrows(BadRequestException.class,
                () -> eventService.updateEvent(1, statusRequest("SETUP")));

        verify(hackathonEventRepository, never()).save(any());
    }

    @Test
    void completeEvent_shouldSnapshotParticipantHistory_whenTransitioningFromInProgress() {
        HackathonEvent event = event(1, "IN_PROGRESS");

        when(hackathonEventRepository.findById(1)).thenReturn(Optional.of(event));
        when(hackathonEventRepository.save(any(HackathonEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(teamEventEntryRepository.findAllByEvent_EventId(1)).thenReturn(List.of());
        when(judgeAssignmentRepository.findActiveByEvent(1)).thenReturn(List.of());

        eventService.completeEvent(1);

        assertEquals("COMPLETED", event.getStatus());
        verify(participantHistorySnapshotService).snapshotEventCompletion(1);
    }

    @Test
    void completeEvent_shouldThrowBadRequest_whenEventIsNotInProgress() {
        HackathonEvent event = event(1, "OPEN");
        when(hackathonEventRepository.findById(1)).thenReturn(Optional.of(event));

        assertThrows(BadRequestException.class, () -> eventService.completeEvent(1));

        verify(participantHistorySnapshotService, never()).snapshotEventCompletion(any());
    }

    @Test
    void completeEvent_shouldDeactivateTeam_whenTeamHasNoEntryInAnotherNonCompletedEvent() {
        HackathonEvent completingEvent = event(1, "IN_PROGRESS");
        Team team = Team.builder().teamId(500).name("Team 500").isActive(true).build();
        TeamEventEntry entry = TeamEventEntry.builder().id(1).team(team).event(completingEvent).status("APPROVED").build();

        when(hackathonEventRepository.findById(1)).thenReturn(Optional.of(completingEvent));
        when(hackathonEventRepository.save(any(HackathonEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(teamEventEntryRepository.findAllByEvent_EventId(1)).thenReturn(List.of(entry));
        when(teamMemberRepository.findByTeam_TeamId(500)).thenReturn(List.of());
        when(teamEventEntryRepository.findAllByTeam_TeamId(500)).thenReturn(List.of(entry));
        when(judgeAssignmentRepository.findActiveByEvent(1)).thenReturn(List.of());

        eventService.completeEvent(1);

        assertFalse(team.getIsActive());
        verify(teamRepository).saveAll(List.of(team));
    }

    @Test
    void completeEvent_shouldKeepTeamActive_whenTeamHasEntryInAnotherNonCompletedEvent() {
        // Multi-season scenario: a next-season event was already opened (non-COMPLETED
        // status) before this admin got around to completing the older one — event date
        // ranges can't overlap, but status transitions are manual, so this gap is real.
        HackathonEvent completingEvent = event(1, "IN_PROGRESS");
        HackathonEvent nextSeasonEvent = fallEvent(2, "OPEN", futureYear() + 1);
        Team team = Team.builder().teamId(500).name("Team 500").isActive(true).build();
        TeamEventEntry completingEntry = TeamEventEntry.builder().id(1).team(team).event(completingEvent).status("APPROVED").build();
        TeamEventEntry nextSeasonEntry = TeamEventEntry.builder().id(2).team(team).event(nextSeasonEvent).status("APPROVED").build();

        when(hackathonEventRepository.findById(1)).thenReturn(Optional.of(completingEvent));
        when(hackathonEventRepository.save(any(HackathonEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(teamEventEntryRepository.findAllByEvent_EventId(1)).thenReturn(List.of(completingEntry));
        when(teamMemberRepository.findByTeam_TeamId(500)).thenReturn(List.of());
        when(teamEventEntryRepository.findAllByTeam_TeamId(500))
                .thenReturn(List.of(completingEntry, nextSeasonEntry));
        when(judgeAssignmentRepository.findActiveByEvent(1)).thenReturn(List.of());

        eventService.completeEvent(1);

        assertTrue(team.getIsActive());
        verify(teamRepository).saveAll(List.of());
    }

    // Helpers

    private static UpdateEventRequest statusRequest(String status) {
        UpdateEventRequest request = new UpdateEventRequest();
        request.setStatus(status);
        return request;
    }

    private static CreateEventRequest createEventRequest(String season, Integer year,
                                                         LocalDateTime registrationStart,
                                                         LocalDateTime registrationEnd,
                                                         LocalDateTime startDate,
                                                         LocalDateTime endDate) {
        CreateEventRequest request = new CreateEventRequest();
        request.setName("SEAL Hackathon");
        request.setSeason(season);
        request.setYear(year);
        request.setRegistrationStart(registrationStart);
        request.setRegistrationEnd(registrationEnd);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        return request;
    }

    private static HackathonEvent event(Integer eventId, String status) {
        return fallEvent(eventId, status, futureYear());
    }

    private static HackathonEvent fallEvent(Integer eventId, String status, int year) {
        return HackathonEvent.builder()
                .eventId(eventId)
                .name("SEAL Hackathon " + eventId)
                .season("FALL")
                .year(year)
                .registrationStart(date(year, 9, 1))
                .registrationEnd(date(year, 10, 15))
                .startDate(date(year, 12, 14))
                .endDate(date(year, 12, 15))
                .status(status)
                .trackSelectionMode("RANDOM")
                .build();
    }

    private static LocalDateTime date(int year, int month, int day) {
        return LocalDateTime.of(year, month, day, 9, 0);
    }

    private static int futureYear() {
        return LocalDate.now().getYear() + 2;
    }

    private static HackathonEvent springEvent(Integer eventId, String status, int year) {
        return HackathonEvent.builder()
                .eventId(eventId)
                .name("SEAL Hackathon " + eventId)
                .season("SPRING")
                .year(year)
                .registrationStart(date(year, 1, 15))
                .registrationEnd(date(year, 2, 15))
                .startDate(date(year, 4, 14))
                .endDate(date(year, 4, 15))
                .status(status)
                .trackSelectionMode("RANDOM")
                .build();
    }

    private static Track track(Integer trackId, HackathonEvent event) {
        return Track.builder().trackId(trackId).event(event).name("Track " + trackId).build();
    }

    private static TeamEventEntry teamOnTrack(Integer teamId, HackathonEvent event, Track track) {
        Team team = Team.builder().teamId(teamId).name("Team " + teamId).build();
        return TeamEventEntry.builder().id(teamId).team(team).event(event).track(track).status("APPROVED").build();
    }

    private static TeamEventEntry unassignedTeam(Integer teamId, HackathonEvent event) {
        Team team = Team.builder().teamId(teamId).name("Team " + teamId).build();
        return TeamEventEntry.builder().id(teamId).team(team).event(event).track(null).status("APPROVED").build();
    }
}
