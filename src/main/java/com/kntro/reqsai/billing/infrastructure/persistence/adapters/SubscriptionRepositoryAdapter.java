package com.kntro.reqsai.billing.infrastructure.persistence.adapters;

import com.kntro.reqsai.billing.application.port.SubscriptionRepositoryPort;
import com.kntro.reqsai.billing.application.port.TokenCounterState;
import com.kntro.reqsai.billing.domain.model.valueobjects.PlanType;
import com.kntro.reqsai.billing.domain.model.Subscription;
import com.kntro.reqsai.billing.infrastructure.persistence.repositories.SubscriptionJpaRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter implementing the {@link SubscriptionRepositoryPort} using a Spring Data JPA repository.
 */
@Repository
@RequiredArgsConstructor
public class SubscriptionRepositoryAdapter implements SubscriptionRepositoryPort {

    private final SubscriptionJpaRepository jpa;
    private final EntityManager entityManager;

    @Override
    public Subscription save(Subscription subscription) {
        return jpa.save(subscription);
    }

    @Override
    public Optional<Subscription> findByOrganizationId(UUID organizationId) {
        return jpa.findByOrganizationId(organizationId);
    }

    @Override
    public Optional<Subscription> findByProviderExternalId(String externalId) {
        return jpa.findByProviderRefExternalId(externalId);
    }

    @Override
    public boolean existsByOrganizationId(UUID organizationId) {
        return jpa.existsByOrganizationId(organizationId);
    }

    @Override
    public Optional<TokenCounterState> addTokens(UUID organizationId, long tokens, Instant now) {
        Optional<TokenCounterState> counted = increment(organizationId, tokens, now);
        if (counted.isPresent()) {
            return counted;
        }
        // No row advanced: the organization has no subscription, or its period has elapsed. Roll the period
        // forward under a row lock (a concurrent caller waits here, then finds nothing left to roll) and
        // count the tokens in the fresh period.
        Subscription subscription = jpa.findLockedByOrganizationId(organizationId).orElse(null);
        if (subscription == null) {
            return Optional.empty();
        }
        if (subscription.rolloverIfPeriodElapsed(now)) {
            jpa.saveAndFlush(subscription);
        }
        return increment(organizationId, tokens, now);
    }

    @Override
    public boolean raiseWarningPercent(UUID organizationId, int percent) {
        int raised = entityManager.createNativeQuery("""
                        UPDATE public.subscriptions SET quota_warning_percent = :percent
                        WHERE organization_id = :organizationId AND quota_warning_percent < :percent""")
                .setParameter("percent", percent)
                .setParameter("organizationId", organizationId)
                .executeUpdate();
        return raised > 0;
    }

    private Optional<TokenCounterState> increment(UUID organizationId, long tokens, Instant now) {
        List<?> rows = entityManager.createNativeQuery("""
                        UPDATE public.subscriptions SET token_quota_used = token_quota_used + :tokens
                        WHERE organization_id = :organizationId AND current_period_end > :now
                        RETURNING token_quota_used, plan_type""")
                .setParameter("tokens", tokens)
                .setParameter("organizationId", organizationId)
                .setParameter("now", now)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = (Object[]) rows.getFirst();
        return Optional.of(new TokenCounterState(((Number) row[0]).longValue(), PlanType.valueOf((String) row[1])));
    }
}
