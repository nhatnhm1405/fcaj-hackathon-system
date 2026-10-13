package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.AssignMentorRequest;
import com.seal.hackathon.dto.response.MentorAssignmentResponse;
import com.seal.hackathon.dto.response.MentorHistoryResponse;
import com.seal.hackathon.dto.response.MentorRosterItemResponse;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.MentorAssignment;
import com.seal.hackathon.entity.Round;
import com.seal.hackathon.entity.RoundResult;
import com.seal.hackathon.entity.Submission;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.entity.Track;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.MentorAssignmentRepository;
import com.seal.hackathon.repository.PrizeRepository;
import com.seal.hackathon.repository.RoundRepository;
import com.seal.hackathon.repository.RoundResultRepository;
import com.seal.hackathon.repository.SubmissionRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TrackRepository;
import com.seal.hackathon.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MentorAssignmentService {

    private static final Set<String> ASSIGNMENT_EVENT_STATUSES = Set.of("SETUP", "IN_PROGRESS");

    private final MentorAssignmentRepository mentorAssignmentRepository;
    private final TeamEventEntryRepository teamEventEntryRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final RoundRepository roundRepository;
    private final TrackRepository trackRepository;
    private final SubmissionRepository submissionRepository;
    private final RoundResultRepository roundResultRepository;
    private final PrizeRepository prizeRepository;
    private final AuditLogService auditLogService;
    private final EventRoleGranter eventRoleGranter;

    /**
     * Lấy danh sách các team thuộc track được phân công cho Mentor (is_active = true)
     */
    @Transactional(readOnly = true)
    public MentorAssignmentResponse getMentorAssignments(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        List<MentorAssignment> assignments = mentorAssignmentRepository.findActiveByMentor(userId);

        String eventName = assignments.isEmpty() ? "N/A"
                : assignments.get(0).getTrack().getEvent().getName();

        // Rounds được cache theo event để không truy vấn lại cho mỗi team.
        Map<Integer, List<Round>> roundsByEvent = new HashMap<>();
        List<MentorAssignmentResponse.AssignedTeamInfo> teamInfos = new ArrayList<>();
        // Track được phân công (kể cả rỗng); dedupe phòng trường hợp trùng.
        List<MentorAssignmentResponse.AssignedTrackInfo> trackInfos = new ArrayList<>();
        Set<Integer> seenTracks = new HashSet<>();

        for (MentorAssignment ma : assignments) {
            Track track = ma.getTrack();
            HackathonEvent trackEvent = track.getEvent();
            List<Round> orderedRounds = roundsByEvent.computeIfAbsent(
                    trackEvent.getEventId(),
                    roundRepository::findAllByEvent_EventIdOrderByOrderNumber);

            if (seenTracks.add(track.getTrackId())) {
                trackInfos.add(MentorAssignmentResponse.AssignedTrackInfo.builder()
                        .trackId(track.getTrackId())
                        .trackName(track.getName())
                        .eventId(trackEvent.getEventId())
                        .eventName(trackEvent.getName())
                        .season(trackEvent.getSeason())
                        .year(trackEvent.getYear())
                        .eventStatus(trackEvent.getStatus())
                        .build());
            }

            for (TeamEventEntry teamEntry : teamEventEntryRepository.findAllByTrack_TrackIdAndStatus(track.getTrackId(), "APPROVED")) {
                Team team = teamEntry.getTeam();
                // Submissions thực sự đã nộp (bỏ DRAFT) để biết team nộp chưa.
                List<Submission> submitted = submissionRepository
                        .findAllByTeam_TeamId(team.getTeamId()).stream()
                        .filter(s -> !"DRAFT".equalsIgnoreCase(s.getStatus()))
                        .collect(Collectors.toList());
                LocalDateTime lastAt = submitted.stream()
                        .map(Submission::getSubmittedAt)
                        .filter(Objects::nonNull)
                        .max(Comparator.naturalOrder())
                        .orElse(null);

                Map<Integer, RoundResult> resultByRound = roundResultRepository
                        .findAllByTeamIdOrderByRoundOrder(team.getTeamId()).stream()
                        .collect(Collectors.toMap(rr -> rr.getRound().getRoundId(), rr -> rr, (a, b) -> a));
                TeamRoundStatus roundStatus = resolveCurrentRound(orderedRounds, resultByRound);

                teamInfos.add(MentorAssignmentResponse.AssignedTeamInfo.builder()
                        .teamId(team.getTeamId())
                        .teamName(team.getName())
                        .trackId(track.getTrackId())
                        .trackName(track.getName())
                        .eventId(trackEvent.getEventId())
                        .eventName(trackEvent.getName())
                        .season(trackEvent.getSeason())
                        .year(trackEvent.getYear())
                        .eventStatus(trackEvent.getStatus())
                        .members(mapMentorMembers(team))
                        .submissionCount(submitted.size())
                        .lastSubmittedAt(lastAt)
                        .currentRoundName(roundStatus.roundName)
                        .eliminated(roundStatus.eliminated)
                        .build());
            }
        }

        return MentorAssignmentResponse.builder()
                .mentorId(user.getUserId())
                .mentorName(user.getFullName())
                .eventName(eventName)
                .teams(teamInfos)
                .tracks(trackInfos)
                .build();
    }

    /**
     * Xác định round xa nhất mà team còn trụ lại, đi qua các round theo thứ tự:
     * - Round chưa FINALIZED → team đang thi ở round này (round hiện tại).
     * - Round đã FINALIZED, không có cut-off (topNAdvance null) → mọi team qua vòng.
     * - Round đã FINALIZED, có cut-off → chỉ qua vòng nếu rankPosition <= topNAdvance;
     *   nếu không, team dừng lại tại round này với eliminated = true.
     */
    private TeamRoundStatus resolveCurrentRound(List<Round> orderedRounds,
                                                Map<Integer, RoundResult> resultByRound) {
        if (orderedRounds.isEmpty()) {
            return new TeamRoundStatus(null, false);
        }
        Round current = orderedRounds.get(0);
        boolean eliminated = false;
        for (int i = 0; i < orderedRounds.size(); i++) {
            Round r = orderedRounds.get(i);
            current = r;
            if (i == orderedRounds.size() - 1) {
                break; // đã tới round cuối cùng
            }
            if ("FINALIZED".equalsIgnoreCase(r.getStatus())) {
                Integer cutoff = r.getTopNAdvance();
                if (cutoff != null) {
                    RoundResult rr = resultByRound.get(r.getRoundId());
                    boolean advanced = rr != null && rr.getRankPosition() != null
                            && rr.getRankPosition() <= cutoff;
                    if (!advanced) {
                        eliminated = true;
                        break;
                    }
                }
                // cut-off null hoặc đã đậu → đi tiếp sang round kế tiếp
            } else {
                break; // round chưa finalized → team đang ở đây
            }
        }
        return new TeamRoundStatus(current.getName(), eliminated);
    }

    /** Kết quả tính round hiện tại của một team. */
    private static final class TeamRoundStatus {
        final String roundName;
        final boolean eliminated;

        TeamRoundStatus(String roundName, boolean eliminated) {
            this.roundName = roundName;
            this.eliminated = eliminated;
        }
    }

    /**
     * Read-only mentor history: every event the mentor was assigned to, grouped by
     * the track(s) they mentored, with each approved team's final standing and prize.
     */
    @Transactional(readOnly = true)
    public List<MentorHistoryResponse> getMentorHistory(Integer userId) {
        List<MentorAssignment> assignments = mentorAssignmentRepository.findActiveByMentor(userId);

        // Group assignments by event (preserve order), then by track within each event.
        Map<Integer, List<MentorAssignment>> byEvent = new LinkedHashMap<>();
        for (MentorAssignment ma : assignments) {
            byEvent.computeIfAbsent(ma.getTrack().getEvent().getEventId(), k -> new ArrayList<>()).add(ma);
        }

        List<MentorHistoryResponse> result = new ArrayList<>();
        for (List<MentorAssignment> eventAssignments : byEvent.values()) {
            HackathonEvent event = eventAssignments.get(0).getTrack().getEvent();

            Round finalRound = roundRepository.findFirstByEvent_EventIdAndIsFinalTrue(event.getEventId()).orElse(null);
            Map<Integer, String> prizeByTeam = prizeRepository
                    .findAllByEvent_EventIdAndAwardedAtIsNotNullOrderByRankPosition(event.getEventId()).stream()
                    .filter(prize -> prize.getTeam() != null)
                    .collect(Collectors.toMap(
                            prize -> prize.getTeam().getTeamId(),
                            prize -> prize.getName(),
                            (first, ignored) -> first));

            Map<Integer, Track> distinctTracks = new LinkedHashMap<>();
            for (MentorAssignment ma : eventAssignments) {
                distinctTracks.putIfAbsent(ma.getTrack().getTrackId(), ma.getTrack());
            }

            List<MentorHistoryResponse.TrackGroup> trackGroups = new ArrayList<>();
            for (Track track : distinctTracks.values()) {
                List<MentorHistoryResponse.TeamResult> teams = teamEventEntryRepository
                        .findAllByTrack_TrackIdAndStatus(track.getTrackId(), "APPROVED").stream()
                        .map(teamEntry -> {
                            Team team = teamEntry.getTeam();
                            Integer finalRank = finalRound == null ? null : roundResultRepository
                        .findByTeam_TeamIdAndRound_RoundId(team.getTeamId(), finalRound.getRoundId())
                        .filter(resultRow -> Boolean.TRUE.equals(resultRow.getIsPublished()))
                        .map(resultRow -> resultRow.getRankPosition())
                        .orElse(null);

                            List<TeamMember> teamMembers = teamMemberRepository.findByTeam_TeamId(team.getTeamId());
                            List<MentorHistoryResponse.MemberInfo> members = teamMembers.stream()
                                    .map(tm -> MentorHistoryResponse.MemberInfo.builder()
                                            .fullName(tm.getUser().getFullName())
                                            .memberRole(tm.getMemberRole())
                                            .studentId(tm.getUser().getStudentId())
                                            .userType(tm.getUser().getUserType())
                                            .university(tm.getUser().getUniversity())
                                            .build())
                                    .collect(Collectors.toList());

                            return MentorHistoryResponse.TeamResult.builder()
                                    .teamId(team.getTeamId())
                                    .teamName(team.getName())
                                    .teamStatus(teamEntry.getStatus())
                                    .finalRank(finalRank)
                                    .prizeName(prizeByTeam.get(team.getTeamId()))
                                    .memberCount(teamMembers.size())
                                    .members(members)
                                    .build();
                        })
                        .collect(Collectors.toList());

                trackGroups.add(MentorHistoryResponse.TrackGroup.builder()
                        .trackId(track.getTrackId())
                        .trackName(track.getName())
                        .teams(teams)
                        .build());
            }

            result.add(MentorHistoryResponse.builder()
                    .eventId(event.getEventId())
                    .eventName(event.getName())
                    .season(event.getSeason())
                    .year(event.getYear())
                    .eventStatus(event.getStatus())
                    .tracks(trackGroups)
                    .build());
        }

        // Newest event first.
        result.sort(Comparator.comparing(MentorHistoryResponse::getYear, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(MentorHistoryResponse::getEventId, Comparator.reverseOrder()));
        return result;
    }

    // ── Coordinator: create assignments ───────────────────────────────

    /**
     * Coordinator assigns a mentor to a track (whole event). Also ensures the
     * mentor holds the MENTOR role for that track's event so they gain access.
     */
    @Transactional
    public MentorAssignmentResponse assignMentor(AssignMentorRequest request, Integer actorUserId) {
        User mentor = userRepository.findByIdWithRoles(request.getMentorUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getMentorUserId()));
        Track track = trackRepository.findById(request.getTrackId())
                .orElseThrow(() -> new ResourceNotFoundException("Track not found: " + request.getTrackId()));
        assertEventAllowsAssignments(track.getEvent().getStatus(), "Mentor assignments");

        // Guest judges are one-off external scorers, not embedded staff — they
        // can't take on a mentor's ongoing track responsibility.
        if ("GUEST".equalsIgnoreCase(mentor.getJudgeType())) {
            throw new BadRequestException("Guest judges cannot be assigned as mentors.");
        }

        if (mentorAssignmentRepository.existsByMentor_UserIdAndTrack_TrackId(mentor.getUserId(), track.getTrackId())) {
            throw new BadRequestException("This mentor is already assigned to this track.");
        }

        // Business rule: a mentor manages at most one track per event. (A track may
        // still have several mentors — only the mentor→track direction is capped.)
        Integer eventId = track.getEvent().getEventId();
        if (mentorAssignmentRepository.existsByMentor_UserIdAndTrack_Event_EventIdAndIsActiveTrue(mentor.getUserId(), eventId)) {
            throw new BadRequestException("This mentor already manages a track in this event. A mentor can manage only one track per event.");
        }

        eventRoleGranter.ensureRole(mentor, "MENTOR", eventId);

        mentorAssignmentRepository.save(MentorAssignment.builder()
                .mentor(mentor)
                .track(track)
                .build());

        auditLogService.record(actorUserId, "ASSIGN_MENTOR", "TRACK", track.getTrackId(), null,
                Map.of("mentor_user_id", mentor.getUserId(),
                        "event_id", track.getEvent().getEventId()));

        return getMentorAssignments(mentor.getUserId());
    }

    /**
     * Coordinator roster: all active mentor assignments (mentor -> track) in an event.
     */
    @Transactional(readOnly = true)
    public List<MentorRosterItemResponse> listMentorAssignmentsByEvent(Integer eventId) {
        return mentorAssignmentRepository.findActiveByEvent(eventId).stream()
                .map(ma -> MentorRosterItemResponse.builder()
                        .id(ma.getId())
                        .mentorUserId(ma.getMentor().getUserId())
                        .mentorName(ma.getMentor().getFullName())
                        .trackId(ma.getTrack().getTrackId())
                        .trackName(ma.getTrack().getName())
                        .build())
                .collect(Collectors.toList());
    }

    /** Removes a mentor assignment (hard delete so the mentor can be re-assigned later). */
    @Transactional
    public void removeMentorAssignment(Integer assignmentId) {
        MentorAssignment assignment = mentorAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentor assignment not found: " + assignmentId));
        mentorAssignmentRepository.delete(assignment);
    }

    private List<MentorAssignmentResponse.TeamMemberInfo> mapMentorMembers(Team team) {
        List<TeamMember> members = teamMemberRepository.findByTeam_TeamId(team.getTeamId());
        return members.stream()
                .map(m -> MentorAssignmentResponse.TeamMemberInfo.builder()
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

    private void assertEventAllowsAssignments(String eventStatus, String subject) {
        if (!ASSIGNMENT_EVENT_STATUSES.contains((eventStatus == null ? "" : eventStatus).toUpperCase())) {
            throw new BadRequestException(subject + " can only be changed when the event is SETUP or IN_PROGRESS.");
        }
    }
}
