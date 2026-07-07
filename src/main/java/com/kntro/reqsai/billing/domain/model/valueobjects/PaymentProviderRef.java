package com.kntro.reqsai.billing.domain.model.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * References an external subscription or customer record in a payment provider.
 * Value object embedded inside the Subscription aggregate.
 */
@Embeddable
public record PaymentProviderRef(
        @Enumerated(EnumType.STRING)
        @Column(name = "payment_provider")
        PaymentProvider provider,

        @Column(name = "payment_external_id")
        String externalId
) {
    public static PaymentProviderRef of(PaymentProvider provider, String externalId) {
        return new PaymentProviderRef(provider, externalId);
    }
}
