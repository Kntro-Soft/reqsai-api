package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.workspace.application.handler.CreateOrganizationCommandHandler;
import com.kntro.reqsai.workspace.application.handler.GetOrganizationQueryHandler;
import com.kntro.reqsai.workspace.application.handler.ListOrganizationsQueryHandler;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateOrganizationRequest;
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

    private final CreateOrganizationCommandHandler createOrganization;
    private final ListOrganizationsQueryHandler listOrganizations;
    private final GetOrganizationQueryHandler getOrganization;

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
    public ResponseEntity<List<OrganizationResponse>> list(Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        List<OrganizationResponse> body = listOrganizations.handle(requestedBy).stream()
                .map(OrganizationResponseMapper::toResponse)
                .toList();
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<OrganizationResponse> get(UUID orgId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        Organization organization = getOrganization.handle(orgId, requestedBy);
        return ResponseEntity.ok(OrganizationResponseMapper.toResponse(organization));
    }
}
