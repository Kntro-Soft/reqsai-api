package com.kntro.reqsai.workspace.interfaces.rest.swagger;

import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.AddProjectConstraintRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectConstraintResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@RequestMapping(
        path = ApiVersioning.BASE + "/organizations/{orgId}/projects/{projectId}/constraints",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Project Constraints", description = "Non-functional and business constraints that apply to the whole project")
public interface ProjectConstraintController {

    @Operation(summary = "Add a constraint",
            description = "Adds a non-functional or business constraint to the project (e.g. compliance rules, performance targets).")
    @ApiResponse(responseCode = "201", description = "Constraint added — Location header points to the parent project",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ProjectConstraintResponse.class)))
    @ApiResponseBadRequest
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(version = ApiVersioning.V1)
    ResponseEntity<ProjectConstraintResponse> add(
            @Parameter(description = "Organization context UUID", required = true) @PathVariable UUID orgId,
            @Parameter(description = "Project to add the constraint to", required = true) @PathVariable UUID projectId,
            @Valid @RequestBody AddProjectConstraintRequest request,
            Authentication authentication);
}
