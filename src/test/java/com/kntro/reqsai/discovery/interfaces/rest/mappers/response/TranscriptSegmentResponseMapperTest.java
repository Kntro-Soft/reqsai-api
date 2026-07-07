package com.kntro.reqsai.discovery.interfaces.rest.mappers.response;

import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.TranscriptSegmentResponse;
import com.kntro.reqsai.discovery.mothers.DiscoverySessionMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TranscriptSegmentResponseMapper}: the absolute {@code occurredAt} of a segment
 * is derived from the session's {@code startedAt} anchor plus the segment {@code startMs}, and the plain
 * fields are copied through.
 *
 * @see TranscriptSegmentResponseMapper
 */
@DisplayName("Interfaces: Transcript Segment Response Mapper")
class TranscriptSegmentResponseMapperTest {

    @Test
    @DisplayName("occurredAt = session.startedAt + startMs, and fields copy through")
    void should_derive_occurred_at_from_started_at() {
        DiscoverySession session = DiscoverySessionMother.draft().build();
        // A DRAFT session sets startedAt in its constructor.
        TranscriptSegment segment = new TranscriptSegment(session.getId(), 4, "A", "hola equipo", 1_500, 3_200, true);

        TranscriptSegmentResponse response = TranscriptSegmentResponseMapper.toResponse(segment, session);

        assertThat(response.sequence()).isEqualTo(4);
        assertThat(response.text()).isEqualTo("hola equipo");
        assertThat(response.speakerLabel()).isEqualTo("A");
        assertThat(response.startMs()).isEqualTo(1_500);
        assertThat(response.endMs()).isEqualTo(3_200);
        assertThat(response.occurredAt()).isEqualTo(session.getStartedAt().plusMillis(1_500));
    }

    @Test
    @DisplayName("null speakerLabel is passed through as null")
    void should_pass_through_null_speaker_label() {
        DiscoverySession session = DiscoverySessionMother.draft().build();
        TranscriptSegment segment = new TranscriptSegment(session.getId(), 1, null, "sin hablante", 0, 100, true);

        TranscriptSegmentResponse response = TranscriptSegmentResponseMapper.toResponse(segment, session);

        assertThat(response.speakerLabel()).isNull();
        assertThat(response.occurredAt()).isEqualTo(session.getStartedAt());
    }
}
