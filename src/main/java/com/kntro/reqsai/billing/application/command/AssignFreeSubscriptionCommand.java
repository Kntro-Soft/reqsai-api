package com.kntro.reqsai.billing.application.command;

import java.util.UUID;

/**
 * Command to assign a free plan subscription to an organization.
 */
public record AssignFreeSubscriptionCommand(UUID organizationId) {}
