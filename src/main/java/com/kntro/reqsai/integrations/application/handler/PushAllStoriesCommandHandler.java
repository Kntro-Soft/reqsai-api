package com.kntro.reqsai.integrations.application.handler;

import com.kntro.reqsai.discovery.api.DiscoveryStoryReadPort;
import com.kntro.reqsai.discovery.api.StoryView;
import com.kntro.reqsai.integrations.application.command.PushAllStoriesCommand;
import com.kntro.reqsai.integrations.application.port.IntegrationProvider.PushedIssue;
import com.kntro.reqsai.integrations.application.port.ProjectIntegrationTargetRepository;
import com.kntro.reqsai.integrations.application.result.BatchPushResult;
import com.kntro.reqsai.integrations.application.result.StoryPushResult;
import com.kntro.reqsai.integrations.application.service.StoryPushService;
import com.kntro.reqsai.integrations.application.service.StoryPushService.PushContext;
import com.kntro.reqsai.integrations.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.integrations.domain.model.ProjectIntegrationTarget;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Pushes every story of a project to its Jira target, <strong>capturing per-story failures without
 * aborting the batch</strong>: a failed push records the error code and the loop continues. 409
 * ({@code INTEGRATION_TARGET_NOT_CONFIGURED}) when no target exists.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PushAllStoriesCommandHandler {

    private final ProjectIntegrationTargetRepository targets;
    private final DiscoveryStoryReadPort stories;
    private final StoryPushService pushService;

    @Transactional(readOnly = true)
    public BatchPushResult handle(PushAllStoriesCommand command) {
        ProjectIntegrationTarget target = targets.findByProjectId(command.projectId())
                .orElseThrow(() -> IntegrationsExceptions.targetNotConfigured(command.projectId()));

        PushContext ctx = pushService.contextFor(target);
        List<StoryView> all = stories.listStories(command.projectId());

        List<StoryPushResult> results = new ArrayList<>(all.size());
        for (StoryView story : all) {
            try {
                PushedIssue issue = pushService.push(ctx, story);
                results.add(StoryPushResult.success(story.storyId(), issue.issueKey(), issue.issueUrl()));
            } catch (DomainException e) {
                // Infrastructure/domain failure on one story must not abort the rest of the batch.
                log.warn("Push failed for story {} [{}]", story.storyId(), e.error().code());
                results.add(StoryPushResult.failure(story.storyId(), e.error().code()));
            }
        }
        return BatchPushResult.of(results);
    }
}
