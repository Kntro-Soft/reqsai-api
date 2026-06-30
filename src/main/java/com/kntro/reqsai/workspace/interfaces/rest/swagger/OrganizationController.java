package com.kntro.reqsai.workspace.interfaces.rest.swagger;

import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateOrganizationRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateOrganizationRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.OrganizationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.UUID;

/**
 * API contract (OpenAPI documentation) for organization endpoints. The implementation lives in
 * {@code controllers.OrganizationControllerImpl}; keeping the annotations here leaves the controller
 * free of documentation noise.
 */
@RequestMapping(path = ApiVersioning.BASE + "/organizations", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Organizations", description = "Organization registry and tenant schema provisioning")
public interface OrganizationController {

    @Operation(
            summary = "List my organizations",
            description = """
                    Returns the organizations the authenticated user owns, newest first.

                    Organizations where the user is solely a member are not yet included: member rows \
                    live in per-tenant schemas without a global index.""")
    @ApiResponse(
            responseCode = "200",
            description = "Organizations the user can access",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = OrganizationResponse.class))))
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<List<OrganizationResponse>> list(Authentication authentication);

    @Operation(
            summary = "Get an organization",
            description = """
                    Returns the organization's current editable configuration and metadata.

                    - Readable fields include `name`, `slug`, `status`, `meetingLanguage`, and `audioRetentionDays`
                    - Only the organization owner may read this resource in the current slice.""")
    @ApiResponse(
            responseCode = "200",
            description = "Organization found",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = OrganizationResponse.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(value = "/{orgId}", version = ApiVersioning.V1)
    ResponseEntity<OrganizationResponse> getById(@PathVariable UUID orgId, Authentication authentication);

    @Operation(
            summary = "Create an organization",
            description = """
                    Creates a new organization for the authenticated user (who becomes its owner) and \
                    provisions its isolated tenant schema (`tenant_<slug>`). The schema is immediately \
                    available for tenant-scoped operations.

                    - `slug` is auto-derived from `name` (lowercased, spaces → hyphens) if omitted.
                    - `meetingLanguage` defaults to `es-PE` if omitted.""")
    @ApiResponse(
            responseCode = "201",
            description = "Organization created and tenant schema provisioned — `Location` header points to the new resource",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = OrganizationResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "id": "019756a0-1234-7abc-8def-000000000001",
                              "name": "Acme Corp",
                              "slug": "acme-corp",
                              "status": "ACTIVE",
                              "ownerId": "019756a0-1234-7abc-8def-000000000099",
                              "meetingLanguage": "es-PE",
                              "audioRetentionDays": 30,
                              "createdAt": "2026-06-15T13:55:00Z",
                              "updatedAt": "2026-06-15T13:55:00Z"
                            }""")))
    @ApiResponseBadRequest
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(version = ApiVersioning.V1)
    ResponseEntity<OrganizationResponse> create(@Valid @RequestBody CreateOrganizationRequest request, Authentication authentication);

    @Operation(
            summary = "Update an organization (partial)",
            description = """
                    Partially updates the organization's editable metadata and generation settings.

                    - Editable fields: `name`, `meetingLanguage`, `audioRetentionDays` — all optional
                    - Only fields present (non-null) in the body are applied; omitted fields are left unchanged
                    - An empty body (no fields) is a successful no-op and returns the unchanged organization
                    - Immutable fields: `slug`, `ownerId`, `planLimits`
                    - Only the organization owner may perform this update.""")
    @ApiResponse(
            responseCode = "200",
            description = "Organization updated successfully",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = OrganizationResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "id": "019756a0-1234-7abc-8def-000000000001",
                              "name": "Acme Corp International",
                              "slug": "acme-corp",
                              "status": "ACTIVE",
                              "ownerId": "019756a0-1234-7abc-8def-000000000099",
                              "meetingLanguage": "pt-BR",
                              "audioRetentionDays": -1,
                              "createdAt": "2026-06-15T13:55:00Z",
                              "updatedAt": "2026-06-20T18:40:00Z"
                            }""")))
    @ApiResponseBadRequest
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PatchMapping(value = "/{orgId}", version = ApiVersioning.V1)
    ResponseEntity<OrganizationResponse> update(
            @PathVariable UUID orgId,
            @Valid @RequestBody UpdateOrganizationRequest request,
            Authentication authentication);
}
