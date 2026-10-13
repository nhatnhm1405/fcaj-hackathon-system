package com.seal.hackathon.service;

import com.seal.hackathon.dto.response.MyTeamResponse;
import com.seal.hackathon.dto.response.TeamDetailResponse;
import com.seal.hackathon.dto.response.TeamResponse;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.Track;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.HackathonEventRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamRepository;
import com.seal.hackathon.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamTrackAssignmentService {

    private final HackathonEventRepository eventRepository;
    private final TrackRepository trackRepository;
    private final TeamRepository teamRepository;
    private final TeamEventEntryRepository teamEventEntryRepository;
    private final AuditLogService auditLogService;
    private final TeamAccessGuard teamAccessGuard;
    private final TeamResponseMapper teamResponseMapper;
    private final TeamQueryService teamQueryService;

    // ── Coordinator: Random track draw (SETUP phase) ─────────────────

    /**
     * Randomly assigns teams to the event's tracks in a balanced way. Only allowed
     * while the event is in SETUP status (registration closed, tracks not yet locked
     * for competition). Rejected/disqualified teams are excluded.
     *
     * @param includeAssigned when false (default), only teams without a track are
     *                        drawn (teams that self-selected keep their choice);
     *                        when true, every eligible team is re-shuffled.
     * @param actorUserId     coordinator performing the draw (for the audit trail)
     * @param reason          optional justification, recorded on a REDRAW
     */
    @Transactional
    public List<TeamResponse> drawTracks(Integer eventId, boolean includeAssigned,
                                         Integer actorUserId, String reason) {
        HackathonEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        if (!"SETUP".equalsIgnoreCase(event.getStatus())) {
            throw new BadRequestException(
                    "Track draw is only allowed while the event is in SETUP status.");
        }

        List<Track> tracks = trackRepository.findAllByEvent_EventId(eventId);
        if (tracks.isEmpty()) {
            throw new BadRequestException("Cannot draw tracks: the event has no tracks.");
        }

        // Only approved teams are placed; track capacities were frozen on SETUP entry.
        List<TeamEventEntry> approved = teamEventEntryRepository.findAllByEvent_EventIdAndStatus(eventId, "APPROVED");

        Map<Integer, Integer> count = new HashMap<>();
        tracks.forEach(t -> count.put(t.getTrackId(), 0));

        List<TeamEventEntry> toAssign = new ArrayList<>();
        for (TeamEventEntry entry : approved) {
            if (includeAssigned) {
                entry.setTrack(null);
            }
            if (entry.getTrack() == null) {
                toAssign.add(entry);
            } else {
                count.merge(entry.getTrack().getTrackId(), 1, Integer::sum);
            }
        }

        if (toAssign.isEmpty()) {
            throw new BadRequestException("No unassigned teams to draw.");
        }

        // Greedy balance: each team goes to the track with the most free slots,
        // never exceeding capacity. Equivalent to round-robin when starting empty.
        Collections.shuffle(toAssign);
        for (TeamEventEntry entry : toAssign) {
            Track best = null;
            int bestFree = Integer.MIN_VALUE;
            for (Track t : tracks) {
                int cap = t.getCapacity() != null ? t.getCapacity() : Integer.MAX_VALUE;
                int free = cap - count.get(t.getTrackId());
                if (free > bestFree) {
                    bestFree = free;
                    best = t;
                }
            }
            if (best == null || bestFree <= 0) {
                throw new BadRequestException(
                        "Not enough track capacity to assign all teams. Re-check tracks or approvals.");
            }
            entry.setTrack(best);
            count.merge(best.getTrackId(), 1, Integer::sum);
        }
        teamEventEntryRepository.saveAll(toAssign);

        // Audit trail: a REDRAW wipes & reshuffles everyone (fairness-sensitive, so
        // it carries the coordinator's reason); a plain DRAW only fills unassigned.
        String action = includeAssigned ? "REDRAW_TRACKS" : "DRAW_TRACKS";
        auditLogService.record(actorUserId, action, "EVENT", eventId,
                (reason != null && !reason.isBlank()) ? reason.trim() : null,
                Map.of("mode", event.getTrackSelectionMode(),
                        "assigned", toAssign.size(),
                        "include_assigned", includeAssigned));

        return toAssign.stream().map(entry -> teamResponseMapper.mapToTeamResponse(entry.getTeam(), entry)).collect(Collectors.toList());
    }

    /** SELF_SELECT: a team leader picks the team's track during SETUP. */
    @Transactional
    public MyTeamResponse selectTrack(Integer userId, Integer teamId, Integer trackId) {
        Team team = teamAccessGuard.requireLeader(userId, teamId);
        TeamEventEntry entry = teamAccessGuard.requireCurrentEntry(team);
        HackathonEvent event = entry.getEvent();

        if (!"SETUP".equalsIgnoreCase(event.getStatus())) {
            throw new BadRequestException("Track selection is only open during the SETUP phase.");
        }
        if (!"SELF_SELECT".equalsIgnoreCase(event.getTrackSelectionMode())) {
            throw new BadRequestException(
                    "This event assigns tracks by random draw — leaders cannot pick a track.");
        }
        if (!"APPROVED".equalsIgnoreCase(entry.getStatus())) {
            throw new BadRequestException("Only approved teams can select a track.");
        }

        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track not found: " + trackId));
        if (!track.getEvent().getEventId().equals(event.getEventId())) {
            throw new BadRequestException("The selected track does not belong to this event.");
        }

        if (track.getCapacity() != null) {
            long current = teamEventEntryRepository.findAllByTrack_TrackIdAndStatus(trackId, "APPROVED").stream()
                    .filter(e -> !e.getTeam().getTeamId().equals(teamId))
                    .count();
            if (current >= track.getCapacity()) {
                throw new BadRequestException("This track is full. Please choose another track.");
            }
        }

        entry.setTrack(track);
        teamEventEntryRepository.save(entry);
        return teamQueryService.getMyTeam(userId);
    }

    // ── Coordinator: Manually (re)assign a team to a track (SETUP) ────

    /**
     * Coordinator drag-and-drop assignment: places {@code teamId} into {@code trackId},
     * or moves it to the unassigned pool when trackId is null. SETUP-only. Unlike
     * participant self-selection ({@link #selectTrack}), this deliberately does NOT
     * enforce track capacity — the coordinator may knowingly exceed the recommended
     * max while cleaning up tracks (the UI surfaces a soft warning). Only APPROVED
     * teams are placeable.
     */
    @Transactional
    public TeamDetailResponse assignTeamToTrack(Integer actorUserId, Integer teamId, Integer trackId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
        TeamEventEntry entry = teamAccessGuard.requireCurrentEntry(team);
        HackathonEvent event = entry.getEvent();

        if (!"SETUP".equalsIgnoreCase(event.getStatus())) {
            throw new BadRequestException("Teams can only be reassigned to tracks during the SETUP phase.");
        }
        if (!"APPROVED".equalsIgnoreCase(entry.getStatus())) {
            throw new BadRequestException("Only approved teams can be assigned to a track.");
        }

        Track track = null;
        if (trackId != null) {
            track = trackRepository.findById(trackId)
                    .orElseThrow(() -> new ResourceNotFoundException("Track not found: " + trackId));
            if (!track.getEvent().getEventId().equals(event.getEventId())) {
                throw new BadRequestException("The selected track does not belong to this event.");
            }
        }

        entry.setTrack(track);
        teamEventEntryRepository.save(entry);

        auditLogService.record(actorUserId, "ASSIGN_TEAM_TRACK", "TEAM", teamId, null,
                Map.<String, Object>of("trackId", trackId == null ? "UNASSIGNED" : trackId));

        return teamResponseMapper.mapToDetailResponse(team, entry);
    }
}
