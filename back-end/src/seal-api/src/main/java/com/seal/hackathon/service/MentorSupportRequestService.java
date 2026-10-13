package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.CreateSupportRequestRequest;
import com.seal.hackathon.dto.response.MentorContactResponse;
import com.seal.hackathon.dto.response.SupportRequestResponse;
import com.seal.hackathon.entity.MentorAssignment;
import com.seal.hackathon.entity.MentorSupportRequest;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.entity.Track;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ForbiddenException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.MentorAssignmentRepository;
import com.seal.hackathon.repository.MentorSupportRequestRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mentor-support requests: a team leader asks its track mentor for help; the mentor
 * is notified, helps in person, and marks it resolved. See {@link MentorSupportRequest}.
 */
@Service
@RequiredArgsConstructor
public class MentorSupportRequestService {

    private static final List<String> LIVE_EVENT_STATUSES = List.of("OPEN", "SETUP", "IN_PROGRESS");
    private static final Set<String> CATEGORIES = Set.of("RULES", "TECHNICAL", "DIRECTION", "OTHER");

    private final MentorSupportRequestRepository supportRequestRepository;
    private final MentorAssignmentRepository mentorAssignmentRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamEventEntryRepository teamEventEntryRepository;
    private final NotificationService notificationService;

    // ── Participant side ──────────────────────────────────────────────

    /** Mentor(s) of the caller's current team's track — whom to ask for help. */
    @Transactional(readOnly = true)
    public List<MentorContactResponse> getMyTeamMentors(Integer userId) {
        Team team = currentTeam(userId);
        Track track = currentEntry(team).getTrack();
        if (track == null) return List.of();
        return mentorAssignmentRepository.findAllByTrack_TrackIdAndIsActiveTrue(track.getTrackId()).stream()
                .map(ma -> MentorContactResponse.builder()
                        .userId(ma.getMentor().getUserId())
                        .fullName(ma.getMentor().getFullName())
                        .email(ma.getMentor().getEmail())
                        .trackId(track.getTrackId())
                        .trackName(track.getName())
                        .build())
                .collect(Collectors.toList());
    }

