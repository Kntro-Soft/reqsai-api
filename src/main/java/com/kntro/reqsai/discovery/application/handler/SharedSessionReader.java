package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.SessionStatsRepository;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.port.TranscriptSegmentRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.application.service.ResolvedStoryStatusReader;
import com.kntro.reqsai.discovery.application.service.SessionSpeakerService;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.ShareLink;
import com.kntro.reqsai.discovery.domain.model.StoryStatus;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import com.kntro.reqsai.workspace.api.ProjectSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reads a shared session inside the link's tenant (bound by {@link SharedLinkGateway}). Merged stories stay
 * hidden; rejected ones show, so a reviewer can bring one back to draft.
 */
@Component
@RequiredArgsConstructor
public class SharedSessionReader {

    /** Latest transcript segments one link shows. */
    static final int MAX_SEGMENTS = 200;
    /** Upper bound on the stories one link shows. */
    static final int MAX_STORIES = 300;

    private final DiscoverySessionRepository sessions;
    private final SessionStatsRepository stats;
    private final TranscriptSegmentRepository segments;
    private final SessionSpeakerService speakers;
    private final SuggestionRepository suggestions;
    private final ResolvedStoryStatusReader storyStatuses;
    private final UserStoryRepository stories;
    private final WorkspaceModuleApi workspace;

    @Transactional(readOnly = true)
    public SharedSession read(ShareLink link) {
        DiscoverySession session = sessions.findById(link.getSessionId())
                .filter(found -> found.getProjectId().equals(link.getProjectId()))
                .orElseThrow(DiscoveryExceptions::shareLinkUnavailable);
        String projectName = workspace.findProjectSnapshot(link.getProjectId())
                .map(ProjectSnapshot::name)
                .orElseThrow(DiscoveryExceptions::shareLinkUnavailable);

        List<TranscriptSegment> latest = new java.util.ArrayList<>(
                segments.findFinalBySessionIdBefore(session.getId(), Integer.MAX_VALUE, MAX_SEGMENTS));
        java.util.Collections.reverse(latest);

        List<Suggestion> pending = suggestions.findAllBySessionIdAndStatus(session.getId(), SuggestionStatus.PENDING);
        storyStatuses.attach(session.getProjectId(), pending);

        List<UserStory> shown = stories.findAllBySessionId(session.getId(),
                        PageRequest.of(0, MAX_STORIES, Sort.by(Sort.Direction.ASC, "createdAt")))
                .stream()
                .filter(story -> story.getStatus() != StoryStatus.MERGED)
                .toList();

        return new SharedSession(link, projectName, session,
                stats.statsForSessions(List.of(session.getId()))
                        .getOrDefault(session.getId(), SessionStatsRepository.SessionStats.zero()),
                latest, segments.countFinalBySessionId(session.getId()),
                speakers.overviewOf(session.getId()), pending, shown);
    }

    /** True when the story was produced by the link's session. */
    @Transactional(readOnly = true)
    public boolean isSessionStory(ShareLink link, java.util.UUID storyId) {
        return stories.findByIdAndProjectId(storyId, link.getProjectId())
                .filter(story -> link.getSessionId().equals(story.getSessionId()))
                .isPresent();
    }
}
