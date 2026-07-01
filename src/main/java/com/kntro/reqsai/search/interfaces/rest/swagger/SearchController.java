package com.kntro.reqsai.search.interfaces.rest.swagger;

import com.kntro.reqsai.search.interfaces.rest.dto.response.SearchHitResponse;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RequestMapping(path = ApiVersioning.BASE + "/search", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Search", description = "Global search powering the command palette")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
public interface SearchController {

    @Operation(summary = "Global search",
            description = "Trigram/prefix search across projects, user stories, organizations and members, "
                    + "scoped to the authenticated tenant and the caller's authorizations. Returns the top "
                    + "matches merged across types. A blank query returns an empty list.")
    @ApiResponse(responseCode = "200", description = "Merged top matches",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    @ApiStandardErrorResponses
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<List<SearchHitResponse>> search(
            @Parameter(description = "Search term; blank returns an empty list", example = "checkout")
            @RequestParam(name = "q", required = false) String q,
            @Parameter(description = "Max results (1-20, default 8)", example = "8")
            @RequestParam(name = "limit", required = false, defaultValue = "8") int limit,
            Authentication authentication);
}
