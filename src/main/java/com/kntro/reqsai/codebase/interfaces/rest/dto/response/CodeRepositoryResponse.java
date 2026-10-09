package com.kntro.reqsai.codebase.interfaces.rest.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A repository connected to the project and where its index stands")
public record CodeRepositoryResponse(
        UUID id,
        UUID projectId,
        @Schema(allowableValues = {"GITHUB"}) String provider,
        String owner,
        String name,
        @Schema(example = "acme/reservas") String fullName,
        String branch,
        String htmlUrl,
        @JsonProperty("private") @Schema(name = "private") boolean isPrivate,
        @Schema(description = "Whether an access token is stored (it is never returned)") boolean hasToken,
        @Schema(allowableValues = {"PENDING", "INDEXING", "READY", "FAILED"}) String status,
        @Schema(nullable = true) @Nullable String error,
        @Schema(description = "Indexed commit", nullable = true) @Nullable String commitSha,
        @Schema(nullable = true) @Nullable Instant indexedAt,
        int fileCount,
        int moduleCount,
        int modulesDone,
        @Schema(description = "False when no AI model was available: modules have structural descriptions")
        boolean summarized,
        CodeProfileResponse profile,
        Instant createdAt
) {
}
