package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.discovery.domain.event.DiscoverySessionCreatedEvent;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.mothers.DiscoverySessionMother;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.testsupport.AggregateEvents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the DiscoverySession aggregate root (creation slice).
 *
 * @see DiscoverySession
 */
@DisplayName("Domain: DiscoverySession Aggregate")
class DiscoverySessionTest {

    @Test
    @DisplayName("should create the session in DRAFT")
    void should_create_in_draft() {
        // Act
        DiscoverySession session = DiscoverySessionMother.draft().build();

        // Assert
        assertThat(session.getStatus()).isEqualTo(SessionStatus.DRAFT);
        assertThat(session.getId()).isNotNull();
        assertThat(session.getProjectId()).isNotNull();
        assertThat(session.getLanguage()).isNotNull();
        assertThat(session.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("should register DiscoverySessionCreatedEvent on creation")
    void should_register_created_event() {
        // Act
        DiscoverySession session = DiscoverySessionMother.draft().build();

        // Assert
        assertThat(AggregateEvents.of(session))
                .anySatisfy(e -> assertThat(e).isInstanceOf(DiscoverySessionCreatedEvent.class));
    }

    @Test
    @DisplayName("should reject a blank title")
    void should_reject_blank_title() {
        // Act & Assert
        assertThatThrownBy(() -> DiscoverySessionMother.draft().withTitle("  ").build())
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("should transition to STOPPED and save transcript on uploadTranscript")
    void should_upload_transcript_and_stop() {
        // Arrange
        DiscoverySession session = DiscoverySessionMother.draft().build();
        String transcript = "El cliente quiere un login con Google.";

        // Act
        session.uploadTranscript(transcript, 0L);

        // Assert
        assertThat(session.getStatus()).isEqualTo(SessionStatus.STOPPED);
        assertThat(session.getTranscript()).isEqualTo(transcript);
        assertThat(session.getEndedAt()).isNotNull();
    }

    @Test
    @DisplayName("should reject uploadTranscript with blank text")
    void should_reject_blank_transcript() {
        // Arrange
        DiscoverySession session = DiscoverySessionMother.draft().build();

        // Act & Assert
        assertThatThrownBy(() -> session.uploadTranscript("   ", 0L))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("should reject uploadTranscript when session is not in DRAFT")
    void should_reject_upload_when_not_draft() {
        // Arrange
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.uploadTranscript("Primera transcripción.", 0L);

        // Act & Assert
        assertThatThrownBy(() -> session.uploadTranscript("Segunda transcripción.", 0L))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).error())
                        .isEqualTo(DiscoveryError.INVALID_SESSION_STATUS));
    }

    @Test
    @DisplayName("should transition STOPPED → PROCESSING → COMPLETED")
    void should_process_and_complete() {
        // Arrange
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.uploadTranscript("Some transcript.", 0L);

        // Act
        session.startProcessing();
        session.complete();

        // Assert
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    @DisplayName("should transition PROCESSING → FAILED with reason")
    void should_fail_with_reason() {
        // Arrange
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.uploadTranscript("Some transcript.", 0L);
        session.startProcessing();

        // Act
        session.fail("Quota exceeded");

        // Assert
        assertThat(session.getStatus()).isEqualTo(SessionStatus.FAILED);
        assertThat(session.getProcessingError()).isEqualTo("Quota exceeded");
    }

    @Test
    @DisplayName("should reject startProcessing when not STOPPED or FAILED")
    void should_reject_start_processing_when_not_stopped() {
        // Arrange
        DiscoverySession session = DiscoverySessionMother.draft().build();

        // Act & Assert
        assertThatThrownBy(session::startProcessing)
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).error())
                        .isEqualTo(DiscoveryError.INVALID_SESSION_STATUS));
    }

    @Test
    @DisplayName("should allow retry: FAILED → PROCESSING → COMPLETED")
    void should_allow_retry_from_failed() {
        // Arrange
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.uploadTranscript("Some transcript.", 0L);
        session.startProcessing();
        session.fail("Timeout");

        // Act
        session.startProcessing();
        session.complete();

        // Assert
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    @DisplayName("should transition status to RECORDING and set startedAt when starting recording")
    void should_transition_to_recording() {
        // Arrange
        DiscoverySession session = DiscoverySessionMother.draft().build();

        // Act
        session.startRecording(Instant.now());

        // Assert
        assertThat(session.getStatus()).isEqualTo(SessionStatus.RECORDING);
        assertThat(session.getStartedAt()).isNotNull();
        assertThat(session.getProcessingError()).isNull();
    }

    @Test
    @DisplayName("should reject starting recording if session is not in DRAFT")
    void should_reject_start_recording_if_not_in_draft() {
        // Arrange
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.startRecording(Instant.now()); // Now status is RECORDING

        // Act & Assert
        assertThatThrownBy(() -> session.startRecording(Instant.now()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("should transition status to PAUSED when pausing recording")
    void should_transition_to_paused() {
        // Arrange
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.startRecording(Instant.now());

        // Act
        session.pauseRecording();

        // Assert
        assertThat(session.getStatus()).isEqualTo(SessionStatus.PAUSED);
    }

    @Test
    @DisplayName("should reject pausing if session is not in RECORDING")
    void should_reject_pause_if_not_recording() {
        // Arrange
        DiscoverySession session = DiscoverySessionMother.draft().build();

        // Act & Assert
        assertThatThrownBy(session::pauseRecording)
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("should transition status to RECORDING when resuming recording")
    void should_transition_to_recording_on_resume() {
        // Arrange
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.startRecording(Instant.now());
        session.pauseRecording();

        // Act
        session.resumeRecording();

        // Assert
        assertThat(session.getStatus()).isEqualTo(SessionStatus.RECORDING);
    }

    @Test
    @DisplayName("should reject resuming if session is not in PAUSED")
    void should_reject_resume_if_not_paused() {
        // Arrange
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.startRecording(Instant.now());

        // Act & Assert
        assertThatThrownBy(session::resumeRecording)
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("should transition status to STOPPED and update endedAt when stopping recording from RECORDING")
    void should_transition_to_stopped_from_recording() {
        // Arrange
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.startRecording(Instant.now());

        // Act
        session.stopRecording(Instant.now());

        // Assert
        assertThat(session.getStatus()).isEqualTo(SessionStatus.STOPPED);
        assertThat(session.getEndedAt()).isNotNull();
    }

    @Test
    @DisplayName("should transition status to STOPPED and update endedAt when stopping recording from PAUSED")
    void should_transition_to_stopped_from_paused() {
        // Arrange
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.startRecording(Instant.now());
        session.pauseRecording();

        // Act
        session.stopRecording(Instant.now());

        // Assert
        assertThat(session.getStatus()).isEqualTo(SessionStatus.STOPPED);
        assertThat(session.getEndedAt()).isNotNull();
    }

    @Test
    @DisplayName("should reject stopping if session is not in RECORDING or PAUSED")
    void should_reject_stop_if_invalid_status() {
        // Arrange
        DiscoverySession session = DiscoverySessionMother.draft().build();

        // Act & Assert
        assertThatThrownBy(() -> session.stopRecording(Instant.now()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("should append a transcript segment while RECORDING, advancing lastSequence")
    void should_record_segment_while_recording() {
        // Arrange
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.startRecording(Instant.now());

        // Act
        int first = session.recordSegment("Hola, necesito un login.", "0", 0, 1500, true);
        int second = session.recordSegment("Con Google también.", "1", 1500, 3000, true);

        // Assert
        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(2);
        assertThat(session.getLastSequence()).isEqualTo(2);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.RECORDING);
    }

    @Test
    @DisplayName("should reject recordSegment when not RECORDING")
    void should_reject_record_segment_when_not_recording() {
        // Arrange
        DiscoverySession session = DiscoverySessionMother.draft().build();

        // Act & Assert
        assertThatThrownBy(() -> session.recordSegment("texto", null, 0, 100, true))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).error())
                        .isEqualTo(DiscoveryError.INVALID_SESSION_STATUS));
    }

    @Test
    @DisplayName("should reject a blank segment text")
    void should_reject_blank_segment_text() {
        // Arrange
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.startRecording(Instant.now());

        // Act & Assert
        assertThatThrownBy(() -> session.recordSegment("   ", null, 0, 100, true))
                .isInstanceOf(DomainException.class);
    }
}
