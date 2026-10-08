package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.UploadTranscriptCommand;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.TranscriptionPort;
import com.kntro.reqsai.discovery.application.port.TranscriptionResult;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.SessionStatus;
import com.kntro.reqsai.discovery.mothers.DiscoverySessionMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UploadTranscriptCommandHandler}: the uploaded audio is transcribed with the
 * session's meeting language as a hint, and the transcript moves the session to {@code STOPPED}.
 */
@DisplayName("Application: Upload Transcript")
@ExtendWith(MockitoExtension.class)
class UploadTranscriptCommandHandlerTest {

    @Mock
    private DiscoverySessionRepository sessions;
    @Mock
    private TranscriptionPort transcription;
    @InjectMocks
    private UploadTranscriptCommandHandler handler;

    @Test
    @DisplayName("should hint the session's language (es-PE → es) and store the transcript")
    void should_hint_session_language_and_store_transcript() {
        DiscoverySession session = DiscoverySessionMother.draft().withLanguage("es-PE").build();
        byte[] audio = {1, 2, 3};
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(transcription.transcribe(audio, "reunion.mp3", "es"))
                .thenReturn(TranscriptionResult.textOnly("Quiero reservar una mesa.", 42_000L));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DiscoverySession saved = handler.handle(new UploadTranscriptCommand(session.getId(), audio, "reunion.mp3"));

        verify(transcription).transcribe(audio, "reunion.mp3", "es");
        assertThat(saved.getTranscript()).isEqualTo("Quiero reservar una mesa.");
        assertThat(saved.getAudioDurationMs()).isEqualTo(42_000L);
        assertThat(saved.getStatus()).isEqualTo(SessionStatus.STOPPED);
    }
}
