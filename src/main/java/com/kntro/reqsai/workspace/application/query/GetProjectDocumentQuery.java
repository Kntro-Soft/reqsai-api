package com.kntro.reqsai.workspace.application.query;

import java.util.UUID;

public record GetProjectDocumentQuery(
        UUID organizationId,
        UUID projectId,
        UUID documentId,
        UUID requestedBy
) {}
