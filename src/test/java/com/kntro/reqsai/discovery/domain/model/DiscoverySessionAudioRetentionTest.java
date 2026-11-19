package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.mothers.DiscoverySessionMother;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the audio retention behavior of {@link DiscoverySession}
 * (US69): the session knows whether its recording is stored or was purged, and
 * never lets the purge touch the audio of a session in progress.
 */
@DisplayName("Domain: DiscoverySession audio retention (US69)")
class DiscoverySessionAudioRetentionTest {

    private static final Instant NOW = Instant.parse("2026-11-18T03:30:00Z");

    /**
     * A session that was created from an uploaded recording stored {@code daysAgo}
     * days ago and is STOPPED.
     */
    private static DiscoverySession uploadedSession(long daysAgo) {
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.uploadTranscript("El cliente quiere reservar una mesa.", 60_000L);
        session.retainAudio(NOW.minus(Duration.ofDays(daysAgo)));
        return session;
    }

    @Test
    @DisplayName("should have no audio until a recording is retained")
    void should_have_no_audio_by_default() {
        DiscoverySession session = DiscoverySessionMother.draft().build();

        assertThat(session.getAudioState()).isEqualTo(AudioState.NONE);
        assertThat(session.getAudioStoredAt()).isNull();
        assertThat(session.getAudioPurgedAt()).isNull();
    }

    @Test
    @DisplayName("should be STORED once the recording is retained")
    void should_be_stored_once_retained() {
        DiscoverySession session = uploadedSession(0);

        assertThat(session.getAudioState()).isEqualTo(AudioState.STORED);
        assertThat(session.getAudioStoredAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("should reject retaining a second audio")
    void should_reject_retaining_twice() {
        DiscoverySession session = uploadedSession(0);

        assertThatThrownBy(() -> session.retainAudio(NOW)).isInstanceOf(DomainException.class).satisfies(
                e -> assertThat(((DomainException) e).error()).isEqualTo(DiscoveryError.AUDIO_NOT_PURGEABLE));
    }

    @Nested
    @DisplayName("Given an audio older than the retention period")
    class ExpiredAudio {

        @Test
        @DisplayName("When purged, then the audio is marked PURGED and the transcript is kept")
        void should_purge_and_keep_transcript() {
            DiscoverySession session = uploadedSession(31);

            session.purgeAudio(NOW);

            assertThat(session.getAudioState()).isEqualTo(AudioState.PURGED);
            assertThat(session.getAudioPurgedAt()).isEqualTo(NOW);
            assertThat(session.getTranscript()).isEqualTo("El cliente quiere reservar una mesa.");
            assertThat(session.getStatus()).isEqualTo(SessionStatus.STOPPED);
        }

        @Test
        @DisplayName("Then it is purgeable only once the cutoff reaches its storage instant")
        void should_be_purgeable_by_cutoff() {
            DiscoverySession session = uploadedSession(31);

            assertThat(session.isAudioPurgeable(NOW.minus(Duration.ofDays(30)))).isTrue();
            assertThat(session.isAudioPurgeable(NOW.minus(Duration.ofDays(31)))).isTrue();
            assertThat(session.isAudioPurgeable(NOW.minus(Duration.ofDays(32)))).isFalse();
        }

        @Test
        @DisplayName("When purged twice, then the second purge is refused")
        void should_refuse_a_second_purge() {
            DiscoverySession session = uploadedSession(31);
            session.purgeAudio(NOW);

            assertThatThrownBy(() -> session.purgeAudio(NOW.plusSeconds(1))).isInstanceOf(DomainException.class)
                    .satisfies(e -> assertThat(((DomainException) e).error())
                            .isEqualTo(DiscoveryError.AUDIO_NOT_PURGEABLE));
            assertThat(session.getAudioPurgedAt()).isEqualTo(NOW);
            assertThat(session.isAudioPurgeable(NOW)).isFalse();
        }
    }

    @Nested
    @DisplayName("Given an audio linked to a session still in progress")
    class SessionInProgress {

        @Test
        @DisplayName("When the scheduled process runs on a RECORDING session, then the audio is not deleted")
        void should_not_purge_while_recording() {
            DiscoverySession session = uploadedSessionLeftIn(SessionStatus.RECORDING);

            assertThat(session.isAudioPurgeable(NOW)).isFalse();
            assertThatThrownBy(() -> session.purgeAudio(NOW)).isInstanceOf(DomainException.class).satisfies(
                    e -> assertThat(((DomainException) e).error()).isEqualTo(DiscoveryError.AUDIO_NOT_PURGEABLE));
            assertThat(session.getAudioState()).isEqualTo(AudioState.STORED);
        }

        @Test
        @DisplayName("When the scheduled process runs on a PAUSED session, then the audio is not deleted")
        void should_not_purge_while_paused() {
            DiscoverySession session = uploadedSessionLeftIn(SessionStatus.PAUSED);

            assertThat(session.isAudioPurgeable(NOW)).isFalse();
            assertThat(session.getAudioState()).isEqualTo(AudioState.STORED);
        }

        @Test
        @DisplayName("When the scheduled process runs on a PROCESSING session, then the audio is not deleted")
        void should_not_purge_while_processing() {
            DiscoverySession session = uploadedSessionLeftIn(SessionStatus.PROCESSING);

            assertThat(session.isAudioPurgeable(NOW)).isFalse();
            assertThatThrownBy(() -> session.purgeAudio(NOW)).isInstanceOf(DomainException.class);
            assertThat(session.getAudioState()).isEqualTo(AudioState.STORED);
        }

        @Test
        @DisplayName("When the session finishes, then the audio becomes purgeable")
        void should_be_purgeable_after_the_session_finishes() {
            DiscoverySession session = uploadedSessionLeftIn(SessionStatus.PROCESSING);
            session.complete();

            assertThat(session.isAudioPurgeable(NOW)).isTrue();
        }

        private DiscoverySession uploadedSessionLeftIn(SessionStatus status) {
            DiscoverySession session = DiscoverySessionMother.draft().build();
            Instant storedAt = NOW.minus(Duration.ofDays(90));
            switch (status) {
                case RECORDING -> {
                    session.startRecording(storedAt);
                    session.retainAudio(storedAt);
                }
                case PAUSED -> {
                    session.startRecording(storedAt);
                    session.pauseRecording();
                    session.retainAudio(storedAt);
                }
                case PROCESSING -> {
                    session.uploadTranscript("Transcripción.", 1_000L);
                    session.retainAudio(storedAt);
                    session.startProcessing();
                }
                default -> throw new IllegalArgumentException(status.name());
            }
            assertThat(session.getStatus()).isEqualTo(status);
            return session;
        }
    }

    @ParameterizedTest
    @EnumSource(value = SessionStatus.class, names = {"RECORDING", "PAUSED", "PROCESSING"})
    @DisplayName("should treat RECORDING, PAUSED and PROCESSING as in progress")
    void should_flag_in_progress_statuses(SessionStatus status) {
        assertThat(status.isInProgress()).isTrue();
        assertThat(SessionStatus.inProgress()).contains(status);
    }

    @ParameterizedTest
    @EnumSource(value = SessionStatus.class, names = {"DRAFT", "STOPPED", "COMPLETED", "FAILED"})
    @DisplayName("should treat the remaining statuses as not in progress")
    void should_not_flag_finished_statuses(SessionStatus status) {
        assertThat(status.isInProgress()).isFalse();
        assertThat(SessionStatus.inProgress()).doesNotContain(status);
    }
}
