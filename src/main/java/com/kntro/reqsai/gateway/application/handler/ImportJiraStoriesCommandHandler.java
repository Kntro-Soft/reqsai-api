package com.kntro.reqsai.gateway.application.handler;

import com.kntro.reqsai.gateway.application.command.ImportJiraStoriesCommand;
import com.kntro.reqsai.gateway.application.port.IntegrationProvider.RemoteIssue;
import com.kntro.reqsai.gateway.application.port.ProjectIntegrationTargetRepository;
import com.kntro.reqsai.gateway.application.result.BatchImportResult;
import com.kntro.reqsai.gateway.application.result.ImportStoryResult;
import com.kntro.reqsai.gateway.application.service.JiraImportService;
import com.kntro.reqsai.gateway.application.service.StoryPushService.PushContext;
import com.kntro.reqsai.gateway.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.gateway.domain.model.ProjectIntegrationTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pulls Jira issues from the project's configured target and creates them as user stories via the discovery
 * write port (which owns the LLM mapping + dedup). 409 ({@code INTEGRATION_TARGET_NOT_CONFIGURED}) when no
 * target exists. Per-issue failures are captured without aborting the batch; duplicates are counted as
 * {@code skipped}. When {@code issueKeys} is null/empty, all eligible issues are imported.
 */
@Component
@RequiredArgsConstructor
public class ImportJiraStoriesCommandHandler {

    private final ProjectIntegrationTargetRepository targets;
    private final JiraImportService importService;

    @Transactional
    public BatchImportResult handle(ImportJiraStoriesCommand command) {
        ProjectIntegrationTarget target = targets.findByProjectId(command.projectId())
                .orElseThrow(() -> IntegrationsExceptions.targetNotConfigured(command.projectId()));

        PushContext ctx = importService.contextFor(target);
        List<RemoteIssue> issues = importService.fetchIssues(ctx);

        Set<String> requested = command.issueKeys() == null ? Set.of() : Set.copyOf(command.issueKeys());
        List<ImportStoryResult> results = new ArrayList<>();
        for (RemoteIssue issue : issues) {
            if (!requested.isEmpty() && !requested.contains(issue.issueKey())) {
                continue;
            }
            results.add(importService.importIssue(command.projectId(), issue));
        }
        return BatchImportResult.of(results);
    }
}
