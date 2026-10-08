package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A client's approval of, or comment on, a story, left through a share link")
public record StoryFeedbackResponse(

        @Schema(description = "Feedback identifier")
        UUID id,

        @Schema(description = "Story the feedback is about")
        UUID storyId,

        @Schema(description = "What the client did", allowableValues = {"APPROVAL", "COMMENT"})
        String kind,

        @Schema(description = "Name the client signed with")
        String authorName,

        @Schema(description = "The comment, or the note that came with an approval", nullable = true)
        @Nullable String comment,

        @Schema(description = "When the client left it")
        Instant createdAt
) {
}
