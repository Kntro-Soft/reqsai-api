package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

public record DeleteProjectDocumentCommand(
        UUID organizationId,
        UUID projectId,
        UUID documentId,
        UUID requestedBy
) {}
