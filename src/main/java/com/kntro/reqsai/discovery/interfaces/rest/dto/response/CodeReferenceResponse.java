package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@Schema(description = "A module or a place in the client's connected code")
public record CodeReferenceResponse(
        @Schema(description = "Repository (owner/name)", example = "acme/reservas") String repository,
        @Schema(description = "Folder of the module; empty for the repository root") String path,
        @Schema(description = "Business name of the module, or the file name for a deep-search reference",
                example = "Reservas") String name,
        @Schema(description = "The module, or the lines, on the code host", nullable = true) @Nullable String url,
        @Schema(description = "File of the code the reference points at (deep search only)", nullable = true)
        @Nullable String file,
        @Schema(description = "First line of the reference in the file", nullable = true) @Nullable Integer lineStart,
        @Schema(description = "Last line of the reference in the file", nullable = true) @Nullable Integer lineEnd,
        @Schema(description = "Short quote of the code that proves it (deep search only)", nullable = true)
        @Nullable String quote
) {

    public CodeReferenceResponse(String repository, String path, String name, @Nullable String url) {
        this(repository, path, name, url, null, null, null, null);
    }
}
