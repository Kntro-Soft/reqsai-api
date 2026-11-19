package com.kntro.reqsai.overview.application.result;

import org.jspecify.annotations.Nullable;

/**
 * How many of the stories decided in the period were accepted without major rewriting.
 *
 * @param similarityThreshold
 *            minimum similarity between the text the AI proposed and the final text for an accepted story to count
 *            as not rewritten
 * @param decided
 *            AI-generated stories approved, exported or rejected in the period
 * @param accepted
 *            of them, those approved or exported
 * @param acceptedAsGenerated
 *            of the accepted, those whose final text stayed at or above the threshold
 * @param acceptedRewritten
 *            of the accepted, those rewritten below the threshold
 * @param rejected
 *            of the decided, those rejected
 * @param withoutBaseline
 *            stories decided in the period that did not come from the AI (or predate the record of its text), left
 *            out of every other figure
 */
public record AcceptanceIndicator(double similarityThreshold, long decided, long accepted, long acceptedAsGenerated,
        long acceptedRewritten, long rejected, long withoutBaseline) {

    /**
     * Share of decided stories accepted without major rewriting, between 0 and 1; null while none has been decided.
     */
    public @Nullable Double rate() {
        return decided == 0 ? null : (double) acceptedAsGenerated / decided;
    }
}
