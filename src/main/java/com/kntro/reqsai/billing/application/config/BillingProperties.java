package com.kntro.reqsai.billing.application.config;

import com.kntro.reqsai.billing.domain.model.valueobjects.PlanType;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Billing configuration bound from {@code reqsai.billing.*}.
 * <p>
 * Holds the active payment-gateway selector (the {@code fake} adapter for local/CI and the
 * {@code stripe} adapter for real charges — swapped by the {@code payment-provider} flag), the
 * display currency, per-plan pricing (display amount in the smallest currency unit plus the external
 * price id the gateway charges against) and the Stripe credentials/return URLs.
 * <p>
 * The provider of payment is the source of truth for the <em>chargeable</em> amount; the amounts here
 * are for display and are stored in integer minor units (e.g. cents), never floating point.
 *
 * @param paymentProvider active gateway: {@code fake} (default) or {@code stripe}
 * @param currency        ISO-4217 display currency (default {@code USD})
 * @param plans           per-plan pricing keyed by lower-case plan name ({@code pro}, {@code enterprise})
 * @param stripe          Stripe credentials and checkout return URLs (required only in {@code stripe} mode)
 */
@ConfigurationProperties(prefix = "reqsai.billing")
public record BillingProperties(
        String paymentProvider,
        String currency,
        @Nullable Map<String, PlanPricing> plans,
        @Nullable Stripe stripe
) {

    public static final String PROVIDER_FAKE = "fake";
    public static final String PROVIDER_STRIPE = "stripe";

    public BillingProperties {
        if (paymentProvider == null || paymentProvider.isBlank()) {
            paymentProvider = PROVIDER_FAKE;
        }
        if (currency == null || currency.isBlank()) {
            currency = "USD";
        }
    }

    /** Display pricing for a single plan tier. */
    public record PlanPricing(long amountCents, @Nullable String stripePriceId) {}

    /** Stripe gateway credentials and checkout return URLs. */
    public record Stripe(
            @Nullable String apiKey,
            @Nullable String webhookSecret,
            @Nullable String successUrl,
            @Nullable String cancelUrl
    ) {}

    /**
     * Resolves the configured pricing for a plan tier, falling back to a zero-amount entry with no
     * external price id when the plan is not configured (e.g. the fake gateway that never charges).
     *
     * @param planType the plan tier
     * @return the configured pricing, or a zero-amount default
     */
    public PlanPricing pricingFor(PlanType planType) {
        if (plans != null) {
            PlanPricing pricing = plans.get(planType.name().toLowerCase());
            if (pricing != null) {
                return pricing;
            }
        }
        return new PlanPricing(0L, null);
    }

    public boolean isStripeEnabled() {
        return PROVIDER_STRIPE.equalsIgnoreCase(paymentProvider);
    }
}
