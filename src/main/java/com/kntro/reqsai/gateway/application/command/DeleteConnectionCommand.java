package com.kntro.reqsai.gateway.application.command;

import java.util.UUID;

/** Delete an organization integration connection. */
public record DeleteConnectionCommand(UUID organizationId, UUID connectionId, UUID requestedBy) {}
