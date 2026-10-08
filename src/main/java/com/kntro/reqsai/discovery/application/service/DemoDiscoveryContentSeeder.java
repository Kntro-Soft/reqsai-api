package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.ProjectDiscoveryDataRepository;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.port.TranscriptSegmentRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Replaces a demo project's discovery data with the sample content of {@link DemoDiscoveryContent}: wipes
 * every session, transcript segment, story, suggestion and assistant chat message of the project, then saves
 * the fresh sample aggregates. Runs in the currently bound tenant and joins the caller's transaction (the
 * workspace demo seeding), so the whole demo (re)seed is atomic.
 * <p>
 * Refuses while a session of the project is recording or paused ({@code SESSION_ALREADY_ACTIVE}), so a
 * restore never pulls a live meeting from under its participants.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DemoDiscoveryContentSeeder {

    private final DiscoverySessionRepository sessions;
    private final TranscriptSegmentRepository segments;
    private final UserStoryRepository stories;
    private final SuggestionRepository suggestions;
    private final ProjectDiscoveryDataRepository projectData;

    @Transactional
    public void reseed(UUID projectId) {
        sessions.findActiveByProjectId(projectId).ifPresent(active -> {
            throw DiscoveryExceptions.sessionAlreadyActive(projectId, active.getId());
        });

        projectData.deleteAllByProjectId(projectId);

        DemoDiscoveryContent.Content content = DemoDiscoveryContent.build(projectId);
        sessions.save(content.session());
        content.segments().forEach(segments::save);
        content.stories().forEach(stories::save);
        content.suggestions().forEach(suggestions::save);
        log.info("Demo discovery content seeded for project {}: session {}, {} stories, {} suggestions",
                projectId, content.session().getId(), content.stories().size(), content.suggestions().size());
    }
}
