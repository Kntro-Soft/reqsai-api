package com.kntro.reqsai.overview.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

@Schema(description = "How much of the client's code is connected")
public record CodeOverviewResponse(@Schema(description = "Connected repositories") long repositories,
        @Schema(description = "Repositories whose module map is ready") long ready,
        @Schema(description = "Repositories whose last indexing failed") long failed,
        @Schema(description = "Indexed modules") long modules,
        @Schema(description = "Source files read while indexing") long files,
        @Schema(description = "When a repository was last indexed", nullable = true) @Nullable Instant lastIndexedAt) {
}
