package com.kntro.reqsai.workspace.interfaces.rest.swagger;

import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseBadRequest;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseForbidden;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.AcceptInvitationRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.AcceptInvitationResponse;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.InvitationDetailsResponse;
import io.swagger.v3.oas.annotations.Operation;
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

/**
 * API contract for organization invitations. Implementation lives in
 * {@code controllers.InvitationControllerImpl}.
 * <p>
 * {@code POST /accept} is authenticated: the caller must both possess the token and own the invited
 * email (exact, case-insensitive match) — a mismatch returns 403. {@code GET /{token}} is public (the
 * accept/signup screen loads it before the user has signed in) and returns only non-sensitive fields.
 */
@RequestMapping(path = ApiVersioning.BASE + "/invitations", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Invitations", description = "Organization invitation acceptance")
public interface InvitationController {

    @Operation(
            summary = "Accept an invitation",
            description = "Accepts a pending invitation by token. Requires authentication AND that the caller's "
                    + "account email matches the invited email. Idempotent: replaying on an already-accepted "
                    + "invitation returns 200. 404 if the token is unknown, 410 if expired, 403 on email mismatch.")
    @ApiResponse(responseCode = "200", description = "Invitation accepted")
    @ApiResponseBadRequest
    @ApiResponseForbidden
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(value = "/accept", version = ApiVersioning.V1)
    ResponseEntity<AcceptInvitationResponse> accept(
            @Valid @RequestBody AcceptInvitationRequest request, Authentication authentication);

    @Operation(
            summary = "Get invitation details (public)",
            description = "Public lookup of an invitation by token for the accept/signup screen. Returns the "
                    + "organization name, role, invited email, inviter name, status and whether it expired. "
                    + "404 if the token is unknown. No authentication required.")
    @ApiResponse(responseCode = "200", description = "Invitation details")
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @GetMapping(value = "/{token}", version = ApiVersioning.V1)
    ResponseEntity<InvitationDetailsResponse> getByToken(@PathVariable String token);
}
