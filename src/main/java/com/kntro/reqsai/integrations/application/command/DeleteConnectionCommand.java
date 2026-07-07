package com.kntro.reqsai.integrations.application.command;

import java.util.UUID;

/** Delete an organization integration connection. */
public record DeleteConnectionCommand(UUID organizationId, UUID connectionId, UUID requestedBy) {}
