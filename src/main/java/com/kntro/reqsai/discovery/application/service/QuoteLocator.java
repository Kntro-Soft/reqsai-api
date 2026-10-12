package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import org.jspecify.annotations.Nullable;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Finds the transcript segment a verbatim quote of the model comes from, so a suggestion can point at the
 * exact moment of the meeting. A live transcriber cuts a sentence into short segments, so a quote may run
 * across a few consecutive ones; the answer is the segment where the quote starts. Exact containment wins;
 * otherwise the run of segments holding most of the quote's words, when it holds enough of them (the model
 * sometimes trims or joins a phrase). Accents, case and punctuation are ignored, so a mis-accented quote
 * still lands.
 */
@FunctionalInterface
public interface QuoteLocator {

    /** Share of the quote's words a run of segments must hold to be its source. */
    double MIN_COVERAGE = 0.6;

    /** Most consecutive segments one quote is looked for across. */
    int MAX_SPAN = 4;

    QuoteLocator NONE = quote -> null;

    @Nullable Integer sequenceOf(@Nullable String quote);

    static QuoteLocator of(List<TranscriptSegment> segments) {
        if (segments == null || segments.isEmpty()) return NONE;
        List<TranscriptSegment> ordered = segments.stream()
                .sorted(Comparator.comparingInt(TranscriptSegment::getSequence)).toList();
        return quote -> locate(quote, ordered);
    }

    /** The sequence of the segment where the quote starts, or null; {@code segments} in transcript order. */
    static @Nullable Integer locate(@Nullable String quote, List<TranscriptSegment> segments) {
        String wanted = normalize(quote);
        if (wanted.isBlank()) return null;
        List<String> texts = segments.stream().map(s -> normalize(s.getText())).toList();

        for (int i = 0; i < texts.size(); i++) {
            if (texts.get(i).contains(wanted)) return segments.get(i).getSequence();
        }
        for (int i = 0; i < texts.size(); i++) {
            StringBuilder joined = new StringBuilder(texts.get(i));
            for (int j = i + 1; j < Math.min(texts.size(), i + MAX_SPAN); j++) {
                joined.append(' ').append(texts.get(j));
                int at = joined.indexOf(wanted);
                if (at >= 0 && at < texts.get(i).length()) return segments.get(i).getSequence();
            }
        }
        return closest(words(wanted), texts, segments);
    }

    /**
     * The start of the run of up to {@link #MAX_SPAN} segments holding most of the quote's words. A run
     * starts at a segment that shares words with the quote; on a tie the shorter, then the earlier, run wins.
     */
    private static @Nullable Integer closest(Set<String> quoteWords, List<String> texts,
                                             List<TranscriptSegment> segments) {
        if (quoteWords.isEmpty()) return null;
        Integer best = null;
        long bestShared = 0;
        for (int i = 0; i < texts.size(); i++) {
            Set<String> run = new HashSet<>();
            for (int j = i; j < Math.min(texts.size(), i + MAX_SPAN); j++) {
                Set<String> segmentWords = words(texts.get(j));
                if (j == i && segmentWords.stream().noneMatch(quoteWords::contains)) break;
                run.addAll(segmentWords);
                long shared = quoteWords.stream().filter(run::contains).count();
                if (shared > bestShared) {
                    bestShared = shared;
                    best = segments.get(i).getSequence();
                }
            }
        }
        double coverage = (double) bestShared / quoteWords.size();
        return bestShared >= 2 && coverage >= MIN_COVERAGE ? best : null;
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
