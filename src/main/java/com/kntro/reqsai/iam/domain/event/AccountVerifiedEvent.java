package com.kntro.reqsai.iam.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when an {@link com.kntro.reqsai.iam.domain.model.Account} transitions from
 * {@code PENDING_VERIFICATION} to {@code ACTIVE} after a successful email verification.
 */
public record AccountVerifiedEvent(UUID accountId, Instant occurredAt) implements DomainEvent {

    public static AccountVerifiedEvent of(UUID accountId) {
        return new AccountVerifiedEvent(accountId, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return accountId;
    }
}
