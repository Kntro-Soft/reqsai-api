package com.kntro.reqsai.discovery.interfaces.rest.mappers.response;

import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.DiscoverySessionResponse;
import com.kntro.reqsai.discovery.mothers.DiscoverySessionMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DiscoverySessionResponseMapper#toResponse(DiscoverySession)}'s
 * {@code durationSeconds}: an uploaded recording reports its audio length, a live session the time
 * between start and stop.
 *
 * @see DiscoverySessionResponseMapper
 */
@DisplayName("Interfaces: Discovery Session Response Mapper")
class DiscoverySessionResponseMapperTest {

    @Test
    @DisplayName("an uploaded recording reports its audio length, not the time the upload took")
    void uploaded_recording_uses_audio_length() {
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.uploadTranscript("Buenos días, doctora.", 138_175);

        DiscoverySessionResponse response = DiscoverySessionResponseMapper.toResponse(session);

        assertThat(response.audioDurationMs()).isEqualTo(138_175);
        assertThat(response.durationSeconds()).isEqualTo(138L);
    }

    @Test
    @DisplayName("a live session is measured from start to stop")
    void live_session_uses_timestamps() {
        DiscoverySession session = DiscoverySessionMother.draft().build();
        Instant start = Instant.parse("2026-09-23T15:00:00Z");
        session.startRecording(start);
        session.stopRecording(start.plusSeconds(117));

        DiscoverySessionResponse response = DiscoverySessionResponseMapper.toResponse(session);

        assertThat(response.audioDurationMs()).isZero();
        assertThat(response.durationSeconds()).isEqualTo(117L);
    }

    @Test
    @DisplayName("a live session that has not stopped has no duration yet")
    void running_session_has_no_duration() {
        DiscoverySession session = DiscoverySessionMother.draft().build();
        session.startRecording(Instant.parse("2026-09-23T15:00:00Z"));

        assertThat(DiscoverySessionResponseMapper.toResponse(session).durationSeconds()).isNull();
    }
}
