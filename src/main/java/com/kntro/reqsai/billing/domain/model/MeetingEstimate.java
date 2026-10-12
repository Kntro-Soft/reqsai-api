package com.kntro.reqsai.billing.domain.model;

/**
 * How many more minutes of live meeting the remaining token allowance covers, in the unit users think in.
 * <p>
 * The rate is measured, not assumed: an organization's own live-suggestion tokens divided by the minutes
 * of audio it transcribed over the measuring window. With less than {@link #MIN_MEASURED_SECONDS} of audio
 * the sample is too small to trust and {@link #DEFAULT_TOKENS_PER_MINUTE} is used instead.
 *
 * @param minutesLeft    whole minutes the remaining allowance covers
 * @param tokensPerMinute quota tokens one minute of meeting costs
 * @param measured       {@code true} when the rate comes from the organization's own history
 */
public record MeetingEstimate(long minutesLeft, long tokensPerMinute, boolean measured) {

    /**
     * Quota tokens per minute of meeting when the organization has no history yet. A live pass runs about
     * every 22 s of new speech (2 to 3 a minute) and, with the prompt prefix served from the provider's
     * cache, weighs about 3,800 to 4,000 quota tokens on the reference recording (see docs/BILLING.md), so a
     * steady meeting costs about 10,000 a minute.
     */
    public static final long DEFAULT_TOKENS_PER_MINUTE = 10_000L;

    /** Minutes of audio an organization needs before its own rate replaces the default. */
    public static final long MIN_MEASURED_SECONDS = 120L;

    public static MeetingEstimate of(long remainingTokens, long liveTokens, long audioSeconds) {
        boolean measured = audioSeconds >= MIN_MEASURED_SECONDS && liveTokens > 0;
        long perMinute = measured
                ? Math.max(1L, Math.round(liveTokens * 60.0 / audioSeconds))
                : DEFAULT_TOKENS_PER_MINUTE;
        return new MeetingEstimate(Math.max(0L, remainingTokens) / perMinute, perMinute, measured);
    }
}
