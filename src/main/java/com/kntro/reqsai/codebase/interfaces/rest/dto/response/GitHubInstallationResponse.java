package com.kntro.reqsai.codebase.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

@Schema(description = "A GitHub account where the organization installed the ReqsAI GitHub App")
public record GitHubInstallationResponse(
        long installationId,
        @Schema(description = "GitHub login of the account", example = "acme") String account,
        @Schema(allowableValues = {"Organization", "User"}) String accountType,
        @Schema(description = "Whether all the account's repositories are shared, or a selection",
                allowableValues = {"all", "selected"}, nullable = true) @Nullable String repositorySelection,
        @Schema(description = "Where to change the shared repositories or uninstall the App, on GitHub",
                nullable = true) @Nullable String manageUrl,
        boolean suspended,
        Instant connectedAt
) {
}
