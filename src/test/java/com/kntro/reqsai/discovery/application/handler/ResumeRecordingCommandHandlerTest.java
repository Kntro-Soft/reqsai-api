package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.ResumeRecordingCommand;
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
@DisplayName("Application: Resume Recording (single active session)")
class ResumeRecordingCommandHandlerTest {

    @Mock
    private DiscoverySessionRepository sessions;
    @InjectMocks
    private ResumeRecordingCommandHandler handler;

    private DiscoverySession pausedSession(UUID projectId) {
        DiscoverySession session = DiscoverySessionMother.draft().withProjectId(projectId).build();
        session.startRecording(Instant.now());
        session.pauseRecording();
        return session;
    }

    @Test
    @DisplayName("should resume when the only active session of the project is itself")
    void should_resume_when_self_is_only_active() {
        UUID projectId = UUID.randomUUID();
        DiscoverySession session = pausedSession(projectId);
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessions.findActiveByProjectId(projectId)).thenReturn(Optional.of(session));
        when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DiscoverySession result = handler.handle(new ResumeRecordingCommand(projectId, session.getId()));

        assertThat(result.getStatus()).isEqualTo(SessionStatus.RECORDING);
    }

    @Test
    @DisplayName("should reject resume when a different session of the project is active")
    void should_reject_when_another_active() {
        UUID projectId = UUID.randomUUID();
        DiscoverySession session = pausedSession(projectId);
        DiscoverySession other = DiscoverySessionMother.draft().withProjectId(projectId).build();
        other.startRecording(Instant.now());
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessions.findActiveByProjectId(projectId)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> handler.handle(new ResumeRecordingCommand(projectId, session.getId())))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).error()).isEqualTo(DiscoveryError.SESSION_ALREADY_ACTIVE);
                    assertThat(e.getMessage()).contains(other.getId().toString());
                });
        verify(sessions, never()).save(any());
    }
}
