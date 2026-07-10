package com.kntro.reqsai.workspace.interfaces.rest.mappers.response;

import com.kntro.reqsai.workspace.application.result.OrganizationAuthorization;
import com.kntro.reqsai.workspace.domain.model.BasePermission;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.BasePermissionResponse;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.OrganizationAuthorizationResponse;

/** Maps organization authorization results to their response DTOs. */
public final class OrganizationAuthorizationResponseMapper {

    private OrganizationAuthorizationResponseMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static BasePermissionResponse toResponse(BasePermission basePermission) {
        return new BasePermissionResponse(basePermission);
    }

    public static OrganizationAuthorizationResponse toResponse(OrganizationAuthorization authorization) {
        return new OrganizationAuthorizationResponse(
                authorization.orgRole(),
                authorization.memberBasePermission());
    }
}
