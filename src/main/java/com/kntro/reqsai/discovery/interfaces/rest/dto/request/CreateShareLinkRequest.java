package com.kntro.reqsai.discovery.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;

@Schema(description = "A link that lets a client review the project's stories without an account")
public record CreateShareLinkRequest(

        @Schema(description = "Days the link stays valid; 14 when omitted", example = "14",
                minimum = "1", maximum = "90", nullable = true)
        @Min(1) @Max(90)
        @Nullable Integer days
) {

    public static final int DEFAULT_DAYS = 14;

    public int daysOrDefault() {
        return days == null ? DEFAULT_DAYS : days;
    }
}
