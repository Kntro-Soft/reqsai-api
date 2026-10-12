package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.GenerationResult;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes the user-story and Gherkin keywords the model writes into a generated story's fields.
 *
 * <p>Clients render a story as "Como {role}, quiero {action}, para {benefit}." ("As {role}, I want
 * {action}, so that {benefit}.") and each criterion as "Dado {given}" / "Cuando {when}" / "Entonces
 * {then}". The model often writes those keywords itself ("Quiero reservar una cita", "Dado que un
 * paciente…", "Entonces se le cobra…."), so the analyst read "quiero Quiero reservar…" and "Dado Dado
 * que…". For role, action, benefit, given, when and then this class:
 * <ul>
 *   <li>removes the leading keyword in any case, with the spaces or {@code , : ; .} after it. Words
 *       that belong to the clause stay: "Dado que un paciente…" becomes "que un paciente…" (rendered
 *       "Dado que un paciente…"), "Para que el paciente…" becomes "que el paciente…", "As a patient"
 *       becomes "a patient" and "I want to book" becomes "to book", because the English labels are "As"
 *       and "I want". A step that opens with "Y" / "And" loses that word too, and at most two keywords
 *       are removed ("And then …");</li>
 *   <li>removes trailing spaces and {@code . ; ,}, since clients add their own punctuation;</li>
 *   <li>lowercases the first word when it is a function word in title case ("Un paciente" → "un
 *       paciente"), because it is always read after a label. Any other word, an acronym ("DNI") or a
 *       name ("María", "Yape", "La Molina") is left as it is;</li>
 *   <li>never empties a field: when nothing would be left, the original text is kept.</li>
 * </ul>
 * Only text the model generated goes through this class. The title, the scenario label and anything an
 * analyst types are never changed.
 */
final class GeneratedStoryNormalizer {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    /** What follows a keyword: spaces or {@code , : ; .}, or the end of the text. */
    private static final String AFTER_KEYWORD = "(?:[\\s,:;.]+|$)";

    private static final Pattern ROLE = keyword("Como|As");
    private static final Pattern ACTION = keyword("(?:Yo\\s+)?Quiero|I\\s+want");
    private static final Pattern BENEFIT = keyword("Para|So\\s+that");
    private static final Pattern GIVEN = keyword("Dad[oa]s?|Given|Y|And");
    private static final Pattern WHEN = keyword("Cuando|When|Y|And");
    private static final Pattern THEN = keyword("Entonces|Then|Y|And");

    /** At most this many keywords are removed from one field ("And then …", "Y cuando …"). */
    private static final int MAX_KEYWORDS = 2;

    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[\\s.;,]+$");

    /** The first word and the word after it. */
    private static final Pattern FIRST_WORDS = Pattern.compile("^(\\p{L}+)(\\s+(\\S+))?");

    /**
     * Function words lowercased when one opens a field. "I" is not one of them: it is always upper case.
     */
    private static final Set<String> FUNCTION_WORDS = Set.of(
            // Spanish articles, demonstratives, possessives, pronouns and prepositions.
            "el", "la", "los", "las", "lo", "un", "una", "unos", "unas", "al", "del", "que", "se", "le", "les",
            "ese", "esa", "esos", "esas", "este", "esta", "estos", "estas", "aquel", "aquella", "su", "sus",
            "mi", "mis", "tu", "tus", "nuestro", "nuestra", "nuestros", "nuestras", "cada", "todo", "toda",
            "todos", "todas", "otro", "otra", "otros", "otras", "ningún", "ninguna", "algún", "alguna",
            "algunos", "algunas", "no", "a", "en", "de", "con", "por", "sin",
            // English articles, demonstratives, possessives, pronouns and prepositions.
            "the", "an", "that", "this", "these", "those", "its", "their", "his", "her", "my", "our", "your",
            "it", "they", "we", "he", "she", "there", "each", "every", "any", "all", "some", "in", "on", "at",
            "to", "for", "with", "of", "from", "by");

    private GeneratedStoryNormalizer() {
    }

    /**
     * {@code gen} with its role, action, benefit and the Given/When/Then of each criterion normalized.
     * Type, title, priority, points, scenario labels, related topic and target are kept as they are.
     */
    static GenerationResult.GeneratedStory normalize(GenerationResult.GeneratedStory gen) {
        List<GenerationResult.GeneratedCriterion> criteria = gen.acceptanceCriteria() == null ? null
                : gen.acceptanceCriteria().stream().map(GeneratedStoryNormalizer::criterion).toList();
        return new GenerationResult.GeneratedStory(gen.type(), gen.title(),
                role(gen.role()), action(gen.action()), benefit(gen.benefit()),
                gen.priority(), gen.storyPoints(), criteria, gen.relatedTopic(), gen.targetStoryId(), gen.insight());
    }

    /** {@code c} with its Given, When and Then normalized; the scenario label is kept. */
    static GenerationResult.@Nullable GeneratedCriterion criterion(GenerationResult.@Nullable GeneratedCriterion c) {
        return c == null ? null
                : new GenerationResult.GeneratedCriterion(c.scenario(), given(c.given()), when(c.when()), then(c.then()));
    }

    /** The role without "Como" / "As": "Como paciente" → "paciente", "As a patient" → "a patient". */
    static @Nullable String role(@Nullable String text) {
        return clean(text, ROLE);
    }

    /** The action without "Quiero" / "Yo quiero" / "I want": "I want to book" → "to book". */
    static @Nullable String action(@Nullable String text) {
        return clean(text, ACTION);
    }

    /** The benefit without "Para" / "So that": "Para que el paciente…" → "que el paciente…". */
    static @Nullable String benefit(@Nullable String text) {
        return clean(text, BENEFIT);
    }

    /** The Given step without "Dado/Dada/Dados/Dadas" / "Given": "Dado que…" → "que…". */
    static @Nullable String given(@Nullable String text) {
        return clean(text, GIVEN);
    }

    /** The When step without "Cuando" / "When". */
    static @Nullable String when(@Nullable String text) {
        return clean(text, WHEN);
    }

    /** The Then step without "Entonces" / "Then". */
    static @Nullable String then(@Nullable String text) {
        return clean(text, THEN);
    }

    private static @Nullable String clean(@Nullable String text, Pattern keyword) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String t = text.strip();
        for (int i = 0; i < MAX_KEYWORDS; i++) {
            Matcher m = keyword.matcher(t);
            if (!m.find()) {
                break;
            }
            t = t.substring(m.end());
        }
        t = TRAILING_PUNCTUATION.matcher(t).replaceFirst("");
        if (t.isBlank()) {
            return text;
        }
        return lowercaseLeadingFunctionWord(t);
    }

    /**
     * "Un paciente" → "un paciente"; "La Molina", "DNI", "María" and "I" are kept. The first word is
     * lowercased only when it is a function word written in title case and the next word is not a name
     * (title case, such as "La Molina" or "El Salvador").
     */
    private static String lowercaseLeadingFunctionWord(String text) {
        Matcher m = FIRST_WORDS.matcher(text);
        if (!m.find()) {
            return text;
        }
        String word = m.group(1);
        String lower = word.toLowerCase(Locale.ROOT);
        if (!FUNCTION_WORDS.contains(lower) || !isTitleCase(word)) {
            return text;
        }
        String next = m.group(3);
        if (next != null && isTitleCase(next)) {
            return text;
        }
        return lower + text.substring(word.length());
    }

    /** An upper-case letter followed by no other upper-case letter ("Un", "A", "Molina,"; not "DNI"). */
    private static boolean isTitleCase(String word) {
        if (word.isEmpty() || !Character.isUpperCase(word.codePointAt(0))) {
            return false;
        }
        return word.substring(Character.charCount(word.codePointAt(0))).codePoints()
                .allMatch(cp -> Character.isLowerCase(cp) || !Character.isLetter(cp));
    }

    private static Pattern keyword(String alternatives) {
        return Pattern.compile("^(?:" + alternatives + ")" + AFTER_KEYWORD, FLAGS);
    }
}
