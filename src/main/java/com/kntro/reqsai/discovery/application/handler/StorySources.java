package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.StorySourceFragment;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Where a story comes from in the meetings (US72).
 *
 * @param storyId   the story
 * @param origin    {@link Origin#SESSION} when the story has fragments of a meeting, {@link Origin#UNAVAILABLE}
 *                  when it was generated in a session but no fragment was kept (a story from before fragments
 *                  were stored), {@link Origin#MANUAL} when it does not come from a session
 * @param sessionId the session the story was generated in; only set for {@code UNAVAILABLE}
 * @param session   that session when it still exists; only set for {@code UNAVAILABLE}
 * @param fragments the fragments, oldest first; empty unless {@code SESSION}
 */
public record StorySources(UUID storyId, Origin origin, @Nullable UUID sessionId, @Nullable DiscoverySession session,
                           List<Fragment> fragments) {

    public enum Origin {
        SESSION, UNAVAILABLE, MANUAL
    }

    /**
     * One fragment, with the transcript it was found in.
     *
     * @param source      the stored fragment: session, quote and where it starts
     * @param session     the session it was said in, when it still exists
     * @param segments    the final segments the quote runs across, in order; empty when it was not located
     * @param speakerName who said it (the name the analyst gave, else "Hablante N"), when the transcript has speakers
     */
    public record Fragment(StorySourceFragment source, @Nullable DiscoverySession session,
                           List<TranscriptSegment> segments, @Nullable String speakerName) {
    }
}
