package com.kntro.reqsai.billing.application.query;

import java.util.UUID;

/**
 * Query to retrieve a Subscription by its organization ID.
 */
public record GetSubscriptionByOrganizationQuery(UUID organizationId) {}
