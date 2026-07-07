package com.kntro.reqsai.billing.domain.model;

import com.kntro.reqsai.billing.domain.event.SubscriptionAssignedEvent;
import com.kntro.reqsai.billing.domain.model.valueobjects.PaymentProviderRef;
import com.kntro.reqsai.billing.domain.model.valueobjects.PlanType;
import com.kntro.reqsai.billing.domain.model.valueobjects.SubscriptionStatus;
import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Aggregate root governing the commercial subscription lifecycle of an organization.
 * Persisted in the global public schema.
 */
@Entity
@Table(name = "subscriptions", schema = "public")
@Getter
public class Subscription extends AggregateRoot {

    @Column(name = "organization_id", columnDefinition = "uuid", nullable = false, unique = true)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 16)
    private PlanType planType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SubscriptionStatus status;

    @Embedded
    private PaymentProviderRef providerRef;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "token_quota_used", nullable = false)
    private long tokenQuotaUsed;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected Subscription() {
        // Required by JPA
    }

    /**
     * Initializes a new FREE subscription for the specified organization.
     *
     * @param organizationId the target organization
     */
    public Subscription(UUID organizationId) {
        super();
        this.organizationId = Assert.notNull(organizationId, "organizationId");
        this.planType = PlanType.FREE;
        this.status = SubscriptionStatus.ACTIVE;
        this.tokenQuotaUsed = 0L;
        Instant now = Instant.now();
        this.currentPeriodStart = now;
        this.currentPeriodEnd = now.plus(30, ChronoUnit.DAYS);
        registerEvent(SubscriptionAssignedEvent.of(getId(), organizationId, planType.name()));
    }

    public boolean isFree() {
        return this.planType == PlanType.FREE;
    }

    public boolean isActive() {
        return this.status == SubscriptionStatus.ACTIVE;
    }
}
