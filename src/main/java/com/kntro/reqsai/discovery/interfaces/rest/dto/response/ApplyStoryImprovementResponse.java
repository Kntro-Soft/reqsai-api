package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The story after the improvement was applied, with its quality evaluated again and stored")
public record ApplyStoryImprovementResponse(

        @Schema(description = "The updated story, with the acceptance criteria of the proposal")
        UserStoryResponse story,

        @Schema(description = "The quality of the story after the change; this is the stored assessment")
        StoryQualityResponse quality
) {
}
