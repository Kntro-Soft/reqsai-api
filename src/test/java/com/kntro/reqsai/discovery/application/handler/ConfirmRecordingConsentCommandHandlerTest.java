package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.ConfirmRecordingConsentCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.mothers.DiscoverySessionMother;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Application: Confirm Recording Consent (US70)")
class ConfirmRecordingConsentCommandHandlerTest {

    @Mock
    private DiscoverySessionRepository sessions;
    @InjectMocks
    private ConfirmRecordingConsentCommandHandler handler;

    @Test
    @DisplayName("should record the confirmation with the user and the date and persist the session")
    void should_record_consent() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        DiscoverySession session = DiscoverySessionMother.draft().withProjectId(projectId).build();
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DiscoverySession result = handler.handle(new ConfirmRecordingConsentCommand(projectId, session.getId(), userId));

        assertThat(result.hasRecordingConsent()).isTrue();
        assertThat(result.getRecordingConsentBy()).isEqualTo(userId);
        assertThat(result.getRecordingConsentAt()).isNotNull();
        verify(sessions).save(session);
    }

    @Test
    @DisplayName("should reject with SESSION_NOT_FOUND when the session belongs to another project")
    void should_reject_foreign_project() {
        DiscoverySession session = DiscoverySessionMother.draft().withProjectId(UUID.randomUUID()).build();
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> handler.handle(
                new ConfirmRecordingConsentCommand(UUID.randomUUID(), session.getId(), UUID.randomUUID())))
                .isInstanceOf(EntityNotFoundException.class)
                .satisfies(e -> assertThat(((EntityNotFoundException) e).error()).isEqualTo(DiscoveryError.SESSION_NOT_FOUND));
        verify(sessions, never()).save(any());
    }
}
