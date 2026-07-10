package com.kntro.reqsai.workspace.interfaces.rest.swagger;

import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.MyProjectPermissionsResponse;
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

import java.util.UUID;

/**
 * API contract for the caller's effective project permissions. The route carries no {@code orgId};
 * the tenant is taken from the JWT. Implementation lives in
 * {@code controllers.ProjectPermissionsControllerImpl}.
 */
@RequestMapping(path = ApiVersioning.BASE + "/projects", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Project Permissions", description = "The caller's effective permissions on a project")
public interface ProjectPermissionsController {

    @Operation(
            summary = "Get my effective project permissions",
            description = """
                    Returns the caller's effective permissions on the project — the union of the \
                    organization's member base-permission floor and the caller's project role. \
                    Owners/admins receive the full permission catalog.

                    - Any active organization member may read their own effective permissions \
                    (the set is empty for a member with neither a base floor nor a project role).""")
    @ApiResponse(
            responseCode = "200",
            description = "The caller's effective permissions",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = MyProjectPermissionsResponse.class),
                    examples = @ExampleObject(value = "{ \"permissions\": [\"STORY_READ\", \"DOCUMENT_READ\"] }")))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(value = "/{projectId}/me/permissions", version = ApiVersioning.V1)
    ResponseEntity<MyProjectPermissionsResponse> getMyPermissions(
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            Authentication authentication);
}
