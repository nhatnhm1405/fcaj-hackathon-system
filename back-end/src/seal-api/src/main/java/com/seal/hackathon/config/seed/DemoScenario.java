package com.seal.hackathon.config.seed;

import com.seal.hackathon.dto.request.AutoGeneratePrizesRequest;
import com.seal.hackathon.entity.HackathonEvent;
import com.seal.hackathon.entity.Round;
import com.seal.hackathon.entity.ScoringCriteria;
import com.seal.hackathon.entity.Submission;
import com.seal.hackathon.entity.Team;
import com.seal.hackathon.entity.Track;
import com.seal.hackathon.entity.User;
import com.seal.hackathon.service.HackathonEventService;
import com.seal.hackathon.service.PrizeService;
import com.seal.hackathon.service.RoundResultService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds one demo event as a layered pipeline. Each scenario is a cut-point:
 * <pre>
 *   S1  accounts + event OPEN + structure + forming teams (incl. solo/pairs for
 *       grouping) — everything from here on (SETUP config, leftover grouping,
 *       track draw, starting the event, submitting, scoring) is demoed live
 *       through the app itself rather than pre-seeded.
 *   S25 + prelim FINALIZED + final round fully scored, no results — ready to
 *        calculate the final ranking then award prizes            (IN_PROGRESS)
 *   S3  = the exact S25 cut-point, then the production workflow has calculated
 *         and published the final ranking, generated and announced prizes, and
 *         completed the event                                     (COMPLETED)
 * </pre>
 * Statuses are set to match each cut so the snapshot is always consistent with the
 * event state machine. Scoring is deterministic (a pure function of team strength),
 * so ranks/prizes are reproducible.
 */
@Component
@RequiredArgsConstructor
public class DemoScenario {

    private static final Logger log = LoggerFactory.getLogger(DemoScenario.class);

    public static final String DEMO_EVENT_NAME = "SEAL Summer 2026";
    /** Seeded in every scenario (incl. S0) — used as the "already seeded?" guard. */
    public static final String COORDINATOR_EMAIL = "coordinator@fpt.edu.vn";
    private static final int YEAR = 2026;
    private static final int MEMBERS_PER_TEAM = 3;   // 1 leader + 2 members
    private static final int TOP_PER_TRACK_ADVANCE = 2;  // top 2 of EACH track advance to the final
    private static final int PRELIM_JUDGES_PER_TRACK = 2;
    private static final double MAX_SCORE = 10.0;

    // name + weight; max score is MAX_SCORE for all (see requirements §6.2)
    private static final String[][] CRITERIA = {
            {"Ý tưởng", "1.0"}, {"Kỹ thuật", "1.5"}, {"UI/UX", "1.0"},
            {"Hoàn thiện", "1.0"}, {"Trình bày", "0.5"},
    };
    // Two judge-evaluation targets per prelim submission, ordered by team strength.
    // Histogram bins 20-30 .. 90-100 therefore contain exactly 1,2,3,5,6,8,3,2 samples.
    private static final double[] PRELIM_EVALUATION_TARGETS = {
            25,
            34, 38,
            42, 46, 49,
            52, 54, 56, 58, 59,
            62, 64, 65, 67, 68, 69,
            72, 73, 74, 75, 76, 77, 78, 79,
            82, 86, 89,
            93, 97,
    };
    // 4 tracks (see requirements §5). TEAMS_PER_TRACK[i] teams in TRACKS[i] → 15 teams total.
    // Prelim takes top 2 of each track → 8 finalists; final ranks them, top 3 get prizes.
    private static final String[] TRACKS = {"Web Application", "AI Solution", "Education Tech", "Social Impact"};
    private static final int[] TEAMS_PER_TRACK = {4, 4, 4, 3};
    /** Team names — football clubs, cycled if there are more teams than names. */
    private static final String[] CLUBS = {
            "Arsenal", "Barcelona", "Real Madrid", "Bayern Munich",
            "Liverpool", "Manchester City", "Chelsea", "Juventus",
            "PSG", "AC Milan", "Inter Milan", "Tottenham",
            "Napoli", "Atlético Madrid", "Borussia Dortmund", "Ajax",
    };
    /** Extra full-roster teams that bring S1 from its 6 special cases to 15 teams. */
    private static final String[] S1_EXTRA_APPROVED_TEAMS = {
            "Liverpool", "Manchester City", "PSG", "AC Milan", "Inter Milan",
            "Tottenham", "Napoli", "Atlético Madrid", "Borussia Dortmund",
    };
    /** Participant names — footballers, cycled if there are more players than names. */
    private static final String[] PLAYERS = {
            "Lionel Messi", "Cristiano Ronaldo", "Kylian Mbappé", "Erling Haaland",
            "Kevin De Bruyne", "Vinícius Júnior", "Mohamed Salah", "Harry Kane",
            "Robert Lewandowski", "Luka Modrić", "Neymar Jr", "Sadio Mané",
            "Bukayo Saka", "Jude Bellingham", "Rodri", "Bernardo Silva",
            "Phil Foden", "Martin Ødegaard", "Rafael Leão", "Federico Valverde",
            "Pedri", "Gavi", "Jamal Musiala", "Florian Wirtz",
            "Antoine Griezmann", "Toni Kroos", "Virgil van Dijk", "Achraf Hakimi",
            "Lautaro Martínez", "Victor Osimhen", "Bruno Fernandes", "Son Heung-min",
            "Declan Rice", "Joško Gvardiol", "Alphonso Davies", "Nico Williams",
            "Cole Palmer", "Lamine Yamal", "Khvicha Kvaratskhelia", "Dušan Vlahović",
            "Enzo Fernández", "Aurélien Tchouaméni", "Randal Kolo Muani", "Ousmane Dembélé",
            "Trent Alexander-Arnold", "Marcus Rashford", "Riyad Mahrez", "Serge Gnabry",
    };

