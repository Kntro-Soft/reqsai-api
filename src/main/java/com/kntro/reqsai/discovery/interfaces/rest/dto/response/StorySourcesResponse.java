package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Where a story comes from in the meetings")
public record StorySourcesResponse(

        @Schema(description = "The story", example = "019756a0-1234-7abc-8def-000000000010")
        UUID storyId,

        @Schema(description = "SESSION: the story has fragments of a meeting. UNAVAILABLE: it was generated in a "
                + "session but no fragment was kept (a story from before fragments were stored). MANUAL: it does "
                + "not come from a session.", example = "SESSION",
                allowableValues = {"SESSION", "UNAVAILABLE", "MANUAL"})
        String origin,

        @Schema(description = "Session the story was generated in; only set when origin is UNAVAILABLE",
                nullable = true)
        @Nullable UUID sessionId,

        @Schema(description = "Title of that session; null when it no longer exists", nullable = true)
        @Nullable String sessionTitle,

        @Schema(description = "When that session started; null when it no longer exists", nullable = true)
        @Nullable Instant sessionStartedAt,

        @Schema(description = "Fragments of the meetings the story comes from, oldest first; empty unless origin "
                + "is SESSION")
        List<StorySourceFragmentResponse> fragments
) {
}
