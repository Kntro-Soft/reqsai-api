package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import com.kntro.reqsai.workspace.domain.model.OrgRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request body to change an organization member role")
public record ChangeMemberRoleRequest(
        @Schema(description = "New organization role", allowableValues = {"ADMIN", "MEMBER"}, example = "ADMIN")
        @NotNull OrgRole role
) {}
