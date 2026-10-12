package com.kntro.reqsai.shared.application.ai;

/**
 * Asks whether the organization bound to the calling thread may still use AI generation this billing
 * period. Called before the features that spend tokens (live suggestions, chat, code indexing, document
 * classification) and never before reading, exporting or editing by hand.
 * <p>
 * Fails open: when the subscription cannot be read, or no organization is bound, the answer is "yes", so
 * a metering problem never takes the product down.
 */
public interface AiQuotaGuard {

    /** A guard that always allows, for code that runs without metering (unit tests). */
    AiQuotaGuard UNLIMITED = new AiQuotaGuard() {
        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void assertAvailable() {
            // always available
        }
    };

    /** Whether the monthly allowance still has room. */
    boolean isAvailable();

    /**
     * Throws the domain error {@code AI_QUOTA_EXCEEDED} (HTTP 402, carrying plan, limit and used) when the
     * allowance is spent; returns normally otherwise.
     */
    void assertAvailable();
}
