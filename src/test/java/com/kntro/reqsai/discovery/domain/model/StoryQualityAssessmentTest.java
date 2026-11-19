package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("StoryQualityAssessment")
class StoryQualityAssessmentTest {

    private static final UUID STORY = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");

    private static List<QualityFinding> findings(int ambiguity, int atomicity, int verifiability, int completeness) {
        return List.of(
                finding(QualityCharacteristic.AMBIGUITY, ambiguity),
                finding(QualityCharacteristic.ATOMICITY, atomicity),
                finding(QualityCharacteristic.VERIFIABILITY, verifiability),
                finding(QualityCharacteristic.COMPLETENESS, completeness));
    }

    private static QualityFinding finding(QualityCharacteristic characteristic, int score) {
        return score == 100
                ? QualityFinding.met(characteristic, "ok")
                : QualityFinding.unmet(characteristic, score, "falla", "arréglalo");
    }

    @Test
    @DisplayName("a story that meets every characteristic scores 100")
    void perfect_score() {
        StoryQualityAssessment assessment = StoryQualityAssessment.evaluate(STORY, findings(100, 100, 100, 100), NOW);

        assertThat(assessment.getScore()).isEqualTo(100);
        assertThat(assessment.getStoryId()).isEqualTo(STORY);
        assertThat(assessment.getEvaluatedAt()).isEqualTo(NOW);
        assertThat(assessment.unmetFindings()).isEmpty();
    }

    @Test
    @DisplayName("the evaluation instant keeps microseconds, the precision the database stores")
    void evaluation_instant_is_truncated_to_microseconds() {
        Instant withNanos = Instant.parse("2026-11-18T12:00:00.123456789Z");

        StoryQualityAssessment assessment = StoryQualityAssessment.evaluate(STORY, findings(100, 100, 100, 100), withNanos);

        assertThat(assessment.getEvaluatedAt()).isEqualTo(Instant.parse("2026-11-18T12:00:00.123456Z"));
    }

    @Test
    @DisplayName("a story that meets none scores 0")
    void zero_score() {
        assertThat(StoryQualityAssessment.evaluate(STORY, findings(0, 0, 0, 0), NOW).getScore()).isZero();
    }

    @Test
    @DisplayName("the overall score is the average of the characteristics, rounded")
    void average_score() {
        assertThat(StoryQualityAssessment.evaluate(STORY, findings(75, 100, 0, 75), NOW).getScore()).isEqualTo(63);
        assertThat(StoryQualityAssessment.evaluate(STORY, findings(70, 70, 70, 71), NOW).getScore()).isEqualTo(70);
        assertThat(StoryQualityAssessment.evaluate(STORY, findings(70, 70, 71, 71), NOW).getScore()).isEqualTo(71);
    }

    @Test
    @DisplayName("lists the unmet characteristics in evaluation order")
    void unmet_findings() {
        StoryQualityAssessment assessment = StoryQualityAssessment.evaluate(STORY, findings(75, 100, 0, 100), NOW);

        assertThat(assessment.unmetFindings()).extracting(QualityFinding::characteristic)
                .containsExactly(QualityCharacteristic.AMBIGUITY, QualityCharacteristic.VERIFIABILITY);
    }

    @Test
    @DisplayName("evaluating again replaces the score, the findings and the date of the same assessment")
    void reevaluate() {
        StoryQualityAssessment assessment = StoryQualityAssessment.evaluate(STORY, findings(0, 0, 0, 0), NOW);
        UUID id = assessment.getId();
        Instant later = NOW.plusSeconds(60);

        assessment.reevaluate(findings(100, 100, 100, 100), later);

        assertThat(assessment.getId()).isEqualTo(id);
        assertThat(assessment.getStoryId()).isEqualTo(STORY);
        assertThat(assessment.getScore()).isEqualTo(100);
        assertThat(assessment.getEvaluatedAt()).isEqualTo(later);
        assertThat(assessment.getFindings()).hasSize(4);
    }

    @Test
    @DisplayName("requires exactly one finding per characteristic")
    void one_finding_per_characteristic() {
        List<QualityFinding> missing = new ArrayList<>(findings(100, 100, 100, 100));
        missing.remove(3);
        assertThatThrownBy(() -> StoryQualityAssessment.evaluate(STORY, missing, NOW))
                .isInstanceOf(DomainException.class);

        List<QualityFinding> repeated = new ArrayList<>(missing);
        repeated.add(finding(QualityCharacteristic.AMBIGUITY, 100));
        assertThatThrownBy(() -> StoryQualityAssessment.evaluate(STORY, repeated, NOW))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("requires a story, findings and a date")
    void required_fields() {
        assertThatThrownBy(() -> StoryQualityAssessment.evaluate(null, findings(100, 100, 100, 100), NOW))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> StoryQualityAssessment.evaluate(STORY, null, NOW))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> StoryQualityAssessment.evaluate(STORY, findings(100, 100, 100, 100), null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("the findings are exposed read-only")
    void read_only_findings() {
        StoryQualityAssessment assessment = StoryQualityAssessment.evaluate(STORY, findings(100, 100, 100, 100), NOW);

        assertThatThrownBy(() -> assessment.getFindings().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
