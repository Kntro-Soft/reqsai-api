package com.kntro.reqsai.overview.interfaces.rest.controllers;

import com.kntro.reqsai.overview.application.OrganizationOverviewService;
import com.kntro.reqsai.overview.application.ProjectOverviewService;
import com.kntro.reqsai.overview.interfaces.rest.dto.response.OrganizationOverviewResponse;
import com.kntro.reqsai.overview.interfaces.rest.dto.response.ProjectOverviewResponse;
import com.kntro.reqsai.overview.interfaces.rest.mappers.response.OverviewResponseMapper;
import com.kntro.reqsai.overview.interfaces.rest.swagger.OverviewController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Implementation of the {@link OverviewController} API contract. */
@RestController
@RequiredArgsConstructor
public class OverviewControllerImpl implements OverviewController {

    private final ProjectOverviewService projectOverview;
    private final OrganizationOverviewService organizationOverview;

    @Override
    @PreAuthorize("@authz.tenantMember(authentication)")
    public ResponseEntity<ProjectOverviewResponse> project(UUID projectId, Authentication authentication) {
        UUID callerId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(OverviewResponseMapper.toResponse(projectOverview.overview(projectId, callerId)));
    }

    @Override
    @PreAuthorize("@authz.orgMember(#organizationId, authentication)")
    public ResponseEntity<OrganizationOverviewResponse> organization(UUID organizationId,
            Authentication authentication) {
        UUID callerId = UUID.fromString(authentication.getName());
        return ResponseEntity
                .ok(OverviewResponseMapper.toResponse(organizationOverview.overview(organizationId, callerId)));
    }
}
