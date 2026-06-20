package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

public record RestoreProjectCommand(
        UUID organizationId,
        UUID projectId,
        UUID requestedBy
) {}
