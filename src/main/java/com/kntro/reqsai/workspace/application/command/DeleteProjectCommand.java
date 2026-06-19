package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

public record DeleteProjectCommand(
        UUID organizationId,
        UUID projectId,
        UUID requestedBy
) {}
