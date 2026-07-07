package com.kntro.reqsai.integrations.interfaces.rest.mappers.request;

import com.kntro.reqsai.integrations.application.command.ConnectJiraCommand;
import com.kntro.reqsai.integrations.application.command.SaveProjectTargetCommand;
import com.kntro.reqsai.integrations.interfaces.rest.dto.request.ConnectJiraRequest;
import com.kntro.reqsai.integrations.interfaces.rest.dto.request.SaveProjectTargetRequest;

import java.util.UUID;

/** Maps integration REST requests to application commands. */
public final class IntegrationRequestMapper {

    private IntegrationRequestMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static ConnectJiraCommand toCommand(UUID orgId, ConnectJiraRequest request, UUID requestedBy) {
        return new ConnectJiraCommand(orgId, request.siteUrl(), request.email(), request.apiToken(), requestedBy);
    }

    public static SaveProjectTargetCommand toCommand(UUID projectId, SaveProjectTargetRequest request, UUID requestedBy) {
        return new SaveProjectTargetCommand(
                projectId, request.connectionId(), request.jiraProjectKey(), request.issueTypeName(), requestedBy);
    }
}
