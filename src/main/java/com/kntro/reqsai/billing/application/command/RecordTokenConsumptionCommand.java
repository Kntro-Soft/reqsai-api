package com.kntro.reqsai.billing.application.command;

import java.util.UUID;

/**
 * Command to record AI token consumption against an organization's subscription.
 */
public record RecordTokenConsumptionCommand(UUID organizationId, long tokens) {}
