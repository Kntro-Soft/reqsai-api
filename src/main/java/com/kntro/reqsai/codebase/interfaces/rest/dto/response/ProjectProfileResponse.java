package com.kntro.reqsai.codebase.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Schema(description = "A proposed profile for a new project, from its Jira and GitHub sources")
public record ProjectProfileResponse(
        @Schema(description = "True when the AI wrote it; false when it only repeats the sources' metadata")
        boolean generated,
        @Nullable String name,
        @Nullable String description,
        @Schema(description = "Business area", nullable = true) @Nullable String domain,
        @Schema(description = "Architectural style, when the sources show one", nullable = true) @Nullable String architecture,
        List<String> programmingLanguages,
        List<String> frameworks,
        List<String> clientPlatforms,
        List<String> databases
) {
}
