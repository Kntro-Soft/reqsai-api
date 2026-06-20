package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

public record DeleteProjectConstraintCommand(
        UUID organizationId,
        UUID projectId,
        UUID constraintId,
        UUID requestedBy
) {}
