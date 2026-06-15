package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.SessionStatus;
import com.kntro.reqsai.discovery.mothers.CreateDiscoverySessionCommandMother;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CreateDiscoverySessionCommandHandler} with a mocked repository.
 *
 * @see CreateDiscoverySessionCommandHandler
 */
@DisplayName("Application: Create Discovery Session")
@ExtendWith(MockitoExtension.class)
class CreateDiscoverySessionCommandHandlerTest {

    @Mock
    private DiscoverySessionRepository sessions;
    @InjectMocks
    private CreateDiscoverySessionCommandHandler handler;

    @Test
    @DisplayName("should create the session in DRAFT and persist it")
    void should_create_session_in_draft() {
        // Arrange
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var command = CreateDiscoverySessionCommandMother.valid();

        // Act
        DiscoverySession session = handler.handle(command);

        // Assert
        assertThat(session.getStatus()).isEqualTo(SessionStatus.DRAFT);
        assertThat(session.getProjectId()).isEqualTo(command.projectId());
        assertThat(session.getLanguage().value()).isEqualTo(command.language());
        verify(sessions).save(any(DiscoverySession.class));
    }

    @Test
    @DisplayName("should reject an invalid language before persisting")
    void should_reject_invalid_language() {
        // Act & Assert
        assertThatThrownBy(() -> handler.handle(CreateDiscoverySessionCommandMother.withInvalidLanguage()))
                .isInstanceOf(DomainException.class);
        verify(sessions, never()).save(any());
    }
}
