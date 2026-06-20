package com.kntro.reqsai.workspace.interfaces.rest.mappers.request;

import com.kntro.reqsai.workspace.application.command.AddGlossaryTermCommand;
import com.kntro.reqsai.workspace.application.command.DeleteGlossaryTermCommand;
import com.kntro.reqsai.workspace.application.command.UpdateGlossaryTermCommand;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.AddGlossaryTermRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateGlossaryTermRequest;

import java.util.UUID;

public final class GlossaryRequestMapper {

    private GlossaryRequestMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static AddGlossaryTermCommand toCommand(UUID organizationId, UUID projectId, AddGlossaryTermRequest request, UUID requestedBy) {
        return new AddGlossaryTermCommand(
                organizationId,
                projectId,
                request.term(),
                request.definition(),
                requestedBy
        );
    }

    public static UpdateGlossaryTermCommand toCommand(UUID organizationId, UUID projectId, UUID termId, UpdateGlossaryTermRequest request, UUID requestedBy) {
        return new UpdateGlossaryTermCommand(
                organizationId,
                projectId,
                termId,
                request.term(),
                request.definition(),
                requestedBy
        );
    }

    public static DeleteGlossaryTermCommand toDeleteCommand(UUID organizationId, UUID projectId, UUID termId, UUID requestedBy) {
        return new DeleteGlossaryTermCommand(
                organizationId,
                projectId,
                termId,
                requestedBy
        );
    }
}