    /** The caller team's requests, newest first (current + history). */
    @Transactional(readOnly = true)
    public List<SupportRequestResponse> getMyTeamRequests(Integer userId) {
        Team team = currentTeam(userId);
        return supportRequestRepository.findByTeam_TeamIdOrderByCreatedAtDesc(team.getTeamId()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** Leader raises a support request to the track mentor. */
    @Transactional
    public SupportRequestResponse createRequest(Integer leaderUserId, CreateSupportRequestRequest request) {
        String category = request.getCategory() == null ? "" : request.getCategory().trim().toUpperCase();
        if (!CATEGORIES.contains(category)) {
            throw new BadRequestException("category must be one of RULES, TECHNICAL, DIRECTION, OTHER.");
        }
        TeamMember membership = currentMembership(leaderUserId);
        if (!"LEADER".equalsIgnoreCase(membership.getMemberRole())) {
            throw new ForbiddenException("Only the team leader can request mentor support.");
        }
        Team team = membership.getTeam();
        Track track = currentEntry(team).getTrack();
        if (track == null) {
            throw new BadRequestException("Your team has no track yet, so it has no mentor to ask.");
        }

        // One open request at a time.
        supportRequestRepository.findFirstByTeam_TeamIdAndStatus(team.getTeamId(), "OPEN")
                .ifPresent(r -> { throw new BadRequestException("Your team already has an open support request. Resolve or cancel it first."); });

        MentorSupportRequest saved = supportRequestRepository.save(MentorSupportRequest.builder()
                .team(team)
                .track(track)
                .requester(membership.getUser())
                .category(category)
                .description(request.getDescription().trim())
                .status("OPEN")
                .build());

        // Notify the track's mentor(s). Carry the requester as sender so the mentor
        // gets the same email-style popup a participant gets for announcements.
        String title = "Support request · " + team.getName();
        String body = "[" + categoryLabel(category) + "] " + saved.getDescription();
        User requester = membership.getUser();
        mentorAssignmentRepository.findAllByTrack_TrackIdAndIsActiveTrue(track.getTrackId())
                .forEach(ma -> notificationService.createNotification(
                        ma.getMentor().getUserId(), title, body, "SUPPORT_REQUEST",
                        requester, "Team Leader", team.getName()));

        return toResponse(saved);
    }

    /** Leader cancels their team's own open request. */
    @Transactional
    public SupportRequestResponse cancelRequest(Integer leaderUserId, Integer requestId) {
        TeamMember membership = currentMembership(leaderUserId);
        if (!"LEADER".equalsIgnoreCase(membership.getMemberRole())) {
            throw new ForbiddenException("Only the team leader can cancel a support request.");
        }
        MentorSupportRequest req = supportRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Support request not found: " + requestId));
        if (!req.getTeam().getTeamId().equals(membership.getTeam().getTeamId())) {
            throw new ForbiddenException("This request does not belong to your team.");
        }
        if (!"OPEN".equalsIgnoreCase(req.getStatus())) {
            throw new BadRequestException("Only an open request can be cancelled.");
        }
        req.setStatus("CANCELLED");
        supportRequestRepository.save(req);
        return toResponse(req);
    }

    // ── Mentor side ───────────────────────────────────────────────────

    /** Requests across the mentor's assigned tracks, newest first. */
    @Transactional(readOnly = true)
    public List<SupportRequestResponse> listForMentor(Integer mentorUserId) {
        List<Integer> trackIds = mentorAssignmentRepository.findActiveByMentor(mentorUserId).stream()
                .map(ma -> ma.getTrack().getTrackId())
                .collect(Collectors.toList());
        if (trackIds.isEmpty()) return List.of();
        return supportRequestRepository.findByTrackIds(trackIds).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** Mentor marks a request resolved (they've helped the team in person). */
    @Transactional
    public SupportRequestResponse resolveRequest(Integer mentorUserId, Integer requestId) {
        MentorSupportRequest req = supportRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Support request not found: " + requestId));

        boolean assigned = mentorAssignmentRepository
                .existsByMentor_UserIdAndTrack_TrackId(mentorUserId, req.getTrack().getTrackId());
        if (!assigned) {
            throw new ForbiddenException("You are not the mentor of this team's track.");
        }
        if (!"OPEN".equalsIgnoreCase(req.getStatus())) {
            throw new BadRequestException("Only an open request can be resolved.");
        }
        req.setStatus("RESOLVED");
        req.setResolvedBy(mentorAssignmentRepository.findActiveByMentor(mentorUserId).stream()
                .findFirst().map(MentorAssignment::getMentor).orElse(req.getRequester()));
        req.setResolvedAt(LocalDateTime.now());
        supportRequestRepository.save(req);

        // Let the team leader know it was handled.
        notificationService.createNotification(
                req.getRequester().getUserId(),
                "Support resolved · " + req.getTeam().getName(),
                "Your mentor marked the [" + categoryLabel(req.getCategory()) + "] support request as resolved.",
                "SUPPORT_RESOLVED");

        return toResponse(req);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    /** The caller's current team membership in a live event (most recent). */
    private TeamMember currentMembership(Integer userId) {
        return teamMemberRepository.findByUser_UserIdAndTeam_Event_StatusIn(userId, LIVE_EVENT_STATUSES).stream()
                .max(Comparator.comparing(TeamMember::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow(() -> new ResourceNotFoundException("You are not currently a member of any team."));
    }

    private Team currentTeam(Integer userId) {
        return currentMembership(userId).getTeam();
    }

    /** The team's current TeamEventEntry — one per team in practice today (rejoin isn't built yet). */
    private TeamEventEntry currentEntry(Team team) {
        return teamEventEntryRepository.findTopByTeam_TeamIdOrderByIdDesc(team.getTeamId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No season participation found for team: " + team.getTeamId()));
    }

    private String categoryLabel(String category) {
        switch (category) {
            case "RULES": return "Rules";
            case "TECHNICAL": return "Technical";
            case "DIRECTION": return "Direction";
            default: return "Other";
        }
    }

    private SupportRequestResponse toResponse(MentorSupportRequest r) {
        return SupportRequestResponse.builder()
                .requestId(r.getRequestId())
                .teamId(r.getTeam().getTeamId())
                .teamName(r.getTeam().getName())
                .trackId(r.getTrack().getTrackId())
                .trackName(r.getTrack().getName())
                .category(r.getCategory())
                .description(r.getDescription())
                .status(r.getStatus())
                .requesterName(r.getRequester() != null ? r.getRequester().getFullName() : null)
                .createdAt(r.getCreatedAt())
                .resolvedByName(r.getResolvedBy() != null ? r.getResolvedBy().getFullName() : null)
                .resolvedAt(r.getResolvedAt())
                .build();
    }
}
