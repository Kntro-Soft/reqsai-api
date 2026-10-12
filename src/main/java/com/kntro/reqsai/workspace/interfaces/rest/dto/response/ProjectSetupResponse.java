package com.kntro.reqsai.workspace.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Where a project's setup stands. Also the payload pushed on {@code /topic/projects/{projectId}/setup}, so the page
 * renders the same object whether it comes from the socket or from a poll.
 */
@Schema(description = "Where a project's setup stands")
public record ProjectSetupResponse(
        UUID projectId,
        @Schema(description = "True while any step still has work ahead") boolean active,
        @Schema(description = "The steps the project asked for, in display order") List<Step> steps,
        @Schema(description = "The technology found in the indexed repositories", nullable = true)
        @Nullable DetectedStack detectedStack
) {

    @Schema(description = "One step of the setup")
    public record Step(
            @Schema(allowableValues = {"JIRA_LINK", "JIRA_IMPORT", "GITHUB_REPOS"}) String key,
            @Schema(allowableValues = {"PENDING", "RUNNING", "DONE", "FAILED", "SKIPPED"}) String status,
            @Schema(description = "Items finished so far (modules indexed, issues imported)") int done,
            @Schema(description = "Items to do; 0 while unknown") int total,
            @Schema(description = "A short note, such as the reason of a failure", nullable = true) @Nullable String detail
    ) {
    }

    @Schema(description = "Technology detected in the project's code")
    public record DetectedStack(List<String> languages, List<String> frameworks, List<String> databases,
                                List<String> platforms) {
    }
}
