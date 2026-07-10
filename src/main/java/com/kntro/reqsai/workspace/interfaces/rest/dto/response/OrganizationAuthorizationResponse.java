package com.kntro.reqsai.workspace.interfaces.rest.dto.response;

import com.kntro.reqsai.workspace.domain.model.BasePermission;
import com.kntro.reqsai.workspace.domain.model.OrgRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The caller's authorization context within an organization")
public record OrganizationAuthorizationResponse(

        @Schema(
                description = "The caller's role in the organization",
                example = "MEMBER",
                allowableValues = {"OWNER", "ADMIN", "MEMBER"})
        OrgRole orgRole,

        @Schema(
                description = "The organization's member base permission floor",
                example = "READ",
                allowableValues = {"NONE", "READ"})
        BasePermission memberBasePermission
) {}
