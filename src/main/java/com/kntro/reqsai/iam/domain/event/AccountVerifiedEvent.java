package com.kntro.reqsai.iam.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when an {@link com.kntro.reqsai.iam.domain.model.Account} transitions from
 * {@code PENDING_VERIFICATION} to {@code ACTIVE} after a successful email verification.
 * <p>
 * Carries the verified {@code email} so cross-module listeners (e.g. the workspace link-on-signup
 * listener) can act on a <em>proven</em> email without a lookup back into IAM. Exposed via the IAM
 * {@code events} named interface.
 */
public record AccountVerifiedEvent(UUID accountId, String email, Instant occurredAt) implements DomainEvent {

    public static AccountVerifiedEvent of(UUID accountId, String email) {
        return new AccountVerifiedEvent(accountId, email, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return accountId;
    }
}
