package com.kntro.reqsai.workspace.application.query;

import java.util.UUID;

public record GetProjectMemberQuery(
        UUID organizationId,
        UUID projectId,
        UUID assignmentId,
        UUID requestedBy
) {}
