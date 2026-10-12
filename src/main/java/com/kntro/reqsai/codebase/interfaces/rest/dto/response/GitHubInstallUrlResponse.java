package com.kntro.reqsai.codebase.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The GitHub page where the organization installs the ReqsAI GitHub App")
public record GitHubInstallUrlResponse(
        @Schema(example = "https://github.com/apps/reqsai/installations/new?state=…") String url
) {
}
