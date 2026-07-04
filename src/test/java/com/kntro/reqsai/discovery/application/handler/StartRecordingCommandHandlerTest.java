package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.StartRecordingCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.SessionStatus;
import com.kntro.reqsai.discovery.mothers.DiscoverySessionMother;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Application: Start Recording (single active session)")
class StartRecordingCommandHandlerTest {

    @Mock
    private DiscoverySessionRepository sessions;
    @InjectMocks
    private StartRecordingCommandHandler handler;

    @Test
    @DisplayName("should start recording when no other session of the project is active")
    void should_start_when_none_active() {
        UUID projectId = UUID.randomUUID();
        DiscoverySession session = DiscoverySessionMother.draft().withProjectId(projectId).build();
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessions.findActiveByProjectId(projectId)).thenReturn(Optional.empty());
        when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DiscoverySession result = handler.handle(new StartRecordingCommand(projectId, session.getId()));

        assertThat(result.getStatus()).isEqualTo(SessionStatus.RECORDING);
    }

    @Test
    @DisplayName("should reject with SESSION_ALREADY_ACTIVE when another session of the project is active")
    void should_reject_when_another_active() {
        UUID projectId = UUID.randomUUID();
        DiscoverySession session = DiscoverySessionMother.draft().withProjectId(projectId).build();
        DiscoverySession active = DiscoverySessionMother.draft().withProjectId(projectId).build();
        active.startRecording(Instant.now());
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessions.findActiveByProjectId(projectId)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> handler.handle(new StartRecordingCommand(projectId, session.getId())))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).error()).isEqualTo(DiscoveryError.SESSION_ALREADY_ACTIVE);
                    assertThat(e.getMessage()).contains(active.getId().toString());
                });
        verify(sessions, never()).save(any());
    }
}
