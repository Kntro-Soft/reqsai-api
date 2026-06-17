package com.kntro.reqsai.discovery.interfaces.rest.swagger;

import com.kntro.reqsai.discovery.interfaces.rest.dto.request.AddAcceptanceCriterionRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.AcceptanceCriterionResponse;
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
}
