package com.seal.hackathon.service;

import com.seal.hackathon.dto.response.ActiveEventResponse;
import com.seal.hackathon.dto.response.MyTeamResponse;
import com.seal.hackathon.dto.response.TeamDetailResponse;
import com.seal.hackathon.dto.response.TeamHistoryResponse;
import com.seal.hackathon.dto.response.TrackResponse;
import com.seal.hackathon.dto.response.UserResponse;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.entity.Track;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.HackathonEventRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TeamRepository;
import com.seal.hackathon.repository.TrackRepository;
import com.seal.hackathon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamQueryService {

    private final TeamRepository teamRepository;
    private final TeamEventEntryRepository teamEventEntryRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final HackathonEventRepository eventRepository;
    private final TrackRepository trackRepository;
    private final UserRepository userRepository;
    private final ParticipantHistorySnapshotService participantHistorySnapshotService;
    private final TeamAccessGuard teamAccessGuard;
    private final TeamResponseMapper teamResponseMapper;

    // ── Participant: live team-name availability check ────────────────
    // Mirrors the register-page student-id check: lets the create-team form warn
    // about a duplicate before submit. Uses the same normalized (case/space
    // insensitive) match the create path enforces, so the two never disagree.
    @Transactional(readOnly = true)
    public boolean teamNameExists(Integer eventId, String name) {
        if (eventId == null || name == null || name.isBlank()) return false;
        return teamEventEntryRepository.existsByEventIdAndNormalizedName(eventId, teamAccessGuard.normalizeName(name));
    }

    // ── Participant: Get my team ──────────────────────────────────────

    /**
     * Tier 1: any membership with a live-season entry (OPEN/SETUP/IN_PROGRESS) —
     * the common case, unchanged. Tier 2 (fallback, only when tier 1 is empty):
     * the user's most recent team membership regardless of season, resolved via
     * its most recent entry (which may belong to a COMPLETED event) — so a
     * dormant team's identity/roster still surfaces read-only instead of 404ing,
     * letting a leader find their way to a rejoin request instead of being
     * routed into "create a new team" and silently orphaning their old roster row.
     */
    @Transactional(readOnly = true)
    public MyTeamResponse getMyTeam(Integer userId) {
        List<String> currentStatuses = List.of("OPEN", "SETUP", "IN_PROGRESS");
        List<TeamMember> myMemberships = teamMemberRepository
                .findByUser_UserIdAndTeam_Event_StatusIn(userId, currentStatuses);

        if (!myMemberships.isEmpty()) {
            TeamMember membership = myMemberships.stream()
                    .max(Comparator.comparing(
                            TeamMember::getId,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElseThrow(() -> new ResourceNotFoundException("You are not currently a member of any team."));
            return teamResponseMapper.mapToMyTeamResponse(membership);
        }

        return teamMemberRepository.findByUser_UserIdOrderByIdDesc(userId).stream()
                .findFirst()
                .map(teamResponseMapper::mapToMyTeamResponse)
                .orElseThrow(() -> new ResourceNotFoundException("You are not currently a member of any team."));
    }

    @Transactional(readOnly = true)
    public List<MyTeamResponse> getMyTeamHistory(Integer userId) {
        return teamMemberRepository.findByUser_UserIdOrderByIdDesc(userId).stream()
                .map(teamResponseMapper::mapToMyTeamResponse)
                .collect(Collectors.toList());
    }

    /**
     * Merges two sources per event id: entries still live on a team (computed
     * fresh, one row per {@link TeamEventEntry} the member actually competed
     * during — see {@link #computeLiveHistory}), and frozen snapshots taken at
     * departure or event completion (see {@link ParticipantHistorySnapshotService}).
     * Live wins whenever the event isn't currently COMPLETED (covers a
     * temporarily reopened event so it doesn't show a stale snapshot); a
     * snapshot wins once the event is COMPLETED (stable, immune to a
     * departed member's TeamMember row having since been deleted); a live row
     * is the fallback for a COMPLETED event that predates this feature and
     * has no snapshot yet (until the admin backfill runs).
     */
    @Transactional(readOnly = true)
    public List<TeamHistoryResponse> getMyResultHistory(Integer userId) {
        Map<Integer, TeamHistoryResponse> liveByEvent = new LinkedHashMap<>();
        for (TeamHistoryResponse row : computeLiveHistory(userId)) {
            liveByEvent.put(row.getEventId(), row);
        }
        Map<Integer, TeamHistoryResponse> snapshotByEvent = participantHistorySnapshotService.getSnapshotsForUser(userId);

        Set<Integer> allEventIds = new LinkedHashSet<>(liveByEvent.keySet());
        allEventIds.addAll(snapshotByEvent.keySet());

        List<TeamHistoryResponse> merged = new ArrayList<>();
        for (Integer eventId : allEventIds) {
            TeamHistoryResponse live = liveByEvent.get(eventId);
            if (live != null && !"COMPLETED".equalsIgnoreCase(live.getEventStatus())) {
                merged.add(live);
            } else if (snapshotByEvent.containsKey(eventId)) {
                merged.add(snapshotByEvent.get(eventId));
            } else if (live != null) {
                merged.add(live);
            }
        }
        merged.sort(Comparator.comparing(TeamHistoryResponse::getEventId, Comparator.nullsLast(Comparator.reverseOrder())));
        return merged;
    }

    // One row per TeamEventEntry the member actually competed during, not one
    // row per team — a rejoined team has multiple entries, and each season's
    // result data must stay on its own row. Entry resolution/filtering lives
    // in ParticipantHistorySnapshotService, shared with snapshotDeparture so
    // a departure snapshot always covers the exact same seasons this live
    // path would have shown a moment earlier.
    private List<TeamHistoryResponse> computeLiveHistory(Integer userId) {
        List<TeamMember> memberships = teamMemberRepository.findByUser_UserIdOrderByIdDesc(userId);
        List<TeamHistoryResponse> history = new ArrayList<>();

        for (TeamMember membership : memberships) {
            for (TeamEventEntry entry : participantHistorySnapshotService.resolveEntriesForMembership(membership)) {
                history.add(participantHistorySnapshotService.buildHistoryView(membership, entry));
            }
        }
        return history;
    }

    @Transactional(readOnly = true)
    public MyTeamResponse getMyTeamByEvent(Integer userId, Integer eventId) {
        return teamMemberRepository.findByUser_UserIdOrderByIdDesc(userId).stream()
                .flatMap(m -> teamEventEntryRepository
                        .findByTeam_TeamIdAndEvent_EventId(m.getTeam().getTeamId(), eventId)
                        .stream()
                        .map(entry -> teamResponseMapper.mapToMyTeamResponse(m, entry)))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("You are not part of any team in this event."));
    }

    // ── Coordinator: Get all teams by event ──────────────────────────

    @Transactional(readOnly = true)
    public List<TeamDetailResponse> getTeamsByEvent(Integer eventId) {
        eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        return teamEventEntryRepository.findAllByEvent_EventId(eventId).stream()
                .map(entry -> teamResponseMapper.mapToDetailResponse(entry.getTeam(), entry))
                .collect(Collectors.toList());
    }

    // Cross-event count backing the Coordinator sidebar's "Teams" badge — mirrors
    // the account-approval badge so both queues surface the same way.
    @Transactional(readOnly = true)
    public long getPendingTeamsCount() {
        return teamEventEntryRepository.countByStatus("PENDING");
    }

    // ── Coordinator: Get single team ─────────────────────────────────

    @Transactional(readOnly = true)
    public TeamDetailResponse getTeamById(Integer teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
        return teamResponseMapper.mapToDetailResponse(team, teamAccessGuard.requireCurrentEntry(team));
    }

    /** Search active student accounts a participant may invite. */
    @Transactional(readOnly = true)
    public List<UserResponse> searchInvitableUsers(String query) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        return userRepository.searchInvitableStudents(query.trim().toLowerCase()).stream()
                .limit(10)
                .map(u -> UserResponse.builder()
                        .userId(u.getUserId())
                        .fullName(u.getFullName())
                        .email(u.getEmail())
                        .studentId(u.getStudentId())
                        .university(u.getUniversity())
                        .userType(u.getUserType())
                        .build())
                .collect(Collectors.toList());
    }

    // ── Participant: Get active events with tracks ────────────────────

    @Transactional(readOnly = true)
    public List<ActiveEventResponse> getActiveEventsWithTracks() {
        // OPEN status is the single source of truth for "registration open" — the
        // list of joinable events matches exactly what createTeam will accept.
        List<HackathonEvent> activeEvents = eventRepository.findAllByStatus("OPEN");
        return activeEvents.stream()
                .map(event -> {
                    List<Track> tracks = trackRepository.findAllByEvent_EventId(event.getEventId());
                    List<TrackResponse> trackResponses = tracks.stream()
                            .map(t -> TrackResponse.builder()
                                    .trackId(t.getTrackId())
                                    .eventId(t.getEvent().getEventId())
                                    .name(t.getName())
                                    .description(t.getDescription())
                                    .capacity(t.getCapacity())
                                    .build())
                            .collect(Collectors.toList());
                    return ActiveEventResponse.builder()
                            .eventId(event.getEventId())
                            .name(event.getName())
                            .season(event.getSeason())
                            .year(event.getYear())
                            .description(event.getDescription())
                            .topic(event.getTopic())
                            .registrationStart(event.getRegistrationStart())
                            .registrationEnd(event.getRegistrationEnd())
                            .startDate(event.getStartDate())
                            .endDate(event.getEndDate())
                            .status(event.getStatus())
                            .tracks(trackResponses)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
