package com.kntro.reqsai.gateway.application.service;

import com.kntro.reqsai.discovery.api.DiscoveryStoryWritePort;
import com.kntro.reqsai.discovery.api.ExternalIssueInput;
import com.kntro.reqsai.discovery.api.ImportedStory;
import com.kntro.reqsai.gateway.application.port.IntegrationProvider.RemoteIssue;
import com.kntro.reqsai.gateway.application.result.ImportStoryResult;
import com.kntro.reqsai.gateway.application.service.StoryPushService.PushContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Shared Jira-import mechanics used by both the import command handler and the preview query handler:
 * resolves the target's provider/credentials once (reusing {@link StoryPushService#contextFor}), fetches
 * the eligible issues from Jira, and — for the import path — maps each issue to a story via the discovery
 * {@link DiscoveryStoryWritePort} (which owns the LLM transformation + dedup). The connection/target model
 * is unchanged: import pulls from the same {@code project_integration_targets} row push writes to.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JiraImportService {

    private final StoryPushService pushService;
    private final DiscoveryStoryWritePort discoveryStories;

    /** Resolves the provider context (connection + credentials + project/issue-type) for the target. */
    public PushContext contextFor(com.kntro.reqsai.gateway.domain.model.ProjectIntegrationTarget target) {
        return pushService.contextFor(target);
    }

    /** Fetches every eligible Jira issue for the resolved target (all pages). */
    public java.util.List<RemoteIssue> fetchIssues(PushContext ctx) {
        return ctx.provider().searchImportableIssues(ctx.credentials(), ctx.projectKey(), ctx.issueTypeName());
    }

    /**
     * Imports one Jira issue into the project as a story via the discovery write port. A near-duplicate is
     * reported as {@link ImportStoryResult.Status#DUPLICATE} (skipped, nothing created). Any failure is
     * captured as {@link ImportStoryResult.Status#FAILED} with a token-free message so the batch continues.
     */
    public ImportStoryResult importIssue(UUID projectId, RemoteIssue issue) {
        try {
            ExternalIssueInput input = new ExternalIssueInput(
                    projectId, issue.summary(), issue.description(), null);
            ImportedStory outcome = discoveryStories.importFromExternalIssue(input);
            if (outcome.isDuplicate()) {
                return ImportStoryResult.duplicate(issue.issueKey());
            }
            return ImportStoryResult.imported(issue.issueKey(), outcome.storyId());
        } catch (RuntimeException e) {
            log.warn("Import failed for Jira issue {}: {}", issue.issueKey(), e.getMessage());
            return ImportStoryResult.failed(issue.issueKey(), e.getMessage());
        }
    }

    /** Checks whether a Jira issue would map to a near-duplicate, without creating anything (preview). */
    public com.kntro.reqsai.discovery.api.StoryDuplicateCheck checkDuplicate(UUID projectId, RemoteIssue issue) {
        return discoveryStories.checkDuplicate(new ExternalIssueInput(
                projectId, issue.summary(), issue.description(), null));
    }
}
