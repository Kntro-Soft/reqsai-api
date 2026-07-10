package com.kntro.reqsai.billing.domain.model;

import com.kntro.reqsai.billing.domain.event.SubscriptionAssignedEvent;
import com.kntro.reqsai.billing.domain.event.SubscriptionCancelledEvent;
import com.kntro.reqsai.billing.domain.event.SubscriptionDowngradedEvent;
import com.kntro.reqsai.billing.domain.event.SubscriptionReactivatedEvent;
import com.kntro.reqsai.billing.domain.event.SubscriptionUpgradedEvent;
import com.kntro.reqsai.billing.domain.exception.BillingExceptions;
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

    public boolean isCancelled() {
        return this.status == SubscriptionStatus.CANCELLED;
    }

    private static final int PERIOD_LENGTH_DAYS = 30;

    /**
     * Switches this subscription to a paid plan, resetting the billing period and token quota.
     * Invoked once the payment provider has confirmed the plan is active (synchronously for the
     * fake gateway, or from the webhook for a real gateway).
     *
     * @param target      the paid plan to move to (must not be FREE)
     * @param providerRef reference to the external subscription at the payment provider
     */
    public void upgradeTo(PlanType target, PaymentProviderRef providerRef) {
        Assert.notNull(target, "target");
        if (target == PlanType.FREE) {
            throw BillingExceptions.invalidPlanChange("Cannot upgrade to the FREE plan");
        }
        if (this.status == SubscriptionStatus.CANCELLED) {
            throw BillingExceptions.invalidSubscriptionState("Cannot upgrade a cancelled subscription; reactivate first");
        }
        if (this.planType == target && this.status == SubscriptionStatus.ACTIVE) {
            throw BillingExceptions.invalidPlanChange("Subscription is already on plan " + target);
        }
        String previous = this.planType.name();
        this.planType = target;
        this.status = SubscriptionStatus.ACTIVE;
        this.providerRef = providerRef;
        startNewPeriod(Instant.now());
        this.cancelledAt = null;
        registerEvent(SubscriptionUpgradedEvent.of(getId(), organizationId, previous, target.name()));
    }

    /**
     * Cancels a paid subscription. The plan stays usable until {@link #currentPeriodEnd}; the record
     * flips to {@code CANCELLED} and stops renewing.
     */
    public void cancel() {
        if (isFree()) {
            throw BillingExceptions.invalidSubscriptionState("The FREE plan cannot be cancelled");
        }
        if (this.status == SubscriptionStatus.CANCELLED) {
            throw BillingExceptions.invalidSubscriptionState("Subscription is already cancelled");
        }
        this.status = SubscriptionStatus.CANCELLED;
        this.cancelledAt = Instant.now();
        registerEvent(SubscriptionCancelledEvent.of(getId(), organizationId, planType.name()));
    }

    /**
     * Reactivates a cancelled paid subscription, resuming its plan. Starts a fresh period (and resets
     * the token quota) when the previous period has already elapsed.
     */
    public void reactivate() {
        if (this.status != SubscriptionStatus.CANCELLED) {
            throw BillingExceptions.invalidSubscriptionState("Only a cancelled subscription can be reactivated");
        }
        if (isFree()) {
            throw BillingExceptions.invalidSubscriptionState("The FREE plan cannot be reactivated");
        }
        this.status = SubscriptionStatus.ACTIVE;
        this.cancelledAt = null;
        Instant now = Instant.now();
        if (!this.currentPeriodEnd.isAfter(now)) {
            startNewPeriod(now);
        }
        registerEvent(SubscriptionReactivatedEvent.of(getId(), organizationId, planType.name()));
    }

    /**
     * Reverts this subscription to the FREE plan (e.g. the external paid subscription ended at the
     * payment provider). Idempotent: a no-op when already on FREE.
     */
    public void downgradeToFree() {
        if (isFree()) {
            return;
        }
        String previous = this.planType.name();
        this.planType = PlanType.FREE;
        this.status = SubscriptionStatus.ACTIVE;
        this.providerRef = null;
        this.cancelledAt = null;
        startNewPeriod(Instant.now());
        registerEvent(SubscriptionDowngradedEvent.of(getId(), organizationId, previous));
    }

    /** Flags the subscription as past-due (e.g. a failed renewal payment). */
    public void markPastDue() {
        if (isFree()) {
            return;
        }
        this.status = SubscriptionStatus.PAST_DUE;
    }

    /**
     * Records AI token consumption against the current period, rolling the period over first when it
     * has elapsed (a fresh period resets the counter).
     *
     * @param tokens number of tokens consumed (must be positive to have any effect)
     */
    public void recordTokenUsage(long tokens) {
        if (tokens <= 0) {
            return;
        }
        rolloverIfPeriodElapsed(Instant.now());
        this.tokenQuotaUsed += tokens;
    }

    /**
     * Rolls the billing period forward (and zeroes the token counter) when the current period has
     * fully elapsed. Safe to call repeatedly.
     */
    public void rolloverIfPeriodElapsed(Instant now) {
        while (!this.currentPeriodEnd.isAfter(now)) {
            this.currentPeriodStart = this.currentPeriodEnd;
            this.currentPeriodEnd = this.currentPeriodStart.plus(PERIOD_LENGTH_DAYS, ChronoUnit.DAYS);
            this.tokenQuotaUsed = 0L;
        }
    }

    private void startNewPeriod(Instant now) {
        this.currentPeriodStart = now;
        this.currentPeriodEnd = now.plus(PERIOD_LENGTH_DAYS, ChronoUnit.DAYS);
        this.tokenQuotaUsed = 0L;
    }
}
