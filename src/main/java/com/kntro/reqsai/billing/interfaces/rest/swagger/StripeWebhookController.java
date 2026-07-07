package com.kntro.reqsai.billing.interfaces.rest.swagger;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * API contract for the Stripe webhook endpoint.
 * <p>
 * Authenticated by the {@code Stripe-Signature} header (HMAC), not JWT — the caller is Stripe. The raw
 * request body is required verbatim for signature verification, so it is bound as {@code String}.
 */
@RequestMapping(path = "/api/billing/webhooks", consumes = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Billing Webhooks", description = "Payment-provider webhook callbacks (signature-verified)")
public interface StripeWebhookController {

    @Operation(
            summary = "Stripe webhook receiver",
            description = """
                    Receives Stripe events (checkout completion, subscription deletion, payment failure).
                    The request is verified against the endpoint signing secret and de-duplicated by
                    event id before the plan change is applied. Not for interactive use."""
    )
    @ApiResponse(responseCode = "200", description = "Event accepted (or ignored)")
    @ApiResponse(responseCode = "400", description = "Invalid or missing signature")
    @PostMapping(path = "/stripe")
    ResponseEntity<Void> handleStripe(
            @RequestBody String payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String signature
    );
}
