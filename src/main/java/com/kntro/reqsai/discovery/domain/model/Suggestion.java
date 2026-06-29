package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.discovery.domain.event.SuggestionAcceptedEvent;
import com.kntro.reqsai.discovery.domain.event.SuggestionCreatedEvent;
import com.kntro.reqsai.discovery.domain.event.SuggestionDismissedEvent;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Aggregate root for an AI-generated suggestion surfaced during a live discovery session.
 *
 * <p>A suggestion is a <em>pending proposal</em>: the analyst must explicitly accept or dismiss it
 * before anything is persisted to the backlog. This creates a human review gate between the AI
 * output and the {@link UserStory} aggregate.
 *
 * <p>The concrete payload depends on {@link SuggestionType}:
 * <ul>
 *   <li>{@code NEW_STORY} / {@code UPDATE_STORY} / {@code EDGE_CASE} — carry draft story fields
 *       ({@code draftTitle}, {@code draftRole}, etc.).</li>
 *   <li>{@code UPDATE_STORY} / {@code EDGE_CASE} — also carry {@code targetStoryId}, the existing
 *       story this suggestion modifies or extends.</li>
 *   <li>{@code CLARIFYING_QUESTION} — carries only {@code question}.</li>
 * </ul>
 *
 * <p>On {@link #accept}, a {@link SuggestionAcceptedEvent} is registered; the handler then
 * performs the actual story/criterion mutation and populates {@code resolvedStoryId}.
 */
@Entity
@Table(name = "suggestions")
@Getter
public class Suggestion extends AggregateRoot {

    private static final int TITLE_MAX = 200;
    private static final int FIELD_MAX = 500;
    private static final int QUESTION_MAX = 1000;
    private static final int ENUM_MAX = 32;

    @Column(name = "session_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "project_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = ENUM_MAX, updatable = false)
    private SuggestionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = ENUM_MAX)
    private SuggestionStatus status;

    // ── Draft story payload (NEW_STORY, UPDATE_STORY, EDGE_CASE) ──────────────

    @Column(name = "draft_title", length = TITLE_MAX)
    private @Nullable String draftTitle;

    @Column(name = "draft_role", length = FIELD_MAX)
    private @Nullable String draftRole;

    @Column(name = "draft_action", length = FIELD_MAX)
    private @Nullable String draftAction;

    @Column(name = "draft_benefit", length = FIELD_MAX)
    private @Nullable String draftBenefit;

    @Enumerated(EnumType.STRING)
    @Column(name = "draft_priority", length = ENUM_MAX)
    private @Nullable Priority draftPriority;

    @Column(name = "draft_story_points")
    private @Nullable Integer draftStoryPoints;

    // ── Related topic hint (EDGE_CASE — aids targetStoryId resolution) ────────

    @Column(name = "related_topic", length = FIELD_MAX)
    private @Nullable String relatedTopic;

    // ── Target (UPDATE_STORY, EDGE_CASE) ─────────────────────────────────────

    @Column(name = "target_story_id", columnDefinition = "uuid")
    private @Nullable UUID targetStoryId;

    // ── Clarifying question (CLARIFYING_QUESTION) ─────────────────────────────

    @Column(name = "question", length = QUESTION_MAX)
    private @Nullable String question;

    // ── Resolution (populated by the acceptance handler) ──────────────────────────

    @Column(name = "resolved_story_id", columnDefinition = "uuid")
    private @Nullable UUID resolvedStoryId;

    /** Cosine similarity (0..1) to the matched story when raised as a duplicate alert; null otherwise. */
    @Column(name = "similarity")
    private @Nullable Double similarity;

    protected Suggestion() {
        super();
    }

    // ── Factory methods ───────────────────────────────────────────────────────

    /** Creates a NEW_STORY or EDGE_CASE suggestion (no target). */
    public static Suggestion newStory(UUID sessionId, UUID projectId,
                                      String title, String role, String action, String benefit,
                                      Priority priority, @Nullable Integer storyPoints) {
        return newStory(sessionId, projectId, title, role, action, benefit, priority, storyPoints, null, null);
    }

    /** Creates an EDGE_CASE suggestion with a resolved target story. */
    public static Suggestion edgeCase(UUID sessionId, UUID projectId,
                                      String title, String role, String action, String benefit,
                                      Priority priority, @Nullable Integer storyPoints,
                                      @Nullable String relatedTopic, @Nullable UUID targetStoryId) {
        Suggestion s = newStory(sessionId, projectId, title, role, action, benefit, priority, storyPoints, relatedTopic, targetStoryId);
        s.type = SuggestionType.EDGE_CASE;
        return s;
    }

    /** Creates an UPDATE_STORY suggestion for a near-duplicate. */
    public static Suggestion updateStory(UUID sessionId, UUID projectId,
                                         String title, String role, String action, String benefit,
                                         Priority priority, @Nullable Integer storyPoints,
                                         UUID targetStoryId) {
        Suggestion s = new Suggestion();
        s.sessionId = Assert.notNull(sessionId, "sessionId");
        s.projectId = Assert.notNull(projectId, "projectId");
        s.type = SuggestionType.UPDATE_STORY;
        s.status = SuggestionStatus.PENDING;
        s.draftTitle = title;
        s.draftRole = role;
        s.draftAction = action;
        s.draftBenefit = benefit;
        s.draftPriority = priority;
        s.draftStoryPoints = storyPoints;
        s.targetStoryId = Assert.notNull(targetStoryId, "targetStoryId");
        s.registerEvent(SuggestionCreatedEvent.of(s));
        return s;
    }

    /** Creates a CLARIFYING_QUESTION suggestion. */
    public static Suggestion clarifyingQuestion(UUID sessionId, UUID projectId, String question) {
        Suggestion s = new Suggestion();
        s.sessionId = Assert.notNull(sessionId, "sessionId");
        s.projectId = Assert.notNull(projectId, "projectId");
        s.type = SuggestionType.CLARIFYING_QUESTION;
        s.status = SuggestionStatus.PENDING;
        s.question = Assert.maxLength(Assert.notBlank(question, "question"), "question", QUESTION_MAX);
        s.registerEvent(SuggestionCreatedEvent.of(s));
        return s;
    }

    // ── State transitions ─────────────────────────────────────────────────────

    /**
     * Accepts the suggestion. The caller must subsequently perform the actual backlog mutation
     * (create a story, update a story, add a criterion) and pass the resulting {@code storyId}.
     *
     * @param resolvedStoryId the story that was created or updated as a result; {@code null} for
     *                        CLARIFYING_QUESTION (no story produced)
     */
    public void accept(@Nullable UUID resolvedStoryId) {
        guardPending();
        this.status = SuggestionStatus.ACCEPTED;
        this.resolvedStoryId = resolvedStoryId;
        registerEvent(SuggestionAcceptedEvent.of(this));
    }

    /** Dismisses the suggestion without taking any backlog action. */
    public void dismiss() {
        guardPending();
        this.status = SuggestionStatus.DISMISSED;
        registerEvent(SuggestionDismissedEvent.of(this));
    }

    /** Records the similarity to the matched story (duplicate alert raised from batch extraction). */
    public void recordSimilarity(double value) {
        this.similarity = value;
    }

    private void guardPending() {
        if (status != SuggestionStatus.PENDING) {
            throw DiscoveryExceptions.suggestionAlreadyResolved(getId(), status);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static Suggestion newStory(UUID sessionId, UUID projectId,
                                       String title, String role, String action, String benefit,
                                       Priority priority, @Nullable Integer storyPoints,
                                       @Nullable String relatedTopic, @Nullable UUID targetStoryId) {
        Suggestion s = new Suggestion();
        s.sessionId = Assert.notNull(sessionId, "sessionId");
        s.projectId = Assert.notNull(projectId, "projectId");
        s.type = SuggestionType.NEW_STORY;
        s.status = SuggestionStatus.PENDING;
        s.draftTitle = title;
        s.draftRole = role;
        s.draftAction = action;
        s.draftBenefit = benefit;
        s.draftPriority = priority;
        s.draftStoryPoints = storyPoints;
        s.relatedTopic = relatedTopic;
        s.targetStoryId = targetStoryId;
        s.registerEvent(SuggestionCreatedEvent.of(s));
        return s;
    }
}
