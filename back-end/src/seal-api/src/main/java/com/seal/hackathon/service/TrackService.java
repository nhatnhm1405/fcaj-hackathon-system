package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.CreateTrackRequest;
import com.seal.hackathon.dto.response.TrackResponse;
import com.seal.hackathon.entity.HackathonEvent;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrackService {

    // Editing/removing tracks is allowed while building the event (DRAFT), while
    // registration is open (OPEN), or during the SETUP phase before competition
    // starts (a track can still be renamed or an empty one removed during SETUP).
    private static final Set<String> TRACK_MUTATION_ALLOWED_EVENT_STATUSES = Set.of("DRAFT", "OPEN", "SETUP");

    // CREATING a track is intentionally stricter: DRAFT or OPEN only. Once
    // registration closes (SETUP), the track set is locked so the per-track slot
    // counts computed on SETUP entry (HackathonEventService.computeTrackCapacities)
    // stay consistent — a track added mid-SETUP would carry no computed capacity
    // and the random draw would treat it as unlimited.
    private static final Set<String> TRACK_CREATE_ALLOWED_EVENT_STATUSES = Set.of("DRAFT", "OPEN");
    private static final int MAX_TRACK_NAME_LENGTH = 255;
    private static final int MAX_TRACK_DESCRIPTION_LENGTH = 2000;

    private final TrackRepository trackRepository;
    private final HackathonEventRepository eventRepository;
    private final TeamEventEntryRepository teamEventEntryRepository;
    private final MentorAssignmentRepository mentorAssignmentRepository;
    private final JudgeAssignmentRepository judgeAssignmentRepository;
    private final AnnouncementRepository announcementRepository;
    private final MentorSupportRequestRepository supportRequestRepository;
    private final PrizeRepository prizeRepository;

    @Transactional(readOnly = true)
    public List<TrackResponse> getTracksByEvent(Integer eventId) {
        requireId(eventId, "Event ID");
        requireEvent(eventId);
        return trackRepository.findAllByEvent_EventId(eventId).stream()
                .sorted(Comparator.comparing(Track::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TrackResponse getTrackById(Integer eventId, Integer trackId) {
        requireId(eventId, "Event ID");
        requireId(trackId, "Track ID");
        requireEvent(eventId);
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track not found: " + trackId));
        if (!track.getEvent().getEventId().equals(eventId)) {
            throw new BadRequestException("Track does not belong to event " + eventId);
        }
        return mapToResponse(track);
    }

    @Transactional
    public TrackResponse createTrack(Integer eventId, CreateTrackRequest request) {
        requireId(eventId, "Event ID");
        HackathonEvent event = requireEvent(eventId);

        requireEventAllowsTrackCreation(event);

        String trackName = requiredTrackName(request);
        String description = normalizeDescription(request.getDescription());

        boolean duplicate = trackRepository.existsByEventIdAndNormalizedName(eventId, normalizeName(trackName));
        if (duplicate) {
            throw new BadRequestException("Track '" + trackName + "' already exists in this event.");
        }

        Track track = Track.builder()
                .event(event)
                .name(trackName)
                .description(description)
                .build();
        track = trackRepository.save(track);
        return mapToResponse(track);
    }

    @Transactional
    public TrackResponse updateTrack(Integer eventId, Integer trackId, CreateTrackRequest request) {
        requireId(eventId, "Event ID");
        requireId(trackId, "Track ID");
        HackathonEvent event = requireEvent(eventId);
        requireEventAllowsTrackMutation(event, "update tracks");

        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track not found: " + trackId));
        if (!track.getEvent().getEventId().equals(eventId)) {
            throw new BadRequestException("Track does not belong to event " + eventId);
        }
        if (request == null) {
            throw new BadRequestException("Track update request is required.");
        }
        if (request.getName() != null) {
            String trackName = requiredTrackName(request);
            boolean duplicate = trackRepository.existsByEventIdAndNormalizedNameAndTrackIdNot(
                    eventId,
                    normalizeName(trackName),
                    trackId
            );
            if (duplicate) {
                throw new BadRequestException("Track '" + trackName + "' already exists in this event.");
            }
            track.setName(trackName);
        }
        if (request.getDescription() != null) {
            track.setDescription(normalizeDescription(request.getDescription()));
        }
        track = trackRepository.save(track);
        return mapToResponse(track);
    }

    @Transactional
    public void deleteTrack(Integer eventId, Integer trackId) {
        requireId(eventId, "Event ID");
        requireId(trackId, "Track ID");
        HackathonEvent event = requireEvent(eventId);
        requireEventAllowsTrackMutation(event, "delete tracks");

        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track not found: " + trackId));
        if (!track.getEvent().getEventId().equals(eventId)) {
            throw new BadRequestException("Track does not belong to event " + eventId);
        }

        // Announcements and support requests are historical records. Deleting them
        // implicitly would erase an audit trail; keep the track and explain the
        // business conflict instead of leaking a database constraint exception.
        if (announcementRepository.existsByTrack_TrackId(trackId)) {
            throw new BadRequestException(
                    "Cannot delete this track because it has announcement history.");
        }
        if (supportRequestRepository.existsByTrack_TrackId(trackId)) {
            throw new BadRequestException(
                    "Cannot delete this track because it has mentor support request history.");
        }
        // Track-scoped prizes are legacy/inconsistent data: turning one into an
        // event-wide prize by setting track = null would silently change its scope.
        if (prizeRepository.existsByTrack_TrackId(trackId)) {
            throw new BadRequestException(
                    "Cannot delete this track because it is referenced by a prize.");
        }

        // Removing a track moves its teams back to the unassigned pool (track = null)
        // rather than blocking the delete; the coordinator re-assigns them by hand.
        // In DRAFT/OPEN no team has a track yet, so this is a no-op there; during SETUP
        // it powers the manual track cleanup. We never auto-distribute to other tracks.
        List<TeamEventEntry> entriesOnTrack = teamEventEntryRepository.findAllByTrack_TrackId(trackId);
        if (!entriesOnTrack.isEmpty()) {
            entriesOnTrack.forEach(e -> e.setTrack(null));
            teamEventEntryRepository.saveAll(entriesOnTrack);
        }
        // Assignments are setup configuration, not the source of audit history
        // (assignment actions are already recorded in AuditLog). Bulk-delete every
        // active/inactive row first so the database FK can never outlive the track.
        mentorAssignmentRepository.deleteAllByTrackId(trackId);
        judgeAssignmentRepository.deleteAllByTrackId(trackId);
        trackRepository.delete(track);
    }

    private HackathonEvent requireEvent(Integer eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
    }

    private TrackResponse mapToResponse(Track track) {
        return TrackResponse.builder()
                .trackId(track.getTrackId())
                .eventId(track.getEvent().getEventId())
                .name(track.getName())
                .description(track.getDescription())
                .capacity(track.getCapacity())
                .teamCount(teamEventEntryRepository.findAllByTrack_TrackIdAndStatus(track.getTrackId(), "APPROVED").size())
                .build();
    }

    private void requireEventAllowsTrackMutation(HackathonEvent event, String action) {
        String status = normalizeEventStatus(event.getStatus());
        if (!TRACK_MUTATION_ALLOWED_EVENT_STATUSES.contains(status)) {
            throw new BadRequestException("Cannot " + action + " when event status is " + status
                    + ". Event status must be DRAFT, OPEN or SETUP.");
        }
    }

    private void requireEventAllowsTrackCreation(HackathonEvent event) {
        String status = normalizeEventStatus(event.getStatus());
        if (!TRACK_CREATE_ALLOWED_EVENT_STATUSES.contains(status)) {
            throw new BadRequestException("Cannot create tracks when event status is " + status
                    + ". Tracks can only be created while the event is DRAFT or OPEN"
                    + " — track creation is locked once the event enters SETUP.");
        }
    }

    private String normalizeEventStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new BadRequestException("Event status is required.");
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        return "SET_UP".equals(normalized) ? "SETUP" : normalized;
    }

    private String requiredTrackName(CreateTrackRequest request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new BadRequestException("Track name is required.");
        }
        String trackName = request.getName().trim();
        if (trackName.length() > MAX_TRACK_NAME_LENGTH) {
            throw new BadRequestException("Track name must not exceed " + MAX_TRACK_NAME_LENGTH + " characters.");
        }
        return trackName;
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        if (trimmed.length() > MAX_TRACK_DESCRIPTION_LENGTH) {
            throw new BadRequestException("Track description must not exceed "
                    + MAX_TRACK_DESCRIPTION_LENGTH + " characters.");
        }
        return trimmed;
    }

    private String normalizeName(String name) {
        return name.trim().toUpperCase(Locale.ROOT);
    }

    private void requireId(Integer id, String label) {
        if (id == null) {
            throw new BadRequestException(label + " is required.");
        }
    }
}
