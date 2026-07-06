package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.TranscriptSegmentRepository;
import com.kntro.reqsai.discovery.application.query.ListSessionSegmentsQuery;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cursor-paginates a discovery session's finalized transcript segments, newest-first, so the frontend
 * can rebuild the chat timeline of a past (potentially hours-long) session in chunks — latest first,
 * older on scroll-up — without pulling thousands of segments in one payload.
 *
 * <p>Given {@code beforeSequence} (exclusive; absent = newest) it fetches the {@code limit} highest-
 * sequence finals below the cursor in DESCENDING order, then reverses them to ASCENDING for rendering.
 * {@code hasMore} tells the client whether an older page still remains. The session is returned so the
 * mapper can derive each segment's absolute {@code occurredAt}. Only {@code isFinal} segments count.
 */
@Component
@RequiredArgsConstructor
public class ListSessionSegmentsQueryHandler {

    /** Page size when the client does not specify a {@code limit}. */
    static final int DEFAULT_LIMIT = 50;

    /** Hard upper bound on {@code limit}, regardless of what the client asks for. */
    static final int MAX_LIMIT = 200;

    private final DiscoverySessionRepository sessions;
    private final TranscriptSegmentRepository segments;

    /**
     * A cursor page of finalized segments.
     *
     * @param session             the owning session (source of the {@code occurredAt} anchor)
     * @param segments            the page, ASCENDING by sequence
     * @param hasMore             whether older finalized segments remain before this page
     * @param totalFinalSegments  total finalized segments in the session (across all pages)
     */
    public record Result(DiscoverySession session, List<TranscriptSegment> segments,
                         boolean hasMore, long totalFinalSegments) {
    }

    @Transactional(readOnly = true)
    public Result handle(ListSessionSegmentsQuery query) {
        DiscoverySession session = sessions.findById(query.sessionId())
                .orElseThrow(() -> DiscoveryExceptions.sessionNotFound(query.sessionId()));

        int limit = resolveLimit(query.limit());
        int before = query.beforeSequence() != null ? query.beforeSequence() : Integer.MAX_VALUE;

        // Over-fetch one row to detect whether an older page remains without a second query.
        List<TranscriptSegment> descending = new ArrayList<>(
                segments.findFinalBySessionIdBefore(query.sessionId(), before, limit + 1));
        boolean hasMore = descending.size() > limit;
        if (hasMore) {
            descending = new ArrayList<>(descending.subList(0, limit));
        }
        long total = segments.countFinalBySessionId(query.sessionId());

        Collections.reverse(descending);
        return new Result(session, descending, hasMore, total);
    }

    private int resolveLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }
}
