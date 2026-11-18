package com.kntro.reqsai.discovery.infrastructure.quality;

import com.kntro.reqsai.discovery.application.port.StoryQualityEvaluator;
import com.kntro.reqsai.discovery.domain.model.AcceptanceCriterion;
import com.kntro.reqsai.discovery.domain.model.QualityCharacteristic;
import com.kntro.reqsai.discovery.domain.model.QualityFinding;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.infrastructure.quality.QualityLexicon.AmbiguousTerm;
import com.kntro.reqsai.discovery.infrastructure.quality.QualityText.Language;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic, rule-based {@link StoryQualityEvaluator} (US68). It reads the story's own words, so it needs no
 * AI provider: results are reproducible, free and not subject to AI quota. Observations and suggestions are
 * written in the language of the story (Spanish unless the story is clearly in English).
 * <ul>
 *   <li><b>Ambiguity</b>: vague, subjective or open-ended terms in any text of the story.</li>
 *   <li><b>Atomicity</b>: several actions joined in one story, an over-long action, a large estimate or too
 *       many criteria.</li>
 *   <li><b>Verifiability</b>: acceptance criteria exist and each states an observable or measurable outcome.</li>
 *   <li><b>Completeness</b>: a specific role, a goal, a benefit and acceptance criteria.</li>
 * </ul>
 */
@Component
public class RuleBasedStoryQualityEvaluator implements StoryQualityEvaluator {

    static final int AMBIGUOUS_TERM_PENALTY = 25;
    static final int EXTRA_ACTION_PENALTY = 30;
    static final int LONG_ACTION_PENALTY = 15;
    static final int LARGE_ESTIMATE_PENALTY = 20;
    static final int TOO_MANY_CRITERIA_PENALTY = 15;
    static final int MISSING_FIELD_PENALTY = 25;
    static final int GENERIC_ROLE_PENALTY = 10;
    /** An action with more words than this is hard to read as a single capability. */
    static final int LONG_ACTION_WORDS = 25;
    /** At this estimate or above a story is too big to be built and verified on its own. */
    static final int LARGE_ESTIMATE_POINTS = 13;
    static final int MAX_CRITERIA = 8;
    /** The most unmet-criteria score a story with a non-verifiable criterion can reach. */
    static final int PARTIAL_VERIFIABILITY_CAP = 75;
    private static final int MAX_LISTED_TERMS = 6;
    private static final int QUOTE_MAX = 60;
    /** An infinitive, alone or with attached pronouns: «exportar», «registrarse», «exportarlos». */
    private static final Pattern SPANISH_INFINITIVE = Pattern.compile("\\p{L}+(?:ar|er|ir)(?:se|me|te|lo|la|los|las|le|les|nos)?");
    private static final Pattern ACTION_SEPARATOR = Pattern.compile(
            "\\s*(?:,|;|/|\\s(?:y|e|o|u|and|or|also|then|ademas|tambien|luego)\\s)\\s*");

    @Override
    public List<QualityFinding> evaluate(UserStory story) {
        Language language = QualityText.detectLanguage(allText(story));
        return List.of(
                ambiguity(story, language),
                atomicity(story, language),
                verifiability(story, language),
                completeness(story, language));
    }

    // ── Ambiguity ────────────────────────────────────────────────────────────────

    private QualityFinding ambiguity(UserStory story, Language language) {
        List<String> terms = ambiguousTerms(allText(story));
        if (terms.isEmpty()) {
            return QualityFinding.met(QualityCharacteristic.AMBIGUITY, language.pick(
                    "No se detectaron términos ambiguos ni subjetivos.",
                    "No ambiguous or subjective terms were found."));
        }
        int score = Math.max(0, QualityFinding.MAX_SCORE - AMBIGUOUS_TERM_PENALTY * terms.size());
        String listed = quoted(terms.subList(0, Math.min(terms.size(), MAX_LISTED_TERMS)), language);
        return QualityFinding.unmet(QualityCharacteristic.AMBIGUITY, score,
                language.pick(
                        "Se detectaron términos ambiguos: " + listed + ".",
                        "Ambiguous terms were found: " + listed + "."),
                language.pick(
                        "Reemplaza cada término por un valor medible o una lista cerrada (por ejemplo, "
                                + "«en menos de 2 segundos» en vez de «rápido», o enumera los casos en vez de «etc.»).",
                        "Replace each term with a measurable value or a closed list (for example, "
                                + "\"in under 2 seconds\" instead of \"fast\", or list the cases instead of \"etc.\")."));
    }

