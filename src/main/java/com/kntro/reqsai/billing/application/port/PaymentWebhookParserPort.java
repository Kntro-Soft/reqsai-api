package com.kntro.reqsai.billing.application.port;

/**
 * Output port that verifies a payment-provider webhook's signature and parses its payload into a
 * gateway-agnostic {@link PaymentWebhookEvent}. Implemented per provider in the infrastructure layer.
 */
public interface PaymentWebhookParserPort {

    /**
     * Verifies the request signature and parses the raw payload.
     *
     * @param payload         the exact raw request body (bytes as received, as a string)
     * @param signatureHeader the provider signature header value
     * @return the normalized, verified event
     * @throws com.kntro.reqsai.shared.domain.exception.DomainException with
     *         {@code WEBHOOK_SIGNATURE_INVALID} when the signature does not verify
     */
    PaymentWebhookEvent verifyAndParse(String payload, String signatureHeader);
}
