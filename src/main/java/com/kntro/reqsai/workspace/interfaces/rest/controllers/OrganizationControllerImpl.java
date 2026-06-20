package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.workspace.application.handler.CreateOrganizationCommandHandler;
import com.kntro.reqsai.workspace.application.handler.UpdateOrganizationCommandHandler;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateOrganizationRequest;
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
import java.util.UUID;

/** Implementation of the {@link OrganizationController} API contract. */
@RestController
@RequiredArgsConstructor
public class OrganizationControllerImpl implements OrganizationController {

    private final CreateOrganizationCommandHandler createOrganization;
    private final UpdateOrganizationCommandHandler updateOrganization;

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
}
