package com.kntro.reqsai.discovery.domain.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds where different speakers talked at the same time: two final segments of different speakers whose
 * time ranges intersect for at least {@link #DEFAULT_MIN_OVERLAP_MS}. A shorter intersection (a quick "ajá"
 * over the other person, or the padding providers add around words) is ignored. Overlapping stretches that
 * touch are merged into one. Pure function: no state, no I/O.
 */
public final class SpeakerOverlapDetector {

    /** Shortest intersection between two speakers that counts as overlapping speech. */
    public static final long DEFAULT_MIN_OVERLAP_MS = 500;
    /** Most stretches listed in a report; {@link SpeakerOverlapReport#count()} still counts them all. */
    public static final int MAX_RANGES = 50;

    private SpeakerOverlapDetector() {
    }

    /** {@link #detect(List, long)} with {@link #DEFAULT_MIN_OVERLAP_MS}. */
    public static SpeakerOverlapReport detect(List<SpeakerSpan> spans) {
        return detect(spans, DEFAULT_MIN_OVERLAP_MS);
    }

    /**
     * Detects the overlapping stretches among {@code spans} (any order; spans without a label are skipped).
     *
     * @param minOverlapMs shortest intersection between two speakers that counts
     */
    public static SpeakerOverlapReport detect(List<SpeakerSpan> spans, long minOverlapMs) {
        List<SpeakerSpan> sorted = spans.stream()
                .filter(s -> s.speakerLabel() != null && !s.speakerLabel().isBlank() && s.endMs() > s.startMs())
                .sorted(Comparator.comparingLong(SpeakerSpan::startMs).thenComparingLong(SpeakerSpan::endMs))
                .toList();

        List<Stretch> raw = new ArrayList<>();
        List<SpeakerSpan> active = new ArrayList<>();
        for (SpeakerSpan span : sorted) {
            active.removeIf(a -> a.endMs() <= span.startMs());
            for (SpeakerSpan other : active) {
                if (other.speakerLabel().equals(span.speakerLabel())) {
                    continue;
                }
                long end = Math.min(other.endMs(), span.endMs());
                if (end - span.startMs() >= minOverlapMs) {
                    raw.add(new Stretch(span.startMs(), end, other.speakerLabel(), span.speakerLabel()));
                }
            }
            active.add(span);
        }
        return merge(raw);
    }

    private static SpeakerOverlapReport merge(List<Stretch> raw) {
        if (raw.isEmpty()) {
            return SpeakerOverlapReport.none();
        }
        List<Stretch> sorted = raw.stream().sorted(Comparator.comparingLong(Stretch::startMs)).toList();
        List<SpeakerOverlapReport.Range> merged = new ArrayList<>();
        long start = sorted.getFirst().startMs();
        long end = sorted.getFirst().endMs();
        Set<String> labels = new LinkedHashSet<>(sorted.getFirst().labels());
        for (Stretch stretch : sorted.subList(1, sorted.size())) {
            if (stretch.startMs() <= end) {
                end = Math.max(end, stretch.endMs());
                labels.addAll(stretch.labels());
            } else {
                merged.add(new SpeakerOverlapReport.Range(start, end, List.copyOf(labels)));
                start = stretch.startMs();
                end = stretch.endMs();
                labels = new LinkedHashSet<>(stretch.labels());
            }
        }
        merged.add(new SpeakerOverlapReport.Range(start, end, List.copyOf(labels)));
        long totalMs = merged.stream().mapToLong(SpeakerOverlapReport.Range::durationMs).sum();
        List<SpeakerOverlapReport.Range> listed = merged.size() > MAX_RANGES ? merged.subList(0, MAX_RANGES) : merged;
        return new SpeakerOverlapReport(merged.size(), totalMs, listed);
    }

    private record Stretch(long startMs, long endMs, String first, String second) {
        List<String> labels() {
            return List.of(first, second);
        }
    }
}
