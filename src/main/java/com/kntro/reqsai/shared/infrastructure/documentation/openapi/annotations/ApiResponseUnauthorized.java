package com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents a {@code 401 Unauthorized} response (missing / invalid / expired token).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(
        responseCode = "401",
        description = "Unauthorized — authentication required or token invalid/expired",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(example = """
                {
                  "type": "about:blank",
                  "title": "Unauthorized",
                  "status": 401,
                  "detail": "Authentication token has expired",
                  "instance": "/api/v1/projects",
                  "code": "TOKEN_EXPIRED",
                  "correlationId": "a3f2c1d0-8e4b-4f1a-9d2e-4f1a8b3c5d6e"
                }""")))
public @interface ApiResponseUnauthorized {
}
