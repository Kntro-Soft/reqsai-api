package com.kntro.reqsai.workspace.application.query;

import java.util.UUID;

public record GetGlossaryTermQuery(
        UUID organizationId,
        UUID projectId,
        UUID termId,
        UUID requestedBy
) {}
