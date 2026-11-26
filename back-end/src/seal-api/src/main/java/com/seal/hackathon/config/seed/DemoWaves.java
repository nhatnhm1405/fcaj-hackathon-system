package com.seal.hackathon.config.seed;

import com.seal.hackathon.entity.AuditLog;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.JudgeAssignment;
import com.seal.hackathon.entity.Round;
import com.seal.hackathon.entity.ScoringCriteria;
import com.seal.hackathon.entity.Submission;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.TeamEventEntry;
import com.seal.hackathon.entity.TeamMember;
import com.seal.hackathon.entity.Track;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.AuditLogRepository;
import com.seal.hackathon.repository.HackathonEventRepository;
import com.seal.hackathon.repository.JudgeAssignmentRepository;
import com.seal.hackathon.repository.MentorAssignmentRepository;
import com.seal.hackathon.repository.RoleRepository;
import com.seal.hackathon.repository.RoundRepository;
import com.seal.hackathon.repository.ScoreRepository;
import com.seal.hackathon.repository.ScoringCriteriaRepository;
import com.seal.hackathon.repository.SubmissionRepository;
import com.seal.hackathon.repository.TeamEventEntryRepository;
import com.seal.hackathon.repository.TeamMemberRepository;
import com.seal.hackathon.repository.TrackRepository;
import com.seal.hackathon.repository.UserRepository;
import com.seal.hackathon.service.RoundTimerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fills one real event with the crowd a live demo needs, one wave at a time, so a
 * presenter can walk a single event from registration to awards:
 * <pre>
 *   REGISTRATION (event OPEN)        teams, pending teams, solo/pair teams and free agents
 *                                    to group, judges and mentors, plus any missing tracks,
 *                                    the single final round and its criteria
 *   SUBMISSIONS  (contest running)   every demo team submits
 *   SCORES       (judging running)   every score is filled in except a real judge's
 *                                    score for a real team, which is scored live
 * </pre>
 * Everything the presenter does live (approving, SETUP, the track draw, leftover
 * grouping, timers, their own team's submission and score, finalizing, prizes) goes
 * through the normal application. Demo accounts use the reserved
 * {@value #DEMO_DOMAIN} domain and have no password, so nobody can sign in to them
 * and no mail can reach anyone. Existing data is only added to, never changed or
 * deleted, except that a lone round becomes the final round.
 */
@Component
@RequiredArgsConstructor
public class DemoWaves {

    public static final String DEMO_DOMAIN = "@seal-demo.invalid";
    public static final String ACTION_PREFIX = "DEMO_WAVE_";
    static final int TEAMS_PER_TRACK = 4;
    static final int PENDING_TEAMS = 2;
    static final int FREE_AGENTS = 2;
    static final int FINAL_JUDGES = 3;
    static final String ROUND_NAME = "Vòng thi";
    /** Small public repositories, so AI assist can read a demo submission. */
    static final List<String> DEMO_REPOS = List.of(
            "https://github.com/expressjs/cors",
            "https://github.com/sindresorhus/slugify",
            "https://github.com/chalk/chalk");
    private static final String[][] JUDGES = {
            {"Nguyễn Văn Ronaldo", "INTERNAL"}, {"Trần Văn Haaland", "INTERNAL"}, {"Lê Văn Messi", "GUEST"},
            {"Pham Van Salah", "INTERNAL"}, {"Vu Van Mbappe", "INTERNAL"},
    };
    private static final String[] MENTORS = {
            "Lê Minh Gia Mẫn", "Hồ Văn Mendes", "Phạm Văn Guardiola", "Vũ Văn Klopp", "Bùi Văn Ancelotti",
    };

    public enum Wave { REGISTRATION, SUBMISSIONS, SCORES }

    public record WaveState(Wave wave, boolean done, String blockedBy) {}

    public record EventWaves(int eventId, String eventName, String eventStatus, List<WaveState> waves) {}

    public record WaveResult(int eventId, Wave wave, Map<String, Integer> added) {}

    private final DemoFixtures fx;
    private final HackathonEventRepository eventRepo;
    private final TrackRepository trackRepo;
    private final RoundRepository roundRepo;
    private final ScoringCriteriaRepository criteriaRepo;
    private final TeamEventEntryRepository entryRepo;
    private final TeamMemberRepository memberRepo;
    private final JudgeAssignmentRepository judgeAssignRepo;
    private final MentorAssignmentRepository mentorAssignRepo;
    private final SubmissionRepository submissionRepo;
    private final ScoreRepository scoreRepo;
    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final AuditLogRepository auditLogRepo;
    private final RoundTimerService timers;

    /** Every published event that is still running, with which waves are done or why one cannot run yet. */
    @Transactional(readOnly = true)
    public List<EventWaves> list() {
        return eventRepo.findAllRegularEvents(Sort.by("eventId")).stream()
                .filter(event -> !outOfDemo(event))
                .map(event -> new EventWaves(event.getEventId(), event.getName(), event.getStatus(),
                        Arrays.stream(Wave.values()).map(wave -> state(event, wave)).toList()))
                .toList();
    }

    @Transactional
    public WaveResult run(int eventId, Wave wave, int actorId) {
        // Serialize waves across requests and app instances, like the demo phases.
        roleRepo.lockByRoleName("EVENT_COORDINATOR")
                .orElseThrow(() -> new IllegalStateException("Coordinator role is missing."));
        HackathonEvent event = eventRepo.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        WaveState state = state(event, wave);
        if (state.done()) {
            throw new BadRequestException("This demo wave has already run for this event.");
        }
        if (state.blockedBy() != null) {
            throw new BadRequestException(state.blockedBy());
        }
        User actor = userRepo.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorId));
        Map<String, Integer> added = switch (wave) {
            case REGISTRATION -> registration(event);
            case SUBMISSIONS -> submissions(event, singleRound(event));
            case SCORES -> scores(event, singleRound(event));
        };
        auditLogRepo.save(AuditLog.builder()
                .actor(actor).action(ACTION_PREFIX + wave.name())
                .targetType("EVENT").targetId(eventId).reason(added.toString())
                .build());
        return new WaveResult(eventId, wave, added);
    }

    // ── Gates ────────────────────────────────────────────────────────

    private WaveState state(HackathonEvent event, Wave wave) {
        boolean done = isDone(event, wave);
        return new WaveState(wave, done, done ? null : blocker(event, wave));
    }

    private boolean isDone(HackathonEvent event, Wave wave) {
        return auditLogRepo.existsByActionAndTargetTypeAndTargetId(
                ACTION_PREFIX + wave.name(), "EVENT", event.getEventId());
    }

    private String blocker(HackathonEvent event, Wave wave) {
        if (outOfDemo(event)) {
            return "The event is " + event.getStatus() + ".";
        }
        List<Round> rounds = roundRepo.findAllByEvent_EventIdOrderByOrderNumber(event.getEventId());
        if (rounds.size() > 1) {
            return "The demo uses exactly one round, the final round. This event has " + rounds.size()
                    + " rounds: delete the others first.";
        }
        if (wave == Wave.REGISTRATION) {
            return "OPEN".equalsIgnoreCase(event.getStatus()) ? null
                    : "Open registration first (the event must be OPEN).";
        }
        Wave previous = Wave.values()[wave.ordinal() - 1];
        if (!isDone(event, previous)) {
            return "Run the " + previous.name().toLowerCase(Locale.ROOT) + " wave first.";
        }
        if (!"IN_PROGRESS".equalsIgnoreCase(event.getStatus())) {
            return "Start the event first (the event must be IN_PROGRESS).";
        }
        if (rounds.isEmpty()) {
            return "The event has no round.";
        }
        Integer roundId = rounds.getFirst().getRoundId();
        try {
            if (wave == Wave.SUBMISSIONS) {
                timers.assertContestOpen(roundId);
            } else {
                timers.assertJudgingOpen(roundId);
            }
        } catch (BadRequestException closed) {
            return (wave == Wave.SUBMISSIONS ? "Start the contest timer first. " : "Start the judging timer first. ")
                    + closed.getMessage();
        }
        return null;
    }

    /** Draft events are not published yet, and closed ones are history: demo data never goes into either. */
    private static boolean outOfDemo(HackathonEvent event) {
        String status = event.getStatus();
        return "DRAFT".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status)
                || "CANCELLED".equalsIgnoreCase(status);
    }

    private Round singleRound(HackathonEvent event) {
        return roundRepo.findAllByEvent_EventIdOrderByOrderNumber(event.getEventId()).getFirst();
    }

    // ── Wave 1: registration and event setup ─────────────────────────

    private Map<String, Integer> registration(HackathonEvent event) {
        int eventId = event.getEventId();
        Map<String, Integer> added = new LinkedHashMap<>();
        Accounts accounts = new Accounts(eventId);

        List<Track> tracks = new ArrayList<>(trackRepo.findAllByEvent_EventId(eventId));
        if (tracks.isEmpty()) {
            for (String name : DemoScenario.TRACKS) {
                tracks.add(fx.track(event, name));
            }
            added.put("tracks", tracks.size());
        }

        List<Round> rounds = roundRepo.findAllByEvent_EventIdOrderByOrderNumber(eventId);
        Round round;
        if (rounds.isEmpty()) {
            round = fx.round(event, 1, ROUND_NAME, true, "PENDING",
                    event.getStartDate(), event.getEndDate(), event.getEndDate(), null);
            added.put("rounds", 1);
        } else {
            round = rounds.getFirst();
            if (!Boolean.TRUE.equals(round.getIsFinal())) {
                round.setIsFinal(true);
                round.setTopNAdvance(null);
                roundRepo.save(round);
            }
        }
        if (criteriaRepo.findAllByRound_RoundIdOrderByOrderNumber(round.getRoundId()).isEmpty()) {
            String[][] criteria = DemoScenario.CRITERIA;
            for (int i = 0; i < criteria.length; i++) {
                fx.criteria(event, round, criteria[i][0], Double.parseDouble(criteria[i][1]), 10.0, i + 1);
            }
            added.put("criteria", criteria.length);
        }

        int mentors = 0;
        for (Track track : tracks) {
            if (mentorAssignRepo.findAllByTrack_TrackIdAndIsActiveTrue(track.getTrackId()).isEmpty()) {
                User mentor = accounts.staff("mentor", MENTORS[mentors % MENTORS.length], null, "MENTOR");
                fx.assignMentor(mentor, track);
                mentors++;
            }
        }
        added.put("mentors", mentors);

        long demoJudges = judgeAssignRepo.findAllByRound_RoundIdAndIsActiveTrue(round.getRoundId()).stream()
                .filter(assignment -> isDemo(assignment.getJudge()))
                .count();
        int judges = 0;
        for (long i = demoJudges; i < FINAL_JUDGES; i++) {
            String[] judge = JUDGES[judges % JUDGES.length];
            fx.assignJudge(accounts.staff("judge", judge[0], judge[1], "JUDGE"), round, null);
            judges++;
        }
        added.put("judges", judges);

        TeamNames names = new TeamNames(eventId);
        int teams = TEAMS_PER_TRACK * tracks.size();
        for (int i = 0; i < teams; i++) {
            fx.team(event, null, names.next(""), "APPROVED", accounts.student(), accounts.students(2));
        }
        added.put("teams", teams);
        for (int i = 0; i < PENDING_TEAMS; i++) {
            fx.team(event, null, names.next(" (pending)"), "PENDING", accounts.student(), accounts.students(2));
        }
        added.put("pendingTeams", PENDING_TEAMS);
        // A solo team, and a pair, are grown into full teams by leftover grouping in SETUP.
        fx.team(event, null, names.next(" (solo)"), "APPROVED", accounts.student(), List.of());
        fx.team(event, null, names.next(" (pair)"), "APPROVED", accounts.student(), accounts.students(1));
        added.put("smallTeams", 2);
        for (int i = 0; i < FREE_AGENTS; i++) {
            accounts.student();
        }
        added.put("freeAgents", FREE_AGENTS);
        added.put("participants", accounts.students);
        return added;
    }

    // ── Wave 2: submissions ──────────────────────────────────────────

    private Map<String, Integer> submissions(HackathonEvent event, Round round) {
        int submitted = 0;
        int realTeams = 0;
        for (TeamEventEntry entry : entryRepo.findAllByEvent_EventIdAndStatus(event.getEventId(), "APPROVED")) {
            Team team = entry.getTeam();
            if (entry.getTrack() == null
                    || submissionRepo.findByTeam_TeamIdAndRound_RoundId(team.getTeamId(), round.getRoundId()).isPresent()) {
                continue;
            }
            List<TeamMember> members = memberRepo.findByTeam_TeamId(team.getTeamId());
            if (!isDemoTeam(members)) {
                realTeams++;
                continue;
            }
            User leader = members.stream().filter(m -> "LEADER".equalsIgnoreCase(m.getMemberRole()))
                    .findFirst().orElse(members.getFirst()).getUser();
            submissionRepo.save(Submission.builder()
                    .team(team).round(round).submittedBy(leader).status("SUBMITTED")
                    .repoUrl(DEMO_REPOS.get(submitted % DEMO_REPOS.size()))
                    .description("Demo submission by " + team.getName() + ".")
                    .build());
            submitted++;
        }
        Map<String, Integer> added = new LinkedHashMap<>();
        added.put("submissions", submitted);
        added.put("realTeamsLeft", realTeams);
        return added;
    }

    // ── Wave 3: scores ───────────────────────────────────────────────

    private Map<String, Integer> scores(HackathonEvent event, Round round) {
        List<ScoringCriteria> criteria = criteriaRepo.findAllByRound_RoundIdOrderByOrderNumber(round.getRoundId());
        List<JudgeAssignment> panel = judgeAssignRepo.findAllByRound_RoundIdAndIsActiveTrue(round.getRoundId());
        int scored = 0;
        int leftForLive = 0;
        for (Submission submission : submissionRepo.findAllByRound_RoundId(round.getRoundId())) {
            Team team = submission.getTeam();
            TeamEventEntry entry = entryRepo.findByTeam_TeamIdAndEvent_EventId(team.getTeamId(), event.getEventId())
                    .orElse(null);
            if (entry == null || "DISQUALIFIED".equalsIgnoreCase(entry.getStatus())) {
                continue;
            }
            boolean demoTeam = isDemoTeam(memberRepo.findByTeam_TeamId(team.getTeamId()));
            for (JudgeAssignment assignment : panel) {
                if (!covers(assignment, entry)) {
                    continue;
                }
                User judge = assignment.getJudge();
                if (scoreRepo.existsBySubmission_SubmissionIdAndJudge_UserId(
                        submission.getSubmissionId(), judge.getUserId())) {
                    continue;
                }
                if (!demoTeam && !isDemo(judge)) {
                    leftForLive++;
                    continue;
                }
                for (ScoringCriteria c : criteria) {
                    fx.score(submission, judge, c, scoreFor(team, judge, c));
                }
                scored++;
            }
        }
        Map<String, Integer> added = new LinkedHashMap<>();
        added.put("scoredSubmissionJudgePairs", scored);
        added.put("leftForLiveJudging", leftForLive);
        return added;
    }

    private static boolean covers(JudgeAssignment assignment, TeamEventEntry entry) {
        return assignment.getTrack() == null
                || (entry.getTrack() != null && assignment.getTrack().getTrackId().equals(entry.getTrack().getTrackId()));
    }

    /** Deterministic, varied scores: a team strength plus a small per-judge spread, in half points. */
    static double scoreFor(Team team, User judge, ScoringCriteria criteria) {
        double max = criteria.getMaxScore().doubleValue();
        double strength = 0.55 + 0.40 * Math.floorMod(team.getTeamId() * 37 + 11, 100) / 100.0;
        double spread = (Math.floorMod(judge.getUserId() * 13 + criteria.getCriteriaId() * 7, 11) - 5) / 100.0;
        double ratio = Math.max(0.3, Math.min(1.0, strength + spread));
        return Math.round(max * ratio * 2) / 2.0;
    }

    // ── Demo accounts ────────────────────────────────────────────────

    public static boolean isDemo(User user) {
        return user.getEmail() != null && user.getEmail().toLowerCase(Locale.ROOT).endsWith(DEMO_DOMAIN);
    }

    private static boolean isDemoTeam(List<TeamMember> members) {
        return !members.isEmpty() && members.stream().allMatch(m -> isDemo(m.getUser()));
    }

    /**
     * Accounts for one event: {@code <name>+event<id>@seal-demo.invalid}. That suffix is
     * what scopes demo free agents to their own event's leftover grouping.
     */
    private final class Accounts {
        private final int eventId;
        private final Map<String, Integer> sequence = new LinkedHashMap<>();
        private int students;

        Accounts(int eventId) {
            this.eventId = eventId;
        }

        private String nextEmail(String prefix) {
            String email;
            do {
                int n = sequence.merge(prefix, 1, Integer::sum);
                email = prefix + n + "+event" + eventId + DEMO_DOMAIN;
            } while (userRepo.existsByEmail(email));
            return email;
        }

        User staff(String prefix, String name, String judgeType, String role) {
            User user = fx.loginlessUser(nextEmail(prefix), name, "STAFF", judgeType, 0);
            fx.grant(user, role, eventId);
            return user;
        }

        User student() {
            String name = DemoScenario.PLAYERS[students % DemoScenario.PLAYERS.length];
            students++;
            String type = students % 3 == 0 ? "EXTERNAL_STUDENT" : "FPT_STUDENT";
            return fx.loginlessUser(nextEmail("p"), name, type, null, 500 + students);
        }

        List<User> students(int count) {
            List<User> list = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                list.add(student());
            }
            return list;
        }
    }

    /** Club names not yet used by a team in this event. */
    private final class TeamNames {
        private final int eventId;
        private int next;

        TeamNames(int eventId) {
            this.eventId = eventId;
        }

        String next(String suffix) {
            while (true) {
                String[] clubs = DemoScenario.CLUBS;
                int i = next++;
                String base = clubs[i % clubs.length] + (i < clubs.length ? "" : " " + (i / clubs.length + 1));
                String name = base + suffix;
                if (!entryRepo.existsByEventIdAndNormalizedName(eventId, name.trim().toUpperCase(Locale.ROOT))) {
                    return name;
                }
            }
        }
    }
}
