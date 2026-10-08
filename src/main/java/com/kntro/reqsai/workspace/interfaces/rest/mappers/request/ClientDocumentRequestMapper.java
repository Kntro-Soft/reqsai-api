package com.kntro.reqsai.workspace.interfaces.rest.mappers.request;

import com.kntro.reqsai.workspace.application.command.ApplyClientDocumentCommand;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.ApplyClientDocumentRequest;

import java.util.List;
import java.util.UUID;

public final class ClientDocumentRequestMapper {

    private ClientDocumentRequestMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static ApplyClientDocumentCommand toCommand(
            UUID orgId, UUID projectId, UUID documentId, ApplyClientDocumentRequest request, UUID requestedBy) {
        List<ApplyClientDocumentCommand.GlossaryTermInput> terms = request.glossaryTerms() == null
                ? List.of()
                : request.glossaryTerms().stream()
                        .map(t -> new ApplyClientDocumentCommand.GlossaryTermInput(t.term(), t.definition()))
                        .toList();
        return new ApplyClientDocumentCommand(
                orgId,
                projectId,
                documentId,
                request.name(),
                request.documentType(),
                request.summary(),
                terms,
                request.constraints() == null ? List.of() : request.constraints(),
                requestedBy);
    }
}
