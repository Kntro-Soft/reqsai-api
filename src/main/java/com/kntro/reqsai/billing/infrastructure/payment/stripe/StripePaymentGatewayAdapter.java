package com.kntro.reqsai.billing.infrastructure.payment.stripe;

import com.kntro.reqsai.billing.application.config.BillingProperties;
import com.kntro.reqsai.billing.application.port.PaymentGatewayPort;
import com.kntro.reqsai.billing.application.port.PlanChangeRequest;
import com.kntro.reqsai.billing.application.port.PlanChangeResult;
import com.kntro.reqsai.billing.domain.exception.BillingExceptions;
import com.kntro.reqsai.billing.domain.model.valueobjects.PaymentProviderRef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Real Stripe {@link PaymentGatewayPort}, active when {@code reqsai.billing.payment-provider=stripe}.
 * <p>
 * Uses Stripe's REST API directly (via Spring {@link RestClient}) rather than the Stripe SDK to keep
 * the dependency surface minimal. An upgrade creates a hosted Checkout Session (subscription mode) and
 * returns its URL; the plan is only activated once Stripe delivers the {@code checkout.session.completed}
 * webhook — so this adapter never mutates the aggregate synchronously, satisfying the
 * {@link PlanChangeResult} contract shared with the fake gateway.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "reqsai.billing.payment-provider", havingValue = "stripe")
public class StripePaymentGatewayAdapter implements PaymentGatewayPort {

    private static final String STRIPE_BASE_URL = "https://api.stripe.com";

    private final BillingProperties billingProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public StripePaymentGatewayAdapter(BillingProperties billingProperties, ObjectMapper objectMapper) {
        this.billingProperties = billingProperties;
        this.objectMapper = objectMapper;
        String apiKey = billingProperties.stripe() != null ? billingProperties.stripe().apiKey() : null;
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("reqsai.billing.stripe.api-key is required when payment-provider=stripe");
        }
        this.restClient = RestClient.builder()
                .baseUrl(STRIPE_BASE_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public String providerName() {
        return "STRIPE";
    }

    @Override
    public PlanChangeResult startPlanChange(PlanChangeRequest request) {
        if (request.stripePriceId() == null || request.stripePriceId().isBlank()) {
            throw BillingExceptions.paymentGatewayError(
                    "No Stripe price id configured for plan " + request.targetPlan());
        }
        BillingProperties.Stripe stripe = billingProperties.stripe();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "subscription");
        form.add("line_items[0][price]", request.stripePriceId());
        form.add("line_items[0][quantity]", "1");
        form.add("success_url", stripe.successUrl());
        form.add("cancel_url", stripe.cancelUrl());
        form.add("client_reference_id", request.organizationId().toString());
        form.add("metadata[organizationId]", request.organizationId().toString());
        form.add("metadata[targetPlan]", request.targetPlan().name());
        form.add("subscription_data[metadata][organizationId]", request.organizationId().toString());
        form.add("subscription_data[metadata][targetPlan]", request.targetPlan().name());

        String response;
        try {
            response = restClient.post()
                    .uri("/v1/checkout/sessions")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException e) {
            throw BillingExceptions.paymentGatewayError("Stripe checkout session creation failed: " + e.getMessage(), e);
        }

        String url = objectMapper.readTree(response).path("url").asString();
        if (url == null || url.isBlank()) {
            throw BillingExceptions.paymentGatewayError("Stripe checkout session returned no URL");
        }
        log.info("Created Stripe checkout session for org {} (plan {})",
                request.organizationId(), request.targetPlan());
        return PlanChangeResult.pendingCheckout(url);
    }

    @Override
    public void cancelExternalSubscription(PaymentProviderRef providerRef) {
        if (providerRef == null || providerRef.externalId() == null || providerRef.externalId().isBlank()) {
            return;
        }
        try {
            restClient.delete()
                    .uri("/v1/subscriptions/{id}", providerRef.externalId())
                    .retrieve()
                    .toBodilessEntity();
            log.info("Cancelled Stripe subscription {}", providerRef.externalId());
        } catch (RuntimeException e) {
            // Best-effort: the local aggregate is already cancelled; log and move on.
            log.warn("Failed to cancel Stripe subscription {}: {}", providerRef.externalId(), e.getMessage());
        }
    }
}
