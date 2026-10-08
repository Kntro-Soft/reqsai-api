package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Stretches where two or more speakers talked at the same time, so the speaker attribution "
        + "of those stretches may be wrong")
public record SpeakerOverlapsResponse(

        @Schema(description = "Number of overlapping stretches", example = "2")
        int count,

        @Schema(description = "Total overlapping time in milliseconds", example = "2300")
        long totalMs,

        @Schema(description = "The stretches in time order (at most 50)")
        List<Range> ranges
) {

    @Schema(description = "One overlapping stretch")
    public record Range(

            @Schema(description = "Start, in milliseconds from the start of the recording", example = "61200")
            long startMs,

            @Schema(description = "End, in milliseconds from the start of the recording", example = "62900")
            long endMs,

            @Schema(description = "Labels of the speakers talking over each other", example = "[\"0\", \"1\"]")
            List<String> speakerLabels
    ) {
    }
}
