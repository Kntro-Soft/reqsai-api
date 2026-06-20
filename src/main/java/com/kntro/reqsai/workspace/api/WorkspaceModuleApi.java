package com.kntro.reqsai.workspace.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the Workspace bounded context, accessible to other Spring Modulith modules.
 * Returns plain-value snapshots — no JPA entities escape this boundary.
 *
 * <p>Implementations are package-private and registered as Spring beans; callers depend
 * only on this interface (ACL / anti-corruption layer pattern).
 */
public interface WorkspaceModuleApi {

    /**
     * Returns a read-only projection of a project with its technical profile, constraints,
     * and glossary terms. Returns {@link Optional#empty()} when the project does not exist
     * or belongs to a different tenant.
     */
    Optional<ProjectSnapshot> findProjectSnapshot(UUID projectId);

    /**
     * Returns a {@link ProjectSnapshot} containing only the {@code topK} most semantically
     * similar constraints and glossary terms to the given embedding vector (cosine similarity
     * via pgvector). Falls back to {@link #findProjectSnapshot} when no embeddings are stored yet.
     */
    Optional<ProjectSnapshot> findRelevantContext(UUID projectId, float[] queryEmbedding, int topK);
}
