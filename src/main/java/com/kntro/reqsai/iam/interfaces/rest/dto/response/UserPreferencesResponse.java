package com.kntro.reqsai.iam.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.util.UUID;

@Schema(description = "User navigation preferences")
public record UserPreferencesResponse(

        @Nullable
        @Schema(description = "ID of the last organization the user visited", nullable = true)
        UUID lastVisitedOrgId,

        @Nullable
        @Schema(description = "ID of the last project the user visited", nullable = true)
        UUID lastVisitedProjectId
) {
}
