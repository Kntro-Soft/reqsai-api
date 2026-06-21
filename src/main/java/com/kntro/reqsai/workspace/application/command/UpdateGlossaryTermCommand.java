package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

public record UpdateGlossaryTermCommand(
        UUID organizationId,
        UUID projectId,
        UUID termId,
        String term,
        String definition,
        UUID requestedBy
) {}
