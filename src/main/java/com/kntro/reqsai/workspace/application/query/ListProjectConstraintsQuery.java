package com.kntro.reqsai.workspace.application.query;

import java.util.UUID;

public record ListProjectConstraintsQuery(
        UUID organizationId,
        UUID projectId,
        UUID requestedBy
) {}
