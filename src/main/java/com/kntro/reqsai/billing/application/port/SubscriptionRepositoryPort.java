package com.kntro.reqsai.billing.application.port;

import com.kntro.reqsai.billing.domain.model.Subscription;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Port interface for managing Subscription persistence.
 */
public interface SubscriptionRepositoryPort {

    Subscription save(Subscription subscription);

    Optional<Subscription> findByOrganizationId(UUID organizationId);

    Optional<Subscription> findByProviderExternalId(String externalId);

    boolean existsByOrganizationId(UUID organizationId);

    /**
     * Adds {@code tokens} to the organization's monthly counter with a single {@code UPDATE ... SET
     * token_quota_used = token_quota_used + n}, so concurrent calls never lose an increment. When the
     * stored period has elapsed it is rolled forward first, under a row lock, and the increment lands in
     * the fresh period.
     *
     * @return the counter after the increment, or empty when the organization has no subscription
     */
    Optional<TokenCounterState> addTokens(UUID organizationId, long tokens, Instant now);

    /**
     * Records that the warning step {@code percent} was announced, but only when no step this high was
     * announced in the period yet.
     *
     * @return {@code true} for the one caller that raised it, who therefore sends the notification
     */
    boolean raiseWarningPercent(UUID organizationId, int percent);
}
