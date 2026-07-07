package com.kntro.reqsai.billing.application.command;

import java.util.UUID;

/**
 * Command to reactivate an organization's cancelled subscription.
 */
public record ReactivateSubscriptionCommand(UUID organizationId) {}