    /** Distinct vague terms in the text, in the order of the lexicon. */
    static List<String> ambiguousTerms(String text) {
        String normalized = QualityText.normalize(text);
        Set<String> found = new LinkedHashSet<>();
        for (AmbiguousTerm term : QualityLexicon.AMBIGUOUS_TERMS) {
            if (term.pattern().matcher(normalized).find()) {
                found.add(term.display());
            }
        }
        if (QualityLexicon.ELLIPSIS.pattern().matcher(text).find()) {
            found.add(QualityLexicon.ELLIPSIS.display());
        }
        return List.copyOf(found);
    }

    // ── Atomicity ────────────────────────────────────────────────────────────────

    private QualityFinding atomicity(UserStory story, Language language) {
        List<String> actions = actionVerbs(story.getAction());
        int penalty = 0;
        List<String> observations = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        if (actions.size() > 1) {
            penalty += EXTRA_ACTION_PENALTY * (actions.size() - 1);
            observations.add(language.pick(
                    "La historia une varias acciones (" + String.join(", ", actions) + ").",
                    "The story joins several actions (" + String.join(", ", actions) + ")."));
            suggestions.add(language.pick(
                    "Divídela en una historia por acción para poder estimarla, construirla y verificarla por separado.",
                    "Split it into one story per action so each can be estimated, built and verified on its own."));
        }
        if (QualityText.wordCount(story.getAction()) > LONG_ACTION_WORDS) {
            penalty += LONG_ACTION_PENALTY;
            observations.add(language.pick(
                    "La acción es demasiado extensa para ser una sola capacidad.",
                    "The action is too long to be a single capability."));
            suggestions.add(language.pick(
                    "Resume la acción en una frase corta y mueve el detalle a los criterios de aceptación.",
                    "Shorten the action to one short sentence and move the detail into the acceptance criteria."));
        }
        Integer points = story.getStoryPoints();
        if (points != null && points >= LARGE_ESTIMATE_POINTS) {
            penalty += LARGE_ESTIMATE_PENALTY;
            observations.add(language.pick(
                    "Está estimada en " + points + " puntos, demasiado grande para una sola historia.",
                    "It is estimated at " + points + " points, too large for a single story."));
            suggestions.add(language.pick(
                    "Pártela en historias más pequeñas, de menos de " + LARGE_ESTIMATE_POINTS + " puntos.",
                    "Break it into smaller stories, each under " + LARGE_ESTIMATE_POINTS + " points."));
        }
        int criteria = story.getAcceptanceCriteria().size();
        if (criteria > MAX_CRITERIA) {
            penalty += TOO_MANY_CRITERIA_PENALTY;
            observations.add(language.pick(
                    "Tiene " + criteria + " criterios de aceptación, señal de que cubre más de una capacidad.",
                    "It has " + criteria + " acceptance criteria, a sign that it covers more than one capability."));
            suggestions.add(language.pick(
                    "Agrupa los criterios por capacidad y separa cada grupo en su propia historia.",
                    "Group the criteria by capability and split each group into its own story."));
        }

        if (penalty == 0) {
            return QualityFinding.met(QualityCharacteristic.ATOMICITY, language.pick(
                    "La historia describe una única acción de tamaño manejable.",
                    "The story describes a single action of manageable size."));
        }
        return QualityFinding.unmet(QualityCharacteristic.ATOMICITY, Math.max(0, QualityFinding.MAX_SCORE - penalty),
                String.join(" ", observations), String.join(" ", suggestions));
    }

