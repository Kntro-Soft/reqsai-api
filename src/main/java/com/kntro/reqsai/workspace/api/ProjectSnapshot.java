package com.kntro.reqsai.workspace.api;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Read-only projection of a project exposed to other bounded contexts via {@link WorkspaceModuleApi}.
 * Contains only the text fields that the LLM prompt context needs — no internal JPA entities,
 * no embeddings, no IDs for child objects.
 */
public record ProjectSnapshot(
        UUID projectId,
        String name,
        @Nullable String description,
        List<String> programmingLanguages,
        List<String> frameworks,
        List<String> clientPlatforms,
        List<String> databases,
        String architecture,
        String domain,
        List<String> constraints,
        List<GlossaryTermSnapshot> glossaryTerms
) {}
