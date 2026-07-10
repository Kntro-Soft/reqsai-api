package com.kntro.reqsai.billing.infrastructure.payment.stripe;

import com.kntro.reqsai.billing.application.config.BillingProperties;
import com.kntro.reqsai.billing.application.port.PaymentWebhookEvent;
import com.kntro.reqsai.billing.application.port.PaymentWebhookParserPort;
import com.kntro.reqsai.billing.domain.exception.BillingExceptions;
import com.kntro.reqsai.billing.domain.model.valueobjects.PlanType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Stripe {@link PaymentWebhookParserPort}: verifies the {@code Stripe-Signature} header (HMAC-SHA256
 * over {@code "<timestamp>.<payload>"} keyed by the endpoint secret, per Stripe's spec) and maps the
 * event JSON to a gateway-agnostic {@link PaymentWebhookEvent}. Implemented without the Stripe SDK to
 * keep the dependency surface minimal; the verification follows Stripe's documented scheme.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class StripeWebhookParser implements PaymentWebhookParserPort {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final BillingProperties billingProperties;
    private final ObjectMapper objectMapper;

    @Override
    public PaymentWebhookEvent verifyAndParse(String payload, String signatureHeader) {
        verifySignature(payload, signatureHeader);
        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventId = text(root, "id");
            String type = text(root, "type");
            JsonNode object = root.path("data").path("object");
            return switch (type == null ? "" : type) {
                case "checkout.session.completed" -> planActivated(eventId, object);
                case "customer.subscription.deleted" -> new PaymentWebhookEvent(
                        eventId, PaymentWebhookEvent.Kind.SUBSCRIPTION_DELETED, null, null, text(object, "id"));
                case "invoice.payment_failed" -> new PaymentWebhookEvent(
                        eventId, PaymentWebhookEvent.Kind.PAYMENT_FAILED, null, null, text(object, "subscription"));
                default -> new PaymentWebhookEvent(eventId, PaymentWebhookEvent.Kind.IGNORED, null, null, null);
            };
        } catch (RuntimeException e) {
            throw BillingExceptions.paymentGatewayError("Failed to parse Stripe webhook payload: " + e.getMessage(), e);
        }
    }

    private PaymentWebhookEvent planActivated(String eventId, JsonNode object) {
        JsonNode metadata = object.path("metadata");
        UUID organizationId = parseUuid(text(metadata, "organizationId"));
        if (organizationId == null) {
            organizationId = parseUuid(text(object, "client_reference_id"));
        }
        PlanType targetPlan = parsePlan(text(metadata, "targetPlan"));
        String externalSubscriptionId = text(object, "subscription");
        return new PaymentWebhookEvent(
                eventId, PaymentWebhookEvent.Kind.PLAN_ACTIVATED, organizationId, targetPlan, externalSubscriptionId);
    }

    private void verifySignature(String payload, @Nullable String signatureHeader) {
        String secret = billingProperties.stripe() != null ? billingProperties.stripe().webhookSecret() : null;
        if (secret == null || secret.isBlank()) {
            throw BillingExceptions.webhookSignatureInvalid("Stripe webhook secret is not configured");
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw BillingExceptions.webhookSignatureInvalid("Missing Stripe-Signature header");
        }

        String timestamp = null;
        String provided = null;
        for (String part : signatureHeader.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if ("t".equals(kv[0].trim())) {
                timestamp = kv[1].trim();
            } else if ("v1".equals(kv[0].trim())) {
                provided = kv[1].trim();
            }
        }
        if (timestamp == null || provided == null) {
            throw BillingExceptions.webhookSignatureInvalid("Malformed Stripe-Signature header");
        }

        String expected = hmacSha256Hex(secret, timestamp + "." + payload);
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, providedBytes)) {
            throw BillingExceptions.webhookSignatureInvalid("Stripe signature verification failed");
        }
    }

    private static String hmacSha256Hex(String secret, String signedPayload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw BillingExceptions.paymentGatewayError("Unable to compute webhook signature", e);
        }
    }

    private static @Nullable String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asString() : null;
    }

    private static @Nullable UUID parseUuid(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static @Nullable PlanType parsePlan(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PlanType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
