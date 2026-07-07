package com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents a {@code 409 Conflict} response (business-rule violation, e.g. duplicate).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(
        responseCode = "409",
        description = "Conflict — the request violates a business rule",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(example = """
                {
                  "type": "about:blank",
                  "title": "Conflict",
                  "status": 409,
                  "detail": "Email 'jhosep@example.com' is already registered",
                  "instance": "/api/v1/auth/register",
                  "code": "EMAIL_ALREADY_EXISTS",
                  "correlationId": "a3f2c1d0-8e4b-4f1a-9d2e-4f1a8b3c5d6e"
                }""")))
public @interface ApiResponseConflict {
}
