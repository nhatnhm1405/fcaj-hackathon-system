package com.seal.hackathon.service;

import com.seal.hackathon.dto.response.TeamRejoinRequestResponse;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.entity.TeamRejoinRequest;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.HackathonEventRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TeamRejoinRequestRepository;
import com.seal.hackathon.repository.TeamRepository;
import com.seal.hackathon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A team leader's request to re-attach their existing (persistent) Team
 * identity to a new season, once the team has gone inactive (its last
 * season completed, or it was disqualified). Mirrors
 * {@link ParticipationAccessRequestService}'s shape, scoped to a team
 * instead of a user — deliberately does NOT touch any member's
 * {@code User.isActive}; that stays owned by
 * {@link ParticipationAccessRequestService}, so a returning leader must
 * reactivate their own account first (existing flow), then request the
 * team's rejoin separately.
 */
@Service
@RequiredArgsConstructor
public class TeamRejoinRequestService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";

    private final TeamRejoinRequestRepository requestRepository;
    private final TeamRepository teamRepository;
    private final TeamEventEntryRepository teamEventEntryRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final HackathonEventRepository eventRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    @Transactional
    public TeamRejoinRequestResponse requestRejoin(Integer leaderUserId, Integer teamId, Integer eventId) {
        Team team = requireLeader(leaderUserId, teamId);
        HackathonEvent event = requireEvent(eventId);

        if (Boolean.TRUE.equals(team.getIsActive())) {
            throw new BadRequestException("This team is already active in a season.");
        }
        if (!"OPEN".equalsIgnoreCase(event.getStatus())) {
            throw new BadRequestException("This event is not currently open for registration.");
        }
        if (teamEventEntryRepository.existsByTeam_TeamIdAndEvent_EventId(teamId, eventId)) {
            throw new BadRequestException("This team already has a season entry for this event.");
        }

        Optional<TeamRejoinRequest> existing = requestRepository.findByTeam_TeamIdAndStatus(teamId, STATUS_PENDING);
        TeamRejoinRequest request = existing.orElseGet(() -> requestRepository.save(
                TeamRejoinRequest.builder()
                        .team(team)
                        .eventId(eventId)
                        .requestedBy(userRepository.findById(leaderUserId)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + leaderUserId)))
                        .status(STATUS_PENDING)
                        .build()));

        return mapToResponse(request, event);
    }

    @Transactional(readOnly = true)
    public List<TeamRejoinRequestResponse> listPending() {
        return requestRepository.findByStatusOrderByRequestedAtDesc(STATUS_PENDING).stream()
                .map(r -> mapToResponse(r, requireEvent(r.getEventId())))
                .collect(Collectors.toList());
    }

    @Transactional
    public TeamRejoinRequestResponse approve(Integer requestId, Integer coordinatorId) {
        TeamRejoinRequest request = requirePending(requestId);
        Team team = request.getTeam();
        HackathonEvent event = requireEvent(request.getEventId());

        TeamEventEntry entry = teamEventEntryRepository.save(TeamEventEntry.builder()
                .team(team)
                .event(event)
                .status("APPROVED")
                .build());
        team.setIsActive(true);
        teamRepository.save(team);

        resolve(request, STATUS_APPROVED, coordinatorId);

        notifyTeamMembers(team,
                "Your team is back in the game",
                "Your team '" + team.getName() + "' has rejoined " + event.getName() + ".",
                "TEAM_REJOIN_APPROVED");

        auditLogService.record(coordinatorId, "APPROVE_TEAM_REJOIN", "TEAM", team.getTeamId(), null,
                Map.of("eventId", event.getEventId(), "requestId", requestId, "newEntryId", entry.getId()));

        return mapToResponse(request, event);
    }

    @Transactional
    public TeamRejoinRequestResponse reject(Integer requestId, Integer coordinatorId) {
        TeamRejoinRequest request = requirePending(requestId);
        HackathonEvent event = requireEvent(request.getEventId());
        Team team = request.getTeam();

        resolve(request, STATUS_REJECTED, coordinatorId);

        notificationService.createNotification(
                request.getRequestedBy().getUserId(),
                "Rejoin request declined",
                "Your request to rejoin " + event.getName() + " with team '" + team.getName()
                        + "' was not approved.",
                "TEAM_REJOIN_REJECTED"
        );

        auditLogService.record(coordinatorId, "REJECT_TEAM_REJOIN", "TEAM", team.getTeamId(), null,
                Map.of("eventId", event.getEventId(), "requestId", requestId));

        return mapToResponse(request, event);
    }

    /** Loads the team and asserts the given user is its LEADER (mirrors TeamAccessGuard#requireLeader). */
    private Team requireLeader(Integer userId, Integer teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
        TeamMember me = teamMemberRepository.findByTeam_TeamId(teamId).stream()
                .filter(m -> m.getUser().getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("You are not a member of this team."));
        if (!"LEADER".equalsIgnoreCase(me.getMemberRole())) {
            throw new BadRequestException("Only the team leader can request to rejoin.");
        }
        return team;
    }

    private HackathonEvent requireEvent(Integer eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
    }

    private TeamRejoinRequest requirePending(Integer requestId) {
        TeamRejoinRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Rejoin request not found: " + requestId));
        if (!STATUS_PENDING.equalsIgnoreCase(request.getStatus())) {
            throw new BadRequestException("This request has already been resolved.");
        }
        return request;
    }

    private void resolve(TeamRejoinRequest request, String status, Integer coordinatorId) {
        request.setStatus(status);
        request.setResolvedAt(LocalDateTime.now());
        request.setResolvedBy(coordinatorId);
        requestRepository.save(request);
    }

    private void notifyTeamMembers(Team team, String title, String content, String type) {
        teamMemberRepository.findByTeam_TeamId(team.getTeamId())
                .forEach(member -> notificationService.createNotification(
                        member.getUser().getUserId(),
                        title,
                        content,
                        type
                ));
    }

    private TeamRejoinRequestResponse mapToResponse(TeamRejoinRequest request, HackathonEvent event) {
        return TeamRejoinRequestResponse.builder()
                .requestId(request.getRequestId())
                .teamId(request.getTeam().getTeamId())
                .teamName(request.getTeam().getName())
                .eventId(event.getEventId())
                .eventName(event.getName())
                .requestedByUserId(request.getRequestedBy().getUserId())
                .requestedByName(request.getRequestedBy().getFullName())
                .status(request.getStatus())
                .requestedAt(request.getRequestedAt())
                .resolvedAt(request.getResolvedAt())
                .resolvedBy(request.getResolvedBy())
                .build();
    }
}
