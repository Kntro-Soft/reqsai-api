package com.kntro.reqsai.codebase.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@Schema(description = "A module of the connected code as the copilot knows it (no source code)")
public record CodeModuleResponse(
        UUID id,
        @Schema(description = "Folder of the module; empty for the repository root") String path,
        String name,
        String summary,
        List<String> capabilities,
        @Schema(description = "Business rules the code implements, with their values") List<String> businessRules,
        List<String> endpoints,
        List<String> entities,
        int fileCount,
        @Schema(description = "The folder on the code host", nullable = true) @Nullable String url
) {
}
