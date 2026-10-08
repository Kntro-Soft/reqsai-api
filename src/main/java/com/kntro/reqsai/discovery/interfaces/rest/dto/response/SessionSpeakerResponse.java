package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@Schema(description = "A diarized speaker of a session")
public record SessionSpeakerResponse(

        @Schema(description = "Diarization label the speech-to-text provider gave the speaker", example = "0")
        String label,

        @Schema(description = "1-based position by first appearance in the transcript", example = "1")
        int index,

        @Schema(description = "Name the analyst gave; null when not named", example = "Ana Torres", nullable = true)
        @Nullable String displayName,

        @Schema(description = "Name to show: displayName, else \"Hablante {index}\"", example = "Ana Torres")
        String name,

        @Schema(description = "Side of the meeting; null when not set", example = "CLIENT",
                allowableValues = {"CLIENT", "TEAM"}, nullable = true)
        @Nullable String side,

        @Schema(description = "Final transcript segments attributed to the speaker", example = "14")
        int segmentCount
) {
}
