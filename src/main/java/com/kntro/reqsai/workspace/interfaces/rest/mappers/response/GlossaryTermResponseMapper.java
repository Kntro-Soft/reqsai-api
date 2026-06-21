package com.kntro.reqsai.workspace.interfaces.rest.mappers.response;

import com.kntro.reqsai.workspace.domain.model.GlossaryTerm;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.GlossaryTermResponse;

public final class GlossaryTermResponseMapper {

    private GlossaryTermResponseMapper() {}

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
