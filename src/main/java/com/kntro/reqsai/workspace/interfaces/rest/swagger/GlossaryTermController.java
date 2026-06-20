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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@RequestMapping(
        path = ApiVersioning.BASE + "/organizations/{orgId}/projects/{projectId}/glossary/terms",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Glossary Terms", description = "Domain vocabulary for a project, used as LLM context during discovery")
public interface GlossaryTermController {

    @Operation(summary = "Add a glossary term",
            description = "Adds a domain term with its definition to the project's glossary.")
    @ApiResponse(responseCode = "201", description = "Term added — Location header points to the parent project",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = GlossaryTermResponse.class)))
    @ApiResponseBadRequest
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(version = ApiVersioning.V1)
    ResponseEntity<GlossaryTermResponse> add(
            @Parameter(description = "Organization context UUID", required = true) @PathVariable UUID orgId,
            @Parameter(description = "Project whose glossary receives the term", required = true) @PathVariable UUID projectId,
            @Valid @RequestBody AddGlossaryTermRequest request,
            Authentication authentication);
}
