package com.kntro.reqsai.workspace.application.command;

import com.kntro.reqsai.workspace.domain.model.DocumentType;

import java.util.UUID;

public record CreateProjectDocumentCommand(
        UUID organizationId,
        UUID projectId,
        String name,
        DocumentType documentType,
        UUID requestedBy
) {}
