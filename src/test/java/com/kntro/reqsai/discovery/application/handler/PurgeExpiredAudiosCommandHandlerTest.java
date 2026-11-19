package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.PurgeExpiredAudiosCommand;
import com.kntro.reqsai.discovery.application.port.AudioStoragePort;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.domain.model.AudioState;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.mothers.DiscoverySessionMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PurgeExpiredAudiosCommandHandler} (US69) with mocked
 * ports: expired audios are deleted and stamped, the transcript is never
 * touched, sessions in progress are skipped.
 */
@DisplayName("Application: Purge Expired Audios")
@ExtendWith(MockitoExtension.class)
class PurgeExpiredAudiosCommandHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-18T03:30:00Z");
    private static final Instant CUTOFF = NOW.minus(Duration.ofDays(30));

    @Mock
    private DiscoverySessionRepository sessions;
    @Mock
    private AudioStoragePort audioStorage;

    @InjectMocks
    private PurgeExpiredAudiosCommandHandler handler;

    private static DiscoverySession sessionWithAudioStoredDaysAgo(long days) {
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.uploadTranscript("Transcripción de la reunión.", 60_000L);
        session.retainAudio(NOW.minus(Duration.ofDays(days)));
        return session;
    }

    @Test
    @DisplayName("Given an organization with a 30-day retention, when the process finds audios older than 30 days, then it deletes them and keeps the transcript")
    void should_delete_expired_audios_and_keep_the_transcript() {
        DiscoverySession first = sessionWithAudioStoredDaysAgo(31);
        DiscoverySession second = sessionWithAudioStoredDaysAgo(120);
        when(sessions.findAudioPurgeCandidates(CUTOFF, 10)).thenReturn(List.of(second, first));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int purged = handler.handle(new PurgeExpiredAudiosCommand(CUTOFF, NOW, 10));

        assertThat(purged).isEqualTo(2);
        InOrder order = inOrder(audioStorage, sessions);
        order.verify(audioStorage).delete(second.getId());
        order.verify(sessions).save(second);
        order.verify(audioStorage).delete(first.getId());
        order.verify(sessions).save(first);
        assertThat(List.of(first, second)).allSatisfy(session -> {
            assertThat(session.getAudioState()).isEqualTo(AudioState.PURGED);
            assertThat(session.getAudioPurgedAt()).isEqualTo(NOW);
            assertThat(session.getTranscript()).isEqualTo("Transcripción de la reunión.");
        });
    }

    @Test
    @DisplayName("should do nothing when there are no expired audios (idempotent re-run)")
    void should_do_nothing_without_candidates() {
        when(sessions.findAudioPurgeCandidates(CUTOFF, 10)).thenReturn(List.of());

        int purged = handler.handle(new PurgeExpiredAudiosCommand(CUTOFF, NOW, 10));

        assertThat(purged).isZero();
        verify(audioStorage, never()).delete(any());
        verify(sessions, never()).save(any());
    }

    @Test
    @DisplayName("Given an audio linked to a session still in progress, when the process runs, then it does not delete that audio")
    void should_not_delete_the_audio_of_a_session_in_progress() {
        DiscoverySession inProgress = DiscoverySessionMother.draft().build();
        inProgress.startRecording(NOW.minus(Duration.ofDays(90)));
        inProgress.retainAudio(NOW.minus(Duration.ofDays(90)));
        when(sessions.findAudioPurgeCandidates(CUTOFF, 10)).thenReturn(List.of(inProgress));

        int purged = handler.handle(new PurgeExpiredAudiosCommand(CUTOFF, NOW, 10));

        assertThat(purged).isZero();
        assertThat(inProgress.getAudioState()).isEqualTo(AudioState.STORED);
        verify(audioStorage, never()).delete(any());
        verify(sessions, never()).save(any());
    }

    @Test
    @DisplayName("should skip an audio that is not yet due while purging the others")
    void should_skip_audios_that_are_not_due() {
        DiscoverySession recent = sessionWithAudioStoredDaysAgo(5);
        DiscoverySession expired = sessionWithAudioStoredDaysAgo(45);
        when(sessions.findAudioPurgeCandidates(CUTOFF, 10)).thenReturn(List.of(recent, expired));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int purged = handler.handle(new PurgeExpiredAudiosCommand(CUTOFF, NOW, 10));

        assertThat(purged).isEqualTo(1);
        assertThat(recent.getAudioState()).isEqualTo(AudioState.STORED);
        assertThat(expired.getAudioState()).isEqualTo(AudioState.PURGED);
        verify(audioStorage, never()).delete(recent.getId());
    }
}
