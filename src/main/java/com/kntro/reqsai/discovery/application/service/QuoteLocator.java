package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import org.jspecify.annotations.Nullable;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Finds the transcript segment a verbatim quote of the model comes from, so a suggestion can point at the
 * exact moment of the meeting. Exact containment wins; otherwise the segment holding most of the quote's
 * words, when it holds enough of them (the model sometimes trims or joins a phrase). Accents, case and
 * punctuation are ignored, so a mis-accented quote still lands.
 */
@FunctionalInterface
public interface QuoteLocator {

    /** Share of the quote's words a segment must hold to be its source. */
    double MIN_COVERAGE = 0.6;

    QuoteLocator NONE = quote -> null;

    @Nullable Integer sequenceOf(@Nullable String quote);

    static QuoteLocator of(List<TranscriptSegment> segments) {
        if (segments == null || segments.isEmpty()) return NONE;
        List<TranscriptSegment> copy = List.copyOf(segments);
        return quote -> locate(quote, copy);
    }

    static @Nullable Integer locate(@Nullable String quote, List<TranscriptSegment> segments) {
        String wanted = normalize(quote);
        if (wanted.isBlank()) return null;
        Set<String> quoteWords = words(wanted);
        if (quoteWords.isEmpty()) return null;

        Integer best = null;
        double bestCoverage = 0;
        for (TranscriptSegment segment : segments) {
            String text = normalize(segment.getText());
            if (text.contains(wanted)) {
                return segment.getSequence();
            }
            Set<String> segmentWords = words(text);
            long shared = quoteWords.stream().filter(segmentWords::contains).count();
            double coverage = (double) shared / quoteWords.size();
            if (shared >= 2 && coverage > bestCoverage) {
                bestCoverage = coverage;
                best = segment.getSequence();
            }
        }
        return bestCoverage >= MIN_COVERAGE ? best : null;
    }

    private static String normalize(@Nullable String value) {
        if (value == null) return "";
        String plain = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return plain.replaceAll("[^a-z0-9ñ]+", " ").strip();
    }

    private static Set<String> words(String normalized) {
        return Arrays.stream(normalized.split(" "))
                .filter(w -> w.length() >= 3)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