    private final DemoFixtures fx;
    private final RoundResultService roundResultService;
    private final PrizeService prizeService;
    private final HackathonEventService hackathonEventService;

    private int participantSeq = 0;
    private int maxStrength = 2;

    @Transactional
    public void seed(String scenario) {
        // ── 1. ACCOUNTS (every scenario) ─────────────────────────────
        User coordinator = fx.user(COORDINATOR_EMAIL, "Event Coordinator", "STAFF", null);
        fx.grant(coordinator, "EVENT_COORDINATOR", null);
        User judge1 = staff("judge1@fpt.edu.vn", "Nguyễn Văn Ronaldo", "INTERNAL", "JUDGE");
        User judge2 = staff("judge2@fpt.edu.vn", "Trần Văn Haaland", "INTERNAL", "JUDGE");
        User judge3 = staff("judge3@fpt.edu.vn", "Pham Van Salah", "INTERNAL", "JUDGE");
        User judge4 = staff("judge4@fpt.edu.vn", "Vu Van Mbappe", "INTERNAL", "JUDGE");
        User judge5 = staff("judge5@fpt.edu.vn", "Đỗ Văn Zidane", "INTERNAL", "JUDGE");
        User judge6 = staff("judge6@fpt.edu.vn", "Bùi Văn Iniesta", "INTERNAL", "JUDGE");
        User judge7 = staff("judge7@fpt.edu.vn", "Ngô Văn Xavi", "INTERNAL", "JUDGE");
        User judge8 = staff("judge8@fpt.edu.vn", "Dương Văn Kaká", "INTERNAL", "JUDGE");
        User guestJudge = staff("guestjudge@gmail.com", "Lê Văn Messi", "GUEST", "JUDGE");
        // One mentor per track (business rule: a mentor manages exactly one track per event).
        User mentor1 = staff("mentor1@fpt.edu.vn", "Lê Minh Gia Mẫn", null, "MENTOR");
        User mentor2 = staff("mentor2@fpt.edu.vn", "Hồ Văn Mendes", null, "MENTOR");
        User mentor3 = staff("mentor3@fpt.edu.vn", "Phạm Văn Guardiola", null, "MENTOR");
        User mentor4 = staff("mentor4@fpt.edu.vn", "Vũ Văn Klopp", null, "MENTOR");
        // spare accounts to demo live actions (register/create team) alongside the seed
        fx.user("leader1@fpt.edu.vn", "Hoàng Văn Neymar Jr.", "FPT_STUDENT", null);
        fx.user("member1@fpt.edu.vn", "Đinh Văn Kane", "FPT_STUDENT", null);

        // ── 2-4. EVENT + STRUCTURE ───────────────────────────────────
        Window w = windowFor(scenario);
        HackathonEvent event = fx.event(DEMO_EVENT_NAME, "SUMMER", YEAR,
                eventStatusFor(scenario), "RANDOM", w.regStart, w.regEnd, w.start, w.end);

        List<Track> tracks = new ArrayList<>();
        for (String name : TRACKS) {
            tracks.add(fx.track(event, name));
        }
        Round prelim = fx.round(event, 1, "Vòng loại", false, roundStatusFor(scenario, true),
                w.start, w.start.plusDays(7), w.start.plusDays(6), TOP_PER_TRACK_ADVANCE);
        Round finalRound = fx.round(event, 2, "Vòng chung kết", true, roundStatusFor(scenario, false),
                w.start.plusDays(8), w.start.plusDays(14), w.start.plusDays(13), null);
        List<ScoringCriteria> prelimCriteria = criteriaFor(event, prelim);
        List<ScoringCriteria> finalCriteria = criteriaFor(event, finalRound);
        // ── S1: forming teams (no tracks yet — assigned at SETUP) ────
        if ("S1".equals(scenario)) {
            fx.team(event, null, "Arsenal", "APPROVED", participant(), members(2));
            fx.team(event, null, "Barcelona", "APPROVED", participant(), members(2));
            fx.team(event, null, "Real Madrid (pending)", "PENDING", participant(), members(2));
            fx.team(event, null, "Chelsea (solo)", "APPROVED", participant(), List.of());       // solo → free agent for grouping
            fx.team(event, null, "Juventus (solo)", "APPROVED", participant(), List.of());      // solo → free agent for grouping
            fx.team(event, null, "Bayern (pair)", "APPROVED", participant(), members(1));        // pair → grown in place on grouping
            // Nine regular squads bring the forming-stage roster to 15 teams total
            // without removing the pending/solo/pair cases above.
            for (String teamName : S1_EXTRA_APPROVED_TEAMS) {
                fx.team(event, null, teamName, "APPROVED", participant(), members(2));
            }
            // Teamless registrants: approved & active students who never joined a squad.
            // SETUP leftover-grouping sweeps these up too (see LeftoverGroupingService).
            for (int i = 0; i < 3; i++) {
                participant();
            }
            log.info("[demo] S1 seeded — OPEN event: 15 teams (14 approved, 1 pending; 2 solo + 1 pair)"
                    + " + 3 teamless registrants for grouping.");
            return;
        }

        // One mentor per track — a mentor manages exactly one track per event.
        List<User> trackMentors = List.of(mentor1, mentor2, mentor3, mentor4);
        for (int t = 0; t < tracks.size(); t++) {
            fx.assignMentor(trackMentors.get(t), tracks.get(t));
        }

        // ── 5-6. APPROVED ROSTER IN TRACKS + judges + submissions ────
        List<Slot> slots = new ArrayList<>();
        int numTracks = tracks.size();
        int totalTeams = 0;
        for (int c : TEAMS_PER_TRACK) totalTeams += c;
        maxStrength = totalTeams;                 // normalizes strength to [0,1]
        // Strength = totalTeams − (seed*numTracks + trackIdx): every track's #1 seed
        // outranks every #2 seed, so the final's top 3 are three DIFFERENT track winners.
        for (int t = 0; t < numTracks; t++) {
            Track track = tracks.get(t);
            for (int seed = 0; seed < TEAMS_PER_TRACK[t]; seed++) {
                User leader = participant();
                String teamName = CLUBS[slots.size() % CLUBS.length];
                Team team = fx.team(event, track, teamName, "APPROVED", leader, members(2));
                int strength = totalTeams - (seed * numTracks + t);
                slots.add(new Slot(team, track, leader, strength));
            }
        }
        // Prelim judges score per track. A judge may cover only one track in the
        // same round, so the seeded prelim roster keeps judge -> round unique.
        List<User> prelimJudgePool = List.of(
                judge1, judge2, judge3, judge4, judge5, judge6, judge7, judge8);
        int requiredPrelimJudges = tracks.size() * PRELIM_JUDGES_PER_TRACK;
        if (requiredPrelimJudges > prelimJudgePool.size()) {
            throw new IllegalStateException("Not enough seeded judges for prelim tracks.");
        }
        Map<Integer, List<User>> prelimJudgesByTrack = new LinkedHashMap<>();
        for (int t = 0; t < tracks.size(); t++) {
            Track track = tracks.get(t);
            int panelStart = t * PRELIM_JUDGES_PER_TRACK;
            List<User> panel = List.copyOf(prelimJudgePool.subList(
                    panelStart, panelStart + PRELIM_JUDGES_PER_TRACK));
            panel.forEach(judge -> fx.assignJudge(judge, prelim, track));
            prelimJudgesByTrack.put(track.getTrackId(), panel);
        }
        fx.assignJudge(judge1, finalRound, null);
        fx.assignJudge(judge2, finalRound, null);
        fx.assignJudge(guestJudge, finalRound, null);

        List<Submission> prelimSubs = new ArrayList<>();
        for (Slot s : slots) {
            prelimSubs.add(fx.submission(s.team, prelim, s.leader));
        }

        // Every judge assigned to each preliminary (round, track) cell has
        // finalized every criterion for every submission. S2 deliberately stops
        // before creating RoundResult rows so Calculate Rankings remains a live
        // coordinator demo rather than a pre-computed result.
        writeScoresByTrack(prelimSubs, slots, prelimCriteria, prelimJudgesByTrack);

        // ── 7-9. SCORES → RESULTS → PRIZES (S3) ──────────────────────
        // Prelim: score everyone, then rank WITHIN each track (mirrors RoundResultService
        // for non-final rounds) so rank ≤ topNAdvance means "top 2 of THIS track advance".
        List<Slot> advancing = new ArrayList<>();
        for (Track track : tracks) {
            List<Slot> inTrack = slots.stream()
                    .filter(s -> track.getTrackId().equals(s.track().getTrackId()))
                    .sorted(Comparator.comparingDouble((Slot s) -> total(s, prelimCriteria,
                            judgesForTrack(s.track(), prelimJudgesByTrack).size())).reversed())
                    .toList();
            for (int r = 0; r < inTrack.size(); r++) {
                Slot s = inTrack.get(r);
                fx.result(s.team, prelim, total(s, prelimCriteria,
                        judgesForTrack(s.track(), prelimJudgesByTrack).size()), r + 1, coordinator);
                if (r < TOP_PER_TRACK_ADVANCE) advancing.add(s);
            }
        }

        // Final: the 8 finalists compete in ONE global ranking; top 3 get prizes,
        // the rest are "reached the final" (qualifier). Non-advancing teams = participated.
        List<Submission> finalSubs = new ArrayList<>();
        for (Slot s : advancing) {
            finalSubs.add(fx.submission(s.team, finalRound, s.leader));
        }
        List<User> finalJudges = List.of(judge1, judge2, guestJudge);
        writeScores(finalSubs, advancing, finalCriteria, finalJudges);

        // Both S2.5 and S3 share this exact completed-scoring cut-point. Seed the
        // same expired phase timers for both; S3 then continues through the real
        // production services below instead of hand-writing their final state.
        fx.expiredTimer(prelim, "CONTEST", prelim.getStartTime(), prelim.getSubmissionDeadline());
        fx.expiredTimer(prelim, "JUDGING", prelim.getSubmissionDeadline(), prelim.getEndTime());
        fx.expiredTimer(finalRound, "CONTEST", finalRound.getStartTime(), finalRound.getSubmissionDeadline());
        fx.expiredTimer(finalRound, "JUDGING", finalRound.getSubmissionDeadline(), finalRound.getEndTime());

        // ── S2.5: STOP right before the final ranking is calculated. Prelim is
        // FINALIZED (finalists chosen); the final round is ACTIVE with every finalist's
        // submission fully scored by all judges — so JudgeScoringCompletenessService
        // passes. The coordinator's live flow runs on top: Calculate ranking
        // (finalizeRound) → auto-generate prizes → announce (award).
        if ("S25".equals(scenario)) {
            log.info("[demo] S2.5 seeded — IN_PROGRESS: prelim FINALIZED, {} finalists submitted & fully "
                    + "scored in the ACTIVE final round; awaiting final ranking calculation → prize award.",
                    advancing.size());
            return;
        }

        // ── S3: replay exactly what the coordinator/admin would do after loading S25.
        // Using production services keeps score normalization, result publication,
        // winner notifications, AWARD_PRIZE audit data, completion locks and participant
        // history snapshots identical to the real UI workflow.
        int finalResultCount = roundResultService
                .finalizeRound(event.getEventId(), finalRound.getRoundId(), coordinator.getUserId())
                .size();
        roundResultService.publishResults(event.getEventId(), finalRound.getRoundId());

        AutoGeneratePrizesRequest prizeRequest = new AutoGeneratePrizesRequest();
        prizeRequest.setTopN(3);
        int prizeCount = prizeService.autoGenerate(event.getEventId(), prizeRequest).size();
        prizeService.announce(event.getEventId(), coordinator.getUserId());

        hackathonEventService.completeEvent(event.getEventId());

        // ── 10. SYSTEM LOG (S3 only) ──────────────────────────────────
        // A believable admin audit trail spanning the event's lifetime, so the
        // System Logs screen has something to show in the fullest demo scenario.
        seedSystemLog(coordinator, judge1, judge2, guestJudge, mentor1, mentor2);

        log.info("[demo] S3 seeded from the S2.5 cut-point — COMPLETED event, {} teams across {} tracks, "
                        + "{} published final results, {} announced prizes.",
                slots.size(), tracks.size(), finalResultCount, prizeCount);
    }

