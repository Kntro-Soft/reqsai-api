package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

public record AddProjectConstraintCommand(
        UUID organizationId,
        UUID projectId,
        String description,
        UUID requestedBy
) {}
