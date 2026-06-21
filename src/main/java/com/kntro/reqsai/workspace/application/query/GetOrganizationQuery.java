package com.kntro.reqsai.workspace.application.query;

import java.util.UUID;

public record GetOrganizationQuery(
        UUID organizationId,
        UUID requestedBy
) {}
