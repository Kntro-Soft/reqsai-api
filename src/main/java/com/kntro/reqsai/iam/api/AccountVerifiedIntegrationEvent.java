package com.kntro.reqsai.iam.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Public integration event: an account's email ownership has been proven (email verified). Relayed by
 * IAM from the internal {@code AccountVerifiedEvent} so other modules can react without importing IAM
 * internals — e.g. the workspace invitation link-on-signup safety net.
 * <p>
 * Lives in the {@code iam::api} named interface (not {@code ..domain..}), so it can carry the Modulith
 * boundary without putting framework annotations in the domain layer.
 *
 * @param accountId  the verified account id
 * @param email      the proven (verified) account email
 * @param occurredAt when verification happened
 */
public record AccountVerifiedIntegrationEvent(UUID accountId, String email, Instant occurredAt) {

    public static AccountVerifiedIntegrationEvent of(UUID accountId, String email) {
        return new AccountVerifiedIntegrationEvent(accountId, email, Instant.now());
    }
}
