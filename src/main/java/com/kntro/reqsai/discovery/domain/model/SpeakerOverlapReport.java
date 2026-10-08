package com.kntro.reqsai.discovery.domain.model;

import java.util.List;

/**
 * Stretches of a session where two or more speakers talked at the same time, so the speaker attribution
 * of those stretches may be wrong.
 *
 * @param count   number of overlapping stretches (all of them, even beyond {@code ranges})
 * @param totalMs total overlapping time in milliseconds
 * @param ranges  the stretches in time order, capped at {@link SpeakerOverlapDetector#MAX_RANGES}
 */
public record SpeakerOverlapReport(int count, long totalMs, List<Range> ranges) {

    private static final SpeakerOverlapReport NONE = new SpeakerOverlapReport(0, 0, List.of());

    public SpeakerOverlapReport {
        ranges = List.copyOf(ranges);
    }

    public static SpeakerOverlapReport none() {
        return NONE;
    }

    public boolean hasOverlaps() {
        return count > 0;
    }

    /**
     * One overlapping stretch.
     *
     * @param startMs       start, in milliseconds from the start of the recording
     * @param endMs         end, in milliseconds from the start of the recording
     * @param speakerLabels the labels of the speakers talking over each other, in first-seen order
     */
    public record Range(long startMs, long endMs, List<String> speakerLabels) {

        public Range {
            speakerLabels = List.copyOf(speakerLabels);
        }

        public long durationMs() {
            return endMs - startMs;
        }
    }
}
