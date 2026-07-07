/**
 * Named interface of the Discovery module — the only types other modules may import.
 * <p>
 * Exposes {@link com.kntro.reqsai.discovery.api.DiscoveryStoryReadPort} and its read-only value
 * records ({@link com.kntro.reqsai.discovery.api.StoryView},
 * {@link com.kntro.reqsai.discovery.api.AcceptanceCriterionView}) so other modules (e.g. the
 * {@code gateway}) can read user stories to push them to external trackers, and
 * {@link com.kntro.reqsai.discovery.api.DiscoveryStoryWritePort} (with
 * {@link com.kntro.reqsai.discovery.api.ExternalIssueInput},
 * {@link com.kntro.reqsai.discovery.api.ImportedStory} and
 * {@link com.kntro.reqsai.discovery.api.StoryDuplicateCheck}) so the {@code gateway} can import external
 * issues as stories — Discovery owns the LLM transformation and reuses its create/dedup use case behind
 * the port. No JPA entities cross this boundary.
 * <p>
 * Declare {@code allowedDependencies = "discovery::api"} in the consuming module's
 * {@code @ApplicationModule} annotation to make Spring Modulith enforce the boundary.
 */
@org.springframework.modulith.NamedInterface("api")
package com.kntro.reqsai.discovery.api;
