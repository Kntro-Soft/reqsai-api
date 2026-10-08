package com.kntro.reqsai.discovery.interfaces.rest.dto.request;

import com.kntro.reqsai.discovery.domain.model.StoryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request body to record the review decision on a user story")
public record ChangeUserStoryStatusRequest(

        @Schema(description = "New review status", example = "APPROVED", requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"DRAFT", "APPROVED", "REJECTED"})
        @NotNull
        StoryStatus status
) {
}
