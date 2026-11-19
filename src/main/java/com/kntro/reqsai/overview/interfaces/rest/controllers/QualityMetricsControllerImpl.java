package com.kntro.reqsai.overview.interfaces.rest.controllers;

import com.kntro.reqsai.overview.application.ProjectQualityService;
import com.kntro.reqsai.overview.interfaces.rest.dto.response.QualityMetricsResponse;
import com.kntro.reqsai.overview.interfaces.rest.mappers.response.QualityMetricsResponseMapper;
import com.kntro.reqsai.overview.interfaces.rest.swagger.QualityMetricsController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** Implementation of the {@link QualityMetricsController} API contract. */
@RestController
@RequiredArgsConstructor
public class QualityMetricsControllerImpl implements QualityMetricsController {

    private final ProjectQualityService quality;

    @Override
    @PreAuthorize("@authz.tenantMember(authentication)")
    public ResponseEntity<QualityMetricsResponse> project(UUID projectId, LocalDate from, LocalDate to,
            Authentication authentication) {
        UUID callerId = UUID.fromString(authentication.getName());
        return ResponseEntity
                .ok(QualityMetricsResponseMapper.toResponse(quality.indicators(projectId, callerId, from, to)));
    }
}
