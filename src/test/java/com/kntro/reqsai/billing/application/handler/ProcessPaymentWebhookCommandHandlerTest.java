package com.kntro.reqsai.billing.application.handler;

import com.kntro.reqsai.billing.application.port.PaymentWebhookEvent;
import com.kntro.reqsai.billing.application.port.PaymentWebhookParserPort;
import com.kntro.reqsai.billing.application.port.ProcessedEventStorePort;
import com.kntro.reqsai.billing.application.port.SubscriptionRepositoryPort;
import com.kntro.reqsai.billing.domain.model.Subscription;
import com.kntro.reqsai.billing.domain.model.valueobjects.PaymentProvider;
import com.kntro.reqsai.billing.domain.model.valueobjects.PaymentProviderRef;
import com.kntro.reqsai.billing.domain.model.valueobjects.PlanType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: ProcessPaymentWebhookCommandHandler")
class ProcessPaymentWebhookCommandHandlerTest {

    private PaymentWebhookParserPort parser;
    private ProcessedEventStorePort processedEvents;
    private SubscriptionRepositoryPort subscriptions;
    private ProcessPaymentWebhookCommandHandler handler;

    @BeforeEach
    void setUp() {
        parser = mock(PaymentWebhookParserPort.class);
        processedEvents = mock(ProcessedEventStorePort.class);
        subscriptions = mock(SubscriptionRepositoryPort.class);
        handler = new ProcessPaymentWebhookCommandHandler(parser, processedEvents, subscriptions);
    }

    @Test
    @DisplayName("PLAN_ACTIVATED upgrades the organization's subscription")
    void should_activate_plan() {
        UUID orgId = UUID.randomUUID();
        Subscription sub = new Subscription(orgId);
        when(parser.verifyAndParse(anyString(), anyString())).thenReturn(new PaymentWebhookEvent(
                "evt_1", PaymentWebhookEvent.Kind.PLAN_ACTIVATED, orgId, PlanType.PRO, "sub_1"));
        when(processedEvents.markProcessed("evt_1", "PLAN_ACTIVATED")).thenReturn(true);
        when(subscriptions.findByOrganizationId(orgId)).thenReturn(Optional.of(sub));

        handler.handle("{}", "sig");

        assertThat(sub.getPlanType()).isEqualTo(PlanType.PRO);
        verify(subscriptions).save(sub);
    }

    @Test
    @DisplayName("a redelivered (already processed) event is a no-op")
    void should_skip_duplicate() {
        UUID orgId = UUID.randomUUID();
        when(parser.verifyAndParse(anyString(), anyString())).thenReturn(new PaymentWebhookEvent(
                "evt_1", PaymentWebhookEvent.Kind.PLAN_ACTIVATED, orgId, PlanType.PRO, "sub_1"));
        when(processedEvents.markProcessed("evt_1", "PLAN_ACTIVATED")).thenReturn(false);

        handler.handle("{}", "sig");

        verify(subscriptions, never()).findByOrganizationId(any());
        verify(subscriptions, never()).save(any());
    }

    @Test
    @DisplayName("SUBSCRIPTION_DELETED downgrades the referenced subscription to FREE")
    void should_downgrade_on_delete() {
        Subscription sub = new Subscription(UUID.randomUUID());
        sub.upgradeTo(PlanType.PRO, PaymentProviderRef.of(PaymentProvider.STRIPE, "sub_9"));
        when(parser.verifyAndParse(anyString(), anyString())).thenReturn(new PaymentWebhookEvent(
                "evt_2", PaymentWebhookEvent.Kind.SUBSCRIPTION_DELETED, null, null, "sub_9"));
        when(processedEvents.markProcessed("evt_2", "SUBSCRIPTION_DELETED")).thenReturn(true);
        when(subscriptions.findByProviderExternalId("sub_9")).thenReturn(Optional.of(sub));

        handler.handle("{}", "sig");

        assertThat(sub.getPlanType()).isEqualTo(PlanType.FREE);
        verify(subscriptions).save(sub);
    }

    @Test
    @DisplayName("IGNORED events are not de-duplicated or applied")
    void should_ignore() {
        when(parser.verifyAndParse(anyString(), anyString())).thenReturn(new PaymentWebhookEvent(
                "evt_3", PaymentWebhookEvent.Kind.IGNORED, null, null, null));

        handler.handle("{}", "sig");

        verify(processedEvents, never()).markProcessed(any(), any());
        verify(subscriptions, never()).save(any());
    }
}
