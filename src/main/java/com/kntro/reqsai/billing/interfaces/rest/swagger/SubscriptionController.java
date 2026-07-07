package com.kntro.reqsai.billing.interfaces.rest.swagger;

import com.kntro.reqsai.billing.interfaces.rest.dto.response.SubscriptionResponse;
import com.kntro.reqsai.shared.infrastructure.configuration.ApiVersioning;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.OpenApiConfiguration;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiResponseNotFound;
import com.kntro.reqsai.shared.infrastructure.documentation.openapi.annotations.ApiStandardErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

/**
 * API contract (OpenAPI documentation) for subscription endpoints.
 */
@RequestMapping(path = ApiVersioning.BASE + "/subscriptions", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Subscriptions", description = "Subscription lifecycle and plan limits management")
public interface SubscriptionController {

    @Operation(
            summary = "Get organization subscription",
            description = """
                    Returns the subscription status and plan details for a specific organization.
                    Only the organization owner may access this resource."""
    )
    @ApiResponse(
            responseCode = "200",
            description = "Subscription found",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SubscriptionResponse.class)
            )
    )
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(path = "/organization/{organizationId}", version = ApiVersioning.V1)
    ResponseEntity<SubscriptionResponse> getByOrganization(
            @PathVariable UUID organizationId,
            Authentication authentication
    );
}
