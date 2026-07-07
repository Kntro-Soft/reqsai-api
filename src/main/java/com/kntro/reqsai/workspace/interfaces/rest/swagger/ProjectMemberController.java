package com.kntro.reqsai.workspace.interfaces.rest.swagger;

import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateProjectMemberRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.InviteProjectMembersRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateProjectMemberRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.MemberResponse;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectMemberResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequestMapping(path = ApiVersioning.BASE + "/organizations/{orgId}/projects/{projectId}/members", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
public interface ProjectMemberController {
    @ApiResponseBadRequest @ApiStandardErrorResponses
    @PostMapping(version = ApiVersioning.V1)
    ResponseEntity<ProjectMemberResponse> createAssignment(@PathVariable UUID orgId, @PathVariable UUID projectId, @Valid @RequestBody CreateProjectMemberRequest request, Authentication authentication);

    @ApiResponseBadRequest @ApiResponseNotFound @ApiStandardErrorResponses
    @PostMapping(value = "/invite", version = ApiVersioning.V1)
    ResponseEntity<List<MemberResponse>> inviteToProject(@PathVariable UUID orgId, @PathVariable UUID projectId, @Valid @RequestBody InviteProjectMembersRequest request, Authentication authentication);

    @ApiStandardErrorResponses
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<List<ProjectMemberResponse>> listAssignments(@PathVariable UUID orgId, @PathVariable UUID projectId, Authentication authentication);

    @ApiResponseNotFound @ApiStandardErrorResponses
    @GetMapping(value = "/{assignmentId}", version = ApiVersioning.V1)
    ResponseEntity<ProjectMemberResponse> getAssignment(@PathVariable UUID orgId, @PathVariable UUID projectId, @PathVariable UUID assignmentId, Authentication authentication);

    @ApiResponseBadRequest @ApiResponseNotFound @ApiStandardErrorResponses
    @PutMapping(value = "/{assignmentId}", version = ApiVersioning.V1)
    ResponseEntity<ProjectMemberResponse> updateAssignment(@PathVariable UUID orgId, @PathVariable UUID projectId, @PathVariable UUID assignmentId, @Valid @RequestBody UpdateProjectMemberRequest request, Authentication authentication);

    @ApiResponseNotFound @ApiStandardErrorResponses
    @DeleteMapping(value = "/{assignmentId}", version = ApiVersioning.V1)
    ResponseEntity<Void> deleteAssignment(@PathVariable UUID orgId, @PathVariable UUID projectId, @PathVariable UUID assignmentId, Authentication authentication);
}
