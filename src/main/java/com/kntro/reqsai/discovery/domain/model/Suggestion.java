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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
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
    /**
     * Max length of a criterion {@code scenario} label, mirroring {@link AcceptanceCriterion}'s own
     * {@code SCENARIO_MAX}. An over-long LLM-emitted scenario is truncated here so accept never fails
     * the whole suggestion on the criterion's {@code maxLength} assertion.
     */
    private static final int SCENARIO_MAX = 200;

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

    /**
     * Proposed structured acceptance criteria, carried through the review gate so acceptance can
     * create/extend a story with real {@link AcceptanceCriterion} rows (each a Given/When/Then).
     * Stored as JSONB (mirrors how {@link UserStory} maps its pgvector embedding with a Hibernate
     * JDBC type code).
     *
     * <ul>
     *   <li>{@code NEW_STORY} — the 2-4 criteria proposed for the new story.</li>
     *   <li>{@code EDGE_CASE} — exactly one entry: the boundary/exceptional criterion to add to the
     *       target story (accepted verbatim, no field twisting).</li>
     *   <li>{@code UPDATE_STORY} / {@code CLARIFYING_QUESTION} — empty.</li>
     * </ul>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_criteria", columnDefinition = "jsonb")
    private List<DraftCriterion> draftCriteria = new ArrayList<>();

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

    /** Creates a NEW_STORY suggestion (no target, no draft criteria). */
    public static Suggestion newStory(UUID sessionId, UUID projectId,
                                      String title, String role, String action, String benefit,
                                      Priority priority, @Nullable Integer storyPoints) {
        return newStory(sessionId, projectId, title, role, action, benefit, priority, storyPoints, List.of());
    }

    /** Creates a NEW_STORY suggestion carrying the LLM's proposed draft acceptance criteria. */
    public static Suggestion newStory(UUID sessionId, UUID projectId,
                                      String title, String role, String action, String benefit,
                                      Priority priority, @Nullable Integer storyPoints,
                                      List<DraftCriterion> criteria) {
        Suggestion s = newStory(sessionId, projectId, title, role, action, benefit, priority, storyPoints, null, null);
        s.draftCriteria = sanitizeCriteria(criteria);
        return s;
    }

    /**
     * Creates an EDGE_CASE suggestion carrying a real Given/When/Then {@code criterion} to add to the
     * target story, plus the story fields kept only for the standalone-story fallback (when no target
     * can be resolved at accept time) and for duplicate detection.
     */
    public static Suggestion edgeCase(UUID sessionId, UUID projectId,
                                      String title, String role, String action, String benefit,
                                      Priority priority, @Nullable Integer storyPoints,
                                      @Nullable String relatedTopic, @Nullable UUID targetStoryId,
                                      @Nullable DraftCriterion criterion) {
        Suggestion s = newStory(sessionId, projectId, title, role, action, benefit, priority, storyPoints, relatedTopic, targetStoryId);
        s.type = SuggestionType.EDGE_CASE;
        s.draftCriteria = sanitizeCriteria(criterion == null ? List.of() : List.of(criterion));
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

    // ── Draft acceptance criteria (NEW_STORY) ─────────────────────────────────

    /**
     * A proposed acceptance criterion in Gherkin form. {@code scenario} is an optional short label
     * (the LLM is asked to provide one in the transcript language, but it is dropped rather than
     * fabricated when omitted); {@code given}/{@code when}/{@code then} are required. Persisted as an
     * element of the {@code draft_criteria} JSONB column. The canonical constructor keeps Jackson
     * happy for JSON (de)serialization by Hibernate.
     */
    public record DraftCriterion(@Nullable String scenario, String given, String when, String then) {}

    /**
     * The structured draft acceptance criteria (empty when none), never null. Holds the NEW_STORY
     * criteria list or the single EDGE_CASE criterion.
     */
    public List<DraftCriterion> getDraftAcceptanceCriteria() {
        return draftCriteria == null ? List.of() : List.copyOf(draftCriteria);
    }

    /**
     * Replaces the draft acceptance criteria with the analyst-edited set on accept. Each is sanitized
     * (given/when/then required, blank scenario normalized to null); an entry missing any of the
     * three is dropped. Used by the accept handler when the request carries edited criteria.
     */
    public void replaceDraftCriteria(List<DraftCriterion> criteria) {
        this.draftCriteria = sanitizeCriteria(criteria);
    }

    /**
     * Keeps only criteria with all three of given/when/then present — a criterion missing any of them
     * could not build a valid {@link AcceptanceCriterion} on accept, so it is dropped rather than
     * fabricated. Strips fields, normalizes a blank scenario to null, and truncates an over-long
     * scenario to {@link #SCENARIO_MAX} so a long LLM label caps the criterion instead of failing the
     * whole accept on {@link AcceptanceCriterion}'s length assertion.
     */
    private static List<DraftCriterion> sanitizeCriteria(@Nullable List<DraftCriterion> criteria) {
        List<DraftCriterion> out = new ArrayList<>();
        if (criteria == null) {
            return out;
        }
        for (DraftCriterion c : criteria) {
            if (c == null || blank(c.given()) || blank(c.when()) || blank(c.then())) {
                continue;
            }
            String scenario = blank(c.scenario()) ? null : truncate(c.scenario().strip(), SCENARIO_MAX);
            out.add(new DraftCriterion(scenario, c.given().strip(), c.when().strip(), c.then().strip()));
        }
        return out;
    }

    /** Caps {@code value} at {@code max} characters (null-safe); shorter/blank values pass through. */
    private static @Nullable String truncate(@Nullable String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private static boolean blank(@Nullable String s) {
        return s == null || s.isBlank();
    }
}
