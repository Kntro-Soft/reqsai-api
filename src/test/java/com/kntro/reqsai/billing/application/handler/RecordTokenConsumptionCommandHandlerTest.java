package com.kntro.reqsai.billing.application.handler;

import com.kntro.reqsai.billing.application.command.RecordTokenConsumptionCommand;
import com.kntro.reqsai.billing.application.port.SubscriptionRepositoryPort;
import com.kntro.reqsai.billing.application.port.TokenCounterState;
import com.kntro.reqsai.billing.domain.model.valueobjects.PlanType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
    @DisplayName("adds the tokens to the organization's counter with the atomic increment")
    void should_record_tokens() {
        UUID orgId = UUID.randomUUID();
        when(subscriptions.addTokens(eq(orgId), eq(750L), any(Instant.class)))
                .thenReturn(Optional.of(new TokenCounterState(750L, PlanType.FREE)));

        handler.handle(new RecordTokenConsumptionCommand(orgId, 750));

        verify(subscriptions).addTokens(eq(orgId), eq(750L), any(Instant.class));
        verify(subscriptions, never()).save(any());
    }

    @Test
    @DisplayName("non-positive token counts are ignored (nothing is touched)")
    void should_ignore_non_positive() {
        UUID orgId = UUID.randomUUID();

        handler.handle(new RecordTokenConsumptionCommand(orgId, 0));

        verify(subscriptions, never()).addTokens(any(), anyLong(), any());
    }

    @Test
    @DisplayName("missing subscription is a safe no-op")
    void should_noop_when_missing() {
        UUID orgId = UUID.randomUUID();
        when(subscriptions.addTokens(eq(orgId), eq(500L), any(Instant.class))).thenReturn(Optional.empty());

        handler.handle(new RecordTokenConsumptionCommand(orgId, 500));

        verify(subscriptions, never()).save(any());
    }
}
