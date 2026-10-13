package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.CreateTeamRequest;
import com.seal.hackathon.dto.request.UpdateTeamRequest;
import com.seal.hackathon.dto.response.MyTeamResponse;
import com.seal.hackathon.dto.response.TeamResponse;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.JoinRequest;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.TeamInvite;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.HackathonEventRepository;
import com.seal.hackathon.repository.JoinRequestRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamInviteRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TeamRepository;
import com.seal.hackathon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamMembershipService {

    private final TeamRepository teamRepository;
    private final TeamEventEntryRepository teamEventEntryRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final HackathonEventRepository eventRepository;
    private final UserRepository userRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final TeamInviteRepository teamInviteRepository;
    private final ParticipantHistorySnapshotService participantHistorySnapshotService;
    private final TeamAccessGuard teamAccessGuard;
    private final TeamResponseMapper teamResponseMapper;
    private final TeamQueryService teamQueryService;
    private final NotificationService notificationService;

    // ── Participant: Create team ──────────────────────────────────────

    @Transactional
    public TeamResponse createTeam(Integer userId, CreateTeamRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (!Boolean.TRUE.equals(user.getIsApproved()) || !Boolean.TRUE.equals(user.getIsActive())) {
            throw new BadRequestException("Your account is not approved or is read-only.");
        }

        HackathonEvent event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + request.getEventId()));

        // Registration is gated purely by event status — OPEN means "accepting
        // teams". Dates (registrationStart/End) are informational only.
        if (!"OPEN".equalsIgnoreCase(event.getStatus())) {
            throw new BadRequestException("This event is not currently open for registration.");
        }

        // Teams register WITHOUT a track. Track is assigned later during SETUP —
        // either the leader self-selects (SELF_SELECT) or the coordinator draws
        // (RANDOM) — once the roster is frozen and per-track slots are computed.

        String teamName = request.getName().trim();
        if (teamEventEntryRepository.existsByEventIdAndNormalizedName(request.getEventId(), teamAccessGuard.normalizeName(teamName))) {
            throw new BadRequestException("A team named '" + request.getName() + "' already exists in this event.");
        }

        if (teamMemberRepository.existsByUser_UserIdAndTeam_Event_EventId(userId, request.getEventId())) {
            throw new BadRequestException("You are already registered in a team for this event.");
        }

        Team team = Team.builder()
                .name(teamName)
                .description(request.getDescription())
                .build();
        team = teamRepository.save(team);

        TeamEventEntry entry = TeamEventEntry.builder()
                .team(team)
                .event(event)
                .track(null)
                .status("PENDING")
                .build();
        entry = teamEventEntryRepository.save(entry);

        TeamMember member = TeamMember.builder()
                .team(team)
                .user(user)
                .memberRole("LEADER")
                .build();
        teamMemberRepository.save(member);

        return teamResponseMapper.mapToTeamResponse(team, entry);
    }

    // ── Participant: team management (leader unless noted) ────────────

    /** Leader edits the team name / description. */
    @Transactional
    public MyTeamResponse updateTeam(Integer userId, Integer teamId, UpdateTeamRequest request) {
        Team team = teamAccessGuard.requireLeader(userId, teamId);
        TeamEventEntry entry = teamAccessGuard.requireCurrentEntry(team);
        teamAccessGuard.ensureTeamManageable(entry);
        if (request.getName() != null && !request.getName().isBlank()) {
            String newName = request.getName().trim();
            String normalizedOldName = teamAccessGuard.normalizeName(team.getName());
            String normalizedNewName = teamAccessGuard.normalizeName(newName);
            if (!normalizedNewName.equals(normalizedOldName)
                    && teamEventEntryRepository.existsByEventIdAndNormalizedName(
                            entry.getEvent().getEventId(), normalizedNewName)) {
                throw new BadRequestException("A team named '" + newName + "' already exists in this event.");
            }
            team.setName(newName);
        }
        if (request.getDescription() != null) {
            team.setDescription(request.getDescription().isBlank() ? null : request.getDescription().trim());
        }
        teamRepository.save(team);
        return teamQueryService.getMyTeam(userId);
    }

    /** Leader removes a MEMBER (not themselves, not another leader). */
    @Transactional
    public MyTeamResponse removeMember(Integer leaderUserId, Integer teamId, Integer targetUserId) {
        Team team = teamAccessGuard.requireLeader(leaderUserId, teamId);
        TeamEventEntry entry = teamAccessGuard.requireCurrentEntry(team);
        teamAccessGuard.ensureTeamManageable(entry);
        if (leaderUserId.equals(targetUserId)) {
            throw new BadRequestException("The leader cannot remove themselves. Transfer leadership or leave the team.");
        }
        TeamMember target = teamMemberRepository.findByTeam_TeamId(teamId).stream()
                .filter(m -> m.getUser().getUserId().equals(targetUserId))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("That user is not a member of this team."));
        if ("LEADER".equalsIgnoreCase(target.getMemberRole())) {
            throw new BadRequestException("Cannot remove the team leader.");
        }
        participantHistorySnapshotService.snapshotDeparture(target, "REMOVED_BY_LEADER");
        teamMemberRepository.delete(target);
        notificationService.createNotification(
                targetUserId,
                "Removed from the team",
                "You were removed from team '" + team.getName() + "' in "
                        + entry.getEvent().getName() + " by the team leader.",
                "TEAM_MEMBER_REMOVED"
        );
        return teamQueryService.getMyTeam(leaderUserId);
    }

    /** Leader hands leadership to an existing member and becomes a member. */
    @Transactional
    public MyTeamResponse transferLeadership(Integer leaderUserId, Integer teamId, Integer newLeaderUserId) {
        Team team = teamAccessGuard.requireLeader(leaderUserId, teamId);
        teamAccessGuard.ensureTeamManageable(teamAccessGuard.requireCurrentEntry(team));
        if (leaderUserId.equals(newLeaderUserId)) {
            throw new BadRequestException("You are already the leader.");
        }
        List<TeamMember> members = teamMemberRepository.findByTeam_TeamId(teamId);
        TeamMember me = members.stream().filter(m -> m.getUser().getUserId().equals(leaderUserId)).findFirst()
                .orElseThrow(() -> new BadRequestException("You are not a member of this team."));
        TeamMember target = members.stream().filter(m -> m.getUser().getUserId().equals(newLeaderUserId)).findFirst()
                .orElseThrow(() -> new BadRequestException("The new leader must be a member of this team."));
        me.setMemberRole("MEMBER");
        target.setMemberRole("LEADER");
        teamMemberRepository.save(me);
        teamMemberRepository.save(target);
        return teamQueryService.getMyTeam(leaderUserId);
    }

    /**
     * A member leaves the team. The leader must transfer leadership first unless
     * they are the only member, in which case this season's (empty) participation
     * is disbanded — only the {@link TeamEventEntry}, never the {@code Team}
     * identity, which may have competed in earlier seasons.
     * Does NOT touch the season's {@code isActive} — leaving a team just makes you
     * teamless (you stay eligible to join/create another team this season). That is
     * a deliberately separate, distinct action — see {@link #leaveEvent}.
     */
    @Transactional
    public void leaveTeam(Integer userId, Integer teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
        TeamEventEntry entry = teamAccessGuard.requireCurrentEntry(team);
        teamAccessGuard.ensureTeamManageable(entry);
        List<TeamMember> members = teamMemberRepository.findByTeam_TeamId(teamId);
        TeamMember me = members.stream().filter(m -> m.getUser().getUserId().equals(userId)).findFirst()
                .orElseThrow(() -> new BadRequestException("You are not a member of this team."));

        if ("LEADER".equalsIgnoreCase(me.getMemberRole())) {
            if (members.size() > 1) {
                throw new BadRequestException("Transfer leadership before leaving the team.");
            }
            // Snapshot BEFORE deleting the entry — this is the one path where the
            // TeamEventEntry itself disappears, not just the membership, so this
            // is the only chance to ever record this season for this user.
            participantHistorySnapshotService.snapshotDeparture(me, "LEFT_TEAM");
            teamMemberRepository.delete(me);
            deletePendingTeamRequests(team);
            teamEventEntryRepository.delete(entry);
            return;
        }
        participantHistorySnapshotService.snapshotDeparture(me, "LEFT_TEAM");
        teamMemberRepository.delete(me);
    }

    /**
     * A teamless participant opts out of the current season entirely — the actual
     * counterpart to {@link ParticipationAccessRequestService#approve} (which
     * reactivates), this is the participant deactivating themselves. Only available
     * while the event is OPEN (registration hasn't closed) and only for someone who
     * currently has no team in it — leaving a team ({@link #leaveTeam}) is a
     * separate, prior step. No coordinator approval needed (self-service, mirrors
     * {@link #leaveTeam}); no reason recorded.
     */
    @Transactional
    public void leaveEvent(Integer userId, Integer eventId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        HackathonEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        if (!"OPEN".equalsIgnoreCase(event.getStatus())) {
            throw new BadRequestException("You can only leave this event while it is open for registration.");
        }
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BadRequestException("You are already inactive for this season.");
        }
        if (teamMemberRepository.existsByUser_UserIdAndTeam_Event_EventId(userId, eventId)) {
            throw new BadRequestException("Leave your team before leaving the event.");
        }
        deactivate(user);
    }

    private void deactivate(User user) {
        user.setIsActive(false);
        userRepository.save(user);
    }

    /**
     * Deletes pending JoinRequest/TeamInvite rows referencing this team — required
     * before deleting the team's entry, since neither FK has ON DELETE CASCADE
     * (mirrors LeftoverGroupingService#dissolve). A team has exactly one
     * TeamEventEntry today, so scoping by teamId alone is unambiguous.
     */
    private void deletePendingTeamRequests(Team team) {
        List<JoinRequest> requests = joinRequestRepository.findByTeam_TeamId(team.getTeamId());
        if (!requests.isEmpty()) {
            joinRequestRepository.deleteAll(requests);
        }
        List<TeamInvite> invites = teamInviteRepository.findByTeam_TeamId(team.getTeamId());
        if (!invites.isEmpty()) {
            teamInviteRepository.deleteAll(invites);
        }
    }

}
