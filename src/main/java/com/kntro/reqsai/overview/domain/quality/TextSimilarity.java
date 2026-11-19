package com.kntro.reqsai.overview.domain.quality;

import java.text.Normalizer;
import java.util.Locale;

/**
 * How close two texts are, for telling a story the analyst kept from one they rewrote (US71).
 * <p>
 * Both texts are first normalized (lower case, no accents, punctuation and spacing collapsed to single spaces), so
 * a change of case, accent, punctuation or line breaks is not a rewrite. The similarity is then
 * {@code 1 - distance / length}: the Levenshtein distance (single-character insertions, deletions and
 * substitutions) over the length of the longer text, between 0 (nothing in common) and 1 (the same text).
 */
public final class TextSimilarity {

    private TextSimilarity() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /** Lower case, accents removed, every run of non-alphanumeric characters turned into one space, trimmed. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{Alnum}]+", " ").strip();
    }

    /** Similarity of the normalized texts, between 0 and 1; two empty texts are identical. */
    public static double similarity(String a, String b) {
        String left = normalize(a);
        String right = normalize(b);
        int longest = Math.max(left.length(), right.length());
        if (longest == 0) {
            return 1.0;
        }
        return 1.0 - (double) distance(left, right) / longest;
    }

    /** Levenshtein distance between two strings, as given (no normalization). */
    public static int distance(String a, String b) {
        int start = 0;
        int endA = a.length();
        int endB = b.length();
        while (start < endA && start < endB && a.charAt(start) == b.charAt(start)) {
            start++;
        }
        while (endA > start && endB > start && a.charAt(endA - 1) == b.charAt(endB - 1)) {
            endA--;
            endB--;
        }
        int lengthA = endA - start;
        int lengthB = endB - start;
        if (lengthA == 0) {
            return lengthB;
        }
        if (lengthB == 0) {
            return lengthA;
        }
        int[] previous = new int[lengthB + 1];
        int[] current = new int[lengthB + 1];
        for (int j = 0; j <= lengthB; j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= lengthA; i++) {
            current[0] = i;
            char charA = a.charAt(start + i - 1);
            for (int j = 1; j <= lengthB; j++) {
                int substitution = previous[j - 1] + (charA == b.charAt(start + j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[lengthB];
    }
}
