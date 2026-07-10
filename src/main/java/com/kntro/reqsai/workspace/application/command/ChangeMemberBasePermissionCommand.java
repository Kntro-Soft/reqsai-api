package com.kntro.reqsai.workspace.application.command;

import com.kntro.reqsai.workspace.domain.model.BasePermission;

import java.util.UUID;

/** Sets the organization-wide RBAC floor applied to every project member. */
public record ChangeMemberBasePermissionCommand(
        UUID organizationId,
        BasePermission basePermission,
        UUID requestedBy
) {}
