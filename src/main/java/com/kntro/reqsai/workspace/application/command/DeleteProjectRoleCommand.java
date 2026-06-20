package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

public record DeleteProjectRoleCommand(
        UUID organizationId,
        UUID projectId,
        UUID roleId,
        UUID requestedBy
) {}
