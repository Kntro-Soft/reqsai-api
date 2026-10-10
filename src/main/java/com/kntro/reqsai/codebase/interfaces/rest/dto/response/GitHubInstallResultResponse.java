package com.kntro.reqsai.codebase.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@Schema(description = "How the install ended")
public record GitHubInstallResultResponse(
        @Schema(description = "LINKED to the organization, or REQUESTED: a GitHub organization owner must approve it",
                allowableValues = {"LINKED", "REQUESTED"}) String status,
        @Schema(nullable = true) @Nullable GitHubInstallationResponse installation
) {
}
