package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request body to change an organization member status (deactivate or reactivate)")
public record ChangeMemberStatusRequest(
        @Schema(description = "New member status", allowableValues = {"ACTIVE", "INACTIVE"}, example = "INACTIVE")
        @NotNull MemberStatus status
) {}
