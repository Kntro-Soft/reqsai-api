package com.kntro.reqsai.workspace.interfaces.rest.mappers.request;

import com.kntro.reqsai.workspace.application.command.CreateOrganizationCommand;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateOrganizationRequest;

import java.util.UUID;

/** Maps inbound organization requests DTOs to application commands. */
public final class OrganizationRequestMapper {

    private OrganizationRequestMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static CreateOrganizationCommand toCommand(CreateOrganizationRequest request, UUID requestedBy) {
        return new CreateOrganizationCommand(
                request.name(),
                request.slug(),
                request.meetingLanguage(),
                requestedBy);
    }
}
