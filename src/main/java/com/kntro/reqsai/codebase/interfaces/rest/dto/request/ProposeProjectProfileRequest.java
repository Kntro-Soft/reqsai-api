package com.kntro.reqsai.codebase.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Schema(description = "The sources a project that does not exist yet was pointed at")
public record ProposeProjectProfileRequest(
        @Schema(description = "Repositories picked from the organization's GitHub installations (at most 3)")
        @Size(max = 3) @Valid List<Repository> repositories,
        @Schema(description = "Name of the picked Jira project", nullable = true) @Size(max = 300) @Nullable String jiraName,
        @Schema(description = "Description of the picked Jira project", nullable = true)
        @Size(max = 4000) @Nullable String jiraDescription,
        @Schema(description = "Language to write the proposal in (es, en, es-PE...); Spanish when omitted", nullable = true)
        @Size(max = 16) @Nullable String language
) {

    public ProposeProjectProfileRequest {
        repositories = repositories == null ? List.of() : repositories;
    }

    @Schema(description = "A repository of one of the organization's GitHub installations")
    public record Repository(
            @Schema(description = "GitHub installation that shares it") long installationId,
            @Schema(example = "acme/reservas") @NotBlank @Size(max = 300) String fullName
    ) {
    }
}
