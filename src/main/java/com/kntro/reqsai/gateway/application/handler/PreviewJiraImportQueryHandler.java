package com.kntro.reqsai.gateway.application.handler;

import com.kntro.reqsai.discovery.api.StoryDuplicateCheck;
import com.kntro.reqsai.gateway.application.port.IntegrationProvider.RemoteIssue;
import com.kntro.reqsai.gateway.application.port.ProjectIntegrationTargetRepository;
import com.kntro.reqsai.gateway.application.query.PreviewJiraImportQuery;
import com.kntro.reqsai.gateway.application.result.ImportPreview;
import com.kntro.reqsai.gateway.application.result.ImportPreview.Candidate;
import com.kntro.reqsai.gateway.application.service.JiraImportService;
import com.kntro.reqsai.gateway.application.service.StoryPushService.PushContext;
import com.kntro.reqsai.gateway.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.gateway.domain.model.ProjectIntegrationTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Lists the candidate Jira issues eligible for import from the project's target and flags likely duplicates
 * via the discovery similarity path — WITHOUT creating anything. 409
 * ({@code INTEGRATION_TARGET_NOT_CONFIGURED}) when no target exists.
 */
@Component
@RequiredArgsConstructor
public class PreviewJiraImportQueryHandler {

    private final ProjectIntegrationTargetRepository targets;
    private final JiraImportService importService;

    @Transactional(readOnly = true)
    public ImportPreview handle(PreviewJiraImportQuery query) {
        ProjectIntegrationTarget target = targets.findByProjectId(query.projectId())
                .orElseThrow(() -> IntegrationsExceptions.targetNotConfigured(query.projectId()));

        PushContext ctx = importService.contextFor(target);
        List<RemoteIssue> issues = importService.fetchIssues(ctx);

        List<Candidate> candidates = issues.stream().map(issue -> {
            StoryDuplicateCheck dup = importService.checkDuplicate(query.projectId(), issue);
            return new Candidate(issue.issueKey(), issue.summary(), issue.issueType(),
                    dup.duplicate(), dup.existingStoryId());
        }).toList();

        return ImportPreview.of(candidates);
    }
}
