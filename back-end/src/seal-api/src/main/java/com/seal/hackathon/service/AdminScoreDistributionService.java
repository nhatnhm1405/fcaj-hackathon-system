package com.seal.hackathon.service;

import com.seal.hackathon.dto.response.AdminScoreDistributionResponse;
import com.seal.hackathon.entity.*;
import com.seal.hackathon.exception.BadRequestException;
import com.seal.hackathon.exception.ResourceNotFoundException;
import com.seal.hackathon.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminScoreDistributionService {

    private static final int BIN_SIZE = 10;

    private final HackathonEventRepository eventRepository;
    private final RoundRepository roundRepository;
    private final TrackRepository trackRepository;
    private final ScoringCriteriaRepository criteriaRepository;
    private final ScoreRepository scoreRepository;
    private final RoundResultRepository resultRepository;
    private final TeamEventEntryRepository teamEventEntryRepository;

    @Transactional(readOnly = true)
    public AdminScoreDistributionResponse getDistribution(
            Integer eventId, Integer roundId, Integer trackId,
            String metricValue, Integer criteriaId) {
        HackathonEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        if (!"COMPLETED".equalsIgnoreCase(event.getStatus())) {
            throw new BadRequestException("Score analytics are available only after the event is completed.");
        }

        Metric metric = parseMetric(metricValue);
        List<Round> rounds = roundRepository.findAllByEvent_EventIdOrderByOrderNumber(eventId);
        List<Track> tracks = trackRepository.findAllByEvent_EventId(eventId).stream()
                .sorted(Comparator.comparing(Track::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        validateTrack(trackId, tracks);

        Map<Integer, TeamEventEntry> entriesByTeam = new HashMap<>();
        for (TeamEventEntry entry : teamEventEntryRepository.findAllByEvent_EventId(eventId)) {
            entriesByTeam.put(entry.getTeam().getTeamId(), entry);
        }

        Round selectedRound = resolveRound(roundId, rounds);
        if (selectedRound == null) {
            return response(event, metric, null, trackId, null,
                    rounds, tracks, List.of(), List.of());
        }

        List<ScoringCriteria> criteria = criteriaRepository
                .findAllByRound_RoundIdOrderByOrderNumber(selectedRound.getRoundId());
        ScoringCriteria selectedCriteria = resolveCriteria(metric, criteriaId, criteria);
        List<AdminScoreDistributionResponse.Observation> observations = switch (metric) {
            case JUDGE_EVALUATION -> judgeEvaluationObservations(selectedRound, trackId, entriesByTeam);
            case SUBMISSION_RESULT -> submissionResultObservations(selectedRound, trackId, entriesByTeam);
            case CRITERIA_SCORE -> criteriaObservations(selectedRound, selectedCriteria, trackId, entriesByTeam);
        };
        observations.sort(Comparator
                .comparing(AdminScoreDistributionResponse.Observation::getScore).reversed()
                .thenComparing(AdminScoreDistributionResponse.Observation::getTeamName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        return response(event, metric, selectedRound, trackId,
                selectedCriteria != null ? selectedCriteria.getCriteriaId() : null,
                rounds, tracks, criteria, observations);
    }

    private List<AdminScoreDistributionResponse.Observation> judgeEvaluationObservations(
            Round round, Integer trackId, Map<Integer, TeamEventEntry> entriesByTeam) {
        Map<JudgeSubmissionKey, List<Score>> scoresByEvaluation = new LinkedHashMap<>();
        for (Score score : finalizedScores(round.getRoundId())) {
            Integer teamId = score.getSubmission().getTeam().getTeamId();
            if (!matchesTrack(teamId, trackId, entriesByTeam)) continue;
            JudgeSubmissionKey key = new JudgeSubmissionKey(
                    score.getSubmission().getSubmissionId(), score.getJudge().getUserId());
            scoresByEvaluation.computeIfAbsent(key, ignored -> new ArrayList<>()).add(score);
        }

        List<AdminScoreDistributionResponse.Observation> observations = new ArrayList<>();
        for (Map.Entry<JudgeSubmissionKey, List<Score>> entry : scoresByEvaluation.entrySet()) {
            Score first = entry.getValue().getFirst();
            ScoreNormalization.weightedPercent(entry.getValue()).ifPresent(percent -> observations.add(
                    observation(
                            "judge-" + entry.getKey().submissionId() + "-" + entry.getKey().judgeId(),
                            first.getSubmission(), first.getJudge(), null, null,
                            percent, entriesByTeam)));
        }
        return observations;
    }

    private List<AdminScoreDistributionResponse.Observation> criteriaObservations(
            Round round, ScoringCriteria selectedCriteria, Integer trackId,
            Map<Integer, TeamEventEntry> entriesByTeam) {
        if (selectedCriteria == null) return new ArrayList<>();

        List<AdminScoreDistributionResponse.Observation> observations = new ArrayList<>();
        for (Score score : finalizedScores(round.getRoundId())) {
            if (!Objects.equals(score.getCriteria().getCriteriaId(), selectedCriteria.getCriteriaId())) continue;
            Integer teamId = score.getSubmission().getTeam().getTeamId();
            if (!matchesTrack(teamId, trackId, entriesByTeam)) continue;
            ScoreNormalization.criterionPercent(score).ifPresent(percent -> observations.add(
                    observation(
                            "score-" + score.getScoreId(), score.getSubmission(), score.getJudge(),
                            selectedCriteria, null, percent, entriesByTeam)));
        }
        return observations;
    }

    private List<AdminScoreDistributionResponse.Observation> submissionResultObservations(
            Round round, Integer trackId, Map<Integer, TeamEventEntry> entriesByTeam) {
        List<AdminScoreDistributionResponse.Observation> observations = new ArrayList<>();
        for (RoundResult result : resultRepository
                .findAllByRound_RoundIdOrderByRankPosition(round.getRoundId())) {
            Integer teamId = result.getTeam().getTeamId();
            if (!matchesTrack(teamId, trackId, entriesByTeam)) continue;
            TrackInfo track = trackInfo(teamId, entriesByTeam);
            observations.add(AdminScoreDistributionResponse.Observation.builder()
                    .observationId("result-" + result.getResultId())
                    .teamId(teamId)
                    .teamName(result.getTeam().getName())
                    .trackId(track.id())
                    .trackName(track.name())
                    .rankPosition(result.getRankPosition())
                    .score(scale(result.getTotalScore()))
                    .build());
        }
        return observations;
    }

    private List<Score> finalizedScores(Integer roundId) {
        return scoreRepository.findAllFinalizedByRoundWithDetails(roundId);
    }

    private AdminScoreDistributionResponse.Observation observation(
            String observationId, Submission submission, User judge,
            ScoringCriteria criteria, Integer rankPosition, BigDecimal score,
            Map<Integer, TeamEventEntry> entriesByTeam) {
        Team team = submission.getTeam();
        TrackInfo track = trackInfo(team.getTeamId(), entriesByTeam);
        return AdminScoreDistributionResponse.Observation.builder()
                .observationId(observationId)
                .submissionId(submission.getSubmissionId())
                .teamId(team.getTeamId())
                .teamName(team.getName())
                .judgeId(judge != null ? judge.getUserId() : null)
                .judgeName(judge != null ? judge.getFullName() : null)
                .trackId(track.id())
                .trackName(track.name())
                .criteriaId(criteria != null ? criteria.getCriteriaId() : null)
                .criteriaName(criteria != null ? criteria.getName() : null)
                .rankPosition(rankPosition)
                .score(scale(score))
                .build();
    }

    private AdminScoreDistributionResponse response(
            HackathonEvent event, Metric metric, Round selectedRound,
            Integer selectedTrackId, Integer selectedCriteriaId,
            List<Round> rounds, List<Track> tracks, List<ScoringCriteria> criteria,
            List<AdminScoreDistributionResponse.Observation> observations) {
        AdminScoreDistributionResponse.Statistics statistics = statistics(observations);
        if (statistics.getAverage() != null) {
            observations.forEach(observation -> observation.setDifferenceFromAverage(
                    observation.getScore().subtract(statistics.getAverage())
                            .setScale(2, RoundingMode.HALF_UP)));
        }

        return AdminScoreDistributionResponse.builder()
                .eventId(event.getEventId())
                .eventName(event.getName())
                .metric(metric.name())
                .selectedRoundId(selectedRound != null ? selectedRound.getRoundId() : null)
                .selectedTrackId(selectedTrackId)
                .selectedCriteriaId(selectedCriteriaId)
                .rounds(rounds.stream().map(round -> AdminScoreDistributionResponse.RoundOption.builder()
                        .roundId(round.getRoundId()).name(round.getName())
                        .orderNumber(round.getOrderNumber()).isFinal(round.getIsFinal()).build()).toList())
                .tracks(tracks.stream().map(track -> AdminScoreDistributionResponse.TrackOption.builder()
                        .trackId(track.getTrackId()).name(track.getName()).build()).toList())
                .criteria(criteria.stream().map(item -> AdminScoreDistributionResponse.CriteriaOption.builder()
                        .criteriaId(item.getCriteriaId()).name(item.getName())
                        .weight(item.getWeight()).maxScore(item.getMaxScore()).build()).toList())
                .bins(bins(observations))
                .statistics(statistics)
                .observations(observations)
                .build();
    }

    private AdminScoreDistributionResponse.Statistics statistics(
            List<AdminScoreDistributionResponse.Observation> observations) {
        if (observations.isEmpty()) {
            return AdminScoreDistributionResponse.Statistics.builder()
                    .sampleCount(0).teamCount(0).judgeCount(0)
                    .belowFiftyCount(0).belowFiftyPercentage(BigDecimal.ZERO.setScale(2))
                    .atOrAboveEightyCount(0).atOrAboveEightyPercentage(BigDecimal.ZERO.setScale(2))
                    .build();
        }

        List<BigDecimal> values = observations.stream()
                .map(AdminScoreDistributionResponse.Observation::getScore).sorted().toList();
        int size = values.size();
        BigDecimal average = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(size), 2, RoundingMode.HALF_UP);
        BigDecimal median = size % 2 == 1
                ? values.get(size / 2)
                : values.get(size / 2 - 1).add(values.get(size / 2))
                        .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        double variance = values.stream()
                .mapToDouble(value -> Math.pow(value.doubleValue() - average.doubleValue(), 2))
                .average().orElse(0);
        int belowFifty = (int) values.stream()
                .filter(value -> value.compareTo(BigDecimal.valueOf(50)) < 0).count();
        int atOrAboveEighty = (int) values.stream()
                .filter(value -> value.compareTo(BigDecimal.valueOf(80)) >= 0).count();

        return AdminScoreDistributionResponse.Statistics.builder()
                .sampleCount(size)
                .teamCount((int) observations.stream()
                        .map(AdminScoreDistributionResponse.Observation::getTeamId)
                        .filter(Objects::nonNull).distinct().count())
                .judgeCount((int) observations.stream()
                        .map(AdminScoreDistributionResponse.Observation::getJudgeId)
                        .filter(Objects::nonNull).distinct().count())
                .average(average).median(scale(median))
                .standardDeviation(BigDecimal.valueOf(Math.sqrt(variance))
                        .setScale(2, RoundingMode.HALF_UP))
                .minimum(values.getFirst()).maximum(values.getLast())
                .belowFiftyCount(belowFifty).belowFiftyPercentage(percentage(belowFifty, size))
                .atOrAboveEightyCount(atOrAboveEighty)
                .atOrAboveEightyPercentage(percentage(atOrAboveEighty, size))
                .build();
    }

    private List<AdminScoreDistributionResponse.ScoreBin> bins(
            List<AdminScoreDistributionResponse.Observation> observations) {
        int[] counts = new int[10];
        for (AdminScoreDistributionResponse.Observation observation : observations) {
            int index = Math.min(9, Math.max(0, observation.getScore().intValue() / BIN_SIZE));
            counts[index]++;
        }

        List<AdminScoreDistributionResponse.ScoreBin> bins = new ArrayList<>();
        for (int index = 0; index < counts.length; index++) {
            int lower = index * BIN_SIZE;
            int upper = lower + BIN_SIZE;
            bins.add(AdminScoreDistributionResponse.ScoreBin.builder()
                    .lowerBound(lower).upperBound(upper)
                    .midpoint(BigDecimal.valueOf(lower + BIN_SIZE / 2.0))
                    .label(lower + "-" + upper)
                    .count(counts[index])
                    .percentage(percentage(counts[index], observations.size()))
                    .build());
        }
        return bins;
    }

    private Round resolveRound(Integer roundId, List<Round> rounds) {
        if (roundId != null) {
            return rounds.stream().filter(round -> Objects.equals(round.getRoundId(), roundId))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Round does not belong to this event."));
        }
        return rounds.stream().filter(round -> Boolean.TRUE.equals(round.getIsFinal()))
                .max(Comparator.comparing(Round::getOrderNumber))
                .orElseGet(() -> rounds.isEmpty() ? null : rounds.getLast());
    }

    private ScoringCriteria resolveCriteria(
            Metric metric, Integer criteriaId, List<ScoringCriteria> criteria) {
        if (metric != Metric.CRITERIA_SCORE) return null;
        if (criteriaId == null) return criteria.isEmpty() ? null : criteria.getFirst();
        return criteria.stream().filter(item -> Objects.equals(item.getCriteriaId(), criteriaId))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Criteria does not belong to the selected round."));
    }

    private void validateTrack(Integer trackId, List<Track> tracks) {
        if (trackId != null && tracks.stream()
                .noneMatch(track -> Objects.equals(track.getTrackId(), trackId))) {
            throw new BadRequestException("Track does not belong to this event.");
        }
    }

    private boolean matchesTrack(
            Integer teamId, Integer selectedTrackId,
            Map<Integer, TeamEventEntry> entriesByTeam) {
        if (selectedTrackId == null) return true;
        TeamEventEntry entry = entriesByTeam.get(teamId);
        return entry != null && entry.getTrack() != null
                && Objects.equals(entry.getTrack().getTrackId(), selectedTrackId);
    }

    private TrackInfo trackInfo(Integer teamId, Map<Integer, TeamEventEntry> entriesByTeam) {
        TeamEventEntry entry = entriesByTeam.get(teamId);
        Track track = entry != null ? entry.getTrack() : null;
        return track == null
                ? new TrackInfo(null, null)
                : new TrackInfo(track.getTrackId(), track.getName());
    }

    private Metric parseMetric(String metricValue) {
        String value = metricValue == null || metricValue.isBlank()
                ? Metric.JUDGE_EVALUATION.name()
                : metricValue.trim().toUpperCase(Locale.ROOT);
        try {
            return Metric.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Unknown score distribution metric: " + metricValue);
        }
    }

    private BigDecimal percentage(int count, int total) {
        if (total == 0) return BigDecimal.ZERO.setScale(2);
        return BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private enum Metric {
        JUDGE_EVALUATION,
        SUBMISSION_RESULT,
        CRITERIA_SCORE
    }

    private record JudgeSubmissionKey(Integer submissionId, Integer judgeId) {
    }

    private record TrackInfo(Integer id, String name) {
    }
}
