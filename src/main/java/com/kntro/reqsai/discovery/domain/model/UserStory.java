package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.discovery.domain.event.UserStoryCreatedEvent;
import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Aggregate root of a user story. This slice covers <strong>manual creation</strong> (a user uploads an
 * existing story): it starts in {@code DRAFT}. Stories generated from a discovery session set
 * {@code sessionId}; manually created ones leave it {@code null}. Acceptance criteria,
 * the duplicate-detection embedding, and the external tracker reference to arrive with their own use cases.
 */
@Entity
@Table(name = "user_stories")
@Getter
public class UserStory extends AggregateRoot {

    private static final int TITLE_MAX = 200;
    private static final int FIELD_MAX = 500;
    public static final int EMBEDDING_DIMENSIONS = 768;
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
    @Column(name = "priority", nullable = false, length = 16)
    private Priority priority;

    @Column(name = "story_points")
    private @Nullable Integer storyPoints;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private StoryStatus status;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = EMBEDDING_DIMENSIONS)
    @Column(name = "embedding")
    private float @Nullable [] embedding;


    protected UserStory() {
        super();
    }

    /** Manual creation: no originating session; starts in {@code DRAFT}. */
    public UserStory(UUID projectId, String title, String role, String action, String benefit, Priority priority, @Nullable Integer storyPoints) {
        super();
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
        registerEvent(UserStoryCreatedEvent.of(getId(), projectId));
    }

    /**
     * Canonical single-string representation fed to the embedding model.
     * Combines all semantic fields so similar stories yield similar vectors regardless of phrasing.
     */
    public String toCanonicalText() {
        return "%s. As %s, I want to %s, so that %s.".formatted(title, role, action, benefit);
    }

    /**
     * Attaches the duplicate-detection embedding once it has been computed by the embedding port.
     * Vector length must match {@link #EMBEDDING_DIMENSIONS}.
     */
    public void assignEmbedding(float[] embedding) {
        Assert.notNull(embedding, "embedding");
        Assert.isTrue(embedding.length == EMBEDDING_DIMENSIONS, "embedding", "must have " + EMBEDDING_DIMENSIONS + " dimensions");
        this.embedding = embedding;
    }
}