    /** The verbs that start each action joined in the action phrase, e.g. «crear, editar y borrar» gives three. */
    static List<String> actionVerbs(String action) {
        String normalized = QualityText.normalize(action).strip();
        List<String> verbs = new ArrayList<>();
        for (String segment : ACTION_SEPARATOR.split(normalized)) {
            List<String> words = QualityText.words(segment);
            int first = 0;
            while (first < words.size() - 1 && isLeadIn(words.get(first))) {
                first++;
            }
            if (!words.isEmpty() && isVerb(words.get(first))) {
                verbs.add(words.get(first));
            }
        }
        return verbs;
    }

    private static boolean isLeadIn(String word) {
        return word.equals("poder") || word.equals("to") || word.equals("be") || word.equals("able");
    }

    private static boolean isVerb(String word) {
        if (QualityLexicon.ENGLISH_VERBS.contains(word)) {
            return true;
        }
        boolean infinitive = SPANISH_INFINITIVE.matcher(word).matches();
        return infinitive && !QualityLexicon.SPANISH_NON_VERBS.contains(word);
    }

    // ── Verifiability ────────────────────────────────────────────────────────────

    private QualityFinding verifiability(UserStory story, Language language) {
        List<AcceptanceCriterion> criteria = story.getAcceptanceCriteria();
        if (criteria.isEmpty()) {
            return QualityFinding.unmet(QualityCharacteristic.VERIFIABILITY, 0,
                    language.pick(
                            "La historia no tiene criterios de aceptación, así que no hay cómo comprobar que se cumple.",
                            "The story has no acceptance criteria, so there is no way to check that it is met."),
                    language.pick(
                            "Agrega al menos un criterio Dado/Cuando/Entonces con un resultado observable "
                                    + "(un mensaje, un estado o un valor concreto).",
                            "Add at least one Given/When/Then criterion with an observable outcome "
                                    + "(a message, a state or a concrete value)."));
        }
        List<Integer> unverifiable = new ArrayList<>();
        for (int i = 0; i < criteria.size(); i++) {
            if (!statesObservableOutcome(criteria.get(i).getThen())) {
                unverifiable.add(i);
            }
        }
        if (unverifiable.isEmpty()) {
            return QualityFinding.met(QualityCharacteristic.VERIFIABILITY, language.pick(
                    "Los " + criteria.size() + " criterios de aceptación declaran un resultado observable.",
                    "All " + criteria.size() + " acceptance criteria state an observable outcome."));
        }
        int checkable = criteria.size() - unverifiable.size();
        int score = Math.min(PARTIAL_VERIFIABILITY_CAP,
                Math.round(100f * checkable / criteria.size()));
        int firstIndex = unverifiable.get(0);
        String firstThen = truncate(criteria.get(firstIndex).getThen());
        return QualityFinding.unmet(QualityCharacteristic.VERIFIABILITY, score,
                language.pick(
                        unverifiable.size() + " de " + criteria.size() + " criterios no declaran un resultado "
                                + "observable ni medible (criterio " + (firstIndex + 1) + ": "
                                + language.quote(firstThen) + ").",
                        unverifiable.size() + " of " + criteria.size() + " criteria state no observable or "
                                + "measurable outcome (criterion " + (firstIndex + 1) + ": "
                                + language.quote(firstThen) + ")."),
                language.pick(
                        "Reescribe el «Entonces» con algo comprobable: un valor, un plazo, un mensaje o un cambio de estado concretos.",
                        "Rewrite the \"Then\" with something checkable: a concrete value, time limit, message or state change."));
    }

    /** A «Then» is checkable when it has a quantity or a verb of something the system does or shows. */
    static boolean statesObservableOutcome(String then) {
        String normalized = QualityText.normalize(then);
        if (QualityText.words(normalized).size() < 3) {
            return false;
        }
        return QualityLexicon.QUANTITY.matcher(normalized).find()
                || QualityLexicon.OBSERVABLE_OUTCOME.matcher(normalized).find();
    }

    // ── Completeness ─────────────────────────────────────────────────────────────

