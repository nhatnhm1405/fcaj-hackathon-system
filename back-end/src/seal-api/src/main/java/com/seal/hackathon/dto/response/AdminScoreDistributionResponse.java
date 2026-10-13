package com.seal.hackathon.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminScoreDistributionResponse {

    private Integer eventId;
    private String eventName;
    /** JUDGE_EVALUATION | SUBMISSION_RESULT | CRITERIA_SCORE */
    private String metric;
    private Integer selectedRoundId;
    private Integer selectedTrackId;
    private Integer selectedCriteriaId;
    private List<RoundOption> rounds;
    private List<TrackOption> tracks;
    private List<CriteriaOption> criteria;
    private List<ScoreBin> bins;
    private Statistics statistics;
    private List<Observation> observations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoundOption {
        private Integer roundId;
        private String name;
        private Integer orderNumber;
        private Boolean isFinal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackOption {
        private Integer trackId;
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriteriaOption {
        private Integer criteriaId;
        private String name;
        private BigDecimal weight;
        private BigDecimal maxScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreBin {
        private Integer lowerBound;
        private Integer upperBound;
        private BigDecimal midpoint;
        private String label;
        private Integer count;
        private BigDecimal percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Statistics {
        private Integer sampleCount;
        private Integer teamCount;
        private Integer judgeCount;
        private BigDecimal average;
        private BigDecimal median;
        private BigDecimal standardDeviation;
        private BigDecimal minimum;
        private BigDecimal maximum;
        private Integer belowFiftyCount;
        private BigDecimal belowFiftyPercentage;
        private Integer atOrAboveEightyCount;
        private BigDecimal atOrAboveEightyPercentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Observation {
        private String observationId;
        private Integer submissionId;
        private Integer teamId;
        private String teamName;
        private Integer judgeId;
        private String judgeName;
        private Integer trackId;
        private String trackName;
        private Integer criteriaId;
        private String criteriaName;
        private Integer rankPosition;
        private BigDecimal score;
        private BigDecimal differenceFromAverage;
    }
}