    /** Writes a spread-out SystemLog history: account creation → role grants →
     * a failed login → a password reset → the admin completing the event. */
    private void seedSystemLog(User coordinator, User judge1, User judge2, User guestJudge,
                               User mentor1, User mentor2) {
        User admin = fx.adminActor();
        LocalDateTime now = LocalDateTime.now();

        fx.systemLog(admin, "CREATE_USER", "Created staff account " + coordinator.getEmail() + ".", now.minusDays(70));
        fx.systemLog(admin, "GRANT_ROLE", "Granted EVENT_COORDINATOR to " + coordinator.getFullName() + " (system-wide).", now.minusDays(70));
        fx.systemLog(admin, "CREATE_USER", "Created staff account " + judge1.getEmail() + ".", now.minusDays(69));
        fx.systemLog(admin, "GRANT_ROLE", "Granted JUDGE to " + judge1.getFullName() + " (system-wide).", now.minusDays(69));
        fx.systemLog(admin, "CREATE_USER", "Created staff account " + judge2.getEmail() + ".", now.minusDays(69));
        fx.systemLog(admin, "GRANT_ROLE", "Granted JUDGE to " + judge2.getFullName() + " (system-wide).", now.minusDays(69));
        fx.systemLog(admin, "CREATE_USER", "Created guest judge account " + guestJudge.getEmail() + ".", now.minusDays(68));
        fx.systemLog(admin, "GRANT_ROLE", "Granted JUDGE to " + guestJudge.getFullName() + " (system-wide).", now.minusDays(68));
        fx.systemLog(admin, "GRANT_ROLE", "Granted MENTOR to " + mentor1.getFullName() + " (system-wide).", now.minusDays(68));
        fx.systemLog(admin, "GRANT_ROLE", "Granted MENTOR to " + mentor2.getFullName() + " (system-wide).", now.minusDays(68));
        fx.systemLog(coordinator, "LOGIN_FAILED", "Failed login attempt for " + coordinator.getEmail() + " (wrong password).", now.minusDays(40));
        fx.systemLog(admin, "RESET_PASSWORD", "Reset password for " + guestJudge.getEmail() + " after a lockout request.", now.minusDays(35));
        fx.systemLog(admin, "COMPLETE_EVENT", "Marked \"" + DEMO_EVENT_NAME + "\" as COMPLETED (IN_PROGRESS → COMPLETED).", now);
    }

