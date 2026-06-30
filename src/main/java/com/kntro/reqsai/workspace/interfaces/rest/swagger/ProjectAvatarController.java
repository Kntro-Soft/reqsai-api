package com.kntro.reqsai.workspace.interfaces.rest.swagger;

import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

/**
 * API contract (OpenAPI documentation) for the public project avatar endpoint. The implementation lives
 * in {@code controllers.ProjectAvatarControllerImpl}.
 * <p>
 * Public on purpose: the bytes are loaded by browser {@code <img src>} tags, which cannot carry the
 * Bearer token. The {@code orgId} path variable lets the endpoint resolve the tenant schema without a
 * JWT. The image is a non-sensitive generated gradient and the ids are unguessable UUIDv7. Whitelisted
 * in {@code SecurityConfiguration}.
 */
@RequestMapping(path = ApiVersioning.BASE + "/organizations/{orgId}/projects")
@Tag(name = "Projects", description = "Workspace project foundation operations")
public interface ProjectAvatarController {

    @Operation(
            summary = "Get a project's avatar",
            description = "Returns the project's generated avatar image bytes. Public — no authentication required.")
    @ApiResponse(
            responseCode = "200",
            description = "Avatar image",
            content = @Content(schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "404", description = "Project has no avatar", content = @Content)
    @GetMapping(path = "/{projectId}/avatar", version = ApiVersioning.V1)
    ResponseEntity<byte[]> getAvatar(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            @Parameter(description = "Project UUID") @PathVariable UUID projectId);
}
