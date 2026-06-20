package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link TranscriptSegment} non-root entity (field invariants and state transitions).
 */
@DisplayName("Domain: TranscriptSegment")
class TranscriptSegmentTest {

    private final UUID sessionId = UUID.randomUUID();

    @Test
    @DisplayName("should create a valid final segment")
    void should_create_valid_segment() {
        TranscriptSegment segment = new TranscriptSegment(sessionId, 1, "0", "Hola mundo", 0, 1500, true);

        assertThat(segment.getId()).isNotNull();
        assertThat(segment.getSessionId()).isEqualTo(sessionId);
        assertThat(segment.getSequence()).isEqualTo(1);
        assertThat(segment.getSpeakerLabel()).isEqualTo("0");
        assertThat(segment.getText()).isEqualTo("Hola mundo");
        assertThat(segment.getStartMs()).isZero();
        assertThat(segment.getEndMs()).isEqualTo(1500);
        assertThat(segment.isFinal()).isTrue();
    }

    @Test
    @DisplayName("should allow a null speakerLabel (no diarization)")
    void should_allow_null_speaker_label() {
        TranscriptSegment segment = new TranscriptSegment(sessionId, 1, null, "texto", 0, 100, true);

        assertThat(segment.getSpeakerLabel()).isNull();
    }

    @Test
    @DisplayName("should create a partial segment with isFinal=false")
    void should_create_partial_segment() {
        TranscriptSegment segment = new TranscriptSegment(sessionId, 0, null, "Nece...", 0, 800, false);

        assertThat(segment.isFinal()).isFalse();
    }

    @Test
    @DisplayName("should finalize a partial segment")
    void should_finalize_partial_segment() {
        TranscriptSegment segment = new TranscriptSegment(sessionId, 0, null, "Nece...", 0, 800, false);

        segment.finalize("Necesito un login");

        assertThat(segment.getText()).isEqualTo("Necesito un login");
        assertThat(segment.isFinal()).isTrue();
    }

    @Test
    @DisplayName("should reject a non-positive sequence for final segments")
    void should_reject_non_positive_sequence() {
        assertThatThrownBy(() -> new TranscriptSegment(sessionId, 0, null, "texto", 0, 100, true))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("should reject a blank text")
    void should_reject_blank_text() {
        assertThatThrownBy(() -> new TranscriptSegment(sessionId, 1, null, "  ", 0, 100, true))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("should reject endMs before startMs")
    void should_reject_end_before_start() {
        assertThatThrownBy(() -> new TranscriptSegment(sessionId, 1, null, "texto", 500, 100, true))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("should reject a negative startMs")
    void should_reject_negative_start() {
        assertThatThrownBy(() -> new TranscriptSegment(sessionId, 1, null, "texto", -1, 100, true))
                .isInstanceOf(DomainException.class);
    }
}
