package com.kntro.reqsai.workspace.api;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Read-only projection of a {@code Project}, its related {@code Glossary} and the context summaries of
 * its active client documents, exposed by the Workspace module via {@link WorkspaceModuleApi}. Carries
 * only the text fields needed for LLM context enrichment in Discovery.
 *
 * <p>No JPA entities, no embedding vectors, no child IDs — purely for cross-BC text consumption.
 */
public record ProjectSnapshot(
        UUID projectId,
        String name,
        @Nullable String description,
        List<String> programmingLanguages,
        List<String> frameworks,
        List<String> clientPlatforms,
        List<String> databases,
        @Nullable String architecture,
        @Nullable String domain,
        List<String> constraints,
        List<GlossaryTermSnapshot> glossaryTerms,
        List<ProjectDocumentSnapshot> documents
) {

    public ProjectSnapshot {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }

    /** A snapshot without client documents. */
    public ProjectSnapshot(UUID projectId, String name, @Nullable String description,
                           List<String> programmingLanguages, List<String> frameworks,
                           List<String> clientPlatforms, List<String> databases,
                           @Nullable String architecture, @Nullable String domain,
                           List<String> constraints, List<GlossaryTermSnapshot> glossaryTerms) {
        this(projectId, name, description, programmingLanguages, frameworks, clientPlatforms, databases,
                architecture, domain, constraints, glossaryTerms, List.of());
    }
}
