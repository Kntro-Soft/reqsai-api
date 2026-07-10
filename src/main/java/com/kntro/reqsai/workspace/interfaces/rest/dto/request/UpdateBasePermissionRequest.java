package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import com.kntro.reqsai.workspace.domain.model.BasePermission;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request body to set the organization's member base permission floor")
public record UpdateBasePermissionRequest(

        @Schema(
                description = "RBAC floor applied to every project member on top of their project role",
                example = "READ",
                allowableValues = {"NONE", "READ"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull BasePermission basePermission
) {}
