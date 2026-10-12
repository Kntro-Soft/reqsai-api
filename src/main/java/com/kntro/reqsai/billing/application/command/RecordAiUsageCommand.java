package com.kntro.reqsai.billing.application.command;

import com.kntro.reqsai.shared.application.ai.TokenUsage;

import java.util.UUID;

/** Records one AI call of an organization in its usage ledger and on its monthly counter. */
public record RecordAiUsageCommand(UUID organizationId, TokenUsage usage) {}
