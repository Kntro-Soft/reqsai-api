package com.kntro.reqsai.overview.application.result;

import org.jspecify.annotations.Nullable;

/**
 * What a project has written down about the client's domain. A figure is null
 * when the caller may not read that part of the project.
 */
public record KnowledgeSummary(@Nullable Long glossaryTerms, @Nullable Long constraints, @Nullable Long documents,
        @Nullable Long assignedMembers) {
}
