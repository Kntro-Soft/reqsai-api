package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import com.kntro.reqsai.workspace.domain.model.Permission;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

@Schema(description = "Request body to replace a project role")
public record UpdateProjectRoleRequest(
        @Schema(description = "Project role display name", example = "Lead Analyst", maxLength = 100)
        @NotBlank @Size(max = 100) String name,

        @Schema(description = "Permissions granted by the role")
        @NotEmpty Set<Permission> permissions
) {}
