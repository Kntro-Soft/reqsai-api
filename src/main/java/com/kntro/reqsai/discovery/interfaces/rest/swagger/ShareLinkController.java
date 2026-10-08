package com.kntro.reqsai.discovery.interfaces.rest.swagger;

import com.kntro.reqsai.discovery.interfaces.rest.dto.request.CreateShareLinkRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.ShareLinkResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.StoryFeedbackResponse;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.UUID;

/**
 * API contract for sharing a project's stories with a client, implemented by
 * {@code controllers.ShareLinkControllerImpl}. The team creates and revokes links and reads what clients
 * said; the client side is {@link SharedBacklogController}. Tenant-scoped through the JWT {@code orgId}.
 */
@RequestMapping(path = ApiVersioning.BASE + "/projects/{projectId}", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Share with client", description = "Links that let a client review stories without an account")
public interface ShareLinkController {

    @Operation(
            summary = "Create a share link",
            description = """
                    Creates a link a client opens without an account to read the project's stories, approve them \
                    and leave comments. The response carries the raw token once: the public page is \
                    /share/{token}. Valid 1 to 90 days, 14 by default. Requires STORY_WRITE.""")
    @ApiResponse(responseCode = "201", description = "The link, with its token")
    @ApiResponseBadRequest
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(path = "/share-links", version = ApiVersioning.V1)
    ResponseEntity<ShareLinkResponse> create(
            @Parameter(description = "Project to share", required = true) @PathVariable UUID projectId,
            @Valid @RequestBody(required = false) @Nullable CreateShareLinkRequest request);

    @Operation(summary = "List share links",
            description = "The project's links, newest first, without their tokens. Requires STORY_READ.")
    @ApiResponse(responseCode = "200", description = "Links, newest first")
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(path = "/share-links", version = ApiVersioning.V1)
    ResponseEntity<List<ShareLinkResponse>> list(
            @Parameter(description = "Project of the links", required = true) @PathVariable UUID projectId);

    @Operation(summary = "Revoke a share link",
            description = "The link stops working right away. Revoking twice is harmless. Requires STORY_WRITE.")
    @ApiResponse(responseCode = "200", description = "The revoked link")
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @DeleteMapping(path = "/share-links/{linkId}", version = ApiVersioning.V1)
    ResponseEntity<ShareLinkResponse> revoke(
            @Parameter(description = "Project of the link", required = true) @PathVariable UUID projectId,
            @Parameter(description = "Link to revoke", required = true) @PathVariable UUID linkId);

    @Operation(summary = "List client feedback on a story",
            description = "Approvals and comments clients left on the story through share links, oldest first. "
                    + "They do not change the story's status. Requires STORY_READ.")
    @ApiResponse(responseCode = "200", description = "Feedback, oldest first")
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(path = "/stories/{storyId}/client-feedback", version = ApiVersioning.V1)
    ResponseEntity<List<StoryFeedbackResponse>> feedback(
            @Parameter(description = "Project of the story", required = true) @PathVariable UUID projectId,
            @Parameter(description = "Story", required = true) @PathVariable UUID storyId);
}
