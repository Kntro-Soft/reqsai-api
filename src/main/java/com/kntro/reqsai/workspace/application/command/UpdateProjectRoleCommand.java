package com.kntro.reqsai.workspace.application.command;

import com.kntro.reqsai.workspace.domain.model.Permission;

import java.util.Set;
import java.util.UUID;

public record UpdateProjectRoleCommand(
        UUID organizationId,
        UUID projectId,
        UUID roleId,
        String name,
        Set<Permission> permissions,
        UUID requestedBy
) {}
