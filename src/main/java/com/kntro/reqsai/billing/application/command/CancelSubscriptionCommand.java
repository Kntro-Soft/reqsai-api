package com.kntro.reqsai.billing.application.command;

import java.util.UUID;

/**
 * Command to cancel an organization's paid subscription.
 */
public record CancelSubscriptionCommand(UUID organizationId) {}
