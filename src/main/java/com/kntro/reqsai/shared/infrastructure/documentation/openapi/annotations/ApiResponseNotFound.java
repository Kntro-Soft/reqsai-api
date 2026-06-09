package com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents a {@code 404 Not Found} response (entity does not exist).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(
        responseCode = "404",
        description = "Not Found — the requested entity does not exist",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(example = """
                {
                  "type": "about:blank",
                  "title": "Not Found",
                  "status": 404,
                  "detail": "User '0196e4a1-b380-7c3a-9d2e-4f1a8b3c5d6e' not found",
                  "instance": "/api/v1/users/0196e4a1-b380-7c3a-9d2e-4f1a8b3c5d6e",
                  "code": "USER_NOT_FOUND",
                  "correlationId": "a3f2c1d0-8e4b-4f1a-9d2e-4f1a8b3c5d6e"
                }""")))
public @interface ApiResponseNotFound {
}
