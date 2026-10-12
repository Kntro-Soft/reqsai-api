package com.kntro.reqsai.billing.domain.exception;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Factory for billing-context domain exceptions.
 */
public final class BillingExceptions {

    private BillingExceptions() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static EntityNotFoundException subscriptionNotFoundByOrganization(UUID organizationId) {
        return new EntityNotFoundException(
                BillingError.SUBSCRIPTION_NOT_FOUND,
                "Subscription not found for organization: " + organizationId
        );
    }

    public static DomainException subscriptionAlreadyExists(UUID organizationId) {
        return new DomainException(
                BillingError.SUBSCRIPTION_ALREADY_EXISTS,
                "Subscription already exists for organization: " + organizationId
        );
    }

    public static DomainException planNotPurchasable(String planType) {
        return new DomainException(
                BillingError.PLAN_NOT_PURCHASABLE,
                "Plan is not purchasable: " + planType
        );
    }

    public static DomainException invalidPlanChange(String detail) {
        return new DomainException(BillingError.INVALID_PLAN_CHANGE, detail);
    }

    public static DomainException invalidSubscriptionState(String detail) {
        return new DomainException(BillingError.INVALID_SUBSCRIPTION_STATE, detail);
    }

    public static DomainException paymentGatewayError(String detail) {
        return new DomainException(BillingError.PAYMENT_GATEWAY_ERROR, detail);
    }

    public static DomainException paymentGatewayError(String detail, Throwable cause) {
        return new DomainException(BillingError.PAYMENT_GATEWAY_ERROR, detail, cause);
    }

    public static DomainException webhookSignatureInvalid(String detail) {
        return new DomainException(BillingError.WEBHOOK_SIGNATURE_INVALID, detail);
    }

    /**
     * The monthly AI allowance is spent. Carries the plan, limit, used and reset instant so a client can
     * explain it and offer an upgrade.
     */
    public static DomainException aiQuotaExceeded(String planType, long limit, long used, Instant resetsAt) {
        return new DomainException(
                BillingError.AI_QUOTA_EXCEEDED,
                "The monthly AI allowance of the " + planType + " plan is used up (" + used + " of " + limit
                        + " tokens). Reading, editing and exporting still work.",
                Map.of("plan", planType, "limit", limit, "used", used, "resetsAt", resetsAt.toString())
        );
    }
}
