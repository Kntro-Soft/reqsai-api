package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Result of a batch user-story delete: how many of the requested stories were actually deleted
 * (candidate ids not found in the project are skipped and not counted).
 */
@Schema(description = "Result of a batch user-story delete")
public record BatchDeleteUserStoriesResponse(
        @Schema(description = "Number of stories actually deleted", example = "3")
        int deleted
) {}
