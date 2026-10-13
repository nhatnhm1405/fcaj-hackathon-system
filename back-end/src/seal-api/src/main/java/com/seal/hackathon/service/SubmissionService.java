package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.SubmitRequest;
import com.seal.hackathon.dto.response.SubmissionEligibilityResponse;
import com.seal.hackathon.dto.response.SubmissionResponse;
import com.seal.hackathon.entity.*;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ForbiddenException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubmissionService {

    private static final Set<String> STUDENT_TYPES = Set.of("FPT_STUDENT", "EXTERNAL_STUDENT");
    private static final String ROLE_EVENT_COORDINATOR = "ROLE_EVENT_COORDINATOR";
    private static final String ROLE_JUDGE = "ROLE_JUDGE";
    private static final int MAX_URL_LENGTH = 500;
    private static final int MAX_DESCRIPTION_LENGTH = 5000;
    private static final Pattern HTTP_URL_PATTERN = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);

    private final SubmissionRepository submissionRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamEventEntryRepository teamEventEntryRepository;
    private final RoundRepository roundRepository;
    private final RoundResultRepository resultRepository;
    private final UserRepository userRepository;
    private final RoundTimerService roundTimerService;

    // ── Participant: submit or update submission ──────────────────────

    @Transactional
    public SubmissionResponse submit(Integer userId, SubmitRequest request) {
        validateSubmitRequest(request);

        User submitter = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (!isStudent(submitter)) {
            throw new ForbiddenException("Only student participants can submit or update a submission.");
        }

        Round round = roundRepository.findById(request.getRoundId())
                .orElseThrow(() -> new ResourceNotFoundException("Round not found: " + request.getRoundId()));

        if (!isOpenRound(round.getStatus())) {
            throw new BadRequestException("Submissions are only accepted for ACTIVE or OPEN rounds.");
        }

        // Server-authoritative hard gate: a configured, running CONTEST timer is required.
        roundTimerService.assertContestOpen(round.getRoundId());

        // Find the user's team in this event
        List<TeamMember> memberships = teamMemberRepository
                .findByUser_UserIdAndTeam_Event_StatusIn(userId,
                        List.of("OPEN", "IN_PROGRESS"));
        TeamMember membership = memberships.stream()
                .filter(m -> teamEventEntryRepository.existsByTeam_TeamIdAndEvent_EventId(
                        m.getTeam().getTeamId(), round.getEvent().getEventId()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "You are not a member of any approved team in this event."));

        Team team = membership.getTeam();
        TeamEventEntry entry = teamEventEntryRepository
                .findByTeam_TeamIdAndEvent_EventId(team.getTeamId(), round.getEvent().getEventId())
                .orElseThrow(() -> new BadRequestException(
                        "You are not a member of any approved team in this event."));
        if (!"APPROVED".equalsIgnoreCase(entry.getStatus())) {
            throw new BadRequestException("Your team must be approved before submitting.");
        }
        if (!"LEADER".equalsIgnoreCase(membership.getMemberRole())) {
            throw new ForbiddenException("Only the team leader can submit or update a submission.");
        }

        assertEligibleForRound(team, round);

        LocalDateTime now = LocalDateTime.now();
        if (round.getSubmissionDeadline() != null && now.isAfter(round.getSubmissionDeadline())) {
            throw new BadRequestException("The submission deadline has passed for this round.");
        }

        Submission submission = submissionRepository
                .findByTeam_TeamIdAndRound_RoundId(team.getTeamId(), round.getRoundId())
                .orElse(null);

        if (submission == null) {
            submission = Submission.builder()
                    .team(team)
                    .round(round)
                    .submittedBy(submitter)
                    .build();
        }
        submission.setRepoUrl(normalizeRequiredUrl(request.getRepoUrl(), "Repository URL"));
        submission.setDemoUrl(normalizeOptionalUrl(request.getDemoUrl(), "Demo URL"));
        submission.setSlideUrl(normalizeOptionalUrl(request.getSlideUrl(), "Slide URL"));
        submission.setDescription(normalizeDescription(request.getDescription()));
        submission.setSubmittedAt(now);
        submission.setStatus("SUBMITTED");
        submission.setSubmittedBy(submitter);

        submission = submissionRepository.save(submission);
        return mapToResponse(submission);
    }

    // ── Participant: server-authoritative eligibility for one round ────────

    @Transactional(readOnly = true)
    public SubmissionEligibilityResponse getMyEligibility(Integer userId, Integer roundId) {
        Round round = roundRepository.findById(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Round not found: " + roundId));
        TeamMember membership = findMembershipForRound(userId, round)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "You are not part of any team in this event."));
        return evaluateEligibility(membership.getTeam(), round);
    }

    // ── Participant: get my team's submission for a round ─────────────

    @Transactional(readOnly = true)
    public SubmissionResponse getMySubmission(Integer userId, Integer roundId) {
        Round round = roundRepository.findById(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Round not found: " + roundId));

        List<TeamMember> memberships = teamMemberRepository
                .findByUser_UserIdAndTeam_Event_StatusIn(userId,
                        List.of("OPEN", "IN_PROGRESS"));
        TeamMember membership = memberships.stream()
                .filter(m -> teamEventEntryRepository.existsByTeam_TeamIdAndEvent_EventId(
                        m.getTeam().getTeamId(), round.getEvent().getEventId()))
                .findFirst()
                .orElse(null);
        if (membership == null) {
            membership = teamMemberRepository.findByUser_UserIdOrderByIdDesc(userId).stream()
                    .filter(m -> teamEventEntryRepository.existsByTeam_TeamIdAndEvent_EventId(
                            m.getTeam().getTeamId(), round.getEvent().getEventId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "You are not part of any team in this event."));
        }

        Submission submission = submissionRepository
                .findByTeam_TeamIdAndRound_RoundId(membership.getTeam().getTeamId(), roundId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No submission found for your team in round " + roundId));

        return mapToResponse(submission);
    }

    // ── Judge/Coordinator: list all submissions for a round ───────────

    @Transactional(readOnly = true)
    public List<SubmissionResponse> getSubmissionsByRound(Integer requesterId, Set<String> authorities, Integer roundId) {
        roundRepository.findById(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Round not found: " + roundId));

        List<Submission> submissions;
        if (hasAuthority(authorities, ROLE_EVENT_COORDINATOR)) {
            submissions = submissionRepository.findAllByRound_RoundId(roundId);
        } else if (hasAuthority(authorities, ROLE_JUDGE)) {
            roundTimerService.assertJudgingStarted(roundId);
            submissions = submissionRepository.findAllByRoundIdAndJudgeId(roundId, requesterId);
        } else {
            throw new ForbiddenException("You do not have permission to view submissions for this round.");
        }

        return submissions.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ── Get single submission by ID ───────────────────────────────────

    @Transactional(readOnly = true)
    public SubmissionResponse getSubmissionById(Integer requesterId, Set<String> authorities, Integer submissionId) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + requesterId));

        Submission submission;
        if (hasAuthority(authorities, ROLE_EVENT_COORDINATOR)) {
            submission = submissionRepository.findById(submissionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + submissionId));
        } else if (isStudent(requester)) {
            submission = submissionRepository.findById(submissionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + submissionId));
            boolean ownsTeam = teamMemberRepository.existsByUser_UserIdAndTeam_TeamId(
                    requesterId, submission.getTeam().getTeamId());
            if (!ownsTeam) {
                throw new ForbiddenException("You can only view submissions from your own team.");
            }
        } else if (hasAuthority(authorities, ROLE_JUDGE)) {
            submission = submissionRepository.findBySubmissionIdAndJudgeId(submissionId, requesterId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Submission not found or not assigned to this judge."));
            roundTimerService.assertJudgingStarted(submission.getRound().getRoundId());
        } else {
            throw new ForbiddenException("You do not have permission to view this submission.");
        }

        return mapToResponse(submission);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private SubmissionResponse mapToResponse(Submission s) {
        return SubmissionResponse.builder()
                .submissionId(s.getSubmissionId())
                .teamId(s.getTeam().getTeamId())
                .teamName(s.getTeam().getName())
                .roundId(s.getRound().getRoundId())
                .roundName(s.getRound().getName())
                .repoUrl(s.getRepoUrl())
                .demoUrl(s.getDemoUrl())
                .slideUrl(s.getSlideUrl())
                .description(s.getDescription())
                .submittedAt(s.getSubmittedAt())
                .submittedById(s.getSubmittedBy().getUserId())
                .submittedByName(s.getSubmittedBy().getFullName())
                .status(s.getStatus())
                .build();
    }

    private boolean isOpenRound(String status) {
        return "ACTIVE".equalsIgnoreCase(status) || "OPEN".equalsIgnoreCase(status);
    }

    private void assertEligibleForRound(Team team, Round round) {
        SubmissionEligibilityResponse eligibility = evaluateEligibility(team, round);
        if (!Boolean.TRUE.equals(eligibility.getEligible())) {
            throw new BadRequestException(eligibility.getReason());
        }
    }

    private SubmissionEligibilityResponse evaluateEligibility(Team team, Round round) {
        List<Round> rounds = roundRepository
                .findAllByEvent_EventIdOrderByOrderNumber(round.getEvent().getEventId());
        Integer targetOrder = round.getOrderNumber();
        Round previous = targetOrder == null ? null : rounds.stream()
                .filter(candidate -> candidate.getOrderNumber() != null
                        && candidate.getOrderNumber() < targetOrder)
                .max(Comparator.comparingInt(Round::getOrderNumber))
                .orElse(null);

        if (previous == null || previous.getTopNAdvance() == null) {
            return eligibility(round, true, "ELIGIBLE",
                    "Your team is eligible for this round.", previous, null);
        }

        Integer cutoff = previous.getTopNAdvance();
        RoundResult result = resultRepository
                .findByTeam_TeamIdAndRound_RoundId(team.getTeamId(), previous.getRoundId())
                .orElse(null);
        boolean resultsPublished = result != null
                ? Boolean.TRUE.equals(result.getIsPublished())
                : !resultRepository
                        .findAllByRound_RoundIdAndIsPublishedTrueOrderByRankPosition(previous.getRoundId())
                        .isEmpty();

        if (!"FINALIZED".equalsIgnoreCase(previous.getStatus()) || !resultsPublished) {
            return eligibility(round, false, "WAITING_FOR_RESULTS",
                    "Results for \"" + previous.getName()
                            + "\" have not been published yet. Submission remains locked.",
                    previous, null);
        }

        if (result != null && result.getRankPosition() != null
                && result.getRankPosition() <= cutoff) {
            return eligibility(round, true, "ADVANCED",
                    "Your team advanced from \"" + previous.getName() + "\".",
                    previous, result.getRankPosition());
        }

        String rankText = result != null && result.getRankPosition() != null
                ? " ranked #" + result.getRankPosition()
                : " did not receive an advancing rank";
        return eligibility(round, false, "ELIMINATED",
                "Your team" + rankText + " in \"" + previous.getName()
                        + "\"; only the top " + cutoff + " advanced.",
                previous, result != null ? result.getRankPosition() : null);
    }

    private SubmissionEligibilityResponse eligibility(
            Round round, boolean eligible, String status, String reason,
            Round previous, Integer rankPosition) {
        return SubmissionEligibilityResponse.builder()
                .roundId(round.getRoundId())
                .roundName(round.getName())
                .eligible(eligible)
                .status(status)
                .reason(reason)
                .previousRoundId(previous != null ? previous.getRoundId() : null)
                .previousRoundName(previous != null ? previous.getName() : null)
                .rankPosition(rankPosition)
                .topNAdvance(previous != null ? previous.getTopNAdvance() : null)
                .build();
    }

    private Optional<TeamMember> findMembershipForRound(Integer userId, Round round) {
        Optional<TeamMember> current = teamMemberRepository
                .findByUser_UserIdAndTeam_Event_StatusIn(userId, List.of("OPEN", "IN_PROGRESS"))
                .stream()
                .filter(member -> teamEventEntryRepository.existsByTeam_TeamIdAndEvent_EventId(
                        member.getTeam().getTeamId(), round.getEvent().getEventId()))
                .findFirst();
        if (current.isPresent()) {
            return current;
        }
        return teamMemberRepository.findByUser_UserIdOrderByIdDesc(userId).stream()
                .filter(member -> teamEventEntryRepository.existsByTeam_TeamIdAndEvent_EventId(
                        member.getTeam().getTeamId(), round.getEvent().getEventId()))
                .findFirst();
    }

    private boolean isStudent(User user) {
        return user.getUserType() != null && STUDENT_TYPES.contains(user.getUserType().toUpperCase());
    }

    private boolean hasAuthority(Set<String> authorities, String authority) {
        return authorities != null && authorities.contains(authority);
    }

    private void validateSubmitRequest(SubmitRequest request) {
        if (request == null) {
            throw new BadRequestException("Submission request is required.");
        }
        if (request.getRoundId() == null) {
            throw new BadRequestException("Round ID is required.");
        }
        normalizeRequiredUrl(request.getRepoUrl(), "Repository URL");
        normalizeOptionalUrl(request.getDemoUrl(), "Demo URL");
        normalizeOptionalUrl(request.getSlideUrl(), "Slide URL");
        normalizeDescription(request.getDescription());
    }

    private String normalizeRequiredUrl(String value, String fieldName) {
        String normalized = normalizeBlankToNull(value);
        if (normalized == null) {
            throw new BadRequestException(fieldName + " is required.");
        }
        validateUrl(normalized, fieldName);
        return normalized;
    }

    private String normalizeOptionalUrl(String value, String fieldName) {
        String normalized = normalizeBlankToNull(value);
        if (normalized == null) {
            return null;
        }
        validateUrl(normalized, fieldName);
        return normalized;
    }

    private void validateUrl(String value, String fieldName) {
        if (value.length() > MAX_URL_LENGTH) {
            throw new BadRequestException(fieldName + " must be at most " + MAX_URL_LENGTH + " characters.");
        }
        if (!HTTP_URL_PATTERN.matcher(value).matches()) {
            throw new BadRequestException(fieldName + " must start with http:// or https://.");
        }
    }

    private String normalizeDescription(String value) {
        String normalized = normalizeBlankToNull(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BadRequestException("Description must be at most " + MAX_DESCRIPTION_LENGTH + " characters.");
        }
        return normalized;
    }

    private String normalizeBlankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
