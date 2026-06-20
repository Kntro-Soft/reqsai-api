package com.kntro.reqsai.workspace.application.query;

import java.util.UUID;

public record GetProjectConstraintQuery(
        UUID organizationId,
        UUID projectId,
        UUID constraintId,
        UUID requestedBy
) {}
