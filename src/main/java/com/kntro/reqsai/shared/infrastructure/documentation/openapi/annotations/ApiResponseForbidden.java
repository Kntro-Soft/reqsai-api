package com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents a {@code 403 Forbidden} response (authenticated but lacking permission / wrong tenant).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(
        responseCode = "403",
        description = "Forbidden — insufficient permissions",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(example = """
                {
                  "type": "about:blank",
                  "title": "Forbidden",
                  "status": 403,
                  "detail": "You do not have permission to access this resource",
                  "instance": "/api/v1/workspaces/0196e4a1-b380-7c3a-9d2e-4f1a8b3c5d6e",
                  "code": "PERMISSION_DENIED",
                  "correlationId": "a3f2c1d0-8e4b-4f1a-9d2e-4f1a8b3c5d6e"
                }""")))
public @interface ApiResponseForbidden {
}
