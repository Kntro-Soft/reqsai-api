package com.kntro.reqsai.billing.application.command;

import com.kntro.reqsai.billing.domain.model.valueobjects.PlanType;

import java.util.UUID;

/**
 * Command to move an organization's subscription to a paid plan.
 */
public record UpgradeSubscriptionCommand(UUID organizationId, PlanType targetPlan) {}
