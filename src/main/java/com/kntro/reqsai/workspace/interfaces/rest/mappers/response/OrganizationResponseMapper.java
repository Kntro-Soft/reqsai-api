package com.kntro.reqsai.workspace.interfaces.rest.mappers.response;

import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.valueobjects.GenerationSettings;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.OrganizationResponse;

/** Maps the {@link Organization} aggregate to its response DTO. */
public final class OrganizationResponseMapper {

    private OrganizationResponseMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static OrganizationResponse toResponse(Organization organization) {
        GenerationSettings settings = organization.getSettings();
        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getSlug().value(),
                organization.getStatus().name(),
                organization.getOwnerId(),
                settings.meetingLanguage().value(),
                settings.audioRetentionDays(),
                organization.getCreatedAt(),
                organization.getUpdatedAt());
    }
}
