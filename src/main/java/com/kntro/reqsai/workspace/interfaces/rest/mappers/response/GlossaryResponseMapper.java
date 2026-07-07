package com.kntro.reqsai.workspace.interfaces.rest.mappers.response;

import com.kntro.reqsai.workspace.domain.model.GlossaryTerm;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.GlossaryTermResponse;

public final class GlossaryResponseMapper {

    private GlossaryResponseMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static GlossaryTermResponse toResponse(GlossaryTerm term) {
        return new GlossaryTermResponse(
                term.getId(),
                term.getTerm(),
                term.getDefinition(),
                term.getAddedAt(),
                term.getCreatedAt(),
                term.getUpdatedAt()
        );
    }
}
