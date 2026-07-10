package com.kntro.reqsai.workspace.application.result;

import com.kntro.reqsai.workspace.domain.model.BasePermission;
import com.kntro.reqsai.workspace.domain.model.OrgRole;

/**
 * The caller's authorization context in an organization: their effective org role and the
 * organization-wide member base-permission floor.
 */
public record OrganizationAuthorization(
        OrgRole orgRole,
        BasePermission memberBasePermission
) {}
