package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request body to change a project member role")
public record UpdateProjectMemberRequest(
        @Schema(description = "New project role id")
        @NotNull UUID roleId
) {}
