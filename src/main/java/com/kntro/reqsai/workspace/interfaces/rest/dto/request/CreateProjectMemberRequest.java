package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request body to assign a member to a project")
public record CreateProjectMemberRequest(
        @Schema(description = "Organization member id to assign")
        @NotNull UUID memberId,

        @Schema(description = "Project role id to assign to the member")
        @NotNull UUID roleId
) {}
