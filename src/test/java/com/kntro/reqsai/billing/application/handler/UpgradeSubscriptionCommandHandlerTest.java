package com.kntro.reqsai.billing.application.handler;

import com.kntro.reqsai.billing.application.command.UpgradeSubscriptionCommand;
import com.kntro.reqsai.billing.application.config.BillingProperties;
import com.kntro.reqsai.billing.application.port.PaymentGatewayPort;
import com.kntro.reqsai.billing.application.port.PlanChangeRequest;
import com.kntro.reqsai.billing.application.port.PlanChangeResult;
import com.kntro.reqsai.billing.application.port.SubscriptionRepositoryPort;
import com.kntro.reqsai.billing.domain.model.Subscription;
import com.kntro.reqsai.billing.domain.model.valueobjects.PaymentProvider;
import com.kntro.reqsai.billing.domain.model.valueobjects.PaymentProviderRef;
import com.kntro.reqsai.billing.domain.model.valueobjects.PlanType;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: UpgradeSubscriptionCommandHandler")
class UpgradeSubscriptionCommandHandlerTest {

    private SubscriptionRepositoryPort subscriptions;
    private PaymentGatewayPort gateway;
    private UpgradeSubscriptionCommandHandler handler;

    @BeforeEach
    void setUp() {
        subscriptions = mock(SubscriptionRepositoryPort.class);
        gateway = mock(PaymentGatewayPort.class);
        BillingProperties props = new BillingProperties(
                "fake", "USD",
                Map.of("pro", new BillingProperties.PlanPricing(2900, "price_pro")),
                null);
        handler = new UpgradeSubscriptionCommandHandler(subscriptions, gateway, props);
    }

    @Test
    @DisplayName("immediate activation (fake gateway) upgrades and persists the subscription")
    void should_activate_immediately() {
        UUID orgId = UUID.randomUUID();
        Subscription sub = new Subscription(orgId);
        when(subscriptions.findByOrganizationId(orgId)).thenReturn(Optional.of(sub));
        PaymentProviderRef ref = PaymentProviderRef.of(PaymentProvider.STRIPE, "sub_1");
        when(gateway.startPlanChange(any(PlanChangeRequest.class))).thenReturn(PlanChangeResult.activated(ref));

        PlanChangeOutcome outcome = handler.handle(new UpgradeSubscriptionCommand(orgId, PlanType.PRO));

        assertThat(outcome.activated()).isTrue();
        assertThat(outcome.checkoutUrl()).isNull();
        assertThat(sub.getPlanType()).isEqualTo(PlanType.PRO);
        verify(subscriptions).save(sub);
    }

    @Test
    @DisplayName("pending checkout (real gateway) returns the URL without changing the plan")
    void should_return_checkout_url_when_pending() {
        UUID orgId = UUID.randomUUID();
        Subscription sub = new Subscription(orgId);
        when(subscriptions.findByOrganizationId(orgId)).thenReturn(Optional.of(sub));
        when(gateway.startPlanChange(any(PlanChangeRequest.class)))
                .thenReturn(PlanChangeResult.pendingCheckout("https://checkout.stripe.com/s"));

        PlanChangeOutcome outcome = handler.handle(new UpgradeSubscriptionCommand(orgId, PlanType.PRO));

        assertThat(outcome.activated()).isFalse();
        assertThat(outcome.checkoutUrl()).isEqualTo("https://checkout.stripe.com/s");
        assertThat(sub.getPlanType()).isEqualTo(PlanType.FREE);
        verify(subscriptions, never()).save(any());
    }

    @Test
    @DisplayName("rejects a non-purchasable target plan before touching the gateway")
    void should_reject_free_target() {
        UUID orgId = UUID.randomUUID();
        assertThatThrownBy(() -> handler.handle(new UpgradeSubscriptionCommand(orgId, PlanType.FREE)))
                .isInstanceOf(DomainException.class);
        verify(gateway, never()).startPlanChange(any());
    }
}
