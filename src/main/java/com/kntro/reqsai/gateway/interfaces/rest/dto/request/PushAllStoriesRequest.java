package com.kntro.reqsai.gateway.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Optional request body for the Jira push-all. {@code storyIds} restricts the push to the given stories;
 * omit the body or leave it empty to push every eligible story of the project (the original behaviour).
 * Ids not belonging to the project are ignored.
 */
@Schema(description = "Optional request body to push a selection of stories to Jira")
public record PushAllStoriesRequest(
        @Schema(description = "Specific story ids to push; omit/empty = all eligible stories",
                example = "[\"019756a0-1234-7abc-8def-000000000010\"]")
        @Nullable List<UUID> storyIds
) {}
