package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Decides whether a fresh story draft repeats an earlier one, whether a NEW draft is the same requirement
 * as an accepted story, and whether an {@code UPDATE_STORY} proposal changes its target story at all.
 *
 * <h2>Why cosine similarity alone is not enough</h2>
 * Embedding similarity measures topic, not content. Requirements of one domain — booking a medical
 * appointment, the 10% penalty for cancelling it late, the doctor's e-mail notification — share the
 * actor, the nouns and the user-story scaffolding, so they can score above the dedup bar, while a
 * synonym paraphrase can score below it. Worse, a draft that repeats a story and adds one clause ("… and
 * pay it online") scores as high as a plain restatement (see {@code SuggestionDedupRealVectorsTest}). So
 * the embedding only says two drafts are <em>related</em>; what they say is compared word by word.
 *
 * <h2>Rules</h2>
 * <ul>
 *   <li>{@link #repeats} — silently dropping a draft loses a requirement for good, so it needs proof that
 *       the draft adds nothing: no number (digits or number words) the earlier draft lacks, no acceptance
 *       criterion it lacks when criteria are the draft's payload ({@code EDGE_CASE}, {@code UPDATE_STORY}),
 *       and no content word in its title, role or action the earlier draft does not use. Only then is a
 *       related draft (same title, cosine at or above {@code discovery.realtime.dedup-similarity-threshold},
 *       or linked by the model) a repeat. A reworded restatement is kept; the analyst dismisses it.</li>
 *   <li>{@link #sameRequirementAs} — turning a NEW draft into an update of an accepted story is visible
 *       and editable, so it accepts rewording: a similar embedding plus the same intent (near-identical
 *       title, or the same actor doing the same action).</li>
 * </ul>
 * Words are compared accent-folded, without Spanish/English function words, and cut to a short stem, so
 * "reservar"/"reservada" and "citas"/"cita" match. No extra model call is made.
 */
@Component
public class SuggestionDedupPolicy {

    /** Token overlap (Jaccard) at which two titles name the same thing. */
    static final double SAME_TITLE_OVERLAP = 0.75;
    /** Token overlap (Jaccard) at which two roles are the same actor (one containing the other also counts). */
    static final double SAME_ACTOR_OVERLAP = 0.5;
    /** Token overlap (Jaccard) at which two actions are the same action, given the same leading verb. */
    static final double SAME_ACTION_OVERLAP = 0.5;
    /** Token overlap (Jaccard) at which two Given/When/Then criteria state the same rule. */
    static final double SAME_CRITERION_OVERLAP = 0.8;
    /** Words are cut to this many characters, a crude stem ("reservar"/"reservada", "médico"/"médica"). */
    private static final int STEM_LENGTH = 5;

    private static final Set<String> STOP_WORDS = Set.of(
            // Spanish function words, plus the modal verbs drafts open with ("poder reservar", "quiero ver").
            "al", "algo", "algun", "alguna", "algunas", "alguno", "algunos", "ante", "antes", "aun", "cada",
            "como", "con", "contra", "cual", "cuales", "cuando", "del", "desde", "donde", "durante", "ella",
            "ellas", "ellos", "entre", "era", "esa", "esas", "ese", "eso", "esos", "esta", "estas", "este",
            "esto", "estos", "fue", "hay", "las", "les", "los", "mas", "mis", "mismo", "misma", "mucho", "muy",
            "nos", "nuestra", "nuestro", "nuestras", "nuestros", "otra", "otras", "otro", "otros", "para",
            "pero", "poco", "por", "porque", "que", "quien", "sea", "ser", "sin", "sobre", "sus", "tambien",
            "toda", "todas", "todo", "todos", "tus", "una", "unas", "uno", "unos", "poder", "pueda", "puedan",
            "puede", "pueden", "podria", "quiero", "quiere", "quieren", "querer", "debe", "deben", "deberia",
            "necesito", "necesita", "necesitan", "tener", "tiene", "tienen", "tenga", "haya", "estar", "esten",
            // English function words.
            "and", "are", "can", "could", "for", "from", "into", "its", "need", "needs", "our", "should",
            "that", "the", "their", "them", "they", "this", "want", "wants", "will", "with", "would", "able",
            "must", "have", "has", "been", "being", "was", "were", "your");

    /**
     * Spelled-out numbers and percent words (Spanish/English). Speech-to-text often writes "diez por
     * ciento" or "veinticuatro horas", and the model may keep that wording. "uno"/"una"/"one" are left
     * out: they are articles far more often than amounts. Words starting with "veinti" also count.
     */
    private static final Set<String> NUMBER_WORDS = Set.of(
            "cero", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez", "once", "doce",
            "trece", "catorce", "quince", "dieciseis", "diecisiete", "dieciocho", "diecinueve", "veinte",
            "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa", "cien", "ciento",
            "cientos", "doscientos", "trescientos", "cuatrocientos", "quinientos", "seiscientos", "setecientos",
            "ochocientos", "novecientos", "mil", "millon", "millones", "porcentaje",
            "zero", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
            "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty",
            "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety", "hundred", "thousand",
            "million", "percent", "percentage");

    private final double similarityThreshold;

    public SuggestionDedupPolicy(
            @Value("${discovery.realtime.dedup-similarity-threshold:0.84}") double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    /** Cosine at or above which two drafts are related enough to compare what they say. */
    public double similarityThreshold() {
        return similarityThreshold;
    }

    /**
     * Whether {@code candidate} only repeats {@code earlier} and can be dropped without losing anything.
     *
     * @param cosine        embedding similarity of the two drafts, or {@code null} when unknown
     * @param linkedByModel {@code true} when the model itself pointed the candidate at {@code earlier}
     *                      ({@code targetStoryId}); the link stands in for the similarity bar
     */
    public Verdict repeats(Draft candidate, Draft earlier, @Nullable Double cosine, boolean linkedByModel) {
        Set<String> newNumbers = new LinkedHashSet<>(numbers(candidate.fullText()));
        newNumbers.removeAll(numbers(earlier.fullText()));
        if (!newNumbers.isEmpty()) {
            return Verdict.distinct("adds the numbers " + newNumbers);
        }
        if (candidate.criteriaArePayload()
                && !criteriaMissingFrom(candidate.criteria(), earlier.criteria()).isEmpty()) {
            return Verdict.distinct("adds acceptance criteria");
        }
        Set<String> newWords = new LinkedHashSet<>(tokens(candidate.title(), candidate.role(), candidate.action()));
        newWords.removeAll(tokens(earlier.fullText()));
        if (!newWords.isEmpty()) {
            return Verdict.distinct("adds the words " + newWords);
        }
        String title = normalize(candidate.title());
        if (title != null && title.equals(normalize(earlier.title()))) {
            return Verdict.duplicate("same title, nothing new");
        }
        if (linkedByModel) {
            return Verdict.duplicate("linked by the model, nothing new");
        }
        if (cosine != null && cosine >= similarityThreshold) {
            return Verdict.duplicate("similar (cosine %.3f), nothing new".formatted(cosine));
        }
        return Verdict.distinct("not related");
    }

    /**
     * Whether a NEW draft is the same requirement as an accepted {@code story} it scored {@code cosine}
     * against, so it may become an {@code UPDATE_STORY} of that story instead of a second story. Needs the
     * similarity bar AND the same intent; a draft with another intent of the same domain (the booking's
     * cancellation penalty, the doctor's notification) stays a NEW_STORY — turning it into an update
     * would overwrite the story's narrative on accept.
     */
    public Verdict sameRequirementAs(Draft candidate, Draft story, double cosine) {
        if (cosine < similarityThreshold) {
            return Verdict.distinct("not similar enough (cosine %.3f)".formatted(cosine));
        }
        if (jaccard(tokens(candidate.title()), tokens(story.title())) >= SAME_TITLE_OVERLAP) {
            return Verdict.duplicate("near-identical title");
        }
        if (sameActor(candidate.role(), story.role()) && sameAction(candidate.action(), story.action())) {
            return Verdict.duplicate("same actor and action");
        }
        return Verdict.distinct("different intent");
    }

    /**
     * The {@code proposed} criteria that state something none of the {@code existing} ones does — the
     * criteria an update or edge case would really add. Order is kept.
     */
    public List<Suggestion.DraftCriterion> criteriaMissingFrom(List<Suggestion.DraftCriterion> proposed,
                                                               List<Suggestion.DraftCriterion> existing) {
        List<Suggestion.DraftCriterion> missing = new ArrayList<>();
        for (Suggestion.DraftCriterion c : proposed) {
            if (existing.stream().noneMatch(e -> sameCriterion(c, e))) {
                missing.add(c);
            }
        }
        return missing;
    }

    /**
     * True when {@code proposal} tells the same story as {@code current}: title, role, action and benefit
     * are each the same words once case, accents, punctuation, plurals and filler words are ignored.
     * Strict on purpose — a single new content word ("y Excel") counts as a change.
     */
    public boolean sameNarrative(Draft proposal, Draft current) {
        return sameField(proposal.title(), current.title())
                && sameField(proposal.role(), current.role())
                && sameField(proposal.action(), current.action())
                && sameField(proposal.benefit(), current.benefit());
    }

    // ── Lexical helpers ───────────────────────────────────────────────────────

    /**
     * Trim + accent-fold + lowercase + collapse whitespace + strip punctuation; null/blank → null. Accent-
     * insensitive so "sesión" and "sesion" collide, matching how STT and the LLM inconsistently emit them.
     */
    static @Nullable String normalize(@Nullable String value) {
        if (value == null || value.isBlank()) return null;
        String stripped = Normalizer.normalize(value.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase()
                .replaceAll("[^\\p{Alnum}\\s]", " ")
                .replaceAll("\\s+", " ")
                .strip();
        return stripped.isBlank() ? null : stripped;
    }

    /**
     * Content words of {@code values}: normalized words minus function words and words under 3 letters,
     * singularized (a trailing "s" dropped) and cut to a short stem. Numbers are kept whole.
     */
    static Set<String> tokens(@Nullable String... values) {
        return words(values)
                .filter(w -> isDigits(w) || (w.length() >= 3 && !STOP_WORDS.contains(w)))
                .map(SuggestionDedupPolicy::stem)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Amounts mentioned in {@code value}: standalone numbers ("10" in "10 %", "24" in "24 horas"; not the
     * "2" of a code such as "2FA") and spelled-out number or percent words.
     */
    static Set<String> numbers(@Nullable String value) {
        return words(value)
                .filter(w -> isDigits(w) || NUMBER_WORDS.contains(w) || w.startsWith("veinti"))
                .map(w -> isDigits(w) ? w.replaceFirst("^0+(?=\\d)", "") : w)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        long shared = a.stream().filter(b::contains).count();
        return (double) shared / union.size();
    }

    private static Stream<String> words(@Nullable String... values) {
        return Arrays.stream(values)
                .map(SuggestionDedupPolicy::normalize)
                .filter(Objects::nonNull)
                .flatMap(v -> Arrays.stream(v.split(" ")));
    }

    private static String stem(String word) {
        if (isDigits(word)) return word;
        String singular = word.length() > 4 && word.endsWith("s") ? word.substring(0, word.length() - 1) : word;
        return singular.length() > STEM_LENGTH ? singular.substring(0, STEM_LENGTH) : singular;
    }

    private static boolean isDigits(String word) {
        return !word.isEmpty() && word.chars().allMatch(Character::isDigit);
    }

    private static boolean sameActor(@Nullable String a, @Nullable String b) {
        Set<String> ta = tokens(a);
        Set<String> tb = tokens(b);
        if (ta.isEmpty() || tb.isEmpty()) return false;
        return ta.containsAll(tb) || tb.containsAll(ta) || jaccard(ta, tb) >= SAME_ACTOR_OVERLAP;
    }

    /** Same leading verb (the first content word) and largely the same words. */
    private static boolean sameAction(@Nullable String a, @Nullable String b) {
        Set<String> ta = tokens(a);
        Set<String> tb = tokens(b);
        if (ta.isEmpty() || tb.isEmpty()) return false;
        return ta.iterator().next().equals(tb.iterator().next()) && jaccard(ta, tb) >= SAME_ACTION_OVERLAP;
    }

    private static boolean sameCriterion(Suggestion.DraftCriterion a, Suggestion.DraftCriterion b) {
        String ta = criterionText(a);
        String tb = criterionText(b);
        String na = normalize(ta);
        if (na != null && na.equals(normalize(tb))) return true;
        return jaccard(tokens(ta), tokens(tb)) >= SAME_CRITERION_OVERLAP;
    }

    private static String criterionText(Suggestion.DraftCriterion c) {
        return String.join(" ", nullToEmpty(c.given()), nullToEmpty(c.when()), nullToEmpty(c.then()));
    }

    private static boolean sameField(@Nullable String a, @Nullable String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na == null || nb == null) return na == null && nb == null;
        if (na.equals(nb)) return true;
        Set<String> ta = tokens(a);
        return !ta.isEmpty() && ta.equals(tokens(b));
    }

    private static String nullToEmpty(@Nullable String s) {
        return s == null ? "" : s;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    /** The outcome of a comparison, with a short reason for the logs. */
    public record Verdict(boolean duplicate, String reason) {
        static Verdict duplicate(String reason) {
            return new Verdict(true, reason);
        }

        static Verdict distinct(String reason) {
            return new Verdict(false, reason);
        }
    }

    /**
     * The comparable text of a story draft, a suggestion or a story. {@code criteria} are its acceptance
     * criteria; for {@code EDGE_CASE} and {@code UPDATE_STORY} they are what the draft adds to its target.
     */
    public record Draft(SuggestionType type, @Nullable String title, @Nullable String role,
                        @Nullable String action, @Nullable String benefit,
                        List<Suggestion.DraftCriterion> criteria) {

        public Draft {
            criteria = criteria == null ? List.of() : List.copyOf(criteria);
        }

        public static Draft of(GenerationResult.GeneratedStory gen) {
            List<Suggestion.DraftCriterion> criteria = gen.acceptanceCriteria() == null ? List.of()
                    : gen.acceptanceCriteria().stream()
                            .filter(Objects::nonNull)
                            .map(c -> new Suggestion.DraftCriterion(c.scenario(), c.given(), c.when(), c.then()))
                            .toList();
            SuggestionType type = gen.type() != null ? gen.type() : SuggestionType.NEW_STORY;
            return new Draft(type, gen.title(), gen.role(), gen.action(), gen.benefit(), criteria);
        }

        public static Draft of(Suggestion s) {
            return new Draft(s.getType(), s.getDraftTitle(), s.getDraftRole(), s.getDraftAction(),
                    s.getDraftBenefit(), s.getDraftAcceptanceCriteria());
        }

        public static Draft of(UserStory story) {
            List<Suggestion.DraftCriterion> criteria = story.getAcceptanceCriteria().stream()
                    .map(c -> new Suggestion.DraftCriterion(c.getScenario(), c.getGiven(), c.getWhen(), c.getThen()))
                    .toList();
            return new Draft(SuggestionType.NEW_STORY, story.getTitle(), story.getRole(), story.getAction(),
                    story.getBenefit(), criteria);
        }

        /** True when the criteria are what this draft adds to another story, not a story's own set. */
        boolean criteriaArePayload() {
            return type == SuggestionType.EDGE_CASE || type == SuggestionType.UPDATE_STORY;
        }

        String fullText() {
            return Stream.concat(
                            Stream.of(title, role, action, benefit),
                            criteria.stream().map(SuggestionDedupPolicy::criterionText))
                    .map(SuggestionDedupPolicy::nullToEmpty)
                    .collect(Collectors.joining(" "));
        }
    }
}
