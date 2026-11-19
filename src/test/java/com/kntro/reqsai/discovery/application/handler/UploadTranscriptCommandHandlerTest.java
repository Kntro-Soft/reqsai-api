package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.shared.application.ai.AiQuotaGuard;
import com.kntro.reqsai.shared.application.ai.AiUsageReporter;
import com.kntro.reqsai.discovery.application.command.UploadTranscriptCommand;
import com.kntro.reqsai.discovery.application.port.AudioStoragePort;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.TranscriptSegmentRepository;
import com.kntro.reqsai.discovery.application.port.TranscriptionPort;
import com.kntro.reqsai.discovery.application.port.TranscriptionResult;
import com.kntro.reqsai.discovery.domain.model.AudioState;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.SessionStatus;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.discovery.mothers.DiscoverySessionMother;
import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
    @Mock
    private TranscriptSegmentRepository segments;
    @Mock
    private AiUsageReporter usage;
    @Mock
    private AiQuotaGuard quota;
    @Mock
    private WorkspaceModuleApi workspace;
    @Mock
    private AudioStoragePort audioStorage;

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
        verify(segments, never()).saveAll(any());
    }

    @Test
    @DisplayName("should keep the uploaded recording and start its retention period when the organization retains audios (US69)")
    void should_keep_the_recording_when_the_organization_retains_audios() {
        DiscoverySession session = DiscoverySessionMother.draft().withLanguage("es-PE").build();
        byte[] audio = {1, 2, 3};
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(transcription.transcribe(audio, "reunion.mp3", "es"))
                .thenReturn(TranscriptionResult.textOnly("Quiero reservar una mesa.", 42_000L));
        when(workspace.currentAudioRetentionDays()).thenReturn(Optional.of(30));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DiscoverySession saved = handler.handle(new UploadTranscriptCommand(session.getId(), audio, "reunion.mp3"));

        verify(audioStorage).store(session.getId(), "reunion.mp3", audio);
        assertThat(saved.getAudioState()).isEqualTo(AudioState.STORED);
        assertThat(saved.getAudioStoredAt()).isNotNull();
    }

    @Test
    @DisplayName("should keep the recording when the organization keeps audios forever")
    void should_keep_the_recording_when_retention_is_forever() {
        DiscoverySession session = DiscoverySessionMother.draft().build();
        byte[] audio = {1, 2, 3};
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(transcription.transcribe(any(), any(), any()))
                .thenReturn(TranscriptionResult.textOnly("Quiero reservar una mesa.", 42_000L));
        when(workspace.currentAudioRetentionDays()).thenReturn(Optional.of(-1));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DiscoverySession saved = handler.handle(new UploadTranscriptCommand(session.getId(), audio, "reunion.mp3"));

        verify(audioStorage).store(session.getId(), "reunion.mp3", audio);
        assertThat(saved.getAudioState()).isEqualTo(AudioState.STORED);
    }

    @Test
    @DisplayName("should not store the recording when the organization deletes audios immediately (0 days)")
    void should_not_store_the_recording_when_retention_is_zero() {
        DiscoverySession session = DiscoverySessionMother.draft().build();
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(transcription.transcribe(any(), any(), any()))
                .thenReturn(TranscriptionResult.textOnly("Quiero reservar una mesa.", 42_000L));
        when(workspace.currentAudioRetentionDays()).thenReturn(Optional.of(0));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DiscoverySession saved = handler.handle(new UploadTranscriptCommand(session.getId(), new byte[] {1}, "reunion.mp3"));

        verify(audioStorage, never()).store(any(), any(), any());
        assertThat(saved.getAudioState()).isEqualTo(AudioState.NONE);
        assertThat(saved.getStatus()).isEqualTo(SessionStatus.STOPPED);
    }

    @Test
    @DisplayName("should not store the recording when the organization's policy cannot be read")
    void should_not_store_the_recording_without_a_policy() {
        DiscoverySession session = DiscoverySessionMother.draft().build();
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(transcription.transcribe(any(), any(), any()))
                .thenReturn(TranscriptionResult.textOnly("Quiero reservar una mesa.", 42_000L));
        when(workspace.currentAudioRetentionDays()).thenReturn(Optional.empty());
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DiscoverySession saved = handler.handle(new UploadTranscriptCommand(session.getId(), new byte[] {1}, "reunion.mp3"));

        verify(audioStorage, never()).store(any(), any(), any());
        assertThat(saved.getAudioState()).isEqualTo(AudioState.NONE);
    }

    @Test
    @DisplayName("should keep each diarized utterance as a final segment with its speaker, in time order (US40)")
    void should_keep_diarized_utterances_as_segments() {
        DiscoverySession session = DiscoverySessionMother.draft().withLanguage("es-PE").build();
        byte[] audio = {1, 2, 3};
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(transcription.transcribe(audio, "reunion.wav", "es")).thenReturn(new TranscriptionResult(
                "Quiero reservar. ¿Para cuántos? Para cuatro.", "es", 6_000L, 0.9, List.of(
                        new TranscriptionResult.SpeakerSegment("1", "¿Para cuántos?", 2_000, 3_000, 0.9),
                        new TranscriptionResult.SpeakerSegment("0", " Quiero reservar. ", 0, 1_800, 0.9),
                        new TranscriptionResult.SpeakerSegment("1", "  ", 3_000, 3_100, 0.9),
                        new TranscriptionResult.SpeakerSegment("0", "Para cuatro.", 3_200, 4_000, 0.9))));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DiscoverySession saved = handler.handle(new UploadTranscriptCommand(session.getId(), audio, "reunion.wav"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TranscriptSegment>> captor = ArgumentCaptor.forClass(List.class);
        verify(segments).saveAll(captor.capture());
        List<TranscriptSegment> kept = captor.getValue();
        assertThat(kept).extracting(TranscriptSegment::getSequence).containsExactly(1, 2, 3);
        assertThat(kept).extracting(TranscriptSegment::getSpeakerLabel).containsExactly("0", "1", "0");
        assertThat(kept).extracting(TranscriptSegment::getText)
                .containsExactly("Quiero reservar.", "¿Para cuántos?", "Para cuatro.");
        assertThat(kept).allMatch(TranscriptSegment::isFinal);
        assertThat(saved.getLastSequence()).isEqualTo(3);
        assertThat(saved.getTranscript()).isEqualTo("Quiero reservar. ¿Para cuántos? Para cuatro.");
    }
}
