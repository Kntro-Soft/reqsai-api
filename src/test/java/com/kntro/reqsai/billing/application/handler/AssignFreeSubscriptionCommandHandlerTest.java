package com.kntro.reqsai.billing.application.handler;

import com.kntro.reqsai.billing.application.command.AssignFreeSubscriptionCommand;
import com.kntro.reqsai.billing.application.port.SubscriptionRepositoryPort;
import com.kntro.reqsai.billing.domain.model.Subscription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Application: AssignFreeSubscriptionCommandHandler")
@ExtendWith(MockitoExtension.class)
class AssignFreeSubscriptionCommandHandlerTest {

    @Mock
    private SubscriptionRepositoryPort repository;

    @InjectMocks
    private AssignFreeSubscriptionCommandHandler handler;

    @Test
    @DisplayName("should create and save FREE subscription if not exists")
    void should_save_free_subscription_when_new() {
        UUID orgId = UUID.randomUUID();
        when(repository.existsByOrganizationId(orgId)).thenReturn(false);

        handler.handle(new AssignFreeSubscriptionCommand(orgId));

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(repository).save(captor.capture());
        Subscription saved = captor.getValue();
        assertThat(saved.getOrganizationId()).isEqualTo(orgId);
        assertThat(saved.isFree()).isTrue();
    }

    @Test
    @DisplayName("should be a no-op if subscription already exists")
    void should_skip_when_already_exists() {
        UUID orgId = UUID.randomUUID();
        when(repository.existsByOrganizationId(orgId)).thenReturn(true);

        handler.handle(new AssignFreeSubscriptionCommand(orgId));

        verify(repository, never()).save(any());
    }
}
