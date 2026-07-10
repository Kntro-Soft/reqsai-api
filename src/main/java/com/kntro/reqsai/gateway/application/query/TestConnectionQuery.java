package com.kntro.reqsai.gateway.application.query;

import java.util.UUID;

/** Re-verify an organization connection's credential against the provider. */
public record TestConnectionQuery(UUID organizationId, UUID connectionId, UUID requestedBy) {}
