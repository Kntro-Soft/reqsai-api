package com.kntro.reqsai.workspace.interfaces.rest.swagger;

import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateBasePermissionRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.BasePermissionResponse;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.OrganizationAuthorizationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

/**
 * API contract for the organization's authorization configuration: the member base-permission floor
 * (owner/admin managed) and the caller's own authorization context. Implementation lives in
 * {@code controllers.OrganizationAuthorizationControllerImpl}.
 */
@RequestMapping(path = ApiVersioning.BASE + "/organizations", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Organization Authorization", description = "Member base permission floor and caller authorization context")
public interface OrganizationAuthorizationController {

    @Operation(
            summary = "Get the member base permission",
            description = """
                    Returns the organization's member base permission floor — the GitHub-style RBAC \
                    baseline applied to every project member on top of their explicit project role.

                    - `NONE` — members get nothing but their explicit project role
                    - `READ` — members get a read-only baseline across the workspace resources
                    - Owner/admin only.""")
    @ApiResponse(
            responseCode = "200",
            description = "The current base permission",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = BasePermissionResponse.class),
                    examples = @ExampleObject(value = "{ \"basePermission\": \"READ\" }")))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(value = "/{orgId}/base-permission", version = ApiVersioning.V1)
    ResponseEntity<BasePermissionResponse> getBasePermission(
            @PathVariable UUID orgId, Authentication authentication);

    @Operation(
            summary = "Set the member base permission",
            description = """
                    Sets the organization's member base permission floor. Applies to every project \
                    member immediately; owners/admins bypass it and keep full access.

                    - Body: `{ "basePermission": "NONE" | "READ" }`
                    - Owner/admin only.""")
    @ApiResponse(
            responseCode = "200",
            description = "The updated base permission",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = BasePermissionResponse.class),
                    examples = @ExampleObject(value = "{ \"basePermission\": \"NONE\" }")))
    @ApiResponseBadRequest
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PutMapping(value = "/{orgId}/base-permission", version = ApiVersioning.V1)
    ResponseEntity<BasePermissionResponse> updateBasePermission(
            @PathVariable UUID orgId,
            @Valid @RequestBody UpdateBasePermissionRequest request,
            Authentication authentication);

    @Operation(
            summary = "Get my organization authorization",
            description = """
                    Returns the caller's authorization context in the organization: their org role \
                    (`OWNER`/`ADMIN`/`MEMBER`) and the organization's member base-permission floor.

                    - Any organization member (owner or active member) may read this.""")
    @ApiResponse(
            responseCode = "200",
            description = "The caller's authorization context",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = OrganizationAuthorizationResponse.class),
                    examples = @ExampleObject(value = "{ \"orgRole\": \"MEMBER\", \"memberBasePermission\": \"READ\" }")))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(value = "/{orgId}/me/authorization", version = ApiVersioning.V1)
    ResponseEntity<OrganizationAuthorizationResponse> getMyAuthorization(
            @PathVariable UUID orgId, Authentication authentication);
}
