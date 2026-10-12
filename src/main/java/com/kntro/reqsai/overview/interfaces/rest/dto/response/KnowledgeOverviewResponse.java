package com.kntro.reqsai.overview.interfaces.rest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@Schema(description = "What the project has written down about the client's domain; a figure is null when the "
        + "caller may not read that part")
public record KnowledgeOverviewResponse(@Nullable Long glossaryTerms, @Nullable Long constraints,
        @Nullable Long documents, @Nullable Long assignedMembers) {
}
