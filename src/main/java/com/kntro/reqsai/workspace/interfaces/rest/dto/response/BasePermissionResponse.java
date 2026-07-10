package com.kntro.reqsai.workspace.interfaces.rest.dto.response;

import com.kntro.reqsai.workspace.domain.model.BasePermission;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The organization's member base permission floor")
public record BasePermissionResponse(

        @Schema(
                description = "RBAC floor applied to every project member on top of their project role",
                example = "READ",
                allowableValues = {"NONE", "READ"})
        BasePermission basePermission
) {}