    private QualityFinding completeness(UserStory story, Language language) {
        int penalty = 0;
        List<String> observations = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        String role = QualityText.normalize(story.getRole()).strip();
        if (isPlaceholder(role) || role.length() < 2) {
            penalty += MISSING_FIELD_PENALTY;
            observations.add(language.pick("Falta el rol: no se sabe quién necesita la historia.",
                    "The role is missing: it is not clear who needs the story."));
            suggestions.add(language.pick(
                    "Indica quién la necesita (por ejemplo, «analista de cumplimiento»).",
                    "Say who needs it (for example, \"compliance analyst\")."));
        } else if (QualityLexicon.GENERIC_ROLES.contains(String.join(" ", QualityText.words(role)))) {
            penalty += GENERIC_ROLE_PENALTY;
            observations.add(language.pick("El rol es demasiado genérico.", "The role is too generic."));
            suggestions.add(language.pick(
                    "Cambia el rol genérico por el perfil real que se beneficia (por ejemplo, «analista de cumplimiento»).",
                    "Replace the generic role with the real profile that benefits (for example, \"compliance analyst\")."));
        }
        if (isIncomplete(story.getAction(), 2)) {
            penalty += MISSING_FIELD_PENALTY;
            observations.add(language.pick("La acción o meta está ausente o demasiado breve.",
                    "The action or goal is missing or too short."));
            suggestions.add(language.pick(
                    "Describe qué quiere lograr el rol con un verbo y su objeto (por ejemplo, «exportar el backlog a CSV»).",
                    "Describe what the role wants to achieve with a verb and its object (for example, \"export the backlog to CSV\")."));
        }
        if (isIncomplete(story.getBenefit(), 3)) {
            penalty += MISSING_FIELD_PENALTY;
            observations.add(language.pick("El beneficio está ausente o no explica el valor.",
                    "The benefit is missing or does not explain the value."));
            suggestions.add(language.pick(
                    "Explica para qué sirve (por ejemplo, «para compartirlo con el cliente sin reescribirlo»).",
                    "Explain what it is for (for example, \"so that I can share it with the client without rewriting it\")."));
        }
        if (story.getAcceptanceCriteria().isEmpty()) {
            penalty += MISSING_FIELD_PENALTY;
            observations.add(language.pick("No tiene criterios de aceptación.", "It has no acceptance criteria."));
            suggestions.add(language.pick("Agrega los criterios de aceptación que la enmarcan.",
                    "Add the acceptance criteria that frame it."));
        }

        if (penalty == 0) {
            return QualityFinding.met(QualityCharacteristic.COMPLETENESS, language.pick(
                    "La historia tiene rol, acción, beneficio y criterios de aceptación.",
                    "The story has a role, an action, a benefit and acceptance criteria."));
        }
        return QualityFinding.unmet(QualityCharacteristic.COMPLETENESS, Math.max(0, QualityFinding.MAX_SCORE - penalty),
                String.join(" ", observations), String.join(" ", suggestions));
    }

    private static boolean isIncomplete(String text, int minWords) {
        String normalized = QualityText.normalize(text).strip();
        return isPlaceholder(normalized) || QualityText.words(normalized).size() < minWords;
    }

    private static boolean isPlaceholder(String normalized) {
        return normalized.isEmpty() || normalized.chars().noneMatch(Character::isLetterOrDigit)
                || QualityLexicon.PLACEHOLDERS.contains(normalized);
    }

    // ── Shared ───────────────────────────────────────────────────────────────────

    /** Every piece of text of the story the rules read: title, fields and criteria. */
    private static String allText(UserStory story) {
        StringBuilder text = new StringBuilder();
        text.append(story.getTitle()).append("\n")
                .append(story.getRole()).append("\n")
                .append(story.getAction()).append("\n")
                .append(story.getBenefit()).append("\n");
        for (AcceptanceCriterion criterion : story.getAcceptanceCriteria()) {
            if (criterion.getScenario() != null) {
                text.append(criterion.getScenario()).append("\n");
            }
            text.append(criterion.getGiven()).append("\n")
                    .append(criterion.getWhen()).append("\n")
                    .append(criterion.getThen()).append("\n");
        }
        return text.toString();
    }

    private static String quoted(List<String> terms, Language language) {
        return String.join(", ", terms.stream().map(language::quote).toList());
    }

    private static String truncate(String text) {
        String stripped = text.strip();
        return stripped.length() <= QUOTE_MAX ? stripped : stripped.substring(0, QUOTE_MAX - 3) + "...";
    }
}
