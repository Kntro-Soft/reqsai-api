package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "A discovery session shared through a link: its state, latest transcript, who spoke, the "
        + "suggestions waiting for a decision and the stories it produced")
public record SharedSessionResponse(
        @Schema(description = "Name of the project the session belongs to")
        String projectName,
        @Schema(description = "What the visitor may do: VIEW (only follow along) or REVIEW (also decide)",
                allowableValues = {"VIEW", "REVIEW"})
        String access,
        @Schema(description = "When the link stops working")
        Instant expiresAt,
        @Schema(description = "The session")
        DiscoverySessionResponse session,
        @Schema(description = "The latest finalized transcript segments, oldest first")
        List<TranscriptSegmentResponse> segments,
        @Schema(description = "Finalized segments in the whole session")
        long totalSegments,
        @Schema(description = "Who spoke")
        SessionSpeakersResponse speakers,
        @Schema(description = "Suggestions still waiting for a decision")
        List<SuggestionResponse> suggestions,
        @Schema(description = "Stories the session produced; merged ones are not shown")
        List<UserStoryResponse> stories
) {
}
