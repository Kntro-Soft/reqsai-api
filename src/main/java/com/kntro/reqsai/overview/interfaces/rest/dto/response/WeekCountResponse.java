package com.kntro.reqsai.overview.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Rows created during one week")
public record WeekCountResponse(
        @Schema(description = "Monday (UTC) the week starts on", example = "2026-10-05") LocalDate weekStart,
        @Schema(description = "Rows created that week", example = "3") long count) {
}
