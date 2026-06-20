package com.kntro.reqsai.iam.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when an {@link com.kntro.reqsai.iam.domain.model.EmailVerification} token is issued.
 * Downstream listeners can use this to trigger the verification email without coupling the domain
 * to the email infrastructure.
 */
public record EmailVerificationRequestedEvent(UUID accountId, String rawToken, Instant occurredAt) implements DomainEvent {

    public static EmailVerificationRequestedEvent of(UUID accountId, String rawToken) {
        return new EmailVerificationRequestedEvent(accountId, rawToken, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return accountId;
    }
}
