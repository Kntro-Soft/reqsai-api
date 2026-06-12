package com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bundles the error responses common to most secured endpoints (401, 403, 404, 409, 500) as RFC 9457
 * {@code ProblemDetail}s, so controllers don't repeat them.
 *
 * <pre>{@code
 * @GetMapping("/{id}")
 * @ApiStandardErrorResponses
 * public WorkspaceDto get(@PathVariable UUID id) { ... }
 * }</pre>
 *
 * Add {@link ApiResponseBadRequest} on endpoints that take a request body to also document 400.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Unauthorized",
                content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = "#/components/schemas/ProblemDetail"))),
        @ApiResponse(responseCode = "403", description = "Forbidden",
                content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = "#/components/schemas/ProblemDetail"))),
        @ApiResponse(responseCode = "404", description = "Not Found",
                content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = "#/components/schemas/ProblemDetail"))),
        @ApiResponse(responseCode = "409", description = "Conflict",
                content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = "#/components/schemas/ProblemDetail"))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error",
                content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
})
public @interface ApiStandardErrorResponses {
}
