package com.kntro.reqsai.workspace.application.query;

import java.util.UUID;

/** Resolves the caller's authorization context (org role + base-permission floor) in an organization. */
public record GetOrganizationAuthorizationQuery(
        UUID organizationId,
        UUID requestedBy
) {}