    // ── scoring/ranking ──────────────────────────────────────────────

    /** Writes scores for prelim submissions using only the judges assigned to each track. */
    private void writeScoresByTrack(List<Submission> subs, List<Slot> slots,
                                    List<ScoringCriteria> criteria,
                                    Map<Integer, List<User>> judgesByTrack) {
        for (int k = 0; k < subs.size(); k++) {
            Slot slot = slots.get(k);
            Submission sub = subs.get(k);
            List<User> judges = judgesForTrack(slot.track(), judgesByTrack);
            for (int c = 0; c < criteria.size(); c++) {
                for (int j = 0; j < judges.size(); j++) {
                    fx.score(sub, judges.get(j), criteria.get(c), prelimValue(slot.strength, j));
                }
            }
        }
    }

    private List<User> judgesForTrack(Track track, Map<Integer, List<User>> judgesByTrack) {
        List<User> judges = judgesByTrack.get(track.getTrackId());
        if (judges == null || judges.isEmpty()) {
            throw new IllegalStateException("No prelim judge seeded for track " + track.getName() + ".");
        }
        return judges;
    }

    /** Writes every judge×criteria score for the round. subs[k] must pair with slots.get(k). */
    private void writeScores(List<Submission> subs, List<Slot> slots,
                             List<ScoringCriteria> criteria, List<User> judges) {
        for (int k = 0; k < subs.size(); k++) {
            Slot slot = slots.get(k);
            Submission sub = subs.get(k);
            for (int c = 0; c < criteria.size(); c++) {
                for (int j = 0; j < judges.size(); j++) {
                    fx.score(sub, judges.get(j), criteria.get(c), value(slot.strength, c, j));
                }
            }
        }
    }

