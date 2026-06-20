package com.kntro.reqsai.workspace.application.query;

import java.util.UUID;

public record ListGlossaryTermsQuery(
        UUID organizationId,
        UUID projectId,
        UUID requestedBy
) {}
