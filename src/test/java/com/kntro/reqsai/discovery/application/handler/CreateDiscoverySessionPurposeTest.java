package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.CreateDiscoverySessionCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.SessionPurpose;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("Application: Create Discovery Session with a purpose")
@ExtendWith(MockitoExtension.class)
class CreateDiscoverySessionPurposeTest {

    @Mock
    private DiscoverySessionRepository sessions;
    @InjectMocks
    private CreateDiscoverySessionCommandHandler handler;

    @Test
    @DisplayName("a command without a purpose creates a discovery meeting")
    void defaultsToDiscovery() {
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DiscoverySession session = handler.handle(new CreateDiscoverySessionCommand(UUID.randomUUID(), "Kickoff", "es-PE"));

        assertThat(session.getPurpose()).isEqualTo(SessionPurpose.DISCOVERY);
    }

    @Test
    @DisplayName("a clarification meeting is created with its purpose")
    void clarification() {
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DiscoverySession session = handler.handle(
                new CreateDiscoverySessionCommand(UUID.randomUUID(), "Aclaración", "es-PE", SessionPurpose.CLARIFICATION));

        assertThat(session.getPurpose()).isEqualTo(SessionPurpose.CLARIFICATION);
    }
}
