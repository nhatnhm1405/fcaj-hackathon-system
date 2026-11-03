package com.seal.hackathon.service;

import com.seal.hackathon.dto.request.ExtendTimerRequest;
import com.seal.hackathon.dto.request.StartTimerRequest;
import com.seal.hackathon.dto.response.RoundTimerResponse;
import com.seal.hackathon.entity.Round;
import com.seal.hackathon.entity.RoundTimer;
import com.seal.hackathon.entity.Submission;
import com.seal.hackathon.entity.Track;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.JudgeAssignmentRepository;
import com.seal.hackathon.repository.RoundRepository;
import com.seal.hackathon.repository.RoundTimerNoticeRepository;
import com.seal.hackathon.repository.RoundTimerRepository;
import com.seal.hackathon.repository.ScoringCriteriaRepository;
import com.seal.hackathon.repository.SubmissionRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TeamRepository;
import com.seal.hackathon.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Owns the live, server-authoritative countdown for each round phase:
 *   CONTEST → gates team submission;  JUDGING → gates judge scoring.
 *
 * Remaining time is always derived from {@code endsAt} (never the client clock),
 * so a reload/restart recomputes the same value. There is no scheduler: milestone
 * reminders are materialised lazily on read ({@link #getState}) and fanned out to
 * the phase's audience exactly once via {@link TimerNoticeClaimer}.
 */
@Service
@RequiredArgsConstructor
public class RoundTimerService {

    public static final String PHASE_CONTEST = "CONTEST";
    public static final String PHASE_JUDGING = "JUDGING";

    private static final String IDLE = "IDLE";
    private static final String RUNNING = "RUNNING";
    private static final String PAUSED = "PAUSED";
    private static final String STOPPED = "STOPPED";
    private static final String EXPIRED = "EXPIRED";

    private static final String NOTIF_TYPE = "TIMER";
    private static final String EVENT_STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final int MIN_DURATION_SECONDS = 30;
    private static final List<Integer> DEFAULT_MILESTONE_MINUTES = List.of(30, 15, 5, 1);

    private final RoundTimerRepository timerRepository;
    private final RoundTimerNoticeRepository noticeRepository;
    private final RoundRepository roundRepository;
    private final TrackRepository trackRepository;
    private final TeamRepository teamRepository;
    private final TeamEventEntryRepository teamEventEntryRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final JudgeAssignmentRepository judgeAssignmentRepository;
    private final ScoringCriteriaRepository scoringCriteriaRepository;
    private final SubmissionRepository submissionRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final TimerNoticeClaimer noticeClaimer;

    // ── Coordinator: start / restart ──────────────────────────────────────────

    @Transactional
    public RoundTimerResponse start(Integer actorUserId, Integer eventId, Integer roundId,
                                    String phaseRaw, StartTimerRequest request) {
        String phase = normalizePhase(phaseRaw);
        Round round = requireRound(eventId, roundId);
        requireEventInProgress(round);

        int duration = request.getDurationSeconds() == null ? 0 : request.getDurationSeconds();
        if (duration < MIN_DURATION_SECONDS) {
            throw new BadRequestException("Duration must be at least " + MIN_DURATION_SECONDS + " seconds.");
        }

        LocalDateTime now = LocalDateTime.now();
        RoundTimer timer = timerRepository.findByRound_RoundIdAndPhase(roundId, phase).orElse(null);
        if (timer != null && RUNNING.equals(timer.getStatus())
                && secondsBetween(now, timer.getEndsAt()) <= 0) {
            materializeMilestones(round, timer, now);
        }
        if (timer != null && (RUNNING.equals(timer.getStatus()) || PAUSED.equals(timer.getStatus()))) {
            throw new BadRequestException(PAUSED.equals(timer.getStatus())
                    ? "This timer is paused. Resume or stop it before starting a new countdown."
                    : "This timer is already running.");
        }
        if (PHASE_JUDGING.equals(phase)) {
            assertJudgingReady(round, now);
        }
        if (timer == null) {
            timer = RoundTimer.builder().round(round).phase(phase).build();
        }

        timer.setStatus(RUNNING);
        timer.setDurationSeconds(duration);
        timer.setStartedAt(now);
        timer.setEndsAt(now.plusSeconds(duration));
        timer.setPausedAt(null);
        timer.setRemainingAtPause(null);
        if (request.getMilestoneMinutes() != null) {
            timer.setMilestoneMinutes(toCsv(sanitizeMinutes(request.getMilestoneMinutes())));
        }
        if (request.getNotifyAtHalf() != null) {
            timer.setNotifyAtHalf(request.getNotifyAtHalf());
        }
        timer.setUpdatedAt(now);
        timerRepository.save(timer);

        // Fresh run: clear any marks from a previous run so they can fire again.
        noticeRepository.deleteByRoundIdAndPhase(roundId, phase);

        // CONTEST drives the round: open it and mirror the deadline for legacy/display.
        if (PHASE_CONTEST.equals(phase)) {
            round.setStatus("ACTIVE");
            round.setSubmissionDeadline(timer.getEndsAt());
            roundRepository.save(round);
        }

        // Announce the start immediately (coordinator action, no race).
        if (noticeClaimer.claim(roundId, phase, "STARTED")) {
            fanOut(round, phase, "STARTED", timer);
        }

        auditLogService.record(actorUserId, "START_TIMER", "ROUND", roundId, null,
                Map.of("phase", phase, "durationSeconds", duration));
        return toResponse(round, timer, now);
    }

    // ── Coordinator: pause ────────────────────────────────────────────────────

    @Transactional
    public RoundTimerResponse pause(Integer actorUserId, Integer eventId, Integer roundId, String phaseRaw) {
        String phase = normalizePhase(phaseRaw);
        Round round = requireRound(eventId, roundId);
        requireEventInProgress(round);
        RoundTimer timer = requireTimer(roundId, phase);
        if (!RUNNING.equals(timer.getStatus())) {
            throw new BadRequestException("Only a running timer can be paused.");
        }
        LocalDateTime now = LocalDateTime.now();
        long remaining = Math.max(0, secondsBetween(now, timer.getEndsAt()));
        timer.setStatus(PAUSED);
        timer.setPausedAt(now);
        timer.setRemainingAtPause((int) remaining);
        timer.setUpdatedAt(now);
        timerRepository.save(timer);

        auditLogService.record(actorUserId, "PAUSE_TIMER", "ROUND", roundId, null, Map.of("phase", phase));
        return toResponse(round, timer, now);
    }

    // ── Coordinator: resume ───────────────────────────────────────────────────

    @Transactional
    public RoundTimerResponse resume(Integer actorUserId, Integer eventId, Integer roundId, String phaseRaw) {
        String phase = normalizePhase(phaseRaw);
        Round round = requireRound(eventId, roundId);
        requireEventInProgress(round);
        RoundTimer timer = requireTimer(roundId, phase);
        if (!PAUSED.equals(timer.getStatus())) {
            throw new BadRequestException("Only a paused timer can be resumed.");
        }
        LocalDateTime now = LocalDateTime.now();
        int remaining = timer.getRemainingAtPause() == null ? 0 : Math.max(0, timer.getRemainingAtPause());
        timer.setStatus(RUNNING);
        timer.setEndsAt(now.plusSeconds(remaining));
        timer.setPausedAt(null);
        timer.setRemainingAtPause(null);
        timer.setUpdatedAt(now);
        timerRepository.save(timer);

        if (PHASE_CONTEST.equals(phase)) {
            round.setSubmissionDeadline(timer.getEndsAt());
            roundRepository.save(round);
        }

        auditLogService.record(actorUserId, "RESUME_TIMER", "ROUND", roundId, null, Map.of("phase", phase));
        return toResponse(round, timer, now);
    }

    // ── Coordinator: extend (+ seconds) ───────────────────────────────────────

    @Transactional
    public RoundTimerResponse extend(Integer actorUserId, Integer eventId, Integer roundId,
                                     String phaseRaw, ExtendTimerRequest request) {
        String phase = normalizePhase(phaseRaw);
        Round round = requireRound(eventId, roundId);
        requireEventInProgress(round);
        RoundTimer timer = requireTimer(roundId, phase);
        int add = request.getSeconds() == null ? 0 : request.getSeconds();
        if (add <= 0) {
            throw new BadRequestException("Extension seconds must be positive.");
        }
        if (!RUNNING.equals(timer.getStatus()) && !PAUSED.equals(timer.getStatus())) {
            throw new BadRequestException("Only a running or paused timer can be extended.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (PAUSED.equals(timer.getStatus())) {
            int remaining = timer.getRemainingAtPause() == null ? 0 : timer.getRemainingAtPause();
            timer.setRemainingAtPause(remaining + add);
        } else {
            // Extend from "now" if it had already run out, else from the current end.
            LocalDateTime base = timer.getEndsAt() != null && timer.getEndsAt().isAfter(now)
                    ? timer.getEndsAt() : now;
            timer.setEndsAt(base.plusSeconds(add));
            // Reopen if the extension revives an expired countdown.
            timer.setStatus(RUNNING);
        }
        timer.setDurationSeconds((timer.getDurationSeconds() == null ? 0 : timer.getDurationSeconds()) + add);
        timer.setUpdatedAt(now);
        timerRepository.save(timer);

        if (PHASE_CONTEST.equals(phase) && timer.getEndsAt() != null) {
            round.setSubmissionDeadline(timer.getEndsAt());
            roundRepository.save(round);
        }

        auditLogService.record(actorUserId, "EXTEND_TIMER", "ROUND", roundId, null,
                Map.of("phase", phase, "seconds", add));
        return toResponse(round, timer, now);
    }

    // ── Coordinator: stop (end now) ───────────────────────────────────────────

    @Transactional
    public RoundTimerResponse stop(Integer actorUserId, Integer eventId, Integer roundId, String phaseRaw) {
        String phase = normalizePhase(phaseRaw);
        Round round = requireRound(eventId, roundId);
        requireEventInProgress(round);
        RoundTimer timer = requireTimer(roundId, phase);
        if (!RUNNING.equals(timer.getStatus()) && !PAUSED.equals(timer.getStatus())) {
            throw new BadRequestException("Only a running or paused timer can be stopped.");
        }
        LocalDateTime now = LocalDateTime.now();
        timer.setStatus(STOPPED);
        timer.setEndsAt(now);
        timer.setPausedAt(null);
        timer.setRemainingAtPause(null);
        timer.setUpdatedAt(now);
        timerRepository.save(timer);

        if (PHASE_CONTEST.equals(phase)) {
            round.setSubmissionDeadline(now);
            roundRepository.save(round);
        }

        if (noticeClaimer.claim(roundId, phase, "STOPPED")) {
            fanOut(round, phase, "STOPPED", timer);
        }

        auditLogService.record(actorUserId, "STOP_TIMER", "ROUND", roundId, null, Map.of("phase", phase));
        return toResponse(round, timer, now);
    }

    // ── Any authenticated user: read state (+ lazy milestone fan-out) ─────────

    @Transactional
    public RoundTimerResponse getState(Integer eventId, Integer roundId, String phaseRaw) {
        String phase = normalizePhase(phaseRaw);
        Round round = requireRound(eventId, roundId);
        LocalDateTime now = LocalDateTime.now();

        RoundTimer timer = timerRepository.findByRound_RoundIdAndPhase(roundId, phase).orElse(null);
        if (timer == null) {
            return idleResponse(roundId, phase, now);
        }
        materializeMilestones(round, timer, now);
        return toResponse(round, timer, now);
    }

    // ── Gate helpers (no side effects) — called from Submission/Scoring ───────

    /** Throws unless the CONTEST timer is running with time remaining. */
    public void assertContestOpen(Integer roundId) {
        assertOpen(roundId, PHASE_CONTEST,
                "Time is up — submissions are closed for this round.",
                "The contest is paused — submissions are temporarily disabled.");
    }

    /** Throws unless the JUDGING timer is running with time remaining. */
    public void assertJudgingOpen(Integer roundId) {
        assertOpen(roundId, PHASE_JUDGING,
                "Time is up — scoring is closed for this round.",
                "Scoring is paused — please wait for the organizers to resume.");
    }

    /**
     * Submission details stay hidden until judging has been started once. After
     * that, paused or closed windows may still be viewed as read-only history.
     */
    public void assertJudgingStarted(Integer roundId) {
        RoundTimer timer = timerRepository.findByRound_RoundIdAndPhase(roundId, PHASE_JUDGING).orElse(null);
        if (timer == null || IDLE.equals(timer.getStatus())) {
            throw new BadRequestException("Scoring has not started for this round.");
        }
    }

    /** Normal add/remove is allowed only before the judging roster becomes live. */
    @Transactional(readOnly = true)
    public void assertJudgeAssignmentsMutable(Integer roundId) {
        RoundTimer timer = timerRepository.findByRound_RoundIdAndPhase(roundId, PHASE_JUDGING).orElse(null);
        if (timer != null && !IDLE.equals(timer.getStatus())) {
            throw new BadRequestException(
                    "Judge assignments are locked after judging starts. Pause or stop judging and use Replace Judge instead.");
        }
    }

    /** A controlled replacement is allowed only while no judging countdown is running. */
    @Transactional(readOnly = true)
    public void assertJudgeReplacementAllowed(Integer roundId) {
        RoundTimer timer = timerRepository.findByRound_RoundIdAndPhase(roundId, PHASE_JUDGING).orElse(null);
        if (timer == null || IDLE.equals(timer.getStatus())) {
            return;
        }
        if (RUNNING.equals(timer.getStatus())
                && timer.getEndsAt() != null
                && timer.getEndsAt().isAfter(LocalDateTime.now())) {
            throw new BadRequestException("Pause or stop judging before replacing a judge.");
        }
    }

    private void assertOpen(Integer roundId, String phase, String closedMsg, String pausedMsg) {
        RoundTimer timer = timerRepository.findByRound_RoundIdAndPhase(roundId, phase).orElse(null);
        if (timer == null || IDLE.equals(timer.getStatus())) {
            throw new BadRequestException(PHASE_JUDGING.equals(phase)
                    ? "Scoring has not started for this round."
                    : "The contest has not started for this round.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (RUNNING.equals(timer.getStatus()) && secondsBetween(now, timer.getEndsAt()) > 0) {
            return; // open
        }
        throw new BadRequestException(PAUSED.equals(timer.getStatus()) ? pausedMsg : closedMsg);
    }

    /** Strict transition guard: CONTEST must be over and the round must be score-ready. */
    private void assertJudgingReady(Round round, LocalDateTime now) {
        // event.status is already enforced by requireEventInProgress() in start().
        if (!"ACTIVE".equalsIgnoreCase(round.getStatus())) {
            throw new BadRequestException("Judging can only start for an ACTIVE round.");
        }

        RoundTimer contestTimer = timerRepository
                .findByRound_RoundIdAndPhase(round.getRoundId(), PHASE_CONTEST)
                .orElseThrow(() -> new BadRequestException(
                        "The CONTEST timer must finish before judging can start."));
        if (RUNNING.equals(contestTimer.getStatus())
                && secondsBetween(now, contestTimer.getEndsAt()) <= 0) {
            materializeMilestones(round, contestTimer, now);
        }
        if (!STOPPED.equals(contestTimer.getStatus()) && !EXPIRED.equals(contestTimer.getStatus())) {
            throw new BadRequestException("The CONTEST timer must be STOPPED or EXPIRED before judging can start.");
        }

        if (scoringCriteriaRepository
                .findAllByRound_RoundIdOrderByOrderNumber(round.getRoundId()).isEmpty()) {
            throw new BadRequestException("Configure scoring criteria before starting judging.");
        }

        List<Submission> submissions = submissionRepository.findAllByRound_RoundId(round.getRoundId());
        if (submissions.isEmpty()) {
            throw new BadRequestException("At least one submission is required before starting judging.");
        }
        var assignments = judgeAssignmentRepository
                .findAllByRound_RoundIdAndIsActiveTrue(round.getRoundId());
        if (assignments.isEmpty()) {
            throw new BadRequestException("Assign at least one judge before starting judging.");
        }
        Integer eventId = round.getEvent().getEventId();
        boolean uncoveredSubmission = submissions.stream().anyMatch(submission -> {
            Integer submissionTrackId = teamEventEntryRepository
                    .findByTeam_TeamIdAndEvent_EventId(submission.getTeam().getTeamId(), eventId)
                    .map(entry -> entry.getTrack())
                    .map(track -> track.getTrackId())
                    .orElse(null);
            return assignments.stream().noneMatch(assignment -> assignment.getTrack() == null
                    || (submissionTrackId != null
                    && Objects.equals(assignment.getTrack().getTrackId(), submissionTrackId)));
        });
        if (uncoveredSubmission) {
            throw new BadRequestException("Every submission must be covered by an active judge assignment.");
        }
    }

    // ── Milestone materialisation ──────────────────────────────────────────────

    private void materializeMilestones(Round round, RoundTimer timer, LocalDateTime now) {
        if (!RUNNING.equals(timer.getStatus())) {
            return; // only a live countdown produces new marks
        }
        long remaining = secondsBetween(now, timer.getEndsAt());
        int duration = timer.getDurationSeconds() == null ? 0 : timer.getDurationSeconds();

        // Build the eligible marks: "minutes remaining" (only those < duration),
        // optional half-time, and the terminal EXPIRED mark.
        Map<String, Long> marks = new LinkedHashMap<>();
        for (Integer m : parseMinutes(timer.getMilestoneMinutes())) {
            long thresh = m * 60L;
            if (thresh < duration) {
                marks.put("REM_" + m, thresh);
            }
        }
        if (Boolean.TRUE.equals(timer.getNotifyAtHalf()) && duration > 0) {
            marks.put("HALF", duration / 2L);
        }
        marks.put("EXPIRED", 0L);

        for (Map.Entry<String, Long> e : marks.entrySet()) {
            if (remaining <= e.getValue() && noticeClaimer.claim(round.getRoundId(), timer.getPhase(), e.getKey())) {
                fanOut(round, timer.getPhase(), e.getKey(), timer);
            }
        }

        // Persist the terminal transition so coordinator views + gates see EXPIRED.
        if (remaining <= 0) {
            timer.setStatus(EXPIRED);
            timer.setUpdatedAt(now);
            timerRepository.save(timer);
        }
    }

    private void fanOut(Round round, String phase, String key, RoundTimer timer) {
        Msg msg = messageFor(round, phase, key, timer);
        for (Integer uid : resolveAudience(round, phase)) {
            notificationService.createNotification(uid, msg.title(), msg.content(), NOTIF_TYPE);
        }
    }

    private record Msg(String title, String content) {}

    private Msg messageFor(Round round, String phase, String key, RoundTimer timer) {
        boolean contest = PHASE_CONTEST.equals(phase);
        String window = contest ? "submission" : "scoring";
        String tag = "[" + round.getName() + "] ";
        switch (key) {
            case "STARTED":
                return new Msg(
                        contest ? "Contest started" : "Scoring opened",
                        tag + (contest ? "Submission" : "Scoring") + " window: "
                                + formatDuration(timer.getDurationSeconds()) + ".");
            case "HALF":
                return new Msg("Halfway point",
                        tag + "Half of the " + window + " time has elapsed.");
            case "EXPIRED":
                return new Msg("Time's up", tag + "The " + window + " window has closed.");
            case "STOPPED":
                return new Msg("Stopped", tag + "The organizers closed the " + window + " window.");
            default: // REM_<minutes>
                String minutes = key.startsWith("REM_") ? key.substring(4) : key;
                return new Msg(minutes + " min left",
                        tag + minutes + " minutes left in the " + window + " window.");
        }
    }

    /** CONTEST → participants of approved teams in the event; JUDGING → assigned judges. */
    private Set<Integer> resolveAudience(Round round, String phase) {
        Set<Integer> ids = new LinkedHashSet<>();
        if (PHASE_CONTEST.equals(phase)) {
            Integer eventId = round.getEvent().getEventId();
            for (Track track : trackRepository.findAllByEvent_EventId(eventId)) {
                teamEventEntryRepository.findAllByTrack_TrackIdAndStatus(track.getTrackId(), "APPROVED").forEach(entry ->
                        teamMemberRepository.findByTeam_TeamId(entry.getTeam().getTeamId()).forEach(member -> {
                            User u = member.getUser();
                            if (isActiveApproved(u)) {
                                ids.add(u.getUserId());
                            }
                        }));
            }
        } else {
            judgeAssignmentRepository.findAllByRound_RoundIdAndIsActiveTrue(round.getRoundId()).forEach(ja -> {
                User j = ja.getJudge();
                if (isActiveApproved(j)) {
                    ids.add(j.getUserId());
                }
            });
        }
        return ids;
    }

    // ── Response mapping ────────────────────────────────────────────────────────

    private RoundTimerResponse toResponse(Round round, RoundTimer timer, LocalDateTime now) {
        String effective = effectiveStatus(timer, now);
        return RoundTimerResponse.builder()
                .roundId(round.getRoundId())
                .phase(timer.getPhase())
                .status(effective)
                .durationSeconds(timer.getDurationSeconds())
                .startedAt(timer.getStartedAt())
                .endsAt(timer.getEndsAt())
                .remainingSeconds(remainingSeconds(timer, now))
                .serverNow(now)
                .milestoneMinutes(parseMinutes(timer.getMilestoneMinutes()))
                .notifyAtHalf(timer.getNotifyAtHalf())
                .build();
    }

    private RoundTimerResponse idleResponse(Integer roundId, String phase, LocalDateTime now) {
        return RoundTimerResponse.builder()
                .roundId(roundId)
                .phase(phase)
                .status(IDLE)
                .remainingSeconds(0L)
                .serverNow(now)
                .milestoneMinutes(DEFAULT_MILESTONE_MINUTES)
                .notifyAtHalf(true)
                .build();
    }

    private String effectiveStatus(RoundTimer timer, LocalDateTime now) {
        if (RUNNING.equals(timer.getStatus()) && secondsBetween(now, timer.getEndsAt()) <= 0) {
            return EXPIRED;
        }
        return timer.getStatus();
    }

    private long remainingSeconds(RoundTimer timer, LocalDateTime now) {
        if (RUNNING.equals(timer.getStatus())) {
            return Math.max(0, secondsBetween(now, timer.getEndsAt()));
        }
        if (PAUSED.equals(timer.getStatus())) {
            return timer.getRemainingAtPause() == null ? 0 : Math.max(0, timer.getRemainingAtPause());
        }
        return 0;
    }

    // ── Small helpers ───────────────────────────────────────────────────────────

    private long secondsBetween(LocalDateTime from, LocalDateTime to) {
        if (to == null) {
            return 0;
        }
        return Duration.between(from, to).getSeconds();
    }

    private Round requireRound(Integer eventId, Integer roundId) {
        return roundRepository.findByIdAndEventId(roundId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Round " + roundId + " not found in event " + eventId));
    }

    /** Every timer mutation (start/pause/resume/extend/stop, either phase) requires
     * a running event — coordinators drive contest/judging windows only once the
     * event has actually started. */
    private void requireEventInProgress(Round round) {
        if (!EVENT_STATUS_IN_PROGRESS.equalsIgnoreCase(round.getEvent().getStatus())) {
            throw new BadRequestException(
                    "Timers can only be operated while the event is IN_PROGRESS.");
        }
    }

    private RoundTimer requireTimer(Integer roundId, String phase) {
        return timerRepository.findByRound_RoundIdAndPhase(roundId, phase)
                .orElseThrow(() -> new BadRequestException(
                        "No " + phase + " timer has been started for this round."));
    }

    private String normalizePhase(String phase) {
        if (phase == null || phase.isBlank()) {
            throw new BadRequestException("phase is required (CONTEST or JUDGING).");
        }
        String p = phase.trim().toUpperCase();
        if (!PHASE_CONTEST.equals(p) && !PHASE_JUDGING.equals(p)) {
            throw new BadRequestException("phase must be CONTEST or JUDGING.");
        }
        return p;
    }

    private boolean isActiveApproved(User u) {
        return u != null && Boolean.TRUE.equals(u.getIsApproved()) && Boolean.TRUE.equals(u.getIsActive());
    }

    private List<Integer> parseMinutes(String csv) {
        if (csv == null || csv.isBlank()) {
            return DEFAULT_MILESTONE_MINUTES;
        }
        List<Integer> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                int v = Integer.parseInt(t);
                if (v > 0 && !out.contains(v)) {
                    out.add(v);
                }
            } catch (NumberFormatException ignored) {
                // skip malformed entries
            }
        }
        out.sort((a, b) -> b - a); // descending: 30, 15, 5, 1
        return out.isEmpty() ? DEFAULT_MILESTONE_MINUTES : out;
    }

    private List<Integer> sanitizeMinutes(List<Integer> minutes) {
        List<Integer> out = new ArrayList<>();
        for (Integer m : minutes) {
            if (m != null && m > 0 && !out.contains(m)) {
                out.add(m);
            }
        }
        out.sort((a, b) -> b - a);
        return out.isEmpty() ? DEFAULT_MILESTONE_MINUTES : out;
    }

    private String toCsv(List<Integer> minutes) {
        StringBuilder sb = new StringBuilder();
        for (Integer m : minutes) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(m);
        }
        return sb.toString();
    }

    private String formatDuration(Integer seconds) {
        if (seconds == null || seconds <= 0) {
            return "0 min";
        }
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        if (h > 0 && m > 0) {
            return h + "h " + m + "m";
        }
        if (h > 0) {
            return h + "h";
        }
        if (m > 0) {
            return m + " min";
        }
        return seconds + " sec";
    }
}
