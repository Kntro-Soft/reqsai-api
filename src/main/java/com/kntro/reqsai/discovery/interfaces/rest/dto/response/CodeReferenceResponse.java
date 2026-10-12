package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@Schema(description = "A module of the client's connected code")
public record CodeReferenceResponse(
        @Schema(description = "Repository (owner/name)", example = "acme/reservas") String repository,
        @Schema(description = "Folder of the module; empty for the repository root") String path,
        @Schema(description = "Business name of the module", example = "Reservas") String name,
        @Schema(description = "The module on the code host", nullable = true) @Nullable String url
) {
}
