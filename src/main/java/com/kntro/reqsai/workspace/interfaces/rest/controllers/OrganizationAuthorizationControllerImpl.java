package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.workspace.application.command.ChangeMemberBasePermissionCommand;
import com.kntro.reqsai.workspace.application.handler.ChangeMemberBasePermissionCommandHandler;
import com.kntro.reqsai.workspace.application.handler.GetOrganizationAuthorizationQueryHandler;
import com.kntro.reqsai.workspace.application.handler.GetOrganizationQueryHandler;
import com.kntro.reqsai.workspace.application.query.GetOrganizationAuthorizationQuery;
import com.kntro.reqsai.workspace.application.query.GetOrganizationQuery;
import com.kntro.reqsai.workspace.application.result.OrganizationAuthorization;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateBasePermissionRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.BasePermissionResponse;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.OrganizationAuthorizationResponse;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.response.OrganizationAuthorizationResponseMapper;
import com.kntro.reqsai.workspace.interfaces.rest.swagger.OrganizationAuthorizationController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Implementation of the {@link OrganizationAuthorizationController} API contract. */
@RestController
@RequiredArgsConstructor
public class OrganizationAuthorizationControllerImpl implements OrganizationAuthorizationController {

    private final GetOrganizationQueryHandler getOrganization;
    private final ChangeMemberBasePermissionCommandHandler changeBasePermission;
    private final GetOrganizationAuthorizationQueryHandler getAuthorization;

    @Override
    @PreAuthorize("@authz.orgOwnerOrAdmin(#orgId, authentication)")
    public ResponseEntity<BasePermissionResponse> getBasePermission(UUID orgId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        Organization organization = getOrganization.handle(new GetOrganizationQuery(orgId, requestedBy));
        return ResponseEntity.ok(
                OrganizationAuthorizationResponseMapper.toResponse(organization.getMemberBasePermission()));
    }

    @Override
    @PreAuthorize("@authz.orgOwnerOrAdmin(#orgId, authentication)")
    public ResponseEntity<BasePermissionResponse> updateBasePermission(
            UUID orgId, UpdateBasePermissionRequest request, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        Organization organization = changeBasePermission.handle(
                new ChangeMemberBasePermissionCommand(orgId, request.basePermission(), requestedBy));
        return ResponseEntity.ok(
                OrganizationAuthorizationResponseMapper.toResponse(organization.getMemberBasePermission()));
    }

    @Override
    @PreAuthorize("@authz.orgMember(#orgId, authentication)")
    public ResponseEntity<OrganizationAuthorizationResponse> getMyAuthorization(
            UUID orgId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        OrganizationAuthorization authorization = getAuthorization.handle(
                new GetOrganizationAuthorizationQuery(orgId, requestedBy));
        return ResponseEntity.ok(OrganizationAuthorizationResponseMapper.toResponse(authorization));
    }
}
