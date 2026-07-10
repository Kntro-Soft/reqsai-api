package com.kntro.reqsai.workspace.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Public ACL interface of the Workspace bounded context, accessible to other Spring Modulith modules.
 * Returns plain-value snapshots — no JPA entities escape this boundary.
 *
 * <p>Implementations are package-private and registered as Spring beans; callers depend
 * only on this interface (ACL / anti-corruption layer pattern).
 *
 * <p>Declare {@code allowedDependencies = "workspace::api"} in the consuming module's
 * {@code @ApplicationModule} annotation to make Spring Modulith enforce the boundary.
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

    /**
     * Whether {@code userId} may exercise {@code permission} (a {@code Permission} enum name, e.g.
     * {@code "SESSION_RUN"}) on the given project of the <em>currently bound tenant</em> (the JWT
     * {@code orgId} resolved by the authentication filter or WebSocket handshake). Org owners and
     * admins always pass; a regular member needs a project assignment whose role carries the
     * permission. Returns {@code false} when no tenant is bound or the organization is unknown.
     * <p>
     * This is the cross-context authorization entry point for modules whose routes carry no
     * {@code orgId} path variable (e.g. discovery's {@code /api/projects/{projectId}/...}).
     */
    boolean callerHasProjectPermission(UUID projectId, UUID userId, String permission);

    /**
     * Whether {@code userId} may access the given project of the <em>currently bound tenant</em>: org
     * owners/admins always may; a regular member needs an explicit project assignment. Returns
     * {@code false} when no tenant is bound or the organization is unknown. Coarse project-access gate
     * for routes carrying no {@code orgId} path variable (e.g. {@code /api/projects/{projectId}/...}).
     */
    boolean callerCanAccessProject(UUID projectId, UUID userId);

    /**
     * Resolves the roster display name of an active member by organization and user id. Used by
     * discovery's live-session presence to label participants without reaching into the workspace
     * member internals. Reads the {@code public.members} registry, so it does not require a tenant
     * schema to be bound. Returns {@link Optional#empty()} when the user is not an active member of
     * the organization.
     *
     * @param organizationId the tenant/organization id (the JWT {@code orgId})
     * @param userId         the authenticated user id (the JWT {@code sub})
     * @return the member's display name, or empty when there is no active membership
     */
    Optional<String> findMemberDisplayName(UUID organizationId, UUID userId);
}
