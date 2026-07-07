package com.kntro.reqsai.gateway.interfaces.rest.mappers.response;

import com.kntro.reqsai.gateway.application.port.IntegrationProvider.RemoteIssueType;
import com.kntro.reqsai.gateway.application.port.IntegrationProvider.RemoteProject;
import com.kntro.reqsai.gateway.application.port.JiraOAuthPort.Site;
import com.kntro.reqsai.gateway.application.result.BatchPushResult;
import com.kntro.reqsai.gateway.application.result.ConnectionTestResult;
import com.kntro.reqsai.gateway.application.result.StoryPushResult;
import com.kntro.reqsai.gateway.domain.model.IntegrationConnection;
import com.kntro.reqsai.gateway.domain.model.ProjectIntegrationTarget;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.BatchPushResponse;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.ConnectionTestResponse;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.IntegrationConnectionResponse;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.JiraIssueTypeResponse;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.JiraOAuthSiteResponse;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.JiraProjectResponse;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.JiraPushResultResponse;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.ProjectJiraTargetResponse;

/** Maps integration domain/results to REST responses. Never emits the API token. */
public final class IntegrationResponseMapper {

    private IntegrationResponseMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static IntegrationConnectionResponse toResponse(IntegrationConnection c) {
        return new IntegrationConnectionResponse(
                c.getId(),
                c.getOrganizationId(),
                c.getProvider().name(),
                c.getCredentialType().name(),
                c.getSiteUrl(),
                c.getEmail(),
                c.getStatus().name(),
                c.getLastVerifiedAt(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }

    public static ConnectionTestResponse toResponse(ConnectionTestResult r) {
        return new ConnectionTestResponse(r.ok(), r.accountName());
    }

    public static JiraProjectResponse toResponse(RemoteProject p) {
        return new JiraProjectResponse(p.key(), p.name());
    }

    public static JiraIssueTypeResponse toResponse(RemoteIssueType t) {
        return new JiraIssueTypeResponse(t.id(), t.name());
    }

    public static JiraOAuthSiteResponse toResponse(Site s) {
        return new JiraOAuthSiteResponse(s.cloudId(), s.url(), s.name());
    }

    public static ProjectJiraTargetResponse toResponse(ProjectIntegrationTarget t) {
        return new ProjectJiraTargetResponse(
                t.getId(),
                t.getProjectId(),
                t.getConnectionId(),
                t.getJiraProjectKey(),
                t.getIssueTypeName(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }

    public static JiraPushResultResponse toResponse(StoryPushResult r) {
        return new JiraPushResultResponse(r.storyId(), r.jiraIssueKey(), r.jiraIssueUrl(), r.error());
    }

    public static BatchPushResponse toResponse(BatchPushResult r) {
        return new BatchPushResponse(
                r.results().stream().map(IntegrationResponseMapper::toResponse).toList(),
                r.pushed(),
                r.failed());
    }
}
