package com.kntro.reqsai.billing.api;

import java.time.Instant;

/**
 * Where an organization stands against its monthly AI token allowance.
 *
 * @param planType  the plan the allowance belongs to
 * @param limit     tokens the plan allows per period
 * @param used      tokens counted in the current period (zero once the stored period has elapsed)
 * @param resetsAt  when the current period ends and the counter starts over
 * @param available whether AI generation may still run
 */
public record TokenQuotaStatus(String planType, long limit, long used, Instant resetsAt, boolean available) {

    /** The status of an organization with no subscription: nothing is limited. */
    public static TokenQuotaStatus unlimited() {
        return new TokenQuotaStatus("NONE", Long.MAX_VALUE, 0, Instant.MAX, true);
    }
}
