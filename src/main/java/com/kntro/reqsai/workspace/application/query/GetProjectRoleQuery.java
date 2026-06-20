package com.kntro.reqsai.workspace.application.query;

import java.util.UUID;

public record GetProjectRoleQuery(
        UUID organizationId,
        UUID projectId,
        UUID roleId,
        UUID requestedBy
) {}
