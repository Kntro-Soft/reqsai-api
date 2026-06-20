package com.kntro.reqsai.workspace.interfaces.rest.mappers.request;

import com.kntro.reqsai.workspace.application.command.AddGlossaryTermCommand;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.AddGlossaryTermRequest;

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
}
