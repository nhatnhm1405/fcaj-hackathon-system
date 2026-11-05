package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.AssignJudgeRequest;
import com.seal.hackathon.dto.request.CreateGuestJudgeRequest;
import com.seal.hackathon.dto.request.ReplaceJudgeRequest;
import com.seal.hackathon.dto.response.JudgeAssignmentResponse;
import com.seal.hackathon.dto.response.JudgeRosterItemResponse;
import com.seal.hackathon.entity.JudgeAssignment;
import com.seal.hackathon.entity.Round;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.entity.Track;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.JudgeAssignmentRepository;
import com.seal.hackathon.repository.RoundRepository;
import com.seal.hackathon.repository.ScoreRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TrackRepository;
import com.seal.hackathon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JudgeAssignmentService {

    private static final Set<String> ASSIGNMENT_EVENT_STATUSES = Set.of("SETUP", "IN_PROGRESS");
    private static final Set<String> JUDGE_TYPES = Set.of("INTERNAL", "GUEST");

    private final JudgeAssignmentRepository judgeAssignmentRepository;
    private final TeamEventEntryRepository teamEventEntryRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final RoundRepository roundRepository;
    private final TrackRepository trackRepository;
    private final ScoreRepository scoreRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final RoundTimerService roundTimerService;
    private final EventRoleGranter eventRoleGranter;

    /**
     * Lấy danh sách các team được phân công cho Judge chấm điểm (is_active = true).
     * Track NULL (vòng Final) → toàn bộ team của event; ngược lại → team trong track.
     */
    @Transactional(readOnly = true)
    public JudgeAssignmentResponse getJudgeAssignments(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        List<JudgeAssignment> assignments = judgeAssignmentRepository.findActiveByJudge(userId);

        JudgeAssignment primaryAssignment = assignments.stream()
                .filter(assignment -> "IN_PROGRESS".equalsIgnoreCase(
                        assignment.getRound().getEvent().getStatus()))
                .findFirst()
                .orElse(assignments.isEmpty() ? null : assignments.get(0));
        Integer eventId = primaryAssignment == null ? null
                : primaryAssignment.getRound().getEvent().getEventId();
        String eventName = primaryAssignment == null ? "N/A"
                : primaryAssignment.getRound().getEvent().getName();
        List<JudgeAssignment> selectedEventAssignments = eventId == null ? List.of() : assignments.stream()
                .filter(assignment -> Objects.equals(eventId,
                        assignment.getRound().getEvent().getEventId()))
                .toList();

        List<JudgeAssignmentResponse.AssignedTeamInfo> teamInfos = selectedEventAssignments.stream()
                .flatMap(ja -> {
                    Round round = ja.getRound();
                    Integer assignmentTrackId = ja.getTrack() == null ? null : ja.getTrack().getTrackId();
                    int panelSize = (int) judgeAssignmentRepository
                            .findAllByRound_RoundIdAndIsActiveTrue(round.getRoundId()).stream()
                            .filter(other -> Objects.equals(assignmentTrackId,
                                    other.getTrack() == null ? null : other.getTrack().getTrackId()))
                            .count();
                    List<TeamEventEntry> teamEntries = ja.getTrack() != null
                            ? teamEventEntryRepository.findAllByTrack_TrackIdAndStatus(ja.getTrack().getTrackId(), "APPROVED")
                            : teamEventEntryRepository.findAllByEvent_EventIdAndStatus(round.getEvent().getEventId(), "APPROVED");
                    return teamEntries.stream()
                            .map(teamEntry -> JudgeAssignmentResponse.AssignedTeamInfo.builder()
                                    .teamId(teamEntry.getTeam().getTeamId())
                                    .teamName(teamEntry.getTeam().getName())
                                    .trackName(teamEntry.getTrack() != null ? teamEntry.getTrack().getName() : null)
                                    .roundId(round.getRoundId())
                                    .assignedJudgeCount(panelSize)
                                    .members(mapJudgeMembers(teamEntry.getTeam()))
                                    .build());
                })
                .collect(Collectors.toList());

        return JudgeAssignmentResponse.builder()
                .judgeId(user.getUserId())
                .judgeName(user.getFullName())
                .eventId(eventId)
                .eventName(eventName)
                .teams(teamInfos)
                .build();
    }

    /**
     * Coordinator assigns a judge to a round (+ track for preliminary rounds).
     * Enforces the is_final rule, defaults judge_type to INTERNAL when unset,
     * and ensures the judge holds the JUDGE role for that round's event.
     */
    @Transactional
    public JudgeAssignmentResponse assignJudge(AssignJudgeRequest request, Integer actorUserId) {
        User judge = userRepository.findByIdWithRoles(request.getJudgeUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getJudgeUserId()));
        Round round = roundRepository.findById(request.getRoundId())
                .orElseThrow(() -> new ResourceNotFoundException("Round not found: " + request.getRoundId()));

        // is_final rule: preliminary rounds are judged per track; the final round across all tracks.
        Track track = null;
        if (Boolean.TRUE.equals(round.getIsFinal())) {
            if (request.getTrackId() != null) {
                throw new BadRequestException("trackId must be null for the final round — judges score all teams.");
            }
        } else {
            if (request.getTrackId() == null) {
                throw new BadRequestException("trackId is required when assigning a judge to a non-final round.");
            }
            track = trackRepository.findById(request.getTrackId())
                    .orElseThrow(() -> new ResourceNotFoundException("Track not found: " + request.getTrackId()));
            if (!track.getEvent().getEventId().equals(round.getEvent().getEventId())) {
                throw new BadRequestException("Track " + track.getTrackId()
                        + " does not belong to the same event as round " + round.getRoundId() + ".");
            }
        }

        assertEventAllowsAssignments(round.getEvent().getStatus(), "Judge assignments");
        roundTimerService.assertJudgeAssignmentsMutable(round.getRoundId());

        JudgeAssignment existing = findExactJudgeAssignment(judge.getUserId(), round.getRoundId(), track)
                .orElse(null);
        if (existing != null && Boolean.TRUE.equals(existing.getIsActive())) {
            throw new BadRequestException("This judge is already assigned to this round/track.");
        }
        if (judgeAssignmentRepository.existsByJudge_UserIdAndRound_RoundIdAndIsActiveTrue(
                judge.getUserId(), round.getRoundId())) {
            throw new BadRequestException("This judge is already assigned in this round. A judge can score only one track per round.");
        }

        // judge_type lives on the user; default internal judges to INTERNAL so every
        // assigned judge has a type (guest judges are created with GUEST by the admin).
        if (judge.getJudgeType() == null || !JUDGE_TYPES.contains(judge.getJudgeType().toUpperCase())) {
            judge.setJudgeType("INTERNAL");
            userRepository.save(judge);
        }

        eventRoleGranter.ensureRole(judge, "JUDGE", round.getEvent().getEventId());

        if (existing != null) {
            existing.setIsActive(true);
            judgeAssignmentRepository.save(existing);
        } else {
            judgeAssignmentRepository.save(JudgeAssignment.builder()
                    .judge(judge)
                    .round(round)
                    .track(track)
                    .build());
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("judge_user_id", judge.getUserId());
        meta.put("round_id", round.getRoundId());
        meta.put("event_id", round.getEvent().getEventId());
        if (track != null) {
            meta.put("track_id", track.getTrackId());
        }
        auditLogService.record(actorUserId, "ASSIGN_JUDGE", "ROUND", round.getRoundId(), null, meta);

        return getJudgeAssignments(judge.getUserId());
    }

    /**
     * Coordinator roster: all active judge assignments in an event, names resolved.
     */
    @Transactional(readOnly = true)
    public List<JudgeRosterItemResponse> listJudgeAssignmentsByEvent(Integer eventId) {
        return judgeAssignmentRepository.findActiveByEvent(eventId).stream()
                .map(ja -> JudgeRosterItemResponse.builder()
                        .id(ja.getId())
                        .judgeUserId(ja.getJudge().getUserId())
                        .judgeName(ja.getJudge().getFullName())
                        .judgeType(ja.getJudge().getJudgeType())
                        .roundId(ja.getRound().getRoundId())
                        .roundName(ja.getRound().getName())
                        .isFinal(ja.getRound().getIsFinal())
                        .trackId(ja.getTrack() != null ? ja.getTrack().getTrackId() : null)
                        .trackName(ja.getTrack() != null ? ja.getTrack().getName() : null)
                        .build())
                .collect(Collectors.toList());
    }

    /** Removes a judge before judging starts, preserving the inactive row for history. */
    @Transactional
    public void removeJudgeAssignment(Integer assignmentId) {
        JudgeAssignment assignment = judgeAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Judge assignment not found: " + assignmentId));
        roundTimerService.assertJudgeAssignmentsMutable(assignment.getRound().getRoundId());
        assignment.setIsActive(false);
        judgeAssignmentRepository.save(assignment);
    }

    /**
     * Replaces an unavailable judge without silently shrinking the panel. The
     * countdown must not be running and the old judge must not have submitted any
     * final score in this assignment's cell.
     */
    @Transactional
    public JudgeAssignmentResponse replaceJudgeAssignment(Integer assignmentId,
                                                           ReplaceJudgeRequest request,
                                                           Integer actorUserId) {
        JudgeAssignment old = judgeAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Judge assignment not found: " + assignmentId));
        if (!Boolean.TRUE.equals(old.getIsActive())) {
            throw new BadRequestException("This judge assignment is no longer active.");
        }
        Round round = old.getRound();
        assertEventAllowsAssignments(round.getEvent().getStatus(), "Judge assignments");
        roundTimerService.assertJudgeReplacementAllowed(round.getRoundId());

        if (Objects.equals(old.getJudge().getUserId(), request.getJudgeUserId())) {
            throw new BadRequestException("Replacement judge must be different from the current judge.");
        }
        if (hasFinalScoreInAssignment(old)) {
            throw new BadRequestException(
                    "This judge has already submitted final scores in this cell and cannot be replaced.");
        }

        User replacement = userRepository.findByIdWithRoles(request.getJudgeUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + request.getJudgeUserId()));
        JudgeAssignment existing = findExactJudgeAssignment(
                replacement.getUserId(), round.getRoundId(), old.getTrack()).orElse(null);
        if (existing != null && Boolean.TRUE.equals(existing.getIsActive())) {
            throw new BadRequestException("The replacement judge is already assigned to this round/track.");
        }
        if (judgeAssignmentRepository.existsByJudge_UserIdAndRound_RoundIdAndIsActiveTrue(
                replacement.getUserId(), round.getRoundId())) {
            throw new BadRequestException("The replacement judge is already assigned in this round. A judge can score only one track per round.");
        }

        if (replacement.getJudgeType() == null
                || !JUDGE_TYPES.contains(replacement.getJudgeType().toUpperCase())) {
            replacement.setJudgeType("INTERNAL");
            userRepository.save(replacement);
        }
        eventRoleGranter.ensureRole(replacement, "JUDGE", round.getEvent().getEventId());

        old.setIsActive(false);
        judgeAssignmentRepository.save(old);
        if (existing != null) {
            existing.setIsActive(true);
            judgeAssignmentRepository.save(existing);
        } else {
            judgeAssignmentRepository.save(JudgeAssignment.builder()
                    .judge(replacement)
                    .round(round)
                    .track(old.getTrack())
                    .build());
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("old_judge_user_id", old.getJudge().getUserId());
        meta.put("new_judge_user_id", replacement.getUserId());
        meta.put("event_id", round.getEvent().getEventId());
        meta.put("reason", request.getReason().trim());
        if (old.getTrack() != null) meta.put("track_id", old.getTrack().getTrackId());
        auditLogService.record(actorUserId, "REPLACE_JUDGE", "ROUND", round.getRoundId(), null, meta);

        return getJudgeAssignments(replacement.getUserId());
    }

    /**
     * Creates a pre-approved GUEST judge account and assigns it to a round in one
     * step. The trackId rule is enforced by {@link #assignJudge}.
     */
    @Transactional
    public JudgeAssignmentResponse createGuestJudge(CreateGuestJudgeRequest request, Integer actorUserId) {
        Round round = roundRepository.findById(request.getRoundId())
                .orElseThrow(() -> new ResourceNotFoundException("Round not found: " + request.getRoundId()));
        assertEventAllowsAssignments(round.getEvent().getStatus(), "Judge assignments");

        String email = request.getEmail().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("An account with this email already exists.");
        }

        User guest = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .userType("STAFF")
                .judgeType("GUEST")
                .provider("LOCAL")
                .isApproved(true)
                .isActive(true)
                .build());

        AssignJudgeRequest assign = new AssignJudgeRequest();
        assign.setJudgeUserId(guest.getUserId());
        assign.setRoundId(request.getRoundId());
        assign.setTrackId(request.getTrackId());
        return assignJudge(assign, actorUserId);
    }

    private void assertEventAllowsAssignments(String eventStatus, String subject) {
        if (!ASSIGNMENT_EVENT_STATUSES.contains((eventStatus == null ? "" : eventStatus).toUpperCase())) {
            throw new BadRequestException(subject + " can only be changed when the event is SETUP or IN_PROGRESS.");
        }
    }

    private Optional<JudgeAssignment> findExactJudgeAssignment(
            Integer judgeUserId, Integer roundId, Track track) {
        return track == null
                ? judgeAssignmentRepository.findByJudge_UserIdAndRound_RoundIdAndTrackIsNull(judgeUserId, roundId)
                : judgeAssignmentRepository.findByJudge_UserIdAndRound_RoundIdAndTrack_TrackId(
                        judgeUserId, roundId, track.getTrackId());
    }

    private boolean hasFinalScoreInAssignment(JudgeAssignment assignment) {
        Integer trackId = assignment.getTrack() == null ? null : assignment.getTrack().getTrackId();
        Integer eventId = assignment.getRound().getEvent().getEventId();
        return scoreRepository
                .findAllByJudge_UserIdAndSubmission_Round_RoundIdAndIsDraftFalse(
                        assignment.getJudge().getUserId(), assignment.getRound().getRoundId())
                .stream()
                .anyMatch(score -> {
                    if (trackId == null) {
                        return true;
                    }
                    Integer scoreTrackId = teamEventEntryRepository
                            .findByTeam_TeamIdAndEvent_EventId(score.getSubmission().getTeam().getTeamId(), eventId)
                            .map(TeamEventEntry::getTrack)
                            .map(Track::getTrackId)
                            .orElse(null);
                    return Objects.equals(trackId, scoreTrackId);
                });
    }

    private List<JudgeAssignmentResponse.TeamMemberInfo> mapJudgeMembers(Team team) {
        List<TeamMember> members = teamMemberRepository.findByTeam_TeamId(team.getTeamId());
        return members.stream()
                .map(m -> JudgeAssignmentResponse.TeamMemberInfo.builder()
                        .userId(m.getUser().getUserId())
                        .fullName(m.getUser().getFullName())
                        .email(m.getUser().getEmail())
                        .memberRole(m.getMemberRole())
                        .studentId(m.getUser().getStudentId())
                        .userType(m.getUser().getUserType())
                        .university(m.getUser().getUniversity())
                        .build())
                .collect(Collectors.toList());
    }
}
