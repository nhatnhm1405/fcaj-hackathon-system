package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.CreateTrackRequest;
import com.seal.hackathon.dto.response.TrackResponse;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.Track;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.AnnouncementRepository;
import com.seal.hackathon.repository.HackathonEventRepository;
import com.seal.hackathon.repository.JudgeAssignmentRepository;
import com.seal.hackathon.repository.MentorAssignmentRepository;
import com.seal.hackathon.repository.MentorSupportRequestRepository;
import com.seal.hackathon.repository.PrizeRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TrackRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackServiceTest {

    @Mock
    private TrackRepository trackRepository;

    @Mock
    private HackathonEventRepository eventRepository;

    @Mock
    private TeamEventEntryRepository teamEventEntryRepository;

    @Mock
    private MentorAssignmentRepository mentorAssignmentRepository;

    @Mock
    private JudgeAssignmentRepository judgeAssignmentRepository;

    @Mock
    private AnnouncementRepository announcementRepository;

    @Mock
    private MentorSupportRequestRepository supportRequestRepository;

    @Mock
    private PrizeRepository prizeRepository;

    @InjectMocks
    private TrackService trackService;

    @Test
    void getTracksByEvent_shouldReturnTracksSortedByName_whenEventExists() {
        HackathonEvent event = event(1, "DRAFT");
        Track webTrack = track(20, event, "Web");
        Track aiTrack = track(10, event, "AI");
        Track dataTrack = track(30, event, "Data");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.findAllByEvent_EventId(1)).thenReturn(List.of(webTrack, aiTrack, dataTrack));

        List<TrackResponse> response = trackService.getTracksByEvent(1);

        assertEquals(List.of("AI", "Data", "Web"), response.stream().map(TrackResponse::getName).toList());
    }

    @Test
    void getTracksByEvent_shouldReturnEmptyList_whenEventHasNoTracks() {
        HackathonEvent event = event(1, "DRAFT");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.findAllByEvent_EventId(1)).thenReturn(List.of());

        List<TrackResponse> response = trackService.getTracksByEvent(1);

        assertEquals(0, response.size());
    }

    @Test
    void getTracksByEvent_shouldThrowBadRequest_whenEventIdIsNull() {
        assertThrows(BadRequestException.class, () -> trackService.getTracksByEvent(null));

        verify(eventRepository, never()).findById(any());
        verify(trackRepository, never()).findAllByEvent_EventId(anyInt());
    }

    @Test
    void getTracksByEvent_shouldThrowResourceNotFound_whenEventDoesNotExist() {
        when(eventRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> trackService.getTracksByEvent(1));

        verify(trackRepository, never()).findAllByEvent_EventId(anyInt());
    }

    @Test
    void getTrackById_shouldReturnTrack_whenTrackBelongsToEvent() {
        HackathonEvent event = event(1, "DRAFT");
        Track track = track(10, event, "AI");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.findById(10)).thenReturn(Optional.of(track));

        TrackResponse response = trackService.getTrackById(1, 10);

        assertEquals(10, response.getTrackId());
        assertEquals(1, response.getEventId());
        assertEquals("AI", response.getName());
    }

    @Test
    void getTrackById_shouldThrowBadRequest_whenEventIdIsNull() {
        assertThrows(BadRequestException.class, () -> trackService.getTrackById(null, 10));

        verify(eventRepository, never()).findById(any());
        verify(trackRepository, never()).findById(anyInt());
    }

    @Test
    void getTrackById_shouldThrowBadRequest_whenTrackIdIsNull() {
        assertThrows(BadRequestException.class, () -> trackService.getTrackById(1, null));

        verify(eventRepository, never()).findById(any());
        verify(trackRepository, never()).findById(anyInt());
    }

    @Test
    void getTrackById_shouldThrowResourceNotFound_whenEventDoesNotExist() {
        when(eventRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> trackService.getTrackById(1, 10));

        verify(trackRepository, never()).findById(anyInt());
    }

    @Test
    void getTrackById_shouldThrowResourceNotFound_whenTrackDoesNotExist() {
        HackathonEvent event = event(1, "DRAFT");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.findById(10)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> trackService.getTrackById(1, 10));
    }

    @Test
    void getTrackById_shouldThrowBadRequest_whenTrackDoesNotBelongToEvent() {
        HackathonEvent requestedEvent = event(1, "DRAFT");
        HackathonEvent otherEvent = event(2, "DRAFT");
        Track track = track(10, otherEvent, "AI");

        when(eventRepository.findById(1)).thenReturn(Optional.of(requestedEvent));
        when(trackRepository.findById(10)).thenReturn(Optional.of(track));

        assertThrows(BadRequestException.class, () -> trackService.getTrackById(1, 10));
    }

    @Test
    void createTrack_shouldCreateTrack_whenEventIsDraftAndRequestIsValid() {
        HackathonEvent event = event(1, "DRAFT");
        CreateTrackRequest request = request(" AI ", " Track description ");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.existsByEventIdAndNormalizedName(1, "AI")).thenReturn(false);
        when(trackRepository.save(any(Track.class))).thenAnswer(invocation -> {
            Track track = invocation.getArgument(0);
            track.setTrackId(10);
            return track;
        });

        TrackResponse response = trackService.createTrack(1, request);

        assertEquals(10, response.getTrackId());
        assertEquals("AI", response.getName());
        assertEquals("Track description", response.getDescription());
        verify(trackRepository).save(any(Track.class));
    }

    @Test
    void createTrack_shouldAcceptNormalizedDraftStatusAndMaxLengthName() {
        HackathonEvent event = event(1, " draft ");
        String maxLengthName = "A".repeat(255);

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.existsByEventIdAndNormalizedName(1, maxLengthName)).thenReturn(false);
        when(trackRepository.save(any(Track.class))).thenAnswer(invocation -> {
            Track track = invocation.getArgument(0);
            track.setTrackId(10);
            return track;
        });

        TrackResponse response = trackService.createTrack(1, request(maxLengthName, null));

        assertEquals(maxLengthName, response.getName());
        assertNull(response.getDescription());
    }

    @Test
    void createTrack_shouldSetDescriptionNull_whenDescriptionIsBlank() {
        HackathonEvent event = event(1, "DRAFT");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.existsByEventIdAndNormalizedName(1, "AI")).thenReturn(false);
        when(trackRepository.save(any(Track.class))).thenAnswer(invocation -> {
            Track track = invocation.getArgument(0);
            track.setTrackId(10);
            return track;
        });

        TrackResponse response = trackService.createTrack(1, request("AI", "   "));

        assertNull(response.getDescription());
    }

    @Test
    void createTrack_shouldThrowBadRequest_whenEventIdIsNull() {
        assertThrows(BadRequestException.class, () -> trackService.createTrack(null, request("AI", null)));

        verify(eventRepository, never()).findById(any());
        verify(trackRepository, never()).save(any());
    }

    @Test
    void createTrack_shouldThrowResourceNotFound_whenEventDoesNotExist() {
        when(eventRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> trackService.createTrack(1, request("AI", null)));

        verify(trackRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ON-GOING", "COMPLETED", "CANCELLED"})
    void createTrack_shouldThrowBadRequest_whenEventStatusCannotMutateTracks(String status) {
        HackathonEvent event = event(1, status);

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));

        assertThrows(BadRequestException.class, () -> trackService.createTrack(1, request("AI", null)));

        verify(trackRepository, never()).existsByEventIdAndNormalizedName(anyInt(), any());
        verify(trackRepository, never()).save(any());
    }

    // Track CREATION is locked once registration closes (SETUP), even though
    // update/delete stay allowed in SETUP. The various spellings exercise the
    // status normalization (SET UP / set-up / padding all collapse to SETUP).
    @ParameterizedTest
    @ValueSource(strings = {"SETUP", "SET UP", "set-up", " setup "})
    void createTrack_shouldThrowBadRequest_whenEventIsInSetup(String status) {
        HackathonEvent event = event(1, status);

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));

        assertThrows(BadRequestException.class, () -> trackService.createTrack(1, request("AI", null)));

        verify(trackRepository, never()).existsByEventIdAndNormalizedName(anyInt(), any());
        verify(trackRepository, never()).save(any());
    }

    @Test
    void createTrack_shouldCreateTrack_whenEventIsOpen() {
        HackathonEvent event = event(1, "OPEN");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.existsByEventIdAndNormalizedName(1, "AI")).thenReturn(false);
        when(trackRepository.save(any(Track.class))).thenAnswer(invocation -> {
            Track track = invocation.getArgument(0);
            track.setTrackId(10);
            return track;
        });

        TrackResponse response = trackService.createTrack(1, request("AI", null));

        assertEquals(10, response.getTrackId());
        assertEquals("AI", response.getName());
        verify(trackRepository).save(any(Track.class));
    }

    @Test
    void createTrack_shouldThrowBadRequest_whenEventStatusIsNull() {
        HackathonEvent event = event(1, null);

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));

        assertThrows(BadRequestException.class, () -> trackService.createTrack(1, request("AI", null)));

        verify(trackRepository, never()).save(any());
    }

    @Test
    void createTrack_shouldThrowBadRequest_whenEventStatusIsBlank() {
        HackathonEvent event = event(1, "   ");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));

        assertThrows(BadRequestException.class, () -> trackService.createTrack(1, request("AI", null)));

        verify(trackRepository, never()).save(any());
    }

    @Test
    void createTrack_shouldThrowBadRequest_whenRequestIsNull() {
        HackathonEvent event = event(1, "DRAFT");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));

        assertThrows(BadRequestException.class, () -> trackService.createTrack(1, null));

        verify(trackRepository, never()).save(any());
    }

    @Test
    void createTrack_shouldThrowBadRequest_whenTrackNameIsNull() {
        HackathonEvent event = event(1, "DRAFT");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));

        assertThrows(BadRequestException.class, () -> trackService.createTrack(1, request(null, null)));

        verify(trackRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void createTrack_shouldThrowBadRequest_whenTrackNameIsBlank(String name) {
        HackathonEvent event = event(1, "DRAFT");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));

        assertThrows(BadRequestException.class, () -> trackService.createTrack(1, request(name, null)));

        verify(trackRepository, never()).save(any());
    }

    @Test
    void createTrack_shouldThrowBadRequest_whenTrackNameExceedsLimit() {
        HackathonEvent event = event(1, "DRAFT");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));

        assertThrows(BadRequestException.class,
                () -> trackService.createTrack(1, request("A".repeat(256), "Description")));

        verify(trackRepository, never()).existsByEventIdAndNormalizedName(anyInt(), any());
        verify(trackRepository, never()).save(any());
    }

    @Test
    void createTrack_shouldThrowBadRequest_whenDescriptionExceedsLimit() {
        HackathonEvent event = event(1, "DRAFT");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));

        assertThrows(BadRequestException.class,
                () -> trackService.createTrack(1, request("AI", "D".repeat(2001))));

        verify(trackRepository, never()).existsByEventIdAndNormalizedName(anyInt(), any());
        verify(trackRepository, never()).save(any());
    }

    @Test
    void createTrack_shouldThrowBadRequest_whenNormalizedNameAlreadyExists() {
        HackathonEvent event = event(1, "DRAFT");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.existsByEventIdAndNormalizedName(1, "AI")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> trackService.createTrack(1, request(" ai ", "Description")));

        verify(trackRepository, never()).save(any());
    }

    @Test
    void updateTrack_shouldUpdateNameAndDescription_whenEventIsDraft() {
        HackathonEvent event = event(1, "DRAFT");
        Track track = track(10, event, "Old");
        CreateTrackRequest request = request(" New Name ", " Updated description ");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.findById(10)).thenReturn(Optional.of(track));
        when(trackRepository.existsByEventIdAndNormalizedNameAndTrackIdNot(1, "NEW NAME", 10)).thenReturn(false);
        when(trackRepository.save(track)).thenReturn(track);

        TrackResponse response = trackService.updateTrack(1, 10, request);

        assertEquals("New Name", response.getName());
        assertEquals("Updated description", response.getDescription());
        verify(trackRepository).save(track);
    }

    @Test
    void updateTrack_shouldUpdateDescriptionOnly_whenNameIsNull() {
        HackathonEvent event = event(1, "DRAFT");
        Track track = track(10, event, "AI");
        CreateTrackRequest request = request(null, " New description ");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.findById(10)).thenReturn(Optional.of(track));
        when(trackRepository.save(track)).thenReturn(track);

        TrackResponse response = trackService.updateTrack(1, 10, request);

        assertEquals("AI", response.getName());
        assertEquals("New description", response.getDescription());
        verify(trackRepository, never()).existsByEventIdAndNormalizedNameAndTrackIdNot(anyInt(), any(), anyInt());
        verify(trackRepository).save(track);
    }

    @Test
    void updateTrack_shouldClearDescription_whenDescriptionIsBlank() {
        HackathonEvent event = event(1, "DRAFT");
        Track track = track(10, event, "Old");
        CreateTrackRequest request = request(" New Name ", "   ");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.findById(10)).thenReturn(Optional.of(track));
        when(trackRepository.existsByEventIdAndNormalizedNameAndTrackIdNot(1, "NEW NAME", 10)).thenReturn(false);
        when(trackRepository.save(track)).thenReturn(track);

        TrackResponse response = trackService.updateTrack(1, 10, request);

        assertEquals("New Name", response.getName());
        assertNull(response.getDescription());
    }

    @Test
    void updateTrack_shouldThrowBadRequest_whenEventIdIsNull() {
        assertThrows(BadRequestException.class, () -> trackService.updateTrack(null, 10, request("AI", null)));

        verify(eventRepository, never()).findById(any());
        verify(trackRepository, never()).findById(anyInt());
    }

    @Test
    void updateTrack_shouldThrowBadRequest_whenTrackIdIsNull() {
        assertThrows(BadRequestException.class, () -> trackService.updateTrack(1, null, request("AI", null)));

        verify(eventRepository, never()).findById(any());
        verify(trackRepository, never()).findById(anyInt());
    }

    @Test
    void updateTrack_shouldThrowResourceNotFound_whenEventDoesNotExist() {
        when(eventRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> trackService.updateTrack(1, 10, request("AI", null)));

        verify(trackRepository, never()).findById(anyInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ON-GOING", "COMPLETED", "CANCELLED"})
    void updateTrack_shouldThrowBadRequest_whenEventStatusCannotMutateTracks(String status) {
        HackathonEvent event = event(1, status);

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));

        assertThrows(BadRequestException.class, () -> trackService.updateTrack(1, 10, request("AI", null)));

        verify(trackRepository, never()).findById(anyInt());
        verify(trackRepository, never()).save(any());
    }

    @Test
    void updateTrack_shouldThrowBadRequest_whenEventStatusIsNull() {
        HackathonEvent event = event(1, null);

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));

        assertThrows(BadRequestException.class, () -> trackService.updateTrack(1, 10, request("AI", null)));

        verify(trackRepository, never()).findById(anyInt());
    }

    @Test
    void updateTrack_shouldThrowBadRequest_whenEventStatusIsBlank() {
        HackathonEvent event = event(1, "   ");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));

        assertThrows(BadRequestException.class, () -> trackService.updateTrack(1, 10, request("AI", null)));

        verify(trackRepository, never()).findById(anyInt());
    }

    @Test
    void updateTrack_shouldThrowResourceNotFound_whenTrackDoesNotExist() {
        HackathonEvent event = event(1, "DRAFT");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.findById(10)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> trackService.updateTrack(1, 10, request("AI", null)));

        verify(trackRepository, never()).save(any());
    }

    @Test
    void updateTrack_shouldThrowBadRequest_whenTrackDoesNotBelongToEvent() {
        HackathonEvent requestedEvent = event(1, "DRAFT");
        HackathonEvent otherEvent = event(2, "DRAFT");
        Track track = track(10, otherEvent, "AI");

        when(eventRepository.findById(1)).thenReturn(Optional.of(requestedEvent));
        when(trackRepository.findById(10)).thenReturn(Optional.of(track));

        assertThrows(BadRequestException.class, () -> trackService.updateTrack(1, 10, request("AI", null)));

        verify(trackRepository, never()).save(any());
    }

    @Test
    void updateTrack_shouldThrowBadRequest_whenRequestIsNull() {
        HackathonEvent event = event(1, "DRAFT");
        Track track = track(10, event, "AI");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.findById(10)).thenReturn(Optional.of(track));

        assertThrows(BadRequestException.class, () -> trackService.updateTrack(1, 10, null));

        verify(trackRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void updateTrack_shouldThrowBadRequest_whenNameIsBlank(String name) {
        HackathonEvent event = event(1, "DRAFT");
        Track track = track(10, event, "Old");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.findById(10)).thenReturn(Optional.of(track));

        assertThrows(BadRequestException.class, () -> trackService.updateTrack(1, 10, request(name, null)));

        verify(trackRepository, never()).save(any());
    }

    @Test
    void updateTrack_shouldThrowBadRequest_whenNameExceedsLimit() {
        HackathonEvent event = event(1, "DRAFT");
        Track track = track(10, event, "Old");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.findById(10)).thenReturn(Optional.of(track));

        assertThrows(BadRequestException.class,
                () -> trackService.updateTrack(1, 10, request("A".repeat(256), null)));

        verify(trackRepository, never()).save(any());
    }

    @Test
    void updateTrack_shouldThrowBadRequest_whenDescriptionExceedsLimit() {
        HackathonEvent event = event(1, "DRAFT");
        Track track = track(10, event, "AI");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.findById(10)).thenReturn(Optional.of(track));

        assertThrows(BadRequestException.class,
                () -> trackService.updateTrack(1, 10, request(null, "D".repeat(2001))));

        verify(trackRepository, never()).save(any());
    }

    @Test
    void updateTrack_shouldThrowBadRequest_whenNormalizedNameAlreadyExists() {
        HackathonEvent event = event(1, "DRAFT");
        Track track = track(10, event, "Old");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.findById(10)).thenReturn(Optional.of(track));
        when(trackRepository.existsByEventIdAndNormalizedNameAndTrackIdNot(1, "AI", 10)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> trackService.updateTrack(1, 10, request(" ai ", null)));

        verify(trackRepository, never()).save(any());
    }

    @Test
    void deleteTrack_shouldDeleteTrack_whenEventIsSetUpAndTrackHasNoTeams() {
        HackathonEvent event = event(1, "SET UP");
        Track track = track(10, event, "AI");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.findById(10)).thenReturn(Optional.of(track));
        when(teamEventEntryRepository.findAllByTrack_TrackId(10)).thenReturn(List.of());

        trackService.deleteTrack(1, 10);

        verify(trackRepository).delete(track);
    }

    @Test
    void deleteTrack_shouldAcceptHyphenatedSetUpStatus() {
        HackathonEvent event = event(1, "set-up");
        Track track = track(10, event, "AI");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.findById(10)).thenReturn(Optional.of(track));
        when(teamEventEntryRepository.findAllByTrack_TrackId(10)).thenReturn(List.of());

        trackService.deleteTrack(1, 10);

        verify(trackRepository).delete(track);
    }

    @Test
    void deleteTrack_shouldThrowBadRequest_whenEventIdIsNull() {
        assertThrows(BadRequestException.class, () -> trackService.deleteTrack(null, 10));

        verify(eventRepository, never()).findById(any());
        verify(trackRepository, never()).findById(anyInt());
    }

    @Test
    void deleteTrack_shouldThrowBadRequest_whenTrackIdIsNull() {
        assertThrows(BadRequestException.class, () -> trackService.deleteTrack(1, null));

        verify(eventRepository, never()).findById(any());
        verify(trackRepository, never()).findById(anyInt());
    }

    @Test
    void deleteTrack_shouldThrowResourceNotFound_whenEventDoesNotExist() {
        when(eventRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> trackService.deleteTrack(1, 10));

        verify(trackRepository, never()).findById(anyInt());
        verify(trackRepository, never()).delete(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ON-GOING", "COMPLETED", "CANCELLED"})
    void deleteTrack_shouldThrowBadRequest_whenEventStatusCannotMutateTracks(String status) {
        HackathonEvent event = event(1, status);

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));

        assertThrows(BadRequestException.class, () -> trackService.deleteTrack(1, 10));

        verify(trackRepository, never()).findById(anyInt());
        verify(trackRepository, never()).delete(any());
    }

    @Test
    void deleteTrack_shouldThrowBadRequest_whenEventStatusIsNull() {
        HackathonEvent event = event(1, null);

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));

        assertThrows(BadRequestException.class, () -> trackService.deleteTrack(1, 10));

        verify(trackRepository, never()).findById(anyInt());
    }

    @Test
    void deleteTrack_shouldThrowBadRequest_whenEventStatusIsBlank() {
        HackathonEvent event = event(1, "   ");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));

        assertThrows(BadRequestException.class, () -> trackService.deleteTrack(1, 10));

        verify(trackRepository, never()).findById(anyInt());
    }

    @Test
    void deleteTrack_shouldThrowResourceNotFound_whenTrackDoesNotExist() {
        HackathonEvent event = event(1, "SET UP");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.findById(10)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> trackService.deleteTrack(1, 10));

        verify(teamEventEntryRepository, never()).findAllByTrack_TrackId(anyInt());
        verify(trackRepository, never()).delete(any());
    }

    @Test
    void deleteTrack_shouldThrowBadRequest_whenTrackDoesNotBelongToEvent() {
        HackathonEvent requestedEvent = event(1, "SET UP");
        HackathonEvent otherEvent = event(2, "SET UP");
        Track track = track(10, otherEvent, "AI");

        when(eventRepository.findById(1)).thenReturn(Optional.of(requestedEvent));
        when(trackRepository.findById(10)).thenReturn(Optional.of(track));

        assertThrows(BadRequestException.class, () -> trackService.deleteTrack(1, 10));

        verify(teamEventEntryRepository, never()).findAllByTrack_TrackId(anyInt());
        verify(trackRepository, never()).delete(any());
    }

    @Test
    void deleteTrack_shouldUnassignTeamsThenDelete_whenTrackHasTeams() {
        // New behaviour: deleting a track during SETUP moves its teams back to the
        // unassigned pool (track = null) instead of blocking the delete.
        HackathonEvent event = event(1, "SET UP");
        Track track = track(10, event, "AI");
        TeamEventEntry entryA = teamOnTrack(100, event, track);
        TeamEventEntry entryB = teamOnTrack(101, event, track);

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.findById(10)).thenReturn(Optional.of(track));
        when(teamEventEntryRepository.findAllByTrack_TrackId(10)).thenReturn(List.of(entryA, entryB));

        trackService.deleteTrack(1, 10);

        assertNull(entryA.getTrack());
        assertNull(entryB.getTrack());
        verify(teamEventEntryRepository).saveAll(List.of(entryA, entryB));
        verify(mentorAssignmentRepository).deleteAllByTrackId(10);
        verify(judgeAssignmentRepository).deleteAllByTrackId(10);
        verify(trackRepository).delete(track);
    }

    @Test
    void deleteTrack_shouldRejectDelete_whenTrackHasAnnouncementHistory() {
        HackathonEvent event = event(1, "SETUP");
        Track track = track(10, event, "AI");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.findById(10)).thenReturn(Optional.of(track));
        when(announcementRepository.existsByTrack_TrackId(10)).thenReturn(true);

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> trackService.deleteTrack(1, 10));

        assertEquals("Cannot delete this track because it has announcement history.", error.getMessage());
        verify(teamEventEntryRepository, never()).findAllByTrack_TrackId(anyInt());
        verify(mentorAssignmentRepository, never()).deleteAllByTrackId(anyInt());
        verify(trackRepository, never()).delete(any());
    }

    @Test
    void deleteTrack_shouldRejectDelete_whenTrackHasSupportRequestHistory() {
        HackathonEvent event = event(1, "SETUP");
        Track track = track(10, event, "AI");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.findById(10)).thenReturn(Optional.of(track));
        when(supportRequestRepository.existsByTrack_TrackId(10)).thenReturn(true);

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> trackService.deleteTrack(1, 10));

        assertEquals("Cannot delete this track because it has mentor support request history.", error.getMessage());
        verify(mentorAssignmentRepository, never()).deleteAllByTrackId(anyInt());
        verify(trackRepository, never()).delete(any());
    }

    @Test
    void deleteTrack_shouldRejectDelete_whenLegacyPrizeReferencesTrack() {
        HackathonEvent event = event(1, "SETUP");
        Track track = track(10, event, "AI");

        when(eventRepository.findById(1)).thenReturn(Optional.of(event));
        when(trackRepository.findById(10)).thenReturn(Optional.of(track));
        when(prizeRepository.existsByTrack_TrackId(10)).thenReturn(true);

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> trackService.deleteTrack(1, 10));

        assertEquals("Cannot delete this track because it is referenced by a prize.", error.getMessage());
        verify(mentorAssignmentRepository, never()).deleteAllByTrackId(anyInt());
        verify(trackRepository, never()).delete(any());
    }

    private static CreateTrackRequest request(String name, String description) {
        CreateTrackRequest request = new CreateTrackRequest();
        request.setName(name);
        request.setDescription(description);
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

    private static Track track(Integer trackId, HackathonEvent event, String name) {
        return Track.builder()
                .trackId(trackId)
                .event(event)
                .name(name)
                .description("Track description")
                .build();
    }

    private static TeamEventEntry teamOnTrack(Integer teamId, HackathonEvent event, Track track) {
        Team team = Team.builder().teamId(teamId).name("Team " + teamId).build();
        return TeamEventEntry.builder().id(teamId).team(team).event(event).track(track).status("APPROVED").build();
    }
}
