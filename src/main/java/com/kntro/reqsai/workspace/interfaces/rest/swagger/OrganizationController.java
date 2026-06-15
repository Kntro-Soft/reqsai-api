package com.kntro.reqsai.workspace.interfaces.rest.swagger;

import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateOrganizationRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.OrganizationResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * API contract (OpenAPI documentation) for organization endpoints. The implementation lives in
 * {@code controllers.OrganizationControllerImpl}; keeping the annotations here leaves the controller
 * free of documentation noise.
 */
@RequestMapping(path = ApiVersioning.BASE + "/organizations", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Organizations", description = "Organization registry and tenant schema provisioning")
public interface OrganizationController {

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
}
