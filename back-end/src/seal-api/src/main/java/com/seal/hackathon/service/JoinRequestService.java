package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.CreateJoinRequestRequest;
import com.seal.hackathon.dto.response.JoinRequestResponse;
import com.seal.hackathon.dto.response.JoinableTeamListResponse;
import com.seal.hackathon.dto.response.JoinableTeamResponse;
import com.seal.hackathon.entity.JoinRequest;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ForbiddenException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.JoinRequestRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TeamRepository;
import com.seal.hackathon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JoinRequestService {

    private static final int MAX_TEAM_MEMBERS = 5;
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ACCEPTED = "ACCEPTED";
    private static final String STATUS_DECLINED = "DECLINED";

    private final JoinRequestRepository joinRequestRepository;
    private final TeamRepository teamRepository;
    private final TeamEventEntryRepository teamEventEntryRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final TeamAccessGuard teamAccessGuard;

    @Transactional
    public JoinRequestResponse createJoinRequest(Integer requesterUserId, Integer teamId,
                                                 CreateJoinRequestRequest request) {
        User requester = userRepository.findById(requesterUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + requesterUserId));
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
        TeamEventEntry entry = teamAccessGuard.requireCurrentEntry(team);

        validateRequesterCanAskToJoin(requester, entry);

        Optional<JoinRequest> existing = joinRequestRepository
                .findByTeam_TeamIdAndRequester_UserId(teamId, requesterUserId);

        JoinRequest joinRequest;
        if (existing.isPresent()) {
            joinRequest = existing.get();
            if (STATUS_PENDING.equalsIgnoreCase(joinRequest.getStatus())) {
                throw new BadRequestException("You already have a pending request for this team.");
            }
            if (STATUS_ACCEPTED.equalsIgnoreCase(joinRequest.getStatus())) {
                throw new BadRequestException("This join request has already been accepted.");
            }
            joinRequest.setEventId(entry.getEvent().getEventId());
            joinRequest.setStatus(STATUS_PENDING);
            joinRequest.setMessage(normalizeMessage(request));
            joinRequest.setCreatedAt(LocalDateTime.now());
            joinRequest.setRespondedAt(null);
        } else {
            joinRequest = JoinRequest.builder()
                    .team(team)
                    .eventId(entry.getEvent().getEventId())
                    .requester(requester)
                    .message(normalizeMessage(request))
                    .status(STATUS_PENDING)
                    .build();
        }

        joinRequest = joinRequestRepository.save(joinRequest);
        notifyLeaderAboutJoinRequest(joinRequest);
        return mapToResponse(joinRequest);
    }

    @Transactional(readOnly = true)
    public JoinableTeamListResponse getJoinableTeams(Integer requesterUserId, Integer eventId, String query) {
        userRepository.findById(requesterUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + requesterUserId));

        List<TeamEventEntry> sourceEntries;
        if (eventId != null) {
            sourceEntries = teamEventEntryRepository.findAllByEvent_EventIdAndStatus(eventId, "APPROVED").stream()
                    .filter(entry -> "OPEN".equalsIgnoreCase(entry.getEvent().getStatus()))
                    .collect(Collectors.toList());
        } else {
            sourceEntries = teamEventEntryRepository.findAllByStatus("APPROVED").stream()
                    .filter(entry -> "OPEN".equalsIgnoreCase(entry.getEvent().getStatus()))
                    .collect(Collectors.toList());
        }

        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        List<JoinableTeamResponse> teams = sourceEntries.stream()
                .filter(entry -> teamMemberRepository.countByTeam_TeamId(entry.getTeam().getTeamId()) < MAX_TEAM_MEMBERS)
                .filter(entry -> !teamMemberRepository.existsByUser_UserIdAndTeam_Event_EventId(
                        requesterUserId, entry.getEvent().getEventId()))
                .filter(entry -> matchesQuery(entry, normalizedQuery))
                .map(entry -> mapToJoinableTeamResponse(entry, requesterUserId))
                .collect(Collectors.toList());

        return JoinableTeamListResponse.builder()
                .totalJoinableTeams(teams.size())
                .teams(teams)
                .build();
    }

    @Transactional(readOnly = true)
    public List<JoinRequestResponse> getMyRequests(Integer requesterUserId) {
        return joinRequestRepository.findByRequester_UserIdOrderByCreatedAtDesc(requesterUserId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void withdrawRequest(Integer requesterUserId, Integer requestId) {
        JoinRequest joinRequest = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Join request not found: " + requestId));
        if (!joinRequest.getRequester().getUserId().equals(requesterUserId)) {
            throw new ForbiddenException("This join request does not belong to you.");
        }
        if (!STATUS_PENDING.equalsIgnoreCase(joinRequest.getStatus())) {
            throw new BadRequestException("Only pending join requests can be withdrawn.");
        }
        joinRequestRepository.delete(joinRequest);
    }

    @Transactional(readOnly = true)
    public List<JoinRequestResponse> getPendingRequestsForTeam(Integer leaderUserId, Integer teamId) {
        requireLeader(leaderUserId, teamId);
        return joinRequestRepository.findByTeam_TeamIdAndStatusOrderByCreatedAtDesc(teamId, STATUS_PENDING).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public JoinRequestResponse acceptRequest(Integer leaderUserId, Integer requestId) {
        JoinRequest joinRequest = requirePendingRequest(requestId);
        Team team = requireLeader(leaderUserId, joinRequest.getTeam().getTeamId());
        TeamEventEntry entry = teamEventEntryRepository
                .findByTeam_TeamIdAndEvent_EventId(team.getTeamId(), joinRequest.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("This join request's season no longer exists."));

        validateTeamCanAcceptRequest(entry);
        if (teamMemberRepository.existsByUser_UserIdAndTeam_Event_EventId(
                joinRequest.getRequester().getUserId(), entry.getEvent().getEventId())) {
            throw new BadRequestException("This user is already a member of a team in this event.");
        }

        LocalDateTime now = LocalDateTime.now();
        TeamMember member = TeamMember.builder()
                .team(team)
                .user(joinRequest.getRequester())
                .memberRole("MEMBER")
                .build();
        teamMemberRepository.save(member);

        joinRequest.setStatus(STATUS_ACCEPTED);
        joinRequest.setRespondedAt(now);
        joinRequest = joinRequestRepository.save(joinRequest);

        declineOtherPendingRequestsForRequester(joinRequest, now);
        notifyRequesterAboutDecision(joinRequest, true);

        return mapToResponse(joinRequest);
    }

    @Transactional
    public JoinRequestResponse declineRequest(Integer leaderUserId, Integer requestId) {
        JoinRequest joinRequest = requirePendingRequest(requestId);
        requireLeader(leaderUserId, joinRequest.getTeam().getTeamId());

        joinRequest.setStatus(STATUS_DECLINED);
        joinRequest.setRespondedAt(LocalDateTime.now());
        joinRequest = joinRequestRepository.save(joinRequest);
        notifyRequesterAboutDecision(joinRequest, false);

        return mapToResponse(joinRequest);
    }

    private void validateRequesterCanAskToJoin(User requester, TeamEventEntry entry) {
        if (!Boolean.TRUE.equals(requester.getIsApproved()) || !Boolean.TRUE.equals(requester.getIsActive())) {
            throw new BadRequestException("Your account is not approved or is read-only.");
        }
        validateTeamCanAcceptRequest(entry);
        if (teamMemberRepository.existsByUser_UserIdAndTeam_Event_EventId(
                requester.getUserId(), entry.getEvent().getEventId())) {
            throw new BadRequestException("You are already a member of a team in this event.");
        }
    }

    private void validateTeamCanAcceptRequest(TeamEventEntry entry) {
        if (!"OPEN".equalsIgnoreCase(entry.getEvent().getStatus())) {
            throw new BadRequestException("This event is not open for team registration.");
        }
        // A not-yet-approved (PENDING) team may still build its roster; approval only
        // gates track selection. Rejected/disqualified teams are done, so they cannot
        // take join requests.
        if ("REJECTED".equalsIgnoreCase(entry.getStatus())
                || "DISQUALIFIED".equalsIgnoreCase(entry.getStatus())) {
            throw new BadRequestException("A rejected or disqualified team cannot receive join requests.");
        }
        if (teamMemberRepository.countByTeam_TeamId(entry.getTeam().getTeamId()) >= MAX_TEAM_MEMBERS) {
            throw new BadRequestException("This team is already full (maximum 5 members).");
        }
    }

    private Team requireLeader(Integer userId, Integer teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
        boolean isLeader = teamMemberRepository.findByTeam_TeamId(teamId).stream()
                .anyMatch(m -> m.getUser().getUserId().equals(userId)
                        && "LEADER".equalsIgnoreCase(m.getMemberRole()));
        if (!isLeader) {
            throw new ForbiddenException("Only the team leader can perform this action.");
        }
        return team;
    }

    private JoinRequest requirePendingRequest(Integer requestId) {
        JoinRequest joinRequest = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Join request not found: " + requestId));
        if (!STATUS_PENDING.equalsIgnoreCase(joinRequest.getStatus())) {
            throw new BadRequestException("This join request has already been " +
                    joinRequest.getStatus().toLowerCase(Locale.ROOT) + ".");
        }
        return joinRequest;
    }

    private void declineOtherPendingRequestsForRequester(JoinRequest acceptedRequest, LocalDateTime respondedAt) {
        Integer requesterId = acceptedRequest.getRequester().getUserId();
        Integer eventId = acceptedRequest.getEventId();
        List<JoinRequest> otherPending = joinRequestRepository
                .findByRequester_UserIdAndStatusAndEventId(requesterId, STATUS_PENDING, eventId).stream()
                .filter(request -> !request.getRequestId().equals(acceptedRequest.getRequestId()))
                .collect(Collectors.toList());

        otherPending.forEach(request -> {
            request.setStatus(STATUS_DECLINED);
            request.setRespondedAt(respondedAt);
        });
        joinRequestRepository.saveAll(otherPending);
    }

    private void notifyLeaderAboutJoinRequest(JoinRequest joinRequest) {
        TeamMember leader = teamAccessGuard.findLeader(joinRequest.getTeam());
        notificationService.createNotification(
                leader.getUser().getUserId(),
                "New join request",
                joinRequest.getRequester().getFullName() + " requested to join team '" +
                        joinRequest.getTeam().getName() + "'.",
                "JOIN_REQUEST"
        );
    }

    private void notifyRequesterAboutDecision(JoinRequest joinRequest, boolean accepted) {
        String title = accepted ? "Join request accepted" : "Join request declined";
        String content = accepted
                ? "Your request to join team '" + joinRequest.getTeam().getName() + "' was accepted."
                : "Your request to join team '" + joinRequest.getTeam().getName() + "' was declined.";
        notificationService.createNotification(
                joinRequest.getRequester().getUserId(),
                title,
                content,
                accepted ? "JOIN_REQUEST_ACCEPTED" : "JOIN_REQUEST_DECLINED"
        );
    }

    private boolean matchesQuery(TeamEventEntry entry, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        Team team = entry.getTeam();
        String teamName = team.getName() == null ? "" : team.getName().toLowerCase(Locale.ROOT);
        String trackName = (entry.getTrack() == null || entry.getTrack().getName() == null)
                ? "" : entry.getTrack().getName().toLowerCase(Locale.ROOT);
        return teamName.contains(query) || trackName.contains(query);
    }

    private String normalizeMessage(CreateJoinRequestRequest request) {
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            return null;
        }
        return request.getMessage().trim();
    }

    private JoinableTeamResponse mapToJoinableTeamResponse(TeamEventEntry entry, Integer requesterUserId) {
        Team team = entry.getTeam();
        TeamMember leader = teamAccessGuard.findLeader(team);
        Optional<JoinRequest> myRequest = joinRequestRepository
                .findByTeam_TeamIdAndRequester_UserId(team.getTeamId(), requesterUserId);

        return JoinableTeamResponse.builder()
                .teamId(team.getTeamId())
                .teamName(team.getName())
                .description(team.getDescription())
                .eventId(entry.getEvent().getEventId())
                .eventName(entry.getEvent().getName())
                .trackId(entry.getTrack() != null ? entry.getTrack().getTrackId() : null)
                .trackName(entry.getTrack() != null ? entry.getTrack().getName() : null)
                .teamStatus(entry.getStatus())
                .memberCount((int) teamMemberRepository.countByTeam_TeamId(team.getTeamId()))
                .maxMembers(MAX_TEAM_MEMBERS)
                .leaderUserId(leader.getUser().getUserId())
                .leaderName(leader.getUser().getFullName())
                .myRequestId(myRequest.map(JoinRequest::getRequestId).orElse(null))
                .myRequestStatus(myRequest.map(JoinRequest::getStatus).orElse(null))
                .build();
    }

    private JoinRequestResponse mapToResponse(JoinRequest request) {
        Team team = request.getTeam();
        User requester = request.getRequester();
        TeamEventEntry entry = teamEventEntryRepository
                .findByTeam_TeamIdAndEvent_EventId(team.getTeamId(), request.getEventId())
                .orElse(null);
        return JoinRequestResponse.builder()
                .requestId(request.getRequestId())
                .teamId(team.getTeamId())
                .teamName(team.getName())
                .eventId(entry != null ? entry.getEvent().getEventId() : null)
                .eventName(entry != null ? entry.getEvent().getName() : null)
                .trackId(entry != null && entry.getTrack() != null ? entry.getTrack().getTrackId() : null)
                .trackName(entry != null && entry.getTrack() != null ? entry.getTrack().getName() : null)
                .teamStatus(entry != null ? entry.getStatus() : null)
                .requesterUserId(requester.getUserId())
                .requesterName(requester.getFullName())
                .requesterEmail(requester.getEmail())
                .message(request.getMessage())
                .status(request.getStatus())
                .createdAt(request.getCreatedAt())
                .respondedAt(request.getRespondedAt())
                .build();
    }
}
