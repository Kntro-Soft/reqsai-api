package com.kntro.reqsai.workspace.application.command;

import com.kntro.reqsai.workspace.domain.model.Permission;

import java.util.Set;
import java.util.UUID;

public record CreateProjectRoleCommand(
        UUID organizationId,
        UUID projectId,
        String name,
        Set<Permission> permissions,
        UUID requestedBy
) {}
