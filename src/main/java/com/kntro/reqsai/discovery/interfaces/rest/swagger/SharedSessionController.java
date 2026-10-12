package com.kntro.reqsai.discovery.interfaces.rest.swagger;

import com.kntro.reqsai.discovery.interfaces.rest.dto.request.AcceptSuggestionRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.ChangeUserStoryStatusRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SharedSessionResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.SuggestionResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.UserStoryResponse;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

/**
 * Public API contract for a session share link, implemented by {@code controllers.SharedSessionControllerImpl}.
 * No account and no JWT: the token in the path is the credential and names the organization, project and
 * session. Unknown, revoked, expired and backlog links all answer the same {@code 404 SHARE_LINK_UNAVAILABLE};
 * a spectator link that tries to decide gets {@code 403 SHARE_LINK_READ_ONLY}.
 */
@RequestMapping(path = ApiVersioning.BASE + "/share/session/{token}", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Share a session", description = "Links that let a client follow a discovery session without an account")
public interface SharedSessionController {

    @Operation(summary = "Open a session link",
            description = "The session as it stands: state, the latest transcript, who spoke, the suggestions "
                    + "waiting for a decision and the stories it produced. Meant to be read again every few "
                    + "seconds while the session is live. Public.")
    @ApiResponse(responseCode = "200", description = "The shared session")
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<SharedSessionResponse> open(
            @Parameter(description = "Token of the session link", required = true) @PathVariable String token);

    @Operation(summary = "Accept a suggestion through a session link",
            description = "Needs a REVIEW link. Works like the analyst's accept: the draft can be edited, and "
                    + "approve=true also approves the resulting story. Public.")
    @ApiResponse(responseCode = "200", description = "The accepted suggestion")
    @ApiResponseBadRequest
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @PostMapping(path = "/suggestions/{suggestionId}/accept", version = ApiVersioning.V1)
    ResponseEntity<SuggestionResponse> accept(
            @Parameter(description = "Token of the session link", required = true) @PathVariable String token,
            @Parameter(description = "Suggestion", required = true) @PathVariable UUID suggestionId,
            @Valid @RequestBody(required = false) @Nullable AcceptSuggestionRequest request);

    @Operation(summary = "Dismiss a suggestion through a session link",
            description = "Needs a REVIEW link. Dismisses a pending suggestion without touching the backlog. Public.")
    @ApiResponse(responseCode = "200", description = "The dismissed suggestion")
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @PostMapping(path = "/suggestions/{suggestionId}/dismiss", version = ApiVersioning.V1)
    ResponseEntity<SuggestionResponse> dismiss(
            @Parameter(description = "Token of the session link", required = true) @PathVariable String token,
            @Parameter(description = "Suggestion", required = true) @PathVariable UUID suggestionId);

    @Operation(summary = "Approve or reject a story through a session link",
            description = "Needs a REVIEW link. Records the review decision (APPROVED, REJECTED or back to DRAFT) on "
                    + "a story the session produced. Public.")
    @ApiResponse(responseCode = "200", description = "The story with its new status")
    @ApiResponseBadRequest
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @PostMapping(path = "/stories/{storyId}/status", consumes = MediaType.APPLICATION_JSON_VALUE,
            version = ApiVersioning.V1)
    ResponseEntity<UserStoryResponse> changeStoryStatus(
            @Parameter(description = "Token of the session link", required = true) @PathVariable String token,
            @Parameter(description = "Story of the session", required = true) @PathVariable UUID storyId,
            @Valid @RequestBody ChangeUserStoryStatusRequest request);
}
