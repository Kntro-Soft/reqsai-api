package com.kntro.reqsai.iam.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when an {@link com.kntro.reqsai.iam.domain.model.Account} records acceptance of a
 * Terms and Conditions version. The {@code termsVersion} string (e.g. {@code "2026-01"}) is
 * included so downstream listeners can store or audit the accepted version.
 */
public record TermsAcceptedEvent(UUID accountId, String termsVersion, Instant occurredAt) implements DomainEvent {

    public static TermsAcceptedEvent of(UUID accountId, String termsVersion) {
        return new TermsAcceptedEvent(accountId, termsVersion, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return accountId;
    }
}
