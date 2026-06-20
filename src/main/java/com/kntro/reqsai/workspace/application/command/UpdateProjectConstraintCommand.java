package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

public record UpdateProjectConstraintCommand(
        UUID organizationId,
        UUID projectId,
        UUID constraintId,
        String description,
        UUID requestedBy
) {}
