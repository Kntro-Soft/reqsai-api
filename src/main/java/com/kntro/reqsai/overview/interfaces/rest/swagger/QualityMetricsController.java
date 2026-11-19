package com.kntro.reqsai.overview.interfaces.rest.swagger;

import com.kntro.reqsai.overview.interfaces.rest.dto.response.QualityMetricsResponse;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.UUID;

/**
 * API contract for the quality indicators of a project; implemented by
 * {@code controllers.QualityMetricsControllerImpl}. Read-only, tenant-scoped (the schema comes from the JWT
 * {@code orgId}).
 */
@RequestMapping(path = ApiVersioning.BASE, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Quality metrics", description = "Indicators to validate the pilots' success criteria: stories accepted without major rewriting, post-processing time and clarification meetings")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
public interface QualityMetricsController {

    @Operation(summary = "Quality indicators of a project", description = """
            Three indicators of one project over a period of UTC days (both ends included; the last 30 days \
            up to today when no dates are sent): the share of AI-generated stories accepted without major \
            rewriting (final text at least 80% similar to the text the AI proposed, over the stories approved \
            or rejected in the period), the time from the end of each meeting to the last review decision on \
            its stories (average and median), and the clarification meetings held. An indicator that cannot \
            be computed is null, never zero; `hasData` is false when no meeting ended and no story was \
            decided in the period. Requires STORY_READ and SESSION_READ on the project. The period may not \
            exceed 366 days and `from` may not be after `to`. The formulas are in docs/QUALITY_INDICATORS.md.""")
    @ApiResponse(responseCode = "200", description = "The project's indicators for the period", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = QualityMetricsResponse.class), examples = @ExampleObject(value = """
            {
              "from": "2026-10-20", "to": "2026-11-18", "hasData": true, "sessions": 6,
              "acceptance": { "similarityThreshold": 0.8, "decided": 10, "accepted": 8, "acceptedAsGenerated": 6,
                              "acceptedRewritten": 2, "rejected": 2, "withoutBaseline": 1, "rate": 0.6 },
              "postProcessing": { "measured": 4, "unmeasured": 2, "averageSeconds": 5400, "medianSeconds": 3600,
                                  "sessions": [] },
              "clarification": { "meetings": 2, "sessions": 6, "share": 0.33 }
            }""")))
    @ApiResponseBadRequest
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @GetMapping(path = "/projects/{projectId}/quality-metrics", version = ApiVersioning.V1)
    ResponseEntity<QualityMetricsResponse> project(
            @Parameter(description = "Project to measure", required = true) @PathVariable UUID projectId,
            @Parameter(description = "First day of the period (ISO date, UTC); defaults to 29 days before `to`", example = "2026-10-20") @RequestParam(required = false) LocalDate from,
            @Parameter(description = "Last day of the period (ISO date, UTC); defaults to today", example = "2026-11-18") @RequestParam(required = false) LocalDate to,
            Authentication authentication);
}
