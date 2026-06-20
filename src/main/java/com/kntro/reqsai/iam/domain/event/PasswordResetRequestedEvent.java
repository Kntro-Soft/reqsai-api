package com.kntro.reqsai.iam.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when {@link com.kntro.reqsai.iam.domain.model.Account#generatePasswordResetToken} is called.
 * Carries the raw (unhashed) token so the notification listener can include it in the email link
 * without ever touching the stored hash.
 */
public record PasswordResetRequestedEvent(UUID accountId, String rawToken, Instant occurredAt) implements DomainEvent {

    public static PasswordResetRequestedEvent of(UUID accountId, String rawToken) {
        return new PasswordResetRequestedEvent(accountId, rawToken, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return accountId;
    }
}