    private double prelimValue(int strength, int judgeIdx) {
        int targetIndex = (strength - 1) * PRELIM_JUDGES_PER_TRACK + judgeIdx;
        if (targetIndex < 0 || targetIndex >= PRELIM_EVALUATION_TARGETS.length) {
            throw new IllegalArgumentException("No prelim score target for strength " + strength
                    + " and judge index " + judgeIdx + ".");
        }
        return PRELIM_EVALUATION_TARGETS[targetIndex] / 10.0;
    }

    /** Deterministic final-round criterion score; stronger teams remain higher. */
    private double value(int strength, int criteriaIdx, int judgeIdx) {
        double strength01 = normalizedStrength(strength);
        double base = 4.0 + 5.5 * strength01;          // 4.0 (weakest) .. 9.5 (strongest)
        double judgeJitter = switch (judgeIdx % 4) {
            case 0 -> -0.35;
            case 1 -> 0.35;
            case 2 -> -0.55;
            default -> 0.15;
        };
        double criteriaAdj = criteriaIdx % 2 == 0 ? 0.2 : -0.1;
        double v = base + judgeJitter + criteriaAdj;
        v = Math.max(0.0, Math.min(MAX_SCORE, v));
        return Math.round(v * 100.0) / 100.0;
    }

    /** Same normalized 0-100 panel total used by RoundResultService. */
    private double total(Slot slot, List<ScoringCriteria> criteria, int judgeCount) {
        double weightedTotal = 0.0;
        double weightSum = 0.0;
        for (int c = 0; c < criteria.size(); c++) {
            double sum = 0.0;
            for (int j = 0; j < judgeCount; j++) {
                sum += prelimValue(slot.strength, j);
            }
            double avg = sum / judgeCount;
            double weight = criteria.get(c).getWeight().doubleValue();
            weightedTotal += (avg / MAX_SCORE) * weight;
            weightSum += weight;
        }
        double normalized = weightSum == 0.0 ? 0.0 : 100.0 * weightedTotal / weightSum;
        return Math.round(normalized * 100.0) / 100.0;
    }

