package com.kntro.reqsai.billing.application.query;

import java.util.UUID;

/**
 * Query to retrieve an organization's subscription usage (plan, limits, token consumption, price).
 */
public record GetSubscriptionUsageQuery(UUID organizationId) {}
