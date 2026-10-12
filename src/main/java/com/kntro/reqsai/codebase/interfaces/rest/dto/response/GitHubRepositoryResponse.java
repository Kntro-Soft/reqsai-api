package com.kntro.reqsai.codebase.interfaces.rest.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

@Schema(description = "A repository the organization's GitHub App installation shares with ReqsAI")
public record GitHubRepositoryResponse(
        long installationId,
        String owner,
        String name,
        @Schema(example = "acme/reservas") String fullName,
        String defaultBranch,
        String htmlUrl,
        @JsonProperty("private") @Schema(name = "private") boolean isPrivate,
        @Schema(nullable = true) @Nullable String description,
        @Schema(nullable = true) @Nullable Instant pushedAt,
        @Schema(description = "Whether the project already reads it") boolean connected
) {
}