    private double normalizedStrength(int strength) {
        return maxStrength <= 1 ? 1.0 : (double) (strength - 1) / (maxStrength - 1);
    }

    // ── helpers ──────────────────────────────────────────────────────

    private User staff(String email, String name, String judgeType, String roleName) {
        User u = fx.user(email, name, "STAFF", judgeType);
        fx.grant(u, roleName, null);
        return u;
    }

    private List<ScoringCriteria> criteriaFor(HackathonEvent event, Round round) {
        List<ScoringCriteria> list = new ArrayList<>();
        for (int i = 0; i < CRITERIA.length; i++) {
            list.add(fx.criteria(event, round, CRITERIA[i][0],
                    Double.parseDouble(CRITERIA[i][1]), MAX_SCORE, i + 1));
        }
        return list;
    }

    private User participant() {
        String name = PLAYERS[participantSeq % PLAYERS.length];
        participantSeq++;
        String type = participantSeq % 3 == 0 ? "EXTERNAL_STUDENT" : "FPT_STUDENT";
        return fx.user("p" + participantSeq + "@fpt.edu.vn", name, type, null);
    }

    private List<User> members(int count) {
        List<User> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(participant());
        }
        return list;
    }

    private String eventStatusFor(String scenario) {
        return switch (scenario) {
            case "S1" -> "OPEN";
            default -> "IN_PROGRESS"; // S25 cut-point; S3 completes through HackathonEventService
        };
    }

    private String roundStatusFor(String scenario, boolean prelim) {
        return switch (scenario) {
            case "S1" -> "PENDING";
            default -> prelim ? "FINALIZED" : "ACTIVE"; // shared S25/S3 cut-point
        };
    }

    private Window windowFor(String scenario) {
        LocalDateTime now = LocalDateTime.now();
        return switch (scenario) {
            case "S1" -> new Window(now.minusDays(5), now.plusDays(15), now.plusDays(20), now.plusDays(40));
            default -> new Window(now.minusDays(45), now.minusDays(25), now.minusDays(16), now.plusDays(5));
        };
    }

    private record Window(LocalDateTime regStart, LocalDateTime regEnd,
                          LocalDateTime start, LocalDateTime end) {
    }

    /** A built team with its track, leader, and a deterministic strength for scoring. */
    private record Slot(Team team, Track track, User leader, int strength) {
    }
}
