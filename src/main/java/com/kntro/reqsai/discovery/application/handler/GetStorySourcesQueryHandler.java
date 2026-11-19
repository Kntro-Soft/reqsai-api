package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.TranscriptSegmentRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.application.query.GetStorySourcesQuery;
import com.kntro.reqsai.discovery.application.service.QuoteLocator;
import com.kntro.reqsai.discovery.application.service.SessionSpeakerService;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.SpeakerRoster;
import com.kntro.reqsai.discovery.domain.model.StorySourceFragment;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Shows where a story comes from in the meetings (US72): the fragments of the transcript it was generated or
 * accepted from, with the moment of the meeting each one was said and who said it.
 *
 * <p>The story keeps the session, the segment where each quote starts and the quote; this handler reads the
 * segments the quote runs across, so the moment (and the text of the transcript) follows the transcript.
 * A story without fragments is not an error: it is {@code MANUAL} when it has no session, and
 * {@code UNAVAILABLE} when it was generated in a session before fragments were stored (the session is still
 * reported). A fragment whose segment is gone still shows its quote, without a moment.
 */
@Component
@RequiredArgsConstructor
public class GetStorySourcesQueryHandler {

    private final UserStoryRepository stories;
    private final DiscoverySessionRepository sessions;
    private final TranscriptSegmentRepository segments;
    private final SessionSpeakerService speakers;

    @Transactional(readOnly = true)
    public StorySources handle(GetStorySourcesQuery query) {
        UserStory story = stories.findByIdAndProjectId(query.storyId(), query.projectId())
                .orElseThrow(() -> DiscoveryExceptions.userStoryNotFound(query.storyId()));

        List<StorySourceFragment> stored = story.getSourceFragments();
        if (stored.isEmpty()) {
            UUID sessionId = story.getSessionId();
            if (sessionId == null) {
                return new StorySources(story.getId(), StorySources.Origin.MANUAL, null, null, List.of());
            }
            return new StorySources(story.getId(), StorySources.Origin.UNAVAILABLE, sessionId,
                    sessions.findById(sessionId).orElse(null), List.of());
        }

        Map<UUID, Optional<DiscoverySession>> sessionsById = new HashMap<>();
        Map<UUID, SpeakerRoster> rosters = new HashMap<>();
        List<StorySources.Fragment> fragments = stored.stream().map(source -> {
            UUID sessionId = source.getSessionId();
            Optional<DiscoverySession> session = sessionsById.computeIfAbsent(sessionId, sessions::findById);
            List<TranscriptSegment> found = segmentsOf(source);
            String speaker = found.isEmpty() ? null : speakerOf(found.getFirst(),
                    rosters.computeIfAbsent(sessionId, speakers::rosterOf));
            return new StorySources.Fragment(source, session.orElse(null), found, speaker);
        }).toList();
        return new StorySources(story.getId(), StorySources.Origin.SESSION, null, null, fragments);
    }

    /**
     * The final segments the quote runs across, from the one where it starts. Looked for in the few segments
     * after the start; when the quote cannot be placed there, only the start segment is taken. Empty when the
     * fragment has no segment or the segment no longer exists.
     */
    private List<TranscriptSegment> segmentsOf(StorySourceFragment source) {
        Integer start = source.getSequence();
        if (start == null) {
            return List.of();
        }
        List<TranscriptSegment> window = segments.findFinalBySessionIdBetween(
                source.getSessionId(), start, start + QuoteLocator.MAX_SPAN - 1);
        if (window.isEmpty() || window.getFirst().getSequence() != start) {
            return List.of();
        }
        QuoteLocator.Span span = QuoteLocator.span(source.getQuote(), window);
        int end = span != null && span.startSequence() == start ? span.endSequence() : start;
        return window.stream().filter(s -> s.getSequence() <= end).toList();
    }

    private static @Nullable String speakerOf(TranscriptSegment segment, SpeakerRoster roster) {
        return roster.find(segment.getSpeakerLabel()).map(SpeakerRoster.Speaker::name).orElse(null);
    }
}
