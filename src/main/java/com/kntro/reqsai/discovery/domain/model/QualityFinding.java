package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.shared.domain.support.Assert;
import org.jspecify.annotations.Nullable;

/**
 * The result of evaluating one {@link QualityCharacteristic} on a story: a score from 0 to 100, what was
 * observed and, when the characteristic is not fully met, how to fix it. Stored as JSON inside the
 * assessment.
 *
 * @param characteristic the characteristic evaluated
 * @param score          0 (not met at all) to 100 (fully met)
 * @param observation    what the evaluation found
 * @param suggestion     how to fix it; null when the characteristic is fully met
 */
public record QualityFinding(
        QualityCharacteristic characteristic,
        int score,
        String observation,
        @Nullable String suggestion
) {

    public static final int MAX_SCORE = 100;
    private static final int TEXT_MAX = 1000;

    public QualityFinding {
        Assert.notNull(characteristic, "characteristic");
        Assert.isTrue(score >= 0 && score <= MAX_SCORE, "score", "must be between 0 and " + MAX_SCORE);
        observation = Assert.maxLength(Assert.notBlank(observation, "observation"), "observation", TEXT_MAX);
        suggestion = suggestion == null || suggestion.isBlank()
                ? null
                : Assert.maxLength(suggestion.strip(), "suggestion", TEXT_MAX);
        Assert.isTrue(score == MAX_SCORE || suggestion != null, "suggestion",
                "an unmet characteristic must say how to fix it");
    }

    /** A characteristic that passed with nothing to fix. */
    public static QualityFinding met(QualityCharacteristic characteristic, String observation) {
        return new QualityFinding(characteristic, MAX_SCORE, observation, null);
    }

    /** A characteristic that falls short, with the way to fix it. */
    public static QualityFinding unmet(QualityCharacteristic characteristic, int score, String observation,
                                       String suggestion) {
        Assert.isTrue(score < MAX_SCORE, "score", "an unmet characteristic scores below " + MAX_SCORE);
        return new QualityFinding(characteristic, score, observation, suggestion);
    }

    /** True when there is nothing left to fix for this characteristic. */
    public boolean met() {
        return score == MAX_SCORE;
    }
}
