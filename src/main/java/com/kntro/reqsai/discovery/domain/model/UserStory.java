package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.discovery.domain.event.UserStoryCreatedEvent;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.CascadeType;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root of a user story. Starts in {@code DRAFT}. Stories generated from a discovery session
 * set {@code sessionId}; manually created ones leave it {@code null}. Acceptance criteria and the
 * external tracker reference to arrive with their own use cases.
 */
@Entity
@Table(name = "user_stories")
@Getter
public class UserStory extends AggregateRoot {

    private static final int TITLE_MAX = 200;
    private static final int FIELD_MAX = 500;
    private static final int ENUM_MAX = 16;
    /**
     * Cosine similarity threshold above which two stories are considered near-duplicates.
     * Shared by every handler that creates or generates stories (manual, AI-generated, imported).
     */
    public static final double DUPLICATE_THRESHOLD = 0.85;

    @Column(name = "session_id", columnDefinition = "uuid")
    private @Nullable UUID sessionId;

    @Column(name = "project_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "title", nullable = false, length = TITLE_MAX)
    private String title;

    @Column(name = "role", nullable = false, length = FIELD_MAX)
    private String role;

    @Column(name = "action", nullable = false, length = FIELD_MAX)
    private String action;

    @Column(name = "benefit", nullable = false, length = FIELD_MAX)
    private String benefit;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = ENUM_MAX)
    private Priority priority;

    @Column(name = "story_points")
    private @Nullable Integer storyPoints;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = ENUM_MAX)
    private StoryStatus status;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = EmbeddingPort.DIMENSIONS)
    @Column(name = "embedding")
    private float @Nullable [] embedding;

    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<AcceptanceCriterion> acceptanceCriteria = new ArrayList<>();

    protected UserStory() {
        super();
    }

    /** Manual creation: no originating session; starts in {@code DRAFT}. */
    public UserStory(UUID projectId, String title, String role, String action, String benefit, Priority priority, @Nullable Integer storyPoints) {
        super();
        initFields(null, projectId, title, role, action, benefit, priority, storyPoints);
        registerEvent(UserStoryCreatedEvent.of(getId(), null, projectId, title, role, action, benefit, priority, storyPoints));
    }

    /** Constructor for AI-generated stories (with originating sessionId). */
    public UserStory(UUID sessionId, UUID projectId, String title, String role, String action, String benefit, Priority priority, @Nullable Integer storyPoints) {
        super();
        initFields(Assert.notNull(sessionId, "sessionId"), projectId, title, role, action, benefit, priority, storyPoints);
        registerEvent(UserStoryCreatedEvent.of(getId(), sessionId, projectId, title, role, action, benefit, priority, storyPoints));
    }

    private void initFields(@Nullable UUID sessionId, UUID projectId, String title, String role, String action, String benefit, Priority priority, @Nullable Integer storyPoints) {
        this.sessionId = sessionId;
        this.projectId = Assert.notNull(projectId, "projectId");
        this.title = Assert.maxLength(Assert.notBlank(title, "title"), "title", TITLE_MAX);
        this.role = Assert.maxLength(Assert.notBlank(role, "role"), "role", FIELD_MAX);
        this.action = Assert.maxLength(Assert.notBlank(action, "action"), "action", FIELD_MAX);
        this.benefit = Assert.maxLength(Assert.notBlank(benefit, "benefit"), "benefit", FIELD_MAX);
        this.priority = Assert.notNull(priority, "priority");
        if (storyPoints != null) {
            Assert.isTrue(storyPoints >= 0, "storyPoints", "must be >= 0");
        }
        this.storyPoints = storyPoints;
        this.status = StoryStatus.DRAFT;
    }

    /**
     * Canonical single-string representation fed to the embedding model.
     * Combines all semantic fields so similar stories yield similar vectors regardless of phrasing.
     */
    public String toCanonicalText() {
        return "%s. As %s, I want to %s, so that %s.".formatted(title, role, action, benefit);
    }

    /**
     * True, when this story is part of the vector index for similarity search — the embedding model
     * was available at creation time, the story was compared against the project's existing stories,
     * and no near-duplicate was found (a duplicate would have been rejected with 409).
     * False means the model was unavailable: no dedup check was performed, and the story is not
     * searchable by similarity until it is re-indexed.
     */
    public boolean isIndexed() {
        return embedding != null;
    }

    /**
     * Attaches the duplicate-detection embedding once it has been computed by the embedding port.
     * Vector length must match {@link EmbeddingPort#DIMENSIONS}.
     */
    public void assignEmbedding(float[] embedding) {
        Assert.notNull(embedding, "embedding");
        Assert.isTrue(embedding.length == EmbeddingPort.DIMENSIONS, "embedding", "must have " + EmbeddingPort.DIMENSIONS + " dimensions");
        this.embedding = embedding;
    }

    /** Returns an unmodifiable view of the acceptance criteria. */
    public List<AcceptanceCriterion> getAcceptanceCriteria() {
        return Collections.unmodifiableList(acceptanceCriteria);
    }

    /**
     * Adds a new acceptance criterion. {@code scenario} may be null.
     * @return the newly created criterion
     */
    public AcceptanceCriterion addAcceptanceCriterion(@Nullable String scenario, String given, String when, String then) {
        AcceptanceCriterion criterion = new AcceptanceCriterion(this, scenario, given, when, then);
        acceptanceCriteria.add(criterion);
        return criterion;
    }

    /**
     * Updates an existing criterion identified by {@code criterionId} and returns it.
     * Throws {@link com.kntro.reqsai.shared.domain.exception.EntityNotFoundException} if not found.
     */
    public AcceptanceCriterion updateAcceptanceCriterion(UUID criterionId, @Nullable String scenario, String given, String when, String then) {
        AcceptanceCriterion criterion = acceptanceCriteria.stream()
                .filter(c -> c.getId().equals(criterionId))
                .findFirst()
                .orElseThrow(() -> DiscoveryExceptions.acceptanceCriterionNotFound(criterionId));
        criterion.update(scenario, given, when, then);
        return criterion;
    }

    /**
     * Removes an existing criterion identified by {@code criterionId}.
     * {@code orphanRemoval = true} on the collection ensures JPA issues the DELETE automatically.
     * Throws {@link com.kntro.reqsai.shared.domain.exception.EntityNotFoundException} if not found.
     */
    public void removeAcceptanceCriterion(UUID criterionId) {
        boolean removed = acceptanceCriteria.removeIf(c -> c.getId().equals(criterionId));
        if (!removed) {
            throw DiscoveryExceptions.acceptanceCriterionNotFound(criterionId);
        }
    }
}
