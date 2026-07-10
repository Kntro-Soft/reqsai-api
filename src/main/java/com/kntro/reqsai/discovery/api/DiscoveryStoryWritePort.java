package com.kntro.reqsai.discovery.api;

/**
 * Public ACL interface of the Discovery bounded context for <strong>creating</strong> user stories from
 * external tracker issues, accessible to other Spring Modulith modules (the {@code gateway} Jira import).
 * The counterpart of {@link DiscoveryStoryReadPort}.
 *
 * <p>Discovery owns the transformation: an external issue (summary + plain-text description) is turned into
 * a well-formed story (role/action/benefit + acceptance criteria) by Discovery's existing LLM generation
 * when configured, and by a deterministic safe mapping otherwise — so the LLM never crosses the module
 * boundary. Creation reuses the existing {@code CreateUserStoryCommandHandler}, keeping the
 * similarity/duplicate detection identical to manual and AI-generated stories: an import that collides with
 * an existing story is reported as a {@link ImportedStory.Status#DUPLICATE} rather than created.
 *
 * <p>Implementations are package-private Spring beans; callers depend only on this interface. All writes
 * are tenant-scoped (schema resolved from the JWT {@code orgId}).
 */
public interface DiscoveryStoryWritePort {

    /**
     * Transforms the external issue into a story and creates it, reusing the standard dedup gate. Returns
     * {@link ImportedStory#created(java.util.UUID)} on success or {@link ImportedStory#duplicate} when the
     * transformed story is a near-duplicate of an existing project story (nothing is created in that case).
     */
    ImportedStory importFromExternalIssue(ExternalIssueInput input);

    /**
     * Checks — without creating — whether the external issue would map to a near-duplicate of an existing
     * project story, so the import preview can flag it. Returns {@link StoryDuplicateCheck#notDuplicate()}
     * when the embedding model is unavailable (no similarity signal to report).
     */
    StoryDuplicateCheck checkDuplicate(ExternalIssueInput input);
}
