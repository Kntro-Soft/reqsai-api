package com.kntro.reqsai.gateway.interfaces.rest.controllers;

import com.kntro.reqsai.gateway.application.handler.ConnectJiraCommandHandler;
import com.kntro.reqsai.gateway.application.handler.DeleteConnectionCommandHandler;
import com.kntro.reqsai.gateway.application.handler.ListConnectionsQueryHandler;
import com.kntro.reqsai.gateway.application.handler.ListJiraIssueTypesQueryHandler;
import com.kntro.reqsai.gateway.application.handler.ListJiraProjectsQueryHandler;
import com.kntro.reqsai.gateway.application.handler.TestConnectionQueryHandler;
import com.kntro.reqsai.gateway.application.command.DeleteConnectionCommand;
import com.kntro.reqsai.gateway.application.query.ListConnectionsQuery;
import com.kntro.reqsai.gateway.application.query.ListJiraIssueTypesQuery;
import com.kntro.reqsai.gateway.application.query.ListJiraProjectsQuery;
import com.kntro.reqsai.gateway.application.query.TestConnectionQuery;
import com.kntro.reqsai.gateway.domain.model.IntegrationConnection;
import com.kntro.reqsai.gateway.interfaces.rest.dto.request.ConnectJiraRequest;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.ConnectionTestResponse;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.IntegrationConnectionResponse;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.JiraIssueTypeResponse;
import com.kntro.reqsai.gateway.interfaces.rest.dto.response.JiraProjectResponse;
import com.kntro.reqsai.gateway.interfaces.rest.mappers.request.IntegrationRequestMapper;
import com.kntro.reqsai.gateway.interfaces.rest.mappers.response.IntegrationResponseMapper;
import com.kntro.reqsai.gateway.interfaces.rest.swagger.OrganizationIntegrationController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Organization-level integration endpoints. Administering an org-wide credential is an org-admin action,
 * so every method is gated by {@code @authz.orgOwnerOrAdmin} (ADR-0022).
 */
@RestController
@RequiredArgsConstructor
public class OrganizationIntegrationControllerImpl implements OrganizationIntegrationController {

    private final ListConnectionsQueryHandler listConnections;
    private final ConnectJiraCommandHandler connectJira;
    private final TestConnectionQueryHandler testConnection;
    private final DeleteConnectionCommandHandler deleteConnection;
    private final ListJiraProjectsQueryHandler listJiraProjects;
    private final ListJiraIssueTypesQueryHandler listJiraIssueTypes;

    @Override
    @PreAuthorize("@authz.orgOwnerOrAdmin(#orgId, authentication)")
    public ResponseEntity<List<IntegrationConnectionResponse>> listConnections(UUID orgId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        List<IntegrationConnectionResponse> body = listConnections.handle(new ListConnectionsQuery(orgId, requestedBy))
                .stream().map(IntegrationResponseMapper::toResponse).toList();
        return ResponseEntity.ok(body);
    }

    @Override
    @PreAuthorize("@authz.orgOwnerOrAdmin(#orgId, authentication)")
    public ResponseEntity<IntegrationConnectionResponse> connectJira(
            UUID orgId, ConnectJiraRequest request, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        IntegrationConnection connection = connectJira.handle(
                IntegrationRequestMapper.toCommand(orgId, request, requestedBy));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .replacePath("/api/organizations/{orgId}/integrations/{id}")
                .buildAndExpand(orgId, connection.getId())
                .toUri();
        return ResponseEntity.created(location).body(IntegrationResponseMapper.toResponse(connection));
    }

    @Override
    @PreAuthorize("@authz.orgOwnerOrAdmin(#orgId, authentication)")
    public ResponseEntity<ConnectionTestResponse> testConnection(UUID orgId, UUID connectionId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(IntegrationResponseMapper.toResponse(
                testConnection.handle(new TestConnectionQuery(orgId, connectionId, requestedBy))));
    }

    @Override
    @PreAuthorize("@authz.orgOwnerOrAdmin(#orgId, authentication)")
    public ResponseEntity<Void> deleteConnection(UUID orgId, UUID connectionId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        deleteConnection.handle(new DeleteConnectionCommand(orgId, connectionId, requestedBy));
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("@authz.orgOwnerOrAdmin(#orgId, authentication)")
    public ResponseEntity<List<JiraProjectResponse>> listJiraProjects(UUID orgId, UUID connectionId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        List<JiraProjectResponse> body = listJiraProjects.handle(new ListJiraProjectsQuery(orgId, connectionId, requestedBy))
                .stream().map(IntegrationResponseMapper::toResponse).toList();
        return ResponseEntity.ok(body);
    }

    @Override
    @PreAuthorize("@authz.orgOwnerOrAdmin(#orgId, authentication)")
    public ResponseEntity<List<JiraIssueTypeResponse>> listJiraIssueTypes(
            UUID orgId, UUID connectionId, String projectKey, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        List<JiraIssueTypeResponse> body = listJiraIssueTypes
                .handle(new ListJiraIssueTypesQuery(orgId, connectionId, projectKey, requestedBy))
                .stream().map(IntegrationResponseMapper::toResponse).toList();
        return ResponseEntity.ok(body);
    }
}
