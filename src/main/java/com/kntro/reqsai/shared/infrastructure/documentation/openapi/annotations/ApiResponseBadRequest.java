package com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents a {@code 400 Bad Request} response (validation / malformed input).
 * Body is an RFC 9457 {@code ProblemDetail}; for bean-validation failures it includes
 * {@code fieldErrors}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(
        responseCode = "400",
        description = "Bad Request — validation failed or malformed input",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(example = """
                {
                  "type": "about:blank",
                  "title": "Bad Request",
                  "status": 400,
                  "detail": "Request validation failed",
                  "instance": "/api/workspaces",
                  "code": "VALIDATION_FAILED",
                  "correlationId": "a3f2c1d0-8e4b-4f1a-9d2e-4f1a8b3c5d6e",
                  "fieldErrors": [
                    { "field": "name", "message": "must not be blank", "rejectedValue": "" }
                  ]
                }""")))
public @interface ApiResponseBadRequest {
}
