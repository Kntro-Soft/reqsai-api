package com.kntro.reqsai.workspace.interfaces.rest.swagger;

import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.AddGlossaryTermRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.GlossaryTermResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.UUID;

@RequestMapping(path = ApiVersioning.BASE + "/organizations/{orgId}/projects/{projectId}/glossary", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Glossary", description = "Project glossary maintenance operations")
public interface GlossaryController {

    @Operation(summary = "List glossary terms", description = "Returns the manual glossary terms currently stored for the project's glossary.")
    @ApiResponse(responseCode = "200", description = "Glossary terms retrieved successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = GlossaryTermResponse.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<List<GlossaryTermResponse>> listTerms(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            Authentication authentication
    );

    @Operation(summary = "Add a glossary term manually", description = "Creates a glossary term with a business term and its definition under the project's glossary.")
    @ApiResponse(responseCode = "201", description = "Glossary term created successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = GlossaryTermResponse.class)))
    @ApiResponseBadRequest
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(version = ApiVersioning.V1)
    ResponseEntity<GlossaryTermResponse> addTerm(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            @Valid @RequestBody AddGlossaryTermRequest request,
            Authentication authentication
    );

    @Operation(summary = "Get a glossary term", description = "Returns one glossary term from the project's glossary.")
    @ApiResponse(responseCode = "200", description = "Glossary term retrieved successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = GlossaryTermResponse.class)))
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(value = "/{termId}", version = ApiVersioning.V1)
    ResponseEntity<GlossaryTermResponse> getTerm(
            @Parameter(description = "Organization context UUID") @PathVariable UUID orgId,
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            @Parameter(description = "Glossary term UUID") @PathVariable UUID termId,
            Authentication authentication
    );
}
