package com.kntro.reqsai.iam.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a new {@link com.kntro.reqsai.iam.domain.model.Account} is registered. Internal for now
 * (audit / future welcome-email and email-verification listeners).
 */
public record AccountCreatedEvent(UUID accountId, String email, Instant occurredAt) implements DomainEvent {

    public static AccountCreatedEvent of(UUID accountId, String email) {
        return new AccountCreatedEvent(accountId, email, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return accountId;
    }
}
