package com.kntro.reqsai.workspace.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Request body to create a project. Only {@code name} is required: the technical profile is optional
 * context the UI collects under "advanced". A missing list is treated as empty; a missing
 * architecture/domain is treated as unset.
 */
@Schema(description = "Request body to create a project")
public record CreateProjectRequest(
        @Schema(description = "Project name", minLength = 1, maxLength = 150, example = "My Awesome Project")
        @NotBlank @Size(max = 150)
        String name,

        @Schema(description = "Project description", maxLength = 2000, nullable = true, example = "A web application for requirement elicitation and management")
        @Size(max = 2000)
        @Nullable String description,

        @Schema(description = "List of programming languages used", nullable = true, example = "[\"Java\", \"TypeScript\"]")
        @Nullable List<String> programmingLanguages,

        @Schema(description = "List of frameworks used", nullable = true, example = "[\"Spring Boot\", \"Next.js\"]")
        @Nullable List<String> frameworks,

        @Schema(description = "List of client platforms", nullable = true, example = "[\"Web\", \"Mobile\"]")
        @Nullable List<String> clientPlatforms,

        @Schema(description = "List of database systems", nullable = true, example = "[\"PostgreSQL\", \"Redis\"]")
        @Nullable List<String> databases,

        @Schema(description = "Architectural style", nullable = true, example = "Clean Architecture")
        @Size(max = 100)
        @Nullable String architecture,

        @Schema(description = "Business Domain", nullable = true, example = "Fintech")
        @Size(max = 100)
        @Nullable String domain,

        @Schema(description = "External sources to wire up right after the project is created: a Jira project and "
                + "repositories. The project opens at once and they are set up in the background.", nullable = true)
        @Valid
        @Nullable Sources sources
) {

    @Schema(description = "External sources of a new project")
    public record Sources(
            @Schema(description = "The Jira project to link", nullable = true)
            @Valid
            @Nullable Jira jira,

            @Schema(description = "Repositories to connect and index (at most the per-project limit)", nullable = true)
            @Valid
            @Nullable List<@Valid GitHub> github
    ) {
    }

    @Schema(description = "A Jira project to link as the push target")
    public record Jira(
            @Schema(description = "The organization's Jira connection; its active one when omitted", nullable = true)
            @Nullable UUID connectionId,

            @Schema(description = "Jira project key", example = "RES", maxLength = 100)
            @NotBlank @Size(max = 100)
            String projectKey,

            @Schema(description = "Issue type stories are pushed as", example = "Story", maxLength = 100)
            @NotBlank @Size(max = 100)
            String issueTypeName,

            @Schema(description = "Import the project's existing issues as stories", defaultValue = "false")
            boolean importStories
    ) {
    }

    @Schema(description = "A repository to connect and index")
    public record GitHub(
            @Schema(description = "owner/name or a github.com URL", example = "acme/reservas", maxLength = 300)
            @NotBlank @Size(max = 300)
            String repository,

            @Schema(description = "Branch to read; the default branch when blank", nullable = true, maxLength = 255)
            @Size(max = 255)
            @Nullable String branch,

            @Schema(description = "The GitHub App installation that shares it, from the picker", nullable = true)
            @Nullable Long installationId
    ) {
    }
}
