package com.kntro.reqsai.discovery.interfaces.rest.swagger;

import com.kntro.reqsai.discovery.interfaces.rest.dto.request.CreateDiscoverySessionRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.DiscoverySessionResponse;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

/**
 * API contract for discovery-session endpoints; implemented by {@code controllers.DiscoverySessionControllerImpl}.
 * All routes are tenant-scoped — the active schema is resolved from the JWT {@code orgId}.
 */
@RequestMapping(path = ApiVersioning.BASE + "/projects/{projectId}/sessions",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Discovery Sessions", description = "Requirements-elicitation sessions")
public interface DiscoverySessionController {

    @Operation(
            summary = "Create a discovery session",
            description = "Creates a new requirements-elicitation session in DRAFT under the given project, "
                    + "in the authenticated user's tenant.")
    @ApiResponse(responseCode = "201", description = "Session created")
    @ApiResponseBadRequest
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(version = ApiVersioning.V1)
    ResponseEntity<DiscoverySessionResponse> create(@PathVariable UUID projectId, @Valid @RequestBody CreateDiscoverySessionRequest request);
}
