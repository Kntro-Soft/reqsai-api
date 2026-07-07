/**
 * Named interface of the Discovery module — the only types other modules may import.
 * <p>
 * Exposes {@link com.kntro.reqsai.discovery.api.DiscoveryStoryReadPort} and its read-only value
 * records ({@link com.kntro.reqsai.discovery.api.StoryView},
 * {@link com.kntro.reqsai.discovery.api.AcceptanceCriterionView}) so other modules (e.g.
 * {@code integrations}) can read user stories to push them to external trackers without reaching into
 * Discovery internals. No JPA entities cross this boundary.
 * <p>
 * Declare {@code allowedDependencies = "discovery::api"} in the consuming module's
 * {@code @ApplicationModule} annotation to make Spring Modulith enforce the boundary.
 */
@org.springframework.modulith.NamedInterface("api")
package com.kntro.reqsai.discovery.api;
