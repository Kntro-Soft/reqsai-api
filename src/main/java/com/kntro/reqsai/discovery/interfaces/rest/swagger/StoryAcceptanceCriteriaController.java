package com.kntro.reqsai.discovery.interfaces.rest.swagger;

import com.kntro.reqsai.discovery.interfaces.rest.dto.request.AddAcceptanceCriterionRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.UpdateAcceptanceCriterionRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.AcceptanceCriterionResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@RequestMapping(
        path = ApiVersioning.BASE + "/projects/{projectId}/stories/{storyId}/criteria",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Acceptance Criteria", description = "Manage acceptance criteria for a user story")
public interface StoryAcceptanceCriteriaController {

    @Operation(
            summary = "Add an acceptance criterion",
            description = "Adds a new Given / When / Then acceptance criterion to the given user story.")
    @ApiResponse(
            responseCode = "201",
            description = "Criterion added — `Location` header points to the parent story",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = AcceptanceCriterionResponse.class)))
    @ApiResponseBadRequest
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(version = ApiVersioning.V1)
    ResponseEntity<AcceptanceCriterionResponse> add(
            @Parameter(description = "Project the story belongs to", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "User story to add the criterion to", required = true)
            @PathVariable UUID storyId,
            @Valid @RequestBody AddAcceptanceCriterionRequest request);

    @Operation(
            summary = "Update an acceptance criterion",
            description = "Replaces all fields (given, when, then, scenario) of an existing criterion.")
    @ApiResponse(
            responseCode = "200",
            description = "Criterion updated",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = AcceptanceCriterionResponse.class)))
    @ApiResponseBadRequest
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PutMapping(path = "/{criterionId}", version = ApiVersioning.V1)
    ResponseEntity<AcceptanceCriterionResponse> update(
            @Parameter(description = "Project the story belongs to", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "User story that owns the criterion", required = true)
            @PathVariable UUID storyId,
            @Parameter(description = "Criterion to update", required = true)
            @PathVariable UUID criterionId,
            @Valid @RequestBody UpdateAcceptanceCriterionRequest request);

    @Operation(
            summary = "Delete an acceptance criterion",
            description = "Permanently removes an acceptance criterion from the user story.")
    @ApiResponse(responseCode = "204", description = "Criterion deleted")
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @DeleteMapping(path = "/{criterionId}", version = ApiVersioning.V1)
    ResponseEntity<Void> delete(
            @Parameter(description = "Project the story belongs to", required = true)
            @PathVariable UUID projectId,
            @Parameter(description = "User story that owns the criterion", required = true)
            @PathVariable UUID storyId,
            @Parameter(description = "Criterion to delete", required = true)
            @PathVariable UUID criterionId);
}
