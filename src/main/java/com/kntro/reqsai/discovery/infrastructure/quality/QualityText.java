package com.kntro.reqsai.discovery.infrastructure.quality;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Text helpers of the rule-based evaluation: accent- and case-insensitive matching, words and language. */
final class QualityText {

    private static final Set<String> SPANISH_MARKERS = Set.of(
            "el", "la", "los", "las", "de", "del", "que", "para", "un", "una", "y", "en", "con", "por",
            "quiero", "poder", "puedo", "como", "al", "lo", "se", "su", "sus", "es", "mi", "mis", "sin");
    private static final Set<String> ENGLISH_MARKERS = Set.of(
            "the", "to", "and", "of", "for", "with", "a", "an", "i", "so", "that", "can", "want", "as", "be",
            "my", "is", "are", "in", "on", "when", "then", "given");

    private QualityText() {
    }

    /** Lower-cased and without accents, so «Rápido» and «rapido» are the same word. */
    static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    /** The words of an already normalized text (letters and digits). */
    static List<String> words(String normalized) {
        return Arrays.stream(normalized.split("[^\\p{L}\\p{N}]+")).filter(word -> !word.isEmpty()).toList();
    }

    static int wordCount(String text) {
        return words(normalize(text)).size();
    }

    /** English only when it clearly outweighs Spanish; Spanish is the product's default. */
    static Language detectLanguage(String text) {
        int spanish = 0;
        int english = 0;
        for (String word : words(normalize(text))) {
            if (SPANISH_MARKERS.contains(word)) {
                spanish++;
            }
            if (ENGLISH_MARKERS.contains(word)) {
                english++;
            }
        }
        return english > spanish ? Language.EN : Language.ES;
    }

    /** Language the observations and suggestions are written in: the story's own. */
    enum Language {
        ES, EN;

        String pick(String spanish, String english) {
            return this == ES ? spanish : english;
        }

        /** Quotes a fragment of the story the way the language does. */
        String quote(String text) {
            return this == ES ? "«" + text + "»" : "\"" + text + "\"";
        }
    }
}
