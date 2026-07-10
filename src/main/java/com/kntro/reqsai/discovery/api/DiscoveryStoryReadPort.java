package com.kntro.reqsai.discovery.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public ACL interface of the Discovery bounded context for reading user stories, accessible to other
 * Spring Modulith modules. Returns plain-value {@link StoryView} snapshots — no JPA entities escape
 * this boundary. All reads are tenant-scoped (schema resolved from the JWT {@code orgId}).
 *
 * <p>Implementations are package-private and registered as Spring beans; callers depend only on this
 * interface (anti-corruption layer). Consumed by {@code integrations} to render stories into external
 * tracker issues.
 */
public interface DiscoveryStoryReadPort {

    /**
     * Returns a read-only projection of one story scoped to the given project, or
     * {@link Optional#empty()} when the story does not exist or belongs to a different project/tenant.
     */
    Optional<StoryView> findStory(UUID projectId, UUID storyId);

    /** Returns read-only projections of every story in the project (empty when the project has none). */
    List<StoryView> listStories(UUID projectId);
}
