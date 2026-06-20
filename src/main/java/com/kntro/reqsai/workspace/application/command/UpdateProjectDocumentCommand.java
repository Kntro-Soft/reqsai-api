package com.kntro.reqsai.workspace.application.command;

import com.kntro.reqsai.workspace.domain.model.DocumentType;

import java.util.UUID;

public record UpdateProjectDocumentCommand(
        UUID organizationId,
        UUID projectId,
        UUID documentId,
        String name,
        DocumentType documentType,
        UUID requestedBy
) {}
