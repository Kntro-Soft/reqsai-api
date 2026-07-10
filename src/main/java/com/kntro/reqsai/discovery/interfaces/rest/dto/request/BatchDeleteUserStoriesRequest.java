package com.kntro.reqsai.discovery.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Request body to delete several user stories of a project in one call. Ids not belonging to the
 * project are silently skipped; the response reports how many were actually deleted.
 */
@Schema(description = "Request body to delete several user stories in one call")
public record BatchDeleteUserStoriesRequest(
        @Schema(description = "Ids of the stories to delete (ids not in the project are skipped)",
                example = "[\"019756a0-1234-7abc-8def-000000000010\",\"019756a0-1234-7abc-8def-000000000011\"]")
        @NotEmpty @Size(max = 200) List<UUID> storyIds
) {}
