package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

public record DeleteGlossaryTermCommand(
        UUID organizationId,
        UUID projectId,
        UUID termId,
        UUID requestedBy
) {}
