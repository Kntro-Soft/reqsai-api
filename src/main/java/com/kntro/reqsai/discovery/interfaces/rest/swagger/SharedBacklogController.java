package com.kntro.reqsai.discovery.interfaces.rest.swagger;

import com.kntro.reqsai.discovery.interfaces.rest.dto.request.LeaveStoryFeedbackRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SharedBacklogResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.StoryFeedbackResponse;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

/**
 * Public API contract for the client side of a share link, implemented by
 * {@code controllers.SharedBacklogControllerImpl}. No account and no JWT: the token in the path is the
 * credential and names the organization and project. Unknown, revoked and expired links answer the same
 * {@code 404 SHARE_LINK_UNAVAILABLE}.
 */
@RequestMapping(path = ApiVersioning.BASE + "/share/{token}", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Share with client", description = "Links that let a client review stories without an account")
public interface SharedBacklogController {

    @Operation(summary = "Open a share link",
            description = "The project's name and its stories under review (rejected and merged stories are not "
                    + "shown), each with the feedback clients already left. Public.")
    @ApiResponse(responseCode = "200", description = "The shared backlog")
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<SharedBacklogResponse> open(
            @Parameter(description = "Token of the share link", required = true) @PathVariable String token);

    @Operation(summary = "Approve or comment on a shared story",
            description = "The client signs with a name. A COMMENT needs the comment; an APPROVAL may carry a note. "
                    + "The feedback reaches the team but does not change the story's status. Public.")
    @ApiResponse(responseCode = "201", description = "The stored feedback")
    @ApiResponseBadRequest
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @PostMapping(path = "/stories/{storyId}/feedback", consumes = MediaType.APPLICATION_JSON_VALUE,
            version = ApiVersioning.V1)
    ResponseEntity<StoryFeedbackResponse> leaveFeedback(
            @Parameter(description = "Token of the share link", required = true) @PathVariable String token,
            @Parameter(description = "Story the feedback is about", required = true) @PathVariable UUID storyId,
            @Valid @RequestBody LeaveStoryFeedbackRequest request);
}
