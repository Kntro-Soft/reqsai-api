package com.kntro.reqsai.codebase.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Schema(description = "Technical profile detected in a repository")
public record CodeProfileResponse(
        @Schema(description = "Languages of its source files") List<String> languages,
        @Schema(description = "Frameworks declared by its manifests") List<String> frameworks,
        @Schema(description = "Databases its dependencies point to") List<String> databases,
        @Schema(description = "Client platforms (Web, Android, iOS, Desktop)") List<String> platforms,
        @Schema(description = "What the product does, from its README and modules", nullable = true)
        @Nullable String overview
) {
}
