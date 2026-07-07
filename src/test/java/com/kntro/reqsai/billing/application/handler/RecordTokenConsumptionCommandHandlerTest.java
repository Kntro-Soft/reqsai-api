package com.kntro.reqsai.billing.application.handler;

import com.kntro.reqsai.billing.application.command.RecordTokenConsumptionCommand;
import com.kntro.reqsai.billing.application.port.SubscriptionRepositoryPort;
import com.kntro.reqsai.billing.domain.model.Subscription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: RecordTokenConsumptionCommandHandler")
@ExtendWith(MockitoExtension.class)
class RecordTokenConsumptionCommandHandlerTest {

    @Mock
    private SubscriptionRepositoryPort subscriptions;

    @InjectMocks
    private RecordTokenConsumptionCommandHandler handler;

    @Test
    @DisplayName("increments token usage on the organization's subscription")
    void should_record_tokens() {
        UUID orgId = UUID.randomUUID();
        Subscription sub = new Subscription(orgId);
        when(subscriptions.findByOrganizationId(orgId)).thenReturn(Optional.of(sub));

        handler.handle(new RecordTokenConsumptionCommand(orgId, 750));

        assertThat(sub.getTokenQuotaUsed()).isEqualTo(750L);
        verify(subscriptions).save(sub);
    }

    @Test
    @DisplayName("non-positive token counts are ignored (no lookup, no save)")
    void should_ignore_non_positive() {
        UUID orgId = UUID.randomUUID();

        handler.handle(new RecordTokenConsumptionCommand(orgId, 0));

        verify(subscriptions, never()).findByOrganizationId(any());
        verify(subscriptions, never()).save(any());
    }

    @Test
    @DisplayName("missing subscription is a safe no-op")
    void should_noop_when_missing() {
        UUID orgId = UUID.randomUUID();
        when(subscriptions.findByOrganizationId(orgId)).thenReturn(Optional.empty());

        handler.handle(new RecordTokenConsumptionCommand(orgId, 500));

        verify(subscriptions, never()).save(any());
    }
}
