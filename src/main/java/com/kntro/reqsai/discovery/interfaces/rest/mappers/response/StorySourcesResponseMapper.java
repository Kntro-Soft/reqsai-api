package com.kntro.reqsai.discovery.interfaces.rest.mappers.response;

import com.kntro.reqsai.discovery.application.handler.StorySources;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.StorySourceFragment;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.StorySourceFragmentResponse;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.StorySourcesResponse;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps {@link StorySources} to a {@link StorySourcesResponse}. The moment of a fragment is the start of its
 * first segment and the end of its last; {@code occurredAt} anchors the start to the session, like the
 * transcript segments do (see {@link TranscriptSegmentResponseMapper}).
 */
public final class StorySourcesResponseMapper {

    private StorySourcesResponseMapper() {
    }

    public static StorySourcesResponse toResponse(StorySources sources) {
        DiscoverySession session = sources.session();
        return new StorySourcesResponse(
                sources.storyId(),
                sources.origin().name(),
                sources.sessionId(),
                session == null ? null : session.getTitle(),
                session == null ? null : startOf(session),
                sources.fragments().stream().map(StorySourcesResponseMapper::toResponse).toList());
    }

    private static StorySourceFragmentResponse toResponse(StorySources.Fragment fragment) {
        StorySourceFragment source = fragment.source();
        DiscoverySession session = fragment.session();
        List<TranscriptSegment> segments = fragment.segments();
        boolean located = !segments.isEmpty();
        TranscriptSegment first = located ? segments.getFirst() : null;
        TranscriptSegment last = located ? segments.getLast() : null;
        return new StorySourceFragmentResponse(
                source.getId(),
                source.getSessionId(),
                session == null ? null : session.getTitle(),
                session == null ? null : startOf(session),
                source.getQuote(),
                located ? segments.stream().map(TranscriptSegment::getText).collect(Collectors.joining(" ")) : null,
                located ? first.getSequence() : null,
                located ? last.getSequence() : null,
                located ? first.getStartMs() : null,
                located ? last.getEndMs() : null,
                located && session != null ? startOf(session).plusMillis(first.getStartMs()) : null,
                fragment.speakerName());
    }

    private static Instant startOf(DiscoverySession session) {
        return session.getStartedAt() != null ? session.getStartedAt() : session.getCreatedAt();
    }
}
