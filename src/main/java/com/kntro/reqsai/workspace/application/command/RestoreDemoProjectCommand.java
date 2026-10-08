package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

/** Puts the organization's demo project back to its original sample content. */
public record RestoreDemoProjectCommand(
        UUID organizationId,
        UUID projectId,
        UUID requestedBy
) {}
