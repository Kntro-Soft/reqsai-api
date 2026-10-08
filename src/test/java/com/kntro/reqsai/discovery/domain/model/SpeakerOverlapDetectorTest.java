package com.kntro.reqsai.discovery.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link SpeakerOverlapDetector}: where two speakers talked at the same time (US40). */
@DisplayName("Domain: SpeakerOverlapDetector")
class SpeakerOverlapDetectorTest {

    @Test
    @DisplayName("reports nothing for alternating turns and for overlaps shorter than the threshold")
    void ignores_turns_and_short_overlaps() {
        SpeakerOverlapReport report = SpeakerOverlapDetector.detect(List.of(
                new SpeakerSpan("0", 0, 2_000),
                new SpeakerSpan("1", 2_000, 4_000),
                new SpeakerSpan("0", 3_700, 6_000)));

        assertThat(report.hasOverlaps()).isFalse();
        assertThat(report.count()).isZero();
        assertThat(report.totalMs()).isZero();
        assertThat(report.ranges()).isEmpty();
    }

    @Test
    @DisplayName("reports the stretch where two different speakers overlap for at least the threshold")
    void reports_overlap_between_speakers() {
        SpeakerOverlapReport report = SpeakerOverlapDetector.detect(List.of(
                new SpeakerSpan("1", 1_000, 5_000),
                new SpeakerSpan("0", 0, 3_000)));

        assertThat(report.count()).isEqualTo(1);
        assertThat(report.totalMs()).isEqualTo(2_000);
        assertThat(report.ranges()).singleElement().satisfies(range -> {
            assertThat(range.startMs()).isEqualTo(1_000);
            assertThat(range.endMs()).isEqualTo(3_000);
            assertThat(range.speakerLabels()).containsExactly("0", "1");
        });
    }

    @Test
    @DisplayName("never counts one speaker overlapping themselves and skips unlabelled spans")
    void ignores_same_speaker_and_unlabelled() {
        List<SpeakerSpan> spans = new ArrayList<>();
        spans.add(new SpeakerSpan("0", 0, 3_000));
        spans.add(new SpeakerSpan("0", 1_000, 4_000));
        spans.add(new SpeakerSpan(null, 1_000, 4_000));
        spans.add(new SpeakerSpan(" ", 1_000, 4_000));

        assertThat(SpeakerOverlapDetector.detect(spans).hasOverlaps()).isFalse();
    }

    @Test
    @DisplayName("merges touching stretches and lists every speaker involved")
    void merges_touching_stretches() {
        SpeakerOverlapReport report = SpeakerOverlapDetector.detect(List.of(
                new SpeakerSpan("0", 0, 4_000),
                new SpeakerSpan("1", 3_000, 6_000),
                new SpeakerSpan("2", 3_500, 8_000),
                new SpeakerSpan("0", 20_000, 22_000),
                new SpeakerSpan("1", 21_000, 23_000)));

        assertThat(report.count()).isEqualTo(2);
        assertThat(report.ranges().getFirst().startMs()).isEqualTo(3_000);
        assertThat(report.ranges().getFirst().endMs()).isEqualTo(6_000);
        assertThat(report.ranges().getFirst().speakerLabels()).containsExactlyInAnyOrder("0", "1", "2");
        assertThat(report.ranges().get(1).startMs()).isEqualTo(21_000);
        assertThat(report.ranges().get(1).endMs()).isEqualTo(22_000);
        assertThat(report.totalMs()).isEqualTo(3_000 + 1_000);
    }

    @Test
    @DisplayName("honours a custom threshold and caps the listed stretches while counting them all")
    void custom_threshold_and_cap() {
        assertThat(SpeakerOverlapDetector.detect(List.of(
                new SpeakerSpan("0", 0, 1_000), new SpeakerSpan("1", 800, 2_000)), 100).count()).isEqualTo(1);

        List<SpeakerSpan> many = new ArrayList<>();
        for (int i = 0; i < SpeakerOverlapDetector.MAX_RANGES + 5; i++) {
            long base = i * 10_000L;
            many.add(new SpeakerSpan("0", base, base + 2_000));
            many.add(new SpeakerSpan("1", base + 1_000, base + 3_000));
        }
        SpeakerOverlapReport report = SpeakerOverlapDetector.detect(many);

        assertThat(report.count()).isEqualTo(SpeakerOverlapDetector.MAX_RANGES + 5);
        assertThat(report.ranges()).hasSize(SpeakerOverlapDetector.MAX_RANGES);
    }
}
