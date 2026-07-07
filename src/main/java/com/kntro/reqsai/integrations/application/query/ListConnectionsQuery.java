package com.kntro.reqsai.integrations.application.query;

import java.util.UUID;

/** List an organization's integration connections. */
public record ListConnectionsQuery(UUID organizationId, UUID requestedBy) {}
