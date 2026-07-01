package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.workspace.application.handler.CreateOrganizationCommandHandler;
import com.kntro.reqsai.workspace.application.handler.DeleteOrganizationCommandHandler;
import com.kntro.reqsai.workspace.application.handler.GetOrganizationQueryHandler;
import com.kntro.reqsai.workspace.application.handler.ListOrganizationsQueryHandler;
import com.kntro.reqsai.workspace.application.handler.TransferOwnershipCommandHandler;
import com.kntro.reqsai.workspace.application.handler.UpdateOrganizationCommandHandler;
import com.kntro.reqsai.workspace.application.query.GetOrganizationQuery;
import com.kntro.reqsai.workspace.application.query.ListOrganizationsQuery;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateOrganizationRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.TransferOwnershipRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateOrganizationRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.OrganizationResponse;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.request.OrganizationRequestMapper;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.response.OrganizationResponseMapper;
import com.kntro.reqsai.workspace.interfaces.rest.swagger.OrganizationController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Implementation of the {@link OrganizationController} API contract. */
@RestController
@RequiredArgsConstructor
public class OrganizationControllerImpl implements OrganizationController {

    private final GetOrganizationQueryHandler getOrganization;
    private final ListOrganizationsQueryHandler listOrganizations;
    private final CreateOrganizationCommandHandler createOrganization;
    private final UpdateOrganizationCommandHandler updateOrganization;
    private final TransferOwnershipCommandHandler transferOwnership;
    private final DeleteOrganizationCommandHandler deleteOrganization;

    @Override
    public ResponseEntity<List<OrganizationResponse>> list(Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        List<OrganizationResponse> response = listOrganizations.handle(new ListOrganizationsQuery(requestedBy))
                .stream()
                .map(OrganizationResponseMapper::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<OrganizationResponse> getById(UUID orgId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        Organization organization = getOrganization.handle(new GetOrganizationQuery(orgId, requestedBy));
        return ResponseEntity.ok(OrganizationResponseMapper.toResponse(organization));
    }

    @Override
    public ResponseEntity<OrganizationResponse> create(CreateOrganizationRequest request, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        Organization organization = createOrganization.handle(OrganizationRequestMapper.toCommand(request, requestedBy));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(organization.getId())
                .toUri();
        return ResponseEntity
                .created(location)
                .body(OrganizationResponseMapper.toResponse(organization));
    }

    @Override
    public ResponseEntity<OrganizationResponse> update(UUID orgId, UpdateOrganizationRequest request, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        Organization organization = updateOrganization.handle(
                OrganizationRequestMapper.toCommand(orgId, request, requestedBy));
        return ResponseEntity.ok(OrganizationResponseMapper.toResponse(organization));
    }

    @Override
    public ResponseEntity<OrganizationResponse> transferOwnership(UUID orgId, TransferOwnershipRequest request, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        Organization organization = transferOwnership.handle(
                OrganizationRequestMapper.toTransferCommand(orgId, request, requestedBy));
        return ResponseEntity.ok(OrganizationResponseMapper.toResponse(organization));
    }

    @Override
    public ResponseEntity<Void> delete(UUID orgId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        deleteOrganization.handle(OrganizationRequestMapper.toDeleteCommand(orgId, requestedBy));
        return ResponseEntity.noContent().build();
    }
}
