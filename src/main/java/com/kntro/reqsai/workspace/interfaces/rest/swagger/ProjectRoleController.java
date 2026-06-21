package com.kntro.reqsai.workspace.interfaces.rest.swagger;

import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateProjectRoleRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateProjectRoleRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectRoleResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequestMapping(path = ApiVersioning.BASE + "/organizations/{orgId}/projects/{projectId}/roles", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
public interface ProjectRoleController {
    @ApiResponseBadRequest @ApiStandardErrorResponses
    @PostMapping(version = ApiVersioning.V1)
    ResponseEntity<ProjectRoleResponse> createRole(@PathVariable UUID orgId, @PathVariable UUID projectId, @Valid @RequestBody CreateProjectRoleRequest request, Authentication authentication);

    @ApiStandardErrorResponses
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<List<ProjectRoleResponse>> listRoles(@PathVariable UUID orgId, @PathVariable UUID projectId, Authentication authentication);

    @ApiResponseNotFound @ApiStandardErrorResponses
    @GetMapping(value = "/{roleId}", version = ApiVersioning.V1)
    ResponseEntity<ProjectRoleResponse> getRole(@PathVariable UUID orgId, @PathVariable UUID projectId, @PathVariable UUID roleId, Authentication authentication);

    @ApiResponseBadRequest @ApiResponseNotFound @ApiStandardErrorResponses
    @PutMapping(value = "/{roleId}", version = ApiVersioning.V1)
    ResponseEntity<ProjectRoleResponse> updateRole(@PathVariable UUID orgId, @PathVariable UUID projectId, @PathVariable UUID roleId, @Valid @RequestBody UpdateProjectRoleRequest request, Authentication authentication);

    @ApiResponseNotFound @ApiStandardErrorResponses
    @DeleteMapping(value = "/{roleId}", version = ApiVersioning.V1)
    ResponseEntity<Void> deleteRole(@PathVariable UUID orgId, @PathVariable UUID projectId, @PathVariable UUID roleId, Authentication authentication);
}
