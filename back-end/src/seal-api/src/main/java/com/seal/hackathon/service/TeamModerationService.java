package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.RejectTeamRequest;
import com.seal.hackathon.dto.response.TeamDetailResponse;
import com.seal.hackathon.entity.RoundResult;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.RoundResultRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TeamRepository;
import com.seal.hackathon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamModerationService {

    private final TeamRepository teamRepository;
    private final TeamEventEntryRepository teamEventEntryRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final ParticipantHistorySnapshotService participantHistorySnapshotService;
    private final TeamAccessGuard teamAccessGuard;
    private final TeamResponseMapper teamResponseMapper;
    private final RoundResultRepository roundResultRepository;

    // ── Coordinator: Approve team ────────────────────────────────────

    @Transactional
    public TeamDetailResponse approveTeam(Integer teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
        TeamEventEntry entry = teamAccessGuard.requireCurrentEntry(team);
        if (!"PENDING".equalsIgnoreCase(entry.getStatus())) {
            throw new BadRequestException("Only pending teams can be approved.");
        }
        entry.setStatus("APPROVED");
        teamEventEntryRepository.save(entry);
        notifyTeamMembers(
                team,
                "Team approved",
                "Your team '" + team.getName() + "' has been approved for " +
                        entry.getEvent().getName() + ".",
                "TEAM_APPROVED"
        );
        return teamResponseMapper.mapToDetailResponse(team, entry);
    }

    // ── Coordinator: Reject team ─────────────────────────────────────

    @Transactional
    public TeamDetailResponse rejectTeam(Integer teamId, RejectTeamRequest request) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
        TeamEventEntry entry = teamAccessGuard.requireCurrentEntry(team);
        if (!"PENDING".equalsIgnoreCase(entry.getStatus())) {
            throw new BadRequestException("Only pending teams can be rejected.");
        }
        entry.setStatus("REJECTED");
        if (request != null && request.getReason() != null) {
            entry.setDisqualifiedReason(normalizeReason(request.getReason()));
        }
        teamEventEntryRepository.save(entry);
        String reason = entry.getDisqualifiedReason();
        notifyTeamMembers(
                team,
                "Team rejected",
                "Your team '" + team.getName() + "' was rejected." +
                        (reason != null && !reason.isBlank() ? " Reason: " + reason : ""),
                "TEAM_REJECTED"
        );
        return teamResponseMapper.mapToDetailResponse(team, entry);
    }

    // ── Coordinator: Disqualify team ─────────────────────────────────

    @Transactional
    public TeamDetailResponse disqualifyTeam(Integer teamId, RejectTeamRequest request) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
        TeamEventEntry entry = teamAccessGuard.requireCurrentEntry(team);
        if (!"APPROVED".equalsIgnoreCase(entry.getStatus())) {
            throw new BadRequestException("Only approved teams can be disqualified.");
        }
        entry.setStatus("DISQUALIFIED");
        if (request != null && request.getReason() != null) {
            entry.setDisqualifiedReason(normalizeReason(request.getReason()));
        }
        entry.setDisqualifiedAt(LocalDateTime.now());
        teamEventEntryRepository.save(entry);
        // A disqualified team is no longer "active" regardless of the event's phase.
        team.setIsActive(false);
        teamRepository.save(team);
        deleteRoundResultsForEvent(team, entry.getEvent().getEventId());
        String reason = entry.getDisqualifiedReason();
        notifyTeamMembers(
                team,
                "Team disqualified",
                "Your team '" + team.getName() + "' was disqualified." +
                        (reason != null && !reason.isBlank() ? " Reason: " + reason : ""),
                "TEAM_DISQUALIFIED"
        );
        return teamResponseMapper.mapToDetailResponse(team, entry);
    }

    /**
     * Coordinator-driven removal during a live competition (attendance/absence),
     * with a mandatory audited reason — the mirror image of
     * {@link ParticipationAccessRequestService#approve}, which is the only other
     * place a Coordinator sets a student's {@code isActive}. Unlike the self-service
     * {@link TeamMembershipService#leaveTeam}, this only runs while the event is
     * IN_PROGRESS: the removed member may be unreachable, so there is no
     * leadership hand-off to ask for — if they were the LEADER, the remaining
     * member who joined earliest is auto-promoted; if they were the last member
     * left, the team is disqualified (not deleted — unlike the SETUP-phase
     * solo-leave case, submissions/scores may already reference this team, and
     * DISQUALIFIED already excludes it from ranking).
     */
    @Transactional
    public TeamDetailResponse coordinatorRemoveMember(Integer coordinatorId, Integer teamId,
                                                       Integer targetUserId, String reason) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
        TeamEventEntry entry = teamAccessGuard.requireCurrentEntry(team);
        if (!"IN_PROGRESS".equalsIgnoreCase(entry.getEvent().getStatus())) {
            throw new BadRequestException("Coordinator removal is only available while the event is in progress.");
        }
        if (!"APPROVED".equalsIgnoreCase(entry.getStatus())) {
            throw new BadRequestException("Only an approved team's members can be removed this way.");
        }

        List<TeamMember> members = teamMemberRepository.findByTeam_TeamId(teamId);
        TeamMember target = members.stream()
                .filter(m -> m.getUser().getUserId().equals(targetUserId))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("That user is not a member of this team."));
        boolean wasLeader = "LEADER".equalsIgnoreCase(target.getMemberRole());

        participantHistorySnapshotService.snapshotDeparture(target, "REMOVED_BY_COORDINATOR");
        teamMemberRepository.delete(target);
        deactivate(target.getUser());

        List<TeamMember> remaining = members.stream()
                .filter(m -> !m.getId().equals(target.getId()))
                .collect(Collectors.toList());

        Integer promotedLeaderUserId = null;
        if (remaining.isEmpty()) {
            entry.setStatus("DISQUALIFIED");
            entry.setDisqualifiedReason("No remaining members (last member removed by coordinator): " + reason);
            entry.setDisqualifiedAt(LocalDateTime.now());
            teamEventEntryRepository.save(entry);
            // A disqualified team is no longer "active" regardless of the event's phase.
            team.setIsActive(false);
            teamRepository.save(team);
            deleteRoundResultsForEvent(team, entry.getEvent().getEventId());
        } else if (wasLeader) {
            TeamMember newLeader = remaining.stream()
                    .min(Comparator.comparing(TeamMember::getJoinedAt))
                    .orElseThrow();
            newLeader.setMemberRole("LEADER");
            teamMemberRepository.save(newLeader);
            promotedLeaderUserId = newLeader.getUser().getUserId();
            notificationService.createNotification(
                    promotedLeaderUserId,
                    "You are now the team leader",
                    "You are now the leader of team '" + team.getName()
                            + "' — the previous leader was removed from the competition.",
                    "TEAM_LEADER_PROMOTED"
            );
        }

        notificationService.createNotification(
                targetUserId,
                "Removed from the competition",
                "You have been removed from team '" + team.getName() + "' and are no longer part of "
                        + entry.getEvent().getName() + ". Reason: " + reason
                        + ". If this was a mistake, request to rejoin the competition.",
                "TEAM_COORDINATOR_REMOVED"
        );

        auditLogService.record(coordinatorId, "COORDINATOR_REMOVE_MEMBER", "TEAM", teamId, reason,
                Map.of("removedUserId", targetUserId,
                        "resultingStatus", entry.getStatus(),
                        "promotedLeaderUserId", promotedLeaderUserId == null ? "NONE" : promotedLeaderUserId));

        return teamResponseMapper.mapToDetailResponse(team, entry);
    }

    /**
     * A disqualified team drops out of the competition entirely, so any ranking
     * it already earned in this event (published or not) must go with it —
     * otherwise a stale, no-longer-valid rank lingers on the leaderboard until
     * the coordinator happens to recalculate. Other teams' rank_position values
     * are left as-is (gaps close on the next CALCULATE RANKINGS run).
     */
    private void deleteRoundResultsForEvent(Team team, Integer eventId) {
        List<RoundResult> staleResults =
                roundResultRepository.findAllByTeam_TeamIdAndRound_Event_EventId(team.getTeamId(), eventId);
        if (!staleResults.isEmpty()) {
            roundResultRepository.deleteAll(staleResults);
        }
    }

    private void deactivate(User user) {
        user.setIsActive(false);
        userRepository.save(user);
    }

    private String normalizeReason(String reason) {
        String trimmed = reason.trim();
        return trimmed.isBlank() ? null : trimmed;
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
}
