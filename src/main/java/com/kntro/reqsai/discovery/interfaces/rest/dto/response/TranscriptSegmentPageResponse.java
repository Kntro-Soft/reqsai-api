package com.kntro.reqsai.discovery.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Cursor-paginated envelope for a discovery session's finalized transcript segments.
 *
 * <p>{@code segments} is ASCENDING by sequence (render order). The page itself is selected newest-first
 * via the {@code beforeSequence} cursor, so to load the previous (older) chunk the client passes the
 * {@code sequence} of the first item as the next {@code beforeSequence}. {@code hasMore} signals whether
 * an older chunk still exists; {@code totalFinalSegments} is the session's total finalized-segment count.
 */
@Schema(description = "Cursor page of finalized transcript segments, ascending by sequence")
public record TranscriptSegmentPageResponse(

        @Schema(description = "Segments in this page, ascending by sequence")
        List<TranscriptSegmentResponse> segments,

        @Schema(description = "Whether older finalized segments remain before this page", example = "true")
        boolean hasMore,

        @Schema(description = "Total finalized segments in the session across all pages", example = "1240")
        long totalFinalSegments
) {
}
