package com.kntro.reqsai.billing.interfaces.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * WebSocket payload sent to {@code /topic/organizations/{organizationId}/usage} when an organization
 * first reaches 80, 90 or 100 percent of its monthly AI token allowance. The client switches on
 * {@code type}; {@code level} is {@code WARNING_80}, {@code WARNING_90} or {@code EXHAUSTED}.
 */
public record QuotaWarningMessage(
        String type,
        UUID organizationId,
        String level,
        int percent,
        long tokensUsed,
        long tokensLimit,
        Instant occurredAt
) {

    public static final String TYPE = "TOKEN_QUOTA_WARNING";

    public static QuotaWarningMessage of(UUID organizationId, String level, int percent, long used, long limit) {
        return new QuotaWarningMessage(TYPE, organizationId, level, percent, used, limit, Instant.now());
    }
}
