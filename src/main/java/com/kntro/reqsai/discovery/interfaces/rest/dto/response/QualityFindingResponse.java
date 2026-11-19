package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@Schema(description = "How a story fares on one quality characteristic")
public record QualityFindingResponse(

        @Schema(description = "Characteristic evaluated", example = "AMBIGUITY",
                allowableValues = {"AMBIGUITY", "ATOMICITY", "VERIFIABILITY", "COMPLETENESS"})
        String characteristic,

        @Schema(description = "Score of this characteristic, from 0 to 100", example = "75", minimum = "0", maximum = "100")
        int score,

        @Schema(description = "True when the characteristic is fully met (score 100)", example = "false")
        boolean met,

        @Schema(description = "What the evaluation found, in the language of the story",
                example = "Se detectaron términos ambiguos: «rápido».")
        String observation,

        @Schema(description = "How to fix it before approving the story; null when the characteristic is met",
                nullable = true,
                example = "Reemplaza cada término por un valor medible o una lista cerrada.")
        @Nullable String suggestion
) {
}
