package com.kntro.reqsai.billing.domain.model.valueobjects;

/**
 * Defines the operational states of a Subscription.
 */
public enum SubscriptionStatus {
    ACTIVE,
    CANCELLED,
    PAST_DUE,
    TRIALING
}
