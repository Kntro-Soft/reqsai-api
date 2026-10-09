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
    public static final int EVIDENCE_MAX = 500;
    public static final int CODE_NOTE_MAX = 1000;
    /**
     * Max length of a criterion {@code scenario} label, mirroring {@link AcceptanceCriterion}'s own
     * {@code SCENARIO_MAX}. An over-long LLM-emitted scenario is truncated here so accept never fails
     * the whole suggestion on the criterion's {@code maxLength} assertion.
     */
    private static final int SCENARIO_MAX = 200;

    /** The session the suggestion came from; {@code null} when it was raised from the assistant chat. */
    @Column(name = "session_id", columnDefinition = "uuid", updatable = false)
    private @Nullable UUID sessionId;

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
     *   <li>{@code UPDATE_STORY} — the new criteria the update adds to the target story (may be empty
     *       when only the story fields change).</li>
     *   <li>{@code CLARIFYING_QUESTION} — empty.</li>
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

    // ── Evidence and code insight (code-aware copilot) ───────────────────────────

    /** Sequence of the session's transcript segment that holds {@link #evidenceQuote}; null when unknown. */
    @Column(name = "evidence_sequence")
    private @Nullable Integer evidenceSequence;

    /** The verbatim fragment of the conversation the suggestion is based on. */
    @Column(name = "evidence_quote", length = EVIDENCE_MAX)
    private @Nullable String evidenceQuote;

    /** What the client's connected code says about it: already built, or in conflict with a rule. */
    @Enumerated(EnumType.STRING)
    @Column(name = "code_finding", length = ENUM_MAX)
    private @Nullable CodeFinding codeFinding;

    @Column(name = "code_note", length = CODE_NOTE_MAX)
    private @Nullable String codeNote;

    /** Modules of the client's code the suggestion relates to. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "code_refs", columnDefinition = "jsonb", nullable = false)
    private List<CodeReference> codeReferences = new ArrayList<>();

    protected Suggestion() {
        super();
    }

    /**
     * Attaches where the suggestion was said and what the client's code says about it, right after the
     * factory and before the save, so the creation event the live card is built from carries them too.
     */
    public void annotate(@Nullable Integer sequence, @Nullable String quote, @Nullable CodeFinding finding,
                         @Nullable String note, @Nullable List<CodeReference> references) {
        this.evidenceQuote = quote == null || quote.isBlank() ? null : clip(quote.strip(), EVIDENCE_MAX);
        this.evidenceSequence = this.evidenceQuote == null ? null : sequence;
        this.codeFinding = finding;
        this.codeNote = finding == null || note == null || note.isBlank() ? null : clip(note.strip(), CODE_NOTE_MAX);
        this.codeReferences = references == null ? new ArrayList<>() : new ArrayList<>(references);
        replaceEvent(SuggestionCreatedEvent.class, SuggestionCreatedEvent.of(this));
    }

    private static String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    // ── Factory methods ───────────────────────────────────────────────────────
    //
    // Every factory builds the whole aggregate first and registers its SuggestionCreatedEvent last:
    // the event is a snapshot (SuggestionCreatedEvent.of reads the fields once), and it is what the live
    // SUGGESTION_GENERATED message carries, so a field set after registering never reaches the analyst.

    /** Creates a NEW_STORY suggestion (no target, no draft criteria). */
    public static Suggestion newStory(@Nullable UUID sessionId, UUID projectId,
                                      String title, String role, String action, String benefit,
                                      Priority priority, @Nullable Integer storyPoints) {
        return newStory(sessionId, projectId, title, role, action, benefit, priority, storyPoints, List.of());
    }

    /** Creates a NEW_STORY suggestion carrying the LLM's proposed draft acceptance criteria. */
    public static Suggestion newStory(@Nullable UUID sessionId, UUID projectId,
                                      String title, String role, String action, String benefit,
                                      Priority priority, @Nullable Integer storyPoints,
                                      List<DraftCriterion> criteria) {
        Suggestion s = storyDraft(sessionId, projectId, SuggestionType.NEW_STORY,
                title, role, action, benefit, priority, storyPoints);
        s.draftCriteria = sanitizeCriteria(criteria);
        return s.withCreatedEvent();
    }

    /**
     * Creates an EDGE_CASE suggestion carrying a real Given/When/Then {@code criterion} to add to the
     * target story, plus the story fields kept only for the standalone-story fallback (when no target
     * can be resolved at accept time) and for duplicate detection.
     */
    public static Suggestion edgeCase(@Nullable UUID sessionId, UUID projectId,
                                      String title, String role, String action, String benefit,
                                      Priority priority, @Nullable Integer storyPoints,
                                      @Nullable String relatedTopic, @Nullable UUID targetStoryId,
                                      @Nullable DraftCriterion criterion) {
        Suggestion s = storyDraft(sessionId, projectId, SuggestionType.EDGE_CASE,
                title, role, action, benefit, priority, storyPoints);
        s.relatedTopic = relatedTopic;
        s.targetStoryId = targetStoryId;
        s.draftCriteria = sanitizeCriteria(criterion == null ? List.of() : List.of(criterion));
        return s.withCreatedEvent();
    }

    /** Creates an UPDATE_STORY suggestion for a near-duplicate (no criteria to add). */
    public static Suggestion updateStory(@Nullable UUID sessionId, UUID projectId,
                                         String title, String role, String action, String benefit,
                                         Priority priority, @Nullable Integer storyPoints,
                                         UUID targetStoryId) {
        return updateStory(sessionId, projectId, title, role, action, benefit, priority, storyPoints,
                targetStoryId, List.of());
    }

    /**
     * Creates an UPDATE_STORY suggestion: the proposed story fields for {@code targetStoryId} plus the
     * acceptance criteria the update adds to it (accepting appends the ones the story does not have yet).
     */
    public static Suggestion updateStory(@Nullable UUID sessionId, UUID projectId,
                                         String title, String role, String action, String benefit,
                                         Priority priority, @Nullable Integer storyPoints,
                                         UUID targetStoryId, List<DraftCriterion> criteria) {
        Suggestion s = storyDraft(sessionId, projectId, SuggestionType.UPDATE_STORY,
                title, role, action, benefit, priority, storyPoints);
        s.targetStoryId = Assert.notNull(targetStoryId, "targetStoryId");
        s.draftCriteria = sanitizeCriteria(criteria);
        return s.withCreatedEvent();
    }

    /** Creates a CLARIFYING_QUESTION suggestion. */
    public static Suggestion clarifyingQuestion(@Nullable UUID sessionId, UUID projectId, String question) {
        Suggestion s = pending(sessionId, projectId, SuggestionType.CLARIFYING_QUESTION);
        s.question = Assert.maxLength(Assert.notBlank(question, "question"), "question", QUESTION_MAX);
        return s.withCreatedEvent();
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

    /** A PENDING suggestion of {@code type}, with no payload and no event yet. */
    private static Suggestion pending(@Nullable UUID sessionId, UUID projectId, SuggestionType type) {
        Suggestion s = new Suggestion();
        s.sessionId = sessionId;
        s.projectId = Assert.notNull(projectId, "projectId");
        s.type = type;
        s.status = SuggestionStatus.PENDING;
        return s;
    }

    /** A PENDING story suggestion of {@code type} with its draft story fields, and no event yet. */
    private static Suggestion storyDraft(@Nullable UUID sessionId, UUID projectId, SuggestionType type,
                                         String title, String role, String action, String benefit,
                                         Priority priority, @Nullable Integer storyPoints) {
        Suggestion s = pending(sessionId, projectId, type);
        s.draftTitle = title;
        s.draftRole = role;
        s.draftAction = action;
        s.draftBenefit = benefit;
        s.draftPriority = priority;
        s.draftStoryPoints = storyPoints;
        return s;
    }

    /**
     * Registers the {@link SuggestionCreatedEvent} of this fully built suggestion. Called last by every
     * factory, since the event copies the fields at this moment.
     */
    private Suggestion withCreatedEvent() {
        registerEvent(SuggestionCreatedEvent.of(this));
        return this;
    }

    // ── Draft acceptance criteria (NEW_STORY, UPDATE_STORY, EDGE_CASE) ────────

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
     * criteria list, the criteria an UPDATE_STORY adds, or the single EDGE_CASE criterion.
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
