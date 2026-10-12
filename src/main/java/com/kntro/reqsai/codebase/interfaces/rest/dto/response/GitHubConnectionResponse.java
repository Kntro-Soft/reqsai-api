package com.kntro.reqsai.codebase.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "The organization's GitHub connection")
public record GitHubConnectionResponse(
        @Schema(description = "Whether this server has the ReqsAI GitHub App configured; when false only public"
                + " repositories can be connected") boolean available,
        List<GitHubInstallationResponse> installations
) {
}
