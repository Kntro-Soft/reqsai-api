package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Who spoke in a session: the diarized speakers and where they talked over each other")
public record SessionSpeakersResponse(

        @Schema(description = "The session", example = "019756a0-1234-7abc-8def-000000000001")
        UUID sessionId,

        @Schema(description = "Speakers by first appearance; empty when the transcript has no speaker labels")
        List<SessionSpeakerResponse> speakers,

        @Schema(description = "Overlapping speech")
        SpeakerOverlapsResponse overlaps
) {
}
