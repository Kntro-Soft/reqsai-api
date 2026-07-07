package com.kntro.reqsai.billing.interfaces.rest.swagger;

import com.kntro.reqsai.billing.interfaces.rest.dto.request.UpgradeSubscriptionRequest;
import com.kntro.reqsai.billing.interfaces.rest.dto.response.PlanChangeResponse;
import com.kntro.reqsai.billing.interfaces.rest.dto.response.SubscriptionResponse;
import com.kntro.reqsai.billing.interfaces.rest.dto.response.SubscriptionUsageResponse;
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
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @Operation(
            summary = "Get subscription usage",
            description = """
                    Returns the current billing period, token consumption against the plan allowance,
                    and display pricing for an organization. Only the organization owner may access it."""
    )
    @ApiResponse(
            responseCode = "200",
            description = "Usage returned",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SubscriptionUsageResponse.class)
            )
    )
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @GetMapping(path = "/organization/{organizationId}/usage", version = ApiVersioning.V1)
    ResponseEntity<SubscriptionUsageResponse> getUsage(
            @PathVariable UUID organizationId,
            Authentication authentication
    );

    @Operation(
            summary = "Upgrade subscription to a paid plan",
            description = """
                    Starts a plan change to a paid tier. With the fake gateway the plan is activated
                    immediately (status ACTIVATED). With a real gateway the response carries a hosted
                    checkout URL (status CHECKOUT_REQUIRED) and the plan is activated once the provider
                    webhook confirms payment. Only the organization owner may perform this."""
    )
    @ApiResponse(
            responseCode = "200",
            description = "Plan change started",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = PlanChangeResponse.class)
            )
    )
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PutMapping(path = "/organization/{organizationId}/upgrade", version = ApiVersioning.V1)
    ResponseEntity<PlanChangeResponse> upgrade(
            @PathVariable UUID organizationId,
            @Valid @RequestBody UpgradeSubscriptionRequest request,
            Authentication authentication
    );

    @Operation(
            summary = "Cancel subscription",
            description = """
                    Cancels a paid subscription. The plan stays usable until the end of the current
                    period. Only the organization owner may perform this."""
    )
    @ApiResponse(
            responseCode = "200",
            description = "Subscription cancelled",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SubscriptionResponse.class)
            )
    )
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PutMapping(path = "/organization/{organizationId}/cancel", version = ApiVersioning.V1)
    ResponseEntity<SubscriptionResponse> cancel(
            @PathVariable UUID organizationId,
            Authentication authentication
    );

    @Operation(
            summary = "Reactivate subscription",
            description = """
                    Reactivates a previously cancelled paid subscription. Only the organization owner
                    may perform this."""
    )
    @ApiResponse(
            responseCode = "200",
            description = "Subscription reactivated",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SubscriptionResponse.class)
            )
    )
    @ApiResponseNotFound
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PutMapping(path = "/organization/{organizationId}/reactivate", version = ApiVersioning.V1)
    ResponseEntity<SubscriptionResponse> reactivate(
            @PathVariable UUID organizationId,
            Authentication authentication
    );
}
