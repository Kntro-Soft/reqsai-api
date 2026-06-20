package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

public record ArchiveProjectCommand(
        UUID organizationId,
        UUID projectId,
        UUID requestedBy
) {}
