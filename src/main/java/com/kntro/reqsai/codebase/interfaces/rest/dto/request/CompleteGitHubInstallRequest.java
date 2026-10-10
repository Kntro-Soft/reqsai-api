package com.kntro.reqsai.codebase.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

@Schema(description = "What GitHub put in the redirect after installing (or updating) the ReqsAI GitHub App")
public record CompleteGitHubInstallRequest(

        @Schema(description = "installation_id; absent when a GitHub organization member only requested the install",
                nullable = true, example = "1001")
        @Positive
        @Nullable Long installationId,

        @Schema(description = "setup_action", allowableValues = {"install", "update", "request"}, nullable = true)
        @Size(max = 16)
        @Nullable String setupAction,

        @Schema(description = "The signed state issued when the install started", nullable = true, maxLength = 1000)
        @Size(max = 1000)
        @Nullable String state,

        @Schema(description = "The OAuth code GitHub adds to the redirect; proves who installed the App",
                nullable = true, maxLength = 200)
        @Size(max = 200)
        @Nullable String code
) {
}
