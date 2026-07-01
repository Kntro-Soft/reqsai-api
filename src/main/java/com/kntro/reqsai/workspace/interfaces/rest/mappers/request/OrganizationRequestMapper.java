package com.kntro.reqsai.workspace.interfaces.rest.mappers.request;

import com.kntro.reqsai.workspace.application.command.CreateOrganizationCommand;
import com.kntro.reqsai.workspace.application.command.DeleteOrganizationCommand;
import com.kntro.reqsai.workspace.application.command.TransferOwnershipCommand;
import com.kntro.reqsai.workspace.application.command.UpdateOrganizationCommand;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateOrganizationRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.TransferOwnershipRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateOrganizationRequest;

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

    public static UpdateOrganizationCommand toCommand(UUID organizationId, UpdateOrganizationRequest request, UUID requestedBy) {
        return new UpdateOrganizationCommand(
                organizationId,
                request.name(),
                request.meetingLanguage(),
                request.audioRetentionDays(),
                requestedBy);
    }

    public static TransferOwnershipCommand toTransferCommand(UUID organizationId, TransferOwnershipRequest request, UUID requestedBy) {
        return new TransferOwnershipCommand(organizationId, request.newOwnerMemberId(), requestedBy);
    }

    public static DeleteOrganizationCommand toDeleteCommand(UUID organizationId, UUID requestedBy) {
        return new DeleteOrganizationCommand(organizationId, requestedBy);
    }
}
