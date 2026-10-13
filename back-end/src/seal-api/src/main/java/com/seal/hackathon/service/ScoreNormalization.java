package com.seal.hackathon.service;

import com.seal.hackathon.entity.Score;
import com.seal.hackathon.entity.ScoringCriteria;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Optional;

/** Shared scoring math used by both ranking and completed-event analytics. */
final class ScoreNormalization {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private ScoreNormalization() {
    }

    /**
     * Normalizes one judge's complete criterion set to a weighted 0-100 score:
     * 100 x sum(weight x value/maxScore) / sum(weight).
     */
    static Optional<BigDecimal> weightedPercent(Collection<Score> scores) {
        BigDecimal weightedFraction = BigDecimal.ZERO;
        BigDecimal weightSum = BigDecimal.ZERO;

        for (Score score : scores) {
            ScoringCriteria criteria = score.getCriteria();
            BigDecimal weight = criteria.getWeight();
            BigDecimal maxScore = criteria.getMaxScore();
            if (weight == null || maxScore == null || maxScore.signum() == 0) {
                continue;
            }
            BigDecimal fraction = score.getValue().divide(maxScore, 6, RoundingMode.HALF_UP);
            weightedFraction = weightedFraction.add(fraction.multiply(weight));
            weightSum = weightSum.add(weight);
        }

        if (weightSum.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(weightedFraction
                .divide(weightSum, 6, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED));
    }

    static Optional<BigDecimal> criterionPercent(Score score) {
        BigDecimal maxScore = score.getCriteria().getMaxScore();
        if (maxScore == null || maxScore.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(score.getValue()
                .divide(maxScore, 6, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED));
    }
}
