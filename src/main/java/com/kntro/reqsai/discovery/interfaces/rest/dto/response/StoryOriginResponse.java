package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

@Schema(description = "Where a story was said: the session, the transcript segment and the verbatim fragment")
public record StoryOriginResponse(
        @Schema(description = "Session where it was said; null for a chat suggestion", nullable = true)
        @Nullable UUID sessionId,
        @Schema(description = "Sequence of the transcript segment, when it was located", nullable = true)
        @Nullable Integer sequence,
        @Schema(description = "Verbatim fragment of the conversation") String quote
) {
}
