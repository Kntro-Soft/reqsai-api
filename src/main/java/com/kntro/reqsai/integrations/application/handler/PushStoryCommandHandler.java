package com.kntro.reqsai.integrations.application.handler;

import com.kntro.reqsai.discovery.api.DiscoveryStoryReadPort;
import com.kntro.reqsai.discovery.api.StoryView;
import com.kntro.reqsai.integrations.application.command.PushStoryCommand;
import com.kntro.reqsai.integrations.application.port.IntegrationProvider.PushedIssue;
import com.kntro.reqsai.integrations.application.port.ProjectIntegrationTargetRepository;
import com.kntro.reqsai.integrations.application.result.StoryPushResult;
import com.kntro.reqsai.integrations.application.service.StoryPushService;
import com.kntro.reqsai.integrations.application.service.StoryPushService.PushContext;
import com.kntro.reqsai.integrations.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.integrations.domain.model.ProjectIntegrationTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pushes a single project story to the project's configured Jira target. 409
 * ({@code INTEGRATION_TARGET_NOT_CONFIGURED}) when no target exists; 404 when the story is not in the
 * project; provider failures ({@code JIRA_*}) surface as infrastructure exceptions.
 */
@Component
@RequiredArgsConstructor
public class PushStoryCommandHandler {

    private final ProjectIntegrationTargetRepository targets;
    private final DiscoveryStoryReadPort stories;
    private final StoryPushService pushService;

    @Transactional(readOnly = true)
    public StoryPushResult handle(PushStoryCommand command) {
        ProjectIntegrationTarget target = targets.findByProjectId(command.projectId())
                .orElseThrow(() -> IntegrationsExceptions.targetNotConfigured(command.projectId()));

        StoryView story = stories.findStory(command.projectId(), command.storyId())
                .orElseThrow(() -> IntegrationsExceptions.storyNotFound(command.storyId()));

        PushContext ctx = pushService.contextFor(target);
        PushedIssue issue = pushService.push(ctx, story);
        return StoryPushResult.success(command.storyId(), issue.issueKey(), issue.issueUrl());
    }
}
