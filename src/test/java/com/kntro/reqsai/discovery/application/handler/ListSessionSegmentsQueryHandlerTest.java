package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.TranscriptSegmentRepository;
import com.kntro.reqsai.discovery.application.query.ListSessionSegmentsQuery;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.discovery.mothers.DiscoverySessionMother;
import com.kntro.reqsai.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ListSessionSegmentsQueryHandler}: cursor pagination over a session's finalized
 * transcript segments — newest-first selection, ascending render order, the {@code hasMore} signal, the
 * total count, and default/cap limit resolution.
 *
 * @see ListSessionSegmentsQueryHandler
 */
@DisplayName("Application: List Session Segments (cursor paging)")
@ExtendWith(MockitoExtension.class)
class ListSessionSegmentsQueryHandlerTest {

    @Mock
    private DiscoverySessionRepository sessions;
    @Mock
    private TranscriptSegmentRepository segments;
    @InjectMocks
    private ListSessionSegmentsQueryHandler handler;

    @Test
    @DisplayName("newest page: returns the limit newest finals ascending, hasMore when older remain")
    void should_return_newest_page_ascending_with_has_more() {
        DiscoverySession session = DiscoverySessionMother.draft().build();
        UUID sessionId = session.getId();
        when(sessions.findById(sessionId)).thenReturn(java.util.Optional.of(session));
        // Over-fetch is limit + 1 = 3; repo returns DESC. Total is 10 → older segments remain.
        when(segments.findFinalBySessionIdBefore(eq(sessionId), eq(Integer.MAX_VALUE), eq(3)))
                .thenReturn(descending(sessionId, 10, 9, 8));
        when(segments.countFinalBySessionId(sessionId)).thenReturn(10L);

        ListSessionSegmentsQueryHandler.Result result =
                handler.handle(new ListSessionSegmentsQuery(sessionId, null, 2));

        // Only 'limit' (2) items, ascending by sequence, extra over-fetched row dropped
        assertThat(result.segments()).extracting(TranscriptSegment::getSequence).containsExactly(9, 10);
        assertThat(result.hasMore()).isTrue();
        assertThat(result.totalFinalSegments()).isEqualTo(10L);
    }

    @Test
    @DisplayName("older page via beforeSequence: no more older segments → hasMore false")
    void should_page_older_via_before_sequence() {
        DiscoverySession session = DiscoverySessionMother.draft().build();
        UUID sessionId = session.getId();
        when(sessions.findById(sessionId)).thenReturn(java.util.Optional.of(session));
        // beforeSequence=3, limit=2 → over-fetch 3, but only 2 finals exist below the cursor → no more.
        when(segments.findFinalBySessionIdBefore(eq(sessionId), eq(3), eq(3)))
                .thenReturn(descending(sessionId, 2, 1));
        when(segments.countFinalBySessionId(sessionId)).thenReturn(4L);

        ListSessionSegmentsQueryHandler.Result result =
                handler.handle(new ListSessionSegmentsQuery(sessionId, 3, 2));

        assertThat(result.segments()).extracting(TranscriptSegment::getSequence).containsExactly(1, 2);
        assertThat(result.hasMore()).isFalse();
        assertThat(result.totalFinalSegments()).isEqualTo(4L);
    }

    @Test
    @DisplayName("null limit falls back to the default page size")
    void should_default_limit() {
        DiscoverySession session = DiscoverySessionMother.draft().build();
        UUID sessionId = session.getId();
        when(sessions.findById(sessionId)).thenReturn(java.util.Optional.of(session));
        when(segments.findFinalBySessionIdBefore(eq(sessionId), eq(Integer.MAX_VALUE), eq(ListSessionSegmentsQueryHandler.DEFAULT_LIMIT + 1)))
                .thenReturn(List.of());
        when(segments.countFinalBySessionId(sessionId)).thenReturn(0L);

        ListSessionSegmentsQueryHandler.Result result =
                handler.handle(new ListSessionSegmentsQuery(sessionId, null, null));

        assertThat(result.segments()).isEmpty();
        assertThat(result.hasMore()).isFalse();
    }

    @Test
    @DisplayName("limit above the cap is clamped to MAX_LIMIT")
    void should_clamp_limit_to_max() {
        DiscoverySession session = DiscoverySessionMother.draft().build();
        UUID sessionId = session.getId();
        when(sessions.findById(sessionId)).thenReturn(java.util.Optional.of(session));
        when(segments.findFinalBySessionIdBefore(eq(sessionId), eq(Integer.MAX_VALUE), eq(ListSessionSegmentsQueryHandler.MAX_LIMIT + 1)))
                .thenReturn(List.of());
        when(segments.countFinalBySessionId(sessionId)).thenReturn(0L);

        handler.handle(new ListSessionSegmentsQuery(sessionId, null, 5_000));
        // verified via the eq(MAX_LIMIT + 1) stub above matching
    }

    @Test
    @DisplayName("should throw when the session does not exist")
    void should_throw_when_session_missing() {
        UUID missing = UUID.randomUUID();
        when(sessions.findById(missing)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> handler.handle(new ListSessionSegmentsQuery(missing, null, null)))
                .isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(segments);
    }

    /** Builds DESC-ordered final segments for the given sequences (as the repo returns them). */
    private static List<TranscriptSegment> descending(UUID sessionId, int... sequences) {
        List<TranscriptSegment> list = new ArrayList<>();
        IntStream.of(sequences).forEach(seq ->
                list.add(new TranscriptSegment(sessionId, seq, "0", "seg " + seq, seq * 100L, seq * 100L + 50, true)));
        return list;
    }
}
